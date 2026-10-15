package ui;

import com.formdev.flatlaf.FlatClientProperties;
import exception.ReferenceInUseException;
import exception.ValidationException;
import model.Customer;
import model.ReservationDetail;
import model.RestaurantTable;
import model.TableAvailability;
import model.TableState;
import model.TableStatus;
import model.TimeSlot;
import service.ReservationService;
import service.TableService;
import ui.components.ChipRow;
import ui.components.AppButton;
import ui.components.Card;
import ui.components.ChipToggle;
import ui.components.CustomerDetailDialog;
import ui.components.DateSelector;
import ui.components.Form;
import ui.components.StyledTable;
import ui.components.FloorPlanPanel;
import ui.components.StatusPillRenderer;
import ui.components.TableGridPanel;
import ui.components.Toast;
import ui.components.WalkInDialog;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import ui.components.Dialogs;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
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
    private final DateSelector floorDateSelector = new DateSelector(true, false);
    private final ButtonGroup floorSlotGroup = new ButtonGroup();
    private final List<FloorSlotToggleButton> floorSlotButtons = new ArrayList<>();
    private final JPanel floorSlotsPanel = new ChipRow(Theme.SPACE_8);
    private final TableGridPanel floorGridPanel = new TableGridPanel();
    private final FloorPlanPanel floorPlanPanel = new FloorPlanPanel();
    private final CardLayout floorCardsLayout = new CardLayout();
    private final JPanel floorViewContainer = new JPanel(floorCardsLayout);
    private final JLabel floorDetailsTitle = new JLabel("Floor Inspector");
    private final JLabel floorDetailsBody = new JLabel("Click on any table on the blueprint to inspect real-time status and bookings.");
    private JButton floorRefreshBtn;
    private JButton editLayoutBtn;
    private JButton saveLayoutBtn;
    private JButton autoArrangeBtn;
    private JButton toggleMaintBtn;
    private JButton quickReserveBtn;
    private JButton guestProfileBtn;
    private JButton walkInBtn;
    private ReservationDetail currentSelectedReservation = null;
    private ChipToggle planChip;
    private ChipToggle gridChip;
    private List<TableAvailability> currentFloorStates = new ArrayList<>();
    private RestaurantTable selectedFloorTable = null;

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

        tableJTable = new StyledTable(tableModel).emptyMessage("No tables configured yet.");

        // Center ID and Capacity
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        tableJTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tableJTable.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);

        // Status column uses custom rounded pill renderer
        tableJTable.getColumnModel().getColumn(4).setCellRenderer(new StatusPillRenderer());

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
        if (floorSlotButtons.isEmpty()) {
            loadFloorSlots();
        } else {
            loadFloorView();
        }
    }

    private void initTabs() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(Theme.FONT_BODY_BOLD);
        tabbedPane.putClientProperty("JTabbedPane.tabType", "underlined");
        tabbedPane.putClientProperty("JTabbedPane.tabAreaInsets", new java.awt.Insets(0, 0, 0, 0));
        tabbedPane.putClientProperty("JTabbedPane.tabsPopupPolicy", "never");

        // Tab 1: Floor View (Primary service view)
        tabbedPane.addTab("Floor View", createFloorViewTab());

        // Tab 2: Manage Tables (Manager CRUD)
        JPanel manageTab = new JPanel(new BorderLayout(Theme.SPACE_24, 0));
        manageTab.setOpaque(false);
        manageTab.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_16, 0, 0, 0));

        manageTab.add(createFormCard(), BorderLayout.WEST);
        manageTab.add(createTableCard(), BorderLayout.CENTER);

        tabbedPane.addTab("Table Inventory", manageTab);

        contentPanel.add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createFormCard() {
        JPanel card = createCardPanel();
        card.setPreferredSize(new Dimension(380, 0));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Table Details");
        title.setFont(Theme.FONT_SECTION);
        title.setForeground(Theme.TEXT_PRIMARY);
        title.setAlignmentX(0.0f);
        card.add(title);
        card.add(Box.createVerticalStrut(Theme.SPACE_16));

        // Table Number
        card.add(createFieldLabel("Table Number * (e.g. T01)"));
        card.add(Box.createVerticalStrut(6));
        Form.input(tableNumberField);
        tableNumberField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. T01");
        card.add(tableNumberField);
        styleErrorLabel(tableNumberErrorLabel);
        card.add(tableNumberErrorLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_8));

        // Capacity
        card.add(createFieldLabel("Seating Capacity * (1–20)"));
        card.add(Box.createVerticalStrut(6));
        Form.input(capacitySpinner);
        card.add(capacitySpinner);
        styleErrorLabel(capacityErrorLabel);
        card.add(capacityErrorLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_8));

        // Location
        card.add(createFieldLabel("Location / Section *"));
        card.add(Box.createVerticalStrut(6));
        locationComboBox.setEditable(true);
        Form.input(locationComboBox);
        card.add(locationComboBox);
        styleErrorLabel(locationErrorLabel);
        card.add(locationErrorLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_8));

        // Status
        card.add(createFieldLabel("Operational Status *"));
        card.add(Box.createVerticalStrut(6));
        Form.input(statusComboBox);
        card.add(statusComboBox);
        card.add(Box.createVerticalStrut(Theme.SPACE_24));

        // Buttons
        JPanel buttonRow1 = new ChipRow(8);
        buttonRow1.setOpaque(false);
        buttonRow1.setAlignmentX(0.0f);

        addBtn = createPrimaryButton("Add Table");
        addBtn.addActionListener(e -> onAddTable());
        buttonRow1.add(addBtn);

        updateBtn = createSecondaryButton("Update");
        updateBtn.setEnabled(false);
        updateBtn.addActionListener(e -> onUpdateTable());
        buttonRow1.add(updateBtn);

        JPanel buttonRow2 = new ChipRow(8);
        buttonRow2.setOpaque(false);
        buttonRow2.setAlignmentX(0.0f);

        deleteBtn = createDangerButton("Delete");
        deleteBtn.setEnabled(false);
        deleteBtn.addActionListener(e -> onDeleteTable());
        buttonRow2.add(deleteBtn);

        clearBtn = createSecondaryButton("Clear");
        clearBtn.addActionListener(e -> clearForm());
        buttonRow2.add(clearBtn);

        for (JPanel r : new JPanel[]{buttonRow1, buttonRow2}) {
            for (java.awt.Component c : r.getComponents()) {
                if (c instanceof AppButton b) b.fixedWidth(162);
            }
        }
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
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.NEUTRAL_TINT, 1));
        scrollPane.getViewport().setBackground(Theme.CARD_BG);
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
        if (tableJTable instanceof StyledTable st) st.reveal();
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
        TableStatus newStatus = (TableStatus) statusComboBox.getSelectedItem();
        if (newStatus == TableStatus.MAINTENANCE || newStatus == TableStatus.INACTIVE) {
            // the bookings lookup is a database call: keep it off the UI thread
            final int tableId = selectedTable.getTableId();
            ((AppButton) updateBtn).setLoading(true);
            Ui.async(
                    () -> tableService.getFutureBlockingReservations(tableId),
                    future -> {
                        ((AppButton) updateBtn).setLoading(false);
                        if (!future.isEmpty()) {
                            int proceed = Dialogs.showConfirmDialog(
                                    this,
                                    "Table " + selectedTable.getTableNumber() + " has " + future.size() +
                                    " upcoming active reservation(s).\n" +
                                    "Setting it to " + newStatus + " takes it out of service.\n" +
                                    "Please reassign or contact these guests.\n\n" +
                                    "Do you wish to proceed?",
                                    "Table Has Upcoming Bookings",
                                    Dialogs.YES_NO_OPTION,
                                    Dialogs.WARNING_MESSAGE
                            );
                            if (proceed != Dialogs.YES_OPTION) {
                                return;
                            }
                        }
                        applyTableUpdate(newStatus);
                    },
                    error -> {
                        ((AppButton) updateBtn).setLoading(false);
                        handleFormError(error);
                    }
            );
            return;
        }
        applyTableUpdate(newStatus);
    }

    private void applyTableUpdate(TableStatus newStatus) {
        selectedTable.setStatus(newStatus);

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

        int confirm = Dialogs.showConfirmDialog(
                this,
                "Are you sure you want to delete table " + selectedTable.getTableNumber() + "?\n" +
                "Note: If this table has active or past reservations, please set its status to INACTIVE instead.",
                "Confirm Table Deletion",
                Dialogs.YES_NO_OPTION,
                Dialogs.WARNING_MESSAGE
        );

        if (confirm != Dialogs.YES_OPTION) return;

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
                        Dialogs.showMessageDialog(
                                this,
                                error.getMessage(),
                                "Cannot Delete Table",
                                Dialogs.ERROR_MESSAGE
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
        return new Card(Theme.SPACE_24);
    }

    private JLabel createFieldLabel(String text) {
        return Form.label(text);
    }

    private void styleErrorLabel(JLabel label) {
        label.setFont(Theme.FONT_SMALL);
        label.setForeground(Theme.DANGER);
        label.setAlignmentX(0.0f);
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

    private JPanel createFloorViewTab() {
        JPanel floorPanel = new JPanel(new BorderLayout(0, Theme.SPACE_8));
        floorPanel.setOpaque(false);
        floorPanel.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_8, 0, 0, 0));

        // 1. Sleek, unified top command ribbon (~44px height)
        JPanel topRibbon = new JPanel(new BorderLayout(Theme.SPACE_12, 0));
        topRibbon.setBackground(Theme.CARD_BG);
        topRibbon.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.CARD_BORDER, 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        // Left Controls: Date Picker + Today shortcut + Seating Slot Chips
        JPanel leftControls = new ChipRow(Theme.SPACE_8);
        leftControls.setOpaque(false);

        JLabel dateLabel = new JLabel("Date:");
        dateLabel.setFont(Theme.FONT_BODY_BOLD);
        dateLabel.setForeground(Theme.TEXT_PRIMARY);
        leftControls.add(dateLabel);
        leftControls.add(floorDateSelector);

        JButton todayBtn = createSecondaryButton("Today");
        todayBtn.addActionListener(e -> floorDateSelector.setDate(util.DbClock.today()));
        leftControls.add(todayBtn);

        JSeparator sep1 = new JSeparator(javax.swing.SwingConstants.VERTICAL);
        sep1.setPreferredSize(new Dimension(1, 22));
        leftControls.add(sep1);

        JLabel slotLabel = new JLabel("Seating:");
        slotLabel.setFont(Theme.FONT_BODY_BOLD);
        slotLabel.setForeground(Theme.TEXT_PRIMARY);
        leftControls.add(slotLabel);
        leftControls.add(floorSlotsPanel);

        walkInBtn = createSecondaryButton("⚡ Walk-In");
        walkInBtn.setToolTipText("Immediately seat walk-in guests into current slot");
        walkInBtn.addActionListener(e -> onWalkInClicked());
        leftControls.add(walkInBtn);

        topRibbon.add(leftControls, BorderLayout.WEST);

        // Right Controls: Visual Legend + Blueprint/Grid Toggle + Layout Editor & Refresh
        JPanel rightControls = new ChipRow(Theme.SPACE_8, true);
        rightControls.setOpaque(false);

        rightControls.add(createLegendDot(new Color(22, 163, 74), "Available"));
        rightControls.add(createLegendDot(new Color(220, 38, 38), "Reserved"));
        rightControls.add(createLegendDot(new Color(217, 119, 6), "Maint"));

        JSeparator sep2 = new JSeparator(javax.swing.SwingConstants.VERTICAL);
        sep2.setPreferredSize(new Dimension(1, 22));
        rightControls.add(sep2);

        planChip = new ChipToggle("Floor Blueprint", ChipToggle.Style.DARK).minWidth(100);
        gridChip = new ChipToggle("Card Grid", ChipToggle.Style.DARK).minWidth(88);
        ButtonGroup viewGroup = new ButtonGroup();
        viewGroup.add(planChip);
        viewGroup.add(gridChip);
        planChip.setSelected(true);
        planChip.addActionListener(e -> {
            floorCardsLayout.show(floorViewContainer, "PLAN");
            editLayoutBtn.setEnabled(true);
        });
        gridChip.addActionListener(e -> {
            floorCardsLayout.show(floorViewContainer, "GRID");
            editLayoutBtn.setEnabled(false);
            saveLayoutBtn.setVisible(false);
            autoArrangeBtn.setVisible(false);
        });

        editLayoutBtn = createSecondaryButton("✎ Edit Layout");
        editLayoutBtn.addActionListener(e -> {
            boolean isEdit = !floorPlanPanel.isEditMode();
            floorPlanPanel.setEditMode(isEdit);
            editLayoutBtn.setText(isEdit ? "✔ Finish Editing" : "✎ Edit Layout");
            saveLayoutBtn.setVisible(isEdit);
            autoArrangeBtn.setVisible(isEdit);
            if (isEdit) {
                Toast.info(SwingUtilities.getWindowAncestor(this), "Drag tables on the grid to reposition them. Snap is 24px.");
            }
        });

        saveLayoutBtn = createPrimaryButton("Save Layout");
        saveLayoutBtn.setVisible(false);
        saveLayoutBtn.addActionListener(e -> {
            final java.util.List<RestaurantTable> snapshot = floorPlanPanel.getTables();
            ((AppButton) saveLayoutBtn).setLoading(true);
            Ui.async(
                    () -> {
                        tableService.updateLayoutPositions(snapshot);
                        return Boolean.TRUE;
                    },
                    ok -> {
                        ((AppButton) saveLayoutBtn).setLoading(false);
                        floorPlanPanel.setLayoutModified(false);
                        Toast.success(SwingUtilities.getWindowAncestor(this), "Floor plan layout saved");
                    },
                    ex -> {
                        ((AppButton) saveLayoutBtn).setLoading(false);
                        Toast.error(SwingUtilities.getWindowAncestor(this), "Failed to save layout: " + ex.getMessage());
                    }
            );
        });

        autoArrangeBtn = createSecondaryButton("Auto-Arrange");
        autoArrangeBtn.setVisible(false);
        autoArrangeBtn.addActionListener(e -> {
            floorPlanPanel.resetAutoLayout();
            Toast.info(SwingUtilities.getWindowAncestor(this), "Tables reset to zone layout. Click 'Save Layout' to persist.");
        });

        floorRefreshBtn = createSecondaryButton("↻ Refresh");
        floorRefreshBtn.addActionListener(e -> loadFloorView());

        boolean canEditLayout = security.Session.getInstance().hasPermission(security.Permission.FLOOR_LAYOUT_EDIT);
        editLayoutBtn.setVisible(canEditLayout);
        if (canEditLayout) {
            rightControls.add(autoArrangeBtn);
            rightControls.add(saveLayoutBtn);
            rightControls.add(editLayoutBtn);
        }
        rightControls.add(planChip);
        rightControls.add(gridChip);
        rightControls.add(floorRefreshBtn);

        topRibbon.add(rightControls, BorderLayout.EAST);
        floorPanel.add(topRibbon, BorderLayout.NORTH);

        // 2. Center: Max-Space Canvas Viewport (Blueprint + Grid)
        JScrollPane planScroll = new JScrollPane(floorPlanPanel);
        planScroll.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        planScroll.getViewport().setBackground(new Color(248, 249, 250));
        planScroll.getVerticalScrollBar().setUnitIncrement(16);
        planScroll.getHorizontalScrollBar().setUnitIncrement(16);

        JScrollPane gridScroll = new JScrollPane(floorGridPanel);
        gridScroll.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        gridScroll.getViewport().setBackground(Theme.CARD_BG);
        gridScroll.getVerticalScrollBar().setUnitIncrement(16);

        floorViewContainer.removeAll();
        floorViewContainer.add(planScroll, "PLAN");
        floorViewContainer.add(gridScroll, "GRID");
        floorCardsLayout.show(floorViewContainer, "PLAN");

        floorPanel.add(floorViewContainer, BorderLayout.CENTER);

        // Listen for table selection
        floorGridPanel.setOnAnyCardClicked(this::onFloorTableClicked);
        floorPlanPanel.setOnTableSelected(tbl -> {
            if (tbl == null) {
                onFloorTableClicked(null);
                return;
            }
            TableAvailability match = currentFloorStates.stream()
                    .filter(a -> a.getTable() != null && a.getTable().getTableId() == tbl.getTableId())
                    .findFirst()
                    .orElse(new TableAvailability(tbl, tbl.getStatus() == TableStatus.MAINTENANCE ? TableState.MAINTENANCE : TableState.AVAILABLE));
            onFloorTableClicked(match);
        });

        // 3. Compact Docked Inspector Bar (~46px) at Bottom
        JPanel inspectorBar = new JPanel(new BorderLayout(Theme.SPACE_16, 0));
        inspectorBar.setBackground(Theme.CARD_BG);
        inspectorBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.CARD_BORDER, 1),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)
        ));

        JPanel inspectorLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_8, 0));
        inspectorLeft.setOpaque(false);

        JLabel iconLabel = new JLabel("🍽️");
        iconLabel.setFont(Theme.FONT_BODY);
        inspectorLeft.add(iconLabel);

        floorDetailsTitle.setFont(Theme.FONT_BODY_BOLD);
        floorDetailsTitle.setForeground(Theme.TEXT_PRIMARY);
        inspectorLeft.add(floorDetailsTitle);

        JSeparator sep3 = new JSeparator(javax.swing.SwingConstants.VERTICAL);
        sep3.setPreferredSize(new Dimension(1, 20));
        inspectorLeft.add(sep3);

        floorDetailsBody.setFont(Theme.FONT_BODY);
        floorDetailsBody.setForeground(Theme.TEXT_MUTED);
        inspectorLeft.add(floorDetailsBody);

        inspectorBar.add(inspectorLeft, BorderLayout.WEST);

        // Quick Actions for Table
        JPanel inspectorRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_8, 0));
        inspectorRight.setOpaque(false);

        toggleMaintBtn = createSecondaryButton("Toggle Maintenance");
        toggleMaintBtn.setVisible(false);
        toggleMaintBtn.addActionListener(e -> onToggleTableMaintenance());
        inspectorRight.add(toggleMaintBtn);

        quickReserveBtn = createPrimaryButton("Reserve Table");
        quickReserveBtn.setVisible(false);
        quickReserveBtn.addActionListener(e -> onQuickReserveClicked());
        inspectorRight.add(quickReserveBtn);

        guestProfileBtn = createSecondaryButton("👤 Guest 360° Profile");
        guestProfileBtn.setVisible(false);
        guestProfileBtn.addActionListener(e -> onGuestProfileClicked());
        inspectorRight.add(guestProfileBtn);

        inspectorBar.add(inspectorRight, BorderLayout.EAST);
        floorPanel.add(inspectorBar, BorderLayout.SOUTH);

        // Listen for date changes
        floorDateSelector.setOnDateChanged(d -> loadFloorView());

        return floorPanel;
    }

    private JPanel createLegendDot(Color color, String labelText) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        JLabel dot = new JLabel("●");
        dot.setForeground(color);
        dot.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JLabel text = new JLabel(labelText);
        text.setFont(Theme.FONT_SMALL);
        text.setForeground(Theme.TEXT_MUTED);
        p.add(dot);
        p.add(text);
        return p;
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
                () -> {
                    List<TableAvailability> states = reservationService.getTableStates(date, selectedSlot.getSlotId());
                    List<RestaurantTable> allTables = tableService.getAllTables();
                    return new Object[]{states, allTables};
                },
                result -> {
                    if (floorRefreshBtn != null) {
                        floorRefreshBtn.setEnabled(true);
                        floorRefreshBtn.setText("Refresh Floor");
                    }
                    @SuppressWarnings("unchecked")
                    List<TableAvailability> states = (List<TableAvailability>) result[0];
                    @SuppressWarnings("unchecked")
                    List<RestaurantTable> allTables = (List<RestaurantTable>) result[1];

                    currentFloorStates = states;
                    floorGridPanel.setData(states, true);
                    floorPlanPanel.setData(allTables, states);

                    if (selectedFloorTable != null) {
                        TableAvailability match = states.stream()
                                .filter(a -> a.getTable() != null && a.getTable().getTableId() == selectedFloorTable.getTableId())
                                .findFirst()
                                .orElse(null);
                        onFloorTableClicked(match);
                    } else {
                        onFloorTableClicked(null);
                    }
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
        currentSelectedReservation = null;
        if (ta == null || ta.getTable() == null) {
            selectedFloorTable = null;
            floorDetailsTitle.setText("Floor Inspector");
            floorDetailsBody.setText("Click on any table on the blueprint to inspect real-time status and bookings.");
            if (toggleMaintBtn != null) toggleMaintBtn.setVisible(false);
            if (quickReserveBtn != null) quickReserveBtn.setVisible(false);
            if (guestProfileBtn != null) guestProfileBtn.setVisible(false);
            return;
        }

        RestaurantTable t = ta.getTable();
        selectedFloorTable = t;
        TableState state = ta.getState();
        LocalDate date = floorDateSelector.getDate();
        TimeSlot slot = getSelectedFloorSlot();

        floorDetailsTitle.setText(String.format("Table %s · %d seats · %s", t.getTableNumber(), t.getCapacity(), t.getLocation()));

        if (state == TableState.AVAILABLE) {
            floorDetailsBody.setText("<html><b style='color:#16A34A;'>AVAILABLE</b> — Ready for walk-in dining or booking during this seating.</html>");
            if (toggleMaintBtn != null) {
                toggleMaintBtn.setVisible(true);
                toggleMaintBtn.setText("Set Maintenance");
            }
            if (quickReserveBtn != null) {
                quickReserveBtn.setVisible(true);
                quickReserveBtn.setText("Reserve Table");
            }
            if (guestProfileBtn != null) guestProfileBtn.setVisible(false);
        } else if (state == TableState.MAINTENANCE) {
            floorDetailsBody.setText("<html><b style='color:#D97706;'>MAINTENANCE</b> — Table is currently offline for maintenance/service.</html>");
            if (toggleMaintBtn != null) {
                toggleMaintBtn.setVisible(true);
                toggleMaintBtn.setText("Clear Maintenance");
            }
            if (quickReserveBtn != null) quickReserveBtn.setVisible(false);
            if (guestProfileBtn != null) guestProfileBtn.setVisible(false);
        } else if (state == TableState.SEATED) {
            if (toggleMaintBtn != null) toggleMaintBtn.setVisible(false);
            if (quickReserveBtn != null) quickReserveBtn.setVisible(false);

            ReservationDetail activeRes = ta.getActiveReservation();
            if (activeRes != null) {
                currentSelectedReservation = activeRes;
                if (guestProfileBtn != null) guestProfileBtn.setVisible(true);
                long elapsed = (activeRes.getSeatedAt() != null) ? java.time.Duration.between(activeRes.getSeatedAt(), util.DbClock.localDateTime()).toMinutes() : 0;
                floorDetailsBody.setText(String.format(
                        "<html><b style='color:#1E3A8A;'>● SEATED</b> — <b>%s</b> · Customer: <b>%s</b> (%s) · ⏱️ <b>%d mins elapsed</b> · %d Guests</html>",
                        activeRes.getDisplayCode(),
                        activeRes.getCustomerName(),
                        activeRes.getPhone(),
                        Math.max(0, elapsed),
                        activeRes.getGuestCount()
                ));
            } else {
                floorDetailsBody.setText("<html><b style='color:#1E3A8A;'>● SEATED</b> — Guests currently dining at this table.</html>");
            }
        } else if (state == TableState.RESERVED) {
            if (toggleMaintBtn != null) toggleMaintBtn.setVisible(false);
            if (quickReserveBtn != null) quickReserveBtn.setVisible(false);

            ReservationDetail activeRes = ta.getActiveReservation();
            if (activeRes != null) {
                currentSelectedReservation = activeRes;
                if (guestProfileBtn != null) guestProfileBtn.setVisible(true);
                floorDetailsBody.setText(String.format(
                        "<html><b style='color:#DC2626;'>● RESERVED</b> — <b>%s</b> · Customer: <b>%s</b> (Phone: %s) · Guests: <b>%d</b> · %s</html>",
                        activeRes.getDisplayCode(),
                        activeRes.getCustomerName(),
                        activeRes.getPhone(),
                        activeRes.getGuestCount(),
                        activeRes.getStatus() != null ? activeRes.getStatus().name() : "CONFIRMED"
                ));
            } else {
                if (slot == null || date == null) {
                    floorDetailsBody.setText("<html><b style='color:#DC2626;'>RESERVED</b> — Booked for this seating.</html>");
                    return;
                }
                floorDetailsBody.setText("<html><b style='color:#DC2626;'>RESERVED</b> — Fetching reservation details...</html>");

                Ui.async(
                        () -> reservationService.getReservationForSlot(date, slot.getSlotId(), t.getTableId()),
                        opt -> {
                            if (opt.isPresent()) {
                                ReservationDetail r = opt.get();
                                currentSelectedReservation = r;
                                if (guestProfileBtn != null) guestProfileBtn.setVisible(true);
                                floorDetailsBody.setText(String.format(
                                        "<html><b style='color:#DC2626;'>RESERVED</b> — <b>%s</b> · Customer: <b>%s</b> (Phone: %s) · Guests: <b>%d</b> · %s</html>",
                                        r.getDisplayCode(),
                                        r.getCustomerName(),
                                        r.getPhone(),
                                        r.getGuestCount(),
                                        r.getStatus() != null ? r.getStatus().name() : "CONFIRMED"
                                ));
                            } else {
                                floorDetailsBody.setText("<html><b style='color:#DC2626;'>RESERVED</b> — Booked for this seating.</html>");
                            }
                        },
                        err -> floorDetailsBody.setText("<html><b style='color:#DC2626;'>RESERVED</b> — Booked for this seating.</html>")
                );
            }
        }
    }

    private void onToggleTableMaintenance() {
        if (selectedFloorTable == null) return;
        TableStatus currentStatus = selectedFloorTable.getStatus();
        TableStatus newStatus = (currentStatus == TableStatus.MAINTENANCE) ? TableStatus.AVAILABLE : TableStatus.MAINTENANCE;
        selectedFloorTable.setStatus(newStatus);

        Ui.async(
                () -> {
                    tableService.updateTable(selectedFloorTable);
                    return true;
                },
                ok -> {
                    Toast.success(SwingUtilities.getWindowAncestor(this),
                            "Table " + selectedFloorTable.getTableNumber() + " is now " + newStatus.name());
                    loadFloorView();
                    loadTables();
                },
                err -> {
                    selectedFloorTable.setStatus(currentStatus);
                    Toast.error(SwingUtilities.getWindowAncestor(this), "Failed: " + err.getMessage());
                }
        );
    }

    private void onQuickReserveClicked() {
        if (selectedFloorTable == null) return;
        MainFrame mf = (MainFrame) SwingUtilities.getAncestorOfClass(MainFrame.class, this);
        if (mf != null) {
            if (mf.getNewReservationPanel() != null) {
                mf.getNewReservationPanel().prefillBooking(
                        floorDateSelector.getDate(),
                        getSelectedFloorSlot(),
                        selectedFloorTable
                );
            }
            mf.showScreen("NEW_RESERVATION");
            Toast.info(mf, "Table " + selectedFloorTable.getTableNumber() + " (" + selectedFloorTable.getLocation() + ") selected. Complete guest details.");
        }
    }

    private void onGuestProfileClicked() {
        if (currentSelectedReservation == null) return;
        Ui.async(
                () -> {
                    List<Customer> found = new service.CustomerService().searchCustomers(currentSelectedReservation.getPhone());
                    if (!found.isEmpty()) return found.get(0);
                    return new service.CustomerService().searchCustomers(currentSelectedReservation.getCustomerName()).stream().findFirst().orElse(null);
                },
                cust -> {
                    if (cust != null) {
                        CustomerDetailDialog.show(
                                SwingUtilities.getWindowAncestor(this),
                                cust,
                                new service.CustomerService(),
                                reservationService,
                                updated -> loadFloorView(),
                                bookedCust -> {
                                    MainFrame mf = (MainFrame) SwingUtilities.getAncestorOfClass(MainFrame.class, this);
                                    if (mf != null) mf.showScreen("NEW_RESERVATION");
                                }
                        );
                    } else {
                        Toast.info(SwingUtilities.getWindowAncestor(this), "Customer profile not found for " + currentSelectedReservation.getCustomerName());
                    }
                },
                err -> Toast.error(SwingUtilities.getWindowAncestor(this), err.getMessage())
        );
    }

    private void onWalkInClicked() {
        java.awt.Frame topFrame = (java.awt.Frame) SwingUtilities.getWindowAncestor(this);
        WalkInDialog dlg = new WalkInDialog(topFrame, reservationService, new service.CustomerService(), tableService, this::loadFloorView);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private static class FloorSlotToggleButton extends ChipToggle {
        private final TimeSlot slot;

        public FloorSlotToggleButton(TimeSlot slot) {
            super(slot.getLabel(), ChipToggle.Style.GOLD);
            this.slot = slot;
            minWidth(100);
        }

        public TimeSlot getSlot() {
            return slot;
        }
    }
}
