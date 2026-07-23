package com.limelight.ligase.endpoint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.limelight.nvstream.http.ComputerDetails;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Persistence adapter for the v2 endpoint list and the computers4 legacy four-slot JSON.
 */
public final class LigaseEndpointAddressBook {
    public static final int VERSION = 2;

    private LigaseEndpointAddressBook() {
    }

    @NonNull
    public static List<LigaseEndpoint> decode(@NonNull JSONObject root) throws JSONException {
        if (root.optInt("version", 0) == VERSION) {
            JSONArray values = root.getJSONArray("endpoints");
            LinkedHashSet<LigaseEndpoint> endpoints = new LinkedHashSet<>();
            for (int index = 0; index < values.length(); index++) {
                JSONObject value = values.getJSONObject(index);
                String zone = value.isNull("zone") ? null : value.optString("zone", null);
                String source = value.isNull("source") ? null : value.optString("source", null);
                endpoints.add(LigaseEndpointParser.parse(
                        LigaseEndpoint.Scheme.fromMachineValue(value.getString("scheme")),
                        value.getString("host"),
                        value.getInt("port"),
                        zone,
                        source == null ? null : LigaseEndpoint.Source.fromMachineValue(source)));
            }
            return new ArrayList<>(endpoints);
        }

        List<LigaseEndpoint> migrated = new ArrayList<>();
        addLegacy(migrated, root, "local", LigaseEndpoint.Source.LOCAL);
        addLegacy(migrated, root, "remote", LigaseEndpoint.Source.REMOTE);
        addLegacy(migrated, root, "manual", LigaseEndpoint.Source.MANUAL);
        addLegacy(migrated, root, "ipv6", LigaseEndpoint.Source.MDNS);
        return deduplicate(migrated);
    }

    @NonNull
    public static JSONObject encode(@NonNull List<LigaseEndpoint> values) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("version", VERSION);
        JSONArray endpoints = new JSONArray();
        for (LigaseEndpoint value : deduplicate(values)) {
            JSONObject endpoint = new JSONObject();
            endpoint.put("scheme", value.scheme.machineValue());
            endpoint.put("host", value.host);
            endpoint.put("port", value.port);
            if (value.zone != null) {
                endpoint.put("zone", value.zone);
            }
            if (value.source != null) {
                endpoint.put("source", value.source.machineValue());
            }
            endpoints.put(endpoint);
        }
        root.put("endpoints", endpoints);
        return root;
    }

    @NonNull
    public static List<LigaseEndpoint> fromLegacyFields(@NonNull ComputerDetails details) {
        List<LigaseEndpoint> endpoints = new ArrayList<>();
        addTuple(endpoints, details.localAddress, LigaseEndpoint.Source.LOCAL);
        addTuple(endpoints, details.remoteAddress, LigaseEndpoint.Source.REMOTE);
        addTuple(endpoints, details.manualAddress, LigaseEndpoint.Source.MANUAL);
        addTuple(endpoints, details.ipv6Address, LigaseEndpoint.Source.MDNS);
        return deduplicate(endpoints);
    }

    /**
     * Reconciles newly discovered legacy transport slots with the structured candidate set.
     * Discovery is intentionally not rewritten in this stage, so both sources remain writable.
     */
    @NonNull
    public static List<LigaseEndpoint> fromComputerDetails(@NonNull ComputerDetails details) {
        List<LigaseEndpoint> endpoints = new ArrayList<>();
        if (details.endpoints != null) {
            endpoints.addAll(details.endpoints);
        }
        endpoints.addAll(fromLegacyFields(details));
        return deduplicate(endpoints);
    }

    public static void projectLegacyFields(
            @NonNull ComputerDetails details,
            @NonNull List<LigaseEndpoint> endpoints) {
        details.localAddress = null;
        details.remoteAddress = null;
        details.manualAddress = null;
        details.ipv6Address = null;
        for (LigaseEndpoint endpoint : endpoints) {
            ComputerDetails.AddressTuple tuple = endpoint.toLegacyAddressTuple();
            LigaseEndpoint.Source source = endpoint.source;
            if ((source == LigaseEndpoint.Source.LOCAL || source == LigaseEndpoint.Source.LOOPBACK)
                    && details.localAddress == null) {
                details.localAddress = tuple;
            }
            else if (source == LigaseEndpoint.Source.REMOTE && details.remoteAddress == null) {
                details.remoteAddress = tuple;
            }
            else if (source == LigaseEndpoint.Source.MANUAL && details.manualAddress == null) {
                details.manualAddress = tuple;
            }
            else if (source == LigaseEndpoint.Source.MDNS && details.ipv6Address == null) {
                details.ipv6Address = tuple;
            }
        }
    }

    private static void addLegacy(
            List<LigaseEndpoint> output,
            JSONObject root,
            String field,
            LigaseEndpoint.Source source) throws JSONException {
        if (!root.has(field) || root.isNull(field)) {
            return;
        }
        JSONObject value = root.getJSONObject(field);
        addRaw(output, value.getString("address"), value.getInt("port"), source);
    }

    private static void addTuple(
            List<LigaseEndpoint> output,
            @Nullable ComputerDetails.AddressTuple tuple,
            LigaseEndpoint.Source source) {
        if (tuple != null) {
            addRaw(output, tuple.address, tuple.port, source);
        }
    }

    private static void addRaw(
            List<LigaseEndpoint> output,
            String host,
            int port,
            LigaseEndpoint.Source source) {
        output.add(LigaseEndpointParser.parse(
                LigaseEndpoint.Scheme.HTTP, host, port, null, source));
    }

    @NonNull
    private static List<LigaseEndpoint> deduplicate(List<LigaseEndpoint> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }
}
