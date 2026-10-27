package com.clippocket.app;
import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Exposes only the verified update APK to the Android installer, by temporary URI grant. */
public class UpdateFileProvider extends ContentProvider {
    static File file(Context c){return new File(new File(c.getCacheDir(),"updates"),"clip-pocket-update.apk");}
    private void validate(Uri uri){if(!"/update.apk".equals(uri.getPath()))throw new SecurityException("Unknown file");}
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){validate(uri);return "application/vnd.android.package-archive";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{validate(uri);if(!"r".equals(mode))throw new SecurityException("Read only");return ParcelFileDescriptor.open(file(getContext()),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){validate(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]="clip-pocket-update.apk";else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=file(getContext()).length();}cursor.addRow(row);return cursor;}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
