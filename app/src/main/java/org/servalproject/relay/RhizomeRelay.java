package org.servalproject.relay;

import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.rhizome.Rhizome;
import org.servalproject.rhizome.RhizomeManifest;
import org.servalproject.rhizome.RhizomeManifest_File;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.keyring.KeyringIdentity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Delay-tolerant fallback transport for RelayPacket messages over Rhizome file bundles.
 */
@SuppressWarnings("deprecation")
public final class RhizomeRelay {
    private static final String TAG = "RhizomeRelay";
    private static final String PREFIX = "relay-";

    private RhizomeRelay() {
    }

    public static boolean isAvailable() {
        ServalBatPhoneApplication app = ServalBatPhoneApplication.context;
        return app != null && app.isRhizomeRuntimeReady();
    }

	@SuppressWarnings("deprecation")
    public static boolean publishPacket(RelayPacket packet) {
        if (!isAvailable()) {
            return false;
        }
        File payload = null;
        try {
            KeyringIdentity identity = ServalBatPhoneApplication.context.server.getIdentity();
            if (identity == null || identity.sid == null) {
                return false;
            }
            payload = File.createTempFile(PREFIX, ".txt", Rhizome.getTempDirectoryCreated());
            FileWriter writer = new FileWriter(payload);
            writer.write(packet.encode());
            writer.close();

            String name = PREFIX + identity.sid.abbreviation() + "-" + System.currentTimeMillis() + ".txt";
            ServalDCommand.rhizomeAddFile(payload, null, null, identity.sid, null, "name=" + name);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to publish Rhizome relay packet", e);
            return false;
        } finally {
            if (payload != null) {
                // keep payload around only until import completed
                Rhizome.safeDelete(payload);
            }
        }
    }

    public static boolean handleBundle(RhizomeManifest_File file) {
        try {
            String name = file.getName();
            if (name == null || !name.startsWith(PREFIX)) {
                return false;
            }
            File temp = new File(Rhizome.getTempDirectoryCreated(), file.getManifestId().toHex() + ".relay");
            ServalDCommand.rhizomeExtractFile(file.getManifestId(), temp);
            BufferedReader reader = new BufferedReader(new FileReader(temp));
            String line = reader.readLine();
            reader.close();
            Rhizome.safeDelete(temp);
            if (line == null) {
                return false;
            }
            return dispatchEncodedPacket(line);
        } catch (RhizomeManifest.MissingField e) {
            Log.w(TAG, "Relay bundle missing manifest field", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to process Rhizome relay bundle", e);
            return false;
        }
    }

    static boolean dispatchEncodedPacket(String encodedPacket) {
        try {
            RelayPacketDispatcher.dispatch(RelayPacket.decode(encodedPacket));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to dispatch encoded Rhizome packet", e);
            return false;
        }
    }
}

