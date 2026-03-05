package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import bwem.Base;
import info.GameState;
import info.tracking.ObservedUnitTracker;
import unit.managed.ManagedUnit;

import java.util.HashSet;
import java.util.Set;


public class ContainmentEvaluator {

    private static final int STATIC_DEFENSE_SUPPLY_PENALTY = 6;
    private static final double BREAK_SUPPLY_RATIO = 1.5;
    private static final int MIN_SUPPLY_THRESHOLD = 8;

    private static final UnitType[] ENEMY_GROUND_ARMY_TYPES = {
        UnitType.Terran_Marine,
        UnitType.Terran_Firebat,
        UnitType.Terran_Medic,
        UnitType.Terran_Vulture,
        UnitType.Terran_Siege_Tank_Siege_Mode,
        UnitType.Terran_Siege_Tank_Tank_Mode,
        UnitType.Terran_Goliath,
        UnitType.Protoss_Zealot,
        UnitType.Protoss_Dragoon,
        UnitType.Protoss_Dark_Templar,
        UnitType.Protoss_High_Templar,
        UnitType.Protoss_Archon,
        UnitType.Protoss_Reaver,
        UnitType.Zerg_Zergling,
        UnitType.Zerg_Hydralisk,
        UnitType.Zerg_Lurker,
        UnitType.Zerg_Ultralisk,
        UnitType.Zerg_Defiler,
    };

    private final GameState gameState;

    public ContainmentEvaluator(GameState gameState) {
        this.gameState = gameState;
    }

    public boolean isEligibleSquad(Squad squad) {
        return squad.isGroundSquad();
    }

    public boolean shouldContain(Squad squad) {
        if (!isEligibleSquad(squad)) return false;
        if (gameState.getBaseData().getEnemyBases().isEmpty()) return false;
        if (!meetsMinimumSize(squad)) return false;
        if (estimateEnemyArmySupply() == 0) return false;
        return true;
    }

    public boolean canBreakContainment(Set<Squad> allSquads) {
        int ourSupply = 0;
        for (Squad s : allSquads) {
            SquadStatus status = s.getStatus();
            if (status != SquadStatus.FIGHT && status != SquadStatus.CONTAIN) continue;
            ourSupply += estimateSquadSupply(s);
        }
        int enemySupply = estimateEnemyArmySupply();
        int staticDefensePenalty = countEnemyStaticDefenseNearBase() * STATIC_DEFENSE_SUPPLY_PENALTY;
        int totalEnemyStrength = enemySupply + staticDefensePenalty;
        return ourSupply >= totalEnemyStrength * BREAK_SUPPLY_RATIO;
    }

    private boolean meetsMinimumSize(Squad squad) {
        if (squad.isGroundSquad()) {
            return ((GroundSquad) squad).getSupply() >= MIN_SUPPLY_THRESHOLD;
        }
        return squad.size() >= 2;
    }

    private Set<Position> getEnemyBasePositions() {
        Set<Position> positions = new HashSet<>();
        Base enemyMain = gameState.getBaseData().getMainEnemyBase();
        if (enemyMain == null) return positions;
        positions.add(enemyMain.getCenter());
        Base enemyNatural = gameState.getBaseData().getEnemyNaturalBase();
        if (enemyNatural != null) {
            positions.add(enemyNatural.getCenter());
        }
        return positions;
    }

    private int estimateSquadSupply(Squad squad) {
        int supply = 0;
        for (ManagedUnit mu : squad.getMembers()) {
            supply += mu.getUnit().getType().supplyRequired();
        }
        return supply;
    }

    private int estimateEnemyArmySupply() {
        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        int supply = 0;
        for (UnitType type : ENEMY_GROUND_ARMY_TYPES) {
            supply += tracker.getCountOfLivingUnits(type) * type.supplyRequired();
        }
        return supply;
    }

    private int countEnemyStaticDefenseNearBase() {
        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        Set<Position> basePositions = getEnemyBasePositions();
        if (basePositions.isEmpty()) return 0;
        int count = 0;
        count += tracker.getCompletedBuildingCountNearPositions(UnitType.Terran_Bunker, basePositions, 512);
        count += tracker.getCompletedBuildingCountNearPositions(UnitType.Protoss_Photon_Cannon, basePositions, 512);
        count += tracker.getCompletedBuildingCountNearPositions(UnitType.Zerg_Sunken_Colony, basePositions, 512);
        return count;
    }
}
