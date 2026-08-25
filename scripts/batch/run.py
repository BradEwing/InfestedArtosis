"""Launch N games of Infested Artosis vs one or more opponents through sc-docker.

Usage:
  py scripts/batch/run.py <opponent> [<opponent>...] [-n 100] [--jobs 1] [--frozen]

Default mode accumulates learning (--read_overwrite) and runs at most one
concurrent game per opponent, because parallel games vs the same opponent
overwrite each other's learning CSV. --frozen disables --read_overwrite so
games may run fully in parallel against a fixed learning snapshot.
"""

import argparse
import queue
import subprocess
import sys
import threading
import time
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import batchlib as bl
import report
from scbw.player import BotPlayer

stop_requested = threading.Event()
manifest_lock = threading.Lock()
launch_failures = [0]
MAX_CONSECUTIVE_LAUNCH_FAILURES = 3


def positive_int(value):
    value = int(value)
    if value <= 0:
        raise argparse.ArgumentTypeError("must be greater than zero")
    return value


def parse_args():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("opponents", nargs="*", help="opponent bot names (as under %%APPDATA%%/scbw/bots)")
    p.add_argument("--opponents-file", help="file with one opponent name per line")
    p.add_argument("-n", "--games", type=int, default=100, help="games per opponent (default 100)")
    p.add_argument("--jobs", type=positive_int, default=1, help="concurrent games (default 1)")
    p.add_argument("--frozen", action="store_true", help="do not write learning back (omit --read_overwrite)")
    p.add_argument("--maps", default=str(bl.DEFAULT_MAPS_FILE), help="map list file")
    p.add_argument("--timeout", type=int, default=2400, help="wall-clock seconds per game")
    p.add_argument("--no-report", action="store_true", help="skip report at end")
    p.add_argument("--skip-jar-check", action="store_true", help="do not verify deployed jar matches target/")
    p.add_argument("--deploy-jar", action="store_true", help="replace the deployed jar with target/*-jar-with-dependencies.jar")
    return p.parse_args()


def collect_opponents(args):
    names = list(args.opponents)
    if args.opponents_file:
        with open(args.opponents_file, encoding="utf-8") as f:
            names += [line.strip() for line in f if line.strip() and not line.startswith("#")]
    if not names:
        raise SystemExit("No opponents given")
    resolved = []
    for name in names:
        r = bl.resolve_opponent(name)
        if r not in resolved:
            resolved.append(r)
    return resolved


def deploy_jar():
    built = bl.built_jar()
    if built is None:
        raise SystemExit("No target/*-jar-with-dependencies.jar to deploy (build with mvn package)")
    print(f"Deploying {built.name} -> {bl.BOTS_DIR / bl.BOT_NAME / 'AI'}")
    bl.deploy_jar(built)


def check_jar(skip):
    built = bl.built_jar()
    jars = bl.deployed_jars()
    pom = bl.pom_version()
    if not jars:
        raise SystemExit(f"No jar deployed under {bl.BOTS_DIR / bl.BOT_NAME / 'AI'}")
    if len(jars) > 1:
        raise SystemExit(f"Multiple jars deployed ({', '.join(j.name for j in jars)}); scbw needs exactly one. "
                         "Pass --deploy-jar or delete the stale ones.")
    deployed = jars[0]
    deployed_sha = bl.sha256_file(deployed)
    deployed_ver = bl.jar_version(deployed)
    print(f"Deployed jar: {deployed.name} ({deployed_sha[:12]})")
    problems = []
    if built is None:
        problems.append("no target/*-jar-with-dependencies.jar found (build with mvn package)")
    elif bl.sha256_file(built) != deployed_sha:
        problems.append(f"deployed jar differs from {built.name}")
    if pom and deployed_ver and pom != deployed_ver:
        problems.append(f"deployed jar version {deployed_ver} != pom.xml version {pom}")
    if problems:
        for msg in problems:
            print(f"  WARNING: {msg}")
        if not skip:
            raise SystemExit("Jar is stale. Build (mvn package) and pass --deploy-jar, or pass --skip-jar-check.")
    return deployed_ver, deployed_sha


