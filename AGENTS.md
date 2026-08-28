# AGENTS.md

This file provides guidance to coding agents working with code in this repository.

## Project Overview

Infested Artosis is a StarCraft: Brood War Zerg bot built with JBWAPI. It uses a sliding UCB multi-armed bandit algorithm for opener and unit composition selection.

## Build Commands

Always run Maven in **offline** mode with `-o`. Dependencies are already available in the local repository, and offline mode avoids unnecessary network fetches.

- `mvn -o package` — compiles, runs the unit tests, builds the jar (~5s)
- `mvn -o checkstyle:check` — matches the `Checkstyle` check required by branch protection on `main`

Both must exit 0 before opening a PR; `main` requires the `build` and `Checkstyle` CI checks to pass.
After changing `pom.xml`, verify with `mvn -o clean package` (plain `package` may report "Nothing to
compile" and prove nothing).

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
- **dotenv** - Set debug settings in local dev environment

## Code Style
- No comments within a function body.
- For varargs utility methods, prefer simple single varargs `(T... items)` over `(T first, T second, T... rest)` unless explicitly asked otherwise.

## Domain Knowledge
- When referencing Brood War game mechanics (damage types, unit stats, pathfinding), do NOT guess. Ask the user or search the codebase for existing constants/enums rather than stating facts that may be wrong.

## Workflow
- When implementing from a plan or Jira ticket, always re-read the current state of files before editing. Files may have changed between planning and implementation.

## Git Conventions
- Do NOT prefix git commands with `cd <project-dir> &&` when already in the project root. Just run the git command directly.
- Stage only files related to the current change. Run `git diff --cached` before committing to verify no unrelated changes leak in.

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

## Pull Request Descriptions

The PR body defaults to **empty**. Across 2022-2026, ~80% of hand-written PRs on this repo have no body at all, including changes over 1000 lines. The title carries the change; the body carries only what the title could not.

- **Write a body only to name changes the title does not cover.** The house form is a bare list of the branch's secondary commit subjects, one per line:
  ```
  refactor: move GameState updates in ProductionManager to InformationManager
  feat: simplify gas rebalance to be all workers
  ```
  Plain `-` bullets are the only accepted alternative. One coherent change means no body.
- **Hard cap: 50 words and 5 lines.** The longest hand-written body in the last two years is 51 words. Length does not scale with diff size - the two largest PRs of 2026 (1054 and 838 lines) both shipped with empty bodies.
- **No markdown headings, ever.** Zero appear in roughly 370 hand-written PRs. If the content seems to need a heading, it is too long.
- **State WHAT changed, present tense, with the code as the subject.** `Replace naive time-based SCV count heuristic with tile intersection check.` Never "This PR...", never first person, never future tense.
- **Clip the WHY onto the same clause or drop it** - "...to prevent units getting stuck". Never a separate rationale paragraph.
- **Rationale, investigation notes, rejected alternatives, deferred defects, and follow-up work belong in the Jira ticket**, not the PR. The body describes the merge, not the work that produced it.
- No bold, tables, code fences, or emoji. Class and method names are written bare - ObservedUnitTracker, not `ObservedUnitTracker`.

### Never put in a PR body

- **`claude.ai` session URLs**, or any AI attribution or co-author trailer.
- **Replay filenames (`*.rep`), batch-run IDs, log directories, or local paths.** None are tracked in this repo, so they resolve for nobody. Replays are discarded over time and the bot changes continuously, so a replay reference is stale on arrival.
- `## Summary`, `## Test plan`, `## Validation status`, `## Assumptions`, `## Known review findings`, or severity labels (CRITICAL/HIGH/MEDIUM/LOW/NITPICK).
- Checkboxes. CI reports `build` and `Checkstyle` status; restating it is noise.
- Corrections to, or arguments with, the Jira ticket. Comment on the ticket instead.
- Citations of other bots (McRave, PurpleWave, BananaBrain) as justification.
- Stacked-PR banners. GitHub already shows the base branch.
- A restatement of the title. If that is all there is, the body is empty.
