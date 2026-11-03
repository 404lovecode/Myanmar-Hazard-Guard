package mm.hazardguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Automatic INLINE headline translation using the documented, no-key MyMemory API.
 * Sends only public article headline text. Never sends the user's location,
 * contacts or personal information. Offline cached translations remain readable.
 *
 * Anonymous provider limit: 5,000 characters/day per public IP and max 500 UTF-8
 * bytes per request. Our local allowance is smaller and cannot override provider
 * limits. We never pretend a failed translation succeeded.
 */
public final class HeadlineTranslationService {
    private HeadlineTranslationService() {}
    private static final String PREF_NAME="hazardguard_inline_translation_v1";
    private static final int BUDGET_CHARS=4400;
    private static final long MILLIS_DAY=86_400_000L;
    private static final Object BUDGET_LOCK=new Object();

    public static final class Result {
        public final boolean ok;
        public final String text;
        public final String problem;
        private Result(boolean success,String value,String message) {
            ok=success;text=value;problem=message;
        }
        public static Result success(String value){return new Result(true,value,"");}
        public static Result failed(String message){return new Result(false,"",message);}
    }

    private static SharedPreferences store(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME,Context.MODE_PRIVATE);
    }
    public static String key(String headline,String from,String to) {
        String seed=TranslationRules.code(from)+"|"+TranslationRules.code(to)+"|"+(headline==null?"":headline);
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder s=new StringBuilder("translated_");
            for(int i=0;i<16;i++)s.append(String.format(Locale.US,"%02x",digest[i]&0xff));
            return s.toString();
        } catch(Exception e) {return "translated_"+Integer.toHexString(seed.hashCode());}
    }
    public static String cached(Context c,String cacheKey) {
        SharedPreferences p=store(c);
        long updated=p.getLong(cacheKey+"_at",0);
        if(updated<=0 || System.currentTimeMillis()-updated>21L*MILLIS_DAY)return "";
        return p.getString(cacheKey,"");
    }
    private static String encode(String s) throws Exception {return URLEncoder.encode(s,"UTF-8");}

    public static Result translate(Context c,String title,String from,String to) {
        if(title==null || title.trim().isEmpty())return Result.failed("Empty headline");
        String source=NewsTranslation.target(from);
        String target=NewsTranslation.target(to);
        if(source.equals(target))return Result.success(title);
        String cacheKey=key(title,source,target);
        String old=cached(c,cacheKey);
        if(!old.isEmpty())return Result.success(old);

        // Limit input by BYTES, not characters: Myanmar script uses multi-byte UTF-8.
        String segment=TranslationRules.fitUtf8(title.trim(),470);
        int bytes=segment.getBytes(StandardCharsets.UTF_8).length;
        if(bytes==0 || bytes>500)return Result.failed("Headline cannot be translated");
        final int cost=segment.codePointCount(0,segment.length());
        SharedPreferences p=store(c);
        synchronized(BUDGET_LOCK) {
            long day=System.currentTimeMillis()/MILLIS_DAY;
            int used=p.getLong("day",0)==day?p.getInt("used",0):0;
            if(used+cost>BUDGET_CHARS)
                return Result.failed("Free translation limit reached today");
            p.edit().putLong("day",day).putInt("used",used+cost).commit();
        }
        HttpURLConnection connection=null;
        try {
            // Public documented endpoint. This is the ONLY automatic translation
            // network request; it does not open an external app or WebView.
            String url="https://api.mymemory.translated.net/get?q="+encode(segment)
                +"&langpair="+encode(TranslationRules.code(source)+"|"+TranslationRules.code(target))
                +"&mt=1";
            connection=(HttpURLConnection)new URL(url).openConnection();
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(8500);
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("User-Agent","MyanmarHazardGuard/7.0 (public-headline-reader)");
            int status=connection.getResponseCode();
            if(status==429)return Result.failed("Translation quota or rate limit reached");
            if(status!=200)return Result.failed("Translation service unavailable");
            try(InputStream in=connection.getInputStream();
                ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096];int n;
                while((n=in.read(buffer))!=-1) {
                    out.write(buffer,0,n);
                    if(out.size()>55_000)throw new java.io.IOException("Oversized response");
                }
                JSONObject json=new JSONObject(out.toString("UTF-8"));
                int providerStatus=json.optInt("responseStatus",0);
                if(providerStatus!=200) {
                    String detail=json.optString("responseDetails","");
                    return Result.failed(detail.toLowerCase(Locale.ROOT).contains("limit")?
                        "Translation daily quota reached":"Translation not available");
                }
                JSONObject data=json.optJSONObject("responseData");
                String returned=data==null?"":data.optString("translatedText","");
                String result=Html.fromHtml(returned).toString().trim();
                if(result.isEmpty() || result.toLowerCase(Locale.ROOT).startsWith("mymemory warning"))
                    return Result.failed("Translation not available");
                // An unchanged title from a different source language is not
                // evidence of a successful translation.
                if(result.equalsIgnoreCase(segment))
                    return Result.failed("No translated text returned");
                p.edit().putString(cacheKey,result)
                    .putLong(cacheKey+"_at",System.currentTimeMillis()).apply();
                return Result.success(result);
            }
        } catch(Exception e) {
            return Result.failed("Unable to translate while offline or service unavailable");
        } finally {if(connection!=null)connection.disconnect();}
    }
}
