package info.tracking.terran;

import bwapi.UnitType;
import info.tracking.StrategyDetectionContext;

import java.util.Arrays;
import java.util.List;

/**
 * A Terran army that has gone mechanical. The evidence is any living Machine Shop, Goliath, Siege Tank
 * (either mode) or Spider Mine, or two or more Factories. A single Factory is not evidence, since bio
 * builds open one for a Starport or Vultures without leaving bio.
 *
 * <p>The predicate is shared by the in-game builds, through {@link #matches}, and by detection, so what
 * a build reacts to and what is written to the learning file's detected strategies cannot drift apart.
 * Detections are never retracted within a game, and the name is read back by {@link #isMechIn} and
 * {@link #isPersistent} in later games.
 */
public class TerranMech extends TerranBaseStrategy {

    public static final String NAME = "TerranMech";

    /**
     * How many of the most recent games the next-game prior looks back over.
     */
    public static final int RECENT_GAMES = 3;

    /**
     * How many of the {@link #RECENT_GAMES} must have detected mech for it to count as the opponent's habit.
     */
    static final int PERSISTENT_MECH_GAMES = 2;

    static final int MECH_FACTORIES = 2;

    public TerranMech() {
        super(NAME);
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        return matches(
                context.getTracker().getCountOfLivingUnits(
                        UnitType.Terran_Siege_Tank_Tank_Mode, UnitType.Terran_Siege_Tank_Siege_Mode),
                context.getTracker().getCountOfLivingUnits(UnitType.Terran_Machine_Shop),
                context.getTracker().getCountOfLivingUnits(UnitType.Terran_Vulture_Spider_Mine),
                context.getTracker().getCountOfLivingUnits(UnitType.Terran_Goliath),
                context.getTracker().getCountOfLivingUnits(UnitType.Terran_Factory));
    }

    /**
     * Whether the living enemy units counted are mech evidence.
     *
     * @param siegeTanks living siege tanks, both modes
     * @param machineShops living Machine Shops
     * @param spiderMines living Spider Mines
     * @param goliaths living Goliaths
     * @param factories living Factories
     * @return true when any of the unit terms is present or there are at least {@link #MECH_FACTORIES} Factories
     */
    public static boolean matches(int siegeTanks, int machineShops, int spiderMines, int goliaths, int factories) {
        return siegeTanks > 0 || machineShops > 0 || spiderMines > 0 || goliaths > 0 || factories >= MECH_FACTORIES;
    }

    /**
     * Whether the detected strategies, joined by ';' as the learning file records them, name mech.
     */
    public static boolean isMechIn(String detectedStrategies) {
        if (detectedStrategies == null || detectedStrategies.isEmpty()) {
            return false;
        }
        return Arrays.asList(detectedStrategies.split(";")).contains(NAME);
    }

    /**
     * Whether mech was detected in at least {@link #PERSISTENT_MECH_GAMES} of the last {@link #RECENT_GAMES}
     * games, given each game's detected strategies oldest first.
     */
    public static boolean isPersistent(List<String> gamesDetectedStrategies) {
        long mechGames = gamesDetectedStrategies
                .subList(Math.max(0, gamesDetectedStrategies.size() - RECENT_GAMES), gamesDetectedStrategies.size())
                .stream()
                .filter(TerranMech::isMechIn)
                .count();
        return mechGames >= PERSISTENT_MECH_GAMES;
    }
}
