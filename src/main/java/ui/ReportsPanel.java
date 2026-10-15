package ui;

import dao.ReportResult;
import model.Customer;
import model.analytics.AnalyticsSummary;
import model.analytics.DailyCoverTrend;
import model.analytics.SlotLoadMetric;
import model.analytics.TablePerformanceMetric;
import model.analytics.TagCountMetric;
import model.analytics.TopGuestMetric;
import model.analytics.WaitlistFunnelStep;
import service.CustomerService;
import service.ReportService;
import service.ReservationService;
import ui.components.AppButton;
import ui.components.BarChartPanel;
import ui.components.Card;
import ui.components.ChipRow;
import ui.components.CustomerDetailDialog;
import ui.components.DonutGaugePanel;
import ui.components.Form;
import ui.components.HorizontalBarChartPanel;
import ui.components.StatCard;
import ui.components.StyledTable;
import ui.components.Toast;
import util.DbClock;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Executive Hospitality Intelligence & Operational Analytics Dashboard.
 * Inspired by OpenTable Pro and SevenRooms analytics suites.
 */
public class ReportsPanel extends BasePanel {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter SHORT_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM");

    private final ReportService reportService;
    private final CustomerService customerService;
    private final ReservationService reservationService;

    // Period Presets
    private enum PeriodPreset {
        TODAY("Today"),
        LAST_7_DAYS("Last 7 Days"),
        LAST_30_DAYS("Last 30 Days"),
        THIS_MONTH("This Month"),
        CUSTOM("Custom Range");

        final String label;
        PeriodPreset(String label) { this.label = label; }
    }

    private PeriodPreset currentPreset = PeriodPreset.LAST_7_DAYS;

    // Controls
    private final JPanel presetChipsRow = new JPanel();
    private final JLabel periodStatusLabel = new JLabel("Loading analytics...");
    private final JSpinner fromDateSpinner = new JSpinner(new SpinnerDateModel());
    private final JSpinner toDateSpinner = new JSpinner(new SpinnerDateModel());
    private final JPanel customDatePanel = new JPanel();
    private final JButton refreshBtn;
    private final JButton exportCsvBtn;

    // Executive Scorecard StatCards
    private final StatCard coversCard;
    private final StatCard utilizationCard;
    private final StatCard lossCard;
    private final StatCard loyaltyCard;
    private final StatCard recoveryCard;

    // Tabbed Suites
    private final JTabbedPane tabbedPane = new JTabbedPane();

    // Suite 1: Overview
    private final BarChartPanel dailyCoversChart = new BarChartPanel();
    private final BarChartPanel slotLoadChart = new BarChartPanel();
    private final JPanel takeawaysBox = new JPanel();

    // Suite 2: Floor & Tables
    private final DonutGaugePanel seatDonutPanel = new DonutGaugePanel();
    private final HorizontalBarChartPanel tableLeaderboardChart = new HorizontalBarChartPanel();
    private final DefaultTableModel tablePerfModel;
    private final JTable tablePerfTable;

    // Suite 3: Guest CRM & Loyalty
    private final DefaultTableModel topGuestsModel;
    private final JTable topGuestsTable;
    private final HorizontalBarChartPanel tagDistributionChart = new HorizontalBarChartPanel();
    private final JButton inspectGuestBtn;
    private List<TopGuestMetric> currentTopGuests = new ArrayList<>();

    // Suite 4: Loss & Waitlist
    private final DonutGaugePanel noShowDonutPanel = new DonutGaugePanel();
    private final HorizontalBarChartPanel waitlistFunnelChart = new HorizontalBarChartPanel();
    private final JLabel lossAdviceLabel = new JLabel();

    // Suite 5: Data Explorer
    private final JComboBox<String> explorerReportCombo = new JComboBox<>(new String[]{
            "Detailed Bookings in Period",
            "Table Utilization & Efficiency Breakdown",
            "Dining Slot Volume Breakdown",
            "Historical No-Show Log",
            "Waitlist Conversion Statuses"
    });
    private final DefaultTableModel explorerModel;
    private final JTable explorerTable;
    private final JLabel explorerRowCountLabel = new JLabel("");

    // Cache of current snapshot
    private AnalyticsSummary currentSummary = null;

    public ReportsPanel() {
        this(new ReportService(), new CustomerService(), new ReservationService());
    }

