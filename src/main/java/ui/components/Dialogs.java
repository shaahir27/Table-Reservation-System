package ui.components;

import java.awt.Component;

/**
 * Drop-in, themed stand-in for the JOptionPane call shapes the screens already use.
 * Picks a meaningful confirm-button label and tone from the dialog title.
 */
public final class Dialogs {
    public static final int YES_OPTION = 0;
    public static final int NO_OPTION = 1;
    public static final int YES_NO_OPTION = 0;
    public static final int ERROR_MESSAGE = 0;
    public static final int INFORMATION_MESSAGE = 1;
    public static final int WARNING_MESSAGE = 2;
    public static final int QUESTION_MESSAGE = 3;

    private Dialogs() {
    }

    public static int showConfirmDialog(Component parent, Object message, String title, int optionType, int messageType) {
        String t = title == null ? "" : title;
        String lower = t.toLowerCase();
        String label;
        boolean danger;
        if (lower.contains("deletion")) {
            label = "Delete";
            danger = true;
        } else if (lower.contains("cancel")) {
            label = lower.contains("refill") ? "Cancel & Refill" : "Cancel Reservation";
            danger = true;
        } else if (lower.contains("removal")) {
            label = "Remove";
            danger = true;
        } else if (lower.contains("no-show")) {
            label = "Release Table";
            danger = true;
        } else if (lower.contains("role")) {
            label = "Change Role";
            danger = false;
        } else if (lower.contains("password reset")) {
            label = "Reset Password";
            danger = false;
        } else if (lower.contains("complete")) {
            label = "Mark Completed";
            danger = false;
        } else if (lower.contains("status")) {
            label = "Confirm";
            danger = messageType == WARNING_MESSAGE;
        } else {
            label = "Yes, Continue";
            danger = messageType == WARNING_MESSAGE;
        }
        String cancel = danger ? "Keep It" : "Go Back";
        boolean ok = danger
                ? ConfirmDialog.confirm(parent, t.replaceFirst("^Confirm ", ""), String.valueOf(message), label, true, cancel)
                : ConfirmDialog.ask(parent, t.replaceFirst("^Confirm ", ""), String.valueOf(message), label);
        return ok ? YES_OPTION : NO_OPTION;
    }

    public static int showConfirmDialog(Component parent, Object message, String title, int optionType) {
        return showConfirmDialog(parent, message, title, optionType, QUESTION_MESSAGE);
    }

    public static void showMessageDialog(Component parent, Object message, String title, int messageType) {
        String msg = String.valueOf(message);
        switch (messageType) {
            case ERROR_MESSAGE -> ConfirmDialog.error(parent, title, msg);
            case WARNING_MESSAGE -> ConfirmDialog.warning(parent, title, msg);
            default -> ConfirmDialog.info(parent, title, msg);
        }
    }
}
