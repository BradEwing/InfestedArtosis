"""Report on a batch launched by run.py: W/L/crash summary plus learning-file slice.

Usage:
  py scripts/batch/report.py [--run latest|<runid>] [--tail 10] [--archive losses|all|none]

Pure read; safe to run while a batch is still in progress.
"""

import argparse
import shutil
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import batchlib as bl


def parse_args():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--run", default="latest", help="run id or 'latest' (default)")
    p.add_argument("--tail", type=int, default=10, help="learning rows to print per opponent")
    p.add_argument("--archive", choices=["losses", "all", "none"], default="none",
                   help="copy replays/logs/learning files into batches/<runid>/")
    return p.parse_args()


def collect(manifest):
    results = []
    in_progress = not manifest.get("finished_at")
    for game in manifest.get("games", []):
        outcome, game_time, row = bl.classify(game)
        if outcome == "NO_RESULT" and in_progress:
            outcome = "RUNNING"
        results.append({**game, "outcome": outcome, "game_time": game_time, "row": row})
    return results


def tally(results, key):
    groups = defaultdict(Counter)
    for r in results:
        groups[r[key]][r["outcome"]] += 1
    return groups


def winrate(c):
    conclusive = c["WIN"] + c["LOSS"]
    return c["WIN"] / conclusive if conclusive else 0.0


def print_table(title, groups):
    print(f"\n{title}")
    rows = sorted(groups.items(), key=lambda kv: winrate(kv[1]))
    overall = Counter()
    for _, c in rows:
        overall.update(c)
    rows.append(("Overall", overall))
    width = 1 + max(len(name) for name, _ in rows)
    print(f"  {'':<{width}} {'WR':>4}  {'W':>3} {'L':>3} {'D':>3} {'C':>3} {'T':>3} {'S':>3} {'?':>3} {'~':>3}  total")
    for name, c in rows:
        total = sum(c.values())
        print(f"  {name + ':':<{width}} {winrate(c):>4.0%}  {c['WIN']:>3} {c['LOSS']:>3} {c['DRAW']:>3} "
              f"{c['CRASH']:>3} {c['TIMEOUT']:>3} {c['STALL']:>3} {c['NO_RESULT']:>3} {c['RUNNING']:>3}  {total}")
    print("  (W win, L loss, D draw, C crash, T realtime timeout, S stalled in lobby (never started), "
          "? launched but no result.json, ~ running)")


def print_field_table(label, rows, field, sep=None):
    stats = defaultdict(Counter)
    for r in rows:
        outcome = "WIN" if r.get("is_winner", "").lower() == "true" else "LOSS"
        values = (r.get(field) or "").split(sep) if sep else [(r.get(field) or "")]
        for value in values:
            value = value.strip()
            if value:
                stats[value][outcome] += 1
    if not stats:
        return
    print(f"    {label}")
    items = sorted(stats.items(), key=lambda kv: -(kv[1]["WIN"] + kv[1]["LOSS"]))
    for name, c in items:
        total = c["WIN"] + c["LOSS"]
        print(f"      {name:<24} {c['WIN']:>3}W {c['LOSS']:>3}L  {c['WIN'] / total:>5.0%}")


def print_bot_errors(results):
    """Report games the JVM did not survive.

    These are invisible in result.json: StarCraft exits normally, so the game scores as an ordinary loss even
    though the bot stopped playing partway through and never wrote a learning row.

    A failure the bot catches and survives is not reported here. The guard suppresses it silently, because the
    bot must not write to stdout or stderr, so nothing distinguishes a degraded game from a clean one.
    """
    crashed = [r for r in results if r["outcome"] == "CRASH"]
    if not crashed:
        return
    print(f"\nBot failures (logs_0/bot.log): {len(crashed)} crashed")
    for r in crashed:
        print(f"  {r['game_name']:<18} vs {r['opponent']:<20} JVM died mid-game (scored {r['outcome']})")


