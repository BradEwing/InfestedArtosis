package strategy.buildorder.terran;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import info.tracking.StrategyTracker;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.SunkenTargets;
import util.Time;

import java.util.Collections;
import java.util.List;

public class TerranBase extends BuildOrder {
    /**
     * Drones the opening pushes to before the build order moves on. Counted as gatherers rather
     * than as living drones, so it is one lower than the count it replaced: a drone scouting or
     * walking to a build site no longer fills the target.
     */
    static final int EARLY_DRONE_TARGET = 14;

    static final int UNSCOUTED_ZERGLINGS = 4;

    static final int SCV_RUSH_ZERGLINGS = 12;

    static final int MAX_ZERGLINGS = 40;

    static final int MECH_ZERGLING_FLOOR = 12;

    static final int ZERGLINGS_PER_SIEGE_TANK = 4;

    static final int ZERGLINGS_PER_VULTURE = 2;

    static final int ZERGLINGS_PER_GOLIATH = 2;

    static final int ZERGLINGS_PER_FACTORY = 6;

    static final int TWO_RAX_ACADEMY_SUNKENS = 3;

    static final int EARLY_BIO_PRESSURE_BIO = 5;

    static final int EARLY_BIO_PRESSURE_SUNKENS = 1;

    private static final int MIDGAME_SUNKEN_DRONES = 14;

    private static final Time TWO_RAX_ACADEMY_OPENS = new Time(4, 0);

    private static final Time TWO_RAX_ACADEMY_CLOSES = new Time(8, 0);

    private static final Time MIDGAME_SUNKEN_OPENS = new Time(8, 0);

    protected TerranBase(String name) {
        super(name);
    }

    /**
     * True while the opening drone-up should take the next larva. Zerglings owed by
     * {@link #zerglingsNeeded(GameState)} come first, so the transition frame after the pool
     * finishes does not spend every larva on drones.
     *
     * @param droneCount gathering plus queued drones, from {@link GameState#numEconomyDrones()}
     * @param zerglingCount zerglings owned and queued
     * @param desiredZerglings zerglings the matchup still owes
     * @return true when the next larva should morph a drone
     */
    static boolean shouldDroneBeforeZerglings(int droneCount, int zerglingCount, int desiredZerglings) {
        return droneCount < EARLY_DRONE_TARGET && zerglingCount >= desiredZerglings;
    }

    /**
     * Zerglings the enemy's mechanical army asks for, as a counterpart to the bio terms.
     * <p>
     * A Terran that leaves bio behind drives every bio term to zero, so without this the target
     * is the unscouted constant no matter how large the mech army is. Factories stand in for the
     * units they have not built yet and are taken as a floor rather than added on top, so a
     * scouted factory and the tanks it produced are not counted twice. Spider mines are left out:
     * they are not something more zerglings answer.
     *
     * @param siegeTanks living enemy siege tanks, both modes
     * @param vultures living enemy vultures
     * @param goliaths living enemy goliaths
     * @param factories living enemy factories
     * @return zerglings the mech army adds to the matchup target
     */
    static int mechDrivenZerglings(int siegeTanks, int vultures, int goliaths, int factories) {
        int fromUnits = siegeTanks * ZERGLINGS_PER_SIEGE_TANK
                + vultures * ZERGLINGS_PER_VULTURE
                + goliaths * ZERGLINGS_PER_GOLIATH;
        int fromProduction = factories * ZERGLINGS_PER_FACTORY;
        return Math.max(fromUnits, fromProduction);
    }

