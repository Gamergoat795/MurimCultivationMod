package com.andymods.murimcultivation.cultivation;

import com.andymods.murimcultivation.config.MurimConfig;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.Optional;

/**
 * Opening the twenty nodes of the Qi network, one deliberate act at a time.
 *
 * <p>Each node costs banked cultivation progress and carries a real chance of Qi Deviation, so
 * widening the network is a series of decisions rather than a checklist. Extraordinary vessels
 * cost far more and are riskier, which is what makes finishing them feel like an achievement.
 *
 * <p>Cost scales with how much of the network is already open, so the twentieth node is a
 * project and the first is a small step.
 */
public final class MeridianService {

    private MeridianService() {
    }

    /** Why a node cannot be opened, or that it can. */
    public enum Refusal {
        ALLOWED(null),
        NOT_AWAKENED("murimcultivation.meridian.refused.not_awakened"),
        ALREADY_OPEN("murimcultivation.meridian.refused.already_open"),
        SUFFERING_DEVIATION("murimcultivation.meridian.refused.deviation"),
        INSUFFICIENT_PROGRESS("murimcultivation.meridian.refused.progress"),
        INSUFFICIENT_PURITY("murimcultivation.meridian.refused.purity");

        private final String translationKey;

        Refusal(String translationKey) {
            this.translationKey = translationKey;
        }

        public boolean allowed() {
            return this == ALLOWED;
        }

        public Component message() {
            return translationKey == null ? Component.empty() : Component.translatable(translationKey);
        }
    }

    /** What happened when a node was forced open. */
    public record Outcome(boolean opened, Meridian meridian, double cost,
                          DeviationService.Outcome deviation) {
    }

    /**
     * The costs and risks governing the meridian network.
     *
     * <p>Extracted into a record so the cost and risk formulas are pure functions of their
     * inputs and can be unit tested. Reading config inside a formula makes it unreachable from
     * a test, because {@code ModConfigSpec} values throw until a world has loaded them.
     */
    public record Tuning(double baseCostInSubstages,
                         double costGrowthPerOpenNode,
                         double primaryDeviationChance,
                         double extraordinaryDeviationChance,
                         double purityProtection,
                         double minimumPurity) {

        public static Tuning fromConfig() {
            return new Tuning(
                    MurimConfig.meridianBaseCostInSubstages(),
                    MurimConfig.meridianCostGrowthPerOpenNode(),
                    MurimConfig.meridianPrimaryDeviationChance(),
                    MurimConfig.meridianExtraordinaryDeviationChance(),
                    MurimConfig.meridianPurityProtection(),
                    MurimConfig.meridianMinimumPurity());
        }
    }

    /**
     * Progress needed to open a given node.
     *
     * <p>Denominated in the current realm's own progress scale so the cost stays proportionate as
     * the player climbs, rather than being crushing at Third-Rate and pocket change at Flower Realm.
     */
    public static double costToOpen(double realmProgressPerSubstage, int openNodeCount,
                                    Meridian meridian, Tuning tuning) {
        // Each already-open node makes the next one harder.
        double crowding = 1.0D + openNodeCount * tuning.costGrowthPerOpenNode();

        return realmProgressPerSubstage
                * tuning.baseCostInSubstages()
                * meridian.openingCostMultiplier()
                * crowding;
    }

    public static double costToOpen(Registry<Realm> registry, CultivationData data, Meridian meridian) {
        double realmScale = CultivationService.realmOf(registry, data)
                .map(Realm::progressPerSubstage)
                .orElse(100.0D);
        return costToOpen(realmScale, data.openMeridianCount(), meridian, Tuning.fromConfig());
    }

    public static double costToOpen(ServerPlayer player, Meridian meridian) {
        return costToOpen(CultivationService.realmRegistry(player), CultivationService.data(player), meridian);
    }

