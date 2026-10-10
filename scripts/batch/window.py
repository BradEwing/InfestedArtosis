"""Per-opponent window comparison of two batch runs, aligned on map index.

Usage:
  py scripts/batch/window.py <run-a> <run-b> [--count-nonwins] [--games-dir DIR]

Each run contributes only the final attempt of every game index, so a replayed index counts once and the
two runs line up on the index (and so on the map) rather than on position in a filtered list. An index is
paired only when both final attempts are a WIN or LOSS: a draw, a crash, a stalemate or a NO_RESULT in either
run drops that index from both sides, so k per opponent is the number of conclusive pairs. The inc columns
count, per side, the aligned indices that are not a WIN or LOSS, and miss counts indices only one run has a
final result for. With --count-nonwins every index both runs have a final result for is
paired and a draw or non-result counts as a non-win instead. An index still in flight in either run has no final result and is left out in both
modes. Outcomes are the manifest's, with no relabelling of stalemates or stopped games. A warning line is
printed when indices name different opponents in the two runs or the runs differ in opponents or
games_per_opponent, as when a one-opponent A/B run is compared with a full batch.

Outcomes come from the manifests, so no game dirs are read; --games-dir is accepted so one command line
serves report.py and window.py, and exits non-zero when DIR is not an existing directory. The delta column is
B's win rate minus A's, in percentage points. The p value is a two-sided Fisher exact test on
the 2x2 table of wins and non-wins. Pure read.
"""

import argparse
import sys
from math import comb
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import batchlib as bl

FISHER_TOLERANCE = 1e-9


def parse_args():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("run_a", help="run id for the first arm (the control)")
    p.add_argument("run_b", help="run id for the second arm (the beta)")
    p.add_argument("--count-nonwins", action="store_true",
                   help="pair every index with a final result and count draws and non-results as non-wins")
    p.add_argument("--games-dir", help="directory holding the GAME_* dirs; must exist when given")
    return p.parse_args()


def fisher_exact(a, b, c, d):
    """Two-sided Fisher exact p for the table [[a, b], [c, d]]: the summed probability of every table with
    the same margins that is no more likely than the observed one."""
    n = a + b + c + d
    row = a + b
    col = a + c
    if n == 0:
        return 1.0

    def probability(x):
        return comb(row, x) * comb(n - row, col - x) / comb(n, col)

    observed = probability(a)
    low = max(0, col - (n - row))
    high = min(row, col)
    total = sum(probability(x) for x in range(low, high + 1) if probability(x) <= observed * (1 + FISHER_TOLERANCE))
    return min(1.0, total)


def finished_by_index(manifest):
    """The final attempt of each game index that recorded an outcome, keyed by index."""
    return {g["index"]: g for g in bl.final_attempts(manifest.get("games", [])) if g.get("outcome")}


def align(manifest_a, manifest_b):
    """Pair the final results of two runs by game index.

    Returns (pairs, missing, mismatched). pairs maps opponent to the (index, game_a, game_b) triples both
    runs have a final result for. missing maps opponent to the count of indices with a final result in only
    one run. mismatched lists the indices whose opponent differs between the runs; they are not paired."""
    finals_a = finished_by_index(manifest_a)
    finals_b = finished_by_index(manifest_b)
    pairs = {}
    missing = {}
    mismatched = []
    for index in sorted(set(finals_a) | set(finals_b)):
        game_a = finals_a.get(index)
        game_b = finals_b.get(index)
        if game_a and game_b:
            if game_a["opponent"] == game_b["opponent"]:
                pairs.setdefault(game_a["opponent"], []).append((index, game_a, game_b))
            else:
                mismatched.append(index)
        else:
            opponent = (game_a or game_b)["opponent"]
            missing[opponent] = missing.get(opponent, 0) + 1
    return pairs, missing, mismatched


def is_conclusive_pair(pair):
    _, game_a, game_b = pair
    return game_a["outcome"] in bl.CONCLUSIVE and game_b["outcome"] in bl.CONCLUSIVE


