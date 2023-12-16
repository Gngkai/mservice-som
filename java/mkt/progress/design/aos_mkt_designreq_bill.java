package mkt.progress.design;

import java.util.*;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.sal.sys.basedata.dao.CountryDao;
import common.sal.sys.basedata.dao.impl.CountryDaoImpl;
import common.sal.util.SalUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.dataentity.serialization.SerializationUtils;
import kd.bos.entity.EntityMetadataCache;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.entity.property.EntryProp;
import kd.bos.form.*;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
import kd.bos.form.events.MessageBoxClosedEvent;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.GlobalMessage;
import mkt.common.MKTCom;
import mkt.common.MKTS3PIC;
import mkt.common.otel.MmsOtelUtils;
import mkt.progress.ProgressUtil;
import mkt.progress.design.aadd.aos_mkt_aadd_bill;
import mkt.progress.design3d.DesignSkuList;
import mkt.progress.design3d.aos_mkt_3design_bill;
import mkt.progress.iface.iteminfo;
import mkt.progress.iface.parainfo;
import mkt.progress.listing.aos_mkt_listingson_bill;
import mkt.progress.parameter.errorListing.ErrorListEntity;

public class aos_mkt_designreq_bill extends AbstractBillPlugIn implements ItemClickListener, HyperLinkClickListener {
	private static final Tracer tracer = MmsOtelUtils.getTracer(aos_mkt_designreq_bill.class, RequestContext.get());

	/** 设计需求表标识 **/
	public final static String aos_mkt_designreq = "aos_mkt_designreq";
	/** 系统管理员 **/
	public final static String system = Cux_Common_Utl.SYSTEM;

	//3d建模页面缓存标识
	private final static String KEY_CreateDesign = "CreateDesign";

	@Override
	public void afterBindData(EventObject e) {
		super.afterBindData(e);
		//设置缓存
		setPageCache();
	}

