package mm.hazardguard;

import java.util.Locale;

/** Published local emergency numbers; unsupported countries NEVER get guessed defaults. */
public final class EmergencyContacts {
    private EmergencyContacts() {}
    public static final class Country {
        public final String code,name,source;
        public final String[] services,numbers;
        Country(String code,String name,String source,String[] services,String[] numbers) {
            this.code=code;this.name=name;this.source=source;
            this.services=services;this.numbers=numbers;
        }
    }
    private static final Country[] LIST={
        new Country("MM","Myanmar","https://www.gov.uk/foreign-travel-advice/myanmar/getting-help",
           new String[]{"Ambulance / medical","Fire and rescue","Police"},new String[]{"192","191","199"}),
        new Country("TH","Thailand","https://www.gov.uk/foreign-travel-advice/thailand/getting-help",
           new String[]{"Ambulance / medical","Fire and rescue","Police / general emergency"},new String[]{"1669","199","191"}),
        new Country("IN","India","https://www.gov.uk/foreign-travel-advice/india/getting-help",
           new String[]{"Ambulance, fire or police"},new String[]{"112"}),
        new Country("BD","Bangladesh","https://www.gov.uk/foreign-travel-advice/bangladesh/getting-help",
           new String[]{"Ambulance, fire or police"},new String[]{"999"}),
        new Country("SG","Singapore","https://www.gov.uk/foreign-travel-advice/singapore/getting-help",
           new String[]{"Ambulance / fire","Police"},new String[]{"995","999"}),
        new Country("MY","Malaysia","https://www.gov.uk/foreign-travel-advice/malaysia/getting-help",
           new String[]{"Police / ambulance","Fire and rescue","Ambulance (mobile)"},new String[]{"999","994","112"})
    };
    public static Country forCode(String code) {
        if(code==null)return null;
        for(Country country:LIST)
            if(country.code.equals(code.trim().toUpperCase(Locale.US)))return country;
        return null;
    }
    public static Country[] supported(){return LIST.clone();}
}
