package mkt.act.select;

import common.CommonDataSom;
import common.Cux_Common_Utl;
import common.fnd.AosomLog;
import common.fnd.FndDate;
import common.fnd.FndGlobal;
import common.sal.util.InStockAvailableDays;
import common.sal.util.SalUtil;
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
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.threads.ThreadPools;
import mkt.common.AosMktGenUtil;
import mkt.common.MKTCom;
import mkt.common.AosMktCacheUtil;
import org.apache.commons.lang3.SerializationUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class aos_mkt_actselect_init extends AbstractTask {

    private static final DistributeSessionlessCache cache =
        CacheFactory.getCommonCacheFactory().getDistributeSessionlessCache("mkt_redis");

    private static final AosomLog logger = AosomLog.init("aos_mkt_actselect_init");

    static {
        logger.setService("aos.mms");
        logger.setDomain("mms.act");
    }

    public static void ManualitemClick() {
        executerun();
    }

    private static void executerun() {
        // 初始化数据
        CommonDataSom.init();
        AosMktCacheUtil.initRedis("act");
        // 调用线程池
        // long is_oversea_flag = aos_sal_sche_pub.get_lookup_values("AOS_YES_NO", "Y");
        QFilter oversea_flag = new QFilter("aos_isomvalid", "=", "1").or("number", QCP.equals, "IE");// 是否销售国别为是
        QFilter[] filters_ou = new QFilter[] {oversea_flag};
        DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number", filters_ou);
        for (DynamicObject ou : bd_country) {
            String p_ou_code = ou.getString("number");
            Map<String, Object> params = new HashMap<>();
            params.put("p_ou_code", p_ou_code);
            MktActSelectRunnable mktActSelectRunnable = new MktActSelectRunnable(params);
            ThreadPools.executeOnce("MKT_活动选品初始化_" + p_ou_code, mktActSelectRunnable);
        }
    }

    public static void do_operate(Map<String, Object> params) {
        byte[] serialize_item = cache.getByteValue("item");
        HashMap<String, Map<String, Object>> item = SerializationUtils.deserialize(serialize_item);
        Map<String, Object> item_maxage_map = item.get("item_maxage");
        Map<String, Object> item_overseaqty_map = item.get("item_overseaqty");
        Map<String, Object> item_intransqty_map = item.get("item_intransqty");
        byte[] serialize_datepara = cache.getByteValue("datepara");
        HashMap<String, Map<String, Object>> datepara = SerializationUtils.deserialize(serialize_datepara);
        Map<String, Object> aos_shpday_map = datepara.get("aos_shp_day");
        Map<String, Object> aos_clearday_map = datepara.get("aos_clear_day");
        Map<String, Object> aos_freightday_map = datepara.get("aos_freight_day");
        Calendar date = Calendar.getInstance();
        int year = date.get(Calendar.YEAR);// 当前年
        int month = date.get(Calendar.MONTH) + 1;// 当前月
        int monthOri = date.get(Calendar.MONTH);
        int day = date.get(Calendar.DAY_OF_MONTH);// 当前日

        int week = date.get(Calendar.DAY_OF_WEEK); // 当前周

        Object p_ou_code = params.get("p_ou_code");
        // 获取当前国别下所有活动物料已提报次数
        Map<String, Integer> alreadyActivityTimes = getAlreadyActivityTimes(p_ou_code);
        HashMap<String, Integer> Order7Days = AosMktGenUtil.generateOrder7Days(p_ou_code);// 7天销量
        QFilter filter_ou = new QFilter("aos_contryentry.aos_nationality.number", "=", p_ou_code);
        QFilter filter_itemtype = new QFilter("aos_protype", "=", "N");
        QFilter[] filters = new QFilter[] {filter_ou, filter_itemtype};
        String SelectField = "id," + "number," + "aos_cn_name," + "aos_contryentry.aos_nationality.id aos_orgid,"
            + "aos_contryentry.aos_nationality.number aos_orgnumber,"
            + "aos_contryentry.aos_seasonseting.number aos_seasonseting,"
            + "aos_contryentry.aos_contryentrystatus aos_contryentrystatus,"
            + "aos_contryentry.aos_festivalseting aos_festivalseting,"
            + "aos_contryentry.aos_is_saleout aos_is_saleout";

        DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", SelectField, filters);
        int rows = bd_materialS.size();
        int count = 0;
        // 清除同维度
        QFilter filter_type = new QFilter("aos_type_code", "=", "MKT_活动选品初始化");
        QFilter filter_group = new QFilter("aos_groupid", "=", p_ou_code.toString() + year + month + day);
        filters = new QFilter[] {filter_type, filter_group};
        DeleteServiceHelper.delete("aos_sync_log", filters);
        QFilter filter_bill = new QFilter("billno", "=", p_ou_code.toString() + year + month + day);
        filters = new QFilter[] {filter_bill};
        DeleteServiceHelper.delete("aos_mkt_actselect", filters);
        // 初始化保存对象
        DynamicObject aos_mkt_actselect = BusinessDataServiceHelper.newDynamicObject("aos_mkt_actselect");
        aos_mkt_actselect.set("billno", p_ou_code.toString() + year + month + day);
        aos_mkt_actselect.set("billstatus", "A");
        DynamicObjectCollection aos_entryentityS = aos_mkt_actselect.getDynamicObjectCollection("aos_entryentity");
        DynamicObject aos_sync_log = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
        aos_sync_log.set("aos_type_code", "MKT_活动选品初始化");
        aos_sync_log.set("aos_groupid", p_ou_code.toString() + year + month + day);
        aos_sync_log.set("billstatus", "A");
        DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");
        List<String> list_unSaleItemid = getNewestUnsaleDy(String.valueOf(p_ou_code));

        // 并上营销国别滞销货号
        filters = new QFilter[] {new QFilter("aos_orgid.number", QCP.equals, p_ou_code)};
        List<String> unsalableProducts = QueryServiceHelper.query("aos_base_stitem", "aos_itemid", filters).stream()
            .map(dy -> dy.getString("aos_itemid")).filter(FndGlobal::IsNotNull).distinct().collect(Collectors.toList());

        // 获取本月第一份排名改善汇总表
        Set<String> firstRank = getFirstRank(p_ou_code);
        Map<String, Object> cateSeason = getCateSeason(p_ou_code); // aos_mkt_cateseason
        Set<String> distSkuSet = getDistSku(p_ou_code);
        Set<String> stSkuSet = getStSku(p_ou_code);

        for (DynamicObject bd_material : bd_materialS) {
            count++;
            // 判断是否跳过
            long l1 = System.currentTimeMillis();
            long item_id = bd_material.getLong("id");
            String itemIdStr = String.valueOf(item_id);
            long org_id = bd_material.getLong("aos_orgid");
            String aos_itemnumber = bd_material.getString("number");
            try {
                String aos_seasonpro = bd_material.getString("aos_seasonseting");
                String aos_itemstatus = bd_material.getString("aos_contryentrystatus");
                String aos_festivalseting = bd_material.getString("aos_festivalseting");
                String aos_orgnumber = bd_material.getString("aos_orgnumber");
                String aos_itemtype = null;
                boolean saleout = bd_material.getBoolean("aos_is_saleout"); // 是否爆品
                Object item_intransqty = item_intransqty_map.get(org_id + "~" + item_id);
                Object org_id_o = Long.toString(org_id);
                int aos_shp_day = (int)aos_shpday_map.get(org_id_o);// 备货天数
                int aos_freight_day = (int)aos_clearday_map.get(org_id_o);// 海运天数
                int aos_clear_day = (int)aos_freightday_map.get(org_id_o);// 清关天数
                String orgid_str = Long.toString(org_id);
                String itemid_str = Long.toString(item_id);
                // 库存可售天数 非平台仓库用量
                int availableDays = InStockAvailableDays.calInstockSalDays(orgid_str, itemid_str);
                Object item_overseaqty = item_overseaqty_map.get(org_id + "~" + item_id);
                int availableDaysByPlatQty =
                    InStockAvailableDays.calAvailableDaysByPlatQty(String.valueOf(org_id), String.valueOf(item_id));
                Object item_maxage = item_maxage_map.get(org_id + "~" + item_id);
                // 产品状态 季节属性
                if (aos_itemstatus == null || aos_itemstatus.equals("null") || aos_seasonpro == null
                    || aos_seasonpro.equals("null")) {
                    MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "季节属性为空");
                    continue;
                }
                // 最大库龄≥15天
                boolean flag1 = item_maxage == null || item_maxage.equals("null") || (int)item_maxage < 15;// 是否剔除
                // 海外库存>30
                boolean flag2 = false;// 是否剔除
                if (item_overseaqty == null || item_overseaqty.equals("null")) {
                    item_overseaqty = 0;
                }
                if (item_overseaqty == null || item_overseaqty.equals("null") || (int)item_overseaqty <= 30) {
                    flag2 = true;
                }
                // 平台仓库数量可售天数
                boolean flag3 = availableDaysByPlatQty < 120;// 是否剔除

                boolean flag4 = availableDaysByPlatQty < 60;// 是否剔除

                // (最大库龄 < 15 || 海外库存 <= 30) && 平台仓可售天数 < 120
                if ((flag1 || flag2) && flag3) {
                    if (flag1) {
                        MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "最大库龄<15天");
                    }
                    if (flag2) {
                        MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "海外库存<30");
                    }
                    MKTCom.Put_SyncLog(aos_sync_logS,
                        aos_itemnumber + "平台仓数量可售天数:" + availableDaysByPlatQty + " < 120");
                    continue;
                }

                // 7天日均销量dd
                float R = InStockAvailableDays.getOrgItemOnlineAvgQty(orgid_str, itemid_str);

                // 可提报活动数量 > 5 (当前可售库存数量+在途)*活动数量占比-已提报的未来60天的活动数量
                if (item_intransqty == null || item_intransqty.equals("null"))
                    item_intransqty = 0;
                // 活动数量占比
                BigDecimal ActQtyRate = MKTCom.Get_ActQtyRate(aos_itemstatus, aos_seasonpro, aos_festivalseting);
                if (ActQtyRate == null) {
                    MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "活动数量占比为空");
                    continue;
                }
                // 已提报未来60天活动数量
                int Act60PreQty = MKTCom.Get_Act60PreQty(org_id, item_id);
                BigDecimal AvaQty = new BigDecimal((int)item_overseaqty + (int)item_intransqty);
                // 可提报活动数量
                MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + ActQtyRate + "活动数量占比");
                MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + item_overseaqty + "海外库存");
                MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + item_intransqty + "在途数量");
                MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + Act60PreQty + "已提报未来60天活动数量");
                int aos_qty = ActQtyRate.multiply(AvaQty).intValue() - Act60PreQty;
                MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + aos_qty + "可提报活动数量");
                if (aos_qty <= 5) {
                    MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "可提报活动数量<=5");
                    // continue;
                }

                String aos_typedetail = "";// 滞销类型 低动销 低周转

                // 7天销量
                int day7Sales = (int)Cux_Common_Utl.nvl(Order7Days.get(String.valueOf(item_id)));

                float SeasonRate = 0;

                // 判断当前月份
                Boolean speFlag = false;
                if (/* monthOri == Calendar.SEPTEMBER && */unsalableProducts.contains(String.valueOf(item_id))) {
                    boolean preSaleOut = MKTCom.Is_PreSaleOut(org_id, item_id, (int)item_intransqty, aos_shp_day,
                        aos_freight_day, aos_clear_day, availableDays);
                    if (!preSaleOut) {
                        speFlag = true;
                        MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "九月春夏滞销不预断货货号");
                    }
                }

                // 获取数据
                String aos_sku = bd_material.getString("number");
                String aos_itemname = bd_material.getString("aos_cn_name");
                String category = (String)SalUtil.getCategoryByItemId(String.valueOf(item_id)).get("name");
                String[] category_group = category.split(",");
                String aos_category1 = "";
                String aos_category2 = "";
                String aos_category3 = "";
                // 处理数据
                int category_length = category_group.length;
                if (category_length > 0)
                    aos_category1 = category_group[0];
                if (category_length > 1)
                    aos_category2 = category_group[1];
                if (category_length > 2)
                    aos_category3 = category_group[2];

                if (!speFlag) {
                    // 日均销量与可售库存天数参数申明
                    String aos_seasonprostr = null;
                    if (aos_seasonpro.equals("AUTUMN_WINTER") || aos_seasonpro.equals("WINTER"))
                        aos_seasonprostr = "AUTUMN_WINTER_PRO";
                    else if (aos_seasonpro.equals("SPRING") || aos_seasonpro.equals("SPRING_SUMMER")
                        || aos_seasonpro.equals("SUMMER"))
                        aos_seasonprostr = "SPRING_SUMMER_PRO";

                    // 4. 如果是春夏品:当前日期大于8/31直接剔除,如果为秋冬品，当前日期大于3月31日小于7月1日直接剔除
                    if ("SPRING_SUMMER_PRO".equals(aos_seasonprostr)) {
                        if (!unsalableProducts.contains(String.valueOf(item_id)) && flag1 && flag2 && flag4
                            && month - 1 >= Calendar.SEPTEMBER)
                        {
                            MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "-春夏品-月份:" + (month - 1));
                            continue;
                        }
                    } else if ("AUTUMN_WINTER_PRO".equals(aos_seasonprostr)) {
                        if (month - 1 >= Calendar.APRIL && month - 1 < Calendar.JULY) {
                            MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "-秋冬品-月份:" + (month - 1));
                            continue;
                        }
                    }

                    long l2 = System.currentTimeMillis();

                    // 季节品
                    boolean seasonProduct = aos_seasonpro.equals("AUTUMN_WINTER") || aos_seasonpro.equals("WINTER")
                        || aos_seasonpro.equals("SPRING") || aos_seasonpro.equals("SPRING_SUMMER")
                        || aos_seasonpro.equals("SUMMER");

                    // 针对爆品 季节 节日 常规 进行筛选 不满足条件的直接跳过
                    Boolean issaleout = false;
                    if ((saleout || distSkuSet.contains(itemIdStr)) && availableDays >= 90) {
                        issaleout = true;
                        aos_itemtype = "G";
                        aos_typedetail = "爆品/铺货";
                    }

                    // 非爆品
                    if (!issaleout) {
                        // 预断货
                        boolean preSaleOut = MKTCom.Is_PreSaleOut(org_id, item_id, (int)item_intransqty, aos_shp_day,
                            aos_freight_day, aos_clear_day, availableDays);

                        if (FndGlobal.IsNotNull(stSkuSet) && stSkuSet.contains(itemIdStr) && !preSaleOut) {
                            aos_typedetail = "秋冬滞销";
                            aos_itemtype = "D";
                        } else if (aos_category2.equals("圣诞装饰") || aos_category2.equals("其它节日装饰")) {
                            // 0.0 节日品
                            SeasonRate = MKTCom.Get_SeasonRate(org_id, item_id, aos_seasonpro, item_overseaqty, month);
                            if (SeasonRate == 0) {
                                MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "节日品累计完成率为空");
                                continue;
                            }
                            if (SeasonRate < 1)
                                aos_itemtype = "S";
                            else {
                                MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "节日品累计完成率不满足条件");
                                continue;
                            }
                            // 季节品预断货 跳过
                            // 海运备货清关
                            if (preSaleOut) {
                                MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "节日品未预断货");
                                continue;
                            }
                            // 判断季节类型
                            aos_typedetail = MKTCom.seasonalProStage(aos_seasonpro);
                        }
                        // 1.0季节品 累计完成率
                        else if (seasonProduct) {
                            // 判断季节品累计完成率是否满足条件
                            SeasonRate = MKTCom.Get_SeasonRate(org_id, item_id, aos_seasonpro, item_overseaqty, month);
                            MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "-季节品累计完成率:" + SeasonRate);
                            if (SeasonRate == 0) {
                                MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "季节品累计完成率为空");
                                continue;
                            }
                            if (MKTCom.Is_SeasonRate(aos_seasonpro, month, SeasonRate))
                                aos_itemtype = "S";
                            else {
                                MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "季节品累计完成率不满足条件");
                                continue;
                            }
                            // 季节品预断货 跳过
                            // 海运备货清关
                            if (preSaleOut) {
                                MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "季节品未预断货");
                                continue;
                            }
                            // 判断季节类型
                            aos_typedetail = MKTCom.seasonalProStage(aos_seasonpro);
                        }
                        // 2.0常规品 滞销
                        else if (aos_seasonpro.equals("REGULAR")
                            || aos_seasonpro.equals("SPRING-SUMMER-CONVENTIONAL")) {
                            // 判断是为周转还是低滞销
                            aos_typedetail = MKTCom.Get_RegularUn(aos_orgnumber, availableDays, R);
                            // 20230711 gk:判断是否为常规品滞销
                            if ("".equals(aos_typedetail)) {
                                // 营销国别滞销货号中存在这个货号,并且还不为 预断货
                                boolean conventType =
                                    unsalableProducts.contains(String.valueOf(item_id)) && !preSaleOut;
                                if (conventType) {
                                    aos_typedetail = "常规品滞销";
                                }
                            }
                            if ("".equals(aos_typedetail)) {// 如果为空则表示不为滞销品
                                MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "常规品未滞销");
                                continue;
                            }
                            aos_itemtype = "D";
                            if ("低周转".equals(aos_typedetail) & week == Calendar.TUESDAY) {
                                if ("US".equals(aos_orgnumber) || "UK".equals(aos_orgnumber)) {
                                    if (R > 1.5 & R <= 3) {
                                        aos_typedetail = "低周转(日均>标准)";
                                    } else {
                                        aos_typedetail = "低周转(日均<=标准)";
                                    }
                                } else {
                                    if (R > 0.75 & R <= 1.5) {
                                        aos_typedetail = "低周转(日均>标准)";
                                    } else {
                                        aos_typedetail = "低周转(日均<=标准)";
                                    }
                                }
                            }
                        }
                        // 3.0其他情况都跳过
                        else {
                            MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "其他情况都跳过");
                            continue;
                        }
                    }
                }
                switch (aos_itemstatus) {
                    case "A":
                        aos_itemstatus = "新品首单";
                        break;
                    case "B":
                        aos_itemstatus = "正常";
                        break;
                    case "C":
                        aos_itemstatus = "终止";
                        break;
                    case "D":
                        aos_itemstatus = "异常";
                        break;
                    case "E":
                        aos_itemstatus = "入库新品";
                        break;
                }

                // 赋值数据
                DynamicObject aos_entryentity = aos_entryentityS.addNew();
                if (list_unSaleItemid.contains(String.valueOf(item_id))) {
                    aos_entryentity.set("aos_weekunsale", "Y");
                }
                aos_entryentity.set("aos_orgid", aos_orgnumber);
                aos_entryentity.set("aos_sku", aos_sku);
                aos_entryentity.set("aos_itemname", aos_itemname);
                aos_entryentity.set("aos_seasonpro", aos_seasonpro);
                aos_entryentity.set("aos_itemstatus", aos_itemstatus);
                aos_entryentity.set("aos_category1", aos_category1);
                aos_entryentity.set("aos_category2", aos_category2);
                aos_entryentity.set("aos_overseaqty", item_overseaqty);
                aos_entryentity.set("aos_qty", aos_qty);
                aos_entryentity.set("aos_typedetail", aos_typedetail);// 类型细分
                aos_entryentity.set("aos_itemtype", aos_itemtype);
                aos_entryentity.set("aos_salesqty", BigDecimal.valueOf(R));
                aos_entryentity.set("aos_last7sales", day7Sales);
                aos_entryentity.set("aos_avadays", availableDays);// 非平台仓库可售天数
                aos_entryentity.set("aos_seasonrate", SeasonRate);
                aos_entryentity.set("aos_times", alreadyActivityTimes.getOrDefault(String.valueOf(item_id), 0));
                aos_entryentity.set("aos_platfqty",
                    InStockAvailableDays.getPlatQty(String.valueOf(org_id), String.valueOf(item_id)));
                aos_entryentity.set("aos_platdays", availableDaysByPlatQty);// 平台仓库可售天数
                aos_entryentity.set("aos_itemmaxage", item_maxage);// 平台仓库可售天数
                aos_entryentity.set("aos_platavgqty",
                    InStockAvailableDays.getPlatAvgQty(String.valueOf(org_id), String.valueOf(item_id)));// 平台仓库可售天数
                aos_entryentity.set("aos_is_saleout", saleout); // 是否爆品

                // 排名是否达标
                if (FndGlobal.IsNotNull(firstRank) && firstRank.contains(String.valueOf(item_id))) {
                    aos_entryentity.set("aos_level", true);
                }
                if (FndGlobal.IsNotNull(cateSeason)) {
                    aos_entryentity.set("aos_attr", cateSeason.get(aos_category1 + "~" + aos_category2));
                }

            } catch (Exception ex) {
                MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "异常跳过");
                continue;
            }
        }

        // 保存正式表
        OperationServiceHelper.executeOperate("save", "aos_mkt_actselect", new DynamicObject[] {aos_mkt_actselect},
            OperateOption.create());

        // 保存日志表
        OperationServiceHelper.executeOperate("save", "aos_sync_log", new DynamicObject[] {aos_sync_log},
            OperateOption.create());
    }

    /**
     * 秋冬滞销
     * 
     * @param pOuCode
     * @return
     */
    private static Set<String> getStSku(Object pOuCode) {
        DynamicObjectCollection stSkuS = QueryServiceHelper.query("aos_base_stitem", "aos_itemid",
            new QFilter("aos_orgid.number", QCP.equals, pOuCode).and("aos_type", QCP.equals, "AUTUMN").toArray());
        return stSkuS.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
    }

    private static Set<String> getDistSku(Object pOuCode) {
        DynamicObjectCollection distSkuS = QueryServiceHelper.query("aos_mkt_distsku", "aos_itemid",
            new QFilter("aos_orgid.number", QCP.equals, pOuCode).toArray());
        return distSkuS.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
    }

    private static Map<String, Object> getCateSeason(Object p_ou_code) {
        HashMap<String, Object> cateSeason = new HashMap<>();
        DynamicObjectCollection aos_mkt_cateseasonS =
            QueryServiceHelper.query("aos_mkt_cateseason", "aos_category1," + "aos_category2,aos_festname",
                new QFilter("aos_startdate", QCP.large_equals, FndDate.add_days(new Date(), 60))
                    .and("aos_endate", QCP.less_equals, FndDate.add_days(new Date(), 60))
                    .and("aos_orgid.number", QCP.equals, p_ou_code).toArray());
        for (DynamicObject aos_mkt_cateseason : aos_mkt_cateseasonS) {
            String aos_category1 = aos_mkt_cateseason.getString("aos_category1");
            String aos_category2 = aos_mkt_cateseason.getString("aos_category2");
            String aos_festname = aos_mkt_cateseason.getString("aos_festname");
            Object lastObject = cateSeason.get("aos_category1" + "~" + "aos_category2");
            if (FndGlobal.IsNull(lastObject))
                cateSeason.put(aos_category1 + "~" + aos_category2, aos_festname);
            else
                cateSeason.put(aos_category1 + "~" + aos_category2, lastObject + "/" + aos_festname);
        }
        return cateSeason;
    }

    /**
     * 获取本月第一份排名改善汇总表
     *
     * @param p_ou_code
     * @return
     */
    private static Set<String> getFirstRank(Object p_ou_code) {
        LocalDate firstDayOfMonth = LocalDate.now().withDayOfMonth(1);
        DynamicObjectCollection aos_sal_ranksumS = QueryServiceHelper.query("aos_sal_ranksum", "id",
            new QFilter("aos_submitdate", ">=", firstDayOfMonth).toArray(), "aos_submitdate asc");
        Object fid = null;
        for (DynamicObject aos_sal_ranksum : aos_sal_ranksumS) {
            fid = aos_sal_ranksum.get("id");
            break;
        }
        aos_sal_ranksumS = QueryServiceHelper.query("aos_sal_ranksum", "aos_entryentity.aos_itemid aos_itemid",
            new QFilter("aos_orgid.number", QCP.equals, p_ou_code).and("id", QCP.equals, fid).toArray());
        if (FndGlobal.IsNotNull(aos_sal_ranksumS))
            return aos_sal_ranksumS.stream().map(obj -> obj.getString("aos_itemid")).distinct()
                .collect(Collectors.toSet());
        else
            return null;
    }

    /**
     * 获取已提报活动次数 查询活动计划和选品表 （1） 单据状态为已提报 （2） 明细行状态为正常 （3） 开始日期或结束日期在未来60天内 （4） 按国别物料分组计算次数
     *
     * @param ouCode 国别编码
     * @Return 该国别下的物料及其次数
     */
    private static Map<String, Integer> getAlreadyActivityTimes(Object ouCode) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar instance = Calendar.getInstance();
        instance.set(Calendar.HOUR_OF_DAY, 0);
        instance.set(Calendar.MINUTE, 0);
        instance.set(Calendar.SECOND, 0);
        instance.set(Calendar.MILLISECOND, 0);
        String start = sdf.format(instance.getTime());
        instance.add(Calendar.DAY_OF_MONTH, +60);
        String end = sdf.format(instance.getTime());

        // 开始日期或结束日期在未来60天内
        QFilter qFilter = new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.large_equals, start)
            .and(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, end));
        qFilter.or(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.large_equals, start)
            .and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.less_equals, end)));
        qFilter.or(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, start)
            .and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.large_equals, end)));
        // 字段
        String selectFields = "aos_sal_actplanentity.aos_itemnum aos_itemid";
        DataSet dataSet =
            QueryServiceHelper.queryDataSet("aos_mkt_actselect_init_getAlreadyActivityTimes", "aos_act_select_plan",
                selectFields, new QFilter[] {new QFilter("aos_nationality.number", QCP.equals, ouCode), // 国别
                    new QFilter("aos_actstatus", QCP.equals, "B"), // 单据状态为已提报
                    new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"), // 明细行状态为正常
                    qFilter// 开始日期或结束日期在未来60天内
                }, null);

        dataSet = dataSet.groupBy(new String[] {"aos_itemid"}).count().finish();
        Map<String, Integer> result = new HashMap<>();
        while (dataSet.hasNext()) {
            Row next = dataSet.next();
            String aos_itemid = next.getString(0);
            Integer aos_times = next.getInteger(1);
            result.put(aos_itemid, aos_times);
        }
        return result;
    }

    /**
     * 获取最新一期的海外滞销表中的数据
     *
     * @param orgNumber 国别编码
     * @return itemNumber
     */
    private static List<String> getNewestUnsaleDy(String orgNumber) {
        // 获取最新一期的时间
        DynamicObject dy_date =
            QueryServiceHelper.queryOne("aos_sal_quo_ur", "max(aos_makedate) as aos_makedate", null);
        if (dy_date == null || dy_date.get("aos_makedate") == null)
            return new ArrayList<>();
        else {
            LocalDate localDate =
                dy_date.getDate("aos_makedate").toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            QFilter filter_date_s = new QFilter("aos_makedate", ">=", localDate.toString());
            QFilter filter_date_e = new QFilter("aos_makedate", "<", localDate.plusDays(1).toString());
            QFilter filter_org = new QFilter("aos_org.number", "=", orgNumber);
            QFilter[] qfs = new QFilter[] {filter_date_s, filter_date_e, filter_org};
            return QueryServiceHelper.query("aos_sal_quo_ur", "aos_entryentity.aos_sku aos_sku", qfs).stream()
                .map(dy -> dy.getString("aos_sku")).distinct().collect(Collectors.toList());
        }
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        // 多线程执行
        executerun();
    }

    static class MktActSelectRunnable implements Runnable {

        private Map<String, Object> params = new HashMap<>();

        public MktActSelectRunnable(Map<String, Object> param) {
            this.params = param;
        }

        @Override
        public void run() {
            try {
                do_operate(params);
            } catch (Exception e) {
                logger.error("活动选品初始化失败", e);
            }
        }
    }

}
