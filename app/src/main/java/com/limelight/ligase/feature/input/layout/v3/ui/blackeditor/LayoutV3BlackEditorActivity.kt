package com.limelight.ligase.feature.input.layout.v3.ui.blackeditor

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.limelight.ligase.LigaseComposeTheme
import com.limelight.ligase.LigaseThemeMode
import com.limelight.ligase.feature.input.layout.v3.application.LayoutV3EditorActivityViewModel
import com.limelight.ligase.feature.input.layout.v3.domain.EditorTargetViewport
import com.limelight.ligase.feature.input.layout.v3.domain.LayoutOrientation
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorExitCode
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorLaunchMode
import com.limelight.ligase.feature.input.layout.v3.editor.LayoutV3EditorLaunchRequest

/**
 * Full-screen, landscape-only owner for editing one Touch Layout v3 draft.
 *
 * The Intent carries only the closed launch mode, an optional opaque draft ID,
 * and an optional display name. Canonical content remains in the application
 * owner and its journal.
 */
class LayoutV3BlackEditorActivity : AppCompatActivity() {
    private val editorViewModel: LayoutV3EditorActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        hideSystemUi()

        setContent {
            LigaseComposeTheme(LigaseThemeMode.DARK) {
                val workspace by editorViewModel.state.collectAsState()
                val handoff by editorViewModel.handoff.collectAsState()
                LayoutV3BlackEditorScreen(
                    workspace = workspace,
                    handoff = handoff,
                    onFullOverlayReady = { widthPx, heightPx ->
                        editorViewModel.initializeNewV3(
                            EditorTargetViewport(
                                widthPx,
                                heightPx,
                                LayoutOrientation.LANDSCAPE,
                            ),
                        )
                    },
                    onSelect = editorViewModel::selectElement,
                    onBeginGesture = editorViewModel::beginGesture,
                    onCommitPixelMove = editorViewModel::commitPixelMove,
                    onCommitPixelResize = editorViewModel::commitPixelResize,
                    onCancelGesture = editorViewModel::cancelGesture,
                    onNudge = editorViewModel::nudgeElement,
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

    private fun finishIfSuccessful(code: LayoutV3EditorExitCode) {
        if (code != LayoutV3EditorExitCode.FAILED) {
            setResult(RESULT_OK)
            finish()
        }
    }

    companion object {
        @JvmStatic
        fun createIntent(context: Context, request: LayoutV3EditorLaunchRequest): Intent =
            Intent(context, LayoutV3BlackEditorActivity::class.java).apply {
                putExtra(
                    LayoutV3EditorActivityViewModel.EXTRA_LAYOUT_V3_MODE,
                    request.mode.name,
                )
                when (request.mode) {
                    LayoutV3EditorLaunchMode.NEW_V3 ->
                        request.displayName?.let {
                            putExtra(
                                LayoutV3EditorActivityViewModel.EXTRA_LAYOUT_V3_DISPLAY_NAME,
                                it,
                            )
                        }
                    LayoutV3EditorLaunchMode.EXISTING_V3 ->
                        putExtra(
                            LayoutV3EditorActivityViewModel.EXTRA_LAYOUT_V3_DRAFT_ID,
                            requireNotNull(request.draftId),
                        )
                }
            }
    }
}
