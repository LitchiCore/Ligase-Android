package com.limelight.ligase.endpoint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;

public final class LigaseEndpointFormatter {
    private LigaseEndpointFormatter() {
    }

    @NonNull
    public static String displayAddress(@NonNull LigaseEndpoint endpoint) {
        return endpoint.zone == null ? endpoint.host : endpoint.host + "%" + endpoint.zone;
    }

    @NonNull
    public static String authority(@NonNull LigaseEndpoint endpoint) {
        return authority(endpoint.host, endpoint.port, endpoint.zone);
    }

    @NonNull
    public static String authority(
            @NonNull String host,
            int port,
            @Nullable String zone) {
        if (host.indexOf(':') < 0) {
            return host + ":" + port;
        }
        String scope = zone == null ? "" : "%25" + percentEncode(zone);
        return "[" + host + scope + "]:" + port;
    }

    @NonNull
    public static String uri(@NonNull LigaseEndpoint endpoint) {
        return uri(endpoint.scheme, endpoint.host, endpoint.port, endpoint.zone);
    }

    @NonNull
    static String uri(
            @NonNull LigaseEndpoint.Scheme scheme,
            @NonNull String host,
            int port,
            @Nullable String zone) {
        return scheme.machineValue() + "://" + authority(host, port, zone);
    }

    @NonNull
    private static String percentEncode(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);
        for (byte raw : bytes) {
            int valueByte = raw & 0xFF;
            if ((valueByte >= 'a' && valueByte <= 'z')
                    || (valueByte >= 'A' && valueByte <= 'Z')
                    || (valueByte >= '0' && valueByte <= '9')
                    || valueByte == '-' || valueByte == '.' || valueByte == '_'
                    || valueByte == '~') {
                encoded.append((char) valueByte);
            }
            else {
                encoded.append('%');
                String hex = Integer.toHexString(valueByte).toUpperCase(java.util.Locale.ROOT);
                if (hex.length() == 1) {
                    encoded.append('0');
                }
                encoded.append(hex);
            }
        }
        return encoded.toString();
    }
}
