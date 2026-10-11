package ui.components;

import ui.Theme;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Dimension;

/** Layout helpers that give every form identical label / input / error rhythm and comfortable field sizes. */
public final class Form {
    private Form() {
    }

    /** Small bold caption above an input. */
    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_SMALL_BOLD);
        l.setForeground(Theme.TEXT_SECONDARY);
        l.setAlignmentX(0f);
        return l;
    }

    /** Inline validation message. Holds a blank line so the layout never jumps. */
    public static JLabel error() {
        JLabel l = new JLabel(" ");
        l.setFont(Theme.FONT_SMALL);
        l.setForeground(Theme.DANGER);
        l.setAlignmentX(0f);
        return l;
    }

    /** Gives an input the standard 42px height and lets it stretch to the available width. */
    public static <T extends JComponent> T input(T c) {
        c.setAlignmentX(0f);
        if (c instanceof javax.swing.JSpinner sp && sp.getEditor() instanceof javax.swing.JSpinner.DefaultEditor ed) {
            ed.getTextField().setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        }
        Dimension pref = c.getPreferredSize();
        c.setPreferredSize(new Dimension(Math.max(pref.width, 160), Theme.CONTROL_HEIGHT));
        c.setMinimumSize(new Dimension(80, Theme.CONTROL_HEIGHT));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.CONTROL_HEIGHT));
        return c;
    }

    /** Fixed-width variant for inputs inside flow rows (spinners, date fields...). */
    public static <T extends JComponent> T input(T c, int width) {
        if (c instanceof javax.swing.JSpinner sp && sp.getEditor() instanceof javax.swing.JSpinner.DefaultEditor ed) {
            ed.getTextField().setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        }
        c.setPreferredSize(new Dimension(width, Theme.CONTROL_HEIGHT));
        c.setMinimumSize(new Dimension(width, Theme.CONTROL_HEIGHT));
        c.setMaximumSize(new Dimension(width, Theme.CONTROL_HEIGHT));
        return c;
    }

    /** Label + input (+ optional error line) stacked with consistent spacing. */
    public static JPanel field(String caption, JComponent input, JLabel error) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(0f);
        p.add(label(caption));
        p.add(Box.createVerticalStrut(6));
        p.add(input(input));
        if (error != null) {
            p.add(Box.createVerticalStrut(4));
            p.add(error);
        } else {
            p.add(Box.createVerticalStrut(Theme.SPACE_12));
        }
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height + 4));
        return p;
    }
}
