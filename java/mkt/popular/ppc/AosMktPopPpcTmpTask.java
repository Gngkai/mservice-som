package mkt.popular.ppc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import common.CommonDataSom;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndLog;
import common.sal.util.SalUtil;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.time.DateUtils;

import common.Cux_Common_Utl;
import common.sal.util.InStockAvailableDays;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.cache.CacheFactory;
import kd.bos.cache.DistributeSessionlessCache;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import mkt.common.MKTCom;
import mkt.common.aos_mkt_common_redis;

/**
 * @author aosom
 * @version 临时剔除计算-调度任务类
 */
public class AosMktPopPpcTmpTask extends AbstractTask {
    private static final DistributeSessionlessCache CACHE =
        CacheFactory.getCommonCacheFactory().getDistributeSessionlessCache("mkt_redis");
    private static void executerun() {
        // 初始化数据
        CommonDataSom.init();
        aos_mkt_common_redis.init_redis("ppc");
        Calendar today = Calendar.getInstance();
        int hour = today.get(Calendar.HOUR_OF_DAY);
        QFilter qfTime;
        DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_base_orgvalue", "aos_value",
            new QFilter[] {new QFilter("aos_type", QCP.equals, "TIME")});
        int time = 16;
        if (dynamicObject != null) {
            time = dynamicObject.getBigDecimal("aos_value").intValue();
        }
        if (hour < time) {
            qfTime = new QFilter("aos_is_north_america.number", QCP.not_equals, "Y");
        } else {
            qfTime = new QFilter("aos_is_north_america.number", QCP.equals, "Y");
        }
        // 调用线程池
        QFilter overseaFlag = new QFilter("aos_is_oversea_ou.number", "=", "Y");
        QFilter overseaFlag2 = new QFilter("aos_isomvalid", "=", true);
        QFilter[] filtersOu = new QFilter[] {overseaFlag, overseaFlag2, qfTime};
        DynamicObjectCollection bdCountry = QueryServiceHelper.query("bd_country", "id,number", filtersOu);
        for (DynamicObject ou : bdCountry) {
            String pOuCode = ou.getString("number");
            Map<String, Object> params = new HashMap<>(16);
            params.put("p_ou_code", pOuCode);
            doOperate(params);
        }
    }
    public static void doOperate(Map<String, Object> params) {
        try {
            // 获取缓存
            byte[] serializeItem = CACHE.getByteValue("item");
            HashMap<String, Map<String, Object>> item = SerializationUtils.deserialize(serializeItem);
            // 海外在库数量
            Map<String, Object> itemOverseaqtyMap = item.get("item_overseaqty");
            // 在途数量
            Map<String, Object> itemIntransqtyMap = item.get("item_intransqty");
            byte[] serializeDatepara = CACHE.getByteValue("datepara");
            HashMap<String, Map<String, Object>> datepara = SerializationUtils.deserialize(serializeDatepara);
            // 备货天数
            Map<String, Object> aosShpdayMap = datepara.get("aos_shp_day");
            // 清关天数
            Map<String, Object> aosCleardayMap = datepara.get("aos_clear_day");
            // 海运天数
            Map<String, Object> aosFreightdayMap = datepara.get("aos_freight_day");
            // Amazon店铺货号
            byte[] serializeProductInfo = CACHE.getByteValue("mkt_productinfo");
            HashMap<String, String> mktProductinfo = SerializationUtils.deserialize(serializeProductInfo);
            // PPC系列与组创建日期
            // PPC系列与组创建日期
            // 营销国别参数表
            byte[] serializePoporgInfo = CACHE.getByteValue("mkt_poporginfo");
            HashMap<String, Map<String, Object>> popOrgInfo = SerializationUtils.deserialize(serializePoporgInfo);
            // SKU报告14日
            byte[] serializeSkurpt14 = CACHE.getByteValue("mkt_skurpt14");
            HashMap<String, Map<String, Object>> skuRpt14 = SerializationUtils.deserialize(serializeSkurpt14);
            // 获取传入参数 国别
            Object pOuCode = params.get("p_ou_code");
            Object pOrgId = FndGlobal.get_import_id(pOuCode, "bd_country");
            // 获取当前日期
            Calendar date = Calendar.getInstance();
            date.set(Calendar.HOUR_OF_DAY, 0);
            date.set(Calendar.MINUTE, 0);
            date.set(Calendar.SECOND, 0);
            date.set(Calendar.MILLISECOND, 0);
            Date today = date.getTime();
            date.add(Calendar.DAY_OF_MONTH, -1);
            date.add(Calendar.DAY_OF_MONTH, -1);
            // 获取单号
            int month = date.get(Calendar.MONTH) + 1;
            // 获取春夏品 秋冬品开始结束日期 营销日期参数表
            Date summerSpringStart = MKTCom.Get_DateRange("aos_datefrom", "SS", pOrgId);
            Date summerSpringEnd = MKTCom.Get_DateRange("aos_dateto", "SS", pOrgId);
            Date autumnWinterStart = MKTCom.Get_DateRange("aos_datefrom", "AW", pOrgId);
            Date autumnWinterEnd = MKTCom.Get_DateRange("aos_dateto", "AW", pOrgId);
            // 判断是否季末
            Date summerSpringSeasonEnd = DateUtils.setDays(DateUtils.addDays(summerSpringEnd, -32), 1);
            Date autumnWinterSeasonEnd = DateUtils.setDays(DateUtils.addDays(autumnWinterEnd, -32), 1);
            // 万圣节开始日期
            Date halloweenStart = MKTCom.Get_DateRange("aos_datefrom", "Halloween", pOrgId);
            // 万圣节结束日期
            Date halloweenEnd = MKTCom.Get_DateRange("aos_dateto", "Halloween", pOrgId);
            // 圣诞节开始日期
            Date christmasStart = MKTCom.Get_DateRange("aos_datefrom", "Christmas", pOrgId);
            // 圣诞节结束日期
            Date christmasEnd = MKTCom.Get_DateRange("aos_dateto", "Christmas", pOrgId);
            Map<String, Integer> productNoMap = new HashMap<>(16);
            // 获取营销国别参数
            BigDecimal qtyStandard = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "QTYSTANDARD").get("aos_value");
            Set<String> actAmazonItem = genActAmazonItem(String.valueOf(pOrgId));
            // 循环国别物料
            QFilter filterOu = new QFilter("aos_contryentry.aos_nationality.number", "=", pOuCode);
            QFilter filterType = new QFilter("aos_protype", "=", "N");
            QFilter[] filters = new QFilter[] {filterOu, filterType};
            String selectField =
                "id,aos_productno," + "number," + "aos_cn_name," + "aos_contryentry.aos_nationality.id aos_orgid,"
                    + "aos_contryentry.aos_nationality.number aos_orgnumber,"
                    + "aos_contryentry.aos_seasonseting.number aos_seasonseting,"
                    + "aos_contryentry.aos_seasonseting.name aos_season,"
                    + "aos_contryentry.aos_seasonseting.id aos_seasonid,"
                    + "aos_contryentry.aos_contryentrystatus aos_contryentrystatus,"
                    + "aos_contryentry.aos_festivalseting.number aos_festivalseting,"
                    + "aos_contryentry.aos_firstindate aos_firstindate";
            DynamicObjectCollection bdMaterialS =
                QueryServiceHelper.query("bd_material", selectField, filters, "aos_productno");
            Set<String> eliminateItemSet = getEliminateItem(pOrgId.toString());
            FndLog log = FndLog.init("MKT_PPC临时剔除计算", pOuCode.toString());
            for (DynamicObject bdMaterial : bdMaterialS) {
                // 判断是否跳过
                long itemId = bdMaterial.getLong("id");
                long orgId = bdMaterial.getLong("aos_orgid");
                String aosItemnumer = bdMaterial.getString("number");
                String aosProductno = bdMaterial.getString("aos_productno");
                if (eliminateItemSet.contains(String.valueOf(itemId))) {
                    continue;
                }
                // 获取AMAZON店铺货号 如果没有则跳过
                String aosShopsku = mktProductinfo.get(orgId + "~" + itemId);
                if (FndGlobal.IsNull(aosShopsku)) {
                    continue;
                }
                Date aosFirstindate = bdMaterial.getDate("aos_firstindate");
                String aosSeasonpro = bdMaterial.getString("aos_seasonseting");
                Object aosSeasonid = bdMaterial.get("aos_seasonid");
                if (aosSeasonpro == null) {
                    aosSeasonpro = "";
                }
                String aosItemstatus = bdMaterial.getString("aos_contryentrystatus");
                String aosFestivalseting = bdMaterial.getString("aos_festivalseting");
                if (aosFestivalseting == null) {
                    aosFestivalseting = "";
                }
                if (aosSeasonid == null) {
                    continue;
                }
                Object itemOverseaqty = itemOverseaqtyMap.get(orgId + "~" + itemId);
                if (FndGlobal.IsNull(itemOverseaqty)) {
                    itemOverseaqty = 0;
                }
                if ("C".equals(aosItemstatus) && (int)itemOverseaqty == 0) {
                    continue;// 终止状态且无海外库存 跳过
                }
                Object itemIntransqty = itemIntransqtyMap.get(orgId + "~" + itemId);
                if (FndGlobal.IsNull(itemIntransqty)) {
                    itemIntransqty = 0;
                }
                // 产品号不存在 跳过
                if (FndGlobal.IsNull(aosProductno)) {
                    continue;
                }
                int auto = 0;
                if (productNoMap.get(aosProductno + "-AUTO") != null) {
                    auto = productNoMap.get(aosProductno + "-AUTO");
                }
                productNoMap.put(aosProductno + "-AUTO", auto);
                // =====是否新系列新组判断=====
                // =====结束是否新系列新组判断=====
                String itemCategoryName = CommonDataSom.getItemCategoryName(String.valueOf(itemId));
                String aosCategory2 = "";
                if (FndGlobal.IsNotNull(itemCategoryName)) {
                    String[] split = itemCategoryName.split(",");
                    if (split.length == 3) {
                        aosCategory2 = split[1];
                    }
                }
                // 获取销售组别并赋值
                // 2.获取产品小类
                String itemCategoryId = CommonDataSom.getItemCategoryId(String.valueOf(itemId));
                if (itemCategoryId == null || "".equals(itemCategoryId)) {
                    continue;
                }
                // 3.根据小类获取组别
                String salOrg = CommonDataSom.getSalOrgV2(String.valueOf(pOrgId), itemCategoryId);
                if (salOrg == null || "".equals(salOrg)) {
                    continue;
                }
                // 剔除海外库存
                if ((int)itemOverseaqty <= qtyStandard.intValue() && !"E".equals(aosItemstatus)
                    && !"A".equals(aosItemstatus) && (int)itemOverseaqty == 0) {
                    continue;
                }
                // 剔除过季品
                boolean springbool = "SPRING".equals(aosSeasonpro) || "SPRING_SUMMER".equals(aosSeasonpro)
                    || "SUMMER".equals(aosSeasonpro);
                boolean cond = (springbool && (today.before(summerSpringStart) || today.after(summerSpringEnd)));
                if (cond) {
                    log.add(aosItemnumer + "春夏品剔除");
                    continue;
                }
                cond = (("AUTUMN_WINTER".equals(aosSeasonpro) || "WINTER".equals(aosSeasonpro))
                    && (today.before(autumnWinterStart) || today.after(autumnWinterEnd)));
                if (cond) {
                    log.add(aosItemnumer + "秋冬品剔除");
                    continue;
                }
                // 非节日品剔除预断货 节日品节日2天内剔除 其它节日装饰 圣诞装饰 这两个中类不做预断货剔除
                cond = ((FndGlobal.IsNull(aosFestivalseting)) && !"其它节日装饰".equals(aosCategory2)
                    && !"圣诞装饰".equals(aosCategory2));
                if (cond) {
                    Object orgIdO = Long.toString(orgId);
                    // 备货天数
                    int aosShpDay = (int)aosShpdayMap.get(orgIdO);
                    // 海运天数
                    int aosFreightDay = (int)aosCleardayMap.get(orgIdO);
                    // 清关天数
                    int aosClearDay = (int)aosFreightdayMap.get(orgIdO);
                    String orgidStr = Long.toString(orgId);
                    String itemidStr = Long.toString(itemId);
                    float aos7daysSale = InStockAvailableDays.getOrgItemOnlineAvgQty(orgidStr, itemidStr);
                    int availableDays = InStockAvailableDays.calInstockSalDays(orgidStr, itemidStr);
                    // 季节品 季节品 季末达标 CONTINUE; 剔除
                    boolean isSeasonEnd = false;
                    if (springbool) {
                        if (today.after(summerSpringSeasonEnd)) {
                            // 季末 判断是否达标
                            isSeasonEnd = true;
                            float seasonRate =
                                MKTCom.Get_SeasonRate(orgId, itemId, aosSeasonpro, itemOverseaqty, month);
                            if (!MKTCom.Is_SeasonRate(aosSeasonpro, month, seasonRate)) {
                                continue;
                            }
                        }
                    }
                    if ("AUTUMN_WINTER".equals(aosSeasonpro) || "WINTER".equals(aosSeasonpro)) {
                        // 判断是否季末
                        if (today.after(autumnWinterSeasonEnd)) {
                            isSeasonEnd = true;
                            // 季末 判断是否达标
                            float seasonRate =
                                MKTCom.Get_SeasonRate(orgId, itemId, aosSeasonpro, itemOverseaqty, month);
                            if (!MKTCom.Is_SeasonRate(aosSeasonpro, month, seasonRate)) {
                                continue;
                            }
                        }
                    }
                    // (海外在库+在途数量)/7日均销量)<60 或 满足销售预断货逻辑 则为营销预断货逻辑 且 不能为季节品季末
                    cond = (((((int)itemOverseaqty + (int)itemIntransqty) / aos7daysSale < 60)
                        || (MKTCom.Is_PreSaleOut(orgId, itemId, (int)itemIntransqty, aosShpDay, aosFreightDay,
                            aosClearDay, availableDays)))
                        && !isSeasonEnd);
                } else {
                    cond = (("HALLOWEEN".equals(aosFestivalseting))
                        && (!(today.after(halloweenStart) && today.before(halloweenEnd))));
                    if (cond) {
                        continue;
                    }
                    cond = (("CHRISTMAS".equals(aosFestivalseting))
                        && (!(today.after(christmasStart) && today.before(christmasEnd))));
                }
                if (cond) {
                    continue;
                }
                // Sku报告
                Map<String, Object> skuRptMap14 = skuRpt14.get(orgId + "~" + itemId);
                // 14天ROI
                BigDecimal roi14Days = BigDecimal.ZERO;
                // 过去14天的花费
                BigDecimal aosSpend14Sku = BigDecimal.ZERO;
                if (skuRptMap14 != null && ((BigDecimal)skuRptMap14.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                    roi14Days = ((BigDecimal)skuRptMap14.get("aos_total_sales"))
                        .divide((BigDecimal)skuRptMap14.get("aos_spend"), 2, RoundingMode.HALF_UP);
                    aosSpend14Sku = (BigDecimal)skuRptMap14.get("aos_spend");
                }
                if (roi14Days.compareTo(BigDecimal.valueOf(3)) >= 0) {
                    log.add(aosItemnumer + "14日ROI " + roi14Days + " 大于等于3");
                }
                if (!Cux_Common_Utl.IsNull(aosFestivalseting)) {
                    log.add(aosItemnumer + "节日属性不为空" + aosFestivalseting);
                }
                if ("AUTUMN_WINTER".equals(aosSeasonpro) || "WINTER".equals(aosSeasonpro)) {
                    log.add(aosItemnumer + "秋冬春夏品");
                }
                if (aosSpend14Sku.compareTo(BigDecimal.valueOf(0)) <= 0) {
                    log.add(aosItemnumer + "14日花费 " + aosSpend14Sku + " 小于等于0");
                }
                if ("其它节日装饰".equals(aosCategory2) || "圣诞装饰".equals(aosCategory2)) {
                    log.add(aosItemnumer + "产品类别" + aosCategory2);
                }
                if (aosFirstindate == null || MKTCom.GetBetweenDays(today, aosFirstindate) < 30) {
                    log.add(aosItemnumer + "首次入库日期" + today);
                }
                if (actAmazonItem.contains(aosItemnumer)) {
                    log.add(aosItemnumer + "有做活动");
                }
                // 14日ROI小于3,节日属性为空,且不为秋冬品,且中类不为 其它节日装饰 圣诞装饰 ，30天内入库新品。
                cond = (roi14Days.compareTo(BigDecimal.valueOf(3)) < 0 && Cux_Common_Utl.IsNull(aosFestivalseting)
                    && !"AUTUMN_WINTER".equals(aosSeasonpro) && !"WINTER".equals(aosSeasonpro)
                    && aosSpend14Sku.compareTo(BigDecimal.valueOf(0)) > 0 && !"其它节日装饰".equals(aosCategory2)
                    && !"圣诞装饰".equals(aosCategory2) && !actAmazonItem.contains(aosItemnumer)
                    && ((aosFirstindate != null) && MKTCom.GetBetweenDays(today, aosFirstindate) >= 30));
                if (cond) {
                    DynamicObject aosMktPopppcCal = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popppc_cal");
                    aosMktPopppcCal.set("aos_orgid", pOrgId);
                    aosMktPopppcCal.set("aos_productno", aosProductno + "-AUTO");
                    aosMktPopppcCal.set("aos_group", aosItemnumer);
                    OperationServiceHelper.executeOperate("save", "aos_mkt_popppc_cal",
                        new DynamicObject[] {aosMktPopppcCal}, OperateOption.create());
                }
            }
            log.finnalSave();
        } catch (Exception ex) {
            FndError.sendMMS(SalUtil.getExceptionStr(ex));
        }
    }

    public static Set<String> genActAmazonItem(String aosOrgid) {
        String[] channelArr = {"AMAZON"};
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        Date dateFrom = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, 7);
        Date dateTo = calendar.getTime();
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterDate1 = new QFilter("aos_sal_actplanentity.aos_l_startdate", ">=", dateFromStr)
            .and("aos_sal_actplanentity.aos_l_startdate", "<=", dateToStr);
        QFilter filterDate2 = new QFilter("aos_sal_actplanentity.aos_enddate", ">=", dateFromStr)
            .and("aos_sal_actplanentity.aos_enddate", "<=", dateToStr);
        QFilter filterDate3 = new QFilter("aos_sal_actplanentity.aos_l_startdate", "<=", dateFromStr)
            .and("aos_sal_actplanentity.aos_enddate", ">=", dateToStr);
        QFilter filterDate = filterDate1.or(filterDate2).or(filterDate3);
        DataSet dataSet = QueryServiceHelper.queryDataSet("aos_mkt_popppc_init.queryApartFromAmzAndEbayItem",
            "aos_act_select_plan", "aos_sal_actplanentity.aos_itemnum.number aos_itennum, 1 as count",
            new QFilter[] {new QFilter("aos_nationality", QCP.equals, aosOrgid),
                new QFilter("aos_channel.number", QCP.in, channelArr), new QFilter("aos_actstatus", QCP.equals, "B"),
                new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"), filterDate},
            null);
        DataSet finish = dataSet.groupBy(new String[] {"aos_itennum"}).sum("count").finish();
        Set<String> itemSet = new HashSet<>();
        while (finish.hasNext()) {
            Row next = finish.next();
            String aosItennum = next.getString("aos_itennum");
            Integer count = next.getInteger("count");
            if (count > 0) {
                itemSet.add(aosItennum);
            }
        }
        dataSet.close();
        finish.close();
        return itemSet;
    }

    private static Set<String> getEliminateItem(String aosOrgid) {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_dataeliminate", "aos_itemid",
            new QFilter[] {new QFilter("aos_orgid", QCP.equals, aosOrgid)});
        return list.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        // 多线程执行
        executerun();
    }
}
