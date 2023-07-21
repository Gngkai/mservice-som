package mkt.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import common.sal.util.SaveUtils;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.control.Control;
import kd.bos.form.control.events.ClickListener;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.fi.bd.util.QFBuilder;
import kd.scm.pur.opplugin.util.SaveUtil;
import mkt.synciface.aos_mkt_item_sync;
import scala.Dynamic;

public class aos_czj_test_bill extends AbstractBillPlugIn implements ItemClickListener, ClickListener {

	public void registerListener(EventObject e) {
		super.registerListener(e);
		// 给工具栏加监听事件
		this.addItemClickListeners("aos_toolbarap");
		this.addItemClickListeners("aos_test");
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		if (Control.equals("aos_test"))
			aos_test();
	}
	/** 同步敏感词库 **/
	private void aos_test() {
		Log log = LogFactory.getLog("mkt.common.aos_czj_test_bill");
		log.info("敏感词同步开始：{}"+ LocalDateTime.now().toString());
		try {
			//查找敏感词信息
			QFBuilder builder = new QFBuilder();
			builder.add("aos_string2","!=","");
			StringJoiner str = new StringJoiner(",");
			for (int i = 1; i <= 10; i++) {
				str.add("aos_string"+i);
			}
			DynamicObjectCollection dyc = QueryServiceHelper.query("aos_czj_tmp", str.toString(), builder.toArray());
			List<DynamicObject> saveEntity = new ArrayList<>(dyc.size());
			//新建单据
			DynamicObjectType type = BusinessDataServiceHelper.newDynamicObject("aos_mkt_sensitive").getDynamicObjectType();
			for (DynamicObject dy : dyc) {
				DynamicObject dy_new = new DynamicObject(type);
				dy_new.set("billstatus","A");
				dy_new.set("aos_words",dy.get("aos_string2"));
				dy_new.set("aos_type",dy.get("aos_string3"));
				dy_new.set("aos_replace",dy.get("aos_string4"));
				dy_new.set("aos_level",dy.get("aos_string5"));

				String org = dy.getString("aos_string1");
				DynamicObjectCollection entity = dy_new.getDynamicObjectCollection("entryentity");
				if (org.equals("CN")){
					DynamicObject subNew = entity.addNew();
					subNew.set("aos_whole",type);
				}
				else {
					String[] split = org.split("/");
					for (String value : split) {
						DynamicObject subNew = entity.addNew();
						subNew.set("aos_lan",value);
						subNew.set("aos_cate1",dy.get("aos_string6"));
						subNew.set("aos_cate2",dy.get("aos_string7"));
						subNew.set("aos_cate3",dy.get("aos_string8"));
						subNew.set("aos_name",dy.get("aos_string9"));
					}
				}
				saveEntity.add(dy_new);
			}
			SaveUtils.SaveEntity("aos_mkt_sensitive",saveEntity,true);

		}catch (Exception e){
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			log.error(sw.toString());
			e.printStackTrace();
		}
		log.info("敏感词同步结束：{}"+ LocalDateTime.now().toString());

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