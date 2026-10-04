package unit.squad;

import bwapi.Position;
import bwem.Base;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything a squad in {@link SquadStatus#HARASS} carries between frames: the phase, the base or exposed group of
 * enemies it is raiding and the point it strikes, the point the whole flock is moving to, the hit points it started
 * with, what it has killed and lost, and the per-Mutalisk memory that keeps evasion and targets from flapping.
 *
 * <p>TRANSIT is the flight to the target. STRIKE starts once the flock reaches it. A retarget starts TRANSIT
 * again. The whole flock flies every leg on one path.
 */
@Getter
@Setter
public class AirHarassState {

    /**
     * Stages of a harass.
     */
    public enum Phase {
        TRANSIT,
        STRIKE
    }

    /**
     * What a credited kill was.
     */
    public enum KillKind {
        WORKER,
        BUILDING,
        OTHER
    }

    private final int startFrame;
    private int startHitPoints;
    private final Set<Base> visitedBases = new HashSet<>();
    private final Map<Integer, AirHarassTargeting.MutaMemory> mutaMemory = new HashMap<>();

    private Phase phase = Phase.TRANSIT;
    private Base targetBase;
    private Position exposedAnchor;
    private ExposedTargets.Group exposedGroup;
    private Position strikePoint;
    private int lastTickFrame;
    private boolean defendedAtTarget;
    private int lastProgressFrame;
    private int arrivedFrame = -1;
    private int workersKilled;
    private int buildingsKilled;
    private int otherKilled;
    private int mutasLost;
    private Position flockPoint;
    private Position flockGoal;
    private int flockPointUntilFrame;
    private final Map<Integer, AntiAirSighting> knownAntiAir = new HashMap<>();

    /**
     * @param startFrame frame the harass started
     * @param startHitPoints summed hit points of the squad's Mutalisks at the start, which grows by the hit points
     *                       of every Mutalisk that joins later
     */
    public AirHarassState(int startFrame, int startHitPoints) {
        this.startFrame = startFrame;
        this.startHitPoints = startHitPoints;
        this.lastTickFrame = startFrame;
        this.lastProgressFrame = startFrame;
    }

    /**
     * Points the harass at a base and starts TRANSIT toward it.
     *
     * @param base base to raid
     * @param strike point to strike at it
     * @param frame current frame
     */
    public void target(Base base, Position strike, int frame) {
        this.targetBase = base;
        this.exposedAnchor = null;
        this.exposedGroup = null;
        this.strikePoint = strike;
        this.phase = Phase.TRANSIT;
        this.arrivedFrame = -1;
        this.lastProgressFrame = frame;
        this.defendedAtTarget = false;
        if (base != null) {
            visitedBases.add(base);
        }
    }

    /**
     * Points the harass at an exposed group of enemies away from a base's heat and starts TRANSIT toward it. The
     * group's anchor is also the strike point.
     *
     * @param group the group
     * @param frame current frame
     */
    public void targetExposed(ExposedTargets.Group group, int frame) {
        this.targetBase = null;
        follow(group);
        this.strikePoint = group.getAnchor();
        this.phase = Phase.TRANSIT;
        this.arrivedFrame = -1;
        this.lastProgressFrame = frame;
    }

    /**
     * Keeps an exposed target on the group found near its last anchor this decision tick: the group and its anchor
     * replace the old ones. The strike point is left to the caller.
     *
     * @param group the group followed
     */
    public void follow(ExposedTargets.Group group) {
        this.exposedGroup = group;
        this.exposedAnchor = group.getAnchor();
    }

    /**
     * @return true while the harass targets a base or an exposed group
     */
    public boolean hasTarget() {
        return targetBase != null || exposedAnchor != null;
    }

    /**
     * @return true while the harass targets an exposed group rather than a base
     */
    public boolean targetsExposed() {
        return targetBase == null && exposedAnchor != null;
    }

    /**
     * @return the target base's center, the exposed group's anchor, or null with no target
     */
    public Position targetCenter() {
        if (targetBase != null) {
            return targetBase.getCenter();
        }
        return exposedAnchor;
    }

    /**
     * Records every anti-air threat given as known, with the frame and the flock's hit points when it was first
     * given, and returns the ones given for the first time.
     *
     * @param threats every known anti-air threat
     * @param frame current frame
     * @param flockHitPoints summed hit points of the Mutalisks now
     * @return the threats not known before this call
     */
    public List<AirHarassTargeting.AirThreat> learnAntiAir(Collection<AirHarassTargeting.AirThreat> threats,
                                                           int frame, int flockHitPoints) {
        List<AirHarassTargeting.AirThreat> fresh = new ArrayList<>();
        for (AirHarassTargeting.AirThreat threat : threats) {
            if (!knownAntiAir.containsKey(threat.getId())) {
                knownAntiAir.put(threat.getId(), new AntiAirSighting(frame, flockHitPoints, false));
                fresh.add(threat);
            }
        }
        return fresh;
    }

    /**
     * Records anti-air already at the target or at the flock when the harass starts or moves on as accepted: it is
     * not new, and no reaction measures from it.
     *
     * @param threats the threats at the target or the flock
     * @param frame current frame
     * @param flockHitPoints summed hit points of the Mutalisks now
     */
    public void acceptAntiAir(Collection<AirHarassTargeting.AirThreat> threats, int frame, int flockHitPoints) {
        for (AirHarassTargeting.AirThreat threat : threats) {
            knownAntiAir.putIfAbsent(threat.getId(), new AntiAirSighting(frame, flockHitPoints, true));
        }
    }

    /**
     * Records anti-air by id as accepted, for units that ended an earlier harass and may be out of sight.
     *
     * @param ids unit ids
     * @param frame current frame
     * @param flockHitPoints summed hit points of the Mutalisks now
     */
    public void acceptIds(Collection<Integer> ids, int frame, int flockHitPoints) {
        for (int id : ids) {
            knownAntiAir.putIfAbsent(id, new AntiAirSighting(frame, flockHitPoints, true));
        }
    }

    /**
     * The earliest first sighting among anti-air threats, leaving out the ones accepted by {@link #acceptAntiAir}.
     *
     * @param ids unit ids of the threats
     * @return the sighting with the lowest frame, or null when none of the ids was seen as new
     */
    public AntiAirSighting earliestSighting(Collection<Integer> ids) {
        AntiAirSighting earliest = null;
        for (int id : ids) {
            AntiAirSighting sighting = knownAntiAir.get(id);
            if (sighting != null && !sighting.isAccepted() && (earliest == null || sighting.getFrame() < earliest.getFrame())) {
                earliest = sighting;
            }
        }
        return earliest;
    }

    /**
     * When an anti-air threat was first seen by the harass and what the flock's hit points were then.
     */
    @Getter
    public static final class AntiAirSighting {
        private final int frame;
        private final int flockHitPoints;
        private final boolean accepted;

        AntiAirSighting(int frame, int flockHitPoints, boolean accepted) {
            this.frame = frame;
            this.flockHitPoints = flockHitPoints;
            this.accepted = accepted;
        }
    }

    /**
     * Moves the harass from TRANSIT to STRIKE.
     *
     * @param frame current frame
     */
    public void arrive(int frame) {
        phase = Phase.STRIKE;
        arrivedFrame = frame;
        lastProgressFrame = frame;
    }

    /**
     * @return true once the flock has reached the target base
     */
    public boolean hasArrived() {
        return arrivedFrame >= 0;
    }

    /**
     * Returns the memory kept for one Mutalisk, creating it on first use.
     *
     * @param unitId the Mutalisk's unit id
     * @return its memory
     */
    public AirHarassTargeting.MutaMemory memoryFor(int unitId) {
        return mutaMemory.computeIfAbsent(unitId, id -> new AirHarassTargeting.MutaMemory());
    }

    /**
     * Whether the flock's shared point must be decided again: none is held, the goal it was decided for has moved,
     * or its commitment has run out.
     *
     * @param goal the point the flock is heading for
     * @param now current frame
     * @return true when due
     */
    public boolean flockPointDue(Position goal, int now) {
        return flockPoint == null || !goal.equals(flockGoal) || now >= flockPointUntilFrame;
    }

    /**
     * Holds the flock's shared point until a frame.
     *
     * @param goal the point it was decided for
     * @param point the shared point, or null with none
     * @param until frame the commitment runs out
     */
    public void holdFlockPoint(Position goal, Position point, int until) {
        flockGoal = goal;
        flockPoint = point;
        flockPointUntilFrame = until;
    }

    /**
     * Counts a kill credited to the harass.
     *
     * @param kind what was killed
     */
    public void creditKill(KillKind kind) {
        switch (kind) {
            case WORKER:
                workersKilled++;
                break;
            case BUILDING:
                buildingsKilled++;
                break;
            default:
                otherKilled++;
                break;
        }
    }

    /**
     * Counts a Mutalisk lost while harassing.
     */
    public void creditLoss() {
        mutasLost++;
    }

    /**
     * Adds the hit points of Mutalisks that joined the harass to the ones it started with.
     *
     * @param hitPoints summed hit points of the Mutalisks that joined
     */
    public void addStartHitPoints(int hitPoints) {
        startHitPoints += hitPoints;
    }
}
