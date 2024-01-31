package mkt.act.statement.calendar;

import common.fnd.FndGlobal;
import common.sal.util.QFBuilder;
import common.sal.util.SalUtil;
import kd.bos.bill.BillShowParameter;
import kd.bos.cache.CacheFactory;
import kd.bos.cache.TempFileCache;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.StyleCss;
import kd.bos.form.control.Control;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.SubEntryGrid;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
import kd.bos.form.field.FieldEdit;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author: GK
 * @create: 2024-01-30 10:30
 * @Description:活动日历报表
 */
@SuppressWarnings("unused")
public class EventsCalendarReport extends AbstractFormPlugin implements HyperLinkClickListener {
    /**单据*/
    protected static final String ENTRY_KEY = "aos_entryentity";
    /**子单据*/
    protected static final String SUBENTRY_KEY = "aos_subentryentity";
    protected static final String QUERY_KEY = "aos_query";
    protected static final String EXPORT_KEY = "export";
    @Override
    public void afterDoOperation(AfterDoOperationEventArgs eventArgs) {
        super.afterDoOperation(eventArgs);
        String operateKey = eventArgs.getOperateKey();

        if (QUERY_KEY.equals(operateKey)) {
            query();
        }
        else if (EXPORT_KEY.equals(operateKey)) {
            export();
        }

    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        SubEntryGrid subEntryGrid = this.getControl(SUBENTRY_KEY);
        subEntryGrid.addHyperClickListener(this);
    }

    /**
     * 导出
     */
    public void export(){
        //构建
        setExcelData();
    }

    public void setExcelData(){
        // 创建excel工作簿
        XSSFWorkbook workbook = new XSSFWorkbook();
        // 创建工作表sheet
        XSSFSheet sheet = workbook.createSheet();
        sheet.setDefaultColumnWidth(15);
        //创建父标题
        EntryGrid entry = this.getControl(ENTRY_KEY);// 单据体
        List<FieldEdit> s1 = entry.getFieldEdits();
        List<String> list_key = new ArrayList<>(); // 标识
        List<String> list_name = new ArrayList<>(); // name
        for (FieldEdit fiel : s1) {
            list_key.add(fiel.getKey());
            list_name.add(fiel.getProperty().getDisplayName().toString());
        }
        //下拉框对应的数据
        BidiMap<String, String> comboMapValue = SalUtil.getComboMapValue("aos_act_select_plan", "aos_seasonattr");
        BidiMap<String, String> typeMap = SalUtil.getComboMapValue("aos_base_stitem", "aos_type");

        DynamicObjectCollection entryRows = this.getModel().getDataEntity(true).getDynamicObjectCollection(ENTRY_KEY);
        // 设置标题行
        XSSFRow headRow = sheet.createRow(0);
        for (int i = 0; i < list_name.size(); i++) {
            XSSFCell cell = headRow.createCell(i);
            cell.setCellValue(list_name.get(i));
        }
        // 设置子单据标题行
        XSSFCell cell = headRow.createCell(list_name.size());
        cell.setCellValue("店铺");
        cell = headRow.createCell(list_name.size()+1);
        cell.setCellValue("活动类型");

        // 设置数据行
        for (DynamicObject dy : entryRows) {
            for (DynamicObject subRow : dy.getDynamicObjectCollection(SUBENTRY_KEY)) {
                XSSFRow dateRow = sheet.createRow(sheet.getLastRowNum() + 1);
                for (String key : list_key) {
                    if (key.equals("aos_seasonattr")) {
                        cell = dateRow.createCell(list_key.indexOf(key));
                        if (FndGlobal.IsNotNull(dy.getString(key))){
                            cell.setCellValue(comboMapValue.get(dy.getString(key)));
                        }
                    }
                    else if (key.equals("aos_type")){
                        cell = dateRow.createCell(list_key.indexOf(key));
                        String value = dy.getString(key);
                        if (FndGlobal.IsNotNull(value)){
                            cell.setCellValue(typeMap.get(value));
                        }
                    }
                    else {
                        cell = dateRow.createCell(list_key.indexOf(key));
                        cell.setCellValue(dy.getString(key));
                    }
                }
                dateRow.createCell(list_key.size()).setCellValue(subRow.getString("aos_shop"));
                dateRow.createCell(list_key.size()+1).setCellValue(subRow.getString("aos_act_type"));
            }
        }
        // 下载
        downloadXSSFWorkbook(workbook,"活动日历报表");
    }

