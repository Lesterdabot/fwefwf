package com.hunted.mod.item;

import com.hunted.mod.HuntedMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class HuntedItems {

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, HuntedMod.MODID);

    public static final DeferredHolder<Item, CursedRelicItem> CURSED_RELIC =
        ITEMS.register(CursedRelicItem.REGISTRY_NAME, CursedRelicItem::new);

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
