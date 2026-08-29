"""Collect one finished batch into a single dataset: one tidy row per game, one per engagement.

Usage:
  py scripts/batch/collect.py [--run latest|<runid>] [--out <dir>]

Joins, per game: the combat telemetry, the per-game record, a plan-event summary, the resource
timeline, both players' scores, and a summary of the opponent's own unit event stream. Output lands
in the batch directory; nothing is ever written into a game directory.

Column prefixes carry the provenance, because the same quantity measured three ways is three
different numbers:
  observed_  what the bot itself saw or recorded
  attr_      attributed to one engagement by the telemetry's own clustering
  window_    a raw join of a frame window against an event stream, with no attribution
  truth_     a player's own event stream, which sees everything that player owned
  score_     the end-of-game score files, one per player
  econ_      the per-sample resource timeline
  plan_      the plan lifecycle log
  learn_     the learning row the bot wrote for its bandit

A game with no combat telemetry file is not a parse failure: that file is written at game end only,
so its absence marks a game the bot did not finish. telemetry_status says which case a row is.
"""

import argparse
import csv
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import batchlib as bl

GAMES_FILE = "dataset_games.csv"
ENGAGEMENTS_FILE = "dataset_engagements.csv"

# Mirrors CombatTelemetry.IGNORED_ENEMY_TYPES: these are morph steps, not losses.
MORPH_TYPES = ("Zerg_Larva", "Zerg_Egg", "Zerg_Lurker_Egg", "Zerg_Cocoon")

TELEMETRY_STATUSES = {
    "ok": "combat telemetry present",
    "malformed": "combat telemetry file present but unreadable",
    "missing": "flag was on but no file: game did not reach onEnd",
    "disabled": "flag was off for this batch",
    "unknown": "no file and the manifest never recorded the flag",
    "no_write_dir": "write_0 missing: game produced no bot output",
    "no_game_dir": "game directory missing",
}

GAME_COLUMNS = [
    "run_id", "game_name", "opponent", "opponent_race", "map", "outcome", "scbw_exit_code",
    "launched_at", "finished_at", "game_time_sec", "jar_version", "git_rev", "frozen",
    "telemetry_flag", "telemetry_status", "plan_flag",
    "result_winner", "result_is_crashed", "result_realtime_outed",
    "score_self_is_winner", "score_self_building", "score_self_kill", "score_self_razing", "score_self_unit",
    "score_opp_is_winner", "score_opp_building", "score_opp_kill", "score_opp_razing", "score_opp_unit",
    "econ_samples", "econ_last_frame", "econ_minerals_gathered", "econ_minerals_spent",
    "econ_gas_gathered", "econ_gas_spent", "econ_supply_used_peak", "econ_supply_total_peak",
    "econ_frame_time_avg", "econ_frame_time_max",
    "record_timestamp", "record_is_winner", "record_num_starting_locations", "record_opener",
    "record_build_order", "record_detected_strategies", "record_frame_count",
    "observed_game_id", "observed_build_order", "observed_is_winner", "observed_end_frame",
    "observed_average_fps", "observed_engagement_count", "observed_our_units_lost",
    "observed_our_supply_lost", "observed_enemy_units_killed", "observed_enemy_supply_killed",
    "observed_enemy_destroyed", "observed_sample_interval_frames", "observed_contact_radius_px",
    "observed_merge_radius_px", "observed_close_cooldown_frames",
    "truth_self_created", "truth_self_destroyed", "truth_self_events",
    "truth_opp_created", "truth_opp_destroyed", "truth_opp_types", "truth_opp_events", "truth_opp_last_frame",
    "plan_events", "plan_enqueued", "plan_transitions", "plan_completed", "plan_cancelled",
    "plan_units", "plan_buildings", "plan_tech", "plan_upgrades", "plan_queue_depth_peak", "plan_last_frame",
    "learn_opener", "learn_build_order", "learn_detected_strategies", "learn_is_winner", "learn_frame_count",
]

