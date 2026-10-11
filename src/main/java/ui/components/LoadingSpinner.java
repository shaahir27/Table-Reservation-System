package ui.components;

import ui.Theme;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;

/** Gold arc spinner. Only ticks while it is actually showing on screen. */
public class LoadingSpinner extends JComponent {
    private final int size;
    private double angle;
    private double sweep = 90;
    private final Timer timer = new Timer(16, e -> {
        angle = (angle + 7) % 360;
        sweep = 70 + 50 * (0.5 + 0.5 * Math.sin(Math.toRadians(angle * 2)));
        repaint();
    });

    public LoadingSpinner(int size) {
        this.size = size;
        setOpaque(false);
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float stroke = Math.max(2f, size / 9f);
        int pad = (int) Math.ceil(stroke);
        int d = Math.min(getWidth(), getHeight()) - pad * 2;
        int x = (getWidth() - d) / 2;
        int y = (getHeight() - d) / 2;
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Theme.alpha(Theme.ACCENT_GOLD, 40));
        g2.drawOval(x, y, d, d);
        g2.setColor(Theme.ACCENT_GOLD);
        g2.draw(new Arc2D.Double(x, y, d, d, -angle, sweep, Arc2D.OPEN));
        g2.dispose();
    }
}
