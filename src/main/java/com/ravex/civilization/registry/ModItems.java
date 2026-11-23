package com.ravex.civilization.registry;

import com.ravex.civilization.RavexCiv;
import com.ravex.civilization.npc.NpcSpawnEggItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RavexCiv.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RavexCiv.MOD_ID);

    public static final DeferredItem<NpcSpawnEggItem> YASSINE_EGG =
            ITEMS.register("yassine_spawn_egg", () -> new NpcSpawnEggItem(true, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<NpcSpawnEggItem> RANDOM_EGG =
            ITEMS.register("random_npc_spawn_egg", () -> new NpcSpawnEggItem(false, new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ravex_civ"))
                    .icon(() -> new ItemStack(YASSINE_EGG.get()))
                    .displayItems((params, out) -> {
                        out.accept(YASSINE_EGG.get());
                        out.accept(RANDOM_EGG.get());
                    })
                    .build());

    private ModItems() {}
}
