package ui;

import db.DatabaseConnection;
import exception.DatabaseUnavailableException;
import model.AppUser;
import security.IdleLockMonitor;
import security.Role;
import security.Session;
import service.AuditService;
import service.AuthService;
import service.SettingsService;
import service.UserService;
import ui.auth.ChangePasswordDialog;
import ui.auth.FirstRunSetupPanel;
import ui.auth.LockOverlay;
import ui.auth.LoginPanel;
import util.AppLog;
import util.DbClock;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Main application window (min 1200x760).
 * Features startup database health checks, authentication flow with first-run setup,
 * role-based app shell construction, idle-lock screen overlay, and session lifecycle.
 */
public class MainFrame extends JFrame {
    private static final String ROOT_SPLASH = "SPLASH";
    private static final String ROOT_ERROR = "DB_ERROR";
    private static final String ROOT_SETUP = "SETUP";
    private static final String ROOT_LOGIN = "LOGIN";
    private static final String ROOT_APP = "APP_SHELL";

    private final CardLayout rootLayout = new CardLayout();
    private final JPanel rootContainer = new JPanel(rootLayout);

    // Backend Services
    private final AuthService authService = new AuthService();
    private final UserService userService = new UserService();
    private final AuditService auditService = new AuditService();
    private final SettingsService settingsService = new SettingsService();

    // Auth Views
    private LoginPanel loginPanel;
    private FirstRunSetupPanel firstRunSetupPanel;
    private LockOverlay lockOverlay;

    // Shell Container & Layout
    private JPanel appShellPanel;
    private Sidebar sidebar;
    private CardLayout contentLayout;
    private JPanel contentContainer;

    // Active View Panels
    private DashboardPanel dashboardPanel;
    private NewReservationPanel newReservationPanel;
    private ReservationsPanel reservationsPanel;
    private CustomerPanel customerPanel;
    private TablePanel tablePanel;
    private ReportsPanel reportsPanel;
    private StaffPanel staffPanel;
    private AuditPanel auditPanel;

    // DB Error Components
    private JLabel errorReasonLabel;
    private JButton retryButton;

    public MainFrame() {
        super("Reserve — Restaurant Table Reservation System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1200, 760));
        setSize(1280, 800);
        setLocationRelativeTo(null);

        initViews();
        setContentPane(rootContainer);

        // Lock screen glass pane
        lockOverlay = new LockOverlay(this::unlockScreen, this::signOut);
        setGlassPane(lockOverlay);
    }

    private void initViews() {
        loginPanel = new LoginPanel(
                authService,
                this::handleLoginSuccess,
                () -> rootLayout.show(rootContainer, ROOT_SETUP)
        );

        firstRunSetupPanel = new FirstRunSetupPanel(
                authService,
                user -> {
                    Session.login(user);
                    handleLoginSuccess(user);
                }
        );

        appShellPanel = new JPanel(new BorderLayout());

        rootContainer.add(createSplashPanel(), ROOT_SPLASH);
        rootContainer.add(createDbErrorPanel(), ROOT_ERROR);
        rootContainer.add(firstRunSetupPanel, ROOT_SETUP);
        rootContainer.add(loginPanel, ROOT_LOGIN);
        rootContainer.add(appShellPanel, ROOT_APP);

        showSplash();
    }

    private JPanel createSplashPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.APP_BG);

        JPanel centerCard = new JPanel();
        centerCard.setLayout(new BoxLayout(centerCard, BoxLayout.Y_AXIS));
        centerCard.setOpaque(false);
        centerCard.setBorder(BorderFactory.createEmptyBorder(150, 40, 40, 40));

