package com.limelight.ligase.feature.input.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.limelight.R
import com.limelight.ligase.LigaseComposeTheme
import com.limelight.ligase.LigaseThemeMode
import com.limelight.ligase.feature.input.application.GameInputOverrideEditorAction
import com.limelight.ligase.feature.input.application.GameInputOverrideEditorState
import com.limelight.ligase.feature.input.application.GameInputOverrideTarget
import com.limelight.ligase.input.LigaseCanonicalGameUuid
import com.limelight.ligase.input.LigaseCloudTouchMode
import com.limelight.ligase.input.LigaseTouchOverlayMode

internal class GameInputOverrideDialogFragment : DialogFragment() {
    private var request: GameInputOverrideEditorState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = arguments?.let(::requestFrom)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        ComposeView(requireContext()).apply {
            val value = request ?: return@apply
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LigaseComposeTheme(themeModeFrom(requireArguments())) {
                    Box(
                        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                            .imePadding().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        GameInputOverrideEditor(value, ::publish, ::dismiss)
                    }
                }
            }
        }

    override fun onStart() {
        super.onStart()
        if (request == null) {
            dismissAllowingStateLoss()
            return
        }
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    private fun publish(action: GameInputOverrideEditorAction) {
        parentFragmentManager.setFragmentResult(RESULT_KEY, resultBundle(action))
        dismiss()
    }

    companion object {
        internal const val RESULT_KEY = "ligase.gameInputOverride.result"
        private const val TAG = "ligase.gameInputOverride.dialog"
        private const val UUID = "uuid"
        private const val TITLE = "title"
        private const val IDENTITY = "identity"
        private const val USE_GLOBAL = "useGlobal"
        private const val OVERLAY = "overlay"
        private const val CLOUD = "cloud"
        private const val WRITABLE = "writable"
        private const val ACTION = "action"
        private const val THEME = "theme"

        fun show(manager: FragmentManager, request: GameInputOverrideEditorState, theme: LigaseThemeMode) {
            if (manager.findFragmentByTag(TAG) != null) return
            if (LigaseCanonicalGameUuid.parse(request.target.gameUuid) == null) return
            GameInputOverrideDialogFragment().apply {
                arguments = requestBundle(request, theme)
            }.show(manager, TAG)
        }

        internal fun resultFrom(bundle: Bundle): GameInputOverrideEditorAction? {
            val uuid = LigaseCanonicalGameUuid.parse(bundle.getString(UUID)) ?: return null
            return when (bundle.getString(ACTION)) {
                "clear" -> GameInputOverrideEditorAction.Clear(uuid)
                "save" -> GameInputOverrideEditorAction.Save(
                    uuid,
                    bundle.getString(OVERLAY)?.let(LigaseTouchOverlayMode::fromStoredValue)
                        ?: return null,
                    bundle.getString(CLOUD)?.let(LigaseCloudTouchMode::fromStoredValue)
                        ?: return null,
                )
                else -> null
            }
        }

        internal fun resultBundle(action: GameInputOverrideEditorAction) = Bundle().apply {
            val uuid = when (action) {
                is GameInputOverrideEditorAction.Clear -> action.gameUuid
                is GameInputOverrideEditorAction.Save -> action.gameUuid
            }
            putString(UUID, uuid)
            when (action) {
                is GameInputOverrideEditorAction.Clear -> putString(ACTION, "clear")
                is GameInputOverrideEditorAction.Save -> {
                    putString(ACTION, "save")
                    putString(OVERLAY, action.overlayMode.storedValue)
                    putString(CLOUD, action.cloudTouchMode.storedValue)
                }
            }
        }

        private fun requestBundle(request: GameInputOverrideEditorState, theme: LigaseThemeMode) =
            Bundle().apply {
                putString(UUID, request.target.gameUuid)
                putString(TITLE, request.target.title)
                putString(IDENTITY, request.target.safeIdentity)
                putBoolean(USE_GLOBAL, request.useGlobal)
                putString(OVERLAY, request.overlayMode.storedValue)
                putString(CLOUD, request.cloudTouchMode.storedValue)
                putBoolean(WRITABLE, request.writable)
                putString(THEME, theme.name)
            }

        private fun requestFrom(bundle: Bundle): GameInputOverrideEditorState? {
            val uuid = LigaseCanonicalGameUuid.parse(bundle.getString(UUID)) ?: return null
            return GameInputOverrideEditorState(
                target = GameInputOverrideTarget(
                    uuid,
                    bundle.getString(TITLE) ?: return null,
                    bundle.getString(IDENTITY),
                ),
                useGlobal = bundle.getBoolean(USE_GLOBAL),
                overlayMode = bundle.getString(OVERLAY)?.let(LigaseTouchOverlayMode::fromStoredValue) ?: return null,
                cloudTouchMode = bundle.getString(CLOUD)?.let(LigaseCloudTouchMode::fromStoredValue) ?: return null,
                writable = bundle.getBoolean(WRITABLE),
            )
        }

        private fun themeModeFrom(bundle: Bundle) = bundle.getString(THEME)
            ?.let { name -> LigaseThemeMode.entries.firstOrNull { it.name == name } }
            ?: LigaseThemeMode.SYSTEM
    }
}

@Composable
private fun GameInputOverrideEditor(
    request: GameInputOverrideEditorState,
    onAction: (GameInputOverrideEditorAction) -> Unit,
    onCancel: () -> Unit,
) {
    var useGlobal by remember { mutableStateOf(request.useGlobal) }
    var overlay by remember { mutableStateOf(request.overlayMode) }
    var cloud by remember { mutableStateOf(request.cloudTouchMode) }
    Surface(
        Modifier.fillMaxWidth().widthIn(max = 560.dp).heightIn(max = 720.dp).fillMaxHeight(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 6.dp,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(request.target.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            request.target.safeIdentity?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.ligase_game_input_override_summary))
                FilterChip(
                    selected = useGlobal,
                    onClick = { useGlobal = true },
                    label = { Text(stringResource(R.string.ligase_game_input_use_global)) },
                )
                FilterChip(
                    selected = !useGlobal,
                    onClick = { useGlobal = false },
                    label = { Text(stringResource(R.string.ligase_game_input_custom)) },
                )
                LigaseTouchOverlayMode.entries.forEach { mode ->
                    FilterChip(
                        selected = !useGlobal && overlay == mode,
                        enabled = !useGlobal && request.writable,
                        onClick = { overlay = mode },
                        label = { Text(mode.name.replace('_', ' ')) },
                    )
                }
                LigaseCloudTouchMode.entries.forEach { mode ->
                    FilterChip(
                        selected = !useGlobal && cloud == mode,
                        enabled = !useGlobal && request.writable,
                        onClick = { cloud = mode },
                        label = { Text(mode.name.replace('_', ' ')) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
                if (!request.useGlobal) {
                    OutlinedButton(
                        enabled = request.writable,
                        onClick = { onAction(GameInputOverrideEditorAction.Clear(request.target.gameUuid)) },
                    ) { Text(stringResource(R.string.ligase_game_input_clear)) }
                }
                Button(
                    enabled = request.writable,
                    onClick = {
                        if (useGlobal) onAction(GameInputOverrideEditorAction.Clear(request.target.gameUuid))
                        else onAction(GameInputOverrideEditorAction.Save(request.target.gameUuid, overlay, cloud))
                    },
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}
