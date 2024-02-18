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
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import mkt.common.MktComUtil;
import mkt.common.MKTS3PIC;
import mkt.progress.ProgressUtil;
import mkt.progress.design.AosMktDesignReqBill;
import mkt.progress.iface.ItemInfoUtil;

/**
 * @author aosom
 * @version 抠图任务清单-动态表单插件
 */
public class AosMktPsListForm extends AbstractBillPlugIn implements ItemClickListener {
    public final static String AOS_SUBMIT = "aos_submit";
    public final static String AOS_SAVE = "aos_save";
    public final static String AOS_QUERY = "aos_query";
    public final static String COMPETE = "已完成";
    public final static String AOS_STATUS = "aos_status";
    public final static int TWO = 2;
    public final static int THREE = 3;
    public final static String AOS_MKT_PHOTOLIST = "aos_mkt_photolist";

    @Override
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
    }

    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        initData(null);
    }

    /** 初始化数据 **/
    private void initData(QFilter qf) {
        String selectColumn = "aos_itemid,aos_itemname,aos_designer,aos_shipdate,"
            + "aos_arrivaldate,aos_reqno,aos_emstatus,aos_status,aos_epi,aos_initdate,aos_startdate,"
            + "aos_enddate,aos_sourceid,id,aos_rcvdate";
        DynamicObjectCollection aosMktPslistS =
            QueryServiceHelper.query("aos_mkt_pslist", selectColumn, new QFilter[] {qf});
        this.getModel().deleteEntryData("aos_entryentity");
        int i = 0;
        for (DynamicObject aosMktPslist : aosMktPslistS) {
            this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
            this.getModel().setValue("aos_itemid", aosMktPslist.get("aos_itemid"), i);
            this.getModel().setValue("aos_itemname", aosMktPslist.get("aos_itemname"), i);
            this.getModel().setValue("aos_designer", aosMktPslist.get("aos_designer"), i);
            this.getModel().setValue("aos_shipdate", aosMktPslist.get("aos_shipdate"), i);
            this.getModel().setValue("aos_arrivaldate", aosMktPslist.get("aos_arrivaldate"), i);
            this.getModel().setValue("aos_reqno", aosMktPslist.get("aos_reqno"), i);
            this.getModel().setValue("aos_emstatus", aosMktPslist.get("aos_emstatus"), i);
            this.getModel().setValue("aos_status", aosMktPslist.get("aos_status"), i);
            this.getModel().setValue("aos_epi", aosMktPslist.get("aos_epi"), i);
            this.getModel().setValue("aos_initdate", aosMktPslist.get("aos_initdate"), i);
            this.getModel().setValue("aos_startdate", aosMktPslist.get("aos_startdate"), i);
            this.getModel().setValue("aos_enddate", aosMktPslist.get("aos_enddate"), i);
            this.getModel().setValue("aos_sourceid", aosMktPslist.get("aos_sourceid"), i);
            this.getModel().setValue("aos_id", aosMktPslist.get("id"), i);
            // 入库日期
            this.getModel().setValue("aos_rcvdate", aosMktPslist.get("aos_rcvdate"), i);
            i++;
        }
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        try {
            if (AOS_SUBMIT.equals(control)) {
                aosSubmit();// 提交
            } else if (AOS_SAVE.equals(control)) {
                aosSave();// 保存
            } else if (AOS_QUERY.equals(control)) {
                aosQuery();
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    @Override
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        e.setCheckDataChange(false);
    }

    private void aosQuery() {
        QFilter filterItem = null;
        Object aosItemidMul = this.getModel().getValue("aos_itemid_mul");
        if (!Cux_Common_Utl.IsNull(aosItemidMul)) {
            DynamicObjectCollection aosItemidS = (DynamicObjectCollection)aosItemidMul;
            Set<String> itemSet = new HashSet<>();
            if (aosItemidS.size() > 0) {
                for (DynamicObject aosItemid : aosItemidS) {
                    itemSet.add(String.valueOf(aosItemid.getDynamicObject("fbasedataid").getPkValue()));
                }
                filterItem = new QFilter("aos_itemid.id", QCP.in, itemSet);
            }
        }
        initData(filterItem);
    }

    /**
     * 批量保存
     *
     */
    private void aosSave() throws FndError {
        // 数据层
        FndError fndError = new FndError();
        EntryGrid entryGrid = this.getControl("aos_entryentity");
        int[] selectRow = entryGrid.getSelectRows();
        if (selectRow.length == 0) {
            fndError.add("未勾选行!");
        }
        if (fndError.getCount() > 0) {
            throw fndError;
        }
        for (int row : selectRow) {
            Object aosId = this.getModel().getValue("aos_id", row);
            DynamicObject aosMktPslist = BusinessDataServiceHelper.loadSingle(aosId, "aos_mkt_pslist");
            Object aosStartdate = this.getModel().getValue("aos_startdate", row);
            Object aosEnddate = this.getModel().getValue("aos_enddate", row);
            aosMktPslist.set("aos_epi", this.getModel().getValue("aos_epi", row));
            aosMktPslist.set("aos_startdate", aosStartdate);
            aosMktPslist.set("aos_enddate", aosEnddate);
            OperationServiceHelper.executeOperate("save", "aos_mkt_pslist", new DynamicObject[] {aosMktPslist},
                OperateOption.create());
        }
        this.getView().showSuccessNotification("保存成功!");
    }

    /** 批量提交 **/
    private void aosSubmit() throws FndError {
        FndError fndError = new FndError();
        // 数据层
        EntryGrid entryGrid = this.getControl("aos_entryentity");
        int[] selectRow = entryGrid.getSelectRows();
        if (selectRow.length == 0) {
            fndError.add("未勾选行!");
        }
        if (fndError.getCount() > 0) {
            throw fndError;
        }
        for (int row : selectRow) {
            String aosStatus = this.getModel().getValue("aos_status", row).toString();
            switch (aosStatus) {
                case "新建":
                    this.getModel().setValue("aos_status", "抠图中", row);
                    this.getModel().setValue("aos_startdate", new Date(), row);
                    break;
                case "抠图中":
                    this.getModel().setValue("aos_status", "已完成", row);
                    this.getModel().setValue("aos_enddate", new Date(), row);
                    generateDesign(row);
                    break;
                case "已完成":
                    continue;
                default:
                    break;
            }
            Object aosId = this.getModel().getValue("aos_id", row);
            DynamicObject aosMktPslist = BusinessDataServiceHelper.loadSingle(aosId, "aos_mkt_pslist");
            aosMktPslist.set("aos_status", this.getModel().getValue("aos_status", row));
            aosMktPslist.set("aos_epi", this.getModel().getValue("aos_epi", row));
            aosMktPslist.set("aos_startdate", this.getModel().getValue("aos_startdate", row));
            aosMktPslist.set("aos_enddate", this.getModel().getValue("aos_enddate", row));
            OperationServiceHelper.executeOperate("save", "aos_mkt_pslist", new DynamicObject[] {aosMktPslist},
                OperateOption.create());
            // 同步拍照需求表
            syncPhotoReq(row);
        }
        this.getView().showSuccessNotification("提交成功!");
    }

    private void generateDesign(int selectRow) {
        // 数据层
        Object reqFid = this.getModel().getValue("aos_sourceid", selectRow);
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
        Object itemId = aosMktPhotoreq.getDynamicObject("aos_itemid").getPkValue();
        Object aosRequirebyid = ((DynamicObject)aosRequireby).getPkValue();
        String messageId = String.valueOf(aosRequirebyid);
        // 初始化
        DynamicObject aosMktDesignreq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designreq");
        aosMktDesignreq.set("aos_requiredate", new Date());
        aosMktDesignreq.set("aos_type", "新品设计");
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
            } else if (setBra.size() == 1) {
                if (value != null) {
                    aosContrybrandStr = new StringBuilder(value);
                }
            }
        }
        String itemNumber = bdMaterial.getString("number");
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
        aosSubentryentity.set("aos_brand", aosContrybrandStr.toString());
        aosSubentryentity.set("aos_pic", url);
        aosSubentryentity.set("aos_developer", bdMaterial.get("aos_developer"));
        aosSubentryentity.set("aos_seting1", bdMaterial.get("aos_seting_cn"));
        aosSubentryentity.set("aos_seting2", bdMaterial.get("aos_seting_en"));
        aosSubentryentity.set("aos_spec", bdMaterial.get("aos_specification_cn"));
        aosSubentryentity.set("aos_url", MKTS3PIC.GetItemPicture(itemNumber));
        aosSubentryentity.set("aos_broitem", aosBroitem.toString());
        aosSubentryentity.set("aos_orgtext", aosOrgtext.toString());
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

    private void syncPhotoReq(int selectRow) {
        Object aosSourceid = this.getModel().getValue("aos_sourceid", selectRow);
        String aosStatus = this.getModel().getValue("aos_status", selectRow).toString();
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
}