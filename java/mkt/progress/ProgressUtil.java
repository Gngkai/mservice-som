package mkt.progress;

import com.alibaba.nacos.common.utils.Pair;
import common.CommonDataSomQuo;
import common.Cux_Common_Utl;
import common.fnd.FndBotp;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.ILocaleString;
import kd.bos.entity.datamodel.ListSelectedRowCollection;
import kd.bos.form.IFormView;
import kd.bos.list.IListView;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import common.sal.util.QFBuilder;
import mkt.common.MKTCom;
import mkt.progress.design.AosMktDesignReqBill;
import mkt.progress.design.AosMktSloganBill;
import mkt.progress.design.aadd.aos_mkt_aadd_bill;
import mkt.progress.design3d.aos_mkt_3design_bill;
import mkt.progress.iface.iteminfo;
import mkt.progress.listing.AosMktListingMinBill;
import mkt.progress.listing.AosMktListingReqBill;
import mkt.progress.listing.AosMktListingSonBill;
import mkt.progress.photo.AosMktProgPhReqBill;
import mkt.progress.photo.AosMktRcvBill;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.TreeBidiMap;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author create by gk
 * @date 2022/10/25 14:29
 * @action 营销审批公用方法（仅仅适用营销审批）
 */
public class ProgressUtil {
	/** 根据组织获取用户的国别 **/
	public static String getOrgByOrganizate(Object userid) {
		// 根据组织获取国别
		String aos_unit = Cux_Common_Utl.GetUserOrgLong(userid);

		QFilter filter_unit = new QFilter("aos_unit", QCP.equals, aos_unit);
		QFilter[] filters = new QFilter[] { filter_unit };
		String SelectColumn = "aos_orgid";
		DynamicObject aos_mkt_progunituser = QueryServiceHelper.queryOne("aos_mkt_progunituser", SelectColumn, filters);
		String re = "";
		if (aos_mkt_progunituser != null)
			re = aos_mkt_progunituser.getString(SelectColumn);
		return re;
	}

