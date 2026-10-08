package telemetry;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;

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
     * @param end how the check ended
     */
    void onBaseChecked(int unitId, UnitType unitType, TilePosition base, int ageAtDispatch, int dispatchFrame,
                       BaseCheckEnd end);

    /**
     * A check was held back or recalled for safety.
     *
     * @param frame the current frame
     * @param base the base the check was meant for
     * @param reason why it did not go
     * @param site the death site or static defence that caused it
     */
    default void onBaseCheckSkipped(int frame, TilePosition base, BaseCheckSkip reason, Position site) {
    }
}
