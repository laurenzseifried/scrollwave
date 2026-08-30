package de.laurenz.scrollwave

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            ScrollwaveApp(
                viewModel = viewModel,
                onLogin = {
                    viewModel.loginUrl()?.let { uri ->
                        CustomTabsIntent.Builder().setShowTitle(false).build().launchUrl(this, uri)
                    }
                },
            )
        }
        handleRedirect(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRedirect(intent)
    }

    private fun handleRedirect(intent: Intent) {
        intent.data?.takeIf { it.scheme == "scrollwave" && it.host == "oauth" }
            ?.let(viewModel::completeLogin)
    }
}