ENGAGEMENT_COLUMNS = [
    "run_id", "game_name", "opponent", "opponent_race", "map", "outcome",
    "observed_game_id", "engagement_id", "start_frame", "end_frame", "duration_frames",
    "anchor_x", "anchor_y",
    "our_supply_open", "our_supply_peak", "our_supply_close", "army_supply_open",
    "arrival_p25_offset", "arrival_p50_offset", "arrival_p75_offset",
    "observed_enemy_supply_peak",
    "squads_involved", "squad_status_open", "squad_status_close", "status_change_count",
    "sim_ratio_open", "sim_engage_threshold_open", "sim_verdict_open",
    "attr_our_units_lost", "attr_our_supply_lost", "attr_enemy_units_killed", "attr_enemy_supply_killed",
    "attr_kills_ambiguous", "attr_units_present", "attr_units_died", "attr_supply_present",
    "window_our_destroyed", "window_truth_enemy_destroyed",
    "record_opener", "record_build_order",
]


def parse_args():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--run", default="latest", help="run id or 'latest' (default)")
    p.add_argument("--out", help="output directory (default batches/<runid>/)")
    return p.parse_args()


def to_int(value, default=None):
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return default


def to_float(value, default=None):
    try:
        return float(str(value).strip())
    except (TypeError, ValueError):
        return default


def to_bool(value):
    if value is None:
        return None
    return str(value).strip().lower() in ("1", "true", "yes")


def telemetry_status(gdir, combat, flag):
    """Which case a game is in. The combat file is written at game end only, so a game the bot did not
    survive is marked missing rather than treated as a parse error."""
    if not gdir.is_dir():
        return "no_game_dir"
    write_dir = gdir / "write_0"
    if bl.telemetry_file(write_dir, bl.TELEMETRY_COMBAT_GAME):
        return "ok" if combat else "malformed"
    if flag is False:
        return "disabled"
    if not write_dir.is_dir():
        return "no_write_dir"
    return "missing" if flag else "unknown"


def scores(gdir, side):
    data = bl.read_json(gdir / f"logs_{side}" / "scores.json") or {}
    return {
        "is_winner": to_bool(data.get("is_winner")),
        "building": to_int(data.get("building_score")),
        "kill": to_int(data.get("kill_score")),
        "razing": to_int(data.get("razing_score")),
        "unit": to_int(data.get("unit_score")),
    }


def econ_summary(gdir):
    """Totals from the per-sample resource timeline. Every resource column is a delta for its sample
    window; the supply columns are levels."""
    totals = Counter()
    samples = 0
    last_frame = None
    supply_used_peak = 0
    supply_total_peak = 0
    frame_time_max = 0.0
    frame_time_sum = 0.0
    for row in bl.iter_csv_rows(gdir / "logs_0" / "frames.csv"):
        samples += 1
        last_frame = to_int(row.get("frame_count"), last_frame)
        for field in ("minerals_gathered", "minerals_spent", "gas_gathered", "gas_spent"):
            totals[field] += to_int(row.get(field), 0)
        supply_used_peak = max(supply_used_peak, to_int(row.get("supply_used"), 0))
        supply_total_peak = max(supply_total_peak, to_int(row.get("supply_total"), 0))
        frame_time_max = max(frame_time_max, to_float(row.get("frame_time_max"), 0.0))
        frame_time_sum += to_float(row.get("frame_time_avg"), 0.0)
    if not samples:
        return {}
    return {
        "econ_samples": samples,
        "econ_last_frame": last_frame,
        "econ_minerals_gathered": totals["minerals_gathered"],
        "econ_minerals_spent": totals["minerals_spent"],
        "econ_gas_gathered": totals["gas_gathered"],
        "econ_gas_spent": totals["gas_spent"],
        "econ_supply_used_peak": supply_used_peak,
        "econ_supply_total_peak": supply_total_peak,
        "econ_frame_time_avg": round(frame_time_sum / samples, 3),
        "econ_frame_time_max": frame_time_max,
    }


