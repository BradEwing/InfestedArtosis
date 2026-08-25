---
name: batch-games
description: Run N local sc-docker games of Infested Artosis against one or more opponents, then summarize win/loss results and the learning-file slice for those opponents.
user-invocable: true
argument-hint: <opponent> [<opponent>...] [-n 100] [--frozen --jobs N]
---

# Batch Games

Thin wrapper over `scripts/batch/run.py` and `scripts/batch/report.py`. Never re-implement the orchestration; the scripts are the source of truth and must work without Claude.

## 1. Resolve arguments

Parse `$ARGUMENTS` into opponent names and flags. Opponent names must match a directory under `%APPDATA%\scbw\bots` (list it with `ls "$APPDATA/scbw/bots"` if unsure; quote names with spaces). Defaults: `-n 100`, `--jobs 1`, accumulate mode.

Explain the mode briefly if the user did not pick one:
- default (accumulate): `--read_overwrite` on, one concurrent game per opponent, the UCB bandit learns across the batch. Use for "which opener wins vs X".
- `--frozen --jobs N`: learning snapshot fixed, games run in parallel. Use for fast A/B regression sweeps of a code change.

`--jobs > 1` vs a single opponent without `--frozen` is rejected by the script (learning-file race).

## 2. Jar check

The script aborts if `target/*-jar-with-dependencies.jar` differs from the deployed jar under `%APPDATA%\scbw\bots\Infested Artosis\AI`, if the jar version differs from `pom.xml`, or if more than one jar is deployed (scbw refuses to launch). Do NOT run `mvn`; ask the user to run `! mvn package` if `target/` is stale. Pass `--deploy-jar` to replace the deployed jar with the built one. If the user explicitly wants the deployed jar as-is, pass `--skip-jar-check`.

Opponent names are SSCAIT names, sometimes the author's (UAlbertaBot = `Dave Churchill`). Random-race opponents produce learning files per race seen (e.g. `Dave Churchill_Unknown.csv`); the report handles this.

## 3. Launch

Run with Bash, `run_in_background: true` (a 100-game batch takes hours):

```
py scripts/batch/run.py "<opp>" ["<opp2>"...] -n <N> [--frozen --jobs <J>]
```

Tell the user the run id and that `py scripts/batch/report.py --run latest` can be run at any time mid-batch. Do not poll; wait for the completion notification.

## 4. Report and interpret

When the run finishes (it prints the report itself), or on request, run `py scripts/batch/report.py --run latest --tail 15` and interpret:
- Win rate per opponent and per map; call out maps with notably worse results.
- Crash / timeout / stall / no-result counts. `S` (stall) means the joiner never entered the host's lobby (no `frames.csv`) and `?` (no result.json) means a docker or launch problem — neither is bot quality. Any stall is a harness/environment bug; stop and investigate rather than reading the W/L. Crashes: check `%APPDATA%\scbw\batches\<runid>.log` and `--archive losses` for `bot.log`.
- Opener and build-order W/L: relate names to classes under `src/main/java/strategy/buildorder/`. Note whether the bandit converged on one opener or is still exploring.
- Detected strategies distribution: flag anything unexpected for the opponent's race (detectors live in `src/main/java/info/tracking/`).
- The `read/... rows` line: a MISMATCH means learning rows were lost to the `--read_overwrite` race; report it.

Finish with 2-3 concrete follow-ups (e.g. a build order to investigate, a replay to watch from `--archive losses`).
