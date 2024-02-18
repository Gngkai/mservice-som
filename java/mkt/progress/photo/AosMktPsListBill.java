package mkt.progress.photo;

import java.util.*;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MktComUtil;
import mkt.common.MktS3Pic;
import mkt.progress.ProgressUtil;
import mkt.progress.design.AosMktDesignReqBill;
import mkt.progress.iface.ItemInfoUtil;

/**
 * @author aosom
 * @version 抠图任务清单-表单插件
 */
public class AosMktPsListBill extends AbstractBillPlugIn implements ItemClickListener {
    public final static String AOS_SUBMIT = "aos_submit";
    public final static String COMPETE = "已完成";
    public final static String AOS_MKT_PHOTOLIST = "aos_mkt_photolist";
    public final static int TWO = 2;
    public final static int THREE = 3;
    public final static String AOS_STATUS = "aos_status";

    @Override
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        statusControl();
    }

    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        statusControl();
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        try {
            if (AOS_SUBMIT.equals(control)) {
                // 提交
                aosSubmit();
            }
            statusControl();
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    /** 全局状态控制 **/
    private void statusControl() {
        // 数据层
        Object aosStatus = this.getModel().getValue("aos_status");
        DynamicObject aosUser = (DynamicObject)this.getModel().getValue("aos_designer");
        Object currentUserId = UserServiceHelper.getCurrentUserId();
        Object currentUserName = UserServiceHelper.getUserInfoByID((long)currentUserId).get("name");
        // 当前节点操作人不为当前用户 全锁
        if (!aosUser.getPkValue().toString().equals(currentUserId.toString())
            && !"刘中怀".equals(currentUserName.toString()) && !"程震杰".equals(currentUserName.toString())
            && !"陈聪".equals(currentUserName.toString())) {
            this.getView().setEnable(false, "titlepanel");
            this.getView().setEnable(false, "contentpanelflex");
            this.getView().setVisible(false, "bar_save");
        }
        if (COMPETE.equals(aosStatus)) {
            this.getView().setVisible(false, "aos_submit");
            this.getView().setVisible(false, "bar_save");
            this.getView().setEnable(false, "titlepanel");
            this.getView().setEnable(false, "contentpanelflex");
        }
    }

    private void aosSubmit() {
        String aosStatus = this.getModel().getValue("aos_status").toString();
        switch (aosStatus) {
            case "新建":
                this.getModel().setValue("aos_status", "抠图中");
                break;
            case "抠图中":
                this.getModel().setValue("aos_status", "已完成");
                generateDesign();
                break;
            default:
                break;
        }
        // 同步拍照需求表
        syncPhotoReq();
        this.getView().invokeOperation("save");
        this.getView().invokeOperation("refresh");
    }

    private void syncPhotoReq() {
        Object aosSourceid = this.getModel().getValue("aos_sourceid");
        String aosStatus = this.getModel().getValue("aos_status").toString();
        if (aosSourceid == null) {
            return;
        }
        // 同步拍照需求表
        DynamicObject aosMktPhotoreq = BusinessDataServiceHelper.loadSingle(aosSourceid, "aos_mkt_photoreq");
        Object aosSourceidlist = aosMktPhotoreq.get("aos_sourceid");
        // 同步拍照任务清单
        if (QueryServiceHelper.exists(AOS_MKT_PHOTOLIST, aosSourceidlist)) {
            DynamicObject aosMktPhotolist = BusinessDataServiceHelper.loadSingle(aosSourceidlist, "aos_mkt_photolist");
            if (COMPETE.equals(aosStatus)) {
                aosMktPhotoreq.set("aos_picdate", new Date());
                aosMktPhotolist.set("aos_picdate", new Date());
            }
            OperationServiceHelper.executeOperate("save", "aos_mkt_photolist", new DynamicObject[] {aosMktPhotolist},
                OperateOption.create());
        }
        OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq", new DynamicObject[] {aosMktPhotoreq},
            OperateOption.create());
    }

    private void generateDesign() {
        // 数据层
        Object reqFid = this.getModel().getValue("aos_sourceid");
        // 获取对应拍照需求表是否为已完成
        DynamicObject aosMktPhotoreq = BusinessDataServiceHelper.loadSingle(reqFid, "aos_mkt_photoreq");
        // 如果不为已完成 则不生成设计需求表 等待拍照需求表完成后再生成设计需求表
        if (aosMktPhotoreq == null || !COMPETE.equals(aosMktPhotoreq.getString(AOS_STATUS))) {
            return;
        }
        Object aosShipDate = aosMktPhotoreq.get("aos_shipdate");
        Object aosBillno = aosMktPhotoreq.get("billno");
        Object aosRequireby = aosMktPhotoreq.get("aos_requireby");
        Object aosDesigner = aosMktPhotoreq.get("aos_designer");
        Object aosDeveloperId = aosMktPhotoreq.getDynamicObject("aos_developer").getPkValue();
        boolean aosNewitem = aosMktPhotoreq.getBoolean("aos_newitem");
        Object itemId = aosMktPhotoreq.getDynamicObject("aos_itemid").getPkValue();
        Object aosRequirebyid = ((DynamicObject)aosRequireby).getPkValue();
        String messageId = String.valueOf(aosDeveloperId);
        // 初始化
        DynamicObject aosMktDesignreq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designreq");
        aosMktDesignreq.set("aos_requiredate", new Date());
        if (aosNewitem) {
            aosMktDesignreq.set("aos_type", "新品设计");
        } else {
            aosMktDesignreq.set("aos_type", "老品重拍");
        }
        aosMktDesignreq.set("aos_shipdate", aosShipDate);
        aosMktDesignreq.set("aos_orignbill", aosBillno);
        aosMktDesignreq.set("aos_sourceid", reqFid);
        aosMktDesignreq.set("aos_status", "拍照功能图制作");
        aosMktDesignreq.set("aos_user", aosDesigner);
        aosMktDesignreq.set("aos_requireby", aosRequireby);
        aosMktDesignreq.set("aos_sourcetype", "PHOTO");
        mkt.progress.design.AosMktDesignReqBill.setEntityValue(aosMktDesignreq);
        try {
            List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(aosRequirebyid);
            if (mapList != null) {
                if (mapList.get(TWO) != null) {
                    aosMktDesignreq.set("aos_organization1", mapList.get(2).get("id"));
                }
                if (mapList.get(THREE) != null) {
                    aosMktDesignreq.set("aos_organization2", mapList.get(3).get("id"));
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        // 产品类别
        String category = (String)SalUtil.getCategoryByItemId(String.valueOf(itemId)).get("name");
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
        // 根据大类中类获取对应营销人员
        if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
            QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
            QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
            QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2};
            String selectStr = "aos_pser aos_designer,aos_designeror,aos_3d";
            DynamicObject aosMktProguser = QueryServiceHelper.queryOne("aos_mkt_proguser", selectStr, filtersCategory);
            if (aosMktProguser != null) {
                aosMktDesignreq.set("aos_designer", aosMktProguser.get("aos_designer"));
                aosMktDesignreq.set("aos_dm", aosMktProguser.get("aos_designeror"));
                aosMktDesignreq.set("aos_3der", aosMktProguser.get("aos_3d"));
            }
        }
        DynamicObjectCollection aosEntryentityS = aosMktDesignreq.getDynamicObjectCollection("aos_entryentity");
        StringBuilder aosContrybrandStr = new StringBuilder();
        StringBuilder aosOrgtext = new StringBuilder();
        DynamicObject bdMaterial = BusinessDataServiceHelper.loadSingle(itemId, "bd_material");
        DynamicObjectCollection aosContryentryS = bdMaterial.getDynamicObjectCollection("aos_contryentry");
        // 获取所有国家品牌 字符串拼接 终止
        Set<String> setBra = new HashSet<>();
        for (DynamicObject aosContryentry : aosContryentryS) {
            DynamicObject aosNationality = aosContryentry.getDynamicObject("aos_nationality");
            String aosNationalitynumber = aosNationality.getString("number");
            if ("IE".equals(aosNationalitynumber)) {
                continue;
            }
            Object orgId = aosNationality.get("id");
            int osQty = ItemInfoUtil.getItemOsQty(orgId, itemId);
            int safeQty = ItemInfoUtil.getSafeQty(orgId);
            if ("C".equals(aosContryentry.getString("aos_contryentrystatus")) && osQty < safeQty) {
                continue;
            }
            aosOrgtext.append(aosNationalitynumber).append(";");

            Object obj = aosContryentry.getDynamicObject("aos_contrybrand");
            if (obj == null) {
                continue;
            }
            String value = aosContryentry.getDynamicObject("aos_contrybrand").getString("number");
            if (value != null) {
                setBra.add(value);
            }
            if (setBra.size() > 1) {
                if (value != null && !aosContrybrandStr.toString().contains(value)) {
                    aosContrybrandStr.append(value).append(";");
                }
            } else {
                if (value != null) {
                    aosContrybrandStr = new StringBuilder(value);
                }
            }
        }
        String itemNumber = bdMaterial.getString("number");
        // 图片字段
        String url = "https://clss.s3.amazonaws.com/" + itemNumber + ".jpg";
        String aosProductno = bdMaterial.getString("aos_productno");
        String aosItemname = bdMaterial.getString("name");
        // 获取同产品号物料
        QFilter filterProductno = new QFilter("aos_productno", QCP.equals, aosProductno);
        QFilter[] filters = new QFilter[] {filterProductno};
        String selectColumn = "number,aos_type";
        StringBuilder aosBroitem = new StringBuilder();
        DynamicObjectCollection bdMaterialS = QueryServiceHelper.query("bd_material", selectColumn, filters);
        for (DynamicObject bd : bdMaterialS) {
            if ("B".equals(bd.getString("aos_type"))) {
                continue; // 配件不获取
            }
            String number = bd.getString("number");
            if (!itemNumber.equals(number)) {
                aosBroitem.append(number).append(";");
            }
        }
        DynamicObject aosEntryentity = aosEntryentityS.addNew();
        aosEntryentity.set("aos_itemid", itemId);
        aosEntryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(itemId));
        DynamicObjectCollection aosSubentryentityS = aosEntryentity.getDynamicObjectCollection("aos_subentryentity");
        DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
        aosSubentryentity.set("aos_sub_item", itemId);
        aosSubentryentity.set("aos_segment3", aosProductno);
        aosSubentryentity.set("aos_itemname", aosItemname);
        aosSubentryentity.set("aos_brand", aosContrybrandStr);
        aosSubentryentity.set("aos_pic", url);
        aosSubentryentity.set("aos_developer", bdMaterial.get("aos_developer"));
        aosSubentryentity.set("aos_seting1", bdMaterial.get("aos_seting_cn"));
        aosSubentryentity.set("aos_seting2", bdMaterial.get("aos_seting_en"));
        aosSubentryentity.set("aos_spec", bdMaterial.get("aos_specification_cn"));
        aosSubentryentity.set("aos_url", MktS3Pic.getItemPicture(itemNumber));
        aosSubentryentity.set("aos_broitem", aosBroitem);
        aosSubentryentity.set("aos_orgtext", aosOrgtext);
        StringJoiner productStyle = new StringJoiner(";");
        DynamicObjectCollection item = bdMaterial.getDynamicObjectCollection("aos_productstyle_new");
        if (item.size() != 0) {
            List<Object> id =
                item.stream().map(e -> e.getDynamicObject("fbasedataid").getPkValue()).collect(Collectors.toList());
            for (Object a : id) {
                DynamicObject dysty = QueryServiceHelper.queryOne("aos_product_style", "id,name",
                    new QFilter("id", QCP.equals, a).toArray());
                String styname = dysty.getString("name");
                productStyle.add(styname);
            }
            aosSubentryentity.set("aos_productstyle_new", productStyle.toString());
        }
        aosSubentryentity.set("aos_shootscenes", bdMaterial.getString("aos_shootscenes"));
        AosMktDesignReqBill.createDesiginBeforeSave(aosMktDesignreq);
        OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
            new DynamicObject[] {aosMktDesignreq}, OperateOption.create());
        if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
            MktComUtil.sendGlobalMessage(messageId, "aos_mkt_photoreq",
                String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktDesignreq.getString("billno"),
                "设计需求表-拍照新品自动创建");
            FndHistory.Create(aosMktDesignreq, aosMktDesignreq.getString("aos_status"), "设计需求表-拍照新品自动创建");
        }
    }
}