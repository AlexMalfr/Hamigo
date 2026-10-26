"""Atomically reserve one global Android versionCode per immutable release tag.

Reservations are annotated ci-build/N tags, kept forever. No source file is mutated.
The legacy public ceiling is 56. External/manual uploads must reserve here first.
"""
import argparse
import json
import os
import subprocess
from pathlib import Path
from check_source import check, properties

PREFIX = "ci-build/"
MAX_CODE = 2100000000


def run(*args, check=True):
    return subprocess.run(["git", *args], capture_output=True, text=True, check=check)


def choose(reservations, tag, revision, floor=56):
    floor = max(56, floor)
    existing = [(code, data) for code, data in reservations.items() if data["releaseTag"] == tag]
    if existing:
        if len(existing) != 1 or existing[0][1]["sourceRevision"] != revision:
            raise ValueError("Release tag was moved or has conflicting reservations; never rewrite a released tag")
        return existing[0][0], True
    code = max([floor, *reservations]) + 1
    if code > MAX_CODE:
        raise ValueError("Android versionCode limit reached")
    return code, False


def read_reservations(remote):
    # Never force-fetch a changed immutable reservation.
    run("fetch", remote, "refs/tags/ci-build/*:refs/tags/ci-build/*")
    result = {}
    for ref in run("for-each-ref", "--format=%(refname:strip=2)", "refs/tags/ci-build/").stdout.splitlines():
        suffix = ref.removeprefix(PREFIX)
        if not suffix.isdigit():
            raise ValueError("Malformed build reservation tag")
        try:
            data = json.loads(run("for-each-ref", "--format=%(contents)", "refs/tags/" + ref).stdout)
        except (ValueError, subprocess.CalledProcessError) as error:
            raise ValueError("Unreadable build reservation " + ref) from error
        if run("rev-parse", ref + "^{commit}").stdout.strip() != data["sourceRevision"]:
            raise ValueError("Reservation does not point at its recorded source")
        result[int(suffix)] = data
    return result


def reserve(tag, *, remote="origin", floor=56):
    source = check(tag, properties()["version"])
    for _ in range(5):
        code, reused = choose(read_reservations(remote), tag, source["sourceRevision"], floor)
        if reused:
            return code
        ref = PREFIX + str(code)
        message = json.dumps({"releaseTag": tag, "sourceRevision": source["sourceRevision"],
                              "runId": os.environ.get("GITHUB_RUN_ID", "local")}, sort_keys=True)
        run("-c", "user.name=Hamigo build reservation", "-c", "user.email=ci@users.noreply.github.com",
            "tag", "-a", ref, source["sourceRevision"], "-m", message)
        pushed = run("push", remote, "refs/tags/" + ref + ":refs/tags/" + ref, check=False)
        if pushed.returncode == 0:
            return code
        # This local tag was created by us and never accepted remotely. Fetch the winner next.
        run("tag", "-d", ref)
        if "already exists" not in pushed.stderr and "fetch first" not in pushed.stderr:
            raise RuntimeError("Could not push the reservation; check repository write permission")
    raise RuntimeError("Build reservation contention; rerun after the other release finishes")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", default=os.environ.get("GITHUB_REF_NAME", ""))
    parser.add_argument("--floor", type=int, default=56, help="Highest externally consumed Play code, if any")
    args = parser.parse_args()
    code = reserve(args.tag, floor=args.floor)
    print(json.dumps({"buildNumber": code, "releaseTag": args.tag}))
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write(f"build={code}\n")
