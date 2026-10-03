package runcoach.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.health.connect.client.PermissionController
import runcoach.app.ui.HomeScreen
import runcoach.app.ui.HomeViewModel

class MainActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()

    private val healthPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { vm.refreshStatus() }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleRedirect(intent)
        NewRunWorker.schedule(this)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                HomeScreen(
                    vm = vm,
                    onConnectHealth = {
                        if (!vm.service.health.isAvailable()) {
                            // Send the user to the Play Store to install or update Health Connect.
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                                "market://details?id=com.google.android.apps.healthdata"
                            )))
                        } else {
                            val perms = vm.service.health.permissions.toMutableSet()
                            if (vm.service.health.supportsBackgroundRead()) perms += vm.service.health.backgroundPermission
                            healthPermissions.launch(perms)
                        }
                    },
                    onConnectSpotify = {
                        runCatching { vm.service.spotify.startLogin(this) }
                            .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
                    },
                    onOpenUrl = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRedirect(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshStatus()
    }

    private fun handleRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "runcoach" && data.host == "callback") vm.finishSpotifyLogin(data)
    }
}
