"""Validate an immutable release tag against the durable version and channel branches."""
import argparse
import json
import os
import re
import subprocess
from pathlib import Path


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def parse_tag(tag, product):
    match = re.fullmatch(r"v([1-9][0-9]*\.[0-9]+(?:\.[0-9]+)?)(?:-(alpha|beta)([1-9][0-9]*))?", tag)
    if not match or match[1] != product:
        raise ValueError("Release tag must match version.properties (v1.0, v1.0-alpha1, v1.0-beta1)")
    channel = match[2] or "release"
    return {"tag": tag, "product": product, "channel": channel,
            "branch": "main" if channel == "release" else channel,
            "prerelease": int(match[3] or 1), "versionBranch": "v" + product}


def check(tag, product):
    if git("rev-parse", "--is-shallow-repository") != "false":
        raise ValueError("Fetch the complete Git history")
    if git("status", "--porcelain"):
        raise ValueError("Source checkout is dirty")
    result = validate_tag(tag, product)
    if result["sourceRevision"] != git("rev-parse", "HEAD"):
        raise ValueError("Release tag does not point at the source commit")
    return result


def validate_tag(tag, product):
    """Validate an archived source ref without pretending that it is the current checkout."""
    result = parse_tag(tag, product)
    if git("rev-parse", "--is-shallow-repository") != "false":
        raise ValueError("Fetch the complete Git history")
    # actions/checkout replaces an annotated event tag with its peeled commit.
    # Restore only this local ref from the authoritative remote before validating it.
    git("fetch", "--no-tags", "origin", "+refs/tags/" + tag + ":refs/tags/" + tag)
    head = git("rev-parse", "refs/tags/" + tag + "^{commit}")
    if git("cat-file", "-t", "refs/tags/" + tag) != "tag":
        raise ValueError("Use an annotated release tag")
    git("merge-base", "--is-ancestor", head, "refs/remotes/origin/" + result["branch"])
    version_ref = "refs/remotes/origin/" + result["versionBranch"]
    if result["channel"] == "release":
        if git("rev-parse", "refs/tags/" + tag + "^{tree}") != git("rev-parse", version_ref + "^{tree}"):
            raise ValueError("Final squash must preserve exactly the frozen version branch tree")
    else:
        git("merge-base", "--is-ancestor", head, version_ref)
    result["sourceRevision"] = head
    return result


def properties():
    return dict(line.strip().split("=", 1) for line in Path("version.properties").read_text(encoding="utf-8").splitlines()
                if "=" in line and not line.lstrip().startswith("#"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", default=os.environ.get("GITHUB_REF_NAME", ""))
    args = parser.parse_args()
    checked = check(args.tag, properties()["version"])
    print(json.dumps(checked))
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            for key in ("channel", "branch", "prerelease"):
                output.write(f"{key}={checked[key]}\n")
