package telemetry;

import bwapi.TilePosition;
import bwapi.UnitType;
import unit.scout.BaseCheckScheduler;

/**
 * Receives finished base checks. Implementations are registered with {@link BaseChecks} and must never
 * throw: they run inside the per frame scout loop, where an escaped exception kills the JVM.
 */
public interface BaseCheckSink {

    /**
     * A scout's check of one base ended.
     *
     * @param unitId the scout's unit id
     * @param unitType the scout's type
     * @param base the checked base's town hall location
     * @param ageAtDispatch frames the base had gone unseen when the check was dispatched, or a negative value
     *     if it had never been seen
     * @param dispatchFrame the frame the scout was sent
     * @param endFrame the frame the check ended, which for a check that saw its base is the arrival frame
     * @param outcome why the check ended
     * @param occupied whether an enemy stood within sight of the base when the check ended
     */
    void onBaseChecked(int unitId, UnitType unitType, TilePosition base, int ageAtDispatch, int dispatchFrame,
                       int endFrame, BaseCheckScheduler.Release outcome, boolean occupied);
}
