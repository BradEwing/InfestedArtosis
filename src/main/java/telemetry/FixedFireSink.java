package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import util.StaticDefenseZone;

/**
 * Receives the fixed fire cooldowns and Lurker holds. Implementations are registered with {@link FixedFireTelemetry}
 * and must never throw: they run inside the per frame squad loop, where an escaped exception kills the JVM.
 */
public interface FixedFireSink {

    /**
     * One of our units was hurt inside a fixed fire zone that was not already cooling, which starts its cooldown.
     *
     * @param frame current frame
     * @param unitId the unit hurt
     * @param unitType its type
     * @param position where it stood
     * @param zone the zone it stood in
     */
    void onCooldownStarted(int frame, int unitId, UnitType unitType, Position position, StaticDefenseZone zone);

    /**
     * A fighter's target was skipped because it stands inside a cooling zone. Written once per attacker and target
     * per cooldown.
     *
     * @param frame current frame
     * @param attackerId the fighter
     * @param attackerType its type
     * @param attackerPosition where it stood
     * @param targetId the target skipped
     * @param targetType its type
     * @param targetPosition where it stood
     * @param zone the cooling zone covering it
     */
    void onTargetSkipped(int frame, int attackerId, UnitType attackerType, Position attackerPosition, int targetId,
                         UnitType targetType, Position targetPosition, StaticDefenseZone zone);

    /**
     * A Lurker was given a point out of fixed fire to hold.
     *
     * @param frame current frame
     * @param lurkerId the Lurker
     * @param position where it stood
     * @param holdPoint the point it holds
     * @param zone the zone that sent it out
     * @param reason what sent it out
     */
    void onLurkerHold(int frame, int lurkerId, Position position, Position holdPoint, StaticDefenseZone zone,
                      String reason);

    /**
     * A Lurker let go of its hold point.
     *
     * @param frame current frame
     * @param lurkerId the Lurker
     * @param position where it stood
     * @param holdPoint the point it held
     * @param reason why it let go
     */
    void onLurkerHoldReleased(int frame, int lurkerId, Position position, Position holdPoint, String reason);
}
