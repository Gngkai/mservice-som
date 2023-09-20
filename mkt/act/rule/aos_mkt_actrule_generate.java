package mkt.act.rule;

import common.Cux_Common_Utl;
import common.fnd.FndDate;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndMath;
import common.fnd.FndMsg;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ContainerOnShowEvent;
import kd.bos.form.control.events.ContainerOnShowListener;
import kd.bos.form.control.events.EntryFilterChangedEvent;
import kd.bos.form.control.events.EntryFilterChangedListener;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.fi.bd.util.QFBuilder;
import mkt.act.rule.allCountries.TrackerVipon;
import mkt.act.rule.de.DotdDE;
import mkt.act.rule.de.LDAnd7DDDE;
import mkt.act.rule.de.PocoDE;
import mkt.act.rule.de.WayfairDE;
import mkt.act.rule.dotd.GenerateData;
import mkt.act.rule.es.*;
import mkt.act.rule.uk.*;
import mkt.act.rule.us.DotdUS;
import mkt.act.rule.us.EbayDDAndPD;
import mkt.act.rule.us.LDAnd7DDUS;
import mkt.act.rule.us.TargetTheme;
import mkt.act.rule.us.TheHomeDepotUS;
import mkt.act.rule.us.WalmartDeal;
import mkt.act.rule.us.WalmartTheme;
import mkt.act.rule.us.WayfairCloseOutUS;
import mkt.act.rule.us.WayfairPro;

import java.math.BigDecimal;
import java.util.*;

