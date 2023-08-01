package mkt.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
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
				keyWordSync();
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
		DynamicObjectCollection dyc = QueryServiceHelper.query("aos_czj_tmp", "aos_string1,aos_string2,aos_string3", null);
		Map<String, List<DynamicObject>> map_data = dyc.stream()
				.collect(Collectors.groupingBy(dy -> dy.getString("aos_string1")));
		List<String> list = new ArrayList<>(map_data.keySet());
		Map<String, String> map_rowData = dyc.stream()
				.collect(Collectors.toMap(
						dy -> dy.getString("aos_string2"),
						dy -> dy.getString("aos_string3"),
						(old, newvalue) -> old
				));


		QFBuilder builder = new QFBuilder();
		builder.add("id", QFilter.in,list);
		StringJoiner str = new StringJoiner(",");
		str.add("aos_itemnamecn");
		str.add("id");
		str.add("aos_linentity.id");
		str.add("aos_linentity.aos_correlate");
		List<DynamicObject> update = new ArrayList<>(dyc.size());
		DynamicObject[] mkt_points = BusinessDataServiceHelper.load("aos_mkt_point", str.toString(), builder.toArray());
		for (DynamicObject dy : mkt_points) {
			DynamicObjectCollection aos_linentity = dy.getDynamicObjectCollection("aos_linentity");
			for (DynamicObject entity : aos_linentity) {
				String entityid = entity.getPkValue().toString();
				if (map_rowData.containsKey(entityid)) {
					String value = map_rowData.get(entityid);
					ILocaleString correlate = entity.getLocaleString("aos_correlate");
					correlate.setLocaleValue_zh_CN(value);
					if (value.contains("高")||value.contains("强")){
						correlate.setLocaleValue_en("High");
					}
					else if (value.contains("中")){
						correlate.setLocaleValue_en("Medium");
					}
					else if (value.contains("低")||value.contains("弱")){
						correlate.setLocaleValue_en("Low");
					}
					else {
						correlate.setLocaleValue_en(value);
					}
					entity.set("aos_correlate",correlate);

				}
			}
			update.add(dy);
		}
		SaveServiceHelper.save(mkt_points);
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