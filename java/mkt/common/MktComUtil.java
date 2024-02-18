package mkt.common;

import common.CommonDataSomQuo;
import common.fnd.FndGlobal;
import common.sal.sys.sync.service.ItemCacheService;
import common.sal.sys.sync.service.impl.ItemCacheServiceImpl;
import kd.bos.algo.DataSet;
import kd.bos.algo.JoinDataSet;
import kd.bos.algo.Row;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.MainEntityType;
import kd.bos.entity.ValueMapItem;
import kd.bos.entity.property.ComboProp;
import kd.bos.notification.IconType;
import kd.bos.notification.NotificationBody;
import kd.bos.notification.NotificationFormInfo;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.MetadataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.notification.NotificationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author aosom
 * @version 营销通用工具类
 */
public class MktComUtil {
    private static final int FORTYFIVE = 4500;
    private static final int TWE = 120;
    private static final String C = "C";
    private static final String E = "E";
    private static final String B = "B";
    private static final String ZERO = "0";
    private static final String US = "US";
    private static final String UK = "UK";
    private static final float FOURTYTWO = 0.42F;
    private static final float FOURTYTHREE = 0.43F;
    private static final float SIX = 6F;
    private static final float THREE = 3F;
    private static final int THREE3 = 3;

    private static final String GROUPITEM = "组合货号";

    /**
     * 单据保存
     * 
     * @param listSave 对象
     * @param end 为true 时表示最后一次保存不考虑长度，否则考虑长度
     */
    public static void entitySave(List<DynamicObject> listSave, boolean end) {
        int size = listSave.size();
        if (end) {
            SaveServiceHelper.save(listSave.toArray(new DynamicObject[size]));
            listSave.clear();
        } else {
            if (size > FORTYFIVE) {
                SaveServiceHelper.save(listSave.toArray(new DynamicObject[size]));
                listSave.clear();
            }
        }
    }

    /**
     * 单据修改
     * 
     * @param listSave 对象
     * @param end 为true 时表示最后一次保存不考虑长度，否则考虑长度
     */
    public static void entityUpdate(List<DynamicObject> listSave, boolean end) {
        int size = listSave.size();
        if (end) {
            SaveServiceHelper.update(listSave.toArray(new DynamicObject[size]));
            listSave.clear();
        } else {
            if (size > FORTYFIVE) {
                SaveServiceHelper.save(listSave.toArray(new DynamicObject[size]));
                listSave.clear();
            }
        }
    }

    public static BigDecimal min(BigDecimal value1, BigDecimal value2) {
        if (value1.compareTo(value2) > -1) {
            return value2;
        } else {
            return value1;
        }
    }

    /** 获取权限用户 */
    public static List<String> getPrivilegedUser() {
        QFilter filterStatus = new QFilter("aos_process", QCP.equals, true);
        return QueryServiceHelper.query("aos_mkt_userights", "aos_user", new QFilter[] {filterStatus}).stream()
            .map(dy -> dy.getString("aos_user")).collect(Collectors.toList());
    }

