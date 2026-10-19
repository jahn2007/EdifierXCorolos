#!/usr/bin/env python3
"""Reassemble user-provided private APK parts outside the public checkout, verifying hashes."""
import argparse, hashlib, json, pathlib
parser=argparse.ArgumentParser()
parser.add_argument('parts_dir',type=pathlib.Path)
parser.add_argument('output',type=pathlib.Path)
args=parser.parse_args()
root=pathlib.Path(__file__).resolve().parents[1]
output=args.output.resolve()
if output.is_relative_to(root):
    raise SystemExit('Private APK must be written outside the public checkout.')
manifest=json.loads((args.parts_dir/'manifest.json').read_text(encoding='utf-8-sig'))
parts=manifest['parts']
if not isinstance(parts,list) or not parts:
    raise SystemExit('Manifest has no parts.')
# Validate every source before creating output; reject traversal and absolute filenames.
for item in parts:
    name=item['name']
    if pathlib.Path(name).name!=name or not name.startswith('official-apk.part'):
        raise SystemExit('Invalid part filename.')
    data=(args.parts_dir/name).read_bytes()
    if len(data)!=item['size'] or hashlib.sha256(data).hexdigest()!=item['sha256']:
        raise SystemExit('Part checksum or size mismatch.')
with output.open('xb') as stream:
    digest=hashlib.sha256();size=0
    for item in parts:
        data=(args.parts_dir/item['name']).read_bytes();stream.write(data);digest.update(data);size+=len(data)
if size!=manifest['size'] or digest.hexdigest()!=manifest['sha256']:
    output.unlink()
    raise SystemExit('Reassembled checksum mismatch; output removed.')
print('Private APK reassembled; SHA-256:',digest.hexdigest())
