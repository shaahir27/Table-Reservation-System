package ui.components;

import model.ReservationDetail;
import model.RestaurantTable;
import model.TableAvailability;
import model.TableState;
import model.TableStatus;
import ui.Theme;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Interactive 2D Dining Room Floor Plan (P5 - Section 5.3).
 * Custom Java2D vector blueprint canvas rendering architectural table layouts, perimeter chair dots,
 * live reservation state coloring (Available, Reserved, Seated, Held, Overdue, Maintenance),
 * click-to-inspect, and visual Drag-and-Drop Editor mode for Managers.
 */
public class FloorPlanPanel extends JComponent {
    public static final int GRID_UNIT = 24; // 1 unit = 24 pixels

    private final List<RestaurantTable> tables = new ArrayList<>();
    private final Map<Integer, TableAvailability> availabilityMap = new HashMap<>();

    private RestaurantTable selectedTable = null;
    private RestaurantTable draggedTable = null;
    private Point dragOffset = new Point();

    private boolean editMode = false;
    private boolean layoutModified = false;

    private Consumer<RestaurantTable> onTableSelected;
    private Consumer<Boolean> onLayoutModifiedChanged;

    public FloorPlanPanel() {
        setOpaque(true);
        setBackground(Theme.APP_BG);
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        setFocusable(true);
        ToolTipManager.sharedInstance().registerComponent(this);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMousePressed(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleMouseReleased(e);
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouseDragged(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                handleMouseMoved(e);
            }
        });

