#!/usr/bin/env python3
import hashlib,pathlib,sys
apk=pathlib.Path(sys.argv[1]);digest=hashlib.sha256(apk.read_bytes()).hexdigest()
print(f'::notice title=APK SHA-256::{digest} {apk.name}')
