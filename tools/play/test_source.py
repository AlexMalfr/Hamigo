import unittest
from unittest.mock import patch
import check_source
from reserve_build import choose


class SourceTests(unittest.TestCase):
    def test_tag_names_cannot_select_another_version_or_legacy_build(self):
        for tag in ('main', 'v0.56', 'v1.0-beta0', 'v1.1', 'v1.0-rc1', 'v1.0-alpha01'):
            with self.assertRaises(ValueError): check_source.parse_tag(tag, '1.0')
        self.assertEqual('beta', check_source.parse_tag('v1.0-beta2', '1.0')['branch'])
        self.assertEqual('main', check_source.parse_tag('v1.0', '1.0')['branch'])

    def test_final_squash_preserves_tree_despite_different_commit(self):
        def git(*args):
            if args == ('rev-parse','--is-shallow-repository'): return 'false'
            if args[0] == 'status': return ''
            if args[0] == 'cat-file': return 'tag'
            if args[0] == 'merge-base': return ''
            return 'tree' if args[-1].endswith('^{tree}') else 'a'*40
        with patch.object(check_source,'git',git):
            self.assertEqual('release',check_source.check('v1.0','1.0')['channel'])
        with patch.object(check_source,'git',side_effect=lambda *a: ' M file' if a[0]=='status' else git(*a)):
            with self.assertRaises(ValueError): check_source.check('v1.0','1.0')

    def test_build_numbers_increase_globally_independent_of_channel(self):
        self.assertEqual((57,False),choose({},'v1.0','a'*40))
        records={57:{'releaseTag':'v1.0-alpha1','sourceRevision':'a'*40},
                 58:{'releaseTag':'v1.0-beta1','sourceRevision':'b'*40}}
        self.assertEqual((59,False),choose(records,'v1.0','c'*40))
        self.assertEqual((61,False),choose(records,'v1.0','c'*40,60))
        self.assertEqual((57,True),choose(records,'v1.0-alpha1','a'*40))
        with self.assertRaises(ValueError): choose(records,'v1.0-alpha1','c'*40)
        records[57]['retired']=True
        self.assertEqual((59,False),choose(records,'v1.0-alpha1','c'*40))
        with self.assertRaises(ValueError): choose({},'v1.0','a'*40,2100000000)


if __name__ == '__main__': unittest.main()
