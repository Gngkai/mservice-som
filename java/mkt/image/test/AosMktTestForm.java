package mkt.image.test;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;

import common.fnd.FndMsg;
import kd.bos.ext.form.control.CustomControl;
import kd.bos.form.events.CustomEventArgs;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.plugin.AbstractFormPlugin;

/**
 * @author aosom
 * @version 测试表单插件
 */
public class AosMktTestForm extends AbstractFormPlugin {
    public final static String KEY1 = "点击";
    public final static String KEY2 = "曝光";
    /**
     * 控制气泡的大小
     */
    public final static double GRADIENT = Double.parseDouble(FndMsg.getStatic("PUPSIZE"));
    public final static int QTY = Integer.parseInt(FndMsg.getStatic("PUPQTY"));

    public static ArrayList<String> outputDynamicRangeCombinations(BigDecimal[] xValues, BigDecimal[] yValues) {
        ArrayList<String> outputList = new ArrayList<>();
        int i = 0;
        for (BigDecimal x : xValues) {
            int k = 0;
            for (BigDecimal y : yValues) {
                StringBuilder output = new StringBuilder();
                if (x.compareTo(xValues[0]) == 0) {
                    // X第一个
                    first(KEY1, output, xValues[i]);
                    if (y.compareTo(yValues[0]) == 0) {
                        // Y第一个
                        first(KEY2, output, yValues[k]);
                    } else if (y.compareTo(yValues[k]) == 0) {
                        // Y其他非最后一个
                        other(KEY2, output, yValues[k - 1], yValues[k]);
                    }
                    end(outputList, output);
                    if (k == yValues.length - 1) {
                        output = new StringBuilder();
                        // Y最后一个
                        first(KEY1, output, xValues[i]);
                        last(KEY2, output, yValues[k]);
                        end(outputList, output);
                    }
                } else if (x.compareTo(xValues[i]) == 0) {
                    // X其他非最后一个
                    other(KEY1, output, xValues[i - 1], xValues[i]);
                    if (y.compareTo(yValues[0]) == 0) {
                        // Y第一个
                        first(KEY2, output, yValues[k]);
                    } else if (y.compareTo(yValues[k]) == 0) {
                        // Y其他非最后一个
                        other(KEY2, output, yValues[k - 1], yValues[k]);
                    }
                    end(outputList, output);
                    if (k == yValues.length - 1) {
                        output = new StringBuilder();
                        // Y最后一个
                        other(KEY1, output, xValues[i - 1], xValues[i]);
                        last(KEY2, output, yValues[k]);
                        end(outputList, output);
                    }
                }
                k++;
            }
            i++;
        }
        // X最后一个
        int lastY = 0;
        for (BigDecimal y : yValues) {
            StringBuilder output = new StringBuilder();
            // 最后一个
            last(KEY1, output, xValues[xValues.length - 1]);
            if (y.compareTo(yValues[0]) == 0) {
                // Y第一个
                first(KEY2, output, yValues[lastY]);
            } else if (y.compareTo(yValues[lastY]) == 0) {
                // Y其他非最后一个
                other(KEY2, output, yValues[lastY - 1], yValues[lastY]);
            }
            end(outputList, output);
            if (lastY == yValues.length - 1) {
                output = new StringBuilder();
                // Y最后一个
                last(KEY1, output, xValues[xValues.length - 1]);
                last(KEY2, output, yValues[lastY]);
                end(outputList, output);
            }
            lastY++;
        }
        outputList.forEach(System.out::println);
        return outputList;
    }

    private static void end(ArrayList<String> outputList, StringBuilder output) {
        output.append(" ;");
        outputList.add(output.toString());
    }

    private static void first(String axis, StringBuilder output, Object value) {
        output.append(axis).append("<").append(value);
        if (KEY1.equals(axis)) {
            output.append(" & ");
        }
    }

    private static void other(String axis, StringBuilder output, Object start, Object end) {
        output.append(start).append("<").append(axis).append("<=").append(end);
        if (KEY1.equals(axis)) {
            output.append(" & ");
        }
    }

    private static void last(String axis, StringBuilder output, Object value) {
        output.append(axis).append(">").append(value);
        if (KEY1.equals(axis)) {
            output.append(" & ");
        }
    }

    public static double[][] generateRandomData(int numData) {
        double[][] data = new double[numData][3];
        Random random = new Random();
        DecimalFormat decimalFormat = new DecimalFormat("#.00");
        for (int i = 0; i < numData; i++) {
            // 随机生成 0 到 2 之间的数
            data[i][0] = Double.parseDouble(decimalFormat.format(random.nextDouble() * 2.0));
            // 随机生成 0 到 2 之间的数
            data[i][1] = Double.parseDouble(decimalFormat.format(random.nextDouble() * 2.0));
            data[i][2] = GRADIENT;
        }
        printArray(data);
        return data;
    }

    public static void printArray(double[][] array) {
        System.out.print("{{");
        for (int i = 0; i < array.length; i++) {
            System.out.print("{");
            for (int j = 0; j < array[i].length; j++) {
                System.out.print(array[i][j]);
                if (j < array[i].length - 1) {
                    System.out.print(", ");
                }
            }
            System.out.print("}");
            if (i < array.length - 1) {
                System.out.print(", ");
            }
        }
        System.out.println("}}");
    }

    @Override
    public void afterBindData(EventObject e) {
        CustomControl aosPupple = this.getView().getControl("aos_pupple");
        // 传入参数
        Map<String, Object> para = new HashMap<>(16);
        para.put("text", "推广关键词库分类器");
        String[] keys = {"double1", "double2", "double3", "double4"};
        double[][][] values = {
            // 藤编
            generateRandomData(QTY),
            // 运动
            generateRandomData(QTY),
            // 家具
            generateRandomData(QTY),
            // 宠物
            generateRandomData(QTY)};
        for (int i = 0; i < keys.length; i++) {
            para.put(keys[i], values[i]);
        }
        aosPupple.setData(para);
    }

    @Override
    public void customEvent(CustomEventArgs e) {
        // 返回参数
        // 设计器上自定义控件的标识
        String key = e.getKey();
        // 前端通过model.invoke传给后端的数据
        String args = e.getEventArgs();
        // 前端通过model.invoke定义的事件名
        String eventName = e.getEventName();
        this.getModel().setValue(eventName, args);
    }

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
            // generateRandomData(5);
            generateRanges();
        }
    }

    public void generateRanges() {
        List<BigDecimal> xlist = new ArrayList<>();
        List<BigDecimal> ylist = new ArrayList<>();
        // 点击A
        xlist.add((BigDecimal)this.getModel().getValue("aos_x1"));
        // 点击B
        xlist.add((BigDecimal)this.getModel().getValue("aos_x2"));
        // 曝光A
        ylist.add((BigDecimal)this.getModel().getValue("aos_y1"));
        // 曝光B
        ylist.add((BigDecimal)this.getModel().getValue("aos_y2"));
        // 假设你已经将数据添加到了 list 中
        // 转换为数组
        Collections.sort(xlist);
        Collections.sort(ylist);
        BigDecimal[] xValues = xlist.toArray(new BigDecimal[xlist.size()]);
        BigDecimal[] yValues = ylist.toArray(new BigDecimal[ylist.size()]);
        ArrayList<String> outputList = outputDynamicRangeCombinations(xValues, yValues);
        // 打印筛选结果
        this.getModel().deleteEntryData("aos_entryentity");
        int i = 0;
        for (String key : outputList) {
            this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
            this.getModel().setValue("aos_condition", key, i);
            i++;
        }
        this.getView().updateView();
    }
}