def unit_events(gdir, side):
    """Summarise one player's event stream and keep the frames of its own losses for the window joins.

    player_owned rows are that player's ground truth. The rest are only what that player could see,
    so logs_0 rows with player_owned false are what the bot observed of the enemy, never the truth.
    """
    owned_created = 0
    owned_destroyed = 0
    seen_destroyed = 0
    events = 0
    last_frame = None
    types = set()
    destroy_frames = []
    for row in bl.iter_csv_rows(gdir / f"logs_{side}" / "unit_events.csv"):
        events += 1
        last_frame = to_int(row.get("frame_number"), last_frame)
        unit_type = (row.get("unit_type") or "").strip()
        if unit_type in MORPH_TYPES:
            continue
        owned = to_bool(row.get("player_owned"))
        event_type = (row.get("event_type") or "").strip()
        if not owned:
            if event_type == "unitDestroy":
                seen_destroyed += 1
            continue
        types.add(unit_type)
        if event_type == "unitCreate":
            owned_created += 1
        elif event_type == "unitDestroy":
            owned_destroyed += 1
            frame = to_int(row.get("frame_number"))
            if frame is not None:
                destroy_frames.append(frame)
    return {
        "events": events,
        "created": owned_created,
        "destroyed": owned_destroyed,
        "seen_destroyed": seen_destroyed,
        "types": len(types),
        "last_frame": last_frame,
        "destroy_frames": destroy_frames,
    }


def plan_summary(write_dir):
    """Counts from the plan event log. The file is one row per lifecycle event and runs to hundreds of
    thousands of rows across a batch, so it is streamed and never held."""
    path = bl.telemetry_file(write_dir, bl.TELEMETRY_PLANS_GLOB)
    if path is None:
        return {}
    counts = Counter()
    queue_peak = 0
    last_frame = None
    for row in bl.iter_csv_rows(path):
        counts["events"] += 1
        last_frame = to_int(row.get("frame"), last_frame)
        queue_peak = max(queue_peak, to_int(row.get("queue_depth"), 0))
        event = (row.get("event") or "").strip()
        if event == "ENQUEUE":
            counts["enqueued"] += 1
            counts[(row.get("plan_type") or "").strip()] += 1
            continue
        counts["transitions"] += 1
        to_state = (row.get("to_state") or "").strip()
        if to_state in ("COMPLETE", "CANCELLED"):
            counts[to_state] += 1
    if not counts["events"]:
        return {}
    return {
        "plan_events": counts["events"],
        "plan_enqueued": counts["enqueued"],
        "plan_transitions": counts["transitions"],
        "plan_completed": counts["COMPLETE"],
        "plan_cancelled": counts["CANCELLED"],
        "plan_units": counts["UNIT"],
        "plan_buildings": counts["BUILDING"],
        "plan_tech": counts["TECH"],
        "plan_upgrades": counts["UPGRADE"],
        "plan_queue_depth_peak": queue_peak,
        "plan_last_frame": last_frame,
    }


def single_row(write_dir, name):
    path = bl.telemetry_file(write_dir, name)
    if path is None:
        return {}
    rows = bl.read_csv_rows(path)
    return rows[-1] if rows else {}


def engagement_unit_totals(write_dir):
    """Per-engagement participation, keyed by engagement id, from the per-unit telemetry rows."""
    totals = {}
    path = bl.telemetry_file(write_dir, bl.TELEMETRY_ENGAGEMENT_UNITS)
    if path is None:
        return totals
    for row in bl.iter_csv_rows(path):
        key = (row.get("engagement_id") or "").strip()
        entry = totals.setdefault(key, {"present": 0, "died": 0, "supply": 0})
        entry["present"] += 1
        entry["died"] += 1 if to_bool(row.get("died")) else 0
        entry["supply"] += to_int(row.get("supply"), 0)
    return totals


def count_in_window(frames, start, end):
    if start is None or end is None:
        return None
    return sum(1 for f in frames if start <= f <= end)


