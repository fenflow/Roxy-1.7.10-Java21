package net.rasanovum.roxy.legacy.loader;

import static org.junit.Assert.*;

import org.junit.Test;

public class SupportReportTest {

    @Test
    public void failureHistoryOmitsMessagesAndSourceNames() throws Exception {
        Throwable error = new IllegalStateException("C:/Users/PrivateName/server.example.org");
        error.setStackTrace(
            new StackTraceElement[] { new StackTraceElement("example.Model", "bake", "PrivateName.java", 42) });
        SupportReport.failure(error);
        java.lang.reflect.Field field = SupportReport.class.getDeclaredField("FAILURES");
        field.setAccessible(true);
        String history = field.get(null)
            .toString();
        assertFalse(history.contains("PrivateName"));
        assertFalse(history.contains("server.example.org"));
        assertTrue(history.contains("example.Model.bake:42"));
        for (int i = 0; i < 30; i++) SupportReport.failure(error);
        assertEquals(16, ((java.util.Deque<?>) field.get(null)).size());
    }
}
