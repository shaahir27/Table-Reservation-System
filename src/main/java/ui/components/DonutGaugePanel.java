package ui.components;

import ui.Theme;
import ui.anim.Anim;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

/**
 * Pure Graphics2D circular gauge/donut panel for percentage-based KPIs.
 * Features animated stroke growth, center percentage callout, and descriptive badge.
 */
public class DonutGaugePanel extends JPanel {
    private String title = "";
    private double percentage = 0.0;
    private String subtitle = "";
    private Color arcColor = Theme.SUCCESS;
    private boolean isInverseDanger = false; // if true, higher percentage = danger

    private final Anim.Value sweep = new Anim.Value(1, this::repaint);

    public DonutGaugePanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(240, 240));
        setMinimumSize(new Dimension(180, 180));
    }

    public void setGauge(String title, double percentage, String subtitle, boolean isInverseDanger) {
        this.title = title != null ? title : "";
        this.percentage = Math.max(0.0, Math.min(100.0, percentage));
        this.subtitle = subtitle != null ? subtitle : "";
        this.isInverseDanger = isInverseDanger;

        if (isInverseDanger) {
            if (this.percentage < 5.0) arcColor = Theme.SUCCESS;
            else if (this.percentage < 10.0) arcColor = Theme.WARNING;
            else arcColor = Theme.DANGER;
        } else {
            if (this.percentage >= 70.0) arcColor = Theme.SUCCESS;
            else if (this.percentage >= 45.0) arcColor = Theme.ACCENT_GOLD;
            else arcColor = Theme.MAINTENANCE_GREY;
        }

        sweep.set(0);
        sweep.animateTo(1, 700, Anim.EASE_OUT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Background Card
        g2.setColor(Theme.CARD_BG);
        g2.fillRoundRect(0, 0, w - 1, h - 1, 16, 16);
        g2.setColor(Theme.CARD_BORDER);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 16, 16);

        // Header Title
        if (!title.isEmpty()) {
            g2.setColor(Theme.TEXT_MUTED);
            g2.setFont(Theme.FONT_SMALL_BOLD);
            FontMetrics tfm = g2.getFontMetrics();
            g2.drawString(title.toUpperCase(), (w - tfm.stringWidth(title.toUpperCase())) / 2, 28);
        }

        // Ring dimensions
        int ringSize = Math.min(w - 60, h - 80);
        if (ringSize <= 20) {
            g2.dispose();
            return;
        }
        int rx = (w - ringSize) / 2;
        int ry = 40 + (h - 55 - ringSize) / 2;
        float strokeW = Math.max(10f, ringSize * 0.12f);

        // Background track ring
        g2.setColor(Theme.NEUTRAL_TINT);
        g2.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Ellipse2D.Double(rx + strokeW / 2, ry + strokeW / 2, ringSize - strokeW, ringSize - strokeW));

        // Active Arc
        double activeAngle = -(360.0 * (percentage / 100.0) * sweep.get());
        if (Math.abs(activeAngle) > 0.5) {
            g2.setColor(arcColor);
            g2.draw(new Arc2D.Double(rx + strokeW / 2, ry + strokeW / 2, ringSize - strokeW, ringSize - strokeW, 90, activeAngle, Arc2D.OPEN));
        }

        // Center Percentage Text
        g2.setColor(Theme.TEXT_PRIMARY);
        g2.setFont(Theme.FONT_TITLE.deriveFont(22f));
        String pctStr = String.format("%.1f%%", percentage * sweep.get());
        FontMetrics pfm = g2.getFontMetrics();
        int cx = (w - pfm.stringWidth(pctStr)) / 2;
        int cy = ry + ringSize / 2 + pfm.getAscent() / 2 - 6;
        g2.drawString(pctStr, cx, cy);

        // Center Subtitle
        if (!subtitle.isEmpty()) {
            g2.setColor(arcColor);
            g2.setFont(Theme.FONT_SMALL_BOLD);
            FontMetrics sfm = g2.getFontMetrics();
            int sx = (w - sfm.stringWidth(subtitle)) / 2;
            g2.drawString(subtitle, sx, cy + 18);
        }

        g2.dispose();
    }
}
