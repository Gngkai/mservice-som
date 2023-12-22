package mkt.act.rule.Import;

import common.fnd.FndLog;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.formula.FormulaEngine;
import kd.bos.servicehelper.QueryServiceHelper;

import java.math.BigDecimal;
import java.util.*;

/**
 * @author: GK
 * @create: 2023-11-20 16:56
 * @Description: 活动库解析函数的工具类，即记录每一行公式的执行结果
 */
public class FormulaResults {
    EventRule eventRule;
    FormulaResults(EventRule eventRule){
        this.eventRule = eventRule;
    }
    /**
     * 执行公式
     * @param key       属性名
     * @param rule      公式
     * @param result    记录结果
     */
    public void getFormulaResult (String key,String rule,Map<String,Boolean> result){
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : eventRule.itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = itemInfoe.getString("aos_contrybrand");
            eventRule.fndLog.add(itemInfoe.getString("number")+"  "+eventRule.rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }
    /**
     * 执行关表达式 Map<String,Integer>
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    public void getFormulaResult(String key,String rule,Map<String,Boolean> result,Map<String,Integer> itemSotck){
        //获取海外库存

        //执行公式的参数
        Map<String,Object> paramars = new HashMap<>();
        //遍历物料
        for (DynamicObject itemInfoe : eventRule.itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            //获取该物料的库存
            Object value = itemSotck.getOrDefault(itemid,0);
            //添加日志
            eventRule.fndLog.add(itemInfoe.getString("number")+"  "+eventRule.rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            //执行公式并且将执行结果添加到返回结果里面
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }
    /**
     * 执行 Map<Long,Integer> 关表达式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    public void getFormulaResultLong(String key,String rule,Map<String,Boolean> result,Map<Long,Integer> itemSotck){
        //获取海外库存

        //执行公式的参数
        Map<String,Object> paramars = new HashMap<>();
        //遍历物料
        for (DynamicObject itemInfoe : eventRule.itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            //获取该物料的库存
            Object value = itemSotck.getOrDefault(Long.parseLong(itemid),0);
            //添加日志
            eventRule.fndLog.add(itemInfoe.getString("number")+"  "+eventRule.rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            //执行公式并且将执行结果添加到返回结果里面
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }
    /**
     * 执行关于参数为Map<String,String> 的表达式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    public void getFormulaResultStr(String key,String rule,Map<String,Boolean> result,Map<String,String> itemStatus){
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : eventRule.itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = itemStatus.getOrDefault(itemid,"0");
            eventRule.fndLog.add(itemInfoe.getString("number")+"  "+eventRule.rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }
    /**
     * 执行关于Review 分数
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    public void  getFormulaResultBde(String key,String rule,Map<String,Boolean> result,Map<String,BigDecimal> reviewStars){
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : eventRule.itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = reviewStars.getOrDefault(itemid, BigDecimal.ZERO);
            eventRule.fndLog.add(itemInfoe.getString("number")+"  "+eventRule.rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }
    /**
     * 执行关于节日属性的公式
     * @param key   参数名
     * @param rule  公式
     * @param result    记录执行结果
     */
    public void getItemFestive(String key,String rule,Map<String,Boolean> result){
        //获取所有的节日属性
        DynamicObjectCollection festRulst = QueryServiceHelper.query("aos_scm_fest_attr", "number,name", null);
        //记录节日属性的 num to name
        Map<String,String> festNum = new HashMap<>(festRulst.size());
        for (DynamicObject row : festRulst) {
            festNum.put(row.getString("number"),row.getString("name"));
        }
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : eventRule.itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            String festival = itemInfoe.getString("festival");
            Object value = festNum.getOrDefault(festival,"");
            eventRule.fndLog.add(itemInfoe.getString("number")+"  "+eventRule.rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }


}
