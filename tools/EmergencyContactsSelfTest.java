import mm.hazardguard.EmergencyContacts;
public final class EmergencyContactsSelfTest {
    public static void main(String[] args) {
        verify("MM", "192", "191", "199");
        verify("TH", "1669", "199", "191");
        verify("IN", "112");
        verify("BD", "999");
        verify("SG", "995", "999");
        verify("MY", "999", "994", "112");
        if(EmergencyContacts.forCode("")!=null || EmergencyContacts.forCode("ZZ")!=null)
            throw new AssertionError("Unknown locations must not have guessed emergency numbers");
        System.out.println("Emergency country routing passed");
    }
    private static void verify(String code, String... expected) {
        EmergencyContacts.Country c=EmergencyContacts.forCode(code);
        if(c==null || c.numbers.length!=expected.length)throw new AssertionError(code);
        for(int i=0;i<expected.length;i++)
            if(!expected[i].equals(c.numbers[i]))throw new AssertionError(code+" "+i);
    }
}
