package app.morphe.extension.youtube.bilibilidanmaku;

/** Geometry for the tall portrait player; all inputs and results are physical pixels. */
final class BilibiliPortraitLayout {
    private BilibiliPortraitLayout() {}

    static float top(float height, float density) {
        // Protect the title and controls while leaving most of a tall video available.
        return Math.max(130f * density, height * .18f);
    }

    static int rowCount(float areaHeight, float lineHeight, float density) {
        return Math.max(1, Math.min(14, (int) (areaHeight / (lineHeight + 14f * density))));
    }
}
