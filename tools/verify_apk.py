#!/usr/bin/env python3
"""Structural APK checks; runtime LSPosed class loading remains a device check."""
import argparse, hashlib, json, pathlib, struct, subprocess, zipfile

def dex_classes(data):
    def u32(offset): return struct.unpack_from('<I',data,offset)[0]
    def string(offset):
        while data[offset] & 0x80: offset += 1
        offset += 1
        return data[offset:data.index(b'\0',offset)].decode('utf-8',errors='replace')
    strings=[string(u32(u32(60)+i*4)) for i in range(u32(56))]
    types=[strings[u32(u32(68)+i*4)] for i in range(u32(64))]
    result={}
    for i in range(u32(96)):
        offset=u32(100)+i*32
        cls,_,sup=struct.unpack_from('<III',data,offset)
        result[types[cls]]=types[sup] if sup!=0xffffffff else None
    return result

def verify(apk):
    with zipfile.ZipFile(apk) as z:
        root='META-INF/xposed/'
        assert z.read(root+'java_init.list').decode().strip()=='io.github.jahn2007.edifierx.xposed.ModuleEntry'
        props=dict(line.split('=',1) for line in z.read(root+'module.prop').decode().splitlines() if '=' in line)
        assert props['minApiVersion']==props['targetApiVersion']=='102'
        assert props['autoHotReload']=='true'
        assert len(z.read(root+'java_init.list').decode().splitlines())==1
        scopes=set(z.read(root+'scope.list').decode().split())
        assert scopes=={'com.android.settings','com.oplus.wirelesssettings','com.oplus.melody','com.heytap.mydevices','com.android.bluetooth'}
        assert 'assets/xposed_init' not in z.namelist()
        classes={}
        for name in z.namelist():
            if name.startswith('classes') and name.endswith('.dex'): classes.update(dex_classes(z.read(name)))
        entry='Lio/github/jahn2007/edifierx/xposed/ModuleEntry;'
        assert classes.get(entry)=='Lio/github/libxposed/api/XposedModule;', 'Entry or superclass missing'
        assert not any(n.startswith('Lio/github/libxposed/api/') for n in classes), 'API must be compileOnly'
        assert not any(n.startswith('Lde/robv/android/xposed/') for n in classes), 'legacy API bundled'
    return {'file':apk.name,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'api':102,'auto_hot_reload':True,'scopes':sorted(scopes),'entry':entry,'runtime_verified':False}

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('apk',type=pathlib.Path);parser.add_argument('--aapt',required=True);args=parser.parse_args()
    badging=subprocess.check_output([args.aapt,'dump','badging',str(args.apk)],text=True)
    assert "package: name='io.github.jahn2007.edifierx'" in badging
    assert "versionName='0.2.0'" in badging
    assert "sdkVersion:'35'" in badging
    print(json.dumps(verify(args.apk),indent=2))
