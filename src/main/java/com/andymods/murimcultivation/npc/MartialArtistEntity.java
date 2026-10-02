package com.andymods.murimcultivation.npc;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.MurimRegistries;
import com.andymods.murimcultivation.config.MurimConfig;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.sect.Sect;
import com.andymods.murimcultivation.sect.SectRank;
import com.andymods.murimcultivation.sect.SectService;
import com.andymods.murimcultivation.technique.Technique;
import com.andymods.murimcultivation.technique.TechniqueService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A sect's envoy: the way in, and the teacher once you are in.
 *
 * <ul>
 *   <li>To an outsider it is a recruiter. Speaking to it once explains what swearing in means;
 *       speaking again within a few seconds asks to join, through {@code SectService.join}, which
 *       applies every gate — realm, allegiance, honour — and the envoy says plainly which one
 *       refused you. Two steps, because joining a sect on a stray right-click would be awful.</li>
 *   <li>To a member it teaches the next art their rank entitles them to.</li>
 *   <li>Sneak-right-click asks where you stand, and how far it is to the next rank.</li>
 * </ul>
 *
 * <p>Envoys appear on their own, rarely, and stay where they are. Until sect compounds exist this
 * is the only way a player without operator rights can find a sect at all, so a few have to be out
 * in the world. Each rolls a sect when it spawns and wears its name in that sect's colour.
 *
 * <p>Which sect an individual belongs to is stored on the entity, so a later milestone can place
 * different teachers in different compounds without a new entity type per sect.
 */
public class MartialArtistEntity extends PathfinderMob {

    private static final String SECT_TAG = "Sect";

    /** How far a natural spawn looks for another envoy before refusing. Envoys should be found, not met in pairs. */
    private static final double CROWDING_RADIUS = 128.0D;

    /** How long the offer to swear in stays open after the first conversation, in ticks. */
    private static final int OATH_WINDOW_TICKS = 200;

    /** Who was last told what joining means, and until when they may confirm. Transient on purpose. */
    private UUID oathOfferedTo;
    private long oathExpires;

    /** Which sect this artist serves. Defaults to the Alliance so a spawned one is never inert. */
    private ResourceLocation sect = MurimCultivationMod.id("murim_alliance");

    public MartialArtistEntity(EntityType<? extends MartialArtistEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        // Enough to look alive and not drown. Combat behaviour is a later milestone's job.
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public ResourceLocation sectId() {
        return sect;
    }

    public void setSectId(ResourceLocation sect) {
        this.sect = sect;
        rename();
    }

    /** "Envoy · Murim Alliance", in the sect's colour. */
    private void rename() {
        SectService.byId(level(), sect).ifPresent(value -> setCustomName(
                Component.translatable("murimcultivation.npc.envoy_name", value.displayName())
                        .withColor(value.color())));
    }

    /** Envoys are placed once and stay; one that despawned would take a whole sect's door with it. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /**
     * Spawn rules: solid ground, any light, the config switch, and no other envoy for 128 blocks.
     * Spawn eggs and summons skip the crowding check, since those are placed on purpose.
     */
    public static boolean checkSpawnRules(EntityType<MartialArtistEntity> type, LevelAccessor level,
                                          MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (!MurimConfig.envoyNaturalSpawns() || !Mob.checkMobSpawnRules(type, level, spawnType, pos, random)) {
            return false;
        }
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) {
            return true;
        }
        return level.getEntitiesOfClass(MartialArtistEntity.class,
                new AABB(pos).inflate(CROWDING_RADIUS)).isEmpty();
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        // Sorted so the roll is reproducible for a seed rather than following registry order.
        List<ResourceLocation> sects = level().registryAccess().registryOrThrow(MurimRegistries.SECT)
                .registryKeySet().stream()
                .map(ResourceKey::location)
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
        if (!sects.isEmpty()) {
            setSectId(sects.get(getRandom().nextInt(sects.size())));
        }
        setPersistenceRequired();
        return result;
    }

