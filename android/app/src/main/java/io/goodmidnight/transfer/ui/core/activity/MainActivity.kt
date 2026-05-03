package io.goodmidnight.transfer.ui.core.activity

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.goodmidnight.transfer.core.utils.PendingFileStorage
import io.goodmidnight.transfer.designsystem.theme.Theme
import io.goodmidnight.transfer.ui.core.component.MainApp
import io.goodmidnight.transfer.ui.core.navigation.MainGraph
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var pendingFileStorage: PendingFileStorage

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
        enableEdgeToEdge()


        setContent {
            val navController = rememberNavController()

            Theme {
                MainApp(navController = navController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return

        val sharedUris = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                uri?.let { sharedUris.add(it) }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)

                uris?.let { sharedUris.addAll(it) }
            }
        }

        if (sharedUris.isNotEmpty()) {
            lifecycleScope.launch {
                pendingFileStorage.saveFiles(sharedUris)
                val homeUri = MainGraph.Transfer.Home.deepLinkUriPattern().toUri()
                intent.data = homeUri
            }
        }
    }
}