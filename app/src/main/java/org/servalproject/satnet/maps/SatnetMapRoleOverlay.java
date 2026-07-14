package org.servalproject.satnet.maps;

public final class SatnetMapRoleOverlay {
    public final String title;
    public final String summary;
    public final int accentColor;
    public final float radiusScale;

    public SatnetMapRoleOverlay(String title, String summary, int accentColor, float radiusScale) {
        this.title = title == null ? "" : title;
        this.summary = summary == null ? "" : summary;
        this.accentColor = accentColor;
        this.radiusScale = radiusScale <= 0f ? 0.16f : radiusScale;
    }
}

