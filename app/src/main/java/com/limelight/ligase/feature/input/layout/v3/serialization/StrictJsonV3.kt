package com.limelight.ligase.feature.input.layout.v3.serialization

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

sealed interface StrictJsonV3Value {
    data class ObjectValue(val fields: Map<String, StrictJsonV3Value>) : StrictJsonV3Value
    data class ArrayValue(val values: List<StrictJsonV3Value>) : StrictJsonV3Value
    data class StringValue(val value: String) : StrictJsonV3Value
    data class IntegerValue(val value: Long) : StrictJsonV3Value
    data class BooleanValue(val value: Boolean) : StrictJsonV3Value
    data object NullValue : StrictJsonV3Value
}

class TouchLayoutV3Exception(
    val code: String,
    val path: String = "",
    message: String = code,
) : IllegalArgumentException(message)

object StrictJsonV3 {
    const val MAX_ARTIFACT_BYTES = 1_048_576
    private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

    fun parse(raw: ByteArray, maxBytes: Int = MAX_ARTIFACT_BYTES): StrictJsonV3Value {
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

        fun parse(): StrictJsonV3Value {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            if (index != source.length) fail("invalidJson")
            return value
        }

        private fun parseValue(): StrictJsonV3Value {
            if (index >= source.length) fail("invalidJson")
            return when (source[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> StrictJsonV3Value.StringValue(parseString())
                't' -> literal("true", StrictJsonV3Value.BooleanValue(true))
                'f' -> literal("false", StrictJsonV3Value.BooleanValue(false))
                'n' -> literal("null", StrictJsonV3Value.NullValue)
                '-', in '0'..'9' -> parseInteger()
                else -> fail("invalidJson")
            }
        }

        private fun parseObject(): StrictJsonV3Value.ObjectValue {
            index++
            skipWhitespace()
            val fields = linkedMapOf<String, StrictJsonV3Value>()
            if (consume('}')) return StrictJsonV3Value.ObjectValue(fields.toMap())
            while (true) {
                if (index >= source.length || source[index] != '"') fail("invalidJson")
                val key = parseString()
                if (fields.containsKey(key)) fail("duplicateKey")
                skipWhitespace()
                requireChar(':')
                skipWhitespace()
                fields[key] = parseValue()
                skipWhitespace()
                if (consume('}')) return StrictJsonV3Value.ObjectValue(fields.toMap())
                requireChar(',')
                skipWhitespace()
            }
        }

        private fun parseArray(): StrictJsonV3Value.ArrayValue {
            index++
            skipWhitespace()
            val values = mutableListOf<StrictJsonV3Value>()
            if (consume(']')) return StrictJsonV3Value.ArrayValue(values.toList())
            while (true) {
                values += parseValue()
                skipWhitespace()
                if (consume(']')) return StrictJsonV3Value.ArrayValue(values.toList())
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

        private fun parseInteger(): StrictJsonV3Value.IntegerValue {
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
            return StrictJsonV3Value.IntegerValue(value)
        }

        private fun <T : StrictJsonV3Value> literal(text: String, value: T): T {
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
        throw TouchLayoutV3Exception(code, path)
}
