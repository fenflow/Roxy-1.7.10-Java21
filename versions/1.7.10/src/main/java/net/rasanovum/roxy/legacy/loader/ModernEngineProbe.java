package net.rasanovum.roxy.legacy.loader;

import java.nio.file.Path;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.rasanovum.roxy.legacy.bridge.LegacyModelInputs;

/** Developer diagnostic. Passing this is not a claim that terrain rendering is integrated. */
public final class ModernEngineProbe {

    private ModernEngineProbe() {}

    public static String run(Path gameDirectory) throws Exception {
        RenderBackendGuard.requireOpenGl();
        Path jar = VoxyJarLocator.find(gameDirectory);
        try (ExternalVoxyLoader loader = new ExternalVoxyLoader(jar, gameDirectory.resolve("roxy/runtime-libs"))) {
            String gpu = (String) loader.loadClass("net.rasanovum.roxy.legacy.modern.GpuProbe")
                .getMethod("run")
                .invoke(null);
            return "Voxy engine diagnostic: " + gpu + "; " + loader.externalClassesLoaded() + " external classes.";
        }
    }

    public static String models(Path gameDirectory) throws Exception {
        RenderBackendGuard.requireOpenGl();
        Block[] blocks = { Blocks.stone, Blocks.oak_stairs, Blocks.log, Blocks.leaves, Blocks.water, Blocks.grass,
            Blocks.snow_layer, Blocks.ice };
        String[] names = new String[blocks.length];
        int[] metadata = new int[blocks.length];
        int[] opacity = new int[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            names[i] = Block.blockRegistry.getNameForObject(blocks[i]);
            opacity[i] = Math.min(15, blocks[i].getLightOpacity());
            LegacyModelInputs.capture(blocks[i], 0);
        }
        try (ExternalVoxyLoader loader = new ExternalVoxyLoader(
            VoxyJarLocator.find(gameDirectory),
            gameDirectory.resolve("roxy/runtime-libs"))) {
            return (String) loader.loadClass("net.rasanovum.roxy.legacy.modern.ModelProbe")
                .getMethod("run", Path.class, String[].class, int[].class, int[].class)
                .invoke(null, gameDirectory.resolve("roxy/model-probe"), names, metadata, opacity);
        }
    }

    public static String renderer(Path gameDirectory) throws Exception {
        RenderBackendGuard.requireOpenGl();
        try (ExternalVoxyLoader loader = new ExternalVoxyLoader(
            VoxyJarLocator.find(gameDirectory),
            gameDirectory.resolve("roxy/runtime-libs"))) {
            return (String) loader.loadClass("net.rasanovum.roxy.legacy.modern.RendererProbe")
                .getMethod("run", Path.class)
                .invoke(null, gameDirectory);
        }
    }
}
