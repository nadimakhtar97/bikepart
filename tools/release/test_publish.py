import hashlib
import json
import pathlib
import subprocess
import tempfile
import unittest
from publish import publish


class PublicationContract(unittest.TestCase):
    def test_incomplete_assets_never_create_a_public_release(self):
        with tempfile.TemporaryDirectory() as directory:
            called = []
            with self.assertRaises((ValueError, FileNotFoundError)):
                publish(pathlib.Path(directory), 'v2', lambda command: called.append(command))
            self.assertEqual([], called)

    def test_failed_upload_validation_leaves_draft_and_existing_binaries_untouched(self):
        with tempfile.TemporaryDirectory() as directory:
            dist = pathlib.Path(directory)
            for name in ('bikepart.apk', 'bikepart.aab'):
                (dist / name).write_bytes(b'signed artifact')
            digest = hashlib.sha256(b'signed artifact').hexdigest()
            (dist / 'SHA256SUMS.txt').write_text(f'{digest}  bikepart.apk\n{digest}  bikepart.aab\n')
            (dist / 'update.json').write_text(json.dumps(dict(formatVersion=1, packageId='com.nadim.bikepart',
                versionCode=2, versionName='2', minSdk=33, apkAssetName='bikepart.apk', sizeBytes=15, sha256=digest)))
            remote = {'existing': b'previous binary'}
            def github(command):
                if command[2] == 'create':
                    self.assertIn('--draft', command)
                    if 'draft' in remote:
                        raise subprocess.CalledProcessError(1, command)
                    remote['draft'] = True
                elif command[2] == 'download':
                    target = pathlib.Path(command[command.index('--dir') + 1])
                    for file in dist.iterdir():
                        (target / file.name).write_bytes(file.read_bytes())
                    (target / 'bikepart.apk').write_bytes(b'corrupt upload')
                elif command[2] == 'edit':
                    remote['published'] = True
            with self.assertRaises(ValueError):
                publish(dist, 'v2', github)
            self.assertTrue(remote['draft'])
            self.assertNotIn('published', remote)
            self.assertEqual(b'previous binary', remote['existing'])
            # A rerun cannot replace the existing release (including an incomplete draft).
            with self.assertRaises(subprocess.CalledProcessError):
                publish(dist, 'v2', github)
            self.assertNotIn('published', remote)

    def test_validated_uploaded_assets_become_public_together(self):
        with tempfile.TemporaryDirectory() as directory:
            dist = pathlib.Path(directory)
            for name in ('bikepart.apk', 'bikepart.aab'):
                (dist / name).write_bytes(b'signed artifact')
            checksum = hashlib.sha256(b'signed artifact').hexdigest()
            (dist / 'SHA256SUMS.txt').write_text(f'{checksum}  bikepart.apk\n{checksum}  bikepart.aab\n')
            (dist / 'update.json').write_text(json.dumps(dict(formatVersion=1, packageId='com.nadim.bikepart',
                versionCode=2, versionName='2', minSdk=33, apkAssetName='bikepart.apk', sizeBytes=15, sha256=checksum)))
            remote = {}
            def github(command):
                if command[2] == 'create':
                    remote['draft'] = '--draft' in command
                    remote['assets'] = {name: (dist / name).read_bytes() for name in ('bikepart.apk', 'bikepart.aab', 'SHA256SUMS.txt', 'update.json')}
                elif command[2] == 'download':
                    for name, content in remote['assets'].items():
                        (pathlib.Path(command[command.index('--dir') + 1]) / name).write_bytes(content)
                elif command[2] == 'edit':
                    remote['draft'] = False
            publish(dist, 'v2', github)
            self.assertFalse(remote['draft'])
            self.assertEqual({'bikepart.apk', 'bikepart.aab', 'SHA256SUMS.txt', 'update.json'}, set(remote['assets']))


if __name__ == '__main__':
    unittest.main()
