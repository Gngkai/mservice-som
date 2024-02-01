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

import java.awt.Color;
import java.awt.geom.Ellipse2D;
import org.jfree.chart.plot.PlotOrientation;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

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
                aosTest("Keyword Filter0", "Click0", "Expouse0", "aos_htmlap", 800, 600);
                aosTest("Keyword Filter1", "Click1", "Expouse1", "aos_htmlap1", 400, 350);
                aosTest("Keyword Filter2", "Click2", "Expouse2", "aos_htmlap2", 400, 350);
                aosTest("Keyword Filter3", "Click3", "Expouse3", "aos_htmlap3", 400, 350);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void aosTest2() throws IOException {
        // 创建样例数据
        double[][] datapoints = new double[2][4];
        datapoints[0][1] = -1;
        datapoints[1][1] = 1;
        datapoints[0][2] = 2;
        datapoints[1][2] = -2;
        datapoints[0][3] = -3;
        datapoints[1][3] = 3;
        datapoints[0][0] = 4;
        datapoints[1][0] = -4;
        // 创建数据集
        DefaultXYDataset dataset = new DefaultXYDataset();
        dataset.addSeries("Data", datapoints);
        // 创建 JFreeChart 实例
        JFreeChart scatterChart = ChartFactory.createScatterPlot("Scatter Plot Demo", "X", "Y", dataset,
            PlotOrientation.VERTICAL, true, true, false);
        // 设置散点样式
        scatterChart.getXYPlot().getRenderer().setSeriesShape(0, new Ellipse2D.Double(-3, -3, 6, 6));
        scatterChart.getXYPlot().getRenderer().setSeriesPaint(0, Color.RED);
        // 创建缓冲图像
        BufferedImage chartImage = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = chartImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setBackground(Color.WHITE);
        g2d.clearRect(0, 0, 800, 600);
        // 绘制图表
        scatterChart.draw(g2d, new java.awt.Rectangle(50, 50, 800, 600), null, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(chartImage, "png", baos);
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] imageBytes = baos.toByteArray();
        String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
        // 生成HTML代码
        String html = "<html><body><img src='data:image/png;base64," + base64Image + "'/></body></html>";
        System.out.println(html);
        Html htmlControl = this.getView().getControl("aos_htmlap");
        htmlControl.setConent(html);
    }

    private void aosTest(String title, String xName, String yName, String sign, int width, int height)
        throws IOException {
        DefaultXYDataset dataset = new DefaultXYDataset();
        // 生成100个随机数据点
        double[][] data = generateRandomData(100);
        dataset.addSeries("Sport", data);
        double[][] data2 = generateRandomData(100);
        dataset.addSeries("Home", data2);
        // 创建散点图
        JFreeChart scatterPlot = ChartFactory.createScatterPlot(
            // 标题
            title,
            // X轴标签
            xName,
            // Y轴标签
            yName,
            // 数据集
            dataset);
        // 将散点图生成为图片，并以Base64编码的方式嵌入到HTML代码中
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ChartUtilities.writeChartAsPNG(outputStream, scatterPlot, width, height);
        byte[] imageBytes = outputStream.toByteArray();
        String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
        // 生成HTML代码
        String html = "<html><body><img src='data:image/png;base64," + base64Image + "'/></body></html>";
        Html htmlControl = this.getView().getControl(sign);
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
