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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

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
        ALLOWED(null, false),
        NOT_AWAKENED("murimcultivation.message.not_awakened", true),
        UNKNOWN_TECHNIQUE("murimcultivation.technique.refused.unknown", true),
        NOT_LEARNED("murimcultivation.technique.refused.not_learned", true),
        REALM_TOO_LOW("murimcultivation.technique.refused.realm", true),
        TECHNIQUE_TIER_TOO_HIGH("murimcultivation.technique.refused.tier", true),
        MERIDIANS_TOO_NARROW("murimcultivation.technique.refused.meridians", true),
        WRONG_HAND(null, true),
        ON_COOLDOWN("murimcultivation.technique.refused.cooldown", false),
        NOT_ENOUGH_QI("murimcultivation.technique.refused.qi", false),
        SUFFERING_DEVIATION("murimcultivation.technique.refused.deviation", true),
        NO_BEHAVIOUR("murimcultivation.technique.refused.no_behaviour", true);

        private final String translationKey;
        private final boolean standing;

        Refusal(String translationKey, boolean standing) {
            this.translationKey = translationKey;
            this.standing = standing;
        }

        public boolean allowed() {
            return this == ALLOWED;
        }

        /**
         * Whether this refusal is a standing condition rather than a passing one.
         *
         * <p>It decides where the player is told. A cooldown or an empty dantian clears itself
         * in seconds and belongs on the action bar, where repeating it would only be noise. The
         * rest — not learned, realm too low, wrong grip — will still be true next time, and
         * those go to chat with a sound, because the action bar fades in about three seconds and
         * is overwritten by held-item names. That is how the first playtest ended up unable to
         * tell a gated art from a broken one: every refusal was being messaged, and none of them
         * was being seen.
         */
        public boolean standing() {
            return standing;
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
            refuse(player, Refusal.UNKNOWN_TECHNIQUE, Refusal.UNKNOWN_TECHNIQUE.message());
            return false;
        }

        Technique technique = found.get();
        Refusal refusal = check(player, id, technique);
        if (!refusal.allowed()) {
            refuse(player, refusal, refusal.messageFor(technique));
            return false;
        }

        CultivationData data = CultivationService.data(player);
        int mastery = data.techniqueMastery(id);
        double cost = TechniqueMastery.qiCost(technique.qiCost(), mastery);
        int cooldown = TechniqueMastery.cooldownTicks(technique.cooldownTicks(), mastery);

        // Channelling Qi outward is not meditating.
        MeditationService.stop(player, MeditationService.Interruption.ATTACKED);

        if (!data.spendQi(cost)) {
            refuse(player, Refusal.NOT_ENOUGH_QI, Refusal.NOT_ENOUGH_QI.message());
            return false;
        }
        data.setTechniqueCooldown(id, cooldown);

        boolean fired = TechniqueBehaviours.get(technique.behaviour())
                .map(behaviour -> behaviour.execute(player, id, technique, mastery))
                .orElse(false);

        if (!fired) {
            data.addQi(cost, CultivationService.qiCapacity(player));
            data.clearTechniqueCooldown(id);
            // Every shipped behaviour that returns false messages first, so this line is
            // unreachable with the arts in the box. It is here for the first datapack behaviour
            // that does not: a refund with no word at all is indistinguishable from a dead key,
            // which is exactly the confusion this whole pass exists to remove.
            player.displayClientMessage(
                    Component.translatable("murimcultivation.technique.refused.no_effect"), true);
            CultivationService.syncValuesToClient(player);
            return false;
        }

        grantMastery(player, data, id, technique, mastery);
        QuestTracker.recordProgress(player, ObjectiveKind.CAST_TECHNIQUE, id, 1);
        CultivationService.syncToClient(player);
        return true;
    }

    /**
     * Tells the player why a cast was refused, loudly enough to be noticed.
     *
     * <p>A standing refusal goes to chat and plays a short cue; a passing one stays on the
     * action bar. The sound is the mod's existing failure register — the same low hurt note the
     * breath-rhythm prompt uses for a missed window — rather than a new one, so "that did not
     * work" sounds the same everywhere in the mod.
     */
    private static void refuse(ServerPlayer player, Refusal refusal, Component message) {
        if (!refusal.standing()) {
            player.displayClientMessage(message, true);
            return;
        }
        player.sendSystemMessage(message);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.25F, 0.5F);
    }

    /** Casts whatever is in a loadout slot. */
    public static boolean castSlot(ServerPlayer player, int slot) {
        CultivationData data = CultivationService.data(player);
        Optional<ResourceLocation> id = data.techniqueInSlot(slot);
        if (id.isEmpty()) {
            // Nothing bound anywhere is a standing condition, not a passing one: the player has
            // pressed the cast key and the loadout bar is not even on screen to explain why
            // nothing happened. One empty slot among several is the passing case.
            if (data.loadout().isEmpty()) {
                player.sendSystemMessage(
                        Component.translatable("murimcultivation.technique.none_learned"));
            } else {
                player.displayClientMessage(
                        Component.translatable("murimcultivation.technique.empty_slot", slot + 1), true);
            }
            return false;
        }
        return cast(player, id.get());
    }

    /**
     * Casts whatever the cycle key has selected.
     *
     * <p>This is what makes one bound key reach all four slots, which the README has promised
     * since M3 and nothing actually implemented: the selection was tracked on the server, never
     * synced, and never read by anything that casts.
     */
    public static boolean castSelected(ServerPlayer player) {
        return castSlot(player, CultivationService.data(player).selectedSlot());
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
        boolean bound = data.assignFirstEmptySlot(id);
        player.sendSystemMessage(Component.translatable(
                "murimcultivation.technique.learned", technique.fullDisplayName()));

        // Say where it went. This is the one moment the player is certainly reading, and a
        // learned art that landed nowhere used to be silent about it — you were told you had
        // learned something and left to discover it was unreachable.
        //
        // The slot number is all the server can honestly name: keybinds live on the client and
        // a player may have rebound them, so the actual key is printed by the loadout bar and
        // the System window, which can read the live mapping.
        if (bound) {
            player.sendSystemMessage(Component.translatable(
                    "murimcultivation.technique.learned_slot", data.loadout().indexOf(id) + 1));
        } else {
            player.sendSystemMessage(
                    Component.translatable("murimcultivation.technique.learned_unbound"));
        }
        // Learning and mastering are threshold objectives, so re-read rather than counted.
        QuestTracker.evaluate(player);
        CultivationService.syncToClient(player);
        return true;
    }
}
