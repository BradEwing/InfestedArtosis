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
 * {@link #WINDOW_FRAMES} passes when its speed is above {@link #SPEED_MARGIN} times the type's
 * {@link UnitType#topSpeed()}, the unupgraded top speed, which an unupgraded unit cannot cover whatever its order,
 * velocity or moving flag read; JBWAPI's {@code Player.topSpeed} makes the upgraded top speed 1.5 times it.
 *
 * <p>A unit seen again after more than {@link #MAX_SAMPLE_GAP} frames, or seen further from its last position than
 * {@link #MAX_STEP_FACTOR} times its top speed per frame between, as when a transport unloads it, starts its
 * positions afresh, so no window spans the gap or the jump.
 *
 * <p>The first window that passes before the upgrade's bound is an early run. The first that passes and starts at
 * or after the bound makes the upgrade seen, so a run that began before the bound is seen only a whole window after
 * it. A window that passes but starts before the bound is neither.
 *
 * <p>Per unit type it also tallies diagnostics, see {@link Diagnostic}.
 */
public class SpeedRunDetector {
    /** Frames in the window whose speed decides whether a unit is upgraded. */
    public static final int WINDOW_FRAMES = 16;

    /** Frames in the shorter diagnostic window. */
    public static final int SHORT_WINDOW_FRAMES = 8;

    /** Frames in the longer diagnostic window, and the positions each unit keeps. */
    public static final int LONG_WINDOW_FRAMES = 24;

    /** How far above the unupgraded top speed a window must move for the unit to count as upgraded. */
    public static final double SPEED_MARGIN = 1.15;

    /** The most frames between two sightings of a unit that one window spans. */
    public static final int MAX_SAMPLE_GAP = 2;

    /** Multiples of the unupgraded top speed per frame between two sightings that no unit walks. */
    public static final double MAX_STEP_FACTOR = 2.0;

    private static final int[] WINDOWS = {SHORT_WINDOW_FRAMES, WINDOW_FRAMES, LONG_WINDOW_FRAMES};

    /** A window that passed. */
    @Value
    public static class Sighting {
        /** The window's last frame. */
        int frame;
        /** The window's speed, pixels per frame. */
        double measuredSpeed;
        /** The type's unupgraded top speed. */
        double topSpeed;
        /** Frames the window spans. */
        int runFrames;
        /** The upgrade's bound on the window's last frame. */
        int boundFrame;
    }

    /** What the detector measured on one unit type since {@link #fromFrame}. */
    @Value
    public static class Diagnostic {
        UnitType type;
        /** The frame the tally began. */
        int fromFrame;
        /** Most consecutive sightings of one unit whose window passed. */
        int longestRun;
        /** Fastest {@link #SHORT_WINDOW_FRAMES} window, as a multiple of the top speed. */
        double shortWindowSpeed;
        /** Fastest {@link #WINDOW_FRAMES} window, as a multiple of the top speed. */
        double windowSpeed;
        /** Fastest {@link #LONG_WINDOW_FRAMES} window, as a multiple of the top speed. */
        double longWindowSpeed;
        /** {@link #WINDOW_FRAMES} windows measured. */
        int windows;
        /** Sightings whose velocity was above the cut. */
        int velocityFrames;
        /** Sightings whose moving flag was false while the unit moved since its last sighting. */
        int notMovingFrames;
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

    private static final class Track {
        final UnitType type;
        final Deque<Sample> samples = new ArrayDeque<>();
        int passing;

        Track(UnitType type) {
            this.type = type;
        }
    }

    private static final class Tally {
        final int fromFrame;
        final double[] fastest = new double[WINDOWS.length];
        int longestRun;
        int windows;
        int velocityFrames;
        int notMovingFrames;

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
     * Whether a speed is above the unupgraded top speed of a followed type by the margin.
     *
     * @param type the unit type
     * @param speed pixels per frame
     * @return true when the unit moves faster than an unupgraded one can
     */
    public static boolean exceedsBaseSpeed(UnitType type, double speed) {
        return EnemySpeedTracker.upgradeFor(type) != null && speed > type.topSpeed() * SPEED_MARGIN;
    }

    /**
     * Whether a unit moved further between two sightings than any unit of its type walks.
     *
     * @param type the unit type
     * @param distance pixels between the two positions
     * @param frames frames between the two sightings
     * @return true for a jump, as when a transport unloads the unit
     */
    public static boolean impossibleStep(UnitType type, double distance, int frames) {
        return distance > type.topSpeed() * MAX_STEP_FACTOR * frames;
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
        Tally tally = tallies.computeIfAbsent(type, t -> new Tally(0));
        if (exceedsBaseSpeed(type, velocity)) {
            tally.velocityFrames++;
        }
        if (last != null) {
            int gap = frame - last.frame;
            double step = Math.hypot(x - last.x, y - last.y);
            if (gap > MAX_SAMPLE_GAP || impossibleStep(type, step, gap)) {
                track.samples.clear();
                track.passing = 0;
            } else if (step > 0 && !moving) {
                tally.notMovingFrames++;
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
            double speed = Math.hypot(x - start.x, y - start.y) / (frame - start.frame);
            tally.fastest[i] = Math.max(tally.fastest[i], speed / type.topSpeed());
            if (WINDOWS[i] == WINDOW_FRAMES) {
                tally.windows++;
                passed = exceedsBaseSpeed(type, speed);
                if (passed) {
                    pass(upgrade, type, frame, start, speed);
                }
            }
        }
        track.passing = passed ? track.passing + 1 : 0;
        tally.longestRun = Math.max(tally.longestRun, track.passing);
    }

    private void pass(UpgradeType upgrade, UnitType type, int frame, Sample start, double speed) {
        if (seen.containsKey(upgrade)) {
            return;
        }
        int boundFrame = bound.applyAsInt(upgrade);
        Sighting sighting = new Sighting(frame, speed, type.topSpeed(), frame - start.frame, boundFrame);
        if (frame < boundFrame) {
            earlyRuns.putIfAbsent(upgrade, sighting);
        } else if (start.frame >= boundFrame) {
            seen.put(upgrade, sighting);
        }
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
     * Drops the units not seen for more than {@link #MAX_SAMPLE_GAP} frames.
     *
     * @param frame current frame
     */
    public void onFrame(int frame) {
        tracks.values().removeIf(track -> track.samples.isEmpty()
                || track.samples.peekLast().frame < frame - MAX_SAMPLE_GAP);
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
     * @return the window that made the upgrade seen, or null
     */
    public Sighting seen(UpgradeType upgrade) {
        return seen.get(upgrade);
    }

    /**
     * @param upgrade the upgrade
     * @return the first window that passed before the upgrade's bound, or null
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
                tally.fastest[2], tally.windows, tally.velocityFrames, tally.notMovingFrames);
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
