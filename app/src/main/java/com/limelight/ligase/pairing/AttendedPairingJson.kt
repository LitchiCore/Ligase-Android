package com.limelight.ligase.pairing

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.TreeMap

internal sealed interface StrictJsonValue {
    data class Obj(val values: Map<String, StrictJsonValue>) : StrictJsonValue
    data class Arr(val values: List<StrictJsonValue>) : StrictJsonValue
    data class Str(val value: String) : StrictJsonValue
    data class Num(val value: Long) : StrictJsonValue
    data class Bool(val value: Boolean) : StrictJsonValue
    data object Null : StrictJsonValue
}

internal object AttendedPairingJson {
    fun parse(bytes: ByteArray): StrictJsonValue {
        val text = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
        return Parser(text).parse()
    }

    fun canonicalBytes(value: StrictJsonValue): ByteArray =
        canonical(value).toByteArray(StandardCharsets.UTF_8)

    fun canonical(value: StrictJsonValue): String = when (value) {
        is StrictJsonValue.Obj -> value.values.toSortedMap().entries.joinToString(
            separator = ",",
            prefix = "{",
            postfix = "}",
        ) { "${quote(it.key)}:${canonical(it.value)}" }
        is StrictJsonValue.Arr -> value.values.joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]",
        ) { canonical(it) }
        is StrictJsonValue.Str -> quote(value.value)
        is StrictJsonValue.Num -> value.value.toString()
        is StrictJsonValue.Bool -> value.value.toString()
        StrictJsonValue.Null -> "null"
    }

    fun objectOf(vararg pairs: Pair<String, StrictJsonValue>): StrictJsonValue.Obj =
        StrictJsonValue.Obj(linkedMapOf(*pairs))

    fun string(value: String) = StrictJsonValue.Str(value)
    fun number(value: Long) = StrictJsonValue.Num(value)

    fun canonicalBase64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun decodeBase64Url(value: String, expectedLength: Int): ByteArray {
        require(value.isNotEmpty() && value.none { it == '=' || it.isWhitespace() }) {
            "invalidBase64url"
        }
        val decoded = try {
            Base64.getUrlDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("invalidBase64url")
        }
        require(decoded.size == expectedLength && canonicalBase64Url(decoded) == value) {
            "invalidBase64url"
        }
        return decoded
    }

    fun requireObject(
        value: StrictJsonValue,
        exactKeys: Set<String>,
    ): Map<String, StrictJsonValue> {
        val objectValue = (value as? StrictJsonValue.Obj)?.values
            ?: throw IllegalArgumentException("invalidType")
        require(objectValue.keys == exactKeys) { "invalidFields" }
        return objectValue
    }

    fun requireString(value: StrictJsonValue): String =
        (value as? StrictJsonValue.Str)?.value ?: throw IllegalArgumentException("invalidType")

    fun requireLong(value: StrictJsonValue): Long =
        (value as? StrictJsonValue.Num)?.value ?: throw IllegalArgumentException("invalidType")

    private fun quote(value: String): String {
        val output = StringBuilder(value.length + 2).append('"')
        value.forEach { character ->
            when (character) {
                '"' -> output.append("\\\"")
                '\\' -> output.append("\\\\")
                '\b' -> output.append("\\b")
                '\u000c' -> output.append("\\f")
                '\n' -> output.append("\\n")
                '\r' -> output.append("\\r")
                '\t' -> output.append("\\t")
                else -> if (character.code < 0x20) {
                    output.append("\\u").append(character.code.toString(16).padStart(4, '0'))
                } else {
                    output.append(character)
                }
            }
        }
        return output.append('"').toString()
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun parse(): StrictJsonValue {
            skipWhitespace()
            val value = readValue()
            skipWhitespace()
            require(index == source.length) { "trailingData" }
            return value
        }

        private fun readValue(): StrictJsonValue {
            require(index < source.length) { "unexpectedEnd" }
            return when (source[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> StrictJsonValue.Str(readString())
                't' -> { expect("true"); StrictJsonValue.Bool(true) }
                'f' -> { expect("false"); StrictJsonValue.Bool(false) }
                'n' -> { expect("null"); StrictJsonValue.Null }
                '-', in '0'..'9' -> readNumber()
                else -> throw IllegalArgumentException("invalidJson")
            }
        }

        private fun readObject(): StrictJsonValue.Obj {
            index++
            skipWhitespace()
            val values = LinkedHashMap<String, StrictJsonValue>()
            if (take('}')) return StrictJsonValue.Obj(values)
            while (true) {
                require(index < source.length && source[index] == '"') { "invalidJson" }
                val key = readString()
                require(!values.containsKey(key)) { "duplicateField" }
                skipWhitespace()
                require(take(':')) { "invalidJson" }
                skipWhitespace()
                values[key] = readValue()
                skipWhitespace()
                if (take('}')) break
                require(take(',')) { "invalidJson" }
                skipWhitespace()
            }
            return StrictJsonValue.Obj(values)
        }

        private fun readArray(): StrictJsonValue.Arr {
            index++
            skipWhitespace()
            val values = ArrayList<StrictJsonValue>()
            if (take(']')) return StrictJsonValue.Arr(values)
            while (true) {
                values += readValue()
                skipWhitespace()
                if (take(']')) break
                require(take(',')) { "invalidJson" }
                skipWhitespace()
            }
            return StrictJsonValue.Arr(values)
        }

        private fun readString(): String {
            require(take('"')) { "invalidJson" }
            val result = StringBuilder()
            while (index < source.length) {
                val character = source[index++]
                when {
                    character == '"' -> {
                        require(result.toString().codePoints().allMatch { it !in 0xD800..0xDFFF }) {
                            "invalidUnicode"
                        }
                        return result.toString()
                    }
                    character == '\\' -> {
                        require(index < source.length) { "invalidJson" }
                        when (val escaped = source[index++]) {
                            '"', '\\', '/' -> result.append(escaped)
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000c')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> {
                                require(index + 4 <= source.length) { "invalidJson" }
                                val code = source.substring(index, index + 4).toIntOrNull(16)
                                    ?: throw IllegalArgumentException("invalidJson")
                                result.append(code.toChar())
                                index += 4
                            }
                            else -> throw IllegalArgumentException("invalidJson")
                        }
                    }
                    character.code < 0x20 -> throw IllegalArgumentException("invalidJson")
                    else -> result.append(character)
                }
            }
            throw IllegalArgumentException("unexpectedEnd")
        }

        private fun readNumber(): StrictJsonValue.Num {
            val start = index
            if (take('-')) Unit
            require(index < source.length) { "invalidNumber" }
            if (take('0')) {
                require(index >= source.length || !source[index].isDigit()) { "invalidNumber" }
            } else {
                require(source[index] in '1'..'9') { "invalidNumber" }
                while (index < source.length && source[index].isDigit()) index++
            }
            require(index >= source.length || source[index] !in charArrayOf('.', 'e', 'E')) {
                "invalidNumber"
            }
            return StrictJsonValue.Num(
                source.substring(start, index).toLongOrNull()
                    ?: throw IllegalArgumentException("invalidNumber"),
            )
        }

        private fun expect(value: String) {
            require(source.regionMatches(index, value, 0, value.length)) { "invalidJson" }
            index += value.length
        }

        private fun take(character: Char): Boolean =
            if (index < source.length && source[index] == character) {
                index++
                true
            } else {
                false
            }

        private fun skipWhitespace() {
            while (index < source.length && source[index] in charArrayOf(' ', '\t', '\r', '\n')) index++
        }
    }
}