    private  void downloadXSSFWorkbook(XSSFWorkbook workbook, String fileName) {
        ByteArrayOutputStream outputStream = null;
        InputStream inputStream = null;
        try {
            TempFileCache tempfile = CacheFactory.getCommonCacheFactory().getTempFileCache();
            int timeout = 60 * 10;// 超时时间10分钟
            outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            inputStream = new ByteArrayInputStream(outputStream.toByteArray());
            String url = tempfile.saveAsUrl(
                    "引出数据_" + fileName + "_" + DateFormatUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss") + ".xlsx",
                    inputStream, timeout);
            this.getView().download(url);
        } catch (IOException e) {
            this.getView().showTipNotification(SalUtil.getExceptionStr(e));
            e.printStackTrace();
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                this.getView().showTipNotification(SalUtil.getExceptionStr(e));
                e.printStackTrace();
            }
        }
    }

    /**
     * 查询
     */
    public void query() {
        this.getModel().deleteEntryData(ENTRY_KEY);
        //获取滞销sku
        Map<String, String> unsalItemMap = new HashMap<>();
        boolean unsalItemFilter = getUnsalItemMap(unsalItemMap);
        //获取活动选品表数据
        Map<String, List<DynamicObject>> actData = getActData(unsalItemMap,unsalItemFilter);
        //添加到报表中
        setReportData(actData,unsalItemMap);
        getView().updateView(ENTRY_KEY);
        getView().updateView(SUBENTRY_KEY);
    }

    /**
     * 设置报表数据
     * @param actData   活动选品表数据
     * @param unsalItemMap  滞销sku
     */
    public void setReportData(Map<String, List<DynamicObject>> actData,Map<String, String> unsalItemMap){
        DynamicObjectCollection entryRows = this.getModel().getDataEntity(true).getDynamicObjectCollection(ENTRY_KEY);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (Map.Entry<String, List<DynamicObject>> entry : actData.entrySet()) {
            DynamicObject actInfoRow = entry.getValue().get(0);
            //新增行
            DynamicObject entryNewRow = entryRows.addNew();
            String orgNum = actInfoRow.getString("aos_nationality");
            entryNewRow.set("aos_nationality",orgNum);
            entryNewRow.set("aos_category_stat1",actInfoRow.get("aos_category_stat1"));
            String itemnum = actInfoRow.getString("aos_itemnum");
            entryNewRow.set("aos_itemnum",itemnum);
            entryNewRow.set("aos_itemname",actInfoRow.get("aos_itemname"));
            entryNewRow.set("aos_is_saleout",actInfoRow.get("aos_is_saleout"));
            System.out.println("actInfoRow.get(\"aos_seasonattr\") = " + actInfoRow.get("aos_seasonattr"));
            entryNewRow.set("aos_seasonattr",actInfoRow.get("aos_seasonattr"));
            entryNewRow.set("aos_type",unsalItemMap.get(orgNum+"/"+itemnum));
            entryNewRow.set("aos_times",entry.getValue().size());

            //子单据行
            DynamicObjectCollection subEntryRows = entryNewRow.getDynamicObjectCollection(SUBENTRY_KEY);
            for (DynamicObject actSubRow : entry.getValue()) {
                DynamicObject subNewRow = subEntryRows.addNew();
                subNewRow.set("aos_shop",actSubRow.get("aos_shop"));
                StringJoiner info = new StringJoiner("/");
                info.add(actSubRow.getString("billno"));
                //活动类型
                if (FndGlobal.IsNotNull(actSubRow.getString("aos_acttype"))){
                    info.add(actSubRow.getString("aos_acttype"));
                }
                else {
                    info.add("");
                }


                //开始时间
                if (actSubRow.get("aos_l_startdate") != null) {
                    info.add(sdf.format(actSubRow.getDate("aos_l_startdate")));
                }
                else {
                    info.add("");
                }
                //结束时间
                if (actSubRow.get("aos_enddate") != null) {
                    info.add(sdf.format(actSubRow.getDate("aos_enddate")));
                }
                else {
                    info.add("");
                }

                //活动价格
                if (FndGlobal.IsNotNull(actSubRow.get("aos_actprice"))) {
                    info.add(actSubRow.getBigDecimal("aos_actprice").toString());
                }
                else {
                    info.add("");
                }
                //折扣力度
                if (FndGlobal.IsNotNull(actSubRow.get("aos_disstrength"))) {
                    info.add(actSubRow.getBigDecimal("aos_disstrength").toString());
                }
                else {
                    info.add("");
                }
                subNewRow.set("aos_act_type",info.toString());
                subNewRow.set("aos_url",actSubRow.getString("id"));
            }
        }
    }

    /**
     * 从活动选品表种查询出相关数据
     */
    public Map<String,List<DynamicObject>> getActData(Map<String, String> unsalItemMap,boolean unsalItemFilter){
        //获取过滤条件
        QFBuilder filter = getFilter();
        //获取活动选品表数据
        StringJoiner select = new StringJoiner(",");
        select.add("id");
        select.add("billno");
        select.add("aos_nationality.number aos_nationality");
        select.add("aos_sal_actplanentity.aos_category_stat1 aos_category_stat1");
        select.add("aos_sal_actplanentity.aos_itemnum.number aos_itemnum");
        select.add("aos_sal_actplanentity.aos_itemnum.name aos_itemname");
        select.add("aos_sal_actplanentity.aos_is_saleout aos_is_saleout");
        select.add("aos_sal_actplanentity.aos_seasonattr aos_seasonattr");
        select.add("aos_sal_actplanentity.aos_l_startdate aos_l_startdate");
        select.add("aos_sal_actplanentity.aos_enddate aos_enddate");
        select.add("aos_acttype.number aos_acttype");
        select.add("aos_sal_actplanentity.aos_actprice aos_actprice");
        select.add("aos_shop.number aos_shop");
        select.add("aos_disstrength");
        DynamicObjectCollection actRows = QueryServiceHelper.query("aos_act_select_plan", select.toString(), filter.toArray());
        Map<String,List<DynamicObject>> results = new HashMap<>(actRows.size());
        for (DynamicObject row : actRows) {
            StringJoiner key = new StringJoiner("/");
            String orgNum = row.getString("aos_nationality");
            if (FndGlobal.IsNull(orgNum)) {
                continue;
            }
            key.add(orgNum);
            String itemnum = row.getString("aos_itemnum");
            if (FndGlobal.IsNull(itemnum)) {
                continue;
            }
            key.add(itemnum);
            if (unsalItemFilter && !unsalItemMap.containsKey(key.toString())) {
                continue;
            }
            results.computeIfAbsent(key.toString(), k -> new ArrayList<>()).add(row);
        }
        return results;
    }

    /**
     * 获取过滤条件
     */
    public QFBuilder getFilter() {
        QFBuilder builder = new QFBuilder();
        //活动状态不为手动关闭
        builder.add("aos_actstatus","!=","C");
        //国家
        DynamicObjectCollection filterInfoRows = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_sel_org");
        List<String> countryList;
        if (filterInfoRows.size() > 0) {
            countryList = filterInfoRows
                    .stream()
                    .map(row -> row.getDynamicObject("fbasedataid").getString("id"))
                    .collect(Collectors.toList());

            builder.add("aos_nationality", QFilter.in, countryList);
        }
        //渠道
        filterInfoRows = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_sel_channel");
        if (filterInfoRows.size() > 0) {
            List<String> idList = filterInfoRows
                    .stream()
                    .map(row -> row.getDynamicObject("fbasedataid").getString("id"))
                    .collect(Collectors.toList());
            builder.add("aos_channel", QFilter.in, idList);
        }
        //大类
        filterInfoRows = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_sel_cate");
        if (filterInfoRows.size() > 0) {
            List<String> cateList = filterInfoRows
                    .stream()
                    .map(row -> row.getDynamicObject("fbasedataid").getLocaleString("name").getLocaleValue_zh_CN())
                    .collect(Collectors.toList());
            builder.add("aos_sal_actplanentity.aos_category_stat1", QFilter.in, cateList);
        }
        //品名
        filterInfoRows = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_sel_name");
        if (filterInfoRows.size() > 0) {
            List<String> nameList = filterInfoRows
                    .stream()
                    .map(row -> row.getDynamicObject("fbasedataid").getString("name"))
                    .collect(Collectors.toList());
            builder.add("aos_sal_actplanentity.aos_itemnum.name", QFilter.in, nameList);
        }
        //sku
        filterInfoRows = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_sel_sku");
        if (filterInfoRows.size() > 0) {
            List<String> skuList = filterInfoRows
                    .stream()
                    .map(row -> row.getDynamicObject("fbasedataid").getString("id"))
                    .collect(Collectors.toList());
            builder.add("aos_sal_actplanentity.aos_itemnum", QFilter.in, skuList);
        }
        //爆品
        Object hot = getModel().getValue("aos_sel_hot");
        if (FndGlobal.IsNotNull(hot)) {
            builder.add("aos_sal_actplanentity.aos_is_saleout", "=", hot);
        }
        //季节属性
        filterInfoRows = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_sel_season");
        if (filterInfoRows.size() > 0) {
            List<String> seasonList = filterInfoRows
                    .stream()
                    .map(row -> row.getDynamicObject("fbasedataid").getString("number"))
                    .collect(Collectors.toList());
            System.out.println("seasonList = " + seasonList);
            builder.add("aos_sal_actplanentity.aos_seasonattr", QFilter.in, seasonList);
        }
        //开始日期 的开始日期
        Object startDate = getModel().getValue("aos_sel_start_s");
        if (FndGlobal.IsNotNull(startDate)) {
            builder.add("aos_sal_actplanentity.aos_l_startdate", ">=", startDate);
        }
        //开始日期 的结束日期
        Object endDate = getModel().getValue("aos_sel_start_e");
        if (FndGlobal.IsNotNull(endDate)) {
            builder.add("aos_sal_actplanentity.aos_l_startdate", "<=", endDate);
        }
        //结束日期 的开始日期
        startDate = getModel().getValue("aos_sel_end_s");
        if (FndGlobal.IsNotNull(startDate)) {
            builder.add("aos_sal_actplanentity.aos_enddate", ">=", startDate);
        }
        //结束日期 的结束日期
        endDate = getModel().getValue("aos_sel_end_e");
        if (FndGlobal.IsNotNull(endDate)) {
            builder.add("aos_sal_actplanentity.aos_enddate", "<=", endDate);
        }
        return builder;
    }

    /**
     * 获取滞销货号
     * @return  滞销货号
     */
    private boolean getUnsalItemMap(Map<String, String> unsalItemMap) {
        //判断是否进行滞销货号过滤
        boolean isUnsalable ;
        //国家
        DynamicObjectCollection filterInfoRows = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_sel_org");
        List<String> countryList = new ArrayList<>();
        if (filterInfoRows.size() > 0) {
            countryList = filterInfoRows
                    .stream()
                    .map(row -> row.getDynamicObject("fbasedataid").getString("id"))
                    .collect(Collectors.toList());
        }

        Object unsalable = getModel().getValue("aos_sel_type");
        List<String> collect = Arrays.stream(String.valueOf(unsalable).split(","))
                .filter(FndGlobal::IsNotNull)
                .collect(Collectors.toList());
        //获取滞销货号
        QFBuilder unsalableBuilder = new QFBuilder();
        if (countryList.size() > 0) {
            unsalableBuilder.add("aos_orgid", QFilter.in, countryList);
        }
        if (collect.size() > 0) {
            unsalableBuilder.add("aos_type", QFilter.in, collect);
            isUnsalable = true;
        }
        else {
            isUnsalable = false;
        }
        StringJoiner str = new StringJoiner(",");
        str.add("aos_orgid.number aos_orgid");
        str.add("aos_itemid.number aos_itemid");
        str.add("aos_type");

        DynamicObjectCollection unsaleRults = QueryServiceHelper.query("aos_base_stitem", str.toString(), unsalableBuilder.toArray());
        for (DynamicObject unsaleRult : unsaleRults) {
            str = new StringJoiner("/");
            str.add(unsaleRult.getString("aos_orgid"));
            str.add(unsaleRult.getString("aos_itemid"));
            unsalItemMap.put(str.toString(), unsaleRult.getString("aos_type"));
        }
        return isUnsalable;
    }

    @Override
    public void hyperLinkClick(HyperLinkClickEvent event) {
        String fieldName = event.getFieldName();
        int rowIndex = event.getRowIndex();
        if ("aos_act_type".equals(fieldName) && rowIndex >= 0) {
            int currentRowIndex = getModel().getEntryCurrentRowIndex(ENTRY_KEY);
            //创建弹出单据页面对象，并赋值
            BillShowParameter billShowParameter = FndGlobal.CraeteBillForm(this,"aos_act_select_plan","aos_sal_act",new HashMap<>());
            //设置弹出子单据页面的样式，高600宽800
            StyleCss inlineStyleCss = new StyleCss();
            inlineStyleCss.setHeight("800");
            inlineStyleCss.setWidth("1500");
            billShowParameter.getOpenStyle().setInlineStyleCss(inlineStyleCss);
            Object value = getModel().getValue("aos_url", currentRowIndex,rowIndex);

            billShowParameter.setPkId(value);
            //弹窗子页面和父页面绑定
            this.getView().showForm(billShowParameter);
        }

    }
}
