package com.limelight.ligase.feature.input.layout.v2.ui.blackeditor

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.limelight.ligase.LigaseComposeTheme
import com.limelight.ligase.LigaseThemeMode
import com.limelight.ligase.feature.input.layout.v2.application.LayoutV2EditorActivityViewModel
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorExitCode

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

        setContent {
            LigaseComposeTheme(LigaseThemeMode.DARK) {
                val workspace by editorViewModel.state.collectAsState()
                val handoff by editorViewModel.handoff.collectAsState()
                LayoutV2BlackEditorScreen(
                    workspace = workspace,
                    handoff = handoff,
                    onSelect = editorViewModel::selectElement,
                    onMove = editorViewModel::moveElement,
                    onResize = editorViewModel::resizeElement,
                    onSetAnchors = editorViewModel::setAnchors,
                    onSetZOrder = editorViewModel::setZOrder,
                    onDelete = editorViewModel::deleteElement,
                    onUpdateProperties = editorViewModel::updateProperties,
                    onAdd = editorViewModel::addElement,
                    onValidate = editorViewModel::validate,
                    onKeepAndFinish = {
                        finishIfSuccessful(editorViewModel.keepDraftAndFinish().code)
                    },
                    onSaveAndFinish = {
                        finishIfSuccessful(editorViewModel.saveAndFinish().code)
                    },
                    onDiscardAndFinish = {
                        finishIfSuccessful(editorViewModel.discardAndFinish().code)
                    },
                    onAbort = ::finish,
                )
            }
        }
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

    private fun finishIfSuccessful(code: LayoutV2EditorExitCode) {
        if (code != LayoutV2EditorExitCode.FAILED) {
            setResult(RESULT_OK)
            finish()
        }
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