def game_row(manifest, game, outcome, game_time, learning_row, flag):
    gdir = bl.game_dir(game["game_name"])
    result = bl.read_json(gdir / "result.json") or {}
    self_score = scores(gdir, 0)
    opp_score = scores(gdir, 1)
    combat = single_row(gdir / "write_0", bl.TELEMETRY_COMBAT_GAME)
    record = single_row(gdir / "write_0", bl.TELEMETRY_GAME_GLOB)
    ours = unit_events(gdir, 0)
    theirs = unit_events(gdir, 1)
    learning_row = learning_row or {}

    row = {
        "run_id": manifest["run_id"],
        "game_name": game["game_name"],
        "opponent": game["opponent"],
        "opponent_race": bl.opponent_race(game["opponent"]),
        "map": game["map"],
        "outcome": outcome,
        "scbw_exit_code": game.get("scbw_exit_code"),
        "launched_at": game.get("launched_at"),
        "finished_at": game.get("finished_at"),
        "game_time_sec": round(game_time, 1) if game_time is not None else None,
        "jar_version": manifest.get("jar_version"),
        "git_rev": manifest.get("git_rev"),
        "frozen": manifest.get("frozen"),
        "telemetry_flag": flag,
        "telemetry_status": telemetry_status(gdir, combat, flag),
        "plan_flag": bl.runtime_flag(manifest, bl.PLAN_EVENT_FLAG),
        "result_winner": result.get("winner"),
        "result_is_crashed": result.get("is_crashed"),
        "result_realtime_outed": result.get("is_realtime_outed"),
        "observed_enemy_destroyed": ours["seen_destroyed"],
        "truth_self_created": ours["created"],
        "truth_self_destroyed": ours["destroyed"],
        "truth_self_events": ours["events"],
        "truth_opp_created": theirs["created"],
        "truth_opp_destroyed": theirs["destroyed"],
        "truth_opp_types": theirs["types"],
        "truth_opp_events": theirs["events"],
        "truth_opp_last_frame": theirs["last_frame"],
        "record_timestamp": record.get("timestamp"),
        "record_is_winner": record.get("is_winner"),
        "record_num_starting_locations": record.get("num_starting_locations"),
        "record_opener": record.get("opener"),
        "record_build_order": record.get("build_order"),
        "record_detected_strategies": record.get("detected_strategies"),
        "record_frame_count": record.get("frame_count"),
        "observed_game_id": combat.get("game_id"),
        "observed_build_order": combat.get("build_order"),
        "observed_is_winner": combat.get("is_winner"),
        "observed_end_frame": combat.get("end_frame"),
        "observed_average_fps": combat.get("average_fps"),
        "observed_engagement_count": combat.get("engagement_count"),
        "observed_our_units_lost": combat.get("our_units_lost"),
        "observed_our_supply_lost": combat.get("our_supply_lost"),
        "observed_enemy_units_killed": combat.get("enemy_units_killed"),
        "observed_enemy_supply_killed": combat.get("enemy_supply_killed"),
        "observed_sample_interval_frames": combat.get("sample_interval_frames"),
        "observed_contact_radius_px": combat.get("contact_radius_px"),
        "observed_merge_radius_px": combat.get("merge_radius_px"),
        "observed_close_cooldown_frames": combat.get("close_cooldown_frames"),
        "learn_opener": learning_row.get("opener"),
        "learn_build_order": learning_row.get("build_order"),
        "learn_detected_strategies": learning_row.get("detected_strategies"),
        "learn_is_winner": learning_row.get("is_winner"),
        "learn_frame_count": learning_row.get("frame_count"),
    }
    for field in ("is_winner", "building", "kill", "razing", "unit"):
        row[f"score_self_{field}"] = self_score[field]
        row[f"score_opp_{field}"] = opp_score[field]
    row.update(econ_summary(gdir))
    row.update(plan_summary(gdir / "write_0"))
    return row, ours["destroy_frames"], theirs["destroy_frames"]