    public ReportsPanel(ReportService reportService) {
        this(reportService, new CustomerService(), new ReservationService());
    }

    public ReportsPanel(ReportService reportService, CustomerService customerService, ReservationService reservationService) {
        super("Hospitality Reports & Executive Analytics");
        this.reportService = reportService;
        this.customerService = customerService;
        this.reservationService = reservationService;

        refreshBtn = AppButton.secondary("↻ Refresh");
        exportCsvBtn = AppButton.primary("Export CSV");
        inspectGuestBtn = AppButton.secondary("Inspect Guest 360° Profile");

        // Format Date Spinners
        fromDateSpinner.setEditor(new JSpinner.DateEditor(fromDateSpinner, "dd-MM-yyyy"));
        toDateSpinner.setEditor(new JSpinner.DateEditor(toDateSpinner, "dd-MM-yyyy"));
        Form.input(fromDateSpinner, 120);
        Form.input(toDateSpinner, 120);

        // Scorecard Cards
        coversCard = new StatCard("TOTAL COVERS", "0", "Diners served")
                .icon("calendar", Theme.ACCENT_GOLD);
        utilizationCard = new StatCard("SEAT UTILIZATION", "0%", "Capacity efficiency")
                .icon("tables", Theme.SUCCESS);
        lossCard = new StatCard("NO-SHOW & LOSS", "0%", "Cancellations & absent")
                .icon("cancel", Theme.DANGER);
        loyaltyCard = new StatCard("GUEST LOYALTY", "0%", "Repeat diner ratio")
                .icon("user", Theme.SIDEBAR_BG);
        recoveryCard = new StatCard("RECOVERED COVERS", "0", "Waitlist slot refills")
                .icon("recover", Theme.SUCCESS);

        // Suite 2 Table
        tablePerfModel = new DefaultTableModel(new String[]{
                "Table #", "Location", "Capacity", "Turns", "Total Covers", "Avg Party", "Seat Fill %"
        }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tablePerfTable = new StyledTable(tablePerfModel).emptyMessage("No table turnover records for this period.");

        // Suite 3 Table
        topGuestsModel = new DefaultTableModel(new String[]{
                "Guest ID", "Customer Name", "Phone", "Visits", "Covers", "Hospitality Tags", "Last Visit"
        }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        topGuestsTable = new StyledTable(topGuestsModel).emptyMessage("No recurring guest records in this window.");

        // Suite 5 Table
        explorerModel = new DefaultTableModel() {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        explorerTable = new StyledTable(explorerModel).emptyMessage("No records returned for current report filter.");

        initLayout();
        setupEvents();
    }

    @Override
    public void onShown() {
        applyPreset(currentPreset);
    }

    private void initLayout() {
        JPanel root = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        root.setOpaque(false);

        // 1. Top Control Bar Card
        root.add(createTopControlBar(), BorderLayout.NORTH);

        // 2. Center Container: Scorecard + Tabbed Suites
        JPanel centerContainer = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        centerContainer.setOpaque(false);

        // Scorecard Row
        centerContainer.add(createScorecardRow(), BorderLayout.NORTH);

        // Tabbed Suites
        tabbedPane.setFont(Theme.FONT_BODY_BOLD);
        tabbedPane.putClientProperty("JTabbedPane.tabType", "underlined");

        tabbedPane.addTab("📊 Overview & Covers Trend", createOverviewSuite());
        tabbedPane.addTab("🪑 Floor & Table Efficiency", createFloorEfficiencySuite());
        tabbedPane.addTab("👥 Guest CRM & Loyalty", createGuestLoyaltySuite());
        tabbedPane.addTab("📉 Operations & Loss Prevention", createLossPreventionSuite());
        tabbedPane.addTab("📋 Data Explorer & Export", createDataExplorerSuite());

        centerContainer.add(tabbedPane, BorderLayout.CENTER);
        root.add(centerContainer, BorderLayout.CENTER);

        contentPanel.add(root, BorderLayout.CENTER);
    }

    private JPanel createTopControlBar() {
        JPanel card = new Card(12, 16);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        // Primary Row: Presets on Left, Actions on Right
        JPanel row1 = new JPanel(new BorderLayout(Theme.SPACE_16, 0));
        row1.setOpaque(false);

        JPanel presetsLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_8, 0));
        presetsLeft.setOpaque(false);

        JLabel presetLabel = new JLabel("Time Window:");
        presetLabel.setFont(Theme.FONT_BODY_BOLD);
        presetLabel.setForeground(Theme.TEXT_PRIMARY);
        presetsLeft.add(presetLabel);

        ButtonGroup bg = new ButtonGroup();
        for (PeriodPreset preset : PeriodPreset.values()) {
            JRadioButton btn = new JRadioButton(preset.label);
            btn.setFont(Theme.FONT_BODY);
            btn.setOpaque(false);
            btn.setSelected(preset == currentPreset);
            btn.addActionListener(e -> applyPreset(preset));
            bg.add(btn);
            presetsLeft.add(btn);
        }
        row1.add(presetsLeft, BorderLayout.WEST);

        JPanel actionsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_8, 0));
        actionsRight.setOpaque(false);
        actionsRight.add(refreshBtn);
        actionsRight.add(exportCsvBtn);
        row1.add(actionsRight, BorderLayout.EAST);

