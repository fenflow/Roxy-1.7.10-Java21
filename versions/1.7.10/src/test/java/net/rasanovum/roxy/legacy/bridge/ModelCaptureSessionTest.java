package net.rasanovum.roxy.legacy.bridge;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.renderer.Tessellator;

import org.junit.Before;
import org.junit.Test;

public class ModelCaptureSessionTest {

    public static class Manager {

        static boolean capturing, drawing;
        static int translations, discards, stops, cleanups;

        public static Tessellator startCapturingAndGet() {
            assertFalse(capturing);
            capturing = drawing = true;
            translations++;
            assertTrue("translation stack overflow", translations <= 16);
            return null;
        }

        public static boolean isCurrentlyCapturing() {
            return capturing;
        }

        public static void discardTessellator(Tessellator tessellator) {
            drawing = false;
            discards++;
        }

        public static List<?> stopCapturingToPooledQuads() {
            assertTrue(capturing);
            capturing = drawing = false;
            translations--;
            stops++;
            return Collections.emptyList();
        }

        public static void cleanup() {
            capturing = drawing = false;
            cleanups++;
        }
    }

    @Before
    public void reset() {
        Manager.capturing = Manager.drawing = false;
        Manager.translations = Manager.discards = Manager.stops = Manager.cleanups = 0;
    }

    @Test
    public void repeatedFailedCallbacksDoNotExhaustTranslationsOrLeaveDrawingActive() throws Exception {
        for (int i = 0; i < 40; i++) {
            try (ModelCaptureSession capture = new ModelCaptureSession(Manager.class)) {
                throw new IllegalStateException("failed model");
            } catch (IllegalStateException expected) {
                assertEquals("failed model", expected.getMessage());
            }
            assertFalse(Manager.capturing);
            assertFalse(Manager.drawing);
            assertEquals(0, Manager.translations);
        }
        assertEquals(40, Manager.discards);
        assertEquals(40, Manager.stops);
    }

    @Test
    public void completedCaptureIsNotStoppedTwice() throws Exception {
        try (ModelCaptureSession capture = new ModelCaptureSession(Manager.class)) {
            capture.finish();
        }
        assertEquals(1, Manager.stops);
        assertEquals(0, Manager.discards);
        assertEquals(1, Manager.cleanups);
        assertEquals(0, Manager.translations);
    }
}
