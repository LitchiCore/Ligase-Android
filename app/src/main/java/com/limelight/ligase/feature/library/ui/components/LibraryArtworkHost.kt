package com.limelight.ligase.feature.library.ui.components

import android.graphics.BitmapFactory
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.ligase.feature.library.application.HostVerifiedCoverLoader
import com.limelight.ligase.feature.library.application.HostVerifiedCoverState
import com.limelight.ligase.feature.library.data.repository.VerifiedHostCover
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem

internal enum class LibraryArtworkSource { VERIFIED_HOST, LEGACY, PLACEHOLDER }

internal fun libraryArtworkSource(
    item: LigaseLibraryItem,
    verifiedLoaderAvailable: Boolean,
    legacyLoaderAvailable: Boolean,
): LibraryArtworkSource = when {
    item.launchApp == null -> LibraryArtworkSource.PLACEHOLDER
    item.coverAuthority != null && verifiedLoaderAvailable -> LibraryArtworkSource.VERIFIED_HOST
    item.coverAuthority != null -> LibraryArtworkSource.PLACEHOLDER
    legacyLoaderAvailable -> LibraryArtworkSource.LEGACY
    else -> LibraryArtworkSource.PLACEHOLDER
}

@Composable
internal fun LibraryArtworkHost(
    item: LigaseLibraryItem,
    assetLoader: CachedAppAssetLoader?,
    verifiedCoverLoader: HostVerifiedCoverLoader?,
    onVerifiedStateChanged: (HostVerifiedCoverState?) -> Unit,
    modifier: Modifier,
) {
    val app = item.launchApp ?: return
    val authority = item.coverAuthority
    if (libraryArtworkSource(item, verifiedCoverLoader != null, assetLoader != null) ==
        LibraryArtworkSource.VERIFIED_HOST && authority != null
    ) {
        var cover by remember(authority.appUuid, authority.expectedSha256) {
            mutableStateOf<VerifiedHostCover?>(null)
        }
        DisposableEffect(verifiedCoverLoader, authority.appUuid, authority.expectedSha256) {
            cover = null
            onVerifiedStateChanged(null)
            val subscription = verifiedCoverLoader?.subscribe(app, authority) { state ->
                cover = when (state) {
                    is HostVerifiedCoverState.Current -> state.cover
                    is HostVerifiedCoverState.Rejected -> state.stale
                    HostVerifiedCoverState.Loading -> null
                }
                onVerifiedStateChanged(state)
            }
            onDispose {
                subscription?.close()
                onVerifiedStateChanged(null)
            }
        }
        val bitmap = remember(cover) {
            cover?.bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        if (bitmap != null) {
            AndroidView(
                modifier = modifier,
                factory = { context ->
                    ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                },
                update = { it.setImageBitmap(bitmap) },
            )
        }
        return
    }
    if (libraryArtworkSource(item, false, assetLoader != null) != LibraryArtworkSource.LEGACY ||
        assetLoader == null
    ) return
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                val image = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
                val fallbackText = TextView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(1, 1)
                }
                addView(image)
                addView(fallbackText)
            }
        },
        update = { container ->
            assetLoader.populateImageView(
                app,
                container.getChildAt(0) as ImageView,
                container.getChildAt(1) as TextView,
            )
        },
    )
}
