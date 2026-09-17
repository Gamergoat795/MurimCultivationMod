package com.andymods.murimcultivation.command;

import com.andymods.murimcultivation.MurimCultivationMod;
import com.andymods.murimcultivation.MurimRegistries;
import com.andymods.murimcultivation.item.MartialManualItem;
import com.andymods.murimcultivation.registry.ModItems;
import com.andymods.murimcultivation.sect.Sect;
import com.andymods.murimcultivation.sect.SectRank;
import com.andymods.murimcultivation.sect.SectService;
import com.andymods.murimcultivation.technique.Technique;
import com.andymods.murimcultivation.system.QuestLog;
import com.andymods.murimcultivation.system.QuestObjective;
import com.andymods.murimcultivation.system.QuestTracker;
import com.andymods.murimcultivation.system.StatType;
import com.andymods.murimcultivation.system.SystemQuest;
import com.andymods.murimcultivation.technique.TechniqueService;
import com.andymods.murimcultivation.cultivation.BreakthroughService;
import com.andymods.murimcultivation.cultivation.CultivationData;
import com.andymods.murimcultivation.cultivation.CultivationService;
import com.andymods.murimcultivation.cultivation.DeviationService;
import com.andymods.murimcultivation.cultivation.DeviationSeverity;
import com.andymods.murimcultivation.cultivation.Meridian;
import com.andymods.murimcultivation.cultivation.QiDensity;
import com.andymods.murimcultivation.cultivation.Realm;
import com.andymods.murimcultivation.cultivation.RealmProgression;
import com.andymods.murimcultivation.cultivation.Substage;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /murim} — operator tooling for inspecting and forcing cultivation state.
 *
 * <p>This exists so that the systems built on top of it are testable without grinding: forcing a
 * realm, emptying purity to watch a breakthrough fail, or slamming a meridian shut should all be
 * one command away. Every subcommand acts on the executing player.
 */
@EventBusSubscriber(modid = MurimCultivationMod.MODID)
public final class MurimCommand {

    private static final SuggestionProvider<CommandSourceStack> REALM_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(
                    realmRegistry(context.getSource()).keySet().stream(), builder);

    private static final SuggestionProvider<CommandSourceStack> TECHNIQUE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(
                    context.getSource().registryAccess()
                            .registryOrThrow(MurimRegistries.TECHNIQUE).keySet().stream(), builder);

    private static final SuggestionProvider<CommandSourceStack> QUEST_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(
                    context.getSource().registryAccess()
                            .registryOrThrow(MurimRegistries.QUEST).keySet().stream(), builder);

    private static final SuggestionProvider<CommandSourceStack> SECT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(
                    context.getSource().registryAccess()
                            .registryOrThrow(MurimRegistries.SECT).keySet().stream(), builder);

