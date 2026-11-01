package net.rasanovum.roxy.legacy.bridge;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/** Contains failures from third-party model callbacks, once per model and resource generation. */
public final class ModelCaptureCache<T> {

    private final Map<String, T> models = new ConcurrentHashMap<String, T>();
    private int fallbacks;

    public T get(String key) {
        return models.get(key);
    }

    public T capture(String key, Callable<T> capture, Callable<T> fallback, BiConsumer<String, Throwable> report)
        throws Exception {
        T existing = models.get(key);
        if (existing != null) return existing;
        T result;
        try {
            result = capture.call();
        } catch (Exception | LinkageError error) {
            net.rasanovum.roxy.legacy.loader.SupportReport.failure(error);
            // Do not consume VM failures, ThreadDeath or unrelated Errors.
            result = fallback.call();
            fallbacks++;
            report.accept(key, error);
        }
        if (result == null) throw new IllegalStateException("Null model: " + key);
        models.put(key, result);
        return result;
    }

    public int fallbackCount() {
        return fallbacks;
    }

    public void clear() {
        models.clear();
        fallbacks = 0;
    }
}
