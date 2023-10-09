package mkt.progress.design.aadd;

import java.util.*;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import common.sal.util.SalUtil;
import common.sal.util.SaveUtils;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.dataentity.serialization.SerializationUtils;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.ClientProperties;
import kd.bos.form.FormShowParameter;
import kd.bos.form.IFormView;
import kd.bos.form.IPageCache;
import kd.bos.form.ShowType;
import kd.bos.form.control.Button;
import kd.bos.form.control.Control;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.operate.FormOperate;
import kd.bos.lang.Lang;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.drp.pos.common.util.StringJoin;
import kd.fi.bd.util.QFBuilder;
import mkt.common.util.translateUtils;

@SuppressWarnings("unchecked")
public class aos_mkt_aaddmodel_bill extends AbstractBillPlugIn {
	private static final String KEY_USER = "LAN"; //用户可操控的语言

	@Override
	public void registerListener(EventObject e) {
		for (int i = 1; i <= 10; i++) {
			Button aos_button = this.getControl("aos_button" + i);
			aos_button.addClickListener(this);
		}
		this.addItemClickListeners("tbmain");// 给工具栏加监听事件
		this.addItemClickListeners("aos_change");
	}

	@Override
	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		if (name.equals("aos_productno"))
			AosProductNoChange();
	}

	@Override
	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		IPageCache iPageCache = this.getPageCache();
		iPageCache.put("button", "1");
		openInContainer();
		AosProductNoChange();
	}

	@Override
	public void afterBindData(EventObject e) {
		super.afterBindData(e);
		StatusControl();
	}

	/** 新建事件 **/
	@Override
	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		IPageCache iPageCache = this.getPageCache();
		iPageCache.put("button", "1");
		openInContainer();
	}

	@Override
	public void click(EventObject evt) {
		String source = ((Control) evt.getSource()).getKey();
		try {
			if (source.contains("aos_button")) {
				aos_button(source);// 提交
			}
		} catch (FndError FndError) {
			this.getView().showTipNotification(FndError.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
			ex.printStackTrace();
		}

	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String control = evt.getItemKey();
		if ("aos_change".equals(control)) {
			aos_change();
		}
		else if ("aos_copyto".equals(control)){
			FormShowParameter showParameter = FndGlobal.CraeteForm(this, "aos_mkt_functcopy", "copyTo", null);
			showParameter.setCaption("A+ Copy To");
			List<String> users= (List<String>) SerializationUtils.fromJsonStringToList(this.getPageCache().get(KEY_USER), String.class);
			showParameter.setCustomParam("userLan",users);
			getView().showForm(showParameter);
		}
		else if ("aos_copyfrom".equals(control)){
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
		System.out.println("actionId = " + actionId);
		Map<String, Object> returnData = (Map<String, Object>) event.getReturnData();
		System.out.println("returnData = " + returnData);
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
			if (actionId.equals("copyTo")){
				Object source = this.getModel().getDataEntity(true).getPkValue();
				String products = (String) returnData.get("no");
				QFBuilder builder = new QFBuilder();
				for (String no : products.split(",")) {
					if (FndGlobal.IsNull(no))
						continue;
					builder.clear();
					builder.add("aos_productno","=",no);
					List<Object> list = QueryServiceHelper.queryPrimaryKeys("aos_aadd_model", builder.toArray(), null, 1);
					saveEntity();
					copyValue(source,list.get(0),no,tabs,lans);
				}
				this.getView().showSuccessNotification("Copy to Success");
			}
			else if (actionId.equals("copyFrom")){
				Object source = this.getModel().getDataEntity(true).getPkValue();
				String products = (String) returnData.get("no");
				QFBuilder builder = new QFBuilder();
				for (String no : products.split(",")) {
					if (FndGlobal.IsNull(no))
						continue;
					builder.clear();
					builder.add("aos_productno","=",no);
					List<Object> list = QueryServiceHelper.queryPrimaryKeys("aos_aadd_model", builder.toArray(), null, 1);
					copyValue(list.get(0),source,no,tabs,lans);
				}
				openInContainer();
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
			saveEntity();
			//翻译前的语言
			String sourceLan = (String) returnData.get("source");
			String field = judgeLan(sourceLan);

			//翻译后的语言
			List<String> list_terminal = (List<String>) returnData.get("terminal");
			List<String> list_table = (List<String>) returnData.get("img");
			Map<String, Map<String, DynamicObject>> entiyData = findEntiyData(getModel().getDataEntity().getPkValue());

			//语言
			for (String lan : list_terminal) {
				//判断对应的字段

				//记录页签下的数据
				List<DynamicObject> list_row = new ArrayList<>();
				List<String> list_text = new ArrayList<>();
				for (String tab : list_table) {
					//当前页签下的数据
					Map<String, DynamicObject> tableData = entiyData.get(tab);
					for (DynamicObject rowData : tableData.values()) {
						String text = rowData.getString(field);
						if (FndGlobal.IsNotNull(text)) {
							list_row.add(rowData);
							list_text.add(text);
						}
					}
				}
				List<String> list_transalate = translateUtils.transalate(sourceLan, lan, list_text);
				//翻译完成进行赋值
				String transField = judgeLan(lan);
				for (int i = 0; i < list_row.size(); i++) {
					DynamicObject dy_row = list_row.get(i);
					dy_row.set(transField,list_transalate.get(i));
				}
			}
			List<DynamicObject> update = new ArrayList<>();
			for (Map.Entry<String, Map<String, DynamicObject>> mapEntry : entiyData.entrySet()) {
				for (Map.Entry<String, DynamicObject> entry : mapEntry.getValue().entrySet()) {
					update.add(entry.getValue());
				}
			}
			SaveUtils.UpdateEntity(update,true);
			openInContainer();
			this.getView().showSuccessNotification("Translate Success");
		}
	}


	/**
	 * copy 单据
	 * @param sourceid	源单据 id
	 * @param targetid	目标单据 id
	 * @param tarGetNo	目标单 产品号
	 * @param tabs		页签
	 * @param lans		语言
	 */
	private void copyValue(Object sourceid,Object targetid,Object tarGetNo,List<String> tabs,List<String> lans){
		//获取源单的相关数据
		Map<String,Map<String,DynamicObject>> sourceData = findEntiyData(sourceid);
		if (sourceData.size()==0) {
			return;
		}
		//获取目标单据的相关数据
		Map<String,Map<String,DynamicObject>> targetData = findEntiyData(targetid);

		//记录需要修改和保存的数据
		List<DynamicObject> list_save = new ArrayList<>(),list_update = new ArrayList<>();
		//页签维度
		for (String tab : tabs) {
			if (!sourceData.containsKey(tab)) {
				continue;
			}
			//行维度
			for (Map.Entry<String, DynamicObject> entry : sourceData.get(tab).entrySet()) {
				DynamicObject sourceRowDy = entry.getValue();
				DynamicObject targetRowDy = null;
				//目标单据存在则覆盖
				if (targetData.containsKey(tab)) {
					Map<String, DynamicObject> map_tar = targetData.get(tab);
					if (map_tar.containsKey(entry.getKey())){
						targetRowDy = map_tar.get(entry.getKey());
						list_update.add(targetRowDy);
					}
				}
				//目标单据不存在则新增
				if (targetRowDy==null){
					targetRowDy = BusinessDataServiceHelper.newDynamicObject("aos_aadd_model_detail");
					list_save.add(targetRowDy);
					targetRowDy.set("aos_productno",tarGetNo);
					targetRowDy.set("aos_sourceid",targetid);
					targetRowDy.set("aos_button",tab);
					targetRowDy.set("aos_seq",entry.getKey());
				}

				//大项，小项
				targetRowDy.set("aos_cate1",sourceRowDy.get("aos_cate1"));
				targetRowDy.set("aos_cate2",sourceRowDy.get("aos_cate2"));

				//设置每个语种
				for (String lan : lans) {
					String field = judgeLan(lan);
					targetRowDy.set(field,sourceRowDy.get(field));
				}
				SaveUtils.SaveEntity("aos_aadd_model_detail",list_save,false);
				SaveUtils.UpdateEntity(list_update,false);
			}
		}
		SaveUtils.SaveEntity("aos_aadd_model_detail",list_save,true);
		SaveUtils.UpdateEntity(list_update,true);
	}
	
	private String judgeLan(String lan){
		String field;
		if (lan.equals("中文")){
			field = "aos_cn";
		}
		else if (lan.equals("CA") || lan.equals("US") || lan.equals("EN")){
			field = "aos_usca";
		}
		else {
			field = "aos_"+lan.toLowerCase();
		}
		return field;
	}

	private Map<String,Map<String,DynamicObject>> findEntiyData(Object sourceid){
		Map<String,Map<String,DynamicObject>> result = new HashMap<>();
		QFBuilder builder = new QFBuilder();
		builder.add("aos_sourceid","=",sourceid);
		builder.add("aos_button","!=","");
		builder.add("aos_seq","!=","");
		StringJoin str = new StringJoin(",");
		str.add("aos_cate1");
		str.add("aos_cate2");
		str.add("aos_cn");
		str.add("aos_usca");
		str.add("aos_uk");
		str.add("aos_de");
		str.add("aos_fr");
		str.add("aos_it");
		str.add("aos_es");
		str.add("aos_button");
		str.add("aos_seq");
		DynamicObject[] dyc = BusinessDataServiceHelper.load("aos_aadd_model_detail", str.toString(), builder.toArray());
		for (DynamicObject dy : dyc) {
			String key = dy.getString("aos_button");
			Map<String, DynamicObject> map = result.computeIfAbsent(key, k -> new HashMap<>());
			map.put(dy.getString("aos_seq"),dy);
		}
		return result;
	}

	/**
	 * 改名
	 */
	private void aos_change() {
		FndMsg.debug("=====into aos_change=====");
		FndGlobal.OpenForm(this, "aos_aadd_model_show", null);
	}

	public void beforeDoOperation(BeforeDoOperationEventArgs args) {
		FormOperate formOperate = (FormOperate) args.getSource();
		String Operatation = formOperate.getOperateKey();
		if ("save".equals(Operatation)) {
			saveEntity();
		}
	}

	/**
	 * 保存单据体
	 */
	private void saveEntity() {
		String sonPageId = this.getPageCache().get("sonPageId");
		IFormView mainView = this.getView().getView(sonPageId);
		Object fid = this.getModel().getDataEntity().getPkValue();
		String button = this.getPageCache().get("button");
		DynamicObjectCollection aos_entryentityS = mainView.getModel().getEntryEntity("aos_entryentity");
		List<DynamicObject> aosBillDeatilS = new ArrayList<>();
		DeleteServiceHelper.delete("aos_aadd_model_detail",
				new QFilter("aos_sourceid", QCP.equals, fid).and("aos_button", QCP.equals, button).toArray());

		int i = 1;
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			aos_entryentity.set("aos_seq", i);
			i++;
		}

		for (DynamicObject aos_entryentity : aos_entryentityS) {
			DynamicObject aos_aadd_model_detail = BusinessDataServiceHelper.newDynamicObject("aos_aadd_model_detail");
			aos_aadd_model_detail.set("aos_cate1", aos_entryentity.get("aos_cate1"));
			aos_aadd_model_detail.set("aos_cate2", aos_entryentity.get("aos_cate2"));
			aos_aadd_model_detail.set("aos_cn", aos_entryentity.get("aos_cn"));
			aos_aadd_model_detail.set("aos_usca", aos_entryentity.get("aos_usca"));
			aos_aadd_model_detail.set("aos_uk", aos_entryentity.get("aos_uk"));
			aos_aadd_model_detail.set("aos_de", aos_entryentity.get("aos_de"));
			aos_aadd_model_detail.set("aos_fr", aos_entryentity.get("aos_fr"));
			aos_aadd_model_detail.set("aos_it", aos_entryentity.get("aos_it"));
			aos_aadd_model_detail.set("aos_es", aos_entryentity.get("aos_es"));
			aos_aadd_model_detail.set("aos_seq", aos_entryentity.get("aos_seq"));
			aos_aadd_model_detail.set("aos_sourceid", fid);
			aos_aadd_model_detail.set("aos_button", Integer.parseInt(button));
			aosBillDeatilS.add(aos_aadd_model_detail);
		}
		SaveServiceHelper.save(aosBillDeatilS.toArray(new DynamicObject[0]));
	}

	private void aos_button(String source) {
		openInContainer();
		IPageCache iPageCache = this.getPageCache();
		String buttonString = source.replace("aos_button", "");
		iPageCache.put("button", buttonString);
		for (int i = 1; i <= 10; i++) {
			Map<String, Object> map = new HashMap<>();
			if (i == Integer.parseInt(buttonString)) {
				map.put(ClientProperties.BackColor, "#45cdff");
			} else
				map.put(ClientProperties.BackColor, "#ffffff");
			this.getView().updateControlMetadata("aos_button" + i, map);
		}
	}

	/**
	 * 在容器中打开
	 */
	private void openInContainer() {
		String language = Lang.get().getLocale().getLanguage();
		for (int i = 1; i <= 10; i++) {
			Map<String, Object> map = new HashMap<>();
			String text = (String) this.getModel().getValue("aos_textfield" + i);
			if (language.equals("zh"))
				map.put(ClientProperties.Text, new LocaleString(text));
			else {
				if ("海报模块".equals(text))
					text = "Full Image";
				else if ("轮播图模块".equals(text))
					text = "Carousel";
				else if ("锚点模块".equals(text))
					text = "Hotspots";
				else if ("细节模块".equals(text))
					text = "Details";
				else if ("QA模块".equals(text))
					text = "Q&A";
				map.put(ClientProperties.Text, new LocaleString(text));
			}
			this.getView().updateControlMetadata("aos_button" + i, map);
		}
		FormShowParameter parameter = new FormShowParameter();
		parameter.setFormId("aos_aadd_model_form");
		parameter.getOpenStyle().setShowType(ShowType.InContainer);
		parameter.getOpenStyle().setTargetKey("aos_flexpanelap1"); // 设置父容器标识
		this.getView().showForm(parameter);
		IPageCache iPageCache = this.getPageCache();
		String sonPageId = parameter.getPageId();
		iPageCache.put("sonPageId", sonPageId);
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
		if (dy_userPermiss!=null){
			if (!Cux_Common_Utl.IsNull(dy_userPermiss.get("aos_lan"))) {
				//用户能够copy的语言
				for (String lan : dy_userPermiss.getString("aos_lan").split(",")) {
					if (!Cux_Common_Utl.IsNull(lan)) {
						userCopyLanguage.add(lan);
					}
				}
			}
		}
		this.getPageCache().put(KEY_USER,SerializationUtils.toJsonString(userCopyLanguage));
	}

}