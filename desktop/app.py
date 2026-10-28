"""Clip Pocket for Windows. Network and engine work never runs on the UI thread."""
import ctypes
import io
import json
import os
from pathlib import Path
import queue
import shutil
import subprocess
import sys
import threading
import tkinter as tk
from tkinter import messagebox
import urllib.request
import webbrowser
import customtkinter as ctk
from PIL import Image
from core import MEDIA, extract_url, selector, size_label, safe_media

VERSION = '1.0.0'
ROOT = Path(getattr(sys, '_MEIPASS', Path(__file__).parent))
DATA = Path(os.environ.get('LOCALAPPDATA', Path.home() / '.local/share')) / 'ClipPocket'
DATA.mkdir(parents=True, exist_ok=True)

def downloads_folder():
    base = Path.home() / 'Downloads'
    if sys.platform == 'win32':
        import winreg
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r'Software\Microsoft\Windows\CurrentVersion\Explorer\User Shell Folders') as key:
                base = Path(os.path.expandvars(winreg.QueryValueEx(key, '{374DE290-123F-4565-9164-39C4925E467B}')[0]))
        except OSError:
            pass
    folder = base / 'ClipPocket'
    folder.mkdir(parents=True, exist_ok=True)
    return folder

class App(ctk.CTk):
    def __init__(self):
        ctk.set_appearance_mode('system')
        ctk.set_default_color_theme('blue')
        super().__init__()
        self.title('Clip Pocket')
        self.geometry('880x740')
        self.minsize(620, 620)
        self.configure(fg_color=('#F2F2F7', '#101116'))
        self.folder = downloads_folder()
        self.events = queue.Queue()
        self.generation = 0
        self.timer = None
        self.ready = None
        self.busy = False
        self.cancelled = threading.Event()
        self.process = None
        self.closing = False
        self.engine_lock = threading.Lock()
        self.photos = []
        self.url = tk.StringVar()
        self.url.trace_add('write', self.changed)
        header = ctk.CTkFrame(self, fg_color='transparent')
        header.pack(fill='x', padx=28, pady=(24, 16))
        ctk.CTkLabel(header, text='↓  Clip Pocket', font=ctk.CTkFont(size=26, weight='bold')).pack(side='left')
        ctk.CTkButton(header, text='•••', width=46, command=self.menu).pack(side='right')
        ctk.CTkButton(header, text='Downloads', width=112, command=self.show_downloads).pack(side='right', padx=10)
        self.body = ctk.CTkFrame(self, fg_color='transparent')
        self.body.pack(fill='both', expand=True, padx=28, pady=(0, 22))
        self.main = ctk.CTkFrame(self.body, fg_color='transparent')
        self.main.pack(fill='both', expand=True)
        input_card = ctk.CTkFrame(self.main, corner_radius=22)
        input_card.pack(fill='x', pady=(0, 16))
        row = ctk.CTkFrame(input_card, fg_color='transparent')
        row.pack(fill='x', padx=16, pady=(16, 10))
        self.entry = ctk.CTkEntry(row, textvariable=self.url, placeholder_text='Paste a video / reel link…', height=46)
        self.entry.pack(side='left', fill='x', expand=True)
        self.clear_button = ctk.CTkButton(row, text='×', width=42, height=46, command=lambda: self.url.set(''))
        self.clear_button.pack(side='right', padx=(8, 0))
        row2 = ctk.CTkFrame(input_card, fg_color='transparent')
        row2.pack(fill='x', padx=16, pady=(0, 16))
        self.paste_button = ctk.CTkButton(row2, text='Paste link', width=110, height=40, command=self.paste)
        self.paste_button.pack(side='left', padx=(0, 12))
        self.quality = ctk.CTkOptionMenu(row2, values=[f'Video · {n}p' for n in (144,240,360,480,720,1080,1440,2160)] + ['Audio · M4A'], command=lambda _: self.changed())
        self.quality.set('Video · 720p')
        self.quality.pack(side='left', fill='x', expand=True)
        preview = ctk.CTkFrame(self.main, corner_radius=22)
        preview.pack(fill='both', expand=True)
        self.art = ctk.CTkLabel(preview, text='Paste a link to see its preview', height=180)
        self.art.pack(fill='both', expand=True, padx=20, pady=(20, 12))
        self.title_text = ctk.CTkLabel(preview, text='Ready when you are', font=ctk.CTkFont(size=20, weight='bold'), anchor='w', wraplength=650)
        self.title_text.pack(fill='x', padx=20)
        self.desc = ctk.CTkLabel(preview, text='', anchor='w', justify='left', wraplength=650)
        self.desc.pack(fill='x', padx=20, pady=8)
        self.desc.bind('<Button-1>', lambda _: self.details())
        self.size = ctk.CTkLabel(preview, text='', anchor='w', text_color=('#007AFF','#82B4FF'))
        self.size.pack(fill='x', padx=20, pady=(0, 16))
        self.progress = ctk.CTkProgressBar(self.main)
        self.progress.pack(fill='x', pady=(16, 12))
        self.progress.set(0)
        actions = ctk.CTkFrame(self.main, fg_color='transparent')
        actions.pack(fill='x')
        self.download_button = ctk.CTkButton(actions, text='↓  Download', height=46, command=self.download, state='disabled')
        self.download_button.pack(side='left', fill='x', expand=True)
        self.cancel_button = ctk.CTkButton(actions, text='Cancel', width=90, height=46, command=self.cancel, state='disabled')
        self.cancel_button.pack(side='right', padx=(10, 0))
        self.status = ctk.CTkLabel(self.main, text='Files save to Downloads / ClipPocket', anchor='w')
        self.status.pack(fill='x', pady=(6, 0))
        self.protocol('WM_DELETE_WINDOW', self.close)
        self.after(100, self.poll)

    def post(self, callback):
        if not self.closing:
            self.events.put(callback)

    def poll(self):
        while not self.events.empty():
            self.events.get()()
        if not self.closing:
            self.after(100, self.poll)

    def worker(self, func):
        threading.Thread(target=func, daemon=True).start()

    def engine(self):
        target = DATA / 'yt-dlp.exe'
        if not target.exists():
            with self.engine_lock:
                return self.install_engine(target)
        return target

    def install_engine(self, target):
        if not target.exists():
            bundled = ROOT / 'tools' / 'yt-dlp.exe'
            if not bundled.exists():
                raise RuntimeError('Download the Windows build or run build_windows.py first.')
            shutil.copy2(bundled, target)
        return target

    def command(self, args):
        return [str(self.engine()), '--ignore-config', '--no-playlist', '--socket-timeout', '20',
                '--ffmpeg-location', str(ROOT / 'tools'), '--js-runtimes', 'deno:' + str(ROOT / 'tools' / 'deno.exe')] + args

    def run(self, args, timeout=90):
        result = subprocess.run(self.command(args), capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=timeout,
                                creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        if result.returncode:
            raise RuntimeError(result.stderr[-1300:] or 'The site could not provide this video.')
        return result.stdout

    def paste(self):
        try:
            value = self.clipboard_get()
        except tk.TclError:
            return
        self.url.set(extract_url(value) or value.strip())
        self.changed()

    def changed(self, *_):
        self.generation += 1
        self.ready = None
        if not hasattr(self, 'download_button'):
            return
        self.download_button.configure(state='disabled')
        if self.timer:
            self.after_cancel(self.timer)
        if not self.url.get().strip():
            self.progress.stop()
            self.progress.configure(mode='determinate')
            self.progress.set(0)
            self.art.configure(image=None, text='Paste a link to see its preview')
            self.title_text.configure(text='Ready when you are')
            self.desc.configure(text='')
            self.size.configure(text='')
            return
        self.timer = self.after(700, self.preview)

    def preview(self):
        self.timer = None
        url, quality, gen = extract_url(self.url.get()), self.quality.get(), self.generation
        if not url:
            self.status.configure(text='Enter a valid HTTP or HTTPS link.')
            return
        self.art.configure(image=None, text='Loading preview…')
        self.progress.configure(mode='indeterminate')
        self.progress.start()
        def work():
            try:
                info = json.loads(self.run(['--dump-single-json', '--skip-download', '-f', selector(quality), '--', url]))
                image = None
                thumb = info.get('thumbnail', '')
                if thumb.startswith('https://'):
                    try:
                        with urllib.request.urlopen(thumb, timeout=15) as response:
                            raw = response.read(8 * 1024 * 1024 + 1)
                        if len(raw) <= 8 * 1024 * 1024:
                            image = Image.open(io.BytesIO(raw)).convert('RGB')
                            image.thumbnail((1000, 600))
                    except Exception:
                        pass
                self.post(lambda: self.preview_done(gen, url, quality, info, image))
            except Exception as error:
                message = str(error)
                self.post(lambda: self.preview_error(gen, message))
        self.worker(work)

    def preview_done(self, gen, url, quality, info, image):
        if gen != self.generation:
            return
        self.progress.stop()
        self.progress.configure(mode='determinate')
        self.progress.set(0)
        self.ready = (url, quality, info, image)
        self.title_text.configure(text=info.get('title', 'Untitled video'))
        self.desc.configure(text=(info.get('description') or 'No description available')[:180] + '  · Tap for details')
        self.size.configure(text=size_label(info) + ' · Selected quality depends on source formats')
        if image:
            ratio = min(560 / image.width, 220 / image.height)
            self.photo = ctk.CTkImage(image, size=(max(1,int(image.width*ratio)), max(1,int(image.height*ratio))))
            self.art.configure(image=self.photo, text='')
        else:
            self.art.configure(image=None, text='Thumbnail unavailable')
        self.download_button.configure(state='disabled' if self.busy else 'normal')
        self.status.configure(text='Preview ready. Size is an estimate; final usage may differ.')

    def preview_error(self, gen, message):
        if gen != self.generation:
            return
        self.progress.stop()
        self.progress.configure(mode='determinate')
        self.progress.set(0)
        self.art.configure(image=None, text='Preview unavailable')
        self.status.configure(text='Try another quality or update the download engine.')
        self.desc.configure(text=message[-200:])

    def details(self):
        if self.ready:
            self.panel('Video details', self.ready[2].get('description') or 'No description available.')

    def set_busy(self, value):
        self.busy = value
        for control in (self.entry, self.clear_button, self.paste_button, self.quality):
            control.configure(state='disabled' if value else 'normal')
        self.cancel_button.configure(state='normal' if value else 'disabled')
        self.download_button.configure(state='normal' if self.ready and not value else 'disabled')

    def download(self):
        if not self.ready or self.busy:
            return
        url, quality, info, image = self.ready
        self.set_busy(True)
        self.cancelled.clear()
        def work():
            message = 'Download cancelled'
            try:
                args = ['--newline', '--progress-template', 'download:CP_PROGRESS:%(progress)j', '--print', 'after_move:CP_FILE:%(filepath)s',
                        '--no-simulate', '--windows-filenames', '--no-overwrites', '--restrict-filenames', '-P', str(self.folder),
                        '-o', '%(title).100s [%(id)s].%(ext)s', '-f', selector(quality)]
                args += ['-x', '--audio-format', 'm4a'] if quality.startswith('Audio') else ['--merge-output-format', 'mp4/mkv']
                proc = subprocess.Popen(self.command(args + ['--', url]), stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                        text=True, encoding='utf-8', errors='replace', creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
                self.process = proc
                if self.cancelled.is_set():
                    self.stop_process(proc)
                last_lines = []
                for line in proc.stdout:
                    last_lines.append(line.strip())
                    last_lines = last_lines[-12:]
                    if self.cancelled.is_set():
                        self.stop_process(proc)
                        break
                    if line.startswith('CP_PROGRESS:'):
                        progress = json.loads(line[len('CP_PROGRESS:'):])
                        total = progress.get('total_bytes') or progress.get('total_bytes_estimate') or 0
                        fraction = min(1, progress.get('downloaded_bytes', 0)/total) if total else 0
                        self.post(lambda n=fraction: (self.progress.set(n), self.status.configure(text=f'Downloading · {n:.0%}')))
                    elif line.startswith('CP_FILE:'):
                        path = Path(line[len('CP_FILE:'):].strip())
                        if image and safe_media(path, self.folder):
                            image.save(DATA / (path.name + '.jpg'), 'JPEG')
                code = proc.wait()
                if not self.cancelled.is_set():
                    if code:
                        raise RuntimeError('\n'.join(last_lines)[-1000:])
                    message = 'Saved to Downloads / ClipPocket'
            except Exception as error:
                if self.process:
                    self.stop_process(self.process)
                message = 'Could not finish: ' + str(error)[-350:]
            finally:
                self.process = None
                self.post(lambda m=message: (self.set_busy(False), self.progress.set(0), self.status.configure(text=m)))
        self.worker(work)

    def stop_process(self, proc):
        if proc.poll() is not None:
            return
        if sys.platform == 'win32':
            subprocess.run(['taskkill', '/PID', str(proc.pid), '/T', '/F'], capture_output=True, creationflags=subprocess.CREATE_NO_WINDOW)
        else:
            proc.terminate()

    def cancel(self):
        self.cancelled.set()
        proc = self.process
        if proc:
            self.worker(lambda: self.stop_process(proc))

    def menu(self):
        panel = self.panel('Clip Pocket', '')
        for title, action in [('Appearance', self.appearance), ('Update download engine', self.update_engine), ('About Clip Pocket', self.about)]:
            ctk.CTkButton(panel.content, text=title, height=44, command=lambda f=action: (panel.destroy(), f())).pack(fill='x', pady=5)

    def panel(self, title, text):
        win = ctk.CTkToplevel(self)
        win.title(title)
        win.geometry('500x420')
        win.transient(self)
        win.after(80, win.lift)
        content = ctk.CTkScrollableFrame(win, corner_radius=20)
        content.pack(fill='both', expand=True, padx=20, pady=20)
        ctk.CTkLabel(content, text=title, font=ctk.CTkFont(size=22, weight='bold')).pack(pady=(8,16))
        if text:
            ctk.CTkLabel(content, text=text, wraplength=410, justify='left').pack(fill='x', pady=10)
        win.content = content
        ctk.CTkButton(win, text='Close', command=win.destroy).pack(pady=(0,16))
        return win

    def appearance(self):
        win = self.panel('Appearance', 'Match your device, or choose a theme.')
        ctk.CTkOptionMenu(win.content, values=['System','Light','Dark'], command=ctk.set_appearance_mode).pack(pady=20)

    def about(self):
        win = self.panel('About Clip Pocket', f'Clip Pocket for Windows · {VERSION}\n\nDeveloped by Nesandu.\nSave video and audio from supported websites.\n\nPowered by yt-dlp, FFmpeg, Deno and CustomTkinter.\nOnly save content you own or have permission to download.')
        for text, url in [('GitHub', 'https://github.com/Nesandux/ClipPocket'), ('Facebook','https://www.facebook.com/nesandu.nimneth')]:
            ctk.CTkButton(win.content, text=text, command=lambda u=url: webbrowser.open(u)).pack(pady=5)

    def update_engine(self):
        if self.busy:
            messagebox.showinfo('Please wait', 'Finish or cancel the download first.', parent=self)
            return
        self.set_busy(True)
        self.cancelled.clear()
        self.status.configure(text='Checking for download engine updates…')
        def work():
            try:
                proc = subprocess.Popen(self.command(['--update']), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace', creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
                self.process = proc
                if self.cancelled.is_set():
                    self.stop_process(proc)
                result, _ = proc.communicate(timeout=180)
                if proc.returncode and not self.cancelled.is_set():
                    result = 'Engine update failed.\n' + result
                if self.cancelled.is_set():
                    result = 'Engine update cancelled.'
            except Exception as error:
                if self.process:
                    self.stop_process(self.process)
                result = str(error)
            finally:
                self.process = None
            self.post(lambda m=result: (self.set_busy(False), self.panel('Download engine', m), self.status.configure(text='Engine check finished')))
        self.worker(work)

    def show_downloads(self):
        win = self.panel('Downloads', '')
        win.geometry('720x580')
        for path in sorted(self.folder.iterdir(), key=lambda p: p.stat().st_mtime, reverse=True):
            if not safe_media(path, self.folder):
                continue
            row = ctk.CTkFrame(win.content, corner_radius=16)
            row.pack(fill='x', pady=5)
            art_path = DATA / (path.name + '.jpg')
            photo = None
            if art_path.exists():
                try:
                    image = Image.open(art_path)
                    photo = ctk.CTkImage(image, size=(80,50))
                    self.photos.append(photo)
                except OSError:
                    pass
            ctk.CTkButton(row, text='' if photo else ('♫' if path.suffix in ('.mp3','.m4a','.opus','.ogg','.wav') else '▶'), image=photo, width=84, height=54, command=lambda p=path: self.play(p)).pack(side='left', padx=10, pady=10)
            ctk.CTkLabel(row, text=path.name + f'\n{path.stat().st_size/1e6:.1f} MB', wraplength=380, justify='left').pack(side='left', fill='x', expand=True)
            ctk.CTkButton(row, text='⌫', width=40, fg_color='#C43D46', command=lambda p=path, r=row: self.delete(p,r)).pack(side='right', padx=10)
        if not any(safe_media(p,self.folder) for p in self.folder.iterdir()):
            ctk.CTkLabel(win.content, text='Your completed videos and audio will appear here.').pack(pady=30)

    def play(self, path):
        if safe_media(path, self.folder):
            try:
                os.startfile(str(path))
            except OSError as error:
                messagebox.showerror('Could not open file', str(error), parent=self)

    def delete(self, path, row):
        if safe_media(path,self.folder) and messagebox.askyesno('Delete file?', f'Permanently delete {path.name}?', parent=self):
            try:
                path.unlink()
                (DATA / (path.name + '.jpg')).unlink(missing_ok=True)
                row.destroy()
            except OSError as error:
                messagebox.showerror('Could not delete file', str(error), parent=self)

    def close(self):
        if self.busy and not messagebox.askyesno('Close Clip Pocket?', 'Stop the current task and close?', parent=self):
            return
        self.cancel()
        self.closing = True
        self.destroy()

if __name__ == '__main__':
    App().mainloop()
