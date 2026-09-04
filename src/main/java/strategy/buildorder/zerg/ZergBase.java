package strategy.buildorder.zerg;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;

import java.util.Collections;
import java.util.List;

public class ZergBase extends BuildOrder {

    static final int BASE_ZERGLING_TARGET = 10;
    static final int MAX_ZERGLING_TARGET = 40;

    protected ZergBase(String name) {
        super(name);
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        return Collections.emptyList();
    }

    @Override
    public boolean playsRace(Race race) {
        return race == Race.Zerg;
    }

    @Override
    protected int requiredSpores(GameState gameState) {
        return 0;
    }

    @Override
    protected int requiredSunkens(GameState gameState) {
        int ourBaseCount = gameState.getBaseData().currentBaseCount();
        int enemyDepots = gameState.enemyResourceDepotCount();
        int ourZerglings = gameState.ourLivingUnitCount(UnitType.Zerg_Zergling);
        int enemyZerglings = gameState.enemyUnitCount(UnitType.Zerg_Zergling);

        if (enemyDepots > ourBaseCount || enemyZerglings >= ourZerglings + 3) {
            return 1;
        }
        return 0;
    }

    @Override
    protected int zerglingsNeeded(GameState gameState) {
        if (gameState.ourUnitCount(UnitType.Zerg_Spawning_Pool) < 1) {
            return 0;
        }

        return zerglingTarget(
                gameState.ourUnitCount(UnitType.Zerg_Zergling),
                gameState.enemyUnitCount(UnitType.Zerg_Zergling),
                gameState.ourUnitCount(UnitType.Zerg_Lair),
                gameState.getTechProgression().isMetabolicBoost(),
                gameState.getResourceCount().availableMinerals());
    }

    /**
     * The zergling count the matchup asks the build order to reach, or zero once it is reached.
     * <p>
     * committedZerglings is living plus planned rather than living alone, because this answers how
     * many more to queue. A plan that has left the queue for an egg still has to count, or the
     * target is asked for again on every frame the egg is morphing and the queue floods. The
     * planned count moves in twos because one zergling plan hatches a pair, which is what a
     * forward looking target wants and what a "can I fight now" guard must not read.
     *
     * @param committedZerglings zerglings alive or already planned
     * @param enemyZerglings zerglings the opponent is known to have
     * @param lairCount our lairs
     * @param hasMetabolicBoost whether speed has finished
     * @param availableMinerals minerals not already reserved
     * @return the target, or zero when it is already met
     */
    static int zerglingTarget(int committedZerglings, int enemyZerglings, int lairCount, boolean hasMetabolicBoost, int availableMinerals) {
        int zerglings = BASE_ZERGLING_TARGET;

        zerglings += enemyZerglings;
        if (lairCount > 0) {
            zerglings += 6;
        }
        if (hasMetabolicBoost) {
            zerglings += 2;
        }

        final int excessMinerals = availableMinerals - 400;
        if (excessMinerals > 0) {
            int excessZerglings = excessMinerals / 50;
            zerglings += excessZerglings * 2;
        }

        zerglings = Math.min(zerglings, MAX_ZERGLING_TARGET);

        if (committedZerglings >= zerglings) {
            return 0;
        }
        return zerglings;
    }
}
