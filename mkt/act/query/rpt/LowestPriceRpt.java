package mkt.act.query.rpt;

import common.fnd.FndGlobal;
import common.sal.permission.PermissionUtil;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.JoinDataSet;
import kd.bos.algo.JoinType;
import kd.bos.algo.Row;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.datamodel.AbstractFormDataModel;
import kd.bos.entity.datamodel.TableValueSetter;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.field.BasedataEdit;
import kd.bos.form.field.events.BeforeF7SelectEvent;
import kd.bos.form.field.events.BeforeF7SelectListener;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.EventObject;
import java.util.List;

/**
 * 最低价查询
 */
public class LowestPriceRpt extends AbstractFormPlugin implements BeforeF7SelectListener {
	private static final String BUTTON_INQUIRE = "aos_inquire";
	private static final String ENTRY_ENTITY = "aos_entryentity";

	private static final String QUERY_FIELD_ORG = "aos_orgid_h";
	private static final String QUERY_FIELD_PLATFORM = "aos_platformid_h";
	private static final String QUERY_FIELD_SHOP = "aos_shopid_h";
	private static final String QUERY_FIELD_START_DATE = "aos_startdate";
	private static final String QUERY_FIELD_END_DATE = "aos_enddate";

	private static final String BILL_ORDER_LINE = "aos_sync_om_order_r";
	private static final String BILL_LOWEST_PRICING = "aos_base_przbak";
	private static final String BILL_ACTIVITY_PLAN = "aos_act_select_plan";
	private static final String BILL_ITEM = "bd_material";
	private static final String BILL_REVIEW = "aos_sync_review";

	@Override
	public void registerListener(EventObject e) {
		BasedataEdit shopBaseData = this.getControl(QUERY_FIELD_SHOP);
		shopBaseData.addBeforeF7SelectListener(this);
	}

	@Override
	public void beforeF7Select(BeforeF7SelectEvent f7SelectEvent) {
		String name = f7SelectEvent.getProperty().getName();
		if (QUERY_FIELD_SHOP.equals(name)) {
			// 根据国别和渠道过滤店铺
			String orgId = getOrgId();
			String platFormId = getPlatFormId();
			f7SelectEvent.addCustomQFilter(new QFilter("aos_org", QCP.equals, orgId));
			f7SelectEvent.addCustomQFilter(new QFilter("aos_channel", QCP.equals, platFormId));
		}

		if (QUERY_FIELD_ORG.equals(name)) {
			QFilter idQFilter = PermissionUtil.getOrgQFilterForSale(UserServiceHelper.getCurrentUserId(), "id");
			f7SelectEvent.addCustomQFilter(idQFilter);
		}
	}

	@Override
	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();

