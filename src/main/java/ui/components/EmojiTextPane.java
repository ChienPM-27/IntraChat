package ui.components;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.event.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Color emoji images are presentation only; the document always retains the original Unicode. */
final class EmojiTextPane extends JTextPane {
    static final String[] PICKER = ("😀 😃 😄 😁 😆 😅 😂 🤣 😊 🙂 🙃 😉 😍 🥰 😘 😋 "
        + "😎 🤩 🥳 🤔 🤫 😴 😮 😱 😢 😭 😡 😤 🙌 👏 👍 👎 🙏 🤝 "
        + "💪 👋 🤟 👌 ❤️ 💙 💚 💛 💜 💔 ✨ 🎉 🔥 💯 🎂 🎁 "
        + "🌈 🌸 🌻 🐱 🐶 ☕ 🍕 🍀 🚀 ⭐ 🇻🇳 👩🏽‍💻").split(" ");
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private static final Object EMOJI_SPAN = new Object();
    private static final Map<String, byte[]> IMAGES = loadImages();
    private static final Map<String, Icon> ICONS = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Icon> e) { return size() > 256; }
    };
    private final SimpleAttributeSet regular = new SimpleAttributeSet();
    private boolean queued;

    EmojiTextPane(Color ink, boolean editable) {
        setEditorKit(new StyledEditorKit() {
            @Override public ViewFactory getViewFactory() {
                ViewFactory base = super.getViewFactory();
                return element -> {
                    if (StyleConstants.getIcon(element.getAttributes()) != null) return new IconView(element);
                    View view = base.create(element);
                    return view instanceof LabelView ? new LabelView(element) {
                        @Override public float getMinimumSpan(int axis) {
                            return axis == View.X_AXIS ? 0 : super.getMinimumSpan(axis);
                        }
                    } : view;
                };
            }
        });
        setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
        setForeground(ink);
        setEditable(editable);
        StyleConstants.setFontFamily(regular, Font.DIALOG);
        StyleConstants.setFontSize(regular, 14);
        StyleConstants.setForeground(regular, ink);
        getStyledDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { schedule(); }
            public void removeUpdate(DocumentEvent e) { schedule(); }
            public void changedUpdate(DocumentEvent e) { }
        });
        if (editable) {
            setNavigationFilter(new NavigationFilter() {
                @Override public void setDot(FilterBypass fb, int dot, Position.Bias bias) {
                    fb.setDot(snap(dot, dot >= getCaretPosition()), bias);
                }
                @Override public void moveDot(FilterBypass fb, int dot, Position.Bias bias) {
                    fb.moveDot(snap(dot, dot >= getCaretPosition()), bias);
                }
            });
            installDelete(DefaultEditorKit.deletePrevCharAction, false);
            installDelete(DefaultEditorKit.deleteNextCharAction, true);
        }
    }

    private static Map<String, byte[]> loadImages() {
        Map<String, byte[]> result = new HashMap<>();
        try (InputStream raw = EmojiTextPane.class.getResourceAsStream("/emoji/twemoji-15.1.0.zip")) {
            if (raw == null) return result;
            try (ZipInputStream zip = new ZipInputStream(raw)) {
                ZipEntry e;
                while ((e = zip.getNextEntry()) != null) {
                    if (e.getName().endsWith(".png")) result.put(e.getName(), zip.readAllBytes());
                }
            }
        } catch (IOException ignored) { }
        return result;
    }

    private static String key(String glyph, boolean removeVariation) {
        StringJoiner key = new StringJoiner("-");
        glyph.codePoints().filter(c -> !removeVariation || c != 0xfe0f)
            .forEach(c -> key.add(Integer.toHexString(c)));
        return key + ".png";
    }

    static synchronized Icon icon(String glyph, int size) {
        String name = key(glyph, false);
        if (!IMAGES.containsKey(name)) name = key(glyph, true);
        byte[] bytes = IMAGES.get(name);
        if (bytes == null || glyph.indexOf('\ufe0e') >= 0) return null;
        String cacheKey = name + ":" + size;
        Icon existing = ICONS.get(cacheKey);
        if (existing != null) return existing;
        try {
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) return null;
            var scaled = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(image, 0, 0, size, size, null);
            g.dispose();
            Icon icon = new ImageIcon(scaled);
            ICONS.put(cacheKey, icon);
            return icon;
        } catch (IOException ex) { return null; }
    }

    private void schedule() {
        if (!queued) {
            queued = true;
            SwingUtilities.invokeLater(() -> { queued = false; styleEmoji(); });
        }
    }

    void styleEmoji() {
        StyledDocument doc = getStyledDocument();
        String value = getText();
        if (value.isEmpty()) { setCharacterAttributes(regular, true); return; }
        // Over-limit drafts remain editable, without building thousands of image views.
        if (value.codePointCount(0, value.length()) > vn.intrachat.domain.ChatText.MAX_CODE_POINTS) return;
        doc.setCharacterAttributes(0, doc.getLength(), regular, true);
        Matcher matcher = GRAPHEME.matcher(value);
        while (matcher.find()) {
            String glyph = matcher.group();
            if (glyph.codePoints().allMatch(c -> c < 128)) continue;
            Icon image = icon(glyph, 18);
            if (image == null) continue;
            SimpleAttributeSet attr = new SimpleAttributeSet(regular);
            StyleConstants.setIcon(attr, image);
            // Distinct adjacent identical emojis must remain separate views.
            attr.addAttribute(EMOJI_SPAN, matcher.start());
            doc.setCharacterAttributes(matcher.start(), matcher.end() - matcher.start(), attr, true);
        }
        getInputAttributes().removeAttributes(getInputAttributes());
        getInputAttributes().addAttributes(regular);
    }

    private int snap(int position, boolean forward) {
        String value = getText();
        int pos = Math.max(0, Math.min(position, value.length()));
        Matcher matcher = GRAPHEME.matcher(value);
        while (matcher.find()) {
            if (pos > matcher.start() && pos < matcher.end()) return forward ? matcher.end() : matcher.start();
            if (matcher.start() >= pos) break;
        }
        return pos;
    }

    private void installDelete(String name, boolean forward) {
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                if (!isEditable() || !isEnabled()) return;
                if (getSelectionStart() != getSelectionEnd()) { replaceSelection(""); return; }
                int dot = getCaretPosition();
                Matcher matcher = GRAPHEME.matcher(getText());
                while (matcher.find()) {
                    if (forward ? matcher.end() > dot : matcher.end() >= dot && matcher.start() < dot) {
                        try { getDocument().remove(matcher.start(), matcher.end() - matcher.start()); }
                        catch (BadLocationException ignored) { }
                        return;
                    }
                }
            }
        });
    }
}
