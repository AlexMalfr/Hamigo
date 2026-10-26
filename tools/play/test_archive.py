import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import check_archive


class ArchiveTests(unittest.TestCase):
    def test_archived_identity_and_hashes_are_checked_without_rebuilding(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = {"channel": "release", "product": "1.0", "prerelease": 1, "sourceRevision": "a" * 40}
            metadata = {"versionName": "1.0", "versionCode": 59, "buildNumber": 59, "channel": "release",
                        "sourceRevision": source["sourceRevision"], "applicationId": "com.malfreyt.alexandre.hamigo", "dirty": False}
            (root / 'Hamigo-v1.0.aab').write_bytes(b'isolated signed-byte fixture')
            def manifest():
                (root / 'version.json').write_text(json.dumps(metadata), encoding='utf-8')
                (root / 'SHA256SUMS.txt').write_text('\n'.join(
                    hashlib.sha256((root / name).read_bytes()).hexdigest() + '  ./' + name
                    for name in ('Hamigo-v1.0.aab', 'version.json')), encoding='utf-8')
            def git(*args):
                return 'version=1.0' if args[0] == 'show' else source['sourceRevision']
            manifest()
            with patch.object(check_archive, 'git', git), patch.object(check_archive, 'validate_tag', return_value=source):
                self.assertFalse(check_archive.verify(root, 'v1.0')['rebuilt'])
                (root / 'Hamigo-v1.0.aab').write_bytes(b'bytes replaced after archival')
                with self.assertRaisesRegex(ValueError, 'Archived hash differs'):
                    check_archive.verify(root, 'v1.0')
                metadata['sourceRevision'] = 'b' * 40
                manifest()  # Even self-consistent replacement checksums cannot substitute another source.
                with self.assertRaisesRegex(ValueError, 'metadata does not match'):
                    check_archive.verify(root, 'v1.0')


if __name__ == '__main__': unittest.main()
