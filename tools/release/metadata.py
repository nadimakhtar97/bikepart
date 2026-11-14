"""Generate/verify the v1 public update contract from APK badging and exact bytes."""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess


def metadata(apk, aapt):
    badging = subprocess.check_output([aapt, 'dump', 'badging', str(apk)], text=True)
    package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging, re.M)
    minimum = re.search(r"^sdkVersion:'([0-9]+)'", badging, re.M)
    if not package or not minimum:
        raise ValueError('APK package/version/minimum API could not be read')
    package_id, code, version = package.groups()
    if package_id != 'com.nadim.bikepart' or int(code) < 1 or not version.strip():
        raise ValueError('Unexpected APK identity/version')
    with apk.open('rb') as stream:
        checksum = hashlib.file_digest(stream, 'sha256').hexdigest()
    return dict(formatVersion=1, packageId=package_id, versionCode=int(code), versionName=version,
                minSdk=int(minimum.group(1)), apkAssetName='bikepart.apk', sizeBytes=apk.stat().st_size,
                sha256=checksum)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=pathlib.Path)
    parser.add_argument('aapt')
    parser.add_argument('output', type=pathlib.Path)
    parser.add_argument('--verify', action='store_true')
    args = parser.parse_args()
    expected = metadata(args.apk, args.aapt)
    if args.verify:
        actual = json.loads(args.output.read_text(encoding='utf-8'))
        if actual != expected or any(type(actual.get(key)) is not type(value) for key, value in expected.items()):
            raise ValueError('Metadata disagrees with the built APK')
    else:
        args.output.write_text(json.dumps(expected, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
