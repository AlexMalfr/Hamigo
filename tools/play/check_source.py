"""Check the expected squash ordinal before reserving a public CI build."""
import os
import subprocess


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def check(branch, ordinal):
    if branch not in ("main", "alpha", "beta"):
        raise ValueError("Public builds only run from main/alpha/beta")
    if git("rev-parse", "--is-shallow-repository") != "false":
        raise ValueError("Fetch the complete Git history")
    if git("status", "--porcelain"):
        raise ValueError("Source checkout is dirty")
    if branch == "main":
        expected = int(git("rev-list", "--first-parent", "--count", "HEAD"))
    else:
        git("merge-base", "--is-ancestor", "origin/main", "HEAD")
        expected = int(git("rev-list", "--first-parent", "--count", "origin/main")) + 1
    if int(ordinal) != expected:
        raise ValueError(f"releaseOrdinal must be {expected}; coordinate integration of the next squash")


if __name__ == "__main__":
    values = dict(line.strip().split("=", 1) for line in open("version.properties", encoding="utf-8")
                  if "=" in line and not line.lstrip().startswith("#"))
    check(os.environ["GITHUB_REF_NAME"], values["releaseOrdinal"])
