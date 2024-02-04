package mkt.act.query.rpt;

import common.sal.sys.sync.dao.OrderLineDao;
import common.sal.sys.sync.dao.impl.OrderLineDaoImpl;
import common.sal.util.QFBuilder;
import common.sal.util.SalUtil;
import common.sal.util.SaveUtils;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import org.apache.commons.collections4.BidiMap;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * @author: GK
 * @create: 2024-02-01 17:43
 * @Description:  国别节日&渠道大促活动日历 报表
 */
public class PromoteCalendarRpt extends AbstractFormPlugin {
    private static final String AMAZON_NUMBER = "AMAZON",EBAY_NUMBER = "EBAY",NOACT_KEY = "aos_mkt_act_promote";
    private static final String ENTRY_KEY = "aos_entryentity",SUBENTRY_KEY = "aos_subentryentity";

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs eventArgs) {
        super.afterDoOperation(eventArgs);
        String operateKey = eventArgs.getOperateKey();
        if ("aos_query".equals(operateKey)) {
            queryData();
        }
        else if ("save".equals(operateKey)) {
            save();
        }
    }

    /**
     * 保存数据
     */
    private void save(){
        List<DynamicObject> saveList = new ArrayList<>(5000);
        DynamicObjectCollection entryRows = getModel().getDataEntity(true).getDynamicObjectCollection(ENTRY_KEY);
        String orgId = ((DynamicObject) getModel().getValue("aos_sel_org")).getString("id");
        Calendar calendar = Calendar.getInstance();
        Date date = (Date) getModel().getValue("aos_sel_year");
        calendar.setTime(date);
        int year = calendar.get(Calendar.YEAR);

        date = (Date) getModel().getValue("aos_sel_month");
        calendar.setTime(date);
        int month = calendar.get(Calendar.MONTH);

        QFBuilder delBuilder = new QFBuilder();
        delBuilder.add("aos_org","=",orgId);
        delBuilder.add("aos_year","=",year);
        delBuilder.add("aos_month","=",month);
        DeleteServiceHelper.delete(NOACT_KEY,delBuilder.toArray());

        for (DynamicObject entryRow : entryRows) {
            if ("无活动渠道".equals(entryRow.getString("aos_style"))) {
                DynamicObjectCollection subRows = entryRow.getDynamicObjectCollection(SUBENTRY_KEY);
                for (DynamicObject subRow : subRows) {
                    DynamicObject shopEntry = subRow.getDynamicObject("aos_shop");
                    if (shopEntry == null) {
                        continue;
                    }
                    DynamicObject newDy = BusinessDataServiceHelper.newDynamicObject(NOACT_KEY);
                    newDy.set("aos_org",orgId);
                    newDy.set("aos_year",year);
                    newDy.set("aos_month",month);
                    newDy.set("aos_sort",entryRow.getString("aos_sort"));
                    newDy.set("aos_topic",entryRow.getString("aos_topic"));
                    newDy.set("aos_shop",shopEntry.getString("id"));
                    newDy.set("billstatus","A");
                    saveList.add(newDy);
                    SaveUtils.SaveEntity(saveList,false);
                }
            }
        }
        SaveUtils.SaveEntity(saveList,true);

    }

    /**
     * 查询数据
     */
    public void queryData(){
        this.getModel().deleteEntryData("aos_entryentity");
        List<QFBuilder> filterList = getFilter();
        //获取无活动数据
        Map<String, Map<String, List<String>>> noActData = getNoActData(filterList.get(3));
        //获取活动计划选品表中的数据
        List<Map<String, Set<String>>> actSelectList = getActSelectList(filterList.get(2));
        //设置国别节日数据
        DynamicObjectCollection entryRows = getModel().getDataEntity(true).getDynamicObjectCollection(ENTRY_KEY);
        //获取所有店铺
        Map<String, DynamicObject> allShopMap = getAllShop();
        //设置节日活动数据
        setFestivalData(noActData.get("节日活动"), allShopMap,filterList.get(0),actSelectList.get(0),entryRows);
        //设置渠道大促活动数据
        setPromoteData(noActData.get("大型活动"),allShopMap,filterList.get(1),actSelectList.get(1),entryRows);

        getView().updateView();
    }



    /**
     * 设置节日活动数据
     * @param builder   查询条件
     * @param actMap    活动选品表中的活动数据
     * @param entryRows 表体行
     */
    public void setFestivalData ( Map<String, List<String>> noActMap,Map<String, DynamicObject> allShop,QFBuilder builder,
                                 Map<String, Set<String>> actMap, DynamicObjectCollection entryRows) {
        //获取营收合格的店铺
        List<String> revenueShopList = getRevenueStores();

        //首先获取国别节日数据
        DynamicObjectCollection festivalList = QueryServiceHelper.query("aos_mkt_festival", "id,name,number", builder.toArray());
        for (DynamicObject row : festivalList) {
            //节日ID
            String festival = row.getString("id");
            String festivalName = row.getString("name");
            //无活动渠道店铺
            List<String> noActShopList = noActMap.computeIfAbsent(festivalName, k -> new ArrayList<>());

            DynamicObject planRow = entryRows.addNew();
            planRow.set("aos_topic",festivalName);
            planRow.set("aos_sort","节日活动");
            planRow.set("aos_style","有计划渠道");
            DynamicObjectCollection planSubRows = planRow.getDynamicObjectCollection(SUBENTRY_KEY);

            DynamicObject noPlanRow = entryRows.addNew();
            noPlanRow.set("aos_topic",festivalName);
            noPlanRow.set("aos_sort","节日活动");
            noPlanRow.set("aos_style","无计划渠道");
            DynamicObjectCollection noPlanSubRows = noPlanRow.getDynamicObjectCollection(SUBENTRY_KEY);

            DynamicObject noActRow = entryRows.addNew();
            noActRow.set("aos_topic",festivalName);
            noActRow.set("aos_sort","节日活动");
            noActRow.set("aos_style","无活动渠道");
            DynamicObjectCollection noActSubRows = noActRow.getDynamicObjectCollection(SUBENTRY_KEY);

            Set<String> actShopSet = new HashSet<>();
            if (actMap.containsKey(festival)){
                actShopSet =  actMap.get(festival);
            }

            for (Map.Entry<String, DynamicObject> entry : allShop.entrySet()) {
                String shopId = entry.getKey();
                //如果活动选品表中有该店铺
                if (actShopSet.contains(shopId)) {
                    planSubRows.addNew().set("aos_shop",entry.getValue());
                }
                //如果无活动渠道中有该店铺
                else if (noActShopList.contains(shopId)){
                    noActSubRows.addNew().set("aos_shop",entry.getValue());
                }
                //如果满足营收
                else if (revenueShopList.contains(shopId)){
                    noPlanSubRows.addNew().set("aos_shop",entry.getValue());
                }
            }
        }
    }

    /**
     * 获取营收合格的店铺
     * @param actMap    活动选品表中的活动数据
     * @param entryRows 表体行
     */
    public void setPromoteData(Map<String, List<String>> noActMap,Map<String, DynamicObject> allShop,QFBuilder builder,Map<String, Set<String>> actMap, DynamicObjectCollection entryRows){
        //获取渠道大促中的数据
        DynamicObjectCollection promoteList = QueryServiceHelper.query("aos_mkt_cl_promote", "aos_shop,name", builder.toArray());
        //根据渠道大促分类
        Map<String,Set<String>> promoteMap = new HashMap<>();
        for (DynamicObject row : promoteList) {
            String name = row.getString("name");
            promoteMap.computeIfAbsent(name, k -> new HashSet<>()).add(row.getString("aos_shop"));
        }

        for (Map.Entry<String, Set<String>> entry : promoteMap.entrySet()) {
            //渠道大促名称
            String promoteName = entry.getKey();
            //无活动渠道店铺
            List<String> noActShopList = noActMap.computeIfAbsent(promoteName, k -> new ArrayList<>());

            DynamicObject planRow = entryRows.addNew();
            planRow.set("aos_topic",promoteName);
            planRow.set("aos_sort","大型活动");
            planRow.set("aos_style","有计划渠道");
            DynamicObjectCollection planSubRows = planRow.getDynamicObjectCollection(SUBENTRY_KEY);

            DynamicObject noPlanRow = entryRows.addNew();
            noPlanRow.set("aos_topic",promoteName);
            noPlanRow.set("aos_sort","大型活动");
            noPlanRow.set("aos_style","无计划渠道");
            DynamicObjectCollection noPlanSubRows = noPlanRow.getDynamicObjectCollection(SUBENTRY_KEY);

            DynamicObject noActRow = entryRows.addNew();
            noActRow.set("aos_topic",promoteName);
            noActRow.set("aos_sort","大型活动");
            noActRow.set("aos_style","无活动渠道");
            DynamicObjectCollection noActSubRows = noActRow.getDynamicObjectCollection(SUBENTRY_KEY);

            Set<String> actShopSet = new HashSet<>();
            if (actMap.containsKey(promoteName)){
                actShopSet =  actMap.get(promoteName);
            }
            for (String shop : actShopSet) {
                planSubRows.addNew().set("aos_shop",allShop.get(shop));
            }
            for (String shop : entry.getValue()) {
                if (actShopSet.contains(shop)){
                    continue;
                }
                else if (noActShopList.contains(shop)){
                    noActSubRows.addNew().set("aos_shop",allShop.get(shop));
                }
                else {
                    noPlanSubRows.addNew().set("aos_shop",allShop.get(shop));
                }
            }
        }
    }

    /**
     * 获取活动库中的 是否平台活动为是的店铺
     */
    public Map<String,DynamicObject> getAllShop(){
        QFBuilder builder = new QFBuilder();
        builder.add("aos_assessment.number", "=", "Y");
        String orgId = ((DynamicObject)getModel().getValue("aos_sel_org")).getString("id");
        builder.add("aos_org","=",orgId);
        builder.add("aos_shop","!=","");
        Map<String,DynamicObject> shopMap = new HashMap<>();
        for (DynamicObject row : BusinessDataServiceHelper.load("aos_sal_act_type_p", "aos_shop", builder.toArray())) {
            DynamicObject shopEntry = row.getDynamicObject("aos_shop");
            shopMap.put(shopEntry.getString("id"),shopEntry);
        }
        return shopMap;
    }

    /**
     * 查询无活动数据
     */
    public  Map<String,Map<String,List<String>>> getNoActData (QFBuilder builder){
        Map<String,Map<String,List<String>>> noActMap = new HashMap<>();
        noActMap.put("大型活动",new HashMap<>());
        noActMap.put("节日活动",new HashMap<>());
        DynamicObjectCollection dyc = QueryServiceHelper.query(NOACT_KEY, "aos_sort,aos_topic,aos_shop", builder.toArray());
        for (DynamicObject dy : dyc) {
            String sort = dy.getString("aos_sort");
            Map<String, List<String>> typeMap = noActMap.computeIfAbsent(sort, k -> new HashMap<>());
            String topic = dy.getString("aos_topic");
            List<String> shopList = typeMap.computeIfAbsent(topic, k -> new ArrayList<>());
            shopList.add(dy.getString("aos_shop"));
        }
        return noActMap;
    }

    /**
     * 获取活动选品表中本月的数据
     */
    public List<Map<String,Set<String>>> getActSelectList(QFBuilder builder){
        DynamicObjectCollection planList = QueryServiceHelper.query("aos_act_select_plan", "aos_shop,aos_act_type,aos_festival", builder.toArray());
        Map<String,Set<String>> actMap = new HashMap<>(planList.size()),festivalMap = new HashMap<>(planList.size());
        //活动选品表中的 大型活动类型 对应的标题和值
        BidiMap<String, String> comboMapValue = SalUtil.getComboMapValue("aos_act_select_plan", "aos_act_type");
        for (DynamicObject row : planList) {
            String shop = row.getString("aos_shop");
            String actType = row.getString("aos_act_type");
            String festival = row.getString("aos_festival");
            if (actType!=null){
                String actName = comboMapValue.get(actType);
                actMap.computeIfAbsent(actName,k->new HashSet<>()).add(shop);
            }
            if (festival!=null){
                festivalMap.computeIfAbsent(festival,k->new HashSet<>()).add(shop);
            }
        }
        List<Map<String,Set<String>>> result = new ArrayList<>(2);
        result.add(festivalMap);
        result.add(actMap);
        return result;
    }

    /**
     * 获取过滤条件
     * @return  过滤条件
     */
    private List<QFBuilder> getFilter(){
        List<QFBuilder> result = new ArrayList<>(3);
        //国别节日库过滤条件，渠道大促活动库过滤条件，活动选品表过滤条件
        QFBuilder orgFilterArray = new QFBuilder(),channelFilterArray = new QFBuilder(),actFilterArray = new QFBuilder(),noActBuilder = new QFBuilder();
        Object org = getModel().getValue("aos_sel_org");
        if (org!=null){
            String orgId = ((DynamicObject)org).getString("id");
            orgFilterArray.add("aos_orgid","=",orgId);
            channelFilterArray.add("aos_org","=",orgId);
            actFilterArray.add("aos_nationality","=",orgId);
            noActBuilder.add("aos_org","=",orgId);
        }

        Date yearDate = (Date) getModel().getValue("aos_sel_year");
        Date monthDate = (Date) getModel().getValue("aos_sel_month");
        Calendar yearCal = Calendar.getInstance(),monthCal = Calendar.getInstance();
        yearCal.setTime(yearDate);
        monthCal.setTime(monthDate);
        yearCal.set(Calendar.MONTH,monthCal.get(Calendar.MONTH));
        yearCal.set(Calendar.DAY_OF_MONTH,1);

        orgFilterArray.add("aos_date",">=",yearCal.getTime());
        channelFilterArray.add("aos_start",">=",yearCal.getTime());
        actFilterArray.add("aos_enddate1",">=",yearCal.getTime());
        noActBuilder.add("aos_year","=",yearCal.get(Calendar.YEAR));
        noActBuilder.add("aos_month","=",yearCal.get(Calendar.MONTH));

        yearCal.add(Calendar.MONTH,1);
        orgFilterArray.add("aos_date","<",yearCal.getTime());
        channelFilterArray.add("aos_start","<",yearCal.getTime());
        actFilterArray.add("aos_startdate","<=",yearCal.getTime());

        //国别节日库过滤条件
        result.add(orgFilterArray);
        //渠道大促活动库过滤条件
        result.add(channelFilterArray);
        //活动选品表过滤条件
        result.add(actFilterArray);
        //无活动过滤条件
        result.add(noActBuilder);
        return result;
    }

    /**
     *  获取营收合格的店铺
     */
    public List<String> getRevenueStores(){
        QFBuilder orderFilter = new QFBuilder();
        //国别
        String orgId = ((DynamicObject) getModel().getValue("aos_sel_org")).getString("id");
        orderFilter.add("aos_org","=",orgId);
        //日期
        LocalDate startDate = LocalDate.now().withDayOfMonth(1).minusMonths(1);
        orderFilter.add("aos_local_date",">=",startDate.toString());
        orderFilter.add("aos_local_date","<",startDate.plusMonths(1).toString());
        orderFilter.add("aos_platformfid","!=","");
        orderFilter.add("aos_shopfid","!=","");
        orderFilter.add("aos_totalamt",">",0);

        StringJoiner selectFields = new StringJoiner(",");
        selectFields.add("aos_platformfid.number aos_platformfid");
        selectFields.add("aos_shopfid");
        selectFields.add("aos_totalamt");

        OrderLineDao orderLineDao = new OrderLineDaoImpl();
        DynamicObjectCollection orderList =  orderLineDao.queryOrderInfo(selectFields.toString(),orderFilter);

        //计算 国别总营收、亚马逊总营收、ebay总营收
        BigDecimal orgAmt = BigDecimal.ZERO,amazonAmt = BigDecimal.ZERO,ebayAmt = BigDecimal.ZERO;
        //计算店铺总营收
        Map<String,BigDecimal> storeAmtMap = new HashMap<>();
        //记录亚马逊店铺、ebay店铺
        List<String> amStoreList = new ArrayList<>(),ebStoreList = new ArrayList<>();

        for (DynamicObject row : orderList) {
            //平台
            String platform = row.getString("aos_platformfid");
            String shopfid = row.getString("aos_shopfid");
            BigDecimal totalamt = row.getBigDecimal("aos_totalamt");

            if (AMAZON_NUMBER.equals(platform)){
                 amazonAmt = amazonAmt.add(totalamt);
                if (!amStoreList.contains(shopfid)) {
                    amStoreList.add(shopfid);
                }
            }
            else if (EBAY_NUMBER.equals(platform)){
                ebayAmt = ebayAmt.add(totalamt);
                if (!ebStoreList.contains(shopfid)) {
                    ebStoreList.add(shopfid);
                }
            }

            BigDecimal shopAmt = storeAmtMap.getOrDefault(shopfid, BigDecimal.ZERO).add(totalamt);
            storeAmtMap.put(shopfid,shopAmt);
            orgAmt = orgAmt.add(totalamt);

        }
        List<String> results = new ArrayList<>(storeAmtMap.keySet());
        //判断 am渠道营收是否大于5%
        if (orgAmt.compareTo(BigDecimal.ZERO)<=0) {
            return results;
        }
        BigDecimal   rate = amazonAmt.divide(orgAmt,4,BigDecimal.ROUND_HALF_UP);
        boolean amFlag = rate.compareTo(new BigDecimal("0.05")) > 0;
        //判断 eb渠道营收是否大于5%
        rate = ebayAmt.divide(orgAmt,4,BigDecimal.ROUND_HALF_UP);
        boolean ebFlag = rate.compareTo(new BigDecimal("0.05")) > 0;

        for (Map.Entry<String, BigDecimal> entry : storeAmtMap.entrySet()) {
            String shopfid = entry.getKey();
            if (amFlag && amStoreList.contains(shopfid)){
                results.add(shopfid);
            }
            else if (ebFlag && ebStoreList.contains(shopfid)){
                results.add(shopfid);
            }
            else {
                rate = entry.getValue().divide(orgAmt,4,BigDecimal.ROUND_HALF_UP);
                if (rate.compareTo(new BigDecimal("0.05")) > 0){
                    results.add(shopfid);
                }
            }
        }
        return results;
    }
}
