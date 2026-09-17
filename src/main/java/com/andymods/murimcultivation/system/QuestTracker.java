package com.andymods.murimcultivation.system;

import com.andymods.murimcultivation.MurimRegistries;
import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.Meridian;
import com.andymods.murimcultivation.cultivation.Realm;
import com.andymods.murimcultivation.item.MartialManualItem;
import com.andymods.murimcultivation.registry.ModItems;
import com.andymods.murimcultivation.technique.TechniqueService;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The System's bookkeeping: what a cultivator is being asked for, whether they have done it,
 * and what they get.
 *
 * <p>Every objective is evaluated here, in one pass over the player's available quests. The
 * alternative — each gameplay system nudging its own objective kind — is how a new objective
 * ends up silently never firing because one call site was missed.
 *
 * <p>Cumulative objectives (seconds meditated, things killed) are pushed in by the systems that
 * produce them via {@link #recordProgress}. Threshold objectives (realm reached, meridians open)
 * are read from live state on every evaluation, so losing a meridian correctly loses the credit.
 */
public final class QuestTracker {

    private QuestTracker() {
    }

    public static Registry<SystemQuest> registry(ServerPlayer player) {
        return player.level().registryAccess().registryOrThrow(MurimRegistries.QUEST);
    }

    public static Registry<Title> titleRegistry(ServerPlayer player) {
        return player.level().registryAccess().registryOrThrow(MurimRegistries.TITLE);
    }

    /**
     * Whether a quest is currently offered: right realm, prerequisites met, not already done.
     */
    public static boolean isAvailable(ServerPlayer player, ResourceLocation id, SystemQuest quest) {
        CultivationData data = CultivationService.data(player);
        QuestLog log = data.questLog();

        if (quest.category().repeatable() ? log.isDailyClaimed(id) : log.isCompleted(id)) {
            return false;
        }
        for (ResourceLocation prerequisite : quest.prerequisites()) {
            if (!log.isCompleted(prerequisite)) {
                return false;
            }
        }
        return CultivationService.realmOf(player)
                .map(realm -> realm.tier() >= quest.requiredRealmTier())
                .orElse(false);
    }

    /** Every quest on offer right now. */
    public static List<ResourceLocation> available(ServerPlayer player) {
        List<ResourceLocation> ids = new ArrayList<>();
        registry(player).entrySet().forEach(entry -> {
            ResourceLocation id = entry.getKey().location();
            if (isAvailable(player, id, entry.getValue())) {
                ids.add(id);
            }
        });
        return ids;
    }

    /**
     * Pushes progress for a cumulative objective kind, then re-evaluates.
     *
     * <p>Called by the systems that generate the events: the cultivation tick for seconds
     * meditated, the combat hooks for kills, the technique service for casts.
     */
    public static void recordProgress(ServerPlayer player, ObjectiveKind kind, int amount) {
        recordProgress(player, kind, null, amount);
    }

    public static void recordProgress(ServerPlayer player, ObjectiveKind kind,
                                      ResourceLocation target, int amount) {
        if (amount <= 0 || !CultivationService.data(player).isAwakened()) {
            return;
        }

        CultivationData data = CultivationService.data(player);
        QuestLog log = data.questLog();
        boolean touched = false;

        for (ResourceLocation id : available(player)) {
            SystemQuest quest = registry(player).get(ResourceKey.create(MurimRegistries.QUEST, id));
            if (quest == null) {
                continue;
            }
            for (QuestObjective objective : quest.objectives()) {
                if (!objective.kind().isCumulative() || objective.kind() != kind) {
                    continue;
                }
                // A targeted objective only counts events naming the same target.
                if (objective.target().isPresent() && !objective.target().get().equals(target)) {
                    continue;
                }
                log.advance(id, objective, amount);
                touched = true;
            }
        }

        if (touched) {
            evaluate(player);
        }
    }

    /**
     * Checks every available quest for completion and pays out the ones that are done.
     *
     * <p>Safe and cheap to call after anything that might have satisfied a threshold — a
     * breakthrough, a meridian opening, a purity change.
     */
    public static void evaluate(ServerPlayer player) {
        CultivationData data = CultivationService.data(player);
        if (!data.isAwakened()) {
            return;
        }

        for (ResourceLocation id : available(player)) {
            SystemQuest quest = registry(player).get(ResourceKey.create(MurimRegistries.QUEST, id));
            if (quest != null && isComplete(player, data, id, quest)) {
                complete(player, id, quest);
            }
        }
    }

    private static boolean isComplete(ServerPlayer player, CultivationData data,
                                      ResourceLocation id, SystemQuest quest) {
        for (QuestObjective objective : quest.objectives()) {
            if (currentValue(player, data, id, objective) < objective.amount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * How far along one objective is.
     *
     * <p>Threshold kinds are read from live state; cumulative kinds come from the stored log.
     * Also used by the System screen to draw progress bars, so the number a player sees is the
     * same number completion is judged against.
     */
    public static int currentValue(ServerPlayer player, CultivationData data,
                                   ResourceLocation questId, QuestObjective objective) {
        return switch (objective.kind()) {
            case REACH_REALM -> CultivationService.realmOf(player)
                    .filter(realm -> matchesRealm(player, realm, objective))
                    .map(realm -> objective.amount())
                    .orElse(0);
            case OPEN_MERIDIANS -> Math.min(objective.amount(), data.openMeridianCount());
            case REACH_PURITY -> (int) Math.min(objective.amount(), Math.floor(data.purity()));
            case LEARN_TECHNIQUE -> objective.target()
                    .filter(data::knowsTechnique)
                    .map(id -> objective.amount())
                    .orElse(0);
            case MASTER_TECHNIQUE -> objective.target()
                    .map(id -> Math.min(objective.amount(), data.techniqueMastery(id)))
                    .orElse(0);
            default -> data.questLog().progressOf(questId, objective);
        };
    }

    private static boolean matchesRealm(ServerPlayer player, Realm realm, QuestObjective objective) {
        return objective.target()
                .flatMap(id -> CultivationService.realmRegistry(player)
                        .getOptional(ResourceKey.create(MurimRegistries.REALM, id)))
                .map(required -> realm.tier() >= required.tier())
                .orElse(false);
    }

    // --- Completion and rewards -------------------------------------------------------

    private static void complete(ServerPlayer player, ResourceLocation id, SystemQuest quest) {
        CultivationData data = CultivationService.data(player);
        data.questLog().complete(id, quest.category());

        for (QuestReward reward : quest.rewards()) {
            grant(player, data, reward);
        }

        SystemNotifications.send(player, SystemNotification.questComplete(quest.displayName()));
        CultivationService.applyAttributes(player);
        CultivationService.syncToClient(player);
    }

    private static void grant(ServerPlayer player, CultivationData data, QuestReward reward) {
        switch (reward.kind()) {
            case PROGRESS -> data.addProgress(reward.amount());
            case PURITY -> data.addPurity(reward.amount());
            case STAT_POINTS -> {
                data.systemProgress().grantPoints((int) reward.amount());
                SystemNotifications.send(player,
                        SystemNotification.statPoints((int) reward.amount()));
            }
            case ITEM -> giveItem(player, reward);
            case TECHNIQUE -> reward.target().ifPresent(id ->
                    TechniqueService.byId(player, id).ifPresent(technique ->
                            TechniqueService.learn(player, id, technique)));
            case TITLE -> reward.target().ifPresent(id -> {
                if (data.systemProgress().grantTitle(id)) {
                    SystemNotifications.send(player, SystemNotification.titleEarned(titleName(player, id)));
                }
            });
        }
    }

    private static void giveItem(ServerPlayer player, QuestReward reward) {
        reward.target().ifPresent(id -> {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == null) {
                return;
            }
            // A manual with no technique component is a blank book, so a quest rewarding one
            // has to say which art it teaches. The convention is the reward's own id path.
            ItemStack stack = item == ModItems.MARTIAL_MANUAL.get()
                    ? MartialManualItem.forTechnique(item, id)
                    : new ItemStack(item, Math.max(1, (int) reward.amount()));
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        });
    }

    private static Component titleName(ServerPlayer player, ResourceLocation id) {
        return Optional.ofNullable(titleRegistry(player).get(ResourceKey.create(MurimRegistries.TITLE, id)))
                .map(Title::fullDisplayName)
                .orElseGet(() -> Component.literal(id.getPath()));
    }

    // --- Daily reset ------------------------------------------------------------------

    /**
     * Clears the repeatable set when the configured number of in-game days has passed.
     *
     * <p>Called from the cultivation tick. One in-game day is the default and the shortest
     * allowed, which is twenty minutes of play; the interval is configurable because at that
     * cadence dailies are farmable by sleeping through nights.
     */
    public static void checkDailyReset(ServerPlayer player) {
        long currentDay = player.level().getDayTime() / 24000L;
        if (CultivationService.data(player).questLog()
                .resetDailiesIfDue(currentDay, MurimConfig.dailyResetIntervalDays())) {
            SystemNotifications.send(player, SystemNotification.dailiesReset());
            CultivationService.syncToClient(player);
        }
    }

    /** Total meridians, for the System screen's objective descriptions. */
    public static int meridianCount() {
        return Meridian.count();
    }
}