	/**
	 * 修复关联关系
	 * 
	 * @param billCode
	 * @param id
	 */
	public static void botp(String billCode, Object id) {
		try {
			FndBotp.main(billCode, "aos_entryentity", "aos_sourcebilltype", "aos_sourcebillno", "aos_srcentrykey",
					"aos_srcrowseq", id);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Listing 列表提交
	 *
	 * @param formView         listView
	 * @param listSelectedRows ListSelectedRows
	 * @return userid
	 */
	public static String submitEntity(IFormView formView, ListSelectedRowCollection listSelectedRows) throws FndError {
		IListView view = (IListView) formView;
		String billFormId = view.getBillFormId();
		// 获取权限用户
		List<String> list_user = MKTCom.getPrivilegedUser();
		String currUserId = String.valueOf(RequestContext.get().getCurrUserId());
		StringJoiner str = new StringJoiner(" , ");
		List<Object> list_rows = listSelectedRows.stream().map(row -> row.getPrimaryKeyValue()).distinct()
				.collect(Collectors.toList());
		Map<String, String> map_billno = listSelectedRows.stream().collect(Collectors
				.toMap(row -> row.getPrimaryKeyValue().toString(), row -> row.getBillNo(), (key1, key2) -> key1));
		for (Object primaryKeyValue : list_rows) {
			DynamicObject dy_main = BusinessDataServiceHelper.loadSingle(primaryKeyValue, billFormId);
			DynamicObject aos_user = dy_main.getDynamicObject("aos_user");
			if (aos_user == null) {
				str.add(dy_main.getString("billno"));
				continue;
			}
			String pkValue = aos_user.getString("id");
			try {
				// 所有权限用户 || 节点操作人
				if (list_user.contains(currUserId) || pkValue.equals(currUserId)) {
					if (billFormId.equals("aos_mkt_listing_min")) // 小语种提交
						new AosMktListingMinBill().aosSubmit(dy_main, "B");
					else if (billFormId.equals("aos_mkt_listing_son")) // 文案提交
						new AosMktListingSonBill().aosSubmit(dy_main, "B");
					else if (billFormId.equals("aos_mkt_listing_req")) // 优化需求提交
						new AosMktListingReqBill().aosSubmit(dy_main, "B");
					else if (billFormId.equals("aos_mkt_3design")) // 3D产品设计单
						new aos_mkt_3design_bill().aos_submit(dy_main, "B");
					else if (billFormId.equals("aos_mkt_designreq")) // 设计需求表
						new AosMktDesignReqBill().aos_submit(dy_main, "B");
					else if (billFormId.equals("aos_mkt_rcv")) // 样品入库通知单
						new AosMktRcvBill().aosSubmit(dy_main, "B");
					else if (billFormId.equals("aos_mkt_photoreq"))
						new AosMktProgPhReqBill().aosSubmit(dy_main, "B");
					else if (billFormId.equals("aos_mkt_aadd"))
						new aos_mkt_aadd_bill().aosSubmit(dy_main, "B");
					else if (billFormId.equals("aos_mkt_slogan"))
						new AosMktSloganBill().aos_submit(dy_main, "B");
				}
				// 没有权限
				else {
					str.add(dy_main.getString("billno"));
				}
			} catch (FndError fndMessage) {
				String message = " 单号：  " + map_billno.get(primaryKeyValue.toString()) + "  "
						+ fndMessage.getErrorMessage();
				fndMessage.SetErrorMessage(message);
				throw fndMessage;
			}
		}
		return str.toString();
	}

	/**
	 * 根据任务类型查找设计师
	 *
	 * @param orgid        国别
	 * @param AosCategory1 大类
	 * @param AosCategory2 中类
	 * @param type         任务类型
	 * @return dy
	 */
	public static DynamicObject findDesignerByType(Object orgid, String AosCategory1, String AosCategory2, String type,
			String... selectFields) {
		String designer;
		StringJoiner select = new StringJoiner(",");
		switch (type) {
		case "新品设计":
			designer = "aos_designer";
			break;
		case "国别新品":
			designer = "aos_designassit aos_designer";
			break;
		case "四者一致":
			designer = "aos_designassit aos_designer";
			break; // 错误修正就是 四者一致
		case "老品优化":
			designer = "aos_designer";
			break;
		case "老品重拍":
			designer = "aos_designassit aos_designer";
			break;
		default:
			designer = "aos_designer";
			break;
		}
		select.add(designer);
		for (String field : selectFields) {
			if (!field.equals("aos_designer")) {
				select.add(field);
			}
		}
		QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
		QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
		QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2 };
		DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_proguser", select.toString(),
				filters_category);
//		List<String> list_transType = Arrays.asList("翻译", "功能图翻译");
//		if (list_transType.contains(type) && orgid != null) {
//			String user = QueryEdit(orgid);
//			if (user != null)
//				aos_mkt_proguser.set("aos_designer", user);
//		}
		return aos_mkt_proguser;
	}

	/**
	 * 查找翻译类型的 设计师
	 *
	 * @param orgid
	 */
	public static String QueryEdit(Object orgid) {
		QFilter filter_org = new QFilter("aos_org", "=", orgid);
		QFilter filter_status = new QFilter("aos_status", "!=", "N");
		DynamicObject dy = QueryServiceHelper.queryOne("aos_mkt_pro_interput", "aos_user",
				new QFilter[] { filter_org, filter_status });
		String re = null;
		if (dy != null)
			re = dy.getString("aos_user");
		return re;
	}

	/**
	 * 根据任务类型查找编辑
	 *
	 * @param AosCategory1 cate1
	 * @param AosCategory2 cate2
	 * @param type         任务类型
	 * @param selectFieds  额外查找字段
	 * @return 编辑
	 */
	public static DynamicObject findEditorByType(String AosCategory1, String AosCategory2, String type,
			String... selectFieds) {
		QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
		QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
		String editor = "aos_eng aos_user";
		if (type.equals("四者一致")) {
			editor = "aos_edmanar aos_user";
		}
		StringJoiner str = new StringJoiner(",");
		str.add(editor);
		for (String fied : selectFieds) {
			str.add(fied);
		}
		return QueryServiceHelper.queryOne("aos_mkt_proguser", str.toString(),
				new QFilter[] { filter_category1, filter_category2 });
	}