def engagement_rows(manifest, game, outcome, row, our_deaths, their_deaths):
    gdir = bl.game_dir(game["game_name"])
    write_dir = gdir / "write_0"
    path = bl.telemetry_file(write_dir, bl.TELEMETRY_ENGAGEMENTS)
    if path is None:
        return []
    units = engagement_unit_totals(write_dir)

    rows = []
    for e in bl.iter_csv_rows(path):
        engagement_id = (e.get("engagement_id") or "").strip()
        if not engagement_id:
            continue
        start = to_int(e.get("start_frame"))
        end = to_int(e.get("end_frame"))
        present = units.get(engagement_id, {})
        rows.append({
            "run_id": manifest["run_id"],
            "game_name": game["game_name"],
            "opponent": game["opponent"],
            "opponent_race": row["opponent_race"],
            "map": game["map"],
            "outcome": outcome,
            "observed_game_id": e.get("game_id"),
            "engagement_id": engagement_id,
            "start_frame": start,
            "end_frame": end,
            "duration_frames": end - start if start is not None and end is not None else None,
            "anchor_x": e.get("anchor_x"),
            "anchor_y": e.get("anchor_y"),
            "our_supply_open": e.get("our_supply_open"),
            "our_supply_peak": e.get("our_supply_peak"),
            "our_supply_close": e.get("our_supply_close"),
            "army_supply_open": e.get("army_supply_open"),
            "arrival_p25_offset": e.get("arrival_p25_offset"),
            "arrival_p50_offset": e.get("arrival_p50_offset"),
            "arrival_p75_offset": e.get("arrival_p75_offset"),
            "observed_enemy_supply_peak": e.get("enemy_supply_seen_peak"),
            "squads_involved": e.get("squads_involved"),
            "squad_status_open": e.get("squad_status_open"),
            "squad_status_close": e.get("squad_status_close"),
            "status_change_count": e.get("status_change_count"),
            "sim_ratio_open": e.get("sim_ratio_open"),
            "sim_engage_threshold_open": e.get("sim_engage_threshold_open"),
            "sim_verdict_open": e.get("sim_verdict_open"),
            "attr_our_units_lost": e.get("our_units_lost"),
            "attr_our_supply_lost": e.get("our_supply_lost"),
            "attr_enemy_units_killed": e.get("enemy_units_killed"),
            "attr_enemy_supply_killed": e.get("enemy_supply_killed"),
            "attr_kills_ambiguous": e.get("kills_ambiguous"),
            "attr_units_present": present.get("present"),
            "attr_units_died": present.get("died"),
            "attr_supply_present": present.get("supply"),
            "window_our_destroyed": count_in_window(our_deaths, start, end),
            "window_truth_enemy_destroyed": count_in_window(their_deaths, start, end),
            "record_opener": row.get("record_opener"),
            "record_build_order": row.get("record_build_order"),
        })
    return rows


def collect(manifest):
    flag = bl.runtime_flag(manifest, bl.TELEMETRY_COMBAT_FLAG)
    games = []
    engagements = []
    statuses = Counter()
    for game in manifest.get("games", []):
        outcome, game_time, learning_row = bl.classify(game)
        row, our_deaths, their_deaths = game_row(manifest, game, outcome, game_time, learning_row, flag)
        statuses[row["telemetry_status"]] += 1
        games.append(row)
        engagements += engagement_rows(manifest, game, outcome, row, our_deaths, their_deaths)
    return games, engagements, statuses


def write_dataset(path, columns, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def main():
    args = parse_args()
    run_id = bl.resolve_run_id(args.run)
    manifest = bl.load_manifest(run_id)
    out_dir = Path(args.out) if args.out else bl.BATCHES_DIR / run_id
    games, engagements, statuses = collect(manifest)

    write_dataset(out_dir / GAMES_FILE, GAME_COLUMNS, games)
    write_dataset(out_dir / ENGAGEMENTS_FILE, ENGAGEMENT_COLUMNS, engagements)

    print(f"Batch {run_id}: {len(games)} game rows, {len(engagements)} engagement rows")
    print(f"  {out_dir / GAMES_FILE}")
    print(f"  {out_dir / ENGAGEMENTS_FILE}")
    print("\nTelemetry status")
    for status, count in statuses.most_common():
        print(f"  {status:<13} {count:>5}  {TELEMETRY_STATUSES.get(status, '')}")


if __name__ == "__main__":
    main()
