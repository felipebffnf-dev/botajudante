package com.botajudante;

import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

public class HelperRenderer extends BipedEntityRenderer<HelperEntity, PlayerEntityModel<HelperEntity>> {
    private static final Identifier TEXTURE =
            new Identifier("textures/entity/player/wide/steve.png");

    public HelperRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public Identifier getTexture(HelperEntity entity) {
        return TEXTURE;
    }
}
