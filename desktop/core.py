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


def validate_folder(folder):
    """Verify a chosen existing directory can actually receive a file."""
    import tempfile
    path = Path(folder).expanduser()
    if not path.is_absolute() or not path.is_dir():
        raise ValueError('Choose an existing folder using an absolute path.')
    with tempfile.TemporaryFile(dir=path):
        pass
    return path.resolve()


def save_download_folder(settings_path, folder):
    """Save only after validation, using an atomic replacement of preferences."""
    import json
    import os
    import tempfile
    folder = validate_folder(folder)
    settings_path = Path(settings_path)
    settings_path.parent.mkdir(parents=True, exist_ok=True)
    prefs = {}
    try:
        prefs = json.loads(settings_path.read_text(encoding='utf-8'))
        if not isinstance(prefs, dict):
            prefs = {}
    except (OSError, ValueError):
        pass
    prefs['download_folder'] = str(folder)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=settings_path.parent, delete=False) as handle:
            temporary = Path(handle.name)
            json.dump(prefs, handle, indent=2)
        os.replace(temporary, settings_path)
    finally:
        if temporary:
            temporary.unlink(missing_ok=True)
    return folder


def load_download_folder(settings_path, fallback):
    """Return the saved directory or an explicit fallback notice for unavailable drives."""
    import json
    settings_path = Path(settings_path)
    if not settings_path.exists():
        return Path(fallback), ''
    try:
        prefs = json.loads(settings_path.read_text(encoding='utf-8'))
        folder = prefs.get('download_folder')
        if not folder:
            return Path(fallback), ''
        return validate_folder(folder), ''
    except (OSError, ValueError, TypeError, AttributeError):
        return Path(fallback), 'Your saved download folder is unavailable. Using the standard Downloads/ClipPocket folder for now.'
