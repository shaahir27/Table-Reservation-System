package ui;

import exception.ValidationException;
import model.ReservationDetail;
import model.ReservationFilter;
import model.ReservationStatus;
import model.RestaurantTable;
import model.TimeSlot;
import service.ReservationService;
import service.TableService;
import ui.components.StatusPillRenderer;
import ui.components.Toast;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Reservation Management and Operations Panel (Section 10.4).
 * Supports parameterized multi-criteria searching, status filtering,
 * cancellation with double-booking index release, and completion.
 */
public class ReservationsPanel extends BasePanel {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final ReservationService reservationService;
    private final TableService tableService;

    // Filter controls
    private final JTextField searchTextField = new JTextField();
    private final JCheckBox dateFilterCheckBox = new JCheckBox("Date:");
    private final JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
    private final JComboBox<String> statusFilterCombo = new JComboBox<>(new String[]{"ALL", "CONFIRMED", "COMPLETED", "CANCELLED"});
    private final JComboBox<String> tableFilterCombo = new JComboBox<>();
    private final JButton searchBtn;
    private final JButton resetBtn;

    // Table & Results
    private final DefaultTableModel tableModel;
    private final JTable resultsTable;
    private final JLabel countFooterLabel = new JLabel("0 reservations found");
    private final List<ReservationDetail> currentResults = new ArrayList<>();

    // Actions
    private final JButton cancelBtn;
    private final JButton markCompletedBtn;

    public ReservationsPanel() {
        this(new ReservationService(), new TableService());
    }

