package ui;

import dao.ReportResult;
import service.ReportService;
import ui.components.BarChartPanel;
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
import javax.swing.JSplitPane;
import javax.swing.JTable;
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

    private final ReportService reportService;

    // Controls
    private final JComboBox<String> reportCombo = new JComboBox<>(new String[]{
            REPORT_TODAY,
            REPORT_DATE_RANGE,
            REPORT_STATUS,
            REPORT_POPULAR_TABLES,
            REPORT_TIME_SLOT
    });

    private final JLabel fromLabel = new JLabel("From:");
    private final JSpinner fromDateSpinner = new JSpinner(new SpinnerDateModel());
    private final JLabel toLabel = new JLabel("To:");
    private final JSpinner toDateSpinner = new JSpinner(new SpinnerDateModel());
    private final JButton runReportBtn;

    // Table & Chart
    private final DefaultTableModel tableModel;
    private final JTable reportTable;
    private final JLabel reportTitleLabel = new JLabel("Select a report and click 'Run Report'");
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

        // Format Date Spinners
        fromDateSpinner.setEditor(new JSpinner.DateEditor(fromDateSpinner, "dd-MM-yyyy"));
        toDateSpinner.setEditor(new JSpinner.DateEditor(toDateSpinner, "dd-MM-yyyy"));
        fromDateSpinner.setPreferredSize(new Dimension(115, 36));
        toDateSpinner.setPreferredSize(new Dimension(115, 36));

        // Default toDate = today, fromDate = 7 days ago
        Date now = new Date();
        toDateSpinner.setValue(now);
        Date weekAgo = Date.from(LocalDate.now().minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant());
        fromDateSpinner.setValue(weekAgo);

        // Table setup
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        reportTable = new JTable(tableModel);
        reportTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        reportTable.setRowHeight(38);
        reportTable.getTableHeader().setPreferredSize(new Dimension(0, 40));
        reportTable.getTableHeader().setFont(Theme.FONT_BODY_BOLD);
        reportTable.setFont(Theme.FONT_BODY);

        setupEvents();
        initLayout();
        updateDateControlsVisibility();
    }

    @Override
    public void onShown() {
        // Auto-run first report if not already executed
        if (tableModel.getColumnCount() == 0) {
            executeCurrentReport();
        }
    }

    private void setupEvents() {
        reportCombo.addActionListener(e -> updateDateControlsVisibility());
        runReportBtn.addActionListener(e -> executeCurrentReport());
    }

    private void updateDateControlsVisibility() {
        boolean isDateRange = REPORT_DATE_RANGE.equals(reportCombo.getSelectedItem());
        fromLabel.setVisible(isDateRange);
        fromDateSpinner.setVisible(isDateRange);
        toLabel.setVisible(isDateRange);
        toDateSpinner.setVisible(isDateRange);
        revalidate();
        repaint();
    }

    private void initLayout() {
        JPanel mainContent = new JPanel(new BorderLayout(0, Theme.SPACE_16));
        mainContent.setOpaque(false);

        // Filter Bar Card
        JPanel filterCard = createCardPanel();
        filterCard.setLayout(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_12, Theme.SPACE_8));

        JLabel selectLabel = new JLabel("Report:");
        selectLabel.setFont(Theme.FONT_BODY_BOLD);
        selectLabel.setForeground(Theme.TEXT_PRIMARY);

        reportCombo.setPreferredSize(new Dimension(260, 36));
        reportCombo.setFont(Theme.FONT_BODY);

        fromLabel.setFont(Theme.FONT_BODY_BOLD);
        toLabel.setFont(Theme.FONT_BODY_BOLD);

        filterCard.add(selectLabel);
        filterCard.add(reportCombo);
        filterCard.add(fromLabel);
        filterCard.add(fromDateSpinner);
        filterCard.add(toLabel);
        filterCard.add(toDateSpinner);
        filterCard.add(runReportBtn);

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
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        scrollPane.getViewport().setBackground(Theme.CARD_BG);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(chartContainer, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        resultsCard.add(centerPanel, BorderLayout.CENTER);

        mainContent.add(resultsCard, BorderLayout.CENTER);
        contentPanel.add(mainContent, BorderLayout.CENTER);
    }

    private void executeCurrentReport() {
        String selected = (String) reportCombo.getSelectedItem();
        if (selected == null) return;

        runReportBtn.setEnabled(false);
        runReportBtn.setText("Generating...");

        Ui.async(
                () -> {
                    switch (selected) {
                        case REPORT_TODAY:
                            return reportService.getTodaysReservationsReport();
                        case REPORT_DATE_RANGE:
                            Date fDate = (Date) fromDateSpinner.getValue();
                            Date tDate = (Date) toDateSpinner.getValue();
                            LocalDate from = fDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                            LocalDate to = tDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                            return reportService.getReservationsByDateRangeReport(from, to);
                        case REPORT_STATUS:
                            return reportService.getReservationsByStatusReport();
                        case REPORT_POPULAR_TABLES:
                            return reportService.getMostBookedTablesReport();
                        case REPORT_TIME_SLOT:
                            return reportService.getReservationsBySlotReport();
                        default:
                            return reportService.getTodaysReservationsReport();
                    }
                },
                result -> {
                    runReportBtn.setEnabled(true);
                    runReportBtn.setText("Run Report");
                    displayReportResult(result, selected);
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
        rowCountLabel.setText(result.getRowCount() + (result.getRowCount() == 1 ? " record" : " records"));

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

        // Configure Chart for Status and Time Slot reports (Section 10.7)
        if (REPORT_STATUS.equals(reportType) || REPORT_TIME_SLOT.equals(reportType)) {
            List<String> categories = new ArrayList<>();
            List<Number> values = new ArrayList<>();

            for (Object[] row : result.getRows()) {
                if (row.length >= 2) {
                    categories.add(String.valueOf(row[0]));
                    try {
                        values.add(Double.parseDouble(String.valueOf(row[row.length - 1])));
                    } catch (Exception e) {
                        values.add(0);
                    }
                }
            }

            barChartPanel.setData(result.getReportTitle() + " (Visual Distribution)", categories, values);
            chartContainer.setVisible(true);
        } else {
            chartContainer.setVisible(false);
        }

        revalidate();
        repaint();
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
        btn.setPreferredSize(new Dimension(130, 36));
        return btn;
    }
}
