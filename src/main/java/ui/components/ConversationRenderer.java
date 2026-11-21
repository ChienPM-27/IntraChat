package ui.components;

import model.Conversation;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Function;

import static ui.components.ChatStyle.*;

public final class ConversationRenderer implements ListCellRenderer<Conversation> {
    private final Function<Conversation, Row> details;

    public ConversationRenderer(Function<Conversation, Row> details) {
        this.details = details;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Conversation> list, Conversation conversation,
                                                   int index, boolean selected, boolean focused) {
        Row row = details.apply(conversation);
        JPanel outer = panel(new BorderLayout());
        outer.setBorder(new EmptyBorder(2, 7, 2, 7));
        JPanel cell = panel(new BorderLayout(10, 0));
        cell.setBackground(selected ? SELECTED : Color.WHITE);
        cell.putClientProperty("FlatLaf.style", "arc: 14");
        cell.setBorder(BorderFactory.createCompoundBorder(
                focused ? BorderFactory.createLineBorder(BLUE, 1, true) : new EmptyBorder(1, 1, 1, 1),
                new EmptyBorder(6, 8, 6, 8)));
        JLabel avatar = new JLabel(new AvatarIcon(row.title(), row.avatarId(), 45, row.online(), conversation.group()));
        cell.add(avatar, BorderLayout.WEST);
        JPanel summaries = new JPanel(new GridLayout(2, 1, 0, 2));
        summaries.setOpaque(false);
        summaries.add(label(row.title(), 14, Font.BOLD, INK));
        JPanel latest = new JPanel(new BorderLayout(5, 0));
        latest.setOpaque(false);
        JLabel preview = label(row.preview(), 12, Font.PLAIN, MUTED);
        JLabel timestamp = label(row.time(), 11, Font.PLAIN, MUTED);
        int available = Math.max(0, list.getFixedCellWidth() - 14 - 18 - 45 - 10 - 5
                - timestamp.getPreferredSize().width);
        preview.setText(ellipsize(row.preview(), preview.getFontMetrics(preview.getFont()), available));
        latest.add(preview);
        latest.add(timestamp, BorderLayout.EAST);
        summaries.add(latest);
        cell.add(summaries);
        outer.add(cell);
        outer.getAccessibleContext().setAccessibleName(row.title() + " · " + row.preview() + " " + row.time());
        return outer;
    }

    private static String ellipsize(String text, FontMetrics font, int width) {
        StringBuilder displayed = new StringBuilder();
        int measured = 0;
        for (int index = 0; index < text.length();) {
            int point = text.codePointAt(index);
            measured += font.charWidth(point);
            if (measured + font.charWidth('…') > width) {
                return displayed + "…";
            }
            displayed.appendCodePoint(point);
            index += Character.charCount(point);
        }
        return displayed.toString();
    }

    public record Row(String title, String avatarId, boolean online, String preview, String time) {
    }
}
