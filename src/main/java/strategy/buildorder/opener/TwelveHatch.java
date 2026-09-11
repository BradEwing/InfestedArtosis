package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
import info.BaseData;
import info.GameState;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TwelveHatch extends BuildOrder {

    private static final int HATCHERY_SUPPLY = 24;

    public TwelveHatch() {
        super("12Hatch");
    }

    @Override
    public boolean playsRace(Race race) {
        return race != Race.Unknown;
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        BaseData baseData = gameState.getBaseData();
        int baseCount = baseData.currentBaseCount();
        int plannedHatcheries = gameState.getPlannedHatcheries();
        final int plannedAndCurrentHatcheries = plannedHatcheries + baseCount;
        int droneCount    = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int supplyUsed    = gameState.getSupply();
        int overlordCount = gameState.ourUnitCount(UnitType.Zerg_Overlord);

        if (droneCount < 9) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (droneCount < 12 && overlordCount >= 2) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (shouldPlanHatchery(supplyUsed, plannedAndCurrentHatcheries)) {
            Plan hatcheryPlan = this.planNewBase(gameState);
            if (hatcheryPlan != null) {
                plans.add(hatcheryPlan);
                return plans;
            }
        }

        return plans;
    }

    static boolean shouldPlanHatchery(int supplyUsed, int plannedAndCurrentHatcheries) {
        return supplyUsed >= HATCHERY_SUPPLY && plannedAndCurrentHatcheries < 2;
    }

    @Override
    public boolean shouldTransition(GameState gameState) {
        BaseData baseData = gameState.getBaseData();
        int baseCount = baseData.currentBaseCount();
        int droneCount = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int plannedHatcheries = gameState.getPlannedHatcheries();
        final int plannedAndCurrentHatcheries = plannedHatcheries + baseCount;
        return plannedAndCurrentHatcheries >= 2 && droneCount == 12;
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
