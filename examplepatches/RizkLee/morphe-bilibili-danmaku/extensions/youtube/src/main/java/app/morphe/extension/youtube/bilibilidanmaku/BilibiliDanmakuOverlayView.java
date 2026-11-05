package app.morphe.extension.youtube.bilibilidanmaku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Video-time based overlay; rendering has no network or blocking operations. */
final class BilibiliDanmakuOverlayView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final float density;
    private final float scaledDensity;
    private List<DanmakuComment> comments = Collections.emptyList();
    private int[] lanes = new int[0];
    private float[] textWidths = new float[0];
    private boolean active;
    private boolean playing;
    private int fontSp = 19;
    private int opacity = 85;
    private int area = 75;
    private int travelSeconds = 8;
    private int maxLength = 120;
    private String blockedWords = "";
    private String[] blockedTokens = new String[0];
    private boolean hideColored;
    private boolean hideTopBottom;
    private double rate = 1.0;
    private long offsetMs;

    BilibiliDanmakuOverlayView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        fill.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        stroke.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f * density);
        stroke.setColor(Color.BLACK);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(GONE);
    }

    void setActive(boolean enabled) {
        active = enabled;
        setVisibility(enabled ? VISIBLE : GONE);
        invalidate();
    }

    void setPlaying(boolean value) {
        playing = value;
        invalidate();
    }

    void updateSettings(int font, int alpha, int travel, int areaPercent, int max,
                        boolean filterColored, boolean filterFixed, String keywords,
                        double timelineRate, long timelineOffset) {
        int newFont = Math.max(10, Math.min(40, font));
        int newArea = Math.max(20, Math.min(100, areaPercent));
        int newTravel = Math.max(3, Math.min(25, travel));
        boolean geometryChanged = newFont != fontSp || newArea != area || newTravel != travelSeconds
                || max != maxLength;
        if (newFont != fontSp) resetWidthCache();
        fontSp = newFont;
        opacity = Math.max(5, Math.min(100, alpha));
        travelSeconds = newTravel;
        area = newArea;
        maxLength = Math.max(1, Math.min(500, max));
        hideColored = filterColored;
        hideTopBottom = filterFixed;
        String newWords = keywords == null ? "" : keywords.toLowerCase(Locale.ROOT);
        if (!newWords.equals(blockedWords)) {
            blockedWords = newWords;
            blockedTokens = Arrays.stream(newWords.split("[,，;；\\n]"))
                    .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        }
        rate = timelineRate > 0 && Double.isFinite(timelineRate) ? timelineRate : 1.0;
        offsetMs = timelineOffset;
        if (geometryChanged) rebuildLanes();
        invalidate();
    }

    void setComments(List<DanmakuComment> loaded) {
        comments = new ArrayList<>(loaded);
        Collections.sort(comments);
        resetWidthCache();
        rebuildLanes();
        invalidate();
    }

    void clearComments() {
        comments = Collections.emptyList();
        lanes = new int[0];
        textWidths = new float[0];
        invalidate();
    }

    List<DanmakuComment> findComments(String text, int limit) {
        String needle = text.toLowerCase(Locale.ROOT);
        List<DanmakuComment> found = new ArrayList<>();
        for (DanmakuComment comment : comments) {
            if (comment.text.toLowerCase(Locale.ROOT).contains(needle)) {
                found.add(comment);
                if (found.size() >= limit) break;
            }
        }
        return found;
    }

    double rate() { return rate; }
    long offsetMs() { return offsetMs; }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Font size depends on the available width. Cached measurements from
        // the previous orientation would otherwise make comments jump or clip.
        if (w != oldw) resetWidthCache();
        rebuildLanes();
    }

    private int rowCount() {
        if (portraitFullscreen()) {
            return BilibiliPortraitLayout.rowCount(portraitAreaHeight(), lineHeightPx(), density);
        }
        int limit = 12;
        return Math.max(1, Math.min(limit,
                (int) (getHeight() * area / 100.0 / lineHeightPx())));
    }

    private boolean portraitFullscreen() { return getHeight() > getWidth(); }

    /** Reserve the title/player controls above and bottom navigation below portrait fullscreen video. */
    private float portraitTop() { return BilibiliPortraitLayout.top(getHeight(), density); }

    private float portraitBottom() { return Math.min(getHeight() * .14f, 78 * density); }

    private float portraitAreaHeight() {
        return Math.max(lineHeightPx(),
                (getHeight() - portraitTop() - portraitBottom()) * Math.min(area, 90) / 100f);
    }

    private float portraitLaneSpacing() {
        return portraitAreaHeight() / rowCount();
    }

    private int fixedLaneCount() {
        return getHeight() >= 160 * density ? 2 : 1;
    }

    /** Use the same physical font size for portrait embedded and fullscreen playback. */
    private float fontSizePx() {
        float availableDp = getWidth() / density;
        return fontSp * scaledDensity * Math.max(.68f, Math.min(1f, availableDp / 500f));
    }

    private float lineHeightPx() {
        return fontSizePx() + 5f * density;
    }

    private void resetWidthCache() {
        textWidths = new float[comments.size()];
        Arrays.fill(textWidths, -1f);
    }

    private void rebuildLanes() {
        if (getWidth() <= 0 || getHeight() <= 0) {
            lanes = new int[0];
            return;
        }
        int count = Math.max(1, rowCount());
        long[] scrollNextFree = new long[count];
        long[] topNextFree = new long[fixedLaneCount()];
        long[] bottomNextFree = new long[fixedLaneCount()];
        int[] result = new int[comments.size()];
        Arrays.fill(result, -1);
        for (int i = 0; i < comments.size(); i++) {
            DanmakuComment item = comments.get(i);
            // Unsupported advanced/code modes have no visual representation here;
            // they must not occupy lanes needed by ordinary scrolling comments.
            if (item.mode < 1 || item.mode > 6 || item.text.length() > maxLength) continue;
            long[] laneFree;
            if (item.mode == 5) laneFree = topNextFree;
            else if (item.mode == 4) laneFree = bottomNextFree;
            else laneFree = scrollNextFree;
            int best = -1;
            int available = laneFree.length;
            for (int k = 0; k < available; k++) {
                int lane = (i + k) % available;
                if (laneFree[lane] <= item.timeMs) { best = lane; break; }
            }
            // Drop excessive overlapping comments rather than drawing over existing ones.
            if (best < 0) continue;
            result[i] = best;
            double estimatedWidth = Math.min(item.text.length(), maxLength) * fontSizePx() * .57;
            double travelMs = travelSeconds * 1000.0;
            long gap = item.mode == 4 || item.mode == 5 ? 4100L
                    : (long) (travelMs * estimatedWidth / (getWidth() + estimatedWidth) + 250);
            laneFree[best] = item.timeMs + gap;
        }
        lanes = result;
    }

    /** First index with comment.timeMs >= timeMs. */
    private int lowerBound(long timeMs) {
        int low = 0, high = comments.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (comments.get(mid).timeMs < timeMs) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!active || comments.isEmpty()) return;
        long videoTimeMs = BilibiliDanmakuController.currentTimeMs();
        if (videoTimeMs < 0) return;
        double biliTime = videoTimeMs * rate + offsetMs;
        double scrollDurationMs = travelSeconds * 1000.0;
        double maxWindowBiliMs = Math.max(scrollDurationMs, 4000) * rate;
        int first = lowerBound((long) (biliTime - maxWindowBiliMs));
        if (lanes.length != comments.size()) return;
        float lineHeight = lineHeightPx();
        int count = rowCount();
        fill.setTextSize(fontSizePx());
        stroke.setTextSize(fontSizePx());
        for (int i = first; i < comments.size(); i++) {
            DanmakuComment item = comments.get(i);
            if (lanes[i] < 0) continue;
            if (item.timeMs > biliTime) break;
            if (item.mode == 7 || item.mode == 8 || item.mode <= 0) continue;
            if (hideTopBottom && (item.mode == 4 || item.mode == 5)) continue;
            if (hideColored && item.color != 0xffffff) continue;
            if (item.text.length() > maxLength) continue;
            if (blockedTokens.length > 0) {
                boolean skip = false;
                String lower = item.text.toLowerCase(Locale.ROOT);
                for (String word : blockedTokens) {
                    if (lower.contains(word)) {
                        skip = true; break;
                    }
                }
                if (skip) continue;
            }
            double elapsed = (biliTime - item.timeMs) / rate;
            double duration = (item.mode == 4 || item.mode == 5) ? 4000 : scrollDurationMs;
            if (elapsed < 0 || elapsed > duration) continue;
            int color = Color.rgb((item.color >>> 16) & 0xff, (item.color >>> 8) & 0xff, item.color & 0xff);
            fill.setColor(Color.argb((int) (255 * opacity / 100.0), Color.red(color), Color.green(color), Color.blue(color)));
            stroke.setAlpha((int) (230 * opacity / 100.0));
            float baseline;
            if (portraitFullscreen()) {
                float top = portraitTop();
                float spacing = portraitLaneSpacing();
                if (item.mode == 5) baseline = top + fontSizePx() + spacing * lanes[i];
                else if (item.mode == 4) baseline = getHeight() - portraitBottom()
                        - Math.max(0f, fill.getFontMetrics().descent) - spacing * lanes[i];
                else baseline = top + spacing * ((lanes[i] % count) + 1);
            } else if (item.mode == 5) baseline = 10 * density + fontSizePx() + lineHeight * lanes[i];
            else if (item.mode == 4) baseline = getHeight() - 10 * density
                    - Math.max(0f, fill.getFontMetrics().descent) - lineHeight * lanes[i];
            else baseline = 7 * density + lineHeight * ((lanes[i] % count) + 1);
            float width = textWidths[i];
            if (width < 0) textWidths[i] = width = fill.measureText(item.text);
            float x;
            if (item.mode == 4 || item.mode == 5) x = (getWidth() - width) / 2;
            else if (item.mode == 6) x = -width + (float) (elapsed / duration) * (getWidth() + width);
            else x = getWidth() - (float) (elapsed / duration) * (getWidth() + width);
            canvas.drawText(item.text, x, baseline, stroke);
            canvas.drawText(item.text, x, baseline, fill);
        }
        if (playing) postInvalidateOnAnimation();
    }
}
