package mkt.popular.ppc;

import common.Cux_Common_Utl;
import common.fnd.FndDate;
import common.fnd.FndGlobal;
import common.fnd.FndLog;
import common.fnd.FndMsg;
import common.sal.sys.basedata.dao.ItemDao;
import common.sal.sys.basedata.dao.impl.ItemDaoImpl;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.cache.CacheFactory;
import kd.bos.cache.TempFileCache;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.entity.datamodel.ListSelectedRowCollection;
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
import kd.bos.servicehelper.operation.SaveServiceHelper;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.xssf.usermodel.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author aosom
 * @version ppc推广调价-列表插件
 */
public class AosMktPopPpcList extends AbstractListPlugin {

    private static HashMap<String, Map<String, Map<String, Object>>> initGroup(String fid) {
        HashMap<String, Map<String, Map<String, Object>>> group = new HashMap<>(16);
        // 循环得到组
        QFilter filterId = new QFilter("id", "=", fid);
        QFilter[] filters = new QFilter[] {filterId};
        String selectField = "aos_entryentity.aos_productno aos_productno,"
            + "aos_entryentity.aos_itemnumer aos_itemnumer," + "aos_entryentity.aos_bid aos_bid,"
            + "aos_entryentity.aos_shopsku aos_shopsku," + "aos_entryentity.aos_groupstatus aos_groupstatus,"
            + "aos_entryentity.aos_topprice aos_topprice," + "aos_entryentity.aos_groupdate aos_groupdate";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("init_group." + fid, "aos_mkt_popular_ppc",
            selectField, filters, "aos_entryentity.aos_itemnumer");
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            Map<String, Object> detail = new HashMap<>(16);
            detail.put("aos_bid", aosMktPopularPpc.getBigDecimal("aos_bid").setScale(2, RoundingMode.HALF_UP));
            detail.put("aos_shopsku", aosMktPopularPpc.get("aos_shopsku"));
            detail.put("aos_groupstatus", aosMktPopularPpc.get("aos_groupstatus"));
            detail.put("aos_groupdate", aosMktPopularPpc.get("aos_groupdate"));
            detail.put("aos_topprice",
                aosMktPopularPpc.getBigDecimal("aos_topprice").setScale(2, RoundingMode.HALF_UP));
            Map<String, Map<String, Object>> info = group.get(aosMktPopularPpc.getString("aos_productno"));
            if (info == null) {
                info = new HashMap<>(16);
            }
            String aosItemnumer = aosMktPopularPpc.getString("aos_itemnumer");
            info.put(aosItemnumer, detail);
            group.put(aosMktPopularPpc.getString("aos_productno"), info);
        }
        aosMktPopularPpcS.close();
        return group;
    }

    private static BigDecimal max(BigDecimal value1, BigDecimal value2) {
        if (value1.compareTo(value2) > -1) {
            return value1;
        } else {
            return value2;
        }
    }

    /**
     * 获取上期的调试数据，map key存的： 组别？物料 ; value ：调价
     *
     * @param orgid 国别
     * @param date 本单的日期
     * @return Map<String, BigDecimal>
     */
    private static Map<String, BigDecimal> getLastPeriodData(String orgid, Date date) {
        Map<String, BigDecimal> mapRe = new HashMap<>(16);
        try {
            List<QFilter> listQf = new ArrayList<>();
            Calendar cal = new GregorianCalendar();
            SimpleDateFormat simD = new SimpleDateFormat("yyyy-MM-dd");
            cal.setTime(simD.parse(simD.format(date)));
            QFilter qfDateMax = new QFilter("aos_date", "<", cal.getTime());
            listQf.add(qfDateMax);
            cal.add(Calendar.DATE, -1);
            QFilter qfDateMin = new QFilter("aos_date", ">=", cal.getTime());
            listQf.add(qfDateMin);
            QFilter qfOrg = new QFilter("aos_orgid", "=", orgid);
            listQf.add(qfOrg);
            // 组状态
            QFilter qfState = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE");
            listQf.add(qfState);
            int size = listQf.size();
            QFilter[] qfs = listQf.toArray(new QFilter[size]);
            StringJoiner strjoin = new StringJoiner(",");
            strjoin.add("aos_entryentity.aos_itemnumer as aos_itemnumer");
            strjoin.add("aos_entryentity.aos_itemid as aos_itemid");
            strjoin.add("aos_entryentity.aos_bid as aos_bid");
            DynamicObjectCollection dycDataS = QueryServiceHelper.query("aos_mkt_popular_ppc", strjoin.toString(), qfs);
            StringBuilder strbuilder = new StringBuilder();
            for (DynamicObject dycData : dycDataS) {
                strbuilder.append(dycData.getString("aos_itemnumer"));
                strbuilder.append("?");
                strbuilder.append(dycData.getString("aos_itemid"));
                mapRe.put(strbuilder.toString(), dycData.getBigDecimal("aos_bid"));
                strbuilder.setLength(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mapRe;
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

    @Override
    public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
        String actionId = closedCallBackEvent.getActionId();
        if (StringUtils.equals(actionId, sign.form.name)) {
            String aosOuCode = (String)((Map<?, ?>)closedCallBackEvent.getReturnData()).get("aos_ou_code");
            AosMktPopPpcTask.manualitemClick(aosOuCode);
            this.getView().invokeOperation("refresh");
            this.getView().showSuccessNotification("已手工提交PPC数据源初始化,请等待,务重复提交!");
        }
    }

    public ByteArrayInputStream parse(final OutputStream out) throws Exception {
        ByteArrayOutputStream baos = (ByteArrayOutputStream)out;
        return new ByteArrayInputStream(baos.toByteArray());
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        if (StringUtils.equals(sign.init.name, evt.getItemKey())) {
            // PPC数据源初始化
            aosInit();
        } else if (StringUtils.equals(sign.export.name, evt.getItemKey())) {
            // 打印报告
            try {
                aosExport();
            } catch (ParseException parseException) {
                parseException.printStackTrace();
            }
        } else if (StringUtils.equals(sign.exportNew.name, evt.getItemKey())) {
            // 打印新版报告
            try {
                aosExportnew();
            } catch (ParseException parseException) {
                parseException.printStackTrace();
                this.getView().showErrorNotification(SalUtil.getExceptionStr(parseException));
            }
        } else if (StringUtils.equals(sign.close.name, evt.getItemKey())) {
            // 关闭时计算 最后调价日期
            try {
                aosClose();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void aosClose() {
        // 获取选中行
        ListSelectedRowCollection selectedRowS = this.getSelectedRows();
        List<DynamicObject> listDy = new ArrayList<>();
        for (ListSelectedRow selectedRow : selectedRowS) {
            DynamicObject dy =
                BusinessDataServiceHelper.loadSingle(selectedRow.getPrimaryKeyValue(), "aos_mkt_popular_ppc");
            listDy.add(dy);
        }
        // 国别 物料 组名 确定唯一值
        // 系列状态只用可用
        listDy.stream().parallel().forEach(e -> {
            // 查找前一天的 同一国别的数据
            Date aosDate = e.getDate("aos_date");
            DynamicObject dyOrg = e.getDynamicObject("aos_orgid");
            Map<String, BigDecimal> mapLastData = getLastPeriodData(dyOrg.getString("id"), aosDate);
            // 单据体行遍历,比较调价日期及赋值
            DynamicObjectCollection dycEnt = e.getDynamicObjectCollection("aos_entryentity");
            StringBuilder strbuilder = new StringBuilder();
            dycEnt.stream().filter(ent -> ent.getString("aos_groupstatus").equals(sign.available.name)).forEach(ent -> {
                strbuilder.append(ent.getString("aos_itemnumer"));
                strbuilder.append("?");
                strbuilder.append(ent.getDynamicObject("aos_itemid").getString("id"));
                String key = strbuilder.toString();
                strbuilder.setLength(0);
                if (mapLastData.containsKey(key)) {
                    BigDecimal bigLast = mapLastData.get(key);
                    BigDecimal bigThe = ent.getBigDecimal("aos_bid");
                    if (bigLast.compareTo(bigThe) != 0) {
                        ent.set("aos_lastpricedate", aosDate);
                    }
                } else {
                    ent.set("aos_lastpricedate", aosDate);
                }
            });
        });
        int size = listDy.size();
        SaveServiceHelper.save(listDy.toArray(new DynamicObject[size]));
        this.getView().showMessage("设置完成");
    }

    private void aosInit() {
        FormShowParameter showParameter = new FormShowParameter();
        showParameter.setFormId("aos_mkt_cal_form");
        showParameter.getOpenStyle().setShowType(ShowType.Modal);
        showParameter.setCloseCallBack(new CloseCallBack(this, "form"));
        this.getView().showForm(showParameter);
    }

    private void aosExport() throws ParseException {
        // 获取选中行
        List<ListSelectedRow> list = getSelectedRows();
        for (ListSelectedRow listSelectedRow : list) {
            String fid = listSelectedRow.toString();
            DynamicObject aosMktPopularPpc = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_popular_ppc");
            DynamicObject aosOrgid = (DynamicObject)aosMktPopularPpc.get("aos_orgid");
            String pOuCode = aosOrgid.getString("number");
            XSSFWorkbook workbook = excel(fid);
            String path = upload("推广报告" + pOuCode, workbook);
            this.getView().download(path);
        }
    }

    private void aosExportnew() throws ParseException {
        // 获取选中行
        List<ListSelectedRow> list = getSelectedRows();
        for (ListSelectedRow listSelectedRow : list) {
            String fid = listSelectedRow.toString();
            DynamicObject aosMktPopularPpc = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_popular_ppc");
            DynamicObject aosOrgid = (DynamicObject)aosMktPopularPpc.get("aos_orgid");
            Date aosDate = aosMktPopularPpc.getDate("aos_date");
            String pOuCode = aosOrgid.getString("number");
            // 上传
            XSSFWorkbook workbook = excelnew(fid, aosDate);
            // 弹出提示框
            String path = upload("新版推广报告" + pOuCode, workbook);
            this.getView().download(path);
        }
    }

    private XSSFWorkbook excel(String fid) throws ParseException {
        // 创建excel工作簿
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFCellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        setExcelAttr(workbook, style, fid);
        return workbook;
    }

    private XSSFWorkbook excelnew(String fid, Date aosDate) throws ParseException {
        // 创建excel工作簿
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFCellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        setExcelAttrnew(workbook, style, fid, aosDate);
        return workbook;
    }

    private void setExcelAttr(XSSFWorkbook workbook, XSSFCellStyle style, String fid) throws ParseException {
        FndLog fndLog = FndLog.init("MKT_推广报告", fid);
        try {
            // 创建工作表sheet
            XSSFSheet sheet = workbook.createSheet();
            sheet.setDefaultColumnWidth(15);
            // 创建标题
            XSSFRow headRow = sheet.createRow(0);
            createColumn(headRow, style, 0, "Record ID");
            createColumn(headRow, style, 1, "Record Type");
            createColumn(headRow, style, 2, "Campaign ID");
            createColumn(headRow, style, 3, "Campaign");
            createColumn(headRow, style, 4, "Campaign Daily Budget");
            createColumn(headRow, style, 5, "Portfolio ID");
            createColumn(headRow, style, 6, "Campaign Start Date");
            createColumn(headRow, style, 7, "Campaign End Date");
            createColumn(headRow, style, 8, "Campaign Targeting Type");
            createColumn(headRow, style, 9, "Ad Group");
            createColumn(headRow, style, 10, "Max Bid");
            createColumn(headRow, style, 11, "Keyword or Product Targeting");
            createColumn(headRow, style, 12, "Product Targeting ID");
            createColumn(headRow, style, 13, "Match Type");
            createColumn(headRow, style, 14, "SKU");
            createColumn(headRow, style, 15, "Campaign Status");
            createColumn(headRow, style, 16, "Ad Group Status");
            createColumn(headRow, style, 17, "Status");
            createColumn(headRow, style, 18, "Impressions");
            createColumn(headRow, style, 19, "Clicks");
            createColumn(headRow, style, 20, "Spend");
            createColumn(headRow, style, 21, "Orders");
            createColumn(headRow, style, 22, "Total units");
            createColumn(headRow, style, 23, "Sales");
            createColumn(headRow, style, 24, "ACoS");
            createColumn(headRow, style, 25, "Bidding strategy");
            createColumn(headRow, style, 26, "Placement Type");
            createColumn(headRow, style, 27, "Increase bids by placement");

            // 查询当前单据国别
            DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_popular_ppc",
                "aos_orgid," + "aos_orgid.number p_ou_code", new QFilter[] {new QFilter("id", QCP.equals, fid)});
            String aosOrgid = dynamicObject.getString("aos_orgid");
            String pOuCode = dynamicObject.getString("p_ou_code");
            // 获取fba数据
            Map<String, List<String>> fbaMap = getFbaMap(aosOrgid);
            // 初始化组数据
            HashMap<String, Map<String, Map<String, Object>>> group = initGroup(fid);
            // 获取该国别下，所有的爆品物料编码
            List<String> listSaleOutItems = getSaleItemByOu(aosOrgid);
            BigDecimal exRateWellSt = AosMktPopPpcTask.getExRateLowSt(pOuCode, "优");
            // 竞价策略
            Map<String, String> comp = initSerialGroup(fid, aosOrgid, exRateWellSt);
            // 特殊广告
            Map<String, String> portfolio = initSerialRoi(fid, aosOrgid);
            // 特殊广告
            Map<String, String> portid = initportid(aosOrgid);

            // 循环单子下所有的产品号
            QFilter filterId = new QFilter("id", "=", fid);
            QFilter[] filters = new QFilter[] {filterId};
            String selectColumn =
                "aos_entryentity.aos_productno aos_productno," + "aos_entryentity.aos_budget aos_budget";
            DataSet aosMktPopppcInitS = QueryServiceHelper.queryDataSet("aos_mkt_popppc_list." + fid,
                "aos_mkt_popular_ppc", selectColumn, filters, "aos_entryentity.aos_productno");
            String[] groupBy = new String[] {"aos_productno", "aos_budget"};
            aosMktPopppcInitS = aosMktPopppcInitS.groupBy(groupBy).finish();
            while (aosMktPopppcInitS.hasNext()) {
                int topOfSearch = 0;
                Row aosMktPopppcInit = aosMktPopppcInitS.next();
                String aosProductno = aosMktPopppcInit.getString("aos_productno");
                // 国别
                BigDecimal aosBudget = aosMktPopppcInit.getBigDecimal("aos_budget").setScale(2, RoundingMode.HALF_UP);
                fndLog.add("系列=" + aosProductno);
                // 每个系列下 创建四行数据
                // 系列第一行
                int row = sheet.getLastRowNum() + 1;
                XSSFRow productRow = sheet.createRow(row);
                createColumn(productRow, style, 1, "Campaign");
                createColumn(productRow, style, 3, aosProductno);
                createColumn(productRow, style, 4, aosBudget);
                createColumn(productRow, style, 8, "Auto");
                createColumn(productRow, style, 15, "enabled");
                createColumn(productRow, style, 25, comp.get(aosProductno));
                createColumn(productRow, style, 26, "All");
                if (FndGlobal.IsNotNull(portfolio.get(aosProductno))) {
                    createColumn(productRow, style, 5, portid.get(portfolio.get(aosProductno)));
                }
                // 系列第二行
                row = sheet.getLastRowNum() + 1;
                topOfSearch = row;
                productRow = sheet.createRow(row);
                createColumn(productRow, style, 1, "Campaign By Placement");
                createColumn(productRow, style, 3, aosProductno);
                createColumn(productRow, style, 26, "Top of search (page 1)");
                // 系列第三行
                row = sheet.getLastRowNum() + 1;
                productRow = sheet.createRow(row);
                createColumn(productRow, style, 1, "Campaign By Placement");
                createColumn(productRow, style, 3, aosProductno);
                createColumn(productRow, style, 26, "Rest of search");
                // 系列第四行
                row = sheet.getLastRowNum() + 1;
                productRow = sheet.createRow(row);
                createColumn(productRow, style, 1, "Campaign By Placement");
                createColumn(productRow, style, 3, aosProductno);
                createColumn(productRow, style, 26, "Product pages");

                // 循环得到组
                Map<String, Map<String, Object>> groupD = group.get(aosProductno);
                BigDecimal aosTopprice = BigDecimal.ZERO;
                // 存在爆品
                boolean saleOutItem = false;
                for (String aosItemnumer : groupD.keySet()) {
                    // 判断是否为爆品
                    if (!saleOutItem && listSaleOutItems.contains(aosItemnumer)) {
                        saleOutItem = true;
                    }
                    BigDecimal aosBid = BigDecimal.ZERO;
                    if (!Cux_Common_Utl.IsNull(groupD.get(aosItemnumer).get("aos_bid"))) {
                        aosBid = (BigDecimal)groupD.get(aosItemnumer).get("aos_bid");
                    }
                    Object aosShopsku = groupD.get(aosItemnumer).get("aos_shopsku");
                    Object aosGroupstatus = groupD.get(aosItemnumer).get("aos_groupstatus");
                    aosTopprice = max((BigDecimal)groupD.get(aosItemnumer).get("aos_topprice"), aosTopprice);
                    String aosToppriceStr = "0%";
                    if (aosTopprice.compareTo(BigDecimal.valueOf(0)) == 0) {
                        aosToppriceStr = "0%";
                    } else {
                        aosToppriceStr = aosTopprice.multiply(BigDecimal.valueOf(100)).intValue() + "%";
                    }
                    String groupStatus = "enabled";
                    if ((String.valueOf(aosGroupstatus)).equals(sign.available.name)) {
                        groupStatus = "enabled";
                    } else {
                        groupStatus = "paused";
                    }
                    // 排除连续两次pause的组
                    fndLog.add("组=" + aosItemnumer);
                    // 每个组要创建六行数据
                    // 组第一行
                    row = sheet.getLastRowNum() + 1;
                    productRow = sheet.createRow(row);
                    createColumn(productRow, style, 1, "Ad Group");
                    createColumn(productRow, style, 3, aosProductno);
                    createColumn(productRow, style, 9, aosItemnumer);
                    createColumn(productRow, style, 10, aosBid);
                    createColumn(productRow, style, 15, "enabled");
                    createColumn(productRow, style, 16, groupStatus);
                    // 组第二行
                    // 判断是否fba
                    int size = 1;
                    boolean flag = false;
                    List<String> fbaList = fbaMap.get(aosItemnumer);
                    if (fbaList != null && fbaList.size() > 0) {
                        flag = true;
                        size = fbaList.size();
                        row = sheet.getLastRowNum() + 1;
                        productRow = sheet.createRow(row);
                        createColumn(productRow, style, 1, "Ad");
                        createColumn(productRow, style, 3, aosProductno);
                        createColumn(productRow, style, 9, aosItemnumer);
                        createColumn(productRow, style, 14, aosItemnumer);
                        createColumn(productRow, style, 15, "enabled");
                        createColumn(productRow, style, 16, "enabled");
                        createColumn(productRow, style, 17, groupStatus);
                    }
                    for (int i = 0; i < size; i++) {
                        row = sheet.getLastRowNum() + 1;
                        productRow = sheet.createRow(row);
                        createColumn(productRow, style, 1, "Ad");
                        createColumn(productRow, style, 3, aosProductno);
                        createColumn(productRow, style, 9, aosItemnumer);
                        createColumn(productRow, style, 14, flag ? fbaList.get(i) : aosShopsku);
                        createColumn(productRow, style, 15, "enabled");
                        createColumn(productRow, style, 16, "enabled");
                        createColumn(productRow, style, 17, groupStatus);
                    }
                    // 组第三行
                    BigDecimal bid = BigDecimal.ZERO;
                    row = sheet.getLastRowNum() + 1;
                    productRow = sheet.createRow(row);
                    createColumn(productRow, style, 1, "Product Targeting");
                    createColumn(productRow, style, 3, aosProductno);
                    createColumn(productRow, style, 9, aosItemnumer);
                    bid = aosBid.multiply(BigDecimal.valueOf(1.3)).setScale(2, RoundingMode.HALF_UP);
                    createColumn(productRow, style, 10, bid);
                    createColumn(productRow, style, 11, "close-match");
                    createColumn(productRow, style, 12, "close-match");
                    createColumn(productRow, style, 13, "Targeting Expression Predefined");
                    createColumn(productRow, style, 15, "enabled");
                    createColumn(productRow, style, 16, "enabled");
                    createColumn(productRow, style, 17, groupStatus);
                    // 组第四行
                    row = sheet.getLastRowNum() + 1;
                    productRow = sheet.createRow(row);
                    createColumn(productRow, style, 1, "Product Targeting");
                    createColumn(productRow, style, 3, aosProductno);
                    createColumn(productRow, style, 9, aosItemnumer);
                    bid = aosBid.multiply(BigDecimal.valueOf(0.7)).setScale(2, RoundingMode.HALF_UP);
                    createColumn(productRow, style, 10, bid);
                    createColumn(productRow, style, 11, "loose-match");
                    createColumn(productRow, style, 12, "loose-match");
                    createColumn(productRow, style, 13, "Targeting Expression Predefined");
                    createColumn(productRow, style, 15, "enabled");
                    createColumn(productRow, style, 16, "enabled");
                    createColumn(productRow, style, 17, groupStatus);
                    // 组第五行
                    row = sheet.getLastRowNum() + 1;
                    productRow = sheet.createRow(row);
                    createColumn(productRow, style, 1, "Product Targeting");
                    createColumn(productRow, style, 3, aosProductno);
                    createColumn(productRow, style, 9, aosItemnumer);
                    bid = aosBid.multiply(BigDecimal.valueOf(1)).setScale(2, RoundingMode.HALF_UP);
                    createColumn(productRow, style, 10, bid);
                    createColumn(productRow, style, 11, "complements");
                    createColumn(productRow, style, 12, "complements");
                    createColumn(productRow, style, 13, "Targeting Expression Predefined");
                    createColumn(productRow, style, 15, "enabled");
                    createColumn(productRow, style, 16, "enabled");
                    createColumn(productRow, style, 17, groupStatus);
                    // 组第六行
                    row = sheet.getLastRowNum() + 1;
                    productRow = sheet.createRow(row);
                    createColumn(productRow, style, 1, "Product Targeting");
                    createColumn(productRow, style, 3, aosProductno);
                    createColumn(productRow, style, 9, aosItemnumer);
                    bid = aosBid.multiply(BigDecimal.valueOf(1)).setScale(2, RoundingMode.HALF_UP);
                    createColumn(productRow, style, 10, bid);
                    createColumn(productRow, style, 11, "substitutes");
                    createColumn(productRow, style, 12, "substitutes");
                    createColumn(productRow, style, 13, "Targeting Expression Predefined");
                    createColumn(productRow, style, 15, "enabled");
                    createColumn(productRow, style, 16, "enabled");
                    createColumn(productRow, style, 17, groupStatus);
                    sheet.getRow(topOfSearch);
                    createColumn(productRow, style, 27, aosToppriceStr);
                }
            }
            aosMktPopppcInitS.close();// 保存日志表
            fndLog.finnalSave();
        } catch (Exception e) {
            e.printStackTrace();
            fndLog.add("报错 =" + e.getMessage());
            fndLog.finnalSave();
            throw e;
        }
    }

    private Map<String, String> initportid(String aosOrgid) {
        Map<String, String> portfolio = new HashMap<>(16);
        DynamicObjectCollection aosMktAdvS = QueryServiceHelper.query("aos_mkt_adv", "aos_idpro,aos_type",
            new QFilter("aos_orgid.id", QCP.equals, aosOrgid).toArray());
        for (DynamicObject aosMktAdv : aosMktAdvS) {
            portfolio.put(aosMktAdv.getString("aos_type"), aosMktAdv.getString("aos_idpro"));
        }
        return portfolio;
    }

    private Map<String, String> initSerialRoi(String fid, String aosOrgid) {
        Map<String, String> portfolio = new HashMap<>(16);
        HashMap<String, Map<String, Object>> popOrgInfo = genPop();
        // 国别标准ROI
        BigDecimal popOrgRoist = (BigDecimal)popOrgInfo.get(aosOrgid + "~" + "ROIST").get("aos_value");
        FndMsg.debug("popOrgRoist:" + popOrgRoist);
        // 系列ROI
        HashMap<String, BigDecimal> skuRpt = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date date = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, 0);
        Date dateTo = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, -7);
        Date dateFrom = calendar.getTime();
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        String dateStr = writeFormat.format(date);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<=", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch};
        String selectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku.aos_productno||'-AUTO' aos_productno,"
            + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis.init_skurpt",
            "aos_base_skupoprpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_productno"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_spend").sum("aos_total_sales").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            BigDecimal aosTotalSales = aosBaseSkupoprpt.getBigDecimal("aos_total_sales");
            BigDecimal aosSpend = aosBaseSkupoprpt.getBigDecimal("aos_spend");
            String aosProductno = aosBaseSkupoprpt.getString("aos_productno");
            if (aosSpend.compareTo(BigDecimal.ZERO) != 0) {
                skuRpt.put(aosProductno, aosTotalSales.divide(aosSpend, 2, RoundingMode.HALF_UP));
            }
        }
        aosBaseSkupoprptS.close();
        DynamicObject aosMktPopularPpc = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_popular_ppc");
        DynamicObjectCollection aosEntryentityS = aosMktPopularPpc.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            String aosProductno = aosEntryentity.getString("aos_productno");
            BigDecimal roi = skuRpt.get(aosProductno);
            Date aosFirstindate = aosEntryentity.getDate("aos_firstindate");
            String aosCategory2 = aosEntryentity.getString("aos_category2");
            String aosCategory3 = aosEntryentity.getString("aos_category3");
            String aosSeasonseting = aosEntryentity.getString("aos_seasonseting");

            if (FndGlobal.IsNotNull(skuRpt.get(aosProductno)) && roi.compareTo(popOrgRoist) < 0) {
                if (aosEntryentity.getBoolean("aos_special")) {
                    portfolio.put(aosProductno, "aos_special");
                }
                if (FndGlobal.IsNull(portfolio.get(aosProductno))
                    && "Y".equals(aosEntryentity.getString("aos_offline"))) {
                    portfolio.put(aosProductno, "aos_special");
                }
            }
            if ((FndGlobal.IsNotNull(aosFirstindate)) && (FndDate.GetBetweenDays(new Date(), aosFirstindate) < 30)) {
                portfolio.put(aosProductno, "aos_new");
            }
            if ("秋冬产品".equals(aosSeasonseting) || "冬季产品".equals(aosSeasonseting)) {
                portfolio.put(aosProductno, "autumn_winter");
            }
            if ("万圣装饰".equals(aosCategory3)) {
                portfolio.put(aosProductno, "halloween");
            }
            if ("圣诞装饰".equals(aosCategory2)) {
                portfolio.put(aosProductno, "chrismas");
            }
        }
        return portfolio;
    }

    private Map<String, String> initSerialGroup(String fid, String pOrgId, BigDecimal exRateWellSt) {
        HashMap<String, Map<String, Object>> popOrgInfo = genPop();
        Map<String, String> comp = new HashMap<>(16);
        BigDecimal worry = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "WORRY").get("aos_value");
        DynamicObject aosMktPopularPpc = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_popular_ppc");
        DynamicObjectCollection aosEntryentityS = aosMktPopularPpc.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            BigDecimal aosRoi = aosEntryentity.getBigDecimal("aos_roi14days");
            String aosProductno = aosEntryentity.getString("aos_productno");
            Date aosFirstindate = aosEntryentity.getDate("aos_firstindate");
            BigDecimal aosRptRoi = aosEntryentity.getBigDecimal("aos_rpt_roi");
            boolean type2 = (("A".equals(aosEntryentity.getString("aos_contryentrystatus"))
                || ((FndGlobal.IsNotNull(aosFirstindate)) && (FndDate.GetBetweenDays(new Date(), aosFirstindate) < 30)))
                && !"Dynamic bids - up and down".equals(comp.get(aosProductno)));
            if (aosRptRoi.compareTo(exRateWellSt) > 0 && aosRoi.compareTo(worry) > 0) {
                comp.put(aosProductno, "Dynamic bids - up and down");
            } else if (type2) {
                comp.put(aosProductno, "Fixed bid");
            } else if (!"Fixed bid".equals(comp.get(aosProductno))
                && !"Dynamic bids - up and down".equals(comp.get(aosProductno))) {
                comp.put(aosProductno, "Dynamic bids - down only");
            }
        }
        return comp;
    }

    private HashMap<String, Map<String, Object>> genPop() {
        HashMap<String, Map<String, Object>> popOrgInfo = new HashMap<>(16);
        String selectColumn = "aos_orgid," + "aos_type," + "aos_value,aos_condition1";
        DataSet aosMktBaseOrgvalueS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis.init_poporg",
            "aos_mkt_base_orgvalue", selectColumn, null, null);
        while (aosMktBaseOrgvalueS.hasNext()) {
            Row aosMktBaseOrgvalue = aosMktBaseOrgvalueS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            info.put("aos_value", aosMktBaseOrgvalue.get("aos_value"));
            info.put("aos_condition1", aosMktBaseOrgvalue.get("aos_condition1"));
            popOrgInfo.put(aosMktBaseOrgvalue.getLong("aos_orgid") + "~" + aosMktBaseOrgvalue.get("aos_type"), info);
        }
        aosMktBaseOrgvalueS.close();
        return popOrgInfo;
    }

    private void setExcelAttrnew(XSSFWorkbook workbook, XSSFCellStyle style, String fid, Date aosDate)
        throws ParseException {
        // 创建工作表sheet
        XSSFSheet sheet = workbook.createSheet();
        sheet.setDefaultColumnWidth(15);
        // 创建标题
        XSSFRow headRow = sheet.createRow(0);
        createColumn(headRow, style, 0, "Product");
        createColumn(headRow, style, 1, "Entity");
        createColumn(headRow, style, 2, "Operation");
        createColumn(headRow, style, 3, "Campaign ID");
        createColumn(headRow, style, 4, "Ad Group ID");
        createColumn(headRow, style, 5, "Portfolio ID");
        createColumn(headRow, style, 6, "Ad ID");
        createColumn(headRow, style, 7, "Keyword ID");
        createColumn(headRow, style, 8, "Product Targeting ID");
        createColumn(headRow, style, 9, "Campaign Name");
        createColumn(headRow, style, 10, "Ad Group Name");
        createColumn(headRow, style, 11, "Start Date");
        createColumn(headRow, style, 12, "End Date");
        createColumn(headRow, style, 13, "Targeting Type");
        createColumn(headRow, style, 14, "State");
        createColumn(headRow, style, 15, "Daily Budget");
        createColumn(headRow, style, 16, "SKU");
        createColumn(headRow, style, 17, "Ad Group Default Bid");
        createColumn(headRow, style, 18, "Bid");
        createColumn(headRow, style, 19, "Keyword Text");
        createColumn(headRow, style, 20, "Match Type");
        createColumn(headRow, style, 21, "Bidding Strategy");
        createColumn(headRow, style, 22, "Placement");
        createColumn(headRow, style, 23, "Percentage");
        createColumn(headRow, style, 24, "Product Targeting Expression");
        // 查询当前单据国别
        DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_popular_ppc",
            "aos_orgid,aos_orgid.number p_ou_code", new QFilter[] {new QFilter("id", QCP.equals, fid)});
        String aosOrgid = dynamicObject.getString("aos_orgid");
        String pOuCode = dynamicObject.getString("p_ou_code");
        // 获取fba数据
        Map<String, List<String>> fbaMap = getFbaMap(aosOrgid);
        Map<String, String> productIdMap = getProductIdMap(aosOrgid);
        Map<String, String> groupIdMap = getGroupIdMap(aosOrgid);
        Map<String, String> itemIdMap = getItemIdMap(aosOrgid);
        Map<String, String> targetIdMap = getTargetIdMap(aosOrgid);
        // 特殊广告
        Map<String, String> portfolio = initSerialRoi(fid, aosOrgid);
        // 特殊广告
        Map<String, String> portid = initportid(aosOrgid);
        BigDecimal exRateWellSt = AosMktPopPpcTask.getExRateLowSt(pOuCode, "优");
        // 竞价策略
        Map<String, String> comp = initSerialGroup(fid, aosOrgid, exRateWellSt);
        // 初始化组数据
        HashMap<String, Map<String, Map<String, Object>>> group = initGroup(fid);
        // 循环单子下所有的产品号
        QFilter filterId = new QFilter("id", "=", fid);
        QFilter[] filters = new QFilter[] {filterId};
        String selectColumn = "aos_entryentity.aos_productno aos_productno," + "aos_entryentity.aos_budget aos_budget,"
            + "aos_entryentity.aos_makedate aos_makedate," + "aos_entryentity.aos_serialstatus aos_serialstatus";
        DataSet aosMktPopppcInitS = QueryServiceHelper.queryDataSet("aos_mkt_popppc_list." + fid, "aos_mkt_popular_ppc",
            selectColumn, filters, "aos_entryentity.aos_productno");
        String[] groupBy = new String[] {"aos_productno", "aos_budget", "aos_makedate", "aos_serialstatus"};
        aosMktPopppcInitS = aosMktPopppcInitS.groupBy(groupBy).finish();
        while (aosMktPopppcInitS.hasNext()) {
            // 用于存贮置顶位置出价的行
            int topOfSearch;
            Row aosMktPopppcInit = aosMktPopppcInitS.next();
            String aosProductno = aosMktPopppcInit.getString("aos_productno");
            BigDecimal aosBudget = aosMktPopppcInit.getBigDecimal("aos_budget").setScale(2, RoundingMode.HALF_UP);
            Date aosMakedate = aosMktPopppcInit.getDate("aos_makedate");
            String aosSerialstatus = aosMktPopppcInit.getString("aos_serialstatus");
            // 判断是否新系列
            boolean newSerialFlag;
            String operation = "";
            if (aosMakedate.compareTo(aosDate) == 0) {
                newSerialFlag = true;
                operation = "create";
            } else {
                newSerialFlag = false;
                operation = "update";
            }
            // 判断系列是否可用
            String serialStatus;
            if ((aosSerialstatus).equals(sign.available.name)) {
                serialStatus = "enabled";
            } else {
                serialStatus = "paused";
            }
            // 3.Campaign Id 如果系列为新增，ID直接等于系列名称
            Object campaignId;
            if (newSerialFlag) {
                campaignId = aosProductno;
            } else {
                campaignId = productIdMap.getOrDefault(aosProductno, aosProductno);
            }
            if (aosProductno.equals(campaignId)) {
                operation = "create";
            } else {
                operation = "update";
            }
            // 每个系列下 创建三行数据
            // 系列第一行
            int row = sheet.getLastRowNum() + 1;
            XSSFRow productRow = sheet.createRow(row);
            createColumn(productRow, style, 0, "Sponsored Products");
            createColumn(productRow, style, 1, "Campaign");
            createColumn(productRow, style, 2, operation);
            createColumn(productRow, style, 3, campaignId);
            if (FndGlobal.IsNotNull(portfolio.get(aosProductno))) {
                createColumn(productRow, style, 5, portid.getOrDefault(portfolio.get(aosProductno), ""));
            }
            createColumn(productRow, style, 9, aosProductno);
            createColumn(productRow, style, 13, "AUTO");
            createColumn(productRow, style, 14, serialStatus);
            createColumn(productRow, style, 15, aosBudget);
            createColumn(productRow, style, 21, comp.get(aosProductno));
            // 系列第二行
            row = sheet.getLastRowNum() + 1;
            productRow = sheet.createRow(row);
            createColumn(productRow, style, 0, "Sponsored Products");
            createColumn(productRow, style, 1, "Bidding Adjustment");
            createColumn(productRow, style, 2, operation);
            createColumn(productRow, style, 3, campaignId);
            createColumn(productRow, style, 21, comp.get(aosProductno));
            createColumn(productRow, style, 22, "placementProductPage");
            createColumn(productRow, style, 23, "0.00");
            // 系列第三行
            row = sheet.getLastRowNum() + 1;
            topOfSearch = row;
            productRow = sheet.createRow(row);
            createColumn(productRow, style, 0, "Sponsored Products");
            createColumn(productRow, style, 1, "Bidding Adjustment");
            createColumn(productRow, style, 2, operation);
            createColumn(productRow, style, 3, campaignId);
            createColumn(productRow, style, 21, comp.get(aosProductno));
            createColumn(productRow, style, 22, "placementTop");
            // 循环得到组
            Map<String, Map<String, Object>> groupD = group.get(aosProductno);
            BigDecimal aosTopprice = BigDecimal.ZERO;
            for (String aosItemnumer : groupD.keySet()) {
                Map<String, Object> groupDmap = groupD.get(aosItemnumer);
                BigDecimal aosBid = ((BigDecimal)groupDmap.get("aos_bid")).setScale(2, RoundingMode.HALF_UP);
                Object aosShopsku = groupDmap.get("aos_shopsku");
                Object aosGroupstatus = groupDmap.get("aos_groupstatus");
                Object aosGroupdate = groupDmap.get("aos_groupdate");
                aosTopprice = max((BigDecimal)groupDmap.get("aos_topprice"), aosTopprice);
                String groupStatus = "enabled";
                if ((String.valueOf(aosGroupstatus)).equals(sign.available.name)) {
                    groupStatus = "enabled";
                } else {
                    groupStatus = "paused";
                }
                // 判断是否新组
                boolean newGroupFlag = false;
                String operationGroup = "";
                if (((Date)aosGroupdate).compareTo(aosDate) == 0) {
                    newGroupFlag = true;
                    operationGroup = "create";
                } else {
                    operationGroup = "update";
                }
                // 4.Ad Group Id 如果广告组为新增，ID直接等于广告组名称
                Object adGroupId;
                if (newGroupFlag) {
                    adGroupId = aosItemnumer;
                } else {
                    adGroupId = groupIdMap.getOrDefault(aosProductno + "~" + aosItemnumer, aosItemnumer);
                }
                if (aosItemnumer.equals(adGroupId)) {
                    operationGroup = "create";
                } else {
                    operationGroup = "update";
                }
                // 每个组要创建六行数据
                // 组第一行
                row = sheet.getLastRowNum() + 1;
                productRow = sheet.createRow(row);
                createColumn(productRow, style, 0, "Sponsored Products");
                createColumn(productRow, style, 1, "Ad Group");
                createColumn(productRow, style, 2, operationGroup);
                createColumn(productRow, style, 3, campaignId);
                createColumn(productRow, style, 4, adGroupId);
                createColumn(productRow, style, 10, aosItemnumer);
                createColumn(productRow, style, 14, groupStatus);
                createColumn(productRow, style, 17, aosBid);
                // 组第二行
                // 判断是否fba
                int size = 1;
                boolean flag = false;
                List<String> fbaList = fbaMap.get(aosItemnumer);
                if (fbaList != null && fbaList.size() > 0) {
                    flag = true;
                    size = fbaList.size();
                    row = sheet.getLastRowNum() + 1;
                    productRow = sheet.createRow(row);
                    createColumn(productRow, style, 0, "Sponsored Products");
                    createColumn(productRow, style, 1, "Product Ad");
                    createColumn(productRow, style, 3, campaignId);
                    createColumn(productRow, style, 4, adGroupId);
                    createColumn(productRow, style, 14, groupStatus);
                    createColumn(productRow, style, 16, aosItemnumer);
                    String adId =
                        itemIdMap.getOrDefault(aosProductno + "~" + aosItemnumer + "~" + aosItemnumer, aosItemnumer);
                    if ("".equals(adId)) {
                        adId = aosItemnumer;
                    }
                    if ((adId).equals(aosItemnumer)) {
                        operationGroup = "create";
                    } else {
                        operationGroup = "update";
                    }
                    createColumn(productRow, style, 2, operationGroup);
                    createColumn(productRow, style, 6, adId);
                }
                for (int i = 0; i < size; i++) {
                    row = sheet.getLastRowNum() + 1;
                    productRow = sheet.createRow(row);
                    createColumn(productRow, style, 0, "Sponsored Products");
                    createColumn(productRow, style, 1, "Product Ad");
                    createColumn(productRow, style, 3, campaignId);
                    createColumn(productRow, style, 4, adGroupId);
                    createColumn(productRow, style, 14, groupStatus);
                    String fbaShopSku = String.valueOf(flag ? fbaList.get(i) : aosShopsku);
                    createColumn(productRow, style, 16, fbaShopSku);
                    String adId =
                        itemIdMap.getOrDefault(aosProductno + "~" + aosItemnumer + "~" + fbaShopSku, fbaShopSku);
                    if ("".equals(adId)) {
                        adId = fbaShopSku;
                    }
                    if ((adId).equals(fbaShopSku)) {
                        operationGroup = "create";
                    } else {
                        operationGroup = "update";
                    }
                    createColumn(productRow, style, 2, operationGroup);
                    createColumn(productRow, style, 6, adId);
                }
                // 组第三行
                row = sheet.getLastRowNum() + 1;
                productRow = sheet.createRow(row);
                createColumn(productRow, style, 0, "Sponsored Products");
                createColumn(productRow, style, 1, "Product Targeting");
                createColumn(productRow, style, 3, campaignId);
                createColumn(productRow, style, 4, adGroupId);
                createColumn(productRow, style, 14, groupStatus);
                createColumn(productRow, style, 18, aosBid);
                createColumn(productRow, style, 24, "close-match");
                String targetId = targetIdMap.getOrDefault(aosProductno + "~" + aosItemnumer + "~" + "close-match", "");
                if ("".equals(targetId)) {
                    operationGroup = "create";
                } else {
                    operationGroup = "update";
                }
                createColumn(productRow, style, 2, operationGroup);
                createColumn(productRow, style, 8, targetId);
                // 组第四行
                row = sheet.getLastRowNum() + 1;
                productRow = sheet.createRow(row);
                createColumn(productRow, style, 0, "Sponsored Products");
                createColumn(productRow, style, 1, "Product Targeting");
                createColumn(productRow, style, 3, campaignId);
                createColumn(productRow, style, 4, adGroupId);
                createColumn(productRow, style, 14, groupStatus);
                createColumn(productRow, style, 18, aosBid);
                createColumn(productRow, style, 24, "loose-match");
                targetId = targetIdMap.getOrDefault(aosProductno + "~" + aosItemnumer + "~" + "loose-match", "");
                if ("".equals(targetId)) {
                    operationGroup = "create";
                } else {
                    operationGroup = "update";
                }
                createColumn(productRow, style, 2, operationGroup);
                createColumn(productRow, style, 8, targetId);
                // 组第五行
                row = sheet.getLastRowNum() + 1;
                productRow = sheet.createRow(row);
                createColumn(productRow, style, 0, "Sponsored Products");
                createColumn(productRow, style, 1, "Product Targeting");
                createColumn(productRow, style, 3, campaignId);
                createColumn(productRow, style, 4, adGroupId);
                createColumn(productRow, style, 14, groupStatus);
                createColumn(productRow, style, 18, aosBid);
                createColumn(productRow, style, 24, "complements");
                targetId = targetIdMap.getOrDefault(aosProductno + "~" + aosItemnumer + "~" + "complements", "");
                if ("".equals(targetId)) {
                    operationGroup = "create";
                } else {
                    operationGroup = "update";
                }
                createColumn(productRow, style, 2, operationGroup);
                createColumn(productRow, style, 8, targetId);
                // 组第六行
                row = sheet.getLastRowNum() + 1;
                productRow = sheet.createRow(row);
                createColumn(productRow, style, 0, "Sponsored Products");
                createColumn(productRow, style, 1, "Product Targeting");
                createColumn(productRow, style, 3, campaignId);
                createColumn(productRow, style, 4, adGroupId);
                createColumn(productRow, style, 14, groupStatus);
                createColumn(productRow, style, 18, aosBid);
                createColumn(productRow, style, 24, "substitutes");
                targetId = targetIdMap.getOrDefault(aosProductno + "~" + aosItemnumer + "~" + "substitutes", "");
                if ("".equals(targetId)) {
                    operationGroup = "create";
                } else {
                    operationGroup = "update";
                }
                createColumn(productRow, style, 2, operationGroup);
                createColumn(productRow, style, 8, targetId);
                // 对于置顶位置出价
                productRow = sheet.getRow(topOfSearch);
                createColumn(productRow, style, 23, aosTopprice);
            }
        }
        aosMktPopppcInitS.close();
    }

    private Map<String, String> getTargetIdMap(String aosOrgid) {
        DynamicObjectCollection list =
            QueryServiceHelper.query("aos_base_popid", "aos_productno,aos_itemnumer,aos_target,aos_targetid",
                new QFilter[] {new QFilter("aos_orgid", QCP.equals, aosOrgid)});
        return list.stream()
            .collect(Collectors.toMap(obj -> obj.getString("aos_productno") + "~" + obj.getString("aos_itemnumer") + "~"
                + obj.getString("aos_target"), obj -> obj.getString("aos_targetid"), (k1, k2) -> k1));
    }

    private Map<String, String> getItemIdMap(String aosOrgid) {
        DynamicObjectCollection list =
            QueryServiceHelper.query("aos_base_popid", "aos_productno,aos_itemnumer,aos_shopsku,aos_shopskuid",
                new QFilter[] {new QFilter("aos_orgid", QCP.equals, aosOrgid)});
        return list.stream()
            .collect(Collectors.toMap(obj -> obj.getString("aos_productno") + "~" + obj.getString("aos_itemnumer") + "~"
                + obj.getString("aos_shopsku"), obj -> obj.getString("aos_shopskuid"), (k1, k2) -> k1));
    }

    private Map<String, String> getGroupIdMap(String aosOrgid) {
        DynamicObjectCollection list =
            QueryServiceHelper.query("aos_base_popid", "aos_productno,aos_itemnumer," + "aos_groupid",
                new QFilter[] {new QFilter("aos_orgid", QCP.equals, aosOrgid)});
        return list.stream()
            .collect(Collectors.toMap(obj -> obj.getString("aos_productno") + "~" + obj.getString("aos_itemnumer"),
                obj -> obj.getString("aos_groupid"), (k1, k2) -> k1));
    }

    private Map<String, String> getProductIdMap(String aosOrgid) {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_base_popid", "aos_productno,aos_serialid",
            new QFilter[] {new QFilter("aos_orgid", QCP.equals, aosOrgid)});
        return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_productno"),
            obj -> obj.getString("aos_serialid"), (k1, k2) -> k1));
    }

    private void createColumn(XSSFRow headRow, XSSFCellStyle style, int column, Object value) {
        XSSFCell headCell = headRow.createCell(column);
        headCell.setCellStyle(style);
        headCell.setCellValue(String.valueOf(value));
    }

    private String upload(String entityName, XSSFWorkbook workbook) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        String fileName = entityName + sdf.format(new Date()) + ".xlsx";
        try {
            OutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            InputStream inputStream = parse(outputStream);
            TempFileCache tfc = CacheFactory.getCommonCacheFactory().getTempFileCache();
            // 超时时间半小时
            int timeout = 60 * 30;
            String path = tfc.saveAsUrl(fileName, inputStream, timeout);
            inputStream.close();
            return path;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 获取fba信息
     * 
     * @param aosOrgid 国别
     * @return result
     */
    private Map<String, List<String>> getFbaMap(String aosOrgid) {
        DynamicObjectCollection fbaObj =
            QueryServiceHelper.query("aos_base_fbaitem", "aos_itemid.number aos_itemnum,aos_shopsku",
                new QFilter[] {new QFilter("aos_orgid", QCP.equals, aosOrgid)});
        Map<String, List<DynamicObject>> aosItemnum =
            fbaObj.stream().collect(Collectors.groupingBy(obj -> obj.getString("aos_itemnum")));
        Map<String, List<String>> result = new HashMap<>(16);
        aosItemnum.forEach((k, v) -> {
            List<String> aosShopsku = v.stream().map(obj -> obj.getString("aos_shopsku")).collect(Collectors.toList());
            result.put(k, aosShopsku);
        });
        return result;
    }

    /**
     * 标识枚举类
     */
    private enum sign {
        /**
         * form
         */
        form("form"),
        /**
         * 老打印报告
         */
        export("aos_export"),
        /**
         * 可用
         */
        available("AVAILABLE"),
        /**
         * 新打印报告
         */
        exportNew("aos_exportnew"),
        /**
         * 关闭
         */
        close("aos_close"),
        /**
         * init按钮
         */
        init("aos_init");

        /**
         * 名称
         */
        private final String name;

        /**
         * 构造方法
         *
         * @param name 名称
         */
        sign(String name) {
            this.name = name;
        }
    }
}