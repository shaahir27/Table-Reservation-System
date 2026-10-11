package ui;

import model.AuditEntry;
import service.AuditService;
import ui.components.ChipRow;
import ui.components.AppButton;
import ui.components.Card;
import ui.components.ChipToggle;
import ui.components.DateSelector;
import ui.components.Form;
import ui.components.StyledTable;
import ui.components.Toast;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
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

    private void applyQuickRange(int days) {
        fromDateSelector.setDate(util.DbClock.today().minusDays(days));
        toDateSelector.setDate(util.DbClock.today());
        currentPage = 1;
        loadData();
    }

    private void initUi() {
        JPanel mainLayout = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        mainLayout.setOpaque(false);

        // Filter Bar
        JPanel filterBar = new Card(14, Theme.SPACE_20);
        filterBar.setLayout(ChipRow.layout(12, false));

        filterBar.add(Form.label("Action"));
        String[] actions = {
                "ALL", "LOGIN_OK", "LOGIN_FAILED", "LOGIN_LOCKED", "LOGOUT",
                "RES_CREATED", "RES_SEATED", "RES_COMPLETED", "RES_NO_SHOW", "RES_CANCELLED", "WALK_IN_SEATED",
                "WAITLIST_JOIN", "WAITLIST_CANCEL", "OFFER_MADE", "OFFER_ACCEPTED", "OFFER_DECLINED", "RECONFIRM_UPDATE",
                "TABLE_CREATED", "TABLE_UPDATED", "TABLE_DELETED", "FLOOR_PLAN_UPDATED",
                "CUSTOMER_CREATED", "CUSTOMER_UPDATED", "CUSTOMER_DELETED",
                "USER_CREATED", "USER_ROLE_CHANGED", "USER_DEACTIVATED", "USER_REACTIVATED",
                "PWD_CHANGED", "PWD_RESET", "SETTING_CHANGED"
        };
        actionCombo = new JComboBox<>(actions);
        Form.input(actionCombo, 210);
        filterBar.add(actionCombo);

        filterBar.add(Form.label("From"));
        fromDateSelector = new DateSelector(true, false);
        fromDateSelector.setDate(util.DbClock.today().minusDays(7));
        filterBar.add(fromDateSelector);

        filterBar.add(Form.label("To"));
        toDateSelector = new DateSelector(true, false);
        toDateSelector.setDate(util.DbClock.today());
        filterBar.add(toDateSelector);

        // quick ranges set both dates and re-run the filter
        ChipToggle todayChip = new ChipToggle("Today", ChipToggle.Style.DARK).minWidth(70);
        ChipToggle weekChip = new ChipToggle("Last 7 days", ChipToggle.Style.DARK).minWidth(70);
        ChipToggle monthChip = new ChipToggle("Last 30 days", ChipToggle.Style.DARK).minWidth(70);
        javax.swing.ButtonGroup rangeGroup = new javax.swing.ButtonGroup();
        rangeGroup.add(todayChip);
        rangeGroup.add(weekChip);
        rangeGroup.add(monthChip);
        weekChip.setSelected(true);
        todayChip.addActionListener(e -> applyQuickRange(0));
        weekChip.addActionListener(e -> applyQuickRange(7));
        monthChip.addActionListener(e -> applyQuickRange(30));
        filterBar.add(todayChip);
        filterBar.add(weekChip);
        filterBar.add(monthChip);

        JButton filterBtn = AppButton.primary("Apply Filters");
        filterBtn.addActionListener(e -> {
            currentPage = 1;
            loadData();
        });
        filterBar.add(filterBtn);

        JButton resetBtn = AppButton.secondary("Reset");
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

        auditTable = new StyledTable(tableModel).emptyMessage("No audit entries match these filters.");

        // Customize column widths

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
        scrollPane.getViewport().setBackground(Theme.CARD_BG);
        mainLayout.add(scrollPane, BorderLayout.CENTER);

        // Pagination Bar
        JPanel paginationBar = new ChipRow(12, true);
        paginationBar.setOpaque(false);

        pageLabel = new JLabel("Page 1 of 1 (0 entries)");
        pageLabel.setFont(Theme.FONT_SMALL);
        pageLabel.setForeground(Theme.TEXT_MUTED);

        prevBtn = AppButton.secondary("‹  Previous").compact();
        prevBtn.setEnabled(false);
        prevBtn.addActionListener(e -> {
            if (currentPage > 1) {
                currentPage--;
                loadData();
            }
        });

        nextBtn = AppButton.secondary("Next  ›").compact();
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
                    if (auditTable instanceof StyledTable st) st.reveal();
                    prevBtn.setEnabled(currentPage > 1);
                    nextBtn.setEnabled(currentPage < maxPages);
                },
                error -> Toast.show(this, "Could not load audit trail: " + error.getMessage(), Toast.Type.ERROR)
        );
    }

    private record DataPayload(int count, List<AuditEntry> entries) {}
}
