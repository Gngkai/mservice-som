package mkt.image.test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EventObject;

import common.fnd.FndMsg;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.events.CustomEventArgs;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.plugin.AbstractFormPlugin;

/**
 * @author aosom
 * @version 测试表单插件
 */
public class AosMktTestForm extends AbstractFormPlugin {

    public static void outputDynamicRangeCombinations(int[] xValues, int[] yValues) {
        ArrayList<String> outputList = new ArrayList<>();
        for (int x : xValues) {
            for (int y : yValues) {
                StringBuilder output = new StringBuilder();
                if (x < xValues[0]) {
                    output.append("x<=").append(xValues[0]);
                } else if (x > xValues[1]) {
                    output.append("x>=").append(xValues[1]);
                } else {
                    output.append(xValues[0]).append("<=x<=").append(xValues[1]);
                }
                output.append(" and ");
                if (y < yValues[0]) {
                    output.append("y<=").append(yValues[0]);
                } else if (y > yValues[1]) {
                    output.append("y>=").append(yValues[1]);
                } else {
                    output.append(yValues[0]).append("<=y<=").append(yValues[1]);
                }
                output.append(" ;");
                outputList.add(output.toString());
            }
        }
        outputList.forEach(System.out::println);
    }

    @Override
    public void customEvent(CustomEventArgs e) {}

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        // 提交
        this.addItemClickListeners("aos_cal");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();

        if ("aos_cal".equals(control)) {
            generateRanges();
        }

    }

    public void generateRanges() {
        // 定义待筛选的坐标轴
        // 点击A
        BigDecimal xAxis1 = (BigDecimal)this.getModel().getValue("aos_x1");
        // 点击B
        BigDecimal xAxis2 = (BigDecimal)this.getModel().getValue("aos_x2");
        // 曝光A
        BigDecimal yAxis1 = (BigDecimal)this.getModel().getValue("aos_y1");
        // 曝光B
        BigDecimal yAxis2 = (BigDecimal)this.getModel().getValue("aos_y2");

        FndMsg.debug("xAxis1:" + xAxis1);
        FndMsg.debug("xAxis2:" + xAxis2);
        FndMsg.debug("yAxis1:" + yAxis1);
        FndMsg.debug("yAxis2:" + yAxis2);

        int[] xValues = {1, 2};
        int[] yValues = {3, 4};
        outputDynamicRangeCombinations(xValues, yValues);

        // 打印筛选结果
        DynamicObjectCollection aosEntryentityS = this.getModel().getEntryEntity("aos_entryentity");
        aosEntryentityS.clear();
        int i = 0;
        // for (CoordinateRange coordinateRange : result) {
        // this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
        // this.getModel().setValue("aos_xstart", coordinateRange.getXStart(), i);
        // this.getModel().setValue("aos_xend", coordinateRange.getXEnd(), i);
        // this.getModel().setValue("aos_ystart", coordinateRange.getYStart(), i);
        // this.getModel().setValue("aos_yend", coordinateRange.getYEnd(), i);
        // i++;
        // }

        this.getView().updateView();
    }

}
