package strategy.buildorder;

import bwapi.Race;
import bwapi.UnitType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Spore Colony targets shared by the build order layer.
 *
 * <p>Each race's target is a pure function of what has been observed, so a build order that does
 * not extend that race's base class can ask for the same number. SpeedlingAllIn plays every
 * matchup and extends {@link BuildOrder} directly, so without a shared home it would either
 * duplicate the numbers or have none at all.
 *
 * <p>The counts are read through a {@link ToIntFunction} rather than a long parameter list. A
 * caller passes {@code gameState::enemyUnitCount} and a test passes a map lookup, so the unit
 * types a race's target reads stay here next to the number they produce.
 */
public final class SporeTargets {

    /**
     * Spores per base a sighting that reads as air or cloak asks for.
     *
     * <p>One per base, because the planner spreads a per base target across every base we hold and
     * each base has its own mineral line for a raid to reach.
     */
    public static final int AIR_THREAT_SPORES = 1;

    /**
     * Spores per base the capital ship tech of a race asks for, which one Spore does not answer.
     */
    public static final int HEAVY_AIR_SPORES = 2;

    /**
     * Protoss sightings that ask a base for a Spore.
     *
     * <p>Air tech and armed flyers, plus the cloaked and dropping units a Spore is wanted against
     * as a detector: Dark Templar, Arbiter and the Templar Archives behind them, and the Observer
     * or Shuttle that arrives over a mineral line.
     */
    private static final List<UnitType> PROTOSS_SPORE_SIGHTINGS = Collections.unmodifiableList(Arrays.asList(
            UnitType.Protoss_Stargate,
            UnitType.Protoss_Corsair,
            UnitType.Protoss_Scout,
            UnitType.Protoss_Dark_Templar,
            UnitType.Protoss_Arbiter,
            UnitType.Protoss_Templar_Archives,
            UnitType.Protoss_Observer,
            UnitType.Protoss_Shuttle));

    /**
     * Terran sightings that ask a base for a Spore: the Starport line, and the Ghost and Science
     * Facility a Spore detects cloak for.
     */
    private static final List<UnitType> TERRAN_SPORE_SIGHTINGS = Collections.unmodifiableList(Arrays.asList(
            UnitType.Terran_Starport,
            UnitType.Terran_Wraith,
            UnitType.Terran_Valkyrie,
            UnitType.Terran_Science_Vessel,
            UnitType.Terran_Ghost,
            UnitType.Terran_Science_Facility));

    /**
     * Zerg sightings that ask a base for a Spore. The Spire is read as well as the flyers it
     * makes, because a Spore needs an Evolution Chamber first and the two together take longer to
     * stand than a Spire takes to produce its first Mutalisk.
     */
    private static final List<UnitType> ZERG_SPORE_SIGHTINGS = Collections.unmodifiableList(Arrays.asList(
            UnitType.Zerg_Spire,
            UnitType.Zerg_Greater_Spire));

    private SporeTargets() {
    }

    /**
     * Spores per base the opponent's race asks for.
     *
     * <p>Zero while the race is Unknown. A Random opponent reveals nothing until a unit is seen,
     * and every sighting that raises a target is a unit of a known race, so there is no threat to
     * price and no reason to spend the Evolution Chamber a Spore needs.
     *
     * @param opponentRace the opponent's race, Unknown until it is revealed
     * @param enemyUnitCount living observed enemy units of a type
     * @param enemyAirCombatUnits living armed enemy flyers we have observed
     * @return spores per base
     */
    public static int sporeTarget(Race opponentRace, ToIntFunction<UnitType> enemyUnitCount, int enemyAirCombatUnits) {
        switch (opponentRace) {
            case Protoss:
                return protossSpores(enemyUnitCount);
            case Terran:
                return terranSpores(enemyUnitCount);
            case Zerg:
                return zergSpores(enemyUnitCount, enemyAirCombatUnits);
            default:
                return 0;
        }
    }

    /**
     * Spores per base the Protoss matchup asks for.
     *
     * <p>A Fleet Beacon is the one sighting a single Spore does not answer: it is Carriers, whose
     * interceptors outrange and outlast what one colony can shoot down.
     *
     * @param enemyUnitCount living observed enemy units of a type
     * @return spores per base
     */
    public static int protossSpores(ToIntFunction<UnitType> enemyUnitCount) {
        if (enemyUnitCount.applyAsInt(UnitType.Protoss_Fleet_Beacon) > 0) {
            return HEAVY_AIR_SPORES;
        }
        return anyObserved(enemyUnitCount, PROTOSS_SPORE_SIGHTINGS) ? AIR_THREAT_SPORES : 0;
    }

    /**
     * Spores per base the Terran matchup asks for.
     *
     * <p>A Battlecruiser is the one sighting a single Spore does not answer.
     *
     * @param enemyUnitCount living observed enemy units of a type
     * @return spores per base
     */
    public static int terranSpores(ToIntFunction<UnitType> enemyUnitCount) {
        if (enemyUnitCount.applyAsInt(UnitType.Terran_Battlecruiser) > 0) {
            return HEAVY_AIR_SPORES;
        }
        return anyObserved(enemyUnitCount, TERRAN_SPORE_SIGHTINGS) ? AIR_THREAT_SPORES : 0;
    }

    /**
     * Spores per base the Zerg matchup asks for.
     *
     * <p>The flyer term covers a Spire that was never scouted. Overlords do not count: it is fed
     * by the armed flyer filter, so the Overlords every Zerg opponent flies raise nothing while
     * the Mutalisks they precede do.
     *
     * @param enemyUnitCount living observed enemy units of a type
     * @param enemyAirCombatUnits living armed enemy flyers we have observed
     * @return spores per base
     */
    public static int zergSpores(ToIntFunction<UnitType> enemyUnitCount, int enemyAirCombatUnits) {
        if (enemyAirCombatUnits > 0 || anyObserved(enemyUnitCount, ZERG_SPORE_SIGHTINGS)) {
            return AIR_THREAT_SPORES;
        }
        return 0;
    }

    private static boolean anyObserved(ToIntFunction<UnitType> enemyUnitCount, List<UnitType> sightings) {
        for (UnitType unitType : sightings) {
            if (enemyUnitCount.applyAsInt(unitType) > 0) {
                return true;
            }
        }
        return false;
    }
}
