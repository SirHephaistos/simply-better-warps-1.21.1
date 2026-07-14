# run_driver_test.py
# Python 3.10+
import os
import re
import time
import glob
import subprocess
import shutil

RUNS = 50         # number of runs
WAIT_SECONDS = 12   # seconds to wait before kill
GRADLEW = os.path.join(os.getcwd(), "gradlew.bat")
BASE_DIR = os.path.join(os.getcwd(), "testjdb")
RESULTS_FILE = os.path.join(BASE_DIR, "driver-test-results.txt")
LOG_PATTERN = os.path.join(BASE_DIR, "run-{:04d}.log")
DETECT_REGEX = re.compile(r"org[_-]xerial[_-]sqlite[-_]jdbc", re.IGNORECASE)


def kill_server_java():
    ps_cmd = r'''
Get-CimInstance Win32_Process |
  Where-Object { $_.Name -eq 'java.exe' -and ($_.CommandLine -match 'dev-launch-injector|knot') } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
'''
    subprocess.run([
        "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-Command", ps_cmd
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def main():
    # Ensure test directory exists
    os.makedirs(BASE_DIR, exist_ok=True)

    # Clean previous logs/results
    for f in glob.glob(os.path.join(BASE_DIR, "run-*.log")):
        try:
            os.remove(f)
        except:
            pass
    try:
        os.remove(RESULTS_FILE)
    except:
        pass

    # RUN LOOP
    for i in range(1, RUNS + 1):
        log_path = LOG_PATTERN.format(i)
        print(f"[{i}/{RUNS}] starting…")

        # reset Gradle per run
        subprocess.run([GRADLEW, "--stop"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        shutil.rmtree(".gradle", ignore_errors=True)

        with open(log_path, "w", encoding="utf-8", errors="ignore") as logf:
            args = ["cmd", "/c", GRADLEW, "runServer", "--no-daemon", "--console=plain",
                    "--refresh-dependencies", "--rerun-tasks", "-Dorg.gradle.caching=false"]
            proc = subprocess.Popen(args, stdout=logf, stderr=subprocess.STDOUT)

        time.sleep(WAIT_SECONDS)
        kill_server_java()
        try: proc.terminate()
        except: pass
        time.sleep(0.3)

    # PARSE RESULTS
    successes = 0
    failures = 0
    lines_out = []

    for i in range(1, RUNS + 1):
        log_path = LOG_PATTERN.format(i)
        found = False
        try:
            with open(log_path, "r", encoding="utf-8", errors="ignore") as f:
                if DETECT_REGEX.search(f.read()):
                    found = True
        except FileNotFoundError:
            found = False

        if found:
            lines_out.append(f"[run {i} / reussite]")
            successes += 1
        else:
            lines_out.append(f"[run {i} / echec]")
            failures += 1

    rate = (successes / RUNS * 100.0) if RUNS else 0.0
    lines_out.append("")
    lines_out.append(f"nombre de reussite: {successes}")
    lines_out.append(f"nombre d'echec: {failures}")
    lines_out.append(f"% de reussite: {rate:.2f}")

    with open(RESULTS_FILE, "w", encoding="utf-8") as rf:
        rf.write("\n".join(lines_out))

    print("----")
    print(f"Done. TRUE={successes} FALSE={failures} -> {RESULTS_FILE}")

    # Final cleanup of run logs
    for f in glob.glob(os.path.join(BASE_DIR, "run-*.log")):
        try:
            os.remove(f)
        except:
            pass


if __name__ == "__main__":
    main()
