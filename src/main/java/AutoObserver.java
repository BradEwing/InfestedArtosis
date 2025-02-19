import bwapi.Game;
import bwapi.Position;
import bwapi.Unit;
import unit.scout.ScoutManager;

/**
 * Adapted from UAlbertaBot: https://github.com/davechurchill/ualbertabot/blob/master/UAlbertaBot/Source/AutoObserver.cpp
 *
 */
public class AutoObserver {
    private Game game;
    private ScoutManager scoutManager;
    private Unit m_observerFollowingUnit;
    private int m_cameraLastMoved;
    private int m_unitFollowFrames;

    public AutoObserver(Game game, ScoutManager scoutManager) {
        this.game = game;
        this.scoutManager = scoutManager;
    }

    public void onFrame() {
        boolean pickUnitToFollow = (m_observerFollowingUnit == null) ||
                !m_observerFollowingUnit.exists() ||
                (game.getFrameCount() - m_cameraLastMoved > m_unitFollowFrames);

        if (pickUnitToFollow) {
            for (Unit unit : game.self().getUnits()) {
                if (unit.isUnderAttack() || unit.isAttacking()) {
                    m_cameraLastMoved = game.getFrameCount();
                    m_unitFollowFrames = 6;
                    m_observerFollowingUnit = unit;
                    pickUnitToFollow = false;
                    break;
                }
            }
        }

        if (pickUnitToFollow) {
            for (Unit unit : game.self().getUnits()) {
                if (unit.isBeingConstructed() && unit.getRemainingBuildTime() < 12) {
                    m_cameraLastMoved = game.getFrameCount();
                    m_unitFollowFrames = 24;
                    m_observerFollowingUnit = unit;
                    pickUnitToFollow = false;
                    break;
                }
            }
        }

        if (pickUnitToFollow) {
            for (Unit unit : game.self().getUnits()) {
                if (scoutManager.isDroneScout(unit)) {
                    m_cameraLastMoved = game.getFrameCount();
                    m_unitFollowFrames = 6;
                    m_observerFollowingUnit = unit;
                    pickUnitToFollow = false;
                    break;
                }
            }
        }

        if (m_observerFollowingUnit != null && m_observerFollowingUnit.exists()) {
            Position unitPos = m_observerFollowingUnit.getPosition();
            Position screenPos = new Position(unitPos.getX() - 320, unitPos.getY() - 180);
            game.setScreenPosition(screenPos);
        }
    }
}