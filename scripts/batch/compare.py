"""Compare two batch arms: win-rate delta with a confidence interval on a matched pool.

Usage:
  py scripts/batch/compare.py <run-a> <run-b> [--label-a A] [--label-b B] [--confidence 0.95]

Each arm takes one run id or a comma-separated list of them. Only conclusive games count, and only
those played on an opponent and map pair both arms actually played: a map or an opponent one arm
never faced would otherwise move the delta on its own.

Outcomes come from the same classifier the collector uses, so this runs on a raw batch without
collecting first. Pure read.
"""

import argparse
import math
import sys
from collections import Counter, defaultdict
from pathlib import Path
from statistics import NormalDist

sys.path.insert(0, str(Path(__file__).resolve().parent))

import batchlib as bl


def parse_args():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("arm_a", help="run id, or comma-separated run ids, for the baseline arm")
    p.add_argument("arm_b", help="run id, or comma-separated run ids, for the treatment arm")
    p.add_argument("--label-a", help="name for the baseline arm (default its run ids)")
    p.add_argument("--label-b", help="name for the treatment arm (default its run ids)")
    p.add_argument("--confidence", type=float, default=0.95, help="confidence level (default 0.95)")
    return p.parse_args()


def load_arm(spec):
    run_ids = [bl.resolve_run_id(part.strip()) for part in spec.split(",") if part.strip()]
    if not run_ids:
        raise SystemExit(f"No run ids in '{spec}'")
    games = []
    manifests = []
    for run_id in run_ids:
        manifest = bl.load_manifest(run_id)
        manifests.append(manifest)
        for game in manifest.get("games", []):
            games.append({
                "run_id": run_id,
                "opponent": game["opponent"],
                "map": game["map"],
                "outcome": bl.classify(game)[0],
            })
    return {"run_ids": run_ids, "manifests": manifests, "games": games}


def conclusive(games):
    return [g for g in games if g["outcome"] in bl.CONCLUSIVE]


def matched_pool(games_a, games_b):
    cells_a = {(g["opponent"], g["map"]) for g in games_a}
    cells_b = {(g["opponent"], g["map"]) for g in games_b}
    return cells_a & cells_b


def tally(games, pool):
    counts = Counter()
    for g in games:
        if (g["opponent"], g["map"]) in pool:
            counts[g["outcome"]] += 1
    return counts["WIN"], counts["WIN"] + counts["LOSS"]


def wilson(wins, total, z):
    """Wilson score interval. The normal approximation is unusable at the win rates and sample sizes a
    single opponent produces."""
    if not total:
        return 0.0, 0.0
    p = wins / total
    denominator = 1 + z * z / total
    centre = p + z * z / (2 * total)
    margin = z * math.sqrt(p * (1 - p) / total + z * z / (4 * total * total))
    return (centre - margin) / denominator, (centre + margin) / denominator


def delta_interval(wins_a, total_a, wins_b, total_b, z):
    """Two-proportion difference with a normal interval, reported as treatment minus baseline."""
    if not total_a or not total_b:
        return None, None, None
    pa = wins_a / total_a
    pb = wins_b / total_b
    se = math.sqrt(pa * (1 - pa) / total_a + pb * (1 - pb) / total_b)
    half = z * se
    return pb - pa, pb - pa - half, pb - pa + half


def rate(wins, total):
    return wins / total if total else 0.0


def points(value):
    """A difference of two win rates is percentage points, not a percentage of anything."""
    return f"{value * 100:+.1f} pp"


def describe_arm(label, arm, pool):
    jars = sorted({m.get("jar_version") for m in arm["manifests"]})
    revs = sorted({m.get("git_rev") for m in arm["manifests"]})
    flags = sorted({m.get("java_opts") or "" for m in arm["manifests"]})
    total = len(arm["games"])
    played = len(conclusive(arm["games"]))
    in_pool = sum(1 for g in conclusive(arm["games"]) if (g["opponent"], g["map"]) in pool)
    print(f"\n{label}")
    print(f"  runs {', '.join(arm['run_ids'])}")
    print(f"  jar {', '.join(str(j) for j in jars)} | git {', '.join(str(r) for r in revs)}")
    print(f"  flags {', '.join(f or 'none recorded' for f in flags)}")
    print(f"  {total} launched, {played} conclusive, {in_pool} inside the matched pool")


