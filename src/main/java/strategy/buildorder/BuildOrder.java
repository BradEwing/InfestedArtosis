package strategy.buildorder;

import bwapi.Race;
import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwem.Base;
import info.BaseData;
import info.GameState;
import info.Readiness;
import info.ResourceCount;
import info.TechProgression;
import info.UnitTypeCount;
import info.map.BuildingPlanner;
import lombok.Getter;
import macro.AdvancedUnitEligibility;
import macro.HatcheryCapacity;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanType;
import macro.plan.TechPlan;
import macro.plan.UnitPlan;
import macro.plan.UpgradePlan;
import telemetry.PlanEvents;
import util.Time;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;
import java.util.function.ToLongFunction;

public abstract class BuildOrder {
    private static final int EARLY_RUSH_SECOND_SUNKEN_ATTACKERS = 4;
    private static final int EARLY_RUSH_MIN_ZERGLINGS = 6;
    public static final int EMERGENCY_DEFENSE_PRIORITY = 1;
    protected static final int SPAWNING_POOL_PRIORITY = 2;
    private static final int DEFAULT_COLONY_PRIORITY = 5;
    private static final int UNKNOWN_RACE_BASE_TARGET = 2;
    private static final int UNKNOWN_RACE_ZERGLING_PLANS = 2;

    /**
     * Multiplier that lets a base tile collapse to one sortable number. Larger than any Brood War
     * map dimension, so no two tiles can share a rank.
     */
    private static final long TILE_RANK_STRIDE = 1024;

    /**
     * Rank added to every base that is not the main, above anything {@link #TILE_RANK_STRIDE} can
     * produce, so the main always sorts first when it is a candidate.
     */
    private static final long NON_MAIN_BASE_RANK = 1L << 32;

    /**
     * The only unit every terminal build can morph without gas once its Spawning Pool is up, which
     * is what makes it the sink for minerals a gas-hungry composition cannot spend.
     */
    static final UnitType MINERAL_SURPLUS_UNIT = UnitType.Zerg_Zergling;

    static final int MINERAL_SURPLUS = 400;

    static final int MAX_QUEUED_SURPLUS_PLANS = 4;

    @Getter
    private final String name;
    protected Time activatedAt;

    /**
     * Frame the unreserved gas bank reached {@link GasBoundHiveTech#BRANCH_GAS} and has held it
     * since, or {@link GasBoundHiveTech#NOT_HELD} while the last observation was below the bar.
     */
    private int hiveTechGasSinceFrame = GasBoundHiveTech.NOT_HELD;

    /** Frame the unreserved gas bank was last read for the Hive-branch hold. */
    private int hiveTechGasEvaluatedFrame;

    protected BuildOrder(String name) {
        this.name = name;
    }

    public boolean shouldTransition(GameState gameState) {
        return gameState.getOpponentRace() != Race.Unknown && openerComplete(gameState);
    }

    /**
     * True when the opener's scripted build has produced everything it will produce. Build
     * orders that are not openers leave this false.
     */
    protected boolean openerComplete(GameState gameState) {
        return false;
    }

    public Set<BuildOrder> transition(GameState gameState) {
        return new HashSet<>();
    }

    /**
     * The plans the build order asks for this frame.
     *
     * <p>Final: the build's own plans come from {@link #buildPlans}, and the shared larva-bound
     * macro hatchery step runs after them whatever the build said. A build order that never
     * mentions the macro hatchery still makes the request, which is what stops the rule being
     * lost again by a build that simply says nothing.
     *
     * <p>An opener is the one exception. It hands over before any tech condition can hold, so the
     * request could only ever stop on its tech gate, and running it would write gate rows naming
     * a build that can never answer them.
     *
     * @param gameState current game state
     * @return the build's plans, plus a macro hatchery when the shared request fires
     */
    public final List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>(buildPlans(gameState));

        if (!runsLarvaBoundMacroHatchery(isOpener(), plans)) {
            return plans;
        }

        Plan macroHatchery = larvaBoundMacroHatchery(gameState);
        if (macroHatchery != null) {
            plans.add(macroHatchery);
        }

