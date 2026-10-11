package security;

import util.AppLog;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;

/**
 * Monitors user interaction across the entire desktop application window.
 * If no mouse or keyboard events occur for idleMinutes, triggers a lock callback.
 */
public class IdleLockMonitor {
    private static final IdleLockMonitor INSTANCE = new IdleLockMonitor();

    private Timer idleTimer;
    private int idleMinutes = 10;
    private Runnable lockCallback;
    private boolean active = false;
    private final AWTEventListener eventListener;

    private IdleLockMonitor() {
        eventListener = event -> onUserActivity();
    }

    public static IdleLockMonitor getInstance() {
        return INSTANCE;
    }

    public synchronized void setLockCallback(Runnable callback) {
        this.lockCallback = callback;
    }

    public synchronized void setIdleMinutes(int minutes) {
        this.idleMinutes = minutes;
        if (active) {
            restartTimer();
        }
    }

    public synchronized void start() {
        if (active) return;
        active = true;

        long eventMask = AWTEvent.MOUSE_EVENT_MASK |
                         AWTEvent.MOUSE_MOTION_EVENT_MASK |
                         AWTEvent.KEY_EVENT_MASK;

        Toolkit.getDefaultToolkit().addAWTEventListener(eventListener, eventMask);
        restartTimer();
        AppLog.info("IdleLockMonitor started with timeout: " + idleMinutes + " minutes");
    }

    public synchronized void stop() {
        if (!active) return;
        active = false;

        try {
            Toolkit.getDefaultToolkit().removeAWTEventListener(eventListener);
        } catch (Exception ignored) {
        }

        if (idleTimer != null) {
            idleTimer.stop();
            idleTimer = null;
        }
        AppLog.info("IdleLockMonitor stopped");
    }

    public synchronized void reset() {
        if (active && idleTimer != null) {
            idleTimer.restart();
        }
    }

    private synchronized void restartTimer() {
        if (idleTimer != null) {
            idleTimer.stop();
        }
        if (idleMinutes <= 0) {
            // Disabled
            return;
        }

        int delayMillis = idleMinutes * 60 * 1000;
        idleTimer = new Timer(delayMillis, e -> {
            synchronized (IdleLockMonitor.this) {
                if (!active || !Session.isLoggedIn()) return;
            }
            AppLog.info("Idle lock triggered due to inactivity (" + idleMinutes + " min)");
            if (lockCallback != null) {
                SwingUtilities.invokeLater(lockCallback);
            }
        });
        idleTimer.setRepeats(false);
        idleTimer.start();
    }

    private void onUserActivity() {
        synchronized (this) {
            if (active && idleTimer != null && idleTimer.isRunning()) {
                idleTimer.restart();
            }
        }
    }
}
