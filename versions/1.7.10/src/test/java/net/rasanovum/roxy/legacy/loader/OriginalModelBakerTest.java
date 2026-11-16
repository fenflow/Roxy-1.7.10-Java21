package net.rasanovum.roxy.legacy.loader;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import net.rasanovum.roxy.legacy.bridge.LegacyModelInputs;
import net.rasanovum.roxy.legacy.bridge.ModelCaptureCache;
import net.rasanovum.roxy.legacy.bridge.ModelSnapshot;

import org.junit.Assume;
import org.junit.Test;

public class OriginalModelBakerTest {

    @Test
    public void originalSixViewBakerHasNoCracksOnCardinalCubeFaces() throws Exception {
        verifyModel(false);
    }

    @Test
    public void fluidInputsKeepFullSidesAndAnIndependentlyLoweredSurface() throws Exception {
        verifyModel(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void restoredMissingBlockUsesStoneUnderItsOriginalKey() throws Exception {
        Field cache = LegacyModelInputs.class.getDeclaredField("MODELS");
        cache.setAccessible(true);
        ModelCaptureCache<ModelSnapshot> models = (ModelCaptureCache<ModelSnapshot>) cache.get(null);
        ModelSnapshot stone = new ModelSnapshot(new float[0], false, false, false, 0, 0xffffff);
        models.clear();
        try {
            models.capture("minecraft:stone:0", () -> stone, () -> stone, (key, error) -> fail());
            LegacyModelInputs.captureCached("VillageNames:glazedTerracotta", 7);
            assertSame(stone, LegacyModelInputs.get("VillageNames:glazedTerracotta", 7));
        } finally {
            models.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private void verifyModel(boolean fluid) throws Exception {
        String configured = System.getProperty("roxy.voxyJar");
        Assume.assumeNotNull(configured);
        float[][] corners = { { 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 0, 1 }, { 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0 },
            { 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0 }, { 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1 },
            { 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0 }, { 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1 } };
        float[] geometry = new float[6 * 4 * 6];
        for (int face = 0; face < 6; face++) for (int vertex = 0; vertex < 4; vertex++) {
            int index = (face * 4 + vertex) * 6;
            System.arraycopy(corners[face], vertex * 3, geometry, index, 3);
            geometry[index + 3] = .5f;
            geometry[index + 4] = .5f;
            geometry[index + 5] = 3;
        }
        Field cache = LegacyModelInputs.class.getDeclaredField("MODELS");
        cache.setAccessible(true);
        ModelCaptureCache<ModelSnapshot> models = (ModelCaptureCache<ModelSnapshot>) cache.get(null);
        models.clear();
        float[][] fluidFaces = null;
        if (fluid) {
            fluidFaces = new float[6][];
            for (int face = 0; face < 6; face++)
                fluidFaces[face] = Arrays.copyOfRange(geometry, face * 24, (face + 1) * 24);
            for (int vertex = 0; vertex < 4; vertex++) fluidFaces[1][vertex * 6 + 1] = .875f;
            geometry = fluidFaces[1];
        }
        ModelSnapshot snapshot = new ModelSnapshot(
            geometry,
            fluid,
            false,
            fluid,
            0,
            0xffffff,
            java.util.Collections.<Integer, Integer>emptyMap(),
            fluidFaces);
        models.capture("roxy:test_cube:0", () -> snapshot, () -> snapshot, (key, error) -> fail());
        try (ExternalVoxyLoader loader = new ExternalVoxyLoader(
            Paths.get(configured),
            Files.createTempDirectory("roxy-baker-"))) {
            Class<?> stateType = loader.loadClass("net.rasanovum.roxy.legacy.modern.BlockState");
            Object state = stateType.getConstructor(String.class, int.class, int.class)
                .newInstance("roxy:test_cube", 0, 15);
            Class<?> bakeryType = loader
                .loadClass("me.cortex.voxy.client.core.model.bakery.SoftwareModelTextureBakery");
            Object bakery = bakeryType.getConstructor()
                .newInstance();
            Class<?> memory = Class.forName("org.lwjgl.system.MemoryUtil");
            long output = (Long) memory.getMethod("nmemAlloc", long.class)
                .invoke(null, 6 * 256 * 8L);
            try {
                Field rasterizer = bakeryType.getDeclaredField("rasterizer");
                rasterizer.setAccessible(true);
                Object raster = rasterizer.get(bakery);
                int[] pixels = new int[256];
                Arrays.fill(pixels, 0xff987654);
                raster.getClass()
                    .getMethod("setSamplerTexture", int[].class, int.class, int.class)
                    .invoke(raster, pixels, 16, 16);
                int bakeFlags = (Integer) bakeryType.getMethod("renderToOutput", stateType, long.class)
                    .invoke(bakery, state, output);
                assertEquals("The original baker must retain the captured directional shade", 1, bakeFlags & 1);
                java.lang.reflect.Method read = memory.getMethod("memGetLong", long.class);
                for (int face = 0; face < 6; face++) for (int pixel = 0; pixel < 256; pixel++) {
                    long value = (Long) read.invoke(null, output + (face * 256 + pixel) * 8L);
                    assertEquals("Crack/colour error on face " + face + ", pixel " + pixel, 0xff987654, (int) value);
                }
                if (fluid) {
                    long bottom = (Long) read.invoke(null, output);
                    long top = (Long) read.invoke(null, output + 256 * 8L);
                    assertTrue(
                        "The top surface must be recessed independently of the full side faces",
                        (top >>> 40) > (bottom >>> 40));
                }
            } finally {
                memory.getMethod("nmemFree", long.class)
                    .invoke(null, output);
                bakeryType.getMethod("free")
                    .invoke(bakery);
            }
        } finally {
            models.clear();
        }
    }
}
