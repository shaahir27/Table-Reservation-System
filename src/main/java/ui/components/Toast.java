package ui.components;

import ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;

/**
 * Non-blocking, auto-dismissing toast notification positioned at the bottom-right of the window.
 */
public class Toast extends JWindow {
    public enum Type {
        SUCCESS,
        ERROR,
        INFO
    }

    public static void show(Window owner, String message, Type type) {
        if (owner == null) return;
        SwingUtilities.invokeLater(() -> {
            Toast toast = new Toast(owner, message, type);
            toast.display();
        });
    }

    public static void success(Window owner, String message) {
        show(owner, message, Type.SUCCESS);
    }

    public static void error(Window owner, String message) {
        show(owner, message, Type.ERROR);
    }

    public static void info(Window owner, String message) {
        show(owner, message, Type.INFO);
    }

    private final Window owner;

    private Toast(Window owner, String message, Type type) {
        super(owner);
        this.owner = owner;

        Color bgColor;
        Color fgColor = Color.WHITE;
        String iconPrefix;

        switch (type) {
            case SUCCESS:
                bgColor = Theme.SUCCESS;
                iconPrefix = "✓  ";
                break;
            case ERROR:
                bgColor = Theme.DANGER;
                iconPrefix = "✕  ";
                break;
            default:
                bgColor = Theme.SIDEBAR_BG;
                iconPrefix = "ℹ  ";
                break;
        }

        JPanel content = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
            }
        };
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));

        JLabel label = new JLabel(iconPrefix + message);
        label.setFont(Theme.FONT_BODY_BOLD);
        label.setForeground(fgColor);
        content.add(label, BorderLayout.CENTER);

        setBackground(new Color(0, 0, 0, 0));
        setContentPane(content);
        pack();
    }

    private void display() {
        if (owner == null || !owner.isShowing()) return;

        Point p = owner.getLocationOnScreen();
        int x = p.x + owner.getWidth() - getWidth() - 32;
        int y = p.y + owner.getHeight() - getHeight() - 40;
        setLocation(Math.max(x, 10), Math.max(y, 10));
        setAlwaysOnTop(true);
        setVisible(true);

        Timer timer = new Timer(2500, e -> {
            setVisible(false);
            dispose();
        });
        timer.setRepeats(false);
        timer.start();
    }
}
