package ui;

import model.Customer;
import model.ReservationDetail;
import model.ReservationSource;
import model.ReservationStatus;
import model.TimeSlot;
import model.WaitlistEntry;
import service.CustomerService;
import service.RecoveryService;
import service.ReservationService;
import service.SettingsService;
import service.TableService;
import service.WaitlistService;
import ui.components.CustomerDetailDialog;
import ui.components.ReconfirmDialog;
import ui.components.Toast;
import ui.components.WaitlistDialog;
import ui.components.WalkInDialog;
import util.DbClock;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import ui.components.AppButton;
import ui.components.ChipRow;
import ui.components.Card;
import ui.components.Dialogs;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service Board — Primary front-of-house operational command center (P3 + P4).
 * Multi-column slot view with live arrival tracking, countdowns for HELD waitlist offers,
 * automated OVERDUE detection, and 1-click Seat / No-Show / Refill actions.
 */
public class ServiceBoardPanel extends BasePanel {
    private static final DateTimeFormatter DATE_NAV_FMT = DateTimeFormatter.ofPattern("EEE, MMM d");
    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("hh:mm:ss a");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    private final ReservationService reservationService;
    private final RecoveryService recoveryService;
    private final WaitlistService waitlistService;
    private final TableService tableService;
    private final CustomerService customerService;

    private LocalDate selectedDate = DbClock.today();

    // Top Header & Ribbon Controls
    private final JLabel dateNavLabel = new JLabel();
    private final JLabel clockLabel = new JLabel();
    private final JButton walkInBtn;
    private final JButton waitlistBtn;
    private final JButton reconfirmBtn;
    private final JButton refreshBtn;

    // KPI Counters
    private final JLabel seatedCountLabel = new JLabel("0");
    private final JLabel confirmedCountLabel = new JLabel("0");
    private final JLabel overdueCountLabel = new JLabel("0");
    private final JLabel offersCountLabel = new JLabel("0");
    private final JLabel recoveredCoversLabel = new JLabel("0");

    // Columns Container
    private final JPanel columnsContainer = new ColumnsPanel();
    private final JScrollPane boardScrollPane;

    // Live UI Timers
    private final Timer tickerTimer;
    private final List<Runnable> liveLabels = new ArrayList<>();
    private String lastSignature = "";
    private final SettingsService settingsService = new SettingsService();

    // Cache of current data
    private final List<ReservationDetail> currentReservations = new ArrayList<>();

