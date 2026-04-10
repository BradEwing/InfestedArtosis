package unit.managed;

import bwapi.Game;
import bwapi.Position;
import bwapi.Race;
import bwapi.TechType;
import bwapi.Unit;
import bwapi.UnitType;
import info.map.GameMap;

import java.util.List;
import java.util.stream.Collectors;

public class Defiler extends ManagedUnit {
    private static final int DARK_SWARM_ENERGY = 100;
    private static final int PLAGUE_ENERGY = 150;
    private static final int SAFE_DISTANCE = 256;
    private static final int SPELL_RANGE = 288;
    private static final int CONSUME_RANGE = 64;
    private static final int CAST_LOCKOUT_FRAMES = 36;
    private static final int PLAGUE_SPLASH_RADIUS = 64;
    private static final int DARK_SWARM_RADIUS = 192;

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

    @Override
    protected void retreat() {
        super.retreat();
    }

    private boolean tryCastSpells() {
        if (unit.getSpellCooldown() > 0) return false;

        int energy = unit.getEnergy();
        Race enemyRace = game.enemy().getRace();

        if (enemyRace == Race.Protoss) {
            if (energy >= PLAGUE_ENERGY && tryPlague()) return true;
            if (energy >= DARK_SWARM_ENERGY && tryDarkSwarm()) return true;
            if (energy < DARK_SWARM_ENERGY && tryConsume()) return true;
        } else {
            if (energy < DARK_SWARM_ENERGY && tryConsume()) return true;
            if (energy >= DARK_SWARM_ENERGY && tryDarkSwarm()) return true;
            if (energy >= PLAGUE_ENERGY && tryPlague()) return true;
        }

        return false;
    }

    private boolean tryConsume() {
        if (!game.self().hasResearched(TechType.Consume)) return false;

        List<Unit> candidates = game.getUnitsInRadius(unit.getPosition(), CONSUME_RANGE)
                .stream()
                .filter(u -> u.getPlayer() == game.self())
                .filter(u -> u.getType() == UnitType.Zerg_Zergling)
                .filter(Unit::exists)
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

        if (closest != null) {
            unit.useTech(TechType.Consume, closest);
            castLockoutUntilFrame = game.getFrameCount() + CAST_LOCKOUT_FRAMES;
            return true;
        }
        return false;
    }

    private boolean tryPlague() {
        if (!game.self().hasResearched(TechType.Plague)) return false;

        List<Unit> enemies = game.getUnitsInRadius(unit.getPosition(), SPELL_RANGE)
                .stream()
                .filter(u -> u.getPlayer().isEnemy(game.self()))
                .filter(u -> u.isDetected() && !u.isPlagued())
                .collect(Collectors.toList());

        if (enemies.isEmpty()) return false;

        Unit bestTarget = null;
        int bestScore = 0;

        for (Unit candidate : enemies) {
            int nearbyCount = 0;
            for (Unit other : enemies) {
                if (candidate.getDistance(other) <= PLAGUE_SPLASH_RADIUS) {
                    nearbyCount++;
                }
            }

            boolean highValue = isHighValuePlagueTarget(candidate.getType());
            int score = nearbyCount;
            if (highValue) score += 3;

            if (nearbyCount >= 2 || highValue) {
                if (score > bestScore) {
                    bestScore = score;
                    bestTarget = candidate;
                }
            }
        }

        if (bestTarget != null) {
            unit.useTech(TechType.Plague, bestTarget.getPosition());
            castLockoutUntilFrame = game.getFrameCount() + CAST_LOCKOUT_FRAMES;
            return true;
        }
        return false;
    }

    private boolean isHighValuePlagueTarget(UnitType type) {
        return type == UnitType.Protoss_Carrier
                || type == UnitType.Terran_Battlecruiser
                || type == UnitType.Terran_Siege_Tank_Siege_Mode
                || type == UnitType.Terran_Siege_Tank_Tank_Mode
                || type == UnitType.Protoss_Archon;
    }

    private boolean tryDarkSwarm() {
        List<Unit> friendlyMelee = game.getUnitsInRadius(unit.getPosition(), SPELL_RANGE)
                .stream()
                .filter(u -> u.getPlayer() == game.self())
                .filter(u -> isMeleeType(u.getType()))
                .collect(Collectors.toList());

        if (friendlyMelee.isEmpty()) return false;

        List<Unit> nearbyEnemies = game.getUnitsInRadius(unit.getPosition(), SPELL_RANGE)
                .stream()
                .filter(u -> u.getPlayer().isEnemy(game.self()))
                .filter(u -> u.isDetected() && !u.isUnderDarkSwarm())
                .collect(Collectors.toList());

        if (nearbyEnemies.isEmpty()) return false;

        List<Unit> engagedMelee = friendlyMelee.stream()
                .filter(m -> nearbyEnemies.stream().anyMatch(e -> m.getDistance(e) <= SAFE_DISTANCE))
                .collect(Collectors.toList());

        if (engagedMelee.isEmpty()) return false;

        for (Unit existing : game.getAllUnits()) {
            if (existing.getType() == UnitType.Spell_Dark_Swarm) {
                Position swarmPos = existing.getPosition();
                Position candidatePos = centroid(engagedMelee);
                if (candidatePos.getDistance(swarmPos) < DARK_SWARM_RADIUS) {
                    return false;
                }
            }
        }

        Position castPosition = centroid(engagedMelee);
        unit.useTech(TechType.Dark_Swarm, castPosition);
        castLockoutUntilFrame = game.getFrameCount() + CAST_LOCKOUT_FRAMES;
        return true;
    }

    private boolean isMeleeType(UnitType type) {
        return type == UnitType.Zerg_Zergling || type == UnitType.Zerg_Ultralisk;
    }

    private Position centroid(List<Unit> units) {
        int sumX = 0;
        int sumY = 0;
        for (Unit u : units) {
            sumX += u.getX();
            sumY += u.getY();
        }
        return new Position(sumX / units.size(), sumY / units.size());
    }

    private void moveToSafePosition() {
        List<Unit> nearbyEnemies = getEnemiesInRadius(unit.getX(), unit.getY());
        if (!nearbyEnemies.isEmpty()) {
            Position retreatPos = getSimpleRetreatPosition();
            if (retreatPos != null) {
                unit.move(retreatPos);
                return;
            }
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
