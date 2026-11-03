package mm.hazardguard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Public, non-emergency international headlines. Never presents stories as official warnings. */
public final class WorldNewsFeed {
    private WorldNewsFeed() {}
    public static final long WINDOW_MS = 72L * 60 * 60 * 1000;
    private static final long CACHE_MS = 10L * 60 * 1000;
    public static final String[] TOPICS = {"world", "protests", "disasters", "food", "health", "technology", "climate"};
    public static final String[] TOPIC_NAMES = {
        "World headlines", "Protests & civic events", "Disasters", "Food & humanitarian", "Health", "Technology", "Climate"
    };
    public static final String[] SOURCES = {"global","myanmar","bbc","dvb","irrawaddy"};
    public static final String[] SOURCE_NAMES = {
        "🌐 World / All publishers",
        "🇲🇲 Myanmar News · BBC + DVB + Irrawaddy",
        "BBC News မြန်မာ",
        "DVB သတင်း",
        "ဧရာဝတီ · The Irrawaddy"
    };
    public static int sourceIndex(String code) {
        for(int i=0;i<SOURCES.length;i++)if(SOURCES[i].equals(code))return i;
        return 0;
    }
    public static String source(String code) {return SOURCES[sourceIndex(code)];}
    // Public Google News editions; an edition may be temporarily unavailable.
    public static final String[] LANGS = {
        "mix","en","my","th","es","fr","hi","id","de","pt","ar","ja","ko","zh",
        "ru","it","tr","vi","nl","pl","uk","bn","ta","ur"
    };
    public static final String[] NAMES = {
        "Multilingual mix (8 editions)","English","မြန်မာ (Burmese)","ไทย (Thai)","Español",
        "Français","हिन्दी","Bahasa Indonesia","Deutsch","Português","العربية","日本語",
        "한국어","中文","Русский","Italiano","Türkçe","Tiếng Việt","Nederlands",
        "Polski","Українська","বাংলা","தமிழ்","اردو"
    };
    public static final String[] EDITIONS = {
        "","US:en","MM:my","TH:th","ES:es","FR:fr","IN:hi","ID:id","DE:de","BR:pt-419",
        "SA:ar","JP:ja","KR:ko","TW:zh-Hant","RU:ru","IT:it","TR:tr","VN:vi",
        "NL:nl","PL:pl","UA:uk","BD:bn","IN:ta","PK:ur"
    };
    public static final String[] HLS = {
        "","en-US","my","th","es","fr","hi","id","de","pt-BR","ar","ja","ko",
        "zh-TW","ru","it","tr","vi","nl","pl","uk","bn","ta","ur"
    };
    private static final String[] MIX = {"en","my","th","es","fr","hi","ar","ja"};
    private static final String[] MULTI_TOPIC_KEYWORDS = {
        "world international", "protest demonstration strike", "earthquake flood cyclone wildfire disaster",
        "food security hunger shortages humanitarian aid", "health disease outbreak medicine",
        "technology research software", "climate weather extreme heat environment"
    };
    public static int langIndex(String code) {
        for(int i=0;i<LANGS.length;i++)if(LANGS[i].equals(code))return i;
        return 1;
    }
    public static int topicIndex(String topic) {
        for(int i=0;i<TOPICS.length;i++)if(TOPICS[i].equals(topic))return i;
        return 0;
    }
    public static String feedUrl(String language, String topic) {
        int index=langIndex(language);
        if(index==0)index=1;
        String edition=EDITIONS[index];
        String country=edition.substring(0, edition.indexOf(':'));
        String locale=edition.substring(edition.indexOf(':')+1);
        String hl=HLS[index];
        String suffix="&hl="+encode(hl)+"&gl="+encode(country)+"&ceid="+encode(country+":"+locale);
        int category=topicIndex(topic);
        if(category==0) {
            return "https://news.google.com/rss/headlines/section/topic/WORLD?hl="+encode(hl)+"&gl="+encode(country)+"&ceid="+encode(country+":"+locale);
        }
        String keyword=MULTI_TOPIC_KEYWORDS[category];
        if(category==1){
            if(language.equals("my"))keyword="ဆန္ဒပြမှု";
            else if(language.equals("th"))keyword="ประท้วง";
            else if(language.equals("fr"))keyword="manifestation grève";
            else if(language.equals("es"))keyword="protestas manifestaciones";
            else if(language.equals("hi"))keyword="विरोध प्रदर्शन";
            else if(language.equals("ar"))keyword="احتجاجات مظاهرات";
            else if(language.equals("ja"))keyword="抗議 デモ";
        }
        return "https://news.google.com/rss/search?q="+encode(keyword+" when:3d")+suffix;
    }
    private static String encode(String text) {
        try{return URLEncoder.encode(text,"UTF-8");}catch(Exception ex){return text;}
    }
    private static String storageKey(String lang,String topic,String publisher) {
        // Preserve v5/v6/v7 global-news cache.
        if("global".equals(publisher))return "world_news_v5_"+lang+"_"+topic;
        return "world_news_v8_"+publisher+"_"+lang+"_"+topic;
    }
    public static JSONObject cached(Context context,String lang,String topic) {
        return cached(context,lang,topic,"global");
    }
    public static JSONObject cached(Context context,String lang,String topic,String publisher) {
        String key=storageKey(lang,topic,source(publisher));
        try{return new JSONObject(context.getSharedPreferences("hazardguard_v1",Context.MODE_PRIVATE).getString(key,"{}"));}
        catch(Exception ignored){return new JSONObject();}
    }
    public static JSONObject load(Context context,String lang,String topic,boolean force) {
        return load(context,lang,topic,"global",force);
    }
    public static JSONObject load(Context context,String lang,String topic,String publisher,boolean force) {
        publisher=source(publisher);
        SharedPreferences prefs=context.getApplicationContext().getSharedPreferences("hazardguard_v1",Context.MODE_PRIVATE);
        JSONObject saved=cached(context,lang,topic,publisher);
        long previous=saved.optLong("updatedAt",0);
        if(!force && previous>0 && System.currentTimeMillis()-previous<CACHE_MS) return saved;
        try {
            ArrayList<JSONObject> results=new ArrayList<>();
            int success=0, expected=1;
            if(!"global".equals(publisher)) {
                String[] outlets="myanmar".equals(publisher)?
                    new String[]{"bbc","dvb","irrawaddy"}:
                    new String[]{publisher};
                expected=outlets.length;
                ExecutorService pool=Executors.newFixedThreadPool(outlets.length);
                ArrayList<Future<ArrayList<JSONObject>>> tasks=new ArrayList<>();
                for(String outlet:outlets)tasks.add(pool.submit(new Callable<ArrayList<JSONObject>>() {
                    @Override public ArrayList<JSONObject> call() throws Exception {
                        return BurmesePublisherFeed.fetch(outlet);
                    }
                }));
                pool.shutdown();
                for(Future<ArrayList<JSONObject>> future:tasks) {
                    try {results.addAll(future.get(14,TimeUnit.SECONDS));success++;}
                    catch(Exception ex){future.cancel(true);}
                }
                pool.shutdownNow();
            } else if("mix".equals(lang)) {
                ExecutorService pool=Executors.newFixedThreadPool(4);
                ArrayList<Future<ArrayList<JSONObject>>> tasks=new ArrayList<>();
                expected=MIX.length;
                for(String edition:MIX) tasks.add(pool.submit(new Callable<ArrayList<JSONObject>>() {
                    @Override public ArrayList<JSONObject> call() throws Exception {return fetchEdition(edition,topic);}
                }));
                pool.shutdown();
                for(Future<ArrayList<JSONObject>> future:tasks) {
                    try {results.addAll(future.get(15,TimeUnit.SECONDS));success++;}
                    catch(Exception ex){future.cancel(true);}
                }
                pool.shutdownNow();
            } else {
                results.addAll(fetchEdition(lang,topic));success=1;
            }
            if(success==0)throw new Exception("News publishers could not be reached");
            Collections.sort(results,new Comparator<JSONObject>() {
                @Override public int compare(JSONObject a,JSONObject b) {
                    return Long.compare(b.optLong("time"),a.optLong("time"));
                }
            });
            JSONArray items=new JSONArray();
            HashSet<String> seenLinks=new HashSet<>();
            HashSet<String> seenTitles=new HashSet<>();
            for(JSONObject item:results) {
                String key=item.optString("link","");
                String label=item.optString("title","").toLowerCase(Locale.ROOT);
                if(key.isEmpty() || seenLinks.contains(key) || seenTitles.contains(label))continue;
                if(!safeNewsUrl(key))continue;
                seenLinks.add(key);seenTitles.add(label);
                if(items.length()<45)items.put(item);
            }
            JSONObject data=new JSONObject().put("items",items).put("updatedAt",System.currentTimeMillis())
                .put("providers",success).put("expectedProviders",expected)
                .put("language",lang).put("topic",topic).put("publisher",publisher);
            prefs.edit().putString(storageKey(lang,topic,publisher),data.toString()).apply();
            return data;
        }catch(Exception ex) {
            try{return new JSONObject(saved.toString())
                .put("loadError","News source unavailable. Pull to retry or choose another publisher.");}
            catch(Exception ignored){return new JSONObject();}
        }
    }
    static ArrayList<JSONObject> parseRss(String xml,String language,long now) throws Exception {
        XmlPullParserFactory f=XmlPullParserFactory.newInstance(); f.setNamespaceAware(true);
        XmlPullParser reader=f.newPullParser();reader.setInput(new StringReader(xml));
        ArrayList<JSONObject> result=new ArrayList<>();
        int itemDepth=-1;String field=null;StringBuilder content=new StringBuilder();
        String title="",link="",published="",source="";
        int event=reader.getEventType();
        while(event!=XmlPullParser.END_DOCUMENT && result.size()<45) {
            if(event==XmlPullParser.START_TAG) {
                String name=reader.getName();
                if("item".equals(name) && itemDepth<0) {
                    itemDepth=reader.getDepth();title="";link="";published="";source="";
                } else if(itemDepth>0 && reader.getDepth()==itemDepth+1) {
                    field=name;content.setLength(0);
                }
            } else if(event==XmlPullParser.TEXT && field!=null) {
                if(content.length()<2500)content.append(reader.getText());
            } else if(event==XmlPullParser.END_TAG && itemDepth>0) {
                String name=reader.getName();
                if(reader.getDepth()==itemDepth+1 && field!=null && name.equals(field)) {
                    String v=content.toString().trim();
                    if("title".equals(field))title=v;
                    else if("link".equals(field))link=v;
                    else if("pubDate".equals(field))published=v;
                    else if("source".equals(field))source=v;
                    field=null;
                } else if("item".equals(name) && reader.getDepth()==itemDepth) {
                    long time=parseRfc822(published);
                    if(time>=now-WINDOW_MS && time<=now+5*60*1000L && safeNewsUrl(link) && !title.isEmpty()) {
                        result.add(new JSONObject().put("title",title).put("link",link)
                            .put("source",source).put("time",time).put("language",language));
                    }
                    itemDepth=-1;field=null;
                }
            }
            event=reader.next();
        }
        return result;
    }
    private static long parseRfc822(String value) {
        if(value==null || value.isEmpty())return 0;
        for(String pattern:new String[]{"EEE, dd MMM yyyy HH:mm:ss Z","EEE, d MMM yyyy HH:mm:ss Z",
                "EEE, dd MMM yyyy HH:mm:ss z","dd MMM yyyy HH:mm:ss Z"}) {
            try {SimpleDateFormat fmt=new SimpleDateFormat(pattern,Locale.ENGLISH);fmt.setLenient(false);
                fmt.setTimeZone(TimeZone.getTimeZone("UTC")); Date date=fmt.parse(value);if(date!=null)return date.getTime();
            }catch(Exception ignored){}
        }
        return 0;
    }
    static boolean safeNewsUrl(String value) {
        try {
            URL url=new URL(value);
            return "https".equalsIgnoreCase(url.getProtocol()) && url.getHost()!=null
                && url.getHost().contains(".") && value.length()<1600
                && url.getUserInfo()==null;
        }catch(Exception ignored){return false;}
    }
    private static ArrayList<JSONObject> fetchEdition(String lang,String topic) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(feedUrl(lang,topic)).openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
        connection.setRequestProperty("User-Agent","Mozilla/5.0 (compatible; HazardGuardNews/1.0; public RSS)");
        connection.setRequestProperty("Accept","application/rss+xml,application/xml,text/xml");
        connection.setInstanceFollowRedirects(true);
        try {
            if(connection.getResponseCode()!=200)throw new java.io.IOException("RSS HTTP "+connection.getResponseCode());
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>1_300_000)throw new java.io.IOException("News response too large");}
                return parseRss(out.toString("UTF-8"),lang,System.currentTimeMillis());
            }
        }finally{connection.disconnect();}
    }
}