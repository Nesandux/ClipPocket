package com.clippocket.app;
import org.json.*;

public class PreviewDataTest {
    static int count;
    static void check(boolean pass,String reason){count++;if(!pass)throw new AssertionError(reason);}
    static void bytes(String json,double expected)throws Exception{double got=PreviewData.estimatedBytes(new JSONObject(json));check(Math.abs(got-expected)<.01,"Expected "+expected+" got "+got);}
    public static void main(String[] args)throws Exception{
        bytes("{\"filesize\":12000000,\"filesize_approx\":20000000}",12000000);
        bytes("{\"filesize\":null,\"filesize_approx\":8000000}",8000000);
        bytes("{\"duration\":60,\"tbr\":1000}",7500000);
        bytes("{\"requested_formats\":[{\"filesize\":20000000},{\"filesize\":3000000}]}",23000000);
        bytes("{\"duration\":60,\"requested_formats\":[{\"filesize_approx\":20000000},{\"tbr\":128}]}",20960000);
        bytes("{\"requested_formats\":[{\"filesize\":20000000},{}]}",-1);
        bytes("{\"filesize\":null,\"filesize_approx\":null,\"tbr\":null,\"duration\":null}",-1);
        bytes("{\"duration\":0,\"tbr\":128}",-1);
        bytes("{}",-1);
        check(SizeEstimate.bytes(Double.NaN,Double.POSITIVE_INFINITY,60,128)==960000,"Ignore invalid values");
        check(SizeEstimate.display(1500000000).equals("1.50 GB"),"Decimal GB");
        check(SizeEstimate.display(12500000).equals("12.5 MB"),"Decimal MB");
        check(SizeEstimate.display(-1).equals("Size unavailable"),"Unknown size");
        check(PreviewData.selectedQuality(new JSONObject("{\"height\":360}"),false).equals("360p video"),"Actual lower resolution");
        check(PreviewData.selectedQuality(new JSONObject("{\"requested_formats\":[{\"height\":1080},{\"vcodec\":\"none\"}]}"),false).equals("1080p video"),"Merged video height");
        check(PreviewData.selectedQuality(new JSONObject(),true).equals("Audio · M4A"),"Audio label");
        System.out.println("Passed "+count+" metadata, stream-size and quality checks");
    }
}
