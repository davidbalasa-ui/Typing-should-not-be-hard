package com.typebetter.keyboard.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.florisboard.lib.compose.stringRes

/**
 * "Check for updates" on the home screen (docs/decisions/2026-11-27-check-for-updates.md). Asks Google
 * Play when the screen opens, says whether a newer build is there, and installs it from inside the app
 * through Play's In-App Updates. The wording for each case comes from [updateStateFor].
 */
@Composable
internal fun CheckForUpdatesRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { AppUpdateManagerFactory.create(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    // Play's AppUpdateInfo is what starts an update; one is used once, then asked for again.
    var info by remember { mutableStateOf<AppUpdateInfo?>(null) }
    // Only the newest check may answer: an older one finishing late must not overwrite what is true now.
    var checkJob by remember { mutableStateOf<Job?>(null) }
    // Set by a check that found an immediate update to reopen; the effect below reopens it.
    var resumePending by remember { mutableStateOf(false) }
    // Reopened by itself at most once while the row is shown; after that only the Resume button does it,
    // so Back on Play's screen always gets David out.
    var autoResumed by remember { mutableStateOf(false) }

    // [resume]: an immediate update left running is reopened straight away (Play's rule), once per showing
    // of the row ([autoResumed]), so closing Play's screen never traps David in a loop.
    fun check(resume: Boolean = false) {
        checkJob?.cancel()
        state = UpdateState.Checking
        if (!installedByPlay(installerOf(context))) {
            state = UpdateState.NotFromPlay
            return
        }
        checkJob = scope.launch {
            state = try {
                // A Play that never answers must not leave the row on "Asking Google Play…" for good.
                val answer = withTimeoutOrNull(CHECK_TIMEOUT_MS) { manager.appUpdateInfo.await() }
                info = answer
                if (answer == null) {
                    UpdateState.Failed
                } else {
                    val next = updateStateFor(answer.toPlayAnswer())
                    if (resume && !autoResumed && next is UpdateState.InProgress && next.resumable) {
                        autoResumed = true
                        resumePending = true
                    }
                    next
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                info = null
                updateStateForError((e as? InstallException)?.errorCode)
            }
        }
    }

    // Play's own screen reports back here: OK means it is installing (TypeBetter restarts on its own);
    // Back or a failure leaves the old build, so ask again and show what is true now.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { check() }

    fun startUpdate(type: Int) {
        val current = info
        val started = current != null && try {
            manager.startUpdateFlowForResult(current, launcher, AppUpdateOptions.newBuilder(type).build())
        } catch (e: Exception) {
            false
        }
        info = null
        if (!started) state = UpdateState.Failed
    }

    LaunchedEffect(resumePending) {
        if (resumePending) {
            resumePending = false
            startUpdate(AppUpdateType.IMMEDIATE)
        }
    }

    // Progress of a background (flexible) download, and the "downloaded" moment it ends with.
    DisposableEffect(manager) {
        val listener = InstallStateUpdatedListener { install ->
            // Play's live progress is newer than any check still waiting for an answer.
            if (install.installStatus() in LIVE_STATUSES) checkJob?.cancel()
            when (install.installStatus()) {
                InstallStatus.DOWNLOADED -> state = UpdateState.ReadyToInstall
                InstallStatus.PENDING, InstallStatus.DOWNLOADING, InstallStatus.INSTALLING -> state =
                    UpdateState.InProgress(progressPercent(install.installStatus(), install.bytesDownloaded(), install.totalBytesToDownload()))
                InstallStatus.FAILED -> state = UpdateState.Failed
                InstallStatus.CANCELED -> check()
                else -> Unit
            }
        }
        manager.registerListener(listener)
        onDispose { manager.unregisterListener(listener) }
    }

    // Ask when the screen opens, and again on coming back while an update is going or after the Play
    // Store page (an immediate update left running must be resumed, Play's own rule).
    LaunchedEffect(Unit) { check(resume = true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                // `state` is snapshot state, so this reads the newest value, not the last composed one.
                (state is UpdateState.InProgress || state is UpdateState.AvailableInStore)
            ) {
                check(resume = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val current = tbBuildNumber(BuildConfig.VERSION_CODE.toLong())
        ?.let { stringRes(R.string.typebetter__update_build, "build" to it) }
        ?: BuildConfig.VERSION_NAME
    fun newer(code: Long): String =
        tbBuildNumber(code)?.toString() ?: code.toString()

    val s = state
    val summary = when (s) {
        UpdateState.Checking -> stringRes(R.string.typebetter__update_checking, "current" to current)
        UpdateState.UpToDate -> stringRes(R.string.typebetter__update_up_to_date, "current" to current)
        is UpdateState.Available ->
            stringRes(R.string.typebetter__update_available, "new" to newer(s.availableVersionCode), "current" to current)
        is UpdateState.AvailableInStore ->
            stringRes(R.string.typebetter__update_available_store, "new" to newer(s.availableVersionCode), "current" to current)
        is UpdateState.InProgress -> if (s.resumable) stringRes(R.string.typebetter__update_resume_summary) else s.percent
            ?.let { stringRes(R.string.typebetter__update_in_progress_percent, "percent" to it) }
            ?: stringRes(R.string.typebetter__update_in_progress)
        UpdateState.ReadyToInstall -> stringRes(R.string.typebetter__update_ready)
        UpdateState.NotFromPlay -> stringRes(R.string.typebetter__update_not_from_play, "current" to current)
        UpdateState.PlayCannotUpdateHere -> stringRes(R.string.typebetter__update_store_only, "current" to current)
        UpdateState.Failed -> stringRes(R.string.typebetter__update_failed, "current" to current)
    }
    val action: (() -> Unit)? = when (s) {
        // Nothing to do for a copy Play did not install: the row only explains.
        UpdateState.Checking, UpdateState.NotFromPlay -> null
        UpdateState.UpToDate, UpdateState.Failed -> fun() { check() }
        is UpdateState.Available -> fun() { startUpdate(s.type) }
        is UpdateState.AvailableInStore, UpdateState.PlayCannotUpdateHere ->
            fun() { if (!openPlayStorePage(context)) state = UpdateState.Failed }
        // Only an immediate update left running is resumed (Play's rule); a background download keeps going.
        is UpdateState.InProgress -> fun() {
            val running = info
            if (s.resumable && running?.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startUpdate(AppUpdateType.IMMEDIATE)
            } else {
                check()
            }
        }
        UpdateState.ReadyToInstall -> fun() {
            manager.completeUpdate().addOnFailureListener { e ->
                state = updateStateForError((e as? InstallException)?.errorCode)
            }
        }
    }

    TbRow(
        title = stringRes(R.string.typebetter__update_title),
        modifier = modifier,
        summary = summary,
        icon = Icons.Outlined.SystemUpdate,
        onClick = action,
        trailing = {
            when (s) {
                is UpdateState.InProgress -> if (s.resumable) {
                    FilledTonalButton(onClick = { action?.invoke() }) { Text(stringRes(R.string.typebetter__update_resume)) }
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                UpdateState.Checking ->
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                is UpdateState.Available, is UpdateState.AvailableInStore, UpdateState.PlayCannotUpdateHere ->
                    FilledTonalButton(onClick = { action?.invoke() }) { Text(stringRes(R.string.typebetter__update_button)) }
                UpdateState.ReadyToInstall ->
                    FilledTonalButton(onClick = { action?.invoke() }) { Text(stringRes(R.string.typebetter__update_restart)) }
                UpdateState.UpToDate, UpdateState.Failed ->
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                UpdateState.NotFromPlay -> Unit
            }
        },
    )
}

private const val CHECK_TIMEOUT_MS = 20_000L

/** The install statuses the listener turns into a state of their own (so a pending check may be dropped). */
private val LIVE_STATUSES = setOf(
    InstallStatus.DOWNLOADED, InstallStatus.PENDING, InstallStatus.DOWNLOADING, InstallStatus.INSTALLING, InstallStatus.FAILED,
)

private fun AppUpdateInfo.toPlayAnswer() = PlayUpdateAnswer(
    updateAvailability = updateAvailability(),
    installStatus = installStatus(),
    availableVersionCode = availableVersionCode().toLong(),
    immediateAllowed = isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
    flexibleAllowed = isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
    bytesDownloaded = bytesDownloaded(),
    totalBytesToDownload = totalBytesToDownload(),
)

/** The app that installed TypeBetter (the Play Store is "com.android.vending"), or null if unknown. */
private fun installerOf(context: Context): String? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getInstallerPackageName(context.packageName)
    }
} catch (e: Exception) {
    null
}

/** Opens TypeBetter's page in the Play Store app, else in the browser. False when neither can open. */
private fun openPlayStorePage(context: Context): Boolean {
    val pkg = context.packageName
    val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        .setPackage(PLAY_STORE_PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    for (intent in listOf(store, web)) {
        try {
            context.startActivity(intent)
            return true
        } catch (e: ActivityNotFoundException) {
            // try the next one
        }
    }
    return false
}
