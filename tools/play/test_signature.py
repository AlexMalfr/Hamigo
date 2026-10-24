"""Exercise the real JDK signer against temporary JARs, never the Android release key."""
import contextlib
import hashlib
import io
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import verify_signature


@unittest.skipUnless(shutil.which("jarsigner") and shutil.which("keytool"), "JDK signer tools required")
class SignatureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temporary.name)
        cls.environment = os.environ | {"HAMIGO_FIXTURE_KEY_PASSWORD": "test-fixture-only"}
        cls.key = cls.root / "fixture.p12"
        # This short-lived fixture never signs an APK or AAB and is discarded with the test.
        cls.command("keytool", "-genkeypair", "-alias", "fixture", "-keystore", str(cls.key),
                    "-storetype", "PKCS12", "-storepass:env", "HAMIGO_FIXTURE_KEY_PASSWORD",
                    "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=Hamigo test fixture")
        certificate = cls.root / "fixture.der"
        cls.command("keytool", "-exportcert", "-alias", "fixture", "-keystore", str(cls.key),
                    "-storepass:env", "HAMIGO_FIXTURE_KEY_PASSWORD", "-file", str(certificate))
        cls.fingerprint = hashlib.sha256(certificate.read_bytes()).hexdigest()
        cls.signed = cls.root / "signed.jar"
        with zipfile.ZipFile(cls.signed, "w") as jar:
            jar.writestr("fixture.txt", "original")
        cls.command("jarsigner", "-keystore", str(cls.key), "-storepass:env", "HAMIGO_FIXTURE_KEY_PASSWORD",
                    str(cls.signed), "fixture")

    @classmethod
    def command(cls, *args):
        return subprocess.run(args, env=cls.environment, capture_output=True, text=True, check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def test_real_signature_accepts_only_its_configured_certificate(self):
        with contextlib.redirect_stdout(io.StringIO()):
            verify_signature.verify(self.signed, self.fingerprint)
        with self.assertRaises(ValueError):
            verify_signature.verify(self.signed, "0" * 64)

    def test_unsigned_archive_is_rejected(self):
        unsigned = self.root / "unsigned.jar"
        with zipfile.ZipFile(unsigned, "w") as jar:
            jar.writestr("fixture.txt", "unsigned")
        with self.assertRaises(ValueError):
            verify_signature.verify(unsigned, self.fingerprint)

    def test_modified_signed_content_is_rejected(self):
        tampered = self.root / "tampered.jar"
        with zipfile.ZipFile(self.signed) as original, zipfile.ZipFile(tampered, "w") as changed:
            for name in original.namelist():
                changed.writestr(name, "modified" if name == "fixture.txt" else original.read(name))
        with self.assertRaises((ValueError, subprocess.CalledProcessError)):
            verify_signature.verify(tampered, self.fingerprint)

    def test_missing_certificate_configuration_fails_before_running_tools(self):
        with patch.object(verify_signature.subprocess, "run", side_effect=AssertionError("must not run")):
            with self.assertRaises(ValueError):
                verify_signature.verify(self.signed, "")


if __name__ == "__main__": unittest.main()