    private static final SuggestionProvider<CommandSourceStack> MERIDIAN_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    Arrays.stream(Meridian.values()).map(Meridian::getSerializedName), builder);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("murim")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS));

        root.then(Commands.literal("info").executes(MurimCommand::info));

        root.then(Commands.literal("awaken").executes(MurimCommand::awaken));

        root.then(Commands.literal("reset").executes(MurimCommand::reset));

        root.then(Commands.literal("realm")
                .then(Commands.literal("get").executes(MurimCommand::info))
                .then(Commands.literal("set")
                        .then(Commands.argument("realm", ResourceLocationArgument.id())
                                .suggests(REALM_SUGGESTIONS)
                                .executes(context -> setRealm(context, Substage.EARLY))
                                .then(Commands.argument("substage", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(Substage.values()).map(Substage::getSerializedName),
                                                builder))
                                        .executes(context -> setRealm(context, readSubstage(context)))))));

        root.then(Commands.literal("qi")
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D))
                        .executes(MurimCommand::setQi)));

        root.then(Commands.literal("progress")
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D))
                        .executes(MurimCommand::setProgress)));

        root.then(Commands.literal("purity")
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(
                                CultivationData.MIN_PURITY, CultivationData.MAX_PURITY))
                        .executes(MurimCommand::setPurity)));

        root.then(Commands.literal("meridian")
                .then(Commands.literal("open")
                        .then(Commands.argument("meridian", StringArgumentType.word())
                                .suggests(MERIDIAN_SUGGESTIONS)
                                .executes(context -> setMeridian(context, true))))
                .then(Commands.literal("close")
                        .then(Commands.argument("meridian", StringArgumentType.word())
                                .suggests(MERIDIAN_SUGGESTIONS)
                                .executes(context -> setMeridian(context, false))))
                .then(Commands.literal("openall").executes(MurimCommand::openAllMeridians)));

        root.then(Commands.literal("breakthrough").executes(MurimCommand::forceBreakthroughCheck));

        root.then(Commands.literal("chance").executes(MurimCommand::showBreakthroughChance));

        root.then(Commands.literal("quest")
                .then(Commands.literal("list").executes(MurimCommand::listQuests))
                .then(Commands.literal("complete")
                        .then(Commands.argument("quest", ResourceLocationArgument.id())
                                .suggests(QUEST_SUGGESTIONS)
                                .executes(MurimCommand::completeQuest)))
                .then(Commands.literal("reset").executes(MurimCommand::resetQuests)));

        root.then(Commands.literal("stat")
                .then(Commands.literal("grant")
                        .then(Commands.argument("points", IntegerArgumentType.integer(1, 10000))
                                .executes(MurimCommand::grantStatPoints)))
                .then(Commands.literal("spend")
                        .then(Commands.argument("stat", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(StatType.values()).map(StatType::getSerializedName),
                                        builder))
                                .then(Commands.argument("points", IntegerArgumentType.integer(1, 100))
                                        .executes(MurimCommand::spendStatPoints))))
                .then(Commands.literal("respec").executes(MurimCommand::respec)));

        root.then(Commands.literal("technique")
                .then(Commands.literal("learn")
                        .then(Commands.argument("technique", ResourceLocationArgument.id())
                                .suggests(TECHNIQUE_SUGGESTIONS)
                                .executes(MurimCommand::learnTechnique)))
                .then(Commands.literal("forget")
                        .then(Commands.argument("technique", ResourceLocationArgument.id())
                                .suggests(TECHNIQUE_SUGGESTIONS)
                                .executes(MurimCommand::forgetTechnique)))
                .then(Commands.literal("mastery")
                        .then(Commands.argument("technique", ResourceLocationArgument.id())
                                .suggests(TECHNIQUE_SUGGESTIONS)
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                        .executes(MurimCommand::setTechniqueMastery))))
                .then(Commands.literal("manual")
                        .then(Commands.argument("technique", ResourceLocationArgument.id())
                                .suggests(TECHNIQUE_SUGGESTIONS)
                                .executes(MurimCommand::giveManual)))
                .then(Commands.literal("learnall").executes(MurimCommand::learnAllTechniques))
                .then(Commands.literal("list").executes(MurimCommand::listTechniques)));

        root.then(Commands.literal("deviate")
                .then(Commands.argument("severity", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(DeviationSeverity.values())
                                        .map(DeviationSeverity::getSerializedName),
                                builder))
                        .executes(MurimCommand::inflictDeviation)));

        root.then(Commands.literal("cure").executes(MurimCommand::cureDeviation));

        root.then(Commands.literal("sect")
                .then(Commands.literal("list").executes(MurimCommand::listSects))
                .then(Commands.literal("join")
                        .then(Commands.argument("sect", ResourceLocationArgument.id())
                                .suggests(SECT_SUGGESTIONS)
                                .executes(MurimCommand::joinSect)))
                .then(Commands.literal("leave")
                        .then(Commands.argument("sect", ResourceLocationArgument.id())
                                .suggests(SECT_SUGGESTIONS)
                                .executes(MurimCommand::leaveSect)))
                .then(Commands.literal("reputation")
                        .then(Commands.argument("sect", ResourceLocationArgument.id())
                                .suggests(SECT_SUGGESTIONS)
                                .then(Commands.argument("amount", IntegerArgumentType.integer(-100000, 100000))
                                        .executes(MurimCommand::addSectReputation)))));

        dispatcher.register(root);
    }

    // --- Subcommands ------------------------------------------------------------------

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CultivationData data = CultivationService.data(player);
        Registry<Realm> registry = CultivationService.realmRegistry(player);
        Optional<Realm> realm = CultivationService.realmOf(registry, data);

        send(context, Component.literal("--- Cultivation ---"));
        send(context, Component.literal("Awakened: " + data.isAwakened()));
        send(context, Component.literal("Realm: ")
                .append(realm.map(Realm::fullDisplayName).orElse(Component.literal("<none>")))
                .append(Component.literal(" (" + data.substage().getSerializedName() + ")")));
        send(context, Component.literal(String.format(Locale.ROOT, "Qi: %.1f / %.1f",
                data.qi(), CultivationService.qiCapacity(registry, data))));
        send(context, Component.literal(String.format(Locale.ROOT, "Progress: %.1f / %.1f",
                data.progress(), CultivationService.progressForNextStep(registry, data))));
        send(context, Component.literal(String.format(Locale.ROOT, "Purity: %.1f", data.purity())));
        send(context, Component.literal("Meridians: " + data.openMeridianCount() + "/" + Meridian.count()
                + " (" + data.openExtraordinaryCount() + " extraordinary)"));
        send(context, Component.literal("Deviation: " + data.deviation().getSerializedName()
                + (data.deviation().isActive() ? " (" + data.deviationTicks() + " ticks left)" : "")));
        send(context, Component.literal("Stat points: " + data.systemProgress().unspentPoints()
                + " unspent, " + data.systemProgress().totalSpent() + " spent "
                + data.systemProgress().allocations()));
        send(context, Component.literal("Titles: " + data.systemProgress().titles().size()
                + ", worn: " + data.systemProgress().equippedTitle().map(Object::toString).orElse("none")));
        send(context, Component.literal("Sects: " + data.sectReputation().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> entry.getKey().getPath() + " "
                        + SectRank.forReputation(entry.getValue()).getSerializedName())
                .toList()));
        send(context, Component.literal("Quests: " + data.questLog().completed().size() + " completed, "
                + QuestTracker.available(player).size() + " available"));
        send(context, Component.literal("Breakthrough: " + BreakthroughService.check(player).name()));
        send(context, Component.literal(String.format(Locale.ROOT, "Ambient Qi: %.2fx (%s)",
                QiDensity.multiplierFor(player),
                QiDensity.qualityOf(QiDensity.multiplierFor(player)).name())));
        BreakthroughService.successChanceForNextRealm(player).ifPresent(chance ->
                send(context, Component.literal(String.format(Locale.ROOT,
                        "Breakthrough chance: %.1f%%", chance * 100.0D))));
        return 1;
    }

    private static int awaken(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CultivationData data = CultivationService.data(player);
        Registry<Realm> registry = CultivationService.realmRegistry(player);

        Optional<Holder.Reference<Realm>> lowest = RealmProgression.lowest(registry);
        if (lowest.isEmpty()) {
            send(context, Component.literal("No realms are defined; check your datapack."));
            return 0;
        }

        data.setAwakened(true);
        CultivationService.setRealm(player, lowest.get().key(), Substage.EARLY);
        send(context, Component.literal("Awakened at ").append(lowest.get().value().fullDisplayName()));
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CultivationService.data(player).reset();
        CultivationService.applyRealmAttributes(player);
        CultivationService.syncToClient(player);
        send(context, Component.literal("Cultivation reset."));
        return 1;
    }

    private static int setRealm(CommandContext<CommandSourceStack> context, Substage substage)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, "realm");
        Registry<Realm> registry = CultivationService.realmRegistry(player);
        ResourceKey<Realm> key = ResourceKey.create(MurimRegistries.REALM, id);

        if (registry.getOptional(key).isEmpty()) {
            send(context, Component.literal("Unknown realm: " + id));
            return 0;
        }

        CultivationService.data(player).setAwakened(true);
        CultivationService.setRealm(player, key, substage);
        send(context, Component.literal("Realm set to " + id + " (" + substage.getSerializedName() + ")"));
        return 1;
    }

    private static int setQi(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        double amount = DoubleArgumentType.getDouble(context, "amount");
        CultivationData data = CultivationService.data(player);
        data.setQi(amount, CultivationService.qiCapacity(player));
        CultivationService.syncValuesToClient(player);
        send(context, Component.literal(String.format(Locale.ROOT, "Qi set to %.1f", data.qi())));
        return 1;
    }

    private static int setProgress(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CultivationData data = CultivationService.data(player);
        data.setProgress(DoubleArgumentType.getDouble(context, "amount"));
        // Banking progress can immediately clear one or more substages.
        CultivationService.advanceSubstages(player);
        CultivationService.syncValuesToClient(player);
        send(context, Component.literal(String.format(Locale.ROOT, "Progress set to %.1f", data.progress())));
        return 1;
    }

    private static int setPurity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CultivationData data = CultivationService.data(player);
        data.setPurity(DoubleArgumentType.getDouble(context, "amount"));
        CultivationService.syncValuesToClient(player);
        send(context, Component.literal(String.format(Locale.ROOT, "Purity set to %.1f", data.purity())));
        return 1;
    }

    private static int setMeridian(CommandContext<CommandSourceStack> context, boolean open)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "meridian");
        Optional<Meridian> meridian = parseMeridian(name);
        if (meridian.isEmpty()) {
            send(context, Component.literal("Unknown meridian: " + name));
            return 0;
        }

        CultivationData data = CultivationService.data(player);
        boolean changed = open ? data.openMeridian(meridian.get()) : data.closeMeridian(meridian.get());
        if (changed) {
            // The meridian network moves the Qi ceiling, so re-clamp and resync.
            CultivationService.clampQiToCapacity(player, data);
            CultivationService.syncToClient(player);
        }
        send(context, Component.literal(name + (open ? " opened" : " closed")
                + (changed ? "" : " (no change)")));
        return 1;
    }

    private static int openAllMeridians(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CultivationData data = CultivationService.data(player);
        for (Meridian meridian : Meridian.values()) {
            data.openMeridian(meridian);
        }
        CultivationService.clampQiToCapacity(player, data);
        CultivationService.syncToClient(player);
        send(context, Component.literal("All " + Meridian.count() + " meridians opened."));
        return 1;
    }

    private static int forceBreakthroughCheck(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BreakthroughService.Eligibility eligibility = BreakthroughService.check(player);
        if (!eligibility.isReady()) {
            send(context, Component.literal("Refused: " + eligibility.name()).append(" — ")
                    .append(eligibility.message()));
            return 0;
        }
        BreakthroughService.attempt(player);
        return 1;
    }

    private static int showBreakthroughChance(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<Double> chance = BreakthroughService.successChanceForNextRealm(player);
        if (chance.isEmpty()) {
            send(context, Component.literal("No next realm; nothing to break through to."));
            return 0;
        }
        send(context, Component.literal(String.format(Locale.ROOT,
                "Breakthrough chance: %.1f%%  (ambient Qi %.2fx, would fail as %s)",
                chance.get() * 100.0D,
                QiDensity.multiplierFor(player),
                BreakthroughService.severityForFailedAttempt(chance.get()).getSerializedName())));
        return 1;
    }

    private static int inflictDeviation(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "severity").toLowerCase(Locale.ROOT);
        Optional<DeviationSeverity> severity = Arrays.stream(DeviationSeverity.values())
                .filter(value -> value.getSerializedName().equals(name))
                .findFirst();

        if (severity.isEmpty()) {
            send(context, Component.literal("Unknown severity: " + name));
            return 0;
        }
        if (!severity.get().isActive()) {
            DeviationService.cure(player);
            send(context, Component.literal("Deviation cleared."));
            return 1;
        }

        DeviationService.Outcome outcome = DeviationService.inflict(player, severity.get());
        send(context, Component.literal(String.format(Locale.ROOT,
                "Inflicted %s: lost %.1f progress%s",
                outcome.severity().getSerializedName(),
                outcome.progressLost(),
                outcome.shatteredAMeridian() ? ", shattered " + outcome.meridianClosed().getSerializedName() : "")));
        return 1;
    }

    private static int cureDeviation(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean cured = DeviationService.cure(player);
        send(context, Component.literal(cured ? "Deviation cured." : "No active deviation."));
        return cured ? 1 : 0;
    }

    private static int learnTechnique(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, "technique");
        Optional<Technique> technique = TechniqueService.byId(player, id);
        if (technique.isEmpty()) {
            send(context, Component.literal("Unknown technique: " + id));
            return 0;
        }

        CultivationService.data(player).setAwakened(true);
        boolean learned = TechniqueService.learn(player, id, technique.get());
        send(context, Component.literal(learned ? "Learned " + id : "Already knows " + id));
        return learned ? 1 : 0;
    }

    private static int forgetTechnique(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, "technique");
        boolean forgotten = CultivationService.data(player).forgetTechnique(id);
        CultivationService.syncToClient(player);
        send(context, Component.literal(forgotten ? "Forgot " + id : "Did not know " + id));
        return forgotten ? 1 : 0;
    }

    private static int setTechniqueMastery(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, "technique");
        CultivationData data = CultivationService.data(player);
        if (!data.knowsTechnique(id)) {
            send(context, Component.literal("Does not know " + id + "; learn it first."));
            return 0;
        }

        int value = IntegerArgumentType.getInteger(context, "value");
        data.setTechniqueMastery(id, value);
        CultivationService.syncToClient(player);
        send(context, Component.literal("Mastery of " + id + " set to " + value));
        return 1;
    }

    private static int giveManual(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, "technique");
        if (TechniqueService.byId(player, id).isEmpty()) {
            send(context, Component.literal("Unknown technique: " + id));
            return 0;
        }

        ItemStack manual = MartialManualItem.forTechnique(ModItems.MARTIAL_MANUAL.get(), id);
        if (!player.getInventory().add(manual)) {
            player.drop(manual, false);
        }
        send(context, Component.literal("Gave a manual for " + id));
        return 1;
    }

    private static int learnAllTechniques(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CultivationService.data(player).setAwakened(true);

        int learned = 0;
        for (var entry : TechniqueService.registry(player).entrySet()) {
            if (TechniqueService.learn(player, entry.getKey().location(), entry.getValue())) {
                learned++;
            }
        }
        send(context, Component.literal("Learned " + learned + " new technique(s)."));
        return learned;
    }

    private static int listTechniques(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CultivationData data = CultivationService.data(player);

        send(context, Component.literal("--- Techniques ---"));
        if (data.techniqueMastery().isEmpty()) {
            send(context, Component.literal("(none learned)"));
            return 0;
        }

        data.techniqueMastery().forEach((id, mastery) -> {
            int slot = data.loadout().indexOf(id);
            send(context, Component.literal(String.format(Locale.ROOT, "  %s  mastery %.1f  %s",
                    id, mastery, slot >= 0 ? "slot " + (slot + 1) : "unbound")));
        });
        return data.techniqueMastery().size();
    }

    private static int listQuests(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CultivationData data = CultivationService.data(player);

        send(context, Component.literal("--- Quests ---"));
        List<ResourceLocation> available = QuestTracker.available(player);
        if (available.isEmpty()) {
            send(context, Component.literal("(nothing available)"));
        }
        for (ResourceLocation id : available) {
            SystemQuest quest = QuestTracker.registry(player)
                    .get(ResourceKey.create(MurimRegistries.QUEST, id));
            if (quest == null) {
                continue;
            }
            send(context, Component.literal("  " + id + " [" + quest.category().getSerializedName() + "]"));
            for (QuestObjective objective : quest.objectives()) {
                int current = QuestTracker.currentValue(player, data, id, objective);
                send(context, Component.literal(String.format(Locale.ROOT, "      %s/%s ",
                        current, objective.amount())).append(objective.description()));
            }
        }
        send(context, Component.literal("Completed: " + data.questLog().completed().size()
                + ", dailies claimed: " + data.questLog().claimedDailies().size()));
        return available.size();
    }

    private static int completeQuest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, "quest");
        SystemQuest quest = QuestTracker.registry(player)
                .get(ResourceKey.create(MurimRegistries.QUEST, id));
        if (quest == null) {
            send(context, Component.literal("Unknown quest: " + id));
            return 0;
        }

        // Force every objective to its target so the real completion path runs, rewards
        // included — a shortcut that skipped it would not be testing anything.
        CultivationData data = CultivationService.data(player);
        for (QuestObjective objective : quest.objectives()) {
            if (objective.kind().isCumulative()) {
                data.questLog().advance(id, objective, objective.amount());
            }
        }
        QuestTracker.evaluate(player);
        send(context, Component.literal(data.questLog().isCompleted(id)
                || data.questLog().isDailyClaimed(id)
                ? "Completed " + id
                : "Objectives filled, but " + id + " still has unmet thresholds (realm, purity or meridians)"));
        return 1;
    }

    private static int resetQuests(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CultivationData data = CultivationService.data(player);
        data.questLog().copyFrom(new QuestLog());
        CultivationService.syncToClient(player);
        send(context, Component.literal("Quest log cleared."));
        return 1;
    }

    private static int grantStatPoints(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int points = IntegerArgumentType.getInteger(context, "points");
        CultivationService.data(player).systemProgress().grantPoints(points);
        CultivationService.syncToClient(player);
        send(context, Component.literal("Granted " + points + " stat point(s)."));
        return points;
    }

    private static int spendStatPoints(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "stat").toLowerCase(Locale.ROOT);
        Optional<StatType> stat = Arrays.stream(StatType.values())
                .filter(value -> value.getSerializedName().equals(name))
                .findFirst();
        if (stat.isEmpty()) {
            send(context, Component.literal("Unknown stat: " + name));
            return 0;
        }

        int points = IntegerArgumentType.getInteger(context, "points");
        if (!CultivationService.data(player).systemProgress().spend(stat.get(), points)) {
            send(context, Component.literal("Cannot spend " + points + " on " + name
                    + " (not enough points, or the stat is capped)."));
            return 0;
        }
        CultivationService.applyAttributes(player);
        CultivationService.syncToClient(player);
        send(context, Component.literal("Spent " + points + " on " + name + "."));
        return 1;
    }

    private static int respec(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int refunded = CultivationService.data(player).systemProgress().refundAll();
        CultivationService.applyAttributes(player);
        CultivationService.syncToClient(player);
        send(context, Component.literal("Refunded " + refunded + " stat point(s)."));
        return refunded;
    }

    private static int listSects(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CultivationData data = CultivationService.data(player);

        send(context, Component.literal("--- Sects ---"));
        for (var entry : SectService.registry(player).entrySet()) {
            ResourceLocation id = entry.getKey().location();
            Sect sect = entry.getValue();
            int reputation = data.sectReputation(id);
            SectRank rank = SectRank.forReputation(reputation);
            SectRank next = rank.next();
            String toNext = next == null ? "" : " (next at " + next.reputationRequired() + ")";
            send(context, Component.literal(String.format(Locale.ROOT, "  %s [%s]  rep %s, %s%s",
                    id, sect.alignment().getSerializedName(), reputation,
                    rank.getSerializedName(), toNext)));
        }
        return 1;
    }

    private static int joinSect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, "sect");
        SectService.JoinResult result = SectService.join(player, id);
        send(context, result.accepted()
                ? Component.literal("Joined " + id)
                : Component.literal("Refused: " + result.name() + " — ").append(result.message()));
        return result.accepted() ? 1 : 0;
    }

    private static int leaveSect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, "sect");
        boolean left = SectService.leave(player, id);
        send(context, Component.literal(left ? "Left " + id : "No standing with " + id));
        return left ? 1 : 0;
    }

    private static int addSectReputation(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, "sect");
        if (SectService.byId(player, id).isEmpty()) {
            send(context, Component.literal("Unknown sect: " + id));
            return 0;
        }

        int amount = IntegerArgumentType.getInteger(context, "amount");
        SectRank rank = SectService.addReputation(player, id, amount);
        send(context, Component.literal("Reputation with " + id + " now "
                + CultivationService.data(player).sectReputation(id)
                + " (" + rank.getSerializedName() + ")"));
        return 1;
    }

    // --- Helpers ----------------------------------------------------------------------

    private static Substage readSubstage(CommandContext<CommandSourceStack> context) {
        String raw = StringArgumentType.getString(context, "substage").toLowerCase(Locale.ROOT);
        return Arrays.stream(Substage.values())
                .filter(substage -> substage.getSerializedName().equals(raw))
                .findFirst()
                .orElse(Substage.EARLY);
    }

    private static Optional<Meridian> parseMeridian(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return Arrays.stream(Meridian.values())
                .filter(meridian -> meridian.getSerializedName().equals(normalized))
                .findFirst();
    }

    private static Registry<Realm> realmRegistry(CommandSourceStack source) {
        return RealmProgression.registry(source.registryAccess());
    }

    private static void send(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendSuccess(() -> message, false);
    }

    private MurimCommand() {
    }
}
