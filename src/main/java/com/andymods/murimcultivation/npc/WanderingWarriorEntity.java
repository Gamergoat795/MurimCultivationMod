package com.andymods.murimcultivation.npc;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.MurimRegistries;
import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.cultivation.AttributeGrant;
import com.andymods.murimcultivation.cultivation.QiDensity;
import com.andymods.murimcultivation.cultivation.Realm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

/**
 * A martial artist walking the roads: neutral until provoked, and carrying a realm of its own.
 *
 * <p><strong>Neutral is the whole point.</strong> It has no targeting goal beyond retaliation, so it
 * will never pick a fight. That leaves the player with two ways to start one — asking, or not
 * asking — and the difference between them is what honour and infamy measure. An entity that
 * attacked on sight would collapse that choice before it was offered.
 *
 * <p>Its realm is rolled once, at spawn, from the local Qi-richness and the distance from world
 * spawn, and then it keeps it. Strength comes from that realm's own {@code attributes} list in the
 * datapack, so a warrior sits on the same curve the player climbs and rebalancing the ladder
 * rebalances them too.
 *
 * <p><strong>Display uses the vanilla custom name rather than synched entity data.</strong> A custom
 * name replicates to clients for free, saves itself in NBT, and renders the way players already
 * expect a named mob to render. Adding a {@code SynchedEntityData} slot to carry the same string
 * would be a networking channel to maintain for no gain. The realm and tier are still kept as real
 * server-side fields, because duel logic needs to compare them and a display string is not state.
 */
public class WanderingWarriorEntity extends PathfinderMob {

    private static final String REALM_TIER_TAG = "RealmTier";
    private static final String TIER_TAG = "Tier";
    private static final String SECT_TAG = "Sect";

    /** Keyed by the same string the modifier helpers use, so it cannot collide with a player's. */
    private static final String MODIFIER_SOURCE = "warrior_realm";

    private int realmTier = 1;
    private WarriorTier tier = WarriorTier.THUG;

    /** Which sect this warrior owes allegiance to, if any. Most wanderers owe none. */
    private Optional<ResourceLocation> sect = Optional.empty();

    public WanderingWarriorEntity(EntityType<? extends WanderingWarriorEntity> type, Level level) {
        super(type, level);
    }

    /**
     * The floor every warrior starts from, before its realm is added on top.
     *
     * <p>Deliberately lower than the teaching NPC's forty health: a Third-Rate thug should be a
     * fair fight for a Third-Rate player, and the realm grants are what make an Expert frightening.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // The only targeting goal, and the only one there should be. A wanderer fights whoever hit
        // it and nobody else; it does not hunt.
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // --- What it is -------------------------------------------------------------------

    public int realmTier() {
        return realmTier;
    }

    public WarriorTier tier() {
        return tier;
    }

    public Optional<ResourceLocation> sectId() {
        return sect;
    }

    public void setSectId(Optional<ResourceLocation> sect) {
        this.sect = sect;
    }

    /**
     * Places this warrior at a realm, applying that realm's grants and renaming it to match.
     *
     * <p>Idempotent: the modifiers carry stable ids and are removed before being re-added, so
     * calling this twice does not stack them. That matters because it runs both on a fresh spawn
     * and again when a saved warrior loads.
     */
    public void setRealmTier(int realmTier) {
        this.realmTier = Math.max(1, realmTier);
        this.tier = WarriorTier.forRealmTier(this.realmTier);
        applyRealmAttributes();
        setCustomName(describe());
    }

    /** The name a player reads when they look at one: what it is, and how far along it is. */
    private Component describe() {
        Component realmName = realmAt(realmTier)
                .map(Realm::displayName)
                .orElseGet(() -> Component.literal("tier " + realmTier));
        return Component.translatable("murimcultivation.warrior.name",
                Component.translatable(tier.translationKey()), realmName);
    }

    private Optional<Realm> realmAt(int tier) {
        Registry<Realm> registry = level().registryAccess().registryOrThrow(MurimRegistries.REALM);
        return registry.stream().filter(realm -> realm.tier() == tier).findFirst();
    }

