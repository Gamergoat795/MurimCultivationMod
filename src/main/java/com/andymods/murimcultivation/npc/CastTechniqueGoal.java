package com.andymods.murimcultivation.npc;

import net.minecraft.world.entity.ai.goal.Goal;

import java.util.Optional;

/**
 * Casts a martial art when one is ready and worth it.
 *
 * <p>Claims no movement or look flags, so it runs alongside the melee goal rather than
 * replacing it: an artist closes in and trades blows, and throws an art whenever one comes
 * off cooldown with the target in its reach.
 */
final class CastTechniqueGoal extends Goal {

    /** How often, in ticks, the artist considers casting. Twice a second is plenty. */
    private static final int CHECK_INTERVAL = 10;

    private final MartialArtistEntity artist;
    private NpcTechniques.Art chosen;

    CastTechniqueGoal(MartialArtistEntity artist) {
        this.artist = artist;
    }

    @Override
    public boolean canUse() {
        if (artist.tickCount % CHECK_INTERVAL != 0) {
            return false;
        }
        Optional<NpcTechniques.Art> art = NpcTechniques.choose(artist, artist.getTarget());
        chosen = art.orElse(null);
        return chosen != null;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        NpcTechniques.cast(artist, chosen, artist.getTarget());
        chosen = null;
    }
}
