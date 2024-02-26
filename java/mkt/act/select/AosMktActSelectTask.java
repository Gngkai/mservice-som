package mkt.act.select;

import common.CommonDataSom;
import common.Cux_Common_Utl;
import common.fnd.AosomLog;
import common.fnd.FndDate;
import common.fnd.FndGlobal;
import common.fnd.FndLog;
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
import mkt.common.MktComUtil;
import mkt.common.AosMktCacheUtil;
import org.apache.commons.lang3.SerializationUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author aosom
 * @version 活动选品表初始化-调度任务类
 */
public class AosMktActSelectTask extends AbstractTask {

    private static final DistributeSessionlessCache CACHE =
        CacheFactory.getCommonCacheFactory().getDistributeSessionlessCache("mkt_redis");

    private static final AosomLog LOGGER = AosomLog.init("aos_mkt_actselect_init");

    static {
        LOGGER.setService("AOS.MMS");
        LOGGER.setDomain("MMS.ACT");
    }

    public static void executerun() {
        // 初始化数据
        CommonDataSom.init();
        AosMktCacheUtil.initRedis("act");
        // 调用线程池
        QFilter overseaFlag = new QFilter("aos_isomvalid", "=", "1").or("number", QCP.equals, "IE");
        DynamicObjectCollection ouS = QueryServiceHelper.query("bd_country", "id,number", overseaFlag.toArray());
        for (DynamicObject ou : ouS) {
            String pOuCode = ou.getString("number");
            Map<String, Object> params = new HashMap<>(16);
            params.put("p_ou_code", pOuCode);
            MktActSelectRunnable mktActSelectRunnable = new MktActSelectRunnable(params);
            ThreadPools.executeOnce("MKT_活动选品初始化_" + pOuCode, mktActSelectRunnable);
        }
    }