    /**
     * Applies the rolled realm's attribute grants.
     *
     * <p>Strip-then-reapply with stable ids, the same discipline
     * {@code CultivationService.applyAttributes} uses on players: a grant that is no longer
     * conferred has to drop its old bonus rather than leave it stranded.
     */
    private void applyRealmAttributes() {
        Registry<Realm> registry = level().registryAccess().registryOrThrow(MurimRegistries.REALM);
        for (Realm realm : registry) {
            for (AttributeGrant grant : realm.attributes()) {
                remove(grant.attribute());
            }
        }
        realmAt(realmTier).ifPresent(realm -> {
            for (AttributeGrant grant : realm.attributes()) {
                grant.apply(this, modifierId(grant.attribute()));
            }
        });
    }

    private void remove(Holder<Attribute> attribute) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(modifierId(attribute));
        }
    }

    private static ResourceLocation modifierId(Holder<Attribute> attribute) {
        return MurimCultivationMod.id(MODIFIER_SOURCE + "/"
                + attribute.unwrapKey().map(key -> key.location().getPath()).orElse("unknown"));
    }

    // --- Spawning ---------------------------------------------------------------------

    /**
     * Spawn rules: solid ground, not peaceful, and any light level.
     *
     * <p>Any light is the departure from the monster default, and it is the point. These are people
     * walking the roads, not something that crawls out of the dark, so they have to be meetable at
     * noon. Everything else defers to {@code Mob.checkMobSpawnRules} rather than being re-derived.
     */
    public static boolean checkSpawnRules(EntityType<WanderingWarriorEntity> type, LevelAccessor level,
                                          MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        // The config veto lives here rather than in the biome modifier because a datapack weight
        // cannot be read from config, and a server owner turning this off should not have to write
        // a datapack to do it.
        if (!MurimConfig.warriorNaturalSpawns()) {
            return false;
        }
        return level.getDifficulty() != Difficulty.PEACEFUL
                && Mob.checkMobSpawnRules(type, level, spawnType, pos, random);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, groupData);

        setRealmTier(rollRealmTier(level, blockPosition(), getRandom()));
        // Only on a fresh spawn: the realm grants raise max health, and a warrior that arrives
        // already wounded reads as a bug. A loaded one keeps the health it was saved with.
        setHealth(getMaxHealth());
        return result;
    }

    /** Rolls a realm from how dangerous this place is. See {@link WarriorSpawns}. */
    private static int rollRealmTier(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        WarriorSpawns.Tuning tuning = WarriorSpawns.Tuning.fromConfig();
        Holder<Biome> biome = level.getBiome(pos);

        double distance = level.getLevel() instanceof ServerLevel server
                ? Math.sqrt(pos.distToCenterSqr(server.getSharedSpawnPos().getCenter()))
                : 0.0D;

        int pressure = WarriorSpawns.pressure(
                biome.is(QiDensity.QI_RICH), biome.is(QiDensity.QI_BARREN), distance, tuning);

        WarriorTier band = WarriorSpawns.bandFor(pressure, random.nextInt(100));
        return WarriorSpawns.realmTierFor(band, random.nextInt(64), tuning);
    }

    // --- Talking to one ---------------------------------------------------------------

    /**
     * Right-clicking a wanderer.
     *
     * <p>For now it only says who it is. The challenge this gesture is obviously reaching for
     * arrives with the duel system; until then it says so plainly, because "nothing happens" is
     * the worst response an NPC can give and a silent right-click is what sent the last playtest
     * looking for a bug.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        serverPlayer.sendSystemMessage(Component.translatable("murimcultivation.npc.prefix",
                describe(), Component.translatable("murimcultivation.warrior.greeting")));
        return InteractionResult.CONSUME;
    }

    // --- Persistence ------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(REALM_TIER_TAG, realmTier);
        tag.putString(TIER_TAG, tier.getSerializedName());
        sect.ifPresent(id -> tag.putString(SECT_TAG, id.toString()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(SECT_TAG)) {
            sect = Optional.ofNullable(ResourceLocation.tryParse(tag.getString(SECT_TAG)));
        }
        if (tag.contains(REALM_TIER_TAG)) {
            // Re-applies the realm's grants, because they are transient modifiers and so were not
            // saved. Health is deliberately left alone: the realm has not changed, so the maximum
            // is the same one the saved health was already within.
            setRealmTier(tag.getInt(REALM_TIER_TAG));
        }
    }
}
