"""Shared helpers for the batch game harness (run.py / report.py).

Requires Python 3.11: scbw's --read_overwrite uses distutils, removed in 3.12.
"""

import csv
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

BOT_NAME = "Infested Artosis"
DOCKER_IMAGE = "starcraft:game"

# Every telemetry file the bot writes carries this prefix, so one rule keeps them all out of the learning glob.
TELEMETRY_PREFIX = "telemetry_"

# Names come from the writers: TelemetryLog for the combat files, PlanEventLogger for the stamped ones.
TELEMETRY_COMBAT_GAME = TELEMETRY_PREFIX + "combat_game.csv"
TELEMETRY_ENGAGEMENTS = TELEMETRY_PREFIX + "engagements.csv"
TELEMETRY_ENGAGEMENT_UNITS = TELEMETRY_PREFIX + "engagement_units.csv"
TELEMETRY_GAME_GLOB = TELEMETRY_PREFIX + "game_*.csv"
TELEMETRY_PLANS_GLOB = TELEMETRY_PREFIX + "plans_*.csv"

TELEMETRY_COMBAT_FLAG = "IA_TELEMETRY_COMBAT"
PLAN_EVENT_FLAG = "IA_LOG_PLAN_EVENTS"

SCBW_ROOT = Path(os.environ.get("APPDATA", Path.home() / "AppData" / "Roaming")) / "scbw"
BOTS_DIR = SCBW_ROOT / "bots"
GAMES_DIR = SCBW_ROOT / "games"
MAPS_DIR = SCBW_ROOT / "maps" / "sscai"
BATCHES_DIR = SCBW_ROOT / "batches"

# A reader holds the manifest for milliseconds; 20 tries over 5 s outlasts any poll without stalling a worker long.
MANIFEST_REPLACE_ATTEMPTS = 20
MANIFEST_REPLACE_BACKOFF_S = 0.25

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MAPS_FILE = Path(__file__).resolve().parent / "maps.txt"


def scbw_play_command():
    exe = shutil.which("scbw.play")
    if exe:
        return [exe]
    return [sys.executable, "-c", "from scbw.cli import main; main()"]


OUTCOMES = ("WIN", "LOSS", "DRAW", "CRASH", "TIMEOUT", "STALL", "NO_RESULT")
CONCLUSIVE = ("WIN", "LOSS")

# Written by the JVM's default handler when an exception escapes a thread: the bot process died mid-game.
JVM_DEATH_MARKER = "Exception in thread"


BOT_EXITED_MARKER = "Bot exited."

# Games that hit the frame cap end near 87k-90k frames (about 61-62 min); the lowest stalemate seen is 87192.
FRAME_CAP_FRAMES = 85000

LABEL_STALEMATE = "STALEMATE"
LABEL_STOPPED = "STOPPED"
LABEL_JVM_DIED = "JVM_DIED"


def now_id():
    return datetime.now().strftime("%Y%m%d-%H%M%S")


def base36(n):
    digits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    out = ""
    while n:
        n, r = divmod(n, 36)
        out = digits[r] + out
    return out or "0"


def game_name_tag():
    """5-char tag unique per launch. Game names must stay short: BW lobby names are limited
    to 24 chars including scbw's GAME_ prefix, and longer names make the joiner never find the host."""
    return base36(int(time.time()))[-5:]


def game_name(tag, index):
    return f"{tag}{base36(index).rjust(3, '0')}"


def needs_retry(classified):
    """A game needs a retry when it produced no learning row, whatever its outcome.
    Takes the (outcome, game_time, learning_row) tuple from classify."""
    return classified[2] is None


def retry_entry(game, attempt):
    """A fresh manifest entry replaying game's index and map under a new unique game name."""
    original = game.get("retry_of") or game["game_name"]
    return {
        "index": game["index"],
        "game_name": f"{original}R{attempt}",
        "opponent": game["opponent"],
        "map": game["map"],
        "retry_of": original,
        "retry": attempt,
    }


def mark_retried(attempt):
    attempt["retried"] = True


def play_index(game, play_fn, max_retries, stopped=lambda: False, on_retry=mark_retried):
    """Play game, then replay the same index while it leaves no learning row, up to max_retries times.

    play_fn(entry) plays one attempt and returns its classify tuple. Returns every attempt in play order;
    on_retry(attempt) marks an attempt that is followed by a retry; callers sharing a manifest pass a locked marker."""
    attempts = []
    attempt = game
    while True:
        attempts.append(attempt)
        classified = play_fn(attempt)
        if not needs_retry(classified) or len(attempts) > max_retries or stopped():
            return attempts
        on_retry(attempt)
        attempt = retry_entry(game, len(attempts))


