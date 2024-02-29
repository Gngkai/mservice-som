package mkt.act.rule;

import common.fnd.FndMsg;
import common.sal.EventRuleCommon;
import common.sal.util.LogUtils;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.OperationContext;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.datamodel.events.ImportDataEventArgs;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.threads.ThreadPools;
import mkt.act.rule.Import.EventRule;
import mkt.act.rule.service.ActPlanService;
import mkt.act.rule.service.impl.ActPlanServiceImpl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import common.fnd.AosomLog;

/**
 * 活动信息计算
 */
public class ActInfoCalEdit extends AbstractBillPlugIn {

	private static AosomLog logger = AosomLog.init("ActInfoCalEdit");
    static{
		logger.setService("aos.mms");
		logger.setDomain("mms.act");
    }
    private static final ActPlanService actPlanService = new ActPlanServiceImpl();

    @Override
    public void registerListener(EventObject e) {
        this.addItemClickListeners("tbmain");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        String itemKey = evt.getItemKey();
        if ("aos_actinfo".equals(itemKey)) {
            calActInfo();
        }
    }

    /**
     * 导入数据后触发
     * @param
     */
    @Override
    public void afterImportData(ImportDataEventArgs e) {
        super.afterImportData(e);
        DynamicObject dataEntity = this.getModel().getDataEntity(true);
        String actstatus = dataEntity.getString("aos_actstatus");
        dataEntity.set("aos_actstatus","E");
        String name = "aos_sal_act_from.afterImportData"+ LocalDateTime.now();
        ThreadPools.executeOnce(name, new ActInfo(dataEntity,actstatus), OperationContext.get());

    }
    class ActInfo implements Runnable{
        private DynamicObject dy_the;
        private String actStatus;
        private Log log;
        String billno;
        ActInfo (DynamicObject dy_the,String actStatus){
            this.dy_the = dy_the;
            this.actStatus = actStatus;
            billno  = dy_the.getString("billno");
            log = LogFactory.getLog("sal.act.ActShopProfit.aos_sal_act_from.ActInfo");
        }
        @Override
        public void run() {
            try {
                log.info(billno+"导入开始");
                setItemInfo();
                new EventRule(dy_the);
                // 赋值库存信息、活动信息
                EventRuleCommon.updateActInfo(dy_the);
            }catch (Exception ex){
                StringWriter sw=new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                ex.printStackTrace();
                log.info(billno+"  导入异常\n"+sw);
            }
            finally {
                log.info(billno+"单据头数据汇总完成");
                dy_the.set("aos_actstatus",actStatus);
                SaveServiceHelper.save(new DynamicObject[]{dy_the});
                log.info(billno+"保存完成");
            }
        }
        private void setItemInfo() throws ParseException {
            String orgid = dy_the.getDynamicObject("aos_nationality").get("id").toString();
            String shopid = dy_the.getDynamicObject("aos_shop").get("id").toString();
            DynamicObjectCollection dyc_ent = dy_the.getDynamicObjectCollection("aos_sal_actplanentity");

            //对引入模板中的活动结束时间进行筛选，并进行赋值
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date latedate = sdf.parse("2019-11-1 23:59:59");
            for (DynamicObject dynamicObject:dyc_ent) {
                if (dynamicObject.get("aos_l_actstatus").equals("A")){
                    Date aos_enddate = (Date) dynamicObject.get("aos_enddate");
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(aos_enddate);
                    calendar.set(Calendar.HOUR_OF_DAY,23);
                    calendar.set(Calendar.MINUTE,59);
                    calendar.set(Calendar.SECOND,59);
                    aos_enddate = calendar.getTime();
                    if (aos_enddate!=null && latedate.compareTo(aos_enddate) < 0){
                        latedate = aos_enddate;
                    }
                }
            }
            if (! latedate.equals("2019-11-1 23:59:59")){
                dy_the.set("aos_enddate1",latedate);
            }

            log.info(billno+"活动结束时间赋值完成");

            //获取物料和价格
            Map<String, BigDecimal[]> map_itemAndPrice = new HashMap<>();
            Map<String,String> map_itemToDate = new HashMap<>();
            //记录活动价超过原价的数据
            List<String> abnormalPriceList = new ArrayList<>();

            for (int i = 0; i < dyc_ent.size(); i++) {
                DynamicObject dy = dyc_ent.get(i);
                StringJoiner str = new StringJoiner("/");
                str.add(String.valueOf(i));
                DynamicObject itemnumDy = dy.getDynamicObject("aos_itemnum");
                if (itemnumDy == null) {
                    continue;
                }
                str.add(itemnumDy.getString("id"));
                BigDecimal[] big_price = new BigDecimal[3];
                big_price[0] = BigDecimal.ZERO;   //原价
                if (dy.get("aos_price")!=null)
                    big_price[0] = dy.getBigDecimal("aos_price");
                big_price[1] = BigDecimal.ZERO;   //活动价
                if (dy.get("aos_actprice")!=null)
                    big_price[1] = dy.getBigDecimal("aos_actprice");
                big_price[2] = BigDecimal.ZERO;
                if (dy.get("aos_actqty")!=null)
                    big_price[2] = dy.getBigDecimal("aos_actqty");
                map_itemAndPrice.put(str.toString(),big_price);
                //活动价超过了原价
                if (big_price[1].compareTo(big_price[0])>0){
                    abnormalPriceList.add(itemnumDy.getString("number"));
                }
                if (dy.get("aos_l_startdate")!=null&&dy.get("aos_enddate")!=null){
                    LocalDate date_s = dy.getDate("aos_l_startdate").toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    LocalDate date_e = dy.getDate("aos_enddate").toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    map_itemToDate.put(str.toString(),date_s.toString()+"/"+date_e.toString());
                }
            }
            log.info(billno+"获取物料和价格完成：  "+map_itemAndPrice.size());

            //计算毛利率
            Map<String, Map<String, BigDecimal>> map_profit = EventRuleCommon.get_formula(orgid, shopid,map_itemToDate, map_itemAndPrice);
            log.info(billno+"计算毛利率完成：  "+map_profit.size());

            Map<String,Map<String,BigDecimal>> map_itemData = new HashMap<>();
            for (Map.Entry<String, Map<String, BigDecimal>> entry : map_profit.entrySet()) {
                if (map_itemAndPrice.containsKey(entry.getKey())){
                    Map<String, BigDecimal> map_itemPartData = entry.getValue();
                    BigDecimal[] big_price = map_itemAndPrice.get(entry.getKey());
                    map_itemPartData.put("aos_price",big_price[0]);
                    map_itemPartData.put("aos_actprice",big_price[1]);
                    map_itemPartData.put("aos_actqty",big_price[2]);
                    map_itemData.put(entry.getKey(),map_itemPartData);
                }
            }

            Map<String, BigDecimal> map_revenue = EventRuleCommon.get_revenue(orgid, shopid, map_itemData);//营收(去税)
            log.info(billno+"计算营收完成：  "+map_revenue.size());
            Map<String, BigDecimal> map_cost = EventRuleCommon.get_cost(orgid, shopid, map_itemData);//成本(去税)
            log.info(billno+"计算成本完成：  "+map_cost.size());
            for (int i = 0; i < dyc_ent.size(); i++) {
                DynamicObject dy = dyc_ent.get(i);
                StringJoiner itemKey =new StringJoiner("/");
                itemKey.add(String.valueOf(i));
                itemKey.add(dy.getDynamicObject("aos_itemnum").getString("id"));
                if (map_profit.containsKey(itemKey.toString())) {
                    Map<String, BigDecimal> map_the = map_profit.get(itemKey.toString());
                    dy.set("aos_profit",map_the.get("value"));
                    dy.set("aos_item_cost",map_the.get("aos_item_cost"));
                    dy.set("aos_lowest_fee",map_the.get("aos_lowest_fee"));
                    dy.set("aos_plat_rate",map_the.get("aos_plat_rate"));
                    dy.set("aos_vat_amount",map_the.get("aos_vat_amount"));
                    dy.set("aos_excval",map_the.get("aos_excval"));
                    if (map_revenue.containsKey(itemKey.toString())) {
                        dy.set("aos_revenue_tax_free",map_revenue.get(itemKey.toString()));//营收(去税)
                    }
                    if (map_cost.containsKey(itemKey.toString())) {
                        dy.set("aos_cost_tax_free",map_cost.get(itemKey.toString()));//成本(去税)
                    }
                }

            }
            log.info(billno+"单据行设置完成");
            dyc_ent.stream()
                    .filter(dy->dy.getString("aos_itemname")==null||dy.getString("aos_itemname").equals(""))
                    .forEach(dy->{
                        String name = dy.getDynamicObject("aos_itemnum").getString("name");
                        dy.set("aos_itemname",name);
                    });
            //设置表头数据
            EventRuleCommon.collectRevenueCost(dy_the);
            //发送消息
            senMessage(abnormalPriceList);
        }

