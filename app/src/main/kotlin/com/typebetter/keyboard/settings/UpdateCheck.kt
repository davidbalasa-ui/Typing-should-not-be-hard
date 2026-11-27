package com.typebetter.keyboard.settings

import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/*
 * "Check for updates" (docs/decisions/2026-11-27-check-for-updates.md): what the home row says and does,
 * worked out from Google Play's answer. Kept free of Android calls so every case is unit tested; the row
 * itself (UpdateRow.kt) only asks Play and hands the answer here.
 */

/** Every main build's version code is 142000 + its GitHub run number, so the run number is the "build". */
internal const val TB_VERSION_CODE_BASE = 142_000L

/** The build number David sees ("build 148"), or null for a version code that is not a TypeBetter build. */
internal fun tbBuildNumber(versionCode: Long): Long? =
    (versionCode - TB_VERSION_CODE_BASE).takeIf { versionCode > TB_VERSION_CODE_BASE }

internal sealed interface UpdateState {
    /** Asking Google Play. */
    data object Checking : UpdateState

    /** Play has nothing newer than this phone's copy. */
    data object UpToDate : UpdateState

    /** A newer build is on Play and can be installed from inside the app with [type] (an [AppUpdateType]). */
    data class Available(val availableVersionCode: Long, val type: Int) : UpdateState

    /** A newer build is on Play but Play will not install it from inside the app: open the Play Store page. */
    data class AvailableInStore(val availableVersionCode: Long) : UpdateState

    /** An update started earlier is still going (downloading or installing). */
    data class InProgress(val percent: Int?) : UpdateState

    /** A background download finished: one tap restarts the app on the new build. */
    data object ReadyToInstall : UpdateState

    /** This copy did not come from Google Play (a GitHub APK or a debug build), so Play cannot update it. */
    data object NotFromPlay : UpdateState

    /** Play could not be reached or refused; tapping tries again. */
    data object Failed : UpdateState
}

/** Play's answer to "is there an update?", as plain values (the fields of AppUpdateInfo this needs). */
internal data class PlayUpdateAnswer(
    val updateAvailability: Int,
    val installStatus: Int,
    val availableVersionCode: Long,
    val immediateAllowed: Boolean,
    val flexibleAllowed: Boolean,
    val bytesDownloaded: Long = 0,
    val totalBytesToDownload: Long = 0,
)

/**
 * The row's state for Play's answer. An immediate update is preferred: Play shows its own full-screen
 * progress and restarts TypeBetter on the new build, so nothing is left half done. A background
 * (flexible) download is the fallback when Play only allows that.
 */
internal fun updateStateFor(answer: PlayUpdateAnswer): UpdateState = when {
    answer.installStatus == InstallStatus.DOWNLOADED -> UpdateState.ReadyToInstall
    answer.updateAvailability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ||
        answer.installStatus == InstallStatus.PENDING ||
        answer.installStatus == InstallStatus.DOWNLOADING ||
        answer.installStatus == InstallStatus.INSTALLING ->
        UpdateState.InProgress(downloadPercent(answer.bytesDownloaded, answer.totalBytesToDownload))
    answer.updateAvailability == UpdateAvailability.UPDATE_AVAILABLE -> when {
        answer.immediateAllowed -> UpdateState.Available(answer.availableVersionCode, AppUpdateType.IMMEDIATE)
        answer.flexibleAllowed -> UpdateState.Available(answer.availableVersionCode, AppUpdateType.FLEXIBLE)
        else -> UpdateState.AvailableInStore(answer.availableVersionCode)
    }
    answer.updateAvailability == UpdateAvailability.UPDATE_NOT_AVAILABLE -> UpdateState.UpToDate
    // UNKNOWN: Play did not say. Treated as a failed check so a tap asks again.
    else -> UpdateState.Failed
}

/** 0–100, or null while Play has not said how big the download is. */
internal fun downloadPercent(downloaded: Long, total: Long): Int? =
    if (total <= 0) null else ((downloaded.coerceIn(0, total) * 100) / total).toInt()

/**
 * The state for an error from Play. "Not owned" and "API not available" mean this copy was not installed
 * by Play for this account (a GitHub APK, a debug build, or another Google account on the Play Store),
 * which retrying cannot fix.
 */
internal fun updateStateForError(errorCode: Int?): UpdateState = when (errorCode) {
    InstallErrorCode.ERROR_APP_NOT_OWNED, InstallErrorCode.ERROR_API_NOT_AVAILABLE -> UpdateState.NotFromPlay
    else -> UpdateState.Failed
}

/** True when the Play Store installed this copy; other installers (or none) mean Play cannot update it. */
internal fun installedByPlay(installerPackage: String?): Boolean = installerPackage == PLAY_STORE_PACKAGE

internal const val PLAY_STORE_PACKAGE = "com.android.vending"
