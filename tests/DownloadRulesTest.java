package com.clippocket.app;

public class DownloadRulesTest {
    static int count;
    static void check(boolean result, String reason) { count++; if (!result) throw new AssertionError(reason); }
    public static void main(String[] args) {
        String[] links = {"https://youtu.be/example", "https://www.youtube.com/watch?v=example", "https://www.facebook.com/reel/123/", "https://fb.watch/abc/", "https://www.instagram.com/reel/abc/", "https://www.tiktok.com/@creator/video/123", "https://vm.tiktok.com/abc/", "https://vimeo.com/123", "https://cdn.example.com/video.mp4?token=abc", "HTTPS://example.com", "https://example.com"};
        for (String link : links) check(DownloadRules.validUrl(link), "Rejected web link: " + link);
        for (String link : new String[]{"", "not a url", "javascript:alert(1)", "file:///sdcard/test", "ftp://example.com/video", "https:///missing-host", "https://user:password@example.com/v", "https://example.com/bad path", "--help"}) check(!DownloadRules.validUrl(link), "Accepted invalid input: " + link);
        check(!DownloadRules.validUrl(null), "Null link");
        check(DownloadRules.extractUrl("Watch this! https://www.instagram.com/reel/abc/ more text").equals("https://www.instagram.com/reel/abc/"), "Instagram share text");
        check(DownloadRules.extractUrl("TikTok video: https://vm.tiktok.com/abc/.").equals("https://vm.tiktok.com/abc/"), "TikTok punctuation");
        check(DownloadRules.extractUrl("(https://fb.watch/abc/)").equals("https://fb.watch/abc/"), "Facebook brackets");
        check(DownloadRules.extractUrl("https://cdn.example.com/v.mp4?token=a%2Fb&x=1").endsWith("token=a%2Fb&x=1"), "Preserve signed query");
        check(DownloadRules.mimeType("clip.WEBM",false).equals("video/webm"),"WebM MIME");
        check(DownloadRules.mimeType("clip.mov",false).equals("video/quicktime"),"MOV MIME");
        check(DownloadRules.mimeType("clip.mkv",false).equals("video/x-matroska"),"MKV MIME");
        check(DownloadRules.mimeType("clip.m4a",true).equals("audio/mp4"),"Audio MIME");
        check(DownloadRules.mimeType("clip.unknown",false).equals("application/octet-stream"),"Unknown extension must not be labelled MP4");
        check(DownloadRules.videoFormat(720).contains("height<=?720"),"Allow missing height metadata");
        check(DownloadRules.videoFormat(1080).contains("/bestvideo[height<=?1080]+bestaudio"),"Non-MP4 separate streams fallback");
        System.out.println("Passed " + count + " URL, shared-link, MIME and format checks");
    }
}
