package ui.components;

import ui.Theme;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerDateModel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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

    public DateSelector() {
        setLayout(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_8, 0));
        setOpaque(false);

        selectedDate = LocalDate.now();

        // 1. Quick Chips: Today, Tomorrow, +4 consecutive days
        addDateChip("Today", selectedDate);
        addDateChip("Tomorrow", selectedDate.plusDays(1));
        for (int i = 2; i <= 5; i++) {
            LocalDate d = selectedDate.plusDays(i);
            addDateChip(d.format(CHIP_FMT), d);
        }

        // Default select "Today"
        if (!chipButtons.isEmpty()) {
            chipButtons.get(0).setSelected(true);
        }

        // 2. Spinner for "Other date"
        Date todayDate = Date.from(selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        spinnerModel = new SpinnerDateModel(todayDate, todayDate, null, Calendar.DAY_OF_MONTH);
        dateSpinner = new JSpinner(spinnerModel);
        JSpinner.DateEditor editor = new JSpinner.DateEditor(dateSpinner, "dd-MM-yyyy");
        dateSpinner.setEditor(editor);
        dateSpinner.setPreferredSize(new Dimension(125, 36));

        dateSpinner.addChangeListener(e -> {
            if (updatingInternal) return;
            Date val = (Date) dateSpinner.getValue();
            LocalDate newDate = Instant.ofEpochMilli(val.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
            if (newDate.isBefore(LocalDate.now())) {
                newDate = LocalDate.now();
                dateSpinner.setValue(Date.from(newDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            }
            setDate(newDate, false);
        });

        JLabel otherLabel = new JLabel("Other date:");
        otherLabel.setFont(Theme.FONT_SMALL_BOLD);
        otherLabel.setForeground(Theme.TEXT_MUTED);

        add(Box.createHorizontalStrut(Theme.SPACE_8));
        add(otherLabel);
        add(dateSpinner);
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

    private void addDateChip(String label, LocalDate date) {
        DateChipButton btn = new DateChipButton(label, date);
        chipGroup.add(btn);
        chipButtons.add(btn);
        add(btn);

        btn.addActionListener(e -> {
            if (updatingInternal) return;
            setDate(date, true);
        });
    }

    private static class DateChipButton extends JToggleButton {
        private final LocalDate chipDate;

        public DateChipButton(String label, LocalDate chipDate) {
            super(label);
            this.chipDate = chipDate;
            setFont(Theme.FONT_BODY);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(86, 36));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (isSelected()) {
                g2.setColor(Theme.ACCENT_GOLD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                setForeground(Color.WHITE);
                setFont(Theme.FONT_BODY_BOLD);
            } else {
                g2.setColor(Theme.CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setColor(getModel().isRollover() ? Theme.ACCENT_GOLD : Theme.CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                setForeground(Theme.TEXT_PRIMARY);
                setFont(Theme.FONT_BODY);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