        JLabel title = new JLabel("Reserve", SwingConstants.CENTER);
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.ACCENT_GOLD);
        title.setAlignmentX(0.5f);

        JLabel status = new JLabel("Connecting to database...", SwingConstants.CENTER);
        status.setFont(Theme.FONT_BODY);
        status.setForeground(Theme.TEXT_MUTED);
        status.setAlignmentX(0.5f);

        centerCard.add(title);
        centerCard.add(Box.createVerticalStrut(Theme.SPACE_16));
        centerCard.add(status);

        panel.add(centerCard, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createDbErrorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.APP_BG);

        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(Theme.CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_32, Theme.SPACE_32, Theme.SPACE_32, Theme.SPACE_32));
        card.setMaximumSize(new Dimension(540, 360));
        card.setPreferredSize(new Dimension(540, 360));

        JLabel iconLabel = new JLabel("⚠", SwingConstants.CENTER);
        iconLabel.setFont(Theme.FONT_STAT_NUM);
        iconLabel.setForeground(Theme.DANGER);
        iconLabel.setAlignmentX(0.5f);

        JLabel titleLabel = new JLabel("Can't reach the database", SwingConstants.CENTER);
        titleLabel.setFont(Theme.FONT_TITLE);
        titleLabel.setForeground(Theme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(0.5f);

        errorReasonLabel = new JLabel("<html><div style='text-align:center;'>Could not establish a connection to the Oracle database.</div></html>", SwingConstants.CENTER);
        errorReasonLabel.setFont(Theme.FONT_BODY);
        errorReasonLabel.setForeground(Theme.TEXT_MUTED);
        errorReasonLabel.setAlignmentX(0.5f);

        JLabel hintLabel = new JLabel("<html><div style='text-align:center;'>Hint: Check <b>config.properties</b> or <b>.env</b> and ensure Oracle XE is running.</div></html>", SwingConstants.CENTER);
        hintLabel.setFont(Theme.FONT_SMALL);
        hintLabel.setForeground(Theme.TEXT_MUTED);
        hintLabel.setAlignmentX(0.5f);

        retryButton = new JButton("Retry Connection") {
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
        retryButton.setFont(Theme.FONT_BODY_BOLD);
        retryButton.setForeground(Color.WHITE);
        retryButton.setContentAreaFilled(false);
        retryButton.setBorderPainted(false);
        retryButton.setFocusPainted(false);
        retryButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        retryButton.setMaximumSize(new Dimension(200, 44));
        retryButton.setPreferredSize(new Dimension(200, 44));
        retryButton.setAlignmentX(0.5f);
        retryButton.addActionListener(e -> checkDatabaseConnection());

        card.add(iconLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_12));
        card.add(titleLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_12));
        card.add(errorReasonLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_16));
        card.add(hintLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_24));
        card.add(retryButton);

        JPanel outerWrapper = new JPanel(new BorderLayout());
        outerWrapper.setOpaque(false);
        outerWrapper.setBorder(BorderFactory.createEmptyBorder(120, 20, 20, 20));
        outerWrapper.add(card, BorderLayout.NORTH);

        panel.add(outerWrapper, BorderLayout.CENTER);
        return panel;
    }

    public void checkDatabaseConnection() {
        showSplash();
        retryButton.setEnabled(false);
        retryButton.setText("Connecting...");

        Ui.async(
                () -> {
                    DatabaseConnection.testConnection();
                    DbClock.syncWithDatabase();
                    return authService.hasUsers();
                },
                hasUsers -> {
                    AppLog.info("Startup database verification passed. Users present: " + hasUsers);
                    if (hasUsers) {
                        loginPanel.resetForm();
                        rootLayout.show(rootContainer, ROOT_LOGIN);
                    } else {
                        rootLayout.show(rootContainer, ROOT_SETUP);
                    }
                },
                error -> {
                    AppLog.warning("Startup database verification failed: " + error.getMessage());
                    String friendlyMessage = error.getMessage();
                    if (error instanceof DatabaseUnavailableException && error.getMessage() != null) {
                        friendlyMessage = error.getMessage();
                    } else {
                        friendlyMessage = "Cannot connect to the Oracle database. Ensure the listener and service are running.";
                    }
                    errorReasonLabel.setText("<html><div style='text-align:center;'>" + friendlyMessage + "</div></html>");
                    retryButton.setEnabled(true);
                    retryButton.setText("Retry Connection");
                    rootLayout.show(rootContainer, ROOT_ERROR);
                }
        );
    }

    private void handleLoginSuccess(AppUser user) {
        if (user.isMustChangePwd()) {
            ChangePasswordDialog dlg = new ChangePasswordDialog(this, authService, user, true);
            dlg.setVisible(true);
        }

        buildShell(user);

        // Configure and start idle lock monitor
        int idleMinutes = settingsService.getInt("idle_lock_minutes", 10);
        IdleLockMonitor.getInstance().setIdleMinutes(idleMinutes);
        IdleLockMonitor.getInstance().setLockCallback(this::lockScreen);
        IdleLockMonitor.getInstance().start();

        rootLayout.show(rootContainer, ROOT_APP);
        showScreen("DASHBOARD");
    }

    private void buildShell(AppUser user) {
        tearDownShell();

        contentLayout = new CardLayout();
        contentContainer = new JPanel(contentLayout);

        sidebar = new Sidebar(
                user,
                this::showScreen,
                this::showChangePasswordDialog,
                this::lockScreen,
                this::signOut
        );
        appShellPanel.add(sidebar, BorderLayout.WEST);

        // Standard Panels
        dashboardPanel = new DashboardPanel(this::showScreen);
        newReservationPanel = new NewReservationPanel();
        reservationsPanel = new ReservationsPanel();
        customerPanel = new CustomerPanel();

        contentContainer.add(dashboardPanel, "DASHBOARD");
        contentContainer.add(newReservationPanel, "NEW_RESERVATION");
        contentContainer.add(reservationsPanel, "RESERVATIONS");
        contentContainer.add(customerPanel, "CUSTOMERS");

        // Role-Gated Panels (Manager only)
        if (user.getRole() == Role.MANAGER) {
            tablePanel = new TablePanel();
            reportsPanel = new ReportsPanel();
            staffPanel = new StaffPanel(userService);
            auditPanel = new AuditPanel(auditService);

            contentContainer.add(tablePanel, "TABLES");
            contentContainer.add(reportsPanel, "REPORTS");
            contentContainer.add(staffPanel, "STAFF");
            contentContainer.add(auditPanel, "AUDIT");
        }

        appShellPanel.add(contentContainer, BorderLayout.CENTER);
        appShellPanel.revalidate();
        appShellPanel.repaint();
    }

    private void tearDownShell() {
        appShellPanel.removeAll();
        contentContainer = null;
        sidebar = null;
        dashboardPanel = null;
        newReservationPanel = null;
        reservationsPanel = null;
        customerPanel = null;
        tablePanel = null;
        reportsPanel = null;
        staffPanel = null;
        auditPanel = null;
    }

    public void showScreen(String screenKey) {
        if (contentContainer != null && contentLayout != null) {
            contentLayout.show(contentContainer, screenKey);
            if (sidebar != null) {
                sidebar.setSelectedScreen(screenKey);
            }
        }
    }

    public void lockScreen() {
        if (Session.isLoggedIn()) {
            lockOverlay.prepareForDisplay();
            lockOverlay.setVisible(true);
        }
    }

    public void unlockScreen() {
        lockOverlay.setVisible(false);
        IdleLockMonitor.getInstance().reset();
    }

    public void signOut() {
        IdleLockMonitor.getInstance().stop();
        lockOverlay.setVisible(false);
        tearDownShell();
        authService.logout();
        loginPanel.resetForm();
        rootLayout.show(rootContainer, ROOT_LOGIN);
    }

    public void showChangePasswordDialog() {
        AppUser current = Session.getInstance().currentUser();
        if (current != null) {
            ChangePasswordDialog dlg = new ChangePasswordDialog(this, authService, current, false);
            dlg.setVisible(true);
        }
    }

    public void showSplash() {
        rootLayout.show(rootContainer, ROOT_SPLASH);
    }
}
