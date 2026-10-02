package telemetry;

/**
 * Why a Lurker was ordered to burrow or unburrow, the reason column of telemetry_lurker_burrow.csv.
 */
public enum BurrowReason {
    CONTAIN_HOLD,
    CONTAIN_ENEMY_IN_RANGE,
    CONTAIN_POINT_MOVED,
    UNDER_FIRE_WITHDRAW,
    FIGHT_ENEMY_IN_RANGE,
    FIGHT_NO_TARGET,
    FIGHT_TARGET_OUT_OF_RANGE,
    FIGHT_IDLE,
    RETREAT,
    RETREAT_HOLD,
    RALLY,
    SCOUT
}