    public static void doOperate(Map<String, Object> params) {
        byte[] serializeItem = CACHE.getByteValue("item");
        HashMap<String, Map<String, Object>> item = SerializationUtils.deserialize(serializeItem);
        Map<String, Object> itemMaxageMap = item.get("item_maxage");
        Map<String, Object> itemOverseaqtyMap = item.get("item_overseaqty");
        Map<String, Object> itemIntransqtyMap = item.get("item_intransqty");
        byte[] serializeDatepara = CACHE.getByteValue("datepara");
        HashMap<String, Map<String, Object>> datepara = SerializationUtils.deserialize(serializeDatepara);
        Map<String, Object> aosShpdayMap = datepara.get("aos_shp_day");
        Map<String, Object> aosCleardayMap = datepara.get("aos_clear_day");
        Map<String, Object> aosFreightdayMap = datepara.get("aos_freight_day");
        Calendar date = Calendar.getInstance();
        int year = date.get(Calendar.YEAR);
        int month = date.get(Calendar.MONTH) + 1;
        int day = date.get(Calendar.DAY_OF_MONTH);
        int week = date.get(Calendar.DAY_OF_WEEK);
        Object pOuCode = params.get("p_ou_code");
        // 获取当前国别下所有活动物料已提报次数
        Map<String, Integer> alreadyActivityTimes = getAlreadyActivityTimes(pOuCode);
        // 7天销量
        HashMap<String, Integer> order7Days = AosMktGenUtil.generateOrder7Days(pOuCode);
        QFilter filterOu = new QFilter("aos_contryentry.aos_nationality.number", "=", pOuCode);
        QFilter filterItemtype = new QFilter("aos_protype", "=", "N");
        QFilter[] filters = new QFilter[] {filterOu, filterItemtype};
        String selectField = "id," + "number," + "aos_cn_name," + "aos_contryentry.aos_nationality.id aos_orgid,"
            + "aos_contryentry.aos_nationality.number aos_orgnumber,"
            + "aos_contryentry.aos_seasonseting.number aos_seasonseting,"
            + "aos_contryentry.aos_contryentrystatus aos_contryentrystatus,"
            + "aos_contryentry.aos_festivalseting aos_festivalseting,"
            + "aos_contryentry.aos_is_saleout aos_is_saleout";
        DynamicObjectCollection bdMaterialS = QueryServiceHelper.query("bd_material", selectField, filters);
        // 清除同维度
        QFilter filterType = new QFilter("aos_type_code", "=", "MKT_活动选品初始化");
        QFilter filterGroup = new QFilter("aos_groupid", "=", pOuCode.toString() + year + month + day);
        filters = new QFilter[] {filterType, filterGroup};
        DeleteServiceHelper.delete("aos_sync_log", filters);
        QFilter filterBill = new QFilter("billno", "=", pOuCode.toString() + year + month + day);
        filters = new QFilter[] {filterBill};
        DeleteServiceHelper.delete("aos_mkt_actselect", filters);
        // 初始化保存对象
        DynamicObject aosMktActselect = BusinessDataServiceHelper.newDynamicObject("aos_mkt_actselect");
        aosMktActselect.set("billno", pOuCode.toString() + year + month + day);
        aosMktActselect.set("billstatus", "A");
        DynamicObjectCollection aosEntryentityS = aosMktActselect.getDynamicObjectCollection("aos_entryentity");
        FndLog log = FndLog.init("MKT_活动选品初始化", pOuCode.toString() + year + month + day);
        List<String> listUnSaleItemid = getNewestUnsaleDy(String.valueOf(pOuCode));
        // 并上营销国别滞销货号
        filters = new QFilter[] {new QFilter("aos_orgid.number", QCP.equals, pOuCode)};
        List<String> unsalableProducts = QueryServiceHelper.query("aos_base_stitem", "aos_itemid", filters).stream()
            .map(dy -> dy.getString("aos_itemid")).filter(FndGlobal::IsNotNull).distinct().collect(Collectors.toList());
        Set<String> firstRank = getFirstRank(pOuCode);
        Map<String, Object> cateSeason = getCateSeason(pOuCode);
        Set<String> distSkuSet = getDistSku(pOuCode);
        Set<String> stSkuSet = getStSku(pOuCode);
        for (DynamicObject bdMaterial : bdMaterialS) {
            // 判断是否跳过
            long itemId = bdMaterial.getLong("id");
            String itemIdStr = String.valueOf(itemId);
            long orgId = bdMaterial.getLong("aos_orgid");
            String orgIdStr = Long.toString(orgId);
            String aosItemNum = bdMaterial.getString("number");
            try {
                String aosSeasonpro = bdMaterial.getString("aos_seasonseting");
                String aosItemstatus = bdMaterial.getString("aos_contryentrystatus");
                String aosFestivalseting = bdMaterial.getString("aos_festivalseting");
                String aosOrgnumber = bdMaterial.getString("aos_orgnumber");
                String aosItemtype = null;
                // 是否爆品
                boolean saleout = bdMaterial.getBoolean("aos_is_saleout");
                Object itemIntransqty = itemIntransqtyMap.get(orgIdStr + "~" + itemIdStr);
                int aosShpDay = (int)aosShpdayMap.get(orgIdStr);
                int aosFreightDay = (int)aosCleardayMap.get(orgIdStr);
                int aosClearDay = (int)aosFreightdayMap.get(orgIdStr);
                // 库存可售天数 非平台仓库用量
                int availableDays = InStockAvailableDays.calInstockSalDays(orgIdStr, itemIdStr);
                Object itemOverseaqty = itemOverseaqtyMap.get(orgIdStr + "~" + itemIdStr);
                int availableDaysByPlatQty = InStockAvailableDays.calAvailableDaysByPlatQty(orgIdStr, itemIdStr);
                Object itemMaxage = itemMaxageMap.get(orgIdStr + "~" + itemIdStr);
                // 产品状态 季节属性
                if (FndGlobal.IsNull(aosItemstatus) || FndGlobal.IsNull(aosSeasonpro)) {
                    log.add(aosItemNum + ":季节属性为空");
                    continue;
                }
                // 最大库龄≥15天
                boolean flag1 = FndGlobal.IsNull(itemMaxage) || (int)itemMaxage < 15;
                // 海外库存>30
                boolean flag2 = false;
                if (FndGlobal.IsNull(itemOverseaqty)) {
                    itemOverseaqty = 0;
                }
                if (FndGlobal.IsNull(itemOverseaqty) || (int)itemOverseaqty <= 30) {
                    flag2 = true;
                }
                // 平台仓库数量可售天数
                boolean flag3 = availableDaysByPlatQty < 120;
                boolean flag4 = availableDaysByPlatQty < 60;
                // (最大库龄 < 15 || 海外库存 <= 30) && 平台仓可售天数 < 120
                boolean cond = (flag1 || flag2) && flag3;
                if (cond) {
                    if (flag1) {
                        log.add(aosItemNum + ":最大库龄<15天");
                    }
                    if (flag2) {
                        log.add(aosItemNum + ":海外库存<30");
                    }
                    log.add(aosItemNum + "平台仓数量可售天数:" + availableDaysByPlatQty + " < 120");
                    continue;
                }
                // 7天日均销量dd
                float r = InStockAvailableDays.getOrgItemOnlineAvgQty(orgIdStr, itemIdStr);
                // 可提报活动数量 > 5 (当前可售库存数量+在途)*活动数量占比-已提报的未来60天的活动数量
                if (FndGlobal.IsNull(itemIntransqty)) {
                    itemIntransqty = 0;
                }
                // 活动数量占比
                BigDecimal actQtyRate = MktComUtil.getActQtyRate(aosItemstatus, aosSeasonpro, aosFestivalseting);
                if (FndGlobal.IsNull(actQtyRate)) {
                    log.add(aosItemNum + ":活动数量占比为空");
                    continue;
                }
                // 已提报未来60天活动数量
                int act60PreQty = MktComUtil.getAct60PreQty(orgId, itemId);
                BigDecimal avaQty = new BigDecimal((int)itemOverseaqty + (int)itemIntransqty);
                // 可提报活动数量
                log.add(aosItemNum + ":活动数量占比 " + actQtyRate);
                log.add(aosItemNum + ":海外库存 " + itemOverseaqty);
                log.add(aosItemNum + ":在途数量 " + itemIntransqty);
                log.add(aosItemNum + ":已提报未来60天活动数量 " + act60PreQty);
                int aosQty = 0;
                if (actQtyRate != null) {
                    aosQty = actQtyRate.multiply(avaQty).intValue() - act60PreQty;
                }
                log.add(aosItemNum + ":可提报活动数量 " + aosQty);
                if (aosQty <= 5) {
                    log.add(aosItemNum + ":可提报活动数量<=5 ");
                }
                // 滞销类型 低动销 低周转
                String aosTypedetail = "";
                // 7天销量
                int day7Sales = (int)Cux_Common_Utl.nvl(order7Days.get(itemIdStr));
                float seasonRate = 0;
                // 判断当前月份
                boolean speFlag = false;
                if (unsalableProducts.contains(itemIdStr)) {
                    boolean preSaleOut = MktComUtil.isPreSaleOut(orgId, itemId, (int)itemIntransqty, aosShpDay,
                        aosFreightDay, aosClearDay, availableDays);
                    if (!preSaleOut) {
                        speFlag = true;
                        log.add(aosItemNum + ":春夏滞销不预断货货号 ");
                    }
                }
                // 获取数据
                String aosSku = bdMaterial.getString("number");
                String aosItemname = bdMaterial.getString("aos_cn_name");
                String category = (String)SalUtil.getCategoryByItemId(itemIdStr).get("name");
                String[] categoryGroup = category.split(",");
                String aosCategory1 = "";
                String aosCategory2 = "";
                // 处理数据
                int categoryLength = categoryGroup.length;
                if (categoryLength > 0) {
                    aosCategory1 = categoryGroup[0];
                }
                if (categoryLength > 1) {
                    aosCategory2 = categoryGroup[1];
                }
                if (!speFlag) {
                    // 日均销量与可售库存天数参数申明
                    String aosSeasonprostr = null;
                    if ("AUTUMN_WINTER".equals(aosSeasonpro) || "WINTER".equals(aosSeasonpro)) {
                        aosSeasonprostr = "AUTUMN_WINTER_PRO";
                    } else if ("SPRING".equals(aosSeasonpro) || "SPRING_SUMMER".equals(aosSeasonpro)
                        || "SUMMER".equals(aosSeasonpro)) {
                        aosSeasonprostr = "SPRING_SUMMER_PRO";
                    }
                    // 4. 如果是春夏品:当前日期大于8/31直接剔除,如果为秋冬品，当前日期大于3月31日小于7月1日直接剔除
                    if ("SPRING_SUMMER_PRO".equals(aosSeasonprostr)) {
                        if (!unsalableProducts.contains(itemIdStr) && flag1 && flag2 && flag4
                            && month - 1 >= Calendar.SEPTEMBER) {
                            log.add(aosItemNum + "-春夏品-月份:" + (month - 1));
                            continue;
                        }
                    } else if ("AUTUMN_WINTER_PRO".equals(aosSeasonprostr)) {
                        if (month - 1 >= Calendar.APRIL && month - 1 < Calendar.JULY) {
                            log.add(aosItemNum + "-秋冬品-月份:" + (month - 1));
                            continue;
                        }
                    }
                    // 季节品
                    boolean seasonProduct = "AUTUMN_WINTER".equals(aosSeasonpro) || "WINTER".equals(aosSeasonpro)
                        || "SPRING".equals(aosSeasonpro) || "SPRING_SUMMER".equals(aosSeasonpro)
                        || "SUMMER".equals(aosSeasonpro);
                    // 针对爆品 季节 节日 常规 进行筛选 不满足条件的直接跳过
                    boolean issaleout = false;
                    boolean cond2 = ((saleout || distSkuSet.contains(itemIdStr)) && availableDays >= 90);
                    if (cond2) {
                        issaleout = true;
                        aosItemtype = "G";
                        aosTypedetail = "爆品/铺货";
                    }
                    // 非爆品
                    if (!issaleout) {
                        // 预断货
                        boolean preSaleOut = MktComUtil.isPreSaleOut(orgId, itemId, (int)itemIntransqty, aosShpDay,
                            aosFreightDay, aosClearDay, availableDays);
                        if (FndGlobal.IsNotNull(stSkuSet) && stSkuSet.contains(itemIdStr) && !preSaleOut) {
                            aosTypedetail = "秋冬滞销";
                            aosItemtype = "D";
                        } else if ("圣诞装饰".equals(aosCategory2) || "其它节日装饰".equals(aosCategory2)) {
                            // 0.0 节日品
                            seasonRate = MktComUtil.getSeasonRate(orgId, itemId, aosSeasonpro, itemOverseaqty, month);
                            if (seasonRate == 0) {
                                log.add(aosItemNum + ":节日品累计完成率为空");
                                continue;
                            }
                            if (seasonRate < 1) {
                                aosItemtype = "S";
                            } else {
                                log.add(aosItemNum + ":节日品累计完成率不满足条件");
                                continue;
                            }
                            // 季节品预断货 跳过
                            // 海运备货清关
                            if (preSaleOut) {
                                log.add(aosItemNum + ":节日品未预断货");
                                continue;
                            }
                            // 判断季节类型
                            aosTypedetail = MktComUtil.seasonalProStage(aosSeasonpro);
                        }
                        // 1.0季节品 累计完成率
                        else if (seasonProduct) {
                            // 判断季节品累计完成率是否满足条件
                            seasonRate = MktComUtil.getSeasonRate(orgId, itemId, aosSeasonpro, itemOverseaqty, month);
                            log.add(aosItemNum + ":季节品累计完成率 " + seasonRate);
                            if (seasonRate == 0) {
                                log.add(aosItemNum + ":季节品累计完成率为空");
                                continue;
                            }
                            if (MktComUtil.isSeasonRate(aosSeasonpro, month, seasonRate)) {
                                aosItemtype = "S";
                            } else {
                                log.add(aosItemNum + ":季节品累计完成率不满足条件");
                                continue;
                            }
                            // 季节品预断货 跳过
                            // 海运备货清关
                            if (preSaleOut) {
                                log.add(aosItemNum + ":季节品未预断货");
                                continue;
                            }
                            // 判断季节类型
                            aosTypedetail = MktComUtil.seasonalProStage(aosSeasonpro);
                        }
                        // 2.0常规品 滞销
                        else if ("REGULAR".equals(aosSeasonpro) || "SPRING-SUMMER-CONVENTIONAL".equals(aosSeasonpro)) {
                            // 判断是为周转还是低滞销
                            aosTypedetail = MktComUtil.getRegularUn(aosOrgnumber, availableDays, r);
                            // 20230711 gk:判断是否为常规品滞销
                            if ("".equals(aosTypedetail)) {
                                // 营销国别滞销货号中存在这个货号,并且还不为 预断货
                                boolean conventType = unsalableProducts.contains(itemIdStr) && !preSaleOut;
                                if (conventType) {
                                    aosTypedetail = "常规品滞销";
                                }
                            }
                            if ("".equals(aosTypedetail)) {
                                // 如果为空则表示不为滞销品
                                log.add(aosItemNum + ":常规品未滞销");
                                continue;
                            }
                            aosItemtype = "D";
                            if ("低周转".equals(aosTypedetail) & week == Calendar.TUESDAY) {
                                if ("US".equals(aosOrgnumber) || "UK".equals(aosOrgnumber)) {
                                    if (r > 1.5 & r <= 3) {
                                        aosTypedetail = "低周转(日均>标准)";
                                    } else {
                                        aosTypedetail = "低周转(日均<=标准)";
                                    }
                                } else {
                                    if (r > 0.75 & r <= 1.5) {
                                        aosTypedetail = "低周转(日均>标准)";
                                    } else {
                                        aosTypedetail = "低周转(日均<=标准)";
                                    }
                                }
                            }
                        }
                        // 3.0其他情况都跳过
                        else {
                            log.add(aosItemNum + ":其他情况都跳过");
                            continue;
                        }
                    }
                }
                switch (aosItemstatus) {
                    case "A":
                        aosItemstatus = "新品首单";
                        break;
                    case "B":
                        aosItemstatus = "正常";
                        break;
                    case "C":
                        aosItemstatus = "终止";
                        break;
                    case "D":
                        aosItemstatus = "异常";
                        break;
                    case "E":
                        aosItemstatus = "入库新品";
                        break;
                    default:
                        break;
                }
                // 赋值数据
                DynamicObject aosEntryentity = aosEntryentityS.addNew();
                if (listUnSaleItemid.contains(itemIdStr)) {
                    aosEntryentity.set("aos_weekunsale", "Y");
                }
                aosEntryentity.set("aos_orgid", aosOrgnumber);
                aosEntryentity.set("aos_sku", aosSku);
                aosEntryentity.set("aos_itemname", aosItemname);
                aosEntryentity.set("aos_seasonpro", aosSeasonpro);
                aosEntryentity.set("aos_itemstatus", aosItemstatus);
                aosEntryentity.set("aos_category1", aosCategory1);
                aosEntryentity.set("aos_category2", aosCategory2);
                aosEntryentity.set("aos_overseaqty", itemOverseaqty);
                aosEntryentity.set("aos_qty", aosQty);
                // 类型细分
                aosEntryentity.set("aos_typedetail", aosTypedetail);
                aosEntryentity.set("aos_itemtype", aosItemtype);
                aosEntryentity.set("aos_salesqty", BigDecimal.valueOf(r));
                aosEntryentity.set("aos_last7sales", day7Sales);
                // 非平台仓库可售天数
                aosEntryentity.set("aos_avadays", availableDays);
                aosEntryentity.set("aos_seasonrate", seasonRate);
                aosEntryentity.set("aos_times", alreadyActivityTimes.getOrDefault(itemIdStr, 0));
                aosEntryentity.set("aos_platfqty", InStockAvailableDays.getPlatQty(orgIdStr, itemIdStr));
                // 平台仓库可售天数
                aosEntryentity.set("aos_platdays", availableDaysByPlatQty);
                // 平台仓库可售天数
                aosEntryentity.set("aos_itemmaxage", itemMaxage);
                // 平台仓库可售天数
                aosEntryentity.set("aos_platavgqty", InStockAvailableDays.getPlatAvgQty(orgIdStr, itemIdStr));
                // 是否爆品
                aosEntryentity.set("aos_is_saleout", saleout);
                // 排名是否达标
                if (firstRank != null && FndGlobal.IsNotNull(firstRank) && firstRank.contains(itemIdStr)) {
                    aosEntryentity.set("aos_level", true);
                }
                if (FndGlobal.IsNotNull(cateSeason)) {
                    aosEntryentity.set("aos_attr", cateSeason.get(aosCategory1 + "~" + aosCategory2));
                }
            } catch (Exception ex) {
                log.add(aosItemNum + ":异常跳过");
            }
        }
        // 保存正式表
        OperationServiceHelper.executeOperate("save", "aos_mkt_actselect", new DynamicObject[] {aosMktActselect},
            OperateOption.create());
        // 保存日志表
        log.finnalSave();
    }

