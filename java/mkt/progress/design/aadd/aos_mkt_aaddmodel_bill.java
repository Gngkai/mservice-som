package mkt.progress.design.aadd;

import java.util.*;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Joiner;
import common.CommonDataSom;
import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.serialization.SerializationUtils;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.events.ChangeData;
import kd.bos.entity.datamodel.events.ImportDataEventArgs;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.report.CellStyle;
import kd.bos.form.FormShowParameter;
import kd.bos.form.cardentry.CardEntry;
import kd.bos.form.control.AbstractGrid;
import kd.bos.form.control.Image;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.RowClickEvent;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.events.*;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;

import common.sal.util.QFBuilder;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.common.util.sensitiveWordsUtils;
import mkt.common.util.translateUtils;
import mkt.progress.design.aos_mkt_funcreq_init;

@SuppressWarnings("unchecked")
public class aos_mkt_aaddmodel_bill extends AbstractBillPlugIn implements RowClickEventListener, HyperLinkClickListener {
	public static final String KEY_USER = "LAN"; //用户可操控的语言
	private final static List<String> list_lans;
	//敏感词语种缓存标识
	public static final String KEY_SENSITIVE ="seniive";
	//需要进行敏感词校验的字段
	private final static List<String> sensitiveFields;
	//需要判定字符串长度的字段
	private final static List<String> lengthFields;
	static {
		sensitiveFields = Arrays.asList("aos_usca","aos_uk","aos_de","aos_fr","aos_it","aos_es","aos_pt","aos_ro");
		lengthFields = Arrays.asList("aos_cn","aos_usca","aos_uk","aos_de","aos_fr","aos_it","aos_es","aos_pt","aos_ro");
		list_lans = Arrays.asList("CN", "中文", "US","CA","UK","EN","DE","FR","IT","ES","PT","RO");
	}

	@Override
	public void registerListener(EventObject e) {
		this.addItemClickListeners("tbmain");// 给工具栏加监听事件
		this.addItemClickListeners("aos_change");
		CardEntry metaEntry = getControl("aos_ent_tab");
		metaEntry.addRowClickListener(this);
		AbstractGrid grid = this.getControl("aos_subentryentity");
		grid.addHyperClickListener(this);
	}
	@Override
	public void entryRowClick(RowClickEvent evt) {
		CardEntry metaEntry = (CardEntry) evt.getSource();
		if (StringUtils.equals("aos_ent_tab",metaEntry.getKey())) {
			setEntityColor();
		}
	}

	@Override
	public void hyperLinkClick(HyperLinkClickEvent event) {
		String fieldName = event.getFieldName();
		int rowIndex = event.getRowIndex();
		if (fieldName.equals("aos_replace") && rowIndex>=0){
			int tabCurrentRow = this.getModel().getEntryCurrentRowIndex("aos_ent_tab");
			DynamicObject dy_tab = this.getModel().getDataEntity(true)
					.getDynamicObjectCollection("aos_ent_tab")
					.get(tabCurrentRow);
			//敏感词行
			DynamicObject senSitiveRow = dy_tab.getDynamicObjectCollection("aos_subentryentity").get(rowIndex);
			//语言
			String lan = senSitiveRow.getString("aos_sublan");
			//替换词
			String replaceWord = senSitiveRow.getString("aos_subword");
			if (FndGlobal.IsNull(replaceWord)) {
				this.getView().showTipNotification("替换词不存在");
				return;
			}
			//敏感词
			String sentitiveWord = senSitiveRow.getString("aos_word");
			//判断替换的控件名
			String field;
			if (lan.equals("US/CA")) field = "aos_usca";
			else {
				field = "aos_"+lan.toLowerCase();
			}

			//语言行
			DynamicObjectCollection dyc_ent = dy_tab.getDynamicObjectCollection("aos_entryentity");
			for (String row : senSitiveRow.getString("aos_rows").split("/")) {
				DynamicObject dy_sentivite = dyc_ent.get(Integer.parseInt(row));
				String value = dy_sentivite.getString(field);
				value = sensitiveWordsUtils.replaceSensitiveWords(value,sentitiveWord,replaceWord);
				this.getModel().setValue(field,value,Integer.parseInt(row),tabCurrentRow);
			}
			this.getView().showSuccessNotification("敏感词已替换");
		}
	}