    /**
     * Talking to a teacher.
     *
     * <p>Teaches the next art your standing with their sect entitles you to, and says plainly
     * why not when it will not. "Nothing happens" is the worst possible response from an NPC,
     * so every branch here produces a sentence.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.sidedSuccess(level().isClientSide());
        }

        Optional<Sect> found = SectService.byId(serverPlayer, sect);
        if (found.isEmpty()) {
            say(serverPlayer, "murimcultivation.npc.no_sect");
            return InteractionResult.CONSUME;
        }

        Sect theirSect = found.get();
        SectRank rank = SectService.rankIn(serverPlayer, sect);
        if (player.isShiftKeyDown()) {
            describeStanding(serverPlayer, theirSect, rank);
            return InteractionResult.CONSUME;
        }
        if (!rank.isMember()) {
            recruit(serverPlayer, theirSect);
            return InteractionResult.CONSUME;
        }

        Optional<ResourceLocation> teachable = theirSect.teachingsUpTo(rank).stream()
                .filter(id -> !CultivationService.data(serverPlayer).knowsTechnique(id))
                .findFirst();

        if (teachable.isEmpty()) {
            SectRank next = rank.next();
            if (next == null) {
                say(serverPlayer, "murimcultivation.npc.nothing_left");
            } else {
                say(serverPlayer, "murimcultivation.npc.rise_further",
                        Component.translatable(next.translationKey()));
            }
            return InteractionResult.CONSUME;
        }

        ResourceLocation id = teachable.get();
        Optional<Technique> technique = TechniqueService.byId(serverPlayer, id);
        if (technique.isEmpty()) {
            // The sect names an art no datapack defines; say so rather than failing silently.
            say(serverPlayer, "murimcultivation.npc.unknown_art");
            return InteractionResult.CONSUME;
        }

        TechniqueService.learn(serverPlayer, id, technique.get());
        say(serverPlayer, "murimcultivation.npc.taught", technique.get().fullDisplayName());
        level().playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.5F, 1.2F);
        return InteractionResult.CONSUME;
    }

    /** The first conversation explains; a second inside the window is the request to join. */
    private void recruit(ServerPlayer player, Sect theirSect) {
        boolean confirming = player.getUUID().equals(oathOfferedTo) && level().getGameTime() <= oathExpires;
        if (!confirming) {
            oathOfferedTo = player.getUUID();
            oathExpires = level().getGameTime() + OATH_WINDOW_TICKS;
            say(player, "murimcultivation.npc.offer_oath", theirSect.fullDisplayName());
            return;
        }

        oathOfferedTo = null;
        SectService.JoinResult result = SectService.join(player, sect);
        if (result.accepted()) {
            say(player, "murimcultivation.npc.welcome", theirSect.fullDisplayName());
            level().playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.5F, 0.9F);
        } else {
            say(player, "murimcultivation.npc.refused", result.message());
        }
    }

    /** Where you stand with this sect, and what the next rank costs. */
    private void describeStanding(ServerPlayer player, Sect theirSect, SectRank rank) {
        int standing = CultivationService.data(player).sectReputation(sect);
        SectRank next = rank.next();
        if (next == null) {
            say(player, "murimcultivation.npc.standing_top", Component.translatable(rank.translationKey()));
        } else {
            say(player, "murimcultivation.npc.standing", Component.translatable(rank.translationKey()),
                    standing, next.reputationRequired(), Component.translatable(next.translationKey()));
        }
    }

    private void say(ServerPlayer player, String key, Object... args) {
        Component[] components = new Component[args.length];
        for (int i = 0; i < args.length; i++) {
            components[i] = args[i] instanceof Component component
                    ? component : Component.literal(String.valueOf(args[i]));
        }
        player.sendSystemMessage(Component.translatable("murimcultivation.npc.prefix",
                getDisplayName(), Component.translatable(key, (Object[]) components)));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(SECT_TAG, sect.toString());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(SECT_TAG)) {
            ResourceLocation parsed = ResourceLocation.tryParse(tag.getString(SECT_TAG));
            if (parsed != null) {
                sect = parsed;
            }
            // Teachers saved before envoys existed carry no name; give them one.
            if (!hasCustomName()) {
                rename();
            }
        }
    }

}
