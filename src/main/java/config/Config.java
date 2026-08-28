package config;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Configuration settings loaded from a .env file, falling back to JVM system properties.
 * Includes feature flags and override settings for testing and development.
 */
public final class Config {

    public static boolean learnDefensiveSunk = false;
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
