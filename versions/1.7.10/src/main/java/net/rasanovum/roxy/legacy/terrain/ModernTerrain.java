package net.rasanovum.roxy.legacy.terrain;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.rasanovum.roxy.legacy.bridge.LegacyMapper;
import net.rasanovum.roxy.legacy.bridge.LegacyModelInputs;
import net.rasanovum.roxy.legacy.loader.ExternalVoxyLoader;
import net.rasanovum.roxy.legacy.loader.LoadDiagnostics;
import net.rasanovum.roxy.legacy.loader.VoxyJarLocator;
import net.rasanovum.roxy.legacy.shader.ShaderHooks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Experimental client lifecycle for the actual external Voxy renderer. */
public final class ModernTerrain implements IResourceManagerReloadListener {

    public static final ModernTerrain INSTANCE = new ModernTerrain();
    private static final Logger LOG = LogManager.getLogger("Roxy");
    private final Minecraft mc = Minecraft.getMinecraft();
    private ExternalVoxyLoader loader;
    private Object runtime;
    private WorldClient world;
    private WorldClient observedWorld;
    private Method ingest, ingestCapacity, registerBlock, registerBiome, draw, prepare, close, debug;
    private final Map<Block, it.unimi.dsi.fastutil.ints.Int2IntMap> states = new IdentityHashMap<Block, it.unimi.dsi.fastutil.ints.Int2IntMap>();
    private final java.util.Set<Block> unsafeProperties = java.util.Collections
        .newSetFromMap(new IdentityHashMap<Block, Boolean>());
    private final Map<Integer, Integer> biomes = new HashMap<Integer, Integer>();
    private final CaptureQueue<ObservedChunk> queue = new CaptureQueue<ObservedChunk>();
    private final Map<Long, Long> captured = new java.util.concurrent.ConcurrentHashMap<Long, Long>();
    private final Map<Long, Integer> dirty = new LinkedHashMap<Long, Integer>();
    private final WorldChanges changes = new WorldChanges(this);
    private long ticks;
    private int sections;
    private boolean enabled;
    private Configuration config;
    private boolean hud;
    private Object shaderPipeline;
    private int distanceChunks = 64;
    private String failure;
    private boolean renderedThisFrame;
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private final float[] projection = new float[16];
    private final float[] modelView = new float[16];
    private static final int[][] LIGHT_NEIGHBORS = { { 0, 1, 0 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 },
        { 0, 0, -1 } };

    private ModernTerrain() {}

