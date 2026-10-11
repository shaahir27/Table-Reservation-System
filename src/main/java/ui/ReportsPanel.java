package ui;

import dao.ReportResult;
import service.ReportService;
import ui.components.ChipRow;
import ui.components.AppButton;
import ui.components.BarChartPanel;
import ui.components.Card;
import ui.components.Form;
import ui.components.StyledTable;
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
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Analytical Reporting & Business Intelligence View (Section 10.7).
 * Supports the 5 core operational reports with tabular display and custom BarChart visualization.
 */
public class ReportsPanel extends BasePanel {
    private static final String REPORT_TODAY = "Today's Reservations";
    private static final String REPORT_DATE_RANGE = "Reservations by Date Range";
    private static final String REPORT_STATUS = "Reservations by Status";
    private static final String REPORT_POPULAR_TABLES = "Most Frequently Booked Tables";
    private static final String REPORT_TIME_SLOT = "Reservation Count by Time Slot";
    private static final String REPORT_NO_SHOW = "Historical No-Show Analysis";
    private static final String REPORT_RECOVERED_COVERS = "Recovered Covers by Source";
    private static final String REPORT_SLOT_UTILISATION = "Dining Slot Utilisation";
    private static final String REPORT_WAITLIST_FUNNEL = "Waitlist Conversion Funnel";

    private final ReportService reportService;

    // Controls
    private final JComboBox<String> reportCombo = new JComboBox<>(new String[]{
            REPORT_TODAY,
            REPORT_DATE_RANGE,
            REPORT_STATUS,
            REPORT_POPULAR_TABLES,
            REPORT_TIME_SLOT,
            REPORT_NO_SHOW,
            REPORT_RECOVERED_COVERS,
            REPORT_SLOT_UTILISATION,
            REPORT_WAITLIST_FUNNEL
    });

    private final JLabel fromLabel = new JLabel("From:");
    private final JSpinner fromDateSpinner = new JSpinner(new SpinnerDateModel());
    private final JLabel toLabel = new JLabel("To:");
    private final JSpinner toDateSpinner = new JSpinner(new SpinnerDateModel());
    private final JPanel dateRangeRow = new JPanel();
    private final JButton runReportBtn;
    private final JButton exportCsvBtn;

    // Table & Chart
    private final DefaultTableModel tableModel;
    private final JTable reportTable;
    private final JLabel reportTitleLabel = new JLabel("Select a report");
    private final JLabel rowCountLabel = new JLabel("");
    private final BarChartPanel barChartPanel = new BarChartPanel();
    private final JPanel chartContainer = new JPanel(new BorderLayout());

    public ReportsPanel() {
        this(new ReportService());
    }

    public ReportsPanel(ReportService reportService) {
        super("Reports & Analytics");
        this.reportService = reportService;

        runReportBtn = createPrimaryButton("Run Report");
        exportCsvBtn = createSecondaryButton("Export CSV");

        // Format Date Spinners
        fromDateSpinner.setEditor(new JSpinner.DateEditor(fromDateSpinner, "dd-MM-yyyy"));
        toDateSpinner.setEditor(new JSpinner.DateEditor(toDateSpinner, "dd-MM-yyyy"));
        Form.input(fromDateSpinner, 140);
        Form.input(toDateSpinner, 140);

        // Default toDate = today, fromDate = 7 days ago
        Date now = new Date();
        toDateSpinner.setValue(now);
        Date weekAgo = Date.from(util.DbClock.today().minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant());
        fromDateSpinner.setValue(weekAgo);

        // Table setup
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        reportTable = new StyledTable(tableModel).emptyMessage("This report returned no rows for the selected period.");

        setupEvents();
        initLayout();
        updateDateControlsVisibility();
    }

    @Override
    public void onShown() {
        // always refresh: report data changes as the service day progresses
        executeCurrentReport(false);
    }

    private void setupEvents() {
        reportCombo.addActionListener(e -> {
            updateDateControlsVisibility();
            if (isShowing()) executeCurrentReport(false);
        });
        runReportBtn.addActionListener(e -> executeCurrentReport(true));
        runReportBtn.setToolTipText("Execute query and refresh data from database with current filters");
        exportCsvBtn.addActionListener(e -> exportToCsv());
    }

    private void updateDateControlsVisibility() {
        String sel = (String) reportCombo.getSelectedItem();
        boolean isDateRange = REPORT_DATE_RANGE.equals(sel) || REPORT_SLOT_UTILISATION.equals(sel);
        dateRangeRow.setVisible(isDateRange);
        revalidate();
        repaint();
    }

