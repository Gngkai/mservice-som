package mkt.data.point;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

public class AosMktPointBill extends AbstractBillPlugIn {
    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if ("aos_category2".equals(name) || "aos_category1".equals(name)) {
            autoSetCategoryInfo();
        }
    }

    private void autoSetCategoryInfo() {
        String aos_category1 = obj2String(this.getModel().getValue("aos_category1"));
        String aos_category2 = obj2String(this.getModel().getValue("aos_category2"));
        // 产品中类改变自动带出编辑
        DynamicObject editor = getEditorInfo2(aos_category1,aos_category2);

        if (editor != null) {
            this.getModel().setValue("aos_user", editor.get("aos_eng"));
        } else {
            this.getModel().setValue("aos_user", null);
        }
    }

    /**
     * 根据大类中类获取编辑助理信息
     * @param aos_category1
     * @param aos_category2
     * @return
     */
    private DynamicObject getEditorInfo2(String aos_category1, String aos_category2) {
        return QueryServiceHelper.queryOne("aos_mkt_proguser",
                "aos_eng", new QFilter[]{
                        new QFilter("aos_category1", QCP.equals, aos_category1)
                                .and(new QFilter("aos_category2", QCP.equals, aos_category2))
                });
    }

    public static String obj2String(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}
