package mm.hazardguard;

import android.text.Html;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Public article headlines from BBC Burmese, DVB Burmese and The Irrawaddy.
 * Only verified publisher pages/feeds are used, no credentials or paid APIs.
 * BBC and The Irrawaddy provide RSS. DVB currently publishes a public HTML
 * listing rather than an RSS feed; limit parsing to /post/id dated articles.
 */
public final class BurmesePublisherFeed {
    private BurmesePublisherFeed() {}
    public static final String BBC = "https://feeds.bbci.co.uk/burmese/rss.xml";
    public static final String DVB = "https://burmese.dvb.no/";
    public static final String IRRAWADDY = "https://burma.irrawaddy.com/feed";
    private static final Pattern POST = Pattern.compile(
        "<a\\b[^>]*\\bhref=[\"'](/post/[0-9]+)[\"'][^>]*>(.*?)</a>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TITLE = Pattern.compile(
        "<(?:div|h[1-6])\\b[^>]*class=[\"'][^\"']*\\btext-(?:sm|lg|xl)[^\"']*[\"'][^>]*>(.*?)</(?:div|h[1-6])>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DATE = Pattern.compile(
        ">\\s*((?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+[0-9]{1,2},\\s+20[0-9]{2})\\s*<");

    public static String label(String publisher) {
        switch(publisher) {
            case "bbc":return "BBC News မြန်မာ";
            case "dvb":return "DVB သတင်း";
            case "irrawaddy":return "ဧရာဝတီ (The Irrawaddy)";
            default:return "Myanmar publisher";
        }
    }
    public static String website(String publisher) {
        switch(publisher) {
            case "bbc":return "https://www.bbc.com/burmese";
            case "dvb":return DVB;
            case "irrawaddy":return "https://burma.irrawaddy.com/";
            default:return "";
        }
    }
    public static ArrayList<JSONObject> fetch(String publisher) throws Exception {
        if("bbc".equals(publisher) || "irrawaddy".equals(publisher)) {
            String xml=download("bbc".equals(publisher)?BBC:IRRAWADDY);
            ArrayList<JSONObject> stories=WorldNewsFeed.parseRss(xml,"my",System.currentTimeMillis());
            for(JSONObject entry:stories)entry.put("source",label(publisher)).put("publisher",publisher);
            return stories;
        }
        if("dvb".equals(publisher))return parseDvbListing(download(DVB),System.currentTimeMillis());
        throw new IllegalArgumentException("Unknown publisher");
    }
    private static String download(String address) throws Exception {
        HttpURLConnection con=(HttpURLConnection)new URL(address).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(9000);
        con.setRequestProperty("User-Agent","Mozilla/5.0 (compatible; HazardGuardNews/8.0; RSS/headline reader)");
        con.setRequestProperty("Accept","application/rss+xml,application/xml,text/html;q=0.9");
        con.setInstanceFollowRedirects(true);
        try {
            if(con.getResponseCode()!=200)throw new java.io.IOException("Publisher HTTP "+con.getResponseCode());
            try(InputStream in=con.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] b=new byte[8192];int n;
                while((n=in.read(b))!=-1) {
                    out.write(b,0,n);
                    if(out.size()>1_500_000)throw new java.io.IOException("Publisher response too large");
                }
                return out.toString("UTF-8");
            }
        } finally {con.disconnect();}
    }
    static ArrayList<JSONObject> parseDvbListing(String html,long now) throws Exception {
        ArrayList<JSONObject> items=new ArrayList<>();
        HashSet<String> seen=new HashSet<>();
        if(html==null || html.length()>1_600_000)return items;
        Matcher found=POST.matcher(html);
        while(found.find() && items.size()<40) {
            String path=found.group(1);
            if(!seen.add(path))continue;
            String card=found.group(2);
            Matcher date=DATE.matcher(card);
            if(!date.find())continue; // Never present an undated item as a 72-hour report.
            long when=parseDate(date.group(1));
            if(when<now-WorldNewsFeed.WINDOW_MS || when>now+5L*60*1000)continue;
            Matcher titles=TITLE.matcher(card);
            if(!titles.find())continue;
            String name=Html.fromHtml(titles.group(1)).toString().replaceAll("\\s+"," ").trim();
            if(name.length()<18 || name.length()>450 || name.startsWith("http"))continue;
            String url=DVB.substring(0,DVB.length()-1)+path;
            items.add(new JSONObject().put("title",name).put("link",url)
                .put("source",label("dvb")).put("publisher","dvb")
                .put("language","my").put("time",when).put("dateOnly",true));
        }
        return items;
    }
    private static long parseDate(String raw) {
        try {
            SimpleDateFormat format=new SimpleDateFormat("MMMM d, yyyy",Locale.ENGLISH);
            format.setLenient(false);
            format.setTimeZone(TimeZone.getTimeZone("Asia/Yangon"));
            Date d=format.parse(raw);
            // Approximate date-time as noon because the site's listing shows date
            // only; dateOnly flag tells the UI never to claim an exact publish time.
            return d==null?0:d.getTime()+12L*3600_000L;
        } catch(Exception e){return 0;}
    }
}
