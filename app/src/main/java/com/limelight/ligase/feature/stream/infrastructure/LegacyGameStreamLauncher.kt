package com.limelight.ligase.feature.stream.infrastructure

import android.app.Activity
import com.limelight.computers.ComputerManagerService
import com.limelight.ligase.feature.stream.application.StreamLaunchPlan
import com.limelight.utils.ServerHelper

/**
 * Concrete seam around the legacy GameStream launch ABI.
 *
 * UI confirmation stays in LigaseActivity; this adapter only invokes the
 * existing ServerHelper start path with an already validated immutable plan.
 */
open class LegacyGameStreamLauncher private constructor(
    private val activity: Activity?,
    private val binderProvider: () -> ComputerManagerService.ComputerManagerBinder?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) {
    constructor(
        activity: Activity,
        binderProvider: () -> ComputerManagerService.ComputerManagerBinder?,
    ) : this(activity, binderProvider, Unit)

    internal constructor() : this(null, { null }, Unit)

    open fun isAvailable(): Boolean = binderProvider() != null

    open fun launch(plan: StreamLaunchPlan): Boolean {
        val parent = activity ?: return false
        val binder = binderProvider() ?: return false
        ServerHelper.doStart(
            parent,
            plan.app,
            plan.host,
            binder,
            plan.withVirtualDisplay,
            plan.width,
            plan.height,
            plan.hostHdrSupported,
            true,
        )
        return true
    }
}
