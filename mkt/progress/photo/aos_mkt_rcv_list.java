package mkt.progress.photo;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import kd.bos.entity.datamodel.ListSelectedRowCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.CloseCallBack;
import kd.bos.form.FormShowParameter;
import kd.bos.form.IFormView;
import kd.bos.form.ShowType;
import mkt.common.MKTCom;
import mkt.progress.ProgressUtil;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.fnd.FndMsg;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.cache.CacheFactory;
import kd.bos.cache.TempFileCache;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.progress.iface.parainfo;

public class aos_mkt_rcv_list extends AbstractListPlugin {

	public final static String aos_mkt_rcv = "aos_mkt_rcv";

	@Override
	public void setFilter(SetFilterEvent e) {
		List<QFilter> qFilters = e.getQFilters();
		parainfo.setRights(qFilters, this.getPageCache(), aos_mkt_rcv);
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String itemKey = evt.getItemKey();
		try {
			if (StringUtils.equals("aos_submit", evt.getItemKey()))
				aos_submit();// 批量提交
			else if (StringUtils.equals("aos_print", evt.getItemKey()))
				aos_print();// 批量打印
			else if (StringUtils.equals("aos_test", evt.getItemKey()))
				aos_test();
			else if ("aos_showclose".equals(itemKey))
				parainfo.showClose(this.getView());// 查询关闭流程
			else if ("aos_batchgive".equals(itemKey))
				aos_open();// 批量转办
			else if ("aos_querysample".equals(itemKey))
				querySample();// 查看封样图片
			else if ("aos_track".equals(itemKey))
				aos_track();// 批量修改快递单号
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	private void aos_track() {
		FndMsg.debug("=====into aos_track=====");
		Map<String, Object> params = new HashMap<>();
		params.put("list",  this.getSelectedRows());
		FndGlobal.OpenForm(this, "aos_mkt_rcvtrack", params);
	}

	/**
	 * 查看封样图片
	 */
	private void querySample() throws FndError {
		try {
			ListSelectedRowCollection selectedRows = this.getSelectedRows();
			int size = selectedRows.size();
			if (size != 1) {
				this.getView().showTipNotification("请先选择单条数据查询!");
			} else {
				aos_mkt_rcv_bill.querySample(this.getView(), selectedRows.get(0).getPrimaryKeyValue());
			}
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	/** 弹出转办框 **/
	private void aos_open() {
		FormShowParameter showParameter = new FormShowParameter();
		showParameter.setFormId("aos_mkt_progive");
		showParameter.getOpenStyle().setShowType(ShowType.Modal);
		showParameter.setCloseCallBack(new CloseCallBack(this, "form"));
		this.getView().showForm(showParameter);
	}

	/** 回调事件 **/
	public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
		String actionId = closedCallBackEvent.getActionId();
		if (StringUtils.equals(actionId, "form")) {
			Object map = closedCallBackEvent.getReturnData();
			if (map == null)
				return;
			@SuppressWarnings("unchecked")
			Object aos_user = ((Map<String, Object>) map).get("aos_user");
			aos_batchgive(aos_user);
		} else if (StringUtils.equals(actionId, "aos_mkt_rcvtrack")) {
			this.getView().invokeOperation("refresh");// 刷新列表
			this.getView().showSuccessNotification("批量修改快递单号成功");
		}
	}

	private void aos_batchgive(Object aos_user) {
		List<Object> list = getSelectedRows().stream().map(row -> row.getPrimaryKeyValue()).distinct()
				.collect(Collectors.toList());
		Object CurrentUserId = UserServiceHelper.getCurrentUserId();
		Object CurrentUserName = UserServiceHelper.getUserInfoByID((long) CurrentUserId).get("name");
		for (int i = 0; i < list.size(); i++) {
			String id = list.get(i).toString();
			DynamicObject aos_mkt_rcv = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_rcv");
			String aos_userold = aos_mkt_rcv.getDynamicObject("aos_user").getPkValue().toString();
			String billno = aos_mkt_rcv.getString("billno");
			String aos_status = aos_mkt_rcv.getString("aos_status");

			if (!"新建".equals(aos_status)) {
				this.getView().showTipNotification(billno + "只允许转办新建状态的单据!");
				return;
			}

			if (!(CurrentUserId + "").equals(aos_userold)) {
				this.getView().showTipNotification(billno + "只允许转办自己的单据!");
				return;
			}
			aos_mkt_rcv.set("aos_user", aos_user);
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_rcv",
					new DynamicObject[] { aos_mkt_rcv }, OperateOption.create());
			MKTCom.SendGlobalMessage(((DynamicObject) aos_user).getPkValue() + "", aos_mkt_rcv + "",
					operationrst.getSuccessPkIds().get(0) + "", billno, CurrentUserName + "流程转办!");
			FndHistory fndHistory = new FndHistory();
			fndHistory.SetActionBy(CurrentUserId);
			fndHistory.SetFormId("aos_mkt_rcv");
			fndHistory.SetSourceId(id);
			fndHistory.SetActionCode("流程转办");// 操作动作
			fndHistory.SetDesc(CurrentUserName + "流程转办!"); // 操作备注
			Cux_Common_Utl.History(fndHistory);// 插入历史记录;
		}

		this.getView().showSuccessNotification("转办成功");
		this.getView().invokeOperation("refresh");// 刷新列表
	}

	private void aos_test() {
		aos_mkt_progphreq_sync.do_operate(null);
	}

	private XSSFWorkbook GenWorkBook(Row aos_mkt_rcv, QFilter filter_id) throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);// 日期格式化
		try {
			int TotalColumn = 11;// 总列数
			XSSFSheet sheet = workbook.createSheet();
			sheet.setDefaultColumnWidth(10);// 默认宽度
			sheet.setDefaultRowHeightInPoints(15);
			sheet.setColumnWidth(4, 2000);
			sheet.setColumnWidth(5, 2000);
			sheet.setColumnWidth(6, 2000);
			sheet.setColumnWidth(7, 2000);
			sheet.setColumnWidth(8, 2000);

			// 通用风格
			XSSFCellStyle styletitle = Cux_Common_Utl.GetStyleTitle(workbook);// 标题风格
			XSSFCellStyle stylerowCenter = GetStyleRowCenter(workbook);// 行风格居中
			XSSFCellStyle stylehead = GetStyleHead(workbook);// 表头风格
			XSSFCellStyle styletitleleft = GetStyleTitleLeft(workbook);
			XSSFCellStyle styleheadblank = GetStyleHeadBlank(workbook);
			XSSFCellStyle stylerow = Cux_Common_Utl.GetStyleRow(workbook);// 行风格

			Object aos_vendor = aos_mkt_rcv.get("aos_vendor");
			Object aos_ponumber = aos_mkt_rcv.get("aos_ponumber");
			Object aos_phstate = aos_mkt_rcv.get("aos_phstate");
			Object aos_address = aos_mkt_rcv.get("aos_address");
			QFilter filter_vendor = new QFilter("aos_vendor", QCP.equals, aos_vendor);
			QFilter filter_ponumber = new QFilter("aos_ponumber", QCP.equals, aos_ponumber);
			QFilter filter_phstate = new QFilter("aos_phstate", QCP.equals, aos_phstate);
			QFilter filter_address = new QFilter("aos_address", QCP.equals, aos_address);
			QFilter[] filters_in = { filter_id, filter_vendor, filter_ponumber, filter_phstate, filter_address };
			String SelectColumn = "aos_requireby.name aos_requireby,aos_requiredate,billno,aos_vendor,aos_ponumber,aos_address,aos_contact,aos_contactway,aos_returnadd,"
					+ "aos_itemid.number aos_itemid,aos_itemname,aos_boxqty,aos_photo,aos_vedio,"
					+ "aos_3d,aos_sample,aos_other,aos_protype,aos_developer.name aos_developer";
			DataSet aos_mkt_rcvInS = QueryServiceHelper.queryDataSet("aos_mkt_rcv_list.aos_mkt_rcvInS", "aos_mkt_rcv",
					SelectColumn, filters_in, null);
			int i = 0;
			while (aos_mkt_rcvInS.hasNext()) {
				i++;
				Row aos_mkt_rcvIn = aos_mkt_rcvInS.next();
				int row = 0;
				if (i == 1) {
					// 初始化默认头行 不需要处理
					GenerateSheetDefualt(sheet, styletitle, stylehead, styletitleleft, styleheadblank, TotalColumn,
							aos_mkt_rcvIn.getString("aos_address"));
					row = sheet.getLastRowNum();
					// 批量赋值头数据
					sheet.getRow(row - 5).getCell(1).setCellValue(aos_mkt_rcvIn.getString("aos_requireby"));// 申请人
					sheet.getRow(row - 5).getCell(4)
							.setCellValue(writeFormat.format(aos_mkt_rcvIn.getDate("aos_requiredate")));// 申请日期
					sheet.getRow(row - 5).getCell(7).setCellValue(aos_mkt_rcvIn.getString("billno"));// 单号
					sheet.getRow(row - 4).getCell(1).setCellValue(aos_mkt_rcvIn.getString("aos_vendor"));// 供应商
					sheet.getRow(row - 4).getCell(4).setCellValue(aos_mkt_rcvIn.getString("aos_ponumber"));// 合同号
					sheet.getRow(row - 4).getCell(7).setCellValue(aos_mkt_rcvIn.getString("aos_address"));// 样品接收地址
					sheet.getRow(row - 3).getCell(1).setCellValue(aos_mkt_rcvIn.getString("aos_contact"));// 退件联系人
					sheet.getRow(row - 3).getCell(4).setCellValue(aos_mkt_rcvIn.getString("aos_contactway"));// 退件联系方式
					sheet.getRow(row - 3).getCell(7).setCellValue(aos_mkt_rcvIn.getString("aos_returnadd"));// 退件地址
				} else
					row = sheet.getLastRowNum();
				// 批量赋值行数据
				row = sheet.getLastRowNum() + 1;
				XSSFRow headRow = sheet.createRow(row);
				headRow = sheet.createRow(row);

				// 口
				XSSFCell headCell = headRow.createCell(0);
				headCell.setCellStyle(stylerowCenter);
				headCell.setCellValue("□");
				// 序号
				headCell = headRow.createCell(1);
				headCell.setCellStyle(stylerowCenter);
				headCell.setCellValue(i);
				// 货号
				headCell = headRow.createCell(2);
				headCell.setCellStyle(stylerow);
				headCell.setCellValue(aos_mkt_rcvIn.getString("aos_itemid"));
				// 品名
				headCell = headRow.createCell(3);
				headCell.setCellStyle(stylerow);
				headCell.setCellValue(aos_mkt_rcvIn.getString("aos_itemname"));
				// 分箱数
				headCell = headRow.createCell(4);
				headCell.setCellStyle(stylerowCenter);
				headCell.setCellValue(aos_mkt_rcvIn.getString("aos_boxqty"));

				// 用途
				// 拍照
				headCell = headRow.createCell(5);
				headCell.setCellStyle(stylerowCenter);
				if (aos_mkt_rcvIn.getBoolean("aos_photo"))
					headCell.setCellValue("√");
				else
					headCell.setCellValue("");
				// 拍视频
				headCell = headRow.createCell(6);
				headCell.setCellStyle(stylerowCenter);
				if (aos_mkt_rcvIn.getBoolean("aos_vedio"))
					headCell.setCellValue("√");
				else
					headCell.setCellValue("");
				// 3D建模
				headCell = headRow.createCell(7);
				headCell.setCellStyle(stylerowCenter);
				if (aos_mkt_rcvIn.getBoolean("aos_3d"))
					headCell.setCellValue("√");
				else
					headCell.setCellValue("");
				// 封样
				headCell = headRow.createCell(8);
				headCell.setCellStyle(stylerowCenter);
				if (aos_mkt_rcvIn.getBoolean("aos_sample"))
					headCell.setCellValue("√");
				else
					headCell.setCellValue("");
				// 其他
				headCell = headRow.createCell(9);
				headCell.setCellStyle(stylerowCenter);
				if (aos_mkt_rcvIn.getBoolean("aos_other"))
					headCell.setCellValue("√");
				else
					headCell.setCellValue("");

				// 样品处理方式
				headCell = headRow.createCell(10);
				headCell.setCellStyle(stylerowCenter);
				headCell.setCellValue(aos_mkt_rcvIn.getString("aos_protype"));

				// 开发员
				headCell = headRow.createCell(11);
				headCell.setCellStyle(stylerowCenter);
				headCell.setCellValue(aos_mkt_rcvIn.getString("aos_developer"));
			}

			// 结束 创建间隔行
			int row = sheet.getLastRowNum() + 1;
			XSSFRow headRow = sheet.createRow(row);
			headRow.setHeightInPoints(50);
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
		return workbook;
	}

	private XSSFCellStyle GetStyleRowCenter(XSSFWorkbook workbook) {
		XSSFCellStyle stylerow = workbook.createCellStyle();
		stylerow.setAlignment(HorizontalAlignment.CENTER);
		stylerow.setBorderLeft(BorderStyle.THIN);
		stylerow.setBorderRight(BorderStyle.THIN);
		stylerow.setBorderTop(BorderStyle.THIN);
		stylerow.setBorderBottom(BorderStyle.THIN);
		// stylerow.setWrapText(true);
		return stylerow;
	}

	private void GenerateSheetDefualt(XSSFSheet sheet, XSSFCellStyle styletitle, XSSFCellStyle stylehead,
			XSSFCellStyle styletitleleft, XSSFCellStyle styleheadblank, int TotalColumn, String aos_address) {
		// 每组创建默认业务实体抬头
		int row = sheet.getLastRowNum() + 1;
		XSSFRow headRow = sheet.createRow(row);
		headRow.setHeightInPoints(40);
		XSSFCell headCell = headRow.createCell(0);
		headCell.setCellValue("遨森电子商务股份有限公司\r\n" + "AOSOM INTERNATIONAL DEVELOPMENT CO.,LIMITED");
		headCell.setCellStyle(styletitleleft);

		row = sheet.getLastRowNum() + 1;
		sheet.createRow(row);
		sheet.addMergedRegion(new CellRangeAddress(row - 1, row, 0, TotalColumn));
		// 创建入库通知单标题
		row = sheet.getLastRowNum() + 1;
		headRow = sheet.createRow(row);
		headCell = headRow.createCell(0);
		headCell.setCellValue("样品入库通知单");
		headCell.setCellStyle(styletitle);
		row = sheet.getLastRowNum() + 1;
		sheet.createRow(row);
		sheet.addMergedRegion(new CellRangeAddress(row - 1, row, 0, TotalColumn));
		// 创建提示行1
		row = sheet.getLastRowNum() + 1;
		headRow = sheet.createRow(row);
		headCell = headRow.createCell(0);
		headCell.setCellValue("请在外箱上备注货号；请不要寄到付件！");
		headCell.setCellStyle(styletitleleft);
		row = sheet.getLastRowNum() + 1;
		sheet.createRow(row);
		sheet.addMergedRegion(new CellRangeAddress(row - 1, row, 0, TotalColumn));
		// 创建提示行2
		row = sheet.getLastRowNum() + 1;
		headRow = sheet.createRow(row);
		headRow.setHeightInPoints(40);
		headCell = headRow.createCell(0);
		headCell.setCellValue(aos_address);
		headCell.setCellStyle(styletitleleft);
		row = sheet.getLastRowNum() + 1;
		sheet.createRow(row);
		sheet.addMergedRegion(new CellRangeAddress(row - 1, row, 0, TotalColumn));
		// 创建头信息行1
		row = sheet.getLastRowNum() + 1;
		headRow = sheet.createRow(row);
		// 申请人
		headCell = headRow.createCell(0);
		headCell.setCellValue("申请人");
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(1);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(2);
		headCell.setCellStyle(styleheadblank);
		sheet.addMergedRegion(new CellRangeAddress(row, row, 1, 2));
		// 申请日期
		headCell = headRow.createCell(3);
		headCell.setCellValue("申请日期");
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(4);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(5);
		headCell.setCellStyle(styleheadblank);
		sheet.addMergedRegion(new CellRangeAddress(row, row, 4, 5));
		// 单号
		headCell = headRow.createCell(6);
		headCell.setCellValue("单号");
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(7);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(8);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(9);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(10);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(11);
		headCell.setCellStyle(styleheadblank);
		sheet.addMergedRegion(new CellRangeAddress(row, row, 7, 11));
		// 创建头信息行2
		row = sheet.getLastRowNum() + 1;
		headRow = sheet.createRow(row);
		headRow.setHeightInPoints(30);
		// 供应商
		headCell = headRow.createCell(0);
		headCell.setCellValue("供应商");
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(1);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(2);
		headCell.setCellStyle(styleheadblank);
		// 合同号
		headCell = headRow.createCell(3);
		headCell.setCellValue("合同号");
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(4);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(5);
		headCell.setCellStyle(styleheadblank);
		// 样品接收地址
		headCell = headRow.createCell(6);
		headCell.setCellValue("样品接收地址");
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(7);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(8);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(9);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(10);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(11);
		headCell.setCellStyle(styleheadblank);
		// 创建头信息行3
		row = sheet.getLastRowNum() + 1;
		headRow = sheet.createRow(row);
		headRow.setHeightInPoints(30);
		// 退件联系人
		headCell = headRow.createCell(0);
		headCell.setCellValue("退件联系人");
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(1);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(2);
		headCell.setCellStyle(styleheadblank);
		// 退件联系方式
		headCell = headRow.createCell(3);
		headCell.setCellValue("退件联系方式");
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(4);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(5);
		headCell.setCellStyle(styleheadblank);
		// 退件地址
		headCell = headRow.createCell(6);
		headCell.setCellValue("退件地址");
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(7);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(8);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(9);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(10);
		headCell.setCellStyle(styleheadblank);
		headCell = headRow.createCell(11);
		headCell.setCellStyle(styleheadblank);

		// 合并
		sheet.addMergedRegion(new CellRangeAddress(row - 1, row - 1, 1, 2));
		sheet.addMergedRegion(new CellRangeAddress(row - 1, row - 1, 4, 5));
		sheet.addMergedRegion(new CellRangeAddress(row - 1, row - 1, 7, 11));
		sheet.addMergedRegion(new CellRangeAddress(row, row, 1, 2));
		sheet.addMergedRegion(new CellRangeAddress(row, row, 4, 5));
		sheet.addMergedRegion(new CellRangeAddress(row, row, 7, 11));

		// 头行间隔行
		row = sheet.getLastRowNum() + 1;
		headRow = sheet.createRow(row);

		// 行标题行1
		row = sheet.getLastRowNum() + 1;
		headRow = sheet.createRow(row);
		headRow.setHeightInPoints(15);

		// 口
		headCell = headRow.createCell(0);
		headCell.setCellValue("□");
		headCell.setCellStyle(stylehead);
		// 序号
		headCell = headRow.createCell(1);
		headCell.setCellValue("序号");
		headCell.setCellStyle(stylehead);
		// 货号
		headCell = headRow.createCell(2);
		headCell.setCellValue("货号");
		headCell.setCellStyle(stylehead);
		// 品名
		headCell = headRow.createCell(3);
		headCell.setCellValue("品名");
		headCell.setCellStyle(stylehead);
		// 分箱数
		headCell = headRow.createCell(4);
		headCell.setCellValue("分箱数");
		headCell.setCellStyle(stylehead);
		// 用途
		headCell = headRow.createCell(5);
		headCell.setCellValue("用途");
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(6);
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(7);
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(8);
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(9);
		headCell.setCellStyle(stylehead);
		// 样品处理方式
		headCell = headRow.createCell(10);
		headCell.setCellValue("样品处理方式");
		headCell.setCellStyle(stylehead);
		// 开发员
		headCell = headRow.createCell(11);
		headCell.setCellValue("开发员");
		headCell.setCellStyle(stylehead);
		// 行标题行2
		row = sheet.getLastRowNum() + 1;
		headRow = sheet.createRow(row);
		headRow.setHeightInPoints(15);
		// 口
		headCell = headRow.createCell(0);
		headCell.setCellStyle(stylehead);
		// 序号
		headCell = headRow.createCell(1);
		headCell.setCellStyle(stylehead);
		// 货号
		headCell = headRow.createCell(2);
		headCell.setCellStyle(stylehead);
		// 品名
		headCell = headRow.createCell(3);
		headCell.setCellStyle(stylehead);
		// 分箱数
		headCell = headRow.createCell(4);
		headCell.setCellStyle(stylehead);
		// 用途
		headCell = headRow.createCell(5);
		headCell.setCellValue("拍照");
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(6);
		headCell.setCellValue("拍视频");
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(7);
		headCell.setCellValue("3D建模");
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(8);
		headCell.setCellValue("封样");
		headCell.setCellStyle(stylehead);
		headCell = headRow.createCell(9);
		headCell.setCellValue("其他");
		headCell.setCellStyle(stylehead);
		// 样品处理方式
		headCell = headRow.createCell(10);
		headCell.setCellStyle(stylehead);
		// 用途
		headCell = headRow.createCell(11);
		headCell.setCellStyle(stylehead);

		// 行标题合并
		sheet.addMergedRegion(new CellRangeAddress(row - 1, row, 0, 0));
		sheet.addMergedRegion(new CellRangeAddress(row - 1, row, 1, 1));
		sheet.addMergedRegion(new CellRangeAddress(row - 1, row, 2, 2));
		sheet.addMergedRegion(new CellRangeAddress(row - 1, row, 3, 3));
		sheet.addMergedRegion(new CellRangeAddress(row - 1, row, 4, 4));
		sheet.addMergedRegion(new CellRangeAddress(row - 1, row, 10, 10));
		sheet.addMergedRegion(new CellRangeAddress(row - 1, row, 11, 11));
		sheet.addMergedRegion(new CellRangeAddress(row - 1, row - 1, 5, 9));

	}

	private XSSFCellStyle GetStyleHead(XSSFWorkbook workbook) {
		XSSFCellStyle stylehead = workbook.createCellStyle();
		stylehead.setAlignment(HorizontalAlignment.CENTER);
		stylehead.setVerticalAlignment(VerticalAlignment.CENTER);
		stylehead.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		stylehead.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
		stylehead.setBorderLeft(BorderStyle.THIN);
		stylehead.setBorderRight(BorderStyle.THIN);
		stylehead.setBorderTop(BorderStyle.THIN);
		stylehead.setBorderBottom(BorderStyle.THIN);
		stylehead.setWrapText(true);
		return stylehead;
	}

	private XSSFCellStyle GetStyleHeadBlank(XSSFWorkbook workbook) {
		XSSFCellStyle stylehead = workbook.createCellStyle();
		stylehead.setAlignment(HorizontalAlignment.CENTER);
		stylehead.setVerticalAlignment(VerticalAlignment.CENTER);
		stylehead.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		stylehead.setFillForegroundColor(IndexedColors.WHITE.getIndex());
		stylehead.setBorderLeft(BorderStyle.THIN);
		stylehead.setBorderRight(BorderStyle.THIN);
		stylehead.setBorderTop(BorderStyle.THIN);
		stylehead.setBorderBottom(BorderStyle.THIN);
		stylehead.setWrapText(true);
		return stylehead;
	}

	private XSSFCellStyle GetStyleTitleLeft(XSSFWorkbook workbook) {
		XSSFCellStyle styletitle = workbook.createCellStyle();
		styletitle.setAlignment(HorizontalAlignment.LEFT);
		styletitle.setVerticalAlignment(VerticalAlignment.CENTER);
		styletitle.setWrapText(true);
		XSSFFont font = workbook.createFont();
		font.setBold(true);
		font.setFontHeight(15);
		font.setFontName("微软雅黑");
		styletitle.setFont(font);
		return styletitle;
	}

	private void aos_print() {
		try {

			List<ListSelectedRow> list = getSelectedRows();
			int size = list.size();
			if (size == 0)
				throw new Exception("SelectError");
			List<String> ListQuery = getSelectedRows().stream().map(row -> row.getPrimaryKeyValue().toString())
					.distinct().collect(Collectors.toList());
			QFilter filter_id = new QFilter("id", QCP.in, ListQuery);
			QFilter[] filters = { filter_id };
			String SelectColumn = "aos_vendor,aos_ponumber,aos_phstate,aos_address";
			DataSet aos_mkt_rcvS = QueryServiceHelper.queryDataSet("aos_mkt_rcv_list.aos_print", "aos_mkt_rcv",
					SelectColumn, filters, null);
			String[] GroupBy = new String[] { "aos_vendor", "aos_ponumber", "aos_phstate", "aos_address" };
			aos_mkt_rcvS = aos_mkt_rcvS.groupBy(GroupBy).finish();
			while (aos_mkt_rcvS.hasNext()) {
				Row aos_mkt_rcv = aos_mkt_rcvS.next();
				this.getView()
						.download(ComPrtUpload(aos_mkt_rcv.getString("aos_vendor") + "-"
								+ aos_mkt_rcv.getString("aos_phstate") + "-样品入库通知单.xlsx",
								GenWorkBook(aos_mkt_rcv, filter_id)));
			}
			aos_mkt_rcvS.close();
		} catch (Exception e) {
			if ("SelectError".equals(e.getMessage()))
				this.getView().showErrorNotification("请选择一条数据打印!");
			else
				this.getView().showErrorNotification(e.getMessage());
		}
	}

	private void aos_submit() throws FndError {
		try {
			ListSelectedRowCollection selectedRows = this.getSelectedRows();
			int size = selectedRows.size();
			if (size == 0) {
				this.getView().showTipNotification("请先选择提交数据");
			} else {
				IFormView view = this.getView();
				String message = ProgressUtil.submitEntity(view, selectedRows);
				if (message != null && message.length() > 0) {
					message = "提交成功,部分无权限单据无法提交：  " + message;
				} else
					message = "提交成功";
				this.getView().updateView();
				this.getView().showSuccessNotification(message);
			}
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	public static String ComPrtUpload(String filename, XSSFWorkbook workbook) {
		String fileName = filename;
		try {
			OutputStream outputStream = new ByteArrayOutputStream();
			workbook.write(outputStream);
			InputStream inputStream = Cux_Common_Utl.ComPrtparse(outputStream);
			TempFileCache tfc = CacheFactory.getCommonCacheFactory().getTempFileCache();
			int timeout = 60 * 30;// 超时时间半小时
			String path = tfc.saveAsUrl(fileName, inputStream, timeout);
			inputStream.close();
			return path;
		} catch (Exception e) {
		}
		return "";
	}

}