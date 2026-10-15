package mm.hazardguard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Locale;

public final class AlertEngine {
    private AlertEngine() {}
    private static final long HOUR=3600_000L;

    public static synchronized void evaluate(Context c, HazardRepository.Snapshot s) {
        SharedPreferences p=Prefs.get(c);
        long now=System.currentTimeMillis();
        if(s.newQuake && Prefs.enabled(c,"alert_eq")) {
            JSONArray array=s.quake.optJSONArray("features");
            if(array!=null) {
                HashSet<String> seen=new HashSet<>();
                JSONArray old;
                try {old=new JSONArray(p.getString("quake_seen","[]"));}catch(Exception e){old=new JSONArray();}
                for(int i=0;i<old.length();i++) seen.add(old.optString(i));
                JSONArray combined=new JSONArray();
                // Add new event IDs, even if below threshold: magnitude revisions must not endlessly notify.
                for(int i=0;i<array.length();i++) {
                    JSONObject event=array.optJSONObject(i); if(event==null) continue;
                    String id=event.optString("id",""); if(id.isEmpty()) continue;
                    JSONObject props=event.optJSONObject("properties"); if(props==null) continue;
                    long time=props.optLong("time",0);
                    if(time<=0 || time>now+5*60_000L || now-time>48*HOUR) continue;
                    if(combined.length()<160) combined.put(id);
                    double mag=props.optDouble("mag",Double.NaN);
                    if(!seen.contains(id) && now-time<=30*60_000L && mag>=Prefs.magnitude(c)) {
                        String place=props.optString("place","Myanmar region");
                        String body=Locales.fmt(c,R.string.earthquake_notification_body,String.format(Locale.US,"%.1f",mag),place);
                        Notifications.post(c,"quake_"+id,Locales.text(c,R.string.earthquake_notification),body,true);
                    }
                    seen.add(id);
                }
                p.edit().putString("quake_seen",combined.toString()).apply();
            }
        }
        if(s.newAir && Prefs.enabled(c,"alert_air")) {
            JSONObject current=s.air.optJSONObject("current");
            int aqi=current==null ? -1 : current.optInt("us_aqi",-1);
            int city=Prefs.cityIndex(c);
            if(aqi>=151 && (now-p.getLong("last_air_"+city,0)>6*HOUR)) {
                Notifications.advisory(c,"air_"+city,Locales.text(c,R.string.aqi_notification),
                    Locales.fmt(c,R.string.aqi_notification_body,aqi,Cities.name(city,"my".equals(Prefs.language(c)))));
                p.edit().putLong("last_air_"+city,now).apply();
            }
        }
        if(s.newWeather && Prefs.enabled(c,"alert_weather")) {
            JSONObject cur=s.weather.optJSONObject("current");
            JSONObject hours=s.weather.optJSONObject("hourly");
            double maxRain=HazardRepository.maxFirst(hours==null?null:hours.optJSONArray("precipitation"),6);
            double maxWind=HazardRepository.maxFirst(hours==null?null:hours.optJSONArray("wind_speed_10m"),6);
            if(cur!=null) {
                double currentWind=cur.optDouble("wind_speed_10m",Double.NaN);
                if(!Double.isNaN(currentWind)) maxWind=Double.isNaN(maxWind)?currentWind:Math.max(maxWind,currentWind);
            }
            int city=Prefs.cityIndex(c);
            if((maxRain>=15 || maxWind>=60) && now-p.getLong("last_weather_"+city,0)>6*HOUR) {
                String cityName=Cities.name(city,"my".equals(Prefs.language(c)));
                String body=Locales.fmt(c,R.string.weather_notification_body,cityName,
                    fmt(maxRain),fmt(maxWind));
                Notifications.advisory(c,"weather_"+city,Locales.text(c,R.string.weather_notification),body);
                p.edit().putLong("last_weather_"+city,now).apply();
            }
        }
        if(s.newClimate && Prefs.enabled(c,"alert_enso")) {
            String phase=s.climate.optString("phase","neutral");
            String old=p.getString("last_climate_phase","");
            if(!phase.equals(old)) {
                if(!old.isEmpty() && !phase.equals("neutral")) {
                    int res=phase.equals("warm")?R.string.phase_warm:R.string.phase_cool;
                    Notifications.post(c,"enso_"+s.climate.optInt("year")+"_"+s.climate.optString("season"),
                       Locales.text(c,R.string.enso_notification),
                       Locales.fmt(c,R.string.enso_notification_body,Locales.text(c,res)),false);
                }
                p.edit().putString("last_climate_phase",phase).apply();
            }
        }
    }
    private static String fmt(double val) {return Double.isNaN(val)?"—":String.format(Locale.US,"%.0f",val);}
}
