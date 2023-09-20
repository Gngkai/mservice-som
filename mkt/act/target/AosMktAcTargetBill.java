package mkt.act.target;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndDate;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import common.fnd.FndWebHook;
import kd.bos.algo.DataSet;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.datamodel.events.ChangeData;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
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
 *
 */
public class AosMktAcTargetBill extends AbstractBillPlugIn {
	/** 系统管理员 **/
	public final static String system = Cux_Common_Utl.SYSTEM;

	private static String NUMBERFORMAT = "^(-?[1-9]\\d*\\.?\\d*)|(-?0\\.\\d*[1-9])|(-?[0])|(-?[0]\\.\\d*)$";

	/**
	 * 进入界面后触发
	 */
	public void afterCreateNewData(EventObject e) {
		initEntity();
	}

	/** 初始化事件 **/
	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		StatusControl();
	}

	/** 全局状态控制 **/
	private void StatusControl() {
		Object billStatus = this.getModel().getValue("billstatus");
		if ("A".equals(billStatus)) {
			this.getView().setVisible(true, "aos_cal");
			this.getView().setVisible(true, "aos_get");
		} else if ("B".equals(billStatus)) {
			this.getView().setVisible(false, "aos_cal");
			this.getView().setVisible(false, "aos_get");
		}
	}

	/**
	 * 值改变事件
	 */
	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		ChangeData[] changeSet = e.getChangeSet();
		try {
			if (name.equals("aos_home") || name.equals("aos_rattan") || name.equals("aos_yard")
					|| name.equals("aos_sport") || name.equals("aos_pet") || name.equals("aos_kid")
					|| name.equals("aos_total")) {
				String newValue = (String) changeSet[0].getNewValue();
				lineRowChanged(name, newValue);
			} else if (name.equals("aos_amount_l") || name.equals("aos_rate_l") || name.equals("aos_home_l")
					|| name.equals("aos_rattan_l") || name.equals("aos_yard_l") || name.equals("aos_sport_l")
					|| name.equals("aos_pet_l") || name.equals("aos_kid_l")) {
				String newValue = (String) changeSet[0].getNewValue();
				detailRowChanged(name, newValue);
			} else if (name.equals("aos_rate")) {
				BigDecimal newValue = (BigDecimal) changeSet[0].getNewValue();
				aosRateChanged(newValue);
			}
		} catch (FndError fndError) {
			fndError.show(getView());
		} catch (Exception ex) {
			FndError.showex(getView(), ex);
		}
	}

	/**
	 * 活动营收占比值改变事件
	 * 
	 * @param newValue
	 */
	private void aosRateChanged(BigDecimal newValue) {
		FndMsg.debug("=====into aosRateChanged=====");
		FndMsg.debug("value:" + newValue);
		String aos_home = (String) this.getModel().getValue("aos_home", 7);
		String aos_rattan = (String) this.getModel().getValue("aos_rattan", 7);
		String aos_yard = (String) this.getModel().getValue("aos_yard", 7);
		String aos_sport = (String) this.getModel().getValue("aos_sport", 7);
		String aos_pet = (String) this.getModel().getValue("aos_pet", 7);
		String aos_kid = (String) this.getModel().getValue("aos_kid", 7);
		String aos_total = (String) this.getModel().getValue("aos_total", 7);

		BigDecimal aosHomeBd = BigDecimal.ZERO;
		if (FndGlobal.IsNotNull(aos_home)) {
			aosHomeBd = new BigDecimal(aos_home);
			this.getModel().setValue("aos_home", aosHomeBd.multiply(newValue).setScale(0, BigDecimal.ROUND_HALF_UP), 1);
		}
		BigDecimal aosRattanBd = BigDecimal.ZERO;
		if (FndGlobal.IsNotNull(aos_rattan)) {
			aosRattanBd = new BigDecimal(aos_rattan);
			this.getModel().setValue("aos_rattan", aosRattanBd.multiply(newValue).setScale(0, BigDecimal.ROUND_HALF_UP),
					1);
		}
		BigDecimal aosYardBd = BigDecimal.ZERO;
		if (FndGlobal.IsNotNull(aos_yard)) {
			aosYardBd = new BigDecimal(aos_yard);
			this.getModel().setValue("aos_yard", aosYardBd.multiply(newValue).setScale(0, BigDecimal.ROUND_HALF_UP), 1);
		}
		BigDecimal aosSportBd = BigDecimal.ZERO;
		if (FndGlobal.IsNotNull(aos_sport)) {
			aosSportBd = new BigDecimal(aos_sport);
			this.getModel().setValue("aos_sport", aosSportBd.multiply(newValue).setScale(0, BigDecimal.ROUND_HALF_UP),
					1);
		}
		BigDecimal aosPetBd = BigDecimal.ZERO;
		if (FndGlobal.IsNotNull(aos_pet)) {
			aosPetBd = new BigDecimal(aos_pet);
			this.getModel().setValue("aos_pet", aosPetBd.multiply(newValue).setScale(0, BigDecimal.ROUND_HALF_UP), 1);
		}
		BigDecimal aosKidBd = BigDecimal.ZERO;
		if (FndGlobal.IsNotNull(aos_kid)) {
			aosKidBd = new BigDecimal(aos_kid);
			this.getModel().setValue("aos_kid", aosKidBd.multiply(newValue).setScale(0, BigDecimal.ROUND_HALF_UP), 1);
		}
		BigDecimal aosTotalBd = BigDecimal.ZERO;
		if (FndGlobal.IsNotNull(aos_total)) {
			aosTotalBd = new BigDecimal(aos_total);
			this.getModel().setValue("aos_total", aosTotalBd.multiply(newValue).setScale(0, BigDecimal.ROUND_HALF_UP),
					1);
		}
		this.getView().invokeOperation("save");
	}

	/**
	 * 明细值改变事件
	 * 
	 * @param name
	 * @param newValue
	 */
	private void detailRowChanged(String name, String value) throws FndError {
		int currentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_entryentity1");
		if (FndGlobal.IsNotNull(value) && !value.matches(NUMBERFORMAT)) {
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
				name2Bd = name1Bd.subtract(name0Bd).divide(name1Bd, 2, BigDecimal.ROUND_HALF_UP);
				this.getModel().setValue(name, String.valueOf(name2Bd), 2);
				this.getView().invokeOperation("save");
			}
		}
	}

	/**
	 * 行值改变事件
	 * 
	 * @param value
	 * 
	 * @param object
	 */
	private void lineRowChanged(String name, String value) throws FndError {
		int currentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_entryentity");
		// 合计汇总
		if (FndGlobal.IsNotNull(value) && !value.matches(NUMBERFORMAT)) {
			this.getModel().setValue(name, null, currentRowIndex);
			throw new FndError("字段格式错误");
		}
		if (currentRowIndex == 0) {
			// 计算合计
			String aos_home = (String) this.getModel().getValue("aos_home", currentRowIndex);
			String aos_rattan = (String) this.getModel().getValue("aos_rattan", currentRowIndex);
			String aos_yard = (String) this.getModel().getValue("aos_yard", currentRowIndex);
			String aos_sport = (String) this.getModel().getValue("aos_sport", currentRowIndex);
			String aos_pet = (String) this.getModel().getValue("aos_pet", currentRowIndex);
			String aos_kid = (String) this.getModel().getValue("aos_kid", currentRowIndex);
			BigDecimal aosHomeBd = BigDecimal.ZERO;
			if (FndGlobal.IsNotNull(aos_home))
				aosHomeBd = new BigDecimal(aos_home);
			BigDecimal aosRattanBd = BigDecimal.ZERO;
			if (FndGlobal.IsNotNull(aos_rattan))
				aosRattanBd = new BigDecimal(aos_rattan);
			BigDecimal aosYardBd = BigDecimal.ZERO;
			if (FndGlobal.IsNotNull(aos_yard))
				aosYardBd = new BigDecimal(aos_yard);
			BigDecimal aosSportBd = BigDecimal.ZERO;
			if (FndGlobal.IsNotNull(aos_sport))
				aosSportBd = new BigDecimal(aos_sport);
			BigDecimal aosPetBd = BigDecimal.ZERO;
			if (FndGlobal.IsNotNull(aos_pet))
				aosPetBd = new BigDecimal(aos_pet);
			BigDecimal aosKidBd = BigDecimal.ZERO;
			if (FndGlobal.IsNotNull(aos_kid))
				aosKidBd = new BigDecimal(aos_kid);
			BigDecimal aosTotalBd = aosHomeBd.add(aosRattanBd).add(aosYardBd).add(aosSportBd).add(aosPetBd)
					.add(aosKidBd);
			this.getModel().setValue("aos_total", String.valueOf(aosTotalBd), 0);
			this.getView().invokeOperation("save");
		} else if (currentRowIndex == 7) {
			Object name7 = this.getModel().getValue(name, currentRowIndex);
			Object aosRate = this.getModel().getValue("aos_rate");
			if (FndGlobal.IsNotNull(name7) && FndGlobal.IsNotNull(aosRate)) {
				BigDecimal name7Bd = new BigDecimal(name7.toString());
				BigDecimal aosRateBd = (BigDecimal) aosRate;
				this.getModel().setValue(name, aosRateBd.multiply(name7Bd).setScale(0, BigDecimal.ROUND_HALF_UP), 1);
				this.getView().invokeOperation("save");
			}

			// 计算活动计划占比
			Object name2 = this.getModel().getValue(name, 2);
			if (FndGlobal.IsNotNull(name7) && FndGlobal.IsNotNull(name2)) {
				BigDecimal name7Bd = new BigDecimal(name7.toString());
				BigDecimal name2Bd = new BigDecimal(name2.toString());
				if (name7Bd.compareTo(BigDecimal.ZERO) != 0)
					this.getModel().setValue(name, name2Bd.divide(name7Bd, 2, BigDecimal.ROUND_HALF_UP), 5);
				this.getView().invokeOperation("save");
			}
		}

		// 无论是0 还是7的修改都需要 计算本月目标
		Object name0 = this.getModel().getValue(name, 0);
		Object name7 = this.getModel().getValue(name, 7);
		if (FndGlobal.IsNotNull(name0) && FndGlobal.IsNotNull(name7)) {
			BigDecimal name0Bd = new BigDecimal(name0.toString());
			BigDecimal name7Bd = new BigDecimal(name7.toString());
			BigDecimal name4Bd = BigDecimal.ZERO;
			if (name7Bd.compareTo(BigDecimal.ZERO) != 0) {
				name4Bd = name0Bd.divide(name7Bd, 2, BigDecimal.ROUND_HALF_UP);
				this.getModel().setValue(name, String.valueOf(name4Bd), 4);
				this.getView().invokeOperation("save");
			}
		}
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		try {
			if ("aos_get".equals(Control))
				aos_get();// 认领
			else if ("aos_reget".equals(Control))
				aos_reget();// 取消认领
			else if ("aos_cal".equals(Control))
				aos_cal();// 计算营收
		} catch (FndError fndError) {
			fndError.show(getView());
		} catch (Exception ex) {
			FndError.showex(getView(), ex);
		}
	}

	/**
	 * 计算营收
	 */
	private void aos_cal() {
		// 计算活动计划
		calActivityPlan();
		// 计算上月实际
		calLastMonthActual();
		// 保存计算的值
		this.getView().invokeOperation("save");
	}

	/**
	 * 计算活动计划
	 */
	private void calLastMonthActual() {
		// 数据层
		Date aos_month = (Date) this.getModel().getValue("aos_month");
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(aos_month);
		calendar.set(Calendar.DAY_OF_MONTH, 1);
		Date fromDate = calendar.getTime();// 开始日期
		calendar.add(Calendar.MONTH, 1);
		Date toDate = calendar.getTime();// 结束日期
		calendar.set(Calendar.DAY_OF_MONTH, 1);
		Object fid = this.getView().getModel().getDataEntity().getPkValue();
		DynamicObject aos_orgid = (DynamicObject) this.getModel().getValue("aos_orgid");
		String aosOrgIdStr = aos_orgid.getPkValue().toString();
		// 活动库
		Set<String> aosSalActLibS = getSalActLib(aosOrgIdStr);
		// 查询活动与选品表
		Map<String, BigDecimal> cateAmtMap = new HashMap<>();
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
			int totalDate = FndDate.GetBetweenDays(endDate, startDate);// 总天数
			int areaDate = FndDate.GetBetweenDays(endDate, fromDate);// 范围中的天数
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
			int totalDate = FndDate.GetBetweenDays(endDate, startDate);// 总天数
			int areaDate = FndDate.GetBetweenDays(toDate, startDate);// 范围中的天数
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
			int totalDate = FndDate.GetBetweenDays(endDate, startDate);// 总天数
			int areaDate = FndDate.GetBetweenDays(toDate, fromDate);// 范围中的天数
			increRevenBd = increRevenBd.add(aosActSelectPlanSec4.getBigDecimal("aos_incre_reven")
					.multiply(BigDecimal.valueOf(areaDate / totalDate)));
			cateAmtMap.put(aosCateGoryStat1, increRevenBd);
		}

		BigDecimal aosHome2Bd = cateAmtMap.getOrDefault("居家系列", BigDecimal.ZERO);
		BigDecimal aosRattan2Bd = cateAmtMap.getOrDefault("藤编产品", BigDecimal.ZERO);
		BigDecimal aosYard2Bd = cateAmtMap.getOrDefault("庭院&花园", BigDecimal.ZERO);
		BigDecimal aosSport2Bd = cateAmtMap.getOrDefault("运动&娱乐&汽配", BigDecimal.ZERO);
		BigDecimal aosPet2Bd = cateAmtMap.getOrDefault("宠物用品", BigDecimal.ZERO);
		BigDecimal aosKid2Bd = cateAmtMap.getOrDefault("婴儿产品&玩具及游戏", BigDecimal.ZERO);
		BigDecimal aosTotal2Bd = aosHome2Bd.add(aosRattan2Bd).add(aosYard2Bd).add(aosSport2Bd).add(aosPet2Bd)
				.add(aosKid2Bd);
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
		if (FndGlobal.IsNotNull(aosTotal7))
			aosTotal7Bd = new BigDecimal(aosTotal7.toString());
		Object aosHome7 = this.getModel().getValue("aos_home", 7);
		if (FndGlobal.IsNotNull(aosHome7))
			aosHome7Bd = new BigDecimal(aosHome7.toString());
		Object aosRattan7 = this.getModel().getValue("aos_rattan", 7);
		if (FndGlobal.IsNotNull(aosRattan7))
			aosRattan7Bd = new BigDecimal(aosRattan7.toString());
		Object aosYard7 = this.getModel().getValue("aos_yard", 7);
		if (FndGlobal.IsNotNull(aosYard7))
			aosYard7Bd = new BigDecimal(aosYard7.toString());
		Object aosSport7 = this.getModel().getValue("aos_sport", 7);
		if (FndGlobal.IsNotNull(aosSport7))
			aosSport7Bd = new BigDecimal(aosSport7.toString());
		Object aosPet7 = this.getModel().getValue("aos_pet", 7);
		if (FndGlobal.IsNotNull(aosPet7))
			aosPet7Bd = new BigDecimal(aosPet7.toString());
		Object aosKid7 = this.getModel().getValue("aos_kid", 7);
		if (FndGlobal.IsNotNull(aosKid7))
			aosKid7Bd = new BigDecimal(aosKid7.toString());

		if (aosTotal7Bd.compareTo(BigDecimal.ZERO) != 0)
			this.getModel().setValue("aos_total", aosTotal2Bd.divide(aosTotal7Bd, 2, BigDecimal.ROUND_HALF_UP), 5);
		if (aosHome7Bd.compareTo(BigDecimal.ZERO) != 0)
			this.getModel().setValue("aos_home", aosHome2Bd.divide(aosHome7Bd, 2, BigDecimal.ROUND_HALF_UP), 5);
		if (aosRattan7Bd.compareTo(BigDecimal.ZERO) != 0)
			this.getModel().setValue("aos_rattan", aosRattan2Bd.divide(aosRattan7Bd, 2, BigDecimal.ROUND_HALF_UP), 5);
		if (aosYard7Bd.compareTo(BigDecimal.ZERO) != 0)
			this.getModel().setValue("aos_yard", aosYard2Bd.divide(aosYard7Bd, 2, BigDecimal.ROUND_HALF_UP), 5);
		if (aosSport7Bd.compareTo(BigDecimal.ZERO) != 0)
			this.getModel().setValue("aos_sport", aosSport2Bd.divide(aosSport7Bd, 2, BigDecimal.ROUND_HALF_UP), 5);
		if (aosPet7Bd.compareTo(BigDecimal.ZERO) != 0)
			this.getModel().setValue("aos_pet", aosPet2Bd.divide(aosPet7Bd, 2, BigDecimal.ROUND_HALF_UP), 5);
		if (aosKid7Bd.compareTo(BigDecimal.ZERO) != 0)
			this.getModel().setValue("aos_kid", aosKid2Bd.divide(aosKid7Bd, 2, BigDecimal.ROUND_HALF_UP), 5);
	}

	private Set<String> getSalActLib(String aosOrgIdStr) {
		DynamicObjectCollection aosSalActLib = QueryServiceHelper.query("aos_sal_act_type_p",
				"aos_acttype.id aos_acttypeid", new QFilter("aos_assessment.number", QCP.equals, "Y")
						.and("aos_org.id", QCP.equals, aosOrgIdStr).toArray(),
				null);
		return aosSalActLib.stream().map(obj -> obj.getString("aos_acttypeid")).collect(Collectors.toSet());
	}

	/**
	 * 计算上月实际
	 */
	private void calActivityPlan() {
		// 判断当前月份
		Date aos_month = (Date) this.getModel().getValue("aos_month");
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(aos_month);
		int month = calendar.get(Calendar.MONTH);
		calendar.add(Calendar.MONTH, -12);
		calendar.set(Calendar.DAY_OF_MONTH, 1);
		Date lastFromDate = calendar.getTime();
		calendar.add(Calendar.MONTH, 1);
		Date lastToDate = calendar.getTime();
		calendar.set(Calendar.DAY_OF_MONTH, 1);
		Calendar calendar2 = Calendar.getInstance();
		calendar2.setTime(aos_month);
		calendar.set(Calendar.DAY_OF_MONTH, 1);
		Date toDate = calendar.getTime();// 开始日期
		calendar.add(Calendar.MONTH, -6);
		Date fromDate = calendar.getTime();// 开始日期
		DynamicObject aos_orgid = (DynamicObject) this.getModel().getValue("aos_orgid");
		String aosOrgIdStr = aos_orgid.getPkValue().toString();
		Set<String> aosSalActLibS = getSalActLib(aosOrgIdStr); // 活动类型

		// 对于庭院 藤编 4-8月
		BigDecimal aosYard3Bd = BigDecimal.ZERO; // 庭院
		BigDecimal aosRattan3Bd = BigDecimal.ZERO; // 藤编
		BigDecimal aosHome3Bd = BigDecimal.ZERO; // 居家
		BigDecimal aosSport3Bd = BigDecimal.ZERO;// 运动
		BigDecimal aosPet3Bd = BigDecimal.ZERO;// 宠物
		BigDecimal aosKid3Bd = BigDecimal.ZERO;// 婴童
		BigDecimal aosTotal3Bd = BigDecimal.ZERO;// 合计

		if (month == Calendar.AUGUST || month == Calendar.JULY || month == Calendar.JUNE || month == Calendar.MAY
				|| month == Calendar.APRIL) {
			// 1.庭院
			DynamicObjectCollection aosActSelectYardS = QueryServiceHelper.query("aos_act_select_plan",
					"aos_sal_actplanentity.aos_incre_reven aos_incre_reven",
					new QFilter("aos_nationality.id", QCP.equals, aosOrgIdStr)
							.and("aos_planentity.aos_yard_plan", QCP.equals, "Y")
							.and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, "庭院&花园")
							.and("aos_enddate1", QCP.large_equals, lastFromDate)
							.and("aos_enddate1", QCP.less_equals, lastToDate).and("aos_acttype", QCP.in, aosSalActLibS)
							.toArray());
			for (DynamicObject aosActSelectYard : aosActSelectYardS) {
				aosYard3Bd = aosYard3Bd.add(aosActSelectYard.getBigDecimal("aos_incre_reven"));
			}
			// 2.藤编
			DynamicObjectCollection aosActSelectRattanS = QueryServiceHelper.query("aos_act_select_plan",
					"aos_sal_actplanentity.aos_incre_reven aos_incre_reven",
					new QFilter("aos_nationality.id", QCP.equals, aosOrgIdStr)
							.and("aos_planentity.aos_rattan_plan", QCP.equals, "Y")
							.and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, "藤编产品")
							.and("aos_enddate1", QCP.large_equals, lastFromDate)
							.and("aos_enddate1", QCP.less_equals, lastToDate).and("aos_acttype", QCP.in, aosSalActLibS)
							.toArray());
			for (DynamicObject aosActSelectRattan : aosActSelectRattanS) {
				aosRattan3Bd = aosRattan3Bd.add(aosActSelectRattan.getBigDecimal("aos_incre_reven"));
			}
		} else {
			// 循环 活动类型
			for (String aosSalActLib : aosSalActLibS) {
				// 1.庭院
				BigDecimal aosYard3SingleBd = BigDecimal.ZERO;
				DynamicObjectCollection aosActSelectYardS = QueryServiceHelper.query("aos_act_select_plan",
						"aos_sal_actplanentity.aos_incre_reven aos_incre_reven",
						new QFilter("aos_nationality.id", QCP.equals, aosOrgIdStr)
								.and("aos_acttype.id", QCP.equals, aosSalActLib)
								.and("aos_planentity.aos_yard_plan", QCP.equals, "Y")
								.and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, "庭院&花园")
								.and("aos_enddate1", QCP.large_equals, toDate)
								.and("aos_enddate1", QCP.less_equals, fromDate).toArray());
				for (DynamicObject aosActSelectYard : aosActSelectYardS) {
					aosYard3SingleBd = aosYard3SingleBd.add(aosActSelectYard.getBigDecimal("aos_incre_reven"));
				}
				DynamicObjectCollection countYardS = QueryServiceHelper.query("aos_act_select_plan", "id",
						new QFilter("aos_nationality.id", QCP.equals, aosOrgIdStr)
								.and("aos_acttype.id", QCP.equals, aosSalActLib)
								.and("aos_planentity.aos_yard_plan", QCP.equals, "Y")
								.and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, "庭院&花园")
								.and("aos_enddate1", QCP.large_equals, toDate)
								.and("aos_enddate1", QCP.less_equals, fromDate).toArray());
				int countYard = 0;
				if (countYardS.size() > 0)
					countYard = countYardS.stream().map(obj -> obj.getString("id")).distinct()
							.collect(Collectors.toSet()).size();
				if (countYard > 0) {
					aosYard3SingleBd = aosYard3SingleBd.divide(BigDecimal.valueOf(countYard), 2,
							BigDecimal.ROUND_HALF_UP);
				}
				aosYard3Bd = aosYard3Bd.add(aosYard3SingleBd);

				// 2.藤编
				BigDecimal aosRattan3SingleBd = BigDecimal.ZERO;
				DynamicObjectCollection aosActSelectRattanS = QueryServiceHelper.query("aos_act_select_plan",
						"aos_sal_actplanentity.aos_incre_reven aos_incre_reven",
						new QFilter("aos_nationality.id", QCP.equals, aosOrgIdStr)
								.and("aos_acttype.id", QCP.equals, aosSalActLib)
								.and("aos_planentity.aos_rattan_plan", QCP.equals, "Y")
								.and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, "藤编产品")
								.and("aos_enddate1", QCP.large_equals, toDate)
								.and("aos_enddate1", QCP.less_equals, fromDate).toArray());
				for (DynamicObject aosActSelectRattan : aosActSelectRattanS) {
					aosRattan3SingleBd = aosRattan3SingleBd.add(aosActSelectRattan.getBigDecimal("aos_incre_reven"));
				}
				DynamicObjectCollection countRattanS = QueryServiceHelper.query("aos_act_select_plan", "id",
						new QFilter("aos_nationality.id", QCP.equals, aosOrgIdStr)
								.and("aos_acttype.id", QCP.equals, aosSalActLib)
								.and("aos_planentity.aos_rattan_plan", QCP.equals, "Y")
								.and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, "藤编产品")
								.and("aos_enddate1", QCP.large_equals, toDate)
								.and("aos_enddate1", QCP.less_equals, fromDate).toArray());
				int countRattan = 0;
				if (countRattanS.size() > 0)
					countRattan = countRattanS.stream().map(obj -> obj.getString("id")).distinct()
							.collect(Collectors.toSet()).size();
				if (countRattan > 0) {
					aosRattan3SingleBd = aosRattan3SingleBd.divide(BigDecimal.valueOf(countRattan), 2,
							BigDecimal.ROUND_HALF_UP);
				}
				aosRattan3Bd = aosRattan3Bd.add(aosRattan3SingleBd);

				// 3.居家
				BigDecimal aosHome3SingleBd = BigDecimal.ZERO;
				DynamicObjectCollection aosActSelectHomeS = QueryServiceHelper.query("aos_act_select_plan",
						"aos_sal_actplanentity.aos_incre_reven aos_incre_reven",
						new QFilter("aos_nationality.id", QCP.equals, aosOrgIdStr)
								.and("aos_acttype.id", QCP.equals, aosSalActLib)
								.and("aos_planentity.aos_home_plan", QCP.equals, "Y")
								.and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, "居家系列")
								.and("aos_enddate1", QCP.large_equals, toDate)
								.and("aos_enddate1", QCP.less_equals, fromDate).toArray());
				for (DynamicObject aosActSelectHome : aosActSelectHomeS) {
					aosHome3SingleBd = aosHome3SingleBd.add(aosActSelectHome.getBigDecimal("aos_incre_reven"));
				}
				DynamicObjectCollection countHomeS = QueryServiceHelper.query("aos_act_select_plan", "id",
						new QFilter("aos_nationality.id", QCP.equals, aosOrgIdStr)
								.and("aos_acttype.id", QCP.equals, aosSalActLib)
								.and("aos_planentity.aos_home_plan", QCP.equals, "Y")
								.and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, "居家系列")
								.and("aos_enddate1", QCP.large_equals, toDate)
								.and("aos_enddate1", QCP.less_equals, fromDate).toArray());
				int countHome = 0;
				if (countHomeS.size() > 0)
					countHome = countHomeS.stream().map(obj -> obj.getString("id")).distinct()
							.collect(Collectors.toSet()).size();
				if (countHome > 0) {
					aosHome3SingleBd = aosHome3SingleBd.divide(BigDecimal.valueOf(countHome), 2,
							BigDecimal.ROUND_HALF_UP);
				}
				aosHome3Bd = aosHome3Bd.add(aosHome3SingleBd);

				// 4.运动
				BigDecimal aosSport3SingleBd = BigDecimal.ZERO;
				DynamicObjectCollection aosActSelectSportS = QueryServiceHelper.query("aos_act_select_plan",
						"aos_sal_actplanentity.aos_incre_reven aos_incre_reven",
						new QFilter("aos_nationality.id", QCP.equals, aosOrgIdStr)
								.and("aos_acttype.id", QCP.equals, aosSalActLib)
								.and("aos_planentity.aos_sport_plan", QCP.equals, "Y")
								.and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, "运动&娱乐&汽配")
								.and("aos_enddate1", QCP.large_equals, toDate)
								.and("aos_enddate1", QCP.less_equals, fromDate).toArray());
				for (DynamicObject aosActSelectSport : aosActSelectSportS) {
					aosSport3SingleBd = aosSport3SingleBd.add(aosActSelectSport.getBigDecimal("aos_incre_reven"));
				}
				DynamicObjectCollection countSportS = QueryServiceHelper.query("aos_act_select_plan", "id",
						new QFilter("aos_nationality.id", QCP.equals, aosOrgIdStr)
								.and("aos_acttype.id", QCP.equals, aosSalActLib)
								.and("aos_planentity.aos_sport_plan", QCP.equals, "Y")
								.and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, "运动&娱乐&汽配")
								.and("aos_enddate1", QCP.large_equals, toDate)
								.and("aos_enddate1", QCP.less_equals, fromDate).toArray());
				int countSport = 0;
				if (countSportS.size() > 0)
					countSport = countSportS.stream().map(obj -> obj.getString("id")).distinct()
							.collect(Collectors.toSet()).size();
				if (countSport > 0) {
					aosSport3SingleBd = aosSport3SingleBd.divide(BigDecimal.valueOf(countSport), 2,
							BigDecimal.ROUND_HALF_UP);
				}
				aosSport3Bd = aosSport3Bd.add(aosSport3SingleBd);

				// 5.宠物
				BigDecimal aosPet3SingleBd = BigDecimal.ZERO;
				DynamicObjectCollection aosActSelectPetS = QueryServiceHelper.query("aos_act_select_plan",
						"aos_sal_actplanentity.aos_incre_reven aos_incre_reven",
						new QFilter("aos_nationality.id", QCP.equals, aosOrgIdStr)
								.and("aos_acttype.id", QCP.equals, aosSalActLib)
								.and("aos_planentity.aos_pet_plan", QCP.equals, "Y")
								.and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, "宠物用品")
								.and("aos_enddate1", QCP.large_equals, toDate)
								.and("aos_enddate1", QCP.less_equals, fromDate).toArray());
				for (DynamicObject aosActSelectPet : aosActSelectPetS) {
					aosPet3SingleBd = aosPet3SingleBd.add(aosActSelectPet.getBigDecimal("aos_incre_reven"));
				}
				DynamicObjectCollection countPetS = QueryServiceHelper.query("aos_act_select_plan", "id",
						new QFilter("aos_nationality.id", QCP.equals, aosOrgIdStr)
								.and("aos_acttype.id", QCP.equals, aosSalActLib)
								.and("aos_planentity.aos_pet_plan", QCP.equals, "Y")
								.and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, "宠物用品")
								.and("aos_enddate1", QCP.large_equals, toDate)
								.and("aos_enddate1", QCP.less_equals, fromDate).toArray());
				int countPet = 0;
				if (countPetS.size() > 0)
					countPet = countPetS.stream().map(obj -> obj.getString("id")).distinct().collect(Collectors.toSet())
							.size();
				if (countPet > 0) {
					aosPet3SingleBd = aosPet3SingleBd.divide(BigDecimal.valueOf(countPet), 2, BigDecimal.ROUND_HALF_UP);
				}
				aosPet3Bd = aosPet3Bd.add(aosPet3SingleBd);

				// 6.婴童
				BigDecimal aosKid3SingleBd = BigDecimal.ZERO;
				DynamicObjectCollection aosActSelectKidS = QueryServiceHelper.query("aos_act_select_plan",
						"aos_sal_actplanentity.aos_incre_reven aos_incre_reven",
						new QFilter("aos_nationality.id", QCP.equals, aosOrgIdStr)
								.and("aos_acttype.id", QCP.equals, aosSalActLib)
								.and("aos_planentity.aos_baby_plan", QCP.equals, "Y")
								.and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, "婴儿产品&玩具及游戏")
								.and("aos_enddate1", QCP.large_equals, toDate)
								.and("aos_enddate1", QCP.less_equals, fromDate).toArray());
				for (DynamicObject aosActSelectKid : aosActSelectKidS) {
					aosKid3SingleBd = aosKid3SingleBd.add(aosActSelectKid.getBigDecimal("aos_incre_reven"));
				}
				DynamicObjectCollection countKidS = QueryServiceHelper.query("aos_act_select_plan", "id",
						new QFilter("aos_nationality.id", QCP.equals, aosOrgIdStr)
								.and("aos_acttype.id", QCP.equals, aosSalActLib)
								.and("aos_planentity.aos_baby_plan", QCP.equals, "Y")
								.and("aos_sal_actplanentity.aos_category_stat1", QCP.equals, "婴儿产品&玩具及游戏")
								.and("aos_enddate1", QCP.large_equals, toDate)
								.and("aos_enddate1", QCP.less_equals, fromDate).toArray());
				int countKid = 0;
				if (countKidS.size() > 0)
					countKid = countKidS.stream().map(obj -> obj.getString("id")).distinct().collect(Collectors.toSet())
							.size();
				if (countKid > 0) {
					aosKid3SingleBd = aosKid3SingleBd.divide(BigDecimal.valueOf(countKid), 2, BigDecimal.ROUND_HALF_UP);
				}
				aosKid3Bd = aosKid3Bd.add(aosKid3SingleBd);
			}
		}

		aosTotal3Bd = aosHome3Bd.add(aosRattan3Bd).add(aosYard3Bd).add(aosSport3Bd).add(aosPet3Bd).add(aosKid3Bd);
		this.getModel().setValue("aos_home", String.valueOf(aosHome3Bd), 3);
		this.getModel().setValue("aos_rattan", String.valueOf(aosRattan3Bd), 3);
		this.getModel().setValue("aos_yard", String.valueOf(aosYard3Bd), 3);
		this.getModel().setValue("aos_sport", String.valueOf(aosSport3Bd), 3);
		this.getModel().setValue("aos_pet", String.valueOf(aosPet3Bd), 3);
		this.getModel().setValue("aos_kid", String.valueOf(aosKid3Bd), 3);
		this.getModel().setValue("aos_total", String.valueOf(aosTotal3Bd), 3);
		// 计算上月实际占比
		BigDecimal aosTotal7Bd = BigDecimal.ZERO;
		BigDecimal aosHome7Bd = BigDecimal.ZERO;
		BigDecimal aosRattan7Bd = BigDecimal.ZERO;
		BigDecimal aosYard7Bd = BigDecimal.ZERO;
		BigDecimal aosSport7Bd = BigDecimal.ZERO;
		BigDecimal aosPet7Bd = BigDecimal.ZERO;
		BigDecimal aosKid7Bd = BigDecimal.ZERO;
		Object aosTotal7 = this.getModel().getValue("aos_total", 7);
		if (FndGlobal.IsNotNull(aosTotal7))
			aosTotal7Bd = new BigDecimal(aosTotal7.toString());
		Object aosHome7 = this.getModel().getValue("aos_home", 7);
		if (FndGlobal.IsNotNull(aosHome7))
			aosHome7Bd = new BigDecimal(aosHome7.toString());
		Object aosRattan7 = this.getModel().getValue("aos_rattan", 7);
		if (FndGlobal.IsNotNull(aosRattan7))
			aosRattan7Bd = new BigDecimal(aosRattan7.toString());
		Object aosYard7 = this.getModel().getValue("aos_yard", 7);
		if (FndGlobal.IsNotNull(aosYard7))
			aosYard7Bd = new BigDecimal(aosYard7.toString());
		Object aosSport7 = this.getModel().getValue("aos_sport", 7);
		if (FndGlobal.IsNotNull(aosSport7))
			aosSport7Bd = new BigDecimal(aosSport7.toString());
		Object aosPet7 = this.getModel().getValue("aos_pet", 7);
		if (FndGlobal.IsNotNull(aosPet7))
			aosPet7Bd = new BigDecimal(aosPet7.toString());
		Object aosKid7 = this.getModel().getValue("aos_kid", 7);
		if (FndGlobal.IsNotNull(aosKid7))
			aosKid7Bd = new BigDecimal(aosKid7.toString());

		if (aosTotal7Bd.compareTo(BigDecimal.ZERO) != 0)
			this.getModel().setValue("aos_total", aosTotal3Bd.divide(aosTotal7Bd, 2, BigDecimal.ROUND_HALF_UP), 6);
		if (aosHome7Bd.compareTo(BigDecimal.ZERO) != 0)
			this.getModel().setValue("aos_home", aosHome3Bd.divide(aosHome7Bd, 2, BigDecimal.ROUND_HALF_UP), 6);
		if (aosRattan7Bd.compareTo(BigDecimal.ZERO) != 0)
			this.getModel().setValue("aos_rattan", aosRattan3Bd.divide(aosRattan7Bd, 2, BigDecimal.ROUND_HALF_UP), 6);
		if (aosYard7Bd.compareTo(BigDecimal.ZERO) != 0)
			this.getModel().setValue("aos_yard", aosYard3Bd.divide(aosYard7Bd, 2, BigDecimal.ROUND_HALF_UP), 6);
		if (aosSport7Bd.compareTo(BigDecimal.ZERO) != 0)
			this.getModel().setValue("aos_sport", aosSport3Bd.divide(aosSport7Bd, 2, BigDecimal.ROUND_HALF_UP), 6);
		if (aosPet7Bd.compareTo(BigDecimal.ZERO) != 0)
			this.getModel().setValue("aos_pet", aosPet3Bd.divide(aosPet7Bd, 2, BigDecimal.ROUND_HALF_UP), 6);
		if (aosKid7Bd.compareTo(BigDecimal.ZERO) != 0)
			this.getModel().setValue("aos_kid", aosKid3Bd.divide(aosKid7Bd, 2, BigDecimal.ROUND_HALF_UP), 6);
	}

	/**
	 * 取消认领
	 */
	private void aos_reget() {
		this.getModel().setValue("aos_user", system);
		this.getView().invokeOperation("save");
		this.getView().showSuccessNotification("取消认领成功!");
	}

	/**
	 * 认领
	 */
	private void aos_get() {
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
		this.getModel().batchCreateNewEntryRow("aos_entryentity", 8);
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
		this.getModel().deleteEntryData("aos_entryentity1");
		this.getModel().batchCreateNewEntryRow("aos_entryentity1", 3);
		this.getModel().setValue("aos_project_l", "上月", 0);
		this.getModel().setValue("aos_project_l", "本月", 1);
		this.getModel().setValue("aos_project_l", "环比", 2);
		this.getView().invokeOperation("save");
	}

	/**
	 * 在操作之后
	 */
	public void afterDoOperation(AfterDoOperationEventArgs args) {
		FormOperate formOperate = (FormOperate) args.getSource();
		String Operatation = formOperate.getOperateKey();
		try {
			if ("save".equals(Operatation))
				args.getOperationResult().setShowMessage(false);
			StatusControl();
		} catch (Exception ex) {
		}
	}

	/**
	 * 在操作之前
	 */
	public void beforeDoOperation(BeforeDoOperationEventArgs args) {
		FormOperate formOperate = (FormOperate) args.getSource();
		String Operatation = formOperate.getOperateKey();
		try {
			if ("submit".equals(Operatation)) {
				DynamicObject aos_user = (DynamicObject) this.getModel().getValue("aos_user");
				long user = UserServiceHelper.getCurrentUserId();
				if (!(user + "").equals(aos_user.getPkValue().toString()))
					throw new FndError("非本人提交,提交失败!");
				// 校验是否已存在数据
				Object fid = this.getView().getModel().getDataEntity().getPkValue();
				DynamicObject aos_orgid = (DynamicObject) this.getModel().getValue("aos_orgid");
				Date aos_month = (Date) this.getModel().getValue("aos_month");
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(aos_month);
				calendar.set(Calendar.DAY_OF_MONTH, 1);
				Date fromDate = calendar.getTime();
				calendar.add(Calendar.MONTH, 1);
				Date toDate = calendar.getTime();
				calendar.set(Calendar.DAY_OF_MONTH, 1);
				DynamicObjectCollection aos_mkt_actarget = QueryServiceHelper.query("aos_mkt_actarget", "id",
						new QFilter("id", QCP.not_equals, fid).and("aos_orgid", QCP.equals, aos_orgid.getPkValue())
								.and("aos_month", QCP.large_equals, fromDate).and("aos_month", QCP.less_equals, toDate)
								.toArray());
				if (FndGlobal.IsNotNull(aos_mkt_actarget) && aos_mkt_actarget.size() > 0) {
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
