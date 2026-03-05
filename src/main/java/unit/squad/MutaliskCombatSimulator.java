package unit.squad;

import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;
import info.GameState;

import unit.managed.ManagedUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Combat simulator implementation for Mutalisk squads.
 * Uses specialized engagement evaluation logic tailored for air units.
 */
public class MutaliskCombatSimulator implements CombatSimulator {

    @Override
    public CombatResult evaluate(Squad squad, Set<ManagedUnit> reinforcements, GameState gameState) {
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

        return evaluateMutaliskEngagement(squad, allEnemies, reinforcements) ? CombatResult.ENGAGE : CombatResult.RETREAT;
    }

    /**
     * Evaluates whether mutalisks should engage based on anti-air threats and squad composition.
     */
    private boolean evaluateMutaliskEngagement(Squad squad, List<Unit> enemies, Set<ManagedUnit> reinforcements) {
        int antiAirThreats = 0;
        int antiAirDps = 0;

        for (Unit enemy : enemies) {
            if (isAntiAir(enemy) && squad.getCenter().getDistance(enemy.getPosition()) < 160) {
                antiAirThreats++;
                antiAirDps += getAntiAirDps(enemy);
            }
        }

        int groundSupply = 0;
        for (ManagedUnit mu : reinforcements) {
            if (!mu.getUnit().getType().isFlyer()) {
                groundSupply += mu.getUnit().getType().supplyRequired();
            }
        }

        int antiAirSupply = antiAirThreats * 4;
        if (antiAirSupply > 0 && groundSupply > 0) {
            double ratio = Math.min(1.0, (double) groundSupply / antiAirSupply);
            double discountFactor = 1.0 - (ratio * 0.5);
            antiAirDps = (int) (antiAirDps * discountFactor);
        }

        int squadSize = squad.size();
        int mutaliskHp = squadSize * 120;

        if (antiAirDps * 2 > mutaliskHp * 0.05) {
            return false;
        }

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

        return (damage * 24) / cooldown;
    }
}