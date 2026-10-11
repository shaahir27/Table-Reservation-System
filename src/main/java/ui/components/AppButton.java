package ui.components;

import ui.Theme;
import ui.anim.Anim;

import javax.swing.JButton;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;

/**
 * The single button used across the whole application. Animated hover + press feedback,
 * keyboard focus ring, loading spinner, and a consistent size system (it ignores
 * setPreferredSize on purpose so every screen gets identical, text-fitted buttons).
 */
public class AppButton extends JButton {
    public enum Variant { PRIMARY, SECONDARY, DANGER, SUCCESS, DARK, GHOST }

    private final Variant variant;
    private boolean compact;
    private int minWidth = 88;
    private int fixedWidth = -1;
    private boolean loading;
    private boolean mouseFocus;
    private double spinnerAngle;
    private Timer spinnerTimer;

    /** True once the user navigates with the keyboard; focus rings only show then (not after mouse clicks / on open). */
    private static boolean keyboardNav;

    static {
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (ev instanceof java.awt.event.KeyEvent ke && ke.getID() == java.awt.event.KeyEvent.KEY_PRESSED) {
                int c = ke.getKeyCode();
                if (c == java.awt.event.KeyEvent.VK_TAB || c == java.awt.event.KeyEvent.VK_LEFT || c == java.awt.event.KeyEvent.VK_RIGHT
                        || c == java.awt.event.KeyEvent.VK_UP || c == java.awt.event.KeyEvent.VK_DOWN) keyboardNav = true;
            } else if (ev instanceof java.awt.event.MouseEvent me && me.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED) {
                keyboardNav = false;
            }
        }, java.awt.AWTEvent.KEY_EVENT_MASK | java.awt.AWTEvent.MOUSE_EVENT_MASK);
    }

    private final Anim.Value hover = new Anim.Value(0, this::repaint);
    private final Anim.Value press = new Anim.Value(0, this::repaint);
    private final Anim.Value focus = new Anim.Value(0, this::repaint);

    public AppButton(String text) {
        this(text, Variant.SECONDARY);
    }

    public AppButton(String text, Variant variant) {
        super(text);
        this.variant = variant;
        setFont(variant == Variant.SECONDARY || variant == Variant.GHOST ? Theme.FONT_BODY_BOLD : Theme.FONT_BODY_BOLD);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setForeground(Theme.TEXT_PRIMARY);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) hover.animateTo(1, 140);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover.animateTo(0, 180);
                press.animateTo(0, 120);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                mouseFocus = true;
                if (isEnabled()) press.animateTo(1, 70);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                press.animateTo(0, 140);
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                focus.animateTo(!mouseFocus && keyboardNav ? 1 : 0, 140);
                mouseFocus = false;
            }

            @Override
            public void focusLost(FocusEvent e) {
                focus.animateTo(0, 140);
            }
        });
    }

    public static AppButton primary(String text) {
        return new AppButton(text, Variant.PRIMARY);
    }

    public static AppButton secondary(String text) {
        return new AppButton(text, Variant.SECONDARY);
    }

    public static AppButton danger(String text) {
        return new AppButton(text, Variant.DANGER);
    }

    public static AppButton success(String text) {
        return new AppButton(text, Variant.SUCCESS);
    }

    public static AppButton ghost(String text) {
        return new AppButton(text, Variant.GHOST);
    }

    public AppButton compact() {
        this.compact = true;
        setFont(Theme.FONT_SMALL_BOLD);
        minWidth = 56;
        revalidate();
        return this;
    }

    /** Forces an exact width (used for full-width form buttons). */
    public AppButton fixedWidth(int w) {
        this.fixedWidth = w;
        revalidate();
        return this;
    }

    public AppButton minWidth(int w) {
        this.minWidth = w;
        revalidate();
        return this;
    }

    /** Shows an inline spinner and blocks clicks while keeping the normal (non-disabled) colours. */
    public void setLoading(boolean on) {
        if (loading == on) return;
        loading = on;
        super.setEnabled(!on);
        if (on) {
            if (spinnerTimer == null) {
                spinnerTimer = new Timer(16, e -> {
                    spinnerAngle = (spinnerAngle + 9) % 360;
                    repaint();
                });
            }
            spinnerTimer.start();
        } else if (spinnerTimer != null) {
            spinnerTimer.stop();
        }
        revalidate();
        repaint();
    }

    public boolean isLoading() {
        return loading;
    }

    @Override
    public void setEnabled(boolean b) {
        if (loading && !b) return;
        super.setEnabled(b);
        if (!b) {
            hover.set(0);
            press.set(0);
        }
        repaint();
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        revalidate();
    }

    @Override
    public void removeNotify() {
        if (spinnerTimer != null) spinnerTimer.stop();
        super.removeNotify();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (loading && spinnerTimer != null) spinnerTimer.start();
    }

    /** Sizes are system-controlled; explicit preferred sizes are ignored by design. */
    @Override
    public void setPreferredSize(Dimension d) {
        // intentionally ignored
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int textW = fm.stringWidth(getText() == null ? "" : getText());
        int padding = compact ? 24 : 36;
        int w = textW + padding + (loading ? 24 : 0);
        minWidth = Math.max(minWidth, w);
        if (fixedWidth > 0) w = fixedWidth;
        else w = minWidth;
        int h = compact ? Theme.CONTROL_HEIGHT_COMPACT : Theme.CONTROL_HEIGHT;
        return new Dimension(w, h);
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
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int w = getWidth();
        int h = getHeight();
        int arc = compact ? 8 : Theme.RADIUS_CONTROL;
        boolean on = isEnabled() || loading;
        double hv = on ? hover.get() : 0;
        double pv = on ? press.get() : 0;

        Color fill;
        Color border = null;
        Color fg;
        switch (variant) {
            case PRIMARY -> {
                fill = on ? Theme.mix(Theme.ACCENT_GOLD, Theme.ACCENT_GOLD_HOVER, hv) : Theme.NEUTRAL_TINT;
                fg = on ? Color.WHITE : Theme.DISABLED_FG;
            }
            case SUCCESS -> {
                fill = on ? Theme.mix(Theme.SUCCESS, Theme.mix(Theme.SUCCESS, Color.BLACK, 0.14), hv) : Theme.NEUTRAL_TINT;
                fg = on ? Color.WHITE : Theme.DISABLED_FG;
            }
            case DARK -> {
                fill = on ? Theme.mix(Theme.SIDEBAR_BG, Theme.SIDEBAR_RAISED, hv) : Theme.NEUTRAL_TINT;
                fg = on ? Color.WHITE : Theme.DISABLED_FG;
            }
            case DANGER -> {
                fill = on ? Theme.mix(Theme.DANGER_TINT, Theme.DANGER, hv) : Theme.NEUTRAL_TINT;
                border = on ? Theme.mix(Theme.alpha(Theme.DANGER, 120), Theme.DANGER, hv) : Theme.CARD_BORDER;
                fg = on ? Theme.mix(Theme.DANGER, Color.WHITE, hv) : Theme.DISABLED_FG;
            }
            case GHOST -> {
                fill = on ? Theme.alpha(Theme.ACCENT_GOLD, (int) (hv * 34)) : new Color(0, 0, 0, 0);
                fg = on ? Theme.mix(Theme.TEXT_SECONDARY, Theme.ACCENT_GOLD_HOVER, hv) : Theme.DISABLED_FG;
            }
            default -> { // SECONDARY
                fill = on ? Theme.mix(Theme.CARD_BG, Theme.GOLD_TINT, hv) : Theme.NEUTRAL_TINT;
                border = on ? Theme.mix(Theme.FIELD_BORDER, Theme.ACCENT_GOLD, hv) : Theme.CARD_BORDER;
                fg = on ? Theme.TEXT_PRIMARY : Theme.DISABLED_FG;
            }
        }

        // press feedback: darken slightly and inset 1px
        if (pv > 0) {
            fill = Theme.mix(fill, Color.BLACK, 0.07 * pv);
        }
        int inset = (int) Math.round(pv);

        // keyboard focus ring
        double fv = focus.get();
        if (fv > 0.01) {
            g2.setColor(Theme.alpha(Theme.ACCENT_GOLD, (int) (140 * fv)));
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
        }

        g2.setColor(fill);
        g2.fillRoundRect(inset, inset, w - inset * 2, h - inset * 2, arc, arc);
        if (border != null) {
            g2.setColor(border);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(inset, inset, w - 1 - inset * 2, h - 1 - inset * 2, arc, arc);
        }
        if (fv > 0.01) {
            g2.setColor(Theme.alpha(Theme.ACCENT_GOLD, (int) (200 * fv)));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
        }

        // label (+ spinner)
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        String text = getText() == null ? "" : getText();
        int textW = fm.stringWidth(text);
        int spinW = loading ? 22 : 0;
        int startX = (w - (textW + spinW)) / 2;
        int baseline = (h - fm.getHeight()) / 2 + fm.getAscent();

        if (loading) {
            int d = 14;
            int sy = (h - d) / 2;
            g2.setColor(Theme.alpha(fg, 70));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawOval(startX, sy, d, d);
            g2.setColor(fg);
            g2.draw(new Arc2D.Double(startX, sy, d, d, -spinnerAngle, 100, Arc2D.OPEN));
            startX += spinW;
        }
        g2.setColor(fg);
        g2.drawString(text, startX, baseline);
        g2.dispose();
    }
}