def preflight(args, opponents, maps):
    if sys.version_info[:2] != (3, 11):
        print(f"WARNING: Python {sys.version_info.major}.{sys.version_info.minor}; scbw expects 3.11")
    try:
        subprocess.run(["docker", "ps"], capture_output=True, check=True)
    except (OSError, subprocess.CalledProcessError):
        raise SystemExit("docker is not available; start Docker Desktop")
    if not bl.MAPS_DIR.is_dir():
        raise SystemExit(f"Map dir missing: {bl.MAPS_DIR}")
    missing = [m for m in maps if not (bl.MAPS_DIR / m).is_file()]
    if missing:
        raise SystemExit(f"Maps not found under {bl.MAPS_DIR}: {', '.join(missing)}")
    for bot_name in (bl.BOT_NAME, *opponents):
        try:
            BotPlayer(str(bl.BOTS_DIR / bot_name))
        except Exception as e:
            raise SystemExit(f"Bot '{bot_name}' is not launchable by scbw: {e}") from e
    if args.jobs > 1 and len(opponents) == 1 and not args.frozen:
        raise SystemExit("--jobs > 1 vs a single opponent requires --frozen "
                         "(parallel games overwrite each other's learning file)")
    if bl.ensure_docker_network():
        print("Created docker network sc_net (scbw only creates it on --install)")
    running = bl.running_game_containers()
    if running:
        raise SystemExit("Game containers are already running (another batch or scbw.play in progress): "
                         f"{' '.join(running)}. Wait for it or docker rm -f them first.")
    exited = bl.remove_exited_game_containers()
    if exited:
        print(f"Removed exited game containers: {' '.join(exited)}")
    if args.deploy_jar:
        deploy_jar()
    return check_jar(args.skip_jar_check)


def build_games(run_id, opponents, n, maps):
    games = []
    tag = bl.game_name_tag()
    i = 0
    for opp in opponents:
        for _ in range(n):
            games.append({
                "index": i,
                "game_name": bl.game_name(tag, i),
                "opponent": opp,
                "map": maps[i % len(maps)],
            })
            i += 1
    return games


def scbw_command(game, args, frozen):
    cmd = bl.scbw_play_command() + [
        "--docker_image", bl.DOCKER_IMAGE,
        "--bots", bl.BOT_NAME, game["opponent"],
        "--headless",
        "--game_speed", "0",
        "--timeout", str(args.timeout),
        "--map_dir", str(bl.MAPS_DIR),
        "--map", game["map"],
        "--game_name", game["game_name"],
    ]
    if not frozen:
        cmd.append("--read_overwrite")
    return cmd


def play_one(game, args, manifest, total, log_file):
    game["launched_at"] = datetime.now().isoformat(timespec="seconds")
    with manifest_lock:
        manifest["games"].append(game)
        bl.save_manifest(manifest)
    cmd = scbw_command(game, args, manifest["frozen"])
    started = time.time()
    proc = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    with manifest_lock:
        log_file.write(f"\n===== {game['game_name']} vs {game['opponent']} on {game['map']} =====\n")
        log_file.write(proc.stdout)
        log_file.write(proc.stderr)
        log_file.flush()
    outcome, game_time, _ = bl.classify(game)
    with manifest_lock:
        game["scbw_exit_code"] = proc.returncode
        game["outcome"] = outcome
        game["finished_at"] = datetime.now().isoformat(timespec="seconds")
        bl.save_manifest(manifest)
    elapsed = int(time.time() - started)
    print(f"[{game['index'] + 1}/{total}] vs {game['opponent']} on {game['map']} -> {outcome} "
          f"(game {bl.fmt_game_time(game_time)}, wall {elapsed}s)", flush=True)
    if outcome == "NO_RESULT":
        tail = "\n".join(proc.stderr.strip().splitlines()[-3:])
        print(f"    scbw.play exit {proc.returncode}:\n    {tail}", flush=True)
    return outcome


