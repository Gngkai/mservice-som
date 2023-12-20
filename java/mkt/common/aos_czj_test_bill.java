package mkt.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import common.CommonDataSom;
import common.fnd.FndGlobal;

import common.sal.sys.basedata.dao.CountryDao;
import common.sal.sys.basedata.dao.impl.CountryDaoImpl;
import common.sal.util.QFBuilder;
import common.sal.util.SaveUtils;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.ILocaleString;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;

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
			log.info("迁移开始： {}",LocalDateTime.now());
			try {
				syncSkuData();
			}catch (Exception e){
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				log.error(sw.toString());
			}
			log.info("迁移结束： {}", LocalDateTime.now());
		}
	}

	//同步sku词库
	public void syncSkuData(){
		List<DynamicObject> saveList = new ArrayList<>(5000);
		QFBuilder builder = new QFBuilder();
		builder.add("aos_orgid","!=","");
		builder.add("aos_category1","!=","");
		builder.add("aos_category2","!=","");
		builder.add("aos_category3","!=","");
		builder.add("aos_itemnamecn","!=","");
		for (DynamicObject dy : QueryServiceHelper.query("aos_mkt_point", "id", builder.toArray())) {
			DynamicObject dy_main = BusinessDataServiceHelper.loadSingle(dy.get("id"), "aos_mkt_point");
			setItemEntity(dy_main);
			saveList.add(dy_main);
			SaveUtils.SaveEntity(saveList,false);
		}
		SaveUtils.SaveEntity(saveList,true);

	}
	//设置sku清单
	private static void setItemEntity(DynamicObject dy_main){
		common.sal.util.QFBuilder builder = new common.sal.util.QFBuilder();
		DynamicObject aos_orgid =  dy_main.getDynamicObject("aos_orgid");
		if (aos_orgid!=null){
			builder.add("aos_orgid","=",aos_orgid.getPkValue());
		}
		builder.add("aos_category1","=",dy_main.getString("aos_category1"));
		builder.add("aos_category2","=",dy_main.getString("aos_category2"));
		builder.add("aos_category3","=",dy_main.getString("aos_category3"));
		builder.add("aos_itemname","=",dy_main.getString("aos_itemnamecn"));
		builder.add("aos_itemid","!=","");
		DynamicObjectCollection dyc_line = dy_main.getDynamicObjectCollection("aos_itementity");
		dyc_line.removeIf(dy->true);

		DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_keyword", "aos_itemid,aos_itemid.number number", builder.toArray());
		for (DynamicObject row : dyc) {
			DynamicObject addNewRow = dyc_line.addNew();
			addNewRow.set("aos_itemid",row.get("aos_itemid"));
			addNewRow.set("aos_picture1",CommonDataSom.get_img_url(row.getString("number")));
		}
	}

	public  void find() {
		//迁移高级A+模板
		DynamicObjectCollection dyc = QueryServiceHelper.query("aos_aadd_model", "id", null);
		List<String> pks = dyc.stream().map(dy -> dy.getString("id")).collect(Collectors.toList());

		//查找明细信息
		QFilter filter = new QFilter("aos_sourceid","!=","");
		StringJoiner str = new StringJoiner(",");
		str.add("aos_sourceid");
		str.add("aos_seq");
		str.add("aos_button");
		str.add("aos_cate1");
		str.add("aos_cate2");
		str.add("aos_cn");
		str.add("aos_usca");
		str.add("aos_uk");
		str.add("aos_de");
		str.add("aos_fr");
		str.add("aos_it");
		str.add("aos_es");

		dyc = QueryServiceHelper.query("aos_aadd_model_detail", str.toString(), new QFilter[]{filter});
		//根据主键分组
		Map<String,Map<String,List<DynamicObject>>> map_data = new HashMap<>();
		for (DynamicObject dy : dyc) {
			String aos_sourceid = dy.getString("aos_sourceid");
			Map<String, List<DynamicObject>> map = map_data.computeIfAbsent(aos_sourceid, k -> new HashMap<>());
			String aos_seq = dy.getString("aos_button");
			List<DynamicObject> list = map.computeIfAbsent(aos_seq, k -> new ArrayList<>());
			list.add(dy);
		}

		List<DynamicObject> list_save = new ArrayList<>(5000);
		//主键
		for (Map.Entry<String, Map<String, List<DynamicObject>>> entry : map_data.entrySet()) {
			if (!pks.contains(entry.getKey())) {
				continue;
			}
			DynamicObject addEntry = BusinessDataServiceHelper.loadSingle(entry.getKey(), "aos_aadd_model");
			if (addEntry == null) {
				continue;
			}

			list_save.add(addEntry);

			DynamicObjectCollection tabEntRows = addEntry.getDynamicObjectCollection("aos_ent_tab");
			//页签遍历
			for (Map.Entry<String, List<DynamicObject>> lanEntry : entry.getValue().entrySet()) {
				//页签对应的第几行
				String tabIndex = lanEntry.getKey();
				//添加页签数据
				DynamicObject tabNewRow = tabEntRows.addNew();
				tabNewRow.set("seq",Integer.valueOf(tabIndex));
				tabNewRow.set("aos_tab",addEntry.getString("aos_textfield"+tabIndex));

				DynamicObjectCollection lanEntryRows = tabNewRow.getDynamicObjectCollection("aos_entryentity");
				//填加语言行
				for (DynamicObject lanRow : lanEntry.getValue()) {
					DynamicObject lanNewRow = lanEntryRows.addNew();
					lanNewRow.set("seq",lanRow.get("aos_seq"));
					lanNewRow.set("aos_cate1",lanRow.get("aos_cate1"));
					lanNewRow.set("aos_cate2",lanRow.get("aos_cate2"));
					lanNewRow.set("aos_cn",lanRow.get("aos_cn"));
					lanNewRow.set("aos_usca",lanRow.get("aos_usca"));
					lanNewRow.set("aos_uk",lanRow.get("aos_uk"));
					lanNewRow.set("aos_de",lanRow.get("aos_de"));
					lanNewRow.set("aos_fr",lanRow.get("aos_fr"));
					lanNewRow.set("aos_it",lanRow.get("aos_it"));
					lanNewRow.set("aos_es",lanRow.get("aos_es"));
				}
			}
			SaveUtils.SaveEntity(list_save,false);
		}
		SaveUtils.SaveEntity(list_save,true);

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

	//同步品名关键字库
	private void syncItemKeyword() {

		StringJoiner str = new StringJoiner(",");
		for (int i = 1; i < 8; i++) {
			str.add("aos_string"+i);
		}
		DynamicObjectCollection list = QueryServiceHelper.query("aos_czj_tmp", str.toString(), null);

		//根据国别,类别，属性拆分
		Map<String,List<String>> splitMap = new HashMap<>();
		for (DynamicObject row : list) {
			StringJoiner title = new StringJoiner("/");
			for (int i = 1; i < 6; i++) {
				title.add(row.getString("aos_string"+i));
			}
			List<String> value = splitMap.computeIfAbsent(title.toString(), k -> new ArrayList<>());

			title = new StringJoiner("/");
			title.add(row.getString("aos_string"+6));
			title.add(row.getString("aos_string"+7));
			if (!value.contains(title.toString())) {
				value.add(title.toString());
			}
		}


		List<DynamicObject> savEntity = new ArrayList<>(5000);
		CountryDao countryDao = new CountryDaoImpl();
		Map<String,String> orgInfo = new HashMap<>();
		for (Map.Entry<String, List<String>> entry : splitMap.entrySet()) {
			String[] split = entry.getKey().split("/");
			//国别
			String orgid;
			if (orgInfo.containsKey(split[0])){
				orgid = orgInfo.get(split[0]);
			}else {
				orgid = countryDao.getCountryID(split[0]);
				orgInfo.put(split[0],orgid);
			}
			//查找对应的单据
			QFBuilder builder = new QFBuilder();
			builder.add("aos_orgid.number","=",split[0] );
			builder.add("aos_category1","=",split[1] );
			builder.add("aos_category2","=",split[2] );
			builder.add("aos_category3","=",split[3] );
			builder.add("aos_category1","=",split[1] );
			builder.add("aos_itemnamecn","=",split[4] );

			DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_point", "id", builder.toArray());

			for (DynamicObject dy : dyc) {
				String id = dy.getString("id");

				DynamicObject pointEntity = BusinessDataServiceHelper.loadSingle(id,"aos_mkt_point");

				//获取已经有的关键词
				DynamicObjectCollection aos_linentity = pointEntity.getDynamicObjectCollection("aos_linentity");
				List<String> keyList = new ArrayList<>();
				for (DynamicObject dy_row : aos_linentity) {
					String aos_keyword = dy_row.getString("aos_keyword");
					if (FndGlobal.IsNotNull(aos_keyword)){
						keyList.add(aos_keyword);
					}
				}

				for (String keyInfo : entry.getValue()) {
					String[] split1 = keyInfo.split("/");
					if (keyList.contains(split1[0])){
						continue;
					}
					DynamicObject row = aos_linentity.addNew();
					row.set("aos_keyword",split1[0]);
					row.set("aos_remake",split1[1]);
					keyList.add(split1[0]);
				}
				savEntity.add(pointEntity);
			}
			SaveUtils.SaveEntity("aos_mkt_point",savEntity,false);
		}
		SaveUtils.SaveEntity("aos_mkt_point",savEntity,true);
	}

	public void beforePropertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		if (name.equals("aos_test_item")) {
		}
	}

	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		if (name.equals("aos_test_item")) {
		}
	}

}