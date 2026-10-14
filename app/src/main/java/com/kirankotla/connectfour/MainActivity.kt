package com.kirankotla.connectfour

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.kirankotla.connectfour.data.ConnectFourDatabase
import com.kirankotla.connectfour.data.PlayerPreferences
import com.kirankotla.connectfour.data.ProgressionProcessor
import com.kirankotla.connectfour.data.SettingsData
import com.kirankotla.connectfour.data.ThemeMode
import com.kirankotla.connectfour.ui.ConnectFourNavHost
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import com.kirankotla.connectfour.ui.theme.ConnectFourTheme
import com.kirankotla.connectfour.util.MusicManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

class MainActivity : ComponentActivity() {
    private var musicManager: MusicManager? = null
    private val deepLinkRoomCode = mutableStateOf<String?>(null)
    private var appUpdateManager: AppUpdateManager? = null
    private var pendingUpdateInfo: com.google.android.play.core.appupdate.AppUpdateInfo? = null
    private val updateUiState = mutableStateOf<UpdateUiState>(UpdateUiState.Hidden)
    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { }
    private val updateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> updateUiState.value = UpdateUiState.Downloading(state.bytesDownloaded(), state.totalBytesToDownload())
            InstallStatus.DOWNLOADED -> updateUiState.value = UpdateUiState.Ready
            InstallStatus.FAILED -> updateUiState.value = UpdateUiState.Hidden
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractDeepLink(intent)

        val music = MusicManager(applicationContext)
        musicManager = music
        appUpdateManager = AppUpdateManagerFactory.create(this).also { it.registerListener(updateListener) }
        checkForFlexibleUpdate()

        val database = ConnectFourDatabase.getDatabase(applicationContext)
        val playerPreferences = PlayerPreferences(applicationContext)
        val progressionProcessor = ProgressionProcessor(database.connectFourDao(), playerPreferences)

        setContent {
            val updateState by updateUiState
            val settings by playerPreferences.settingsFlow.collectAsState(initial = SettingsData())

            LaunchedEffect(settings.musicEnabled) {
                music.isMusicEnabled = settings.musicEnabled
                if (settings.musicEnabled) {
                    music.start()
                } else {
                    music.pause()
                }
            }

            val systemInDark = isSystemInDarkTheme()
            val isDark = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemInDark
            }

            ConnectFourTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConnectFourNavHost(
                        progressionProcessor = progressionProcessor,
                        playerPreferences = playerPreferences,
                        deepLinkCode = deepLinkRoomCode.value,
                        onClearDeepLink = { deepLinkRoomCode.value = null }
                    )
                }
            }

            AppUpdatePrompt(
                state = updateState,
                onStart = { startFlexibleUpdate() },
                onComplete = { appUpdateManager?.completeUpdate() },
                onDismiss = { updateUiState.value = UpdateUiState.Hidden }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractDeepLink(intent)
    }

    private fun extractDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        val code = uri.getQueryParameter("code") ?: uri.lastPathSegment
        if (!code.isNullOrBlank()) {
            deepLinkRoomCode.value = code.trim().uppercase()
        }
    }

    override fun onStart() {
        super.onStart()
        musicManager?.resume()
        appUpdateManager?.appUpdateInfo?.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) updateUiState.value = UpdateUiState.Ready
        }
    }

    override fun onStop() {
        super.onStop()
        musicManager?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        musicManager?.release()
        musicManager = null
        appUpdateManager?.unregisterListener(updateListener)
        appUpdateManager = null
    }

    private fun checkForFlexibleUpdate() {
        appUpdateManager?.appUpdateInfo?.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                pendingUpdateInfo = info
                updateUiState.value = UpdateUiState.Available
            }
        }
    }

    private fun startFlexibleUpdate() {
        val info = pendingUpdateInfo ?: return
        updateUiState.value = UpdateUiState.Downloading(0L, 0L)
        appUpdateManager?.startUpdateFlowForResult(
            info,
            updateLauncher,
            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
        )
    }
}

private sealed interface UpdateUiState {
    data object Hidden : UpdateUiState
    data object Available : UpdateUiState
    data class Downloading(val downloaded: Long, val total: Long) : UpdateUiState
    data object Ready : UpdateUiState
}

@androidx.compose.runtime.Composable
private fun AppUpdatePrompt(
    state: UpdateUiState,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        UpdateUiState.Hidden -> Unit
        UpdateUiState.Available -> androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { androidx.compose.material3.Text("Update Connect Four") },
            text = { androidx.compose.material3.Text("A new version is available. Download it in the background while you continue playing.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = onStart) { androidx.compose.material3.Text("Download") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Later") } }
        )
        is UpdateUiState.Downloading -> androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { androidx.compose.material3.Text("Downloading update") },
            text = {
                Column {
                    androidx.compose.material3.Text("Connect Four is downloading in the background.")
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { if (state.total > 0) state.downloaded.toFloat() / state.total else 0f },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {}
        )
        UpdateUiState.Ready -> androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { androidx.compose.material3.Text("Update ready") },
            text = { androidx.compose.material3.Text("The update is downloaded. Restart Connect Four to install it.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = onComplete) { androidx.compose.material3.Text("Restart and install") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Later") } }
        )
    }
}
