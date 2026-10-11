package ui.components;

import model.ReconfirmStatus;
import model.ReservationDetail;
import model.ReservationStatus;
import service.ReservationService;
import ui.Theme;
import util.DbClock;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Frame;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Reconfirmation Call List (P3 Lifecycle & Prevention).
 * Allows hosts to review upcoming reservations by phone and update reconfirmation status with 1-click.
 */
public class ReconfirmDialog extends JDialog {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEE, MMM d");

    private final ReservationService reservationService;
    private final Runnable onChange;

    private LocalDate targetDate;
    private final JLabel headerTitle = new JLabel("Reconfirmation Call List");
    private final JLabel statsLabel = new JLabel("Loading...");
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final List<ReservationDetail> currentList = new ArrayList<>();

    private final JButton confirmBtn = AppButton.success("✓  Confirmed");
    private final JButton noAnswerBtn = AppButton.secondary("?  No Answer");
    private final JButton cancelBtn = AppButton.danger("✕  Cancel Booking");

    public ReconfirmDialog(Frame parent, ReservationService reservationService, Runnable onChange) {
        super(parent, "Reconfirmation Call List", true);
        this.reservationService = reservationService;
        this.onChange = onChange;
        this.targetDate = DbClock.today().plusDays(1); // Default to tomorrow

        setSize(880, 580);
        setLocationRelativeTo(parent);

        JPanel content = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        content.setBackground(Theme.APP_BG);
        content.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_20, Theme.SPACE_20, Theme.SPACE_20, Theme.SPACE_20));

        // Top Header Panel
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        JPanel leftHeader = new JPanel();
        leftHeader.setLayout(new BoxLayout(leftHeader, BoxLayout.Y_AXIS));
        leftHeader.setOpaque(false);

        headerTitle.setFont(Theme.FONT_TITLE);
        headerTitle.setForeground(Theme.TEXT_PRIMARY);
        statsLabel.setFont(Theme.FONT_BODY);
        statsLabel.setForeground(Theme.TEXT_MUTED);

        leftHeader.add(headerTitle);
        leftHeader.add(Box.createVerticalStrut(4));
        leftHeader.add(statsLabel);
        topPanel.add(leftHeader, BorderLayout.WEST);

        // Date Toggle (Today vs Tomorrow)
        JPanel dateToggle = new ChipRow(6, true);
        dateToggle.setOpaque(false);

        ChipToggle todayBtn = new ChipToggle("Today · " + DbClock.today().format(DATE_FMT), ChipToggle.Style.DARK).minWidth(120);
        ChipToggle tomorrowBtn = new ChipToggle("Tomorrow · " + DbClock.today().plusDays(1).format(DATE_FMT), ChipToggle.Style.DARK).minWidth(120);
        javax.swing.ButtonGroup dayGroup = new javax.swing.ButtonGroup();
        dayGroup.add(todayBtn);
        dayGroup.add(tomorrowBtn);
        tomorrowBtn.setSelected(true);

        tomorrowBtn.addActionListener(e -> {
            targetDate = DbClock.today().plusDays(1);
            loadData();
        });
        todayBtn.addActionListener(e -> {
            targetDate = DbClock.today();
            loadData();
        });

        dateToggle.add(todayBtn);
        dateToggle.add(tomorrowBtn);
        topPanel.add(dateToggle, BorderLayout.EAST);

        content.add(topPanel, BorderLayout.NORTH);

        // Table
        String[] columns = {"ID", "Guest Name", "Phone", "Slot", "Table", "Party", "Status", "Reconfirm"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        table = new StyledTable(tableModel).emptyMessage("No active reservations on this date.");
        table.setRowHeight(42);

        // Style phone column prominently
        DefaultTableCellRenderer phoneRenderer = new DefaultTableCellRenderer();
        phoneRenderer.setFont(Theme.FONT_BODY_BOLD);
        phoneRenderer.setForeground(Theme.mix(Theme.ACCENT_GOLD, Color.BLACK, 0.3));
        table.getColumnModel().getColumn(2).setCellRenderer(phoneRenderer);

        table.getColumnModel().getColumn(6).setCellRenderer(new StatusPillRenderer());
        table.getColumnModel().getColumn(7).setCellRenderer(new StatusPillRenderer());

        table.getSelectionModel().addListSelectionListener(e -> updateButtonStates());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        scrollPane.getViewport().setBackground(Theme.CARD_BG);
        content.add(scrollPane, BorderLayout.CENTER);

        // Bottom Action Bar
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);

        JLabel hint = new JLabel("Select a reservation to update its reconfirmation outcome");
        hint.setFont(Theme.FONT_SMALL);
        hint.setForeground(Theme.TEXT_MUTED);
        bottomBar.add(hint, BorderLayout.WEST);

        JPanel actionGroup = new ChipRow(8, true);
        actionGroup.setOpaque(false);

        confirmBtn.setEnabled(false);
        confirmBtn.addActionListener(e -> onUpdateStatus(ReconfirmStatus.CONFIRMED));
        actionGroup.add(confirmBtn);

        noAnswerBtn.setEnabled(false);
        noAnswerBtn.addActionListener(e -> onUpdateStatus(ReconfirmStatus.NO_ANSWER));
        actionGroup.add(noAnswerBtn);

        cancelBtn.setEnabled(false);
        cancelBtn.addActionListener(e -> onCancelReservation());
        actionGroup.add(cancelBtn);

        JButton closeBtn = AppButton.secondary("Close");
        closeBtn.addActionListener(e -> dispose());
        actionGroup.add(closeBtn);

        bottomBar.add(actionGroup, BorderLayout.EAST);
        content.add(bottomBar, BorderLayout.SOUTH);

        setContentPane(content);
        loadData();
    }

    private void loadData() {
        headerTitle.setText("Reconfirmation Call List — " + targetDate.format(DATE_FMT));
        statsLabel.setText("Loading reservations...");

        Ui.async(
                () -> reservationService.getReservationsByDate(targetDate),
                list -> {
                    currentList.clear();
                    tableModel.setRowCount(0);

                    int pendingCount = 0;
                    int confirmedCount = 0;
                    int noAnswerCount = 0;

                    for (ReservationDetail r : list) {
                        if (r.getStatus() == ReservationStatus.CONFIRMED || r.getStatus() == ReservationStatus.HELD) {
                            currentList.add(r);
                            ReconfirmStatus rs = r.getReconfirmStatus() != null ? r.getReconfirmStatus() : ReconfirmStatus.PENDING;
                            if (rs == ReconfirmStatus.CONFIRMED) confirmedCount++;
                            else if (rs == ReconfirmStatus.NO_ANSWER) noAnswerCount++;
                            else pendingCount++;

                            tableModel.addRow(new Object[]{
                                    r.getDisplayCode(),
                                    r.getCustomerName(),
                                    r.getPhone(),
                                    r.getSlotLabel(),
                                    r.getTableNumber(),
                                    r.getGuestCount() + " guests",
                                    r.getStatus().name(),
                                    rs.name()
                            });
                        }
                    }

                    statsLabel.setText("Total: " + currentList.size() + " · Confirmed: " + confirmedCount +
                            " · Pending: " + pendingCount + " · No Answer: " + noAnswerCount);
                    updateButtonStates();
                },
                err -> statsLabel.setText("Error loading call list: " + err.getMessage())
        );
    }

    private void updateButtonStates() {
        int row = table.getSelectedRow();
        boolean hasRow = row >= 0 && row < currentList.size();
        confirmBtn.setEnabled(hasRow);
        noAnswerBtn.setEnabled(hasRow);
        cancelBtn.setEnabled(hasRow);
    }

    private void onUpdateStatus(ReconfirmStatus status) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= currentList.size()) return;

        ReservationDetail r = currentList.get(row);
        Ui.async(
                () -> {
                    reservationService.updateReconfirmStatus(r.getReservationId(), status);
                    return null;
                },
                res -> {
                    Toast.success(this, "Updated " + r.getCustomerName() + " to " + status.name());
                    loadData();
                    if (onChange != null) onChange.run();
                },
                err -> Toast.error(this, err.getMessage())
        );
    }

    private void onCancelReservation() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= currentList.size()) return;

        ReservationDetail r = currentList.get(row);
        int confirm = Dialogs.showConfirmDialog(
                this,
                "Cancel reservation " + r.getDisplayCode() + " for " + r.getCustomerName() + "?\n" +
                "The freed slot will immediately be offered to waiting guests.",
                "Cancel & Refill Slot",
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
                        Toast.success(this, "Reservation cancelled. Table freed and slot refilled.");
                        loadData();
                        if (onChange != null) onChange.run();
                    },
                    err -> Toast.error(this, err.getMessage())
            );
        }
    }


}
