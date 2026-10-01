package dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generic tabular data result for reporting queries.
 */
public class ReportResult {
    private final String reportTitle;
    private final List<String> columns;
    private final List<Object[]> rows;

    public ReportResult(String reportTitle, List<String> columns, List<Object[]> rows) {
        this.reportTitle = reportTitle;
        this.columns = columns != null ? Collections.unmodifiableList(new ArrayList<>(columns)) : Collections.emptyList();
        this.rows = rows != null ? Collections.unmodifiableList(new ArrayList<>(rows)) : Collections.emptyList();
    }

    public String getReportTitle() {
        return reportTitle;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<Object[]> getRows() {
        return rows;
    }

    public int getRowCount() {
        return rows.size();
    }
}
