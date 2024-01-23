package mkt.popularst.ppc;

import kd.bos.dataentity.utils.StringUtils;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.list.plugin.AbstractListPlugin;

/**
 * @author aosom
 * @version ST-列表插件
 */
public class AosMktPopPpcstList extends AbstractListPlugin {
	private static final String AOS_MANUAL = "aos_manual";
    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        if (StringUtils.equals(AOS_MANUAL, evt.getItemKey())) {
			// 手工初始化
			aosManual();
        }
    }

    private void aosManual() {
        this.getView().invokeOperation("refresh");
    }

}