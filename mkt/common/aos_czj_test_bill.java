package mkt.common;

import java.io.PrintWriter;
import java.io.StringWriter;
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
		StringJoiner str = new StringJoiner(",");
		str.add("id");
		str.add("aos_linentity.aos_type");	//类型
		str.add("aos_linentity.aos_remake");	//备注
		str.add("aos_linentity.aos_rate");	//同款占比
		str.add("aos_linentity.aos_apply");	//应用
		str.add("aos_linentity.aos_correlate");	//新相关性
		str.add("modifytime");

		Date date = new Date();
		DynamicObject[] mktPoints = BusinessDataServiceHelper.load("aos_mkt_point", str.toString(), null);
		for (DynamicObject point : mktPoints) {
			DynamicObjectCollection lineEntitys = point.getDynamicObjectCollection("aos_linentity");
			for (DynamicObject lineRow : lineEntitys) {
				//获取备注
				StringJoiner desc = new StringJoiner(",");
				if (FndGlobal.IsNotNull(lineRow.getString("aos_remake"))){
					desc.add(lineRow.getString("aos_remake"));
				}

				//类型对应应用
				String aos_type = lineRow.getString("aos_type");
				if (FndGlobal.IsNotNull(aos_type)){
					//核心关键词对应标题
					if (aos_type.equals("核心关键词")){
						lineRow.set("aos_apply","标题");
					}
					//主要关键词对应Listing前台
					else if (aos_type.equals("主要关键词")){
						lineRow.set("aos_apply","Listing前台");
					}
					else {
						desc.add(aos_type);
					}
					point.set("modifytime",date);
				}

				//相关性是数值则保留（其他移到备注）
				String aos_relate = lineRow.getLocaleString("aos_correlate").getLocaleValue_zh_CN();
				if (FndGlobal.IsNotNull(aos_relate)){
					ILocaleString aos_correlate = lineRow.getLocaleString("aos_correlate");
					//是数字
					if (figure(aos_relate)) {
						aos_correlate.setLocaleValue_zh_CN(aos_relate);
						aos_correlate.setLocaleValue_en(aos_relate);
					}
					//不是则放到备注
					else {
						aos_correlate.setLocaleValue_en("");
						aos_correlate.setLocaleValue_zh_CN("");
						desc.add(aos_relate);
					}
					lineRow.set("aos_correlate",aos_correlate);
					point.set("modifytime",date);
				}

				//同款占比移到相关性
				Object aos_rate = lineRow.get("aos_rate");
				if (FndGlobal.IsNotNull(aos_rate)){
					ILocaleString aos_correlate = lineRow.getLocaleString("aos_correlate");
					aos_correlate.setLocaleValue_en(aos_rate.toString());
					aos_correlate.setLocaleValue_zh_CN(aos_rate.toString());
					lineRow.set("aos_correlate",aos_correlate);
					point.set("modifytime",date);
				}

				if (str.length()>0){
					lineRow.set("aos_remake",desc.toString());
					point.set("modifytime",date);
				}
			}
		}
		SaveServiceHelper.save(mktPoints);
	}

	public static boolean figure(String str) {
		String regex = "[-+]?\\d*\\.?\\d+";
		return str.matches(regex);
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