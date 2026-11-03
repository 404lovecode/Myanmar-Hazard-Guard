package mm.hazardguard;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private Prefs() {}
    public static SharedPreferences get(Context c) { return c.getApplicationContext().getSharedPreferences("hazardguard_v1", Context.MODE_PRIVATE); }
    public static boolean enabled(Context c, String key) { return get(c).getBoolean(key, true); }
    public static boolean voice(Context c) { return get(c).getBoolean("voice", false); }
    public static boolean sound(Context c) { return get(c).getBoolean("sound", true); }
    public static boolean vibration(Context c) { return get(c).getBoolean("vibration", true); }
    public static float magnitude(Context c) { return get(c).getFloat("magnitude", 4.5f); }
    public static int cityIndex(Context c) { return Math.max(0, Math.min(Cities.NAMES.length - 1, get(c).getInt("city", 0))); }
    public static boolean isCustom(Context c) { return !get(c).getString("custom_name", "").isEmpty(); }
    public static double latitude(Context c) { return isCustom(c) ? Double.longBitsToDouble(get(c).getLong("custom_lat", Double.doubleToLongBits(16.8409))) : Cities.LAT[cityIndex(c)]; }
    public static double longitude(Context c) { return isCustom(c) ? Double.longBitsToDouble(get(c).getLong("custom_lon", Double.doubleToLongBits(96.1735))) : Cities.LON[cityIndex(c)]; }
    public static String location(Context c, boolean my) { return isCustom(c) ? get(c).getString("custom_name","Yangon") : Cities.name(cityIndex(c), my); }
    public static String locationKey(Context c) { return String.format(java.util.Locale.US,"%.4f,%.4f",latitude(c),longitude(c)); }
    public static void saveCustom(Context c,String name,double lat,double lon) {
        get(c).edit().putString("custom_name",name).putLong("custom_lat",Double.doubleToLongBits(lat))
            .putLong("custom_lon",Double.doubleToLongBits(lon)).putString("custom_country", "").putBoolean("using_device_location",false).apply();
    }
    public static String language(Context c) { return Languages.resolve(get(c).getString("language", "auto"), countryCode(c)); }
    public static String languageSetting(Context c) { return get(c).getString("language", "auto"); }
    public static String theme(Context c) { return get(c).getString("theme", "system"); }
    public static String countryCode(Context c) {
        if(isCustom(c)) return get(c).getString("custom_country", "");
        return Languages.countryForPreset(Cities.COUNTRY[cityIndex(c)]);
    }
    public static void saveCustom(Context c,String name,double lat,double lon,String iso,boolean fromDevice) {
        get(c).edit().putString("custom_name",name)
            .putLong("custom_lat",Double.doubleToLongBits(lat))
            .putLong("custom_lon",Double.doubleToLongBits(lon))
            .putString("custom_country",iso==null?"":iso.toUpperCase(java.util.Locale.US))
            .putBoolean("using_device_location",fromDevice).apply();
    }
}
