package mkt.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import common.sal.util.SaveUtils;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.ILocaleString;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.control.Control;
import kd.bos.form.control.events.ClickListener;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.util.ExceptionUtils;
import kd.fi.bd.util.QFBuilder;
import kd.scm.pur.opplugin.util.SaveUtil;
import mkt.synciface.aos_mkt_item_sync;

public class aos_czj_test_bill extends AbstractBillPlugIn implements ItemClickListener, ClickListener {

	public void registerListener(EventObject e) {
		super.registerListener(e);
		// 给工具栏加监听事件
		this.addItemClickListeners("aos_toolbarap");
		this.addItemClickListeners("aos_test");
	}
	private static Log log = LogFactory.getLog("mkt.common.aos_czj_test_bill");
	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		if (Control.equals("aos_test")){
			log.info("迁移开始： {}"+ LocalDateTime.now().toString());
			try {
				syncItemKeyword();
			}catch (Exception e){
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				log.error(sw.toString());
			}
			log.info("迁移结束： {}"+ LocalDateTime.now().toString());
		}
	}

	private void aos_test() {
		StringJoiner message = new StringJoiner(";");
		//设置设计需求表
		QFBuilder qfBuilder = new QFBuilder();
		qfBuilder.add("aos_status","!=","结束");
		StringJoiner str = new StringJoiner(",");
		str.add("id");
		str.add("aos_user");
		str.add("aos_userorganizat1");
		str.add("aos_userorganizat2");
		str.add("aos_userorganizat3");
		DynamicObject[] aos_mkt_designreqs = BusinessDataServiceHelper.load("aos_mkt_designreq", str.toString(), qfBuilder.toArray());
		for (DynamicObject dy : aos_mkt_designreqs) {
			mkt.progress.design.aos_mkt_designreq_bill.setEntityValue(dy);
		}
		SaveServiceHelper.update(aos_mkt_designreqs);
		message.add("设计需求表修改完成");
		//文案
		DynamicObject[] aos_mkt_listing_sons = BusinessDataServiceHelper.load("aos_mkt_listing_son", str.toString(), qfBuilder.toArray());
		for (DynamicObject dy : aos_mkt_listing_sons) {
			mkt.progress.listing.aos_mkt_listingson_bill.setListSonUserOrganizate(dy);
		}
		SaveServiceHelper.update(aos_mkt_listing_sons);
		message.add("文案修改完成");
		this.getView().showMessage(message.toString());
	}

	private void keyWordSync(){
		StringJoiner str = new StringJoiner(",");
		DecimalFormat df = new DecimalFormat("#.####"); // 定义格式化模式，最多保留4位小数
		str.add("id");
		str.add("modifytime");
		str.add("aos_linentity.aos_correlate");	//新相关性

		Date date = new Date();
		DynamicObject[] mktPoints = BusinessDataServiceHelper.load("aos_mkt_point", str.toString(), null);
		for (DynamicObject point : mktPoints) {
			DynamicObjectCollection lineEntitys = point.getDynamicObjectCollection("aos_linentity");
			for (DynamicObject lineRow : lineEntitys) {
				//相关性是数值则保留（其他移到备注）
				String aos_relate = lineRow.getLocaleString("aos_correlate").getLocaleValue_zh_CN();
				if (FndGlobal.IsNotNull(aos_relate)){
					ILocaleString aos_correlate = lineRow.getLocaleString("aos_correlate");
					//是数字
					if (figure(aos_relate)) {
						String format = df.format(Double.parseDouble(aos_relate));
						if (format.equals("0")){
							format ="";
						}
						aos_correlate.setLocaleValue_zh_CN(format);
						aos_correlate.setLocaleValue_en(format);
						lineRow.set("aos_correlate",aos_correlate);
						point.set("modifytime",date);
					}
				}
			}
		}
		SaveServiceHelper.save(mktPoints);
	}

	public static boolean figure(String str) {
		String regex = "[-+]?\\d*\\.?\\d+";
		return str.matches(regex);
	}


	private Map<String, String> getAllItemCategory() {
		QFilter qFilter = new QFilter("standard.number", QCP.equals, "JBFLBZ");
		String selectFields = "material,group.name categoryname";
		DynamicObjectCollection list = QueryServiceHelper.query("bd_materialgroupdetail", selectFields, qFilter.toArray());
		return list.stream().collect(Collectors.toMap(
				obj -> obj.getString("material"),
				obj -> obj.getString("categoryname"),
				(k1, k2) -> k1));
	}

	private void syncItemKeyword() {
		Map<String, String> allItemCategory = getAllItemCategory();
		String selectFields = "aos_org aos_orgid," +
				"aos_item aos_itemid," +
				"aos_item.name aos_itemname";
		DynamicObjectCollection list = QueryServiceHelper.query("aos_czj_tmp", selectFields, null);

		// 查询关键词库中已有的数据
		DynamicObjectCollection aos_mkt_keyword = QueryServiceHelper.query("aos_mkt_keyword", "aos_orgid,aos_itemid", null);
		Set<String> keywordExists = new HashSet<>();
		for (DynamicObject obj:aos_mkt_keyword) {
			String aos_orgid = obj.getString("aos_orgid");
			String aos_itemid = obj.getString("aos_itemid");
			keywordExists.add(aos_orgid + "~" + aos_itemid);
		}

		List<DynamicObject> savEntity = new ArrayList<>(list.size());
		for (DynamicObject obj:list) {
			String aos_orgid = obj.getString("aos_orgid");
			String aos_itemid = obj.getString("aos_itemid");
			String aos_itemname = obj.getString("aos_itemname");
			String aos_category1 = "";
			String aos_category2 = "";
			String aos_category3 = "";

			// 如果SKU关键词库中已存在
			if (keywordExists.contains(aos_orgid + "~" + aos_itemid)) continue;

			String aos_category = allItemCategory.get(aos_itemid);
			if (aos_category == null) continue;
			String[] categoryArr = aos_category.split(",");
			if (categoryArr.length > 0) {
				aos_category1 =  categoryArr[0];
			}
			if (categoryArr.length > 1) {
				aos_category2 =  categoryArr[1];
			}
			if (categoryArr.length > 2) {
				aos_category3 =  categoryArr[2];
			}

			// 新建一单
			DynamicObject itemKeywordObj = BusinessDataServiceHelper.newDynamicObject("aos_mkt_keyword");
			itemKeywordObj.set("billstatus", "A");
			itemKeywordObj.set("aos_orgid", aos_orgid);
			itemKeywordObj.set("aos_itemid", aos_itemid);
			itemKeywordObj.set("aos_itemname", aos_itemname);
			itemKeywordObj.set("aos_category1", aos_category1);
			itemKeywordObj.set("aos_category2", aos_category2);
			itemKeywordObj.set("aos_category3", aos_category3);
			savEntity.add(itemKeywordObj);
		}
		SaveUtils.SaveEntity("aos_mkt_keyword",savEntity,true);
	}




	public void beforePropertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		System.out.println("name =" + name);
		if (name.equals("aos_test_item")) {
			System.out.println("====into before aos_test_item====");
			System.out.println(this.getModel().getValue(name));
		}
	}

	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		System.out.println("name =" + name);
		if (name.equals("aos_test_item")) {
			System.out.println("====into aos_test_item====");
			System.out.println(this.getModel().getValue(name));
		}
	}

}