	/**
	 * 根据任务类型查找国别编辑
	 *
	 * @param AosCategory1 cate1
	 * @param AosCategory2 cate2
	 * @param type         任务类型
	 * @param selectFieds  额外查找字段
	 * @return 编辑
	 */
	public static DynamicObject minListtFindEditorByType(Object org, String AosCategory1, String AosCategory2,
			String type, String... selectFieds) {
		QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
		QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
		QFilter filter_org = new QFilter("aos_orgid", "=", org);
		QFilter[] qfs = new QFilter[] { filter_org, filter_category1, filter_category2 };
		String editor = "aos_oueditor aos_user";
		if (type.equals("四者一致") || type.equals("功能图翻译")) {
			editor = "aos_language aos_user";
		}
		StringJoiner str = new StringJoiner(",");
		str.add(editor);
		for (String fied : selectFieds) {
			str.add(fied);
		}
		return QueryServiceHelper.queryOne("aos_mkt_progorguser", str.toString(), qfs);
	}

	/**
	 * 判断紧急程度
	 **/
	public static String JudgeUrgency(Date date_now, Date date_shipment) {
		if (date_now == null || date_shipment == null)
			return "";
		LocalDate local_now = date_now.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		LocalDate local_shipment = date_shipment.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		long days = local_now.toEpochDay() - local_shipment.toEpochDay();
		if (days <= 10) {
			if (days >= 0)
				return "已经出运" + days + "天";
			else
				return (-days) + "天后出运";
		} else if (days > 10 && days < 20)
			return "紧急";
		else
			return "非常紧急";
	}

	/**
	 * 获取新品排单的链接
	 **/
	public static String QueryImgUrlThroughNew(Object itemID) {
		QFilter filter_item = new QFilter("entryentity.aos_articlenumber", "=", itemID);
		DynamicObject dy = QueryServiceHelper.queryOne("aos_newarrangeorders", "entryentity.aos_picture aos_picture",
				new QFilter[] { filter_item });
		if (dy == null)
			return "";
		else
			return System.getProperty("fileserver") + dy.getString("aos_picture");
	}

	/**
	 * 判断用户是否为该部门人员，只能2级、3级组织
	 **/
	public static Boolean JudgeSaleUser(long userID, String dapartmentNumber) {
		// 获取所有的销售部门
		QFilter filter = new QFilter("parent.number", "=", dapartmentNumber);
		List<String> list_second = QueryServiceHelper
				.query("bos_adminorg_structure", "org.number number", new QFilter[] { filter }).stream()
				.map(dy -> dy.getString("number")).distinct().collect(Collectors.toList());
		QFilter filter_se = new QFilter("parent.number", QFilter.in, list_second);
		List<String> list_thrid = QueryServiceHelper
				.query("bos_adminorg_structure", "org.number number", new QFilter[] { filter_se }).stream()
				.map(dy -> dy.getString("number")).distinct().collect(Collectors.toList());
		QFilter filter_user = new QFilter("id", "=", userID);
		List<String> list_userDep = QueryServiceHelper
				.query("bos_user", "entryentity.dpt.number number", new QFilter[] { filter_user }).stream()
				.map(dy -> dy.getString("number")).collect(Collectors.toList());
		// 最后一级的部门，通过最后一级的部门，判断是否在这个部门
		List<String> list_dp = list_thrid;
		if (list_dp.size() == 0)
			list_dp = list_second;

		boolean saleDep = false; // 不在该部门
		for (String dep : list_userDep) {
			if (list_dp.contains(dep)) {
				saleDep = true; // 在该部门
				break;
			}
		}
		return saleDep;
	}

	/** 部门枚举 **/
	public enum Dapartment {
		Sale("销售部", "Org-00023"), Mkt_Design("营销设计部", "Org-00134");

