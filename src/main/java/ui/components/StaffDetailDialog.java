package ui.components;

import com.formdev.flatlaf.FlatClientProperties;
import exception.ValidationException;
import model.AppUser;
import model.AuditEntry;
import security.PasswordPolicy;
import security.Role;
import service.UserService;
import ui.Theme;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
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
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Staff 360° Floating Profile & Credential Management Modal.
 * Displays staff account demographics, role, activity timeline, and allows in-place updates.
 */
public class StaffDetailDialog extends JDialog {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final AppUser user;
    private final UserService userService;
    private final Runnable onUpdated;

    // Form inputs
    private final JTextField fullNameField = new JTextField();
    private final JTextField usernameField = new JTextField();
    private final JComboBox<Role> roleCombo = new JComboBox<>(Role.values());
    private final JComboBox<String> statusCombo = new JComboBox<>(new String[]{"ACTIVE", "INACTIVE"});
    private final JPasswordField newPwdField = new JPasswordField();

    private final JLabel nameErrorLabel = new JLabel(" ");
    private final JLabel pwdErrorLabel = new JLabel(" ");

    // Audit History Table
    private final DefaultTableModel auditTableModel;
    private final JTable auditTable;
    private final JLabel auditSummaryLabel = new JLabel("Loading activity history...");

    private final JLabel roleBadge = new JLabel();
    private final JLabel statusBadge = new JLabel();
    private final JLabel subtitleLabel = new JLabel();

    public static void show(Window parent, AppUser user, UserService userService, Runnable onUpdated) {
        Frame frame = (parent instanceof Frame) ? (Frame) parent : (Frame) SwingUtilities.getAncestorOfClass(Frame.class, parent);
        StaffDetailDialog dlg = new StaffDetailDialog(frame, user, userService, onUpdated);
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
    }

