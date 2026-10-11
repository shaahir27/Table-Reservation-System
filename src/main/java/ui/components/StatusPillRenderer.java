package ui.components;

import ui.Theme;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Table cell renderer that shows statuses as rounded pills. Colours come only from the RTRS palette:
 * gold = confirmed / manager, green = good / seated, amber = attention, red = problem, charcoal = finished.
 */
public class StatusPillRenderer extends JLabel implements TableCellRenderer {
    private Color pillBg = Theme.NEUTRAL_TINT;
    private Color pillFg = Theme.TEXT_PRIMARY;

    private static final Color GOLD_TEXT = Theme.mix(Theme.ACCENT_GOLD, Color.BLACK, 0.28);
    private static final Color AMBER_TEXT = Theme.mix(Theme.WARNING, Color.BLACK, 0.22);

    public StatusPillRenderer() {
        setOpaque(false);
        setHorizontalAlignment(SwingConstants.CENTER);
        setFont(Theme.FONT_SMALL_BOLD);
    }

    /** {foreground, background} for a status keyword. Shared so cards and pills always agree. */
    public static Color[] tones(String status) {
        String s = status == null ? "" : status.trim().toUpperCase();
        switch (s) {
            case "CONFIRMED":
            case "MANAGER":
                return new Color[]{GOLD_TEXT, Theme.alpha(Theme.ACCENT_GOLD, 40)};
            case "AVAILABLE":
            case "ACTIVE":
            case "SEATED":
            case "FULFILLED":
            case "ACCEPTED":
                return new Color[]{Theme.SUCCESS, Theme.alpha(Theme.SUCCESS, 34)};
            case "HELD":
            case "OFFERED":
            case "WAITING":
            case "PENDING":
            case "MAINTENANCE":
            case "NO_SHOW":
                return new Color[]{AMBER_TEXT, Theme.alpha(Theme.WARNING, 42)};
            case "CANCELLED":
            case "DANGER":
            case "OVERDUE":
            case "DECLINED":
                return new Color[]{Theme.DANGER, Theme.alpha(Theme.DANGER, 30)};
            case "COMPLETED":
                return new Color[]{Theme.SIDEBAR_BG, Theme.alpha(Theme.SIDEBAR_BG, 22)};
            default: // INACTIVE, LAPSED, NO_ANSWER, HOST, unknown
                return new Color[]{Theme.TEXT_SECONDARY, Theme.alpha(Theme.MAINTENANCE_GREY, 48)};
        }
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        String text = value != null ? value.toString() : "";
        setText(text.replace('_', ' '));
        Color[] t = tones(text);
        pillFg = t[0];
        pillBg = t[1];
        setForeground(pillFg);
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        String text = getText();
        int pillWidth = Math.min(getWidth() - 12, fm.stringWidth(text) + 28);
        int pillHeight = 26;
        int x = (getWidth() - pillWidth) / 2;
        int y = (getHeight() - pillHeight) / 2;

        g2.setColor(pillBg);
        g2.fillRoundRect(x, y, pillWidth, pillHeight, pillHeight, pillHeight);
        g2.setColor(pillFg);
        g2.drawString(text, x + (pillWidth - fm.stringWidth(text)) / 2, y + (pillHeight - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
