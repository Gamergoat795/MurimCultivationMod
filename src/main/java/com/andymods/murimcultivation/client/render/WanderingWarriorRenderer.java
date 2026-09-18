package com.andymods.murimcultivation.client.render;

import com.andymods.murimcultivation.npc.WanderingWarriorEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renders a wandering warrior as a humanoid.
 *
 * <p>The vanilla player model and the Alex skin, deliberately rather than a mod texture: it reads
 * as a different person from the Alliance teacher without waiting on art nobody has drawn, and
 * telling warriors apart is already handled by the custom name, which states the tier and realm.
 * A model and per-tier textures belong in the art pass.
 *
 * <p>The armour layer is here for the same reason it is on the teacher — so a later pass can put a
 * sect uniform on one without touching the renderer.
 */
@OnlyIn(Dist.CLIENT)
public class WanderingWarriorRenderer
        extends HumanoidMobRenderer<WanderingWarriorEntity, HumanoidModel<WanderingWarriorEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/player/slim/alex.png");

    public WanderingWarriorRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM)), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(WanderingWarriorEntity entity) {
        return TEXTURE;
    }
}
