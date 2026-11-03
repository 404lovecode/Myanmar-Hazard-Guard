package mm.hazardguard;

import java.util.Locale;

/** No network-based language guessing. Uses country code from chosen city / device geocoding. */
public final class Languages {
    private Languages() {}
    public static final String[] CODES={"auto","en","my","th","es","fr","hi","id"};
    public static final String[] NAMES={"Automatic (by location)","English","မြန်မာ (Burmese)",
        "ไทย (Thai)","Español (Spanish)","Français (French)","हिन्दी (Hindi)","Bahasa Indonesia"};
    public static String resolve(String pref,String countryCode) {
        if(pref!=null && !pref.equals("auto")) {
            for(String lang:CODES) if(lang.equals(pref) && !lang.equals("auto")) return lang;
            return "en";
        }
        String c=countryCode==null?"":countryCode.toUpperCase(Locale.US);
        if(c.equals("MM"))return "my";
        if(c.equals("TH"))return "th";
        if(c.equals("FR"))return "fr";
        if(c.equals("IN"))return "hi";
        if(c.equals("ID"))return "id";
        if(c.equals("ES") || c.equals("MX") || c.equals("AR") || c.equals("CL") ||
           c.equals("CO") || c.equals("PE") || c.equals("VE") || c.equals("BO") ||
           c.equals("EC") || c.equals("UY") || c.equals("PY") || c.equals("GT") ||
           c.equals("CR") || c.equals("CU") || c.equals("DO"))return "es";
        return "en";
    }
    public static String countryForPreset(String name) {
        if(name==null)return "";
        switch(name) {
          case "Myanmar":return "MM";case "Thailand":return "TH";
          case "France":return "FR";case "India":return "IN";
          case "Indonesia":return "ID";case "Spain":return "ES";
          case "Mexico":return "MX";case "Argentina":return "AR";
          case "Chile":return "CL";case "Peru":return "PE";
          case "United States":return "US";case "United Kingdom":return "GB";
          default:return "";
        }
    }
    public static String label(String code) {
        for(int i=0;i<CODES.length;i++)if(CODES[i].equals(code))return NAMES[i];
        return NAMES[0];
    }
}
