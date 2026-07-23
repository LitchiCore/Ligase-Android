package com.limelight.ligase.endpoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class LigaseEndpointParserTest {
    @Test
    public void normalizesHostnameAndIdnWithoutResolvingIt() {
        LigaseEndpoint plain = LigaseEndpointParser.parseManual(
                "  Ligase-Host.LOCAL  ", "", 48989);
        LigaseEndpoint idn = LigaseEndpointParser.parseManual(
                "例子.测试", "48989", 48989);

        assertEquals("ligase-host.local", plain.host);
        assertEquals(LigaseEndpoint.Family.HOSTNAME, plain.family);
        assertEquals("xn--fsqu00a.xn--0zwm56d", idn.host);
    }

    @Test
    public void normalizesIpv4AndLegacyPort() {
        LigaseEndpoint endpoint = LigaseEndpointParser.parseManual(
                "010.168.001.191:49989", "", 48989);

        assertEquals("10.168.1.191", endpoint.host);
        assertEquals(49989, endpoint.port);
        assertEquals(LigaseEndpoint.Family.IPV4, endpoint.family);
    }

    @Test
    public void normalizesCompressedIpv6AndLoopback() {
        LigaseEndpoint compressed = LigaseEndpointParser.parseManual(
                "2001:0db8:0:0:0:0:0:1", "48989", 48989);
        LigaseEndpoint loopback = LigaseEndpointParser.parseManual("::1", "", 48989);

        assertEquals("2001:db8::1", compressed.host);
        assertEquals("::1", loopback.host);
        assertEquals("[::1]:48989", LigaseEndpointFormatter.authority(loopback));
        assertEquals("http://[::1]:48989", LigaseEndpointFormatter.uri(loopback));
    }

    @Test
    public void acceptsLinkLocalZoneAndEncodesUriAuthority() {
        LigaseEndpoint endpoint = LigaseEndpointParser.parseManual(
                "fe80::1%局域 网", "48989", 48989);

        assertEquals("fe80::1", endpoint.host);
        assertEquals("局域 网", endpoint.zone);
        assertEquals(
                "[fe80::1%25%E5%B1%80%E5%9F%9F%20%E7%BD%91]:48989",
                LigaseEndpointFormatter.authority(endpoint));
        assertEquals("fe80::1%局域 网", LigaseEndpointFormatter.displayAddress(endpoint));
    }

    @Test
    public void acceptsBracketedLegacyScopedAuthority() {
        LigaseEndpoint endpoint = LigaseEndpointParser.parseManual(
                "[fe80::abcd%25wlan0]:49989", "", 48989);

        assertEquals("fe80::abcd", endpoint.host);
        assertEquals("wlan0", endpoint.zone);
        assertEquals(49989, endpoint.port);
    }

    @Test
    public void separatePortFieldWinsOnlyWhenLegacyInputHasNoPort() {
        LigaseEndpoint endpoint = LigaseEndpointParser.parseManual(
                "ligase.local", "50000", 48989);

        assertEquals(50000, endpoint.port);
        assertThrows(
                IllegalArgumentException.class,
                () -> LigaseEndpointParser.parseManual(
                        "ligase.local:49989", "50000", 48989));
    }

    @Test
    public void rejectsInvalidAddressesAndPorts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LigaseEndpointParser.parseManual("", "48989", 48989));
        assertThrows(
                IllegalArgumentException.class,
                () -> LigaseEndpointParser.parseManual("https://ligase.local", "48989", 48989));
        assertThrows(
                IllegalArgumentException.class,
                () -> LigaseEndpointParser.parseManual("999.1.1.1", "48989", 48989));
        assertThrows(
                IllegalArgumentException.class,
                () -> LigaseEndpointParser.parseManual("host..local", "48989", 48989));
        assertThrows(
                IllegalArgumentException.class,
                () -> LigaseEndpointParser.parseManual("ligase.local", "0", 48989));
        assertThrows(
                IllegalArgumentException.class,
                () -> LigaseEndpointParser.parseManual("ligase.local", "65536", 48989));
    }

    @Test
    public void enforcesScopedLinkLocalRules() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LigaseEndpointParser.parseManual("fe80::1", "48989", 48989));
        assertThrows(
                IllegalArgumentException.class,
                () -> LigaseEndpointParser.parseManual("::1%wlan0", "48989", 48989));
        assertThrows(
                IllegalArgumentException.class,
                () -> LigaseEndpointParser.parseManual("fd00::1%wlan0", "48989", 48989));
        assertThrows(
                IllegalArgumentException.class,
                () -> LigaseEndpointParser.parseManual("ff02::1%wlan0", "48989", 48989));
        assertThrows(
                IllegalArgumentException.class,
                () -> LigaseEndpointParser.parseManual("fe80::1%bad:name", "48989", 48989));
        assertThrows(
                IllegalArgumentException.class,
                () -> LigaseEndpointParser.parseManual("fe80::1%0", "48989", 48989));
        assertThrows(
                IllegalArgumentException.class,
                () -> LigaseEndpointParser.parseManual("fe80::1%4294967296", "48989", 48989));
    }

    @Test
    public void sourceDoesNotParticipateInCandidateIdentity() {
        LigaseEndpoint manual = LigaseEndpointParser.parse(
                LigaseEndpoint.Scheme.HTTP,
                "10.168.1.191",
                48989,
                null,
                LigaseEndpoint.Source.MANUAL);
        LigaseEndpoint mdns = manual.withSource(LigaseEndpoint.Source.MDNS);

        assertEquals(manual, mdns);
        assertEquals(manual.hashCode(), mdns.hashCode());
        assertNull(LigaseEndpointParser.parse(
                LigaseEndpoint.Scheme.HTTP,
                "localhost",
                48989,
                null,
                null).source);
    }
}
