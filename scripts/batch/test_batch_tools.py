import json
import sys
import tempfile
import unittest
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import batchlib as bl
import opphistory
import report
import window

FIXTURES = Path(__file__).resolve().parent / "fixtures"

SCORES_CRASHED = {"is_winner": False, "is_crashed": True, "is_nostart": False, "timed_out": False}
SCORES_CLEAN = {"is_winner": False, "is_crashed": False, "is_nostart": False, "timed_out": False}
RESULT_CRASHED = {"is_crashed": True, "is_realtime_outed": False, "game_time": 842.7, "winner": None, "loser": None}
FRAMES_HEADER = "frame_count,frame_time_max,frame_time_avg\n"
LOG_UNFINISHED = "Connected\nConnection successful\n"
LOG_FINISHED = LOG_UNFINISHED + "2026-10-07T08:35:23 Bot exited.\n"
LOG_JVM_DEATH = LOG_UNFINISHED + 'Exception in thread "main" java.lang.NullPointerException\n'


def load_fixture(name):
    with open(FIXTURES / name, encoding="utf-8") as f:
        return json.load(f)


def make_game_dir(root, name, result=None, scores=None, last_frame=None, log=None):
    gdir = root / f"GAME_{name}"
    (gdir / "logs_0").mkdir(parents=True)
    if result is not None:
        (gdir / "result.json").write_text(json.dumps(result), encoding="utf-8")
    if scores is not None:
        (gdir / "logs_0" / "scores.json").write_text(json.dumps(scores), encoding="utf-8")
    if last_frame is not None:
        (gdir / "logs_0" / "frames.csv").write_text(f"{FRAMES_HEADER}24,1,1\n{last_frame},1,1\n", encoding="utf-8")
    if log is not None:
        (gdir / "logs_0" / "bot.log").write_text(log, encoding="utf-8")
    return gdir


class GamesDirTestCase(unittest.TestCase):
    def setUp(self):
        self.saved_games_dir = bl.GAMES_DIR
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        bl.set_games_dir(self.root)

    def tearDown(self):
        bl.GAMES_DIR = self.saved_games_dir
        self.tmp.cleanup()


class FisherTest(unittest.TestCase):
    def test_tea_tasting_table(self):
        self.assertAlmostEqual(window.fisher_exact(3, 1, 1, 3), 34 / 70, places=9)

    def test_extreme_table(self):
        self.assertAlmostEqual(window.fisher_exact(0, 10, 10, 0), 2 / 184756, places=12)

    def test_identical_arms_give_one(self):
        self.assertEqual(window.fisher_exact(5, 5, 5, 5), 1.0)

    def test_empty_table(self):
        self.assertEqual(window.fisher_exact(0, 0, 0, 0), 1.0)


class WindowTest(unittest.TestCase):
    def setUp(self):
        self.control = load_fixture("control_trim.json")
        self.beta = load_fixture("beta_trim.json")

    def rows(self, count_nonwins):
        return dict(window.window(self.control, self.beta, count_nonwins))

    def test_draw_does_not_slide_later_indices(self):
        grim = self.rows(False)["GrimHammer"]
        self.assertEqual(grim["k"], 5)
        self.assertEqual(grim["inc_a"], 1)
        self.assertEqual(grim["inc_b"], 0)
        self.assertEqual(grim["miss"], 1)
        self.assertEqual(grim["wins_a"], 1)
        self.assertEqual(grim["wins_b"], 1)

    def test_draw_counts_as_non_win_when_asked(self):
        grim = self.rows(True)["GrimHammer"]
        self.assertEqual(grim["k"], 6)
        self.assertEqual(grim["wins_a"], 1)
        self.assertEqual(grim["wins_b"], 1)

    def test_in_flight_index_is_left_out(self):
        pairs, missing, _ = window.align(self.control, self.beta)
        self.assertNotIn(340, [index for index, _, _ in pairs["GrimHammer"]])
        self.assertEqual(missing["GrimHammer"], 1)

    def test_replayed_index_uses_the_final_attempt(self):
        pair = window.align(self.control, self.beta)[0]["insanitybot"]
        self.assertEqual(len(pair), 1)
        _, game_a, game_b = pair[0]
        self.assertEqual(game_a["outcome"], "WIN")
        self.assertEqual(game_b["game_name"], "MJ3IG00DR1")
        self.assertEqual(game_b["outcome"], "LOSS")

    def test_non_result_final_counts_as_non_win_and_is_shown(self):
        self.beta["games"][-2]["outcome"] = "NO_RESULT"
        grim = self.rows(True)["GrimHammer"]
        self.assertEqual(grim["k"], 6)
        self.assertEqual(grim["inc_b"], 1)
        self.assertEqual(grim["wins_b"], 0)

    def test_maps_column_counts_matching_maps(self):
        self.beta["games"][3]["map"] = "elsewhere.scx"
        grim = self.rows(False)["GrimHammer"]
        self.assertEqual(grim["maps_same"], grim["k"] - 1)

    def test_opponent_mismatch_at_an_index_is_not_paired(self):
        for game in self.beta["games"]:
            if game["index"] == 13:
                game["opponent"] = "GrimHammer"
        pairs, _, mismatched = window.align(self.control, self.beta)
        self.assertNotIn(13, [i for i, _, _ in pairs.get("insanitybot", [])])
        self.assertEqual(mismatched, [13])
        self.assertTrue(any("different opponent" in w for w in window.warnings(self.control, self.beta)))

    def test_isolated_run_against_a_full_batch_warns(self):
        self.beta["opponents"] = ["GrimHammer"]
        self.beta["games_per_opponent"] = 25
        found = window.warnings(self.control, self.beta)
        self.assertTrue(any("different opponents" in w for w in found))
        self.assertTrue(any("games_per_opponent" in w for w in found))
        self.assertEqual(window.warnings(self.control, self.control), [])

    def test_total_row_sums_opponents(self):
        rows = window.window(self.control, self.beta)
        self.assertEqual(rows[-1][0], "TOTAL")
        self.assertEqual(rows[-1][1]["k"], sum(s["k"] for _, s in rows[:-1]))

    def test_render_has_a_row_per_opponent_and_total(self):
        text = window.render("A", "B", self.control, self.beta)
        for name in ("GrimHammer", "insanitybot", "TOTAL"):
            self.assertIn(name, text)


