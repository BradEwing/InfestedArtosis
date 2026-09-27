package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import bwem.Base;
import info.GameState;
import info.tracking.BunkerGarrison;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitTracker;
import unit.managed.ManagedUnit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class ContainmentEvaluator {

    private static final int STATIC_DEFENSE_SUPPLY_PENALTY = 6;
    private static final double BREAK_SUPPLY_RATIO = 1.5;
    private static final int MIN_SUPPLY_THRESHOLD = 8;
    private static final int STATIC_DEFENSE_BASE_RADIUS = 512;

    /**
     * Distance from an enemy main or natural centre, or from a containing squad's centre, within which a squad that
     * is neither fighting nor containing still counts toward the break: 24 tiles, enough to take in a flock
     * harassing or regrouping over the base a ground squad is containing, while leaving out one back at our rally
     * point.
     */
    static final int NEAR_CONTAIN_RADIUS = 768;

    /**
     * Supply each garrisoned Bunker slot adds to the enemy side of the break, on top of the occupant's own supply in
     * the army count: a Marine's supply, so a garrisoned Marine counts twice.
     */
    static final int BUNKER_SLOT_SUPPLY = UnitType.Terran_Marine.supplyRequired();

    /**
     * Distance from a completed enemy static defence within which an enemy army unit counts as sheltering behind it
     * rather than standing outside it: 8 tiles, a loaded Bunker's reach with room for the units walled in behind it.
     */
    static final int STATIC_COVER_RADIUS = 256;

    private static final Set<UnitType> STATIC_DEFENSE_TYPES = EnumSet.of(UnitType.Terran_Bunker,
            UnitType.Protoss_Photon_Cannon, UnitType.Zerg_Sunken_Colony);

    private static final Set<UnitType> NON_ARMY_UNITS = EnumSet.of(UnitType.Zerg_Larva, UnitType.Zerg_Egg,
            UnitType.Zerg_Lurker_Egg, UnitType.Zerg_Cocoon);

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

    /**
     * Whether our army is strong enough to push into the position being contained.
     *
     * <p>Our side is the supply of every squad fighting or containing, and of every other squad, air or ground, that
     * is not defending or running by, a break never commits either, standing within {@link #NEAR_CONTAIN_RADIUS} of the enemy main or natural or of a
     * containing squad. The enemy side is its ground army supply, a fixed penalty per Photon Cannon and Sunken Colony
     * near its main or natural, and {@link #BUNKER_SLOT_SUPPLY} per occupant believed to sit in a Bunker there, see
     * {@link BunkerGarrison}.
     *
     * @param allSquads every fight squad
     * @param currentFrame current frame
     * @return true when our supply reaches the break ratio over the enemy's
     */
    public boolean canBreakContainment(Set<Squad> allSquads, int currentFrame) {
        List<Position> contested = contestedPositions(allSquads);
        int ourSupply = 0;
        for (Squad s : allSquads) {
            if (countsTowardBreak(s.getStatus(), s.getCenter(), contested)) {
                ourSupply += estimateSquadSupply(s);
            }
        }
        return breaks(ourSupply, estimateEnemyArmySupply() + staticDefenseSupply(currentFrame));
    }

    /**
     * Whether our supply reaches {@link #BREAK_SUPPLY_RATIO} times the enemy's strength.
     *
     * @param ourSupply supply of the squads that count toward the break
     * @param enemyStrength enemy army supply plus its static defence supply
     * @return true when the army may push in
     */
    static boolean breaks(int ourSupply, int enemyStrength) {
        return ourSupply >= enemyStrength * BREAK_SUPPLY_RATIO;
    }

    /**
     * Whether a squad's supply counts toward the break.
     *
     * @param status the squad's status
     * @param center the squad's centre
     * @param contested enemy main and natural centres and the centres of our containing squads
     * @return true for a fighting or containing squad, and for any other squad near a contested position that is
     *     neither defending nor running by
     */
    static boolean countsTowardBreak(SquadStatus status, Position center, Collection<Position> contested) {
        if (status == SquadStatus.FIGHT || status == SquadStatus.CONTAIN) {
            return true;
        }
        if (status == null || status == SquadStatus.DEFENSE || status == SquadStatus.RUNBY || center == null) {
            return false;
        }
        for (Position position : contested) {
            if (center.getDistance(position) <= NEAR_CONTAIN_RADIUS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enemy supply the Bunkers near its bases add to the break.
     *
     * @param trustedEstimates one entry per Bunker, its trusted garrison estimate or -1 when unknown
     * @param livingOccupants Bunker occupants known to be alive
     * @return {@link #BUNKER_SLOT_SUPPLY} per believed garrisoned occupant
     */
    static int bunkerSupply(List<Integer> trustedEstimates, int livingOccupants) {
        return BunkerGarrison.believedTotal(trustedEstimates, livingOccupants) * BUNKER_SLOT_SUPPLY;
    }

    /**
     * Whether the enemy defends only with static defence: at least one completed static defence is known, and every
     * known enemy army unit stands within {@link #STATIC_COVER_RADIUS} of one. An army unit whose position is
     * unknown counts as outside.
     *
     * @return true when no known enemy army stands outside its static defence
     */
    public boolean enemyDefenceIsStaticOnly() {
        List<Position> defences = new ArrayList<>();
        List<Position> army = new ArrayList<>();
        for (ObservedUnit ou : gameState.getObservedUnitTracker().getLivingObservedUnits()) {
            UnitType type = ou.getUnitType();
            if (STATIC_DEFENSE_TYPES.contains(type)) {
                Position position = ou.isCompleted() ? ou.getCurrentOrLastKnownPosition() : null;
                if (position != null) {
                    defences.add(position);
                }
            } else if (isArmyUnit(type)) {
                army.add(ou.getCurrentOrLastKnownPosition());
            }
        }
        return staticOnly(defences, army);
    }

    /**
     * Whether every enemy army unit stands under its static defence.
     *
     * @param defences positions of the enemy's completed static defence
     * @param army positions of the enemy's army units, null where unknown
     * @return true when there is static defence and no army unit stands outside it
     */
    static boolean staticOnly(List<Position> defences, List<Position> army) {
        if (defences.isEmpty()) {
            return false;
        }
        for (Position unit : army) {
            if (unit == null || !coveredBy(unit, defences)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a unit type counts as enemy army for the static-only test: any unit that takes supply other than a
     * worker, larva or morphing egg.
     *
     * @param type unit type
     * @return true for an army unit
     */
    static boolean isArmyUnit(UnitType type) {
        return !type.isBuilding() && !type.isWorker() && type.supplyRequired() > 0 && !NON_ARMY_UNITS.contains(type);
    }

    private static boolean coveredBy(Position unit, List<Position> defences) {
        for (Position defence : defences) {
            if (unit.getDistance(defence) <= STATIC_COVER_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private List<Position> contestedPositions(Set<Squad> allSquads) {
        List<Position> contested = new ArrayList<>(getEnemyBasePositions());
        for (Squad s : allSquads) {
            if (s.getStatus() == SquadStatus.CONTAIN) {
                contested.add(s.getCenter());
            }
        }
        return contested;
    }

    private boolean meetsMinimumSize(Squad squad) {
        return squad.getSupply() >= MIN_SUPPLY_THRESHOLD;
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

    private int staticDefenseSupply(int currentFrame) {
        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        Set<Position> basePositions = getEnemyBasePositions();
        if (basePositions.isEmpty()) return 0;
        int count = 0;
        count += tracker.getCompletedBuildingCountNearPositions(UnitType.Protoss_Photon_Cannon, basePositions,
                STATIC_DEFENSE_BASE_RADIUS);
        count += tracker.getCompletedBuildingCountNearPositions(UnitType.Zerg_Sunken_Colony, basePositions,
                STATIC_DEFENSE_BASE_RADIUS);
        List<Integer> bunkerEstimates = new ArrayList<>();
        for (ObservedUnit bunker : tracker.getCompletedBuildingsNearPositions(UnitType.Terran_Bunker, basePositions,
                STATIC_DEFENSE_BASE_RADIUS)) {
            bunkerEstimates.add(BunkerGarrison.trustedEstimate(bunker.getLastKnownLoadedCount(),
                    bunker.getLastLoadedCheckFrame(), currentFrame));
        }
        int livingOccupants = tracker.getCountOfLivingUnits(BunkerGarrison.OCCUPANTS::contains);
        return count * STATIC_DEFENSE_SUPPLY_PENALTY + bunkerSupply(bunkerEstimates, livingOccupants);
    }
}