	@Override
	public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
		int RowIndex = hyperLinkClickEvent.getRowIndex();
		String FieldName = hyperLinkClickEvent.getFieldName();
		if ("aos_segment3".equals(FieldName)) {
			Object aos_segment3 = this.getModel().getValue("aos_segment3", RowIndex);
			DynamicObject aos_mkt_functreq = QueryServiceHelper.queryOne("aos_mkt_functreq", "id",
					new QFilter[] { new QFilter("aos_segment3", QCP.equals, aos_segment3) });
			if (!Cux_Common_Utl.IsNull(aos_mkt_functreq))
				Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_functreq", aos_mkt_functreq.get("id"));
			else
				this.getView().showErrorNotification("功能图需求表信息不存在!");
		} else if (FieldName.equals("aos_url")) {
			int rowIndex = hyperLinkClickEvent.getRowIndex();
			String url = this.getModel().getValue("aos_url", rowIndex).toString();
			if (url != null)
				this.getView().openUrl(url);
		}
	}

	@Override
	public void registerListener(EventObject e) {
		super.registerListener(e);
		this.addItemClickListeners("aos_toolbarap");// 给工具栏加监听事件
		this.addItemClickListeners("aos_submit"); // 提交
		this.addItemClickListeners("aos_return");
		this.addItemClickListeners("aos_open");
		this.addItemClickListeners("aos_openorign");
		EntryGrid entryGrid = this.getControl("aos_subentryentity");
		entryGrid.addHyperClickListener(this);
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		Span span = MmsOtelUtils.getCusMainSpan(tracer, MmsOtelUtils.getMethodPath());

		super.itemClick(evt);
		String Control = evt.getItemKey();
		try (Scope scope = span.makeCurrent()) {
			if ("aos_submit".equals(Control)) {
				DynamicObject dy_mian = this.getModel().getDataEntity(true);
				aos_submit(dy_mian, "A");// 提交
			} else if ("aos_return".equals(Control))
				aos_return();
			else if ("aos_open".equals(Control))
				aos_open();
			else if ("aos_openorign".equals(Control))
				aos_openorign();
			else if ("aos_history".equals(Control))
				aos_history();// 查看历史记录
			else if ("aos_close".equals(Control))
				aos_close();// 手工关闭
			else if ("aos_querysample".equals(Control))
				querySample();
		} catch (FndError FndError) {
			this.getView().showTipNotification(FndError.getErrorMessage());
			MmsOtelUtils.setException(span, FndError);
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
			MmsOtelUtils.setException(span, ex);
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	private void querySample() throws FndError {
		try {
			// 封样单参数
			Object sampleId = null;
			// 当前行
			int currentRow = this.getModel().getEntryCurrentRowIndex("aos_entryentity");
			// 货号
			DynamicObject aosItemId = (DynamicObject) this.getModel().getValue("aos_itemid", currentRow);
			List<Object> primaryKeys = QueryServiceHelper.queryPrimaryKeys("aos_sealsample",
					new QFilter("aos_item.id", QCP.equals, aosItemId.getPkValue()).toArray(), "createtime desc", 1);
			if (FndGlobal.IsNotNull(primaryKeys) && primaryKeys.size() > 0)
				sampleId = primaryKeys.get(0);
			else
				throw new FndError("未找到对应封样单!");
			// 打开封样单
			FndGlobal.OpenBillById(this.getView(), "aos_sealsample", sampleId);
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	private void aos_close() {
		String KEY_CANCEL = "bar_cancel";
		ConfirmCallBackListener confirmCallBackListener = new ConfirmCallBackListener(KEY_CANCEL, this);
		// 设置页面确认框，参数为：标题，选项框类型，回调监听
		this.getView().showConfirm("您确认关闭此申请单吗？", MessageBoxOptions.YesNo, confirmCallBackListener);
	}

	@Override
	public void confirmCallBack(MessageBoxClosedEvent event) {
		super.confirmCallBack(event);
		String callBackId = event.getCallBackId();
		if (callBackId.equals("bar_cancel")) {
			if (event.getResult().equals(MessageBoxResult.Yes)) {
				this.getModel().setValue("aos_user", system);
				this.getModel().setValue("aos_status", "结束");
				this.getView().invokeOperation("save");
				this.getView().invokeOperation("refresh");
				setErrorList(this.getModel().getDataEntity(true));
				FndHistory.Create(this.getView(), "手工关闭", "手工关闭");
			}
		}
	}

	/** 打开历史记录 **/
	private void aos_history() throws FndError {
		Cux_Common_Utl.OpenHistory(this.getView());
	}

	private void aos_openorign() {
		Object aos_sourceid = this.getModel().getValue("aos_sourceid");
		Object aos_sourcetype = this.getModel().getValue("aos_sourcetype");
		if ("LISTING".equals(aos_sourcetype))
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_listing_req", aos_sourceid);
		else if ("PHOTO".equals(aos_sourcetype))
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_photoreq", aos_sourceid);
	}

	private void aos_open() {
		Object aos_productid = this.getModel().getValue("aos_productid");
		DynamicObject aos_prodatachan = QueryServiceHelper.queryOne("aos_prodatachan", "id",
				new QFilter[] { new QFilter("id", QCP.equals, aos_productid) });
		if (!Cux_Common_Utl.IsNull(aos_prodatachan))
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_prodatachan", aos_prodatachan.get("id"));
		else
			this.getView().showErrorNotification("产品资料变跟单信息不存在!");
	}

	/** 申请人确认退回 **/
	private void aos_return() throws FndError {
		Object aos_laststatus = this.getModel().getValue("aos_laststatus");
		Object aos_lastuser = this.getModel().getValue("aos_lastuser");
		if (!Cux_Common_Utl.IsNull(aos_laststatus) && !Cux_Common_Utl.IsNull(aos_lastuser)) {
			this.getModel().setValue("aos_status", this.getModel().getValue("aos_laststatus"));
			this.getModel().setValue("aos_user", this.getModel().getValue("aos_lastuser"));
		} else {
			FndError fndError = new FndError("前置节点不允许退回!");
			throw fndError;
		}

		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");

	}

	/** 初始化事件 **/
	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		setHeadTable();
		StatusControl();
	}

	/** 新建事件 **/
	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		StatusControl();
		InitDefualt();
	}

	/** 界面关闭事件 **/
	public void beforeClosed(BeforeClosedEvent e) {
		super.beforeClosed(e);
		e.setCheckDataChange(true);
	}

	/** 值改变事件 **/
	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		if (name.equals("aos_itemid"))
			AosItemChange();
	}

	/** 值校验 **/
	private static void SaveControl(DynamicObject dy_main) throws FndError {
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		Object aos_status = dy_main.get("aos_status");
		DynamicObject dy_firstRow = dy_main.getDynamicObjectCollection("aos_entryentity").get(0);
		Object aos_itemid = dy_firstRow.get("aos_itemid");
		Object aos_desreq = dy_firstRow.get("aos_desreq");
		Object aos_type = dy_main.get("aos_type");
		Object aos_3d = dy_firstRow.get("aos_3d");

		// 校验 物料信息 新建状态下必填
		if ("申请人".equals(aos_status)) {
			if (Cux_Common_Utl.IsNull(aos_itemid)) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "物料信息必填!");
			}
			if (Cux_Common_Utl.IsNull(aos_desreq)) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "设计要求必填!");
			}
			if (Cux_Common_Utl.IsNull(aos_type)) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "任务类型必填!");
			}
		}

		if ("设计".equals(aos_status)) {
			if ((Boolean) aos_3d) {
				// 勾选3D建模 必填字段
			}
		}

		if (ErrorCount > 0) {
			FndError fndError = new FndError(ErrorMessage);
			throw fndError;
		}

	}

	/** 全局状态控制 **/
	private void StatusControl() {
		// 数据层
		Object AosStatus = this.getModel().getValue("aos_status");
		DynamicObject AosUser = (DynamicObject) this.getModel().getValue("aos_user");
		Object CurrentUserId = UserServiceHelper.getCurrentUserId();
		Object CurrentUserName = UserServiceHelper.getUserInfoByID((long) CurrentUserId).get("name");

		// 图片控制
		// InitPic();

		// 锁住需要控制的字段
		this.getView().setVisible(false, "aos_return");
		// 当前节点操作人不为当前用户 全锁
		if (!AosUser.getPkValue().toString().equals(CurrentUserId.toString())
				&& !"龚凯".equals(CurrentUserName.toString()) && !"刘中怀".equals(CurrentUserName.toString())
				&& !"程震杰".equals(CurrentUserName.toString()) && !"陈聪".equals(CurrentUserName.toString())
				&& !"邹地".equals(CurrentUserName.toString())) {
			this.getView().setEnable(false, "contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");
			this.getView().setVisible(false, "aos_submit");
			return;
		}
		// 状态控制
		if ("申请人".equals(AosStatus)) {
			this.getView().setVisible(true, "bar_save");
			this.getView().setVisible(true, "aos_submit");
		} else if ("设计".equals(AosStatus)) {
			this.getView().setVisible(true, "bar_save");
			this.getView().setVisible(true, "aos_submit");
		} else if ("3D建模".equals(AosStatus)) {
			this.getView().setVisible(false, "bar_save");
			this.getView().setVisible(false, "aos_submit");
		} else if ("设计确认3D".equals(AosStatus)) {
			Map<String, Object> map = new HashMap<>();
			map.put(ClientProperties.Text, new LocaleString("设计确认"));
			this.getView().updateControlMetadata("aos_submit", map);
			this.getView().setVisible(true, "aos_submit");
			this.getView().setEnable(true, "contentpanelflex");// 主界面面板
			this.getView().setVisible(true, "bar_save");
		} else if ("功能图翻译".equals(AosStatus)) {
			this.getView().setVisible(false, "aos_submit");
			this.getView().setEnable(false, "contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");
		} else if ("设计确认:翻译".equals(AosStatus)) {
			Map<String, Object> map = new HashMap<>();
			map.put(ClientProperties.Text, new LocaleString("设计确认"));
			this.getView().updateControlMetadata("aos_submit", map);
			this.getView().setVisible(true, "aos_submit");
			this.getView().setEnable(true, "contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");
		} else if ("组长确认".equals(AosStatus)) {
			Map<String, Object> map = new HashMap<>();
			map.put(ClientProperties.Text, new LocaleString("组长确认"));
			this.getView().updateControlMetadata("aos_submit", map);
			this.getView().setVisible(true, "aos_submit");
			this.getView().setEnable(false, "contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");
		} else if ("申请人确认".equals(AosStatus)) {
			Map<String, Object> map = new HashMap<>();
			map.put(ClientProperties.Text, new LocaleString("申请人确认"));
			this.getView().updateControlMetadata("aos_submit", map);
			this.getView().setVisible(true, "aos_submit");
			this.getView().setEnable(false, "contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");
			this.getView().setVisible(true, "aos_return");
		} else if ("结束".equals(AosStatus)) {
			this.getView().setVisible(false, "aos_submit");
			this.getView().setVisible(false, "bar_save");
			this.getView().setVisible(false, "aos_close");
			this.getView().setVisible(false, "aos_refresh");
			this.getView().setEnable(false, "contentpanelflex");// 主界面面板
		}
	}

	/** 新建设置默认值 **/
	private void InitDefualt() {
		long CurrentUserId = UserServiceHelper.getCurrentUserId();
		List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(CurrentUserId);
		if (MapList != null) {
			if (MapList.get(2) != null)
				this.getModel().setValue("aos_organization1", MapList.get(2).get("id"));
			if (MapList.get(3) != null)
				this.getModel().setValue("aos_organization2", MapList.get(3).get("id"));
		}
	}

	/** 物料值改变 **/
	private void AosItemChange() {
		int CurrentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_entryentity");
		Object aos_itemid = this.getModel().getValue("aos_itemid", CurrentRowIndex);
		if (aos_itemid == null) {
			// 清空值
			this.getModel().setValue("aos_segment3", null, 0);
			this.getModel().setValue("aos_itemname", null, 0);
			this.getModel().setValue("aos_brand", null, 0);
			this.getModel().setValue("aos_developer", null, 0);
			this.getModel().setValue("aos_seting1", null, 0);
			this.getModel().setValue("aos_seting2", null, 0);
			this.getModel().setValue("aos_spec", null, 0);
			this.getModel().setValue("aos_url", null, 0);
			this.getModel().setValue("aos_pic", null, 0);
			this.getModel().setValue("aos_sellingpoint", null, 0);
			this.getModel().setValue("aos_is_saleout", false, 0); // 是否爆品
			this.getModel().setValue("aos_is_design",false,0);		//生成3d
			this.getModel().setValue("aos_productstyle_new",null,0);
			this.getModel().setValue("aos_shootscenes",null,0);
		} else {
			DynamicObject AosItemidObject = (DynamicObject) aos_itemid;
			Object fid = AosItemidObject.getPkValue();
			String aos_contrybrandStr = "";
			String aos_orgtext = "";
			DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(fid, "bd_material");
			DynamicObjectCollection aos_contryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
			// 获取所有国家品牌 字符串拼接 终止
			Set<String> set_bra = new HashSet<>();
			for (DynamicObject aos_contryentry : aos_contryentryS) {
				DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
				String aos_nationalitynumber = aos_nationality.getString("number");
				if ("IE".equals(aos_nationalitynumber))
					continue;
				Object org_id = aos_nationality.get("id"); // ItemId
				int OsQty = iteminfo.GetItemOsQty(org_id, fid);
				int SafeQty = iteminfo.GetSafeQty(org_id);
				// 安全库存 海外库存
				if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
					continue;
				// 代卖、小于安全库存
				if ("F".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
					continue;
				// 虚拟上架、小于安全库存
				if ("H".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
					continue;
				
				
				aos_orgtext = aos_orgtext + aos_nationalitynumber + ";";

				Object obj = aos_contryentry.getDynamicObject("aos_contrybrand");
				if (obj == null)
					continue;
				String value = aos_nationalitynumber + "~"
						+ aos_contryentry.getDynamicObject("aos_contrybrand").getString("number");
				String bra = aos_contryentry.getDynamicObject("aos_contrybrand").getString("number");
				if (bra != null)
					set_bra.add(bra);
				if (set_bra.size() > 1) {
					if (!aos_contrybrandStr.contains(value))
						aos_contrybrandStr = aos_contrybrandStr + value + ";";
				} else if (set_bra.size() == 1) {
					aos_contrybrandStr = bra;
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
			this.getModel().setValue("aos_sub_item", fid, 0);
			this.getModel().setValue("aos_segment3", aos_productno, 0);
			this.getModel().setValue("aos_itemname", aos_itemname, 0);
			this.getModel().setValue("aos_brand", aos_contrybrandStr, 0);
			this.getModel().setValue("aos_pic", url, 0);
			this.getModel().setValue("aos_developer", bd_material.get("aos_developer"), 0);// 开发
			this.getModel().setValue("aos_seting1", bd_material.get("aos_seting_cn"), 0);
			this.getModel().setValue("aos_seting2", bd_material.get("aos_seting_en"), 0);
			this.getModel().setValue("aos_spec", bd_material.get("aos_specification_cn"), 0);
			this.getModel().setValue("aos_url", MKTS3PIC.GetItemPicture(item_number), 0);
			this.getModel().setValue("aos_broitem", aos_broitem, 0);
			this.getModel().setValue("aos_orgtext", aos_orgtext, 0);
			this.getModel().setValue("aos_sellingpoint", bd_material.get("aos_sellingpoint"), 0);
			this.getModel().setValue("aos_is_saleout", ProgressUtil.Is_saleout(fid), 0);
			StringJoiner productStyle = new StringJoiner(";");
			DynamicObjectCollection item = bd_material.getDynamicObjectCollection("aos_productstyle_new");
			if(item.size() != 0){
				List<Object> id = item.stream().map(e -> e.getDynamicObject("fbasedataid").getPkValue()).collect(Collectors.toList());
				for(Object a : id) {
					DynamicObject dysty = QueryServiceHelper.queryOne("aos_product_style","id,name",
							new QFilter("id", QCP.equals,a).toArray());
					String styname = dysty.getString("name");
					productStyle.add(styname);
				}
				this.getModel().setValue("aos_productstyle_new", productStyle.toString(), 0);
			}
			this.getModel().setValue("aos_shootscenes", bd_material.getString("aos_shootscenes"), 0);

			//设置是否已经3d建模
			List<String> designItem = (List<String>) SerializationUtils.fromJsonStringToList(getPageCache().get(KEY_CreateDesign), String.class);
			this.getModel().setValue("aos_is_design",designItem.contains(String.valueOf(fid)),0);

			// 产品类别
			String category = MKTCom.getItemCateNameZH(fid);
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
				String type = "";
				if (this.getModel().getValue("aos_type") != null)
					type = this.getModel().getValue("aos_type").toString();
				Object orgid = null;
				if (this.getModel().getValue("aos_orgid") != null) {
					orgid = this.getModel().getDataEntity(true).getDynamicObject("aos_orgid").getPkValue();
				}
				String[] fields = new String[] { "aos_designeror", "aos_3d", "aos_eng" };
				DynamicObject aos_mkt_proguser = ProgressUtil.findDesignerByType(orgid, AosCategory1, AosCategory2,
						type, fields);
				if (aos_mkt_proguser != null) {
					this.getModel().setValue("aos_designer", aos_mkt_proguser.get("aos_designer"));
					this.getModel().setValue("aos_dm", aos_mkt_proguser.get("aos_designeror"));
					this.getModel().setValue("aos_3der", aos_mkt_proguser.get("aos_3d"));
				}
			}
		}
	}

	/** 提交 **/
	public void aos_submit(DynamicObject dy_main, String type) throws FndError {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			SaveControl(dy_main);// 先做数据校验判断是否可以提交
			String aos_status = dy_main.getString("aos_status");// 根据状态判断当前流程节点
			switch (aos_status) {
				case "申请人":
					SubmitForNew(dy_main);
					break;
				case "设计":
					SubmitForDesign(dy_main);
					break;
				case "3D建模":
					SubmitFor3D(dy_main);
					break;
				case "设计确认:翻译":
					SubmitForTrans(dy_main);
					break;
				case "设计确认3D":
					SubmitForConfirm(dy_main);
					break;
				case "组长确认":
					SubmitForConfirmDm(dy_main);
					break;
				case "申请人确认":
					SubmitForConfirmReq(dy_main, type);
					break;
			}
			SaveServiceHelper.save(new DynamicObject[] { dy_main });
			setEntityValue(dy_main);
			FndHistory.Create(dy_main, "提交", aos_status);
			if (type.equals("A")) {
				this.getView().invokeOperation("refresh");
				StatusControl();// 提交完成后做新的界面状态控制
			}
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/**
	 * 目前给当前操作人赋值
	 * 
	 * @param dy_main 设计需求表
	 */
	public static void setEntityValue(DynamicObject dy_main) {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			setUser(dy_main);
			setErrorList(dy_main);
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	private static void setUser(DynamicObject dy_main) {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			Object aos_user = dy_main.get("aos_user");
			Object userID;
			if (aos_user instanceof Long) {
				userID = aos_user;
			} else if (aos_user instanceof DynamicObject) {
				userID = ((DynamicObject) aos_user).get("id");
			} else if (aos_user instanceof EntryProp) {
				userID = ((DynamicObject) aos_user).get("id");
			} else if (aos_user instanceof String) {
				userID = aos_user;
			} else
				return;
			List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(userID);
			if (MapList != null) {
				if (MapList.size() >= 3 && MapList.get(2) != null)
					dy_main.set("aos_userorganizat1", MapList.get(2).get("id"));
				if (MapList.size() >= 4 && MapList.get(3) != null)
					dy_main.set("aos_userorganizat2", MapList.get(3).get("id"));
				if (MapList.size() >= 5 && MapList.get(4) != null)
					dy_main.set("aos_userorganizat3", MapList.get(4).get("id"));
			}
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/** 设置改错任务清单 **/
	public static void setErrorList(DynamicObject dy_main) {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			String aos_status = dy_main.getString("aos_status");
			if (!aos_status.equals("结束")) {
				return;
			}
			String aos_type = dy_main.getString("aos_type");
			if (!ErrorListEntity.errorListType.contains(aos_type)) {
				return;
			}
			String billno = dy_main.getString("billno");

			DynamicObject aos_orgid = dy_main.getDynamicObject("aos_orgid");
			if (aos_orgid == null) {
				CountryDao countryDao = new CountryDaoImpl();
				DynamicObjectCollection dyc_ent = dy_main.getDynamicObjectCollection("aos_entryentity");
				for (DynamicObject dy : dyc_ent) {
					DynamicObject aos_itemid = dy.getDynamicObject("aos_itemid");
					if (aos_itemid == null)
						continue;
					DynamicObject dy_sub = dy.getDynamicObjectCollection("aos_subentryentity").get(0);
					String orgtext = dy_sub.getString("aos_orgtext");
					if (Cux_Common_Utl.IsNull(orgtext))
						continue;

					String[] split = orgtext.split(";");
					for (String org : split) {
						String countryID = countryDao.getCountryID(org);
						ErrorListEntity errorListEntity = new ErrorListEntity(billno, aos_type, countryID,
								aos_itemid.getString("id"));
						errorListEntity.save();
					}
				}
			} else {
				String orgid = aos_orgid.getString("id");
				DynamicObjectCollection dyc_ent = dy_main.getDynamicObjectCollection("aos_entryentity");
				for (DynamicObject dy : dyc_ent) {
					DynamicObject aos_itemid = dy.getDynamicObject("aos_itemid");
					if (aos_itemid == null)
						continue;
					ErrorListEntity errorListEntity = new ErrorListEntity(billno, aos_type, orgid,
							aos_itemid.getString("id"));
					errorListEntity.save();
				}
			}
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/** 申请人确认状态下提交 **/
	private static void SubmitForNew(DynamicObject dy_main) {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			// 异常参数
			int ErrorCount = 0;
			String ErrorMessage = "";

			// 数据层
			Object FId = dy_main.getPkValue(); // 当前界面主键
			Object AosBillno = dy_main.get("billno");
			Object aos_designer = dy_main.get("aos_designer");

			if (aos_designer == null) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "设计为空,流程无法流转!");
			}
			if (ErrorCount > 0) {
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}

			// 执行保存操作
			dy_main.set("aos_status", "设计");// 设置单据流程状态

			dy_main.set("aos_receivedate", new Date());// 设计接收日期

			dy_main.set("aos_user", aos_designer);// 设置操作人为设计

			String MessageId = null;
			MessageId = ((DynamicObject) aos_designer).getPkValue().toString();

			// 发送消息
			MKTCom.SendGlobalMessage(MessageId, aos_mkt_designreq, FId + "", AosBillno + "", "设计需求表-设计节点");

		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/** 设计状态下提交 **/
	private static void SubmitForDesign(DynamicObject dy_main) {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
// 信息参数
			String MessageId = null;
			String Message = "";
			// 异常参数
			int ErrorCount = 0;
			String ErrorMessage = "";

			// 数据层
			Object FId = dy_main.getPkValue(); // 当前界面主键
			Object AosBillno = dy_main.get("billno");
			Object aos_requireby = dy_main.get("aos_requireby");
			Object aos_3der = dy_main.get("aos_3der");
			Object aos_dm = dy_main.get("aos_dm");
			String aos_type = dy_main.getString("aos_type");
			// 判断行上是否存在3D建模 判断优化项是否存在数字
			Boolean Flag3D = false;
			String AosLanguage = "";

			int total = 0;
			DynamicObjectCollection aos_entryentityS = dy_main.getDynamicObjectCollection("aos_entryentity");
			List<DynamicObject> List3D = new ArrayList<DynamicObject>();
			List<DynamicObject> ListListing = new ArrayList<DynamicObject>();
			List<DynamicObject> ListLanguageListing = new ArrayList<DynamicObject>();

			for (DynamicObject aos_entryentity : aos_entryentityS) {
				Boolean aos_3d = aos_entryentity.getBoolean("aos_3d");
				String aos_language = aos_entryentity.getString("aos_language");
				int aos_whited = aos_entryentity.getInt("aos_whited");
				int aos_whites = aos_entryentity.getInt("aos_whites");
				int aos_backround = aos_entryentity.getInt("aos_backround");
				int aos_funcpic = aos_entryentity.getInt("aos_funcpic");
				int aos_funclike = aos_entryentity.getInt("aos_funclike");
				int aos_sizepic = aos_entryentity.getInt("aos_sizepic");
				int aos_anew = aos_entryentity.getInt("aos_anew");
				int aos_alike = aos_entryentity.getInt("aos_alike");
				int aos_trans = aos_entryentity.getInt("aos_trans");
				int aos_despic = aos_entryentity.getInt("aos_despic");
				int aos_ps = aos_entryentity.getInt("aos_ps");
				int aos_fix = aos_entryentity.getInt("aos_fix");
				int aos_whitepro = aos_entryentity.getInt("aos_whitepro");
				int aos_proground = aos_entryentity.getInt("aos_proground");
				int aos_detailpic = aos_entryentity.getInt("aos_detailpic");

				// 优化项内数字
				int linetotal = aos_whited + aos_whites + aos_backround + aos_funcpic + aos_funclike + aos_sizepic
						+ aos_anew + aos_alike + aos_trans + aos_despic + aos_ps + aos_fix + aos_whitepro + aos_proground
						+ aos_detailpic;
				total += linetotal;

				// 存在一个则确认为3D建模
				if (aos_3d) {
					Flag3D = true;
					List3D.add(aos_entryentity);
				}
				// 功能图翻译语种
				if (!(aos_language == null || aos_language.equals("") || aos_language.equals("null"))) {
					if ("EN".equals(aos_language) || "US".equals(aos_language) || "CA".equals(aos_language)
							|| "UK".equals(aos_language)) {
						ListListing.add(aos_entryentity);
					} else if ("DE/FR/IT/ES/PT/RO".contains(aos_language + "") && !"".equals(aos_language)) {
						ListLanguageListing.add(aos_entryentity);
					}
					if (AosLanguage == null || "".equals(AosLanguage) || "null".equals(AosLanguage)) {
						AosLanguage = aos_language;
					} else if (!AosLanguage.equals(aos_language)) {
						ErrorCount++;
						ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "同单非空功能图翻译语种翻译必须相同!");
					}
				}
			}

			if (Flag3D && aos_3der == null) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "3D设计师为空,流程无法流转!");
			} else if (!Flag3D && aos_requireby == null && !"新品设计".equals(aos_type)) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "申请人为空,流程无法流转!");
			} else if (!Flag3D && aos_dm == null && "新品设计".equals(aos_type)) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "设计组长为空,流程无法流转!");
			}

			if (ErrorCount > 0) {
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}

			dy_main.set("aos_designby", RequestContext.get().getCurrUserId());
			dy_main.set("aos_design_date", new Date());

			// 设置单据流程状态
			if (Flag3D) {
				// 1.是否3D建模=是，走3D建模节点
				dy_main.set("aos_status", "3D建模");
				dy_main.set("aos_user", aos_3der);
				MessageId = ((DynamicObject) aos_3der).getPkValue().toString();
				Message = "设计需求表-3D建模";
				// 同时创建3D产品设计单
				aos_mkt_3design_bill.Generate3Design(List3D, dy_main);
			} else if ((!Flag3D) && (AosLanguage == null || "".equals(AosLanguage))
					&& ("翻译".equals(aos_type) || "四者一致".equals(aos_type))) {
				// 2.是否3D建模=否，功能图翻译语种=空，且任务类型=翻译或者四者一致，流程到结束节点 并生成Listing优化销售确认单
				GenerateListingSal(dy_main, "A");
				dy_main.set("aos_status", "结束");
				dy_main.set("aos_user", system);
				MessageId = ((DynamicObject) aos_requireby).getPkValue().toString();
				Message = "设计需求表-自动结束!";
			} else if ((!Flag3D) && (AosLanguage == null || "".equals(AosLanguage)) && total > 0) {
				// 3.优化项内有数字，是否3D建模=否，功能图翻译语种=空，到申请人确认节点
				if ("新品设计".equals(aos_type)) {
					dy_main.set("aos_status", "组长确认");
					dy_main.set("aos_user", aos_dm);
					MessageId = ((DynamicObject) aos_dm).getPkValue().toString();
					Message = "设计需求表-组长确认!";
				} else if ("老品优化".equals(aos_type)) { // 老品优化
					dy_main.set("aos_laststatus", dy_main.get("aos_status"));
					dy_main.set("aos_lastuser", dy_main.get("aos_user"));
					dy_main.set("aos_status", "申请人确认");
					dy_main.set("aos_user", aos_requireby);
					MessageId = ((DynamicObject) aos_requireby).getPkValue().toString();
					Message = "设计需求表-申请人确认!";
				} else {
					// 其他结束
					dy_main.set("aos_status", "结束");
					dy_main.set("aos_user", system);
					MessageId = ((DynamicObject) aos_requireby).getPkValue().toString();
					Message = "设计需求表-结束";
				}
			} else if (AosLanguage != null && ("EN".equals(AosLanguage) || "US".equals(AosLanguage)
					|| "CA".equals(AosLanguage) || "UK".equals(AosLanguage))) {
				// 4.功能图翻译语种=EN，走功能图翻译节点
				long aos_editor = GenerateListing(dy_main, ListListing);// 同时创建 Listing优化需求表子表
				dy_main.set("aos_status", "功能图翻译");
				dy_main.set("aos_user", aos_editor);// 编辑
				MessageId = aos_editor + "";
				Message = "设计需求表-功能图翻译";
			} else if (AosLanguage != null && "DE/FR/IT/ES/PT/RO".contains(AosLanguage + "") && total > 0) {
				GenerateListingLanguage(dy_main, ListLanguageListing);// 同时触发生成listing优化需求表-小语种
				// 5.功能图翻译语种=DE/FR/IT/ES时，优化项内有数字的到申请人确认节点
				if ("新品设计".equals(aos_type)) {
					dy_main.set("aos_status", "组长确认");
					dy_main.set("aos_user", aos_dm);
					MessageId = ((DynamicObject) aos_dm).getPkValue().toString();
					Message = "设计需求表-组长确认";
				} else if ("老品优化".equals(aos_type)) {
					dy_main.set("aos_status", "申请人确认");
					dy_main.set("aos_user", aos_requireby);
					MessageId = ((DynamicObject) aos_requireby).getPkValue().toString();
					Message = "设计需求表-申请人确认";
				} else {
					dy_main.set("aos_status", "结束");
					dy_main.set("aos_user", system);
					MessageId = ((DynamicObject) aos_requireby).getPkValue().toString();
					Message = "设计需求表-结束";
				}
			} else if (AosLanguage != null && "DE/FR/IT/ES/PT/RO".contains(AosLanguage + "") && total == 0) {
				// 6.功能图翻译语种=DE/FR/IT/ES时，优化项内没有数字的流程到结束节点
				GenerateListingLanguage(dy_main, ListLanguageListing);// 同时触发生成listing优化需求表-小语种
				dy_main.set("aos_status", "结束");
				dy_main.set("aos_user", system);
				MessageId = ((DynamicObject) aos_requireby).getPkValue().toString();
				Message = "设计需求表-自动结束!";
			} else if (!Flag3D) {
				if ("新品设计".equals(aos_type)) {
					dy_main.set("aos_status", "组长确认");
					dy_main.set("aos_user", aos_dm);
					MessageId = ((DynamicObject) aos_dm).getPkValue().toString();
					Message = "设计需求表-组长确认";
				} else if ("老品优化".equals(aos_type)) {
					dy_main.set("aos_laststatus", dy_main.get("aos_status"));
					dy_main.set("aos_lastuser", dy_main.get("aos_user"));
					dy_main.set("aos_status", "申请人确认");
					dy_main.set("aos_user", aos_requireby);
					MessageId = ((DynamicObject) aos_requireby).getPkValue().toString();
					Message = "设计需求表-申请人确认";
				} else {
					dy_main.set("aos_status", "结束");
					dy_main.set("aos_user", system);
					MessageId = ((DynamicObject) aos_requireby).getPkValue().toString();
					Message = "设计需求表-结束";
				}
			}
			// 发送消息
			MKTCom.SendGlobalMessage(MessageId, aos_mkt_designreq, FId + "", AosBillno + "", Message);
			if ("申请人确认".equals(dy_main.getString("aos_status"))){
				GlobalMessage.SendMessage(AosBillno + "-设计需求单据待申请人确认", MessageId);
			}
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/** 3D建模状态下提交 **/
	private static void SubmitFor3D(DynamicObject dy_main) {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			// 信息参数
			String MessageId = null;
			String Message = "";
			// 异常参数
			int ErrorCount = 0;
			String ErrorMessage = "";
			// 数据层
			Object FId = dy_main.getPkValue(); // 当前界面主键
			Object AosBillno = dy_main.get("billno");
			// 先取制作设计师若为空，则取设计师
			Object aos_designer = dy_main.get("aos_designby");
			if (aos_designer == null)
				aos_designer = dy_main.get("aos_designer");
			if (aos_designer == null) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "设计为空,流程无法流转!");
			}
			if (ErrorCount > 0) {
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
			// 设置单据流程状态
			dy_main.set("aos_status", "设计确认3D");
			dy_main.set("aos_receivedate", new Date());// 设计接收日期
			dy_main.set("aos_user", aos_designer);
			MessageId = ((DynamicObject) aos_designer).getPkValue().toString();
			Message = "设计需求表-设计确认3D";
			// 发送消息
			MKTCom.SendGlobalMessage(MessageId, aos_mkt_designreq, FId + "", AosBillno + "", Message);
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/** 设计确认3D状态下提交 **/
	private static void SubmitForConfirm(DynamicObject dy_main) {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			// 信息参数
			String MessageId = null;
			String Message = "";
			// 异常参数
			int ErrorCount = 0;
			String ErrorMessage = "";

			// 数据层
			Object FId = dy_main.getPkValue(); // 当前界面主键
			Object AosBillno = dy_main.get("billno");
			Object aos_requireby = dy_main.get("aos_requireby");
			Object aos_dm = dy_main.get("aos_dm");
			String aos_type = dy_main.getString("aos_type");

			if (!"新品设计".equals(aos_type) && aos_requireby == null) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "申请人为空,流程无法流转!");
			}

			if ("新品设计".equals(aos_dm) && aos_requireby == null) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "设计组长为空,流程无法流转!");
			}

			if (ErrorCount > 0) {
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
			// 设置单据流程状态
			if ("新品设计".equals(aos_type)) {
				dy_main.set("aos_status", "组长确认");
				dy_main.set("aos_user", aos_dm);
				MessageId = ((DynamicObject) aos_dm).getPkValue().toString();
				Message = "设计需求表-组长确认";
			} else if ("老品优化".equals(aos_type)) {
				dy_main.set("aos_laststatus", dy_main.get("aos_status"));
				dy_main.set("aos_lastuser", dy_main.get("aos_user"));
				dy_main.set("aos_status", "申请人确认");
				dy_main.set("aos_user", aos_requireby);
				MessageId = ((DynamicObject) aos_requireby).getPkValue().toString();
				Message = "设计需求表-申请人确认";
			} else {
				// 老品重拍回写拍照
				if ("PHOTO".equals(dy_main.getString("aos_sourcetype")))
				{
					DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingleFromCache("aos_mkt_photoreq",
							new QFilter("billno", QCP.equals, dy_main.getString("aos_sourcebillno"))
									.and("aos_type", QCP.equals, "视频").toArray());
					if (FndGlobal.IsNotNull(aos_mkt_photoreq)) {
						aos_mkt_photoreq.set("aos_user", aos_mkt_photoreq.get("aos_vedior"));
						OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq",
								new DynamicObject[] { aos_mkt_photoreq }, OperateOption.create());
					}
				}
				dy_main.set("aos_status", "结束");
				dy_main.set("aos_user", system);
				MessageId = ((DynamicObject) aos_requireby).getPkValue().toString();
				Message = "设计需求表-结束";
			}
			// 发送消息
			dy_main.set("aos_designby", RequestContext.get().getCurrUserId());
			dy_main.set("aos_design_date", new Date());
			MKTCom.SendGlobalMessage(MessageId, aos_mkt_designreq, FId + "", AosBillno + "", Message);
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
		}

	/** 设计确认翻译状态下提交 **/
	private static void SubmitForTrans(DynamicObject dy_main) {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			// 信息参数
			String MessageId = null;
			String Message = "";
			// 异常参数
			int ErrorCount = 0;
			String ErrorMessage = "";

			// 数据层
			Object FId = dy_main.getPkValue(); // 当前界面主键
			Object AosBillno = dy_main.get("billno");
			Object aos_requireby = dy_main.get("aos_requireby");
			Object aos_dm = dy_main.get("aos_dm");
			String aos_type = dy_main.getString("aos_type");

			if (!"新品设计".equals(aos_type) && aos_requireby == null) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "申请人为空,流程无法流转!");
			}

			if ("新品设计".equals(aos_dm) && aos_requireby == null) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "设计组长为空,流程无法流转!");
			}

			if (ErrorCount > 0) {
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
			// 设置单据流程状态
			if ("新品设计".equals(aos_type)) {
				dy_main.set("aos_status", "组长确认");
				dy_main.set("aos_user", aos_dm);
				MessageId = ((DynamicObject) aos_dm).getPkValue().toString();
				Message = "设计需求表-组长确认";
			} else if ("老品优化".equals(aos_type)) {
				dy_main.set("aos_laststatus", dy_main.get("aos_status"));
				dy_main.set("aos_lastuser", dy_main.get("aos_user"));
				dy_main.set("aos_status", "申请人确认");
				dy_main.set("aos_user", aos_requireby);
				MessageId = ((DynamicObject) aos_requireby).getPkValue().toString();
				Message = "设计需求表-申请人确认";
			} else {
				dy_main.set("aos_status", "结束");
				dy_main.set("aos_user", system);
				MessageId = ((DynamicObject) aos_requireby).getPkValue().toString();
				Message = "设计需求表-结束";
			}
			dy_main.set("aos_designby", RequestContext.get().getCurrUserId());
			dy_main.set("aos_design_date", new Date());
			// 发送消息
			MKTCom.SendGlobalMessage(MessageId, aos_mkt_designreq, FId + "", AosBillno + "", Message);
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/** 组长确认 **/
	private static void SubmitForConfirmDm(DynamicObject dy_main) {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			// 信息参数
			String MessageId = null;
			String Message = "";
			// 异常参数
			int ErrorCount = 0;
			String ErrorMessage = "";
			// 数据层
			Object FId = dy_main.getPkValue(); // 当前界面主键
			Object AosBillno = dy_main.get("billno");
			Object aos_requireby = dy_main.get("aos_requireby");

			if (aos_requireby == null) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "申请人为空,流程无法流转!");
			}
			if (ErrorCount > 0) {
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
			// 设置单据流程状态
			dy_main.set("aos_laststatus", dy_main.get("aos_status"));
			dy_main.set("aos_lastuser", dy_main.get("aos_user"));
			dy_main.set("aos_status", "申请人确认");
			dy_main.set("aos_user", aos_requireby);
			MessageId = ((DynamicObject) aos_requireby).getPkValue().toString();
			Message = "设计需求表-申请人确认";
			// 发送消息
			MKTCom.SendGlobalMessage(MessageId, aos_mkt_designreq, FId + "", AosBillno + "", Message);
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/** 申请人确认 **/
	private static void SubmitForConfirmReq(DynamicObject dy_main, String type) throws FndError {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			Object aos_orgid = dy_main.get("aos_orgid");
			Object aos_sourcetype = dy_main.get("aos_sourcetype");
			Object aos_type = dy_main.get("aos_type");
			// A+生成逻辑
			if ("新品设计".equals(aos_type)) {
				DynamicObjectCollection aos_entryentityS = dy_main.getDynamicObjectCollection("aos_entryentity");
				for (DynamicObject aos_entryentity : aos_entryentityS) {
					DynamicObject aos_itemid = aos_entryentity.getDynamicObject("aos_itemid");
					String aos_productno = aos_itemid.getString("aos_productno");
					Boolean exists1 = QueryServiceHelper.exists("aos_mkt_addtrack",
							new QFilter("aos_itemid.aos_productno", QCP.equals, aos_productno));
					Boolean exists2 = QueryServiceHelper.exists("aos_mkt_addtrack",
							new QFilter("aos_itemid", QCP.equals, aos_itemid.getPkValue()));
					// A+跟踪表中不存在物料 存在产品号
					if (exists1 && !exists2) {
						// 循环
						DynamicObjectCollection aos_subentryentityS = aos_entryentity
								.getDynamicObjectCollection("aos_subentryentity");
						DynamicObject aos_subentryentity = aos_subentryentityS.get(0);
						String aos_orgtext = aos_subentryentity.getString("aos_orgtext");
						if (Cux_Common_Utl.IsNull(aos_orgtext))
							continue;
						String[] aos_orgtextArray = aos_orgtext.split(";");
						for (int i = 0; i < aos_orgtextArray.length; i++) {
							String org = aos_orgtextArray[i];
							// 判断国别类型为英语还是小语种
							if ("US,CA,UK".contains(org)) {
								// 判断同产品号是否有英语国别制作完成
								Boolean exists3 = QueryServiceHelper.exists("aos_mkt_addtrack",
										new QFilter("aos_itemid.aos_productno", QCP.equals, aos_productno)
												.and(new QFilter("aos_us", QCP.equals, true).or("aos_ca", QCP.equals, true)
														.or("aos_uk", QCP.equals, true))
												.toArray());
								// 存在英语国别制作完成 到05节点 不存在 到04节点
								if (exists3) {
									aos_mkt_aadd_bill.generateAddFromDesign(aos_itemid, org, "EN_05");
								} else {
									aos_mkt_aadd_bill.generateAddFromDesign(aos_itemid, org, "EN_04");
								}
							} else if ("DE,FR,IT,ES".contains(org)) {
								// 判断同产品号是否有小语种国别制作完成
								Boolean exists3 = QueryServiceHelper.exists("aos_mkt_addtrack",
										new QFilter("aos_itemid.aos_productno", QCP.equals, aos_productno)
												.and(new QFilter("aos_de", QCP.equals, true).or("aos_fr", QCP.equals, true)
														.or("aos_it", QCP.equals, true).or("aos_es", QCP.equals, true))
												.toArray());
								// 存在小语种国别制作完成 到04节点 不存在 到02节点
								if (exists3) {
									aos_mkt_aadd_bill.generateAddFromDesign(aos_itemid, org, "SM_04");
								} else {
									aos_mkt_aadd_bill.generateAddFromDesign(aos_itemid, org, "SM_02");
								}
							}
						}
					}
				}
			}

			if (aos_orgid != null && "LISTING".equals(aos_sourcetype)) {
				// 1.触发生成设计需求表的listing优化需求表上有国别，设计需求表结束后只触发生成本国的设计完成表-国别;
				GenerateDesignHasOu(dy_main);
				// 优化确认单
				GenerateListingSal(dy_main, "B");
			} else if (aos_orgid == null && "LISTING".equals(aos_sourcetype)) {
				// 2.触发生成设计需求表的listing优化需求表上无国别，设计需求表结束后触发生成下单国别的设计完成表-国别;
				GenerateDesignNotHasOu(dy_main);
				// 优化确认单
				GenerateListingSal(dy_main, "C");
			}
			if (type.equals("A"))
				dy_main.set("aos_submitter", "person");
			else {
				dy_main.set("aos_submitter", "system");
			}

			// 老品重拍回写拍照
			if ("PHOTO".equals(dy_main.getString("aos_sourcetype")))
			{
				DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingleFromCache("aos_mkt_photoreq",
						new QFilter("billno", QCP.equals, dy_main.getString("aos_sourcebillno"))
								.and("aos_type", QCP.equals, "视频").toArray());
				if (FndGlobal.IsNotNull(aos_mkt_photoreq)) {
					aos_mkt_photoreq.set("aos_user", aos_mkt_photoreq.get("aos_vedior"));
					OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq",
							new DynamicObject[] { aos_mkt_photoreq }, OperateOption.create());
				}
			}

			// 执行保存操作
			dy_main.set("aos_status", "结束");// 设置单据流程状态
			dy_main.set("aos_user", system);// 设置操作人为系统管理员

		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/**
	 * 创建Listing优化需求表子表
	 * 
	 * @return
	 **/
	private static long GenerateListing(DynamicObject dy_main, List<DynamicObject> ListingEn) throws FndError {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
// 信息参数
			Boolean MessageFlag = false;
			String MessageId = null;
			String Message = "";
			// 异常参数
			int ErrorCount = 0;
			String ErrorMessage = "";
			// 数据层
			DynamicObject AosDesigner = dy_main.getDynamicObject("aos_designer");
			Object AosDesignerId = AosDesigner.getPkValue();
			Object billno = dy_main.get("billno");
			Object ReqFId = dy_main.getPkValue(); // 当前界面主键
			Object aos_type = dy_main.get("aos_type");// 任务类型
			Object aos_source = dy_main.get("aos_source");// 任务来源
			Object aos_importance = dy_main.get("aos_importance");// 紧急程度
			Object aos_requireby = dy_main.get("aos_requireby");// 设计需求表申请人
			Object aos_demandate = new Date();
			Object aos_ismalllov = "否";
			Object aos_orgid = dy_main.get("aos_orgid");
			Object aos_orgnumber = null;
			Object aos_osconfirmlov = "否";

			if (aos_orgid != null)
				aos_orgnumber = ((DynamicObject) aos_orgid).get("number");
			if ("US".equals(aos_orgnumber) || "CA".equals(aos_orgnumber) || "UK".equals(aos_orgnumber))
				aos_ismalllov = "否";
			else
				aos_ismalllov = "是";
			if ("四者一致".equals(aos_type)) {
				aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
				aos_ismalllov = "是";
			} else if ("老品优化".equals(aos_type))
				aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);

			List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(((DynamicObject) aos_requireby).getPkValue());
			long aos_editor = 0;
			// 校验
			if (ListingEn.size() == 0) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "EN功能图翻译行信息不存在!");
			}
			if (ErrorCount > 0) {
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
			// 循环创建
			for (int i = 0; i < ListingEn.size(); i++) {
				DynamicObject dyn3d_r = ListingEn.get(i);
				DynamicObject dyn3d_d = dyn3d_r.getDynamicObjectCollection("aos_subentryentity").get(0);
				// 头信息
				// 根据国别大类中类取对应营销US编辑
				Object ItemId = dyn3d_r.getDynamicObject("aos_itemid").getPkValue();
				String category = MKTCom.getItemCateNameZH(ItemId);
				String[] category_group = category.split(",");
				String AosCategory1 = null;
				String AosCategory2 = null;
				int category_length = category_group.length;
				if (category_length > 0)
					AosCategory1 = category_group[0];
				if (category_length > 1)
					AosCategory2 = category_group[1];
				aos_editor = 0;
				if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
					DynamicObject aos_mkt_proguser = ProgressUtil.findEditorByType(AosCategory1, AosCategory2,
							String.valueOf(aos_type));
					if (aos_mkt_proguser != null) {
						aos_editor = aos_mkt_proguser.getLong("aos_user");
					}
				}
				if (aos_editor == 0) {
					ErrorMessage = FndError.AddErrorMessage(ErrorMessage, AosCategory1 + "," + AosCategory2 + "品类英语编辑不存在!");
					FndError fndMessage = new FndError(ErrorMessage);
					throw fndMessage;
				}
				// 根据国别编辑合并单据
				DynamicObject aos_mkt_listing_son = null;
				QFilter filter_editor = new QFilter("aos_editor", "=", aos_editor);
				QFilter filter_sourceid = new QFilter("aos_sourceid", "=", ReqFId);
				QFilter[] filters = new QFilter[] { filter_editor, filter_sourceid };
				DynamicObject aos_mkt_listing_sonq = QueryServiceHelper.queryOne("aos_mkt_listing_son", "id", filters);
				if (aos_mkt_listing_sonq == null) {
					aos_mkt_listing_son = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_son");
					MessageFlag = true;
					aos_mkt_listing_son.set("aos_type", aos_type);
					aos_mkt_listing_son.set("aos_source", aos_source);
					aos_mkt_listing_son.set("aos_importance", aos_importance);
					aos_mkt_listing_son.set("aos_designer", AosDesignerId);
					aos_mkt_listing_son.set("aos_editor", aos_editor);
					aos_mkt_listing_son.set("aos_user", aos_editor);
					aos_mkt_listing_son.set("aos_orignbill", billno);
					aos_mkt_listing_son.set("aos_sourceid", ReqFId);
					aos_mkt_listing_son.set("aos_status", "编辑确认");
					aos_mkt_listing_son.set("aos_sourcetype", "DESIGN");
					aos_mkt_listing_son.set("aos_demandate", aos_demandate);// 要求完成日期
					aos_mkt_listing_son.set("aos_ismalllov", aos_ismalllov);
					aos_mkt_listing_son.set("aos_osconfirmlov", aos_osconfirmlov);
					// BOTP
					aos_mkt_listing_son.set("aos_sourcebilltype", aos_mkt_designreq);
					aos_mkt_listing_son.set("aos_sourcebillno", dy_main.get("billno"));
					aos_mkt_listing_son.set("aos_srcentrykey", "aos_entryentity");

					if (!"EN".equals(dyn3d_r.getString("aos_language")))
						aos_mkt_listing_son.set("aos_orgid", aos_orgid);
				} else {
					aos_mkt_listing_son = BusinessDataServiceHelper.loadSingle(aos_mkt_listing_sonq.getLong("id"),
							"aos_mkt_listing_son");
				}
				aos_mkt_listing_son.set("aos_requireby", aos_requireby);// 设计需求表申请人
				aos_mkt_listing_son.set("aos_requiredate", new Date());
				if (MapList != null) {
					if (MapList.get(2) != null)
						aos_mkt_listing_son.set("aos_organization1", MapList.get(2).get("id"));
					if (MapList.get(3) != null)
						aos_mkt_listing_son.set("aos_organization2", MapList.get(3).get("id"));
				}
				// 明细
				DynamicObjectCollection aos_entryentityS = aos_mkt_listing_son
						.getDynamicObjectCollection("aos_entryentity");
				DynamicObject aos_entryentity = aos_entryentityS.addNew();
				aos_entryentity.set("aos_itemid", ItemId);
				aos_entryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(ItemId));
				aos_entryentity.set("aos_require", dyn3d_r.get("aos_desreq"));
				aos_entryentity.set("aos_srcrowseq", dyn3d_r.get("SEQ"));

				// 功能图文案备注
				aos_entryentity.set("aos_remakes", dyn3d_r.get("aos_remakes"));

				DynamicObjectCollection aos_attribute = aos_entryentity.getDynamicObjectCollection("aos_attribute");
				aos_attribute.clear();
				DynamicObjectCollection aos_attributefrom = dyn3d_r.getDynamicObjectCollection("aos_attribute");
				DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
				DynamicObject tempFile = null;
				for (DynamicObject d : aos_attributefrom) {
					tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
					aos_attribute.addNew().set("fbasedataid", tempFile);
				}

				// 物料相关信息
				DynamicObjectCollection aos_subentryentityS = aos_entryentity
						.getDynamicObjectCollection("aos_subentryentity");
				DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
				aos_subentryentity.set("aos_segment3", dyn3d_d.get("aos_segment3"));
				aos_subentryentity.set("aos_broitem", dyn3d_d.get("aos_broitem"));
				aos_subentryentity.set("aos_itemname", dyn3d_d.get("aos_itemname"));
				aos_subentryentity.set("aos_orgtext", dyn3d_d.get("aos_orgtext"));
				aos_subentryentity.set("aos_reqinput", dyn3d_r.get("aos_desreq"));
				aos_entryentity.set("aos_segment3_r", dyn3d_d.get("aos_segment3"));
				aos_entryentity.set("aos_broitem_r", dyn3d_d.get("aos_broitem"));
				aos_entryentity.set("aos_itemname_r", dyn3d_d.get("aos_itemname"));
				aos_entryentity.set("aos_orgtext_r", ProgressUtil.getOrderOrg(ItemId));

				if (MessageFlag) {
					MessageId = AosDesignerId + "";
					Message = "Listing优化需求表子表-设计需求自动创建";
					aos_mkt_listingson_bill.setListSonUserOrganizate(dy_main);
					OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_son",
							new DynamicObject[] { aos_mkt_listing_son }, OperateOption.create());

					// 修复关联关系
					try {
						ProgressUtil.botp("aos_mkt_listing_son", aos_mkt_listing_son.get("id"));
					} catch (Exception ex) {
						ex.printStackTrace();
					}

					if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
						MKTCom.SendGlobalMessage(MessageId, aos_mkt_listing_son + "",
								operationrst.getSuccessPkIds().get(0) + "", aos_mkt_listing_son.getString("billno"),
								Message);
						FndHistory.Create(dy_main, MessageId, "生成文案", aos_mkt_listing_son.getString("billno"));
						FndHistory.Create(aos_mkt_listing_son, "来源-设计需求表", dy_main.getString("billno"));
					}
				}
			}
			return aos_editor;
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/** 创建Listing优化需求表小语种 **/
	private static void GenerateListingLanguage(DynamicObject dy_main, List<DynamicObject> ListingLanguage)
			throws FndError {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			// 信息参数
			String MessageId = null;
			String Message = "";
			// 异常参数
			int ErrorCount = 0;
			String ErrorMessage = "";
			// 数据层
			Object AosDesignerId = parainfo.dynFormat(dy_main.get("aos_designer"));
			Object billno = dy_main.get("billno");
			Object ReqFId = dy_main.getPkValue(); // 当前界面主键
			Object aos_type = dy_main.get("aos_type");// 任务类型
			Object aos_source = dy_main.get("aos_source");// 任务来源
			Object aos_importance = dy_main.get("aos_importance");// 紧急程度
			Object aos_requireby = parainfo.dynFormat(dy_main.get("aos_requireby"));// 设计需求表申请人
			List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(aos_requireby);
			Object LastItemId = null;
			Object LastOrgNumber = null;
			Object aos_demandate = new Date();
			if ("四者一致".equals(aos_type)) {
				aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
			} else if ("老品优化".equals(aos_type))
				aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);

			// 校验
			if (ListingLanguage.size() == 0) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "小语种功能图翻译行信息不存在!");
			}
			if (ErrorCount > 0) {
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}

			DynamicObject aos_mkt_listing_min = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_min");
			aos_mkt_listing_min.set("aos_requireby", aos_requireby);
			aos_mkt_listing_min.set("aos_requiredate", new Date());
			aos_mkt_listing_min.set("aos_type", "功能图翻译");
			aos_mkt_listing_min.set("aos_source", aos_source);
			aos_mkt_listing_min.set("aos_importance", aos_importance);
			aos_mkt_listing_min.set("aos_designer", AosDesignerId);
			aos_mkt_listing_min.set("aos_orignbill", billno);
			aos_mkt_listing_min.set("aos_sourceid", ReqFId);
			aos_mkt_listing_min.set("aos_status", "编辑确认");
			aos_mkt_listing_min.set("aos_sourcetype", "DESIGN");
			aos_mkt_listing_min.set("aos_demandate", aos_demandate);
			aos_mkt_listing_min.set("aos_osconfirmlov", "否");
			// BOTP
			aos_mkt_listing_min.set("aos_sourcebilltype", aos_mkt_designreq);
			aos_mkt_listing_min.set("aos_sourcebillno", dy_main.get("billno"));
			aos_mkt_listing_min.set("aos_srcentrykey", "aos_entryentity");

			if (MapList != null) {
				if (MapList.get(2) != null)
					aos_mkt_listing_min.set("aos_organization1", MapList.get(2).get("id"));
				if (MapList.get(3) != null)
					aos_mkt_listing_min.set("aos_organization2", MapList.get(3).get("id"));
			}

			DynamicObjectCollection mkt_listing_minS = aos_mkt_listing_min.getDynamicObjectCollection("aos_entryentity");
			// 循环所有行
			for (int i = 0; i < ListingLanguage.size(); i++) {
				DynamicObject aos_entryentity = ListingLanguage.get(i);
				DynamicObject mkt_listing_min = mkt_listing_minS.addNew();
				DynamicObject subentryentity = aos_entryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
				LastItemId = aos_entryentity.get("aos_itemid.id");
				LastOrgNumber = aos_entryentity.get("aos_language");
				mkt_listing_min.set("aos_itemid", aos_entryentity.get("aos_itemid"));
				mkt_listing_min.set("aos_is_saleout", ProgressUtil.Is_saleout(LastItemId));
				mkt_listing_min.set("aos_require", aos_entryentity.get("aos_desreq"));
				mkt_listing_min.set("aos_srcrowseq", aos_entryentity.get("SEQ"));
				// 功能图文案备注
				mkt_listing_min.set("aos_remakes", aos_entryentity.get("aos_remakes"));

				// 附件
				DynamicObjectCollection aos_attribute = mkt_listing_min.getDynamicObjectCollection("aos_attribute");
				aos_attribute.clear();
				DynamicObjectCollection aos_attributefrom = aos_entryentity.getDynamicObjectCollection("aos_attribute");
				DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
				DynamicObject tempFile = null;
				for (DynamicObject d : aos_attributefrom) {
					tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
					aos_attribute.addNew().set("fbasedataid", tempFile);
				}
				DynamicObjectCollection aos_subentryentityS = mkt_listing_min
						.getDynamicObjectCollection("aos_subentryentity");
				DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
				aos_subentryentity.set("aos_segment3", subentryentity.get("aos_segment3"));
				aos_subentryentity.set("aos_broitem", subentryentity.get("aos_broitem"));
				aos_subentryentity.set("aos_itemname", subentryentity.get("aos_itemname"));
				aos_subentryentity.set("aos_orgtext", subentryentity.get("aos_orgtext"));
				aos_subentryentity.set("aos_reqinput", aos_entryentity.get("aos_desreq"));

				mkt_listing_min.set("aos_segment3_r", subentryentity.get("aos_segment3"));
				mkt_listing_min.set("aos_broitem_r", subentryentity.get("aos_broitem"));
				mkt_listing_min.set("aos_itemname_r", subentryentity.get("aos_itemname"));
				mkt_listing_min.set("aos_orgtext_r",
						ProgressUtil.getOrderOrg(aos_entryentity.getDynamicObject("aos_itemid").getPkValue()));

			}

			// 根据循环中最后一个物料去获取对应的 英语编辑师 小语种编辑师 LastItemId
			String category = MKTCom.getItemCateNameZH(LastItemId);
			String[] category_group = category.split(",");
			String AosCategory1 = null;
			String AosCategory2 = null;
			int category_length = category_group.length;
			if (category_length > 0)
				AosCategory1 = category_group[0];
			if (category_length > 1)
				AosCategory2 = category_group[1];

			long aos_editor = 0;
			if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
				QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
				QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
				QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2 };
				String SelectStr = "aos_eng aos_editor";
				DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_proguser", SelectStr,
						filters_category);
				if (aos_mkt_proguser != null) {
					aos_editor = aos_mkt_proguser.getLong("aos_editor");
				}
			}
			if (aos_editor == 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, AosCategory1 + "," + AosCategory2 + "英语编辑不存在!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
			long aos_oueditor = 0;
			Object orgid = FndGlobal.get_import_id(LastOrgNumber, "bd_country");
			if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
				DynamicObject aos_mkt_progorguser = ProgressUtil.minListtFindEditorByType(orgid, AosCategory1, AosCategory2,
						"功能图翻译");
				if (aos_mkt_progorguser != null) {
					aos_oueditor = aos_mkt_progorguser.getLong("aos_user");
				}
			}
			if (aos_oueditor == 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, AosCategory1 + "," + AosCategory2 + "小语种编辑师不存在!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
			aos_mkt_listing_min.set("aos_editormin", aos_oueditor);// 小语种编辑师
			aos_mkt_listing_min.set("aos_user", aos_oueditor);
			aos_mkt_listing_min.set("aos_editor", aos_editor);
			aos_mkt_listing_min.set("aos_orgid", orgid);
			// 国别=RO或者PT时，流程直接到小站海外编辑确认:功能图节点
			if (!Cux_Common_Utl.IsNull(LastOrgNumber)) {
				boolean overseasCountry = String.valueOf(LastOrgNumber).equalsIgnoreCase("RO")
						|| String.valueOf(LastOrgNumber).equals("PT");
				if (overseasCountry) {
					aos_mkt_listing_min.set("aos_status", "小站海外编辑确认:功能图");
					aos_mkt_listing_min.set("aos_funcdate", new Date());
				}

			}
			MessageId = aos_oueditor + "";
			Message = "Listing优化需求表小语种-设计需求自动创建";
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
					new DynamicObject[] { aos_mkt_listing_min }, OperateOption.create());

			// 修复关联关系
			ProgressUtil.botp("aos_mkt_listing_min", aos_mkt_listing_min.get("id"));
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
				MKTCom.SendGlobalMessage(MessageId, aos_mkt_listing_min + "", operationrst.getSuccessPkIds().get(0) + "",
						aos_mkt_listing_min.getString("billno"), Message);
				FndHistory.Create(dy_main, MessageId, "生成小语种", aos_mkt_listing_min.getString("billno"));
				FndHistory.Create(aos_mkt_listing_min, "来源-设计需求表", dy_main.getString("billno"));
			}

		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}

	}

	/** 触发生成设计需求表的listing优化需求表上有国别,设计需求表结束后触发生成下单国别的设计完成表-国别 **/
	private static void GenerateDesignHasOu(DynamicObject dy_main) throws FndError {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
// 信息处理
			String ErrorMessage = "";
			String MessageId = null;
			String Message = "";
			// 数据层
			Object aos_designer = dy_main.getDynamicObject("aos_designer").getPkValue();// 设计
			Object aos_designby = dy_main.getDynamicObject("aos_designby").getPkValue();// 制作设计师
			Object billno = dy_main.get("billno");
			Object ReqFId = dy_main.getPkValue(); // 当前界面主键
			Object aos_type = dy_main.get("aos_type");// 任务类型
			Object aos_source = dy_main.get("aos_source");// 任务来源
			Object aos_importance = dy_main.get("aos_importance");// 紧急程度
			Object aos_orgid = parainfo.dynFormat(dy_main.get("aos_orgid")); // 国别

			Boolean aos_3d = null;
			// 循环创建
			DynamicObject aos_mkt_designcmp = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designcmp");
			aos_mkt_designcmp.set("aos_requireby", aos_designby);
			aos_mkt_designcmp.set("aos_designer", aos_designer);
			aos_mkt_designcmp.set("aos_orgid", aos_orgid);
			aos_mkt_designcmp.set("aos_orignbill", billno);
			aos_mkt_designcmp.set("aos_sourceid", ReqFId);
			aos_mkt_designcmp.set("aos_type", aos_type);
			aos_mkt_designcmp.set("aos_source", aos_source);
			aos_mkt_designcmp.set("aos_importance", aos_importance);
			aos_mkt_designcmp.set("aos_requiredate", new Date());
			// BOTP
			aos_mkt_designcmp.set("aos_sourcebilltype", aos_mkt_designreq);
			aos_mkt_designcmp.set("aos_sourcebillno", dy_main.get("billno"));
			aos_mkt_designcmp.set("aos_srcentrykey", "aos_entryentity");

			List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(aos_designer);
			if (MapList != null) {
				if (MapList.get(2) != null)
					aos_mkt_designcmp.set("aos_organization1", MapList.get(2).get("id"));
				if (MapList.get(3) != null)
					aos_mkt_designcmp.set("aos_organization2", MapList.get(3).get("id"));
			}

			DynamicObjectCollection aos_entryentityS = dy_main.getDynamicObjectCollection("aos_entryentity");
			DynamicObjectCollection cmp_entryentityS = aos_mkt_designcmp.getDynamicObjectCollection("aos_entryentity");
			int total = 0;
			for (DynamicObject aos_entryentity : aos_entryentityS) {
				// 优化项无数字 直接跳过
				int aos_whited = aos_entryentity.getInt("aos_whited");
				int aos_whites = aos_entryentity.getInt("aos_whites");
				int aos_backround = aos_entryentity.getInt("aos_backround");
				int aos_funcpic = aos_entryentity.getInt("aos_funcpic");
				int aos_funclike = aos_entryentity.getInt("aos_funclike");
				int aos_sizepic = aos_entryentity.getInt("aos_sizepic");
				int aos_anew = aos_entryentity.getInt("aos_anew");
				int aos_alike = aos_entryentity.getInt("aos_alike");
				int aos_trans = aos_entryentity.getInt("aos_trans");
				int aos_despic = aos_entryentity.getInt("aos_despic");
				int aos_ps = aos_entryentity.getInt("aos_ps");
				int aos_fix = aos_entryentity.getInt("aos_fix");
				int aos_whitepro = aos_entryentity.getInt("aos_whitepro");
				int aos_proground = aos_entryentity.getInt("aos_proground");
				int aos_detailpic = aos_entryentity.getInt("aos_detailpic");
				// 优化项内数字
				int linetotal = aos_whited + aos_whites + aos_backround + aos_funcpic + aos_funclike + aos_sizepic
						+ aos_anew + aos_alike + aos_trans + aos_despic + aos_ps + aos_fix + aos_whitepro + aos_proground
						+ aos_detailpic;
				if (linetotal == 0)
					continue;
				total += aos_whited + aos_backround + aos_funcpic + aos_funclike;

				DynamicObject cmp_entryentity = cmp_entryentityS.addNew();
				cmp_entryentity.set("aos_itemid", aos_entryentity.get("aos_itemid"));
				cmp_entryentity.set("aos_3d", aos_entryentity.get("aos_3d"));
				cmp_entryentity.set("aos_desreq", aos_entryentity.get("aos_desreq"));
				cmp_entryentity.set("aos_designway", aos_entryentity.get("aos_designway"));
				cmp_entryentity.set("aos_srcrowseq", aos_entryentity.get("SEQ"));

				if (Cux_Common_Utl.IsNull(aos_3d))
					aos_3d = aos_entryentity.getBoolean("aos_3d");

				cmp_entryentity.set("aos_language", aos_entryentity.get("aos_language"));
				cmp_entryentity.set("aos_whited", aos_entryentity.get("aos_whited"));
				cmp_entryentity.set("aos_whites", aos_entryentity.get("aos_whites"));
				cmp_entryentity.set("aos_backround", aos_entryentity.get("aos_backround"));
				cmp_entryentity.set("aos_funcpic", aos_entryentity.get("aos_funcpic"));
				cmp_entryentity.set("aos_funclike", aos_entryentity.get("aos_funclike"));
				cmp_entryentity.set("aos_sizepic", aos_entryentity.get("aos_sizepic"));
				cmp_entryentity.set("aos_anew", aos_entryentity.get("aos_anew"));
				cmp_entryentity.set("aos_alike", aos_entryentity.get("aos_alike"));
				cmp_entryentity.set("aos_trans", aos_entryentity.get("aos_trans"));
				cmp_entryentity.set("aos_despic", aos_entryentity.get("aos_despic"));
				cmp_entryentity.set("aos_ps", aos_entryentity.get("aos_ps"));
				cmp_entryentity.set("aos_fix", aos_entryentity.get("aos_fix"));

				Object ItemId = aos_entryentity.getDynamicObject("aos_itemid").getPkValue();
				String category = MKTCom.getItemCateNameZH(ItemId);
				String[] category_group = category.split(",");
				String AosCategory1 = null;
				String AosCategory2 = null;
				int category_length = category_group.length;
				if (category_length > 0)
					AosCategory1 = category_group[0];
				if (category_length > 1)
					AosCategory2 = category_group[1];

				// 附件
				DynamicObjectCollection aos_attribute = cmp_entryentity.getDynamicObjectCollection("aos_attribute");
				aos_attribute.clear();
				DynamicObjectCollection aos_attributefrom = aos_entryentity.getDynamicObjectCollection("aos_attribute");
				DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
				DynamicObject tempFile = null;
				for (DynamicObject d : aos_attributefrom) {
					tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
					aos_attribute.addNew().set("fbasedataid", tempFile);
				}
				// 子单据体
				DynamicObjectCollection aos_subentryentityS = aos_entryentity
						.getDynamicObjectCollection("aos_subentryentity");
				DynamicObjectCollection cmp_subentryentityS = cmp_entryentity
						.getDynamicObjectCollection("aos_subentryentity");
				for (DynamicObject aos_subentryentity : aos_subentryentityS) {
					DynamicObject cmp_subentryentity = cmp_subentryentityS.addNew();
					cmp_subentryentity.set("aos_broitem", aos_subentryentity.get("aos_broitem"));
					cmp_subentryentity.set("aos_segment3", aos_subentryentity.get("aos_segment3"));
					cmp_subentryentity.set("aos_itemname", aos_subentryentity.get("aos_itemname"));
					cmp_subentryentity.set("aos_orgtext", aos_subentryentity.get("aos_orgtext"));
					cmp_subentryentity.set("aos_brand", aos_subentryentity.get("aos_brand"));
					cmp_subentryentity.set("aos_developer", aos_subentryentity.get("aos_developer"));
					cmp_subentryentity.set("aos_url", aos_subentryentity.get("aos_url"));
					cmp_subentryentity.set("aos_pic", aos_subentryentity.get("aos_pic"));
					Object aos_editor = null;
					if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("")
							&& !AosCategory2.equals("")) {
						QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
						QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
						QFilter filter_org = new QFilter("aos_orgid", "=", aos_orgid);
						QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2, filter_org };
						String SelectStr = "aos_oueditor";
						DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", SelectStr,
								filters_category);
						if (aos_mkt_proguser != null) {
							aos_editor = aos_mkt_proguser.getLong("aos_oueditor");
						}
					}
					cmp_subentryentity.set("aos_editor", aos_editor);
				}
			}

			// 推送给销售
			Object ItemId = aos_entryentityS.get(0).getDynamicObject("aos_itemid").getPkValue();
			String category = MKTCom.getItemCateNameZH(ItemId);
			String[] category_group = category.split(",");
			String AosCategory1 = null;
			String AosCategory2 = null;
			int category_length = category_group.length;
			if (category_length > 0)
				AosCategory1 = category_group[0];
			if (category_length > 1)
				AosCategory2 = category_group[1];
			long aos_sale = 0;
			if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
				QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
				QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
				QFilter filter_ou = new QFilter("aos_orgid", "=", aos_orgid);
				QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2, filter_ou };
				String SelectStr = "aos_02hq";
				DynamicObject aos_mkt_progorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", SelectStr,
						filters_category);
				if (aos_mkt_progorguser != null) {
					aos_sale = aos_mkt_progorguser.getLong(SelectStr);
				}
			}
			if (aos_sale == 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, AosCategory1 + "," + AosCategory2 + "国别组长不存在!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}

			aos_mkt_designcmp.set("aos_sale", aos_sale);

			if (Cux_Common_Utl.IsNull(aos_3d))
				aos_3d = false;

			// 节点判断
			if (total > 0 || aos_3d) {
				aos_mkt_designcmp.set("aos_status", "销售确认");
				aos_mkt_designcmp.set("aos_user", aos_sale);
				MessageId = aos_sale + "";
				Message = "设计完成表国别-设计需求表自动创建";
				OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designcmp",
						new DynamicObject[] { aos_mkt_designcmp }, OperateOption.create());

				// 修复关联关系
				try {
					ProgressUtil.botp("aos_mkt_designcmp", aos_mkt_designcmp.get("id"));
				} catch (Exception ex) {
					ex.printStackTrace();
				}

				if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
					MKTCom.SendGlobalMessage(MessageId, aos_mkt_designcmp + "", operationrst.getSuccessPkIds().get(0) + "",
							aos_mkt_designcmp.getString("billno"), Message);
					FndHistory.Create(dy_main, MessageId, "生成设计完成表", aos_mkt_designcmp.getString("billno"));
					FndHistory.Create(aos_mkt_designcmp, "来源-设计需求表", dy_main.getString("billno"));
				}
			}

		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/** 触发生成设计需求表的listing优化需求表上无国别，设计需求表结束后触发生成下单国别的设计完成表-国别 **/
	private static void GenerateDesignNotHasOu(DynamicObject dy_main) throws FndError {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			// 信息处理
			String ErrorMessage = "";
			String MessageId = null;
			String Message = "";
			// 数据层
			Object aos_designer = dy_main.getDynamicObject("aos_designer").getPkValue();// 设计
			Object aos_designby = dy_main.getDynamicObject("aos_designby").getPkValue(); // 制作设计师
			Object billno = dy_main.get("billno");
			Object ReqFId = dy_main.getPkValue(); // 当前界面主键
			Object aos_type = dy_main.get("aos_type");// 任务类型
			Object aos_source = dy_main.get("aos_source");// 任务来源
			Object aos_importance = dy_main.get("aos_importance");// 紧急程度
			DynamicObjectCollection aos_entryentityS = dy_main.getDynamicObjectCollection("aos_entryentity");
			Map<String, List<DynamicObject>> Oumap = new HashMap<>();
			List<DynamicObject> MapList = new ArrayList<DynamicObject>();
			List<DynamicObject> OrgList = Cux_Common_Utl.GetUserOrg(aos_designer);
			Boolean aos_3d = null;
			int total = 0;

			// 循环国别分组
			for (DynamicObject aos_entryentity : aos_entryentityS) {
				// 优化项无数字 直接跳过
				int aos_whited = aos_entryentity.getInt("aos_whited");
				int aos_whites = aos_entryentity.getInt("aos_whites");
				int aos_backround = aos_entryentity.getInt("aos_backround");
				int aos_funcpic = aos_entryentity.getInt("aos_funcpic");
				int aos_funclike = aos_entryentity.getInt("aos_funclike");
				int aos_sizepic = aos_entryentity.getInt("aos_sizepic");
				int aos_anew = aos_entryentity.getInt("aos_anew");
				int aos_alike = aos_entryentity.getInt("aos_alike");
				int aos_trans = aos_entryentity.getInt("aos_trans");
				int aos_despic = aos_entryentity.getInt("aos_despic");
				int aos_ps = aos_entryentity.getInt("aos_ps");
				int aos_fix = aos_entryentity.getInt("aos_fix");
				int aos_whitepro = aos_entryentity.getInt("aos_whitepro");
				int aos_proground = aos_entryentity.getInt("aos_proground");
				int aos_detailpic = aos_entryentity.getInt("aos_detailpic");
				// 优化项内数字
				int linetotal = aos_whited + aos_whites + aos_backround + aos_funcpic + aos_funclike + aos_sizepic
						+ aos_anew + aos_alike + aos_trans + aos_despic + aos_ps + aos_fix + aos_whitepro + aos_proground
						+ aos_detailpic;
				if (linetotal == 0)
					continue;
				total += aos_whited + aos_backround + aos_funcpic + aos_funclike;

				DynamicObjectCollection aos_subentryentityS = aos_entryentity
						.getDynamicObjectCollection("aos_subentryentity");
				DynamicObject aos_subentryentity = aos_subentryentityS.get(0);
				String aos_orgtext = aos_subentryentity.getString("aos_orgtext");
				if (Cux_Common_Utl.IsNull(aos_orgtext))
					continue;
				String[] aos_orgtextArray = aos_orgtext.split(";");
				for (int i = 0; i < aos_orgtextArray.length; i++) {
					String org = aos_orgtextArray[i];
					MapList = Oumap.get(org);
					if (MapList == null || MapList.size() == 0) {
						MapList = new ArrayList<DynamicObject>();
					}
					MapList.add(aos_entryentity);
					Oumap.put(org, MapList);
				}
			}

			// 循环每个分组后的国家 创建一个头
			for (String ou : Oumap.keySet()) {
				Object org_id = FndGlobal.get_import_id(ou, "bd_country");
				if (Cux_Common_Utl.IsNull(org_id))
					continue;
				DynamicObject aos_mkt_designcmp = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designcmp");
				aos_mkt_designcmp.set("aos_requireby", aos_designby);
				aos_mkt_designcmp.set("aos_designer", aos_designer);
				aos_mkt_designcmp.set("aos_status", "申请人");
				aos_mkt_designcmp.set("aos_orgid", org_id);
				aos_mkt_designcmp.set("aos_orignbill", billno);
				aos_mkt_designcmp.set("aos_sourceid", ReqFId);
				aos_mkt_designcmp.set("aos_type", aos_type);
				aos_mkt_designcmp.set("aos_source", aos_source);
				aos_mkt_designcmp.set("aos_importance", aos_importance);
				aos_mkt_designcmp.set("aos_requiredate", new Date());
				// BOTP
				aos_mkt_designcmp.set("aos_sourcebilltype", aos_mkt_designreq);
				aos_mkt_designcmp.set("aos_sourcebillno", dy_main.get("billno"));
				aos_mkt_designcmp.set("aos_srcentrykey", "aos_entryentity");

				if (OrgList != null) {
					if (OrgList.get(2) != null)
						aos_mkt_designcmp.set("aos_organization1", OrgList.get(2).get("id"));
					if (OrgList.get(3) != null)
						aos_mkt_designcmp.set("aos_organization2", OrgList.get(3).get("id"));
				}
				DynamicObjectCollection cmp_entryentityS = aos_mkt_designcmp.getDynamicObjectCollection("aos_entryentity");
				List<DynamicObject> EntryList = Oumap.get(ou);
				for (int i = 0; i < EntryList.size(); i++) {
					DynamicObject aos_entryentity = EntryList.get(i);
					DynamicObject cmp_entryentity = cmp_entryentityS.addNew();
					cmp_entryentity.set("aos_itemid", aos_entryentity.get("aos_itemid"));
					cmp_entryentity.set("aos_3d", aos_entryentity.get("aos_3d"));

					if (Cux_Common_Utl.IsNull(aos_3d))
						aos_3d = aos_entryentity.getBoolean("aos_3d");

					cmp_entryentity.set("aos_desreq", aos_entryentity.get("aos_desreq"));
					cmp_entryentity.set("aos_language", aos_entryentity.get("aos_language"));
					cmp_entryentity.set("aos_whited", aos_entryentity.get("aos_whited"));
					cmp_entryentity.set("aos_whites", aos_entryentity.get("aos_whites"));
					cmp_entryentity.set("aos_backround", aos_entryentity.get("aos_backround"));
					cmp_entryentity.set("aos_funcpic", aos_entryentity.get("aos_funcpic"));
					cmp_entryentity.set("aos_funclike", aos_entryentity.get("aos_funclike"));
					cmp_entryentity.set("aos_sizepic", aos_entryentity.get("aos_sizepic"));
					cmp_entryentity.set("aos_anew", aos_entryentity.get("aos_anew"));
					cmp_entryentity.set("aos_alike", aos_entryentity.get("aos_alike"));
					cmp_entryentity.set("aos_trans", aos_entryentity.get("aos_trans"));
					cmp_entryentity.set("aos_despic", aos_entryentity.get("aos_despic"));
					cmp_entryentity.set("aos_ps", aos_entryentity.get("aos_ps"));
					cmp_entryentity.set("aos_fix", aos_entryentity.get("aos_fix"));
					cmp_entryentity.set("aos_designway", aos_entryentity.get("aos_designway"));
					cmp_entryentity.set("aos_srcrowseq", aos_entryentity.get("SEQ"));

					Object ItemId = aos_entryentity.getDynamicObject("aos_itemid").getPkValue();
					String category = MKTCom.getItemCateNameZH(ItemId + "");
					String[] category_group = category.split(",");
					String AosCategory1 = null;
					String AosCategory2 = null;
					int category_length = category_group.length;
					if (category_length > 0)
						AosCategory1 = category_group[0];
					if (category_length > 1)
						AosCategory2 = category_group[1];

					// 附件
					DynamicObjectCollection aos_attribute = cmp_entryentity.getDynamicObjectCollection("aos_attribute");
					aos_attribute.clear();
					DynamicObjectCollection aos_attributefrom = aos_entryentity.getDynamicObjectCollection("aos_attribute");
					DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
					DynamicObject tempFile = null;
					for (DynamicObject d : aos_attributefrom) {
						tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
						aos_attribute.addNew().set("fbasedataid", tempFile);
					}
					// 子单据体
					DynamicObjectCollection aos_subentryentityS = aos_entryentity
							.getDynamicObjectCollection("aos_subentryentity");
					DynamicObjectCollection cmp_subentryentityS = cmp_entryentity
							.getDynamicObjectCollection("aos_subentryentity");
					for (DynamicObject aos_subentryentity : aos_subentryentityS) {
						DynamicObject cmp_subentryentity = cmp_subentryentityS.addNew();
						cmp_subentryentity.set("aos_broitem", aos_subentryentity.get("aos_broitem"));
						cmp_subentryentity.set("aos_segment3", aos_subentryentity.get("aos_segment3"));
						cmp_subentryentity.set("aos_itemname", aos_subentryentity.get("aos_itemname"));
						cmp_subentryentity.set("aos_orgtext", aos_subentryentity.get("aos_orgtext"));
						cmp_subentryentity.set("aos_brand", aos_subentryentity.get("aos_brand"));
						cmp_subentryentity.set("aos_developer", aos_subentryentity.get("aos_developer"));
						cmp_subentryentity.set("aos_url", aos_subentryentity.get("aos_url"));
						cmp_subentryentity.set("aos_pic", aos_subentryentity.get("aos_pic"));
						Object aos_editor = null;
						if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("")
								&& !AosCategory2.equals("")) {
							QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
							QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
							QFilter filter_org = new QFilter("aos_orgid", "=", org_id);
							QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2, filter_org };
							String SelectStr = "aos_oueditor";
							DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", SelectStr,
									filters_category);
							if (aos_mkt_proguser != null) {
								aos_editor = aos_mkt_proguser.getLong("aos_oueditor");
							}
						}
						cmp_subentryentity.set("aos_editor", aos_editor);
					}
				}
				// 推送给销售
				Object ItemId = aos_entryentityS.get(0).getDynamicObject("aos_itemid").getPkValue();
				String category = MKTCom.getItemCateNameZH(ItemId);
				String[] category_group = category.split(",");
				String AosCategory1 = null;
				String AosCategory2 = null;
				int category_length = category_group.length;
				if (category_length > 0)
					AosCategory1 = category_group[0];
				if (category_length > 1)
					AosCategory2 = category_group[1];
				long aos_sale = 0;
				if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
					QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
					QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
					QFilter filter_ou = new QFilter("aos_orgid", "=", org_id);
					QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2, filter_ou };
					String SelectStr = "aos_02hq";
					DynamicObject aos_mkt_progorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", SelectStr,
							filters_category);
					if (aos_mkt_progorguser != null) {
						aos_sale = aos_mkt_progorguser.getLong(SelectStr);
					}
				}
				if (aos_sale == 0) {
					ErrorMessage = FndError.AddErrorMessage(ErrorMessage, AosCategory1 + "," + AosCategory2 + "国别组长不存在!");
					FndError fndMessage = new FndError(ErrorMessage);
					throw fndMessage;
				}

				aos_mkt_designcmp.set("aos_sale", aos_sale);

				if (Cux_Common_Utl.IsNull(aos_3d))
					aos_3d = false;

				// 节点判断
				if (total > 0 || aos_3d) {
					aos_mkt_designcmp.set("aos_status", "销售确认");
					aos_mkt_designcmp.set("aos_user", aos_sale);
					MessageId = aos_sale + "";
					Message = "设计完成表国别-设计需求表自动创建";
					OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designcmp",
							new DynamicObject[] { aos_mkt_designcmp }, OperateOption.create());

					// 修复关联关系

					ProgressUtil.botp("aos_mkt_designcmp", aos_mkt_designcmp.get("id"));

					if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
						MKTCom.SendGlobalMessage(MessageId, aos_mkt_designcmp + "",
								operationrst.getSuccessPkIds().get(0) + "", aos_mkt_designcmp.getString("billno"), Message);
						FndHistory.Create(dy_main, MessageId, "生成设计完成表", aos_mkt_designcmp.getString("billno"));
						FndHistory.Create(aos_mkt_designcmp, "来源-设计需求表", dy_main.getString("billno"));
					}
				}
			}

		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/** 是否3D建模=否，功能图翻译语种=空，且任务类型=翻译或者四者一致，流程到结束节点 并生成Listing优化销售确认单 **/
	private static void GenerateListingSal(DynamicObject dy_main, String type) throws FndError {
		Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
		try (Scope scope = span.makeCurrent()) {
			String ErrorMessage = "";
			// 信息处理
			String MessageId = null;
			String Message = "";
			// 数据层
			Object aos_designer = dy_main.getDynamicObject("aos_designer").getPkValue();// 设计
			Object billno = dy_main.get("billno");
			Object ReqFId = dy_main.getPkValue(); // 当前界面主键
			Object aos_type = dy_main.get("aos_type");// 任务类型
			Object aos_orgid = dy_main.get("aos_orgid");
			String aos_orgnumber = null;
			if (aos_orgid != null)
				aos_orgnumber = ((DynamicObject) aos_orgid).getString("number");

			DynamicObjectCollection aos_entryentityS = dy_main.getDynamicObjectCollection("aos_entryentity");
			Map<String, List<DynamicObject>> Oumap = new HashMap<>();
			List<DynamicObject> MapList = new ArrayList<DynamicObject>();
			// 循环国别分组
			for (DynamicObject aos_entryentity : aos_entryentityS) {
				int aos_whited = aos_entryentity.getInt("aos_whited");
				int aos_whites = aos_entryentity.getInt("aos_whites");
				int aos_backround = aos_entryentity.getInt("aos_backround");
				int aos_funcpic = aos_entryentity.getInt("aos_funcpic");
				int aos_funclike = aos_entryentity.getInt("aos_funclike");
				int aos_sizepic = aos_entryentity.getInt("aos_sizepic");
				int aos_anew = aos_entryentity.getInt("aos_anew");
				int aos_alike = aos_entryentity.getInt("aos_alike");
				int aos_trans = aos_entryentity.getInt("aos_trans");
				int aos_despic = aos_entryentity.getInt("aos_despic");
				int aos_ps = aos_entryentity.getInt("aos_ps");
				int aos_fix = aos_entryentity.getInt("aos_fix");
				int aos_whitepro = aos_entryentity.getInt("aos_whitepro");
				int aos_proground = aos_entryentity.getInt("aos_proground");
				int aos_detailpic = aos_entryentity.getInt("aos_detailpic");
				int linetotal = aos_whited + aos_whites + aos_backround + aos_funcpic + aos_funclike + aos_sizepic
						+ aos_anew + aos_alike + aos_trans + aos_despic + aos_ps + aos_fix + aos_whitepro + aos_proground
						+ aos_detailpic;// 优化项内数字
				if (linetotal == 0)
					continue;// 本条件需要优化项内有数字

				int total = aos_whited + aos_backround + aos_funcpic + aos_funclike;
				boolean aos_3d = aos_entryentity.getBoolean("aos_3d");

				DynamicObjectCollection aos_subentryentityS = aos_entryentity
						.getDynamicObjectCollection("aos_subentryentity");
				DynamicObject aos_subentryentity = aos_subentryentityS.get(0);
				String aos_orgtext = aos_subentryentity.getString("aos_orgtext");
				String[] aos_orgtextArray = aos_orgtext.split(";");

				if (type.equals("A")) {
					for (int i = 0; i < aos_orgtextArray.length; i++) {
						String org = aos_orgtextArray[i];
						if (aos_orgid != null && !org.equals(aos_orgnumber))
							continue;
						MapList = Oumap.get(org);
						if (MapList == null || MapList.size() == 0) {
							MapList = new ArrayList<DynamicObject>();
						}
						MapList.add(aos_entryentity);
						Oumap.put(org, MapList);
					}
				}
				// 申请人提交，且头表有国别
				else if (type.equals("B")) {
					if (total == 0 && !aos_3d) {
						String orgNumber = dy_main.getDynamicObject("aos_orgid").getString("number");
						List<DynamicObject> list = Oumap.computeIfAbsent(orgNumber, key -> new ArrayList<>());
						list.add(aos_entryentity);
					}
				} else if (type.equals("C")) {
					if (total == 0 && !aos_3d) {
						for (int i = 0; i < aos_orgtextArray.length; i++) {
							String org = aos_orgtextArray[i];
							if (aos_orgid != null && !org.equals(aos_orgnumber))
								continue;
							MapList = Oumap.get(org);
							if (MapList == null || MapList.size() == 0) {
								MapList = new ArrayList<DynamicObject>();
							}
							MapList.add(aos_entryentity);
							Oumap.put(org, MapList);
						}
					}
				}
			}

			// 循环每个分组后的国家 创建一个头
			for (String ou : Oumap.keySet()) {
				Object org_id = FndGlobal.get_import_id(ou, "bd_country");
				DynamicObject aos_mkt_listing_sal = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_sal");
				aos_mkt_listing_sal.set("aos_requireby", aos_designer);
				aos_mkt_listing_sal.set("aos_designer", aos_designer);
				aos_mkt_listing_sal.set("aos_status", "销售确认");
				aos_mkt_listing_sal.set("aos_orgid", org_id);
				aos_mkt_listing_sal.set("aos_orignbill", billno);
				aos_mkt_listing_sal.set("aos_sourceid", ReqFId);
				aos_mkt_listing_sal.set("aos_type", aos_type);
				aos_mkt_listing_sal.set("aos_sourcetype", "设计需求表");
				aos_mkt_listing_sal.set("aos_requiredate", new Date());
				// BOTP
				aos_mkt_listing_sal.set("aos_sourcebilltype", aos_mkt_designreq);
				aos_mkt_listing_sal.set("aos_sourcebillno", dy_main.get("billno"));
				aos_mkt_listing_sal.set("aos_srcentrykey", "aos_entryentity");

				DynamicObjectCollection cmp_entryentityS = aos_mkt_listing_sal
						.getDynamicObjectCollection("aos_entryentity");
				List<DynamicObject> EntryList = Oumap.get(ou);
				long aos_sale = 0;
				for (int i = 0; i < EntryList.size(); i++) {
					DynamicObject aos_entryentity = EntryList.get(i);
					DynamicObject aos_subentryentity = aos_entryentity.getDynamicObjectCollection("aos_subentryentity")
							.get(0);
					DynamicObject cmp_entryentity = cmp_entryentityS.addNew();
					cmp_entryentity.set("aos_itemid", aos_entryentity.get("aos_itemid"));
					cmp_entryentity.set("aos_segment3", aos_subentryentity.get("aos_segment3"));
					cmp_entryentity.set("aos_itemname", aos_subentryentity.get("aos_itemname"));
					cmp_entryentity.set("aos_broitem", aos_subentryentity.get("aos_broitem"));
					cmp_entryentity.set("aos_salestatus", "已确认");
					cmp_entryentity.set("aos_text", aos_entryentity.get("aos_desreq"));
					cmp_entryentity.set("aos_srcrowseq", aos_entryentity.get("SEQ"));

					Object ItemId = aos_entryentity.getDynamicObject("aos_itemid").getPkValue();
					String category = MKTCom.getItemCateNameZH(ItemId);
					String[] category_group = category.split(",");
					String AosCategory1 = null;
					String AosCategory2 = null;
					int category_length = category_group.length;
					if (category_length > 0)
						AosCategory1 = category_group[0];
					if (category_length > 1)
						AosCategory2 = category_group[1];
					if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("")
							&& !AosCategory2.equals("")) {
						Object aos_editor = null;
						if (aos_editor == null) {
							QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
							QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
							QFilter filter_org = new QFilter("aos_orgid", "=", org_id);
							QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2, filter_org };
							String SelectStr = "aos_oueditor";
							DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", SelectStr,
									filters_category);
							if (aos_mkt_proguser != null) {
								aos_editor = aos_mkt_proguser.getLong("aos_oueditor");
							}
						}
						aos_mkt_listing_sal.set("aos_editor", aos_editor);
					}
					if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("")
							&& !AosCategory2.equals("")) {
						QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
						QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
						QFilter filter_ou = new QFilter("aos_orgid", "=", org_id);
						QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2, filter_ou };
						String SelectStr = "aos_salehelper aos_salehelper";
						DynamicObject aos_mkt_progorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", SelectStr,
								filters_category);
						if (aos_mkt_progorguser != null) {
							aos_sale = aos_mkt_progorguser.getLong("aos_salehelper");
						}
					}
					if (aos_sale == 0) {
						ErrorMessage = FndError.AddErrorMessage(ErrorMessage,
								AosCategory1 + "," + AosCategory2 + "国别销售不存在!");
						FndError fndMessage = new FndError(ErrorMessage);
						throw fndMessage;
					}
					aos_mkt_listing_sal.set("aos_user", aos_sale);
					aos_mkt_listing_sal.set("aos_sale", aos_sale);
				}
				MessageId = aos_sale + "";
				Message = "Listing优化销售确认单-设计需求表自动创建";
				OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_sal",
						new DynamicObject[] { aos_mkt_listing_sal }, OperateOption.create());
				// 修复关联关系
				try {
					ProgressUtil.botp("aos_mkt_listing_sal", aos_mkt_listing_sal.get("id"));
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
					MKTCom.SendGlobalMessage(MessageId, aos_mkt_listing_sal + "",
							operationrst.getSuccessPkIds().get(0) + "", aos_mkt_listing_sal.getString("billno"), Message);
					FndHistory.Create(dy_main, MessageId, "生成销售确认单", aos_mkt_listing_sal.getString("billno"));
					FndHistory.Create(aos_mkt_listing_sal, "来源-设计需求表", dy_main.getString("billno"));
				}
			}
		} catch (Exception ex) {
			MmsOtelUtils.setException(span, ex);
			throw ex;
		} finally {
			MmsOtelUtils.spanClose(span);
		}
	}

	/** table设置 -物料 or -新建 **/
	private void setHeadTable() {
		String message = "新建";
		DynamicObjectCollection dyc_ent = this.getModel().getDataEntity(true)
				.getDynamicObjectCollection("aos_entryentity");
		if (dyc_ent.size() > 0) {
			if (dyc_ent.get(0).get("aos_itemid") != null) {
				message = dyc_ent.get(0).getDynamicObject("aos_itemid").getString("number");
			}
		}
		LocaleString value = new LocaleString();
		value.setLocaleValue_zh_CN("设计需求表- " + message);
		this.getView().setFormTitle(value);
	}

	/**
	 * 创建设计需求表后，未保存前的，用于一些赋值
	 */
	public static void createDesiginBeforeSave (DynamicObject designEntity){
		//查找已经生成3d建模的物料
		List<String> skuList = DesignSkuList.getSkuList();
		DynamicObjectCollection entityRows = designEntity.getDynamicObjectCollection("aos_entryentity");
		for (DynamicObject row : entityRows) {
			String itemid = row.getString("aos_itemid");
			row.set("aos_is_design",skuList.contains(itemid));
		}
	}

	//设置页面缓存
	private void setPageCache(){
		IPageCache pageCache = getPageCache();
		//缓存3d建模物料清单
		pageCache.put(KEY_CreateDesign, SerializationUtils.toJsonString(DesignSkuList.getSkuList()));

	}
}
