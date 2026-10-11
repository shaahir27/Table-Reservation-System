package ui.components;

import com.formdev.flatlaf.FlatClientProperties;
import model.Customer;
import model.TimeSlot;
import model.WaitlistEntry;
import model.WaitlistStatus;
import service.CustomerService;
import service.RecoveryService;
import service.ReservationService;
import service.WaitlistService;
import ui.Theme;
import util.DbClock;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Waitlist Management & Active Offers View (P4 Slot Recovery).
 * Displays live queue, real-time HELD offer countdowns, and race-safe Accept/Decline actions.
 */
public class WaitlistDialog extends JDialog {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    private final WaitlistService waitlistService;
    private final RecoveryService recoveryService;
    private final ReservationService reservationService;
    private final CustomerService customerService;
    private final Runnable onChange;

    private LocalDate targetDate = DbClock.today();
    private final JLabel headerTitle = new JLabel("Waitlist & Slot Recovery Engine");
    private final JLabel statsLabel = new JLabel("Loading queue...");
    private final JPanel offersContainer = new JPanel();
    private final JTable queueTable;
    private final DefaultTableModel tableModel;
    private final List<WaitlistEntry> currentEntries = new ArrayList<>();
    private final List<WaitlistEntry> currentOffers = new ArrayList<>();

    private final JButton cancelEntryBtn = AppButton.danger("Remove from Waitlist");
    private Timer countdownTimer;

    public WaitlistDialog(Frame parent, WaitlistService waitlistService,
                          RecoveryService recoveryService, ReservationService reservationService,
                          CustomerService customerService, Runnable onChange) {
        super(parent, "Waitlist & Slot Recovery Engine", true);
        this.waitlistService = waitlistService;
        this.recoveryService = recoveryService;
        this.reservationService = reservationService;
        this.customerService = customerService;
        this.onChange = onChange;

        setSize(960, 680);
        setLocationRelativeTo(parent);

        JPanel mainPanel = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        mainPanel.setBackground(Theme.APP_BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_20, Theme.SPACE_20, Theme.SPACE_20, Theme.SPACE_20));

