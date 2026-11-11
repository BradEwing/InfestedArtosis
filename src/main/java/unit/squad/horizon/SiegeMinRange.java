package unit.squad.horizon;

import bwapi.Position;
import bwapi.UnitType;
import info.tracking.EnemyReachMemory;
import lombok.Getter;

import java.util.List;

/**
 * Which of our ground units a sieged Siege Tank can fire on, read from where each one stands.
 *
 * <p>A sieged tank's cannon cannot fire on a target at or inside its weapon's minimum range: JBWAPI's
 * Unit#isInWeaponRange holds only while the minimum range is below the distance, and that distance is the gap between
 * the two units' boxes, Unit#getDistance(Unit). The gap here is {@link EnemyReachMemory#footprintGap} between the
 * tank's box and our unit's box, both by their types' dimensions, the same measure the tank's learned reach is read in.
 * It is the Euclidean gap rather than the game's approximate distance, which differs by a few pixels on a diagonal.
 *
 * <p>A tank is priced by the share of our weighted ground strength outside its own minimum range, so a unit inside one
 * tank's minimum range still counts against every other tank whose minimum range it is outside. Members beyond a
 * tank's reach stay in that share: how far the squad stands from the tank's outer edge is already priced by
 * {@link HorizonCombatSimulator#enemyDistanceWeight}, and this only removes the hole at the tank's feet.
 */
final class SiegeMinRange {

    static final UnitType SIEGED = UnitType.Terran_Siege_Tank_Siege_Mode;

    /**
     * Minimum range of the sieged tank's ground weapon, in pixels, as JBWAPI reports it.
     */
    static final int MIN_RANGE = SIEGED.groundWeapon().minRange();

    private SiegeMinRange() {
    }

    /**
     * One of our ground units at the engagement, weighed by its strength standing on the ground.
     */
    @Getter
    @lombok.RequiredArgsConstructor
    static final class Member {
        private final UnitType type;
        private final Position position;
        private final double strength;
    }

    /**
     * A sieged tank the simulator sampled, at the reach it was priced at.
     */
    @Getter
    @lombok.RequiredArgsConstructor
    static final class Tank {
        private final Position position;
        private final int reach;
        private final double hitShare;
    }

    /**
     * Where our weighted ground strength stands against every sampled sieged tank.
     */
    @Getter
    @lombok.RequiredArgsConstructor
    static final class Shares {
        private final double inside;
        private final double covered;
    }

    /**
     * Whether a sieged tank at the given position cannot fire on one of our units: the gap between their boxes is at
     * most {@link #MIN_RANGE}.
     *
     * @param member our unit
     * @param tank where the tank stands
     * @return true when our unit is inside the tank's minimum range
     */
    static boolean inside(Member member, Position tank) {
        return gap(member, tank) <= MIN_RANGE;
    }

    /**
     * Whether a sieged tank can fire on one of our units where it stands: the gap between their boxes is past
     * {@link #MIN_RANGE} and no more than the tank's priced reach.
     *
     * @param member our unit
     * @param tank the tank
     * @return true when our unit is between the tank's minimum range and its reach
     */
    static boolean covered(Member member, Tank tank) {
        int gap = gap(member, tank.getPosition());
        return gap > MIN_RANGE && gap <= tank.getReach();
    }

    /**
     * The share of our weighted ground strength a sieged tank can fire on: everything outside its minimum range.
     *
     * @param members our ground units
     * @param tank where the tank stands
     * @return the share from 0 to 1, and 1 when our ground units carry no strength
     */
    static double hitShare(List<Member> members, Position tank) {
        double total = 0;
        double inside = 0;
        for (Member member : members) {
            total += member.getStrength();
            if (inside(member, tank)) {
                inside += member.getStrength();
            }
        }
        if (total <= 0) return 1.0;
        return 1.0 - inside / total;
    }

    /**
     * The share of our weighted ground strength inside the minimum range of at least one sampled tank, and the share
     * at least one sampled tank can fire on where it stands, see {@link #covered}.
     *
     * @param members our ground units
     * @param tanks the sampled sieged tanks
     * @return both shares, each from 0 to 1, and both 0 when our ground units carry no strength
     */
    static Shares shares(List<Member> members, List<Tank> tanks) {
        double total = 0;
        double inside = 0;
        double covered = 0;
        for (Member member : members) {
            total += member.getStrength();
            boolean in = false;
            boolean hit = false;
            for (Tank tank : tanks) {
                in |= inside(member, tank.getPosition());
                hit |= covered(member, tank);
            }
            if (in) {
                inside += member.getStrength();
            }
            if (hit) {
                covered += member.getStrength();
            }
        }
        if (total <= 0) return new Shares(0, 0);
        return new Shares(inside / total, covered / total);
    }

    private static int gap(Member member, Position tank) {
        return EnemyReachMemory.footprintGap(SIEGED, tank, member.getType(), member.getPosition());
    }
}
