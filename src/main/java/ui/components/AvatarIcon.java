package ui.components;

import javax.swing.Icon;
import java.awt.*;
import java.util.Locale;

public record AvatarIcon(String title, String id, int size, boolean online, boolean group) implements Icon {
    private static final int[] SURFACES = {0xDCEBFF, 0xEDE4FC, 0xDDF3E9, 0xFCE8DE};
    private static final int[] INKS = {0x27559B, 0x6946A5, 0x21684D, 0x98512E};

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int left, int top) {
        Graphics2D draw = ChatStyle.drawing(graphics);
        draw.translate(left, top);
        int palette = group ? 1 : Math.floorMod(id.hashCode(), SURFACES.length);
        draw.setColor(new Color(SURFACES[palette]));
        draw.fillOval(0, 0, size, size);
        draw.setColor(new Color(INKS[palette]));
        if (group) {
            int unit = Math.max(2, size / 8);
            draw.fillOval(size / 2 - unit - 1, size / 4, unit * 2, unit * 2);
            draw.fillOval(size / 2 + unit, size / 3, unit + 1, unit + 1);
            draw.fillRoundRect(size / 4, size / 2 + 2, size / 2, unit + 2, unit, unit);
        } else {
            String initial = title.substring(0, title.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
            draw.setFont(component.getFont().deriveFont(Font.BOLD, size * .41f));
            FontMetrics metrics = draw.getFontMetrics();
            draw.drawString(initial, (size - metrics.stringWidth(initial)) / 2,
                    (size - metrics.getHeight()) / 2 + metrics.getAscent());
        }
        if (online) {
            int dot = Math.max(10, size / 4);
            draw.setColor(Color.WHITE);
            draw.fillOval(size - dot, size - dot, dot, dot);
            draw.setColor(ChatStyle.GREEN);
            draw.fillOval(size - dot + 2, size - dot + 2, dot - 4, dot - 4);
        }
        draw.dispose();
    }
}
