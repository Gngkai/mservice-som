package mkt.progress.photo;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndDate;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.fnd.FndMsg;
import common.fnd.FndWebHook;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.bill.BillShowParameter;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.ClientProperties;
import kd.bos.form.FormShowParameter;
import kd.bos.form.ShowType;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.Image;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import kd.bos.url.UrlService;
import kd.fi.bd.util.QFBuilder;
import mkt.common.MKTCom;
import mkt.common.MKTS3PIC;
import mkt.progress.ProgressUtil;
import mkt.progress.design.aos_mkt_designreq_bill;
import mkt.progress.design3d.aos_mkt_3design_bill;
import mkt.progress.iface.iteminfo;
import mkt.progress.listing.aos_mkt_listingreq_bill;
import mkt.progress.listing.aos_mkt_listingson_bill;

import static mkt.progress.ProgressUtil.Is_saleout;

public class aos_mkt_progphreq_bill extends AbstractBillPlugIn implements ItemClickListener, HyperLinkClickListener {

	@Override
	public void registerListener(EventObject e) {
		super.registerListener(e);
		this.addItemClickListeners("aos_toolbarap");// 给工具栏加监听事件
		this.addItemClickListeners("aos_submit"); // 提交
		this.addItemClickListeners("aos_confirm"); // 确认
		this.addItemClickListeners("aos_back"); // 退回

		EntryGrid entryGrid = this.getControl("aos_entryentity5");
		entryGrid.addHyperClickListener(this);
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		try {
			if ("aos_submit".equals(Control)) {
				DynamicObject dy_main = this.getModel().getDataEntity(true);
				aos_submit(dy_main, "A");// 提交
				Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键
				DeleteServiceHelper.delete("aos_mkt_photoreq", new QFilter[] { new QFilter("aos_sonflag", "=", true),
						new QFilter("aos_status", "=", "已完成"), new QFilter("id", "=", ReqFId) });
			} else if ("aos_confirm".equals(Control))
				aos_confirm();// 确认
			else if ("aos_back".equals(Control))
				aos_back();// 退回
			else if ("aos_history".equals(Control))
				aos_history();// 查看历史记录
			else if ("aos_search".equals(Control))
				aos_search();// 查看子流程
			else if ("aos_showrcv".equals(Control))
				aos_showrcv();// 查看入库单
			else if ("aos_photourl".equals(Control))
				openPhotoUrl();
			else if ("aos_senceurl".equals(Control))
				openSenceUrl();
		} catch (FndError fndError) {
			fndError.show(getView());
		} catch (Exception ex) {
			FndError.showex(getView(), ex, FndWebHook.urlMms);
		}
	}

	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		StatusControl();
		// AosItemChange();
		itemInit();
	}

	private void itemInit() {
		Object AosItemid = this.getModel().getValue(aos_itemid);
		if (AosItemid == null) {
			// 清空值
			this.getModel().setValue(aos_itemname, null);
			this.getModel().setValue(aos_contrybrand, null);
			this.getModel().setValue(aos_specification, null);
			this.getModel().setValue(aos_seting1, null);
			this.getModel().setValue(aos_seting2, null);
			this.getModel().setValue(aos_sellingpoint, null);
			this.getModel().setValue(aos_vendor, null);
			this.getModel().setValue(aos_city, null);
			this.getModel().setValue(aos_contact, null);
			this.getModel().setValue(aos_address, null);
			this.getModel().setValue(aos_phone, null);
			this.getModel().setValue(aos_phstate, null);
			this.getModel().setValue(aos_developer, null);
			this.getModel().setValue(aos_poer, null);
			this.getModel().setValue(aos_follower, null);
			this.getModel().setValue("aos_orgtext", null);
			this.getModel().setValue("aos_sale", null);
			this.getModel().setValue("aos_vedior", null);
			this.getModel().setValue("aos_3d", null);
			this.getModel().setValue(aos_shipdate, null);
			this.getModel().setValue(aos_urgent, null);
			this.getModel().setValue("aos_is_saleout", false); // 爆品
			// 清空图片
			Image image = this.getControl("aos_image");
			image.setUrl(null);
			this.getModel().setValue("aos_picturefield", null);
			// 清空品牌信息
			this.getModel().deleteEntryData("aos_entryentity4");
			// RGB颜色
			this.getModel().setValue("aos_rgb", null);
		} else {
			DynamicObject AosItemidObject = (DynamicObject) AosItemid;
			Object fid = AosItemidObject.getPkValue();
			String aos_contrybrandStr = "";
			String aos_orgtext = "";
			DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(fid, "bd_material");
			DynamicObjectCollection aos_contryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
			String ItemNumber = bd_material.getString("number");
			List<DynamicObject> list_country = aos_contryentryS.stream().sorted((dy1, dy2) -> {
				int d1 = dy1.getDynamicObject("aos_nationality").getInt("aos_orderby");
				int d2 = dy2.getDynamicObject("aos_nationality").getInt("aos_orderby");
				if (d1 > d2)
					return 1;
				else
					return -1;
			}).collect(Collectors.toList());

			this.getModel().deleteEntryData("aos_entryentity4");
			int i = 1;
			// 获取所有国家品牌 字符串拼接 终止
			for (DynamicObject aos_contryentry : list_country) {
				DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
				String aos_nationalitynumber = aos_nationality.getString("number");
				if ("IE".equals(aos_nationalitynumber))
					continue;
				Object org_id = aos_nationality.get("id"); // ItemId
				int OsQty = iteminfo.GetItemOsQty(org_id, fid);
				int SafeQty = iteminfo.GetSafeQty(org_id);
				if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
					continue;
				aos_orgtext = aos_orgtext + aos_nationalitynumber + ";";

				Object obj = aos_contryentry.getDynamicObject("aos_contrybrand");
				if (obj == null)
					continue;
				String value = aos_contryentry.getDynamicObject("aos_contrybrand").getString("name");
				String Str = "";

				if ("US".equals(aos_nationalitynumber) || "CA".equals(aos_nationalitynumber)
						|| "UK".equals(aos_nationalitynumber))
					Str = "";
				else
					Str = "-" + aos_nationalitynumber;

				this.getModel().batchCreateNewEntryRow("aos_entryentity4", 1);
				this.getModel().setValue("aos_orgshort", aos_nationalitynumber, i - 1);
				this.getModel().setValue("aos_brand", value, i - 1);

				String first = ItemNumber.substring(0, 1);

				// 含品牌
				if ("US".equals(aos_nationalitynumber) || "CA".equals(aos_nationalitynumber)
						|| "UK".equals(aos_nationalitynumber))
					// 非小语种
					this.getModel().setValue("aos_s3address1", "https://uspm.aosomcdn.com/videos/en/" + first + "/"
							+ ItemNumber + "/" + ItemNumber + "-" + value + ".mp4", i - 1);
				else
					// 小语种
					this.getModel()
							.setValue(
									"aos_s3address1", "https://uspm.aosomcdn.com/videos/en/" + first + "/" + ItemNumber
											+ "/" + ItemNumber + "-" + value + "-" + aos_nationalitynumber + ".mp4",
									i - 1);

				// 不含品牌
				if ("US".equals(aos_nationalitynumber) || "CA".equals(aos_nationalitynumber)
						|| "UK".equals(aos_nationalitynumber))
					// 非小语种
					this.getModel().setValue("aos_s3address2", "https://uspm.aosomcdn.com/videos/en/" + first + "/"
							+ ItemNumber + "/" + ItemNumber + ".mp4", i - 1);
				else
					// 小语种
					this.getModel().setValue("aos_s3address2", "https://uspm.aosomcdn.com/videos/en/" + first + "/"
							+ ItemNumber + "/" + ItemNumber + "-" + aos_nationalitynumber + ".mp4", i - 1);

				if ("US".equals(aos_nationalitynumber) || "CA".equals(aos_nationalitynumber)
						|| "UK".equals(aos_nationalitynumber))
					this.getModel().setValue("aos_filename", ItemNumber + "-" + value, i - 1);
				else
					this.getModel().setValue("aos_filename", ItemNumber + "-" + value + "-" + aos_nationalitynumber,
							i - 1);
				i++;
				if (!aos_contrybrandStr.contains(value))
					aos_contrybrandStr = aos_contrybrandStr + value + ";";
			}

			// 图片字段
			QFilter filter = new QFilter("entryentity.aos_articlenumber", QCP.equals, AosItemidObject.getPkValue())
					.and("billstatus", QCP.in, new String[] { "C", "D" });
			DynamicObjectCollection query = QueryServiceHelper.query("aos_newarrangeorders", "entryentity.aos_picture",
					filter.toArray(), "aos_creattime desc");
			if (query != null && query.size() > 0) {
				Object o = query.get(0).get("entryentity.aos_picture");
				this.getModel().setValue("aos_picturefield", o);
			}

			// 产品类别
			String category = (String) SalUtil.getCategoryByItemId(fid + "").get("name");
			String[] category_group = category.split(",");
			String AosCategory1 = null;
			String AosCategory2 = null;
			String AosCategory3 = null;
			int category_length = category_group.length;
			if (category_length > 0)
				AosCategory1 = category_group[0];
			if (category_length > 1)
				AosCategory2 = category_group[1];
			if (category_length > 2)
				AosCategory3 = category_group[2];
			// 赋值物料相关
			String aosItemName = bd_material.getString("name");
			this.getModel().setValue(aos_itemname, aosItemName);
			this.getModel().setValue(aos_contrybrand, aos_contrybrandStr);
			this.getModel().setValue("aos_orgtext", aos_orgtext);
			this.getModel().setValue(aos_specification, bd_material.get("aos_specification_cn"));
			this.getModel().setValue(aos_seting1, bd_material.get("aos_seting_cn"));
			this.getModel().setValue(aos_seting2, bd_material.get("aos_seting_en"));
			this.getModel().setValue(aos_sellingpoint, bd_material.get("aos_sellingpoint"));
			this.getModel().setValue(aos_developer, bd_material.get("aos_developer"));
			this.getModel().setValue(aos_category1, AosCategory1);
			this.getModel().setValue(aos_category2, AosCategory2);
			this.getModel().setValue(aos_category3, AosCategory3);

			// 摄影标准库字段
			this.getModel().deleteEntryData("aos_entryentity5");
			this.getModel().batchCreateNewEntryRow("aos_entryentity5", 1);
			this.getModel().setValue("aos_refer", "摄影标准库", 0);
			DynamicObject aosMktPhotoStd = QueryServiceHelper.queryOne("aos_mkt_photostd",
					"aos_firstpicture,aos_other,aos_require",
					new QFilter("aos_category1_name", QCP.equals, AosCategory1)
							.and("aos_category2_name", QCP.equals, AosCategory2)
							.and("aos_category3_name", QCP.equals, AosCategory3)
							.and("aos_itemnamecn", QCP.equals, aosItemName).toArray());
			if (FndGlobal.IsNotNull(aosMktPhotoStd)) {
				this.getModel().setValue("aos_reqfirst", aosMktPhotoStd.getString("aos_firstpicture"), 0);
				this.getModel().setValue("aos_reqother", aosMktPhotoStd.getString("aos_other"), 0);
				this.getModel().setValue("aos_detail", aosMktPhotoStd.getString("aos_require"), 0);
			}

			// 布景标准库字段
			DynamicObject aosMktViewStd = QueryServiceHelper.queryOne("aos_mkt_viewstd",
					"aos_scene1,aos_object1,aos_scene2,aos_object2,aos_itemnamecn,aos_descpic",
					new QFilter("aos_category1_name", QCP.equals, AosCategory1)
							.and("aos_category2_name", QCP.equals, AosCategory2)
							.and("aos_category3_name", QCP.equals, AosCategory3)
							.and("aos_itemnamecn", QCP.equals, aosItemName).toArray());
			if (FndGlobal.IsNotNull(aosMktViewStd)) {
				this.getModel().setValue("aos_scene1", aosMktViewStd.getString("aos_scene1"), 0);
				this.getModel().setValue("aos_object1", aosMktViewStd.getString("aos_object1"), 0);
				this.getModel().setValue("aos_scene2", aosMktViewStd.getString("aos_scene2"), 0);
				this.getModel().setValue("aos_object2", aosMktViewStd.getString("aos_object2"), 0);
				this.getModel().setValue("aos_descpic", aosMktViewStd.getString("aos_descpic"), 0);
			}

			DynamicObject aos_color = bd_material.getDynamicObject("aos_color");
			if (FndGlobal.IsNotNull(aos_color)) {
				String aos_rgb = aos_color.getString("aos_rgb");
				this.getModel().setValue("aos_rgb", aos_rgb);
				this.getModel().setValue("aos_colors", aos_color.get("aos_colors"));
				this.getModel().setValue("aos_colorname", aos_color.get("name"));
				this.getModel().setValue("aos_colorex", aos_color);

				HashMap<String, Object> fieldMap = new HashMap<>();
				// 设置前景色
				fieldMap.put(ClientProperties.BackColor, aos_rgb);
				// 同步指定元数据到控件
				this.getView().updateControlMetadata("aos_color", fieldMap);
			}
		}
	}

	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		InitDefualt();
		StatusControl();
		this.getView().setVisible(false, "aos_submit");
	}

	@Override
	public void afterBindData(EventObject e) {
		super.afterBindData(e);
		// 设置白底和实景状态下的默认值
		setDefaulAtPhotoNode();
	}

	/** 在白底和实景拍摄节点设置拍摄的完成数的默认值 白1，实2 **/
	private void setDefaulAtPhotoNode() {
		Object aos_status = this.getModel().getValue("aos_status");
		if (!Cux_Common_Utl.IsNull(aos_status)) {
			if (StringUtils.equals(aos_status.toString(), "白底拍摄")
					|| StringUtils.equals(aos_status.toString(), "实景拍摄")) {
				int value = (int) this.getModel().getValue("aos_completeqty", 0);
				if (value == 0) {
					this.getModel().setValue("aos_completeqty", 1, 0);
				}
				value = (int) this.getModel().getValue("aos_completeqty", 1);
				if (value == 0) {
					this.getModel().setValue("aos_completeqty", 2, 1);
				}
			}
		}
	}

	public void beforeClosed(BeforeClosedEvent e) {
		super.beforeClosed(e);
		e.setCheckDataChange(false);
	}

	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		if (name.equals("aos_itemid"))
			AosItemChange();
		else if (name.equals("aos_phstate"))
			PhstateChange();
		else if (name.equals("aos_vediotype"))
			VedioTypeChange();
		else if (name.equals("aos_ponumber"))
			poNumberChanged();
	}

	/**
	 * 采购订单值改变时
	 */
	private void poNumberChanged() {
		Object AosItemid = this.getModel().getValue(aos_itemid);
		Object aos_ponumber = this.getModel().getValue("aos_ponumber");
		if (FndGlobal.IsNotNull(aos_ponumber) && FndGlobal.IsNotNull(AosItemid)) {
			DynamicObject AosItemidObject = (DynamicObject) AosItemid;
			Object fid = AosItemidObject.getPkValue();

			DynamicObject isSealSample = QueryServiceHelper.queryOne("aos_sealsample", "aos_model",
					new QFilter("aos_item.id", QCP.equals, fid).and("aos_contractnowb", QCP.equals, aos_ponumber)
							.toArray());//
			if (FndGlobal.IsNotNull(isSealSample)) {
				String aos_model = isSealSample.getString("aos_model");
				if ("是".equals(aos_model))
					this.getModel().setValue("aos_phstate", "工厂简拍");
				else if ("否".equals(aos_model))
					this.getModel().setValue("aos_phstate", null);
			} else {
				DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(fid, "bd_material");
				String category = (String) SalUtil.getCategoryByItemId(fid + "").get("name");
				String[] category_group = category.split(",");
				String AosCategory1 = null;
				String AosCategory2 = null;
				String AosCategory3 = null;
				int category_length = category_group.length;
				if (category_length > 0)
					AosCategory1 = category_group[0];
				if (category_length > 1)
					AosCategory2 = category_group[1];
				if (category_length > 2)
					AosCategory3 = category_group[2];

				Boolean exists = Judge3dSelect(AosCategory1, AosCategory2, AosCategory3, bd_material.getString("name"));
				if (exists)
					this.getModel().setValue("aos_phstate", "工厂简拍");
				else
					this.getModel().setValue("aos_phstate", null);
			}
		} else {
			this.getModel().setValue("aos_phstate", null);
		}

	}

	public void beforeDoOperation(BeforeDoOperationEventArgs args) {
		FormOperate formOperate = (FormOperate) args.getSource();
		String Operatation = formOperate.getOperateKey();
		try {
			if ("save".equals(Operatation)) {
				NewControl(this.getModel().getDataEntity(true));
				SyncPhotoList(); // 拍照任务清单同步
			}
		} catch (FndError fndError) {
			fndError.show(getView(), FndWebHook.urlMms);
			args.setCancel(true);
		} catch (Exception ex) {
			FndError.showex(getView(), ex, FndWebHook.urlMms);
			args.setCancel(true);
		}
	}

	public void afterDoOperation(AfterDoOperationEventArgs args) {
		FormOperate formOperate = (FormOperate) args.getSource();
		String Operatation = formOperate.getOperateKey();
		try {
			if ("save".equals(Operatation))
				InitReq();
			StatusControl();
		} catch (FndError fndError) {
			fndError.show(getView(), FndWebHook.urlMms);
		} catch (Exception ex) {
			FndError.showex(getView(), ex, FndWebHook.urlMms);
		}
	}

	/** 有进行中拍照需求表，不允许再新增拍照需求流程 **/
	private static void NewControl(DynamicObject dy_main) throws FndError {
		String ErrorMessage = "";
		Object ReqFId = dy_main.getPkValue(); // 当前界面主键
		Object aos_type = dy_main.get("aos_type");
		Boolean aos_sonflag = dy_main.getBoolean("aos_sonflag");
		DynamicObject aos_itemid = dy_main.getDynamicObject("aos_itemid");
		// 本单不需要拍照的话则不做校验
		if (!dy_main.getBoolean("aos_photoflag") || "视频".equals(aos_type)) {
			return;
		}
		// 子流程不做判断
		if (!aos_sonflag && FndGlobal.IsNotNull(aos_itemid)) {
			QFilter filter_itemid = new QFilter("aos_itemid", "=", aos_itemid.getPkValue());
			QFilter filter_status = new QFilter("aos_status", "!=", "已完成");
			QFilter filter_status2 = new QFilter("aos_status", "!=", "不需拍");
			QFilter filter_status3 = new QFilter("aos_type", "!=", "视频");

			QFilter filter_son = new QFilter("aos_sonflag", "=", false);
			QFilter filter_id = new QFilter("id", "!=", ReqFId);
			QFilter filter_type = new QFilter("aos_photoflag", "=", true);
			QFilter[] filters = new QFilter[] { filter_status3, filter_itemid, filter_status, filter_id, filter_type,
					filter_son, filter_status2 };
			DynamicObject aos_mkt_photoreq = QueryServiceHelper.queryOne("aos_mkt_photoreq", "billno", filters);
			if (aos_mkt_photoreq != null) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage,
						"有进行中拍照需求表" + aos_mkt_photoreq.getString("billno") + "，不允许再新增拍照需求流程!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
		}
	}

	private void VedioTypeChange() {
		Object aos_vediotype = this.getModel().getValue("aos_vediotype");
		if ("剪辑".equals(aos_vediotype)) {
			this.getModel().setValue("aos_photoflag", false);
			this.getModel().setValue("aos_vedioflag", true);
			this.getModel().setValue("aos_reason", "只拍展示视频");
		}
	}

	private void PhstateChange() {
		Object aos_phstate = this.getModel().getValue("aos_phstate");
		if ("工厂简拍".equals(aos_phstate)) {
			this.getModel().setValue("aos_3dflag", true);
			this.getModel().setValue("aos_3d_reason", true);
		}
	}

	private void aos_search() throws FndError {
		Map<String, Object> map = new HashMap<>();
		FormShowParameter parameter = new FormShowParameter();
		parameter.getOpenStyle().setShowType(ShowType.Modal);
		parameter.setFormId("aos_mkt_phoson_form");
		map.put("aos_parentid", this.getView().getModel().getDataEntity().getPkValue());// 当前界面主键
		parameter.setCustomParam("params", map);
		this.getView().showForm(parameter);
	}

	/** 跳转到 数据库-摄影标准库 **/
	private void openPhotoUrl() {
		QFilter filter_cate1 = new QFilter("aos_category1_name", "=", this.getModel().getValue("aos_category1"));
		QFilter filter_cate2 = new QFilter("aos_category2_name", "=", this.getModel().getValue("aos_category2"));
		QFilter filter_cate3 = new QFilter("aos_category3_name", "=", this.getModel().getValue("aos_category3"));
		QFilter filter_name = new QFilter("aos_itemnamecn", "=", this.getModel().getValue("aos_itemname"));
		QFilter[] qfs = new QFilter[] { filter_cate1, filter_cate2, filter_cate3, filter_name };
		List<Object> list_id = QueryServiceHelper.queryPrimaryKeys("aos_mkt_photostd", qfs, null, 1);
		if (list_id.size() == 0)
			this.getView().showMessage("数据库还未建立");
		else {
			BillShowParameter parameter = new BillShowParameter();
			parameter.setFormId("aos_mkt_photostd");
			parameter.setPkId(list_id.get(0));
			parameter.getOpenStyle().setShowType(ShowType.NewWindow);
			this.getView().showForm(parameter);
		}
	}

	/** 跳转到 布景标准库 **/
	private void openSenceUrl() {
		QFilter filter_cate1 = new QFilter("aos_category1_name", "=", this.getModel().getValue("aos_category1"));
		QFilter filter_cate2 = new QFilter("aos_category2_name", "=", this.getModel().getValue("aos_category2"));
		QFilter filter_cate3 = new QFilter("aos_category3_name", "=", this.getModel().getValue("aos_category3"));
		QFilter filter_name = new QFilter("aos_itemnamecn", "=", this.getModel().getValue("aos_itemname"));
		QFilter[] qfs = new QFilter[] { filter_cate1, filter_cate2, filter_cate3, filter_name };
		List<Object> list_id = QueryServiceHelper.queryPrimaryKeys("aos_mkt_viewstd", qfs, null, 1);
		if (list_id.size() == 0)
			this.getView().showMessage("数据库还未建立");
		else {
			BillShowParameter parameter = new BillShowParameter();
			parameter.setFormId("aos_mkt_viewstd");
			parameter.setPkId(list_id.get(0));
			parameter.getOpenStyle().setShowType(ShowType.NewWindow);
			this.getView().showForm(parameter);
		}
	}

	/** 新建设置默认值 **/
	private void InitDefualt() {
		try {
			long CurrentUserId = UserServiceHelper.getCurrentUserId();
			List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(CurrentUserId);
			if (MapList != null) {
				if (MapList.get(2) != null)
					this.getModel().setValue("aos_organization1", MapList.get(2).get("id"));
				if (MapList.get(3) != null)
					this.getModel().setValue("aos_organization2", MapList.get(3).get("id"));
			}
		} catch (Exception ex) {
			FndError.showex(getView(), ex, FndWebHook.urlMms);
		}
	}

	/** 拍照需求表同步拍照任务清单 **/
	private void SyncPhotoList() throws FndError {
		String ErrorMessage = "";
		Object AosBillno = this.getModel().getValue(billno);
		Object AosItemName = this.getModel().getValue(aos_itemname);
		Object AosShipdate = this.getModel().getValue(aos_shipdate);
		Object AosDeveloper = this.getModel().getValue(aos_developer);
		Object AosPoer = this.getModel().getValue(aos_poer);
		Object AosFollower = this.getModel().getValue(aos_follower);
		Object AosWhiteph = this.getModel().getValue(aos_whiteph);
		Object AosActph = this.getModel().getValue(aos_actph);
		Object AosVedior = this.getModel().getValue(aos_vedior);
		Object AosStatus = this.getModel().getValue(aos_status);
		Object AosPhotoFlag = this.getModel().getValue(aos_photoflag);
		Object AosVedioFlag = this.getModel().getValue(aos_vedioflag);
		Object AosItemid = this.getModel().getValue(aos_itemid);
		Object AosPhstate = this.getModel().getValue(aos_phstate);
		Object aos_sourceid = this.getModel().getValue("aos_sourceid");
		Object aos_address = this.getModel().getValue("aos_address"); // 地址
		Object aos_orgtext = this.getModel().getValue("aos_orgtext"); // 下单国别
		Object aos_urgent = this.getModel().getValue("aos_urgent"); // 紧急提醒

		// 调整为新拍照界面
		String aos_picdesc = "", aos_picdesc1 = ""; // 照片需求
		DynamicObjectCollection dyc_photo = this.getModel().getDataEntity(true)
				.getDynamicObjectCollection("aos_entryentity6");
		if (dyc_photo.size() > 0) {
			aos_picdesc = dyc_photo.get(0).getString("aos_reqsupp");
			aos_picdesc1 = dyc_photo.get(0).getString("aos_devsupp");
		}

		// 如果新建状态下 单据编号为空 则需要生成对应的拍照任务清单 并回写单据编号
		if ("新建".equals(AosStatus) && (AosBillno == null || "".equals(AosBillno))) {
			DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.newDynamicObject("aos_mkt_photolist");
			aos_mkt_photolist.set(aos_itemid, AosItemid);
			aos_mkt_photolist.set(aos_itemname, AosItemName);
			aos_mkt_photolist.set(aos_photoflag, AosPhotoFlag);
			aos_mkt_photolist.set(aos_vedioflag, AosVedioFlag);
			aos_mkt_photolist.set(aos_phstate, AosPhstate);
			aos_mkt_photolist.set(aos_shipdate, AosShipdate);
			aos_mkt_photolist.set(aos_developer, AosDeveloper);
			aos_mkt_photolist.set(aos_poer, AosPoer);
			aos_mkt_photolist.set(aos_follower, AosFollower);
			aos_mkt_photolist.set(aos_whiteph, AosWhiteph);
			aos_mkt_photolist.set(aos_actph, AosActph);
			aos_mkt_photolist.set(aos_vedior, AosVedior);
			aos_mkt_photolist.set("aos_photourl", "拍照");
			aos_mkt_photolist.set("aos_vediourl", "视频");
			aos_mkt_photolist.set("aos_address", aos_address);
			aos_mkt_photolist.set("aos_orgtext", aos_orgtext);
			aos_mkt_photolist.set("aos_urgent", aos_urgent);
			aos_mkt_photolist.set("aos_picdesc", aos_picdesc);
			aos_mkt_photolist.set("aos_picdesc1", aos_picdesc1);

			if ((Boolean) AosPhotoFlag)
				aos_mkt_photolist.set("aos_phstatus", "新建");
			if ((Boolean) AosVedioFlag)
				aos_mkt_photolist.set("aos_vedstatus", "新建");
			aos_mkt_photolist.set("billstatus", "A");
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
					new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			} else {
				this.getModel().setValue(billno, aos_mkt_photolist.get("billno"));
				this.getModel().setValue("aos_sourceid", operationrst.getSuccessPkIds().get(0));
				this.getView().setVisible(true, "aos_submit");// 设置提交按钮可见
			}
		} else {
			// 其他情况下 都需要同步数据至拍照任务清单
			if (!QueryServiceHelper.exists("aos_mkt_photolist", aos_sourceid))
				return;
			DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(aos_sourceid, "aos_mkt_photolist");
			// 除状态外字段同步
			aos_mkt_photolist.set("aos_itemid", this.getModel().getValue(aos_itemid));
			aos_mkt_photolist.set("aos_itemname", this.getModel().getValue(aos_itemname));
			aos_mkt_photolist.set("aos_samplestatus", this.getModel().getValue("aos_samplestatus"));
			aos_mkt_photolist.set("aos_newitem", this.getModel().getValue(aos_newitem));
			aos_mkt_photolist.set("aos_newvendor", this.getModel().getValue(aos_newvendor));
			aos_mkt_photolist.set("aos_photoflag", this.getModel().getValue(aos_photoflag));
			aos_mkt_photolist.set("aos_phstate", this.getModel().getValue(aos_phstate));
			aos_mkt_photolist.set("aos_shipdate", this.getModel().getValue(aos_shipdate));
			aos_mkt_photolist.set("aos_arrivaldate", this.getModel().getValue(aos_arrivaldate));
			aos_mkt_photolist.set("aos_developer", this.getModel().getValue(aos_developer));
			aos_mkt_photolist.set("aos_poer", this.getModel().getValue(aos_poer));
			aos_mkt_photolist.set("aos_follower", this.getModel().getValue(aos_follower));
			aos_mkt_photolist.set("aos_whiteph", this.getModel().getValue(aos_whiteph));
			aos_mkt_photolist.set("aos_actph", this.getModel().getValue(aos_actph));
			aos_mkt_photolist.set("aos_vedior", this.getModel().getValue(aos_vedior));
			aos_mkt_photolist.set("aos_sampledate", this.getModel().getValue("aos_sampledate"));
			aos_mkt_photolist.set("aos_installdate", this.getModel().getValue("aos_installdate"));
			aos_mkt_photolist.set("aos_whitedate", this.getModel().getValue("aos_whitedate"));
			aos_mkt_photolist.set("aos_actdate", this.getModel().getValue("aos_actdate"));
			aos_mkt_photolist.set("aos_picdate", this.getModel().getValue("aos_picdate"));
			aos_mkt_photolist.set("aos_funcpicdate", this.getModel().getValue("aos_funcpicdate"));
			aos_mkt_photolist.set("aos_vedio", this.getModel().getValue("aos_vedio"));

			OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
					new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());

		}

	}

	/** 初始化图片 **/
	private void InitPic() {
		// 数据层
		Object AosItemid = this.getModel().getValue(aos_itemid);
		// 如果存在物料 设置图片
		if (AosItemid != null) {
			QFilter filter = new QFilter("entryentity.aos_articlenumber", QCP.equals,
					((DynamicObject) AosItemid).getPkValue()).and("billstatus", QCP.in, new String[] { "C", "D" });
			DynamicObjectCollection query = QueryServiceHelper.query("aos_newarrangeorders", "entryentity.aos_picture",
					filter.toArray(), "aos_creattime desc");
			if (query != null && query.size() > 0) {
				Object o = query.get(0).get("entryentity.aos_picture");
				this.getModel().setValue("aos_picturefield", o);
			}
		}

		// 对于流程图
		Object aos_type = this.getModel().getValue("aos_type");

		if (aos_type == null || "".equals(aos_type) || "null".equals(aos_type))
			this.getModel().setValue("aos_flowpic", MKTS3PIC.aos_flowpic);
		else if (aos_type != null && "拍照".equals(aos_type))
			this.getModel().setValue("aos_flowpic", MKTS3PIC.aos_flowpic);
		else if (aos_type != null && "视频".equals(aos_type))
			this.getModel().setValue("aos_flowpic", MKTS3PIC.aos_flowved);

	}

	/** 新建初始化 **/
	private void InitReq() throws FndError {
		// 数据层
		Boolean AosInit = (Boolean) this.getModel().getValue("aos_init");

		// 数据赋值
		if (!AosInit) {
			// 照片需求(new1)
			this.getModel().deleteEntryData("aos_entryentity5");
			this.getModel().batchCreateNewEntryRow("aos_entryentity5", 1);
			// 照片需求(new2)
			this.getModel().deleteEntryData("aos_entryentity6");
			this.getModel().batchCreateNewEntryRow("aos_entryentity6", 1);
			// 照片需求
			this.getModel().deleteEntryData("aos_entryentity");
			this.getModel().batchCreateNewEntryRow("aos_entryentity", 2);
			this.getModel().setValue("aos_applyby", "申请人", 0);
			this.getModel().setValue("aos_applyby", "开发/采购", 1);
			// 视频需求
			this.getModel().deleteEntryData("aos_entryentity1");
			this.getModel().batchCreateNewEntryRow("aos_entryentity1", 2);
			this.getModel().setValue("aos_applyby2", "申请人", 0);
			this.getModel().setValue("aos_applyby2", "开发/采购", 1);
			// 拍摄情况
			this.getModel().deleteEntryData("aos_entryentity2");
			this.getModel().batchCreateNewEntryRow("aos_entryentity2", 2);
			this.getModel().setValue("aos_phtype", "白底", 0);
			this.getModel().setValue("aos_phtype", "实景", 1);
			// 流程退回原因
			this.getModel().deleteEntryData("aos_entryentity3");
			this.getModel().batchCreateNewEntryRow("aos_entryentity3", 2);
			this.getModel().setValue("aos_returnby", "摄影师", 0);
			this.getModel().setValue("aos_returnby", "开发", 1);
			// 初始化标记
			this.getModel().setValue("aos_init", true);
		}

	}

	/** 物料值改变 **/
	private void AosItemChange() {
		Object AosItemid = this.getModel().getValue(aos_itemid);
		if (AosItemid == null) {
			// 清空值
			this.getModel().setValue(aos_itemname, null);
			this.getModel().setValue(aos_contrybrand, null);
			this.getModel().setValue(aos_specification, null);
			this.getModel().setValue(aos_seting1, null);
			this.getModel().setValue(aos_seting2, null);
			this.getModel().setValue(aos_sellingpoint, null);
			this.getModel().setValue(aos_vendor, null);
			this.getModel().setValue(aos_city, null);
			this.getModel().setValue(aos_contact, null);
			this.getModel().setValue(aos_address, null);
			this.getModel().setValue(aos_phone, null);
			this.getModel().setValue(aos_phstate, null);
			this.getModel().setValue(aos_developer, null);
			this.getModel().setValue(aos_poer, null);
			this.getModel().setValue(aos_follower, null);
			this.getModel().setValue("aos_orgtext", null);
			this.getModel().setValue("aos_sale", null);
			this.getModel().setValue("aos_vedior", null);
			this.getModel().setValue("aos_3d", null);
			this.getModel().setValue(aos_shipdate, null);
			this.getModel().setValue(aos_urgent, null);
			this.getModel().setValue("aos_is_saleout", false); // 爆品
			// 清空图片
			Image image = this.getControl("aos_image");
			image.setUrl(null);
			this.getModel().setValue("aos_picturefield", null);
			// 清空品牌信息
			this.getModel().deleteEntryData("aos_entryentity4");
			// RGB颜色
			this.getModel().setValue("aos_rgb", null);
		} else {
			DynamicObject AosItemidObject = (DynamicObject) AosItemid;
			Object fid = AosItemidObject.getPkValue();
			String aos_contrybrandStr = "";
			String aos_orgtext = "";
			DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(fid, "bd_material");
			DynamicObjectCollection aos_contryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
			String ItemNumber = bd_material.getString("number");
			List<DynamicObject> list_country = aos_contryentryS.stream().sorted((dy1, dy2) -> {
				int d1 = dy1.getDynamicObject("aos_nationality").getInt("aos_orderby");
				int d2 = dy2.getDynamicObject("aos_nationality").getInt("aos_orderby");
				if (d1 > d2)
					return 1;
				else
					return -1;
			}).collect(Collectors.toList());

			this.getModel().deleteEntryData("aos_entryentity4");
			int i = 1;
			// 获取所有国家品牌 字符串拼接 终止
			for (DynamicObject aos_contryentry : list_country) {
				DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
				String aos_nationalitynumber = aos_nationality.getString("number");
				if ("IE".equals(aos_nationalitynumber))
					continue;
				Object org_id = aos_nationality.get("id"); // ItemId
				int OsQty = iteminfo.GetItemOsQty(org_id, fid);
				int SafeQty = iteminfo.GetSafeQty(org_id);
				if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
					continue;
				aos_orgtext = aos_orgtext + aos_nationalitynumber + ";";

				Object obj = aos_contryentry.getDynamicObject("aos_contrybrand");
				if (obj == null)
					continue;
				String value = aos_contryentry.getDynamicObject("aos_contrybrand").getString("name");
				String Str = "";

				if ("US".equals(aos_nationalitynumber) || "CA".equals(aos_nationalitynumber)
						|| "UK".equals(aos_nationalitynumber))
					Str = "";
				else
					Str = "-" + aos_nationalitynumber;

				this.getModel().batchCreateNewEntryRow("aos_entryentity4", 1);
				this.getModel().setValue("aos_orgshort", aos_nationalitynumber, i - 1);
				this.getModel().setValue("aos_brand", value, i - 1);

				String first = ItemNumber.substring(0, 1);

				// 含品牌
				if ("US".equals(aos_nationalitynumber) || "CA".equals(aos_nationalitynumber)
						|| "UK".equals(aos_nationalitynumber))
					// 非小语种
					this.getModel().setValue("aos_s3address1", "https://uspm.aosomcdn.com/videos/en/" + first + "/"
							+ ItemNumber + "/" + ItemNumber + "-" + value + ".mp4", i - 1);
				else
					// 小语种
					this.getModel()
							.setValue(
									"aos_s3address1", "https://uspm.aosomcdn.com/videos/en/" + first + "/" + ItemNumber
											+ "/" + ItemNumber + "-" + value + "-" + aos_nationalitynumber + ".mp4",
									i - 1);

				// 不含品牌
				if ("US".equals(aos_nationalitynumber) || "CA".equals(aos_nationalitynumber)
						|| "UK".equals(aos_nationalitynumber))
					// 非小语种
					this.getModel().setValue("aos_s3address2", "https://uspm.aosomcdn.com/videos/en/" + first + "/"
							+ ItemNumber + "/" + ItemNumber + ".mp4", i - 1);
				else
					// 小语种
					this.getModel().setValue("aos_s3address2", "https://uspm.aosomcdn.com/videos/en/" + first + "/"
							+ ItemNumber + "/" + ItemNumber + "-" + aos_nationalitynumber + ".mp4", i - 1);

				if ("US".equals(aos_nationalitynumber) || "CA".equals(aos_nationalitynumber)
						|| "UK".equals(aos_nationalitynumber))
					this.getModel().setValue("aos_filename", ItemNumber + "-" + value, i - 1);
				else
					this.getModel().setValue("aos_filename", ItemNumber + "-" + value + "-" + aos_nationalitynumber,
							i - 1);
				i++;
				if (!aos_contrybrandStr.contains(value))
					aos_contrybrandStr = aos_contrybrandStr + value + ";";
			}

			// 图片字段
			QFilter filter = new QFilter("entryentity.aos_articlenumber", QCP.equals, AosItemidObject.getPkValue())
					.and("billstatus", QCP.in, new String[] { "C", "D" });
			DynamicObjectCollection query = QueryServiceHelper.query("aos_newarrangeorders", "entryentity.aos_picture",
					filter.toArray(), "aos_creattime desc");
			if (query != null && query.size() > 0) {
				Object o = query.get(0).get("entryentity.aos_picture");
				this.getModel().setValue("aos_picturefield", o);
			}

			// 产品类别

			String category = (String) SalUtil.getCategoryByItemId(fid + "").get("name");
			String[] category_group = category.split(",");
			String AosCategory1 = null;
			String AosCategory2 = null;
			String AosCategory3 = null;
			int category_length = category_group.length;
			if (category_length > 0)
				AosCategory1 = category_group[0];
			if (category_length > 1)
				AosCategory2 = category_group[1];
			if (category_length > 2)
				AosCategory3 = category_group[2];
			// 赋值物料相关
			String aosItemName = bd_material.getString("name");
			this.getModel().setValue(aos_itemname, aosItemName);
			this.getModel().setValue(aos_contrybrand, aos_contrybrandStr);
			this.getModel().setValue("aos_orgtext", aos_orgtext);
			this.getModel().setValue(aos_specification, bd_material.get("aos_specification_cn"));
			this.getModel().setValue(aos_seting1, bd_material.get("aos_seting_cn"));
			this.getModel().setValue(aos_seting2, bd_material.get("aos_seting_en"));
			this.getModel().setValue(aos_sellingpoint, bd_material.get("aos_sellingpoint"));
			this.getModel().setValue(aos_developer, bd_material.get("aos_developer"));
			this.getModel().setValue(aos_category1, AosCategory1);
			this.getModel().setValue(aos_category2, AosCategory2);
			this.getModel().setValue(aos_category3, AosCategory3);
			this.getModel().setValue(aos_vedioflag,
					JudgeTakeVideo(AosCategory1, AosCategory2, AosCategory3, bd_material.getString("name")));

			// 根据大类中类获取对应营销人员
			if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
				QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
				QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
				QFilter[] filters = new QFilter[] { filter_category1, filter_category2 };
				String SelectStr = "aos_actph,aos_whiteph,aos_designer,aos_salreq,aos_photoer,aos_3d";
				DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_proguser", SelectStr, filters);
				if (aos_mkt_proguser != null) {
					this.getModel().setValue(aos_actph, aos_mkt_proguser.get(aos_actph));
					this.getModel().setValue(aos_whiteph, aos_mkt_proguser.get(aos_whiteph));
					this.getModel().setValue(aos_designer, aos_mkt_proguser.get(aos_designer));
					this.getModel().setValue("aos_sale", aos_mkt_proguser.get("aos_salreq"));
					this.getModel().setValue("aos_vedior", aos_mkt_proguser.get("aos_photoer"));
					this.getModel().setValue("aos_3d", aos_mkt_proguser.get("aos_3d"));
				}
			}
			// 对于供应商
			long VendorId = Cux_Common_Utl.GetItemVendor(fid);
			if (VendorId != -1) {
				DynamicObject bd_supplier = BusinessDataServiceHelper.loadSingle(VendorId, "bd_supplier");
				// 赋值供应商相关
				this.getModel().setValue(aos_vendor, bd_supplier.get("name"));
				this.getModel().setValue(aos_poer, bd_supplier.get("aos_purer"));
				this.getModel().setValue(aos_follower, bd_supplier.get("aos_documentary"));
				// 供应商地址
				QFilter[] filterRelation = new QFilter("supplier", QCP.equals, VendorId).toArray();
				DynamicObject bd_address = BusinessDataServiceHelper.loadSingle("bd_address",
						"admindivision,admindivision.parent,aos_contacts,aos_addfulladdress,aos_addphone",
						filterRelation);
				if (bd_address != null) {
					QFilter[] filterid = new QFilter("id", QCP.equals, bd_address.get("admindivision")).toArray();
					DynamicObject bd_admindivision = BusinessDataServiceHelper.loadSingle("bd_admindivision",
							"parent.name", filterid);
					if (bd_admindivision != null)
						this.getModel().setValue(aos_city, bd_admindivision.get("parent.name"));
					this.getModel().setValue(aos_contact, bd_address.get("aos_contacts"));
					this.getModel().setValue(aos_address, bd_address.get("aos_addfulladdress"));
					this.getModel().setValue(aos_phone, bd_address.get("aos_addphone"));
				}
			}

//			boolean is3d = false;
//			// 3d计划表为否 不存在
//			if (!Judge3dPlan(fid, AosCategory1, AosCategory2, AosCategory3, "B")) {
//				// 3d计划表为是 存在
//				if (Judge3dPlan(fid, AosCategory1, AosCategory2, AosCategory3, "A")) {
//					is3d = true;
//				}
//				// 在3D选品表存在，拍照地点默认=工厂简拍
//				if (Judge3dSelect(AosCategory1, AosCategory2, AosCategory3, bd_material.getString("name"))) {
//					is3d = true;
//				}
//			}
//			if (is3d)
//				this.getModel().setValue(aos_phstate, "工厂简拍");

			Object aos_ponumber = this.getModel().getValue("aos_ponumber");
			if (FndGlobal.IsNotNull(aos_ponumber)) {
				DynamicObject isSealSample = QueryServiceHelper.queryOne("aos_sealsample", "aos_model",
						new QFilter("aos_item.id", QCP.equals, fid).and("aos_contractnowb", QCP.equals, aos_ponumber)
								.toArray());//
				if (FndGlobal.IsNotNull(isSealSample)) {
					String aos_model = isSealSample.getString("aos_model");
					if ("是".equals(aos_model))
						this.getModel().setValue("aos_phstate", "工厂简拍");
					else if ("否".equals(aos_model))
						this.getModel().setValue("aos_phstate", null);
				} else {
					Boolean exists = Judge3dSelect(AosCategory1, AosCategory2, AosCategory3,
							bd_material.getString("name"));
					if (exists)
						this.getModel().setValue("aos_phstate", "工厂简拍");
					else
						this.getModel().setValue("aos_phstate", null);
				}
			}

			// 增加爆品字段
			Boolean aos_is_saleout = Is_saleout(fid);
			this.getModel().setValue("aos_is_saleout", aos_is_saleout);

			if (aos_is_saleout) {
				this.getModel().setValue("aos_vedioflag", true);
			}

			// 摄影标准库字段
			this.getModel().deleteEntryData("aos_entryentity5");
			this.getModel().batchCreateNewEntryRow("aos_entryentity5", 1);
			this.getModel().setValue("aos_refer", "摄影标准库", 0);
			DynamicObject aosMktPhotoStd = QueryServiceHelper.queryOne("aos_mkt_photostd",
					"aos_firstpicture,aos_other,aos_require",
					new QFilter("aos_category1_name", QCP.equals, AosCategory1)
							.and("aos_category2_name", QCP.equals, AosCategory2)
							.and("aos_category3_name", QCP.equals, AosCategory3)
							.and("aos_itemnamecn", QCP.equals, aosItemName).toArray());
			if (FndGlobal.IsNotNull(aosMktPhotoStd)) {
				this.getModel().setValue("aos_reqfirst", aosMktPhotoStd.getString("aos_firstpicture"), 0);
				this.getModel().setValue("aos_reqother", aosMktPhotoStd.getString("aos_other"), 0);
				this.getModel().setValue("aos_detail", aosMktPhotoStd.getString("aos_require"), 0);
			}

			// 布景标准库字段
			DynamicObject aosMktViewStd = QueryServiceHelper.queryOne("aos_mkt_viewstd",
					"aos_scene1,aos_object1,aos_scene2,aos_object2,aos_itemnamecn,aos_descpic",
					new QFilter("aos_category1_name", QCP.equals, AosCategory1)
							.and("aos_category2_name", QCP.equals, AosCategory2)
							.and("aos_category3_name", QCP.equals, AosCategory3)
							.and("aos_itemnamecn", QCP.equals, aosItemName).toArray());
			if (FndGlobal.IsNotNull(aosMktViewStd)) {
				this.getModel().setValue("aos_scene1", aosMktViewStd.getString("aos_scene1"), 0);
				this.getModel().setValue("aos_object1", aosMktViewStd.getString("aos_object1"), 0);
				this.getModel().setValue("aos_scene2", aosMktViewStd.getString("aos_scene2"), 0);
				this.getModel().setValue("aos_object2", aosMktViewStd.getString("aos_object2"), 0);
				this.getModel().setValue("aos_descpic", aosMktViewStd.getString("aos_descpic"), 0);
			}

			DynamicObject aos_color = bd_material.getDynamicObject("aos_color");
			if (FndGlobal.IsNotNull(aos_color)) {
				String aos_rgb = aos_color.getString("aos_rgb");
				this.getModel().setValue("aos_rgb", aos_rgb);
				this.getModel().setValue("aos_colors", aos_color.get("aos_colors"));
				this.getModel().setValue("aos_colorname", aos_color.get("name"));
				this.getModel().setValue("aos_colorex", aos_color);

				HashMap<String, Object> fieldMap = new HashMap<>();
				// 设置前景色
				fieldMap.put(ClientProperties.BackColor, aos_rgb);
				// 同步指定元数据到控件
				this.getView().updateControlMetadata("aos_color", fieldMap);
			}
		}
	}

	/** 通用控制校验 **/
	private static void SaveControl(DynamicObject dy_main) throws FndError {
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		Object AosStatus = dy_main.get(aos_status);
		Boolean AosPhotoFlag = dy_main.getBoolean(aos_photoflag);
		Boolean AosVedioFlag = dy_main.getBoolean(aos_vedioflag);
		Object AosReason = dy_main.get(aos_reason);
		Object AosReqtype = dy_main.get(aos_reqtype);
		Object AosItemid = dy_main.get(aos_itemid);
		Object AosPhstate = dy_main.get(aos_phstate);
		Object aos_sameitemid = dy_main.get("aos_sameitemid");
		Object aos_poer_o = dy_main.get(aos_poer);
		Object aos_developer_o = dy_main.get(aos_developer);
		Object aos_follower_o = dy_main.get(aos_follower);
		Object aos_whiteph_o = dy_main.get(aos_whiteph);
		Object aos_actph_o = dy_main.get(aos_actph);
		Object aos_vedior_o = dy_main.get(aos_vedior);
		Object aos_designer_o = dy_main.get(aos_designer);
		Object aos_3d_o = dy_main.get("aos_3d");
		Object aos_vediotype = dy_main.get("aos_vediotype");
		Boolean StrightCut = false;
		Boolean aos_3dflag = dy_main.getBoolean("aos_3dflag");
		Object aos_3d_reason = dy_main.get("aos_3d_reason");

		if ("剪辑".equals(aos_vediotype) && AosVedioFlag) {
			StrightCut = true;
		}

		// 校验 视频优化类型为剪辑时 只勾选是否拍展示视频
		if ("剪辑".equals(aos_vediotype) && (!AosVedioFlag || AosPhotoFlag)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "视频优化类型为剪辑时 只允许勾选是否拍展示视频!");
		} else

		// 校验 新建时是否拍照、是否拍展示视频不能同时为false
		if ("新建".equals(AosStatus) && AosPhotoFlag != null && AosVedioFlag != null && !AosPhotoFlag && !AosVedioFlag
				&& (AosReason == null || "".equals(AosReason))) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "是否拍照与是否拍视频同时为否时不拍照原因必填!");
		}
		// 校验 新建时如果选择了拍照或者拍视频 拍照地点必填 非视频剪辑类型才做校验

		if ("开发/采购确认".equals(AosStatus) && AosPhotoFlag != null && AosVedioFlag != null
				&& (AosPhotoFlag || AosVedioFlag) && (AosPhstate == null || "".equals(AosPhstate)) && !StrightCut) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "选择了拍照或者拍视频则拍照地点必填!");
		}

		// 校验 不拍照原因在是否拍照为否时必填
		if (AosPhotoFlag != null && !AosPhotoFlag && (AosReason == null || "".equals(AosReason))) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "不拍照原因在是否拍照为否时必填!");
		}
		// 校验 同货号在是否拍照为否不拍照原因=同XX货号时必填
		if (AosPhotoFlag != null && !AosPhotoFlag
				&& (aos_sameitemid == null && AosReason != null && "同XX货号".equals((String) AosReason))) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "同货号在是否拍照为否,不拍照原因为同XX货号时必填!");
		}
		// 校验 需求类型在新建状态下必填
		if ("新建".equals(AosStatus) && (AosReqtype == null || "".equals(AosReqtype))) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "需求类型必填!");
		}
		// 校验 物料信息 新建状态下必填
		if ("新建".equals(AosStatus) && (AosItemid == null)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "物料信息必填!");
		}

		// 校验 转3D需填写原因
		if (aos_3dflag && Cux_Common_Utl.IsNull(aos_3d_reason)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "转3D需填写原因!");
		}

		// 校验人员信息是否都存在
		if (Cux_Common_Utl.IsNull(aos_poer_o)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "采购员必填!");
		}
		if (Cux_Common_Utl.IsNull(aos_developer_o)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "开发员必填!");
		}
		if (Cux_Common_Utl.IsNull(aos_follower_o)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "跟单员必填!");
		}
		if (Cux_Common_Utl.IsNull(aos_whiteph_o)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "白底摄影师必填!");
		}
		if (Cux_Common_Utl.IsNull(aos_actph_o)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "实景摄影师必填!");
		}
		if (Cux_Common_Utl.IsNull(aos_vedior_o)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "摄像师必填!");
		}
		if (Cux_Common_Utl.IsNull(aos_3d_o)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "3D设计师必填!");
		}
		if (Cux_Common_Utl.IsNull(aos_designer_o)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "设计师必填!");
		}
		/*
		 * if (Cux_Common_Utl.IsNull(aos_sale_o)) { ErrorCount++; ErrorMessage =
		 * FndError.AddErrorMessage(ErrorMessage, "销售人员必填!"); }
		 */

		// 校验 跟单 行政组别
		try {
			if (dy_main.get(aos_follower) != null) {
				DynamicObject AosFollower = dy_main.getDynamicObject(aos_follower);
				Object AosFollowerId = AosFollower.getPkValue();
				List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(AosFollowerId);
				if (MapList != null) {
					if (MapList.get(2) != null)
						MapList.get(2).get("id");
					if (MapList.get(3) != null)
						MapList.get(3).get("id");
				}
			}
		} catch (Exception ex) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "跟单行政组织级别有误!");
		}

		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}
	}

	/** 全局状态控制 **/
	private void StatusControl() {
		// 数据层
		Object AosStatus = this.getModel().getValue(aos_status);
		DynamicObject AosUser = (DynamicObject) this.getModel().getValue(aos_user);
		Object CurrentUserId = UserServiceHelper.getCurrentUserId();
		Object CurrentUserName = UserServiceHelper.getUserInfoByID((long) CurrentUserId).get("name");
		Object AosBillno = this.getModel().getValue(billno);
		// 图片控制
		InitPic();

		// 锁住需要控制的字段
		this.getView().setEnable(false, aos_photoflag);
		this.getView().setEnable(false, aos_reason);
		this.getView().setEnable(false, aos_sameitemid);
		this.getView().setEnable(false, aos_vedioflag);
		this.getView().setEnable(false, aos_newitem);
		this.getView().setEnable(false, aos_newvendor);
		this.getView().setEnable(false, aos_ponumber);
		this.getView().setEnable(false, aos_linenumber);
		this.getView().setEnable(false, aos_phstate);
		this.getView().setEnable(false, "aos_vediotype");

		this.getView().setEnable(false, "aos_phreason");
		this.getView().setEnable(false, "aos_dereason");

		// this.getView().setEnable(false, aos_poer);
		// this.getView().setEnable(false, aos_developer);
		// this.getView().setEnable(false, aos_follower);
		this.getView().setVisible(false, "aos_confirm");// 确认按钮
		this.getView().setVisible(false, "aos_back");// 退回按钮
		this.getView().setVisible(true, "aos_submit");
		this.getView().setVisible(true, "bar_new");
		this.getView().setVisible(false, "aos_3dflag");
		this.getView().setVisible(false, "aos_3d_reason");
		this.getView().setVisible(false, "aos_search");// 查看子流程
		this.getView().setVisible(true, "aos_flexpanelap11");
		this.getView().setVisible(true, "aos_flexpanelap12");
		this.getView().setVisible(true, "aos_flexpanelap13");
		this.getView().setVisible(true, "aos_flexpanelap14");
		this.getView().setVisible(true, "aos_flexpanelap18");
		this.getView().setVisible(true, "aos_flexpanelap15");
		this.getView().setVisible(true, "aos_flexpanelap20");

		this.getView().setVisible(true, "aos_flexpanelap14");

		// 当前节点操作人不为当前用户 全锁
		if (!AosUser.getPkValue().toString().equals(CurrentUserId.toString())) {
			this.getView().setEnable(false, "contentpanelflex");// 主界面面板
			this.getView().setEnable(false, "bar_save");
			this.getView().setEnable(false, "aos_submit");
			this.getView().setEnable(false, "aos_confirm");
		}

		// 状态控制
		Map<String, Object> map = new HashMap<>();
		if (AosBillno == null || AosBillno.equals("")) {
			this.getView().setVisible(false, "aos_submit");
			this.getView().setVisible(false, "aos_flexpanelap12");
			this.getView().setVisible(false, "aos_flexpanelap13");
			this.getView().setVisible(false, "aos_flexpanelap14");
			this.getView().setVisible(false, "aos_flexpanelap18");
			this.getView().setVisible(false, "aos_flexpanelap15");
			this.getView().setVisible(false, "aos_flexpanelap20");
		}
		if ("新建".equals(AosStatus) || "开发/采购确认".equals(AosStatus) || "跟单提样".equals(AosStatus)) {
			this.getView().setEnable(true, aos_photoflag);
			this.getView().setEnable(true, aos_reason);
			this.getView().setEnable(true, aos_sameitemid);
			this.getView().setEnable(true, aos_vedioflag);
			this.getView().setEnable(true, aos_phstate);
		}
		if ("新建".equals(AosStatus)) {
			this.getView().setEnable(true, aos_newitem);
			this.getView().setEnable(true, aos_newvendor);
			this.getView().setEnable(true, "aos_vediotype");
		} else if ("开发/采购确认".equals(AosStatus)) {
			this.getView().setEnable(true, aos_ponumber);
			this.getView().setEnable(true, aos_linenumber);
			this.getView().setEnable(true, aos_poer);
			this.getView().setEnable(true, aos_developer);
		} else if ("视频更新".equals(AosStatus)) {
			this.getView().setEnable(false, "contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "aos_submit");// 提交
			this.getView().setVisible(false, "bar_save");// 保存
			this.getView().setVisible(true, "aos_search");// 查看子流程
		} else if ("跟单提样".equals(AosStatus)) {
			// 跟单提样状态下不可修改 等待入库单回传
			this.getView().setEnable(false, "contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "aos_submit");// 提交
			this.getView().setVisible(false, "bar_save");// 保存
		} else if ("字幕翻译".equals(AosStatus)) {
			// 字幕翻译状态下不可修改 等待入库单回传
			this.getView().setEnable(false, "contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "aos_submit");// 提交
			this.getView().setVisible(false, "bar_save");// 保存
		} else if ("3D建模".equals(AosStatus)) {
			// 3D建模状态下不可修改 等待3D设计表回传
			this.getView().setEnable(false, "contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "aos_submit");// 提交
			this.getView().setVisible(false, "bar_save");// 保存
		} else if ("不需拍".equals(AosStatus)) {
			this.getView().setEnable(false, "contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "aos_submit");// 提交
			this.getView().setVisible(false, "bar_save");// 保存
		} else if ("开发/采购确认图片".equals(AosStatus)) {
			this.getView().setEnable(true, "contentpanelflex");
			this.getView().setEnable(false, "aos_flexpanelap");
			this.getView().setEnable(false, "aos_flexpanelap1");
			this.getView().setEnable(false, "aos_flexpanelap8");
			this.getView().setEnable(false, "aos_flexpanelap11");
			this.getView().setEnable(false, "aos_flexpanelap12");
			this.getView().setEnable(false, "aos_flexpanelap13");
			this.getView().setEnable(false, "aos_flexpanelap18");
			this.getView().setEnable(false, "aos_flexpanelap15");
			this.getView().setEnable(false, "aos_flexpanelap20");
			this.getView().setVisible(false, "aos_submit");// 提交
			this.getView().setVisible(false, "bar_save");// 保存
			if (AosUser.getPkValue().toString().equals(CurrentUserId.toString())) {
				this.getView().setVisible(true, "aos_confirm");// 确认
				this.getView().setVisible(true, "aos_back");// 退回按钮
				this.getView().setEnable(true, "aos_dereason");// 开发退回原因
			}
		} else if ("已完成".equals(AosStatus)) {
			// this.getView().setEnable(false, "titlepanel");// 标题面板
			this.getView().setEnable(false, "contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");
			this.getView().setVisible(false, "aos_submit");
			this.getView().setVisible(false, "aos_confirm");
		} else if ("白底拍摄".equals(AosStatus)) {
			this.getView().setVisible(true, "aos_back");// 退回按钮
			this.getView().setEnable(true, "aos_phreason");// 摄影师退回原因
			this.getView().setVisible(true, "aos_3dflag");
			this.getView().setVisible(true, "aos_3d_reason");
			// 白底摄影师
			DynamicObject aos_whiteph = this.getModel().getDataEntity(true).getDynamicObject("aos_whiteph");
			if (aos_whiteph != null) {
				if (aos_whiteph.getLong("id") == RequestContext.get().getCurrUserId()) {
					this.getView().setVisible(true, "aos_submit");
					this.getView().setEnable(true, "aos_submit");
				}
			}
		} else if ("编辑确认".equals(AosStatus)) {
			map.put(ClientProperties.Text, new LocaleString("编辑确认"));
			this.getView().updateControlMetadata("aos_submit", map);
			this.getView().setVisible(true, "aos_submit");
			this.getView().setEnable(false, "contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");
		} else if ("开发确认:视频".equals(AosStatus)) {
			map.put(ClientProperties.Text, new LocaleString("开发确认:视频"));
			this.getView().updateControlMetadata("aos_submit", map);
			this.getView().setVisible(true, "aos_submit");
			this.getView().setVisible(false, "bar_save");
			this.getView().setVisible(true, "aos_back");// 退回按钮

			this.getView().setEnable(true, "aos_dereason");// 开发退回原因

			this.getView().setEnable(true, "aos_flexpanelap");
			this.getView().setEnable(true, "aos_flexpanelap1");
			this.getView().setEnable(true, "aos_flexpanelap11");
			this.getView().setEnable(true, "aos_flexpanelap12");
			this.getView().setEnable(true, "aos_flexpanelap13");
			this.getView().setEnable(true, "aos_flexpanelap18");
			this.getView().setEnable(true, "aos_flexpanelap20");
			this.getView().setEnable(true, "aos_flexpanelap15");
			this.getView().setEnable(true, "aos_flexpanelap141");

		} else if ("视频剪辑".equals(AosStatus)) {
			map.put(ClientProperties.Text, new LocaleString("提交"));
			this.getView().updateControlMetadata("aos_submit", map);
		} else if ("实景拍摄".equals(AosStatus)) {
			map.put(ClientProperties.Text, new LocaleString("提交"));
			this.getView().updateControlMetadata("aos_submit", map);
			this.getView().setVisible(true, "aos_3dflag");
			this.getView().setVisible(true, "aos_3d_reason");
			// 实景摄影师
			DynamicObject aos_whiteph = this.getModel().getDataEntity(true).getDynamicObject("aos_actph");
			if (aos_whiteph != null) {
				if (aos_whiteph.getLong("id") == RequestContext.get().getCurrUserId()) {
					this.getView().setVisible(true, "aos_submit");
					this.getView().setEnable(true, "aos_submit");
				}
			}
		} else if ("视频拍摄".equals(AosStatus)) {
			// 摄影师
			DynamicObject aos_whiteph = this.getModel().getDataEntity(true).getDynamicObject("aos_vedior");
			if (aos_whiteph != null) {
				if (aos_whiteph.getLong("id") == RequestContext.get().getCurrUserId()) {
					this.getView().setVisible(true, "aos_submit");
					this.getView().setEnable(true, "aos_submit");
					this.getView().setVisible(true, "bar_new");
				}
			}
		}

		if (FndGlobal.IsNotNull(this.getModel().getValue("aos_rgb"))) {
			String aos_rgb = this.getModel().getValue("aos_rgb").toString();
			DynamicObject aos_colorex = (DynamicObject) this.getModel().getValue("aos_colorex");

			this.getModel().setValue("aos_rgb", aos_rgb);
			this.getModel().setValue("aos_colors", aos_colorex.get("aos_colors"));
			this.getModel().setValue("aos_colorname", aos_colorex.get("name"));
			this.getModel().setValue("aos_colorex", aos_colorex);

			HashMap<String, Object> fieldMap = new HashMap<>();
			fieldMap.put(ClientProperties.BackColor, aos_rgb);
			this.getView().updateControlMetadata("aos_color", fieldMap);

		}

		InitDifference();
	}

	/** 拍照与视频界面显示做区分 **/
	private void InitDifference() {
		Object aos_type = this.getModel().getValue("aos_type");
		if (Cux_Common_Utl.IsNull(aos_type))
			return;

		// 视频需要隐藏字段
		if ("视频".equals(aos_type)) {
			this.getView().setVisible(true, aos_photoflag);
			this.getView().setVisible(true, aos_vedioflag);
			this.getView().setVisible(false, aos_reqtype);
			this.getView().setVisible(false, aos_arrivaldate);
			this.getView().setVisible(false, aos_reason);
			this.getView().setVisible(false, aos_sameitemid);
			this.getView().setVisible(false, "aos_3dflag");
			this.getView().setVisible(false, "aos_3d_reason");
			this.getView().setVisible(false, aos_contrybrand);
			this.getView().setVisible(false, aos_newitem);
			this.getView().setVisible(false, aos_newvendor);
			this.getView().setVisible(false, aos_ponumber);
			this.getView().setVisible(false, aos_linenumber);
			this.getView().setVisible(false, "aos_earlydate");
			this.getView().setVisible(false, "aos_checkdate");
			this.getView().setVisible(false, aos_category1);
			this.getView().setVisible(false, aos_category2);
			this.getView().setVisible(false, aos_category3);
			this.getView().setVisible(false, aos_vendor);
			this.getView().setVisible(false, aos_city);
			this.getView().setVisible(false, aos_contact);
			this.getView().setVisible(false, aos_address);
			this.getView().setVisible(false, aos_phone);
			this.getView().setVisible(false, "aos_flexpanelap15");// 流程节点时间
			this.getView().setVisible(false, "aos_flexpanelap14");// 流程退回原因
			this.getView().setVisible(false, "aos_flexpanelap13");// 拍摄情况
			this.getView().setVisible(false, "aos_flexpanelap11");// 拍照需求
		} else if ("拍照".equals(aos_type)) {
			this.getView().setVisible(false, "aos_flexpanelap12");// 视频需求
			this.getView().setVisible(false, "aos_flexpanelap18");// 字幕需求
		}
	}

	/** 打开历史记录 **/
	private void aos_history() throws FndError {
		Cux_Common_Utl.OpenHistory(this.getView());
	}

	/** 退回 **/
	private void aos_back() throws FndError {
		// 先做数据校验判断是否可以确认
		SaveControl(this.getModel().getDataEntity(true));
		String AosStatus = this.getModel().getValue(aos_status).toString();// 根据状态判断当前流程节点
		boolean is_return = false;
		switch (AosStatus) {
		case "白底拍摄":
			BackForWhite();
			is_return = true;
			break;
		case "开发/采购确认图片":
			BackForConfirm();
			is_return = true;
			break;
		case "开发确认:视频":
			BackForConfirm();
			is_return = true;
			break;
		}
		if (is_return) {
			FndHistory.Create(this.getView(), "退回", AosStatus + " 退回");
			int returnTimes = (int) this.getModel().getValue("aos_returntimes") + 1;
			this.getModel().setValue("aos_returntimes", returnTimes);
			this.getView().invokeOperation("save");
			this.getView().invokeOperation("refresh");
		}
		// 确认完成后做新的界面状态控制
		StatusControl();
	}

	/** 确认 **/
	private void aos_confirm() throws FndError {
		// 先做数据校验判断是否可以确认
		SaveControl(this.getModel().getDataEntity(true));

		// 异常参数
		String ErrorMessage = "";

		// 数据层
		Object AosSourceid = this.getModel().getValue(aos_sourceid);
		Object AosType = this.getModel().getValue("aos_type");

		// 回写拍照任务清单
		if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
			DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
			if ("拍照".equals(AosType))
				aos_mkt_photolist.set("aos_phstatus", "已完成");
			else if ("视频".equals(AosType))
				aos_mkt_photolist.set("aos_vedstatus", "已完成");
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
					new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
		}
		// 如果是拍照类型的新品则生成 设计需求表
		if ("拍照".equals(AosType)) {
			GenerateDesign(this.getModel().getDataEntity(true));
		}
		FndHistory.Create(this.getView(), "确认", "确认");
		// 执行保存操作
		this.getModel().setValue(aos_status, "已完成");// 设置单据流程状态
		this.getModel().setValue(aos_user, system);// 设置操作人为系统管理员
		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");

		// 确认完成后做新的界面状态控制
		StatusControl();
	}

	/** 提交 **/
	public void aos_submit(DynamicObject dy_main, String type) throws FndError {
		SaveControl(dy_main);// 先做数据校验判断是否可以提交
		String AosStatus = dy_main.getString(aos_status);// 根据状态判断当前流程节点
		switch (AosStatus) {
		case "新建":
			SubmitForNew(dy_main);
			break;
		case "开发/采购确认":
			SubmitForProductConfirm(dy_main);
			break;
		case "白底拍摄":
			SubmitForWhite(dy_main);
			break;
		case "实景拍摄":
			SubmitForAct(dy_main);
			break;
		case "视频拍摄":
			SubmitForVedio();
			break;
		case "视频剪辑":
			SubmitForCut();
			break;
		case "编辑确认":
			SubmitForEdit();
			break;
		case "开发确认:视频":
			SubmitForVc();
			break;
		case "视频更新":
			aos_confirm();
			break;
		case "视频更新:多人会审":
			aos_sonsubmit();
			break;
		}
		SaveServiceHelper.saveOperate("aos_mkt_photoreq", new DynamicObject[] { dy_main }, OperateOption.create());
		FndHistory.Create(dy_main, "提交", AosStatus);
		if (type.equals("A")) {
			this.getView().invokeOperation("refresh");
			StatusControl();// 提交完成后做新的界面状态控制
		}
	}

	/** 视频更新 多人会审 提交 **/
	private void aos_sonsubmit() {
		// 本单直接变为已完成
		this.getModel().setValue(aos_user, system);
		this.getModel().setValue(aos_status, "已完成");// 设置单据流程状态
		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");

		// 判断是否为最后一个完成
		Object aos_parentid = this.getModel().getValue("aos_parentid");
		QFilter filter_id = new QFilter("aos_parentid", "=", aos_parentid);
		QFilter filter_status = new QFilter("aos_status", "=", "视频更新:多人会审");
		QFilter[] filters = new QFilter[] { filter_id, filter_status };
		DynamicObject aos_mkt_photoreq = QueryServiceHelper.queryOne("aos_mkt_photoreq", "id", filters);
		// 全部已完成 修改主流程状态
		if (aos_mkt_photoreq == null) {
			// 回写主流程
			aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(aos_parentid, "aos_mkt_photoreq");
			aos_mkt_photoreq.set("aos_status", "已完成");
			aos_mkt_photoreq.set("aos_user", system);
			OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq", new DynamicObject[] { aos_mkt_photoreq },
					OperateOption.create());
			// 回写拍照任务清单
			if (QueryServiceHelper.exists("aos_mkt_photolist", aos_mkt_photoreq.get(aos_sourceid))) {
				DynamicObject aos_mkt_photolist = BusinessDataServiceHelper
						.loadSingle(aos_mkt_photoreq.get(aos_sourceid), "aos_mkt_photolist");
				aos_mkt_photolist.set("aos_vedstatus", "已完成");
				OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
						new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
			}
		}
	}

	/**
	 * 新建状态下提交
	 * 
	 * @param dy_main
	 **/
	public static void SubmitForNew(DynamicObject dy_main) throws FndError {
		// 异常参数
		int ErrorCount = 0;
		String ErrorMessage = "";

		// 数据层
		Object AosNewItem = dy_main.get(aos_newitem);
		Object AosPoer = dy_main.get(aos_poer);
		Object AosDeveloper = dy_main.get(aos_developer);
		Object AosPhotoFlag = dy_main.get(aos_photoflag);
		Object AosVedioFlag = dy_main.get(aos_vedioflag);
		Object AosSourceid = dy_main.get(aos_sourceid);
		Object AosBillno = dy_main.get(billno);
		Object ReqFId = dy_main.get("id"); // 当前界面主键
		Object aos_vediotype = dy_main.get("aos_vediotype");
		Boolean StrightCut = false;
		Object AosVedior = dy_main.get(aos_vedior);// 视频剪辑流转给摄像师

		Object aos_picdesc = null;
		Object aos_veddesc = null;

		DynamicObjectCollection aos_entryentityS = dy_main.getDynamicObjectCollection("aos_entryentity6");
		if (aos_entryentityS.size() > 0)
			aos_picdesc = aos_entryentityS.get(0).get("aos_reqsupp");
		else
			aos_picdesc = dy_main.getDynamicObjectCollection("aos_entryentity").get(1).get("aos_picdesc");

		DynamicObjectCollection aos_entryentity1S = dy_main.getDynamicObjectCollection("aos_entryentity1");
		if (!Cux_Common_Utl.IsNull(aos_entryentity1S))
			aos_veddesc = aos_entryentity1S.get(0).get("aos_veddesc");
		else
			aos_veddesc = dy_main.getDynamicObjectCollection("aos_entryentity1").get(1).get("aos_veddesc");

		// 校验
		if ((Boolean) AosNewItem && AosDeveloper == null) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "新产品开发为空,流程无法流转!");
		} else if (!(Boolean) AosNewItem && AosPoer == null) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "非新产品采购为空,流程无法流转!");
		}

		// 校验 申请人需求
		if ((Boolean) AosPhotoFlag && Cux_Common_Utl.IsNull(aos_picdesc)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "新建状态下 若勾选拍照 则申请人拍照需求必填!");
		}
		if ((Boolean) AosVedioFlag && Cux_Common_Utl.IsNull(aos_veddesc)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "新建状态下 若勾选视频 则申请人视频需求必填!");
		}

		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		if ("剪辑".equals(aos_vediotype) && (Boolean) AosVedioFlag) {
			StrightCut = true;
		}

		// 回写拍照任务清单
		if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
			DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
			if ((Boolean) AosPhotoFlag)
				aos_mkt_photolist.set("aos_phstatus", "开发/采购确认");
			if ((Boolean) AosVedioFlag)
				if (StrightCut) {
					aos_mkt_photolist.set("aos_vedstatus", "视频拍摄");
				} else {
					aos_mkt_photolist.set("aos_vedstatus", "开发/采购确认");
				}
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
					new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
		}

		// 执行保存操作
		String MessageId = null;
		String AosStatus = null;
		Object AosUser = null;
		String message = "拍照需求表-开发/采购确认";

		if ((Boolean) AosNewItem) {
			AosUser = AosDeveloper;// 勾选新产品流转给开发
			MessageId = ((DynamicObject) AosDeveloper).getPkValue().toString();
		} else {
			AosUser = AosPoer; // 不勾新产品流转给采购
			MessageId = ((DynamicObject) AosPoer).getPkValue().toString();
		}

		if (StrightCut) {
			dy_main.set(aos_vedior, UserServiceHelper.getCurrentUserId());
			AosUser = UserServiceHelper.getCurrentUserId();// 直接流转给摄像
			MessageId = UserServiceHelper.getCurrentUserId() + "";
			AosStatus = "视频剪辑";
			dy_main.set(aos_type, "视频");
			message = "拍照需求表-视频拍摄";
		} else {
			AosStatus = "开发/采购确认";
		}

		dy_main.set(aos_user, AosUser);
		dy_main.set(aos_status, AosStatus);// 设置单据流程状态

		OperationServiceHelper.executeOperate("save", aos_mkt_photoreq, new DynamicObject[] { dy_main },
				OperateOption.create());

		// 发送消息
		MKTCom.SendGlobalMessage(MessageId, aos_mkt_photoreq, ReqFId + "", AosBillno + "", message);
	}

	/** 开发/采购确认 状态下提交 **/
	private static void SubmitForProductConfirm(DynamicObject dy_mian) throws FndError {
		// 异常参数
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		Object AosSourceid = dy_mian.get(aos_sourceid);
		Object AosPhotoFlag = dy_mian.get(aos_photoflag);
		Object AosVedioFlag = dy_mian.get(aos_vedioflag);
		Object AosFollower = dy_mian.get(aos_follower);
		Object AosPoNumber = dy_mian.get(aos_ponumber);

		DynamicObject aos_itemid = dy_mian.getDynamicObject("aos_itemid");

		// 子流程不做判断
		NewControl(dy_mian);

		DynamicObjectCollection dyc_photo = dy_mian.getDynamicObjectCollection("aos_entryentity6");
		Object aos_reqsupp = null;
		Object aos_veddesc = null;
		if (dyc_photo.size() > 0) {
			aos_reqsupp = dy_mian.getDynamicObjectCollection("aos_entryentity6").get(0).get("aos_devsupp");
		} else {
			aos_reqsupp = dy_mian.getDynamicObjectCollection("aos_entryentity").get(1).get("aos_picdesc");
		}
		aos_veddesc = dy_mian.getDynamicObjectCollection("aos_entryentity1").get(1).get("aos_veddesc");
		// 校验
		if ((Boolean) AosPhotoFlag && Cux_Common_Utl.IsNull(aos_reqsupp)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "若勾选拍照 则拍照需求必填!");
		}
		if ((Boolean) AosVedioFlag && Cux_Common_Utl.IsNull(aos_veddesc)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "若勾选视频 则视频需求必填!");
		}

		// 拍照地点=工厂简拍，且新产品=是或新供应商=是，开发/采购节点提交时增加判断：合同号+SKU对应的封样流程大货样的产品整体图有图，否则提示“3D建模，必须有大货封样图片！”；
		Object aos_phstate = dy_mian.get("aos_phstate");// 拍照地点
		Boolean aos_newitem = dy_mian.getBoolean("aos_newitem");// 新产品
		Boolean aos_newvendor = dy_mian.getBoolean("aos_newvendor");// 新供应商

		if ("工厂简拍".equals(aos_phstate) && (aos_newitem || aos_newvendor)) {
			Boolean isSealSample = QueryServiceHelper.exists("aos_sealsample",
					new QFilter("aos_item.id", QCP.equals, aos_itemid.getPkValue())
							.and("aos_contractnowb", QCP.equals, AosPoNumber).and("aos_islargeseal", QCP.equals, "是")
							.toArray());
			if (isSealSample)
				ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "3D建模，必须有大货封样图片!");
		}

		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}
		// 不拍照不拍视频 状态直接调整为不需拍 并进入不拍照任务清单
		if (!(Boolean) AosPhotoFlag && !(Boolean) AosVedioFlag) {
			// 生成不拍照任务清单
			aos_mkt_nophoto_bill.create_noPhotoEntity(dy_mian);
		} else {
			// 校验跟单是否为空
			if (AosFollower == null) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "跟单为空,流程无法流转!");
			}
			// 校验采购订单号是否为空
			if (AosPoNumber == null || AosPoNumber.equals("")) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "开发/采购确认节点,采购订单号不允许为空!");
			} else {
				// 校验采购订单号、行号是否存在
				QFilter filter_billno = new QFilter("billno", "=", AosPoNumber);
				QFilter[] filters = new QFilter[] { filter_billno };
				DynamicObject aos_purcontract = QueryServiceHelper.queryOne("aos_purcontract", "id", filters);
				if (aos_purcontract == null) {
					ErrorCount++;
					ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "购销合同不存在!");
				}
			}
			if (ErrorCount > 0) {
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
		}

		// 回写拍照任务清单
		if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
			DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
			if (!(Boolean) AosPhotoFlag && !(Boolean) AosVedioFlag) {
				aos_mkt_photolist.set("aos_phstatus", "不需拍");
				aos_mkt_photolist.set("aos_vedstatus", "不需拍");
			} else {
				aos_mkt_photolist.set("aos_phstatus", "跟单提样");
				aos_mkt_photolist.set("aos_vedstatus", "跟单提样");
			}
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
					new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
		}

		// 执行保存操作
		dy_mian.set(aos_status, "跟单提样");// 设置单据流程状态
		if (!(Boolean) AosPhotoFlag && !(Boolean) AosVedioFlag) {
			dy_mian.set(aos_status, "不需拍");// 拍照需求表状态调整为不需拍
			dy_mian.set(aos_user, system);// 将流程人员设置为系统管理员
		} else {
			// 根据采购订单获取备货单出运日期 预计到港日期
//			Object aos_shipmentdate = null;
//			Object aos_estarrivaldate = null;
//			QFilter filter_billno = new QFilter("aos_stockentry.aos_sto_contractno", "=", AosPoNumber);
//			QFilter filter_linenum = new QFilter("aos_stockentry.aos_sto_lineno", "=", AosLineNumber);
//			QFilter[] filters = new QFilter[] { filter_billno, filter_linenum };
//			String SelectStr = "max(aos_shipmentdate) aos_shipmentdate,max(aos_estarrivaldate) aos_estarrivaldate";
//			DynamicObject aos_preparationnote = QueryServiceHelper.queryOne("aos_preparationnote", SelectStr, filters);
//			if (aos_preparationnote != null) {
//				aos_shipmentdate = aos_preparationnote.get("aos_shipmentdate");
//				aos_estarrivaldate = aos_preparationnote.get("aos_estarrivaldate");
//				this.getModel().setValue(aos_shipdate, aos_shipmentdate);
//				this.getModel().setValue(aos_arrivaldate, aos_estarrivaldate);
//			}

			dy_mian.set(aos_user, AosFollower);// 流转给跟单
			// 样品入库通知单 在样品入库通知单中通知跟单
			GenerateRcv(dy_mian);
		}
	}

	/** 白底拍摄 状态下提交 **/
	private static void SubmitForWhite(DynamicObject dy_main) throws FndError {
		// 异常参数
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		Object AosSourceid = dy_main.get(aos_sourceid);
		Object AosBillno = dy_main.get(billno);
		Object AosActph = dy_main.get(aos_actph);
		Object aos_3d = dy_main.get("aos_3d");
		Object ReqFId = dy_main.getPkValue(); // 当前界面主键
		Boolean aos_3dflag = dy_main.getBoolean("aos_3dflag");
		String MessageId = null;
		String MessageStr = null;
		String Status = "";
		Date now = new Date();

		// 校验
		if (AosActph == null && !aos_3dflag) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "实景摄影师为空,视频流程无法流转!");
		}
		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		if (aos_3dflag) {
			DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(ReqFId, "aos_mkt_photoreq");
			aos_mkt_3design_bill.Generate3Design(aos_mkt_photoreq);// 生成3D确认单
			Status = "3D建模";
			MessageStr = "拍照需求表-3D建模";
			MessageId = ((DynamicObject) aos_3d).getPkValue() + "";
		} else {
			// 回写拍照任务清单
			Status = "实景拍摄";
			MessageStr = "拍照需求表-实景拍摄";
			MessageId = ((DynamicObject) AosActph).getPkValue() + "";
		}

		if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
			DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
			aos_mkt_photolist.set("aos_phstatus", Status);
			aos_mkt_photolist.set("aos_whitedate", now);
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
					new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
		}

		// 执行保存操作
		dy_main.set(aos_status, Status);
		dy_main.set(aos_user, MessageId);
		dy_main.set("aos_whitedate", now);// 设置白底完成日期为当前日期
		dy_main.set("aos_whiteph", RequestContext.get().getCurrUserId());

		DynamicObject query = QueryServiceHelper.queryOne(aos_mkt_photoreq, "id",
				new QFilter("billno", QCP.equals, AosBillno).and("aos_type", QCP.equals, "视频").toArray());
		if (FndGlobal.IsNotNull(query)) {
			DynamicObject dyn = BusinessDataServiceHelper.loadSingle(query.get("id"), aos_mkt_photoreq);
			dyn.set("aos_whitedate", now);// 设置白底完成日期为当前日期
			dyn.set("aos_whiteph", RequestContext.get().getCurrUserId());
			OperationServiceHelper.executeOperate("save", aos_mkt_photoreq, new DynamicObject[] { dyn },
					OperateOption.create());
		}

		// 发送消息
		MKTCom.SendGlobalMessage(MessageId, aos_mkt_photoreq, ReqFId + "", AosBillno + "", MessageStr);
	}

	/** 实景拍摄 状态下提交 **/
	private static void SubmitForAct(DynamicObject dy_mian) throws FndError {
		// 异常参数
		String ErrorMessage = "";
		// 数据层
		Object AosSourceid = dy_mian.get(aos_sourceid);
		Object AosBillno = dy_mian.get(billno);
		Object AosDeveloper = dy_mian.get(aos_developer);
		DynamicObject AosItemid = dy_mian.getDynamicObject(aos_itemid);
		Object AosItemName = dy_mian.get(aos_itemname);
		Object AosShipdate = dy_mian.get(aos_shipdate);
		Object AosArrivaldate = dy_mian.get(aos_arrivaldate);
		Object ReqFId = dy_mian.getPkValue(); // 当前界面主键
		Object aos_3d = dy_mian.get("aos_3d");

		String sku = dy_mian.getDynamicObject("aos_itemid").getString("number");
		String poNumber = dy_mian.getString("aos_ponumber");

		Boolean aos_3dflag = dy_mian.getBoolean("aos_3dflag");
		String MessageId = null;
		String MessageStr = null;
		String Status = "";
		Date now = new Date();
		boolean createPS = false; // 是否生成抠图任务表

		// 校验
		if (aos_3dflag) {
			DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(ReqFId, "aos_mkt_photoreq");
			aos_mkt_3design_bill.Generate3Design(aos_mkt_photoreq);// 生成3D确认单
			Status = "3D建模";
			MessageStr = "拍照需求表-3D建模";
			MessageId = ((DynamicObject) aos_3d).getPkValue() + "";
			String aos_phstate = dy_mian.getString("aos_phstate"); // 拍照地点
			if (aos_phstate.equals("来样拍照"))
				createPS = true;
		} else {
			// 回写拍照任务清单
			Status = "开发/采购确认图片";
			MessageStr = "拍照需求表-开发/采购确认图片";
			MessageId = ((DynamicObject) AosDeveloper).getPkValue() + "";
			createPS = true;
		}
		if (createPS) {
			// 生成抠图任务清单
			DynamicObject aos_mkt_pslist = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pslist");
			aos_mkt_pslist.set("billstatus", "A");
			aos_mkt_pslist.set("aos_initdate", now);
			aos_mkt_pslist.set("aos_reqno", AosBillno);
			aos_mkt_pslist.set("aos_sourceid", ReqFId);
			aos_mkt_pslist.set(aos_itemid, AosItemid);
			aos_mkt_pslist.set(aos_itemname, AosItemName);
			aos_mkt_pslist.set(aos_designer, ProgressUtil.findPSlistDesign(AosItemid.getPkValue()));

			// 出运日期 到港日期 入库日期
			Date shippingDate = ProgressUtil.getShippingDate(poNumber, sku);
			Date arrivalDate = ProgressUtil.getArrivalDate(poNumber, sku);
			Date rcvDate = ProgressUtil.getRcvDate(poNumber, sku);
			Date today = new Date();
			aos_mkt_pslist.set(aos_shipdate, shippingDate);
			aos_mkt_pslist.set(aos_arrivaldate, arrivalDate);
			aos_mkt_pslist.set("aos_rcvdate", rcvDate);
			// 紧急状态
			String aos_emstatus = null;
			if (FndGlobal.IsNotNull(rcvDate) && rcvDate.compareTo(today) <= 0) {
				aos_emstatus = "非常紧急";
			} else if (FndGlobal.IsNotNull(arrivalDate) && arrivalDate.compareTo(today) <= 0) {
				aos_emstatus = "紧急";
			} else if (FndGlobal.IsNotNull(shippingDate)) {
				int days = FndDate.GetBetweenDays(today, shippingDate);
				aos_emstatus = "出运" + days + "天";
			}
			aos_mkt_pslist.set("aos_emstatus", aos_emstatus);

			aos_mkt_pslist.set(aos_status, "新建");
			OperationResult operationrstps = OperationServiceHelper.executeOperate("save", "aos_mkt_pslist",
					new DynamicObject[] { aos_mkt_pslist }, OperateOption.create());
			if (operationrstps.getValidateResult().getValidateErrors().size() != 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "抠图任务清单保存失败!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
		}

		// 回写拍照任务清单
		if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
			DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
			aos_mkt_photolist.set("aos_phstatus", Status);
			aos_mkt_photolist.set("aos_actdate", now);// 同步实景完成日期为当前日期
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
					new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
		}

//		//实景提交，如果完成数量为空或者0，设置为1
//		int qty = 0;
//		if (this.getModel().getValue("aos_completeqty",1)!= null){
//			qty = (int) this.getModel().getValue("aos_completeqty",1);
//		}
//		if (qty ==0)
//			qty = 2;
//		this.getModel().setValue("aos_completeqty",qty,1);

		// 执行保存操作
		dy_mian.set("aos_actph", RequestContext.get().getCurrUserId());
		dy_mian.set(aos_status, Status);// 设置单据流程状态
		dy_mian.set(aos_user, MessageId);// 流转给开发
		dy_mian.set("aos_actdate", now);// 设置实景完成日期为当前日期

		DynamicObject query = QueryServiceHelper.queryOne(aos_mkt_photoreq, "id",
				new QFilter("billno", QCP.equals, AosBillno).and("aos_type", QCP.equals, "视频").toArray());
		if (FndGlobal.IsNotNull(query)) {
			DynamicObject dyn = BusinessDataServiceHelper.loadSingle(query.get("id"), aos_mkt_photoreq);
			dy_mian.set("aos_actdate", now);// 设置实景完成日期为当前日期
			dy_mian.set("aos_actph", RequestContext.get().getCurrUserId());
			OperationServiceHelper.executeOperate("save", aos_mkt_photoreq, new DynamicObject[] { dyn },
					OperateOption.create());
		}

		// 发送消息
		MKTCom.SendGlobalMessage(MessageId, aos_mkt_photoreq, ReqFId + "", AosBillno + "", MessageStr);
	}

	/** 视频拍摄 状态下提交 **/
	private void SubmitForVedio() throws FndError {
		// 异常参数
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		Object AosSourceid = this.getModel().getValue(aos_sourceid);
		Object AosBillno = this.getModel().getValue(billno);
		Object AosVedior = this.getModel().getValue(aos_vedior);
		Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键
		Object aos_subtitle = this.getModel().getValue("aos_subtitle");
		Object aos_language = this.getModel().getValue("aos_language");
		Boolean StrightCut = false;
		String AosStatus = null;
		Object AosUser = null;
		String MessageId = null;

		// 根据国别大类中类取对应营销US编辑
		Object ItemId = ((DynamicObject) this.getModel().getValue("aos_itemid")).getPkValue();
		String category = (String) SalUtil.getCategoryByItemId(ItemId + "").get("name");
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
			String SelectStr = "aos_eng";
			DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_proguser", SelectStr,
					filters_category);
			if (aos_mkt_proguser != null) {
				aos_editor = aos_mkt_proguser.getLong("aos_eng");
			}
		}
		if (aos_editor == 0) {
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, AosCategory1 + "," + AosCategory2 + "品类英语编辑不存在!");
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		// 校验
		if ((Cux_Common_Utl.IsNull(aos_subtitle) && !Cux_Common_Utl.IsNull(aos_language))
				|| (!Cux_Common_Utl.IsNull(aos_subtitle) && Cux_Common_Utl.IsNull(aos_language))) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "字幕需求必须同时填写 或 同时不填写!");
		}
		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		if (!Cux_Common_Utl.IsNull(aos_subtitle) && !Cux_Common_Utl.IsNull(aos_language))
			StrightCut = true;

		if (StrightCut) {
			AosStatus = "字幕翻译";
			AosUser = aos_editor;
			MessageId = aos_editor + "";
			GenerateListing();// 按照语言生成Listing优化需求子表&小语种
		} else {
			AosStatus = "视频剪辑";
			this.getModel().setValue(aos_vedior, UserServiceHelper.getCurrentUserId());
			AosUser = UserServiceHelper.getCurrentUserId();
			MessageId = UserServiceHelper.getCurrentUserId() + "";
		}

		// 回写拍照任务清单
		if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
			DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
			aos_mkt_photolist.set("aos_vedstatus", AosStatus);
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
					new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
		}

		// 执行保存操作
		this.getModel().setValue(aos_status, AosStatus);// 设置单据流程状态
		this.getModel().setValue(aos_user, AosUser);// 流转给摄像师
		this.getModel().setValue("aos_vediodate", new Date());// 视频拍摄完成日期

		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");
		// 发送消息
		if (StrightCut) {
		} else
			MKTCom.SendGlobalMessage(MessageId, aos_mkt_photoreq, ReqFId + "", AosBillno + "", "拍照需求表-视频剪辑");
	}

	/** 生成Listing优化需求表子表 视频语言只能填英语 **/
	private void GenerateListing() throws FndError {
		// 信息参数
		String MessageId = null;
		String Message = "";
		// 异常参数
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		long CurrentUserId = UserServiceHelper.getCurrentUserId();
		DynamicObject AosDesigner = (DynamicObject) this.getModel().getValue("aos_designer");
		Object AosDesignerId = AosDesigner.getPkValue();
		Object billno = this.getModel().getValue("billno");
		Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键
		Object aos_reqtype = this.getModel().getValue("aos_reqtype");// 任务类型
		Object ItemId = ((DynamicObject) this.getModel().getValue("aos_itemid")).getPkValue();
		Object aos_requireby = CurrentUserId;// 申请人为拍照需求表当前操作人
		Object aos_subtitle = this.getModel().getValue("aos_subtitle");
		Object aos_demandate = new Date();
		Object aos_osconfirmlov = null;
		if ("四者一致".equals(aos_type)) {
			aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
			aos_osconfirmlov = "否";
		} else if ("优化".equals(aos_type))
			aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);

		// 拍照任务类型转化
		if ("新品".equals(aos_reqtype))
			aos_reqtype = "新品设计";
		else if ("老品".equals(aos_reqtype))
			aos_reqtype = "老品优化";
		List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(aos_requireby);
		String aos_orgtext = "";
		DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(ItemId, "bd_material");
		String aos_productno = bd_material.getString("aos_productno");
		String aos_itemname = bd_material.getString("name");
		String item_number = bd_material.getString("number");
		DynamicObjectCollection aos_contryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
		// 获取所有国家品牌 字符串拼接 终止
		for (DynamicObject aos_contryentry : aos_contryentryS) {
			DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
			String aos_nationalitynumber = aos_nationality.getString("number");
			if ("IE".equals(aos_nationalitynumber))
				continue;
			Object org_id = aos_nationality.get("id"); // ItemId
			int OsQty = iteminfo.GetItemOsQty(org_id, ItemId);
			int SafeQty = iteminfo.GetSafeQty(org_id);
			if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
				continue;
			aos_orgtext = aos_orgtext + aos_nationalitynumber + ";";
		}
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
		// 根据国别大类中类取对应营销US编辑
		String category = MKTCom.getItemCateNameZH(ItemId + "");
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
			DynamicObject dy_editor = ProgressUtil.findEditorByType(AosCategory1, AosCategory2,
					String.valueOf(aos_reqtype));
			if (dy_editor != null) {
				aos_editor = dy_editor.getLong("aos_user");
			}
		}
		if (aos_editor == 0) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, AosCategory1 + "," + AosCategory2 + "品类英语编辑不存在!");
		}

		// 校验
		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		// 创建Listing优化需求表子表
		DynamicObject aos_mkt_listing_son = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_son");
		aos_mkt_listing_son.set("aos_type", aos_reqtype);
		aos_mkt_listing_son.set("aos_designer", AosDesignerId);
		aos_mkt_listing_son.set("aos_editor", aos_editor);
		aos_mkt_listing_son.set("aos_user", aos_editor);
		aos_mkt_listing_son.set("aos_orignbill", billno);
		aos_mkt_listing_son.set("aos_sourceid", ReqFId);
		aos_mkt_listing_son.set("aos_status", "编辑确认");
		aos_mkt_listing_son.set("aos_sourcetype", "VED");
		aos_mkt_listing_son.set("aos_requireby", aos_requireby); // 申请人为当前操作人
		aos_mkt_listing_son.set("aos_requiredate", new Date());
		aos_mkt_listing_son.set("aos_ismall", true);
		aos_mkt_listing_son.set("aos_ismalllov", "是");
		aos_mkt_listing_son.set("aos_demandate", aos_demandate);
		aos_mkt_listing_son.set("aos_osconfirmlov", aos_osconfirmlov);
		// BOTP
		aos_mkt_listing_son.set("aos_sourcebilltype", aos_mkt_photoreq);
		aos_mkt_listing_son.set("aos_sourcebillno", billno);
		aos_mkt_listing_son.set("aos_srcentrykey", "aos_entryentity");

		if (MapList != null) {
			if (MapList.get(2) != null)
				aos_mkt_listing_son.set("aos_organization1", MapList.get(2).get("id"));
			if (MapList.get(3) != null)
				aos_mkt_listing_son.set("aos_organization2", MapList.get(3).get("id"));
		}
		DynamicObjectCollection aos_entryentityS = aos_mkt_listing_son.getDynamicObjectCollection("aos_entryentity");
		DynamicObject aos_entryentity = aos_entryentityS.addNew();
		aos_entryentity.set("aos_itemid", ItemId);
		aos_entryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(ItemId));
		aos_entryentity.set("aos_require", aos_subtitle);
		aos_entryentity.set("aos_srcrowseq", 1);
		// 物料相关信息
		DynamicObjectCollection aos_subentryentityS = aos_entryentity.getDynamicObjectCollection("aos_subentryentity");
		DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
		aos_subentryentity.set("aos_segment3", aos_productno);
		aos_subentryentity.set("aos_broitem", aos_broitem);
		aos_subentryentity.set("aos_itemname", aos_itemname);
		aos_subentryentity.set("aos_orgtext", aos_orgtext);
		aos_subentryentity.set("aos_reqinput", aos_subtitle);

		aos_entryentity.set("aos_segment3_r", aos_productno);
		aos_entryentity.set("aos_broitem_r", aos_broitem);
		aos_entryentity.set("aos_itemname_r", aos_itemname);
		aos_entryentity.set("aos_orgtext_r", ProgressUtil.getOrderOrg(ItemId));

		// 消息推送
		MessageId = aos_editor + "";
		Message = "Listing优化需求表子表-拍照需求表:视频-自动创建";
		aos_mkt_listingson_bill.setListSonUserOrganizate(aos_mkt_listing_son);
		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_son",
				new DynamicObject[] { aos_mkt_listing_son }, OperateOption.create());

		// 修复关联关系
		try {
			ProgressUtil.botp("aos_mkt_listing_son", aos_mkt_listing_son.get("id"));
		} catch (Exception ex) {
			FndError.showex(getView(), ex, FndWebHook.urlMms);
		}

		if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			MKTCom.SendGlobalMessage(MessageId, aos_mkt_listing_son + "", operationrst.getSuccessPkIds().get(0) + "",
					aos_mkt_listing_son.getString("billno"), Message);
		}
	}

	/** 视频剪辑 状态下提交 直接到 开发确认：视频 **/
	private void SubmitForCut() throws FndError {
		// 异常参数
		String ErrorMessage = "";
		// 数据层
		DynamicObject AosDeveloper = (DynamicObject) this.getModel().getValue(aos_developer);
		if (AosDeveloper == null) {
			throw new FndError("开发不能为空");
		}
		Object AosSourceid = this.getModel().getValue(aos_sourceid);
		Object AosBillno = this.getModel().getValue(billno);
		Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键
		// 回写拍照任务清单
		if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
			DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
			aos_mkt_photolist.set("aos_vedstatus", "开发确认:视频");
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
					new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
		}
		// 执行保存操作
		this.getModel().setValue(aos_status, "开发确认:视频");// 设置单据流程状态
		this.getModel().setValue("aos_cutdate", new Date());// 视频剪辑完成日期

		String MessageId = null;
		this.getModel().setValue(aos_user, AosDeveloper.getPkValue());// 流转给开发
		MessageId = AosDeveloper.getPkValue() + "";
		// 发送消息
		MKTCom.SendGlobalMessage(MessageId, aos_mkt_photoreq, ReqFId + "", AosBillno + "", "拍照需求表-开发确认");
	}

	/** 编辑确认 状态下提交 **/
	private void SubmitForEdit() throws FndError {
		// 异常参数
		String ErrorMessage = "";
		// 数据层
		Object AosSourceid = this.getModel().getValue(aos_sourceid);
		Object AosBillno = this.getModel().getValue(billno);
		Object AosDeveloper = this.getModel().getValue(aos_developer);
		Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键

		// 校验

		// 回写拍照任务清单
		if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
			DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
			aos_mkt_photolist.set("aos_vedstatus", "开发确认:视频");
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
					new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
		}

		// 执行保存操作
		this.getModel().setValue(aos_status, "开发确认:视频");// 设置单据流程状态
		String MessageId = null;
		this.getModel().setValue(aos_user, AosDeveloper);// 流转给开发
		MessageId = ((DynamicObject) AosDeveloper).getPkValue().toString();
		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");
		// 发送消息
		MKTCom.SendGlobalMessage(MessageId, aos_mkt_photoreq, ReqFId + "", AosBillno + "", "拍照需求表-开发确认");
	}

	/** 开发确认:视频 状态下提交 **/
	private void SubmitForVc() throws FndError {
		// 异常参数
		String ErrorMessage = "";
		// 数据层
		Object AosSourceid = this.getModel().getValue(aos_sourceid);
		// 校验

		// 回写拍照任务清单
		if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
			DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
			aos_mkt_photolist.set("aos_vedstatus", "视频更新");
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
					new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
		}

		/** 销售助理取值逻辑：按照拍照需求表 视频地址 中出现的 国别，和流程中SKU的大类+中类，在营销审批流-国别品类人员表中找到销售助理 **/
		Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键即id
		DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(ReqFId, "aos_mkt_photoreq");
		Object AosItemId = this.getModel().getValue(aos_itemid);
		DynamicObject AosItemidObject = (DynamicObject) AosItemId;
		Object fid = AosItemidObject.getPkValue();
		// 产品类别
		String category = (String) SalUtil.getCategoryByItemId(fid + "").get("name");
		String[] category_group = category.split(",");
		String AosCategory1 = null;
		String AosCategory2 = null;
		int category_length = category_group.length;
		if (category_length > 0)
			AosCategory1 = category_group[0];
		if (category_length > 1)
			AosCategory2 = category_group[1];

