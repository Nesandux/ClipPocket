package com.clippocket.app;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;

/** User-initiated updater for the separately distributed APK, not the Play Store. */
final class AppUpdates {
    private static final String RELEASES="https://api.github.com/repos/Nesandux/ClipPocket/releases/latest";
    private static final String ASSET_PREFIX="https://github.com/Nesandux/ClipPocket/releases/download/";
    private final MainActivity activity;
    AppUpdates(MainActivity activity){this.activity=activity;}
    private void ui(Runnable action){activity.runOnUiThread(()->{if(!activity.isFinishing()&&!activity.isDestroyed())action.run();});}
    private ProgressDialog loading(String message){ProgressDialog d=new ProgressDialog(activity);d.setMessage(message);d.setIndeterminate(true);d.setCancelable(false);d.show();return d;}
    private void message(String title,String message){new AlertDialog.Builder(activity).setTitle(title).setMessage(message).setPositiveButton("OK",null).show();}
    void check(){
        ProgressDialog dialog=loading("Checking for updates…");
        activity.io.submit(()->{try{
            HttpURLConnection c=(HttpURLConnection)new URL(RELEASES).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("User-Agent","Clip-Pocket-Android");
            JSONObject release;try{if(c.getResponseCode()==404)throw new IOException("No published release is available yet.");if(c.getResponseCode()!=200)throw new IOException("GitHub returned HTTP "+c.getResponseCode());try(InputStream in=c.getInputStream()){release=new JSONObject(readText(in,2_000_000));}}finally{c.disconnect();}
            String version=release.optString("tag_name","");String current=activity.appVersion();
            if(VersionOrder.compare(version,current)<=0){ui(()->{dialog.dismiss();message("You're up to date","You have version "+current+".");});return;}
            JSONArray assets=release.optJSONArray("assets");JSONObject asset=null;if(assets!=null)for(int i=0;i<assets.length();i++){JSONObject candidate=assets.optJSONObject(i);if(candidate!=null&&candidate.optString("name").toLowerCase(Locale.ROOT).endsWith(".apk")&&candidate.optString("browser_download_url").startsWith(ASSET_PREFIX)){asset=candidate;break;}}
            if(asset==null)throw new IOException("The latest release does not contain a Clip Pocket APK.");
            final String url=asset.optString("browser_download_url"),digest=asset.isNull("digest")?"":asset.optString("digest","");String notes=release.optString("body","");if(notes.length()>1000)notes=notes.substring(0,1000)+"…";final String details=notes.trim().isEmpty()?"A new update is available.":notes;
            ui(()->{dialog.dismiss();new AlertDialog.Builder(activity).setTitle("Update "+version+" available").setMessage(details+"\n\nAndroid will ask you to confirm installation.").setNegativeButton("Later",null).setPositiveButton("Download update",(d,w)->download(url,digest,version)).show();});
        }catch(Exception e){ui(()->{dialog.dismiss();message("Could not check for updates",friendly(e));});}});
    }
    private void download(String url,String digest,String releaseVersion){
        if(!url.startsWith(ASSET_PREFIX)){message("Update unavailable","The download address is not a Clip Pocket release.");return;}
        ProgressDialog dialog=new ProgressDialog(activity);dialog.setTitle("Downloading update");dialog.setMessage("Please wait…");dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);dialog.setIndeterminate(true);dialog.setCancelable(false);dialog.show();
        activity.io.submit(()->{File dir=new File(activity.getCacheDir(),"updates"),tmp=new File(dir,"update.part.apk"),result=UpdateFileProvider.file(activity);try{
            if(!dir.exists()&&!dir.mkdirs())throw new IOException("Unable to create update folder.");
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","Clip-Pocket-Android");MessageDigest sha=MessageDigest.getInstance("SHA-256");
            try{if(c.getResponseCode()!=200)throw new IOException("Download failed (HTTP "+c.getResponseCode()+").");long expected=c.getContentLengthLong(),total=0;try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(tmp)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Download interrupted");total+=n;if(total>512L*1024*1024)throw new IOException("Update exceeds the size limit.");out.write(buf,0,n);sha.update(buf,0,n);if(expected>0){int progress=(int)Math.min(100,total*100/expected);ui(()->{dialog.setIndeterminate(false);dialog.setMax(100);dialog.setProgress(progress);});}}}}finally{c.disconnect();}
            if(digest.startsWith("sha256:")&&!digest.substring(7).equalsIgnoreCase(hex(sha.digest())))throw new IOException("Update checksum did not match the release.");
            verifyPackage(tmp,releaseVersion);
            if(result.exists()&&!result.delete())throw new IOException("Could not replace previous download.");if(!tmp.renameTo(result))throw new IOException("Could not save update.");
            ui(()->{dialog.dismiss();new AlertDialog.Builder(activity).setTitle("Update ready").setMessage("The update was verified. Continue to Android's installer?").setNegativeButton("Later",null).setPositiveButton("Install",(d,w)->requestInstall()).show();});
        }catch(Exception e){tmp.delete();ui(()->{dialog.dismiss();message("Update failed",friendly(e));});}});
    }
    private void verifyPackage(File apk,String releaseVersion)throws Exception{
        PackageManager pm=activity.getPackageManager();PackageInfo incoming=pm.getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES),installed=pm.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(incoming==null||!activity.getPackageName().equals(incoming.packageName))throw new IOException("The downloaded file is not a Clip Pocket APK.");
        if(VersionOrder.compare(releaseVersion,incoming.versionName)!=0)throw new IOException("APK version does not match its release tag.");
        if(incoming.getLongVersionCode()<=installed.getLongVersionCode())throw new IOException("The downloaded version is not newer than your installed app.");
        if(incoming.signingInfo==null||installed.signingInfo==null)throw new IOException("Could not verify the update signature.");
        android.content.pm.Signature[] a=incoming.signingInfo.getApkContentsSigners(),b=installed.signingInfo.getApkContentsSigners();
        if(a.length!=b.length)throw new IOException("Update signing certificate does not match.");
        for(android.content.pm.Signature signature:a){boolean found=false;for(android.content.pm.Signature expected:b)if(signature.equals(expected)){found=true;break;}if(!found)throw new IOException("Update signing certificate does not match.");}
    }
    void requestInstall(){
        if(!UpdateFileProvider.file(activity).isFile()){message("Update unavailable","Check for updates and download it again.");return;}
        if(!activity.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(activity).setTitle("Allow installation").setMessage("Android needs permission for Clip Pocket to request installation. You will still confirm the update in Android's installer.").setNegativeButton("Cancel",null).setPositiveButton("Open settings",(d,w)->{activity.pendingUpdateInstall=true;try{activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())),MainActivity.UPDATE_PERMISSION_REQUEST);}catch(Exception e){activity.pendingUpdateInstall=false;message("Settings unavailable",friendly(e));}}).show();return;
        }
        try{Uri uri=Uri.parse("content://"+activity.getPackageName()+".updatefiles/update.apk");Intent install=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);activity.startActivity(install);}catch(Exception e){message("Could not open installer",friendly(e));}
    }
    private static String readText(InputStream in,int limit)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("Release details are too large.");out.write(b,0,n);}return out.toString("UTF-8");}
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
    private static String friendly(Exception e){String m=e.getMessage();return m==null||m.isEmpty()?"Please check your connection and try again.":m;}
}
