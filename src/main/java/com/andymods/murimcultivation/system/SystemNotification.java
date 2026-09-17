package com.andymods.murimcultivation.system;

import net.minecraft.network.chat.Component;

/**
 * One line the System shows the player.
 *
 * <p>The register is fixed and deliberate: terse, bracketed, mechanical. No personality, no
 * opinions — `[Quest complete: Foundations of Breathing]`. That reads unmistakably as the genre
 * and, unlike a narrator with a voice, never grates on the hundredth reading.
 *
 * @param kind  which accent colour and icon the toast uses
 * @param title the bracketed headline
 * @param detail an optional second line
 */
public record SystemNotification(Kind kind, Component title, Component detail) {

    public enum Kind {
        /** A quest finished. */
        QUEST,
        /** A realm, meridian or mastery milestone. */
        ADVANCEMENT,
        /** Something earned: points, a title. */
        REWARD,
        /** Something went wrong: a deviation, a failed breakthrough. */
        WARNING
    }

    public static SystemNotification questComplete(Component questName) {
        return new SystemNotification(Kind.QUEST,
                Component.translatable("murimcultivation.system.quest_complete", questName),
                Component.empty());
    }

    public static SystemNotification questAvailable(Component questName) {
        return new SystemNotification(Kind.QUEST,
                Component.translatable("murimcultivation.system.quest_available", questName),
                Component.empty());
    }

    public static SystemNotification realmAttained(Component realmName) {
        return new SystemNotification(Kind.ADVANCEMENT,
                Component.translatable("murimcultivation.system.realm_attained", realmName),
                Component.empty());
    }

    public static SystemNotification statPoints(int amount) {
        return new SystemNotification(Kind.REWARD,
                Component.translatable("murimcultivation.system.stat_points", amount),
                Component.translatable("murimcultivation.system.stat_points_hint"));
    }

    public static SystemNotification titleEarned(Component titleName) {
        return new SystemNotification(Kind.REWARD,
                Component.translatable("murimcultivation.system.title_earned", titleName),
                Component.empty());
    }

    public static SystemNotification dailiesReset() {
        return new SystemNotification(Kind.QUEST,
                Component.translatable("murimcultivation.system.dailies_reset"),
                Component.empty());
    }

    public static SystemNotification sectJoined(Component sectName) {
        return new SystemNotification(Kind.ADVANCEMENT,
                Component.translatable("murimcultivation.system.sect_joined", sectName),
                Component.empty());
    }

    public static SystemNotification sectPromoted(Component sectName, Component rankName) {
        return new SystemNotification(Kind.ADVANCEMENT,
                Component.translatable("murimcultivation.system.sect_promoted", rankName),
                sectName);
    }

    public static SystemNotification warning(Component detail) {
        return new SystemNotification(Kind.WARNING,
                Component.translatable("murimcultivation.system.warning"), detail);
    }
}
