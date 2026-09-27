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
 * Everything a squad in {@link SquadStatus#HARASS} carries between frames: the phase, the base it is raiding and
 * the point it strikes, the hit points it started with, what it has killed and lost, and the per-Mutalisk memory
 * that keeps evasion and targets from flapping.
 *
 * <p>TRANSIT is the flight to the target base. STRIKE starts once the flock reaches it. A retarget to another base
 * starts TRANSIT again. PROBE replaces TRANSIT on a base whose anti-air sighting is stale: one Mutalisk flies to the
 * probe point, and on to the strike point once the base's core is sighted, while the rest wait at the hold point; the
 * harass moves on to TRANSIT once the probe clears the base.
 */
@Getter
@Setter
public class AirHarassState {

    /**
     * Stages of a harass.
     */
    public enum Phase {
        TRANSIT,
        STRIKE,
        PROBE
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
    private final int startHitPoints;
    private final Set<Base> visitedBases = new HashSet<>();
    private final Map<Integer, AirHarassTargeting.MutaMemory> mutaMemory = new HashMap<>();

    private Phase phase = Phase.TRANSIT;
    private Base targetBase;
    private Position strikePoint;
    private int lastTickFrame;
    private int lastProgressFrame;
    private int arrivedFrame = -1;
    private int workersKilled;
    private int buildingsKilled;
    private int otherKilled;
    private int mutasLost;
    private final Set<Integer> knownAntiAir = new HashSet<>();
    private int proberId = -1;
    private int proberPeakHitPoints;
    private int probeStartFrame = -1;
    private boolean probeCoreSighted;
    private Position probePoint;
    private Position holdPoint;

    /**
     * @param startFrame frame the harass started
     * @param startHitPoints summed hit points of the squad's Mutalisks at the start
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
        this.strikePoint = strike;
        this.phase = Phase.TRANSIT;
        this.arrivedFrame = -1;
        this.lastProgressFrame = frame;
        clearProbeFields();
        if (base != null) {
            visitedBases.add(base);
        }
    }

    /**
     * Points the harass at a base whose anti-air sighting is stale and starts PROBE on it.
     *
     * @param base base to probe
     * @param strike point to strike at it once the probe clears it
     * @param proberId unit id of the probing Mutalisk
     * @param proberHitPoints its hit points now
     * @param probePoint where it flies
     * @param holdPoint where the rest of the flock waits
     * @param frame current frame
     */
    public void probe(Base base, Position strike, int proberId, int proberHitPoints, Position probePoint,
                      Position holdPoint, int frame) {
        target(base, strike, frame);
        this.phase = Phase.PROBE;
        this.proberId = proberId;
        this.proberPeakHitPoints = proberHitPoints;
        this.probeStartFrame = frame;
        this.probePoint = probePoint;
        this.holdPoint = holdPoint;
    }

    /**
     * Ends a probe that cleared its base: the whole flock starts TRANSIT to the strike point.
     *
     * @param strike point to strike
     * @param frame current frame
     */
    public void clearProbe(Position strike, int frame) {
        this.strikePoint = strike;
        this.phase = Phase.TRANSIT;
        this.lastProgressFrame = frame;
        clearProbeFields();
    }

    /**
     * Raises the prober's peak hit points to its hit points now, so regeneration does not hide a later hit.
     *
     * @param hitPoints the prober's hit points now
     */
    public void observeProberHitPoints(int hitPoints) {
        proberPeakHitPoints = Math.max(proberPeakHitPoints, hitPoints);
    }

    private void clearProbeFields() {
        this.proberId = -1;
        this.proberPeakHitPoints = 0;
        this.probeStartFrame = -1;
        this.probeCoreSighted = false;
        this.probePoint = null;
        this.holdPoint = null;
    }

    /**
     * Records every anti-air threat as known and returns the ones seen for the first time.
     *
     * @param threats every known anti-air threat
     * @return the threats not known before this call
     */
    public List<AirHarassTargeting.AirThreat> learnAntiAir(Collection<AirHarassTargeting.AirThreat> threats) {
        List<AirHarassTargeting.AirThreat> fresh = new ArrayList<>();
        for (AirHarassTargeting.AirThreat threat : threats) {
            if (knownAntiAir.add(threat.getId())) {
                fresh.add(threat);
            }
        }
        return fresh;
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
}
