package com.hunted.mod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class HuntedConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue    PREP_TIME_SECONDS;
    public static final ModConfigSpec.IntValue    BROADCAST_INTERVAL_SECONDS;
    public static final ModConfigSpec.IntValue    CHEST_SEARCH_RADIUS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CHEST_LOOT;
    public static final ModConfigSpec.ConfigValue<String> MSG_EVENT_START;
    public static final ModConfigSpec.ConfigValue<String> MSG_CHEST_SPAWNED;
    public static final ModConfigSpec.ConfigValue<String> MSG_TARGET_ACQUIRED;
    public static final ModConfigSpec.ConfigValue<String> MSG_COORDS_BROADCAST;
    public static final ModConfigSpec.ConfigValue<String> MSG_TARGET_KILLED;
    public static final ModConfigSpec.ConfigValue<String> MSG_EVENT_END;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("=== Hunted Event Config ===").push("timing");

        PREP_TIME_SECONDS = b
            .comment("Countdown in seconds before the cursed item chest spawns.")
            .defineInRange("prepTimeSeconds", 60, 10, 600);

        BROADCAST_INTERVAL_SECONDS = b
            .comment("How often (seconds) the target's coords are broadcast to everyone.")
            .defineInRange("broadcastIntervalSeconds", 15, 5, 120);

        b.pop().push("world");

        CHEST_SEARCH_RADIUS = b
            .comment("Radius (blocks) from world origin to search for an existing chest.")
            .defineInRange("chestSearchRadius", 500, 50, 5000);

        b.pop().push("loot");

        CHEST_LOOT = b
            .comment(
                "Items placed in the event chest alongside the cursed item.",
                "Format: 'modid:item_id count'  e.g. 'minecraft:diamond 5'"
            )
            .defineListAllowEmpty("chestLoot",
                List.of("minecraft:diamond 3", "minecraft:golden_apple 2"),
                e -> e instanceof String);

        b.pop().push("messages");

        MSG_EVENT_START = b
            .comment("Broadcast when event kicks off. {time} = prep seconds.")
            .define("eventStart", "§6[Hunted] §eAn event starts in §c{time}s§e! A cursed item will spawn — claim it or hunt whoever does!");

        MSG_CHEST_SPAWNED = b
            .comment("Broadcast when chest spawns. {x} {y} {z} = coords.")
            .define("chestSpawned", "§6[Hunted] §aThe cursed chest has spawned at §f{x}, {y}, {z}§a! First to grab it becomes the target!");

        MSG_TARGET_ACQUIRED = b
            .comment("Broadcast when a player picks up the cursed item. {player} = name.")
            .define("targetAcquired", "§6[Hunted] §c{player} §ehas picked up the cursed item and is now §cTHE TARGET§e!");

        MSG_COORDS_BROADCAST = b
            .comment("Repeated broadcast showing target location. {player} {x} {y} {z} {dir} {dist} available.")
            .define("coordsBroadcast", "§6[Hunted] §cTARGET §f{player} §7— §e{x}, {y}, {z} §7({dir} §7| §e{dist}m away§7)");

        MSG_TARGET_KILLED = b
            .comment("Broadcast when target is killed. {killer} {target} available.")
            .define("targetKilled", "§6[Hunted] §b{killer} §eeliminated §c{target}§e! The cursed item dropped — grab it to become the next target!");

        MSG_EVENT_END = b
            .comment("Broadcast when the cursed item is destroyed / event ends with no target.")
            .define("eventEnd", "§6[Hunted] §7The cursed item has been lost. The hunt is over.");

        b.pop();
        SPEC = b.build();
    }
}
