package ui.components;

import model.Customer;
import model.Reservation;
import model.RestaurantTable;
import model.TimeSlot;
import ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.time.format.DateTimeFormatter;

/**
 * Ticket-style confirmation modal dialog presented upon successful table reservation.
 */
public class BookingConfirmedDialog extends JDialog {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public BookingConfirmedDialog(Window owner, Reservation reservation, Customer customer, 
                                  RestaurantTable table, TimeSlot slot, Runnable onBookAnother) {
        super(owner, "Reservation Confirmed", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout());
        setResizable(false);

        JPanel ticketPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(Theme.CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);

                // Dashed separator line
                g2.setColor(Theme.CARD_BORDER);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{6.0f, 6.0f}, 0.0f));
                g2.drawLine(24, 76, getWidth() - 24, 76);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        ticketPanel.setOpaque(false);
        ticketPanel.setLayout(new BoxLayout(ticketPanel, BoxLayout.Y_AXIS));
        ticketPanel.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        ticketPanel.setPreferredSize(new Dimension(500, 360));

        // Header Title
        JLabel badge = new JLabel("✓  RESERVATION CONFIRMED", JLabel.CENTER);
        badge.setFont(Theme.FONT_SECTION);
        badge.setForeground(Theme.SUCCESS);
        badge.setAlignmentX(0.5f);
        ticketPanel.add(badge);

        ticketPanel.add(Box.createVerticalStrut(36));

        // Details Grid (2 columns: Label / Value)
        JPanel grid = new JPanel(new GridLayout(4, 2, 24, 14));
        grid.setOpaque(false);

        grid.add(createPair("Reservation ID", reservation.getDisplayCode(), true));
        grid.add(createPair("Customer", customer != null ? customer.getName() : "Guest", false));

        grid.add(createPair("Table", table != null ? table.getTableNumber() + " (" + table.getLocation() + ")" : "", true));
        grid.add(createPair("Date", reservation.getReservationDate().format(DATE_FMT), false));

        grid.add(createPair("Time Slot", slot != null ? slot.getLabel() : "", false));
        grid.add(createPair("Guests", String.valueOf(reservation.getGuestCount()), false));

        grid.add(createPair("Status", reservation.getStatus().name(), false));
        grid.add(createPair("Phone", customer != null ? customer.getPhone() : "", false));

        ticketPanel.add(grid);
        ticketPanel.add(Box.createVerticalStrut(28));

        // Action Buttons Row
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, Theme.SPACE_16, 0));
        buttonRow.setOpaque(false);

        JButton doneBtn = AppButton.secondary("Done");
        doneBtn.addActionListener(e -> dispose());
        buttonRow.add(doneBtn);

        JButton bookAnotherBtn = AppButton.primary("Book another");
        bookAnotherBtn.addActionListener(e -> {
            dispose();
            if (onBookAnother != null) {
                onBookAnother.run();
            }
        });
        buttonRow.add(bookAnotherBtn);

        ticketPanel.add(buttonRow);
        add(ticketPanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(owner);
        if (isUndecorated()) {
            try {
                setOpacity(0f);
                ui.anim.Anim.run(240, ui.anim.Anim.EASE_OUT, p -> {
                    try {
                        setOpacity((float) Math.max(0, Math.min(1, p)));
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
                // translucency unsupported: show immediately
            }
        }

        // Escape key to close
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private JPanel createPair(String label, String value, boolean isPrimary) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);

        JLabel l = new JLabel(label + " :");
        l.setFont(Theme.FONT_SMALL_BOLD);
        l.setForeground(Theme.TEXT_MUTED);

        JLabel v = new JLabel(value);
        v.setFont(isPrimary ? Theme.FONT_BODY_BOLD : Theme.FONT_BODY);
        v.setForeground(Theme.TEXT_PRIMARY);

        p.add(l, BorderLayout.WEST);
        p.add(v, BorderLayout.CENTER);
        return p;
    }
}
