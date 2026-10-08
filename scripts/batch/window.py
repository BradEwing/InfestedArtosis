"""Per-opponent window comparison of two batch runs, aligned on map index.

Usage:
  py scripts/batch/window.py <run-a> <run-b> [--count-nonwins]

Each run contributes only the final attempt of every game index, so a replayed index counts once and the
two runs line up on the index (and so on the map) rather than on position in a filtered list. An index is
paired only when both final attempts are a WIN or LOSS: a draw, a crash, a stalemate or a NO_RESULT in either
run drops that index from both sides, so k per opponent is the number of conclusive pairs and the inc column
counts the indices dropped that way. With --count-nonwins every index both runs have a final result for is
paired and a draw or non-result counts as a non-win instead; a final attempt that recorded NO_RESULT is
shown in the nr column. An index still in flight in either run has no final result and is left out in both
modes. Outcomes are the manifest's, with no relabelling of stalemates or stopped games.

Outcomes come from the manifests, so no game dirs are read. The p value is a two-sided Fisher exact test on
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
    """Per opponent, the (index, game_a, game_b) triples both runs have a final result for, by index.

    An index whose opponent differs between the runs is not a pairing and is dropped."""
    finals_a = finished_by_index(manifest_a)
    finals_b = finished_by_index(manifest_b)
    pairs = {}
    for index in sorted(set(finals_a) & set(finals_b)):
        game_a = finals_a[index]
        game_b = finals_b[index]
        if game_a["opponent"] == game_b["opponent"]:
            pairs.setdefault(game_a["opponent"], []).append((index, game_a, game_b))
    return pairs


def is_conclusive_pair(pair):
    _, game_a, game_b = pair
    return game_a["outcome"] in bl.CONCLUSIVE and game_b["outcome"] in bl.CONCLUSIVE


def summarize(pairs, count_nonwins):
    """Counts for one opponent's aligned pairs.

    k pairs are scored: every pair when count_nonwins is set, otherwise only the conclusive ones. inc is the
    number of aligned pairs with a draw or non-result on either side, dropped from k unless count_nonwins."""
    scored = pairs if count_nonwins else [p for p in pairs if is_conclusive_pair(p)]
    return {
        "k": len(scored),
        "wins_a": sum(1 for _, a, _ in scored if a["outcome"] == "WIN"),
        "wins_b": sum(1 for _, _, b in scored if b["outcome"] == "WIN"),
        "nr_a": sum(1 for _, a, _ in scored if a["outcome"] == "NO_RESULT"),
        "nr_b": sum(1 for _, _, b in scored if b["outcome"] == "NO_RESULT"),
        "maps_same": sum(1 for _, a, b in scored if a.get("map") == b.get("map")),
        "inc": sum(1 for p in pairs if not is_conclusive_pair(p)),
    }


def window(manifest_a, manifest_b, count_nonwins=False):
    """Rows of (opponent, summary) sorted by opponent, followed by the ("TOTAL", summary) row."""
    aligned = align(manifest_a, manifest_b)
    rows = [(o, summarize(p, count_nonwins)) for o, p in sorted(aligned.items())]
    total = {key: sum(s[key] for _, s in rows) for key in ("k", "wins_a", "wins_b", "nr_a", "nr_b", "maps_same", "inc")}
    return rows + [("TOTAL", total)]


def p_value(summary):
    k = summary["k"]
    return fisher_exact(summary["wins_a"], k - summary["wins_a"], summary["wins_b"], k - summary["wins_b"])


def format_row(name, summary, width):
    k = summary["k"]
    wa = summary["wins_a"]
    wb = summary["wins_b"]
    ra = wa / k if k else 0.0
    rb = wb / k if k else 0.0
    return (f"  {name:<{width}} {k:>4}  {wa:>3}/{k:<3} {ra:>6.1%}  {wb:>3}/{k:<3} {rb:>6.1%}  "
            f"{(rb - ra) * 100:>+6.1f}pp  {p_value(summary):>6.3f}  {summary['maps_same']:>3}/{k:<3}  "
            f"{summary['inc']:>3}  {summary['nr_a']:>2}/{summary['nr_b']:<2}")


def render(run_a, run_b, manifest_a, manifest_b, count_nonwins=False):
    rows = window(manifest_a, manifest_b, count_nonwins)
    treatment = "draws and non-results count as non-wins" if count_nonwins else "WIN or LOSS pairs only"
    width = max(len(name) for name, _ in rows)
    lines = [f"Window {run_a} (A) vs {run_b} (B): final attempt per map index; {treatment}",
             f"  {'':<{width}} {'k':>4}  {'A W/n':>7} {'':>6}  {'B W/n':>7} {'':>6}  {'delta':>8}  {'p':>6}  "
             f"{'maps':>7}  {'inc':>3}  nr A/B"]
    lines.extend(format_row(name, summary, width) for name, summary in rows)
    lines.append("  (k indices with a final result in both runs; maps = same map at the index; "
                 "inc = aligned pairs with a draw or non-result on either side; "
                 "nr = scored final attempts that recorded NO_RESULT; Fisher exact two-sided)")
    return "\n".join(lines)


def main():
    args = parse_args()
    run_a = bl.resolve_run_id(args.run_a)
    run_b = bl.resolve_run_id(args.run_b)
    print(render(run_a, run_b, bl.load_manifest(run_a), bl.load_manifest(run_b), args.count_nonwins))


if __name__ == "__main__":
    main()
