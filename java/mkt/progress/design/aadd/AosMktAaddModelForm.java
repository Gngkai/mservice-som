package mkt.progress.design.aadd;

import java.util.EventObject;
import java.util.List;

import common.fnd.FndGlobal;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.serialization.SerializationUtils;
import kd.bos.form.IFormView;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

/**
 * @author aosom
 * @version 高级A+模板弹框-动态表单插件
 */
public class AosMktAaddModelForm extends AbstractFormPlugin {
    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        initData();
    }

    private void initData() {
        IFormView parentView = this.getView().getParentView();
        Object fid = parentView.getModel().getDataEntity().getPkValue();
        String button = parentView.getPageCache().get("button");
        DynamicObjectCollection aosAaddModelDetailS = QueryServiceHelper.query("aos_aadd_model_detail",
            "aos_cate1,aos_cate2," + "aos_cn,aos_usca,aos_uk,aos_de,aos_fr,aos_it,aos_es,aos_seq",
            new QFilter("aos_sourceid", QCP.equals, fid.toString())
                .and("aos_button", QCP.equals, Integer.parseInt(button)).toArray(),
            "aos_seq asc");
        this.getModel().deleteEntryData("aos_entryentity");
        int i = 0;
        for (DynamicObject aosAaddModelDetail : aosAaddModelDetailS) {
            this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
            this.getModel().setValue("aos_cate1", aosAaddModelDetail.get("aos_cate1"), i);
            this.getModel().setValue("aos_cate2", aosAaddModelDetail.get("aos_cate2"), i);
            this.getModel().setValue("aos_cn", aosAaddModelDetail.get("aos_cn"), i);
            this.getModel().setValue("aos_usca", aosAaddModelDetail.get("aos_usca"), i);
            this.getModel().setValue("aos_uk", aosAaddModelDetail.get("aos_uk"), i);
            this.getModel().setValue("aos_de", aosAaddModelDetail.get("aos_de"), i);
            this.getModel().setValue("aos_fr", aosAaddModelDetail.get("aos_fr"), i);
            this.getModel().setValue("aos_it", aosAaddModelDetail.get("aos_it"), i);
            this.getModel().setValue("aos_es", aosAaddModelDetail.get("aos_es"), i);
            this.getModel().setValue("aos_seq", aosAaddModelDetail.get("aos_seq"), i);
            i++;
        }
        // 控制语言
        String lan = parentView.getPageCache().get(AosMktAaddModelBill.KEY_USER);
        List<String> users = (List<String>)SerializationUtils.fromJsonStringToList(lan, String.class);
        for (String userLan : users) {
            String field = AosMktAaddModelBill.judgeLan(userLan);
            if (FndGlobal.IsNotNull(field)) {
                this.getModel().setValue(field + "_h", true);
            }
        }
    }
}
