package com.example.guardhr

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

object Session {
    /** Текущий пользователь; null — приложение заблокировано. */
    var user by mutableStateOf<AppUser?>(null)
    var skipNextLock = false
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySecure(Sec.secureScreen(this))
        ThemeState.mode = Sec.themeMode(this)
        scheduleReminders(this)
        val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { GuardTheme { Root() } }
    }

    fun applySecure(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onResume() {
        super.onResume()
        Session.skipNextLock = false
    }

    /** Уход приложения в фон блокирует его (кроме открытия системного окна выбора файла). */
    override fun onStop() {
        super.onStop()
        if (Session.skipNextLock) Session.skipNextLock = false else Session.user = null
    }

    @Composable
    private fun Root() {
        val act = this@MainActivity
        val dao = remember { AppDb.get(act).dao() }
        val usersFlow = remember { dao.users() }
        val users by usersFlow.collectAsState(initial = null)
        val scope = rememberCoroutineScope()
        val legacy = remember { Sec.legacyPin(act) }
        val list = users

        // Обновление с версии с одним PIN-кодом: создаём учётную запись администратора
        LaunchedEffect(list?.isEmpty(), legacy) {
            if (list != null && list.isEmpty() && legacy != null) {
                val id = dao.insertUser(
                    AppUser(
                        login = "admin",
                        displayName = Sec.userName(act).ifBlank { "Администратор" },
                        passwordHash = legacy.second, salt = legacy.first, role = Role.ADMIN.name
                    )
                )
                if (Sec.biometricEnabled(act)) Sec.setBioUser(act, id)
                Sec.clearLegacyPin(act)
            }
        }

        when {
            list == null -> Box(Modifier.fillMaxSize())
            list.isEmpty() && legacy == null -> OnboardingScreen { name, pin, bio ->
                scope.launch {
                    val (salt, hash) = hashPin(pin)
                    val u = AppUser(login = "admin", displayName = name, passwordHash = hash, salt = salt, role = Role.ADMIN.name)
                    val id = dao.insertUser(u)
                    Sec.setBioUser(act, if (bio) id else 0L)
                    Sec.setLastUser(act, id)
                    Session.user = u.copy(id = id)
                }
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize())
            Session.user == null -> LockScreen(act, list) { Session.user = it }
            else -> App(Session.user!!, list) { applySecure(it) }
        }
    }
}
