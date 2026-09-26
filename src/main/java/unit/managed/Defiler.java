package unit.managed;

import bwapi.Game;
import bwapi.Position;
import bwapi.TechType;
import bwapi.Unit;
import bwapi.UnitType;
import info.map.GameMap;
import info.tracking.DarkSwarm;
import unit.squad.SwarmLock;

import java.util.List;
import java.util.stream.Collectors;

public class Defiler extends ManagedUnit {
    private static final int DARK_SWARM_ENERGY = 100;
    private static final int PLAGUE_ENERGY = 150;
    private static final int SAFE_DISTANCE = 256;
    private static final int SPELL_RANGE = 288;
    private static final int CONSUME_CAST_RANGE = 32;
    private static final int CONSUME_SEARCH_RANGE = 256;
    private static final int CAST_LOCKOUT_FRAMES = 36;
    private static final int PLAGUE_SPLASH_RADIUS = 64;
    private static final int SWARM_HALF_WIDTH = UnitType.Spell_Dark_Swarm.dimensionLeft();
    /**
     * Radius searched around a cast point for existing swarms: any swarm whose footprint could overlap a footprint
     * centred on the point has its centre within one footprint width on each axis, so within this diagonal.
     */
    private static final int DARK_SWARM_RADIUS =
            (int) Math.ceil(Math.hypot(2 * SWARM_HALF_WIDTH, 2 * SWARM_HALF_WIDTH));
    /**
     * Frames left on a swarm below which a Defiler recasts over melee committed under it: the swarm lock horizon plus
     * the cast lockout, so the replacement is ordered before the lock on the old swarm lapses.
     */
    static final int RECAST_REMAINING_FRAMES = SwarmLock.MIN_REMAINING_FRAMES + CAST_LOCKOUT_FRAMES;

    private int castLockoutUntilFrame = 0;

    public Defiler(Game game, Unit unit, UnitRole role, GameMap gameMap) {
        super(game, unit, role, gameMap);
    }

    @Override
    protected void fight() {
        if (game.getFrameCount() < castLockoutUntilFrame) return;
        setUnready(6);

        if (tryCastSpells()) return;
        moveToSafePosition();
    }

    @Override
    protected void contain() {
        if (containPosition == null) {
            role = UnitRole.IDLE;
            return;
        }

        if (game.getFrameCount() >= castLockoutUntilFrame) {
            setUnready(6);
            if (tryCastSpells()) return;
        }

        if (unit.getDistance(containPosition) < 24) {
            setUnready(6);
            unit.holdPosition();
            return;
        }

        setUnready(6);
        unit.move(containPosition);
    }

    private boolean tryCastSpells() {
        if (unit.getSpellCooldown() > 0) return false;

        int energy = unit.getEnergy();
        int consumeThreshold = game.self().hasResearched(TechType.Plague) ? PLAGUE_ENERGY : DARK_SWARM_ENERGY;

        if (energy < consumeThreshold && tryConsume()) return true;
        if (energy >= PLAGUE_ENERGY && tryPlague()) return true;
        if (energy >= DARK_SWARM_ENERGY && tryDarkSwarm()) return true;

        return false;
    }

    private boolean tryConsume() {
        if (!game.self().hasResearched(TechType.Consume)) return false;

        List<Unit> candidates = game.getUnitsInRadius(unit.getPosition(), CONSUME_SEARCH_RANGE)
                .stream()
                .filter(u -> u.getPlayer() == game.self())
                .filter(u -> u.getType() == UnitType.Zerg_Zergling)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) return false;

