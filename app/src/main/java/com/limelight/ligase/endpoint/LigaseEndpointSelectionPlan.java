package com.limelight.ligase.endpoint;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Frozen dual-stack scheduling policy used by the computer polling transport.
 */
public final class LigaseEndpointSelectionPlan {
    public static final long FAMILY_STAGGER_MILLIS = 250;
    public static final long CANDIDATE_TIMEOUT_MILLIS = 3_000;
    public static final long OVERALL_TIMEOUT_MILLIS = 5_000;

    public static final class Attempt {
        @NonNull public final LigaseEndpoint endpoint;
        public final long startDelayMillis;

        Attempt(@NonNull LigaseEndpoint endpoint, long startDelayMillis) {
            this.endpoint = endpoint;
            this.startDelayMillis = startDelayMillis;
        }
    }

    private LigaseEndpointSelectionPlan() {
    }

    /**
     * Builds an alternating plan from already resolved IP candidates.
     * Hostname resolution is deliberately outside this pure policy object.
     */
    @NonNull
    public static List<Attempt> forResolvedCandidates(
            @NonNull List<LigaseEndpoint> candidates) {
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayDeque<LigaseEndpoint> ipv4 = new ArrayDeque<>();
        ArrayDeque<LigaseEndpoint> ipv6 = new ArrayDeque<>();
        LigaseEndpoint.Family firstFamily = null;
        for (LigaseEndpoint endpoint : candidates) {
            if (endpoint.family == LigaseEndpoint.Family.HOSTNAME) {
                throw new IllegalArgumentException(
                        "Hostnames must be resolved before building the race plan");
            }
            if (firstFamily == null) {
                firstFamily = endpoint.family;
            }
            (endpoint.family == LigaseEndpoint.Family.IPV6 ? ipv6 : ipv4).add(endpoint);
        }

        List<Attempt> result = new ArrayList<>(candidates.size());
        LigaseEndpoint.Family next = firstFamily;
        while (!ipv4.isEmpty() || !ipv6.isEmpty()) {
            ArrayDeque<LigaseEndpoint> preferred =
                    next == LigaseEndpoint.Family.IPV6 ? ipv6 : ipv4;
            ArrayDeque<LigaseEndpoint> alternate =
                    next == LigaseEndpoint.Family.IPV6 ? ipv4 : ipv6;
            LigaseEndpoint endpoint = preferred.poll();
            if (endpoint == null) {
                endpoint = alternate.poll();
            }
            result.add(new Attempt(endpoint, result.size() * FAMILY_STAGGER_MILLIS));
            if (!alternate.isEmpty()) {
                next = next == LigaseEndpoint.Family.IPV6
                        ? LigaseEndpoint.Family.IPV4
                        : LigaseEndpoint.Family.IPV6;
            }
        }
        return result;
    }

    /**
     * Literal IPv4/IPv6 candidates retain the explicit family interleave above.
     *
     * Hostnames remain one bounded probe in the current OkHttp 4.12 adapter. DNS results are not
     * yet expanded into independently staggered attempts.
     */
    @NonNull
    public static List<Attempt> forCandidates(@NonNull List<LigaseEndpoint> candidates) {
        List<LigaseEndpoint> literals = new ArrayList<>();
        List<LigaseEndpoint> hostnames = new ArrayList<>();
        for (LigaseEndpoint endpoint : candidates) {
            (endpoint.family == LigaseEndpoint.Family.HOSTNAME
                    ? hostnames
                    : literals).add(endpoint);
        }
        List<Attempt> literalAttempts = forResolvedCandidates(literals);
        List<Attempt> result = new ArrayList<>(candidates.size());
        result.addAll(literalAttempts);
        long nextDelay = result.size() * FAMILY_STAGGER_MILLIS;
        for (LigaseEndpoint hostname : hostnames) {
            result.add(new Attempt(hostname, nextDelay));
            nextDelay += FAMILY_STAGGER_MILLIS;
        }
        return result;
    }
}
