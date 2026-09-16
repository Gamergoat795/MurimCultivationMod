package com.andymods.murimcultivation.cultivation;

import com.andymods.murimcultivation.config.MurimConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Optional;

/**
 * Deciding whether a cultivator may break through, and carrying it out.
 *
 * <p>M1 implements the gate — banked progress, foundation purity, an adequately open meridian
 * network — and the advancement itself. The probabilistic roll, the Qi Deviation consequences
 * of failure, and the tribulation spectacle on success are layered on in M2; the reasons a
 * player can be refused are already the real ones.
 */
public final class BreakthroughService {

    /** Why a breakthrough attempt was refused, or that it may proceed. */
    public enum Eligibility {
        READY(null),
        NOT_AWAKENED("murimcultivation.breakthrough.not_awakened"),
        NO_REALM_DATA("murimcultivation.breakthrough.no_realm_data"),
        ALREADY_AT_PEAK_REALM("murimcultivation.breakthrough.already_at_peak_realm"),
        SUBSTAGE_NOT_PEAK("murimcultivation.breakthrough.substage_not_peak"),
        INSUFFICIENT_PROGRESS("murimcultivation.breakthrough.insufficient_progress"),
        INSUFFICIENT_PURITY("murimcultivation.breakthrough.insufficient_purity"),
        MERIDIANS_TOO_NARROW("murimcultivation.breakthrough.meridians_too_narrow"),
        SUFFERING_DEVIATION("murimcultivation.breakthrough.suffering_deviation");

        private final String translationKey;

        Eligibility(String translationKey) {
            this.translationKey = translationKey;
        }

        public boolean isReady() {
            return this == READY;
        }

        public Component message() {
            return translationKey == null ? Component.empty() : Component.translatable(translationKey);
        }
    }

    /**
     * Whether the player may attempt a breakthrough right now, and if not, why.
     *
     * <p>Evaluated against the realm being broken <em>into</em>, not the current one: it is the
     * next realm that decides how clean a foundation and how open a network the body needs to
     * survive the transition.
     */
    public static Eligibility check(ServerPlayer player) {
        CultivationData data = CultivationService.data(player);

        if (!data.isAwakened()) {
            return Eligibility.NOT_AWAKENED;
        }
        if (data.deviation().isActive()) {
            return Eligibility.SUFFERING_DEVIATION;
        }

        Registry<Realm> registry = CultivationService.realmRegistry(player);
        Optional<Realm> current = CultivationService.realmOf(registry, data);
        if (current.isEmpty()) {
            return Eligibility.NO_REALM_DATA;
        }

        Optional<Holder.Reference<Realm>> next = RealmProgression.next(registry, current.get());
        if (next.isEmpty()) {
            return Eligibility.ALREADY_AT_PEAK_REALM;
        }

        // A breakthrough is only available from the top of the current realm.
        if (!data.substage().isLast()) {
            return Eligibility.SUBSTAGE_NOT_PEAK;
        }
        if (data.progress() < current.get().progressToLeave(data.substage())) {
            return Eligibility.INSUFFICIENT_PROGRESS;
        }

        Realm target = next.get().value();
        if (data.purity() < target.purityFloor()) {
            return Eligibility.INSUFFICIENT_PURITY;
        }
        if (data.openMeridianCount() < target.requiredOpenMeridians()) {
            return Eligibility.MERIDIANS_TOO_NARROW;
        }

        return Eligibility.READY;
    }

    /**
     * Attempts a breakthrough. Returns the realm entered, or empty if the attempt was refused;
     * the caller is expected to have told the player why via {@link #check}.
     */
    public static Optional<Realm> attempt(ServerPlayer player) {
        if (!check(player).isReady()) {
            return Optional.empty();
        }

        Registry<Realm> registry = CultivationService.realmRegistry(player);
        CultivationData data = CultivationService.data(player);
        Realm current = CultivationService.realmOf(registry, data).orElseThrow();
        Holder.Reference<Realm> next = RealmProgression.next(registry, current).orElseThrow();

        // Spend the banked progress and enter the new realm at its first substage.
        data.setProgress(data.progress() - current.progressToLeave(data.substage()));
        CultivationService.setRealm(player, next.key(), Substage.EARLY);

        Realm entered = next.value();
        announce(player, entered);
        return Optional.of(entered);
    }

    private static void announce(ServerPlayer player, Realm entered) {
        player.sendSystemMessage(Component.translatable(
                "murimcultivation.breakthrough.success", entered.fullDisplayName()));

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.7F);

        // Broadcast to everyone *else*: the player already got the personal message above.
        if (MurimConfig.announceBreakthroughs() && player.getServer() != null) {
            Component broadcast = Component.translatable("murimcultivation.breakthrough.broadcast",
                    player.getDisplayName(), entered.fullDisplayName());
            for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
                if (other != player) {
                    other.sendSystemMessage(broadcast);
                }
            }
        }
    }

    private BreakthroughService() {
    }
}
