package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
import info.BaseData;
import info.GameState;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.protoss.ThreeHatchMuta;
import strategy.buildorder.terran.TwoHatchMuta;
import strategy.buildorder.zerg.OneHatchSpire;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TwelveHatch extends BuildOrder {

    public TwelveHatch() {
        super("12Hatch");
    }

    @Override
    public boolean playsRace(Race race) {
        return race != Race.Random;
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        BaseData baseData = gameState.getBaseData();
        int baseCount = baseData.currentBaseCount();
        int plannedHatcheries = gameState.getPlannedHatcheries();
        final int plannedAndCurrentHatcheries = plannedHatcheries + baseCount;
        int droneCount    = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int overlordCount = gameState.ourUnitCount(UnitType.Zerg_Overlord);

        if (droneCount < 8) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (overlordCount < 2 && droneCount >= 8) {
            plans.add(planUnit(gameState, UnitType.Zerg_Overlord));
            return plans;
        }

        if (droneCount < 12 && overlordCount >= 2) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (droneCount >= 12 && plannedAndCurrentHatcheries < 2) {
            Plan hatcheryPlan = this.planNewBase(gameState);
            if (hatcheryPlan != null) {
                plans.add(hatcheryPlan);
                return plans;
            }
        }

        return plans;
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
        Set<BuildOrder> next = new HashSet<>();
        Race opponentRace = gameState.getOpponentRace();
        switch (opponentRace) {
            case Protoss:
                next.add(new ThreeHatchMuta());
                return next;
            case Zerg:
                next.add(new OneHatchSpire());
                return next;
            case Terran:
                next.add(new TwoHatchMuta());
                return next;
        }
        return next;
    }

    @Override
    public boolean isOpener() { return true; }
}
