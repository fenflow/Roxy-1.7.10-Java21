package net.rasanovum.roxy.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.chunk.Chunk;
import net.rasanovum.roxy.legacy.bridge.LegacyChunkCapture;
import net.rasanovum.roxy.legacy.bridge.LegacyPalette;
import net.rasanovum.roxy.legacy.loader.LoadDiagnostics;
import net.rasanovum.roxy.legacy.loader.ModernEngineProbe;
import net.rasanovum.roxy.legacy.loader.VoxyJarLocator;
import net.rasanovum.roxy.legacy.loader.VoxyRuntime;
import net.rasanovum.roxy.legacy.terrain.ModernTerrain;

/** Client-local rendering controls and an explicit external binary diagnostic. */
public final class RoxyProbeCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "roxy";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/roxy status | report | on | off | hud | distance <8-128> | reload | probe";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) {
        if (arguments.length == 1 && "report".equals(arguments[0])) {
            try {
                net.rasanovum.roxy.legacy.loader.SupportReport.write(
                    Minecraft.getMinecraft().mcDataDir.toPath(),
                    ModernTerrain.INSTANCE.isEnabled(),
                    ModernTerrain.INSTANCE.distance());
                sender.addChatMessage(
                    new ChatComponentText("Support report saved: roxy/support-report.txt. Review before sharing."));
                sender.addChatMessage(
                    new ChatComponentText(
                        "Attach it and reviewed logs to your Roxy issue; include GTNH version or your mod list."));
            } catch (Exception error) {
                sender.addChatMessage(
                    new ChatComponentText(
                        "Could not save support report: " + error.getClass()
                            .getSimpleName()));
            }
            return;
        }
        if (Boolean.getBoolean("roxy.rendererDiagnostics") && arguments.length == 3
            && "testview".equals(arguments[0])) {
            net.minecraft.entity.player.EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            player.rotationYaw = player.prevRotationYaw = Float.parseFloat(arguments[1]);
            player.rotationPitch = player.prevRotationPitch = Float.parseFloat(arguments[2]);
            return;
        }
        if (arguments.length == 1 && ("modern".equals(arguments[0]) || "modernstatus".equals(arguments[0]))) {
            net.rasanovum.roxy.legacy.terrain.ModernTerrain terrain = net.rasanovum.roxy.legacy.terrain.ModernTerrain.INSTANCE;
            sender.addChatMessage(
                new ChatComponentText("modern".equals(arguments[0]) ? terrain.toggle() : terrain.status()));
            return;
        }
        if (arguments.length == 1 && ("engineprobe".equals(arguments[0]) || "modelprobe".equals(arguments[0])
            || "rendererprobe".equals(arguments[0]))) {
            try {
                String result = "modelprobe".equals(arguments[0])
                    ? ModernEngineProbe.models(Minecraft.getMinecraft().mcDataDir.toPath())
                    : "rendererprobe".equals(arguments[0])
                        ? ModernEngineProbe.renderer(Minecraft.getMinecraft().mcDataDir.toPath())
                        : ModernEngineProbe.run(Minecraft.getMinecraft().mcDataDir.toPath());
                org.apache.logging.log4j.LogManager.getLogger("Roxy")
                    .info(result);
                sender.addChatMessage(new ChatComponentText(result));
            } catch (Exception | LinkageError error) {
                org.apache.logging.log4j.LogManager.getLogger("Roxy")
                    .error("Voxy engine diagnostic failed", error);
                sender.addChatMessage(
                    new ChatComponentText("Voxy engine diagnostic failed: " + LoadDiagnostics.describe(error)));
            }
            return;
        }
        ModernTerrain terrain = ModernTerrain.INSTANCE;
        if (arguments.length == 1) {
            if ("reload".equals(arguments[0])) {
                Minecraft.getMinecraft()
                    .refreshResources();
            } else if ("on".equals(arguments[0]) || "off".equals(arguments[0])) {
                terrain.setEnabled("on".equals(arguments[0]));
            } else if ("hud".equals(arguments[0])) {
                terrain.toggleHud();
            } else if (!"status".equals(arguments[0]) && !"probe".equals(arguments[0])) {
                sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
                return;
            }
            if (!"probe".equals(arguments[0])) {
                sender.addChatMessage(new ChatComponentText(terrain.status()));
                return;
            }
        } else if (arguments.length == 2 && "distance".equals(arguments[0])) {
            try {
                terrain.setDistance(Integer.parseInt(arguments[1]));
                sender.addChatMessage(new ChatComponentText(terrain.status()));
            } catch (IllegalArgumentException invalid) {
                sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            }
            return;
        }
        if (arguments.length != 1 || !"probe".equals(arguments[0])) {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            sender.addChatMessage(new ChatComponentText("Roxy: enter a world first."));
            return;
        }
        try (VoxyRuntime runtime = new VoxyRuntime(VoxyJarLocator.find(mc.mcDataDir.toPath()))) {
            int x = MathHelper.floor_double(mc.thePlayer.posX) >> 4;
            int z = MathHelper.floor_double(mc.thePlayer.posZ) >> 4;
            int y = Math.max(0, Math.min(15, MathHelper.floor_double(mc.thePlayer.posY) >> 4));
            Chunk chunk = mc.theWorld.getChunkFromChunkCoords(x, z);
            LegacyPalette palette = new LegacyPalette();
            long[] snapshot = LegacyChunkCapture.capture(mc.theWorld, chunk, y, palette);
            Object section = runtime.convert(x, y, z, snapshot, palette.snapshotMapper());
            sender.addChatMessage(
                new ChatComponentText(
                    "Roxy bridge passed: " + runtime.copyData(section).length
                        + " voxels across 5 LOD levels, "
                        + palette.size()
                        + " legacy states."));
        } catch (Exception | LinkageError error) {
            sender.addChatMessage(new ChatComponentText("Roxy probe failed: " + LoadDiagnostics.describe(error)));
            sender.addChatMessage(new ChatComponentText("Roxy runtime: " + LoadDiagnostics.environment()));
            org.apache.logging.log4j.LogManager.getLogger("Roxy")
                .error("Legacy Voxy bridge probe failed", error);
        }
    }
}
