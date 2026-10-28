package ekuzo.echeloncore.client;

import ekuzo.crazyworldprogression.client.style.PanelStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Echelon's look for CWP's plain windows (party, friends and clan, chest filter, quicksort and the chest buttons):
 * a parchment page in a dark oak frame with gold trim and corner studs, and wooden plank buttons with brass edges.
 */
final class MedievalPanelStyle implements PanelStyle {
    private static final int OAK_DARK = 0xFF2A1A0E, OAK = 0xFF4A2F18, OAK_LIGHT = 0xFF6B4423;
    private static final int GOLD = 0xFFC9A227, GOLD_DARK = 0xFF7A5E14;
    private static final int PARCHMENT = 0xFFE6D3A3, PARCHMENT_DARK = 0xFFD2BB86;

    @Override
    public void backdrop(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0xB0100804);
    }

    @Override
    public void window(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        // Oak frame, five pixels, with plank seams.
        graphics.fill(x - 6, y - 6, x + width + 6, y + height + 6, OAK_DARK);
        graphics.fill(x - 5, y - 5, x + width + 5, y + height + 5, OAK);
        for (int seam = x + 24; seam < x + width; seam += 48) {
            graphics.fill(seam, y - 5, seam + 1, y - 1, OAK_DARK);
            graphics.fill(seam, y + height + 1, seam + 1, y + height + 5, OAK_DARK);
        }
        graphics.fill(x - 5, y - 5, x + width + 5, y - 4, OAK_LIGHT);
        // Gold trim and the parchment page, darker towards the edges.
        graphics.outline(x - 2, y - 2, width + 4, height + 4, GOLD);
        graphics.outline(x - 1, y - 1, width + 2, height + 2, GOLD_DARK);
        graphics.fill(x, y, x + width, y + height, PARCHMENT_DARK);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, PARCHMENT);
        // Faint ruled lines on the page.
        for (int line = y + 22; line < y + height - 6; line += 22)
            graphics.fill(x + 6, line, x + width - 6, line + 1, 0x18603C14);
        // Studs in the corners.
        int[][] studs = {{x - 6, y - 6}, {x + width + 1, y - 6}, {x - 6, y + height + 1}, {x + width + 1, y + height + 1}};
        for (int[] stud : studs) {
            graphics.fill(stud[0], stud[1], stud[0] + 5, stud[1] + 5, GOLD_DARK);
            graphics.fill(stud[0] + 1, stud[1] + 1, stud[0] + 4, stud[1] + 4, GOLD);
            graphics.fill(stud[0] + 1, stud[1] + 1, stud[0] + 2, stud[1] + 2, 0xFFF4E08A);
        }
    }

    @Override
    public void divider(GuiGraphicsExtractor graphics, int x, int top, int bottom) {
        graphics.fill(x - 1, top, x + 2, bottom, 0x30603C14);
        graphics.fill(x, top, x + 1, bottom, GOLD_DARK);
        graphics.fill(x - 1, top, x + 2, top + 3, GOLD);
        graphics.fill(x - 1, bottom - 3, x + 2, bottom, GOLD);
    }

    @Override
    public void bar(GuiGraphicsExtractor graphics, int x, int y, int width, double share, int color) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + 4, OAK);
        graphics.fill(x, y, x + width, y + 3, 0xFF3A2614);
        graphics.fill(x, y, x + (int) (width * Math.max(0, Math.min(1, share))), y + 3, color);
    }

    @Override
    public boolean button(GuiGraphicsExtractor graphics, AbstractWidget button, boolean hovered) {
        int x = button.getX(), y = button.getY(), width = button.getWidth(), height = button.getHeight();
        boolean active = button.isActive();
        graphics.fill(x, y, x + width, y + height, active && hovered ? GOLD : OAK_DARK);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, active ? (hovered ? 0xFF7A4E28 : OAK_LIGHT) : 0xFF4A3A2C);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, active ? 0xFF8F6136 : 0xFF54443A);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, OAK_DARK);
        // Brass nails at the ends.
        boolean nails = width > 48;
        if (nails) {
            graphics.fill(x + 3, y + height / 2 - 1, x + 5, y + height / 2 + 1, active ? GOLD : GOLD_DARK);
            graphics.fill(x + width - 5, y + height / 2 - 1, x + width - 3, y + height / 2 + 1, active ? GOLD : GOLD_DARK);
        }
        var font = Minecraft.getInstance().font;
        String text = button.getMessage().getString();
        int room = width - (nails ? 14 : 4);
        if (font.width(text) > room) text = font.plainSubstrByWidth(text, Math.max(1, room - font.width("…"))) + "…";
        graphics.centeredText(font, net.minecraft.network.chat.Component.literal(text), x + width / 2, y + (height - 8) / 2,
                active ? 0xFFF3E6C4 : 0xFFA89880);
        return true;
    }

    @Override public int title() { return 0xFF3B1F0B; }

    @Override public int text() { return 0xFF3B2412; }

    @Override public int muted() { return 0xFF7A5C3A; }

    @Override public int heading() { return 0xFF6E1414; }

    @Override public int accent() { return 0xFF8A5A00; }

    @Override public int good() { return 0xFF2E6B2E; }

    @Override public int bad() { return 0xFFA02020; }

    @Override public int track() { return 0xFF3A2614; }
}
