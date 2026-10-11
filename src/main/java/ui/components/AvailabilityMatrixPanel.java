package ui.components;

import model.AvailabilityCell;
import model.AvailabilityGrid;
import model.TimeSlot;
import ui.Theme;
import util.DbClock;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * One-Glance Availability Matrix (P5 - Section 6).
 * Renders an interactive 7-day × 5-slot grid with high-DPI Java2D vectors,
 * status indicators (Green, Amber, Red/Waitlist), keyboard navigation, and instant selection.
 */
public class AvailabilityMatrixPanel extends JComponent {
    private static final DateTimeFormatter DAY_HEADER_FMT = DateTimeFormatter.ofPattern("EEE dd");

    private static final int ROW_HEADER_WIDTH = 90;
    private static final int COL_HEADER_HEIGHT = 44;
    private static final int CELL_WIDTH = 96;
    private static final int CELL_HEIGHT = 50;
    private static final int PADDING = 10;

    private AvailabilityGrid grid;
    private int selectedRow = -1; // slot index
    private int selectedCol = -1; // date index
    private int hoverRow = -1;
    private int hoverCol = -1;

    private BiConsumer<LocalDate, TimeSlot> onCellSelected;
    private BiConsumer<LocalDate, TimeSlot> onWaitlistRequested;

