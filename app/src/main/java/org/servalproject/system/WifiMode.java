/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2011 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */
package org.servalproject.system;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.shell.CommandCapture;
import org.servalproject.shell.Shell;

import android.util.Log;

public enum WifiMode {
	Adhoc(120, "Adhoc"),
	Direct(0, "Wifi-Direct"),
	Client(90, "Client"),
	Ap(45, "Access Point"),
	Off(5 * 60, "Off"),
	Unsupported(0, "Unsupported"),
	Unknown(0, "Unknown");

	int sleepTime;
	String display;

	WifiMode(int sleepTime, String display) {
		this.sleepTime = sleepTime;
		this.display = display;
	}

	public String getDisplay() {
		return display;
	}

	private static WifiMode values[] = WifiMode.values();

	public static WifiMode nextMode(WifiMode m) {
		// return the next wifi mode
		if (m == null || m.ordinal() + 1 == values.length)
			return values[0];

		return values[m.ordinal() + 1];
	}

	public static String lastIwconfigOutput;
	private static Pattern iwTypePattern = Pattern.compile("type\\s(\\w+)");

	public static WifiMode getWiFiMode(Shell rootShell, String interfaceName,
			String ipAddr) {
		if (rootShell == null)
			throw new NullPointerException();

		CoreTask coretask = ServalBatPhoneApplication.context.coretask;
		NetworkInterface networkInterface = null;
		lastIwconfigOutput = null;
		boolean hasMatchingAddress = false;

		try {
			networkInterface = NetworkInterface
					.getByName(interfaceName);

			// interface doesn't exist? must be off.
			if (networkInterface == null)
				return WifiMode.Off;

			if (!networkInterface.isUp()) {
				/*
				 * With nl80211 drivers, network type is kept even when network
				 * interface is down
				 */
				return WifiMode.Off;
			}

			boolean hasAddress = false;
			if (ipAddr != null && ipAddr.contains("/"))
				ipAddr = ipAddr.substring(0, ipAddr.indexOf('/'));

			// the interface may exist, but is currently down.
			for (Enumeration<InetAddress> enumIpAddress = networkInterface
					.getInetAddresses(); enumIpAddress
					.hasMoreElements();) {
				InetAddress iNetAddress = enumIpAddress.nextElement();
				if (!iNetAddress.isLoopbackAddress()) {
					hasAddress = true;
					if (ipAddr != null
							&& ipAddr.equals(iNetAddress.getHostAddress()))
						hasMatchingAddress = true;
				}
			}

			if (!hasAddress)
				return WifiMode.Off;

		} catch (Exception e) {
			Log.e("WifiMode", e.getMessage(), e);
			return WifiMode.Off;
		}

		if (ChipsetDetection.getDetection().getWifiChipset().nl80211) {
			try {
				CommandCapture c = new CommandCapture(
						coretask.DATA_FILE_PATH + "/bin/iw dev "
								+ interfaceName + " info");
				rootShell.run(c);

				if (c.exitCode() == 0) {
					String iw = c.toString();
					lastIwconfigOutput = iw;

					Matcher m = iwTypePattern.matcher(c.toString());
					if (m.find()) {
									String type = m.group(1).toLowerCase(Locale.ROOT);
						if ("managed".equals(type)) {
							return WifiMode.Client;
						}
						if ("ibss".equals(type)) {
							return WifiMode.Adhoc;
						}
									if ("ap".equals(type)) {
							return WifiMode.Ap;
						}
						// type not recognised
						return WifiMode.Unknown;
					} else {
						return WifiMode.Off;
					}
				}
				/* fall through */
			} catch (Exception e) {
				Log.v("WifiMode", e.getMessage(), e);
			}
		}

		if (!ChipsetDetection.getDetection().getWifiChipset()
				.lacksWirelessExtensions()) {
			try {
				// find out what mode the wifi interface is in by asking
				// iwconfig
				// The native iwstatus (iwconfig read-only command) here doesn't
				// work,
				// even though the same code from the same library works from
				// the command
				// line (this is because iwconfig requires root to READ the wifi
				// mode).
				// public static native String iwstatus(String s);
				CommandCapture c = new CommandCapture(
						coretask.DATA_FILE_PATH + "/bin/iwconfig "
								+ interfaceName);
				rootShell.run(c);

				String iw = c.toString();
				lastIwconfigOutput = iw;

				if (iw.contains("Mode:")) {
					// not sure why, but if not run as root, mode is
					// incorrect
					// (this is because iwconfig needs to be run as root to
					// correctly
					// return the wifi mode -- this is probably a linux
					// kernel/wifi
					// driver bug).
					if (rootShell.isRoot) {
						int b = iw.indexOf("Mode:") + 5;
						int e = iw.substring(b).indexOf(" ");
						String mode = iw.substring(b, b + e).toLowerCase(Locale.ROOT);

						if (mode.contains("adhoc")
								|| mode.contains("ad-hoc"))
							return WifiMode.Adhoc;
						if (mode.contains("client")
								|| mode.contains("managed"))
							return WifiMode.Client;
						if (mode.contains("master"))
							return WifiMode.Ap;
					}

					// Found, but unrecognised = unknown
					return WifiMode.Unknown;
				}

				return WifiMode.Off;
			} catch (Exception e) {
				Log.e("WifiMode", e.getMessage(), e);
				return WifiMode.Unknown;
			}
		}

		if (ipAddr != null && hasMatchingAddress)
			return WifiMode.Unknown;

		return WifiMode.Off;
	}
}
