package mm.hazardguard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class HazardRepository {
    private HazardRepository() {}
    public static final String USGS = "https://earthquake.usgs.gov/fdsnws/event/1/query";
    public static final String RONI = "https://www.cpc.ncep.noaa.gov/products/analysis_monitoring/enso/roni/";
    public static final String WEATHER = "https://api.open-meteo.com/v1/forecast";
    public static final String AIR = "https://air-quality-api.open-meteo.com/v1/air-quality";
    public static final String EONET = "https://eonet.gsfc.nasa.gov/api/v3/events?status=open&limit=100";
    public static final String GDACS = "https://www.gdacs.org/xml/rss.xml";
    public static final String GDACS_BACKUP = "https://data.gdacs.org/xml/rss_7d.xml";
    private static final String[] SOURCE_NAMES={"USGS regional","Open-Meteo weather","Open-Meteo air","NOAA RONI","USGS worldwide","NASA EONET","GDACS news"};
    public static final String GEOCODE = "https://geocoding-api.open-meteo.com/v1/search";

    public static final class Snapshot {
        public JSONObject quake, globalQuake, events, weather, air, climate, news;
        public long quakeAt, globalQuakeAt, eventsAt, weatherAt, airAt, climateAt, newsAt, checkedAt;
        public boolean newQuake, newWorldQuake, newEvents, newWeather, newAir, newClimate, newNews;
        public int failed, succeeded;
        public JSONArray sourceReport = new JSONArray();
        public long lastUpdate() { return Math.max(Math.max(Math.max(quakeAt,weatherAt),Math.max(airAt,climateAt)), Math.max(Math.max(eventsAt,globalQuakeAt),newsAt)); }
    }

    public static JSONObject cached(Context c, String key) {
        try { return new JSONObject(Prefs.get(c).getString("data_" + key, "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }
    public static Snapshot snapshot(Context c) {
        SharedPreferences p = Prefs.get(c);
        Snapshot s = new Snapshot();
        s.quake = scoped(c,"quake"); s.weather = scoped(c,"weather");
        s.globalQuake = cached(c,"global_quake"); s.events=cached(c,"events"); s.news=cached(c,"news");
        s.air = scoped(c,"air"); s.climate = cached(c,"climate");
        s.quakeAt = scopedTime(c,"quake"); s.weatherAt = scopedTime(c,"weather");
        s.globalQuakeAt = p.getLong("time_global_quake",0); s.eventsAt=p.getLong("time_events",0); s.newsAt=p.getLong("time_news",0);
        s.airAt = scopedTime(c,"air"); s.climateAt = p.getLong("time_climate",0);
        s.checkedAt = p.getLong("time_checked",0);
        try {s.sourceReport = new JSONArray(p.getString("source_report","[]"));}
        catch(Exception ignored) {s.sourceReport = new JSONArray();}
        s.succeeded=p.getInt("source_success_count",0);
        s.failed=p.getInt("source_failure_count",0);
        return s;
    }
    private static void save(Context c, String key, JSONObject data) {
        Prefs.get(c).edit().putString("data_" + key, data.toString()).putLong("time_" + key,System.currentTimeMillis()).apply();
    }
    private static JSONObject scoped(Context c,String key) {
        return Prefs.locationKey(c).equals(Prefs.get(c).getString("location_"+key,"")) ? cached(c,key) : new JSONObject();
    }
    private static long scopedTime(Context c,String key) {
        return Prefs.locationKey(c).equals(Prefs.get(c).getString("location_"+key,"")) ? Prefs.get(c).getLong("time_"+key,0) : 0;
    }
    /** Prevent older asynchronous requests from overwriting new-location data. */
    private static boolean saveScoped(Context c,String key,JSONObject result,String locationKey) {
        if(!locationKey.equals(Prefs.locationKey(c))) return false;
        Prefs.get(c).edit().putString("data_"+key,result.toString())
            .putString("location_"+key,locationKey).putLong("time_"+key,System.currentTimeMillis()).apply();
        return true;
    }
    public static Snapshot update(Context c, boolean notify) {
        final Context app=c.getApplicationContext();
        final double lat=Prefs.latitude(app), lon=Prefs.longitude(app);
        final String selected=Prefs.locationKey(app);
        ExecutorService pool=Executors.newFixedThreadPool(7);
        ArrayList<Future<Boolean>> tasks=new ArrayList<>();
        final String[] results=new String[SOURCE_NAMES.length];
        tasks.add(pool.submit(() -> { try {
            boolean saved=saveScoped(app,"quake",new JSONObject(fetch(quakeUrl(lat,lon))),selected);
            results[0]=saved?"ok":"changed";return saved;
        }catch(Exception e){results[0]=shortError(e);return false;} }));
        tasks.add(pool.submit(() -> { try {
            boolean saved=saveScoped(app,"weather",new JSONObject(fetch(weatherUrl(lat,lon))),selected);
            results[1]=saved?"ok":"changed";return saved;
        }catch(Exception e){results[1]=shortError(e);return false;} }));
        tasks.add(pool.submit(() -> { try {
            boolean saved=saveScoped(app,"air",new JSONObject(fetch(airUrl(lat,lon))),selected);
            results[2]=saved?"ok":"changed";return saved;
        }catch(Exception e){results[2]=shortError(e);return false;} }));
        tasks.add(pool.submit(() -> { try {
            if(System.currentTimeMillis()-Prefs.get(app).getLong("time_climate",0) < 6*3600_000L) {
                results[3]="cached";return true;
            }
            save(app,"climate",parseRoni(fetch(RONI)));results[3]="ok";return true;
        }catch(Exception e){results[3]=shortError(e);return false;} }));
        tasks.add(pool.submit(() -> { try {
            save(app,"global_quake",new JSONObject(fetch(globalQuakeUrl())));
            results[4]="ok";return true;
        }catch(Exception e){results[4]=shortError(e);return false;} }));
        tasks.add(pool.submit(() -> { try {
            save(app,"events",new JSONObject(fetch(EONET)));
            results[5]="ok";return true;
        }catch(Exception e){results[5]=shortError(e);return false;} }));
        tasks.add(pool.submit(() -> { try {
            if(System.currentTimeMillis()-Prefs.get(app).getLong("time_news",0)<30*60_000L) {
                results[6]="cached";return true;
            }
            JSONObject news;
            try { news=GdacsFeed.parse(fetch(GDACS)); }
            catch(Exception first) {
                // Official alternate feed, if the primary feed is unavailable or malformed.
                try {news=GdacsFeed.parse(fetch(GDACS_BACKUP));}
                catch(Exception second) {second.addSuppressed(first);throw second;}
            }
            save(app,"news",news);results[6]="ok";return true;
        }catch(Exception e){results[6]=shortError(e);return false;} }));
        pool.shutdown();
        boolean[] successes=new boolean[tasks.size()];
        for(int i=0;i<tasks.size();i++) {
            try {successes[i]=tasks.get(i).get(24,TimeUnit.SECONDS);}
            catch(Exception e){tasks.get(i).cancel(true);successes[i]=false;results[i]=shortError(e);}
        }
        pool.shutdownNow();
        JSONArray report=new JSONArray();
        int ok=0,failed=0;
        for(int i=0;i<successes.length;i++) {
            if(successes[i])ok++;else failed++;
            JSONObject row=new JSONObject();
            try {
                row.put("source",SOURCE_NAMES[i]);
                row.put("status",successes[i]?("cached".equals(results[i])?"cached":"ok"):"failed");
                if(!successes[i])row.put("error",results[i]==null?"Timed out":results[i]);
            }catch(Exception ignored){}
            report.put(row);
        }
        Prefs.get(app).edit().putLong("time_checked",System.currentTimeMillis())
            .putString("source_report",report.toString())
            .putInt("source_success_count",ok).putInt("source_failure_count",failed).apply();
        Snapshot s=snapshot(app);
        s.newQuake=successes[0]; s.newWeather=successes[1];s.newAir=successes[2];
        s.newClimate=successes[3];s.newWorldQuake=successes[4];s.newEvents=successes[5];s.newNews=successes[6];
        // Only earthquakes near the chosen location trigger earthquake notifications.
        if(notify)AlertEngine.evaluate(app,s);
        return s;
    }
    private static String since48h() {
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(System.currentTimeMillis()-48L*3600_000L));
    }
    private static String quakeUrl(double lat,double lon) {
        return USGS+"?format=geojson&starttime="+since48h()+"&latitude="+lat+"&longitude="+lon
            +"&maxradiuskm=600&minmagnitude=2.5&orderby=time&limit=120";
    }
    private static String globalQuakeUrl() {
        return USGS+"?format=geojson&starttime="+since48h()+"&minmagnitude=4.5&orderby=time&limit=150";
    }
    private static String weatherUrl(double lat,double lon) {
        return WEATHER+"?latitude="+lat+"&longitude="+lon
          +"&current=temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m,weather_code"
          +"&hourly=precipitation,wind_speed_10m&forecast_hours=6&timezone=auto";
    }
    private static String airUrl(double lat,double lon) {
        return AIR+"?latitude="+lat+"&longitude="+lon
          +"&current=us_aqi,pm2_5,pm10&timezone=auto";
    }
    /** Search city names anywhere in the world (no API key; requires network). */
    public static JSONArray places(String query) throws Exception {
        String url=GEOCODE+"?name="+java.net.URLEncoder.encode(query,"UTF-8")+"&count=12&language=en&format=json";
        JSONObject result=new JSONObject(fetch(url));
        JSONArray places=result.optJSONArray("results");return places==null?new JSONArray():places;
    }
    private static String shortError(Exception e) {
        Throwable cause=e;
        while(cause.getCause()!=null && cause.getCause()!=cause) cause=cause.getCause();
        if(cause instanceof java.net.SocketTimeoutException || cause instanceof java.util.concurrent.TimeoutException) return "Timeout";
        if(cause instanceof java.net.UnknownHostException) return "DNS unavailable";
        if(cause instanceof javax.net.ssl.SSLException) return "TLS connection failed";
        String message=cause.getMessage();
        if(message!=null && message.matches("HTTP [0-9]{3}"))return message;
        if(cause instanceof org.json.JSONException)return "Invalid data";
        if(cause instanceof java.io.IOException)return "Network error";
        return cause.getClass().getSimpleName();
    }
    private static String fetch(String url) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
        connection.setRequestProperty("Accept","application/json,application/rss+xml,application/xml,text/html;q=0.8");
        connection.setRequestProperty("Accept-Encoding","gzip");
        connection.setRequestProperty("User-Agent","MyanmarHazardGuard/1.0 public-data-reader");
        connection.setInstanceFollowRedirects(true);
        try {
            if(connection.getResponseCode()!=200) throw new java.io.IOException("HTTP "+connection.getResponseCode());
            InputStream stream=connection.getInputStream();
            if("gzip".equalsIgnoreCase(connection.getContentEncoding()))stream=new GZIPInputStream(stream);
            try(InputStream in=stream; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192]; int len;
                while((len=in.read(buffer))!=-1) {
                    out.write(buffer,0,len);
                    if(out.size()>6_000_000) throw new java.io.IOException("Response too large");
                }
                return out.toString("UTF-8");
            }
        } finally { connection.disconnect(); }
    }
    static JSONObject parseRoni(String html) throws Exception {
        RoniParser.Reading r=RoniParser.parse(html);
        return new JSONObject().put("value",r.value).put("year",r.year)
            .put("season",r.season).put("phase",r.phase)
            .put("consecutive",r.consecutive).put("sustained",r.sustained)
            .put("source",RONI);
    }
    public static double number(JSONObject o, String key) { return o==null ? Double.NaN : o.optDouble(key,Double.NaN); }
    public static double maxFirst(JSONArray a, int n) {
        if(a==null || a.length()==0) return Double.NaN;
        double v=Double.NaN;
        for(int i=0;i<Math.min(n,a.length());i++) {
            double x=a.optDouble(i,Double.NaN);
            if(!Double.isNaN(x)) v=Double.isNaN(v)?x:Math.max(v,x);
        }
        return v;
    }
}
