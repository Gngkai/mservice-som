package mkt.act.rule;

import common.Cux_Common_Utl;
import common.fnd.FndDate;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndMath;
import common.fnd.FndMsg;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.bill.BillShowParameter;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.StyleCss;
import kd.bos.form.control.Control;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ContainerOnShowEvent;
import kd.bos.form.control.events.ContainerOnShowListener;
import kd.bos.form.control.events.EntryFilterChangedEvent;
import kd.bos.form.control.events.EntryFilterChangedListener;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import common.sal.util.QFBuilder;
import mkt.act.basedata.actType.actTypeForm;
import mkt.act.rule.Import.EventRule;
import mkt.act.rule.dotd.GenerateData;
import mkt.act.rule.service.ActPlanService;
import mkt.act.rule.service.impl.ActPlanServiceImpl;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.*;

import static mkt.act.basedata.actType.actTypeForm.Form_key;

public class aos_mkt_actrule_generate extends AbstractBillPlugIn
		implements ItemClickListener, RowClickEventListener, EntryFilterChangedListener, ContainerOnShowListener {
	static Log logging = LogFactory.getLog("aos_mkt_actrule_generate");
	public void registerListener(EventObject e) {
		// 给工具栏加监听事件
		this.addItemClickListeners("tbmain");
		this.addItemClickListeners("aos_generate"); // 生成销售数据

		this.addItemClickListeners("aos_toolbarap");
		this.addItemClickListeners("aos_batchquery");
		this.addItemClickListeners("aos_qtycal");

		EntryGrid entryGrid = this.getControl("aos_sal_actplanentity");
		entryGrid.addFilterChangedListener(this);
		this.addClickListeners(new String[]{"aos_rule","aos_name"});
	}

	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		this.getModel().setValue("aos_itemid_mul", null);
	}

	public void beforeClosed(BeforeClosedEvent e) {
		super.beforeClosed(e);
		e.setCheckDataChange(false);
	}

	@Override
	public void afterDoOperation(AfterDoOperationEventArgs eventArgs) {
		super.afterDoOperation(eventArgs);
		String operateKey = eventArgs.getOperateKey();
		if (operateKey.equals("open_rule")) {
			//打开活动库界面修改规则集
			openActForm();
		}
		else if (operateKey.equals("pull_rule")){
			pullActRule();
		}
		else if ("sync_rule".equals(operateKey)){
			syncActRule();
		}

	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		String Control = evt.getItemKey();
		try {
			switch (Control) {
				case "aos_generate":
					aos_generate();
					break;
				case "aos_detailimp":
					genActPlan();// 明细导入

					break;
				case "aos_batchquery":
					aos_batchquery();
					break;
				case "aos_batchclose":
					aos_batchclose();
					break;
				case "aos_batchopen":
					aos_batchopen();
					break;
				case "aos_qtycal":
					// 从新批量设置活动数量
					batchSetActQty(getModel().getDataEntity(true),false);
					getView().updateView();
					getView().showTipNotification("批量计算数量完成");
					break;
			}

		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception e) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(e));
		}

	}

	@Override
	public void click(EventObject evt) {
		super.click(evt);
		Control control= (Control) evt.getSource();
		String key= control.getKey();

		if (key.equals("aos_rule")) {
			actTypeForm.showParameter(key,Form_key,this);
		}
		else if (key.equals("aos_name")){
			actTypeForm.showParameter(key,"aos_act_type_cate",this);
		}
	}

	@Override
	public void closedCallBack(ClosedCallBackEvent event) {
		super.closedCallBack(event);
		String actionId = event.getActionId();
		Object redata =  event.getReturnData();
		if (redata==null)
			return;
		if (actionId.equals("aos_rule")) {
			Map<String,String> returnData = (Map<String, String>) redata;
			String key = returnData.get(actTypeForm.Key_return_k);
			String name = returnData.get(actTypeForm.Key_return_v);
			if (FndGlobal.IsNotNull(key)){
				try {
					actTypeForm.parseFormula(key);
					getModel().setValue("aos_rule",name);
					getModel().setValue("aos_rule_v",key);
				}
				catch (Exception e){
					e.printStackTrace();
					getView().showTipNotification("表达式输入有误");
				}
			}
			else{
				getModel().setValue("aos_rule","");
				getModel().setValue("aos_rule_v","");
			}
		}
		else if (actionId.equals("aos_name")){
			int index = this.getModel().getEntryCurrentRowIndex("aos_entryentity1");
			this.getModel().setValue("aos_name",redata.toString(),index);
		}
	}

	/**
	 * 打开活动库界面
	 */
	private void openActForm(){
		QFBuilder builder = new QFBuilder();
		DynamicObject entity = getModel().getDataEntity();
		builder.add("aos_org","=",entity.getDynamicObject("aos_nationality").getPkValue());
		builder.add("aos_channel","=",entity.getDynamicObject("aos_channel").getPkValue());
		builder.add("aos_shop","=",entity.getDynamicObject("aos_shop").getPkValue());
		builder.add("aos_acttype","=",entity.getDynamicObject("aos_acttype").getPkValue());
		DynamicObject dy_actType = QueryServiceHelper.queryOne("aos_sal_act_type_p", "id", builder.toArray());
		if (dy_actType == null)
			return;
		//创建弹出单据页面对象，并赋值
		BillShowParameter billShowParameter = FndGlobal.CraeteBillForm(this,"aos_sal_act_type_p","aos_sal_act",new HashMap<>());
		//设置弹出子单据页面的样式，高600宽800
		StyleCss inlineStyleCss = new StyleCss();
		inlineStyleCss.setHeight("800");
		inlineStyleCss.setWidth("1500");
		billShowParameter.getOpenStyle().setInlineStyleCss(inlineStyleCss);
		billShowParameter.setPkId(dy_actType.get("id"));
		//弹窗子页面和父页面绑定
		this.getView().showForm(billShowParameter);
	}

	/**
	 * 将活动库的数据生成到活动计划中
	 */
	private void pullActRule(){

		DynamicObject actTypeEntity = getActTypeEntry();
		if (actTypeEntity == null) {
			return;
		}

		//添加品类
		DynamicObjectCollection dyc_act = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity1");
		dyc_act.removeIf(dy->true);
		for (DynamicObject row : actTypeEntity.getDynamicObjectCollection("aos_entryentity1")) {
			DynamicObject newActRow = dyc_act.addNew();
			newActRow.set("aos_cate",row.get("aos_cate"));
			newActRow.set("aos_name",row.get("aos_name"));
		}
		//添加活动规则
		dyc_act = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity2");
		dyc_act.removeIf(dy->true);
		for (DynamicObject row : actTypeEntity.getDynamicObjectCollection("aos_entryentity2")) {
			DynamicObject newActRow = dyc_act.addNew();
			newActRow.set("aos_act_project",row.get("aos_project"));
			newActRow.set("aos_condite",row.get("aos_condite"));
			newActRow.set("aos_rule_value",row.get("aos_rule_value"));
			newActRow.set("aos_rule_day",row.get("aos_rule_day"));
		}
		getModel().setValue("aos_rule",actTypeEntity.get("aos_rule"));
		getModel().setValue("aos_rule_v",actTypeEntity.get("aos_rule_v"));

		//去重规则
		DynamicObjectCollection dyc_rule = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity3");
		dyc_rule.removeIf(dy->true);
		for (DynamicObject row : actTypeEntity.getDynamicObjectCollection("aos_entryentity3")) {
			DynamicObject newActRow = dyc_rule.addNew();
			newActRow.set("aos_re_project",row.get("aos_re_project"));
			newActRow.set("aos_frame",row.get("aos_frame"));
			newActRow.set("aos_re_channel",row.get("aos_re_channel"));
			newActRow.set("aos_re_shop",row.get("aos_re_shop"));
			newActRow.set("aos_re_act",row.get("aos_re_act"));
		}

		//优先级
		 dyc_rule = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity4");
		dyc_rule.removeIf(dy->true);
		for (DynamicObject row : actTypeEntity.getDynamicObjectCollection("aos_entryentity4")) {
			DynamicObject newActRow = dyc_rule.addNew();
			newActRow.set("aos_pr_project",row.get("aos_pr_project"));
			newActRow.set("aos_pr_way",row.get("aos_pr_way"));
			newActRow.set("aos_pr_weight",row.get("aos_pr_weight"));
			newActRow.set("aos_pr_day",row.get("aos_pr_day"));
			newActRow.set("seq",row.get("seq"));
		}

		getView().updateView("aos_flexpanelap3");
	}

	/**
	 * 将选品表中的数据同步至 活动库中
	 */
	private void syncActRule(){
		DynamicObject actTypeEntity = getActTypeEntry();
		if (actTypeEntity == null) {
			return;
		}
		//添加品类
		DynamicObjectCollection dyc_act = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity1");
		DynamicObjectCollection actTypeRows = actTypeEntity.getDynamicObjectCollection("aos_entryentity1");
		actTypeRows.removeIf(dy->true);
		for (DynamicObject row : dyc_act) {
			DynamicObject newActRow = actTypeRows.addNew();
			newActRow.set("aos_cate",row.get("aos_cate"));
			newActRow.set("aos_name",row.get("aos_name"));
		}

		//添加活动规则
		dyc_act = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity2");
		actTypeRows = actTypeEntity.getDynamicObjectCollection("aos_entryentity2");
		actTypeRows.removeIf(dy->true);
		for (DynamicObject row :dyc_act ) {
			DynamicObject newActRow = actTypeRows.addNew();
			newActRow.set("aos_project",row.get("aos_act_project"));
			newActRow.set("aos_condite",row.get("aos_condite"));
			newActRow.set("aos_rule_value",row.get("aos_rule_value"));
			newActRow.set("aos_rule_day",row.get("aos_rule_day"));
		}
		actTypeEntity.set("aos_rule",getModel().getValue("aos_rule"));
		actTypeEntity.set("aos_rule_v",getModel().getValue("aos_rule_v"));


		//去重规则
		dyc_act = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity3");

		actTypeRows = actTypeEntity.getDynamicObjectCollection("aos_entryentity3");
		actTypeRows.removeIf(dy->true);
		for (DynamicObject row : dyc_act) {
			DynamicObject newActRow = actTypeRows.addNew();
			newActRow.set("aos_re_project",row.get("aos_re_project"));
			newActRow.set("aos_frame",row.get("aos_frame"));
			newActRow.set("aos_re_channel",row.get("aos_re_channel"));
			newActRow.set("aos_re_shop",row.get("aos_re_shop"));
			newActRow.set("aos_re_act",row.get("aos_re_act"));
		}

		//优先级
		dyc_act = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity4");
		actTypeRows = actTypeEntity.getDynamicObjectCollection("aos_entryentity4");
		for (DynamicObject row : dyc_act ) {
			DynamicObject newActRow = actTypeRows.addNew();
			newActRow.set("aos_pr_project",row.get("aos_pr_project"));
			newActRow.set("aos_pr_way",row.get("aos_pr_way"));
			newActRow.set("aos_pr_weight",row.get("aos_pr_weight"));
			newActRow.set("aos_pr_day",row.get("aos_pr_day"));
			newActRow.set("seq",row.get("seq"));
		}

		SaveServiceHelper.save(new DynamicObject[]{actTypeEntity});
	}

	/**
	 * 查找对应的活动库
	 */
	private DynamicObject getActTypeEntry(){
		QFBuilder builder = new QFBuilder();
		Object aos_nationality = getModel().getValue("aos_nationality");
		//国别不为空，添加过滤
		if (aos_nationality!=null) {
			String org = ((DynamicObject) aos_nationality).getString("id");
			builder.add("aos_org", "=", org);
		}
		//渠道不为空，添加过滤
		Object aos_channel = getModel().getValue("aos_channel");
		if (aos_channel!=null) {
			String channel = ((DynamicObject) aos_channel).getString("id");
			builder.add("aos_channel", "=", channel);
		}
		//店铺不为空，添加过滤
		Object aos_shop = getModel().getValue("aos_shop");
		if (aos_shop!=null) {
			String shop = ((DynamicObject) aos_shop).getString("id");
			builder.add("aos_shop", "=", shop);
		}
		//活动类型不为空，添加过滤
		Object aos_acttype = getModel().getValue("aos_acttype");
		if (aos_acttype!=null) {
			String acttype = ((DynamicObject) aos_acttype).getString("id");
			builder.add("aos_acttype", "=", acttype);
		}
		//查询活动库
		DynamicObject actTypeEntity = BusinessDataServiceHelper.loadSingle("aos_sal_act_type_p", builder.toArray());
		if (actTypeEntity == null) {
			getView().showTipNotification("没有找到活动库数据！");
			return null;
		}
		return actTypeEntity;
	}


	/** 批量打开 **/
	private void aos_batchopen() throws FndError {
		// 数据层
		int ErrorCount = 0;
		String ErrorMessage = "";
		EntryGrid entryGrid = this.getControl("aos_sal_actplanentity");
		int[] SelectRow = entryGrid.getSelectRows();

		// 校验
		if (SelectRow.length == 0) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "未勾选行！");
		}

		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		for (int i = 0; i < SelectRow.length; i++) {
			this.getModel().setValue("aos_l_actstatus", "A", SelectRow[i]);
		}

		this.getView().invokeOperation("save");

		aos_batchquery();
	}

	/** 批量关闭 **/
	private void aos_batchclose() throws FndError {
		// 数据层
		int ErrorCount = 0;
		String ErrorMessage = "";
		EntryGrid entryGrid = this.getControl("aos_sal_actplanentity");
		int[] SelectRow = entryGrid.getSelectRows();

		// 校验
		if (SelectRow.length == 0) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "未勾选行！");
		}

		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		for (int i = 0; i < SelectRow.length; i++) {
			this.getModel().setValue("aos_l_actstatus", "B", SelectRow[i]);
		}

		// 如果行上全为已关闭 则将头状态改为已关闭
		Boolean closeFlag = true;
		DynamicObjectCollection aos_sal_actplanentityS = this.getModel().getEntryEntity("aos_sal_actplanentity");
		for (DynamicObject aos_sal_actplanentity : aos_sal_actplanentityS) {
			if ("A".equals(aos_sal_actplanentity.getString("aos_l_actstatus"))) {
				closeFlag = false;
				break;
			}
		}

		if (closeFlag) {
			this.getModel().setValue("aos_actstatus", "C");
		}

		this.getView().invokeOperation("save");
		aos_batchquery();
	}

	/** 批量筛选货号 **/
	private void aos_batchquery() throws FndError {
		Object aos_itemid_mul = this.getModel().getValue("aos_itemid_mul");
		if (!Cux_Common_Utl.IsNull(aos_itemid_mul)) {
			DynamicObjectCollection aos_itemidS = (DynamicObjectCollection) aos_itemid_mul;
			Set<String> itemSet = new HashSet<>();
			for (DynamicObject aos_itemid : aos_itemidS) {
				itemSet.add(aos_itemid.getDynamicObject("fbasedataid").getPkValue() + "");
			}
			if (itemSet != null) {
				DynamicObjectCollection aos_sal_actplanentityS = this.getModel()
						.getEntryEntity("aos_sal_actplanentity");
				EntryGrid entryGrid = this.getControl("aos_sal_actplanentity");
				int size = aos_sal_actplanentityS.size();
				List<Integer> list = new ArrayList<>();
				for (int i = 0; i < size; i++) {
					Object aos_itemnum = this.getModel().getValue("aos_itemnum", i);
					if (!Cux_Common_Utl.IsNull(aos_itemnum)) {
						if (itemSet.contains(((DynamicObject) aos_itemnum).getPkValue() + "")) {
							list.add(i);
						}
					}
				}
				int[] selectRow = new int[list.size()];
				for (int i = 0; i < list.size(); i++)
					selectRow[i] = list.get(i);
				entryGrid.selectRows(selectRow, 0);
				this.getModel().setEntryRowTop("aos_sal_actplanentity", selectRow);
			}
		}
	}

	private void aos_generate() {
		boolean aos_gensalconfirm = (boolean) this.getModel().getValue("aos_gensalconfirm");
		if (aos_gensalconfirm) {
			this.getView().showTipNotification("已生成销售确认单,请勿重复点击");
			return;
		}
		DynamicObject aos_act_select_plan = this.getModel().getDataEntity(true);
		GenerateData.genSaleActRule(aos_act_select_plan);
		this.getView().invokeOperation("refresh");
	}

	private void genActPlan() {
		Object actstatus = this.getModel().getValue("aos_actstatus");
		if (FndGlobal.IsNotNull(actstatus)){
			if (String.valueOf(actstatus).equals("E")) {
				this.getView().showTipNotification("明细导入中，请勿重复点击");
				return;
			}
		}
		this.getModel().setValue("aos_actstatus","E");


		// 获取头信息关键字段
		Date aos_startdate = (Date) this.getModel().getValue("aos_startdate");
		Date aos_enddate1 = (Date) this.getModel().getValue("aos_enddate1");
		DynamicObject aos_nationality = (DynamicObject) this.getModel().getValue("aos_nationality");
		DynamicObject aos_acttype = (DynamicObject) this.getModel().getValue("aos_acttype");
		DynamicObject aos_shop = (DynamicObject) this.getModel().getValue("aos_shop");
		DynamicObject aos_channel = (DynamicObject) this.getModel().getValue("aos_channel");

		if (aos_startdate == null || aos_enddate1 == null || aos_nationality == null || aos_acttype == null
				|| aos_shop == null || aos_channel == null) {
			this.getView().showTipNotification("请填写基本信息中的必填字段!");
			return;
		}

		String actType = aos_acttype.getString("number");

		// 判断用户是否已保存
		long pkValue = (long) this.getModel().getDataEntity().getPkValue();
		if (pkValue == 0) {
			this.getView().showTipNotification("请先保存数据!");
			return;
		}
		// 获取国别+活动类型
		String ouCode = aos_nationality.getString("number");
		// 店铺
		String shop = aos_shop.getString("number");
		//230821 gk :活动明细导入根据用户配置规则筛选物料导入
		execute(actstatus,aos_nationality,aos_channel,aos_shop,aos_acttype,getModel().getDataEntity(true));

		//execute(ouCode, shop, actType, this.getModel().getDataEntity());

//		String noPriceItem = getNoPriceItem();
//		if (noPriceItem.length() > 0) {
//			this.getView().showMessage("以下物料缺失价格：  " + noPriceItem);
//		}
		//从新批量设置活动数量
		batchSetActQty(getModel().getDataEntity(true),false);
		this.getView().updateView();

	}
	private void execute(Object actstatus,DynamicObject ou,DynamicObject channel ,DynamicObject shop, DynamicObject actType, DynamicObject actPlanEntity){
		QFBuilder builder = new QFBuilder();
		builder.add("aos_org","=",ou.getPkValue());
		builder.add("aos_channel","=",channel.getPkValue());
		builder.add("aos_shop","=",shop.getPkValue());
		builder.add("aos_acttype","=",actType.getPkValue());
		DynamicObject type = QueryServiceHelper.queryOne("aos_sal_act_type_p", "id", builder.toArray());
		if (type==null) {
			getView().showTipNotification("活动库选品规则未维护");
			return;
		}

		try {
			actPlanEntity.set("aos_actstatus","E");
			SaveServiceHelper.update(actPlanEntity);
			//获取活动规则
			DynamicObject acTypEntity = BusinessDataServiceHelper.loadSingle(type.getString("id"), "aos_sal_act_type_p");
			new EventRule(acTypEntity,actPlanEntity);
			// 赋值库存信息、活动信息
			SaveServiceHelper.save(new DynamicObject[]{actPlanEntity});
			ActPlanService actPlanService = new ActPlanServiceImpl();
			actPlanEntity = BusinessDataServiceHelper.loadSingle(actPlanEntity.getPkValue(),"aos_act_select_plan");
			actPlanService.updateActInfo(actPlanEntity);
			SaveServiceHelper.save(new DynamicObject[]{actPlanEntity});

		}catch (Exception e){
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			getView().showMessage(sw.toString());
		}
		finally {
			actPlanEntity.set("aos_actstatus",actstatus);
			SaveServiceHelper.save(new DynamicObject[]{actPlanEntity});
			getView().invokeOperation("refresh");
		}
	}

	/**
	 * 	批量设置活动数量
	 * @param aos_act_select_plan	单据
	 * @param rawValue	是否保留原值
	 */
	public static void batchSetActQty(DynamicObject aos_act_select_plan,boolean rawValue) {
		System.out.println("活动数量计算---活动数量计算开始");
		logging.info("导入数据---活动数量计算开始");

		Object fid = aos_act_select_plan.getPkValue().toString();
		DynamicObject aos_nationality = aos_act_select_plan.getDynamicObject("aos_nationality");
		DynamicObject aos_acttype = aos_act_select_plan.getDynamicObject("aos_acttype");
		DynamicObject aos_shop = aos_act_select_plan.getDynamicObject("aos_shop");
		Date date = FndDate.GetGlMonth(new Date(),-6);
		String format = DateFormatUtils.format(date, "yyyy-MM-dd");//开始日期
		String shopIdStr = aos_shop.getPkValue().toString();
		String orgIdStr = aos_nationality.getPkValue().toString();
		HashMap<String, Integer> itemShopMap = getItemShopQty(orgIdStr);
		logging.info("获取七日 国别+物料+店铺销量 ：" + itemShopMap);
		DynamicObjectCollection aos_sal_actplanentityS =aos_act_select_plan.getDynamicObjectCollection("aos_sal_actplanentity");
		int size = aos_sal_actplanentityS.size();
		FndMsg.debug("size:" + size);
		//最小值
		BigDecimal minQtyBd = BigDecimal.valueOf(Long.parseLong("2"));
		for (DynamicObject aos_sal_actplanentity : aos_sal_actplanentityS) {
			//如果要保留原值，且该行原值大于0，则该行跳过
			if (rawValue){
				BigDecimal rowQtyBd = aos_sal_actplanentity.getBigDecimal("aos_actqty");
				if (rowQtyBd!=null && rowQtyBd.compareTo(BigDecimal.ZERO)>0){
					continue;
				}
			}
			DynamicObject aos_itemnum = aos_sal_actplanentity.getDynamicObject("aos_itemnum");
			Date aos_l_startdate = aos_sal_actplanentity.getDate("aos_l_startdate");
			Date aos_enddate = aos_sal_actplanentity.getDate("aos_enddate");
			int betweenDays = FndDate.GetBetweenDays(aos_enddate, aos_l_startdate) + 1;
			String itemIdStr = aos_itemnum.getPkValue().toString();
			DynamicObjectCollection queryLastThreeS = QueryServiceHelper.query("aos_act_select_plan",
					"billno,aos_sal_actplanentity.aos_itemnum.number,aos_sal_actplanentity.aos_actproid_salqty aos_actproid_salqty,"    //活动期间日销量
							+ "aos_sal_actplanentity.aos_actbefore_salqty aos_actbefore_salqty",  //活动前日销量
					new QFilter("aos_nationality.id", QCP.equals, orgIdStr).and("id", QCP.not_equals, fid)
							.and("aos_sal_actplanentity.aos_itemnum.id", QCP.equals, itemIdStr)
							.and("aos_makedate", QCP.large_equals,format) //近6个月数据
							.and("aos_acttype.id", QCP.equals, aos_acttype.getPkValue().toString()).toArray(),
					"aos_sal_actplanentity.id desc", 3);//降序

			BigDecimal aos_actqty = BigDecimal.ZERO;
			int hisQty = 0;
			logging.info("天数：" + betweenDays + " 活动计划表个数：" + queryLastThreeS);
			String key = itemIdStr + "~" + shopIdStr;
			if (FndGlobal.IsNotNull(itemShopMap) && FndGlobal.IsNotNull(itemShopMap.get(key)))
				hisQty = itemShopMap.get(key);

			if (queryLastThreeS.size() == 3) {
				for (DynamicObject queryLastThree : queryLastThreeS) {
					aos_actqty = aos_actqty.add(BigDecimal.valueOf(queryLastThree.getInt("aos_actproid_salqty")))
							.subtract(BigDecimal.valueOf(queryLastThree.getInt("aos_actbefore_salqty")));
				}
				aos_actqty = FndMath
						.max(aos_actqty.divide(BigDecimal.valueOf(3), 2, BigDecimal.ROUND_HALF_UP), BigDecimal.ZERO)
						.add(BigDecimal.valueOf(hisQty)).multiply(BigDecimal.valueOf(betweenDays));

				if(aos_actqty.compareTo(BigDecimal.ZERO) < 0)
					aos_actqty = BigDecimal.ZERO;
				logging.info("活动计划表个数=3时，活动数量 = " + aos_actqty);
			}
			else {
				aos_actqty = BigDecimal.valueOf(hisQty).multiply(BigDecimal.valueOf(1.1))
						.multiply(BigDecimal.valueOf(betweenDays));
				logging.info("活动计划表个数!=3时，活动数量 = " + aos_actqty);
			}
			if (aos_actqty.compareTo(minQtyBd)<0){
				aos_actqty = minQtyBd;
			}

			aos_sal_actplanentity.set("aos_actqty", aos_actqty);
		}
		logging.info("活动数量计算--活动数量计算结束");

	}

	/**
	 * 获取七日 国别+物料+店铺销量
	 * 
	 * @param orgIdStr
	 * @return
	 */
	private static HashMap<String, Integer> getItemShopQty(String orgIdStr) {
		FndMsg.debug("=====into getItemShopQty=====");
		HashMap<String, Integer> itemShopMap = new HashMap<>();
		Calendar date = Calendar.getInstance();
		date.set(Calendar.HOUR_OF_DAY, 0);
		date.set(Calendar.MINUTE, 0);
		date.set(Calendar.SECOND, 0);
		date.set(Calendar.MILLISECOND, 0);
		Date Today = date.getTime();
		DataSet orderS = QueryServiceHelper.queryDataSet("aos_mkt_actrule_generate.getItemShopQty." + orgIdStr,
				"aos_sync_om_order_r", "aos_item_fid,aos_shopfid,aos_order_qty",
				new QFilter("aos_org.id", QCP.equals, orgIdStr)
						.and("aos_order_date", QCP.large_equals, FndDate.add_days(Today, -7)).toArray(),
				null);
		String[] GroupBy = new String[] { "aos_item_fid", "aos_shopfid" };
		orderS = orderS.groupBy(GroupBy).sum("aos_order_qty").finish();
		while (orderS.hasNext()) {
			Row order = orderS.next();
			String key = order.getString("aos_item_fid") + "~" + order.getString("aos_shopfid");
			FndMsg.debug("key1:" + key);
			itemShopMap.put(key, order.getInteger("aos_order_qty"));
		}
		orderS.close();
		return itemShopMap;
	}

	@Override
	public void containerOnShow(ContainerOnShowEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void entryFilterChanged(EntryFilterChangedEvent e) {
	}
}