"""Google Play edit API: preflight by default, draft upload behind explicit gates.

Credentials, request headers and HTTP error bodies must never enter logs.
"""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
from urllib.parse import quote

PACKAGE = "com.malfreyt.alexandre.hamigo"
CHANNEL_BRANCH = {"alpha": "alpha", "beta": "beta", "release": "main"}
API = "https://androidpublisher.googleapis.com/androidpublisher/v3"
UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3"


def validate_candidate(metadata, branch, track, tracks, bundles):
    if metadata["applicationId"] != PACKAGE or metadata["channel"] not in CHANNEL_BRANCH:
        raise ValueError("Only public Hamigo candidates can be uploaded")
    if CHANNEL_BRANCH[metadata["channel"]] != branch:
        raise ValueError("Channel and source branch do not match")
    if metadata["dirty"] or len(metadata["sourceRevision"]) != 40:
        raise ValueError("Candidate must come from a clean source commit")
    suffix = "" if metadata["channel"] == "release" else "-" + metadata["channel"] + "[1-9][0-9]*"
    if not re.fullmatch(r"[1-9][0-9]*\.[0-9]+(?:\.[0-9]+)?" + suffix, metadata["versionName"]):
        raise ValueError("Public version name does not match its channel")
    code = metadata["versionCode"]
    if code != metadata["releaseOrdinal"] * 10000 + metadata["buildNumber"] or not 56 < code <= 2100000000:
        raise ValueError("Invalid version code reservation")
    if track not in {item["track"] for item in tracks}:
        raise ValueError("Track not found: copy its exact ID from edits.tracks.list")
    if (metadata["channel"] == "release") != (track == "production"):
        raise ValueError("Production is reserved for the release channel")
    previous = [int(v) for item in tracks for release in item.get("releases", [])
                for v in release.get("versionCodes", [])]
    previous.extend(int(b["versionCode"]) for b in bundles)
    if previous and code <= max(previous):
        raise ValueError("Reserve a buildNumber above all uploaded candidates; promote the original AAB when reusing a code")


def require_upload_authorization():
    if os.environ.get("PLAY_UPLOAD_ENABLED") != "true" or os.environ.get("PLAY_FIRST_RELEASE_COMPLETED") != "true":
        raise ValueError("Uploads disabled until Console bootstrap and explicit activation are complete")


def request(session, method, url, **kwargs):
    response = session.request(method, url, timeout=300, **kwargs)
    if not response.ok:
        raise RuntimeError(f"Google Play request failed (HTTP {response.status_code}); inspect Console for details")
    return response.json() if response.content else {}


def publish(session, metadata, aab, branch, track, *, upload=False, status="draft", send_for_review=False):
    if upload:
        require_upload_authorization()
    if status not in ("draft", "completed") or (send_for_review and status == "draft"):
        raise ValueError("Invalid status/review combination")
    base = f"{API}/applications/{PACKAGE}/edits"
    edit = request(session, "POST", base, json={})["id"]
    endpoint = f"{base}/{quote(edit, safe='')}"
    committed = False
    try:
        tracks = request(session, "GET", endpoint + "/tracks").get("tracks", [])
        if metadata is None:
            return {"tracks": [item["track"] for item in tracks]}
        bundles = request(session, "GET", endpoint + "/bundles").get("bundles", [])
        validate_candidate(metadata, branch, track, tracks, bundles)
        with Path(aab).open("rb") as binary:
            digest = hashlib.file_digest(binary, "sha256").hexdigest()
        result = {"versionCode": metadata["versionCode"], "track": track,
                  "sha256": digest, "status": status, "uploaded": False}
        if not upload:
            return result
        target = next(item for item in tracks if item["track"] == track)
        if any(r.get("status") in ("draft", "inProgress", "halted") for r in target.get("releases", [])):
            raise ValueError("Target track has unfinished work; resolve it in Console first")
        with Path(aab).open("rb") as binary:
            bundle = request(session, "POST", f"{UPLOAD}/applications/{PACKAGE}/edits/{quote(edit, safe='')}/bundles",
                             params={"uploadType": "media"}, headers={"Content-Type": "application/octet-stream"}, data=binary)
        if int(bundle["versionCode"]) != metadata["versionCode"] or bundle.get("sha256", "").lower() != digest:
            raise ValueError("Uploaded bundle identity does not match candidate")
        release = {"name": metadata["versionName"], "versionCodes": [str(metadata["versionCode"])], "status": status}
        request(session, "PUT", endpoint + "/tracks/" + quote(track, safe=""), json={"track": track, "releases": [release]})
        request(session, "POST", endpoint + ":validate")
        request(session, "POST", endpoint + ":commit", params={"changesNotSentForReview": str(not send_for_review).lower(),
                "changesInReviewBehavior": "ERROR_IF_IN_REVIEW"})
        committed = True
        result["uploaded"] = True
        return result
    finally:
        if not committed:
            request(session, "DELETE", endpoint)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--list-tracks", action="store_true")
    parser.add_argument("--metadata", type=Path)
    parser.add_argument("--aab", type=Path)
    parser.add_argument("--track", default=os.environ.get("PLAY_TRACK", ""))
    parser.add_argument("--branch", default=os.environ.get("GITHUB_REF_NAME", ""))
    parser.add_argument("--upload", action="store_true")
    parser.add_argument("--status", choices=("draft", "completed"), default="draft")
    parser.add_argument("--send-for-review", action="store_true")
    args = parser.parse_args()
    if args.list_tracks and (args.upload or args.metadata or args.aab):
        parser.error("--list-tracks cannot be combined with a candidate or upload")
    if not args.list_tracks and (not args.metadata or not args.aab or not args.track):
        parser.error("--metadata, --aab and --track are required")
    from google.auth import default
    from google.auth.transport.requests import AuthorizedSession
    credentials, _ = default(scopes=["https://www.googleapis.com/auth/androidpublisher"])
    metadata = None if args.list_tracks else json.loads(args.metadata.read_text(encoding="utf-8"))
    with AuthorizedSession(credentials) as session:
        print(json.dumps(publish(session, metadata, args.aab, args.branch, args.track,
                                 upload=args.upload, status=args.status, send_for_review=args.send_for_review)))


if __name__ == "__main__":
    main()
