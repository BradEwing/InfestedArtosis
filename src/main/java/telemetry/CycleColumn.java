package telemetry;

import unit.managed.AttackCycles;

import java.util.function.ToIntFunction;

/**
 * The attack cycle and cooldown kite columns appended to the engagement unit rows, in column order, each read from a
 * unit's {@link AttackCycles} and written as its change between the unit's arrival and its exit or death. See
 * {@link AttackCycles} for what each counts.
 */
enum CycleColumn {
    ATTACK_CYCLES("attack_cycles", AttackCycles::getCycles),
    CYCLE_FRAMES("cycle_frames_sum", AttackCycles::getCycleFrames),
    COOLDOWN_FRAMES_IN_THREAT("cooldown_frames_in_threat", AttackCycles::getCooldownFramesInThreat),
    COOLDOWN_DAMAGE_TAKEN("cooldown_damage_taken", AttackCycles::getCooldownDamageTaken),
    READY_FRAMES("ready_frames_out_of_range", AttackCycles::getReadyFrames),
    IN_REACH_CYCLES("in_reach_cycles", AttackCycles::getInReachCycles),
    IN_REACH_CYCLE_FRAMES("in_reach_cycle_frames_sum", AttackCycles::getInReachCycleFrames),
    COOLDOWN_FRAMES("cooldown_frames", AttackCycles::getCooldownFrames),
    HP_LOST("hp_lost_total", AttackCycles::getHitPointsLost),
    KITE_PLANS("kite_plans", AttackCycles::getKitePlans),
    KITE_BACKOFFS("kite_backoffs", AttackCycles::getKiteBackoffs),
    KITE_NO_CLEAR("kite_no_clear", AttackCycles::getKiteNoClear),
    KITE_NO_THREAT("kite_no_threat", AttackCycles::getKiteNoThreat),
    KITE_REPLANS("kite_replans", AttackCycles::getKiteReplans);

    private final String column;
    private final ToIntFunction<AttackCycles> reader;

    CycleColumn(String column, ToIntFunction<AttackCycles> reader) {
        this.column = column;
        this.reader = reader;
    }

    String column() {
        return column;
    }

    /**
     * @param cycles a unit's cycles
     * @return every column's count, indexed by ordinal
     */
    static int[] read(AttackCycles cycles) {
        CycleColumn[] columns = values();
        int[] counts = new int[columns.length];
        for (CycleColumn column : columns) {
            counts[column.ordinal()] = column.reader.applyAsInt(cycles);
        }
        return counts;
    }

    /**
     * @return the column names joined by commas, in column order
     */
    static String header() {
        StringBuilder header = new StringBuilder();
        for (CycleColumn column : values()) {
            if (header.length() > 0) {
                header.append(',');
            }
            header.append(column.column);
        }
        return header.toString();
    }
}
