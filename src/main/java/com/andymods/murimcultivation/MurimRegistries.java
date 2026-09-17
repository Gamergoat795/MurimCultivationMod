package com.andymods.murimcultivation;

import com.andymods.murimcultivation.cultivation.Realm;
import com.andymods.murimcultivation.alchemy.PillRecipe;
import com.andymods.murimcultivation.sect.Sect;
import com.andymods.murimcultivation.system.SystemQuest;
import com.andymods.murimcultivation.system.Title;
import com.andymods.murimcultivation.technique.Technique;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * Keys for this mod's datapack-driven registries.
 *
 * <p>Content defined here is loaded from JSON under
 * {@code data/<namespace>/murimcultivation/<registry>/<name>.json} and synced to clients,
 * so realms and (from M3) techniques can be rebalanced or extended by a datapack with no
 * recompile. The built-in content ships in this mod's own jar under the same paths.
 */
public final class MurimRegistries {

    public static final ResourceKey<Registry<Realm>> REALM = key("realm");
    public static final ResourceKey<Registry<Technique>> TECHNIQUE = key("technique");
    public static final ResourceKey<Registry<SystemQuest>> QUEST = key("quest");
    public static final ResourceKey<Registry<Title>> TITLE = key("title");
    public static final ResourceKey<Registry<Sect>> SECT = key("sect");
    public static final ResourceKey<Registry<PillRecipe>> PILL_RECIPE = key("pill_recipe");

    private static <T> ResourceKey<Registry<T>> key(String path) {
        return ResourceKey.createRegistryKey(MurimCultivationMod.id(path));
    }

    private MurimRegistries() {
    }
}
