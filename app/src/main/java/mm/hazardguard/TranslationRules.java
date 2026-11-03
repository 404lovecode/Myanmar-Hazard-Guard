package mm.hazardguard;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Language and byte-length rules for public headline translation. Pure Java/testable. */
public final class TranslationRules {
    private TranslationRules() {}
    public static String source(String edition, String title) {
        String s=edition==null?"en":edition.trim().toLowerCase(Locale.ROOT);
        if(s.equals("mix") || s.isEmpty())s="en";
        if(title==null)title="";
        for(int i=0;i<title.length();) {
            int cp=title.codePointAt(i);i+=Character.charCount(cp);
            if(cp>=0x1000 && cp<=0x109F)return "my";
            if(cp>=0x0E00 && cp<=0x0E7F)return "th";
            if(cp>=0x3040 && cp<=0x30FF)return "ja";
            if(cp>=0xAC00 && cp<=0xD7AF)return "ko";
            if(cp>=0x0600 && cp<=0x06FF)return "ar";
            if(cp>=0x0900 && cp<=0x097F)return "hi";
            if(cp>=0x0400 && cp<=0x04FF)return "ru";
        }
        // News editions are not reliable language detectors. A Latin-only headline
        // in a Burmese/Thai/Chinese/etc edition is commonly an English wire item.
        if((s.equals("my")||s.equals("th")||s.equals("ja")||s.equals("ko")||
            s.equals("zh")||s.equals("ar")||s.equals("ru")||s.equals("hi"))
            && title.matches(".*[a-zA-Z]{3}.*")
            && !containsNonLatinScript(title))return "en";
        return s;
    }
    private static boolean containsNonLatinScript(String t) {
        for(int i=0;i<t.length();) {
            int cp=t.codePointAt(i);i+=Character.charCount(cp);
            if((cp>=0x0600&&cp<=0x06FF)||(cp>=0x0900&&cp<=0x109F)||
                (cp>=0x3040&&cp<=0x30FF)||(cp>=0x3400&&cp<=0x9FFF)||
                (cp>=0xAC00&&cp<=0xD7AF))return true;
        }
        return false;
    }
    public static String code(String lang) {
        if(lang==null)return "en";
        switch(lang.toLowerCase(Locale.ROOT)) {
            case "my":return "my-MM";
            case "zh":return "zh-CN";
            case "pt":return "pt-PT";
            case "hi":return "hi-IN";
            case "bn":return "bn-BD";
            case "ta":return "ta-IN";
            case "ur":return "ur-PK";
            default:return lang.toLowerCase(Locale.ROOT);
        }
    }
    public static String fitUtf8(String text,int maxBytes) {
        if(text==null || maxBytes<=0)return "";
        StringBuilder out=new StringBuilder();
        int bytes=0;
        for(int offset=0;offset<text.length();) {
            int cp=text.codePointAt(offset);
            String character=new String(Character.toChars(cp));
            int n=character.getBytes(StandardCharsets.UTF_8).length;
            if(bytes+n>maxBytes)break;
            out.append(character);bytes+=n;offset+=Character.charCount(cp);
        }
        return out.toString();
    }
}