    public ReservationsPanel(ReservationService reservationService, TableService tableService) {
        super("Reservations & History");
        this.reservationService = reservationService;
        this.tableService = tableService;

        // Date spinner styling
        JSpinner.DateEditor editor = new JSpinner.DateEditor(dateSpinner, "dd-MM-yyyy");
        dateSpinner.setEditor(editor);
        dateSpinner.setEnabled(false);
        dateFilterCheckBox.setFont(Theme.FONT_BODY_BOLD);
        dateFilterCheckBox.setForeground(Theme.TEXT_PRIMARY);
        dateFilterCheckBox.addActionListener(e -> dateSpinner.setEnabled(dateFilterCheckBox.isSelected()));

        searchBtn = createPrimaryButton("Search");
        resetBtn = createSecondaryButton("Reset");

        cancelBtn = createDangerButton("Cancel Reservation");
        cancelBtn.setEnabled(false);

        markCompletedBtn = createSecondaryButton("Mark Completed");
        markCompletedBtn.setEnabled(false);

        // Setup results table
        tableModel = new DefaultTableModel(new String[]{
                "ID", "Customer", "Phone", "Table", "Date", "Time Slot", "Guests", "Status"
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        resultsTable = new JTable(tableModel);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.setRowHeight(38);
        resultsTable.getTableHeader().setPreferredSize(new Dimension(0, 40));
        resultsTable.getTableHeader().setFont(Theme.FONT_BODY_BOLD);
        resultsTable.setFont(Theme.FONT_BODY);

        // Center alignments
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        resultsTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        resultsTable.getColumnModel().getColumn(0).setMaxWidth(90);
        resultsTable.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        resultsTable.getColumnModel().getColumn(3).setMaxWidth(80);
        resultsTable.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);
        resultsTable.getColumnModel().getColumn(6).setMaxWidth(70);

        resultsTable.getColumnModel().getColumn(7).setCellRenderer(new StatusPillRenderer());
        resultsTable.getColumnModel().getColumn(7).setMinWidth(120);

        // Row selection listener to enable/disable action buttons
        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateActionButtonsState();
            }
        });

        setupEvents();
        initLayout();
    }

    @Override
    public void onShown() {
        loadTableOptions();
        performSearch();
    }

    private void setupEvents() {
        searchBtn.addActionListener(e -> performSearch());
        searchTextField.addActionListener(e -> performSearch());

        resetBtn.addActionListener(e -> {
            searchTextField.setText("");
            dateFilterCheckBox.setSelected(false);
            dateSpinner.setEnabled(false);
            dateSpinner.setValue(new Date());
            statusFilterCombo.setSelectedIndex(0);
            if (tableFilterCombo.getItemCount() > 0) {
                tableFilterCombo.setSelectedIndex(0);
            }
            performSearch();
        });

        cancelBtn.addActionListener(e -> onCancelSelectedReservation());
        markCompletedBtn.addActionListener(e -> onMarkCompletedSelectedReservation());
    }

    private void initLayout() {
        JPanel mainLayout = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        mainLayout.setOpaque(false);

        // 1. Filter Card
        JPanel filterCard = createCardPanel();
        filterCard.setLayout(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_12, Theme.SPACE_8));

        searchTextField.setPreferredSize(new Dimension(240, 36));
        searchTextField.putClientProperty("JTextField.placeholderText", "Search ID, name, phone, table...");

        filterCard.add(searchTextField);
        filterCard.add(dateFilterCheckBox);
        dateSpinner.setPreferredSize(new Dimension(120, 36));
        filterCard.add(dateSpinner);

        JLabel statusLabel = new JLabel("Status:");
        statusLabel.setFont(Theme.FONT_BODY_BOLD);
        statusLabel.setForeground(Theme.TEXT_PRIMARY);
        filterCard.add(statusLabel);
        statusFilterCombo.setPreferredSize(new Dimension(130, 36));
        filterCard.add(statusFilterCombo);

        JLabel tableLabel = new JLabel("Table:");
        tableLabel.setFont(Theme.FONT_BODY_BOLD);
        tableLabel.setForeground(Theme.TEXT_PRIMARY);
        filterCard.add(tableLabel);
        tableFilterCombo.setPreferredSize(new Dimension(110, 36));
        filterCard.add(tableFilterCombo);

        filterCard.add(searchBtn);
        filterCard.add(resetBtn);

        mainLayout.add(filterCard, BorderLayout.NORTH);

        // 2. Results Card
        JPanel resultsCard = createCardPanel();
        resultsCard.setLayout(new BorderLayout(0, Theme.SPACE_12));

        JScrollPane scrollPane = new JScrollPane(resultsTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        scrollPane.getViewport().setBackground(Theme.CARD_BG);
        resultsCard.add(scrollPane, BorderLayout.CENTER);

        // 3. Action bar & Footer
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);

        countFooterLabel.setFont(Theme.FONT_BODY_BOLD);
        countFooterLabel.setForeground(Theme.TEXT_MUTED);
        bottomBar.add(countFooterLabel, BorderLayout.WEST);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_12, 0));
        buttonBar.setOpaque(false);
        buttonBar.add(markCompletedBtn);
        buttonBar.add(cancelBtn);
        bottomBar.add(buttonBar, BorderLayout.EAST);

        resultsCard.add(bottomBar, BorderLayout.SOUTH);
        mainLayout.add(resultsCard, BorderLayout.CENTER);

        contentPanel.add(mainLayout, BorderLayout.CENTER);
    }

    private void loadTableOptions() {
        tableFilterCombo.removeAllItems();
        tableFilterCombo.addItem("All Tables");
        Ui.async(
                tableService::getAllTables,
                tables -> {
                    for (RestaurantTable t : tables) {
                        tableFilterCombo.addItem(t.getTableNumber());
                    }
                },
                null
        );
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
            filter.setStatus(ReservationStatus.valueOf(selectedStatus));
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
            cancelBtn.setEnabled(false);
            markCompletedBtn.setEnabled(false);
            return;
        }

        ReservationDetail detail = currentResults.get(selectedRow);
        boolean isConfirmed = detail.getStatus() == ReservationStatus.CONFIRMED;

        // Cancel enabled only for CONFIRMED
        cancelBtn.setEnabled(isConfirmed);

        // Mark Completed enabled only for CONFIRMED whose slot has begun or is in the past
        boolean eligibleForCompletion = false;
        if (isConfirmed) {
            LocalDate today = LocalDate.now();
            if (detail.getReservationDate().isBefore(today)) {
                eligibleForCompletion = true;
            } else if (detail.getReservationDate().isEqual(today)) {
                // Check slot start time against now
                eligibleForCompletion = isSlotStarted(detail.getSlotLabel());
            }
        }
        markCompletedBtn.setEnabled(eligibleForCompletion);
    }

    private boolean isSlotStarted(String slotLabel) {
        if (slotLabel == null) return false;
        try {
            // Find slot matching label
            List<TimeSlot> slots = reservationService.getTimeSlots();
            for (TimeSlot s : slots) {
                if (slotLabel.contains(s.getStartTime().format(DateTimeFormatter.ofPattern("hh:mm a"))) ||
                    slotLabel.equalsIgnoreCase(s.getLabel())) {
                    return !s.getStartTime().isAfter(LocalTime.now());
                }
            }
        } catch (Exception ignored) {
        }
        return false;
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

        int confirm = JOptionPane.showConfirmDialog(
                this,
                message,
                "Confirm Cancellation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            cancelBtn.setEnabled(false);
            cancelBtn.setText("Cancelling...");

            Ui.async(
                    () -> {
                        reservationService.cancelReservation(detail.getReservationId());
                        return null;
                    },
                    res -> {
                        cancelBtn.setText("Cancel Reservation");
                        Toast.success(SwingUtilities.getWindowAncestor(this), "Reservation " + detail.getDisplayCode() + " cancelled successfully.");
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

    private void onMarkCompletedSelectedReservation() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentResults.size()) return;

        ReservationDetail detail = currentResults.get(selectedRow);

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Mark reservation " + detail.getDisplayCode() + " as COMPLETED?",
                "Complete Reservation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            markCompletedBtn.setEnabled(false);
            markCompletedBtn.setText("Updating...");

            Ui.async(
                    () -> {
                        reservationService.markCompleted(detail.getReservationId());
                        return null;
                    },
                    res -> {
                        markCompletedBtn.setText("Mark Completed");
                        Toast.success(SwingUtilities.getWindowAncestor(this), "Reservation " + detail.getDisplayCode() + " marked as COMPLETED.");
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
        card.setBorder(BorderFactory.createEmptyBorder(16, Theme.SPACE_24, 16, Theme.SPACE_24));
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
        btn.setPreferredSize(new Dimension(110, 36));
        return btn;
    }

    private JButton createSecondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(Theme.FONT_BODY);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(140, 36));
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
        btn.setPreferredSize(new Dimension(160, 36));
        return btn;
    }
}
