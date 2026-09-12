package info;

import bwapi.Bullet;
import bwapi.BulletType;
import bwapi.Game;
import bwapi.Player;
import bwapi.Position;
import bwapi.Race;
import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwapi.WalkPosition;
import bwem.BWEM;
import bwem.Base;
import bwem.Mineral;
import config.Config;
import info.map.BuildingPlanner;
import info.map.GameMap;
import info.map.MapTile;
import info.tracking.ObservedBulletTracker;
import info.tracking.ObservedUnitTracker;
import info.tracking.PsiStormTracker;
import info.tracking.StrategyTracker;
import learning.Decisions;
import lombok.Data;
import macro.HatcheryCapacity;
import macro.SupplyCapacity;
import macro.plan.ColonyClaims;
import macro.plan.Plan;
import macro.plan.PlanCancelSource;
import macro.plan.PlanType;
import org.jetbrains.annotations.Nullable;
import macro.ProductionQueue;
import macro.plan.PlanState;
import strategy.buildorder.BuildOrder;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;
import util.Distance;
import util.Filter;
import util.Time;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * GameState tracks global state that is shared among agents and managers.
 */
@Data
public class GameState {
    private static final int BUNKER_BULLET_RADIUS = 224;

    private Game game;
    private Config config;
    private Player self;
    private BWEM bwem;

    private Race opponentRace;

    private int larvaDeadlockDetectedFrame;
    private int lastEnemyUnitSeenFrame = 0;

    private HashMap<Unit, ManagedUnit> managedUnitLookup = new HashMap<>();
    private HashSet<ManagedUnit> managedUnits = new HashSet<>();
    private HashSet<ManagedUnit> assignedManagedWorkers = new HashSet<>();
    private HashSet<ManagedUnit> gatherers = new HashSet<>();
    private HashSet<ManagedUnit> mineralGatherers = new HashSet<>();
    private HashSet<ManagedUnit> gasGatherers = new HashSet<>();

    private HashMap<Unit, HashSet<ManagedUnit>> geyserAssignments = new HashMap<>();
    private HashMap<Unit, HashSet<ManagedUnit>> mineralAssignments = new HashMap<>();

    private HashSet<ManagedUnit> larva = new HashSet<>();

    private boolean enemyHasCloakedUnits = false;
    private boolean isLarvaDeadlocked = false;
    private boolean isAllIn = false;
    private boolean cannonRushed = false;
    private boolean cannonRushDefend = false;
    private boolean scvRushed = false;
    private boolean earlyRushed = false;
    private boolean earlyRushDelayLair = false;
    private boolean earlyRushMacroHatch = false;

    private HashSet<Plan> plansScheduled = new HashSet<>();
    private HashSet<Plan> plansBuilding = new HashSet<>();
    private HashSet<Plan> plansMorphing = new HashSet<>();
    private HashSet<Plan> plansComplete = new HashSet<>();
    private HashSet<Plan> plansImpossible = new HashSet<>();
    private ProductionQueue productionQueue = new ProductionQueue();
    private HashMap<Unit, Plan> assignedPlannedItems = new HashMap<>();
    private int plannedWorkers;
    private int plannedHatcheries = 1;
    private int lastHatcheryEnqueueFrame = -HatcheryCapacity.ENQUEUE_COOLDOWN_FRAMES;
    private int idleDroneReclaims;

    private HashMap<Base, HashSet<ManagedUnit>> gatherersAssignedToBase = new HashMap<>();

    private HashMap<Base, HashSet<Unit>> baseToThreatLookup = new HashMap<>();

    private boolean defensiveSunk = false;
    private BuildOrder activeBuildOrder;
    private final BuildOrderChain buildOrderChain = new BuildOrderChain();

    private boolean transitionBuildOrder = false;

    private UnitTypeCount unitTypeCount = new UnitTypeCount();

    private TechProgression techProgression = new TechProgression();

    private ResourceCount resourceCount;

    private BaseData baseData;
    private ScoutData scoutData;
    private ObservedUnitTracker observedUnitTracker = new ObservedUnitTracker();
    private ObservedBulletTracker observedBulletTracker = new ObservedBulletTracker();
    private PsiStormTracker psiStormTracker = new PsiStormTracker(observedBulletTracker);
    private StrategyTracker strategyTracker;

    // Initialized in InformationManager
    private GameMap gameMap;
    private BuildingPlanner buildingPlanner;

    public GameState(Game game, BWEM bwem) {
        this.game = game;
        this.self = game.self();
        this.bwem = bwem;
        this.resourceCount = new ResourceCount(self);
        this.baseData = new BaseData(bwem.getMap().getBases());
        this.scoutData = new ScoutData();
        this.config = new Config();
    }

    public void onStart(Decisions decisions, Race opponentRace) {
        this.activeBuildOrder = decisions.getOpener();
        this.opponentRace = opponentRace;
        this.gameMap = new GameMap(game.mapWidth(), game.mapHeight());
        this.strategyTracker = new StrategyTracker(game, opponentRace, this.observedUnitTracker, this.baseData, this.gameMap, bwem.getMap());
    }

    public void onFrame() {
        observedUnitTracker.onFrame(game.getFrameCount());
        updateObservedUnitGroundHeights();
        updateBunkerGarrisonCounts();
        strategyTracker.onFrame();
        clearVisibleEnemyWorkerLocations();
    }

    private void updateBunkerGarrisonCounts() {
        int currentFrame = game.getFrameCount();

        Map<Unit, Integer> bunkerBulletCounts = new HashMap<>();
        for (Unit enemy : observedUnitTracker.getVisibleEnemyUnits()) {
            if (enemy.getType() == UnitType.Terran_Bunker) {
                bunkerBulletCounts.put(enemy, 0);
            }
        }
        if (bunkerBulletCounts.isEmpty()) return;

        for (Bullet bullet : game.getBullets()) {
            if (bullet == null || !bullet.exists()) continue;
            if (bullet.getType() != BulletType.Gauss_Rifle_Hit) continue;
            if (bullet.getSource() != null) continue;
            Position bulletPos = bullet.getPosition();
            Unit closestBunker = null;
            double closestDist = BUNKER_BULLET_RADIUS;
            for (Unit bunker : bunkerBulletCounts.keySet()) {
                double dist = bunker.getPosition().getDistance(bulletPos);
                if (dist <= closestDist) {
                    closestDist = dist;
                    closestBunker = bunker;
                }
            }
            if (closestBunker != null) {
                bunkerBulletCounts.merge(closestBunker, 1, Integer::sum);
            }
        }

        for (Map.Entry<Unit, Integer> entry : bunkerBulletCounts.entrySet()) {
            int bulletsThisFrame = Math.min(entry.getValue(), 4);
            observedUnitTracker.updateBunkerGarrison(entry.getKey(), bulletsThisFrame, currentFrame);
        }
    }

