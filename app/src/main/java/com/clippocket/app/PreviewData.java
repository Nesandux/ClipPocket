package com.clippocket.app;
import org.json.*;

final class PreviewData {
    static double estimatedBytes(JSONObject info) {
        JSONArray streams = info.optJSONArray("requested_formats");
        if (streams != null && streams.length() > 0) {
            double total=0;
            for(int i=0;i<streams.length();i++) {
                JSONObject stream=streams.optJSONObject(i);
                if(stream==null) return -1;
                double value=streamBytes(stream,info.optDouble("duration",-1));
                if(value<0) return -1; // Do not show a misleading partial total.
                total+=value;
            }
            return total;
        }
        return streamBytes(info,info.optDouble("duration",-1));
    }
    static double streamBytes(JSONObject stream,double duration) {
        return SizeEstimate.bytes(stream.optDouble("filesize",-1),stream.optDouble("filesize_approx",-1),duration,stream.optDouble("tbr",-1));
    }
    static String selectedQuality(JSONObject info, boolean audio) {
        if(audio) return "Audio · M4A";
        double height=info.optDouble("height",-1);
        JSONArray streams=info.optJSONArray("requested_formats");
        if(streams!=null) for(int i=0;i<streams.length();i++) {
            JSONObject s=streams.optJSONObject(i);
            if(s!=null) height=Math.max(height,s.optDouble("height",-1));
        }
        return height>0 ? ((int)height)+"p video" : "Video · resolution unavailable";
    }
}
