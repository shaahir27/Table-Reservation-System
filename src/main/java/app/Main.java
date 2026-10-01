package app;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import ui.MainFrame;
import util.AppLog;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Font;

/**
 * Application entry point. Bootstraps FlatLaf with Inter font and launches the MainFrame.
 */
public class Main {
    public static void main(String[] args) {
        AppLog.info("Starting Restaurant Table Reservation System...");

        // Setup FlatLaf with Inter font and rounded aesthetics before any Swing components are instantiated
        try {
            FlatInterFont.install();
            UIManager.put("defaultFont", new Font(FlatInterFont.FAMILY, Font.PLAIN, 14));
            FlatLightLaf.setup();

            UIManager.put("Component.arc", 12);
            UIManager.put("Button.arc", 12);
            UIManager.put("TextComponent.arc", 10);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("Table.rowHeight", 38);
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", false);
            UIManager.put("TableHeader.height", 40);
        } catch (Exception e) {
            AppLog.error("Error configuring FlatLaf look and feel: " + e.getMessage(), e);
        }

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            frame.checkDatabaseConnection();
        });
    }
}
