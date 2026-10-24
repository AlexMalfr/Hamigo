import unittest
from unittest.mock import patch
import check_source


class SourceTests(unittest.TestCase):
    def run_case(self, branch, ordinal, *, dirty="", shallow="false", base="56", head="57"):
        def git(*args):
            if args[0] == "rev-parse": return shallow
            if args[0] == "status": return dirty
            if args[0] == "merge-base": return ""
            return head if args[-1] == "HEAD" else base
        with patch.object(check_source, "git", git):
            check_source.check(branch, ordinal)

    def test_squashed_main_commit_has_its_own_ordinal(self):
        self.run_case("main", 57)
        with self.assertRaises(ValueError): self.run_case("main", 58)

    def test_both_prerelease_branches_target_the_next_squash(self):
        for branch in ("alpha", "beta"):
            self.run_case(branch, 57)
            with self.assertRaises(ValueError): self.run_case(branch, 56)

    def test_shallow_dirty_and_other_branches_are_refused(self):
        for kwargs in ({"dirty": " M app.kt"}, {"shallow": "true"}):
            with self.assertRaises(ValueError): self.run_case("main", 57, **kwargs)
        with self.assertRaises(ValueError): self.run_case("codex/work", 57)


if __name__ == "__main__": unittest.main()