    private void updateObservedUnitGroundHeights() {
        for (Unit enemy : observedUnitTracker.getVisibleEnemyUnits()) {
            observedUnitTracker.updateGroundHeight(enemy, game.getGroundHeight(enemy.getTilePosition()));
        }
    }

    private void clearVisibleEnemyWorkerLocations() {
        Set<Position> lastKnownWorkerPositions = observedUnitTracker.getLastKnownPositionsOfLivingUnits(
            UnitType.Terran_SCV,
            UnitType.Protoss_Probe,
            UnitType.Zerg_Drone
        );

        Set<Position> visibleWorkerPositions = lastKnownWorkerPositions.stream()
            .filter(p -> p != null)
            .filter(p -> game.isVisible(p.toTilePosition()))
            .collect(Collectors.toSet());

        if (!visibleWorkerPositions.isEmpty()) {
            observedUnitTracker.clearLastKnownLocationsAt(visibleWorkerPositions);
        }
    }

    public int numGatherers() {
        return gatherers.size();
    }

    /**
     * Drone count the production gates read: drones on a resource plus drones already queued
     * or in an egg.
     * <p>
     * A drone that is parked, scouting, defending or walking to a build site gathers nothing,
     * so it does not hold the drone target up. Queued drones stay counted because the build
     * order plans every frame and would otherwise refill the queue until the first egg hatched.
     *
     * @return gathering plus queued drones
     */
    public int numEconomyDrones() {
        return gatherers.size() + unitTypeCount.plannedCount(UnitType.Zerg_Drone);
    }

    public int numLarva() { 
        return larva.size(); 
    }

    /**
     * Records a drone that was parked in {@link UnitRole#IDLE} and put back to gathering.
     * Before the reclaim existed such a drone stood still until it died, so a rising count
     * points at whichever caller idled it.
     */
    public void incrementIdleDroneReclaims() {
        idleDroneReclaims += 1;
    }

    /**
     * Larva already handed to a plan. The assignment removes them from the larva set while their
     * reservation stands, so the larva check adds them back to avoid counting them twice.
     *
     * @return count of assigned larva that have not become eggs yet
     */
    public int larvaAssignedToPlans() {
        int assigned = 0;
        for (Unit unit : assignedPlannedItems.keySet()) {
            if (unit.getType() == UnitType.Zerg_Larva) {
                assigned += 1;
            }
        }
        return assigned;
    }

    public int frameCanAffordUnit(UnitType unit, int currentFrame) {
        return this.resourceCount.frameCanAffordUnit(unit, currentFrame, mineralGatherers.size(), gasGatherers.size());
    }

    /** Projection for a plan's own cost, including an upgrade or a research plan. */
    public int frameCanAffordPlan(Plan plan, int currentFrame) {
        return this.resourceCount.frameCanAffordPlan(plan, currentFrame, mineralGatherers.size(), gasGatherers.size());
    }

    /** Refreshed prediction for a plan whose cost already stands in the reservation ledger. */
    public int frameCanAffordReserved(int currentFrame) {
        return this.resourceCount.frameCanAffordReserved(currentFrame, mineralGatherers.size(), gasGatherers.size());
    }

    public Base reserveBase() {
        return baseData.reserveBase(getGameTime().getFrames());
    }

    public void claimBase(Unit hatchery) {
        if (this.baseData.isBase(hatchery)) {
            return;
        }
        final Base newBase = baseData.claimBase(hatchery);
        addBaseToGameState(hatchery, newBase);
    }

    public void addBaseToGameState(Unit hatchery, Base base) {
        if (base == null) { 
            return; 
        }
        gatherersAssignedToBase.put(base, new HashSet<>());
        this.baseData.addBase(hatchery, base);

        for (Mineral mineral: base.getMinerals()) {
            mineralAssignments.put(mineral.getUnit(), new HashSet<>());
        }
    }

    public void addMainBase(Unit hatchery, Base base) {
        this.baseData.initializeMainBase(base, this.gameMap);
        addBaseToGameState(hatchery, base);
    }

    public void addMacroHatchery(Unit hatchery) {
        this.baseData.addMacroHatchery(hatchery);
    }

    public void removeHatchery(Unit hatchery) {
        if (this.baseData.isBase(hatchery)) {
            Base base = this.baseData.get(hatchery);
            gatherersAssignedToBase.remove(base);
            baseToThreatLookup.remove(base);
        }
        this.baseData.removeHatchery(hatchery);
    }

    public void cancelPlan(Unit unit, Plan plan, PlanCancelSource source) {
        if (plan.getState() == PlanState.CANCELLED) {
            return;
        }

        final PlanState priorState = plan.getState();
        plan.setCancelSource(source);
        plansBuilding.remove(plan);
        plansMorphing.remove(plan);
        plansImpossible.remove(plan);
        plan.setState(PlanState.CANCELLED);
        assignedPlannedItems.remove(unit);

        if (unit != null) {
            ManagedUnit managedUnit = managedUnitLookup.get(unit);
            if (managedUnit != null) {
                managedUnit.setPlan(null);
                managedUnit.setRole(UnitRole.IDLE);
            }
        }

        switch (plan.getType()) {
            case UNIT:
                cancelUnitPlanAccounting(plan.getPlannedUnit(), priorState);
                resourceCount.unreserveUnit(plan.getPlannedUnit());
                break;
            case BUILDING:
                UnitType buildingType = plan.getPlannedUnit();
                unitTypeCount.unplanUnit(buildingType);
                resourceCount.unreserveUnit(buildingType);

                if (plan.getBuildPosition() != null) {
                    buildingPlanner.unreservePlannedBuildingTiles(plan.getBuildPosition(), buildingType);
                }

                if (buildingType == UnitType.Zerg_Extractor && plan.getBuildPosition() != null) {
                    baseData.unreserveExtractor(plan.getBuildPosition(), getGameTime().getFrames());
                }

                if (buildingType == UnitType.Zerg_Hatchery) {
                    removePlannedHatchery(1);
                }

                TilePosition tp = plan.getBuildPosition();
                if (tp != null && baseData.isBaseTilePosition(tp)) {
                    Base base = baseData.baseAtTilePosition(tp);
                    baseData.cancelReserveBase(base);
                    if (BaseData.shouldBackoffExpansion(buildingType, plan.getCancelReason())) {
                        baseData.backoffExpansion(base, getGameTime().getFrames());
                    }
                }
                
                if (buildingType == UnitType.Zerg_Creep_Colony) {
                    cancelPairedColonyPlan(plan);
                }

                if (ColonyClaims.isColonyMorph(buildingType)) {
                    cancelPairedCreepColonyPlan(plan);
                }

                clearPlannedTechFlags(buildingType);
                break;
            case UPGRADE:
                resourceCount.unreserveUpgrade(plan);
                clearPlannedUpgradeFlags(plan.getPlannedUpgrade());
                break;
            case TECH:
                resourceCount.unreserveTechResearch(plan.getPlannedTechType());
                clearPlannedTechResearchFlags(plan.getPlannedTechType());
                break;
            default:
                return;
        }
    }

