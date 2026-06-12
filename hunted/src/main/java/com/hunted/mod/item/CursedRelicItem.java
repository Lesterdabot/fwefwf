package com.hunted.mod.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.List;

public class CursedRelicItem extends Item {

    public static final String REGISTRY_NAME = "cursed_relic";

    public CursedRelicItem() {
        super(new Item.Properties()
            .stacksTo(1)
            .rarity(Rarity.EPIC)
            .fireResistant()
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§cYou are being hunted."));
        tooltip.add(Component.literal("§7Drop it or die — the hunt never ends."));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Makes the item glow with enchant effect
        return true;
    }
}
