package ui;

import model.AppUser;
import ui.anim.Anim;
import ui.components.AppButton;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dark navigation rail: brand, primary CTA, grouped icon navigation with a sliding gold active
 * indicator, live badge counts, and a user card with account actions.
 */
public class Sidebar extends JPanel {
    public static final int WIDTH = 236;

    private final Consumer<String> onNavigate;
    private final List<NavItem> navItems = new ArrayList<>();
    private final NavList navList = new NavList();
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

        setPreferredSize(new Dimension(WIDTH, 760));
        setMinimumSize(new Dimension(WIDTH, 0));
        setBackground(Theme.SIDEBAR_BG);
        setLayout(new BorderLayout());

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(26, 18, 8, 18));

        top.add(createBrand());
        top.add(Box.createVerticalStrut(24));

        AppButton newResBtn = AppButton.primary("+  New Reservation");
        newResBtn.fixedWidth(WIDTH - 36);
        newResBtn.setAlignmentX(0f);
        newResBtn.addActionListener(e -> navigateTo("NEW_RESERVATION"));
        top.add(newResBtn);
        top.add(Box.createVerticalStrut(22));

        boolean isManager = currentUser == null || currentUser.getRole() == security.Role.MANAGER;

        navList.setLayout(new BoxLayout(navList, BoxLayout.Y_AXIS));
        navList.setOpaque(false);
        navList.setAlignmentX(0f);

        addSection("OPERATIONS");
        if (isManager) {
            addNavItem("Dashboard", "DASHBOARD");
            addNavItem("Service Board", "SERVICE_BOARD");
        } else {
            addNavItem("Service Board", "SERVICE_BOARD");
            addNavItem("Dashboard", "DASHBOARD");
        }
        addNavItem("Floor Plan", "TABLES");
        addNavItem("Reservations", "RESERVATIONS");
        addNavItem("Customers", "CUSTOMERS");

        if (isManager) {
            navList.add(Box.createVerticalStrut(14));
            addSection("MANAGEMENT");
            addNavItem("Reports", "REPORTS");
            addNavItem("Staff", "STAFF");
            addNavItem("Audit Log", "AUDIT");
            addNavItem("Settings", "SETTINGS");
        }
        top.add(navList);
        add(top, BorderLayout.NORTH);
        add(createUserFooterPanel(), BorderLayout.SOUTH);

        setSelectedScreen(isManager ? "DASHBOARD" : "SERVICE_BOARD");
    }

    private JComponent createBrand() {
        JPanel brand = new JPanel(new BorderLayout(12, 0));
        brand.setOpaque(false);
        brand.setAlignmentX(0f);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        JComponent mark = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new java.awt.GradientPaint(0, 0, Theme.mix(Theme.ACCENT_GOLD, Color.WHITE, 0.18f), 0, getHeight(), Theme.ACCENT_GOLD_HOVER));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(Color.WHITE);
                int cx = getWidth() / 2, cy = getHeight() / 2;
                java.awt.Polygon d = new java.awt.Polygon(new int[]{cx, cx + 8, cx, cx - 8}, new int[]{cy - 9, cy, cy + 9, cy}, 4);
                g2.fillPolygon(d);
                g2.dispose();
            }
        };
        mark.setPreferredSize(new Dimension(40, 40));
        brand.add(mark, BorderLayout.WEST);

        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Reserve");
        title.setFont(Theme.FONT_TITLE.deriveFont(21f));
        title.setForeground(Color.WHITE);
        JLabel sub = new JLabel("Restaurant Table System");
        sub.setFont(Theme.FONT_SMALL);
        sub.setForeground(Theme.SIDEBAR_MUTED);
        names.add(title);
        names.add(sub);
        brand.add(names, BorderLayout.CENTER);
        return brand;
    }

    private void addSection(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_SMALL_BOLD.deriveFont(10.5f));
        l.setForeground(Theme.SIDEBAR_MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 0));
        l.setAlignmentX(0f);
        navList.add(l);
    }

    private void addNavItem(String title, String screenKey) {
        NavItem item = new NavItem(title, screenKey);
        navItems.add(item);
        navList.add(item);
        navList.add(Box.createVerticalStrut(4));
    }

    private JPanel createUserFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout(12, 0)) {
            private final Anim.Value hover = new Anim.Value(0, this::repaint);

            {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover.animateTo(1, 140);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover.animateTo(0, 180);
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.mix(Theme.SIDEBAR_RAISED, Theme.SIDEBAR_BORDER, hover.get()));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
            }
        };
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        footer.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        String initials = currentUser != null ? currentUser.getInitials() : "U";
        String name = currentUser != null ? currentUser.getFullName() : "User";
        String role = currentUser != null && currentUser.getRole() != null ? currentUser.getRole().name() : "STAFF";
        boolean mgr = "MANAGER".equalsIgnoreCase(role);

        JComponent avatar = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(mgr ? Theme.ACCENT_GOLD : Theme.mix(Theme.SIDEBAR_RAISED, Theme.SIDEBAR_TEXT, 0.28));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(Theme.FONT_SMALL_BOLD);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(initials, (getWidth() - fm.stringWidth(initials)) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(36, 36));
        footer.add(avatar, BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        JLabel nameLabel = new JLabel(name.length() > 16 ? name.substring(0, 14) + "…" : name);
        nameLabel.setFont(Theme.FONT_BODY_BOLD);
        nameLabel.setForeground(Color.WHITE);
        JLabel roleLabel = new JLabel(role.charAt(0) + role.substring(1).toLowerCase());
        roleLabel.setFont(Theme.FONT_SMALL);
        roleLabel.setForeground(mgr ? Theme.ACCENT_GOLD : Theme.SIDEBAR_TEXT);
        textPanel.add(Box.createVerticalGlue());
        textPanel.add(nameLabel);
        textPanel.add(roleLabel);
        textPanel.add(Box.createVerticalGlue());
        footer.add(textPanel, BorderLayout.CENTER);

        JComponent chevron = new JComponent() {
            {
                setPreferredSize(new Dimension(20, 20));
            }

            @Override
            protected void paintComponent(Graphics g) {
                Icons.paint((Graphics2D) g, "chevron", 0, 0, 20, Theme.SIDEBAR_MUTED);
            }
        };
        footer.add(chevron, BorderLayout.EAST);

        JPopupMenu menu = new JPopupMenu();
        if (onChangePassword != null) {
            JMenuItem changePwdItem = new JMenuItem("Change Password…");
            changePwdItem.addActionListener(e -> onChangePassword.run());
            menu.add(changePwdItem);
        }
        if (onLockScreen != null) {
            JMenuItem lockItem = new JMenuItem("Lock Screen");
            lockItem.addActionListener(e -> onLockScreen.run());
            menu.add(lockItem);
        }
        if (onSignOut != null) {
            menu.addSeparator();
            JMenuItem signOutItem = new JMenuItem("Sign Out");
            signOutItem.setForeground(Theme.DANGER);
            signOutItem.addActionListener(e -> onSignOut.run());
            menu.add(signOutItem);
        }
        footer.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (menu.getComponentCount() > 0) {
                    menu.show(footer, 0, -menu.getPreferredSize().height - 8);
                }
            }
        });

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(8, 14, 16, 14));
        wrap.add(footer, BorderLayout.CENTER);
        return wrap;
    }

    public void setSelectedScreen(String screenKey) {
        this.currentScreen = screenKey;
        for (NavItem item : navItems) {
            item.setActive(item.screenKey.equals(screenKey));
        }
        navList.moveIndicatorTo(navItems.stream().filter(i -> i.screenKey.equals(screenKey)).findFirst().orElse(null));
    }

    public void setBadge(String screenKey, int count) {
        for (NavItem item : navItems) {
            if (item.screenKey.equals(screenKey)) {
                item.setBadge(count);
            }
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

    /** Container that paints the sliding highlight pill + gold bar behind the nav rows. */
    private static class NavList extends JPanel {
        private final Anim.Value y = new Anim.Value(-100, this::repaint);
        private final Anim.Value alpha = new Anim.Value(0, this::repaint);
        private NavItem target;
        private boolean placed;

        void moveIndicatorTo(NavItem item) {
            this.target = item;
            if (item == null) return;
            if (item.getHeight() == 0) { // not laid out yet; doLayout() will place it
                return;
            }
            if (!placed) {
                y.set(item.getY());
                alpha.set(1);
                placed = true;
            } else {
                y.animateTo(item.getY(), 140, Anim.EASE_OUT);
            }
        }

        @Override
        public void doLayout() {
            super.doLayout();
            if (target != null && target.getHeight() > 0) {
                if (!placed) {
                    y.set(target.getY());
                    alpha.set(1);
                    placed = true;
                } else if (Math.abs(y.get() - target.getY()) > 0.5 && !Anim.isEnabled()) {
                    y.set(target.getY());
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (target == null || alpha.get() <= 0) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = target.getHeight();
            int yy = (int) Math.round(y.get());
            g2.setColor(new Color(255, 255, 255, 22));
            g2.fillRoundRect(0, yy, getWidth(), h, 12, 12);
            g2.setColor(Theme.ACCENT_GOLD);
            g2.fillRoundRect(0, yy + 9, 4, h - 18, 4, 4);
            g2.dispose();
        }
    }

    /** One navigation row: icon + label + optional badge. Hover fades, Enter/Space activates. */
    private class NavItem extends JPanel {
        private final String screenKey;
        private final String title;
        private boolean active;
        private int badge;
        private final Anim.Value hover = new Anim.Value(0, this::repaint);
        private final Anim.Value badgePop = new Anim.Value(1, this::repaint);

        NavItem(String title, String screenKey) {
            this.title = title;
            this.screenKey = screenKey;
            setOpaque(false);
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            Dimension d = new Dimension(WIDTH - 36, 42);
            setPreferredSize(d);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            setMinimumSize(d);
            setAlignmentX(0f);
            getAccessibleContext().setAccessibleName(title);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover.animateTo(1, 130);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover.animateTo(0, 180);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    navigateTo(screenKey);
                }
            });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "go");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "go");
            getActionMap().put("go", new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    navigateTo(screenKey);
                }
            });
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusGained(java.awt.event.FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    repaint();
                }
            });
        }

        void setBadge(int count) {
            boolean changed = count != badge;
            badge = Math.max(0, count);
            if (changed && badge > 0) {
                badgePop.set(0.6);
                badgePop.animateTo(1, 320, Anim.EASE_OUT_BACK);
            }
            repaint();
        }

        void setActive(boolean on) {
            this.active = on;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            double hv = hover.get();
            if (!active && hv > 0.01) {
                g2.setColor(new Color(255, 255, 255, (int) (14 * hv)));
                g2.fillRoundRect(0, 0, w, h, 12, 12);
            }
            if (isFocusOwner()) {
                g2.setColor(Theme.alpha(Theme.ACCENT_GOLD, 140));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
            }

            Color fg = active ? Color.WHITE : Theme.mix(Theme.SIDEBAR_TEXT, Color.WHITE, hv * 0.75);
            Icons.paint(g2, screenKey, 16, (h - 20) / 2, 20, active ? Theme.ACCENT_GOLD : Theme.mix(Theme.SIDEBAR_MUTED, Color.WHITE, hv * 0.7));
            g2.setFont(active ? Theme.FONT_BODY_BOLD : Theme.FONT_BODY);
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(fg);
            g2.drawString(title, 48, (h - fm.getHeight()) / 2 + fm.getAscent());

            if (badge > 0) {
                String t = badge > 99 ? "99+" : String.valueOf(badge);
                g2.setFont(Theme.FONT_SMALL_BOLD);
                FontMetrics bf = g2.getFontMetrics();
                int bw = Math.max(22, bf.stringWidth(t) + 14);
                int bh = 20;
                double sc = badgePop.get();
                int cx = w - 14 - bw / 2;
                int cy = h / 2;
                g2.translate(cx, cy);
                g2.scale(sc, sc);
                g2.setColor(Theme.ACCENT_GOLD);
                g2.fillRoundRect(-bw / 2, -bh / 2, bw, bh, bh, bh);
                g2.setColor(Color.WHITE);
                g2.drawString(t, -bf.stringWidth(t) / 2, (bf.getAscent() - bf.getDescent()) / 2);
            }
            g2.dispose();
        }
    }
}
