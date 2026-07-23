package com.limelight.ligase.endpoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.net.UnknownHostException;

public class LigaseScopedEndpointDnsTest {
    @Test
    public void unscopedAddressDoesNotInstallAdapter() throws Exception {
        assertNull(LigaseScopedEndpointDns.fromLegacyAddress("2001:db8::1"));
        assertNull(LigaseScopedEndpointDns.fromLegacyAddress("10.0.0.1"));
    }

    @Test
    public void numericScopeProducesScopedInet6Address() throws Exception {
        LigaseScopedEndpointDns dns =
                LigaseScopedEndpointDns.fromLegacyAddress("fe80::1%7");

        assertEquals(7, dns.getScopedAddress().getScopeId());
        assertEquals(
                dns.getScopedAddress(),
                dns.lookup(LigaseScopedEndpointDns.SYNTHETIC_HOST).get(0));
    }

    @Test
    public void missingInterfaceFailsClosed() {
        assertThrows(
                UnknownHostException.class,
                () -> LigaseScopedEndpointDns.fromLegacyAddress(
                        "fe80::1%ligase-interface-that-does-not-exist"));
    }

    @Test
    public void scopeOnGlobalAddressFailsClosed() {
        assertThrows(
                UnknownHostException.class,
                () -> LigaseScopedEndpointDns.fromLegacyAddress("2001:db8::1%7"));
    }
}
