package ui.components;

import ui.Theme;
import ui.anim.Anim;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Rounded white surface with hairline border and a soft two-layer drop shadow.
 * Replaces the copy-pasted anonymous "createCardPanel" painters that every screen used to carry.
 */
public class Card extends JPanel {
    private static final int SHADOW = 3;

    private Color background = Theme.CARD_BG;
    private Color accent;
    private boolean hoverLift;
    private final Anim.Value lift = new Anim.Value(0, this::repaint);

    public Card() {
        this(Theme.SPACE_24);
    }

    public Card(int padding) {
        this(padding, padding);
    }

    public Card(int vertical, int horizontal) {
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(vertical, horizontal, vertical + SHADOW, horizontal));
    }

    public Card background(Color c) {
        this.background = c;
        return this;
    }

    /** Draws a slim accent stripe on the left edge. */
    public Card accent(Color c) {
        this.accent = c;
        return this;
    }

    public Card hoverLift(boolean on) {
        if (on && !hoverLift) {
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    lift.animateTo(1, 160);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    // ignore exits caused by entering a child component
                    java.awt.Point p = e.getPoint();
                    if (!contains(p)) lift.animateTo(0, 220);
                }
            });
        }
        hoverLift = on;
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight() - SHADOW;
        int arc = Theme.RADIUS_CARD;
        double l = lift.get();

        // shadow layers
        g2.setColor(new Color(23, 24, 28, (int) (10 + 12 * l)));
        g2.fillRoundRect(1, 2 + (int) Math.round(l), w - 2, h, arc, arc);
        g2.setColor(new Color(23, 24, 28, (int) (8 + 8 * l)));
        g2.fillRoundRect(0, 1, w, h, arc, arc);

        g2.setColor(background);
        g2.fillRoundRect(0, 0, w, h, arc, arc);
        g2.setColor(Theme.mix(Theme.CARD_BORDER, Theme.ACCENT_GOLD, 0.55 * l));
        g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

        if (accent != null) {
            g2.setColor(accent);
            g2.fillRoundRect(0, 14, 4, Math.max(0, h - 28), 4, 4);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
