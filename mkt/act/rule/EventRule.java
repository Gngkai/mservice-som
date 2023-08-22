package mkt.act.rule;

import common.fms.util.FieldUtils;
import common.fnd.FndGlobal;
import common.sal.sys.basedata.dao.ItemDao;
import common.sal.sys.basedata.dao.impl.ItemDaoImpl;
import common.sal.sys.sync.dao.ItemStockDao;
import common.sal.sys.sync.dao.impl.ItemStockDaoImpl;
import common.sal.sys.sync.service.ItemCacheService;
import common.sal.sys.sync.service.impl.ItemCacheServiceImpl;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.fi.bd.util.QFBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * @author create by gk
 * @date 2023/8/21 14:11
 * @action  活动规则信息
 */
public class EventRule {
    DynamicObject typEntity;
    Map<String,String> rowRule,rowRuleType;
    DynamicObject orgEntity;
    EventRule(Object typeID,DynamicObject dy_main){
        this.typEntity = BusinessDataServiceHelper.loadSingle(typeID, "aos_sal_act_type_p");
        //活动规则标识
        String aos_rule_v = typEntity.getString("aos_rule_v");
        rowRule = new HashMap<>();
        rowRuleType = new HashMap<>();
        parsingRule();
        orgEntity = dy_main.getDynamicObject("aos_nationality");
    }

    /**
     * 解析规则
     */
    private  void parsingRule (){
        //规则单据体
        DynamicObjectCollection rulEntity = typEntity.getDynamicObjectCollection("aos_entryentity2");
        StringBuilder rule = new StringBuilder();
        for (DynamicObject row : rulEntity) {
            //序列号
            String seq = row.getString("seq");
            rule.setLength(0);
            //规则类型
            String project = row.getString("aos_project");
            cachedData(project,row);
            rule.append(project);
            rule.append(" ");
            //匹配类型
            String condite = row.getString("aos_condite");
            rule.append(condite);
            rule.append(" ");
            //值
            String value = row.getString("aos_rule_value");

            if (condite.equals("in")){
                String[] split = value.split(",");
                rule.append(" ( ");
                for (int i = 0; i < split.length; i++) {
                    rule.append("'");
                    rule.append(split[i]);
                    rule.append("'");
                    if (i<split.length-1)
                        rule.append(",");
                }
                rule.append(" ) ");
            }
            else {
                rule.append(value);
            }
            rowRule.put(seq,rule.toString());
            rowRuleType.put(seq,project);
        }
    }

    private void cachedData (String dataType,DynamicObject row){
        switch (dataType){
            case "overseasStock":
                //海外库存
                setItemStock(); break;
            case "saleDays":
                //可售天数
                setSaleDays();
                break;
            case "itemStatus":
                //物料状态
                setItemStatus();
                break;
            case "season":
                //季节属性
                setItemSeason();
                break;
            case "countyAverage":
                setItemAverage(row);
                break;
        }
    }

    Map<Long,Integer> itemSotck;
    private void setItemStock(){
        if (itemSotck!=null) {
            return;
        }
        itemSotck = new HashMap<>();
        ItemStockDao stockDao = new ItemStockDaoImpl();
        itemSotck = stockDao.listItemOverSeaStock(orgEntity.getLong("id"));
    }

    Map<String,Integer> itemSaleDay;
    private void setSaleDays(){
        if (itemSaleDay !=null) {
            return;
        }
        QFBuilder builder = new QFBuilder();
        builder.add("aos_ou_code","=",orgEntity.getString("number"));
        builder.add("aos_item_code","!=","");
        builder.add("aos_7avadays",">=",0);
        DynamicObjectCollection results = QueryServiceHelper.query("aos_sal_dw_invavadays", "aos_item_code,aos_7avadays", builder.toArray());
        for (DynamicObject result : results) {
            itemSaleDay.put(result.getString("aos_item_code"),result.getInt("aos_7avadays"));
        }

    }

    Map<String,String> itemStatus;
    private void setItemStatus(){
        if (itemSotck!=null) {
            return;
        }
        ItemDao itemDao = new ItemDaoImpl();
        Map<Long, String> result = itemDao.listItemStatus(orgEntity.getLong("id"));
        //获取下拉列表
        Map<String, String> countryStatus = FieldUtils.getComboMap("bd_material", "aos_contryentrystatus");
        itemSotck = new HashMap<>(result.size());
        for (Map.Entry<Long, String> entry : result.entrySet()) {
            itemStatus.put(entry.getKey().toString(),countryStatus.get(entry.getValue()));
        }

    }

    Map<String,String> itemSeason;
    private void setItemSeason(){
        if (itemSeason!=null) {
            return;
        }
        ItemDao itemDao = new ItemDaoImpl();
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        str.add("aos_contryentry.aos_seasonseting.name aos_seasonseting");
        QFilter filter = new QFilter("aos_contryentry.aos_nationality","=",orgEntity.getPkValue());
        DynamicObjectCollection results = itemDao.listItemObj(str.toString(), filter, null);
        itemSeason = new HashMap<>(results.size());
        for (DynamicObject result : results) {
            itemSeason.put(result.getString("id"),result.getString("aos_seasonseting"));
        }
    }

    private void setItemAverage(DynamicObject row){
        int days = 7;
        if (FndGlobal.IsNotNull(row.get("aos_rule_day")))
            days = row.getInt("aos_rule_day");
        ItemCacheServiceImpl.calOnlineSalesCache(orgEntity.getLong("id"),days);

    }

    /**
     * 获取在某个日期前的入局数量
     */
    private Map<String,Integer> inventQty(){return  null;};
}
