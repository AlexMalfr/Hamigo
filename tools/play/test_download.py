import io
from pathlib import Path
import tempfile
import unittest
import zipfile
from download_archive import extract_files, select_artifact


class DownloadTests(unittest.TestCase):
    def test_only_one_unexpired_exact_source_can_be_used(self):
        def artifact(number, source="a" * 40, expired=False, name="release-v1.0"):
            return {"id": number, "name": name, "expired": expired,
                    "workflow_run": {"head_sha": source}}
        good = artifact(1)
        discarded = [artifact(2, source="b" * 40), artifact(3, expired=True),
                     artifact(4, name="release-v1.0-alpha1")]
        self.assertEqual(select_artifact(discarded + [good], "v1.0", "a" * 40), good)
        for invalid in (discarded, [good, artifact(5)]):
            with self.assertRaises(ValueError):
                select_artifact(invalid, "v1.0", "a" * 40)

    def test_zip_extracts_only_expected_files_and_rejects_duplicates(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            archive = io.BytesIO()
            with zipfile.ZipFile(archive, "w") as bundle:
                bundle.writestr("Hamigo-v1.0.aab", b"signed bytes")
                bundle.writestr("../unrelated.txt", b"must not be extracted")
            extract_files(archive, target, ("Hamigo-v1.0.aab",))
            self.assertEqual((target / "Hamigo-v1.0.aab").read_bytes(), b"signed bytes")
            self.assertEqual([item.name for item in target.iterdir()], ["Hamigo-v1.0.aab"])
            with self.assertRaisesRegex(ValueError, "Missing or duplicate"):
                extract_files(archive, target, ("version.json",))
            duplicate = io.BytesIO()
            with zipfile.ZipFile(duplicate, "w") as bundle:
                bundle.writestr("Hamigo-v1.0.aab", b"first")
                with self.assertWarns(UserWarning):
                    bundle.writestr("Hamigo-v1.0.aab", b"substitute")
            with self.assertRaisesRegex(ValueError, "Missing or duplicate"):
                extract_files(duplicate, target, ("Hamigo-v1.0.aab",))


if __name__ == "__main__": unittest.main()
