package com.andymods.murimcultivation.client.render;

import com.andymods.murimcultivation.npc.MartialArtistEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renders a martial artist as a humanoid.
 *
 * <p>Reuses the vanilla player model and skin rather than shipping a model and texture. A
 * custom model is exactly the kind of thing that belongs in the M6 art pass — building one now
 * would block a working NPC on assets nobody has drawn yet, and the mod would look no better for
 * it in the meantime.
 */
@OnlyIn(Dist.CLIENT)
public class MartialArtistRenderer extends HumanoidMobRenderer<MartialArtistEntity, HumanoidModel<MartialArtistEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public MartialArtistRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        // Armour layers, so the sect robes an artist spawns in (dyed leather) actually show.
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(MartialArtistEntity entity) {
        return TEXTURE;
    }
}
