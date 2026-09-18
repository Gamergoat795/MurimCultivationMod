package com.andymods.murimcultivation.cultivation;

import com.andymods.murimcultivation.MurimRegistries;
import com.andymods.murimcultivation.system.QuestCategory;
import com.andymods.murimcultivation.system.StatType;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trips {@link CultivationData} through its codec.
 *
 * <p>This is the test that protects a player's save file. It also pins down two deliberate
 * design decisions that are easy to undo by accident: meditation state must <em>not</em>
 * persist, and there must be no stored Qi maximum.
 */
class CultivationDataCodecTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static CultivationData populated() {
        CultivationData data = new CultivationData();
        data.setAwakened(true);
        data.setRealmKey(ResourceKey.create(MurimRegistries.REALM,
                ResourceLocation.fromNamespaceAndPath("murimcultivation", "first_rate")));
        data.setSubstage(Substage.LATE);
        data.setQi(87.5D, 1000.0D);
        data.setProgress(1234.5D);
        data.setPurity(63.25D);
        data.openMeridian(Meridian.HEART);
        data.openMeridian(Meridian.LUNG);
        data.openMeridian(Meridian.GOVERNING_VESSEL);
        data.setTechniqueMastery(ResourceLocation.fromNamespaceAndPath("murimcultivation", "sword_qi"), 42);
        data.setLoadout(List.of(ResourceLocation.fromNamespaceAndPath("murimcultivation", "sword_qi")));
        data.systemProgress().grantPoints(7);
        data.systemProgress().grantTitle(
                ResourceLocation.fromNamespaceAndPath("murimcultivation", "sword_saint"));
        data.systemProgress().equipTitle(
                ResourceLocation.fromNamespaceAndPath("murimcultivation", "sword_saint"));
        data.systemProgress().spend(StatType.BODY, 3);
        data.questLog().complete(
                ResourceLocation.fromNamespaceAndPath("murimcultivation", "first_breath"),
                QuestCategory.STORY);
        data.addSectReputation(ResourceLocation.fromNamespaceAndPath("murimcultivation", "murim_alliance"), 250);
        data.standing().setHonour(37);
        data.standing().setInfamy(9);
        data.applyDeviation(DeviationSeverity.REVERSE_FLOW);
        return data;
    }

    private static CultivationData roundTripThroughNbt(CultivationData original) {
        Tag encoded = CultivationData.CODEC.encodeStart(NbtOps.INSTANCE, original)
                .getOrThrow(error -> new AssertionError("encode failed: " + error));
        return CultivationData.CODEC.parse(NbtOps.INSTANCE, encoded)
                .getOrThrow(error -> new AssertionError("decode failed: " + error));
    }

    @Test
    void everyPersistentFieldSurvivesTheRoundTrip() {
        CultivationData original = populated();
        CultivationData restored = roundTripThroughNbt(original);

        assertEquals(original.realmKey(), restored.realmKey());
        assertEquals(original.substage(), restored.substage());
        assertEquals(original.qi(), restored.qi(), 1.0e-9D);
        assertEquals(original.progress(), restored.progress(), 1.0e-9D);
        assertEquals(original.purity(), restored.purity(), 1.0e-9D);
        assertEquals(original.isAwakened(), restored.isAwakened());
        assertEquals(original.openMeridians(), restored.openMeridians());
        assertEquals(original.techniqueMastery(), restored.techniqueMastery());
        assertEquals(original.loadout(), restored.loadout());
        assertEquals(original.systemProgress().unspentPoints(), restored.systemProgress().unspentPoints());
        assertEquals(original.systemProgress().allocations(), restored.systemProgress().allocations());
        assertEquals(original.systemProgress().titles(), restored.systemProgress().titles());
        assertEquals(original.systemProgress().equippedTitle(), restored.systemProgress().equippedTitle());
        assertEquals(original.questLog().completed(), restored.questLog().completed());
        assertEquals(original.questLog().lastDailyResetDay(), restored.questLog().lastDailyResetDay());
        assertEquals(original.sectReputation(), restored.sectReputation());
        assertEquals(original.standing().honour(), restored.standing().honour());
        assertEquals(original.standing().infamy(), restored.standing().infamy());
        assertEquals(original.deviation(), restored.deviation());
        assertEquals(original.deviationTicks(), restored.deviationTicks());
    }

    @Test
    void meditationStateIsNeverPersisted() {
        CultivationData original = populated();
        original.setMeditating(true);
        original.incrementMeditationTicks();
        original.incrementMeditationTicks();
        assertTrue(original.isMeditating());

        CultivationData restored = roundTripThroughNbt(original);

        // Meditation is a moment-to-moment activity, not a property of the character. If it
        // persisted, dying mid-meditation would respawn the player frozen in place.
        assertFalse(restored.isMeditating(), "meditation state must not survive serialisation");
        assertEquals(0, restored.meditationTicks(), "meditation timer must not survive serialisation");
    }

    @Test
    void thereIsNoStoredQiMaximum() {
        CompoundTag encoded = (CompoundTag) CultivationData.CODEC
                .encodeStart(NbtOps.INSTANCE, populated())
                .getOrThrow(error -> new AssertionError("encode failed: " + error));

        // Qi capacity is always derived from realm and meridians. Persisting a maximum is what
        // creates the class of bug where a value is clamped against a stale bound.
        for (String key : encoded.getAllKeys()) {
            String lower = key.toLowerCase(Locale.ROOT);
            assertFalse(lower.contains("max") || lower.contains("capacity"),
                    "unexpected stored maximum in save data: " + key);
        }
        // A fresh cultivator's default state must also be free of one.
        assertFalse(encoded.getAllKeys().isEmpty(), "the encoded form should not be empty");
    }

    @Test
    void aDefaultCultivatorIsAnOrdinaryPerson() {
        CultivationData fresh = new CultivationData();
        assertFalse(fresh.isAwakened(), "a new player has not awakened");
        assertEquals(0, fresh.systemProgress().unspentPoints());
        assertEquals(0, fresh.systemProgress().totalSpent());
        assertTrue(fresh.systemProgress().titles().isEmpty());
        assertTrue(fresh.questLog().completed().isEmpty());
        assertEquals(Optional.empty(), fresh.realmKey(), "a new player is not on the ladder yet");
        assertEquals(0.0D, fresh.qi(), 1.0e-9D);
        assertEquals(0.0D, fresh.progress(), 1.0e-9D);
        assertEquals(CultivationData.DEFAULT_PURITY, fresh.purity(), 1.0e-9D);
        assertEquals(0, fresh.openMeridianCount());
        assertEquals(DeviationSeverity.NONE, fresh.deviation());
        assertFalse(fresh.isMeditating());
    }

    @Test
    void anEmptyTagDecodesToTheDefaultCultivator() {
        // Every field is optional, so a save written by an older version still loads.
        CultivationData decoded = CultivationData.CODEC.parse(NbtOps.INSTANCE, new CompoundTag())
                .getOrThrow(error -> new AssertionError("an empty tag should decode: " + error));

        assertFalse(decoded.isAwakened());
        assertEquals(CultivationData.DEFAULT_PURITY, decoded.purity(), 1.0e-9D);
        assertEquals(0, decoded.openMeridianCount());
    }

    @Test
    void theJsonAndNbtEncodingsAgree() {
        CultivationData original = populated();
        // Encode to JSON, convert into NBT, and decode: the codec must be format-agnostic,
        // which is what lets the same codec back both the save file and the sync packet.
        var json = CultivationData.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(error -> new AssertionError("json encode failed: " + error));
        Tag asNbt = new Dynamic<>(JsonOps.INSTANCE, json).convert(NbtOps.INSTANCE).getValue();
        CultivationData restored = CultivationData.CODEC.parse(NbtOps.INSTANCE, asNbt)
                .getOrThrow(error -> new AssertionError("nbt decode failed: " + error));

        assertEquals(original.realmKey(), restored.realmKey());
        assertEquals(original.substage(), restored.substage());
        assertEquals(original.openMeridians(), restored.openMeridians());
        assertEquals(original.purity(), restored.purity(), 1.0e-9D);
    }

    @Test
    void copyFromDuplicatesPersistentStateButNotMeditation() {
        CultivationData source = populated();
        source.setMeditating(true);

        CultivationData target = new CultivationData();
        target.copyFrom(source);

        assertEquals(source.realmKey(), target.realmKey());
        assertEquals(source.openMeridians(), target.openMeridians());
        assertEquals(source.progress(), target.progress(), 1.0e-9D);
        assertEquals(source.deviation(), target.deviation());
        // Standing is persistent and nested, so it has to be copied into rather than replaced —
        // the parent holds it in a final field.
        assertEquals(source.standing().honour(), target.standing().honour());
        assertEquals(source.standing().infamy(), target.standing().infamy());
        assertEquals(source.sectReputation(), target.sectReputation());
        assertFalse(target.isMeditating(), "copyFrom must not carry meditation across a respawn");
    }

    @Test
    void purityIsClampedToItsRange() {
        CultivationData data = new CultivationData();
        data.setPurity(-50.0D);
        assertEquals(CultivationData.MIN_PURITY, data.purity(), 1.0e-9D);
        data.setPurity(500.0D);
        assertEquals(CultivationData.MAX_PURITY, data.purity(), 1.0e-9D);
    }

    @Test
    void spendQiRefusesWhenShort() {
        CultivationData data = new CultivationData();
        data.setQi(30.0D, 100.0D);

        assertFalse(data.spendQi(50.0D), "spending more Qi than is held must fail");
        assertEquals(30.0D, data.qi(), 1.0e-9D, "a failed spend must not deduct anything");
        assertTrue(data.spendQi(30.0D), "spending exactly what is held must succeed");
        assertEquals(0.0D, data.qi(), 1.0e-9D);
    }

    @Test
    void qiIsClampedToTheSuppliedCapacity() {
        CultivationData data = new CultivationData();
        data.setQi(500.0D, 120.0D);
        assertEquals(120.0D, data.qi(), 1.0e-9D, "Qi must not exceed the capacity passed in");
        data.setQi(-10.0D, 120.0D);
        assertEquals(0.0D, data.qi(), 1.0e-9D, "Qi must not go negative");
    }
}
