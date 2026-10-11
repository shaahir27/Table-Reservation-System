package ui.components;

import ui.Theme;

import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modern date selector featuring quick date chips (Today, Tomorrow, +4 days)
 * paired with a JSpinner date picker for custom dates.
 */
public class DateSelector extends JPanel {
    private static final DateTimeFormatter CHIP_FMT = DateTimeFormatter.ofPattern("EEE dd");

    private final ButtonGroup chipGroup = new ButtonGroup();
    private final List<DateChipButton> chipButtons = new ArrayList<>();
    private final JSpinner dateSpinner;
    private final SpinnerDateModel spinnerModel;
    private LocalDate selectedDate;
    private Consumer<LocalDate> onDateChanged;
    private boolean updatingInternal = false;

    private final boolean allowPast;
    private final boolean showChips;
    private final ChipRow row;

    public DateSelector() {
        this(false);
    }

    /** @param allowPast true for history views (audit, reports) where past dates are valid choices. */
    public DateSelector(boolean allowPast) {
        this(allowPast, true);
    }

    /** @param showChips false renders just the date field (compact filter bars). */
    public DateSelector(boolean allowPast, boolean showChips) {
        this.allowPast = allowPast;
        this.showChips = showChips;
        setLayout(new java.awt.BorderLayout());
        row = new ChipRow(Theme.SPACE_8);
        add(row, java.awt.BorderLayout.CENTER);
        setOpaque(false);

        selectedDate = util.DbClock.today();

        // 1. Quick chips: forward-looking for booking screens, backward-looking for history views
        if (!showChips) {
            // compact mode: no chips
        } else if (allowPast) {
            addDateChip("Today", selectedDate);
            addDateChip("Yesterday", selectedDate.minusDays(1));
            addDateChip("7 days ago", selectedDate.minusDays(7));
            addDateChip("30 days ago", selectedDate.minusDays(30));
        } else {
            addDateChip("Today", selectedDate);
            addDateChip("Tomorrow", selectedDate.plusDays(1));
            for (int i = 2; i <= 5; i++) {
                LocalDate d = selectedDate.plusDays(i);
                addDateChip(d.format(CHIP_FMT), d);
            }
        }

        // Default select "Today"
        if (!chipButtons.isEmpty()) {
            chipButtons.get(0).setSelected(true);
        }

        // 2. Spinner for "Other date"
        Date todayDate = Date.from(selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        spinnerModel = new SpinnerDateModel(todayDate, allowPast ? null : todayDate, null, Calendar.DAY_OF_MONTH);
        dateSpinner = new JSpinner(spinnerModel);
        JSpinner.DateEditor editor = new JSpinner.DateEditor(dateSpinner, "dd-MM-yyyy");
        dateSpinner.setEditor(editor);
        Form.input(dateSpinner, 138);

        dateSpinner.addChangeListener(e -> {
            if (updatingInternal) return;
            Date val = (Date) dateSpinner.getValue();
            LocalDate newDate = Instant.ofEpochMilli(val.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
            if (!allowPast && newDate.isBefore(util.DbClock.today())) {
                newDate = util.DbClock.today();
                dateSpinner.setValue(Date.from(newDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            }
            setDate(newDate, false);
        });

        JLabel otherLabel = new JLabel(allowPast ? "Pick date:" : "Other date:");
        otherLabel.setFont(Theme.FONT_SMALL_BOLD);
        otherLabel.setForeground(Theme.TEXT_MUTED);

        if (showChips) row.add(otherLabel);
        row.add(dateSpinner);
    }

    public void setOnDateChanged(Consumer<LocalDate> onDateChanged) {
        this.onDateChanged = onDateChanged;
    }

    public LocalDate getDate() {
        return selectedDate;
    }

    public void setDate(LocalDate newDate) {
        setDate(newDate, true);
    }

    private void setDate(LocalDate newDate, boolean updateSpinner) {
        this.selectedDate = newDate;

        updatingInternal = true;
        try {
            // Check if matches one of our chip buttons
            boolean matched = false;
            for (DateChipButton chip : chipButtons) {
                if (chip.chipDate.isEqual(newDate)) {
                    chip.setSelected(true);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                chipGroup.clearSelection();
            }

            if (updateSpinner) {
                Date d = Date.from(newDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
                dateSpinner.setValue(d);
            }
        } finally {
            updatingInternal = false;
        }

        if (onDateChanged != null) {
            onDateChanged.accept(selectedDate);
        }
    }

    public boolean isAllowPast() {
        return allowPast;
    }

    public boolean isShowChips() {
        return showChips;
    }

    private void addDateChip(String label, LocalDate date) {
        DateChipButton btn = new DateChipButton(label, date);
        chipGroup.add(btn);
        chipButtons.add(btn);
        row.add(btn);

        btn.addActionListener(e -> {
            if (updatingInternal) return;
            setDate(date, true);
        });
    }

    private static class DateChipButton extends ChipToggle {
        private final LocalDate chipDate;

        public DateChipButton(String label, LocalDate chipDate) {
            super(label, ChipToggle.Style.GOLD);
            this.chipDate = chipDate;
            minWidth(84);
        }
    }
}
