package com.limelight.ligase.feature.library.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem

@Composable
internal fun LibraryArtworkHost(
    item: LigaseLibraryItem,
    assetLoader: CachedAppAssetLoader,
    modifier: Modifier,
) {
    val app = item.launchApp ?: return
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