def print_learning(manifest, results, tail):
    print("\nLearning slice (per-game write_0 rows, race-immune)")
    for opp in manifest["opponents"]:
        rows = [r["row"] for r in results if r["opponent"] == opp and r["row"]]
        played = sum(1 for r in results if r["opponent"] == opp and r["outcome"] in bl.CONCLUSIVE)
        print(f"\n  vs {opp} ({bl.opponent_race(opp)}): {len(rows)} learning rows from {played} conclusive games")
        if not rows:
            continue
        print_field_table("Opener", rows, "opener")
        print_field_table("Build order (chain-split: a game counts once per build order played)", rows, "build_order", ";")
        strategies = Counter()
        for r in rows:
            for s in (r.get("detected_strategies") or "").split(";"):
                if s.strip():
                    strategies[s.strip()] += 1
        if strategies:
            print("    Detected strategies")
            for s, n in strategies.most_common():
                print(f"      {s:<24} {n:>3}")
        frames = [int(r["frame_count"]) for r in rows if r.get("frame_count", "").isdigit()]
        if frames:
            avg = sum(frames) / len(frames)
            print(f"    Avg frame_count: {avg:.0f} (~{avg / 24 / 60:.1f} min at 24fps)")
        print(f"    Last {min(tail, len(rows))} rows")
        for r in rows[-tail:]:
            print(f"      {'W' if r.get('is_winner', '').lower() == 'true' else 'L'} "
                  f"{r.get('map_name', ''):<24} {r.get('opener', ''):<18} {r.get('build_order', ''):<18} "
                  f"{r.get('detected_strategies', '')}")
        read_rows = bl.read_dir_row_count(opp)
        files = ", ".join(p.name for p in bl.read_dir_csvs(opp)) or "(none)"
        before = manifest.get("read_rows_before", {}).get(opp)
        if manifest.get("frozen"):
            print(f"    read/ [{files}]: {read_rows} rows (frozen mode: unchanged by this batch)")
        elif before is not None:
            landed = read_rows - before
            note = "" if landed == len(rows) else "  <-- MISMATCH: rows lost to read_overwrite race"
            print(f"    read/ [{files}]: {before} -> {read_rows} rows (+{landed}, batch produced {len(rows)}){note}")


def archive(manifest, results, mode):
    if mode == "none":
        return
    for r in results:
        if r["outcome"] in ("NO_RESULT", "RUNNING"):
            continue
        if mode == "losses" and r["outcome"] != "LOSS":
            continue
        bucket = "wins" if r["outcome"] == "WIN" else "losses" if r["outcome"] == "LOSS" else "other"
        dest = bl.BATCHES_DIR / manifest["run_id"] / bucket
        dest.mkdir(parents=True, exist_ok=True)
        gdir = bl.game_dir(r["game_name"])
        base = f"{r['game_name']}-{r['opponent']}"
        rep = gdir / "player_0.rep"
        if rep.is_file():
            shutil.copyfile(rep, dest / f"{base}.rep")
        for sub in ("write_0", "logs_0", "logs_1"):
            d = gdir / sub
            if d.is_dir():
                for f in d.iterdir():
                    if f.is_file():
                        shutil.copyfile(f, dest / f"{base}.{sub}.{f.name}")
    print(f"\nArchived {mode} to {bl.BATCHES_DIR / manifest['run_id']}")


def describe_flags(manifest):
    """The runtime flags the batch was launched with. Batches launched before run.py recorded them leave
    nothing on disk saying whether telemetry was enabled."""
    flags = manifest.get("runtime_flags")
    if flags is None:
        return "unrecorded (batch predates flag capture)"
    return ", ".join(f"{k}={v}" for k, v in flags.items()) or "none"


def report(run_id, tail=10, archive_mode="none"):
    manifest = bl.load_manifest(run_id)
    results = collect(manifest)
    mode = "frozen" if manifest.get("frozen") else "accumulate"
    if manifest.get("finished_at"):
        status = f"{manifest.get('status', 'finished').upper()} {manifest['finished_at']}"
    else:
        status = "IN PROGRESS"
    print(f"Batch {run_id} | {status}")
    print(f"  jar {manifest.get('jar_version')} ({(manifest.get('jar_sha256') or '')[:12]}) | "
          f"git {manifest.get('git_rev')} | mode {mode} | jobs {manifest.get('jobs')} | "
          f"{len(results)}/{len(manifest['opponents']) * manifest['games_per_opponent']} launched")
    print(f"  flags {describe_flags(manifest)}")
    if not results:
        print("  No games launched yet.")
        return
    print_bot_errors(results)
    print_table("By opponent", tally(results, "opponent"))
    print_table("By map", tally(results, "map"))
    print_learning(manifest, results, tail)
    archive(manifest, results, archive_mode)


def main():
    args = parse_args()
    report(bl.resolve_run_id(args.run), tail=args.tail, archive_mode=args.archive)


if __name__ == "__main__":
    main()
