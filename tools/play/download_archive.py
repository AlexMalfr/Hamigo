"""Retrieve published release assets, or the original CI artifact for a draft release."""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile
from check_source import git


def select_artifact(artifacts, tag, revision):
    matches = [item for item in artifacts
               if item["name"] == "release-" + tag and not item["expired"]
               and item["workflow_run"]["head_sha"] == revision]
    if len(matches) != 1:
        raise ValueError("Expected one unexpired CI archive for the exact release source")
    return matches[0]


def extract_files(archive, target, names):
    # Read only expected root entries; never extract arbitrary ZIP paths or overwrite duplicates.
    with zipfile.ZipFile(archive) as bundle:
        for name in names:
            if bundle.namelist().count(name) != 1:
                raise ValueError("Missing or duplicate archived file: " + name)
        for name in names:
            with bundle.open(name) as source, (target / name).open("wb") as destination:
                shutil.copyfileobj(source, destination)


def download(repo, tag, folder):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
        raise ValueError("Expected owner/repository")
    if not re.fullmatch(r"v[1-9][0-9]*\.[0-9]+(?:\.[0-9]+)?(?:-(?:alpha|beta)[1-9][0-9]*)?", tag):
        raise ValueError("Expected a modern release tag")
    revision = git("rev-parse", "refs/tags/" + tag + "^{commit}")
    names = ("Hamigo-" + tag + ".aab", "version.json", "SHA256SUMS.txt")
    folder.mkdir(parents=True, exist_ok=True)
    if any((folder / name).exists() for name in names):
        raise ValueError("Use an empty download destination")
    with tempfile.TemporaryDirectory() as temporary:
        staging = Path(temporary)
        command = ["gh", "release", "download", tag, "--repo", repo, "--dir", str(staging)]
        for name in names:
            command.extend(["--pattern", name])
        release = subprocess.run(command, capture_output=True, text=True)
        provenance = {"source": "github-release", "tag": tag, "sourceRevision": revision}
        if release.returncode != 0:
            # A read-only GITHUB_TOKEN cannot see drafts. Keep its permissions read-only.
            pages = subprocess.check_output([
                "gh", "api", "--method", "GET", "repos/" + repo + "/actions/artifacts",
                "-f", "name=release-" + tag, "-F", "per_page=100", "--paginate", "--slurp"
            ], text=True)
            artifact = select_artifact([item for page in json.loads(pages)
                                        for item in page["artifacts"]], tag, revision)
            archive = staging / "artifact.zip"
            with archive.open("wb") as destination:
                subprocess.run(["gh", "api", "repos/" + repo + "/actions/artifacts/"
                                + str(artifact["id"]) + "/zip"], stdout=destination, check=True)
            extract_files(archive, staging, names)
            provenance.update(source="actions-artifact", artifactId=artifact["id"],
                              runId=artifact["workflow_run"]["id"])
        for name in names:
            shutil.copyfile(staging / name, folder / name)
    return provenance


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--folder", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(download(args.repo, args.tag, args.folder)))
