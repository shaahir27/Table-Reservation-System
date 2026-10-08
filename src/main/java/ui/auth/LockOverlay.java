package ui.auth;

import com.formdev.flatlaf.FlatClientProperties;
import model.AppUser;
import security.PasswordHasher;
import security.Session;
import ui.Theme;
import util.AppLog;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.util.function.Consumer;

/**
 * Full-screen frosted lock overlay displayed on the window's glass pane when idle or locked.
 * Requires the current user's password to resume; enforces sign out after 3 failed unlock attempts.
 */
public class LockOverlay extends JPanel {
    private final Runnable onUnlocked;
    private final Runnable onForceSignOut;

    private JPasswordField passwordField;
    private JButton unlockButton;
    private JLabel errorLabel;
    private int failedAttempts = 0;

    public LockOverlay(Runnable onUnlocked, Runnable onForceSignOut) {
        this.onUnlocked = onUnlocked;
        this.onForceSignOut = onForceSignOut;

        setLayout(new GridBagLayout());
        setOpaque(false);

        // Trap all mouse clicks from reaching underlying components
        addMouseListener(new MouseAdapter() {});
        addMouseMotionListener(new MouseAdapter() {});

        initUi();
    }

    private void initUi() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setColor(Theme.CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
                g2.dispose();
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(40, 40, 36, 40));
        card.setPreferredSize(new Dimension(380, 420));
        card.setMaximumSize(new Dimension(380, 420));

        // Avatar Circle
        JPanel avatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.ACCENT_GOLD);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(Theme.FONT_TITLE);
                String initials = getInitials();
                var fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(initials)) / 2;
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(initials, x, y);
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(64, 64));
        avatar.setMaximumSize(new Dimension(64, 64));
        avatar.setOpaque(false);
        avatar.setAlignmentX(0.5f);

        JLabel nameLabel = new JLabel(getUserName(), SwingConstants.CENTER);
        nameLabel.setFont(Theme.FONT_TITLE);
        nameLabel.setForeground(Theme.TEXT_PRIMARY);
        nameLabel.setAlignmentX(0.5f);

        JLabel statusLabel = new JLabel("Session Locked", SwingConstants.CENTER);
        statusLabel.setFont(Theme.FONT_BODY);
        statusLabel.setForeground(Theme.TEXT_MUTED);
        statusLabel.setAlignmentX(0.5f);

        passwordField = new JPasswordField();
        passwordField.setFont(Theme.FONT_BODY);
        passwordField.setPreferredSize(new Dimension(300, 42));
        passwordField.setMaximumSize(new Dimension(300, 42));
        passwordField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Enter your password");
        passwordField.setAlignmentX(0.5f);
        passwordField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performUnlock();
                }
            }
        });

        errorLabel = new JLabel("", SwingConstants.CENTER);
        errorLabel.setFont(Theme.FONT_SMALL);
        errorLabel.setForeground(Theme.DANGER);
        errorLabel.setVisible(false);
        errorLabel.setAlignmentX(0.5f);

        unlockButton = new JButton("Unlock") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? Theme.ACCENT_GOLD_HOVER : Theme.ACCENT_GOLD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        unlockButton.setFont(Theme.FONT_BODY_BOLD);
        unlockButton.setForeground(Color.WHITE);
        unlockButton.setContentAreaFilled(false);
        unlockButton.setBorderPainted(false);
        unlockButton.setFocusPainted(false);
        unlockButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        unlockButton.setPreferredSize(new Dimension(300, 42));
        unlockButton.setMaximumSize(new Dimension(300, 42));
        unlockButton.setAlignmentX(0.5f);
        unlockButton.addActionListener(e -> performUnlock());

        JButton signOutBtn = new JButton("Sign out instead");
        signOutBtn.setFont(Theme.FONT_SMALL);
        signOutBtn.setForeground(Theme.TEXT_MUTED);
        signOutBtn.setContentAreaFilled(false);
        signOutBtn.setBorderPainted(false);
        signOutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        signOutBtn.setAlignmentX(0.5f);
        signOutBtn.addActionListener(e -> {
            if (onForceSignOut != null) onForceSignOut.run();
        });

        card.add(avatar);
        card.add(Box.createVerticalStrut(Theme.SPACE_16));
        card.add(nameLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_4));
        card.add(statusLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_24));
        card.add(passwordField);
        card.add(Box.createVerticalStrut(Theme.SPACE_8));
        card.add(errorLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_16));
        card.add(unlockButton);
        card.add(Box.createVerticalStrut(Theme.SPACE_12));
        card.add(signOutBtn);

        add(card);
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Scrim background darkening the entire app
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(15, 23, 42, 215));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }

    public void prepareForDisplay() {
        failedAttempts = 0;
        passwordField.setText("");
        errorLabel.setVisible(false);
        unlockButton.setEnabled(true);
        repaint();
        passwordField.requestFocusInWindow();
    }

    private String getInitials() {
        AppUser u = Session.getInstance().currentUser();
        return u != null ? u.getInitials() : "U";
    }

    private String getUserName() {
        AppUser u = Session.getInstance().currentUser();
        return u != null ? u.getFullName() : "User";
    }

    private void performUnlock() {
        char[] pwd = passwordField.getPassword();
        if (pwd.length == 0) return;

        AppUser user = Session.getInstance().currentUser();
        if (user == null) {
            if (onForceSignOut != null) onForceSignOut.run();
            return;
        }

        unlockButton.setEnabled(false);
        Ui.async(
                () -> PasswordHasher.verify(pwd, user.getPwdHash(), user.getPwdSalt(), user.getPwdIter()),
                valid -> {
                    unlockButton.setEnabled(true);
                    if (valid) {
                        AppLog.info("Screen successfully unlocked by " + user.getUsername());
                        if (onUnlocked != null) {
                            onUnlocked.run();
                        }
                    } else {
                        failedAttempts++;
                        passwordField.setText("");
                        if (failedAttempts >= 3) {
                            AppLog.warning("Too many failed unlock attempts (3) by " + user.getUsername() + "; forcing sign out");
                            if (onForceSignOut != null) {
                                onForceSignOut.run();
                            }
                        } else {
                            errorLabel.setText("Incorrect password (" + (3 - failedAttempts) + " attempts left)");
                            errorLabel.setVisible(true);
                            passwordField.requestFocusInWindow();
                        }
                    }
                },
                error -> {
                    unlockButton.setEnabled(true);
                    errorLabel.setText("Verification failed.");
                    errorLabel.setVisible(true);
                }
        );
    }
}
