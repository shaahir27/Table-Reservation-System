package ui.components;

import ui.Theme;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Custom table cell renderer that displays entity statuses as elegant, rounded colored pills.
 */
public class StatusPillRenderer extends JLabel implements TableCellRenderer {
    private Color pillBg = Theme.CARD_BORDER;
    private Color pillFg = Theme.TEXT_PRIMARY;

    public StatusPillRenderer() {
        setOpaque(false);
        setHorizontalAlignment(SwingConstants.CENTER);
        setFont(Theme.FONT_SMALL_BOLD);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, 
                                                   boolean hasFocus, int row, int column) {
        String text = value != null ? value.toString() : "";
        setText(text);

        switch (text) {
            case "AVAILABLE":
            case "CONFIRMED":
                pillBg = new Color(46, 125, 91, 35); // Soft green
                pillFg = Theme.SUCCESS;
                break;
            case "COMPLETED":
                pillBg = new Color(52, 101, 164, 35); // Soft blue-grey
                pillFg = Color.decode("#2E5B88");
                break;
            case "CANCELLED":
            case "DANGER":
                pillBg = new Color(192, 57, 43, 35); // Soft red
                pillFg = Theme.DANGER;
                break;
            case "MAINTENANCE":
                pillBg = new Color(183, 121, 31, 35); // Soft amber
                pillFg = Theme.WARNING;
                break;
            case "INACTIVE":
            default:
                pillBg = new Color(154, 160, 166, 45); // Soft grey
                pillFg = Theme.MAINTENANCE_GREY;
                break;
        }

        setForeground(pillFg);
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int pillWidth = Math.min(getWidth() - 16, 110);
        int pillHeight = 24;
        int x = (getWidth() - pillWidth) / 2;
        int y = (getHeight() - pillHeight) / 2;

        g2.setColor(pillBg);
        g2.fillRoundRect(x, y, pillWidth, pillHeight, 12, 12);
        g2.dispose();

        super.paintComponent(g);
    }
}
