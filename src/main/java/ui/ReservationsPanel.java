package ui;

import com.formdev.flatlaf.FlatClientProperties;
import model.ReservationDetail;
import model.ReservationFilter;
import model.ReservationStatus;
import model.RestaurantTable;
import model.TimeSlot;
import service.ReservationService;
import service.TableService;
import ui.components.ChipRow;
import ui.components.AppButton;
import ui.components.Card;
import ui.components.CustomerDetailDialog;
import ui.components.Form;
import ui.components.StatusPillRenderer;
import ui.components.StyledTable;
import ui.components.Toast;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import ui.components.Dialogs;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Reservation Management and Operations Panel (Section 10.4).
 * Supports parameterized multi-criteria searching, status filtering,
 * guest seating, completion, cancellation with index release, and full booking inspection.
 */
public class ReservationsPanel extends BasePanel {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final ReservationService reservationService;
    private final TableService tableService;
    private final service.CustomerService customerService;

    // Filter controls
    private final JTextField searchTextField = new JTextField();
    private final JCheckBox dateFilterCheckBox = new JCheckBox("Date:");
    private final JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
    private final JComboBox<String> statusFilterCombo = new JComboBox<>(new String[]{"ALL", "CONFIRMED", "SEATED", "COMPLETED", "CANCELLED"});
    private final JComboBox<String> tableFilterCombo = new JComboBox<>();
    private final JButton searchBtn;
    private final JButton resetBtn;

    // Table & Results
    private final DefaultTableModel tableModel;
    private final JTable resultsTable;
    private final JLabel countFooterLabel = new JLabel("0 reservations found");
    private final JLabel actionHintLabel = new JLabel("💡 Select a reservation above to manage (Seat Guest, Complete, Cancel, or View Details)");
    private final List<ReservationDetail> currentResults = new ArrayList<>();

    // Actions
    private final JButton seatGuestBtn;
    private final JButton markCompletedBtn;
    private final JButton cancelBtn;
    private final JButton viewDetailsBtn;
    private final JButton viewCustomerBtn;

    public ReservationsPanel() {
        this(new ReservationService(), new TableService(), new service.CustomerService());
    }

    public ReservationsPanel(ReservationService reservationService, TableService tableService) {
        this(reservationService, tableService, new service.CustomerService());
    }

