package ui.components;

import ui.Theme;

import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.Component;

/** CardLayout host that instantly and cleanly switches screens without heavyweight alpha composite overhead. */
public class ScreenHost extends JPanel {
    private final CardLayout layout = new CardLayout();
    private String current;

    public ScreenHost() {
        setLayout(layout);
        setBackground(Theme.APP_BG);
    }

    /** Kept for API compatibility. */
    public ScreenHost slide(int px) {
        return this;
    }

    public void addScreen(Component c, String key) {
        add(c, key);
    }

    public String getCurrent() {
        return current;
    }

    public void showScreen(String key) {
        if (key == null || key.equals(current)) return;
        current = key;
        layout.show(this, key);
        revalidate();
        repaint();
    }
}