		// 国别或店铺改变 清空店铺
		if (QUERY_FIELD_ORG.equals(name) || QUERY_FIELD_PLATFORM.equals(name)) {
			this.getModel().setValue(QUERY_FIELD_SHOP, null);
		}
	}

	@Override
	public void afterDoOperation(AfterDoOperationEventArgs args) {
		String operateKey = args.getOperateKey();
		if (BUTTON_INQUIRE.equals(operateKey)) {
//			inquire();
			inquire2();
		}
	}

	private void inquire2() {
		String startDate = getStartDate();
		String endDate = getEndDate();
		String aos_orgid = getOrgId();
		String aos_platformid = getPlatFormId();
		String aos_shopid = getShopId();
		if ("".equals(startDate) || "".equals(endDate) || FndGlobal.IsNull(aos_orgid)
				|| FndGlobal.IsNull(aos_platformid) || FndGlobal.IsNull(aos_shopid)) {
			this.getView().showTipNotification("参数必填");
			return;
		}
		// 界面赋值字段
		List<String> fieldList = new ArrayList<>();
		fieldList.add("aos_orgid");
		fieldList.add("aos_itemid");
		fieldList.add("aos_brandid");
		fieldList.add("aos_asin");
		fieldList.add("aos_reviewnum");
		fieldList.add("aos_stars");
		// 物料查询
		QFilter qFilter = new QFilter("aos_contryentry.aos_nationality", QCP.is_notnull, null);
		qFilter.and(new QFilter("aos_protype", QCP.equals, "N"));// 正常产品
		qFilter.and(new QFilter("aos_type", QCP.equals, "A"));// 类别为“产品”
		qFilter.and(new QFilter("aos_iscomb", QCP.equals, "0"));// 不为组合产品
		qFilter.and(new QFilter("aos_contryentry.aos_nationality", QCP.equals, aos_orgid));
		String selectFields = "id aos_itemid, aos_contryentry.aos_nationality aos_orgid, aos_contryentry.aos_contrybrand aos_brandid";
		DataSet dataSet = queryItem(selectFields, qFilter);
		// 店铺 平台
		DataSet dataSetShop = QueryServiceHelper.queryDataSet("LowestPriceRpt.shop", "aos_sal_shop",
				"aos_org,id aos_shopid,aos_channel aos_platformid", new QFilter("id", QCP.equals, aos_shopid).toArray(),
				null);
		dataSet = dataSet.join(dataSetShop, JoinType.LEFT).on("aos_orgid", "aos_org")
				.select(new String[] { "aos_orgid", "aos_itemid", "aos_brandid", "aos_shopid", "aos_platformid" })
				.finish();
		fieldList.add("aos_shopid");
		fieldList.add("aos_platformid");
		// 客户评论信息
		String selectFields4 = "aos_orgid, aos_itemid, aos_asin,aos_review aos_reviewnum, aos_stars";
		QFilter qFilter4 = new QFilter("aos_orgid", QCP.equals, aos_orgid);
		DataSet dataSet4 = queryReview(selectFields4, qFilter4);
		dataSet = dataSet.join(dataSet4, JoinType.LEFT).on("aos_orgid", "aos_orgid").on("aos_itemid", "aos_itemid")
				.select(fieldList.toArray(new String[0])).finish();
		// 最低成交价过滤条件
		QFilter qFilter1 = new QFilter("aos_local_date", QCP.large_equals, startDate);
		qFilter1.and(new QFilter("aos_local_date", QCP.less_equals, endDate));
		qFilter1.and(new QFilter("aos_org", QCP.equals, aos_orgid));
		qFilter1.and(new QFilter("aos_platformfid", QCP.equals, aos_platformid));
		qFilter1.and(new QFilter("aos_shopfid", QCP.equals, aos_shopid));
		String selectFields1 = "aos_org aos_orgid, " + "aos_item_fid aos_itemid," + "aos_platformfid aos_platformid, "
				+ "aos_shopfid aos_shopid," + "aos_trans_price aos_lwsttransprice";
		String[] groupByField = { "aos_orgid", "aos_itemid", "aos_shopid", "aos_platformid" };
		DataSet dataSet1 = queryLowestTransPrice(selectFields1, qFilter1, groupByField);
		fieldList.add("aos_lwsttransprice");
		dataSet = dataSet.join(dataSet1, JoinType.LEFT).on("aos_orgid", "aos_orgid").on("aos_itemid", "aos_itemid")
				.on("aos_shopid", "aos_shopid").select(fieldList.toArray(new String[0])).finish();
		// 活动次数
		QFilter qFilter2 = new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, endDate);
		qFilter2.and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.large_equals, startDate));
		qFilter2.and(new QFilter("aos_nationality", QCP.equals, aos_orgid));
		qFilter2.and(new QFilter("aos_channel", QCP.equals, aos_platformid));
		qFilter2.and(new QFilter("aos_shop", QCP.equals, aos_shopid));
		String selectFields2 = "aos_nationality aos_orgid, " + "aos_channel aos_platformid, " + "aos_shop aos_shopid, "
				+ "aos_sal_actplanentity.aos_itemnum aos_itemid," + "1 as aos_acttimes";
		DataSet dataSet2 = queryActivityTimes(selectFields2, qFilter2, groupByField);
		fieldList.add("aos_acttimes");
		dataSet = dataSet.join(dataSet2, JoinType.LEFT).on("aos_orgid", "aos_orgid").on("aos_itemid", "aos_itemid")
				.on("aos_shopid", "aos_shopid").select(fieldList.toArray(new String[0])).finish();
		// 最低定价
		QFilter qFilter3 = new QFilter("aos_date", QCP.large_equals, startDate);
		qFilter3.and(new QFilter("aos_date", QCP.less_equals, endDate)).and("aos_entryentity.aos_orgid", QCP.equals, aos_orgid);
		String selectFields3 = "aos_entryentity.aos_orgid aos_orgid, " + "aos_entryentity.aos_itemid aos_itemid,"
				+ "aos_entryentity.aos_currentprice aos_lwstpricing";
		DataSet dataSet3 = queryLowestPricing(selectFields3, qFilter3, new String[] { "aos_orgid", "aos_itemid" });
		fieldList.add("aos_lwstpricing");
		dataSet = dataSet.join(dataSet3, JoinType.LEFT).on("aos_orgid", "aos_orgid").on("aos_itemid", "aos_itemid")
				.select(fieldList.toArray(new String[0])).finish();

		TableValueSetter tableValueSetter = handleDataToSetter(dataSet, fieldList);
		setEntryEntity(tableValueSetter);
		dataSet.close();
		dataSetShop.close();
		dataSet4.close();
		dataSet1.close();
		dataSet2.close();
		dataSet3.close();
	}

	private void inquire() {
		String startDate = getStartDate();
		String endDate = getEndDate();
		if ("".equals(startDate) || "".equals(endDate)) {
			this.getView().showTipNotification("日期范围必填");
			return;
		}
		String aos_orgid = getOrgId();
		String aos_platformid = getPlatFormId();
		String aos_shopid = getShopId();

		int queryType = getQueryType(aos_orgid, aos_platformid, aos_shopid);

		// 界面赋值字段
		List<String> fieldList = new ArrayList<>();
		fieldList.add("aos_orgid");
		fieldList.add("aos_itemid");
		fieldList.add("aos_brandid");
		fieldList.add("aos_asin");
		fieldList.add("aos_reviewnum");
		fieldList.add("aos_stars");

		// 物料查询字段等
		String selectFields = "id aos_itemid, aos_contryentry.aos_nationality aos_orgid, aos_contryentry.aos_contrybrand aos_brandid";
		QFilter qFilter = new QFilter("aos_contryentry.aos_nationality", QCP.is_notnull, null);
		qFilter.and(new QFilter("aos_protype", QCP.equals, "N"));// 正常产品
		qFilter.and(new QFilter("aos_type", QCP.equals, "A"));// 类别为“产品”
		qFilter.and(new QFilter("aos_iscomb", QCP.equals, "0"));// 不为组合产品

		// 客户评论信息查询字段等
		String selectFields4 = "aos_orgid, aos_itemid, aos_asin,aos_review aos_reviewnum, aos_stars";
		QFilter qFilter4 = new QFilter("aos_orgid", QCP.is_notnull, null);
		if (queryType == 2) {
			qFilter.and(new QFilter("aos_contryentry.aos_nationality", QCP.equals, aos_orgid));
			qFilter4.and(new QFilter("aos_orgid", QCP.equals, aos_orgid));
		}

		DataSet dataSet = queryItem(selectFields, qFilter);
		DataSet dataSetShop = QueryServiceHelper.queryDataSet(this.getClass().getName() + ".shop", "aos_sal_shop",
				"aos_org,id shopid", null, null);
		dataSet = dataSet.join(dataSetShop, JoinType.LEFT).on("aos_orgid", "aos_org")
				.select(new String[] { "aos_orgid", "aos_itemid", "aos_brandid", "shopid" }).finish();
		fieldList.add("shopid");

		DataSet dataSet4 = queryReview(selectFields4, qFilter4);
		dataSet = dataSet.join(dataSet4, JoinType.LEFT).on("aos_orgid", "aos_orgid").on("aos_itemid", "aos_itemid")
				.select(fieldList.toArray(new String[0])).finish();

		// 分组字段
		String[] groupByField = { "aos_orgid", "aos_itemid", "aos_shopid" };

		// 最低成交价过滤条件
		QFilter qFilter1 = new QFilter("aos_local_date", QCP.large_equals, startDate);
		qFilter1.and(new QFilter("aos_local_date", QCP.less_equals, endDate));
		// 最低成交价查询字段
		String selectFields1 = "aos_org aos_orgid, " + "aos_item_fid aos_itemid,"
				+ "aos_trans_price aos_lwsttransprice,aos_shopfid aos_shopid";

		// 活动次数过滤条件
		QFilter qFilter2 = new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, endDate);
		qFilter2.and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.large_equals, startDate));
		// 活动次数查询字段
		String selectFields2 = "aos_nationality aos_orgid, " + "aos_sal_actplanentity.aos_itemnum aos_itemid,"
				+ "1 as aos_acttimes";

		// 最低定价过滤条件
		QFilter qFilter3 = new QFilter("aos_date", QCP.large_equals, startDate);
		qFilter3.and(new QFilter("aos_date", QCP.less_equals, endDate));
		String selectFields3 = "aos_entryentity.aos_orgid aos_orgid, " + "aos_entryentity.aos_itemid aos_itemid,"
				+ "aos_entryentity.aos_currentprice aos_lwstpricing";

		// 如果只到国别维度 活动次数不取亚马逊 eBay活动
		String[] platformArr = { "AMAZON", "EBAY" };
		if (1 == queryType) {
			qFilter2.and(new QFilter("aos_channel.number", QCP.not_in, platformArr));
		}
		// 只填了国别
		else if (2 == queryType) {
			qFilter1.and(new QFilter("aos_org", QCP.equals, aos_orgid));

			//
			qFilter2.and(new QFilter("aos_nationality", QCP.equals, aos_orgid));
			qFilter2.and(new QFilter("aos_channel.number", QCP.not_in, platformArr));

			qFilter3.and(new QFilter("aos_entryentity.aos_orgid", QCP.equals, aos_orgid));
		}
		// 只填了国别和渠道
		else if (3 == queryType) {
			// 最低成交价
			qFilter1.and(new QFilter("aos_org", QCP.equals, aos_orgid));
			qFilter1.and(new QFilter("aos_platformfid", QCP.equals, aos_platformid));
			selectFields1 = "aos_org aos_orgid, " + "aos_item_fid aos_itemid," + "aos_platformfid aos_platformid, "
					+ "aos_trans_price aos_lwsttransprice,aos_shopfid aos_shopid";

			// 活动次数
			qFilter2.and(new QFilter("aos_nationality", QCP.equals, aos_orgid));
			qFilter2.and(new QFilter("aos_channel", QCP.equals, aos_platformid));
			selectFields2 = "aos_nationality aos_orgid, " + "aos_channel aos_platformid, "
					+ "aos_sal_actplanentity.aos_itemnum aos_itemid," + "1 as aos_acttimes";

			fieldList.add("aos_platformid");
			groupByField = new String[] { "aos_orgid", "aos_platformid", "aos_itemid", "aos_shopid" };
		} else if (4 == queryType) {
			// 最低成交价
			qFilter1.and(new QFilter("aos_org", QCP.equals, aos_orgid));
			qFilter1.and(new QFilter("aos_platformfid", QCP.equals, aos_platformid));
			qFilter1.and(new QFilter("aos_shopfid", QCP.equals, aos_shopid));
			selectFields1 = "aos_org aos_orgid, " + "aos_item_fid aos_itemid," + "aos_platformfid aos_platformid, "
					+ "aos_shopfid aos_shopid," + "aos_trans_price aos_lwsttransprice";

			// 活动次数
			qFilter2.and(new QFilter("aos_nationality", QCP.equals, aos_orgid));
			qFilter2.and(new QFilter("aos_channel", QCP.equals, aos_platformid));
			qFilter2.and(new QFilter("aos_shop", QCP.equals, aos_shopid));
			selectFields2 = "aos_nationality aos_orgid, " + "aos_channel aos_platformid, " + "aos_shop aos_shopid, "
					+ "aos_sal_actplanentity.aos_itemnum aos_itemid," + "1 as aos_acttimes";

			fieldList.add("aos_platformid");
			fieldList.add("aos_shopid");

			groupByField = new String[] { "aos_orgid", "aos_platformid", "aos_shopid", "aos_itemid" };
		}

		DataSet dataSet1 = queryLowestTransPrice(selectFields1, qFilter1, groupByField);
		DataSet dataSet2 = queryActivityTimes(selectFields2, qFilter2, groupByField);
		DataSet dataSet3 = queryLowestPricing(selectFields3, qFilter3, new String[] { "aos_orgid", "aos_itemid" });
		fieldList.add("aos_lwsttransprice");
		dataSet = dataSet.join(dataSet1, JoinType.LEFT).on("aos_orgid", "aos_orgid").on("aos_itemid", "aos_itemid")
				.on("shopid", "aos_shopid").select(fieldList.toArray(new String[0])).finish();

		fieldList.add("aos_acttimes");
		dataSet = joinDataSet(dataSet, dataSet2, queryType, fieldList);

		fieldList.add("aos_lwstpricing");
		dataSet = dataSet.join(dataSet3, JoinType.LEFT).on("aos_orgid", "aos_orgid").on("aos_itemid", "aos_itemid")
				.select(fieldList.toArray(new String[0])).finish();

		if (3 == queryType) {
			dataSet = dataSet.where("aos_platformid = " + aos_platformid);
		} else if (4 == queryType) {
			dataSet = dataSet.where("aos_platformid = " + aos_platformid);
			dataSet = dataSet.where("shopid = " + aos_shopid);
		}
		TableValueSetter tableValueSetter = handleDataToSetter(dataSet, fieldList);
		setEntryEntity(tableValueSetter);
		dataSet1.close();
		dataSet2.close();
		dataSet3.close();
	}

	private DataSet joinDataSet(DataSet dataSet1, DataSet dataSet2, int queryType, List<String> fieldList) {
		JoinDataSet joinDataSet = dataSet1.join(dataSet2, JoinType.LEFT);
		if (1 == queryType || 2 == queryType) {
			joinDataSet = joinDataSet.on("aos_orgid", "aos_orgid").on("aos_itemid", "aos_itemid")
					.on("shopid", "aos_shopid").select(fieldList.toArray(new String[0]));
		} else if (3 == queryType) {
			joinDataSet = joinDataSet.on("aos_orgid", "aos_orgid").on("aos_platformid", "aos_platformid")
					.on("shopid", "aos_shopid").on("aos_itemid", "aos_itemid").select(fieldList.toArray(new String[0]));
		} else {
			joinDataSet = joinDataSet.on("aos_orgid", "aos_orgid").on("aos_platformid", "aos_platformid")
					.on("shopid", "aos_shopid").on("aos_shopid", "aos_shopid").on("aos_itemid", "aos_itemid")
					.select(fieldList.toArray(new String[0]));
		}
		return joinDataSet.finish();
	}

	private int getQueryType(String aos_orgid, String aos_platformid, String aos_shopid) {
		// 任何查询字段都没填
		if ("".equals(aos_orgid) && "".equals(aos_platformid) && "".equals(aos_shopid)) {
			return 1;
		}
		// 只填了国别
		else if (!"".equals(aos_orgid) && "".equals(aos_platformid) && "".equals(aos_shopid)) {
			return 2;
		}
		// 只填了国别和渠道
		else if (!"".equals(aos_orgid) && !"".equals(aos_platformid) && "".equals(aos_shopid)) {
			return 3;
		}
		// 国别渠道店铺都填
		else {
			return 4;
		}
	}

	private DataSet queryActivityTimes(String selectFields, QFilter qFilter, String[] groupByField) {
		String algoKey = this.getClass().getName() + "queryActivityTimes";
		DataSet dataSet = QueryServiceHelper.queryDataSet(algoKey, BILL_ACTIVITY_PLAN, selectFields, qFilter.toArray(),
				null);
		return dataSet.groupBy(groupByField).sum("aos_acttimes").finish();
	}

	private DataSet queryLowestTransPrice(String selectFields, QFilter qFilter, String[] groupByField) {
		String algoKey = this.getClass().getName() + "queryLowestPrice";
		List<QFilter> orderFilter = SalUtil.getOrderFilter();
		qFilter = qFilter.and(orderFilter.get(0));
		DataSet dataSet = QueryServiceHelper.queryDataSet(algoKey, BILL_ORDER_LINE, selectFields, qFilter.toArray(),
				null);
		return dataSet.groupBy(groupByField).min("aos_lwsttransprice").finish();
	}

	private DataSet queryLowestPricing(String selectFields, QFilter qFilter, String[] groupByField) {
		String algoKey = this.getClass().getName() + "queryLowestPricing";
		DataSet dataSet = QueryServiceHelper.queryDataSet(algoKey, BILL_LOWEST_PRICING, selectFields, qFilter.toArray(),
				null);
		return dataSet.groupBy(groupByField).min("aos_lwstpricing").finish();
	}

	private DataSet queryItem(String selectFields, QFilter qFilter) {
		String algoKey = this.getClass().getName() + "queryItem";
		return QueryServiceHelper.queryDataSet(algoKey, BILL_ITEM, selectFields, qFilter.toArray(), null);
	}

	private DataSet queryReview(String selectFields, QFilter qFilter) {
		String algoKey = this.getClass().getName() + "queryItem";
		return QueryServiceHelper.queryDataSet(algoKey, BILL_REVIEW, selectFields, qFilter.toArray(), null);
	}

	private TableValueSetter handleDataToSetter(DataSet dataSet, List<String> fieldList) {
		TableValueSetter tableValueSetter = new TableValueSetter();
		int count = 0;
		while (dataSet.hasNext()) {
			Row next = dataSet.next();
			for (String field : fieldList) {
				tableValueSetter.set(field, next.get(field), count);
			}
			count++;
		}
		return tableValueSetter;
	}

	private void setEntryEntity(TableValueSetter setter) {
		AbstractFormDataModel model = (AbstractFormDataModel) this.getModel();
		model.deleteEntryData(ENTRY_ENTITY);
		// 开启事务
		model.beginInit();
		model.batchCreateNewEntryRow(ENTRY_ENTITY, setter);
		// 关闭事务
		model.endInit();
		this.getView().updateView(ENTRY_ENTITY);
	}

	private String getEndDate() {
		return getDateStr(QUERY_FIELD_END_DATE);
	}

	private String getStartDate() {
		return getDateStr(QUERY_FIELD_START_DATE);
	}

	private String getDateStr(String dateField) {
		Date date = (Date) this.getModel().getValue(dateField);
		return date == null ? "" : DateFormatUtils.format(date, "yyyy-MM-dd");
	}

	private String getShopId() {
		return getBaseDataId(QUERY_FIELD_SHOP);
	}

	private String getPlatFormId() {
		return getBaseDataId(QUERY_FIELD_PLATFORM);
	}

	private String getOrgId() {
		return getBaseDataId(QUERY_FIELD_ORG);
	}

	private String getBaseDataId(String field) {
		DynamicObject object = (DynamicObject) this.getModel().getValue(field);
		return object == null ? "" : object.getString("id");
	}
}
