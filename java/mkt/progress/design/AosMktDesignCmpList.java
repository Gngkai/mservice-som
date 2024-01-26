package mkt.progress.design;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import common.fnd.FndError;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import mkt.progress.iface.ParaInfoUtil;

/**
 * @author aosom
 * @version 设计完成表-列表插件
 */
public class AosMktDesignCmpList extends AbstractListPlugin {
    public final static String AOS_SHOWCLOSE = "aos_showclose";
    @Override
    public void setFilter(SetFilterEvent e) {
        List<QFilter> qFilters = e.getQFilters();
        ParaInfoUtil.setRightsForDesign(qFilters, this.getPageCache());
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String itemKey = evt.getItemKey();
        try {
            if (AOS_SHOWCLOSE.equals(itemKey)) {
                // 查询关闭流程
                ParaInfoUtil.showClose(this.getView());
            }
        } catch (FndError error) {
            this.getView().showMessage(error.getErrorMessage());
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            this.getView().showMessage(writer.toString());
            e.printStackTrace();
        }
    }
}
