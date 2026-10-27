"""Deterministic source-only bundle. No credentials, reports or local runtime files."""
from pathlib import Path
import hashlib
import sys
import zipfile

root = Path(__file__).resolve().parent
output = Path(sys.argv[1]).resolve()
files = ['README.md', 'index.html', 'check.py', 'Start-Windows.cmd']
with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED) as z:
    for name in files:
        info = zipfile.ZipInfo('Gemini-Communication-Check/' + name, date_time=(2026,10,27,0,0,0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        z.writestr(info, (root / name).read_bytes())
with zipfile.ZipFile(output) as z:
    assert z.testzip() is None
    assert len(z.namelist()) == len(files)
print(output.name, output.stat().st_size, hashlib.sha256(output.read_bytes()).hexdigest())
