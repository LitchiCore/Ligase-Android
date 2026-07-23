package com.limelight.ligase.endpoint;

import androidx.annotation.NonNull;

import com.limelight.nvstream.http.ComputerDetails;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Boundary between computer persistence and transport-era AddressTuple fields.
 */
public final class LigaseEndpointRepository {
    @NonNull
    public JSONObject serialize(@NonNull ComputerDetails details) throws JSONException {
        List<LigaseEndpoint> endpoints =
                LigaseEndpointAddressBook.fromComputerDetails(details);
        details.endpoints = endpoints;
        return LigaseEndpointAddressBook.encode(endpoints);
    }

    public void deserialize(
            @NonNull ComputerDetails details,
            @NonNull JSONObject addresses) throws JSONException {
        details.endpoints = LigaseEndpointAddressBook.decode(addresses);
        LigaseEndpointAddressBook.projectLegacyFields(details, details.endpoints);
    }
}
