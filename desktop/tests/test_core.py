import tempfile
from pathlib import Path
import unittest
from core import extract_url, selector, size_label, safe_media

class CoreTests(unittest.TestCase):
    def test_shared_url(self):
        self.assertEqual(extract_url('Watch https://example.com/video?id=4 now'), 'https://example.com/video?id=4')
        self.assertEqual(extract_url('file:///etc/passwd'),'')
        self.assertEqual(extract_url('https://'),'')
    def test_quality_caps_both_fallbacks(self):
        self.assertEqual(selector('Video · 720p'), 'bestvideo[height<=720]+bestaudio/best[height<=720]')
        self.assertEqual(selector('Audio · M4A'),'bestaudio/best')
    def test_size_combines_streams(self):
        self.assertEqual(size_label({'requested_formats':[{'filesize':2e6},{'filesize':1e6}]}),'3.0 MB')
    def test_unknown_does_not_show_partial_size(self):
        self.assertEqual(size_label({'requested_formats':[{'filesize':2e6},{}]}),'Size unavailable')
    def test_estimated_bitrate(self):
        self.assertEqual(size_label({'duration':80,'tbr':1000}),'≈ 10.0 MB')
    def test_only_media_in_target_folder(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); video=root/'video.mp4'; video.touch()
            other=root/'app.exe'; other.touch()
            nested=root/'nested'; nested.mkdir(); child=nested/'video.mp4';child.touch()
            self.assertTrue(safe_media(video,root))
            self.assertFalse(safe_media(other,root))
            self.assertFalse(safe_media(child,root))
            self.assertFalse(safe_media(root/'missing.mp4',root))

if __name__ == '__main__':
    unittest.main()