    /**
     * Cancels the Sunken or Spore plan waiting on a cancelled Creep Colony. The morph releases the
     * colony reservation as it is cancelled, so this side never touches it.
     */
    private void cancelPairedColonyPlan(Plan creepColonyPlan) {
        TilePosition tp = creepColonyPlan.getBuildPosition();
        if (tp == null) {
            return;
        }

        boolean[] foundInQueue = {false};

        productionQueue.removeWhere(
                p -> isColonyMorphAtPosition(p, tp),
                PlanCancelSource.GAME_STATE_PAIRED_COLONY,
                p -> {
                    foundInQueue[0] = true;
                    setImpossiblePlan(p);
                }
        );

        if (foundInQueue[0]) {
            return;
        }

        for (Plan p : plansScheduled) {
            if (isColonyMorphAtPosition(p, tp)) {
                plansScheduled.remove(p);
                cancelPlan(null, p, PlanCancelSource.GAME_STATE_PAIRED_COLONY);
                break;
            }
        }
    }

    /**
     * Releases what a cancelled Sunken or Spore plan reserved, and takes down the Creep Colony
     * queued to feed it.
     *
     * <p>The reservation counts towards the colony targets that decide whether another colony is
     * wanted, so a morph cancelled without releasing it silences the reaction for the rest of the
     * game once enough of them have leaked.
     */
    private void cancelPairedCreepColonyPlan(Plan morphPlan) {
        releaseColonyReservation(morphPlan);

        Plan pairedColonyPlan = morphPlan.getPairedColonyPlan();
        if (!ColonyClaims.pairedColonyDiesWithMorph(pairedColonyPlan)) {
            return;
        }

        productionQueue.removeWhere(
                p -> p.equals(pairedColonyPlan),
                PlanCancelSource.GAME_STATE_PAIRED_COLONY,
                this::setImpossiblePlan
        );
    }

    /**
     * Hands back the colony slot a Sunken or Spore plan reserved, at the base it was reserved at.
     * The plan drops its hold as it does so, so a second release cannot reach the counters.
     */
    private void releaseColonyReservation(Plan morphPlan) {
        Base reservedBase = morphPlan.getReservedColonyBase();
        if (reservedBase == null) {
            return;
        }

        morphPlan.setReservedColonyBase(null);
        if (morphPlan.getPlannedUnit() == UnitType.Zerg_Sunken_Colony) {
            baseData.unreserveSunkenColony(reservedBase);
        } else {
            baseData.unreserveSporeColony(reservedBase);
        }
    }

    private boolean isColonyMorphAtPosition(Plan p, TilePosition tp) {
        return ColonyClaims.isColonyMorph(p.getPlannedUnit()) && tp.equals(p.claimedColonyTile());
    }

    /**
     * The creep colony standing on a tile, whether still under construction or complete, or null
     * if no colony of ours is there.
     */
    public Unit creepColonyAt(TilePosition tilePosition) {
        if (tilePosition == null) {
            return null;
        }

        for (Unit unit : self.getUnits()) {
            if (unit.getType() == UnitType.Zerg_Creep_Colony && unit.getTilePosition().equals(tilePosition)) {
                return unit;
            }
        }
        return null;
    }

    private void clearPlannedTechFlags(UnitType buildingType) {
        switch (buildingType) {
            case Zerg_Spawning_Pool:
                techProgression.setPlannedSpawningPool(false);
                break;
            case Zerg_Hydralisk_Den:
                techProgression.setPlannedDen(false);
                break;
            case Zerg_Lair:
                techProgression.setPlannedLair(false);
                break;
            case Zerg_Spire:
                techProgression.setPlannedSpire(false);
                break;
            case Zerg_Queens_Nest:
                techProgression.setPlannedQueensNest(false);
                break;
            case Zerg_Hive:
                techProgression.setPlannedHive(false);
                break;
            case Zerg_Ultralisk_Cavern:
                techProgression.setPlannedUltraliskCavern(false);
                break;
            case Zerg_Defiler_Mound:
                techProgression.setPlannedDefilerMound(false);
                break;
            case Zerg_Evolution_Chamber:
                techProgression.setPlannedEvolutionChambers(techProgression.getPlannedEvolutionChambers() - 1);
                break;
            default:
                break;
        }
    }

    private void clearPlannedUpgradeFlags(UpgradeType upgradeType) {
        switch (upgradeType) {
            case Metabolic_Boost:
                techProgression.setPlannedMetabolicBoost(false);
                break;
            case Muscular_Augments:
                techProgression.setPlannedMuscularAugments(false);
                break;
            case Grooved_Spines:
                techProgression.setPlannedGroovedSpines(false);
                break;
            case Zerg_Carapace:
                techProgression.setPlannedCarapaceUpgrades(false);
                break;
            case Zerg_Missile_Attacks:
                techProgression.setPlannedRangedUpgrades(false);
                break;
            case Zerg_Melee_Attacks:
                techProgression.setPlannedMeleeUpgrades(false);
                break;
            case Zerg_Flyer_Attacks:
                techProgression.setPlannedFlyerAttack(false);
                break;
            case Zerg_Flyer_Carapace:
                techProgression.setPlannedFlyerDefense(false);
                break;
            case Pneumatized_Carapace:
                techProgression.setPlannedOverlordSpeed(false);
                break;
            case Chitinous_Plating:
                techProgression.setPlannedChitinousPlating(false);
                break;
            case Anabolic_Synthesis:
                techProgression.setPlannedAnabolicSynthesis(false);
                break;
            case Adrenal_Glands:
                techProgression.setPlannedAdrenalGlands(false);
                break;
            default:
                break;
        }
    }

    private void clearPlannedTechResearchFlags(TechType techType) {
        switch (techType) {
            case Lurker_Aspect:
                techProgression.setPlannedLurker(false);
                break;
            case Consume:
                techProgression.setPlannedConsume(false);
                break;
            default:
                break;
        }
    }

