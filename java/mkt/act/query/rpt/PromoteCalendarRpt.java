package mkt.act.query.rpt;

import common.sal.sys.sync.dao.OrderLineDao;
import common.sal.sys.sync.dao.impl.OrderLineDaoImpl;
import common.sal.util.QFBuilder;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.servicehelper.QueryServiceHelper;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * @author: GK
 * @create: 2024-02-01 17:43
 * @Description:  国别节日&渠道大促活动日历 报表
 */
public class PromoteCalendarRpt extends AbstractFormPlugin {
    private static final String AMAZON_NUMBER = "AMAZON",EBAY_NUMBER = "EBAY";

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs eventArgs) {
        super.afterDoOperation(eventArgs);
        String operateKey = eventArgs.getOperateKey();
        if ("aos_query".equals(operateKey)) {
            queryData();
        }
    }

    /**
     * 查询数据
     */
    public void queryData(){
        List<QFBuilder> filterList = getFilter();
        //获取营收合格的店铺
        List<String> shopList = getRevenueStores();
        //获取活动计划选品表中的数据
        getActSelectList(filterList.get(2));
        //设置国别节日数据


    }

    /**
     * 获取活动库中的 是否平台活动为是的店铺
     */
    public void getAllShop(){
        QFBuilder builder = new QFBuilder();
        builder.add("aos_assessment.number", "=", "Y");
        String orgId = ((DynamicObject)getModel().getValue("aos_sel_org")).getString("id");
        builder.add("aos_org","=",orgId);
        builder.add("aos_act_type","=","1");
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_sal_act_type_p", "aos_shop", builder.toArray());
        List<String> results = new ArrayList<>(dyc.size());
        for (DynamicObject row : dyc) {
            results.add(row.getString("aos_shop"));
        }
    }

    /**
     * 设置节日活动数据
     */
    public void setFestivalData(QFBuilder builder) {
        //首先获取国别节日数据
        DynamicObjectCollection festivalList = QueryServiceHelper.query("aos_mkt_festival", "name,number", builder.toArray());
        for (DynamicObject row : festivalList) {

        }
    }


    /**
     * 获取活动选品表中本月的数据
     */
    public List<Map<String,String>> getActSelectList(QFBuilder builder){
        DynamicObjectCollection planList = QueryServiceHelper.query("aos_act_select_plan", "aos_shop,aos_act_type,aos_festival", builder.toArray());
        Map<String,String> actMap = new HashMap<>(planList.size()),festivalMap = new HashMap<>(planList.size());
        for (DynamicObject row : planList) {
            String shop = row.getString("aos_shop");
            String actType = row.getString("aos_act_type");
            String festival = row.getString("aos_festival");
            if (actType!=null){
                actMap.put(shop,actType);
            }
            if (festival!=null){
                festivalMap.put(shop,festival);
            }
        }
        List<Map<String,String>> result = new ArrayList<>(2);
        result.add(actMap);
        result.add(festivalMap);
        return result;
    }

    /**
     * 获取过滤条件
     * @return  过滤条件
     */
    private List<QFBuilder> getFilter(){
        List<QFBuilder> result = new ArrayList<>(3);
        //国别节日库过滤条件，渠道大促活动库过滤条件，活动选品表过滤条件
        QFBuilder orgFilterArray = new QFBuilder(),channelFilterArray = new QFBuilder(),actFilterArray = new QFBuilder();
        Object org = getModel().getValue("aos_sel_org");
        if (org!=null){
            String orgId = ((DynamicObject)org).getString("id");
            orgFilterArray.add("aos_orgid","=",orgId);
            channelFilterArray.add("aos_org","=",orgId);
            actFilterArray.add("aos_nationality","=",orgId);
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

        yearCal.add(Calendar.MONTH,1);
        orgFilterArray.add("aos_date","<",yearCal.getTime());
        channelFilterArray.add("aos_start","<",yearCal.getTime());
        actFilterArray.add("aos_startdate","<=",yearCal.getTime());

        result.add(orgFilterArray);
        result.add(channelFilterArray);
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
