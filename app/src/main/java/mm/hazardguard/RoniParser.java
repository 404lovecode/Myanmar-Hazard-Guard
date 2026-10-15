package mm.hazardguard;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses published historical RONI year rows (not NOAA's official forecast). No Android dependencies. */
public final class RoniParser {
    private RoniParser() {}
    public static final class Reading {
        public final int year,consecutive;
        public final String season,phase;
        public final boolean sustained;
        public final double value;
        Reading(int year,String season,double value,String phase,int consecutive,boolean sustained) {
            this.year=year;this.season=season;this.value=value;this.phase=phase;
            this.consecutive=consecutive;this.sustained=sustained;
        }
    }
    public static Reading parse(String html) {
        String[] seasons={"DJF","JFM","FMA","MAM","AMJ","MJJ","JJA","JAS","ASO","SON","OND","NDJ"};
        Pattern rowPattern=Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>");
        Pattern cellPattern=Pattern.compile("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>");
        Matcher rows=rowPattern.matcher(html);
        int bestYear=0; ArrayList<Double> best=new ArrayList<>();
        int previousYear=0;ArrayList<Double> previous=new ArrayList<>();
        while(rows.find()) {
            Matcher cells=cellPattern.matcher(rows.group(1));
            ArrayList<String> values=new ArrayList<>();
            while(cells.find()) {
                String text=cells.group(1).replaceAll("(?is)<[^>]+>"," ")
                    .replace("&nbsp;"," ").replace("&#160;"," ").trim();
                if(!text.isEmpty())values.add(text);
            }
            if(values.size()<2 || !values.get(0).matches("(?:19|20)\\d{2}"))continue;
            int year=Integer.parseInt(values.get(0));
            ArrayList<Double> numbers=new ArrayList<>();
            for(int i=1;i<values.size() && numbers.size()<12;i++) {
                String value=values.get(i).trim();
                if(value.matches("[+-]?\\d+(?:\\.\\d+)?"))numbers.add(Double.parseDouble(value));
            }
            if(!numbers.isEmpty() && year>bestYear) {
                previousYear=bestYear;previous=best;bestYear=year;best=numbers;
            }
        }
        if(bestYear<2020 || best.isEmpty())throw new IllegalArgumentException("No recent NOAA RONI row available");
        double latest=best.get(best.size()-1);
        ArrayList<Double> sequence=new ArrayList<>();
        if(previousYear==bestYear-1)sequence.addAll(previous);
        sequence.addAll(best);
        int count=0;
        for(int i=sequence.size()-1;i>=0;i--) {
            if((latest>=0.5 && sequence.get(i)>=0.5) || (latest<=-0.5 && sequence.get(i)<=-0.5))count++;
            else break;
        }
        boolean sustained=count>=5;
        String phase=(sustained && latest>=0.5)?"warm":(sustained && latest<=-0.5)?"cool":"neutral";
        return new Reading(bestYear,seasons[Math.min(best.size()-1,11)],latest,phase,count,sustained);
    }
}