class LabelTest(GamesDirTestCase):
    def label(self, game, outcome="CRASH", run_stopped=False):
        return bl.non_result_label(game, outcome, run_stopped)

    def test_frame_cap_stalemate(self):
        make_game_dir(self.root, "MJ3IG00D", RESULT_CRASHED, SCORES_CRASHED, 89616, LOG_UNFINISHED)
        game = {"game_name": "MJ3IG00D", "outcome": "CRASH"}
        self.assertEqual(bl.classify(game)[0], "CRASH")
        self.assertEqual(self.label(game), bl.LABEL_STALEMATE)

    def test_stalemate_that_wrote_its_row_is_recognised_and_not_replayed(self):
        gdir = make_game_dir(self.root, "MJ3IG00E", RESULT_CRASHED, SCORES_CRASHED, 89616, LOG_UNFINISHED)
        (gdir / "write_0").mkdir()
        (gdir / "write_0" / "Pylon Puller_Protoss.csv").write_text(
            "timestamp,is_winner,num_starting_locations,map_name,opponent_name,opponent_race,opener,build_order,"
            "detected_strategies,frame_count,reason\n"
            "1,true,4,(4)Map.scx,Pylon Puller,Protoss,9Hatch,SpeedlingP,,12000\n"
            "2,false,4,(4)Map.scx,Pylon Puller,Protoss,3HatchBeforePool,SpeedlingP,2Gate,86400,stalemate\n",
            encoding="utf-8")
        game = {"game_name": "MJ3IG00E", "outcome": "CRASH"}
        classified = bl.classify(game)
        self.assertEqual(classified[0], "CRASH")
        self.assertTrue(bl.is_stalemate_row(classified[2]))
        self.assertFalse(bl.needs_retry(classified))
        self.assertEqual(self.label(game), bl.LABEL_STALEMATE)

    def test_stop_rule_cut_is_stopped_with_the_end_line(self):
        make_game_dir(self.root, "MJ3IG09G", RESULT_CRASHED, SCORES_CRASHED, 6648, LOG_UNFINISHED)
        game = {"game_name": "MJ3IG09G"}
        self.assertEqual(self.label(game, run_stopped=True), bl.LABEL_STOPPED)

    def test_unrecorded_attempt_with_a_result_is_stopped_without_the_end_line(self):
        make_game_dir(self.root, "MJ3IG09G", RESULT_CRASHED, SCORES_CRASHED, 6648, LOG_UNFINISHED)
        self.assertEqual(self.label({"game_name": "MJ3IG09G"}), bl.LABEL_STOPPED)

    def test_in_flight_game_without_a_result_is_not_labelled_while_the_run_lives(self):
        make_game_dir(self.root, "LIVE", None, None, 500, LOG_UNFINISHED)
        self.assertIsNone(self.label({"game_name": "LIVE"}, "NO_RESULT"))

    def test_recorded_crash_is_not_stopped(self):
        make_game_dir(self.root, "RECORDED", RESULT_CRASHED, SCORES_CRASHED, 30000, LOG_FINISHED)
        game = {"game_name": "RECORDED", "outcome": "CRASH"}
        self.assertIsNone(self.label(game, run_stopped=True))

    def test_real_crash_is_jvm_died_when_the_log_has_no_end(self):
        make_game_dir(self.root, "DIED", RESULT_CRASHED, SCORES_CRASHED, 30000, LOG_UNFINISHED)
        self.assertEqual(self.label({"game_name": "DIED", "outcome": "CRASH"}), bl.LABEL_JVM_DIED)

    def test_real_crash_is_jvm_died_on_the_exception_marker(self):
        make_game_dir(self.root, "DIED", None, None, 30000, LOG_JVM_DEATH)
        self.assertEqual(self.label({"game_name": "DIED", "outcome": "CRASH"}), bl.LABEL_JVM_DIED)

    def test_opponent_crash_is_not_jvm_died(self):
        gdir = make_game_dir(self.root, "THEIRS", RESULT_CRASHED, SCORES_CLEAN, 30000, LOG_UNFINISHED)
        (gdir / "logs_1").mkdir()
        (gdir / "logs_1" / "scores.json").write_text(json.dumps(SCORES_CRASHED), encoding="utf-8")
        self.assertIsNone(self.label({"game_name": "THEIRS", "outcome": "CRASH"}))

    def test_opponent_assert_is_not_jvm_died(self):
        gdir = make_game_dir(self.root, "FROZE", RESULT_CRASHED, SCORES_CRASHED, 30000, LOG_UNFINISHED)
        (gdir / "logs_1").mkdir()
        (gdir / "logs_1" / "bot.log").write_text("Assertion failed\n", encoding="utf-8")
        self.assertIsNone(self.label({"game_name": "FROZE", "outcome": "CRASH"}))

    def test_crash_with_a_clean_log_keeps_its_plain_outcome(self):
        make_game_dir(self.root, "CLEAN", RESULT_CRASHED, SCORES_CRASHED, 30000, LOG_FINISHED)
        self.assertIsNone(self.label({"game_name": "CLEAN", "outcome": "CRASH"}))

    def test_draw_below_the_frame_cap_is_unlabelled(self):
        make_game_dir(self.root, "MIP91099", RESULT_CRASHED, SCORES_CLEAN, 30768, LOG_FINISHED)
        game = {"game_name": "MIP91099", "outcome": "DRAW"}
        self.assertEqual(bl.classify(game)[0], "DRAW")
        self.assertIsNone(self.label(game, "DRAW"))

    def test_conclusive_outcomes_are_never_labelled(self):
        make_game_dir(self.root, "WON", {"winner": bl.BOT_NAME, "is_crashed": False}, None, 90000, LOG_UNFINISHED)
        self.assertIsNone(self.label({"game_name": "WON", "outcome": "WIN"}, "WIN"))

    def test_missing_log_is_not_jvm_died(self):
        make_game_dir(self.root, "NOLOG", RESULT_CRASHED, SCORES_CRASHED, 30000, None)
        self.assertIsNone(self.label({"game_name": "NOLOG", "outcome": "CRASH"}))

    def test_stdout_log_end_line(self):
        text = "Batch 20261007-012752 stopped-by-owner-rule after 493 games\n"
        self.assertTrue(bl.run_stopped_by_log("20261007-012752", text))
        self.assertFalse(bl.run_stopped_by_log("20261007-999999", text))
        self.assertFalse(bl.run_stopped_by_log("20261007-012752", "Batch 20261007-012752 completed (1/1)\n"))

    def test_report_collect_labels_a_stopped_run_and_keeps_it_out_of_running(self):
        make_game_dir(self.root, "CUT", None, None, 500, LOG_UNFINISHED)
        manifest = {"games": [{"index": 0, "game_name": "CUT", "opponent": "o", "map": "m"}]}
        live = report.collect(manifest)
        stopped = report.collect(manifest, run_stopped=True)
        self.assertEqual(live[0]["outcome"], "RUNNING")
        self.assertIsNone(live[0]["label"])
        self.assertEqual(stopped[0]["outcome"], "NO_RESULT")
        self.assertEqual(stopped[0]["label"], bl.LABEL_STOPPED)


