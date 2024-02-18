package mkt.common;

/**
 * @author aosom
 */
public class MktS3Pic {

    /** 拍照需求表-拍照流程图 **/
    public final static String AOS_FLOWPIC = "https://clss.s3.amazonaws.com/"
        + "%E6%8B%8D%E7%85%A7%E9%9C%80%E6%B1%82%E8%A1%A8-%E6%8B%8D%E7%85%A7%E6%B5%81%E7%A8%8B.jpg";
    /** 拍照需求表-视频流程图 **/
    public final static String AOS_FLOWVED = "https://clss.s3.amazonaws.com/"
        + "%E6%8B%8D%E7%85%A7%E9%9C%80%E6%B1%82%E8%A1%A8-%E8%A7%86%E9%A2%91%E6%B5%81%E7%A8%8B.jpg";
    /** 设计需求表-图片链接前缀 **/
    public final static String AOS_DESIGNPIC2 = "file://192.168.70.60/产品图&视频/02 设计图片/4/";
    public final static String EIGHT = "8";
    public final static String EIGHTYTHREE = "83";
    public final static String EIGHTYFOUR = "84";

    public static String getItemPicture(String itemNumber) {
        /*
          ①货号第一位字符是8，
          若第二位字符是3，图片路径=file:\\192.168.70.61\Marketing_Files\图片库\83\SKU；
          若第二位字符是4，图片路径=file:\\192.168.70.61\Marketing_Files\图片库\84\SKU；
          若第二位字符是其他，图片路径=file:\\192.168.70.61\Marketing_Files\图片库\8X\SKU；
          ②货号第一位字符是其他，图片路径=file:\\192.168.70.61\Marketing_Files\图片库\货号首字符\SKU；
         */
        String start = itemNumber.substring(0, 1);
        if (itemNumber.startsWith(EIGHT)) {
            if (itemNumber.startsWith(EIGHTYTHREE)) {
                itemNumber = "file:\\\\192.168.70.61\\Marketing_Files\\图片库\\83\\" + itemNumber;
            } else if (itemNumber.startsWith(EIGHTYFOUR)) {
                itemNumber = "file:\\\\192.168.70.61\\Marketing_Files\\图片库\\84\\" + itemNumber;
            } else {
                itemNumber = "file:\\\\192.168.70.61\\Marketing_Files\\图片库\\8X\\" + itemNumber;
            }
        } else {
            itemNumber = "file:\\\\192.168.70.61\\Marketing_Files\\图片库\\" + start + "\\" + itemNumber;
        }
        return itemNumber;
    }

    public static String decrypt(String value) {
        try {
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return value;
        }
    }

}
