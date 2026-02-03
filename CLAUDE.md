# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Infested Artosis is a StarCraft: Brood War Zerg bot built with JBWAPI. It uses a sliding UCB multi-armed bandit algorithm for opener and unit composition selection.

## Build Commands

```bash
# Build (requires Java 1.8 and JAVA_HOME set)
mvn package

# Run
java -jar target/InfestedArtosis-{version}-jar-with-dependencies.jar

# Run tests
mvn test
```

## Architecture

### Core Loop

`Bot.java` is the entry point exposing event handlers. 
The `onFrame()` method executes each game tick.

### Central State

`GameState` (`info/GameState.java`) is the central data store shared by all managers. Contains unit tracking, resource counts, build orders, tech progression, base data, and enemy intelligence.

### Manager System

- **InformationManager** - Enemy tracking via `ObservedUnitTracker`, tech state, scout targets, game map
- **ProductionManager** - Queues units/buildings/upgrades, validates prerequisites, manages resources
- **PlanManager** - Assigns larva to unit morphs, drones to building plans
- **UnitManager** - Central hub delegating to WorkerManager, SquadManager, ScoutManager, BuildingManager
- **SquadManager** - Organizes units into combat squads, uses combat simulation to determine behavior.

### Unit Management

Units are wrapped in `ManagedUnit` subclasses (`unit/managed/`) with type-specific implementations (Drone, Zergling, Mutalisk, etc.). 
Each unit has a `UnitRole` (GATHER, SCOUT, FIGHT, BUILD, MORPH, DEFEND, IDLE, LARVA).

### Strategy System

Build orders extend `BuildOrder` abstract class (`strategy/buildorder/`):
- `opener/` - Early game (9PoolSpeed, 12Hatch, 12Pool, etc.)
- `protoss/`, `terran/`, `zerg/` - Matchup-specific strategies

`LearningManager` uses sliding UCB algorithm to select strategies based on opponent history.

### Plan System

Plans (`macro/plan/`) progress through states: PLANNED -> SCHEDULE -> BUILDING -> MORPHING -> COMPLETE. 
Types: BuildingPlan, UnitPlan, UpgradePlan, TechPlan.

### Key Dependencies

- **JBWAPI** - Java Brood War API bindings
- **BWEM** - Map analysis
- **Lombok** - Reduces boilerplate (@Getter, @Setter, etc.)
- **dotenv** - Set debug settings in local dev enviornment
