"""Exercise real Windows widgets with a deterministic folder-picker result."""
from pathlib import Path
import sys
import tempfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import app as module
from core import load_download_folder

with tempfile.TemporaryDirectory() as directory:
    root=Path(directory)
    module.DATA=root/'state';module.DATA.mkdir()
    default=root/'default';default.mkdir()
    chosen=root/'My custom video folder';chosen.mkdir()
    module.downloads_folder=lambda: default
    module.filedialog.askdirectory=lambda **kwargs: str(chosen)
    window=module.App()
    window.update()
    window.change_download_folder()
    assert window.folder==chosen.resolve()
    assert str(chosen.resolve()) in window.folder_label.cget('text')
    assert load_download_folder(window.settings_path,default)[0]==chosen.resolve()
    module.filedialog.askdirectory=lambda **kwargs: ''
    window.change_download_folder()
    assert window.folder==chosen.resolve()
    window.after(1000,window.destroy)
    window.mainloop()
print('Windows UI and folder selection smoke test passed')
