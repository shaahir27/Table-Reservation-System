package ui;

import model.AppUser;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modern dark sidebar (220px) featuring brand header, prominent New Reservation button,
 * and navigation tabs with gold active indicator bars.
 */
public class Sidebar extends JPanel {
    private final Consumer<String> onNavigate;
    private final List<NavItemPanel> navItems = new ArrayList<>();
    private String currentScreen = "DASHBOARD";

    private final AppUser currentUser;
    private final Runnable onChangePassword;
    private final Runnable onLockScreen;
    private final Runnable onSignOut;

    public Sidebar(Consumer<String> onNavigate) {
        this(security.Session.getInstance().currentUser(), onNavigate, null, null, null);
    }

    public Sidebar(AppUser currentUser, Consumer<String> onNavigate, 
                   Runnable onChangePassword, Runnable onLockScreen, Runnable onSignOut) {
        this.currentUser = currentUser;
        this.onNavigate = onNavigate;
        this.onChangePassword = onChangePassword;
        this.onLockScreen = onLockScreen;
        this.onSignOut = onSignOut;

        setPreferredSize(new Dimension(220, 760));
        setBackground(Theme.SIDEBAR_BG);
        setLayout(new BorderLayout());

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);
        container.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_24, Theme.SPACE_16, Theme.SPACE_16, Theme.SPACE_16));

        // Brand Logo / Title
        JLabel brand = new JLabel("Reserve");
        brand.setFont(Theme.FONT_TITLE);
        brand.setForeground(Theme.ACCENT_GOLD);
        brand.setAlignmentX(0.0f);
        container.add(brand);

        JLabel subtitle = new JLabel("Restaurant Table System");
        subtitle.setFont(Theme.FONT_SMALL);
        subtitle.setForeground(Theme.SIDEBAR_TEXT);
        subtitle.setAlignmentX(0.0f);
        container.add(subtitle);

        container.add(Box.createVerticalStrut(Theme.SPACE_24));

        // Emphasized "+ New Reservation" Gold Button
        JButton newResBtn = new JButton("+ New Reservation") {
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
        newResBtn.setFont(Theme.FONT_BODY_BOLD);
        newResBtn.setForeground(Color.WHITE);
        newResBtn.setContentAreaFilled(false);
        newResBtn.setBorderPainted(false);
        newResBtn.setFocusPainted(false);
        newResBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newResBtn.setMaximumSize(new Dimension(188, 42));
        newResBtn.setPreferredSize(new Dimension(188, 42));
        newResBtn.setAlignmentX(0.0f);
        newResBtn.addActionListener(e -> navigateTo("NEW_RESERVATION"));
        container.add(newResBtn);

        container.add(Box.createVerticalStrut(Theme.SPACE_24));

        // Navigation Items - Filtered by Role
        boolean isManager = currentUser == null || currentUser.getRole() == security.Role.MANAGER;

        addNavItem(container, "Dashboard", "DASHBOARD");
        addNavItem(container, "Reservations", "RESERVATIONS");
        addNavItem(container, "Customers", "CUSTOMERS");

        if (isManager) {
            addNavItem(container, "Tables", "TABLES");
            addNavItem(container, "Reports", "REPORTS");
            addNavItem(container, "Staff", "STAFF");
            addNavItem(container, "Audit Log", "AUDIT");
        }

        add(container, BorderLayout.NORTH);

        // Footer User Profile Card
        add(createUserFooterPanel(), BorderLayout.SOUTH);

        // Default selection
        setSelectedScreen("DASHBOARD");
    }

    private JPanel createUserFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.CARD_BORDER),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));
        footer.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        String initials = currentUser != null ? currentUser.getInitials() : "U";
        String name = currentUser != null ? currentUser.getFullName() : "User";
        String role = currentUser != null && currentUser.getRole() != null ? currentUser.getRole().name() : "STAFF";

        // Avatar
        JPanel avatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor("MANAGER".equalsIgnoreCase(role) ? Theme.ACCENT_GOLD : new Color(100, 116, 139));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(Theme.FONT_SMALL_BOLD);
                var fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(initials)) / 2;
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(initials, x, y);
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(34, 34));
        avatar.setOpaque(false);
        footer.add(avatar, BorderLayout.WEST);

        // Name & Role
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        JLabel nameLabel = new JLabel(name.length() > 14 ? name.substring(0, 12) + "…" : name);
        nameLabel.setFont(Theme.FONT_BODY_BOLD);
        nameLabel.setForeground(Theme.TEXT_PRIMARY);

        JLabel roleLabel = new JLabel(role);
        roleLabel.setFont(Theme.FONT_SMALL);
        roleLabel.setForeground("MANAGER".equalsIgnoreCase(role) ? Theme.ACCENT_GOLD : Theme.TEXT_MUTED);

        textPanel.add(nameLabel);
        textPanel.add(roleLabel);
        footer.add(textPanel, BorderLayout.CENTER);

        // Popup Menu on Click
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        if (onChangePassword != null) {
            javax.swing.JMenuItem changePwdItem = new javax.swing.JMenuItem("Change Password...");
            changePwdItem.addActionListener(e -> onChangePassword.run());
            menu.add(changePwdItem);
        }
        if (onLockScreen != null) {
            javax.swing.JMenuItem lockItem = new javax.swing.JMenuItem("Lock Screen");
            lockItem.addActionListener(e -> onLockScreen.run());
            menu.add(lockItem);
        }
        if (onSignOut != null) {
            menu.addSeparator();
            javax.swing.JMenuItem signOutItem = new javax.swing.JMenuItem("Sign Out");
            signOutItem.setForeground(Theme.DANGER);
            signOutItem.addActionListener(e -> onSignOut.run());
            menu.add(signOutItem);
        }

        footer.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                menu.show(footer, 0, -menu.getPreferredSize().height - 5);
            }
        });

        return footer;
    }

    private void addNavItem(JPanel container, String title, String screenKey) {
        NavItemPanel item = new NavItemPanel(title, screenKey);
        navItems.add(item);
        container.add(item);
        container.add(Box.createVerticalStrut(Theme.SPACE_8));
    }

    public void setSelectedScreen(String screenKey) {
        this.currentScreen = screenKey;
        for (NavItemPanel item : navItems) {
            item.setActive(item.screenKey.equals(screenKey));
        }
    }

    public String getCurrentScreen() {
        return currentScreen;
    }

    private void navigateTo(String screenKey) {
        setSelectedScreen(screenKey);
        if (onNavigate != null) {
            onNavigate.accept(screenKey);
        }
    }

    /**
     * Navigation row item with 4px left gold active indicator.
     */
    private class NavItemPanel extends JPanel {
        private final String screenKey;
        private final JLabel label;
        private boolean isActive = false;
        private boolean isHover = false;

        public NavItemPanel(String title, String screenKey) {
            this.screenKey = screenKey;
            setLayout(new BorderLayout());
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMaximumSize(new Dimension(188, 40));
            setPreferredSize(new Dimension(188, 40));
            setAlignmentX(0.0f);

            label = new JLabel("  " + title);
            label.setFont(Theme.FONT_BODY);
            label.setForeground(Theme.SIDEBAR_TEXT);
            label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
            add(label, BorderLayout.CENTER);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    isHover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    navigateTo(screenKey);
                }
            });
        }

        public void setActive(boolean active) {
            this.isActive = active;
            label.setForeground(active ? Theme.SIDEBAR_ACTIVE_TEXT : Theme.SIDEBAR_TEXT);
            label.setFont(active ? Theme.FONT_BODY_BOLD : Theme.FONT_BODY);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (isActive) {
                // Background subtle highlight
                g2.setColor(new Color(255, 255, 255, 18));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                // 4px Gold indicator bar
                g2.setColor(Theme.ACCENT_GOLD);
                g2.fillRoundRect(0, 4, 4, getHeight() - 8, 4, 4);
            } else if (isHover) {
                g2.setColor(new Color(255, 255, 255, 10));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            }

            g2.dispose();
        }
    }
}
