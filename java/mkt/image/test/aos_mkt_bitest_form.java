package mkt.image.test;

import java.util.*;

import common.fnd.FndMsg;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.plugin.AbstractFormPlugin;

/**
 * @author aosom
 */
public class aos_mkt_bitest_form extends AbstractFormPlugin {

    private long n = 0;

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        this.addItemClickListeners("aos_test"); // 提交
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
        FndMsg.debug("printName:" + controlEnum.testControl.name);
    }

    public long add(long x) {
        n = n + x;
        return n;
    }

    public enum controlEnum {
        testControl("aos_test");

        private final String name;

        // 构造方法
        controlEnum(String name) {
            this.name = name;
        }
    }

}
