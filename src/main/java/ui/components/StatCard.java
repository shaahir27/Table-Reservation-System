package ui.components;

import ui.Icons;
import ui.Theme;
import ui.anim.Anim;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Metric card: dedicated top icon badge, caption, big number that counts up when it changes,
 * and a contextual subtitle. Designed for responsive layout so icons and text never collide.
 * Lifts on hover and supports click drill-down.
 */
public class StatCard extends JPanel {
    private final JLabel valueLabel;
    private final JLabel titleLabel;
    private final JLabel subtextLabel;
    private final JLabel actionHintLabel;
    private final IconBadge iconBadge;
    private String iconKey = "calendar";
    private Color tone = Theme.ACCENT_GOLD;

    private final Anim.Value hover = new Anim.Value(0, this::repaint);
    private final Anim.Value appear = new Anim.Value(1, this::repaint);
    private double shownNumber = 0;
    private Anim.Tween countTween;
    private Runnable clickAction;

    public StatCard(String title, String initialValue, String subtext) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        setPreferredSize(new Dimension(200, 140));
        setMinimumSize(new Dimension(130, 136));

        // Top Header Row: Icon Badge on left, subtle action arrow on right
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(0f);
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        iconBadge = new IconBadge();
        headerRow.add(iconBadge, BorderLayout.WEST);

        actionHintLabel = new JLabel("↗");
        actionHintLabel.setFont(Theme.FONT_BODY_BOLD);
        actionHintLabel.setForeground(Theme.alpha(Theme.TEXT_MUTED, 0));
        headerRow.add(actionHintLabel, BorderLayout.EAST);

        titleLabel = new JLabel(title.toUpperCase());
        titleLabel.setFont(Theme.FONT_SMALL_BOLD.deriveFont(10.5f));
        titleLabel.setForeground(Theme.TEXT_MUTED);
        titleLabel.setAlignmentX(0f);

        valueLabel = new JLabel(initialValue);
        valueLabel.setFont(Theme.FONT_STAT_NUM.deriveFont(26f));
        valueLabel.setForeground(Theme.TEXT_PRIMARY);
        valueLabel.setAlignmentX(0f);
        try {
            shownNumber = Double.parseDouble(initialValue.trim());
        } catch (NumberFormatException ignored) {
        }

        subtextLabel = new JLabel(subtext);
        subtextLabel.setFont(Theme.FONT_SMALL.deriveFont(11f));
        subtextLabel.setForeground(Theme.TEXT_MUTED);
        subtextLabel.setAlignmentX(0f);

        add(headerRow);
        add(Box.createVerticalStrut(Theme.SPACE_8));
        add(titleLabel);
        add(Box.createVerticalStrut(Theme.SPACE_4));
        add(valueLabel);
        add(Box.createVerticalStrut(2));
        add(subtextLabel);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover.animateTo(1, 160);
                if (clickAction != null) {
                    actionHintLabel.setForeground(tone);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!contains(e.getPoint())) {
                    hover.animateTo(0, 220);
                    actionHintLabel.setForeground(Theme.alpha(Theme.TEXT_MUTED, 0));
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (clickAction != null && SwingUtilities.isLeftMouseButton(e)) {
                    clickAction.run();
                }
            }
        });
    }

    private class IconBadge extends JPanel {
        IconBadge() {
            setOpaque(false);
            setPreferredSize(new Dimension(30, 30));
            setMinimumSize(new Dimension(30, 30));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.alpha(tone, 32));
            g2.fillRoundRect(0, 0, 30, 30, 10, 10);
            Icons.paint(g2, iconKey, 7, 7, 16, tone);
            g2.dispose();
        }
    }

    /** Sets click action for drilldown navigation. */
    public StatCard onClick(Runnable action) {
        this.clickAction = action;
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        return this;
    }

    /** Sets the corner icon (see {@link Icons}) and its tone colour. */
    public StatCard icon(String key, Color tone) {
        this.iconKey = key;
        this.tone = tone;
        iconBadge.repaint();
        repaint();
        return this;
    }

    /** Fade + slide-up entrance, delayed so a row of cards cascades. */
    public void playEntrance(int delayMs) {
        appear.set(0);
        Anim.later(delayMs, () -> appear.animateTo(1, 350, Anim.EASE_OUT));
    }

    public void setValue(String value) {
        double target;
        try {
            target = Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            if (countTween != null) countTween.cancel();
            valueLabel.setText(value);
            return;
        }
        if (countTween != null) countTween.cancel();
        final double from = shownNumber;
        if (from == target) {
            valueLabel.setText(value);
            return;
        }
        countTween = Anim.run(380, Anim.EASE_OUT, p -> {
            shownNumber = from + (target - from) * p;
            valueLabel.setText(String.valueOf((int) Math.round(shownNumber)));
        }, () -> valueLabel.setText(value));
        if (countTween == null) valueLabel.setText(value);
        shownNumber = target;
    }

    public void setSubtext(String subtext) {
        subtextLabel.setText(subtext);
    }

    @Override
    protected void paintChildren(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        double a = appear.get();
        if (a < 1) {
            g2.translate(0, (1 - a) * 14);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, a))));
        }
        super.paintChildren(g2);
        g2.dispose();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double a = appear.get();
        double l = hover.get();
        if (a < 1) {
            g2.translate(0, (1 - a) * 14);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, a))));
        }
        int w = getWidth();
        int h = getHeight() - 3;
        int arc = Theme.RADIUS_CARD;

        g2.setColor(new Color(23, 24, 28, (int) (10 + 14 * l)));
        g2.fillRoundRect(1, 2 + (int) Math.round(l * 1.5), w - 2, h, arc, arc);
        g2.setColor(new Color(23, 24, 28, 8));
        g2.fillRoundRect(0, 1, w, h, arc, arc);
        g2.setColor(Theme.CARD_BG);
        g2.fillRoundRect(0, 0, w, h, arc, arc);
        g2.setColor(Theme.mix(Theme.CARD_BORDER, tone, 0.5 * l));
        g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
        g2.dispose();
    }
}
