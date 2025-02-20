import bwapi.Game;
import bwapi.Position;
import bwapi.Unit;
import config.Config;
import unit.managed.ManagedUnit;
import unit.scout.ScoutManager;
import unit.squad.Squad;
import unit.squad.SquadManager;

/**
 * Adapted from UAlbertaBot: https://github.com/davechurchill/ualbertabot/blob/master/UAlbertaBot/Source/AutoObserver.cpp
 *
 */
public class AutoObserver {
    private Config config;
    private Game game;
    private ScoutManager scoutManager;
    private SquadManager squadManager;

    private Unit m_observerFollowingUnit;
    private int m_cameraLastMoved;
    private int m_unitFollowFrames;

    public AutoObserver(Config config, Game game, ScoutManager scoutManager, SquadManager squadManager) {
        this.config = config;
        this.game = game;
        this.scoutManager = scoutManager;
        this.squadManager = squadManager;
    }

    private static final int FOLLOW_DURATION_DEFAULT = 24;
    private static final int FOLLOW_DURATION_SCOUT = 6;
    private static final int SCREEN_WIDTH_HALF = 320;
    private static final int SCREEN_HEIGHT_HALF = 180;
    private static final int BUILD_TIME_THRESHOLD = 12;

    /**
     * Automatically updates the camera position to follow relevant game units based on priority:
     * 1. Units under attack or attacking
     * 2. First unit in the largest squad
     * 3. Units nearing construction completion
     * 4. Scout drones (with shorter follow duration)
     *
     * Executes each frame when auto-observer is enabled in configuration.
     */
    public void onFrame() {
        if (!config.enabledAutoObserver) {
            return;
        }

        if (shouldPickNewUnitToFollow()) {
            followHighestPriorityUnit();
        }

        updateCameraPosition();
    }

    /**
     * Determines if we need to select a new unit to follow based on:
     * - No current followed unit
     * - Current unit no longer exists
     * - Follow duration has expired
     */
    private boolean shouldPickNewUnitToFollow() {
        return m_observerFollowingUnit == null
                || !m_observerFollowingUnit.exists()
                || (game.getFrameCount() - m_cameraLastMoved > m_unitFollowFrames);
    }

    /**
     * Attempts to find and follow a unit based on priority rules.
     * Stops checking lower priority categories once a valid unit is found.
     */
    private void followHighestPriorityUnit() {
        Unit unitToFollow = findUnitUnderAttackOrAttacking();

        if (unitToFollow == null) {
            unitToFollow = findFirstUnitInLargestSquad();
        }

        if (unitToFollow == null) {
            unitToFollow = findAlmostCompletedBuilding();
        }

        if (unitToFollow == null) {
            unitToFollow = findScoutDrone();
        }

        if (unitToFollow != null) {
            int followDuration = (unitToFollow == findScoutDrone())
                    ? FOLLOW_DURATION_SCOUT : FOLLOW_DURATION_DEFAULT;
            startFollowingUnit(unitToFollow, followDuration);
        }
    }

    /**
     * Finds the first unit that is either under attack or attacking
     */
    private Unit findUnitUnderAttackOrAttacking() {
        for (Unit unit : game.self().getUnits()) {
            if (unit.isUnderAttack() || unit.isAttacking()) {
                return unit;
            }
        }
        return null;
    }

    /**
     * Finds the first unit in the largest available squad
     */
    private Unit findFirstUnitInLargestSquad() {
        Squad largestSquad = squadManager.largestSquad();
        if (largestSquad != null && !largestSquad.getMembers().isEmpty()) {
            for (ManagedUnit managedUnit: largestSquad.getMembers()) {
                return managedUnit.getUnit();
            }
        }
        return null;
    }

    /**
     * Finds a unit that's near completion of construction
     */
    private Unit findAlmostCompletedBuilding() {
        for (Unit unit : game.self().getUnits()) {
            if (unit.isBeingConstructed() && unit.getRemainingBuildTime() < BUILD_TIME_THRESHOLD) {
                return unit;
            }
        }
        return null;
    }

    /**
     * Finds the first valid scout drone unit
     */
    private Unit findScoutDrone() {
        for (Unit unit : game.self().getUnits()) {
            if (scoutManager.isDroneScout(unit)) {
                return unit;
            }
        }
        return null;
    }

    /**
     * Starts following a unit with specified follow duration
     */
    private void startFollowingUnit(Unit unit, int followDuration) {
        m_cameraLastMoved = game.getFrameCount();
        m_unitFollowFrames = followDuration;
        m_observerFollowingUnit = unit;
    }

    /**
     * Updates camera position to center on the followed unit
     */
    private void updateCameraPosition() {
        if (m_observerFollowingUnit != null && m_observerFollowingUnit.exists()) {
            Position unitPos = m_observerFollowingUnit.getPosition();
            Position screenPos = new Position(
                    unitPos.getX() - SCREEN_WIDTH_HALF,
                    unitPos.getY() - SCREEN_HEIGHT_HALF
            );
            game.setScreenPosition(screenPos);
        }
    }
}