        card.add(row1);

        // Sub-bar Row: Custom Date Pickers (when active) on Left, Period Status on Right
        JPanel row2 = new JPanel(new BorderLayout(Theme.SPACE_16, 0));
        row2.setOpaque(false);
        row2.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_8, Theme.SPACE_4, 0, Theme.SPACE_4));

        customDatePanel.setLayout(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_8, 0));
        customDatePanel.setOpaque(false);
        customDatePanel.setVisible(false);

        JLabel fromL = new JLabel("From:");
        fromL.setFont(Theme.FONT_SMALL_BOLD);
        fromL.setForeground(Theme.TEXT_SECONDARY);
        JLabel toL = new JLabel("To:");
        toL.setFont(Theme.FONT_SMALL_BOLD);
        toL.setForeground(Theme.TEXT_SECONDARY);

        customDatePanel.add(fromL);
        customDatePanel.add(fromDateSpinner);
        customDatePanel.add(toL);
        customDatePanel.add(toDateSpinner);

        JButton applyCustomBtn = AppButton.secondary("Apply Range");
        applyCustomBtn.addActionListener(e -> runCurrentDateQuery());
        customDatePanel.add(applyCustomBtn);
        row2.add(customDatePanel, BorderLayout.WEST);

        periodStatusLabel.setFont(Theme.FONT_SMALL);
        periodStatusLabel.setForeground(Theme.TEXT_MUTED);
        JPanel statusWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        statusWrap.setOpaque(false);
        statusWrap.add(periodStatusLabel);
        row2.add(statusWrap, BorderLayout.EAST);

        card.add(row2);
        return card;
    }

    private JPanel createScorecardRow() {
        JPanel row = new JPanel(new GridLayout(1, 5, Theme.SPACE_12, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(0, 136));

        row.add(coversCard);
        row.add(utilizationCard);
        row.add(lossCard);
        row.add(loyaltyCard);
        row.add(recoveryCard);

        // Card clicks switch to respective analytical tabs
        coversCard.onClick(() -> tabbedPane.setSelectedIndex(0));
        utilizationCard.onClick(() -> tabbedPane.setSelectedIndex(1));
        lossCard.onClick(() -> tabbedPane.setSelectedIndex(3));
        loyaltyCard.onClick(() -> tabbedPane.setSelectedIndex(2));
        recoveryCard.onClick(() -> tabbedPane.setSelectedIndex(3));

        return row;
    }

    // Suite 1: Overview & Covers Trend
    private JPanel createOverviewSuite() {
        JPanel suite = new JPanel(new BorderLayout(0, Theme.SPACE_12));
        suite.setOpaque(false);
        suite.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_12, 0, 0, 0));

        // Top: Daily Covers Velocity Bar Chart
        dailyCoversChart.setPreferredSize(new Dimension(0, 210));
        dailyCoversChart.setSubtitle("Daily guest covers and bookings across the selected dining window");
        suite.add(dailyCoversChart, BorderLayout.NORTH);

        // Bottom Grid: Slot Load Chart (Left) + Executive Takeaways (Right)
        JPanel bottomGrid = new JPanel(new GridLayout(1, 2, Theme.SPACE_16, 0));
        bottomGrid.setOpaque(false);
        bottomGrid.setPreferredSize(new Dimension(0, 220));

        slotLoadChart.setPreferredSize(new Dimension(0, 220));
        slotLoadChart.setSubtitle("Guest covers by fixed 90-minute service seating");
        bottomGrid.add(slotLoadChart);

        JPanel takeawaysCard = new Card(14, 16);
        takeawaysCard.setLayout(new BorderLayout());

        JLabel tTitle = new JLabel("💡  Executive Hospitality Takeaways");
        tTitle.setFont(Theme.FONT_SECTION);
        tTitle.setForeground(Theme.TEXT_PRIMARY);
        takeawaysCard.add(tTitle, BorderLayout.NORTH);

        takeawaysBox.setLayout(new BoxLayout(takeawaysBox, BoxLayout.Y_AXIS));
        takeawaysBox.setOpaque(false);
        takeawaysBox.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_8, 0, 0, 0));

        JScrollPane scroll = new JScrollPane(takeawaysBox);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        takeawaysCard.add(scroll, BorderLayout.CENTER);

        bottomGrid.add(takeawaysCard);
        suite.add(bottomGrid, BorderLayout.CENTER);

        JScrollPane suiteScroll = new JScrollPane(suite);
        suiteScroll.setBorder(null);
        suiteScroll.setOpaque(false);
        suiteScroll.getViewport().setOpaque(false);
        suiteScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        suiteScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(suiteScroll, BorderLayout.CENTER);
        return wrapper;
    }

    // Suite 2: Floor & Table Efficiency
    private JPanel createFloorEfficiencySuite() {
        JPanel suite = new JPanel(new BorderLayout(0, Theme.SPACE_12));
        suite.setOpaque(false);
        suite.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_12, 0, 0, 0));

        // Top Row: Seat Utilization Donut (Left) + Table Covers Leaderboard (Right)
        JPanel topRow = new JPanel(new BorderLayout(Theme.SPACE_16, 0));
        topRow.setOpaque(false);
        topRow.setPreferredSize(new Dimension(0, 240));

        seatDonutPanel.setPreferredSize(new Dimension(240, 240));
        topRow.add(seatDonutPanel, BorderLayout.WEST);

        tableLeaderboardChart.setPreferredSize(new Dimension(0, 240));
        tableLeaderboardChart.setBarColor(Theme.SUCCESS);
        topRow.add(tableLeaderboardChart, BorderLayout.CENTER);

        suite.add(topRow, BorderLayout.NORTH);

        // Bottom: Detailed Table Performance Matrix
        JPanel tableCard = new Card(14, 16);
        tableCard.setLayout(new BorderLayout(0, Theme.SPACE_8));

        JLabel title = new JLabel("Table Turnover & Seating Fill Matrix");
        title.setFont(Theme.FONT_SECTION);
        title.setForeground(Theme.TEXT_PRIMARY);
        tableCard.add(title, BorderLayout.NORTH);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < tablePerfTable.getColumnCount(); i++) {
            if (i != 1) tablePerfTable.getColumnModel().getColumn(i).setCellRenderer(center);
        }

        JScrollPane scroll = new JScrollPane(tablePerfTable);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        tableCard.add(scroll, BorderLayout.CENTER);

        suite.add(tableCard, BorderLayout.CENTER);
        return suite;
    }

    // Suite 3: Guest CRM & Loyalty
    private JPanel createGuestLoyaltySuite() {
        JPanel suite = new JPanel(new BorderLayout(Theme.SPACE_16, 0));
        suite.setOpaque(false);
        suite.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_12, 0, 0, 0));

        // Left (60%): Top Loyal Guests Table + Inspect Button
        JPanel leftCard = new Card(14, 16);
        leftCard.setLayout(new BorderLayout(0, Theme.SPACE_8));

        JLabel title = new JLabel("Top Frequent Diners & VIPs");
        title.setFont(Theme.FONT_SECTION);
        title.setForeground(Theme.TEXT_PRIMARY);
        leftCard.add(title, BorderLayout.NORTH);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        topGuestsTable.getColumnModel().getColumn(0).setCellRenderer(center);
        topGuestsTable.getColumnModel().getColumn(3).setCellRenderer(center);
        topGuestsTable.getColumnModel().getColumn(4).setCellRenderer(center);
        topGuestsTable.getColumnModel().getColumn(6).setCellRenderer(center);

        JScrollPane scroll = new JScrollPane(topGuestsTable);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        leftCard.add(scroll, BorderLayout.CENTER);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actionRow.setOpaque(false);
        actionRow.add(inspectGuestBtn);
        leftCard.add(actionRow, BorderLayout.SOUTH);

        suite.add(leftCard, BorderLayout.CENTER);

        // Right (40%): Hospitality & Dietary Tag Distribution
        tagDistributionChart.setPreferredSize(new Dimension(380, 0));
        tagDistributionChart.setBarColor(Theme.ACCENT_GOLD);
        suite.add(tagDistributionChart, BorderLayout.EAST);

        return suite;
    }

    // Suite 4: Loss & Waitlist Prevention
    private JPanel createLossPreventionSuite() {
        JPanel suite = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        suite.setOpaque(false);
        suite.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_12, 0, 0, 0));

        // Top Row: No-Show Donut + Waitlist Funnel Chart
        JPanel topRow = new JPanel(new BorderLayout(Theme.SPACE_16, 0));
        topRow.setOpaque(false);
        topRow.setPreferredSize(new Dimension(0, 240));

        noShowDonutPanel.setPreferredSize(new Dimension(240, 240));
        topRow.add(noShowDonutPanel, BorderLayout.WEST);

        waitlistFunnelChart.setPreferredSize(new Dimension(0, 240));
        waitlistFunnelChart.setBarColor(Theme.SIDEBAR_BG);
        topRow.add(waitlistFunnelChart, BorderLayout.CENTER);

        suite.add(topRow, BorderLayout.NORTH);

        // Bottom: Operations Advisory Card
        JPanel adviceCard = new Card(14, 16);
        adviceCard.setLayout(new BorderLayout(0, Theme.SPACE_12));

        JLabel title = new JLabel("🛡️  Operational Reliability & Seat Loss Advisory");
        title.setFont(Theme.FONT_SECTION);
        title.setForeground(Theme.TEXT_PRIMARY);
        adviceCard.add(title, BorderLayout.NORTH);

        lossAdviceLabel.setFont(Theme.FONT_BODY);
        lossAdviceLabel.setForeground(Theme.TEXT_SECONDARY);
        adviceCard.add(lossAdviceLabel, BorderLayout.CENTER);

        suite.add(adviceCard, BorderLayout.CENTER);
        return suite;
    }

    // Suite 5: Data Explorer & CSV Export
    private JPanel createDataExplorerSuite() {
        JPanel suite = new JPanel(new BorderLayout(0, Theme.SPACE_12));
        suite.setOpaque(false);
        suite.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_12, 0, 0, 0));

        // Header Filter Card
        JPanel filterCard = new Card(12, 14);
        filterCard.setLayout(new BorderLayout(Theme.SPACE_16, 0));

        JPanel fLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_12, 0));
        fLeft.setOpaque(false);

        JLabel selLabel = new JLabel("Select Report Dataset:");
        selLabel.setFont(Theme.FONT_BODY_BOLD);
        selLabel.setForeground(Theme.TEXT_PRIMARY);
        fLeft.add(selLabel);

        Form.input(explorerReportCombo, 340);
        fLeft.add(explorerReportCombo);

        filterCard.add(fLeft, BorderLayout.WEST);

        explorerRowCountLabel.setFont(Theme.FONT_SMALL_BOLD);
        explorerRowCountLabel.setForeground(Theme.TEXT_MUTED);
        JPanel rRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rRight.setOpaque(false);
        rRight.add(explorerRowCountLabel);
        filterCard.add(rRight, BorderLayout.EAST);

        suite.add(filterCard, BorderLayout.NORTH);

        // Results Table
        JScrollPane scroll = new JScrollPane(explorerTable);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        suite.add(scroll, BorderLayout.CENTER);

        return suite;
    }

    private void setupEvents() {
        refreshBtn.addActionListener(e -> runCurrentDateQuery());
        exportCsvBtn.addActionListener(e -> exportActiveDataToCsv());
        inspectGuestBtn.addActionListener(e -> onInspectSelectedGuest());

        explorerReportCombo.addActionListener(e -> runExplorerQuery());
    }

    private void applyPreset(PeriodPreset preset) {
        this.currentPreset = preset;
        LocalDate today = DbClock.today();
        LocalDate from;
        LocalDate to = today;

        switch (preset) {
            case TODAY -> from = today;
            case LAST_7_DAYS -> from = today.minusDays(6);
            case LAST_30_DAYS -> from = today.minusDays(29);
            case THIS_MONTH -> from = today.withDayOfMonth(1);
            case CUSTOM -> {
                customDatePanel.setVisible(true);
                revalidate();
                repaint();
                return;
            }
            default -> from = today.minusDays(6);
        }

        customDatePanel.setVisible(false);
        fromDateSpinner.setValue(Date.from(from.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        toDateSpinner.setValue(Date.from(to.atStartOfDay(ZoneId.systemDefault()).toInstant()));

        loadAnalytics(from, to);
    }

    private void runCurrentDateQuery() {
        Date fDate = (Date) fromDateSpinner.getValue();
        Date tDate = (Date) toDateSpinner.getValue();
        LocalDate from = fDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate to = tDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        if (from.isAfter(to)) {
            Toast.error(SwingUtilities.getWindowAncestor(this), "Start date cannot be after end date.");
            return;
        }

        loadAnalytics(from, to);
    }

    private void loadAnalytics(LocalDate from, LocalDate to) {
        refreshBtn.setEnabled(false);
        refreshBtn.setText("Updating...");
        periodStatusLabel.setText(String.format("Analyzing %s to %s...", from.format(DATE_FMT), to.format(DATE_FMT)));

        Ui.async(
                () -> reportService.getAnalyticsDashboard(from, to),
                summary -> {
                    this.currentSummary = summary;
                    refreshBtn.setEnabled(true);
                    refreshBtn.setText("↻ Refresh");
                    displaySummary(summary);
                    periodStatusLabel.setText(String.format("Showing %s to %s · Updated at %s",
                            from.format(DATE_FMT), to.format(DATE_FMT),
                            java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))));
                    runExplorerQuery();
                },
                err -> {
                    refreshBtn.setEnabled(true);
                    refreshBtn.setText("↻ Refresh");
                    periodStatusLabel.setText("Failed to load analytics: " + err.getMessage());
                    Toast.error(SwingUtilities.getWindowAncestor(this), err.getMessage());
                }
        );
    }

    private void displaySummary(AnalyticsSummary summary) {
        // 1. Update Scorecard Cards
        coversCard.setValue(String.valueOf(summary.getTotalCovers()));
        coversCard.setSubtext(String.format("%d parties · %.1f avg", summary.getTotalParties(), summary.getAvgPartySize()));

        utilizationCard.setValue(String.format("%.1f%%", summary.getSeatUtilizationPct()));
        utilizationCard.setSubtext(summary.getSeatUtilizationPct() >= 70 ? "Optimal seat fill" : "Available capacity");

        lossCard.setValue(String.format("%.1f%%", summary.getNoShowPct()));
        lossCard.setSubtext(String.format("%d no-shows · %d cancels", summary.getNoShowCount(), summary.getCancellationCount()));

        loyaltyCard.setValue(String.format("%.1f%%", summary.getRepeatCustomerPct()));
        loyaltyCard.setSubtext(String.format("%d repeat parties", summary.getRepeatCustomerCount()));

        recoveryCard.setValue(String.valueOf(summary.getRecoveredCovers()));
        recoveryCard.setSubtext("Waitlist & walk-in saves");

        // 2. Suite 1: Overview Daily Trend & Slot Load
        List<String> trendDates = new ArrayList<>();
        List<Number> trendCovers = new ArrayList<>();
        for (DailyCoverTrend pt : summary.getDailyTrends()) {
            trendDates.add(pt.getDate().format(SHORT_DATE_FMT));
            trendCovers.add(pt.getCovers());
        }
        dailyCoversChart.setData("Daily Covers Velocity (Guests Served)", trendDates, trendCovers);

        List<String> slotLabels = new ArrayList<>();
        List<Number> slotCovers = new ArrayList<>();
        for (SlotLoadMetric sm : summary.getSlotMetrics()) {
            slotLabels.add(sm.getSlotLabel());
            slotCovers.add(sm.getTotalCovers());
        }
        slotLoadChart.setData("Time Slot Volume & Load", slotLabels, slotCovers);

        // Takeaways
        takeawaysBox.removeAll();
        for (String item : summary.getKeyTakeaways()) {
            JPanel row = new JPanel(new BorderLayout(Theme.SPACE_8, 0));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

            JLabel bullet = new JLabel("•");
            bullet.setFont(Theme.FONT_BODY_BOLD);
            bullet.setForeground(item.contains("Elevated") ? Theme.DANGER : (item.startsWith("✓") ? Theme.SUCCESS : Theme.ACCENT_GOLD));
            row.add(bullet, BorderLayout.WEST);

            JTextArea text = new JTextArea(item);
            text.setFont(Theme.FONT_BODY);
            text.setForeground(Theme.TEXT_SECONDARY);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setEditable(false);
            text.setOpaque(false);
            text.setFocusable(false);
            text.setBorder(null);
            row.add(text, BorderLayout.CENTER);

            takeawaysBox.add(row);
        }
        takeawaysBox.revalidate();
        takeawaysBox.repaint();

        // 3. Suite 2: Floor & Table Efficiency
        seatDonutPanel.setGauge("Seat Utilization", summary.getSeatUtilizationPct(),
                summary.getSeatUtilizationPct() >= 70 ? "High Efficiency" : "Capacity Open", false);

        List<String> tableLabels = new ArrayList<>();
        List<Number> tableCovers = new ArrayList<>();
        tablePerfModel.setRowCount(0);
        for (TablePerformanceMetric tm : summary.getTableMetrics()) {
            tableLabels.add(tm.getTableNumber() + " (" + tm.getLocation() + ")");
            tableCovers.add(tm.getTotalCovers());

            tablePerfModel.addRow(new Object[]{
                    tm.getTableNumber(),
                    tm.getLocation(),
                    tm.getCapacity() + " seats",
                    tm.getTurns() + " turns",
                    tm.getTotalCovers() + " covers",
                    String.format("%.1f", tm.getAvgPartySize()),
                    String.format("%.1f%%", tm.getSeatEfficiencyPct())
            });
        }
        tableLeaderboardChart.setData("Top Performing Tables Leaderboard", tableLabels, tableCovers, "covers");

        // 4. Suite 3: Guest CRM & Loyalty
        currentTopGuests = summary.getTopGuests();
        topGuestsModel.setRowCount(0);
        for (TopGuestMetric gm : currentTopGuests) {
            topGuestsModel.addRow(new Object[]{
                    "#" + gm.getCustomerId(),
                    gm.getName(),
                    gm.getPhone(),
                    gm.getVisitCount() + " visits",
                    gm.getTotalCovers() + " covers",
                    gm.getTags().isEmpty() ? "Standard" : gm.getTags(),
                    gm.getLastVisit() != null ? gm.getLastVisit().format(DATE_FMT) : "-"
            });
        }

        List<String> tags = new ArrayList<>();
        List<Number> tagCounts = new ArrayList<>();
        for (TagCountMetric tm : summary.getTagDistribution()) {
            tags.add(tm.getTag());
            tagCounts.add(tm.getCount());
        }
        tagDistributionChart.setData("Hospitality & Dietary Preferences", tags, tagCounts, "parties");

        // 5. Suite 4: Operations & Loss Prevention
        noShowDonutPanel.setGauge("No-Show Rate", summary.getNoShowPct(),
                summary.getNoShowPct() > 5.0 ? "Action Required" : "Healthy Range", true);

        List<String> funnelStages = new ArrayList<>();
        List<Number> funnelCounts = new ArrayList<>();
        for (WaitlistFunnelStep step : summary.getWaitlistFunnel()) {
            funnelStages.add(step.getStage() + " (" + step.getPctOfTotal() + "%)");
            funnelCounts.add(step.getCount());
        }
        waitlistFunnelChart.setData("Waitlist Conversion Funnel", funnelStages, funnelCounts, "entries");

        lossAdviceLabel.setText(String.format("<html>" +
                "<b>Operational Summary:</b><br>" +
                "• Recorded <b>%d no-show parties</b> and <b>%d cancellations</b> during this period.<br>" +
                "• Current no-show loss is <b>%.1f%%</b> (Industry benchmark &lt; 5.0%%).<br>" +
                "• Waitlist recovery successfully reclaimed <b>%d covers</b> back into active dining seatings.<br>" +
                "• <i>Recommendation:</i> Send proactive SMS reconfirmations 90 minutes before dinner rush slots." +
                "</html>",
                summary.getNoShowCount(), summary.getCancellationCount(), summary.getNoShowPct(), summary.getRecoveredCovers()));
    }

    private void runExplorerQuery() {
        int idx = explorerReportCombo.getSelectedIndex();
        Date fDate = (Date) fromDateSpinner.getValue();
        Date tDate = (Date) toDateSpinner.getValue();
        LocalDate from = fDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate to = tDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        Ui.async(
                () -> {
                    return switch (idx) {
                        case 0 -> reportService.getReservationsByDateRangeReport(from, to);
                        case 1 -> reportService.getMostBookedTablesReport();
                        case 2 -> reportService.getReservationsBySlotReport();
                        case 3 -> reportService.getNoShowAnalysisReport();
                        case 4 -> reportService.getWaitlistFunnelReport();
                        default -> reportService.getReservationsByDateRangeReport(from, to);
                    };
                },
                result -> {
                    explorerRowCountLabel.setText(result.getRowCount() + " records");
                    explorerModel.setDataVector(
                            result.getRows().toArray(new Object[0][0]),
                            result.getColumns().toArray()
                    );
                    DefaultTableCellRenderer center = new DefaultTableCellRenderer();
                    center.setHorizontalAlignment(SwingConstants.CENTER);
                    for (int i = 0; i < explorerTable.getColumnCount(); i++) {
                        String name = explorerTable.getColumnName(i).toLowerCase();
                        if (name.contains("id") || name.contains("count") || name.contains("capacity") ||
                            name.contains("guests") || name.contains("bookings") || name.contains("date") ||
                            name.contains("status")) {
                            explorerTable.getColumnModel().getColumn(i).setCellRenderer(center);
                        }
                    }
                },
                err -> Toast.error(SwingUtilities.getWindowAncestor(this), err.getMessage())
        );
    }

    private void onInspectSelectedGuest() {
        int row = topGuestsTable.getSelectedRow();
        if (row < 0 || row >= currentTopGuests.size()) {
            Toast.warning(SwingUtilities.getWindowAncestor(this), "Please select a guest from the table first.");
            return;
        }

        TopGuestMetric selected = currentTopGuests.get(row);
        Ui.async(
                () -> customerService.getCustomerById(selected.getCustomerId()),
                cust -> {
                    if (cust != null) {
                        CustomerDetailDialog.show(
                                SwingUtilities.getWindowAncestor(this),
                                cust,
                                customerService,
                                reservationService,
                                updated -> runCurrentDateQuery(),
                                bookFor -> {}
                        );
                    }
                },
                err -> Toast.error(SwingUtilities.getWindowAncestor(this), err.getMessage())
        );
    }

    private void exportActiveDataToCsv() {
        int activeTab = tabbedPane.getSelectedIndex();
        JTable tableToExport = switch (activeTab) {
            case 1 -> tablePerfTable;
            case 2 -> topGuestsTable;
            case 4 -> explorerTable;
            default -> (explorerTable.getRowCount() > 0 ? explorerTable : tablePerfTable);
        };

        if (tableToExport.getRowCount() == 0) {
            Toast.warning(SwingUtilities.getWindowAncestor(this), "No records to export.");
            return;
        }

        javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
        fileChooser.setDialogTitle("Export Analytics Dataset to CSV");
        fileChooser.setSelectedFile(new File("rtrs_analytics_" + System.currentTimeMillis() + ".csv"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == javax.swing.JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            try (PrintWriter pw = new PrintWriter(new FileWriter(fileToSave))) {
                for (int c = 0; c < tableToExport.getColumnCount(); c++) {
                    pw.print("\"" + tableToExport.getColumnName(c).replace("\"", "\"\"") + "\"");
                    if (c < tableToExport.getColumnCount() - 1) pw.print(",");
                }
                pw.println();

                for (int r = 0; r < tableToExport.getRowCount(); r++) {
                    for (int c = 0; c < tableToExport.getColumnCount(); c++) {
                        Object val = tableToExport.getValueAt(r, c);
                        String s = val != null ? val.toString() : "";
                        pw.print("\"" + s.replace("\"", "\"\"") + "\"");
                        if (c < tableToExport.getColumnCount() - 1) pw.print(",");
                    }
                    pw.println();
                }
                Toast.success(SwingUtilities.getWindowAncestor(this), "Exported dataset to " + fileToSave.getName());
            } catch (Exception ex) {
                Toast.error(SwingUtilities.getWindowAncestor(this), "Export failed: " + ex.getMessage());
            }
        }
    }
}
