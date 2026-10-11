package ui.components;

import ui.Theme;
import ui.anim.Anim;

import javax.swing.JToggleButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Pill toggle used for dates, time slots and filters. Selection and hover colours cross-fade. */
public class ChipToggle extends JToggleButton {
    public enum Style { GOLD, DARK }

    private final Style style;
    private final Anim.Value sel = new Anim.Value(0, this::repaint);
    private final Anim.Value hov = new Anim.Value(0, this::repaint);
    private int minWidth = 64;

    public ChipToggle(String text) {
        this(text, Style.GOLD);
    }

    public ChipToggle(String text, Style style) {
        super(text);
        this.style = style;
        setFont(Theme.FONT_BODY_BOLD);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addItemListener(e -> sel.animateTo(e.getStateChange() == ItemEvent.SELECTED ? 1 : 0, 180));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) hov.animateTo(1, 120);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hov.animateTo(0, 160);
            }
        });
    }

    public ChipToggle minWidth(int w) {
        minWidth = w;
        return this;
    }

    @Override
    public void setSelected(boolean b) {
        super.setSelected(b);
        sel.set(b ? 1 : 0);
    }

    @Override
    public void setPreferredSize(Dimension d) {
        // sizes are text-driven so every chip row looks identical
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(Math.max(minWidth, fm.stringWidth(getText()) + 32), 36);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        double s = sel.get();
        double hv = hov.get();
        Color selFill = style == Style.DARK ? Theme.SIDEBAR_BG : Theme.ACCENT_GOLD;

        Color fill, border, fg;
        if (!isEnabled()) {
            fill = Theme.NEUTRAL_TINT;
            border = Theme.NEUTRAL_TINT;
            fg = Theme.DISABLED_FG;
        } else {
            fill = Theme.mix(Theme.mix(Theme.CARD_BG, Theme.GOLD_TINT, hv), selFill, s);
            border = Theme.mix(Theme.mix(Theme.FIELD_BORDER, Theme.ACCENT_GOLD, hv), selFill, s);
            fg = Theme.mix(Theme.TEXT_PRIMARY, Color.WHITE, s);
        }
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, w, h, h, h);
        g2.setColor(border);
        g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);

        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(fg);
        g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