def next_failure_count(current, attempts):
    """Consecutive game indexes whose final attempt launched no game. A retried index counts once,
    so retries alone cannot reach the abort threshold, while indexes that keep failing still do."""
    return current + 1 if attempts[-1].get("outcome") == "NO_RESULT" else 0


def final_attempts(games):
    """One entry per game index: its last attempt, ordered by index."""
    last = {}
    for g in games:
        last[g["index"]] = g
    return [last[i] for i in sorted(last)]


def retry_count(games):
    return sum(1 for g in games if g.get("retry"))


def manifest_path(run_id):
    return BATCHES_DIR / f"{run_id}.json"


def log_path(run_id):
    return BATCHES_DIR / f"{run_id}.log"


def load_manifest(run_id):
    with open(manifest_path(run_id), encoding="utf-8") as f:
        return json.load(f)


def save_manifest(manifest):
    """Write the manifest atomically, tolerating another process holding it open.

    On Windows os.replace fails while any other process has the destination open, as a dashboard or
    sitrep polling the manifest does. Every save writes the whole manifest, so a save that still cannot
    land after retrying is skipped with a warning and the next save carries its state. Raising instead
    would record a finished game as NO_RESULT and end the worker thread that tried to save it.
    """
    BATCHES_DIR.mkdir(parents=True, exist_ok=True)
    path = manifest_path(manifest["run_id"])
    tmp = path.with_suffix(".json.tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
    for _ in range(MANIFEST_REPLACE_ATTEMPTS):
        try:
            os.replace(tmp, path)
            return
        except PermissionError:
            time.sleep(MANIFEST_REPLACE_BACKOFF_S)
    print(f"warning: {path.name} is held open by another process; this save is deferred to the next one",
          file=sys.stderr, flush=True)


def latest_run_id():
    if not BATCHES_DIR.is_dir():
        return None
    ids = sorted(p.stem for p in BATCHES_DIR.glob("*.json"))
    return ids[-1] if ids else None


def resolve_run_id(arg):
    if arg in (None, "latest"):
        run_id = latest_run_id()
        if run_id is None:
            raise SystemExit(f"No batches found in {BATCHES_DIR}")
        return run_id
    if not manifest_path(arg).is_file():
        raise SystemExit(f"No manifest for run '{arg}' in {BATCHES_DIR}")
    return arg


def resolve_opponent(name):
    if (BOTS_DIR / name).is_dir():
        return name
    lowered = name.lower()
    for p in BOTS_DIR.iterdir():
        if p.is_dir() and p.name.lower() == lowered:
            return p.name
    raise SystemExit(f"Opponent '{name}' not found under {BOTS_DIR}")


def opponent_race(name):
    try:
        with open(BOTS_DIR / name / "bot.json", encoding="utf-8") as f:
            return json.load(f).get("race", "Unknown")
    except (OSError, ValueError):
        return "Unknown"


def load_maps(path):
    with open(path, encoding="utf-8") as f:
        maps = [line.strip() for line in f if line.strip() and not line.startswith("#")]
    if not maps:
        raise SystemExit(f"No maps listed in {path}")
    return maps


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def jar_version(path):
    m = re.search(r"InfestedArtosis-([\d.]+)-jar-with-dependencies\.jar$", Path(path).name)
    return m.group(1) if m else None


def pom_version():
    try:
        with open(REPO_ROOT / "pom.xml", encoding="utf-8") as f:
            for line in f:
                m = re.search(r"<version>([^<]+)</version>", line)
                if m:
                    return m.group(1)
    except OSError:
        pass
    return None


def built_jar():
    jars = sorted((REPO_ROOT / "target").glob("*-jar-with-dependencies.jar"), key=lambda p: p.stat().st_mtime)
    return jars[-1] if jars else None


def git_rev():
    try:
        out = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=REPO_ROOT,
                             capture_output=True, text=True, check=True)
        return out.stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def game_dir(game_name):
    return GAMES_DIR / f"GAME_{game_name}"


