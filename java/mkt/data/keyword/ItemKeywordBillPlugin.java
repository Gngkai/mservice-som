package mkt.data.keyword;

import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.BadgeInfo;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.container.TabPage;
import kd.bos.form.control.Control;
import kd.bos.form.control.Hyperlink;
import kd.bos.form.control.Label;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import common.sal.util.QFBuilder;
import org.apache.commons.lang3.StringUtils;
import sal.dis.util.DisUtil;

import java.util.*;

/**
 * lch 2022-04-15
 */
public class ItemKeywordBillPlugin extends AbstractBillPlugIn {
	@Override
	public void afterBindData(EventObject e) {
		super.afterBindData(e);
		setItemUrl();
	}

	private void setItemUrl() {
		this.getModel().setValue("aos_textfield", "AM链接");
		Hyperlink hyperlink = this.getView().getControl("aos_hyperlinkap");
		DynamicObject aos_orgid = (DynamicObject) this.getModel().getValue("aos_orgid");
		DynamicObject aos_itemid = (DynamicObject) this.getModel().getValue("aos_itemid");
		Map<String, Object> style = new HashMap<>();
		if (aos_orgid != null && aos_itemid != null) {
			String itemID = aos_itemid.getString("id");
			List<String> list = Arrays.asList(itemID);
			Map<String, String> map = SalUtil.queryAsin(aos_orgid.getPkValue(), aos_orgid.getString("number"), list);
			if (map.containsKey(itemID)) {
				String url = map.get(itemID);
				String[] split = url.split("!");
				if (split.length > 1) {
					hyperlink.setUrl(split[0] + split[1]);
					Map<String, Object> map1 = new HashMap<>();
					style.put("text", map1);
					map1.put("zh_CN", split[1]);
					map1.put("en_US", split[1]);
				}
			}
		}
//        if (style.isEmpty()) {
//            Map<String, Object> map1 = new HashMap<>();
//            style.put("text", map1);
//            map1.put("zh_CN", " ");
//            map1.put("en_US"," ");
//        }
		this.getView().updateControlMetadata("aos_hyperlinkap", style);
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
			statusControl();
		}

		if ("aos_copyto".equals(itemKey)) {
			// 如果为引用关键词库
			DisUtil.popForm(this, "aos_mkt_itemselect", "items_select", null);
			statusControl();
		}
	}

	@Override
	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		if (name.equals("aos_orgid"))
			setItemUrl();
		else if (name.equals("aos_itemid"))
			setItemUrl();
	}

	@Override
	public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
		if (StringUtils.equals(closedCallBackEvent.getActionId(), "items_select")
				&& null != closedCallBackEvent.getReturnData()) {
			// 这里返回对象为Object，可强转成相应的其他类型，
			// 单条数据可用String类型传输，返回多条数据可放入map中，也可使用json等方式传输
			Object returnData = closedCallBackEvent.getReturnData();
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
				}
			}
		}
	}

	@Override
	public void beforeDoOperation(BeforeDoOperationEventArgs args) {
		super.beforeDoOperation(args);
		FormOperate operate = (FormOperate) args.getSource();
		String operateKey = operate.getOperateKey();
		if (operateKey.equals("save")) {
			beforeSave();
			statusControl();
		}
	}

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
			TabPage control = this.getControl("aos_tabpageap3");
			BadgeInfo badgeInfo = new BadgeInfo();
			badgeInfo.setBadgeText("!");
			badgeInfo.setOffset(new String[] { "5px", "5px" });
			control.setBadgeInfo(badgeInfo);
		}
	}

	// 引入关键词库
	private void importItemKeyword() {
		// 1. 国别
		DynamicObject aos_orgid = (DynamicObject) this.getModel().getValue("aos_orgid");
		// 2. 物料
		DynamicObject aos_itemid = (DynamicObject) this.getModel().getValue("aos_itemid");
		StringJoiner str = new StringJoiner(",");

		str.add("aos_linentity.aos_keyword aos_keyword");
		str.add("aos_linentity.aos_sort aos_sort");
		str.add("aos_linentity.aos_search aos_search");
		str.add("aos_linentity.aos_apply aos_apply");
		str.add("aos_linentity.aos_attribute aos_attribute");
		str.add("aos_linentity.aos_remake aos_remake");

		QFBuilder builder = new QFBuilder();
		builder.add("aos_orgid", QCP.equals, aos_orgid.getString("id"));
		builder.add("aos_category1", "=", getModel().getValue("aos_category1"));
		builder.add("aos_category2", "=", getModel().getValue("aos_category2"));
		builder.add("aos_category3", "=", getModel().getValue("aos_category3"));
		builder.add("aos_itemnamecn", "=", getModel().getValue("aos_itemname"));
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_point", str.toString(), builder.toArray());
		if (list == null || list.size() == 0) {
			this.getView().showTipNotification("无可用数据!");
			return;
		}

		// 删除原来的
		this.getModel().deleteEntryData("aos_entryentity");
		// 取出关键词 赋值到关键词单据体
		for (DynamicObject obj : list) {
			int index = this.getModel().createNewEntryRow("aos_entryentity");
			getModel().setValue("aos_mainvoc", obj.get("aos_keyword"), index);
			getModel().setValue("aos_sort", obj.get("aos_sort"), index);
			getModel().setValue("aos_search", obj.get("aos_search"), index);
			getModel().setValue("aos_apply", obj.get("aos_apply"), index);
			getModel().setValue("aos_attribute", obj.get("aos_attribute"), index);
			getModel().setValue("aos_attribute", obj.get("aos_attribute"), index);
			getModel().setValue("aos_remake", obj.get("aos_remake"), index);
		}

		// 删除原来的
		str = new StringJoiner(",");
		str.add("aos_linentity_gw.aos_gw_keyword aos_gw_keyword");
		str.add("aos_linentity_gw.aos_gw_search aos_gw_search");
		DynamicObjectCollection listgw = QueryServiceHelper.query("aos_mkt_point", str.toString(), builder.toArray());
		if (list == null || list.size() == 0) {
			this.getView().showTipNotification("无官网可用数据!");
			return;
		}
		
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

	// 保存前事件
	private void beforeSave() {
		DynamicObjectCollection sourcEntity = this.getModel().getDataEntity(true)
				.getDynamicObjectCollection("aos_entryentity");
		DynamicObjectCollection copyEntity = this.getModel().getDataEntity(true)
				.getDynamicObjectCollection("aos_entryentity1");
		copyEntity.removeIf(dy -> true);
		for (DynamicObject sourceRow : sourcEntity) {
			DynamicObject newRow = copyEntity.addNew();
			newRow.set("aos_pr_keyword", sourceRow.get("aos_mainvoc"));
			newRow.set("aos_pr_sort", sourceRow.get("aos_sort"));
			newRow.set("aos_pr_search", sourceRow.get("aos_search"));
			newRow.set("aos_pr_employ", sourceRow.get("aos_employ"));
			newRow.set("aos_pr_state", sourceRow.get("aos_promote"));
			newRow.set("aos_pr_lable", sourceRow.get("aos_attribute"));
		}
		getView().updateView("aos_entryentity1");
	}

}
