package com.botajudante;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class BotAjudante implements ModInitializer {
    public static final String MOD_ID = "botajudante";

    public static final EntityType<HelperEntity> HELPER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MOD_ID, "helper"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, HelperEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                    .trackRangeBlocks(64)
                    .build()
    );

    public static final Item HELPER_SPAWN_EGG = new SpawnEggItem(
            HELPER, 0x3B8EC9, 0xF2C14E, new Item.Settings()
    );

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(HELPER, HelperEntity.createHelperAttributes());

        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "helper_spawn_egg"), HELPER_SPAWN_EGG);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS)
                .register(entries -> entries.add(HELPER_SPAWN_EGG));

        ServerMessageEvents.CHAT_MESSAGE.register(BotChat::onChat);
    }
}
