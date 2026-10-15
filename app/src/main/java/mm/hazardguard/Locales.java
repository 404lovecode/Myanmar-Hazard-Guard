package mm.hazardguard;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

public final class Locales {
    private Locales() {}
    public static Context wrap(Context context) {
        String code = Prefs.language(context);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(new Locale(code));
        return context.createConfigurationContext(config);
    }
    public static String text(Context c, int res) { return wrap(c).getString(res); }
    public static String fmt(Context c, int res, Object... values) { return wrap(c).getString(res, values); }
}
