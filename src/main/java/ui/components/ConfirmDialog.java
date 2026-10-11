package ui.components;

import ui.Theme;
import ui.anim.Anim;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Themed replacement for JOptionPane. Rounded card, tinted icon badge, animated fade-in,
 * Esc = cancel, Enter = confirm (dangerous actions default to Cancel).
 */
public final class ConfirmDialog extends JDialog {
    public enum Kind { INFO, SUCCESS, WARNING, DANGER, ERROR }

    private boolean confirmed;

    private ConfirmDialog(Window owner, Kind kind, String title, String message, String confirmText, String cancelText) {
        super(owner, ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        setResizable(false);

        Color tone = switch (kind) {
            case SUCCESS -> Theme.SUCCESS;
            case WARNING -> Theme.WARNING;
            case DANGER, ERROR -> Theme.DANGER;
            default -> Theme.ACCENT_GOLD;
        };
        Color tint = switch (kind) {
            case SUCCESS -> Theme.SUCCESS_TINT;
            case WARNING -> Theme.WARNING_TINT;
            case DANGER, ERROR -> Theme.DANGER_TINT;
            default -> Theme.GOLD_TINT;
        };

        JPanel root = new JPanel(new BorderLayout(18, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(Theme.alpha(tone, 90));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(26, 26, 22, 26));

        JComponent badge = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(tint);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(tone);
                g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2, cy = getHeight() / 2;
                switch (kind) {
                    case SUCCESS -> {
                        g2.drawLine(cx - 8, cy + 1, cx - 2, cy + 7);
                        g2.drawLine(cx - 2, cy + 7, cx + 9, cy - 7);
                    }
                    case INFO -> {
                        g2.drawLine(cx, cy - 1, cx, cy + 8);
                        g2.fillOval(cx - 2, cy - 9, 4, 4);
                    }
                    case ERROR -> {
                        g2.drawLine(cx - 7, cy - 7, cx + 7, cy + 7);
                        g2.drawLine(cx + 7, cy - 7, cx - 7, cy + 7);
                    }
                    default -> { // warning / danger: exclamation
                        g2.drawLine(cx, cy - 9, cx, cy + 2);
                        g2.fillOval(cx - 2, cy + 6, 4, 4);
                    }
                }
                g2.dispose();
            }
        };
        badge.setPreferredSize(new Dimension(48, 48));
        JPanel badgeWrap = new JPanel(new BorderLayout());
        badgeWrap.setOpaque(false);
        badgeWrap.add(badge, BorderLayout.NORTH);
        root.add(badgeWrap, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(Theme.FONT_SECTION.deriveFont(17f));
        t.setForeground(Theme.TEXT_PRIMARY);
        t.setAlignmentX(0f);
        String html = "<html><body style='width:330px'>" + escape(message).replace("\n", "<br>") + "</body></html>";
        JLabel m = new JLabel(html);
        m.setFont(Theme.FONT_BODY);
        m.setForeground(Theme.TEXT_SECONDARY);
        m.setAlignmentX(0f);
        text.add(t);
        text.add(Box.createVerticalStrut(8));
        text.add(m);
        root.add(text, BorderLayout.CENTER);

        JPanel buttons = new ChipRow(10, true);
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(22, 0, 0, 0));
        AppButton cancel = cancelText == null ? null : AppButton.secondary(cancelText);
        AppButton ok = switch (kind) {
            case DANGER -> AppButton.danger(confirmText);
            case SUCCESS -> AppButton.success(confirmText);
            default -> AppButton.primary(confirmText);
        };
        if (cancel != null) {
            cancel.addActionListener(e -> close(false));
            buttons.add(cancel);
        }
        ok.addActionListener(e -> close(true));
        buttons.add(ok);
        root.add(buttons, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setSize(Math.max(getWidth(), 460), getHeight());
        try {
            setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 20, 20));
        } catch (Exception ignored) {
            // shaped windows unsupported on this platform; square corners are fine
        }
        setLocationRelativeTo(owner);

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        root.getActionMap().put("cancel", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                close(false);
            }
        });
        getRootPane().setDefaultButton(null);
        final JComponent initialFocus = (kind == Kind.DANGER && cancel != null) ? cancel : ok;
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter");
        root.getActionMap().put("enter", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (initialFocus instanceof AppButton b) b.doClick();
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                initialFocus.requestFocusInWindow();
            }
        });
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void close(boolean result) {
        confirmed = result;
        dispose();
    }

    private boolean showAndWait() {
        try {
            setOpacity(0f);
        } catch (Exception ignored) {
        }
        Anim.run(180, Anim.EASE_OUT, p -> {
            try {
                setOpacity((float) Math.max(0.0, Math.min(1.0, p)));
            } catch (Exception ignored) {
            }
        });
        setVisible(true);
        return confirmed;
    }

    private static Window ownerOf(Component c) {
        if (c == null) return null;
        return c instanceof Window w ? w : SwingUtilities.getWindowAncestor(c);
    }

    /** Yes/No style confirmation. Returns true when the user confirmed. */
    public static boolean confirm(Component parent, String title, String message, String confirmText, boolean danger) {
        return confirm(parent, title, message, confirmText, danger, "Cancel");
    }

    public static boolean confirm(Component parent, String title, String message, String confirmText, boolean danger, String cancelText) {
        return new ConfirmDialog(ownerOf(parent), danger ? Kind.DANGER : Kind.WARNING, title, message, confirmText, cancelText).showAndWait();
    }

    public static boolean ask(Component parent, String title, String message, String confirmText) {
        return new ConfirmDialog(ownerOf(parent), Kind.INFO, title, message, confirmText, "Cancel").showAndWait();
    }

    public static void info(Component parent, String title, String message) {
        new ConfirmDialog(ownerOf(parent), Kind.INFO, title, message, "Got it", null).showAndWait();
    }

    public static void success(Component parent, String title, String message) {
        new ConfirmDialog(ownerOf(parent), Kind.SUCCESS, title, message, "Done", null).showAndWait();
    }

    public static void warning(Component parent, String title, String message) {
        new ConfirmDialog(ownerOf(parent), Kind.WARNING, title, message, "OK", null).showAndWait();
    }

    public static void error(Component parent, String title, String message) {
        new ConfirmDialog(ownerOf(parent), Kind.ERROR, title, message, "Close", null).showAndWait();
    }
}