//		Set<String> user_set = new HashSet<>();
		HashMap<String, String> userMap = new HashMap<>();

		// 视频地址单据体
		DynamicObjectCollection dy_spentry = aos_mkt_photoreq.getDynamicObjectCollection("aos_entryentity4");
		for (DynamicObject dy : dy_spentry) {
			DynamicObject bd_country = QueryServiceHelper.queryOne("bd_country", "id,name,number",
					new QFilter[] { new QFilter("number", "=", dy.getString("aos_orgshort")) });
			// 根据大类中类获取对应销售助理
			if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
				QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
				QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
				QFilter filter_org = new QFilter("aos_orgid", "=", bd_country.get("id"));
				QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2, filter_org };
				String SelectStr = "aos_salehelper.number aos_salehelper";
				DynamicObjectCollection aos_mkt_progorguser = QueryServiceHelper.query("aos_mkt_progorguser", SelectStr,
						filters_category);
				if (aos_mkt_progorguser != null) {
					for (DynamicObject user : aos_mkt_progorguser) {
						userMap.put(user.getString("aos_salehelper"), bd_country.getString("id"));
					}
				} else {
					throw new FndError(bd_country.getString("number") + " " + AosCategory1 + " " + AosCategory2
							+ " 未查询出销售助理，请确认销售助理是否存在");
				}
			}
		}

		// 视频更新需要根据推送人员 拆分为多个单据 为子流程