def print_pool(label_a, label_b, arm_a, arm_b, pool, z):
    wins_a, total_a = tally(arm_a["games"], pool)
    wins_b, total_b = tally(arm_b["games"], pool)
    low_a, high_a = wilson(wins_a, total_a, z)
    low_b, high_b = wilson(wins_b, total_b, z)
    width = 2 + max(len(label_a), len(label_b))
    print(f"\nMatched pool: {len(pool)} opponent/map cells")
    print(f"  {'':<{width}} {'W':>4} {'L':>4} {'WR':>6}   interval")
    print(f"  {label_a + ':':<{width}} {wins_a:>4} {total_a - wins_a:>4} {rate(wins_a, total_a):>6.1%}   "
          f"[{low_a:.1%}, {high_a:.1%}]")
    print(f"  {label_b + ':':<{width}} {wins_b:>4} {total_b - wins_b:>4} {rate(wins_b, total_b):>6.1%}   "
          f"[{low_b:.1%}, {high_b:.1%}]")

    delta, low, high = delta_interval(wins_a, total_a, wins_b, total_b, z)
    if delta is None:
        print("\n  No conclusive games in the matched pool; no delta.")
        return
    verdict = "excludes 0" if low > 0 or high < 0 else "includes 0, not distinguishable"
    print(f"\n  Delta ({label_b} - {label_a}): {points(delta)}  "
          f"interval [{points(low)}, {points(high)}]  {verdict}")


def print_by_opponent(label_a, label_b, arm_a, arm_b, pool, z):
    opponents = sorted({opponent for opponent, _ in pool})
    if len(opponents) < 2:
        return
    print("\nBy opponent")
    width = 2 + max(len(o) for o in opponents)
    print(f"  {'':<{width}} {label_a:>16} {label_b:>16}   delta")
    for opponent in opponents:
        cells = {cell for cell in pool if cell[0] == opponent}
        wins_a, total_a = tally(arm_a["games"], cells)
        wins_b, total_b = tally(arm_b["games"], cells)
        delta, low, high = delta_interval(wins_a, total_a, wins_b, total_b, z)
        summary = "--" if delta is None else f"{points(delta)} [{points(low)}, {points(high)}]"
        print(f"  {opponent + ':':<{width}} {f'{rate(wins_a, total_a):.0%} ({wins_a}/{total_a})':>16} "
              f"{f'{rate(wins_b, total_b):.0%} ({wins_b}/{total_b})':>16}   {summary}")


def print_dropped(arm_a, arm_b, pool):
    dropped = defaultdict(Counter)
    for name, arm in (("a", arm_a), ("b", arm_b)):
        for g in arm["games"]:
            if g["outcome"] not in bl.CONCLUSIVE:
                dropped[name][g["outcome"]] += 1
            elif (g["opponent"], g["map"]) not in pool:
                dropped[name]["UNMATCHED"] += 1
    if not dropped["a"] and not dropped["b"]:
        return
    print("\nDropped")
    for reason in sorted(set(dropped["a"]) | set(dropped["b"])):
        print(f"  {reason:<12} {dropped['a'][reason]:>5} {dropped['b'][reason]:>5}")


def compare(spec_a, spec_b, label_a, label_b, confidence):
    arm_a = load_arm(spec_a)
    arm_b = load_arm(spec_b)
    z = NormalDist().inv_cdf(1 - (1 - confidence) / 2)
    pool = matched_pool(conclusive(arm_a["games"]), conclusive(arm_b["games"]))

    print(f"Arm comparison at {confidence:.0%} confidence")
    describe_arm(label_a, arm_a, pool)
    describe_arm(label_b, arm_b, pool)
    print_pool(label_a, label_b, arm_a, arm_b, pool, z)
    print_by_opponent(label_a, label_b, arm_a, arm_b, pool, z)
    print_dropped(arm_a, arm_b, pool)


def main():
    args = parse_args()
    if not 0 < args.confidence < 1:
        raise SystemExit("--confidence must be between 0 and 1")
    compare(args.arm_a, args.arm_b, args.label_a or args.arm_a, args.label_b or args.arm_b, args.confidence)


if __name__ == "__main__":
    main()
