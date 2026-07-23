package com.limelight.ligase.endpoint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LigaseEndpointParser {
    private LigaseEndpointParser() {
    }

    @NonNull
    public static LigaseEndpoint parseManual(
            @NonNull String addressInput,
            @NonNull String portInput,
            int defaultPort) {
        String raw = addressInput.trim();
        if (raw.isEmpty()) {
            throw invalid("Address is empty");
        }
        if (containsUriSyntax(raw)) {
            throw invalid("Protocols, paths, queries, fragments, and user info are not allowed");
        }

        ParsedLegacy legacy = splitLegacyAuthority(raw);
        String explicitPort = portInput.trim();
        if (legacy.port != null && !explicitPort.isEmpty()) {
            int fieldPort = parsePort(explicitPort);
            if (fieldPort != legacy.port) {
                throw invalid("Address and port field specify different ports");
            }
        }
        int port = legacy.port != null
                ? legacy.port
                : (explicitPort.isEmpty() ? validatePort(defaultPort) : parsePort(explicitPort));

        return parse(
                LigaseEndpoint.Scheme.HTTP,
                legacy.host,
                port,
                legacy.zone,
                LigaseEndpoint.Source.MANUAL);
    }

    @NonNull
    public static LigaseEndpoint parse(
            @NonNull LigaseEndpoint.Scheme scheme,
            @NonNull String hostInput,
            int port,
            @Nullable String zoneInput,
            @Nullable LigaseEndpoint.Source source) {
        validatePort(port);
        String rawHost = hostInput.trim();
        String rawZone = normalizeBlank(zoneInput);
        if (rawHost.startsWith("[") && rawHost.endsWith("]")) {
            rawHost = rawHost.substring(1, rawHost.length() - 1);
        }

        ZoneSplit zoneSplit = splitZone(rawHost);
        rawHost = zoneSplit.host;
        if (rawZone != null && zoneSplit.zone != null && !rawZone.equals(zoneSplit.zone)) {
            throw invalid("Address contains a different zone");
        }
        if (rawZone == null) {
            rawZone = zoneSplit.zone;
        }

        if (rawHost.indexOf(':') >= 0) {
            byte[] bytes = parseIpv6Bytes(rawHost);
            if ((bytes[0] & 0xFF) == 0xFF) {
                throw invalid("IPv6 multicast addresses are not supported");
            }
            boolean linkLocal = (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xC0) == 0x80;
            String zone = validateZone(rawZone);
            if (linkLocal && zone == null) {
                throw invalid("Link-local IPv6 addresses require a network interface");
            }
            if (!linkLocal && zone != null) {
                throw invalid("Only link-local IPv6 addresses may include a network interface");
            }
            return new LigaseEndpoint(
                    scheme,
                    canonicalIpv6(bytes),
                    port,
                    zone,
                    source,
                    LigaseEndpoint.Family.IPV6);
        }

        if (rawZone != null) {
            throw invalid("Only IPv6 addresses may include a network interface");
        }

        String ipv4 = canonicalIpv4(rawHost);
        if (ipv4 != null) {
            return new LigaseEndpoint(
                    scheme, ipv4, port, null, source, LigaseEndpoint.Family.IPV4);
        }
        if (rawHost.matches("[0-9.]+")) {
            throw invalid("Invalid IPv4 address");
        }

        String hostname = canonicalHostname(rawHost);
        return new LigaseEndpoint(
                scheme, hostname, port, null, source, LigaseEndpoint.Family.HOSTNAME);
    }

    private static boolean containsUriSyntax(String value) {
        return value.contains("://")
                || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0
                || value.indexOf('?') >= 0
                || value.indexOf('#') >= 0
                || value.indexOf('@') >= 0;
    }

    @NonNull
    private static ParsedLegacy splitLegacyAuthority(String raw) {
        String host = raw;
        Integer port = null;

        if (raw.startsWith("[")) {
            int close = raw.indexOf(']');
            if (close <= 1) {
                throw invalid("Invalid bracketed IPv6 address");
            }
            host = raw.substring(1, close);
            String suffix = raw.substring(close + 1);
            if (!suffix.isEmpty()) {
                if (!suffix.startsWith(":") || suffix.length() == 1) {
                    throw invalid("Invalid address suffix");
                }
                port = parsePort(suffix.substring(1));
            }
        }
        else {
            int firstColon = raw.indexOf(':');
            int lastColon = raw.lastIndexOf(':');
            if (firstColon >= 0 && firstColon == lastColon) {
                host = raw.substring(0, firstColon);
                port = parsePort(raw.substring(firstColon + 1));
            }
            // Multiple colons are an unbracketed IPv6 literal. Its port must use the port field.
        }

        ZoneSplit zone = splitZone(host);
        return new ParsedLegacy(zone.host, zone.zone, port);
    }

    @NonNull
    private static ZoneSplit splitZone(String value) {
        int separator = value.indexOf('%');
        if (separator < 0) {
            return new ZoneSplit(value, null);
        }
        if (value.indexOf('%', separator + 1) >= 0) {
            throw invalid("Address contains more than one zone separator");
        }
        String zone = value.substring(separator + 1);
        if (zone.startsWith("25")) {
            zone = zone.substring(2);
        }
        if (zone.isEmpty()) {
            throw invalid("Network interface is empty");
        }
        return new ZoneSplit(value.substring(0, separator), zone);
    }

    @Nullable
    private static String normalizeBlank(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Nullable
    private static String validateZone(@Nullable String zoneInput) {
        String zone = normalizeBlank(zoneInput);
        if (zone == null) {
            return null;
        }
        int scalarCount = zone.codePointCount(0, zone.length());
        if (scalarCount < 1 || scalarCount > 128) {
            throw invalid("Network interface must contain 1 to 128 characters");
        }
        if (zone.matches("[0-9]+")) {
            try {
                long scope = Long.parseLong(zone);
                if (scope < 1 || scope > 0xFFFF_FFFFL) {
                    throw invalid("Numeric scope is outside the supported range");
                }
            }
            catch (NumberFormatException error) {
                throw invalid("Numeric scope is outside the supported range");
            }
        }
        for (int offset = 0; offset < zone.length();) {
            int codePoint = zone.codePointAt(offset);
            if (Character.isISOControl(codePoint)
                    || "%[]/\\?#:".indexOf(codePoint) >= 0) {
                throw invalid("Network interface contains an unsupported character");
            }
            offset += Character.charCount(codePoint);
        }
        return zone;
    }

    @NonNull
    private static String canonicalHostname(String input) {
        if (input.isEmpty() || input.endsWith(".") || input.startsWith(".")) {
            throw invalid("Invalid hostname");
        }
        final String ascii;
        try {
            ascii = IDN.toASCII(input, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        }
        catch (IllegalArgumentException error) {
            throw invalid("Invalid hostname");
        }
        if (ascii.length() > 253) {
            throw invalid("Hostname is too long");
        }
        String[] labels = ascii.split("\\.", -1);
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63
                    || label.startsWith("-") || label.endsWith("-")) {
                throw invalid("Invalid hostname label");
            }
        }
        return ascii;
    }

    @Nullable
    private static String canonicalIpv4(String input) {
        String[] parts = input.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        List<String> normalized = new ArrayList<>(4);
        for (String part : parts) {
            if (part.isEmpty() || !part.matches("[0-9]{1,3}")) {
                return null;
            }
            int value = Integer.parseInt(part);
            if (value > 255) {
                return null;
            }
            normalized.add(Integer.toString(value));
        }
        return String.join(".", normalized);
    }

    @NonNull
    private static byte[] parseIpv6Bytes(String input) {
        try {
            InetAddress parsed = InetAddress.getByName(input);
            if (!(parsed instanceof Inet6Address)) {
                throw invalid("Invalid IPv6 address");
            }
            return parsed.getAddress();
        }
        catch (UnknownHostException error) {
            throw invalid("Invalid IPv6 address");
        }
    }

    @NonNull
    static String canonicalIpv6(byte[] bytes) {
        int bestStart = -1;
        int bestLength = 0;
        for (int index = 0; index < 8;) {
            int start = index;
            while (index < 8 && bytes[index * 2] == 0 && bytes[index * 2 + 1] == 0) {
                index++;
            }
            int length = index - start;
            if (length > bestLength && length >= 2) {
                bestStart = start;
                bestLength = length;
            }
            if (index == start) {
                index++;
            }
        }

        StringBuilder output = new StringBuilder();
        for (int index = 0; index < 8;) {
            if (index == bestStart) {
                output.append("::");
                index += bestLength;
                continue;
            }
            if (output.length() > 0 && output.charAt(output.length() - 1) != ':') {
                output.append(':');
            }
            int group = ((bytes[index * 2] & 0xFF) << 8) | (bytes[index * 2 + 1] & 0xFF);
            output.append(Integer.toHexString(group));
            index++;
        }
        return output.toString();
    }

    private static int parsePort(String input) {
        if (!input.matches("[0-9]+")) {
            throw invalid("Port must be a number");
        }
        try {
            return validatePort(Integer.parseInt(input));
        }
        catch (NumberFormatException error) {
            throw invalid("Port is outside the supported range");
        }
    }

    private static int validatePort(int port) {
        if (port < 1 || port > 65535) {
            throw invalid("Port is outside the supported range");
        }
        return port;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static final class ParsedLegacy {
        final String host;
        final String zone;
        final Integer port;

        ParsedLegacy(String host, String zone, Integer port) {
            this.host = host;
            this.zone = zone;
            this.port = port;
        }
    }

    private static final class ZoneSplit {
        final String host;
        final String zone;

        ZoneSplit(String host, String zone) {
            this.host = host;
            this.zone = zone;
        }
    }
}
