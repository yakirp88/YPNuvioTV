"""Validate checked-in version metadata and emit non-secret Actions variables."""
import os
import pathlib
import re

values = dict(line.split('=', 1) for line in pathlib.Path('yp-version.properties').read_text().splitlines() if line.strip())
name = values['versionName']
assert re.fullmatch(r'\d+\.\d+\.\d+', name), 'Use a numeric semantic version'
base = int(values['versionCode'])
run = int(os.environ.get('GITHUB_RUN_NUMBER', '0'))
assert 0 <= run < 100000, 'Reserve a new version-code range'
assert 1072 < base < 2100000000 - 100000
assert f'## [{name}]' in pathlib.Path('CHANGELOG.md').read_text(), 'Document the version'
print(f'YP_VERSION_NAME={name}.build.{run}' if run else f'YP_VERSION_NAME={name}')
print(f'YP_VERSION_CODE={base + run}')
