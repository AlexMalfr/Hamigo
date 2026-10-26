"""Verify the AAB signature and compare its signer with the configured upload certificate."""
import base64
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys


def verify(aab, expected):
    expected = expected.replace(":", "").lower()
    if not re.fullmatch("[0-9a-f]{64}", expected):
        raise ValueError("Set HAMIGO_UPLOAD_CERT_SHA256 to the existing upload certificate fingerprint")
    result = subprocess.run(["jarsigner", "-J-Duser.language=en", "-verify", str(aab)],
                            capture_output=True, text=True, check=True)
    if "jar verified." not in result.stdout.lower():
        raise ValueError("AAB is unsigned or its signature could not be verified")
    certs = subprocess.check_output(["keytool", "-printcert", "-jarfile", str(aab), "-rfc"], text=True)
    pem = re.search(r"-----BEGIN CERTIFICATE-----\s*(.*?)\s*-----END CERTIFICATE-----", certs, re.S)
    if pem is None:
        raise ValueError("No signer certificate found")
    fingerprint = hashlib.sha256(base64.b64decode(pem[1])).hexdigest()
    if fingerprint != expected:
        raise ValueError("AAB signer differs from the existing upload certificate")
    print("Verified AAB upload signer SHA-256: " + fingerprint)


if __name__ == "__main__": verify(Path(sys.argv[1]), os.environ["HAMIGO_UPLOAD_CERT_SHA256"])