		private String name, number;

		Dapartment(String name, String number) {
			this.name = name;
			this.number = number;
		}

		public String getName() {
			return name;
		}

		public String getNumber() {
			return number;
		}

		@Override
		public String toString() {
			return "Dapartment{" + "name='" + name + '\'' + ", number='" + number + '\'' + '}';
		}
	}

	/**
	 * 查找单据节点提交人
	 * 
	 * @param fid      单据主键
	 * @param formName 单据标识
	 * @param status   单据状态
	 */
	public static String getSubmitUser(Object fid, String formName, String status) {
		QFilter filter_source = new QFilter("aos_sourceid", "=", fid);
		QFilter filter_name = new QFilter("aos_formid", "=", formName);
		QFilter filter_status = new QFilter("aos_desc", "=", status);
		QFilter[] qfs = new QFilter[] { filter_name, filter_source, filter_status };
		DynamicObjectCollection dyc = QueryServiceHelper.query("aos_sync_operate", "aos_actionby", qfs,
				"aos_actiondate desc", 1);
		if (dyc.size() > 0) {
			return dyc.get(0).getString("aos_actionby");
		}
		return null;
	}

	/** 判断是否为权限用户 **/
	public static Boolean JudgeUserRight() {
		QFilter filter = new QFilter("aos_user.id", QCP.equals, RequestContext.get().getCurrUserId());
		QFilter filter2 = new QFilter("aos_process", QCP.equals, true);// 判断是否为工作流管理员
		return QueryServiceHelper.exists("aos_mkt_userights", new QFilter[] { filter, filter2 });
	}

	/**
	 * 查询操作日期
	 * 
	 * @param BillName 单据标识
	 * @param BillId   单据主键
	 */
	public static Pair<BidiMap<String, Integer>, Map<String, Date>> getOperateLog(Object BillName, Object BillId) {
		QFilter filter_name = new QFilter("aos_formid", "=", BillName);
		QFilter filter_id = new QFilter("aos_sourceid", "=", BillId);
		QFilter[] qfs = new QFilter[] { filter_name, filter_id };
		DynamicObjectCollection dyc = QueryServiceHelper.query("aos_sync_operate",
				"aos_desc,aos_actiondate,aos_actionby", qfs, "aos_actiondate asc");
		BidiMap<String, Integer> map_opIndex = new TreeBidiMap<>(); // 每个操作对应的步骤
		Map<String, Date> map_opDate = new HashMap<>(); // 每个操作对应的时间
		for (int i = 0; i < dyc.size(); i++) {
			map_opIndex.put(dyc.get(i).getString("aos_desc"), i);
			map_opDate.put(dyc.get(i).getString("aos_desc"), dyc.get(i).getDate("aos_actiondate"));
		}
		return Pair.with(map_opIndex, map_opDate);
	}

	/**
	 * 查询操作日期
	 *
	 * @param BillName 单据标识
	 * @param BillId   单据主键
	 */
	public static Pair<BidiMap<String, Integer>, Map<String, DynamicObject>> getOperateLog(Object BillName,
			Object BillId, String... selectFields) {
		QFilter filter_name = new QFilter("aos_formid", "=", BillName);
		QFilter filter_id = new QFilter("aos_sourceid", "=", BillId);
		QFilter[] qfs = new QFilter[] { filter_name, filter_id };
		StringJoiner str = new StringJoiner(",");
		str.add("aos_desc");
		str.add("aos_actiondate");
		str.add("aos_actionby");
		DynamicObjectCollection dyc = QueryServiceHelper.query("aos_sync_operate", str.toString(), qfs,
				"aos_actiondate asc");
		BidiMap<String, Integer> map_opIndex = new TreeBidiMap<>(); // 每个操作对应的步骤
		Map<String, DynamicObject> map_opDate = new HashMap<>(); // 每个操作对应的数据
		for (int i = 0; i < dyc.size(); i++) {
			map_opIndex.put(dyc.get(i).getString("aos_desc"), i);
			map_opDate.put(dyc.get(i).getString("aos_desc"), dyc.get(i));
		}
		return Pair.with(map_opIndex, map_opDate);
	}