        // Spacious, uncompressed architectural blueprint dimensions
        setPreferredSize(new Dimension(1080, 720));
        setMinimumSize(new Dimension(840, 560));
    }

    public void setOnTableSelected(Consumer<RestaurantTable> onTableSelected) {
        this.onTableSelected = onTableSelected;
    }

    public void setOnLayoutModifiedChanged(Consumer<Boolean> onLayoutModifiedChanged) {
        this.onLayoutModifiedChanged = onLayoutModifiedChanged;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        setCursor(Cursor.getPredefinedCursor(editMode ? Cursor.MOVE_CURSOR : Cursor.DEFAULT_CURSOR));
        repaint();
    }

    public boolean isEditMode() {
        return editMode;
    }

    public boolean isLayoutModified() {
        return layoutModified;
    }

    public void setLayoutModified(boolean modified) {
        this.layoutModified = modified;
        if (onLayoutModifiedChanged != null) {
            onLayoutModifiedChanged.accept(modified);
        }
    }

    public List<RestaurantTable> getTables() {
        return new ArrayList<>(tables);
    }

    public void setData(List<RestaurantTable> tableList, List<TableAvailability> availabilities) {
        tables.clear();
        availabilityMap.clear();

        if (tableList != null) {
            tables.addAll(tableList);
        }

        if (availabilities != null) {
            for (TableAvailability a : availabilities) {
                if (a.getTable() != null) {
                    availabilityMap.put(a.getTable().getTableId(), a);
                }
            }
        }

        ensureAllTablesHavePositions();
        setLayoutModified(false);
        repaint();
    }

    public void setSelectedTable(RestaurantTable t) {
        this.selectedTable = t;
        repaint();
    }

    public RestaurantTable getSelectedTable() {
        return selectedTable;
    }

    private void ensureAllTablesHavePositions() {
        boolean anyMissing = false;
        for (RestaurantTable t : tables) {
            if (t.getPosX() == null || t.getPosY() == null) {
                anyMissing = true;
                break;
            }
        }
        if (anyMissing) {
            resetAutoLayout();
        }
    }

    /**
     * Spaces out all tables cleanly into their architectural dining zones:
     * Indoor (Top-Left), Window (Top-Right), Grand Hall (Bottom-Left), Outdoor (Bottom-Right).
     */
    public void resetAutoLayout() {
        int indoorX = 4, indoorY = 4;
        int windowX = 26, windowY = 4;
        int hallX = 4, hallY = 16;
        int outdoorX = 26, outdoorY = 16;

        for (RestaurantTable t : tables) {
            String loc = t.getLocation() != null ? t.getLocation().toLowerCase() : "";
            if (loc.contains("indoor")) {
                t.setPosX(indoorX);
                t.setPosY(indoorY);
                indoorX += 6;
                if (indoorX > 16) {
                    indoorX = 4;
                    indoorY += 5;
                }
            } else if (loc.contains("window")) {
                t.setPosX(windowX);
                t.setPosY(windowY);
                windowX += 6;
                if (windowX > 38) {
                    windowX = 26;
                    windowY += 5;
                }
            } else if (loc.contains("hall")) {
                t.setPosX(hallX);
                t.setPosY(hallY);
                hallX += 7;
                if (hallX > 17) {
                    hallX = 4;
                    hallY += 5;
                }
            } else {
                t.setPosX(outdoorX);
                t.setPosY(outdoorY);
                outdoorX += 6;
                if (outdoorX > 38) {
                    outdoorX = 26;
                    outdoorY += 5;
                }
            }
            if (t.getShape() == null) {
                t.setShape((t.getCapacity() <= 4 && t.getTableId() % 2 == 1) ? "ROUND" : "RECT");
            }
        }
        setLayoutModified(true);
        repaint();
    }

    private Rectangle getTableBounds(RestaurantTable t) {
        int px = (t.getPosX() != null ? t.getPosX() : 2) * GRID_UNIT;
        int py = (t.getPosY() != null ? t.getPosY() : 2) * GRID_UNIT;

        int w;
        int h;
        int cap = t.getCapacity();
        boolean isRound = "ROUND".equalsIgnoreCase(t.getShape());

        if (isRound) {
            int diameter = cap <= 2 ? 68 : (cap <= 4 ? 80 : 96);
            w = diameter;
            h = diameter;
        } else {
            if (cap <= 2) {
                w = 68;
                h = 58;
            } else if (cap <= 4) {
                w = 88;
                h = 72;
            } else if (cap <= 6) {
                w = 114;
                h = 78;
            } else if (cap <= 8) {
                w = 140;
                h = 84;
            } else {
                w = 168;
                h = 90;
            }
        }
        return new Rectangle(px, py, w, h);
    }

    private RestaurantTable getTableAt(int mx, int my) {
        for (int i = tables.size() - 1; i >= 0; i--) {
            RestaurantTable t = tables.get(i);
            Rectangle r = getTableBounds(t);
            Rectangle hitBox = new Rectangle(r.x - 8, r.y - 8, r.width + 16, r.height + 16);
            if (hitBox.contains(mx, my)) {
                return t;
            }
        }
        return null;
    }

    private void handleMousePressed(MouseEvent e) {
        RestaurantTable clicked = getTableAt(e.getX(), e.getY());
        if (clicked != null) {
            selectedTable = clicked;
            if (editMode) {
                draggedTable = clicked;
                Rectangle b = getTableBounds(clicked);
                dragOffset = new Point(e.getX() - b.x, e.getY() - b.y);
            }
            if (onTableSelected != null) {
                onTableSelected.accept(clicked);
            }
            repaint();
        } else {
            if (!editMode) {
                selectedTable = null;
                if (onTableSelected != null) {
                    onTableSelected.accept(null);
                }
                repaint();
            }
        }
    }

    private void handleMouseReleased(MouseEvent e) {
        if (editMode && draggedTable != null) {
            Rectangle b = getTableBounds(draggedTable);
            int snappedUnitX = Math.max(1, Math.round((float) b.x / GRID_UNIT));
            int snappedUnitY = Math.max(1, Math.round((float) b.y / GRID_UNIT));

            draggedTable.setPosX(snappedUnitX);
            draggedTable.setPosY(snappedUnitY);
            setLayoutModified(true);

            draggedTable = null;
            repaint();
        }
    }

    private void handleMouseDragged(MouseEvent e) {
        if (editMode && draggedTable != null) {
            int newX = Math.max(GRID_UNIT, e.getX() - dragOffset.x);
            int newY = Math.max(GRID_UNIT, e.getY() - dragOffset.y);

            int unitX = Math.round((float) newX / GRID_UNIT);
            int unitY = Math.round((float) newY / GRID_UNIT);

            draggedTable.setPosX(unitX);
            draggedTable.setPosY(unitY);
            setLayoutModified(true);
            repaint();
        }
    }

    private void handleMouseMoved(MouseEvent e) {
        RestaurantTable hovered = getTableAt(e.getX(), e.getY());
        setCursor(Cursor.getPredefinedCursor(hovered != null ? Cursor.HAND_CURSOR : (editMode ? Cursor.MOVE_CURSOR : Cursor.DEFAULT_CURSOR)));
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        RestaurantTable t = getTableAt(e.getX(), e.getY());
        if (t == null) return null;

        TableAvailability av = availabilityMap.get(t.getTableId());
        String statusText = t.getStatus().name();
        String activeBooking = "None";

        if (av != null) {
            statusText = av.getState().getDisplayName();
            if (av.getActiveReservation() != null) {
                ReservationDetail r = av.getActiveReservation();
                activeBooking = r.getCustomerName() + " (" + r.getGuestCount() + "p) · " + r.getStatus();
            }
        }

        return "<html><div style='padding:6px; font-family:sans-serif;'>"
                + "<b style='font-size:13px;'>Table " + t.getTableNumber() + "</b> · " + t.getCapacity() + " Seats<br>"
                + "Zone: <b>" + t.getLocation() + "</b><br>"
                + "Status: <b>" + statusText + "</b><br>"
                + "Reservation: " + activeBooking + "</div></html>";
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = Math.max(getWidth(), 1080);
        int h = Math.max(getHeight(), 720);

        // 1. Draw Architectural Floor Backdrop
        g2.setColor(Theme.APP_BG);
        g2.fillRect(0, 0, w, h);

        // Grid lines (subtle blueprint guidelines)
        g2.setColor(Theme.alpha(Theme.CARD_BORDER, 140));
        g2.setStroke(new BasicStroke(0.65f));
        for (int x = 0; x < w; x += GRID_UNIT) {
            g2.drawLine(x, 0, x, h);
        }
        for (int y = 0; y < h; y += GRID_UNIT) {
            g2.drawLine(0, y, w, y);
        }

        // Section Guides & Architectural Room Zones
        drawSectionBorders(g2, w, h);

        // 2. Render Tables & Chairs
        for (RestaurantTable t : tables) {
            paintTable(g2, t);
        }

        // 3. Edit Mode Overlay Badge
        if (editMode) {
            g2.setColor(new Color(154, 107, 31, 230));
            g2.fillRoundRect(20, 20, 250, 36, 10, 10);
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.FONT_BODY_BOLD);
            g2.drawString("✎ FLOOR PLAN DRAG & DROP MODE", 32, 43);
        }

        g2.dispose();
    }

    private void drawSectionBorders(Graphics2D g2, int w, int h) {
        Stroke dashed = new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{6.0f, 6.0f}, 0.0f);
        g2.setStroke(dashed);

        int halfW = w / 2;
        int halfH = h / 2;

        // Zone 1: Indoor Main Dining (Top-Left)
        drawZoneBox(g2, 18, 20, halfW - 36, halfH - 40, "INDOOR MAIN DINING", Theme.alpha(Theme.TEXT_MUTED, 200));

        // Zone 2: Scenic Window Area (Top-Right)
        drawZoneBox(g2, halfW + 18, 20, halfW - 36, halfH - 40, "SCENIC WINDOW TERRACE", Theme.alpha(Theme.ACCENT_GOLD, 220));

        // Zone 3: Grand Dining Hall (Bottom-Left)
        drawZoneBox(g2, 18, halfH + 16, halfW - 36, halfH - 36, "GRAND BANQUET HALL", Theme.alpha(Theme.SIDEBAR_BG, 220));

        // Zone 4: Outdoor Garden Patio (Bottom-Right)
        drawZoneBox(g2, halfW + 18, halfH + 16, halfW - 36, halfH - 36, "GARDEN & PATIO OUTDOOR", Theme.alpha(Theme.SUCCESS, 220));

        // Service Corridor Indicators
        g2.setFont(Theme.FONT_SMALL_BOLD.deriveFont(10.5f));
        g2.setColor(Theme.TEXT_MUTED);

        // Top center: Kitchen & Service Pass
        g2.drawString("🍽️ KITCHEN & SERVICE PASS", halfW - 75, 14);

        // Bottom center: Main Entrance & Host Stand
        g2.drawString("🛎️ MAIN ENTRANCE & HOST PODIUM", halfW - 95, h - 8);
    }

    private void drawZoneBox(Graphics2D g2, int x, int y, int bw, int bh, String label, Color tagColor) {
        g2.setColor(Theme.alpha(Theme.CARD_BORDER, 180));
        g2.drawRoundRect(x, y, bw, bh, 14, 14);

        // Label pill
        g2.setColor(Theme.alpha(tagColor, 26));
        g2.fillRoundRect(x + 12, y + 10, label.length() * 7 + 20, 22, 8, 8);
        g2.setColor(tagColor);
        g2.setFont(Theme.FONT_SMALL_BOLD.deriveFont(10.5f));
        g2.drawString(label, x + 20, y + 25);
    }

    private void paintTable(Graphics2D g2, RestaurantTable t) {
        Rectangle b = getTableBounds(t);
        boolean isSelected = (selectedTable != null && selectedTable.getTableId() == t.getTableId());
        boolean isRound = "ROUND".equalsIgnoreCase(t.getShape());

        TableAvailability av = availabilityMap.get(t.getTableId());
        TableState state = (av != null) ? av.getState() : (t.getStatus() == TableStatus.MAINTENANCE ? TableState.MAINTENANCE : TableState.AVAILABLE);

        Color fillColor;
        Color borderColor;
        Color textColor;

        switch (state) {
            case AVAILABLE -> {
                fillColor = Theme.SUCCESS_TINT;
                borderColor = Theme.SUCCESS;
                textColor = Theme.SUCCESS;
            }
            case RESERVED -> {
                fillColor = Theme.GOLD_TINT_STRONG;
                borderColor = Theme.ACCENT_GOLD;
                textColor = Theme.mix(Theme.ACCENT_GOLD, Color.BLACK, 0.35);
            }
            case SEATED -> {
                fillColor = Theme.mix(Theme.CARD_BG, Theme.SIDEBAR_BG, 0.15);
                borderColor = Theme.SIDEBAR_BG;
                textColor = Theme.SIDEBAR_BG;
            }
            case HELD -> {
                fillColor = Theme.WARNING_TINT;
                borderColor = Theme.WARNING;
                textColor = Theme.mix(Theme.WARNING, Color.BLACK, 0.35);
            }
            case OVERDUE -> {
                fillColor = Theme.DANGER_TINT;
                borderColor = Theme.DANGER;
                textColor = Theme.DANGER;
            }
            case MAINTENANCE -> {
                fillColor = Theme.NEUTRAL_TINT;
                borderColor = Theme.MAINTENANCE_GREY;
                textColor = Theme.TEXT_SECONDARY;
            }
            default -> {
                fillColor = Color.WHITE;
                borderColor = Theme.CARD_BORDER;
                textColor = Theme.TEXT_PRIMARY;
            }
        }

        // Draw Chairs around table perimeter
        paintChairs(g2, b, t.getCapacity(), isRound, borderColor);

        // Table Shadow
        g2.setColor(new Color(0, 0, 0, 18));
        if (isRound) {
            g2.fill(new Ellipse2D.Double(b.x + 2, b.y + 3, b.width, b.height));
        } else {
            g2.fill(new RoundRectangle2D.Double(b.x + 2, b.y + 3, b.width, b.height, 14, 14));
        }

        // Table Body
        g2.setColor(fillColor);
        if (isRound) {
            g2.fill(new Ellipse2D.Double(b.x, b.y, b.width, b.height));
        } else {
            g2.fill(new RoundRectangle2D.Double(b.x, b.y, b.width, b.height, 14, 14));
        }

        // Maintenance Hatching Pattern
        if (state == TableState.MAINTENANCE) {
            g2.setColor(new Color(203, 213, 225, 140));
            g2.setStroke(new BasicStroke(1.2f));
            for (int d = -b.height; d < b.width; d += 10) {
                g2.drawLine(b.x + Math.max(0, d), b.y, b.x + Math.min(b.width, d + b.height), b.y + b.height);
            }
        }

        // Table Border / Selected Focus Ring
        if (isSelected) {
            g2.setColor(Theme.ACCENT_GOLD);
            g2.setStroke(new BasicStroke(3.2f));
        } else {
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(1.8f));
        }

        if (isRound) {
            g2.draw(new Ellipse2D.Double(b.x, b.y, b.width, b.height));
        } else {
            g2.draw(new RoundRectangle2D.Double(b.x, b.y, b.width, b.height, 14, 14));
        }

        // Table Number & Capacity Label
        g2.setColor(textColor);
        g2.setFont(Theme.FONT_TITLE.deriveFont(22f));
        FontMetrics fm = g2.getFontMetrics();
        String tblNum = t.getTableNumber();
        int centerY = (state == TableState.SEATED) ? (b.y + (b.height / 2) - 3) : (b.y + (b.height / 2) + 2);
        g2.drawString(tblNum, b.x + (b.width - fm.stringWidth(tblNum)) / 2, centerY);

        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        FontMetrics fmCap = g2.getFontMetrics();
        String capStr = t.getCapacity() + "P · " + t.getLocation();
        g2.drawString(capStr, b.x + (b.width - fmCap.stringWidth(capStr)) / 2, centerY + 14);

        // Live Dining Turn Timer badge for SEATED tables
        if (state == TableState.SEATED && av != null && av.getActiveReservation() != null && av.getActiveReservation().getSeatedAt() != null) {
            long elapsed = java.time.Duration.between(av.getActiveReservation().getSeatedAt(), util.DbClock.localDateTime()).toMinutes();
            if (elapsed < 0) elapsed = 0;
            String timerStr = "⏱ " + elapsed + "m";
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            FontMetrics fmTimer = g2.getFontMetrics();
            int tw = fmTimer.stringWidth(timerStr) + 12;
            int tx = b.x + (b.width - tw) / 2;
            int ty = b.y + b.height - 18;

            Color timerBg = elapsed > 75 ? new Color(220, 38, 38, 230) : (elapsed > 45 ? new Color(217, 119, 6, 230) : new Color(22, 163, 74, 230));
            g2.setColor(timerBg);
            g2.fillRoundRect(tx, ty, tw, 15, 7, 7);
            g2.setColor(Color.WHITE);
            g2.drawString(timerStr, tx + 6, ty + 11);
        }
    }

    private void paintChairs(Graphics2D g2, Rectangle b, int capacity, boolean isRound, Color chairColor) {
        g2.setColor(new Color(chairColor.getRed(), chairColor.getGreen(), chairColor.getBlue(), 190));
        int chairSize = 9;

        if (isRound) {
            double cx = b.getCenterX();
            double cy = b.getCenterY();
            double radius = (b.width / 2.0) + 6.0;

            for (int i = 0; i < capacity; i++) {
                double angle = (2 * Math.PI * i) / capacity - (Math.PI / 2);
                int chairX = (int) (cx + radius * Math.cos(angle) - (chairSize / 2.0));
                int chairY = (int) (cy + radius * Math.sin(angle) - (chairSize / 2.0));
                g2.fill(new Ellipse2D.Double(chairX, chairY, chairSize, chairSize));
            }
        } else {
            int topBottomEach = Math.max(1, (capacity - 2) / 2);
            int sides = Math.min(2, capacity - (topBottomEach * 2));

            // Top chairs
            for (int i = 0; i < topBottomEach; i++) {
                int cx = b.x + ((b.width * (i + 1)) / (topBottomEach + 1)) - (chairSize / 2);
                int cy = b.y - chairSize - 2;
                g2.fillRoundRect(cx, cy, chairSize, chairSize, 4, 4);
            }
            // Bottom chairs
            for (int i = 0; i < topBottomEach; i++) {
                int cx = b.x + ((b.width * (i + 1)) / (topBottomEach + 1)) - (chairSize / 2);
                int cy = b.y + b.height + 2;
                g2.fillRoundRect(cx, cy, chairSize, chairSize, 4, 4);
            }
            // Left chair
            if (sides >= 1) {
                int cx = b.x - chairSize - 2;
                int cy = b.y + (b.height / 2) - (chairSize / 2);
                g2.fillRoundRect(cx, cy, chairSize, chairSize, 4, 4);
            }
            // Right chair
            if (sides >= 2) {
                int cx = b.x + b.width + 2;
                int cy = b.y + (b.height / 2) - (chairSize / 2);
                g2.fillRoundRect(cx, cy, chairSize, chairSize, 4, 4);
            }
        }
    }
}
