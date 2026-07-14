package org.servalproject.satnet;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WalletSessionStore {
	public static final String EXTRA_SESSION_TOKEN = "wallet_session_token";
	private static final long SESSION_TTL_MS = 2 * 60 * 1000L;
	private static final Map<String, SessionEntry> SESSIONS = new ConcurrentHashMap<String, SessionEntry>();

	private WalletSessionStore() {
	}

	public static String createSession(String pin) {
		char[] pinChars = pin == null ? null : pin.toCharArray();
		try {
			return createSession(pinChars);
		} finally {
			clearChars(pinChars);
		}
	}

	public static String createSession(char[] pinChars) {
		if (pinChars == null || pinChars.length == 0) {
			throw new IllegalArgumentException("PIN is required to create a wallet session");
		}
		pruneExpired();
		String token = UUID.randomUUID().toString();
		SESSIONS.put(token, new SessionEntry(pinChars, System.currentTimeMillis() + SESSION_TTL_MS));
		return token;
	}

	public static String getPin(String token) {
		char[] pinChars = getPinChars(token);
		if (pinChars == null || pinChars.length == 0) {
			return null;
		}
		try {
			return new String(pinChars);
		} finally {
			clearChars(pinChars);
		}
	}

	public static char[] getPinChars(String token) {
		if (token == null || token.isEmpty()) {
			return null;
		}
		pruneExpired();
		SessionEntry sessionEntry = SESSIONS.get(token);
		if (sessionEntry == null || sessionEntry.expiresAtMs < System.currentTimeMillis()) {
			invalidate(token);
			return null;
		}
		return sessionEntry.copyPinChars();
	}

	public static SessionAccess refreshSession(String token) {
		char[] pinChars = getPinChars(token);
		if (pinChars == null || pinChars.length == 0) {
			return null;
		}
		try {
			String refreshedToken = createSession(pinChars);
			invalidate(token);
			return new SessionAccess(refreshedToken, pinChars);
		} catch (RuntimeException e) {
			clearChars(pinChars);
			throw e;
		}
	}

	public static void invalidate(String token) {
		if (token != null) {
			SessionEntry removed = SESSIONS.remove(token);
			if (removed != null) {
				removed.clear();
			}
		}
	}

	public static void pruneExpired() {
		long now = System.currentTimeMillis();
		for (Map.Entry<String, SessionEntry> entry : SESSIONS.entrySet()) {
			if (entry.getValue().expiresAtMs < now) {
				invalidateIfSameEntry(entry.getKey(), entry.getValue());
			}
		}
	}

	private static void invalidateIfSameEntry(String token, SessionEntry expectedEntry) {
		if (token == null || expectedEntry == null) {
			return;
		}
		synchronized (SESSIONS) {
			SessionEntry currentEntry = SESSIONS.get(token);
			if (currentEntry == expectedEntry) {
				SESSIONS.remove(token);
				expectedEntry.clear();
			}
		}
	}

	private static void clearChars(char[] value) {
		if (value != null) {
			Arrays.fill(value, '\0');
		}
	}

	public static final class SessionAccess implements AutoCloseable {
		public final String token;
		private char[] pinChars;

		private SessionAccess(String token, char[] pinChars) {
			this.token = token;
			this.pinChars = pinChars;
		}

		public char[] consumePinChars() {
			return pinChars == null ? null : Arrays.copyOf(pinChars, pinChars.length);
		}

		@Override
		public void close() {
			clearChars(pinChars);
			pinChars = null;
		}
	}

	private static final class SessionEntry {
		final char[] pinChars;
		final long expiresAtMs;

		SessionEntry(char[] pinChars, long expiresAtMs) {
			this.pinChars = Arrays.copyOf(pinChars, pinChars.length);
			this.expiresAtMs = expiresAtMs;
		}

		char[] copyPinChars() {
			return Arrays.copyOf(pinChars, pinChars.length);
		}

		void clear() {
			clearChars(pinChars);
		}
	}
}

