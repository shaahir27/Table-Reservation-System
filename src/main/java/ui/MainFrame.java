package ui;

import db.DatabaseConnection;
import exception.DatabaseUnavailableException;
import model.AppUser;
import security.IdleLockMonitor;
import security.Role;
import security.Session;
import service.AuditService;
import service.AuthService;
import service.CustomerService;
import service.RecoveryService;
import service.ReservationService;
import service.SettingsService;
import service.TableService;
import service.UserService;
import service.WaitlistService;
import ui.auth.ChangePasswordDialog;
import ui.components.AppButton;
import ui.components.Card;
import ui.components.LoadingSpinner;
import ui.components.ScreenHost;
import ui.auth.FirstRunSetupPanel;
import ui.auth.LockOverlay;
import ui.auth.LoginPanel;
import util.AppLog;
import util.DbClock;
import util.Ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
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

    private final ScreenHost rootHost = new ScreenHost().slide(0);

    // Backend Services
    private final AuthService authService = new AuthService();
    private final UserService userService = new UserService();
    private final AuditService auditService = new AuditService();
    private final SettingsService settingsService = new SettingsService();
    private final CustomerService customerService = new CustomerService();
    private final TableService tableService = new TableService();
    private final WaitlistService waitlistService = new WaitlistService();
    private final service.notification.UiNotifier uiNotifier = new service.notification.UiNotifier();
    private final RecoveryService recoveryService = new RecoveryService(
            new dao.ReservationDAO(), new dao.WaitlistDAO(), new dao.TableDAO(),
            new dao.SettingDAO(), new dao.AuditDAO(), Session.getInstance(),
            new service.offer.BestFitOfferPolicy(), uiNotifier
    );
    private final ReservationService reservationService = new ReservationService(
            new dao.ReservationDAO(), new dao.TableDAO(), new dao.CustomerDAO(),
            new dao.TimeSlotDAO(), new dao.AuditDAO(), Session.getInstance(), recoveryService
    );

    // Auth Views
    private LoginPanel loginPanel;
    private FirstRunSetupPanel firstRunSetupPanel;
    private LockOverlay lockOverlay;

    // Shell Container & Layout
    private JPanel appShellPanel;
    private Sidebar sidebar;
    private ScreenHost contentContainer;

    // Active View Panels
    private DashboardPanel dashboardPanel;
    private ServiceBoardPanel serviceBoardPanel;
    private NewReservationPanel newReservationPanel;
    private ReservationsPanel reservationsPanel;
    private CustomerPanel customerPanel;
    private TablePanel tablePanel;
    private ReportsPanel reportsPanel;
    private StaffPanel staffPanel;
    private SettingsPanel settingsPanel;
    private AuditPanel auditPanel;

    private javax.swing.Timer sweepTimer;

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
        setContentPane(rootHost);

        // Lock screen glass pane
        lockOverlay = new LockOverlay(this::unlockScreen, this::signOut);
        setGlassPane(lockOverlay);
    }

    private void initViews() {
        loginPanel = new LoginPanel(
                authService,
                this::handleLoginSuccess,
                () -> rootHost.showScreen(ROOT_SETUP)
        );

        firstRunSetupPanel = new FirstRunSetupPanel(
                authService,
                user -> {
                    Session.login(user);
                    handleLoginSuccess(user);
                }
        );

        appShellPanel = new JPanel(new BorderLayout());

        rootHost.addScreen(createSplashPanel(), ROOT_SPLASH);
        rootHost.addScreen(createDbErrorPanel(), ROOT_ERROR);
        rootHost.addScreen(firstRunSetupPanel, ROOT_SETUP);
        rootHost.addScreen(loginPanel, ROOT_LOGIN);
        rootHost.addScreen(appShellPanel, ROOT_APP);

        showSplash();
    }

    private JPanel createSplashPanel() {
        return new JPanel(new java.awt.GridBagLayout()) {
            private final javax.swing.Timer breathe = new javax.swing.Timer(40, e -> repaint());
            private long t0 = System.nanoTime();

            {
                setBackground(Theme.APP_BG);
                JPanel stack = new JPanel();
                stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
                stack.setOpaque(false);

                JLabel title = new JLabel("Reserve", SwingConstants.CENTER);
                title.setFont(Theme.FONT_HERO);
                title.setForeground(Theme.TEXT_PRIMARY);
                title.setAlignmentX(0.5f);

                JLabel tagline = new JLabel("Restaurant Table System", SwingConstants.CENTER);
                tagline.setFont(Theme.FONT_BODY);
                tagline.setForeground(Theme.ACCENT_GOLD);
                tagline.setAlignmentX(0.5f);

                LoadingSpinner spinner = new LoadingSpinner(34);
                spinner.setAlignmentX(0.5f);

                JLabel status = new JLabel("Connecting to database…", SwingConstants.CENTER);
                status.setFont(Theme.FONT_SMALL);
                status.setForeground(Theme.TEXT_MUTED);
                status.setAlignmentX(0.5f);

                stack.add(title);
                stack.add(Box.createVerticalStrut(4));
                stack.add(tagline);
                stack.add(Box.createVerticalStrut(34));
                stack.add(spinner);
                stack.add(Box.createVerticalStrut(16));
                stack.add(status);
                add(stack);
            }

            @Override
            public void addNotify() {
                super.addNotify();
                breathe.start();
            }

            @Override
            public void removeNotify() {
                breathe.stop();
                super.removeNotify();
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double ph = (System.nanoTime() - t0) / 1_000_000_000.0;
                int cx = getWidth() / 2, cy = getHeight() / 2 - 20;
                for (int i = 0; i < 3; i++) {
                    double k = (ph * 0.35 + i / 3.0) % 1.0;
                    int r = (int) (110 + k * 260);
                    g2.setColor(Theme.alpha(Theme.ACCENT_GOLD, (int) (26 * (1 - k))));
                    g2.fillOval(cx - r, cy - r, r * 2, r * 2);
                }
                g2.dispose();
            }
        };
    }

    private JPanel createDbErrorPanel() {
        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        panel.setBackground(Theme.APP_BG);

        Card card = new Card(Theme.SPACE_32, 40);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel iconLabel = new JLabel() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(64, 64);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.DANGER_TINT);
                g2.fillOval(0, 0, 64, 64);
                g2.setColor(Theme.DANGER);
                g2.setStroke(new java.awt.BasicStroke(3f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g2.drawLine(32, 18, 32, 36);
                g2.fillOval(30, 42, 4, 4);
                g2.dispose();
            }
        };
        iconLabel.setAlignmentX(0.5f);
        iconLabel.setMaximumSize(new Dimension(64, 64));

        JLabel titleLabel = new JLabel("Can't reach the database", SwingConstants.CENTER);
        titleLabel.setFont(Theme.FONT_TITLE);
        titleLabel.setForeground(Theme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(0.5f);

        errorReasonLabel = new JLabel("<html><div style='text-align:center;width:380px'>Could not establish a connection to the Oracle database.</div></html>", SwingConstants.CENTER);
        errorReasonLabel.setFont(Theme.FONT_BODY);
        errorReasonLabel.setForeground(Theme.TEXT_SECONDARY);
        errorReasonLabel.setAlignmentX(0.5f);

        JLabel hintLabel = new JLabel("<html><div style='text-align:center;width:380px'>Check <b>config.properties</b> or <b>.env</b> and make sure Oracle XE is running.</div></html>", SwingConstants.CENTER);
        hintLabel.setFont(Theme.FONT_SMALL);
        hintLabel.setForeground(Theme.TEXT_MUTED);
        hintLabel.setAlignmentX(0.5f);

        retryButton = AppButton.primary("Retry Connection");
        retryButton.setAlignmentX(0.5f);
        retryButton.addActionListener(e -> checkDatabaseConnection());

        card.add(iconLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_20));
        card.add(titleLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_12));
        card.add(errorReasonLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_12));
        card.add(hintLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_24));
        card.add(retryButton);

        panel.add(card);
        return panel;
    }

    public void checkDatabaseConnection() {
        showSplash();
        ((AppButton) retryButton).setLoading(true);
        retryButton.setText("Connecting…");

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
                        rootHost.showScreen(ROOT_LOGIN);
                    } else {
                        rootHost.showScreen(ROOT_SETUP);
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
                    errorReasonLabel.setText("<html><div style='text-align:center;width:380px'>" + friendlyMessage + "</div></html>");
                    ((AppButton) retryButton).setLoading(false);
                    retryButton.setText("Retry Connection");
                    rootHost.showScreen(ROOT_ERROR);
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

        // Start background 30-second Slot Recovery Loop sweep
        startRecoverySweepTimer();

        rootHost.showScreen(ROOT_APP);
        String defaultScreen = (user.getRole() == Role.MANAGER) ? "DASHBOARD" : "SERVICE_BOARD";
        showScreen(defaultScreen);
    }

    private void buildShell(AppUser user) {
        tearDownShell();

        contentContainer = new ScreenHost();

        sidebar = new Sidebar(
                user,
                this::showScreen,
                this::showChangePasswordDialog,
                this::lockScreen,
                this::signOut
        );
        appShellPanel.add(sidebar, BorderLayout.WEST);

        // Core Operational Panels
        dashboardPanel = new DashboardPanel(this::showScreen);
        serviceBoardPanel = new ServiceBoardPanel(reservationService, recoveryService, waitlistService, tableService, customerService);
        tablePanel = new TablePanel();
        newReservationPanel = new NewReservationPanel();
        reservationsPanel = new ReservationsPanel();
        customerPanel = new CustomerPanel();

        newReservationPanel.setScreenNavigator(this::showScreen);

        contentContainer.addScreen(dashboardPanel, "DASHBOARD");
        contentContainer.addScreen(serviceBoardPanel, "SERVICE_BOARD");
        contentContainer.addScreen(tablePanel, "TABLES");
        contentContainer.addScreen(newReservationPanel, "NEW_RESERVATION");
        contentContainer.addScreen(reservationsPanel, "RESERVATIONS");
        contentContainer.addScreen(customerPanel, "CUSTOMERS");

        // Role-Gated Panels (Manager only)
        if (user.getRole() == Role.MANAGER) {
            reportsPanel = new ReportsPanel();
            staffPanel = new StaffPanel(userService);
            auditPanel = new AuditPanel(auditService);
            settingsPanel = new SettingsPanel(settingsService);

            contentContainer.addScreen(reportsPanel, "REPORTS");
            contentContainer.addScreen(staffPanel, "STAFF");
            contentContainer.addScreen(auditPanel, "AUDIT");
            contentContainer.addScreen(settingsPanel, "SETTINGS");
        }

        appShellPanel.add(contentContainer, BorderLayout.CENTER);
        appShellPanel.revalidate();
        appShellPanel.repaint();
    }

    private void startRecoverySweepTimer() {
        if (sweepTimer != null) sweepTimer.stop();
        sweepTimer = new javax.swing.Timer(30000, e -> runRecoverySweep());
        sweepTimer.start();
        runRecoverySweep();
    }

    private void runRecoverySweep() {
        Ui.async(
                () -> {
                    int lapsed = recoveryService.sweep();
                    int waiting = waitlistService.countWaiting(DbClock.today());
                    var offers = waitlistService.getActiveOffers(DbClock.today());
                    return new int[]{lapsed, waiting + offers.size()};
                },
                counts -> {
                    int badgeCount = counts[1];
                    if (sidebar != null) {
                        sidebar.setBadge("SERVICE_BOARD", badgeCount);
                    }
                    if (serviceBoardPanel != null && sidebar != null && "SERVICE_BOARD".equals(sidebar.getCurrentScreen())) {
                        serviceBoardPanel.loadBoardData();
                    }
                },
                err -> AppLog.warning("Background recovery sweep failed: " + err.getMessage())
        );
    }

    private void tearDownShell() {
        if (sweepTimer != null) {
            sweepTimer.stop();
            sweepTimer = null;
        }
        appShellPanel.removeAll();
        contentContainer = null;
        sidebar = null;
        dashboardPanel = null;
        serviceBoardPanel = null;
        newReservationPanel = null;
        reservationsPanel = null;
        customerPanel = null;
        tablePanel = null;
        reportsPanel = null;
        staffPanel = null;
        auditPanel = null;
        settingsPanel = null;
    }

    public void showScreen(String screenKey) {
        if (contentContainer != null) {
            String baseKey = screenKey;
            String param = null;
            if (screenKey != null && screenKey.contains(":")) {
                String[] parts = screenKey.split(":", 2);
                baseKey = parts[0];
                param = parts[1];
            }
            contentContainer.showScreen(baseKey);
            if (sidebar != null) {
                sidebar.setSelectedScreen(baseKey);
            }
            if ("RESERVATIONS".equals(baseKey) && reservationsPanel != null && param != null) {
                reservationsPanel.applyPresetFilter(param);
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
        rootHost.showScreen(ROOT_LOGIN);
    }

    public void showChangePasswordDialog() {
        AppUser current = Session.getInstance().currentUser();
        if (current != null) {
            ChangePasswordDialog dlg = new ChangePasswordDialog(this, authService, current, false);
            dlg.setVisible(true);
        }
    }

    public void showSplash() {
        rootHost.showScreen(ROOT_SPLASH);
    }

    public NewReservationPanel getNewReservationPanel() {
        return newReservationPanel;
    }
}
