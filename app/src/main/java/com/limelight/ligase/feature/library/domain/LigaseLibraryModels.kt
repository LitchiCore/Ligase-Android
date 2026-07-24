package com.limelight.ligase.feature.library.domain

import com.limelight.ligase.feature.library.data.dto.LigaseSyncSnapshotDto
import com.limelight.nvstream.http.NvApp
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

enum class HostLibraryKind(val wireValue: String) {
    DESKTOP("desktop"),
    VIRTUAL_DESKTOP("virtualDesktop"),
    STEAM("steam"),
    EXECUTABLE("executable");

    val isSystem: Boolean
        get() = this == DESKTOP || this == VIRTUAL_DESKTOP

    companion object {
        fun fromWireValue(value: String?): HostLibraryKind? =
            entries.firstOrNull { it.wireValue == value }
    }
}

enum class HostSortMode(val wireValue: String) {
    NAME_ASCENDING("nameAscending"),
    NAME_DESCENDING("nameDescending"),
    ADDED_NEWEST("addedNewest"),
    ADDED_OLDEST("addedOldest"),
    LAST_PLAYED_NEWEST("lastPlayedNewest"),
    MANUAL("manual");

    companion object {
        fun fromWireValue(value: String?): HostSortMode =
            entries.firstOrNull { it.wireValue == value } ?: NAME_ASCENDING
    }
}

enum class LibraryLayoutMode(val storedValue: String) {
    LIST("list"),
    POSTER("poster");

    companion object {
        fun fromStoredValue(value: String?): LibraryLayoutMode =
            entries.firstOrNull { it.storedValue == value } ?: LIST
    }
}

enum class LigaseLibraryStatus {
    IDLE,
    LOADING,
    READY,
    INCOMPATIBLE,
    SYNC_ERROR,
    PERMISSION_ERROR,
}

internal object LibrarySyncAutoLoadPolicy {
    fun shouldFetch(
        hasSnapshot: Boolean,
        status: LigaseLibraryStatus,
    ): Boolean =
        !hasSnapshot &&
            status != LigaseLibraryStatus.INCOMPATIBLE &&
            status != LigaseLibraryStatus.SYNC_ERROR &&
            status != LigaseLibraryStatus.PERMISSION_ERROR
}

sealed interface LibraryItemKey {
    val stableValue: String

    data class HostUuid(val uuid: String) : LibraryItemKey {
        override val stableValue: String = "uuid:$uuid"
    }
}

data class LigaseLibraryItem(
    val key: LibraryItemKey,
    val name: String,
    val kind: HostLibraryKind?,
    val hostAppUuid: String?,
    val appId: Int?,
    val steamAppId: Long?,
    val addedAt: String?,
    val updatedAt: String?,
    val lastPlayedAt: String?,
    val launchApp: NvApp?,
) {
    val isSystem: Boolean
        get() = kind?.isSystem == true

    val isLaunchable: Boolean
        get() = launchApp != null
}

object LigaseLibraryAdapter {
    const val DESKTOP_UUID = "78A25216-F239-45BD-B4AA-F41C814066E9"
    const val VIRTUAL_DESKTOP_UUID = "8902CB19-674A-403D-A587-41B092E900BA"

    fun fromSyncSnapshot(
        snapshot: LigaseSyncSnapshotDto,
        gameStreamApps: List<NvApp>,
    ): List<LigaseLibraryItem> {
        val launchApps = gameStreamApps.mapNotNull { app ->
            app.appUUID.normalizedUuidOrNull()?.let { it to app }
        }.toMap()

        return snapshot.library.items.mapNotNull { dto ->
            val uuid = dto.id.normalizedUuidOrNull() ?: return@mapNotNull null
            val kind = HostLibraryKind.fromWireValue(dto.kind) ?: return@mapNotNull null
            if (!kind.isSystem && dto.publishedToClients == false) return@mapNotNull null
            val launchApp = launchApps[uuid]

            LigaseLibraryItem(
                key = LibraryItemKey.HostUuid(uuid),
                name = when (kind) {
                    HostLibraryKind.DESKTOP -> "监控桌面"
                    HostLibraryKind.VIRTUAL_DESKTOP -> "虚拟桌面"
                    else -> dto.name
                },
                kind = kind,
                hostAppUuid = uuid,
                appId = launchApp?.appId,
                steamAppId = dto.steamAppId,
                addedAt = dto.addedAt,
                updatedAt = dto.updatedAt,
                lastPlayedAt = dto.lastPlayedAt,
                launchApp = launchApp,
            )
        }
    }

