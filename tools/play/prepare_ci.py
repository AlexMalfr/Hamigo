"""Materialize CI credentials in ignored files; never print their values."""
import base64
import os
from pathlib import Path


def property_value(name):
    value = os.environ[name]
    if not value or any(c in value for c in "\r\n\0"):
        raise ValueError(f"Missing/invalid CI credential: {name}")
    return value.replace("\\", "\\\\").replace(" ", "\\ ").replace(":", "\\:").replace("=", "\\=")


def main():
    target = Path(".tools")
    target.mkdir(exist_ok=True)
    key = base64.b64decode(os.environ["HAMIGO_KEYSTORE_BASE64"], validate=True)
    if not key:
        raise ValueError("Empty upload keystore")
    (target / "hamigo-upload.jks").write_bytes(key)
    (target / "upload-signing.properties").write_text(
        "storeFile=hamigo-upload.jks\nkeyAlias=hamigo-upload\nstorePassword=" + property_value("HAMIGO_STORE_PASSWORD") + "\nkeyPassword=" + property_value("HAMIGO_KEY_PASSWORD") + "\n", encoding="utf-8")
    (target / "oauth.properties").write_text(
        "githubClientSecret=" + property_value("HAMIGO_OAUTH_CLIENT_SECRET") + "\n", encoding="utf-8")
    for name in ("hamigo-upload.jks", "upload-signing.properties", "oauth.properties"):
        (target / name).chmod(0o600)


if __name__ == "__main__": main()
