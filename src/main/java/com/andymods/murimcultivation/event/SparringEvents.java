package com.andymods.murimcultivation.event;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.npc.MartialArtistEntity;
import com.andymods.murimcultivation.npc.Sparring;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Keeps a spar a spar: nobody is killed by their sparring partner, and whoever is beaten
 * yields instead of fighting on.
 *
 * <p>Two events because the two jobs need different moments. Capping a blow has to happen
 * before it lands; deciding who has been beaten has to happen after, against the health that
 * is actually left once armour has had its say.
 */
@EventBusSubscriber(modid = MurimCultivationMod.MODID)
public final class SparringEvents {

    /**
     * Starts or sustains a spar, and caps the blow so it cannot take the last point of health.
     *
     * <p>High priority so the cap is in place before anything else reads the amount — the
     * near-death enlightenment roll in {@link MeditationInterruptEvents} included, which would
     * otherwise see an uncapped killing blow.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();

        if (target instanceof MartialArtistEntity artist && attacker instanceof ServerPlayer player) {
            artist.onStruckBy(player);
            if (artist.isSparringWith(player)) {
                event.setAmount(Sparring.protect(artist.getHealth(), event.getAmount()));
            }
        } else if (target instanceof ServerPlayer player && attacker instanceof MartialArtistEntity artist
                && artist.isSparringWith(player)) {
            artist.onLandedBlow();
            event.setAmount(Sparring.protect(player.getHealth(), event.getAmount()));
        }
    }

    /** After a blow lands in a spar, whoever it left beaten yields. */
    @SubscribeEvent
    public static void afterDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();

        if (target instanceof MartialArtistEntity artist && attacker instanceof ServerPlayer player
                && artist.isSparringWith(player)
                && Sparring.yields(artist.getHealth(), artist.getMaxHealth())) {
            artist.yieldTo(player);
        } else if (target instanceof ServerPlayer player && attacker instanceof MartialArtistEntity artist
                && artist.isSparringWith(player)
                && Sparring.yields(player.getHealth(), player.getMaxHealth())) {
            artist.acceptYieldFrom(player);
        }
    }

    private SparringEvents() {
    }
}