    public ReservationsPanel(ReservationService reservationService, TableService tableService, service.CustomerService customerService) {
        super("Reservations & History");
        this.reservationService = reservationService;
        this.tableService = tableService;
        this.customerService = customerService;

        // Date spinner styling
        JSpinner.DateEditor editor = new JSpinner.DateEditor(dateSpinner, "dd-MM-yyyy");
        dateSpinner.setValue(new Date());
        dateSpinner.setEditor(editor);
        dateSpinner.setEnabled(false);
        dateFilterCheckBox.setFont(Theme.FONT_BODY_BOLD);
        dateFilterCheckBox.setForeground(Theme.TEXT_PRIMARY);
        dateFilterCheckBox.addActionListener(e -> dateSpinner.setEnabled(dateFilterCheckBox.isSelected()));

        searchBtn = createPrimaryButton("Search");
        resetBtn = createSecondaryButton("Reset");

        seatGuestBtn = createPrimaryButton("Seat Guest");
        seatGuestBtn.setEnabled(false);
        seatGuestBtn.setToolTipText("Seat arriving guest at their assigned table");

        markCompletedBtn = createSecondaryButton("Mark Completed");
        markCompletedBtn.setEnabled(false);
        markCompletedBtn.setToolTipText("Mark dining finished and release table");

        cancelBtn = createDangerButton("Cancel Reservation");
        cancelBtn.setEnabled(false);
        cancelBtn.setToolTipText("Cancel reservation and release table slot");

        viewDetailsBtn = createSecondaryButton("View Details");
        viewDetailsBtn.setEnabled(false);
        viewDetailsBtn.setToolTipText("Inspect customer, table, and booking audit details");

        viewCustomerBtn = createSecondaryButton("👤 Guest 360° Profile");
        viewCustomerBtn.setEnabled(false);
        viewCustomerBtn.setToolTipText("Open guest profile, hospitality tags, and full dining history");
        viewCustomerBtn.addActionListener(e -> onViewCustomerSelectedReservation());

        // Setup results table
        tableModel = new DefaultTableModel(new String[]{
                "ID", "Customer", "Phone", "Table", "Date", "Time Slot", "Guests", "Status"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        resultsTable = new StyledTable(tableModel).emptyMessage("No reservations match these filters.");

        // Center alignments
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        resultsTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        resultsTable.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        resultsTable.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);
        resultsTable.getColumnModel().getColumn(7).setCellRenderer(new StatusPillRenderer());

        // Row selection listener to enable/disable action buttons
        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateActionButtonsState();
            }
        });

        resultsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onViewCustomerSelectedReservation();
                }
            }
        });

        setupEvents();
        initLayout();
    }

    @Override
    public void onShown() {
        if (tableFilterCombo.getItemCount() <= 1) {
            loadTableOptions();
        }
        performSearch();
    }

    private void setupEvents() {
        searchBtn.addActionListener(e -> performSearch());
        searchTextField.addActionListener(e -> performSearch());

        resetBtn.addActionListener(e -> applyPresetFilter("ALL"));

        seatGuestBtn.addActionListener(e -> onSeatSelectedReservation());
        markCompletedBtn.addActionListener(e -> onMarkCompletedSelectedReservation());
        cancelBtn.addActionListener(e -> onCancelSelectedReservation());
        viewDetailsBtn.addActionListener(e -> onViewDetailsSelectedReservation());
    }

    private void initLayout() {
        JPanel mainLayout = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        mainLayout.setOpaque(false);

        // 1. Filter Card (2-Row Spacious, Responsive Layout)
        JPanel filterCard = createCardPanel();
        filterCard.setLayout(new BoxLayout(filterCard, BoxLayout.Y_AXIS));

        // Row 1: Search field + Search & Reset actions
        JPanel row1 = new ChipRow(Theme.SPACE_12);
        row1.setOpaque(false);
        row1.setAlignmentX(0.0f);

        Form.input(searchTextField, 400);
        searchTextField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search by ID, customer name, phone, or table...");

        row1.add(searchTextField);
        row1.add(searchBtn);
        row1.add(resetBtn);

        // Row 2: Date filter + Status filter + Table filter with comfortable spacing
        JPanel row2 = new ChipRow(Theme.SPACE_24);
        row2.setOpaque(false);
        row2.setAlignmentX(0.0f);

        JPanel dateGroup = new ChipRow(Theme.SPACE_8);
        dateGroup.setOpaque(false);
        Form.input(dateSpinner, 140);
        dateGroup.add(dateFilterCheckBox);
        dateGroup.add(dateSpinner);

        JPanel statusGroup = new ChipRow(Theme.SPACE_8);
        statusGroup.setOpaque(false);
        JLabel statusLabel = new JLabel("Status:");
        statusLabel.setFont(Theme.FONT_BODY_BOLD);
        statusLabel.setForeground(Theme.TEXT_PRIMARY);
        Form.input(statusFilterCombo, 150);
        statusGroup.add(statusLabel);
        statusGroup.add(statusFilterCombo);

        JPanel tableGroup = new ChipRow(Theme.SPACE_8);
        tableGroup.setOpaque(false);
        JLabel tableLabel = new JLabel("Table:");
        tableLabel.setFont(Theme.FONT_BODY_BOLD);
        tableLabel.setForeground(Theme.TEXT_PRIMARY);
        Form.input(tableFilterCombo, 140);
        tableGroup.add(tableLabel);
        tableGroup.add(tableFilterCombo);

        row2.add(dateGroup);
        row2.add(statusGroup);
        row2.add(tableGroup);

        filterCard.add(row1);
        filterCard.add(Box.createVerticalStrut(Theme.SPACE_12));
        filterCard.add(row2);

        mainLayout.add(filterCard, BorderLayout.NORTH);

        // 2. Results Card
        JPanel resultsCard = createCardPanel();
        resultsCard.setLayout(new BorderLayout(0, Theme.SPACE_12));

        JScrollPane scrollPane = new JScrollPane(resultsTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.NEUTRAL_TINT, 1));
        scrollPane.getViewport().setBackground(Theme.CARD_BG);
        resultsCard.add(scrollPane, BorderLayout.CENTER);

        // 3. Action bar & Footer (Resilient two-tier layout to prevent overlapping)
        JPanel bottomBar = new JPanel(new BorderLayout(0, Theme.SPACE_8));
        bottomBar.setOpaque(false);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_8, 0, 0, 0));
        bottomBar.setPreferredSize(new Dimension(0, 68));
        bottomBar.setMinimumSize(new Dimension(0, 68));

        // Sub-row 1: Count and Selection Status
        JPanel statusRow = new JPanel(new BorderLayout(Theme.SPACE_16, 0));
        statusRow.setOpaque(false);
        statusRow.setPreferredSize(new Dimension(0, 22));

        countFooterLabel.setFont(Theme.FONT_BODY_BOLD);
        countFooterLabel.setForeground(Theme.TEXT_PRIMARY);

        actionHintLabel.setFont(Theme.FONT_SMALL);
        actionHintLabel.setForeground(Theme.TEXT_MUTED);

        statusRow.add(countFooterLabel, BorderLayout.WEST);
        statusRow.add(actionHintLabel, BorderLayout.EAST);
        bottomBar.add(statusRow, BorderLayout.NORTH);

        // Sub-row 2: Action buttons toolbar
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_8, 0));
        buttonBar.setOpaque(false);
        buttonBar.setPreferredSize(new Dimension(0, 36));
        buttonBar.add(seatGuestBtn);
        buttonBar.add(markCompletedBtn);
        buttonBar.add(cancelBtn);
        buttonBar.add(viewDetailsBtn);
        buttonBar.add(viewCustomerBtn);
        bottomBar.add(buttonBar, BorderLayout.SOUTH);

        resultsCard.add(bottomBar, BorderLayout.SOUTH);
        mainLayout.add(resultsCard, BorderLayout.CENTER);

        contentPanel.add(mainLayout, BorderLayout.CENTER);
    }

    private List<TimeSlot> cachedSlots = new ArrayList<>();

    private void loadTableOptions() {
        final Object previous = tableFilterCombo.getSelectedItem();
        Ui.async(
                () -> new Object[]{tableService.getAllTables(), reservationService.getTimeSlots()},
                payload -> {
                    @SuppressWarnings("unchecked")
                    List<RestaurantTable> tables = (List<RestaurantTable>) payload[0];
                    @SuppressWarnings("unchecked")
                    List<TimeSlot> slots = (List<TimeSlot>) payload[1];
                    cachedSlots = slots;
                    tableFilterCombo.removeAllItems();
                    tableFilterCombo.addItem("All Tables");
                    for (RestaurantTable t : tables) {
                        tableFilterCombo.addItem(t.getTableNumber());
                    }
                    if (previous != null) tableFilterCombo.setSelectedItem(previous);
                },
                null
        );
    }

    public void applyPresetFilter(String preset) {
        if ("TODAY".equalsIgnoreCase(preset)) {
            dateFilterCheckBox.setSelected(true);
            dateSpinner.setEnabled(true);
            dateSpinner.setValue(new Date());
            statusFilterCombo.setSelectedItem("ALL");
        } else if ("CONFIRMED".equalsIgnoreCase(preset)) {
            dateFilterCheckBox.setSelected(false);
            dateSpinner.setEnabled(false);
            statusFilterCombo.setSelectedItem("CONFIRMED");
        } else if ("CANCELLED".equalsIgnoreCase(preset)) {
            dateFilterCheckBox.setSelected(false);
            dateSpinner.setEnabled(false);
            statusFilterCombo.setSelectedItem("CANCELLED");
        } else if ("SEATED".equalsIgnoreCase(preset)) {
            dateFilterCheckBox.setSelected(false);
            dateSpinner.setEnabled(false);
            statusFilterCombo.setSelectedItem("SEATED");
        } else {
            dateFilterCheckBox.setSelected(false);
            dateSpinner.setEnabled(false);
            dateSpinner.setValue(new Date());
            statusFilterCombo.setSelectedItem("ALL");
        }
        searchTextField.setText("");
        if (tableFilterCombo.getItemCount() > 0) tableFilterCombo.setSelectedIndex(0);
        performSearch();
    }

    private void performSearch() {
        searchBtn.setEnabled(false);
        searchBtn.setText("Searching...");

        ReservationFilter filter = new ReservationFilter();

        String query = searchTextField.getText().trim();
        if (!query.isEmpty()) {
            filter.setSearchText(query);
        }

        if (dateFilterCheckBox.isSelected()) {
            Date d = (Date) dateSpinner.getValue();
            LocalDate localDate = d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            filter.setDate(localDate);
        }

        String selectedStatus = (String) statusFilterCombo.getSelectedItem();
        if (selectedStatus != null && !"ALL".equalsIgnoreCase(selectedStatus)) {
            try {
                filter.setStatus(ReservationStatus.valueOf(selectedStatus));
            } catch (IllegalArgumentException ignored) {
            }
        }

        String selectedTable = (String) tableFilterCombo.getSelectedItem();
        if (selectedTable != null && !"All Tables".equalsIgnoreCase(selectedTable)) {
            filter.setTableNumber(selectedTable);
        }

        Ui.async(
                () -> reservationService.searchReservations(filter),
                results -> {
                    searchBtn.setEnabled(true);
                    searchBtn.setText("Search");

                    currentResults.clear();
                    currentResults.addAll(results);

                    tableModel.setRowCount(0);
                    for (ReservationDetail r : results) {
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

                    if (resultsTable instanceof StyledTable st) st.reveal();
                    countFooterLabel.setText(results.size() + (results.size() == 1 ? " reservation found" : " reservations found"));
                    updateActionButtonsState();
                },
                error -> {
                    searchBtn.setEnabled(true);
                    searchBtn.setText("Search");
                    Toast.error(SwingUtilities.getWindowAncestor(this), error.getMessage());
                }
        );
    }

    private void updateActionButtonsState() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentResults.size()) {
            seatGuestBtn.setEnabled(false);
            cancelBtn.setEnabled(false);
            markCompletedBtn.setEnabled(false);
            viewDetailsBtn.setEnabled(false);
            viewCustomerBtn.setEnabled(false);
            actionHintLabel.setText("💡 Select a reservation above to manage (Seat Guest, Complete, Cancel, or View Details)");
            return;
        }

        ReservationDetail detail = currentResults.get(selectedRow);
        ReservationStatus status = detail.getStatus();
        viewDetailsBtn.setEnabled(true);
        viewCustomerBtn.setEnabled(true);

        // Seat Guest enabled ONLY for TODAY, for CONFIRMED and HELD
        boolean isToday = detail.getReservationDate() != null && detail.getReservationDate().equals(util.DbClock.today());
        boolean canSeat = isToday && (status == ReservationStatus.CONFIRMED || status == ReservationStatus.HELD);
        seatGuestBtn.setEnabled(canSeat);

        // Cancel enabled for CONFIRMED and HELD
        boolean canCancel = (status == ReservationStatus.CONFIRMED || status == ReservationStatus.HELD);
        cancelBtn.setEnabled(canCancel);

        // Mark Completed enabled ONLY for SEATED bookings (DB trigger enforces CONFIRMED -> SEATED -> COMPLETED)
        boolean canComplete = (status == ReservationStatus.SEATED);
        markCompletedBtn.setEnabled(canComplete);

        if (canSeat) {
            actionHintLabel.setText("Selected " + detail.getDisplayCode() + " (" + status + ") · Ready to Seat Guest or Cancel");
        } else if (!isToday && (status == ReservationStatus.CONFIRMED || status == ReservationStatus.HELD)) {
            String dateStr = detail.getReservationDate() != null ? detail.getReservationDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "N/A";
            actionHintLabel.setText("Selected " + detail.getDisplayCode() + " · Scheduled for " + dateStr + " (Seating opens on reservation date)");
        } else if (status == ReservationStatus.SEATED) {
            actionHintLabel.setText("Selected " + detail.getDisplayCode() + " (Seated at Table " + detail.getTableNumber() + ") · Ready to Mark Completed");
        } else {
            actionHintLabel.setText("Selected " + detail.getDisplayCode() + " (" + status + ") · Double-click or click View Details");
        }
    }

    private void onSeatSelectedReservation() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentResults.size()) return;

        ReservationDetail detail = currentResults.get(selectedRow);

        int confirm = Dialogs.showConfirmDialog(
                this,
                String.format("Seat guest %s (%d guests) at Table %s?",
                        detail.getCustomerName(), detail.getGuestCount(), detail.getTableNumber()),
                "Seat Arriving Guest",
                Dialogs.YES_NO_OPTION,
                Dialogs.QUESTION_MESSAGE
        );

        if (confirm == Dialogs.YES_OPTION) {
            seatGuestBtn.setEnabled(false);
            seatGuestBtn.setText("Seating...");

            Ui.async(
                    () -> {
                        reservationService.seat(detail.getReservationId());
                        return null;
                    },
                    res -> {
                        seatGuestBtn.setText("Seat Guest");
                        Toast.success(SwingUtilities.getWindowAncestor(this),
                                "✓ Guest " + detail.getCustomerName() + " seated at Table " + detail.getTableNumber());
                        performSearch();
                    },
                    error -> {
                        seatGuestBtn.setText("Seat Guest");
                        Toast.error(SwingUtilities.getWindowAncestor(this), error.getMessage());
                        performSearch();
                    }
            );
        }
    }

    private void onMarkCompletedSelectedReservation() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentResults.size()) return;

        ReservationDetail detail = currentResults.get(selectedRow);

        int confirm = Dialogs.showConfirmDialog(
                this,
                "Mark reservation " + detail.getDisplayCode() + " (" + detail.getCustomerName() + ") as COMPLETED?",
                "Complete Dining Service",
                Dialogs.YES_NO_OPTION,
                Dialogs.QUESTION_MESSAGE
        );

        if (confirm == Dialogs.YES_OPTION) {
            markCompletedBtn.setEnabled(false);
            markCompletedBtn.setText("Updating...");

            Ui.async(
                    () -> {
                        if (detail.getStatus() == ReservationStatus.CONFIRMED) {
                            reservationService.seat(detail.getReservationId());
                        }
                        reservationService.markCompleted(detail.getReservationId());
                        return null;
                    },
                    res -> {
                        markCompletedBtn.setText("Mark Completed");
                        Toast.success(SwingUtilities.getWindowAncestor(this),
                                "✓ Reservation " + detail.getDisplayCode() + " marked as COMPLETED. Table " + detail.getTableNumber() + " is now free.");
                        performSearch();
                    },
                    error -> {
                        markCompletedBtn.setText("Mark Completed");
                        Toast.error(SwingUtilities.getWindowAncestor(this), error.getMessage());
                        performSearch();
                    }
            );
        }
    }

    private void onCancelSelectedReservation() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentResults.size()) return;

        ReservationDetail detail = currentResults.get(selectedRow);

        String message = String.format(
                "Are you sure you want to cancel %s (%s, %s, %s)?\n" +
                "The table will immediately become available again for booking.",
                detail.getDisplayCode(),
                detail.getTableNumber(),
                detail.getReservationDate().format(DATE_FMT),
                detail.getSlotLabel()
        );

        int confirm = Dialogs.showConfirmDialog(
                this,
                message,
                "Confirm Cancellation",
                Dialogs.YES_NO_OPTION,
                Dialogs.WARNING_MESSAGE
        );

        if (confirm == Dialogs.YES_OPTION) {
            cancelBtn.setEnabled(false);
            cancelBtn.setText("Cancelling...");

            Ui.async(
                    () -> {
                        reservationService.cancelReservation(detail.getReservationId());
                        return null;
                    },
                    res -> {
                        cancelBtn.setText("Cancel Reservation");
                        Toast.success(SwingUtilities.getWindowAncestor(this), "✓ Reservation " + detail.getDisplayCode() + " cancelled successfully.");
                        performSearch();
                    },
                    error -> {
                        cancelBtn.setText("Cancel Reservation");
                        Toast.error(SwingUtilities.getWindowAncestor(this), error.getMessage());
                        performSearch();
                    }
            );
        }
    }

    private void onViewDetailsSelectedReservation() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentResults.size()) return;

        ReservationDetail detail = currentResults.get(selectedRow);

        String info = String.format(
                "Booking Code: %s\n" +
                "Customer: %s\n" +
                "Phone: %s\n" +
                "Table: Table %s\n" +
                "Date: %s\n" +
                "Dining Slot: %s\n" +
                "Party Size: %d guests\n" +
                "Status: %s\n" +
                "Source: %s\n" +
                "Reconfirmation: %s",
                detail.getDisplayCode(),
                detail.getCustomerName(),
                detail.getPhone(),
                detail.getTableNumber(),
                detail.getReservationDate().format(DATE_FMT),
                detail.getSlotLabel(),
                detail.getGuestCount(),
                detail.getStatus(),
                detail.getSource(),
                detail.getReconfirmStatus()
        );

        Dialogs.showMessageDialog(this, info, "Reservation Details · " + detail.getDisplayCode(), Dialogs.INFORMATION_MESSAGE);
    }

    private void onViewCustomerSelectedReservation() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentResults.size()) return;
        ReservationDetail detail = currentResults.get(selectedRow);

        Ui.async(
                () -> {
                    List<model.Customer> found = customerService.searchCustomers(detail.getPhone());
                    if (!found.isEmpty()) return found.get(0);
                    return customerService.searchCustomers(detail.getCustomerName()).stream().findFirst().orElse(null);
                },
                cust -> {
                    if (cust != null) {
                        CustomerDetailDialog.show(
                                SwingUtilities.getWindowAncestor(this),
                                cust,
                                customerService,
                                reservationService,
                                updated -> performSearch(),
                                bookedCust -> {
                                    MainFrame mf = (MainFrame) SwingUtilities.getAncestorOfClass(MainFrame.class, this);
                                    if (mf != null) mf.showScreen("NEW_RESERVATION");
                                }
                        );
                    } else {
                        Toast.info(SwingUtilities.getWindowAncestor(this), "Customer profile not found for " + detail.getCustomerName());
                    }
                },
                err -> Toast.error(SwingUtilities.getWindowAncestor(this), err.getMessage())
        );
    }

    private JPanel createCardPanel() {
        return new Card(18, Theme.SPACE_24);
    }

    private JButton createPrimaryButton(String text) {
        return AppButton.primary(text);
    }

    private JButton createSecondaryButton(String text) {
        return AppButton.secondary(text);
    }

    private JButton createDangerButton(String text) {
        return AppButton.danger(text);
    }
}