	/**
	 * @param itemID SKU终止，且海外在途+海外库存＜安全库存，且首次入库日期为空时 国别跳过
	 * @return 下单国别
	 */
	public static String getOrderOrg(Object itemID) {
		StringJoiner aos_orgtext = new StringJoiner(";");
		DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(itemID, "bd_material");
		DynamicObjectCollection aos_contryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
		// 获取所有国家品牌 字符串拼接 终止
		for (DynamicObject aos_contryentry : aos_contryentryS) {
			DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
			String aos_nationalitynumber = aos_nationality.getString("number");
			if ("IE".equals(aos_nationalitynumber))
				continue;
			Object org_id = aos_nationality.get("id"); // ItemId
			int OsQty = iteminfo.GetItemOsQty(org_id, itemID); // 海外库存
			int onQty = CommonDataSomQuo.get_on_hand_qty(Long.valueOf(org_id.toString()),
					Long.valueOf(itemID.toString()));
			OsQty += onQty;

			int SafeQty = iteminfo.GetSafeQty(org_id);
			// 当SKU终止，且海外在途+海外库存＜安全库存，且首次入库日期为空时，则自动去掉对应下单国别
			if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty
					&& Cux_Common_Utl.IsNull(aos_contryentry.get("aos_firstindate")))
				continue;
			aos_orgtext.add(aos_nationalitynumber);
		}
		return aos_orgtext.toString();
	}

	/** 设置容器锁定性 **/
	public void setContinEnable(String name, List<String> list_noEableField) {
		String panelName = name;
//		if (panelName.equals("aos_contentpanelflex"))
//			panelName = "aos_flexpanelap1";
//		Container flexPanel = this.getView().getControl(panelName);
//		String[] keys = flexPanel.getItems().stream().map(Control::getKey)
//				.filter(key -> !list_noEableField.contains(key)).collect(Collectors.toList()).toArray(new String[0]);
//		this.getView().setEnable(false, keys);
	}

	/** 判断布景师 **/
	public static Boolean JudeMaster() {
		DynamicObject dy = BusinessDataServiceHelper.loadSingle(RequestContext.get().getCurrUserId(), "bos_user");
		for (DynamicObject dy_ent : dy.getDynamicObjectCollection("entryentity")) {
			ILocaleString position = dy_ent.getLocaleString("position");
			if (position.getLocaleValue_zh_CN().contains("布景")) {
				return true;
			}
		}
		return false;
	}

	/** 获取抠图任务清单中的设计师 **/
	public static String findPSlistDesign(Object itemID) {
		String re = "";
		String category = (String) SalUtil.getCategoryByItemId(String.valueOf(itemID)).get("name");
		String[] category_group = category.split(",");
		String AosCategory1 = null;
		String AosCategory2 = null;
		int category_length = category_group.length;
		if (category_length > 0)
			AosCategory1 = category_group[0];
		if (category_length > 1)
			AosCategory2 = category_group[1];
		QFilter filter_ca1 = new QFilter("aos_category1", "=", AosCategory1);
		QFilter filter_ca2 = new QFilter("aos_category2", "=", AosCategory2);
		DynamicObject dy = QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_pser",
				new QFilter[] { filter_ca1, filter_ca2 });
		if (dy != null && !Cux_Common_Utl.IsNull(dy.get("aos_pser")))
			re = dy.getString("aos_pser");
		return re;
	}

	/** 获取用户id **/
	public static String findUserByNumber(String number) {
		QFilter filter_number = new QFilter("number", "=", number);
		DynamicObject dy = QueryServiceHelper.queryOne("bos_user", "id", new QFilter[] { filter_number });
		String re = "";
		if (dy != null)
			re = dy.getString("id");
		return re;
	}

	/**
	 * @param org_id
	 * @param itemid 物料id
	 * @param user   人员标识
	 * @return 查找国别品类人员的人员主键
	 */
	public static String findUserByOrgCate(Object org_id, Object itemid, String user) {
		String re = "";
		String category = MKTCom.getItemCateNameZH(itemid);
		String[] category_group = category.split(",");
		String AosCategory1 = null;
		String AosCategory2 = null;
		int category_length = category_group.length;
		if (category_length > 0)
			AosCategory1 = category_group[0];
		if (category_length > 1)
			AosCategory2 = category_group[1];
		if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
			QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
			QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
			QFilter filter_ou = new QFilter("aos_orgid", "=", org_id);
			QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2, filter_ou };
			DynamicObject aos_mkt_progorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", user,
					filters_category);
			if (aos_mkt_progorguser != null) {
				re = aos_mkt_progorguser.getString(user);
			} else
				throw new FndError("国别品类表中的 " + user + " 不存在");
		} else
			throw new FndError("物理产品类别 不存在");
		return re;
	}

	public static boolean Is_saleout(Object id) {
		QFBuilder builder = new QFBuilder();
		StringJoiner str = new StringJoiner(",");
		str.add("aos_contryentry.aos_is_saleout aos_is_saleout");
		builder.add("id", "=", id);
		builder.add("aos_contryentry.aos_is_saleout", "=", "1");
		DynamicObject dy = QueryServiceHelper.queryOne("bd_material", str.toString(), builder.toArray());
		if (dy == null) {
			return false;
		} else
			return true;
	}

	/**
	 * 出运日期：关联拍照需求表对应的合同号+货号，最早的出运日期；
	 * 
	 * @param poNum 合同号
	 * @param sku   货号
	 * @return
	 */
	public static Date getShippingDate(String poNum, String sku) {
		Date shippingDate = null;
		DynamicObjectCollection aosMktPhotoReq = QueryServiceHelper.query("aos_mkt_photoreq", "aos_shipdate",
				(new QFilter("aos_ponumber", QCP.equals, poNum).and("aos_itemid.number", QCP.equals, sku)).toArray(),
				"aos_shipdate asc", 1);
		if (FndGlobal.IsNotNull(aosMktPhotoReq) && aosMktPhotoReq.size() > 0)
			shippingDate = aosMktPhotoReq.get(0).getDate("aos_shipdate");
		return shippingDate;
	}

	/**
	 * 关联拍照需求表对应的合同号+货号，最早的到港日期(有实际取实际，否则取预计)；
	 * 
	 * @param poNum 合同号
	 * @param sku   货号
	 * @return
	 */
	public static Date getArrivalDate(String poNum, String sku) {
		Date aos_arrivaldate = null;
		DynamicObjectCollection aosMktPhotoReq = QueryServiceHelper.query("aos_mkt_photoreq", "aos_arrivaldate",
				(new QFilter("aos_ponumber", QCP.equals, poNum).and("aos_itemid.number", QCP.equals, sku)).toArray(),
				"aos_arrivaldate asc", 1);
		if (FndGlobal.IsNotNull(aosMktPhotoReq) && aosMktPhotoReq.size() > 0)
			aos_arrivaldate = aosMktPhotoReq.get(0).getDate("aos_arrivaldate");
		return aos_arrivaldate;
	}

	/**
	 * 关联拍照需求表对应的合同号+货号，最早的入库日期(有实际取实际，否则取预计)；
	 * 
	 * @param poNum
	 * @param sku
	 * @return
	 */
	public static Date getRcvDate(String poNum, String sku) {
		Date aos_overseasdate = null;
		DynamicObjectCollection aosMktPhotoReq = QueryServiceHelper.query("aos_mkt_photoreq", "aos_overseasdate",
				(new QFilter("aos_ponumber", QCP.equals, poNum).and("aos_itemid.number", QCP.equals, sku)).toArray(),
				"aos_overseasdate asc", 1);
		if (FndGlobal.IsNotNull(aosMktPhotoReq) && aosMktPhotoReq.size() > 0)
			aos_overseasdate = aosMktPhotoReq.get(0).getDate("aos_overseasdate");
		return aos_overseasdate;
	}

}
