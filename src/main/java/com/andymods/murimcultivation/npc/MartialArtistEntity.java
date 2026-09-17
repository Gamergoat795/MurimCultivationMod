package com.andymods.murimcultivation.npc;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.sect.Sect;
import com.andymods.murimcultivation.sect.SectRank;
import com.andymods.murimcultivation.sect.SectService;
import com.andymods.murimcultivation.technique.Technique;
import com.andymods.murimcultivation.technique.TechniqueService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * A martial artist of some sect — for now, a teacher.
 *
 * <p><strong>A skeleton, and the seam that makes the sect system reachable.</strong> Sects
 * declare which arts they teach at which rank, and until now nothing could act on that. Talking
 * to one of these teaches you the next art your standing entitles you to. What is missing is
 * everything else: sparring, duels, sect quests, wandering, hostility between alignments, and
 * any way to find one outside creative mode.
 *
 * <p>Which sect an individual belongs to is stored on the entity, so a later milestone can place
 * different teachers in different compounds without a new entity type per sect.
 */
public class MartialArtistEntity extends PathfinderMob {

    private static final String SECT_TAG = "Sect";

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
    }

}
