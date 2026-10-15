package ui;

import model.AppSetting;
import security.IdleLockMonitor;
import service.SettingsService;
import ui.components.ChipRow;
import ui.components.AppButton;
import ui.components.Card;
import ui.components.Form;
import ui.components.Toast;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager-only runtime settings (SettingsService / app_setting table).
 * Every value is read live by the backend, so a saved change applies immediately.
 */
public class SettingsPanel extends BasePanel {
    private record Def(String key, String title, String hint, String unit, int min, int max, String group) {
    }

    private static final String G_SESSION = "Sessions & security";
    private static final String G_FLOOR = "Service floor";

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    static {
        add(new Def("idle_lock_minutes", "Auto-lock screen", "Lock the screen after this much inactivity. 0 disables auto-lock.", "minutes", 0, 240, G_SESSION));
        add(new Def("lockout_threshold", "Failed sign-ins before lockout", "Number of wrong passwords before an account is temporarily locked.", "attempts", 1, 20, G_SESSION));
        add(new Def("lockout_minutes", "Lockout duration", "How long a locked account must wait before trying again.", "minutes", 1, 1440, G_SESSION));
        add(new Def("grace_minutes", "Overdue grace period", "A confirmed booking is flagged OVERDUE this long after its slot starts.", "minutes", 0, 120, G_FLOOR));
        add(new Def("hold_minutes", "Waitlist offer hold", "How long an offered table is held for a waitlisted party before it expires.", "minutes", 1, 120, G_FLOOR));
        add(new Def("max_waste_seats", "Max spare seats for auto-offers", "Never auto-offer a table with more than this many empty seats.", "seats", 0, 20, G_FLOOR));
    }

    private static void add(Def d) {
        DEFS.put(d.key(), d);
    }

    private final SettingsService settingsService;
    private final Map<String, JComponent> editors = new LinkedHashMap<>();
    private final Map<String, String> original = new LinkedHashMap<>();
    private final JPanel body = new JPanel();
    private final JLabel dirtyLabel = new JLabel(" ");
    private final AppButton saveBtn = AppButton.primary("Save Changes");
    private final AppButton discardBtn = AppButton.secondary("Discard");
    private boolean loading;

