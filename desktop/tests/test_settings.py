import json
from pathlib import Path
import tempfile
import unittest
from core import load_download_folder, save_download_folder

class FolderSettingsTests(unittest.TestCase):
    def test_first_run_default(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            self.assertEqual(load_download_folder(root/'prefs.json',root),(root,''))

    def test_choice_persists_and_preserves_existing_preferences(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); selected=root/'Videos with spaces';selected.mkdir()
            prefs=root/'settings.json';prefs.write_text('{"theme":"dark"}')
            save_download_folder(prefs,selected)
            self.assertEqual(load_download_folder(prefs,root),(selected.resolve(),''))
            self.assertEqual(json.loads(prefs.read_text())['theme'],'dark')
            self.assertEqual(list(selected.iterdir()),[])

    def test_failed_choice_does_not_replace_saved_setting(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);prefs=root/'settings.json'
            save_download_folder(prefs,root)
            previous=prefs.read_text()
            with self.assertRaises(ValueError):
                save_download_folder(prefs,root/'missing')
            self.assertEqual(prefs.read_text(),previous)

    def test_offline_folder_and_corrupt_settings_report_fallback(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);prefs=root/'settings.json'
            prefs.write_text(json.dumps({'download_folder':str(root/'offline')}))
            folder,notice=load_download_folder(prefs,root)
            self.assertEqual(folder,root);self.assertTrue(notice)
            prefs.write_text('not json')
            self.assertTrue(load_download_folder(prefs,root)[1])

    def test_existing_files_are_not_moved(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);old=root/'old';new=root/'new';old.mkdir();new.mkdir()
            file=old/'video.mp4';file.write_bytes(b'video')
            save_download_folder(root/'prefs.json',new)
            self.assertEqual(file.read_bytes(),b'video');self.assertEqual(list(new.iterdir()),[])
