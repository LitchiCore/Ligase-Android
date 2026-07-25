package com.limelight.ligase.feature.input.layout.v2.data

import android.content.Context
import java.io.FileNotFoundException

sealed interface LayoutCatalogV2PackagedSnapshot {
    data object Unavailable : LayoutCatalogV2PackagedSnapshot
    data object InvalidManifest : LayoutCatalogV2PackagedSnapshot
}

/**
 * Fixed APK-owned entry point for future signed packaged layout assets.
 *
 * The current APK intentionally ships no production v2 manifest. A present
 * manifest is rejected until its frozen parser and descriptor byte format are
 * implemented; test fixtures are never consulted.
 */
class LayoutCatalogV2PackagedSource(context: Context) {
    private val assets = context.applicationContext.assets

    fun read(): LayoutCatalogV2PackagedSnapshot =
        try {
            assets.open(MANIFEST_ASSET).use { input ->
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_MANIFEST_BYTES) break
                }
                if (total > MAX_MANIFEST_BYTES) {
                    LayoutCatalogV2PackagedSnapshot.InvalidManifest
                } else {
                    LayoutCatalogV2PackagedSnapshot.InvalidManifest
                }
            }
        } catch (_: FileNotFoundException) {
            LayoutCatalogV2PackagedSnapshot.Unavailable
        } catch (_: Exception) {
            LayoutCatalogV2PackagedSnapshot.InvalidManifest
        }

    companion object {
        internal const val MANIFEST_ASSET =
            "ligase-touch-layout-v2/manifest.json"
        private const val MAX_MANIFEST_BYTES = 1_048_576
    }
}
