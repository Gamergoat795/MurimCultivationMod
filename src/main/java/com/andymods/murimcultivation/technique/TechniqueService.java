package com.andymods.murimcultivation.technique;

import com.andymods.murimcultivation.MurimRegistries;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.MeditationService;
import com.andymods.murimcultivation.cultivation.Realm;
import com.andymods.murimcultivation.system.ObjectiveKind;
import com.andymods.murimcultivation.system.QuestTracker;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * The single gate every technique cast passes through.
 *
 * <p>Having exactly one path means the checks cannot get out of step with each other: a cast
 * from a keybind, from a command, and (later) from an NPC sparring partner all validate the
 * same way. The client never decides any of it — it only says which slot was pressed.
 */
public final class TechniqueService {

    private TechniqueService() {
    }

    /** Why a cast was refused, or that it may proceed. */
    public enum Refusal {
        ALLOWED(null),
        NOT_AWAKENED("murimcultivation.message.not_awakened"),
        UNKNOWN_TECHNIQUE("murimcultivation.technique.refused.unknown"),
        NOT_LEARNED("murimcultivation.technique.refused.not_learned"),
        REALM_TOO_LOW("murimcultivation.technique.refused.realm"),
        TECHNIQUE_TIER_TOO_HIGH("murimcultivation.technique.refused.tier"),
        MERIDIANS_TOO_NARROW("murimcultivation.technique.refused.meridians"),
        WRONG_HAND(null),
        ON_COOLDOWN("murimcultivation.technique.refused.cooldown"),
        NOT_ENOUGH_QI("murimcultivation.technique.refused.qi"),
        SUFFERING_DEVIATION("murimcultivation.technique.refused.deviation"),
        NO_BEHAVIOUR("murimcultivation.technique.refused.no_behaviour");

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

        /**
         * The message to show the player. A wrong grip names the grip the art actually wants,
         * which is far more useful than "your grip is wrong" when a player has eight arts with
         * three different requirements.
         */
        public Component messageFor(Technique technique) {
            if (this == WRONG_HAND) {
                return Component.translatable(technique.hand().refusalTranslationKey());
            }
            return message();
        }
    }

    public static Registry<Technique> registry(ServerPlayer player) {
        return player.level().registryAccess().registryOrThrow(MurimRegistries.TECHNIQUE);
    }

    public static Optional<Technique> byId(ServerPlayer player, ResourceLocation id) {
        return registry(player).getOptional(ResourceKey.create(MurimRegistries.TECHNIQUE, id));
    }

    /**
     * Whether the player may cast this technique right now.
     *
     * <p>Note the hand check is here rather than inside each behaviour: a sword art refusing
     * to fire bare-handed is a rule about the art, and putting it in one place means all eight
     * behave consistently.
     */
    public static Refusal check(ServerPlayer player, ResourceLocation id, Technique technique) {
        CultivationData data = CultivationService.data(player);

        if (!data.isAwakened()) {
            return Refusal.NOT_AWAKENED;
        }
        if (data.deviation().isActive()) {
            return Refusal.SUFFERING_DEVIATION;
        }
        if (!data.knowsTechnique(id)) {
            return Refusal.NOT_LEARNED;
        }
        if (!TechniqueBehaviours.isRegistered(technique.behaviour())) {
            return Refusal.NO_BEHAVIOUR;
        }

        Registry<Realm> realms = CultivationService.realmRegistry(player);
        Optional<Realm> realm = CultivationService.realmOf(realms, data);
        if (realm.isEmpty() || realm.get().tier() < technique.requiredRealmTier()) {
            return Refusal.REALM_TOO_LOW;
        }
        // The realm also caps how advanced an art the body can channel at all.
        if (technique.tier() > realm.get().techniqueTier()) {
            return Refusal.TECHNIQUE_TIER_TOO_HIGH;
        }
        if (data.openMeridianCount() < technique.requiredOpenMeridians()) {
            return Refusal.MERIDIANS_TOO_NARROW;
        }
        if (!technique.hand().isSatisfiedBy(player)) {
            return Refusal.WRONG_HAND;
        }
        if (data.isTechniqueOnCooldown(id)) {
            return Refusal.ON_COOLDOWN;
        }

        int mastery = data.techniqueMastery(id);
        if (data.qi() < TechniqueMastery.qiCost(technique.qiCost(), mastery)) {
            return Refusal.NOT_ENOUGH_QI;
        }

        return Refusal.ALLOWED;
    }

