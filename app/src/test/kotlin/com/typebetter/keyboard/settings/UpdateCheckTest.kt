package com.typebetter.keyboard.settings

import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Check for updates" on the home screen: what each answer from Google Play turns into. */
class UpdateCheckTest {
    private fun answer(
        availability: Int = UpdateAvailability.UPDATE_NOT_AVAILABLE,
        status: Int = InstallStatus.UNKNOWN,
        code: Long = 142_148L,
        immediate: Boolean = false,
        flexible: Boolean = false,
        downloaded: Long = 0,
        total: Long = 0,
    ) = PlayUpdateAnswer(availability, status, code, immediate, flexible, downloaded, total)

    @Test
    fun `build number is the run number on top of 142000`() {
        assertEquals(147L, tbBuildNumber(142_147L))
        assertNull(tbBuildNumber(142L), "a local build's plain version code is not a TypeBetter build")
        assertNull(tbBuildNumber(142_000L))
    }

    @Test
    fun `nothing newer on Play is up to date`() {
        assertEquals(UpdateState.UpToDate, updateStateFor(answer()))
    }

    @Test
    fun `a newer build installs in the app, immediate preferred`() {
        assertEquals(
            UpdateState.Available(142_148L, AppUpdateType.IMMEDIATE),
            updateStateFor(answer(UpdateAvailability.UPDATE_AVAILABLE, immediate = true, flexible = true)),
        )
        assertEquals(
            UpdateState.Available(142_148L, AppUpdateType.FLEXIBLE),
            updateStateFor(answer(UpdateAvailability.UPDATE_AVAILABLE, flexible = true)),
        )
    }

    @Test
    fun `a newer build Play will not install in the app opens the Play Store`() {
        assertEquals(UpdateState.AvailableInStore(142_148L), updateStateFor(answer(UpdateAvailability.UPDATE_AVAILABLE)))
    }

    @Test
    fun `an update already running shows its progress`() {
        assertEquals(
            UpdateState.InProgress(null),
            updateStateFor(answer(UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS, immediate = true)),
        )
        assertEquals(
            UpdateState.InProgress(25),
            updateStateFor(answer(UpdateAvailability.UPDATE_AVAILABLE, InstallStatus.DOWNLOADING, downloaded = 25, total = 100)),
        )
    }

    @Test
    fun `a finished background download asks for a restart`() {
        assertEquals(
            UpdateState.ReadyToInstall,
            updateStateFor(answer(UpdateAvailability.UPDATE_AVAILABLE, InstallStatus.DOWNLOADED, flexible = true)),
        )
    }

    @Test
    fun `an unknown answer is a failed check that a tap retries`() {
        assertEquals(UpdateState.Failed, updateStateFor(answer(UpdateAvailability.UNKNOWN)))
    }

    @Test
    fun `download percent stays in range and waits for a size`() {
        assertNull(downloadPercent(10, 0))
        assertEquals(0, downloadPercent(-5, 100))
        assertEquals(50, downloadPercent(50, 100))
        assertEquals(100, downloadPercent(150, 100))
    }

    @Test
    fun `a copy Play does not own says so instead of retrying forever`() {
        assertEquals(UpdateState.NotFromPlay, updateStateForError(InstallErrorCode.ERROR_APP_NOT_OWNED))
        assertEquals(UpdateState.NotFromPlay, updateStateForError(InstallErrorCode.ERROR_API_NOT_AVAILABLE))
        assertEquals(UpdateState.Failed, updateStateForError(InstallErrorCode.ERROR_INTERNAL_ERROR))
        assertEquals(UpdateState.Failed, updateStateForError(null))
    }

    @Test
    fun `only the Play Store counts as installed by Play`() {
        assertTrue(installedByPlay("com.android.vending"))
        assertFalse(installedByPlay(null))
        assertFalse(installedByPlay("com.google.android.packageinstaller"))
    }
}
