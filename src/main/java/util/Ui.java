package util;

import javax.swing.SwingWorker;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Utility for asynchronous background task execution using SwingWorker.
 * Ensures the Swing Event Dispatch Thread (EDT) remains unblocked during I/O and DB queries.
 */
public class Ui {

    /**
     * Executes work in a background worker thread and delivers results or errors back on the EDT.
     *
     * @param work      Callable providing background computation or database access
     * @param onSuccess Callback invoked on the EDT with the computed result
     * @param onError   Callback invoked on the EDT if an exception occurs
     * @param <T>       Result type
     */
    public static <T> void async(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                try {
                    T result = get();
                    if (onSuccess != null) {
                        onSuccess.accept(result);
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    AppLog.error("Asynchronous UI task failed: " + cause.getMessage(), cause);
                    if (onError != null) {
                        onError.accept(cause);
                    }
                }
            }
        };

        worker.execute();
    }
}
