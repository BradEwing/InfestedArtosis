import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import prior


def game(run, opponent, gnum, opener, build, win, race="Terran", file_race=None):
    return {"run": run, "opponent": opponent, "race": race, "file_race": file_race or race, "win": win,
            "gnum": gnum, "index": gnum, "opener": opener, "build": build}


def repeat(count, **kwargs):
    return [game(run=f"2026100{i}", **kwargs) for i in range(count)]


class FirstExposureTest(unittest.TestCase):
    def test_counts_only_the_first_game_of_an_arm_per_run_and_opponent(self):
        rows = [game("r0", "a", 1, "9Hatch", "x", 1), game("r0", "a", 2, "9Hatch", "x", 0)]
        self.assertEqual([("Terran", "9Hatch", "a", 1)], prior.first_exposures(rows, "opener", 10))

    def test_ignores_exposures_past_the_window(self):
        rows = [game("r0", "a", 11, "9Hatch", "x", 1)]
        self.assertEqual([], prior.first_exposures(rows, "opener", 10))

    def test_a_build_equal_to_the_opener_is_not_a_build(self):
        rows = [game("r0", "a", 1, "4Pool", "4Pool", 1)]
        self.assertEqual([], prior.first_exposures(rows, "build", 10))

    def test_since_drops_earlier_runs(self):
        rows = [game("20260905%02d" % i, "a", 1, "Old", "x", 1) for i in range(30)]
        self.assertEqual([], prior.build_prior(rows, since="20261001"))
        self.assertNotEqual([], prior.build_prior(rows, since="20260901"))

    def test_random_bot_is_keyed_unknown(self):
        rows = [game("r0", "Dave Churchill", 1, "12Pool", "x", 1, race="Zerg")]
        self.assertEqual("Unknown", prior.first_exposures(rows, "opener", 10)[0][0])


class ArmNameTest(unittest.TestCase):
    def test_legacy_speedling_maps_by_race(self):
        for race, expected in (("Terran", "SpeedlingT"), ("Protoss", "SpeedlingP"), ("Zerg", "SpeedlingZ"),
                               ("Unknown", "SpeedlingR")):
            self.assertEqual(expected, prior.arm_name("build", "SpeedlingAllIn", race))

    def test_other_names_and_openers_are_unchanged(self):
        self.assertEqual("3HatchMuta", prior.arm_name("build", "3HatchMuta", "Protoss"))
        self.assertEqual("SpeedlingAllIn", prior.arm_name("opener", "SpeedlingAllIn", "Protoss"))


class BuildPriorTest(unittest.TestCase):
    def test_balanced_rate_is_mean_of_opponent_rates(self):
        observations = [("a", 1)] * 3 + [("b", 0)] + [("b", 1)]
        self.assertAlmostEqual((1.0 + 0.5) / 2, prior.balanced_rate(observations))

    def test_strong_arm_gets_more_pseudo_wins_than_weak_arm(self):
        rows = []
        for i in range(20):
            rows.append(game(f"s{i}", "a", 1, "Strong", "x", 1))
            rows.append(game(f"w{i}", "a", 1, "Weak", "x", 0 if i else 1))
        rows = [r for r in rows if r["opener"] in ("Strong", "Weak")]
        rows.append(game("z", "a", 1, "Mid", "x", 1))
        result = {arm: (wins, games) for race, kind, arm, wins, games in prior.build_prior(rows, min_n=5)
                  if kind == "opener"}
        self.assertGreater(result["Strong"][0], result["Weak"][0])

    def test_consistent_loser_gets_more_pseudo_games(self):
        rows = repeat(100, opponent="a", gnum=1, opener="Loser", build="x", win=0)
        rows += repeat(100, opponent="a", gnum=1, opener="Other", build="x", win=1)
        result = {arm: games for race, kind, arm, wins, games in prior.build_prior(rows)
                  if kind == "opener"}
        self.assertEqual(5, result["Loser"])
        self.assertEqual(3, result["Other"])

    def test_arm_below_min_n_is_left_out(self):
        rows = repeat(4, opponent="a", gnum=1, opener="Rare", build="x", win=1)
        self.assertEqual([], [row for row in prior.build_prior(rows, min_n=5) if row[2] == "Rare"])

    def test_pseudo_wins_are_fractional_not_rounded(self):
        rows = repeat(10, opponent="a", gnum=1, opener="Mid", build="x", win=0) + [
            game("z%d" % i, "a", 1, "Mid", "x", 1) for i in range(10)]
        wins = {arm: w for race, kind, arm, w, g in prior.build_prior(rows, since="") if kind == "opener"}
        self.assertNotEqual(wins["Mid"], round(wins["Mid"]))

    def test_pseudo_wins_never_exceed_pseudo_games(self):
        rows = repeat(30, opponent="a", gnum=1, opener="Great", build="x", win=1)
        for race, kind, arm, wins, games in prior.build_prior(rows):
            self.assertLessEqual(wins, games)

    def test_build_rows_use_per_race_speedling_name(self):
        rows = repeat(10, opponent="a", gnum=1, opener="9Hatch", build="SpeedlingAllIn", win=1, race="Protoss")
        builds = [row for row in prior.build_prior(rows) if row[1] == "build"]
        self.assertEqual(["SpeedlingP"], [row[2] for row in builds])


class WriteTest(unittest.TestCase):
    def test_round_trips_with_header(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "p.csv"
            prior.write_prior([("Terran", "opener", "9Hatch", 2, 3)], path)
            lines = path.read_text(encoding="utf-8").splitlines()
        self.assertEqual("race,kind,arm,pseudo_wins,pseudo_games", lines[0])
        self.assertEqual("Terran,opener,9Hatch,2,3", lines[1])


if __name__ == "__main__":
    unittest.main()
