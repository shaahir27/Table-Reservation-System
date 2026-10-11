package ui.components;

import model.RestaurantTable;
import model.TableAvailability;
import model.TableState;
import ui.Theme;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Custom painted interactive table card (150x120) with antialiased rendering.
 * Supports AVAILABLE, RECOMMENDED, SELECTED, RESERVED, and MAINTENANCE states.
 */
public class TableCard extends JPanel {
    private static final int CARD_WIDTH = 150;
    private static final int CARD_HEIGHT = 120;
    private static final int ARC = 14;

    private final TableAvailability availability;
    private boolean isSelected = false;
    private boolean isHover = false;
    private final ui.anim.Anim.Value hoverAmt = new ui.anim.Anim.Value(0, this::repaint);
    private Consumer<TableCard> onClick;

    public TableCard(TableAvailability availability) {
        this(availability, false);
    }

    public TableCard(TableAvailability availability, boolean clickableForAll) {
        this.availability = availability;
        setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setMaximumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setMinimumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setOpaque(false);

        boolean isClickable = clickableForAll || availability.getState() == TableState.AVAILABLE;
        if (isClickable) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    isHover = true;
                    hoverAmt.animateTo(1, 140);
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHover = false;
                    hoverAmt.animateTo(0, 200);
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (onClick != null) {
                        onClick.accept(TableCard.this);
                    }
                }
            });
        }
    }

    public TableAvailability getAvailability() {
        return availability;
    }

    public RestaurantTable getTable() {
        return availability.getTable();
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
        repaint();
    }

    public void setOnClick(Consumer<TableCard> onClick) {
        this.onClick = onClick;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        TableState state = availability.getState();
        boolean isRec = availability.isRecommended();

        // 1. Background fill
        if (state == TableState.MAINTENANCE) {
            g2.setColor(Theme.NEUTRAL_TINT);
            g2.fillRoundRect(0, 0, w, h, ARC, ARC);

            // Paint hatched diagonal lines
            g2.setColor(Theme.CARD_BORDER);
            g2.setStroke(new BasicStroke(1.5f));
            for (int d = -h; d < w + h; d += 14) {
                g2.drawLine(d, 0, d + h, h);
            }
        } else if (state == TableState.RESERVED) {
            g2.setColor(Theme.NEUTRAL_TINT);
            g2.fillRoundRect(0, 0, w, h, ARC, ARC);
        } else if (isSelected) {
            g2.setColor(Theme.GOLD_TINT_STRONG);
            g2.fillRoundRect(0, 0, w, h, ARC, ARC);
        } else {
            g2.setColor(Theme.mix(Theme.CARD_BG, Theme.GOLD_TINT, hoverAmt.get()));
            g2.fillRoundRect(0, 0, w, h, ARC, ARC);
        }

        // 2. Borders and Badges
        if (isSelected) {
            g2.setColor(Theme.ACCENT_GOLD);
            g2.setStroke(new BasicStroke(2.2f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, ARC, ARC);
        } else if (isRec && state == TableState.AVAILABLE) {
            g2.setColor(Theme.ACCENT_GOLD);
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, ARC, ARC);

            // Draw "RECOMMENDED" badge pill at the top
            g2.setColor(Theme.ACCENT_GOLD);
            g2.fillRoundRect(w / 2 - 46, 6, 92, 18, 9, 9);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
            FontMetrics fmBadge = g2.getFontMetrics();
            String badgeText = "BEST FIT";
            int badgeX = (w - fmBadge.stringWidth(badgeText)) / 2;
            g2.drawString(badgeText, badgeX, 19);
        } else {
            g2.setColor(Theme.CARD_BORDER);
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
        }

        // 3. Status indicator dot (top-right)
        if (state == TableState.AVAILABLE) {
            g2.setColor(Theme.SUCCESS);
            g2.fillOval(w - 18, 12, 8, 8);
        } else if (state == TableState.RESERVED) {
            g2.setColor(Theme.DANGER);
            g2.fillOval(w - 18, 12, 8, 8);
        }

        // 4. Text Contents
        RestaurantTable table = availability.getTable();
        String tableNum = table != null ? table.getTableNumber() : "T??";
        String capacityText = table != null ? table.getCapacity() + " seats" : "";
        String locationText = table != null ? table.getLocation() : "";

        // Table Number (Big)
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        FontMetrics fmNum = g2.getFontMetrics();
        int numY = isRec ? 52 : 46;

        if (state == TableState.AVAILABLE) {
            g2.setColor(isSelected ? Theme.ACCENT_GOLD : Theme.TEXT_PRIMARY);
        } else {
            g2.setColor(Theme.TEXT_MUTED);
        }
        g2.drawString(tableNum, (w - fmNum.stringWidth(tableNum)) / 2, numY);

        // Capacity
        g2.setFont(Theme.FONT_BODY);
        FontMetrics fmCap = g2.getFontMetrics();
        g2.setColor(Theme.TEXT_MUTED);
        g2.drawString(capacityText, (w - fmCap.stringWidth(capacityText)) / 2, numY + 22);

        // Location or State tag
        g2.setFont(Theme.FONT_SMALL);
        FontMetrics fmLoc = g2.getFontMetrics();
        String statusSubtext;
        Color subtextColor = Theme.TEXT_MUTED;

        if (state == TableState.MAINTENANCE) {
            statusSubtext = "Maintenance";
            subtextColor = Theme.WARNING;
        } else if (state == TableState.RESERVED) {
            statusSubtext = "Booked";
            subtextColor = Theme.DANGER;
        } else {
            statusSubtext = locationText;
        }

        g2.setColor(subtextColor);
        g2.drawString(statusSubtext, (w - fmLoc.stringWidth(statusSubtext)) / 2, numY + 42);

        g2.dispose();
    }
}
