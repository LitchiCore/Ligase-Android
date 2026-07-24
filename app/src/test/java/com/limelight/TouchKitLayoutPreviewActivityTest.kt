package com.limelight

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [com.limelight.shadows.ShadowMoonBridge::class])
class TouchKitLayoutPreviewActivityTest {
    private lateinit var context: Context
    private lateinit var layoutId: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("touchkit_layout_registry", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("touchkit_layout_names", Context.MODE_PRIVATE)
            .edit().clear().commit()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        layoutId = TouchKitLayoutNames.getValues(context).single()
        context.getSharedPreferences(layoutId, Context.MODE_PRIVATE)
            .edit().clear().putString("future_field", "keep-me").commit()
    }

    @Test
    fun `preview uses real renderer without mutating preferences or enabling input`() {
        val layoutBefore = context.getSharedPreferences(layoutId, Context.MODE_PRIVATE).all.toMap()
        val defaultsBefore = PreferenceManager.getDefaultSharedPreferences(context).all.toMap()
        val activityController = Robolectric.buildActivity(
            TouchKitLayoutPreviewActivity::class.java,
            TouchKitLayoutPreviewActivity.createIntent(context, layoutId),
        ).setup()
        val activity = activityController.get()

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.requestedOrientation)
        assertEquals(
            layoutBefore,
            context.getSharedPreferences(layoutId, Context.MODE_PRIVATE).all.toMap(),
        )
        assertEquals(defaultsBefore, PreferenceManager.getDefaultSharedPreferences(context).all.toMap())

        val controllerField = TouchKitLayoutPreviewActivity::class.java
            .getDeclaredField("controller").apply { isAccessible = true }
        val touchController = controllerField.get(activity) as KeyBoardController
        assertTrue(touchController.elements.isNotEmpty())
        assertTrue(touchController.elements.all { !it.isEnabled && !it.isClickable && !it.isLongClickable })
        activityController.close()
    }

    @Test
    fun `unknown layout finishes without constructing renderer`() {
        val activity = Robolectric.buildActivity(
            TouchKitLayoutPreviewActivity::class.java,
            TouchKitLayoutPreviewActivity.createIntent(context, "missing-layout"),
        ).create().get()

        assertTrue(activity.isFinishing)
        val controllerField = TouchKitLayoutPreviewActivity::class.java
            .getDeclaredField("controller").apply { isAccessible = true }
        assertFalse(controllerField.get(activity) is KeyBoardController)
    }
}
