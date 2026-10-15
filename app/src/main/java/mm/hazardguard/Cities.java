package mm.hazardguard;

public final class Cities {
    private Cities() {}
    public static final String[] NAMES = {
        "Yangon", "Mandalay", "Naypyidaw", "Sagaing", "Bago", "Magway",
        "Taunggyi", "Myitkyina", "Pathein", "Mawlamyine", "Sittwe", "Lashio", "Kalemyo", "Hpa-An"
    };
    public static final String[] MY_NAMES = {
        "ရန်ကုန်", "မန္တလေး", "နေပြည်တော်", "စစ်ကိုင်း", "ပဲခူး", "မကွေး",
        "တောင်ကြီး", "မြစ်ကြီးနား", "ပုသိမ်", "မော်လမြိုင်", "စစ်တွေ", "လားရှိုး", "ကလေး", "ဘားအံ"
    };
    public static final double[] LAT = {16.8409,21.9588,19.7633,21.8787,17.3352,20.1496,20.7892,25.3946,16.7792,16.4905,20.1462,22.9359,23.1880,16.8909};
    public static final double[] LON = {96.1735,96.0891,96.0785,95.9787,96.4797,94.9328,97.0378,97.3990,94.7321,97.6278,92.8980,97.7523,94.0120,97.6338};
    public static String name(int i, boolean my) { return (my ? MY_NAMES : NAMES)[Math.max(0,Math.min(i,NAMES.length-1))]; }
}
