# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Infested Artosis is a StarCraft: Brood War Zerg bot built with JBWAPI. It uses a sliding UCB multi-armed bandit algorithm for opener and unit composition selection.

## Build Commands

Do not run `mvn` build commands because claude-code cannot pull maven dependencies. [GH Issue](https://github.com/anthropics/claude-code/issues/13372)

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
  - [Java Docs](https://javabwapi.github.io/JBWAPI/overview-summary.html)
  - [C++ Docs](https://bwapi.github.io/)
- **BWEM** - Map analysis
- **Lombok** - Reduces boilerplate (@Getter, @Setter, etc.)
- **dotenv** - Set debug settings in local dev enviornment

## Code Style
- No comments within a function body.

## Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/) with Jira issue keys:

```
<type>(IA-<number>): <lowercase description>
```

- **Types:** `feat`, `fix`, `refactor`, `chore`, `build`, `poc`
- **Scope** is the Jira ticket key (e.g., `IA-34`). Omit scope for trivial changes or dependency bumps handled by Dependabot.
- **Description** is lowercase, imperative, concise.
- Release commits use the format: `release X.XX`

Examples:
- `feat(IA-34): automate release workflow`
- `fix(IA-60): optimize opening build orders`
- `chore(IA-38): document learning architecture`
- `refactor: introduce Vec2 utility class. cleanup distance calculations`
- `release 0.59`