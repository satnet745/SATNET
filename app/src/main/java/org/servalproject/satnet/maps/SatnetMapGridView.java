package org.servalproject.satnet.maps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SatnetMapGridView extends View {
    private static final float WORLD_MIN_LAT = -90f;
    private static final float WORLD_MAX_LAT = 90f;
    private static final float WORLD_MIN_LON = -180f;
    private static final float WORLD_MAX_LON = 180f;

    private final Paint backgroundPaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bookmarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint currentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint meshPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trustPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF contentRect = new RectF();
    private List<SatnetMapBookmark> bookmarks = Collections.emptyList();
    private Marker currentMarker;
    private SatnetMapRoleOverlay roleOverlay;
    private SatnetMeshOverlaySnapshot meshOverlay = SatnetMeshOverlaySnapshot.EMPTY;
    private SatnetPeerTrustSnapshot peerTrustOverlay = SatnetPeerTrustSnapshot.EMPTY;
    private String emptyStateText = "No locations selected";

    public SatnetMapGridView(Context context) {
        super(context);
        init();
    }

    public SatnetMapGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SatnetMapGridView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        backgroundPaint.setColor(Color.parseColor("#F7F9FC"));

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.5f * density);
        borderPaint.setColor(Color.parseColor("#CFD8E3"));

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f * density);
        gridPaint.setColor(Color.parseColor("#E5EBF3"));

        axisPaint.setStyle(Paint.Style.STROKE);
        axisPaint.setStrokeWidth(1.6f * density);
        axisPaint.setColor(Color.parseColor("#8FA0B8"));

        bookmarkPaint.setStyle(Paint.Style.FILL);
        bookmarkPaint.setColor(Color.parseColor("#1565C0"));

        currentPaint.setStyle(Paint.Style.FILL);
        currentPaint.setColor(Color.parseColor("#D32F2F"));

        overlayPaint.setStyle(Paint.Style.FILL);
        overlayPaint.setColor(Color.parseColor("#331565C0"));

        meshPaint.setStyle(Paint.Style.STROKE);
        meshPaint.setStrokeWidth(1.3f * density);
        meshPaint.setColor(Color.parseColor("#668FA0B8"));

        trustPaint.setStyle(Paint.Style.STROKE);
        trustPaint.setStrokeWidth(1.8f * density);
        trustPaint.setColor(Color.parseColor("#66546E7A"));

        textPaint.setColor(Color.parseColor("#213041"));
        textPaint.setTextSize(spToPx(12f));

        hintPaint.setColor(Color.parseColor("#5E6D7E"));
        hintPaint.setTextSize(spToPx(13f));

        setMinimumHeight((int) dpToPx(260f));
    }

    public void setEmptyStateText(String emptyStateText) {
        this.emptyStateText = emptyStateText == null ? "" : emptyStateText;
        invalidate();
    }

    public void setBookmarks(List<SatnetMapBookmark> bookmarks) {
        if (bookmarks == null || bookmarks.isEmpty()) {
            this.bookmarks = Collections.emptyList();
        } else {
            this.bookmarks = new ArrayList<SatnetMapBookmark>(bookmarks);
        }
        invalidate();
    }

    public void setCurrentMarker(double latitude, double longitude, String label) {
        currentMarker = new Marker(latitude, longitude, label);
        invalidate();
    }

    public void clearCurrentMarker() {
        currentMarker = null;
        invalidate();
    }

    public void setRoleOverlay(SatnetMapRoleOverlay roleOverlay) {
        this.roleOverlay = roleOverlay;
        invalidate();
    }

    public void setMeshOverlay(SatnetMeshOverlaySnapshot meshOverlay) {
        this.meshOverlay = meshOverlay == null ? SatnetMeshOverlaySnapshot.EMPTY : meshOverlay;
        invalidate();
    }

    public void setPeerTrustOverlay(SatnetPeerTrustSnapshot peerTrustOverlay) {
        this.peerTrustOverlay = peerTrustOverlay == null ? SatnetPeerTrustSnapshot.EMPTY : peerTrustOverlay;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        contentRect.set(
                getPaddingLeft() + dpToPx(12f),
                getPaddingTop() + dpToPx(12f),
                getWidth() - getPaddingRight() - dpToPx(12f),
                getHeight() - getPaddingBottom() - dpToPx(12f));

        canvas.drawRect(contentRect, backgroundPaint);
        drawGrid(canvas);
        canvas.drawRect(contentRect, borderPaint);
        drawMeshOverlay(canvas);
        drawTrustOverlay(canvas);
        drawRoleOverlay(canvas);
        drawAxisLabels(canvas);
        drawBookmarks(canvas);
        drawCurrentMarker(canvas);
        drawEmptyState(canvas);
    }

    private void drawMeshOverlay(Canvas canvas) {
        if (meshOverlay == null || !meshOverlay.hasCoverage()) {
            return;
        }
        float centerX = contentRect.centerX();
        float centerY = contentRect.centerY();
        float baseRadius = Math.min(contentRect.width(), contentRect.height()) * 0.12f;
        float directRadius = baseRadius + (meshOverlay.directPeerCount * dpToPx(4f));
        float relayedRadius = directRadius + (meshOverlay.relayedPeerCount * dpToPx(5f));
        meshPaint.setColor(Color.parseColor("#6680CBC4"));
        canvas.drawCircle(centerX, centerY, directRadius, meshPaint);
        if (meshOverlay.relayedPeerCount > 0) {
            meshPaint.setColor(Color.parseColor("#66FFB300"));
            canvas.drawCircle(centerX, centerY, relayedRadius, meshPaint);
        }
        drawLabel(canvas, meshOverlay.getCompactSummary(), centerX - dpToPx(90f), contentRect.top + dpToPx(32f), hintPaint);
    }

    private void drawRoleOverlay(Canvas canvas) {
        if (roleOverlay == null) {
            return;
        }
        Marker anchor = currentMarker;
        if (anchor == null && !bookmarks.isEmpty()) {
            SatnetMapBookmark bookmark = bookmarks.get(0);
            if (bookmark != null && bookmark.isValid()) {
                anchor = new Marker(bookmark.latitude, bookmark.longitude, bookmark.getDisplayLabel());
            }
        }
        if (anchor != null) {
            float x = mapLongitudeToX(anchor.longitude);
            float y = mapLatitudeToY(anchor.latitude);
            int alphaColor = (roleOverlay.accentColor & 0x00FFFFFF) | 0x33000000;
            overlayPaint.setColor(alphaColor);
            float radius = Math.min(contentRect.width(), contentRect.height()) * roleOverlay.radiusScale;
            canvas.drawCircle(x, y, radius, overlayPaint);
        }
        Paint overlayTextPaint = new Paint(textPaint);
        overlayTextPaint.setColor(roleOverlay.accentColor);
        drawLabel(canvas, roleOverlay.title, contentRect.left + dpToPx(6f), contentRect.top + dpToPx(18f), overlayTextPaint);
    }

    private void drawTrustOverlay(Canvas canvas) {
        if (peerTrustOverlay == null || !peerTrustOverlay.hasEvidence()) {
            return;
        }
        float centerX = contentRect.centerX();
        float centerY = contentRect.centerY();
        float baseRadius = Math.min(contentRect.width(), contentRect.height()) * 0.16f;
        float localRadius = baseRadius + (peerTrustOverlay.localEvidenceCount * dpToPx(2f));
        float meshRadius = localRadius + (peerTrustOverlay.meshEvidenceCount * dpToPx(2.5f));
        float trustRadius = meshRadius + (peerTrustOverlay.trustedAuditCount * dpToPx(3f));
        float cautionRadius = trustRadius + (peerTrustOverlay.cautionAuditCount * dpToPx(3f));
        float rotationRadius = cautionRadius + (peerTrustOverlay.rotationAlertCount * dpToPx(2.5f));
        trustPaint.setColor(Color.parseColor("#88546E7A"));
        if (peerTrustOverlay.localEvidenceCount > 0) {
            canvas.drawCircle(centerX, centerY, localRadius, trustPaint);
        }
        if (peerTrustOverlay.meshEvidenceCount > 0) {
            trustPaint.setColor(Color.parseColor("#884A90E2"));
            canvas.drawCircle(centerX, centerY, meshRadius, trustPaint);
        }
        trustPaint.setColor((peerTrustOverlay.getOverlayColor() & 0x00FFFFFF) | 0x88000000);
        if (peerTrustOverlay.trustedAuditCount > 0) {
            canvas.drawCircle(centerX, centerY, trustRadius, trustPaint);
        }
        if (peerTrustOverlay.cautionAuditCount > 0) {
            trustPaint.setColor(Color.parseColor("#88EF6C00"));
            canvas.drawCircle(centerX, centerY, cautionRadius, trustPaint);
        }
        if (peerTrustOverlay.rotationAlertCount > 0) {
            trustPaint.setColor(Color.parseColor("#88D84315"));
            canvas.drawCircle(centerX, centerY, rotationRadius, trustPaint);
            float cross = rotationRadius * 0.42f;
            canvas.drawLine(centerX - cross, centerY - cross, centerX + cross, centerY + cross, trustPaint);
            canvas.drawLine(centerX - cross, centerY + cross, centerX + cross, centerY - cross, trustPaint);
        }
        Paint trustTextPaint = new Paint(hintPaint);
        trustTextPaint.setColor(peerTrustOverlay.getOverlayColor());
        drawLabel(canvas, peerTrustOverlay.getCompactSummary(), centerX - dpToPx(96f), contentRect.top + dpToPx(48f), trustTextPaint);
    }

    private void drawGrid(Canvas canvas) {
        for (int lon = -150; lon <= 150; lon += 30) {
            float x = mapLongitudeToX(lon);
            Paint paint = lon == 0 ? axisPaint : gridPaint;
            canvas.drawLine(x, contentRect.top, x, contentRect.bottom, paint);
        }

        for (int lat = -60; lat <= 60; lat += 30) {
            float y = mapLatitudeToY(lat);
            Paint paint = lat == 0 ? axisPaint : gridPaint;
            canvas.drawLine(contentRect.left, y, contentRect.right, y, paint);
        }
    }

    private void drawAxisLabels(Canvas canvas) {
        float topTextY = contentRect.top + dpToPx(18f);
        canvas.drawText("W", contentRect.left + dpToPx(8f), topTextY, textPaint);
        canvas.drawText("E", contentRect.right - dpToPx(18f), topTextY, textPaint);
        canvas.drawText("N", contentRect.centerX() - dpToPx(4f), contentRect.top + dpToPx(18f), textPaint);
        canvas.drawText("S", contentRect.centerX() - dpToPx(4f), contentRect.bottom - dpToPx(8f), textPaint);
        canvas.drawText("Offline privacy grid", contentRect.left + dpToPx(8f), contentRect.bottom - dpToPx(8f), hintPaint);
    }

    private void drawBookmarks(Canvas canvas) {
        for (SatnetMapBookmark bookmark : bookmarks) {
            if (bookmark == null || !bookmark.isValid()) {
                continue;
            }
            float x = mapLongitudeToX(bookmark.longitude);
            float y = mapLatitudeToY(bookmark.latitude);
            canvas.drawCircle(x, y, dpToPx(5f), bookmarkPaint);
            drawLabel(canvas, bookmark.getDisplayLabel(), x, y - dpToPx(8f), textPaint);
        }
    }

    private void drawCurrentMarker(Canvas canvas) {
        if (currentMarker == null) {
            return;
        }
        float x = mapLongitudeToX(currentMarker.longitude);
        float y = mapLatitudeToY(currentMarker.latitude);
        float radius = dpToPx(6f);
        canvas.drawCircle(x, y, radius, currentPaint);
        canvas.drawLine(x - radius * 1.8f, y, x + radius * 1.8f, y, currentPaint);
        canvas.drawLine(x, y - radius * 1.8f, x, y + radius * 1.8f, currentPaint);
        drawLabel(canvas, currentMarker.getDisplayLabel(), x, y - dpToPx(12f), textPaint);
    }

    private void drawEmptyState(Canvas canvas) {
        if (currentMarker != null || !bookmarks.isEmpty()) {
            return;
        }
        float textWidth = hintPaint.measureText(emptyStateText);
        canvas.drawText(emptyStateText,
                contentRect.centerX() - (textWidth / 2f),
                contentRect.centerY(),
                hintPaint);
    }

    private void drawLabel(Canvas canvas, String label, float x, float y, Paint paint) {
        if (label == null || label.trim().isEmpty()) {
            return;
        }
        String safeLabel = label.length() > 20 ? label.substring(0, 20).trim() + "…" : label;
        float maxX = contentRect.right - dpToPx(54f);
        float minX = contentRect.left + dpToPx(6f);
        float safeX = Math.max(minX, Math.min(x + dpToPx(8f), maxX));
        float safeY = Math.max(contentRect.top + dpToPx(18f), y);
        canvas.drawText(safeLabel, safeX, safeY, paint);
    }

    private float mapLongitudeToX(double longitude) {
        double normalized = (longitude - WORLD_MIN_LON) / (WORLD_MAX_LON - WORLD_MIN_LON);
        return (float) (contentRect.left + (normalized * contentRect.width()));
    }

    private float mapLatitudeToY(double latitude) {
        double normalized = (WORLD_MAX_LAT - latitude) / (WORLD_MAX_LAT - WORLD_MIN_LAT);
        return (float) (contentRect.top + (normalized * contentRect.height()));
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private float spToPx(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }

    private static final class Marker {
        final double latitude;
        final double longitude;
        final String label;

        Marker(double latitude, double longitude, String label) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.label = label;
        }

        String getDisplayLabel() {
            if (label == null || label.trim().isEmpty()) {
                return String.format(Locale.US, "%.2f, %.2f", latitude, longitude);
            }
            return label.trim();
        }
    }
}

