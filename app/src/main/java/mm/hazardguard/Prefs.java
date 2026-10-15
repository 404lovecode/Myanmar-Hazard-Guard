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
    public static String language(Context c) { return get(c).getString("language", "my"); }
}
