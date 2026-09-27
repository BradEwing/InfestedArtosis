package unit.squad;

import bwapi.Position;
import util.StaticDefenseZone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Why a Lurker is sent out of fixed fire to a hold point, and why it lets go of one. The names are the reasons
 * telemetry_fixed_fire.csv records.
 */
final class LurkerHold {

    static final String HIT = "HIT";
    static final String RETREAT = "RETREAT";
    static final String MOVED = "MOVED";
    static final String COOLDOWN = "COOLDOWN";
    static final String TANK_ZONE = "TANK_ZONE";
    static final String RELEASE_COMMIT = "COMMIT";
    static final String RELEASE_CLEAR = "CLEAR";
    static final String RELEASE_STATUS = "STATUS";

    /**
     * Tuning value: pixels between a sieged-tank zone's centre and a sieged tank the sim priced for the tank to count
     * as priced. Half a tile: the zone and the sim read the same tracked position, and no two tanks stand this close.
     */
    static final int PRICED_MATCH_DISTANCE = 16;

    /**
     * Tuning value: pixels of margin out of the fire a new hold point must gain over the point a Lurker already holds
     * for the Lurker to be moved to it. One walk tile, so a point found again from where the Lurker stands never
     * unburrows it for no gain.
     */
    static final int MOVE_GAIN = 32;

    private LurkerHold() {
    }

    /**
     * Why a Lurker needs a new hold point this frame. One that already holds a point keeps it unless the fire has
     * moved onto it. One that holds none is sent out when it is hurt inside a sieged tank's reach, or when it is
     * retreating inside fixed fire that outranges it.
     *
     * @param holdCovered whether the point it holds is now inside fixed fire that outranges it
     * @param hitByTank whether it was hurt this frame inside a sieged tank's reach
     * @param retreatingInFire whether it holds the RETREAT role inside fixed fire that outranges it
     * @param holding whether it holds a point
     * @return {@link #MOVED}, {@link #HIT} or {@link #RETREAT}, or null when it needs no new point
     */
    static String reason(boolean holdCovered, boolean hitByTank, boolean retreatingInFire, boolean holding) {
        if (holding) {
            return holdCovered ? MOVED : null;
        }
        if (hitByTank) {
            return HIT;
        }
        return retreatingInFire ? RETREAT : null;
    }

    /**
     * Whether a Lurker holding a point the fire has moved onto is moved to a new point: only when the new point
     * stands at least {@link #MOVE_GAIN} further out of the fire.
     *
     * @param heldMargin margin of the point it holds out of the fire, negative inside it
     * @param newMargin margin of the new point out of the fire
     * @return true when the new point is worth unburrowing for
     */
    static boolean worthMoving(double heldMargin, double newMargin) {
        return newMargin >= heldMargin + MOVE_GAIN;
    }

    /**
     * Whether a squad's Lurkers commit with it this frame: it is in FIGHT, and it is either breaking its contain or
     * collapsing, a decision to fight with the whole squad, or the sim read ENGAGE for it this frame. A fight lock
     * with no ENGAGE read, including a squad born into FIGHT on its lock, does not commit them.
     *
     * @param status the squad's status
     * @param wholeSquadCommit whether the squad is breaking its contain or collapsing, under the fight lock it armed
     * @param freshVerdict the sim's verdict for the squad read this frame, or null when it was not read this frame
     * @return true when the squad's Lurkers commit
     */
    static boolean lurkersCommit(SquadStatus status, boolean wholeSquadCommit,
                                 CombatSimulator.CombatResult freshVerdict) {
        return status == SquadStatus.FIGHT
                && (wholeSquadCommit || freshVerdict == CombatSimulator.CombatResult.ENGAGE);
    }

    /**
     * The zones a squad's Lurkers keep out of. Lurkers that do not commit keep out of every zone. Lurkers committed by
     * a contain break or collapse keep out of none. Lurkers committed by an ENGAGE read keep out of the sieged-tank
     * zones whose tank that read did not price, see {@link #priced}: an ENGAGE never walks them into a tank it did not
     * weigh.
     *
     * @param zones fixed fire zones that outrange a Lurker
     * @param commit whether the squad's Lurkers commit, see {@link #lurkersCommit}
     * @param wholeSquadCommit whether the squad is breaking its contain or collapsing
     * @param pricedTanks positions of the sieged tanks the sim priced this frame
     * @return the zones the Lurkers keep out of
     */
    static List<StaticDefenseZone> keptOut(List<StaticDefenseZone> zones, boolean commit, boolean wholeSquadCommit,
                                           Collection<Position> pricedTanks) {
        if (!commit) {
            return zones;
        }
        if (wholeSquadCommit) {
            return Collections.emptyList();
        }
        List<StaticDefenseZone> kept = new ArrayList<>();
        for (StaticDefenseZone zone : FixedFire.siegedTankZones(zones)) {
            if (!priced(zone, pricedTanks)) {
                kept.add(zone);
            }
        }
        return kept;
    }

    /**
     * Whether a sieged-tank zone's tank was priced by the sim: a priced sieged tank stands within
     * {@link #PRICED_MATCH_DISTANCE} of the zone's centre.
     *
     * @param zone a sieged-tank zone
     * @param pricedTanks positions of the sieged tanks the sim priced
     * @return true when the zone's tank was priced
     */
    static boolean priced(StaticDefenseZone zone, Collection<Position> pricedTanks) {
        for (Position tank : pricedTanks) {
            if (tank.getDistance(zone.getCenter()) <= PRICED_MATCH_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pairs every unit with the first squad, in iteration order, that lists it, so a unit listed by two squads is
     * visited once per frame.
     *
     * @param squads the squads, in visiting order
     * @param members a squad's members
     * @param <S> the squad type
     * @param <U> the unit type
     * @return each unit and the first squad that lists it, in visiting order
     */
    static <S, U> Map<U, S> firstSquadOf(Collection<S> squads, Function<S, Collection<U>> members) {
        Map<U, S> first = new LinkedHashMap<>();
        for (S squad : squads) {
            for (U unit : members.apply(squad)) {
                first.putIfAbsent(unit, squad);
            }
        }
        return first;
    }

    /**
     * The units that held a point last frame and were not visited this frame: they left every fight squad, or died.
     *
     * @param holding units that held a point at the end of last frame
     * @param visited units visited this frame
     * @param <U> the unit type
     * @return the holding units not visited
     */
    static <U> List<U> leftBehind(Collection<U> holding, Set<U> visited) {
        List<U> left = new ArrayList<>();
        for (U unit : holding) {
            if (!visited.contains(unit)) {
                left.add(unit);
            }
        }
        return left;
    }
}