    /**
     * Casts a technique by id.
     *
     * <p>Order matters: gates, then spend Qi and start the cooldown, then fire. If the
     * behaviour reports it did nothing — healing at full health, a dash into a wall — the Qi is
     * refunded and the cooldown cleared, so a wasted cast costs only the moment it took.
     *
     * @return whether the technique fired
     */
    public static boolean cast(ServerPlayer player, ResourceLocation id) {
        Optional<Technique> found = byId(player, id);
        if (found.isEmpty()) {
            player.displayClientMessage(Refusal.UNKNOWN_TECHNIQUE.message(), true);
            return false;
        }

        Technique technique = found.get();
        Refusal refusal = check(player, id, technique);
        if (!refusal.allowed()) {
            player.displayClientMessage(refusal.messageFor(technique), true);
            return false;
        }

        CultivationData data = CultivationService.data(player);
        int mastery = data.techniqueMastery(id);
        double cost = TechniqueMastery.qiCost(technique.qiCost(), mastery);
        int cooldown = TechniqueMastery.cooldownTicks(technique.cooldownTicks(), mastery);

        // Channelling Qi outward is not meditating.
        MeditationService.stop(player, MeditationService.Interruption.ATTACKED);

        if (!data.spendQi(cost)) {
            player.displayClientMessage(Refusal.NOT_ENOUGH_QI.message(), true);
            return false;
        }
        data.setTechniqueCooldown(id, cooldown);

        boolean fired = TechniqueBehaviours.get(technique.behaviour())
                .map(behaviour -> behaviour.execute(player, id, technique, mastery))
                .orElse(false);

        if (!fired) {
            data.addQi(cost, CultivationService.qiCapacity(player));
            data.clearTechniqueCooldown(id);
            CultivationService.syncValuesToClient(player);
            return false;
        }

        grantMastery(player, data, id, technique, mastery);
        QuestTracker.recordProgress(player, ObjectiveKind.CAST_TECHNIQUE, id, 1);
        CultivationService.syncToClient(player);
        return true;
    }

    /** Casts whatever is in a loadout slot. */
    public static boolean castSlot(ServerPlayer player, int slot) {
        CultivationData data = CultivationService.data(player);
        Optional<ResourceLocation> id = data.techniqueInSlot(slot);
        if (id.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("murimcultivation.technique.empty_slot", slot + 1), true);
            return false;
        }
        return cast(player, id.get());
    }

    /** Awards mastery for a successful cast, announcing whole-point milestones. */
    private static void grantMastery(ServerPlayer player, CultivationData data, ResourceLocation id,
                                     Technique technique, int masteryBefore) {
        double gain = TechniqueMastery.gainFrom(masteryBefore, technique.masteryGainPerUse());
        if (gain <= 0.0D) {
            return;
        }

        // Mastery is stored as a whole number, so fractional gains are accumulated separately;
        // otherwise a sub-1.0 gain would round to nothing and high mastery would never advance.
        data.addTechniqueMasteryProgress(id, gain);
        int masteryAfter = data.techniqueMastery(id);

        if (masteryAfter > masteryBefore) {
            if (masteryAfter >= TechniqueMastery.MAX) {
                player.sendSystemMessage(Component.translatable(
                        "murimcultivation.technique.mastered", technique.fullDisplayName()));
            } else if (TechniqueMastery.stars(masteryAfter) > TechniqueMastery.stars(masteryBefore)) {
                player.sendSystemMessage(Component.translatable(
                        "murimcultivation.technique.mastery_up",
                        technique.displayName(), masteryAfter));
            }
        }
    }

    /**
     * Teaches a technique, or deepens it if already known.
     *
     * @return true if this was newly learned
     */
    public static boolean learn(ServerPlayer player, ResourceLocation id, Technique technique) {
        CultivationData data = CultivationService.data(player);
        if (data.knowsTechnique(id)) {
            return false;
        }

        data.setTechniqueMastery(id, 0);
        // Put it somewhere usable immediately rather than making the player hunt for a UI.
        data.assignFirstEmptySlot(id);
        player.sendSystemMessage(Component.translatable(
                "murimcultivation.technique.learned", technique.fullDisplayName()));
        // Learning and mastering are threshold objectives, so re-read rather than counted.
        QuestTracker.evaluate(player);
        CultivationService.syncToClient(player);
        return true;
    }
}
