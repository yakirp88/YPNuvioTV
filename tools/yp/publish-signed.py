"""Publish an already-signed APK; never accepts or transmits a private signing key."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.parse
import urllib.request


def run(*args):
    return subprocess.check_output(args, text=True)


def main():
    data = json.loads(Path('config/yp-signed-release.json').read_text())
    version, source, digest = data['version'], data['source_sha'], data['sha256']
    assert re.fullmatch(r'\d+\.\d+\.\d+', version)
    assert re.fullmatch(r'[a-f0-9]{40}', source)
    assert re.fullmatch(r'[a-f0-9]{64}', digest)
    output = Path('distribution')
    output.mkdir(exist_ok=True)
    apk = output / 'Nuvio-YP.apk'
    artifact = data.get('build_artifact')
    if artifact:
        assert type(artifact['run_id']) is int and artifact['run_id'] > 0
        assert re.fullmatch(r'yp-ai-signing-input-\d+', artifact['name'])
        repo = os.environ['GITHUB_REPOSITORY']
        build = json.loads(run('gh', 'api', f'repos/{repo}/actions/runs/{artifact["run_id"]}'))
        assert build['head_sha'] == source and build['conclusion'] == 'success'
        incoming = Path('signing-input')
        subprocess.run(['gh', 'run', 'download', str(artifact['run_id']), '--repo', repo,
                        '--name', artifact['name'], '--dir', str(incoming)], check=True)
        original = incoming / 'app-full-debug.apk'
        patch = json.loads(Path('config/yp-apk-signature-delta.json').read_text())
        with original.open('rb') as stream:
            assert hashlib.file_digest(stream, 'sha256').hexdigest() == patch['input_sha256']
        assert patch['output_sha256'] == digest and patch['output_size'] == data['size_bytes']
        assert len(patch['ops']) < 100_000
        size = original.stat().st_size
        count = 0
        with original.open('rb') as origin, apk.open('wb') as target:
            for operation in patch['ops']:
                if set(operation) == {'copy'}:
                    offset, length = operation['copy']
                    assert type(offset) is int and type(length) is int
                    assert 0 <= offset <= size and 0 <= length <= size - offset
                    origin.seek(offset)
                    remaining = length
                    while remaining:
                        block = origin.read(min(1024 * 1024, remaining))
                        assert block
                        target.write(block)
                        remaining -= len(block)
                    count += length
                else:
                    assert set(operation) == {'data'}
                    block = base64.b64decode(operation['data'], validate=True)
                    target.write(block)
                    count += len(block)
                assert count <= data['size_bytes'] <= 200 * 1024 * 1024
        assert count == data['size_bytes']
    else:
        url = urllib.parse.urlparse(data['url'])
        assert url.scheme == 'https' and url.netloc in {'litter.catbox.moe', 'files.catbox.moe'}
        assert url.path.endswith('.apk') and not url.query and not url.fragment
        count = 0
        with urllib.request.urlopen(data['url'], timeout=120) as response, apk.open('wb') as target:
            while chunk := response.read(1024 * 1024):
                count += len(chunk)
                assert count <= data['size_bytes'] <= 200 * 1024 * 1024
                target.write(chunk)
        assert count == data['size_bytes']
    with apk.open('rb') as stream:
        assert hashlib.file_digest(stream, 'sha256').hexdigest() == digest, 'APK checksum mismatch'
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools' / '36.0.0'
    verification = run(str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk))
    cert = re.search(r'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9:]+)', verification)
    assert cert and cert.group(1).replace(':', '').lower() == Path('config/yp-signing-sha256.txt').read_text().strip()
    manifest = run(str(tools / 'aapt'), 'dump', 'badging', str(apk))
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", manifest)
    assert package and package.group(1) == 'com.nuvio.tv.yp'
    assert int(package.group(2)) == data['version_code']
    assert f'-yp-ai.{version}.build.' in package.group(3)
    run('git', 'fetch', '--depth=1', 'origin', source)
    checked_version = run('git', 'show', source + ':yp-version.properties')
    assert f'versionName={version}\n' in checked_version
    with (output / 'source.zip').open('wb') as archive:
        subprocess.run(['git', 'archive', '--format=zip', '--prefix=Nuvio-YP/', source], stdout=archive, check=True)
    (output / 'SHA256SUMS.txt').write_text(f'{digest}  Nuvio-YP.apk\n')
    notes = output / 'release-notes.md'
    notes.write_text(
        f'Nuvio YP AI {version}; Android version code {data["version_code"]}.\n\n'
        'Includes a bounded detection journal with manual dpaste upload, end-of-episode status and late-analysis review fixes.\n'
        'Checksum, original signing certificate, package and version verified before publication.\n'
        f'Corresponding source commit: {source}.\n'
        'Device playback and reproduction of the reported crash still need user testing.\n'
        'Audio analysis and automatic AI reporting remain unavailable.\n'
    )
    tag = f'yp-ai-v{version}'
    exists = subprocess.run(['gh', 'release', 'view', tag], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    assert exists.returncode != 0, 'Immutable release already exists; never overwrite it'
    subprocess.run(['gh', 'release', 'create', tag, str(apk), str(output / 'SHA256SUMS.txt'),
                    str(output / 'source.zip'), '--target', source, '--prerelease',
                    '--title', f'YP AI {version} — crash diagnostics', '--notes-file', str(notes)], check=True)


if __name__ == '__main__':
    main()
