package com.limelight.ligase.library

internal enum class LibraryContentPresentation {
    BLOCKING_STATUS,
    CONTENT,
}

internal enum class PreservedLibraryBanner {
    CHECKING,
    OFFLINE,
    LOADING,
    ERROR,
}

internal fun libraryContentPresentation(
    status: LigaseLibraryStatus,
    hasItems: Boolean,
): LibraryContentPresentation =
    when {
        status == LigaseLibraryStatus.READY -> LibraryContentPresentation.CONTENT
        hasItems && (
            status == LigaseLibraryStatus.IDLE ||
                status == LigaseLibraryStatus.LOADING ||
                status == LigaseLibraryStatus.SYNC_ERROR
            ) -> LibraryContentPresentation.CONTENT
        else -> LibraryContentPresentation.BLOCKING_STATUS
    }

internal fun preservedLibraryBanner(
    status: LigaseLibraryStatus,
    hasItems: Boolean,
    connectivity: LibraryConnectivity,
): PreservedLibraryBanner? {
    if (!hasItems) return null
    return when {
        status == LigaseLibraryStatus.SYNC_ERROR -> PreservedLibraryBanner.ERROR
        status == LigaseLibraryStatus.PERMISSION_ERROR ||
            status == LigaseLibraryStatus.INCOMPATIBLE -> null
        connectivity == LibraryConnectivity.OFFLINE -> PreservedLibraryBanner.OFFLINE
        connectivity == LibraryConnectivity.CHECKING ||
            connectivity == LibraryConnectivity.UNKNOWN -> PreservedLibraryBanner.CHECKING
        status == LigaseLibraryStatus.IDLE ||
            status == LigaseLibraryStatus.LOADING -> PreservedLibraryBanner.LOADING
        else -> null
    }
}
