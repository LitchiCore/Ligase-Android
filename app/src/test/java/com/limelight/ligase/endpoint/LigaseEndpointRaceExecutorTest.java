package com.limelight.ligase.endpoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LigaseEndpointRaceExecutorTest {
    @Test
    public void firstHealthyCompletionWinsRatherThanCandidateOrder() throws Exception {
        LigaseEndpoint first = endpoint("10.0.0.1");
        LigaseEndpoint second = endpoint("2001:db8::1");
        List<LigaseEndpointSelectionPlan.Attempt> attempts =
                LigaseEndpointSelectionPlan.forCandidates(Arrays.asList(first, second));

        LigaseEndpointRaceExecutor.Result<String> result =
                new LigaseEndpointRaceExecutor<String>().race(attempts, endpoint -> {
                    if (endpoint.equals(first)) {
                        try {
                            Thread.sleep(700);
                        }
                        catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                    }
                    return endpoint.host;
                });

        assertNotNull(result);
        assertEquals(second, result.endpoint);
        assertEquals(second.host, result.value);
    }

    @Test
    public void failedProbeIsNotRetriedOrDuplicated() throws Exception {
        LigaseEndpoint first = endpoint("10.0.0.1");
        LigaseEndpoint second = endpoint("10.0.0.2");
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();

        LigaseEndpointRaceExecutor.Result<String> result =
                new LigaseEndpointRaceExecutor<String>().race(
                        LigaseEndpointSelectionPlan.forCandidates(
                                Arrays.asList(first, second)),
                        endpoint -> {
                            if (endpoint.equals(first)) {
                                firstCalls.incrementAndGet();
                                return null;
                            }
                            secondCalls.incrementAndGet();
                            return "ok";
                        });

        assertNotNull(result);
        assertEquals(1, firstCalls.get());
        assertEquals(1, secondCalls.get());
    }

    @Test
    public void allFailedCandidatesReturnNull() throws Exception {
        LigaseEndpoint candidate = endpoint("10.0.0.1");

        LigaseEndpointRaceExecutor.Result<String> result =
                new LigaseEndpointRaceExecutor<String>().race(
                        LigaseEndpointSelectionPlan.forCandidates(
                                java.util.Collections.singletonList(candidate)),
                        endpoint -> null);

        assertNull(result);
    }

    private static LigaseEndpoint endpoint(String host) {
        return LigaseEndpointParser.parse(
                LigaseEndpoint.Scheme.HTTP,
                host,
                48989,
                null,
                LigaseEndpoint.Source.LOCAL);
    }
}
