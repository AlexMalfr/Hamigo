"""Exercise real Git reservation pushes and reruns against an isolated bare remote."""
import contextlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import reserve_build


class ReservationIntegrationTests(unittest.TestCase):
    def test_two_release_tags_allocate_once_each_and_reruns_reuse_the_original_code(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder); remote=root/'remote.git'; checkout=root/'source'
            def git(*args,cwd=None):
                return subprocess.run(['git',*args],cwd=cwd,capture_output=True,text=True,check=True).stdout.strip()
            git('init','--bare',str(remote)); git('init',str(checkout))
            git('config','user.name','Isolated fixture',cwd=checkout)
            git('config','user.email','fixture@example.invalid',cwd=checkout)
            (checkout/'fixture.txt').write_text('source',encoding='utf-8')
            git('add','fixture.txt',cwd=checkout); git('commit','-m','fixture',cwd=checkout)
            git('remote','add','origin',str(remote),cwd=checkout)
            revision=git('rev-parse','HEAD',cwd=checkout)
            previous=Path.cwd()
            try:
                os.chdir(checkout)
                with patch.object(reserve_build,'check',return_value={'sourceRevision':revision}), patch.object(reserve_build,'properties',return_value={'version':'1.0'}):
                    self.assertEqual(57,reserve_build.reserve('v1.0-alpha1'))
                    self.assertEqual(57,reserve_build.reserve('v1.0-alpha1'))
                    self.assertEqual(58,reserve_build.reserve('v1.0-beta1'))
                    self.assertEqual(57,reserve_build.reserve('v1.0-alpha1'))
                reservations=reserve_build.read_reservations('origin')
                self.assertEqual({57,58},set(reservations))
                self.assertEqual(revision,git('rev-parse','ci-build/58^{commit}',cwd=checkout))
                self.assertIn('refs/tags/ci-build/57',git('ls-remote','--tags','origin',cwd=checkout))
            finally:
                os.chdir(previous)


if __name__ == '__main__': unittest.main()
