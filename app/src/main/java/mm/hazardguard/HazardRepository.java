package mm.hazardguard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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

    public static final class Snapshot {
        public JSONObject quake, weather, air, climate;
        public long quakeAt, weatherAt, airAt, climateAt, checkedAt;
        public boolean newQuake, newWeather, newAir, newClimate;
        public int failed;
        public long lastUpdate() { return Math.max(Math.max(quakeAt, weatherAt), Math.max(airAt, climateAt)); }
    }

    public static JSONObject cached(Context c, String key) {
        try { return new JSONObject(Prefs.get(c).getString("data_" + key, "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }
    public static Snapshot snapshot(Context c) {
        SharedPreferences p = Prefs.get(c);
        Snapshot s = new Snapshot();
        s.quake = cached(c,"quake"); s.weather = cached(c,"weather");
        s.air = cached(c,"air"); s.climate = cached(c,"climate");
        s.quakeAt = p.getLong("time_quake",0); s.weatherAt = p.getLong("time_weather",0);
        s.airAt = p.getLong("time_air",0); s.climateAt = p.getLong("time_climate",0);
        s.checkedAt = p.getLong("time_checked",0);
        return s;
    }
    private static void save(Context c, String key, JSONObject data) {
        Prefs.get(c).edit().putString("data_" + key, data.toString()).putLong("time_" + key,System.currentTimeMillis()).apply();
    }
    public static Snapshot update(Context c, boolean notify) {
        final Context app = c.getApplicationContext();
        final int index = Prefs.cityIndex(app);
        // Four independent calls; a failed provider never overwrites successful cached data.
        ExecutorService pool = Executors.newFixedThreadPool(4);
        ArrayList<Future<Boolean>> tasks = new ArrayList<>();
        tasks.add(pool.submit(new Callable<Boolean>() { public Boolean call() {
            try { save(app,"quake", new JSONObject(fetch(quakeUrl()))); return true; } catch(Exception ex) { return false; }
        }}));
        tasks.add(pool.submit(new Callable<Boolean>() { public Boolean call() {
            try { save(app,"weather", new JSONObject(fetch(weatherUrl(index)))); return true; } catch(Exception ex) { return false; }
        }}));
        tasks.add(pool.submit(new Callable<Boolean>() { public Boolean call() {
            try { save(app,"air", new JSONObject(fetch(airUrl(index)))); return true; } catch(Exception ex) { return false; }
        }}));
        tasks.add(pool.submit(new Callable<Boolean>() { public Boolean call() {
            try { JSONObject climate = parseRoni(fetch(RONI)); save(app,"climate",climate); return true; } catch(Exception ex) { return false; }
        }}));
        pool.shutdown();
        boolean[] successes = new boolean[4];
        for (int i=0; i<4; i++) {
            try { successes[i] = tasks.get(i).get(13,TimeUnit.SECONDS); }
            catch(Exception e) { tasks.get(i).cancel(true); successes[i] = false; }
        }
        pool.shutdownNow();
        Prefs.get(app).edit().putLong("time_checked",System.currentTimeMillis()).apply();
        Snapshot s = snapshot(app);
        s.newQuake = successes[0]; s.newWeather=successes[1]; s.newAir=successes[2]; s.newClimate=successes[3];
        for(boolean ok : successes) if(!ok) s.failed++;
        if(notify) AlertEngine.evaluate(app, s);
        return s;
    }
    private static String quakeUrl() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String start = fmt.format(new Date(System.currentTimeMillis() - 48L*3600_000L));
        return USGS + "?format=geojson&starttime=" + start + "&minlatitude=9&maxlatitude=29&minlongitude=90&maxlongitude=104&orderby=time&limit=80";
    }
    private static String weatherUrl(int i) {
        return WEATHER+"?latitude="+Cities.LAT[i]+"&longitude="+Cities.LON[i]
          +"&current=temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m,weather_code"
          +"&hourly=precipitation,wind_speed_10m&forecast_hours=6&timezone=Asia%2FYangon";
    }
    private static String airUrl(int i) {
        return AIR+"?latitude="+Cities.LAT[i]+"&longitude="+Cities.LON[i]
          +"&current=us_aqi,pm2_5,pm10&timezone=Asia%2FYangon";
    }
    private static String fetch(String url) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
        connection.setRequestProperty("Accept","application/json,text/html;q=0.9");
        connection.setRequestProperty("User-Agent","MyanmarHazardGuard/1.0 public-data-reader");
        connection.setInstanceFollowRedirects(true);
        try {
            if(connection.getResponseCode()!=200) throw new java.io.IOException("HTTP "+connection.getResponseCode());
            try(InputStream in=connection.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192]; int len;
                while((len=in.read(buffer))!=-1) {
                    out.write(buffer,0,len);
                    if(out.size()>2_500_000) throw new java.io.IOException("Response too large");
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
