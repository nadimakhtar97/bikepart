"""Contract check against an actual APK (debug or signed release)."""
import hashlib
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

APK, AAPT = sys.argv[1:3]
sys.argv = [sys.argv[0]]

class BuiltApkContract(unittest.TestCase):
    def test_metadata_matches_artifact_and_publisher_detects_tampering(self):
        with tempfile.TemporaryDirectory() as directory:
            output = pathlib.Path(directory) / 'update.json'
            subprocess.run([sys.executable, str(pathlib.Path(__file__).with_name('metadata.py')), APK, AAPT, str(output)], check=True)
            data = json.loads(output.read_text())
            badging = subprocess.check_output([AAPT, 'dump', 'badging', APK], text=True)
            self.assertIn("name='" + data['packageId'] + "'", badging)
            self.assertIn("versionCode='" + str(data['versionCode']) + "'", badging)
            self.assertIn("versionName='" + data['versionName'] + "'", badging)
            self.assertIn("sdkVersion:'" + str(data['minSdk']) + "'", badging)
            self.assertEqual(pathlib.Path(APK).stat().st_size, data['sizeBytes'])
            self.assertEqual(hashlib.sha256(pathlib.Path(APK).read_bytes()).hexdigest(), data['sha256'])
            self.assertEqual(1, data['formatVersion'])
            self.assertEqual('bikepart.apk', data['apkAssetName'])
            subprocess.run([sys.executable, str(pathlib.Path(__file__).with_name('metadata.py')), APK, AAPT, str(output), '--verify'], check=True)
            data['versionCode'] += 1
            output.write_text(json.dumps(data))
            self.assertNotEqual(0, subprocess.run([sys.executable, str(pathlib.Path(__file__).with_name('metadata.py')), APK, AAPT, str(output), '--verify'], capture_output=True).returncode)

unittest.main()
