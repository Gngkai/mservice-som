package mkt.data.global;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.bill.BillShowParameter;
import kd.bos.bill.OperationStatus;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.ShowType;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.CellClickEvent;
import kd.bos.form.control.events.CellClickListener;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
import kd.bos.form.operate.FormOperate;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

public class aos_mkt_global_form extends AbstractFormPlugin
		implements RowClickEventListener, CellClickListener, HyperLinkClickListener {

	private final static String Point = "P";
	private final static String Point_Bill = "aos_mkt_point";
	private final static String Standard = "S";
	private final static String Standard_Bill = "aos_mkt_standard";
	private final static String Slogan = "Slogan";
	private final static String Slogan_Bill = "aos_mkt_data_slogan";

	public void registerListener(EventObject e) {
		super.registerListener(e);
		// 给工具栏加监听事件
		this.addItemClickListeners("aos_toolbarap");
		EntryGrid aos_entryentity = this.getControl("aos_entryentity");
		aos_entryentity.addCellClickListener(this);
		aos_entryentity.addRowClickListener(this);
		aos_entryentity.addHyperClickListener(this);
	}

	public void afterDoOperation(AfterDoOperationEventArgs args) {
		super.afterDoOperation(args);
		FormOperate formOperate = (FormOperate) args.getSource();
		if (formOperate.getOperateKey().equals("aos_query")) {
			aos_query();
		}
	}

	private void aos_query() {
		this.getModel().deleteEntryData("aos_entryentity");
		Object aos_text = this.getModel().getValue("aos_text");
		if (aos_text == null || aos_text.toString().equals("") || aos_text.toString().equals("null")) {
			this.getView().showTipNotification("文本不能为空!");
			return;
		} else {
			String aos_text_str = aos_text.toString();
			int i = 0;
			query_all(aos_text_str, i);
		}
	}

	private void query_all(String aos_text_str, int i) {
		// 获取人员国别权限
		long CurrentUserId = UserServiceHelper.getCurrentUserId();
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_userights", "entryentity.aos_orgid aos_orgid",
				new QFilter[] { new QFilter("aos_user", QCP.equals, CurrentUserId) });
		List<String> orgList = new ArrayList<>();
		for (DynamicObject obj : list) {
			orgList.add(obj.getString("aos_orgid"));
		}
		QFilter QFilter_RightS = new QFilter("aos_orgid", QCP.in, orgList);
		// 关键词库头查询
		i = Common_Query(i, aos_text_str, "aos_orgid.number", "国别", Point, Point_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_category1", "大类", Point, Point_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_category2", "中类", Point, Point_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_category3", "小类", Point, Point_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_itemnamecn", "品名", Point, Point_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_points", "核心词", Point, Point_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_egsku.number", "举例SKU", Point, Point_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_linentity.aos_mainvoc", "关键词", Point, Point_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_linentity.aos_type", "类型", Point, Point_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_linentity.aos_relate", "相关性", Point, Point_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_linentity.aos_rate", "同款占比", Point, Point_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_linentity.aos_counts", "搜索量", Point, Point_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_linentity.aos_source", "来源", Point, Point_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_linentity.aos_adress", "应用位置", Point, Point_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_pointses", "ES核心词", Point, Point_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_pointsfr", "FR核心词", Point, Point_Bill, QFilter_RightS);
		// 国别文案标准库头查询
		i = Common_Query(i, aos_text_str, "aos_orgid.number", "1、产品资料 国别", Standard, Standard_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_category1", "1、产品资料 大类", Standard, Standard_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_category2", "1、产品资料 中类", Standard, Standard_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_category3", "1、产品资料 小类", Standard, Standard_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_itemnamecn", "1、产品资料 中文品名", Standard, Standard_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_itemnameen", "1、产品资料 英文品名", Standard, Standard_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_user.name", "1、产品资料 品类编辑", Standard, Standard_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_detail", "1、产品资料 属性细分", Standard, Standard_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_salesentity.aos_salesl", "2、产品卖点 卖点", Standard, Standard_Bill,
				QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_salesentity.aos_saledetaill", "2、产品卖点 卖点细分-CN", Standard, Standard_Bill,
				QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_salesentity.aos_bulletl", "2、产品卖点 Bullet Points-EN", Standard,
				Standard_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_salesentity.aos_featuresl", "2、产品卖点 Features-EN", Standard, Standard_Bill,
				QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_descentity.aos_contain", "3、长描述 内容", Standard, Standard_Bill,
				QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_para", "4、参数", Standard, Standard_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_warnentity.aos_warncnl", "5、警示语Note 中文", Standard, Standard_Bill,
				QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_warnentity.aos_warnusl", "5、警示语Note 英语", Standard, Standard_Bill,
				QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_titleentity.aos_platformid.name", "6、标题 英语", Standard, Standard_Bill,
				QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_titleentity.aos_standard_definel", "6、标题 标准定义", Standard, Standard_Bill,
				QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_titleentity.aos_classic_definel", "6、标题 经典定义", Standard, Standard_Bill,
				QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_colour", "7、综合帖选项类型 颜色", Standard, Standard_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_size", "7、综合帖选项类型 尺寸", Standard, Standard_Bill, QFilter_RightS);
		i = Common_Query(i, aos_text_str, "aos_desc", "8、备注Tips", Standard, Standard_Bill, QFilter_RightS);
		// 品名Slogan库
		i = Common_Query(i, aos_text_str, "aos_category1", "大类", Slogan, Slogan_Bill, null);
		i = Common_Query(i, aos_text_str, "aos_category2", "中类", Slogan, Slogan_Bill, null);
		i = Common_Query(i, aos_text_str, "aos_category3", "小类", Slogan, Slogan_Bill, null);
		i = Common_Query(i, aos_text_str, "aos_user.number", "英语编辑", Slogan, Slogan_Bill, null);
		i = Common_Query(i, aos_text_str, "aos_itemnamecn", "CN 品名", Slogan, Slogan_Bill, null);
		i = Common_Query(i, aos_text_str, "aos_slogancn", "CN Slogan", Slogan, Slogan_Bill, null);
		i = Common_Query(i, aos_text_str, "aos_itemid.number", "参考SKU", Slogan, Slogan_Bill, null);
		i = Common_Query(i, aos_text_str, "aos_itemnameen", "EN 品名", Slogan, Slogan_Bill, null);
		i = Common_Query(i, aos_text_str, "aos_sloganen", "EN Slogan", Slogan, Slogan_Bill, null);
		i = Common_Query(i, aos_text_str, "aos_itemnamede", "DE 品名", Slogan, Slogan_Bill, null);
		i = Common_Query(i, aos_text_str, "aos_slogande", "DE Slogan", Slogan, Slogan_Bill, null);
		i = Common_Query(i, aos_text_str, "aos_itemnamefr", "FR 品名", Slogan, Slogan_Bill, null);
		i = Common_Query(i, aos_text_str, "aos_sloganfr", "FR Slogan", Slogan, Slogan_Bill, null);
		i = Common_Query(i, aos_text_str, "aos_itemnameit", "IT 品名", Slogan, Slogan_Bill, null);
		i = Common_Query(i, aos_text_str, "aos_sloganit", "IT Slogan", Slogan, Slogan_Bill, null);
		i = Common_Query(i, aos_text_str, "aos_itemnamees", "ES 品名", Slogan, Slogan_Bill, null);
		i = Common_Query(i, aos_text_str, "aos_sloganes", "ES Slogan", Slogan, Slogan_Bill, null);
	}

	private int Common_Query(int i, String aos_text_str, String sign, String name, String data, String bill,
			QFilter qFilter_RightS) {
		QFilter filter_com = new QFilter(sign, "like", "%" + aos_text_str + "%");
		QFilter[] filters_com = new QFilter[] { filter_com, qFilter_RightS };
		String select_com = "id," + sign;
		DataSet COMS = QueryServiceHelper.queryDataSet("Common_Query.COMS" + data + sign, bill, select_com, filters_com,
				null);
		while (COMS.hasNext()) {
			Row COM = COMS.next();
			String aos_contain = COM.getString(sign);
			long aos_id = COM.getLong("id");
			this.getModel().createNewEntryRow("aos_entryentity");
			this.getModel().setValue("aos_data", data, i);
			this.getModel().setValue("aos_name", name, i);
			this.getModel().setValue("aos_contain", aos_contain, i);
			this.getModel().setValue("aos_dataid", aos_id, i);
			i++;
		}
		COMS.close();
		return i;
	}

	@Override
	public void cellClick(CellClickEvent arg0) {
	}

	@Override
	public void cellDoubleClick(CellClickEvent arg0) {
	}

	@Override
	public void hyperLinkClick(HyperLinkClickEvent arg0) {
		String name = arg0.getFieldName().toString();
		int row = arg0.getRowIndex();
		if (name.equals("aos_data") && (row != -1)) {
			BillShowParameter showParameter = new BillShowParameter();
			Object pkId = this.getModel().getValue("aos_dataid", row);
			String aos_data = (String) this.getModel().getValue("aos_data");
			if (aos_data.equals("P"))
				showParameter.setFormId(Point_Bill);
			else if (aos_data.equals("S"))
				showParameter.setFormId(Standard_Bill);
			else if (aos_data.equals("Slogan"))
				showParameter.setFormId(Slogan_Bill);
			showParameter.getOpenStyle().setShowType(ShowType.Modal);
			showParameter.setPkId(pkId);
			showParameter.setStatus(OperationStatus.EDIT);
			this.getView().showForm(showParameter);
		}
	}
}
