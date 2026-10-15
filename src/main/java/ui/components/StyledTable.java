package ui.components;

import ui.Theme;
import ui.anim.Anim;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Premium data table: roomy rows, uppercase muted header, animated row-hover band, gold-tint selection,
 * built-in empty state and a fade/slide "reveal" that panels trigger after (re)loading data.
 */
public class StyledTable extends JTable {
    private int hoverRow = -1;
    private boolean refitQueued;
    private String emptyMessage = "Nothing to show yet.";
    private final Anim.Value hoverFade = new Anim.Value(0, this::repaint);

    public StyledTable(TableModel model) {
        super(model);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setRowHeight(46);
        setIntercellSpacing(new Dimension(0, 0));
        setShowHorizontalLines(true);
        setShowVerticalLines(false);
        setGridColor(Theme.NEUTRAL_TINT);
        setFont(Theme.FONT_BODY);
        setForeground(Theme.TEXT_PRIMARY);
        setBackground(Theme.CARD_BG);
        setSelectionBackground(Theme.GOLD_TINT_STRONG);
        setSelectionForeground(Theme.TEXT_PRIMARY);
        setFillsViewportHeight(true);
        setFocusable(true);
        putClientProperty("JTable.autoStartsEdit", Boolean.FALSE);

        JTableHeader header = getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(0, 44));
        header.setDefaultRenderer(new HeaderRenderer());

        getModel().addTableModelListener(e -> {
            if (!refitQueued) {
                refitQueued = true;
                javax.swing.SwingUtilities.invokeLater(() -> {
                    refitQueued = false;
                    fitColumns();
                });
            }
        });

        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHoverRow(rowAtPoint(e.getPoint()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHoverRow(-1);
            }
        };
        addMouseMotionListener(hover);
        addMouseListener(hover);
    }

    public StyledTable emptyMessage(String message) {
        this.emptyMessage = message;
        return this;
    }

    /** Sizes every column to its content (header or widest cell); spare width is shared out proportionally. */
    public void fitColumns() {
        setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        FontMetrics hfm = getTableHeader().getFontMetrics(Theme.FONT_SMALL_BOLD.deriveFont(11.5f));
        for (int c = 0; c < getColumnCount(); c++) {
            javax.swing.table.TableColumn col = getColumnModel().getColumn(c);
            int w = hfm.stringWidth(String.valueOf(col.getHeaderValue()).toUpperCase()) + 40;
            int rows = Math.min(getRowCount(), 60);
            for (int r = 0; r < rows; r++) {
                Component comp = super.prepareRenderer(getCellRenderer(r, c), r, c);
                w = Math.max(w, comp.getPreferredSize().width + 34);
            }
            w = Math.min(w, 380);
            col.setMinWidth(Math.min(w, 60));
            col.setPreferredWidth(w);
        }
    }

    /** Fade + 10px slide-up of the whole table body. Call after repopulating the model. */
    public void reveal() {
        fitColumns();
        repaint();
    }

    private void setHoverRow(int row) {
        if (row != hoverRow) {
            hoverRow = row;
            hoverFade.set(row >= 0 ? 0.0 : 0.0);
            hoverFade.animateTo(row >= 0 ? 1.0 : 0.0, 120);
            repaint();
        }
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        boolean selected = isRowSelected(row);
        if (c instanceof JComponent jc) {
            jc.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
            if (jc.isOpaque()) {
                if (selected) {
                    jc.setBackground(Theme.GOLD_TINT_STRONG);
                } else if (row == hoverRow) {
                    jc.setBackground(Theme.mix(Theme.CARD_BG, Theme.GOLD_TINT, hoverFade.get()));
                } else {
                    jc.setBackground(Theme.CARD_BG);
                }
                if (!(c instanceof StatusPillRenderer) && jc.getForeground() != null && selected) {
                    // keep colored cells (role/status colours) but make plain text charcoal on the tint
                    if (jc.getForeground().equals(getSelectionForeground())) jc.setForeground(Theme.TEXT_PRIMARY);
                }
            }
        }
        return c;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        // opaque background first, then hover/selection bands, then the cells themselves
        g2.setColor(getBackground());
        g2.fillRect(0, 0, getWidth(), getHeight());
        if (hoverRow >= 0 && hoverRow < getRowCount() && !isRowSelected(hoverRow)) {
            Rectangle rect = getCellRect(hoverRow, 0, true);
            g2.setColor(Theme.mix(Theme.CARD_BG, Theme.GOLD_TINT, hoverFade.get()));
            g2.fillRect(0, rect.y, getWidth(), rect.height);
        }
        int sel = getSelectedRow();
        if (sel >= 0 && sel < getRowCount()) {
            Rectangle rect = getCellRect(sel, 0, true);
            g2.setColor(Theme.GOLD_TINT_STRONG);
            g2.fillRect(0, rect.y, getWidth(), rect.height);
            g2.setColor(Theme.ACCENT_GOLD);
            g2.fillRect(0, rect.y, 3, rect.height);
        }
        getUI().paint(g2, this);

        if (getRowCount() == 0) {
            Rectangle vis = getVisibleRect();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int cx = vis.x + vis.width / 2;
            int cy = vis.y + Math.min(vis.height / 2, 140);
            // soft glyph
            g2.setColor(Theme.NEUTRAL_TINT);
            g2.fillOval(cx - 22, cy - 46, 44, 44);
            g2.setColor(Theme.DISABLED_FG);
            g2.setStroke(new java.awt.BasicStroke(2.2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g2.drawRoundRect(cx - 10, cy - 34, 20, 20, 5, 5);
            g2.drawLine(cx - 6, cy - 27, cx + 6, cy - 27);
            g2.drawLine(cx - 6, cy - 21, cx + 2, cy - 21);
            g2.setFont(Theme.FONT_BODY);
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.TEXT_MUTED);
            g2.drawString(emptyMessage, cx - fm.stringWidth(emptyMessage) / 2, cy + 18);
        }
        g2.dispose();
    }

    /** Uppercase muted header that follows the alignment of its column. */
    private class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(table, value == null ? "" : value.toString().toUpperCase(),
                    false, false, row, column);
            l.setFont(Theme.FONT_SMALL_BOLD.deriveFont(11.5f));
            l.setForeground(Theme.TEXT_MUTED);
            l.setBackground(Theme.TABLE_HEADER_BG);
            l.setOpaque(true);
            l.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.CARD_BORDER),
                    BorderFactory.createEmptyBorder(0, 16, 0, 16)));
            int align = SwingConstants.LEFT;
            if (column < table.getColumnModel().getColumnCount()) {
                TableCellRenderer cr = table.getColumnModel().getColumn(column).getCellRenderer();
                if (cr instanceof StatusPillRenderer) align = SwingConstants.CENTER;
                else if (cr instanceof DefaultTableCellRenderer d && d.getHorizontalAlignment() == SwingConstants.CENTER)
                    align = SwingConstants.CENTER;
            }
            l.setHorizontalAlignment(align);
            return l;
        }
    }

    /** Convenience: a centered plain-text renderer for ID / count style columns. */
    public static DefaultTableCellRenderer centered() {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer();
        r.setHorizontalAlignment(SwingConstants.CENTER);
        return r;
    }
}
