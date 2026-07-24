package com.limelight.ligase.library

import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import java.util.Locale

data class ManualLibraryOrderEntry(
    val uuid: String,
    val name: String,
    val locked: Boolean,
)

data class ManualLibraryOrderDraft(
    val originalEntries: List<ManualLibraryOrderEntry>,
    val entries: List<ManualLibraryOrderEntry> = originalEntries,
) {
    val isDirty: Boolean
        get() = entries.map(ManualLibraryOrderEntry::uuid) !=
            originalEntries.map(ManualLibraryOrderEntry::uuid)

    val orderedUuids: List<String>
        get() = entries.map(ManualLibraryOrderEntry::uuid)

    val orderedPublishedUuids: List<String>
        get() = entries
            .map { it.uuid.lowercase(Locale.ROOT) }

    fun move(
        movingUuid: String,
        targetUuid: String,
    ): ManualLibraryOrderDraft {
        if (movingUuid == targetUuid) return this
        val moving = entries.firstOrNull { it.uuid == movingUuid } ?: return this
        val target = entries.firstOrNull { it.uuid == targetUuid } ?: return this
        val reordered = entries.toMutableList()
        val fromIndex = reordered.indexOfFirst { it.uuid == movingUuid }
        val targetIndex = reordered.indexOfFirst { it.uuid == targetUuid }
        if (fromIndex < 0 || targetIndex < 0) return this
        val moved = reordered.removeAt(fromIndex)
        reordered.add(targetIndex, moved)
        return copy(entries = reordered)
    }

    fun adjacentMovableUuid(
        uuid: String,
        direction: Int,
    ): String? {
        if (direction == 0) return null
        val index = entries.indexOfFirst { it.uuid == uuid }
        if (index < 0) return null
        return entries.getOrNull(index + direction.coerceIn(-1, 1))?.uuid
    }

    companion object {
        fun fromLibraryItems(items: List<LigaseLibraryItem>): ManualLibraryOrderDraft {
            val entries = items.mapNotNull { item ->
                item.hostAppUuid?.let { uuid ->
                    ManualLibraryOrderEntry(
                        uuid = uuid,
                        name = item.name,
                        locked = false,
                    )
                }
            }
            return ManualLibraryOrderDraft(originalEntries = entries)
        }
    }
}