    public void completePlan(Unit unit, Plan plan) {
        plansBuilding.remove(plan);
        plansMorphing.remove(plan);
        plan.setReservedColonyBase(null);
        plan.setState(PlanState.COMPLETE);
        plansComplete.add(plan);
        assignedPlannedItems.remove(unit);
        if (plan.getType() == PlanType.UPGRADE) {
            clearPlannedUpgradeFlags(plan.getPlannedUpgrade());
        }
    }

    /**
     * Cancels an unexecutable plan and retains it until its executor releases it.
     */
    public void setImpossiblePlan(Plan plan) {
        if (plan.getState() == PlanState.CANCELLED) {
            return;
        }

        plansImpossible.add(plan);

        PlanState currentState = plan.getState();
        boolean shouldUnreserve = currentState == PlanState.SCHEDULE ||
                                   currentState == PlanState.BUILDING ||
                                   currentState == PlanState.MORPHING;

        plan.setState(PlanState.CANCELLED);

        switch (plan.getType()) {
            case UNIT:
                cancelUnitPlanAccounting(plan.getPlannedUnit(), currentState);
                if (shouldUnreserve) {
                    resourceCount.unreserveUnit(plan.getPlannedUnit());
                }
                break;
            case BUILDING:
                UnitType buildingType = plan.getPlannedUnit();
                unitTypeCount.unplanUnit(buildingType);
                if (shouldUnreserve) {
                    resourceCount.unreserveUnit(buildingType);
                }
                if (plan.getBuildPosition() != null) {
                    buildingPlanner.unreservePlannedBuildingTiles(plan.getBuildPosition(), buildingType);
                }
                if (buildingType == UnitType.Zerg_Hatchery) {
                    removePlannedHatchery(1);
                    TilePosition tp = plan.getBuildPosition();
                    if (!plan.isMacroHatchery() && tp != null && baseData.isBaseTilePosition(tp)) {
                        Base base = baseData.baseAtTilePosition(tp);
                        baseData.cancelReserveBase(base);
                    }
                }
                if (buildingType == UnitType.Zerg_Extractor && plan.getBuildPosition() != null) {
                    baseData.unreserveExtractor(plan.getBuildPosition(), getGameTime().getFrames());
                }
                if (buildingType == UnitType.Zerg_Creep_Colony) {
                    cancelPairedColonyPlan(plan);
                }
                if (ColonyClaims.isColonyMorph(buildingType)) {
                    cancelPairedCreepColonyPlan(plan);
                }
                clearPlannedTechFlags(buildingType);
                break;
            case UPGRADE:
                if (shouldUnreserve) {
                    resourceCount.unreserveUpgrade(plan);
                }
                clearPlannedUpgradeFlags(plan.getPlannedUpgrade());
                break;
            case TECH:
                if (shouldUnreserve) {
                    resourceCount.unreserveTechResearch(plan.getPlannedTechType());
                }
                clearPlannedTechResearchFlags(plan.getPlannedTechType());
                break;
            default:
                break;
        }
    }

    /**
     * Rolls back what planning a unit reserved. A drone plan releases its planned worker when the
     * morph starts, so a plan cancelled out of {@link PlanState#MORPHING} has already paid that
     * back and must not pay it twice.
     *
     * @param unitType the planned unit
     * @param priorState the plan's state before it was cancelled
     */
    private void cancelUnitPlanAccounting(UnitType unitType, PlanState priorState) {
        unitTypeCount.cancelUnitPlan(unitType);
        if (unitType == UnitType.Zerg_Drone && priorState != PlanState.MORPHING) {
            removePlannedWorker(1);
        }
        if (unitType == UnitType.Zerg_Overlord) {
            int plannedSupply = resourceCount.getPlannedSupply();
            resourceCount.setPlannedSupply(Math.max(0, plannedSupply - unitType.supplyProvided()));
        }
    }

    /**
     * Checks tech progression, build order and base data to decide if a lair can be planned.
     *
     * <p>The Extractor term reads {@link #ourUnitCount}, which sees only finished Extractors,
     * and that is what it wants: it stands for gas income, and an Extractor under construction
     * earns none. Reading {@link #ourBuildingOrPlannedCount} here would queue a Lair and its
     * 100 gas against a bank that cannot grow until the Extractor finishes.
     */
    public boolean canPlanLair() {
        return !earlyRushDelayLair && needLair() && techProgression.canPlanLair()
                && hasMinHatchForLair() && ourUnitCount(UnitType.Zerg_Extractor) > 0;
    }

    public boolean canPlanHive() {
        return needHive() && techProgression.canPlanHive();
    }

    public boolean canPlanQueensNest() {
        return needHive() && techProgression.canPlanQueensNest();
    }

    private boolean needLair() {
        return activeBuildOrder.needLair() || techProgression.needLairForNextEvolutionChamberUpgrades() || needHive();
    }

    private boolean needHive() {
        return activeBuildOrder.needHive() || techProgression.needHiveForUpgrades();
    }

    public boolean canPlanUltraliskCavern() {
        return needHive() && techProgression.canPlanUltraliskCavern();
    }

    public boolean canPlanDefilerMound() {
        return needHive() && techProgression.canPlanDefilerMound();
    }

    // Only take 1 hatch -> lair against zerg
    private boolean hasMinHatchForLair() {
        final int numHatch = baseData.numHatcheries();

        if (opponentRace == Race.Zerg) {
            return numHatch > 0;
        } else {
            return numHatch > 1;
        }
    }

    public boolean needGeyserWorkers() { 
        return this.getGeyserWorkers() < (3 * this.getGeyserAssignments().size()); 
    }

    public int needGeyserWorkersAmount() { 
        return (3 * this.getGeyserAssignments().size()) - this.getGeyserWorkers(); 
    }

    /**
     * Removes a managed unit from all data structures, before assigning it a new role.
     */
    public void clearAssignments(ManagedUnit managedUnit) {
        if (assignedManagedWorkers.contains(managedUnit)) {
            for (HashSet<ManagedUnit> mineralWorkers: mineralAssignments.values()) {
                if (mineralWorkers.contains(managedUnit)) {
                    mineralWorkers.remove(managedUnit);
                }
            }
            for (HashSet<ManagedUnit> geyserWorkers: geyserAssignments.values()) {
                if (geyserWorkers.contains(managedUnit)) {
                    geyserWorkers.remove(managedUnit);
                }
            }
        }

        larva.remove(managedUnit);
        gatherers.remove(managedUnit);
        mineralGatherers.remove(managedUnit);
        gasGatherers.remove(managedUnit);
        assignedManagedWorkers.remove(managedUnit);
        managedUnit.setGatherTarget(null);
        managedUnit.setNewGatherTarget(false);

        for (HashSet<ManagedUnit> managedUnitAssignments: gatherersAssignedToBase.values()) {
            managedUnitAssignments.remove(managedUnit);
        }
    }

