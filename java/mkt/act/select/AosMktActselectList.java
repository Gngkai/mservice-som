package mkt.act.select;

import kd.bos.dataentity.utils.StringUtils;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.list.plugin.AbstractListPlugin;

/**
 * @author aosom
 * @version 活动选品表-列表插件
 */
public class AosMktActselectList extends AbstractListPlugin {
    /** 单据 */
    protected static final String AOS_INIT = "aos_init";

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        if (StringUtils.equals(AOS_INIT, evt.getItemKey())) {
            // 周期性活动初始化
            aosInit();
        }
    }

    private void aosInit() {
        AosMktActSelectTask.executerun();
        this.getView().invokeOperation("refresh");
        this.getView().showSuccessNotification("已手工提交周期性活动初始化,请等待,务重复提交!");
    }

}