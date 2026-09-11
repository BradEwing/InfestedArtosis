package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
import info.BaseData;
import info.GameState;
import info.TechProgression;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * The 3 Hatch before Pool build is designed to give the Zerg a large economic advantage over any fast expansion build 
 * that the opponent tries. It is weak against cheese and the Zerg player is forced to play passively. In addition, 
 * tech is generally slower in the 3 Hatch before Pool build due to the later pool and gas. 
 * However, the Zerg early game, and thus mid game and late game, economy will be much stronger when using this build 
 * because of the large amount of early production this build gives. 
 * </p>
 * @see https://liquipedia.net/starcraft/3_Hatch_before_Pool
 */
public class ThreeHatchBeforePool extends BuildOrder {

    private static final int FIRST_HATCHERY_SUPPLY = 24;

    private static final int SECOND_HATCHERY_SUPPLY = 28;

    public ThreeHatchBeforePool() {
        super("3HatchBeforePool");
    }

    @Override
    public boolean playsRace(Race race) {
        return race == Race.Terran || race == Race.Protoss;
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        BaseData baseData = gameState.getBaseData();
        TechProgression techProgression = gameState.getTechProgression();
        int baseCount = baseData.currentBaseCount();
        int plannedHatcheries = gameState.getPlannedHatcheries();
        final int plannedAndCurrentBases = plannedHatcheries + baseCount;
        int droneCount = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int supplyUsed = gameState.getSupply();
        int zerglingCount = gameState.ourUnitCount(UnitType.Zerg_Zergling);

        int extractorCount = baseData.numExtractor();
        boolean wantFirstGas = gameState.canPlanExtractor() && plannedAndCurrentBases > 2 && extractorCount < 1;

        if (droneCount < 9) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (droneCount < 12 && plannedAndCurrentBases < 2) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (shouldPlanFirstHatchery(supplyUsed, plannedAndCurrentBases)) {
            Plan hatcheryPlan = this.planNewBase(gameState);
            if (hatcheryPlan != null) {
                plans.add(hatcheryPlan);
                return plans;
            }
        }

        if (droneCount < 13 && plannedAndCurrentBases >= 2) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (droneCount < 14 && plannedAndCurrentBases < 3) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (shouldPlanSecondHatchery(supplyUsed, plannedAndCurrentBases)) {
            Plan hatcheryPlan = this.planNewBase(gameState);
            if (hatcheryPlan != null) {
                plans.add(hatcheryPlan);
                return plans;
            }
        }

        if (droneCount >= 13 && plannedAndCurrentBases >= 3 && techProgression.canPlanPool()) {
            plans.add(planSpawningPool(gameState));
            return plans;
        }

        if (wantFirstGas) {
            plans.add(planExtractor(gameState));
            return plans;
        }

        if (gameState.ourUnitCount(UnitType.Zerg_Spawning_Pool) > 0 && zerglingCount < 4) {
            plans.add(planUnit(gameState, UnitType.Zerg_Zergling));
            return plans;
        }

        if (droneCount < 16 && gameState.canPlanDrone()) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        return plans;
    }

    static boolean shouldPlanFirstHatchery(int supplyUsed, int plannedAndCurrentBases) {
        return supplyUsed >= FIRST_HATCHERY_SUPPLY && plannedAndCurrentBases < 2;
    }

    static boolean shouldPlanSecondHatchery(int supplyUsed, int plannedAndCurrentBases) {
        return supplyUsed >= SECOND_HATCHERY_SUPPLY && plannedAndCurrentBases < 3;
    }

    @Override
    public boolean shouldTransition(GameState gameState) {
        int poolCount = gameState.ourUnitCount(UnitType.Zerg_Spawning_Pool);
        int zerglingCount = gameState.ourUnitCount(UnitType.Zerg_Zergling);
        return poolCount > 0 && zerglingCount >= 4;
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        return OpenerTransitions.forRace(gameState.getOpponentRace());
    }

    @Override
    public boolean isOpener() { 
        return true; 
    }
}

