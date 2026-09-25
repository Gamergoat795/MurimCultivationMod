package com.andymods.murimcultivation.npc;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.MurimRegistries;
import com.andymods.murimcultivation.cultivation.AttributeGrant;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.Realm;
import com.andymods.murimcultivation.cultivation.RealmProgression;
import com.andymods.murimcultivation.sect.Sect;
import com.andymods.murimcultivation.sect.SectRank;
import com.andymods.murimcultivation.sect.SectService;
import com.andymods.murimcultivation.technique.HandRequirement;
import com.andymods.murimcultivation.technique.Technique;
import com.andymods.murimcultivation.technique.TechniqueBehaviours;
import com.andymods.murimcultivation.technique.TechniqueService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.List;
import java.util.Optional;

/**
 * A martial artist of some sect.
 *
 * <p>The seam that makes the sect system reachable. Sects declare which arts they teach at
 * which rank, and talking to one of these teaches you the next art your standing entitles you
 * to. They spawn naturally in the biomes a sect's JSON claims as its {@code territory}, each at
 * a realm rolled from the sect's {@code realm_range} and carrying that realm's attribute
 * bonuses, so a Peak elder of the Cult is a genuinely different fight from a Third-Rate Alliance
 * disciple.
 *
 * <p>Which sect an individual belongs to is stored on the entity, so one entity type serves
 * every sect a datapack defines.
 */
public class MartialArtistEntity extends PathfinderMob {

    private static final String SECT_TAG = "Sect";
    private static final String REALM_TIER_TAG = "RealmTier";

    /** Which sect this artist serves. Defaults to the Alliance so a spawned one is never inert. */
    private ResourceLocation sect = MurimCultivationMod.id("murim_alliance");

    /** The realm tier this artist has reached, which sets its strength and what it knows. */
    private int realmTier = 1;

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
    }

    public int realmTier() {
        return realmTier;
    }

    // --- Spawning ---------------------------------------------------------------------

    /**
     * The spawn rule registered for natural spawning: ordinary ground-mob rules, and only in a
     * biome some sect claims. The biome modifier already restricts spawns to the union tag, but
     * a datapack can edit a sect's territory without touching that tag; this keeps a spawn from
     * ever producing an artist with no sect to belong to.
     */
    public static boolean checkSpawnRules(EntityType<MartialArtistEntity> type, ServerLevelAccessor level,
                                          MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return Mob.checkMobSpawnRules(type, level, spawnType, pos, random)
                && !SectService.claimants(level.registryAccess(), level.getBiome(pos)).isEmpty();
    }

    /**
     * Carries the sect chosen for the first artist of a spawn group to the rest, so a group is
     * always one sect even where two territories overlap.
     */
    private record SectGroup(ResourceLocation sect) implements SpawnGroupData {
    }

    /**
     * Gives a newly spawned artist its sect, realm and gear.
     *
     * <p>Runs for spawn eggs and {@code /summon} too. In a biome no sect claims, the artist
     * keeps whatever sect it already has, so an egg used anywhere still yields a working teacher.
     */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);

        if (groupData instanceof SectGroup group) {
            sect = group.sect();
        } else {
            List<ResourceLocation> claimants =
                    SectService.claimants(level.registryAccess(), level.getBiome(blockPosition()));
            if (!claimants.isEmpty()) {
                sect = claimants.get(getRandom().nextInt(claimants.size()));
            }
            data = new SectGroup(sect);
        }

        Optional<Sect> found = level.registryAccess().registryOrThrow(MurimRegistries.SECT)
                .getOptional(sect);
        found.ifPresent(theirSect -> {
            Sect.RealmRange range = theirSect.realmRange();
            realmTier = range.min() + getRandom().nextInt(range.max() - range.min() + 1);
            applyRealm(level);
            equipFor(theirSect, level);
        });
        return data;
    }

    /**
     * Applies the realm's attribute bonuses, straight from its JSON, so an artist at a realm is
     * exactly as much stronger as a player at that realm would be. Permanent modifiers, so they
     * are saved with the entity and survive a reload.
     */
    private void applyRealm(ServerLevelAccessor level) {
        RealmProgression.atOrBelow(RealmProgression.registry(level.registryAccess()), realmTier)
                .ifPresent(holder -> {
                    Realm realm = holder.value();
                    realmTier = realm.tier();
                    for (AttributeGrant grant : realm.attributes()) {
                        AttributeInstance instance = getAttribute(grant.attribute());
                        if (instance != null) {
                            instance.addOrReplacePermanentModifier(new AttributeModifier(
                                    CultivationService.realmModifierId(grant.attribute()),
                                    grant.amount(), grant.operation()));
                        }
                    }
                });
        setHealth(getMaxHealth());
    }

    /**
     * Arms the artist for the arts its sect teaches: a sword when those arts need one, bare
     * hands when any of them is a palm art that a blade would block. Robes are leather dyed in
     * the sect's colour, and never drop, so a sect's uniform is not a farmable item.
     */
    private void equipFor(Sect theirSect, ServerLevelAccessor level) {
        List<HandRequirement> grips = theirSect.teachingsUpTo(SectRank.PATRIARCH).stream()
                .flatMap(id -> level.registryAccess().registryOrThrow(MurimRegistries.TECHNIQUE)
                        .getOptional(id).stream())
                .filter(technique -> TechniqueBehaviours.npcUsable(technique.behaviour()))
                .map(Technique::hand)
                .toList();
        boolean wantsSword = grips.contains(HandRequirement.SWORD) && !grips.contains(HandRequirement.FREE_HAND);
        setItemSlot(EquipmentSlot.MAINHAND, wantsSword ? new ItemStack(Items.IRON_SWORD) : ItemStack.EMPTY);

        setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, theirSect.color()));
        setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, theirSect.color()));
        setDropChance(EquipmentSlot.CHEST, 0.0F);
        setDropChance(EquipmentSlot.LEGS, 0.0F);
    }

    private static ItemStack dyed(Item item, int color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, false));
        return stack;
    }

    /** A teacher you found stays where you found them. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
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
        if (!rank.isMember()) {
            say(serverPlayer, "murimcultivation.npc.not_a_member", theirSect.fullDisplayName());
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
        tag.putInt(REALM_TIER_TAG, realmTier);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(SECT_TAG)) {
            ResourceLocation parsed = ResourceLocation.tryParse(tag.getString(SECT_TAG));
            if (parsed != null) {
                sect = parsed;
            }
        }
        if (tag.contains(REALM_TIER_TAG)) {
            realmTier = Math.max(1, tag.getInt(REALM_TIER_TAG));
        }
    }

}