public class aos_mkt_actrule_generate extends AbstractBillPlugIn
		implements ItemClickListener, RowClickEventListener, EntryFilterChangedListener, ContainerOnShowListener {
	public void registerListener(EventObject e) {
		// 给工具栏加监听事件
		this.addItemClickListeners("tbmain");
		this.addItemClickListeners("aos_generate"); // 生成销售数据

		this.addItemClickListeners("aos_toolbarap");
		this.addItemClickListeners("aos_batchquery");
		this.addItemClickListeners("aos_qtycal");

		EntryGrid entryGrid = this.getControl("aos_sal_actplanentity");
		entryGrid.addFilterChangedListener(this);
	}

	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		this.getModel().setValue("aos_itemid_mul", null);
	}

	public void beforeClosed(BeforeClosedEvent e) {
		super.beforeClosed(e);
		e.setCheckDataChange(false);
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		String Control = evt.getItemKey();
		try {

			if (Control.equals("aos_generate"))
				aos_generate();
			else if (Control.equals("aos_detailimp")) {
				genActPlan();// 明细导入
			} else if ("aos_batchquery".equals(Control)) {
				aos_batchquery();
			} else if ("aos_batchclose".equals(Control)) {
				aos_batchclose();
			} else if ("aos_batchopen".equals(Control)) {
				aos_batchopen();
			} else if ("aos_qtycal".equals(Control)) {
				// 从新批量设置活动数量
				batchSetActQty();
			}
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception e) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(e));
		}

	}

	/** 批量打开 **/
	private void aos_batchopen() throws FndError {
		// 数据层
		int ErrorCount = 0;
		String ErrorMessage = "";
		EntryGrid entryGrid = this.getControl("aos_sal_actplanentity");
		int[] SelectRow = entryGrid.getSelectRows();

		// 校验
		if (SelectRow.length == 0) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "未勾选行！");
		}

		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		for (int i = 0; i < SelectRow.length; i++) {
			this.getModel().setValue("aos_l_actstatus", "A", SelectRow[i]);
		}

		this.getView().invokeOperation("save");

		aos_batchquery();
	}

	/** 批量关闭 **/
	private void aos_batchclose() throws FndError {
		// 数据层
		int ErrorCount = 0;
		String ErrorMessage = "";
		EntryGrid entryGrid = this.getControl("aos_sal_actplanentity");
		int[] SelectRow = entryGrid.getSelectRows();

		// 校验
		if (SelectRow.length == 0) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "未勾选行！");
		}

		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		for (int i = 0; i < SelectRow.length; i++) {
			this.getModel().setValue("aos_l_actstatus", "B", SelectRow[i]);
		}

		// 如果行上全为已关闭 则将头状态改为已关闭
		Boolean closeFlag = true;
		DynamicObjectCollection aos_sal_actplanentityS = this.getModel().getEntryEntity("aos_sal_actplanentity");
		for (DynamicObject aos_sal_actplanentity : aos_sal_actplanentityS) {
			if ("A".equals(aos_sal_actplanentity.getString("aos_l_actstatus"))) {
				closeFlag = false;
				break;
			}
		}

		if (closeFlag) {
			this.getModel().setValue("aos_actstatus", "C");
		}

		this.getView().invokeOperation("save");
		aos_batchquery();
	}

	/** 批量筛选货号 **/
	private void aos_batchquery() throws FndError {
		Object aos_itemid_mul = this.getModel().getValue("aos_itemid_mul");
		if (!Cux_Common_Utl.IsNull(aos_itemid_mul)) {
			DynamicObjectCollection aos_itemidS = (DynamicObjectCollection) aos_itemid_mul;
			Set<String> itemSet = new HashSet<>();
			for (DynamicObject aos_itemid : aos_itemidS) {
				itemSet.add(aos_itemid.getDynamicObject("fbasedataid").getPkValue() + "");
			}
			if (itemSet != null) {
				DynamicObjectCollection aos_sal_actplanentityS = this.getModel()
						.getEntryEntity("aos_sal_actplanentity");
				EntryGrid entryGrid = this.getControl("aos_sal_actplanentity");
				int size = aos_sal_actplanentityS.size();
				List<Integer> list = new ArrayList<>();
				for (int i = 0; i < size; i++) {
					Object aos_itemnum = this.getModel().getValue("aos_itemnum", i);
					if (!Cux_Common_Utl.IsNull(aos_itemnum)) {
						if (itemSet.contains(((DynamicObject) aos_itemnum).getPkValue() + "")) {
							list.add(i);
						}
					}
				}
				int[] selectRow = new int[list.size()];
				for (int i = 0; i < list.size(); i++)
					selectRow[i] = list.get(i);
				entryGrid.selectRows(selectRow, 0);
				this.getModel().setEntryRowTop("aos_sal_actplanentity", selectRow);
			}
		}
	}

	private void aos_generate() {
		boolean aos_gensalconfirm = (boolean) this.getModel().getValue("aos_gensalconfirm");
		if (aos_gensalconfirm) {
			this.getView().showTipNotification("已生成销售确认单,请勿重复点击");
			return;
		}
		DynamicObject aos_act_select_plan = this.getModel().getDataEntity(true);
		GenerateData.genSaleActRule(aos_act_select_plan);
		this.getView().invokeOperation("refresh");
	}

	private void genActPlan() throws Exception {
		// 获取头信息关键字段
		Date aos_startdate = (Date) this.getModel().getValue("aos_startdate");
		Date aos_enddate1 = (Date) this.getModel().getValue("aos_enddate1");
		DynamicObject aos_nationality = (DynamicObject) this.getModel().getValue("aos_nationality");
		DynamicObject aos_acttype = (DynamicObject) this.getModel().getValue("aos_acttype");
		DynamicObject aos_shop = (DynamicObject) this.getModel().getValue("aos_shop");
		DynamicObject aos_channel = (DynamicObject) this.getModel().getValue("aos_channel");

		if (aos_startdate == null || aos_enddate1 == null || aos_nationality == null || aos_acttype == null
				|| aos_shop == null || aos_channel == null) {
			this.getView().showTipNotification("请填写基本信息中的必填字段!");
			return;
		}

		String actType = aos_acttype.getString("number");

		// 判断用户是否已保存
		long pkValue = (long) this.getModel().getDataEntity().getPkValue();
		if (pkValue == 0) {
			this.getView().showTipNotification("请先保存数据!");
			return;
		}
		// 获取国别+活动类型
		String ouCode = aos_nationality.getString("number");
		// 店铺
		String shop = aos_shop.getString("number");
		//230821 gk :活动明细导入根据用户配置规则筛选物料导入
		execute(aos_nationality,aos_channel,aos_shop,aos_acttype,getModel().getDataEntity(true));

		//execute(ouCode, shop, actType, this.getModel().getDataEntity());
		this.getView().invokeOperation("refresh");
		String noPriceItem = getNoPriceItem();
		if (noPriceItem.length() > 0) {
			this.getView().showMessage("以下物料缺失价格：  " + noPriceItem);
		}
		// 从新批量设置活动数量
		batchSetActQty();
	}
	private void execute(DynamicObject ou,DynamicObject channel ,DynamicObject shop, DynamicObject actType, DynamicObject actPlanEntity){
		QFBuilder builder = new QFBuilder();
		builder.add("aos_org","=",ou.getPkValue());
		builder.add("aos_channel","=",channel.getPkValue());
		builder.add("aos_shop","=",shop.getPkValue());
		builder.add("aos_acttype","=",actType.getPkValue());
		DynamicObject type = QueryServiceHelper.queryOne("aos_sal_act_type_p", "id", builder.toArray());
		if (type==null) {
			getView().showTipNotification("活动库选品规则未维护");
			return;
		}
		//获取活动规则
		DynamicObject acTypEntity = BusinessDataServiceHelper.loadSingle(type.getString("id"), "aos_sal_act_type_p");
		new EventRule(acTypEntity,actPlanEntity);

	}

	/**
	 * 批量设置活动数量
	 */
	private void batchSetActQty() {
		FndMsg.debug("=====into batchSetActQty=====");
		DynamicObject aos_act_select_plan = this.getModel().getDataEntity();
		Object fid = aos_act_select_plan.getPkValue().toString();
		DynamicObject aos_nationality = aos_act_select_plan.getDynamicObject("aos_nationality");
		DynamicObject aos_acttype = aos_act_select_plan.getDynamicObject("aos_acttype");
		DynamicObject aos_shop = aos_act_select_plan.getDynamicObject("aos_shop");
		String shopIdStr = aos_shop.getPkValue().toString();
		String orgIdStr = aos_nationality.getPkValue().toString();
		HashMap<String, Integer> itemShopMap = getItemShopQty(orgIdStr);
		DynamicObjectCollection aos_sal_actplanentityS = this.getModel().getEntryEntity("aos_sal_actplanentity");
		int size = aos_sal_actplanentityS.size();
		FndMsg.debug("size:" + size);
		int count = 0;
		for (DynamicObject aos_sal_actplanentity : aos_sal_actplanentityS) {
			FndMsg.debug(count++ + "/" + size);
			DynamicObject aos_itemnum = aos_sal_actplanentity.getDynamicObject("aos_itemnum");
			Date aos_l_startdate = aos_sal_actplanentity.getDate("aos_l_startdate");
			Date aos_enddate = aos_sal_actplanentity.getDate("aos_enddate");
			int betweenDays = FndDate.GetBetweenDays(aos_enddate, aos_l_startdate);
			String itemIdStr = aos_itemnum.getPkValue().toString();
			DynamicObjectCollection queryLastThreeS = QueryServiceHelper.query("aos_act_select_plan",
					"aos_sal_actplanentity.aos_actproid_salqty aos_actproid_salqty,"
							+ "aos_sal_actplanentity.aos_actbefore_salqty aos_actbefore_salqty",
					new QFilter("aos_nationality.id", QCP.equals, orgIdStr).and("id", QCP.not_equals, fid)
							.and("aos_sal_actplanentity.aos_itemnum.id", QCP.equals, itemIdStr)
							.and("aos_acttype.id", QCP.equals, aos_acttype.getPkValue().toString()).toArray(),
					"aos_sal_actplanentity.id desc", 3);
			BigDecimal aos_actqty = BigDecimal.ZERO;
			int hisQty = 0;
			String key = itemIdStr + "~" + shopIdStr;
			if (FndGlobal.IsNotNull(itemShopMap) && FndGlobal.IsNotNull(itemShopMap.get(key)))
				hisQty = itemShopMap.get(key);
			if (queryLastThreeS.size() == 3) {
				for (DynamicObject queryLastThree : queryLastThreeS) {
					aos_actqty = aos_actqty.add(BigDecimal.valueOf(queryLastThree.getInt("aos_actproid_salqty")))
							.subtract(BigDecimal.valueOf(queryLastThree.getInt("aos_actbefore_salqty")));
				}
				aos_actqty = FndMath
						.max(aos_actqty.divide(BigDecimal.valueOf(3), 2, BigDecimal.ROUND_HALF_UP), BigDecimal.ZERO)
						.add(BigDecimal.valueOf(hisQty)).multiply(BigDecimal.valueOf(betweenDays));
			} else {
				aos_actqty = BigDecimal.valueOf(hisQty).multiply(BigDecimal.valueOf(1.1))
						.multiply(BigDecimal.valueOf(betweenDays));
			}
			aos_sal_actplanentity.set("aos_actqty", aos_actqty);
		}
		this.getView().updateView();
		this.getView().showSuccessNotification("批量计算" + size + "条活动数量成功!");
	}

	/**
	 * 获取七日 国别+物料+店铺销量
	 * 
	 * @param orgIdStr
	 * @return
	 */
	private HashMap<String, Integer> getItemShopQty(String orgIdStr) {
		FndMsg.debug("=====into getItemShopQty=====");
		HashMap<String, Integer> itemShopMap = new HashMap<>();
		Calendar date = Calendar.getInstance();
		date.set(Calendar.HOUR_OF_DAY, 0);
		date.set(Calendar.MINUTE, 0);
		date.set(Calendar.SECOND, 0);
		date.set(Calendar.MILLISECOND, 0);
		Date Today = date.getTime();
		DataSet orderS = QueryServiceHelper.queryDataSet("aos_mkt_actrule_generate.getItemShopQty." + orgIdStr,
				"aos_sync_om_order_r", "aos_item_fid,aos_shopfid,aos_order_qty",
				new QFilter("aos_org.id", QCP.equals, orgIdStr)
						.and("aos_order_date", QCP.large_equals, FndDate.add_days(Today, -7)).toArray(),
				null);
		String[] GroupBy = new String[] { "aos_item_fid", "aos_shopfid" };
		orderS = orderS.groupBy(GroupBy).sum("aos_order_qty").finish();
		while (orderS.hasNext()) {
			Row order = orderS.next();
			String key = order.getString("aos_item_fid") + "~" + order.getString("aos_shopfid");
			FndMsg.debug("key1:" + key);
			itemShopMap.put(key, order.getInteger("aos_order_qty"));
		}
		orderS.close();
		return itemShopMap;
	}

	private String getNoPriceItem() {
		try {
			Object billno = this.getModel().getValue("billno");
			QFilter filter = new QFilter("aos_billno", "=", billno);
			QFilter filter_item = new QFilter("entryentity.aos_item", "!=", "");
			DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_noprice", "entryentity.aos_item aos_item",
					new QFilter[] { filter, filter_item });
			StringJoiner str = new StringJoiner(" , ");
			for (DynamicObject dy : dyc) {
				str.add(dy.getString("aos_item"));
			}
			return str.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// 根据国别+活动类型选择代码执行
	private void execute(String ouCode, String shop, String actType, DynamicObject object) throws Exception {
		switch (ouCode) {
		case "US":
			switchActUS(shop, actType, object);
			break;

		case "CA":
			switchActCA(shop, actType, object);
			break;

		case "UK":
			switchActUK(shop, actType, object);
			break;

		case "DE":
			switchActDE(shop, actType, object);
			break;

		case "FR":
			switchActFR(shop, actType, object);
			break;

		case "IT":
			switchActIT(shop, actType, object);
			break;

		case "ES":
			switchActES(shop, actType, object);
			break;
		}
	}

	private void switchActUS(String shop, String actType, DynamicObject object) throws Exception {
		FndMsg.debug("=====into switchActUS=====");
		FndMsg.debug("actType:" + actType);
		FndMsg.debug("shop:" + shop);
		switch (actType) {
		// 1.活动类型：DOTD
		case "DOTD": {
			FndMsg.debug("=====US DotdUS=====");
			new ActExecute(new DotdUS()).execute(object);
			break;
		}
		// 2.活动类型：LD/7DD=============
		case "LD":
			FndMsg.debug("=====US LD=====");
		case "7DD": {
			FndMsg.debug("=====US LDAnd7DDUS=====");
			new ActExecute(new LDAnd7DDUS()).execute(object);
			break;
		}
		// 3.活动类型：Ebay（Daily Deal、Primary Deal）
		case "Daily Deal":
			FndMsg.debug("=====US Daily Deal=====");
		case "Primary Deal": {
			FndMsg.debug("=====US EbayDDAndPD=====");
			new ActExecute(new EbayDDAndPD()).execute(object);
			break;
		}

		case "Theme Event": {
			switch (shop) {
			// 4.活动类型：Walmart（Theme Event）
			case "US-Walmart": {
				FndMsg.debug("=====US WalmartTheme=====");
				new ActExecute(new WalmartTheme()).execute(object);
				break;
			}
			// 6、活动类型：Target（Theme Event）、Bed Bath & Beyond（Theme Event）
			case "US-Target":
				FndMsg.debug("=====US Target=====");
			case "US-Bed Bath & Beyond": {
				FndMsg.debug("=====US TargetTheme=====");
				new ActExecute(new TargetTheme()).execute(object);
				break;
			}

			// 8、活动类型：The Home Depot（Theme Event）
			case "US-The Home Depot": {
				FndMsg.debug("=====US TheHomeDepotUS=====");
				new ActExecute(new TheHomeDepotUS()).execute(object);
				break;
			}

			// 9、活动类型：untilgone.com（Theme Event）
			case "US-untilgone.com": {
				FndMsg.debug("=====US WayfairCloseOutUS=====");
				new ActExecute(new WayfairCloseOutUS()).execute(object);
				break;
			}
			}
		}
		// 5、活动类型：Walmart（Walmart Deal、Pet Saving Deal、Daily Hot Deals、Quarterly
		// Carousel、Home Furniture Deal、Seasonal Shelf of Deal、Sport Deal）
		// TODO Home Furniture Deal Sport Deal 系统不存在
		case "Walmart Deal":
		case "Pet Saving Deal":
		case "Daily Hot Deals":
		case "Quarterly Carousel":
		case "Seasonal Shelf of Deal": {
			FndMsg.debug("=====US WalmartDeal=====");
			new ActExecute(new WalmartDeal()).execute(object);
			break;
		}

		// 7、活动类型：Wayfair(Promotion/Flash Deal)
		case "Promotion":
		case "Flash Deal": {
			switch (shop) {
			case "US-Wayfair": {
				FndMsg.debug("=====US WayfairPro=====");
				new ActExecute(new WayfairPro()).execute(object);
				break;
			}
			}
		}

		// 8、活动类型：The Home Depot（SBOTW、SBOTD、DOTW）
		case "SBOTW":
		case "SBOTD":
		// TODO DOTW 系统不存在
		{
			FndMsg.debug("=====US TheHomeDepotUS=====");
			new ActExecute(new TheHomeDepotUS()).execute(object);
		}

		// 9、活动类型：Wayfair （Closeout）、Overstock（Clearance Badge）
		case "Closeout":
		case "Clearance Badge": {
			FndMsg.debug("=====US WayfairCloseOutUS=====");
			new ActExecute(new WayfairCloseOutUS()).execute(object);
		}

		// Tracker/Vipon
		case "Tracker/Vipon": {
			new ActExecute(new TrackerVipon()).execute(object);
			break;
		}
		}
	}

	private void switchActCA(String shop, String actType, DynamicObject object) throws Exception {
		FndMsg.debug("=====into switchActCA=====");
		FndMsg.debug("actType:" + actType);
		FndMsg.debug("shop:" + shop);
		switch (actType) {
		case "Tracker/Vipon": {
			FndMsg.debug("=====CA TrackerVipon=====");
			new ActExecute(new TrackerVipon()).execute(object);
			break;
		}
		}
	}

	private void switchActUK(String shop, String actType, DynamicObject object) throws Exception {
		FndMsg.debug("=====into switchActUK=====");
		FndMsg.debug("actType:" + actType);
		FndMsg.debug("shop:" + shop);
		if ("DOTD".equals(actType)) {
			FndMsg.debug("=====UK DOTDAct=====");
			new ActExecute(new DOTDAct()).execute(object);
		} else if ("LD".equals(actType) || "7DD".equals(actType)) {
			FndMsg.debug("=====UK LDAnd7DD=====");
			new ActExecute(new LDAnd7DD()).execute(object);
		} else if ("Daily Deal".equals(actType) || "eBay Deal".equals(actType) || "Seller Offer".equals(actType)) {
			FndMsg.debug("=====UK EbayAndDailyDeal=====");
			new ActExecute(new EbayAndDailyDeal()).execute(object);
		} else if ((("UK-ManoMano".equals(shop)) && ("Store Takeover".equals(actType) || "Theme Event".equals(actType)
				|| "Banner Event".equals(actType) || "Coupon".equals(actType)))
				|| ("UK-OnBuy".equals(shop) && ("Theme Event".equals(actType)))) {
			FndMsg.debug("=====UK Mano=====");
			new ActExecute(new Mano()).execute(object);
		} else if ("UK-OnBuy".equals(shop) && "WD".equals(actType)) {
			FndMsg.debug("=====UK WD=====");
			new ActExecute(new WD()).execute(object);
		} else if ("UK-The Range".equals(shop) && ("Theme Event".equals(actType) || "Banner".equals(actType))) {
			FndMsg.debug("=====UK TheRange=====");
			new ActExecute(new TheRange()).execute(object);
		} else if (("UK-Wayfair".equals(shop)) && ("Promotion".equals(actType) || "Daily Promotion".equals(actType)
				|| "Event".equals(actType) || "Flash Deal".equals(actType) || "Promotional Pricing".equals(actType)
				|| "Banner Event".equals(actType))) {
			FndMsg.debug("=====UK Wayfair=====");
			new ActExecute(new Wayfair()).execute(object);
		} else if (("UK-Matalan".equals(shop) && "Theme Event".equals(actType))
				|| ("UK-Ryman".equals(shop)
						&& ("Promotion".equals(actType) || "Weekly Deal".equals(actType) || "Leaftlet".equals(actType)))
				|| ("UK-Robert Dyas".equals(shop) && ("Theme Event".equals(actType)))) {
			{
				FndMsg.debug("=====UK Matalan=====");
				new ActExecute(new Matalan()).execute(object);
			}
		} else if (actType.equals("Tracker/Vipon")) {
			{
				FndMsg.debug("=====UK Tracker/Vipon=====");
				new ActExecute(new TrackerVipon()).execute(object);
			}
		}
	}

	private void switchActDE(String shop, String actType, DynamicObject object) throws Exception {
		FndMsg.debug("=====into switchActDE=====");
		if ("DOTD".equals(actType)) {
			FndMsg.debug("=====DE DotdDE=====");
			new ActExecute(new DotdDE()).execute(object);
		} else if ("LD".equals(actType) || "7DD".equals(actType)) {
			FndMsg.debug("=====DE LDAnd7DDDE=====");
			new ActExecute(new LDAnd7DDDE()).execute(object);
		} else if (("DE-POCO".equals(shop) && "Theme Event".equals(actType))
				|| ("DE-Weltbild".equals(shop) && "Catalogue".equals(actType))) {
			FndMsg.debug("=====DE PocoDE=====");
			new ActExecute(new PocoDE()).execute(object);
		} else if ("DE-Wayfair".equals(shop)
				&& ("Flash Deal".equals(actType) || "Promotional Pricing".equals(actType))) {
			FndMsg.debug("=====DE WayfairDE=====");
			new ActExecute(new WayfairDE()).execute(object);
		} else if (actType.equals("Tracker/Vipon")) {
			FndMsg.debug("=====DE TrackerVipon=====");
			new ActExecute(new TrackerVipon()).execute(object);
		}
	}

	private void switchActFR(String shop, String actType, DynamicObject object) throws Exception {
		FndMsg.debug("=====into switchActFR=====");
		if (actType.equals("Tracker/Vipon")) {
			new ActExecute(new TrackerVipon()).execute(object);
		}
	}

	private void switchActIT(String shop, String actType, DynamicObject object) throws Exception {
		FndMsg.debug("=====into switchActIT=====");
		if (actType.equals("Tracker/Vipon")) {
			new ActExecute(new TrackerVipon()).execute(object);
		}
	}

	private void switchActES(String shop, String actType, DynamicObject object) throws Exception {
		FndMsg.debug("=====into switchActES=====");
		if (!"ES-Amazon Vendor VOC".equals(shop) && "DOTD".equals(actType)) {
			FndMsg.debug("=====ES DotdES=====");
			new ActExecute(new DotdES()).execute(object);
		} else if ("LD".equals(actType) || "7DD".equals(actType)) {
			FndMsg.debug("=====ES LDAnd7DDES=====");
			new ActExecute(new LDAnd7DDES()).execute(object);
		} else if ("ES-Monechelle".equals(shop) && ("Theme Event".equals(actType) || "Banner".equals(actType))) {
			FndMsg.debug("=====ES ManoES=====");
			new ActExecute(new ManoES()).execute(object);
		} else if ("ES-Privalia".equals(shop) && "Theme Event".equals(actType)) {
			FndMsg.debug("=====ES PrivaliaActES=====");
			new ActExecute(new PrivaliaActES()).execute(object);
		} else if ("ES-Amazon Vendor VOC".equals(shop) && ("DOTD".equals(actType) || "Theme Event".equals(actType))) {
			FndMsg.debug("=====ES VCES=====");
			new ActExecute(new VCES()).execute(object);
		} else if (("ES-Worten ES".equals(shop) && ("Theme Event".equals(actType) || "WD".equals(actType)))
				|| ("ES-Worten".equals(shop) && ("Theme Event".equals(actType) || "Mega Noite".equals(actType)))
				|| ("ES-Carrefour".equals(shop) && ("Weekend Sales".equals(actType) || "Theme Event".equals(actType)))
				|| ("ES-Sprinter".equals(shop) && ("Weekend Sales".equals(actType) || "Theme Event".equals(actType)))
				|| ("ES-Sprinter PT".equals(shop) && ("Weekend Sales".equals(actType) || "Theme Event".equals(actType)))
				|| ("ES-MediaMarkt ES".equals(shop)
						&& ("Weekend Sales".equals(actType) || "Theme Event".equals(actType)))
				|| ("ES-PC COMPONENTES Y MULTIMEDIA SLU".equals(shop) && "Theme Event".equals(actType))) {
			FndMsg.debug("=====ES ThemeEventES=====");
			new ActExecute(new ThemeEventES()).execute(object);
		} else if (("ES-aliexpress-Aosom Spain (EU)".equals(shop) && "Theme Event".equals(actType))
				|| ("ES-Decathlon ES".equals(shop) && "Theme Event".equals(actType))
				|| ("ES-FNAC Portugal".equals(shop) && "Theme Event".equals(actType))
				|| ("ES-Fnac.es".equals(shop) && "Theme Event".equals(actType))
				|| ("ES-PC COMPONENTES Y MULTIMEDIA SLU".equals(shop) && "Weekly Deal".equals(actType))
				|| ("ES-Tienda Animal".equals(shop) && "Theme Event".equals(actType))) {
			FndMsg.debug("=====ES AliexpressES=====");
			new ActExecute(new AliexpressES()).execute(object);
		} else if (actType.equals("Tracker/Vipon")) {
			FndMsg.debug("=====ES TrackerVipon=====");
			new ActExecute(new TrackerVipon()).execute(object);
		}
	}

	@Override
	public void containerOnShow(ContainerOnShowEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void entryFilterChanged(EntryFilterChangedEvent e) {
	}
}