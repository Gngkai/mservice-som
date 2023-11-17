package mkt.common.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import common.fnd.FndGlobal;
import common.sal.sys.basedata.dao.ItemCategoryDao;
import common.sal.sys.basedata.dao.impl.ItemCategoryDaoImpl;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.ILocaleString;
import kd.bos.servicehelper.QueryServiceHelper;
import common.sal.util.QFBuilder;

import java.util.*;

/**
 * @author create by gk
 * @date 2023/7/6 14:04
 * @action 敏感词校验工具类
 */
public class sensitiveWordsUtils {
    public static List<String> sensitiveWordSpecies;
    private static final  String specialSymbols = "\n [`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*()——+|{}【】‘；：”“’。，、？\\\\]";
    private static JSONObject wordsInfo = new JSONObject();
    private static Date intiWordInfoDate =  new Date(); //敏感词的初始化时间
    static {
        sensitiveWordSpecies = Arrays.asList("DE","FR","IT","ES","PT","RO","UK","US","CA");
        setWordsInfo();
    }
    /**
     * 通过产品号查找物料的敏感词
     * @param productNo
     */
    public static JSONObject FindMaterialSensitiveWords(Object productNo){
        JSONObject result = InitLanguageSensitiveWords();
        if (FndGlobal.IsNull(productNo))
            return result;
        String itemName = null;
        String[] cateNameEn = null;
        QFBuilder builder = new QFBuilder("aos_productno","=",productNo);
        //加入物料通用过滤条件
        builder.add("name","!=","");
        DynamicObjectCollection items = QueryServiceHelper.query("bd_material", "id,name", builder.toArray(), "createtime desc", 1);
        if (items.size()>0) {
            itemName = items.get(0).getString("name");
            String itemId = items.get(0).getString("id");
            //查找英文品类名
            ItemCategoryDao categoryDao = new ItemCategoryDaoImpl();
            ILocaleString cateName = categoryDao.getItemCateName(itemId);
            if (FndGlobal.IsNotNull(cateName) && FndGlobal.IsNotNull(cateName.getLocaleValue_en()))
                cateNameEn = cateName.getLocaleValue_en().split(",");
        }

        //查询敏感词
        builder.clear();
        builder.add("aos_words","!=","");
        StringJoiner selectFields = new StringJoiner(",");
        selectFields.add("aos_words");
        selectFields.add("entryentity.aos_whole aos_whole");
        selectFields.add("entryentity.aos_lan aos_lan");
        selectFields.add("entryentity.aos_cate1 aos_cate1");
        selectFields.add("entryentity.aos_cate2 aos_cate2");
        selectFields.add("entryentity.aos_cate3 aos_cate3");
        selectFields.add("entryentity.aos_name aos_name");
        DynamicObjectCollection searchResults = QueryServiceHelper.query("aos_mkt_sensitive", selectFields.toString(), builder.toArray());
        for (DynamicObject searchResult : searchResults) {
            String words = searchResult.getString("aos_words");
            //全部适用
            if (searchResult.getBoolean("aos_whole")) {
                for (String wordSpecy : sensitiveWordSpecies) {
                    JSONArray array = result.getJSONArray(wordSpecy);
                    array.add(SplitString(words));
                }
                continue;
            }

            //语言
            String lan = searchResult.getString("aos_lan");
            //不适合所有语言，且语言行为空，则判断为脏数据
            if (FndGlobal.IsNull(lan))
                continue;

            //判断大类是否相等
            String cate = searchResult.getString("aos_cate1");
            boolean equivalent = FndGlobal.IsNull(cate)|| FndGlobal.IsNull(cateNameEn) || cateNameEn.length<1 || cate.equals(cateNameEn[0]);
            if (!equivalent) {
                continue;
            }

            //中类
            cate = searchResult.getString("aos_cate2");
            equivalent = FndGlobal.IsNull(cate) || FndGlobal.IsNull(cateNameEn) || cateNameEn.length<2 || cate.equals(cateNameEn[1]);
            if (!equivalent){
                continue;
            }

            //小类
            cate = searchResult.getString("aos_cate3");
            equivalent = FndGlobal.IsNull(cate)|| FndGlobal.IsNull(cateNameEn) || cateNameEn.length<3 || cate.equals(cateNameEn[2]);
            if (!equivalent){
                continue;
            }

            //品名
            String searchName = searchResult.getString("aos_name");
            equivalent = FndGlobal.IsNull(searchName) || FndGlobal.IsNull(itemName) || itemName.equals(searchName);
            if (!equivalent){
                continue;
            }
            if (result.containsKey(lan)) {
                JSONArray value = result.getJSONArray(lan);
                value.add(SplitString(words));
            }
        }

        //判断是否需要更新敏感词
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_sensitive", "modifytime", builder.toArray(), "modifytime desc", 1);
        if (dyc.size()>0){
            Date modifytime = dyc.get(0).getDate("modifytime");
            if (modifytime.after(intiWordInfoDate)) {
                setWordsInfo();
            }
        }
        return result;
    }
    /**
     *初始化每种语言的敏感词
     * @return  每种语言的敏感词
     */
    private static JSONObject InitLanguageSensitiveWords(){
        JSONObject result = new JSONObject();
        for (String sensitiveWordSpecy : sensitiveWordSpecies) {
            result.put(sensitiveWordSpecy,new JSONArray());
        }
        return result;
    }
    /**
     * 分割字符串
     * @param text  文本
     * @return  分割结果 保留特殊符号
     */
    private static JSONArray SplitString(String text){
        StringTokenizer stringTokenizer = new StringTokenizer(text,specialSymbols,true);
        JSONArray result = new JSONArray();
        while (stringTokenizer.hasMoreTokens()) {
            result.add(stringTokenizer.nextElement().toString());
        }
        return result;
    }
    /**
     * 敏感词校验
     * @param sensitiveWords    敏感词
     * @param text              校验内容
     * @param language          校验语言
     */
    public static JSONObject sensitiveWordVerificate(JSONObject sensitiveWords,String text,String language){
        JSONObject result = new JSONObject();
        result.put("state",false);
        if ( FndGlobal.IsNull(text) || FndGlobal.IsNull(language))
            return result;
        JSONArray contentArrays = SplitString(text);
        if (language.equals("EN")) {
            JSONObject  ukResult = calibrate(sensitiveWords.getJSONArray("UK"), contentArrays);
            JSONObject  caResult = calibrate(sensitiveWords.getJSONArray("CA"), contentArrays);
            JSONObject  usResult = calibrate(sensitiveWords.getJSONArray("US"), contentArrays);

            result.put("data",new JSONArray());

            //记录已经校验出来的敏感词
            List<String> fillWords = new ArrayList<>();
            if (ukResult.getBoolean("state")) {
                result.put("state",true);
                JSONArray dataRows = ukResult.getJSONArray("data");
                for (int i = 0; i < dataRows.size(); i++) {
                    JSONObject row = dataRows.getJSONObject(i);
                    String words = row.getString("words");
                    fillWords.add(words);
                    row.put("lan","UK");
                }
                result.put("data",dataRows);
            }

            if (caResult.getBoolean("state")) {
                result.put("state",true);
                JSONArray dataRows = caResult.getJSONArray("data");
                JSONArray resultRows = result.getJSONArray("data");
                for (int i = 0; i < dataRows.size(); i++) {
                    JSONObject row = dataRows.getJSONObject(i);
                    String words = row.getString("words");
                    if (fillWords.contains(words)){
                        for (int indedx = 0; indedx < resultRows.size(); indedx++) {
                            JSONObject resultRow = resultRows.getJSONObject(indedx);
                            if (resultRow.getString("words").equals(words)) {
                                String lan = resultRow.getString("lan");
                                resultRow.put("lan",lan+"/CA");
                            }
                        }
                    }else {
                        row.put("lan","CA");
                        resultRows.add(row);
                    }
                }
            }

            if (usResult.getBoolean("state")) {
                result.put("state",true);
                JSONArray dataRows = usResult.getJSONArray("data");
                JSONArray resultRows = result.getJSONArray("data");
                for (int i = 0; i < dataRows.size(); i++) {
                    JSONObject row = dataRows.getJSONObject(i);
                    String words = row.getString("words");
                    if (fillWords.contains(words)){
                        for (int indedx = 0; indedx < resultRows.size(); indedx++) {
                            JSONObject resultRow = resultRows.getJSONObject(indedx);
                            if (resultRow.getString("words").equals(words)) {
                                String lan = resultRow.getString("lan");
                                resultRow.put("lan",lan+"/US");
                            }
                        }
                    }else {
                        row.put("lan","US");
                        resultRows.add(row);
                    }
                }
            }

        }
        else {
            result = calibrate(sensitiveWords.getJSONArray(language), contentArrays);
            if (result.getBoolean("state")) {
                JSONArray dataRows = result.getJSONArray("data");
                for (int i = 0; i < dataRows.size(); i++) {
                    JSONObject row = dataRows.getJSONObject(i);
                    row.put("lan",language);
                }
            }
        }
        return result;
    }
    /**
     * 敏感词字符校验方法
     * @param sensitiveWords    敏感词数组
     * @param contentArrays     校验文本数组
     * @return  校验结果（state :true/false (true 为异常); words:" " (敏感词)  ）
     */
    public static JSONObject calibrate(JSONArray sensitiveWords,JSONArray contentArrays){
        JSONObject result = new JSONObject();
        result.put("data",new JSONArray());
        result.put("state",false);
        if (sensitiveWords==null || sensitiveWords.size()==0)
            return result;
        //记录校验到的敏感词
        StringBuilder sensitiveBuilder = new StringBuilder();

        //记录已经校验出来的敏感词
        List<String> calibratedWords = new ArrayList<>(contentArrays.size());

        //校验内容按照字符遍历
        for (int contentIndex = 0; contentIndex < contentArrays.size(); contentIndex++) {
            //敏感词遍历
            for (int sensitiveIndex = 0; sensitiveIndex < sensitiveWords.size(); sensitiveIndex++) {
                //单个敏感词拆分后的数组
                JSONArray sensitiveCharArray = sensitiveWords.getJSONArray(sensitiveIndex);
                boolean abnormal = true;
                sensitiveBuilder.setLength(0);
                //单个敏感词的字符遍历
                for (int senCharIndex = 0; senCharIndex < sensitiveCharArray.size(); senCharIndex++) {
                    //校验内容字符
                    if ((contentIndex+senCharIndex)>=contentArrays.size()){
                        abnormal = false;
                        break;
                    }

                    String contentChar = contentArrays.getString(contentIndex+senCharIndex);
                    //校验敏感词字符
                    String sensitiveChar = sensitiveCharArray.getString(senCharIndex);
                    //不相等则认为不异常
                    if (!contentChar.equalsIgnoreCase(sensitiveChar)){
                        abnormal = false;
                        break;
                    }
                    sensitiveBuilder.append(sensitiveChar);
                }
                //单个敏感词完全匹配，则认为异常
                if (abnormal){
                    //先判断这个敏感词是否已经校验出来了,如果已经校验出来了，则跳过
                    if (calibratedWords.contains(sensitiveBuilder.toString())){
                        continue;
                    }
                    calibratedWords.add(sensitiveBuilder.toString());
                    //判断返回状态，如果状态还剩正常，则打上异常标记
                    if (!result.getBoolean("state"))
                        result.put("state",abnormal);
                    JSONArray words = result.getJSONArray("data");
                    if (wordsInfo.containsKey(sensitiveBuilder.toString())) {
                        JSONObject clone = wordsInfo.getJSONObject(sensitiveBuilder.toString()).clone();
                        words.add(clone);
                    }
                    else {
                        setWordsInfo();
                        if (wordsInfo.containsKey(sensitiveBuilder.toString())) {
                            JSONObject clone = wordsInfo.getJSONObject(sensitiveBuilder.toString()).clone();
                            words.add(clone);
                        }
                    }
                }
            }
        }
        return result;
    }
    public static void setWordsInfo (){
        intiWordInfoDate = new Date();
        QFBuilder builder = new QFBuilder();
        builder.add("aos_words","!=","");
        DynamicObjectCollection results = QueryServiceHelper.query("aos_mkt_sensitive", "aos_words,aos_type,aos_replace", builder.toArray());
        wordsInfo.clear();
        for (DynamicObject result : results) {
            JSONObject words = new JSONObject();
            words.put("type",result.getString("aos_type"));
            words.put("replace",result.getString("aos_replace"));
            words.put("words",result.getString("aos_words"));
            wordsInfo.put(result.getString("aos_words"),words);
        }
    }
    public static String replaceSensitiveWords(String text,String sensitiveWord,String replaceWords){
        if (FndGlobal.IsNull(text) || FndGlobal.IsNull(sensitiveWord))
            return text;
        JSONArray contentArrays = SplitString(text);
        JSONArray sensitiveCharArray = SplitString(sensitiveWord);
        StringBuilder result = new StringBuilder();
        for (int contentIndex = 0; contentIndex < contentArrays.size(); contentIndex++) {
            boolean abnormal = true;
            for (int senCharIndex = 0; senCharIndex < sensitiveCharArray.size(); senCharIndex++) {
                //校验内容字符
                String contentChar = contentArrays.getString(contentIndex+senCharIndex);
                //校验敏感词字符
                String sensitiveChar = sensitiveCharArray.getString(senCharIndex);
                //不相等则认为不异常
                if (!contentChar.equalsIgnoreCase(sensitiveChar)){
                    abnormal = false;
                    break;
                }
            }
            // 存在敏感词
            if (abnormal){
                result.append(replaceWords);
                contentIndex = contentIndex + sensitiveCharArray.size()-1;
            }
            else {
                result.append(contentArrays.getString(contentIndex));
            }
        }
        return result.toString();
    }
}