    fun visibleItems(
        items: List<LigaseLibraryItem>,
        query: String,
        sortMode: HostSortMode,
    ): List<LigaseLibraryItem> {
        val filtered = query.trim().takeIf(String::isNotEmpty)?.let { normalized ->
            items.filter { it.name.contains(normalized, ignoreCase = true) }
        } ?: items

        if (sortMode == HostSortMode.MANUAL) {
            return filtered
        }

        val systemItems = filtered
            .filter(LigaseLibraryItem::isSystem)
            .sortedBy(::systemRank)
        val ordinaryItems = filtered
            .filterNot(LigaseLibraryItem::isSystem)
            .sortedWith(ordinaryComparator(sortMode))
        return systemItems + ordinaryItems
    }

    private fun ordinaryComparator(sortMode: HostSortMode): Comparator<LigaseLibraryItem> {
        val nameAscending = compareBy<LigaseLibraryItem>(
            { it.name.lowercase(Locale.ROOT) },
            { it.key.stableValue },
        )
        return when (sortMode) {
            HostSortMode.NAME_ASCENDING -> nameAscending
            HostSortMode.NAME_DESCENDING -> compareByDescending<LigaseLibraryItem> {
                it.name.lowercase(Locale.ROOT)
            }.thenBy { it.key.stableValue }
            HostSortMode.ADDED_NEWEST -> timestampComparator(
                selector = LigaseLibraryItem::addedAt,
                newestFirst = true,
                fallback = nameAscending,
            )
            HostSortMode.ADDED_OLDEST -> timestampComparator(
                selector = LigaseLibraryItem::addedAt,
                newestFirst = false,
                fallback = nameAscending,
            )
            HostSortMode.LAST_PLAYED_NEWEST -> timestampComparator(
                selector = LigaseLibraryItem::lastPlayedAt,
                newestFirst = true,
                fallback = nameAscending,
            )
            HostSortMode.MANUAL -> nameAscending
        }
    }

    private fun timestampComparator(
        selector: (LigaseLibraryItem) -> String?,
        newestFirst: Boolean,
        fallback: Comparator<LigaseLibraryItem>,
    ): Comparator<LigaseLibraryItem> = Comparator { left, right ->
        val leftTime = selector(left)?.let(::parseIsoTimestamp)
        val rightTime = selector(right)?.let(::parseIsoTimestamp)
        val timeResult = when {
            leftTime == null && rightTime == null -> 0
            leftTime == null -> 1
            rightTime == null -> -1
            newestFirst -> rightTime.compareTo(leftTime)
            else -> leftTime.compareTo(rightTime)
        }
        if (timeResult != 0) timeResult else fallback.compare(left, right)
    }

    private fun systemRank(item: LigaseLibraryItem): Int = when (item.kind) {
        HostLibraryKind.DESKTOP -> 0
        HostLibraryKind.VIRTUAL_DESKTOP -> 1
        else -> 2
    }

    private fun parseIsoTimestamp(value: String): Long? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
        )
        for (pattern in patterns) {
            val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val position = ParsePosition(0)
            val parsed = formatter.parse(value, position)
            if (parsed != null && position.index == value.length) return parsed.time
        }
        return null
    }

    private fun String?.normalizedUuidOrNull(): String? =
        this?.trim()?.takeIf(String::isNotEmpty)?.uppercase(Locale.ROOT)
}
