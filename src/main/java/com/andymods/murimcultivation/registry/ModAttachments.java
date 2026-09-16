package com.andymods.murimcultivation.registry;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.cultivation.CultivationData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Data attachments. This replaces the capability system used before 1.21.
 *
 * <p>{@code copyOnDeath()} is doing real work here: it is what makes a player's realm,
 * meridians and techniques survive dying, without a hand-written clone handler that has to
 * remember every field.
 */
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MurimCultivationMod.MODID);

    public static final Supplier<AttachmentType<CultivationData>> CULTIVATION =
            ATTACHMENT_TYPES.register("cultivation", () -> AttachmentType
                    .builder(CultivationData::new)
                    .serialize(CultivationData.CODEC)
                    .copyOnDeath()
                    .build());

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }

    private ModAttachments() {
    }
}
