package mkt.progress.photo;

import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;

import java.util.Date;

/**
 * @author aosom
 * @version 不拍照任务清单-工具类
 */
public class AosMktNoPhotoUtil {
    /** 拍照需求表生成不需拍任务清单 **/
    public static void createNoPhotoEntity(DynamicObject dyMian) {
        // 生成不拍照任务清单
        Object reqFid = dyMian.getPkValue();
        Object aosBillno = dyMian.get("billno");
        Object aosItemid = dyMian.get("aos_itemid");
        Object aosItemName = dyMian.get("aos_itemname");
        Object aosSameItemId = dyMian.get("aos_sameitemid");
        Object aosReason = dyMian.get("aos_reason");
        Object aosDeveloper = dyMian.get("aos_developer");
        Object aosPoer = dyMian.get("aos_poer");
        Object aosShipdate = dyMian.get("aos_shipdate");
        Object aosSpecification = dyMian.get("aos_specification");
        Object aosSeting1 = dyMian.get("aos_seting1");
        DynamicObject aosMktNophotolist = BusinessDataServiceHelper.newDynamicObject("aos_mkt_nophotolist");
        aosMktNophotolist.set("billstatus", "A");
        aosMktNophotolist.set("aos_status", "新建");
        aosMktNophotolist.set("aos_initdate", new Date());
        aosMktNophotolist.set("aos_sourceid", reqFid);
        aosMktNophotolist.set("aos_reqno", aosBillno);
        aosMktNophotolist.set("aos_itemid", aosItemid);
        aosMktNophotolist.set("aos_itemname", aosItemName);
        aosMktNophotolist.set("aos_sameitemid", aosSameItemId);
        aosMktNophotolist.set("aos_reason", aosReason);
        aosMktNophotolist.set("aos_developer", aosDeveloper);
        aosMktNophotolist.set("aos_poer", aosPoer);
        aosMktNophotolist.set("aos_shipdate", aosShipdate);
        aosMktNophotolist.set("aos_specification", aosSpecification);
        aosMktNophotolist.set("aos_seting1", aosSeting1);
        // 获取不拍照设计师
        String itemid = ((DynamicObject)aosItemid).getString("id");
        String category = (String)SalUtil.getCategoryByItemId(itemid).get("name");
        String[] categoryGroup = category.split(",");
        String aosCategory1 = null;
        String aosCategory2 = null;
        int categoryLength = categoryGroup.length;
        if (categoryLength > 0) {
            aosCategory1 = categoryGroup[0];
        }
        if (categoryLength > 1) {
            aosCategory2 = categoryGroup[1];
        }
        long aosDiser = 0;
        if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
            QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
            QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
            QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2};
            DynamicObject aosMktProguser =
                QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_nophdesign", filtersCategory);
            if (aosMktProguser != null) {
                aosDiser = aosMktProguser.getLong("aos_nophdesign");
            }
        }
        aosMktNophotolist.set("aos_diser", aosDiser);
        OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_nophotolist",
            new DynamicObject[] {aosMktNophotolist}, OperateOption.create());
        if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
            throw new FndError("不拍照任务清单保存失败!");
        }
    }
}
