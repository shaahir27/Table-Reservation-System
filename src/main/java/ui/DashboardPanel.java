package ui;

import model.DashboardStats;
import model.ReservationDetail;
import service.ReservationService;
import ui.components.StatCard;
import ui.components.StatusPillRenderer;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dashboard View (Section 10.2).
 * Displays 4 key metric StatCards, quick "+ New Reservation" CTA,
 * and the live feed of upcoming reservations.
 */
public class DashboardPanel extends BasePanel {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final ReservationService reservationService;
    private final Consumer<String> screenNavigator;

    // Stat Cards
    private final StatCard todayCard;
    private final StatCard nextSlotFreeCard;
    private final StatCard upcomingCard;
    private final StatCard cancelledCard;

    // Table
    private final DefaultTableModel tableModel;
    private final JTable reservationsTable;
    private final JLabel emptyStateLabel;
    private final JButton refreshBtn;

    public DashboardPanel() {
        this(null);
    }

    public DashboardPanel(Consumer<String> screenNavigator) {
        this(new ReservationService(), screenNavigator);
    }

    public DashboardPanel(ReservationService reservationService, Consumer<String> screenNavigator) {
        super("Dashboard");
        this.reservationService = reservationService;
        this.screenNavigator = screenNavigator;

        // Initialize StatCards
        todayCard = new StatCard("Today's Reservations", "0", "Active & completed");
        nextSlotFreeCard = new StatCard("Available Tables", "0", "Checking schedule...");
        upcomingCard = new StatCard("Upcoming Bookings", "0", "Future confirmed seatings");
        cancelledCard = new StatCard("Cancelled", "0", "Today and future");

        // Initialize Table
        tableModel = new DefaultTableModel(new String[]{
                "ID", "Customer", "Phone", "Table", "Date", "Time Slot", "Guests", "Status"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        reservationsTable = new JTable(tableModel);
        reservationsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        reservationsTable.setRowHeight(38);
        reservationsTable.getTableHeader().setPreferredSize(new Dimension(0, 40));
        reservationsTable.getTableHeader().setFont(Theme.FONT_BODY_BOLD);
        reservationsTable.setFont(Theme.FONT_BODY);

        // Renderers
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        reservationsTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        reservationsTable.getColumnModel().getColumn(0).setMaxWidth(90);
        reservationsTable.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        reservationsTable.getColumnModel().getColumn(3).setMaxWidth(80);
        reservationsTable.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);
        reservationsTable.getColumnModel().getColumn(6).setMaxWidth(70);

        reservationsTable.getColumnModel().getColumn(7).setCellRenderer(new StatusPillRenderer());
        reservationsTable.getColumnModel().getColumn(7).setMinWidth(120);

        emptyStateLabel = new JLabel("No upcoming reservations scheduled.", JLabel.CENTER);
        emptyStateLabel.setFont(Theme.FONT_BODY);
        emptyStateLabel.setForeground(Theme.TEXT_MUTED);

        refreshBtn = createSecondaryButton("Refresh");
        refreshBtn.addActionListener(e -> loadData());

        initLayout();
    }

    @Override
    public void onShown() {
        loadData();
    }

    private void initLayout() {
        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
        mainContent.setOpaque(false);
        mainContent.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.SPACE_16, 0));

        // Action bar top right
        JPanel actionHeader = new JPanel(new BorderLayout());
        actionHeader.setOpaque(false);

        JLabel overviewTitle = new JLabel("Restaurant Overview");
        overviewTitle.setFont(Theme.FONT_SECTION);
        overviewTitle.setForeground(Theme.TEXT_PRIMARY);

        JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_12, 0));
        headerButtons.setOpaque(false);

        JButton newResBtn = createPrimaryButton("+ New Reservation");
        newResBtn.addActionListener(e -> {
            if (screenNavigator != null) {
                screenNavigator.accept("NEW_RESERVATION");
            }
        });

        headerButtons.add(refreshBtn);
        headerButtons.add(newResBtn);

        actionHeader.add(overviewTitle, BorderLayout.WEST);
        actionHeader.add(headerButtons, BorderLayout.EAST);
        mainContent.add(actionHeader);
        mainContent.add(Box.createVerticalStrut(Theme.SPACE_16));

        // 4 StatCards Grid
        JPanel statsGrid = new JPanel(new GridLayout(1, 4, Theme.SPACE_16, 0));
        statsGrid.setOpaque(false);
        statsGrid.add(todayCard);
        statsGrid.add(nextSlotFreeCard);
        statsGrid.add(upcomingCard);
        statsGrid.add(cancelledCard);

        mainContent.add(statsGrid);
        mainContent.add(Box.createVerticalStrut(Theme.SPACE_24));

        // Upcoming Reservations Card
        JPanel feedCard = createCardPanel();
        feedCard.setLayout(new BorderLayout(0, Theme.SPACE_12));

        JPanel feedHeader = new JPanel(new BorderLayout());
        feedHeader.setOpaque(false);
        JLabel feedTitle = new JLabel("Upcoming Scheduled Reservations");
        feedTitle.setFont(Theme.FONT_SECTION);
        feedTitle.setForeground(Theme.TEXT_PRIMARY);

        JLabel feedSub = new JLabel("Next confirmed seatings ordered chronologically");
        feedSub.setFont(Theme.FONT_SMALL);
        feedSub.setForeground(Theme.TEXT_MUTED);

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);
        titleBlock.add(feedTitle);
        titleBlock.add(Box.createVerticalStrut(2));
        titleBlock.add(feedSub);

        feedHeader.add(titleBlock, BorderLayout.WEST);
        feedCard.add(feedHeader, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(reservationsTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        scrollPane.getViewport().setBackground(Theme.CARD_BG);
        feedCard.add(scrollPane, BorderLayout.CENTER);

        mainContent.add(feedCard);

        contentPanel.add(mainContent, BorderLayout.CENTER);
    }

    public void loadData() {
        refreshBtn.setEnabled(false);
        refreshBtn.setText("Updating...");

        Ui.async(
                reservationService::getDashboardStats,
                this::populateDashboard,
                error -> {
                    refreshBtn.setEnabled(true);
                    refreshBtn.setText("Refresh");
                }
        );
    }

    private void populateDashboard(DashboardStats stats) {
        refreshBtn.setEnabled(true);
        refreshBtn.setText("Refresh");

        todayCard.setValue(String.valueOf(stats.getTodayCount()));
        upcomingCard.setValue(String.valueOf(stats.getUpcomingCount()));
        cancelledCard.setValue(String.valueOf(stats.getCancelledCount()));

        nextSlotFreeCard.setValue(String.valueOf(stats.getFreeTablesNextSlot()));
        nextSlotFreeCard.setSubtext(stats.getNextSlotLabel());

        // Populate table
        tableModel.setRowCount(0);
        List<ReservationDetail> list = stats.getUpcoming();
        if (list == null || list.isEmpty()) {
            // Empty
        } else {
            for (ReservationDetail r : list) {
                tableModel.addRow(new Object[]{
                        r.getDisplayCode(),
                        r.getCustomerName(),
                        r.getPhone(),
                        r.getTableNumber(),
                        r.getReservationDate().format(DATE_FMT),
                        r.getSlotLabel(),
                        r.getGuestCount(),
                        r.getStatus()
                });
            }
        }
    }

    private JPanel createCardPanel() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.CARD_BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.setColor(Theme.CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(20, Theme.SPACE_24, 20, Theme.SPACE_24));
        return card;
    }

    private JButton createPrimaryButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? (getModel().isRollover() ? Theme.ACCENT_GOLD_HOVER : Theme.ACCENT_GOLD) : Theme.CARD_BORDER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(Theme.FONT_BODY_BOLD);
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(170, 38));
        return btn;
    }

    private JButton createSecondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(Theme.FONT_BODY);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(100, 38));
        return btn;
    }
}
