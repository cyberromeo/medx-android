package quest.srihari.medxelite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import quest.srihari.medxelite.data.MedxAppearance
import quest.srihari.medxelite.ui.AppRoot
import quest.srihari.medxelite.ui.auth.SignInScreen
import quest.srihari.medxelite.ui.theme.MedxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Medx.init(this)
        setContent {
            val accent by Medx.prefs.accent.collectAsState()
            val appearance by Medx.prefs.appearance.collectAsState()
            val session by Medx.auth.session.collectAsState()
            val dark = when (appearance) {
                MedxAppearance.SYSTEM -> isSystemInDarkTheme()
                MedxAppearance.LIGHT -> false
                MedxAppearance.DARK -> true
            }
            SystemBars(dark)
            MedxTheme(accent, appearance) {
                if (session == null) SignInScreen() else AppRoot()
            }
        }
    }

    @Composable
    private fun SystemBars(dark: Boolean) {
        DisposableEffect(dark) {
            val style = if (dark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose {}
        }
    }
}
