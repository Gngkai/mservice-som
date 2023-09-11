package mkt.progress.photo;

import java.util.*;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndHistory;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;
import mkt.common.MKTS3PIC;
import mkt.progress.ProgressUtil;
import mkt.progress.design.aos_mkt_designreq_bill;
import mkt.progress.iface.iteminfo;

public class aos_mkt_pslist_bill extends AbstractBillPlugIn implements ItemClickListener {

	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		StatusControl();
	}

	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		StatusControl();
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		try {
			if ("aos_submit".equals(Control))
				aos_submit();// 提交
			StatusControl();
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	/** 全局状态控制 **/
	private void StatusControl() {
		// 数据层
		Object aos_status = this.getModel().getValue("aos_status");
		DynamicObject aos_user = (DynamicObject) this.getModel().getValue("aos_designer");
		Object CurrentUserId = UserServiceHelper.getCurrentUserId();
		Object CurrentUserName = UserServiceHelper.getUserInfoByID((long) CurrentUserId).get("name");
		// 当前节点操作人不为当前用户 全锁
		if (!aos_user.getPkValue().toString().equals(CurrentUserId.toString())
				&& !"刘中怀".equals(CurrentUserName.toString()) && !"程震杰".equals(CurrentUserName.toString())
				&& !"陈聪".equals(CurrentUserName.toString())) {
			this.getView().setEnable(false, "titlepanel");// 标题面板
			this.getView().setEnable(false, "contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");
		}

		if ("已完成".equals(aos_status)) {
			this.getView().setVisible(false, "aos_submit");
			this.getView().setVisible(false, "bar_save");
			this.getView().setEnable(false, "titlepanel");// 标题面板
			this.getView().setEnable(false, "contentpanelflex");// 主界面面板
		}
	}

	private void aos_submit() {
		System.out.println("===into aos_submit===");
		String aos_status = this.getModel().getValue("aos_status").toString();
		switch (aos_status) {
		case "新建":
			this.getModel().setValue("aos_status", "抠图中");
			break;
		case "抠图中":
			this.getModel().setValue("aos_status", "已完成");
			GenerateDesign();
			break;
		}

		// 同步拍照需求表
		SyncPhotoReq();

		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");
	}

	private void SyncPhotoReq() {
		Object aos_sourceid = this.getModel().getValue("aos_sourceid");
		String aos_status = this.getModel().getValue("aos_status").toString();
		if (aos_sourceid == null)
			return;
		// 同步拍照需求表
		DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(aos_sourceid, "aos_mkt_photoreq");
		Object aos_sourceidlist = aos_mkt_photoreq.get("aos_sourceid");
		// 同步拍照任务清单
		if (QueryServiceHelper.exists("aos_mkt_photolist",aos_sourceidlist)){
			DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(aos_sourceidlist, "aos_mkt_photolist");
			if ("已完成".equals(aos_status)) {
				aos_mkt_photoreq.set("aos_picdate", new Date());
				aos_mkt_photolist.set("aos_picdate", new Date());
			}
			OperationServiceHelper.executeOperate("save", "aos_mkt_photolist", new DynamicObject[] { aos_mkt_photolist },
					OperateOption.create());
		}

		OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq", new DynamicObject[] { aos_mkt_photoreq },
				OperateOption.create());

	}

	private void GenerateDesign() {
		// 数据层
		Object ReqFId = this.getModel().getValue("aos_sourceid");

		// 获取对应拍照需求表是否为已完成
		DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(ReqFId, "aos_mkt_photoreq");
		// 如果不为已完成 则不生成设计需求表 等待拍照需求表完成后再生成设计需求表
		if (aos_mkt_photoreq == null || !"已完成".equals(aos_mkt_photoreq.getString("aos_status")))
			return;

		Object AosShipDate = aos_mkt_photoreq.get("aos_shipdate");
		Object aos_billno = aos_mkt_photoreq.get("billno");
		Object aos_requireby = aos_mkt_photoreq.get("aos_requireby");
		Object AosDesigner = aos_mkt_photoreq.get("aos_designer");
		Object AosDeveloperId = aos_mkt_photoreq.getDynamicObject("aos_developer").getPkValue();
		boolean aos_newitem = aos_mkt_photoreq.getBoolean("aos_newitem");
		Object ItemId = aos_mkt_photoreq.getDynamicObject("aos_itemid").getPkValue();
		Object aos_requirebyid  = ((DynamicObject)aos_requireby).getPkValue();
		String MessageId = AosDeveloperId + "";

		// 初始化
		DynamicObject aos_mkt_designreq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designreq");
		aos_mkt_designreq.set("aos_requiredate", new Date());
		if (aos_newitem)
			aos_mkt_designreq.set("aos_type", "新品设计");
		else
			aos_mkt_designreq.set("aos_type", "老品重拍");
		aos_mkt_designreq.set("aos_shipdate", AosShipDate);
		aos_mkt_designreq.set("aos_orignbill", aos_billno);
		aos_mkt_designreq.set("aos_sourceid", ReqFId);
		aos_mkt_designreq.set("aos_status", "拍照功能图制作");
		aos_mkt_designreq.set("aos_user", AosDesigner);
		aos_mkt_designreq.set("aos_requireby", aos_requireby);
		aos_mkt_designreq.set("aos_sourcetype", "PHOTO");

		mkt.progress.design.aos_mkt_designreq_bill.setEntityValue(aos_mkt_designreq);

		try {
			List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(aos_requirebyid);
			if (MapList != null) {
				if (MapList.get(2) != null)
					aos_mkt_designreq.set("aos_organization1", MapList.get(2).get("id"));
				if (MapList.get(3) != null)
					aos_mkt_designreq.set("aos_organization2", MapList.get(3).get("id"));
			}
		} catch (Exception ex) {
		}

		// 产品类别
		String category = (String) SalUtil.getCategoryByItemId(ItemId + "").get("name");
		String[] category_group = category.split(",");
		String AosCategory1 = null;
		String AosCategory2 = null;
		int category_length = category_group.length;
		if (category_length > 0)
			AosCategory1 = category_group[0];
		if (category_length > 1)
			AosCategory2 = category_group[1];
		// 根据大类中类获取对应营销人员
		if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
			QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
			QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
			QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2 };
			String SelectStr = "aos_pser aos_designer,aos_designeror,aos_3d";
			DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_proguser", SelectStr,
					filters_category);
			if (aos_mkt_proguser != null) {
				aos_mkt_designreq.set("aos_designer", aos_mkt_proguser.get("aos_designer"));
				aos_mkt_designreq.set("aos_dm", aos_mkt_proguser.get("aos_designeror"));
				aos_mkt_designreq.set("aos_3der", aos_mkt_proguser.get("aos_3d"));
			}
		}

		DynamicObjectCollection aos_entryentityS = aos_mkt_designreq.getDynamicObjectCollection("aos_entryentity");
		String aos_contrybrandStr = "";
		String aos_orgtext = "";
		DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(ItemId, "bd_material");
		DynamicObjectCollection aos_contryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
		// 获取所有国家品牌 字符串拼接 终止
		Set<String> set_bra = new HashSet<>();
		for (DynamicObject aos_contryentry : aos_contryentryS) {
			DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
			String aos_nationalitynumber = aos_nationality.getString("number");
			if ("IE".equals(aos_nationalitynumber))
				continue;
			Object org_id = aos_nationality.get("id"); // ItemId
			int OsQty = iteminfo.GetItemOsQty(org_id, ItemId);
			int SafeQty = iteminfo.GetSafeQty(org_id);
			if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
				continue;
			aos_orgtext = aos_orgtext + aos_nationalitynumber + ";";

			Object obj = aos_contryentry.getDynamicObject("aos_contrybrand");
			if (obj == null)
				continue;
			String value = aos_contryentry.getDynamicObject("aos_contrybrand").getString("number");
//			if (!aos_contrybrandStr.contains(value))
//				aos_contrybrandStr = aos_contrybrandStr + value + ";";

			if(value != null)
				set_bra.add(value);
			if(set_bra.size() > 1){
				if (!aos_contrybrandStr.contains(value))
					aos_contrybrandStr = aos_contrybrandStr + value + ";";
			}
			else if(set_bra.size() == 1){
				aos_contrybrandStr = value;
			}
		}
		String item_number = bd_material.getString("number");
		String url = "https://clss.s3.amazonaws.com/" + item_number + ".jpg";// 图片字段
		String aos_productno = bd_material.getString("aos_productno");
		String aos_itemname = bd_material.getString("name");
		// 获取同产品号物料
		QFilter filter_productno = new QFilter("aos_productno", QCP.equals, aos_productno);
		QFilter[] filters = new QFilter[] { filter_productno };
		String SelectColumn = "number,aos_type";
		String aos_broitem = "";
		DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", SelectColumn, filters);
		for (DynamicObject bd : bd_materialS) {
			if ("B".equals(bd.getString("aos_type")))
				continue; // 配件不获取
			String number = bd.getString("number");
			if (item_number.equals(number))
				continue;
			else
				aos_broitem = aos_broitem + number + ";";
		}

		DynamicObject aos_entryentity = aos_entryentityS.addNew();
		aos_entryentity.set("aos_itemid", ItemId);
		aos_entryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(ItemId));
		DynamicObjectCollection aos_subentryentityS = aos_entryentity.getDynamicObjectCollection("aos_subentryentity");

		DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
		aos_subentryentity.set("aos_sub_item",ItemId);
		aos_subentryentity.set("aos_segment3", aos_productno);
		aos_subentryentity.set("aos_itemname", aos_itemname);
		aos_subentryentity.set("aos_brand", aos_contrybrandStr);
		aos_subentryentity.set("aos_pic", url);
		aos_subentryentity.set("aos_developer", bd_material.get("aos_developer"));// 开发
		aos_subentryentity.set("aos_seting1", bd_material.get("aos_seting_cn"));
		aos_subentryentity.set("aos_seting2", bd_material.get("aos_seting_en"));
		aos_subentryentity.set("aos_spec", bd_material.get("aos_specification_cn"));
		aos_subentryentity.set("aos_url", MKTS3PIC.GetItemPicture(item_number));
		aos_subentryentity.set("aos_broitem", aos_broitem);
		aos_subentryentity.set("aos_orgtext", aos_orgtext);

		aos_mkt_designreq_bill.createDesiginBeforeSave(aos_mkt_designreq);
		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
				new DynamicObject[] { aos_mkt_designreq }, OperateOption.create());
		if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			MKTCom.SendGlobalMessage(MessageId, "aos_mkt_photoreq", operationrst.getSuccessPkIds().get(0) + "",
					aos_mkt_designreq.getString("billno"), "设计需求表-拍照新品自动创建");
			FndHistory.Create(aos_mkt_designreq, aos_mkt_designreq.getString("aos_status"),
					"设计需求表-拍照新品自动创建");
		}

	}

}