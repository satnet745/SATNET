package org.servalproject.permissions;

import android.os.Build;

import org.junit.Test;
import org.servalproject.ui.Networks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuntimePermissionGateTest {

    @Test
    public void returnsTrueWhenActivityIsNull() {
        assertTrue(RuntimePermissionGate.ensurePermissions(null, new String[]{"android.permission.CAMERA"}, 1));
    }

    @Test
    public void returnsTrueWhenPermissionsAreEmpty() {
        assertTrue(RuntimePermissionGate.ensurePermissions(null, new String[0], 1));
    }

    @Test
    public void hasPermissionsReturnsTrueWhenContextIsNull() {
        assertTrue(RuntimePermissionGate.hasPermissions(null, new String[]{"android.permission.CAMERA"}));
    }

    @Test
    public void hasPermissionsReturnsTrueWhenPermissionsAreEmpty() {
        assertTrue(RuntimePermissionGate.hasPermissions(null, new String[0]));
    }

    @Test
    public void getMissingPermissionsReturnsEmptyWhenContextIsNull() {
        assertEquals(0, RuntimePermissionGate.getMissingPermissions(null, new String[]{"android.permission.CAMERA"}).length);
    }

    @Test
    public void getMissingPermissionsReturnsEmptyWhenPermissionsAreEmpty() {
        assertEquals(0, RuntimePermissionGate.getMissingPermissions(null, new String[0]).length);
    }

    @Test
    public void rationaleHelperReturnsFalseForNullActivity() {
        assertFalse(RuntimePermissionGate.shouldShowAnyRequestPermissionRationale(null, new String[]{"android.permission.CAMERA"}));
    }

    @Test
    public void rationaleHelperReturnsFalseForEmptyPermissions() {
        assertFalse(RuntimePermissionGate.shouldShowAnyRequestPermissionRationale(null, new String[0]));
    }

    @Test
    public void bluetoothRuntimePermissionsAreEmptyBeforeAndroid12() {
        assertEquals(0, Networks.getBluetoothRuntimePermissionsForSdk(Build.VERSION_CODES.R).length);
    }

    @Test
    public void bluetoothRuntimePermissionsIncludeModernBluetoothPermissionsOnAndroid12Plus() {
        String[] permissions = Networks.getBluetoothRuntimePermissionsForSdk(Build.VERSION_CODES.S);

        assertEquals(3, permissions.length);
        assertEquals("android.permission.BLUETOOTH_CONNECT", permissions[0]);
        assertEquals("android.permission.BLUETOOTH_SCAN", permissions[1]);
        assertEquals("android.permission.BLUETOOTH_ADVERTISE", permissions[2]);
        assertFalse(permissions == Networks.getBluetoothRuntimePermissionsForSdk(Build.VERSION_CODES.S));
    }
}