    public StaffDetailDialog(Frame parent, AppUser user, UserService userService, Runnable onUpdated) {
        super(parent, "Staff 360° Profile · " + user.getFullName(), true);
        this.user = user;
        this.userService = userService;
        this.onUpdated = onUpdated;

        setSize(720, 620);
        setMinimumSize(new Dimension(640, 500));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        root.setBackground(Theme.APP_BG);
        root.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_16, Theme.SPACE_20, Theme.SPACE_16, Theme.SPACE_20));

        // 1. Header Card (Avatar + Identity + Badges)
        root.add(createHeaderCard(), BorderLayout.NORTH);

        // 2. Tabbed Body (Profile / Activity Trail)
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(Theme.FONT_BODY_BOLD);
        tabbedPane.putClientProperty("JTabbedPane.tabType", "underlined");

        // Audit Table Setup
        auditTableModel = new DefaultTableModel(new String[]{"Time", "Action", "Entity", "Details"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        auditTable = new StyledTable(auditTableModel).emptyMessage("No audit activity logged for this staff member.");
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        auditTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        auditTable.getColumnModel().getColumn(1).setCellRenderer(new StatusPillRenderer());
        auditTable.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);

        JScrollPane profileScroll = new JScrollPane(createProfileTab());
        profileScroll.setBorder(null);
        profileScroll.setOpaque(false);
        profileScroll.getViewport().setOpaque(false);
        profileScroll.getVerticalScrollBar().setUnitIncrement(16);
        profileScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        tabbedPane.addTab("Account & Credentials", profileScroll);
        tabbedPane.addTab("Staff Activity & Audit Trail", createAuditTab());

        root.add(tabbedPane, BorderLayout.CENTER);

        // 3. Bottom Action Bar
        root.add(createBottomActionBar(), BorderLayout.SOUTH);

        setContentPane(root);
        loadAuditHistory();
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
                g2.setColor(user.getRole() == Role.MANAGER ? Theme.ACCENT_GOLD : Theme.SIDEBAR_BG);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(Theme.FONT_TITLE.deriveFont(Font.BOLD, 18f));
                String initials = getInitials(user.getFullName());
                int fw = g2.getFontMetrics().stringWidth(initials);
                int fh = g2.getFontMetrics().getAscent();
                g2.drawString(initials, (getWidth() - fw) / 2, (getHeight() + fh) / 2 - 3);
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(54, 54));
        avatar.setOpaque(false);
        card.add(avatar, BorderLayout.WEST);

        // Middle: Staff Name + Username
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        JLabel nameLabel = new JLabel(user.getFullName());
        nameLabel.setFont(Theme.FONT_TITLE);
        nameLabel.setForeground(Theme.TEXT_PRIMARY);

        subtitleLabel.setFont(Theme.FONT_BODY);
        subtitleLabel.setForeground(Theme.TEXT_MUTED);
        updateSubtitleText();

        center.add(nameLabel);
        center.add(Box.createVerticalStrut(2));
        center.add(subtitleLabel);
        card.add(center, BorderLayout.CENTER);

        // Right: Role & Status Pills
        JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        rightBox.setOpaque(false);

        updateHeaderBadges();
        rightBox.add(roleBadge);
        rightBox.add(statusBadge);

        card.add(rightBox, BorderLayout.EAST);
        return card;
    }

    private void updateSubtitleText() {
        String created = user.getCreatedAt() != null ? user.getCreatedAt().format(TIME_FMT) : "System Seed";
        String lastLogin = user.getLastLoginAt() != null ? user.getLastLoginAt().format(TIME_FMT) : "Never";
        subtitleLabel.setText("@" + user.getUsername() + " · Created: " + created + " · Last login: " + lastLogin);
    }

    private void updateHeaderBadges() {
        roleBadge.setText(user.getRole().name());
        roleBadge.setFont(Theme.FONT_SMALL_BOLD);
        roleBadge.setForeground(user.getRole() == Role.MANAGER ? Theme.mix(Theme.ACCENT_GOLD, Color.BLACK, 0.3) : Theme.TEXT_PRIMARY);
        roleBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.CARD_BORDER, 1),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));

        statusBadge.setText(user.isActive() ? "ACTIVE" : "INACTIVE");
        statusBadge.setFont(Theme.FONT_SMALL_BOLD);
        statusBadge.setForeground(user.isActive() ? Theme.SUCCESS : Theme.DANGER);
        statusBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(user.isActive() ? Theme.SUCCESS : Theme.DANGER, 1),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
    }

    private JPanel createProfileTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_16, Theme.SPACE_8, Theme.SPACE_8, Theme.SPACE_8));

        // Full Name
        fullNameField.setText(user.getFullName());
        Form.input(fullNameField);
        panel.add(createFieldLabel("Full Name *"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(fullNameField);
        styleErrorLabel(nameErrorLabel);
        panel.add(nameErrorLabel);
        panel.add(Box.createVerticalStrut(4));

        // Username (Read-only)
        usernameField.setText(user.getUsername());
        usernameField.setEditable(false);
        usernameField.setEnabled(false);
        Form.input(usernameField);
        panel.add(createFieldLabel("System Username (Read-Only)"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(usernameField);
        panel.add(Box.createVerticalStrut(12));

        // Role & Status Row
        JPanel roleStatusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_16, 0));
        roleStatusRow.setOpaque(false);
        roleStatusRow.setAlignmentX(0.0f);

        JPanel roleCol = new JPanel();
        roleCol.setLayout(new BoxLayout(roleCol, BoxLayout.Y_AXIS));
        roleCol.setOpaque(false);
        roleCol.add(createFieldLabel("Access Role"));
        roleCol.add(Box.createVerticalStrut(4));
        roleCombo.setSelectedItem(user.getRole());
        Form.input(roleCombo, 160);
        roleCol.add(roleCombo);
        roleStatusRow.add(roleCol);

        JPanel statusCol = new JPanel();
        statusCol.setLayout(new BoxLayout(statusCol, BoxLayout.Y_AXIS));
        statusCol.setOpaque(false);
        statusCol.add(createFieldLabel("Account Status"));
        statusCol.add(Box.createVerticalStrut(4));
        statusCombo.setSelectedItem(user.isActive() ? "ACTIVE" : "INACTIVE");
        Form.input(statusCombo, 160);
        statusCol.add(statusCombo);
        roleStatusRow.add(statusCol);

        panel.add(roleStatusRow);
        panel.add(Box.createVerticalStrut(16));

        // Password Reset Sub-card
        JPanel pwdCard = new Card(12, 14);
        pwdCard.setLayout(new BoxLayout(pwdCard, BoxLayout.Y_AXIS));
        pwdCard.setAlignmentX(0.0f);

        JLabel pwdTitle = new JLabel("Reset Account Password (Optional)");
        pwdTitle.setFont(Theme.FONT_BODY_BOLD);
        pwdTitle.setForeground(Theme.TEXT_PRIMARY);
        pwdCard.add(pwdTitle);
        pwdCard.add(Box.createVerticalStrut(4));

        JLabel pwdSub = new JLabel("Leave blank to keep existing password. If specified, minimum 8 characters with upper, lower, digit & special.");
        pwdSub.setFont(Theme.FONT_SMALL);
        pwdSub.setForeground(Theme.TEXT_MUTED);
        pwdCard.add(pwdSub);
        pwdCard.add(Box.createVerticalStrut(8));

        JPanel pwdInputRow = new JPanel(new BorderLayout(8, 0));
        pwdInputRow.setOpaque(false);
        Form.input(newPwdField);
        newPwdField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Enter new temporary password...");
        pwdInputRow.add(newPwdField, BorderLayout.CENTER);

        JButton genBtn = AppButton.secondary("Generate Safe");
        genBtn.addActionListener(e -> generateSafePassword());
        pwdInputRow.add(genBtn, BorderLayout.EAST);
        pwdCard.add(pwdInputRow);

        styleErrorLabel(pwdErrorLabel);
        pwdCard.add(pwdErrorLabel);

        panel.add(pwdCard);
        return panel;
    }

    private void generateSafePassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%^&*";
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        sb.append("Safe");
        for (int i = 0; i < 4; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        sb.append(rng.nextInt(90) + 10).append("!");
        newPwdField.setText(sb.toString());
        pwdErrorLabel.setText("Generated: " + sb.toString());
        pwdErrorLabel.setForeground(Theme.SUCCESS);
    }

    private JPanel createAuditTab() {
        JPanel panel = new JPanel(new BorderLayout(0, Theme.SPACE_8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_12, Theme.SPACE_8, Theme.SPACE_8, Theme.SPACE_8));

        JScrollPane scrollPane = new JScrollPane(auditTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        auditSummaryLabel.setFont(Theme.FONT_SMALL);
        auditSummaryLabel.setForeground(Theme.TEXT_MUTED);
        panel.add(auditSummaryLabel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createBottomActionBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);

        JButton closeBtn = AppButton.secondary("Close");
        closeBtn.addActionListener(e -> dispose());
        bar.add(closeBtn, BorderLayout.WEST);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_12, 0));
        rightActions.setOpaque(false);

        JButton saveBtn = AppButton.primary("💾 Save Changes");
        saveBtn.addActionListener(e -> onSaveClicked(saveBtn));
        rightActions.add(saveBtn);

        bar.add(rightActions, BorderLayout.EAST);
        return bar;
    }

    private void loadAuditHistory() {
        Ui.async(
                () -> userService.getUserAuditLogs(user.getUserId(), 50),
                logs -> {
                    auditTableModel.setRowCount(0);
                    for (AuditEntry a : logs) {
                        auditTableModel.addRow(new Object[]{
                                a.getCreatedAt() != null ? a.getCreatedAt().format(TIME_FMT) : "N/A",
                                a.getAction(),
                                a.getEntityType() != null ? a.getEntityType() : "SYSTEM",
                                a.getDetails()
                        });
                    }
                    auditSummaryLabel.setText("Showing last " + logs.size() + " actions recorded by @" + user.getUsername());
                },
                err -> auditSummaryLabel.setText("Failed to load audit history: " + err.getMessage())
        );
    }

    private void onSaveClicked(JButton saveBtn) {
        nameErrorLabel.setText(" ");
        pwdErrorLabel.setText(" ");
        fullNameField.putClientProperty(FlatClientProperties.OUTLINE, null);
        newPwdField.putClientProperty(FlatClientProperties.OUTLINE, null);

        String name = fullNameField.getText().trim();
        if (name.length() < 2) {
            nameErrorLabel.setText("Full name must be at least 2 characters.");
            fullNameField.putClientProperty(FlatClientProperties.OUTLINE, "error");
            return;
        }

        char[] pwdChars = newPwdField.getPassword();
        if (pwdChars != null && pwdChars.length > 0) {
            PasswordPolicy.PolicyResult policy = PasswordPolicy.validate(pwdChars, user.getUsername());
            if (!policy.valid()) {
                pwdErrorLabel.setText(policy.getFirstError());
                pwdErrorLabel.setForeground(Theme.DANGER);
                newPwdField.putClientProperty(FlatClientProperties.OUTLINE, "error");
                return;
            }
        }

        Role newRole = (Role) roleCombo.getSelectedItem();
        boolean newActive = "ACTIVE".equals(statusCombo.getSelectedItem());

        saveBtn.setEnabled(false);
        saveBtn.setText("Saving...");

        Ui.async(
                () -> {
                    userService.updateUserProfile(user.getUserId(), name, newRole, newActive, pwdChars);
                    user.setFullName(name);
                    user.setRole(newRole);
                    user.setActive(newActive);
                    return true;
                },
                success -> {
                    saveBtn.setEnabled(true);
                    saveBtn.setText("💾 Save Changes");
                    Toast.success(this, "Staff profile updated successfully!");
                    updateSubtitleText();
                    updateHeaderBadges();
                    repaint();
                    if (onUpdated != null) onUpdated.run();
                },
                err -> {
                    saveBtn.setEnabled(true);
                    saveBtn.setText("💾 Save Changes");
                    Toast.error(this, err.getMessage());
                }
        );
    }

    private static String getInitials(String name) {
        if (name == null || name.isBlank()) return "ST";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    private JLabel createFieldLabel(String text) {
        return Form.label(text);
    }

    private void styleErrorLabel(JLabel label) {
        label.setFont(Theme.FONT_SMALL);
        label.setForeground(Theme.DANGER);
        label.setAlignmentX(0.0f);
    }
}