        return plans;
    }

    /**
     * The build order's own plans for this frame.
     *
     * @param gameState current game state
     * @return the plans this build wants, which {@link #plan} appends the shared steps to
     */
    protected abstract List<Plan> buildPlans(GameState gameState);

    /**
     * The build's tech condition for the larva-bound macro hatchery.
     *
     * <p>Abstract so a new build order has to state it. A build with no tech unit to be larva
     * bound on - an opener, or a build that only ever makes Zerglings - answers false.
     *
     * @param techProgression the bot's tech state
     * @return true once the tech whose units the build spends its larva on is finished
     * @see LarvaBoundMacroHatchery#evaluate
     */
    protected abstract boolean macroHatcheryTechReady(TechProgression techProgression);

    public abstract boolean playsRace(Race race);

    public boolean isOpener() {
        return false;
    }

    /**
     * True while the build wants no Overlord queued by the shared supply planner.
     *
     * <p>Defaults to false. A build that scripts its own supply timing answers true for as long
     * as an Overlord would take minerals from steps it has not finished.
     *
     * @param gameState current game state
     * @return true while the supply planner must not queue an Overlord
     */
    public boolean holdsOverlords(GameState gameState) {
        return false;
    }

    /**
     * Whether no opener offers this build order any more. A retired build order stays registered so
     * learning rows that name it still resolve, but it is not seeded as a playable arm.
     */
    public boolean isRetired() {
        return false;
    }

    public boolean needLair() {
        return false;
    }

    public boolean needHive() {
        return false;
    }

    /**
     * Returns true if Overlord Speed should be researched, based on Lair, game time and unit triggers.
     *
     * <p>The Lair term reads {@link Readiness#USABLE}, which counts only finished Lairs.
     * That is what it wants: the upgrade is researched at the Lair, so a Lair still morphing
     * cannot start it.
     */
    public boolean needOverlordSpeed(GameState gameState) {
        if (gameState.structureCount(Readiness.USABLE, bwapi.UnitType.Zerg_Lair) < 1) {
            return false;
        }
        if (gameState.getGameTime().greaterThan(new util.Time(12, 0))) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Zerg_Lurker) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Protoss_Templar_Archives) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Protoss_Fleet_Beacon) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Protoss_Dark_Templar) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Protoss_Observer) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Terran_Vulture_Spider_Mine) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Terran_Science_Vessel) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Protoss_Stargate) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Terran_Starport) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Terran_Valkyrie) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Terran_Wraith) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Protoss_Scout) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Protoss_Corsair) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Zerg_Devourer) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Zerg_Greater_Spire) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Zerg_Hive) > 0) {
            return true;
        }
        return false;
    }

    /**
     * Sunkens per base the bot wants: the matchup term under the race agnostic floors.
     * <p>
     * The floors are applied here rather than in each matchup because a matchup class is not
     * always in play. SpeedlingAllIn plays every race and every opener extends this class
     * directly, so for those builds {@link #matchupSunkens(GameState)} is the zero default and a
     * floor is the only thing that can answer a threat. Applying them once here also means every
     * reader of this number - the defense path, the default colony helper and each build order's
     * own plan loop - sees the same target.
     */
    protected final int requiredSunkens(GameState gameState) {
        return SunkenTargets.sunkenTarget(matchupSunkens(gameState),
                gameState.getStrategyTracker().isDetectedStrategy(SunkenTargets.ONE_BASE_STRATEGY),
                gameState.getBaseData().getEnemyBases().size(),
                SunkenTargets.hasGroundLead(gameState.getOpponentRace(),
                        gameState.ourLivingUnitCount(UnitType.Zerg_Zergling),
                        gameState.enemyUnitCount(UnitType.Zerg_Zergling)),
                gameState.enemyUnitCount(UnitType.Terran_Barracks),
                gameState.getGameTime());
    }

    /**
     * Sunkens per base the matchup asks for, before any race agnostic floor. Each race's base
     * class overrides this; {@link #requiredSunkens(GameState)} is what callers read.
     */
    protected int matchupSunkens(GameState gameState) {
        return 0;
    }

    protected int requiredSpores(GameState gameState) {
        return 0;
    }

    protected int zerglingsNeeded(GameState gameState) {
        return 6;
    }

    /**
     * The sunken floor an early rush sets, keyed on enemy ground combat units at our bases.
     * <p>
     * EarlyRush is inferred from what the scout saw of the opponent's build, often minutes before
     * any of that army moves, so the detection alone commits nothing. The floor opens once an
     * enemy ground combat unit is known at one of our bases. Last known positions are read for
     * that, so a rush that has crossed into the fog near a base still counts. The second sunken
     * still waits for enough attackers to be visible there now. Workers are not ground combat
     * units, so a scouting worker opens nothing.
     *
     * @param knownAttackers living enemy ground combat units last known to be at our bases
     * @param visibleAttackers enemy ground combat units visible at our bases now
     * @return sunkens per base the rush asks for
     */
    static int earlyRushSunkens(int knownAttackers, int visibleAttackers) {
        if (knownAttackers == 0 && visibleAttackers == 0) {
            return 0;
        }
        return visibleAttackers >= EARLY_RUSH_SECOND_SUNKEN_ATTACKERS ? 2 : 1;
    }

    /**
     * The zergling floor an early rush sets: two per enemy ground combat unit known at our bases,
     * and never fewer than the minimum. Units seen only elsewhere, such as the army still in the
     * enemy main, do not raise it.
     *
     * @param knownAttackers living enemy ground combat units last known to be at our bases
     * @return zerglings the rush asks for
     */
    static int earlyRushZerglings(int knownAttackers) {
        return Math.max(EARLY_RUSH_MIN_ZERGLINGS, 2 * knownAttackers);
    }

    /**
     * Whether the emergency owes another zergling plan.
     * <p>
     * committedZerglings is living plus planned, not living alone. This runs on every frame that
     * the bot is rushed, so a plan that has already left the queue for an egg has to count: reading
     * living units would queue a fresh zergling every frame until the first egg hatched. The
     * planned count moves in twos because one plan hatches a pair, which is the number a target for
     * future zerglings wants. The guards that ask what the bot can fight with now read living
     * units instead.
     *
     * @param committedZerglings zerglings alive or already planned
     * @param zerglingTarget zerglings the emergency demands
     * @return true when one more zergling plan should be queued
     */
    static boolean shouldPlanEmergencyZergling(int committedZerglings, int zerglingTarget) {
        return committedZerglings < zerglingTarget;
    }

    /**
     * Whether the defence path owes a Spawning Pool: a rush reaction is active and no pool is
     * standing or planned. Every zergling and colony the rush asks for waits on a pool, and a
     * hatch-first build order that has not reached its own pool trigger may never reach it while
     * the rush forbids further expansions.
     *
     * @param rushed whether an EarlyRush or ScvRush reaction is active
     * @param canPlanPool whether no Spawning Pool is standing or already claimed by a plan
     * @return true when an emergency Spawning Pool plan should be queued
     */
    static boolean shouldPlanEmergencyPool(boolean rushed, boolean canPlanPool) {
        return rushed && canPlanPool;
    }

    /**
     * Defense reachable from every build order, including the openers and SpeedlingAllIn that
     * never plan colonies of their own.
     *
     * <p>The static defense half runs on any frame the target is unmet, not only while the bot is
     * rushed. A build order that never calls {@link #planSunkenColony(GameState)} has no other way
     * to spend a target, so gating this on {@link GameState#isEarlyRushed()} left every such build
     * with nothing at all against a push that arrives after EarlyRush stops looking. The emergency
     * priority and the rushed sunken floor still apply only while rushed, so a target the build
     * order would have reached on its own does not jump the queue ahead of the spawning pool.
     *
     * <p>The zergling half stays emergency only: it is a response to units already at our bases.
     *
     * <p>While an EarlyRush or ScvRush reaction is active and no Spawning Pool is standing or
     * planned, a pool is queued at emergency priority. A build order that already claimed its own
     * pool is left alone, and claiming one here stops the build order from queuing a second.
     *
     * <p>Returned plans carry reservations (sunken base, build tiles, planned unit counts) and
     * must be added to the production queue by the caller.
     */
    public List<Plan> planDefense(GameState gameState) {
        List<Plan> plans = new ArrayList<>(planStaticDefense(gameState));
        boolean rushed = gameState.isEarlyRushed() || gameState.isScvRushed();
        if (shouldPlanEmergencyPool(rushed, gameState.getTechProgression().canPlanPool())) {
            Plan poolPlan = this.planSpawningPool(gameState);
            poolPlan.setPriority(EMERGENCY_DEFENSE_PRIORITY);
            plans.add(poolPlan);
        }
        if (!gameState.isEarlyRushed()) {
            return plans;
        }
        int zerglingTarget = Math.max(this.zerglingsNeeded(gameState),
                earlyRushZerglings(gameState.knownEnemyMobileGroundCombatUnitsAtOurBases()));
        int zerglingCount = gameState.ourUnitCount(UnitType.Zerg_Zergling);
        if (shouldPlanEmergencyZergling(zerglingCount, zerglingTarget) && gameState.canPlanUnit(UnitType.Zerg_Zergling)) {
            Plan zerglingPlan = this.planUnit(gameState, UnitType.Zerg_Zergling);
            zerglingPlan.setPriority(EMERGENCY_DEFENSE_PRIORITY);
            plans.add(zerglingPlan);
        }
        return plans;
    }

    private Set<Plan> planStaticDefense(GameState gameState) {
        if (!gameState.getTechProgression().isSpawningPool()) {
            return Collections.emptySet();
        }
        boolean earlyRushed = gameState.isEarlyRushed();
        int sunkenTarget = this.requiredSunkens(gameState);
        int priority = DEFAULT_COLONY_PRIORITY;
        if (earlyRushed) {
            sunkenTarget = Math.max(sunkenTarget, earlyRushSunkens(gameState.knownEnemyMobileGroundCombatUnitsAtOurBases(),
                    gameState.visibleEnemyMobileGroundCombatUnitsAtOurBases()));
            priority = EMERGENCY_DEFENSE_PRIORITY;
        }
        if (gameState.basesNeedingSunken(sunkenTarget).isEmpty()) {
            return Collections.emptySet();
        }
        return this.planSunkenColony(gameState, priority, sunkenTarget);
    }

    /**
     * Whether the race agnostic macro continuation may run this frame.
     *
     * <p>canPlanZergling is what the army branch needs and therefore what the whole method opens
     * on. Opening the method on a merely planned Spawning Pool while the army branch still waited
     * on a finished one left the economy branches as the only reachable ones for the pool's entire
     * build time, and {@link macro.plan.PlanComparator} sorts on the enqueue frame, so the drones,
     * natural and extractor queued in that window outranked forever every zergling the same method
     * would later be allowed to create.
     *
     * @param raceUnknown whether the opponent's race is still unresolved
     * @param openerComplete whether the opener has produced everything it will produce
     * @param canPlanZergling whether a zergling plan is legal now, per {@link GameState#canPlanUnit}
     * @param earlyRushed whether the emergency owns production instead
     * @return true when the continuation should plan this frame
     */
    static boolean unknownRaceMacroOpen(boolean raceUnknown, boolean openerComplete, boolean canPlanZergling,
                                        boolean earlyRushed) {
        return raceUnknown && openerComplete && canPlanZergling && !earlyRushed;
    }

    /**
     * Race agnostic macro continuation for an opener that has finished its build order but cannot
     * transition, because a random opponent's race is still unknown.
     * <p>
     * Ensures defensive zerglings, a natural expansion, drones and finally gas if they were not
     * covered by the initial opener.
     * <p>
     * Every branch waits on a finished Spawning Pool, because the army branch has to: a zergling
     * plan created before the pool finishes still takes a larva and holds it in BUILDING, because
     * {@code ManagedUnit.morph} no-ops until the morph is buildable. The zergling branch runs
     * first once the method opens, so the natural and its drones are queued behind army rather
     * than ahead of it.
     */
    protected List<Plan> planUnknownRaceMacro(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        boolean raceUnknown = gameState.getOpponentRace() == Race.Unknown;
        boolean canPlanZergling = gameState.canPlanUnit(UnitType.Zerg_Zergling);
        if (!unknownRaceMacroOpen(raceUnknown, openerComplete(gameState), canPlanZergling, gameState.isEarlyRushed())) {
            return plans;
        }

        if (gameState.ourUnitCount(UnitType.Zerg_Zergling) == 0) {
            for (int i = 0; i < UNKNOWN_RACE_ZERGLING_PLANS; i++) {
                plans.add(this.planUnit(gameState, UnitType.Zerg_Zergling));
            }
            return plans;
        }

        int plannedAndCurrentBases = gameState.getPlannedHatcheries() + gameState.getBaseData().currentBaseCount();
        if (plannedAndCurrentBases < UNKNOWN_RACE_BASE_TARGET) {
            Plan hatcheryPlan = this.planNewBase(gameState);
            if (hatcheryPlan != null) {
                plans.add(hatcheryPlan);
            }
        }

        if (gameState.canPlanDrone()) {
            plans.add(this.planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (gameState.canPlanExtractor()) {
            plans.add(this.planExtractor(gameState));
        }

        return plans;
    }

    /**
     * Plans a hatchery that claims a base. Returns null while any rule deletes a queued hatchery
     * this frame, so a cancelled expansion is not re-created on the following frame.
     */
    protected Plan planNewBase(GameState gameState) {
        if (!gameState.mayQueueExpansionHatchery()) {
            return null;
        }

        Base base = gameState.reserveBase();
        if (base == null) {
            return null;
        }

        gameState.addPlannedHatchery(1);
        return new BuildingPlan(UnitType.Zerg_Hatchery, gameState.getGameTime().getFrames(), base.getLocation());
    }

    protected Plan planLair(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedLair(true);
        return new BuildingPlan(UnitType.Zerg_Lair, 3);
    }

    protected Plan planQueensNest(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedQueensNest(true);
        Plan plan = new BuildingPlan(UnitType.Zerg_Queens_Nest, 4);
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Queens_Nest);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planHive(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedHive(true);
        return new BuildingPlan(UnitType.Zerg_Hive, 3);
    }

    protected Plan planUltraliskCavern(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedUltraliskCavern(true);
        Plan plan = new BuildingPlan(UnitType.Zerg_Ultralisk_Cavern, 4);
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Ultralisk_Cavern);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planDefilerMound(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedDefilerMound(true);
        Plan plan = new BuildingPlan(UnitType.Zerg_Defiler_Mound, 4);
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Defiler_Mound);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    /**
     * Priority for this build order's Spawning Pool plan. Defaults to the enqueue frame, which sorts the
     * pool behind every building already queued. Openers that open on the pool override it with a small
     * constant so nothing queued after the pool outranks it.
     *
     * @param enqueueFrame the frame the plan is created on
     * @return the plan priority
     */
    protected int poolPriority(int enqueueFrame) {
        return enqueueFrame;
    }

    protected Plan planSpawningPool(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedSpawningPool(true);
        Plan plan = new BuildingPlan(UnitType.Zerg_Spawning_Pool, poolPriority(gameState.getGameTime().getFrames()));
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Spawning_Pool);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planSpire(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedSpire(true);
        Plan plan = new BuildingPlan(UnitType.Zerg_Spire, 4);
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Spire);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planExtractor(GameState gameState) {
        BaseData baseData = gameState.getBaseData();
        Plan plan = new BuildingPlan(UnitType.Zerg_Extractor, gameState.getGameTime().getFrames());
        Unit geyser = baseData.reserveExtractor();
        plan.setBuildPosition(baseData.getGeyserPosition(geyser));
        return plan;
    }

    /**
     * Returns a set of Creep and Sunken Colony plans.
     */
    protected Set<Plan> planSunkenColony(GameState gameState) {
        return planSunkenColony(gameState, DEFAULT_COLONY_PRIORITY, this.requiredSunkens(gameState));
    }

    /**
     * Sorts a base for static defense, main first and every other base by its tile.
     * <p>
     * The set of bases needing a sunken is a HashSet, so taking the first element made the choice
     * depend on hash order: with the main and the natural both short of the target, whichever
     * hashed first took the whole deficit and the main could be left under its target. The main
     * wins whenever it is a candidate at all, because it is only eligible while a reaction has
     * decided the main needs defending. The tile term is a tie break that only has to be stable,
     * not meaningful; which of two equally eligible expansions is served first is placement
     * policy, which this does not try to decide.
     *
     * @param isMainBase whether the base is our main
     * @param tileX base tile x
     * @param tileY base tile y
     * @return a rank that sorts ascending, main first
     */
    static long sunkenBaseRank(boolean isMainBase, int tileX, int tileY) {
        return homeFirstBaseRank(isMainBase, tileX, tileY);
    }

    /**
     * Ranks a base of ours, main first and every other base by its tile.
     *
     * <p>The tile term is a tie break that only has to be stable, not meaningful. A set of our
     * bases is a HashSet, so without it the choice between two equally eligible bases follows
     * hash order and can differ from frame to frame.
     *
     * @param isMainBase whether the base is our main
     * @param tileX base tile x
     * @param tileY base tile y
     * @return a rank that sorts ascending, main first
     */
    static long homeFirstBaseRank(boolean isMainBase, int tileX, int tileY) {
        long tieBreak = tileX * TILE_RANK_STRIDE + tileY;
        return isMainBase ? tieBreak : NON_MAIN_BASE_RANK + tieBreak;
    }

    /**
     * Returns Creep and Sunken Colony plan pairs, up to the deficit one base can be short of.
     *
     * <p>Each pair reserves its base and its build tiles before the next base is chosen, so the
     * loop sees the bases it has already served and stops on the caps a single pair would also
     * have stopped on: the per base target and the five colony ceiling inside
     * {@link GameState#basesNeedingSunken(int)}. Filling in one call matters because a pair per
     * call answers a three sunken deficit one pair per frame, which spreads the reservations, the
     * builder assignments and the mineral draw across frames the threat is already using. The
     * bound is the target itself, which keeps a target of one behaving exactly as a single pair
     * did and stops a multi base fan out from pulling the whole mining line off minerals at once.
     *
     * <p>A base with no placeable creep tile is skipped rather than ending the call. The ranking
     * puts the main first whenever it is eligible, so ending on the first null location would let
     * a main that is short of target and out of tiles starve every other base for the rest of the
     * game.
     */
    protected Set<Plan> planSunkenColony(GameState gameState, int priority, int target) {
        Set<Plan> plans = new HashSet<>();
        BaseData baseData = gameState.getBaseData();
        BuildingPlanner buildingPlanner = gameState.getBuildingPlanner();
        Base mainBase = baseData.getMainBase();
        Set<Base> unplaceable = new HashSet<>();
        int planned = 0;
        while (planned < target) {
            Optional<Base> eligibleBase = nextSunkenBase(gameState.basesNeedingSunken(target), unplaceable,
                    base -> sunkenBaseRank(base == mainBase, base.getLocation().getX(), base.getLocation().getY()));
            if (!eligibleBase.isPresent()) {
                break;
            }
            TilePosition location = buildingPlanner.getLocationForCreepColony(eligibleBase.get(), gameState.getOpponentRace());
            if (location == null) {
                unplaceable.add(eligibleBase.get());
                continue;
            }
            baseData.reserveSunkenColony(eligibleBase.get());
            buildingPlanner.reservePlannedBuildingTiles(location, UnitType.Zerg_Creep_Colony);
            Plan creepColonyPlan = new BuildingPlan(UnitType.Zerg_Creep_Colony, priority, location);
            Plan sunkenColonyPlan = new BuildingPlan(UnitType.Zerg_Sunken_Colony, priority, location);
            sunkenColonyPlan.setPairedColonyPlan(creepColonyPlan);
            sunkenColonyPlan.setReservedColonyBase(eligibleBase.get());
            plans.add(creepColonyPlan);
            plans.add(sunkenColonyPlan);
            planned++;
        }
        return plans;
    }

    /**
     * The next base to serve: the lowest ranked candidate that this call has not already found
     * unplaceable.
     * <p>
     * Separate from the reservation loop so the skip rule can be tested. bwem Base is final with a
     * package private constructor and cannot be built in a test, so the seam is generic over the
     * candidate type and the loop supplies the rank.
     *
     * @param candidates bases short of their sunken target this pass
     * @param skipped bases this call has already found no creep tile for
     * @param rank the ordering to serve candidates in, ascending
     * @param <T> the candidate type
     * @return the base to serve, or empty when every candidate is skipped
     */
    static <T> Optional<T> nextSunkenBase(Set<T> candidates, Set<T> skipped, ToLongFunction<T> rank) {
        return candidates.stream()
                .filter(candidate -> !skipped.contains(candidate))
                .min(Comparator.comparingLong(rank));
    }

    /**
     * Returns a set of Creep and Spore Colony plans, or the Evolution Chamber the Spore needs
     * before either of them can be queued.
     *
     * <p>Nothing is reserved until the prerequisite stands. A Spore queued without an Evolution
     * Chamber is cancelled by the production sweep on the frame it is queued, while its paired
     * Creep Colony survives and is built, so the pair is formed only once the morph can follow.
     */
    protected Set<Plan> planSporeColony(GameState gameState) {
        Set<Plan> plans = new HashSet<>();
        BaseData baseData = gameState.getBaseData();
        TechProgression techProgression = gameState.getTechProgression();
        BuildingPlanner buildingPlanner = gameState.getBuildingPlanner();
        Optional<Base> eligibleBase = gameState.basesNeedingSpore(this.requiredSpores(gameState)).stream().findFirst();
        if (!eligibleBase.isPresent()) {
            return plans;
        }
        SporeStep step = sporeStep(techProgression);
        if (step == SporeStep.EVOLUTION_CHAMBER) {
            plans.add(planEvolutionChamber(gameState));
        }
        if (step != SporeStep.SPORE_COLONY) {
            return plans;
        }
        TilePosition location = buildingPlanner.getLocationForSporeColony(eligibleBase.get());
        if (location == null) {
            return plans;
        }
        baseData.reserveSporeColony(eligibleBase.get());
        buildingPlanner.reservePlannedBuildingTiles(location, UnitType.Zerg_Creep_Colony);
        Plan creepColonyPlan = new BuildingPlan(UnitType.Zerg_Creep_Colony, 5, location);
        Plan sporeColonyPlan = new BuildingPlan(UnitType.Zerg_Spore_Colony, 5, location);
        sporeColonyPlan.setPairedColonyPlan(creepColonyPlan);
        sporeColonyPlan.setReservedColonyBase(eligibleBase.get());
        plans.add(creepColonyPlan);
        plans.add(sporeColonyPlan);
        return plans;
    }

    /**
     * What a base short of its Spore target gets this frame.
     */
    enum SporeStep {
        EVOLUTION_CHAMBER,
        SPORE_COLONY,
        WAIT
    }

    /**
     * The step towards a Spore Colony the bot's tech allows this frame.
     *
     * <p>The Creep and Spore pair is formed only once an Evolution Chamber stands. Until then the
     * chamber is planned, or waited on while it is planned or while the Spawning Pool it needs is
     * missing, and no Spore is requested.
     *
     * @param techProgression the bot's tech state
     * @return the Spore pair, the chamber it needs, or nothing
     */
    static SporeStep sporeStep(TechProgression techProgression) {
        if (techProgression.canPlanSporeColony()) {
            return SporeStep.SPORE_COLONY;
        }
        if (shouldPlanSporePrerequisite(techProgression)) {
            return SporeStep.EVOLUTION_CHAMBER;
        }
        return SporeStep.WAIT;
    }

    /**
     * Whether the Evolution Chamber a Spore Colony needs should be planned now.
     *
     * <p>Chambers already planned count alongside those standing, so a chamber on the way is waited
     * on rather than duplicated, and the Spawning Pool the chamber itself needs is left to
     * {@link TechProgression#canPlanEvolutionChamber()}. A build order that plans its own chamber
     * for upgrades reaches the same gate, so the anti-air route never adds a second one.
     *
     * @param techProgression the bot's tech state
     * @return true when the Spore's missing prerequisite should be queued
     */
    static boolean shouldPlanSporePrerequisite(TechProgression techProgression) {
        return techProgression.evolutionChambers() == 0 && techProgression.canPlanEvolutionChamber();
    }

    /**
     * Buys a mineral-only unit with minerals the build's own unit targets have stopped spending.
     * Sits at the end of the plan chain, so it only fires once every branch above it declined:
     * the build gets what it asked for first and the leftovers become zerglings rather than bank.
     *
     * <p>Returns null when there is nothing to buy, which is the caller's signal to add nothing.
     */
    protected Plan planMineralSurplusUnit(GameState gameState) {
        ResourceCount resourceCount = gameState.getResourceCount();
        boolean larvaAvailable = resourceCount.canScheduleLarva(
                gameState.numLarva(), gameState.larvaAssignedToPlans());
        boolean poolComplete = gameState.getTechProgression().isSpawningPool();
        int queuedPlans = gameState.queuedUnitPlanCount(MINERAL_SURPLUS_UNIT);
        if (!shouldSpendMineralSurplus(resourceCount.availableMinerals(), larvaAvailable, poolComplete, queuedPlans)) {
            return null;
        }
        return this.planUnit(gameState, MINERAL_SURPLUS_UNIT);
    }

    /**
     * Unreserved minerals are the surplus signal, not {@link GameState#isFloatingMinerals()},
     * whose bar is scaled to hatchery count because it exists to decide expansions. A build that
     * has met every unit target it knows how to ask for is not short of hatcheries, it is short of
     * things to spend on, and the same reasoning is written out at
     * {@link SpeedlingAllIn#shouldPlanHatchery}.
     *
     * <p>The bound is on plans already waiting in the queue rather than on army size, so the
     * surplus drains at a fixed rate instead of stacking a plan every frame it stays true.
     *
     * @param availableMinerals minerals mined and not reserved by a queued plan
     * @param larvaAvailable whether a larva is free to morph, from
     *     {@link ResourceCount#canScheduleLarva}, which is the same authority the scheduler uses
     * @param poolComplete a Spawning Pool has finished, so the unit can be morphed
     * @param queuedPlans plans for the surplus unit already waiting in the queue
     */
    static boolean shouldSpendMineralSurplus(int availableMinerals, boolean larvaAvailable,
                                             boolean poolComplete, int queuedPlans) {
        return poolComplete
                && larvaAvailable
                && queuedPlans < MAX_QUEUED_SURPLUS_PLANS
                && availableMinerals >= MINERAL_SURPLUS;
    }

    protected Plan planUnit(GameState gameState, UnitType unitType) {
        return planUnit(gameState, unitType, gameState.getGameTime().getFrames());
    }

    protected Plan planUnit(GameState gameState, UnitType unitType, int priority) {
        UnitTypeCount count = gameState.getUnitTypeCount();
        count.planUnit(unitType);
        if (unitType == UnitType.Zerg_Drone) {
            gameState.addPlannedWorker(1);
        }
        if (unitType == UnitType.Zerg_Overlord) {
            int plannedSupply = gameState.getResourceCount().getPlannedSupply();
            gameState.getResourceCount().setPlannedSupply(plannedSupply + 16);
        }
        return new UnitPlan(unitType, priority);
    }

    /**
     * Plans a unit that a tech building unlocked, ahead of the backlog. Queues at most one plan
     * per call, and none while the production sweep would cancel it the same frame.
     */
    protected List<Plan> planAdvancedUnit(GameState gameState, UnitType unitType) {
        return planAdvancedUnit(unitType, gameState.getTechProgression(), gameState.numGatherers(),
                gameState.queuedUnitPlanCount(unitType), gameState.getUnitTypeCount());
    }

    /**
     * The advanced unit plan for a type, counted into the planned units it is charged to.
     *
     * @param unitType the unit a tech building unlocked
     * @param techProgression the bot's tech state
     * @param gatherers workers gathering, for the eligibility gate
     * @param queuedPlans plans of this type still waiting in the production queue
     * @param count the unit counts the plan is charged to
     * @return one plan at {@link UnitPlan#ADVANCED_UNIT_PRIORITY}, or none
     */
    protected static List<Plan> planAdvancedUnit(UnitType unitType, TechProgression techProgression, int gatherers,
                                                 int queuedPlans, UnitTypeCount count) {
        List<Plan> plans = new ArrayList<>();
        if (queuedPlans > 0) {
            return plans;
        }
        if (!canPlanAdvancedUnit(unitType, techProgression, gatherers)) {
            return plans;
        }
        count.planUnit(unitType);
        plans.add(new UnitPlan(unitType, UnitPlan.ADVANCED_UNIT_PRIORITY));
        return plans;
    }

    /**
     * True when a unit a tech building unlocks passes the gate the production sweep applies, so a
     * plan created now survives the frame. Reports the failing term when it does not.
     */
    protected boolean canPlanAdvancedUnit(GameState gameState, UnitType unitType) {
        return canPlanAdvancedUnit(unitType, gameState.getTechProgression(), gameState.numGatherers());
    }

    protected static boolean canPlanAdvancedUnit(UnitType unitType, TechProgression techProgression, int gatherers) {
        PlanBlocker blocker = AdvancedUnitEligibility.blocker(unitType, techProgression, gatherers);
        if (blocker == PlanBlocker.NONE) {
            return true;
        }
        PlanEvents.withheld(unitType, blocker);
        return false;
    }

    protected Plan planUpgrade(GameState gameState, UpgradeType upgradeType) {
        TechProgression techProgression = gameState.getTechProgression();
        int priority = gameState.getGameTime().getFrames();
        switch (upgradeType) {
            case Metabolic_Boost:
                techProgression.setPlannedMetabolicBoost(true);
                break;
            case Muscular_Augments:
                techProgression.setPlannedMuscularAugments(true);
                break;
            case Grooved_Spines:
                techProgression.setPlannedGroovedSpines(true);
                break;
            case Zerg_Melee_Attacks:
                techProgression.setPlannedMeleeUpgrades(true);
                break;
            case Zerg_Missile_Attacks:
                techProgression.setPlannedRangedUpgrades(true);
                break;
            case Zerg_Flyer_Attacks:
                techProgression.setPlannedFlyerAttack(true);
                break;
            case Zerg_Flyer_Carapace:
                techProgression.setPlannedFlyerDefense(true);
                break;
            case Zerg_Carapace:
                techProgression.setPlannedCarapaceUpgrades(true);
                break;
            case Pneumatized_Carapace:
                techProgression.setPlannedOverlordSpeed(true);
                priority = 100;
                break;
            case Chitinous_Plating:
                techProgression.setPlannedChitinousPlating(true);
                break;
            case Anabolic_Synthesis:
                techProgression.setPlannedAnabolicSynthesis(true);
                break;
            case Adrenal_Glands:
                techProgression.setPlannedAdrenalGlands(true);
                break;
            default:
                break;
        }

        int currentLevel = getCurrentUpgradeLevel(techProgression, upgradeType);
        return new UpgradePlan(upgradeType, priority, currentLevel);
    }

    private int getCurrentUpgradeLevel(TechProgression tp, UpgradeType type) {
        switch (type) {
            case Zerg_Carapace: return tp.getCarapaceUpgrades();
            case Zerg_Melee_Attacks: return tp.getMeleeUpgrades();
            case Zerg_Missile_Attacks: return tp.getRangedUpgrades();
            case Zerg_Flyer_Attacks: return tp.getFlyerAttack();
            case Zerg_Flyer_Carapace: return tp.getFlyerDefense();
            default: return 0;
        }
    }

    protected Plan planHydraliskDen(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedDen(true);
        Plan plan = new BuildingPlan(UnitType.Zerg_Hydralisk_Den, gameState.getGameTime().getFrames());
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Hydralisk_Den);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planEvolutionChamber(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedEvolutionChambers(techProgression.getPlannedEvolutionChambers() + 1);
        Plan plan = new BuildingPlan(UnitType.Zerg_Evolution_Chamber, gameState.getGameTime().getFrames());
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Evolution_Chamber);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planTech(GameState gameState, TechType techType) {
        TechProgression techProgression = gameState.getTechProgression();
        int priority = gameState.getGameTime().getFrames();
        
        if (techType == TechType.Lurker_Aspect) {
            techProgression.setPlannedLurker(true);
            priority = 100;
        }

        if (techType == TechType.Consume) {
            techProgression.setPlannedConsume(true);
        }

        if (techType == TechType.Plague) {
            techProgression.setPlannedPlague(true);
        }

        return new TechPlan(techType, priority, true);
    }

    /**
     * The shared larva-bound macro hatchery step, run for every build order by {@link #plan}.
     *
     * <p>Reads the gate inputs off the game state, hands {@link LarvaBoundMacroHatchery#evaluate}
     * the build's own tech condition, and reports to plan telemetry the gate the request stopped
     * on. A gate that opens every term but still yields no plan is reported as
     * {@link LarvaBoundMacroHatchery.Gate#PLACEMENT_UNAVAILABLE}, so TRIGGER is written only on a
     * frame a macro hatchery is actually enqueued.
     *
     * <p>The plan is placed at the main first. The main is behind the army and already on creep,
     * and placing there keeps the request off the expansion path, so it never reserves a base and
     * is never held by the expansion backoff.
     *
     * @param gameState current game state
     * @return the macro hatchery plan, or null when the request is withheld or cannot be placed
     */
    private Plan larvaBoundMacroHatchery(GameState gameState) {
        ResourceCount resourceCount = gameState.getResourceCount();
        boolean techReady = macroHatcheryTechReady(gameState.getTechProgression());
        int hatcheries = gameState.hatcheryCount();
        int outstanding = gameState.inFlightHatcheryPlans(true) + gameState.hatcheriesUnderConstruction(true);
        LarvaBoundMacroHatchery.Gate gate = LarvaBoundMacroHatchery.evaluate(techReady, gameState.numLarva(),
                hatcheries, resourceCount.availableMinerals(), resourceCount.availableGas(),
                gameState.knownEnemyMobileGroundCombatUnitsAtOurBases(), outstanding);

        if (gate != LarvaBoundMacroHatchery.Gate.TRIGGER) {
            PlanEvents.macroHatcheryGate(gate, techReady, hatcheries, outstanding);
            return null;
        }

        Plan plan = planMacroHatcheryAt(gameState, gameState.getBaseData().getMainBase());
        PlanEvents.macroHatcheryGate(plan == null
                ? LarvaBoundMacroHatchery.Gate.PLACEMENT_UNAVAILABLE : gate, techReady, hatcheries, outstanding);
        return plan;
    }

    /**
     * Whether the shared larva-bound macro hatchery step runs after the build's own plans.
     *
     * <p>An opener is out: it hands over before any tech condition can hold, so the request could
     * only stop on its tech gate and would write gate rows naming a build that can never answer
     * them. A build that already asked for a hatchery this frame is out too, whatever it wanted
     * the hatchery for, so a build that owns its own hatchery policy keeps it and the shared step
     * does not buy a second hatchery on the frame the build bought one.
     *
     * @param isOpener whether the active build order is an opener
     * @param plans the plans the build order produced this frame
     * @return true when the shared step should run
     */
    static boolean runsLarvaBoundMacroHatchery(boolean isOpener, List<Plan> plans) {
        return !isOpener && !containsHatcheryPlan(plans);
    }

    /**
     * Whether the build already asked for a hatchery this frame.
     *
     * @param plans the plans the build order produced this frame
     * @return true when one of them is a hatchery
     */
    static boolean containsHatcheryPlan(List<Plan> plans) {
        for (Plan plan : plans) {
            if (plan.getType() == PlanType.BUILDING && plan.getPlannedUnit() == UnitType.Zerg_Hatchery) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a Hive-branch structure is requested this frame.
     *
     * <p>Reads the unreserved gas bank off the game state, tracks how long it has held the branch
     * bar, hands {@link GasBoundHiveTech#evaluate} the caller's tech term, and reports the gate it
     * stopped on to plan telemetry. The extractor count travels with the row as a diagnostic only:
     * no gate reads it.
     *
     * <p>The hold is sampled at each evaluation rather than at each frame, because a build order
     * plans only while its queue has room. A bank observed below the bar restarts the hold, as does
     * a gap between evaluations longer than the window itself. Reading the hold twice in one frame
     * neither advances nor restarts it, so both guarded structures evaluate off one hold.
     *
     * @param gameState current game state
     * @param structure the structure the gate guards, carried on the telemetry row
     * @param techAvailable whether the structure can be planned at all: its prerequisite is
     *     finished and no copy is planned or standing
     * @return true when every gate is open
     */
    protected boolean wantGasBoundHiveTech(GameState gameState, UnitType structure, boolean techAvailable) {
        int frame = gameState.getGameTime().getFrames();
        int availableGas = gameState.getResourceCount().availableGas();
        hiveTechGasSinceFrame = GasBoundHiveTech.holdSince(hiveTechGasSinceFrame, hiveTechGasEvaluatedFrame,
                frame, availableGas);
        hiveTechGasEvaluatedFrame = frame;
        GasBoundHiveTech.Gate gate = GasBoundHiveTech.evaluate(techAvailable, availableGas,
                GasBoundHiveTech.framesHeld(hiveTechGasSinceFrame, frame));
        PlanEvents.hiveTechGate(gate, structure, availableGas, GasBoundHiveTech.BRANCH_GAS,
                gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Extractor));
        return gate == GasBoundHiveTech.Gate.TRIGGER;
    }

    protected Plan planMacroHatchery(GameState gameState) {
        BuildingPlanner buildingPlanner = gameState.getBuildingPlanner();
        BaseData baseData = gameState.getBaseData();
        return macroHatcheryAt(gameState, buildingPlanner.targetBaseForMacroHatchery(gameState.getOpponentRace(), baseData));
    }

    /**
     * Plans a macro hatchery at an explicitly chosen base rather than the race-keyed rotation.
     */
    protected Plan planMacroHatcheryAt(GameState gameState, Base base) {
        return macroHatcheryAt(gameState, base);
    }

    /**
     * The macro hatchery plan for the first base with room for one.
     *
     * <p>The preferred base is tried first and every other base we hold after it. A base whose
     * buildable ring is full used to drop the plan and leave nothing behind; the caller sees null
     * only once no base we hold can take a hatchery.
     *
     * @param gameState current game state
     * @param preferredBase the base the request wants the hatchery at, which may be null
     * @return the plan, or null when the enqueue is barred or no base has room
     */
    private Plan macroHatcheryAt(GameState gameState, Base preferredBase) {
        if (!gameState.mayQueueMacroHatchery()) {
            return null;
        }

        BuildingPlanner buildingPlanner = gameState.getBuildingPlanner();
        for (Base base : macroHatcheryBaseOrder(preferredBase, gameState.getBaseData())) {
            TilePosition location = buildingPlanner.getLocationForMacroHatchery(base);
            if (location == null) {
                continue;
            }

            buildingPlanner.reservePlannedBuildingTiles(location, UnitType.Zerg_Hatchery);
            gameState.addPlannedHatchery(1);
            return macroHatcheryPlan(gameState.getGameTime().getFrames(), location);
        }

        return null;
    }

    /**
     * The bases a macro hatchery request tries, in order: the base it asked for, then the main,
     * then every other base we hold.
     *
     * <p>The main comes first among the fallbacks because it is the base furthest behind the
     * army and the one a builder reaches without crossing the map. The rest are ranked by tile so
     * the order does not depend on the hash order of the base set, which would make a request that
     * fails at one base land somewhere different on the next frame.
     *
     * @param preferredBase the base the request wants the hatchery at, which may be null
     * @param baseData the bases we hold
     * @return the bases to try, in order, without repeats
     */
    private static List<Base> macroHatcheryBaseOrder(Base preferredBase, BaseData baseData) {
        Base mainBase = baseData.getMainBase();
        List<Base> fallbacks = new ArrayList<>();
        for (Base base : baseData.getMyBases()) {
            if (base != null && base != preferredBase) {
                fallbacks.add(base);
            }
        }
        fallbacks.sort(Comparator.comparingLong(base -> homeFirstBaseRank(base == mainBase,
                base.getLocation().getX(), base.getLocation().getY())));

        List<Base> ordered = new ArrayList<>();
        if (preferredBase != null) {
            ordered.add(preferredBase);
        }
        ordered.addAll(fallbacks);
        return ordered;
    }

    /**
     * The plan every macro hatchery request creates: a Hatchery marked as a macro hatchery at the
     * tile the request chose.
     */
    public static Plan macroHatcheryPlan(int frame, TilePosition location) {
        Plan plan = new BuildingPlan(UnitType.Zerg_Hatchery, frame, location);
        plan.setMacroHatchery(true);
        return plan;
    }

    /**
     * Reports if we must add a hatchery to keep parity with the enemy resource depot count,
     * whatever the opponent's race.
     *
     * <p>Our total is {@link GameState#hatcheryCount()} plus the hatcheries already queued.
     *
     * @return true when the enemy has more resource depots, our hatcheries are not excess, and no
     *     reaction is holding expansions out of the queue
     */
    protected boolean behindOnHatchery(GameState gameState) {
        int ourTotal = gameState.hatcheryCount() + Math.max(0, gameState.getPlannedHatcheries());

        int enemyTotal = gameState.enemyResourceDepotCount();

        return HatcheryCapacity.isBehind(ourTotal, enemyTotal, gameState.hasExcessHatchery(),
                gameState.isEarlyRushed());
    }

    protected boolean behindOnBases(GameState gameState) {
        BaseData baseData = gameState.getBaseData();

        if (!baseData.hasNaturalExpansion()) {
            return false;
        }
        
        return isBehindOnBases(baseData.currentAndReservedCount(), gameState.enemyResourceDepotCount());
    }

    /**
     * Base parity as the transition builds read it: an opponent level with us on depots already
     * counts as ahead, so the request fires at parity rather than only once we trail.
     *
     * @param ourBaseCount bases we hold or have reserved for a queued hatchery
     * @param enemyDepots living enemy resource depots we have observed
     * @return true when we should take another base
     */
    static boolean isBehindOnBases(int ourBaseCount, int enemyDepots) {
        return ourBaseCount <= enemyDepots;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuildOrder that = (BuildOrder) o;
        return Objects.equals(this.name, that.name);
    }

    /**
     * Hashes the class by name rather than by the {@link Class} object, whose hash is a JVM
     * identity hash and differs on every process start. Build orders live in hash sets, so the
     * hash has to be stable across runs for their iteration order to be.
     */
    @Override
    public int hashCode() {
        return Objects.hash(getClass().getName(), name);
    }
}
