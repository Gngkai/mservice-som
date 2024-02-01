package mkt.image.test;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.tree.TreeNode;
import kd.bos.form.control.Html;
import kd.bos.form.control.TreeView;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.servicehelper.QueryServiceHelper;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.DefaultXYDataset;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * @author aosom
 * @since 2024/2/1 9:35
 */
public class AosMktTestBill extends AbstractBillPlugIn {

    public final static String AOS_CAL = "aos_cal";

    public static List<String[]> cartesianProduct(List<String[]> arrays) {
        List<String[]> result = new ArrayList<>();
        cartesianProductHelper(result, new String[arrays.size()], arrays, 0);
        return result;
    }

    private static void cartesianProductHelper(List<String[]> result, String[] current, List<String[]> arrays,
        int depth) {
        if (depth == arrays.size()) {
            result.add(current.clone());
            return;
        }

        for (String str : arrays.get(depth)) {
            current[depth] = str;
            cartesianProductHelper(result, current, arrays, depth + 1);
        }
    }

    private static double[][] generateRandomData(int count) {
        double[][] data = new double[2][count];
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            double x = random.nextDouble();
            double y = random.nextDouble();
            data[0][i] = x;
            data[1][i] = y;
        }
        return data;
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        try {
            if (AOS_CAL.equals(control)) {
                aosCal();
                aosTest();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void aosTest() throws IOException {
        DefaultXYDataset dataset = new DefaultXYDataset();
        // 生成100个随机数据点
        double[][] data = generateRandomData(100);
        dataset.addSeries("Scatter Plot", data);
        // 创建散点图
        JFreeChart scatterPlot = ChartFactory.createScatterPlot(
            // 标题
            "Scatter Plot Example",
            // X轴标签
            "X",
            // Y轴标签
            "Y",
            // 数据集
            dataset);
        // 将散点图生成为图片，并以Base64编码的方式嵌入到HTML代码中
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ChartUtilities.writeChartAsPNG(outputStream, scatterPlot, 800, 600);
        byte[] imageBytes = outputStream.toByteArray();
        String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
        // 生成HTML代码
        String html = "<html><body><img src='data:image/png;base64," + base64Image + "'/></body></html>";
        System.out.println(html);
        Html htmlControl = this.getView().getControl("aos_htmlap");
        htmlControl.setConent(html);
    }

    private void aosCal() {
        TreeView treeView = this.getView().getControl("aos_treeview");
        treeView.deleteAllNodes();
        DynamicObjectCollection tmpS = QueryServiceHelper.query("aos_mkt_pop_tmp",
            "aos_itemid,aos_roi,aos_flow,aos_click," + "aos_itemid.number item_number", null);
        DynamicObjectCollection entityS = this.getModel().getEntryEntity("aos_entryentity");
        List<TreeNode> list = new ArrayList<>();
        List<String[]> total = new ArrayList<>();
        for (DynamicObject entity : entityS) {
            DynamicObject standard = entity.getDynamicObject("aos_standard");
            String cond = entity.getString("aos_cond");
            BigDecimal value = entity.getBigDecimal("aos_value");
            String stNamePos = standard.getString("name");
            String stNameNega = "!" + stNamePos;
            total.add(new String[] {stNamePos, stNameNega});
        }
        List<String[]> cartesianList = cartesianProduct(total);
        // 输出结果
        for (String[] arr : cartesianList) {
            System.out.println(Arrays.toString(arr));
            String key = String.join("&", arr);
            list.add(new TreeNode(null, key, key));
            for (DynamicObject tmp : tmpS) {
                list.add(new TreeNode(key, key + tmp.getString("item_number"), tmp.getString("item_number")));
            }
        }
        // 赋值数据
        treeView.setMulti(false);
        treeView.addNodes(list);
        treeView.setRootVisible(true);
        treeView.showNode("head");
    }
}
