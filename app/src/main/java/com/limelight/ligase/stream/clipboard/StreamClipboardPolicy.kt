package com.limelight.ligase.stream.clipboard

import java.nio.charset.StandardCharsets

enum class StreamClipboardDecisionCode {
    ACCEPTED,
    EMPTY,
    UNSUPPORTED_TYPE,
    TOO_LARGE,
}

class StreamClipboardDecision private constructor(
    val code: StreamClipboardDecisionCode,
    val text: String?,
) {
    override fun toString(): String = "StreamClipboardDecision(code=$code, text=[redacted])"

    companion object {
        internal fun accepted(text: String) =
            StreamClipboardDecision(StreamClipboardDecisionCode.ACCEPTED, text)

        internal fun rejected(code: StreamClipboardDecisionCode) =
            StreamClipboardDecision(code, null)
    }
}

/** Pure validation owner for explicit stream clipboard actions. */
object StreamClipboardPolicy {
    const val MAX_UTF8_BYTES: Int = 64 * 1024
    const val PLAIN_TEXT_MIME: String = "text/plain"

    @JvmStatic
    fun validateLocal(
        mimeTypes: List<String>,
        itemCount: Int,
        text: String?,
    ): StreamClipboardDecision {
        if (itemCount != 1 || text == null) {
            return StreamClipboardDecision.rejected(StreamClipboardDecisionCode.EMPTY)
        }
        if (mimeTypes.size != 1 || mimeTypes.single() != PLAIN_TEXT_MIME) {
            return StreamClipboardDecision.rejected(StreamClipboardDecisionCode.UNSUPPORTED_TYPE)
        }
        return validateText(text)
    }

    @JvmStatic
    fun validateRemote(text: String?): StreamClipboardDecision =
        if (text == null) {
            StreamClipboardDecision.rejected(StreamClipboardDecisionCode.EMPTY)
        } else {
            validateText(text)
        }

    private fun validateText(text: String): StreamClipboardDecision {
        if (text.isEmpty()) {
            return StreamClipboardDecision.rejected(StreamClipboardDecisionCode.EMPTY)
        }
        if (text.toByteArray(StandardCharsets.UTF_8).size > MAX_UTF8_BYTES) {
            return StreamClipboardDecision.rejected(StreamClipboardDecisionCode.TOO_LARGE)
        }
        return StreamClipboardDecision.accepted(text)
    }
}
