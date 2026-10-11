package ui;

import com.formdev.flatlaf.FlatClientProperties;
import model.AppUser;
import security.Role;
import service.UserService;
import ui.components.ChipRow;
import ui.components.AppButton;
import ui.components.Form;
import ui.components.StatusPillRenderer;
import ui.components.StyledTable;
import ui.components.Toast;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import ui.components.Dialogs;
import ui.components.StaffDetailDialog;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
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
    private JButton viewProfileBtn;
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
        JPanel toolbar = new ChipRow(12);
        toolbar.setOpaque(false);

        addStaffBtn = AppButton.primary("+ Add Staff Member");
        addStaffBtn.addActionListener(e -> showAddStaffDialog());

        refreshBtn = AppButton.secondary("Refresh");
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

        userTable = new StyledTable(tableModel).emptyMessage("No staff accounts found.");
        userTable.getSelectionModel().addListSelectionListener(e -> updateActionButtonsState());
        userTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openStaffProfile();
                }
            }
        });

        // Role and status render as palette pills
        userTable.getColumnModel().getColumn(0).setCellRenderer(StyledTable.centered());
        userTable.getColumnModel().getColumn(3).setCellRenderer(new StatusPillRenderer());
        userTable.getColumnModel().getColumn(4).setCellRenderer(new StatusPillRenderer());

        JScrollPane scrollPane = new JScrollPane(userTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        scrollPane.getViewport().setBackground(Theme.CARD_BG);
        mainLayout.add(scrollPane, BorderLayout.CENTER);

        // Bottom Action Bar
        JPanel bottomActions = new ChipRow(12, true);
        bottomActions.setOpaque(false);

        viewProfileBtn = AppButton.primary("👤 View Staff Profile");
        viewProfileBtn.setEnabled(false);
        viewProfileBtn.addActionListener(e -> openStaffProfile());

        changeRoleBtn = AppButton.secondary("Change Role");
        changeRoleBtn.setEnabled(false);
        changeRoleBtn.addActionListener(e -> handleChangeRole());

        resetPwdBtn = AppButton.secondary("Reset Password");
        resetPwdBtn.setEnabled(false);
        resetPwdBtn.addActionListener(e -> handleResetPassword());

        toggleStatusBtn = AppButton.secondary("Toggle Status");
        toggleStatusBtn.setEnabled(false);
        toggleStatusBtn.addActionListener(e -> handleToggleStatus());

        bottomActions.add(viewProfileBtn);
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
                    if (userTable instanceof StyledTable st) st.reveal();
                },
                error -> Toast.show(this, "Could not load staff accounts: " + error.getMessage(), Toast.Type.ERROR)
        );
    }

    private void updateActionButtonsState() {
        int row = userTable.getSelectedRow();
        boolean selected = row >= 0 && row < currentUsers.size();
        if (viewProfileBtn != null) viewProfileBtn.setEnabled(selected);
        changeRoleBtn.setEnabled(selected);
        resetPwdBtn.setEnabled(selected);
        toggleStatusBtn.setEnabled(selected);

        if (selected) {
            AppUser u = currentUsers.get(row);
            toggleStatusBtn.setText(u.isActive() ? "Deactivate Account" : "Reactivate Account");
        }
    }

    private void openStaffProfile() {
        int row = userTable.getSelectedRow();
        if (row < 0 || row >= currentUsers.size()) return;
        AppUser u = currentUsers.get(row);
        StaffDetailDialog.show(SwingUtilities.getWindowAncestor(this), u, userService, this::loadUsers);
    }

    private void showAddStaffDialog() {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this) instanceof java.awt.Frame f ? f : null, "Add Staff Account", true);
        dialog.setLayout(new BorderLayout());

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(Theme.CARD_BG);
        form.setBorder(BorderFactory.createEmptyBorder(26, 30, 24, 30));

        JLabel heading = new JLabel("New staff account");
        heading.setFont(Theme.FONT_SECTION.deriveFont(18f));
        heading.setForeground(Theme.TEXT_PRIMARY);
        heading.setAlignmentX(0f);
        JLabel sub = new JLabel("They will be asked to choose a new password at first sign-in.");
        sub.setFont(Theme.FONT_SMALL);
        sub.setForeground(Theme.TEXT_MUTED);
        sub.setAlignmentX(0f);
        form.add(heading);
        form.add(Box.createVerticalStrut(4));
        form.add(sub);
        form.add(Box.createVerticalStrut(20));

        JTextField nameField = new JTextField();
        nameField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. Priya Patel");
        JTextField userField = new JTextField();
        userField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. priya");
        JComboBox<Role> roleBox = new JComboBox<>(Role.values());
        JPasswordField pwdField = new JPasswordField();
        pwdField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        pwdField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "At least 10 characters");

        form.add(Form.field("Full name", nameField, null));
        form.add(Form.field("Username", userField, null));
        form.add(Form.field("Role", roleBox, null));
        form.add(Form.field("Temporary password (min 10 characters)", pwdField, null));
        form.add(Box.createVerticalStrut(6));

        JPanel btnPanel = new ChipRow(10, true);
        btnPanel.setOpaque(false);
        btnPanel.setAlignmentX(0f);
        JButton cancelBtn = AppButton.secondary("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        AppButton saveBtn = AppButton.primary("Create Account");
        saveBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            String username = userField.getText().trim();
            Role role = (Role) roleBox.getSelectedItem();
            char[] pwd = pwdField.getPassword();

            if (name.isBlank() || username.isBlank() || pwd.length == 0) {
                Dialogs.showMessageDialog(dialog, "Please fill out all fields.", "Validation Error", Dialogs.ERROR_MESSAGE);
                return;
            }

            saveBtn.setLoading(true);
            Ui.async(
                    () -> userService.createUser(username, name, role, pwd, true),
                    newUser -> {
                        dialog.dispose();
                        Toast.show(this, "Created staff account: " + newUser.getUsername(), Toast.Type.SUCCESS);
                        loadUsers();
                    },
                    error -> {
                        saveBtn.setLoading(false);
                        Dialogs.showMessageDialog(dialog, error.getMessage(), "Could not create account", Dialogs.ERROR_MESSAGE);
                    }
            );
        });

        btnPanel.add(cancelBtn);
        btnPanel.add(saveBtn);
        form.add(btnPanel);
        dialog.getRootPane().setDefaultButton(saveBtn);

        dialog.getContentPane().add(form);
        dialog.setMinimumSize(new Dimension(440, 0));
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void handleChangeRole() {
        int row = userTable.getSelectedRow();
        if (row < 0 || row >= currentUsers.size()) return;
        AppUser user = currentUsers.get(row);

        Role newRole = user.getRole() == Role.MANAGER ? Role.HOST : Role.MANAGER;
        int opt = Dialogs.showConfirmDialog(SwingUtilities.getWindowAncestor(this),
                "Change role of " + user.getFullName() + " (" + user.getUsername() + ") to " + newRole + "?",
                "Confirm Role Change",
                Dialogs.YES_NO_OPTION);

        if (opt == Dialogs.YES_OPTION) {
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
        int opt = Dialogs.showConfirmDialog(SwingUtilities.getWindowAncestor(this),
                "Reset password for " + user.getFullName() + " (" + user.getUsername() + ")?\n" +
                "The user will be prompted to set a new password on next login.",
                "Confirm Password Reset",
                Dialogs.YES_NO_OPTION);

        if (opt == Dialogs.YES_OPTION) {
            Ui.async(
                    () -> {
                        userService.resetPassword(user.getUserId(), tempPwd.toCharArray());
                        return tempPwd;
                    },
                    pwd -> {
                        Dialogs.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                                "Temporary password generated:\n\n   " + pwd + "\n\n" +
                                "Please provide this temporary password to the staff member.",
                                "Password Reset Complete",
                                Dialogs.INFORMATION_MESSAGE);
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

        int opt = Dialogs.showConfirmDialog(SwingUtilities.getWindowAncestor(this),
                actionName + " account for " + user.getFullName() + " (" + user.getUsername() + ")?",
                "Confirm Status Change",
                Dialogs.YES_NO_OPTION);

        if (opt == Dialogs.YES_OPTION) {
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
