package ui.auth;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;

/**
 * High-precision Java2D vector graphics and icons for authentication panels.
 * Resolution-independent, crisp on all HiDPI scaling factors.
 */
public final class AuthIcons {

    private AuthIcons() {}

    /**
     * Creates a vector icon for text fields.
     */
    public static Icon userIcon(int size, Color color) {
        return new VectorIcon(size, size, (g2, w, h) -> {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Head
            int headR = w / 4;
            int headX = (w - headR * 2) / 2;
            int headY = h / 6;
            g2.drawOval(headX, headY, headR * 2, headR * 2);

            // Torso arc
            int bodyW = (int) (w * 0.72);
            int bodyH = (int) (h * 0.55);
            int bodyX = (w - bodyW) / 2;
            int bodyY = (int) (h * 0.55);
            g2.drawArc(bodyX, bodyY, bodyW, bodyH, 0, 180);
        });
    }

    public static Icon lockIcon(int size, Color color) {
        return new VectorIcon(size, size, (g2, w, h) -> {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Shackle
            int shackleW = (int) (w * 0.44);
            int shackleH = (int) (h * 0.42);
            int shackleX = (w - shackleW) / 2;
            int shackleY = h / 8;
            g2.draw(new Arc2D.Float(shackleX, shackleY, shackleW, shackleH * 2, 0, 180, Arc2D.OPEN));

            // Padlock Body
            int bodyW = (int) (w * 0.68);
            int bodyH = (int) (h * 0.46);
            int bodyX = (w - bodyW) / 2;
            int bodyY = (int) (h * 0.45);
            g2.drawRoundRect(bodyX, bodyY, bodyW, bodyH, 5, 5);

            // Keyhole
            int dotR = Math.max(2, w / 10);
            g2.fillOval((w - dotR) / 2, bodyY + bodyH / 4, dotR, dotR);
            g2.drawLine(w / 2, bodyY + bodyH / 4 + dotR, w / 2, bodyY + bodyH * 3 / 4);
        });
    }

    public static Icon badgeIcon(int size, Color color) {
        return new VectorIcon(size, size, (g2, w, h) -> {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // ID Card outline
            int cw = (int) (w * 0.7);
            int ch = (int) (h * 0.76);
            int cx = (w - cw) / 2;
            int cy = (int) (h * 0.16);
            g2.drawRoundRect(cx, cy, cw, ch, 4, 4);

            // Clip slot
            g2.drawLine(w / 2 - 3, cy - 2, w / 2 + 3, cy - 2);

            // Avatar circle and line
            g2.drawOval(w / 2 - 2, cy + 4, 5, 5);
            g2.drawLine(cx + 4, cy + ch - 5, cx + cw - 4, cy + ch - 5);
        });
    }

    public static Icon shieldCheckIcon(int size, Color color) {
        return new VectorIcon(size, size, (g2, w, h) -> {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            GeneralPath p = new GeneralPath();
            float left = w * 0.18f;
            float right = w * 0.82f;
            float mid = w * 0.5f;
            float top = h * 0.15f;
            float midY = h * 0.55f;
            float bottom = h * 0.88f;

            p.moveTo(mid, top);
            p.lineTo(right, top + 2);
            p.quadTo(right, midY, mid, bottom);
            p.quadTo(left, midY, left, top + 2);
            p.closePath();
            g2.draw(p);

            // Checkmark
            g2.drawLine((int)(w * 0.38f), (int)(h * 0.52f), (int)(w * 0.48f), (int)(h * 0.62f));
            g2.drawLine((int)(w * 0.48f), (int)(h * 0.62f), (int)(w * 0.64f), (int)(h * 0.42f));
        });
    }

    /**
     * Paints a luxury restaurant cloche & dining crest with gold gradient sheen.
     */
    public static void paintBrandCrest(Graphics2D g2, int cx, int cy, int diameter) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int r = diameter / 2;

