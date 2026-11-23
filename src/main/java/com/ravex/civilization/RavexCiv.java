package com.ravex.civilization;

import com.ravex.civilization.client.ClientSetup;
import com.ravex.civilization.npc.NpcEntity;
import com.ravex.civilization.registry.ModEntities;
import com.ravex.civilization.registry.ModItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(RavexCiv.MOD_ID)
public class RavexCiv {
    public static final String MOD_ID = "ravex_civ";
    public static final Logger LOGGER = LoggerFactory.getLogger("RAVEX-AI-Civilization");

    public RavexCiv(IEventBus modBus, ModContainer container) {
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        modBus.addListener(RavexCiv::onAttributes);
        container.registerConfig(ModConfig.Type.COMMON, RavexConfig.SPEC);
        RavexEvents.register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modBus, container);
        }
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.NPC.get(), NpcEntity.createAttributes().build());
    }
}
