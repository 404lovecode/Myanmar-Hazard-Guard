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

public class MainActivity extends Activity {
    private static final int NAVY=0xff10263c, INK=0xff22354b, MUTED=0xff708399,
        TEAL=0xff109886, MINT=0xffe9f9f4, BG=0xfff3f6f8, WHITE=0xffffffff,
        ORANGE=0xfff47c59, PALE=0xfffff2ec, PURPLE=0xff7977c8, BLUE=0xff307ac4;
    private LinearLayout root, pageBody;
    private ScrollView scroll;
    private HazardRepository.Snapshot snapshot;
    private int page=0;
    private boolean busy=false, voiceReady=false;
    private TextToSpeech tts;
    private final Handler ui=new Handler(Looper.getMainLooper());

    @Override protected void attachBaseContext(Context newBase) { super.attachBaseContext(Locales.wrap(newBase)); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        snapshot=HazardRepository.snapshot(this);
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
    @Override protected void onDestroy() {if(tts!=null){tts.stop();tts.shutdown();} super.onDestroy();}
    @Override public void onRequestPermissionsResult(int requestCode,String[] perms,int[] results) {
        super.onRequestPermissionsResult(requestCode,perms,results);
        if(requestCode==101 && (results.length==0 || results[0]!=PackageManager.PERMISSION_GRANTED))
            Toast.makeText(this,R.string.permission_help,Toast.LENGTH_LONG).show();
    }
    private String t(int id) {return getString(id);}
    private int dp(float p) {return Math.round(getResources().getDisplayMetrics().density*p);}
    private boolean my(){return "my".equals(Prefs.language(this));}
    private String city(){return Cities.name(Prefs.cityIndex(this),my());}
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
        c.setBackground(background(WHITE,20));c.setElevation(dp(2));
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
        cell.setBackground(background(0xfff5f8fa,14));
        add(cell,text(label,11,MUTED,false));space(cell,4);
        add(cell,text(val,21,color,true));return cell;
    }
    private void twoMetrics(LinearLayout parent,String labelA,String valA,String labelB,String valB) {
        LinearLayout r=horizontal();r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout a=metric(labelA,valA,INK),b=metric(labelB,valB,INK);
        LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,-2,1f);p1.rightMargin=dp(8);
        r.addView(a,p1);r.addView(b,new LinearLayout.LayoutParams(0,-2,1f));parent.addView(r);
    }
    private void rule(LinearLayout container){View v=new View(this);v.setBackgroundColor(0xffe9edf2);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.topMargin=dp(12);p.bottomMargin=dp(12);container.addView(v,p);}
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
        else if(page==3) showEnvironment();else showSettings();
        buildNav(root);
    }
    private void buildHeader(LinearLayout column) {
        LinearLayout h=vertical();h.setPadding(dp(22),dp(24),dp(22),dp(24));
        h.setBackground(gradient(0xff102840,0xff13505a,0));
        LinearLayout small=horizontal();
        TextView logo=text("◈",25,0xff6ae6c5,true);
        small.addView(logo);TextView name=text("  HAZARDGUARD",17,WHITE,true);name.setLetterSpacing(0.08f);
        small.addView(name,new LinearLayout.LayoutParams(0,-2,1f));
        small.addView(chip("MM  •  EN",WHITE,0x3342bdb0));
        h.addView(small);space(h,20);
        add(h,text(page==0?t(R.string.hero_title):
            page==1?t(R.string.quakes):page==2?t(R.string.enso_title):page==3?t(R.string.environment):t(R.string.settings),27,WHITE,true));
        space(h,7);
        add(h,text(page==0?t(R.string.hero_subtitle):t(R.string.monitoring),13,0xffb5d9dd,false));
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
        LinearLayout bar=horizontal();bar.setBackgroundColor(WHITE);bar.setPadding(dp(5),dp(9),dp(5),dp(8));bar.setElevation(dp(12));
        final int[] labels={R.string.home,R.string.quakes,R.string.climate,R.string.environment,R.string.settings};
        final String[] icons={"⌂","≋","☼","◉","⚙"};
        for(int i=0;i<labels.length;i++) {
            final int target=i;boolean active=page==i;
            LinearLayout b=vertical();b.setGravity(Gravity.CENTER);b.setPadding(dp(1),dp(4),dp(1),dp(3));
            if(active)b.setBackground(background(0xffecf8f5,12));
            TextView ico=text(icons[i],21,active?TEAL:MUTED,true);ico.setGravity(Gravity.CENTER);b.addView(ico);
            TextView lab=text(t(labels[i]),my()?9:10,active?TEAL:MUTED,active);lab.setSingleLine(true);lab.setGravity(Gravity.CENTER);
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
            try {updated=HazardRepository.update(getApplicationContext(),true);}
            catch(Exception ignored) {updated=HazardRepository.snapshot(getApplicationContext());updated.failed=4;}
            final HazardRepository.Snapshot result=updated;
            ui.post(()->{
                if(isFinishing() || isDestroyed())return;
                snapshot=result;busy=false;draw();
                if(result.failed>0)Toast.makeText(this,R.string.no_network,Toast.LENGTH_LONG).show();
                sayForegroundAlert();
            });
        },"HazardGuard-refresh").start();
    }
    private void refreshButton() {
        TextView btn=button(busy?t(R.string.refreshing):"↻  "+t(R.string.refresh),TEAL,WHITE);
        btn.setEnabled(!busy);btn.setOnClickListener(v->refresh());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);pageBody.addView(btn,lp);
        refreshedLabel(pageBody);
    }
    private void showHome() {
        LinearLayout overview=vertical();overview.setPadding(dp(16),dp(16),dp(16),dp(16));
        overview.setBackground(gradient(0xfff5e4d9,0xfffbf6ed,18));
        add(overview,text("✦  "+t(R.string.monitoring),12,0xff91613d,true));space(overview,7);
        add(overview,text(t(R.string.safety_text),12,0xff765b4c,false));
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
        add(environment,chip("◉  OPEN-METEO",BLUE,0xffebf4fd));space(environment,12);
        JSONObject weather=snapshot.weather.optJSONObject("current");
        JSONObject air=snapshot.air.optJSONObject("current");
        twoMetrics(environment,t(R.string.temperature),weather==null?"—":num(weather.optDouble("temperature_2m",Double.NaN),1)+"°C",
            t(R.string.aqi),air==null?"—":num(air.optDouble("us_aqi",Double.NaN),0));
        space(environment,10);paragraph(environment,t(R.string.weather_note));
        dataAge(environment,Math.min(snapshot.weatherAt,snapshot.airAt));
        environment.setOnClickListener(v->{page=3;draw();});
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
        LinearLayout notice=card();
        add(notice,chip("USGS  •  48H",TEAL,MINT));space(notice,10);
        paragraph(notice,t(R.string.earthquake_alert_info));space(notice,6);
        paragraph(notice,t(R.string.earthquake_note));space(notice,6);
        paragraph(notice,t(R.string.no_results_note));
        JSONArray events=snapshot.quake.optJSONArray("features");
        if(events==null) {LinearLayout empty=card();paragraph(empty,t(R.string.no_readings));return;}
        if(events.length()==0){LinearLayout empty=card();paragraph(empty,t(R.string.earthquakes_empty));}
        for(int i=0;i<Math.min(events.length(),25);i++) {
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
            add(details,text(props.optString("place","Myanmar region"),14,INK,true));
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
        add(hero,chip("NOAA  •  RONI",PURPLE,0xfff1efff));space(hero,14);
        add(hero,text(t(R.string.roni_index),14,MUTED,false));space(hero,4);
        if(snapshot.climate.has("value")) {
            double value=snapshot.climate.optDouble("value",Double.NaN);
            int col=value>=0.5?ORANGE:value<=-0.5?BLUE:TEAL;
            add(hero,text(num(value,1)+"°C",44,col,true));space(hero,5);
            add(hero,text(phaseName(snapshot.climate.optString("phase")),20,INK,true));
            space(hero,5);
            add(hero,text(snapshot.climate.optString("season")+"  "+snapshot.climate.optInt("year"),12,MUTED,false));
            space(hero,9);
            add(hero,chip(t(R.string.enso_pattern).replace("%1$s",snapshot.climate.optBoolean("sustained")?t(R.string.yes):t(R.string.no)),col,0xfff5f6f9));
        } else paragraph(hero,t(R.string.roni_unknown));
        rule(hero);paragraph(hero,t(R.string.roni_disclaimer));
        section(t(R.string.enso_prepare));
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
        String[] items={t(R.string.source_usgs),t(R.string.source_noaa),t(R.string.source_meteo)};
        String[] urls={"https://earthquake.usgs.gov/earthquakes/",HazardRepository.RONI,"https://open-meteo.com/"};
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
    private void showSettings() {
        section(t(R.string.preferences));
        LinearLayout general=card();
        settingAction(general,t(R.string.choose_location),city(),()->chooseCity());rule(general);
        settingAction(general,t(R.string.language),my()?t(R.string.burmese):t(R.string.english),()->chooseLanguage());
        rule(general);
        settingAction(general,t(R.string.notification_settings),t(R.string.notification_settings_help),()->openNotificationSettings());
        LinearLayout alerts=card();
        toggle(alerts,R.string.alert_eq,R.string.alert_eq_desc,"alert_eq",true);rule(alerts);
        toggle(alerts,R.string.alert_enso,R.string.alert_enso_desc,"alert_enso",true);rule(alerts);
        toggle(alerts,R.string.alert_air,R.string.alert_air_desc,"alert_air",true);rule(alerts);
        toggle(alerts,R.string.alert_weather,R.string.alert_weather_desc,"alert_weather",true);
        LinearLayout threshold=card();add(threshold,text(t(R.string.threshold),15,INK,true));space(threshold,12);
        LinearLayout magnitudes=horizontal();
        float[] choices={3.0f,4.0f,4.5f,5.0f,5.5f,6.0f};
        for(float choice:choices) {
            boolean chosen=Math.abs(Prefs.magnitude(this)-choice)<0.01;
            TextView v=chip(num(choice,1),chosen?WHITE:INK,chosen?TEAL:0xffeff3f6);
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
        TextView test=button("◉  "+t(R.string.test_alert),TEAL,WHITE);test.setOnClickListener(v->{Notifications.test(this);sayForegroundAlert();});tests.addView(test);
        space(tests,8);
        TextView voice=button("♫  "+t(R.string.test_voice),0xffe7f4f1,TEAL);voice.setOnClickListener(v->speak(t(R.string.voice_test_phrase)));tests.addView(voice);
        section(t(R.string.latest_alerts));showHistory(8);
        section(t(R.string.safety_title));LinearLayout safety=card();paragraph(safety,t(R.string.safety_text));
        section(t(R.string.data_sources));sources();
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
    private void chooseCity() {
        int current=Prefs.cityIndex(this);
        String[] names=my()?Cities.MY_NAMES:Cities.NAMES;
        new AlertDialog.Builder(this).setTitle(t(R.string.choose_location)).setSingleChoiceItems(names,current,(dialog,index)->{
            Prefs.get(this).edit().putInt("city",index)
              .remove("data_air").remove("data_weather").remove("time_air").remove("time_weather").apply();
            dialog.dismiss();snapshot=HazardRepository.snapshot(this);draw();refresh();
            Toast.makeText(this,R.string.city_changed,Toast.LENGTH_SHORT).show();
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
        new AlertDialog.Builder(this).setTitle(t(R.string.language))
            .setSingleChoiceItems(new String[]{"English","မြန်မာ"},my()?1:0,(dialog,which)->{
                Prefs.get(this).edit().putString("language",which==1?"my":"en").apply();dialog.dismiss();recreate();
            }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void configureSpeech() {
        if(tts==null || !voiceReady)return;
        Locale language=my()?new Locale("my","MM"):Locale.ENGLISH;
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
    private static class Ring extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int level,color;
        Ring(Context c,int aqi,int shade){super(c);level=aqi;color=shade;setContentDescription("US AQI "+aqi);}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);float w=getWidth(),h=getHeight(),stroke=Math.min(w,h)*0.095f;
            RectF box=new RectF(stroke,stroke,w-stroke,h-stroke);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(stroke);
            paint.setColor(0xffeaf0f2);canvas.drawArc(box,135,270,false,paint);
            paint.setColor(color);canvas.drawArc(box,135,Math.max(2,Math.min(270,level/300f*270)),false,paint);
            paint.setStyle(Paint.Style.FILL);paint.setColor(INK);paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));paint.setTextSize(w*0.29f);
            canvas.drawText(String.valueOf(level),w/2,h/2+stroke,paint);
        }
    }
}
