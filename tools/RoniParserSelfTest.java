package mm.hazardguard;

/** Run using only a JDK: javac RoniParser.java RoniParserSelfTest.java; java mm.hazardguard.RoniParserSelfTest */
public final class RoniParserSelfTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static String row(int year,String... values){
        StringBuilder b=new StringBuilder("<tr><th>"+year+"</th>");
        for(String v:values)b.append("<td>").append(v).append("</td>");
        return b.append("</tr>").toString();
    }
    public static void main(String[] args) {
        String html="<table>"+row(2025,"-0.5","-0.4","-0.1","0.0","0.2","0.3","0.5","0.8","0.9","1.0","1.1","1.2")
            +row(2026,"1.3","1.4","1.1","0.9","0.8","0.7","0.6","0.5")+"</table>";
        RoniParser.Reading r=RoniParser.parse(html);
        check(r.year==2026,"latest year");check(r.season.equals("JAS"),"latest season");
        check(r.phase.equals("warm"),"warm phase across year");
        check(r.consecutive==14,"consecutive warm sequence across years");
        check(Math.abs(r.value-0.5)<0.0001,"index numeric");
        RoniParser.Reading cooler=RoniParser.parse("<table>"+row(2026,"-0.4","-0.5","-0.6","-0.7","-0.8","-0.9")+"</table>");
        check(cooler.phase.equals("cool"),"cool phase");
        RoniParser.Reading transitional=RoniParser.parse("<table>"+row(2026,"-0.8","-0.9","-0.7","0.2")+"</table>");
        check(transitional.phase.equals("neutral"),"transition not a declared episode");
        try {RoniParser.parse("<body>unavailable</body>");throw new AssertionError("must reject missing data");}
        catch(IllegalArgumentException expected) {}
        System.out.println("PASS: 4 NOAA RONI parsing cases including year crossover and missing-data rejection");
    }
}