def read_json(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def iter_csv_rows(path):
    """Stream a CSV as dicts. A missing, truncated or malformed file yields nothing rather than raising:
    a batch is full of games the bot did not survive, and one of them must not abort a whole collection."""
    try:
        with open(path, newline="", encoding="utf-8", errors="replace") as f:
            for row in csv.DictReader(f):
                yield row
    except (OSError, csv.Error):
        return


def read_csv_rows(path):
    return list(iter_csv_rows(path))


def learning_csvs(write_dir):
    """Learning CSVs in a game's write dir. The bot's telemetry files share the dir and are excluded by prefix."""
    if not write_dir.is_dir():
        return []
    return sorted(p for p in write_dir.glob("*.csv") if not p.name.startswith(TELEMETRY_PREFIX))


def telemetry_file(write_dir, name):
    """Path to one telemetry file, or None when it was never written.

    PlanEventLogger stamps its file names with a wall-clock time, so those are matched by glob and the
    newest wins. A stamped name sorts by its own timestamp.
    """
    if not write_dir.is_dir():
        return None
    if "*" in name:
        matches = sorted(write_dir.glob(name))
        return matches[-1] if matches else None
    path = write_dir / name
    return path if path.is_file() else None


def read_dir_csvs(opponent):
    """Learning CSVs for an opponent in the bot's read dir. Random opponents get one file per race seen."""
    return sorted((BOTS_DIR / BOT_NAME / "read").glob(f"{opponent}_*.csv"))


def read_dir_row_count(opponent):
    return sum(len(read_csv_rows(p)) for p in read_dir_csvs(opponent))


def deployed_jars():
    return sorted((BOTS_DIR / BOT_NAME / "AI").glob("*.jar"))


def deploy_jar(jar):
    ai_dir = BOTS_DIR / BOT_NAME / "AI"
    ai_dir.mkdir(parents=True, exist_ok=True)
    for old in deployed_jars():
        old.unlink()
    shutil.copyfile(jar, ai_dir / jar.name)
    return ai_dir / jar.name


def set_java_opts(opts):
    """Patch the deployed bot.json. sc-docker forwards javaOpts as JAVA_OPTS, the only route for -D flags
    into a container game: the bot's .env lives on the host and is never mounted."""
    path = BOTS_DIR / BOT_NAME / "bot.json"
    with open(path, encoding="utf-8") as f:
        meta = json.load(f)
    meta["javaOpts"] = opts
    with open(path, "w", encoding="utf-8") as f:
        json.dump(meta, f)
    return path


def deployed_java_opts():
    """javaOpts from the deployed bot.json, the only route for -D flags into a container game."""
    meta = read_json(BOTS_DIR / BOT_NAME / "bot.json") or {}
    return meta.get("javaOpts") or ""


def parse_runtime_flags(java_opts):
    """The bot's IA_ settings from a javaOpts string, so a manifest records what a batch actually ran with."""
    return dict(re.findall(r"-D(IA_[A-Z0-9_]+)=(\S+)", java_opts or ""))


def runtime_flag(manifest, name):
    """Tri-state: True or False once a manifest records its runtime flags, None for a batch launched before
    they were recorded, where nothing on disk says whether the flag was set."""
    flags = manifest.get("runtime_flags")
    if flags is None:
        return None
    return str(flags.get(name, "")).strip().lower() == "true"


def jvm_died(gdir):
    """True when the bot's log shows the JVM died mid-game.

    A JVM death is invisible in result.json: StarCraft itself exits normally, so the game is scored as
    an ordinary loss even though the bot stopped playing partway through and never wrote a learning row.
    crashes_0/ stays empty because the watchdog follows the StarCraft process, not the JVM.
    """
    log = gdir / "logs_0" / "bot.log"
    try:
        with open(log, encoding="utf-8", errors="replace") as f:
            return any(JVM_DEATH_MARKER in line for line in f)
    except OSError:
        return False


def set_games_dir(path):
    """Point game_dir at an archived games directory so read-side tools work on runs moved off the scbw root."""
    global GAMES_DIR
    GAMES_DIR = Path(path)


def last_frame(frames_csv):
    """Last frame_count written to a frames.csv, or None for a missing, empty or header-only file."""
    try:
        with open(frames_csv, "rb") as f:
            lines = f.read().splitlines()
        return int(lines[-1].split(b",")[0]) if len(lines) > 1 else None
    except (OSError, ValueError):
        return None


def bot_log_ended_cleanly(gdir):
    """True when our bot.log carries the wrapper's end line, False when it does not, None without a log."""
    try:
        with open(gdir / "logs_0" / "bot.log", encoding="utf-8", errors="replace") as f:
            return any(BOT_EXITED_MARKER in line for line in f)
    except OSError:
        return None


def is_frame_cap_stalemate(gdir):
    """True when our bot played to the frame cap: a game neither side could finish."""
    frame = last_frame(gdir / "logs_0" / "frames.csv")
    return frame is not None and frame >= FRAME_CAP_FRAMES


def run_stopped_by_log(run_id, text):
    """True when a run.py stdout log carries a stopped end line for run_id, as stop_rule.sh writes."""
    return re.search(rf"^Batch {re.escape(run_id)} stopped", text, re.MULTILINE) is not None


def non_result_label(game, outcome, run_stopped=False):
    """Report-side label for a game that produced no WIN or LOSS, or None for a plain outcome.

    STOPPED: the manifest never recorded an outcome for the attempt, so run.py was killed while it was in
    flight; either the run is known stopped, or a result.json already exists that run.py never classified.
    STALEMATE: our frames.csv reached the frame cap.
    JVM_DIED: scored CRASH and our bot.log has no end line, or shows the JVM's uncaught-exception marker.
    The manifest outcome strings written by run.py are never changed.
    """
    if outcome in CONCLUSIVE:
        return None
    gdir = game_dir(game["game_name"])
    if "outcome" not in game and (run_stopped or (gdir / "result.json").is_file()):
        return LABEL_STOPPED
    if is_frame_cap_stalemate(gdir):
        return LABEL_STALEMATE
    if outcome == "CRASH" and (jvm_died(gdir) or bot_log_ended_cleanly(gdir) is False):
        return LABEL_JVM_DIED
    return None


def classify(game):
    """Return (outcome, game_time, learning_row) for one manifest game entry."""
    gdir = game_dir(game["game_name"])
    result = read_json(gdir / "result.json")
    if result is None:
        return "NO_RESULT", None, None

    csvs = learning_csvs(gdir / "write_0")
    rows = read_csv_rows(csvs[0]) if csvs else []
    learning_row = rows[-1] if rows else None

    game_time = result.get("game_time")
    if result.get("is_realtime_outed"):
        started = (gdir / "logs_0" / "frames.csv").is_file()
        return ("TIMEOUT" if started else "STALL"), game_time, learning_row

    scores = read_json(gdir / "logs_0" / "scores.json") or {}
    if result.get("is_crashed"):
        if scores and not scores.get("is_crashed") and not scores.get("is_nostart"):
            return "DRAW", game_time, learning_row
        return "CRASH", game_time, learning_row

    if jvm_died(gdir):
        return "CRASH", game_time, learning_row

    winner = (result.get("winner") or "").lower()
    if winner and BOT_NAME.lower() in winner:
        return "WIN", game_time, learning_row
    loser = (result.get("loser") or "").lower()
    if loser and BOT_NAME.lower() in loser:
        return "LOSS", game_time, learning_row
    return "DRAW", game_time, learning_row


def fmt_game_time(seconds):
    if seconds is None:
        return "--:--"
    seconds = int(seconds)
    return f"{seconds // 60}:{seconds % 60:02d}"


def ensure_docker_network(name="sc_net", subnet="172.18.0.0/16"):
    out = subprocess.run(["docker", "network", "ls", "--format", "{{.Name}}"], capture_output=True, text=True, check=True)
    if name in out.stdout.split():
        return False
    created = subprocess.run(["docker", "network", "create", "--subnet", subnet, name], capture_output=True, text=True)
    if created.returncode != 0:
        subprocess.run(["docker", "network", "create", name], capture_output=True, check=True)
    return True


def docker_game_containers(prefix="GAME_", running_only=False):
    cmd = ["docker", "ps", "--format", "{{.Names}}", "--filter", f"name={prefix}"]
    if not running_only:
        cmd.insert(2, "-a")
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, check=True)
        return [c for c in out.stdout.split() if c]
    except (OSError, subprocess.CalledProcessError):
        return []


def running_game_containers(prefix="GAME_"):
    return docker_game_containers(prefix, running_only=True)


def kill_game_containers(prefix="GAME_"):
    names = docker_game_containers(prefix)
    if names:
        subprocess.run(["docker", "rm", "-f"] + names, capture_output=True)
    return names


def remove_exited_game_containers(prefix="GAME_"):
    running = set(running_game_containers(prefix))
    exited = [c for c in docker_game_containers(prefix) if c not in running]
    if exited:
        subprocess.run(["docker", "rm", "-f"] + exited, capture_output=True)
    return exited
