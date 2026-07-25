package com.limelight.ligase.feature.input.layout.v3.ui.blackeditor

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import com.limelight.ligase.feature.input.layout.v3.application.LayoutV3EditorActivityViewModel
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorLaunchMode
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorLaunchRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LayoutV3BlackEditorActivityTest {
    @Test
    fun manifestResolvesExactLandscapeNonExportedActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val component = ComponentName(context, LayoutV3BlackEditorActivity::class.java)
        val activityInfo = context.packageManager.getActivityInfo(component, 0)

        assertEquals(LayoutV3BlackEditorActivity::class.java.name, activityInfo.name)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activityInfo.screenOrientation)
        assertFalse(activityInfo.exported)
    }

    @Test
    fun newIntentContainsOnlyClosedModeAndOptionalDisplayName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = LayoutV3BlackEditorActivity.createIntent(
            context,
            LayoutV3EditorLaunchRequest(LayoutV3EditorLaunchMode.NEW_V3, displayName = "Local"),
        )

        assertEquals(LayoutV3BlackEditorActivity::class.java.name, intent.component?.className)
        assertEquals(
            LayoutV3EditorLaunchMode.NEW_V3.name,
            intent.getStringExtra(LayoutV3EditorActivityViewModel.EXTRA_LAYOUT_V3_MODE),
        )
        assertEquals(
            "Local",
            intent.getStringExtra(LayoutV3EditorActivityViewModel.EXTRA_LAYOUT_V3_DISPLAY_NAME),
        )
        assertFalse(intent.hasExtra(LayoutV3EditorActivityViewModel.EXTRA_LAYOUT_V3_DRAFT_ID))
        assertFalse(intent.hasExtra("state"))
        assertFalse(intent.hasExtra("path"))
        assertFalse(intent.hasExtra("hash"))
    }

    @Test
    fun existingIntentContainsOnlyClosedModeAndOpaqueDraftId() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val draftId = "123e4567-e89b-42d3-a456-426614174000"
        val intent = LayoutV3BlackEditorActivity.createIntent(
            context,
            LayoutV3EditorLaunchRequest(LayoutV3EditorLaunchMode.EXISTING_V3, draftId),
        )

        assertEquals(
            setOf(
                LayoutV3EditorActivityViewModel.EXTRA_LAYOUT_V3_MODE,
                LayoutV3EditorActivityViewModel.EXTRA_LAYOUT_V3_DRAFT_ID,
            ),
            intent.extras?.keySet(),
        )
        assertEquals(draftId, intent.getStringExtra(
            LayoutV3EditorActivityViewModel.EXTRA_LAYOUT_V3_DRAFT_ID,
        ))
    }
}
