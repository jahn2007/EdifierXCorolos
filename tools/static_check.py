#!/usr/bin/env python3
import pathlib, re, xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
for p in (root/'app/src/main/java').rglob('*.java'):
    text=p.read_text()
    assert not re.search(r'\b(XposedBridge|XC_MethodHook|XposedHelpers)\b',text),p
assert 'compileOnly("io.github.libxposed:api:102.0.0")' in (root/'app/build.gradle.kts').read_text()
ET.parse(root/'app/src/main/AndroidManifest.xml')
for path in ['ARCHITECTURE','PROTOCOL_ANALYSIS','COLOROS15_COMPAT','BUILD','TESTING','TROUBLESHOOTING','STATUS']:
    assert (root/f'docs/{path}.md').is_file(),path
print('Static source and metadata contract checks passed; APK compilation is a separate CI step.')
