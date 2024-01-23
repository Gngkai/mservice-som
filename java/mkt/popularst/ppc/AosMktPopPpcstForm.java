package mkt.popularst.ppc;

import java.util.EventObject;
import java.util.List;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.datamodel.IDataModel;
import kd.bos.entity.filter.FilterParameter;
import kd.bos.form.IFormView;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.list.BillList;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;

/**
 * @author aosom
 * @version ST-动态表单插件
 */
public class AosMktPopPpcstForm extends AbstractFormPlugin {
    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        // 查询父界面实体对象
        IFormView parentView = this.getView().getParentView();
        IDataModel parentModel = parentView.getModel();
        // 对象实体
        DynamicObject aosPrMain = parentModel.getDataEntity(true);
        // 给打开的列表设置过滤条件
        // 单据列表控件标识
        BillList aosBillLisTap = this.getControl("aos_billlistap");
        FilterParameter filterParameter = aosBillLisTap.getFilterParameter();
        List<QFilter> qFilters = filterParameter.getQFilters();
        qFilters.add(new QFilter("aos_sourceid", QCP.equals, aosPrMain.get("id")));
        aosBillLisTap.setQueryFilterParameter(filterParameter);
    }
}
