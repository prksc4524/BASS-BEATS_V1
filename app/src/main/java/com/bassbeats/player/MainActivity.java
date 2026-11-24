package com.bassbeats.player;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
 static final int BG=Color.rgb(0,0,0), PANEL=Color.rgb(8,10,15), CYAN=Color.rgb(40,125,255), MUTED=Color.rgb(145,155,172), WHITE=Color.WHITE;
 ArrayList<Song> songs=new ArrayList<>(); ArrayList<Song> activeQueue=new ArrayList<>(); ArrayList<Playlist> playlists=new ArrayList<>(); ArrayList<LinearLayout> navItems=new ArrayList<>(); ArrayList<TextView> navIcons=new ArrayList<>(), navLabels=new ArrayList<>(); LinearLayout root, content, nav; BassVisualizerView waveView; ImageView artworkView; String tab="PLAYER", query="", selectedFilter=""; int current=-1; boolean playing=false, shuffleEnabled=false; int repeatMode=0; TextView songTitle,songArtist,playBtn,curTime,durTime; SeekBar seek; Handler handler=new Handler(); boolean userSeeking=false; long trackDuration=0;
  Runnable playerTicker=new Runnable(){public void run(){if(seek!=null&&!userSeeking){Intent x=new Intent(MainActivity.this,PlaybackService.class);x.setAction(PlaybackService.GET_STATE);startService(x);}handler.postDelayed(this,1000);}};
 static class Song {long id; String title,artist,album,path; Uri uri; Song(long i,String t,String a,String al,String pa,Uri u){id=i;title=t;artist=a;album=al;path=pa;uri=u;}}
 static class Playlist {String name; ArrayList<Long> ids=new ArrayList<>(); Playlist(String n){name=n;}}
 String musicSection="All Songs"; Playlist selectedPlaylist=null; boolean viewingPlaylist=false;
 BroadcastReceiver serviceReceiver = new BroadcastReceiver(){@Override public void onReceive(Context c, Intent i){String a=i.getAction();if(PlaybackService.VISUALIZER_DATA.equals(a)){if(waveView!=null){byte[] fft=i.getByteArrayExtra("fft");int sampleRate=i.getIntExtra("sampling_rate",44100);if(fft!=null)waveView.setFftData(fft,sampleRate);}}else if(PlaybackService.SKIP_NEXT.equals(a)){if(!songs.isEmpty())playIndex(current<0?0:(current+1)%songs.size());}else if(PlaybackService.SKIP_PREV.equals(a)){if(!songs.isEmpty())playIndex(current<=0?songs.size()-1:current-1);}else if(PlaybackService.STATE.equals(a)){playing=i.getBooleanExtra("playing",false);if(songTitle!=null&&i.hasExtra("title"))songTitle.setText(cleanSongTitle(i.getStringExtra("title")));if(songArtist!=null&&i.hasExtra("artist"))songArtist.setText(i.getStringExtra("artist"));if(artworkView!=null)updateArtwork();if(playBtn!=null)playBtn.setText(playing?"Ⅱ":"▶");if(i.hasExtra("queue_index")){int serviceIndex=i.getIntExtra("queue_index",-1);if(serviceIndex>=0&&serviceIndex<activeQueue.size()){current=findSongIndex(activeQueue.get(serviceIndex).id);if(songTitle!=null)songTitle.setText(cleanSongTitle(songs.get(current).title));if(songArtist!=null)songArtist.setText(songs.get(current).artist);if(tab.equals("MUSIC") && !viewingPlaylist)renderSongRows();}}if(i.hasExtra("duration")){trackDuration=Math.max(0,i.getLongExtra("duration",0));if(seek!=null){seek.setMax(1000);if(!userSeeking&&trackDuration>0){long pos=Math.max(0,i.getLongExtra("position",0));seek.setProgress((int)Math.max(0,Math.min(1000,(pos*1000L)/trackDuration)));}else if(trackDuration<=0&&!userSeeking){seek.setProgress(0);}}if(durTime!=null)durTime.setText(formatTime(trackDuration));}if(curTime!=null&&i.hasExtra("position"))curTime.setText(formatTime(i.getLongExtra("position",0)));}}};
 boolean receiverRegistered=false;
 @Override public void onCreate(Bundle b){super.onCreate(b); getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(0); requestMusicPermission(); loadPlaylists(); buildShell(); IntentFilter f=new IntentFilter();f.addAction(PlaybackService.SKIP_NEXT);f.addAction(PlaybackService.SKIP_PREV);f.addAction(PlaybackService.STATE);f.addAction(PlaybackService.VISUALIZER_DATA);if(Build.VERSION.SDK_INT>=33)registerReceiver(serviceReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(serviceReceiver,f);receiverRegistered=true;}
 void requestMusicPermission(){String p=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;ArrayList<String> needed=new ArrayList<>();if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)needed.add(p);if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)needed.add(Manifest.permission.RECORD_AUDIO);if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)needed.add(Manifest.permission.READ_PHONE_STATE);if(!needed.isEmpty())requestPermissions(needed.toArray(new String[0]),11);else loadSongs();}
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==11){String music=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;boolean musicGranted=checkSelfPermission(music)==PackageManager.PERMISSION_GRANTED;if(musicGranted){loadSongs();showTab(tab);}else Toast.makeText(this,"Music access is needed to scan local songs",Toast.LENGTH_LONG).show();if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)Toast.makeText(this,"Allow microphone/audio permission for music-synced wave animation",Toast.LENGTH_LONG).show();if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)Toast.makeText(this,"Allow Phone permission for automatic pause/resume during calls",Toast.LENGTH_LONG).show();}}
 void buildShell(){root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);root.setPadding(dp(16),dp(8),dp(16),dp(40));setContentView(root); TextView brand=text("BASS BEATS",23,CYAN,true);brand.setGravity(Gravity.CENTER);brand.setVisibility(View.INVISIBLE);root.addView(brand,new LinearLayout.LayoutParams(-1,dp(40))); TextView sub=text("OFFLINE MUSIC PLAYER",10,MUTED,true);sub.setLetterSpacing(.08f);sub.setGravity(Gravity.CENTER);sub.setVisibility(View.INVISIBLE);LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(-1,dp(22));subLp.bottomMargin=dp(4);root.addView(sub,subLp); FrameLayout area=new FrameLayout(this);root.addView(area,new LinearLayout.LayoutParams(-1,0,1)); content=new LinearLayout(this);content.setOrientation(1);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);scroll.addView(content);area.addView(scroll); nav=new LinearLayout(this);nav.setOrientation(0);nav.setGravity(Gravity.CENTER);nav.setBackground(round(PANEL,dp(18)));nav.setPadding(dp(4),dp(5),dp(4),dp(5));LinearLayout.LayoutParams navLp=new LinearLayout.LayoutParams(-1,dp(64));navLp.topMargin=dp(5);root.addView(nav,navLp);String[] tabs={"PLAYER","MUSIC","EQUALIZER","SETTINGS"};String[] icons={"♫","☷","≋","⚙"};navItems.clear();navIcons.clear();navLabels.clear();for(int i=0;i<tabs.length;i++){final String t=tabs[i];LinearLayout item=new LinearLayout(this);item.setOrientation(1);item.setGravity(Gravity.CENTER);item.setPadding(0,dp(4),0,dp(3));TextView ico=text(icons[i],21,MUTED,true);ico.setGravity(Gravity.CENTER);TextView label=text(t,9,MUTED,true);label.setGravity(Gravity.CENTER);item.addView(ico,new LinearLayout.LayoutParams(-1,-2));item.addView(label,new LinearLayout.LayoutParams(-1,-2));nav.addView(item,new LinearLayout.LayoutParams(0,-1,1));navItems.add(item);navIcons.add(ico);navLabels.add(label);item.setOnClickListener(v->showTab(t));}showTab("PLAYER");}
 void showTab(String t){tab=t;content.removeAllViews();for(int i=0;i<navItems.size();i++){boolean selected=new String[]{"PLAYER","MUSIC","EQUALIZER","SETTINGS"}[i].equals(t);navItems.get(i).setBackground(selected?round(Color.rgb(8,24,48),dp(12)):null);navIcons.get(i).setTextColor(selected?CYAN:MUTED);navLabels.get(i).setTextColor(selected?CYAN:MUTED);} if(t.equals("PLAYER"))playerScreen();else if(t.equals("MUSIC"))musicScreen();else if(t.equals("EQUALIZER"))eqScreen();else settingsScreen();}
   void playerScreen(){
  content.addView(gap(6));
  TextView heading=text("NOW PLAYING",12,MUTED,true);heading.setLetterSpacing(.06f);heading.setGravity(Gravity.CENTER);content.addView(heading);
  artworkView=new ImageView(this);artworkView.setScaleType(ImageView.ScaleType.CENTER_CROP);artworkView.setBackground(round(Color.rgb(9,18,32),dp(22)));artworkView.setClipToOutline(true);
  LinearLayout.LayoutParams artLp=new LinearLayout.LayoutParams(dp(148),dp(148));artLp.gravity=Gravity.CENTER;artLp.topMargin=dp(18);artLp.bottomMargin=dp(14);content.addView(artworkView,artLp);updateArtwork();
  waveView=new BassVisualizerView(this);LinearLayout.LayoutParams waveLp=new LinearLayout.LayoutParams(-1,dp(112));waveLp.gravity=Gravity.CENTER;waveLp.setMargins(0,dp(8),0,dp(14));content.addView(waveView,waveLp);
  android.content.SharedPreferences displayPrefs=getSharedPreferences("player_display",MODE_PRIVATE);
  boolean visualizerMode=displayPrefs.getString("mode","visualizer").equals("visualizer");
  artworkView.setVisibility(visualizerMode?View.GONE:View.VISIBLE);
  waveView.setVisibility(visualizerMode?View.VISIBLE:View.GONE);
  if(visualizerMode){LinearLayout.LayoutParams visualLp=(LinearLayout.LayoutParams)waveView.getLayoutParams();visualLp.height=dp(220);visualLp.width=-1;visualLp.gravity=Gravity.CENTER;visualLp.setMargins(dp(8),dp(22),dp(8),dp(22));waveView.setLayoutParams(visualLp);}
  songTitle=text(current>=0&&current<songs.size()?cleanSongTitle(songs.get(current).title):"Choose a song",20,WHITE,true);songTitle.setGravity(Gravity.CENTER);songTitle.setMaxLines(2);songTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);titleLp.setMargins(dp(8),dp(6),dp(8),0);content.addView(songTitle,titleLp);
  songArtist=text(current>=0&&current<songs.size()?songs.get(current).artist:"Your offline library",13,MUTED,false);songArtist.setGravity(Gravity.CENTER);LinearLayout.LayoutParams artistLp=new LinearLayout.LayoutParams(-1,-2);artistLp.topMargin=dp(6);content.addView(songArtist,artistLp);
  View spacer=new View(this);content.addView(spacer,new LinearLayout.LayoutParams(1,0,1f));
  LinearLayout times=new LinearLayout(this);times.setGravity(Gravity.CENTER_VERTICAL);times.setPadding(dp(2),0,dp(2),0);curTime=text("0:00",11,MUTED,false);durTime=text("0:00",11,MUTED,false);times.addView(curTime,new LinearLayout.LayoutParams(0,-2,1));durTime.setGravity(Gravity.RIGHT);times.addView(durTime,new LinearLayout.LayoutParams(0,-2,1));content.addView(times);
  seek=new SeekBar(this);seek.setMax(1000);seek.setPadding(dp(2),0,dp(2),0);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int progress,boolean fromUser){}public void onStartTrackingTouch(SeekBar b){userSeeking=true;}public void onStopTrackingTouch(SeekBar b){long target=trackDuration>0?(trackDuration*(long)b.getProgress())/Math.max(1,b.getMax()):0;userSeeking=false;Intent x=new Intent(MainActivity.this,PlaybackService.class);x.setAction(PlaybackService.SEEK);x.putExtra("position",target);if(Build.VERSION.SDK_INT>=26)startForegroundService(x);else startService(x);}});seek.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(CYAN));seek.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(38,48,66)));content.addView(seek);
  LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);controls.setPadding(0,dp(5),0,dp(5));String[] c={"⤨","|◀","▶","▶|","↻"};for(int i=0;i<c.length;i++){final int n=i;TextView b=text(c[i],i==2?27:21,i==2?CYAN:WHITE,true);b.setGravity(Gravity.CENTER);if(n==2){playBtn=b;android.graphics.drawable.GradientDrawable playBg=round(Color.rgb(5,20,42),dp(32));playBg.setStroke(dp(1),CYAN);b.setBackground(playBg);b.setElevation(dp(3));}controls.addView(b,new LinearLayout.LayoutParams(0,dp(58),i==2?1.25f:1));b.setOnClickListener(v->{if(n==1)sendTransport(PlaybackService.PREV);else if(n==2)toggle();else if(n==3)sendTransport(PlaybackService.NEXT);else if(n==0){shuffleEnabled=!shuffleEnabled;b.setText(shuffleEnabled?"🔀":"⤨");b.setTextColor(shuffleEnabled?CYAN:WHITE);Toast.makeText(this,shuffleEnabled?"Shuffle on":"Shuffle off",Toast.LENGTH_SHORT).show();}else{repeatMode=(repeatMode+1)%3;b.setText(repeatMode==0?"↻":repeatMode==1?"🔁":"1↻");b.setTextColor(repeatMode==0?WHITE:CYAN);Toast.makeText(this,repeatMode==0?"Repeat off":repeatMode==1?"Repeat all":"Repeat one",Toast.LENGTH_SHORT).show();sendRepeatMode();}});}content.addView(controls);
 }
 android.graphics.Bitmap createDefaultArtwork(){
  int size=512;android.graphics.Bitmap bmp=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);
  android.graphics.Canvas canvas=new android.graphics.Canvas(bmp);android.graphics.Paint p=new android.graphics.Paint(3);
  p.setShader(new android.graphics.LinearGradient(0,0,size,size,Color.rgb(18,34,62),Color.rgb(8,10,20),android.graphics.Shader.TileMode.CLAMP));canvas.drawRoundRect(0,0,size,size,64,64,p);p.setShader(null);
  p.setColor(Color.rgb(35,55,88));for(int r=82;r<220;r+=17){p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(3);canvas.drawCircle(256,256,r,p);}p.setStyle(android.graphics.Paint.Style.FILL);
  p.setShader(new android.graphics.RadialGradient(256,256,100,new int[]{Color.rgb(165,105,255),Color.rgb(45,125,255),Color.rgb(14,24,44)},null,android.graphics.Shader.TileMode.CLAMP));canvas.drawCircle(256,256,76,p);p.setShader(null);
  p.setColor(Color.WHITE);p.setTextAlign(android.graphics.Paint.Align.CENTER);p.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));p.setTextSize(112);canvas.drawText("♫",256,295,p);
  p.setColor(Color.rgb(110,150,255));p.setTextSize(22);p.setLetterSpacing(0.18f);canvas.drawText("BASS BEATS",256,430,p);return bmp;
 }
 void updateArtwork(){
  if(artworkView==null)return;
  artworkView.setImageDrawable(null);artworkView.setBackground(round(Color.rgb(9,18,32),dp(22)));
  if(current<0||current>=songs.size()){artworkView.setImageBitmap(createDefaultArtwork());return;}
  MediaMetadataRetriever mmr=new MediaMetadataRetriever();
  try{mmr.setDataSource(songs.get(current).path);byte[] bytes=mmr.getEmbeddedPicture();if(bytes!=null){android.graphics.Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap!=null){artworkView.setImageBitmap(bitmap);return;}}}catch(Exception ignored){}finally{try{mmr.release();}catch(Exception ignored){}}
  artworkView.setImageBitmap(createDefaultArtwork());
 }
 void musicScreen(){
  if(viewingPlaylist && selectedPlaylist!=null){playlistDetailScreen(selectedPlaylist);return;}
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
  TextView h=text("YOUR MUSIC",21,WHITE,true);header.addView(h,new LinearLayout.LayoutParams(0,-2,1));
  TextView refresh=text("⟳",24,CYAN,true);refresh.setGravity(Gravity.CENTER);header.addView(refresh,new LinearLayout.LayoutParams(dp(42),dp(42)));refresh.setOnClickListener(v->{loadSongs();showTab("MUSIC");Toast.makeText(this,"Music library refreshed",Toast.LENGTH_SHORT).show();});
  content.addView(header);
  EditText search=new EditText(this);search.setSingleLine(true);search.setHint("⌕  Search songs");search.setHintTextColor(MUTED);search.setTextColor(WHITE);search.setTextSize(14);search.setBackground(round(PANEL,dp(14)));search.setPadding(dp(14),0,dp(14),0);LinearLayout.LayoutParams searchLp=new LinearLayout.LayoutParams(-1,dp(48));searchLp.topMargin=dp(10);content.addView(search,searchLp);
  LinearLayout tabs=new LinearLayout(this);tabs.setGravity(Gravity.CENTER);tabs.setPadding(0,dp(12),0,dp(10));
  for(String section:new String[]{"All Songs","Playlists"}){TextView b=text(section,13,section.equals(musicSection)?CYAN:WHITE,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(18),dp(10),dp(18),dp(10));b.setBackground(round(section.equals(musicSection)?Color.rgb(8,24,48):PANEL,dp(14)));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(44),1);bp.setMargins(dp(3),0,dp(3),0);tabs.addView(b,bp);b.setOnClickListener(v->{musicSection=section;query="";showTab("MUSIC");});}
  content.addView(tabs);
  TextView count=text(musicSection.equals("All Songs")?"ALL SONGS  ·  "+songs.size():"YOUR PLAYLISTS  ·  "+playlists.size(),12,CYAN,true);count.setPadding(dp(2),dp(5),0,dp(8));content.addView(count);
  search.setVisibility(musicSection.equals("All Songs")?View.VISIBLE:View.GONE);
  search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int n,int a){}public void onTextChanged(CharSequence s,int st,int before,int n){query=s.toString().toLowerCase(Locale.ROOT);renderSongRows();}public void afterTextChanged(android.text.Editable e){}});
  if(musicSection.equals("Playlists")){Button add=button("＋  CREATE PLAYLIST",()->createPlaylist());add.setAllCaps(false);add.setTextColor(CYAN);add.setBackground(round(Color.rgb(8,24,48),dp(12)));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(48));ap.bottomMargin=dp(8);content.addView(add,ap);}
  renderSongRows();
 }
 void renderSongRows(){
  if(!tab.equals("MUSIC"))return;
  int start=0;for(int i=0;i<content.getChildCount();i++)if(content.getChildAt(i) instanceof TextView && ((TextView)content.getChildAt(i)).getText().toString().startsWith(musicSection.equals("All Songs")?"ALL SONGS  ·":"YOUR PLAYLISTS  ·")){start=i+1;break;}
  if(musicSection.equals("Playlists") && content.getChildCount()>start && content.getChildAt(start) instanceof Button)start++;
  while(content.getChildCount()>start)content.removeViewAt(start);
  if(musicSection.equals("Playlists")){if(content.getChildCount()>start){};for(Playlist p:playlists){final Playlist selected=p;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(13),dp(12),dp(10),dp(12));row.setBackground(round(PANEL,dp(14)));TextView art=text("♫",25,CYAN,true);art.setGravity(Gravity.CENTER);art.setBackground(round(Color.rgb(8,24,48),dp(10)));row.addView(art,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setPadding(dp(11),0,0,0);info.addView(text(p.name,14,WHITE,true));info.addView(text(p.ids.size()+" songs",11,MUTED,false));row.addView(info,new LinearLayout.LayoutParams(0,-2,1));TextView menu=text("⋮",24,WHITE,true);row.addView(menu);menu.setOnClickListener(v->playlistMenu(selected));row.setOnClickListener(v->openPlaylist(selected));content.addView(row,rowParams());}if(playlists.isEmpty())content.addView(text("No playlists yet. Create one and add your favourite songs.",13,MUTED,false));return;}
  int shown=0;for(int i=0;i<songs.size();i++){Song s=songs.get(i);String searchable=(s.title+" "+s.artist+" "+s.album).toLowerCase(Locale.ROOT);if(!searchable.contains(query))continue;final int index=i;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(9),dp(8),dp(9));row.setBackground(round(i==current?Color.rgb(7,25,53):PANEL,dp(13)));TextView art=text("♫",22,i==current?CYAN:WHITE,true);art.setGravity(Gravity.CENTER);art.setBackground(round(Color.rgb(15,20,29),dp(9)));row.addView(art,new LinearLayout.LayoutParams(dp(46),dp(46)));LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setPadding(dp(10),0,dp(4),0);info.addView(text(cleanSongTitle(s.title),14,i==current?CYAN:WHITE,true));TextView detail=text(s.artist+"  ·  "+(s.album==null?"Unknown album":s.album),10,MUTED,false);detail.setMaxLines(1);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(detail);row.addView(info,new LinearLayout.LayoutParams(0,dp(48),1));TextView menu=text("⋮",24,WHITE,true);menu.setGravity(Gravity.CENTER);row.addView(menu,new LinearLayout.LayoutParams(dp(30),dp(48)));menu.setOnClickListener(v->songMenu(s));row.setOnClickListener(v->playSongs(songs,index));content.addView(row,rowParams());shown++;}
  if(shown==0)content.addView(text(songs.isEmpty()?"No local songs found. Add audio files to your phone and refresh.":"No songs match your search.",13,MUTED,false));
 }
 String cleanSongTitle(String title){if(title==null)return "Unknown title";String s=title.replace('_',' ').replaceAll("(?i)\\.(mp3|m4a|aac|wav|ogg|flac)$","").replaceAll("(?i)\\s*\\((\\d+\\s*k(bps)?)\\)","").replaceAll("\\s+"," ").trim();return s.isEmpty()?"Unknown title":s;}
 int findSongIndex(long id){for(int i=0;i<songs.size();i++)if(songs.get(i).id==id)return i;return -1;}
 void songMenu(Song song){String[] actions={"Play now","Add to playlist"};new AlertDialog.Builder(this).setTitle(cleanSongTitle(song.title)).setItems(actions,(d,w)->{if(w==0)playSongs(songs,findSongIndex(song.id));else choosePlaylistForSong(song);}).show();}
 void createPlaylist(){EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Playlist name");input.setTextColor(WHITE);input.setHintTextColor(MUTED);new AlertDialog.Builder(this).setTitle("New playlist").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{String name=input.getText().toString().trim();if(name.isEmpty()){Toast.makeText(this,"Enter a playlist name",Toast.LENGTH_SHORT).show();return;}Playlist p=new Playlist(name);playlists.add(p);savePlaylists();showTab("MUSIC");addSongsToPlaylist(p);}).show();}
 void playlistMenu(Playlist p){new AlertDialog.Builder(this).setTitle(p.name).setItems(new String[]{"Play playlist","Add songs","Rename","Delete playlist"},(d,w)->{if(w==0)openPlaylist(p);else if(w==1)addSongsToPlaylist(p);else if(w==2)renamePlaylist(p);else new AlertDialog.Builder(this).setTitle("Delete playlist?").setMessage("Songs on your phone will not be deleted.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(x,y)->{playlists.remove(p);savePlaylists();showTab("MUSIC");}).show();}).show();}
 void renamePlaylist(Playlist p){EditText input=new EditText(this);input.setText(p.name);input.setTextColor(WHITE);new AlertDialog.Builder(this).setTitle("Rename playlist").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{String n=input.getText().toString().trim();if(!n.isEmpty()){p.name=n;savePlaylists();showTab("MUSIC");}}).show();}
 void addSongsToPlaylist(Playlist p){if(songs.isEmpty()){Toast.makeText(this,"No songs found",Toast.LENGTH_SHORT).show();return;}String[] labels=new String[songs.size()];boolean[] checked=new boolean[songs.size()];for(int i=0;i<songs.size();i++){Song s=songs.get(i);labels[i]=cleanSongTitle(s.title)+" · "+s.artist;checked[i]=p.ids.contains(s.id);}new AlertDialog.Builder(this).setTitle("Add songs to "+p.name).setMultiChoiceItems(labels,checked,(d,w,is)->{long id=songs.get(w).id;if(is){if(!p.ids.contains(id))p.ids.add(id);}else p.ids.remove(id);}).setNegativeButton("Cancel",null).setPositiveButton("Done",(d,w)->{savePlaylists();if(viewingPlaylist&&selectedPlaylist==p)showTab("MUSIC");else showTab("MUSIC");}).show();}
 void choosePlaylistForSong(Song song){if(playlists.isEmpty()){new AlertDialog.Builder(this).setMessage("Create a playlist first.").setPositiveButton("Create",(d,w)->createPlaylist()).setNegativeButton("Cancel",null).show();return;}String[] names=new String[playlists.size()];for(int i=0;i<names.length;i++)names[i]=playlists.get(i).name;new AlertDialog.Builder(this).setTitle("Add to playlist").setItems(names,(d,w)->{Playlist p=playlists.get(w);if(!p.ids.contains(song.id))p.ids.add(song.id);savePlaylists();Toast.makeText(this,"Added to "+p.name,Toast.LENGTH_SHORT).show();}).show();}
 void openPlaylist(Playlist p){selectedPlaylist=p;viewingPlaylist=true;showTab("MUSIC");}
 void playlistDetailScreen(Playlist p){
  LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
  TextView back=text("‹",32,CYAN,true);back.setGravity(Gravity.CENTER);top.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));back.setOnClickListener(v->{viewingPlaylist=false;selectedPlaylist=null;showTab("MUSIC");});
  TextView title=text(p.name,19,WHITE,true);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  TextView menu=text("⋮",25,WHITE,true);menu.setGravity(Gravity.CENTER);top.addView(menu,new LinearLayout.LayoutParams(dp(38),dp(48)));menu.setOnClickListener(v->playlistMenu(p));content.addView(top);
  TextView count=text(p.ids.size()+" SONGS",11,CYAN,true);count.setPadding(dp(4),dp(5),0,dp(8));content.addView(count);
  LinearLayout actions=new LinearLayout(this);actions.setOrientation(0);
  Button play=button("▶  PLAY ALL",()->{ArrayList<Song> list=playlistSongs(p);if(!list.isEmpty())playSongs(list,0);else Toast.makeText(this,"No available songs",Toast.LENGTH_SHORT).show();});play.setAllCaps(false);play.setBackground(round(Color.rgb(8,24,48),dp(12)));actions.addView(play,new LinearLayout.LayoutParams(0,dp(46),1));
  Button add=button("＋  ADD SONGS",()->addSongsToPlaylist(p));add.setAllCaps(false);add.setBackground(round(PANEL,dp(12)));LinearLayout.LayoutParams addLp=new LinearLayout.LayoutParams(0,dp(46),1);addLp.leftMargin=dp(7);actions.addView(add,addLp);content.addView(actions);
  ArrayList<Song> list=playlistSongs(p);
  if(list.isEmpty()){content.addView(text("This playlist is empty. Add songs to get started.",13,MUTED,false));return;}
  for(int i=0;i<list.size();i++){
   final int pos=i;Song s=list.get(i);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(8),dp(5),dp(8));row.setBackground(round(PANEL,dp(12)));
   TextView art=text("♫",21,CYAN,true);art.setGravity(Gravity.CENTER);row.addView(art,new LinearLayout.LayoutParams(dp(38),dp(42)));
   LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setPadding(dp(8),0,dp(3),0);info.addView(text(cleanSongTitle(s.title),13,WHITE,true));TextView sub=text(s.artist,10,MUTED,false);sub.setMaxLines(1);sub.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(sub);row.addView(info,new LinearLayout.LayoutParams(0,dp(44),1));
   TextView up=text("↑",19,pos==0?MUTED:CYAN,true);up.setGravity(Gravity.CENTER);row.addView(up,new LinearLayout.LayoutParams(dp(34),dp(42)));up.setOnClickListener(v->movePlaylistSong(p,pos,-1));
   TextView down=text("↓",19,pos==list.size()-1?MUTED:CYAN,true);down.setGravity(Gravity.CENTER);row.addView(down,new LinearLayout.LayoutParams(dp(34),dp(42)));down.setOnClickListener(v->movePlaylistSong(p,pos,1));
   TextView remove=text("×",23,WHITE,true);remove.setGravity(Gravity.CENTER);row.addView(remove,new LinearLayout.LayoutParams(dp(32),dp(42)));remove.setOnClickListener(v->{p.ids.remove(Long.valueOf(s.id));savePlaylists();showTab("MUSIC");});
   row.setOnClickListener(v->playSongs(list,pos));content.addView(row,rowParams());
  }
 }
 ArrayList<Song> playlistSongs(Playlist p){ArrayList<Song> list=new ArrayList<>();for(Long id:p.ids){int i=findSongIndex(id);if(i>=0)list.add(songs.get(i));}return list;}
 void movePlaylistSong(Playlist p,int index,int delta){int next=index+delta;if(index<0||next<0||next>=p.ids.size())return;Collections.swap(p.ids,index,next);savePlaylists();showTab("MUSIC");}
 void removeSongsFromPlaylist(Playlist p){ArrayList<Song> list=new ArrayList<>();for(Long id:p.ids){int i=findSongIndex(id);if(i>=0)list.add(songs.get(i));}String[] labels=new String[list.size()];boolean[] checked=new boolean[list.size()];for(int i=0;i<list.size();i++)labels[i]=cleanSongTitle(list.get(i).title);new AlertDialog.Builder(this).setTitle("Select songs to remove").setMultiChoiceItems(labels,checked,(d,w,is)->{}).setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{android.widget.ListView lv=((AlertDialog)d).getListView();for(int i=list.size()-1;i>=0;i--)if(lv.isItemChecked(i))p.ids.remove(Long.valueOf(list.get(i).id));savePlaylists();showTab("MUSIC");}).show();}
 void savePlaylists(){try{JSONArray arr=new JSONArray();for(Playlist p:playlists){JSONObject o=new JSONObject();o.put("name",p.name);JSONArray ids=new JSONArray();for(Long id:p.ids)ids.put(id);o.put("ids",ids);arr.put(o);}getSharedPreferences("music_playlists",MODE_PRIVATE).edit().putString("data",arr.toString()).apply();}catch(Exception ignored){}}
 void loadPlaylists(){playlists.clear();try{JSONArray arr=new JSONArray(getSharedPreferences("music_playlists",MODE_PRIVATE).getString("data","[]"));for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);Playlist p=new Playlist(o.optString("name","My Playlist"));JSONArray ids=o.optJSONArray("ids");if(ids!=null)for(int j=0;j<ids.length();j++)p.ids.add(ids.getLong(j));playlists.add(p);}}catch(Exception ignored){}}
 void playSongs(ArrayList<Song> list,int index){if(index<0||index>=list.size())return;activeQueue=new ArrayList<>(list);Song s=activeQueue.get(index);current=findSongIndex(s.id);if(songTitle!=null)songTitle.setText(cleanSongTitle(s.title));if(songArtist!=null)songArtist.setText(s.artist);Intent x=new Intent(this,PlaybackService.class);x.setAction(PlaybackService.PLAY);x.putExtra("uri",s.uri.toString());x.putExtra("title",cleanSongTitle(s.title));x.putExtra("artist",s.artist);String[] uris=new String[activeQueue.size()],titles=new String[activeQueue.size()],artists=new String[activeQueue.size()];for(int i=0;i<activeQueue.size();i++){Song q=activeQueue.get(i);uris[i]=q.uri.toString();titles[i]=cleanSongTitle(q.title);artists[i]=q.artist;}x.putExtra("repeat_mode",0);x.putExtra("queue_uris",uris);x.putExtra("queue_titles",titles);x.putExtra("queue_artists",artists);x.putExtra("queue_index",index);if(Build.VERSION.SDK_INT>=26)startForegroundService(x);else startService(x);showTab("PLAYER");}
 LinearLayout libraryRow(String title,String subtitle,Runnable click){LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setBackground(round(PANEL,dp(12)));row.addView(text(title,14,WHITE,true));row.addView(text(subtitle,10,MUTED,false));row.setOnClickListener(v->click.run());return row;}
 LinearLayout.LayoutParams rowParams(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(4));return lp;}
 void eqScreen(){
  android.content.SharedPreferences prefs=getSharedPreferences("audio_effects",MODE_PRIVATE);
  content.addView(text("EQ STUDIO",22,WHITE,true));
  content.addView(text("Fine-tune your sound",12,MUTED,false));
  Switch power=new Switch(this);power.setText("Equalizer");power.setTextColor(WHITE);power.setChecked(prefs.getBoolean("eq_enabled",true));
  LinearLayout powerCard=new LinearLayout(this);powerCard.setGravity(Gravity.CENTER_VERTICAL);powerCard.setPadding(dp(16),dp(8),dp(12),dp(8));powerCard.setBackground(round(Color.rgb(14,21,38),dp(18)));powerCard.addView(text("EQ STUDIO",17,WHITE,true),new LinearLayout.LayoutParams(0,-2,1));powerCard.addView(power);content.addView(powerCard);
  String[] freq={"31","62","125","250","500","1k","2k","4k","8k","16k"};int[] defaults={5,7,5,2,0,-1,0,1,2,2};
  if(!prefs.contains("band_0")){android.content.SharedPreferences.Editor e=prefs.edit();for(int i=0;i<10;i++)e.putInt("band_"+i,defaults[i]);e.apply();}
  LinearLayout eqCard=new LinearLayout(this);eqCard.setOrientation(1);eqCard.setPadding(dp(5),dp(14),dp(5),dp(12));eqCard.setBackground(round(Color.rgb(13,19,34),dp(22)));
  LinearLayout headingRow=new LinearLayout(this);headingRow.setGravity(Gravity.CENTER_VERTICAL);TextView heading=text("10-BAND EQUALIZER",12,WHITE,true);headingRow.addView(heading,new LinearLayout.LayoutParams(0,-2,1));TextView reset=new TextView(this);reset.setText("↻ Reset");reset.setTextColor(CYAN);reset.setTextSize(11);reset.setTypeface(Typeface.DEFAULT,Typeface.BOLD);reset.setPadding(dp(8),dp(5),dp(8),dp(5));reset.setBackground(round(Color.rgb(25,39,65),dp(14)));headingRow.addView(reset);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.leftMargin=dp(7);hp.rightMargin=dp(5);hp.bottomMargin=dp(5);eqCard.addView(headingRow,hp);
  LinearLayout bands=new LinearLayout(this);bands.setGravity(Gravity.CENTER);VerticalEqSlider[] sliders=new VerticalEqSlider[10];
  for(int i=0;i<10;i++){
   final int bi=i;int level=Math.max(-12,Math.min(12,prefs.getInt("band_"+i,defaults[i])));
   LinearLayout col=new LinearLayout(this);col.setOrientation(1);col.setGravity(Gravity.CENTER_HORIZONTAL);
   TextView label=text(freq[i],10,MUTED,true);label.setGravity(Gravity.CENTER);col.addView(label,new LinearLayout.LayoutParams(-1,dp(20)));
   
   TextView db=text((level>0?"+":"")+level,9,CYAN,true);db.setGravity(Gravity.CENTER);col.addView(db,new LinearLayout.LayoutParams(-1,dp(23)));
   VerticalEqSlider slider=new VerticalEqSlider(this);slider.setLevel(level);slider.setOnLevelChanged(v->{db.setText((v>0?"+":"")+v);prefs.edit().putInt("band_"+bi,v).apply();if(power.isChecked())sendEffect(PlaybackService.EQ_BAND,"band",bi,"level",v);});sliders[i]=slider;
   TextView topRange=text("+12",7,MUTED,false);topRange.setGravity(Gravity.CENTER);col.addView(topRange,new LinearLayout.LayoutParams(-1,dp(12)));col.addView(slider,new LinearLayout.LayoutParams(-1,dp(174)));
   TextView bottomRange=text("-12",7,MUTED,false);bottomRange.setGravity(Gravity.CENTER);col.addView(bottomRange,new LinearLayout.LayoutParams(-1,dp(12)));
   LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1f);cp.leftMargin=dp(0);cp.rightMargin=dp(0);bands.addView(col,cp);
  }
  eqCard.addView(bands);LinearLayout.LayoutParams eqLp=new LinearLayout.LayoutParams(-1,-2);eqLp.topMargin=dp(10);eqLp.bottomMargin=dp(8);content.addView(eqCard,eqLp);
  String[] names={"Flat","Deep","Vocal","Electronic","Rock","Jazz","Classical"};
  int[][] values={{0,0,0,0,0,0,0,0,0,0},{5,7,5,2,0,-1,0,1,2,2},{-2,-1,1,3,4,3,1,0,-1,-2},{2,3,1,-1,-2,0,2,4,5,4},{4,3,1,-2,-3,1,3,4,4,3},{3,2,0,-2,-2,0,2,3,3,2},{4,3,1,0,-1,1,2,3,2,1}};
  LinearLayout presetCard=new LinearLayout(this);presetCard.setOrientation(1);presetCard.setPadding(dp(12),dp(7),dp(12),dp(7));presetCard.setBackground(round(Color.rgb(14,21,38),dp(18)));presetCard.addView(text("PRESETS",12,WHITE,true));
  LinearLayout[] presetRows={new LinearLayout(this),new LinearLayout(this)};for(LinearLayout row:presetRows){row.setGravity(Gravity.CENTER);row.setPadding(0,dp(2),0,dp(1));presetCard.addView(row);}
  Button[] presetButtons=new Button[7];int selected=prefs.getInt("selected_preset",1);
  for(int i=0;i<7;i++){final int pi=i;Button chip=new Button(this);chip.setAllCaps(false);chip.setText(names[i]);chip.setTextSize(10);chip.setTextColor(Color.WHITE);chip.setMinHeight(dp(30));chip.setMinimumHeight(dp(30));chip.setPadding(dp(2),0,dp(2),0);chip.setBackground(round(i==selected?Color.rgb(48,39,91):Color.rgb(24,31,49),dp(18)));presetButtons[i]=chip;LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,dp(34),1);pp.leftMargin=dp(3);pp.rightMargin=dp(3);presetRows[i<4?0:1].addView(chip,pp);chip.setOnClickListener(v->{prefs.edit().putInt("selected_preset",pi).apply();for(int k=0;k<7;k++)presetButtons[k].setBackground(round(k==pi?Color.rgb(48,39,91):Color.rgb(24,31,49),dp(22)));android.content.SharedPreferences.Editor ed=prefs.edit();for(int j=0;j<10;j++){sliders[j].setLevel(values[pi][j]);ed.putInt("band_"+j,values[pi][j]);if(power.isChecked())sendEffect(PlaybackService.EQ_BAND,"band",j,"level",values[pi][j]);}ed.apply();});}
  LinearLayout.LayoutParams presetLp=new LinearLayout.LayoutParams(-1,-2);presetLp.topMargin=dp(8);presetLp.bottomMargin=dp(12);content.addView(presetCard,presetLp);
  LinearLayout bassCard=new LinearLayout(this);bassCard.setOrientation(1);bassCard.setPadding(dp(15),dp(12),dp(15),dp(12));bassCard.setBackground(round(Color.rgb(14,21,38),dp(18)));
  LinearLayout bassRow=new LinearLayout(this);bassRow.setGravity(Gravity.CENTER_VERTICAL);bassRow.addView(text("◉   Bass Boost",15,WHITE,true),new LinearLayout.LayoutParams(0,-2,1));Switch bass=new Switch(this);bass.setChecked(prefs.getBoolean("bass_enabled",false));bassRow.addView(bass);bassCard.addView(bassRow);
  bassCard.addView(text("Intensity",12,MUTED,true));SeekBar intensity=new SeekBar(this);intensity.setMax(100);intensity.setProgress(prefs.getInt("bass_strength",700)/10);intensity.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));intensity.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.rgb(165,112,255)));bassCard.addView(intensity);
  Runnable applyBass=()->sendBassEffect(bass.isChecked(),intensity.getProgress()*10);bass.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("bass_enabled",on).apply();applyBass.run();});intensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar v,int p,boolean user){if(user){prefs.edit().putInt("bass_strength",p*10).apply();applyBass.run();}}public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){}});
  LinearLayout.LayoutParams bassLp=new LinearLayout.LayoutParams(-1,-2);bassLp.topMargin=dp(4);bassLp.bottomMargin=dp(12);content.addView(bassCard,bassLp);
  reset.setOnClickListener(v->{android.content.SharedPreferences.Editor ed=prefs.edit();for(int j=0;j<10;j++){sliders[j].setLevel(0);ed.putInt("band_"+j,0);sendEffect(PlaybackService.EQ_BAND,"band",j,"level",0);}bass.setChecked(false);intensity.setProgress(0);ed.putBoolean("bass_enabled",false).putInt("bass_strength",0).putInt("selected_preset",0).apply();sendBassEffect(false,0);for(int k=0;k<7;k++)presetButtons[k].setBackground(round(k==0?Color.rgb(48,39,91):Color.rgb(24,31,49),dp(18)));Toast.makeText(this,"Equalizer reset to 0 dB",Toast.LENGTH_SHORT).show();});
  power.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("eq_enabled",on).apply();for(int j=0;j<10;j++)sendEffect(PlaybackService.EQ_BAND,"band",j,"level",on?sliders[j].getLevel():0);});

 }
 class VerticalEqSlider extends View {
  private final android.graphics.Paint paint=new android.graphics.Paint(3);private int level=0;private java.util.function.IntConsumer listener;
  VerticalEqSlider(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setFocusable(true);}
  void setOnLevelChanged(java.util.function.IntConsumer l){listener=l;}
  int getLevel(){return level;}
  void setLevel(int v){level=Math.max(-12,Math.min(12,v));invalidate();}
  protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);float cx=getWidth()/2f,top=dp(10),bottom=getHeight()-dp(10),mid=(top+bottom)/2f;
   paint.setStyle(android.graphics.Paint.Style.FILL);paint.setColor(Color.rgb(27,36,56));canvas.drawRoundRect(cx-dp(5),top,cx+dp(5),bottom,dp(5),dp(5),paint);
   paint.setColor(Color.rgb(105,119,151));canvas.drawRoundRect(cx-dp(4),mid-dp(1),cx+dp(4),mid+dp(1),dp(1),dp(1),paint);
   
   float y=mid-(level/12f)*(bottom-top)/2f;
   paint.setColor(Color.rgb(42,125,255));if(level>=0)canvas.drawRoundRect(cx-dp(2),y,cx+dp(2),mid,dp(2),dp(2),paint);else canvas.drawRoundRect(cx-dp(2),mid,cx+dp(2),y,dp(2),dp(2),paint);
   paint.setShadowLayer(dp(10),0,0,Color.rgb(137,83,255));paint.setColor(Color.rgb(165,112,255));canvas.drawRoundRect(cx-dp(14),y-dp(17),cx+dp(14),y+dp(17),dp(8),dp(8),paint);paint.clearShadowLayer();
   paint.setColor(Color.WHITE);canvas.drawRoundRect(cx-dp(7),y-dp(2),cx+dp(7),y+dp(2),dp(2),dp(2),paint);
  }
  public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE){float top=dp(10),bottom=getHeight()-dp(10),mid=(top+bottom)/2f;float f=1f-2f*((e.getY()-top)/(bottom-top));int n=Math.max(-12,Math.min(12,Math.round(f*12)));if(n!=level){level=n;invalidate();if(listener!=null)listener.accept(level);}getParent().requestDisallowInterceptTouchEvent(true);return true;}if(e.getAction()==MotionEvent.ACTION_UP){performClick();return true;}return true;}
  public boolean performClick(){super.performClick();return true;}
 }
 void sendEffect(String action,String key1,int value1,String key2,int value2){Intent x=new Intent(this,PlaybackService.class);x.setAction(action);x.putExtra(key1,value1);x.putExtra(key2,value2);startService(x);}
 void sendBassEffect(boolean enabled,int strength){Intent x=new Intent(this,PlaybackService.class);x.setAction(PlaybackService.EQ_BASS);x.putExtra("enabled",enabled);x.putExtra("strength",strength);if(Build.VERSION.SDK_INT>=26)startForegroundService(x);else startService(x);}

 void settingsScreen(){content.addView(text("SETTINGS",22,WHITE,true));setting("Visualizer Style",getVisualizerName()+" · 9 FFT visualizer styles");setting("Appearance","Black theme · Electric blue / cyan");setting("Playback","Resume last song and position");setting("Music Library","Scan local music files");setting("Audio","Equalizer and Bass Boost");setting("App Info","BASS BEATS · Version 2.2.0");setting("Reset Settings","Restore default preferences");}
 void setting(String title,String desc){LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.setPadding(dp(14),dp(13),dp(14),dp(13));row.setBackground(round(PANEL,dp(12)));row.addView(text(title,14,WHITE,true));row.addView(text(desc,11,MUTED,false));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));content.addView(row,lp);row.setOnClickListener(v->{if(title.equals("Visualizer Style")){showVisualizerPicker();}else if(title.equals("Music Library")){loadSongs();showTab("MUSIC");Toast.makeText(this,"Library refreshed",Toast.LENGTH_SHORT).show();}else if(title.equals("Audio")){showTab("EQUALIZER");}else if(title.equals("App Info")){new AlertDialog.Builder(this).setTitle("BASS BEATS").setMessage("Version 2.2.0\nPremium AMOLED offline music player").setPositiveButton("OK",null).show();}else if(title.equals("Reset Settings")){new AlertDialog.Builder(this).setTitle("Reset settings?").setMessage("Restore the app's default screen settings?").setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{Toast.makeText(this,"Settings reset",Toast.LENGTH_SHORT).show();showTab("SETTINGS");}).show();}else if(title.equals("Appearance")){new AlertDialog.Builder(this).setTitle("Appearance").setItems(new String[]{"Black · Electric blue / cyan"},(d,w)->Toast.makeText(this,"Current theme selected",Toast.LENGTH_SHORT).show()).show();}else if(title.equals("Playback")){new AlertDialog.Builder(this).setTitle("Playback").setItems(new String[]{"Resume last song and position", "Audio focus (default)"},(d,w)->Toast.makeText(this,"Playback preference selected",Toast.LENGTH_SHORT).show()).show();}});}
 void loadSongs(){songs.clear();if(checkSelfPermission(Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)return;Uri collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;String[] projection={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.DATA};try(Cursor c=getContentResolver().query(collection,projection,MediaStore.Audio.Media.IS_MUSIC+" != 0",null,MediaStore.Audio.Media.TITLE+" COLLATE NOCASE ASC")){if(c!=null)while(c.moveToNext()){long id=c.getLong(0);String title=c.getString(1),artist=c.getString(2),album=c.getString(3),path=c.getString(4);if(title==null)title="Unknown title";if(artist==null||artist.equals("<unknown>"))artist="Unknown artist";if(album==null)album="Unknown album";songs.add(new Song(id,title,artist,album,path,Uri.withAppendedPath(collection,String.valueOf(id))));}}catch(Exception e){Toast.makeText(this,"Could not scan music",Toast.LENGTH_SHORT).show();}}
 @Override public void onBackPressed(){if(!"PLAYER".equals(tab)){showTab("PLAYER");return;}super.onBackPressed();}
 @Override protected void onResume(){super.onResume();handler.removeCallbacks(playerTicker);handler.post(playerTicker);}
 @Override protected void onPause(){handler.removeCallbacks(playerTicker);super.onPause();}
 @Override protected void onDestroy(){handler.removeCallbacks(playerTicker);if(receiverRegistered){try{unregisterReceiver(serviceReceiver);}catch(Exception ignored){}receiverRegistered=false;}super.onDestroy();}
 void playIndex(int i){playSongs(songs,i);return;}
 void legacyPlayIndexUnused(int i){if(i<0||i>=songs.size())return;current=i;if(songTitle!=null)songTitle.setText(songs.get(i).title);if(songArtist!=null)songArtist.setText(songs.get(i).artist);if(curTime!=null)curTime.setText("0:00");if(durTime!=null)durTime.setText("0:00");trackDuration=0;if(seek!=null){seek.setMax(1000);seek.setProgress(0);}if(playBtn!=null)playBtn.setText("Ⅱ");Intent x=new Intent(this,PlaybackService.class);x.setAction(PlaybackService.PLAY);x.putExtra("uri",songs.get(i).uri.toString());x.putExtra("title",songs.get(i).title);x.putExtra("artist",songs.get(i).artist);String[] queueUris=new String[songs.size()],queueTitles=new String[songs.size()],queueArtists=new String[songs.size()];for(int q=0;q<songs.size();q++){queueUris[q]=songs.get(q).uri.toString();queueTitles[q]=songs.get(q).title;queueArtists[q]=songs.get(q).artist;}int queueIndex=i;if(shuffleEnabled&&queueUris.length>1){java.util.ArrayList<Integer> order=new java.util.ArrayList<>();for(int q=0;q<queueUris.length;q++)order.add(q);java.util.Collections.shuffle(order);String[] u=queueUris.clone(),t=queueTitles.clone(),a=queueArtists.clone();for(int q=0;q<order.size();q++){int original=order.get(q);queueUris[q]=u[original];queueTitles[q]=t[original];queueArtists[q]=a[original];if(original==i)queueIndex=q;}}x.putExtra("repeat_mode",repeatMode);x.putExtra("queue_uris",queueUris);x.putExtra("queue_titles",queueTitles);x.putExtra("queue_artists",queueArtists);x.putExtra("queue_index",queueIndex);if(Build.VERSION.SDK_INT>=26)startForegroundService(x);else startService(x);showTab("PLAYER");}
 void sendTransport(String action){Intent x=new Intent(this,PlaybackService.class);x.setAction(action);if(Build.VERSION.SDK_INT>=26)startForegroundService(x);else startService(x);}
 void sendRepeatMode(){Intent x=new Intent(this,PlaybackService.class);x.setAction(PlaybackService.SET_REPEAT);x.putExtra("repeat_mode",repeatMode);if(Build.VERSION.SDK_INT>=26)startForegroundService(x);else startService(x);}
 void toggle(){Intent x=new Intent(this,PlaybackService.class);x.setAction(PlaybackService.TOGGLE);if(Build.VERSION.SDK_INT>=26)startForegroundService(x);else startService(x);}
 String formatTime(long milliseconds){long totalSeconds=Math.max(0,milliseconds)/1000;long minutes=totalSeconds/60;long seconds=totalSeconds%60;return minutes+":"+(seconds<10?"0":"")+seconds;}
 TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;} View gap(int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return v;} int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} android.graphics.drawable.GradientDrawable round(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;} Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setTextColor(CYAN);b.setOnClickListener(v->r.run());return b;}
 class WaveView extends View {
  private final android.graphics.Paint wavePaint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  private final android.graphics.Path wavePath=new android.graphics.Path();
  private final float[] levels=new float[96],raw=new float[96];
  WaveView(Context context){
   super(context);setBackgroundColor(Color.BLACK);
   wavePaint.setColor(Color.WHITE);wavePaint.setStyle(android.graphics.Paint.Style.STROKE);
   wavePaint.setStrokeWidth(dp(2f));wavePaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
   wavePaint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
  }
  void setWaveData(byte[] data){
   if(data==null||data.length<2)return;
   int count=levels.length;
   for(int i=0;i<count;i++){
    int from=(int)((long)i*data.length/count),to=Math.max(from+1,(int)((long)(i+1)*data.length/count));
    float sum=0f,energy=0f,peak=0f;int samples=0;
    for(int j=from;j<to&&j<data.length;j++){
     float sample=((data[j]&0xff)-128)/128f;
     sum+=sample;energy+=sample*sample;peak=Math.max(peak,Math.abs(sample));samples++;
    }
    float rms=samples==0?0f:(float)Math.sqrt(energy/samples);
    float mean=samples==0?0f:sum/samples;
    float sign=mean<0f?-1f:1f;
    float magnitude=Math.max(rms*.95f,peak*.42f);
    raw[i]=sign*Math.min(.72f,magnitude*2.15f);
   }
   for(int i=0;i<count;i++){
    float smooth=raw[Math.max(0,i-2)]*.08f+raw[Math.max(0,i-1)]*.22f+raw[i]*.40f+raw[Math.min(count-1,i+1)]*.22f+raw[Math.min(count-1,i+2)]*.08f;
    levels[i]=levels[i]*.48f+smooth*.52f;
   }
   postInvalidateOnAnimation();
  }
  @Override protected void onDraw(android.graphics.Canvas canvas){
   super.onDraw(canvas);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
   float mid=h*.5f,amp=dp(42),step=w/(levels.length-1);
   wavePath.reset();wavePath.moveTo(0,mid-levels[0]*amp);
   for(int i=0;i<levels.length-1;i++){
    float x0=i*step,x1=(i+1)*step,y0=mid-levels[i]*amp,y1=mid-levels[i+1]*amp;
    float prev=mid-levels[Math.max(0,i-1)]*amp,next=mid-levels[Math.min(levels.length-1,i+2)]*amp;
    wavePath.cubicTo(x0+step/3f,y0-(y1-prev)/6f,x1-step/3f,y1+(next-y0)/6f,x1,y1);
   }
   wavePaint.setAlpha(245);canvas.drawPath(wavePath,wavePaint);
  }
 }

 static final String[] VISUALIZER_NAMES={"Neon Spectrum","Dot Matrix","Flow Wave","Outline Spectrum","Radial Spectrum","Diamond Caps","Stacked Blocks","Mirror Wave","Soft Neon Bars"};
 int selectedVisualizer(){return getSharedPreferences("visualizer_settings",MODE_PRIVATE).getInt("style",0);}
 String getVisualizerName(){int n=selectedVisualizer();return VISUALIZER_NAMES[Math.max(0,Math.min(VISUALIZER_NAMES.length-1,n))];}
 void showVisualizerPicker(){
  final android.app.Dialog dialog=new android.app.Dialog(this);LinearLayout shell=new LinearLayout(this);shell.setOrientation(1);shell.setPadding(dp(18),dp(18),dp(18),dp(16));shell.setBackground(round(Color.rgb(7,10,17),dp(22)));
  TextView title=text("VISUALIZER STYLE",18,WHITE,true);shell.addView(title);TextView sub=text("Choose a live music visualizer",12,MUTED,false);LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);slp.topMargin=dp(5);slp.bottomMargin=dp(14);shell.addView(sub,slp);
  android.widget.GridLayout grid=new android.widget.GridLayout(this);grid.setColumnCount(2);grid.setRowCount(5);
  for(int i=0;i<VISUALIZER_NAMES.length;i++){final int index=i;LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(8),dp(13),dp(8),dp(13));tile.setBackground(round(selectedVisualizer()==i?Color.rgb(10,37,75):Color.rgb(18,23,34),dp(14)));TextView glyph=text(new String[]{"▥","⠿","∿","▯","◉","◆","▦","ϟ","▥"}[i],25,CYAN,true);glyph.setGravity(Gravity.CENTER);tile.addView(glyph);TextView name=text(VISUALIZER_NAMES[i],12,WHITE,true);name.setGravity(Gravity.CENTER);LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-1,-2);nlp.topMargin=dp(5);tile.addView(name,nlp);if(selectedVisualizer()==i){TextView active=text("SELECTED",9,CYAN,true);active.setGravity(Gravity.CENTER);tile.addView(active);}android.widget.GridLayout.LayoutParams gp=new android.widget.GridLayout.LayoutParams();gp.width=0;gp.height=dp(102);gp.columnSpec=android.widget.GridLayout.spec(i%2,1,1f);gp.rowSpec=android.widget.GridLayout.spec(i/2);gp.setMargins(dp(4),dp(4),dp(4),dp(4));grid.addView(tile,gp);tile.setOnClickListener(v->{getSharedPreferences("visualizer_settings",MODE_PRIVATE).edit().putInt("style",index).apply();dialog.dismiss();if(tab.equals("PLAYER"))showTab("PLAYER");Toast.makeText(this,VISUALIZER_NAMES[index]+" selected",Toast.LENGTH_SHORT).show();});}
  shell.addView(grid);TextView note=text("Selection applies instantly and stays saved",10,MUTED,false);note.setGravity(Gravity.CENTER);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(10);shell.addView(note,np);dialog.setContentView(shell);Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(getResources().getDisplayMetrics().widthPixels-dp(32),-2);}dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(32),-2);
 }
 class BassVisualizerView extends View {
  final android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  final int BAND_COUNT=48;
  final float[] bands=new float[BAND_COUNT];
  float bass=0;
  BassVisualizerView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setBackgroundColor(Color.BLACK);}
  void setFftData(byte[] fft,int samplingRate){
   if(fft==null||fft.length<16)return;
   // Android Visualizer supplies samplingRate in milliHertz and interleaved complex FFT bytes.
   float nyquist=Math.max(1000f,(samplingRate>0?samplingRate:44100000)/2000f);
   float binHz=Math.max(0.1f,nyquist/Math.max(1,fft.length/2f));
   int maxBin=Math.min((fft.length-1)/2,Math.max(1,(int)(nyquist/binHz)));
   float minHz=30f,maxHz=Math.min(16000f,nyquist*0.92f);
   if(maxHz<=minHz)maxHz=Math.max(minHz+1f,nyquist*0.9f);
   for(int band=0;band<BAND_COUNT;band++){
    float low=(float)(minHz*Math.pow(maxHz/minHz,band/(double)BAND_COUNT));
    float high=(float)(minHz*Math.pow(maxHz/minHz,(band+1)/(double)BAND_COUNT));
    int first=Math.max(1,(int)Math.ceil(low/binHz));
    int last=Math.min(maxBin,(int)Math.floor(high/binHz));
    float magnitude=0f;int samples=0;
    if(last>=first){
     for(int bin=first;bin<=last;bin++){
      int idx=bin*2;if(idx+1>=fft.length)break;
      float re=fft[idx],im=fft[idx+1];
      magnitude+=(float)Math.sqrt(re*re+im*im);samples++;
     }
    }else{
     int bin=Math.max(1,Math.min(maxBin,Math.round((float)Math.sqrt(low*high)/binHz)));
     int idx=bin*2;
     if(idx+1<fft.length){float re=fft[idx],im=fft[idx+1];magnitude=(float)Math.sqrt(re*re+im*im);samples=1;}
    }
    float average=samples==0?0f:magnitude/samples;
    float db=average<=0.5f?-72f:(float)(20.0*Math.log10(average/127.0));
    // A fixed dB window preserves real frequency balance without forcing quiet bands to move.
    float target=Math.max(0f,Math.min(1f,(db+58f)/42f));
    target=(float)Math.pow(target,0.82f);
    // Keep peaks comfortably inside the visualizer area instead of touching its edges.
    target*=0.68f;
    float previous=bands[band];
    float attack=0.58f,release=0.34f;
    bands[band]=target>previous?previous+(target-previous)*attack:previous*release;
    if(bands[band]<0.009f)bands[band]=0f;
   }
   bass=0f;for(int i=0;i<6;i++)bass+=bands[i];bass/=6f;
   postInvalidateOnAnimation();
  }
  float bandAt(float x){
   float pos=Math.max(0f,Math.min(1f,x))*(bands.length-1);
   int i=(int)pos,j=Math.min(bands.length-1,i+1);
   return bands[i]+(bands[j]-bands[i])*(pos-i);
  }
  void glow(int color,float radius){p.setShadowLayer(radius,0,0,color);p.setColor(color);}
  void rect(android.graphics.Canvas c,float l,float t,float r,float b,float rad,int color){if(r<=l||b<=t)return;p.setShader(null);p.setStyle(android.graphics.Paint.Style.FILL);p.setColor(color);p.setShadowLayer(dp(7),0,0,color);c.drawRoundRect(l,t,r,b,rad,rad,p);p.clearShadowLayer();}
  protected void onDraw(android.graphics.Canvas c){
   super.onDraw(c);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
   p.setShader(null);p.setStyle(android.graphics.Paint.Style.FILL);p.setColor(Color.BLACK);c.drawRect(0,0,w,h,p);
   float peak=0;for(float v:bands)peak=Math.max(peak,v);if(peak<.012f)return;
   int mode=Math.max(0,Math.min(8,selectedVisualizer()));float mid=h*.5f;
   int cyan=Color.rgb(35,205,255),blue=Color.rgb(45,105,255),purple=Color.rgb(175,75,255),pink=Color.rgb(255,65,190);
   int count=48;
   if(mode==0){
    // Premium warm spectrum: decorative flowing ribbons are drawn behind the bars.
    float base=h*.70f;
    android.graphics.Path flow=new android.graphics.Path();
    android.graphics.Path flowSoft=new android.graphics.Path();
    int samples=84;
    for(int i=0;i<samples;i++){
     float t=i/(float)(samples-1),x=t*w;
     float y=h*(.365f+.052f*(float)Math.sin(t*Math.PI*2.0+bass*1.8f)+.018f*(float)Math.sin(t*Math.PI*4.0-bass*2.4f));
     float y2=y+dp(9)+(float)Math.sin(t*Math.PI*3.0+bass)*dp(2);
     if(i==0){flow.moveTo(x,y);flowSoft.moveTo(x,y2);}else{flow.lineTo(x,y);flowSoft.lineTo(x,y2);}
    }
    int warm=Color.rgb(244,145,48),gold=Color.rgb(255,218,145),cool=Color.rgb(30,190,215);
    android.graphics.Shader ribbon=new android.graphics.LinearGradient(0,0,w,0,new int[]{warm,gold,cool},new float[]{0f,.49f,1f},android.graphics.Shader.TileMode.CLAMP);
    p.setShader(ribbon);p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeCap(android.graphics.Paint.Cap.ROUND);p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
    p.setStrokeWidth(dp(1.35f));p.setAlpha(92);p.setShadowLayer(dp(5),0,0,Color.rgb(70,155,170));c.drawPath(flow,p);p.clearShadowLayer();
    p.setStrokeWidth(dp(.8f));p.setAlpha(42);c.drawPath(flowSoft,p);p.setAlpha(255);p.setStyle(android.graphics.Paint.Style.FILL);p.setShader(null);
    // A few restrained particles follow the flowing ribbon.
    for(int i=0;i<13;i++){
     float t=(i+.35f)/13f,x=t*w;
     float y=h*(.365f+.052f*(float)Math.sin(t*Math.PI*2.0+bass*1.8f)+.018f*(float)Math.sin(t*Math.PI*4.0-bass*2.4f));
     p.setColor(i<5?warm:(i<8?gold:cool));p.setAlpha(105);c.drawCircle(x,y-dp(8+(i%3)*5),dp(1.1f),p);
    }
    p.setAlpha(255);
    float gap=dp(3),bw=(w-gap*(count+1))/count;
    android.graphics.Shader barGradient=new android.graphics.LinearGradient(0,0,w,0,new int[]{Color.rgb(239,126,34),warm,gold,Color.rgb(115,205,190),cool},new float[]{0f,.24f,.49f,.73f,1f},android.graphics.Shader.TileMode.CLAMP);
    android.graphics.Shader reflectionGradient=new android.graphics.LinearGradient(0,base,0,base+dp(40),new int[]{Color.argb(62,244,145,48),Color.argb(34,255,218,145),Color.argb(0,30,190,215)},null,android.graphics.Shader.TileMode.CLAMP);
    for(int i=0;i<count;i++){
     float v=bandAt(i/(float)(count-1)),bh=v*h*.56f;
     if(bh<dp(1))continue;
     float x=gap+i*(bw+gap),top=base-bh;
     p.setShader(barGradient);p.setStyle(android.graphics.Paint.Style.FILL);
     p.setShadowLayer(dp(5),0,0,i<count/3?warm:(i>count*2/3?cool:gold));
     c.drawRoundRect(x,top,x+bw,base,dp(3.5f),dp(3.5f),p);p.clearShadowLayer();
     p.setShader(reflectionGradient);p.setAlpha(255);
     float reflect=Math.min(dp(40),Math.max(dp(7),bh*.30f));
     c.drawRoundRect(x,base+dp(2),x+bw,base+dp(2)+reflect,dp(2),dp(2),p);
    }
    p.setShader(barGradient);p.setAlpha(90);p.setStrokeWidth(dp(1));c.drawLine(0,base,w,base,p);
    p.setAlpha(255);p.setShader(null);
   }else if(mode==8){
    float gap=dp(mode==0?3:2),bw=(w-gap*(count+1))/count;
    for(int i=0;i<count;i++){float v=bandAt(i/(float)(count-1));float bh=v*h*(mode==0?.82f:.76f);if(bh<dp(1))continue;float x=gap+i*(bw+gap);int top=Color.rgb(55,190,255);int bottom=i%5==0?purple:blue;p.setShader(new android.graphics.LinearGradient(0,mid-bh/2,0,mid+bh/2,top,bottom,android.graphics.Shader.TileMode.CLAMP));p.setStyle(android.graphics.Paint.Style.FILL);p.setShadowLayer(dp(8),0,0,i%5==0?purple:cyan);c.drawRoundRect(x,mid-bh/2,x+bw,mid+bh/2,dp(mode==8?5:2),dp(mode==8?5:2),p);p.clearShadowLayer();}p.setShader(null);
   
   }else if(mode==1){
    int cols=52,rows=7;float cw=w/cols,ch=h/(rows+1);float radius=Math.min(cw,ch)*.34f;
    for(int x=0;x<cols;x++){float v=bandAt(x/(float)(cols-1));int lit=Math.round(v*rows);for(int y=0;y<lit;y++){float xx=x*cw+cw/2,yy=mid+(y-(rows-1)/2f)*ch;int col=y>4?purple:(x%4==0?pink:cyan);p.setColor(col);p.setShadowLayer(dp(5),0,0,col);c.drawCircle(xx,yy,radius,p);p.clearShadowLayer();}}
   }else if(mode==2){
    android.graphics.Path path=new android.graphics.Path();int n=100;for(int i=0;i<n;i++){float x=i*w/(n-1),v=bandAt(i/(float)(n-1));float y=mid-v*h*.43f;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(dp(2.6f));p.setStrokeCap(android.graphics.Paint.Cap.ROUND);p.setColor(cyan);p.setShadowLayer(dp(9),0,0,blue);c.drawPath(path,p);p.clearShadowLayer();path.reset();for(int i=0;i<n;i++){float x=i*w/(n-1),v=bandAt(i/(float)(n-1));float y=mid+v*h*.34f;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}p.setColor(purple);p.setStrokeWidth(dp(1.5f));c.drawPath(path,p);p.setStyle(android.graphics.Paint.Style.FILL);
   }else if(mode==3){
    float gap=dp(3),bw=(w-gap*(count+1))/count;for(int i=0;i<count;i++){float v=bandAt(i/(float)(count-1)),bh=v*h*.78f;if(bh<dp(2))continue;float x=gap+i*(bw+gap),t=mid-bh/2,b=mid+bh/2;p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(i%6==0?purple:cyan);p.setShadowLayer(dp(5),0,0,cyan);c.drawRoundRect(x,t,x+bw,b,dp(2),dp(2),p);p.clearShadowLayer();p.setStyle(android.graphics.Paint.Style.FILL);}
   }else if(mode==4){
    float cx=w/2,cy=mid,r=Math.min(w,h)*.22f;for(int i=0;i<bands.length;i++){float v=bands[i];if(v<.012f)continue;double a=-Math.PI/2+2*Math.PI*i/bands.length;float outer=r+v*Math.min(w,h)*.32f;float x1=cx+(float)Math.cos(a)*r,y1=cy+(float)Math.sin(a)*r,x2=cx+(float)Math.cos(a)*outer,y2=cy+(float)Math.sin(a)*outer;p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(dp(4));p.setStrokeCap(android.graphics.Paint.Cap.ROUND);p.setColor(i%3==0?purple:cyan);p.setShadowLayer(dp(7),0,0,p.getColor());c.drawLine(x1,y1,x2,y2,p);p.clearShadowLayer();}p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(blue);c.drawCircle(cx,cy,r,p);p.setStyle(android.graphics.Paint.Style.FILL);
   }else if(mode==5){
    float gap=dp(4),bw=(w-gap*(count+1))/count;for(int i=0;i<count;i++){float v=bandAt(i/(float)(count-1)),bh=v*h*.68f;if(bh<dp(2))continue;float x=gap+i*(bw+gap),y=mid-bh/2;rect(c,x,y+dp(4),x+bw,y+bh,dp(2),i%5==0?purple:cyan);float d=Math.min(bw,dp(7));p.setColor(pink);p.setShadowLayer(dp(5),0,0,pink);android.graphics.Path diamond=new android.graphics.Path();diamond.moveTo(x+bw/2,y-d);diamond.lineTo(x+bw/2+d,y);diamond.lineTo(x+bw/2,y+d);diamond.lineTo(x+bw/2-d,y);diamond.close();c.drawPath(diamond,p);p.clearShadowLayer();}
   }else if(mode==6){
    int cols=48,rows=9;float cw=w/cols,ch=h/(rows+1);for(int x=0;x<cols;x++){int lit=Math.round(bandAt(x/(float)(cols-1))*rows);for(int y=0;y<lit;y++){float size=Math.min(cw,ch)*.72f,xx=x*cw+(cw-size)/2,yy=mid+(y-(rows-1)/2f)*ch;rect(c,xx,yy,xx+size,yy+size,dp(1),y>6?purple:cyan);}}
   }else{
    android.graphics.Path top=new android.graphics.Path(),bottom=new android.graphics.Path();int n=96;for(int i=0;i<n;i++){float x=i*w/(n-1),v=bandAt(i/(float)(n-1));float spread=v*h*.43f;float yt=mid-spread,yb=mid+spread;if(i==0){top.moveTo(x,yt);bottom.moveTo(x,yb);}else{top.lineTo(x,yt);bottom.lineTo(x,yb);}}p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(dp(2.4f));p.setColor(cyan);p.setShadowLayer(dp(8),0,0,blue);c.drawPath(top,p);p.setColor(purple);c.drawPath(bottom,p);p.clearShadowLayer();p.setStyle(android.graphics.Paint.Style.FILL);
   }
  }
 }
 class VisualizerView extends View {android.graphics.Paint p=new android.graphics.Paint(3);int[] vals=new int[36];VisualizerView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);for(int i=0;i<vals.length;i++)vals[i]=(int)(20+Math.random()*90);}protected void onDraw(android.graphics.Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();p.setColor(PANEL);c.drawRoundRect(0,0,w,h,dp(20),dp(20),p);float bw=w/(vals.length*1.8f),gap=(w-bw*vals.length)/(vals.length+1);for(int i=0;i<vals.length;i++){float bh=dp(vals[i]);p.setShader(new android.graphics.LinearGradient(0,h/2-bh/2,0,h/2+bh/2,CYAN,Color.rgb(0,75,220),android.graphics.Shader.TileMode.CLAMP));c.drawRoundRect(gap+i*(bw+gap),h/2-bh/2,gap+i*(bw+gap)+bw,h/2+bh/2,dp(4),dp(4),p);}p.setShader(null);}}
}