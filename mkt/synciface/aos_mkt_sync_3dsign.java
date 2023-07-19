package mkt.synciface;

import common.fnd.FndGlobal;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.exception.KDException;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.fi.bd.util.QFBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * @author create by gk
 * @date 2023/7/18 17:20
 * @action  3d产品设计单同步数据（出运日期，质检完成日期，大货样封样状态）
 */
public class aos_mkt_sync_3dsign extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        //查找大货封样的3d产品设计单
        QFBuilder builder = new QFBuilder();
        builder.add("aos_status","=","大货样封样");
        StringJoiner select = new StringJoiner(","),photoSelect = new StringJoiner(",");
        select.add("id");
        select.add("aos_status");
        select.add("aos_orignbill");
        select.add("aos_quainscomdate");
        select.add("aos_shipdate");

        photoSelect.add("aos_shipdate");
        photoSelect.add("aos_quainscomdate");
        photoSelect.add("aos_itemid");
        photoSelect.add("aos_ponumber");

        DynamicObject[] designs = BusinessDataServiceHelper.load("aos_mkt_3design", select.toString(), builder.toArray());
        //记录修改的单据
        List<DynamicObject> updateEntity = new ArrayList<>(designs.length);
        for (DynamicObject design : designs) {
            String photoBill = design.getString("aos_orignbill");
            //拍照需求表相关的信息
            builder.clear();
            builder.add("billno","=",photoBill);
            DynamicObject dy_photo = QueryServiceHelper.queryOne("aos_mkt_photoreq", photoSelect.toString(), builder.toArray());
            if (dy_photo==null) {
                continue;
            }
            boolean invalidDate = FndGlobal.IsNull(dy_photo.get("aos_shipdate")) && FndGlobal.IsNull(dy_photo.get("aos_quainscomdate"));
            //出运日期和质检日期都无效
            if (invalidDate){
                //查找封样单信息
                builder.clear();
                builder.add("aos_item","=",dy_photo.get("aos_itemid"));
                builder.add("aos_contractnowb","=",dy_photo.get("aos_ponumber"));
                builder.add("aos_largegood","!=","");
                boolean sealsample = QueryServiceHelper.exists("aos_sealsample", builder.toArray());
                if (!sealsample){
                    continue;
                }
            }
            else {
                design.set("aos_shipdate",dy_photo.get("aos_shipdate"));
                design.set("aos_quainscomdate",dy_photo.get("aos_quainscomdate"));
            }
            design.set("aos_status","大货样封样");
            updateEntity.add(design);
        }
        DynamicObject[] array = updateEntity.toArray(new DynamicObject[0]);
        SaveServiceHelper.update(array);
    }



}
