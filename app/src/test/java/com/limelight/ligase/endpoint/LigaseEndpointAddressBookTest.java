package com.limelight.ligase.endpoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.computers.ComputerDatabaseManager;
import com.limelight.nvstream.http.ComputerDetails;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class LigaseEndpointAddressBookTest {
    @Test
    public void migratesLegacyFourSlotJsonWithoutChangingIpv4OrPort() throws Exception {
        JSONObject legacy = new JSONObject()
                .put("local", new JSONObject()
                        .put("address", "10.168.1.191")
                        .put("port", 49989))
                .put("remote", JSONObject.NULL)
                .put("manual", new JSONObject()
                        .put("address", "ligase-host.local")
                        .put("port", 48989))
                .put("ipv6", new JSONObject()
                        .put("address", "fe80::1234%wlan0")
                        .put("port", 49989));

        List<LigaseEndpoint> migrated = LigaseEndpointAddressBook.decode(legacy);

        assertEquals(3, migrated.size());
        assertEquals("10.168.1.191", migrated.get(0).host);
        assertEquals(49989, migrated.get(0).port);
        assertEquals(LigaseEndpoint.Source.LOCAL, migrated.get(0).source);
        assertEquals("ligase-host.local", migrated.get(1).host);
        assertEquals("fe80::1234", migrated.get(2).host);
        assertEquals("wlan0", migrated.get(2).zone);
    }

    @Test
    public void writesExactV2ShapeAndRoundTripsCandidates() throws Exception {
        LigaseEndpoint manual = LigaseEndpointParser.parseManual(
                "[2001:db8::8]:49989", "", 48989);
        LigaseEndpoint remote = LigaseEndpointParser.parse(
                LigaseEndpoint.Scheme.HTTPS,
                "host.example",
                49984,
                null,
                LigaseEndpoint.Source.REMOTE);

        JSONObject encoded = LigaseEndpointAddressBook.encode(Arrays.asList(manual, remote));
        List<LigaseEndpoint> decoded = LigaseEndpointAddressBook.decode(encoded);

        assertEquals(2, encoded.getInt("version"));
        assertEquals(2, encoded.getJSONArray("endpoints").length());
        assertEquals("http", encoded.getJSONArray("endpoints")
                .getJSONObject(0).getString("scheme"));
        assertEquals("2001:db8::8", encoded.getJSONArray("endpoints")
                .getJSONObject(0).getString("host"));
        assertEquals(Arrays.asList(manual, remote), decoded);
    }

    @Test
    public void deduplicatesByNormalizedEndpointNotSource() throws Exception {
        LigaseEndpoint manual = LigaseEndpointParser.parse(
                LigaseEndpoint.Scheme.HTTP,
                "Ligase.Local",
                48989,
                null,
                LigaseEndpoint.Source.MANUAL);
        LigaseEndpoint mdns = LigaseEndpointParser.parse(
                LigaseEndpoint.Scheme.HTTP,
                "ligase.local",
                48989,
                null,
                LigaseEndpoint.Source.MDNS);

        JSONObject encoded = LigaseEndpointAddressBook.encode(Arrays.asList(manual, mdns));

        assertEquals(1, encoded.getJSONArray("endpoints").length());
        assertEquals("manual", encoded.getJSONArray("endpoints")
                .getJSONObject(0).getString("source"));
    }

    @Test
    public void projectsV2CandidatesIntoLegacyTransportSlots() {
        LigaseEndpoint local = LigaseEndpointParser.parse(
                LigaseEndpoint.Scheme.HTTP,
                "10.0.0.5",
                48989,
                null,
                LigaseEndpoint.Source.LOCAL);
        LigaseEndpoint manual = LigaseEndpointParser.parseManual(
                "ligase.local", "49989", 48989);
        ComputerDetails details = new ComputerDetails();

        LigaseEndpointAddressBook.projectLegacyFields(details, Arrays.asList(local, manual));

        assertNotNull(details.localAddress);
        assertEquals("10.0.0.5", details.localAddress.address);
        assertNotNull(details.manualAddress);
        assertEquals("ligase.local", details.manualAddress.address);
        assertEquals(49989, details.manualAddress.port);
        assertNull(details.remoteAddress);
        assertNull(details.ipv6Address);
    }

    @Test
    public void preservesAllCandidatesEvenWhenLegacyProjectionHasOneSlotPerSource() throws Exception {
        LigaseEndpoint first = LigaseEndpointParser.parse(
                LigaseEndpoint.Scheme.HTTP,
                "10.0.0.5",
                48989,
                null,
                LigaseEndpoint.Source.LOCAL);
        LigaseEndpoint second = LigaseEndpointParser.parse(
                LigaseEndpoint.Scheme.HTTP,
                "10.0.0.6",
                48989,
                null,
                LigaseEndpoint.Source.LOCAL);

        JSONObject encoded = LigaseEndpointAddressBook.encode(Arrays.asList(first, second));

        assertEquals(2, LigaseEndpointAddressBook.decode(encoded).size());
        assertTrue(encoded.toString().contains("\"version\":2"));
    }

    @Test
    public void databaseReadsLegacyRecordAndWritesItBackAsV2WithoutChangingIdentity()
            throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("computers4.db");
        try {
            ComputerDetails seed = new ComputerDetails();
            seed.uuid = "53beb7ec-9788-cc23-461a-061f153029a5";
            seed.name = "Ligase Host";
            seed.manualAddress = new ComputerDetails.AddressTuple("10.168.1.191", 49989);
            ComputerDatabaseManager manager = new ComputerDatabaseManager(context);
            manager.updateComputer(seed);
            manager.close();

            JSONObject legacy = new JSONObject()
                    .put("local", JSONObject.NULL)
                    .put("remote", JSONObject.NULL)
                    .put("manual", new JSONObject()
                            .put("address", "10.168.1.191")
                            .put("port", 49989))
                    .put("ipv6", JSONObject.NULL);
            SQLiteDatabase raw = context.openOrCreateDatabase("computers4.db", 0, null);
            ContentValues values = new ContentValues();
            values.put("Addresses", legacy.toString());
            raw.update(
                    "Computers",
                    values,
                    "UUID=?",
                    new String[]{seed.uuid});
            raw.close();

            manager = new ComputerDatabaseManager(context);
            ComputerDetails migrated = manager.getComputerByUUID(seed.uuid);
            assertNotNull(migrated);
            assertEquals(seed.uuid, migrated.uuid);
            assertEquals("10.168.1.191", migrated.manualAddress.address);
            assertEquals(49989, migrated.manualAddress.port);
            assertEquals(1, migrated.endpoints.size());
            manager.updateComputer(migrated);
            manager.close();

            raw = context.openOrCreateDatabase("computers4.db", 0, null);
            android.database.Cursor cursor = raw.rawQuery(
                    "SELECT Addresses FROM Computers WHERE UUID=?",
                    new String[]{seed.uuid});
            assertTrue(cursor.moveToFirst());
            JSONObject persisted = new JSONObject(cursor.getString(0));
            assertEquals(2, persisted.getInt("version"));
            assertEquals("10.168.1.191", persisted.getJSONArray("endpoints")
                    .getJSONObject(0).getString("host"));
            cursor.close();
            raw.close();
        }
        finally {
            context.deleteDatabase("computers4.db");
        }
    }
}
