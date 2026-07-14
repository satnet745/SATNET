/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2011 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */
/*
 * Includes code from Paul James Mutton's Mini Web Server / SimpleWebServer.
 * Copyright Paul James Mutton, 2001-2004, <http://www.jibble.org/>
 * Original project notes described dual GPL/commercial licensing; see the
 * original upstream distribution for full third-party license details.
 */

package org.servalproject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.annotation.SuppressLint;
import android.util.Log;

import org.servalproject.relay.RelayServer;

/**
 * Derived from <https://www.jibble.org/>. Copyright Paul Mutton.
 */
public class RequestThread extends Thread {

	private final Socket _socket;

	public static final Hashtable<String, String> MIME_TYPES = new Hashtable<>();

	static {
		String image = "image/";
		MIME_TYPES.put(".gif", image + "gif");
		MIME_TYPES.put(".jpg", image + "jpeg");
		MIME_TYPES.put(".jpeg", image + "jpeg");
		MIME_TYPES.put(".png", image + "png");
		String text = "text/";
		MIME_TYPES.put(".html", text + "html");
		MIME_TYPES.put(".htm", text + "html");
		MIME_TYPES.put(".txt", text + "plain");
		MIME_TYPES.put(".css", text + "css");
		MIME_TYPES.put(".apk", "application/vnd.android.package-archive");
	}

	public RequestThread(Socket socket) {
        _socket = socket;
    }

	private void sendHeader(OutputStream out, int code, String contentType,
			long contentLength, long lastModified) throws IOException {
		String header = "HTTP/1.0 "
				+ code
				+ " OK\n" +
				"Date: "
				+ new Date()
				+ "\n" +
				"Content-Type: "
				+ contentType
				+ "\n" +
				"Connection: close\n" +
				"Expires: Thu, 01 Dec 1994 16:00:00 GMT\n" +
				"Cache-Control: no-cache\n" +
				((contentLength != -1) ? "Content-Length: " + contentLength
						+ "\n" : "") +
				"Last-modified: " + new Date(lastModified) + "\n\n";
		Log.v("BatPhone", "Returning header\n" + header);
		writeString(out, header);
    }

	private void sendError(OutputStream out, int code, String message)
			throws IOException {
		sendHeader(out, code, "text/html", message.length(), System.currentTimeMillis());
		writeString(out, message);
	}

	private void sendJson(OutputStream out, int code, String json) throws IOException {
		sendHeader(out, code, "application/json", json.getBytes(StandardCharsets.UTF_8).length,
				System.currentTimeMillis());
		writeString(out, json);
	}

