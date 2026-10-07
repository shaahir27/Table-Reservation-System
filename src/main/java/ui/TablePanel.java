package ui;

import com.formdev.flatlaf.FlatClientProperties;
import exception.ReferenceInUseException;
import exception.ValidationException;
import model.ReservationDetail;
import model.RestaurantTable;
import model.TableAvailability;
import model.TableState;
import model.TableStatus;
import model.TimeSlot;
import service.ReservationService;
import service.TableService;
import ui.components.DateSelector;
import ui.components.StatusPillRenderer;
import ui.components.TableGridPanel;
import ui.components.Toast;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Restaurant Table management view (Section 10.6).
 * Features two tabs: "Manage" (Phase 2 CRUD) and "Floor view" (Phase 5).
 */
public class TablePanel extends BasePanel {
    private final TableService tableService;
    private final ReservationService reservationService;

    // Form fields
    private final JTextField tableNumberField = new JTextField();
    private final JSpinner capacitySpinner = new JSpinner(new SpinnerNumberModel(4, 1, 20, 1));
    private final JComboBox<String> locationComboBox = new JComboBox<>(new String[]{"Indoor", "Window", "Outdoor", "Hall"});
    private final JComboBox<TableStatus> statusComboBox = new JComboBox<>(TableStatus.values());

    // Error labels
    private final JLabel tableNumberErrorLabel = new JLabel(" ");
    private final JLabel capacityErrorLabel = new JLabel(" ");
    private final JLabel locationErrorLabel = new JLabel(" ");

    // Action buttons
    private JButton addBtn;
    private JButton updateBtn;
    private JButton deleteBtn;
    private JButton clearBtn;

    // Table view
    private final DefaultTableModel tableModel;
    private final JTable tableJTable;
    private final List<RestaurantTable> displayedTables = new ArrayList<>();
    private RestaurantTable selectedTable = null;

