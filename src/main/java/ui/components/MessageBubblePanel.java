package ui.components;

import model.ChatMessage;
import model.DeliveryState;
import model.MessageType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.LabelView;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import java.awt.*;

import static ui.components.ChatStyle.*;

public final class MessageBubblePanel extends JPanel {
    private final JLabel receipt = label("", 11, Font.PLAIN, MUTED);

    public MessageBubblePanel(ChatMessage message, boolean mine, boolean group, String sender, MessageMedia media) {
        super(new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(4, 18, 4, 18));
        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        float alignment = mine ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT;
        String time = time(message.sentAt());
        if (group && !mine) {
            JLabel byline = label(sender, 11, Font.PLAIN, MUTED);
            byline.setBorder(new EmptyBorder(0, 5, 3, 5));
            byline.setAlignmentX(alignment);
            stack.add(byline);
        }
        Color bubbleColor = mine ? BLUE : LINE;
        Color ink = mine ? Color.WHITE : INK;
        JPanel bubble = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D draw = drawing(graphics);
                draw.setColor(bubbleColor);
                draw.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                draw.dispose();
                super.paintComponent(graphics);
            }
        };
        bubble.setOpaque(false);
        boolean visualMedia = message.content().type() == MessageType.IMAGE || message.content().type() == MessageType.VIDEO;
        bubble.setBorder(visualMedia ? new EmptyBorder(5, 5, 5, 5) : new EmptyBorder(7, 11, 7, 11));
        bubble.setAlignmentX(alignment);
        bubble.add(message.content().type() == MessageType.TEXT ? text(message.content().text(), ink)
                : media.render(message.content(), mine));
        bubble.setToolTipText(sender + " · " + time);
        bubble.getAccessibleContext().setAccessibleName(sender + " · " + time);
        stack.add(bubble);

        JLabel timestamp = label(time, 11, Font.PLAIN, MUTED);
        timestamp.setBorder(new EmptyBorder(2, 5, 0, 5));
        timestamp.setAlignmentX(alignment);
        stack.add(timestamp);
        receipt.setBorder(new EmptyBorder(2, 5, 0, 5));
        receipt.setAlignmentX(alignment);
        receipt.setVisible(false);
        stack.add(receipt);
        add(stack);
    }

    public void showReceipt(DeliveryState state) {
        receipt.setVisible(state != null);
        if (state != null) {
            receipt.setText(state.label());
            receipt.setIcon(state == DeliveryState.SEEN ? null : new ChatIcon(
                    state == DeliveryState.SENT ? ChatIcon.Kind.CHECK : ChatIcon.Kind.DOUBLE_CHECK, MUTED, 13));
            receipt.setHorizontalTextPosition(SwingConstants.LEADING);
        }
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    private static JTextPane text(String value, Color ink) {
        JTextPane pane = new JTextPane();
        pane.setEditorKit(new StyledEditorKit() {
            @Override
            public ViewFactory getViewFactory() {
                ViewFactory standard = super.getViewFactory();
                return element -> {
                    View view = standard.create(element);
                    return view instanceof LabelView ? new LabelView(element) {
                        @Override
                        public float getMinimumSpan(int axis) {
                            return axis == View.X_AXIS ? 0 : super.getMinimumSpan(axis);
                        }
                    } : view;
                };
            }
        });
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(null);
        pane.setMargin(new Insets(0, 0, 0, 0));
        pane.setForeground(ink);
        pane.setText(value);
        Font regular = new Font("Segoe UI", Font.PLAIN, 14);
        Font emoji = new Font("Segoe UI Emoji", Font.PLAIN, 14);
        SimpleAttributeSet regularStyle = new SimpleAttributeSet();
        StyleConstants.setFontFamily(regularStyle, regular.getFamily());
        StyleConstants.setFontSize(regularStyle, 14);
        StyleConstants.setForeground(regularStyle, ink);
        SimpleAttributeSet emojiStyle = new SimpleAttributeSet(regularStyle);
        StyleConstants.setFontFamily(emojiStyle, emoji.getFamily());
        SimpleAttributeSet fallbackStyle = new SimpleAttributeSet(regularStyle);
        StyleConstants.setFontFamily(fallbackStyle, Font.DIALOG);
        pane.getStyledDocument().setCharacterAttributes(0, value.length(), regularStyle, true);
        for (int start = 0; start < value.length();) {
            int codePoint = value.codePointAt(start);
            int end = start + Character.charCount(codePoint);
            var style = codePoint >= 0x1F000 || codePoint == 0x2764 || codePoint == 0x2728
                    || codePoint == 0xFE0F || codePoint == 0x200D
                    ? (emoji.canDisplay(codePoint) ? emojiStyle : fallbackStyle)
                    : regular.canDisplay(codePoint) ? regularStyle : fallbackStyle;
            while (end < value.length()) {
                int next = value.codePointAt(end);
                if (next != 0xFE0F && next != 0x200D && (next < 0x1F3FB || next > 0x1F3FF)
                        && value.codePointBefore(end) != 0x200D) {
                    break;
                }
                end += Character.charCount(next);
            }
            if (style != regularStyle) {
                pane.getStyledDocument().setCharacterAttributes(start, end - start, style, true);
            }
            start = end;
        }
        int width = Math.min(340, Math.max(16, pane.getPreferredSize().width + 2));
        var view = pane.getUI().getRootView(pane);
        view.setSize(width, Integer.MAX_VALUE);
        pane.setPreferredSize(new Dimension(width, (int) Math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS)) + 1));
        return pane;
    }
}
