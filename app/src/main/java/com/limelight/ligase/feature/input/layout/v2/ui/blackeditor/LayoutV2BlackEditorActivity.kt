package com.limelight.ligase.feature.input.layout.v2.ui.blackeditor

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.limelight.R
import com.limelight.ligase.feature.input.layout.v2.application.LayoutV2EditorActivityViewModel

/**
 * Internal landscape shell for the v2 TouchKit editor.
 *
 * The Intent contains only an opaque draft ID. The Activity ViewModel is the
 * exclusive owner of the journal-backed editor session.
 */
class LayoutV2BlackEditorActivity : AppCompatActivity() {
    private val editorViewModel: LayoutV2EditorActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        hideSystemUi()

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        val loading = TextView(this).apply {
            setText(R.string.title_touchkit_layout_editor)
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
        }
        root.addView(
            loading,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(root)

        // Force creation now so invalid/missing/duplicate handoffs fail closed
        // before an editor surface is attached.
        editorViewModel.handoff.value
    }

    override fun onStop() {
        editorViewModel.onStop()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun hideSystemUi() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    companion object {
        @JvmStatic
        fun createIntent(context: Context, draftId: String): Intent =
            Intent(context, LayoutV2BlackEditorActivity::class.java).putExtra(
                LayoutV2EditorActivityViewModel.EXTRA_LAYOUT_V2_DRAFT_ID,
                draftId,
            )
    }
}
