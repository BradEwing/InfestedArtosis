package unit.squad;

import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;
import info.tracking.ObservedUnit;
import util.Time;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Hunts down the last enemy structures, so a game the enemy can no longer fight ends instead of running to the
 * frame cap.
 *
 * <p>Every enemy structure we remember is a hunt target: gas structures, lifted Terran buildings and buildings
 * anywhere on the map. A fighter with nothing to attack walks to the nearest one it can hit, and to the nearest
 * one at all when it can hit none, which keeps vision on a lifted building for the fighters that can.
 *
 * <p>A building is taken as lifted when it was lifted the last time we saw it. The hunt turns to flying buildings
 * once the enemy has no unit we know of and every structure we know of is lifted, past a game time and supply floor
 * (see {@link #huntsFlyingBuildings}): the build order then asks for anti-air through {@link #antiAirRequest} until
 * {@link #ANTI_AIR_TARGET} anti-air units are alive or on the way.
 */
public class EndgameHunt {

    /**
     * Anti-air units the hunt keeps alive or on the way while only flying buildings remain. Tuning value.
     */
    public static final int ANTI_AIR_TARGET = 6;

    /**
     * The units we can make that fire on a flyer.
     */
    public static final List<UnitType> ANTI_AIR_UNITS = Collections.unmodifiableList(Arrays.asList(
            UnitType.Zerg_Hydralisk, UnitType.Zerg_Mutalisk, UnitType.Zerg_Scourge, UnitType.Zerg_Devourer));

    /**
     * What the build order makes for the hunt: a Mutalisk once the Spire stands, otherwise a Hydralisk once the
     * Hydralisk Den stands, otherwise the Hydralisk Den when it can be planned, and nothing else.
     */
    public enum AntiAirRequest {
        NONE,
        MUTALISK,
        HYDRALISK,
        HYDRALISK_DEN
    }

    /**
     * Earliest game time the hunt may turn to flying buildings. Tuning value: before it, an enemy army we have not
     * seen yet is the likelier reason we know of no enemy unit than a won game.
     */
    static final Time MIN_ANTI_AIR_TIME = new Time(10, 0);

    /**
     * Supply we must be using, in BWAPI's half-supply units, before the hunt may turn to flying buildings: 60 supply.
     * Tuning value: the enemy has no unit we know of by then, so this is the out-supply margin, and a bot that has
     * just traded its army away is not yet winning.
     */
    static final int MIN_OUR_SUPPLY_USED = 120;

    private final Map<Unit, Boolean> liftedAtLastSight = new HashMap<>();
    private boolean huntingFlyingBuildings;

    /**
     * Records whether each visible enemy building is lifted, and whether the hunt turns to flying buildings, see
     * {@link #huntsFlyingBuildings}.
     *
     * @param livingEnemies every enemy unit we track as living
     * @param frame the current frame
     * @param ourSupplyUsed the supply we are using, in BWAPI's half-supply units
     */
    public void update(Collection<ObservedUnit> livingEnemies, int frame, int ourSupplyUsed) {
        update(livingEnemies, frame, ourSupplyUsed, EndgameHunt::liftedIfVisible);
    }

    /**
     * @param liftedIfVisible whether a building is lifted while it is visible, or null while it is not
     */
    void update(Collection<ObservedUnit> livingEnemies, int frame, int ourSupplyUsed,
                Function<Unit, Boolean> liftedIfVisible) {
        int supplyUnits = 0;
        int lifted = 0;
        int grounded = 0;
        for (ObservedUnit observed : livingEnemies) {
            UnitType type = observed.getUnitType();
            if (!type.isBuilding()) {
                if (type.supplyRequired() > 0) {
                    supplyUnits++;
                }
                continue;
            }
            Unit unit = observed.getUnit();
            Boolean seenLifted = liftedIfVisible.apply(unit);
            if (seenLifted != null) {
                liftedAtLastSight.put(unit, seenLifted);
            }
            if (isLifted(unit)) {
                lifted++;
            } else {
                grounded++;
            }
        }
        huntingFlyingBuildings = huntsFlyingBuildings(onlyFlyingBuildingsRemain(supplyUnits, lifted, grounded),
                frame, ourSupplyUsed);
    }

    private static Boolean liftedIfVisible(Unit unit) {
        return unit.isVisible() ? unit.isLifted() : null;
    }

    /**
     * Whether the hunt turns to flying buildings, and so asks for anti-air: only flying buildings remain, the game
     * is at least {@link #MIN_ANTI_AIR_TIME} old and we use at least {@link #MIN_OUR_SUPPLY_USED}. The two floors
     * keep a mid-game scouting gap, where every building we know of happens to be lifted while the enemy army is
     * out of sight, from turning production over to anti-air.
     *
     * @param onlyFlyingBuildingsRemain whether {@link #onlyFlyingBuildingsRemain} holds
     * @param frame the current frame
     * @param ourSupplyUsed the supply we are using, in BWAPI's half-supply units
     */
    static boolean huntsFlyingBuildings(boolean onlyFlyingBuildingsRemain, int frame, int ourSupplyUsed) {
        return onlyFlyingBuildingsRemain
                && frame >= MIN_ANTI_AIR_TIME.getFrames()
                && ourSupplyUsed >= MIN_OUR_SUPPLY_USED;
    }

    /**
     * @return true while the hunt has turned to flying buildings, see {@link #huntsFlyingBuildings}
     */
    public boolean isHuntingFlyingBuildings() {
        return huntingFlyingBuildings;
    }

    /**
     * @return true when the building was lifted the last time we saw it
     */
    public boolean isLifted(Unit building) {
        return Boolean.TRUE.equals(liftedAtLastSight.get(building));
    }

    /**
     * The position of the remembered enemy structure an attacker hunts: the nearest one it can hit, or the nearest
     * one at all when it can hit none.
     *
     * @param from the attacker's position
     * @param livingEnemies every enemy unit we track as living
     * @param attackerType the attacker's type
     * @return the structure's current or last known position, or null when no structure's position is known
     */
    public Position huntPosition(Position from, Collection<ObservedUnit> livingEnemies, UnitType attackerType) {
        return huntPosition(from, livingEnemies, attackerType, ObservedUnit::getCurrentOrLastKnownPosition);
    }

    /**
     * @param positionOf a tracked unit's current or last known position, or null when it is unknown
     */
    Position huntPosition(Position from, Collection<ObservedUnit> livingEnemies, UnitType attackerType,
                          Function<ObservedUnit, Position> positionOf) {
        List<Target> targets = new ArrayList<>();
        for (ObservedUnit observed : livingEnemies) {
            if (!observed.getUnitType().isBuilding()) {
                continue;
            }
            Position position = positionOf.apply(observed);
            if (position != null) {
                targets.add(new Target(observed.getUnitType(), position, isLifted(observed.getUnit())));
            }
        }
        return closestTarget(from, targets, hitsGround(attackerType), hitsAir(attackerType));
    }

    /**
     * @return the nearest target that {@link #isHuntTarget} admits for the attacker, or the nearest target when it
     *     admits none, or null when there are no targets
     */
    static Position closestTarget(Position from, List<Target> targets, boolean hitsGround, boolean hitsAir) {
        Position closestHittable = null;
        Position closest = null;
        double hittableDistance = Double.MAX_VALUE;
        double distance = Double.MAX_VALUE;
        for (Target target : targets) {
            double d = from.getDistance(target.position);
            if (d < distance) {
                distance = d;
                closest = target.position;
            }
            if (isHuntTarget(target.type, target.lifted, hitsGround, hitsAir) && d < hittableDistance) {
                hittableDistance = d;
                closestHittable = target.position;
            }
        }
        return closestHittable != null ? closestHittable : closest;
    }

    /**
     * Whether an enemy structure is one an attacker can hunt: any building, a gas structure included, that the
     * attacker's weapons reach, a lifted building needing an air weapon and a grounded one a ground weapon.
     *
     * @param type the structure's type
     * @param lifted whether the structure was lifted the last time we saw it
     * @param hitsGround whether the attacker fires on ground targets
     * @param hitsAir whether the attacker fires on air targets
     */
    static boolean isHuntTarget(UnitType type, boolean lifted, boolean hitsGround, boolean hitsAir) {
        return type.isBuilding() && (lifted ? hitsAir : hitsGround);
    }

    /**
     * @param supplyUnits enemy units we know of that cost supply
     * @param lifted enemy buildings we know of that were lifted when last seen
     * @param grounded enemy buildings we know of that were not
     * @return true when the enemy has no unit we know of and every structure we know of is lifted
     */
    static boolean onlyFlyingBuildingsRemain(int supplyUnits, int lifted, int grounded) {
        return supplyUnits == 0 && lifted > 0 && grounded == 0;
    }

    /**
     * What the build order makes for the hunt this frame.
     *
     * @param huntingFlyingBuildings whether {@link #isHuntingFlyingBuildings()} holds
     * @param antiAirUnits our {@link #ANTI_AIR_UNITS} alive or on the way
     * @param spire whether our Spire stands
     * @param hydraliskDen whether our Hydralisk Den stands
     * @param canPlanHydraliskDen whether a Hydralisk Den can be planned now
     */
    public static AntiAirRequest antiAirRequest(boolean huntingFlyingBuildings, int antiAirUnits, boolean spire,
                                                boolean hydraliskDen, boolean canPlanHydraliskDen) {
        if (!huntingFlyingBuildings || antiAirUnits >= ANTI_AIR_TARGET) {
            return AntiAirRequest.NONE;
        }
        if (spire) {
            return AntiAirRequest.MUTALISK;
        }
        if (hydraliskDen) {
            return AntiAirRequest.HYDRALISK;
        }
        return canPlanHydraliskDen ? AntiAirRequest.HYDRALISK_DEN : AntiAirRequest.NONE;
    }

    static boolean hitsGround(UnitType type) {
        return type.groundWeapon() != null && type.groundWeapon() != WeaponType.None;
    }

    static boolean hitsAir(UnitType type) {
        return type.airWeapon() != null && type.airWeapon() != WeaponType.None;
    }

    /**
     * A remembered enemy structure.
     */
    static final class Target {
        private final UnitType type;
        private final Position position;
        private final boolean lifted;

        Target(UnitType type, Position position, boolean lifted) {
            this.type = type;
            this.position = position;
            this.lifted = lifted;
        }
    }
}