    public void initialize() {
        config = new Configuration(
            mc.mcDataDir.toPath()
                .resolve("config/roxy.cfg")
                .toFile());
        config.load();
        enabled = config
            .getBoolean("enabled", "voxy", true, "Render distant terrain using the separate external Voxy JAR.");
        distanceChunks = config
            .getInt("distanceChunks", "voxy", 64, 8, 128, "Maximum Voxy terrain distance in chunks.");
        hud = config.getBoolean("hud", "voxy", false, "Show Voxy status outside the debug screen.");
        config.save();
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        ((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(this);
    }

    public String toggle() {
        setEnabled(!enabled);
        return status();
    }

    public void setEnabled(boolean value) {
        enabled = value;
        failure = null;
        if (!enabled) stop();
        config.get("voxy", "enabled", true)
            .set(enabled);
        config.save();
    }

    public void setDistance(int chunks) {
        if (chunks < 8 || chunks > 128) throw new IllegalArgumentException("Distance must be 8–128 chunks");
        distanceChunks = chunks;
        config.get("voxy", "distanceChunks", 64)
            .set(chunks);
        config.save();
        if (runtime != null) try {
            runtime.getClass()
                .getMethod("setDistance", int.class)
                .invoke(runtime, chunks);
        } catch (Exception error) {
            fail(error);
        }
    }

    public void toggleHud() {
        hud = !hud;
        config.get("voxy", "hud", false)
            .set(hud);
        config.save();
    }

    public String status() {
        if (failure != null) return "Voxy modern renderer failed: " + failure;
        return "Voxy " + (enabled ? "ON" : "OFF")
            + ", "
            + sections
            + " sections captured, "
            + queue.size()
            + " chunks pending, "
            + LegacyModelInputs.fallbackCount()
            + " model fallbacks, "
            + distanceChunks
            + "-chunk distance";
    }

    public boolean isEnabled() {
        return enabled && failure == null;
    }

    /** Fixed labels and counters only; status() may contain private exception text. */
    public String supportState() {
        return "renderer.state="
            + (failure != null ? "failed" : !enabled ? "disabled" : runtime != null ? "running" : "waiting")
            + "\nrenderer.capturedSections="
            + sections
            + "\nrenderer.pendingChunks="
            + queue.size()
            + "\nrenderer.modelFallbacks="
            + LegacyModelInputs.fallbackCount()
            + "\n";
    }

    public int distance() {
        return distanceChunks;
    }

    private void start() throws Exception {
        if (mc.theWorld == null) return;
        if (observedWorld != null && observedWorld != mc.theWorld) stop(false);
        observedWorld = mc.theWorld;
        shaderPipeline = ShaderHooks.activePipeline();
        world = mc.theWorld;
        Path game = mc.mcDataDir.toPath();
        if (loader == null)
            loader = new ExternalVoxyLoader(VoxyJarLocator.find(game), game.resolve("roxy/runtime-libs"));
        Class<?> type = loader.loadClass("net.rasanovum.roxy.legacy.modern.TerrainRuntime");
        String identity = mc.isIntegratedServerRunning() ? "local:" + mc.getIntegratedServer()
            .getFolderName() : "server:" + (mc.func_147104_D() == null ? "unknown" : mc.func_147104_D().serverIP);
        Path database = game.resolve("roxy/voxy-worlds")
            .resolve(
                UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8))
                    .toString())
            .resolve("dim" + world.provider.dimensionId);
        runtime = type.getConstructor(Path.class, Path.class)
            .newInstance(game, database);
        close = type.getMethod("close");
        for (String entry : (String[]) type.getMethod("requiredModels")
            .invoke(runtime)) {
            String[] parts = entry.split("\t");
            Block block = (Block) Block.blockRegistry.getObject(parts[0]);
            if (block == null || block == Blocks.air)
                throw new IllegalStateException("Cached model is missing from this modpack: " + parts[0]);
            LegacyModelInputs.capture(block, Integer.parseInt(parts[1]));
        }
        ingest = type.getMethod("ingest", int.class, int.class, int.class, long[].class);
        ingestCapacity = type.getMethod("ingestCapacity");
        registerBlock = type.getMethod("registerBlock", String.class, int.class, int.class);
        registerBiome = type.getMethod("registerBiome", int.class);
        draw = type.getMethod(
            "render",
            float[].class,
            float[].class,
            double.class,
            double.class,
            double.class,
            int.class,
            float.class);
        prepare = type.getMethod("prepare", draw.getParameterTypes());
        debug = type.getMethod("debug");
        type.getMethod("start", int.class, int.class)
            .invoke(runtime, mc.gameSettings.renderDistanceChunks, mc.gameSettings.mipmapLevels);
        type.getMethod("setDistance", int.class)
            .invoke(runtime, distanceChunks);
        world.addWorldAccess(changes);
        LOG.info("Original Voxy renderer started using world database {}", database);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (world != null && world != mc.theWorld) stop();
        if (!enabled || failure != null || mc.theWorld == null || mc.thePlayer == null || mc.isGamePaused()) return;
        try {
            if (runtime == null) start();
            if (shaderPipeline != ShaderHooks.activePipeline()) {
                stop(true);
                start();
            }
            if (++ticks % 5 == 1) {
                scan();
            }
            long begin = System.nanoTime();
            for (int count = 0; count < 128 && System.nanoTime() - begin < 6000000; count++) {
                if ((Integer) ingestCapacity.invoke(runtime) == 0) break;
                // Reserve half the budget for newly observed terrain even during heavy block updates.
                if (count % 2 == 0 && captureChanged()) continue;
                CaptureQueue.Work<ObservedChunk> work = queue.poll();
                if (work == null) break;
                capture(work.value, work.section);
                sections++;
                if (work.last) captured.put(work.key, ticks);
            }
        } catch (Exception | LinkageError error) {
            fail(error);
        }
    }