        // Top Header
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);

        headerTitle.setFont(Theme.FONT_TITLE);
        headerTitle.setForeground(Theme.TEXT_PRIMARY);
        statsLabel.setFont(Theme.FONT_BODY);
        statsLabel.setForeground(Theme.TEXT_MUTED);

        titlePanel.add(headerTitle);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(statsLabel);
        topBar.add(titlePanel, BorderLayout.WEST);

        JPanel headerActions = new ChipRow(8, true);
        headerActions.setOpaque(false);

        JButton addWaitlistBtn = createPrimaryBtn("+ Join Waitlist");
        addWaitlistBtn.addActionListener(e -> showJoinWaitlistDialog());
        headerActions.add(addWaitlistBtn);

        JButton refreshBtn = AppButton.secondary("Refresh");
        refreshBtn.addActionListener(e -> loadData());
        headerActions.add(refreshBtn);

        topBar.add(headerActions, BorderLayout.EAST);

        // Center Split Panel (Offers Strip on top, Queue Table below)
        JPanel centerPanel = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        centerPanel.setOpaque(false);

        // Offers Strip
        offersContainer.setLayout(new BoxLayout(offersContainer, BoxLayout.Y_AXIS));
        offersContainer.setOpaque(false);
        centerPanel.add(offersContainer, BorderLayout.NORTH);

        // Table
        String[] cols = {"Rank", "Joined At", "Customer", "Phone", "Party Size", "Pref Section", "Target Slot", "Status", "Notes"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        queueTable = new StyledTable(tableModel).emptyMessage("Nobody is waiting for this date.");
        queueTable.setRowHeight(42);
        queueTable.getColumnModel().getColumn(7).setCellRenderer(new StatusPillRenderer());

        queueTable.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

        JScrollPane scroll = new JScrollPane(queueTable);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        scroll.getViewport().setBackground(Theme.CARD_BG);
        centerPanel.add(scroll, BorderLayout.CENTER);

        mainPanel.add(topBar, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Bottom Bar
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);

        cancelEntryBtn.setEnabled(false);
        cancelEntryBtn.addActionListener(e -> onCancelSelectedEntry());
        bottomBar.add(cancelEntryBtn, BorderLayout.WEST);

        JButton closeBtn = AppButton.secondary("Close");
        closeBtn.addActionListener(e -> dispose());
        bottomBar.add(closeBtn, BorderLayout.EAST);

        mainPanel.add(bottomBar, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        // 1-second countdown ticker for active holds
        countdownTimer = new Timer(1000, e -> updateOfferCountdowns());
        countdownTimer.start();

        loadData();
    }

    @Override
    public void dispose() {
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        super.dispose();
    }

    private void loadData() {
        statsLabel.setText("Updating waitlist queue...");

        Ui.async(
                () -> {
                    List<WaitlistEntry> waiting = waitlistService.getWaitingForDate(targetDate);
                    List<WaitlistEntry> offers = waitlistService.getActiveOffers(targetDate);
                    return new DataPair(waiting, offers);
                },
                pair -> {
                    currentEntries.clear();
                    currentEntries.addAll(pair.entries());
                    currentOffers.clear();
                    currentOffers.addAll(pair.offers());

                    renderOffersStrip();
                    renderQueueTable();

                    statsLabel.setText("Queue for " + targetDate + ": " + currentEntries.size() + 
                            " waiting · " + currentOffers.size() + " active hold offers");
                    updateButtonStates();
                },
                err -> statsLabel.setText("Error loading waitlist: " + err.getMessage())
        );
    }

    private void renderOffersStrip() {
        offersContainer.removeAll();
        if (currentOffers.isEmpty()) {
            offersContainer.setVisible(false);
            return;
        }

        offersContainer.setVisible(true);

        JPanel stripCard = new JPanel();
        stripCard.setLayout(new BoxLayout(stripCard, BoxLayout.Y_AXIS));
        stripCard.setBackground(Theme.WARNING_TINT);
        stripCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.alpha(Theme.WARNING, 150), 1, true),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        JLabel title = new JLabel("Active held offers — action required");
        title.setFont(Theme.FONT_SECTION);
        title.setForeground(Theme.mix(Theme.WARNING, Color.BLACK, 0.3));
        stripCard.add(title);
        stripCard.add(Box.createVerticalStrut(8));

        for (WaitlistEntry offer : currentOffers) {
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setOpaque(false);

            String slotTxt = offer.getSlotLabel() != null ? offer.getSlotLabel() : "Any Slot";
            JLabel details = new JLabel("<html><b>" + offer.getCustomerName() + "</b> (" + 
                    offer.getPhone() + ") · Party of " + offer.getPartySize() + " · Slot: " + slotTxt + "</html>");
            details.setFont(Theme.FONT_BODY);

            JLabel countdownLbl = new JLabel(formatCountdown(offer.getCreatedAt()));
            countdownLbl.setFont(Theme.FONT_BODY_BOLD);
            countdownLbl.setForeground(Theme.mix(Theme.WARNING, Color.BLACK, 0.3));
            countdownLbl.putClientProperty("ENTRY", offer);

            JPanel actions = new ChipRow(8, true);
            actions.setOpaque(false);
            actions.add(countdownLbl);

            JButton seatNowBtn = AppButton.success("Seat Guest").compact();
            seatNowBtn.addActionListener(e -> acceptOffer(offer, true));
            actions.add(seatNowBtn);

            JButton confirmBtn = AppButton.secondary("Accept").compact();
            confirmBtn.addActionListener(e -> acceptOffer(offer, false));
            actions.add(confirmBtn);

            JButton declineBtn = AppButton.danger("Decline").compact();
            declineBtn.addActionListener(e -> declineOffer(offer));
            actions.add(declineBtn);

            row.add(details, BorderLayout.WEST);
            row.add(actions, BorderLayout.EAST);

            stripCard.add(row);
            stripCard.add(Box.createVerticalStrut(8));
        }

        offersContainer.add(stripCard);
        offersContainer.revalidate();
        offersContainer.repaint();
    }

    private void renderQueueTable() {
        tableModel.setRowCount(0);
        int rank = 1;
        for (WaitlistEntry e : currentEntries) {
            String timeStr = e.getCreatedAt() != null ? e.getCreatedAt().format(TIME_FMT) : "—";
            String slotStr = e.getSlotLabel() != null ? e.getSlotLabel() : "Any Slot";
            String prefStr = e.getPreferredSection() != null ? e.getPreferredSection() : "Any";
            String noteStr = e.getNotes() != null ? e.getNotes() : "";

            tableModel.addRow(new Object[]{
                    "#" + rank++,
                    timeStr,
                    e.getCustomerName(),
                    e.getPhone(),
                    e.getPartySize() + " guests",
                    prefStr,
                    slotStr,
                    e.getStatus().name(),
                    noteStr
            });
        }
    }

    private void updateOfferCountdowns() {
        // Ticked every second to animate hold countdowns
        if (!currentOffers.isEmpty()) {
            renderOffersStrip();
        }
    }

    private String formatCountdown(LocalDateTime createdAt) {
        if (createdAt == null) return "Hold: Active";
        // Default hold is 10 mins from creation
        LocalDateTime expiresAt = createdAt.plusMinutes(10);
        LocalDateTime now = DbClock.localDateTime();
        if (now.isAfter(expiresAt)) {
            return "Expired";
        }
        Duration d = Duration.between(now, expiresAt);
        long mins = d.toMinutes();
        long secs = d.toSecondsPart();
        return String.format("Hold  %02d:%02d left", mins, secs);
    }

    private void updateButtonStates() {
        int row = queueTable.getSelectedRow();
        boolean hasRow = row >= 0 && row < currentEntries.size();
        if (hasRow) {
            WaitlistEntry e = currentEntries.get(row);
            cancelEntryBtn.setEnabled(e.getStatus() == WaitlistStatus.WAITING);
        } else {
            cancelEntryBtn.setEnabled(false);
        }
    }

    private void acceptOffer(WaitlistEntry offer, boolean seatImmediately) {
        Ui.async(
                () -> {
                    recoveryService.acceptOffer(offer.getEntryId(), seatImmediately);
                    return null;
                },
                res -> {
                    Toast.success(this, "Offer accepted for " + offer.getCustomerName());
                    loadData();
                    if (onChange != null) onChange.run();
                },
                err -> Toast.error(this, err.getMessage())
        );
    }

    private void declineOffer(WaitlistEntry offer) {
        Ui.async(
                () -> {
                    recoveryService.declineOffer(offer.getEntryId());
                    return null;
                },
                res -> {
                    Toast.success(this, "Offer declined. Refilling slot for next candidate.");
                    loadData();
                    if (onChange != null) onChange.run();
                },
                err -> Toast.error(this, err.getMessage())
        );
    }

    private void onCancelSelectedEntry() {
        int row = queueTable.getSelectedRow();
        if (row < 0 || row >= currentEntries.size()) return;

        WaitlistEntry e = currentEntries.get(row);
        int confirm = Dialogs.showConfirmDialog(
                this,
                "Remove " + e.getCustomerName() + " from the waitlist?",
                "Confirm Removal",
                Dialogs.YES_NO_OPTION,
                Dialogs.QUESTION_MESSAGE
        );

        if (confirm == Dialogs.YES_OPTION) {
            Ui.async(
                    () -> {
                        waitlistService.cancelEntry(e.getEntryId());
                        return null;
                    },
                    res -> {
                        Toast.success(this, "Removed from waitlist.");
                        loadData();
                        if (onChange != null) onChange.run();
                    },
                    err -> Toast.error(this, err.getMessage())
            );
        }
    }

    private void showJoinWaitlistDialog() {
        JDialog dlg = new JDialog(this, "Join Waitlist", true);

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Theme.CARD_BG);
        p.setBorder(BorderFactory.createEmptyBorder(26, 30, 24, 30));

        JLabel title = new JLabel("Add guest to waitlist");
        title.setFont(Theme.FONT_SECTION.deriveFont(18f));
        title.setForeground(Theme.TEXT_PRIMARY);
        title.setAlignmentX(0f);
        JLabel sub = new JLabel("For " + targetDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy")));
        sub.setFont(Theme.FONT_SMALL);
        sub.setForeground(Theme.TEXT_MUTED);
        sub.setAlignmentX(0f);
        p.add(title);
        p.add(Box.createVerticalStrut(4));
        p.add(sub);
        p.add(Box.createVerticalStrut(18));

        SearchableComboBox custPicker = new SearchableComboBox();
        custPicker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 98));
        p.add(Form.label("Customer *"));
        p.add(Box.createVerticalStrut(6));
        p.add(custPicker);
        p.add(Box.createVerticalStrut(Theme.SPACE_12));

        Ui.async(
                () -> customerService.getAllCustomers(),
                custPicker::setCustomerList,
                err -> Toast.error(dlg, err.getMessage())
        );

        JSpinner partySpinner = new JSpinner(new SpinnerNumberModel(2, 1, 20, 1));
        p.add(Form.field("Party size *", partySpinner, null));

        JComboBox<String> sectionCombo = new JComboBox<>(new String[]{"Any Section", "Indoor", "Outdoor", "Window", "Hall"});
        p.add(Form.field("Preferred section", sectionCombo, null));

        // slots come from the database: load off the UI thread and keep the list for the save handler
        final List<TimeSlot> slotList = new ArrayList<>();
        JComboBox<String> slotCombo = new JComboBox<>();
        slotCombo.addItem("Any slot today");
        Ui.async(
                () -> reservationService.getTimeSlots(),
                slots -> {
                    slotList.addAll(slots);
                    for (TimeSlot s : slots) slotCombo.addItem(s.getLabel());
                },
                err -> Toast.error(dlg, "Could not load dining slots: " + err.getMessage())
        );
        p.add(Form.field("Target dining slot", slotCombo, null));

        JTextField noteField = new JTextField();
        noteField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Special occasion, highchair, etc.");
        p.add(Form.field("Special notes", noteField, null));
        p.add(Box.createVerticalStrut(6));

        JPanel btnRow = new ChipRow(10, true);
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(0f);
        JButton cancel = AppButton.secondary("Cancel");
        cancel.addActionListener(e -> dlg.dispose());
        btnRow.add(cancel);

        AppButton save = AppButton.primary("Add to Waitlist");
        save.addActionListener(e -> {
            Customer c = custPicker.getSelectedCustomer();
            if (c == null) {
                Toast.error(dlg, "Please select a customer.");
                return;
            }
            int guests = (Integer) partySpinner.getValue();
            String sec = sectionCombo.getSelectedIndex() > 0 ? (String) sectionCombo.getSelectedItem() : null;
            Integer slotId = null;
            int idx = slotCombo.getSelectedIndex();
            if (idx > 0 && idx - 1 < slotList.size()) {
                slotId = slotList.get(idx - 1).getSlotId();
            }

            WaitlistEntry entry = new WaitlistEntry();
            entry.setCustomerId(c.getCustomerId());
            entry.setServiceDate(targetDate);
            entry.setSlotId(slotId);
            entry.setPartySize(guests);
            entry.setPreferredSection(sec);
            entry.setNotes(noteField.getText().trim());

            save.setLoading(true);
            save.setText("Adding…");

            Ui.async(
                    () -> waitlistService.addToWaitlist(entry),
                    res -> {
                        Toast.success(this, c.getName() + " added to waitlist.");
                        dlg.dispose();
                        loadData();
                        if (onChange != null) onChange.run();
                    },
                    err -> {
                        save.setLoading(false);
                        save.setText("Add to Waitlist");
                        Toast.error(dlg, err.getMessage());
                    }
            );
        });
        btnRow.add(save);
        p.add(btnRow);
        dlg.getRootPane().setDefaultButton(save);

        dlg.setContentPane(p);
        dlg.pack();
        dlg.setSize(Math.max(dlg.getWidth(), 480), dlg.getHeight());
        dlg.setResizable(false);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private static JButton createPrimaryBtn(String text) {
        return AppButton.primary(text);
    }

    private record DataPair(List<WaitlistEntry> entries, List<WaitlistEntry> offers) {}
}
