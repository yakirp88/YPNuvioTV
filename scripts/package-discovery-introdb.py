#!/usr/bin/env python3
"""Create and validate a feature-only porting kit against the recorded upstream base."""
import hashlib
import json
import pathlib
import subprocess
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
BASE = "6377bee8cc644ecd8048a771ced61f06bcdf7946"

def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args])

def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=pathlib.Path)
    args = parser.parse_args()
    head = git("rev-parse", "HEAD").decode().strip()
    paths = git("diff", "--name-only", BASE, head, "--", "app").decode().splitlines()
    paths = [p for p in paths if p not in ("app/build.gradle.kts", "app/src/main/AndroidManifest.xml")]
    patch = git("diff", "--binary", BASE, head, "--", *paths)
    # Validate against exact original bytes without copying native binaries or build output.
    with tempfile.TemporaryDirectory(prefix="nuvio-feature-check-") as folder:
        target = pathlib.Path(folder)
        subprocess.run(["git", "init", "-q", folder], check=True)
        for path in paths:
            original = subprocess.run(["git", "-C", str(ROOT), "show", f"{BASE}:{path}"], capture_output=True)
            if original.returncode == 0:
                file = target / path
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(original.stdout)
        subprocess.run(["git", "-C", folder, "apply", "--check", "-"], input=patch, check=True)
        subprocess.run(["git", "-C", folder, "apply", "-"], input=patch, check=True)
        for path in paths:
            assert (target / path).read_bytes() == git("show", f"{head}:{path}"), path
    manifest = {"upstream_repository": "NuvioMedia/NuvioTV", "upstream_base": BASE,
        "source_repository": "yakirp88/YPNuvioTV", "source_commit": head,
        "version": "discovery.11", "patch_apply_check": "passed on exact upstream base",
        "android_build": "pending GitHub CI", "files": {
            p: hashlib.sha256(git("show", f"{head}:{p}")).hexdigest() for p in paths}}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED) as archive:
        prefix = "YP-Nuvio-Discovery-IntroDB/"
        archive.writestr(prefix + "features.patch", patch)
        archive.writestr(prefix + "manifest.json", json.dumps(manifest, indent=2))
        for path in paths:
            archive.writestr(prefix + "source-reference/" + path, git("show", f"{head}:{path}"))
        for name in ["LICENSE", "PORTING-DISCOVERY-INTRODB.md", "PORTING-DISCOVERY-INTRODB-HE.md"]:
            archive.write(ROOT / name, prefix + ("README.md" if name == "PORTING-DISCOVERY-INTRODB.md" else name))
    with zipfile.ZipFile(args.output) as archive:
        assert archive.testzip() is None
    print(json.dumps({"file": str(args.output), "files": len(paths), "size": args.output.stat().st_size,
        "sha256": hashlib.sha256(args.output.read_bytes()).hexdigest(), "patch_apply_check": "passed"}))

if __name__ == "__main__":
    main()
