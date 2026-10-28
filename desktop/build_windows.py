"""Build a portable folder on Windows, with official download engines bundled."""
import hashlib
import importlib.metadata
import json
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request
import zipfile
import imageio_ffmpeg

HERE = Path(__file__).resolve().parent
TOOLS = HERE / 'tools'
TOOLS.mkdir(exist_ok=True)

def get(url):
    request = urllib.request.Request(url, headers={'User-Agent':'ClipPocket-Build', 'Accept':'application/vnd.github+json'})
    with urllib.request.urlopen(request, timeout=180) as response:
        return response.read()

def asset(repo, filename):
    release = json.loads(get(f'https://api.github.com/repos/{repo}/releases/latest'))
    entry = next(a for a in release['assets'] if a['name'] == filename)
    data = get(entry['browser_download_url'])
    digest = entry.get('digest') or ''
    if digest.startswith('sha256:'):
        expected = digest.split(':',1)[1]
    else:
        checksums = next((a for a in release['assets'] if a['name'] in ('SHA2-256SUMS','SHA256SUMS',filename+'.sha256sum')), None)
        if not checksums:
            raise RuntimeError(f'No SHA-256 digest available for {repo}/{filename}')
        lines = get(checksums['browser_download_url']).decode().splitlines()
        matches = [line.split()[0] for line in lines if line.split()[-1].lstrip('*') == filename]
        if not matches and checksums['name'] == filename+'.sha256sum':
            matches = [lines[0].split()[0]]
        if not matches:
            raise RuntimeError('Could not find the asset checksum')
        expected = matches[0]
    if hashlib.sha256(data).hexdigest().lower() != expected.lower():
        raise RuntimeError(f'Checksum mismatch: {filename}')
    return data, {'repository':repo,'release':release['tag_name'],'asset':filename,'sha256':expected}

if sys.platform != 'win32':
    raise SystemExit('Run this build on Windows or use the GitHub Actions Windows build.')

locks = []
data, lock = asset('yt-dlp/yt-dlp','yt-dlp.exe')
(TOOLS/'yt-dlp.exe').write_bytes(data)
locks.append(lock)
data, lock = asset('denoland/deno','deno-x86_64-pc-windows-msvc.zip')
archive = TOOLS/'deno.zip'
archive.write_bytes(data)
with zipfile.ZipFile(archive) as z:
    with z.open('deno.exe') as source, (TOOLS/'deno.exe').open('wb') as target:
        shutil.copyfileobj(source,target)
archive.unlink()
locks.append(lock)
shutil.copy2(imageio_ffmpeg.get_ffmpeg_exe(),TOOLS/'ffmpeg.exe')
(TOOLS/'BUILD-DEPENDENCIES.json').write_text(json.dumps({'engines':locks,'python_packages':{name:importlib.metadata.version(name) for name in ('customtkinter','Pillow','pyinstaller','imageio-ffmpeg')}},indent=2))
subprocess.run([sys.executable,'-m','PyInstaller','--noconfirm','--clean','--windowed','--onedir','--name','ClipPocket','--collect-all','customtkinter','--add-data',f'{TOOLS};tools','--add-data',f'{HERE / "THIRD-PARTY.md"};.','app.py'],cwd=HERE,check=True)
shutil.copy2(HERE/'README.md',HERE/'dist/ClipPocket/README.md')
shutil.copy2(HERE/'THIRD-PARTY.md',HERE/'dist/ClipPocket/THIRD-PARTY.md')
shutil.copy2(TOOLS/'BUILD-DEPENDENCIES.json',HERE/'dist/ClipPocket/BUILD-DEPENDENCIES.json')
# Validate bundle imports without starting the GUI, then verify bundled engines execute.
for exe in (TOOLS/'yt-dlp.exe',TOOLS/'deno.exe'):
    subprocess.run([str(exe),'--version'],check=True,timeout=30)
subprocess.run([str(TOOLS/'ffmpeg.exe'),'-version'],check=True,timeout=30)
print('Built dist/ClipPocket/ClipPocket.exe. Keep the entire folder together.')
