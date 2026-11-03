package mm.hazardguard;

import java.net.URL;
import java.net.URLEncoder;

/** Safe, user-initiated links to public translation pages. No translation API calls. */
public final class NewsTranslation {
    private NewsTranslation() {}
    private static final String LANGS =
        "|en|my|th|es|fr|hi|id|de|pt|ar|ja|ko|zh|ru|it|tr|vi|nl|pl|uk|bn|ta|ur|";
    public static String target(String code) {
        if(code==null)return "en";
        String value=code.trim().toLowerCase(java.util.Locale.ROOT);
        return LANGS.contains("|"+value+"|")?value:"en";
    }
    private static String encode(String text) {
        try {return URLEncoder.encode(text,"UTF-8");}
        catch(Exception ex) {return "";}
    }
    public static String headlineUrl(String headline,String to) {
        if(headline==null || headline.trim().isEmpty())return "";
        String snippet=headline.trim();
        if(snippet.length()>750)snippet=snippet.substring(0,750);
        return "https://translate.google.com/?sl=auto&tl="+encode(target(to))
            +"&text="+encode(snippet)+"&op=translate";
    }
    public static String websiteUrl(String url,String to) {
        try {
            if(url==null || url.length()>1600)return "";
            URL parsed=new URL(url);
            if(!"https".equalsIgnoreCase(parsed.getProtocol()) ||
                parsed.getHost()==null || !parsed.getHost().contains(".") ||
                parsed.getUserInfo()!=null)return "";
            return "https://translate.google.com/translate?sl=auto&tl="
                +encode(target(to))+"&u="+encode(url);
        } catch(Exception ex) {return "";}
    }
}
