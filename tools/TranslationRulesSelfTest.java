import mm.hazardguard.TranslationRules;
import java.nio.charset.StandardCharsets;
public final class TranslationRulesSelfTest {
  private static void ok(boolean b,String message){if(!b)throw new AssertionError(message);}
  public static void main(String[] args){
    ok("my-MM".equals(TranslationRules.code("my")),"Burmese ISO locale");
    ok("my".equals(TranslationRules.source("mix","မြန်မာနိုင်ငံ သတင်း")),"Myanmar Unicode");
    ok("en".equals(TranslationRules.source("my","Major flood warning for Myanmar")),"English wire in my edition");
    ok("th".equals(TranslationRules.source("mix","น้ำท่วม")),"Thai script");
    ok("ja".equals(TranslationRules.source("mix","ニュース")),"Japanese script");
    ok("en".equals(TranslationRules.source("en","Myanmar flood update")),"English unchanged");
    String clipped=TranslationRules.fitUtf8("မြန်မာမင်္ဂလာပါ flood",24);
    ok(clipped.getBytes(StandardCharsets.UTF_8).length<=24,"UTF-8 limit");
    ok(!clipped.contains("\uFFFD"),"no broken UTF-8");
    ok(TranslationRules.fitUtf8("",500).isEmpty(),"empty text");
    System.out.println("In-app Burmese translation safety rules passed.");
  }
}
