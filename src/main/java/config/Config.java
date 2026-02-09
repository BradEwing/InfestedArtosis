package config;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Configuration settings loaded from environment variables via .env file.
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
    
    // Bases and buildings
    public boolean debugBases = false;
    public boolean debugBaseCreepTiles = false;
    public boolean debugBaseChoke = false;
    public boolean debugLocationForTechBuilding = false;
    public boolean debugReserveTiles = false;
    public boolean debugNextCreepColonyLocation = false;
    public boolean debugMineralBoundingBox = false;
    public boolean debugGeyserBoundingBox = false;
    public boolean debugMacroHatcheryLocation = false;
    
    // Combat and units
    public boolean debugEnemyTargets = false;
    public boolean debugSquads = false;
    public boolean debugManagedUnits = false;
    public boolean debugStaticDefenseCoverage = false;
    public boolean debugPsiStorms = false;
    
    // Production and planning
    public boolean debugProductionQueue = false;
    public boolean debugInProgressQueue = false;
    public boolean debugScheduledPlannedItems = false;

    public Config() {

        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        this.enabledAutoObserver = Boolean.parseBoolean(dotenv.get("IA_ENABLE_AUTO_OBSERVER"));
        this.strategyOverride = dotenv.get("IA_STRATEGY_OVERRIDE");
        this.openerOverride = dotenv.get("IA_OPENER_OVERRIDE");
        
        // Load debug drawing flags
        // HUD and general info
        this.debugHud = Boolean.parseBoolean(dotenv.get("IA_DEBUG_HUD"));
        this.debugUnitCount = Boolean.parseBoolean(dotenv.get("IA_DEBUG_UNIT_COUNT"));
        
        // Map and pathfinding
        this.debugGameMap = Boolean.parseBoolean(dotenv.get("IA_DEBUG_GAME_MAP"));
        this.debugBasePaths = Boolean.parseBoolean(dotenv.get("IA_DEBUG_BASE_PATHS"));
        this.debugAccessibleWalkPositions = Boolean.parseBoolean(dotenv.get("IA_DEBUG_ACCESSIBLE_WALK_POSITIONS"));
        this.debugBlockingMinerals = Boolean.parseBoolean(dotenv.get("IA_DEBUG_BLOCKING_MINERALS"));
        
        // Bases and buildings
        this.debugBases = Boolean.parseBoolean(dotenv.get("IA_DEBUG_BASES"));
        this.debugBaseCreepTiles = Boolean.parseBoolean(dotenv.get("IA_DEBUG_BASE_CREEP_TILES"));
        this.debugBaseChoke = Boolean.parseBoolean(dotenv.get("IA_DEBUG_BASE_CHOKE"));
        this.debugLocationForTechBuilding = Boolean.parseBoolean(dotenv.get("IA_DEBUG_LOCATION_FOR_TECH_BUILDING"));
        this.debugReserveTiles = Boolean.parseBoolean(dotenv.get("IA_DEBUG_RESERVE_TILES"));
        this.debugNextCreepColonyLocation = Boolean.parseBoolean(dotenv.get("IA_DEBUG_NEXT_CREEP_COLONY_LOCATION"));
        this.debugMineralBoundingBox = Boolean.parseBoolean(dotenv.get("IA_DEBUG_MINERAL_BOUNDING_BOX"));
        this.debugGeyserBoundingBox = Boolean.parseBoolean(dotenv.get("IA_DEBUG_GEYSER_BOUNDING_BOX"));
        this.debugMacroHatcheryLocation = Boolean.parseBoolean(dotenv.get("IA_DEBUG_MACRO_HATCHERY_LOCATION"));
        
        // Combat and units
        this.debugEnemyTargets = Boolean.parseBoolean(dotenv.get("IA_DEBUG_ENEMY_TARGETS"));
        this.debugSquads = Boolean.parseBoolean(dotenv.get("IA_DEBUG_SQUADS"));
        this.debugManagedUnits = Boolean.parseBoolean(dotenv.get("IA_DEBUG_MANAGED_UNITS"));
        this.debugStaticDefenseCoverage = Boolean.parseBoolean(dotenv.get("IA_DEBUG_STATIC_DEFENSE_COVERAGE"));
        this.debugPsiStorms = Boolean.parseBoolean(dotenv.get("IA_DEBUG_PSI_STORMS"));
        
        // Production and planning
        this.debugProductionQueue = Boolean.parseBoolean(dotenv.get("IA_DEBUG_PRODUCTION_QUEUE"));
        this.debugInProgressQueue = Boolean.parseBoolean(dotenv.get("IA_DEBUG_IN_PROGRESS_QUEUE"));
        this.debugScheduledPlannedItems = Boolean.parseBoolean(dotenv.get("IA_DEBUG_SCHEDULED_PLANNED_ITEMS"));
    }
}
