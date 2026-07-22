package com.limelight.binding.input.virtual_controller.keyboard;

import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class TouchKitKeyBindingParser {
    private TouchKitKeyBindingParser() { }

    static final class BindingLine {
        final String keySpec;
        final String description;

        BindingLine(String keySpec, String description) {
            this.keySpec = keySpec;
            this.description = description;
        }
    }

    /** Splits KEY=description without mistaking the equals key for the separator. */
    static BindingLine splitBindingLine(String rawLine) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty()) return new BindingLine("", "");

        try {
            parseCombo(line);
            return new BindingLine(line, "");
        } catch (IllegalArgumentException ignored) { }

        for (int separator = line.lastIndexOf('='); separator >= 0;
             separator = line.lastIndexOf('=', separator - 1)) {
            String keySpec = line.substring(0, separator).trim();
            if (keySpec.isEmpty()) continue;
            try {
                parseCombo(keySpec);
                return new BindingLine(keySpec,
                        line.substring(separator + 1).trim());
            } catch (IllegalArgumentException ignored) { }
        }
        return new BindingLine(line, "");
    }

    static int parseKeyCode(String rawToken) {
        String token = rawToken.trim().toUpperCase(Locale.ROOT);
        if (token.matches("(NUM|NUMPAD|KP)[0-9]")) {
            char digit = token.charAt(token.length() - 1);
            return KeyEvent.KEYCODE_NUMPAD_0 + (digit - '0');
        }
        switch (token) {
            case "CTRL":
            case "LCTRL":
            case "LEFTCTRL":
            case "CTRL_LEFT": return KeyEvent.KEYCODE_CTRL_LEFT;
            case "RCTRL":
            case "RIGHTCTRL":
            case "CTRL_RIGHT": return KeyEvent.KEYCODE_CTRL_RIGHT;
            case "ALT":
            case "LALT":
            case "LEFTALT":
            case "ALT_LEFT": return KeyEvent.KEYCODE_ALT_LEFT;
            case "RALT":
            case "RIGHTALT":
            case "ALT_RIGHT": return KeyEvent.KEYCODE_ALT_RIGHT;
            case "SHIFT":
            case "LSHIFT":
            case "LEFTSHIFT":
            case "SHIFT_LEFT": return KeyEvent.KEYCODE_SHIFT_LEFT;
            case "RSHIFT":
            case "RIGHTSHIFT":
            case "SHIFT_RIGHT": return KeyEvent.KEYCODE_SHIFT_RIGHT;
            case "META":
            case "WIN":
            case "LWIN":
            case "LEFTWIN":
            case "META_LEFT": return KeyEvent.KEYCODE_META_LEFT;
            case "RWIN":
            case "RIGHTWIN":
            case "META_RIGHT": return KeyEvent.KEYCODE_META_RIGHT;
            case "ESC": return KeyEvent.KEYCODE_ESCAPE;
            case "SPACE": return KeyEvent.KEYCODE_SPACE;
            case "ENTER": return KeyEvent.KEYCODE_ENTER;
            case "TAB": return KeyEvent.KEYCODE_TAB;
            case "BACKSPACE": return KeyEvent.KEYCODE_DEL;
            case "DELETE": return KeyEvent.KEYCODE_FORWARD_DEL;
            case "HOME": return KeyEvent.KEYCODE_MOVE_HOME;
            case "END": return KeyEvent.KEYCODE_MOVE_END;
            case "PGUP":
            case "PAGEUP": return KeyEvent.KEYCODE_PAGE_UP;
            case "PGDN":
            case "PAGEDOWN": return KeyEvent.KEYCODE_PAGE_DOWN;
            case "INSERT": return KeyEvent.KEYCODE_INSERT;
            case "`":
            case "GRAVE":
            case "BACKTICK": return KeyEvent.KEYCODE_GRAVE;
            case "+":
            case "PLUS": return KeyEvent.KEYCODE_PLUS;
            case "-":
            case "MINUS": return KeyEvent.KEYCODE_MINUS;
            case "=":
            case "EQUALS": return KeyEvent.KEYCODE_EQUALS;
            case "[": return KeyEvent.KEYCODE_LEFT_BRACKET;
            case "]": return KeyEvent.KEYCODE_RIGHT_BRACKET;
            case "\\": return KeyEvent.KEYCODE_BACKSLASH;
            case ";": return KeyEvent.KEYCODE_SEMICOLON;
            case "'": return KeyEvent.KEYCODE_APOSTROPHE;
            case ",": return KeyEvent.KEYCODE_COMMA;
            case ".": return KeyEvent.KEYCODE_PERIOD;
            case "/": return KeyEvent.KEYCODE_SLASH;
            case "CAPSLOCK": return KeyEvent.KEYCODE_CAPS_LOCK;
            case "NUMLOCK": return KeyEvent.KEYCODE_NUM_LOCK;
            case "SCROLLLOCK": return KeyEvent.KEYCODE_SCROLL_LOCK;
            case "PRINTSCREEN":
            case "PRTSC": return KeyEvent.KEYCODE_SYSRQ;
            case "PAUSE": return KeyEvent.KEYCODE_BREAK;
            case "MENU":
            case "CONTEXTMENU": return KeyEvent.KEYCODE_MENU;
            case "NUMDIVIDE": return KeyEvent.KEYCODE_NUMPAD_DIVIDE;
            case "NUMMULTIPLY": return KeyEvent.KEYCODE_NUMPAD_MULTIPLY;
            case "NUMMINUS": return KeyEvent.KEYCODE_NUMPAD_SUBTRACT;
            case "NUMPLUS": return KeyEvent.KEYCODE_NUMPAD_ADD;
            case "NUMDOT": return KeyEvent.KEYCODE_NUMPAD_DOT;
            case "UP": return KeyEvent.KEYCODE_DPAD_UP;
            case "DOWN": return KeyEvent.KEYCODE_DPAD_DOWN;
            case "LEFT": return KeyEvent.KEYCODE_DPAD_LEFT;
            case "RIGHT": return KeyEvent.KEYCODE_DPAD_RIGHT;
        }

        String androidName = token.startsWith("KEYCODE_") ? token : "KEYCODE_" + token;
        int keyCode = KeyEvent.keyCodeFromString(androidName);
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            throw new IllegalArgumentException("Unknown key: " + rawToken);
        }
        return keyCode;
    }

    static int[] parseCombo(String rawCombo) {
        String[] tokens = rawCombo.trim().split("\\+");
        List<Integer> result = new ArrayList<>();
        for (String token : tokens) {
            if (!token.trim().isEmpty()) {
                result.add(parseKeyCode(token));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Empty key combo");
        }
        int[] keys = new int[result.size()];
        for (int i = 0; i < result.size(); i++) {
            keys[i] = result.get(i);
        }
        return keys;
    }

    static String keyCodeName(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CTRL_LEFT: return "LeftCtrl";
            case KeyEvent.KEYCODE_CTRL_RIGHT: return "RightCtrl";
            case KeyEvent.KEYCODE_ALT_LEFT: return "LeftAlt";
            case KeyEvent.KEYCODE_ALT_RIGHT: return "RightAlt";
            case KeyEvent.KEYCODE_SHIFT_LEFT: return "LeftShift";
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return "RightShift";
            case KeyEvent.KEYCODE_META_LEFT: return "LeftWin";
            case KeyEvent.KEYCODE_META_RIGHT: return "RightWin";
            case KeyEvent.KEYCODE_ESCAPE: return "Esc";
            case KeyEvent.KEYCODE_FORWARD_DEL: return "Delete";
            case KeyEvent.KEYCODE_DEL: return "Backspace";
            case KeyEvent.KEYCODE_INSERT: return "Insert";
            case KeyEvent.KEYCODE_MOVE_HOME: return "Home";
            case KeyEvent.KEYCODE_MOVE_END: return "End";
            case KeyEvent.KEYCODE_PAGE_UP: return "PgUp";
            case KeyEvent.KEYCODE_PAGE_DOWN: return "PgDn";
            case KeyEvent.KEYCODE_GRAVE: return "`";
            case KeyEvent.KEYCODE_PLUS: return "Plus";
            case KeyEvent.KEYCODE_MINUS: return "-";
            case KeyEvent.KEYCODE_EQUALS: return "=";
            case KeyEvent.KEYCODE_LEFT_BRACKET: return "[";
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return "]";
            case KeyEvent.KEYCODE_BACKSLASH: return "\\";
            case KeyEvent.KEYCODE_SEMICOLON: return ";";
            case KeyEvent.KEYCODE_APOSTROPHE: return "'";
            case KeyEvent.KEYCODE_COMMA: return ",";
            case KeyEvent.KEYCODE_PERIOD: return ".";
            case KeyEvent.KEYCODE_SLASH: return "/";
            case KeyEvent.KEYCODE_CAPS_LOCK: return "CapsLock";
            case KeyEvent.KEYCODE_NUM_LOCK: return "NumLock";
            case KeyEvent.KEYCODE_SCROLL_LOCK: return "ScrollLock";
            case KeyEvent.KEYCODE_SYSRQ: return "PrintScreen";
            case KeyEvent.KEYCODE_BREAK: return "Pause";
            case KeyEvent.KEYCODE_MENU: return "Menu";
            case KeyEvent.KEYCODE_NUMPAD_DIVIDE: return "NumDivide";
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY: return "NumMultiply";
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT: return "NumMinus";
            case KeyEvent.KEYCODE_NUMPAD_ADD: return "NumPlus";
            case KeyEvent.KEYCODE_NUMPAD_DOT: return "NumDot";
            case KeyEvent.KEYCODE_DPAD_UP: return "↑";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "↓";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "←";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "→";
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return Integer.toString(keyCode - KeyEvent.KEYCODE_0);
        }
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            return "Num" + (keyCode - KeyEvent.KEYCODE_NUMPAD_0);
        }
        String name = KeyEvent.keyCodeToString(keyCode);
        return name.startsWith("KEYCODE_") ? name.substring(8) : name;
    }

    static String normalizeCombo(String rawCombo) {
        int[] keys = parseCombo(rawCombo);
        return normalizeKeys(keys);
    }

    static String displayCombo(String rawCombo, boolean showPhysicalKeyNames) {
        int[] keys = parseCombo(rawCombo);
        StringBuilder display = new StringBuilder();
        for (int key : keys) {
            if (display.length() > 0) display.append('+');
            display.append(displayKeyCodeName(key, showPhysicalKeyNames));
        }
        return display.toString();
    }

    static String displayKeyCodeName(int keyCode, boolean showPhysicalKeyNames) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_PLUS: return "+";
            case KeyEvent.KEYCODE_NUMPAD_DIVIDE: return "Num/";
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY: return "Num*";
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT: return "Num-";
            case KeyEvent.KEYCODE_NUMPAD_ADD: return "Num+";
            case KeyEvent.KEYCODE_NUMPAD_DOT: return "Num.";
        }
        if (showPhysicalKeyNames) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_CTRL_LEFT: return "L Ctrl";
                case KeyEvent.KEYCODE_CTRL_RIGHT: return "R Ctrl";
                case KeyEvent.KEYCODE_ALT_LEFT: return "L Alt";
                case KeyEvent.KEYCODE_ALT_RIGHT: return "R Alt";
                case KeyEvent.KEYCODE_SHIFT_LEFT: return "L Shift";
                case KeyEvent.KEYCODE_SHIFT_RIGHT: return "R Shift";
                case KeyEvent.KEYCODE_META_LEFT: return "L Win";
                case KeyEvent.KEYCODE_META_RIGHT: return "R Win";
            }
        } else {
            switch (keyCode) {
                case KeyEvent.KEYCODE_CTRL_LEFT:
                case KeyEvent.KEYCODE_CTRL_RIGHT: return "Ctrl";
                case KeyEvent.KEYCODE_ALT_LEFT:
                case KeyEvent.KEYCODE_ALT_RIGHT: return "Alt";
                case KeyEvent.KEYCODE_SHIFT_LEFT:
                case KeyEvent.KEYCODE_SHIFT_RIGHT: return "Shift";
                case KeyEvent.KEYCODE_META_LEFT:
                case KeyEvent.KEYCODE_META_RIGHT: return "Win";
            }
            if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 &&
                    keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
                return Integer.toString(keyCode - KeyEvent.KEYCODE_NUMPAD_0);
            }
        }
        return keyCodeName(keyCode);
    }

    private static String normalizeKeys(int[] keys) {
        StringBuilder normalized = new StringBuilder();
        for (int key : keys) {
            if (normalized.length() > 0) {
                normalized.append('+');
            }
            normalized.append(keyCodeName(key));
        }
        return normalized.toString();
    }
}