//				for (int i = 0; i < 1; i++) {
//					SplitSon(null);
//				}

		if (FndGlobal.IsNotNull(userMap) && userMap.size() >= 1) {
			for (String userMapKey : userMap.keySet()) {
				SplitSon(userMapKey, userMap);
			}
		} else {
			throw new FndError("未查询出销售助理，请确认销售助理是否存在");
		}

		// 源单状态调整
		if (FndGlobal.IsNotNull(userMap) && userMap.size() >= 1)
			this.getView().setVisible(false, "aos_submit");
		this.getModel().setValue(aos_status, "视频更新");// 设置单据流程状态
		this.getModel().setValue(aos_user, system);// 设置单据节点为申请人
		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");
	}

	/**
	 * 视频更新拆分子流程
	 * 
	 * @param userMap
	 **/
	private void SplitSon(String userMapKey, HashMap<String, String> userMap) throws FndError {
		Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键即id
		DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(ReqFId, "aos_mkt_photoreq");
		DynamicObject AosMktPhotoReq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_photoreq");

		String ErrorMessage = "";
		Object aos_itemid = aos_mkt_photoreq.getDynamicObject("aos_itemid").get("id");
		String aos_orgid = userMap.get(userMapKey);
		DynamicObject bd_material = QueryServiceHelper.queryOne("bd_material",
				"aos_contryentry.aos_firstindate aos_firstindate", new QFilter("id", QCP.equals, aos_itemid)
				.and("aos_contryentry.aos_nationality", QCP.equals, aos_orgid).toArray());
		Date aos_firstindate = bd_material.getDate("aos_firstindate");
		
		if (FndGlobal.IsNull(aos_firstindate)) 
			AosMktPhotoReq.set("aos_user", system);
		 else 
			AosMktPhotoReq.set("aos_user", userMapKey);
		AosMktPhotoReq.set("aos_salehelper", userMapKey);
		
		// 国别
		AosMktPhotoReq.set("aos_orgid", aos_orgid);
		// 国别首次入库日期
		AosMktPhotoReq.set("aos_firstindate", aos_firstindate );

		AosMktPhotoReq.set("billstatus", "A");
		AosMktPhotoReq.set("aos_requireby", aos_mkt_photoreq.get("aos_requireby"));
		AosMktPhotoReq.set("aos_requiredate", aos_mkt_photoreq.get("aos_requiredate"));
		AosMktPhotoReq.set("aos_shipdate", aos_mkt_photoreq.getDate("aos_shipdate"));
		AosMktPhotoReq.set(aos_urgent, aos_mkt_photoreq.get(aos_urgent));
		AosMktPhotoReq.set("aos_photoflag", aos_mkt_photoreq.get("aos_photoflag"));
		AosMktPhotoReq.set("aos_reason", aos_mkt_photoreq.get("aos_reason"));
		AosMktPhotoReq.set("aos_sameitemid", aos_mkt_photoreq.get("aos_sameitemid"));
		AosMktPhotoReq.set("aos_vedioflag", aos_mkt_photoreq.get("aos_vedioflag"));
		AosMktPhotoReq.set("aos_reqtype", aos_mkt_photoreq.get("aos_reqtype"));
		AosMktPhotoReq.set("aos_sourceid", aos_mkt_photoreq.get("aos_sourceid"));
		AosMktPhotoReq.set("aos_itemid", aos_mkt_photoreq.get("aos_itemid"));
		AosMktPhotoReq.set("aos_is_saleout", Is_saleout(aos_itemid));
		AosMktPhotoReq.set("aos_itemname", aos_mkt_photoreq.get("aos_itemname"));
		AosMktPhotoReq.set("aos_contrybrand", aos_mkt_photoreq.get("aos_contrybrand"));
		AosMktPhotoReq.set("aos_newitem", aos_mkt_photoreq.get("aos_newitem"));
		AosMktPhotoReq.set("aos_newvendor", aos_mkt_photoreq.get("aos_newvendor"));
		AosMktPhotoReq.set("aos_ponumber", aos_mkt_photoreq.get("aos_ponumber"));
		AosMktPhotoReq.set("aos_linenumber", aos_mkt_photoreq.get("aos_linenumber"));
		AosMktPhotoReq.set("aos_earlydate", aos_mkt_photoreq.get("aos_earlydate"));
		AosMktPhotoReq.set("aos_checkdate", aos_mkt_photoreq.get("aos_checkdate"));
		AosMktPhotoReq.set("aos_specification", aos_mkt_photoreq.get("aos_specification"));
		AosMktPhotoReq.set("aos_seting1", aos_mkt_photoreq.get("aos_seting1"));
		AosMktPhotoReq.set("aos_seting2", aos_mkt_photoreq.get("aos_seting2"));
		AosMktPhotoReq.set("aos_sellingpoint", aos_mkt_photoreq.get("aos_sellingpoint"));
		AosMktPhotoReq.set("aos_vendor", aos_mkt_photoreq.get("aos_vendor"));
		AosMktPhotoReq.set("aos_city", aos_mkt_photoreq.get("aos_city"));
		AosMktPhotoReq.set("aos_contact", aos_mkt_photoreq.get("aos_contact"));
		AosMktPhotoReq.set("aos_address", aos_mkt_photoreq.get("aos_address"));
		AosMktPhotoReq.set("aos_phone", aos_mkt_photoreq.get("aos_phone"));
		AosMktPhotoReq.set("aos_phstate", aos_mkt_photoreq.get("aos_phstate"));
		AosMktPhotoReq.set("aos_rcvbill", aos_mkt_photoreq.get("aos_rcvbill"));
		AosMktPhotoReq.set("aos_sampledate", aos_mkt_photoreq.get("aos_sampledate"));
		AosMktPhotoReq.set("aos_installdate", aos_mkt_photoreq.get("aos_installdate"));
		AosMktPhotoReq.set("aos_poer", aos_mkt_photoreq.get("aos_poer"));
		AosMktPhotoReq.set("aos_developer", aos_mkt_photoreq.get("aos_developer"));
		AosMktPhotoReq.set("aos_follower", aos_mkt_photoreq.get("aos_follower"));
		AosMktPhotoReq.set("aos_whiteph", aos_mkt_photoreq.get("aos_whiteph"));
		AosMktPhotoReq.set("aos_actph", aos_mkt_photoreq.get("aos_actph"));
		AosMktPhotoReq.set("aos_vedior", aos_mkt_photoreq.get("aos_vedior"));
		AosMktPhotoReq.set("aos_3d", aos_mkt_photoreq.get("aos_3d"));
		AosMktPhotoReq.set("aos_whitedate", aos_mkt_photoreq.get("aos_whitedate"));
		AosMktPhotoReq.set("aos_actdate", aos_mkt_photoreq.get("aos_actdate"));
		AosMktPhotoReq.set("aos_picdate", aos_mkt_photoreq.get("aos_picdate"));
		AosMktPhotoReq.set("aos_funcpicdate", aos_mkt_photoreq.get("aos_funcpicdate"));
		AosMktPhotoReq.set("aos_vedio", aos_mkt_photoreq.get("aos_vedio"));
		AosMktPhotoReq.set("billno", aos_mkt_photoreq.get("billno"));
		// AosMktPhotoReq.set("aos_user", aos_mkt_photoreq.get("aos_vedior"));
		AosMktPhotoReq.set("aos_type", "视频");
		AosMktPhotoReq.set("aos_designer", aos_mkt_photoreq.get("aos_designer"));
		AosMktPhotoReq.set("aos_sale", aos_mkt_photoreq.get("aos_sale"));
		AosMktPhotoReq.set("aos_status", "视频更新:多人会审");

		AosMktPhotoReq.set("aos_desc", aos_mkt_photoreq.get("aos_desc"));

		AosMktPhotoReq.set("aos_sonflag", true);
		AosMktPhotoReq.set("aos_parentid", ReqFId);
		AosMktPhotoReq.set("aos_parentbill", aos_mkt_photoreq.get("billno"));
		AosMktPhotoReq.set("aos_vediotype", aos_mkt_photoreq.get("aos_vediotype"));

		AosMktPhotoReq.set("aos_organization1", aos_mkt_photoreq.get("aos_organization1"));
		AosMktPhotoReq.set("aos_organization2", aos_mkt_photoreq.get("aos_organization2"));
		AosMktPhotoReq.set("aos_vediotype", aos_mkt_photoreq.get("aos_vediotype"));
		AosMktPhotoReq.set("aos_orgtext", aos_mkt_photoreq.get("aos_orgtext"));
		AosMktPhotoReq.set("aos_samplestatus", aos_mkt_photoreq.get("aos_samplestatus"));

		// 新增质检完成日期
		QFilter qFilter_contra = new QFilter("aos_insrecordentity.aos_insresultchk", "=", "A");
		QFilter qFilter_lineno = new QFilter("aos_insrecordentity.aos_instypedetailchk", "=", "1");
		// 优化 过滤条件 增加合同号 行号
		QFilter qFilter_ponumber = new QFilter("aos_insrecordentity.aos_contractnochk", "=",
				aos_mkt_photoreq.get("aos_ponumber"));
		QFilter qFilter_linenumber = new QFilter("aos_insrecordentity.aos_lineno", "=",
				aos_mkt_photoreq.get("aos_linenumber"));
		QFilter[] qFilters = { qFilter_contra, qFilter_lineno, qFilter_ponumber, qFilter_linenumber };
		DynamicObject dy_date = QueryServiceHelper.queryOne("aos_qctasklist", "aos_quainscomdate", qFilters);
		if (dy_date != null) {
			AosMktPhotoReq.set("aos_quainscomdate", dy_date.get("aos_quainscomdate"));
		}

		// 照片需求单据体(新)
		DynamicObjectCollection aos_entryentity5S = AosMktPhotoReq.getDynamicObjectCollection("aos_entryentity5");
		DynamicObjectCollection aos_entryentity5OriS = aos_mkt_photoreq.getDynamicObjectCollection("aos_entryentity5");
		for (DynamicObject aos_entryentity5Ori : aos_entryentity5OriS) {
			DynamicObject aos_entryentity5 = aos_entryentity5S.addNew();
			aos_entryentity5.set("aos_reqfirst", aos_entryentity5Ori.get("aos_reqfirst"));
			aos_entryentity5.set("aos_reqother", aos_entryentity5Ori.get("aos_reqother"));
			aos_entryentity5.set("aos_detail", aos_entryentity5Ori.get("aos_detail"));
			aos_entryentity5.set("aos_scene1", aos_entryentity5Ori.get("aos_scene1"));
			aos_entryentity5.set("aos_object1", aos_entryentity5Ori.get("aos_object1"));
			aos_entryentity5.set("aos_scene2", aos_entryentity5Ori.get("aos_scene2"));
			aos_entryentity5.set("aos_object2", aos_entryentity5Ori.get("aos_object2"));
		}
		// 照片需求单据体(新2)
		DynamicObjectCollection aos_entryentity6S = AosMktPhotoReq.getDynamicObjectCollection("aos_entryentity6");
		DynamicObjectCollection aos_entryentity6OriS = aos_mkt_photoreq.getDynamicObjectCollection("aos_entryentity6");
		for (DynamicObject aos_entryentity6Ori : aos_entryentity6OriS) {
			DynamicObject aos_entryentity6 = aos_entryentity6S.addNew();
			aos_entryentity6.set("aos_reqsupp", aos_entryentity6Ori.get("aos_reqsupp"));
			aos_entryentity6.set("aos_devsupp", aos_entryentity6Ori.get("aos_devsupp"));
		}

		// 照片需求单据体
		DynamicObjectCollection aos_entryentityS = AosMktPhotoReq.getDynamicObjectCollection("aos_entryentity");
		DynamicObjectCollection aos_entryentityOriS = aos_mkt_photoreq.getDynamicObjectCollection("aos_entryentity");
		for (DynamicObject aos_entryentityOri : aos_entryentityOriS) {
			DynamicObject aos_entryentity = aos_entryentityS.addNew();
			aos_entryentity.set("aos_applyby", aos_entryentityOri.get("aos_applyby"));
			aos_entryentity.set("aos_picdesc", aos_entryentityOri.get("aos_picdesc"));
		}

		// 视频需求单据体
		DynamicObjectCollection aos_entryentity1S = AosMktPhotoReq.getDynamicObjectCollection("aos_entryentity1");
		DynamicObjectCollection aos_entryentityOri1S = aos_mkt_photoreq.getDynamicObjectCollection("aos_entryentity1");
		for (DynamicObject aos_entryentityOri1 : aos_entryentityOri1S) {
			DynamicObject aos_entryentity1 = aos_entryentity1S.addNew();
			aos_entryentity1.set("aos_applyby2", aos_entryentityOri1.get("aos_applyby2"));
			aos_entryentity1.set("aos_veddesc", aos_entryentityOri1.get("aos_veddesc"));
		}

		// 拍摄情况单据体
		DynamicObjectCollection aos_entryentity2S = AosMktPhotoReq.getDynamicObjectCollection("aos_entryentity2");
		DynamicObjectCollection aos_entryentityOri2S = aos_mkt_photoreq.getDynamicObjectCollection("aos_entryentity2");
		for (DynamicObject aos_entryentityOri2 : aos_entryentityOri2S) {
			DynamicObject aos_entryentity2 = aos_entryentity2S.addNew();
			aos_entryentity2.set("aos_phtype", aos_entryentityOri2.get("aos_phtype"));
			aos_entryentity2.set("aos_complete", aos_entryentityOri2.get("aos_complete"));
			aos_entryentity2.set("aos_completeqty", aos_entryentityOri2.get("aos_completeqty"));
		}

		// 流程退回原因单据体
		DynamicObjectCollection aos_entryentity3S = AosMktPhotoReq.getDynamicObjectCollection("aos_entryentity3");
		DynamicObjectCollection aos_entryentityOri3S = aos_mkt_photoreq.getDynamicObjectCollection("aos_entryentity3");
		for (DynamicObject aos_entryentityOri3 : aos_entryentityOri3S) {
			DynamicObject aos_entryentity3 = aos_entryentity3S.addNew();
			aos_entryentity3.set("aos_returnby", aos_entryentityOri3.get("aos_returnby"));
			aos_entryentity3.set("aos_return", aos_entryentityOri3.get("aos_return"));
			aos_entryentity3.set("aos_returnreason", aos_entryentityOri3.get("aos_returnreason"));
		}

		AosMktPhotoReq.set("aos_phreturn", aos_mkt_photoreq.get("aos_phreturn"));
		AosMktPhotoReq.set("aos_phreason", aos_mkt_photoreq.get("aos_phreason"));
		AosMktPhotoReq.set("aos_dereturn", aos_mkt_photoreq.get("aos_dereturn"));
		AosMktPhotoReq.set("aos_dereason", aos_mkt_photoreq.get("aos_dereason"));

		// 视频地址单据体
		DynamicObjectCollection aos_entryentity4S = AosMktPhotoReq.getDynamicObjectCollection("aos_entryentity4");
		DynamicObjectCollection aos_entryentityOri4S = aos_mkt_photoreq.getDynamicObjectCollection("aos_entryentity4");
		for (DynamicObject aos_entryentityOri4 : aos_entryentityOri4S) {
			DynamicObject aos_entryentity4 = aos_entryentity4S.addNew();
			aos_entryentity4.set("aos_orgshort", aos_entryentityOri4.get("aos_orgshort"));
			aos_entryentity4.set("aos_brand", aos_entryentityOri4.get("aos_brand"));
			aos_entryentity4.set("aos_s3address1", aos_entryentityOri4.get("aos_s3address1"));
			aos_entryentity4.set("aos_s3address2", aos_entryentityOri4.get("aos_s3address2"));
		}

		OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq", new DynamicObject[] { AosMktPhotoReq },
				OperateOption.create());

	}

	/** 白底退回 **/
	private void BackForWhite() throws FndError {
		// 异常参数
		String ErrorMessage = "";
		// 数据层
		Object AosSourceid = this.getModel().getValue(aos_sourceid);
		Object AosBillno = this.getModel().getValue(billno);
		Object AosFollower = this.getModel().getValue(aos_follower);
		Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键

		// 校验
		Object aosPhReason = this.getModel().getValue("aos_phreason");
		if (FndGlobal.IsNull(aosPhReason)) {
			throw new FndError("摄影师退回原因必填！");
		}

		// 回写拍照任务清单
		if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
			DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
			aos_mkt_photolist.set("aos_vedstatus", "跟单提样");
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
					new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
		}

		// 退回时生成新的样品入库单
		GenerateRcv(this.getModel().getDataEntity(true));

		// 执行保存操作
		Object aos_return = 0;
		aos_return = this.getModel().getValue("aos_return", 0);
		if (aos_return == null)
			aos_return = 1;
		else
			aos_return = (int) aos_return + 1;
		this.getModel().setValue("aos_return", aos_return, 0);// 退回次数

		// 摄影师退回
		Object aos_phreturn = 0;
		aos_phreturn = this.getModel().getValue("aos_phreturn");
		if (aos_phreturn == null)
			aos_phreturn = 1;
		else
			aos_phreturn = (int) aos_phreturn + 1;
		this.getModel().setValue("aos_phreturn", aos_phreturn);

		this.getModel().setValue(aos_status, "跟单提样");// 设置单据流程状态
		String MessageId = null;
		this.getModel().setValue(aos_user, AosFollower);// 流转给跟单
		// 退回时去除备样安装完成日期
		this.getModel().setValue("aos_sampledate", null);
		this.getModel().setValue("aos_installdate", null);
		MessageId = ((DynamicObject) AosFollower).getPkValue().toString();
		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");
		// 发送消息
		MKTCom.SendGlobalMessage(MessageId, aos_mkt_photoreq, ReqFId + "", AosBillno + "", "拍照需求表-白底退回");
	}

	/** 开发退回 **/
	private void BackForConfirm() throws FndError {
		// 异常参数
		String ErrorMessage = "";
		// 数据层
		Object AosSourceid = this.getModel().getValue(aos_sourceid);
		Object AosBillno = this.getModel().getValue(billno);
		Object AosActph = this.getModel().getValue(aos_actph);
		Object aos_3d = this.getModel().getValue("aos_3d");
		Object AosVedior = this.getModel().getValue(aos_vedior);
		Object AosType = this.getModel().getValue(aos_type).toString();
		Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键
		Object aos_3dflag = this.getModel().getValue("aos_3dflag");
		Boolean Is3DFlag = false;
		Object aosDeReason = this.getModel().getValue("aos_dereason");

		if (FndGlobal.IsNull(aosDeReason)) {
			throw new FndError("开发退回原因必填！");
		}

		// 校验
		if ((boolean) aos_3dflag)
			Is3DFlag = true;
		if (Is3DFlag) {
			QFilter filter_id = new QFilter("aos_orignbill", QCP.equals, AosBillno);
			QFilter[] filters_3d = new QFilter[] { filter_id };
			DynamicObject aos_mkt_3design = QueryServiceHelper.queryOne("aos_mkt_3design", "id", filters_3d);
			if (FndGlobal.IsNotNull(aos_mkt_3design)) {
				Object id = aos_mkt_3design.get("id");
				aos_mkt_3design = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_3design");
				aos_mkt_3design.set("aos_returnflag", true);
				aos_mkt_3design.set("aos_user", aos_3d);
				aos_mkt_3design.set("aos_status", "新建");
				aos_mkt_3design.set("aos_returnreason", this.getModel().getValue("aos_dereason"));
				OperationServiceHelper.executeOperate("save", "aos_mkt_3design",
						new DynamicObject[] { aos_mkt_3design }, OperateOption.create());
			}
		}

		// 回写拍照任务清单
		if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
			DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
			if ("拍照".equals(AosType) && !Is3DFlag)
				aos_mkt_photolist.set("aos_phstatus", "实景拍摄");
			else if ("拍照".equals(AosType) && Is3DFlag)
				aos_mkt_photolist.set("aos_phstatus", "3D建模");
			else if ("视频".equals(AosType))
				aos_mkt_photolist.set("aos_vedstatus", "视频剪辑");

			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
					new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
		}

		// 执行保存操作
		Object aos_return = 0;
		Object aos_dereturn = 0;

		String MessageId = null;
		aos_return = this.getModel().getValue("aos_return", 1);
		if (aos_return == null)
			aos_return = 1;
		else
			aos_return = (int) aos_return + 1;
		this.getModel().setValue("aos_return", aos_return, 1);// 退回次数
		// 开发退回
		aos_dereturn = this.getModel().getValue("aos_dereturn", 1);
		if (aos_dereturn == null)
			aos_dereturn = 1;
		else
			aos_dereturn = (int) aos_dereturn + 1;
		this.getModel().setValue("aos_dereturn", aos_dereturn, 1);// 退回次数

		if ("拍照".equals(AosType) && !Is3DFlag) {
			this.getModel().setValue("aos_actdate", "");
			this.getModel().setValue(aos_status, "实景拍摄");// 设置单据流程状态
			this.getModel().setValue(aos_user, AosActph);// 流转给实景摄影师
			MessageId = ((DynamicObject) AosActph).getPkValue().toString();
		} else if ("拍照".equals(AosType) && Is3DFlag) {
			this.getModel().setValue(aos_status, "3D建模");// 设置单据流程状态
			this.getModel().setValue(aos_user, aos_3d);// 流转给3D设计师
			MessageId = ((DynamicObject) aos_3d).getPkValue().toString();
		} else if ("视频".equals(AosType)) {
			this.getModel().setValue(aos_status, "视频剪辑");// 设置单据流程状态
			this.getModel().setValue(aos_user, AosVedior);// 流转给摄像师
			MessageId = ((DynamicObject) AosVedior).getPkValue().toString();
		}

		// 退回时 删除对应抠图任务清单 数据
		QFilter filter_id = new QFilter("aos_sourceid", "=", ReqFId);
		QFilter[] filters_ps = new QFilter[] { filter_id };
		DeleteServiceHelper.delete("aos_mkt_pslist", filters_ps);

		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");
		// 发送消息
		MKTCom.SendGlobalMessage(MessageId, aos_mkt_photoreq, ReqFId + "", AosBillno + "", "拍照需求表-开发退回");
	}

	/**
	 * 拍照类型新品结束后 生成设计需求表
	 * 
	 * @param dyn
	 **/
	public static void GenerateDesign(DynamicObject dyn) {
		// 数据层
		Object AosShipDate = dyn.get(aos_shipdate);
		Object aos_billno = dyn.get(billno);

		// 判断是否已经生成了设计需求表
		QFBuilder builder = new QFBuilder("aos_orignbill", "=", aos_billno);
		DynamicObject exists = QueryServiceHelper.queryOne("aos_mkt_designreq", "id", builder.toArray());
		if (FndGlobal.IsNotNull(exists))
			return;

		Object ReqFId = dyn.getPkValue(); // 当前界面主键
		Object AosItemId = dyn.get(aos_itemid);
		Object aos_requireby = dyn.get("aos_requireby");
		Object AosDesigner = dyn.get(aos_designer);
		DynamicObject AosDeveloper = dyn.getDynamicObject(aos_developer);
		Object AosDeveloperId = AosDeveloper.getPkValue();
		DynamicObject AosItemidObject = (DynamicObject) AosItemId;
		Object fid = AosItemidObject.getPkValue();
		String MessageId = AosDeveloperId + "";
		Object aos_requirebyid = ((DynamicObject) aos_requireby).getPkValue();

		Boolean aos_3dflag = dyn.getBoolean("aos_3dflag");
		String aos_phstate = dyn.getString("aos_phstate");

		// 判断对应抠图任务清单是否为已完成
		if (!aos_3dflag && !aos_phstate.equals("工厂简拍")) {
			QFilter filter_status = new QFilter("aos_status", QCP.equals, "已完成");
			QFilter filter_id = new QFilter("aos_sourceid", "=", ReqFId);
			QFilter[] filters_ps = new QFilter[] { filter_status, filter_id };
			DynamicObject aos_mkt_pslist = QueryServiceHelper.queryOne("aos_mkt_pslist", "id", filters_ps);
			// 如果不为已完成 则不生成设计需求表 等待抠图任务清单完成后再生成设计需求表
			if (aos_mkt_pslist == null)
				return;
		}

		// 初始化
		DynamicObject aos_mkt_designreq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designreq");
		aos_mkt_designreq.set("aos_requiredate", new Date());

		String category = (String) SalUtil.getCategoryByItemId(fid + "").get("name");
		String[] category_group = category.split(",");
		String AosCategory1 = null;
		String AosCategory2 = null;
		String AosCategory3 = null;
		int category_length = category_group.length;
		if (category_length > 0)
			AosCategory1 = category_group[0];
		if (category_length > 1)
			AosCategory2 = category_group[1];
		if (category_length > 2)
			AosCategory3 = category_group[2];

		if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
			QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
			QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
			QFilter[] filters = new QFilter[] { filter_category1, filter_category2 };
			String SelectStr = "aos_designer,aos_3d,aos_designeror";
			DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_proguser", SelectStr, filters);
			if (aos_mkt_proguser != null) {
				aos_mkt_designreq.set("aos_user", aos_mkt_proguser.get(aos_designer));
				aos_mkt_designreq.set("aos_designer", aos_mkt_proguser.get(aos_designer));
				aos_mkt_designreq.set("aos_dm", aos_mkt_proguser.get("aos_designeror"));
				aos_mkt_designreq.set("aos_3der", aos_mkt_proguser.get("aos_3d"));
			}
		}

		// 是否新品
		boolean aos_newitem = dyn.getBoolean("aos_newitem");
		if (aos_newitem)
			aos_mkt_designreq.set("aos_type", "新品设计");
		else
			aos_mkt_designreq.set("aos_type", "老品重拍");
		aos_mkt_designreq.set("aos_shipdate", AosShipDate);
		aos_mkt_designreq.set("aos_orignbill", aos_billno);
		aos_mkt_designreq.set("aos_sourceid", ReqFId);

		// TODO 状态先默认为 拍照功能图制作
		aos_mkt_designreq.set("aos_status", "拍照功能图制作");

		aos_mkt_designreq.set("aos_requireby", aos_requireby);
		aos_mkt_designreq.set("aos_sourcetype", "PHOTO");

		// BOTP
		aos_mkt_designreq.set("aos_sourcebilltype", aos_mkt_photoreq);
		aos_mkt_designreq.set("aos_sourcebillno", aos_billno);
		aos_mkt_designreq.set("aos_srcentrykey", "aos_entryentity");

		mkt.progress.design.aos_mkt_designreq_bill.setEntityValue(aos_mkt_designreq);

		List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(aos_requirebyid);
		if (MapList != null) {
			if (MapList.get(2) != null)
				aos_mkt_designreq.set("aos_organization1", MapList.get(2).get("id"));
			if (MapList.get(3) != null)
				aos_mkt_designreq.set("aos_organization2", MapList.get(3).get("id"));
		}

		DynamicObjectCollection aos_entryentityS = aos_mkt_designreq.getDynamicObjectCollection("aos_entryentity");
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
			if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
				continue;
			aos_orgtext = aos_orgtext + aos_nationalitynumber + ";";

			Object obj = aos_contryentry.getDynamicObject("aos_contrybrand");
			if (obj == null)
				continue;
			String value = aos_contryentry.getDynamicObject("aos_contrybrand").getString("number");
