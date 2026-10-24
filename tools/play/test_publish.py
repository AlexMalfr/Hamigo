import copy
import hashlib
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import publish


def candidate(channel="beta", build=8):
    return {"applicationId": publish.PACKAGE, "channel": channel, "dirty": False,
            "sourceRevision": "a" * 40, "releaseOrdinal": 57, "buildNumber": build,
            "versionCode": 570000 + build, "versionName": "1.0" if channel == "release" else "1.0-" + channel + "2"}


TRACKS = [{"track": "production", "releases": [{"versionCodes": ["56"], "status": "completed"}]},
          {"track": "internal"}, {"track": "closed-hamigo"}]


class PlayTests(unittest.TestCase):
    def validate(self, meta=None, **kwargs):
        return publish.validate_candidate(meta or candidate(), kwargs.get("branch", "beta"),
               kwargs.get("track", "closed-hamigo"), kwargs.get("tracks", TRACKS), kwargs.get("bundles", []))

    def test_exact_track_ids_are_required(self):
        self.validate()
        for track in ("beta", "alpha", "qa", ""):
            with self.assertRaises(ValueError): self.validate(track=track)

    def test_no_dev_dirty_or_wrong_branch_candidate(self):
        for changes in ({"applicationId": publish.PACKAGE + ".dev"}, {"dirty": True},
                        {"channel": "dev"}, {"sourceRevision": "local"}):
            with self.assertRaises(ValueError): self.validate(candidate() | changes)
        with self.assertRaises(ValueError): self.validate(branch="main")

    def test_release_is_only_production_channel(self):
        with self.assertRaises(ValueError): self.validate(track="production")
        self.validate(candidate("release"), branch="main", track="production")
        with self.assertRaises(ValueError): self.validate(candidate("release"), branch="main")

    def test_all_tracks_and_bundles_prevent_reuse_or_downgrade(self):
        tracks = copy.deepcopy(TRACKS)
        tracks[1]["releases"] = [{"versionCodes": ["570009"]}]
        for args in ({"tracks": tracks}, {"bundles": [{"versionCode": 570008}]}):
            with self.assertRaises(ValueError): self.validate(**args)

    def test_corrupted_reservation_rejected(self):
        with self.assertRaises(ValueError): self.validate(candidate() | {"versionCode": 570009})
        for name in ("1.0", "1.0-dev8+a1b2c3d4", "1.0-beta2+a1b2c3d4"):
            with self.assertRaises(ValueError): self.validate(candidate() | {"versionName": name})

    def test_both_upload_gates_required(self):
        for values in ({}, {"PLAY_UPLOAD_ENABLED": "true"}, {"PLAY_FIRST_RELEASE_COMPLETED": "true"}):
            with patch.dict(os.environ, values, clear=True), self.assertRaises(ValueError):
                publish.require_upload_authorization()

    def test_preflight_deletes_edit_without_upload_or_track_change(self):
        calls = []
        def request(session, method, url, **kwargs):
            calls.append((method, url))
            if url.endswith("/edits"): return {"id": "fixture"}
            if url.endswith("/tracks"): return {"tracks": TRACKS}
            return {}
        with tempfile.TemporaryDirectory() as folder, patch.object(publish, "request", request):
            aab = Path(folder) / "fixture.aab"
            aab.write_bytes(b"fixture")
            result = publish.publish(None, candidate(), aab, "beta", "closed-hamigo")
        self.assertFalse(result["uploaded"])
        self.assertEqual(["POST", "GET", "GET", "DELETE"], [method for method, _ in calls])

    def test_failed_validation_discards_edit(self):
        calls = []
        def request(session, method, url, **kwargs):
            calls.append(method)
            return {"id": "fixture"} if method == "POST" else {"tracks": TRACKS}
        with patch.object(publish, "request", request), self.assertRaises(ValueError):
            publish.publish(None, candidate(), None, "wrong", "closed-hamigo")
        self.assertEqual("DELETE", calls[-1])

    def test_signed_bundle_uploaded_as_draft_and_existing_review_preserved(self):
        calls = []
        def request(session, method, url, **kwargs):
            calls.append((method, url, kwargs))
            if url.endswith("/edits"): return {"id": "fixture"}
            if method == "GET" and url.endswith("/tracks"): return {"tracks": TRACKS}
            if method == "POST" and url.endswith("/bundles"):
                return {"versionCode": 570008, "sha256": hashlib.sha256(b"fixture").hexdigest()}
            return {}
        gates = {"PLAY_UPLOAD_ENABLED": "true", "PLAY_FIRST_RELEASE_COMPLETED": "true"}
        with tempfile.TemporaryDirectory() as folder, patch.object(publish, "request", request), patch.dict(os.environ, gates):
            aab = Path(folder) / "fixture.aab"
            aab.write_bytes(b"fixture")
            result = publish.publish(None, candidate(), aab, "beta", "closed-hamigo", upload=True)
        self.assertTrue(result["uploaded"])
        update = next(kwargs["json"] for method, url, kwargs in calls if method == "PUT")
        self.assertEqual("draft", update["releases"][0]["status"])
        self.assertEqual(["570008"], update["releases"][0]["versionCodes"])
        commit = calls[-1]
        self.assertTrue(commit[1].endswith(":commit"))
        self.assertEqual({"changesNotSentForReview": "true", "changesInReviewBehavior": "ERROR_IF_IN_REVIEW"}, commit[2]["params"])

    def test_unfinished_track_cannot_be_overwritten(self):
        tracks = copy.deepcopy(TRACKS)
        tracks[-1]["releases"] = [{"status": "draft", "versionCodes": ["570007"]}]
        calls = []
        def request(session, method, url, **kwargs):
            calls.append((method, url))
            if url.endswith("/edits"): return {"id": "fixture"}
            if url.endswith("/tracks"): return {"tracks": tracks}
            return {}
        gates = {"PLAY_UPLOAD_ENABLED": "true", "PLAY_FIRST_RELEASE_COMPLETED": "true"}
        with tempfile.TemporaryDirectory() as folder, patch.object(publish, "request", request), patch.dict(os.environ, gates):
            aab = Path(folder) / "fixture.aab"
            aab.write_bytes(b"fixture")
            with self.assertRaises(ValueError):
                publish.publish(None, candidate(), aab, "beta", "closed-hamigo", upload=True)
        self.assertEqual("DELETE", calls[-1][0])
        self.assertFalse(any(method == "PUT" for method, _ in calls))


if __name__ == "__main__": unittest.main()
