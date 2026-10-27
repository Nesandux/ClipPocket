package com.clippocket.app;
import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.net.*;

final class ArtworkStore {
    static File file(Context context,Uri uri){return new File(new File(context.getFilesDir(),"artwork"),uri.getLastPathSegment().replaceAll("[^0-9]","")+".image");}
    static void save(Context context,Uri uri,String url){
        if(!DownloadRules.validUrl(url))return;
        HttpURLConnection connection=null;File temporary=null;
        try{connection=(HttpURLConnection)new URL(url).openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(5000);if(connection.getContentLengthLong()>4_000_000)return;
            File output=file(context,uri);output.getParentFile().mkdirs();temporary=new File(output.getPath()+".tmp");
            try(InputStream input=connection.getInputStream();OutputStream out=new FileOutputStream(temporary)){byte[] buffer=new byte[8192];int n,total=0;while((n=input.read(buffer))!=-1){total+=n;if(total>4_000_000||Thread.currentThread().isInterrupted())return;out.write(buffer,0,n);}}
            temporary.renameTo(output);
        }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();if(temporary!=null)temporary.delete();}
    }
}