    public AvailabilityMatrixPanel() {
        setOpaque(true);
        setBackground(Theme.CARD_BG);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        ToolTipManager.sharedInstance().registerComponent(this);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoverRow = -1;
                hoverCol = -1;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                handleClick(e.getX(), e.getY());
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyNavigation(e.getKeyCode());
            }
        });
    }

    public void setOnCellSelected(BiConsumer<LocalDate, TimeSlot> onCellSelected) {
        this.onCellSelected = onCellSelected;
    }

    public void setOnWaitlistRequested(BiConsumer<LocalDate, TimeSlot> onWaitlistRequested) {
        this.onWaitlistRequested = onWaitlistRequested;
    }

    public void setGrid(AvailabilityGrid grid) {
        this.grid = grid;
        this.selectedRow = -1;
        this.selectedCol = -1;
        this.hoverRow = -1;
        this.hoverCol = -1;

        if (grid != null && !grid.dates().isEmpty() && !grid.slots().isEmpty()) {
            int w = PADDING * 2 + ROW_HEADER_WIDTH + (grid.dates().size() * CELL_WIDTH);
            int h = PADDING * 2 + COL_HEADER_HEIGHT + (grid.slots().size() * CELL_HEIGHT);
            setPreferredSize(new Dimension(w, h));
            setMinimumSize(new Dimension(w, h));

            // Auto-select first available slot if any
            selectFirstAvailable();
        } else {
            setPreferredSize(new Dimension(400, 200));
        }
        revalidate();
        repaint();
    }

    private void selectFirstAvailable() {
        if (grid == null) return;
        List<LocalDate> dates = grid.dates();
        List<TimeSlot> slots = grid.slots();
        LocalDate today = DbClock.today();
        LocalTime now = DbClock.localTime();

        for (int c = 0; c < dates.size(); c++) {
            LocalDate d = dates.get(c);
            for (int r = 0; r < slots.size(); r++) {
                TimeSlot s = slots.get(r);
                boolean isPast = d.isBefore(today) || (d.isEqual(today) && now.isAfter(s.getStartTime()));
                if (isPast) continue;

                AvailabilityCell cell = grid.getCell(d, s.getSlotId());
                if (cell != null && cell.freeTables() > 0) {
                    selectedRow = r;
                    selectedCol = c;
                    notifySelected();
                    return;
                }
            }
        }
        // Fallback to first non-past
        for (int c = 0; c < dates.size(); c++) {
            LocalDate d = dates.get(c);
            for (int r = 0; r < slots.size(); r++) {
                TimeSlot s = slots.get(r);
                boolean isPast = d.isBefore(today) || (d.isEqual(today) && now.isAfter(s.getStartTime()));
                if (!isPast) {
                    selectedRow = r;
                    selectedCol = c;
                    notifySelected();
                    return;
                }
            }
        }
    }

    private void updateHover(int x, int y) {
        if (grid == null) return;
        int gridX = x - PADDING - ROW_HEADER_WIDTH;
        int gridY = y - PADDING - COL_HEADER_HEIGHT;

        int newCol = (gridX >= 0) ? gridX / CELL_WIDTH : -1;
        int newRow = (gridY >= 0) ? gridY / CELL_HEIGHT : -1;

        if (newCol >= grid.dates().size()) newCol = -1;
        if (newRow >= grid.slots().size()) newRow = -1;

        if (newRow != hoverRow || newCol != hoverCol) {
            hoverRow = newRow;
            hoverCol = newCol;
            repaint();
        }
    }

    private void handleClick(int x, int y) {
        if (grid == null) return;
        int gridX = x - PADDING - ROW_HEADER_WIDTH;
        int gridY = y - PADDING - COL_HEADER_HEIGHT;

        if (gridX >= 0 && gridY >= 0) {
            int col = gridX / CELL_WIDTH;
            int row = gridY / CELL_HEIGHT;

            if (col < grid.dates().size() && row < grid.slots().size()) {
                selectedRow = row;
                selectedCol = col;
                repaint();
                notifySelected();
            }
        }
    }

    private void handleKeyNavigation(int keyCode) {
        if (grid == null || grid.dates().isEmpty() || grid.slots().isEmpty()) return;
        int numRows = grid.slots().size();
        int numCols = grid.dates().size();

        if (selectedRow == -1) selectedRow = 0;
        if (selectedCol == -1) selectedCol = 0;

        switch (keyCode) {
            case KeyEvent.VK_UP -> {
                if (selectedRow > 0) selectedRow--;
            }
            case KeyEvent.VK_DOWN -> {
                if (selectedRow < numRows - 1) selectedRow++;
            }
            case KeyEvent.VK_LEFT -> {
                if (selectedCol > 0) selectedCol--;
            }
            case KeyEvent.VK_RIGHT -> {
                if (selectedCol < numCols - 1) selectedCol++;
            }
            default -> { return; }
        }
        repaint();
        notifySelected();
    }

    private void notifySelected() {
        if (grid == null || selectedRow < 0 || selectedCol < 0) return;
        LocalDate date = grid.dates().get(selectedCol);
        TimeSlot slot = grid.slots().get(selectedRow);
        AvailabilityCell cell = grid.getCell(date, slot.getSlotId());

        if (onCellSelected != null) {
            onCellSelected.accept(date, slot);
        }
        if (cell != null && cell.isFull() && onWaitlistRequested != null) {
            onWaitlistRequested.accept(date, slot);
        }
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (grid == null) return null;
        int gridX = e.getX() - PADDING - ROW_HEADER_WIDTH;
        int gridY = e.getY() - PADDING - COL_HEADER_HEIGHT;

        if (gridX >= 0 && gridY >= 0) {
            int col = gridX / CELL_WIDTH;
            int row = gridY / CELL_HEIGHT;
            if (col < grid.dates().size() && row < grid.slots().size()) {
                LocalDate date = grid.dates().get(col);
                TimeSlot slot = grid.slots().get(row);
                AvailabilityCell cell = grid.getCell(date, slot.getSlotId());
                if (cell != null) {
                    if (cell.isFull()) {
                        return "<html><b>" + date.format(DAY_HEADER_FMT) + " at " + slot.getLabel() + "</b><br>"
                                + "<font color='#DC2626'>● Fully Booked</font><br>"
                                + "Waitlist: " + cell.waitingCount() + " party(ies) waiting.<br>"
                                + "<i>Click to join Waitlist</i></html>";
                    } else {
                        return "<html><b>" + date.format(DAY_HEADER_FMT) + " at " + slot.getLabel() + "</b><br>"
                                + "<font color='#16A34A'>● " + cell.freeTables() + " of " + cell.totalFittingTables() + " tables available</font><br>"
                                + "<i>Click to view and select tables</i></html>";
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Background
        g2.setColor(Theme.CARD_BG);
        g2.fillRoundRect(0, 0, w, h, 14, 14);
        g2.setColor(Theme.CARD_BORDER);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);

        if (grid == null || grid.dates().isEmpty() || grid.slots().isEmpty()) {
            g2.setColor(Theme.TEXT_MUTED);
            g2.setFont(Theme.FONT_BODY);
            String msg = "No availability data loaded. Select party size above.";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            g2.dispose();
            return;
        }

        List<LocalDate> dates = grid.dates();
        List<TimeSlot> slots = grid.slots();
        LocalDate today = DbClock.today();
        LocalTime now = DbClock.localTime();

        // Draw Column Headers (Dates)
        g2.setFont(Theme.FONT_BODY_BOLD);
        for (int c = 0; c < dates.size(); c++) {
            LocalDate d = dates.get(c);
            int x = PADDING + ROW_HEADER_WIDTH + (c * CELL_WIDTH);
            int y = PADDING;

            boolean isToday = d.isEqual(today);

            if (isToday) {
                g2.setColor(new Color(200, 150, 62, 30));
                g2.fillRoundRect(x + 2, y + 2, CELL_WIDTH - 4, COL_HEADER_HEIGHT - 6, 8, 8);
            }

            g2.setColor(isToday ? Theme.ACCENT_GOLD : Theme.TEXT_PRIMARY);
            String dayStr = d.format(DAY_HEADER_FMT);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(dayStr, x + (CELL_WIDTH - fm.stringWidth(dayStr)) / 2, y + 20);

            if (isToday) {
                g2.setFont(new Font("SansSerif", Font.BOLD, 10));
                g2.setColor(Theme.ACCENT_GOLD);
                String badge = "TODAY";
                fm = g2.getFontMetrics();
                g2.drawString(badge, x + (CELL_WIDTH - fm.stringWidth(badge)) / 2, y + 34);
                g2.setFont(Theme.FONT_BODY_BOLD);
            }
        }

        // Draw Row Headers (Time Slots)
        for (int r = 0; r < slots.size(); r++) {
            TimeSlot s = slots.get(r);
            int x = PADDING;
            int y = PADDING + COL_HEADER_HEIGHT + (r * CELL_HEIGHT);

            g2.setFont(Theme.FONT_BODY_BOLD);
            g2.setColor(Theme.TEXT_SECONDARY);
            String label = s.getLabel();
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, x + ROW_HEADER_WIDTH - fm.stringWidth(label) - 12, y + (CELL_HEIGHT / 2) + 5);
        }

        // Draw Cells
        for (int r = 0; r < slots.size(); r++) {
            TimeSlot slot = slots.get(r);
            int y = PADDING + COL_HEADER_HEIGHT + (r * CELL_HEIGHT);

            for (int c = 0; c < dates.size(); c++) {
                LocalDate date = dates.get(c);
                int x = PADDING + ROW_HEADER_WIDTH + (c * CELL_WIDTH);

                AvailabilityCell cell = grid.getCell(date, slot.getSlotId());
                boolean isPast = date.isBefore(today) || (date.isEqual(today) && now.isAfter(slot.getStartTime()));
                boolean isSelected = (r == selectedRow && c == selectedCol);
                boolean isHover = (r == hoverRow && c == hoverCol);

                paintMatrixCell(g2, x + 3, y + 3, CELL_WIDTH - 6, CELL_HEIGHT - 6, cell, isPast, isSelected, isHover);
            }
        }

        g2.dispose();
    }

    private void paintMatrixCell(Graphics2D g2, int x, int y, int w, int h,
                                 AvailabilityCell cell, boolean isPast, boolean isSelected, boolean isHover) {
        // Base fill
        if (isPast) {
            g2.setColor(Theme.NEUTRAL_TINT);
            g2.fillRoundRect(x, y, w, h, 8, 8);
            g2.setColor(Theme.CARD_BORDER);
            g2.drawRoundRect(x, y, w, h, 8, 8);

            g2.setColor(Theme.DISABLED_FG);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            String str = "Ended";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(str, x + (w - fm.stringWidth(str)) / 2, y + (h / 2) + 4);
            return;
        }

        Color cellBg;
        Color textColor;
        Color dotColor;
        String mainText;
        String subText = null;

        if (cell == null || cell.totalFittingTables() == 0) {
            cellBg = Theme.APP_BG;
            dotColor = Theme.FIELD_BORDER;
            textColor = Theme.TEXT_MUTED;
            mainText = "No tables";
        } else if (cell.isFull()) {
            // Full -> Red / Waitlist
            cellBg = Theme.DANGER_TINT;
            dotColor = Theme.DANGER;
            textColor = Theme.DANGER;
            if (cell.waitingCount() > 0) {
                mainText = "○ WL " + cell.waitingCount();
                subText = "Waitlist";
            } else {
                mainText = "○ Full";
                subText = "Join WL";
            }
        } else if (cell.isFewLeft()) {
            // Few Left -> Amber
            cellBg = Theme.WARNING_TINT;
            dotColor = Theme.WARNING;
            textColor = Theme.mix(Theme.WARNING, Color.BLACK, 0.3);
            mainText = "◐ " + cell.freeTables() + " left";
            subText = cell.freeTables() + " of " + cell.totalFittingTables();
        } else {
            // Plenty Available -> Green
            cellBg = Theme.SUCCESS_TINT;
            dotColor = Theme.SUCCESS;
            textColor = Theme.SUCCESS;
            mainText = "● " + cell.freeTables() + " free";
            subText = cell.freeTables() + " of " + cell.totalFittingTables();
        }

        // Hover & Selection highlight adjustments
        if (isSelected) {
            g2.setColor(Theme.mix(cellBg, dotColor, 0.16));
        } else if (isHover) {
            g2.setColor(new Color(cellBg.getRed(), cellBg.getGreen(), cellBg.getBlue(), 220));
        } else {
            g2.setColor(cellBg);
        }
        g2.fillRoundRect(x, y, w, h, 8, 8);

        // Border
        if (isSelected) {
            g2.setColor(Theme.ACCENT_GOLD);
            g2.drawRoundRect(x, y, w, h, 8, 8);
            g2.drawRoundRect(x + 1, y + 1, w - 2, h - 2, 6, 6);
        } else if (isHover) {
            g2.setColor(dotColor);
            g2.drawRoundRect(x, y, w, h, 8, 8);
        } else {
            g2.setColor(new Color(dotColor.getRed(), dotColor.getGreen(), dotColor.getBlue(), 70));
            g2.drawRoundRect(x, y, w, h, 8, 8);
        }

        // Text
        g2.setColor(textColor);
        g2.setFont(Theme.FONT_BODY_BOLD);
        FontMetrics fm = g2.getFontMetrics();
        int ty = (subText != null) ? y + 18 : y + (h / 2) + 4;
        g2.drawString(mainText, x + (w - fm.stringWidth(mainText)) / 2, ty);

        if (subText != null) {
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(textColor.brighter());
            FontMetrics fms = g2.getFontMetrics();
            g2.drawString(subText, x + (w - fms.stringWidth(subText)) / 2, y + 33);
        }
    }
}
