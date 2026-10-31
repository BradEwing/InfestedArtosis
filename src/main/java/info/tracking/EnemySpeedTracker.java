package info.tracking;

import bwapi.UnitType;
import bwapi.UpgradeType;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Tracks whether the enemy has the movement upgrade of a unit type: Metabolic Boost for Zerglings and Muscular
 * Augments for Hydralisks.
 *
 * <p>Each upgrade has an earliest finish frame, when the enemy can have finished it given what we observed, see
 * {@link #earliestFinishFrame}. From that frame the upgrade is {@link Evidence#PLAUSIBLE}. It is
 * {@link Evidence#SEEN} once one unit covered more ground than its type can unupgraded over a run of two windows
 * that starts at or after the sound earliest finish, see {@link SpeedRunDetector} and
 * {@link #soundEarliestFinishFrame}. A run that ends before the sound earliest finish is kept as an early run and
 * never makes the upgrade SEEN. SEEN is
 * permanent; PLAUSIBLE follows the earliest finish, which moves later as observations rule earlier finishes out.
 *
 * <p>An earliest finish is the research start plus {@link UpgradeType#upgradeTime()}. The research starts no earlier
 * than the frame the researching structure is ready, and than the frame the gas spent up to the research can have
 * been mined at {@link #GAS_PER_FRAME} per Extractor from each Extractor's ready frame. An Extractor seen under
 * construction is ready when its remaining build time runs out, and one destroyed before it is ready never counts.
 * An Extractor first seen finished is ready, for the earliest finish, on the frame it was first seen, and for the
 * sound earliest finish, and whenever a trusted gas reading of it exists, at its build time from the start of the
 * game. With no Extractor observed, one Extractor finished at its build time from the start of the game is assumed.
 * A gas reading, the gas mined from the only Extractor observed, is trusted once the Extractor's mined gas has been
 * read changing; a trusted reading rules out every frame before the remaining gas can be mined. A structure seen
 * under construction or morphing is ready when its remaining build time runs out; otherwise it is ready no earlier
 * than its prerequisites, its gas and its build time allow from the start of the game.
 *
 * <p>Metabolic Boost is researched at the Spawning Pool for its own gas, or for its own and a Lair's when a Lair began
 * before the two could have been mined, since the Lair's gas was then spent first. Muscular Augments is researched at
 * the Hydralisk Den, whose gas is spent before it.
 */
public class EnemySpeedTracker {
    /** The most gas one Extractor is taken to yield per frame. */
    public static final double GAS_PER_FRAME = 0.25;

    /** How far the earliest finish moves, with the same anchor, before it is reported again. */
    public static final int PLAUSIBLE_REPORT_FRAMES = 120;

    private static final double FRAME_EPSILON = 1e-9;

    private static final UpgradeType[] FOLLOWED = {UpgradeType.Metabolic_Boost, UpgradeType.Muscular_Augments};

    /** What is known about an enemy upgrade. */
    public enum Evidence {
        /** Before the earliest finish and not seen. */
        NONE,
        /** At or after the earliest finish, not seen. */
        PLAUSIBLE,
        /** A unit was seen covering more ground than the unupgraded top speed allows over a whole run. */
        SEEN
    }

    /** What the earliest finish rests on. */
    public enum Anchor {
        /** No observation the bound rests on: the start of the game. */
        FLOOR,
        /** The ready frames of the enemy Extractors observed. */
        EXTRACTOR,
        /** A gas reading of the enemy's only observed Extractor. */
        GAS_READING,
        /** The ready frame of the researching or a required structure, seen under construction. */
        RESEARCHER
    }

    /** What a report is about. */
    public enum Event {
        /** The frame reached the earliest finish, or its anchor changed or it moved far since the last report. */
        PLAUSIBLE,
        /** A run made the upgrade SEEN. */
        SEEN,
        /** A run ended before the earliest finish and was refused. */
        EARLY_RUN
    }

    /** An event reported for an upgrade. */
    @Value
    public static class Report {
        Event event;
        UpgradeType upgrade;
        /** Distance covered per frame over the slower of the run's two windows, or -1 when no run applies. */
        double measuredSpeed;
        /** The type's unupgraded top speed, or -1 when no run applies. */
        double topSpeed;
        /** Frames the run spans, or -1 when no run applies. */
        int runFrames;
        /** The earliest finish on the reported frame; for a run, the bound it was held to. */
        int boundFrame;
        /** What the earliest finish rested on, or null for a run of our own units. */
        Anchor anchor;
    }

    private static final class Structure {
        final UnitType type;
        final int readyFrame;
        final boolean exact;
        boolean completed;

        Structure(UnitType type, int readyFrame, boolean exact, boolean completed) {
            this.type = type;
            this.readyFrame = readyFrame;
            this.exact = exact;
            this.completed = completed;
        }
    }

    private static final class Bound {
        final int frame;
        final Anchor anchor;

        Bound(int frame, Anchor anchor) {
            this.frame = frame;
            this.anchor = anchor;
        }
    }

    private final SpeedRunDetector detector = new SpeedRunDetector(this::soundEarliestFinishFrame);
    private final Map<Integer, Structure> structures = new HashMap<>();
    private final Map<Integer, NavigableMap<Integer, Integer>> gasReadings = new HashMap<>();
    private final Map<Integer, Integer> firstMined = new HashMap<>();
    private final Set<Integer> trustedReadings = new HashSet<>();
    private final Map<UpgradeType, Bound> bounds = new HashMap<>();
    private final Map<UpgradeType, Bound> soundBounds = new HashMap<>();
    private final Map<UpgradeType, Set<Event>> reported = new HashMap<>();
    private final Map<UpgradeType, Bound> plausibleReported = new HashMap<>();
    private final Set<UpgradeType> boundsCrossed = new HashSet<>();
    private boolean boundsStale = true;

    /**
     * The upgrade that speeds up a unit type, among those this tracker follows.
     *
     * @param type the unit type
     * @return Metabolic Boost for Zerglings, Muscular Augments for Hydralisks, else null
     */
    public static UpgradeType upgradeFor(UnitType type) {
        if (type == UnitType.Zerg_Zergling) {
            return UpgradeType.Metabolic_Boost;
        }
        if (type == UnitType.Zerg_Hydralisk) {
            return UpgradeType.Muscular_Augments;
        }
        return null;
    }

    /**
     * The unit type an upgrade speeds up, among those this tracker follows.
     *
     * @param upgrade the upgrade
     * @return the unit type, or null for an upgrade not followed
     */
    public static UnitType unitFor(UpgradeType upgrade) {
        if (upgrade == UpgradeType.Metabolic_Boost) {
            return UnitType.Zerg_Zergling;
        }
        if (upgrade == UpgradeType.Muscular_Augments) {
            return UnitType.Zerg_Hydralisk;
        }
        return null;
    }

    /**
     * Whether the tracker reads the timing of a structure type: the Extractor, the Spawning Pool, the Hydralisk Den
     * and the Lair.
     *
     * @param type the unit type
     * @return true for a structure the earliest finish depends on
     */
    public static boolean followsStructure(UnitType type) {
        return type == UnitType.Zerg_Extractor || type == UnitType.Zerg_Spawning_Pool
                || type == UnitType.Zerg_Hydralisk_Den || type == UnitType.Zerg_Lair;
    }

    /**
     * The earliest frame Extractors yield an amount of gas between them, each mining {@link #GAS_PER_FRAME} from
     * its ready frame.
     *
     * @param readyFrames each Extractor's ready frame
     * @param gas the gas mined
     * @return the frame, or -1 with no Extractor
     */
    public static int earliestGasFrame(List<Integer> readyFrames, int gas) {
        List<Integer> sorted = new ArrayList<>(readyFrames);
        Collections.sort(sorted);
        double sum = 0;
        for (int k = 0; k < sorted.size(); k++) {
            sum += sorted.get(k);
            double frame = (Math.max(gas, 0) / GAS_PER_FRAME + sum) / (k + 1);
            if (k + 1 == sorted.size() || frame <= sorted.get(k + 1)) {
                return (int) Math.ceil(frame - FRAME_EPSILON);
            }
        }
        return -1;
    }

    /**
     * The earliest frame an amount of gas can have been mined from one Extractor given readings of the gas mined
     * from it: each reading below the amount rules out every frame before the rest can be mined.
     *
     * @param readings gas mined, mapped to the last frame it was read
     * @param gas the gas mined
     * @return the frame, or -1 when no reading is below the amount
     */
    public static int readingBound(NavigableMap<Integer, Integer> readings, int gas) {
        int bound = -1;
        for (Map.Entry<Integer, Integer> reading : readings.headMap(gas, false).entrySet()) {
            int frame = reading.getValue() + (int) Math.ceil((gas - reading.getKey()) / GAS_PER_FRAME - FRAME_EPSILON);
            bound = Math.max(bound, frame);
        }
        return bound;
    }

    /**
     * Whether a Lair's gas was spent before Metabolic Boost's: true when the Lair began before the gas of both could
     * have been mined, since the research beginning first would have needed both by the Lair's start.
     *
     * @param lairStartUpper latest frame the first Lair can have begun, or -1 with no Lair observed
     * @param bothGasFrame earliest frame the gas of both can have been mined
     * @return true when the Lair's gas counts toward the research
     */
    public static boolean lairGasFirst(int lairStartUpper, int bothGasFrame) {
        return lairStartUpper >= 0 && lairStartUpper < bothGasFrame;
    }

    /**
     * What the frames recorded say about an upgrade at a frame.
     *
     * @param seenFrame frame the upgrade was first seen, or -1
     * @param earliestFinish earliest finish frame
     * @param frame the frame asked about
     * @return SEEN once seen, PLAUSIBLE from the earliest finish, else NONE
     */
    public static Evidence classify(int seenFrame, int earliestFinish, int frame) {
        if (seenFrame >= 0 && frame >= seenFrame) {
            return Evidence.SEEN;
        }
        if (earliestFinish >= 0 && frame >= earliestFinish) {
            return Evidence.PLAUSIBLE;
        }
        return Evidence.NONE;
    }

    /**
     * Records one sighting of an enemy unit, see {@link SpeedRunDetector#observe}.
     *
     * @param frame current frame
     * @param unitId the unit's ID
     * @param type the unit's type
     * @param x the unit's position in pixels
     * @param y the unit's position in pixels
     * @param velocity the magnitude of the unit's velocity, pixels per frame, for the diagnostics
     * @param moving the unit's moving flag, for the diagnostics
     */
    public void observe(int frame, int unitId, UnitType type, int x, int y, double velocity, boolean moving) {
        detector.observe(frame, unitId, type, x, y, velocity, moving);
    }

    /**
     * Records one visible enemy structure the earliest finish depends on, see {@link #followsStructure}.
     *
     * @param frame current frame
     * @param unitId the structure's ID
     * @param type the structure's type
     * @param completed whether it is finished and not morphing
     * @param remainingBuildTime frames left on its construction or morph
     */
    public void observeStructure(int frame, int unitId, UnitType type, boolean completed, int remainingBuildTime) {
        if (!followsStructure(type)) {
            return;
        }
        Structure known = structures.get(unitId);
        if (known != null && known.type == type) {
            if (completed && !known.completed) {
                known.completed = true;
                boundsStale = true;
            }
            return;
        }
        if (completed) {
            structures.put(unitId, new Structure(type, frame, false, true));
        } else if (remainingBuildTime > 0) {
            structures.put(unitId, new Structure(type, frame + remainingBuildTime, true, false));
        } else {
            return;
        }
        boundsStale = true;
    }

    /**
     * Records the gas mined so far from a finished enemy Extractor. The Extractor's readings are trusted from the
     * first one that differs from its first reading.
     *
     * @param frame current frame
     * @param extractorId the Extractor's ID
     * @param mined the geyser's starting gas less what it holds now
     */
    public void observeGasReading(int frame, int extractorId, int mined) {
        Integer first = firstMined.putIfAbsent(extractorId, mined);
        if (first != null && first != mined) {
            trustedReadings.add(extractorId);
        }
        gasReadings.computeIfAbsent(extractorId, id -> new TreeMap<>()).put(mined, frame);
        if (trustedReadings.contains(extractorId)) {
            boundsStale = true;
        }
    }

    /**
     * Forgets a destroyed enemy unit's positions, and a structure destroyed before it was ready together with its gas
     * readings, as a cancelled Extractor never mined.
     *
     * @param unitId the unit's ID
     * @param frame current frame
     */
    public void onUnitDestroy(int unitId, int frame) {
        detector.forget(unitId);
        Structure structure = structures.get(unitId);
        if (structure != null && !structure.completed && frame < structure.readyFrame) {
            structures.remove(unitId);
            gasReadings.remove(unitId);
            firstMined.remove(unitId);
            trustedReadings.remove(unitId);
            boundsStale = true;
        }
    }

    /**
     * Drops the units not seen recently, see {@link SpeedRunDetector#onFrame}.
     *
     * @param frame current frame
     */
    public void onFrame(int frame) {
        detector.onFrame(frame);
    }

    /**
     * Reports what is new for each upgrade: a PLAUSIBLE report the first frame reaching its earliest finish and on
     * every later frame its anchor changed or it moved {@link #PLAUSIBLE_REPORT_FRAMES} or more from the last earliest
     * finish reported, the run that made it SEEN, and the first run refused before the earliest finish.
     *
     * @param frame current frame
     * @return the reports, in the order of {@link #FOLLOWED} and then of {@link Event}
     */
    public List<Report> announce(int frame) {
        List<Report> reports = new ArrayList<>();
        for (UpgradeType upgrade : FOLLOWED) {
            Set<Event> done = reported.computeIfAbsent(upgrade, u -> EnumSet.noneOf(Event.class));
            Bound bound = bound(upgrade);
            if (plausibleChanged(upgrade, bound, frame)) {
                plausibleReported.put(upgrade, bound);
                reports.add(new Report(Event.PLAUSIBLE, upgrade, -1, -1, -1, bound.frame, bound.anchor));
            }
            SpeedRunDetector.Sighting sighting = detector.seen(upgrade);
            if (sighting != null && done.add(Event.SEEN)) {
                reports.add(runReport(Event.SEEN, upgrade, sighting, bound.anchor));
            }
            SpeedRunDetector.Sighting early = detector.earlyRun(upgrade);
            if (early != null && done.add(Event.EARLY_RUN)) {
                reports.add(runReport(Event.EARLY_RUN, upgrade, early, bound.anchor));
            }
        }
        return reports;
    }

    private boolean plausibleChanged(UpgradeType upgrade, Bound bound, int frame) {
        Bound last = plausibleReported.get(upgrade);
        if (last == null) {
            return frame >= bound.frame;
        }
        return last.anchor != bound.anchor || Math.abs(bound.frame - last.frame) >= PLAUSIBLE_REPORT_FRAMES;
    }

    /**
     * A report of a run.
     *
     * @param event SEEN or EARLY_RUN
     * @param upgrade the upgrade
     * @param sighting the run
     * @param anchor what the earliest finish rested on, or null for our own units
     * @return the report
     */
    static Report runReport(Event event, UpgradeType upgrade, SpeedRunDetector.Sighting sighting, Anchor anchor) {
        return new Report(event, upgrade, sighting.getMeasuredSpeed(), sighting.getTopSpeed(),
                sighting.getRunFrames(), sighting.getBoundFrame(), anchor);
    }

    /**
     * Hands over, once per upgrade, the diagnostics of its unit type from before the frame first reaching its sound
     * earliest finish, and starts them afresh from that finish, so one tally covers enemy units that cannot have the
     * upgrade and the next those that can.
     *
     * @param upgrade Metabolic Boost or Muscular Augments
     * @param frame current frame
     * @return the diagnostics up to the sound earliest finish, or null before it, once handed over, or for any other
     *     upgrade
     */
    public SpeedRunDetector.Diagnostic soundBoundCrossed(UpgradeType upgrade, int frame) {
        UnitType type = unitFor(upgrade);
        if (type == null || boundsCrossed.contains(upgrade)) {
            return null;
        }
        int soundBound = soundEarliestFinishFrame(upgrade);
        if (frame < soundBound) {
            return null;
        }
        boundsCrossed.add(upgrade);
        SpeedRunDetector.Diagnostic before = detector.diagnostic(type);
        detector.resetDiagnostic(type, soundBound);
        return before;
    }

    /**
     * @param type Zergling or Hydralisk
     * @return what the detector measured on the enemy's units of the type since its tally began, see
     *     {@link #soundBoundCrossed}
     */
    public SpeedRunDetector.Diagnostic diagnostic(UnitType type) {
        return detector.diagnostic(type);
    }

    /**
     * What is known about an enemy upgrade at a frame.
     *
     * @param upgrade Metabolic Boost or Muscular Augments; any other upgrade reads NONE
     * @param frame the frame asked about
     * @return the evidence
     */
    public Evidence evidence(UpgradeType upgrade, int frame) {
        if (unitFor(upgrade) == null) {
            return Evidence.NONE;
        }
        return classify(seenFrame(upgrade), earliestFinishFrame(upgrade), frame);
    }

    /**
     * Whether the enemy has an upgrade, or plausibly has it.
     *
     * @param upgrade Metabolic Boost or Muscular Augments
     * @param frame the frame asked about
     * @return true when the evidence is SEEN or PLAUSIBLE
     */
    public boolean hasOrPlausiblyHas(UpgradeType upgrade, int frame) {
        return evidence(upgrade, frame) != Evidence.NONE;
    }

    /**
     * @param upgrade the upgrade
     * @return the frame a run first made the upgrade SEEN, or -1
     */
    public int seenFrame(UpgradeType upgrade) {
        SpeedRunDetector.Sighting sighting = detector.seen(upgrade);
        return sighting == null ? -1 : sighting.getFrame();
    }

    /**
     * @param upgrade the upgrade
     * @return the frame the upgrade becomes PLAUSIBLE, {@link #earliestFinishFrame}, or -1 for an upgrade not followed
     */
    public int plausibleFrame(UpgradeType upgrade) {
        return unitFor(upgrade) == null ? -1 : earliestFinishFrame(upgrade);
    }

    /**
     * The earliest frame the enemy can have finished an upgrade given what was observed.
     *
     * @param upgrade Metabolic Boost or Muscular Augments
     * @return the frame
     */
    public int earliestFinishFrame(UpgradeType upgrade) {
        return bound(upgrade).frame;
    }

    /**
     * @param upgrade Metabolic Boost or Muscular Augments
     * @return what the earliest finish rests on
     */
    public Anchor earliestFinishAnchor(UpgradeType upgrade) {
        return bound(upgrade).anchor;
    }

    /**
     * The earliest frame the enemy can have finished an upgrade with every Extractor first seen finished taken as
     * ready at its build time from the start of the game: a lower bound a run must not start before to make the
     * upgrade SEEN.
     *
     * @param upgrade Metabolic Boost or Muscular Augments
     * @return the frame
     */
    public int soundEarliestFinishFrame(UpgradeType upgrade) {
        return soundBound(upgrade).frame;
    }

    private Bound bound(UpgradeType upgrade) {
        refreshBounds();
        return bounds.computeIfAbsent(upgrade, u -> computeBound(u, false));
    }

    private Bound soundBound(UpgradeType upgrade) {
        refreshBounds();
        return soundBounds.computeIfAbsent(upgrade, u -> computeBound(u, true));
    }

    private void refreshBounds() {
        if (boundsStale) {
            bounds.clear();
            soundBounds.clear();
            boundsStale = false;
        }
    }

    private Bound computeBound(UpgradeType upgrade, boolean sound) {
        int poolReady = readyFrame(UnitType.Zerg_Spawning_Pool, UnitType.Zerg_Spawning_Pool.buildTime());
        if (upgrade == UpgradeType.Metabolic_Boost) {
            int both = UnitType.Zerg_Lair.gasPrice() + upgrade.gasPrice();
            int gas = lairGasFirst(lairStartUpper(), gasFrame(both, sound)) ? both : upgrade.gasPrice();
            return start(upgrade, gas, poolReady, hasExact(UnitType.Zerg_Spawning_Pool), sound);
        }
        int denGas = UnitType.Zerg_Hydralisk_Den.gasPrice();
        int denReady = readyFrame(UnitType.Zerg_Hydralisk_Den,
                Math.max(poolReady, gasFrame(denGas, sound)) + UnitType.Zerg_Hydralisk_Den.buildTime());
        return start(upgrade, denGas + upgrade.gasPrice(), denReady, hasExact(UnitType.Zerg_Hydralisk_Den), sound);
    }

    private Bound start(UpgradeType upgrade, int gas, int researcherReady, boolean researcherObserved,
                        boolean sound) {
        int fromExtractors = earliestGasFrame(extractorReadyFrames(sound), gas);
        int fromReading = readingBound(singleExtractorReadings(), gas);
        int gasReady = Math.max(fromExtractors, fromReading);
        Anchor anchor;
        if (researcherReady > gasReady && researcherObserved) {
            anchor = Anchor.RESEARCHER;
        } else if (researcherReady <= gasReady && fromReading >= fromExtractors) {
            anchor = Anchor.GAS_READING;
        } else {
            anchor = hasStructure(UnitType.Zerg_Extractor) ? Anchor.EXTRACTOR : Anchor.FLOOR;
        }
        return new Bound(Math.max(gasReady, researcherReady) + upgrade.upgradeTime(), anchor);
    }

    private int gasFrame(int gas, boolean sound) {
        return Math.max(earliestGasFrame(extractorReadyFrames(sound), gas),
                readingBound(singleExtractorReadings(), gas));
    }

    private List<Integer> extractorReadyFrames(boolean sound) {
        List<Integer> frames = new ArrayList<>();
        int floor = UnitType.Zerg_Extractor.buildTime();
        for (Map.Entry<Integer, Structure> entry : structures.entrySet()) {
            Structure structure = entry.getValue();
            if (structure.type != UnitType.Zerg_Extractor) {
                continue;
            }
            boolean read = structure.exact || !sound && !trustedReadings.contains(entry.getKey());
            frames.add(read ? structure.readyFrame : floor);
        }
        if (frames.isEmpty()) {
            frames.add(floor);
        }
        return frames;
    }

    private NavigableMap<Integer, Integer> singleExtractorReadings() {
        Integer only = null;
        for (Map.Entry<Integer, Structure> entry : structures.entrySet()) {
            if (entry.getValue().type != UnitType.Zerg_Extractor) {
                continue;
            }
            if (only != null) {
                return Collections.emptyNavigableMap();
            }
            only = entry.getKey();
        }
        NavigableMap<Integer, Integer> readings = only == null || !trustedReadings.contains(only)
                ? null : gasReadings.get(only);
        return readings == null ? Collections.emptyNavigableMap() : readings;
    }

    private int readyFrame(UnitType type, int floor) {
        int exact = -1;
        for (Structure structure : structures.values()) {
            if (structure.type != type) {
                continue;
            }
            if (!structure.exact) {
                return floor;
            }
            exact = exact < 0 ? structure.readyFrame : Math.min(exact, structure.readyFrame);
        }
        return exact < 0 ? floor : exact;
    }

    private int lairStartUpper() {
        int upper = -1;
        for (Structure structure : structures.values()) {
            if (structure.type != UnitType.Zerg_Lair) {
                continue;
            }
            int start = structure.readyFrame - UnitType.Zerg_Lair.buildTime();
            upper = upper < 0 ? start : Math.min(upper, start);
        }
        return upper;
    }

    private boolean hasExact(UnitType type) {
        for (Structure structure : structures.values()) {
            if (structure.type == type && structure.exact) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStructure(UnitType type) {
        for (Structure structure : structures.values()) {
            if (structure.type == type) {
                return true;
            }
        }
        return false;
    }
}