    /**
     * 秋冬滞销
     * 
     * @param pOuCode 国别编码
     * @return 秋冬滞销货号
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

    private static Map<String, Object> getCateSeason(Object pOuCode) {
        HashMap<String, Object> cateSeason = new HashMap<>(16);
        DynamicObjectCollection aosMktCateseasonS =
            QueryServiceHelper.query("aos_mkt_cateseason", "aos_category1," + "aos_category2,aos_festname",
                new QFilter("aos_startdate", QCP.large_equals, FndDate.add_days(new Date(), 60))
                    .and("aos_endate", QCP.less_equals, FndDate.add_days(new Date(), 60))
                    .and("aos_orgid.number", QCP.equals, pOuCode).toArray());
        for (DynamicObject aosMktCateseason : aosMktCateseasonS) {
            String aosCategory1 = aosMktCateseason.getString("aos_category1");
            String aosCategory2 = aosMktCateseason.getString("aos_category2");
            String aosFestname = aosMktCateseason.getString("aos_festname");
            Object lastObject = cateSeason.get("aos_category1" + "~" + "aos_category2");
            if (FndGlobal.IsNull(lastObject)) {
                cateSeason.put(aosCategory1 + "~" + aosCategory2, aosFestname);
            } else {
                cateSeason.put(aosCategory1 + "~" + aosCategory2, lastObject + "/" + aosFestname);
            }
        }
        return cateSeason;
    }

    /**
     * 获取本月第一份排名改善汇总表
     *
     * @param pOuCode 国别编码
     * @return 排名改善货号
     */
    private static Set<String> getFirstRank(Object pOuCode) {
        LocalDate firstDayOfMonth = LocalDate.now().withDayOfMonth(1);
        DynamicObjectCollection aosSalRanksumS = QueryServiceHelper.query("aos_sal_ranksum", "id",
            new QFilter("aos_submitdate", ">=", firstDayOfMonth).toArray(), "aos_submitdate asc");
        Object fid = null;
        for (DynamicObject aosSalRanksum : aosSalRanksumS) {
            fid = aosSalRanksum.get("id");
            break;
        }
        aosSalRanksumS = QueryServiceHelper.query("aos_sal_ranksum", "aos_entryentity.aos_itemid aos_itemid",
            new QFilter("aos_orgid.number", QCP.equals, pOuCode).and("id", QCP.equals, fid).toArray());
        if (FndGlobal.IsNotNull(aosSalRanksumS)) {
            return aosSalRanksumS.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
        } else {
            return null;
        }
    }

