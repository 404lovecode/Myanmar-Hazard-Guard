package mm.hazardguard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.location.Geocoder;
import android.location.Address;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.content.res.Configuration;
import java.util.List;
import android.provider.Settings;
import android.view.WindowInsets;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputType;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    // Accessible design tokens, switched together to preserve contrast.
    private int NAVY, INK, MUTED, TEAL, MINT, BG, WHITE=0xffffffff,
        SURFACE, ORANGE, PALE, PURPLE, BLUE, CELL, BORDER, NAV,
        CHIPBLUE, CHIPPURPLE, SECONDARY_BUTTON, ACTIVE_NAV, BUTTON_TEXT;
    private boolean lightMode;
    private void applyPalette() {
        String mode=Prefs.theme(this);
        lightMode="light".equals(mode) || ("system".equals(mode) &&
            (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)!=Configuration.UI_MODE_NIGHT_YES);
        if(lightMode) {
            NAVY=0xff173e40; INK=0xff203a38; MUTED=0xff617b76; TEAL=0xff147d70;
            MINT=0xffe1f3e7; BG=0xfff8f7f1; SURFACE=0xffffffff; ORANGE=0xffb65c38;
            PALE=0xffffebe0; PURPLE=0xff7162ad; BLUE=0xff246d90;
            CELL=0xfff0f5ec; BORDER=0xffdae7df; NAV=0xffffffff;
            CHIPBLUE=0xffe5f1f4; CHIPPURPLE=0xfff1edfb; SECONDARY_BUTTON=0xffe2f5ed;
            ACTIVE_NAV=0xffe2f2e7;BUTTON_TEXT=WHITE;
        } else {
            NAVY=0xff14292b; INK=0xfff4f2eb; MUTED=0xffb3c6bc; TEAL=0xff8ce3c0;
            MINT=0xff22463e; BG=0xff101f20; SURFACE=0xff1b3030; ORANGE=0xffffc091;
            PALE=0xff46372f; PURPLE=0xffd1c0ff; BLUE=0xffa5d7e3;
            CELL=0xff263e3c; BORDER=0xff3c5450; NAV=0xff162a2a;
            CHIPBLUE=0xff254446; CHIPPURPLE=0xff3b3450; SECONDARY_BUTTON=0xff23473e;
            ACTIVE_NAV=0xff31594d; BUTTON_TEXT=0xff102b26;
        }
    }
    private LinearLayout root, pageBody;
    private ScrollView scroll;
    private HazardRepository.Snapshot snapshot;
    private int page=0;
    private boolean worldwideQuakes=false;
    private boolean headlinesLoading=false;
    private JSONObject headlines=new JSONObject();
    private final ExecutorService inlineTranslationWorker=Executors.newSingleThreadExecutor();
    private final HashSet<String> translationsRunning=new HashSet<>();
    // Only on-screen views are held; redrawing clears them to avoid leaking old cards.
    private final HashMap<String,ArrayList<TextView>> onScreenTranslations=new HashMap<>();

    private boolean busy=false, voiceReady=false;
    private LocationManager pendingLocationManager;
    private LocationListener pendingLocationListener;
    private TextToSpeech tts;
    private final Handler ui=new Handler(Looper.getMainLooper());

    @Override protected void attachBaseContext(Context newBase) { super.attachBaseContext(Locales.wrap(newBase)); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        applyPalette();
        setTheme(lightMode ? android.R.style.Theme_Material_Light_NoActionBar :
            android.R.style.Theme_Material_NoActionBar);
        getWindow().setStatusBarColor(lightMode?0xffedf5f9:NAVY);
        getWindow().setNavigationBarColor(lightMode?0xffffffff:NAVY);
        getWindow().getDecorView().setSystemUiVisibility(lightMode?
            (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR):0);
        snapshot=HazardRepository.snapshot(this);
        headlines=WorldNewsFeed.cached(this, newsLanguage(), newsTopic());
        Notifications.setup(this);
        MonitorJob.schedule(this);
        tts=new TextToSpeech(this,status -> { voiceReady=status==TextToSpeech.SUCCESS; if(voiceReady) configureSpeech(); });
        draw();
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
            && !Prefs.get(this).getBoolean("notification_prompted",false)) {
            Prefs.get(this).edit().putBoolean("notification_prompted",true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},101);
        }
        refresh();
    }
    @Override protected void onNewIntent(Intent intent) {super.onNewIntent(intent); setIntent(intent); page=0; snapshot=HazardRepository.snapshot(this); draw();}
    @Override protected void onDestroy() {
        if(pendingLocationManager!=null && pendingLocationListener!=null) {
            try {pendingLocationManager.removeUpdates(pendingLocationListener);}catch(Exception ignored){}
            pendingLocationListener=null;pendingLocationManager=null;
        }
        if(tts!=null){tts.stop();tts.shutdown();}
        inlineTranslationWorker.shutdownNow();
        onScreenTranslations.clear();
        super.onDestroy();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] perms,int[] results) {
        super.onRequestPermissionsResult(requestCode,perms,results);
        if(requestCode==101 && (results.length==0 || results[0]!=PackageManager.PERMISSION_GRANTED))
            Toast.makeText(this,R.string.permission_help,Toast.LENGTH_LONG).show();
        if(requestCode==202) {
            if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) readDeviceLocation();
            else Toast.makeText(this,R.string.location_denied,Toast.LENGTH_LONG).show();
        }
    }
    private String t(int id) {return getString(id);}
    private boolean online() {
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        if(cm==null)return false;
        try {
            NetworkCapabilities c=cm.getNetworkCapabilities(cm.getActiveNetwork());
            return c!=null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch(Exception e){return false;}
    }
    private int dp(float p) {return Math.round(getResources().getDisplayMetrics().density*p);}
    private boolean my(){return "my".equals(Prefs.language(this));}
    private String city(){return Prefs.location(this,my());}
    private String num(double n,int digits) {if(Double.isNaN(n) || Double.isInfinite(n))return "—";return String.format(Locale.US,"%."+digits+"f",n);}
    private String time(long millis) {if(millis<=0)return t(R.string.never);return new SimpleDateFormat("dd MMM, HH:mm",my()?new Locale("my"):Locale.ENGLISH).format(new Date(millis));}
    private String duration(long millis) {
        long mins=Math.max(0,(System.currentTimeMillis()-millis)/60_000);
        if(mins<60)return mins+" min";
        if(mins<1440)return (mins/60)+" h";
        return (mins/1440)+" d";
    }
    private GradientDrawable background(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private GradientDrawable gradient(int a,int b,int radius) {
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});g.setCornerRadius(dp(radius));return g;
    }
    private TextView text(String s,float size,int color,boolean heavy) {
        TextView v=new TextView(this);v.setText(s);v.setTextColor(color);v.setTextSize(size);
        v.setTypeface(Typeface.create(heavy?"sans-serif-medium":"sans-serif",heavy?Typeface.BOLD:Typeface.NORMAL));
        v.setIncludeFontPadding(true);v.setLineSpacing(dp(2),1.04f);return v;
    }
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void add(LinearLayout parent,View v){parent.addView(v);}
    private void space(LinearLayout parent,int dp){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(dp)));}
    private LinearLayout card() {
        LinearLayout c=vertical();c.setPadding(dp(18),dp(17),dp(18),dp(17));
        c.setBackground(background(SURFACE,24));c.setElevation(dp(2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);
        pageBody.addView(c,lp);return c;
    }
    private TextView section(String title) {
        TextView v=text(title,11,MUTED,true);v.setLetterSpacing(0.085f);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(14);lp.bottomMargin=dp(12);
        pageBody.addView(v,lp);return v;
    }
    private TextView chip(String title,int fg,int bg) {
        TextView t=text(title,11,fg,true);t.setPadding(dp(12),dp(7),dp(12),dp(7));t.setBackground(background(bg,30));return t;
    }
    private void paragraph(LinearLayout c,String s) {TextView v=text(s,13,MUTED,false);c.addView(v);}
    private LinearLayout metric(String label,String val,int color) {
        LinearLayout cell=vertical();cell.setPadding(dp(12),dp(11),dp(12),dp(11));
        cell.setBackground(background(CELL,16));
        add(cell,text(label,11,MUTED,false));space(cell,4);
        add(cell,text(val,21,color,true));return cell;
    }
    private void twoMetrics(LinearLayout parent,String labelA,String valA,String labelB,String valB) {
        LinearLayout r=horizontal();r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout a=metric(labelA,valA,INK),b=metric(labelB,valB,INK);
        LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,-2,1f);p1.rightMargin=dp(8);
        r.addView(a,p1);r.addView(b,new LinearLayout.LayoutParams(0,-2,1f));parent.addView(r);
    }
    private void rule(LinearLayout container){View v=new View(this);v.setBackgroundColor(BORDER);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.topMargin=dp(12);p.bottomMargin=dp(12);container.addView(v,p);}
    private TextView button(String title,int color,int txt) {
        TextView b=text(title,14,txt,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(16),dp(13),dp(16),dp(13));
        b.setBackground(background(color,14));b.setClickable(true);return b;
    }
    private void draw() {
        root=vertical();root.setBackgroundColor(BG);
        if(Build.VERSION.SDK_INT>=35) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view,insets)->{
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(0,bars.top,0,bars.bottom);
                return insets;
            });
        }
        setContentView(root);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout column=vertical();scroll.addView(column);
        buildHeader(column);
        pageBody=vertical();pageBody.setPadding(dp(18),dp(12),dp(18),dp(34));column.addView(pageBody);
        if(page==0) showHome();else if(page==1) showQuakes();else if(page==2) showClimate();
        else if(page==3) showEnvironment();else if(page==4) showWorld();
        else if(page==6) showGlobalHeadlines(); else showSettings();
        buildEmergencyShortcut(root);
        buildNav(root);
    }
    private void buildHeader(LinearLayout column) {
        LinearLayout h=vertical();h.setPadding(dp(22),dp(24),dp(22),dp(24));
        h.setBackground(gradient(0xff204c49,0xff35675a,0));
        LinearLayout small=horizontal();
        TextView logo=text("✦",25,0xffffe2ac,true);
        small.addView(logo);TextView name=text("  HAZARDGUARD WORLD",14,WHITE,true);name.setLetterSpacing(0.03f);
        name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        small.addView(name,new LinearLayout.LayoutParams(0,-2,1f));
        TextView language=chip(Prefs.language(this).toUpperCase(Locale.US)+"  ⌄",WHITE,0x553be3bb);
        language.setOnClickListener(v->chooseLanguage());small.addView(language);
        h.addView(small);space(h,20);
        add(h,text(page==0?t(R.string.hero_title):
            page==1?t(R.string.quakes):page==2?t(R.string.enso_title):page==3?t(R.string.environment):page==4?t(R.string.world):
            page==6?newsText("Global news"):t(R.string.settings),27,WHITE,true));
        space(h,7);
        add(h,text(page==0?t(R.string.hero_subtitle):t(R.string.global_monitoring),13,0xffc8f4ef,false));
        space(h,18);
        LinearLayout area=horizontal();
        LinearLayout left=vertical();
        add(left,text(t(R.string.selected_city).toUpperCase(Locale.ROOT),10,0xffa6cecf,true));
        space(left,2);add(left,text("⌖  "+city(),17,WHITE,true));
        area.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        TextView change=button("⌄",0x3357d9b8,WHITE);
        change.setOnClickListener(v -> chooseCity());area.addView(change);h.addView(area);
        column.addView(h);
    }
    private void buildNav(LinearLayout parent) {
        LinearLayout bar=horizontal();bar.setBackgroundColor(NAV);bar.setPadding(dp(5),dp(9),dp(5),dp(8));bar.setElevation(dp(12));
        final int[] labels={R.string.home,R.string.quakes,R.string.climate,R.string.environment,R.string.world,R.string.settings};
        final String[] icons={"⌂","≋","☼","◉","◎","⚙"};
        for(int i=0;i<labels.length;i++) {
            final int target=i;boolean active=page==i;
            LinearLayout b=vertical();b.setGravity(Gravity.CENTER);b.setPadding(dp(1),dp(4),dp(1),dp(3));
            if(active)b.setBackground(background(ACTIVE_NAV,13));
            TextView ico=text(icons[i],21,active?TEAL:MUTED,true);ico.setGravity(Gravity.CENTER);b.addView(ico);
            TextView lab=text(t(labels[i]),9,active?TEAL:MUTED,active);lab.setSingleLine(true);lab.setEllipsize(android.text.TextUtils.TruncateAt.END);lab.setGravity(Gravity.CENTER);
            b.addView(lab);b.setContentDescription(t(labels[i]));
            b.setOnClickListener(v->{page=target;draw();scroll.scrollTo(0,0);});
            bar.addView(b,new LinearLayout.LayoutParams(0,-2,1f));
        }
        parent.addView(bar);
    }
    private void refreshedLabel(LinearLayout parent) {
        LinearLayout row=horizontal();
        String checked=snapshot.checkedAt>0?time(snapshot.checkedAt):t(R.string.not_updated);
        add(row,text(t(R.string.last_checked).replace("%1$s",checked),11,MUTED,false));
        space(parent,8);parent.addView(row);
    }
    private void refresh() {
        if(busy)return;
        busy=true;draw();
        new Thread(()->{
            HazardRepository.Snapshot updated;
            try {updated=HazardRepository.update(getApplicationContext(),true,true);}
            catch(Exception ignored) {updated=HazardRepository.snapshot(getApplicationContext());updated.failed=4;}
            final HazardRepository.Snapshot result=updated;
            ui.post(()->{
                if(isFinishing() || isDestroyed())return;
                snapshot=result;busy=false;draw();
                // A single temporarily unavailable provider does not mean the phone is offline.
                if(result.succeeded==0 && result.failed>0)
                    Toast.makeText(this,online()?R.string.feeds_unavailable:R.string.no_network,Toast.LENGTH_LONG).show();
                sayForegroundAlert();
            });
        },"HazardGuard-refresh").start();
    }
    private void refreshButton() {
        TextView btn=button(busy?t(R.string.refreshing):"↻  "+t(R.string.refresh),TEAL,BUTTON_TEXT);
        btn.setEnabled(!busy);btn.setOnClickListener(v->refresh());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);pageBody.addView(btn,lp);
        refreshedLabel(pageBody);
        if(snapshot.checkedAt>0 && snapshot.failed>0) {
            space(pageBody,8);
            add(pageBody,text(t(snapshot.succeeded>0?R.string.partial_feeds:R.string.feeds_unavailable),12,ORANGE,false));
        }
    }
    private void showHome() {
        LinearLayout overview=vertical();overview.setPadding(dp(18),dp(18),dp(18),dp(18));
        overview.setBackground(gradient(0xff365b51,0xff24443d,24));
        LinearLayout status=horizontal();
        add(status,text("✦  "+t(R.string.global_monitoring),12,0xff75fae1,true));
        status.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        add(status,chip(online()?t(R.string.online_status):t(R.string.offline_status),
            0xffffffff,online()?0xff1c8876:0xff90564b));
        overview.addView(status);space(overview,8);
        add(overview,text(t(R.string.global_intro),15,WHITE,true));space(overview,7);
        add(overview,text(t(R.string.safety_short),12,0xffbfd9df,false));
        LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,-2);op.bottomMargin=dp(15);pageBody.addView(overview,op);
        refreshButton();
        section(t(R.string.quick_view));
        LinearLayout quake=card();
        LinearLayout top=horizontal();
        add(top,chip("≋  USGS",ORANGE,PALE));
        top.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        add(top,chip(t(R.string.quakes),TEAL,MINT));quake.addView(top);space(quake,14);
        JSONObject latest=firstQuake();
        if(latest!=null) {
            JSONObject p=latest.optJSONObject("properties");
            add(quake,text("M " + num(p.optDouble("mag",Double.NaN),1),31,INK,true));
            add(quake,text(p.optString("place","—"),14,MUTED,false));
            space(quake,5);add(quake,text(t(R.string.quake_age).replace("%1$s",duration(p.optLong("time",0))),12,ORANGE,true));
        }else paragraph(quake,snapshot.quakeAt==0?t(R.string.no_quakes):t(R.string.earthquakes_empty));
        rule(quake);paragraph(quake,t(R.string.earthquake_note));
        dataAge(quake,snapshot.quakeAt);
        quake.setOnClickListener(v->{page=1;draw();});
        LinearLayout c=card();
        add(c,chip("☼  NOAA CPC",ORANGE,PALE));space(c,12);
        add(c,text(t(R.string.enso_title),17,INK,true));space(c,6);
        if(snapshot.climate.has("value")) {
            add(c,text(num(snapshot.climate.optDouble("value",Double.NaN),1)+"°C  •  "+phaseName(snapshot.climate.optString("phase")),22,ORANGE,true));
            add(c,text(snapshot.climate.optString("season")+" "+snapshot.climate.optInt("year"),12,MUTED,false));
        } else paragraph(c,t(R.string.roni_unknown));
        rule(c);paragraph(c,t(R.string.roni_disclaimer));
        dataAge(c,snapshot.climateAt);
        c.setOnClickListener(v->{page=2;draw();});
        LinearLayout environment=card();
        add(environment,chip("◉  OPEN-METEO",BLUE,CHIPBLUE));space(environment,12);
        JSONObject weather=snapshot.weather.optJSONObject("current");
        JSONObject air=snapshot.air.optJSONObject("current");
        twoMetrics(environment,t(R.string.temperature),weather==null?"—":num(weather.optDouble("temperature_2m",Double.NaN),1)+"°C",
            t(R.string.aqi),air==null?"—":num(air.optDouble("us_aqi",Double.NaN),0));
        space(environment,10);paragraph(environment,t(R.string.weather_note));
        dataAge(environment,Math.max(snapshot.weatherAt,snapshot.airAt));
        environment.setOnClickListener(v->{page=3;draw();});
        LinearLayout world=card();
        add(world,chip("◎  NASA EONET",PURPLE,CHIPPURPLE));space(world,12);
        JSONArray worldEvents=snapshot.events.optJSONArray("events");
        add(world,text(t(R.string.world_events),19,INK,true));space(world,6);
        add(world,text(worldEvents==null?t(R.string.no_readings):worldEvents.length()+" "+t(R.string.tracked_events),27,TEAL,true));
        space(world,7);paragraph(world,t(R.string.eonet_note));dataAge(world,snapshot.eventsAt);
        world.setOnClickListener(v->{page=4;draw();});
        newsEntryCard(true);
        section(t(R.string.news_heading));showNews(3);
        section(t(R.string.latest_alerts));showHistory(3);
        section(t(R.string.data_sources));sources();
    }
    private JSONObject firstQuake() {
        JSONArray arr=snapshot.quake.optJSONArray("features");
        return arr!=null && arr.length()>0?arr.optJSONObject(0):null;
    }
    private void dataAge(LinearLayout parent,long when) {
        if(when<=0)return;
        space(parent,8);
        String label=t(R.string.source_date).replace("%1$s",time(when));
        if(System.currentTimeMillis()-when>90*60_000L)label="⚠  "+t(R.string.stale_data)+" • "+label;
        add(parent,text(label,11,System.currentTimeMillis()-when>90*60_000L?ORANGE:MUTED,false));
    }
    private void showQuakes() {
        refreshButton();section(t(R.string.earthquake_feed));
        LinearLayout selector=horizontal();
        TextView local=button("⌖ "+t(R.string.nearby),worldwideQuakes?SURFACE:MINT,worldwideQuakes?MUTED:TEAL);
        TextView global=button("◎ "+t(R.string.worldwide),worldwideQuakes?MINT:SURFACE,worldwideQuakes?TEAL:MUTED);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.rightMargin=dp(8);
        selector.addView(local,left);selector.addView(global,new LinearLayout.LayoutParams(0,-2,1));
        pageBody.addView(selector);space(pageBody,12);
        local.setOnClickListener(v->{worldwideQuakes=false;draw();});
        global.setOnClickListener(v->{worldwideQuakes=true;draw();});
        LinearLayout notice=card();
        add(notice,chip("USGS  •  48H",TEAL,MINT));space(notice,10);
        paragraph(notice,worldwideQuakes?t(R.string.world_quake_note):t(R.string.earthquake_alert_info));space(notice,6);
        paragraph(notice,t(R.string.earthquake_note));space(notice,6);
        paragraph(notice,t(R.string.no_results_note));
        JSONArray events=(worldwideQuakes?snapshot.globalQuake:snapshot.quake).optJSONArray("features");
        if(events==null) {LinearLayout empty=card();paragraph(empty,t(R.string.no_readings));return;}
        if(events.length()==0){LinearLayout empty=card();paragraph(empty,t(R.string.earthquakes_empty));}
        for(int i=0;i<Math.min(events.length(),45);i++) {
            JSONObject ev=events.optJSONObject(i);if(ev==null)continue;
            JSONObject props=ev.optJSONObject("properties");if(props==null)continue;
            JSONObject geometry=ev.optJSONObject("geometry");
            JSONArray coordinates=geometry==null?null:geometry.optJSONArray("coordinates");
            double mag=props.optDouble("mag",Double.NaN);
            LinearLayout item=card();LinearLayout r=horizontal();
            LinearLayout magnitude=vertical();magnitude.setGravity(Gravity.CENTER);
            magnitude.setBackground(background(mag>=5.0?PALE:MINT,14));magnitude.setPadding(dp(15),dp(10),dp(15),dp(10));
            add(magnitude,text("M "+num(mag,1),19,mag>=5?ORANGE:TEAL,true));
            r.addView(magnitude);
            LinearLayout details=vertical();details.setPadding(dp(12),0,0,0);
            add(details,text(props.optString("place","Earthquake"),14,INK,true));
            space(details,4);add(details,text(t(R.string.quake_age).replace("%1$s",duration(props.optLong("time",0))),12,MUTED,false));
            if(coordinates!=null && coordinates.length()>2)
                add(details,text(t(R.string.quake_depth).replace("%1$s",num(coordinates.optDouble(2,Double.NaN),1)),11,MUTED,false));
            r.addView(details,new LinearLayout.LayoutParams(0,-2,1));item.addView(r);
            final String target=props.optString("url","");
            if(target.startsWith("https://earthquake.usgs.gov/"))item.setOnClickListener(v->open(target));
        }
        section(t(R.string.learn_more));LinearLayout tip=card();paragraph(tip,t(R.string.quake_tips));
    }
    private String phaseName(String phase) {
        if("warm".equals(phase))return t(R.string.phase_warm);
        if("cool".equals(phase))return t(R.string.phase_cool);
        if("neutral".equals(phase))return t(R.string.phase_neutral);
        return t(R.string.phase_unknown);
    }
    private void showClimate() {
        refreshButton();section(t(R.string.climate_index));
        LinearLayout hero=card();
        add(hero,chip("NOAA  •  RONI",PURPLE,CHIPPURPLE));space(hero,14);
        add(hero,text(t(R.string.roni_index),14,MUTED,false));space(hero,4);
        if(snapshot.climate.has("value")) {
            double value=snapshot.climate.optDouble("value",Double.NaN);
            int col=value>=0.5?ORANGE:value<=-0.5?BLUE:TEAL;
            add(hero,text(num(value,1)+"°C",44,col,true));space(hero,5);
            add(hero,text(phaseName(snapshot.climate.optString("phase")),20,INK,true));
            space(hero,5);
            add(hero,text(snapshot.climate.optString("season")+"  "+snapshot.climate.optInt("year"),12,MUTED,false));
            space(hero,9);
            add(hero,chip(t(R.string.enso_pattern).replace("%1$s",snapshot.climate.optBoolean("sustained")?t(R.string.yes):t(R.string.no)),col,CELL));
        } else paragraph(hero,t(R.string.roni_unknown));
        rule(hero);paragraph(hero,t(R.string.roni_disclaimer));
        section(t(R.string.global_prepare));
        String[] tips={t(R.string.enso_tip_1),t(R.string.enso_tip_2),t(R.string.enso_tip_3)};
        for(int i=0;i<tips.length;i++){
            LinearLayout item=card();LinearLayout r=horizontal();
            add(r,chip("0"+(i+1),TEAL,MINT));
            TextView message=text(tips[i],14,INK,false);message.setPadding(dp(12),0,0,0);
            r.addView(message,new LinearLayout.LayoutParams(0,-2,1));item.addView(r);
        }
        TextView source=button("NOAA CPC  ↗",NAVY,WHITE);source.setOnClickListener(v->open(HazardRepository.RONI));
        pageBody.addView(source);
    }
    private int aqiColor(int aqi) {if(aqi<=50)return TEAL;if(aqi<=100)return 0xffc9a43b;if(aqi<=150)return ORANGE;if(aqi<=200)return 0xffdd6260;return PURPLE;}
    private String aqiName(int aqi) {
        return t(aqi<=50?R.string.aqi_good:aqi<=100?R.string.aqi_moderate:aqi<=150?R.string.aqi_sensitive:
          aqi<=200?R.string.aqi_unhealthy:aqi<=300?R.string.aqi_very:R.string.aqi_hazardous);
    }
    private void showEnvironment() {
        refreshButton();section(t(R.string.air_quality));
        JSONObject currentAir=snapshot.air.optJSONObject("current");
        LinearLayout air=card();
        add(air,chip("◉  "+city(),TEAL,MINT));space(air,10);
        if(currentAir!=null && currentAir.has("us_aqi") && !currentAir.isNull("us_aqi")) {
            int aqi=currentAir.optInt("us_aqi",0);
            LinearLayout r=horizontal();
            Ring ring=new Ring(this,aqi,aqiColor(aqi));r.addView(ring,new LinearLayout.LayoutParams(dp(125),dp(125)));
            LinearLayout desc=vertical();desc.setPadding(dp(13),0,0,0);
            add(desc,text(t(R.string.aqi),13,MUTED,false));space(desc,8);
            add(desc,text(aqiName(aqi),17,aqiColor(aqi),true));
            r.addView(desc,new LinearLayout.LayoutParams(0,-2,1));air.addView(r);
        } else paragraph(air,t(R.string.no_readings));
        space(air,10);
        twoMetrics(air,t(R.string.pm25),currentAir==null?"—":num(currentAir.optDouble("pm2_5",Double.NaN),1)+" µg/m³",
            t(R.string.pm10),currentAir==null?"—":num(currentAir.optDouble("pm10",Double.NaN),1)+" µg/m³");
        rule(air);paragraph(air,t(R.string.aqi_note));dataAge(air,snapshot.airAt);
        section(t(R.string.weather));
        LinearLayout weather=card();
        JSONObject cur=snapshot.weather.optJSONObject("current");
        JSONObject hourly=snapshot.weather.optJSONObject("hourly");
        if(cur==null)paragraph(weather,t(R.string.no_readings));
        else {
            add(weather,text(num(cur.optDouble("temperature_2m",Double.NaN),1)+"°",38,INK,true));
            space(weather,12);
            twoMetrics(weather,t(R.string.humidity),num(cur.optDouble("relative_humidity_2m",Double.NaN),0)+"%",
                t(R.string.wind),num(cur.optDouble("wind_speed_10m",Double.NaN),0)+" km/h");
            space(weather,9);
            twoMetrics(weather,t(R.string.precipitation),num(cur.optDouble("precipitation",Double.NaN),1)+" mm",
                t(R.string.max_rain),num(HazardRepository.maxFirst(hourly==null?null:hourly.optJSONArray("precipitation"),6),1)+" mm/h");
        }
        rule(weather);paragraph(weather,t(R.string.weather_note));dataAge(weather,snapshot.weatherAt);
        section(t(R.string.data_sources));sources();
    }
    private void sources(){
        LinearLayout c=card();
        String[] items={t(R.string.source_usgs),t(R.string.source_noaa),t(R.string.source_meteo),t(R.string.source_nasa),t(R.string.source_gdacs)};
        String[] urls={"https://earthquake.usgs.gov/earthquakes/",HazardRepository.RONI,"https://open-meteo.com/","https://eonet.gsfc.nasa.gov/","https://www.gdacs.org/"};
        for(int i=0;i<items.length;i++) {
            final String url=urls[i];
            TextView label=text("↗  "+items[i],13,INK,true);label.setPadding(0,dp(7),0,dp(7));label.setOnClickListener(v->open(url));add(c,label);
        }
        rule(c);paragraph(c,t(R.string.source_refresh));
    }
    private void showHistory(int max){
        JSONArray history;try{history=new JSONArray(Prefs.get(this).getString("history","[]"));}
        catch(Exception e){history=new JSONArray();}
        if(history.length()==0){LinearLayout c=card();paragraph(c,t(R.string.alerts_empty));return;}
        for(int i=0;i<Math.min(max,history.length());i++) {
            JSONObject item=history.optJSONObject(i);if(item==null)continue;
            LinearLayout c=card();add(c,text(item.optString("title"),14,INK,true));space(c,4);
            paragraph(c,item.optString("body"));space(c,4);
            add(c,text(time(item.optLong("time",0)),11,MUTED,false));
        }
    }
    private void showWorld() {
        refreshButton();
        newsEntryCard(true);
        section(t(R.string.news_heading));showNews(20);
        section(t(R.string.global_coverage));
        LinearLayout intro=card();
        add(intro,chip("◎  NASA EONET",PURPLE,CHIPPURPLE));space(intro,12);
        add(intro,text(t(R.string.world_events),21,INK,true));space(intro,8);
        paragraph(intro,t(R.string.eonet_note));
        dataAge(intro,snapshot.eventsAt);
        JSONArray events=snapshot.events.optJSONArray("events");
        if(events==null){LinearLayout missing=card();paragraph(missing,t(R.string.no_readings));events=new JSONArray();}
        int fires=0,storms=0,volcanoes=0,floods=0;
        for(int i=0;i<events.length();i++) {
            JSONObject e=events.optJSONObject(i);if(e==null)continue;
            String cat=eventCategory(e).toLowerCase(Locale.US);
            if(cat.contains("wildfire"))fires++;
            if(cat.contains("storm")||cat.contains("cyclone"))storms++;
            if(cat.contains("volcano"))volcanoes++;
            if(cat.contains("flood"))floods++;
        }
        LinearLayout categories=card();
        twoMetrics(categories,t(R.string.wildfires),String.valueOf(fires),t(R.string.storms),String.valueOf(storms));
        space(categories,9);
        twoMetrics(categories,t(R.string.volcanoes),String.valueOf(volcanoes),t(R.string.floods),String.valueOf(floods));
        section(t(R.string.tracked_events)+"  ("+events.length()+")");
        if(events.length()==0){LinearLayout missing=card();paragraph(missing,t(R.string.world_no_events));}
        for(int i=0;i<Math.min(45,events.length());i++) {
            JSONObject ev=events.optJSONObject(i);if(ev==null)continue;
            String category=eventCategory(ev);
            LinearLayout item=card();
            LinearLayout head=horizontal();
            add(head,chip(category,PURPLE,CHIPPURPLE));
            item.addView(head);space(item,9);
            add(item,text(ev.optString("title",t(R.string.world_events)),15,INK,true));space(item,7);
            JSONArray geo=ev.optJSONArray("geometry");
            if(geo!=null && geo.length()>0) {
                JSONObject last=geo.optJSONObject(geo.length()-1);
                if(last!=null) {
                    String date=last.optString("date","");
                    if(date.length()>=10)add(item,text(t(R.string.reported_date)+"  "+date.substring(0,10),11,MUTED,false));
                }
            }
            space(item,9);
            add(item,text(t(R.string.event_tap_details)+"  ›",12,TEAL,true));
            item.setClickable(true);
            item.setContentDescription(ev.optString("title",t(R.string.world_events))+". "+t(R.string.event_tap_details));
            item.setOnClickListener(v->showEventDetails(ev));
        }
        section(t(R.string.safety_title));LinearLayout disclaimer=card();paragraph(disclaimer,t(R.string.global_safety_note));
        TextView source=button("NASA EONET   ↗",MINT,TEAL);
        source.setOnClickListener(v->open("https://eonet.gsfc.nasa.gov/"));pageBody.addView(source);
    }
    /** Display human-readable EONET data inside the app, never its raw API JSON. */
    private void showEventDetails(JSONObject event) {
        if(event==null)return;
        ScrollView scroller=new ScrollView(this);
        scroller.setFillViewport(false);
        LinearLayout content=vertical();
        content.setBackground(background(BG,20));
        content.setPadding(dp(20),dp(22),dp(20),dp(22));
        scroller.addView(content);

        add(content,chip("◎  NASA EONET  •  "+eventCategory(event),PURPLE,CHIPPURPLE));
        space(content,13);
        add(content,text(event.optString("title",t(R.string.world_events)),23,INK,true));
        space(content,14);
        boolean closed=!event.isNull("closed") && !event.optString("closed","").isEmpty();
        add(content,chip(closed?t(R.string.event_closed):t(R.string.event_active),
            closed?MUTED:TEAL,closed?CELL:MINT));

        JSONArray geometries=event.optJSONArray("geometry");
        if(geometries!=null && geometries.length()>0) {
            JSONObject first=geometries.optJSONObject(0);
            JSONObject last=geometries.optJSONObject(geometries.length()-1);
            if(first!=null)eventField(content,t(R.string.event_first_observed),eventDate(first.optString("date","")));
            if(last!=null)eventField(content,t(R.string.event_last_observed),eventDate(last.optString("date","")));
            if(last!=null){
                double[] position=eventPoint(last);
                if(position!=null) {
                    String coordinates=num(position[1],3)+"°, "+num(position[0],3)+"°";
                    eventField(content,t(R.string.event_coordinates),coordinates);
                    TextView map=button("⌖  "+t(R.string.event_view_map)+"  ↗",SECONDARY_BUTTON,TEAL);
                    content.addView(map);space(content,10);
                    final String mapUrl="https://www.google.com/maps/search/?api=1&query="+
                        String.format(Locale.US,"%.6f,%.6f",position[1],position[0]);
                    map.setOnClickListener(v->open(mapUrl));
                } else eventField(content,t(R.string.event_coordinates),t(R.string.event_unlocated));
            }
        }
        space(content,8);
        rule(content);
        add(content,text(t(R.string.event_sources),16,INK,true));
        space(content,7);
        paragraph(content,t(R.string.event_source_explain));
        space(content,10);
        JSONArray sources=event.optJSONArray("sources");
        int shown=0;
        if(sources!=null) for(int i=0;i<sources.length() && shown<5;i++) {
            JSONObject source=sources.optJSONObject(i);if(source==null)continue;
            String url=source.optString("url","");
            if(!safeSourceUrl(url))continue;
            String name=source.optString("id","").trim();
            if(name.isEmpty())name=t(R.string.open_source);
            TextView link=button("↗  "+name,SECONDARY_BUTTON,TEAL);
            content.addView(link);space(content,8);
            link.setContentDescription(t(R.string.open_source)+": "+name);
            link.setOnClickListener(v->open(url));
            shown++;
        }
        if(shown==0)paragraph(content,t(R.string.event_no_sources));
        space(content,9);
        paragraph(content,t(R.string.event_disclaimer));
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setView(scroller)
            .setPositiveButton(t(R.string.event_close),(d,which)->d.dismiss())
            .create();
        dialog.setOnShowListener(d->{
            if(dialog.getWindow()!=null) {
                dialog.getWindow().setBackgroundDrawable(background(BG,20));
                dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*0.94f),-2);
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(TEAL);
        });
        dialog.show();
    }
    private void eventField(LinearLayout content,String label,String value) {
        if(value==null || value.isEmpty())return;
        space(content,13);
        add(content,text(label.toUpperCase(Locale.ROOT),11,MUTED,true));
        space(content,3);
        add(content,text(value,14,INK,false));
    }
    private String eventDate(String iso) {
        if(iso==null || iso.length()<10)return "";
        return iso.length()>=16?iso.substring(0,10)+" "+iso.substring(11,16)+" UTC":iso.substring(0,10);
    }
    /** EONET Point coordinates use [longitude,latitude]; polygons have no single event point. */
    private double[] eventPoint(JSONObject geometry) {
        if(!"Point".equalsIgnoreCase(geometry.optString("type","")))return null;
        JSONArray data=geometry.optJSONArray("coordinates");
        if(data==null || data.length()<2)return null;
        double lon=data.optDouble(0,Double.NaN),lat=data.optDouble(1,Double.NaN);
        if(Double.isNaN(lon)||Double.isNaN(lat)||Math.abs(lon)>180||Math.abs(lat)>90)return null;
        return new double[]{lon,lat};
    }
    private boolean safeSourceUrl(String url) {
        try {
            Uri parsed=Uri.parse(url);
            if(!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost()==null)return false;
            // API JSON is developer data; link to reports or official pages instead.
            String path=parsed.getPath();
            return !parsed.getHost().equalsIgnoreCase("eonet.gsfc.nasa.gov") ||
                path==null || !path.startsWith("/api/");
        }catch(Exception ex){return false;}
    }

    /** General world news is intentionally separate from verified GDACS disaster notices. */
    private String newsLanguage() {
        String language=Prefs.get(this).getString("world_news_language", "");
        if(language.isEmpty()) language=Prefs.language(this);
        return WorldNewsFeed.LANGS[WorldNewsFeed.langIndex(language)];
    }
    private String newsTopic() {
        String topic=Prefs.get(this).getString("world_news_topic","world");
        return WorldNewsFeed.TOPICS[WorldNewsFeed.topicIndex(topic)];
    }

    // Translation is a separate preference: readers can follow any news edition
    // and translate a headline into the language that they understand.
    private String newsTranslationLanguage() {
        String selected=Prefs.get(this).getString("world_news_translate_language","");
        // Default to the app's chosen language, not the publisher edition.
        if(selected.isEmpty() || "mix".equals(selected))selected=Prefs.language(this);
        int index=WorldNewsFeed.langIndex(selected);
        if(index==0)index=1;
        return WorldNewsFeed.LANGS[index];
    }
    private void selectTranslationLanguage() {
        String[] options=WorldNewsFeed.NAMES.clone();
        options[0]=translateCaption("Follow selected news/app language");
        String selected=Prefs.get(this).getString("world_news_translate_language","");
        int checked=selected.isEmpty()?0:WorldNewsFeed.langIndex(selected);
        new AlertDialog.Builder(this)
            .setTitle(translateCaption("Translate articles to"))
            .setSingleChoiceItems(options,checked,(dialog,which)->{
                Prefs.get(this).edit().putString("world_news_translate_language",
                    which==0?"":WorldNewsFeed.LANGS[which]).apply();
                dialog.dismiss();
                draw();
            }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private String translateCaption(String en) {
        String lang=Prefs.language(this);
        if("my".equals(lang)) {
            if("Translate articles to".equals(en))return "သတင်းများ ဘာသာပြန်မည့် ဘာသာစကား";
            if("Translate headline".equals(en))return "ခေါင်းစဉ် ဘာသာပြန်ရန်";
            if("Auto-translate headlines".equals(en))return "သတင်းခေါင်းစဉ်များ အလိုအလျောက် ဘာသာပြန်ရန်";
            if("Read in app".equals(en))return "အက်ပ်အတွင်း ဖတ်ရန်";
            if("Translate now".equals(en))return "ယခု ဘာသာပြန်ရန်";
            if("Translating".equals(en))return "ဘာသာပြန်နေသည်";
            if("Original headline".equals(en))return "မူရင်းခေါင်းစဉ်";
            if("Read translated article".equals(en))return "ဘာသာပြန်ထားသည့်သတင်း ဖတ်ရန်";
            if("Read original".equals(en))return "မူရင်းသတင်း ဖတ်ရန်";
            if("Follow selected news/app language".equals(en))return "ရွေးထားသော သတင်း / အက်ပ် ဘာသာစကားအတိုင်း";
        } else if("th".equals(lang)) {
            if("Translate articles to".equals(en))return "แปลข่าวเป็นภาษา";
            if("Translate headline".equals(en))return "แปลพาดหัวข่าว";
            if("Auto-translate headlines".equals(en))return "แปลหัวข้อข่าวอัตโนมัติ";
            if("Read in app".equals(en))return "อ่านในแอป";
            if("Translate now".equals(en))return "แปลตอนนี้";
            if("Translating".equals(en))return "กำลังแปล";
            if("Original headline".equals(en))return "พาดหัวข่าวต้นฉบับ";
            if("Read translated article".equals(en))return "อ่านข่าวฉบับแปล";
            if("Read original".equals(en))return "อ่านต้นฉบับ";
            if("Follow selected news/app language".equals(en))return "ตามภาษาข่าวหรือแอป";
        } else if("es".equals(lang)) {
            if("Translate articles to".equals(en))return "Traducir noticias a";
            if("Translate headline".equals(en))return "Traducir titular";
            if("Read translated article".equals(en))return "Leer noticia traducida";
            if("Read original".equals(en))return "Leer original";
        } else if("fr".equals(lang)) {
            if("Translate articles to".equals(en))return "Traduire les articles en";
            if("Translate headline".equals(en))return "Traduire le titre";
            if("Read translated article".equals(en))return "Lire l'article traduit";
            if("Read original".equals(en))return "Lire l'original";
        } else if("hi".equals(lang)) {
            if("Translate articles to".equals(en))return "खबरों का अनुवाद करें";
            if("Translate headline".equals(en))return "शीर्षक अनुवाद करें";
            if("Read translated article".equals(en))return "अनूदित खबर पढ़ें";
            if("Read original".equals(en))return "मूल खबर पढ़ें";
        } else if("id".equals(lang)) {
            if("Translate articles to".equals(en))return "Terjemahkan berita ke";
            if("Translate headline".equals(en))return "Terjemahkan judul";
            if("Read translated article".equals(en))return "Baca berita terjemahan";
            if("Read original".equals(en))return "Baca berita asli";
        }
        return en;
    }
    private String newsText(String english) {
        String lang=Prefs.language(this);
        if("my".equals(lang)) {
            if("Global news".equals(english))return "ကမ္ဘာ့သတင်း";
            if("News from the last 72 hours".equals(english))return "နောက်ဆုံး ၇၂ နာရီအတွင်း သတင်းများ";
            if("72-hour emergency kit".equals(english))return "၇၂ နာရီ အရေးပေါ်အသုံးအဆောင်များ";
        } else if("th".equals(lang)) {
            if("Global news".equals(english))return "ข่าวทั่วโลก";
            if("News from the last 72 hours".equals(english))return "ข่าวในช่วง 72 ชั่วโมงที่ผ่านมา";
            if("72-hour emergency kit".equals(english))return "ชุดฉุกเฉิน 72 ชั่วโมง";
        } else if("es".equals(lang)) {
            if("Global news".equals(english))return "Noticias mundiales";
            if("News from the last 72 hours".equals(english))return "Noticias de las últimas 72 horas";
        } else if("fr".equals(lang)) {
            if("Global news".equals(english))return "Actualités mondiales";
            if("News from the last 72 hours".equals(english))return "Actualités des dernières 72 heures";
        } else if("hi".equals(lang)) {
            if("Global news".equals(english))return "विश्व समाचार";
        } else if("id".equals(lang)) {
            if("Global news".equals(english))return "Berita dunia";
        }
        return english;
    }
    private void newsEntryCard(boolean showKit) {
        LinearLayout entry=card();
        add(entry,chip("🌐  "+newsText("Global news"),TEAL,MINT));space(entry,10);
        add(entry,text(newsText("News from the last 72 hours"),19,INK,true));space(entry,7);
        paragraph(entry,"World • Protests • Disasters • Food • Health • Technology • Climate");
        space(entry,11);
        TextView explore=button("↗  "+newsText("Global news"),TEAL,BUTTON_TEXT);
        explore.setOnClickListener(v->openHeadlines());entry.addView(explore);
        if(showKit) {space(entry,9);TextView kit=button("✚  "+newsText("72-hour emergency kit"),SECONDARY_BUTTON,TEAL);
            kit.setOnClickListener(v->show72HourKit());entry.addView(kit);}
    }
    private void openHeadlines() {
        page=6;headlines=WorldNewsFeed.cached(this,newsLanguage(),newsTopic());draw();loadHeadlines(false);
    }
    @Override public void onBackPressed() {
        if(page==6){page=4;draw();}else super.onBackPressed();
    }
    private void loadHeadlines(boolean force) {
        if(headlinesLoading)return;
        headlinesLoading=true;draw();
        final String language=newsLanguage(), topic=newsTopic();
        new Thread(()->{
            JSONObject data=WorldNewsFeed.load(getApplicationContext(),language,topic,force);
            ui.post(()->{
                if(isFinishing() || isDestroyed())return;
                headlinesLoading=false;headlines=data;
                if(page==6 && language.equals(newsLanguage()) && topic.equals(newsTopic()))draw();
            });
        },"WorldNews-rss").start();
    }
    private void selectNewsLanguage() {
        new AlertDialog.Builder(this).setTitle("News language / edition")
            .setSingleChoiceItems(WorldNewsFeed.NAMES,WorldNewsFeed.langIndex(newsLanguage()),(dialog,which)->{
                Prefs.get(this).edit().putString("world_news_language",WorldNewsFeed.LANGS[which]).apply();
                dialog.dismiss();headlines=WorldNewsFeed.cached(this,newsLanguage(),newsTopic());draw();loadHeadlines(false);
            }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void selectNewsTopic() {
        new AlertDialog.Builder(this).setTitle("News topic")
            .setSingleChoiceItems(WorldNewsFeed.TOPIC_NAMES,WorldNewsFeed.topicIndex(newsTopic()),(dialog,which)->{
                Prefs.get(this).edit().putString("world_news_topic",WorldNewsFeed.TOPICS[which]).apply();
                dialog.dismiss();headlines=WorldNewsFeed.cached(this,newsLanguage(),newsTopic());draw();loadHeadlines(false);
            }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void showGlobalHeadlines() {
        onScreenTranslations.clear();
        LinearLayout intro=card();
        add(intro,chip("🌏  NEWS • 72H",TEAL,MINT));space(intro,8);
        add(intro,text(newsText("News from the last 72 hours"),21,INK,true));space(intro,7);
        paragraph(intro, "my".equals(Prefs.language(this))
            ?"သတင်းများသည် တရားဝင်အရေးပေါ်သတိပေးချက်များ မဟုတ်ပါ။ မူရင်းသတင်းကို အောက်တွင်ဖော်ပြထားပြီး သင်ရွေးထားသော ဘာသာစကားသို့ ဘာသာပြန်ကြည့်ရှုနိုင်သည်။"
            :"Public news headlines are not official emergency alerts. Headlines automatically translate inside Hazard Guard; original titles stay visible.");
        space(intro,12);
        TextView lang=button("🌐  "+WorldNewsFeed.NAMES[WorldNewsFeed.langIndex(newsLanguage())]+"  ▾",SECONDARY_BUTTON,TEAL);
        lang.setOnClickListener(v->selectNewsLanguage());intro.addView(lang);space(intro,9);
        String target=newsTranslationLanguage();
        TextView translation=button("🌐  "+translateCaption("Translate articles to")+"  •  "+
            WorldNewsFeed.NAMES[WorldNewsFeed.langIndex(target)]+"  ▾",SECONDARY_BUTTON,TEAL);
        translation.setOnClickListener(v->selectTranslationLanguage());
        intro.addView(translation);space(intro,9);
        LinearLayout toggleRow=horizontal();
        TextView autoLabel=text("文  "+translateCaption("Auto-translate headlines"),13,INK,true);
        toggleRow.addView(autoLabel,new LinearLayout.LayoutParams(0,-2,1));
        Switch auto=new Switch(this);
        auto.setChecked(Prefs.get(this).getBoolean("world_news_auto_translate",true));
        auto.setOnCheckedChangeListener((button,enabled)->{
            Prefs.get(this).edit().putBoolean("world_news_auto_translate",enabled).apply();
            draw();
        });
        toggleRow.addView(auto);
        intro.addView(toggleRow);space(intro,8);
        TextView topic=button("☷  "+WorldNewsFeed.TOPIC_NAMES[WorldNewsFeed.topicIndex(newsTopic())]+"  ▾",SECONDARY_BUTTON,TEAL);
        topic.setOnClickListener(v->selectNewsTopic());intro.addView(topic);space(intro,9);
        TextView refresh=button(headlinesLoading?"Fetching news…":"↻  Refresh latest headlines",TEAL,BUTTON_TEXT);
        refresh.setEnabled(!headlinesLoading);refresh.setOnClickListener(v->loadHeadlines(true));intro.addView(refresh);
        long updated=headlines.optLong("updatedAt",0);
        if(updated>0){space(intro,9);add(intro,text("Feed checked: "+time(updated)+" • "+duration(updated)+" ago",11,MUTED,false));}
        String failed=headlines.optString("loadError","");
        if(!failed.isEmpty()){space(intro,9);add(intro,text("Unable to refresh. Showing saved headlines if available. " + failed,12,ORANGE,true));}
        int ok=headlines.optInt("providers",0), expected=headlines.optInt("expectedProviders",0);
        if(ok>0 && expected>ok) {space(intro,6);add(intro,text("Only "+ok+" of "+expected+" language editions responded.",11,ORANGE,true));}
        TextView original=text("my".equals(Prefs.language(this))
            ?"အလိုအလျောက် ဘာသာပြန်ထားသော ခေါင်းစဉ်များကို ဤနေရာတွင် ဖတ်နိုင်သည်။ အများသုံးသတင်းခေါင်းစဉ်ကို အခမဲ့ MyMemory ဝန်ဆောင်မှုသို့ ပို့နိုင်သည်။ အင်တာနက်နှင့် အသုံးပြုမှုကန့်သတ်ချက်ရှိသည်။ အရေးပေါ်အချက်အလက်ကို တရားဝင်ရင်းမြစ်မှ စစ်ဆေးပါ။"
            :"Auto-translated headlines appear here, not in another app. Only public headline text is sent to the free MyMemory translation service when enabled. Daily limits apply; verify safety information with official sources.",11,MUTED,false);
        original.setPadding(0,dp(10),0,0);intro.addView(original);
        JSONArray items=headlines.optJSONArray("items");
        if(items==null || items.length()==0) {
            LinearLayout empty=card();paragraph(empty,headlinesLoading?"Loading headlines…":
                "No verified recent headlines for this edition/topic. Check internet access, choose another language, or try again.");
        } else {
            section("Headlines ("+items.length()+")");
            boolean automatic=Prefs.get(this).getBoolean("world_news_auto_translate",true);
            int automaticallyQueued=0;
            final String targetLanguage=newsTranslationLanguage();
            for(int i=0;i<Math.min(40,items.length());i++) {
                JSONObject story=items.optJSONObject(i);if(story==null)continue;
                String url=story.optString("link","");
                final String title=story.optString("title","");
                final String language=story.optString("language","en");
                final String from=TranslationRules.source(language,title);
                final String key=HeadlineTranslationService.key(title,from,targetLanguage);
                LinearLayout item=card();
                TextView translated=text("",16,INK,true);
                translated.setLineSpacing(dp(2),1.08f);
                item.addView(translated);space(item,8);
                if(!from.equals(targetLanguage)) {
                    TextView old=text(translateCaption("Original headline")+": "+title,12,MUTED,false);
                    item.addView(old);space(item,8);
                }
                String src=story.optString("source","").trim();
                long published=story.optLong("time",0);
                add(item,text((src.isEmpty()?"News":src)+" • "+language.toUpperCase(Locale.ROOT)+
                    (published>0?" • "+time(published)+" ("+duration(published)+" ago)":""),11,MUTED,false));
                space(item,11);
                String cached=HeadlineTranslationService.cached(this,key);
                if(from.equals(targetLanguage)) {
                    translated.setText(title);
                } else if(!cached.isEmpty()) {
                    translated.setText(cached);
                } else if(automatic && automaticallyQueued<22) {
                    automaticallyQueued++;
                    enqueueInlineTranslation(title,from,targetLanguage,translated);
                } else {
                    translated.setText(title);
                }
                if(!from.equals(targetLanguage)) {
                    TextView retry=button("文  "+translateCaption("Translate now"),SECONDARY_BUTTON,TEAL);
                    retry.setOnClickListener(v->enqueueInlineTranslation(title,from,targetLanguage,translated));
                    item.addView(retry);space(item,7);
                }
                if(WorldNewsFeed.safeNewsUrl(url)) {
                    TextView read=button("↗  "+translateCaption("Read in app"),TEAL,BUTTON_TEXT);
                    final String storyUrl=url;
                    read.setOnClickListener(v->showInAppNews(storyUrl,title,from,targetLanguage));
                    item.addView(read);
                }
            }
        }
        section(newsText("72-hour emergency kit"));
        LinearLayout kit=card();
        paragraph(kit,"Prepare supplies before any emergency; this is general guidance, not a claim that a disaster is imminent.");
        space(kit,9);
        TextView openKit=button("✚  Open 72-hour checklist",SECONDARY_BUTTON,TEAL);
        openKit.setOnClickListener(v->show72HourKit());kit.addView(openKit);
        TextView back=button("‹  Back to World disasters",SECONDARY_BUTTON,TEAL);
        back.setOnClickListener(v->{page=4;draw();});pageBody.addView(back);
    }
    /** Keep the user on this screen and update only the headline TextViews. */
    private void enqueueInlineTranslation(String title,String from,String target,TextView targetView) {
        if(title.isEmpty())return;
        if(from.equals(target)) {targetView.setText(title);return;}
        final String key=HeadlineTranslationService.key(title,from,target);
        String saved=HeadlineTranslationService.cached(this,key);
        if(!saved.isEmpty()){targetView.setText(saved);return;}
        ArrayList<TextView> waiting=onScreenTranslations.get(key);
        if(waiting==null){waiting=new ArrayList<>();onScreenTranslations.put(key,waiting);}
        if(!waiting.contains(targetView))waiting.add(targetView);
        targetView.setText(translateCaption("Translating")+"…");
        if(!translationsRunning.add(key))return;
        inlineTranslationWorker.execute(()->{
            HeadlineTranslationService.Result result=
                HeadlineTranslationService.translate(getApplicationContext(),title,from,target);
            ui.post(()->{
                translationsRunning.remove(key);
                ArrayList<TextView> views=onScreenTranslations.remove(key);
                if(isFinishing() || isDestroyed() || views==null)return;
                String message=result.ok?result.text:
                    ("my".equals(Prefs.language(this))
                        ?"ဘာသာပြန်၍ မရနိုင်ပါ • မူရင်းခေါင်းစဉ်ကို ဖတ်နိုင်သည်"
                        :"Translation unavailable • original headline shown");
                for(TextView t:views) {
                    t.setText(message);
                    t.setTextColor(result.ok?INK:ORANGE);
                }
            });
        });
    }
    private void showInAppNews(String url,String title,String source,String target) {
        if(!WorldNewsFeed.safeNewsUrl(url))return;
        Intent intent=new Intent(this,NewsReaderActivity.class);
        intent.putExtra("url",url);
        intent.putExtra("language",target);
        intent.putExtra("title",title);
        String cached=HeadlineTranslationService.cached(this,
            HeadlineTranslationService.key(title,source,target));
        intent.putExtra("translation",cached);
        startActivity(intent);
    }
    private void show72HourKit() {
        ScrollView scroller=new ScrollView(this);
        LinearLayout body=vertical();body.setPadding(dp(20),dp(16),dp(20),dp(20));scroller.addView(body);
        add(body,text(newsText("72-hour emergency kit"),22,INK,true));space(body,10);
        add(body,text("Prepare supplies for at least 3 days for every household member. Adjust for infants, pets, medical needs and local conditions.",14,INK,false));
        space(body,12);
        String[] checks={
            "✓ Water — at least one gallon per person per day, for three days (drinking and sanitation)",
            "✓ Three-day supply of shelf-stable food, including any special dietary items",
            "✓ Essential medicines, first-aid kit and personal hygiene supplies",
            "✓ Phone charger, power bank, flashlight, batteries and battery radio",
            "✓ Important documents, contact numbers, cash and a family meeting plan",
            "✓ Manual can opener, whistle, local maps and a sturdy bag",
            "✓ Adapt supplies for children, older adults, disabilities and pets"
        };
        for(String line:checks){TextView v=text(line,14,INK,false);v.setPadding(0,dp(7),0,dp(7));body.addView(v);}
        space(body,12);
        add(body,text("Sources: Ready.gov emergency supply kit. Follow local civil-defense and evacuation instructions during actual emergencies.",12,MUTED,false));
        space(body,9);
        TextView source=button("↗  Read official preparedness guidance",SECONDARY_BUTTON,TEAL);
        source.setOnClickListener(v->open("https://www.ready.gov/kit"));body.addView(source);
        new AlertDialog.Builder(this).setView(scroller).setPositiveButton(android.R.string.ok,null).show();
    }

    private void showNews(int limit) {
        LinearLayout intro=card();
        add(intro,chip("◷  GDACS",ORANGE,PALE));space(intro,8);
        paragraph(intro,t(R.string.news_explain));
        dataAge(intro,snapshot.newsAt);
        for(int j=0;j<snapshot.sourceReport.length();j++) {
            JSONObject status=snapshot.sourceReport.optJSONObject(j);
            if(status!=null && "GDACS news".equals(status.optString("source")) &&
               "failed".equals(status.optString("status"))) {
                space(intro,8);
                add(intro,text(t(R.string.gdacs_unavailable),12,ORANGE,true));
                break;
            }
        }
        TextView retry=button("↻  "+t(R.string.refresh),MINT,TEAL);
        retry.setOnClickListener(v->refresh());space(intro,9);intro.addView(retry);
        JSONArray items=snapshot.news.optJSONArray("items");
        if(items==null || items.length()==0){
            LinearLayout empty=card();paragraph(empty,snapshot.newsAt==0?
                t(R.string.gdacs_no_verified):t(R.string.news_empty));return;
        }
        for(int i=0;i<Math.min(limit,items.length());i++) {
            JSONObject item=items.optJSONObject(i);if(item==null)continue;
            String url=item.optString("link","");
            LinearLayout news=card();
            add(news,text(item.optString("title",""),15,INK,true));space(news,7);
            String badge=item.optString("alertlevel","");
            String date=item.optString("pubDate","");
            add(news,text("GDACS  •  "+(badge.isEmpty()?t(R.string.news_report):badge)+
                (date.isEmpty()?"":"  •  "+date),11,MUTED,false));
            if(url.startsWith("https://")) {
                space(news,7);add(news,text(t(R.string.open_source)+"  ↗",12,TEAL,true));
                news.setOnClickListener(v->open(url));
            }
        }
    }
    private String eventCategory(JSONObject e) {
        JSONArray cats=e.optJSONArray("categories");
        if(cats!=null&&cats.length()>0) {
            JSONObject o=cats.optJSONObject(0);
            if(o!=null)return o.optString("title",t(R.string.world_events));
        }
        return t(R.string.world_events);
    }

    // User-initiated dialer: NEVER place calls automatically or infer a universal number.
    private void buildEmergencyShortcut(LinearLayout parent) {
        LinearLayout bar=horizontal();
        bar.setPadding(dp(13),dp(9),dp(13),dp(9));
        bar.setBackgroundColor(lightMode?0xfffff1ea:0xff352824);
        LinearLayout copy=vertical();
        add(copy,text("✚  "+t(R.string.emergency_shortcut),14,lightMode?0xff9a332a:0xffffbba0,true));
        add(copy,text(t(R.string.emergency_subline),11,MUTED,false));
        bar.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        TextView action=button(t(R.string.emergency_open),0xffb8493c,WHITE);
        action.setOnClickListener(v->showEmergencyHelp());
        bar.addView(action);parent.addView(bar);
    }
    private void showEmergencyHelp() {
        String override=Prefs.get(this).getString("emergency_country","");
        String code=override.isEmpty()?Prefs.countryCode(this):override;
        EmergencyContacts.Country c=EmergencyContacts.forCode(code);
        ScrollView scroller=new ScrollView(this);
        LinearLayout body=vertical();body.setPadding(dp(20),dp(15),dp(20),dp(18));scroller.addView(body);
        add(body,text(t(R.string.emergency_note),13,INK,false));space(body,12);
        add(body,text(t(R.string.emergency_country_label),12,MUTED,true));space(body,5);
        TextView select=button((c==null?t(R.string.emergency_select_country):c.name)+"  ▾",SECONDARY_BUTTON,TEAL);
        body.addView(select);space(body,13);
        if(c==null){
            add(body,text(t(R.string.emergency_unknown_country),13,ORANGE,true));space(body,12);
        } else {
            add(body,text(c.name+" · "+t(R.string.emergency_verified),12,MUTED,false));space(body,10);
            for(int i=0;i<c.numbers.length;i++){
                final String num=c.numbers[i];
                TextView dial=button("☎  "+c.services[i]+"  ·  "+num,0xffb8493c,WHITE);
                dial.setContentDescription(c.services[i]+" "+num);
                dial.setOnClickListener(v->openEmergencyDialer(num));
                body.addView(dial);space(body,9);
            }
            String source=c.source;
            TextView verify=text(t(R.string.emergency_number_source)+"  ↗",12,TEAL,true);
            verify.setPadding(0,dp(7),0,dp(12));
            verify.setOnClickListener(v->open(source));body.addView(verify);
        }
        TextView nearby=button("⌖  "+t(R.string.emergency_nearby_hospital),MINT,TEAL);
        nearby.setOnClickListener(v->openNearbyHospitals());body.addView(nearby);space(body,12);
        add(body,text(t(R.string.emergency_disclaimer),12,MUTED,false));
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle(t(R.string.emergency_title))
          .setView(scroller).setNegativeButton(android.R.string.cancel,null).create();
        select.setOnClickListener(v->{dlg.dismiss();chooseEmergencyCountry();});
        dlg.show();
    }
    private void chooseEmergencyCountry() {
        EmergencyContacts.Country[] countries=EmergencyContacts.supported();
        String[] labels=new String[countries.length];
        for(int i=0;i<countries.length;i++)labels[i]=countries[i].name;
        new AlertDialog.Builder(this).setTitle(t(R.string.emergency_select_country))
          .setItems(labels,(dialog,i)->{
             Prefs.get(this).edit().putString("emergency_country",countries[i].code).apply();
             showEmergencyHelp();
          }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void openEmergencyDialer(String number) {
        if(number==null || !number.matches("[0-9]{2,6}"))return;
        try {startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",number,null)));}
        catch(Exception e){Toast.makeText(this,R.string.emergency_dialer_unavailable,Toast.LENGTH_LONG).show();}
    }
    private void openNearbyHospitals() {
        String geo=String.format(Locale.US,"geo:%.5f,%.5f?q=emergency%%20hospital",
            Prefs.latitude(this),Prefs.longitude(this));
        try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(geo)));}
        catch(Exception e){Toast.makeText(this,R.string.emergency_map_unavailable,Toast.LENGTH_LONG).show();}
    }
    private void showSettings() {
        section(t(R.string.preferences));
        LinearLayout general=card();
        settingAction(general,t(R.string.choose_location),city(),()->chooseCity());rule(general);
        settingAction(general,t(R.string.my_location),t(R.string.location_opt_in),()->useDeviceLocation());rule(general);
        settingAction(general,t(R.string.language),Languages.label(Prefs.languageSetting(this)),()->chooseLanguage());
        rule(general);
        settingAction(general,t(R.string.appearance),themeLabel(),()->chooseTheme());
        rule(general);
        settingAction(general,t(R.string.internet_access),t(R.string.internet_explain),()->open("https://www.gdacs.org/"));
        rule(general);
        settingAction(general,t(R.string.notification_settings),t(R.string.notification_settings_help),()->openNotificationSettings());
        showSourceHealth();
        LinearLayout alerts=card();
        toggle(alerts,R.string.alert_eq,R.string.alert_eq_desc,"alert_eq",true);rule(alerts);
        toggle(alerts,R.string.alert_enso,R.string.alert_enso_desc,"alert_enso",true);rule(alerts);
        toggle(alerts,R.string.alert_air,R.string.alert_air_desc,"alert_air",true);rule(alerts);
        toggle(alerts,R.string.alert_weather,R.string.alert_weather_desc,"alert_weather",true);
        rule(alerts);paragraph(alerts,t(R.string.world_alerts_note));
        LinearLayout threshold=card();add(threshold,text(t(R.string.threshold),15,INK,true));space(threshold,12);
        LinearLayout magnitudes=horizontal();
        float[] choices={3.0f,4.0f,4.5f,5.0f,5.5f,6.0f};
        for(float choice:choices) {
            boolean chosen=Math.abs(Prefs.magnitude(this)-choice)<0.01;
            TextView v=chip(num(choice,1),chosen?BUTTON_TEXT:INK,chosen?TEAL:CELL);
            v.setGravity(Gravity.CENTER);v.setPadding(dp(2),dp(12),dp(2),dp(12));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);p.rightMargin=dp(4);
            magnitudes.addView(v,p);
            v.setOnClickListener(view->{Prefs.get(this).edit().putFloat("magnitude",choice).apply();draw();});
        }
        threshold.addView(magnitudes);
        LinearLayout sounds=card();
        toggle(sounds,R.string.sound,0,"sound",true);rule(sounds);
        toggle(sounds,R.string.alarm_sound,R.string.alarm_sound_desc,"alarm",false);rule(sounds);
        toggle(sounds,R.string.vibration,0,"vibration",true);rule(sounds);
        toggle(sounds,R.string.voice,R.string.voice_desc,"voice",false);
        LinearLayout tests=card();
        TextView test=button("◉  "+t(R.string.test_alert),TEAL,BUTTON_TEXT);test.setOnClickListener(v->{Notifications.test(this);sayForegroundAlert();});tests.addView(test);
        space(tests,8);
        TextView voice=button("♫  "+t(R.string.test_voice),SECONDARY_BUTTON,TEAL);voice.setOnClickListener(v->speak(t(R.string.voice_test_phrase)));tests.addView(voice);
        section(t(R.string.news_heading));LinearLayout info=card();paragraph(info,t(R.string.news_explain));
        section(t(R.string.latest_alerts));showHistory(8);
        section(t(R.string.safety_title));LinearLayout safety=card();paragraph(safety,t(R.string.safety_text));
        rule(safety);paragraph(safety,t(R.string.global_safety_note));
        section(t(R.string.data_sources));sources();
    }
    private void showSourceHealth() {
        section(t(R.string.source_health_title));
        LinearLayout health=card();
        paragraph(health,t(R.string.source_health_explain));
        if(snapshot.sourceReport==null || snapshot.sourceReport.length()==0) {
            space(health,8);paragraph(health,t(R.string.not_updated));return;
        }
        for(int i=0;i<snapshot.sourceReport.length();i++) {
            JSONObject line=snapshot.sourceReport.optJSONObject(i);
            if(line==null)continue;
            if(i>0)rule(health);
            String state=line.optString("status","failed");
            String label="ok".equals(state)?t(R.string.source_ok):
                ("cached".equals(state)?t(R.string.source_cached):t(R.string.source_failed));
            String cause=line.optString("error","");
            add(health,text(line.optString("source","Provider"),13,INK,true));
            add(health,text(label+(cause.isEmpty()?"":" • "+cause),12,
                "failed".equals(state)?ORANGE:TEAL,false));
        }
    }
    private void settingAction(LinearLayout parent,String label,String value,Runnable action){
        LinearLayout row=horizontal();
        LinearLayout desc=vertical();add(desc,text(label,14,INK,true));space(desc,3);add(desc,text(value,13,TEAL,false));
        row.addView(desc,new LinearLayout.LayoutParams(0,-2,1));add(row,text("›",26,MUTED,false));
        row.setOnClickListener(v->action.run());parent.addView(row);
    }
    private void toggle(LinearLayout parent,int label,int help,String prefKey,boolean def){
        LinearLayout row=horizontal();LinearLayout detail=vertical();
        add(detail,text(t(label),14,INK,true));if(help!=0){space(detail,3);add(detail,text(t(help),11,MUTED,false));}
        row.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
        Switch sw=new Switch(this);sw.setChecked(Prefs.get(this).getBoolean(prefKey,def));
        sw.setOnCheckedChangeListener((button,value)->Prefs.get(this).edit().putBoolean(prefKey,value).apply());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.leftMargin=dp(8);row.addView(sw,lp);
        row.setOnClickListener(v->sw.setChecked(!sw.isChecked()));parent.addView(row);
    }
    private void selectPreset(int index) {
        Prefs.get(this).edit().putInt("city",index).remove("custom_name").remove("using_device_location").apply();
        locationChanged();
    }
    private void locationChanged() {
        Prefs.get(this).edit().remove("emergency_country").apply();
        if("auto".equals(Prefs.languageSetting(this))) {recreate();return;}
        snapshot=HazardRepository.snapshot(this);draw();refresh();
        Toast.makeText(this,R.string.city_changed,Toast.LENGTH_SHORT).show();
    }
    private void chooseCity() {
        new AlertDialog.Builder(this).setTitle(t(R.string.choose_location))
            .setItems(new String[]{t(R.string.my_location),t(R.string.search_online),t(R.string.browse_presets)},(d,which)->{
                if(which==0)useDeviceLocation();else if(which==1)searchCity();else choosePreset();
            }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void choosePreset() {
        String[] names=new String[Cities.NAMES.length];
        for(int i=0;i<names.length;i++) names[i]=Cities.labeled(i,my());
        new AlertDialog.Builder(this).setTitle(t(R.string.browse_presets))
            .setSingleChoiceItems(names,Prefs.isCustom(this)?-1:Prefs.cityIndex(this),(d,index)->{
                d.dismiss();selectPreset(index);
            }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void searchCity() {
        EditText entry=new EditText(this);entry.setSingleLine(true);
        entry.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        entry.setHint(t(R.string.search_hint));entry.setPadding(dp(22),dp(16),dp(22),dp(16));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(t(R.string.search_online))
            .setMessage(t(R.string.search_info)).setView(entry)
            .setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(t(R.string.search_button),null).create();
        dialog.setOnShowListener(a->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String query=entry.getText().toString().trim();
            if(query.length()<2){entry.setError(t(R.string.search_hint));return;}
            dialog.dismiss();
            Toast.makeText(this,R.string.searching,Toast.LENGTH_SHORT).show();
            new Thread(()->{
                try {
                    JSONArray data=HazardRepository.places(query);
                    ui.post(()->{if(!isFinishing())showResults(data);});
                }catch(Exception e){ui.post(()->Toast.makeText(this,R.string.search_failed,Toast.LENGTH_LONG).show());}
            },"HazardGuard-geocoding").start();
        }));
        dialog.show();
    }
    private void showResults(JSONArray results) {
        if(results.length()==0){new AlertDialog.Builder(this).setMessage(t(R.string.no_cities)).setPositiveButton(android.R.string.ok,null).show();return;}
        String[] labels=new String[results.length()];
        for(int i=0;i<results.length();i++) {
            JSONObject o=results.optJSONObject(i);if(o==null)continue;
            labels[i]=o.optString("name")+", "+o.optString("admin1")+" · "+o.optString("country");
        }
        new AlertDialog.Builder(this).setTitle(t(R.string.search_results))
            .setItems(labels,(d,index)->{
                JSONObject loc=results.optJSONObject(index);if(loc==null)return;
                double lat=loc.optDouble("latitude",Double.NaN),lon=loc.optDouble("longitude",Double.NaN);
                if(Double.isNaN(lat)||Double.isNaN(lon))return;
                String full=loc.optString("name")+", "+loc.optString("country");
                Prefs.saveCustom(this,full,lat,lon,loc.optString("country_code",""),false);
                locationChanged();
            }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void openNotificationSettings() {
        Intent intent;
        if(Build.VERSION.SDK_INT>=26) {
            intent=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
        } else {
            intent=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));
        }
        try {startActivity(intent);} catch(Exception e) {Toast.makeText(this,R.string.permission_help,Toast.LENGTH_LONG).show();}
    }
    private void chooseLanguage() {
        String current=Prefs.languageSetting(this);
        int selected=0;for(int i=0;i<Languages.CODES.length;i++)if(Languages.CODES[i].equals(current))selected=i;
        new AlertDialog.Builder(this).setTitle(t(R.string.language))
            .setSingleChoiceItems(Languages.NAMES,selected,(dialog,which)->{
                Prefs.get(this).edit().putString("language",Languages.CODES[which]).apply();
                dialog.dismiss();recreate();
            }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private String themeLabel(){
        String mode=Prefs.theme(this);
        return "light".equals(mode)?t(R.string.light_mode):"dark".equals(mode)?t(R.string.dark_mode):t(R.string.system_mode);
    }
    private void chooseTheme() {
        final String[] modes={"system","light","dark"};
        String current=Prefs.theme(this);int selected=0;
        for(int i=0;i<modes.length;i++)if(modes[i].equals(current))selected=i;
        new AlertDialog.Builder(this).setTitle(t(R.string.appearance))
            .setSingleChoiceItems(new String[]{t(R.string.system_mode),t(R.string.light_mode),t(R.string.dark_mode)},selected,
                (dialog,which)->{
                    Prefs.get(this).edit().putString("theme",modes[which]).apply();
                    dialog.dismiss();recreate();
                }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void useDeviceLocation() {
        if(Build.VERSION.SDK_INT>=23 &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},202);
            return;
        }
        readDeviceLocation();
    }
    private void readDeviceLocation() {
        if(Build.VERSION.SDK_INT>=23 &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
        LocationManager manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(manager==null || !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Toast.makeText(this,R.string.location_enable,Toast.LENGTH_LONG).show();return;
        }
        Toast.makeText(this,R.string.location_searching,Toast.LENGTH_SHORT).show();
        final boolean[] done={false};
        final Location[] prior={null};
        try {prior[0]=manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);}catch(Exception ignored){}
        LocationListener listener=new LocationListener(){
            public void onLocationChanged(Location loc){
                if(done[0] || loc==null)return;
                done[0]=true;manager.removeUpdates(this);
                pendingLocationManager=null;pendingLocationListener=null;
                acceptLocation(loc);
            }
            public void onStatusChanged(String provider,int status,Bundle extras){}
            public void onProviderEnabled(String provider){}
            public void onProviderDisabled(String provider){}
        };
        pendingLocationManager=manager;pendingLocationListener=listener;
        try {manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER,listener,Looper.getMainLooper());}
        catch(Exception e){
            pendingLocationManager=null;pendingLocationListener=null;
            if(prior[0]!=null)acceptLocation(prior[0]);
            else Toast.makeText(this,R.string.location_unavailable,Toast.LENGTH_LONG).show();
            return;
        }
        ui.postDelayed(()->{
            if(done[0] || isFinishing())return;
            done[0]=true;manager.removeUpdates(listener);
            pendingLocationManager=null;pendingLocationListener=null;
            if(prior[0]!=null && System.currentTimeMillis()-prior[0].getTime()<60*60_000L)
                acceptLocation(prior[0]);
            else Toast.makeText(this,R.string.location_unavailable,Toast.LENGTH_LONG).show();
        },12000);
    }
    private void acceptLocation(Location location) {
        double lat=location.getLatitude(),lon=location.getLongitude();
        new Thread(()->{
            String name=Locales.text(getApplicationContext(),R.string.detected_area),country="";
            try {
                Geocoder geocoder=new Geocoder(getApplicationContext(),Locale.ENGLISH);
                List<Address> results=geocoder.getFromLocation(lat,lon,1);
                if(results!=null && !results.isEmpty()) {
                    Address address=results.get(0);
                    country=address.getCountryCode()==null?"":address.getCountryCode();
                    String town=address.getLocality();
                    if(town==null||town.isEmpty())town=address.getAdminArea();
                    if(town!=null&&!town.isEmpty())name=town;
                    if(address.getCountryName()!=null)name+=", "+address.getCountryName();
                }
            } catch(Exception ignored) {}
            final String label=name,code=country;
            ui.post(()->{
                if(isFinishing() || isDestroyed())return;
                Prefs.saveCustom(this,label,lat,lon,code,true);
                locationChanged();
            });
        },"HazardGuard-reverse-geocode").start();
    }
    private void configureSpeech() {
        if(tts==null || !voiceReady)return;
        Locale language=Locale.forLanguageTag(Prefs.language(this));
        int response=tts.setLanguage(language);
        if(response==TextToSpeech.LANG_MISSING_DATA || response==TextToSpeech.LANG_NOT_SUPPORTED)
            tts.setLanguage(Locale.ENGLISH);
        tts.setSpeechRate(0.92f);
    }
    private void speak(String message) {
        if(tts==null || !voiceReady) {Toast.makeText(this,R.string.voice_unavailable,Toast.LENGTH_LONG).show();return;}
        configureSpeech();tts.speak(message,TextToSpeech.QUEUE_FLUSH,null,"hazard_voice");
    }
    private void sayForegroundAlert() {
        if(!Prefs.voice(this))return;
        long timestamp=Prefs.get(this).getLong("voice_pending_at",0);
        if(timestamp>0 && System.currentTimeMillis()-timestamp<60_000L) {
            String phrase=Prefs.get(this).getString("voice_pending","");
            Prefs.get(this).edit().remove("voice_pending_at").remove("voice_pending").apply();
            if(!phrase.isEmpty())speak(phrase);
        }
    }
    private void open(String url) {
        try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception ex) {Toast.makeText(this,url,Toast.LENGTH_LONG).show();}
    }
    private class Ring extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int level,color;
        Ring(Context c,int aqi,int shade){super(c);level=aqi;color=shade;setContentDescription("US AQI "+aqi);}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);float w=getWidth(),h=getHeight(),stroke=Math.min(w,h)*0.095f;
            RectF box=new RectF(stroke,stroke,w-stroke,h-stroke);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(stroke);
            paint.setColor(BORDER);canvas.drawArc(box,135,270,false,paint);
            paint.setColor(color);canvas.drawArc(box,135,Math.max(2,Math.min(270,level/300f*270)),false,paint);
            paint.setStyle(Paint.Style.FILL);paint.setColor(INK);paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));paint.setTextSize(w*0.29f);
            canvas.drawText(String.valueOf(level),w/2,h/2+stroke,paint);
        }
    }
}
