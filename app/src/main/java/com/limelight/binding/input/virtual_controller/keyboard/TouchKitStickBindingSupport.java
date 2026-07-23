package com.limelight.binding.input.virtual_controller.keyboard;

import java.util.Arrays;
import java.util.Locale;

final class TouchKitStickBindingSupport {
    private TouchKitStickBindingSupport() { }

    static String format(int[] bindings) {
        return "上=" + TouchKitKeyBindingParser.keyCodeName(bindings[0]) +
                "\n下=" + TouchKitKeyBindingParser.keyCodeName(bindings[1]) +
                "\n左=" + TouchKitKeyBindingParser.keyCodeName(bindings[2]) +
                "\n右=" + TouchKitKeyBindingParser.keyCodeName(bindings[3]);
    }

    static int[] parse(String spec, int[] current) {
        int[] parsed = Arrays.copyOf(current, 4);
        boolean found = false;
        try {
            for (String rawLine : spec.split("\\r?\\n")) {
                String line = rawLine.trim();
                if (line.isEmpty()) continue;
                int separator = line.indexOf('=');
                if (separator <= 0 || separator == line.length() - 1) return null;
                String direction = line.substring(0, separator).trim().toUpperCase(Locale.ROOT);
                int index;
                switch (direction) {
                    case "上": case "UP": index = 0; break;
                    case "下": case "DOWN": index = 1; break;
                    case "左": case "LEFT": index = 2; break;
                    case "右": case "RIGHT": index = 3; break;
                    default: return null;
                }
                parsed[index] = TouchKitKeyBindingParser.parseKeyCode(
                        line.substring(separator + 1));
                found = true;
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return found ? parsed : null;
    }

    /** Removes the obsolete fifth/centre binding while loading older saved layouts. */
    static String removeLegacyCenterBinding(String spec) {
        if (spec == null || spec.trim().isEmpty()) return spec;
        StringBuilder migrated = new StringBuilder();
        for (String rawLine : spec.split("\\r?\\n")) {
            String line = rawLine.trim();
            int separator = line.indexOf('=');
            String direction = separator > 0
                    ? line.substring(0, separator).trim().toUpperCase(Locale.ROOT) : "";
            if (direction.equals("中") || direction.equals("中心") ||
                    direction.equals("CENTER")) {
                continue;
            }
            if (!line.isEmpty()) {
                if (migrated.length() > 0) migrated.append('\n');
                migrated.append(line);
            }
        }
        return migrated.toString();
    }
}
