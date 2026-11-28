package ui.components;

import javax.swing.Icon;
import java.awt.*;
import java.awt.geom.*;

public record ChatIcon(Kind kind, Color color, int size) implements Icon {
    public enum Kind { LOGO, PLUS, SEARCH, SETTINGS, IMAGE, VIDEO, FILE, AUDIO, MIC, SMILE, PLAY, PAUSE, DOWNLOAD, CHECK, DOUBLE_CHECK }

    /** Shared application icon, independent from the deleted demo login window. */
    public static Image appIconImage() {
        var image = new java.awt.image.BufferedImage(48, 48, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        new ChatIcon(Kind.LOGO, ChatStyle.BLUE, 48).paintIcon(new javax.swing.JLabel(), graphics, 0, 0);
        graphics.dispose();
        return image;
    }

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
        draw.scale(size / 24.0, size / 24.0);
        draw.setColor(!component.isEnabled() ? ChatStyle.MUTED : color);
        draw.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (kind) {
            case LOGO -> {
                draw.fill(new RoundRectangle2D.Double(0, 0, 24, 24, 8, 8));
                draw.setColor(Color.WHITE);
                draw.fill(new Ellipse2D.Double(4, 4.6, 16, 13.8));
                draw.fill(path(6, 15, 6, 20, 11, 17, 6, 15));
                draw.setColor(color);
                draw.fill(path(6.6, 13.3, 11.4, 8.5, 14, 10.9, 17.5, 9.3, 13, 14.2, 10.3, 11.8, 6.6, 13.3));
            }
            case PLUS -> draw.draw(path(12, 5, 12, 19, 12, 12, 5, 12, 19, 12));
            case SEARCH -> {
                draw.draw(new Ellipse2D.Double(4, 4, 11, 11));
                draw.drawLine(13, 13, 20, 20);
            }
            case SETTINGS -> {
                Path2D gear = new Path2D.Double();
                // Eight teeth, with a circular bearing in the center.
                double[] radii = {8, 10, 10, 8};
                for (int i = 0; i < 32; i++) {
                    double angle = -Math.PI / 2 + (i - 1.5) * Math.PI / 16;
                    double radius = radii[i % radii.length];
                    double x = 12 + radius * Math.cos(angle);
                    double y = 12 + radius * Math.sin(angle);
                    if (i == 0) gear.moveTo(x, y);
                    else gear.lineTo(x, y);
                }
                gear.closePath();
                draw.draw(gear);
                draw.draw(new Ellipse2D.Double(8.5, 8.5, 7, 7));
            }
            case IMAGE -> {
                draw.draw(new RoundRectangle2D.Double(3, 4, 18, 16, 4, 4));
                draw.draw(new Ellipse2D.Double(7, 7, 3, 3));
                draw.draw(path(4, 17, 9, 13, 12, 16, 16, 11, 20, 16));
            }
            case VIDEO -> {
                draw.draw(new RoundRectangle2D.Double(3, 5, 18, 14, 4, 4));
                draw.fill(path(10, 8.8, 15, 12, 10, 15.2, 10, 8.8));
            }
            case FILE -> {
                draw.draw(path(6, 3, 13, 3, 18, 8, 18, 21, 6, 21, 6, 3));
                draw.draw(path(13, 3, 13, 8, 18, 8));
                draw.drawLine(9, 13, 15, 13);
                draw.drawLine(9, 17, 15, 17);
            }
            case AUDIO -> {
                draw.drawLine(10, 17, 10, 5);
                draw.drawLine(10, 5, 19, 3);
                draw.drawLine(19, 3, 19, 15);
                draw.fill(new Ellipse2D.Double(4, 15, 6, 5));
                draw.fill(new Ellipse2D.Double(13, 13, 6, 5));
            }
            case MIC -> {
                draw.draw(new RoundRectangle2D.Double(9, 3, 6, 11, 6, 6));
                draw.draw(new Arc2D.Double(6, 6, 12, 12, 180, 180, Arc2D.OPEN));
                draw.drawLine(12, 18, 12, 21);
            }
            case SMILE -> {
                draw.draw(new Ellipse2D.Double(3, 3, 18, 18));
                draw.fill(new Ellipse2D.Double(8, 8, 2, 2));
                draw.fill(new Ellipse2D.Double(14, 8, 2, 2));
                draw.draw(new Arc2D.Double(8, 10, 8, 6, 190, 160, Arc2D.OPEN));
            }
            case PLAY -> draw.fill(path(8, 4, 20, 12, 8, 20, 8, 4));
            case PAUSE -> {
                draw.fillRoundRect(7, 5, 4, 14, 2, 2);
                draw.fillRoundRect(14, 5, 4, 14, 2, 2);
            }
            case DOWNLOAD -> {
                draw.draw(path(12, 3, 12, 15, 7, 10, 12, 15, 17, 10));
                draw.draw(path(4, 16, 4, 20, 20, 20, 20, 16));
            }
            case CHECK -> draw.draw(path(5, 12, 10, 17, 19, 7));
            case DOUBLE_CHECK -> {
                draw.draw(path(2, 12, 7, 17, 16, 7));
                draw.draw(path(11, 15, 13, 17, 22, 7));
            }
        }
        draw.dispose();
    }

    private static Path2D path(double... points) {
        Path2D path = new Path2D.Double();
        path.moveTo(points[0], points[1]);
        for (int index = 2; index < points.length; index += 2) {
            path.lineTo(points[index], points[index + 1]);
        }
        return path;
    }
}
