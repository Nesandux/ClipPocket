package com.clippocket.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import com.yausername.youtubedl_android.*;
import com.yausername.ffmpeg.FFmpeg;
import kotlin.Unit;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class DownloadService extends Service {
    public static volatile boolean busy=false;
    volatile boolean cancelled=false; String artworkUrl=""; Thread worker; File jobDir; PowerManager.WakeLock wake;
    final String channel="downloads"; long lastUpdate=0;

    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){super.onCreate();NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel(channel,"Downloads",NotificationManager.IMPORTANCE_LOW));}
    Notification notification(String message,int progress){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent cancel=PendingIntent.getService(this,1,new Intent(this,DownloadService.class).setAction("cancel"),PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(this,channel).setSmallIcon(com.clippocket.app.R.drawable.ic_download).setContentTitle("Clip Pocket").setContentText(message).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).addAction(new Notification.Action.Builder(null,"Cancel",cancel).build());
        if(progress>=0)b.setProgress(100,progress,false);else b.setProgress(0,0,true);
        return b.build();
    }
    void status(String text,int pc){getSharedPreferences("downloads",0).edit().putString("status",text).putInt("progress",pc).apply();getSystemService(NotificationManager.class).notify(1,notification(text,pc));}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if("cancel".equals(intent.getAction())){cancelled=true;YoutubeDL.getInstance().destroyProcessById("download");if(worker!=null)worker.interrupt();if(!busy)stopSelf();return START_NOT_STICKY;}
        if(busy)return START_NOT_STICKY;
        busy=true;cancelled=false;startForeground(1,notification("Preparing download engine…",-1));status("Preparing download engine…",-1);
        artworkUrl=intent.getStringExtra("thumbnail");String url=intent.getStringExtra("url");String selectedFormat=intent.getStringExtra("selector");boolean isAudio=intent.getBooleanExtra("audio",false);boolean updating="update".equals(intent.getAction());
        worker=new Thread(()->{
            String result="Download stopped";int done=0;
            try{
                wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"ClipPocket:download");wake.acquire(TimeUnit.HOURS.toMillis(2));
                YoutubeDL.getInstance().init(getApplicationContext());FFmpeg.getInstance().init(getApplicationContext());checkCancel();
                if(updating){status("Checking for engine updates…",-1);YoutubeDL.getInstance().updateYoutubeDL(getApplicationContext(),YoutubeDL.UpdateChannel._STABLE);result="Download engine is up to date";done=100;}
                else{
                    if(!DownloadRules.validUrl(url))throw new IOException("Enter a valid HTTP or HTTPS video link");
                    jobDir=new File(getCacheDir(),"download-"+System.currentTimeMillis());if(!jobDir.mkdirs())throw new IOException("Could not create download folder");
                    YoutubeDLRequest request=new YoutubeDLRequest(url);
                    request.addOption("--no-playlist");request.addOption("--restrict-filenames");request.addOption("--no-mtime");request.addOption("--socket-timeout",30);request.addOption("--retries",3);
                    request.addOption("-o",new File(jobDir,"%(title).100s [%(id)s].%(ext)s").getAbsolutePath());
                    request.addOption("-f",selectedFormat==null?(isAudio?"bestaudio/best":DownloadRules.videoFormat(720)):selectedFormat);
                    if(isAudio){request.addOption("-x");request.addOption("--audio-format","m4a");}
                    else{request.addOption("--merge-output-format","mp4/mkv");}
                    status("Connecting to video site…",-1);checkCancel();
                    YoutubeDL.getInstance().execute(request,"download",(percent,eta,line)->{if(cancelled)throw new CancellationException();long now=System.currentTimeMillis();if(now-lastUpdate>500){lastUpdate=now;int value=Math.max(0,Math.min(100,percent.intValue()));status(value>=100?"Finishing video…":("Downloading · "+value+"%"),value);}return Unit.INSTANCE;});
                    checkCancel();File[] files=jobDir.listFiles(f->f.isFile()&&!f.getName().endsWith(".part")&&!f.getName().endsWith(".ytdl")&&!f.getName().endsWith(".temp"));
                    if(files==null||files.length==0)throw new IOException("No completed file received");
                    for(File file:files){checkCancel();status("Saving to Downloads…",100);save(file,isAudio);}
                    result="Saved to Downloads / ClipPocket";done=100;
                }
            }catch(Exception e){if(cancelled||e instanceof InterruptedException||e instanceof YoutubeDL.CanceledException)result="Download cancelled";else{String message=e.getMessage();if(message==null)message=e.getClass().getSimpleName();if(message.length()>650)message=message.substring(message.length()-650);result="Could not finish: "+message+"\nTry a public video link or update the download engine. Some sites and videos are unsupported.";}}
            finally{remove(jobDir);if(wake!=null&&wake.isHeld())wake.release();getSharedPreferences("downloads",0).edit().putString("status",result).putInt("progress",done).apply();busy=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
        },"ClipPocket-download");worker.start();return START_NOT_STICKY;
    }
    void checkCancel() throws InterruptedException{if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedException();}
    void save(File file,boolean audio)throws Exception{
        String mime=DownloadRules.mimeType(file.getName(),audio);
        ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,file.getName());values.put(MediaStore.MediaColumns.MIME_TYPE,mime);values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/ClipPocket");values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("Could not create file in Downloads");
        try{try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Storage is unavailable");byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){checkCancel();out.write(buffer,0,n);}}
            values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);getContentResolver().update(uri,values,null,null);
            android.content.SharedPreferences p=getSharedPreferences("downloads",0);JSONArray old=new JSONArray(p.getString("history","[]"));JSONArray entries=new JSONArray();JSONObject item=new JSONObject();item.put("name",file.getName());item.put("uri",uri.toString());item.put("mime",mime);item.put("size",file.length());item.put("date",System.currentTimeMillis()/1000);entries.put(item);for(int i=0;i<old.length()&&i<29;i++)entries.put(old.get(i));p.edit().putString("history",entries.toString()).apply();
            ArtworkStore.save(this,uri,artworkUrl);
        }catch(Exception e){getContentResolver().delete(uri,null,null);throw e;}
    }
    void remove(File f){if(f==null)return;if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File child:children)remove(child);}f.delete();}
    @Override public void onDestroy(){if(busy){cancelled=true;YoutubeDL.getInstance().destroyProcessById("download");if(worker!=null)worker.interrupt();}super.onDestroy();}
    @Override public void onTimeout(int startId,int fgsType){cancelled=true;YoutubeDL.getInstance().destroyProcessById("download");if(worker!=null)worker.interrupt();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
}
