"""Download and verify a Play-signed universal APK without accessing the distribution private key."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
from urllib.parse import quote
from check_source import parse_tag
from publish import API, PACKAGE


def certificate(value):
    value = value.replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", value):
        raise ValueError("Configure the historical distribution certificate SHA-256")
    return value


def validate_metadata(metadata):
    code = metadata["versionCode"]
    name = metadata["versionName"]
    source = parse_tag("v" + name, name.split("-", 1)[0])
    if (metadata.get("applicationId") != PACKAGE or metadata.get("dirty") is not False
            or type(code) is not int or not 56 < code <= 2100000000
            or metadata.get("buildNumber") != code or metadata.get("channel") != source["channel"]
            or not re.fullmatch(r"[0-9a-f]{40}", metadata.get("sourceRevision", ""))):
        raise ValueError("Only a clean, reserved public Hamigo build can be exported")


def select_universal(listing, expected):
    matches = [item["generatedUniversalApk"]["downloadId"]
               for item in listing.get("generatedApks", [])
               if item.get("certificateSha256Hash", "").replace(":", "").lower() == expected
               and item.get("generatedUniversalApk", {}).get("downloadId")]
    if len(matches) > 1:
        raise ValueError("Ambiguous universal APK for the historical signing certificate")
    return matches[0] if matches else None


def android_tool(name):
    sdk = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "")))
    suffix = ".bat" if os.name == "nt" and name == "apksigner" else ".exe" if os.name == "nt" else ""
    path = sdk / "build-tools" / "36.0.0" / (name + suffix)
    if not path.is_file():
        raise ValueError("Install Android build-tools 36.0.0 and set ANDROID_HOME")
    return str(path)


def verify_apk(apk, metadata, expected):
    signed = subprocess.check_output([android_tool("apksigner"), "verify", "--print-certs", str(apk)],
                                     text=True, stderr=subprocess.PIPE)
    signers = re.findall(r"^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-f]+)$", signed, re.M)
    if signers != [expected]:
        raise ValueError("APK signer differs from the historical distribution certificate")
    manifest = subprocess.check_output([android_tool("aapt2"), "dump", "badging", str(apk)],
                                       text=True, stderr=subprocess.PIPE)
    package = next((line for line in manifest.splitlines() if line.startswith("package: ")), "")
    attrs = dict(re.findall(r"(\w+)='([^']*)'", package))
    if (attrs.get("name") != PACKAGE or attrs.get("versionCode") != str(metadata["versionCode"])
            or attrs.get("versionName") != metadata["versionName"]):
        raise ValueError("Downloaded APK package or version differs from the archived AAB")


def export(session, metadata, folder, expected, *, attempts=12, delay=15):
    validate_metadata(metadata)
    expected = certificate(expected)
    code = metadata["versionCode"]
    endpoint = f"{API}/applications/{PACKAGE}/generatedApks/{code}"
    download_id = None
    for attempt in range(attempts):
        response = session.get(endpoint, timeout=300)
        try:
            if response.ok:
                download_id = select_universal(response.json(), expected)
            elif response.status_code not in (404, 409):
                raise RuntimeError(f"Play APK lookup failed (HTTP {response.status_code})")
        finally:
            response.close()
        if download_id:
            break
        if attempt + 1 < attempts:
            time.sleep(delay)
    if not download_id:
        raise RuntimeError("Historical-key universal APK not ready; keep the GitHub release draft and retry export later")
    folder.mkdir(parents=True, exist_ok=True)
    name = f"Hamigo-v{metadata['versionName']}-code{code}.apk"
    apk = folder / name
    partial = folder / (name + ".part")
    if apk.exists() or partial.exists():
        raise ValueError("Refuse replacing an existing archived APK")
    try:
        response = session.get(endpoint + "/downloads/" + quote(download_id, safe="") + ":download",
                               params={"alt": "media"}, stream=True, timeout=300)
        try:
            if not response.ok:
                raise RuntimeError(f"Play APK download failed (HTTP {response.status_code})")
            with partial.open("xb") as output:
                for chunk in response.iter_content(chunk_size=1024 * 1024):
                    if chunk:
                        output.write(chunk)
        finally:
            response.close()
        verify_apk(partial, metadata, expected)
        partial.rename(apk)
    finally:
        partial.unlink(missing_ok=True)
    with apk.open("rb") as binary:
        digest = hashlib.file_digest(binary, "sha256").hexdigest()
    provenance = {"source": "google-play-generated-universal-apk", "applicationId": PACKAGE,
                  "versionName": metadata["versionName"], "versionCode": code,
                  "sourceRevision": metadata["sourceRevision"], "sha256": digest,
                  "distributionCertificateSha256": expected, "file": name}
    (folder / "APK-SHA256SUMS.txt").write_text(digest + "  " + name + "\n", encoding="utf-8")
    (folder / "APK-PROVENANCE.json").write_text(json.dumps(provenance, indent=2) + "\n", encoding="utf-8")
    return provenance


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--folder", type=Path, required=True)
    args = parser.parse_args()
    from google.auth import default
    from google.auth.transport.requests import AuthorizedSession
    credentials, _ = default(scopes=["https://www.googleapis.com/auth/androidpublisher"])
    with AuthorizedSession(credentials) as session:
        print(json.dumps(export(session, json.loads(args.metadata.read_text(encoding="utf-8")), args.folder,
                                os.environ["HAMIGO_DISTRIBUTION_CERT_SHA256"])))
