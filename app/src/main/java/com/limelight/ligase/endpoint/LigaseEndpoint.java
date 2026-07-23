package com.limelight.ligase.endpoint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.limelight.nvstream.http.ComputerDetails;

import java.util.Locale;
import java.util.Objects;

/**
 * Stable, structured location for a Ligase host.
 *
 * Host identity remains the server unique ID plus its pinned certificate. This value only
 * describes one address candidate and must never be used as host identity.
 */
public final class LigaseEndpoint {
    public static final int DEFAULT_GAMESTREAM_HTTP_PORT = 48989;

    public enum Scheme {
        HTTP, HTTPS, RTSP;

        @NonNull
        public String machineValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        @NonNull
        public static Scheme fromMachineValue(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    public enum Source {
        MANUAL, MDNS, LOCAL, REMOTE, LOOPBACK;

        @NonNull
        public String machineValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        @NonNull
        public static Source fromMachineValue(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    public enum Family {
        HOSTNAME, IPV4, IPV6
    }

    @NonNull public final Scheme scheme;
    @NonNull public final String host;
    public final int port;
    @Nullable public final String zone;
    @Nullable public final Source source;
    @NonNull public final Family family;

    LigaseEndpoint(
            @NonNull Scheme scheme,
            @NonNull String host,
            int port,
            @Nullable String zone,
            @Nullable Source source,
            @NonNull Family family) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.zone = zone;
        this.source = source;
        this.family = family;
    }

    @NonNull
    public LigaseEndpoint withSource(@Nullable Source newSource) {
        return new LigaseEndpoint(scheme, host, port, zone, newSource, family);
    }

    /**
     * Temporary adapter for the existing Moonlight/GameStream transport ABI.
     * Scoped HTTP must not use this adapter with OkHttp; it needs the dedicated scoped transport.
     */
    @NonNull
    public ComputerDetails.AddressTuple toLegacyAddressTuple() {
        return new ComputerDetails.AddressTuple(
                zone == null ? host : host + "%" + zone,
                port);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof LigaseEndpoint)) {
            return false;
        }
        LigaseEndpoint that = (LigaseEndpoint) other;
        return scheme == that.scheme
                && host.equals(that.host)
                && port == that.port
                && Objects.equals(zone, that.zone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, host, port, zone);
    }

    @NonNull
    @Override
    public String toString() {
        return LigaseEndpointFormatter.uri(scheme, host, port, zone);
    }
}