    private void initLayout() {
        JPanel mainContent = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        mainContent.setOpaque(false);

        // Filter Bar Card (Spacious 2-Row Layout)
        JPanel filterCard = createCardPanel();
        filterCard.setLayout(new BoxLayout(filterCard, BoxLayout.Y_AXIS));

        // Row 1: Report selector + Run button
        JPanel row1 = new ChipRow(Theme.SPACE_12);
        row1.setOpaque(false);
        row1.setAlignmentX(0.0f);

        JLabel selectLabel = new JLabel("Report:");
        selectLabel.setFont(Theme.FONT_BODY_BOLD);
        selectLabel.setForeground(Theme.TEXT_PRIMARY);

        Form.input(reportCombo, 320);
        reportCombo.setFont(Theme.FONT_BODY);

        row1.add(selectLabel);
        row1.add(reportCombo);
        row1.add(runReportBtn);
        row1.add(exportCsvBtn);

        // Row 2: Date range controls (visible only for custom date range reports)
        dateRangeRow.setLayout(ChipRow.layout(Theme.SPACE_12, false));
        dateRangeRow.setOpaque(false);
        dateRangeRow.setAlignmentX(0.0f);
        dateRangeRow.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_12, 0, 0, 0));

        fromLabel.setFont(Theme.FONT_BODY_BOLD);
        fromLabel.setForeground(Theme.TEXT_PRIMARY);
        toLabel.setFont(Theme.FONT_BODY_BOLD);
        toLabel.setForeground(Theme.TEXT_PRIMARY);

        dateRangeRow.add(fromLabel);
        dateRangeRow.add(fromDateSpinner);
        dateRangeRow.add(Box.createHorizontalStrut(Theme.SPACE_8));
        dateRangeRow.add(toLabel);
        dateRangeRow.add(toDateSpinner);

        filterCard.add(row1);
        filterCard.add(dateRangeRow);

        mainContent.add(filterCard, BorderLayout.NORTH);

        // Results Card
        JPanel resultsCard = createCardPanel();
        resultsCard.setLayout(new BorderLayout(0, Theme.SPACE_12));

        // Header for Results
        JPanel resultsHeader = new JPanel(new BorderLayout());
        resultsHeader.setOpaque(false);

        reportTitleLabel.setFont(Theme.FONT_SECTION);
        reportTitleLabel.setForeground(Theme.TEXT_PRIMARY);

        rowCountLabel.setFont(Theme.FONT_BODY_BOLD);
        rowCountLabel.setForeground(Theme.TEXT_MUTED);

        resultsHeader.add(reportTitleLabel, BorderLayout.WEST);
        resultsHeader.add(rowCountLabel, BorderLayout.EAST);
        resultsCard.add(resultsHeader, BorderLayout.NORTH);

        // Center split/stack containing BarChart (if applicable) and Table
        chartContainer.setOpaque(false);
        chartContainer.setVisible(false);
        chartContainer.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.SPACE_16, 0));
        chartContainer.add(barChartPanel, BorderLayout.CENTER);

        JScrollPane scrollPane = new JScrollPane(reportTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.NEUTRAL_TINT, 1));
        scrollPane.getViewport().setBackground(Theme.CARD_BG);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(chartContainer, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        resultsCard.add(centerPanel, BorderLayout.CENTER);

        mainContent.add(resultsCard, BorderLayout.CENTER);
        contentPanel.add(mainContent, BorderLayout.CENTER);
    }

    private void executeCurrentReport(boolean userInitiated) {
        String selected = (String) reportCombo.getSelectedItem();
        if (selected == null) return;

        runReportBtn.setEnabled(false);
        runReportBtn.setText("Generating...");

        Ui.async(
                () -> {
                    switch (selected) {
                        case REPORT_TODAY:
                            return reportService.getTodaysReservationsReport();
                        case REPORT_DATE_RANGE: {
                            Date fDate = (Date) fromDateSpinner.getValue();
                            Date tDate = (Date) toDateSpinner.getValue();
                            LocalDate from = fDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                            LocalDate to = tDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                            return reportService.getReservationsByDateRangeReport(from, to);
                        }
                        case REPORT_STATUS:
                            return reportService.getReservationsByStatusReport();
                        case REPORT_POPULAR_TABLES:
                            return reportService.getMostBookedTablesReport();
                        case REPORT_TIME_SLOT:
                            return reportService.getReservationsBySlotReport();
                        case REPORT_NO_SHOW:
                            return reportService.getNoShowAnalysisReport();
                        case REPORT_RECOVERED_COVERS:
                            return reportService.getRecoveredCoversReport();
                        case REPORT_SLOT_UTILISATION: {
                            Date fDate = (Date) fromDateSpinner.getValue();
                            Date tDate = (Date) toDateSpinner.getValue();
                            LocalDate from = fDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                            LocalDate to = tDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                            return reportService.getSlotUtilisationReport(from, to);
                        }
                        case REPORT_WAITLIST_FUNNEL:
                            return reportService.getWaitlistFunnelReport();
                        default:
                            return reportService.getTodaysReservationsReport();
                    }
                },
                result -> {
                    runReportBtn.setEnabled(true);
                    runReportBtn.setText("Run Report");
                    displayReportResult(result, selected);
                    if (userInitiated) {
                        Toast.success(SwingUtilities.getWindowAncestor(this),
                                "✓ Report generated: " + selected + " (" + result.getRowCount() + " records)");
                    }
                },
                error -> {
                    runReportBtn.setEnabled(true);
                    runReportBtn.setText("Run Report");
                    Toast.error(SwingUtilities.getWindowAncestor(this), error.getMessage());
                }
        );
    }

    private void displayReportResult(ReportResult result, String reportType) {
        reportTitleLabel.setText(result.getReportTitle());
        String timeStr = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm:ss a"));
        rowCountLabel.setText(result.getRowCount() + (result.getRowCount() == 1 ? " record" : " records") + "  ·  Updated at " + timeStr);

        // Rebuild Table Model
        tableModel.setDataVector(
                result.getRows().toArray(new Object[0][0]),
                result.getColumns().toArray()
        );

        // Center specific columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 0; i < reportTable.getColumnCount(); i++) {
            String colName = reportTable.getColumnName(i).toLowerCase();
            if (colName.contains("id") || colName.contains("count") || colName.contains("capacity") ||
                colName.contains("guests") || colName.contains("bookings")) {
                reportTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
            }
        }

        // Bar chart for the categorical reports: category = first column, value = last fully numeric column
        boolean chartable = REPORT_STATUS.equals(reportType) || REPORT_TIME_SLOT.equals(reportType)
                || REPORT_POPULAR_TABLES.equals(reportType) || REPORT_RECOVERED_COVERS.equals(reportType)
                || REPORT_WAITLIST_FUNNEL.equals(reportType);
        int valueCol = -1;
        if (chartable && !result.getRows().isEmpty()) {
            for (int c = result.getColumns().size() - 1; c >= 1 && valueCol < 0; c--) {
                boolean numeric = true;
                for (Object[] row : result.getRows()) {
                    try {
                        Double.parseDouble(String.valueOf(row[c]));
                    } catch (Exception e) {
                        numeric = false;
                        break;
                    }
                }
                if (numeric) valueCol = c;
            }
        }
        if (valueCol >= 0) {
            List<String> categories = new ArrayList<>();
            List<Number> values = new ArrayList<>();
            for (Object[] row : result.getRows()) {
                categories.add(String.valueOf(row[0]));
                values.add(Double.parseDouble(String.valueOf(row[valueCol])));
            }
            barChartPanel.setSemanticColors(REPORT_STATUS.equals(reportType));
            barChartPanel.setData(result.getReportTitle() + " (Visual Distribution)", categories, values);
            chartContainer.setVisible(true);
        } else {
            chartContainer.setVisible(false);
        }
        if (reportTable instanceof StyledTable st) st.reveal();

        revalidate();
        repaint();
    }

    private JPanel createCardPanel() {
        return new Card(18, Theme.SPACE_24);
    }

    private JButton createPrimaryButton(String text) {
        return AppButton.primary(text);
    }

    private JButton createSecondaryButton(String text) {
        return AppButton.secondary(text);
    }

    private void exportToCsv() {
        if (tableModel.getRowCount() == 0) {
            Toast.warning(SwingUtilities.getWindowAncestor(this), "No data to export. Run a report first.");
            return;
        }

        javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
        fileChooser.setDialogTitle("Export Report to CSV");
        fileChooser.setSelectedFile(new java.io.File("rtrs_report_" + System.currentTimeMillis() + ".csv"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == javax.swing.JFileChooser.APPROVE_OPTION) {
            java.io.File fileToSave = fileChooser.getSelectedFile();
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(fileToSave))) {
                for (int c = 0; c < tableModel.getColumnCount(); c++) {
                    pw.print("\"" + tableModel.getColumnName(c).replace("\"", "\"\"") + "\"");
                    if (c < tableModel.getColumnCount() - 1) pw.print(",");
                }
                pw.println();

                for (int r = 0; r < tableModel.getRowCount(); r++) {
                    for (int c = 0; c < tableModel.getColumnCount(); c++) {
                        Object val = tableModel.getValueAt(r, c);
                        String s = val != null ? val.toString() : "";
                        pw.print("\"" + s.replace("\"", "\"\"") + "\"");
                        if (c < tableModel.getColumnCount() - 1) pw.print(",");
                    }
                    pw.println();
                }
                Toast.success(SwingUtilities.getWindowAncestor(this), "Report exported to " + fileToSave.getName());
            } catch (Exception ex) {
                Toast.error(SwingUtilities.getWindowAncestor(this), "Export failed: " + ex.getMessage());
            }
        }
    }
}
