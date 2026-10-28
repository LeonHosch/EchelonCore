package ekuzo.echeloncore.client;

import ekuzo.crazyworldprogression.client.reforge.ReforgeScreenStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Echelon's look for CWP's reforge window: soul-fire teal on near-black, with a soul altar around the item slot
 * (a dark socket, a pulsing soul-fire ring, glowing corner stones and rising wisps).
 */
final class SoulforgingStyle implements ReforgeScreenStyle {
    private static final int SOUL = 0x3FD8E0, SOUL_BRIGHT = 0xFF7FF7FF, SOUL_DIM = 0xFF1E5C66;

    @Override
    public void window(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xF2080F12);
        graphics.outline(x, y, width, height, SOUL_DIM);
        graphics.outline(x + 2, y + 2, width - 4, height - 4, 0x553FD8E0);
        // Corner stones.
        int[][] corners = {{x, y}, {x + width - 4, y}, {x, y + height - 4}, {x + width - 4, y + height - 4}};
        for (int[] corner : corners) {
            graphics.fill(corner[0], corner[1], corner[0] + 4, corner[1] + 4, 0xFF0F2A30);
            graphics.fill(corner[0] + 1, corner[1] + 1, corner[0] + 3, corner[1] + 3, 0xFF6FF3F8);
        }
    }

    @Override
    public void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF0B1A1E);
        graphics.outline(x, y, width, height, SOUL_DIM);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0x403FD8E0);
    }

    @Override
    public void slot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF050B0D);
        graphics.fill(x, y, x + 17, y + 17, 0xFF153840);
        graphics.fill(x, y, x + 16, y + 16, 0xFF0C1C21);
    }

    @Override
    public void itemSocket(GuiGraphicsExtractor graphics, int centreX, int centreY, long time) {
        double pulse = 0.5 + 0.5 * Math.sin(time / 450.0);
        int glow = (int) (0x40 + 0x70 * pulse);
        int left = centreX - 17, top = centreY - 17;
        graphics.fill(left - 3, top - 3, left + 37, top + 37, (glow / 3) << 24 | SOUL);
        graphics.fill(left, top, left + 34, top + 34, 0xFF0A1316);
        graphics.outline(left, top, 34, 34, SOUL_DIM);
        graphics.outline(left + 7, top + 7, 20, 20, glow << 24 | SOUL);
        graphics.fill(left + 8, top + 8, left + 26, top + 26, 0xFF06272B);
        int[][] corners = {{left - 1, top - 1}, {left + 32, top - 1}, {left - 1, top + 32}, {left + 32, top + 32}};
        for (int[] corner : corners) graphics.fill(corner[0], corner[1], corner[0] + 3, corner[1] + 3, 0xFF6FF3F8);
        for (int wisp = 0; wisp < 6; wisp++) {
            long phase = (time / 45 + wisp * 23L) % 40;
            int wx = left + 4 + (int) ((wisp * 11 + (time / 700 + wisp) % 4 * 5) % 26);
            int wy = top + 32 - (int) (phase * 0.9);
            int alpha = (int) (0xC0 * (1.0 - phase / 40.0));
            graphics.fill(wx, wy, wx + 1, wy + 2, alpha << 24 | (SOUL_BRIGHT & 0xFFFFFF));
        }
    }

    @Override public int titleColor() { return 0xFFDDFBFC; }

    @Override public int textColor() { return 0xFFE4F4F5; }

    @Override public int mutedColor() { return 0xFF7FA6AA; }

    @Override public int accentColor() { return 0xFF7FF7FF; }

    @Override public int categoryColor() { return 0xFF4FE0E6; }

    @Override public int goodColor() { return 0xFF6FF3B0; }

    @Override public int badColor() { return 0xFFFF7A6B; }

    @Override public String categoryMarker() { return "✦ "; }
}
