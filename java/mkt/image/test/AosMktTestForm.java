package mkt.image.test;

import java.util.*;

import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.plugin.AbstractFormPlugin;
import sal.synciface.imp.AosSalImportPriceTask;

/**
 * @author aosom
 * @version 测试表单插件
 */
public class AosMktTestForm extends AbstractFormPlugin {
    private long n = 0;

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
        if (controlEnum.testControl.name.equals(control)) {
            aosTest();
        }
    }

    private void aosTest() {
        AosSalImportPriceTask.doOperate();
    }

    public long add(long x) {
        n = n + x;
        return n;
    }

    /**
     * 按钮枚举类
     */
    public enum controlEnum {
        /**
         * 测试按钮
         */
        testControl("aos_test");

        /**
         * 按钮名称
         */
        private final String name;

        /**
         * 构造方法
         * 
         * @param name 名称
         */
        controlEnum(String name) {
            this.name = name;
        }
    }
}