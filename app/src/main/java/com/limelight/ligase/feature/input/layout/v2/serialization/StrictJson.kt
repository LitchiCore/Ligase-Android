package com.limelight.ligase.feature.input.layout.v2.serialization

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

sealed interface StrictJsonValue {
    data class ObjectValue(val fields: Map<String, StrictJsonValue>) : StrictJsonValue
    data class ArrayValue(val values: List<StrictJsonValue>) : StrictJsonValue
    data class StringValue(val value: String) : StrictJsonValue
    data class IntegerValue(val value: Long) : StrictJsonValue
    data class BooleanValue(val value: Boolean) : StrictJsonValue
    data object NullValue : StrictJsonValue
}

class TouchLayoutV2Exception(
    val code: String,
    val path: String = "",
    message: String = code,
) : IllegalArgumentException(message)

object StrictJson {
    const val MAX_ARTIFACT_BYTES = 1_048_576
    private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

    fun parse(raw: ByteArray, maxBytes: Int = MAX_ARTIFACT_BYTES): StrictJsonValue {
        if (raw.size > maxBytes) fail("fileTooLarge")
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(raw))
                .toString()
        } catch (_: CharacterCodingException) {
            fail("invalidUtf8")
        }
        return Parser(text).parse()
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun parse(): StrictJsonValue {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            if (index != source.length) fail("invalidJson")
            return value
        }

        private fun parseValue(): StrictJsonValue {
            if (index >= source.length) fail("invalidJson")
            return when (source[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> StrictJsonValue.StringValue(parseString())
                't' -> literal("true", StrictJsonValue.BooleanValue(true))
                'f' -> literal("false", StrictJsonValue.BooleanValue(false))
                'n' -> literal("null", StrictJsonValue.NullValue)
                '-', in '0'..'9' -> parseInteger()
                else -> fail("invalidJson")
            }
        }

        private fun parseObject(): StrictJsonValue.ObjectValue {
            index++
            skipWhitespace()
            val fields = linkedMapOf<String, StrictJsonValue>()
            if (consume('}')) return StrictJsonValue.ObjectValue(fields.toMap())
            while (true) {
                if (index >= source.length || source[index] != '"') fail("invalidJson")
                val key = parseString()
                if (fields.containsKey(key)) fail("duplicateKey")
                skipWhitespace()
                requireChar(':')
                skipWhitespace()
                fields[key] = parseValue()
                skipWhitespace()
                if (consume('}')) return StrictJsonValue.ObjectValue(fields.toMap())
                requireChar(',')
                skipWhitespace()
            }
        }

        private fun parseArray(): StrictJsonValue.ArrayValue {
            index++
            skipWhitespace()
            val values = mutableListOf<StrictJsonValue>()
            if (consume(']')) return StrictJsonValue.ArrayValue(values.toList())
            while (true) {
                values += parseValue()
                skipWhitespace()
                if (consume(']')) return StrictJsonValue.ArrayValue(values.toList())
                requireChar(',')
                skipWhitespace()
            }
        }

        private fun parseString(): String {
            requireChar('"')
            val result = StringBuilder()
            while (index < source.length) {
                when (val char = source[index++]) {
                    '"' -> return validateScalarString(result.toString())
                    '\\' -> {
                        if (index >= source.length) fail("invalidJson")
                        when (source[index++]) {
                            '"', '\\', '/' -> result.append(source[index - 1])
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000c')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> appendUnicodeEscape(result)
                            else -> fail("invalidJson")
                        }
                    }
                    else -> {
                        if (char.code < 0x20) fail("invalidJson")
                        if (Character.isHighSurrogate(char)) {
                            if (index >= source.length || !Character.isLowSurrogate(source[index])) {
                                fail("invalidString")
                            }
                            result.append(char).append(source[index++])
                        } else if (Character.isLowSurrogate(char)) {
                            fail("invalidString")
                        } else {
                            result.append(char)
                        }
                    }
                }
            }
            fail("invalidJson")
        }

        private fun appendUnicodeEscape(result: StringBuilder) {
            val first = readHexCodeUnit()
            val firstChar = first.toChar()
            if (Character.isHighSurrogate(firstChar)) {
                if (index + 1 >= source.length || source[index] != '\\' || source[index + 1] != 'u') {
                    fail("invalidString")
                }
                index += 2
                val secondChar = readHexCodeUnit().toChar()
                if (!Character.isLowSurrogate(secondChar)) fail("invalidString")
                result.append(firstChar).append(secondChar)
            } else if (Character.isLowSurrogate(firstChar)) {
                fail("invalidString")
            } else {
                result.append(firstChar)
            }
        }

        private fun readHexCodeUnit(): Int {
            if (index + 4 > source.length) fail("invalidJson")
            var value = 0
            repeat(4) {
                val digit = source[index++].digitToIntOrNull(16) ?: fail("invalidJson")
                value = value * 16 + digit
            }
            return value
        }

        private fun parseInteger(): StrictJsonValue.IntegerValue {
            val start = index
            if (source[index] == '-') index++
            if (index >= source.length) fail("invalidJson")
            if (source[index] == '0') {
                index++
                if (index < source.length && source[index] in '0'..'9') fail("invalidJson")
            } else {
                if (source[index] !in '1'..'9') fail("invalidJson")
                while (index < source.length && source[index] in '0'..'9') index++
            }
            if (index < source.length && source[index] in charArrayOf('.', 'e', 'E')) {
                fail("fractionNotAllowed")
            }
            val lexeme = source.substring(start, index)
            if (lexeme == "-0") fail("negativeZero")
            val value = lexeme.toLongOrNull() ?: fail("unsafeInteger")
            if (value !in -MAX_SAFE_INTEGER..MAX_SAFE_INTEGER) fail("unsafeInteger")
            return StrictJsonValue.IntegerValue(value)
        }

        private fun <T : StrictJsonValue> literal(text: String, value: T): T {
            if (!source.regionMatches(index, text, 0, text.length)) fail("invalidJson")
            index += text.length
            return value
        }

        private fun requireChar(expected: Char) {
            if (index >= source.length || source[index] != expected) fail("invalidJson")
            index++
        }

        private fun consume(expected: Char): Boolean =
            if (index < source.length && source[index] == expected) {
                index++
                true
            } else {
                false
            }

        private fun skipWhitespace() {
            while (index < source.length && source[index] in charArrayOf(' ', '\t', '\r', '\n')) {
                index++
            }
        }
    }

    internal fun validateScalarString(value: String): String {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (Character.isHighSurrogate(char)) {
                if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) {
                    fail("invalidString")
                }
                index += 2
                continue
            }
            if (Character.isLowSurrogate(char)) fail("invalidString")
            index++
        }
        return value
    }

    internal fun fail(code: String, path: String = ""): Nothing =
        throw TouchLayoutV2Exception(code, path)
}
