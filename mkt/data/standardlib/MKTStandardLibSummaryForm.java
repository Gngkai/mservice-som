package mkt.data.standardlib;

import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.field.FieldEdit;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.*;

/**
 * lch
 * 2022-05-05
 * 汇总表动态表单插件
 */
public class MKTStandardLibSummaryForm extends AbstractFormPlugin {
    @Override
    public void registerListener(EventObject e) {
        this.addItemClickListeners("aos_toolbarap");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        String itemKey = evt.getItemKey();
        if ("aos_query".equals(itemKey)) {
            // 查询
            // 非手动关闭
            DynamicObject dataEntity = this.getModel().getDataEntity(true);
            String aos_stdlibs = dataEntity.getString("aos_stdlibs");
            queryArticleStandard(aos_stdlibs);
        }

        if ("aos_export".equals(itemKey)) {
            export();
        }
    }

    private void queryArticleStandard(String aos_stdlibs) {
        this.getModel().deleteEntryData("aos_entryentity");
        // 品名下物料数量
        Map<String, Integer> itemQtyMap = MKTStandardUtil.sumItemByItemName();
        if (null != aos_stdlibs && !"".equals(aos_stdlibs) && !"null".equals(aos_stdlibs) ) {
            setHz(itemQtyMap, aos_stdlibs);
        } else {
            // 查全部
            Map<String, String> comboMap = SalUtil.getComboMap("aos_mkt_stdsummary", "aos_stdlibs");
            for (String key:comboMap.keySet()) {
                setHz(itemQtyMap, comboMap.get(key));
            }
        }
    }


    private void setHz(Map<String, Integer> itemQtyMap, String libs) {
        DataSet allItemNameDataSet = QueryServiceHelper.queryDataSet("1111", libs, "aos_groupid,aos_category1_name||','||aos_category2_name||','||aos_category3_name aos_category,aos_itemnamecn", new QFilter[]{
                new QFilter("billstatus", QCP.not_equals, "E"),
                new QFilter("aos_groupid", QCP.not_equals, null)
        }, null);
        DataSet completedItemNameDataSet = QueryServiceHelper.queryDataSet("2222", libs, "aos_groupid,aos_category1_name||','||aos_category2_name||','||aos_category3_name aos_category,aos_itemnamecn,case when billstatus = 'C' then 1 else 0 end as billstatus", new QFilter[]{
                new QFilter("aos_groupid", QCP.not_equals, null)
        }, null);
        completedItemNameDataSet = completedItemNameDataSet.groupBy(new String[]{"aos_groupid", "aos_category", "aos_itemnamecn"}).min("billstatus").finish();
        completedItemNameDataSet = completedItemNameDataSet.filter("billstatus = 1");// 全部国家完成才算完成
        Map<String, Set<String>> allItemNameMap = sumItemNameByGroup(allItemNameDataSet);
        Map<String, Set<String>> completedItemNameMap = sumItemNameByGroup(completedItemNameDataSet);
        for (String groupId:allItemNameMap.keySet()) {
            int index = this.getModel().createNewEntryRow("aos_entryentity");
            this.getModel().setValue("aos_groupid", groupId, index);
            this.getModel().setValue("aos_stdlib", libs, index);

            // 总品名数
            Set<String> allItemNameSet = allItemNameMap.get(groupId);
            int aos_total = allItemNameSet != null ? allItemNameSet.size() : 0;
            this.getModel().setValue("aos_total", aos_total, index);

            // 完成数
            Set<String> completedItemNameSet = completedItemNameMap.get(groupId);
            int aos_completeamt = completedItemNameSet != null ? completedItemNameSet.size() : 0;
            this.getModel().setValue("aos_completeamt", aos_completeamt, index);

            float aos_completerate = 0;
            if (aos_total != 0) {
                aos_completerate = (float)aos_completeamt/aos_total;
            }
            // 完成率
            this.getModel().setValue("aos_completerate", aos_completerate, index);

            // 未完成数
            this.getModel().setValue("aos_undoneamt", aos_total - aos_completeamt, index);

            // SKU覆盖率
            // 所有非手动关闭状态的所有品名的SKU；
            int itemQtyAll = MKTStandardUtil.getItemQtyByGroup(itemQtyMap, allItemNameSet);
            // 已完成的品名涉及SKU
            int itemQtyCompleted = MKTStandardUtil.getItemQtyByGroup(itemQtyMap, completedItemNameSet);
            if (itemQtyAll != 0) {
                this.getModel().setValue("aos_coveragerate", (float)itemQtyCompleted/itemQtyAll, index);
            }
        }
    }


    /**
     * 组别维度下合计所有非手动关闭状态的品名数量
     * @return 组别 下品名数量
     */
    public Map<String, Set<String>> sumItemNameByGroup(DataSet libDataSet) {
        Map<String, Set<String>>  result = new HashMap<>();
        while (libDataSet.hasNext()) {
            Row next = libDataSet.next();
            String aos_groupid = next.getString("aos_groupid");
            String aos_category = next.getString("aos_category");
            String aos_itemnamecn = next.getString("aos_itemnamecn");
            Set<String> set = result.computeIfAbsent(aos_groupid, k -> new HashSet<>());
            set.add(aos_category + "~" + aos_itemnamecn);
        }
        return result;
    }

    // 导出
    public void export() {
        EntryGrid aos_itementity = this.getControl("aos_entryentity");
        List<FieldEdit> fieldEdits = aos_itementity.getFieldEdits();
        List<String> fieldNameList = new ArrayList<>();
        List<String> fieldMarkList = new ArrayList<>();
        for (FieldEdit fieldEdit:fieldEdits) {
            String displayName = fieldEdit.getProperty().getDisplayName().getLocaleValue();
            String fieldKey = fieldEdit.getFieldKey();
            fieldNameList.add(displayName);
            fieldMarkList.add(fieldKey);
        }
        XSSFWorkbook workbook = new XSSFWorkbook();
        // 创建头 返回sheet
        XSSFSheet xssfSheet = MKTStandardUtil.createXSSFSheet(workbook, fieldNameList);
        // 设置单元格的值
        MKTStandardUtil.setCellValue(this.getModel(), xssfSheet, fieldMarkList);
        // 下载
        MKTStandardUtil.downloadXSSFWorkbook(this, workbook, "国别表");
    }
}
