"""Pure helpers shared by the Windows UI and tests."""
from urllib.parse import urlsplit
from pathlib import Path
import re

MEDIA = {'.mp4', '.mkv', '.webm', '.mov', '.m4a', '.mp3', '.opus', '.ogg', '.wav'}

def extract_url(text):
    match = re.search(r'https?://[^\s<>]+', text.strip())
    value = match.group(0).rstrip('.,);]') if match else ''
    parts = urlsplit(value)
    return value if parts.scheme in ('http', 'https') and parts.hostname else ''

def selector(quality):
    if quality == 'Audio · M4A':
        return 'bestaudio/best'
    height = int(re.search(r'\d+', quality).group())
    return f'bestvideo[height<={height}]+bestaudio/best[height<={height}]'

def size_label(info):
    streams = info.get('requested_formats') or [info]
    total = 0
    approximate = False
    for stream in streams:
        size = stream.get('filesize')
        if not size:
            size = stream.get('filesize_approx')
            approximate = True
        if not size and stream.get('tbr') and info.get('duration'):
            size = stream['tbr'] * 1000 / 8 * info['duration']
            approximate = True
        if not size or size <= 0:
            return 'Size unavailable'
        total += size
    unit = 'GB' if total >= 1e9 else 'MB'
    return ('≈ ' if approximate else '') + f'{total / (1e9 if unit == "GB" else 1e6):.1f} {unit}'

def safe_media(path, folder):
    p, root = Path(path), Path(folder).resolve()
    return p.is_file() and not p.is_symlink() and p.resolve().parent == root and p.suffix.lower() in MEDIA
