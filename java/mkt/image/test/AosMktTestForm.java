package mkt.image.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;

import com.google.gson.*;
import common.fnd.FndMsg;
import freemarker.cache.FileTemplateLoader;
import freemarker.template.TemplateException;
import kd.bos.form.control.Control;
import kd.bos.form.control.Html;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.plugin.AbstractFormPlugin;

import java.io.IOException;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.DefaultXYDataset;

import java.io.StringWriter;

/**
 * @author aosom
 * @version 测试表单插件
 */
public class AosMktTestForm extends AbstractFormPlugin {
    private final long n = 0;

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
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        // 提交
        this.addItemClickListeners("aos_test");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        if ("aos_cal".equals(control)) {
            try {
                aosTest4();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void afterCreateNewData(EventObject e) {}

    private void aosTest4() throws IOException {
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

    private void aosTest3() throws IOException, TemplateException {
        // 创建数据点
        List<DataPoint> data = new ArrayList<>();
        data.add(new DataPoint(1, 10));
        data.add(new DataPoint(2, 20));
        data.add(new DataPoint(3, 15));
        data.add(new DataPoint(4, 25));
        data.add(new DataPoint(5, 18));
        // 将数据转换为JSON数组
        Gson gson = new Gson();
        String json = gson.toJson(data);
        FndMsg.debug(json);
        Configuration configuration = new Configuration();
        FileTemplateLoader templateLoader =
            new FileTemplateLoader(new File(System.getProperty("user.dir") + "/mms/java/templates"));
        configuration.setTemplateLoader(templateLoader);
        freemarker.template.Template template = configuration.getTemplate("chart_template.html");
        StringWriter writer = new StringWriter();
        Map<String, Object> dataModel = new HashMap<>(16);
        dataModel.put("jsonString", json);
        template.process(dataModel, writer);
        String html = writer.toString();
        FndMsg.debug("html:" + html);
        Html htmlControl = this.getView().getControl("aos_htmlap");
        htmlControl.setConent(html);
    }

    public static class DataPoint {
        private int x;
        private int y;

        public DataPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        // Getter和Setter方法
        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }
        // 省略构造函数和getter/setter方法

        // 将数据转换为JSON格式
        public String toJSON() {
            Gson gson = new Gson();
            return gson.toJson(this);
        }
    }

}
