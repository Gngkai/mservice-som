package mkt.data.keyword;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.BadgeInfo;
import kd.bos.form.container.TabPage;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import org.apache.commons.lang3.StringUtils;

import common.fnd.FndGlobal;
import sal.dis.util.DisUtil;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

/**
 * lch 2022-04-15
 */
public class ItemKeywordBillPlugin extends AbstractBillPlugIn {

	/** 初始化事件 **/
	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		statusControl();
	}

	/**
	 * 界面状态控制
	 */
	private void statusControl() {
		DynamicObjectCollection aos_linentity_gwS = this.getModel().getEntryEntity("aos_linentity_gw");
		if (FndGlobal.IsNotNull(aos_linentity_gwS) && aos_linentity_gwS.size() > 0) {
			TabPage control = this.getControl("aos_linentity_gw");
			BadgeInfo badgeInfo = new BadgeInfo();
			badgeInfo.setBadgeText("!");
			badgeInfo.setOffset(new String[] { "5px", "5px" });
			control.setBadgeInfo(badgeInfo);
		}
	}

	@Override
	public void registerListener(EventObject e) {
		this.addItemClickListeners("tbmain");
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		String itemKey = evt.getItemKey();
		if ("aos_impkeyword".equals(itemKey)) {
			// 如果为引用关键词库
			importItemKeyword();
		}

		if ("aos_copyto".equals(itemKey)) {
			// 如果为引用关键词库
			DisUtil.popForm(this, "aos_mkt_itemselect", "items_select", null);
		}
	}

	// 引入关键词库
	private void importItemKeyword() {
		// 1. 国别
		DynamicObject aos_orgid = (DynamicObject) this.getModel().getValue("aos_orgid");
		// 2. 物料
		DynamicObject aos_itemid = (DynamicObject) this.getModel().getValue("aos_itemid");

		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_point", "aos_linentity.aos_mainvoc",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid.getString("id")),
						new QFilter("aos_itementity.aos_itemid", QCP.equals, aos_itemid.getString("id")), });
		if (list == null || list.size() == 0) {
			this.getView().showTipNotification("无可用数据!");
			return;
		}

		// 删除原来的
		this.getModel().deleteEntryData("aos_entryentity");
		// 取出关键词 赋值到关键词单据体
		for (DynamicObject obj : list) {
			String aos_mainvoc = obj.getString("aos_linentity.aos_mainvoc");
			int index = this.getModel().createNewEntryRow("aos_entryentity");
			this.getModel().setValue("aos_mainvoc", aos_mainvoc, index);
		}

		DynamicObjectCollection listgw = QueryServiceHelper.query("aos_mkt_point",
				"aos_linentity_gw.aos_gw_keyword aos_gw_keyword," + "aos_linentity_gw.aos_gw_search aos_gw_search",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid.getString("id")),
						new QFilter("aos_itementity.aos_itemid", QCP.equals, aos_itemid.getString("id")), });
		// 删除原来的
		this.getModel().deleteEntryData("aos_linentity_gw");
		// 取出关键词 赋值到关键词单据体
		for (DynamicObject obj : listgw) {
			String aos_gw_keyword = obj.getString("aos_gw_keyword");
			String aos_gw_search = obj.getString("aos_gw_search");
			int index = this.getModel().createNewEntryRow("aos_linentity_gw");
			this.getModel().setValue("aos_gw_keyword", aos_gw_keyword, index);
			this.getModel().setValue("aos_gw_search", aos_gw_search, index);
		}
	}

	@Override
	public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
		if (StringUtils.equals(closedCallBackEvent.getActionId(), "items_select")
				&& null != closedCallBackEvent.getReturnData()) {
			// 这里返回对象为Object，可强转成相应的其他类型，
			// 单条数据可用String类型传输，返回多条数据可放入map中，也可使用json等方式传输
			Object returnData = closedCallBackEvent.getReturnData();
			System.out.println("returnData = " + returnData);
			if (returnData instanceof List) {
				DynamicObject aos_orgid = (DynamicObject) this.getModel().getValue("aos_orgid");
				@SuppressWarnings("unchecked")
				List<String> aos_items = (List<String>) returnData;

				DynamicObject[] load = BusinessDataServiceHelper.load("aos_mkt_keyword", "aos_entryentity.aos_mainvoc",
						new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid.getString("id")),
								new QFilter("aos_itemid", QCP.in, aos_items) });
				// 获取界面上所有的关键词
				List<String> itemKeywordList = new ArrayList<>();
				DynamicObjectCollection aos_entryentity1 = this.getModel().getEntryEntity("aos_entryentity");
				for (DynamicObject obj : aos_entryentity1) {
					itemKeywordList.add(obj.getString("aos_mainvoc"));
				}
				for (DynamicObject obj : load) {
					DynamicObjectCollection aos_entryentity = obj.getDynamicObjectCollection("aos_entryentity");
					for (String keyword : itemKeywordList) {
						DynamicObject dynamicObject = aos_entryentity.addNew();
						dynamicObject.set("aos_mainvoc", keyword);
					}
				}

				Object[] save = SaveServiceHelper.save(load);
				for (Object obj : save) {
					System.out.println("obj = " + obj);
				}
			}
		}
	}
}
