package ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Common base panel for all primary application views.
 * Provides a standardized header with page title, today's date, and container padding.
 */
public abstract class BasePanel extends JPanel {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy");

    protected final JPanel headerPanel;
    protected final JLabel titleLabel;
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

        dateLabel = new JLabel(LocalDate.now().format(DATE_FMT));
        dateLabel.setFont(Theme.FONT_BODY);
        dateLabel.setForeground(Theme.TEXT_MUTED);

        headerPanel.add(titleLabel);
        headerPanel.add(Box.createHorizontalGlue());
        headerPanel.add(dateLabel);

        add(headerPanel, BorderLayout.NORTH);

        // Content Area
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        add(contentPanel, BorderLayout.CENTER);

        // Trigger onShown when switching tabs/panels in CardLayout
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                onShown();
            }
        });
    }

    public void addHeaderAction(Component actionComponent) {
        headerPanel.add(Box.createHorizontalStrut(Theme.SPACE_16));
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