    // Floor View components
    private final DateSelector floorDateSelector = new DateSelector();
    private final ButtonGroup floorSlotGroup = new ButtonGroup();
    private final List<FloorSlotToggleButton> floorSlotButtons = new ArrayList<>();
    private final JPanel floorSlotsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_8, 0));
    private final TableGridPanel floorGridPanel = new TableGridPanel();
    private final JLabel floorDetailsTitle = new JLabel("Table Details");
    private final JLabel floorDetailsBody = new JLabel("Click on any table above to inspect status and reservation details.");
    private JButton floorRefreshBtn;

    public TablePanel() {
        this(new TableService(), new ReservationService());
    }

    public TablePanel(TableService tableService) {
        this(tableService, new ReservationService());
    }

    public TablePanel(TableService tableService, ReservationService reservationService) {
        super("Restaurant Tables");
        this.tableService = tableService;
        this.reservationService = reservationService;

        tableModel = new DefaultTableModel(new String[]{"ID", "Table Number", "Capacity", "Location", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tableJTable = new JTable(tableModel);
        tableJTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableJTable.setRowHeight(38);
        tableJTable.getTableHeader().setPreferredSize(new Dimension(0, 40));
        tableJTable.getTableHeader().setFont(Theme.FONT_BODY_BOLD);
        tableJTable.setFont(Theme.FONT_BODY);

        // Center ID and Capacity
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        tableJTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tableJTable.getColumnModel().getColumn(0).setMaxWidth(60);
        tableJTable.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        tableJTable.getColumnModel().getColumn(2).setMaxWidth(90);

        // Status column uses custom rounded pill renderer
        tableJTable.getColumnModel().getColumn(4).setCellRenderer(new StatusPillRenderer());
        tableJTable.getColumnModel().getColumn(4).setMinWidth(120);

        tableJTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = tableJTable.getSelectedRow();
                if (selectedRow >= 0 && selectedRow < displayedTables.size()) {
                    selectTable(displayedTables.get(selectedRow));
                }
            }
        });

        initTabs();
    }

    @Override
    public void onShown() {
        loadTables();
        loadFloorSlots();
    }

    private void initTabs() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(Theme.FONT_BODY_BOLD);

        // Tab 1: Manage Tables
        JPanel manageTab = new JPanel(new BorderLayout(Theme.SPACE_24, 0));
        manageTab.setOpaque(false);
        manageTab.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_16, 0, 0, 0));

        manageTab.add(createFormCard(), BorderLayout.WEST);
        manageTab.add(createTableCard(), BorderLayout.CENTER);

        tabbedPane.addTab("Manage Tables", manageTab);

        // Tab 2: Floor View (Section 10.6 & Phase 5)
        tabbedPane.addTab("Floor View", createFloorViewTab());

        contentPanel.add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createFormCard() {
        JPanel card = createCardPanel();
        card.setPreferredSize(new Dimension(360, 0));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Table Details");
        title.setFont(Theme.FONT_SECTION);
        title.setForeground(Theme.TEXT_PRIMARY);
        title.setAlignmentX(0.0f);
        card.add(title);
        card.add(Box.createVerticalStrut(Theme.SPACE_16));

        // Table Number
        card.add(createFieldLabel("Table Number * (e.g. T01)"));
        card.add(Box.createVerticalStrut(4));
        tableNumberField.setPreferredSize(new Dimension(300, 38));
        tableNumberField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        tableNumberField.setAlignmentX(0.0f);
        tableNumberField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. T01");
        card.add(tableNumberField);
        styleErrorLabel(tableNumberErrorLabel);
        card.add(tableNumberErrorLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_8));

        // Capacity
        card.add(createFieldLabel("Seating Capacity * (1–20)"));
        card.add(Box.createVerticalStrut(4));
        capacitySpinner.setPreferredSize(new Dimension(300, 38));
        capacitySpinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        capacitySpinner.setAlignmentX(0.0f);
        card.add(capacitySpinner);
        styleErrorLabel(capacityErrorLabel);
        card.add(capacityErrorLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_8));

        // Location
        card.add(createFieldLabel("Location / Section *"));
        card.add(Box.createVerticalStrut(4));
        locationComboBox.setEditable(true);
        locationComboBox.setPreferredSize(new Dimension(300, 38));
        locationComboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        locationComboBox.setAlignmentX(0.0f);
        card.add(locationComboBox);
        styleErrorLabel(locationErrorLabel);
        card.add(locationErrorLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_8));

        // Status
        card.add(createFieldLabel("Operational Status *"));
        card.add(Box.createVerticalStrut(4));
        statusComboBox.setPreferredSize(new Dimension(300, 38));
        statusComboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        statusComboBox.setAlignmentX(0.0f);
        card.add(statusComboBox);
        card.add(Box.createVerticalStrut(Theme.SPACE_24));

        // Buttons
        JPanel buttonRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonRow1.setOpaque(false);
        buttonRow1.setAlignmentX(0.0f);

        addBtn = createPrimaryButton("Add Table");
        addBtn.addActionListener(e -> onAddTable());
        buttonRow1.add(addBtn);

        updateBtn = createSecondaryButton("Update");
        updateBtn.setEnabled(false);
        updateBtn.addActionListener(e -> onUpdateTable());
        buttonRow1.add(updateBtn);

        JPanel buttonRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonRow2.setOpaque(false);
        buttonRow2.setAlignmentX(0.0f);

        deleteBtn = createDangerButton("Delete");
        deleteBtn.setEnabled(false);
        deleteBtn.addActionListener(e -> onDeleteTable());
        buttonRow2.add(deleteBtn);

        clearBtn = createSecondaryButton("Clear");
        clearBtn.addActionListener(e -> clearForm());
        buttonRow2.add(clearBtn);

        card.add(buttonRow1);
        card.add(Box.createVerticalStrut(Theme.SPACE_8));
        card.add(buttonRow2);
        card.add(Box.createVerticalGlue());

        return card;
    }

    private JPanel createTableCard() {
        JPanel card = createCardPanel();
        card.setLayout(new BorderLayout(0, Theme.SPACE_16));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JLabel listTitle = new JLabel("All Restaurant Tables");
        listTitle.setFont(Theme.FONT_SECTION);
        listTitle.setForeground(Theme.TEXT_PRIMARY);
        topBar.add(listTitle, BorderLayout.WEST);

        JButton refreshBtn = createSecondaryButton("Refresh");
        refreshBtn.addActionListener(e -> loadTables());
        topBar.add(refreshBtn, BorderLayout.EAST);

        card.add(topBar, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(tableJTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        scrollPane.getViewport().setBackground(Color.WHITE);
        card.add(scrollPane, BorderLayout.CENTER);

        return card;
    }

    public void loadTables() {
        Ui.async(
                tableService::getAllTables,
                this::populateTable,
                error -> Toast.error(SwingUtilities.getWindowAncestor(this), "Failed to load tables: " + error.getMessage())
        );
    }

    private void populateTable(List<RestaurantTable> tables) {
        displayedTables.clear();
        displayedTables.addAll(tables);
        tableModel.setRowCount(0);

        for (RestaurantTable t : tables) {
            tableModel.addRow(new Object[]{
                    t.getTableId(),
                    t.getTableNumber(),
                    t.getCapacity() + " seats",
                    t.getLocation(),
                    t.getStatus().name()
            });
        }
    }

    private void selectTable(RestaurantTable table) {
        this.selectedTable = table;
        tableNumberField.setText(table.getTableNumber());
        capacitySpinner.setValue(table.getCapacity());
        locationComboBox.setSelectedItem(table.getLocation());
        statusComboBox.setSelectedItem(table.getStatus());

        clearValidationErrors();
        addBtn.setEnabled(false);
        updateBtn.setEnabled(true);
        deleteBtn.setEnabled(true);
    }

    private void clearForm() {
        selectedTable = null;
        tableJTable.clearSelection();
        tableNumberField.setText("");
        capacitySpinner.setValue(4);
        locationComboBox.setSelectedIndex(0);
        statusComboBox.setSelectedItem(TableStatus.AVAILABLE);

        clearValidationErrors();
        addBtn.setEnabled(true);
        updateBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
    }

    private void onAddTable() {
        clearValidationErrors();
        RestaurantTable table = new RestaurantTable(
                tableNumberField.getText().trim(),
                (Integer) capacitySpinner.getValue(),
                locationComboBox.getSelectedItem() != null ? locationComboBox.getSelectedItem().toString().trim() : "",
                (TableStatus) statusComboBox.getSelectedItem()
        );

        addBtn.setEnabled(false);
        addBtn.setText("Adding...");

        Ui.async(
                () -> tableService.createTable(table),
                created -> {
                    addBtn.setEnabled(true);
                    addBtn.setText("Add Table");
                    Toast.success(SwingUtilities.getWindowAncestor(this), "Table " + created.getTableNumber() + " added successfully");
                    clearForm();
                    loadTables();
                },
                error -> {
                    addBtn.setEnabled(true);
                    addBtn.setText("Add Table");
                    handleFormError(error);
                }
        );
    }

    private void onUpdateTable() {
        if (selectedTable == null) return;
        clearValidationErrors();

        selectedTable.setTableNumber(tableNumberField.getText().trim());
        selectedTable.setCapacity((Integer) capacitySpinner.getValue());
        selectedTable.setLocation(locationComboBox.getSelectedItem() != null ? locationComboBox.getSelectedItem().toString().trim() : "");
        selectedTable.setStatus((TableStatus) statusComboBox.getSelectedItem());

        updateBtn.setEnabled(false);
        updateBtn.setText("Updating...");

        Ui.async(
                () -> {
                    tableService.updateTable(selectedTable);
                    return selectedTable;
                },
                updated -> {
                    updateBtn.setEnabled(true);
                    updateBtn.setText("Update");
                    Toast.success(SwingUtilities.getWindowAncestor(this), "Table " + updated.getTableNumber() + " updated successfully");
                    clearForm();
                    loadTables();
                },
                error -> {
                    updateBtn.setEnabled(true);
                    updateBtn.setText("Update");
                    handleFormError(error);
                }
        );
    }

    private void onDeleteTable() {
        if (selectedTable == null) return;

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete table " + selectedTable.getTableNumber() + "?\n" +
                "Note: If this table has active or past reservations, please set its status to INACTIVE instead.",
                "Confirm Table Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) return;

        int tableId = selectedTable.getTableId();
        deleteBtn.setEnabled(false);
        deleteBtn.setText("Deleting...");

        Ui.async(
                () -> {
                    tableService.deleteTable(tableId);
                    return true;
                },
                success -> {
                    deleteBtn.setEnabled(true);
                    deleteBtn.setText("Delete");
                    Toast.success(SwingUtilities.getWindowAncestor(this), "Table deleted successfully");
                    clearForm();
                    loadTables();
                },
                error -> {
                    deleteBtn.setEnabled(true);
                    deleteBtn.setText("Delete");
                    if (error instanceof ReferenceInUseException) {
                        JOptionPane.showMessageDialog(
                                this,
                                error.getMessage(),
                                "Cannot Delete Table",
                                JOptionPane.ERROR_MESSAGE
                        );
                    } else {
                        Toast.error(SwingUtilities.getWindowAncestor(this), error.getMessage());
                    }
                }
        );
    }

    private void handleFormError(Throwable error) {
        if (error instanceof ValidationException) {
            ValidationException ve = (ValidationException) error;
            Map<String, String> errors = ve.getFieldErrors();
            if (errors != null && !errors.isEmpty()) {
                if (errors.containsKey("tableNumber")) {
                    tableNumberErrorLabel.setText(errors.get("tableNumber"));
                    tableNumberField.putClientProperty(FlatClientProperties.OUTLINE, "error");
                }
                if (errors.containsKey("capacity")) {
                    capacityErrorLabel.setText(errors.get("capacity"));
                }
                if (errors.containsKey("location")) {
                    locationErrorLabel.setText(errors.get("location"));
                }
            } else {
                Toast.error(SwingUtilities.getWindowAncestor(this), error.getMessage());
            }
        } else {
            Toast.error(SwingUtilities.getWindowAncestor(this), error.getMessage());
        }
    }

    private void clearValidationErrors() {
        tableNumberErrorLabel.setText(" ");
        capacityErrorLabel.setText(" ");
        locationErrorLabel.setText(" ");

        tableNumberField.putClientProperty(FlatClientProperties.OUTLINE, null);
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
        return card;
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.FONT_SMALL_BOLD);
        label.setForeground(Theme.TEXT_MUTED);
        return label;
    }

    private void styleErrorLabel(JLabel label) {
        label.setFont(Theme.FONT_SMALL);
        label.setForeground(Theme.DANGER);
        label.setAlignmentX(0.0f);
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
        btn.setPreferredSize(new Dimension(130, 38));
        return btn;
    }

    private JButton createSecondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(Theme.FONT_BODY);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(100, 38));
        return btn;
    }

    private JButton createDangerButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                if (isEnabled()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(192, 57, 43, 20));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(Theme.DANGER);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        btn.setFont(Theme.FONT_BODY_BOLD);
        btn.setForeground(Theme.DANGER);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(100, 38));
        return btn;
    }

    private JPanel createFloorViewTab() {
        JPanel floorPanel = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        floorPanel.setOpaque(false);
        floorPanel.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_16, 0, 0, 0));

        // 1. Filter / Selector Header Card
        JPanel headerCard = createCardPanel();
        headerCard.setLayout(new BorderLayout(Theme.SPACE_16, Theme.SPACE_12));

        JPanel selectorsTop = new JPanel(new BorderLayout());
        selectorsTop.setOpaque(false);

        JLabel dateLabel = new JLabel("Date:");
        dateLabel.setFont(Theme.FONT_BODY_BOLD);
        dateLabel.setForeground(Theme.TEXT_PRIMARY);

        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_8, 0));
        dateRow.setOpaque(false);
        dateRow.add(dateLabel);
        dateRow.add(floorDateSelector);
        selectorsTop.add(dateRow, BorderLayout.WEST);

        floorRefreshBtn = createSecondaryButton("Refresh Floor");
        floorRefreshBtn.addActionListener(e -> loadFloorView());
        selectorsTop.add(floorRefreshBtn, BorderLayout.EAST);

        headerCard.add(selectorsTop, BorderLayout.NORTH);

        JPanel slotRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_8, 0));
        slotRow.setOpaque(false);
        JLabel slotLabel = new JLabel("Time Slot:");
        slotLabel.setFont(Theme.FONT_BODY_BOLD);
        slotLabel.setForeground(Theme.TEXT_PRIMARY);
        slotRow.add(slotLabel);
        slotRow.add(floorSlotsPanel);

        headerCard.add(slotRow, BorderLayout.CENTER);
        floorPanel.add(headerCard, BorderLayout.NORTH);

        // 2. Interactive Floor Grid Card
        JPanel gridCard = createCardPanel();
        gridCard.setLayout(new BorderLayout(0, Theme.SPACE_12));

        JLabel gridTitle = new JLabel("Floor Seating Layout & Real-time Table States");
        gridTitle.setFont(Theme.FONT_SECTION);
        gridTitle.setForeground(Theme.TEXT_PRIMARY);
        gridCard.add(gridTitle, BorderLayout.NORTH);

        JScrollPane gridScroll = new JScrollPane(floorGridPanel);
        gridScroll.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        gridScroll.getViewport().setBackground(Theme.CARD_BG);
        gridCard.add(gridScroll, BorderLayout.CENTER);

        floorGridPanel.setOnAnyCardClicked(this::onFloorTableClicked);

        // 3. Details Card at bottom
        JPanel detailsCard = createCardPanel();
        detailsCard.setLayout(new BoxLayout(detailsCard, BoxLayout.Y_AXIS));
        floorDetailsTitle.setFont(Theme.FONT_SECTION);
        floorDetailsTitle.setForeground(Theme.TEXT_PRIMARY);
        floorDetailsBody.setFont(Theme.FONT_BODY);
        floorDetailsBody.setForeground(Theme.TEXT_MUTED);

        detailsCard.add(floorDetailsTitle);
        detailsCard.add(Box.createVerticalStrut(Theme.SPACE_8));
        detailsCard.add(floorDetailsBody);

        gridCard.add(detailsCard, BorderLayout.SOUTH);
        floorPanel.add(gridCard, BorderLayout.CENTER);

        // Listen for date changes
        floorDateSelector.setOnDateChanged(d -> loadFloorView());

        return floorPanel;
    }

    private void loadFloorSlots() {
        Ui.async(
                reservationService::getTimeSlots,
                slots -> {
                    floorSlotsPanel.removeAll();
                    floorSlotButtons.clear();
                    floorSlotGroup.clearSelection();

                    for (int i = 0; i < slots.size(); i++) {
                        TimeSlot s = slots.get(i);
                        FloorSlotToggleButton btn = new FloorSlotToggleButton(s);
                        btn.addActionListener(e -> loadFloorView());
                        floorSlotGroup.add(btn);
                        floorSlotButtons.add(btn);
                        floorSlotsPanel.add(btn);

                        if (i == 0) {
                            btn.setSelected(true);
                        }
                    }

                    floorSlotsPanel.revalidate();
                    floorSlotsPanel.repaint();
                    loadFloorView();
                },
                null
        );
    }

    private void loadFloorView() {
        TimeSlot selectedSlot = getSelectedFloorSlot();
        LocalDate date = floorDateSelector.getDate();

        if (selectedSlot == null || date == null) {
            return;
        }

        if (floorRefreshBtn != null) {
            floorRefreshBtn.setEnabled(false);
            floorRefreshBtn.setText("Loading...");
        }

        Ui.async(
                () -> reservationService.getTableStates(date, selectedSlot.getSlotId()),
                states -> {
                    if (floorRefreshBtn != null) {
                        floorRefreshBtn.setEnabled(true);
                        floorRefreshBtn.setText("Refresh Floor");
                    }
                    floorGridPanel.setData(states, true);
                    floorDetailsTitle.setText("Table Details");
                    floorDetailsBody.setText("Click on any table above to view real-time reservation details.");
                },
                error -> {
                    if (floorRefreshBtn != null) {
                        floorRefreshBtn.setEnabled(true);
                        floorRefreshBtn.setText("Refresh Floor");
                    }
                    Toast.error(SwingUtilities.getWindowAncestor(this), error.getMessage());
                }
        );
    }

    private TimeSlot getSelectedFloorSlot() {
        for (FloorSlotToggleButton btn : floorSlotButtons) {
            if (btn.isSelected()) {
                return btn.getSlot();
            }
        }
        return floorSlotButtons.isEmpty() ? null : floorSlotButtons.get(0).getSlot();
    }

    private void onFloorTableClicked(TableAvailability ta) {
        if (ta == null || ta.getTable() == null) {
            floorDetailsTitle.setText("Table Details");
            floorDetailsBody.setText("Click on any table above to inspect status and reservation details.");
            return;
        }

        RestaurantTable t = ta.getTable();
        TableState state = ta.getState();
        LocalDate date = floorDateSelector.getDate();
        TimeSlot slot = getSelectedFloorSlot();

        floorDetailsTitle.setText(String.format("Table %s · %d seats · %s", t.getTableNumber(), t.getCapacity(), t.getLocation()));

        if (state == TableState.AVAILABLE) {
            floorDetailsBody.setText("<html><b>Status: AVAILABLE</b> — This table is available for booking during this seating.</html>");
        } else if (state == TableState.MAINTENANCE) {
            floorDetailsBody.setText("<html><b>Status: MAINTENANCE</b> — This table is currently marked for maintenance and unavailable for guests.</html>");
        } else if (state == TableState.RESERVED) {
            if (slot == null || date == null) {
                floorDetailsBody.setText("<html><b>Status: RESERVED</b> — Booked for this seating.</html>");
                return;
            }
            floorDetailsBody.setText("<html><b>Status: RESERVED</b> — Fetching reservation details...</html>");

            Ui.async(
                    () -> reservationService.getReservationForSlot(date, slot.getSlotId(), t.getTableId()),
                    opt -> {
                        if (opt.isPresent()) {
                            ReservationDetail r = opt.get();
                            floorDetailsBody.setText(String.format(
                                    "<html><b>Status: RESERVED</b> — <b>%s</b> · Customer: <b>%s</b> (Phone: %s) · Guests: <b>%d</b> · Booked on %s</html>",
                                    r.getDisplayCode(),
                                    r.getCustomerName(),
                                    r.getPhone(),
                                    r.getGuestCount(),
                                    r.getCreatedAt().toLocalDate()
                            ));
                        } else {
                            floorDetailsBody.setText("<html><b>Status: RESERVED</b> — Booked for this seating.</html>");
                        }
                    },
                    err -> floorDetailsBody.setText("<html><b>Status: RESERVED</b> — Booked for this seating.</html>")
            );
        }
    }

    private static class FloorSlotToggleButton extends JToggleButton {
        private final TimeSlot slot;

        public FloorSlotToggleButton(TimeSlot slot) {
            super(slot.getLabel());
            this.slot = slot;
            setFont(Theme.FONT_BODY);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(100, 36));
        }

        public TimeSlot getSlot() {
            return slot;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (isSelected()) {
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
