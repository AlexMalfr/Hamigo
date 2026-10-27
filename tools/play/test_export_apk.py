import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import export_apk


CERT = "a" * 64
META = {"applicationId": export_apk.PACKAGE, "versionName": "1.1-beta2", "versionCode": 61,
        "buildNumber": 61, "sourceRevision": "b" * 40, "dirty": False, "channel": "beta"}


class Response:
    def __init__(self, body=None, status=200):
        self.body = body
        self.status_code = status
        self.ok = status == 200
    def json(self): return self.body
    def iter_content(self, **kwargs): return iter([b"signed-apk-fixture"])
    def close(self): pass


class Session:
    def __init__(self, *responses): self.responses = iter(responses); self.calls = []
    def get(self, url, **kwargs):
        self.calls.append((url, kwargs))
        return next(self.responses)


class ExportTests(unittest.TestCase):
    def listing(self, cert=CERT):
        return {"generatedApks": [{"certificateSha256Hash": cert,
                                  "generatedUniversalApk": {"downloadId": "opaque/id"}}]}

    def test_only_unambiguous_historical_signer_is_selected(self):
        self.assertIsNone(export_apk.select_universal(self.listing("c" * 64), CERT))
        self.assertIsNone(export_apk.select_universal({"generatedApks": [{"certificateSha256Hash": CERT}]}, CERT))
        self.assertEqual(export_apk.select_universal(self.listing(), CERT), "opaque/id")
        duplicate = self.listing(); duplicate["generatedApks"] *= 2
        with self.assertRaises(ValueError): export_apk.select_universal(duplicate, CERT)

    def test_version_and_signer_are_verified_from_the_actual_apk(self):
        manifest = "package: name='" + export_apk.PACKAGE + "' versionCode='61' versionName='1.1-beta2'\n"
        def check(signer=CERT, badging=manifest):
            with patch.object(export_apk, "android_tool", lambda name: name), patch.object(
                    export_apk.subprocess, "check_output", side_effect=[
                        "Signer #1 certificate SHA-256 digest: " + signer + "\n", badging]):
                export_apk.verify_apk(Path("fixture.apk"), META, CERT)
        check()
        with self.assertRaisesRegex(ValueError, "signer differs"): check("c" * 64)
        for old, new in (("versionCode='61'", "versionCode='60'"),
                         ("versionName='1.1-beta2'", "versionName='1.1'"),
                         (export_apk.PACKAGE, export_apk.PACKAGE + ".dev")):
            with self.assertRaisesRegex(ValueError, "package or version differs"):
                check(badging=manifest.replace(old, new))

    def test_export_waits_for_generation_and_archives_verified_bytes(self):
        session = Session(Response(status=404), Response(self.listing()), Response())
        with tempfile.TemporaryDirectory() as directory, patch.object(export_apk, "verify_apk") as verify:
            root = Path(directory)
            result = export_apk.export(session, META, root, CERT, attempts=2, delay=0)
            verify.assert_called_once()
            self.assertEqual((root / result["file"]).read_bytes(), b"signed-apk-fixture")
            self.assertEqual(result["sha256"], hashlib.sha256(b"signed-apk-fixture").hexdigest())
            self.assertEqual(json.loads((root / "APK-PROVENANCE.json").read_text()), result)
            self.assertIn("opaque%2Fid:download", session.calls[-1][0])
            self.assertTrue(all("/edits" not in call[0] for call in session.calls))

    def test_invalid_candidates_and_failed_verification_leave_no_apk(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for changes in ({"applicationId": export_apk.PACKAGE + ".dev"}, {"dirty": True},
                            {"versionCode": 56}, {"channel": "release"}, {"sourceRevision": "local"}):
                with self.assertRaises(ValueError):
                    export_apk.export(Session(), META | changes, root, CERT, attempts=1)
            with self.assertRaises(RuntimeError):
                export_apk.export(Session(Response(self.listing("c" * 64))), META, root, CERT, attempts=1)
            with patch.object(export_apk, "verify_apk", side_effect=ValueError("bad actual APK")):
                with self.assertRaisesRegex(ValueError, "bad actual APK"):
                    export_apk.export(Session(Response(self.listing()), Response()), META, root, CERT, attempts=1)
            self.assertEqual(list(root.iterdir()), [])


if __name__ == "__main__": unittest.main()
