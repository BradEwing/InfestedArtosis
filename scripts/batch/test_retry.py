import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import batchlib as bl
import report

ROW = {"is_winner": "true"}


def entry(index, opponent="opp", maps=("m0", "m1", "m2")):
    return {"index": index, "game_name": f"T{index:03d}", "opponent": opponent, "map": maps[index % len(maps)]}


def drive(games, outcomes, max_retries, log):
    queue = list(games)
    manifest = []
    failures = 0
    while queue:
        game = queue.pop(0)

        def play(attempt):
            manifest.append(attempt)
            log.append((attempt["game_name"], attempt["index"], attempt["map"]))
            result = outcomes.get(attempt["game_name"], ("WIN", 100, ROW))
            attempt["outcome"] = result[0]
            return result

        attempts = bl.play_index(game, play, max_retries)
        failures = bl.next_failure_count(failures, attempts)
    return manifest, failures


class NeedsRetryTest(unittest.TestCase):
    def test_no_learning_row_needs_retry_whatever_the_outcome(self):
        for outcome in ("WIN", "LOSS", "DRAW", "CRASH", "TIMEOUT", "STALL", "NO_RESULT"):
            self.assertTrue(bl.needs_retry((outcome, 100, None)), outcome)

    def test_learning_row_needs_no_retry(self):
        for outcome in ("WIN", "LOSS", "CRASH"):
            self.assertFalse(bl.needs_retry((outcome, 100, ROW)), outcome)


class PlayIndexTest(unittest.TestCase):
    def test_non_result_is_retried_on_same_index_before_the_next(self):
        games = [entry(i) for i in range(3)]
        log = []
        drive(games, {"T001": ("CRASH", 10, None)}, 2, log)
        self.assertEqual(
            [("T000", 0, "m0"), ("T001", 1, "m1"), ("T001R1", 1, "m1"), ("T002", 2, "m2")], log)

    def test_recorded_game_map_sequence_matches_run_without_non_result(self):
        games = [entry(i) for i in range(5)]
        clean_log, faulty_log = [], []
        clean, _ = drive(games, {}, 2, clean_log)
        faulty, _ = drive([entry(i) for i in range(5)], {"T002": ("NO_RESULT", None, None)}, 2, faulty_log)
        recorded = [(g["index"], g["map"]) for g in faulty if g["outcome"] != "NO_RESULT"]
        self.assertEqual([(g["index"], g["map"]) for g in clean], recorded)

    def test_retries_are_bounded_then_scheduling_continues(self):
        games = [entry(i) for i in range(3)]
        log = []
        manifest, _ = drive(games, {name: ("CRASH", 1, None) for name in ("T001", "T001R1", "T001R2")}, 2, log)
        self.assertEqual(["T000", "T001", "T001R1", "T001R2", "T002"], [n for n, _, _ in log])
        finals = bl.final_attempts(manifest)
        self.assertEqual("T001R2", finals[1]["game_name"])
        self.assertEqual(2, bl.retry_count(manifest))

    def test_zero_max_retries_never_retries(self):
        log = []
        drive([entry(0)], {"T000": ("CRASH", 1, None)}, 0, log)
        self.assertEqual(["T000"], [n for n, _, _ in log])

    def test_retry_entry_links_to_original_and_marks_it_retried(self):
        games = [entry(0)]
        manifest, _ = drive(games, {"T000": ("CRASH", 1, None)}, 2, [])
        original, retry = manifest
        self.assertTrue(original["retried"])
        self.assertEqual("T000", retry["retry_of"])
        self.assertEqual(1, retry["retry"])
        self.assertEqual(original["index"], retry["index"])
        self.assertEqual(original["map"], retry["map"])
        self.assertNotIn("retried", retry)

    def test_second_retry_names_derive_from_the_original(self):
        manifest, _ = drive([entry(0)], {"T000": ("CRASH", 1, None), "T000R1": ("CRASH", 1, None)}, 2, [])
        self.assertEqual(["T000", "T000R1", "T000R2"], [g["game_name"] for g in manifest])
        self.assertEqual({"T000"}, {g.get("retry_of", "T000") for g in manifest})

    def test_stop_request_prevents_further_retries(self):
        game = entry(0)
        attempts = bl.play_index(game, lambda g: ("CRASH", 1, None), 2, lambda: True)
        self.assertEqual(1, len(attempts))


class LaunchFailureCountTest(unittest.TestCase):
    def test_retried_index_counts_once(self):
        _, failures = drive([entry(0)], {n: ("NO_RESULT", None, None) for n in ("T000", "T000R1", "T000R2")}, 2, [])
        self.assertEqual(1, failures)

    def test_recovered_retry_resets_the_count(self):
        _, failures = drive([entry(0)], {"T000": ("NO_RESULT", None, None)}, 2, [])
        self.assertEqual(0, failures)

    def test_broken_environment_still_reaches_the_threshold(self):
        outcomes = {}
        for i in range(3):
            for suffix in ("", "R1", "R2"):
                outcomes[f"T{i:03d}{suffix}"] = ("NO_RESULT", None, None)
        _, failures = drive([entry(i) for i in range(3)], outcomes, 2, [])
        self.assertEqual(3, failures)


class FinalAttemptsTest(unittest.TestCase):
    def test_returns_last_attempt_per_index_ordered(self):
        games = [
            {"index": 1, "game_name": "b"},
            {"index": 0, "game_name": "a"},
            {"index": 1, "game_name": "bR1", "retry": 1},
            {"index": 1, "game_name": "bR2", "retry": 2},
        ]
        self.assertEqual(["a", "bR2"], [g["game_name"] for g in bl.final_attempts(games)])

    def test_empty(self):
        self.assertEqual([], bl.final_attempts([]))


def result(index, row, outcome="WIN", opponent="opp", retry=0):
    r = {"index": index, "opponent": opponent, "row": row, "outcome": outcome}
    if retry:
        r["retry"] = retry
    return r


class FirstNonResultIndexTest(unittest.TestCase):
    def test_none_when_every_final_attempt_has_a_row(self):
        results = [result(0, ROW), result(1, None, "CRASH"), result(1, ROW, retry=1)]
        self.assertIsNone(report.first_non_result_index(results, "opp"))

    def test_first_index_whose_final_attempt_has_no_row(self):
        results = [
            result(0, ROW),
            result(1, None, "CRASH"), result(1, ROW, retry=1),
            result(2, None, "CRASH"), result(2, None, "NO_RESULT", retry=1),
            result(3, None, "CRASH"),
        ]
        self.assertEqual(2, report.first_non_result_index(results, "opp"))

    def test_other_opponents_are_ignored(self):
        results = [result(0, None, "CRASH", opponent="other"), result(1, ROW)]
        self.assertIsNone(report.first_non_result_index(results, "opp"))

    def test_running_game_is_not_a_non_result(self):
        self.assertIsNone(report.first_non_result_index([result(0, None, "RUNNING")], "opp"))


if __name__ == "__main__":
    unittest.main()
