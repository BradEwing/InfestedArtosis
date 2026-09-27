package unit.squad;

import bwapi.Position;
import bwem.Base;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Everything a squad in {@link SquadStatus#HARASS} carries between frames: the phase, the base it is raiding and
 * the point it strikes, the hit points it started with, what it has killed and lost, and the per-Mutalisk memory
 * that keeps evasion and targets from flapping.
 *
 * <p>TRANSIT is the flight to the target base. STRIKE starts once the flock reaches it. A retarget to another base
 * starts TRANSIT again.
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
        if (base != null) {
            visitedBases.add(base);
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