        Unit closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Unit candidate : candidates) {
            double d = unit.getDistance(candidate);
            if (d < closestDist) {
                closestDist = d;
                closest = candidate;
            }
        }

        if (closestDist <= CONSUME_CAST_RANGE) {
            unit.useTech(TechType.Consume, closest);
            castLockoutUntilFrame = game.getFrameCount() + CAST_LOCKOUT_FRAMES;
            return true;
        }

        List<Unit> nearbyEnemies = getEnemiesInRadius(unit.getX(), unit.getY());
        if (!nearbyEnemies.isEmpty()) return false;

        unit.move(closest.getPosition());
        return true;
    }

    private boolean tryPlague() {
        if (!game.self().hasResearched(TechType.Plague)) return false;

        List<Unit> enemies = game.getUnitsInRadius(unit.getPosition(), SPELL_RANGE)
                .stream()
                .filter(u -> u.getPlayer().isEnemy(game.self()))
                .filter(u -> u.isDetected() && !u.isPlagued())
                .filter(u -> !u.getType().isBuilding() || util.Filter.isHostileBuilding(u.getType()))
                .collect(Collectors.toList());

        if (enemies.isEmpty()) return false;

        Unit bestTarget = null;
        int bestScore = 0;

        for (Unit candidate : enemies) {
            int nearbyCount = 0;
            int splashHp = 0;
            for (Unit other : enemies) {
                if (candidate.getDistance(other) <= PLAGUE_SPLASH_RADIUS) {
                    nearbyCount++;
                    splashHp += other.getHitPoints();
                }
            }

            boolean highValue = isHighValuePlagueTarget(candidate.getType());
            int score = splashHp / 50 + nearbyCount;
            if (highValue) score += 3;

            if (nearbyCount >= 2 || highValue) {
                if (score > bestScore) {
                    bestScore = score;
                    bestTarget = candidate;
                }
            }
        }

        if (bestTarget != null) {
            unit.useTech(TechType.Plague, bestTarget);
            castLockoutUntilFrame = game.getFrameCount() + CAST_LOCKOUT_FRAMES;
            return true;
        }
        return false;
    }

    private boolean isHighValuePlagueTarget(UnitType type) {
        return type == UnitType.Terran_Marine
                || type == UnitType.Terran_Battlecruiser
                || type == UnitType.Terran_Science_Vessel
                || type == UnitType.Zerg_Mutalisk
                || type == UnitType.Protoss_Carrier
                || type == UnitType.Protoss_Reaver
                || type == UnitType.Protoss_Dragoon;
    }

    /**
     * Casts Dark Swarm where our melee is fighting or about to fight.
     *
     * <p>The aim is the pair of one of our melee units and an enemy it could be fighting that stand closest together,
     * and the swarm goes down between them, see {@link #castPoint}. Buildings that cannot attack a ground unit are left
     * out of the aim, so a Supply Depot on a wall does not draw the cast. Nothing is cast unless a melee unit is within
     * {@link #SAFE_DISTANCE} of an aim target, nor where its footprint would overlap one of our live swarms, unless
     * that swarm is about to lapse under melee committed to it, see {@link #blocksCast}.
     */
    private boolean tryDarkSwarm() {
        List<Unit> friendlyMelee = game.getUnitsInRadius(unit.getPosition(), SPELL_RANGE)
                .stream()
                .filter(u -> u.getPlayer() == game.self())
                .filter(u -> SwarmLock.isMelee(u.getType()))
                .collect(Collectors.toList());

        if (friendlyMelee.isEmpty()) return false;

        List<Unit> aimTargets = game.getUnitsInRadius(unit.getPosition(), SPELL_RANGE)
                .stream()
                .filter(u -> u.getPlayer().isEnemy(game.self()))
                .filter(u -> u.isDetected() && isAimTarget(u.getType()))
                .collect(Collectors.toList());

        if (aimTargets.isEmpty()) return false;

        Unit front = null;
        Unit target = null;
        double closest = Double.MAX_VALUE;
        for (Unit melee : friendlyMelee) {
            for (Unit enemy : aimTargets) {
                double distance = melee.getDistance(enemy);
                if (distance < closest) {
                    closest = distance;
                    front = melee;
                    target = enemy;
                }
            }
        }

        if (closest > SAFE_DISTANCE) return false;

        Position castPosition = castPoint(front.getPosition(), target.getPosition());

        for (Unit existing : game.getUnitsInRadius(castPosition, DARK_SWARM_RADIUS)) {
            if (existing.getType() != UnitType.Spell_Dark_Swarm) continue;
            DarkSwarm swarm = new DarkSwarm(existing.getID(), existing.getPosition(), existing.getRemoveTimer());
            if (blocksCast(swarm, meleeUnder(swarm, friendlyMelee), castPosition)) {
                return false;
            }
        }
        unit.useTech(TechType.Dark_Swarm, castPosition);
        castLockoutUntilFrame = game.getFrameCount() + CAST_LOCKOUT_FRAMES;
        return true;
    }

    private static boolean meleeUnder(DarkSwarm swarm, List<Unit> friendlyMelee) {
        for (Unit melee : friendlyMelee) {
            if (swarm.overlaps(melee.getPosition(), melee.getType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an enemy of this type may draw a Dark Swarm: any unit, and a building only when it can attack a ground
     * unit.
     *
     * @param type the enemy's type
     * @return true when the enemy is in the aim set
     */
    static boolean isAimTarget(UnitType type) {
        return !type.isBuilding() || util.Filter.isHostileBuildingToGround(type);
    }

    /**
     * Where to cast from our melee unit nearest the enemy toward that enemy: the swarm's centre is placed up to half
     * the footprint's width ahead of the melee unit, so the footprint's near edge sits on our front and it reaches as
     * far toward the enemy as it can. An enemy within that half width puts the centre on the enemy.
     *
     * @param front our melee unit's position
     * @param target the enemy's position
     * @return the cast position
     */
    static Position castPoint(Position front, Position target) {
        double distance = front.getDistance(target);
        if (distance <= 0) return target;
        double scale = Math.min(distance, SWARM_HALF_WIDTH) / distance;
        int x = (int) Math.round(front.getX() + (target.getX() - front.getX()) * scale);
        int y = (int) Math.round(front.getY() + (target.getY() - front.getY()) * scale);
        return new Position(x, y);
    }

    /**
     * Whether an existing swarm rules out casting at a point: the new footprint, centred on the point, would overlap
     * the existing one, and the existing one is not about to lapse under melee committed to it. A swarm with fewer
     * than {@link #RECAST_REMAINING_FRAMES} left and our melee under it is recast over.
     *
     * @param existing the existing swarm
     * @param meleeUnder whether any of our melee units stands under it
     * @param castPosition the point the cast would target
     * @return true when the cast is refused
     */
    static boolean blocksCast(DarkSwarm existing, boolean meleeUnder, Position castPosition) {
        if (existing.gap(castPosition) > SWARM_HALF_WIDTH) return false;
        return !meleeUnder || existing.getRemainingFrames() >= RECAST_REMAINING_FRAMES;
    }

    private void moveToSafePosition() {
        Position retreatPos = getSimpleRetreatPosition();
        if (retreatPos != null) {
            unit.move(retreatPos);
            return;
        }

        if (fightTarget != null && fightTarget.exists()) {
            double distance = unit.getDistance(fightTarget);
            if (distance > SPELL_RANGE) {
                unit.move(fightTarget.getPosition());
                return;
            }
        }

        if (rallyPoint != null) {
            unit.move(rallyPoint);
        }
    }
}
