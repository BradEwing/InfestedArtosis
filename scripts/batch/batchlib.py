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

SCBW_ROOT = Path(os.environ.get("APPDATA", Path.home() / "AppData" / "Roaming")) / "scbw"
BOTS_DIR = SCBW_ROOT / "bots"
GAMES_DIR = SCBW_ROOT / "games"
MAPS_DIR = SCBW_ROOT / "maps" / "sscai"
BATCHES_DIR = SCBW_ROOT / "batches"

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


def manifest_path(run_id):
    return BATCHES_DIR / f"{run_id}.json"


def log_path(run_id):
    return BATCHES_DIR / f"{run_id}.log"


def load_manifest(run_id):
    with open(manifest_path(run_id), encoding="utf-8") as f:
        return json.load(f)


def save_manifest(manifest):
    BATCHES_DIR.mkdir(parents=True, exist_ok=True)
    path = manifest_path(manifest["run_id"])
    tmp = path.with_suffix(".json.tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
    os.replace(tmp, path)


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


def read_csv_rows(path):
    try:
        with open(path, newline="", encoding="utf-8") as f:
            return list(csv.DictReader(f))
    except OSError:
        return []


def learning_csvs(write_dir):
    """Learning CSVs in a game's write dir. The bot's telemetry files share the dir and are excluded by prefix."""
    if not write_dir.is_dir():
        return []
    return sorted(p for p in write_dir.glob("*.csv") if not p.name.startswith(TELEMETRY_PREFIX))


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
