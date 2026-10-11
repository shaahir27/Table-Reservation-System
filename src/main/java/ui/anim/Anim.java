package ui.anim;

import javax.swing.Timer;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * Tiny Swing animation engine. One shared 60fps javax.swing.Timer drives every active tween on the
 * Event Dispatch Thread, so animations never need their own threads and never outlive the UI.
 * Set -Drtrs.noanim=true (or Anim.setEnabled(false)) to jump every animation to its end state.
 */
public final class Anim {
    public interface Easing {
        double apply(double t);
    }

    public static final Easing LINEAR = t -> t;
    public static final Easing EASE_OUT = t -> 1 - Math.pow(1 - t, 3);
    public static final Easing EASE_IN_OUT = t -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    public static final Easing EASE_OUT_BACK = t -> {
        double c1 = 1.4, c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    };

    private static boolean enabled = !Boolean.getBoolean("rtrs.noanim");
    private static final List<Tween> ACTIVE = new ArrayList<>();
    private static final Timer TIMER = new Timer(16, e -> tick());

    static {
        TIMER.setCoalesce(true);
    }

    private Anim() {
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** A running animation. Cancel to stop it without firing its completion callback. */
    public static final class Tween {
        private final long startNanos;
        private final long durationNanos;
        private final Easing easing;
        private final DoubleConsumer onFrame;
        private final Runnable onDone;
        private boolean cancelled;

        private Tween(long startNanos, long durationNanos, Easing easing, DoubleConsumer onFrame, Runnable onDone) {
            this.startNanos = startNanos;
            this.durationNanos = Math.max(1, durationNanos);
            this.easing = easing;
            this.onFrame = onFrame;
            this.onDone = onDone;
        }

        public void cancel() {
            cancelled = true;
            ACTIVE.remove(this);
        }
    }

    /** Runs onFrame(0..1 eased) for durationMs, then onDone. Must be called on the EDT. */
    public static Tween run(int durationMs, Easing easing, DoubleConsumer onFrame, Runnable onDone) {
        if (!enabled || durationMs <= 0) {
            onFrame.accept(1.0);
            if (onDone != null) onDone.run();
            return null;
        }
        Tween t = new Tween(System.nanoTime(), durationMs * 1_000_000L, easing, onFrame, onDone);
        ACTIVE.add(t);
        onFrame.accept(easing.apply(0));
        if (!TIMER.isRunning()) TIMER.start();
        return t;
    }

    public static Tween run(int durationMs, Easing easing, DoubleConsumer onFrame) {
        return run(durationMs, easing, onFrame, null);
    }

    /** Runs a task after a delay on the EDT (used for staggered entrances). */
    public static void later(int delayMs, Runnable r) {
        if (!enabled || delayMs <= 0) {
            r.run();
            return;
        }
        Timer t = new Timer(delayMs, e -> r.run());
        t.setRepeats(false);
        t.start();
    }

    private static void tick() {
        long now = System.nanoTime();
        List<Tween> snapshot = new ArrayList<>(ACTIVE);
        for (Tween t : snapshot) {
            if (t.cancelled) continue;
            double p = Math.min(1.0, (now - t.startNanos) / (double) t.durationNanos);
            t.onFrame.accept(t.easing.apply(p));
            if (p >= 1.0) {
                ACTIVE.remove(t);
                if (t.onDone != null && !t.cancelled) t.onDone.run();
            }
        }
        if (ACTIVE.isEmpty()) TIMER.stop();
        Toolkit.getDefaultToolkit().sync();
    }

    /** An animated number (hover progress, count-up value...). Calls onChange after every step. */
    public static final class Value {
        private double value;
        private Tween current;
        private final Runnable onChange;

        public Value(double initial, Runnable onChange) {
            this.value = initial;
            this.onChange = onChange;
        }

        public double get() {
            return value;
        }

        public void set(double v) {
            if (current != null) current.cancel();
            value = v;
            if (onChange != null) onChange.run();
        }

        public void animateTo(double target, int ms) {
            animateTo(target, ms, EASE_OUT);
        }

        public void animateTo(double target, int ms, Easing easing) {
            if (current != null) current.cancel();
            final double from = value;
            if (from == target) return;
            current = run(ms, easing, p -> {
                value = from + (target - from) * p;
                if (onChange != null) onChange.run();
            });
            if (current == null) value = target;
        }
    }
}