        private void senMessage(List<String> abnormalPriceList){
            if (abnormalPriceList.size()>0){
                String text = String.join(",", abnormalPriceList);
                String receiver = String.valueOf(RequestContext.get().getCurrUserId());
                FndMsg.SendGlobalMessage(receiver,"aos_act_select_plan",dy_the.getString("id"),"活动价格异常",text);
            }
        }
    }

    private void calActInfo() {
        DynamicObject dataEntity = this.getModel().getDataEntity(true);
        DynamicObject aos_nationality = dataEntity.getDynamicObject("aos_nationality");
        if (aos_nationality == null) {
            this.getView().showTipNotification("请选择国别!");
            return;
        }
        // 获取活动开始日期
        Date aos_startdate = dataEntity.getDate("aos_startdate");
        if (aos_startdate == null) {
            this.getView().showTipNotification("请填写开始日期!");
            return;
        }

        Date aos_enddate1 = dataEntity.getDate("aos_enddate1");
        if (aos_enddate1 == null) {
            this.getView().showTipNotification("请填写结束日期!");
            return;
        }
        // 获取渠道id
        long aos_platformid = dataEntity.getLong("aos_channel.id");
        if (aos_platformid == 0) {
            this.getView().showTipNotification("请选择平台!");
            return;
        }

        String aos_shopnum = dataEntity.getString("aos_shop.number");
        if (aos_shopnum == null || "".equals(aos_shopnum)) {
            this.getView().showTipNotification("请选择店铺!");
            return;
        }

        String aos_acttypenum = dataEntity.getString("aos_acttype.number");
        if (aos_acttypenum == null || "".equals(aos_acttypenum)) {
            this.getView().showTipNotification("请选择活动类型!");
            return;
        }
        actPlanService.updateActInfo(dataEntity);
        this.getView().updateView("aos_sal_actplanentity");
    }
}
