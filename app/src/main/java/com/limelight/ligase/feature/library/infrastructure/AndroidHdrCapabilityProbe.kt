package com.limelight.ligase.feature.library.infrastructure

import android.app.Activity
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import com.limelight.LimeLog
import com.limelight.preferences.PreferenceConfiguration

data class AndroidHdrCapabilities(
    val displaySupported: Boolean?,
    val decoderSupported: Boolean?,
    val userEnabled: Boolean?,
)

internal data class AndroidCodecCapability(
    val isEncoder: Boolean,
    val profilesByMimeType: Map<String, Set<Int>>,
)

/**
 * Android-only HDR capability probe.
 *
 * It reports typed nullable facts and leaves reason precedence to the existing
 * LibraryHdrStateResolver domain policy.
 */
class AndroidHdrCapabilityProbe private constructor(
    private val sdkInt: Int,
    private val displayHdrTypes: () -> IntArray?,
    private val codecCapabilities: () -> List<AndroidCodecCapability>,
    private val userHdrEnabled: () -> Boolean?,
) {
    constructor(activity: Activity) : this(
        sdkInt = Build.VERSION.SDK_INT,
        displayHdrTypes = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                null
            } else {
                activity.windowManager.defaultDisplay.hdrCapabilities?.supportedHdrTypes
            }
        },
        codecCapabilities = {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.map { codec ->
                AndroidCodecCapability(
                    isEncoder = codec.isEncoder,
                    profilesByMimeType = codec.supportedTypes.associate { type ->
                        type.lowercase() to
                            codec.getCapabilitiesForType(type).profileLevels
                                .mapTo(mutableSetOf()) { it.profile }
                    },
                )
            }
        },
        userHdrEnabled = {
            PreferenceConfiguration.readPreferences(activity).enableHdr
        },
    )

    internal constructor(
        sdkInt: Int,
        displayHdrTypes: () -> IntArray?,
        codecCapabilities: () -> List<AndroidCodecCapability>,
        userHdrEnabled: () -> Boolean?,
        @Suppress("UNUSED_PARAMETER") testing: Unit = Unit,
    ) : this(sdkInt, displayHdrTypes, codecCapabilities, userHdrEnabled)

    fun probe(): AndroidHdrCapabilities {
        if (sdkInt < Build.VERSION_CODES.N) {
            return AndroidHdrCapabilities(
                displaySupported = false,
                decoderSupported = false,
                userEnabled = userHdrEnabled(),
            )
        }
        val displaySupported = displayHdrTypes()?.any {
            it == Display.HdrCapabilities.HDR_TYPE_HDR10
        } ?: false
        val decoderSupported = try {
            codecCapabilities()
                .asSequence()
                .filterNot(AndroidCodecCapability::isEncoder)
                .any { codec ->
                    codec.profilesByMimeType.any { (mimeType, profiles) ->
                        when {
                            mimeType.equals("video/hevc", ignoreCase = true) ->
                                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 in profiles
                            sdkInt >= Build.VERSION_CODES.Q &&
                                mimeType.equals("video/av01", ignoreCase = true) ->
                                MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10 in profiles
                            else -> false
                        }
                    }
                }
        } catch (error: RuntimeException) {
            LimeLog.warning("Unable to inspect local HDR decoder capability: $error")
            null
        }
        return AndroidHdrCapabilities(
            displaySupported = displaySupported,
            decoderSupported = decoderSupported,
            userEnabled = userHdrEnabled(),
        )
    }
}
