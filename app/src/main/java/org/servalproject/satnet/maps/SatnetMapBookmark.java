package org.servalproject.satnet.maps;

import java.util.Locale;
import java.util.UUID;

public final class SatnetMapBookmark {
    private static final int MAX_LABEL_LENGTH = 48;
    private static final int MAX_NOTE_LENGTH = 160;

    public String id;
    public String label;
    public String note;
    public double latitude;
    public double longitude;
    public long createdAtMs;

    public SatnetMapBookmark() {
        // Required for Gson deserialization.
    }

    public SatnetMapBookmark(String id, String label, String note, double latitude, double longitude, long createdAtMs) {
        this.id = normalizeId(id);
        this.label = normalizeLabel(label);
        this.note = normalizeNote(note);
        this.latitude = latitude;
        this.longitude = longitude;
        this.createdAtMs = createdAtMs > 0L ? createdAtMs : System.currentTimeMillis();
    }

    public static SatnetMapBookmark create(String label, String note, double latitude, double longitude) {
        return new SatnetMapBookmark(UUID.randomUUID().toString(), label, note, latitude, longitude, System.currentTimeMillis());
    }

    public boolean isValid() {
        return isFinite(latitude)
                && isFinite(longitude)
                && latitude >= -90.0d
                && latitude <= 90.0d
                && longitude >= -180.0d
                && longitude <= 180.0d;
    }

    public SatnetMapBookmark sanitizedCopy() {
        return new SatnetMapBookmark(id, label, note, latitude, longitude, createdAtMs);
    }

    public String getDisplayLabel() {
        return normalizeLabel(label);
    }

    public String getDisplayNote() {
        return normalizeNote(note);
    }

    public String getCoordinateSummary() {
        return String.format(Locale.US, "%.5f, %.5f", latitude, longitude);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String normalizeId(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? UUID.randomUUID().toString() : trimmed;
    }

    private static String normalizeLabel(String value) {
        String collapsed = collapseWhitespace(value);
        if (collapsed.isEmpty()) {
            return "Pinned location";
        }
        return collapsed.length() > MAX_LABEL_LENGTH
                ? collapsed.substring(0, MAX_LABEL_LENGTH).trim()
                : collapsed;
    }

    private static String normalizeNote(String value) {
        String collapsed = collapseWhitespace(value);
        if (collapsed.length() > MAX_NOTE_LENGTH) {
            return collapsed.substring(0, MAX_NOTE_LENGTH).trim();
        }
        return collapsed;
    }

    private static String collapseWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}

