package com.limelight.ligase.feature.input.layout.v3.presentation

import com.limelight.ligase.feature.input.layout.v3.domain.InputCode
import com.limelight.ligase.feature.input.layout.v3.domain.InputCodeNamespace

data class LayoutV3KeyboardKey(
    val label: String,
    val code: Int? = null,
    val widthUnits: Float = 1f,
) {
    val inputCode: InputCode?
        get() = code?.let { InputCode(InputCodeNamespace.ANDROID_KEY_CODE, it) }
}

val LAYOUT_V3_ANSI_104_ROWS: List<List<LayoutV3KeyboardKey>> = listOf(
    keyboardRow(
        key("Esc", 111), gap(), key("F1", 131), key("F2", 132), key("F3", 133),
        key("F4", 134), gap(), key("F5", 135), key("F6", 136), key("F7", 137),
        key("F8", 138), gap(), key("F9", 139), key("F10", 140), key("F11", 141),
        key("F12", 142), gap(), key("PrtSc", 120), key("ScrLk", 116), key("Pause", 121),
    ),
    keyboardRow(
        key("`", 68), key("1", 8), key("2", 9), key("3", 10), key("4", 11),
        key("5", 12), key("6", 13), key("7", 14), key("8", 15), key("9", 16),
        key("0", 7), key("-", 69), key("=", 70), key("Backspace", 67, 2f),
        gap(), key("Ins", 124), key("Home", 122), key("PgUp", 92),
        gap(), key("Num", 143), key("/", 154), key("*", 155), key("-", 156),
    ),
    keyboardRow(
        key("Tab", 61, 1.5f), key("Q", 45), key("W", 51), key("E", 33),
        key("R", 46), key("T", 48), key("Y", 53), key("U", 49), key("I", 37),
        key("O", 43), key("P", 44), key("[", 71), key("]", 72), key("\\", 73, 1.5f),
        gap(), key("Del", 112), key("End", 123), key("PgDn", 93),
        gap(), key("7", 151), key("8", 152), key("9", 153), key("+", 157),
    ),
    keyboardRow(
        key("Caps", 115, 1.8f), key("A", 29), key("S", 47), key("D", 32),
        key("F", 34), key("G", 35), key("H", 36), key("J", 38), key("K", 39),
        key("L", 40), key(";", 74), key("'", 75), key("Enter", 66, 2.2f),
        gap(4f), key("4", 148), key("5", 149), key("6", 150),
    ),
    keyboardRow(
        key("Shift", 59, 2.3f), key("Z", 54), key("X", 52), key("C", 31),
        key("V", 50), key("B", 30), key("N", 42), key("M", 41), key(",", 55),
        key(".", 56), key("/", 76), key("Shift", 60, 2.7f),
        gap(2f), key("↑", 19), gap(2f),
        key("1", 145), key("2", 146), key("3", 147), key("Enter", 160),
    ),
    keyboardRow(
        key("Ctrl", 113, 1.4f), key("Win", 117, 1.3f), key("Alt", 57, 1.3f),
        key("Space", 62, 6.2f), key("Alt", 58, 1.3f), key("Win", 118, 1.3f),
        key("Menu", 82, 1.3f), key("Ctrl", 114, 1.4f),
        gap(), key("←", 21), key("↓", 20), key("→", 22),
        gap(), key("0", 144, 2f), key(".", 158),
    ),
)

val LAYOUT_V3_ANSI_FUNCTION_ROW = LAYOUT_V3_ANSI_104_ROWS.first()
val LAYOUT_V3_ANSI_FUNCTION_MAIN =
    LAYOUT_V3_ANSI_FUNCTION_ROW.take(16)
val LAYOUT_V3_ANSI_FUNCTION_NAVIGATION =
    LAYOUT_V3_ANSI_FUNCTION_ROW.takeLast(3)

val LAYOUT_V3_ANSI_MAIN_ROWS = listOf(
    LAYOUT_V3_ANSI_104_ROWS[1].take(14),
    LAYOUT_V3_ANSI_104_ROWS[2].take(14),
    LAYOUT_V3_ANSI_104_ROWS[3].take(13),
    LAYOUT_V3_ANSI_104_ROWS[4].take(12),
    LAYOUT_V3_ANSI_104_ROWS[5].take(8),
)

