package config;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Configuration settings loaded from a .env file, falling back to JVM system properties.
 * Includes feature flags and override settings for testing and development.
 */
public final class Config {

    public static boolean learnDefensiveSunk = false;
    /**
     * Whether an air squad that flaps between FIGHT and RETREAT may leave its retreat lock and re-entry hold to harass
     * or raid an exposed group. Off unless IA_AIR_FLAP_ESCAPE is set to true.
     */
    public static boolean airFlapEscape = false;
    /**
     * Whether the periodic base checks and the search for the unseen enemy main run. On unless IA_BASE_CHECKS is set
     * to false.
     */
    public static boolean baseChecks = true;
    /**
     * Whether a Lurker refuses to burrow inside known enemy fire it cannot answer, and a containing squad keeps its
     * points out of live hit marks. On unless IA_LURKER_FIRE_AWARE is set to false.
     */
    public static boolean lurkerFireAware = true;
    /**
     * Whether an opponent with no history starts from the per-race learning prior bundled in the jar. Off unless
     * IA_RACE_PRIOR is set to true.
     */
    public static boolean racePrior = false;
    /**
     * Whether Drones are added while an enemy Bunker stance stands. On unless IA_BUNKER_ECON is set to false.
     */
    public static boolean bunkerEcon = true;
    /**
     * Whether a ground squad's fight/retreat decision uses a band between the retreat and engage thresholds: an
     * engaged squad stays in FIGHT until its ratio falls below the retreat threshold, its fight lock holds a RETREAT
     * read above that threshold and renews despite lost supply there, and a squad that turns back out of a retreat
     * commits to FIGHT for a window. On unless IA_FIGHT_HYSTERESIS is set to false.
     */
    public static boolean fightHysteresis = true;
    /**
     * Whether a ling-heavy ground squad needs more supply to move out until a ling upgrade finishes, and a ZvZ ling
     * squad without Metabolic Boost holds near home against an enemy with, or plausibly with, it. On unless
     * IA_SPEED_PARITY_GATE is set to false.
     */
    public static boolean speedParityGate = true;
    /**
     * Whether SpeedlingAllIn answers a Protoss opponent with the Protoss sunken and zergling targets, a larva-bound
     * hatchery, and a drone floor suspended while enemy ground units are in our bases. On unless
     * IA_SPEEDLING_P_REACTIONS is set to false.
     */
    public static boolean speedlingPReactions = true;
    /**
     * Whether a build that cuts gas after Metabolic Boost keeps its drones off gas while no queued plan costs gas. On
     * unless IA_SPEEDLING_GAS_CUT is set to false.
     */
    public static boolean speedlingGasCut = true;
    /**
     * Whether the combat sim prices a missing ling or hydra speed upgrade only against faster enemies, prices enemy
     * lings and hydras with no upgrade down, and lowers the retreat threshold of a ground squad that cannot escape.
     * On unless IA_RELATIVE_SPEED is set to false.
     */
    public static boolean relativeSpeed = true;
    /**
     * Whether neighbouring ground squads share one fight/retreat decision: a neighbour's fight lock holds a FIGHT
     * squad, and a pair that still holds opposite statuses after the decision step turns to one status. On unless
     * IA_SQUAD_SHARED_VERDICT is set to false.
     */
    public static boolean squadSharedVerdict = true;
    /**
     * Whether a ground squad gathers at a standoff point outside the enemy army's reach before it engages, and a
     * reinforcement rallies to the rear of the friendly squad it is joining. On unless IA_STAGING is set to false.
     */
    public static boolean staging = true;
    public boolean enabledAutoObserver = false;
    public String strategyOverride;
    public String openerOverride;
    
    // Debug drawing flags
    // HUD and general info
    public boolean debugHud = false;
    public boolean debugUnitCount = false;
    
    // Map and pathfinding
    public boolean debugGameMap = false;
    public boolean debugBasePaths = false;
    public boolean debugAccessibleWalkPositions = false;
    public boolean debugBlockingMinerals = false;
    public boolean debugMainBaseTiles = false;
    public boolean debugOverlordPerches = false;
    
    // Bases and buildings
    public boolean debugBases = false;
    public boolean debugBaseCreepTiles = false;
    public boolean debugBaseChoke = false;
    public boolean debugLocationForTechBuilding = false;
    public boolean debugReserveTiles = false;
    public boolean debugNextCreepColonyLocation = false;
    public boolean debugNextSporeColonyLocation = false;
    public boolean debugMineralBoundingBox = false;
    public boolean debugGeyserBoundingBox = false;
    public boolean debugMacroHatcheryLocation = false;
    
    // Combat and units
    public boolean debugEnemyTargets = false;
    public boolean debugSquads = false;
    public boolean debugManagedUnits = false;
    public boolean debugStaticDefenseCoverage = false;
    public boolean debugPsiStorms = false;
    public boolean debugContainment = false;
    public boolean debugCombatSim = false;
    
    // Production and planning
    public boolean debugProductionQueue = false;
    public boolean debugInProgressQueue = false;
    public boolean debugScheduledPlannedItems = false;
    public boolean debugResourceReservations = false;