    /**
     * 获取 已提报的未来60天的活动数量
     * 
     * @param orgId 国别
     * @param itemId 物料
     * @return act60PreQty
     */
    public static int getAct60PreQty(long orgId, long itemId) {
        // TODO 获取已提报的未来60天的活动数量
        int act60PreQty = 0;
        try {
            Calendar calendar = Calendar.getInstance();
            Date dateFrom = calendar.getTime();
            calendar.set(Calendar.DATE, calendar.get(Calendar.DATE) + 60);
            Date dateTo = calendar.getTime();
            // 日期格式化
            SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            String dateFromStr = writeFormat.format(dateFrom);
            String dateToStr = writeFormat.format(dateTo);
            QFilter filterOu = new QFilter("aos_nationality", "=", orgId);
            // 已提报
            QFilter filterStatus = new QFilter("aos_actstatus", "=", "B");
            // 活动状态为正常
            QFilter filterStatusl = new QFilter("aos_sal_actplanentity.aos_l_actstatus", "=", "A");
            QFilter filterItem = new QFilter("aos_sal_actplanentity.aos_itemnum", "=", itemId);
            QFilter filterDate = new QFilter("aos_sal_actplanentity.aos_enddate", ">=", dateFromStr)
                .and("aos_sal_actplanentity.aos_enddate", "<=", dateToStr);
            filterDate = filterDate.or("aos_startdate", ">=", dateFromStr).and("aos_startdate", "<=", dateToStr);
            QFilter[] filters = new QFilter[] {filterOu, filterItem, filterDate, filterStatus, filterStatusl};
            String selectColumn =
                "aos_sal_actplanentity.aos_actqty aos_actqty,aos_acttype,aos_nationality,aos_channel,aos_shop";
            DataSet aosActSelectPlanS = QueryServiceHelper.queryDataSet("Get_Act60PreQty." + orgId,
                "aos_act_select_plan", selectColumn, filters, null);
            QFilter filterType = new QFilter("aos_assessment.number", "=", "Y");
            filters = new QFilter[] {filterType};
            DataSet aosSalActTypePs = QueryServiceHelper.queryDataSet("aos_sal_act_type_p." + orgId,
                "aos_sal_act_type_p", "aos_org,aos_channel,aos_shop,aos_acttype", filters, null);
            JoinDataSet join = aosActSelectPlanS.join(aosSalActTypePs);
            String[] joinSelect = new String[] {"aos_actqty"};
            aosActSelectPlanS = join.on("aos_nationality", "aos_org").on("aos_channel", "aos_channel")
                .on("aos_shop", "aos_shop").on("aos_acttype", "aos_acttype").select(joinSelect).finish();
            aosActSelectPlanS = aosActSelectPlanS.groupBy(new String[] {"aos_actqty"}).sum("aos_actqty").finish();
            while (aosActSelectPlanS.hasNext()) {
                Row aosActSelectPlan = aosActSelectPlanS.next();
                act60PreQty = aosActSelectPlan.getInteger("aos_actqty");
            }
            aosActSelectPlanS.close();
            aosSalActTypePs.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return act60PreQty;
    }

    /**
     *
     * @param currentUserId 当前人员
     * @param orgField 国别字段
     * @param groupField 组别字段
     * @return QFilter
     */
    public static QFilter querySalespersonOrg(long currentUserId, String orgField, String groupField) {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_sal_porggroup", "aos_org,aos_categorygroup",
            new QFilter[] {new QFilter("aos_personal", QCP.equals, currentUserId)});
        QFilter qFilter = null;
        for (DynamicObject obj : list) {
            String aosCategorygroup = obj.getString("aos_categorygroup");
            if (StringUtils.equals(aosCategorygroup, "0") || aosCategorygroup == null) {
                continue;
            }
            QFilter permissionFilter = new QFilter(orgField, QCP.equals, obj.getString("aos_org"))
                .and(new QFilter(groupField, QCP.equals, aosCategorygroup));
            if (qFilter == null) {
                qFilter = permissionFilter;
            } else {
                qFilter.or(permissionFilter);
            }
        }
        return qFilter;
    }

