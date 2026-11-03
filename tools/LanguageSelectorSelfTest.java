package mm.hazardguard;
/** Standalone JDK test: automatic region mapping and manual overrides. */
public final class LanguageSelectorSelfTest {
    private static void eq(String expected,String actual) {
        if(!expected.equals(actual))throw new AssertionError("expected "+expected+" got "+actual);
    }
    public static void main(String[] args) {
        eq("my",Languages.resolve("auto","MM"));
        eq("th",Languages.resolve("auto","TH"));
        eq("fr",Languages.resolve("auto","FR"));
        eq("es",Languages.resolve("auto","MX"));
        eq("hi",Languages.resolve("auto","IN"));
        eq("id",Languages.resolve("auto","ID"));
        eq("en",Languages.resolve("auto","JP"));
        eq("en",Languages.resolve("en","MM"));
        eq("my",Languages.resolve("my","TH"));
        eq("es",Languages.resolve("auto",Languages.countryForPreset("Spain")));
        System.out.println("PASS: 10 language and region selection cases");
    }
}
