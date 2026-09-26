package unit.squad.horizon;

import bwapi.ExplosionType;
import bwapi.Position;
import bwapi.UnitType;
import bwapi.WeaponType;
import info.tracking.DarkSwarm;

import java.util.List;

/**
 * Prices our Dark Swarms into the combat sim.
 *
 * <p>A swarm negates ordinary ranged direct attacks on ground non-building units under it. It does not negate melee,
 * splash, sieged tanks included, spells or Spider Mines, and gives flyers and buildings no cover. The sim prices
 * that on the enemy side: an enemy whose ground attack the swarm negates loses the share of its ground strength
 * aimed at our covered units, and every other enemy keeps its full price.
 *
 * <p>How covered one of our units is falls with its distance to the footprint and with the swarm's remaining time:
 * {@link #unitCover}. The squad's cover is those values weighted by each unit's strength, see {@link #coverShare}.
 */
public final class SwarmCover {

    /**
     * Frames the sim's verdict is read as holding for, the horizon SquadManager simulates over. A swarm with this
     * long or longer left covers the whole engagement being judged; one with less covers that fraction of it.
     */
    public static final int HORIZON_FRAMES = 150;

    /**
     * Tuning value: gap in pixels between a unit's box and the footprint at which it counts as no longer pathing
     * into the swarm. Cover falls linearly from full at the footprint's edge to nothing here. It matches the radius
     * {@link HorizonCombatSimulator#distanceWeight} keeps at full weight, so a unit priced fully into the engagement
     * can also be priced as reaching its swarm.
     */
    static final double NEAR_DISTANCE = 256;

    /**
     * Tuning value: the largest share of a negatable enemy's ground strength the sim removes, reached only when all
     * our priced ground strength stands under a swarm with the full horizon left. Mechanically the negation is total;
     * the residual is kept because the sim cannot see a unit stepping out of the box, stale enemy positions, Spider
     * Mines or Irradiate, and it holds the equivalent friendly multiplier at 4.
     */
    static final double MAX_NEGATION = 0.75;

    private static final int MELEE_MAX_RANGE = 32;

    private SwarmCover() {
    }

    /**
     * Whether the swarm negates this enemy's ground attack: an ordinary ranged direct weapon, whose explosion type
     * is Normal and whose range is beyond melee, or a Bunker, whose occupants fire such weapons.
     *
     * <p>Melee, every splash explosion (a sieged tank's, a Lurker's, a Firebat's, a Spider Mine's) and a unit with
     * no ground weapon of its own stay fully priced. A Sunken Colony is kept at full price as well: the sources the
     * negation rests on do not establish what its tentacle does under a swarm, so it is not assumed negated.
     *
     * @param enemyType the enemy unit's type
     * @return true when its ground attack is negated against a covered unit
     */
    public static boolean isNegated(UnitType enemyType) {
        if (enemyType == UnitType.Terran_Bunker) {
            return true;
        }
        if (enemyType == UnitType.Zerg_Sunken_Colony) {
            return false;
        }
        WeaponType weapon = enemyType.groundWeapon();
        if (weapon == null || weapon == WeaponType.None) {
            return false;
        }
        return weapon.explosionType() == ExplosionType.Normal && weapon.maxRange() > MELEE_MAX_RANGE;
    }

    /**
     * Multiplier on an enemy's ground strength for the cover our force stands under.
     *
     * @param enemyType the enemy unit's type
     * @param coverShare our cover share, see {@link #coverShare}
     * @return 1 for an enemy the swarm does not negate, otherwise down to 1 - {@link #MAX_NEGATION} at full cover
     */
    public static double groundMultiplier(UnitType enemyType, double coverShare) {
        if (coverShare <= 0 || !isNegated(enemyType)) {
            return 1.0;
        }
        return 1.0 - MAX_NEGATION * Math.min(coverShare, 1.0);
    }

    /**
     * How covered one of our units is over the sim horizon, from 0 to 1.
     *
     * <p>A flyer or a building gets no cover. A ground unit takes the best of the active swarms: the product of how
     * close it is, full when its box overlaps the footprint and falling to nothing at {@link #NEAR_DISTANCE}, and
     * how much of the horizon the swarm still covers once the unit has walked the gap at its top speed.
     *
     * @param position the unit's position
     * @param type the unit's type
     * @param topSpeed the unit's top speed in pixels per frame, upgrades included
     * @param swarms our active swarms
     * @return the unit's cover
     */
    public static double unitCover(Position position, UnitType type, double topSpeed, List<DarkSwarm> swarms) {
        if (type.isFlyer() || type.isBuilding() || swarms.isEmpty()) {
            return 0;
        }
        double best = 0;
        for (DarkSwarm swarm : swarms) {
            best = Math.max(best, cover(swarm.gap(position, type), topSpeed, swarm.getRemainingFrames()));
        }
        return best;
    }

    /**
     * Cover from one swarm at a given gap to its footprint.
     *
     * @param gap edge to edge gap between the unit's box and the footprint, 0 when they overlap
     * @param topSpeed the unit's top speed in pixels per frame
     * @param remainingFrames frames the swarm has left
     * @return the cover, from 0 to 1
     */
    static double cover(double gap, double topSpeed, int remainingFrames) {
        if (gap >= NEAR_DISTANCE) {
            return 0;
        }
        if (gap > 0 && topSpeed <= 0) {
            return 0;
        }
        double proximity = 1.0 - gap / NEAR_DISTANCE;
        double travelFrames = gap > 0 ? gap / topSpeed : 0;
        return proximity * remainingShare(remainingFrames - travelFrames);
    }

    /**
     * Share of the sim horizon a swarm covers.
     *
     * @param framesLeft frames of swarm left once the unit is under it
     * @return 1 with the full horizon or more left, 0 with none, linear between
     */
    static double remainingShare(double framesLeft) {
        if (framesLeft <= 0) {
            return 0;
        }
        return Math.min(1.0, framesLeft / HORIZON_FRAMES);
    }

    /**
     * Our force's cover: each unit's cover weighted by its strength, over the strength of our ground units.
     *
     * @param covers each unit's cover, see {@link #unitCover}
     * @param weights each unit's strength weight, in the same order; flyers carry cover 0 and are left out by the
     *                caller
     * @return the cover share, 0 when no weight was given
     */
    public static double coverShare(List<Double> covers, List<Double> weights) {
        double covered = 0;
        double total = 0;
        for (int i = 0; i < covers.size(); i++) {
            covered += covers.get(i) * weights.get(i);
            total += weights.get(i);
        }
        return total > 0 ? covered / total : 0;
    }
}