    /** Chance that forcing this node open goes wrong. Purity is the main defence. */
    public static double deviationChance(double purity, Meridian meridian, Tuning tuning) {
        double base = meridian.isExtraordinary()
                ? tuning.extraordinaryDeviationChance()
                : tuning.primaryDeviationChance();

        // A clean foundation protects: full purity removes most of the risk.
        double protection = purity / CultivationData.MAX_PURITY;
        return Math.max(0.0D, base * (1.0D - protection * tuning.purityProtection()));
    }

    public static double deviationChance(CultivationData data, Meridian meridian) {
        return deviationChance(data.purity(), meridian, Tuning.fromConfig());
    }

    public static Refusal check(ServerPlayer player, Meridian meridian) {
        CultivationData data = CultivationService.data(player);

        if (!data.isAwakened()) {
            return Refusal.NOT_AWAKENED;
        }
        if (data.isMeridianOpen(meridian)) {
            return Refusal.ALREADY_OPEN;
        }
        if (data.deviation().isActive()) {
            return Refusal.SUFFERING_DEVIATION;
        }
        if (data.purity() < MurimConfig.meridianMinimumPurity()) {
            return Refusal.INSUFFICIENT_PURITY;
        }
        if (data.progress() < costToOpen(player, meridian)) {
            return Refusal.INSUFFICIENT_PROGRESS;
        }
        return Refusal.ALLOWED;
    }

    /**
     * Attempts to force a node open. The progress is spent either way — the attempt is the
     * commitment, the same as a breakthrough.
     */
    public static Optional<Outcome> open(ServerPlayer player, Meridian meridian) {
        Refusal refusal = check(player, meridian);
        if (!refusal.allowed()) {
            player.displayClientMessage(refusal.message(), true);
            return Optional.empty();
        }

        CultivationData data = CultivationService.data(player);
        double cost = costToOpen(player, meridian);
        data.setProgress(data.progress() - cost);

        MeditationService.stop(player, MeditationService.Interruption.MANUAL);

        if (player.getRandom().nextDouble() < deviationChance(data, meridian)) {
            // The node resists. No progress refund, and the backlash is the mild tier: this is
            // a smaller undertaking than a realm transition, so it should not shatter anything.
            DeviationService.Outcome outcome =
                    DeviationService.inflict(player, DeviationSeverity.MINOR_BLOCKAGE);
            player.sendSystemMessage(Component.translatable("murimcultivation.meridian.failed",
                    Component.translatable(meridian.translationKey())));
            return Optional.of(new Outcome(false, meridian, cost, outcome));
        }

        data.openMeridian(meridian);
        // The network just widened, so the Qi ceiling rose with it.
        CultivationService.clampQiToCapacity(player, data);
        celebrate(player, meridian, data);
        CultivationService.syncToClient(player);
        return Optional.of(new Outcome(true, meridian, cost, null));
    }

    /**
     * The next node a player would sensibly open: primary meridians first, then the
     * extraordinary vessels. Lets a keybind or a UI offer a single obvious next step.
     */
    public static Optional<Meridian> nextSealed(CultivationData data) {
        for (List<Meridian> group : List.of(Meridian.PRIMARY_MERIDIANS, Meridian.EXTRAORDINARY_VESSELS)) {
            for (Meridian meridian : group) {
                if (!data.isMeridianOpen(meridian)) {
                    return Optional.of(meridian);
                }
            }
        }
        return Optional.empty();
    }

    private static void celebrate(ServerPlayer player, Meridian meridian, CultivationData data) {
        player.sendSystemMessage(Component.translatable("murimcultivation.meridian.opened",
                Component.translatable(meridian.translationKey()),
                data.openMeridianCount(), Meridian.count()));

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 1.5F);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.GLOW,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    30, 0.4D, 0.8D, 0.4D, 0.1D);
        }

        // Finishing the whole network deserves more than another line of the same message.
        if (data.openMeridianCount() == Meridian.count()) {
            player.sendSystemMessage(Component.translatable("murimcultivation.meridian.network_complete"));
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }
}
