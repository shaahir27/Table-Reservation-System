package ui;

import exception.BookingConflictException;
import model.Customer;
import model.RestaurantTable;
import model.TableAvailability;
import model.TimeSlot;
import service.CustomerService;
import service.ReservationService;
import service.TableService;
import ui.components.AddCustomerDialog;
import ui.components.BookingConfirmedDialog;
import ui.components.DateSelector;
import ui.components.SearchableComboBox;
import ui.components.TableGridPanel;
import ui.components.Toast;
import util.AppLog;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The core 3-step reservation booking panel (Section 10.3).
 * 1. Who (Customer selection & quick-add)
 * 2. When & how many (DateSelector, slot toggles, guests, location chips, find button)
 * 3. Choose a table (Interactive table card grid, best-fit recommendation, confirmation)
 */
public class NewReservationPanel extends BasePanel {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final ReservationService reservationService;
    private final CustomerService customerService;
    private final TableService tableService;

    // Step 1: Who
    private final SearchableComboBox customerSelector = new SearchableComboBox();

    // Step 2: When & How Many
    private final DateSelector dateSelector = new DateSelector();
    private final ButtonGroup slotButtonGroup = new ButtonGroup();
    private final List<SlotToggleButton> slotButtons = new ArrayList<>();
    private final JPanel slotButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_8, 0));
    private final JSpinner guestSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 20, 1));
    private final ButtonGroup locationGroup = new ButtonGroup();
    private final JPanel locationChipsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_8, 0));
    private String selectedLocationFilter = "Any";
    private JButton findTablesBtn;

    // Step 3: Choose a Table
    private final JPanel step3Card;
    private final TableGridPanel tableGridPanel = new TableGridPanel();
    private final JLabel summaryStripLabel = new JLabel("Please select an available table above.");
    private JButton confirmBtn;

    // State
    private List<TableAvailability> currentSearchResults = new ArrayList<>();
    private RestaurantTable selectedTable = null;

    public NewReservationPanel() {
        this(new ReservationService(), new CustomerService(), new TableService());
    }

    public NewReservationPanel(ReservationService reservationService, CustomerService customerService, TableService tableService) {
        super("New Reservation");
        this.reservationService = reservationService;
        this.customerService = customerService;
        this.tableService = tableService;

        step3Card = createCardPanel();

        initLayout();
        setupInputInvalidation();
    }

    @Override
    public void onShown() {
        loadData();
    }

    private void loadData() {
        // Load customers
        Ui.async(
                customerService::getAllCustomers,
                customers -> {
                    Customer previous = customerSelector.getSelectedCustomer();
                    customerSelector.setCustomers(customers);
                    if (previous != null) {
                        customerSelector.setSelectedCustomer(previous);
                    }
                },
                error -> AppLog.error("Error loading customers: " + error.getMessage(), error)
        );

        // Load time slots and location chips
        Ui.async(
                () -> {
                    List<TimeSlot> slots = reservationService.getTimeSlots();
                    List<String> locations = tableService.getDistinctLocations();
                    return new Object[]{slots, locations};
                },
                result -> {
                    @SuppressWarnings("unchecked")
                    List<TimeSlot> slots = (List<TimeSlot>) result[0];
                    @SuppressWarnings("unchecked")
                    List<String> locations = (List<String>) result[1];

                    buildSlotButtons(slots);
                    buildLocationChips(locations);
                    updateSlotButtonsState();

                    int maxCap = tableService.getMaxCapacity();
                    SpinnerNumberModel model = (SpinnerNumberModel) guestSpinner.getModel();
                    model.setMaximum(maxCap);
                    if (((Integer) guestSpinner.getValue()) > maxCap) {
                        guestSpinner.setValue(maxCap);
                    }
                },
                error -> AppLog.error("Error loading slots/locations: " + error.getMessage(), error)
        );
    }

    private void initLayout() {
        JPanel scrollContent = new JPanel();
        scrollContent.setLayout(new BoxLayout(scrollContent, BoxLayout.Y_AXIS));
        scrollContent.setOpaque(false);

        // ====================================================================
        // CARD 1: WHO
        // ====================================================================
        JPanel step1Card = createCardPanel();
        step1Card.setLayout(new BoxLayout(step1Card, BoxLayout.Y_AXIS));

        JLabel step1Title = new JLabel("1. Who is dining?");
        step1Title.setFont(Theme.FONT_SECTION);
        step1Title.setForeground(Theme.TEXT_PRIMARY);
        step1Title.setAlignmentX(0.0f);
        step1Card.add(step1Title);
        step1Card.add(Box.createVerticalStrut(Theme.SPACE_16));

        JPanel step1Body = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_16, 0));
        step1Body.setOpaque(false);
        step1Body.setAlignmentX(0.0f);

        customerSelector.setPreferredSize(new Dimension(360, 84));
        step1Body.add(customerSelector);

        JButton newCustomerBtn = new JButton("+ New Customer");
        newCustomerBtn.setFont(Theme.FONT_BODY_BOLD);
        newCustomerBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newCustomerBtn.setPreferredSize(new Dimension(145, 38));
        newCustomerBtn.addActionListener(e -> openAddCustomerDialog());

        JPanel btnWrapper = new JPanel();
        btnWrapper.setLayout(new BoxLayout(btnWrapper, BoxLayout.Y_AXIS));
        btnWrapper.setOpaque(false);
        btnWrapper.add(Box.createVerticalStrut(44));
        btnWrapper.add(newCustomerBtn);
        step1Body.add(btnWrapper);

        step1Card.add(step1Body);
        scrollContent.add(step1Card);
        scrollContent.add(Box.createVerticalStrut(Theme.SPACE_24));

        // ====================================================================
        // CARD 2: WHEN & HOW MANY
        // ====================================================================
        JPanel step2Card = createCardPanel();
        step2Card.setLayout(new BoxLayout(step2Card, BoxLayout.Y_AXIS));

        JLabel step2Title = new JLabel("2. When and how many guests?");
        step2Title.setFont(Theme.FONT_SECTION);
        step2Title.setForeground(Theme.TEXT_PRIMARY);
        step2Title.setAlignmentX(0.0f);
        step2Card.add(step2Title);
        step2Card.add(Box.createVerticalStrut(Theme.SPACE_16));

        // Date selection
        JLabel dateLabel = new JLabel("Select Date:");
        dateLabel.setFont(Theme.FONT_SMALL_BOLD);
        dateLabel.setForeground(Theme.TEXT_MUTED);
        dateLabel.setAlignmentX(0.0f);
        step2Card.add(dateLabel);
        step2Card.add(Box.createVerticalStrut(Theme.SPACE_8));

        dateSelector.setAlignmentX(0.0f);
        dateSelector.setOnDateChanged(d -> {
            updateSlotButtonsState();
            clearStep3();
        });
        step2Card.add(dateSelector);
        step2Card.add(Box.createVerticalStrut(Theme.SPACE_16));

        // Slot selection
        JLabel slotLabel = new JLabel("Time Slot (90-Minute Seating):");
        slotLabel.setFont(Theme.FONT_SMALL_BOLD);
        slotLabel.setForeground(Theme.TEXT_MUTED);
        slotLabel.setAlignmentX(0.0f);
        step2Card.add(slotLabel);
        step2Card.add(Box.createVerticalStrut(Theme.SPACE_8));

        slotButtonsPanel.setOpaque(false);
        slotButtonsPanel.setAlignmentX(0.0f);
        step2Card.add(slotButtonsPanel);
        step2Card.add(Box.createVerticalStrut(Theme.SPACE_16));

        // Guests & Location row
        JPanel guestsAndLocationRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_24, 0));
        guestsAndLocationRow.setOpaque(false);
        guestsAndLocationRow.setAlignmentX(0.0f);

        // Guest count
        JPanel guestsGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_8, 0));
        guestsGroup.setOpaque(false);
        JLabel guestsLbl = new JLabel("Guests:");
        guestsLbl.setFont(Theme.FONT_SMALL_BOLD);
        guestsLbl.setForeground(Theme.TEXT_MUTED);
        guestSpinner.setPreferredSize(new Dimension(80, 36));
        guestSpinner.setFont(Theme.FONT_BODY_BOLD);
        guestSpinner.addChangeListener(e -> clearStep3());
        guestsGroup.add(guestsLbl);
        guestsGroup.add(guestSpinner);
        guestsAndLocationRow.add(guestsGroup);

        // Location chips
        locationChipsPanel.setOpaque(false);
        guestsAndLocationRow.add(locationChipsPanel);

        step2Card.add(guestsAndLocationRow);
        step2Card.add(Box.createVerticalStrut(Theme.SPACE_24));

        // Find available tables button
        findTablesBtn = createPrimaryButton("Find Available Tables");
        findTablesBtn.setAlignmentX(0.0f);
        findTablesBtn.addActionListener(e -> onFindTables());
        step2Card.add(findTablesBtn);

        scrollContent.add(step2Card);
        scrollContent.add(Box.createVerticalStrut(Theme.SPACE_24));

        // ====================================================================
        // CARD 3: CHOOSE A TABLE
        // ====================================================================
        step3Card.setLayout(new BoxLayout(step3Card, BoxLayout.Y_AXIS));

        JLabel step3Title = new JLabel("3. Choose an available table");
        step3Title.setFont(Theme.FONT_SECTION);
        step3Title.setForeground(Theme.TEXT_PRIMARY);
        step3Title.setAlignmentX(0.0f);
        step3Card.add(step3Title);
        step3Card.add(Box.createVerticalStrut(Theme.SPACE_16));

        tableGridPanel.setAlignmentX(0.0f);
        tableGridPanel.setOnTableSelected(this::onTableSelectedFromGrid);
        step3Card.add(tableGridPanel);
        step3Card.add(Box.createVerticalStrut(Theme.SPACE_16));

        // Selection Summary Strip
        JPanel summaryStrip = new JPanel(new BorderLayout());
        summaryStrip.setOpaque(false);
        summaryStrip.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_12, 0, Theme.SPACE_12, 0));
        summaryStrip.setAlignmentX(0.0f);

        summaryStripLabel.setFont(Theme.FONT_BODY_BOLD);
        summaryStripLabel.setForeground(Theme.TEXT_PRIMARY);
        summaryStrip.add(summaryStripLabel, BorderLayout.CENTER);

        confirmBtn = createPrimaryButton("Confirm Reservation");
        confirmBtn.setEnabled(false);
        confirmBtn.addActionListener(e -> onConfirmReservation());
        summaryStrip.add(confirmBtn, BorderLayout.EAST);

        step3Card.add(summaryStrip);

        // Initially hide Step 3 until a search is performed
        step3Card.setVisible(false);
        scrollContent.add(step3Card);

        JScrollPane mainScrollPane = new JScrollPane(scrollContent);
        mainScrollPane.setBorder(null);
        mainScrollPane.setOpaque(false);
        mainScrollPane.getViewport().setOpaque(false);
        mainScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        contentPanel.add(mainScrollPane, BorderLayout.CENTER);
    }

    private void setupInputInvalidation() {
        customerSelector.setOnSelectionChanged(c -> clearStep3());
    }

    private void clearStep3() {
        if (step3Card.isVisible()) {
            step3Card.setVisible(false);
            tableGridPanel.clearSelection();
            selectedTable = null;
            confirmBtn.setEnabled(false);
            summaryStripLabel.setText("Please select an available table above.");
            revalidate();
            repaint();
        }
    }

    private void buildSlotButtons(List<TimeSlot> slots) {
        slotButtonsPanel.removeAll();
        slotButtons.clear();
        slotButtonGroup.clearSelection();

        for (TimeSlot slot : slots) {
            SlotToggleButton btn = new SlotToggleButton(slot);
            slotButtonGroup.add(btn);
            slotButtons.add(btn);
            slotButtonsPanel.add(btn);

            btn.addActionListener(e -> clearStep3());
        }

        if (!slotButtons.isEmpty()) {
            slotButtons.get(0).setSelected(true);
        }

        slotButtonsPanel.revalidate();
        slotButtonsPanel.repaint();
    }

    private void buildLocationChips(List<String> locations) {
        locationChipsPanel.removeAll();
        locationGroup.clearSelection();

        JLabel locLabel = new JLabel("Section:");
        locLabel.setFont(Theme.FONT_SMALL_BOLD);
        locLabel.setForeground(Theme.TEXT_MUTED);
        locationChipsPanel.add(locLabel);

        List<String> allLocs = new ArrayList<>();
        allLocs.add("Any");
        for (String loc : locations) {
            if (!allLocs.contains(loc)) {
                allLocs.add(loc);
            }
        }

        for (String loc : allLocs) {
            JToggleButton chip = createFilterChip(loc);
            locationGroup.add(chip);
            locationChipsPanel.add(chip);

            if ("Any".equals(loc)) {
                chip.setSelected(true);
            }

            chip.addActionListener(e -> {
                selectedLocationFilter = loc;
                applyLocationFilterToGrid();
            });
        }

        locationChipsPanel.revalidate();
        locationChipsPanel.repaint();
    }

    private void updateSlotButtonsState() {
        LocalDate selectedDate = dateSelector.getDate();
        boolean isToday = selectedDate.isEqual(util.DbClock.today());
        LocalTime now = util.DbClock.localTime();

        boolean selectedIsDisabled = false;
        SlotToggleButton firstEnabled = null;

        for (SlotToggleButton btn : slotButtons) {
            boolean active = true;
            if (isToday) {
                active = btn.slot.getStartTime().isAfter(now);
            }
            btn.setEnabled(active);

            if (active && firstEnabled == null) {
                firstEnabled = btn;
            }

            if (btn.isSelected() && !active) {
                selectedIsDisabled = true;
            }
        }

        if (selectedIsDisabled && firstEnabled != null) {
            firstEnabled.setSelected(true);
        }
    }

    private TimeSlot getSelectedSlot() {
        for (SlotToggleButton btn : slotButtons) {
            if (btn.isSelected() && btn.isEnabled()) {
                return btn.slot;
            }
        }
        return null;
    }

    private void onFindTables() {
        LocalDate date = dateSelector.getDate();
        TimeSlot slot = getSelectedSlot();
        int guests = (Integer) guestSpinner.getValue();

        if (slot == null) {
            Toast.error(SwingUtilities.getWindowAncestor(this), "Please select an available time slot for " + date.format(DATE_FMT));
            return;
        }

        findTablesBtn.setEnabled(false);
        findTablesBtn.setText("Searching available tables...");

        Ui.async(
                () -> reservationService.findAvailableTables(date, slot, guests),
                availabilities -> {
                    findTablesBtn.setEnabled(true);
                    findTablesBtn.setText("Find Available Tables");

                    currentSearchResults = availabilities;
                    step3Card.setVisible(true);

                    if (availabilities.isEmpty()) {
                        int maxCap = tableService.getMaxCapacity();
                        if (guests > maxCap) {
                            Toast.info(SwingUtilities.getWindowAncestor(this), "No single table seats " + guests + " guests (max capacity is " + maxCap + "). Table combination will be available in an update.");
                        } else {
                            Toast.info(SwingUtilities.getWindowAncestor(this), "No single table can seat " + guests + " guests for this slot. Try another slot or party size.");
                        }
                    }

                    applyLocationFilterToGrid();
                    revalidate();
                    repaint();
                },
                error -> {
                    findTablesBtn.setEnabled(true);
                    findTablesBtn.setText("Find Available Tables");
                    Toast.error(SwingUtilities.getWindowAncestor(this), error.getMessage());
                }
        );
    }

    private void applyLocationFilterToGrid() {
        if (currentSearchResults == null) return;

        List<TableAvailability> filtered;
        if ("Any".equalsIgnoreCase(selectedLocationFilter)) {
            filtered = currentSearchResults;
        } else {
            filtered = currentSearchResults.stream()
                    .filter(ta -> ta.getTable() != null && selectedLocationFilter.equalsIgnoreCase(ta.getTable().getLocation()))
                    .collect(Collectors.toList());
        }

        tableGridPanel.setData(filtered);
    }

    private void onTableSelectedFromGrid(RestaurantTable table) {
        this.selectedTable = table;

        if (table == null) {
            confirmBtn.setEnabled(false);
            summaryStripLabel.setText("Please select an available table above.");
            return;
        }

        confirmBtn.setEnabled(true);
        LocalDate date = dateSelector.getDate();
        TimeSlot slot = getSelectedSlot();
        int guests = (Integer) guestSpinner.getValue();

        String summaryText = String.format("%s · %d seats · %s · %s · %s · %d guests",
                table.getTableNumber(),
                table.getCapacity(),
                table.getLocation(),
                date.format(DATE_FMT),
                slot != null ? slot.getLabel() : "",
                guests
        );
        summaryStripLabel.setText(summaryText);
    }

    private void onConfirmReservation() {
        Customer customer = customerSelector.getSelectedCustomer();
        if (customer == null) {
            Toast.error(SwingUtilities.getWindowAncestor(this), "Please select a dining customer.");
            return;
        }

        if (selectedTable == null) {
            Toast.error(SwingUtilities.getWindowAncestor(this), "Please select a table.");
            return;
        }

        LocalDate date = dateSelector.getDate();
        TimeSlot slot = getSelectedSlot();
        int guests = (Integer) guestSpinner.getValue();
        int tableId = selectedTable.getTableId();
        RestaurantTable chosenTable = selectedTable;

        confirmBtn.setEnabled(false);
        confirmBtn.setText("Booking...");

        Ui.async(
                () -> reservationService.bookTable(customer.getCustomerId(), tableId, date, slot, guests),
                reservation -> {
                    confirmBtn.setEnabled(true);
                    confirmBtn.setText("Confirm Reservation");

                    // Show Ticket-style BookingConfirmedDialog
                    BookingConfirmedDialog dialog = new BookingConfirmedDialog(
                            SwingUtilities.getWindowAncestor(this),
                            reservation,
                            customer,
                            chosenTable,
                            slot,
                            this::resetForm
                    );
                    dialog.setVisible(true);

                    // Reset state
                    clearStep3();
                },
                error -> {
                    confirmBtn.setEnabled(true);
                    confirmBtn.setText("Confirm Reservation");

                    if (error instanceof BookingConflictException) {
                        // Conflict handling: exact message, mark table booked in grid, preserve inputs
                        JOptionPane.showMessageDialog(
                                this,
                                error.getMessage(),
                                "Reservation Conflict",
                                JOptionPane.WARNING_MESSAGE
                        );
                        tableGridPanel.markTableBooked(chosenTable.getTableNumber());
                    } else {
                        Toast.error(SwingUtilities.getWindowAncestor(this), error.getMessage());
                    }
                }
        );
    }

    private void openAddCustomerDialog() {
        AddCustomerDialog dialog = new AddCustomerDialog(
                SwingUtilities.getWindowAncestor(this),
                customerService,
                newCustomer -> {
                    customerSelector.setSelectedCustomer(newCustomer);
                    Toast.success(SwingUtilities.getWindowAncestor(this), "Customer \"" + newCustomer.getName() + "\" registered");
                }
        );
        dialog.setVisible(true);
    }

    private void resetForm() {
        customerSelector.clearFilter();
        guestSpinner.setValue(2);
        dateSelector.setDate(util.DbClock.today());
        updateSlotButtonsState();
        clearStep3();
    }

    private JPanel createCardPanel() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(Theme.CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_24, Theme.SPACE_24, Theme.SPACE_24, Theme.SPACE_24));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
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
        btn.setPreferredSize(new Dimension(190, 40));
        return btn;
    }

    private JToggleButton createFilterChip(String label) {
        JToggleButton chip = new JToggleButton(label) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (isSelected()) {
                    g2.setColor(Theme.SIDEBAR_BG);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                    setForeground(Color.WHITE);
                } else {
                    g2.setColor(Theme.CARD_BG);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                    g2.setColor(Theme.CARD_BORDER);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                    setForeground(Theme.TEXT_PRIMARY);
                }

                g2.dispose();
                super.paintComponent(g);
            }
        };
        chip.setFont(Theme.FONT_SMALL);
        chip.setFocusPainted(false);
        chip.setBorderPainted(false);
        chip.setContentAreaFilled(false);
        chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chip.setPreferredSize(new Dimension(78, 32));
        return chip;
    }

    /**
     * Custom toggle button representing a time slot.
     */
    private static class SlotToggleButton extends JToggleButton {
        private final TimeSlot slot;

        public SlotToggleButton(TimeSlot slot) {
            super(slot.getLabel());
            this.slot = slot;
            setFont(Theme.FONT_BODY);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(100, 36));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (!isEnabled()) {
                g2.setColor(new Color(240, 240, 240));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                setForeground(Theme.TEXT_MUTED);
            } else if (isSelected()) {
                g2.setColor(Theme.ACCENT_GOLD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                setForeground(Color.WHITE);
                setFont(Theme.FONT_BODY_BOLD);
            } else {
                g2.setColor(Theme.CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(getModel().isRollover() ? Theme.ACCENT_GOLD : Theme.CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                setForeground(Theme.TEXT_PRIMARY);
                setFont(Theme.FONT_BODY);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
