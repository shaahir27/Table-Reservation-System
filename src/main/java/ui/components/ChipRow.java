package ui.components;

import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Flush-aligned row that wraps onto new lines when it runs out of width and vertically centres
 * mixed-height items (chips next to a date field). Replaces FlowLayout, which indents the first item.
 * Use {@link #layout(int, boolean)} to apply the same behaviour to any existing container.
 */
public class ChipRow extends JPanel {

    public ChipRow(int gap) {
        this(gap, false);
    }

    public ChipRow(int gap, boolean alignRight) {
        setOpaque(false);
        setLayout(layout(gap, alignRight));
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    public static LayoutManager layout(int gap, boolean alignRight) {
        return new LayoutManager() {
            private int lastReportedHeight = -1;

            @Override
            public void addLayoutComponent(String name, Component comp) {
            }

            @Override
            public void removeLayoutComponent(Component comp) {
            }

            @Override
            public Dimension preferredLayoutSize(Container target) {
                int w = target.getWidth();
                if (w <= 0 && target.getParent() != null) w = target.getParent().getWidth();
                if (w <= 0) w = Integer.MAX_VALUE / 4;
                Dimension d = measure(target, w, false);
                lastReportedHeight = d.height;
                return d;
            }

            @Override
            public Dimension minimumLayoutSize(Container target) {
                return new Dimension(0, measure(target, Math.max(1, target.getWidth()), false).height);
            }

            @Override
            public void layoutContainer(Container target) {
                Dimension d = measure(target, Math.max(1, target.getWidth()), true);
                if (d.height != lastReportedHeight && target.getParent() != null) {
                    lastReportedHeight = d.height;
                    target.getParent().revalidate();
                }
            }

            private Dimension measure(Container target, int maxWidth, boolean place) {
                Insets in = target.getInsets();
                int avail = maxWidth - in.left - in.right;
                List<List<Component>> rows = new ArrayList<>();
                List<Component> cur = new ArrayList<>();
                int x = 0;
                int widest = 0;
                for (Component c : target.getComponents()) {
                    if (!c.isVisible()) continue;
                    Dimension p = c.getPreferredSize();
                    if (!cur.isEmpty() && x + p.width > avail) {
                        rows.add(cur);
                        cur = new ArrayList<>();
                        x = 0;
                    }
                    cur.add(c);
                    x += p.width + gap;
                    widest = Math.max(widest, x - gap);
                }
                if (!cur.isEmpty()) rows.add(cur);

                int y = in.top;
                for (List<Component> row : rows) {
                    int rowH = 0;
                    int rowW = -gap;
                    for (Component c : row) {
                        Dimension p = c.getPreferredSize();
                        rowH = Math.max(rowH, p.height);
                        rowW += p.width + gap;
                    }
                    if (place) {
                        int px = alignRight ? maxWidth - in.right - rowW : in.left;
                        for (Component c : row) {
                            Dimension p = c.getPreferredSize();
                            c.setBounds(px, y + (rowH - p.height) / 2, p.width, p.height);
                            px += p.width + gap;
                        }
                    }
                    y += rowH + gap;
                }
                if (!rows.isEmpty()) y -= gap;
                return new Dimension(widest + in.left + in.right, y + in.bottom);
            }
        };
    }
}
