"""Verify archived AAB bytes, metadata and immutable source without rebuilding them."""
import argparse
import hashlib
import json
from pathlib import Path
from check_source import git, validate_tag


def verify(folder, tag):
    # Read the product at the release tag, not the maintenance workflow's checkout.
    raw = git("show", "refs/tags/" + tag + ":version.properties")
    props = dict(line.strip().split("=", 1) for line in raw.splitlines()
                 if "=" in line and not line.lstrip().startswith("#"))
    source = validate_tag(tag, props["version"])
    metadata = json.loads((folder / "version.json").read_text(encoding="utf-8"))
    code = metadata["versionCode"]
    suffix = "" if source["channel"] == "release" else "-" + source["channel"] + str(source["prerelease"])
    if (type(code) is not int or code <= 56 or metadata.get("buildNumber") != code
            or metadata.get("dirty") is not False
            or metadata.get("applicationId") != "com.malfreyt.alexandre.hamigo"
            or metadata.get("channel") != source["channel"]
            or metadata.get("versionName") != source["product"] + suffix
            or metadata.get("sourceRevision") != source["sourceRevision"]):
        raise ValueError("Archived metadata does not match the immutable release")
    if git("rev-parse", "refs/tags/ci-build/" + str(code) + "^{commit}") != source["sourceRevision"]:
        raise ValueError("Archived build number belongs to another source")
    sums = {}
    for line in (folder / "SHA256SUMS.txt").read_text(encoding="utf-8").splitlines():
        digest, name = line.split(maxsplit=1)
        sums[name.removeprefix("./")] = digest.lower()
    for name in ("Hamigo-" + tag + ".aab", "version.json"):
        actual = hashlib.sha256((folder / name).read_bytes()).hexdigest()
        if sums.get(name) != actual:
            raise ValueError("Archived hash differs: " + name)
    return {"tag": tag, "versionCode": code, "sourceRevision": source["sourceRevision"],
            "channel": source["channel"], "rebuilt": False}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--folder", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(verify(args.folder, args.tag)))