//			if (!aos_contrybrandStr.contains(value))
//				aos_contrybrandStr = aos_contrybrandStr + value + ";";

			if (value != null)
				set_bra.add(value);
			if (set_bra.size() > 1) {
				if (!aos_contrybrandStr.contains(value))
					aos_contrybrandStr = aos_contrybrandStr + value + ";";
			} else if (set_bra.size() == 1) {
				aos_contrybrandStr = value;
			}
		}
		String item_number = bd_material.getString("number");
		String url = "https://clss.s3.amazonaws.com/" + item_number + ".jpg";// 图片字段
		String aos_productno = bd_material.getString("aos_productno");
		String aos_itemname = bd_material.getString("name");
		// 获取同产品号物料
		String aos_broitem = "";
		DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", "id,number,aos_type",
				new QFilter("aos_productno", QCP.equals, aos_productno).and("aos_type", QCP.equals, "A").toArray());
		for (DynamicObject bd : bd_materialS) {
			Object itemid = bd_material.get("id");
			if ("B".equals(bd.getString("aos_type")))
				continue; // 配件不获取
			Boolean exist = QueryServiceHelper.exists("bd_material", new QFilter("id", QCP.equals, itemid)
					.and("aos_contryentry.aos_contryentrystatus", QCP.not_equals, "C"));
			if (!exist)
				continue;// 全球终止不取
			int osQty = aos_mkt_listingreq_bill.getOsQty(itemid);
			if (osQty < 10)
				continue;
			String number = bd.getString("number");
			if (item_number.equals(number))
				continue;
			else
				aos_broitem = aos_broitem + number + ";";
		}

		DynamicObject aos_entryentity = aos_entryentityS.addNew();
		aos_entryentity.set("aos_itemid", AosItemId);
		aos_entryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(fid));
		aos_entryentity.set("aos_srcrowseq", 1);

		DynamicObjectCollection aos_entryentity6S = dyn.getDynamicObjectCollection("aos_entryentity6");
		for (DynamicObject aos_entryentity6 : aos_entryentity6S) {
			aos_entryentity.set("aos_desreq",
					aos_entryentity6.getString("aos_reqsupp") + "/" + aos_entryentity6.getString("aos_devsupp"));
		}

		DynamicObjectCollection aos_subentryentityS = aos_entryentity.getDynamicObjectCollection("aos_subentryentity");

		DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
		aos_subentryentity.set("aos_sub_item", AosItemId);
		aos_subentryentity.set("aos_segment3", aos_productno);
		aos_subentryentity.set("aos_itemname", aos_itemname);
		aos_subentryentity.set("aos_brand", aos_contrybrandStr);
		aos_subentryentity.set("aos_pic", url);
		aos_subentryentity.set("aos_developer", bd_material.get("aos_developer"));// 开发
		aos_subentryentity.set("aos_seting1", bd_material.get("aos_seting_cn"));
		aos_subentryentity.set("aos_seting2", bd_material.get("aos_seting_en"));
		aos_subentryentity.set("aos_spec", bd_material.get("aos_specification_cn"));
		aos_subentryentity.set("aos_url", MKTS3PIC.GetItemPicture(item_number));
		aos_subentryentity.set("aos_broitem", aos_broitem);
		aos_subentryentity.set("aos_orgtext", aos_orgtext);

		aos_mkt_designreq_bill.createDesiginBeforeSave(aos_mkt_designreq);
		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
				new DynamicObject[] { aos_mkt_designreq }, OperateOption.create());
		if (operationrst.isSuccess()) {
			String PK = operationrst.getSuccessPkIds().get(0).toString();
			FndHistory.Create("aos_mkt_designreq", PK, "新建", "新建节点： " + aos_mkt_designreq.getString("aos_status"));
		}

		// 修复关联关系
		try {
			ProgressUtil.botp("aos_mkt_designreq", aos_mkt_designreq.get("id"));
		} catch (Exception ex) {
		}

		if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			MKTCom.SendGlobalMessage(MessageId, "aos_mkt_designreq", operationrst.getSuccessPkIds().get(0) + "",
					aos_mkt_designreq.getString("billno"), "设计需求表-拍照新品自动创建");
			FndHistory.Create(aos_mkt_designreq, aos_mkt_designreq.getString("aos_status"), "设计需求表-拍照新品自动创建");
		}

	}

	/** 查看入库单 **/
	private void aos_showrcv() throws FndError {
		// 数据层
		int ErrorCount = 0;
		String ErrorMessage = "";
		Object aos_rcvbill = this.getModel().getValue("aos_rcvbill");

		if (Cux_Common_Utl.IsNull(aos_rcvbill)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "入库单号不存在!");
		}

		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		QFilter filter_bill = new QFilter("billno", "=", aos_rcvbill);
		QFilter[] filters = new QFilter[] { filter_bill };
		DynamicObject aos_mkt_photoreq = QueryServiceHelper.queryOne("aos_mkt_rcv", "id", filters);
		if (Cux_Common_Utl.IsNull(aos_mkt_photoreq)) {
			FndError fndMessage = new FndError("未查询到入库单!");
			throw fndMessage;
		}
		Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_rcv", aos_mkt_photoreq.get("id"));
	}

	/** 开发/采购确认 创建跟单样品入库通知单 **/
	public static void GenerateRcv(DynamicObject dy_main) throws FndError {
		// 数据层
		Object aos_billno = dy_main.get(billno);
		Object ReqFId = dy_main.getPkValue(); // 当前界面主键
		Object AosItemId = dy_main.get(aos_itemid);
		DynamicObject AosFollower = dy_main.getDynamicObject(aos_follower);
		Object AosFollowerId = AosFollower.getPkValue();
		Object aos_vendor = dy_main.get("aos_vendor");
		DynamicObject AosItemidObject = (DynamicObject) AosItemId;
		Object fid = AosItemidObject.getPkValue();
		String MessageId = AosFollowerId + "";
		Object aos_phstate = dy_main.get("aos_phstate");
		DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(fid, "bd_material", "aos_boxrate");
		DynamicObject aos_mkt_rcv = BusinessDataServiceHelper.newDynamicObject("aos_mkt_rcv");

		// 校验
		try {
			List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(AosFollowerId);
			if (MapList != null) {
				if (MapList.get(2) != null)
					aos_mkt_rcv.set("aos_organization1", MapList.get(2).get("id"));
				if (MapList.get(3) != null)
					aos_mkt_rcv.set("aos_organization2", MapList.get(3).get("id"));
			}
		} catch (Exception ex) {
			FndError fndMessage = new FndError("跟单行政组织级别有误!");
			throw fndMessage;
		}
		// 校验生成的单据的唯一性
		JudgeRcvRepeat(dy_main);

		// 初始化
		aos_mkt_rcv.set("aos_user", AosFollowerId);
		aos_mkt_rcv.set("aos_status", "新建");
		aos_mkt_rcv.set("aos_vendor", aos_vendor);
		aos_mkt_rcv.set("aos_ponumber", dy_main.get("aos_ponumber"));
		aos_mkt_rcv.set("aos_lineno", dy_main.get("aos_linenumber"));// 行号
		aos_mkt_rcv.set("aos_itemid", AosItemId);
		aos_mkt_rcv.set("aos_itemname", dy_main.get("aos_itemname"));
		aos_mkt_rcv.set("aos_boxqty", bd_material.get("aos_boxrate"));
		aos_mkt_rcv.set("aos_photoflag", true);
		aos_mkt_rcv.set("aos_phstate", aos_phstate);
		aos_mkt_rcv.set("aos_photo", dy_main.get(aos_photoflag));
		aos_mkt_rcv.set("aos_vedio", dy_main.get(aos_vedioflag));
		aos_mkt_rcv.set("aos_reason", dy_main.get(aos_reason));
		aos_mkt_rcv.set("aos_sameitemid", dy_main.get(aos_sameitemid));
		aos_mkt_rcv.set("aos_developer", dy_main.get(aos_developer));
		aos_mkt_rcv.set("aos_earlydate", dy_main.get("aos_earlydate"));
		aos_mkt_rcv.set("aos_shipdate", dy_main.get(aos_shipdate));
		aos_mkt_rcv.set("aos_requireby", AosFollowerId);
		aos_mkt_rcv.set("aos_requiredate", new Date());
		aos_mkt_rcv.set("aos_orignbill", aos_billno);
		aos_mkt_rcv.set("aos_sourceid", ReqFId);
		/*
		 * aos_mkt_rcv.set("aos_returnreason",
		 * dy_main.getDynamicObjectCollection("aos_entryentity3").get(0).get(
		 * "aos_returnreason"));
		 */
		aos_mkt_rcv.set("aos_returnreason", dy_main.get("aos_phreason"));

		// BOTP
		aos_mkt_rcv.set("aos_sourcebilltype", aos_mkt_photoreq);
		aos_mkt_rcv.set("aos_sourcebillno", dy_main.get("billno"));
		aos_mkt_rcv.set("aos_srcentrykey", "aos_entryentity");

		// 新增质检完成日期、紧急程度字段
		QFilter qFilter_contra = new QFilter("aos_insrecordentity.aos_insresultchk", "=", "A");
		QFilter qFilter_lineno = new QFilter("aos_insrecordentity.aos_instypedetailchk", "=", "1");
		// 优化 过滤条件 增加合同号 行号
		QFilter qFilter_ponumber = new QFilter("aos_insrecordentity.aos_contractnochk", "=",
				dy_main.get("aos_ponumber"));
		QFilter qFilter_linenumber = new QFilter("aos_insrecordentity.aos_lineno", "=", dy_main.get("aos_linenumber"));
		QFilter[] qFilters = { qFilter_contra, qFilter_lineno, qFilter_ponumber, qFilter_linenumber };
		DynamicObject dy_date = QueryServiceHelper.queryOne("aos_qctasklist", "aos_quainscomdate", qFilters);
		if (dy_date != null) {
			aos_mkt_rcv.set("aos_quainscomdate", dy_date.get("aos_quainscomdate"));
			// 当前日期-10(质检完成日期)，紧急程度刷新成紧急
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			Date dt = new Date();
			Calendar rightNow = Calendar.getInstance();
			rightNow.setTime(dt);
			rightNow.add(Calendar.DAY_OF_YEAR, -10);// 日期减10天
			Date dt1 = rightNow.getTime();
			String reStr1 = sdf.format(dt1);
			String reStr2 = sdf.format(dy_date.get("aos_quainscomdate"));
			if (reStr2.compareTo(reStr1) >= 0)
				aos_mkt_rcv.set("aos_importance", "紧急");
		}

		DynamicObjectCollection aos_entryentityS = aos_mkt_rcv.getDynamicObjectCollection("aos_entryentity");
		DynamicObject aos_entryentity = aos_entryentityS.addNew();
		aos_entryentity.set("aos_srcrowseq", 1);

		if ("来样拍照".equals(aos_phstate) || "外包拍照".equals(aos_phstate)) {
			aos_mkt_rcv.set("aos_protype", "退回");
			QFilter filter_vendor = new QFilter("aos_vendor", "=", aos_vendor);
			QFilter filter_type = new QFilter("aos_protype", "=", "退回");
			QFilter[] filters = { filter_vendor, filter_type };
			DynamicObjectCollection aos_mkt_rcv_SameS = QueryServiceHelper.query("aos_mkt_rcv",
					"aos_contact,aos_contactway,aos_returnadd", filters, "createtime desc");
			for (DynamicObject aos_mkt_rcv_Same : aos_mkt_rcv_SameS) {
				aos_mkt_rcv.set("aos_contact", aos_mkt_rcv_Same.get("aos_contact"));
				aos_mkt_rcv.set("aos_contactway", aos_mkt_rcv_Same.get("aos_contactway"));
				aos_mkt_rcv.set("aos_returnadd", aos_mkt_rcv_Same.get("aos_returnadd"));
				break;
			}

		}
		// 给 拍照地点赋值
		if ("来样拍照".equals(aos_phstate)) {
			// 取拍照地点
			QFilter filter_status = new QFilter("aos_address", "=", aos_phstate);
			QFilter filter_type = new QFilter("aos_entryentity.aos_valid", "=", true);
			QFilter[] filters = { filter_status, filter_type };
			DynamicObjectCollection aos_mkt_sampleaddress = QueryServiceHelper.query("aos_mkt_sampleaddress",
					"aos_entryentity.aos_content aos_content", filters);
			if (aos_mkt_sampleaddress.size() > 0) {
				DynamicObject dy = aos_mkt_sampleaddress.get(0);
				aos_mkt_rcv.set("aos_address", dy.getString("aos_content"));
			}
		}

		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_rcv",
				new DynamicObject[] { aos_mkt_rcv }, OperateOption.create());

		// 修复关联关系
		try {
			ProgressUtil.botp("aos_mkt_rcv", aos_mkt_rcv.get("id"));
		} catch (Exception ex) {
			FndError.send(SalUtil.getExceptionStr(ex), FndWebHook.urlMms);
		}

		if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			MKTCom.SendGlobalMessage(MessageId, "aos_mkt_rcv", operationrst.getSuccessPkIds().get(0) + "",
					aos_mkt_rcv.getString("billno"), "样品入库通知单-拍照流程自动创建");
		}

		dy_main.set("aos_rcvbill", aos_mkt_rcv.getString("billno"));
		dy_main.set("aos_samplestatus", "新建");
	}

	/** 生成拍照需求表校验唯一性 **/
	private static void JudgeRcvRepeat(DynamicObject dy_main) {
		Object bill = dy_main.get(billno);
		DynamicObject aos_itemid = dy_main.getDynamicObject("aos_itemid");
		if (aos_itemid == null)
			return;
		Object item = aos_itemid.getPkValue();
		QFilter filter_billno = new QFilter("aos_orignbill", "=", bill);
		QFilter filter_item = new QFilter("aos_itemid", "=", item);
		DeleteServiceHelper.delete("aos_mkt_rcv", new QFilter[] { filter_billno, filter_item });
	}

	/**
	 * 判断是否拍摄视频
	 * 
	 * @param cate1    大类
	 * @param cate2    中类
	 * @param cate3    小类
	 * @param itemName 品名
	 */
	public boolean JudgeTakeVideo(String cate1, String cate2, String cate3, String itemName) {
		QFilter filter_majorCate = new QFilter("aos_cate1", "=", cate1);
		QFilter filter_middleCate = new QFilter("aos_cate2", "=", cate2);
		QFilter filter_subCate = new QFilter("aos_cate3", "=", cate3);
		QFilter filter_itemName = new QFilter("aos_item", "=", itemName);
		QFilter[] qfs = new QFilter[] { filter_majorCate, filter_middleCate, filter_subCate, filter_itemName };
		return QueryServiceHelper.exists("aos_mkt_photo_cate", qfs);
	}

	/** 在3D选品表存在，拍照地点默认=工厂简拍 **/
	public boolean Judge3dSelect(String cate1, String cate2, String cate3, String itemName) {
		QFBuilder qfBuilder = new QFBuilder();
		qfBuilder.add("aos_category1", "=", cate1);
		qfBuilder.add("aos_category2", "=", cate2);
		qfBuilder.add("aos_category3", "=", cate3);
		qfBuilder.add("aos_name", "=", itemName);
		return QueryServiceHelper.exists("aos_mkt_3dselect", qfBuilder.toArray());
	}

	public boolean Judge3dPlan(Object itemID, String cate1, String cate2, String cate3, String aos_type) {
		QFBuilder qfBuilder = new QFBuilder();
		qfBuilder.add("aos_item", "=", itemID);
		qfBuilder.add("aos_cate1", "=", cate1);
		qfBuilder.add("aos_cate2", "=", cate2);
		qfBuilder.add("aos_cate3", "=", cate3);
		if (aos_type.equals("A"))
			qfBuilder.add("aos_model", "=", "是");
		else
			qfBuilder.add("aos_model", "=", "否");
		return QueryServiceHelper.exists("aos_mkt_3dplan", qfBuilder.toArray());
	}

	/** 拍照需求表标识 **/
	public final static String aos_mkt_photoreq = "aos_mkt_photoreq";
	/** 状态 **/
	public final static String aos_status = "aos_status";
	/** 需求类型 **/
	public final static String aos_type = "aos_type";
	/** 是否拍照 **/
	public final static String aos_photoflag = "aos_photoflag";
	/** 是否拍展示视频 **/
	public final static String aos_vedioflag = "aos_vedioflag";
	/** 不拍照原因 **/
	public final static String aos_reason = "aos_reason";
	/** 同货号 **/
	public final static String aos_sameitemid = "aos_sameitemid";
	/** 需求类型 **/
	public final static String aos_reqtype = "aos_reqtype";
	/** 物料信息 **/
	public final static String aos_itemid = "aos_itemid";
	/** 品名 **/
	public final static String aos_itemname = "aos_itemname";
	/** 品牌 **/
	public final static String aos_contrybrand = "aos_contrybrand";
	/** 是否新产品 **/
	public final static String aos_newitem = "aos_newitem";
	/** 是否新供应商 **/
	public final static String aos_newvendor = "aos_newvendor";
	/** 合同号 **/
	public final static String aos_ponumber = "aos_ponumber";
	/** 行号 **/
	public final static String aos_linenumber = "aos_linenumber";
	/** 产品规格 **/
	public final static String aos_specification = "aos_specification";
	/** 产品属性1 **/
	public final static String aos_seting1 = "aos_seting1";
	/** 产品属性2 **/
	public final static String aos_seting2 = "aos_seting2";
	/** 产品卖点 **/
	public final static String aos_sellingpoint = "aos_sellingpoint";
	/** 供应商 **/
	public final static String aos_vendor = "aos_vendor";
	/** 城市 **/
	public final static String aos_city = "aos_city";
	/** 工厂联系人 **/
	public final static String aos_contact = "aos_contact";
	/** 地址 **/
	public final static String aos_address = "aos_address";
	/** 联系电话 **/
	public final static String aos_phone = "aos_phone";
	/** 拍照地点 **/
	public final static String aos_phstate = "aos_phstate";
	/** 采购 **/
	public final static String aos_poer = "aos_poer";
	/** 开发 **/
	public final static String aos_developer = "aos_developer";
	/** 跟单 **/
	public final static String aos_follower = "aos_follower";
	/** 单据编号 **/
	public final static String billno = "billno";
	/** 出运日期 **/
	public final static String aos_shipdate = "aos_shipdate";
	/** 白底摄影师 **/
	public final static String aos_whiteph = "aos_whiteph";
	/** 实景摄影师 **/
	public final static String aos_actph = "aos_actph";
	/** 摄像师 **/
	public final static String aos_vedior = "aos_vedior";
	/** 当前节点操作人 **/
	public final static String aos_user = "aos_user";
	/** 源单ID **/
	public final static String aos_sourceid = "aos_sourceid";
	/** 设计师 **/
	public final static String aos_designer = "aos_designer";
	/** 到港日期 **/
	public final static String aos_arrivaldate = "aos_arrivaldate";
	/** 系统管理员 **/
	public final static String system = Cux_Common_Utl.SYSTEM;
	/** 大类 **/
	public final static String aos_category1 = "aos_category1";
	/** 中类 **/
	public final static String aos_category2 = "aos_category2";
	/** 小类 **/
	public final static String aos_category3 = "aos_category3";
	/** 紧急程度 **/
	public final static String aos_urgent = "aos_urgent";
	/** 出运日期 **/
	public final static String aos_requiredate = "aos_requiredate";

	@Override
	public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
		String FieldName = hyperLinkClickEvent.getFieldName();
		if ("aos_refer".equals(FieldName)) {
			openPhotoUrl();
		}
	}
}