package mkt.progress;

public enum enumUtil {
    List_min("aos_mkt_listing_min", "mkt.progress.listing.aos_mkt_listingmin_bill");


    private String type;
    private String desc;

    private enumUtil(String type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public static String getValue(String type) {
        enumUtil[] enumUtils = values();
        for (enumUtil eu : enumUtils) {
            if (eu.type().equals(type)) {
                return eu.desc();
            }
        }
        return null;
    }

    public static String getType(String desc) {
        enumUtil[] eus = values();
        for (enumUtil eu : eus) {
            if (eu.desc().equals(desc)) {
                return eu.type();
            }
        }
        return null;
    }


    private String type() {
        return this.type;
    }

    private String desc() {
        return this.desc;
    }
}