    /**
     * 获取 活动数量占比
     * 
     * @param aosItemstatus 物料状态
     * @param aosSeasonpro 季节属性
     * @param aosFestivalseting 节日属性
     * @return ActQtyRate
     */
    public static BigDecimal getActQtyRate(String aosItemstatus, String aosSeasonpro, String aosFestivalseting) {
        try {
            // TODO 获取活动数量占比
            BigDecimal actQtyRate = null;
            boolean cond = ("AUTUMN_WINTER".equals(aosSeasonpro) || "SPRING".equals(aosSeasonpro)
                || "SPRING_SUMMER".equals(aosSeasonpro) || "SUMMER".equals(aosSeasonpro)
                || "WINTER".equals(aosSeasonpro));
            // 终止品80%节日品50%新品30%季节品30%正常产品30%
            if (C.equals(aosItemstatus)) {
                // 终止品
                actQtyRate = BigDecimal.valueOf(0.8);
            } else if (FndGlobal.IsNotNull(aosFestivalseting) && !ZERO.equals(aosFestivalseting)) {
                // 节日品
                actQtyRate = BigDecimal.valueOf(0.5);
            } else if (E.equals(aosItemstatus)) {
                // 新品
                actQtyRate = BigDecimal.valueOf(0.3);
            } else if (cond) {
                // 季节品
                actQtyRate = BigDecimal.valueOf(0.3);
            } else if (B.equals(aosItemstatus)) {
                // 正常产品
                actQtyRate = BigDecimal.valueOf(0.3);
            } else {
                actQtyRate = BigDecimal.ONE;
            }
            return actQtyRate;
        } catch (Exception ex) {
            return null;
        }
    }

    public static float getSeasonRate(long orgId, long itemId, String aosSeason, Object itemOverseaqty, int month) {
        // TODO 季节品累计完成率
        String aosSeasonpro = null;
        boolean cond1 = ("AUTUMN_WINTER".equals(aosSeason) || "WINTER".equals(aosSeason));
        boolean cond2 = ("SPRING".equals(aosSeason) || "SPRING_SUMMER".equals(aosSeason) || "SUMMER".equals(aosSeason));
        if (cond1) {
            aosSeasonpro = "AUTUMN_WINTER_PRO";
        } else if (cond2) {
            aosSeasonpro = "SPRING_SUMMER_PRO";
        }
        float cumulativeCompletionRate = 0;
        String orgidStr = Long.toString(orgId);
        String itemidStr = Long.toString(itemId);
        // 季节总预测销量
        int seasonTotalForecastSales = CommonDataSomQuo.calSeasonForecastNum(orgidStr, itemidStr, aosSeasonpro);
        // Σ季初至制表日预测量
        int earlySeasonToMakeDayForecastNum =
            CommonDataSomQuo.calEarlySeasonToMakeDayForecastNum(orgidStr, itemidStr, aosSeasonpro);
        if (seasonTotalForecastSales == 0 || earlySeasonToMakeDayForecastNum == 0) {
            return cumulativeCompletionRate;
        }
        // 季节要求进度
        float seasonalRequirementsProgress = (float)earlySeasonToMakeDayForecastNum / seasonTotalForecastSales;
        // 在途数量
        int transitNum = CommonDataSomQuo.getTransitNum(orgidStr, itemidStr);
        // 实际销量
        int actualSales = CommonDataSomQuo.calSeasonalProductsActualSales(orgidStr, itemidStr, aosSeasonpro);
        // 季节总供应量
        int seasonalTotalSupplyQty = actualSales + transitNum + (int)itemOverseaqty;
        boolean cond = ((StringUtils.equals(aosSeasonpro, "SPRING_SUMMER_PRO") && month <= Calendar.JUNE)
            || (StringUtils.equals(aosSeasonpro, "AUTUMN_WINTER_PRO") && month >= Calendar.AUGUST));
        if (cond) {
            // a. 春夏品： 销售开始时间为1月1日，6月1日以后国内在制+国内在库的数量不计算入总供应
            // b. 秋冬品： 销售开始时间为8月1日，1月1日以后国内在制+国内在库的数量不计算入总供应
            // 国内在制
            int inProcessQty = CommonDataSomQuo.get_in_process_qty(orgId, itemId);
            // 国内在库
            int domesticQty = CommonDataSomQuo.get_domestic_qty(orgId, itemId);
            seasonalTotalSupplyQty = seasonalTotalSupplyQty + inProcessQty + domesticQty;
        }
        if (seasonalTotalSupplyQty == 0) {
            // 如果实际销量或者季节总供应量为0 下一次
            return cumulativeCompletionRate;
        }
        // 5.2季节实际进度=季节实际总销量/季节总供应数量
        // 季节总供应数量 = 实际销量 + 在途数量 + 海外库存 + 国内在制 + 国内在库
        // 季节实际进度
        float seasonActualProgress = (float)actualSales / seasonalTotalSupplyQty;
        // 累计完成率
        cumulativeCompletionRate = seasonActualProgress / seasonalRequirementsProgress;
        return cumulativeCompletionRate;
    }

