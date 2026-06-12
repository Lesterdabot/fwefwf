package com.hunted.mod.event;

import com.hunted.mod.HuntedMod;
import com.hunted.mod.config.HuntedConfig;
import com.hunted.mod.item.HuntedItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

public class HuntedEventManager {

    public enum Phase { IDLE, PREP, ACTIVE }

    // ── State ──────────────────────────────────────────────────────────────
    private static Phase          phase               = Phase.IDLE;
    private static MinecraftServer server             = null;

    // prep
    private static int  prepTicksLeft     = 0;
    private static int  lastPrepAnnounced = -1;

    // chest
    private static BlockPos    chestPos   = null;
    private static ServerLevel chestLevel = null;

    // target
    private static UUID targetUUID         = null;
    private static int  broadcastTicksLeft = 0;

    // post-death new-target scan
    private static boolean scanningForNewTarget = false;
    private static int     scanCooldown         = 0;

    // delayed pickup check (after chest open)
    private static UUID pendingPickupUUID   = null;
    private static int  pickupCheckCountdown = 0;

    // ── Server lifecycle ───────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        server = e.getServer();
    }

    // ── Public API ─────────────────────────────────────────────────────────

    public static boolean startEvent() {
        if (phase != Phase.IDLE) return false;
        int secs = HuntedConfig.PREP_TIME_SECONDS.get();
        prepTicksLeft    = secs * 20;
        lastPrepAnnounced = secs;
        phase = Phase.PREP;
        broadcast(HuntedConfig.MSG_EVENT_START.get().replace("{time}", String.valueOf(secs)));
        HuntedMod.LOGGER.info("[Hunted] Event started — prep {}s", secs);
        return true;
    }

    public static Phase  getPhase()      { return phase; }
    public static String getTargetName() {
        if (server == null || targetUUID == null) return "none";
        ServerPlayer p = server.getPlayerList().getPlayer(targetUUID);
        return p != null ? p.getName().getString() : "none (offline)";
    }

    // ── Main tick ──────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        if (server == null) return;
        switch (phase) {
            case PREP   -> tickPrep();
            case ACTIVE -> tickActive();
            default     -> { }
        }
    }

    // ── PREP ───────────────────────────────────────────────────────────────

    private static void tickPrep() {
        prepTicksLeft--;
        int secsLeft = prepTicksLeft / 20;
        if (secsLeft != lastPrepAnnounced) {
            lastPrepAnnounced = secsLeft;
            if (secsLeft > 0 && (secsLeft % 10 == 0 || secsLeft <= 5))
                broadcast("§6[Hunted] §eCursed chest spawning in §c" + secsLeft + "s§e!");
        }
        if (prepTicksLeft <= 0) spawnChest();
    }

    // ── CHEST SPAWN ────────────────────────────────────────────────────────

    private static void spawnChest() {
        if (server == null) { reset(); return; }
        ServerLevel overworld = server.overworld();
        List<BlockPos> candidates = scanForChests(overworld, HuntedConfig.CHEST_SEARCH_RADIUS.get());
        if (candidates.isEmpty()) {
            broadcast("§c[Hunted] No chests found within " + HuntedConfig.CHEST_SEARCH_RADIUS.get()
                + " blocks of spawn! Place some chests and restart the event.");
            reset();
            return;
        }
        BlockPos chosen = candidates.get(new Random().nextInt(candidates.size()));
        chestPos   = chosen;
        chestLevel = overworld;
        fillChest(overworld, chosen);
        phase = Phase.ACTIVE;
        broadcastTicksLeft = HuntedConfig.BROADCAST_INTERVAL_SECONDS.get() * 20;
        broadcast(HuntedConfig.MSG_CHEST_SPAWNED.get()
            .replace("{x}", String.valueOf(chosen.getX()))
            .replace("{y}", String.valueOf(chosen.getY()))
            .replace("{z}", String.valueOf(chosen.getZ())));
        HuntedMod.LOGGER.info("[Hunted] Chest spawned at {}", chosen);
    }

    private static List<BlockPos> scanForChests(ServerLevel level, int radius) {
        List<BlockPos> found = new ArrayList<>();
        Random rand = new Random();
        int samples = Math.min(radius * radius / 16, 3000);
        for (int i = 0; i < samples; i++) {
            int x = rand.nextInt(radius * 2) - radius;
            int z = rand.nextInt(radius * 2) - radius;
            for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (level.getBlockState(pos).is(Blocks.CHEST) || level.getBlockState(pos).is(Blocks.TRAPPED_CHEST)) {
                    found.add(pos);
                    break;
                }
            }
        }
        return found;
    }

    private static void fillChest(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return;
        chest.clearContent();
        // Cursed relic in center slot
        chest.setItem(13, new ItemStack(HuntedItems.CURSED_RELIC.get(), 1));
        // Bonus loot
        int slot = 0;
        for (String entry : HuntedConfig.CHEST_LOOT.get()) {
            if (slot == 13) slot++;
            if (slot >= chest.getContainerSize()) break;
            String[] parts = entry.trim().split(" ");
            int count = parts.length > 1 ? parseSafe(parts[1], 1) : 1;
            var item = net.neoforged.neoforge.registries.ForgeRegistries.ITEMS
                .getValue(ResourceLocation.tryParse(parts[0]));
            if (item != null) chest.setItem(slot, new ItemStack(item, count));
            slot++;
        }
        chest.setChanged();
    }

    // ── ACTIVE ─────────────────────────────────────────────────────────────

    private static void tickActive() {
        // Delayed pickup check (after player opens the chest)
        if (pendingPickupUUID != null) {
            pickupCheckCountdown--;
            if (pickupCheckCountdown <= 0) {
                checkPendingPickup();
            }
        }

        // Post-death scan: look for anyone who picked up the dropped relic
        if (scanningForNewTarget) {
            scanCooldown--;
            if (scanCooldown <= 0) {
                scanCooldown = 20;
                doNewTargetScan();
            }
            return; // don't broadcast coords while waiting for new target
        }

        if (targetUUID == null) return;

        // Broadcast coords on interval
        broadcastTicksLeft--;
        if (broadcastTicksLeft <= 0) {
            broadcastTargetCoords();
            broadcastTicksLeft = HuntedConfig.BROADCAST_INTERVAL_SECONDS.get() * 20;
        }

        // Sanity: verify target still holds the relic
        ServerPlayer target = server.getPlayerList().getPlayer(targetUUID);
        if (target != null && !playerHasRelic(target)) {
            broadcast(HuntedConfig.MSG_EVENT_END.get());
            reset();
        }
    }

    private static void checkPendingPickup() {
        ServerPlayer player = server.getPlayerList().getPlayer(pendingPickupUUID);
        pendingPickupUUID    = null;
        pickupCheckCountdown = 0;
        if (player == null) return;
        if (targetUUID == null && playerHasRelic(player)) {
            setTarget(player);
        }
    }

    private static void doNewTargetScan() {
        // Check if any online player now holds the relic
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (playerHasRelic(p)) {
                scanningForNewTarget = false;
                setTarget(p);
                return;
            }
        }
        // Check if relic is still a dropped item entity
        boolean relicExists = false;
        for (ServerLevel level : server.getAllLevels()) {
            if (!level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(-30000, -64, -30000, 30000, 320, 30000),
                    ie -> ie.getItem().is(HuntedItems.CURSED_RELIC.get())).isEmpty()) {
                relicExists = true;
                break;
            }
        }
        if (!relicExists) {
            broadcast(HuntedConfig.MSG_EVENT_END.get());
            reset();
        }
    }

    private static void broadcastTargetCoords() {
        ServerPlayer target = server.getPlayerList().getPlayer(targetUUID);
        if (target == null) return;
        int tx = (int) target.getX(), ty = (int) target.getY(), tz = (int) target.getZ();

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer.getUUID().equals(targetUUID)) {
                viewer.sendSystemMessage(Component.literal(
                    "§6[Hunted] §c⚠ You are the target! Everyone sees your position every "
                    + HuntedConfig.BROADCAST_INTERVAL_SECONDS.get() + "s!"));
                continue;
            }
            double dx = tx - viewer.getX();
            double dz = tz - viewer.getZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            String dir = getCardinalDirection(dx, dz);
            String msg = HuntedConfig.MSG_COORDS_BROADCAST.get()
                .replace("{player}", target.getName().getString())
                .replace("{x}", String.valueOf(tx))
                .replace("{y}", String.valueOf(ty))
                .replace("{z}", String.valueOf(tz))
                .replace("{dir}", dir)
                .replace("{dist}", String.valueOf(dist));
            viewer.sendSystemMessage(Component.literal(msg));
        }
    }

    // ── Events ─────────────────────────────────────────────────────────────

    /** Detect when the event chest is right-clicked → schedule relic pickup check */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (phase != Phase.ACTIVE) return;
        if (chestPos == null || targetUUID != null) return; // relic already taken
        if (!e.getPos().equals(chestPos)) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        // Check after 1 second — enough time for the player to take the item
        pendingPickupUUID    = player.getUUID();
        pickupCheckCountdown = 20;
    }

    /** Detect target death */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent e) {
        if (phase != Phase.ACTIVE) return;
        if (targetUUID == null) return;
        if (!(e.getEntity() instanceof ServerPlayer dead)) return;
        if (!dead.getUUID().equals(targetUUID)) return;

        String killerName = "the environment";
        if (e.getSource().getEntity() instanceof ServerPlayer killer)
            killerName = killer.getName().getString();

        broadcast(HuntedConfig.MSG_TARGET_KILLED.get()
            .replace("{killer}", killerName)
            .replace("{target}", dead.getName().getString()));

        // Clear target and start scanning for whoever picks up the dropped relic
        targetUUID           = null;
        scanningForNewTarget = true;
        scanCooldown         = 20;
        HuntedMod.LOGGER.info("[Hunted] {} eliminated by {}", dead.getName().getString(), killerName);
    }

    /** Protect the event chest until relic is claimed */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent e) {
        if (phase != Phase.ACTIVE) return;
        if (chestPos == null || targetUUID != null) return; // already taken
        if (!e.getPos().equals(chestPos)) return;
        if (e.getPlayer() instanceof ServerPlayer p)
            p.sendSystemMessage(Component.literal("§c[Hunted] This chest is protected until the relic is claimed!"));
        e.setCanceled(true);
    }

    // ── Utilities ──────────────────────────────────────────────────────────

    private static boolean playerHasRelic(ServerPlayer p) {
        return p.getInventory().items.stream().anyMatch(s -> s.is(HuntedItems.CURSED_RELIC.get()))
            || p.getInventory().offhand.stream().anyMatch(s -> s.is(HuntedItems.CURSED_RELIC.get()));
    }

    private static void setTarget(ServerPlayer player) {
        targetUUID         = player.getUUID();
        broadcastTicksLeft = 3 * 20; // first broadcast 3s after becoming target
        broadcast(HuntedConfig.MSG_TARGET_ACQUIRED.get()
            .replace("{player}", player.getName().getString()));
        HuntedMod.LOGGER.info("[Hunted] New target: {}", player.getName().getString());
    }

    private static String getCardinalDirection(double dx, double dz) {
        // Minecraft: +X=East, +Z=South
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0) angle += 360;
        if (angle >= 337.5 || angle < 22.5) return "East →";
        if (angle < 67.5)  return "SE ↘";
        if (angle < 112.5) return "South ↓";
        if (angle < 157.5) return "SW ↙";
        if (angle < 202.5) return "West ←";
        if (angle < 247.5) return "NW ↖";
        if (angle < 292.5) return "North ↑";
        return "NE ↗";
    }

    private static void broadcast(String msg) {
        if (server == null) return;
        server.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
    }

    private static void reset() {
        phase                = Phase.IDLE;
        prepTicksLeft        = 0;
        lastPrepAnnounced    = -1;
        chestPos             = null;
        chestLevel           = null;
        targetUUID           = null;
        broadcastTicksLeft   = 0;
        scanningForNewTarget = false;
        scanCooldown         = 0;
        pendingPickupUUID    = null;
        pickupCheckCountdown = 0;
        HuntedMod.LOGGER.info("[Hunted] Reset to IDLE.");
    }

    private static int parseSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException ex) { return def; }
    }
}