	private String appName(PackageManager packageManager, PackageInfo info) {
		ApplicationInfo appInfo = info.applicationInfo;
		if (appInfo == null
				|| (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
			return null;

		String name = appInfo.name;
		if (name == null) {
			name = appInfo.loadLabel(packageManager).toString();
		}
		return name;
	}

	private static class HTTPException extends Exception {
		private static final long serialVersionUID = 1L;

		int code;

		HTTPException(int code, String text) {
			super(text);
			this.code = code;
		}
	}

	private void writeString(OutputStream out, String str) throws IOException {
		out.write(str.getBytes(StandardCharsets.UTF_8));
	}

	@SuppressLint("QueryPermissionsNeeded")
	private void listPackages(String path, OutputStream out) throws IOException {
		final PackageManager packageManager = ServalBatPhoneApplication.context
				.getPackageManager();
		List<PackageInfo> packages = packageManager
				.getInstalledPackages(0);
		Set<PackageInfo> sortedPackages = new TreeSet<>((object1, object2) -> {
			String name1 = appName(packageManager, object1);
			if (name1 == null)
				return -1;
			String name2 = appName(packageManager, object2);
			if (name2 == null)
				return 1;
			return name1.compareTo(name2);
		});

		for (PackageInfo info : packages) {
			ApplicationInfo appInfo = info.applicationInfo;
			if (appInfo == null
					|| (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
				continue;
			sortedPackages.add(info);
		}

		sendHeader(out, 200, "text/html", -1,
				System.currentTimeMillis());
		writeString(out, "<html><head><title>Index of " + path
				+ "</title></head><body><h3>Index of " + path + "</h3><p>\n");

		for (PackageInfo info : sortedPackages) {
			ApplicationInfo appInfo = info.applicationInfo;
			if (appInfo == null
					|| (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
				continue;

			writeString(out, "<a href=\"/packages/" + appInfo.packageName
					+ ".apk\">" + appName(packageManager, info)
					+ "</a> " + info.versionName + "<br>\n");
		}
		writeString(out, "</p></body></html>");
	}

	private boolean handleRelayApi(String method, String path, BufferedReader in, OutputStream out,
			int contentLength) throws IOException {
		RelayServer relayServer = ServalBatPhoneApplication.context.relayServer;
		if (!path.startsWith("/api/relay")) {
			return false;
		}

		if (relayServer == null) {
			sendJson(out, 503, "{\"status\":\"offline\"}");
			return true;
		}

		if ("GET".equals(method) && "/api/relay/status".equals(path)) {
			String json = String.format(Locale.ROOT,
					"{\"status\":\"online\",\"relayPort\":%d,\"clients\":%d,\"queued\":%d}",
					relayServer.getPort(),
					relayServer.getConnectedClientCount(),
					relayServer.getQueuedPacketCount());
			sendJson(out, 200, json);
			return true;
		}

		if ("POST".equals(method) && "/api/relay/send".equals(path)) {
			char[] body = new char[Math.max(0, contentLength)];
			int offset = 0;
			while (offset < body.length) {
				int read = in.read(body, offset, body.length - offset);
				if (read < 0)
					break;
				offset += read;
			}
			String payload = new String(body, 0, offset).trim();
			boolean accepted = relayServer.submitPacket(payload);
			sendJson(out, accepted ? 202 : 400,
					accepted ? "{\"status\":\"accepted\"}" : "{\"status\":\"invalid\"}");
			return true;
		}

		sendJson(out, 404, "{\"status\":\"not_found\"}");
		return true;
	}

    @Override
	public void run() {
		BufferedReader in;
		OutputStream out = null;
		InputStream content = null;

		try {
			_socket.setSoTimeout(30000);
			_socket.setSendBufferSize(4096);
			in = new BufferedReader(new InputStreamReader(
					_socket.getInputStream()), 256);
			out = _socket.getOutputStream();

			String request = in.readLine();
			if (request == null) {
				throw new HTTPException(500, "Invalid Method.");
			}
			String[] parts = request.split(" ");
			if (parts.length < 3 || !(parts[2].endsWith("HTTP/1.1") || parts[2].endsWith("HTTP/1.0"))) {
				throw new HTTPException(500, "Invalid Method.");
			}
			String method = parts[0];
			String path = parts[1];
			Log.v("BatPhone", request);
			int contentLength = 0;
			while (request != null && !request.isEmpty()) {
				request = in.readLine();
				if (request != null && request.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
					try {
						contentLength = Integer.parseInt(request.substring("content-length:".length()).trim());
					} catch (NumberFormatException e) {
						contentLength = 0;
					}
				}
				Log.v("BatPhone", request);
			}

			if (handleRelayApi(method, path, in, out, contentLength)) {
				return;
			}

			if (!"GET".equals(method)) {
				throw new HTTPException(500, "Invalid Method.");
			}
			String contentType = null;
			long responseLength = -1;
			long contentModified = System.currentTimeMillis();

			if (path.equals("/packages")) {
				listPackages(path, out);
				return;
			}

			if (path.startsWith("/packages/")) {
				final PackageManager packageManager = ServalBatPhoneApplication.context
						.getPackageManager();

				PackageInfo info = packageManager.getPackageInfo(
						path.substring(path.lastIndexOf('/') + 1,
								path.lastIndexOf('.')), 0);
				ApplicationInfo appInfo = info.applicationInfo;
				File file = new File(appInfo.sourceDir).getCanonicalFile();
				if (!file.exists())
					throw new HTTPException(404, "File Not Found.");
				Log.v("BatPhone", "Serving file " + file);

				contentType = MIME_TYPES.get(".apk");
				responseLength = file.length();
				contentModified = file.lastModified();
				content = new BufferedInputStream(new FileInputStream(file),
						4096);
			} else {
				AssetManager am = ServalBatPhoneApplication.context.getAssets();
				if (path.indexOf('?') >= 0)
					path = path.substring(0, path.indexOf('?'));
				if (path.equals("/"))
					path = "/index.html";

				int ext = path.lastIndexOf('.');
				if (path.lastIndexOf('/') > ext)
					ext = -1;
				if (ext >= 0)
					contentType = MIME_TYPES.get(path
							.substring(ext).toLowerCase(Locale.ROOT));
				content = am.open(path.substring(1));
				Log.v("BatPhone", "Serving asset " + path.substring(1));
			}


			if (contentType == null)
				contentType = "application/octet-stream";

			sendHeader(out, 200, contentType, responseLength, contentModified);

			byte[] buffer = new byte[256];
			int bytesRead;
			while ((bytesRead = content.read(buffer)) != -1) {
				Log.v("BatPhone", "read " + bytesRead + " bytes");
				if (bytesRead > 0) {
					out.write(buffer, 0, bytesRead);
					Log.v("BatPhone", "written");
				}
			}
			_socket.shutdownInput();
			_socket.shutdownOutput();
			Log.v("BatPhone", "Done");
		} catch (NameNotFoundException e) {
			try {
				sendError(out, 404, "File Not Found.");
			} catch (IOException e1) {
				Log.v("BatPhone", e1.getMessage(), e1);
			}
		} catch (HTTPException e) {
			try {
				sendError(out, e.code, e.getMessage());
			} catch (IOException e1) {
				Log.v("BatPhone", e1.getMessage(), e1);
			}
		} catch (IOException e) {
			Log.v("BatPhone", e.getMessage(), e);
		} catch (Exception e) {
			Log.v("BatPhone", e.getMessage(), e);
			try {
				sendError(out, 500, e.getMessage());
			} catch (IOException e1) {
				Log.v("BatPhone", e1.getMessage(), e1);
			}
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e1) {
					Log.v("BatPhone", String.valueOf(e1.getMessage()), e1);
				}
			}
			if (content != null) {
				try {
					content.close();
				} catch (IOException e1) {
					Log.v("BatPhone", String.valueOf(e1.getMessage()), e1);
				}
			}
		}
    }


}