    /**
     * 获取已提报活动次数 查询活动计划和选品表 （1） 单据状态为已提报 （2） 明细行状态为正常 （3） 开始日期或结束日期在未来60天内 （4） 按国别物料分组计算次数
     *
     * @param ouCode 国别编码
     * @return 该国别下的物料及其次数
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
        DataSet dataSet = QueryServiceHelper.queryDataSet("aos_mkt_actselect_init_getAlreadyActivityTimes",
            "aos_act_select_plan", selectFields,
            new QFilter[] {new QFilter("aos_nationality.number", QCP.equals, ouCode),
                new QFilter("aos_actstatus", QCP.equals, "B"),
                new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"), qFilter},
            null);
        dataSet = dataSet.groupBy(new String[] {"aos_itemid"}).count().finish();
        Map<String, Integer> result = new HashMap<>(16);
        while (dataSet.hasNext()) {
            Row next = dataSet.next();
            String aosItemid = next.getString(0);
            Integer aosTimes = next.getInteger(1);
            result.put(aosItemid, aosTimes);
        }
        dataSet.close();
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
        DynamicObject dyDate = QueryServiceHelper.queryOne("aos_sal_quo_ur", "max(aos_makedate) as aos_makedate", null);
        boolean cond = (dyDate == null || dyDate.get("aos_makedate") == null);
        if (cond) {
            return new ArrayList<>();
        } else {
            LocalDate localDate =
                dyDate.getDate("aos_makedate").toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            QFilter filterDateS = new QFilter("aos_makedate", ">=", localDate.toString());
            QFilter filterDateE = new QFilter("aos_makedate", "<", localDate.plusDays(1).toString());
            QFilter filterOrg = new QFilter("aos_org.number", "=", orgNumber);
            QFilter[] qfs = new QFilter[] {filterDateS, filterDateE, filterOrg};
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

        private final Map<String, Object> params;

        public MktActSelectRunnable(Map<String, Object> param) {
            this.params = param;
        }

        @Override
        public void run() {
            try {
                doOperate(params);
            } catch (Exception e) {
                LOGGER.error("活动选品初始化失败", e);
            }
        }
    }
}