	@Override
	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		//修改产品编号
		if (name.equals("aos_productno")){
			AosProductNoChange();
		}
		//敏感词校验
		else if (sensitiveFields.contains(name)){
			ChangeData changeData = e.getChangeSet()[0];
			if (changeData.getRowIndex()>=0) {
				int tabRow = this.getModel().getEntryCurrentRowIndex("aos_ent_tab");
				DynamicObject dy_tab = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_ent_tab").get(tabRow);
				JSONObject sensitiveWords = JSONObject.parseObject(getPageCache().get(KEY_SENSITIVE));
				filterSentiviteWordsRow(sensitiveWords,dy_tab,changeData.getNewValue(),changeData.getRowIndex(),name);
				getView().updateView("aos_subentryentity");
				getView().updateView("aos_entryentity",changeData.getRowIndex());
				setEntityColor();
				if (dy_tab.getDynamicObjectCollection("aos_subentryentity").size()>0) {
					getModel().setValue("aos_check",true,tabRow);
				}
				else {
					getModel().setValue("aos_check",false,tabRow);
				}
			}
		}
		//tab修改检验行长度
		else if(name.equals("aos_tab")){
			int rowIndex = e.getChangeSet()[0].getRowIndex();
			if (rowIndex>=0){
				if (!checkTable(rowIndex)){
					this.getView().showTipNotification("The input exceeds the specified length!");
				}
			}
		}
		//小项修改校验长度
		else if (name.equals("aos_cate2")) {
			int rowIndex = e.getChangeSet()[0].getRowIndex();
			int tabIndex = e.getChangeSet()[0].getParentRowIndex();
			if (rowIndex>=0 && tabIndex>=0){
				if (!checkLanRow(tabIndex,rowIndex)) {
					this.getView().showTipNotification("The input exceeds the specified length!");
				}
			}
		}
		//判断字符串长度
		if (lengthFields.contains(name)){
			int rowIndex = e.getChangeSet()[0].getRowIndex();
			int tabIndex = e.getChangeSet()[0].getParentRowIndex();
			if (rowIndex>=0 && tabIndex>=0){
				if (!checkSensitiveWord(tabIndex,rowIndex,name)) {
					this.getView().showTipNotification("The input exceeds the specified length!");
				}
			}
		}
	}

	@Override
	public void afterBindData(EventObject e) {
		super.afterBindData(e);
		StatusControl();
		List<QFilter> materialFilter = SalUtil.get_MaterialFilter();
		QFilter filter = new QFilter("aos_productno","=",this.getModel().getValue("aos_productno"));
		materialFilter.add(filter);
		DynamicObject dy = QueryServiceHelper.queryOne("bd_material", "number", materialFilter.toArray(new QFilter[0]));
		if (dy!=null){
			Image image = this.getView().getControl("aos_imageap");
			image.setUrl(CommonDataSom.get_img_url(dy.getString("number")));
		}
		initSensitiveWords();
		AosProductNoChange();
		setEntityColor();
	}

	@Override
	public void afterDoOperation(AfterDoOperationEventArgs evt) {
		super.afterDoOperation(evt);
		String control = evt.getOperateKey();
		if ("aos_change".equals(control)) {
			aos_change();
		}
		else if (control.equals("save")){
			if (!checkTable()) {
				getView().showTipNotification("The input exceeds the specified length!");
			}
		}
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String control = evt.getItemKey();
		if ("aos_copyto".equals(control)){
			filterSentivitEntry();
			FormShowParameter showParameter = FndGlobal.CraeteForm(this, "aos_mkt_functcopy", "copyTo", null);
			showParameter.setCaption("A+ Copy To");
			List<String> users= (List<String>) SerializationUtils.fromJsonStringToList(this.getPageCache().get(KEY_USER), String.class);
			showParameter.setCustomParam("userLan",users);
			getView().showForm(showParameter);
		}
		else if ("aos_copyfrom".equals(control)){
			filterSentivitEntry();
			FormShowParameter showParameter = FndGlobal.CraeteForm(this, "aos_mkt_functcopy", "copyFrom", null);
			showParameter.setCaption("A+ Copy Form");
			List<String> users= (List<String>) SerializationUtils.fromJsonStringToList(this.getPageCache().get(KEY_USER), String.class);
			showParameter.setCustomParam("userLan",users);
			getView().showForm(showParameter);
		}
		else if ("aos_translate".equals(control)){
			FormShowParameter showParameter = FndGlobal.CraeteForm(this, "aos_mkt_funct_tran", "trans", null);
			showParameter.setCaption("Translate Form");
			List<String> users= (List<String>) SerializationUtils.fromJsonStringToList(this.getPageCache().get(KEY_USER), String.class);
			showParameter.setCustomParam("userLan",users);
			getView().showForm(showParameter);
		}
	}

	@Override
	public void closedCallBack(ClosedCallBackEvent event) {
		super.closedCallBack(event);
		String actionId = event.getActionId();
		Map<String, Object> returnData = (Map<String, Object>) event.getReturnData();
		if (returnData == null)
			return;
		if (actionId.equals("copyTo") ||actionId.equals("copyFrom")){
			if (!returnData.containsKey("no")) {
				return;
			}
			Map<String,Object> data = (Map<String, Object>) returnData.get("data");
			if (!data.containsKey("tab")) {
				return;
			}
			if (!data.containsKey("lan")) {
				return;
			}

			List<String> tabs = (List<String>) data.get("tab");
			List<String> lans = (List<String>) data.get("lan");
			String products = (String) returnData.get("no");
			QFBuilder builder = new QFBuilder();
			if (actionId.equals("copyTo")){
				for (String no : products.split(",")) {
					if (FndGlobal.IsNull(no))
						continue;
					builder.clear();
					builder.add("aos_productno","=",no);
					List<Object> list = QueryServiceHelper.queryPrimaryKeys("aos_aadd_model", builder.toArray(), null, 1);
					DynamicObject dy = BusinessDataServiceHelper.loadSingle(list.get(0), "aos_aadd_model");
					copyValue(this.getModel().getDataEntity(true),dy,tabs,lans);
					SaveServiceHelper.save(new DynamicObject[]{dy});
				}
				this.getView().showSuccessNotification("Copy to Success");
			}
			else {
				for (String no : products.split(",")) {
					if (FndGlobal.IsNull(no))
						continue;
					builder.clear();
					builder.add("aos_productno","=",no);
					List<Object> list = QueryServiceHelper.queryPrimaryKeys("aos_aadd_model", builder.toArray(), null, 1);
					DynamicObject dy = BusinessDataServiceHelper.loadSingle(list.get(0), "aos_aadd_model");
					copyValue(dy,this.getModel().getDataEntity(true),tabs,lans);
				}
				filterSentivitEntry();
				this.getView().showSuccessNotification("Copy from Success");
			}
		}
		else if (actionId.equals("trans")){
			if (returnData.get("img")==null)
				return;
			if (returnData.get("source")==null)
				return;
			if (returnData.get("terminal")==null)
				return;
			//翻译前的语言
			String sourceLan = (String) returnData.get("source");
			String field = judgeLan(sourceLan);
			if (FndGlobal.IsNull(field)){
				this.getView().showErrorNotification("翻译源语言不存在");
				return;
			}

			//翻译后的语言
			List<String> list_terminal = (List<String>) returnData.get("terminal");
			List<String> list_table = (List<String>) returnData.get("img");
			Map<String, DynamicObjectCollection> entiyData = findEntiyData(this.getModel().getDataEntity(true));

			//语言
			for (String lan : list_terminal) {
				//判断对应的字段
				//翻译完成进行赋值
				String transField = judgeLan(lan);
				if (FndGlobal.IsNull(transField))
					continue;
				//记录页签下的数据
				List<DynamicObject> list_row = new ArrayList<>();
				List<String> list_text = new ArrayList<>();
				for (String tab : list_table) {
					//当前页签下的数据
					DynamicObjectCollection tableData = entiyData.get(tab);
					for (DynamicObject rowData : tableData) {
						String text = rowData.getString(field);
						if (FndGlobal.IsNotNull(text)) {
							list_row.add(rowData);
							list_text.add(text);
						}
					}
				}
				List<String> list_transalate = translateUtils.transalate(sourceLan, lan, list_text);

				for (int i = 0; i < list_row.size(); i++) {
					DynamicObject dy_row = list_row.get(i);
					dy_row.set(transField,list_transalate.get(i));
				}
			}
			filterSentivitEntry();
			this.getView().showSuccessNotification("Translate Success");
		}
	}

	@Override
	public void beforeDoOperation(BeforeDoOperationEventArgs args) {
		FormOperate formOperate = (FormOperate) args.getSource();
		String Operatation = formOperate.getOperateKey();
		if ("save".equals(Operatation)) {
			setProductItem(this.getModel().getDataEntity(true));
			cleanButton();
			filterSentivitEntry();
		}
	}

	@Override
	public void afterImportData(ImportDataEventArgs e) {
		super.afterImportData(e);
		DynamicObject entity = this.getModel().getDataEntity(true);
		setProductItem(entity);
	}

	@Override
	public void beforeClosed(BeforeClosedEvent e) {
		super.beforeClosed(e);
		DynamicObject dataEntity = this.getModel().getDataEntity(true);
		Set<String> fields= new HashSet<>();
		fields.add("aos_picturefield");
		for (String field : sensitiveFields) {
			fields.add(field+"_h");
		}
		fields.add("aos_cn_h");
		fields.add("aos_check");
		fields.add("aos_sublan");
		fields.add("aos_wordtype");
		fields.add("aos_word");
		fields.add("aos_subword");
		fields.add("aos_replace");
		fields.add("aos_rows");
		SalUtil.skipVerifyFieldChanged(dataEntity,dataEntity.getDynamicObjectType(),fields);
//		this.getView().invokeOperation("save");
	}

	//将没有命名的页签删除
	private void cleanButton() {
		DynamicObjectCollection dyc = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_ent_tab");
		dyc.removeIf(dy->dy.getDynamicObjectCollection("aos_entryentity").size()==0);
		getView().updateView();
	}

	/**
	 * 设置兄弟货号
	 * @param dy_main 单据
	 */
	public void setProductItem(DynamicObject dy_main){
		String aos_productno = dy_main.getString("aos_productno");
		if (FndGlobal.IsNull(aos_productno)) {
			return;
		}
		DynamicObjectCollection productEntity = dy_main.getDynamicObjectCollection("aos_entryentity2");
		if (productEntity.size()>0) {
			return;
		}
		QFilter filter_productno = new QFilter("aos_productno", "=", aos_productno);// 未导入标记
		QFilter[] filters = new QFilter[] { filter_productno };
		DynamicObjectCollection bd_materialSameS = QueryServiceHelper.query("bd_material", "id", filters);
		List<String> list_orgtext_total = new ArrayList<>();
		for (DynamicObject bd_materialSame : bd_materialSameS) {
			Object item_id = bd_materialSame.get("id");
			List<String> list_orgtext = new ArrayList<>();
			DynamicObject material = BusinessDataServiceHelper.loadSingle(item_id, "bd_material");
			DynamicObjectCollection aos_contryentryS = material.getDynamicObjectCollection("aos_contryentry");
			// 获取所有国家品牌 字符串拼接 终止
			for (DynamicObject aos_contryentry : aos_contryentryS) {
				aos_mkt_funcreq_init.addItemOrg(aos_contryentry,item_id,list_orgtext,list_orgtext_total);
			}
			DynamicObject aos_entryentity2 = productEntity.addNew();
			aos_entryentity2.set("aos_itemid",material);
			aos_entryentity2.set("aos_orgdetail", Joiner.on(";").join(list_orgtext));
		}
	}

	/**
	 * copy 单据
	 * @param dy_source	源单据
	 * @param dy_target	目标单据
	 * @param tabs		页签
	 * @param lans		语言
	 */
	private void copyValue(DynamicObject dy_source,DynamicObject dy_target,List<String> tabs,List<String> lans){
		DynamicObjectCollection tabEntity = dy_target.getDynamicObjectCollection("aos_ent_tab");
		DynamicObjectCollection sourcEntity = dy_source.getDynamicObjectCollection("aos_ent_tab");
		//获取源单的相关数据
		Map<String, DynamicObjectCollection> sourceData = findEntiyData(dy_source);
		if (sourceData.size()==0) {
			return;
		}
		//获取目标单据的相关数据
		Map<String, DynamicObjectCollection> targetData = findEntiyData(dy_target);

		//页签维度
		for (String tab : tabs) {
			if (!sourceData.containsKey(tab)) {
				continue;
			}
			//如果源单包含该页签
			if (sourceData.containsKey(tab)) {
				DynamicObjectCollection sourceRowDy = sourceData.get(tab);
				DynamicObjectCollection targetRowDy = null;
				//目标单据存在则覆盖
				if (targetData.containsKey(tab)) {
					tabEntity.get(Integer.parseInt(tab)).set("aos_tab",sourcEntity.get(Integer.parseInt(tab)).get("aos_tab"));
					targetRowDy = targetData.get(tab);
				}
				//目标单据不存在则新增
				if (targetRowDy==null){
					DynamicObject tabRow = tabEntity.addNew();
					tabRow.set("seq",Integer.parseInt(tab)+1);
					tabRow.set("aos_tab",sourcEntity.get(Integer.parseInt(tab)).get("aos_tab"));
					targetRowDy = tabRow.getDynamicObjectCollection("aos_entryentity");
				}

				for (int sourceIndex = 0; sourceIndex < sourceRowDy.size(); sourceIndex++) {
					DynamicObject sourceSubRow = sourceRowDy.get(sourceIndex);
					DynamicObject targetSubRow;

					if (targetRowDy.size() == sourceIndex) {
						targetSubRow = targetRowDy.addNew();
						targetSubRow.set("seq",sourceIndex+1);
					}
					else {
						targetSubRow = targetRowDy.get(sourceIndex);
					}

					//大项，小项
					targetSubRow.set("aos_cate1",sourceSubRow.get("aos_cate1"));
					targetSubRow.set("aos_cate2",sourceSubRow.get("aos_cate2"));

					//设置每个语种
					for (String lan : lans) {
						String field = judgeLan(lan);
						if (FndGlobal.IsNull(field)) {
							continue;
						}
						targetSubRow.set(field,sourceSubRow.get(field));
					}
				}
			}
		}

	}
	
	public static String judgeLan(String lan){
		String field;
		if (list_lans.contains(lan)){
			if (lan.equals("中文")){
				field = "aos_cn";
			}
			else if (lan.equals("CA") || lan.equals("US") || lan.equals("EN")) field = "aos_usca";
			else {
				field = "aos_"+lan.toLowerCase();
			}
			return field;
		}
		else {
			return null;
		}
	}

	public static Map<String,DynamicObjectCollection> findEntiyData(DynamicObject dy_main){
		Map<String,DynamicObjectCollection> result = new HashMap<>();
		DynamicObjectCollection dyc = dy_main.getDynamicObjectCollection("aos_ent_tab");
		for (int i = 0; i < dyc.size(); i++) {
			DynamicObject dy = dyc.get(i);
			result.put(String.valueOf(i),dy.getDynamicObjectCollection("aos_entryentity"));
		}
		return result;
	}

	/**
	 * 改名
	 */
	private void aos_change() {
		FndGlobal.OpenForm(this, "aos_aadd_model_show", null);
	}

	/**
	 * 产品号值改变
	 */
	private void AosProductNoChange() {
		Object aos_productno = this.getModel().getValue("aos_productno");
		if (FndGlobal.IsNull(aos_productno)) {
			this.getModel().setValue("aos_picturefield", null);
		} else {
			// 图片字段
			DynamicObject AosItemidObject = BusinessDataServiceHelper.loadSingle("bd_material",
					new QFilter("aos_productno", QCP.equals, aos_productno).toArray());
			if (FndGlobal.IsNotNull(AosItemidObject)) {
				QFilter filter = new QFilter("entryentity.aos_articlenumber", QCP.equals, AosItemidObject.getPkValue())
						.and("billstatus", QCP.in, new String[] { "C", "D" });
				DynamicObjectCollection query = QueryServiceHelper.query("aos_newarrangeorders",
						"entryentity.aos_picture", filter.toArray(), "aos_creattime desc");
				if (query != null && query.size() > 0) {
					Object o = query.get(0).get("entryentity.aos_picture");
					this.getModel().setValue("aos_picturefield", o);
				}
			}
		}
	}

	/**
	 * 查询可以copy，翻译的语言
	 */
	private void StatusControl() {
		//根据用户查找相应的人员能够操作的语言
		QFBuilder builder = new QFBuilder();
		builder.add("aos_user","=", RequestContext.get().getCurrUserId());
		DynamicObject dy_userPermiss = QueryServiceHelper.queryOne("aos_mkt_permiss_lan", "aos_row,aos_exchange,aos_lan", builder.toArray());
		List<String> userCopyLanguage = new ArrayList<>();
		//记录能够操作的字段控件
		List<String> userFidlds = new ArrayList<>();
		if (dy_userPermiss!=null){
			if (!Cux_Common_Utl.IsNull(dy_userPermiss.get("aos_lan"))) {
				//用户能够copy的语言
				for (String lan : dy_userPermiss.getString("aos_lan").split(",")) {
					if (!Cux_Common_Utl.IsNull(lan)) {
						userCopyLanguage.add(lan);
						userFidlds.add(judgeLan(lan));
					}
				}
			}
		}
		this.getPageCache().put(KEY_USER,SerializationUtils.toJsonString(userCopyLanguage));

		AbstractGrid grid = this.getControl("aos_entryentity");
		grid.setLock("aos_cn");
		if (!userFidlds.contains("aos_cn")){
			this.getModel().setValue("aos_cn_h",true);
		}

		for (String field : sensitiveFields) {
			if (!userFidlds.contains(field)) {
				getModel().setValue(field+"_h",true);
			}
		}
	}

	/**
	 * 初始化敏感词
	 */
	private void initSensitiveWords(){
		//根据产品号查询一个最新的物料
		Object productNo = getModel().getValue("aos_productno");
		JSONObject lanSensitiveWords = sensitiveWordsUtils.FindMaterialSensitiveWords(productNo);
		getPageCache().put(KEY_SENSITIVE,lanSensitiveWords.toString());
	}

	/**
	 * 对所有的页签行进行校验
	 */
	private  void filterSentivitEntry(){
		DynamicObjectCollection dyc_tab = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_ent_tab");
		JSONObject sensitiveWords = JSONObject.parseObject(getPageCache().get(KEY_SENSITIVE));
		for (int tabIndex = 0; tabIndex < dyc_tab.size(); tabIndex++) {
			DynamicObject dy_table = dyc_tab.get(tabIndex);
			//语言单据体
			DynamicObjectCollection entityRows = dy_table.getDynamicObjectCollection("aos_entryentity");
			for (int i = 0; i < entityRows.size(); i++) {
				DynamicObject row = entityRows.get(i);
				for (String field : sensitiveFields) {
					filterSentiviteWordsRow(sensitiveWords,dy_table,row.get(field),i,field);
				}
			}
			if (dy_table.getDynamicObjectCollection("aos_subentryentity").size()>0) {
				this.getModel().setValue("aos_check",true,tabIndex);
			}
			else {
				this.getModel().setValue("aos_check",false,tabIndex);
			}
		}
		setEntityColor();
		getView().updateView();
	}

	/**
	 * 进行敏感词校验
	 * @param sensitiveWords	存在的敏感词
	 * @param dy_tab			页签行
	 * @param text				校验文本
	 * @param lanRow			校验行
	 * @param name				校验控件名
	 */
	private static void filterSentiviteWordsRow(JSONObject sensitiveWords,DynamicObject dy_tab,Object text,int lanRow,String name){
		String lan;
		if (name.equals("aos_usca")){
			lan = "US/CA";
		}
		else {
			lan = name.substring(name.length() - 2).toUpperCase();
		}
		if (FndGlobal.IsNotNull(text)) {
			if (lan.equals("US/CA")){
				JSONObject result = sensitiveWordsUtils.sensitiveWordVerificate(sensitiveWords,text.toString() ,"US" );
				if (result.getBoolean("state")) {
					setSentiviteWord(dy_tab,result,lanRow,lan);
				}
				//校验结果没有敏感词，则删除敏感词记录
				else {
					setSentiviteWord(dy_tab,null,lanRow,lan);
				}

				result = sensitiveWordsUtils.sensitiveWordVerificate(sensitiveWords,text.toString() ,"CA" );
				if (result.getBoolean("state")) {
					setSentiviteWord(dy_tab,result,lanRow,lan);
				}
				//校验结果没有敏感词，则删除敏感词记录
				else {
					setSentiviteWord(dy_tab,null,lanRow,lan);
				}
			}
			else {
				JSONObject sensitiveResult = sensitiveWordsUtils.sensitiveWordVerificate(sensitiveWords,text.toString() ,lan );
				if (sensitiveResult.getBoolean("state")) {
					setSentiviteWord(dy_tab,sensitiveResult,lanRow,lan);
				}
				//校验结果没有敏感词，则删除敏感词记录
				else {
					setSentiviteWord(dy_tab,null,lanRow,lan);
				}

			}
		}
		//校验内容为空，说明没有敏感词
		else {
			setSentiviteWord(dy_tab,null,lanRow,lan);
		}
	}

	/**
	 * 填充敏感词单据
	 * @param dy_tab	敏感词单据
	 * @param sensitiveResult	校验结果
	 * @param lanRow	行索引
	 * @param lan	语言
	 */
	private  static void setSentiviteWord(DynamicObject dy_tab,JSONObject sensitiveResult,int lanRow,String lan){
		DynamicObjectCollection wordEntityRows = dy_tab.getDynamicObjectCollection("aos_subentryentity");
		//记录敏感词对应的单据行,顺便删除该行单据体以前的敏感词信息
		Map<String,DynamicObject> map_sentiveWord = new HashMap<>();
		//语言行
		String lanRowInfo = String.valueOf(lanRow);
		for (DynamicObject subRow :wordEntityRows) {
			String subLan = subRow.getString("aos_sublan");
			if (!subLan.equals(lan))
				continue;
			StringJoiner rowInfo = new StringJoiner("/");
			//如果该敏感词对应的行里面有校验行，则删掉其中的校验行
			for (String row : subRow.getString("aos_rows").split("/")) {
				if (!row.equals(lanRowInfo)) {
					rowInfo.add(row);
				}
			}
			subRow.set("aos_rows",rowInfo.toString());
			map_sentiveWord.put(subRow.getString("aos_word"),subRow);
		}
		if (sensitiveResult!=null) {
			//校验结果
			JSONArray dataRows = sensitiveResult.getJSONArray("data");
			//开始将结果加入到单据体中
			for (int i = 0; i < dataRows.size(); i++) {
				JSONObject dataRow = dataRows.getJSONObject(i);
				String words = dataRow.getString("words");
				//如果敏感词已经存在了，则在敏感词行中加入这条数据
				if (map_sentiveWord.containsKey(words)) {
					DynamicObject dy = map_sentiveWord.get(words);
					String value = dy.getString("aos_rows");
					if (FndGlobal.IsNull(value)){
						value = lanRowInfo;
					}
					else {
						value = value+"/"+lanRowInfo;
					}
					dy.set("aos_rows",value);
				}
				//反之则添加行
				else{
					DynamicObject newRow = wordEntityRows.addNew();
					newRow.set("aos_sublan",lan);
					newRow.set("aos_wordtype",dataRow.get("type"));
					newRow.set("aos_word",words);
					newRow.set("aos_subword",dataRow.get("replace"));
					newRow.set("aos_rows",lanRowInfo);
					newRow.set("aos_replace","replace");
				}
			}
		}
		//将敏感词单据中 没有对应行的数据删除
		wordEntityRows.removeIf(dy->FndGlobal.IsNull(dy.get("aos_rows")));
	}

	/**
	 * 修改敏感词行的颜色
	 */
	private void setEntityColor(){
		int tabRow = this.getModel().getEntryCurrentRowIndex("aos_ent_tab");
		if (FndGlobal.IsNull(tabRow)) {
			return;
		}
		DynamicObjectCollection dyc_tab = this.getModel().getDataEntity(true)
				.getDynamicObjectCollection("aos_ent_tab");
		if (dyc_tab.isEmpty()) {
			return;
		}
		DynamicObject tabEntryRow = dyc_tab.get(tabRow);
		//获取语言信息单据体控件
		AbstractGrid grid = this.getView().getControl("aos_entryentity");
		List<CellStyle> list = new ArrayList<>();
		//获取语言单据数据
		DynamicObjectCollection dyc_lanInfo = tabEntryRow.getDynamicObjectCollection("aos_entryentity");

		DynamicObjectCollection subRows = tabEntryRow.getDynamicObjectCollection("aos_subentryentity");
		//记录存在敏感词的行
		Map<String,List<String>> map_sentiviteRows = new HashMap<>();
		for (DynamicObject row : subRows) {

			String field;
			String lan = row.getString("aos_sublan");
			if (lan.equals("US/CA")) field = "aos_usca";
			else {
				field = "aos_"+lan.toLowerCase();
			}
			map_sentiviteRows.put(field,Arrays.asList(row.getString("aos_rows").split("/")));
		}
		for (int i = 0; i < dyc_lanInfo.size(); i++) {

			for (String field : sensitiveFields) {

				CellStyle cs = new CellStyle();
				cs.setFieldKey(field);
				cs.setRow(i);
				//该行为敏感词，设置为红色，否则为黑色
				if (map_sentiviteRows.containsKey(field) && map_sentiviteRows.get(field).contains(String.valueOf(i))){
					//红色
					cs.setForeColor("#fb2323");
				}
				else {
					//黑色
					cs.setForeColor("#404040");
				}
				list.add(cs);
			}
		}
		grid.setCellStyle(list);
	}

	private boolean checkTable( ){
		DynamicObjectCollection dyc_tab = this.getModel().getEntryEntity("aos_ent_tab");
		boolean flag = true;
		for (int tabIndex = 0; tabIndex < dyc_tab.size(); tabIndex++) {
			if (!checkTable(tabIndex)) {
				flag = false;
			}
		}
		return flag;
	}

	/**
	 * 校验敏感词
	 */
	private boolean checkTable( int tabRow){
		DynamicObjectCollection dyc_lan = this.getModel().getEntryEntity("aos_ent_tab").get(tabRow).getDynamicObjectCollection("aos_entryentity");
		boolean flag = true;
		for (int i = 0; i < dyc_lan.size(); i++) {
			if (!checkLanRow(tabRow,i)) {
				flag = false;
			}
		}
		return flag;
	}

	/**
	 * 校验敏感词行
	 */
	private boolean checkLanRow (int tabRow,int lanRow){
		boolean flag = true;
		for (String lengthField : lengthFields) {
			if (!checkSensitiveWord(tabRow,lanRow,lengthField)) {
				flag = false;
			}
		}
		return flag;
	}

	/**
	 * @param tabRow	单据头行
	 * @param lanRow	语言行
	 * @param field		字段
	 * @return		是否校验成功
	 */
	private boolean checkSensitiveWord(int tabRow,int lanRow,String field){
		Object aos_tab = getModel().getValue("aos_tab",tabRow);
		Object aos_cate2 = getModel().getValue("aos_cate2", lanRow,tabRow);
		if (FndGlobal.IsNull(aos_tab) || FndGlobal.IsNull(aos_cate2)){
			this.getModel().setValue(field+"_t","",lanRow,tabRow);
			return true;
		}

		String tabValue = String.valueOf(aos_tab);
		String cate2Value = String.valueOf(aos_cate2);
		Object value =  this.getModel().getValue(field, lanRow, tabRow);

		int valueSize = 0;
		if(FndGlobal.IsNotNull(value)){
			valueSize = value.toString().length();
		}
		String filterValue = "";
		boolean resultt = true;
		switch (tabValue) {
			case "轮播图模块":
				//标题
				if (cate2Value.equals("TITLE") || cate2Value.equals("标题")) {
					filterValue = valueSize + "/" + 25;
					resultt = valueSize <= 25;
				}
				break;
			case "锚点模块":
				if (cate2Value.equals("TITLE") || cate2Value.equals("标题")) {
					filterValue = valueSize + "/" + 50;
					resultt = valueSize <= 50;
				} else if (cate2Value.equals("CONTENT") || cate2Value.equals("正文")) {
					filterValue = valueSize + "/" + 200;
					resultt = valueSize <= 200;
				}
				break;
			case "细节模块":
				if (cate2Value.equals("TITLE") || cate2Value.equals("标题")) {
					filterValue = valueSize + "/" + 30;
					resultt = valueSize <= 30;
				} else if (cate2Value.equals("CONTENT") || cate2Value.equals("正文")) {
					filterValue = valueSize + "/" + 150;
					resultt = valueSize <= 150;
				}
				break;
			case "QA模块":
				if (cate2Value.equals("Q")) {
					filterValue = valueSize + "/" + 120;
					resultt = valueSize <= 120;
				} else if (cate2Value.equals("A")) {
					filterValue = valueSize + "/" + 250;
					resultt = valueSize <= 250;
				}
				break;
		}

		this.getModel().setValue(field+"_t",filterValue,lanRow,tabRow);
		return resultt;
	}
}