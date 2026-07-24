package com.limelight.ligase.feature.layout.editor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.limelight.ligase.feature.layout.data.LayoutCatalogRepository
import com.limelight.ligase.feature.layout.data.LayoutRepositoryResult
import com.limelight.ligase.feature.layout.domain.LayoutCatalogUiState
import com.limelight.ligase.feature.layout.domain.LayoutControlKind
import com.limelight.ligase.feature.layout.domain.LayoutEditorSaveResult
import com.limelight.ligase.feature.layout.domain.LayoutEditorSessionState
import com.limelight.ligase.input.LigaseTouchLayout
import com.limelight.ligase.input.LigaseTouchLayoutRepository

class LayoutWorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val catalogRepository = LayoutCatalogRepository(application)
    private val selectionRepository = LigaseTouchLayoutRepository(application)
    private val editorSession = LayoutEditorSession(catalogRepository)

    var catalogState by mutableStateOf(LayoutCatalogUiState())
        private set

    var editorState by mutableStateOf(LayoutEditorSessionState())
        private set

    init {
        refreshCatalog()
    }

    fun refreshCatalog() {
        catalogState = catalogState.copy(loading = true, error = null)
        when (val result = catalogRepository.catalog()) {
            is LayoutRepositoryResult.Success -> {
                val available = result.value.map { LigaseTouchLayout(it.layoutId, it.displayName) }
                catalogState = LayoutCatalogUiState(
                    items = result.value,
                    selectedLayoutId = selectionRepository.initializeSelection(available),
                )
            }
            is LayoutRepositoryResult.Failure -> {
                catalogState = catalogState.copy(loading = false, error = result.error)
            }
        }
    }

    fun selectGlobal(layoutId: String): Boolean {
        val available = catalogState.items.map { LigaseTouchLayout(it.layoutId, it.displayName) }
        val selected = selectionRepository.select(layoutId, available)
        if (selected) catalogState = catalogState.copy(selectedLayoutId = layoutId, error = null)
        return selected
    }

    fun createEditableCopy(sourceLayoutId: String): Boolean =
        editorSession.createEditableCopy(sourceLayoutId).also { publishEditor() }

    fun openEditor(layoutId: String): Boolean =
        editorSession.openEditor(layoutId).also { publishEditor() }

    fun moveElement(elementId: String, x: Float, y: Float): Boolean =
        editorSession.moveElement(elementId, x, y).also { publishEditor() }

    fun resizeElement(elementId: String, width: Float, height: Float): Boolean =
        editorSession.resizeElement(elementId, width, height).also { publishEditor() }

    fun deleteElement(elementId: String): Boolean =
        editorSession.deleteElement(elementId).also { publishEditor() }

    fun addElement(kind: LayoutControlKind): Boolean =
        editorSession.addElement(kind).also { publishEditor() }

    fun saveDraft(): LayoutEditorSaveResult? =
        editorSession.saveDraft().also {
            publishEditor()
            if (it != null) refreshCatalog()
        }

    fun discardDraft() {
        editorSession.discardDraft()
        publishEditor()
    }

    private fun publishEditor() {
        editorState = editorSession.state
    }
}
