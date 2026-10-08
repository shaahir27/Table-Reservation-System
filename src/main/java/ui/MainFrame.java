package ui;

import db.DatabaseConnection;
import exception.DatabaseUnavailableException;
import util.AppLog;
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
 * Features startup database health checks, friendly connection failure screen with retry,
 * and a responsive master-detail layout containing the Sidebar and CardLayout views.
 */
public class MainFrame extends JFrame {
    private static final String ROOT_SPLASH = "SPLASH";
    private static final String ROOT_ERROR = "DB_ERROR";
    private static final String ROOT_APP = "APP_SHELL";

    private final CardLayout rootLayout = new CardLayout();
    private final JPanel rootContainer = new JPanel(rootLayout);

    // App Shell Components
    private Sidebar sidebar;
    private final CardLayout contentLayout = new CardLayout();
    private final JPanel contentContainer = new JPanel(contentLayout);

    // Panels
    private DashboardPanel dashboardPanel;
    private NewReservationPanel newReservationPanel;
    private ReservationsPanel reservationsPanel;
    private CustomerPanel customerPanel;
    private TablePanel tablePanel;
    private ReportsPanel reportsPanel;

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
    }

    private void initViews() {
        rootContainer.add(createSplashPanel(), ROOT_SPLASH);
        rootContainer.add(createDbErrorPanel(), ROOT_ERROR);
        rootContainer.add(createAppShellPanel(), ROOT_APP);

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

        JLabel hintLabel = new JLabel("<html><div style='text-align:center;'>Hint: Check <b>config.properties</b> and ensure Oracle XE is running on your machine.</div></html>", SwingConstants.CENTER);
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

    private JPanel createAppShellPanel() {
        JPanel shell = new JPanel(new BorderLayout());
        sidebar = new Sidebar(this::showScreen);
        shell.add(sidebar, BorderLayout.WEST);

        // Screens
        dashboardPanel = new DashboardPanel(this::showScreen);
        newReservationPanel = new NewReservationPanel();
        reservationsPanel = new ReservationsPanel();
        customerPanel = new CustomerPanel();
        tablePanel = new TablePanel();
        reportsPanel = new ReportsPanel();

        contentContainer.add(dashboardPanel, "DASHBOARD");
        contentContainer.add(newReservationPanel, "NEW_RESERVATION");
        contentContainer.add(reservationsPanel, "RESERVATIONS");
        contentContainer.add(customerPanel, "CUSTOMERS");
        contentContainer.add(tablePanel, "TABLES");
        contentContainer.add(reportsPanel, "REPORTS");

        shell.add(contentContainer, BorderLayout.CENTER);
        return shell;
    }

    public void showScreen(String screenKey) {
        contentLayout.show(contentContainer, screenKey);
        sidebar.setSelectedScreen(screenKey);
    }

    public void checkDatabaseConnection() {
        showSplash();
        retryButton.setEnabled(false);
        retryButton.setText("Connecting...");

        Ui.async(
                () -> {
                    DatabaseConnection.testConnection();
                    util.DbClock.syncWithDatabase();
                    return true;
                },
                success -> {
                    AppLog.info("Startup database verification passed.");
                    rootLayout.show(rootContainer, ROOT_APP);
                    showScreen("DASHBOARD");
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

    public void showSplash() {
        rootLayout.show(rootContainer, ROOT_SPLASH);
    }

    // Accessors for panel updating in later phases
    public void setCustomerPanel(CustomerPanel panel) {
        this.customerPanel = panel;
        contentContainer.add(panel, "CUSTOMERS");
    }

    public void setTablePanel(TablePanel panel) {
        this.tablePanel = panel;
        contentContainer.add(panel, "TABLES");
    }
}