    // Telemetry
    public boolean logPlanEvents = false;
    public boolean telemetryCombat = false;

    public Config() {

        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        this.enabledAutoObserver = Boolean.parseBoolean(setting(dotenv, "IA_ENABLE_AUTO_OBSERVER"));
        this.strategyOverride = setting(dotenv, "IA_STRATEGY_OVERRIDE");
        this.openerOverride = setting(dotenv, "IA_OPENER_OVERRIDE");
        this.debugHud = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_HUD"));
        this.debugUnitCount = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_UNIT_COUNT"));
        this.debugGameMap = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_GAME_MAP"));
        this.debugBasePaths = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_BASE_PATHS"));
        this.debugAccessibleWalkPositions = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_ACCESSIBLE_WALK_POSITIONS"));
        this.debugBlockingMinerals = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_BLOCKING_MINERALS"));
        this.debugMainBaseTiles = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_MAIN_BASE_TILES"));
        this.debugOverlordPerches = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_OVERLORD_PERCHES"));
        this.debugBases = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_BASES"));
        this.debugBaseCreepTiles = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_BASE_CREEP_TILES"));
        this.debugBaseChoke = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_BASE_CHOKE"));
        this.debugLocationForTechBuilding = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_LOCATION_FOR_TECH_BUILDING"));
        this.debugReserveTiles = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_RESERVE_TILES"));
        this.debugNextCreepColonyLocation = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_NEXT_CREEP_COLONY_LOCATION"));
        this.debugNextSporeColonyLocation = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_NEXT_SPORE_COLONY_LOCATION"));
        this.debugMineralBoundingBox = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_MINERAL_BOUNDING_BOX"));
        this.debugGeyserBoundingBox = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_GEYSER_BOUNDING_BOX"));
        this.debugMacroHatcheryLocation = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_MACRO_HATCHERY_LOCATION"));
        this.debugEnemyTargets = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_ENEMY_TARGETS"));
        this.debugSquads = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_SQUADS"));
        this.debugManagedUnits = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_MANAGED_UNITS"));
        this.debugStaticDefenseCoverage = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_STATIC_DEFENSE_COVERAGE"));
        this.debugPsiStorms = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_PSI_STORMS"));
        this.debugContainment = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_CONTAINMENT"));
        this.debugCombatSim = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_COMBAT_SIM"));
        this.debugProductionQueue = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_PRODUCTION_QUEUE"));
        this.debugInProgressQueue = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_IN_PROGRESS_QUEUE"));
        this.debugScheduledPlannedItems = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_SCHEDULED_PLANNED_ITEMS"));
        this.debugResourceReservations = Boolean.parseBoolean(setting(dotenv, "IA_DEBUG_RESOURCE_RESERVATIONS"));
        this.logPlanEvents = Boolean.parseBoolean(setting(dotenv, "IA_LOG_PLAN_EVENTS"));
        this.telemetryCombat = Boolean.parseBoolean(setting(dotenv, "IA_TELEMETRY_COMBAT"));
        String airFlapEscapeSetting = setting(dotenv, "IA_AIR_FLAP_ESCAPE");
        airFlapEscape = Boolean.parseBoolean(airFlapEscapeSetting);
        baseChecks = enabledUnlessFalse(setting(dotenv, "IA_BASE_CHECKS"));
        lurkerFireAware = enabledUnlessFalse(setting(dotenv, "IA_LURKER_FIRE_AWARE"));
        racePrior = Boolean.parseBoolean(setting(dotenv, "IA_RACE_PRIOR"));
        bunkerEcon = enabledUnlessFalse(setting(dotenv, "IA_BUNKER_ECON"));
        fightHysteresis = enabledUnlessFalse(setting(dotenv, "IA_FIGHT_HYSTERESIS"));
        speedParityGate = enabledUnlessFalse(setting(dotenv, "IA_SPEED_PARITY_GATE"));
        speedlingPReactions = enabledUnlessFalse(setting(dotenv, "IA_SPEEDLING_P_REACTIONS"));
        speedlingGasCut = enabledUnlessFalse(setting(dotenv, "IA_SPEEDLING_GAS_CUT"));
        relativeSpeed = enabledUnlessFalse(setting(dotenv, "IA_RELATIVE_SPEED"));
        squadSharedVerdict = enabledUnlessFalse(setting(dotenv, "IA_SQUAD_SHARED_VERDICT"));
        staging = enabledUnlessFalse(setting(dotenv, "IA_STAGING"));
    }

    /**
     * Parses a default-on switch: on when the setting is absent or anything other than "false" (case-insensitive).
     */
    static boolean enabledUnlessFalse(String setting) {
        return !"false".equalsIgnoreCase(setting);
    }

    /**
     * Reads a setting from .env, falling back to a JVM system property. Dotenv does not consult system
     * properties itself, and under sc-docker the .env file is unreachable: -D flags are the only way in.
     */
    private static String setting(Dotenv dotenv, String key) {
        String value = dotenv.get(key);
        return value != null ? value : System.getProperty(key);
    }
}
