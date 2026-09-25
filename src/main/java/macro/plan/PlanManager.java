package macro.plan;

import bwapi.Game;
import bwapi.Order;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.Base;
import bwem.Mineral;
import info.BaseData;
import info.BuilderThreat;
import info.GameState;
import info.map.GameMap;
import telemetry.PlanEvents;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;
import util.Distance;
import util.TravelTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PlanManager {

    private Game game;
    private GameState gameState;

    private HashSet<ManagedUnit> assignedManagedWorkers;
    private HashSet<ManagedUnit> gasGatherers;
    private HashSet<ManagedUnit> larva;
    private HashSet<ManagedUnit> scheduledDrones = new HashSet<>();
    private DispatchedBuilders<ManagedUnit> dispatchedDrones = new DispatchedBuilders<>();
    private BuilderReleases<ManagedUnit> builderReleases = new BuilderReleases<>();


    public PlanManager(Game game, GameState gameState) {
        this.game = game;
        this.gameState = gameState;
        this.larva = gameState.getLarva();
        this.gasGatherers = gameState.getGasGatherers();
        this.assignedManagedWorkers = gameState.getAssignedManagedWorkers();
    }

    public void onFrame() {
        fixOutOfBoundsBuildingPlans();
        assignScheduledPlannedItems();
        releaseLostBuilders();
        recallThreatenedBuilders();
        executeScheduledDrones();
        releaseImpossiblePlans();
    }

    private void fixOutOfBoundsBuildingPlans() {
        GameMap gameMap = gameState.getGameMap();
        BaseData baseData = gameState.getBaseData();

        List<Plan> allActivePlans = new ArrayList<>(gameState.getPlansScheduled());
        allActivePlans.addAll(gameState.getPlansBuilding());

        Set<TilePosition> validExtractorPositions = collectValidExtractorPositions(allActivePlans, gameMap);

        for (Plan plan : allActivePlans) {
            if (plan.getType() != PlanType.BUILDING) {
                continue;
            }
            TilePosition bp = plan.getBuildPosition();
            if (bp == null || gameMap.isValidTile(bp)) {
                continue;
            }
            if (plan.getPlannedUnit() == UnitType.Zerg_Extractor) {
                TilePosition fixed = baseData.findUnassignedExtractorPosition(validExtractorPositions);
                if (fixed != null) {
                    plan.setBuildPosition(fixed);
                    validExtractorPositions.add(fixed);
                } else {
                    plan.setCancelSource(PlanCancelSource.PLAN_MANAGER_INVALID_BUILD_POSITION);
                    gameState.setImpossiblePlan(plan);
                }
            } else {
                plan.setCancelSource(PlanCancelSource.PLAN_MANAGER_INVALID_BUILD_POSITION);
                gameState.setImpossiblePlan(plan);
            }
        }
    }

    private Set<TilePosition> collectValidExtractorPositions(List<Plan> plans, GameMap gameMap) {
        Set<TilePosition> positions = new HashSet<>();
        for (Plan plan : plans) {
            TilePosition bp = plan.getBuildPosition();
            if (plan.getPlannedUnit() == UnitType.Zerg_Extractor && bp != null && gameMap.isValidTile(bp)) {
                positions.add(bp);
            }
        }
        return positions;
    }

    private void assignScheduledPlannedItems() {
        List<Plan> scheduledPlans = new ArrayList<>(gameState.getPlansScheduled());
        if (scheduledPlans.isEmpty()) {
            return;
        }

        Collections.sort(scheduledPlans, new PlanComparator());
        List<Plan> assignedPlans = new ArrayList<>();

        for (Plan plan : scheduledPlans) {
            UnitType planType = plan.getPlannedUnit();

            boolean didAssign = false;
            if (plan.getType() == PlanType.BUILDING) {
                if (isBuildingMorph(planType)) continue;
                didAssign = assignMorphDrone(plan);
            } else if (plan.getType() == PlanType.UNIT) {
                didAssign = assignMorphUnit(plan);
            }

            if (didAssign) {
                assignedPlans.add(plan);
            }
        }

        HashSet<Plan> buildingPlans = gameState.getPlansBuilding();
        for (Plan plan : assignedPlans) {
            scheduledPlans.remove(plan);
            buildingPlans.add(plan);
        }

        gameState.setPlansScheduled(new HashSet<>(scheduledPlans));
    }

    private void releaseImpossiblePlans() {
        HashSet<Plan> impossiblePlans = gameState.getPlansImpossible();

        for (ManagedUnit larva: larva) {
            Plan currentPlan = larva.getPlan();
            if (currentPlan != null && impossiblePlans.contains(currentPlan)) {
                gameState.cancelPlan(larva.getUnit(), currentPlan, PlanCancelSource.PLAN_MANAGER_RELEASE_IMPOSSIBLE);
            }
        }

        for (ManagedUnit drone: scheduledDrones) {
            Plan currentPlan = drone.getPlan();
            if (currentPlan != null && impossiblePlans.contains(currentPlan)) {
                gameState.cancelPlan(drone.getUnit(), currentPlan, PlanCancelSource.PLAN_MANAGER_RELEASE_IMPOSSIBLE);
            }
        }
    }

    private boolean isBuildingMorph(UnitType unitType) {
        switch (unitType) {
            case Zerg_Lair:
            case Zerg_Hive:
            case Zerg_Sunken_Colony:
            case Zerg_Spore_Colony:
                return true;
            default:
                return false;
        }
    }

    private void executeScheduledDrones() {
        final int currentFrame = game.getFrameCount();
        List<ManagedUnit> executed = new ArrayList<>();
        for (ManagedUnit managedUnit: scheduledDrones) {
            Plan plan = managedUnit.getPlan();
            if (plan == null || plan.getState() == PlanState.CANCELLED) {
                executed.add(managedUnit);
                continue;
            }
            int travelFrames = this.getTravelFrames(managedUnit.getUnit(), plan.getBuildPosition().toPosition());
            if (currentFrame <= plan.getPredictedReadyFrame() - travelFrames) {
                continue;
            }
            BuilderThreat threat = builderThreat(managedUnit, plan);
            BuilderDispatchDecision decision = dispatchDecision(threat, gameState.isColonyBuilderBackedOff(plan));
            PlanEvents.builderDispatchDecision(plan, decision, threat);
            if (!decision.isDispatch()) {
                continue;
            }
            gameState.clearAssignments(managedUnit);
            plan.setState(PlanState.BUILDING);
            managedUnit.setRole(UnitRole.BUILD);
            dispatchedDrones.dispatch(managedUnit, plan, currentFrame, travelFrames);
            executed.add(managedUnit);
        }

        for (ManagedUnit managedUnit: executed) {
            scheduledDrones.remove(managedUnit);
        }
    }

    /**
     * Re-runs the dispatch gate against every builder already walking whose morph has not been
     * issued, and pulls back the ones whose ground has gone hot since they left.
     *
     * <p>A launch-time-only gate does not save a builder that walks into an army it could not see
     * at launch, which is how the first expansion of LMR9R0MB died: nothing was known at any base
     * on the frame it was dispatched. A recall restores exactly the state the builder left from -
     * the plan parked in SCHEDULE still holding its build-ahead claim, the drone back on minerals
     * with the plan still assigned to it - so the next frame evaluates it like any other scheduled
     * builder and dispatches it again once the route is clear.
     *
     * <p>It runs the same predicate as the launch gate, so a builder working at a base we hold is
     * not pulled off it: recalling a drone from a threatened home site is the same defect as
     * refusing to send it there. The exception is the one the launch gate makes, a Creep Colony at
     * a base that has already lost a colony builder, whose builder is recalled like any other.
     */
    private void recallThreatenedBuilders() {
        List<ManagedUnit> recalled = new ArrayList<>();
        for (Map.Entry<ManagedUnit, Plan> entry: dispatchedDrones.snapshot()) {
            ManagedUnit managedUnit = entry.getKey();
            Plan plan = entry.getValue();
            if (plan.getState() != PlanState.BUILDING) {
                recalled.add(managedUnit);
                continue;
            }
            BuilderThreat threat = builderThreat(managedUnit, plan);
            if (dispatchDecision(threat, gameState.isColonyBuilderBackedOff(plan)).isDispatch()) {
                continue;
            }
            PlanEvents.builderDispatchDecision(plan, BuilderDispatchDecision.RECALLED, threat);
            plan.setState(PlanState.SCHEDULE);
            managedUnit.setRole(UnitRole.IDLE);
            scheduledDrones.add(managedUnit);
            recalled.add(managedUnit);
        }

        for (ManagedUnit managedUnit: recalled) {
            dispatchedDrones.undispatch(managedUnit);
        }
    }

    /**
     * Releases every walking builder that no longer executes the plan it was dispatched for, and
     * drops the ones whose plan has left BUILDING.
     *
     * <p>The morph of a drone-built building is issued only by a builder in role BUILD, bound to the
     * plan and within arrival distance of the site. A builder that fails any of those while the plan
     * sits in BUILDING holds the plan, its build-ahead claim and its reservation until the claim is
     * evicted. See {@link BuilderLossReason} for the reasons a builder is lost. A plan past its
     * {@link BuilderReleases} stray cap is no longer measured for a stray and keeps its builder.
     */
    private void releaseLostBuilders() {
        final int frame = game.getFrameCount();
        for (Map.Entry<ManagedUnit, Plan> entry : dispatchedDrones.snapshot()) {
            ManagedUnit builder = entry.getKey();
            Plan plan = entry.getValue();
            if (plan.getState() != PlanState.BUILDING) {
                dispatchedDrones.undispatch(builder);
                if (isSettled(plan.getState())) {
                    builderReleases.forget(plan);
                }
                continue;
            }
            BuilderLossReason reason = lossReason(builder, plan, frame);
            if (reason != null) {
                releaseLostBuilder(builder, plan, reason);
            }
        }
    }

    private BuilderLossReason lossReason(ManagedUnit builder, Plan plan, int frame) {
        Unit unit = builder.getUnit();
        boolean planBound = builder.getPlan() == plan && plan.equals(gameState.getAssignedPlannedItems().get(unit));
        TilePosition buildPosition = plan.getBuildPosition();
        if (buildPosition == null || !builderReleases.mayStray(plan)) {
            return BuilderLossReason.of(builder.getRole() == UnitRole.BUILD, planBound, false);
        }
        Position moveTarget = siteMoveTarget(plan.getPlannedUnit(), buildPosition);
        boolean strayed = dispatchedDrones.strayOf(builder).isStrayed(
                unit.getPosition(),
                unit.getDistance(moveTarget),
                isHeadingTo(unit.getOrder(), unit.getOrderTargetPosition(), moveTarget),
                coversCost(game.self().minerals(), game.self().gas(), plan),
                unit.isCarrying() || builder.isClearingBlocker(),
                frame);
        return BuilderLossReason.of(builder.getRole() == UnitRole.BUILD, planBound, strayed);
    }

    /**
     * Whether a builder's order is a move to the site's move target, the order
     * {@code ManagedUnit.build()} gives a builder walking to its site.
     *
     * @param order the builder's order
     * @param orderTarget the builder's order target position
     * @param moveTarget the site's move target
     */
    static boolean isHeadingTo(Order order, Position orderTarget, Position moveTarget) {
        return order == Order.Move && orderTarget != null
                && orderTarget.getDistance(moveTarget) <= BuilderStray.CLOSING_PROGRESS;
    }

    /** Whether a plan in this state will not be dispatched again. */
    static boolean isSettled(PlanState state) {
        return state == PlanState.MORPHING || state == PlanState.COMPLETE || state == PlanState.CANCELLED;
    }

    /**
     * Releases a lost builder and returns its plan to SCHEDULE without an executor, so
     * {@link #assignMorphDrone} assigns a new builder on the next frame. The plan keeps its
     * build-ahead claim and its resource reservation, since neither is released here and the claim
     * is kept for any plan in SCHEDULE or BUILDING.
     *
     * <p>The builder drops the plan only while it still holds it. A builder that still reads BUILD
     * with no plan left goes IDLE, so the worker manager takes it back; a builder another manager
     * gave a new role keeps that role.
     *
     * @param builder the executor the plan loses
     * @param plan the plan, in BUILDING
     * @param reason why the builder is lost
     */
    private void releaseLostBuilder(ManagedUnit builder, Plan plan, BuilderLossReason reason) {
        dispatchedDrones.undispatch(builder);
        builderReleases.record(plan, builder, reason, game.getFrameCount());
        if (builder.getPlan() == plan) {
            builder.setPlan(null);
        }
        builder.setRole(roleAfterRelease(builder.getRole(), builder.getPlan() != null));
        returnToSchedule(plan, gameState.getAssignedPlannedItems(), gameState.getPlansBuilding(),
                gameState.getPlansScheduled());
        PlanEvents.builderDispatchDecision(plan, reason.decision(), builderThreat(builder, plan));
    }

    /**
     * The role a released builder is left in: IDLE for a builder still in BUILD with no plan left,
     * so the worker manager takes it back, and otherwise the role it already has.
     *
     * @param role the builder's role on release
     * @param holdsPlan whether the builder still holds a plan after dropping the released one
     */
    static UnitRole roleAfterRelease(UnitRole role, boolean holdsPlan) {
        return role == UnitRole.BUILD && !holdsPlan ? UnitRole.IDLE : role;
    }

    /**
     * Moves a BUILDING plan back to SCHEDULE and unbinds every executor mapped to it, so no unit is
     * left holding a plan that no longer names it.
     *
     * @param plan the plan to return
     * @param assignedPlannedItems executor to plan assignments
     * @param plansBuilding plans in BUILDING
     * @param plansScheduled plans in SCHEDULE
     * @param <U> the executor type
     */
    static <U> void returnToSchedule(Plan plan, Map<U, Plan> assignedPlannedItems, Set<Plan> plansBuilding,
            Set<Plan> plansScheduled) {
        assignedPlannedItems.values().removeIf(plan::equals);
        plansBuilding.remove(plan);
        plansScheduled.add(plan);
        plan.setState(PlanState.SCHEDULE);
    }

    /**
     * Whether the bank covers a plan's own cost, ignoring what other plans reserve.
     *
     * @param minerals minerals in the bank
     * @param gas gas in the bank
     * @param plan the plan
     */
    static boolean coversCost(int minerals, int gas, Plan plan) {
        return minerals >= plan.mineralPrice() && gas >= plan.gasPrice();
    }

    /** The point a builder walks to for a building at this tile, the centre of its footprint. */
    static Position siteMoveTarget(UnitType building, TilePosition buildPosition) {
        return buildPosition.toPosition().add(new Position(building.tileWidth() * 16, building.tileHeight() * 16));
    }

    private BuilderThreat builderThreat(ManagedUnit drone, Plan plan) {
        return gameState.builderThreat(plan.getBuildPosition(), drone.getUnit().getTilePosition());
    }

    /**
     * Whether a builder may leave for its site, and what stops it when it may not.
     *
     * <p>A builder standing on a base we hold, sent to a site at a base we hold, is never held.
     * The gate exists to stop a lone drone setting out across the map into a contested expansion,
     * and a walk with both ends on ground we own is not that walk. Holding it refuses the creep
     * colony a sunken grows from at the moment enemies arrive, which is the condition that makes
     * the sunken worth having. A 900-game batch measured the cost: the gate blocked 4,005
     * departures against 3,873 allowed, 2,831 of them creep colonies, and the run fell from 15.5%
     * to 6.8%. The carve-out is keyed on ownership rather than on unit type because the walk, not
     * the building, is what the gate is about.
     *
     * <p>Both ends are required. A site of ours whose own drones are all carrying, on gas or
     * already building hands the plan to the nearest drone anywhere on the map, and that drone
     * does set out across it; the full gate still applies to it.
     *
     * <p>The site is read before the route because it is the more specific answer: a plan held for
     * enemies standing on the ground it would build on says something a corridor count does not.
     * Away from our bases a builder already at the site no longer waves the site check through;
     * that bypass was an accidental proxy for the builder being home, and the carve-out reads
     * ownership directly instead.
     *
     * <p>A Creep Colony at a base that has already lost a colony builder does not get the
     * carve-out. Enemies at a home site that has already killed a builder are enemies the next one
     * walks into, so it waits for the site and the route to clear like any other builder. Bases
     * that have lost no colony builder keep the carve-out.
     *
     * @param threat what the builder would walk into
     * @param colonyBackoff whether the plan is a Creep Colony at a base that has lost a colony
     *     builder, from {@link GameState#isColonyBuilderBackedOff(Plan)}
     */
    static BuilderDispatchDecision dispatchDecision(BuilderThreat threat, boolean colonyBackoff) {
        if (threat.getSiteEnemies() == 0 && threat.getRouteEnemies() == 0 && threat.getRouteDefenseZones() == 0) {
            return BuilderDispatchDecision.DISPATCH;
        }
        if (!colonyBackoff && threat.isSiteAtOurBase() && threat.isBuilderAtOurBase()) {
            return BuilderDispatchDecision.DISPATCH_HOME_SITE;
        }
        if (threat.getSiteEnemies() > 0) {
            return BuilderDispatchDecision.HOLD_SITE_THREAT;
        }
        return BuilderDispatchDecision.HOLD_PATH_THREAT;
    }

    private int getTravelFrames(Unit unit, Position buildingPosition) {
        return TravelTime.framesToReach(unit, buildingPosition);
    }

    /**
     * Assign a drone to the building plan if it's not carrying resources, not mining gas and not already assigned to a plan.
     * The unit will store a scheduled plan until it's time to execute.
     * If the plan has an assigned building location, find the drone closest to the location.
     * A Creep Colony takes a drone already on its site's base ahead of a closer one elsewhere, so
     * its builder does not cross contested ground between two of our bases to reach it.
     * A drone recently released from this plan is skipped, see {@link BuilderReleases}.
     * @param plan plan to build
     * @return true if plan assigned, false otherwise
     */
    private boolean assignMorphDrone(Plan plan) {
        final int frame = game.getFrameCount();
        List<ManagedUnit> eligibleDrones = assignedManagedWorkers
                .stream()
                .filter(d -> {
                    Unit unit = d.getUnit();
                    return !unit.isCarrying() && !gasGatherers.contains(d) && !gameState.getAssignedPlannedItems().containsKey(unit);
                })
                .filter(d -> !builderReleases.isBackedOff(plan, d, frame))
                .collect(Collectors.toList());

        TilePosition buildPosition = plan.getBuildPosition();
        if (buildPosition != null) {
            Comparator<ManagedUnit> order = Distance.closestManagedUnitTo(buildPosition.toPosition());
            if (plan.getPlannedUnit() == UnitType.Zerg_Creep_Colony) {
                Set<TilePosition> siteTiles = gameState.siteTiles(buildPosition);
                order = atSiteFirst(d -> siteTiles.contains(d.getUnit().getTilePosition()), order);
            }
            eligibleDrones.sort(order);
        }

        if (eligibleDrones.isEmpty()) {
            return false;
        }

        ManagedUnit managedUnit = eligibleDrones.get(0);
        Unit unit = managedUnit.getUnit();
        scheduledDrones.add(managedUnit);
        managedUnit.setPlan(plan);
        gameState.getAssignedPlannedItems().put(unit, plan);
        return true;
    }

    /**
     * Orders candidates standing at the site ahead of those that are not, then by the given order.
     *
     * @param atSite whether a candidate stands on the site's base tiles
     * @param then the order within each group
     * @param <T> the candidate type
     */
    static <T> Comparator<T> atSiteFirst(Predicate<T> atSite, Comparator<T> then) {
        return Comparator.comparing((T candidate) -> !atSite.test(candidate)).thenComparing(then);
    }

    private boolean assignMorphUnit(Plan plan) {
        switch (plan.getPlannedUnit()) {
            case Zerg_Lurker:
                return assignMorphHydralisk(plan);
            default:
                return assignMorphLarva(plan);
        }
    }

    private boolean assignMorphHydralisk(Plan plan) {
        List<ManagedUnit> hydralisks = gameState.getManagedUnitsByType(UnitType.Zerg_Hydralisk);
        for (ManagedUnit managedUnit: hydralisks) {
            Unit unit = managedUnit.getUnit();
            if (!gameState.getAssignedPlannedItems().containsKey(unit)) {
                gameState.clearAssignments(managedUnit);
                plan.setState(PlanState.BUILDING);
                managedUnit.setRole(UnitRole.MORPH);
                managedUnit.setPlan(plan);
                gameState.getAssignedPlannedItems().put(unit, plan);
                return true;
            }
        }
        return false;
    }

    private boolean assignMorphLarva(Plan plan) {
        List<ManagedUnit> availableLarva = new ArrayList<>();
        for (ManagedUnit managedUnit : larva) {
            Unit unit = managedUnit.getUnit();
            if (!gameState.getAssignedPlannedItems().containsKey(unit)) {
                availableLarva.add(managedUnit);
            }
        }

        if (availableLarva.isEmpty()) {
            return false;
        }

        UnitType plannedUnit = plan.getPlannedUnit();
        ManagedUnit selectedLarva = null;

        if (availableLarva.size() > 1) {
            if (plannedUnit == UnitType.Zerg_Drone) {
                List<ManagedUnit> prioritizedLarva = availableLarva.stream()
                        .filter(l -> {
                            Base base = getBaseForLarva(l);
                            return base != null && hasUnderSaturatedResources(base);
                        })
                        .collect(Collectors.toList());

                if (!prioritizedLarva.isEmpty()) {
                    selectedLarva = prioritizedLarva.get(0);
                }
            } else if (plannedUnit == UnitType.Zerg_Overlord) {
                Base naturalBase = getNaturalExpansionBase();
                if (naturalBase != null) {
                    List<ManagedUnit> naturalLarva = availableLarva.stream()
                            .filter(l -> {
                                Base base = getBaseForLarva(l);
                                return base != null && base.equals(naturalBase);
                            })
                            .collect(Collectors.toList());

                    if (!naturalLarva.isEmpty()) {
                        selectedLarva = naturalLarva.get(0);
                    }
                }
            }
        }

        if (selectedLarva == null) {
            selectedLarva = availableLarva.get(0);
        }

        Unit unit = selectedLarva.getUnit();
        gameState.clearAssignments(selectedLarva);
        plan.setState(PlanState.BUILDING);
        selectedLarva.setRole(UnitRole.MORPH);
        selectedLarva.setPlan(plan);
        gameState.getAssignedPlannedItems().put(unit, plan);
        return true;
    }

    private Base getBaseForLarva(ManagedUnit larva) {
        Unit larvaUnit = larva.getUnit();
        Unit hatchery = larvaUnit.getHatchery();

        if (hatchery != null) {
            return gameState.getBaseData().get(hatchery);
        }

        HashSet<Unit> baseHatcheries = gameState.getBaseData().baseHatcheries();
        if (baseHatcheries.isEmpty()) {
            return null;
        }

        Unit closestHatchery = null;
        double closestDistance = Double.MAX_VALUE;
        for (Unit h : baseHatcheries) {
            double distance = larvaUnit.getDistance(h);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestHatchery = h;
            }
        }

        if (closestHatchery != null) {
            return gameState.getBaseData().get(closestHatchery);
        }

        return null;
    }

    private boolean hasUnderSaturatedResources(Base base) {
        HashMap<Unit, HashSet<ManagedUnit>> mineralAssignments = gameState.getMineralAssignments();
        for (Mineral mineral : base.getMinerals()) {
            Unit mineralUnit = mineral.getUnit();
            if (mineralAssignments.containsKey(mineralUnit)) {
                HashSet<ManagedUnit> mineralUnits = mineralAssignments.get(mineralUnit);
                if (mineralUnits.size() < 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private Base getNaturalExpansionBase() {
        BaseData baseData = gameState.getBaseData();
        if (!baseData.hasNaturalExpansion()) {
            return null;
        }
        return baseData.baseAtTilePosition(baseData.naturalExpansionPosition());
    }
}