val LAYOUT_V3_ANSI_NAVIGATION_ROWS = listOf(
    LAYOUT_V3_ANSI_104_ROWS[1].slice(15..17),
    LAYOUT_V3_ANSI_104_ROWS[2].slice(15..17),
    emptyList(),
    listOf(gap(1f), LAYOUT_V3_ANSI_104_ROWS[4][13], gap(1f)),
    LAYOUT_V3_ANSI_104_ROWS[5].slice(9..11),
)

val LAYOUT_V3_ANSI_NUMPAD_ROWS = listOf(
    LAYOUT_V3_ANSI_104_ROWS[1].takeLast(4),
    LAYOUT_V3_ANSI_104_ROWS[2].takeLast(4),
    LAYOUT_V3_ANSI_104_ROWS[3].takeLast(3),
    LAYOUT_V3_ANSI_104_ROWS[4].takeLast(4),
    LAYOUT_V3_ANSI_104_ROWS[5].takeLast(2),
)

data class LayoutV3KeyboardGridPlacement(
    val key: LayoutV3KeyboardKey,
    val row: Int,
    val column: Int,
    val rowSpan: Int = 1,
    val columnSpan: Int = 1,
)

val LAYOUT_V3_ANSI_NUMPAD_GRID = listOf(
    LayoutV3KeyboardGridPlacement(LAYOUT_V3_ANSI_NUMPAD_ROWS[0][0], 0, 0),
    LayoutV3KeyboardGridPlacement(LAYOUT_V3_ANSI_NUMPAD_ROWS[0][1], 0, 1),
    LayoutV3KeyboardGridPlacement(LAYOUT_V3_ANSI_NUMPAD_ROWS[0][2], 0, 2),
    LayoutV3KeyboardGridPlacement(LAYOUT_V3_ANSI_NUMPAD_ROWS[0][3], 0, 3),
    LayoutV3KeyboardGridPlacement(LAYOUT_V3_ANSI_NUMPAD_ROWS[1][0], 1, 0),
    LayoutV3KeyboardGridPlacement(LAYOUT_V3_ANSI_NUMPAD_ROWS[1][1], 1, 1),
    LayoutV3KeyboardGridPlacement(LAYOUT_V3_ANSI_NUMPAD_ROWS[1][2], 1, 2),
    LayoutV3KeyboardGridPlacement(LAYOUT_V3_ANSI_NUMPAD_ROWS[1][3], 1, 3, rowSpan = 2),
    LayoutV3KeyboardGridPlacement(LAYOUT_V3_ANSI_NUMPAD_ROWS[2][0], 2, 0),
    LayoutV3KeyboardGridPlacement(LAYOUT_V3_ANSI_NUMPAD_ROWS[2][1], 2, 1),
    LayoutV3KeyboardGridPlacement(LAYOUT_V3_ANSI_NUMPAD_ROWS[2][2], 2, 2),
    LayoutV3KeyboardGridPlacement(LAYOUT_V3_ANSI_NUMPAD_ROWS[3][0], 3, 0),
    LayoutV3KeyboardGridPlacement(LAYOUT_V3_ANSI_NUMPAD_ROWS[3][1], 3, 1),
    LayoutV3KeyboardGridPlacement(LAYOUT_V3_ANSI_NUMPAD_ROWS[3][2], 3, 2),
    LayoutV3KeyboardGridPlacement(LAYOUT_V3_ANSI_NUMPAD_ROWS[3][3], 3, 3, rowSpan = 2),
    LayoutV3KeyboardGridPlacement(
        LAYOUT_V3_ANSI_NUMPAD_ROWS[4][0],
        4,
        0,
        columnSpan = 2,
    ),
    LayoutV3KeyboardGridPlacement(LAYOUT_V3_ANSI_NUMPAD_ROWS[4][1], 4, 2),
)

val LAYOUT_V3_ANSI_SELECTABLE_KEYS =
    LAYOUT_V3_ANSI_FUNCTION_ROW.filter(LayoutV3KeyboardKey::isSelectable) +
        LAYOUT_V3_ANSI_MAIN_ROWS.flatten() +
        LAYOUT_V3_ANSI_NAVIGATION_ROWS.flatten() +
        LAYOUT_V3_ANSI_NUMPAD_GRID.map(LayoutV3KeyboardGridPlacement::key)

private val LAYOUT_V3_ANDROID_KEY_LABELS =
    LAYOUT_V3_ANSI_SELECTABLE_KEYS
        .mapNotNull { key -> key.inputCode?.let { it to key.label } }
        .toMap()

