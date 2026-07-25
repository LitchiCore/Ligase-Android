package com.limelight.ligase.feature.input.layout.v2.ui.blackeditor

import android.content.Context
import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import com.limelight.ligase.feature.input.layout.v2.application.LayoutV2EditorActivityViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LayoutV2BlackEditorActivityTest {
    @Test
    fun manifestResolvesTheExactIntentComponent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val component = ComponentName(context, LayoutV2BlackEditorActivity::class.java)
        val activityInfo = context.packageManager.getActivityInfo(component, 0)

        assertEquals(LayoutV2BlackEditorActivity::class.java.name, activityInfo.name)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activityInfo.screenOrientation)
        assertFalse(activityInfo.exported)
    }

    @Test
    fun createIntentCarriesOnlyOpaqueDraftId() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val draftId = "123e4567-e89b-42d3-a456-426614174000"

        val intent = LayoutV2BlackEditorActivity.createIntent(context, draftId)

        assertEquals(
            LayoutV2BlackEditorActivity::class.java.name,
            intent.component?.className,
        )
        assertEquals(
            draftId,
            intent.getStringExtra(LayoutV2EditorActivityViewModel.EXTRA_LAYOUT_V2_DRAFT_ID),
        )
        assertEquals(
            setOf(LayoutV2EditorActivityViewModel.EXTRA_LAYOUT_V2_DRAFT_ID),
            intent.extras?.keySet(),
        )
        assertFalse(intent.hasExtra("state"))
        assertFalse(intent.hasExtra("path"))
        assertFalse(intent.hasExtra("hash"))
    }
}
