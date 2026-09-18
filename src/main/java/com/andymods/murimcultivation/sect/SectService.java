package com.andymods.murimcultivation.sect;

import com.andymods.murimcultivation.MurimRegistries;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.standing.StandingService;
import com.andymods.murimcultivation.system.SystemNotification;
import com.andymods.murimcultivation.system.SystemNotifications;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * Joining sects, earning standing, and rising through the ranks.
 *
 * <p><strong>A skeleton.</strong> Reputation, rank and the alignment penalty all work; what is
 * missing is anything that *grants* reputation, because that will come from the NPCs and sect
 * quests of a later milestone. Until then {@code /murim sect} is the only source, which is
 * enough to exercise every path.
 *
 * <p>Reputation is stored per sect rather than as one number precisely so that serving the
 * Alliance can cost you with the Demonic Cult — the genre's central tension, applied in
 * {@link #addReputation}.
 */
public final class SectService {

    /** How much of a reputation gain is taken from opposing sects. */
    private static final double OPPOSED_PENALTY = 0.5D;

    private SectService() {
    }

    public static Registry<Sect> registry(Player player) {
        return player.level().registryAccess().registryOrThrow(MurimRegistries.SECT);
    }

    public static Optional<Sect> byId(Player player, ResourceLocation id) {
        return registry(player).getOptional(ResourceKey.create(MurimRegistries.SECT, id));
    }

    /** The player's standing with a sect, as a rank. */
    public static SectRank rankIn(Player player, ResourceLocation sect) {
        return SectRank.forReputation(CultivationService.data(player).sectReputation(sect));
    }

    /** Every sect the player has any standing with at all. */
    public static boolean isMemberOf(Player player, ResourceLocation sect) {
        return rankIn(player, sect).isMember();
    }

    /** Why a sect will not take someone in, or that it will. */
    public enum JoinResult {
        ACCEPTED(null),
        UNKNOWN_SECT("murimcultivation.sect.refused.unknown"),
        NOT_AWAKENED("murimcultivation.sect.refused.not_awakened"),
        REALM_TOO_LOW("murimcultivation.sect.refused.realm"),
        ALREADY_MEMBER("murimcultivation.sect.refused.already_member"),
        OPPOSED_ALLEGIANCE("murimcultivation.sect.refused.opposed"),
        HONOUR_TOO_LOW("murimcultivation.sect.refused.honour"),
        INFAMY_TOO_HIGH("murimcultivation.sect.refused.notorious"),
        INFAMY_TOO_LOW("murimcultivation.sect.refused.unfeared");

        private final String translationKey;

        JoinResult(String translationKey) {
            this.translationKey = translationKey;
        }

        public boolean accepted() {
            return this == ACCEPTED;
        }

        public Component message() {
            return translationKey == null ? Component.empty() : Component.translatable(translationKey);
        }
    }

    /**
     * Asks a sect to take the player in.
     *
     * <p>Refuses if they already carry standing with an opposing sect — you cannot be both an
     * Alliance disciple and a cultist, and being told so plainly is better than discovering it
     * through a reputation number quietly going nowhere.
     */
    public static JoinResult join(ServerPlayer player, ResourceLocation id) {
        Optional<Sect> found = byId(player, id);
        if (found.isEmpty()) {
            return JoinResult.UNKNOWN_SECT;
        }

        Sect sect = found.get();
        CultivationData data = CultivationService.data(player);
        if (!data.isAwakened()) {
            return JoinResult.NOT_AWAKENED;
        }
        if (isMemberOf(player, id)) {
            return JoinResult.ALREADY_MEMBER;
        }
        if (CultivationService.realmOf(player).map(realm -> realm.tier() < sect.requiredRealmTier())
                .orElse(true)) {
            return JoinResult.REALM_TOO_LOW;
        }

        for (var entry : registry(player).entrySet()) {
            ResourceLocation otherId = entry.getKey().location();
            if (!otherId.equals(id)
                    && sect.alignment().opposes(entry.getValue().alignment())
                    && isMemberOf(player, otherId)) {
                return JoinResult.OPPOSED_ALLEGIANCE;
            }
        }

        // Standing is the last gate, and it is checked here rather than earlier so a player is told
        // about the concrete obstacles — unknown sect, wrong realm, already sworn elsewhere — before
        // being told about a reputation they may not yet know they have.
        //
        // This is the gate M8's first commit wrote and deliberately left disconnected: until duels
        // existed nothing in the game could move honour, so enforcing it would have made two of the
        // three shipped sects unjoinable with no way to qualify. Duels exist now, so it is live.
        StandingService.Verdict standing = StandingService.judge(
                sect.alignment(),
                CultivationService.data(player).standing(),
                StandingService.Tuning.fromConfig());
        switch (standing) {
            case HONOUR_TOO_LOW -> {
                return JoinResult.HONOUR_TOO_LOW;
            }
            case INFAMY_TOO_HIGH -> {
                return JoinResult.INFAMY_TOO_HIGH;
            }
            case INFAMY_TOO_LOW -> {
                return JoinResult.INFAMY_TOO_LOW;
            }
            case ACCEPTED -> {
            }
        }

        // Joining grants exactly enough standing to be an outer disciple, and no more.
        data.addSectReputation(id, SectRank.OUTER_DISCIPLE.reputationRequired());
        SystemNotifications.send(player, SystemNotification.sectJoined(sect.fullDisplayName()));
        CultivationService.syncToClient(player);
        return JoinResult.ACCEPTED;
    }

    /** Leaves a sect, forfeiting every point of standing with it. */
    public static boolean leave(ServerPlayer player, ResourceLocation id) {
        CultivationData data = CultivationService.data(player);
        int standing = data.sectReputation(id);
        if (standing <= 0) {
            return false;
        }

        data.addSectReputation(id, -standing);
        byId(player, id).ifPresent(sect -> player.sendSystemMessage(
                Component.translatable("murimcultivation.sect.left", sect.fullDisplayName())));
        CultivationService.syncToClient(player);
        return true;
    }

    /**
     * Grants standing with a sect, taking a share of it from any sect that opposes them.
     *
     * @return the rank the player now holds with that sect
     */
    public static SectRank addReputation(ServerPlayer player, ResourceLocation id, int amount) {
        CultivationData data = CultivationService.data(player);
        SectRank before = rankIn(player, id);
        data.addSectReputation(id, amount);

        if (amount > 0) {
            byId(player, id).ifPresent(sect -> {
                int penalty = (int) Math.round(amount * OPPOSED_PENALTY);
                if (penalty <= 0) {
                    return;
                }
                for (var entry : registry(player).entrySet()) {
                    ResourceLocation otherId = entry.getKey().location();
                    if (!otherId.equals(id)
                            && sect.alignment().opposes(entry.getValue().alignment())
                            && data.sectReputation(otherId) > 0) {
                        data.addSectReputation(otherId, -penalty);
                    }
                }
            });
        }

        SectRank after = rankIn(player, id);
        if (after.ordinal() > before.ordinal()) {
            byId(player, id).ifPresent(sect -> SystemNotifications.send(player,
                    SystemNotification.sectPromoted(sect.displayName(),
                            Component.translatable(after.translationKey()))));
        }
        CultivationService.syncToClient(player);
        return after;
    }
}