    public static boolean isSeasonRate(String aosSeasonpro, int month, float seasonRate) {
        // 判断季节品累计完成率
        boolean isSeasonRate = false;
        boolean cond1 = (("AUTUMN_WINTER".equals(aosSeasonpro) || "WINTER".equals(aosSeasonpro))
            && (month == 8 || month == 9 || month == 10) && (seasonRate <= 0.6));
        boolean cond2 = (("AUTUMN_WINTER".equals(aosSeasonpro) || "WINTER".equals(aosSeasonpro))
            && (month == 11 || month == 12 || month == 1) && (seasonRate <= 0.75));
        boolean cond3 = (("AUTUMN_WINTER".equals(aosSeasonpro) || "WINTER".equals(aosSeasonpro))
            && (month == 2 || month == 3) && (seasonRate <= 0.9));
        boolean spring =
            "SPRING".equals(aosSeasonpro) || "SPRING_SUMMER".equals(aosSeasonpro) || "SUMMER".equals(aosSeasonpro);
        boolean cond4 =
            (spring && (month == 0 || month == 1 || month == 2 || month == 3 || month == 4) && (seasonRate <= 0.6));
        boolean cond5 = (spring && (month == 5 || month == 6 || month == 7) && (seasonRate <= 0.75));
        boolean cond6 = (spring && (month == 8 || month == 9) && (seasonRate <= 0.9));
        // 对于秋冬产品
        if (cond1 || cond2 || cond3 || cond4 || cond5 || cond6) {
            isSeasonRate = true;
        }
        return isSeasonRate;
    }

    /**
     * 判断预断货逻辑
     * 
     * @param orgId 国别
     * @param itemId 物料
     * @param itemIntransqty 在途数量
     * @param aosShpDay 备货天数
     * @param aosFreightDay 海运天数
     * @param aosClearDay 清关天数
     * @param availableDays 可售天数
     * @return preSaleOut
     */
    public static boolean isPreSaleOut(long orgId, long itemId, int itemIntransqty, int aosShpDay, int aosFreightDay,
        int aosClearDay, int availableDays) {
        // 首先判断物料是否为季节品，如果不是季节品，调用销售的判断逻辑，否则还用之前的逻辑
        ItemCacheService cacheService = new ItemCacheServiceImpl();
        String seasonProNum = cacheService.getSeasonProNum(orgId, itemId);
        // 不为季节品
        if (FndGlobal.IsNull(seasonProNum)) {
            return cacheService.getIsPreStockOut(String.valueOf(orgId), String.valueOf(itemId));
        }
        // 判断预断货逻辑
        boolean preSaleOut = false;
        String orgidStr = Long.toString(orgId);
        String itemidStr = Long.toString(itemId);
        // 安全库存天数
        int safetyStockDays = getSafetyStockDays(orgId);
        // 离进仓天数
        int intoTheWarehouseDays = getIntoTheWarehouseDays(orgidStr, itemidStr);
        boolean cond =
            ((itemIntransqty == 0 && (availableDays - aosShpDay - aosClearDay - aosFreightDay - safetyStockDays) <= 0)
                || (itemIntransqty > 0 && (availableDays - intoTheWarehouseDays - safetyStockDays) <= 0));
        // a) 海外在途 = 0：库存可售天数-备货天数-海运清关周期-安全库存天数(15天)≤0
        if (cond) {
            preSaleOut = true;
        }
        return preSaleOut;
    }