    public List<ManagedUnit> getManagedUnitsByType(UnitType type) {
        return managedUnits.stream()
                .filter(m -> m.getUnitType() == type)
                .collect(Collectors.toList());
    }

    public void updateRace(Race race) {
        opponentRace = race;
        strategyTracker.updateRace(race);
    }

    public Time getGameTime() {
        return new Time(game.getFrameCount());
    }

    /** The unit executing a plan, or null while the plan has no executor assigned. */
    @Nullable
    public Unit executorOf(Plan plan) {
        for (Map.Entry<Unit, Plan> entry : assignedPlannedItems.entrySet()) {
            if (plan.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void addPlannedWorker(int numWorkers) {
        plannedWorkers += numWorkers;
    }

    public void removePlannedWorker(int numWorkers) {
        plannedWorkers -= numWorkers;
    }

    /**
     * Records a hatchery plan the build order has just created. Every hatchery plan the bot
     * creates passes through here, so this is where the request that produced it re-arms from.
     */
    public void addPlannedHatchery(int numHatcheries) {
        plannedHatcheries += numHatcheries;
        lastHatcheryEnqueueFrame = getGameTime().getFrames();
    }

    public void removePlannedHatchery(int numHatcheries) {
        plannedHatcheries -= numHatcheries;
    }

    public boolean canPlanDrone() {
        final int expectedWorkers = expectedWorkers();
        int hatchCount = ourUnitCount(UnitType.Zerg_Hatchery, UnitType.Zerg_Lair, UnitType.Zerg_Hive);
        int plannedWorkerConstraint = hatchCount * 3;
        return plannedWorkers < plannedWorkerConstraint && numWorkers() < 80 && numWorkers() < expectedWorkers;
    }

    public int numWorkers() {
        return mineralGatherers.size() + gasGatherers.size();
    }

    public int getMineralWorkers() {
        return mineralGatherers.size();
    }

    public int getGeyserWorkers() {
        return gasGatherers.size();
    }

    private int expectedWorkers() {
        final int base = 5;
        final int expectedMineralWorkers = baseData.currentBaseCount() * 7;
        final int expectedGasWorkers = geyserAssignments.size() * 3;

        Race race = opponentRace;
        switch (race) {
            case Zerg:
                return expectedMineralWorkers + expectedGasWorkers;
            default:
                return base + expectedMineralWorkers + expectedGasWorkers;
        }
    }

    public boolean canPlanExtractor() {
        final int reservedGeysers = baseData.numExtractor();
        final int completedExtractors = geyserAssignments.size();
        final int gasWorkers = getGeyserWorkers();
        return !isAllIn &&
                !scvRushed &&
                techProgression.canPlanExtractor() &&
                baseData.canReserveExtractor() &&
                shouldRequestExtractor(
                        reservedGeysers,
                        completedExtractors,
                        gasWorkers,
                        getGameTime().getFrames(),
                        baseData.getExtractorReplanBackoffUntil());
    }

    /**
     * Whether another Extractor is worth asking for.
     *
     * <p>Past the first one the gate is gas actually being mined, not a bank imbalance. Minerals
     * lead gas for most of a Zerg game by construction, so a bank rule asks for a geyser on almost
     * every frame it is consulted, including while the extractor it already owns stands empty.
     * Requiring every claimed geyser to be standing also serializes the requests: a plan holds its
     * reservation from the frame it is created, so a second one cannot be queued behind the first.
     *
     * <p>Gatherers are counted across all geysers rather than per geyser, which the drone
     * distribution spreads out anyway, so a single saturated geyser can stand in for an empty
     * second one for as long as the split takes to even out.
     *
     * @param reservedGeysers geysers claimed by a standing extractor or an in-flight plan, from
     *     {@link BaseData#numExtractor()}
     * @param completedExtractors extractors finished and open to gatherers
     * @param gasWorkers drones assigned to gas
     * @param currentFrame current frame
     * @param replanBackoffUntil frame the hold after a cancelled Extractor plan expires, from
     *     {@link BaseData#backoffExtractor(int)}. One deadline for the whole bot, not one per
     *     geyser, and it outranks every other term including the first-extractor branch
     * @return true when a geyser should be claimed
     */
    static boolean shouldRequestExtractor(int reservedGeysers, int completedExtractors, int gasWorkers,
                                          int currentFrame, int replanBackoffUntil) {
        if (currentFrame < replanBackoffUntil) {
            return false;
        }
        if (reservedGeysers < 1) {
            return true;
        }
        return completedExtractors >= reservedGeysers && gasWorkers >= completedExtractors;
    }

    public int ourUnitCount(UnitType unitType) {
        return unitTypeCount.get(unitType);
    }

    /**
     * Counts unit plans of this type still waiting in the production queue. Derived from the
     * live queue, unlike the planned unit counts.
     */
    public int queuedUnitPlanCount(UnitType unitType) {
        return productionQueue.unitPlanCount(unitType);
    }

    /**
     * Counts every unit plan of this type that has not finished yet, across each stage it can sit
     * in: waiting in the queue, scheduled, or assigned to a producer. A build order comparing its
     * target against living units alone cannot see what it has already asked for.
     *
     * @param unitType the planned unit
     * @return count of plans for that unit still in flight
     */
    public int outstandingUnitPlanCount(UnitType unitType) {
        int outstanding = productionQueue.unitPlanCount(unitType);
        for (Plan plan : plansScheduled) {
            if (plan.getType() == PlanType.UNIT && plan.getPlannedUnit() == unitType) {
                outstanding += 1;
            }
        }
        for (Plan plan : assignedPlannedItems.values()) {
            if (plan.getType() == PlanType.UNIT
                    && plan.getPlannedUnit() == unitType
                    && !plansScheduled.contains(plan)) {
                outstanding += 1;
            }
        }
        return outstanding;
    }

    public int ourLivingUnitCount(UnitType unitType) {
        return unitTypeCount.livingCount(unitType);
    }

    /**
     * Structures of this type we have committed to, whether or not they can be used yet:
     * completed structures, structures standing on the map but still under construction, and
     * building plans that have not produced a structure yet.
     *
     * <p>{@link #ourUnitCount} sees only the first of the three. The planned count it adds is
     * incremented by unit plans alone, so a structure reads zero from the frame its plan is
     * created to the frame it finishes, and a caller gating on a structure it has already
     * committed to waits out the whole build time. A caller that needs the structure to be
     * usable, rather than committed to, wants {@link #ourUnitCount} instead.
     *
     * <p>The three terms hand over without a gap and without overlapping. A building plan retires
     * on the frame its builder morphs, which is the frame the structure appears on the map, so
     * {@link #incompleteBuildingCount} takes over from
     * {@link #outstandingBuildingPlanCount} there; {@link #ourUnitCount} takes over when
     * construction finishes. A structure destroyed mid-construction is removed from a count it
     * was never added to, so {@link UnitTypeCount#removeUnit} floors at zero to keep the
     * completed term from going negative and hiding the replacement.
     *
     * @param unitType the structure to count
     * @return structures standing, under construction, or claimed by a plan in flight
     */
    public int ourBuildingOrPlannedCount(UnitType unitType) {
        return ourUnitCount(unitType) + incompleteBuildingCount(unitType) + outstandingBuildingPlanCount(unitType);
    }

    /**
     * Structures of this type standing on the map that have not finished constructing.
     *
     * <p>Neither {@link #ourUnitCount} nor {@link #outstandingBuildingPlanCount} covers this
     * interval. A Zerg structure exists from the frame its builder morphs, and that same frame
     * retires its building plan, while the unit type count is not incremented until the structure
     * completes. Without this term a structure is invisible for its whole construction.
     *
     * @param unitType the structure to count
     * @return structures of that type under construction
     */
    public int incompleteBuildingCount(UnitType unitType) {
        int count = 0;
        for (Unit unit : self.getUnits()) {
            if (unit.getType() == unitType && !unit.isCompleted()) {
                count += 1;
            }
        }
        return count;
    }

    /**
     * Building plans of this type that have not produced a structure yet, across every stage one
     * can sit in: waiting in the production queue, scheduled, dispatched to a builder, or
     * morphing.
     *
     * <p>Cancelled plans are excluded because they produce nothing, and completed plans because
     * the structure they produced is counted by {@link #ourUnitCount} or
     * {@link #incompleteBuildingCount} instead.
     *
     * @param unitType the planned structure
     * @return count of building plans for that structure still in flight
     */
    public int outstandingBuildingPlanCount(UnitType unitType) {
        int outstanding = productionQueue.buildingPlanCount(unitType);
        outstanding += buildingPlanCount(plansScheduled, unitType);
        outstanding += buildingPlanCount(plansBuilding, unitType);
        outstanding += buildingPlanCount(plansMorphing, unitType);
        return outstanding;
    }

    static int buildingPlanCount(Set<Plan> plans, UnitType unitType) {
        int count = 0;
        for (Plan plan : plans) {
            if (plan.getType() == PlanType.BUILDING
                    && plan.getPlannedUnit() == unitType
                    && plan.getState() != PlanState.CANCELLED
                    && plan.getState() != PlanState.COMPLETE) {
                count += 1;
            }
        }
        return count;
    }

    public int totalProduced(UnitType unitType) {
        return unitTypeCount.getTotalProduced(unitType);
    }

    public int ourUnitCount(UnitType... unitTypes) {
        int i = 0;
        for (UnitType unitType: unitTypes) {
            i += ourUnitCount(unitType);
        }
        return i;
    }

    public int enemyMobileGroundCombatUnitCount() {
        return observedUnitTracker.getCountOfLivingUnits(Filter::isMobileGroundCombatUnit);
    }

    public int visibleEnemyMobileGroundCombatUnitsAtOurBases() {
        Set<TilePosition> tiles = baseData.ourBaseTiles(gameMap, BaseData.NATURAL_DEFENSE_TILE_RADIUS);
        return observedUnitTracker.getCountOfVisibleUnitsOnTiles(Filter::isMobileGroundCombatUnit, tiles);
    }

    /**
     * Enemy mobile ground combat units last known to be at our bases, whether or not we can see them now.
     *
     * <p>Counts last known positions rather than the visible ones
     * {@link #visibleEnemyMobileGroundCombatUnitsAtOurBases} reads, so an army that has crossed into
     * the fog still reads as present at the base it was last seen approaching.
     *
     * @return the number of living observed enemy ground combat units whose last known tile is at one of our bases
     */
    public int knownEnemyMobileGroundCombatUnitsAtOurBases() {
        Set<TilePosition> tiles = baseData.ourBaseTiles(gameMap, BaseData.NATURAL_DEFENSE_TILE_RADIUS);
        return observedUnitTracker.getCountOfLivingUnitsOnTiles(Filter::isMobileGroundCombatUnit, tiles);
    }

    public int enemyUnitCount(UnitType unitType) {
        return observedUnitTracker.getCountOfLivingUnits(unitType);
    }

    /**
     * Living enemy air units we have observed that carry a weapon.
     *
     * <p>Narrower than the set of sightings that make {@code requiredSpores} ask for a Spore
     * Colony. Air-tech buildings, unarmed flyers and the cloaked ground units a Spore is wanted
     * as a detector against all raise that target while leaving this count at zero, so a reader
     * asking "did we face air" from this column undercounts. What it does guarantee is the
     * converse: a non-zero count means an armed enemy flyer is alive.
     */
    public int observedEnemyAirCombatUnitCount() {
        return observedUnitTracker.getCountOfLivingUnits(Filter::isAirCombatUnit);
    }

    public int getSupply() {
        return game.self().supplyUsed();
    }

    public boolean hasExcessSupply() {
        return SupplyCapacity.isExcess(self.supplyTotal(), self.supplyUsed());
    }

    public TilePosition getTechBuildingLocation(UnitType unitType) {
        Base main = baseData.getMainBase();
        TilePosition position = buildingPlanner.getLocationForTechBuilding(main, unitType);
        buildingPlanner.reservePlannedBuildingTiles(position, unitType);
        return position;
    }

    public Set<Base> basesNeedingSunken(int target) {
        Time tenMinutes = new Time(10, 0);
        Time currentTime = getGameTime();
        int totalSunkens = baseData.getTotalSunkenCount();

        Set<Base> neededBases = new HashSet<>();
        if (currentTime.lessThanOrEqual(tenMinutes) && totalSunkens >= 5) {
            return neededBases;
        }

        if (currentTime.greaterThan(tenMinutes)) {
            target = Math.min(target, 1);
        }


        for (Base base: baseData.getMyBases()) {
            if (baseData.isEligibleForSunkenColony(base) && baseData.sunkensPerBase(base) < target) {
                neededBases.add(base);
            }
        }

        return neededBases;
    }

    public Set<Base> basesNeedingSpore(int target) {
        int totalSpores = baseData.getTotalSporeCount();

        Set<Base> neededBases = new HashSet<>();
        if (totalSpores >= 5) {
            return neededBases;
        }

        for (Base base: baseData.getMyBases()) {
            if (baseData.isEligibleForSporeColony(base) && baseData.sporesPerBase(base) < target) {
                neededBases.add(base);
            }
        }

        return neededBases;
    }

    public boolean canPlanUnit(UnitType unitType) {
        switch (unitType) {
            case Zerg_Zergling:
                return techProgression.isSpawningPool();
            default:
                return false;
        }
    }

    /**
     * Whether an upgrade's prerequisites are met and it is not already researched or queued.
     *
     * <p>Metabolic Boost costs gas, so its Extractor term reads {@link #ourUnitCount} and waits
     * for a finished Extractor rather than one under construction. Every term here names a
     * structure the upgrade needs standing, not one it has merely been committed to.
     */
    public boolean canPlanUpgrade(UpgradeType upgradeType) {
        switch (upgradeType) {
            case Metabolic_Boost:
                return ourUnitCount(UnitType.Zerg_Extractor) > 0 && techProgression.isSpawningPool() && techProgression.canPlanMetabolicBoost();
            case Zerg_Carapace:
                return techProgression.canPlanCarapaceUpgrades();
            case Zerg_Melee_Attacks:
                return techProgression.canPlanMeleeUpgrades();
            case Zerg_Missile_Attacks:
                return techProgression.canPlanRangedUpgrades();
            case Zerg_Flyer_Attacks:
                return techProgression.canPlanFlyerAttack();
            case Zerg_Flyer_Carapace:
                return techProgression.canPlanFlyerDefense();
            default:
                return false;
        }
    }

    public Set<Position> getActiveStormPositions() {
        return psiStormTracker.getActiveStormPositions();
    }

    public boolean isPositionInStorm(Position pos, int buffer) {
        return psiStormTracker.isPositionInStorm(pos, buffer);
    }

    public Set<Position> getStaticDefenseCoverage() {
        Set<Position> coveredPositions = new HashSet<>();

        Map<UnitType, Integer> staticDefenseRanges = new HashMap<>();
        switch (opponentRace) {
            case Terran:
                staticDefenseRanges.put(UnitType.Terran_Missile_Turret, UnitType.Terran_Missile_Turret.airWeapon().maxRange());
                staticDefenseRanges.put(UnitType.Terran_Bunker, UnitType.Terran_Marine.groundWeapon().maxRange() + 32);
                break;
            case Protoss:
                staticDefenseRanges.put(UnitType.Protoss_Photon_Cannon, UnitType.Protoss_Photon_Cannon.groundWeapon().maxRange());
                break;
            case Zerg:
                staticDefenseRanges.put(UnitType.Zerg_Spore_Colony, UnitType.Zerg_Spore_Colony.airWeapon().maxRange());
                staticDefenseRanges.put(UnitType.Zerg_Sunken_Colony, UnitType.Zerg_Sunken_Colony.groundWeapon().maxRange());
                break;
            default:
                return coveredPositions;
        }

        for (Map.Entry<UnitType, Integer> entry : staticDefenseRanges.entrySet()) {
            UnitType defenseType = entry.getKey();
            int range = entry.getValue();

            Set<Position> defensePositions = observedUnitTracker.getLastKnownPositionsOfLivingUnits(defenseType);

            for (Position defensePos : defensePositions) {
                if (defensePos != null) {
                    for (int x = defensePos.getX() - range; x <= defensePos.getX() + range; x += 8) {
                        for (int y = defensePos.getY() - range; y <= defensePos.getY() + range; y += 8) {
                            Position testPos = new Position(x, y);

                            if (Distance.isWithinRange(x, y, defensePos.getX(), defensePos.getY(), range)) {
                                coveredPositions.add(testPos);
                            }
                        }
                    }
                }
            }
        }

        return coveredPositions;
    }

    public Set<Position> getLastKnownLocationOfEnemyWorkers() {
        switch (opponentRace) {
            case Terran:
                return observedUnitTracker.getLastKnownPositionsOfLivingUnits(UnitType.Terran_SCV);
            case Zerg:
                return observedUnitTracker.getLastKnownPositionsOfLivingUnits(UnitType.Zerg_Drone);
            case Protoss:
                return observedUnitTracker.getLastKnownPositionsOfLivingUnits(UnitType.Protoss_Probe);
            default:
                return new HashSet<>();
        }
    }

    public Set<Unit> getDetectedEnemyUnits() {
        return observedUnitTracker.getDetectedUnits();
    }

    public Set<Unit> getEnemyBuildings() {
        return observedUnitTracker.getBuilding();
    }

    public Set<Unit> getCompletedEnemyBuildings() {
        return observedUnitTracker.getCompletedBuildings();
    }

    public Set<Position> getLastKnownPositionsOfBuildings() {
        return observedUnitTracker.getLastKnownPositionsOfBuildings();
    }

    public Set<Unit> getVisibleEnemyUnits() {
        return observedUnitTracker.getVisibleEnemyUnits();
    }

    public Position getSquadRallyPoint() {
        if (baseData.hasNaturalExpansion()) {
            return baseData.naturalExpansionPosition().toPosition();
        } else {
            return baseData.mainBasePosition().toPosition();
        }
    }

    public int enemyResourceDepotCount() {
        switch (opponentRace) {
            case Terran:
                return enemyUnitCount(UnitType.Terran_Command_Center);

            case Protoss:
                return enemyUnitCount(UnitType.Protoss_Nexus);

            case Zerg:
                return enemyUnitCount(UnitType.Zerg_Hatchery) +
                        enemyUnitCount(UnitType.Zerg_Lair) +
                        enemyUnitCount(UnitType.Zerg_Hive);
            default:
                return 0;
        }
    }

    public Set<WalkPosition> getAccessibleWalkPositions() {
        return gameMap.getAccessibleWalkPositions();
    }

    /**
     * True when mined minerals outstrip what our larva-producing hatcheries can spend.
     *
     * <p>Reads completed hatcheries and unreserved minerals. Neither moves when a hatchery plan
     * is queued or cancelled, so the value holds across the enqueue and the cancel that used to
     * toggle it.
     */
    public boolean isFloatingMinerals() {
        return HatcheryCapacity.isFloatingMinerals(
                resourceCount.minedMinerals(),
                hatcheryCount(),
                getGameTime().greaterThan(new Time(5, 0)));
    }

    /**
     * Counts larva-producing hatcheries under our control: completed Hatcheries, Lairs and
     * Hives. A hatchery morphing into a Lair or Hive still counts. Excludes queued, scheduled
     * and morphing plans.
     */
    public int hatcheryCount() {
        return ourUnitCount(UnitType.Zerg_Hatchery, UnitType.Zerg_Lair, UnitType.Zerg_Hive);
    }

    public boolean hasExcessHatchery() {
        return HatcheryCapacity.isExcess(hatcheryCount(), numLarva());
    }

    /**
     * True when an expansion hatchery queued now survives the frame and the request that asks
     * for it has re-armed.
     *
     * <p>Every rule that deletes a queued hatchery plan contributes a term: the excess rule, the
     * early rush reaction and the SCV rush reaction. No term depends on the opponent's race.
     */
    public boolean mayQueueExpansionHatchery() {
        return isHatcheryEnqueueRearmed(false)
                && HatcheryCapacity.isQueueable(hasExcessHatchery(), isEarlyRushed() || isScvRushed());
    }

    /**
     * True when a macro hatchery queued now survives the frame and the request that asks for it
     * has re-armed.
     *
     * <p>The early rush reaction deletes only expansion hatcheries, so it does not suppress this.
     * The excess rule and the SCV rush reaction delete both kinds.
     */
    public boolean mayQueueMacroHatchery() {
        return isHatcheryEnqueueRearmed(true)
                && HatcheryCapacity.isQueueable(hasExcessHatchery(), isScvRushed());
    }

    /**
     * True when the hatchery request of this kind may create another plan.
     *
     * <p>The outstanding count is per kind, so an expansion a drone is still walking to does not
     * hold back the macro hatchery a rush reaction asks for. The cooldown is shared, because
     * both kinds create the same plan and commit the same minerals.
     *
     * @param macroHatchery true for the macro hatchery request, false for the expansion request
     */
    private boolean isHatcheryEnqueueRearmed(boolean macroHatchery) {
        return HatcheryCapacity.isEnqueueRearmed(
                inFlightHatcheryPlans(macroHatchery) + hatcheriesUnderConstruction(macroHatchery),
                getGameTime().getFrames() - lastHatcheryEnqueueFrame);
    }

    /**
     * Hatchery building plans of one kind that the production system still carries.
     *
     * <p>A hatchery plan moves out of the queue and into the scheduled, building or morphing set
     * on the frame it is created, so the queue count alone reads zero while the plan it counted
     * is still in flight.
     *
     * @param macroHatchery true to count macro hatcheries, false to count expansions
     */
    public int inFlightHatcheryPlans(boolean macroHatchery) {
        return countHatcheryPlans(macroHatchery, productionQueue, plansScheduled, plansBuilding, plansMorphing);
    }

    /**
     * Counts hatchery plans of one kind across every stage the production system holds them in.
     *
     * <p>A cancelled plan is not carried, and its set membership does not say so:
     * {@link #cancelPlan} removes a plan from plansBuilding and plansMorphing but not from
     * plansScheduled, and {@link #setImpossiblePlan} removes it from none of them. Both set the
     * plan state, so the state is what this reads.
     */
    static int countHatcheryPlans(boolean macroHatchery, Iterable<Plan> queued, Iterable<Plan> scheduled,
            Iterable<Plan> building, Iterable<Plan> morphing) {
        return countHatcheryPlansIn(macroHatchery, queued)
                + countHatcheryPlansIn(macroHatchery, scheduled)
                + countHatcheryPlansIn(macroHatchery, building)
                + countHatcheryPlansIn(macroHatchery, morphing);
    }

    private static int countHatcheryPlansIn(boolean macroHatchery, Iterable<Plan> plans) {
        int count = 0;
        for (Plan plan : plans) {
            if (isOutstandingHatcheryPlan(plan, macroHatchery)) {
                count += 1;
            }
        }
        return count;
    }

    /**
     * True when this plan is a hatchery of the given kind that the bot is still committed to.
     */
    static boolean isOutstandingHatcheryPlan(Plan plan, boolean macroHatchery) {
        return plan.getState() != PlanState.CANCELLED
                && plan.getType() == PlanType.BUILDING
                && plan.getPlannedUnit() == UnitType.Zerg_Hatchery
                && plan.isMacroHatchery() == macroHatchery;
    }

    /**
     * Hatcheries of one kind we have started and not finished. The plan that produced one is
     * complete the moment the drone morphs, and {@link #hatcheryCount} does not count the
     * building until it finishes, so neither side sees it while it is going up.
     *
     * <p>A hatchery on a base tile is an expansion and any other is a macro hatchery, the same
     * split InformationManager applies when the building completes.
     *
     * @param macroHatchery true to count macro hatcheries, false to count expansions
     */
    public int hatcheriesUnderConstruction(boolean macroHatchery) {
        int count = 0;
        for (Unit unit : self.getUnits()) {
            if (unit.getType() != UnitType.Zerg_Hatchery || unit.isCompleted()) {
                continue;
            }

            boolean isMacro = !baseData.isBaseTilePosition(unit.getTilePosition());
            if (isMacro == macroHatchery) {
                count += 1;
            }
        }
        return count;
    }

    public void setGeyserAssignment(Unit unit) {
        geyserAssignments.put(unit, new HashSet<>());
    }

    public TilePosition pollScoutTarget() {
        if (baseData.getMainEnemyBase() == null && !scoutData.isEnemyBuildingLocationKnown()) {
            Base baseTarget = fetchBaseRoundRobin(scoutData.getScoutingBaseSet());
            if (baseTarget != null) {
                int assignments = scoutData.getScoutsAssignedToBase(baseTarget);
                scoutData.updateBaseScoutAssignment(baseTarget, assignments);
                return baseTarget.getLocation();
            }
        }

        if (scoutData.isEnemyBuildingLocationKnown()) {
            for (TilePosition target: scoutData.getEnemyBuildingPositions()) {
                if (!scoutData.hasScoutTarget(target) && !game.isVisible(target)) {
                    return target;
                }
            }
        }

        TilePosition scoutTile = getHotScoutTile();
        if (scoutTile != null) return scoutTile;

        return scoutData.findNewActiveScoutTarget();
    }

    @Nullable
    private TilePosition getHotScoutTile() {
        ArrayList<MapTile> heatMap = gameMap.getHeatMap();
        if (!heatMap.isEmpty()) {
            MapTile scoutTile = heatMap.get(0);
            scoutTile.setScoutImportance(0);
            gameMap.ageHeatMap();
            return scoutTile.getTile();
        }
        return null;
    }

    private Base fetchBaseRoundRobin(Set<Base> candidateBases) {
        Base leastScoutedBase = null;
        Integer fewestScouts = Integer.MAX_VALUE;
        for (Base base: candidateBases) {
            Integer assignedScoutsToBase = scoutData.getScoutsAssignedToBase(base);
            if (assignedScoutsToBase < fewestScouts) {
                leastScoutedBase = base;
                fewestScouts = assignedScoutsToBase;
            }
        }
        return leastScoutedBase;
    }

    public int getCountOfAllEnemyUnits() {
        return observedUnitTracker.getCountOfAllEnemyUnits();
    }
}
