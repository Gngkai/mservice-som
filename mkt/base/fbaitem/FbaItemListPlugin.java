package mkt.base.fbaitem;

import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.threads.ThreadPools;


/**
 * @author create by ?
 * @date 2022/9/19 18:27
 * @action
 */
public class FbaItemListPlugin extends AbstractListPlugin {
    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String itemKey = evt.getItemKey();
        if (itemKey.equals("aos_sync")){
            ThreadPools.executeOnce("FBA每日物料同步",new SyncFbaItemTask.SyncRunnable());
        }
    }
}
