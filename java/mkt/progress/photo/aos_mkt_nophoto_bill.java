package mkt.progress.photo;

import common.fnd.FndError;
import common.sal.util.SalUtil;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;

import java.util.Date;

/**
 * @author create by gk
 * @date 2023/3/2 9:24
 * @action  不拍照任务清单界面
 */
public class aos_mkt_nophoto_bill extends AbstractFormPlugin {
    /** 拍照需求表生成不需拍任务清单 **/
    public static void create_noPhotoEntity(DynamicObject dy_mian){
        // 生成不拍照任务清单
        Object ReqFId = dy_mian.getPkValue(); // 当前界面主键
        Object AosBillno = dy_mian.get("billno");
        Object AosItemid = dy_mian.get("aos_itemid");
        Object AosItemName = dy_mian.get("aos_itemname");
        Object AosSameItemId = dy_mian.get("aos_sameitemid");
        Object AosReason = dy_mian.get("aos_reason");
        Object AosDeveloper = dy_mian.get("aos_developer");
        Object AosPoer = dy_mian.get("aos_poer");
        Object AosShipdate = dy_mian.get("aos_shipdate");
        Object AosSpecification = dy_mian.get("aos_specification");
        Object AosSeting1 = dy_mian.get("aos_seting1");

        DynamicObject aos_mkt_nophotolist = BusinessDataServiceHelper.newDynamicObject("aos_mkt_nophotolist");
        aos_mkt_nophotolist.set("billstatus", "A");
        aos_mkt_nophotolist.set("aos_status", "新建");
        aos_mkt_nophotolist.set("aos_initdate", new Date());
        aos_mkt_nophotolist.set("aos_sourceid", ReqFId);
        aos_mkt_nophotolist.set("aos_reqno", AosBillno);
        aos_mkt_nophotolist.set("aos_itemid", AosItemid);
        aos_mkt_nophotolist.set("aos_itemname", AosItemName);
        aos_mkt_nophotolist.set("aos_sameitemid", AosSameItemId);
        aos_mkt_nophotolist.set("aos_reason", AosReason);
        aos_mkt_nophotolist.set("aos_developer", AosDeveloper);
        aos_mkt_nophotolist.set("aos_poer", AosPoer);
        aos_mkt_nophotolist.set("aos_shipdate", AosShipdate);
        aos_mkt_nophotolist.set("aos_specification", AosSpecification);
        aos_mkt_nophotolist.set("aos_seting1", AosSeting1);

        // 获取不拍照设计师
        String itemid = ((DynamicObject) AosItemid).getString("id");
        String category = (String) SalUtil.getCategoryByItemId(itemid).get("name");
        String[] category_group = category.split(",");
        String AosCategory1 = null;
        String AosCategory2 = null;
        int category_length = category_group.length;
        if (category_length > 0)
            AosCategory1 = category_group[0];
        if (category_length > 1)
            AosCategory2 = category_group[1];
        long aos_diser = 0;
        if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
            QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
            QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
            QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2 };
            DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_nophdesign",
                    filters_category);
            if (aos_mkt_proguser != null) {
                aos_diser = aos_mkt_proguser.getLong("aos_nophdesign");
            }
        }
        aos_mkt_nophotolist.set("aos_diser", aos_diser);
        OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_nophotolist",
                new DynamicObject[] { aos_mkt_nophotolist }, OperateOption.create());
        String ErrorMessage = "";
        if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
            ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "不拍照任务清单保存失败!");
            FndError fndMessage = new FndError(ErrorMessage);
            throw fndMessage;
        }
    }
}
