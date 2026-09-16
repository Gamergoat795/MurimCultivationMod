package com.andymods.murimcultivation;

import com.andymods.murimcultivation.cultivation.Realm;
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

    private static <T> ResourceKey<Registry<T>> key(String path) {
        return ResourceKey.createRegistryKey(MurimCultivationMod.id(path));
    }

    private MurimRegistries() {
    }
}
