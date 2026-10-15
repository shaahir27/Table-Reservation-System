package ui.components;

import com.formdev.flatlaf.FlatClientProperties;
import model.Customer;
import model.RestaurantTable;
import model.TableAvailability;
import model.TimeSlot;
import service.CustomerService;
import service.ReservationService;
import service.TableService;
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
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Fast walk-in seating dialog (P3 Lifecycle).
 * Detects current active dining slot, finds best-fit table, and seats the party immediately.
 */
public class WalkInDialog extends JDialog {
    private final ReservationService reservationService;
    private final CustomerService customerService;
    private final Runnable onSuccess;

    private final SearchableComboBox customerPicker = new SearchableComboBox();
    private final JSpinner guestSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 20, 1));
    private final JTextField notesField = new JTextField();
    private final JLabel slotLabel = new JLabel("Detecting active dining slot...");
    private final javax.swing.JComboBox<RestaurantTable> tableCombo = new javax.swing.JComboBox<>();
    private final JLabel conflictWarningLabel = new JLabel(" ");
    private final JButton seatBtn;
    private final JButton cancelBtn;

    private TimeSlot activeSlot = null;

    public WalkInDialog(Frame parent, ReservationService reservationService,
                        CustomerService customerService, TableService tableService,
                        Runnable onSuccess) {
        this(parent, reservationService, customerService, onSuccess);
    }

    public WalkInDialog(Frame parent, ReservationService reservationService,
                        CustomerService customerService,
                        Runnable onSuccess) {
        super(parent, "Seat Walk-In Party", true);
        this.reservationService = reservationService;
        this.customerService = customerService;
        this.onSuccess = onSuccess;

        setResizable(false);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Theme.CARD_BG);
        content.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_24, Theme.SPACE_24, Theme.SPACE_24, Theme.SPACE_24));

        // Header
        JLabel title = new JLabel("Immediate Walk-In Seating");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_PRIMARY);
        title.setAlignmentX(0.0f);
        content.add(title);

        JLabel subtitle = new JLabel("Seat guest immediately into today's ongoing dining slot");
        subtitle.setFont(Theme.FONT_SMALL);
        subtitle.setForeground(Theme.TEXT_MUTED);
        subtitle.setAlignmentX(0.0f);
        content.add(subtitle);
        content.add(Box.createVerticalStrut(Theme.SPACE_16));

        // Active Slot Banner
        JPanel slotBanner = new JPanel(new BorderLayout(8, 0));
        slotBanner.setBackground(Theme.GOLD_TINT);
        slotBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.ACCENT_GOLD, 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        slotBanner.setAlignmentX(0.0f);
        slotBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        slotLabel.setFont(Theme.FONT_BODY_BOLD);
        slotLabel.setForeground(Theme.TEXT_PRIMARY);
        
        slotBanner.add(slotLabel, BorderLayout.CENTER);
        content.add(slotBanner);
        content.add(Box.createVerticalStrut(Theme.SPACE_16));

        // Customer
        JLabel custLabel = new JLabel("Customer *");
        custLabel.setFont(Theme.FONT_SMALL_BOLD);
        custLabel.setForeground(Theme.TEXT_MUTED);
        custLabel.setAlignmentX(0.0f);
        content.add(custLabel);
        content.add(Box.createVerticalStrut(6));
        customerPicker.setAlignmentX(0.0f);
        customerPicker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 98));
        content.add(customerPicker);
        content.add(Box.createVerticalStrut(Theme.SPACE_12));

        // Guests & Spinner
        JLabel guestCountLabel = new JLabel("Party Size * (Guests)");
        guestCountLabel.setFont(Theme.FONT_SMALL_BOLD);
        guestCountLabel.setForeground(Theme.TEXT_MUTED);
        guestCountLabel.setAlignmentX(0.0f);
        content.add(guestCountLabel);
        content.add(Box.createVerticalStrut(6));

        guestSpinner.setFont(Theme.FONT_BODY);
        Form.input(guestSpinner);
        guestSpinner.addChangeListener(e -> updateRecommendation());
        content.add(guestSpinner);
        content.add(Box.createVerticalStrut(Theme.SPACE_12));

        // Notes
        JLabel notesTitle = new JLabel("Special Request / Notes (Optional)");
        notesTitle.setFont(Theme.FONT_SMALL_BOLD);
        notesTitle.setForeground(Theme.TEXT_MUTED);
        notesTitle.setAlignmentX(0.0f);
        content.add(notesTitle);
        content.add(Box.createVerticalStrut(6));

        notesField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. Near window, birthday, highchair");
        Form.input(notesField);
        content.add(notesField);
        content.add(Box.createVerticalStrut(Theme.SPACE_16));

        // Table Selection
        JLabel tableLabel = new JLabel("Available Table *");
        tableLabel.setFont(Theme.FONT_SMALL_BOLD);
        tableLabel.setForeground(Theme.TEXT_MUTED);
        tableLabel.setAlignmentX(0.0f);
        content.add(tableLabel);
        content.add(Box.createVerticalStrut(6));

        Form.input(tableCombo);
        tableCombo.setAlignmentX(0.0f);
        tableCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        tableCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof RestaurantTable t) {
                    setText("Table " + t.getTableNumber() + " (" + t.getCapacity() + " seats · " + t.getLocation() + ")");
                }
                return this;
            }
        });
        tableCombo.addActionListener(e -> checkConflict((RestaurantTable) tableCombo.getSelectedItem()));
        content.add(tableCombo);
        content.add(Box.createVerticalStrut(6));

        conflictWarningLabel.setFont(Theme.FONT_SMALL);
        conflictWarningLabel.setAlignmentX(0.0f);
        content.add(conflictWarningLabel);
        content.add(Box.createVerticalStrut(Theme.SPACE_8));

        // Buttons
        JPanel buttonRow = new ChipRow(10, true);
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(0.0f);

        cancelBtn = AppButton.secondary("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        buttonRow.add(cancelBtn);

        seatBtn = AppButton.primary("Seat Party Now");
        seatBtn.addActionListener(e -> onSeat());
        buttonRow.add(seatBtn);

        content.add(Box.createVerticalStrut(Theme.SPACE_16));
        content.add(buttonRow);
        setContentPane(content);
        pack();
        setSize(Math.max(getWidth(), 500), getHeight());
        setLocationRelativeTo(parent);

        seatBtn.setEnabled(false);
        loadInitialData();
    }

    private void loadInitialData() {
        // Customers and the slot list both come from the database: load them off the UI thread
        Ui.async(
                () -> customerService.getAllCustomers(),
                customers -> customerPicker.setCustomerList(customers),
                err -> conflictWarningLabel.setText("Error loading customers: " + err.getMessage())
        );

        Ui.async(
                () -> reservationService.getTimeSlots(),
                slots -> {
                    LocalTime now = DbClock.localTime();
                    for (TimeSlot s : slots) {
                        if (!now.isBefore(s.getStartTime()) && now.isBefore(s.getEndTime())) {
                            activeSlot = s;
                            break;
                        }
                    }
                    if (activeSlot == null) {
                        for (TimeSlot s : slots) {
                            if (now.isBefore(s.getStartTime())) {
                                activeSlot = s;
                                break;
                            }
                        }
                    }
                    if (activeSlot != null) {
                        slotLabel.setText("Slot: " + activeSlot.getLabel());
                    } else {
                        slotLabel.setText("No active or upcoming dining slots today.");
                        seatBtn.setEnabled(false);
                    }
                    updateRecommendation();
                },
                err -> {
                    slotLabel.setText("Could not load dining slots.");
                    conflictWarningLabel.setText(err.getMessage());
                }
        );
    }

    private void updateRecommendation() {
        if (activeSlot == null) {
            conflictWarningLabel.setText("Dining service closed for today.");
            seatBtn.setEnabled(false);
            return;
        }

        int guests = (Integer) guestSpinner.getValue();
        LocalDate today = DbClock.today();

        Ui.async(
                () -> reservationService.findAvailableTables(today, activeSlot, guests),
                availabilities -> {
                    tableCombo.removeAllItems();
                    RestaurantTable bestFit = null;
                    for (TableAvailability ta : availabilities) {
                        if (ta.getTable() != null) {
                            tableCombo.addItem(ta.getTable());
                            if (ta.isRecommended() && bestFit == null) {
                                bestFit = ta.getTable();
                            }
                        }
                    }
                    if (bestFit != null) {
                        tableCombo.setSelectedItem(bestFit);
                    }
                    if (tableCombo.getItemCount() > 0) {
                        seatBtn.setEnabled(true);
                        checkConflict((RestaurantTable) tableCombo.getSelectedItem());
                    } else {
                        conflictWarningLabel.setText("<html><b style='color:#DC2626;'>No tables available</b> fit " + guests + " guests right now.</html>");
                        seatBtn.setEnabled(false);
                    }
                },
                err -> {
                    conflictWarningLabel.setText("Recommendation error: " + err.getMessage());
                    seatBtn.setEnabled(false);
                }
        );
    }

    private void checkConflict(RestaurantTable table) {
        if (table == null || activeSlot == null) {
            conflictWarningLabel.setText(" ");
            return;
        }
        LocalDate today = DbClock.today();
        Ui.async(
                () -> reservationService.getNextBookedSlotForTable(table.getTableId(), today, activeSlot.getSlotId()),
                optSlot -> {
                    if (optSlot.isPresent()) {
                        TimeSlot s = optSlot.get();
                        conflictWarningLabel.setText("<html><b style='color:#D97706;'>⚠️ Note:</b> Table " + table.getTableNumber() +
                                " is booked for the next seating (<b>" + s.getLabel() + "</b>). Ensure party finishes before then.</html>");
                    } else {
                        conflictWarningLabel.setText("<html><span style='color:#16A34A;'>✓ Table " + table.getTableNumber() +
                                " is unreserved for subsequent seatings today.</span></html>");
                    }
                },
                null
        );
    }

    private void onSeat() {
        Customer selectedCust = customerPicker.getSelectedCustomer();
        if (selectedCust == null) {
            Toast.error(this, "Please select or search for a customer.");
            return;
        }

        int guests = (Integer) guestSpinner.getValue();
        String notes = notesField.getText().trim();
        RestaurantTable selectedTable = (RestaurantTable) tableCombo.getSelectedItem();
        Integer tableId = selectedTable != null ? selectedTable.getTableId() : null;

        ((AppButton) seatBtn).setLoading(true);
        seatBtn.setText("Seating…");

        Ui.async(
                () -> reservationService.walkIn(selectedCust.getCustomerId(), guests, notes, tableId),
                res -> {
                    Toast.success(getParent(), "Walk-in seated successfully (RES" + res.getReservationId() + ")");
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                    dispose();
                },
                error -> {
                    ((AppButton) seatBtn).setLoading(false);
                    seatBtn.setText("Seat Party Now");
                    Toast.error(this, error.getMessage());
                }
        );
    }
}
