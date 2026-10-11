package ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** Crisp vector line icons (no image assets). All icons are drawn inside a size x size box. */
public final class Icons {
    private Icons() {
    }

    public static void paint(Graphics2D g, String key, int x, int y, int size, Color color) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.translate(x, y);
        double s = size / 24.0;
        g2.scale(s, s);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (key) {
            case "DASHBOARD" -> {
                g2.draw(new RoundRectangle2D.Double(3.5, 3.5, 7, 7, 2.5, 2.5));
                g2.draw(new RoundRectangle2D.Double(13.5, 3.5, 7, 7, 2.5, 2.5));
                g2.draw(new RoundRectangle2D.Double(3.5, 13.5, 7, 7, 2.5, 2.5));
                g2.draw(new RoundRectangle2D.Double(13.5, 13.5, 7, 7, 2.5, 2.5));
            }
            case "SERVICE_BOARD" -> {
                g2.draw(new RoundRectangle2D.Double(3.5, 4, 5, 16, 2, 2));
                g2.draw(new RoundRectangle2D.Double(10, 4, 5, 10, 2, 2));
                g2.draw(new RoundRectangle2D.Double(16.5, 4, 4, 13, 2, 2));
            }
            case "RESERVATIONS", "calendar" -> {
                g2.draw(new RoundRectangle2D.Double(3.5, 5, 17, 15.5, 3.5, 3.5));
                g2.draw(new Line2D.Double(3.5, 10, 20.5, 10));
                g2.draw(new Line2D.Double(8, 2.8, 8, 6.5));
                g2.draw(new Line2D.Double(16, 2.8, 16, 6.5));
                g2.fill(new Ellipse2D.Double(7.2, 13.2, 2.2, 2.2));
                g2.fill(new Ellipse2D.Double(11, 13.2, 2.2, 2.2));
            }
            case "CUSTOMERS", "user" -> {
                g2.draw(new Ellipse2D.Double(8, 3.5, 8, 8));
                Path2D p = new Path2D.Double();
                p.moveTo(4, 20.5);
                p.curveTo(4, 15.5, 8, 14, 12, 14);
                p.curveTo(16, 14, 20, 15.5, 20, 20.5);
                g2.draw(p);
            }
            case "TABLES", "tables" -> {
                g2.draw(new Ellipse2D.Double(6, 6, 12, 12));
                g2.fill(new Ellipse2D.Double(10.8, 1.6, 2.4, 2.4));
                g2.fill(new Ellipse2D.Double(10.8, 20, 2.4, 2.4));
                g2.fill(new Ellipse2D.Double(1.6, 10.8, 2.4, 2.4));
                g2.fill(new Ellipse2D.Double(20, 10.8, 2.4, 2.4));
            }
            case "REPORTS", "chart" -> {
                g2.draw(new Line2D.Double(3.5, 20.5, 20.5, 20.5));
                g2.draw(new RoundRectangle2D.Double(5.5, 12, 3.5, 6, 1.2, 1.2));
                g2.draw(new RoundRectangle2D.Double(10.3, 7, 3.5, 11, 1.2, 1.2));
                g2.draw(new RoundRectangle2D.Double(15.1, 3.5, 3.5, 14.5, 1.2, 1.2));
            }
            case "STAFF" -> {
                g2.draw(new RoundRectangle2D.Double(3, 4.5, 18, 15, 3.5, 3.5));
                g2.draw(new Ellipse2D.Double(6.5, 8.5, 4.4, 4.4));
                g2.draw(new Line2D.Double(5.8, 16.4, 11.6, 16.4));
                g2.draw(new Line2D.Double(14, 9.5, 18, 9.5));
                g2.draw(new Line2D.Double(14, 13, 18, 13));
            }
            case "AUDIT" -> {
                g2.draw(new RoundRectangle2D.Double(4.5, 3, 15, 18, 3, 3));
                g2.draw(new Line2D.Double(8.5, 8.5, 15.5, 8.5));
                g2.draw(new Line2D.Double(8.5, 12, 15.5, 12));
                g2.draw(new Line2D.Double(8.5, 15.5, 12.5, 15.5));
            }
            case "SETTINGS" -> {
                g2.draw(new Line2D.Double(4, 7, 20, 7));
                g2.draw(new Line2D.Double(4, 12, 20, 12));
                g2.draw(new Line2D.Double(4, 17, 20, 17));
                g2.setColor(Theme.SIDEBAR_BG.equals(color) ? color : color);
                g2.fill(new Ellipse2D.Double(14, 4.6, 4.8, 4.8));
                g2.fill(new Ellipse2D.Double(6, 9.6, 4.8, 4.8));
                g2.fill(new Ellipse2D.Double(12, 14.6, 4.8, 4.8));
            }
            case "clock" -> {
                g2.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17));
                Path2D p = new Path2D.Double();
                p.moveTo(12, 7.5);
                p.lineTo(12, 12.3);
                p.lineTo(15.3, 14.2);
                g2.draw(p);
            }
            case "recover" -> {
                g2.draw(new Arc2D.Double(4, 4, 16, 16, 30, 280, Arc2D.OPEN));
                Path2D p = new Path2D.Double();
                p.moveTo(17.5, 1.8);
                p.lineTo(18.6, 6.4);
                p.lineTo(14, 7.4);
                g2.draw(p);
            }
            case "cancel" -> {
                g2.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17));
                g2.draw(new Line2D.Double(8.8, 8.8, 15.2, 15.2));
                g2.draw(new Line2D.Double(15.2, 8.8, 8.8, 15.2));
            }
            case "lock" -> {
                g2.draw(new RoundRectangle2D.Double(5, 10.5, 14, 10, 3, 3));
                Path2D p = new Path2D.Double();
                p.moveTo(8, 10.5);
                p.lineTo(8, 8);
                p.curveTo(8, 2.5, 16, 2.5, 16, 8);
                p.lineTo(16, 10.5);
                g2.draw(p);
            }
            case "plus" -> {
                g2.draw(new Line2D.Double(12, 5, 12, 19));
                g2.draw(new Line2D.Double(5, 12, 19, 12));
            }
            case "chevron" -> {
                Path2D p = new Path2D.Double();
                p.moveTo(8, 10);
                p.lineTo(12, 14);
                p.lineTo(16, 10);
                g2.draw(p);
            }
            default -> g2.draw(new Ellipse2D.Double(5, 5, 14, 14));
        }
        g2.dispose();
    }
}
