package info.tracking;

import bwapi.UnitType;
import bwapi.UpgradeType;
import lombok.Value;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Detects the movement upgrade of a unit type from the positions its units are seen at: Metabolic Boost for
 * Zerglings and Muscular Augments for Hydralisks.
 *
 * <p>Each unit keeps the positions it was seen at over the last {@link #LONG_WINDOW_FRAMES} frames. A window of a
 * length ends on a frame the unit is seen and starts at the latest position seen at least that many frames earlier;
 * its speed is the straight-line distance between the two positions over the frames between them. A window of
 * {@link #WINDOW_FRAMES} passes when its speed is above the type's {@link #speedMargin} times its unupgraded
 * {@link UnitType#topSpeed()}, see {@link #passes}. JBWAPI's {@code Player.topSpeed} makes the upgraded top speed
 * {@link #UPGRADED_SPEED_FACTOR} times the unupgraded one.
 *
 * <p>A run is two passing windows of one unit that do not overlap, the second ending at most
 * {@link #PAIR_SPAN_FRAMES} frames after the first. The first run that ends before the upgrade's bound is an early
 * run. The first run whose first window starts at or after the bound makes the upgrade seen, so a unit running since
 * before the bound is seen only two windows after it. A run that starts before the bound and ends after it is
 * neither.
 *
 * <p>A unit seen again after more than {@link #MAX_SAMPLE_GAP} frames, as when it left sight or was carried in a
 * transport, or seen further from its last position than {@link #impossibleStep} allows, starts afresh: its
 * positions and its passing windows are dropped, so no window or run spans the gap or the jump.
 *
 * <p>Per unit type it also tallies diagnostics, see {@link Diagnostic}.
 */
public class SpeedRunDetector {
    /** Frames in the window whose speed decides whether a unit is upgraded. */
    public static final int WINDOW_FRAMES = 8;

    /** Frames in the middle diagnostic window. */
    public static final int MID_WINDOW_FRAMES = 16;

    /** Frames in the longest diagnostic window, and the positions each unit keeps. */
    public static final int LONG_WINDOW_FRAMES = 24;

    /** The most frames from the end of a run's first window to the end of its second. */
    public static final int PAIR_SPAN_FRAMES = 48;

    /** The fewest frames a unit must be followed for to make a run. */
    public static final int MIN_RUN_FRAMES = 2 * WINDOW_FRAMES;

    /** How far above the unupgraded top speed a Zergling's window must move to pass. */
    public static final double ZERGLING_SPEED_MARGIN = 1.25;

    /** How far above the unupgraded top speed a Hydralisk's window must move to pass. */
    public static final double HYDRALISK_SPEED_MARGIN = 1.30;

    /** The most frames between two sightings of a unit that one window spans. */
    public static final int MAX_SAMPLE_GAP = 2;

    /** The upgraded top speed as a multiple of the unupgraded one, as JBWAPI's {@code Player.topSpeed} gives it. */
    public static final double UPGRADED_SPEED_FACTOR = 1.5;

    /** How many times further than an upgraded unit walks a step must go to be a jump, see {@link #impossibleStep}. */
    public static final double JUMP_FACTOR = 2;

    /** The most the integer positions can add to the distance between two sightings, pixels. */
    public static final double POSITION_ROUNDING = 2 * Math.sqrt(2);

    /**
     * Upper ends of the step histogram's bins, as multiples of the top speed per frame; the first bin holds the steps
     * of no distance and the last the steps above the last end, see {@link Diagnostic#getSteps}.
     */
    public static final double[] STEP_BINS = {0, 1, 1.25, 1.5, 2};

    private static final int[] WINDOWS = {WINDOW_FRAMES, MID_WINDOW_FRAMES, LONG_WINDOW_FRAMES};

    /** A run that passed. */
    @Value
    public static class Sighting {
        /** The run's last frame. */
        int frame;
        /** The speed of the slower of the run's two windows, pixels per frame. */
        double measuredSpeed;
        /** The type's unupgraded top speed. */
        double topSpeed;
        /** Frames from the start of the run's first window to the end of its second. */
        int runFrames;
        /** The upgrade's bound on the run's last frame. */
        int boundFrame;
    }

    /** What the detector measured on one unit type since {@link #fromFrame}. */
    @Value
    public static class Diagnostic {
        UnitType type;
        /** The frame the tally began. */
        int fromFrame;
        /** Most consecutive sightings of one unit whose deciding window passed. */
        int longestRun;
        /** Fastest {@link #WINDOW_FRAMES} window, as a multiple of the top speed. */
        double windowSpeed;
        /** Fastest {@link #MID_WINDOW_FRAMES} window, as a multiple of the top speed. */
        double midWindowSpeed;
        /** Fastest {@link #LONG_WINDOW_FRAMES} window, as a multiple of the top speed. */
        double longWindowSpeed;
        /** {@link #WINDOW_FRAMES} windows measured. */
        int windows;
        /** Sightings whose velocity was above the cut. */
        int velocityFrames;
        /** Sightings whose moving flag was false while the unit moved since its last sighting. */
        int notMovingFrames;
        /** Units started afresh after more than {@link #MAX_SAMPLE_GAP} frames unseen. */
        int gapClears;
        /** Units started afresh after a jump, see {@link #impossibleStep}. */
        int jumpClears;
        /**
         * Steps between two sightings at most {@link #MAX_SAMPLE_GAP} frames apart, by their distance per frame as a
         * multiple of the top speed: none, then up to each of {@link #STEP_BINS} after the first, then above the last.
         */
        int[] steps;
        /**
         * Fastest {@link #WINDOW_FRAMES} window less how far its longest step went beyond the top speed times its
         * frames, as a multiple of the top speed.
         */
        double discountedWindowSpeed;
        /** Fastest run, the slower of its two {@link #WINDOW_FRAMES} windows, as a multiple of the top speed. */
        double runSpeed;
    }

    private static final class Sample {
        final int frame;
        final int x;
        final int y;

        Sample(int frame, int x, int y) {
            this.frame = frame;
            this.x = x;
            this.y = y;
        }
    }

    private static final class Window {
        final int start;
        final int end;
        final double speed;

        Window(int start, int end, double speed) {
            this.start = start;
            this.end = end;
            this.speed = speed;
        }
    }

    private static final class Track {
        final UnitType type;
        final Deque<Sample> samples = new ArrayDeque<>();
        final Deque<Window> windows = new ArrayDeque<>();
        int passing;

        Track(UnitType type) {
            this.type = type;
        }

        void clear() {
            samples.clear();
            windows.clear();
            passing = 0;
        }
    }

    private static final class Tally {
        final int fromFrame;
        final double[] fastest = new double[WINDOWS.length];
        final int[] steps = new int[STEP_BINS.length + 1];
        int longestRun;
        int windows;
        int velocityFrames;
        int notMovingFrames;
        int gapClears;
        int jumpClears;
        double fastestDiscounted;
        double fastestRun;

        Tally(int fromFrame) {
            this.fromFrame = fromFrame;
        }
    }

    private final ToIntFunction<UpgradeType> bound;
    private final Map<Integer, Track> tracks = new HashMap<>();
    private final Map<UpgradeType, Sighting> seen = new HashMap<>();
    private final Map<UpgradeType, Sighting> earlyRuns = new HashMap<>();
    private final Map<UnitType, Tally> tallies = new HashMap<>();

    /**
     * @param bound the frame an upgrade can first be seen from
     */
    public SpeedRunDetector(ToIntFunction<UpgradeType> bound) {
        this.bound = bound;
    }

    /**
     * The multiple of its unupgraded top speed a window of a followed type must move faster than to pass. Each is
     * above the fastest window of {@link #WINDOW_FRAMES} measured on our own unupgraded units.
     *
     * @param type the unit type
     * @return {@link #ZERGLING_SPEED_MARGIN} or {@link #HYDRALISK_SPEED_MARGIN}, or infinity for any other type
     */
    public static double speedMargin(UnitType type) {
        if (type == UnitType.Zerg_Zergling) {
            return ZERGLING_SPEED_MARGIN;
        }
        if (type == UnitType.Zerg_Hydralisk) {
            return HYDRALISK_SPEED_MARGIN;
        }
        return Double.POSITIVE_INFINITY;
    }

    /**
     * Whether a speed is above the unupgraded top speed of a followed type by its margin.
     *
     * @param type the unit type
     * @param speed pixels per frame
     * @return true when the unit moves faster than an unupgraded one can
     */
    public static boolean exceedsBaseSpeed(UnitType type, double speed) {
        return speed > type.topSpeed() * speedMargin(type);
    }

    /**
     * Whether a unit moved further between two sightings than {@link #JUMP_FACTOR} times what an upgraded unit of its
     * type walks in one frame more than the frames between them, rounding included. The extra frame and the factor
     * leave room for a walking unit's uneven steps, a still frame followed by a step of two included, at any speed it
     * reaches.
     *
     * @param type the unit type
     * @param distance pixels between the two positions
     * @param frames frames between the two sightings
     * @return true for a jump
     */
    public static boolean impossibleStep(UnitType type, double distance, int frames) {
        return distance > type.topSpeed() * UPGRADED_SPEED_FACTOR * JUMP_FACTOR * (frames + 1) + POSITION_ROUNDING;
    }

    /**
     * Whether a window passes: its distance per frame is above the type's margin.
     *
     * @param type the unit type
     * @param distance pixels between the window's first and last positions
     * @param frames frames the window spans
     * @return true when the window moved faster than an unupgraded unit can
     */
    public static boolean passes(UnitType type, double distance, int frames) {
        return exceedsBaseSpeed(type, distance / frames);
    }

    /**
     * Records one sighting of a unit. The velocity and moving flag feed only the diagnostics.
     *
     * @param frame current frame
     * @param unitId the unit's ID
     * @param type the unit's type
     * @param x the unit's position in pixels
     * @param y the unit's position in pixels
     * @param velocity the magnitude of the unit's velocity, pixels per frame
     * @param moving the unit's moving flag
     */
    public void observe(int frame, int unitId, UnitType type, int x, int y, double velocity, boolean moving) {
        UpgradeType upgrade = EnemySpeedTracker.upgradeFor(type);
        if (upgrade == null) {
            return;
        }
        Track track = tracks.get(unitId);
        if (track == null || track.type != type) {
            track = new Track(type);
            tracks.put(unitId, track);
        }
        Sample last = track.samples.peekLast();
        if (last != null && frame <= last.frame) {
            return;
        }
        Tally tally = tally(type);
        if (exceedsBaseSpeed(type, velocity)) {
            tally.velocityFrames++;
        }
        if (last != null) {
            int gap = frame - last.frame;
            double step = Math.hypot(x - last.x, y - last.y);
            if (gap > MAX_SAMPLE_GAP) {
                tally.gapClears++;
                track.clear();
            } else {
                tally.steps[stepBin(step / gap / type.topSpeed())]++;
                if (impossibleStep(type, step, gap)) {
                    tally.jumpClears++;
                    track.clear();
                } else if (step > 0 && !moving) {
                    tally.notMovingFrames++;
                }
            }
        }
        track.samples.addLast(new Sample(frame, x, y));
        trim(track.samples, frame);
        boolean passed = false;
        for (int i = 0; i < WINDOWS.length; i++) {
            Sample start = windowStart(track.samples, frame, WINDOWS[i]);
            if (start == null) {
                continue;
            }
            int span = frame - start.frame;
            double distance = Math.hypot(x - start.x, y - start.y);
            tally.fastest[i] = Math.max(tally.fastest[i], distance / span / type.topSpeed());
            if (WINDOWS[i] == WINDOW_FRAMES) {
                tally.windows++;
                double discounted = distance - longestStepExcess(track.samples, start, type);
                tally.fastestDiscounted = Math.max(tally.fastestDiscounted, discounted / span / type.topSpeed());
                passed = passes(type, distance, span);
                window(track, tally, upgrade, new Window(start.frame, frame, distance / span), passed);
            }
        }
        track.passing = passed ? track.passing + 1 : 0;
        tally.longestRun = Math.max(tally.longestRun, track.passing);
    }

    private Tally tally(UnitType type) {
        return tallies.computeIfAbsent(type, t -> new Tally(0));
    }

    private static int stepBin(double multiple) {
        if (multiple <= 0) {
            return 0;
        }
        for (int i = 1; i < STEP_BINS.length; i++) {
            if (multiple <= STEP_BINS[i]) {
                return i;
            }
        }
        return STEP_BINS.length;
    }

    private void window(Track track, Tally tally, UpgradeType upgrade, Window current, boolean passed) {
        while (!track.windows.isEmpty() && track.windows.peekFirst().end < current.end - PAIR_SPAN_FRAMES) {
            track.windows.removeFirst();
        }
        int boundFrame = bound.applyAsInt(upgrade);
        Window first = null;
        for (Window earlier : track.windows) {
            if (earlier.end > current.start) {
                break;
            }
            double runSpeed = Math.min(earlier.speed, current.speed);
            tally.fastestRun = Math.max(tally.fastestRun, runSpeed / track.type.topSpeed());
            if (passed && exceedsBaseSpeed(track.type, earlier.speed) && (first == null || first.start < boundFrame)) {
                first = earlier;
            }
        }
        track.windows.addLast(current);
        if (first != null) {
            run(upgrade, track.type, first, current);
        }
    }

    private void run(UpgradeType upgrade, UnitType type, Window first, Window second) {
        if (seen.containsKey(upgrade)) {
            return;
        }
        int boundFrame = bound.applyAsInt(upgrade);
        Sighting sighting = new Sighting(second.end, Math.min(first.speed, second.speed), type.topSpeed(),
                second.end - first.start, boundFrame);
        if (second.end < boundFrame) {
            earlyRuns.putIfAbsent(upgrade, sighting);
        } else if (first.start >= boundFrame) {
            seen.put(upgrade, sighting);
        }
    }

    private static double longestStepExcess(Deque<Sample> samples, Sample start, UnitType type) {
        double excess = 0;
        Sample previous = null;
        for (Sample sample : samples) {
            if (sample.frame < start.frame) {
                continue;
            }
            if (previous != null) {
                double step = Math.hypot(sample.x - previous.x, sample.y - previous.y);
                excess = Math.max(excess, step - type.topSpeed() * (sample.frame - previous.frame));
            }
            previous = sample;
        }
        return excess;
    }

    private static void trim(Deque<Sample> samples, int frame) {
        while (samples.size() > 1) {
            Iterator<Sample> oldest = samples.iterator();
            oldest.next();
            if (oldest.next().frame > frame - LONG_WINDOW_FRAMES) {
                return;
            }
            samples.removeFirst();
        }
    }

    private static Sample windowStart(Deque<Sample> samples, int frame, int length) {
        Iterator<Sample> newest = samples.descendingIterator();
        while (newest.hasNext()) {
            Sample sample = newest.next();
            if (sample.frame <= frame - length) {
                return sample;
            }
        }
        return null;
    }

    /**
     * Drops the units not seen for more than {@link #MAX_SAMPLE_GAP} frames, each a gap clear of its type.
     *
     * @param frame current frame
     */
    public void onFrame(int frame) {
        tracks.values().removeIf(track -> {
            boolean stale = track.samples.isEmpty() || track.samples.peekLast().frame < frame - MAX_SAMPLE_GAP;
            if (stale && !track.samples.isEmpty()) {
                tally(track.type).gapClears++;
            }
            return stale;
        });
    }

    /**
     * Forgets a unit's positions.
     *
     * @param unitId the unit's ID
     */
    public void forget(int unitId) {
        tracks.remove(unitId);
    }

    /**
     * @param upgrade the upgrade
     * @return the run that made the upgrade seen, or null
     */
    public Sighting seen(UpgradeType upgrade) {
        return seen.get(upgrade);
    }

    /**
     * @param upgrade the upgrade
     * @return the first run that ended before the upgrade's bound, or null
     */
    public Sighting earlyRun(UpgradeType upgrade) {
        return earlyRuns.get(upgrade);
    }

    /**
     * @param type Zergling or Hydralisk
     * @return what was measured on the type since its tally began, zero when none of its units was seen
     */
    public Diagnostic diagnostic(UnitType type) {
        Tally tally = tallies.getOrDefault(type, new Tally(0));
        return new Diagnostic(type, tally.fromFrame, tally.longestRun, tally.fastest[0], tally.fastest[1],
                tally.fastest[2], tally.windows, tally.velocityFrames, tally.notMovingFrames, tally.gapClears,
                tally.jumpClears, tally.steps.clone(), tally.fastestDiscounted, tally.fastestRun);
    }

    /**
     * Starts a type's diagnostics afresh.
     *
     * @param type the unit type
     * @param frame the frame the new tally begins
     */
    public void resetDiagnostic(UnitType type, int frame) {
        tallies.put(type, new Tally(frame));
    }
}
