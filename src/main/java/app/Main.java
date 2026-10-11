package app;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import ui.MainFrame;
import ui.Theme;
import util.AppLog;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.InsetsUIResource;
import java.awt.Color;
import java.awt.Font;
import java.util.Map;

/**
 * Application entry point. Bootstraps FlatLaf with Inter font and launches the MainFrame.
 */
public class Main {
    public static void main(String[] args) {
        // Prevent JVM restricted-method warning on modern Java when native access is not passed
        if (System.getProperty("flatlaf.useNativeLibrary") == null) {
            boolean hasNativeAccess = false;
            try {
                hasNativeAccess = java.lang.management.ManagementFactory.getRuntimeMXBean()
                        .getInputArguments()
                        .stream()
                        .anyMatch(arg -> arg.contains("enable-native-access"));
            } catch (Throwable ignored) {
            }
            if (!hasNativeAccess) {
                System.setProperty("flatlaf.useNativeLibrary", "false");
            }
        }

        AppLog.info("Starting Restaurant Table Reservation System...");

        installLookAndFeel();

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            frame.checkDatabaseConnection();
        });
    }

    /** Installs FlatLaf + Inter + the RTRS design tokens. Safe to call once before any Swing component exists. */
    public static void installLookAndFeel() {
        try {
            FlatInterFont.install();
            UIManager.put("defaultFont", new Font(FlatInterFont.FAMILY, Font.PLAIN, 14));
            // Palette-driven FlatLaf: gold accent everywhere (focus rings, selections, checkboxes, tabs)
            FlatLaf.setGlobalExtraDefaults(Map.of(
                    "@accentColor", "#C8963E",
                    "@componentMargin", "4,12,4,12",
                    "@background", "#F6F4F0"
            ));
            FlatLightLaf.setup();

            Color gold = Theme.ACCENT_GOLD;
            UIManager.put("Component.arc", 12);
            UIManager.put("Button.arc", 12);
            UIManager.put("TextComponent.arc", 10);
            UIManager.put("Component.focusWidth", 1);
            UIManager.put("Component.innerFocusWidth", 0);
            UIManager.put("Component.focusColor", new ColorUIResource(Theme.alpha(gold, 160)));
            UIManager.put("Component.borderColor", new ColorUIResource(Theme.FIELD_BORDER));
            UIManager.put("Component.hoverBorderColor", new ColorUIResource(Theme.mix(Theme.FIELD_BORDER, gold, 0.6)));
            UIManager.put("Component.disabledBorderColor", new ColorUIResource(Theme.CARD_BORDER));
            UIManager.put("TextField.margin", new InsetsUIResource(4, 12, 4, 12));
            UIManager.put("PasswordField.margin", new InsetsUIResource(4, 12, 4, 12));
            UIManager.put("FormattedTextField.margin", new InsetsUIResource(4, 12, 4, 12));
            UIManager.put("TextArea.margin", new InsetsUIResource(8, 12, 8, 12));
            UIManager.put("ComboBox.padding", new InsetsUIResource(4, 12, 4, 8));
            UIManager.put("Spinner.padding", new InsetsUIResource(4, 12, 4, 4));
            UIManager.put("ComboBox.selectionBackground", new ColorUIResource(Theme.GOLD_TINT_STRONG));
            UIManager.put("ComboBox.selectionForeground", new ColorUIResource(Theme.TEXT_PRIMARY));
            UIManager.put("List.selectionBackground", new ColorUIResource(Theme.GOLD_TINT_STRONG));
            UIManager.put("List.selectionForeground", new ColorUIResource(Theme.TEXT_PRIMARY));
            UIManager.put("MenuItem.selectionBackground", new ColorUIResource(Theme.GOLD_TINT_STRONG));
            UIManager.put("MenuItem.selectionForeground", new ColorUIResource(Theme.TEXT_PRIMARY));
            UIManager.put("PopupMenu.borderColor", new ColorUIResource(Theme.CARD_BORDER));
            UIManager.put("PopupMenu.borderInsets", new InsetsUIResource(6, 6, 6, 6));
            UIManager.put("MenuItem.margin", new InsetsUIResource(8, 14, 8, 14));
            UIManager.put("CheckBox.icon.focusedBorderColor", new ColorUIResource(gold));
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.thumbInsets", new InsetsUIResource(2, 2, 2, 2));
            UIManager.put("ScrollBar.width", 12);
            UIManager.put("ScrollBar.thumb", new ColorUIResource(Theme.mix(Theme.CARD_BORDER, Theme.MAINTENANCE_GREY, 0.5)));
            UIManager.put("ScrollBar.hoverThumbColor", new ColorUIResource(Theme.MAINTENANCE_GREY));
            UIManager.put("ScrollBar.pressedThumbColor", new ColorUIResource(Theme.TEXT_MUTED));
            UIManager.put("ScrollBar.track", new ColorUIResource(new Color(0, 0, 0, 0)));
            UIManager.put("ScrollPane.border", javax.swing.BorderFactory.createEmptyBorder());
            UIManager.put("TabbedPane.tabHeight", 40);
            UIManager.put("TabbedPane.tabInsets", new InsetsUIResource(8, 18, 8, 18));
            UIManager.put("TabbedPane.underlineColor", new ColorUIResource(gold));
            UIManager.put("TabbedPane.selectedForeground", new ColorUIResource(Theme.TEXT_PRIMARY));
            UIManager.put("TabbedPane.hoverColor", new ColorUIResource(Theme.GOLD_TINT));
            UIManager.put("TabbedPane.focusColor", new ColorUIResource(Theme.GOLD_TINT));
            UIManager.put("TabbedPane.tabSeparatorsFullHeight", false);
            UIManager.put("TabbedPane.showTabSeparators", false);
            UIManager.put("TabbedPane.contentAreaColor", new ColorUIResource(Theme.CARD_BORDER));
            UIManager.put("ToolTip.background", new ColorUIResource(Theme.SIDEBAR_BG));
            UIManager.put("ToolTip.foreground", new ColorUIResource(Color.WHITE));
            UIManager.put("ToolTip.border", javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10));
            UIManager.put("ProgressBar.foreground", new ColorUIResource(gold));
            UIManager.put("Table.rowHeight", 46);
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", false);
            UIManager.put("TableHeader.height", 44);
        } catch (Exception e) {
            AppLog.error("Error configuring FlatLaf look and feel: " + e.getMessage(), e);
        }

    }
}