const val LAYOUT_V3_KEYBOARD_KEY_GAP_DP = 4f
const val LAYOUT_V3_KEYBOARD_MIN_KEY_DP = 24f
const val LAYOUT_V3_KEYBOARD_MAX_KEY_DP = 42f
const val LAYOUT_V3_KEYBOARD_SECTION_GAP_UNITS = 0.65f

fun layoutV3KeyboardUnitWidthDp(availableWidthDp: Float): Float {
    require(availableWidthDp > 0f)
    val functionUnits = rowUnits(LAYOUT_V3_ANSI_FUNCTION_ROW)
    val bodyUnits = sectionUnits(LAYOUT_V3_ANSI_MAIN_ROWS) +
        sectionUnits(LAYOUT_V3_ANSI_NAVIGATION_ROWS) +
        sectionUnits(LAYOUT_V3_ANSI_NUMPAD_ROWS) +
        LAYOUT_V3_KEYBOARD_SECTION_GAP_UNITS * 2
    val widestUnits = maxOf(functionUnits, bodyUnits)
    val widestSlots = maxOf(
        LAYOUT_V3_ANSI_FUNCTION_ROW.size,
        maxRowSize(LAYOUT_V3_ANSI_MAIN_ROWS) +
            maxRowSize(LAYOUT_V3_ANSI_NAVIGATION_ROWS) +
            maxRowSize(LAYOUT_V3_ANSI_NUMPAD_ROWS) + 2,
    )
    val availableForKeys = availableWidthDp -
        (widestSlots - 1) * LAYOUT_V3_KEYBOARD_KEY_GAP_DP
    return (availableForKeys / widestUnits)
        .coerceIn(LAYOUT_V3_KEYBOARD_MIN_KEY_DP, LAYOUT_V3_KEYBOARD_MAX_KEY_DP)
}

fun layoutV3KeyboardRequiredWidthDp(unitWidthDp: Float): Float {
    require(unitWidthDp > 0f)
    val functionWidth = rowWidthDp(LAYOUT_V3_ANSI_FUNCTION_ROW, unitWidthDp)
    val bodyWidth =
        sectionWidthDp(LAYOUT_V3_ANSI_MAIN_ROWS, unitWidthDp) +
            sectionWidthDp(LAYOUT_V3_ANSI_NAVIGATION_ROWS, unitWidthDp) +
            sectionWidthDp(LAYOUT_V3_ANSI_NUMPAD_ROWS, unitWidthDp) +
            LAYOUT_V3_KEYBOARD_SECTION_GAP_UNITS * unitWidthDp * 2
    return maxOf(functionWidth, bodyWidth)
}

fun layoutV3KeyboardSectionWidthDp(
    rows: List<List<LayoutV3KeyboardKey>>,
    unitWidthDp: Float,
): Float = sectionWidthDp(rows, unitWidthDp)

fun toggleLayoutV3KeyboardSelection(
    selected: Set<InputCode>,
    key: InputCode,
): Set<InputCode> = if (key in selected) selected - key else selected + key

fun layoutV3KeyboardLabel(inputCode: InputCode): String? =
    LAYOUT_V3_ANDROID_KEY_LABELS[inputCode]

private fun keyboardRow(vararg keys: LayoutV3KeyboardKey) = keys.toList()

private fun key(label: String, code: Int, widthUnits: Float = 1f) =
    LayoutV3KeyboardKey(label, code, widthUnits)

private fun gap(widthUnits: Float = 0.65f) =
    LayoutV3KeyboardKey("", widthUnits = widthUnits)

private fun LayoutV3KeyboardKey.isSelectable() = code != null

private fun rowUnits(row: List<LayoutV3KeyboardKey>) =
    row.sumOf { it.widthUnits.toDouble() }.toFloat()

private fun sectionUnits(rows: List<List<LayoutV3KeyboardKey>>) =
    rows.maxOf(::rowUnits)

private fun maxRowSize(rows: List<List<LayoutV3KeyboardKey>>) =
    rows.maxOf(List<LayoutV3KeyboardKey>::size)

private fun rowWidthDp(row: List<LayoutV3KeyboardKey>, unitWidthDp: Float) =
    rowUnits(row) * unitWidthDp +
        (row.size - 1).coerceAtLeast(0) * LAYOUT_V3_KEYBOARD_KEY_GAP_DP

private fun sectionWidthDp(
    rows: List<List<LayoutV3KeyboardKey>>,
    unitWidthDp: Float,
) = rows.maxOf { rowWidthDp(it, unitWidthDp) }
