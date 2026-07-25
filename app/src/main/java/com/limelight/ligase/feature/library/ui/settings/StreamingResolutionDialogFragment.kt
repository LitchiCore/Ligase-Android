package com.limelight.ligase.feature.library.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.limelight.ligase.LigaseComposeTheme
import com.limelight.ligase.LigaseThemeMode
import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto

internal class StreamingResolutionDialogFragment : DialogFragment() {
    private val request: StreamingResolutionEditorRequest
        get() = requireNotNull(requestFrom(requireArguments()))

    private var resultGate = StreamingResolutionResultGate()
    private var currentDraft: StreamingResolutionDraft? = null
    private var currentInvalid = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultGate = StreamingResolutionResultGate(
            savedInstanceState?.getBoolean(STATE_RESULT_SENT) ?: false,
        )
        currentDraft = savedInstanceState?.let(::draftFrom)
        currentInvalid = savedInstanceState?.getBoolean(STATE_INVALID) ?: false
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val initialDraft = currentDraft ?: StreamingResolutionDraft(
                widthText = request.initialResolution.width.toString(),
                heightText = request.initialResolution.height.toString(),
                useGlobal = request.useGlobal,
            ).also { currentDraft = it }
            var widthText by remember { mutableStateOf(initialDraft.widthText) }
            var heightText by remember {
                mutableStateOf(initialDraft.heightText)
            }
            var useGlobal by remember { mutableStateOf(initialDraft.useGlobal) }
            var invalid by remember { mutableStateOf(currentInvalid) }
            val draft = StreamingResolutionDraft(widthText, heightText, useGlobal)
            currentDraft = draft
            currentInvalid = invalid
            LigaseComposeTheme(themeModeFrom(requireArguments())) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    StreamingResolutionEditor(
                        request = request,
                        draft = draft,
                        invalid = invalid,
                        onDraftChanged = { updated ->
                            widthText = updated.widthText
                            heightText = updated.heightText
                            useGlobal = updated.useGlobal
                            invalid = false
                            currentDraft = updated
                            currentInvalid = false
                        },
                        onSave = {
                            when (val parsed = parseStreamingResolutionDraft(request, draft)) {
                                StreamingResolutionDraftResult.Invalid -> {
                                    invalid = true
                                    currentInvalid = true
                                }
                                is StreamingResolutionDraftResult.Ready -> {
                                    publishResult(parsed.resolution)
                                }
                            }
                        },
                        onCancel = ::dismiss,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_RESULT_SENT, resultGate.sent)
        currentDraft?.let { putDraft(outState, it) }
        outState.putBoolean(STATE_INVALID, currentInvalid)
        super.onSaveInstanceState(outState)
    }

    private fun publishResult(resolution: LigaseResolutionDto?) {
        if (!resultGate.trySend()) return
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            resultBundle(request, resolution),
        )
        dismiss()
    }

    companion object {
        internal const val RESULT_KEY = "ligase.streamingResolution.result"
        private const val TAG = "ligase.streamingResolution.dialog"
        private const val STATE_RESULT_SENT = "resultSent"
        private const val STATE_WIDTH_TEXT = "widthText"
        private const val STATE_HEIGHT_TEXT = "heightText"
        private const val STATE_USE_GLOBAL = "draftUseGlobal"
        private const val STATE_INVALID = "invalid"
        private const val ARG_TARGET = "target"
        private const val ARG_TITLE = "title"
        private const val ARG_HOST_KEY = "hostKey"
        private const val ARG_BASE_REVISION = "baseRevision"
        private const val ARG_WIDTH = "width"
        private const val ARG_HEIGHT = "height"
        private const val ARG_APP_UUID = "appUuid"
        private const val ARG_USE_GLOBAL = "useGlobal"
        private const val ARG_THEME = "theme"
        private const val RESULT_HAS_RESOLUTION = "hasResolution"

        fun show(
            manager: FragmentManager,
            request: StreamingResolutionEditorRequest,
            themeMode: LigaseThemeMode,
        ) {
            if (manager.findFragmentByTag(TAG) != null) return
            StreamingResolutionDialogFragment().apply {
                arguments = requestBundle(request, themeMode)
            }.show(manager, TAG)
        }

        internal fun requestFrom(bundle: Bundle): StreamingResolutionEditorRequest? {
            val target = bundle.getString(ARG_TARGET)
                ?.let { value -> StreamingResolutionTarget.entries.firstOrNull { it.name == value } }
                ?: return null
            val title = bundle.getString(ARG_TITLE) ?: return null
            val hostKey = bundle.getString(ARG_HOST_KEY) ?: return null
            val width = bundle.getInt(ARG_WIDTH, -1)
            val height = bundle.getInt(ARG_HEIGHT, -1)
            val baseRevision = bundle.getLong(ARG_BASE_REVISION, -1)
            val resolution = LigaseResolutionDto(width, height)
            if (!resolution.isValid() || baseRevision < 1) return null
            val appUuid = bundle.getString(ARG_APP_UUID)
            if (target == StreamingResolutionTarget.APP && appUuid.isNullOrBlank()) return null
            return StreamingResolutionEditorRequest(
                target = target,
                title = title,
                hostKey = hostKey,
                baseRevision = baseRevision,
                initialResolution = resolution,
                appUuid = appUuid,
                useGlobal = target == StreamingResolutionTarget.APP &&
                    bundle.getBoolean(ARG_USE_GLOBAL),
            )
        }

        internal fun resultFrom(bundle: Bundle): StreamingResolutionEditResult? {
            val request = requestFrom(bundle) ?: return null
            val resolution = if (bundle.getBoolean(RESULT_HAS_RESOLUTION)) {
                val value = LigaseResolutionDto(
                    bundle.getInt(ARG_WIDTH, -1),
                    bundle.getInt(ARG_HEIGHT, -1),
                )
                value.takeIf { it.isValid() } ?: return null
            } else {
                null
            }
            if (request.target == StreamingResolutionTarget.GLOBAL && resolution == null) {
                return null
            }
            return StreamingResolutionEditResult(request, resolution)
        }

        internal fun putDraft(bundle: Bundle, draft: StreamingResolutionDraft) {
            bundle.putString(STATE_WIDTH_TEXT, draft.widthText)
            bundle.putString(STATE_HEIGHT_TEXT, draft.heightText)
            bundle.putBoolean(STATE_USE_GLOBAL, draft.useGlobal)
        }

        internal fun draftFrom(bundle: Bundle): StreamingResolutionDraft? {
            val width = bundle.getString(STATE_WIDTH_TEXT) ?: return null
            val height = bundle.getString(STATE_HEIGHT_TEXT) ?: return null
            return StreamingResolutionDraft(
                widthText = width,
                heightText = height,
                useGlobal = bundle.getBoolean(STATE_USE_GLOBAL),
            )
        }

        private fun requestBundle(
            request: StreamingResolutionEditorRequest,
            themeMode: LigaseThemeMode,
        ) = Bundle().apply {
            putString(ARG_TARGET, request.target.name)
            putString(ARG_TITLE, request.title)
            putString(ARG_HOST_KEY, request.hostKey)
            putLong(ARG_BASE_REVISION, request.baseRevision)
            putInt(ARG_WIDTH, request.initialResolution.width)
            putInt(ARG_HEIGHT, request.initialResolution.height)
            putString(ARG_APP_UUID, request.appUuid)
            putBoolean(ARG_USE_GLOBAL, request.useGlobal)
            putString(ARG_THEME, themeMode.name)
        }

        internal fun resultBundle(
            request: StreamingResolutionEditorRequest,
            resolution: LigaseResolutionDto?,
        ) = requestBundle(request, LigaseThemeMode.SYSTEM).apply {
            putBoolean(RESULT_HAS_RESOLUTION, resolution != null)
            resolution?.let {
                putInt(ARG_WIDTH, it.width)
                putInt(ARG_HEIGHT, it.height)
            }
        }

        private fun themeModeFrom(bundle: Bundle): LigaseThemeMode =
            bundle.getString(ARG_THEME)
                ?.let { value -> LigaseThemeMode.entries.firstOrNull { it.name == value } }
                ?: LigaseThemeMode.SYSTEM
    }
}

internal data class StreamingResolutionEditResult(
    val request: StreamingResolutionEditorRequest,
    val resolution: LigaseResolutionDto?,
)

internal class StreamingResolutionResultGate(
    sent: Boolean = false,
) {
    var sent: Boolean = sent
        private set

    fun trySend(): Boolean {
        if (sent) return false
        sent = true
        return true
    }
}
