package com.limelight.ligase.feature.library.ui.settings

import androidx.fragment.app.FragmentActivity
import com.limelight.ligase.LigaseThemeMode
import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StreamingResolutionDialogFragmentTest {
    @Test
    fun `back or outside cancel emits no action result`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        var results = 0
        activity.supportFragmentManager.setFragmentResultListener(
            StreamingResolutionDialogFragment.RESULT_KEY,
            activity,
        ) { _, _ -> results++ }

        StreamingResolutionDialogFragment.show(
            activity.supportFragmentManager,
            request(),
            LigaseThemeMode.SYSTEM,
        )
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.fragments
            .single() as StreamingResolutionDialogFragment

        fragment.requireDialog().cancel()
        activity.supportFragmentManager.executePendingTransactions()

        assertEquals(0, results)
    }

    private fun request() = StreamingResolutionEditorRequest(
        target = StreamingResolutionTarget.GLOBAL,
        title = "Global resolution",
        hostKey = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
        baseRevision = 7,
        initialResolution = LigaseResolutionDto(1920, 1080),
    )
}
