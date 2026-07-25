package com.limelight.ligase.feature.input.layout.v3.serialization

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object TouchLayoutV3Jcs {
    fun canonicalBytes(value: StrictJsonV3Value): ByteArray =
        canonicalText(value).toByteArray(StandardCharsets.UTF_8)

    fun contentHash(root: StrictJsonV3Value.ObjectValue): String {
        val withoutHash = LinkedHashMap(root.fields)
        withoutHash.remove("contentHash")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalBytes(StrictJsonV3Value.ObjectValue(withoutHash)))
        return "sha256:" + digest.joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun canonicalText(value: StrictJsonV3Value): String = when (value) {
        is StrictJsonV3Value.ObjectValue -> value.fields.keys
            .sortedWith(::compareUtf16)
            .joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
                quote(key) + ":" + canonicalText(value.fields.getValue(key))
            }
        is StrictJsonV3Value.ArrayValue -> value.values.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ",",
            transform = ::canonicalText,
        )
        is StrictJsonV3Value.StringValue -> quote(value.value)
        is StrictJsonV3Value.IntegerValue -> value.value.toString()
        is StrictJsonV3Value.BooleanValue -> value.value.toString()
        StrictJsonV3Value.NullValue -> "null"
    }

    private fun compareUtf16(left: String, right: String): Int {
        val length = minOf(left.length, right.length)
        for (index in 0 until length) {
            val compared = left[index].code.compareTo(right[index].code)
            if (compared != 0) return compared
        }
        return left.length.compareTo(right.length)
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
        append('"')
    }
}
