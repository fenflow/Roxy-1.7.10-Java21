package net.rasanovum.roxy.legacy.bridge;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

/** Captures actual legacy renderer geometry on the client thread, before asynchronous Voxy baking. */
public final class LegacyModelInputs {

    private static final ModelCaptureCache<ModelSnapshot> MODELS = new ModelCaptureCache<ModelSnapshot>();

    private LegacyModelInputs() {}

    public static void clear() {
        MODELS.clear();
    }

    public static int fallbackCount() {
        return MODELS.fallbackCount();
    }

    public static ModelSnapshot get(String name, int metadata) {
        if (name.equals("minecraft:air")) return new ModelSnapshot(new float[0], false, false, false, 0, 0xffffff);
        ModelSnapshot result = MODELS.get(name + ":" + metadata);
        if (result == null) throw new IllegalStateException(
            "Model has not been captured on the client thread: " + name + ":" + metadata);
        return result;
    }

    public static int atlasTexture() {
        return Minecraft.getMinecraft()
            .getTextureManager()
            .getTexture(TextureMap.locationBlocksTexture)
            .getGlTextureId();
    }

    public static int lightmapTexture() {
        DynamicTexture texture = cpw.mods.fml.common.ObfuscationReflectionHelper.getPrivateValue(
            EntityRenderer.class,
            Minecraft.getMinecraft().entityRenderer,
            "lightmapTexture",
            "field_78513_d");
        return texture.getGlTextureId();
    }

    public static ModelSnapshot capture(final Block block, final int metadata) throws Exception {
        String name = Block.blockRegistry.getNameForObject(block);
        return MODELS.capture(
            name + ":" + metadata,
            () -> captureModel(block, metadata),
            () -> fallback(block, metadata),
            (key, error) -> {
                if (MODELS.fallbackCount() <= 32) org.apache.logging.log4j.LogManager.getLogger("Roxy")
                    .warn(
                        "Using a static texture fallback for model {}. Terrain capture continues; model callback failed",
                        key,
                        error);
            });
    }

    /** Keep the persisted key even if the current registry removed or aliased its block. */
    public static void captureCached(String name, int metadata) throws Exception {
        Block block = (Block) Block.blockRegistry.getObject(name);
        ModelSnapshot snapshot;
        if (block == null || block == Blocks.air) {
            snapshot = MODELS.get("minecraft:stone:0");
            if (snapshot == null) snapshot = capture(Blocks.stone, 0);
            org.apache.logging.log4j.LogManager.getLogger("Roxy")
                .warn("Cached block {}:{} is unavailable; using stone for its saved terrain", name, metadata);
        } else snapshot = capture(block, metadata);
        MODELS.remember(name + ":" + metadata, snapshot);
    }

