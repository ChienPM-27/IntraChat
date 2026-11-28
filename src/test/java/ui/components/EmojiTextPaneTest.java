package ui.components;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class EmojiTextPaneTest {
    @Test void colorIconsPreserveUnicodeAndDeleteWholeGrapheme() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (String glyph : EmojiTextPane.PICKER) assertNotNull(EmojiTextPane.icon(glyph, 25), glyph);
            var pane = new EmojiTextPane(Color.BLACK, true);
            String text = "Chào 😀😀 👩🏽‍💻 🇻🇳";
            pane.setText(text);
            pane.styleEmoji();
            assertEquals(text, pane.getText());
            StyledDocument doc = pane.getStyledDocument();
            int first = text.indexOf("😀");
            int second = first + "😀".length();
            assertNotNull(StyleConstants.getIcon(doc.getCharacterElement(first).getAttributes()));
            assertNotNull(StyleConstants.getIcon(doc.getCharacterElement(second).getAttributes()));
            assertNotEquals(doc.getCharacterElement(first), doc.getCharacterElement(second));
            int flag = text.indexOf("🇻🇳");
            assertNotNull(StyleConstants.getIcon(doc.getCharacterElement(flag).getAttributes()));
            pane.setSize(400, 60);
            BufferedImage image = new BufferedImage(400, 60, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            pane.paint(g); g.dispose();
            int colored = 0;
            for (int y=0; y<image.getHeight(); y++) for (int x=0; x<image.getWidth(); x++) {
                Color c = new Color(image.getRGB(x,y));
                if (c.getRed() > 150 && c.getGreen() > 70 && c.getBlue() < 90) colored++;
            }
            assertTrue(colored > 20, "emoji must be rendered in color, not just a font glyph");
            pane.setCaretPosition(doc.getLength());
            pane.getActionMap().get(DefaultEditorKit.deletePrevCharAction).actionPerformed(
                new ActionEvent(pane, ActionEvent.ACTION_PERFORMED, ""));
            assertEquals("Chào 😀😀 👩🏽‍💻 ", pane.getText());
            pane.setText("👩🏽‍💻");
            pane.setCaretPosition(pane.getDocument().getLength());
            pane.getActionMap().get(DefaultEditorKit.deletePrevCharAction).actionPerformed(
                new ActionEvent(pane, ActionEvent.ACTION_PERFORMED, ""));
            assertEquals("", pane.getText());
        });
    }
}
