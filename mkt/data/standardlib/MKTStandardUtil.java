package mkt.data.standardlib;

import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.cache.CacheFactory;
import kd.bos.cache.TempFileCache;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.datamodel.IDataModel;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.servicehelper.QueryServiceHelper;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

public class MKTStandardUtil {

    // 获取组别下所有品名下的所有sku数量
    public static int getItemQtyByGroup(Map<String, Integer> itemQty, Set<String> allItemNameSet) {
        if (allItemNameSet == null) return 0;
        int total = 0;
        for (String aos_category : allItemNameSet) {
            total += itemQty.getOrDefault(aos_category, 0);
        }
        return total;
    }


    /**
     * 合计品名下sku数量
     * @return 品名下sku的数量
     */
    public static Map<String, Integer> sumItemByItemName() {
        DataSet materialDataSet = QueryServiceHelper.queryDataSet("sumItemByItemName", "bd_materialgroupdetail", "group.name aos_category,material.name aos_itemnamecn,1 aos_count", null, null);
        materialDataSet = materialDataSet.groupBy(new String[]{"aos_category", "aos_itemnamecn"}).sum("aos_count").finish();
        Map<String, Integer> result = new HashMap<>();
        while (materialDataSet.hasNext()) {
            Row next = materialDataSet.next();
            String aos_category = next.getString("aos_category");
            String aos_itemnamecn = next.getString("aos_itemnamecn");
            Integer aos_count = next.getInteger("aos_count");
            result.put(aos_category + "~" + aos_itemnamecn, aos_count);
        }
        return result;
    }


    // 创建头信息等
    public static XSSFSheet createXSSFSheet(XSSFWorkbook workbook, List<String> fieldNameList) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
//		style.setAlignment(HorizontalAlignment.LEFT);
        style.setFillForegroundColor(IndexedColors.GREY_40_PERCENT.getIndex());
        // 创建工作表sheet
        XSSFSheet sheet = workbook.createSheet();
        sheet.setDefaultColumnWidth(15);
        XSSFRow headRow = sheet.createRow(0);
        // 创建标题
        for (int i = 0; i < fieldNameList.size(); i++) {
            String displayName = fieldNameList.get(i);
            XSSFCell cell = headRow.createCell(i);
            cell.setCellStyle(style);
            cell.setCellValue(displayName);
        }
        return sheet;
    }

    public static String getLibName(String key) {
        String value = "";
        switch (key) {
            case "aos_mkt_standard" : value = "文案标准库"; break;
            case "aos_mkt_point" : value = "关键词库"; break;
            case "aos_mkt_designstd" : value = "设计标准库"; break;
            case "aos_mkt_photostd" : value = "摄影标准库"; break;
            case "aos_mkt_videostd" : value = "摄像标准库"; break;
            case "aos_mkt_viewstd" : value = "布景标准库"; break;
        }
        return value;
    }


    // 下载工作簿
    public static void downloadXSSFWorkbook(AbstractFormPlugin obj, XSSFWorkbook workbook, String fileName) {
        ByteArrayOutputStream outputStream = null;
        InputStream inputStream = null;
        try {
            TempFileCache tempfile = CacheFactory.getCommonCacheFactory().getTempFileCache();
            int timeout = 60*10;//超时时间10分钟
            outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            inputStream = new ByteArrayInputStream(outputStream.toByteArray());
            String url = tempfile.saveAsUrl("引出数据_" + fileName + "_" + DateFormatUtils.format(new Date(),  "yyyy-MM-dd HH:mm:ss") + ".xlsx", inputStream, timeout);
            obj.getView().download(url);
        } catch (IOException e) {
            obj.getView().showTipNotification(SalUtil.getExceptionStr(e));
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
                obj.getView().showTipNotification(SalUtil.getExceptionStr(e));
                e.printStackTrace();
            }
        }
    }


    static void setCellValue(IDataModel model, XSSFSheet sheet, List<String> fieldMarkList) {
        DynamicObject dataEntity = model.getDataEntity(true);
        DynamicObjectCollection aos_entryentity = dataEntity.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject obj : aos_entryentity) {
            int row = sheet.getLastRowNum() + 1;
            XSSFRow dataRow = sheet.createRow(row);
            for (int i = 0; i < fieldMarkList.size(); i++) {
                Object value = obj.get(fieldMarkList.get(i));
                if ("aos_stdlib".equals(fieldMarkList.get(i))) {
                    setCellValue(dataRow, getLibName(value.toString()), i);
                } else {
                    setCellValue(dataRow, value, i);
                }
            }
        }
    }


    private static void setCellValue(XSSFRow dataRow, Object value, int index) {
        XSSFCell cell = dataRow.createCell(index);
        if (value instanceof String) {
            cell.setCellValue((String)value);
        } else if (value instanceof Integer) {
            cell.setCellValue((Integer)value);
        } else if (value instanceof BigDecimal) {
            cell.setCellValue(((BigDecimal) value).doubleValue());
        } else if (value instanceof DynamicObject) {
            cell.setCellValue(((DynamicObject) value).getString("name"));
        }
    }
}
