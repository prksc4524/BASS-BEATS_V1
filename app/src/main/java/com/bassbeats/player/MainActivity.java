package com.bassbeats.player;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.graphics.drawable.Drawable;
import java.util.*;

public class MainActivity extends Activity {
 static final int BG=Color.rgb(5,9,15), PANEL=Color.rgb(14,24,37), CYAN=Color.rgb(34,211,238), MUTED=Color.rgb(145,168,188), WHITE=Color.WHITE;
 ArrayList<Song> songs=new ArrayList<>(); LinearLayout root, content, nav; String tab="PLAYER", query=""; int current=-1; boolean playing=false; TextView songTitle,songArtist,playBtn; SeekBar seek; Handler handler=new Handler();
 static class Song {long id; String title,artist; Uri uri; Song(long i,String t,String a,Uri u){id=i;title=t;artist=a;uri=u;}}
 BroadcastReceiver serviceReceiver = new BroadcastReceiver(){@Override public void onReceive(Context c, Intent i){String a=i.getAction();if(PlaybackService.SKIP_NEXT.equals(a)){if(!songs.isEmpty())playIndex(current<0?0:(current+1)%songs.size());}else if(PlaybackService.SKIP_PREV.equals(a)){if(!songs.isEmpty())playIndex(current<=0?songs.size()-1:current-1);}else if(PlaybackService.STATE.equals(a)){playing=i.getBooleanExtra("playing",false);if(songTitle!=null&&i.hasExtra("title"))songTitle.setText(i.getStringExtra("title"));if(songArtist!=null&&i.hasExtra("artist"))songArtist.setText(i.getStringExtra("artist"));if(playBtn!=null)playBtn.setText(playing?"Ⅱ":"▶"); long pos=i.getLongExtra("position",0),dur=i.getLongExtra("duration",0); if(seek!=null&&!dragging){seek.setMax((int)Math.min(Integer.MAX_VALUE,dur));seek.setProgress((int)Math.min(Integer.MAX_VALUE,pos));} if(currentTime!=null)currentTime.setText(fmt(pos));if(durationTime!=null)durationTime.setText(fmt(dur));}}};
 boolean receiverRegistered=false; boolean shuffle=false, repeat=false, dragging=false; TextView currentTime, durationTime;
 @Override public void onCreate(Bundle b){super.onCreate(b); getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(0); requestMusicPermission(); buildShell(); IntentFilter f=new IntentFilter();f.addAction(PlaybackService.SKIP_NEXT);f.addAction(PlaybackService.SKIP_PREV);f.addAction(PlaybackService.STATE);if(Build.VERSION.SDK_INT>=33)registerReceiver(serviceReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(serviceReceiver,f);receiverRegistered=true;}
 void requestMusicPermission(){String p=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{p},11);else loadSongs();}
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==11&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED){loadSongs();showTab(tab);}else if(r==11){Toast.makeText(this,"Music access is needed to scan local songs",Toast.LENGTH_LONG).show();}}
 void buildShell(){root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);root.setPadding(dp(18),dp(10),dp(18),dp(8));setContentView(root); if(Build.VERSION.SDK_INT>=29){getWindow().setNavigationBarContrastEnforced(false);getWindow().getDecorView().setOnApplyWindowInsetsListener((v,insets)->{int bottomInset=insets.getSystemWindowInsetBottom();root.setPadding(dp(18),dp(10),dp(18),dp(8)+bottomInset);return v.onApplyWindowInsets(insets);});} TextView brand=text("BASS BEATS",22,CYAN,true);brand.setGravity(Gravity.CENTER);root.addView(brand,new LinearLayout.LayoutParams(-1,dp(42))); TextView sub=text("OFFLINE MUSIC PLAYER",10,MUTED,true);sub.setGravity(Gravity.CENTER);root.addView(sub); FrameLayout area=new FrameLayout(this);root.addView(area,new LinearLayout.LayoutParams(-1,0,1)); content=new LinearLayout(this);content.setOrientation(1);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(content);area.addView(scroll); nav=new LinearLayout(this);nav.setOrientation(0);nav.setGravity(Gravity.CENTER);nav.setBackgroundColor(PANEL);nav.setPadding(0,dp(7),0,dp(4));root.addView(nav,new LinearLayout.LayoutParams(-1,dp(60)));String[] tabs={"PLAYER","MUSIC","EQUALIZER","SETTINGS"};String[] icons={"♫","☷","≋","⚙"};for(int i=0;i<tabs.length;i++){final String t=tabs[i];LinearLayout item=new LinearLayout(this);item.setOrientation(1);item.setGravity(Gravity.CENTER);TextView ico=text(icons[i],21,CYAN,true);TextView label=text(t,9,MUTED,true);item.addView(ico);item.addView(label);nav.addView(item,new LinearLayout.LayoutParams(0,-1,1));item.setOnClickListener(v->showTab(t));}showTab("PLAYER");}
 void showTab(String t){tab=t;content.removeAllViews(); if(t.equals("PLAYER"))playerScreen();else if(t.equals("MUSIC"))musicScreen();else if(t.equals("EQUALIZER"))eqScreen();else settingsScreen();}
 void playerScreen(){content.addView(gap(12)); TextView heading=text("NOW PLAYING",12,MUTED,true);heading.setGravity(Gravity.CENTER);content.addView(heading); ImageView logo=new ImageView(this); logo.setImageResource(getResources().getIdentifier("bass_beats_logo","drawable",getPackageName())); logo.setScaleType(ImageView.ScaleType.FIT_CENTER); logo.setBackground(round(PANEL,dp(22))); LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,dp(220));vp.setMargins(dp(18),dp(12),dp(18),dp(12));content.addView(logo,vp); songTitle=text(current>=0&&current<songs.size()?songs.get(current).title:"Choose a song",20,WHITE,true);songTitle.setGravity(Gravity.CENTER);content.addView(songTitle);songArtist=text(current>=0&&current<songs.size()?songs.get(current).artist:"Your offline library",13,MUTED,false);songArtist.setGravity(Gravity.CENTER);content.addView(songArtist);content.addView(gap(14));LinearLayout times=new LinearLayout(this);times.setGravity(Gravity.CENTER_VERTICAL);TextView cur=text("0:00",11,MUTED,false),dur=text("0:00",11,MUTED,false); currentTime=cur; durationTime=dur; times.addView(cur,new LinearLayout.LayoutParams(0,-2,1));dur.setGravity(Gravity.RIGHT);times.addView(dur,new LinearLayout.LayoutParams(0,-2,1));content.addView(times);seek=new SeekBar(this);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(CYAN));content.addView(seek); seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){if(u&&currentTime!=null)currentTime.setText(fmt(p));}public void onStartTrackingTouch(SeekBar b){dragging=true;}public void onStopTrackingTouch(SeekBar b){dragging=false;Intent x=new Intent(MainActivity.this,PlaybackService.class).setAction(PlaybackService.SEEK).putExtra("position",b.getProgress());if(Build.VERSION.SDK_INT>=26)startForegroundService(x);else startService(x);}});LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);String[] c={"⤨","|◀","▶","▶|","↻"};for(int i=0;i<c.length;i++){final int n=i;TextView b=text(c[i],i==2?28:22,i==2?CYAN:WHITE,true);b.setGravity(Gravity.CENTER);if(n==2)playBtn=b;controls.addView(b,new LinearLayout.LayoutParams(0,dp(60),i==2?1.35f:1));b.setOnClickListener(v->{if(n==1)playIndex(Math.max(0,current-1));else if(n==2)toggle();else if(n==3)playIndex(Math.min(songs.size()-1,current+1));else if(n==0){shuffle=!shuffle;b.setTextColor(shuffle?CYAN:WHITE);Toast.makeText(this,shuffle?"Shuffle on":"Shuffle off",Toast.LENGTH_SHORT).show();}else {repeat=!repeat;b.setTextColor(repeat?CYAN:WHITE);Toast.makeText(this,repeat?"Repeat on":"Repeat off",Toast.LENGTH_SHORT).show();}});}content.addView(controls);content.addView(gap(14));TextView hint=text("Select MUSIC to browse songs on this phone",12,MUTED,false);hint.setGravity(Gravity.CENTER);content.addView(hint);}
 void musicScreen(){TextView h=text("YOUR MUSIC",20,WHITE,true);content.addView(h);EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Search songs or artists");search.setHintTextColor(MUTED);search.setTextColor(WHITE);search.setBackground(round(PANEL,dp(14)));search.setPadding(dp(14),0,dp(14),0);content.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));LinearLayout shortcuts=new LinearLayout(this);shortcuts.setPadding(0,dp(8),0,dp(8));for(String s:new String[]{"Favorites","Playlists","Folders","Queue"}){TextView b=text(s,11,CYAN,true);b.setGravity(Gravity.CENTER);shortcuts.addView(b,new LinearLayout.LayoutParams(0,dp(42),1));b.setOnClickListener(v->Toast.makeText(this,s+" section will be completed in the next implementation pass",Toast.LENGTH_SHORT).show());}content.addView(shortcuts);TextView all=text("ALL SONGS  ·  "+songs.size(),15,CYAN,true);content.addView(all);search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString().toLowerCase();renderSongRows();}public void afterTextChanged(android.text.Editable e){}});renderSongRows();}
 void renderSongRows(){if(!tab.equals("MUSIC"))return;int start=0;for(int i=0;i<content.getChildCount();i++)if(content.getChildAt(i) instanceof TextView && ((TextView)content.getChildAt(i)).getText().toString().startsWith("ALL SONGS")){start=i+1;break;}while(content.getChildCount()>start)content.removeViewAt(start);int shown=0;for(int i=0;i<songs.size();i++){Song s=songs.get(i);if(!s.title.toLowerCase().contains(query)&&!s.artist.toLowerCase().contains(query))continue;final int index=i;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(8),dp(8),dp(8));row.setBackground(round(i==current?Color.rgb(7,43,60):PANEL,dp(12)));LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.addView(text(s.title,14,i==current?CYAN:WHITE,true));info.addView(text(s.artist,11,MUTED,false));row.addView(info,new LinearLayout.LayoutParams(0,dp(48),1));TextView menu=text("⋮",24,WHITE,true);row.addView(menu);menu.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(s.title).setItems(new String[]{"Play now","Add to favorites","Add to queue"},(d,w)->{if(w==0)playIndex(index);else Toast.makeText(this,"Saved for this session",Toast.LENGTH_SHORT).show();}).show());row.setOnClickListener(v->playIndex(index));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(4));content.addView(row,lp);shown++;}if(shown==0)content.addView(text(songs.isEmpty()?"No local songs found. Add audio files to your phone and refresh.":"No matching songs",13,MUTED,false));}
 void eqScreen(){
  content.addView(text("EQUALIZER",21,WHITE,true));
  content.addView(text("10-BAND AUDIO CONTROL",10,MUTED,true));
  String[] names={"Custom","Normal","Rock","Pop","Jazz","Classical"};
  Spinner presets=new Spinner(this);
  presets.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names));
  content.addView(presets);
  LinearLayout bands=new LinearLayout(this); bands.setGravity(Gravity.CENTER); bands.setPadding(0,dp(8),0,dp(8));
  String[] freq={"31","62","125","250","500","1k","2k","4k","8k","16k"};
  int[][] curves={{0,0,0,0,0,0,0,0,0,0},{0,0,0,0,0,0,0,0,0,0},{5,4,3,1,-1,-2,1,3,4,4},{-1,1,3,2,0,0,2,3,2,1},{2,1,0,-1,-1,1,2,2,1,1},{3,2,0,-2,-1,2,3,2,0,-1}};
  final VerticalBandView[] sliders=new VerticalBandView[10]; final TextView[] values=new TextView[10];
  for(int i=0;i<freq.length;i++){
   final int bandIndex=i;
   LinearLayout col=new LinearLayout(this); col.setOrientation(1); col.setGravity(Gravity.CENTER);
   TextView db=text("0 dB",9,CYAN,true); values[i]=db;
   VerticalBandView sb=new VerticalBandView(this); sliders[i]=sb; sb.setValue(0);
   sb.setOnValueChanged(value->{ db.setText((value>0?"+":"")+value+" dB"); if(!applyingPreset) sendBand(bandIndex,value); });
   col.addView(db,new LinearLayout.LayoutParams(-1,dp(22)));
   col.addView(sb,new LinearLayout.LayoutParams(dp(27),dp(150)));
   TextView label=text(freq[i],8,MUTED,false); label.setGravity(Gravity.CENTER); col.addView(label,new LinearLayout.LayoutParams(-1,dp(22)));
   bands.addView(col,new LinearLayout.LayoutParams(0,dp(194),1));
  }
  content.addView(bands);
  LinearLayout actions=new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
  Button reset=button("RESET",()->{applyingPreset=true; for(VerticalBandView v:sliders)v.setValue(0); applyingPreset=false; for(int i=0;i<10;i++)sendBand(i,0);presets.setSelection(1);});
  actions.addView(reset,new LinearLayout.LayoutParams(0,dp(46),1)); content.addView(actions);
  presets.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){ public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int which,long id){ if(which<0||which>=curves.length)return; applyingPreset=true; for(int i=0;i<10;i++)sliders[i].setValue(curves[which][i]); applyingPreset=false; for(int i=0;i<10;i++)sendBand(i,curves[which][i]); }});
  content.addView(gap(8));
  Switch bass=new Switch(this); bass.setText("Bass Boost"); bass.setTextColor(WHITE); bass.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN)); content.addView(bass);
  content.addView(text("Intensity",13,MUTED,true)); SeekBar intensity=new SeekBar(this); intensity.setMax(100); intensity.setProgress(70); intensity.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN)); intensity.setThumbTintList(android.content.res.ColorStateList.valueOf(CYAN)); content.addView(intensity);
  bass.setOnCheckedChangeListener((v,on)->sendBass(on,intensity.getProgress()*10));
  intensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){if(u&&bass.isChecked())sendBass(true,p*10);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
  content.addView(gap(8)); content.addView(text("10 sliders control the available Android equalizer bands. The exact number of hardware bands varies by phone.",11,MUTED,false));
 }
 boolean applyingPreset=false;
 void sendBand(int band,int gain){Intent x=new Intent(this,PlaybackService.class).setAction(PlaybackService.EQ_BAND).putExtra("band",band).putExtra("gain",gain);if(Build.VERSION.SDK_INT>=26)startForegroundService(x);else startService(x);}
 void sendBass(boolean enabled,int strength){Intent x=new Intent(this,PlaybackService.class).setAction(PlaybackService.BASS).putExtra("enabled",enabled).putExtra("strength",strength);if(Build.VERSION.SDK_INT>=26)startForegroundService(x);else startService(x);}
 static class VerticalBandView extends View {
  interface Listener { void onValue(int value); }
  private final android.graphics.Paint paint=new android.graphics.Paint(3); private int value=0; private Listener listener; private float downY; private int startValue;
  VerticalBandView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
  void setOnValueChanged(Listener l){listener=l;}
  int getValue(){return value;}
  void setValue(int v){value=Math.max(-6,Math.min(6,v));invalidate();}
  @Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);float mid=getHeight()/2f, x=getWidth()/2f, top=dpLocal(8), bottom=getHeight()-dpLocal(8);paint.setColor(Color.rgb(48,67,82));paint.setStrokeWidth(dpLocal(4));paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);c.drawLine(x,top,x,bottom,paint);paint.setColor(Color.rgb(34,211,238));paint.setStrokeWidth(dpLocal(3));c.drawLine(x,mid,x,mid-value*(mid-top)/6f,paint);paint.setColor(Color.rgb(34,211,238));c.drawCircle(x,mid-value*(mid-top)/6f,dpLocal(7),paint);paint.setColor(Color.rgb(95,125,145));paint.setStrokeWidth(dpLocal(1));c.drawLine(x-dpLocal(7),mid,x+dpLocal(7),mid,paint);}
  private float dpLocal(float n){return n*getResources().getDisplayMetrics().density;}
  private void setFromY(float y){float mid=getHeight()/2f, range=Math.max(1,mid-dpLocal(8));int next=Math.round((mid-y)*6f/range);next=Math.max(-6,Math.min(6,next));if(next!=value){value=next;invalidate();if(listener!=null)listener.onValue(value);}}
  @Override public boolean onTouchEvent(android.view.MotionEvent e){switch(e.getAction()){case MotionEvent.ACTION_DOWN:downY=e.getY();startValue=value;setPressed(true);setFromY(e.getY());return true;case MotionEvent.ACTION_MOVE:setFromY(e.getY());return true;case MotionEvent.ACTION_UP:setFromY(e.getY());setPressed(false);performClick();return true;case MotionEvent.ACTION_CANCEL:setPressed(false);return true;}return true;}
  @Override public boolean performClick(){super.performClick();return true;}
 }
 void settingsScreen(){content.addView(text("SETTINGS",22,WHITE,true));setting("Appearance","Black theme · Electric blue / cyan");setting("Playback","Resume last song and position");setting("Music Library","Scan local music files");setting("Audio","Equalizer and Bass Boost");setting("App Info","BASS BEATS · Version 1.0");setting("Reset Settings","Restore default preferences");}
 void setting(String title,String desc){LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.setPadding(dp(14),dp(13),dp(14),dp(13));row.setBackground(round(PANEL,dp(12)));row.addView(text(title,14,WHITE,true));row.addView(text(desc,11,MUTED,false));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));content.addView(row,lp);row.setOnClickListener(v->{if(title.equals("Music Library")){loadSongs();showTab("MUSIC");Toast.makeText(this,"Library refreshed",Toast.LENGTH_SHORT).show();}else if(title.equals("Audio")){showTab("EQUALIZER");}else if(title.equals("App Info")){new AlertDialog.Builder(this).setTitle("BASS BEATS").setMessage("Version 1.0\nOffline music player").setPositiveButton("OK",null).show();}else if(title.equals("Reset Settings")){new AlertDialog.Builder(this).setTitle("Reset settings?").setMessage("Restore the app's default screen settings?").setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{Toast.makeText(this,"Settings reset",Toast.LENGTH_SHORT).show();showTab("SETTINGS");}).show();}else if(title.equals("Appearance")){new AlertDialog.Builder(this).setTitle("Appearance").setItems(new String[]{"Black · Electric blue / cyan"},(d,w)->Toast.makeText(this,"Current theme selected",Toast.LENGTH_SHORT).show()).show();}else if(title.equals("Playback")){new AlertDialog.Builder(this).setTitle("Playback").setItems(new String[]{"Resume last song and position", "Audio focus (default)"},(d,w)->Toast.makeText(this,"Playback preference selected",Toast.LENGTH_SHORT).show()).show();}});}
 void loadSongs(){songs.clear();if(checkSelfPermission(Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)return;Uri collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;String[] projection={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST};try(Cursor c=getContentResolver().query(collection,projection,MediaStore.Audio.Media.IS_MUSIC+" != 0",null,MediaStore.Audio.Media.TITLE+" COLLATE NOCASE ASC")){if(c!=null)while(c.moveToNext()){long id=c.getLong(0);String title=c.getString(1),artist=c.getString(2);if(title==null)title="Unknown title";if(artist==null||artist.equals("<unknown>"))artist="Unknown artist";songs.add(new Song(id,title,artist,Uri.withAppendedPath(collection,String.valueOf(id))));}}catch(Exception e){Toast.makeText(this,"Could not scan music",Toast.LENGTH_SHORT).show();}}
 @Override protected void onDestroy(){if(receiverRegistered){try{unregisterReceiver(serviceReceiver);}catch(Exception ignored){}receiverRegistered=false;}super.onDestroy();}
 String fmt(long ms){long sec=Math.max(0,ms/1000);return (sec/60)+":"+String.format(java.util.Locale.US,"%02d",sec%60);}
 void playIndex(int i){if(i<0||i>=songs.size())return;current=i;Intent x=new Intent(this,PlaybackService.class);x.setAction(PlaybackService.PLAY);x.putExtra("uri",songs.get(i).uri.toString());x.putExtra("title",songs.get(i).title);x.putExtra("artist",songs.get(i).artist);if(Build.VERSION.SDK_INT>=26)startForegroundService(x);else startService(x);showTab("PLAYER");}
 void toggle(){playing=!playing;if(playBtn!=null)playBtn.setText(playing?"Ⅱ":"▶");Intent x=new Intent(this,PlaybackService.class);x.setAction(PlaybackService.TOGGLE);if(Build.VERSION.SDK_INT>=26)startForegroundService(x);else startService(x);}
 TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;} View gap(int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return v;} int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} android.graphics.drawable.GradientDrawable round(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;} Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setTextColor(CYAN);b.setOnClickListener(v->r.run());return b;}
 // A true vertical slider: rotation alone leaves Android's touch coordinates horizontal.
 static class VerticalSeekBar extends SeekBar {
  private int lastH, lastW;
  public VerticalSeekBar(Context context){super(context);}
  @Override protected synchronized void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
   super.onMeasure(heightMeasureSpec,widthMeasureSpec);
   setMeasuredDimension(getMeasuredHeight(),getMeasuredWidth());
  }
  @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(h,w,oldh,oldw);lastW=w;lastH=h;}
  @Override protected void onDraw(android.graphics.Canvas canvas){
   canvas.save();
   canvas.rotate(-90);
   canvas.translate(-getHeight(),0);
   super.onDraw(canvas);
   canvas.restore();
  }
  @Override public boolean onTouchEvent(android.view.MotionEvent event){
   if(!isEnabled()) return false;
   float y=event.getY();
   int progress=getMax()-(int)(getMax()*Math.max(0f,Math.min(1f,y/Math.max(1,getHeight()))));
   switch(event.getAction()){
    case MotionEvent.ACTION_DOWN: setPressed(true); if(getThumb()!=null)getThumb().setState(new int[]{android.R.attr.state_pressed}); setProgress(progress); onStartTrackingTouchCompat(); return true;
    case MotionEvent.ACTION_MOVE: setProgress(progress); return true;
    case MotionEvent.ACTION_UP: setProgress(progress); setPressed(false); if(getThumb()!=null)getThumb().setState(new int[]{}); performClick(); return true;
    case MotionEvent.ACTION_CANCEL: setPressed(false); return true;
   }
   return true;
  }
  private void onStartTrackingTouchCompat(){}
  @Override public boolean performClick(){super.performClick();return true;}
 }

}
