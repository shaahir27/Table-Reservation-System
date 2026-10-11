package ui.components;

import ui.Theme;
import ui.anim.Anim;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-blocking toast anchored bottom-right of the app window. Slides in, stacks upward when several
 * are visible, shows a countdown hairline, and fades out on its own.
 */
public class Toast extends JWindow {
    public enum Type {
        SUCCESS,
        ERROR,
        WARNING,
        INFO
    }

    private static final List<Toast> VISIBLE = new ArrayList<>();
    private static final int MARGIN_X = 32;
    private static final int MARGIN_Y = 36;
    private static final int GAP = 10;

    public static void show(java.awt.Component comp, String message, Type type) {
        if (comp == null) return;
        Window owner = comp instanceof Window w ? w : SwingUtilities.getWindowAncestor(comp);
        show(owner, message, type);
    }

    public static void show(Window owner, String message, Type type) {
        if (owner == null) return;
        SwingUtilities.invokeLater(() -> {
            Toast toast = new Toast(owner, message, type);
            toast.display();
        });
    }

    public static void success(java.awt.Component comp, String message) {
        show(comp, message, Type.SUCCESS);
    }

    public static void error(java.awt.Component comp, String message) {
        show(comp, message, Type.ERROR);
    }

    public static void warning(java.awt.Component comp, String message) {
        show(comp, message, Type.WARNING);
    }

    public static void info(java.awt.Component comp, String message) {
        show(comp, message, Type.INFO);
    }

    private final Window owner;
    private final int lifetimeMs;
    private double remaining = 1.0;
    private int targetY;
    private int currentY;
    private int slideX;
    private boolean dismissing;

    private Toast(Window owner, String message, Type type) {
        super(owner);
        this.owner = owner;
        this.lifetimeMs = type == Type.ERROR ? 4200 : 2800;

        final Color tone = switch (type) {
            case SUCCESS -> Theme.SUCCESS;
            case ERROR -> Theme.DANGER;
            case WARNING -> Theme.WARNING;
            default -> Theme.ACCENT_GOLD;
        };

        JPanel content = new JPanel(new BorderLayout(14, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g2.setColor(Theme.SIDEBAR_BG);
                g2.fillRoundRect(0, 0, w, h, 16, 16);
                g2.setColor(Theme.SIDEBAR_BORDER);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 16, 16);
                // icon badge
                g2.setColor(Theme.alpha(tone, 60));
                g2.fillOval(14, (h - 26) / 2, 26, 26);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = 14 + 13, cy = h / 2;
                switch (type) {
                    case SUCCESS -> {
                        g2.drawLine(cx - 5, cy, cx - 1, cy + 4);
                        g2.drawLine(cx - 1, cy + 4, cx + 6, cy - 4);
                    }
                    case ERROR -> {
                        g2.drawLine(cx - 4, cy - 4, cx + 4, cy + 4);
                        g2.drawLine(cx + 4, cy - 4, cx - 4, cy + 4);
                    }
                    case WARNING -> {
                        g2.drawLine(cx, cy - 6, cx, cy + 1);
                        g2.fillOval(cx - 1, cy + 4, 3, 3);
                    }
                    default -> {
                        g2.drawLine(cx, cy - 1, cx, cy + 6);
                        g2.fillOval(cx - 1, cy - 6, 3, 3);
                    }
                }
                // lifetime hairline
                g2.setColor(Theme.alpha(tone, 200));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int bar = (int) ((w - 36) * remaining);
                g2.drawLine(18, h - 5, 18 + bar, h - 5);
                g2.dispose();
            }
        };
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(14, 54, 16, 20));

        String html = "<html><body style='width:300px'>" + message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</body></html>";
        JLabel label = new JLabel(html);
        label.setFont(Theme.FONT_BODY_BOLD);
        label.setForeground(Color.WHITE);
        content.add(label, BorderLayout.CENTER);

        setBackground(new Color(0, 0, 0, 0));
        setContentPane(content);
        pack();
    }

    private int baseX() {
        Point p = owner.getLocationOnScreen();
        return Math.max(p.x + owner.getWidth() - getWidth() - MARGIN_X, 10);
    }

    private void restack() {
        Point p = owner.getLocationOnScreen();
        int y = p.y + owner.getHeight() - MARGIN_Y;
        for (int i = VISIBLE.size() - 1; i >= 0; i--) {
            Toast t = VISIBLE.get(i);
            y -= t.getHeight();
            t.targetY = Math.max(y, 10);
            y -= GAP;
        }
    }

    private void display() {
        if (owner == null || !owner.isShowing()) return;
        VISIBLE.add(this);
        restack();
        for (Toast t : new ArrayList<>(VISIBLE)) {
            if (t != this) t.glideTo(t.targetY);
        }
        currentY = targetY + 10;
        slideX = 36;
        setAlwaysOnTop(true);
        setLocation(baseX() + slideX, currentY);
        try {
            setOpacity(0f);
        } catch (Exception ignored) {
        }
        setVisible(true);

        Anim.run(320, Anim.EASE_OUT, p -> {
            slideX = (int) Math.round(36 * (1 - p));
            currentY = (int) Math.round(targetY + 10 * (1 - p));
            setLocation(baseX() + slideX, currentY);
            try {
                setOpacity((float) Math.max(0, Math.min(1, p)));
            } catch (Exception ignored) {
            }
        });

        long start = System.nanoTime();
        Timer timer = new Timer(33, null);
        timer.addActionListener(e -> {
            double elapsed = (System.nanoTime() - start) / 1_000_000.0;
            remaining = Math.max(0, 1.0 - elapsed / lifetimeMs);
            repaint();
            if (elapsed >= lifetimeMs) {
                timer.stop();
                dismiss();
            }
        });
        timer.start();
    }

    private void glideTo(int newY) {
        final int from = currentY;
        Anim.run(240, Anim.EASE_OUT, p -> {
            if (dismissing) return;
            currentY = (int) Math.round(from + (newY - from) * p);
            setLocation(baseX() + slideX, currentY);
        });
    }

    private void dismiss() {
        if (dismissing) return;
        dismissing = true;
        Anim.run(260, Anim.EASE_OUT, p -> {
            try {
                setOpacity((float) Math.max(0, 1 - p));
            } catch (Exception ignored) {
            }
            setLocation(baseX() + (int) Math.round(24 * p), currentY);
        }, () -> {
            VISIBLE.remove(this);
            setVisible(false);
            dispose();
            if (!VISIBLE.isEmpty() && owner.isShowing()) {
                restack();
                for (Toast t : VISIBLE) t.glideTo(t.targetY);
            }
        });
        if (!Anim.isEnabled()) {
            VISIBLE.remove(this);
            setVisible(false);
            dispose();
        }
    }
}
