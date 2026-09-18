package com.andymods.murimcultivation.cultivation;

import com.andymods.murimcultivation.network.SyncCultivationPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The loadout selection, and the fact that it reaches the client.
 *
 * <p>This exists because of a bug the 188 tests before it could not have caught. The selection
 * was tracked correctly on the server and never sent anywhere: it is transient, so it is not in
 * the persistence codec, and {@code copyFrom} does not carry it either. The client's copy
 * therefore sat at zero forever, the loadout bar's highlight never moved, and the cycle key read
 * as doing nothing at all.
 *
 * <p>So the round-trip below is the point of the file. The arithmetic tests are cheap company.
 */
class TechniqueSelectionTest {

    private static final ResourceLocation SWORD_QI =
            ResourceLocation.fromNamespaceAndPath("murimcultivation", "sword_qi");
    private static final ResourceLocation DIVINE_PALM =
            ResourceLocation.fromNamespaceAndPath("murimcultivation", "divine_palm");
    private static final ResourceLocation QINGGONG =
            ResourceLocation.fromNamespaceAndPath("murimcultivation", "qinggong");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Three arts learned and bound. {@code setLoadout} keeps only arts the player actually knows,
     * so the mastery entries are what make the loadout non-empty, not decoration.
     */
    private static CultivationData withThreeBound() {
        CultivationData data = new CultivationData();
        data.setTechniqueMastery(SWORD_QI, 10);
        data.setTechniqueMastery(DIVINE_PALM, 20);
        data.setTechniqueMastery(QINGGONG, 30);
        data.setLoadout(List.of(SWORD_QI, DIVINE_PALM, QINGGONG));
        return data;
    }

    @Test
    void cyclingWrapsOverTheOccupiedSlots() {
        CultivationData data = withThreeBound();
        assertEquals(3, data.loadout().size());

        assertEquals(1, data.cycleSelectedSlot());
        assertEquals(2, data.cycleSelectedSlot());
        assertEquals(0, data.cycleSelectedSlot(), "the cycle wraps at the end of the loadout");
    }

    @Test
    void cyclingAnEmptyLoadoutStaysAtZero() {
        CultivationData data = new CultivationData();
        assertEquals(0, data.cycleSelectedSlot());
        assertEquals(0, data.cycleSelectedSlot());
    }

    @Test
    void settingTheSelectionClampsToARealSlot() {
        CultivationData data = new CultivationData();

        data.setSelectedSlot(2);
        assertEquals(2, data.selectedSlot());

        // A hostile or simply stale value must never index outside the loadout array.
        data.setSelectedSlot(-5);
        assertEquals(0, data.selectedSlot());

        data.setSelectedSlot(99);
        assertEquals(CultivationData.LOADOUT_SIZE - 1, data.selectedSlot());
    }

    @Test
    void theSyncPayloadCarriesTheSelection() {
        CultivationData data = withThreeBound();
        data.setAwakened(true);
        data.cycleSelectedSlot();
        data.cycleSelectedSlot();
        assertEquals(2, data.selectedSlot());

        SyncCultivationPayload restored = roundTrip(new SyncCultivationPayload(data, true, data.selectedSlot()));

        assertEquals(2, restored.selectedSlot(), "the selection has to survive the wire");
        assertTrue(restored.meditating());
        assertEquals(data.loadout(), restored.data().loadout());
        // Transient by construction: the codec does not carry it, which is precisely why the
        // payload has to carry it separately.
        assertEquals(0, restored.data().selectedSlot());
    }

    private static SyncCultivationPayload roundTrip(SyncCultivationPayload payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        SyncCultivationPayload.STREAM_CODEC.encode(buffer, payload);
        return SyncCultivationPayload.STREAM_CODEC.decode(buffer);
    }
}
