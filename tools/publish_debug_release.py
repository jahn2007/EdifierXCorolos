#!/usr/bin/env python3
"""Publish a verified Actions Debug APK as a clearly labeled immutable prerelease."""
import hashlib,json,os,pathlib,re,subprocess,sys,tempfile

apk=pathlib.Path(sys.argv[1]).resolve()
repo=os.environ['GITHUB_REPOSITORY']
number=os.environ['GITHUB_RUN_NUMBER']
commit=os.environ['GITHUB_SHA']
run_id=os.environ['GITHUB_RUN_ID']
assert re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+',repo)
assert number.isdigit() and run_id.isdigit() and re.fullmatch(r'[a-f0-9]{40}',commit)
assert apk.name=='EdifierXCorolos-v0.2.2-debug.apk' and apk.is_file()
tag=f'debug-v0.2.2-build{number}'
digest=hashlib.sha256(apk.read_bytes()).hexdigest()
endpoint=f'repos/{repo}/releases/tags/{tag}'

def gh(*args):
    return subprocess.check_output(['gh',*args],text=True)

existing=subprocess.run(['gh','api',endpoint],capture_output=True,text=True)
if existing.returncode==0:
    release=json.loads(existing.stdout)
    found=next((a for a in release['assets'] if a['name']==apk.name),None)
    if found is None or found.get('digest')!='sha256:'+digest:
        raise SystemExit('Existing release has different or incomplete assets; preserved without overwriting.')
elif '404' in existing.stderr:
    with tempfile.TemporaryDirectory(prefix='edifier-release-') as directory:
        root=pathlib.Path(directory)
        checksum=root/'DEBUG-SHA256SUMS.txt'
        checksum.write_text(f'{digest}  {apk.name}\n')
        notes=root/'notes.md'
        notes.write_text(f'''可安装的开发调试版。点击 Assets 中的 **{apk.name}** 直接下载，无需下载源码或 Actions ZIP。

构建与校验： https://github.com/{repo}/actions/runs/{run_id} 。单元测试、Lint、Debug/R8 Release 编译及 API 102 / 热重载元数据检查已通过。

本 APK 使用 Android 调试证书，没有使用正式 keystore，不是正式签名稳定版。ColorOS 15、Evo Pro 与热重载实机功能仍待验收。从 0.1.0 首次升级后需重启宿主一次。不同证书的 APK 不能直接覆盖安装。

SHA-256：`{digest}`。

源码提交：`{commit}`。完整源码和许可证可通过本 Release 的源代码附件获取。
''')
        print(gh('release','create',tag,str(apk),str(checksum),'--repo',repo,'--target',commit,'--title',f'EdifierXCorolos v0.2.2 Debug — Build {number}','--notes-file',str(notes),'--prerelease').strip())
    release=json.loads(gh('api',endpoint))
else:
    raise SystemExit('Cannot inspect release state; publication stopped: '+existing.stderr)
asset=next(a for a in release['assets'] if a['name']==apk.name)
assert asset['size']==apk.stat().st_size and asset.get('digest')=='sha256:'+digest
url=asset['browser_download_url']
print(f'::notice title=Direct Debug APK download::{url}')
with open(os.environ['GITHUB_STEP_SUMMARY'],'a') as summary:
    summary.write(f'## 可直接下载的 Debug APK\n\n[下载 {apk.name}]({url})\n\nSHA-256: `{digest}`\n\n开发调试版，正式签名未启用，实机功能待验收。\n')