    public static int getIntoTheWarehouseDays(String orgidStr, String itemidStr) {
        int intoTheWarehouseDays = 0;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Map<String, String> estimatedStorageDateAndTransitNum =
                CommonDataSomQuo.calEstimatedStorageDate(orgidStr, itemidStr);
            if (estimatedStorageDateAndTransitNum != null) {
                // 预计入库日期
                String estStorageDate = estimatedStorageDateAndTransitNum.get("est_storage_date");
                // 计算离进仓天数 预计入库日期-制单日期
                Calendar instance = Calendar.getInstance();
                instance.setTime(sdf.parse(sdf.format(instance.getTime())));
                // 制单日期
                long makeDateTimeInMillis = instance.getTimeInMillis();
                instance.setTime(sdf.parse(estStorageDate));
                // 预计入库日期
                long estimatedStorageDate = instance.getTimeInMillis();
                // 离进仓天数
                intoTheWarehouseDays = (int)((estimatedStorageDate - makeDateTimeInMillis) / (24 * 60 * 60 * 1000));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return Math.max(intoTheWarehouseDays, 0);
    }

    /**
     * 获取安全库存天数
     * 
     * @param orgId 国别ID
     * @return safetyStockDays
     */
    public static int getSafetyStockDays(long orgId) {
        int safetyStockDays = 0;
        QFilter filterOu = new QFilter("aos_org", "=", orgId);
        QFilter[] filters = new QFilter[] {filterOu};
        DynamicObject aosSalQuomCoe = QueryServiceHelper.queryOne("aos_sal_quo_m_coe", "aos_value", filters);
        if (aosSalQuomCoe != null) {
            safetyStockDays = aosSalQuomCoe.getInt("aos_value");
        }
        return safetyStockDays;
    }

    public static Boolean getRegularUn(long orgIdL, String aosOrgnumber, int availableDays, float r,
        int halfMonthTotalSales) {
        boolean regularUn = false;
        String orgidStr = Long.toString(orgIdL);
        // 获取对比参数
        // 零销量排半月销量 国别标准
        float zeroSalesHalfMonthSalesStandard = CommonDataSomQuo.getOrgStandard(orgidStr, "零销量品(半月销量=)");
        // 低销量 半月销量 国别标准
        float lowSalesHalfMonthSalesStandard = CommonDataSomQuo.getOrgStandard(orgidStr, "低销量品(半月销量<)");
        // 低销量库存可售天数
        float lowTurnaroundAvailableDaysInStockStandard = CommonDataSomQuo.getOrgStandard(orgidStr, "低周转品(库存可售天数≥)");
        if (halfMonthTotalSales == zeroSalesHalfMonthSalesStandard) {
            // "零销量品"
        } else if (halfMonthTotalSales > zeroSalesHalfMonthSalesStandard
            && halfMonthTotalSales < lowSalesHalfMonthSalesStandard
            && availableDays >= lowTurnaroundAvailableDaysInStockStandard) {
            // 低销量低周转品
        } else if (availableDays >= TWE) {
            // 高销量低周转 判断调整为: 可售天数>=120且 US/UK 7天日均<6，其他国家7天日均<3
            if ((US.equals(aosOrgnumber) || UK.equals(aosOrgnumber))) {
                regularUn = (r >= SIX);
            } else {
                regularUn = (r >= THREE);
            }
        } else {
            // 其他情况下都跳过
            regularUn = true;
        }
        return regularUn;
    }

    /**
     *
     * @param aosOrgnumber 国别编码
     * @param availableDays 可售天数
     * @param r 7天日均
     * @return usType
     */
    public static String getRegularUn(String aosOrgnumber, int availableDays, float r) {
        String usType = "";
        // 可售天数标准 可售天数(7天日均) > 90(法国60)
        boolean availbaleDayStand = ("FR".equals(aosOrgnumber) && availableDays > 60 && availableDays < 90)
            || (!"FR".equals(aosOrgnumber) && availableDays > 90);
        boolean cond = ((("FR".equals(aosOrgnumber) && availableDays >= 90)
            || (!"FR".equals(aosOrgnumber) && availableDays >= 120)));
        // 不是爆品,7天日均小于等于0.42 且可售天数(7天日均) > 90(法国60) 则为 "低动销"
        if (r <= FOURTYTWO && availbaleDayStand) {
            usType = "低动销";
        }
        // 低周转:可售天数(7天日均)>=120天(法国90天), 且0.43<日均<6(US,UK)其他3
        else if (cond) {
            if (US.equals(aosOrgnumber) || UK.equals(aosOrgnumber)) {
                if (r > FOURTYTHREE && r < SIX) {
                    usType = "低周转";
                }
            } else {
                if (r < THREE) {
                    usType = "低周转";
                }
            }
        }
        return usType;
    }

    /**
     * 判断季节品阶段
     * 
     * @param seasonattr 季节属性
     * @return result
     */
    public static String seasonalProStage(String seasonattr) {
        String result = "";
        Calendar instance = Calendar.getInstance();
        int month = instance.get(Calendar.MONTH);
        boolean cond1 = ("AUTUMN_WINTER".equals(seasonattr) || "WINTER".equals(seasonattr));
        boolean cond2 =
            ("SPRING".equals(seasonattr) || "SPRING_SUMMER".equals(seasonattr) || "SUMMER".equals(seasonattr));
        if (cond1) {
            if (month == Calendar.AUGUST || month == Calendar.SEPTEMBER || month == Calendar.OCTOBER) {
                result = "季初";
            } else if (month == Calendar.DECEMBER || month == Calendar.NOVEMBER || month == Calendar.JANUARY) {
                result = "季中";
            } else if (month == Calendar.FEBRUARY || month == Calendar.MARCH) {
                result = "季末";
            }
        } else if (cond2) {
            if (month == Calendar.JANUARY || month == Calendar.FEBRUARY || month == Calendar.MARCH) {
                result = "季初";
            } else if (month == Calendar.APRIL || month == Calendar.MAY || month == Calendar.JUNE) {
                result = "季中";
            } else if (month == Calendar.JULY || month == Calendar.AUGUST) {
                result = "季末";
            }
        }
        return result;
    }

    public static int calInstockSalDays(String itemid, float r, String seasonalProduct, String orgId,
        String categoryId) {
        if (r == 0) {
            return 999;
        }
        // 非平台可用量
        int nonPlatQty = CommonDataSomQuo.getNonPlatQty(orgId, itemid);
        Map<Integer, Float> coefficientMap = null;
        // 如果产品月度系数还为空 则取常规产品月度系数
        coefficientMap = CommonDataSomQuo.getCategoryC(orgId, categoryId);
        if (coefficientMap == null) {
            // 如果产品月度系数还为空 则每个月的系数设为1
            coefficientMap = new HashMap<>(16);
            int count = 12;
            for (int i = 0; i < count; i++) {
                coefficientMap.put(i, 1f);
            }
        }
        Calendar instance = Calendar.getInstance();
        instance.add(Calendar.DAY_OF_MONTH, +1);
        // 当前月份
        int currentMonth = instance.get(Calendar.MONTH);
        // 当前月份
        int currentYear = instance.get(Calendar.YEAR);
        // 当月季节系数
        BigDecimal currentMonthSeasonalCoefficient = BigDecimal.valueOf(coefficientMap.get(currentMonth));
        if (currentMonthSeasonalCoefficient.floatValue() == 0) {
            return 999;
        }
        // 日均销量
        BigDecimal x = BigDecimal.valueOf(r);
        BigDecimal inStockNumTemp = BigDecimal.valueOf(nonPlatQty);
        int count = 0;
        while (inStockNumTemp.floatValue() > 0) {
            // 剩余库存大于0 且要大于日均销量
            // 获取月份 月份的季度系数
            int month = instance.get(Calendar.MONTH);
            int year = instance.get(Calendar.YEAR);
            int day = instance.get(Calendar.DAY_OF_MONTH);
            boolean cond = (("SPRING_SUMMER_PRO".equals(seasonalProduct)
                && ((month == Calendar.AUGUST && day > 16) || month > Calendar.AUGUST))
                || ("AUTUMN_WINTER_PRO".equals(seasonalProduct)
                    && ((month == Calendar.MARCH && day > 16) || (month > Calendar.MARCH && month < Calendar.AUGUST))));
            if (cond) {
                ++count;
                instance.add(Calendar.DAY_OF_MONTH, +1);
                continue;
            }
            if (month != currentMonth || year != currentYear) {
                BigDecimal seasonalCoefficient = BigDecimal.valueOf(coefficientMap.get(month));
                x = BigDecimal.valueOf(r).multiply(seasonalCoefficient).divide(currentMonthSeasonalCoefficient, 10,
                    RoundingMode.HALF_UP);
            }
            // 日均销量
            inStockNumTemp = inStockNumTemp.subtract(x);
            ++count;
            instance.add(Calendar.DAY_OF_MONTH, +1);
        }
        return count;
    }

    /**
     * 获取季节范围
     * 
     * @param context 条件
     * @param type 类型
     * @param pOrgId 国别ID
     * @return date
     */
    public static Date getDateRange(String context, String type, Object pOrgId) {
        Date date = null;
        QFilter filterType = new QFilter("aos_type", "=", type);
        QFilter filterOrg = new QFilter("aos_orgid", "=", pOrgId);
        QFilter[] filters = new QFilter[] {filterType, filterOrg};
        DynamicObject aosMktBaseSeason = QueryServiceHelper.queryOne("aos_mkt_base_date", context, filters);
        if (aosMktBaseSeason != null) {
            date = aosMktBaseSeason.getDate(context);
        }
        return date;
    }

    /**
     * 获取国别标准
     * 
     * @param pOuCode 国别编码
     * @return standardOrgQty
     */
    public static int getStandardOrgQty(Object pOuCode) {
        int standardOrgQty = 0;
        QFilter filterOrg = new QFilter("aos_org.number", "=", pOuCode);
        // 调价库存标准类型
        QFilter filterType = new QFilter("aos_project.number", "=", "00000006");
        QFilter[] filters = new QFilter[] {filterOrg, filterType};
        String selectField = "aos_value";
        DynamicObject aosSalQuomCoe = QueryServiceHelper.queryOne("aos_sal_quo_m_coe", selectField, filters);
        if (aosSalQuomCoe != null) {
            standardOrgQty = aosSalQuomCoe.getBigDecimal("aos_value").intValue();
        }
        return standardOrgQty;
    }

    /**
     * 获取间隔日期
     * 
     * @param endDay 结束日期
     * @param fromDay 开始日期
     * @return 日期间隔
     */
    public static int getBetweenDays(Date endDay, Date fromDay) {
        return (int)((endDay.getTime() - fromDay.getTime()) / (1000 * 3600 * 24));
    }

    public static DynamicObjectCollection putSyncLog(DynamicObjectCollection aosSyncLogS, String info) {
        DynamicObject aosSyncLog = aosSyncLogS.addNew();
        aosSyncLog.set("aos_content", info);
        return aosSyncLogS;
    }

    /**
     * 发送全局消息通知
     * 
     * @param receiver 接收人
     * @param entityName 实体标识
     * @param billPkId 单据主键
     * @param title 标题
     * @param content 文本内容
     */
    public static void sendGlobalMessage(String receiver, String entityName, String billPkId, String title,
        String content) {

        NotificationBody notificationBody = new NotificationBody();
        notificationBody.setAppId("aos");
        notificationBody.setTitle(title);
        notificationBody.setContent(content);
        notificationBody.setNotificationId(UUID.randomUUID().toString());
        notificationBody.setIconType(IconType.Info.toString());

        // 忽略按钮平台自动添加
        NotificationBody.ButtonInfo detailButton = new NotificationBody.ButtonInfo();
        detailButton.setKey("aos_detail");
        detailButton.setText("查看详情");
        notificationBody.addButtonInfo(detailButton);

        // 消息窗口按钮点击事件处理的类
        notificationBody.setClickClassName("mkt.common.GlobalMessage");

        // 自定义参数 (用于点击事件)
        Map<String, Object> params = new HashMap<>(16);
        params.put("name", entityName);
        params.put("id", billPkId);
        notificationBody.setParams(params);

        // 创建消息通知界面参数
        NotificationFormInfo notificationFormInfo = new NotificationFormInfo();
        notificationFormInfo.setNotification(notificationBody);

        // 调用发送全局消息帮助服务方法
        NotificationServiceHelper.sendNotification(Collections.singletonList(String.valueOf(receiver)),
            notificationFormInfo);
    }

    /** 获取物料的中文分类名称 **/
    public static String getItemCateNameZh(Object skuId) {
        QFilter filterSku = new QFilter("material.id", QCP.equals, skuId);
        QFilter filterStand = new QFilter("standard.number", QCP.equals, "JBFLBZ");
        QFilter qFilterGroup = new QFilter("group.number", "!=", "waitgroup");
        DynamicObject[] itemCate = BusinessDataServiceHelper.load("bd_materialgroupdetail", "group",
            new QFilter[] {filterSku, filterStand, qFilterGroup}, null, 1);
        if (itemCate.length > 0) {
            String cateName = itemCate[0].getDynamicObject("group").getLocaleString("name").getLocaleValue_zh_CN();
            String[] split = cateName.split(",");
            if (split.length >= THREE3) {
                if (!split[1].equals(GROUPITEM)) {
                    return cateName;
                }
            }
        }
        // 获取关联的sku
        QFilter filterItem = new QFilter("id", "=", skuId);
        QFilter filterCate = new QFilter("aos_contryentry.aos_submaterialentry.aos_submaterial", "!=", "");
        StringJoiner str = new StringJoiner(",");
        str.add("number");
        str.add("aos_contryentry.aos_submaterialentry.aos_submaterial aos_submaterial");
        List<String> listOtherSku =
            QueryServiceHelper.query("bd_material", str.toString(), new QFilter[] {filterCate, filterItem}).stream()
                .map(dy -> dy.getString("aos_submaterial")).distinct().collect(Collectors.toList());
        filterSku = new QFilter("material.id", QCP.in, listOtherSku);
        itemCate = BusinessDataServiceHelper.load("bd_materialgroupdetail", "group",
            new QFilter[] {filterSku, filterStand, qFilterGroup}, null, 1);
        for (DynamicObject dy : itemCate) {
            String cateName = dy.getDynamicObject("group").getLocaleString("name").getLocaleValue_zh_CN();
            String[] split = cateName.split(",");
            if (split.length >= 3) {
                if (!split[1].equals(GROUPITEM)) {
                    return cateName;
                }
            }
        }
        return "";
    }

    /**
     *
     * @param billName 单据标识
     * @param comboName 下拉框标识
     * @return K 下拉值 V下拉标题
     */
    public static Map<String, String> getComboMap(String billName, String comboName) {
        // 界面标识
        MainEntityType type = MetadataServiceHelper.getDataEntityType(billName);
        // 下拉框标识
        ComboProp combo = (ComboProp)type.findProperty(comboName);
        List<ValueMapItem> items = combo.getComboItems();
        Map<String, String> result = new HashMap<>(16);
        for (ValueMapItem item : items) {
            String title = item.getName().getLocaleValue();
            String value = item.getValue();
            result.put(value, title);
        }
        return result;
    }
}
