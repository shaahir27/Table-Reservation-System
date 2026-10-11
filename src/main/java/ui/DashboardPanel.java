package ui;

import model.DashboardStats;
import model.ReservationDetail;
import model.ReservationStatus;
import service.ReservationService;
import ui.components.ChipRow;
import ui.components.AppButton;
import ui.components.Card;
import ui.components.Dialogs;
import ui.components.StatCard;
import ui.components.StyledTable;
import ui.components.StatusPillRenderer;
import ui.components.Toast;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dashboard View (Section 10.2).
 * Interactive Command Center: 5 drilldown StatCards, Quick Actions (New Booking, Walk-in Seating),
 * and live upcoming feed with instant 1-click Guest Seating & Details inspection.
 */
public class DashboardPanel extends BasePanel {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final ReservationService reservationService;
    private final Consumer<String> screenNavigator;

    // Stat Cards
    private final StatCard todayCard;
    private final StatCard nextSlotFreeCard;
    private final StatCard upcomingCard;
    private final StatCard recoveredCard;
    private final StatCard cancelledCard;

    // Upcoming feed
    private final DefaultTableModel tableModel;
    private final JTable reservationsTable;
    private final JButton refreshBtn;
    private final JButton seatGuestBtn;
    private final JButton viewDetailsBtn;
    private final JButton openInResBtn;
    private final JLabel feedHintLabel;
    private final List<ReservationDetail> currentUpcoming = new ArrayList<>();

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

        // Initialize StatCards with interactive drilldowns
        todayCard = new StatCard("Today's Bookings", "0", "Active & completed")
                .icon("calendar", Theme.ACCENT_GOLD)
                .onClick(() -> navigate("RESERVATIONS:TODAY"));
        todayCard.setToolTipText("Click to view and manage today's reservations in detail");

        nextSlotFreeCard = new StatCard("Available Tables", "0", "Checking schedule...")
                .icon("tables", Theme.SUCCESS)
                .onClick(() -> navigate("SERVICE_BOARD"));
        nextSlotFreeCard.setToolTipText("Click to view live table floor plan & availability");

        upcomingCard = new StatCard("Upcoming Bookings", "0", "Future confirmed seatings")
                .icon("clock", Theme.SIDEBAR_BG)
                .onClick(() -> navigate("RESERVATIONS:CONFIRMED"));
        upcomingCard.setToolTipText("Click to view future confirmed bookings");

        recoveredCard = new StatCard("Recovered Covers", "0", "Waitlist slot refills")
                .icon("recover", Theme.SUCCESS)
                .onClick(() -> navigate("SERVICE_BOARD"));
        recoveredCard.setToolTipText("Click to view waitlist & slot recovery conversions");

        cancelledCard = new StatCard("Cancelled", "0", "Today and future")
                .icon("cancel", Theme.DANGER)
                .onClick(() -> navigate("RESERVATIONS:CANCELLED"));
        cancelledCard.setToolTipText("Click to view cancelled bookings");

