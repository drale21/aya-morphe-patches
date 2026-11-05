package app.morphe.extension.youtube.bilibilidanmaku;

import static org.junit.Assert.*;
import org.junit.Test;

public class BilibiliPortraitLayoutTest {
    @Test public void portraitReservesControlsAndSpreadsTracksAcrossAvailableArea() {
        float height = 2400f, density = 3f, lineHeight = 72f;
        float top = BilibiliPortraitLayout.top(height, density);
        float bottom = Math.min(height * .14f, 78f * density);
        float area = (height - top - bottom) * .75f;
        int rows = BilibiliPortraitLayout.rowCount(area, lineHeight, density);
        float spacing = area / rows;
        assertTrue(top >= 130f * density);
        assertTrue(rows > 7 && rows <= 14);
        assertTrue(spacing >= lineHeight);
        assertTrue("Tracks should span a substantial part of a tall display", area > height * .5f);
        assertTrue(top + area <= height - bottom);
    }

    @Test public void smallAreasStillHaveOneTrack() {
        assertEquals(1, BilibiliPortraitLayout.rowCount(30f, 50f, 2f));
    }
}
