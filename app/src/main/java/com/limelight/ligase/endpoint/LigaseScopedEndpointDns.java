package com.limelight.ligase.endpoint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

import okhttp3.Dns;

/**
 * OkHttp adapter for scoped link-local IPv6.
 *
 * OkHttp rejects a '%' in HttpUrl.host(). We therefore use a non-resolving synthetic URL host
 * and return the scoped Inet6Address directly from DNS. The original pinned certificate remains
 * the HTTPS identity check.
 */
public final class LigaseScopedEndpointDns implements Dns {
    public static final String SYNTHETIC_HOST = "ligase-scoped.invalid";

    @NonNull private final Inet6Address scopedAddress;

    private LigaseScopedEndpointDns(@NonNull Inet6Address scopedAddress) {
        this.scopedAddress = scopedAddress;
    }

    @Nullable
    public static LigaseScopedEndpointDns fromLegacyAddress(@NonNull String address)
            throws UnknownHostException {
        if (address.indexOf('%') < 0) {
            return null;
        }
        final LigaseEndpoint endpoint;
        try {
            endpoint = LigaseEndpointParser.parse(
                    LigaseEndpoint.Scheme.HTTP,
                    address,
                    LigaseEndpoint.DEFAULT_GAMESTREAM_HTTP_PORT,
                    null,
                    null);
        }
        catch (IllegalArgumentException error) {
            UnknownHostException wrapped =
                    new UnknownHostException("Invalid scoped IPv6 endpoint");
            wrapped.initCause(error);
            throw wrapped;
        }

        byte[] bytes = InetAddress.getByName(endpoint.host).getAddress();
        Inet6Address scoped;
        if (endpoint.zone.matches("[0-9]+")) {
            long numericScope = Long.parseLong(endpoint.zone);
            if (numericScope > Integer.MAX_VALUE) {
                throw new UnknownHostException("Android cannot represent this numeric scope ID");
            }
            scoped = Inet6Address.getByAddress(
                    null,
                    bytes,
                    (int) numericScope);
        }
        else {
            final NetworkInterface networkInterface;
            try {
                networkInterface = NetworkInterface.getByName(endpoint.zone);
            }
            catch (SocketException error) {
                UnknownHostException wrapped =
                        new UnknownHostException("Unable to inspect network interfaces");
                wrapped.initCause(error);
                throw wrapped;
            }
            if (networkInterface == null) {
                throw new UnknownHostException(
                        "Network interface is not available: " + endpoint.zone);
            }
            scoped = Inet6Address.getByAddress(null, bytes, networkInterface);
        }
        return new LigaseScopedEndpointDns(scoped);
    }

    @NonNull
    public Inet6Address getScopedAddress() {
        return scopedAddress;
    }

    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
        if (SYNTHETIC_HOST.equals(hostname)) {
            return Collections.singletonList(scopedAddress);
        }
        return Dns.SYSTEM.lookup(hostname);
    }
}