def summarize(pairs, missing, count_nonwins):
    """Counts for one opponent's aligned pairs.

    k pairs are scored: every pair when count_nonwins is set, otherwise only the conclusive ones. inc_a and
    inc_b count the aligned pairs where that side is not a WIN or LOSS, which are dropped from k unless
    count_nonwins. miss counts indices with a final result in only one run."""
    scored = pairs if count_nonwins else [p for p in pairs if is_conclusive_pair(p)]
    return {
        "k": len(scored),
        "wins_a": sum(1 for _, a, _ in scored if a["outcome"] == "WIN"),
        "wins_b": sum(1 for _, _, b in scored if b["outcome"] == "WIN"),
        "inc_a": sum(1 for _, a, _ in pairs if a["outcome"] not in bl.CONCLUSIVE),
        "inc_b": sum(1 for _, _, b in pairs if b["outcome"] not in bl.CONCLUSIVE),
        "miss": missing,
        "maps_same": sum(1 for _, a, b in scored if a.get("map") == b.get("map")),
    }


def window(manifest_a, manifest_b, count_nonwins=False):
    """Rows of (opponent, summary) sorted by opponent, followed by the ("TOTAL", summary) row."""
    pairs, missing, _ = align(manifest_a, manifest_b)
    rows = [(o, summarize(pairs.get(o, []), missing.get(o, 0), count_nonwins))
            for o in sorted(set(pairs) | set(missing))]
    total = {key: sum(s[key] for _, s in rows) for key in ("k", "wins_a", "wins_b", "inc_a", "inc_b", "miss", "maps_same")}
    return rows + [("TOTAL", total)]


def warnings(manifest_a, manifest_b):
    """Reasons the two runs may not be comparable index for index."""
    found = []
    _, _, mismatched = align(manifest_a, manifest_b)
    if mismatched:
        found.append(f"{len(mismatched)} indices name a different opponent in the two runs and are not paired "
                     f"(first {mismatched[0]})")
    if manifest_a.get("opponents") != manifest_b.get("opponents"):
        found.append("the runs list different opponents; indices only line up when the opponent order matches")
    if manifest_a.get("games_per_opponent") != manifest_b.get("games_per_opponent"):
        found.append("the runs have a different games_per_opponent, so the same index is a different game")
    return found


def p_value(summary):
    k = summary["k"]
    return fisher_exact(summary["wins_a"], k - summary["wins_a"], summary["wins_b"], k - summary["wins_b"])


def format_row(name, summary, width):
    k = summary["k"]
    wa = summary["wins_a"]
    wb = summary["wins_b"]
    ra = wa / k if k else 0.0
    rb = wb / k if k else 0.0
    return (f"  {name:<{width}} {k:>5}  {wa:>3}/{k:<4} {ra:>6.1%}  {wb:>3}/{k:<4} {rb:>6.1%}  "
            f"{(rb - ra) * 100:>+7.1f}pp  {p_value(summary):>6.3f}  {summary['maps_same']:>4}/{k:<4}  "
            f"{summary['miss']:>4}  {summary['inc_a']:>3}/{summary['inc_b']:<3}")


def render(run_a, run_b, manifest_a, manifest_b, count_nonwins=False):
    rows = window(manifest_a, manifest_b, count_nonwins)
    width = max(len(name) for name, _ in rows)
    if count_nonwins:
        scoring = "k = indices with a final result in both runs; a draw or non-result counts as a non-win"
    else:
        scoring = "k = indices where both final attempts are a WIN or LOSS; a draw or non-result drops the index"
    lines = [f"Window {run_a} (A) vs {run_b} (B): final attempt per map index"]
    lines.extend(f"warning: {w}" for w in warnings(manifest_a, manifest_b))
    lines.append(f"  {'':<{width}} {'k':>5}  {'A W/k':>8} {'':>6}  {'B W/k':>8} {'':>6}  {'delta B-A':>9}  {'p':>6}  "
                 f"{'maps':>9}  {'miss':>4}  inc A/B")
    lines.extend(format_row(name, summary, width) for name, summary in rows)
    lines.append(f"  {scoring}.")
    lines.append("  maps = same map at the index; miss = indices with a final result in only one run "
                 "(unlaunched or still in flight);")
    lines.append("  inc = aligned indices where that side is not a WIN or LOSS; Fisher exact, two-sided.")
    return "\n".join(lines)


def main():
    args = parse_args()
    if args.games_dir:
        bl.set_games_dir(args.games_dir)
    run_a = bl.resolve_run_id(args.run_a)
    run_b = bl.resolve_run_id(args.run_b)
    print(render(run_a, run_b, bl.load_manifest(run_a), bl.load_manifest(run_b), args.count_nonwins))


if __name__ == "__main__":
    main()
