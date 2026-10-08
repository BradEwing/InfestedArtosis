package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import bwem.Base;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
    private final Set<Integer> engagedEdgeTurrets = new HashSet<>();

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
    private final Map<Integer, Snipe> snipes = new LinkedHashMap<>();
    private List<AirHarassTargeting.AirThreat> approachZones = new ArrayList<>();
    private String lastApproachKey;

    /**
     * A target the flock committed a volley to: what it was, its hit points plus shields and the volley's alpha when
     * the volley was assigned, and the frame it was.
     */
    @Getter
    public static final class Snipe {
        private final int id;
        private final UnitType type;
        private final int hitPoints;
        private final int alpha;
        private final int frame;

        Snipe(int id, UnitType type, int hitPoints, int alpha, int frame) {
            this.id = id;
            this.type = type;
            this.hitPoints = hitPoints;
            this.alpha = alpha;
            this.frame = frame;
        }
    }

    /**
     * Records a target the flock committed a volley to; a target already recorded keeps its first record.
     *
     * @param id enemy unit id
     * @param type its type
     * @param hitPoints its hit points plus shields
     * @param alpha the volley's damage against it
     * @param frame current frame
     * @return true the first time this harass commits to that target
     */
    public boolean noteSnipe(int id, UnitType type, int hitPoints, int alpha, int frame) {
        if (snipes.containsKey(id)) {
            return false;
        }
        snipes.put(id, new Snipe(id, type, hitPoints, alpha, frame));
        return true;
    }

    /**
     * Takes a committed target out of the record once it is dead.
     *
     * @param id enemy unit id
     * @return the record, or null when the harass committed no volley to it
     */
    public Snipe resolveSnipe(int id) {
        return snipes.remove(id);
    }

    /**
     * Takes out the committed targets that were assigned more than a window of frames ago and still stand.
     *
     * @param now current frame
     * @param window frames after which a committed target counts as missed
     * @return the records taken out, oldest first
     */
    public List<Snipe> missedSnipes(int now, int window) {
        List<Snipe> missed = new ArrayList<>();
        Iterator<Snipe> iterator = snipes.values().iterator();
        while (iterator.hasNext()) {
            Snipe snipe = iterator.next();
            if (now - snipe.getFrame() > window) {
                missed.add(snipe);
                iterator.remove();
            }
        }
        return missed;
    }

    /**
     * Takes out every committed target still recorded.
     *
     * @return the records taken out, oldest first
     */
    public List<Snipe> drainSnipes() {
        List<Snipe> rest = new ArrayList<>(snipes.values());
        snipes.clear();
        return rest;
    }

    /**
     * Records how the approach was priced, and whether that differs from the last pricing this harass wrote.
     *
     * @param key the pricing's decision and reason, see {@link AirApproachPricing.Result#key}
     * @return true when the key differs from the last one recorded
     */
    public boolean notePricing(String key) {
        boolean changed = !key.equals(lastApproachKey);
        lastApproachKey = key;
        return changed;
    }

    /**
     * Sets the mobile anti-air the flock flies around on this approach.
     *
     * @param zones the threats, none when the approach is flown straight
     */
    public void setApproachZones(List<AirHarassTargeting.AirThreat> zones) {
        approachZones = new ArrayList<>(zones);
    }

    /**
     * @return the mobile anti-air the flock flies around on this approach, a copy
     */
    public List<AirHarassTargeting.AirThreat> approachZones() {
        return new ArrayList<>(approachZones);
    }

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
        this.approachZones = new ArrayList<>();
        this.lastApproachKey = null;
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
        this.approachZones = new ArrayList<>();
        this.lastApproachKey = null;
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
     * Records that a Mutalisk of the flock took a lone Missile Turret on, see {@link AirHarassTargeting#edgeTurrets}.
     *
     * @param turretId the Turret's unit id
     * @return true the first time this harass takes that Turret on
     */
    public boolean engageEdgeTurret(int turretId) {
        return engagedEdgeTurrets.add(turretId);
    }

    /**
     * @return the ids of the lone Missile Turrets the flock has taken on in this harass, which stay taken on while
     *         they stand and stay lone, see {@link AirHarassTargeting#edgeTurrets(Collection, int, Position,
     *         Collection)}
     */
    public Set<Integer> getEngagedEdgeTurrets() {
        return Collections.unmodifiableSet(engagedEdgeTurrets);
    }

    /**
     * @return how many lone Missile Turrets the flock has taken on in this harass
     */
    public int edgeTurretsEngaged() {
        return engagedEdgeTurrets.size();
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