def worker(q, sem, args, manifest, total, log_file):
    while not stop_requested.is_set():
        with sem:
            try:
                game = q.get_nowait()
            except queue.Empty:
                return
            try:
                outcome = play_one(game, args, manifest, total, log_file)
            except Exception as e:
                print(f"[{game['index'] + 1}/{total}] vs {game['opponent']} -> ERROR {e}", flush=True)
                outcome = "NO_RESULT"
                with manifest_lock:
                    game["outcome"] = outcome
                    game["error"] = str(e)
                    game["finished_at"] = datetime.now().isoformat(timespec="seconds")
                    bl.save_manifest(manifest)
            with manifest_lock:
                launch_failures[0] = launch_failures[0] + 1 if outcome == "NO_RESULT" else 0
                if launch_failures[0] >= MAX_CONSECUTIVE_LAUNCH_FAILURES and not stop_requested.is_set():
                    print(f"{MAX_CONSECUTIVE_LAUNCH_FAILURES} consecutive launch failures; aborting batch", flush=True)
                    stop_requested.set()


def build_workers(games, opponents, args, manifest, log_file):
    total = len(games)
    if args.jobs <= 0:
        raise ValueError("jobs must be greater than zero")
    sem = threading.Semaphore(args.jobs)
    if manifest["frozen"]:
        shared = queue.Queue()
        for g in games:
            shared.put(g)
        queues = [shared] * min(args.jobs, total)
    else:
        per_opp = {opp: queue.Queue() for opp in opponents}
        for g in games:
            per_opp[g["opponent"]].put(g)
        queues = list(per_opp.values())
    return [threading.Thread(target=worker, args=(q, sem, args, manifest, total, log_file), daemon=True)
            for q in queues]


def run_games(games, opponents, args, manifest, log_file):
    threads = build_workers(games, opponents, args, manifest, log_file)
    for t in threads:
        t.start()
    try:
        while any(t.is_alive() for t in threads):
            time.sleep(0.5)
    except KeyboardInterrupt:
        print("\nCtrl+C: no new games will launch; waiting for in-flight games...", flush=True)
        stop_requested.set()
        try:
            while any(t.is_alive() for t in threads):
                time.sleep(0.5)
        except KeyboardInterrupt:
            print("Second Ctrl+C: killing game containers", flush=True)
            bl.kill_game_containers(f"GAME_{manifest['run_id']}")
            for t in threads:
                t.join()


def main():
    stop_requested.clear()
    launch_failures[0] = 0
    args = parse_args()
    opponents = collect_opponents(args)
    maps = bl.load_maps(args.maps)
    jar_ver, jar_sha = preflight(args, opponents, maps)
    run_id = bl.now_id()
    games = build_games(run_id, opponents, args.games, maps)
    manifest = {
        "run_id": run_id,
        "started_at": datetime.now().isoformat(timespec="seconds"),
        "bot": bl.BOT_NAME,
        "opponents": opponents,
        "games_per_opponent": args.games,
        "jobs": args.jobs,
        "frozen": args.frozen,
        "timeout": args.timeout,
        "jar_version": jar_ver,
        "jar_sha256": jar_sha,
        "git_rev": bl.git_rev(),
        "maps": maps,
        "read_rows_before": {opp: bl.read_dir_row_count(opp) for opp in opponents},
        "games": [],
    }
    bl.save_manifest(manifest)
    mode = "frozen (no learning writes)" if args.frozen else "accumulate (--read_overwrite)"
    print(f"Batch {run_id}: {len(games)} games vs {', '.join(opponents)} | jobs={args.jobs} | {mode}")
    print(f"Manifest: {bl.manifest_path(run_id)}")
    print(f"Log:      {bl.log_path(run_id)}\n")
    with open(bl.log_path(run_id), "a", encoding="utf-8") as log_file:
        run_games(games, opponents, args, manifest, log_file)
    manifest["finished_at"] = datetime.now().isoformat(timespec="seconds")
    failed_games = [g for g in manifest["games"] if bl.classify(g)[0] == "NO_RESULT"]
    unlaunched = len(games) - len(manifest["games"])
    manifest["status"] = "failed" if failed_games or unlaunched else "completed"
    with manifest_lock:
        bl.save_manifest(manifest)
    print(f"\nBatch {run_id} {manifest['status']} "
          f"({len(manifest['games'])}/{len(games)} launched, "
          f"{len(manifest['games']) - len(failed_games)}/{len(games)} produced results)")
    if not args.no_report:
        print()
        report.report(run_id, tail=10, archive_mode="none")
    if manifest["status"] != "completed":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
