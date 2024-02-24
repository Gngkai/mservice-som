package mkt.act.target;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndDate;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.datamodel.events.ChangeData;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.report.CellStyle;
import kd.bos.form.control.AbstractGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

/**
 * 月活动目标设置表单插件
 *
 * @author aosom
 */
public class AosMktAcTargetBill extends AbstractBillPlugIn {
    /**
     * 系统管理员
     **/
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    public final static String A = "A";
    public final static String B = "B";
    public final static String AOS_GET = "aos_get";
    public final static String AOS_REGET = "aos_reget";
    public final static String AOS_CAL = "aos_cal";
    public final static String SAVE = "save";
    public final static String SUBMIT = "submit";

    /**
     * 进入界面后触发
     */
    @Override
    public void afterCreateNewData(EventObject e) {
        initEntity();
    }

    /**
     * 初始化事件
     **/
    @Override
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        statusControl();
        setCurrentTotal(0);
        calTimeS();
        setColor();
    }

    /**
     * 全局状态控制
     **/
    private void statusControl() {
        Object billStatus = this.getModel().getValue("billstatus");
        if (A.equals(billStatus)) {
            this.getView().setVisible(true, "aos_cal");
            this.getView().setVisible(true, "aos_get");
        } else if (B.equals(billStatus)) {
            this.getView().setVisible(false, "aos_cal");
            this.getView().setVisible(false, "aos_get");
        }
    }

    /**
     * 值改变事件
     */
    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        ChangeData[] changeSet = e.getChangeSet();
        try {
            switch (name) {
                case "aos_home":
                case "aos_rattan":
                case "aos_yard":
                case "aos_sport":
                case "aos_pet":
                case "aos_kid":
                case "aos_total": {
                    String newValue = (String)changeSet[0].getNewValue();
                    lineRowChanged(name, newValue);
                    break;
                }
                case "aos_amount_l":
                case "aos_rate_l":
                case "aos_home_l":
                case "aos_rattan_l":
                case "aos_yard_l":
                case "aos_sport_l":
                case "aos_pet_l":
                case "aos_kid_l": {
                    String newValue = (String)changeSet[0].getNewValue();
                    detailRowChanged(name, newValue);
                    break;
                }
                case "aos_rate": {
                    BigDecimal newValue = (BigDecimal)changeSet[0].getNewValue();
                    aosRateChanged(newValue);
                    break;
                }
                case "aos_month":
                    calTimeS();
                    break;
                default:
                    break;
            }
        } catch (FndError fndError) {
            fndError.show(getView());
        } catch (Exception ex) {
            FndError.showex(getView(), ex);
        }
    }

    /**
     * 计算平台活动次数
     */
    private void calTimeS() {
        Object aosMonthObj = this.getModel().getValue("aos_month");
        Object aosOrgIdObj = this.getModel().getValue("aos_orgid");
        if (FndGlobal.IsNotNull(aosMonthObj) && FndGlobal.IsNotNull(aosOrgIdObj)) {
            DynamicObject aosOrgid = (DynamicObject)aosOrgIdObj;
            String aosOrgIdStr = aosOrgid.getPkValue().toString();
            Set<String> aosSalActLibS = getSalActLib(aosOrgIdStr);
            Date aosMonth = (Date)aosMonthObj;
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(aosMonth);
            // 获取当月第一天
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMinimum(Calendar.DAY_OF_MONTH));
            Date dateFrom = calendar.getTime();
            // 获取当月最后一天
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
            Date dateTo = calendar.getTime();
            DynamicObjectCollection selectS = QueryServiceHelper.query("aos_act_select_plan", "id," + "billno",
                new QFilter("aos_enddate1", QCP.large_equals, dateFrom).and("aos_enddate1", QCP.less_equals, dateTo)
                    .and("aos_acttype", QCP.in, aosSalActLibS).and("aos_nationality.id", QCP.equals, aosOrgIdStr)
                    .toArray());
            for (DynamicObject select : selectS) {
                FndMsg.debug(select.getString("billno"));
            }
            if (FndGlobal.IsNotNull(selectS)) {
                this.getModel().setValue("aos_times", selectS.size());
            }
        }
    }

    /**
     * 活动营收占比值改变事件
     *
     * @param newValue 新值
     */
    private void aosRateChanged(BigDecimal newValue) {
        FndMsg.debug("=====into aosRateChanged=====");
        FndMsg.debug("value:" + newValue);
        String aosHome = (String)this.getModel().getValue("aos_home", 7);
        String aosRattan = (String)this.getModel().getValue("aos_rattan", 7);
        String aosYard = (String)this.getModel().getValue("aos_yard", 7);
        String aosSport = (String)this.getModel().getValue("aos_sport", 7);
        String aosPet = (String)this.getModel().getValue("aos_pet", 7);
        String aosKid = (String)this.getModel().getValue("aos_kid", 7);
        String aosTotal = (String)this.getModel().getValue("aos_total", 7);
        BigDecimal aosHomeBd;
        if (FndGlobal.IsNotNull(aosHome)) {
            aosHomeBd = new BigDecimal(aosHome);
            this.getModel().setValue("aos_home", aosHomeBd.multiply(newValue).setScale(0, RoundingMode.HALF_UP), 1);
        }
        BigDecimal aosRattanBd;
        if (FndGlobal.IsNotNull(aosRattan)) {
            aosRattanBd = new BigDecimal(aosRattan);
            this.getModel().setValue("aos_rattan", aosRattanBd.multiply(newValue).setScale(0, RoundingMode.HALF_UP), 1);
        }
        BigDecimal aosYardBd;
        if (FndGlobal.IsNotNull(aosYard)) {
            aosYardBd = new BigDecimal(aosYard);
            this.getModel().setValue("aos_yard", aosYardBd.multiply(newValue).setScale(0, RoundingMode.HALF_UP), 1);
        }
        BigDecimal aosSportBd;
        if (FndGlobal.IsNotNull(aosSport)) {
            aosSportBd = new BigDecimal(aosSport);
            this.getModel().setValue("aos_sport", aosSportBd.multiply(newValue).setScale(0, RoundingMode.HALF_UP), 1);
        }
        BigDecimal aosPetBd;
        if (FndGlobal.IsNotNull(aosPet)) {
            aosPetBd = new BigDecimal(aosPet);
            this.getModel().setValue("aos_pet", aosPetBd.multiply(newValue).setScale(0, RoundingMode.HALF_UP), 1);
        }
        BigDecimal aosKidBd;
        if (FndGlobal.IsNotNull(aosKid)) {
            aosKidBd = new BigDecimal(aosKid);
            this.getModel().setValue("aos_kid", aosKidBd.multiply(newValue).setScale(0, RoundingMode.HALF_UP), 1);
        }
        BigDecimal aosTotalBd;
        if (FndGlobal.IsNotNull(aosTotal)) {
            aosTotalBd = new BigDecimal(aosTotal);
            this.getModel().setValue("aos_total", aosTotalBd.multiply(newValue).setScale(0, RoundingMode.HALF_UP), 1);
        }
        this.getView().invokeOperation("save");
    }

    /**
     * 明细值改变事件
     *
     * @param name 标识
     * @param value 待检查的值
     * @throws FndError 异常
     */
    private void detailRowChanged(String name, String value) throws FndError {
        int currentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_entryentity1");
        if (FndGlobal.IsNotNull(value)
            && !value.matches("^(-?[1-9]\\d*\\.?\\d*)|(-?0\\.\\d*[1-9])|(-?[0])|(-?[0]\\.\\d*)$")) {
            this.getModel().setValue(name, null, currentRowIndex);
            throw new FndError("字段格式错误");
        }
        Object name0 = this.getModel().getValue(name, 0);
        Object name1 = this.getModel().getValue(name, 1);
        if (FndGlobal.IsNotNull(name0) && FndGlobal.IsNotNull(name1)) {
            BigDecimal name0Bd = new BigDecimal(name0.toString());
            BigDecimal name1Bd = new BigDecimal(name1.toString());
            BigDecimal name2Bd = BigDecimal.ZERO;
            if (name1Bd.compareTo(BigDecimal.ZERO) != 0) {
                name2Bd = name1Bd.subtract(name0Bd).divide(name1Bd, 2, RoundingMode.HALF_UP);
                this.getModel().setValue(name, String.valueOf(name2Bd), 2);
                this.getView().invokeOperation("save");
            }
        }
    }

    /**
     * 行值改变事件
     *
     * @param value 待检查的值
     * @param name 标识
     */
    private void lineRowChanged(String name, String value) throws FndError {
        int currentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_entryentity");
        int seven = 7;
        int eight = 8;
        // 合计汇总
        if (FndGlobal.IsNotNull(value)
            && !value.matches("^(-?[1-9]\\d*\\.?\\d*)|(-?0\\.\\d*[1-9])|(-?[0])|(-?[0]\\.\\d*)$")) {
            this.getModel().setValue(name, null, currentRowIndex);
            throw new FndError("字段格式错误");
        }
        if (currentRowIndex == 0) {
            // 计算合计
            setCurrentTotal(currentRowIndex);
        } else if (currentRowIndex == seven) {
            setCurrentTotal(currentRowIndex);
            Object name7 = this.getModel().getValue(name, currentRowIndex);
            Object aosRate = this.getModel().getValue("aos_rate");
            if (FndGlobal.IsNotNull(name7) && FndGlobal.IsNotNull(aosRate)) {
                BigDecimal name7Bd = new BigDecimal(name7.toString());
                BigDecimal aosRateBd = (BigDecimal)aosRate;
                this.getModel().setValue(name, aosRateBd.multiply(name7Bd).setScale(0, RoundingMode.HALF_UP), 1);
                this.getView().invokeOperation("save");
            }
            // 计算活动计划占比
            Object name2 = this.getModel().getValue(name, 2);
            if (FndGlobal.IsNotNull(name7) && FndGlobal.IsNotNull(name2)) {
                BigDecimal name7Bd = new BigDecimal(name7.toString());
                BigDecimal name2Bd = new BigDecimal(name2.toString());
                if (name7Bd.compareTo(BigDecimal.ZERO) != 0) {
                    this.getModel().setValue(name,
                        name2Bd.divide(name7Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros(), 5);
                }
                this.getView().invokeOperation("save");
            }
        } else if (currentRowIndex == eight) {
            setCurrentTotal(currentRowIndex);
            // 计算上月实际占比
            Object name8 = this.getModel().getValue(name, currentRowIndex);
            Object name3 = this.getModel().getValue(name, 3);
            if (FndGlobal.IsNotNull(name8) && FndGlobal.IsNotNull(name3)) {
                BigDecimal name8Bd = new BigDecimal(name8.toString());
                BigDecimal name3Bd = new BigDecimal(name3.toString());
                if (name8Bd.compareTo(BigDecimal.ZERO) != 0) {
                    this.getModel().setValue(name,
                        changeToRate(name3Bd.divide(name8Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros()), 6);
                }
                this.getView().invokeOperation("save");
            }
        }
        // 无论是0 还是7的修改都需要 计算本月目标
        Object name0 = this.getModel().getValue(name, 0);
        Object name7 = this.getModel().getValue(name, 7);
        if (FndGlobal.IsNotNull(name0) && FndGlobal.IsNotNull(name7)) {
            BigDecimal name0Bd = new BigDecimal(name0.toString());
            BigDecimal name7Bd = new BigDecimal(name7.toString());
            BigDecimal name4Bd;
            if (name7Bd.compareTo(BigDecimal.ZERO) != 0) {
                name4Bd = name0Bd.divide(name7Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros();
                this.getModel().setValue(name, changeToRate(name4Bd), 4);
                this.getView().invokeOperation("save");
            }
        }
    }

    private String changeToRate(BigDecimal value) {
        // 将 BigDecimal 转化为百分比字符串
        BigDecimal percentage = value.multiply(new BigDecimal("100"));
        return percentage.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /**
     * 计算当前行合计
     *
     * @param currentRowIndex 当前行号
     */
    private void setCurrentTotal(int currentRowIndex) {
        String aosHome = (String)this.getModel().getValue("aos_home", currentRowIndex);
        String aosRattan = (String)this.getModel().getValue("aos_rattan", currentRowIndex);
        String aosYard = (String)this.getModel().getValue("aos_yard", currentRowIndex);
        String aosSport = (String)this.getModel().getValue("aos_sport", currentRowIndex);
        String aosPet = (String)this.getModel().getValue("aos_pet", currentRowIndex);
        String aosKid = (String)this.getModel().getValue("aos_kid", currentRowIndex);
        BigDecimal aosHomeBd = BigDecimal.ZERO;
        if (FndGlobal.IsNotNull(aosHome)) {
            aosHomeBd = new BigDecimal(aosHome);
        }
        BigDecimal aosRattanBd = BigDecimal.ZERO;
        if (FndGlobal.IsNotNull(aosRattan)) {
            aosRattanBd = new BigDecimal(aosRattan);
        }
        BigDecimal aosYardBd = BigDecimal.ZERO;
        if (FndGlobal.IsNotNull(aosYard)) {
            aosYardBd = new BigDecimal(aosYard);
        }
        BigDecimal aosSportBd = BigDecimal.ZERO;
        if (FndGlobal.IsNotNull(aosSport)) {
            aosSportBd = new BigDecimal(aosSport);
        }
        BigDecimal aosPetBd = BigDecimal.ZERO;
        if (FndGlobal.IsNotNull(aosPet)) {
            aosPetBd = new BigDecimal(aosPet);
        }
        BigDecimal aosKidBd = BigDecimal.ZERO;
        if (FndGlobal.IsNotNull(aosKid)) {
            aosKidBd = new BigDecimal(aosKid);
        }
        BigDecimal aosTotalBd = aosHomeBd.add(aosRattanBd).add(aosYardBd).add(aosSportBd).add(aosPetBd).add(aosKidBd);
        this.getModel().setValue("aos_total", String.valueOf(aosTotalBd), currentRowIndex);
        this.getModel().setValue("aos_amount", String.valueOf(aosTotalBd));
        this.getView().invokeOperation("save");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        try {
            if (AOS_GET.equals(control)) {
                aosGet();// 认领
            } else if (AOS_REGET.equals(control)) {
                aosReget();// 取消认领
            } else if (AOS_CAL.equals(control)) {
                aosCal();// 计算营收
            }
        } catch (FndError fndError) {
            fndError.show(getView());
        } catch (Exception ex) {
            FndError.showex(getView(), ex);
        }
    }

    /**
     * 计算营收
     */
    private void aosCal() {
        // 计算活动计划
        calActivityPlan();
        // 计算上月实际
        calLastMonthActual();
        // 保存计算的值
        this.getView().invokeOperation("save");
    }

    /**
     * 计算上月实际
     */
    private void calLastMonthActual() {
        // 数据层
        Date aosMonth = (Date)this.getModel().getValue("aos_month");
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(aosMonth);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        // 开始日期
        Date fromDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 1);
        // 结束日期
        Date toDate = calendar.getTime();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        DynamicObject aosOrgid = (DynamicObject)this.getModel().getValue("aos_orgid");
        String aosOrgIdStr = aosOrgid.getPkValue().toString();
        // 活动库
        Set<String> aosSalActLibS = getSalActLib(aosOrgIdStr);
        // 查询活动与选品表
        Map<String, BigDecimal> cateAmtMap = new HashMap<>(16);
        QFilter orgQf = new QFilter("aos_nationality", QCP.equals, aosOrgIdStr);
        QFilter actTypeQf = new QFilter("aos_acttype", QCP.in, aosSalActLibS);
        QFilter statusQf = new QFilter("aos_actstatus", QCP.not_equals, "C");
        // 品类金额 情况① 开始日期与结束日期在本月之中
        QFilter dateQfSec1 = new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.large_than, fromDate)
            .and("aos_sal_actplanentity.aos_enddate", QCP.less_than, toDate);
        DynamicObjectCollection aosActSelectPlanSec1S = QueryServiceHelper.query("aos_act_select_plan",
            "aos_sal_actplanentity.aos_category_stat1 aos_category_stat1,"
                + "   aos_sal_actplanentity.aos_incre_reven aos_incre_reven",
            orgQf.and(actTypeQf).and(statusQf).and(dateQfSec1).toArray());
        for (DynamicObject aosActSelectPlanSec1 : aosActSelectPlanSec1S) {
            String aosCateGoryStat1 = aosActSelectPlanSec1.getString("aos_category_stat1");
            BigDecimal increRevenBd = cateAmtMap.computeIfAbsent(aosCateGoryStat1, k -> BigDecimal.ZERO);
            increRevenBd = increRevenBd.add(aosActSelectPlanSec1.getBigDecimal("aos_incre_reven"));
            cateAmtMap.put(aosCateGoryStat1, increRevenBd);
        }

        // 品类金额 情况② 开始日期在本月之中 结束日期在本月之后 需要做折算
        QFilter dateQfSec2 = new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_than, fromDate)
            .and("aos_sal_actplanentity.aos_enddate", QCP.large_than, fromDate)
            .and("aos_sal_actplanentity.aos_enddate", QCP.less_than, toDate);
        DynamicObjectCollection aosActSelectPlanSec2S = QueryServiceHelper.query("aos_act_select_plan",
            "aos_sal_actplanentity.aos_category_stat1 aos_category_stat1,"
                + "   aos_sal_actplanentity.aos_incre_reven aos_incre_reven,"
                + "aos_sal_actplanentity.aos_l_startdate aos_l_startdate,"
                + "aos_sal_actplanentity.aos_enddate aos_enddate",
            orgQf.and(actTypeQf).and(statusQf).and(dateQfSec2).toArray());
        for (DynamicObject aosActSelectPlanSec2 : aosActSelectPlanSec2S) {
            String aosCateGoryStat1 = aosActSelectPlanSec2.getString("aos_category_stat1");
            BigDecimal increRevenBd = cateAmtMap.computeIfAbsent(aosCateGoryStat1, k -> BigDecimal.ZERO);
            Date startDate = aosActSelectPlanSec2.getDate("aos_l_startdate");
            Date endDate = aosActSelectPlanSec2.getDate("aos_enddate");
            // 总天数
            int totalDate = FndDate.GetBetweenDays(endDate, startDate);
            // 范围中的天数
            int areaDate = FndDate.GetBetweenDays(endDate, fromDate);
            increRevenBd = increRevenBd.add(aosActSelectPlanSec2.getBigDecimal("aos_incre_reven")
                .multiply(BigDecimal.valueOf(areaDate / totalDate)));
            cateAmtMap.put(aosCateGoryStat1, increRevenBd);
        }

        // 品类金额 情况③ 结束日期在本月之中 开始日期在本月之前 需要做折算
        QFilter dateQfSec3 = new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_than, toDate)
            .and("aos_sal_actplanentity.aos_enddate", QCP.large_than, toDate)
            .and("aos_sal_actplanentity.aos_l_startdate", QCP.large_than, fromDate);
        DynamicObjectCollection aosActSelectPlanSec3S = QueryServiceHelper.query("aos_act_select_plan",
            "aos_sal_actplanentity.aos_category_stat1 aos_category_stat1,"
                + "   aos_sal_actplanentity.aos_incre_reven aos_incre_reven,"
                + "aos_sal_actplanentity.aos_l_startdate aos_l_startdate,"
                + "aos_sal_actplanentity.aos_enddate aos_enddate",
            orgQf.and(actTypeQf).and(statusQf).and(dateQfSec3).toArray());

        for (DynamicObject aosActSelectPlanSec3 : aosActSelectPlanSec3S) {
            String aosCateGoryStat1 = aosActSelectPlanSec3.getString("aos_category_stat1");
            BigDecimal increRevenBd = cateAmtMap.computeIfAbsent(aosCateGoryStat1, k -> BigDecimal.ZERO);
            Date startDate = aosActSelectPlanSec3.getDate("aos_l_startdate");
            Date endDate = aosActSelectPlanSec3.getDate("aos_enddate");
            // 总天数
            int totalDate = FndDate.GetBetweenDays(endDate, startDate);
            // 范围中的天数
            int areaDate = FndDate.GetBetweenDays(toDate, startDate);
            increRevenBd = increRevenBd.add(aosActSelectPlanSec3.getBigDecimal("aos_incre_reven")
                .multiply(BigDecimal.valueOf(areaDate / totalDate)));
            cateAmtMap.put(aosCateGoryStat1, increRevenBd);
        }
        // 品类金额 情况④ 开始日期与结束日期完全包括了本月 需要做折算
        QFilter dateQfSec4 = new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.large_than, fromDate)
            .and("aos_sal_actplanentity.aos_enddate", QCP.less_than, toDate);
        DynamicObjectCollection aosActSelectPlanSec4S = QueryServiceHelper.query("aos_act_select_plan",
            "aos_sal_actplanentity.aos_category_stat1 aos_category_stat1,"
                + "   aos_sal_actplanentity.aos_incre_reven aos_incre_reven,"
                + "aos_sal_actplanentity.aos_l_startdate aos_l_startdate,"
                + "aos_sal_actplanentity.aos_enddate aos_enddate",
            orgQf.and(actTypeQf).and(statusQf).and(dateQfSec4).toArray());
        for (DynamicObject aosActSelectPlanSec4 : aosActSelectPlanSec4S) {
            String aosCateGoryStat1 = aosActSelectPlanSec4.getString("aos_category_stat1");
            BigDecimal increRevenBd = cateAmtMap.computeIfAbsent(aosCateGoryStat1, k -> BigDecimal.ZERO);
            Date startDate = aosActSelectPlanSec4.getDate("aos_l_startdate");
            Date endDate = aosActSelectPlanSec4.getDate("aos_enddate");
            // 总天数
            int totalDate = FndDate.GetBetweenDays(endDate, startDate);
            // 范围中的天数
            int areaDate = FndDate.GetBetweenDays(toDate, fromDate);
            increRevenBd = increRevenBd.add(aosActSelectPlanSec4.getBigDecimal("aos_incre_reven")
                .multiply(BigDecimal.valueOf(areaDate / totalDate)));
            cateAmtMap.put(aosCateGoryStat1, increRevenBd);
        }

        BigDecimal aosHome3Bd = cateAmtMap.getOrDefault("居家系列", BigDecimal.ZERO).stripTrailingZeros();
        BigDecimal aosRattan3Bd = cateAmtMap.getOrDefault("藤编产品", BigDecimal.ZERO).stripTrailingZeros();
        BigDecimal aosYard3Bd = cateAmtMap.getOrDefault("庭院&花园", BigDecimal.ZERO).stripTrailingZeros();
        BigDecimal aosSport3Bd = cateAmtMap.getOrDefault("运动&娱乐&汽配", BigDecimal.ZERO).stripTrailingZeros();
        BigDecimal aosPet3Bd = cateAmtMap.getOrDefault("宠物用品", BigDecimal.ZERO).stripTrailingZeros();
        BigDecimal aosKid3Bd = cateAmtMap.getOrDefault("婴儿产品&玩具及游戏", BigDecimal.ZERO).stripTrailingZeros();
        BigDecimal aosTotal3Bd =
            aosHome3Bd.add(aosRattan3Bd).add(aosYard3Bd).add(aosSport3Bd).add(aosPet3Bd).add(aosKid3Bd);
        this.getModel().setValue("aos_home", String.valueOf(aosHome3Bd), 3);
        this.getModel().setValue("aos_rattan", String.valueOf(aosRattan3Bd), 3);
        this.getModel().setValue("aos_yard", String.valueOf(aosYard3Bd), 3);
        this.getModel().setValue("aos_sport", String.valueOf(aosSport3Bd), 3);
        this.getModel().setValue("aos_pet", String.valueOf(aosPet3Bd), 3);
        this.getModel().setValue("aos_kid", String.valueOf(aosKid3Bd), 3);
        this.getModel().setValue("aos_total", String.valueOf(aosTotal3Bd), 3);

        // 计算上月实际占比
        BigDecimal aosTotal8Bd = BigDecimal.ZERO;
        BigDecimal aosHome8Bd = BigDecimal.ZERO;
        BigDecimal aosRattan8Bd = BigDecimal.ZERO;
        BigDecimal aosYard8Bd = BigDecimal.ZERO;
        BigDecimal aosSport8Bd = BigDecimal.ZERO;
        BigDecimal aosPet8Bd = BigDecimal.ZERO;
        BigDecimal aosKid8Bd = BigDecimal.ZERO;
        Object aosTotal8 = this.getModel().getValue("aos_total", 8);
        if (FndGlobal.IsNotNull(aosTotal8)) {
            aosTotal8Bd = new BigDecimal(aosTotal8.toString());
        }
        Object aosHome8 = this.getModel().getValue("aos_home", 8);
        if (FndGlobal.IsNotNull(aosHome8)) {
            aosHome8Bd = new BigDecimal(aosHome8.toString());
        }
        Object aosRattan8 = this.getModel().getValue("aos_rattan", 8);
        if (FndGlobal.IsNotNull(aosRattan8)) {
            aosRattan8Bd = new BigDecimal(aosRattan8.toString());
        }
        Object aosYard8 = this.getModel().getValue("aos_yard", 8);
        if (FndGlobal.IsNotNull(aosYard8)) {
            aosYard8Bd = new BigDecimal(aosYard8.toString());
        }
        Object aosSport8 = this.getModel().getValue("aos_sport", 8);
        if (FndGlobal.IsNotNull(aosSport8)) {
            aosSport8Bd = new BigDecimal(aosSport8.toString());
        }
        Object aosPet8 = this.getModel().getValue("aos_pet", 8);
        if (FndGlobal.IsNotNull(aosPet8)) {
            aosPet8Bd = new BigDecimal(aosPet8.toString());
        }
        Object aosKid8 = this.getModel().getValue("aos_kid", 8);
        if (FndGlobal.IsNotNull(aosKid8)) {
            aosKid8Bd = new BigDecimal(aosKid8.toString());
        }

        if (aosTotal8Bd.compareTo(BigDecimal.ZERO) != 0) {
            this.getModel().setValue("aos_total",
                changeToRate(aosTotal3Bd.divide(aosTotal8Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros()), 6);
        }
        if (aosHome8Bd.compareTo(BigDecimal.ZERO) != 0) {
            this.getModel().setValue("aos_home",
                changeToRate(aosHome3Bd.divide(aosHome8Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros()), 6);
        }
        if (aosRattan8Bd.compareTo(BigDecimal.ZERO) != 0) {
            this.getModel().setValue("aos_rattan",
                changeToRate(aosRattan3Bd.divide(aosRattan8Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros()), 6);
        }
        if (aosYard8Bd.compareTo(BigDecimal.ZERO) != 0) {
            this.getModel().setValue("aos_yard",
                changeToRate(aosYard3Bd.divide(aosYard8Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros()), 6);
        }
        if (aosSport8Bd.compareTo(BigDecimal.ZERO) != 0) {
            this.getModel().setValue("aos_sport",
                changeToRate(aosSport3Bd.divide(aosSport8Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros()), 6);
        }
        if (aosPet8Bd.compareTo(BigDecimal.ZERO) != 0) {
            this.getModel().setValue("aos_pet",
                changeToRate(aosPet3Bd.divide(aosPet8Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros()), 6);
        }
        if (aosKid8Bd.compareTo(BigDecimal.ZERO) != 0) {
            this.getModel().setValue("aos_kid",
                changeToRate(aosKid3Bd.divide(aosKid8Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros()), 6);
        }
    }

    private Set<String> getSalActLib(String aosOrgIdStr) {
        DynamicObjectCollection aosSalActLib =
            QueryServiceHelper.query("aos_sal_act_type_p", "aos_acttype.id aos_acttypeid",
                new QFilter("aos_assessment.number", QCP.equals, "Y").and("aos_org.id", QCP.equals, aosOrgIdStr)
                    .and("aos_sort", QCP.in, new String[] {"平台活动", "线下活动", "站外Deal"}).toArray(),
                null);
        return aosSalActLib.stream().map(obj -> obj.getString("aos_acttypeid")).collect(Collectors.toSet());
    }

    /**
     * 计算活动计划
     */
    private void calActivityPlan() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        // 判断当前月份
        Date aosMonth = (Date)this.getModel().getValue("aos_month");
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(aosMonth);
        int month = calendar.get(Calendar.MONTH);
        calendar.add(Calendar.MONTH, -12);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        Date lastFromDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 1);
        Date lastToDate = calendar.getTime();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        Calendar calendar2 = Calendar.getInstance();
        calendar2.setTime(aosMonth);
        calendar2.set(Calendar.DAY_OF_MONTH, 1);
        Date toDate = calendar2.getTime();
        calendar2.add(Calendar.MONTH, -6);
        Date fromDate = calendar2.getTime();
        DynamicObject aosOrgid = (DynamicObject)this.getModel().getValue("aos_orgid");
        String aosOrgIdStr = aosOrgid.getPkValue().toString();
        // 活动类型
        Set<String> aosSalActLibS = getSalActLib(aosOrgIdStr);
        Calendar calendar3 = Calendar.getInstance();
        calendar3.setTime(aosMonth);
        calendar3.set(Calendar.DAY_OF_MONTH, 1);
        Date thisMonthFrom = calendar3.getTime();
        calendar3.add(Calendar.MONTH, 1);
        Date thisMonthTo = calendar3.getTime();
        // 去年同期from
        String lastFromDateStr = df.format(lastFromDate);
        // 去年同期to
        String lastToDateStr = df.format(lastToDate);
        // 当月from
        String thisMonthFromStr = df.format(thisMonthFrom);
        // 当月to
        String thisMonthToStr = df.format(thisMonthTo);
        // 前6月from
        String fromDateStr = df.format(fromDate);
        // 前6月to
        String toDateStr = df.format(toDate);
        FndMsg.debug("当前日期:" + df.format(aosMonth));
        FndMsg.debug("去年同期from:" + lastFromDateStr);
        FndMsg.debug("去年同期to:" + lastToDateStr);
        FndMsg.debug("当月from:" + thisMonthFromStr);
        FndMsg.debug("当月to:" + thisMonthToStr);
        FndMsg.debug("前6月from:" + fromDateStr);
        FndMsg.debug("前6月to:" + toDateStr);
        // 对于庭院 藤编 4-8月
        // 庭院
        BigDecimal aosYard2Bd = BigDecimal.ZERO;
        // 藤编
        BigDecimal aosRattan2Bd = BigDecimal.ZERO;
        // 居家
        BigDecimal aosHome2Bd = BigDecimal.ZERO;
        // 运动
        BigDecimal aosSport2Bd = BigDecimal.ZERO;
        // 宠物
        BigDecimal aosPet2Bd = BigDecimal.ZERO;
        // 婴童
        BigDecimal aosKid2Bd = BigDecimal.ZERO;
        // 合计
        BigDecimal aosTotal2Bd = BigDecimal.ZERO;

        if (month == Calendar.AUGUST || month == Calendar.JULY || month == Calendar.JUNE || month == Calendar.MAY
            || month == Calendar.APRIL) {
            // 1.庭院
            aosYard2Bd = getIncreReven(aosOrgIdStr, "庭院&花园", lastFromDateStr, lastToDateStr, aosSalActLibS,
                thisMonthFromStr, thisMonthToStr);
            // 2.藤编
            aosRattan2Bd = getIncreReven(aosOrgIdStr, "藤编产品", lastFromDateStr, lastToDateStr, aosSalActLibS,
                thisMonthFromStr, thisMonthToStr);
        } else {
            // 1.庭院
            aosYard2Bd = getIncreReven(aosOrgIdStr, "庭院&花园", fromDateStr, toDateStr, aosSalActLibS, thisMonthFromStr,
                thisMonthToStr);
            // 2.藤编
            aosRattan2Bd = getIncreReven(aosOrgIdStr, "藤编产品", fromDateStr, toDateStr, aosSalActLibS, thisMonthFromStr,
                thisMonthToStr);
        }

        aosHome2Bd =
            getIncreReven(aosOrgIdStr, "居家系列", fromDateStr, toDateStr, aosSalActLibS, thisMonthFromStr, thisMonthToStr);
        aosSport2Bd = getIncreReven(aosOrgIdStr, "运动&娱乐&汽配", fromDateStr, toDateStr, aosSalActLibS, thisMonthFromStr,
            thisMonthToStr);
        aosPet2Bd =
            getIncreReven(aosOrgIdStr, "宠物用品", fromDateStr, toDateStr, aosSalActLibS, thisMonthFromStr, thisMonthToStr);
        aosKid2Bd = getIncreReven(aosOrgIdStr, "婴儿产品&玩具及游戏", fromDateStr, toDateStr, aosSalActLibS, thisMonthFromStr,
            thisMonthToStr);

        aosTotal2Bd = aosHome2Bd.add(aosRattan2Bd).add(aosYard2Bd).add(aosSport2Bd).add(aosPet2Bd).add(aosKid2Bd);
        this.getModel().setValue("aos_home", String.valueOf(aosHome2Bd), 2);
        this.getModel().setValue("aos_rattan", String.valueOf(aosRattan2Bd), 2);
        this.getModel().setValue("aos_yard", String.valueOf(aosYard2Bd), 2);
        this.getModel().setValue("aos_sport", String.valueOf(aosSport2Bd), 2);
        this.getModel().setValue("aos_pet", String.valueOf(aosPet2Bd), 2);
        this.getModel().setValue("aos_kid", String.valueOf(aosKid2Bd), 2);
        this.getModel().setValue("aos_total", String.valueOf(aosTotal2Bd), 2);

        // 计算活动计划占比
        BigDecimal aosTotal7Bd = BigDecimal.ZERO;
        BigDecimal aosHome7Bd = BigDecimal.ZERO;
        BigDecimal aosRattan7Bd = BigDecimal.ZERO;
        BigDecimal aosYard7Bd = BigDecimal.ZERO;
        BigDecimal aosSport7Bd = BigDecimal.ZERO;
        BigDecimal aosPet7Bd = BigDecimal.ZERO;
        BigDecimal aosKid7Bd = BigDecimal.ZERO;
        Object aosTotal7 = this.getModel().getValue("aos_total", 7);
        if (FndGlobal.IsNotNull(aosTotal7)) {
            aosTotal7Bd = new BigDecimal(aosTotal7.toString());
        }
        Object aosHome7 = this.getModel().getValue("aos_home", 7);
        if (FndGlobal.IsNotNull(aosHome7)) {
            aosHome7Bd = new BigDecimal(aosHome7.toString());
        }
        Object aosRattan7 = this.getModel().getValue("aos_rattan", 7);
        if (FndGlobal.IsNotNull(aosRattan7)) {
            aosRattan7Bd = new BigDecimal(aosRattan7.toString());
        }
        Object aosYard7 = this.getModel().getValue("aos_yard", 7);
        if (FndGlobal.IsNotNull(aosYard7)) {
            aosYard7Bd = new BigDecimal(aosYard7.toString());
        }
        Object aosSport7 = this.getModel().getValue("aos_sport", 7);
        if (FndGlobal.IsNotNull(aosSport7)) {
            aosSport7Bd = new BigDecimal(aosSport7.toString());
        }
        Object aosPet7 = this.getModel().getValue("aos_pet", 7);
        if (FndGlobal.IsNotNull(aosPet7)) {
            aosPet7Bd = new BigDecimal(aosPet7.toString());
        }
        Object aosKid7 = this.getModel().getValue("aos_kid", 7);
        if (FndGlobal.IsNotNull(aosKid7)) {
            aosKid7Bd = new BigDecimal(aosKid7.toString());
        }

        if (aosTotal7Bd.compareTo(BigDecimal.ZERO) != 0) {
            this.getModel().setValue("aos_total",
                changeToRate(aosTotal2Bd.divide(aosTotal7Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros()), 5);
        }
        if (aosHome7Bd.compareTo(BigDecimal.ZERO) != 0) {
            this.getModel().setValue("aos_home",
                changeToRate(aosHome2Bd.divide(aosHome7Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros()), 5);
        }
        if (aosRattan7Bd.compareTo(BigDecimal.ZERO) != 0) {
            this.getModel().setValue("aos_rattan",
                changeToRate(aosRattan2Bd.divide(aosRattan7Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros()), 5);
        }
        if (aosYard7Bd.compareTo(BigDecimal.ZERO) != 0) {
            this.getModel().setValue("aos_yard",
                changeToRate(aosYard2Bd.divide(aosYard7Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros()), 5);
        }
        if (aosSport7Bd.compareTo(BigDecimal.ZERO) != 0) {
            this.getModel().setValue("aos_sport",
                changeToRate(aosSport2Bd.divide(aosSport7Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros()), 5);
        }
        if (aosPet7Bd.compareTo(BigDecimal.ZERO) != 0) {
            this.getModel().setValue("aos_pet",
                changeToRate(aosPet2Bd.divide(aosPet7Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros()), 5);
        }
        if (aosKid7Bd.compareTo(BigDecimal.ZERO) != 0) {
            this.getModel().setValue("aos_kid",
                changeToRate(aosKid2Bd.divide(aosKid7Bd, 2, RoundingMode.HALF_UP).stripTrailingZeros()), 5);
        }

    }

    /**
     * @param orgIdStr 国别
     * @param cateGory 大类
     * @param dateFrom 营收日期从
     * @param dateTo 营收日期到
     * @param actSetS 活动类型
     * @param dateFrom2 活动数量日期从
     * @param dateTo2 活动数量日期到
     * @return 增长
     */
    private BigDecimal getIncreReven(String orgIdStr, String cateGory, String dateFrom, String dateTo,
        Set<String> actSetS, String dateFrom2, String dateTo2) {
        FndMsg.debug("计算活动计划:" + cateGory);
        BigDecimal returnVal = BigDecimal.ZERO;
        for (String actSet : actSetS) {
            // 活动增量
            BigDecimal aosIncreReven = BigDecimal.ZERO;
            // 活动SKU数量
            BigDecimal aosActQty = BigDecimal.ZERO;
            // 当前月外面的sku数量
            BigDecimal thisMonthQty = BigDecimal.ZERO;
            // 增量营收之和 & 活动SKU数量
            DataSet actSelectS = QueryServiceHelper.queryDataSet("getIncreReven." + cateGory + actSetS,
                "aos_act_select_plan", "aos_sal_actplanentity.aos_incre_reven aos_incre_reven," + "1 aos_actqty",
                new QFilter("aos_nationality.id", QCP.equals, orgIdStr)
                    .and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, cateGory)
                    .and("aos_enddate1", QCP.large_equals, dateFrom).and("aos_enddate1", QCP.less_equals, dateTo)
                    .and("aos_acttype", QCP.equals, actSet).and("aos_nationality.id", QCP.equals, orgIdStr).toArray(),
                null);
            actSelectS = actSelectS.groupBy().sum("aos_incre_reven").sum("aos_actqty").finish();
            while (actSelectS.hasNext()) {
                Row actSelect = actSelectS.next();
                aosIncreReven = actSelect.getBigDecimal("aos_incre_reven");
                aosActQty = BigDecimal.valueOf(actSelect.getInteger("aos_actqty"));
            }
            actSelectS.close();
            // 当前月外面的sku数量
            DataSet actSelectThisMonthS =
                QueryServiceHelper.queryDataSet("getIncreRevenThisMonth." + cateGory + actSetS, "aos_act_select_plan",
                    "aos_sal_actplanentity.aos_actqty aos_actqty",
                    new QFilter("aos_nationality.id", QCP.equals, orgIdStr)
                        .and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, cateGory)
                        .and("aos_enddate1", QCP.large_equals, dateFrom2).and("aos_enddate1", QCP.less_equals, dateTo2)
                        .and("aos_acttype", QCP.equals, actSet).toArray(),
                    null);
            actSelectThisMonthS = actSelectThisMonthS.groupBy().sum("aos_actqty").finish();
            while (actSelectThisMonthS.hasNext()) {
                Row actSelectThisMonth = actSelectThisMonthS.next();
                thisMonthQty = actSelectThisMonth.getBigDecimal("aos_actqty");
            }
            actSelectThisMonthS.close();
            // 如果数量为0则取计划下手工维护的值
            if (thisMonthQty.compareTo(BigDecimal.ZERO) == 0) {
                String code = "";
                if ("居家系列".equals(cateGory)) {
                    code = "aos_home_plan";
                } else if ("庭院&花园".equals(cateGory)) {
                    code = "aos_yard_plan";
                } else if ("藤编产品".equals(cateGory)) {
                    code = "aos_rattan_plan";
                } else if ("运动&娱乐&汽配".equals(cateGory)) {
                    code = "aos_sport_plan";
                } else if ("宠物用品".equals(cateGory)) {
                    code = "aos_pet_plan";
                } else if ("婴儿产品&玩具及游戏".equals(cateGory)) {
                    code = "aos_baby_plan";
                }
                DataSet planThisMonthS = QueryServiceHelper.queryDataSet("planThisMonth." + cateGory + actSetS,
                    "aos_act_select_plan", "aos_planentity." + code + " " + code,
                    new QFilter("aos_nationality.id", QCP.equals, orgIdStr)
                        .and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, cateGory)
                        .and("aos_enddate1", QCP.large_equals, dateFrom2).and("aos_enddate1", QCP.less_equals, dateTo2)
                        .and("aos_acttype", QCP.equals, actSet).toArray(),
                    null);
                planThisMonthS = planThisMonthS.groupBy().sum(code).finish();
                while (planThisMonthS.hasNext()) {
                    Row planThisMonth = planThisMonthS.next();
                    thisMonthQty = planThisMonth.getBigDecimal(code);
                }
                planThisMonthS.close();
            }
            if (aosActQty.compareTo(BigDecimal.ZERO) != 0) {
                if (FndGlobal.IsNull(thisMonthQty)) {
                    thisMonthQty = BigDecimal.ZERO;
                }
                returnVal =
                    returnVal.add(aosIncreReven.divide(aosActQty, 0, RoundingMode.HALF_UP).multiply(thisMonthQty));
            }
        }

        return returnVal;
    }

    /**
     * 取消认领
     */
    private void aosReget() {
        this.getModel().setValue("aos_user", SYSTEM);
        this.getView().invokeOperation("save");
        this.getView().showSuccessNotification("取消认领成功!");
    }

    /**
     * 认领
     */
    private void aosGet() {
        this.getModel().setValue("aos_user", UserServiceHelper.getCurrentUserId());
        this.getView().invokeOperation("save");
        this.getView().showSuccessNotification("认领成功!");
    }

    /**
     * 初始化月活动目标行
     */
    private void initEntity() {
        this.getModel().setValue("aos_creationdate", new Date());
        this.getModel().deleteEntryData("aos_entryentity");
        this.getModel().batchCreateNewEntryRow("aos_entryentity", 9);
        this.getModel().setValue("aos_project", "金额", 0);
        this.getModel().setValue("aos_project1", "手动调整", 0);
        this.getModel().setValue("aos_project", "金额", 1);
        this.getModel().setValue("aos_project1", "本月计算", 1);
        this.getModel().setValue("aos_project", "金额", 2);
        this.getModel().setValue("aos_project1", "活动计划", 2);
        this.getModel().setValue("aos_project", "金额", 3);
        this.getModel().setValue("aos_project1", "上月实际", 3);
        this.getModel().setValue("aos_project", "占比", 4);
        this.getModel().setValue("aos_project1", "本月目标", 4);
        this.getModel().setValue("aos_project", "占比", 5);
        this.getModel().setValue("aos_project1", "活动计划", 5);
        this.getModel().setValue("aos_project", "占比", 6);
        this.getModel().setValue("aos_project1", "上月实际", 6);
        this.getModel().setValue("aos_project", "营收目标", 7);
        this.getModel().setValue("aos_project1", "(去官网)", 7);
        this.getModel().setValue("aos_project", "实际营收", 8);
        this.getModel().setValue("aos_project1", "(去官网)", 8);

        this.getModel().deleteEntryData("aos_entryentity1");
        this.getModel().batchCreateNewEntryRow("aos_entryentity1", 3);
        this.getModel().setValue("aos_project_l", "上月", 0);
        this.getModel().setValue("aos_project_l", "本月", 1);
        this.getModel().setValue("aos_project_l", "环比", 2);
        this.getView().invokeOperation("save");

        // 设置可修改行背景色
        setColor();
    }

    private void setColor() {
        FndMsg.debug("============INTO setColor============");
        String[] groupS = new String[] {"aos_project", "aos_project1", "aos_total", "aos_home", "aos_rattan",
            "aos_yard", "aos_sport", "aos_pet", "aos_kid"};
        batchSetColor("aos_entryentity", groupS, 0);
        batchSetColor("aos_entryentity", groupS, 7);
        batchSetColor("aos_entryentity", groupS, 8);
        groupS = new String[] {"aos_project_l", "aos_amount_l", "aos_rate_l", "aos_home_l", "aos_rattan_l",
            "aos_yard_l", "aos_sport_l", "aos_pet_l", "aos_kid_l"};
        batchSetColor("aos_entryentity1", groupS, 0);
        batchSetColor("aos_entryentity1", groupS, 1);
    }

    private void batchSetColor(String entityName, String[] groupS, int row) {
        AbstractGrid grid = this.getView().getControl(entityName);
        List<CellStyle> csList = new ArrayList<>();
        List<String> color = new ArrayList<>(Arrays.asList(groupS));
        for (String key : color) {
            CellStyle cs = new CellStyle();
            cs.setBackColor("#40bd6e");
            cs.setFieldKey(key);
            cs.setRow(row);
            csList.add(cs);
        }
        grid.setCellStyle(csList);
    }

    /**
     * 在操作之后
     */
    @Override
    public void afterDoOperation(AfterDoOperationEventArgs args) {
        FormOperate formOperate = (FormOperate)args.getSource();
        String operatation = formOperate.getOperateKey();
        try {
            if (SAVE.equals(operatation)) {
                args.getOperationResult().setShowMessage(false);
            }
            statusControl();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 在操作之前
     */
    @Override
    public void beforeDoOperation(BeforeDoOperationEventArgs args) {
        FormOperate formOperate = (FormOperate)args.getSource();
        String operatation = formOperate.getOperateKey();
        try {
            if (SUBMIT.equals(operatation)) {
                DynamicObject aosUser = (DynamicObject)this.getModel().getValue("aos_user");
                long user = UserServiceHelper.getCurrentUserId();
                if (!(String.valueOf(user)).equals(aosUser.getPkValue().toString())) {
                    throw new FndError("非本人提交,提交失败!");
                }
                // 校验是否已存在数据
                Object fid = this.getView().getModel().getDataEntity().getPkValue();
                DynamicObject aosOrgid = (DynamicObject)this.getModel().getValue("aos_orgid");
                Date aosMonth = (Date)this.getModel().getValue("aos_month");
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(aosMonth);
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                Date fromDate = calendar.getTime();
                calendar.add(Calendar.MONTH, 1);
                Date toDate = calendar.getTime();
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                DynamicObjectCollection aosMktActarget = QueryServiceHelper.query("aos_mkt_actarget", "id",
                    new QFilter("id", QCP.not_equals, fid).and("aos_orgid", QCP.equals, aosOrgid.getPkValue())
                        .and("aos_month", QCP.large_equals, fromDate).and("aos_month", QCP.less_equals, toDate)
                        .toArray());
                if (FndGlobal.IsNotNull(aosMktActarget) && aosMktActarget.size() > 0) {
                    throw new FndError("已存在该数据!");
                }
            }
        } catch (FndError fndError) {
            fndError.show(getView());
            args.setCancel(true);
        } catch (Exception ex) {
            FndError.showex(getView(), ex);
            args.setCancel(true);
        }
    }

}
