package strategy.buildorder.zerg;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import info.Readiness;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.SunkenTargets;

import java.util.Collections;
import java.util.List;

public class ZergBase extends BuildOrder {

    static final int BASE_ZERGLING_TARGET = 10;
    static final int MAX_ZERGLING_TARGET = 40;

    static final int AIR_THREAT_SPORES = 1;

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
        return sporeTarget(
                gameState.enemyUnitCount(UnitType.Zerg_Spire) + gameState.enemyUnitCount(UnitType.Zerg_Greater_Spire),
                gameState.observedEnemyAirCombatUnitCount());
    }

    /**
     * Spores per base the Zerg matchup asks for.
     * <p>
     * A Spire is read as well as the flyers it makes, because a Spore needs an Evolution Chamber
     * first and the two together take longer to stand than a Spire takes to produce its first
     * Mutalisk. Waiting for the flyer leaves the Spore finishing after the raid it answers. The
     * flyer term covers a Spire that was never scouted. Overlords do not count: the flyer term is
     * {@link GameState#observedEnemyAirCombatUnitCount()}, which counts only flyers that carry a
     * weapon.
     * <p>
     * One per base, because the planner already spreads a per base target across every base we
     * hold, and each base has its own mineral line for a raid to reach.
     *
     * @param enemySpires living enemy Spires and Greater Spires we have observed
     * @param enemyAirCombatUnits living armed enemy flyers we have observed
     * @return spores per base
     */
    static int sporeTarget(int enemySpires, int enemyAirCombatUnits) {
        if (enemySpires > 0 || enemyAirCombatUnits > 0) {
            return AIR_THREAT_SPORES;
        }
        return 0;
    }

    @Override
    protected int matchupSunkens(GameState gameState) {
        int ourBaseCount = gameState.getBaseData().currentBaseCount();
        int enemyDepots = gameState.enemyResourceDepotCount();
        int ourZerglings = gameState.ourLivingUnitCount(UnitType.Zerg_Zergling);
        int enemyZerglings = gameState.enemyUnitCount(UnitType.Zerg_Zergling);

        if (enemyDepots > ourBaseCount || SunkenTargets.isZerglingLead(enemyZerglings, ourZerglings)) {
            return 1;
        }
        return 0;
    }

    @Override
    protected int zerglingsNeeded(GameState gameState) {
        if (gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Spawning_Pool) < 1) {
            return 0;
        }

        return zerglingTarget(
                gameState.ourUnitCount(UnitType.Zerg_Zergling),
                gameState.enemyUnitCount(UnitType.Zerg_Zergling),
                gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Lair),
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
