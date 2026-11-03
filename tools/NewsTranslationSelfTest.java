import mm.hazardguard.NewsTranslation;

public final class NewsTranslationSelfTest {
    public static void main(String[] args) {
        String my=NewsTranslation.headlineUrl("Flood warnings မြန်မာ","my");
        require(my.startsWith("https://translate.google.com/"),"HTTPS");
        require(my.contains("tl=my"),"Burmese target");
        require(my.contains("%E1%80"),"Unicode URL encoded");
        require(NewsTranslation.headlineUrl("  ","my").isEmpty(),"Empty title");
        require("my".equals(NewsTranslation.target("MY")),"case insensitive");
        require("en".equals(NewsTranslation.target("evil")), "Unrecognized lang");
        String story=NewsTranslation.websiteUrl("https://news.google.com/rss/articles/example?x=1&y=2","my");
        require(story.contains("tl=my"),"Burmese website target");
        require(story.contains("https%3A%2F%2F"),"Article URL encoded");
        require(NewsTranslation.websiteUrl("javascript:alert(1)","my").isEmpty(),"Disallow javascript");
        require(NewsTranslation.websiteUrl("http://example.com/","my").isEmpty(),"Disallow HTTP");
        require(NewsTranslation.websiteUrl("https://user:pass@example.com/","my").isEmpty(),"Disallow credentials");
        System.out.println("Burmese and safe translation link tests passed.");
    }
    private static void require(boolean ok,String name) {
        if(!ok)throw new AssertionError(name);
    }
}
