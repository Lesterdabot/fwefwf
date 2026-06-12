package com.hunted.mod;

import com.hunted.mod.command.HuntedCommand;
import com.hunted.mod.config.HuntedConfig;
import com.hunted.mod.event.HuntedEventManager;
import com.hunted.mod.item.HuntedItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(HuntedMod.MODID)
public class HuntedMod {

    public static final String MODID = "hunted";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HuntedMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the cursed relic item
        HuntedItems.register(modEventBus);

        // Register server config
        modContainer.registerConfig(ModConfig.Type.SERVER, HuntedConfig.SPEC);

        // Register server-side event listeners on the NeoForge bus
        NeoForge.EVENT_BUS.register(HuntedEventManager.class);
        NeoForge.EVENT_BUS.register(HuntedCommand.class);

        LOGGER.info("[Hunted] Mod loaded.");
    }
}
