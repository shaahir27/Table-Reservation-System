package ui.components;

import com.formdev.flatlaf.FlatClientProperties;
import exception.ValidationException;
import model.Customer;
import model.ReservationDetail;
import model.ReservationStatus;
import service.CustomerService;
import service.ReservationService;
import ui.Theme;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Customer 360° Floating Profile & Quick Editor Modal.
 * Displays customer demographics, interactive hospitality tags, special preferences,
 * editable contact info, and complete reservation visit history.
 */
public class CustomerDetailDialog extends JDialog {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final Customer customer;
    private final CustomerService customerService;
    private final ReservationService reservationService;
    private final Consumer<Customer> onUpdated;
    private final Consumer<Customer> onBookReservation;

    // Form inputs
    private final JTextField nameField = new JTextField();
    private final JTextField phoneField = new JTextField();
    private final JTextField emailField = new JTextField();
    private final JTextArea notesArea = new JTextArea(3, 20);
    private final List<ChipToggle> tagChips = new ArrayList<>();

    // Error labels
    private final JLabel nameErrorLabel = new JLabel(" ");
    private final JLabel phoneErrorLabel = new JLabel(" ");

    // History Table
    private final DefaultTableModel historyTableModel;
    private final JTable historyTable;
    private final JLabel historySummaryLabel = new JLabel("Loading reservation history...");

    private final JLabel subtitleLabel = new JLabel();
    private final JLabel visitsBadge = new JLabel();

    public static void show(Window parent, Customer customer, CustomerService customerService,
                            ReservationService reservationService, Consumer<Customer> onUpdated,
                            Consumer<Customer> onBookReservation) {
        Frame frame = (parent instanceof Frame) ? (Frame) parent : (Frame) SwingUtilities.getAncestorOfClass(Frame.class, parent);
        CustomerDetailDialog dlg = new CustomerDetailDialog(frame, customer, customerService, reservationService, onUpdated, onBookReservation);
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
    }

