package unit.squad;

import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;
import info.GameState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Combat simulator implementation for Mutalisk squads.
 * Uses specialized engagement evaluation logic tailored for air units.
 */
public class MutaliskCombatSimulator implements CombatSimulator {

    @Override
    public CombatResult evaluate(Squad squad, GameState gameState) {
        Set<Unit> enemyUnits = gameState.getDetectedEnemyUnits();
        Set<Unit> enemyBuildings = gameState.getEnemyBuildings();

        // Get all enemy units and buildings within reasonable range
        List<Unit> allEnemies = new ArrayList<>();
        for (Unit enemy : enemyUnits) {
            if (enemy.isDetected() && !enemy.isInvincible()) {
                allEnemies.add(enemy);
            }
        }
        for (Unit building : enemyBuildings) {
            if (building.isDetected() && !building.isInvincible()) {
                allEnemies.add(building);
            }
        }

        if (allEnemies.isEmpty()) {
            return CombatResult.ENGAGE;
        }

        return evaluateMutaliskEngagement(squad, allEnemies) ? CombatResult.ENGAGE : CombatResult.RETREAT;
    }

    /**
     * Evaluates whether mutalisks should engage based on anti-air threats and squad composition.
     */
    private boolean evaluateMutaliskEngagement(Squad squad, List<Unit> enemies) {
        // Count anti-air threats
        int antiAirThreats = 0;
        int antiAirDps = 0;

        for (Unit enemy : enemies) {
            if (isAntiAir(enemy) && squad.getCenter().getDistance(enemy.getPosition()) < 160) {
                antiAirThreats++;
                antiAirDps += getAntiAirDps(enemy);
            }
        }

        int squadSize = squad.size();
        int mutaliskHp = squadSize * 120;
        int expectedDamagePerSecond = antiAirDps;

        // Don't engage if we'll lose more than 5% HP in 2 seconds
        if (expectedDamagePerSecond * 2 > mutaliskHp * 0.05) {
            return false;
        }

        // Don't engage if outnumbered by anti-air
        if (antiAirThreats > squadSize * 1.5) {
            return false;
        }

        return true;
    }

    /**
     * Checks if a unit can attack air units.
     */
    private boolean isAntiAir(Unit unit) {
        UnitType type = unit.getType();

        // Check if unit can attack air
        if (type.airWeapon() != WeaponType.None) {
            return true;
        }

        if (type == UnitType.Terran_Bunker) {
            return true;
        }

        return false;
    }

    /**
     * Calculates the anti-air DPS of a unit.
     */
    private int getAntiAirDps(Unit unit) {
        UnitType type = unit.getType();
        WeaponType weapon = type.airWeapon();

        if (weapon == WeaponType.None) {
            return 0;
        }

        int damage = weapon.damageAmount();
        int cooldown = weapon.damageCooldown();

        if (cooldown == 0) {
            return 0;
        }

        int dps = (damage * 24) / cooldown;

        return dps;
    }
}