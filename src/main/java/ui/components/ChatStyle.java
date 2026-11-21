package ui.components;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletionException;

public final class ChatStyle {
    public static final Color BLUE = new Color(0x0084FF);
    public static final Color INK = new Color(0x050505);
    public static final Color MUTED = new Color(0x65676B);
    public static final Color LINE = new Color(0xE4E6EB);
    public static final Color FIELD = new Color(0xF0F2F5);
    public static final Color SELECTED = new Color(0xEAF4FF);
    public static final Color GREEN = new Color(0x31A24C);
    public static final Color RED = new Color(0xB32237);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private ChatStyle() {
    }

    public static void install() {
        FlatLightLaf.setup();
        UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("Component.focusColor", BLUE);
    }

    public static String time(Instant instant) {
        return TIME.format(instant.atZone(ZoneId.systemDefault()));
    }

    public static JLabel label(String text, int size, int weight, Color color) {
        JLabel label = new JLabel();
        label.putClientProperty("html.disable", true);
        label.setText(text);
        label.setFont(label.getFont().deriveFont(weight, (float) size));
        label.setForeground(color);
        return label;
    }

    public static JPanel panel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(Color.WHITE);
        return panel;
    }

    public static void field(JTextField field, String placeholder) {
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.putClientProperty(FlatClientProperties.STYLE,
                "arc: 999; borderWidth: 0; margin: 3,12,3,12");
        field.setBackground(FIELD);
        field.getAccessibleContext().setAccessibleName(placeholder);
    }

    public static JButton primary(String text) {
        JButton button = new JButton(text);
        button.putClientProperty(FlatClientProperties.STYLE, "arc: 12");
        button.setBackground(BLUE);
        button.setForeground(Color.WHITE);
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        return button;
    }

    public static JButton iconButton(ChatIcon.Kind kind, String name, Color color) {
        JButton button = new JButton(new ChatIcon(kind, color, 19));
        button.putClientProperty(FlatClientProperties.STYLE, "arc: 999; borderWidth: 0; margin: 0,0,0,0");
        button.setBackground(Color.WHITE);
        button.setPreferredSize(new Dimension(35, 35));
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
        return button;
    }

    public static Graphics2D drawing(Graphics graphics) {
        Graphics2D copy = (Graphics2D) graphics.create();
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        copy.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return copy;
    }

    public static String error(Throwable error, String fallback) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof IllegalArgumentException && cause.getMessage() != null
                ? cause.getMessage() : fallback;
    }
}