    public SettingsPanel(SettingsService settingsService) {
        super("Settings");
        this.settingsService = settingsService;
        setSubtitle("Operational rules that apply across the whole restaurant. Changes take effect immediately.");

        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_16, 0, 0, 0));
        dirtyLabel.setFont(Theme.FONT_BODY_BOLD);
        dirtyLabel.setForeground(Theme.WARNING);
        JPanel buttons = new ChipRow(Theme.SPACE_12, true);
        buttons.setOpaque(false);
        buttons.add(discardBtn);
        buttons.add(saveBtn);
        footer.add(dirtyLabel, BorderLayout.WEST);
        footer.add(buttons, BorderLayout.EAST);

        saveBtn.setEnabled(false);
        discardBtn.setEnabled(false);
        saveBtn.addActionListener(e -> save());
        discardBtn.addActionListener(e -> revert());

        contentPanel.add(scroll, BorderLayout.CENTER);
        contentPanel.add(footer, BorderLayout.SOUTH);
    }

    @Override
    public void onShown() {
        load();
    }

    private void load() {
        Ui.async(
                settingsService::getAll,
                this::build,
                err -> Toast.error(this, "Could not load settings: " + err.getMessage())
        );
    }

    private void build(List<AppSetting> settings) {
        loading = true;
        body.removeAll();
        editors.clear();
        original.clear();

        Map<String, List<AppSetting>> groups = new LinkedHashMap<>();
        groups.put(G_SESSION, new ArrayList<>());
        groups.put(G_FLOOR, new ArrayList<>());
        for (AppSetting s : settings) {
            Def d = DEFS.get(s.getKey());
            if (d != null) {
                groups.get(d.group()).add(s);
            }
        }
        // keep a stable, meaningful order for known keys
        for (List<AppSetting> list : groups.values()) {
            list.sort((a, b) -> {
                int ia = indexOf(a.getKey()), ib = indexOf(b.getKey());
                return ia != ib ? Integer.compare(ia, ib) : a.getKey().compareTo(b.getKey());
            });
        }

        for (Map.Entry<String, List<AppSetting>> g : groups.entrySet()) {
            if (g.getValue().isEmpty()) continue;
            body.add(createGroupCard(g.getKey(), g.getValue()));
            body.add(Box.createVerticalStrut(Theme.SPACE_16));
        }
        if (settings.isEmpty()) {
            JLabel empty = new JLabel("No settings are configured in the database.");
            empty.setFont(Theme.FONT_BODY);
            empty.setForeground(Theme.TEXT_MUTED);
            body.add(empty);
        }
        body.revalidate();
        body.repaint();
        loading = false;
        refreshDirty();
    }

    private static int indexOf(String key) {
        int i = 0;
        for (String k : DEFS.keySet()) {
            if (k.equals(key)) return i;
            i++;
        }
        return 1000;
    }

    private JPanel createGroupCard(String title, List<AppSetting> items) {
        Card card = new Card(Theme.SPACE_20, Theme.SPACE_24);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(0f);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel t = new JLabel(title);
        t.setFont(Theme.FONT_SECTION);
        t.setForeground(Theme.TEXT_PRIMARY);
        t.setAlignmentX(0f);
        card.add(t);
        card.add(Box.createVerticalStrut(Theme.SPACE_8));

        for (int i = 0; i < items.size(); i++) {
            card.add(createRow(items.get(i), i > 0));
        }
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private JPanel createRow(AppSetting s, boolean topRule) {
        Def d = DEFS.get(s.getKey());
        JPanel row = new JPanel(new BorderLayout(Theme.SPACE_24, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (topRule) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(Theme.NEUTRAL_TINT);
                    g2.drawLine(0, 0, getWidth(), 0);
                    g2.dispose();
                }
            }
        };
        row.setOpaque(false);
        row.setAlignmentX(0f);
        row.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_16, 0, Theme.SPACE_16, 0));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(d != null ? d.title() : prettify(s.getKey()));
        name.setFont(Theme.FONT_BODY_BOLD);
        name.setForeground(Theme.TEXT_PRIMARY);
        String hintText = d != null ? d.hint() : (s.getDescription() != null ? s.getDescription() : "");
        JLabel hint = new JLabel("<html><body style='width:460px'>" + hintText + "</body></html>");
        hint.setFont(Theme.FONT_SMALL);
        hint.setForeground(Theme.TEXT_MUTED);
        text.add(name);
        text.add(Box.createVerticalStrut(3));
        text.add(hint);
        row.add(text, BorderLayout.CENTER);

        JPanel editorWrap = new ChipRow(Theme.SPACE_8, true);
        editorWrap.setOpaque(false);
        JComponent editor;
        String value = s.getValue() == null ? "" : s.getValue();
        if (d != null) {
            int v = s.getIntValue(d.min());
            v = Math.max(d.min(), Math.min(d.max(), v));
            JSpinner sp = new JSpinner(new SpinnerNumberModel(v, d.min(), d.max(), 1));
            Form.input(sp, 110);
            sp.addChangeListener(e -> refreshDirty());
            editor = sp;
            original.put(s.getKey(), String.valueOf(v));
            editorWrap.add(sp);
            JLabel unit = new JLabel(d.unit());
            unit.setFont(Theme.FONT_BODY);
            unit.setForeground(Theme.TEXT_MUTED);
            unit.setPreferredSize(new Dimension(64, 20));
            editorWrap.add(unit);
        } else {
            JTextField tf = new JTextField(value);
            Form.input(tf, 190);
            tf.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshDirty(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshDirty(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshDirty(); }
            });
            editor = tf;
            original.put(s.getKey(), value);
            editorWrap.add(tf);
        }
        editors.put(s.getKey(), editor);
        row.add(editorWrap, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + 6));
        return row;
    }

    private static String prettify(String key) {
        String s = key.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String currentValue(JComponent c) {
        if (c instanceof JSpinner sp) return String.valueOf(sp.getValue());
        if (c instanceof JTextField tf) return tf.getText().trim();
        return "";
    }

    private Map<String, String> changed() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, JComponent> e : editors.entrySet()) {
            String cur = currentValue(e.getValue());
            if (!cur.equals(original.get(e.getKey()))) out.put(e.getKey(), cur);
        }
        return out;
    }

    private void refreshDirty() {
        if (loading) return;
        int n = changed().size();
        saveBtn.setEnabled(n > 0);
        discardBtn.setEnabled(n > 0);
        dirtyLabel.setText(n == 0 ? " " : "●  " + n + (n == 1 ? " unsaved change" : " unsaved changes"));
    }

    private void revert() {
        loading = true;
        for (Map.Entry<String, JComponent> e : editors.entrySet()) {
            String orig = original.get(e.getKey());
            if (e.getValue() instanceof JSpinner sp) {
                try {
                    sp.setValue(Integer.parseInt(orig));
                } catch (NumberFormatException ignored) {
                }
            } else if (e.getValue() instanceof JTextField tf) {
                tf.setText(orig);
            }
        }
        loading = false;
        refreshDirty();
    }

    private void save() {
        Map<String, String> toSave = changed();
        if (toSave.isEmpty()) return;
        saveBtn.setLoading(true);
        discardBtn.setEnabled(false);
        Ui.async(
                () -> {
                    for (Map.Entry<String, String> e : toSave.entrySet()) {
                        settingsService.update(e.getKey(), e.getValue());
                    }
                    return toSave;
                },
                saved -> {
                    saveBtn.setLoading(false);
                    String idle = saved.get("idle_lock_minutes");
                    if (idle != null) {
                        try {
                            IdleLockMonitor.getInstance().setIdleMinutes(Integer.parseInt(idle));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    Toast.success(SwingUtilities.getWindowAncestor(this),
                            saved.size() == 1 ? "Setting saved" : saved.size() + " settings saved");
                    load();
                },
                err -> {
                    saveBtn.setLoading(false);
                    refreshDirty();
                    Toast.error(SwingUtilities.getWindowAncestor(this), err.getMessage());
                }
        );
    }
}
