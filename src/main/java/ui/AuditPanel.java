package ui;

import model.AuditEntry;
import service.AuditService;
import ui.components.DateSelector;
import ui.components.Toast;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Audit log management view (Manager only).
 * Displays append-only security and operational events with pagination and filters.
 */
public class AuditPanel extends BasePanel {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int PAGE_SIZE = 25;

    private final AuditService auditService;

    private JComboBox<String> actionCombo;
    private DateSelector fromDateSelector;
    private DateSelector toDateSelector;

    private JTable auditTable;
    private DefaultTableModel tableModel;
    private JLabel pageLabel;
    private JButton prevBtn;
    private JButton nextBtn;

    private int currentPage = 1;
    private int totalCount = 0;

    public AuditPanel(AuditService auditService) {
        super("Audit Trail & Compliance");
        this.auditService = auditService;
        initUi();
    }

    private void initUi() {
        JPanel mainLayout = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        mainLayout.setOpaque(false);

        // Filter Bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        filterBar.setOpaque(false);

        filterBar.add(new JLabel("Action:"));
        String[] actions = {
                "ALL", "LOGIN_OK", "LOGIN_FAILED", "LOGIN_LOCKED", "LOGOUT",
                "RES_CREATED", "RES_CANCELLED",
                "TABLE_CREATED", "TABLE_UPDATED", "TABLE_DELETED",
                "CUSTOMER_CREATED", "CUSTOMER_UPDATED", "CUSTOMER_DELETE",
                "USER_CREATED", "USER_ROLE_CHANGED", "USER_DEACTIVATED", "USER_REACTIVATED",
                "PWD_CHANGED", "PWD_RESET", "SETTING_CHANGED"
        };
        actionCombo = new JComboBox<>(actions);
        actionCombo.setPreferredSize(new Dimension(160, 36));
        filterBar.add(actionCombo);

        filterBar.add(new JLabel("From:"));
        fromDateSelector = new DateSelector();
        fromDateSelector.setDate(util.DbClock.today().minusDays(7));
        filterBar.add(fromDateSelector);

        filterBar.add(new JLabel("To:"));
        toDateSelector = new DateSelector();
        toDateSelector.setDate(util.DbClock.today());
        filterBar.add(toDateSelector);

        JButton filterBtn = new JButton("Filter");
        filterBtn.setPreferredSize(new Dimension(80, 36));
        filterBtn.addActionListener(e -> {
            currentPage = 1;
            loadData();
        });
        filterBar.add(filterBtn);

        JButton resetBtn = new JButton("Reset");
        resetBtn.setPreferredSize(new Dimension(80, 36));
        resetBtn.addActionListener(e -> {
            actionCombo.setSelectedIndex(0);
            fromDateSelector.setDate(util.DbClock.today().minusDays(7));
            toDateSelector.setDate(util.DbClock.today());
            currentPage = 1;
            loadData();
        });
        filterBar.add(resetBtn);

        mainLayout.add(filterBar, BorderLayout.NORTH);

        // Audit Table
        String[] columns = {"ID", "Timestamp", "Action", "User", "Entity", "Entity ID", "Details"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        auditTable = new JTable(tableModel);
        auditTable.setFont(Theme.FONT_BODY);
        auditTable.setRowHeight(38);

        // Customize column widths
        auditTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        auditTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        auditTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        auditTable.getColumnModel().getColumn(3).setPreferredWidth(110);
        auditTable.getColumnModel().getColumn(4).setPreferredWidth(90);
        auditTable.getColumnModel().getColumn(5).setPreferredWidth(70);
        auditTable.getColumnModel().getColumn(6).setPreferredWidth(320);

        // Action color renderer
        auditTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String act = String.valueOf(value);
                if (act.contains("OK") || act.contains("CREATED") || act.contains("REACTIVATED")) {
                    l.setForeground(Theme.SUCCESS);
                } else if (act.contains("FAILED") || act.contains("CANCELLED") || act.contains("DELETED") || act.contains("DEACTIVATED")) {
                    l.setForeground(Theme.DANGER);
                } else if (act.contains("LOCKED") || act.contains("RESET") || act.contains("CHANGED")) {
                    l.setForeground(Theme.ACCENT_GOLD);
                } else {
                    l.setForeground(Theme.TEXT_SECONDARY);
                }
                return l;
            }
        });

        JScrollPane scrollPane = new JScrollPane(auditTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        mainLayout.add(scrollPane, BorderLayout.CENTER);

        // Pagination Bar
        JPanel paginationBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        paginationBar.setOpaque(false);

        pageLabel = new JLabel("Page 1 of 1 (0 entries)");
        pageLabel.setFont(Theme.FONT_SMALL);
        pageLabel.setForeground(Theme.TEXT_MUTED);

        prevBtn = new JButton("◀ Previous");
        prevBtn.setFont(Theme.FONT_SMALL);
        prevBtn.setEnabled(false);
        prevBtn.addActionListener(e -> {
            if (currentPage > 1) {
                currentPage--;
                loadData();
            }
        });

        nextBtn = new JButton("Next ▶");
        nextBtn.setFont(Theme.FONT_SMALL);
        nextBtn.setEnabled(false);
        nextBtn.addActionListener(e -> {
            int maxPages = Math.max(1, (int) Math.ceil((double) totalCount / PAGE_SIZE));
            if (currentPage < maxPages) {
                currentPage++;
                loadData();
            }
        });

        paginationBar.add(pageLabel);
        paginationBar.add(prevBtn);
        paginationBar.add(nextBtn);

        mainLayout.add(paginationBar, BorderLayout.SOUTH);
        contentPanel.add(mainLayout, BorderLayout.CENTER);
    }

    @Override
    public void onShown() {
        loadData();
    }

    private void loadData() {
        String action = (String) actionCombo.getSelectedItem();
        LocalDate from = fromDateSelector.getDate();
        LocalDate to = toDateSelector.getDate();
        int offset = (currentPage - 1) * PAGE_SIZE;

        Ui.async(
                () -> {
                    int count = auditService.getAuditCount(action, null, from, to);
                    List<AuditEntry> entries = auditService.getAuditLogs(PAGE_SIZE, offset, action, null, from, to);
                    return new DataPayload(count, entries);
                },
                payload -> {
                    totalCount = payload.count;
                    tableModel.setRowCount(0);

                    for (AuditEntry e : payload.entries) {
                        String timeStr = e.getCreatedAt() != null ? e.getCreatedAt().format(TIME_FMT) : "";
                        String userStr = e.getUsername() != null ? e.getUsername() : (e.getUserId() != null ? "#" + e.getUserId() : "System");
                        tableModel.addRow(new Object[]{
                                e.getAuditId(),
                                timeStr,
                                e.getAction(),
                                userStr,
                                e.getEntityType() != null ? e.getEntityType() : "",
                                e.getEntityId() != null ? e.getEntityId() : "",
                                e.getDetails() != null ? e.getDetails() : ""
                        });
                    }

                    int maxPages = Math.max(1, (int) Math.ceil((double) totalCount / PAGE_SIZE));
                    pageLabel.setText(String.format("Page %d of %d (%d total entries)", currentPage, maxPages, totalCount));
                    prevBtn.setEnabled(currentPage > 1);
                    nextBtn.setEnabled(currentPage < maxPages);
                },
                error -> Toast.show(this, "Could not load audit trail: " + error.getMessage(), Toast.Type.ERROR)
        );
    }

    private record DataPayload(int count, List<AuditEntry> entries) {}
}
