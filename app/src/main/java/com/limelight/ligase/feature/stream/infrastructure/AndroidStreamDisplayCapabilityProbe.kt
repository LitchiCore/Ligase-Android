package com.limelight.ligase.feature.stream.infrastructure

import android.content.Context
import android.os.Build
import android.view.WindowManager
import com.limelight.ligase.feature.stream.domain.DeviceStreamCapabilities

object AndroidStreamDisplayCapabilityProbe {
    fun probe(context: Context): DeviceStreamCapabilities {
        val display = context.getSystemService(WindowManager::class.java)?.defaultDisplay
        val modes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) display?.supportedModes.orEmpty() else emptyArray()
        val best = modes.maxWithOrNull(
            compareBy<android.view.Display.Mode> { maxOf(it.physicalWidth, it.physicalHeight).toLong() * minOf(it.physicalWidth, it.physicalHeight) }
                .thenBy { it.refreshRate },
        )
        val width = best?.let { maxOf(it.physicalWidth, it.physicalHeight) }
            ?: context.resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) }.coerceAtLeast(1)
        val height = best?.let { minOf(it.physicalWidth, it.physicalHeight) }
            ?: context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) }.coerceAtLeast(1)
        val refresh = modes.maxOfOrNull { it.refreshRate } ?: display?.refreshRate ?: 60f
        return DeviceStreamCapabilities(width, height, refresh.coerceAtLeast(1f), modes.isNotEmpty())
    }
}
