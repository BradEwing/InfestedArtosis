package unit.squad;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitType;
import bwem.Base;
import info.GameState;
import info.tracking.BunkerGarrison;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitTracker;
import info.tracking.terran.TerranMech;
import unit.managed.ManagedUnit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class ContainmentEvaluator {

    private static final int STATIC_DEFENSE_SUPPLY_PENALTY = 6;
    private static final double BREAK_SUPPLY_RATIO = 1.5;
    private static final int MIN_SUPPLY_THRESHOLD = 8;
    static final int STATIC_DEFENSE_BASE_RADIUS = 512;

    /**
     * The supply cap in BWAPI's doubled units, 200 as the game shows it, the same cap ProductionManager plans
     * against.
     */
    static final int MAX_SUPPLY = 400;

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

    /**
     * Frames an enemy army unit's last sighting stays trusted for the static-only test: 1000, about 42 seconds. An
     * older sighting says nothing about where the unit stands now.
     */
    static final int ARMY_MEMORY_FRAMES = 1000;

    /**
     * Army supply outside the Bunkers' garrisons that each completed Bunker may shelter while the defence still
     * counts as static-only: a garrison-equivalent, the supply of a full Bunker of Marines. A bigger force under the
     * defence is an army parked behind it, and no supply is allowed under a defence with no Bunker.
     */
    static final int PARKED_SUPPLY_PER_BUNKER = BunkerGarrison.MAX_GARRISON * BUNKER_SLOT_SUPPLY;

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

    /**
     * What the static-only test knows of one enemy army unit.
     */
    static final class ArmySighting {
        private final Position position;
        private final int supply;
        private final boolean fresh;
        private final boolean bunkerOccupant;

        /**
         * @param position the unit's current or last known position, null when unknown
         * @param supply the unit's supply
         * @param fresh true when the unit is visible or was seen within
         *     {@link ContainmentEvaluator#ARMY_MEMORY_FRAMES}
         * @param bunkerOccupant true for a unit type that can garrison a Bunker
         */
        ArmySighting(Position position, int supply, boolean fresh, boolean bunkerOccupant) {
            this.position = position;
            this.supply = supply;
            this.fresh = fresh;
            this.bunkerOccupant = bunkerOccupant;
        }
    }

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
        return compositionAllows(squad);
    }

    /**
     * Whether the squad's makeup lets it contain the enemy, see {@link ContainmentGate#compositionAllows}.
     *
     * @param squad the squad offered an arc
     * @return true when the squad may contain
     */
    public boolean compositionAllows(Squad squad) {
        return ContainmentGate.compositionAllows(versusTerran(), mechDetected(), squad.getComposition(),
                enemyGroundArmyCounts());
    }

    /**
     * Whether a squad the combat sim read as RETREAT may hold a contain arc instead, see
     * {@link ContainmentGate#safeToHold}.
     *
     * @param squad the squad offered an arc
     * @return true when holding the arc is safe
     */
    public boolean safeToHold(Squad squad) {
        return ContainmentGate.safeToHold(versusTerran(), mechDetected(), squad.getComposition(),
                enemyGroundArmyCounts());
    }

    /**
     * @return the enemy's known ground army supply, in BWAPI's doubled supply units
     */
    public int enemyArmySupply() {
        return estimateEnemyArmySupply();
    }

    private boolean versusTerran() {
        return gameState.getOpponentRace() == Race.Terran;
    }

    private boolean mechDetected() {
        return gameState.getStrategyTracker() != null
                && gameState.getStrategyTracker().isDetectedStrategy(TerranMech.NAME);
    }

    private Map<UnitType, Integer> enemyGroundArmyCounts() {
        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        Map<UnitType, Integer> counts = new EnumMap<>(UnitType.class);
        for (UnitType type : ENEMY_GROUND_ARMY_TYPES) {
            counts.put(type, tracker.getCountOfLivingUnits(type));
        }
        return counts;
    }

    /**
     * Whether our army is strong enough to push into the position being contained.
     *
     * <p>Our side is the supply of every squad fighting or containing, and of every other squad, air or ground,
     * standing within {@link #NEAR_CONTAIN_RADIUS} of the enemy main or natural or of a containing squad, unless it
     * is defending or running by, since a break never commits either. The enemy side is its ground army supply, a
     * fixed penalty per Photon Cannon and Sunken Colony near its main or natural, and {@link #BUNKER_SLOT_SUPPLY} per
     * occupant believed to sit in a Bunker there, see {@link BunkerGarrison}.
     *
     * @param allSquads every fight squad
     * @param currentFrame current frame
     * @return true when our supply reaches the break ratio over the enemy's
     */
    public boolean canBreakContainment(Set<Squad> allSquads, int currentFrame) {
        return measureBreak(allSquads, currentFrame).breaks();
    }

    /**
     * Both sides of the break, as {@link #canBreakContainment} counts them.
     *
     * @param allSquads every fight squad
     * @param currentFrame current frame
     * @return our supply and the enemy's strength
     */
    public BreakMeasure measureBreak(Set<Squad> allSquads, int currentFrame) {
        List<Position> contested = contestedPositions(allSquads);
        int ourSupply = 0;
        for (Squad s : allSquads) {
            if (countsTowardBreak(s.getStatus(), s.getCenter(), contested)) {
                ourSupply += estimateSquadSupply(s);
            }
        }
        return new BreakMeasure(ourSupply, estimateEnemyArmySupply() + staticDefenseSupply(currentFrame));
    }

    /**
     * Both sides of the break, in BWAPI's doubled supply units.
     */
    public static final class BreakMeasure {
        private final int ourSupply;
        private final int enemyStrength;

        /**
         * @param ourSupply supply of the squads that count toward the break
         * @param enemyStrength enemy army supply plus its static defence supply
         */
        public BreakMeasure(int ourSupply, int enemyStrength) {
            this.ourSupply = ourSupply;
            this.enemyStrength = enemyStrength;
        }

        /**
         * @return true when the army may push in, see {@link ContainmentEvaluator#breaks}
         */
        public boolean breaks() {
            return ContainmentEvaluator.breaks(ourSupply, enemyStrength);
        }

        /**
         * @return supply still missing for the break, 0 when it clears
         */
        public int shortfall() {
            return breakShortfall(ourSupply, enemyStrength);
        }

        /**
         * @return true when the break needs more than the supply cap, see
         *     {@link ContainmentEvaluator#breakUnreachable}
         */
        public boolean unreachable() {
            return breakUnreachable(enemyStrength);
        }
    }

    /**
     * Supply our side still needs for the break.
     *
     * @param ourSupply supply of the squads that count toward the break
     * @param enemyStrength enemy army supply plus its static defence supply
     * @return the smallest supply that would reach the break ratio less our supply, 0 when the break clears
     */
    static int breakShortfall(int ourSupply, int enemyStrength) {
        return Math.max(0, (int) Math.ceil(enemyStrength * BREAK_SUPPLY_RATIO) - ourSupply);
    }

    /**
     * Whether the break stays out of reach even with our supply at the cap, {@link #MAX_SUPPLY}, all of it army.
     *
     * @param enemyStrength enemy army supply plus its static defence supply
     * @return true when no army we can field clears the break ratio
     */
    static boolean breakUnreachable(int enemyStrength) {
        return !breaks(MAX_SUPPLY, enemyStrength);
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
     * Whether the enemy defends only with static defence, see {@link #staticOnly}, read from the tracker: completed
     * Bunkers, Photon Cannons and Sunken Colonies at their last known positions within
     * {@link #STATIC_DEFENSE_BASE_RADIUS} of the enemy main or natural, the same defence the break prices, and every
     * living enemy army unit, see {@link #isArmyUnit}. A defence elsewhere shelters nothing for this test.
     *
     * @param currentFrame current frame
     * @return true when no known enemy army stands outside its static defence and the army under it is a
     *     garrison-sized force
     */
    public boolean enemyDefenceIsStaticOnly(int currentFrame) {
        List<Position> defences = new ArrayList<>();
        List<Position> bunkers = new ArrayList<>();
        List<ArmySighting> army = new ArrayList<>();
        Set<Position> basePositions = getEnemyBasePositions();
        for (ObservedUnit ou : gameState.getObservedUnitTracker().getLivingObservedUnits()) {
            UnitType type = ou.getUnitType();
            if (STATIC_DEFENSE_TYPES.contains(type)) {
                Position position = ou.isCompleted() ? ou.getCurrentOrLastKnownPosition() : null;
                if (position != null && !defendsBase(position, basePositions)) {
                    position = null;
                }
                if (position != null) {
                    defences.add(position);
                }
                if (position != null && type == UnitType.Terran_Bunker) {
                    bunkers.add(position);
                }
            } else if (isArmyUnit(type)) {
                boolean fresh = isFreshSighting(ou.getUnit().isVisible(), ou.getLastObservedFrame().getFrames(),
                        currentFrame);
                army.add(new ArmySighting(ou.getCurrentOrLastKnownPosition(), type.supplyRequired(), fresh,
                        BunkerGarrison.OCCUPANTS.contains(type)));
            }
        }
        return staticOnly(defences, bunkers, army);
    }

    /**
     * Whether the enemy defends only with static defence: at least one completed static defence is known, every
     * enemy army unit is known to stand within {@link #STATIC_COVER_RADIUS} of one, and the army standing there
     * outside the Bunkers' garrisons is no more than {@link #PARKED_SUPPLY_PER_BUNKER} per Bunker.
     *
     * <p>A Bunker occupant last seen within {@link #STATIC_COVER_RADIUS} of a Bunker is taken as part of its
     * garrison, up to {@link BunkerGarrison#MAX_GARRISON} per Bunker, however old the sighting, since a loaded
     * occupant cannot be seen again until it unloads. Every other army unit counts toward the parked supply, and must
     * have been seen under cover within {@link #ARMY_MEMORY_FRAMES}: an older sighting, or no known position, leaves
     * the unit unknown and possibly anywhere, so the defence is not static-only.
     *
     * @param defences positions of the enemy's completed static defence
     * @param bunkers positions of the enemy's completed Bunkers, each also among the defences
     * @param army the enemy's army units
     * @return true when there is static defence and the only army under it is a garrison-sized force
     */
    static boolean staticOnly(List<Position> defences, List<Position> bunkers, List<ArmySighting> army) {
        if (defences.isEmpty()) {
            return false;
        }
        int garrisonSlots = bunkers.size() * BunkerGarrison.MAX_GARRISON;
        int parkedSupply = 0;
        for (ArmySighting unit : army) {
            if (unit.position == null || !coveredBy(unit.position, defences)) {
                return false;
            }
            if (unit.bunkerOccupant && garrisonSlots > 0 && coveredBy(unit.position, bunkers)) {
                garrisonSlots--;
                continue;
            }
            if (!unit.fresh) {
                return false;
            }
            parkedSupply += unit.supply;
        }
        return parkedSupply <= bunkers.size() * PARKED_SUPPLY_PER_BUNKER;
    }

    /**
     * Whether a static defence stands at an enemy main or natural.
     *
     * @param defence the defence's position
     * @param basePositions enemy main and natural centres
     * @return true within {@link #STATIC_DEFENSE_BASE_RADIUS} of one of them
     */
    static boolean defendsBase(Position defence, Collection<Position> basePositions) {
        for (Position base : basePositions) {
            if (defence.getDistance(base) <= STATIC_DEFENSE_BASE_RADIUS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an enemy army unit's position is recent enough to rule on.
     *
     * @param visible true while the unit is in sight
     * @param lastObservedFrame frame the unit was last shown or hidden
     * @param currentFrame current frame
     * @return true while the unit is visible or was seen within {@link #ARMY_MEMORY_FRAMES}
     */
    static boolean isFreshSighting(boolean visible, int lastObservedFrame, int currentFrame) {
        return visible || currentFrame - lastObservedFrame <= ARMY_MEMORY_FRAMES;
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
