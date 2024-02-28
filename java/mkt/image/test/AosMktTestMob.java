package mkt.image.test;

import common.fnd.FndMsg;
import kd.bos.bill.events.LocateEvent;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.field.PictureEdit;
import kd.bos.form.operate.FormOperate;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.form.plugin.IMobFormPlugin;
import kd.bos.url.UrlService;
import mkt.synciface.AosMktSyncErrorPicTask;

import java.net.URL;
import java.util.EventObject;

import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

/**
 * @author aosom
 * @since 2024/2/27 19:21
 */
public class AosMktTestMob extends AbstractFormPlugin implements IMobFormPlugin {

    @Override
    public void afterCreateNewData(EventObject e) {
        FndMsg.debug("======================afterCreateNewData======================");

    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给工具栏加监听事件

        this.addItemClickListeners("aos_mtoolbarap");

        // 提交
        this.addItemClickListeners("aos_button");
    }

    @Override
    public void beforeDoOperation(BeforeDoOperationEventArgs args) {
        FormOperate formOperate = (FormOperate)args.getSource();
        String operateKey = formOperate.getOperateKey();
        FndMsg.debug("======================beforeDoOperation======================");
        if ("aos_button".equals(operateKey)) {
            try {
                aosButton();
            } catch (IOException | NotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void aosButton() throws IOException, NotFoundException {
        FndMsg.debug("======================aosButton======================");
        Object aosPicture = this.getModel().getValue("aos_picturefield");
        String str = aosPicture.toString();
        // 文件服务器
        String property = System.getProperty("fileserver");
        BufferedImage image = ImageIO.read(new URL(property + str));
        FndMsg.debug("image:" + image);
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        MultiFormatReader reader = new MultiFormatReader();
        Result result = reader.decode(bitmap);
        this.getModel().setValue("aos_show", result.getText());
        System.out.println("Barcode content: " + result.getText());
    }

    @Override
    public void uploadFile(EventObject eventObject) {

    }

    @Override
    public void locate(LocateEvent locateEvent) {

    }

}