class HistoryRowTest(unittest.TestCase):
    PURPLEWAVE = ("{ms}, 2, (2)Destination.scx,     Infested Artosis, Protoss, Zerg, true,  13:31, PvZExpand,"
                  "                {plan}, PvZSpeedlot,    &12Pool,        ")
    MICROWAVE = "v2.7;{s};2;(2)Benzene.scx;Microwave;Zerg;Infested_Artosis;Zerg;0;1;{plan};Unknown;HeavyRush"

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    @staticmethod
    def epoch(iso):
        return datetime.fromisoformat(iso).timestamp()

    def write(self, name, lines):
        path = self.root / name
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return path

    def game(self, name, launched, finished, map_name="(2)Destination.scx"):
        return {"game_name": name, "map": map_name, "launched_at": launched, "finished_at": finished}

    def purplewave_file(self, *plans_at):
        rows = [self.PURPLEWAVE.format(ms=int(self.epoch(iso) * 1000), plan=plan) for iso, plan in plans_at]
        return self.write("_v4_history_Infested Artosis.csv", rows)

    def test_purplewave_joins_the_row_for_the_game_not_the_oldest_or_newest(self):
        path = self.purplewave_file(("2026-10-07T05:34:25", "PlanNew"), ("2026-10-07T05:31:15", "PlanMid"),
                                    ("2026-10-07T05:28:36", "PlanOld"))
        game = self.game("G", "2026-10-07T05:28:45", "2026-10-07T05:31:25")
        row = opphistory.history_row(game, path)
        self.assertEqual(row["format"], opphistory.PURPLEWAVE_FORMAT)
        self.assertEqual(opphistory.purplewave_strategy(row)[1], "PlanMid")

    def test_purplewave_strategy_drops_the_ampersand_tokens(self):
        path = self.purplewave_file(("2026-10-07T05:34:25", "PlanNew"))
        row = opphistory.history_row(self.game("G", "2026-10-07T05:31:25", "2026-10-07T05:34:35"), path)
        self.assertEqual(opphistory.purplewave_strategy(row), ["PvZExpand", "PlanNew", "PvZSpeedlot"])

    def test_microwave_oldest_first_file(self):
        rows = [self.MICROWAVE.format(s=int(self.epoch(iso)), plan=plan)
                for iso, plan in (("2026-10-07T01:28:10", "First"), ("2026-10-07T01:31:10", "Second"))]
        path = self.write("history_Infested_Artosis.txt", rows)
        game = self.game("G", "2026-10-07T01:30:55", "2026-10-07T01:34:10", "(2)Benzene.scx")
        row = opphistory.history_row(game, path)
        self.assertEqual(row["format"], opphistory.MICROWAVE_FORMAT)
        self.assertEqual(row["fields"][10], "Second")

    def test_game_id_wins_over_timestamp(self):
        rows = [self.MICROWAVE.format(s=int(self.epoch("2026-10-07T01:28:10")), plan="Mine"),
                self.MICROWAVE.format(s=int(self.epoch("2026-10-07T01:31:10")), plan="Other")]
        rows[0] = rows[0] + ";GAMEID1"
        path = self.write("history_Infested_Artosis.txt", rows)
        game = self.game("GAMEID1", "2026-10-07T01:30:55", "2026-10-07T01:34:10", "(2)Benzene.scx")
        self.assertEqual(opphistory.history_row(game, path)["fields"][10], "Mine")

    def test_nearest_timestamp_within_tolerance_only(self):
        path = self.purplewave_file(("2026-10-07T05:00:00", "Far"))
        game = self.game("G", "2026-10-07T05:28:45", "2026-10-07T05:31:25")
        self.assertIsNone(opphistory.history_row(game, path))

    def test_map_breaks_a_tie_between_nearby_rows(self):
        near = self.PURPLEWAVE.format(ms=int(self.epoch("2026-10-07T05:31:20") * 1000), plan="Wrong").replace(
            "(2)Destination.scx", "(4)Python.scx")
        right = self.PURPLEWAVE.format(ms=int(self.epoch("2026-10-07T05:31:40") * 1000), plan="Right")
        path = self.write("_v4_history_Infested Artosis.csv", [near, right])
        row = opphistory.history_row(self.game("G", "2026-10-07T05:28:45", "2026-10-07T05:31:25"), path)
        self.assertEqual(row["fields"][9], "Right")

    def test_two_equally_near_rows_return_none(self):
        rows = [self.PURPLEWAVE.format(ms=int(self.epoch(iso) * 1000), plan="P")
                for iso in ("2026-10-07T05:28:15", "2026-10-07T05:29:15")]
        path = self.write("_v4_history_Infested Artosis.csv", rows)
        self.assertIsNone(opphistory.history_row(self.game("G", "2026-10-07T05:28:45", "2026-10-07T05:28:45"), path))

    def test_unknown_format_returns_none(self):
        path = self.write("Infested Artosis.txt", ["(2)Benzene.scx|288,3120|Nuke|W"])
        game = self.game("G", "2026-10-07T01:30:55", "2026-10-07T01:34:10")
        self.assertIsNone(opphistory.history_row(game, path))

    def test_missing_file_and_missing_timestamp_return_none(self):
        game = self.game("G", "2026-10-07T01:30:55", "2026-10-07T01:34:10")
        self.assertIsNone(opphistory.history_row(game, self.root / "absent.csv"))
        path = self.purplewave_file(("2026-10-07T05:34:25", "Plan"))
        self.assertIsNone(opphistory.history_row({"game_name": "G", "map": "m"}, path))


if __name__ == "__main__":
    unittest.main()
