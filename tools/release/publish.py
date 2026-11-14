"""Publish a new immutable release only after every uploaded byte is verified."""
import hashlib
import json
import pathlib
import subprocess
import sys
import tempfile

FILES = ('bikepart.apk', 'bikepart.aab', 'SHA256SUMS.txt', 'update.json')


def digest(file):
    with file.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def validate(dist):
    for name in FILES:
        if not (dist / name).is_file() or (dist / name).stat().st_size == 0:
            raise ValueError(f'Missing or empty release asset: {name}')
    expected = ''.join(f'{digest(dist / name)}  {name}\n' for name in FILES[:2])
    if (dist / 'SHA256SUMS.txt').read_text(encoding='utf-8') != expected:
        raise ValueError('Checksums disagree with prepared assets')
    data = json.loads((dist / 'update.json').read_text(encoding='utf-8'))
    if (type(data.get('formatVersion')) is not int or data.get('formatVersion') != 1 or data.get('packageId') != 'com.nadim.bikepart'
            or type(data.get('versionCode')) is not int or data['versionCode'] < 1
            or type(data.get('minSdk')) is not int or data['minSdk'] < 1
            or not isinstance(data.get('versionName'), str) or not data['versionName'].strip()
            or data.get('apkAssetName') != 'bikepart.apk'
            or data.get('sizeBytes') != (dist / 'bikepart.apk').stat().st_size
            or data.get('sha256') != digest(dist / 'bikepart.apk')):
        raise ValueError('Metadata disagrees with prepared APK')


def publish(dist, tag, run=None):
    if run is None:
        run = lambda command: subprocess.run(command, check=True)
    validate(dist)
    # GitHub rejects an existing tag release. Never upload with --clobber or edit its assets.
    run(['gh', 'release', 'create', tag, *(str(dist / name) for name in FILES), '--draft',
         '--verify-tag', '--generate-notes', '--title', f'Bikepart {tag}'])
    with tempfile.TemporaryDirectory() as directory:
        uploaded = pathlib.Path(directory)
        run(['gh', 'release', 'download', tag, '--dir', directory])
        for name in FILES:
            if digest(uploaded / name) != digest(dist / name):
                raise ValueError(f'Uploaded release asset differs: {name}; release remains a draft')
        validate(uploaded)
    run(['gh', 'release', 'edit', tag, '--draft=false'])


if __name__ == '__main__':
    publish(pathlib.Path(sys.argv[1]), sys.argv[2])