        // Ambient radial gold glow
        RadialGradientPaint glow = new RadialGradientPaint(
                cx, cy, r * 1.5f,
                new float[]{0.0f, 0.4f, 1.0f},
                new Color[]{
                        new Color(200, 150, 62, 50),
                        new Color(200, 150, 62, 15),
                        new Color(200, 150, 62, 0)
                }
        );
        g2.setPaint(glow);
        g2.fill(new Ellipse2D.Float(cx - r * 1.5f, cy - r * 1.5f, r * 3f, r * 3f));

        // Outer circular gold border
        LinearGradientPaint goldGrad = new LinearGradientPaint(
                cx - r, cy - r, cx + r, cy + r,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{
                        new Color(235, 205, 120),
                        new Color(200, 150, 62),
                        new Color(145, 105, 35)
                }
        );
        g2.setPaint(goldGrad);
        g2.setStroke(new BasicStroke(1.8f));
        g2.draw(new Ellipse2D.Float(cx - r, cy - r, diameter, diameter));

        // Inner dashed fine ring
        float[] dash = {3.0f, 4.0f};
        g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));
        g2.setColor(new Color(200, 150, 62, 120));
        g2.draw(new Ellipse2D.Float(cx - r + 5, cy - r + 5, diameter - 10, diameter - 10));

        // 4 Cardinal Star Diamonds
        g2.setColor(new Color(235, 205, 120));
        paintDiamond(g2, cx, cy - r, 3);
        paintDiamond(g2, cx, cy + r, 3);
        paintDiamond(g2, cx - r, cy, 3);
        paintDiamond(g2, cx + r, cy, 3);

        // Cloche Dome (Vector Art)
        g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setPaint(goldGrad);

        // Top Handle
        g2.draw(new Ellipse2D.Float(cx - 3.5f, cy - 18, 7, 5));

        // Cloche Lid Curve
        GeneralPath cloche = new GeneralPath();
        cloche.moveTo(cx - 19, cy + 3);
        cloche.curveTo(cx - 18, cy - 11, cx + 18, cy - 11, cx + 19, cy + 3);
        g2.draw(cloche);

        // Cloche Platter Base
        g2.draw(new RoundRectangle2D.Float(cx - 23, cy + 4, 46, 3, 2, 2));

        // Subtle Dining Steam Curves
        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(235, 205, 120, 160));
        GeneralPath steam1 = new GeneralPath();
        steam1.moveTo(cx - 5, cy - 8);
        steam1.quadTo(cx - 2, cy - 11, cx - 4, cy - 14);
        g2.draw(steam1);

        GeneralPath steam2 = new GeneralPath();
        steam2.moveTo(cx + 4, cy - 8);
        steam2.quadTo(cx + 7, cy - 11, cx + 5, cy - 14);
        g2.draw(steam2);
    }

    private static void paintDiamond(Graphics2D g2, int x, int y, int radius) {
        GeneralPath p = new GeneralPath();
        p.moveTo(x, y - radius);
        p.lineTo(x + radius, y);
        p.lineTo(x, y + radius);
        p.lineTo(x - radius, y);
        p.closePath();
        g2.fill(p);
    }

    /**
     * Paints an architectural CAD dining room floorplan blueprint as atmospheric background art.
     */
    public static void paintFloorplanBlueprint(Graphics2D g2, int width, int height) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Architectural Grid Dots / Lines (Faint Gold)
        Color gridColor = new Color(200, 150, 62, 10);
        g2.setColor(gridColor);
        int step = 32;
        for (int x = 0; x < width; x += step) {
            g2.drawLine(x, 0, x, height);
        }
        for (int y = 0; y < height; y += step) {
            g2.drawLine(0, y, width, y);
        }

        // Blueprint Wireframe Color (Warm Champagne 6% alpha)
        Color wireColor = new Color(210, 165, 80, 22);
        Color wireFill = new Color(210, 165, 80, 7);
        Color textColor = new Color(210, 165, 80, 35);
        g2.setColor(wireColor);
        g2.setStroke(new BasicStroke(1.2f));

        Font labelFont = new Font("SansSerif", Font.PLAIN, 9);
        g2.setFont(labelFont);

        // --- Zone Title Badge ---
        g2.drawRoundRect(24, height - 60, 190, 26, 6, 6);
        g2.setColor(textColor);
        g2.drawString("ZONE A • MAIN DINING SALON", 34, height - 43);

        // --- Table T01: Round 4-Top (Top Center) ---
        int t1X = 140, t1Y = 190, t1R = 26;
        g2.setColor(wireFill);
        g2.fillOval(t1X - t1R, t1Y - t1R, t1R * 2, t1R * 2);
        g2.setColor(wireColor);
        g2.drawOval(t1X - t1R, t1Y - t1R, t1R * 2, t1R * 2);
        paintChairsCircle(g2, t1X, t1Y, t1R + 8, 4, wireColor);
        g2.setColor(textColor);
        g2.drawString("T01 • 4P", t1X - 16, t1Y + 3);

        // --- Table T02: Rectangular Booth (Left) ---
        int b1X = 35, b1Y = 300, b1W = 76, b1H = 46;
        g2.setColor(wireFill);
        g2.fillRoundRect(b1X, b1Y, b1W, b1H, 8, 8);
        g2.setColor(wireColor);
        g2.drawRoundRect(b1X, b1Y, b1W, b1H, 8, 8);
        // Left & right booth bench cushions
        g2.drawRoundRect(b1X - 10, b1Y + 2, 8, b1H - 4, 3, 3);
        g2.drawRoundRect(b1X + b1W + 2, b1Y + 2, 8, b1H - 4, 3, 3);
        g2.setColor(textColor);
        g2.drawString("B01 • BOOTH", b1X + 8, b1Y + 26);

        // --- Table T03: Round 6-Top (Center) ---
        int t3X = 260, t3Y = 350, t3R = 34;
        g2.setColor(wireFill);
        g2.fillOval(t3X - t3R, t3Y - t3R, t3R * 2, t3R * 2);
        g2.setColor(wireColor);
        g2.drawOval(t3X - t3R, t3Y - t3R, t3R * 2, t3R * 2);
        paintChairsCircle(g2, t3X, t3Y, t3R + 9, 6, wireColor);
        g2.setColor(textColor);
        g2.drawString("T03 • 6P VIP", t3X - 22, t3Y + 3);

        // --- Table T04: Long Banquet (Bottom) ---
        int t4X = 70, t4Y = 470, t4W = 110, t4H = 44;
        g2.setColor(wireFill);
        g2.fillRoundRect(t4X, t4Y, t4W, t4H, 6, 6);
        g2.setColor(wireColor);
        g2.drawRoundRect(t4X, t4Y, t4W, t4H, 6, 6);
        // 3 Chairs on top and 3 on bottom
        for (int i = 0; i < 3; i++) {
            int cx = t4X + 18 + i * 36;
            g2.drawOval(cx - 5, t4Y - 10, 10, 8);
            g2.drawOval(cx - 5, t4Y + t4H + 2, 10, 8);
        }
        g2.setColor(textColor);
        g2.drawString("T04 • BANQUET (8)", t4X + 12, t4Y + 26);

        // --- Architectural Dimension Line with arrows ---
        g2.setColor(new Color(210, 165, 80, 20));
        g2.drawLine(35, 270, 380, 270);
        g2.drawLine(35, 265, 35, 275);
        g2.drawLine(380, 265, 380, 275);
        g2.drawString("DIMENSION: 16.4 m", 170, 266);
    }

    private static void paintChairsCircle(Graphics2D g2, int cx, int cy, int dist, int count, Color color) {
        g2.setColor(color);
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            int px = (int) (cx + dist * Math.cos(angle));
            int py = (int) (cy + dist * Math.sin(angle));
            g2.drawOval(px - 4, py - 4, 8, 8);
        }
    }

    @FunctionalInterface
    public interface IconPainter {
        void paint(Graphics2D g2, int width, int height);
    }

    private static class VectorIcon implements Icon {
        private final int width;
        private final int height;
        private final IconPainter painter;

        public VectorIcon(int width, int height, IconPainter painter) {
            this.width = width;
            this.height = height;
            this.painter = painter;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            painter.paint(g2, width, height);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }
}