    void changed(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int firstY = Math.max(0, minY >> 4), lastY = Math.min(15, maxY >> 4);
        if (firstY > lastY || (long) maxX - minX > 2048 || (long) maxZ - minZ > 2048) return;
        int mask = ((1 << (lastY + 1)) - 1) & ~((1 << firstY) - 1);
        synchronized (dirty) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) for (int x = minX >> 4; x <= maxX >> 4; x++) {
                long key = LodChunk.key(x, z);
                // A section already processed in a partially captured chunk can change again.
                if (queue.changed(key, mask)) continue;
                if (!captured.containsKey(key)) continue;
                Integer previous = dirty.get(key);
                if (previous != null || dirty.size() < 4096) dirty.put(key, mask | (previous == null ? 0 : previous));
            }
        }
    }

    private boolean captureChanged() throws Exception {
        long key;
        int sy;
        synchronized (dirty) {
            if (dirty.isEmpty()) return false;
            Map.Entry<Long, Integer> entry = dirty.entrySet()
                .iterator()
                .next();
            key = entry.getKey();
            sy = Integer.numberOfTrailingZeros(entry.getValue());
            int remaining = entry.getValue() & ~(1 << sy);
            if (remaining == 0) dirty.remove(key);
            else entry.setValue(remaining);
        }
        int x = (int) (key >> 32), z = (int) key;
        if (!world.getChunkProvider()
            .chunkExists(x, z)) return true;
        Chunk chunk = world.getChunkFromChunkCoords(x, z);
        if (!(chunk instanceof EmptyChunk)) {
            capture(new ObservedChunk(chunk, world), sy);
            sections++;
        }
        return true;
    }

    private void scan() {
        int cx = MathHelper.floor_double(mc.thePlayer.posX) >> 4;
        int cz = MathHelper.floor_double(mc.thePlayer.posZ) >> 4;
        int radius = Math.max(8, mc.gameSettings.renderDistanceChunks + 2);
        for (int ring = 0; ring <= radius; ring++)
            for (int z = -ring; z <= ring; z++) for (int x = -ring; x <= ring; x++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != ring) continue;
                long key = LodChunk.key(cx + x, cz + z);
                Long last = captured.get(key);
                if (queue.contains(key) || last != null
                    || !world.getChunkProvider()
                        .chunkExists(cx + x, cz + z))
                    continue;
                Chunk chunk = world.getChunkFromChunkCoords(cx + x, cz + z);
                if (chunk instanceof EmptyChunk) continue;
                enqueue(chunk);
            }
    }

    private void enqueue(Chunk chunk) {
        long key = LodChunk.key(chunk.xPosition, chunk.zPosition);
        if (chunk instanceof EmptyChunk || queue.contains(key)) return;
        int preferred = 0;
        net.minecraft.world.chunk.storage.ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        for (int sy = 0; sy < 16; sy++) if (storage[sy] != null && !storage[sy].isEmpty()) preferred |= 1 << sy;
        queue.offer(key, new ObservedChunk(chunk, world), 65535, preferred);
    }

    public static void chunkReceived(Chunk chunk) {
        ModernTerrain terrain = INSTANCE;
        if (terrain.runtime == null || chunk.worldObj != terrain.world) return;
        int preferred = 0;
        net.minecraft.world.chunk.storage.ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        for (int sy = 0; sy < 16; sy++) if (storage[sy] != null && !storage[sy].isEmpty()) preferred |= 1 << sy;
        terrain.queue.offer(
            LodChunk.key(chunk.xPosition, chunk.zPosition),
            new ObservedChunk(chunk, terrain.world),
            65535,
            preferred);
    }

    @SubscribeEvent
    public void chunkUnloaded(net.minecraftforge.event.world.ChunkEvent.Unload event) {
        if (runtime != null && event.world == world) {
            // The provider can remove the chunk immediately after this event. Retain its received
            // data until our client-thread capture finishes, even when flying or teleporting away.
            long key = LodChunk.key(event.getChunk().xPosition, event.getChunk().zPosition);
            if (!captured.containsKey(key)) enqueue(event.getChunk());
            synchronized (dirty) {
                Integer changed = dirty.remove(key);
                if (changed != null) queue.offer(key, new ObservedChunk(event.getChunk(), world), changed, changed);
            }
            captured.remove(key);
        }
    }

    private void capture(ObservedChunk observed, int sy) throws Exception {
        observed.prepare();
        Chunk chunk = observed.chunk;
        long[] data = new long[4096];
        boolean empty = chunk.getBlockStorageArray()[sy] == null;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int wx = chunk.xPosition * 16 + x, wz = chunk.zPosition * 16 + z;
            int biome = observed.biomes[x + z * 16].biomeID;
            Integer biomeId = biomes.get(biome);
            if (biomeId == null) {
                biomeId = (Integer) registerBiome.invoke(runtime, biome);
                biomes.put(biome, biomeId);
            }
            if (empty) {
                long air = LegacyMapper.pack(
                    0,
                    biomeId,
                    observed.light(EnumSkyBlock.Sky, wx, sy * 16, wz),
                    observed.light(EnumSkyBlock.Block, wx, sy * 16, wz));
                for (int y = 0; y < 16; y++) data[x + 16 * (z + 16 * y)] = air;
                continue;
            }
            for (int y = 0; y < 16; y++) {
                int wy = sy * 16 + y;
                Block block = chunk.getBlock(x, wy, z);
                int id = 0;
                boolean air = block == Blocks.air;
                int opacity = air ? 0
                    : (block.getMaterial()
                        .isOpaque() ? 15 : 0);
                if (!air && !unsafeProperties.contains(block)) try {
                    air = block.isAir(observed, wx, wy, wz);
                    opacity = Math.max(0, Math.min(15, block.getLightOpacity(observed, wx, wy, wz)));
                } catch (RuntimeException | LinkageError error) {
                    unsafeProperties.add(block);
                    LOG.warn(
                        "Using static block properties for {} after a mod callback failed; capture continues",
                        Block.blockRegistry.getNameForObject(block),
                        error);
                }
                if (!air) {
                    int meta = chunk.getBlockMetadata(x, wy, z);
                    int key = (opacity << 16) | meta;
                    it.unimi.dsi.fastutil.ints.Int2IntMap blockStates = states.get(block);
                    if (blockStates == null) {
                        blockStates = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
                        blockStates.defaultReturnValue(-1);
                        states.put(block, blockStates);
                    }
                    int known = blockStates.get(key);
                    if (known == -1) {
                        LegacyModelInputs.capture(block, meta);
                        String name = Block.blockRegistry.getNameForObject(block);
                        known = (Integer) registerBlock.invoke(runtime, name, meta, opacity);
                        blockStates.put(key, known);
                    }
                    id = known;
                }
                int sky = world.provider.hasNoSky ? 0 : chunk.getSavedLightValue(EnumSkyBlock.Sky, x, wy, z);
                int light = chunk.getSavedLightValue(EnumSkyBlock.Block, x, wy, z);
                if (block.getUseNeighborBrightness()) {
                    for (int[] offset : LIGHT_NEIGHBORS) {
                        sky = Math
                            .max(sky, observed.light(EnumSkyBlock.Sky, wx + offset[0], wy + offset[1], wz + offset[2]));
                        light = Math.max(
                            light,
                            observed.light(EnumSkyBlock.Block, wx + offset[0], wy + offset[1], wz + offset[2]));
                    }
                }
                data[x + 16 * (z + 16 * y)] = LegacyMapper.pack(id, biomeId, sky, light);
            }
        }
        ingest.invoke(runtime, chunk.xPosition, sy, chunk.zPosition, data);
    }

    @SubscribeEvent
    public void fog(EntityViewRenderEvent.RenderFogEvent event) {
        if (runtime != null && enabled
            && failure == null
            && ShaderHooks.activePipeline() == null
            && event.fogMode >= 0
            && !LegacyTerrainRenderer.hasEnvironmentalFog(event.entity)) {
            GL11.glFogf(GL11.GL_FOG_START, distanceChunks * 12);
            GL11.glFogf(GL11.GL_FOG_END, distanceChunks * 16);
        }
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        if (!renderedThisFrame && runtime != null && failure == null)
            fail(new IllegalStateException("The Voxy handoff before translucent terrain did not run"));
    }

    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent event) {
        ShaderHooks.partialTick = event.renderTickTime;
        if (event.phase == TickEvent.Phase.START) renderedThisFrame = false;
    }

    public static void beforeTerrainPass(int pass, double partialTicks) {
        if (ShaderHooks.activePipeline() != null) return;
        if (pass == 0) INSTANCE.renderedThisFrame = false;
        if (pass == 1 && !INSTANCE.renderedThisFrame) {
            INSTANCE.renderedThisFrame = true;
            INSTANCE.drawTerrain((float) partialTicks);
        }
    }

    public static void beforeShaderDeferred() {
        if (ShaderHooks.shadowPass()) return;
        ModernTerrain terrain = INSTANCE;
        terrain.renderedThisFrame = true;
        if (terrain.enabled && terrain.failure == null && terrain.world != null) try {
            if (terrain.shaderPipeline != ShaderHooks.activePipeline()) {
                terrain.stop(true);
                terrain.start();
            }
            terrain.drawTerrain(ShaderHooks.partialTick);
        } catch (Exception | LinkageError error) {
            terrain.fail(error);
        }
    }

    public static void prepareShaderFrame() {
        // Angelica updates shader uniforms immediately after this hook. Publish this frame's
        // Voxy projection first so reconstruction/TAA never use the previous camera transform.
        if (!ShaderHooks.shadowPass()) INSTANCE.drawTerrain(ShaderHooks.partialTick, true);
    }

    private void drawTerrain(float partialTicks) {
        drawTerrain(partialTicks, false);
    }

    private void drawTerrain(float partialTicks, boolean prepareOnly) {
        if (runtime == null || failure != null || !enabled) return;
        try {
            // Shader menus pause client ticks while continuing to render. Angelica can delete
            // the old targets immediately, so neither preparation nor drawing may use them.
            if (shaderPipeline != ShaderHooks.activePipeline()) {
                stop(true);
                start();
            }
            EntityLivingBase camera = mc.renderViewEntity;
            if (camera == null) return;
            if (ShaderHooks.activePipeline() == null && LegacyTerrainRenderer.hasEnvironmentalFog(camera)) return;
            matrixBuffer.clear();
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, matrixBuffer);
            matrixBuffer.get(projection);
            matrixBuffer.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrixBuffer);
            matrixBuffer.get(modelView);
            float p = partialTicks;
            (prepareOnly ? prepare : draw).invoke(
                runtime,
                projection,
                modelView,
                camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * p,
                camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * p,
                camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * p,
                mc.gameSettings.renderDistanceChunks,
                p);
        } catch (Exception | LinkageError error) {
            fail(error);
        }
    }

    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Text event) {
        if (!enabled || (!hud && !mc.gameSettings.showDebugInfo)) return;
        event.left.add(status());
        if (runtime != null && failure == null && mc.gameSettings.showDebugInfo) try {
            event.left.add((String) debug.invoke(runtime));
        } catch (Exception error) {
            LOG.debug("Voxy debug unavailable", error);
        }
    }

    private void fail(Throwable error) {
        net.rasanovum.roxy.legacy.loader.SupportReport.failure(error);
        failure = LoadDiagnostics.describe(error);
        LOG.error("Original Voxy renderer disabled", error);
        stop();
        if (mc.thePlayer != null)
            mc.thePlayer.addChatMessage(new ChatComponentText("Voxy modern renderer disabled: " + failure));
    }

    private void stop() {
        stop(false);
    }

    private void stop(boolean preserveObservations) {
        if (world != null) world.removeWorldAccess(changes);
        try {
            if (runtime != null && close != null) close.invoke(runtime);
        } catch (Exception error) {
            LOG.error("Voxy shutdown failed", error);
        }
        runtime = null;
        world = null;
        states.clear();
        unsafeProperties.clear();
        biomes.clear();
        if (!preserveObservations) {
            observedWorld = null;
            queue.clear();
            captured.clear();
            synchronized (dirty) {
                dirty.clear();
            }
            sections = 0;
            ticks = 0;
        }
        if (loader != null && System.getProperty("roxy.developmentBridgeJar") != null) {
            try {
                loader.close();
            } catch (Exception error) {
                LOG.warn("Voxy loader close failed", error);
            }
            loader = null;
        }
    }

    public void onResourceManagerReload(IResourceManager manager) {
        stop(world != null && world == mc.theWorld);
        LegacyModelInputs.clear();
    }

    @SubscribeEvent
    public void unload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        if (event.world == world || event.world == observedWorld) stop();
    }
}
