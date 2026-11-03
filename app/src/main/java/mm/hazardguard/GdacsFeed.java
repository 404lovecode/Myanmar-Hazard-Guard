package mm.hazardguard;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.StringReader;

/** Parse untrusted GDACS RSS into a small, display-only bulletin list. */
public final class GdacsFeed {
    private GdacsFeed() {}
    public static JSONObject parse(String xml) throws Exception {
        XmlPullParserFactory factory=XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser reader=factory.newPullParser();
        reader.setInput(new StringReader(xml));
        JSONArray list=new JSONArray();
        JSONObject item=null;
        int depth=0;
        String field=null;
        int event=reader.getEventType();
        while(event!=XmlPullParser.END_DOCUMENT) {
            if(event==XmlPullParser.START_TAG) {
                String name=reader.getName();
                if("item".equals(name) && item==null){item=new JSONObject();depth=reader.getDepth();}
                else if(item!=null && reader.getDepth()==depth+1){field=name;}
            } else if(event==XmlPullParser.TEXT && item!=null && field!=null) {
                String value=reader.getText();
                if(value!=null && value.length()>0){
                    String old=item.optString(field,"");
                    item.put(field,old+value);
                }
            } else if(event==XmlPullParser.END_TAG && item!=null) {
                if("item".equals(reader.getName()) && reader.getDepth()==depth){
                    String link=item.optString("link","").trim();
                    if(link.startsWith("http://www.gdacs.org/") || link.startsWith("http://gdacs.org/"))
                        link="https://"+link.substring(7);
                    if(item.optString("title","").length()>0 &&
                        (link.startsWith("https://www.gdacs.org/") ||
                         link.startsWith("https://gdacs.org/") ||
                         link.startsWith("https://data.gdacs.org/"))) {
                        JSONObject entry=new JSONObject();
                        entry.put("title",item.optString("title"));
                        entry.put("link",link);
                        entry.put("pubDate",item.optString("pubDate"));
                        entry.put("alertlevel",item.optString("alertlevel",""));
                        entry.put("eventtype",item.optString("eventtype",""));
                        list.put(entry);
                    }
                    item=null;field=null;
                    if(list.length()>=45)break;
                } else if(reader.getDepth()==depth+1){field=null;}
            }
            event=reader.next();
        }
        return new JSONObject().put("items",list).put("source","GDACS");
    }
}