    /**
     * The zergling count the matchup asks the build order to reach, or zero once it is reached.
     * <p>
     * committedZerglings is living plus planned rather than living alone, because this answers how
     * many more to queue: a plan that has left the queue for an egg still counts against the
     * target. The bio terms and the mech term are added together, so a Terran holding both is
     * priced for both; the mech floor then covers the compositions the additive terms cannot see,
     * such as a scouted machine shop or a spider mine with no factory behind it yet. The sum is
     * clamped non-negative because firebats subtract, and capped at {@link #MAX_ZERGLINGS}.
     *
     * @param committedZerglings zerglings alive or already planned
     * @param medics living enemy medics
     * @param firebats living enemy firebats
     * @param marines living enemy marines
     * @param bunkers living enemy bunkers
     * @param mechDrivenZerglings zerglings the mech army asks for, from {@link #mechDrivenZerglings}
     * @param mechComposition whether the enemy army reads as mech at all
     * @return the target, or zero when it is already met
     */
    static int zerglingTarget(int committedZerglings, int medics, int firebats, int marines, int bunkers,
                              int mechDrivenZerglings, boolean mechComposition) {
        int zerglings = UNSCOUTED_ZERGLINGS;

        zerglings += medics;
        zerglings -= firebats * 2;
        zerglings += marines * 2;
        zerglings += bunkers * 4;
        zerglings += mechDrivenZerglings;
        if (mechComposition) {
            zerglings = Math.max(zerglings, MECH_ZERGLING_FLOOR);
        }
        zerglings = Math.max(0, zerglings);

        if (committedZerglings >= zerglings) {
            return 0;
        }
        return Math.min(MAX_ZERGLINGS, zerglings);
    }

    @Override
    protected int zerglingsNeeded(GameState gameState) {
        if (gameState.ourUnitCount(UnitType.Zerg_Spawning_Pool) < 1) {
            return 0;
        }

        int committedZerglings = gameState.getUnitTypeCount().get(UnitType.Zerg_Zergling);
        if (gameState.isScvRushed() && committedZerglings < SCV_RUSH_ZERGLINGS) {
            return SCV_RUSH_ZERGLINGS;
        }

        int mechDriven = mechDrivenZerglings(
                gameState.enemyUnitCount(UnitType.Terran_Siege_Tank_Tank_Mode)
                        + gameState.enemyUnitCount(UnitType.Terran_Siege_Tank_Siege_Mode),
                gameState.enemyUnitCount(UnitType.Terran_Vulture),
                gameState.enemyUnitCount(UnitType.Terran_Goliath),
                gameState.enemyUnitCount(UnitType.Terran_Factory));

        return zerglingTarget(committedZerglings,
                gameState.enemyUnitCount(UnitType.Terran_Medic),
                gameState.enemyUnitCount(UnitType.Terran_Firebat),
                gameState.enemyUnitCount(UnitType.Terran_Marine),
                gameState.enemyUnitCount(UnitType.Terran_Bunker),
                mechDriven,
                isMechComposition(gameState));
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        return Collections.emptyList();
    }

    @Override
    public boolean playsRace(Race race) {
        return race == Race.Terran;
    }

    @Override
    protected int requiredSpores(GameState gameState) {
        int spores = 0;

        if (gameState.enemyUnitCount(UnitType.Terran_Starport) > 0) {
            spores = 1;
        }

        if (gameState.enemyUnitCount(UnitType.Terran_Wraith) > 0) {
            spores = Math.max(spores, 1);
        }

        if (gameState.enemyUnitCount(UnitType.Terran_Valkyrie) > 0) {
            spores = Math.max(spores, 1);
        }

        if (gameState.enemyUnitCount(UnitType.Terran_Science_Vessel) > 0) {
            spores = Math.max(spores, 1);
        }

        if (gameState.enemyUnitCount(UnitType.Terran_Ghost) > 0
                || gameState.enemyUnitCount(UnitType.Terran_Science_Facility) > 0) {
            spores = Math.max(spores, 1);
        }

        if (gameState.enemyUnitCount(UnitType.Terran_Battlecruiser) > 0) {
            spores = Math.max(spores, 2);
        }

        return spores;
    }

