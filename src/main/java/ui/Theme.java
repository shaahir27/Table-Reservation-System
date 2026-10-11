package ui;

import com.formdev.flatlaf.fonts.inter.FlatInterFont;

import java.awt.Color;
import java.awt.Font;

/**
 * Single source of truth for design tokens, colors, typography, and spacing.
 */
public class Theme {
    // Colors
    public static final Color SIDEBAR_BG = Color.decode("#17181C");
    public static final Color SIDEBAR_TEXT = Color.decode("#B9BCC4");
    public static final Color SIDEBAR_ACTIVE_TEXT = Color.decode("#FFFFFF");
    public static final Color ACCENT_GOLD = Color.decode("#C8963E");
    public static final Color ACCENT_GOLD_HOVER = Color.decode("#B58534");
    public static final Color APP_BG = Color.decode("#F6F4F0");
    public static final Color CARD_BG = Color.decode("#FFFFFF");
    public static final Color CARD_BORDER = Color.decode("#E7E3DC");
    public static final Color TEXT_PRIMARY = Color.decode("#1E2024");
    public static final Color TEXT_SECONDARY = Color.decode("#4B5563");
    public static final Color TEXT_MUTED = Color.decode("#6B7078");
    public static final Color SUCCESS = Color.decode("#2E7D5B");
    public static final Color DANGER = Color.decode("#C0392B");
    public static final Color WARNING = Color.decode("#B7791F");
    public static final Color MAINTENANCE_GREY = Color.decode("#9AA0A6");
    public static final Color TABLE_HEADER_BG = Color.decode("#EFECE6");
    public static final Color HOVER_ROW_BG = Color.decode("#F8F7F4");

    // Derived palette tokens (all computed from the core palette above)
    public static final Color SIDEBAR_RAISED = Color.decode("#22242A");
    public static final Color SIDEBAR_BORDER = Color.decode("#2C2E35");
    public static final Color SIDEBAR_MUTED = Color.decode("#7E828C");
    public static final Color GOLD_TINT = Color.decode("#F7EFDF");
    public static final Color GOLD_TINT_STRONG = Color.decode("#F1E3C6");
    public static final Color SUCCESS_TINT = Color.decode("#E6F1EC");
    public static final Color DANGER_TINT = Color.decode("#F8E7E4");
    public static final Color WARNING_TINT = Color.decode("#F6EBD7");
    public static final Color NEUTRAL_TINT = Color.decode("#EFECE6");
    public static final Color DISABLED_FG = Color.decode("#A8ACB2");
    public static final Color FIELD_BORDER = Color.decode("#D9D4CA");

    // Shape
    public static final int RADIUS_CARD = 16;
    public static final int RADIUS_CONTROL = 10;
    public static final int CONTROL_HEIGHT = 42;
    public static final int CONTROL_HEIGHT_COMPACT = 30;

    // Spacing
    public static final int SPACE_4 = 4;
    public static final int SPACE_8 = 8;
    public static final int SPACE_12 = 12;
    public static final int SPACE_16 = 16;
    public static final int SPACE_20 = 20;
    public static final int SPACE_24 = 24;
    public static final int SPACE_32 = 32;

    // Typography
    public static final Font FONT_HERO = new Font(FlatInterFont.FAMILY, Font.BOLD, 36);
    public static final Font FONT_TITLE = new Font(FlatInterFont.FAMILY, Font.BOLD, 26);
    public static final Font FONT_SECTION = new Font(FlatInterFont.FAMILY, Font.BOLD, 16);
    public static final Font FONT_BODY = new Font(FlatInterFont.FAMILY, Font.PLAIN, 14);
    public static final Font FONT_BODY_BOLD = new Font(FlatInterFont.FAMILY, Font.BOLD, 14);
    public static final Font FONT_SMALL = new Font(FlatInterFont.FAMILY, Font.PLAIN, 12);
    public static final Font FONT_SMALL_BOLD = new Font(FlatInterFont.FAMILY, Font.BOLD, 12);
    public static final Font FONT_STAT_NUM = new Font(FlatInterFont.FAMILY, Font.BOLD, 32);

    /** Returns the colour with the given alpha (0-255). */
    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    /** Linear blend: t=0 returns a, t=1 returns b. */
    public static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int al = (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return new Color(r, g, bl, al);
    }

    private Theme() {
    }
}
