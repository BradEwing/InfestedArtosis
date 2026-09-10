package strategy.buildorder.terran;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import info.tracking.StrategyTracker;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;
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

    static final int MAX_ZERGLINGS = 40;

    static final int MECH_ZERGLING_FLOOR = 12;

    static final int ZERGLINGS_PER_SIEGE_TANK = 4;

    static final int ZERGLINGS_PER_VULTURE = 2;

    static final int ZERGLINGS_PER_GOLIATH = 2;

    static final int ZERGLINGS_PER_FACTORY = 6;

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

    @Override
    protected int zerglingsNeeded(GameState gameState) {
        if (gameState.ourUnitCount(UnitType.Zerg_Spawning_Pool) < 1) {
            return 0;
        }

        if (gameState.isScvRushed()) {
            int currentZerglings = gameState.getUnitTypeCount().get(UnitType.Zerg_Zergling);
            if (currentZerglings < 12) {
                return 12;
            }
        }

        int zerglings = UNSCOUTED_ZERGLINGS;
        int currentZerglings = gameState.getUnitTypeCount().get(UnitType.Zerg_Zergling);
        int medicCount = gameState.enemyUnitCount(UnitType.Terran_Medic);
        int firebatCount = gameState.enemyUnitCount(UnitType.Terran_Firebat);
        int marineCount = gameState.enemyUnitCount(UnitType.Terran_Marine);
        int bunkerCount = gameState.enemyUnitCount(UnitType.Terran_Bunker);

        int siegeTankCount = gameState.enemyUnitCount(UnitType.Terran_Siege_Tank_Tank_Mode)
                + gameState.enemyUnitCount(UnitType.Terran_Siege_Tank_Siege_Mode);
        int vultureCount = gameState.enemyUnitCount(UnitType.Terran_Vulture);
        int goliathCount = gameState.enemyUnitCount(UnitType.Terran_Goliath);
        int factoryCount = gameState.enemyUnitCount(UnitType.Terran_Factory);

        zerglings += medicCount;
        zerglings -= firebatCount * 2;
        zerglings += marineCount * 2;
        zerglings += bunkerCount * 4;
        zerglings += mechDrivenZerglings(siegeTankCount, vultureCount, goliathCount, factoryCount);
        if (isMechComposition(gameState)) {
            zerglings = Math.max(zerglings, MECH_ZERGLING_FLOOR);
        }
        zerglings = Math.max(0, zerglings);
        if (currentZerglings >= zerglings) {
            return 0;
        }
        return Math.min(MAX_ZERGLINGS, zerglings);
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
     * requiredSunkens per base
     */
    @Override
    protected int requiredSunkens(GameState gameState) {
        if (gameState.isScvRushed() && gameState.getBaseData().getMyBases().size() == 1) {
            return 1;
        }

        int sunkens = 0;
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        Time gameTime = gameState.getGameTime();

        int medicCount = gameState.enemyUnitCount(UnitType.Terran_Medic);
        int firebatCount = gameState.enemyUnitCount(UnitType.Terran_Firebat);
        int marineCount = gameState.enemyUnitCount(UnitType.Terran_Marine);
        int vultureCount = gameState.enemyUnitCount(UnitType.Terran_Vulture);
        int factoryCount = gameState.enemyUnitCount(UnitType.Terran_Factory);
        int bioCount = marineCount + firebatCount + medicCount;
        boolean possibleEarlyBioPressire = bioCount > 5 && gameTime.lessThanOrEqual(new Time(5, 0));
        boolean is2RaxAcademy = strategyTracker.isDetectedStrategy("2RaxAcademy");
        if (gameTime.lessThanOrEqual(new Time(8, 0))) {
            if (is2RaxAcademy && gameTime.greaterThan(new Time(4, 0))) {
                sunkens = 3;
            } else if (possibleEarlyBioPressire) {
                sunkens += 1;
            }
        }

        if (gameTime.greaterThan(new Time(8, 0)) && gameState.numEconomyDrones() > 14) {
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

