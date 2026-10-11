package ui.auth;

import ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;

/**
 * High-end brand hero banner for authentication and onboarding panels.
 * Features luxury obsidian gradient, Java2D vector cloche crest, architectural
 * floor plan blueprint overlay, and enterprise capability cards.
 */
public class AuthHeroPanel extends JPanel {

    public AuthHeroPanel() {
        setPreferredSize(new Dimension(480, 800));
        setLayout(new BorderLayout());
        setOpaque(false);

        initContent();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // 1. Deep luxury obsidian vertical gradient
        LinearGradientPaint bgGrad = new LinearGradientPaint(
                0, 0, 0, h,
                new float[]{0.0f, 0.45f, 1.0f},
                new Color[]{
                        new Color(10, 12, 18),
                        new Color(18, 22, 32),
                        new Color(13, 16, 24)
                }
        );
        g2.setPaint(bgGrad);
        g2.fillRect(0, 0, w, h);

        // 2. Architectural CAD dining room blueprint overlay
        AuthIcons.paintFloorplanBlueprint(g2, w, h);

        // 3. Right edge separator with subtle gold vertical sheen
        LinearGradientPaint borderGrad = new LinearGradientPaint(
                w - 1, 0, w - 1, h,
                new float[]{0.0f, 0.3f, 0.7f, 1.0f},
                new Color[]{
                        new Color(255, 255, 255, 10),
                        new Color(200, 150, 62, 80),
                        new Color(200, 150, 62, 40),
                        new Color(255, 255, 255, 5)
                }
        );
        g2.setPaint(borderGrad);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawLine(w - 1, 0, w - 1, h);

        g2.dispose();
    }

    private void initContent() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(46, 44, 36, 44));

        // Crest Component (Custom painted)
        JPanel crestPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                AuthIcons.paintBrandCrest(g2, 42, 42, 68);
                g2.dispose();
            }
        };
        crestPanel.setOpaque(false);
        crestPanel.setPreferredSize(new Dimension(84, 84));
        crestPanel.setMaximumSize(new Dimension(84, 84));
        crestPanel.setAlignmentX(0.0f);

        // Brand Title
        JLabel brandName = new JLabel("RESERVE");
        brandName.setFont(new Font(Theme.FONT_HERO.getFamily(), Font.BOLD, 32));
        brandName.setForeground(new Color(245, 240, 230));
        brandName.setAlignmentX(0.0f);

        JLabel brandCategory = new JLabel("HOSPITALITY OPERATING SYSTEM");
        brandCategory.setFont(new Font("SansSerif", Font.BOLD, 10));
        brandCategory.setForeground(Theme.ACCENT_GOLD);
        brandCategory.setAlignmentX(0.0f);

        JLabel brandDesc = new JLabel("<html><div style='width:340px; line-height:1.55; color:#94A3B8; font-size:12px;'>" +
                "Engineered for high-velocity dining rooms, Michelin-standard service pacing, and zero-conflict table allocation." +
                "</div></html>");
        brandDesc.setAlignmentX(0.0f);

        // Feature cards container
        JPanel featureList = new JPanel();
        featureList.setLayout(new BoxLayout(featureList, BoxLayout.Y_AXIS));
        featureList.setOpaque(false);
        featureList.setAlignmentX(0.0f);

        featureList.add(createFeatureCard("⚡", "Zero-Conflict Table Allocation",
                "Atomic table locks with optimistic concurrency & instant conflict guards."));
        featureList.add(Box.createVerticalStrut(Theme.SPACE_12));
        featureList.add(createFeatureCard("🛡️", "NIST SP 800-63B Authentication",
                "Hardware-grade PBKDF2-HMAC-SHA256 with append-only audit verification."));
        featureList.add(Box.createVerticalStrut(Theme.SPACE_12));
        featureList.add(createFeatureCard("⏱️", "Waitlist Recovery Engine",
                "Real-time turn tracking & automated 60-second reclamation cycles."));

        // Live System Status Badge
        JPanel statusPill = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(16, 185, 129, 20));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(new Color(16, 185, 129, 60));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                // Glowing green dot
                g2.setColor(new Color(52, 211, 153));
                g2.fillOval(14, (getHeight() - 8) / 2, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        statusPill.setOpaque(false);
        statusPill.setLayout(new BorderLayout());
        statusPill.setPreferredSize(new Dimension(380, 32));
        statusPill.setMaximumSize(new Dimension(380, 32));
        statusPill.setBorder(BorderFactory.createEmptyBorder(0, 32, 0, 12));
        statusPill.setAlignmentX(0.0f);

        JLabel statusText = new JLabel("Engine Online • Oracle 21c XE • v2.0 Enterprise");
        statusText.setFont(new Font("SansSerif", Font.BOLD, 11));
        statusText.setForeground(new Color(110, 231, 183));
        statusPill.add(statusText, BorderLayout.CENTER);

        JLabel footerNote = new JLabel("CONFIDENTIAL & PROPRIETARY • AUTHORIZED STAFF TERMINAL ONLY");
        footerNote.setFont(new Font("SansSerif", Font.PLAIN, 9));
        footerNote.setForeground(new Color(100, 116, 139));
        footerNote.setAlignmentX(0.0f);

        // Assembly
        content.add(crestPanel);
        content.add(Box.createVerticalStrut(Theme.SPACE_8));
        content.add(brandName);
        content.add(Box.createVerticalStrut(Theme.SPACE_4));
        content.add(brandCategory);
        content.add(Box.createVerticalStrut(Theme.SPACE_12));
        content.add(brandDesc);
        content.add(Box.createVerticalStrut(Theme.SPACE_24));
        content.add(featureList);
        content.add(Box.createVerticalGlue());
        content.add(statusPill);
        content.add(Box.createVerticalStrut(Theme.SPACE_8));
        content.add(footerNote);

        add(content, BorderLayout.CENTER);
    }

    private JPanel createFeatureCard(String glyph, String title, String description) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Frosted translucent card
                g2.setColor(new Color(255, 255, 255, 8));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(new Color(255, 255, 255, 16));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setLayout(new BorderLayout(12, 0));
        card.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        card.setPreferredSize(new Dimension(380, 74));
        card.setMaximumSize(new Dimension(380, 74));
        card.setAlignmentX(0.0f);

        JLabel iconLabel = new JLabel(glyph);
        iconLabel.setFont(new Font("SansSerif", Font.PLAIN, 18));
        iconLabel.setForeground(Theme.ACCENT_GOLD);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLabel.setForeground(Color.WHITE);

        JLabel descLabel = new JLabel("<html><body style='width:250px; color:#94A3B8; font-size:10px'>"
                + description + "</body></html>");

        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(descLabel);

        card.add(iconLabel, BorderLayout.WEST);
        card.add(textPanel, BorderLayout.CENTER);

        return card;
    }
}
