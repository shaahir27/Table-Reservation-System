package ui;

import com.formdev.flatlaf.FlatClientProperties;
import exception.AppException;
import model.AppUser;
import security.PasswordPolicy;
import security.Role;
import security.Session;
import service.UserService;
import ui.components.Toast;
import util.AppLog;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
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
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Staff and RBAC management view (Manager only).
 * Provides staff list, role modification, account deactivation/reactivation,
 * and secure temporary password generation with last-manager safety guards.
 */
public class StaffPanel extends BasePanel {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private final UserService userService;

    private JTable userTable;
    private DefaultTableModel tableModel;
    private List<AppUser> currentUsers = new ArrayList<>();

    private JButton addStaffBtn;
    private JButton changeRoleBtn;
    private JButton resetPwdBtn;
    private JButton toggleStatusBtn;
    private JButton refreshBtn;

    public StaffPanel(UserService userService) {
        super("Staff Accounts & RBAC");
        this.userService = userService;
        initUi();
    }

    private void initUi() {
        JPanel mainLayout = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        mainLayout.setOpaque(false);

        // Top Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        toolbar.setOpaque(false);

        addStaffBtn = new JButton("+ Add Staff Member") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? Theme.ACCENT_GOLD_HOVER : Theme.ACCENT_GOLD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        addStaffBtn.setFont(Theme.FONT_BODY_BOLD);
        addStaffBtn.setForeground(Color.WHITE);
        addStaffBtn.setContentAreaFilled(false);
        addStaffBtn.setBorderPainted(false);
        addStaffBtn.setFocusPainted(false);
        addStaffBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addStaffBtn.setPreferredSize(new Dimension(170, 38));
        addStaffBtn.addActionListener(e -> showAddStaffDialog());

        refreshBtn = new JButton("↻ Refresh");
        refreshBtn.setFont(Theme.FONT_BODY);
        refreshBtn.setPreferredSize(new Dimension(100, 38));
        refreshBtn.addActionListener(e -> loadUsers());

        toolbar.add(addStaffBtn);
        toolbar.add(refreshBtn);
        mainLayout.add(toolbar, BorderLayout.NORTH);

        // User Table
        String[] columns = {"ID", "Full Name", "Username", "Role", "Status", "Last Login"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        userTable = new JTable(tableModel);
        userTable.setFont(Theme.FONT_BODY);
        userTable.setRowHeight(42);
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userTable.getSelectionModel().addListSelectionListener(e -> updateActionButtonsState());

        // Custom Cell Renderers
        userTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                l.setHorizontalAlignment(SwingConstants.CENTER);
                String roleStr = String.valueOf(value);
                if ("MANAGER".equalsIgnoreCase(roleStr)) {
                    l.setForeground(Theme.ACCENT_GOLD);
                } else {
                    l.setForeground(new Color(148, 163, 184));
                }
                return l;
            }
        });

        userTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                l.setHorizontalAlignment(SwingConstants.CENTER);
                String statusStr = String.valueOf(value);
                if ("Active".equalsIgnoreCase(statusStr)) {
                    l.setForeground(Theme.SUCCESS);
                } else {
                    l.setForeground(Theme.DANGER);
                }
                return l;
            }
        });

        JScrollPane scrollPane = new JScrollPane(userTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        mainLayout.add(scrollPane, BorderLayout.CENTER);

        // Bottom Action Bar
        JPanel bottomActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        bottomActions.setOpaque(false);

        changeRoleBtn = new JButton("Change Role");
        changeRoleBtn.setFont(Theme.FONT_BODY);
        changeRoleBtn.setEnabled(false);
        changeRoleBtn.addActionListener(e -> handleChangeRole());

        resetPwdBtn = new JButton("Reset Password");
        resetPwdBtn.setFont(Theme.FONT_BODY);
        resetPwdBtn.setEnabled(false);
        resetPwdBtn.addActionListener(e -> handleResetPassword());

        toggleStatusBtn = new JButton("Toggle Status");
        toggleStatusBtn.setFont(Theme.FONT_BODY);
        toggleStatusBtn.setEnabled(false);
        toggleStatusBtn.addActionListener(e -> handleToggleStatus());

        bottomActions.add(changeRoleBtn);
        bottomActions.add(resetPwdBtn);
        bottomActions.add(toggleStatusBtn);

        mainLayout.add(bottomActions, BorderLayout.SOUTH);
        contentPanel.add(mainLayout, BorderLayout.CENTER);
    }

    @Override
    public void onShown() {
        loadUsers();
    }

    private void loadUsers() {
        Ui.async(
                userService::getAllUsers,
                users -> {
                    currentUsers = users;
                    tableModel.setRowCount(0);
                    for (AppUser u : users) {
                        String lastLoginStr = u.getLastLoginAt() != null ? u.getLastLoginAt().format(TIME_FMT) : "Never";
                        tableModel.addRow(new Object[]{
                                u.getUserId(),
                                u.getFullName(),
                                u.getUsername(),
                                u.getRole() != null ? u.getRole().name() : "N/A",
                                u.isActive() ? "Active" : "Inactive",
                                lastLoginStr
                        });
                    }
                    updateActionButtonsState();
                },
                error -> Toast.show(this, "Could not load staff accounts: " + error.getMessage(), Toast.Type.ERROR)
        );
    }

    private void updateActionButtonsState() {
        int row = userTable.getSelectedRow();
        boolean selected = row >= 0 && row < currentUsers.size();
        changeRoleBtn.setEnabled(selected);
        resetPwdBtn.setEnabled(selected);
        toggleStatusBtn.setEnabled(selected);

        if (selected) {
            AppUser u = currentUsers.get(row);
            toggleStatusBtn.setText(u.isActive() ? "Deactivate Account" : "Reactivate Account");
        }
    }

    private void showAddStaffDialog() {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this) instanceof java.awt.Frame f ? f : null, "Add Staff Account", true);
        dialog.setLayout(new BorderLayout());

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(Theme.CARD_BG);
        form.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JTextField nameField = new JTextField();
        nameField.setPreferredSize(new Dimension(300, 36));
        JTextField userField = new JTextField();
        userField.setPreferredSize(new Dimension(300, 36));
        JComboBox<Role> roleBox = new JComboBox<>(Role.values());
        roleBox.setPreferredSize(new Dimension(300, 36));
        JPasswordField pwdField = new JPasswordField();
        pwdField.setPreferredSize(new Dimension(300, 36));
        pwdField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");

        form.add(new JLabel("Full Name:"));
        form.add(Box.createVerticalStrut(4));
        form.add(nameField);
        form.add(Box.createVerticalStrut(10));
        form.add(new JLabel("Username:"));
        form.add(Box.createVerticalStrut(4));
        form.add(userField);
        form.add(Box.createVerticalStrut(10));
        form.add(new JLabel("Role:"));
        form.add(Box.createVerticalStrut(4));
        form.add(roleBox);
        form.add(Box.createVerticalStrut(10));
        form.add(new JLabel("Temporary Initial Password (min 10 chars):"));
        form.add(Box.createVerticalStrut(4));
        form.add(pwdField);
        form.add(Box.createVerticalStrut(16));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnPanel.setOpaque(false);
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        JButton saveBtn = new JButton("Create Account");
        saveBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            String username = userField.getText().trim();
            Role role = (Role) roleBox.getSelectedItem();
            char[] pwd = pwdField.getPassword();

            if (name.isBlank() || username.isBlank() || pwd.length == 0) {
                JOptionPane.showMessageDialog(dialog, "Please fill out all fields.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            saveBtn.setEnabled(false);
            Ui.async(
                    () -> userService.createUser(username, name, role, pwd, true),
                    newUser -> {
                        dialog.dispose();
                        Toast.show(this, "Created staff account: " + newUser.getUsername(), Toast.Type.SUCCESS);
                        loadUsers();
                    },
                    error -> {
                        saveBtn.setEnabled(true);
                        JOptionPane.showMessageDialog(dialog, error.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
            );
        });

        btnPanel.add(cancelBtn);
        btnPanel.add(saveBtn);
        form.add(btnPanel);

        dialog.getContentPane().add(form);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void handleChangeRole() {
        int row = userTable.getSelectedRow();
        if (row < 0 || row >= currentUsers.size()) return;
        AppUser user = currentUsers.get(row);

        Role newRole = user.getRole() == Role.MANAGER ? Role.HOST : Role.MANAGER;
        int opt = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(this),
                "Change role of " + user.getFullName() + " (" + user.getUsername() + ") to " + newRole + "?",
                "Confirm Role Change",
                JOptionPane.YES_NO_OPTION);

        if (opt == JOptionPane.YES_OPTION) {
            Ui.async(
                    () -> {
                        userService.changeRole(user.getUserId(), newRole);
                        return true;
                    },
                    success -> {
                        Toast.show(this, "Updated role for " + user.getUsername(), Toast.Type.SUCCESS);
                        loadUsers();
                    },
                    error -> Toast.show(this, error.getMessage(), Toast.Type.ERROR)
            );
        }
    }

    private void handleResetPassword() {
        int row = userTable.getSelectedRow();
        if (row < 0 || row >= currentUsers.size()) return;
        AppUser user = currentUsers.get(row);

        // Generate a 12-char secure random temporary password
        String tempPwd = generateRandomPassword();
        int opt = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(this),
                "Reset password for " + user.getFullName() + " (" + user.getUsername() + ")?\n" +
                "The user will be prompted to set a new password on next login.",
                "Confirm Password Reset",
                JOptionPane.YES_NO_OPTION);

        if (opt == JOptionPane.YES_OPTION) {
            Ui.async(
                    () -> {
                        userService.resetPassword(user.getUserId(), tempPwd.toCharArray());
                        return tempPwd;
                    },
                    pwd -> {
                        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                                "Temporary password generated:\n\n   " + pwd + "\n\n" +
                                "Please provide this temporary password to the staff member.",
                                "Password Reset Complete",
                                JOptionPane.INFORMATION_MESSAGE);
                        Toast.show(this, "Password reset for " + user.getUsername(), Toast.Type.SUCCESS);
                        loadUsers();
                    },
                    error -> Toast.show(this, error.getMessage(), Toast.Type.ERROR)
            );
        }
    }

    private void handleToggleStatus() {
        int row = userTable.getSelectedRow();
        if (row < 0 || row >= currentUsers.size()) return;
        AppUser user = currentUsers.get(row);

        boolean willDeactivate = user.isActive();
        String actionName = willDeactivate ? "Deactivate" : "Reactivate";

        int opt = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(this),
                actionName + " account for " + user.getFullName() + " (" + user.getUsername() + ")?",
                "Confirm Status Change",
                JOptionPane.YES_NO_OPTION);

        if (opt == JOptionPane.YES_OPTION) {
            Ui.async(
                    () -> {
                        if (willDeactivate) {
                            userService.deactivateUser(user.getUserId());
                        } else {
                            userService.reactivateUser(user.getUserId());
                        }
                        return true;
                    },
                    success -> {
                        Toast.show(this, user.getUsername() + " status updated.", Toast.Type.SUCCESS);
                        loadUsers();
                    },
                    error -> Toast.show(this, error.getMessage(), Toast.Type.ERROR)
            );
        }
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