    public CustomerDetailDialog(Frame parent, Customer customer, CustomerService customerService,
                                ReservationService reservationService, Consumer<Customer> onUpdated,
                                Consumer<Customer> onBookReservation) {
        super(parent, "Customer 360° Profile · " + customer.getName(), true);
        this.customer = customer;
        this.customerService = customerService;
        this.reservationService = reservationService;
        this.onUpdated = onUpdated;
        this.onBookReservation = onBookReservation;

        setSize(720, 620);
        setMinimumSize(new Dimension(640, 500));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Load enriched tags & notes
        if (customer.getTags() == null || customer.getTags().isEmpty()) {
            customer.setTags(customerService.getCustomerTags(customer.getCustomerId()));
        }
        if (customer.getNotes() == null || customer.getNotes().isEmpty()) {
            customer.setNotes(customerService.getCustomerNotes(customer.getCustomerId()));
        }

        JPanel root = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        root.setBackground(Theme.APP_BG);
        root.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_16, Theme.SPACE_20, Theme.SPACE_16, Theme.SPACE_20));

        // 1. Header Card (Avatar + Identity + Stats)
        root.add(createHeaderCard(), BorderLayout.NORTH);

        // 2. Tabbed Body (Profile / History)
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(Theme.FONT_BODY_BOLD);
        tabbedPane.putClientProperty("JTabbedPane.tabType", "underlined");

        // History Table Setup
        historyTableModel = new DefaultTableModel(new String[]{"Code", "Date", "Time Slot", "Table", "Guests", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        historyTable = new StyledTable(historyTableModel).emptyMessage("No reservation history recorded for this customer.");
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        historyTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        historyTable.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        historyTable.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        historyTable.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
        historyTable.getColumnModel().getColumn(5).setCellRenderer(new StatusPillRenderer());

        JScrollPane profileScroll = new JScrollPane(createProfileTab());
        profileScroll.setBorder(null);
        profileScroll.setOpaque(false);
        profileScroll.getViewport().setOpaque(false);
        profileScroll.getVerticalScrollBar().setUnitIncrement(16);
        profileScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        tabbedPane.addTab("Profile & Preferences", profileScroll);
        tabbedPane.addTab("Reservation History", createHistoryTab());

        root.add(tabbedPane, BorderLayout.CENTER);

        // 3. Bottom Action Bar
        root.add(createBottomActionBar(), BorderLayout.SOUTH);

        setContentPane(root);
        loadReservationHistory();
    }

    private JPanel createHeaderCard() {
        JPanel card = new Card(14, 16);
        card.setLayout(new BorderLayout(Theme.SPACE_16, 0));

        // Avatar Initials Circle
        JPanel avatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.ACCENT_GOLD);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(Theme.FONT_TITLE.deriveFont(Font.BOLD, 18f));
                String initials = getInitials(customer.getName());
                int fw = g2.getFontMetrics().stringWidth(initials);
                int fh = g2.getFontMetrics().getAscent();
                g2.drawString(initials, (getWidth() - fw) / 2, (getHeight() + fh) / 2 - 3);
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(54, 54));
        avatar.setOpaque(false);
        card.add(avatar, BorderLayout.WEST);

        // Name + Subtitle
        JPanel infoBox = new JPanel();
        infoBox.setLayout(new BoxLayout(infoBox, BoxLayout.Y_AXIS));
        infoBox.setOpaque(false);

        JLabel nameTitle = new JLabel(customer.getName());
        nameTitle.setFont(Theme.FONT_TITLE);
        nameTitle.setForeground(Theme.TEXT_PRIMARY);
        infoBox.add(nameTitle);

        subtitleLabel.setText("Customer #" + customer.getCustomerId() + " · Phone: " + customer.getPhone() +
                (customer.getEmail() != null && !customer.getEmail().isEmpty() ? " · " + customer.getEmail() : ""));
        subtitleLabel.setFont(Theme.FONT_SMALL);
        subtitleLabel.setForeground(Theme.TEXT_MUTED);
        infoBox.add(subtitleLabel);

        card.add(infoBox, BorderLayout.CENTER);

        // Badges on Right
        JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        rightBox.setOpaque(false);

        visitsBadge.setFont(Theme.FONT_SMALL_BOLD);
        visitsBadge.setForeground(Theme.ACCENT_GOLD);
        visitsBadge.setText("★ Loading visits...");
        rightBox.add(visitsBadge);

        card.add(rightBox, BorderLayout.EAST);
        return card;
    }

    private JPanel createProfileTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_16, Theme.SPACE_8, Theme.SPACE_8, Theme.SPACE_8));

        // Form fields
        nameField.setText(customer.getName());
        Form.input(nameField);
        panel.add(createFieldLabel("Full Name *"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(nameField);
        styleErrorLabel(nameErrorLabel);
        panel.add(nameErrorLabel);
        panel.add(Box.createVerticalStrut(4));

        phoneField.setText(customer.getPhone());
        Form.input(phoneField);
        panel.add(createFieldLabel("Phone Number * (10 digits)"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(phoneField);
        styleErrorLabel(phoneErrorLabel);
        panel.add(phoneErrorLabel);
        panel.add(Box.createVerticalStrut(4));

        emailField.setText(customer.getEmail() != null ? customer.getEmail() : "");
        Form.input(emailField);
        panel.add(createFieldLabel("Email Address"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(emailField);
        panel.add(Box.createVerticalStrut(10));

        // Hospitality Tags Row
        panel.add(createFieldLabel("Hospitality & Service Tags"));
        panel.add(Box.createVerticalStrut(6));

        JPanel tagsRow = new ChipRow(8);
        tagsRow.setOpaque(false);
        tagsRow.setAlignmentX(0.0f);

        String currentTags = customer.getTags() != null ? customer.getTags() : "";
        List<String> activeTags = Arrays.asList(currentTags.split(","));

        String[] availableTags = new String[]{"⭐ VIP", "Regular", "⚠️ Nut Allergy", "⚠️ Gluten Free", "🍷 High Spender", "🪟 Window Seat", "Quiet Booth"};
        for (String tag : availableTags) {
            ChipToggle chip = new ChipToggle(tag, ChipToggle.Style.GOLD);
            chip.setSelected(activeTags.contains(tag));
            tagChips.add(chip);
            tagsRow.add(chip);
        }
        panel.add(tagsRow);
        panel.add(Box.createVerticalStrut(12));

        // Notes Area
        panel.add(createFieldLabel("Special Hospitality & Dietary Notes"));
        panel.add(Box.createVerticalStrut(4));
        notesArea.setText(customer.getNotes() != null ? customer.getNotes() : "");
        notesArea.setFont(Theme.FONT_BODY);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        Form.input(notesArea);
        JScrollPane notesScroll = new JScrollPane(notesArea);
        notesScroll.setPreferredSize(new Dimension(0, 64));
        notesScroll.setAlignmentX(0.0f);
        panel.add(notesScroll);

        return panel;
    }

    private JPanel createHistoryTab() {
        JPanel panel = new JPanel(new BorderLayout(0, Theme.SPACE_8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_12, Theme.SPACE_8, Theme.SPACE_8, Theme.SPACE_8));

        JScrollPane scrollPane = new JScrollPane(historyTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        historySummaryLabel.setFont(Theme.FONT_SMALL);
        historySummaryLabel.setForeground(Theme.TEXT_MUTED);
        panel.add(historySummaryLabel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createBottomActionBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);

        // Left Action: Book New Reservation
        JButton bookBtn = AppButton.primary("+ New Reservation for Guest");
        bookBtn.addActionListener(e -> {
            dispose();
            if (onBookReservation != null) {
                onBookReservation.accept(customer);
            }
        });
        bar.add(bookBtn, BorderLayout.WEST);

        // Right Actions: Close & Save Changes
        JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBox.setOpaque(false);

        JButton closeBtn = AppButton.secondary("Close");
        closeBtn.addActionListener(e -> dispose());
        rightBox.add(closeBtn);

        JButton saveBtn = AppButton.primary("Save Changes");
        saveBtn.addActionListener(e -> onSaveProfile((AppButton) saveBtn));
        rightBox.add(saveBtn);

        bar.add(rightBox, BorderLayout.EAST);
        return bar;
    }

    private void onSaveProfile(AppButton saveBtn) {
        clearErrors();

        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        String email = emailField.getText().trim();

        if (name.isEmpty()) {
            nameErrorLabel.setText("Customer name is required.");
            nameField.putClientProperty(FlatClientProperties.OUTLINE, "error");
            return;
        }

        if (phone.isEmpty()) {
            phoneErrorLabel.setText("Phone number is required.");
            phoneField.putClientProperty(FlatClientProperties.OUTLINE, "error");
            return;
        }

        // Collect selected tags
        List<String> selectedTags = new ArrayList<>();
        for (ChipToggle chip : tagChips) {
            if (chip.isSelected()) {
                selectedTags.add(chip.getText());
            }
        }
        String tagsJoined = String.join(",", selectedTags);
        String notes = notesArea.getText().trim();

        customer.setName(name);
        customer.setPhone(phone);
        customer.setEmail(email.isEmpty() ? null : email);
        customer.setTags(tagsJoined);
        customer.setNotes(notes);

        saveBtn.setLoading(true);
        Ui.async(
                () -> {
                    customerService.updateCustomer(customer);
                    return customer;
                },
                updated -> {
                    saveBtn.setLoading(false);
                    Toast.success(this, "✓ Profile for " + updated.getName() + " updated successfully");
                    subtitleLabel.setText("Customer #" + updated.getCustomerId() + " · Phone: " + updated.getPhone() +
                            (updated.getEmail() != null && !updated.getEmail().isEmpty() ? " · " + updated.getEmail() : ""));
                    if (onUpdated != null) {
                        onUpdated.accept(updated);
                    }
                },
                error -> {
                    saveBtn.setLoading(false);
                    if (error instanceof ValidationException) {
                        phoneErrorLabel.setText(error.getMessage());
                        phoneField.putClientProperty(FlatClientProperties.OUTLINE, "error");
                    } else {
                        Toast.error(this, "Failed to update profile: " + error.getMessage());
                    }
                }
        );
    }

    private void loadReservationHistory() {
        Ui.async(
                () -> reservationService.getReservationsByCustomer(customer.getCustomerId()),
                list -> {
                    historyTableModel.setRowCount(0);
                    int completed = 0;
                    int cancelled = 0;
                    int active = 0;

                    for (ReservationDetail r : list) {
                        historyTableModel.addRow(new Object[]{
                                r.getDisplayCode(),
                                r.getReservationDate().format(DATE_FMT),
                                r.getSlotLabel(),
                                "Table " + r.getTableNumber(),
                                r.getGuestCount() + " guests",
                                r.getStatus()
                        });
                        if (r.getStatus() == ReservationStatus.COMPLETED) completed++;
                        else if (r.getStatus() == ReservationStatus.CANCELLED) cancelled++;
                        else active++;
                    }

                    int total = list.size();
                    if (total == 0) {
                        visitsBadge.setText("★ New Guest");
                        historySummaryLabel.setText("No past bookings recorded.");
                    } else {
                        visitsBadge.setText(String.format("★ %d Lifetime Visit%s", total, total > 1 ? "s" : ""));
                        historySummaryLabel.setText(String.format("Total: %d Bookings · %d Completed · %d Active/Upcoming · %d Cancelled",
                                total, completed, active, cancelled));
                    }
                },
                err -> {
                    visitsBadge.setText("★ Registered Guest");
                    historySummaryLabel.setText("Unable to load history: " + err.getMessage());
                }
        );
    }

    private JLabel createFieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_SMALL_BOLD);
        l.setForeground(Theme.TEXT_SECONDARY);
        l.setAlignmentX(0.0f);
        return l;
    }

    private void styleErrorLabel(JLabel label) {
        label.setFont(Theme.FONT_SMALL);
        label.setForeground(Theme.DANGER);
        label.setAlignmentX(0.0f);
    }

    private void clearErrors() {
        nameErrorLabel.setText(" ");
        phoneErrorLabel.setText(" ");
        nameField.putClientProperty(FlatClientProperties.OUTLINE, null);
        phoneField.putClientProperty(FlatClientProperties.OUTLINE, null);
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "CU";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }
}
