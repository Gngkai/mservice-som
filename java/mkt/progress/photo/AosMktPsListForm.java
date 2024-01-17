package mkt.progress.photo;

import java.util.*;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndError;
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
import mkt.common.MKTCom;
import mkt.common.MKTS3PIC;
import mkt.progress.ProgressUtil;
import mkt.progress.design.aos_mkt_designreq_bill;
import mkt.progress.iface.iteminfo;

/**
 * @author aosom
 * @version 抠图任务清单-动态表单插件
 */
public class AosMktPsListForm extends AbstractBillPlugIn implements ItemClickListener {
    public final static String AOS_SUBMIT = "aos_submit";
    public final static String AOS_SAVE = "aos_save";
    public final static String AOS_QUERY = "aos_query";

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
                aos_submit();// 提交
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
     * @throws FndError
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
    private void aos_submit() throws FndError {
        // 数据层
        int ErrorCount = 0;
        String ErrorMessage = "";

        EntryGrid entryGrid = this.getControl("aos_entryentity");
        int[] SelectRow = entryGrid.getSelectRows();

        if (SelectRow.length == 0) {
            ErrorCount++;
            ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "未勾选行！");
        }
        if (ErrorCount > 0) {
            FndError fndMessage = new FndError(ErrorMessage);
            throw fndMessage;
        }

