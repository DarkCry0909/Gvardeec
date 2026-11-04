package com.example.guardhr

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity

object Session {
    var unlocked by mutableStateOf(false)
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
        if (Session.skipNextLock) Session.skipNextLock = false else Session.unlocked = false
    }

    @Composable
    private fun Root() {
        var hasPin by remember { mutableStateOf(Sec.hasPin(this@MainActivity)) }
        when {
            !hasPin -> SetupPinScreen { pin ->
                Sec.setPin(this@MainActivity, pin)
                Sec.setBiometric(this@MainActivity, canBiometric(this@MainActivity))
                hasPin = true
                Session.unlocked = true
            }
            !Session.unlocked -> LockScreen(this@MainActivity) { Session.unlocked = true }
            else -> App { applySecure(it) }
        }
    }
}
