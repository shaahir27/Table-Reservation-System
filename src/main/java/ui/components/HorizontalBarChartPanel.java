package ui.components;

import ui.Theme;
import ui.anim.Anim;

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
 * Pure Graphics2D horizontal bar ranking chart.
 * Displays ranking metrics (e.g. Table covers leaderboard, Tag distribution)
 * with left-aligned category labels and right-aligned value badges.
 */
public class HorizontalBarChartPanel extends JPanel {
    private String chartTitle = "";
    private List<String> categories = new ArrayList<>();
    private List<Double> values = new ArrayList<>();
    private String unit = "";
    private Color barBaseColor = Theme.ACCENT_GOLD;

    private final Anim.Value grow = new Anim.Value(1, this::repaint);
    private int hoverIndex = -1;

    public HorizontalBarChartPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(460, 260));
        setMinimumSize(new Dimension(300, 180));

        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int count = categories.size();
                int idx = -1;
                if (count > 0) {
                    int topMargin = chartTitle.isEmpty() ? 20 : 44;
                    int rowH = 34;
                    int y = e.getY() - topMargin;
                    if (y >= 0) {
                        int row = y / rowH;
                        if (row >= 0 && row < count) idx = row;
                    }
                }
                if (idx != hoverIndex) {
                    hoverIndex = idx;
                    repaint();
                }
            }
        });

        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                hoverIndex = -1;
                repaint();
            }
        });
    }

    public void setBarColor(Color color) {
        this.barBaseColor = color != null ? color : Theme.ACCENT_GOLD;
        repaint();
    }

    public void setData(String title, List<String> categories, List<? extends Number> values, String unit) {
        this.chartTitle = title != null ? title : "";
        this.unit = unit != null ? unit : "";
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

        grow.set(0);
        grow.animateTo(1, 600, Anim.EASE_OUT);
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

        if (categories.isEmpty() || values.isEmpty()) {
            g2.setColor(Theme.TEXT_MUTED);
            g2.setFont(Theme.FONT_BODY);
            String emptyMsg = "No ranking data available";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(emptyMsg, (w - fm.stringWidth(emptyMsg)) / 2, h / 2);
            g2.dispose();
            return;
        }

        int topMargin = 20;
        if (!chartTitle.isEmpty()) {
            g2.setColor(Theme.TEXT_PRIMARY);
            g2.setFont(Theme.FONT_BODY_BOLD);
            g2.drawString(chartTitle, 20, 28);
            topMargin = 44;
        }

        double maxVal = 1.0;
        for (Double val : values) {
            if (val > maxVal) maxVal = val;
        }

        int count = Math.min(categories.size(), 7); // display top 7 max
        int labelWidth = 140;
        int badgeWidth = 70;
        int barStart = 20 + labelWidth + 10;
        int barMaxW = Math.max(20, w - barStart - badgeWidth - 24);
        int rowHeight = Math.min(36, Math.max(26, (h - topMargin - 16) / count));
        int barThickness = Math.max(12, rowHeight - 12);

        for (int i = 0; i < count; i++) {
            int rowY = topMargin + i * rowHeight;
            String cat = categories.get(i);
            double val = values.get(i);

            // Row hover background
            if (hoverIndex == i) {
                g2.setColor(Theme.HOVER_ROW_BG);
                g2.fillRoundRect(12, rowY - 2, w - 24, rowHeight - 2, 8, 8);
            }

            // Category Label
            g2.setColor(Theme.TEXT_PRIMARY);
            g2.setFont(Theme.FONT_SMALL_BOLD);
            FontMetrics fm = g2.getFontMetrics();
            String displayCat = cat;
            if (fm.stringWidth(displayCat) > labelWidth) {
                while (displayCat.length() > 3 && fm.stringWidth(displayCat + "...") > labelWidth) {
                    displayCat = displayCat.substring(0, displayCat.length() - 1);
                }
                displayCat = displayCat + "...";
            }
            int textY = rowY + (rowHeight + fm.getAscent() - fm.getDescent()) / 2 - 2;
            g2.drawString(displayCat, 20, textY);

            // Bar Track
            int barY = rowY + (rowHeight - barThickness) / 2 - 2;
            g2.setColor(Theme.NEUTRAL_TINT);
            g2.fillRoundRect(barStart, barY, barMaxW, barThickness, barThickness, barThickness);

            // Active Bar with ease animation
            int activeW = (int) Math.round((val / maxVal) * barMaxW * grow.get());
            if (activeW > 0) {
                Color tone = barBaseColor;
                if (hoverIndex >= 0 && hoverIndex != i) {
                    tone = Theme.mix(tone, Theme.CARD_BG, 0.4);
                }
                Color brightTone = new Color(
                        Math.min(255, tone.getRed() + 30),
                        Math.min(255, tone.getGreen() + 30),
                        Math.min(255, tone.getBlue() + 30)
                );
                GradientPaint gp = new GradientPaint(barStart, barY, brightTone, barStart + activeW, barY, tone);
                g2.setPaint(gp);
                g2.fillRoundRect(barStart, barY, Math.max(barThickness, activeW), barThickness, barThickness, barThickness);
            }

            // Value Badge
            g2.setColor(Theme.TEXT_SECONDARY);
            g2.setFont(Theme.FONT_SMALL_BOLD);
            String valStr = (val == Math.floor(val)) ? String.valueOf((long) val) : String.format("%.1f", val);
            if (!unit.isEmpty()) valStr += " " + unit;
            g2.drawString(valStr, barStart + barMaxW + 10, textY);
        }

        g2.dispose();
    }
}