        // Initialize Table
        tableModel = new DefaultTableModel(new String[]{
                "ID", "Customer", "Phone", "Table", "Date", "Time Slot", "Guests", "Status"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        reservationsTable = new StyledTable(tableModel).emptyMessage("No upcoming reservations scheduled.");

        // Renderers
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        reservationsTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        reservationsTable.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        reservationsTable.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);
        reservationsTable.getColumnModel().getColumn(7).setCellRenderer(new StatusPillRenderer());

        refreshBtn = createSecondaryButton("Refresh");
        refreshBtn.addActionListener(e -> loadData());

        // Quick Actions for Upcoming Table
        seatGuestBtn = createPrimaryButton("Seat Guest");
        seatGuestBtn.setEnabled(false);
        seatGuestBtn.setToolTipText("Seat selected arriving guest at their assigned table");
        seatGuestBtn.addActionListener(e -> onSeatSelectedUpcoming());

        viewDetailsBtn = createSecondaryButton("View Details");
        viewDetailsBtn.setEnabled(false);
        viewDetailsBtn.addActionListener(e -> onViewSelectedUpcoming());

        openInResBtn = createSecondaryButton("All Reservations →");
        openInResBtn.addActionListener(e -> navigate("RESERVATIONS"));

        feedHintLabel = new JLabel("💡 Select an upcoming booking above to seat arriving guests or view details.");
        feedHintLabel.setFont(Theme.FONT_SMALL);
        feedHintLabel.setForeground(Theme.TEXT_MUTED);

        reservationsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateTableActionButtons();
            }
        });

        reservationsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onViewSelectedUpcoming();
                }
            }
        });

        initLayout();
    }

    private void navigate(String destination) {
        if (screenNavigator != null) {
            screenNavigator.accept(destination);
        }
    }

    private boolean entrancePlayed;

    @Override
    public void onShown() {
        if (!entrancePlayed) {
            StatCard[] cards = {todayCard, nextSlotFreeCard, upcomingCard, recoveredCard, cancelledCard};
            for (int i = 0; i < cards.length; i++) {
                cards[i].playEntrance(30 * i);
            }
            entrancePlayed = true;
        }
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

        JPanel headerButtons = new ChipRow(Theme.SPACE_12, true);
        headerButtons.setOpaque(false);

        JButton walkInBtn = createSecondaryButton("⚡ Walk-In Seating");
        walkInBtn.setToolTipText("Open Service Board to seat immediate walk-in parties");
        walkInBtn.addActionListener(e -> navigate("SERVICE_BOARD"));

        JButton newResBtn = createPrimaryButton("+ New Reservation");
        newResBtn.addActionListener(e -> navigate("NEW_RESERVATION"));

        headerButtons.add(walkInBtn);
        headerButtons.add(refreshBtn);
        headerButtons.add(newResBtn);

        actionHeader.add(overviewTitle, BorderLayout.WEST);
        actionHeader.add(headerButtons, BorderLayout.EAST);
        actionHeader.setPreferredSize(new Dimension(100, 42));
        actionHeader.setMinimumSize(new Dimension(100, 42));
        mainContent.add(actionHeader);
        mainContent.add(Box.createVerticalStrut(Theme.SPACE_16));

        // 5 StatCards Grid with comfortable responsive gap
        JPanel statsGrid = new JPanel(new GridLayout(1, 5, Theme.SPACE_12, 0));
        statsGrid.setOpaque(false);
        statsGrid.add(todayCard);
        statsGrid.add(nextSlotFreeCard);
        statsGrid.add(upcomingCard);
        statsGrid.add(recoveredCard);
        statsGrid.add(cancelledCard);

        mainContent.add(statsGrid);
        mainContent.add(Box.createVerticalStrut(Theme.SPACE_20));

        // Upcoming Reservations Card
        JPanel feedCard = createCardPanel();
        feedCard.setLayout(new BorderLayout(0, Theme.SPACE_12));

        JPanel feedHeader = new JPanel(new BorderLayout());
        feedHeader.setOpaque(false);
        JLabel feedTitle = new JLabel("Upcoming Scheduled Reservations");
        feedTitle.setFont(Theme.FONT_SECTION);
        feedTitle.setForeground(Theme.TEXT_PRIMARY);

        JLabel feedSub = new JLabel("Next confirmed seatings ordered chronologically · Double-click row for details");
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
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.NEUTRAL_TINT, 1));
        scrollPane.getViewport().setBackground(Theme.CARD_BG);
        feedCard.add(scrollPane, BorderLayout.CENTER);

        // Action Bar below upcoming feed
        JPanel feedFooter = new JPanel(new BorderLayout());
        feedFooter.setOpaque(false);
        feedFooter.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_8, 0, 0, 0));

        feedFooter.add(feedHintLabel, BorderLayout.WEST);

        JPanel actionRow = new ChipRow(Theme.SPACE_8, true);
        actionRow.setOpaque(false);
        actionRow.add(seatGuestBtn);
        actionRow.add(viewDetailsBtn);
        actionRow.add(openInResBtn);
        feedFooter.add(actionRow, BorderLayout.EAST);

        feedCard.add(feedFooter, BorderLayout.SOUTH);

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
        recoveredCard.setValue(String.valueOf(stats.getRecoveredCoversCount()));
        cancelledCard.setValue(String.valueOf(stats.getCancelledCount()));

        nextSlotFreeCard.setValue(String.valueOf(stats.getFreeTablesNextSlot()));
        nextSlotFreeCard.setSubtext(stats.getNextSlotLabel());

        // Populate table
        currentUpcoming.clear();
        tableModel.setRowCount(0);
        List<ReservationDetail> list = stats.getUpcoming();
        if (list != null && !list.isEmpty()) {
            currentUpcoming.addAll(list);
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
        if (reservationsTable instanceof StyledTable st) st.reveal();
        updateTableActionButtons();
    }

    private void updateTableActionButtons() {
        int selected = reservationsTable.getSelectedRow();
        if (selected < 0 || selected >= currentUpcoming.size()) {
            seatGuestBtn.setEnabled(false);
            viewDetailsBtn.setEnabled(false);
            feedHintLabel.setText("💡 Select an upcoming booking above to seat arriving guests or view details.");
            return;
        }

        ReservationDetail r = currentUpcoming.get(selected);
        boolean canSeat = (r.getStatus() == ReservationStatus.CONFIRMED || r.getStatus() == ReservationStatus.HELD);
        seatGuestBtn.setEnabled(canSeat);
        viewDetailsBtn.setEnabled(true);

        if (canSeat) {
            feedHintLabel.setText("Selected " + r.getDisplayCode() + " (" + r.getCustomerName() + ") · Table " + r.getTableNumber() + " · Ready to seat!");
        } else {
            feedHintLabel.setText("Selected " + r.getDisplayCode() + " · " + r.getStatus());
        }
    }

    private void onSeatSelectedUpcoming() {
        int selected = reservationsTable.getSelectedRow();
        if (selected < 0 || selected >= currentUpcoming.size()) return;
        ReservationDetail r = currentUpcoming.get(selected);

        int confirm = Dialogs.showConfirmDialog(
                this,
                String.format("Seat guest %s (%d guests) at Table %s?",
                        r.getCustomerName(), r.getGuestCount(), r.getTableNumber()),
                "Seat Arriving Guest",
                Dialogs.YES_NO_OPTION,
                Dialogs.QUESTION_MESSAGE
        );

        if (confirm == Dialogs.YES_OPTION) {
            seatGuestBtn.setEnabled(false);
            seatGuestBtn.setText("Seating...");
            Ui.async(
                    () -> {
                        reservationService.seat(r.getReservationId());
                        return null;
                    },
                    res -> {
                        seatGuestBtn.setText("Seat Guest");
                        Toast.success(SwingUtilities.getWindowAncestor(this),
                                "✓ Guest " + r.getCustomerName() + " seated at Table " + r.getTableNumber());
                        loadData();
                    },
                    err -> {
                        seatGuestBtn.setText("Seat Guest");
                        Toast.error(SwingUtilities.getWindowAncestor(this), err.getMessage());
                        loadData();
                    }
            );
        }
    }

    private void onViewSelectedUpcoming() {
        int selected = reservationsTable.getSelectedRow();
        if (selected < 0 || selected >= currentUpcoming.size()) return;
        ReservationDetail r = currentUpcoming.get(selected);

        String info = String.format(
                "Booking Code: %s\n" +
                "Customer: %s\n" +
                "Phone: %s\n" +
                "Table: Table %s\n" +
                "Date: %s\n" +
                "Time Slot: %s\n" +
                "Guest Count: %d guests\n" +
                "Current Status: %s\n" +
                "Source: %s\n" +
                "Reconfirmation: %s",
                r.getDisplayCode(),
                r.getCustomerName(),
                r.getPhone(),
                r.getTableNumber(),
                r.getReservationDate().format(DATE_FMT),
                r.getSlotLabel(),
                r.getGuestCount(),
                r.getStatus(),
                r.getSource(),
                r.getReconfirmStatus()
        );

        Dialogs.showMessageDialog(this, info, "Reservation Details - " + r.getDisplayCode(), Dialogs.INFORMATION_MESSAGE);
    }

    private JPanel createCardPanel() {
        return new Card(20, Theme.SPACE_24);
    }

    private JButton createPrimaryButton(String text) {
        return AppButton.primary(text);
    }

    private JButton createSecondaryButton(String text) {
        return AppButton.secondary(text);
    }
}
