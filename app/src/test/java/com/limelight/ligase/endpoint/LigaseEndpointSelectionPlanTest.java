package com.limelight.ligase.endpoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LigaseEndpointSelectionPlanTest {
    @Test
    public void interleavesIpv6AndIpv4AtFrozenStagger() {
        LigaseEndpoint v6a = endpoint("2001:db8::1");
        LigaseEndpoint v6b = endpoint("2001:db8::2");
        LigaseEndpoint v4a = endpoint("10.0.0.1");
        LigaseEndpoint v4b = endpoint("10.0.0.2");

        List<LigaseEndpointSelectionPlan.Attempt> plan =
                LigaseEndpointSelectionPlan.forResolvedCandidates(
                        Arrays.asList(v6a, v6b, v4a, v4b));

        assertEquals(v6a, plan.get(0).endpoint);
        assertEquals(0, plan.get(0).startDelayMillis);
        assertEquals(v4a, plan.get(1).endpoint);
        assertEquals(250, plan.get(1).startDelayMillis);
        assertEquals(v6b, plan.get(2).endpoint);
        assertEquals(500, plan.get(2).startDelayMillis);
        assertEquals(v4b, plan.get(3).endpoint);
        assertEquals(750, plan.get(3).startDelayMillis);
    }

    @Test
    public void singleStackDoesNotInsertMissingFamilySlots() {
        LigaseEndpoint first = endpoint("10.0.0.1");
        LigaseEndpoint second = endpoint("10.0.0.2");

        List<LigaseEndpointSelectionPlan.Attempt> plan =
                LigaseEndpointSelectionPlan.forResolvedCandidates(
                        Arrays.asList(first, second));

        assertEquals(2, plan.size());
        assertEquals(0, plan.get(0).startDelayMillis);
        assertEquals(250, plan.get(1).startDelayMillis);
    }

    @Test
    public void keepsFrozenTimeoutConstantsAndRejectsUnresolvedHostname() {
        assertEquals(3_000, LigaseEndpointSelectionPlan.CANDIDATE_TIMEOUT_MILLIS);
        assertEquals(5_000, LigaseEndpointSelectionPlan.OVERALL_TIMEOUT_MILLIS);
        LigaseEndpoint hostname = LigaseEndpointParser.parseManual(
                "ligase.local", "48989", 48989);

        assertThrows(
                IllegalArgumentException.class,
                () -> LigaseEndpointSelectionPlan.forResolvedCandidates(
                        Collections.singletonList(hostname)));
    }

    private static LigaseEndpoint endpoint(String host) {
        return LigaseEndpointParser.parse(
                LigaseEndpoint.Scheme.HTTP,
                host,
                48989,
                null,
                LigaseEndpoint.Source.MDNS);
    }
}
