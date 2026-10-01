package ui.components;

import ui.Theme;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure Graphics2D custom bar chart component (Section 10.7 & Phase 5).
 * Visualizes categorical aggregated metrics (e.g., status distribution, slot popularity).
 */
public class BarChartPanel extends JPanel {
    private String chartTitle = "";
    private List<String> categories = new ArrayList<>();
    private List<Double> values = new ArrayList<>();

    public BarChartPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(500, 260));
        setMinimumSize(new Dimension(300, 200));
    }

    public void setData(String title, List<String> categories, List<? extends Number> values) {
        this.chartTitle = title != null ? title : "";
        this.categories = new ArrayList<>();
        this.values = new ArrayList<>();

        if (categories != null && values != null) {
            int len = Math.min(categories.size(), values.size());
            for (int i = 0; i < len; i++) {
                this.categories.add(categories.get(i));
                Number n = values.get(i);
                this.values.add(n != null ? n.doubleValue() : 0.0);
            }
        }
        repaint();
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
        g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
        g2.setColor(Theme.CARD_BORDER);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);

        if (categories.isEmpty() || values.isEmpty()) {
            g2.setColor(Theme.TEXT_MUTED);
            g2.setFont(Theme.FONT_BODY);
            String emptyMsg = "No data available for chart";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(emptyMsg, (w - fm.stringWidth(emptyMsg)) / 2, h / 2);
            g2.dispose();
            return;
        }

        // Draw Chart Title
        int topMargin = 40;
        if (!chartTitle.isEmpty()) {
            g2.setColor(Theme.TEXT_PRIMARY);
            g2.setFont(Theme.FONT_BODY_BOLD);
            g2.drawString(chartTitle, 24, 28);
        }

        int bottomMargin = 45;
        int leftMargin = 30;
        int rightMargin = 30;

        int chartW = w - leftMargin - rightMargin;
        int chartH = h - topMargin - bottomMargin;

        if (chartW <= 0 || chartH <= 0) {
            g2.dispose();
            return;
        }

        // Find max value
        double maxVal = 1.0;
        for (Double val : values) {
            if (val > maxVal) {
                maxVal = val;
            }
        }

        // Baseline
        int baselineY = topMargin + chartH;
        g2.setColor(Theme.CARD_BORDER);
        g2.drawLine(leftMargin, baselineY, leftMargin + chartW, baselineY);

        int count = categories.size();
        int slotW = chartW / count;
        int barW = Math.min(60, Math.max(24, (int) (slotW * 0.55)));

        // Color palettes
        Color[] barColors = new Color[]{
                Theme.ACCENT_GOLD,
                Theme.SUCCESS,
                Theme.WARNING,
                Theme.DANGER,
                new Color(52, 152, 219),
                new Color(155, 89, 182)
        };

        for (int i = 0; i < count; i++) {
            double val = values.get(i);
            int barHeight = (int) Math.round((val / maxVal) * (chartH - 24));
            if (barHeight < 0) barHeight = 0;

            int barX = leftMargin + i * slotW + (slotW - barW) / 2;
            int barY = baselineY - barHeight;

            Color baseColor = barColors[i % barColors.length];
            Color lightColor = new Color(
                    Math.min(255, baseColor.getRed() + 35),
                    Math.min(255, baseColor.getGreen() + 35),
                    Math.min(255, baseColor.getBlue() + 35)
            );

            // Draw Bar with subtle gradient and rounded top
            GradientPaint gp = new GradientPaint(barX, barY, lightColor, barX, baselineY, baseColor);
            g2.setPaint(gp);
            g2.fillRoundRect(barX, barY, barW, barHeight + 4, 8, 8);

            // Clean bottom edge so it sits flat on baseline
            g2.setColor(Theme.CARD_BG);
            g2.fillRect(barX, baselineY + 1, barW, 6);

            // Value text on top
            g2.setColor(Theme.TEXT_PRIMARY);
            g2.setFont(Theme.FONT_SMALL_BOLD);
            String valStr = (val == Math.floor(val)) ? String.valueOf((long) val) : String.format("%.1f", val);
            FontMetrics valFm = g2.getFontMetrics();
            int valX = barX + (barW - valFm.stringWidth(valStr)) / 2;
            g2.drawString(valStr, valX, barY - 6);

            // Category text below baseline
            g2.setColor(Theme.TEXT_MUTED);
            g2.setFont(Theme.FONT_SMALL);
            String cat = categories.get(i);
            FontMetrics catFm = g2.getFontMetrics();
            // Truncate if too wide
            if (catFm.stringWidth(cat) > slotW + 10) {
                while (cat.length() > 3 && catFm.stringWidth(cat + "...") > slotW + 10) {
                    cat = cat.substring(0, cat.length() - 1);
                }
                cat = cat + "...";
            }
            int catX = leftMargin + i * slotW + (slotW - catFm.stringWidth(cat)) / 2;
            g2.drawString(cat, catX, baselineY + 20);
        }

        g2.dispose();
    }
}
