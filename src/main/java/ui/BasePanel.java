package ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.format.DateTimeFormatter;

/**
 * Common base panel for all primary application views.
 * Provides a standardized header with page title, optional subtitle, today's date chip and container padding.
 */
public abstract class BasePanel extends JPanel {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy");

    protected final JPanel headerPanel;
    protected final JLabel titleLabel;
    protected final JLabel subtitleLabel;
    protected final JLabel dateLabel;
    protected final JPanel contentPanel;

    public BasePanel(String title) {
        setLayout(new BorderLayout());
        setBackground(Theme.APP_BG);
        setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_24, Theme.SPACE_32, Theme.SPACE_24, Theme.SPACE_32));

        // Header
        headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.X_AXIS));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.SPACE_24, 0));

        titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.FONT_TITLE);
        titleLabel.setForeground(Theme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(0f);

        subtitleLabel = new JLabel(" ");
        subtitleLabel.setFont(Theme.FONT_BODY);
        subtitleLabel.setForeground(Theme.TEXT_MUTED);
        subtitleLabel.setAlignmentX(0f);
        subtitleLabel.setVisible(false);

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(titleLabel);
        titleBlock.add(subtitleLabel);

        dateLabel = new JLabel(util.DbClock.today().format(DATE_FMT)) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(d.width + 28, 34);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.CARD_BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 34, 34);
                g2.setColor(Theme.CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 34, 34);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        dateLabel.setHorizontalAlignment(JLabel.CENTER);
        dateLabel.setFont(Theme.FONT_SMALL_BOLD);
        dateLabel.setForeground(Theme.TEXT_SECONDARY);

        headerPanel.add(titleBlock);
        headerPanel.add(Box.createHorizontalGlue());
        JPanel dateWrap = new JPanel(new BorderLayout());
        dateWrap.setOpaque(false);
        dateWrap.add(dateLabel, BorderLayout.NORTH);
        dateWrap.setMaximumSize(new Dimension(400, 40));
        headerPanel.add(dateWrap);

        add(headerPanel, BorderLayout.NORTH);

        // Content Area
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        add(contentPanel, BorderLayout.CENTER);

        // Trigger onShown when switching tabs/panels in CardLayout
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                dateLabel.setText(util.DbClock.today().format(DATE_FMT));
                onShown();
            }
        });
    }

    /** Muted one-line description shown under the page title. */
    public void setSubtitle(String text) {
        subtitleLabel.setText(text == null || text.isBlank() ? " " : text);
        subtitleLabel.setVisible(text != null && !text.isBlank());
    }

    public void addHeaderAction(Component actionComponent) {
        headerPanel.add(Box.createHorizontalStrut(Theme.SPACE_16));
        if (actionComponent instanceof JComponent jc) jc.setAlignmentY(0.5f);
        headerPanel.add(actionComponent);
    }

    /**
     * Hook invoked every time the screen becomes visible in the CardLayout.
     * Screens should override to reload dynamic data.
     */
    public void onShown() {
        // Default empty implementation
    }
}