    private static ModelSnapshot captureModel(final Block block, final int metadata) throws Exception {
        Class<?> manager = Class.forName("com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager");
        if ((Boolean) manager.getMethod("isCurrentlyCapturing")
            .invoke(null)) throw new IllegalStateException("Nested model capture");
        final BiomeGenBase[] colourBiome = { BiomeGenBase.plains };
        final boolean fluid = block.getMaterial()
            .isLiquid();
        final int[] fluidFace = { -1 };
        final IBlockAccess access = new IBlockAccess() {

            public Block getBlock(int x, int y, int z) {
                if (fluidFace[0] >= 0) {
                    ForgeDirection direction = ForgeDirection.getOrientation(fluidFace[0]);
                    int outward = x * direction.offsetX + (y - 64) * direction.offsetY + z * direction.offsetZ;
                    return outward >= 1 ? Blocks.air : block;
                }
                return x == 0 && y == 64 && z == 0 ? block : Blocks.air;
            }

            public TileEntity getTileEntity(int x, int y, int z) {
                return null;
            }

            public int getLightBrightnessForSkyBlocks(int x, int y, int z, int light) {
                return 0xf000f0;
            }

            public int getBlockMetadata(int x, int y, int z) {
                return getBlock(x, y, z) == block ? metadata : 0;
            }

            public boolean isAirBlock(int x, int y, int z) {
                return getBlock(x, y, z) == Blocks.air;
            }

            public BiomeGenBase getBiomeGenForCoords(int x, int z) {
                return colourBiome[0];
            }

            public int getHeight() {
                return 256;
            }

            public boolean extendedLevelsInChunkCache() {
                return false;
            }

            public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
                return 0;
            }

            public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean fallback) {
                return getBlock(x, y, z).isOpaqueCube();
            }
        };
        int colour = block.colorMultiplier(access, 0, 64, 0);
        try {
            float[][] fluidFaces = fluid ? new float[6][] : null;
            float[] vertices;
            if (fluid) {
                for (int face = 0; face < 6; face++) {
                    fluidFace[0] = face;
                    fluidFaces[face] = geometry(manager, access, block, colour, false);
                }
                vertices = fluidFaces[1];
                fluidFace[0] = -1;
            } else vertices = geometry(manager, access, block, colour, true);
            Map<Integer, Integer> colours = new HashMap<Integer, Integer>();
            for (BiomeGenBase biome : BiomeGenBase.getBiomeGenArray()) {
                if (biome == null) continue;
                colourBiome[0] = biome;
                int value = block.colorMultiplier(access, 0, 64, 0);
                if (value != colour) colours.put(biome.biomeID, value);
            }
            ModelSnapshot result = new ModelSnapshot(
                vertices,
                fluid,
                block instanceof BlockLeavesBase,
                block.getRenderBlockPass() == 1,
                block.getLightValue(access, 0, 64, 0),
                colour,
                colours,
                fluidFaces);
            return result;
        } finally {
            manager.getMethod("cleanup")
                .invoke(null);
        }
    }

    /** Does not invoke a custom renderer, world lookup, tile factory, tint or light callback. */
    private static ModelSnapshot fallback(Block block, int metadata) {
        int[][] faces = { { 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1 }, { 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0 },
            { 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0 }, { 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1 },
            { 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0 }, { 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1 } };
        float[] vertices = new float[144];
        for (int face = 0; face < 6; face++) {
            IIcon icon = null;
            try {
                icon = block.getIcon(face, metadata);
            } catch (RuntimeException | LinkageError ignored) {}
            if (icon == null) icon = Minecraft.getMinecraft()
                .getTextureMapBlocks()
                .getAtlasSprite("missingno");
            for (int v = 0; v < 4; v++) {
                int offset = face * 24 + v * 6;
                for (int axis = 0; axis < 3; axis++) vertices[offset + axis] = faces[face][v * 3 + axis];
                vertices[offset + 3] = v == 0 || v == 3 ? icon.getMinU() : icon.getMaxU();
                vertices[offset + 4] = v < 2 ? icon.getMaxV() : icon.getMinV();
                vertices[offset + 5] = 3;
            }
        }
        boolean translucent = false;
        try {
            translucent = block.getRenderBlockPass() == 1;
        } catch (RuntimeException | LinkageError ignored) {}
        return new ModelSnapshot(vertices, false, block instanceof BlockLeavesBase, translucent, 0, 0xffffff);
    }

    private static float[] geometry(Class<?> manager, IBlockAccess access, Block block, int colour, boolean allFaces)
        throws Exception {
        try (ModelCaptureSession capture = new ModelCaptureSession(manager)) {
            Tessellator tess = capture.tessellator;
            tess.startDrawingQuads();
            tess.setTranslation(0, -64, 0);
            RenderBlocks renderer = new RenderBlocks(access);
            renderer.renderAllFaces = allFaces;
            renderer.renderBlockByRenderType(block, 0, 64, 0);
            List<?> quads = capture.finish();
            Class<?> view = Class.forName("com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadView");
            Method[] values = { view.getMethod("getX", int.class), view.getMethod("getY", int.class),
                view.getMethod("getZ", int.class), view.getMethod("getTexU", int.class),
                view.getMethod("getTexV", int.class) };
            float[] vertices = new float[quads.size() * 24];
            int cursor = 0;
            for (Object quad : quads) {
                boolean shade = (Boolean) view.getMethod("hasDirectionalShading")
                    .invoke(quad);
                int vertexColour = ((Number) view.getMethod("getColor", int.class)
                    .invoke(quad, 0)).intValue();
                // Standard RenderBlocks cubes and liquids always use directional lighting. Some
                // Angelica capture paths normalize their vertex colours and omit the quad flag.
                shade |= block.getRenderType() == 0 || block.getMaterial()
                    .isLiquid() || LegacyVertexColour.hasDirectionalShade(vertexColour, colour);
                int red = vertexColour & 255;
                int green = (vertexColour >>> 8) & 255;
                int blue = (vertexColour >>> 16) & 255;
                boolean tinted = colour != 0xffffff && (red != green || green != blue);
                for (int vertex = 0; vertex < 4; vertex++) {
                    for (Method value : values) vertices[cursor++] = ((Number) value.invoke(quad, vertex)).floatValue();
                    // Legacy solid renderers also emit alpha-cutout overlays (notably grass sides).
                    vertices[cursor++] = 1 | (shade ? 2 : 0) | (tinted ? 4 : 0);
                }
            }
            return vertices;
        }
    }
}
