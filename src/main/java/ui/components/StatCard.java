package ui.components;

import ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Metric card displaying a primary figure, title, and contextual subtitle (Section 9 & 10.2).
 */
public class StatCard extends JPanel {
    private final JLabel valueLabel;
    private final JLabel titleLabel;
    private final JLabel subtextLabel;

    public StatCard(String title, String initialValue, String subtext) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(20, Theme.SPACE_24, 20, Theme.SPACE_24));
        setPreferredSize(new Dimension(240, 125));
        setMinimumSize(new Dimension(180, 115));

        titleLabel = new JLabel(title.toUpperCase());
        titleLabel.setFont(Theme.FONT_SMALL_BOLD);
        titleLabel.setForeground(Theme.TEXT_MUTED);

        valueLabel = new JLabel(initialValue);
        valueLabel.setFont(Theme.FONT_STAT_NUM);
        valueLabel.setForeground(Theme.TEXT_PRIMARY);

        subtextLabel = new JLabel(subtext);
        subtextLabel.setFont(Theme.FONT_SMALL);
        subtextLabel.setForeground(Theme.TEXT_MUTED);

        add(titleLabel);
        add(Box.createVerticalStrut(Theme.SPACE_8));
        add(valueLabel);
        add(Box.createVerticalStrut(Theme.SPACE_8));
        add(subtextLabel);
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }

    public void setSubtext(String subtext) {
        subtextLabel.setText(subtext);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Card background
        g2.setColor(Theme.CARD_BG);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);

        // Subtle border
        g2.setColor(Theme.CARD_BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);

        g2.dispose();
        super.paintComponent(g);
    }
}
