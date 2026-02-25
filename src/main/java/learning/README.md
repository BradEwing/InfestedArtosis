# Learning Architecture

Adaptive build order selection using a Discounted Upper Confidence Bound (D-UCB) multi-armed bandit algorithm. The system learns which openers and build orders perform best against each opponent, with prioritization toward recent results.

## Architecture

```
                         Bot.java
                      onStart / onEnd
                            |
                    +-------v--------+
                    | LearningManager |-----> Decisions (opener)
                    +-------+--------+       passed to GameState
                            |
              +-------------+-------------+
              |                           |
     determineOpener()          determineBuildOrder()
              |                           |
              +-------------+-------------+
                            |
                  +---------v----------+
                  | WeightedUCBCalculator |
                  +---------+----------+
                            |
              +-------------+-------------+
              |                           |
   Map-Specific Records         Opponent-Specific Records
   (MapAwareRecord)                  (Record)
              |                           |
              +-------------+-------------+
                            |
                   +--------v--------+
                   | OpponentRecord   |
                   +-----------------+
                   | openerRecord     |  Map<String, Record>
                   | buildOrderRecord |  Map<String, Record>
                   | mapSpecific*     |  Map<String, MapAwareRecord>
                   +-----------------+
                            |
                     CSV Persistence
                  (read/ and write/ dirs)
```

### Execution Flow

1. **Game start** - `Bot.onStart()` creates `LearningManager`, which reads opponent CSV history and calls `determineOpener()` to select the opening strategy via D-UCB.
2. **Mid-game transition** - `ProductionManager` calls `determineBuildOrder(candidates)` when the opener signals a transition, selecting the mid-game strategy via D-UCB.
3. **Game end** - `Bot.onEnd()` calls `LearningManager.onEnd(isWinner)`, which updates win/loss records for both the opener and active build order, then appends a new row to the CSV file.

## D-UCB Algorithm

Each strategy is an arm in a multi-armed bandit. 

### Index Formula

For a strategy with game history, the D-UCB index is:

```
index = sampleMean + explorationTerm

where:
  sampleMean    = discountedWins / discountedGames
  explorationTerm = sqrt(2 * ln(totalGames) / discountedGames)
```

- `totalGames` = total games played against this opponent (raw count, not discounted)
- `discountedWins` and `discountedGames` use exponential decay (see below)

### Exponential Decay

Games are sorted newest-first and weighted by `gamma^age`:

```
gamma = 0.95
age   = position in reverse chronological order (0 = most recent)

weight(game_i) = 0.95^i
```

| Age | Weight |
|-----|--------|
| 0 (most recent) | 1.000 |
| 1 | 0.950 |
| 2 | 0.903 |
| 5 | 0.774 |
| 10 | 0.599 |
| 20 | 0.358 |
| 50 | 0.077 |

This makes the system responsive to shifts in opponent behavior. A strategy that won 3 recent games scores higher than one that won 3 games long ago.

### Worked Example

Strategy A: 3 recent wins (ages 0, 1, 2)

```
discountedWins  = 0.95^0 + 0.95^1 + 0.95^2 = 1.0 + 0.95 + 0.9025 = 2.852
discountedGames = 2.8525 (all games are wins)
sampleMean      = 2.8525 / 2.8525 = 1.0
```

Strategy B: 3 old wins (ages 18, 19, 20) with 0 recent games

```
discountedWins  = 0.95^18 + 0.95^19 + 0.95^20 = 0.397 + 0.377 + 0.358 = 1.133
discountedGames = 1.132
sampleMean      = 1.0
```

Both have the same sample mean, but Strategy B has a lower `discountedGames` denominator in the exploration term, giving it a *larger* exploration bonus -- encouraging the system to re-test old strategies.

### Edge Cases

| Condition | Behavior |
|---|---|
| Zero total games | Return `Math.random()`  |
| Unplayed strategy | Return `sqrt(ln(totalGames)) + noise`  |
| Zero discounted games | Return `1.0` |

### Map-Aware Weighted Scoring

`WeightedUCBCalculator` blends map-specific and opponent-specific scores using a sigmoid confidence curve:

```
confidence = 1 / (1 + e^(-0.3 * (mapGames - 10)))
mapWeight  = 0.8 * confidence
score      = mapWeight * mapScore + (1 - mapWeight) * opponentScore
```

The sigmoid gradually increases trust in map-specific data as sample size grows:

| Map Games | Confidence | Map Weight |
|-----------|------------|------------|
| 0 | 0.05 | 0.04 |
| 5 | 0.18 | 0.15 |
| 10 | 0.50 | 0.40 |
| 15 | 0.82 | 0.65 |
| 20+ | ~1.0 | ~0.80 |

Falls back to opponent-only data if no map-specific history exists, then to pure exploration if no data at all.

### Special Cases

- **CannonRush reaction**: If the last game's detected strategies include "CannonRush", the system forces the `Overpool` opener regardless of UCB scores.
- **Config overrides**: `config.openerOverride` and `config.strategyOverride` bypass UCB selection entirely for local testing.

## Learning File Structure

### Directory Layout

```
bwapi-data/
  read/               
    Akilae Tribe_Protoss.csv
    Iron bot_Terran.csv
  write/              
    Akilae Tribe_Protoss.csv
```

Files are named `{opponentName}_{opponentRace}.csv`. On first write, the system copies all rows from `read/` into the new `write/` file, then appends the current game result.

### CSV Format

**Header:**
```
timestamp,is_winner,num_starting_locations,map_name,opponent_name,opponent_race,opener,build_order,detected_strategies
```

**Fields:**

| Field | Type | Description |
|---|---|---|
| `timestamp` | long | Epoch milliseconds when the game ended |
| `is_winner` | boolean | `true` if the bot won |
| `num_starting_locations` | int | Number of starting positions on the map |
| `map_name` | string | Map filename (e.g., `(4)Polypoid_1.65.scx`) |
| `opponent_name` | string | Enemy player name |
| `opponent_race` | string | `Protoss`, `Terran`, or `Zerg` |
| `opener` | string | Opening strategy used (e.g., `Overpool`, `12Hatch`) |
| `build_order` | string | Mid-game strategy used (e.g., `3HatchMuta`). Same as opener if no transition. |
| `detected_strategies` | string | Enemy strategies detected during the game, multiple strategies are semicolon separated (e.g., `2Gate;1Base`) |

**Example rows:**
```
1761107433421,true,4,(4)Polypoid_1.65.scx,Akilae Tribe,Protoss,Overpool,3HatchMuta,2Gate
1761282554674,false,3,(3)PowerBond_1.00.scx,Akilae Tribe,Protoss,Overpool,3HatchMuta,2Gate;1Base
```

### How Records Are Reconstructed

On startup, `LearningManager.readOpponentRecord()` parses the CSV and builds four maps in `OpponentRecord`:

1. **`openerRecord`** - `Map<String, Record>` keyed by opener name
2. **`buildOrderRecord`** - `Map<String, Record>` keyed by build order name (only for rows where build order differs from opener)
3. **`mapSpecificOpenerRecord`** - `Map<String, MapAwareRecord>` keyed by `{mapName}_{openerName}`
4. **`mapSpecificBuildOrderRecord`** - `Map<String, MapAwareRecord>` keyed by `{mapName}_{buildOrderName}`

Each row's win/loss and timestamp are added to the appropriate `Record` and `MapAwareRecord` objects, which then use the D-UCB algorithm for scoring during strategy selection.