        for (int i = 0; i < SelectRow.length; i++) {
            String aos_status = this.getModel().getValue("aos_status", SelectRow[i]).toString();

            switch (aos_status) {
                case "新建":
                    this.getModel().setValue("aos_status", "抠图中", SelectRow[i]);
                    this.getModel().setValue("aos_startdate", new Date(), SelectRow[i]);
                    break;
                case "抠图中":
                    this.getModel().setValue("aos_status", "已完成", SelectRow[i]);
                    this.getModel().setValue("aos_enddate", new Date(), SelectRow[i]);
                    GenerateDesign(SelectRow[i]);
                    break;
                case "已完成":
                    continue;
            }
            Object aos_id = this.getModel().getValue("aos_id", SelectRow[i]);
            DynamicObject aos_mkt_pslist = BusinessDataServiceHelper.loadSingle(aos_id, "aos_mkt_pslist");
            aos_mkt_pslist.set("aos_status", this.getModel().getValue("aos_status", SelectRow[i]));
            aos_mkt_pslist.set("aos_epi", this.getModel().getValue("aos_epi", SelectRow[i]));
            aos_mkt_pslist.set("aos_startdate", this.getModel().getValue("aos_startdate", SelectRow[i]));
            aos_mkt_pslist.set("aos_enddate", this.getModel().getValue("aos_enddate", SelectRow[i]));
            OperationServiceHelper.executeOperate("save", "aos_mkt_pslist", new DynamicObject[] {aos_mkt_pslist},
                OperateOption.create());
            SyncPhotoReq(SelectRow[i]);// 同步拍照需求表
        }
        this.getView().showSuccessNotification("提交成功!");
    }

    private void GenerateDesign(int selectRow) {
        // 数据层
        Object ReqFId = this.getModel().getValue("aos_sourceid", selectRow);

        // 获取对应拍照需求表是否为已完成
        DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(ReqFId, "aos_mkt_photoreq");
        // 如果不为已完成 则不生成设计需求表 等待拍照需求表完成后再生成设计需求表
        if (aos_mkt_photoreq == null || !"已完成".equals(aos_mkt_photoreq.getString("aos_status")))
            return;

        Object AosShipDate = aos_mkt_photoreq.get("aos_shipdate");
        Object aos_billno = aos_mkt_photoreq.get("billno");
        Object aos_requireby = aos_mkt_photoreq.get("aos_requireby");
        Object AosDesigner = aos_mkt_photoreq.get("aos_designer");
        Object AosDeveloperId = aos_mkt_photoreq.getDynamicObject("aos_developer").getPkValue();
        Object ItemId = aos_mkt_photoreq.getDynamicObject("aos_itemid").getPkValue();
        Object aos_requirebyid = ((DynamicObject)aos_requireby).getPkValue();
        String MessageId = String.valueOf(AosDeveloperId);

        // 初始化
        DynamicObject aos_mkt_designreq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designreq");
        aos_mkt_designreq.set("aos_requiredate", new Date());
        aos_mkt_designreq.set("aos_type", "新品设计");
        aos_mkt_designreq.set("aos_shipdate", AosShipDate);
        aos_mkt_designreq.set("aos_orignbill", aos_billno);
        aos_mkt_designreq.set("aos_sourceid", ReqFId);
        aos_mkt_designreq.set("aos_status", "拍照功能图制作");
        aos_mkt_designreq.set("aos_user", AosDesigner);
        aos_mkt_designreq.set("aos_requireby", aos_requireby);
        aos_mkt_designreq.set("aos_sourcetype", "PHOTO");
        mkt.progress.design.aos_mkt_designreq_bill.setEntityValue(aos_mkt_designreq);
        try {
            List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(aos_requirebyid);
            if (MapList != null) {
                if (MapList.get(2) != null)
                    aos_mkt_designreq.set("aos_organization1", MapList.get(2).get("id"));
                if (MapList.get(3) != null)
                    aos_mkt_designreq.set("aos_organization2", MapList.get(3).get("id"));
            }
        } catch (Exception ex) {
        }

        // 产品类别
        String category = (String)SalUtil.getCategoryByItemId(String.valueOf(ItemId)).get("name");
        String[] category_group = category.split(",");
        String AosCategory1 = null;
        String AosCategory2 = null;
        int category_length = category_group.length;
        if (category_length > 0)
            AosCategory1 = category_group[0];
        if (category_length > 1)
            AosCategory2 = category_group[1];
        // 根据大类中类获取对应营销人员
        if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
            QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
            QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
            QFilter[] filters_category = new QFilter[] {filter_category1, filter_category2};
            String SelectStr = "aos_pser aos_designer,aos_designeror,aos_3d";
            DynamicObject aos_mkt_proguser =
                QueryServiceHelper.queryOne("aos_mkt_proguser", SelectStr, filters_category);
            if (aos_mkt_proguser != null) {
                aos_mkt_designreq.set("aos_designer", aos_mkt_proguser.get("aos_designer"));
                aos_mkt_designreq.set("aos_dm", aos_mkt_proguser.get("aos_designeror"));
                aos_mkt_designreq.set("aos_3der", aos_mkt_proguser.get("aos_3d"));
            }
        }

        DynamicObjectCollection aos_entryentityS = aos_mkt_designreq.getDynamicObjectCollection("aos_entryentity");
        String aos_contrybrandStr = "";
        String aos_orgtext = "";
        DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(ItemId, "bd_material");
        DynamicObjectCollection aos_contryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
        // 获取所有国家品牌 字符串拼接 终止
        Set<String> set_bra = new HashSet<>();
        for (DynamicObject aos_contryentry : aos_contryentryS) {
            DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
            String aos_nationalitynumber = aos_nationality.getString("number");
            if ("IE".equals(aos_nationalitynumber))
                continue;
            Object org_id = aos_nationality.get("id"); // ItemId
            int OsQty = iteminfo.GetItemOsQty(org_id, ItemId);
            int SafeQty = iteminfo.GetSafeQty(org_id);
            if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
                continue;
            aos_orgtext = aos_orgtext + aos_nationalitynumber + ";";

            Object obj = aos_contryentry.getDynamicObject("aos_contrybrand");
            if (obj == null)
                continue;
            String value = aos_contryentry.getDynamicObject("aos_contrybrand").getString("number");
            // if (!aos_contrybrandStr.contains(value))
            // aos_contrybrandStr = aos_contrybrandStr + value + ";";

            if (value != null)
                set_bra.add(value);
            if (set_bra.size() > 1) {
                if (!aos_contrybrandStr.contains(value))
                    aos_contrybrandStr = aos_contrybrandStr + value + ";";
            } else if (set_bra.size() == 1) {
                aos_contrybrandStr = value;
            }
        }

        String item_number = bd_material.getString("number");
        String url = "https://clss.s3.amazonaws.com/" + item_number + ".jpg";// 图片字段
        String aos_productno = bd_material.getString("aos_productno");
        String aos_itemname = bd_material.getString("name");
        // 获取同产品号物料
        QFilter filter_productno = new QFilter("aos_productno", QCP.equals, aos_productno);
        QFilter[] filters = new QFilter[] {filter_productno};
        String SelectColumn = "number,aos_type";
        String aos_broitem = "";
        DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", SelectColumn, filters);
        for (DynamicObject bd : bd_materialS) {
            if ("B".equals(bd.getString("aos_type")))
                continue; // 配件不获取
            String number = bd.getString("number");
            if (item_number.equals(number))
                continue;
            else
                aos_broitem = aos_broitem + number + ";";
        }

        DynamicObject aos_entryentity = aos_entryentityS.addNew();
        aos_entryentity.set("aos_itemid", ItemId);
        aos_entryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(ItemId));
        DynamicObjectCollection aos_subentryentityS = aos_entryentity.getDynamicObjectCollection("aos_subentryentity");

        DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
        aos_subentryentity.set("aos_sub_item", ItemId);
        aos_subentryentity.set("aos_segment3", aos_productno);
        aos_subentryentity.set("aos_itemname", aos_itemname);
        aos_subentryentity.set("aos_brand", aos_contrybrandStr);
        aos_subentryentity.set("aos_pic", url);
        aos_subentryentity.set("aos_developer", bd_material.get("aos_developer"));// 开发
        aos_subentryentity.set("aos_seting1", bd_material.get("aos_seting_cn"));
        aos_subentryentity.set("aos_seting2", bd_material.get("aos_seting_en"));
        aos_subentryentity.set("aos_spec", bd_material.get("aos_specification_cn"));
        aos_subentryentity.set("aos_url", MKTS3PIC.GetItemPicture(item_number));
        aos_subentryentity.set("aos_broitem", aos_broitem);
        aos_subentryentity.set("aos_orgtext", aos_orgtext);

        StringJoiner productStyle = new StringJoiner(";");
        DynamicObjectCollection item = bd_material.getDynamicObjectCollection("aos_productstyle_new");
        if (item.size() != 0) {
            List<Object> id =
                item.stream().map(e -> e.getDynamicObject("fbasedataid").getPkValue()).collect(Collectors.toList());
            for (Object a : id) {
                DynamicObject dysty = QueryServiceHelper.queryOne("aos_product_style", "id,name",
                    new QFilter("id", QCP.equals, a).toArray());
                String styname = dysty.getString("name");
                productStyle.add(styname);
            }
            aos_subentryentity.set("aos_productstyle_new", productStyle.toString());
        }
        aos_subentryentity.set("aos_shootscenes", bd_material.getString("aos_shootscenes"));

        aos_mkt_designreq_bill.createDesiginBeforeSave(aos_mkt_designreq);
        OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
            new DynamicObject[] {aos_mkt_designreq}, OperateOption.create());
        if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
            MKTCom.SendGlobalMessage(MessageId, "aos_mkt_photoreq",
                String.valueOf(operationrst.getSuccessPkIds().get(0)), aos_mkt_designreq.getString("billno"),
                "设计需求表-拍照新品自动创建");
            FndHistory.Create(aos_mkt_designreq, aos_mkt_designreq.getString("aos_status"), "设计需求表-拍照新品自动创建");
        }
    }

    private void SyncPhotoReq(int selectRow) {
        Object aos_sourceid = this.getModel().getValue("aos_sourceid", selectRow);
        String aos_status = this.getModel().getValue("aos_status", selectRow).toString();
        if (aos_sourceid == null)
            return;
        // 同步拍照需求表
        DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(aos_sourceid, "aos_mkt_photoreq");
        Object aos_sourceidlist = aos_mkt_photoreq.get("aos_sourceid");
        // 同步拍照任务清单
        if (QueryServiceHelper.exists("aos_mkt_photolist", aos_sourceidlist)) {
            DynamicObject aos_mkt_photolist =
                BusinessDataServiceHelper.loadSingle(aos_sourceidlist, "aos_mkt_photolist");
            if ("已完成".equals(aos_status)) {
                aos_mkt_photoreq.set("aos_picdate", new Date());
                aos_mkt_photolist.set("aos_picdate", new Date());
            }
            OperationServiceHelper.executeOperate("save", "aos_mkt_photolist", new DynamicObject[] {aos_mkt_photolist},
                OperateOption.create());
        }
        OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq", new DynamicObject[] {aos_mkt_photoreq},
            OperateOption.create());
    }

}