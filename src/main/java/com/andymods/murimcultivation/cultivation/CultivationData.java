package com.andymods.murimcultivation.cultivation;

import com.andymods.murimcultivation.MurimRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Everything the game remembers about one player's cultivation. Attached to the player via
 * NeoForge's data attachment system, serialized with {@link #CODEC}, and copied across death.
 *
 * <p><strong>Three separate numbers.</strong> {@link #qi()} is the resource techniques burn
 * and that regenerates. {@link #progress()} is long-term advancement toward the next realm and
 * is never spent on techniques. {@link #purity()} is the quality of the foundation and gates
 * breakthrough odds. Collapsing these into one value is what makes a cultivation system unable
 * to have costed techniques.
 *
 * <p><strong>Qi capacity is deliberately not stored here.</strong> It is always derived from the
 * current realm plus open meridians by {@link CultivationService#qiCapacity}. A stored maximum
 * has to be kept in step with the realm on every code path that can change either, and any
 * path that misses becomes a clamp against a stale bound.
 *
 * <p>Meditation state is intentionally transient: it is a moment-to-moment activity, not a
 * property of the character, so it never reaches disk and cannot survive a death or a relog.
 */
public class CultivationData {

    public static final double MIN_PURITY = 0.0D;
    public static final double MAX_PURITY = 100.0D;
    /** A new cultivator's foundation: neither refined nor ruined. */
    public static final double DEFAULT_PURITY = 50.0D;

    private Optional<ResourceKey<Realm>> realm;
    private Substage substage;
    private double qi;
    private double progress;
    private double purity;
    private boolean awakened;
    private final Set<Meridian> openMeridians;
    private final Map<ResourceLocation, Double> techniqueMastery;
    private final List<ResourceLocation> loadout;
    private int statPoints;
    private final Set<ResourceLocation> titles;
    private final Map<ResourceLocation, Integer> sectReputation;
    private DeviationSeverity deviation;
    private int deviationTicks;

    // Transient: never serialized, never copied on death.
    private boolean meditating;
    private int meditationTicks;
    private Vec3 meditationAnchor;
    private final Map<ResourceLocation, Integer> techniqueCooldowns = new HashMap<>();
    private final Map<ResourceLocation, Integer> activeTechniques = new HashMap<>();
    private int selectedSlot;

    public static final Codec<CultivationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(MurimRegistries.REALM).optionalFieldOf("realm").forGetter(CultivationData::realmKey),
            Substage.CODEC.optionalFieldOf("substage", Substage.EARLY).forGetter(CultivationData::substage),
            Codec.DOUBLE.optionalFieldOf("qi", 0.0D).forGetter(CultivationData::qi),
            Codec.DOUBLE.optionalFieldOf("progress", 0.0D).forGetter(CultivationData::progress),
            Codec.doubleRange(MIN_PURITY, MAX_PURITY).optionalFieldOf("purity", DEFAULT_PURITY)
                    .forGetter(CultivationData::purity),
            Codec.BOOL.optionalFieldOf("awakened", false).forGetter(CultivationData::isAwakened),
            Meridian.CODEC.listOf().optionalFieldOf("open_meridians", List.of())
                    .forGetter(data -> List.copyOf(data.openMeridians)),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).optionalFieldOf("technique_mastery", Map.of())
                    .forGetter(data -> Map.copyOf(data.techniqueMastery)),
            ResourceLocation.CODEC.listOf().optionalFieldOf("loadout", List.of())
                    .forGetter(data -> List.copyOf(data.loadout)),
            Codec.INT.optionalFieldOf("stat_points", 0).forGetter(CultivationData::statPoints),
            ResourceLocation.CODEC.listOf().optionalFieldOf("titles", List.of())
                    .forGetter(data -> List.copyOf(data.titles)),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("sect_reputation", Map.of())
                    .forGetter(data -> Map.copyOf(data.sectReputation)),
            DeviationSeverity.CODEC.optionalFieldOf("deviation", DeviationSeverity.NONE)
                    .forGetter(CultivationData::deviation),
            Codec.INT.optionalFieldOf("deviation_ticks", 0).forGetter(CultivationData::deviationTicks)
    ).apply(instance, CultivationData::new));

    /** A fresh, un-awakened cultivator. Used by the attachment's default supplier. */
    public CultivationData() {
        this(Optional.empty(), Substage.EARLY, 0.0D, 0.0D, DEFAULT_PURITY, false,
                List.of(), Map.of(), List.of(), 0, List.of(), Map.of(), DeviationSeverity.NONE, 0);
    }

    public CultivationData(Optional<ResourceKey<Realm>> realm,
                           Substage substage,
                           double qi,
                           double progress,
                           double purity,
                           boolean awakened,
                           List<Meridian> openMeridians,
                           Map<ResourceLocation, Double> techniqueMastery,
                           List<ResourceLocation> loadout,
                           int statPoints,
                           List<ResourceLocation> titles,
                           Map<ResourceLocation, Integer> sectReputation,
                           DeviationSeverity deviation,
                           int deviationTicks) {
        this.realm = realm;
        this.substage = substage;
        this.qi = Math.max(0.0D, qi);
        this.progress = Math.max(0.0D, progress);
        this.purity = clampPurity(purity);
        this.awakened = awakened;
        this.openMeridians = openMeridians.isEmpty() ? EnumSet.noneOf(Meridian.class) : EnumSet.copyOf(openMeridians);
        this.techniqueMastery = new HashMap<>(techniqueMastery);
        this.loadout = new ArrayList<>(loadout);
        this.statPoints = statPoints;
        this.titles = new LinkedHashSet<>(titles);
        this.sectReputation = new HashMap<>(sectReputation);
        this.deviation = deviation;
        this.deviationTicks = Math.max(0, deviationTicks);
    }

    // --- Realm ------------------------------------------------------------------------

    /**
     * The realm the player currently stands in, or empty if they have not been placed on the
     * ladder yet. Resolve it against the registry with
     * {@link CultivationService#realmOf}, which substitutes the lowest realm when empty.
     */
    public Optional<ResourceKey<Realm>> realmKey() {
        return realm;
    }

    public void setRealmKey(ResourceKey<Realm> realm) {
        this.realm = Optional.of(realm);
    }

    public void clearRealmKey() {
        this.realm = Optional.empty();
    }

    public Substage substage() {
        return substage;
    }

    public void setSubstage(Substage substage) {
        this.substage = substage;
    }

    // --- Qi, progress, purity ---------------------------------------------------------

    public double qi() {
        return qi;
    }

    /**
     * Sets current Qi. The caller supplies the capacity because capacity is derived from the
     * realm and meridians rather than stored — see {@link CultivationService#qiCapacity}.
     */
    public void setQi(double qi, double capacity) {
        this.qi = Math.max(0.0D, Math.min(qi, capacity));
    }

    public void addQi(double amount, double capacity) {
        setQi(this.qi + amount, capacity);
    }

    /** Spends Qi if there is enough, and reports whether it went through. */
    public boolean spendQi(double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        if (this.qi < amount) {
            return false;
        }
        this.qi -= amount;
        return true;
    }

    public double progress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = Math.max(0.0D, progress);
    }

    public void addProgress(double amount) {
        setProgress(this.progress + amount);
    }

    public double purity() {
        return purity;
    }

    public void setPurity(double purity) {
        this.purity = clampPurity(purity);
    }

    public void addPurity(double amount) {
        setPurity(this.purity + amount);
    }

    public static double clampPurity(double value) {
        return Math.max(MIN_PURITY, Math.min(MAX_PURITY, value));
    }

    /**
     * Whether the player has learned to sense Qi at all. Before this, they are an ordinary
     * person: no HUD, no cultivation, no techniques.
     */
    public boolean isAwakened() {
        return awakened;
    }

    public void setAwakened(boolean awakened) {
        this.awakened = awakened;
    }

    // --- Meridians --------------------------------------------------------------------

    public boolean isMeridianOpen(Meridian meridian) {
        return openMeridians.contains(meridian);
    }

    /** Opens a meridian. Returns false if it was already open. */
    public boolean openMeridian(Meridian meridian) {
        return openMeridians.add(meridian);
    }

    /** Forces a meridian shut, as a shattered-meridian deviation does. */
    public boolean closeMeridian(Meridian meridian) {
        return openMeridians.remove(meridian);
    }

    public Set<Meridian> openMeridians() {
        return Collections.unmodifiableSet(openMeridians);
    }

    public int openMeridianCount() {
        return openMeridians.size();
    }

    public int openExtraordinaryCount() {
        return (int) openMeridians.stream().filter(Meridian::isExtraordinary).count();
    }

    // --- Techniques -------------------------------------------------------------------

    /** How many techniques can be bound for use at once. */
    public static final int LOADOUT_SIZE = 4;
    public static final double MIN_MASTERY = 0.0D;
    public static final double MAX_MASTERY = 100.0D;

    public boolean knowsTechnique(ResourceLocation technique) {
        return techniqueMastery.containsKey(technique);
    }

    /** Mastery as a whole number, for display and for scaling. */
    public int techniqueMastery(ResourceLocation technique) {
        return (int) Math.floor(techniqueMasteryExact(technique));
    }

    /**
     * Mastery to full precision. Stored continuously because gains diminish as mastery rises:
     * once gains drop below 1.0 per use, an integer store would round them away and mastery
     * would stop advancing short of the maximum.
     */
    public double techniqueMasteryExact(ResourceLocation technique) {
        return techniqueMastery.getOrDefault(technique, 0.0D);
    }

    public void setTechniqueMastery(ResourceLocation technique, int mastery) {
        setTechniqueMasteryExact(technique, mastery);
    }

    public void setTechniqueMasteryExact(ResourceLocation technique, double mastery) {
        techniqueMastery.put(technique, Math.max(MIN_MASTERY, Math.min(MAX_MASTERY, mastery)));
    }

    /** Adds mastery progress, clamped to the maximum. */
    public void addTechniqueMasteryProgress(ResourceLocation technique, double amount) {
        setTechniqueMasteryExact(technique, techniqueMasteryExact(technique) + amount);
    }

    /** Forgets a technique, removing it from the loadout too so no slot points at nothing. */
    public boolean forgetTechnique(ResourceLocation technique) {
        loadout.remove(technique);
        techniqueCooldowns.remove(technique);
        return techniqueMastery.remove(technique) != null;
    }

    public Map<ResourceLocation, Double> techniqueMastery() {
        return Collections.unmodifiableMap(techniqueMastery);
    }

    // --- Loadout ----------------------------------------------------------------------

    public List<ResourceLocation> loadout() {
        return Collections.unmodifiableList(loadout);
    }

    /**
     * Sets the whole loadout, keeping only known techniques and never exceeding
     * {@link #LOADOUT_SIZE}. Slots are dense: there are no holes, so a bound slot always
     * points at something.
     */
    public void setLoadout(Collection<ResourceLocation> techniques) {
        loadout.clear();
        for (ResourceLocation technique : techniques) {
            if (loadout.size() >= LOADOUT_SIZE) {
                break;
            }
            if (knowsTechnique(technique) && !loadout.contains(technique)) {
                loadout.add(technique);
            }
        }
    }

    public Optional<ResourceLocation> techniqueInSlot(int slot) {
        if (slot < 0 || slot >= loadout.size()) {
            return Optional.empty();
        }
        return Optional.of(loadout.get(slot));
    }

    /** Binds a technique to the first free slot, so a newly learned art is usable at once. */
    public boolean assignFirstEmptySlot(ResourceLocation technique) {
        if (loadout.contains(technique) || loadout.size() >= LOADOUT_SIZE) {
            return false;
        }
        return loadout.add(technique);
    }

    /** The slot the cycle key has selected, for players who prefer one cast key. */
    public int selectedSlot() {
        return selectedSlot;
    }

    /** Advances the selection to the next occupied slot, wrapping. */
    public int cycleSelectedSlot() {
        if (loadout.isEmpty()) {
            selectedSlot = 0;
            return selectedSlot;
        }
        selectedSlot = (selectedSlot + 1) % loadout.size();
        return selectedSlot;
    }

    // --- Technique cooldowns (transient) ----------------------------------------------

    public boolean isTechniqueOnCooldown(ResourceLocation technique) {
        return techniqueCooldowns.getOrDefault(technique, 0) > 0;
    }

    public int techniqueCooldown(ResourceLocation technique) {
        return techniqueCooldowns.getOrDefault(technique, 0);
    }

    public void setTechniqueCooldown(ResourceLocation technique, int ticks) {
        if (ticks <= 0) {
            techniqueCooldowns.remove(technique);
        } else {
            techniqueCooldowns.put(technique, ticks);
        }
    }

    public void clearTechniqueCooldown(ResourceLocation technique) {
        techniqueCooldowns.remove(technique);
    }

    /**
     * Counts every cooldown down one tick. Transient like meditation state: a cooldown that
     * survived a relog would be a strange thing to persist.
     */
    public void tickTechniqueCooldowns() {
        if (techniqueCooldowns.isEmpty()) {
            return;
        }
        techniqueCooldowns.replaceAll((technique, ticks) -> ticks - 1);
        techniqueCooldowns.values().removeIf(ticks -> ticks <= 0);
    }

    // --- Active timed techniques (transient) ------------------------------------------

    /** Whether a sustained technique — Sword Force, Iron Body, Qinggong — is currently running. */
    public boolean isTechniqueActive(ResourceLocation technique) {
        return activeTechniques.getOrDefault(technique, 0) > 0;
    }

    public int activeTechniqueTicks(ResourceLocation technique) {
        return activeTechniques.getOrDefault(technique, 0);
    }

    public void setTechniqueActive(ResourceLocation technique, int ticks) {
        if (ticks <= 0) {
            activeTechniques.remove(technique);
        } else {
            activeTechniques.put(technique, ticks);
        }
    }

    public void clearTechniqueActive(ResourceLocation technique) {
        activeTechniques.remove(technique);
    }

    public Set<ResourceLocation> activeTechniques() {
        return Set.copyOf(activeTechniques.keySet());
    }

    public boolean hasAnyTechniqueActive() {
        return !activeTechniques.isEmpty();
    }

    /**
     * Counts active techniques down one tick and reports which just ended, so the caller can
     * undo whatever they were doing. Returns an empty set on the common path.
     */
    public Set<ResourceLocation> tickActiveTechniques() {
        if (activeTechniques.isEmpty()) {
            return Set.of();
        }
        activeTechniques.replaceAll((technique, ticks) -> ticks - 1);

        Set<ResourceLocation> expired = new LinkedHashSet<>();
        activeTechniques.forEach((technique, ticks) -> {
            if (ticks <= 0) {
                expired.add(technique);
            }
        });
        expired.forEach(activeTechniques::remove);
        return expired;
    }

    // --- System progression (populated in M4) -----------------------------------------

    public int statPoints() {
        return statPoints;
    }

    public void setStatPoints(int statPoints) {
        this.statPoints = Math.max(0, statPoints);
    }

    public void addStatPoints(int amount) {
        setStatPoints(this.statPoints + amount);
    }

    public Set<ResourceLocation> titles() {
        return Collections.unmodifiableSet(titles);
    }

    public boolean grantTitle(ResourceLocation title) {
        return titles.add(title);
    }

    // --- Sects (populated in M5) ------------------------------------------------------

    public int sectReputation(ResourceLocation sect) {
        return sectReputation.getOrDefault(sect, 0);
    }

    public void addSectReputation(ResourceLocation sect, int amount) {
        sectReputation.merge(sect, amount, Integer::sum);
    }

    public Map<ResourceLocation, Integer> sectReputation() {
        return Collections.unmodifiableMap(sectReputation);
    }

    // --- Qi deviation -----------------------------------------------------------------

    public DeviationSeverity deviation() {
        return deviation;
    }

    public int deviationTicks() {
        return deviationTicks;
    }

    public void applyDeviation(DeviationSeverity severity) {
        this.deviation = severity;
        this.deviationTicks = severity.durationTicks();
    }

    public void clearDeviation() {
        this.deviation = DeviationSeverity.NONE;
        this.deviationTicks = 0;
    }

    /** Counts a deviation down. Returns true on the tick it finally clears. */
    public boolean tickDeviation() {
        if (!deviation.isActive()) {
            return false;
        }
        if (--deviationTicks <= 0) {
            clearDeviation();
            return true;
        }
        return false;
    }

    // --- Meditation (transient) -------------------------------------------------------

    public boolean isMeditating() {
        return meditating;
    }

    public void setMeditating(boolean meditating) {
        if (this.meditating != meditating) {
            this.meditationTicks = 0;
        }
        this.meditating = meditating;
        if (!meditating) {
            this.meditationAnchor = null;
        }
    }

    /**
     * Where the player settled down, or {@code null} if not meditating. Drifting away from
     * this point interrupts the session — which is how meditation stays still without the
     * game having to freeze the player in place.
     */
    public Vec3 meditationAnchor() {
        return meditationAnchor;
    }

    public void setMeditationAnchor(Vec3 anchor) {
        this.meditationAnchor = anchor;
    }

    public void clearMeditationAnchor() {
        this.meditationAnchor = null;
    }

    /** How long the current meditation has been sustained, in ticks. Longer means faster gain. */
    public int meditationTicks() {
        return meditationTicks;
    }

    public void incrementMeditationTicks() {
        this.meditationTicks++;
    }

    // --- Bulk copy --------------------------------------------------------------------

    /** Overwrites this data with another's persistent state. Transient meditation state is not copied. */
    public void copyFrom(CultivationData source) {
        this.realm = source.realm;
        this.substage = source.substage;
        this.qi = source.qi;
        this.progress = source.progress;
        this.purity = source.purity;
        this.awakened = source.awakened;
        this.openMeridians.clear();
        this.openMeridians.addAll(source.openMeridians);
        this.techniqueMastery.clear();
        this.techniqueMastery.putAll(source.techniqueMastery);
        this.loadout.clear();
        this.loadout.addAll(source.loadout);
        this.statPoints = source.statPoints;
        this.titles.clear();
        this.titles.addAll(source.titles);
        this.sectReputation.clear();
        this.sectReputation.putAll(source.sectReputation);
        this.deviation = source.deviation;
        this.deviationTicks = source.deviationTicks;
    }

    /** Clears all transient combat state. Used on respawn and by {@code /murim reset}. */
    public void clearTransientCombatState() {
        techniqueCooldowns.clear();
        activeTechniques.clear();
        selectedSlot = 0;
    }

    /** Wipes everything back to a fresh, un-awakened cultivator. Used by {@code /murim reset}. */
    public void reset() {
        copyFrom(new CultivationData());
        setMeditating(false);
        clearTransientCombatState();
    }
}