    public ServiceBoardPanel(ReservationService reservationService, RecoveryService recoveryService,
                             WaitlistService waitlistService, TableService tableService,
                             CustomerService customerService) {
        super("Service Board");
        this.reservationService = reservationService;
        this.recoveryService = recoveryService;
        this.waitlistService = waitlistService;
        this.tableService = tableService;
        this.customerService = customerService;

        // Custom Header controls
        headerPanel.removeAll();

        // Left Header: Title + Date Navigator
        JPanel leftHeader = new JPanel();
        leftHeader.setLayout(new BoxLayout(leftHeader, BoxLayout.Y_AXIS));
        leftHeader.setOpaque(false);

        titleLabel.setText("Service Board");
        leftHeader.add(titleLabel);
        leftHeader.add(Box.createVerticalStrut(4));

        JPanel navRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        navRow.setOpaque(false);

        JButton prevBtn = AppButton.secondary("‹").compact().minWidth(36);
        prevBtn.setToolTipText("Previous day");
        prevBtn.addActionListener(e -> navigateDate(-1));
        navRow.add(prevBtn);

        JButton todayBtn = AppButton.secondary("Today").compact();
        todayBtn.addActionListener(e -> {
            selectedDate = DbClock.today();
            updateDateNav();
            loadBoardData();
        });
        navRow.add(todayBtn);

        JButton nextBtn = AppButton.secondary("›").compact().minWidth(36);
        nextBtn.setToolTipText("Next day");
        nextBtn.addActionListener(e -> navigateDate(1));
        navRow.add(nextBtn);

        dateNavLabel.setFont(Theme.FONT_BODY_BOLD);
        dateNavLabel.setForeground(Theme.TEXT_PRIMARY);
        navRow.add(dateNavLabel);

        tightRow(navRow, 6);
        navRow.setAlignmentX(0f);
        titleLabel.setAlignmentX(0f);
        leftHeader.add(navRow);
        headerPanel.add(leftHeader);
        headerPanel.add(Box.createHorizontalGlue());

        // Right Header: Clock + Action Buttons
        clockLabel.setFont(Theme.FONT_BODY_BOLD);
        clockLabel.setForeground(Theme.ACCENT_GOLD);
        clockLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.FIELD_BORDER, 1, true),
                BorderFactory.createEmptyBorder(9, 14, 9, 14)
        ));
        headerPanel.add(clockLabel);
        headerPanel.add(Box.createHorizontalStrut(Theme.SPACE_12));

        walkInBtn = AppButton.primary("+ Walk-In");
        walkInBtn.addActionListener(e -> openWalkInDialog());
        headerPanel.add(walkInBtn);
        headerPanel.add(Box.createHorizontalStrut(Theme.SPACE_8));

        waitlistBtn = AppButton.secondary("Waitlist (0)");
        waitlistBtn.addActionListener(e -> openWaitlistDialog());
        headerPanel.add(waitlistBtn);
        headerPanel.add(Box.createHorizontalStrut(Theme.SPACE_8));

        reconfirmBtn = AppButton.secondary("Reconfirm List");
        reconfirmBtn.addActionListener(e -> openReconfirmDialog());
        headerPanel.add(reconfirmBtn);
        headerPanel.add(Box.createHorizontalStrut(Theme.SPACE_8));

        refreshBtn = AppButton.ghost("↻  Refresh");
        refreshBtn.setToolTipText("Refresh Service Board");
        refreshBtn.addActionListener(e -> loadBoardData());
        headerPanel.add(refreshBtn);

        // Content: Ribbon on top, Columns scroll below
        JPanel centerWrapper = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        centerWrapper.setOpaque(false);

        centerWrapper.add(createKpiRibbon(), BorderLayout.NORTH);

        columnsContainer.setLayout(new GridLayout(1, 0, 14, 0));
        columnsContainer.setOpaque(false);

        boardScrollPane = new JScrollPane(columnsContainer);
        boardScrollPane.setBorder(null);
        boardScrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        boardScrollPane.setOpaque(false);
        boardScrollPane.getViewport().setOpaque(false);
        centerWrapper.add(boardScrollPane, BorderLayout.CENTER);

        contentPanel.add(centerWrapper, BorderLayout.CENTER);

        // Live ticker (updates clock, hold countdowns, overdue times)
        tickerTimer = new Timer(1000, e -> tickUi());
        tickerTimer.start();

        updateDateNav();
        loadBoardData();
    }

    private void navigateDate(int days) {
        lastSignature = "";
        selectedDate = selectedDate.plusDays(days);
        updateDateNav();
        loadBoardData();
    }

    private void updateDateNav() {
        boolean isToday = selectedDate.equals(DbClock.today());
        String dateText = selectedDate.format(DATE_NAV_FMT);
        if (isToday) {
            dateNavLabel.setText(dateText + " (Today)");
        } else {
            dateNavLabel.setText(dateText);
        }
    }

    private JPanel createKpiRibbon() {
        JPanel ribbon = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        ribbon.setOpaque(false);

        ribbon.add(createPillStat("SEATED NOW", seatedCountLabel, Theme.SUCCESS));
        ribbon.add(createPillStat("CONFIRMED", confirmedCountLabel, Theme.mix(Theme.ACCENT_GOLD, Color.BLACK, 0.25)));
        ribbon.add(createPillStat("OVERDUE", overdueCountLabel, Theme.DANGER));
        ribbon.add(createPillStat("HELD OFFERS", offersCountLabel, Theme.mix(Theme.WARNING, Color.BLACK, 0.2)));
        ribbon.add(createPillStat("RECOVERED COVERS", recoveredCoversLabel, Theme.TEXT_SECONDARY));

        tightRow(ribbon, 10);
        return ribbon;
    }

    /** Converts a flow row into a left-flush horizontal box with exact gaps (FlowLayout adds an unwanted leading indent). */
    private static void tightRow(JPanel row, int gap) {
        java.awt.Component[] cs = row.getComponents();
        row.removeAll();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        for (int i = 0; i < cs.length; i++) {
            if (i > 0) row.add(Box.createHorizontalStrut(gap));
            row.add(cs[i]);
        }
        row.add(Box.createHorizontalGlue());
    }

    private JPanel createPillStat(String title, JLabel valueLabel, Color fg) {
        JPanel pill = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.alpha(fg, 22));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.setColor(fg);
                g2.fillOval(12, getHeight() / 2 - 3, 6, 6);
                g2.dispose();
            }
        };
        pill.setOpaque(false);
        pill.setBorder(BorderFactory.createEmptyBorder(8, 22, 8, 12));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(Theme.FONT_SMALL_BOLD.deriveFont(11.5f));
        titleLbl.setForeground(fg);

        valueLabel.setFont(Theme.FONT_BODY_BOLD);
        valueLabel.setForeground(fg);

        pill.add(titleLbl);
        pill.add(valueLabel);
        return pill;
    }

    @Override
    public void onShown() {
        super.onShown();
        selectedDate = DbClock.today();
        updateDateNav();
        loadBoardData();
    }

    public void loadBoardData() {
        refreshBtn.setEnabled(false);

        Ui.async(
                () -> {
                    List<ReservationDetail> resList = reservationService.getReservationsByDate(selectedDate);
                    List<WaitlistEntry> offers = waitlistService.getActiveOffers(selectedDate);
                    int waiting = waitlistService.countWaiting(selectedDate);
                    int recovered = reservationService.getRecoveredCoversCount();
                    List<TimeSlot> slots = reservationService.getTimeSlots();
                    int grace = settingsService.getInt("grace_minutes", 15);
                    return new BoardData(resList, offers, waiting, recovered, slots, grace);
                },
                data -> {
                    refreshBtn.setEnabled(true);
                    currentReservations.clear();
                    currentReservations.addAll(data.reservations());

                    renderBoard(data);
                },
                err -> {
                    refreshBtn.setEnabled(true);
                    Toast.error(this, "Failed to load service board: " + err.getMessage());
                }
        );
    }

    private String signatureOf(BoardData d) {
        StringBuilder sb = new StringBuilder();
        sb.append(selectedDate).append('|').append(d.graceMinutes()).append('|').append(d.waiting()).append('|').append(d.recoveredCovers())
                .append('|').append(DbClock.localTime().withSecond(0).withNano(0)).append('|');
        for (ReservationDetail r : d.reservations()) {
            sb.append(r.getReservationId()).append(':').append(r.getStatus()).append(':').append(r.getTableNumber())
                    .append(':').append(r.getHoldExpiresAt()).append(';');
        }
        sb.append('|');
        for (WaitlistEntry o : d.offers()) sb.append(o.getEntryId()).append(';');
        sb.append('|');
        for (TimeSlot t : d.slots()) sb.append(t.getSlotId()).append(';');
        return sb.toString();
    }

    private void renderBoard(BoardData data) {
        String signature = signatureOf(data);
        if (signature.equals(lastSignature)) {
            return; // nothing changed since the last refresh: keep the board (and the user's scroll position) as is
        }
        lastSignature = signature;

        // remember scroll positions so a background refresh never yanks the board around
        final int hScroll = boardScrollPane.getHorizontalScrollBar().getValue();
        final Map<Integer, Integer> vScroll = new HashMap<>();
        for (java.awt.Component c : columnsContainer.getComponents()) {
            if (c instanceof JPanel colPanel && colPanel.getClientProperty("slotId") instanceof Integer sid) {
                for (java.awt.Component inner : colPanel.getComponents()) {
                    if (inner instanceof JScrollPane sp) vScroll.put(sid, sp.getVerticalScrollBar().getValue());
                }
            }
        }

        columnsContainer.removeAll();
        liveLabels.clear();

        // Tally KPIs
        int seated = 0;
        int confirmed = 0;
        int overdue = 0;
        int graceMinutes = data.graceMinutes();
        LocalTime nowTime = DbClock.localTime();
        boolean isToday = selectedDate.equals(DbClock.today());

        Map<Integer, List<ReservationDetail>> slotMap = new HashMap<>();
        for (TimeSlot s : data.slots()) {
            slotMap.put(s.getSlotId(), new ArrayList<>());
        }

        for (ReservationDetail r : data.reservations()) {
            if (r.getStatus() == ReservationStatus.SEATED) seated++;
            else if (r.getStatus() == ReservationStatus.CONFIRMED) {
                confirmed++;
                if (isToday) {
                    TimeSlot slot = findSlot(data.slots(), r.getSlotLabel());
                    if (slot != null && nowTime.isAfter(slot.getStartTime().plusMinutes(graceMinutes))) {
                        overdue++;
                    }
                }
            }
            if (slotMap.containsKey(findSlotId(data.slots(), r.getSlotLabel()))) {
                slotMap.get(findSlotId(data.slots(), r.getSlotLabel())).add(r);
            }
        }

        seatedCountLabel.setText(String.valueOf(seated));
        confirmedCountLabel.setText(String.valueOf(confirmed));
        overdueCountLabel.setText(String.valueOf(overdue));
        offersCountLabel.setText(String.valueOf(data.offers().size()));
        recoveredCoversLabel.setText(String.valueOf(data.recoveredCovers()));

        waitlistBtn.setText("Waitlist (" + (data.waiting() + data.offers().size()) + ")");

        // Build each column
        for (TimeSlot slot : data.slots()) {
            List<ReservationDetail> bookingsInSlot = slotMap.getOrDefault(slot.getSlotId(), new ArrayList<>());
            columnsContainer.add(createSlotColumn(slot, bookingsInSlot, isToday, nowTime, graceMinutes));
        }

        columnsContainer.revalidate();
        columnsContainer.repaint();

        SwingUtilities.invokeLater(() -> {
            boardScrollPane.getHorizontalScrollBar().setValue(hScroll);
            for (java.awt.Component c : columnsContainer.getComponents()) {
                if (c instanceof JPanel colPanel && colPanel.getClientProperty("slotId") instanceof Integer sid && vScroll.containsKey(sid)) {
                    for (java.awt.Component inner : colPanel.getComponents()) {
                        if (inner instanceof JScrollPane sp) sp.getVerticalScrollBar().setValue(vScroll.get(sid));
                    }
                }
            }
        });
    }

    private JPanel createSlotColumn(TimeSlot slot, List<ReservationDetail> bookings,
                                     boolean isToday, LocalTime nowTime, int graceMinutes) {
        JPanel col = new Card(12, 12);
        col.setLayout(new BorderLayout(0, Theme.SPACE_8));
        col.putClientProperty("slotId", slot.getSlotId());
        col.setPreferredSize(new Dimension(250, 0));

        // Column Header
        JPanel colHeader = new JPanel();
        colHeader.setLayout(new BoxLayout(colHeader, BoxLayout.Y_AXIS));
        colHeader.setOpaque(false);

        String stateStr = "UPCOMING";
        Color stateColor = Theme.mix(Theme.ACCENT_GOLD, Color.BLACK, 0.25);
        if (isToday) {
            if (!nowTime.isBefore(slot.getStartTime()) && nowTime.isBefore(slot.getEndTime())) {
                stateStr = "● ONGOING";
                stateColor = Theme.SUCCESS;
            } else if (nowTime.isAfter(slot.getEndTime())) {
                stateStr = "PAST";
                stateColor = Theme.MAINTENANCE_GREY;
            }
        } else if (selectedDate.isBefore(DbClock.today())) {
            stateStr = "PAST";
            stateColor = Theme.MAINTENANCE_GREY;
        }

        JPanel titleLine = new JPanel(new BorderLayout());
        titleLine.setOpaque(false);

        JLabel slotTitle = new JLabel(slot.getLabel());
        slotTitle.setFont(Theme.FONT_BODY_BOLD);
        slotTitle.setForeground(Theme.TEXT_PRIMARY);

        JLabel stateLabel = new JLabel(stateStr);
        stateLabel.setFont(Theme.FONT_SMALL_BOLD);
        stateLabel.setForeground(stateColor);

        titleLine.add(slotTitle, BorderLayout.WEST);
        titleLine.add(stateLabel, BorderLayout.EAST);
        colHeader.add(titleLine);

        JLabel countLbl = new JLabel(bookings.size() + " Booking(s) · " + 
                slot.getStartTime().format(TIME_FMT) + " - " + slot.getEndTime().format(TIME_FMT));
        countLbl.setFont(Theme.FONT_SMALL);
        countLbl.setForeground(Theme.TEXT_MUTED);
        colHeader.add(countLbl);
        colHeader.add(Box.createVerticalStrut(6));

        for (java.awt.Component c : colHeader.getComponents()) {
            if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(0f);
        }
        col.add(colHeader, BorderLayout.NORTH);

        // Scrollable Cards List
        JPanel cardsList = new JPanel();
        cardsList.setLayout(new BoxLayout(cardsList, BoxLayout.Y_AXIS));
        cardsList.setOpaque(false);

        if (bookings.isEmpty()) {
            JPanel empty = new JPanel(new BorderLayout());
            empty.setOpaque(false);
            empty.setBorder(BorderFactory.createEmptyBorder(40, 10, 40, 10));
            JLabel emptyLbl = new JLabel("<html><div style='text-align:center;'>No bookings<br>for this slot</div></html>", SwingConstants.CENTER);
            emptyLbl.setFont(Theme.FONT_SMALL);
            emptyLbl.setForeground(Theme.TEXT_MUTED);
            empty.add(emptyLbl, BorderLayout.CENTER);
            cardsList.add(empty);
        } else {
            for (ReservationDetail r : bookings) {
                cardsList.add(createReservationCard(r, slot, isToday, nowTime, graceMinutes));
                cardsList.add(Box.createVerticalStrut(Theme.SPACE_8));
            }
        }

        cardsList.add(Box.createVerticalGlue());

        JScrollPane cardScroll = new JScrollPane(cardsList);
        cardScroll.setBorder(null);
        cardScroll.setOpaque(false);
        cardScroll.getViewport().setOpaque(false);
        cardScroll.getVerticalScrollBar().setUnitIncrement(14);
        col.add(cardScroll, BorderLayout.CENTER);

        return col;
    }

    private JPanel createReservationCard(ReservationDetail r, TimeSlot slot,
                                          boolean isToday, LocalTime nowTime, int graceMinutes) {
        boolean isConfirmed = r.getStatus() == ReservationStatus.CONFIRMED;
        boolean isOverdue = isConfirmed && isToday && nowTime.isAfter(slot.getStartTime().plusMinutes(graceMinutes));
        boolean isHeld = r.getStatus() == ReservationStatus.HELD;
        boolean isSeated = r.getStatus() == ReservationStatus.SEATED;

        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);

                // Border coloring
                if (isOverdue) {
                    g2.setColor(Theme.DANGER);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                } else if (isHeld) {
                    g2.setColor(Theme.WARNING);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                } else {
                    g2.setColor(Theme.CARD_BORDER);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                }

                // Seated green indicator strip
                if (isSeated) {
                    g2.setColor(Theme.SUCCESS);
                    g2.fillRoundRect(0, 4, 4, getHeight() - 8, 4, 4);
                }
                g2.dispose();
            }
        };

        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 10));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));

        // Top line: Table & Guests
        JPanel line1 = new JPanel(new BorderLayout());
        line1.setOpaque(false);

        JLabel tableLabel = new JLabel("Table " + r.getTableNumber());
        tableLabel.setFont(Theme.FONT_BODY_BOLD);
        tableLabel.setForeground(Theme.TEXT_PRIMARY);

        JLabel partyLabel = new JLabel(r.getGuestCount() + (r.getGuestCount() == 1 ? " guest" : " guests"));
        partyLabel.setFont(Theme.FONT_SMALL_BOLD);
        partyLabel.setForeground(Theme.ACCENT_GOLD);

        line1.add(tableLabel, BorderLayout.WEST);
        line1.add(partyLabel, BorderLayout.EAST);
        card.add(line1);

        // Guest Name & Phone
        JLabel guestName = new JLabel(r.getCustomerName());
        guestName.setFont(Theme.FONT_BODY);
        guestName.setForeground(Theme.TEXT_PRIMARY);
        card.add(guestName);

        JLabel phoneLbl = new JLabel(r.getPhone());
        phoneLbl.setFont(Theme.FONT_SMALL);
        phoneLbl.setForeground(Theme.TEXT_MUTED);
        card.add(phoneLbl);

        // Source or Notes Pill if any
        if (r.getSource() == ReservationSource.WALK_IN || r.getSource() == ReservationSource.WAITLIST) {
            JLabel srcLbl = new JLabel("[" + r.getSource().name() + "]");
            srcLbl.setFont(Theme.FONT_SMALL_BOLD);
            srcLbl.setForeground(Theme.mix(Theme.ACCENT_GOLD, Color.BLACK, 0.25));
            card.add(srcLbl);
        }

        card.add(Box.createVerticalStrut(4));

        // Status & Alerts
        JPanel statusLine = new JPanel(new BorderLayout());
        statusLine.setOpaque(false);

        if (isOverdue) {
            long lateMins = Duration.between(slot.getStartTime().plusMinutes(graceMinutes), nowTime).toMinutes() + graceMinutes;
            JLabel overdueTag = new JLabel("OVERDUE (" + lateMins + "m late)");
            overdueTag.setFont(Theme.FONT_SMALL_BOLD);
            overdueTag.setForeground(Theme.DANGER);
            statusLine.add(overdueTag, BorderLayout.WEST);
            final LocalTime slotStart = slot.getStartTime();
            liveLabels.add(() -> overdueTag.setText("OVERDUE (" + Math.max(0, Duration.between(slotStart, DbClock.localTime()).toMinutes()) + "m late)"));
        } else if (isHeld) {
            JLabel heldTimer = new JLabel(formatHeldCountdown(r.getHoldExpiresAt()));
            heldTimer.setFont(Theme.FONT_SMALL_BOLD);
            heldTimer.setForeground(Theme.mix(Theme.WARNING, Color.BLACK, 0.2));
            statusLine.add(heldTimer, BorderLayout.WEST);
            final LocalDateTime expiresAt = r.getHoldExpiresAt();
            liveLabels.add(() -> heldTimer.setText(formatHeldCountdown(expiresAt)));
        } else if (isSeated) {
            JLabel seatedTag = new JLabel(r.getSeatedAt() != null ? "Seated at " + r.getSeatedAt().format(TIME_FMT) : "Seated");
            seatedTag.setFont(Theme.FONT_SMALL_BOLD);
            seatedTag.setForeground(Theme.SUCCESS);
            statusLine.add(seatedTag, BorderLayout.WEST);
        } else {
            JLabel stLbl = new JLabel(r.getStatus().name());
            stLbl.setFont(Theme.FONT_SMALL_BOLD);
            stLbl.setForeground(Theme.TEXT_MUTED);
            statusLine.add(stLbl, BorderLayout.WEST);
        }

        card.add(statusLine);
        card.add(Box.createVerticalStrut(6));

        // Quick Action Buttons
        JPanel actionRow = new ChipRow(6, true);
        actionRow.setOpaque(false);

        if (isConfirmed) {
            JButton seatBtn = AppButton.success("Seat").compact();
            seatBtn.addActionListener(e -> seatReservation(r));
            actionRow.add(seatBtn);

            JButton noShowBtn = AppButton.danger("No-Show").compact();
            noShowBtn.addActionListener(e -> markNoShow(r));
            actionRow.add(noShowBtn);

            JButton cancelBtn = AppButton.ghost("Cancel").compact();
            cancelBtn.addActionListener(e -> cancelReservation(r));
            actionRow.add(cancelBtn);
        } else if (isSeated) {
            JButton completeBtn = new AppButton("Complete", AppButton.Variant.DARK).compact();
            completeBtn.addActionListener(e -> completeReservation(r));
            actionRow.add(completeBtn);
        } else if (isHeld) {
            JButton acceptBtn = AppButton.primary("Accept").compact();
            acceptBtn.addActionListener(e -> openWaitlistDialog());
            actionRow.add(acceptBtn);
        }

        JButton profileBtn = AppButton.secondary("👤 Profile").compact();
        profileBtn.setToolTipText("Open Guest 360° Profile & Notes");
        profileBtn.addActionListener(e -> openCustomerProfile(r));
        actionRow.add(profileBtn);

        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openCustomerProfile(r);
                }
            }
        });

        card.add(actionRow);
        for (java.awt.Component c : card.getComponents()) {
            if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(0f);
        }
        return card;
    }

    private void openCustomerProfile(ReservationDetail r) {
        if (r == null) return;
        Ui.async(
                () -> {
                    List<Customer> found = customerService.searchCustomers(r.getPhone());
                    if (!found.isEmpty()) return found.get(0);
                    return customerService.searchCustomers(r.getCustomerName()).stream().findFirst().orElse(null);
                },
                cust -> {
                    if (cust != null) {
                        CustomerDetailDialog.show(
                                SwingUtilities.getWindowAncestor(this),
                                cust,
                                customerService,
                                reservationService,
                                updated -> loadBoardData(),
                                bookedCust -> {
                                    MainFrame mf = (MainFrame) SwingUtilities.getAncestorOfClass(MainFrame.class, this);
                                    if (mf != null) mf.showScreen("NEW_RESERVATION");
                                }
                        );
                    } else {
                        Toast.info(SwingUtilities.getWindowAncestor(this), "Customer profile not found for " + r.getCustomerName());
                    }
                },
                err -> Toast.error(SwingUtilities.getWindowAncestor(this), err.getMessage())
        );
    }

    private String formatHeldCountdown(LocalDateTime expiresAt) {
        if (expiresAt == null) return "Hold active";
        LocalDateTime now = DbClock.localDateTime();
        if (now.isAfter(expiresAt)) return "Hold expired";
        Duration d = Duration.between(now, expiresAt);
        return String.format("Hold  %02d:%02d left", d.toMinutes(), d.toSecondsPart());
    }

    private void seatReservation(ReservationDetail r) {
        Ui.async(
                () -> {
                    reservationService.seat(r.getReservationId());
                    return null;
                },
                res -> {
                    Toast.success(this, "Seated " + r.getCustomerName() + " on Table " + r.getTableNumber());
                    loadBoardData();
                },
                err -> Toast.error(this, err.getMessage())
        );
    }

    private void markNoShow(ReservationDetail r) {
        int confirm = Dialogs.showConfirmDialog(
                this,
                "Release Table " + r.getTableNumber() + " and mark " + r.getCustomerName() + " as NO_SHOW?\n" +
                "The table will immediately be offered to waiting waitlist guests.",
                "Confirm No-Show Release",
                Dialogs.YES_NO_OPTION,
                Dialogs.WARNING_MESSAGE
        );

        if (confirm == Dialogs.YES_OPTION) {
            Ui.async(
                    () -> {
                        reservationService.markNoShow(r.getReservationId());
                        return null;
                    },
                    res -> {
                        Toast.success(this, "Marked as NO_SHOW. Slot recovery triggered.");
                        loadBoardData();
                    },
                    err -> Toast.error(this, err.getMessage())
            );
        }
    }

    private void completeReservation(ReservationDetail r) {
        Ui.async(
                () -> {
                    reservationService.complete(r.getReservationId());
                    return null;
                },
                res -> {
                    Toast.success(this, "Completed dining service for Table " + r.getTableNumber());
                    loadBoardData();
                },
                err -> Toast.error(this, err.getMessage())
        );
    }

    private void cancelReservation(ReservationDetail r) {
        int confirm = Dialogs.showConfirmDialog(
                this,
                "Cancel reservation " + r.getDisplayCode() + " for " + r.getCustomerName() + "?\n" +
                "The slot will immediately be refilled from waitlist if candidates exist.",
                "Confirm Cancellation",
                Dialogs.YES_NO_OPTION,
                Dialogs.WARNING_MESSAGE
        );

        if (confirm == Dialogs.YES_OPTION) {
            Ui.async(
                    () -> {
                        reservationService.cancelReservation(r.getReservationId());
                        return null;
                    },
                    res -> {
                        Toast.success(this, "Cancelled. Slot freed and refilled.");
                        loadBoardData();
                    },
                    err -> Toast.error(this, err.getMessage())
            );
        }
    }

    private void openWalkInDialog() {
        Frame topFrame = (Frame) SwingUtilities.getWindowAncestor(this);
        WalkInDialog dlg = new WalkInDialog(topFrame, reservationService, customerService, tableService, this::loadBoardData);
        dlg.setVisible(true);
    }

    private void openWaitlistDialog() {
        Frame topFrame = (Frame) SwingUtilities.getWindowAncestor(this);
        WaitlistDialog dlg = new WaitlistDialog(topFrame, waitlistService, recoveryService, reservationService, customerService, this::loadBoardData);
        dlg.setVisible(true);
    }

    private void openReconfirmDialog() {
        Frame topFrame = (Frame) SwingUtilities.getWindowAncestor(this);
        ReconfirmDialog dlg = new ReconfirmDialog(topFrame, reservationService, this::loadBoardData);
        dlg.setVisible(true);
    }

    private void tickUi() {
        clockLabel.setText(DbClock.localTime().format(CLOCK_FMT));
        if (!isShowing()) return;
        for (Runnable r : liveLabels) {
            r.run();
        }
    }

    private TimeSlot findSlot(List<TimeSlot> slots, String label) {
        if (label == null) return null;
        for (TimeSlot s : slots) {
            if (label.equalsIgnoreCase(s.getLabel()) || label.contains(s.getStartTime().format(TIME_FMT))) {
                return s;
            }
        }
        return null;
    }

    private int findSlotId(List<TimeSlot> slots, String label) {
        TimeSlot s = findSlot(slots, label);
        return s != null ? s.getSlotId() : -1;
    }







    private record BoardData(List<ReservationDetail> reservations, List<WaitlistEntry> offers,
                             int waiting, int recoveredCovers, List<TimeSlot> slots, int graceMinutes) {}

    /** Column strip that fills the viewport when there are few slots and scrolls sideways when there are many. */
    private static class ColumnsPanel extends JPanel implements javax.swing.Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) {
            return o == SwingConstants.HORIZONTAL ? r.width - 40 : r.height - 40;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getParent() != null && getParent().getWidth() >= getPreferredSize().width;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return true;
        }
    }
}
