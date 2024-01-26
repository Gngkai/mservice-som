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
import kd.bos.form.*;
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
import mkt.progress.iface.ParaInfoUtil;

/**
 * @author aosom
 * @version 样品入库通知单-列表插件
 */
public class AosMktRcvList extends AbstractListPlugin {

    public final static String AOS_MKT_RCV = "aos_mkt_rcv";
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    public final static String AOS_SUBMIT = "aos_submit";
    public final static String AOS_PRINT = "aos_print";
    public final static String AOS_TEST = "aos_test";
    public final static String AOS_SHOWCLOSE = "aos_showclose";
    public final static String AOS_SHOWOPEN = "aos_showopen";
    public final static String AOS_SHOWALL = "aos_showall";
    public final static String AOS_BATCHGIVE = "aos_batchgive";
    public final static String AOS_QUERYSAMPLE = "aos_querysample";
    public final static String AOS_TRACK = "aos_track";
    public final static String AOS_CLOSE = "aos_close";
    public final static String ZX = "赵轩";
    public final static String FORM = "form";
    public final static String AOS_MKT_RCVTRACK = "aos_mkt_rcvtrack";
    public final static String SELECTERROR = "SelectError";

    public static String comPrtUpload(String filename, XSSFWorkbook workbook) {
        try {
            OutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            InputStream inputStream = Cux_Common_Utl.ComPrtparse(outputStream);
            TempFileCache tfc = CacheFactory.getCommonCacheFactory().getTempFileCache();
            // 超时时间半小时
            int timeout = 60 * 30;
            String path = tfc.saveAsUrl(filename, inputStream, timeout);
            inputStream.close();
            return path;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public void setFilter(SetFilterEvent e) {
        List<QFilter> qFilters = e.getQFilters();
        ParaInfoUtil.setRights(qFilters, this.getPageCache(), AOS_MKT_RCV);
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String itemKey = evt.getItemKey();
        try {
            if (StringUtils.equals(AOS_SUBMIT, evt.getItemKey())) {
                // 批量提交
                aosSubmit();
            } else if (StringUtils.equals(AOS_PRINT, evt.getItemKey())) {
                // 批量打印
                aosPrint();
            } else if (StringUtils.equals(AOS_TEST, evt.getItemKey())) {
                aosTest();
            } else if (AOS_SHOWCLOSE.equals(itemKey)) {
                // 查询关闭流程
                IPageCache iPageCache = this.getView().getPageCache();
                iPageCache.put("p_close_flag", "true");
                this.getView().invokeOperation("refresh");
            } else if (AOS_SHOWOPEN.equals(itemKey)) {
                // 查询未关闭流程
                IPageCache iPageCache = this.getView().getPageCache();
                iPageCache.put("p_close_flag", "false");
                this.getView().invokeOperation("refresh");
            } else if (AOS_SHOWALL.equals(itemKey)) {
                // 查询全部流程
                IPageCache iPageCache = this.getView().getPageCache();
                iPageCache.put("p_close_flag", "all");
                this.getView().invokeOperation("refresh");
            } else if (AOS_BATCHGIVE.equals(itemKey)) {
                // 批量转办
                aosOpen();
            } else if (AOS_QUERYSAMPLE.equals(itemKey)) {
                // 查看封样图片
                querySample();
            } else if (AOS_TRACK.equals(itemKey)) {
                // 批量修改快递单号
                aosTrack();
            } else if (AOS_CLOSE.equals(itemKey)) {
                aosClose();
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    /**
     * 批量关闭
     */
    private void aosClose() {
        List<Object> list =
            getSelectedRows().stream().map(ListSelectedRow::getPrimaryKeyValue).distinct().collect(Collectors.toList());
        Object currentUserId = UserServiceHelper.getCurrentUserId();
        Object currentUserName = UserServiceHelper.getUserInfoByID((long)currentUserId).get("name");
        if (!ZX.equals(currentUserName)) {
            this.getView().showErrorNotification("无关闭权限,请联系管理员!");
            return;
        }
        for (Object o : list) {
            String id = o.toString();
            DynamicObject aosMktRcv = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_rcv");
            aosMktRcv.set("aos_status", "已完成");
            aosMktRcv.set("aos_user", SYSTEM);
            OperationServiceHelper.executeOperate("save", "aos_mkt_rcv", new DynamicObject[] {aosMktRcv},
                OperateOption.create());
            FndHistory fndHistory = new FndHistory();
            fndHistory.SetActionBy(currentUserId);
            fndHistory.SetFormId("aos_mkt_rcv");
            fndHistory.SetSourceId(id);
            fndHistory.SetActionCode("手工关闭");
            fndHistory.SetDesc(currentUserName + "手工关闭!");
            Cux_Common_Utl.History(fndHistory);
        }
        this.getView().showSuccessNotification("关闭成功");
        this.getView().invokeOperation("refresh");
    }

    private void aosTrack() {
        FndMsg.debug("=====into aos_track=====");
        Map<String, Object> params = new HashMap<>(16);
        params.put("list", this.getSelectedRows());
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
                AosMktRcvBill.querySample(this.getView(), selectedRows.get(0).getPrimaryKeyValue());
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    /** 弹出转办框 **/
    private void aosOpen() {
        FormShowParameter showParameter = new FormShowParameter();
        showParameter.setFormId("aos_mkt_progive");
        showParameter.getOpenStyle().setShowType(ShowType.Modal);
        showParameter.setCloseCallBack(new CloseCallBack(this, "form"));
        this.getView().showForm(showParameter);
    }

    /** 回调事件 **/
    @Override
    public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
        String actionId = closedCallBackEvent.getActionId();
        if (StringUtils.equals(actionId, FORM)) {
            Object map = closedCallBackEvent.getReturnData();
            if (map == null) {
                return;
            }
            @SuppressWarnings("unchecked")
            Object aosUser = ((Map<String, Object>)map).get("aos_user");
            aosBatchgive(aosUser);
        } else if (StringUtils.equals(actionId, AOS_MKT_RCVTRACK)) {
            this.getView().invokeOperation("refresh");
            this.getView().showSuccessNotification("批量修改快递单号成功");
        }
    }

    private void aosBatchgive(Object aosUser) {
        List<Object> list =
            getSelectedRows().stream().map(ListSelectedRow::getPrimaryKeyValue).distinct().collect(Collectors.toList());
        Object currentUserId = UserServiceHelper.getCurrentUserId();
        Object currentUserName = UserServiceHelper.getUserInfoByID((long)currentUserId).get("name");
        for (Object o : list) {
            String id = o.toString();
            DynamicObject aosMktRcv = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_rcv");
            String aosUserold = aosMktRcv.getDynamicObject("aos_user").getPkValue().toString();
            String billno = aosMktRcv.getString("billno");
            String aosStatus = aosMktRcv.getString("aos_status");
            if (!"新建".equals(aosStatus)) {
                this.getView().showTipNotification(billno + "只允许转办新建状态的单据!");
                return;
            }
            if (!(String.valueOf(currentUserId)).equals(aosUserold)) {
                this.getView().showTipNotification(billno + "只允许转办自己的单据!");
                return;
            }
            aosMktRcv.set("aos_user", aosUser);
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_rcv",
                new DynamicObject[] {aosMktRcv}, OperateOption.create());
            MKTCom.SendGlobalMessage(String.valueOf(((DynamicObject)aosUser).getPkValue()), String.valueOf(aosMktRcv),
                String.valueOf(operationrst.getSuccessPkIds().get(0)), billno, currentUserName + "流程转办!");
            FndHistory fndHistory = new FndHistory();
            fndHistory.SetActionBy(currentUserId);
            fndHistory.SetFormId("aos_mkt_rcv");
            fndHistory.SetSourceId(id);
            fndHistory.SetActionCode("流程转办");
            fndHistory.SetDesc(currentUserName + "流程转办!");
            Cux_Common_Utl.History(fndHistory);
        }
        this.getView().showSuccessNotification("转办成功");
        this.getView().invokeOperation("refresh");
    }

    private void aosTest() {
    }

    private XSSFWorkbook genWorkBook(Row aosMktRcv, QFilter filterId) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        try {
            // 总列数
            int totalColumn = 11;
            XSSFSheet sheet = workbook.createSheet();
            // 默认宽度
            sheet.setDefaultColumnWidth(10);
            sheet.setDefaultRowHeightInPoints(15);
            sheet.setColumnWidth(4, 2000);
            sheet.setColumnWidth(5, 2000);
            sheet.setColumnWidth(6, 2000);
            sheet.setColumnWidth(7, 2000);
            sheet.setColumnWidth(8, 2000);
            // 标题风格
            XSSFCellStyle styletitle = Cux_Common_Utl.GetStyleTitle(workbook);
            // 行风格居中
            XSSFCellStyle stylerowCenter = getStyleRowCenter(workbook);
            // 表头风格
            XSSFCellStyle stylehead = getStyleHead(workbook);
            XSSFCellStyle styletitleleft = getStyleTitleLeft(workbook);
            XSSFCellStyle styleheadblank = getStyleHeadBlank(workbook);
            // 行风格
            XSSFCellStyle stylerow = Cux_Common_Utl.GetStyleRow(workbook);
            Object aosVendor = aosMktRcv.get("aos_vendor");
            Object aosPonumber = aosMktRcv.get("aos_ponumber");
            Object aosPhstate = aosMktRcv.get("aos_phstate");
            Object aosAddress = aosMktRcv.get("aos_address");
            QFilter filterVendor = new QFilter("aos_vendor", QCP.equals, aosVendor);
            QFilter filterPonumber = new QFilter("aos_ponumber", QCP.equals, aosPonumber);
            QFilter filterPhstate = new QFilter("aos_phstate", QCP.equals, aosPhstate);
            QFilter filterAddress = new QFilter("aos_address", QCP.equals, aosAddress);
            QFilter[] filtersIn = {filterId, filterVendor, filterPonumber, filterPhstate, filterAddress};
            String selectColumn =
                "aos_requireby.name aos_requireby,aos_requiredate,billno,aos_vendor,aos_ponumber,aos_address,aos_contact,aos_contactway,aos_returnadd,"
                    + "aos_itemid.number aos_itemid,aos_itemname,aos_boxqty,aos_photo,aos_vedio,"
                    + "aos_3d,aos_sample,aos_other,aos_protype,aos_developer.name aos_developer";
            DataSet aosMktRcvInS = QueryServiceHelper.queryDataSet("aos_mkt_rcv_list.aos_mkt_rcvInS", "aos_mkt_rcv",
                selectColumn, filtersIn, null);
            int i = 0;
            while (aosMktRcvInS.hasNext()) {
                i++;
                Row aosMktRcvIn = aosMktRcvInS.next();
                int row;
                if (i == 1) {
                    // 初始化默认头行 不需要处理
                    generateSheetDefualt(sheet, styletitle, stylehead, styletitleleft, styleheadblank, totalColumn,
                        aosMktRcvIn.getString("aos_address"));
                    row = sheet.getLastRowNum();
                    // 批量赋值头数据
                    // 申请人
                    sheet.getRow(row - 5).getCell(1).setCellValue(aosMktRcvIn.getString("aos_requireby"));
                    // 申请日期
                    sheet.getRow(row - 5).getCell(4)
                        .setCellValue(writeFormat.format(aosMktRcvIn.getDate("aos_requiredate")));
                    // 单号
                    sheet.getRow(row - 5).getCell(7).setCellValue(aosMktRcvIn.getString("billno"));
                    // 供应商
                    sheet.getRow(row - 4).getCell(1).setCellValue(aosMktRcvIn.getString("aos_vendor"));
                    // 合同号
                    sheet.getRow(row - 4).getCell(4).setCellValue(aosMktRcvIn.getString("aos_ponumber"));
                    // 样品接收地址
                    sheet.getRow(row - 4).getCell(7).setCellValue(aosMktRcvIn.getString("aos_address"));
                    // 退件联系人
                    sheet.getRow(row - 3).getCell(1).setCellValue(aosMktRcvIn.getString("aos_contact"));
                    // 退件联系方式
                    sheet.getRow(row - 3).getCell(4).setCellValue(aosMktRcvIn.getString("aos_contactway"));
                    // 退件地址
                    sheet.getRow(row - 3).getCell(7).setCellValue(aosMktRcvIn.getString("aos_returnadd"));
                } else {
                    sheet.getLastRowNum();
                }
                // 批量赋值行数据
                row = sheet.getLastRowNum() + 1;
                sheet.createRow(row);
                XSSFRow headRow = sheet.createRow(row);
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
                headCell.setCellValue(aosMktRcvIn.getString("aos_itemid"));
                // 品名
                headCell = headRow.createCell(3);
                headCell.setCellStyle(stylerow);
                headCell.setCellValue(aosMktRcvIn.getString("aos_itemname"));
                // 分箱数
                headCell = headRow.createCell(4);
                headCell.setCellStyle(stylerowCenter);
                headCell.setCellValue(aosMktRcvIn.getString("aos_boxqty"));

                // 用途
                // 拍照
                headCell = headRow.createCell(5);
                headCell.setCellStyle(stylerowCenter);
                if (aosMktRcvIn.getBoolean("aos_photo")) {
                    headCell.setCellValue("√");
                } else {
                    headCell.setCellValue("");
                }
                // 拍视频
                headCell = headRow.createCell(6);
                headCell.setCellStyle(stylerowCenter);
                if (aosMktRcvIn.getBoolean("aos_vedio")) {
                    headCell.setCellValue("√");
                } else {
                    headCell.setCellValue("");
                }
                // 3D建模
                headCell = headRow.createCell(7);
                headCell.setCellStyle(stylerowCenter);
                if (aosMktRcvIn.getBoolean("aos_3d")) {
                    headCell.setCellValue("√");
                } else {
                    headCell.setCellValue("");
                }
                // 封样
                headCell = headRow.createCell(8);
                headCell.setCellStyle(stylerowCenter);
                if (aosMktRcvIn.getBoolean("aos_sample")) {
                    headCell.setCellValue("√");
                } else {
                    headCell.setCellValue("");
                }
                // 其他
                headCell = headRow.createCell(9);
                headCell.setCellStyle(stylerowCenter);
                if (aosMktRcvIn.getBoolean("aos_other")) {
                    headCell.setCellValue("√");
                } else {
                    headCell.setCellValue("");
                }

                // 样品处理方式
                headCell = headRow.createCell(10);
                headCell.setCellStyle(stylerowCenter);
                headCell.setCellValue(aosMktRcvIn.getString("aos_protype"));

                // 开发员
                headCell = headRow.createCell(11);
                headCell.setCellStyle(stylerowCenter);
                headCell.setCellValue(aosMktRcvIn.getString("aos_developer"));
            }
            aosMktRcvInS.close();
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

    private XSSFCellStyle getStyleRowCenter(XSSFWorkbook workbook) {
        XSSFCellStyle stylerow = workbook.createCellStyle();
        stylerow.setAlignment(HorizontalAlignment.CENTER);
        stylerow.setBorderLeft(BorderStyle.THIN);
        stylerow.setBorderRight(BorderStyle.THIN);
        stylerow.setBorderTop(BorderStyle.THIN);
        stylerow.setBorderBottom(BorderStyle.THIN);
        return stylerow;
    }

    private void generateSheetDefualt(XSSFSheet sheet, XSSFCellStyle styletitle, XSSFCellStyle stylehead,
        XSSFCellStyle styletitleleft, XSSFCellStyle styleheadblank, int totalColumn, String aosAddress) {
        // 每组创建默认业务实体抬头
        int row = sheet.getLastRowNum() + 1;
        XSSFRow headRow = sheet.createRow(row);
        headRow.setHeightInPoints(40);
        XSSFCell headCell = headRow.createCell(0);
        headCell.setCellValue("遨森电子商务股份有限公司\r\n" + "AOSOM INTERNATIONAL DEVELOPMENT CO.,LIMITED");
        headCell.setCellStyle(styletitleleft);
        row = sheet.getLastRowNum() + 1;
        sheet.createRow(row);
        sheet.addMergedRegion(new CellRangeAddress(row - 1, row, 0, totalColumn));
        // 创建入库通知单标题
        row = sheet.getLastRowNum() + 1;
        headRow = sheet.createRow(row);
        headCell = headRow.createCell(0);
        headCell.setCellValue("样品入库通知单");
        headCell.setCellStyle(styletitle);
        row = sheet.getLastRowNum() + 1;
        sheet.createRow(row);
        sheet.addMergedRegion(new CellRangeAddress(row - 1, row, 0, totalColumn));
        // 创建提示行1
        row = sheet.getLastRowNum() + 1;
        headRow = sheet.createRow(row);
        headCell = headRow.createCell(0);
        headCell.setCellValue("请在外箱上备注货号；请不要寄到付件！");
        headCell.setCellStyle(styletitleleft);
        row = sheet.getLastRowNum() + 1;
        sheet.createRow(row);
        sheet.addMergedRegion(new CellRangeAddress(row - 1, row, 0, totalColumn));
        // 创建提示行2
        row = sheet.getLastRowNum() + 1;
        headRow = sheet.createRow(row);
        headRow.setHeightInPoints(40);
        headCell = headRow.createCell(0);
        headCell.setCellValue(aosAddress);
        headCell.setCellStyle(styletitleleft);
        row = sheet.getLastRowNum() + 1;
        sheet.createRow(row);
        sheet.addMergedRegion(new CellRangeAddress(row - 1, row, 0, totalColumn));
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
        sheet.createRow(row);
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

    private XSSFCellStyle getStyleHead(XSSFWorkbook workbook) {
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

    private XSSFCellStyle getStyleHeadBlank(XSSFWorkbook workbook) {
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

    private XSSFCellStyle getStyleTitleLeft(XSSFWorkbook workbook) {
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

    private void aosPrint() {
        try {
            List<ListSelectedRow> list = getSelectedRows();
            int size = list.size();
            if (size == 0) {
                throw new Exception("SelectError");
            }
            List<String> listQuery = getSelectedRows().stream().map(row -> row.getPrimaryKeyValue().toString())
                .distinct().collect(Collectors.toList());
            QFilter filterId = new QFilter("id", QCP.in, listQuery);
            QFilter[] filters = {filterId};
            String selectColumn = "aos_vendor,aos_ponumber,aos_phstate,aos_address";
            DataSet aosMktRcvS = QueryServiceHelper.queryDataSet("aos_mkt_rcv_list.aos_print", "aos_mkt_rcv",
                selectColumn, filters, null);
            String[] groupBy = new String[] {"aos_vendor", "aos_ponumber", "aos_phstate", "aos_address"};
            aosMktRcvS = aosMktRcvS.groupBy(groupBy).finish();
            while (aosMktRcvS.hasNext()) {
                Row aosMktRcv = aosMktRcvS.next();
                this.getView()
                    .download(comPrtUpload(
                        aosMktRcv.getString("aos_vendor") + "-" + aosMktRcv.getString("aos_phstate") + "-样品入库通知单.xlsx",
                        genWorkBook(aosMktRcv, filterId)));
            }
            aosMktRcvS.close();
        } catch (Exception e) {
            if (SELECTERROR.equals(e.getMessage())) {
                this.getView().showErrorNotification("请选择一条数据打印!");
            } else {
                this.getView().showErrorNotification(e.getMessage());
            }
        }
    }

    private void aosSubmit() throws FndError {
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
                } else {
                    message = "提交成功";
                }
                this.getView().updateView();
                this.getView().showSuccessNotification(message);
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }
}