package mkt.popular.ppc;

import common.Cux_Common_Utl;
import common.fnd.FndDate;
import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import common.sal.sys.basedata.dao.ItemDao;
import common.sal.sys.basedata.dao.impl.ItemDaoImpl;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.cache.CacheFactory;
import kd.bos.cache.TempFileCache;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.entity.datamodel.ListSelectedRowCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.CloseCallBack;
import kd.bos.form.FormShowParameter;
import kd.bos.form.ShowType;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.common.MKTCom;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.xssf.usermodel.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class aos_mkt_popppc_list extends AbstractListPlugin {

	@SuppressWarnings("unchecked")
	@Override
	public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
		String actionId = closedCallBackEvent.getActionId();
		if (StringUtils.equals(actionId, "form")) {
			Object map = closedCallBackEvent.getReturnData();
			String aos_ou_code = (String) ((Map<String, Object>) map).get("aos_ou_code");

			aos_mkt_popppc_init.ManualitemClick(aos_ou_code);
			this.getView().invokeOperation("refresh");
			this.getView().showSuccessNotification("已手工提交PPC数据源初始化,请等待,务重复提交!");
		}
	}

	public ByteArrayInputStream parse(final OutputStream out) throws Exception {
		ByteArrayOutputStream baos = (ByteArrayOutputStream) out;
		final ByteArrayInputStream swapStream = new ByteArrayInputStream(baos.toByteArray());
		return swapStream;
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		if (StringUtils.equals("aos_init", evt.getItemKey())) {
			// PPC数据源初始化
			aos_init();
		} else if (StringUtils.equals("aos_export", evt.getItemKey())) {
			// 打印报告
			try {
				aos_export();
			} catch (ParseException parseException) {
				parseException.printStackTrace();
			}
		}

		else if (StringUtils.equals("aos_exportnew", evt.getItemKey())) {
			// 打印新版报告
			try {
				aos_exportnew();
			} catch (ParseException parseException) {
				parseException.printStackTrace();
			}
		}

		else if (StringUtils.equals("aos_close", evt.getItemKey())) {
			// 关闭时计算 最后调价日期
			try {
				aos_close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void aos_close() {
		// 获取选中行
		ListSelectedRowCollection selectedRows = this.getSelectedRows();
		List<DynamicObject> list_dy = new ArrayList<>();
		selectedRows.stream().forEach(e -> {
			DynamicObject dy = BusinessDataServiceHelper.loadSingle(e.getPrimaryKeyValue(), "aos_mkt_popular_ppc");
			list_dy.add(dy);
		});
		// 国别 物料 组名 确定唯一值
		// 系列状态只用可用
		list_dy.stream().parallel().forEach(e -> {
			// 查找前一天的 同一国别的数据
			Date aos_date = e.getDate("aos_date");
			DynamicObject dy_org = e.getDynamicObject("aos_orgid");
			Map<String, BigDecimal> map_lastData = getLastPeriodData(dy_org.getString("id"), aos_date);
			// 单据体行遍历,比较调价日期及赋值
			DynamicObjectCollection dyc_ent = e.getDynamicObjectCollection("aos_entryentity");
			StringBuilder strbuilder = new StringBuilder();
			dyc_ent.stream().filter(ent -> ent.getString("aos_groupstatus").equals("AVAILABLE")).forEach(ent -> {
				strbuilder.append(ent.getString("aos_itemnumer"));
				strbuilder.append("?");
				strbuilder.append(ent.getDynamicObject("aos_itemid").getString("id"));
				String key = strbuilder.toString();
				strbuilder.setLength(0);
				if (map_lastData.containsKey(key)) {
					BigDecimal big_last = map_lastData.get(key);
					BigDecimal big_the = ent.getBigDecimal("aos_bid");
					if (big_last.compareTo(big_the) != 0)
						ent.set("aos_lastpricedate", aos_date);
				} else
					ent.set("aos_lastpricedate", aos_date);
			});
		});
		SaveServiceHelper.save(list_dy.toArray(new DynamicObject[list_dy.size()]));
		this.getView().showMessage("设置完成");
	}

	private void aos_init() {
		FormShowParameter showParameter = new FormShowParameter();
		showParameter.setFormId("aos_mkt_cal_form");
		showParameter.getOpenStyle().setShowType(ShowType.Modal);
		showParameter.setCloseCallBack(new CloseCallBack(this, "form"));
		this.getView().showForm(showParameter);
	}

	private void aos_export() throws ParseException {
		List<ListSelectedRow> list = getSelectedRows();// 获取选中行
		for (int i = 0; i < list.size(); i++) {
			String fid = list.get(i).toString();
			DynamicObject aos_mkt_popular_ppc = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_popular_ppc");
			DynamicObject aos_orgid = (DynamicObject) aos_mkt_popular_ppc.get("aos_orgid");
			Date aos_date = aos_mkt_popular_ppc.getDate("aos_date");
			String p_ou_code = aos_orgid.getString("number");
			XSSFWorkbook workbook = excel("title", fid, aos_date);// 上传
			String path = upload("推广报告" + p_ou_code, workbook);// 弹出提示框
			this.getView().download(path);
		}
	}

	private void aos_exportnew() throws ParseException {
		List<ListSelectedRow> list = getSelectedRows();// 获取选中行
		for (int i = 0; i < list.size(); i++) {
			String fid = list.get(i).toString();
			DynamicObject aos_mkt_popular_ppc = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_popular_ppc");
			DynamicObject aos_orgid = (DynamicObject) aos_mkt_popular_ppc.get("aos_orgid");
			Date aos_date = aos_mkt_popular_ppc.getDate("aos_date");
			String p_ou_code = aos_orgid.getString("number");
			XSSFWorkbook workbook = excelnew("title", fid, aos_date);// 上传
			String path = upload("新版推广报告" + p_ou_code, workbook);// 弹出提示框
			this.getView().download(path);
		}
	}

	private XSSFWorkbook excel(String title, String fid, Date aos_date) throws ParseException {
		// 创建excel工作簿
		XSSFWorkbook workbook = new XSSFWorkbook();
		XSSFCellStyle style = workbook.createCellStyle();
		style.setAlignment(HorizontalAlignment.LEFT);
		set_excelAttr(workbook, style, fid, aos_date);
		return workbook;
	}

	private XSSFWorkbook excelnew(String string, String fid, Date aos_date) throws ParseException {
		// 创建excel工作簿
		XSSFWorkbook workbook = new XSSFWorkbook();
		XSSFCellStyle style = workbook.createCellStyle();
		style.setAlignment(HorizontalAlignment.LEFT);
		set_excelAttrnew(workbook, style, fid, aos_date);
		return workbook;
	}

	private void set_excelAttr(XSSFWorkbook workbook, XSSFCellStyle style, String fid, Date aos_date)
			throws ParseException {

		DynamicObject aos_sync_log = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
		aos_sync_log.set("aos_type_code", "MKT_推广报告");
		aos_sync_log.set("aos_groupid", fid);
		aos_sync_log.set("billstatus", "A");
		DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");

		try {
			// 创建工作表sheet
			XSSFSheet sheet = workbook.createSheet();
			sheet.setDefaultColumnWidth(15);
			// 创建标题
			XSSFRow headRow = sheet.createRow(0);
			CreateColumn(headRow, style, 0, "Record ID");
			CreateColumn(headRow, style, 1, "Record Type");
			CreateColumn(headRow, style, 2, "Campaign ID");
			CreateColumn(headRow, style, 3, "Campaign");
			CreateColumn(headRow, style, 4, "Campaign Daily Budget");
			CreateColumn(headRow, style, 5, "Portfolio ID");
			CreateColumn(headRow, style, 6, "Campaign Start Date");
			CreateColumn(headRow, style, 7, "Campaign End Date");
			CreateColumn(headRow, style, 8, "Campaign Targeting Type");
			CreateColumn(headRow, style, 9, "Ad Group");
			CreateColumn(headRow, style, 10, "Max Bid");
			CreateColumn(headRow, style, 11, "Keyword or Product Targeting");
			CreateColumn(headRow, style, 12, "Product Targeting ID");
			CreateColumn(headRow, style, 13, "Match Type");
			CreateColumn(headRow, style, 14, "SKU");
			CreateColumn(headRow, style, 15, "Campaign Status");
			CreateColumn(headRow, style, 16, "Ad Group Status");
			CreateColumn(headRow, style, 17, "Status");
			CreateColumn(headRow, style, 18, "Impressions");
			CreateColumn(headRow, style, 19, "Clicks");
			CreateColumn(headRow, style, 20, "Spend");
			CreateColumn(headRow, style, 21, "Orders");
			CreateColumn(headRow, style, 22, "Total units");
			CreateColumn(headRow, style, 23, "Sales");
			CreateColumn(headRow, style, 24, "ACoS");
			CreateColumn(headRow, style, 25, "Bidding strategy");
			CreateColumn(headRow, style, 26, "Placement Type");
			CreateColumn(headRow, style, 27, "Increase bids by placement");

			// 查询当前单据国别
			DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_popular_ppc",
					"aos_orgid," + "aos_orgid.number p_ou_code", new QFilter[] { new QFilter("id", QCP.equals, fid) });
			String aos_orgid = dynamicObject.getString("aos_orgid");
			String p_ou_code = dynamicObject.getString("p_ou_code");
			// 获取fba数据
			Map<String, List<String>> fbaMap = getFbaMap(aos_orgid);
			// 初始化组数据
			HashMap<String, Map<String, Map<String, Object>>> Group = init_group(fid);
			// HashMap<String, Map<String, Map<String, Object>>> GroupLast =
			// init_groupLast(aos_date);

			// 获取该国别下，所有的爆品物料编码
			List<String> list_saleOutItems = getSaleItemByOu(aos_orgid);

			BigDecimal exRateWellSt = aos_mkt_popppc_init.getExRateLowSt(p_ou_code, "优");

			Map<String, String> comp = initSerialGroup(fid, aos_orgid, exRateWellSt);// 竞价策略

			Map<String, String> portfolio = initSerialRoi(fid, aos_orgid);// 特殊广告

			Map<String, String> portid = initportid(fid, aos_orgid);// 特殊广告

			// 循环单子下所有的产品号
			QFilter filter_id = new QFilter("id", "=", fid);
			// QFilter filter_budget = new QFilter("aos_entryentity.aos_budget", ">", 0);
			QFilter[] filters = new QFilter[] { filter_id/* , filter_budget */ };
			String SelectColumn = "aos_entryentity.aos_productno aos_productno,"
					+ "aos_entryentity.aos_budget aos_budget";
			DataSet aos_mkt_popppc_initS = QueryServiceHelper.queryDataSet("aos_mkt_popppc_list." + fid,
					"aos_mkt_popular_ppc", SelectColumn, filters, "aos_entryentity.aos_productno");
			String[] GroupBy = new String[] { "aos_productno", "aos_budget" };
			aos_mkt_popppc_initS = aos_mkt_popppc_initS.groupBy(GroupBy).finish();
			while (aos_mkt_popppc_initS.hasNext()) {
				int TopOfSearch = 0;
				Row aos_mkt_popppc_init = aos_mkt_popppc_initS.next();
				String aos_productno = aos_mkt_popppc_init.getString("aos_productno");
				// 国别
				BigDecimal aos_budget = aos_mkt_popppc_init.getBigDecimal("aos_budget").setScale(2,
						BigDecimal.ROUND_HALF_UP);
				MKTCom.Put_SyncLog(aos_sync_logS, "系列=" + aos_productno);
				// 每个系列下 创建四行数据
				// 系列第一行
				int row = sheet.getLastRowNum() + 1;
				XSSFRow ProductRow = sheet.createRow(row);
				CreateColumn(ProductRow, style, 1, "Campaign");
				CreateColumn(ProductRow, style, 3, aos_productno);
				CreateColumn(ProductRow, style, 4, aos_budget);
				CreateColumn(ProductRow, style, 8, "Auto");
				CreateColumn(ProductRow, style, 15, "enabled");
				CreateColumn(ProductRow, style, 25, comp.get(aos_productno));
				CreateColumn(ProductRow, style, 26, "All");
				if (FndGlobal.IsNotNull(portfolio.get(aos_productno))) {
					CreateColumn(ProductRow, style, 5, portid.get(portfolio.get(aos_productno)));
				}
				// 系列第二行
				row = sheet.getLastRowNum() + 1;
				TopOfSearch = row;
				ProductRow = sheet.createRow(row);
				CreateColumn(ProductRow, style, 1, "Campaign By Placement");
				CreateColumn(ProductRow, style, 3, aos_productno);
				CreateColumn(ProductRow, style, 26, "Top of search (page 1)");
				// 系列第三行
				row = sheet.getLastRowNum() + 1;
				ProductRow = sheet.createRow(row);
				CreateColumn(ProductRow, style, 1, "Campaign By Placement");
				CreateColumn(ProductRow, style, 3, aos_productno);
				CreateColumn(ProductRow, style, 26, "Rest of search");
				// 系列第四行
				row = sheet.getLastRowNum() + 1;
				ProductRow = sheet.createRow(row);
				CreateColumn(ProductRow, style, 1, "Campaign By Placement");
				CreateColumn(ProductRow, style, 3, aos_productno);
				CreateColumn(ProductRow, style, 26, "Product pages");

				// 循环得到组
				Map<String, Map<String, Object>> GroupD = Group.get(aos_productno);
				Object aos_topprice = BigDecimal.ZERO;
				boolean saleOutItem = false; // 存在爆品
				for (String aos_itemnumer : GroupD.keySet()) {
					// 判断是否为爆品
					if (!saleOutItem && list_saleOutItems.contains(aos_itemnumer))
						saleOutItem = true;
					BigDecimal aos_bid = BigDecimal.ZERO;
					if (!Cux_Common_Utl.IsNull(GroupD.get(aos_itemnumer).get("aos_bid")))
						aos_bid = (BigDecimal) GroupD.get(aos_itemnumer).get("aos_bid");
					Object aos_shopsku = GroupD.get(aos_itemnumer).get("aos_shopsku");
					Object aos_groupstatus = GroupD.get(aos_itemnumer).get("aos_groupstatus");
					aos_topprice = max((BigDecimal) GroupD.get(aos_itemnumer).get("aos_topprice"),
							(BigDecimal) aos_topprice);
					String aos_topprice_str = "0%";
					if (((BigDecimal) aos_topprice).compareTo(BigDecimal.valueOf(0)) == 0)
						aos_topprice_str = "0%";
					else
						aos_topprice_str = ((BigDecimal) aos_topprice).multiply(BigDecimal.valueOf(100)).intValue()
								+ "%";

					String GroupStatus = "enabled";
					if ((aos_groupstatus + "").equals("AVAILABLE"))
						GroupStatus = "enabled";
					else
						GroupStatus = "paused";
					// 排除连续两次pause的组
					/*
					 * if (GroupStatus.equals("paused") && GroupLast != null) { Map<String,
					 * Map<String, Object>> GroupDLast = GroupLast.get(aos_productno); if
					 * (GroupDLast != null) { if (GroupDLast.get(aos_itemnumer) != null) { Object
					 * aos_groupstatusLast = GroupDLast.get(aos_itemnumer).get("aos_groupstatus");
					 * if (aos_groupstatusLast != null && !(aos_groupstatusLast +
					 * "").equals("AVAILABLE")) continue; } } }
					 */
					MKTCom.Put_SyncLog(aos_sync_logS, "组=" + aos_itemnumer);
					// 每个组要创建六行数据
					// 组第一行

					row = sheet.getLastRowNum() + 1;
					ProductRow = sheet.createRow(row);
					CreateColumn(ProductRow, style, 1, "Ad Group");
					CreateColumn(ProductRow, style, 3, aos_productno);
					CreateColumn(ProductRow, style, 9, aos_itemnumer);
					CreateColumn(ProductRow, style, 10, aos_bid);
					CreateColumn(ProductRow, style, 15, "enabled");
					CreateColumn(ProductRow, style, 16, GroupStatus);

					// 组第二行
					// 判断是否fba
					int size = 1;
					boolean flag = false;
					List<String> fbaList = fbaMap.get(aos_itemnumer);
					if (fbaList != null && fbaList.size() > 0) {
						flag = true;
						size = fbaList.size();

						row = sheet.getLastRowNum() + 1;
						ProductRow = sheet.createRow(row);
						CreateColumn(ProductRow, style, 1, "Ad");
						CreateColumn(ProductRow, style, 3, aos_productno);
						CreateColumn(ProductRow, style, 9, aos_itemnumer);
						CreateColumn(ProductRow, style, 14, aos_itemnumer);
						CreateColumn(ProductRow, style, 15, "enabled");
						CreateColumn(ProductRow, style, 16, "enabled");
						CreateColumn(ProductRow, style, 17, GroupStatus);

					}
					for (int i = 0; i < size; i++) {
						row = sheet.getLastRowNum() + 1;
						ProductRow = sheet.createRow(row);
						CreateColumn(ProductRow, style, 1, "Ad");
						CreateColumn(ProductRow, style, 3, aos_productno);
						CreateColumn(ProductRow, style, 9, aos_itemnumer);
						CreateColumn(ProductRow, style, 14, flag ? fbaList.get(i) : aos_shopsku);
						CreateColumn(ProductRow, style, 15, "enabled");
						CreateColumn(ProductRow, style, 16, "enabled");
						CreateColumn(ProductRow, style, 17, GroupStatus);
					}
					// 组第三行
					BigDecimal bid = BigDecimal.ZERO;
					row = sheet.getLastRowNum() + 1;
					ProductRow = sheet.createRow(row);
					CreateColumn(ProductRow, style, 1, "Product Targeting");
					CreateColumn(ProductRow, style, 3, aos_productno);
					CreateColumn(ProductRow, style, 9, aos_itemnumer);
					bid = aos_bid.multiply(BigDecimal.valueOf(1.3)).setScale(2, BigDecimal.ROUND_HALF_UP);
					CreateColumn(ProductRow, style, 10, bid);
					CreateColumn(ProductRow, style, 11, "close-match");
					CreateColumn(ProductRow, style, 12, "close-match");
					CreateColumn(ProductRow, style, 13, "Targeting Expression Predefined");
					CreateColumn(ProductRow, style, 15, "enabled");
					CreateColumn(ProductRow, style, 16, "enabled");
					CreateColumn(ProductRow, style, 17, GroupStatus);
					// 组第四行
					row = sheet.getLastRowNum() + 1;
					ProductRow = sheet.createRow(row);
					CreateColumn(ProductRow, style, 1, "Product Targeting");
					CreateColumn(ProductRow, style, 3, aos_productno);
					CreateColumn(ProductRow, style, 9, aos_itemnumer);
					bid = aos_bid.multiply(BigDecimal.valueOf(1)).setScale(2, BigDecimal.ROUND_HALF_UP);
					CreateColumn(ProductRow, style, 10, bid);
					CreateColumn(ProductRow, style, 11, "loose-match");
					CreateColumn(ProductRow, style, 12, "loose-match");
					CreateColumn(ProductRow, style, 13, "Targeting Expression Predefined");
					CreateColumn(ProductRow, style, 15, "enabled");
					CreateColumn(ProductRow, style, 16, "enabled");
					CreateColumn(ProductRow, style, 17, GroupStatus);
					// 组第五行
					row = sheet.getLastRowNum() + 1;
					ProductRow = sheet.createRow(row);
					CreateColumn(ProductRow, style, 1, "Product Targeting");
					CreateColumn(ProductRow, style, 3, aos_productno);
					CreateColumn(ProductRow, style, 9, aos_itemnumer);
					bid = aos_bid.multiply(BigDecimal.valueOf(1)).setScale(2, BigDecimal.ROUND_HALF_UP);
					CreateColumn(ProductRow, style, 10, bid);
					CreateColumn(ProductRow, style, 11, "complements");
					CreateColumn(ProductRow, style, 12, "complements");
					CreateColumn(ProductRow, style, 13, "Targeting Expression Predefined");
					CreateColumn(ProductRow, style, 15, "enabled");
					CreateColumn(ProductRow, style, 16, "enabled");
					CreateColumn(ProductRow, style, 17, GroupStatus);
					// 组第六行
					row = sheet.getLastRowNum() + 1;
					ProductRow = sheet.createRow(row);
					CreateColumn(ProductRow, style, 1, "Product Targeting");
					CreateColumn(ProductRow, style, 3, aos_productno);
					CreateColumn(ProductRow, style, 9, aos_itemnumer);
					bid = aos_bid.multiply(BigDecimal.valueOf(0.7)).setScale(2, BigDecimal.ROUND_HALF_UP);
					CreateColumn(ProductRow, style, 10, bid);
					CreateColumn(ProductRow, style, 11, "substitutes");
					CreateColumn(ProductRow, style, 12, "substitutes");
					CreateColumn(ProductRow, style, 13, "Targeting Expression Predefined");
					CreateColumn(ProductRow, style, 15, "enabled");
					CreateColumn(ProductRow, style, 16, "enabled");
					CreateColumn(ProductRow, style, 17, GroupStatus);
					sheet.getRow(TopOfSearch);
					CreateColumn(ProductRow, style, 27, aos_topprice_str);

				}
				// 对于置顶位置出价,如果是爆品
//				if (saleOutItem) {
//					ProductRow = sheet.getRow(TopOfSearch);
//					ProductRow = CreateColumn(ProductRow, style, 27, "50%");
//				}
			}
			aos_mkt_popppc_initS.close();// 保存日志表
			OperationResult operationrstLog = OperationServiceHelper.executeOperate("save", "aos_sync_log",
					new DynamicObject[] { aos_sync_log }, OperateOption.create());
			if (operationrstLog.getValidateResult().getValidateErrors().size() != 0) {
			}
		} catch (Exception e) {
			e.printStackTrace();
			MKTCom.Put_SyncLog(aos_sync_logS, "报错 =" + e.getMessage());// 保存日志表
			OperationResult operationrstLog = OperationServiceHelper.executeOperate("save", "aos_sync_log",
					new DynamicObject[] { aos_sync_log }, OperateOption.create());
			if (operationrstLog.getValidateResult().getValidateErrors().size() != 0) {
			}
		}
	}

	private Map<String, String> initportid(String fid, String aos_orgid) {
		Map<String, String> portfolio = new HashMap<>();
		DynamicObjectCollection aos_mkt_advS = QueryServiceHelper.query("aos_mkt_adv", "aos_idpro,aos_type",
				new QFilter("aos_orgid.id", QCP.equals, aos_orgid).toArray());
		for (DynamicObject aos_mkt_adv : aos_mkt_advS) {
			portfolio.put(aos_mkt_adv.getString("aos_type"), aos_mkt_adv.getString("aos_idpro"));
		}

		return portfolio;
	}

	private Map<String, String> initSerialRoi(String fid, String aos_orgid) {
		Map<String, String> portfolio = new HashMap<>();
		HashMap<String, Map<String, Object>> PopOrgInfo = genPop();
		BigDecimal PopOrgRoist = (BigDecimal) PopOrgInfo.get(aos_orgid + "~" + "ROIST").get("aos_value");// 国别标准ROI

		FndMsg.debug("PopOrgRoist:" + PopOrgRoist);

		// 系列ROI
		HashMap<String, BigDecimal> SkuRpt = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -2);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -14);
		Date date_from = calendar.getTime();

		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		String date_str = writeFormat.format(date);

		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<=", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match };
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku.aos_productno||'-AUTO' aos_productno,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurpt",
				"aos_base_skupoprpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_productno" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_spend").sum("aos_total_sales").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			BigDecimal aos_total_sales = aos_base_skupoprpt.getBigDecimal("aos_total_sales");
			BigDecimal aos_spend = aos_base_skupoprpt.getBigDecimal("aos_spend");
			String aos_productno = aos_base_skupoprpt.getString("aos_productno");
			if (aos_spend.compareTo(BigDecimal.ZERO) != 0) {
				SkuRpt.put(aos_productno, aos_total_sales.divide(aos_spend, 2, BigDecimal.ROUND_HALF_UP));
			}
		}
		aos_base_skupoprptS.close();
		DynamicObject aos_mkt_popular_ppc = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_popular_ppc");
		DynamicObjectCollection aos_entryentityS = aos_mkt_popular_ppc.getDynamicObjectCollection("aos_entryentity");
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			String aos_productno = aos_entryentity.getString("aos_productno");
			BigDecimal roi = SkuRpt.get(aos_productno);
			Date aos_firstindate = aos_entryentity.getDate("aos_firstindate");
			String aos_category2 = aos_entryentity.getString("aos_category2");
			String aos_category3 = aos_entryentity.getString("aos_category3");
			String aos_seasonseting = aos_entryentity.getString("aos_seasonseting");

			if (FndGlobal.IsNotNull(SkuRpt.get(aos_productno)) && roi.compareTo(PopOrgRoist) < 0) {
				if (aos_entryentity.getBoolean("aos_special"))
					portfolio.put(aos_productno, "aos_special");
				if (FndGlobal.IsNull(portfolio.get(aos_productno))
						&& "Y".equals(aos_entryentity.getString("aos_offline")))
					portfolio.put(aos_productno, "aos_special");
				if ((FndGlobal.IsNotNull(aos_firstindate))
						&& (FndDate.GetBetweenDays(new Date(), aos_firstindate) < 30))
					portfolio.put(aos_productno, "aos_new");
				if ("秋冬产品".equals(aos_seasonseting) || "冬季产品".equals(aos_seasonseting))
					portfolio.put(aos_productno, "autumn_winter");
				if ("万圣装饰".equals(aos_category3))
					portfolio.put(aos_productno, "halloween");
				if ("圣诞装饰".equals(aos_category2))
					portfolio.put(aos_productno, "chrismas");
			}
		}
		return portfolio;
	}

	private Map<String, String> initSerialGroup(String fid, String p_org_id, BigDecimal exRateWellSt) {
		HashMap<String, Map<String, Object>> PopOrgInfo = genPop();
		Map<String, String> comp = new HashMap<>();
		BigDecimal PopOrgRoist = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "ROIST").get("aos_value");// 国别标准ROI
		BigDecimal WORRY = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "WORRY").get("aos_value");// 差

		DynamicObject aos_mkt_popular_ppc = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_popular_ppc");
		DynamicObjectCollection aos_entryentityS = aos_mkt_popular_ppc.getDynamicObjectCollection("aos_entryentity");
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			BigDecimal aos_roi = aos_entryentity.getBigDecimal("aos_roi14days"); // TODO 调整为14日
			String aos_productno = aos_entryentity.getString("aos_productno");
			Date aos_firstindate = aos_entryentity.getDate("aos_firstindate");
			BigDecimal aos_rpt_roi = aos_entryentity.getBigDecimal("aos_rpt_roi");

			if (aos_rpt_roi.compareTo(exRateWellSt) > 0 && aos_roi.compareTo(WORRY) > 0) {
				comp.put(aos_productno, "Dynamic bidding(up and down)");
			} else if (("A".equals(aos_entryentity.getString("aos_contryentrystatus"))
					|| ((FndGlobal.IsNotNull(aos_firstindate))
							&& (FndDate.GetBetweenDays(new Date(), aos_firstindate) < 30)))
					&& !"Dynamic bidding(up and down)".equals(comp.get(aos_productno))) {
				comp.put(aos_productno, "Fixed bids");
			} else if (!"Fixed bids".equals(comp.get(aos_productno))
					&& !"Dynamic bidding(up and down)".equals(comp.get(aos_productno))) {
				comp.put(aos_productno, "Dynamic bidding (down only)");
			}
		}

		return comp;
	}

	private HashMap<String, Map<String, Object>> genPop() {
		HashMap<String, Map<String, Object>> PopOrgInfo = new HashMap<>();
		String SelectColumn = "aos_orgid," + "aos_type," + "aos_value,aos_condition1";
		DataSet aos_mkt_base_orgvalueS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_poporg",
				"aos_mkt_base_orgvalue", SelectColumn, null, null);
		while (aos_mkt_base_orgvalueS.hasNext()) {
			Row aos_mkt_base_orgvalue = aos_mkt_base_orgvalueS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_value", aos_mkt_base_orgvalue.get("aos_value"));
			Info.put("aos_condition1", aos_mkt_base_orgvalue.get("aos_condition1"));
			PopOrgInfo.put(aos_mkt_base_orgvalue.getLong("aos_orgid") + "~" + aos_mkt_base_orgvalue.get("aos_type"),
					Info);
		}
		aos_mkt_base_orgvalueS.close();
		return PopOrgInfo;
	}

	private void set_excelAttrnew(XSSFWorkbook workbook, XSSFCellStyle style, String fid, Date aos_date)
			throws ParseException {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
		// 创建工作表sheet
		XSSFSheet sheet = workbook.createSheet();
		sheet.setDefaultColumnWidth(15);
		// 创建标题
		XSSFRow headRow = sheet.createRow(0);
		CreateColumn(headRow, style, 0, "Product");
		CreateColumn(headRow, style, 1, "Enity");
		CreateColumn(headRow, style, 2, "Operation");
		CreateColumn(headRow, style, 3, "Campaign Id");
		CreateColumn(headRow, style, 4, "Ad Group Id");
		CreateColumn(headRow, style, 5, "Portfolio Id");
		CreateColumn(headRow, style, 6, "Ad Id (Read only)");
		CreateColumn(headRow, style, 7, "Keyword Id (Read only)");
		CreateColumn(headRow, style, 8, "Product Targeting Id (Read only)");
		CreateColumn(headRow, style, 9, "Campaign Name");
		CreateColumn(headRow, style, 10, "Ad Group Name");
		CreateColumn(headRow, style, 11, "Start Date");
		CreateColumn(headRow, style, 12, "End Date");
		CreateColumn(headRow, style, 13, "Targeting Type");
		CreateColumn(headRow, style, 14, "State");
		CreateColumn(headRow, style, 15, "Daily Budget");
		CreateColumn(headRow, style, 16, "SKU");

		CreateColumn(headRow, style, 17, "Ad Group Default Bid");
		CreateColumn(headRow, style, 18, "Bid");
		CreateColumn(headRow, style, 19, "Keyword Text");
		CreateColumn(headRow, style, 20, "Match Type");
		CreateColumn(headRow, style, 21, "Bidding Strategy");
		CreateColumn(headRow, style, 22, "Placement");
		CreateColumn(headRow, style, 23, "Percentage");
		CreateColumn(headRow, style, 24, "Product Targeting Expression");

		// 查询当前单据国别
		DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_popular_ppc", "aos_orgid,aos_orgid.number p_ou_code",
				new QFilter[] { new QFilter("id", QCP.equals, fid) });
		String aos_orgid = dynamicObject.getString("aos_orgid");
		String p_ou_code = dynamicObject.getString("p_ou_code");
		// 获取fba数据
		Map<String, List<String>> fbaMap = getFbaMap(aos_orgid);
		Map<String, String> productIdMap = getProductIdMap(aos_orgid);
		Map<String, String> groupIdMap = getGroupIdMap(aos_orgid);
		Map<String, String> itemIdMap = getItemIdMap(aos_orgid);
		Map<String, String> portfolio = initSerialRoi(fid, aos_orgid);// 特殊广告
		Map<String, String> portid = initportid(fid, aos_orgid);// 特殊广告
		BigDecimal exRateWellSt = aos_mkt_popppc_init.getExRateLowSt(p_ou_code, "优");
		Map<String, String> comp = initSerialGroup(fid, aos_orgid, exRateWellSt);// 竞价策略

		// 初始化组数据
		HashMap<String, Map<String, Map<String, Object>>> Group = init_group(fid);
		// 循环单子下所有的产品号
		QFilter filter_id = new QFilter("id", "=", fid);
		QFilter[] filters = new QFilter[] { filter_id };
		String SelectColumn = "aos_entryentity.aos_productno aos_productno," + "aos_entryentity.aos_budget aos_budget,"
				+ "aos_entryentity.aos_makedate aos_makedate," + "aos_entryentity.aos_serialstatus aos_serialstatus";
		DataSet aos_mkt_popppc_initS = QueryServiceHelper.queryDataSet("aos_mkt_popppc_list." + fid,
				"aos_mkt_popular_ppc", SelectColumn, filters, "aos_entryentity.aos_productno");
		String[] GroupBy = new String[] { "aos_productno", "aos_budget", "aos_makedate", "aos_serialstatus" };
		aos_mkt_popppc_initS = aos_mkt_popppc_initS.groupBy(GroupBy).finish();
		while (aos_mkt_popppc_initS.hasNext()) {
			// 数据层
			int TopOfSearch = 0;// 用于存贮置顶位置出价的行
			Row aos_mkt_popppc_init = aos_mkt_popppc_initS.next();
			String aos_productno = aos_mkt_popppc_init.getString("aos_productno");
			BigDecimal aos_budget = aos_mkt_popppc_init.getBigDecimal("aos_budget").setScale(2,
					BigDecimal.ROUND_HALF_UP);
			Date aos_makedate = aos_mkt_popppc_init.getDate("aos_makedate");
			String aos_serialstatus = aos_mkt_popppc_init.getString("aos_serialstatus");
			// 判断是否新系列
			Boolean NewSerialFlag = false;
			String Operation = "";
			if (aos_makedate.compareTo(aos_date) == 0) {
				NewSerialFlag = true;
				Operation = "create";
			} else {
				NewSerialFlag = false;
				Operation = "update";
			}
			// 判断系列是否可用
			String SerialStatus;
			if ((aos_serialstatus + "").equals("AVAILABLE"))
				SerialStatus = "enabled";
			else
				SerialStatus = "paused";

			// 3.Campaign Id 如果系列为新增，ID直接等于系列名称
			Object campaignId ;
			if (NewSerialFlag)
				campaignId = aos_productno;
			else
				campaignId = productIdMap.get(aos_productno);

			// 每个系列下 创建三行数据
			// 系列第一行
			int row = sheet.getLastRowNum() + 1;
			XSSFRow ProductRow = sheet.createRow(row);
			CreateColumn(ProductRow, style, 0, "Sponsored Products");
			CreateColumn(ProductRow, style, 1, "Campaign");
			CreateColumn(ProductRow, style, 2, Operation);
			CreateColumn(ProductRow, style, 3, campaignId);
			if (FndGlobal.IsNotNull(portfolio.get(aos_productno)))
				CreateColumn(ProductRow, style, 5, portid.get(portfolio.get(aos_productno)));
			CreateColumn(ProductRow, style, 9, aos_productno);
			CreateColumn(ProductRow, style, 11, formatter.format(aos_makedate));
			CreateColumn(ProductRow, style, 13, "AUTO");
			CreateColumn(ProductRow, style, 14, SerialStatus);
			CreateColumn(ProductRow, style, 15, aos_budget);
			CreateColumn(ProductRow, style, 21, comp.get(aos_productno));
			// 系列第二行
			row = sheet.getLastRowNum() + 1;
			ProductRow = sheet.createRow(row);
			CreateColumn(ProductRow, style, 0, "Sponsored Products");
			CreateColumn(ProductRow, style, 1, "Bidding Adjustment");
			CreateColumn(ProductRow, style, 2, Operation);
			CreateColumn(ProductRow, style, 3, campaignId);
			CreateColumn(ProductRow, style, 21, "Dynamic bids - down only");
			CreateColumn(ProductRow, style, 22, "placementProductPage");
			CreateColumn(ProductRow, style, 23, "0.00");
			// 系列第三行
			row = sheet.getLastRowNum() + 1;
			TopOfSearch = row;
			ProductRow = sheet.createRow(row);
			CreateColumn(ProductRow, style, 0, "Sponsored Products");
			CreateColumn(ProductRow, style, 1, "Bidding Adjustment");
			CreateColumn(ProductRow, style, 2, Operation);
			CreateColumn(ProductRow, style, 3, campaignId);
			CreateColumn(ProductRow, style, 21, "Dynamic bids - down only");
			CreateColumn(ProductRow, style, 22, "placementTop");
			// 循环得到组
			Map<String, Map<String, Object>> GroupD = Group.get(aos_productno);
			Object aos_topprice = BigDecimal.ZERO;
			for (String aos_itemnumer : GroupD.keySet()) {
				Map<String, Object> GroupDmap = GroupD.get(aos_itemnumer);
				BigDecimal aos_bid = ((BigDecimal) GroupDmap.get("aos_bid")).setScale(2, BigDecimal.ROUND_HALF_UP);
				Object aos_shopsku = GroupDmap.get("aos_shopsku");
				Object aos_groupstatus = GroupDmap.get("aos_groupstatus");
				Object aos_groupdate = GroupDmap.get("aos_groupdate");
				aos_topprice = max((BigDecimal) GroupDmap.get("aos_topprice"), (BigDecimal) aos_topprice);

				String GroupStatus = "enabled";
				if ((aos_groupstatus + "").equals("AVAILABLE"))
					GroupStatus = "enabled";
				else
					GroupStatus = "paused";

				// 判断是否新组
				Boolean NewGroupFlag = false;
				String OperationGroup = "";
				if (((Date) aos_groupdate).compareTo(aos_date) == 0) {
					NewGroupFlag = true;
					OperationGroup = "create";
				} else {
					NewGroupFlag = false;
					OperationGroup = "update";
				}

				// 4.Ad Group Id 如果广告组为新增，ID直接等于广告组名称
				Object adGroupId;
				if (NewGroupFlag)
					adGroupId = aos_itemnumer;
				else
					adGroupId = groupIdMap.get(aos_productno + "~" + aos_itemnumer);

				// 每个组要创建六行数据
				// 组第一行
				row = sheet.getLastRowNum() + 1;
				ProductRow = sheet.createRow(row);
				CreateColumn(ProductRow, style, 0, "Sponsored Products");
				CreateColumn(ProductRow, style, 1, "Ad Group");
				CreateColumn(ProductRow, style, 2, OperationGroup);
				CreateColumn(ProductRow, style, 3, campaignId);
				CreateColumn(ProductRow, style, 4, adGroupId);
				CreateColumn(ProductRow, style, 10, aos_itemnumer);
				CreateColumn(ProductRow, style, 14, GroupStatus);
				CreateColumn(ProductRow, style, 17, aos_bid);

				// 组第二行
				// 判断是否fba
				int size = 1;
				boolean flag = false;
				List<String> fbaList = fbaMap.get(aos_itemnumer);
				if (fbaList != null && fbaList.size() > 0) {
					flag = true;
					size = fbaList.size();
					row = sheet.getLastRowNum() + 1;
					ProductRow = sheet.createRow(row);
					CreateColumn(ProductRow, style, 0, "Sponsored Products");
					CreateColumn(ProductRow, style, 1, "Product Ad");
					CreateColumn(ProductRow, style, 2, OperationGroup);
					CreateColumn(ProductRow, style, 3, campaignId);
					CreateColumn(ProductRow, style, 4, adGroupId);
					CreateColumn(ProductRow, style, 14, GroupStatus);
					CreateColumn(ProductRow, style, 16, aos_itemnumer);
				}

				for (int i = 0; i < size; i++) {
					row = sheet.getLastRowNum() + 1;
					ProductRow = sheet.createRow(row);
					CreateColumn(ProductRow, style, 0, "Sponsored Products");
					CreateColumn(ProductRow, style, 1, "Product Ad");
					CreateColumn(ProductRow, style, 2, OperationGroup);
					CreateColumn(ProductRow, style, 3, campaignId);
					CreateColumn(ProductRow, style, 4, adGroupId);
					CreateColumn(ProductRow, style, 14, GroupStatus);
					CreateColumn(ProductRow, style, 16, flag ? fbaList.get(i) : aos_shopsku);
					CreateColumn(ProductRow, style, 6, itemIdMap.getOrDefault(
							aos_productno + "~" + aos_itemnumer + "~" + (flag ? fbaList.get(i) : aos_shopsku), ""));
				}

				if (!NewGroupFlag) {
					// 组第三行
					BigDecimal bid = BigDecimal.ZERO;
					row = sheet.getLastRowNum() + 1;
					ProductRow = sheet.createRow(row);
					CreateColumn(ProductRow, style, 0, "Sponsored Products");
					CreateColumn(ProductRow, style, 1, "Product Targeting");
					CreateColumn(ProductRow, style, 2, OperationGroup);
					CreateColumn(ProductRow, style, 3, campaignId);
					CreateColumn(ProductRow, style, 4, adGroupId);
					CreateColumn(ProductRow, style, 14, GroupStatus);
					bid = aos_bid.multiply(BigDecimal.valueOf(1.3)).setScale(2, BigDecimal.ROUND_HALF_UP);
					CreateColumn(ProductRow, style, 18, bid);
					CreateColumn(ProductRow, style, 24, "close-match");
					// 组第四行
					row = sheet.getLastRowNum() + 1;
					ProductRow = sheet.createRow(row);
					CreateColumn(ProductRow, style, 0, "Sponsored Products");
					CreateColumn(ProductRow, style, 1, "Product Targeting");
					CreateColumn(ProductRow, style, 2, OperationGroup);
					CreateColumn(ProductRow, style, 3, campaignId);
					CreateColumn(ProductRow, style, 4, adGroupId);
					CreateColumn(ProductRow, style, 14, GroupStatus);
					bid = aos_bid.multiply(BigDecimal.valueOf(0.7)).setScale(2, BigDecimal.ROUND_HALF_UP);
					CreateColumn(ProductRow, style, 18, aos_bid);
					CreateColumn(ProductRow, style, 24, "loose-match");
					// 组第五行
					row = sheet.getLastRowNum() + 1;
					ProductRow = sheet.createRow(row);
					CreateColumn(ProductRow, style, 0, "Sponsored Products");
					CreateColumn(ProductRow, style, 1, "Product Targeting");
					CreateColumn(ProductRow, style, 2, OperationGroup);
					CreateColumn(ProductRow, style, 3, campaignId);
					CreateColumn(ProductRow, style, 4, adGroupId);
					CreateColumn(ProductRow, style, 14, GroupStatus);
					CreateColumn(ProductRow, style, 18, aos_bid);
					CreateColumn(ProductRow, style, 24, "complements");
					// 组第六行
					row = sheet.getLastRowNum() + 1;
					ProductRow = sheet.createRow(row);
					CreateColumn(ProductRow, style, 0, "Sponsored Products");
					CreateColumn(ProductRow, style, 1, "Product Targeting");
					CreateColumn(ProductRow, style, 2, OperationGroup);
					CreateColumn(ProductRow, style, 3, campaignId);
					CreateColumn(ProductRow, style, 4, adGroupId);
					CreateColumn(ProductRow, style, 14, GroupStatus);
					CreateColumn(ProductRow, style, 18, aos_bid);
					CreateColumn(ProductRow, style, 24, "substitutes");
				}
				// 对于置顶位置出价
				ProductRow = sheet.getRow(TopOfSearch);
				CreateColumn(ProductRow, style, 23, aos_topprice);
			}
		}
		aos_mkt_popppc_initS.close();
	}

	private Map<String, String> getItemIdMap(String aos_orgid) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_base_popid",
				"aos_productno,aos_itemnumer,aos_shopsku,aos_shopskuid",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid) });
		return list.stream()
				.collect(
						Collectors.toMap(
								obj -> obj.getString("aos_productno") + "~" + obj.getString("aos_itemnumer") + "~"
										+ obj.getString("aos_shopsku"),
								obj -> obj.getString("aos_shopskuid"), (k1, k2) -> k1));
	}

	private Map<String, String> getGroupIdMap(String aos_orgid) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_base_popid",
				"aos_productno,aos_itemnumer," + "aos_groupid",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid) });
		return list.stream()
				.collect(Collectors.toMap(obj -> obj.getString("aos_productno") + "~" + obj.getString("aos_itemnumer"),
						obj -> obj.getString("aos_groupid"), (k1, k2) -> k1));
	}

	private Map<String, String> getProductIdMap(String aos_orgid) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_base_popid", "aos_productno,aos_serialid",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid) });
		return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_productno"),
				obj -> obj.getString("aos_serialid"), (k1, k2) -> k1));
	}

	private XSSFRow CreateColumn(XSSFRow headRow, XSSFCellStyle style, int column, Object value) {
		XSSFCell headCell = headRow.createCell(column);
		headCell.setCellStyle(style);
		headCell.setCellValue(value + "");
		return headRow;
	}

	private String upload(String entityName, XSSFWorkbook workbook) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
		String fileName = entityName + sdf.format(new Date()) + ".xlsx";
		try {
			OutputStream outputStream = new ByteArrayOutputStream();
			workbook.write(outputStream);
			InputStream inputStream = parse(outputStream);
			TempFileCache tfc = CacheFactory.getCommonCacheFactory().getTempFileCache();
			int timeout = 60 * 30;// 超时时间半小时
			String path = tfc.saveAsUrl(fileName, inputStream, timeout);
			inputStream.close();
			return path;
		} catch (Exception e) {
		}
		return "";
	}

	private static HashMap<String, Map<String, Map<String, Object>>> init_group(String fid) {
		HashMap<String, Map<String, Map<String, Object>>> Group = new HashMap<>();
		// 循环得到组
		QFilter filter_id = new QFilter("id", "=", fid);
		QFilter[] filters = new QFilter[] { filter_id };
		String SelectField = "aos_entryentity.aos_productno aos_productno,"
				+ "aos_entryentity.aos_itemnumer aos_itemnumer," + "aos_entryentity.aos_bid aos_bid,"
				+ "aos_entryentity.aos_shopsku aos_shopsku," + "aos_entryentity.aos_groupstatus aos_groupstatus,"
				+ "aos_entryentity.aos_topprice aos_topprice," + "aos_entryentity.aos_groupdate aos_groupdate";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet("init_group." + fid, "aos_mkt_popular_ppc",
				SelectField, filters, "aos_entryentity.aos_itemnumer");
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			Map<String, Object> Detail = new HashMap<>();
			Detail.put("aos_bid", aos_mkt_popular_ppc.getBigDecimal("aos_bid").setScale(2, BigDecimal.ROUND_HALF_UP));
			Detail.put("aos_shopsku", aos_mkt_popular_ppc.get("aos_shopsku"));
			Detail.put("aos_groupstatus", aos_mkt_popular_ppc.get("aos_groupstatus"));
			Detail.put("aos_groupdate", aos_mkt_popular_ppc.get("aos_groupdate"));
			Detail.put("aos_topprice",
					aos_mkt_popular_ppc.getBigDecimal("aos_topprice").setScale(2, BigDecimal.ROUND_HALF_UP));
			Map<String, Map<String, Object>> Info = Group.get(aos_mkt_popular_ppc.getString("aos_productno"));
			if (Info == null)
				Info = new HashMap<>();
			String aos_itemnumer = aos_mkt_popular_ppc.getString("aos_itemnumer");
			Info.put(aos_itemnumer, Detail);
			Group.put(aos_mkt_popular_ppc.getString("aos_productno"), Info);
		}
		aos_mkt_popular_ppcS.close();
		return Group;
	}

	/*
	 * private static HashMap<String, Map<String, Map<String, Object>>>
	 * init_groupLast(Date aos_date) { HashMap<String, Map<String, Map<String,
	 * Object>>> Group = new HashMap<>(); // 循环得到组 Calendar calendar =
	 * Calendar.getInstance(); calendar.setTime(aos_date);
	 * calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0);
	 * calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0);
	 * calendar.add(Calendar.DAY_OF_MONTH, -1); Date date_to = calendar.getTime();
	 * SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
	 * Locale.US);// 日期格式化 String date_to_str = writeFormat.format(date_to); QFilter
	 * filter_date = new QFilter("aos_date", "=", date_to_str); QFilter[] filters =
	 * new QFilter[] { filter_date }; String SelectField =
	 * "aos_entryentity.aos_productno aos_productno," +
	 * "aos_entryentity.aos_itemnumer aos_itemnumer," +
	 * "aos_entryentity.aos_bid aos_bid," +
	 * "aos_entryentity.aos_shopsku aos_shopsku," +
	 * "aos_entryentity.aos_groupstatus aos_groupstatus"; DataSet
	 * aos_mkt_popular_ppcS =
	 * QueryServiceHelper.queryDataSet("init_group.init_groupLast",
	 * "aos_mkt_popular_ppc", SelectField, filters,
	 * "aos_entryentity.aos_itemnumer"); while (aos_mkt_popular_ppcS.hasNext()) {
	 * Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next(); Map<String, Object>
	 * Detail = new HashMap<>(); Detail.put("aos_bid",
	 * aos_mkt_popular_ppc.getBigDecimal("aos_bid").setScale(2,
	 * BigDecimal.ROUND_HALF_UP)); Detail.put("aos_shopsku",
	 * aos_mkt_popular_ppc.get("aos_shopsku")); Detail.put("aos_groupstatus",
	 * aos_mkt_popular_ppc.get("aos_groupstatus")); Map<String, Map<String, Object>>
	 * Info = Group.get(aos_mkt_popular_ppc.getString("aos_productno")); if (Info ==
	 * null) Info = new HashMap<>(); String aos_itemnumer =
	 * aos_mkt_popular_ppc.getString("aos_itemnumer"); Info.put(aos_itemnumer,
	 * Detail); Group.put(aos_mkt_popular_ppc.getString("aos_productno"), Info); }
	 * aos_mkt_popular_ppcS.close(); return Group; }
	 */

	private static BigDecimal max(BigDecimal Value1, BigDecimal Value2) {
		if (Value1.compareTo(Value2) > -1)
			return Value1;
		else
			return Value2;
	}

	// 获取fba信息
	private Map<String, List<String>> getFbaMap(String aos_orgid) {
		DynamicObjectCollection fbaObj = QueryServiceHelper.query("aos_base_fbaitem",
				"aos_itemid.number aos_itemnum,aos_shopsku",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid) });
		Map<String, List<DynamicObject>> aos_itemnum = fbaObj.stream()
				.collect(Collectors.groupingBy(obj -> obj.getString("aos_itemnum")));
		Map<String, List<String>> result = new HashMap<>();
		aos_itemnum.forEach((k, v) -> {
			List<String> aos_shopsku = v.stream().map(obj -> obj.getString("aos_shopsku")).collect(Collectors.toList());
			result.put(k, aos_shopsku);
		});
		return result;
	}

	/**
	 * 获取上期的调试数据，map key存的： 组别？物料 ; value ：调价
	 * 
	 * @param orgid 国别
	 * @param date  本单的日期
	 * @return
	 */
	private static Map<String, BigDecimal> getLastPeriodData(String orgid, Date date) {
		Map<String, BigDecimal> map_re = new HashMap<>();
		try {
			List<QFilter> list_qf = new ArrayList<>();
			Calendar cal = new GregorianCalendar();
			SimpleDateFormat sim_d = new SimpleDateFormat("yyyy-MM-dd");
			cal.setTime(sim_d.parse(sim_d.format(date)));
			QFilter qf_date_max = new QFilter("aos_date", "<", cal.getTime());
			list_qf.add(qf_date_max);
			cal.add(Calendar.DATE, -1);
			QFilter qf_date_min = new QFilter("aos_date", ">=", cal.getTime());
			list_qf.add(qf_date_min);
			QFilter qf_org = new QFilter("aos_orgid", "=", orgid);
			list_qf.add(qf_org);
			// 组状态
			QFilter qf_state = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE");
			list_qf.add(qf_state);

			QFilter[] qfs = list_qf.toArray(new QFilter[list_qf.size()]);
			StringJoiner strjoin = new StringJoiner(",");
			strjoin.add("aos_entryentity.aos_itemnumer as aos_itemnumer");
			strjoin.add("aos_entryentity.aos_itemid as aos_itemid");
			strjoin.add("aos_entryentity.aos_bid as aos_bid");
			DynamicObjectCollection dyc_data = QueryServiceHelper.query("aos_mkt_popular_ppc", strjoin.toString(), qfs);
			StringBuilder strbuilder = new StringBuilder();
			dyc_data.stream().forEach(e -> {
				strbuilder.append(e.getString("aos_itemnumer"));
				strbuilder.append("?");
				strbuilder.append(e.getString("aos_itemid"));
				map_re.put(strbuilder.toString(), e.getBigDecimal("aos_bid"));
				strbuilder.setLength(0);
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
		return map_re;
	}

	/** 获取该国别下，所有的爆品的sku **/
	private static List<String> getSaleItemByOu(Object orgid) {
		// 本国别，且为爆品
		QFilter filter = new QFilter("aos_contryentry.aos_nationality", "=", orgid);
		filter = filter.and(new QFilter("aos_contryentry.aos_is_saleout", "=", "1"));

		ItemDao itemDao = new ItemDaoImpl();
		return itemDao.listItemObj("number", filter, null).stream().map(dy -> dy.getString("number")).distinct()
				.collect(Collectors.toList());
	}
}