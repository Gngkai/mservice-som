package mkt.common.util;

import com.deepl.api.DeepLException;
import com.deepl.api.Translator;
import common.Cux_Common_Utl;
import kd.bos.exception.ErrorCode;
import kd.bos.exception.KDException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author create by gk
 * @date 2023/5/19 10:22
 * @action  翻译Api
 */
public class translateUtils {
    private static Translator translator ;
    private static Map<String,String> map_tranSource;   //源语言
    private static Map<String,String> map_transLan;     //目标语言
    static {
        String  authKey = "7d2a206b-8182-c99a-a314-76874dd27d89:fx";
        translator = new Translator(authKey);
        map_tranSource = new HashMap<>();
        map_tranSource.put("中文","ZH");
        map_tranSource.put("EN","EN");
        map_tranSource.put("DE","DE");
        map_tranSource.put("FR","FR");
        map_tranSource.put("IT","IT");
        map_tranSource.put("ES","ES");
        map_tranSource.put("PT","PT");
        map_tranSource.put("RO","RO");
        map_tranSource.put("UK","EN");
        map_tranSource.put("US","EN");
        map_tranSource.put("CA","EN");
        map_tranSource.put("CN","ZH");
        map_tranSource.put("ZH","ZH");

        map_transLan = new HashMap<>();
        map_transLan.put("中文","ZH");
        map_transLan.put("EN","EN-GB");
        map_transLan.put("DE","DE");
        map_transLan.put("FR","FR");
        map_transLan.put("IT","IT");
        map_transLan.put("ES","ES");
        map_transLan.put("PT","PT-PT");
        map_transLan.put("RO","RO");
        map_transLan.put("UK","EN-GB");
        map_transLan.put("US","EN-US");
        map_transLan.put("CA","EN-US");
        map_transLan.put("CN","ZH");
        map_transLan.put("ZH","ZH");
    }

    /**
     *
     * @param sourceLan     翻译源语言 （如：US,CA）
     * @param targetLan     翻译目标语言（如：US,CA）
     * @param text          翻译内容
     * @return  翻译
     */
    public static String  transalate(String sourceLan,String targetLan,String text){
        if (Cux_Common_Utl.IsNull(text))
            throw new KDException(new ErrorCode("translateUtils","翻译内容不能为空"));
        if (!map_tranSource.containsKey(sourceLan)) {
            throw new KDException(new ErrorCode("translateUtils","不支持翻译该源语言 "+sourceLan));
        }
        if (!map_transLan.containsKey(targetLan)){
            throw new KDException(new ErrorCode("translateUtils","不支持翻译该木匾语言 "+targetLan));
        }
        try {

            return translator.translateText(text, map_tranSource.get(sourceLan), map_transLan.get(targetLan)).getText();
        } catch (DeepLException e) {
            throw new KDException(new ErrorCode("translateUtils",e.getMessage()));
        } catch (InterruptedException e) {
            throw new KDException(new ErrorCode("translateUtils",e.getMessage()));
        }
    }

    /**
     *
     * @param sourceLan      翻译源语言 （如：US,CA）
     * @param targetLan      翻译目标语言（如：US,CA）
     * @param texts          翻译内容
     * @return  翻译
     */
    public static List<String>  transalate(String sourceLan, String targetLan, List<String> texts){
        if (texts == null)
            throw new KDException(new ErrorCode("translateUtils","翻译内容不能为空"));
        if (texts.size()==0)
            return new ArrayList<>();
        for (String text : texts) {
            if (Cux_Common_Utl.IsNull(text))
                throw new KDException(new ErrorCode("translateUtils","翻译内容不能为空"));
        }
        if (!map_tranSource.containsKey(sourceLan)) {
            throw new KDException(new ErrorCode("translateUtils","不支持翻译该源语言 "+sourceLan));
        }
        if (!map_transLan.containsKey(targetLan)){
            throw new KDException(new ErrorCode("translateUtils","不支持翻译该木匾语言 "+targetLan));
        }
        try {
            return translator.translateText(texts, map_tranSource.get(sourceLan), map_transLan.get(targetLan)).stream()
                    .map(result -> result.getText())
                    .collect(Collectors.toList());
        } catch (DeepLException e) {
            throw new KDException(new ErrorCode("translateUtils",e.getMessage()));
        } catch (InterruptedException e) {
            throw new KDException(new ErrorCode("translateUtils",e.getMessage()));
        }
    }


}