    /**
     * Sunkens per base the enemy's bio reads as owing.
     * <p>
     * The branches are ordered by threat and exactly one fires. Detected strategies accumulate in
     * a set and are never retracted, so an enemy matching several of these matches them all, and
     * a lower branch running afterwards would lower an answer a higher one had already given.
     * <p>
     * The Barracks and bio terms carry no expiry. Both read living observed units, so they fall
     * when the production and the army behind them fall, which is the only thing that should lower
     * a defensive target; a clock lets the target reach zero while the army it answers is still
     * growing. The 2RaxAcademy branch keeps its window because it reads a timing commitment rather
     * than an army: it prices the push that build makes, and past the window the Barracks and bio
     * terms are what still describe what is on the field.
     *
     * @param enemyBarracks living enemy Barracks we have observed
     * @param bioCount living enemy marines, firebats and medics
     * @param twoRaxAcademy whether StrategyTracker has detected 2RaxAcademy
     * @param gameTime current game time
     * @return sunkens per base the bio read asks for
     */
    static int bioPressureSunkens(int enemyBarracks, int bioCount, boolean twoRaxAcademy, Time gameTime) {
        if (SunkenTargets.isBarracksPressure(enemyBarracks)) {
            return SunkenTargets.BARRACKS_PRESSURE_SUNKENS;
        } else if (twoRaxAcademy && gameTime.greaterThan(TWO_RAX_ACADEMY_OPENS)
                && gameTime.lessThanOrEqual(TWO_RAX_ACADEMY_CLOSES)) {
            return TWO_RAX_ACADEMY_SUNKENS;
        } else if (bioCount > EARLY_BIO_PRESSURE_BIO) {
            return EARLY_BIO_PRESSURE_SUNKENS;
        }
        return 0;
    }

    /**
     * Sunkens per base the Terran matchup asks for: the bio read, plus the terms a mech opponent
     * and a grown economy add on top of it.
     */
    @Override
    protected int matchupSunkens(GameState gameState) {
        if (gameState.isScvRushed() && gameState.getBaseData().getMyBases().size() == 1) {
            return 1;
        }

        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        Time gameTime = gameState.getGameTime();

        int medicCount = gameState.enemyUnitCount(UnitType.Terran_Medic);
        int firebatCount = gameState.enemyUnitCount(UnitType.Terran_Firebat);
        int marineCount = gameState.enemyUnitCount(UnitType.Terran_Marine);
        int vultureCount = gameState.enemyUnitCount(UnitType.Terran_Vulture);
        int factoryCount = gameState.enemyUnitCount(UnitType.Terran_Factory);

        int sunkens = bioPressureSunkens(
                gameState.enemyUnitCount(UnitType.Terran_Barracks),
                marineCount + firebatCount + medicCount,
                strategyTracker.isDetectedStrategy("2RaxAcademy"),
                gameTime);

        if (gameTime.greaterThan(MIDGAME_SUNKEN_OPENS) && gameState.numEconomyDrones() > MIDGAME_SUNKEN_DRONES) {
            sunkens += 1;
        }
        if (factoryCount > 0 || vultureCount > 1) {
            sunkens += 1;
        }

        return sunkens;
    }

    protected boolean isMechComposition(GameState gameState) {
        int tankCount = gameState.enemyUnitCount(UnitType.Terran_Siege_Tank_Tank_Mode) +
                        gameState.enemyUnitCount(UnitType.Terran_Siege_Tank_Siege_Mode);
        int machineShopCount = gameState.enemyUnitCount(UnitType.Terran_Machine_Shop);
        int spiderMineCount = gameState.enemyUnitCount(UnitType.Terran_Vulture_Spider_Mine);
        int goliathCount = gameState.enemyUnitCount(UnitType.Terran_Goliath);
        int factoryCount = gameState.enemyUnitCount(UnitType.Terran_Factory);
        
        return tankCount > 0 || machineShopCount > 0 || spiderMineCount > 0 || 
               goliathCount > 0 || factoryCount >= 2;
    }
}

