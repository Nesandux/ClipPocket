package com.clippocket.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.ClipboardManager;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.media.MediaMetadataRetriever;
import android.os.*;
import android.provider.MediaStore;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.yausername.youtubedl_android.*;
import com.yausername.ffmpeg.FFmpeg;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    int bg,card,green,solid,white,muted,control;
    final int[] heights={144,240,360,480,720,1080,1440,2160,0};
    EditText link; Spinner quality; Button download,cancel,paste,openDownloads,closeLibrary; TextView screenTitle; TextView state,previewStatus,videoTitle,description,size,libraryStatus;
    ImageView thumbnail; LinearLayout downloadPanel,libraryPanel,previewBox,history,mainContent,inputBox,aboutPanel; ProgressBar progress,thumbnailProgress;
    boolean deleting=false; int layoutMode=-1; Button[] filterTabs=new Button[3];
    final Handler handler=new Handler(); final ExecutorService io=Executors.newCachedThreadPool(); final ExecutorService thumbs=Executors.newFixedThreadPool(2);
    Future<?> previewTask; volatile int previewGeneration=0; String previewProcess="",readyKey="",observedHistory=""; volatile boolean dead=false; boolean libraryVisible=false; int filter=0; volatile long libraryGeneration=0;
    final Map<String,JSONObject> previewCache=new LinkedHashMap<String,JSONObject>() {protected boolean removeEldestEntry(Map.Entry<String,JSONObject> e){return size()>20;}};
    final Runnable debounce=()->loadPreview();
    final Runnable tick=new Runnable(){public void run(){refreshState();handler.postDelayed(this,800);}};

    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));return t;}
    GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(20));return d;}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    Button button(String label,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));b.setTextColor(primary?Color.WHITE:white);b.setBackground(shape(primary?solid:control));b.setStateListAnimator(null);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(10);b.setLayoutParams(p);return b;}
    void card(LinearLayout box){box.setBackground(shape(card));box.setPadding(dp(16),dp(16),dp(16),dp(16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(16);box.setLayoutParams(p);}
    Button tab(LinearLayout row,String label,Runnable action){Button b=button(label,false);b.setTextSize(13);b.setPadding(0,0,0,0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(b,p);b.setOnClickListener(v->action.run());return b;}
    void showMenu(View anchor){
        LinearLayout panel=column();panel.setPadding(dp(6),dp(6),dp(6),dp(6));panel.setBackground(shape(card));
        PopupWindow popup=new PopupWindow(panel,Math.min(dp(270),getResources().getDisplayMetrics().widthPixels-dp(32)),-2,true);
        popup.setBackgroundDrawable(shape(card));popup.setOutsideTouchable(true);popup.setElevation(dp(12));
        String[] labels={"Appearance","Update download engine","About Clip Pocket"};
        for(int i=0;i<labels.length;i++){final int action=i;TextView row=text(labels[i],15,white);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),0,dp(16),0);row.setBackground(shape(card));panel.addView(row,new LinearLayout.LayoutParams(-1,dp(50)));row.setOnClickListener(v->{popup.dismiss();if(action==0)showAppearance();else if(action==1){if(!DownloadService.busy)startForegroundService(new Intent(this,DownloadService.class).setAction("update"));}else showAbout();});if(i<labels.length-1){View line=new View(this);line.setBackgroundColor(control);LinearLayout.LayoutParams divider=new LinearLayout.LayoutParams(-1,dp(1));divider.setMargins(dp(16),0,dp(16),0);panel.addView(line,divider);}}
        popup.showAsDropDown(anchor,0,dp(4),Gravity.END);
    }
    void updateFilters(){for(int i=0;i<filterTabs.length;i++){Button b=filterTabs[i];if(b==null)continue;b.setBackground(shape(i==filter?card:Color.TRANSPARENT));b.setTextColor(i==filter?green:muted);b.setContentDescription(b.getText()+(i==filter?", selected":""));}}
    @Override protected void attachBaseContext(Context base){
        int mode=base.getSharedPreferences("appearance",0).getInt("mode",0);
        if(mode==0){super.attachBaseContext(base);return;}
        android.content.res.Configuration config=new android.content.res.Configuration(base.getResources().getConfiguration());
        config.uiMode=(config.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK) | (mode==2?android.content.res.Configuration.UI_MODE_NIGHT_YES:android.content.res.Configuration.UI_MODE_NIGHT_NO);
        super.attachBaseContext(base.createConfigurationContext(config));
    }
    void initializePalette(){
        boolean dark=(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        bg=dark?Color.rgb(8,12,20):Color.rgb(244,245,249);card=dark?Color.rgb(17,26,42):Color.WHITE;
        green=dark?Color.rgb(130,180,255):Color.rgb(0,112,235);solid=dark?Color.rgb(31,98,220):Color.rgb(0,112,235);
        white=dark?Color.rgb(246,248,252):Color.rgb(28,28,30);muted=dark?Color.rgb(182,193,210):Color.rgb(108,113,125);control=dark?Color.rgb(48,60,78):Color.rgb(235,238,244);
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);initializePalette();
        LinearLayout root=column();root.setBackgroundColor(bg);root.setPadding(dp(16),0,dp(16),0);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(16),i.getSystemWindowInsetTop(),dp(16),i.getSystemWindowInsetBottom());return i;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,dp(56)));
        closeLibrary=button("×",false);closeLibrary.setTextSize(27);closeLibrary.setPadding(0,0,0,0);closeLibrary.setGravity(Gravity.CENTER);closeLibrary.setTextColor(white);closeLibrary.setBackground(shape(control));closeLibrary.setContentDescription("Close page and return to main screen");LinearLayout.LayoutParams closeParams=new LinearLayout.LayoutParams(dp(40),dp(40));closeParams.rightMargin=dp(8);header.addView(closeLibrary,closeParams);closeLibrary.setVisibility(View.GONE);closeLibrary.setOnClickListener(v->showLibrary(false));
        TextView mark=text("↓",19,Color.WHITE);mark.setGravity(Gravity.CENTER);mark.setPadding(0,0,0,0);mark.setTypeface(null,Typeface.BOLD);mark.setBackground(shape(solid));LinearLayout.LayoutParams markParams=new LinearLayout.LayoutParams(dp(34),dp(34));markParams.rightMargin=dp(8);header.addView(mark,markParams);
        screenTitle=text("Clip Pocket",20,white);screenTitle.setTypeface(null,Typeface.BOLD);screenTitle.setSingleLine(true);screenTitle.setEllipsize(TextUtils.TruncateAt.END);header.addView(screenTitle,new LinearLayout.LayoutParams(0,-2,1));
        openDownloads=button("Downloads",false);openDownloads.setTextSize(13);openDownloads.setTextColor(green);openDownloads.setPadding(dp(6),0,dp(6),0);openDownloads.setSingleLine(true);header.addView(openDownloads,new LinearLayout.LayoutParams(dp(105),dp(40)));openDownloads.setOnClickListener(v->showLibrary(true));
        Button menu=button("•••",false);menu.setPadding(0,0,0,0);menu.setTextColor(green);menu.setTextSize(16);header.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(44)));menu.setOnClickListener(v->showMenu(menu));
        FrameLayout pages=new FrameLayout(this);root.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
        downloadPanel=column();pages.addView(downloadPanel,new FrameLayout.LayoutParams(-1,-1));libraryPanel=column();pages.addView(libraryPanel,new FrameLayout.LayoutParams(-1,-1));libraryPanel.setVisibility(View.GONE);
        mainContent=column();downloadPanel.addView(mainContent,new LinearLayout.LayoutParams(-1,0,1));
        inputBox=column();inputBox.setBackground(shape(card));inputBox.setPadding(dp(14),dp(12),dp(14),dp(10));mainContent.addView(inputBox);
        link=new EditText(this);link.setSingleLine(true);link.setTextColor(white);link.setHintTextColor(muted);link.setTextSize(14);link.setPadding(dp(12),0,dp(48),0);link.setBackground(shape(bg));link.setHint("Paste a video / reel link…");link.setInputType(17);link.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);FrameLayout linkFrame=new FrameLayout(this);inputBox.addView(linkFrame,new LinearLayout.LayoutParams(-1,dp(42)));linkFrame.addView(link,new FrameLayout.LayoutParams(-1,-1));Button clearLink=new Button(this);clearLink.setText("×");clearLink.setTextSize(22);clearLink.setTextColor(muted);clearLink.setAllCaps(false);clearLink.setPadding(0,0,0,0);clearLink.setBackgroundColor(Color.TRANSPARENT);clearLink.setStateListAnimator(null);clearLink.setContentDescription("Clear link");FrameLayout.LayoutParams clearParams=new FrameLayout.LayoutParams(dp(44),-1,Gravity.END|Gravity.CENTER_VERTICAL);linkFrame.addView(clearLink,clearParams);clearLink.setVisibility(View.GONE);clearLink.setOnClickListener(v->link.setText(""));link.setOnEditorActionListener((v,a,event)->{if(a==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(link.getWindowToken(),0);link.clearFocus();schedulePreview(0);return true;}return false;});
        LinearLayout actions=new LinearLayout(this);inputBox.addView(actions,new LinearLayout.LayoutParams(-1,dp(38)));
        paste=button("Paste link",false);paste.setTextColor(green);actions.addView(paste,new LinearLayout.LayoutParams(-1,-1));
        paste.setOnClickListener(v->{ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(c.hasPrimaryClip()&&c.getPrimaryClip().getItemCount()>0){link.setText(DownloadRules.extractUrl(c.getPrimaryClip().getItemAt(0).coerceToText(this).toString()));((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(link.getWindowToken(),0);link.clearFocus();previewCache.remove(currentKey());schedulePreview(0);}else Toast.makeText(this,"Copy a video link first",0).show();});
        quality=new Spinner(this);quality.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Video · up to 144p","Video · up to 240p","Video · up to 360p","Video · up to 480p","Video · up to 720p","Video · up to 1080p","Video · up to 1440p","Video · up to 2160p (4K)","Audio · M4A"}));quality.setSelection(4);quality.setPopupBackgroundDrawable(shape(card));quality.setPadding(dp(10),0,dp(10),0);quality.setBackground(shape(bg));inputBox.addView(quality,new LinearLayout.LayoutParams(-1,dp(40)));
        previewStatus=text("Paste a link to preview",12,muted);previewStatus.setMaxLines(2);previewStatus.setEllipsize(TextUtils.TruncateAt.END);inputBox.addView(previewStatus,new LinearLayout.LayoutParams(-1,dp(38)));previewStatus.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Preview status").setMessage(previewStatus.getText()).setPositiveButton("OK",null).show());
        previewBox=column();previewBox.setPadding(dp(14),dp(14),dp(14),dp(10));previewBox.setBackground(shape(card));mainContent.addView(previewBox,new LinearLayout.LayoutParams(-1,0,1));previewBox.setVisibility(View.INVISIBLE);
        thumbnail=new ImageView(this);thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);thumbnail.setContentDescription("Video thumbnail");FrameLayout thumbnailFrame=new FrameLayout(this);thumbnailFrame.setBackground(shape(bg));thumbnailFrame.setClipToOutline(true);previewBox.addView(thumbnailFrame,new LinearLayout.LayoutParams(-1,0,1));thumbnailFrame.addView(thumbnail,new FrameLayout.LayoutParams(-1,-1));thumbnail.setVisibility(View.INVISIBLE);thumbnailProgress=new ProgressBar(this);thumbnailProgress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(green));FrameLayout.LayoutParams loadingParams=new FrameLayout.LayoutParams(dp(36),dp(36),Gravity.CENTER);thumbnailFrame.addView(thumbnailProgress,loadingParams);thumbnailProgress.setVisibility(View.GONE);thumbnailProgress.setContentDescription("Loading thumbnail");
        videoTitle=text("",16,white);videoTitle.setTypeface(null,Typeface.BOLD);videoTitle.setMaxLines(1);videoTitle.setEllipsize(TextUtils.TruncateAt.END);previewBox.addView(videoTitle,new LinearLayout.LayoutParams(-1,dp(28)));
        description=text("",12,muted);description.setMaxLines(1);description.setEllipsize(TextUtils.TruncateAt.END);previewBox.addView(description,new LinearLayout.LayoutParams(-1,dp(26)));description.setContentDescription("Description. Tap for full details");description.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(videoTitle.getText()).setMessage(description.getText()).setPositiveButton("Close",null).show());
        size=text("",15,green);previewBox.addView(size,new LinearLayout.LayoutParams(-1,dp(30)));size.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Estimated download size").setMessage("Final saved size and data usage may differ. Selected quality depends on the source formats.").setPositiveButton("OK",null).show());
        LinearLayout bottom=new LinearLayout(this);LinearLayout.LayoutParams bottomParams=new LinearLayout.LayoutParams(-1,dp(48));bottomParams.topMargin=dp(10);downloadPanel.addView(bottom,bottomParams);download=button("↓ Download",true);cancel=button("Cancel",false);bottom.addView(download,new LinearLayout.LayoutParams(0,-1,1));bottom.addView(cancel,new LinearLayout.LayoutParams(dp(92),-1));cancel.setVisibility(View.GONE);download.setEnabled(false);download.setAlpha(0.45f);download.setOnClickListener(v->startDownload());cancel.setOnClickListener(v->startService(new Intent(this,DownloadService.class).setAction("cancel")));
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(green));downloadPanel.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
        state=text("Ready · Downloads / ClipPocket",11,muted);state.setMaxLines(1);state.setEllipsize(TextUtils.TruncateAt.END);downloadPanel.addView(state,new LinearLayout.LayoutParams(-1,dp(26)));state.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Download status").setMessage(state.getText()).setPositiveButton("OK",null).show());
        LinearLayout filters=new LinearLayout(this);LinearLayout.LayoutParams filterParams=new LinearLayout.LayoutParams(-1,dp(44));filterParams.topMargin=dp(8);filterParams.bottomMargin=dp(8);libraryPanel.addView(filters,filterParams);filters.setBackground(shape(control));filterTabs[0]=tab(filters,"All",()->{filter=0;updateFilters();loadLibrary();});filterTabs[1]=tab(filters,"Videos",()->{filter=1;updateFilters();loadLibrary();});filterTabs[2]=tab(filters,"Audio",()->{filter=2;updateFilters();loadLibrary();});updateFilters();
        libraryStatus=text("",12,muted);libraryStatus.setMaxLines(1);libraryPanel.addView(libraryStatus);ScrollView filesScroll=new ScrollView(this);libraryPanel.addView(filesScroll,new LinearLayout.LayoutParams(-1,0,1));history=column();filesScroll.addView(history);
        aboutPanel=column();pages.addView(aboutPanel,new FrameLayout.LayoutParams(-1,-1));aboutPanel.setVisibility(View.GONE);ScrollView aboutScroll=new ScrollView(this);aboutPanel.addView(aboutScroll,new LinearLayout.LayoutParams(-1,-1));LinearLayout aboutContent=column();aboutContent.setPadding(dp(2),dp(12),dp(2),dp(24));aboutScroll.addView(aboutContent);
        LinearLayout aboutCard=column();aboutCard.setGravity(Gravity.CENTER_HORIZONTAL);aboutCard.setBackground(shape(card));aboutCard.setPadding(dp(24),dp(26),dp(24),dp(26));aboutContent.addView(aboutCard,new LinearLayout.LayoutParams(-1,-2));
        TextView aboutTitle=text("About Me",26,white);aboutTitle.setTypeface(null,Typeface.BOLD);aboutTitle.setGravity(Gravity.CENTER);LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(-1,-2);titleParams.topMargin=dp(14);aboutCard.addView(aboutTitle,titleParams);
        TextView developer=text("Nesandu",19,green);developer.setGravity(Gravity.CENTER);developer.setTypeface(null,Typeface.BOLD);aboutCard.addView(developer);
        TextView bio=text("Hi! I enjoy creating useful applications and exploring new technologies.",16,muted);bio.setGravity(Gravity.CENTER);bio.setLineSpacing(dp(4),1);LinearLayout.LayoutParams bioParams=new LinearLayout.LayoutParams(-1,-2);bioParams.topMargin=dp(12);aboutCard.addView(bio,bioParams);
        LinearLayout socials=new LinearLayout(this);socials.setGravity(Gravity.CENTER);LinearLayout.LayoutParams socialParams=new LinearLayout.LayoutParams(-1,dp(58));socialParams.topMargin=dp(10);aboutCard.addView(socials,socialParams);
        ImageButton github=new ImageButton(this);github.setImageResource(R.drawable.ic_github);github.setImageTintList(android.content.res.ColorStateList.valueOf(white));github.setPadding(dp(12),dp(12),dp(12),dp(12));github.setBackgroundColor(Color.TRANSPARENT);github.setContentDescription("Open Clip Pocket on GitHub");socials.addView(github,new LinearLayout.LayoutParams(dp(56),dp(52)));github.setOnClickListener(v->openContact("https://github.com/Nesandux/ClipPocket"));
        ImageButton facebook=new ImageButton(this);facebook.setImageResource(R.drawable.ic_facebook);facebook.setImageTintList(android.content.res.ColorStateList.valueOf(green));facebook.setPadding(dp(12),dp(12),dp(12),dp(12));facebook.setBackgroundColor(Color.TRANSPARENT);facebook.setContentDescription("Open Nesandu on Facebook");socials.addView(facebook,new LinearLayout.LayoutParams(dp(56),dp(52)));facebook.setOnClickListener(v->openContact("https://www.facebook.com/nesandu.nimneth"));
        TextView appInfo=text("Clip Pocket · Version "+appVersion(),13,muted);appInfo.setGravity(Gravity.CENTER);LinearLayout.LayoutParams infoParams=new LinearLayout.LayoutParams(-1,-2);infoParams.topMargin=dp(20);aboutCard.addView(appInfo,infoParams);
        TextView details=text("Save videos or audio from supported websites. Files are saved in Downloads / ClipPocket.",13,muted);details.setGravity(Gravity.CENTER);aboutCard.addView(details);
        TextView notices=text("Powered by yt-dlp and FFmpeg. Open-source notices are included with the source-code package.",11,muted);notices.setGravity(Gravity.CENTER);aboutCard.addView(notices);
        root.addOnLayoutChangeListener((v,l,t,r,bt,ol,ot,or,ob)->adaptMain(mainContent.getWidth(),mainContent.getHeight()));
        link.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){clearLink.setVisibility(s.length()>0?View.VISIBLE:View.GONE);schedulePreview(700);}public void afterTextChanged(Editable e){}});
        quality.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){schedulePreview(300);}public void onNothingSelected(AdapterView<?> p){}});
        if(b==null){readShare(getIntent());}else{quality.setSelection(b.getInt("quality",4));link.setText(b.getString("url",""));int page=b.getInt("page",0);if(page==1)showLibrary(true);else if(page==2)showAbout();}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=0)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
    }
    void adaptMain(int width,int height){
        if(width<=0||height<=0)return;
        boolean side=width>=dp(320)&&(width>height&&width>=dp(480)||height<dp(260));
        int mode=(side?1:0)+(height<dp(180)?2:0);if(layoutMode==mode)return;layoutMode=mode;
        mainContent.setOrientation(side?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        LinearLayout.LayoutParams input=new LinearLayout.LayoutParams(side?0:-1,side?-1:-2,side?1:0);
        LinearLayout.LayoutParams preview=new LinearLayout.LayoutParams(side?0:-1,side?-1:0,1);if(side)preview.leftMargin=dp(12);else preview.topMargin=dp(height<dp(400)?6:12);inputBox.setLayoutParams(input);previewBox.setLayoutParams(preview);
        int[] heightsDp=height<dp(180)?new int[]{34,32,32,24}:new int[]{42,38,40,38};
        for(int i=0;i<4;i++){View child=inputBox.getChildAt(i);LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)child.getLayoutParams();p.height=dp(heightsDp[i]);child.setLayoutParams(p);}
    }
    boolean audio(){return quality.getSelectedItemPosition()==8;}
    String selector(){return audio()?"bestaudio/best":DownloadRules.videoFormat(heights[quality.getSelectedItemPosition()]);}
    String currentUrl(){return DownloadRules.extractUrl(link.getText().toString());}
    String currentKey(){return currentUrl()+"\n"+selector();}
    void schedulePreview(long delay){
        handler.removeCallbacks(debounce);previewGeneration++;readyKey="";download.setEnabled(false);previewBox.setVisibility(View.INVISIBLE);
        if(previewTask!=null)previewTask.cancel(true);if(!previewProcess.isEmpty())YoutubeDL.getInstance().destroyProcessById(previewProcess);
        if(!DownloadRules.validUrl(currentUrl())){thumbnailProgress.setVisibility(View.GONE);previewStatus.setText(currentUrl().isEmpty()?"Paste a link to get started":"Paste a valid HTTP or HTTPS video link.");previewBox.setVisibility(View.VISIBLE);thumbnail.setScaleType(ImageView.ScaleType.CENTER);thumbnail.setImageResource(android.R.drawable.ic_menu_gallery);thumbnail.setColorFilter(green);thumbnail.setVisibility(View.VISIBLE);videoTitle.setText("Ready when you are");description.setText("Paste a link to see its preview");size.setText("");return;}
        previewBox.setVisibility(View.VISIBLE);thumbnail.setVisibility(View.INVISIBLE);thumbnailProgress.setVisibility(View.VISIBLE);videoTitle.setText("");description.setText("");size.setText("");
        previewStatus.setText("Loading thumbnail, description and selected quality…");handler.postDelayed(debounce,delay);
    }
    void loadPreview(){
        final String url=currentUrl(),key=currentKey(),selection=selector();final boolean isAudio=audio();final int generation=previewGeneration;
        if(!DownloadRules.validUrl(url)||dead)return;
        JSONObject cached=previewCache.get(key);if(cached!=null){applyPreview(cached,key,generation,isAudio);return;}
        final String process="preview-"+UUID.randomUUID();previewProcess=process;
        previewTask=io.submit(()->{try{
            YoutubeDL.getInstance().init(getApplicationContext());FFmpeg.getInstance().init(getApplicationContext());if(Thread.currentThread().isInterrupted())return;
            YoutubeDLRequest r=new YoutubeDLRequest(url);r.addOption("--no-playlist");r.addOption("--dump-single-json");r.addOption("--skip-download");r.addOption("--socket-timeout",20);r.addOption("--retries",1);r.addOption("-f",selection);
            YoutubeDLResponse result=YoutubeDL.getInstance().execute(r,process,null);
            JSONObject data=new JSONObject(result.getOut());
            handler.post(()->{if(dead||generation!=previewGeneration||!key.equals(currentKey()))return;previewCache.put(key,data);applyPreview(data,key,generation,isAudio);});
        }catch(Exception e){handler.post(()->{if(dead||generation!=previewGeneration)return;readyKey="";download.setEnabled(false);thumbnailProgress.setVisibility(View.GONE);previewBox.setVisibility(View.INVISIBLE);previewStatus.setText("Preview unavailable for this quality or link. Try another quality, tap Paste link again, or update the download engine.\n"+shortError(e));});}});
    }
    String shortError(Exception e){String m=e.getMessage();if(m==null)return e.getClass().getSimpleName();return m.length()>300?m.substring(m.length()-300):m;}
    void applyPreview(JSONObject info,String key,int generation,boolean isAudio){
        if(dead||generation!=previewGeneration||!key.equals(currentKey()))return;
        readyKey=key;previewStatus.setText("Preview ready · "+PreviewData.selectedQuality(info,isAudio));videoTitle.setText(info.optString("title","Untitled video"));
        String desc=info.isNull("description")?"":info.optString("description","");description.setText(desc.isEmpty()?"No description provided by this source.":desc);
        double bytes=PreviewData.estimatedBytes(info);size.setText((bytes>=0?"≈ ":"")+SizeEstimate.display(bytes));previewBox.setVisibility(View.VISIBLE);thumbnail.setVisibility(View.INVISIBLE);thumbnail.setImageDrawable(null);thumbnailProgress.setVisibility(View.VISIBLE);download.setEnabled(!DownloadService.busy);
        String image=info.optString("thumbnail","");if(!DownloadRules.validUrl(image)){finishThumbnail(null);return;}
        io.submit(()->{HttpURLConnection connection=null;Bitmap loaded=null;try{
            connection=(HttpURLConnection)new URL(image).openConnection();connection.setConnectTimeout(12000);connection.setReadTimeout(12000);
            if(connection.getContentLengthLong()>8_000_000)throw new IOException("Thumbnail is too large");
            ByteArrayOutputStream bytesOut=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(generation!=previewGeneration||dead)return;if(bytesOut.size()+n>8_000_000)throw new IOException("Thumbnail is too large");bytesOut.write(buffer,0,n);}}
            byte[] encoded=bytesOut.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);int sample=1;while(options.outWidth/sample>1200||options.outHeight/sample>1200)sample*=2;options.inJustDecodeBounds=false;options.inSampleSize=sample;loaded=BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);
        }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();final Bitmap result=loaded;handler.post(()->{if(!dead&&generation==previewGeneration&&key.equals(currentKey()))finishThumbnail(result);});}});
    }
    void finishThumbnail(Bitmap bitmap){thumbnailProgress.setVisibility(View.GONE);if(bitmap!=null){thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);thumbnail.setImageBitmap(bitmap);thumbnail.clearColorFilter();thumbnail.setContentDescription("Video thumbnail");}else{thumbnail.setScaleType(ImageView.ScaleType.CENTER);thumbnail.setImageResource(android.R.drawable.ic_menu_gallery);thumbnail.setColorFilter(muted);thumbnail.setContentDescription("Thumbnail unavailable");}thumbnail.setVisibility(View.VISIBLE);}
    String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){return "2.2";}}
    void openContact(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){Toast.makeText(this,"No app available to open this link",Toast.LENGTH_SHORT).show();}}
    void showAppearance(){int current=getSharedPreferences("appearance",0).getInt("mode",0);new AlertDialog.Builder(this).setTitle("Appearance").setSingleChoiceItems(new String[]{"Match device", "Light", "Dark"},current,(dialog,choice)->{dialog.dismiss();if(choice!=current){getSharedPreferences("appearance",0).edit().putInt("mode",choice).apply();recreate();}}).setNegativeButton("Cancel",null).show();}
    void showAbout(){libraryVisible=false;downloadPanel.setVisibility(View.GONE);libraryPanel.setVisibility(View.GONE);aboutPanel.setVisibility(View.VISIBLE);closeLibrary.setVisibility(View.VISIBLE);openDownloads.setVisibility(View.GONE);screenTitle.setText("About");}
    void startDownload(){if(DownloadService.busy||!readyKey.equals(currentKey()))return;JSONObject info=previewCache.get(currentKey());startForegroundService(new Intent(this,DownloadService.class).setAction("download").putExtra("url",currentUrl()).putExtra("selector",selector()).putExtra("audio",audio()).putExtra("thumbnail",info==null?"":info.optString("thumbnail","")));}
    void showLibrary(boolean show){aboutPanel.setVisibility(View.GONE);libraryVisible=show;closeLibrary.setVisibility(show?View.VISIBLE:View.GONE);openDownloads.setVisibility(show?View.GONE:View.VISIBLE);screenTitle.setText(show?"Downloads":"Clip Pocket");libraryPanel.setVisibility(show?View.VISIBLE:View.GONE);downloadPanel.setVisibility(show?View.GONE:View.VISIBLE);if(show)loadLibrary();}
    void refreshState(){android.content.SharedPreferences p=getSharedPreferences("downloads",0);boolean busy=DownloadService.busy;download.setEnabled(!busy&&!readyKey.isEmpty()&&readyKey.equals(currentKey()));download.setAlpha(download.isEnabled()?1f:0.45f);quality.setEnabled(!busy);paste.setEnabled(!busy);link.setEnabled(!busy);cancel.setVisibility(busy?View.VISIBLE:View.GONE);state.setText(p.getString("status","Ready when you are"));int pc=p.getInt("progress",0);progress.setIndeterminate(busy&&pc<0);progress.setProgress(Math.max(0,pc));String changed=p.getString("history","[]");if(!changed.equals(observedHistory)){observedHistory=changed;if(libraryVisible)loadLibrary();}}
    void loadLibrary(){
        if(deleting)return;
        final long generation=++libraryGeneration;final int chosenFilter=filter;libraryStatus.setText("Loading saved files…");
        io.submit(()->{ArrayList<JSONObject> files=new ArrayList<>();String problem="";
            String[] projection={MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.MIME_TYPE,MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.DATE_ADDED};
            String selection=MediaStore.MediaColumns.RELATIVE_PATH+" = ? AND "+MediaStore.MediaColumns.IS_PENDING+" = 0 AND "+MediaStore.MediaColumns.OWNER_PACKAGE_NAME+" = ?";
            try(Cursor cursor=getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,projection,selection,new String[]{Environment.DIRECTORY_DOWNLOADS+"/ClipPocket/",getPackageName()},MediaStore.MediaColumns.DATE_ADDED+" DESC")){
                if(cursor!=null)while(cursor.moveToNext()){
                    String mime=cursor.getString(2);boolean isAudio=mime!=null&&mime.startsWith("audio/");if(chosenFilter==1&&isAudio||chosenFilter==2&&!isAudio)continue;
                    JSONObject item=new JSONObject();item.put("name",cursor.getString(1));item.put("mime",mime==null?"application/octet-stream":mime);item.put("size",cursor.getLong(3));item.put("date",cursor.getLong(4));item.put("uri",ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cursor.getLong(0)).toString());files.add(item);
                }
            }catch(Exception e){problem="Could not read saved files. Tap Refresh files to try again.";}
            final String error=problem;handler.post(()->{if(dead||generation!=libraryGeneration)return;history.removeAllViews();libraryStatus.setText(!error.isEmpty()?error:files.isEmpty()?"No "+(chosenFilter==1?"videos":chosenFilter==2?"audio files":"downloads")+" yet. Completed files will appear here.":files.size()+" saved "+(files.size()==1?"file":"files"));for(JSONObject item:files)addFile(item);});
        });
    }
    void addFile(JSONObject item){
        String mime=item.optString("mime"),uri=item.optString("uri");boolean isAudio=mime.startsWith("audio/");
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setBackground(shape(card));row.setPadding(dp(12),dp(8),dp(12),dp(8));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(88));rp.bottomMargin=dp(10);history.addView(row,rp);
        ImageView art=new ImageView(this);art.setBackground(shape(bg));art.setScaleType(ImageView.ScaleType.CENTER_CROP);art.setImageResource(isAudio?android.R.drawable.ic_lock_silent_mode_off:android.R.drawable.ic_media_play);art.setColorFilter(green);art.setContentDescription("Play "+item.optString("name"));FrameLayout artFrame=new FrameLayout(this);row.addView(artFrame,new LinearLayout.LayoutParams(dp(94),dp(64)));artFrame.setBackground(shape(bg));artFrame.setClipToOutline(true);artFrame.addView(art,new FrameLayout.LayoutParams(-1,-1));ProgressBar artLoading=new ProgressBar(this);artLoading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(green));artLoading.setContentDescription("Loading saved-file thumbnail");artFrame.addView(artLoading,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));art.setOnClickListener(v->play(item));
        LinearLayout labels=column();labels.setPadding(dp(10),0,dp(4),0);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView name=text(item.optString("name"),14,white);name.setTypeface(null,Typeface.BOLD);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);labels.addView(name);labels.addView(text((isAudio?"Audio":"Video")+" · "+SizeEstimate.display(item.optLong("size")),11,muted));
        ImageButton trash=new ImageButton(this);trash.setImageResource(R.drawable.ic_trash);trash.setImageTintList(android.content.res.ColorStateList.valueOf(Color.rgb(255,105,112)));trash.setBackgroundColor(Color.TRANSPARENT);trash.setPadding(dp(12),dp(12),dp(12),dp(12));trash.setContentDescription("Delete "+item.optString("name"));row.addView(trash,new LinearLayout.LayoutParams(dp(48),dp(48)));trash.setOnClickListener(v->confirmDelete(item));
        final long generation=libraryGeneration;thumbs.submit(()->{if(dead||generation!=libraryGeneration)return;Bitmap bitmap=null;MediaMetadataRetriever retriever=new MediaMetadataRetriever();try{
            File cached=ArtworkStore.file(this,Uri.parse(uri));if(cached.isFile())bitmap=decodeArt(java.nio.file.Files.readAllBytes(cached.toPath()));
            if(bitmap==null){retriever.setDataSource(this,Uri.parse(uri));if(isAudio){byte[] cover=retriever.getEmbeddedPicture();if(cover!=null)bitmap=decodeArt(cover);}else bitmap=retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,188,128);}
        }catch(Exception ignored){}finally{try{retriever.release();}catch(Exception ignored){}}
        final Bitmap result=bitmap;handler.post(()->{if(!dead&&generation==libraryGeneration){artLoading.setVisibility(View.GONE);if(result!=null){art.clearColorFilter();art.setImageBitmap(result);}}});});
    }
    Bitmap decodeArt(byte[] bytes){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);o.inSampleSize=1;while(o.outWidth/o.inSampleSize>400||o.outHeight/o.inSampleSize>400)o.inSampleSize*=2;o.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);}
    void play(JSONObject item){try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(item.optString("uri")),item.optString("mime")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception e){Toast.makeText(this,"File moved, deleted, or no player installed",1).show();}}
    void confirmDelete(JSONObject item){if(deleting)return;new AlertDialog.Builder(this).setTitle("Delete this file?").setMessage(item.optString("name")+"\n\nThis permanently deletes the file from Downloads / ClipPocket.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->deleteFile(item)).show();}
    void deleteFile(JSONObject item){if(deleting)return;deleting=true;io.submit(()->{boolean removed=false;String id=item.optString("uri");try{Uri uri=Uri.parse(id);int count=getContentResolver().delete(uri,MediaStore.MediaColumns.OWNER_PACKAGE_NAME+" = ? AND "+MediaStore.MediaColumns.RELATIVE_PATH+" = ?",new String[]{getPackageName(),Environment.DIRECTORY_DOWNLOADS+"/ClipPocket/"});if(count>0){removed=true;ArtworkStore.file(this,uri).delete();}}catch(Exception ignored){}
        if(removed)try{android.content.SharedPreferences p=getSharedPreferences("downloads",0);JSONArray old=new JSONArray(p.getString("history","[]")),kept=new JSONArray();for(int i=0;i<old.length();i++){JSONObject entry=old.optJSONObject(i);if(entry!=null&&!id.equals(entry.optString("uri")))kept.put(entry);}p.edit().putString("history",kept.toString()).apply();}catch(Exception ignored){}
        final boolean done=removed;handler.post(()->{if(dead)return;deleting=false;Toast.makeText(this,done?"File deleted":"Could not delete this file. Refresh and try again.",1).show();loadLibrary();});});}
    void readShare(Intent intent){if(Intent.ACTION_SEND.equals(intent.getAction())){String shared=intent.getStringExtra(Intent.EXTRA_TEXT);if(shared!=null){showLibrary(false);link.setText(DownloadRules.extractUrl(shared));}}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);readShare(intent);}
    @Override protected void onSaveInstanceState(Bundle b){b.putString("url",link.getText().toString());b.putInt("quality",quality.getSelectedItemPosition());b.putInt("page",libraryVisible?1:aboutPanel.getVisibility()==View.VISIBLE?2:0);super.onSaveInstanceState(b);}
    @Override protected void onResume(){super.onResume();handler.post(tick);if(libraryVisible)loadLibrary();}
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){dead=true;previewGeneration++;handler.removeCallbacksAndMessages(null);if(previewTask!=null)previewTask.cancel(true);if(!previewProcess.isEmpty())YoutubeDL.getInstance().destroyProcessById(previewProcess);io.shutdownNow();thumbs.shutdownNow();super.onDestroy();}
}
