package mkt.progress.design3d;

import java.util.Date;
import java.util.EventObject;
import java.util.List;

import com.grapecity.documents.excel.T;
import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.entity.EntityMetadataCache;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.IFormView;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import common.sal.util.QFBuilder;
import mkt.common.MktComUtil;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.ItemInfoUtil;
import mkt.progress.photo.AosMktProgPhReqBill;
import mkt.progress.photo.AosMktRcvBill;

/**
 * @author aosom
 * @version 3D产品设计单-表单插件
 */
public class AosMkt3DesignBill extends AbstractBillPlugIn implements ItemClickListener {

    /** 系统管理员 **/
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    public final static String DESIGN = "设计需求表";
    public final static String PHOTO = "拍照需求表";
    public final static String VED = "视频";
    public final static String PH = "拍照";
    public final static String FACEZ = "工厂简拍";
    public final static String SAMPLE = "来样拍照";
    public final static String AOS_MKT_PHOTOLIST = "aos_mkt_photolist";
    public final static String AOS_SOURCEID = "aos_sourceid";

    public final static String NEWDES = "新品设计";
    public final static String OLDPRO = "老品优化";
    public final static int TWO = 2;
    public final static int THREE = 3;

    public static void openSample(IFormView iFormView, Object id) {
        QFBuilder builder = new QFBuilder();
        builder.add("id", "=", id);
        builder.add("aos_source", "=", "拍照需求表");
        DynamicObject dy = QueryServiceHelper.queryOne("aos_mkt_3design", "aos_orignbill", builder.toArray());
        if (dy == null) {
            throw new FndError("拍照需求表不存在");
        }
        builder.clear();
        builder.add("billno", "=", dy.get("aos_orignbill"));
        dy = QueryServiceHelper.queryOne("aos_mkt_photoreq", "aos_itemid,aos_ponumber", builder.toArray());
        if (dy == null) {
            throw new FndError("拍照需求表不存在");
        }
        String aosItemid = dy.getString("aos_itemid");
        String ponumber = dy.getString("aos_ponumber");
        AosMktRcvBill.openSample(iFormView, aosItemid, ponumber);
    }

    /** 新建状态下提交 **/
    private static void submitForNew(DynamicObject dyMain) {
        FndError fndError = new FndError();
        // 数据层
        Object pkValue = dyMain.getPkValue();
        Object aosDesigner = dyMain.get("aos_designer");
        Object aosOrignbill = dyMain.get("aos_orignbill");
        Object aosSourceid = dyMain.get("aos_sourceid");
        Object aosSource = dyMain.get("aos_source");
        String messageId;
        if (aosDesigner == null) {
            fndError.add("设计为空,流程无法流转!");
        }
        if (fndError.getCount() > 0) {
            throw fndError;
        }
        // 执行保存操作
        dyMain.set("aos_status", "已完成");
        dyMain.set("aos_user", SYSTEM);
        SaveServiceHelper.save(new DynamicObject[] {dyMain});
        // 如果是设计需求表来的
        if (DESIGN.equals(aosSource)) {
            // 判断本单下3D产品设计单是否全为已完成
            QFilter filterBill = new QFilter("aos_orignbill", "=", aosOrignbill);
            QFilter filterStatus = new QFilter("aos_status", "!=", "已完成");
            QFilter filterId = new QFilter("id", "!=", pkValue);
            QFilter[] filters = new QFilter[] {filterBill, filterStatus, filterId};
            DynamicObject aosMkt3design = QueryServiceHelper.queryOne("aos_mkt_3design", "count(0)", filters);
            int count = aosMkt3design.getInt(0);
            if (count == 0) {
                String designer = null;
                if ((aosDesigner instanceof String) || (aosDesigner instanceof Long)) {
                    designer = String.valueOf(aosDesigner);
                } else {
                    if (aosDesigner != null) {
                        designer = ((DynamicObject)aosDesigner).getPkValue().toString();
                    }
                }
                messageId = designer;
                DynamicObject aosMktDesignreq = BusinessDataServiceHelper.loadSingle(aosSourceid, "aos_mkt_designreq");
                String aosStatus = aosMktDesignreq.getString("aos_status");
                aosMktDesignreq.set("aos_user", messageId);
                aosMktDesignreq.set("aos_status", "设计确认3D");
                mkt.progress.design.AosMktDesignReqBill.setEntityValue(aosMktDesignreq);
                FndHistory.Create(aosMktDesignreq, "提交", aosStatus);
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
                    new DynamicObject[] {aosMktDesignreq}, OperateOption.create());
                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    MktComUtil.sendGlobalMessage(messageId, "aos_mkt_designreq", String.valueOf(aosSourceid),
                        String.valueOf(aosOrignbill), "设计确认3D");
                }
            }
        } else if (PHOTO.equals(aosSource)) {
            DynamicObject aosMktPhotoreq = BusinessDataServiceHelper.loadSingle(aosSourceid, "aos_mkt_photoreq");
            String aosStatus = aosMktPhotoreq.getString("aos_status");
            String aosPhstate = aosMktPhotoreq.getString("aos_phstate");
            // 24-1-3 如果拍照需求表 需求类型是 视频，则到视频更新节点（实际是到 开发采购确认视频，再触发提交）
            String aosType = aosMktPhotoreq.getString("aos_type");
            if (PH.equals(aosType)) {
                // 判断是否有 视频
                DynamicObject ved =
                    BusinessDataServiceHelper.loadSingle("aos_mkt_photoreq", new QFilter("aos_type", QCP.equals, VED)
                        .and("billno", QCP.equals, aosOrignbill).and("aos_status", QCP.equals, "视频剪辑").toArray());
                if (FndGlobal.IsNotNull(ved)) {
                    ved.set("aos_status", "开发确认:视频");
                    FndHistory.Create(ved, "提交(3d回写),开发确认:视频", "开发确认:视频");
                    new AosMktProgPhReqBill().aosSubmit(ved, "B");
                }
            }
            if (VED.equals(aosType)) {
                aosMktPhotoreq.set("aos_status", "开发确认:视频");
                FndHistory.Create(aosMktPhotoreq, "提交(3d回写),开发确认:视频", aosStatus);
                new AosMktProgPhReqBill().aosSubmit(aosMktPhotoreq, "B");
            }
            // 24-1-3 来样拍照，直接结束
            else if (FACEZ.equals(aosPhstate) || SAMPLE.equals(aosPhstate)) {
                AosMktProgPhReqBill.generateDesign(aosMktPhotoreq);
                aosMktPhotoreq.set("aos_status", "已完成");
                aosMktPhotoreq.set("aos_user", SYSTEM);
                // 回写拍照任务清单
                FndHistory.Create(aosMktPhotoreq, "提交(3d回写),工厂简拍结束", aosStatus);
                OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq", new DynamicObject[] {aosMktPhotoreq},
                    OperateOption.create());
                if (QueryServiceHelper.exists(AOS_MKT_PHOTOLIST, aosMktPhotoreq.get(AOS_SOURCEID))) {
                    DynamicObject aosMktPhotolist =
                        BusinessDataServiceHelper.loadSingle(aosMktPhotoreq.get("aos_sourceid"), "aos_mkt_photolist");
                    aosMktPhotolist.set("aos_vedstatus", "已完成");
                    OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                        new DynamicObject[] {aosMktPhotolist}, OperateOption.create());
                }
            } else {
                aosMktPhotoreq.set("aos_status", "开发/采购确认图片");
                boolean aosNewitem = aosMktPhotoreq.getBoolean("aos_newitem");
                Object aosDeveloper = aosMktPhotoreq.getDynamicObject("aos_developer").getPkValue();
                Object aosPoer = aosMktPhotoreq.getDynamicObject("aos_poer").getPkValue();
                if (aosNewitem) {
                    aosMktPhotoreq.set("aos_user", aosDeveloper);
                    messageId = aosDeveloper.toString();
                } else {
                    aosMktPhotoreq.set("aos_user", aosPoer);
                    messageId = aosPoer.toString();
                }
                FndHistory.Create(aosMktPhotoreq, "提交(3d回写),下节点：开发/采购", aosStatus);
                OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq", new DynamicObject[] {aosMktPhotoreq},
                    OperateOption.create());
                // 回写拍照任务清单
                if (QueryServiceHelper.exists(AOS_MKT_PHOTOLIST, aosMktPhotoreq.get(AOS_SOURCEID))) {
                    DynamicObject aosMktPhotolist =
                        BusinessDataServiceHelper.loadSingle(aosMktPhotoreq.get("aos_sourceid"), "aos_mkt_photolist");
                    aosMktPhotolist.set("aos_vedstatus", "开发/采购确认图片");
                    OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                        new DynamicObject[] {aosMktPhotolist}, OperateOption.create());
                }
                MktComUtil.sendGlobalMessage(messageId, "aos_mkt_photoreq", String.valueOf(aosSourceid),
                    String.valueOf(aosOrignbill), "开发/采购确认图片");
            }
        }
    }

    /**
     * 设计需求表创建3D产品设计单
     *
     * @param dyMain 设计需求表单据
     **/
    public static void generate3Design(List<DynamicObject> list3d, DynamicObject dyMain) throws FndError {
        // 信息参数
        String messageId;
        String message = "";
        // 异常参数
        FndError fndError = new FndError();
        // 数据层
        Object aosDesignerId = dyMain.get("aos_designby");
        List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(aosDesignerId);
        Object aosShipdate = dyMain.get("aos_shipdate");
        Object billno = dyMain.get("billno");
        Object reqFid = dyMain.getPkValue();
        DynamicObject aos3Der = dyMain.getDynamicObject("aos_3der");
        Object aos3DerId = aos3Der.getPkValue();
        Object aosType = dyMain.get("aos_type");
        String aosType3d = null;
        if (NEWDES.equals(aosType)) {
            aosType3d = "新建";
        } else if (OLDPRO.equals(aosType)) {
            aosType3d = "优化";
        }
        if (list3d.size() == 0) {
            fndError.add("3D行信息不存在!");
        }
        if (fndError.getCount() > 0) {
            throw fndError;
        }
        // 创建3D产品设计单头
        DynamicObject aosMkt3design = BusinessDataServiceHelper.newDynamicObject("aos_mkt_3design");
        // 头信息
        aosMkt3design.set("aos_requireby", aosDesignerId);
        aosMkt3design.set("aos_requiredate", new Date());
        aosMkt3design.set("aos_shipdate", aosShipdate);
        aosMkt3design.set("aos_orignbill", billno);
        aosMkt3design.set("aos_sourceid", reqFid);
        aosMkt3design.set("aos_3der", aos3DerId);
        aosMkt3design.set("aos_user", aos3DerId);
        aosMkt3design.set("aos_status", "新建");
        aosMkt3design.set("aos_designer", aosDesignerId);
        aosMkt3design.set("aos_type", aosType3d);
        aosMkt3design.set("aos_source", "设计需求表");
        aosMkt3design.set("aos_source1", dyMain.getString("aos_source"));
        // BOTP
        aosMkt3design.set("aos_sourcebilltype", "aos_mkt_designreq");
        aosMkt3design.set("aos_sourcebillno", billno);
        aosMkt3design.set("aos_srcentrykey", "aos_entryentity");
        // 是否已经生成3d
        List<String> skuList = DesignSkuList.getSkuList();
        DynamicObjectCollection aosEntryentityS = aosMkt3design.getDynamicObjectCollection("aos_entryentity");
        for (int i = 0; i < list3d.size(); i++) {
            DynamicObject dyn3dR = list3d.get(i);
            DynamicObject dyn3dD = dyn3dR.getDynamicObjectCollection("aos_subentryentity").get(0);
            if (i == 0) {
                aosMkt3design.set("aos_3dreq", dyn3dR.get("aos_3dreq"));
                // 3d需求附件
                DynamicObjectCollection aosAttribute = aosMkt3design.getDynamicObjectCollection("aos_3datta");
                DynamicObjectCollection aosAttributefrom = dyn3dR.getDynamicObjectCollection("aos_3datta");
                DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
                DynamicObject tempFile;
                for (DynamicObject d : aosAttributefrom) {
                    tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
                    aosAttribute.addNew().set("fbasedataid", tempFile);
                }
                aosMkt3design.set("aos_productno", dyn3dD.get("aos_segment3"));
            }
            if (mapList != null) {
                if (mapList.get(2) != null) {
                    aosMkt3design.set("aos_organization1", mapList.get(2).get("id"));
                }
                if (mapList.get(3) != null) {
                    aosMkt3design.set("aos_organization2", mapList.get(3).get("id"));
                }
            }
            // 产品信息
            DynamicObject aosEntryentity = aosEntryentityS.addNew();
            aosEntryentity.set("aos_itemid", dyn3dR.get("aos_itemid"));
            aosEntryentity.set("aos_is_design",
                skuList.contains(dyn3dR.getDynamicObject("aos_itemid").getString("id")));
            aosEntryentity.set("aos_is_saleout",
                ProgressUtil.Is_saleout(dyn3dR.getDynamicObject("aos_itemid").getPkValue()));
            aosEntryentity.set("aos_itemname", dyn3dD.get("aos_itemname"));
            aosEntryentity.set("aos_orgtext", dyn3dD.get("aos_orgtext"));
            aosEntryentity.set("aos_specification", dyn3dD.get("aos_spec"));
            aosEntryentity.set("aos_seting1", dyn3dD.get("aos_seting1"));
            aosEntryentity.set("aos_seting2", dyn3dD.get("aos_seting2"));
            aosEntryentity.set("aos_sellingpoint", dyn3dD.get("aos_sellingpoint"));
            aosEntryentity.set("aos_srcrowseq", dyn3dR.get("SEQ"));
        }
        messageId = String.valueOf(aos3DerId);
        message = "3D产品设计单-设计需求自动创建";
        OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_3design",
            new DynamicObject[] {aosMkt3design}, OperateOption.create());
        if (operationrst.isSuccess()) {
            String pk = operationrst.getSuccessPkIds().get(0).toString();
            FndHistory.Create("aos_mkt_3design", pk, "新建", "新建节点： " + aosMkt3design.getString("aos_status"));
        }
        // 修复关联关系
        try {
            ProgressUtil.botp("aos_mkt_3design", aosMkt3design.get("id"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
            MktComUtil.sendGlobalMessage(messageId, String.valueOf(aosMkt3design),
                String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMkt3design.getString("billno"), message);
        }
    }

    /** 拍照需求表类型创建3D **/
    public static void generate3Design(DynamicObject aosMktPhotoreq) throws FndError {
        // 信息参数
        String messageId;
        String message;
        // 数据层
        Object aosDesignerId = aosMktPhotoreq.getDynamicObject("aos_designer").getPkValue();
        List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(aosDesignerId);
        Object aosShipdate = aosMktPhotoreq.get("aos_shipdate");
        Object billno = aosMktPhotoreq.get("billno");
        Object reqFid = aosMktPhotoreq.get("id");
        Object aos3DerId = aosMktPhotoreq.getDynamicObject("aos_3d").getPkValue();
        Object itemId = aosMktPhotoreq.getDynamicObject("aos_itemid").getPkValue();
        // 创建一条3D产品设计单
        DynamicObject aosMkt3design = BusinessDataServiceHelper.newDynamicObject("aos_mkt_3design");
        // 头信息
        aosMkt3design.set("aos_requireby", aosDesignerId);
        aosMkt3design.set("aos_requiredate", new Date());
        aosMkt3design.set("aos_shipdate", aosShipdate);
        aosMkt3design.set("aos_orignbill", billno);
        aosMkt3design.set("aos_sourceid", reqFid);
        aosMkt3design.set("aos_3der", aos3DerId);
        aosMkt3design.set("aos_user", aos3DerId);
        aosMkt3design.set("aos_status", "新建");
        // 3D类型 此方法默认新建
        aosMkt3design.set("aos_type", "新建");
        aosMkt3design.set("aos_designer", aosDesignerId);
        aosMkt3design.set("aos_source", "拍照需求表");
        aosMkt3design.set("aos_quainscomdate", aosMktPhotoreq.get("aos_quainscomdate"));
        // 230718 gk:(新产品=是，或新供应商=是)且工厂简拍 生成3D产品设计单，3D产品设计单到大货样封样节点
        boolean newitem = aosMktPhotoreq.getBoolean("aos_newitem");
        boolean newvendor = aosMktPhotoreq.getBoolean("aos_newvendor");
        boolean phstate = aosMktPhotoreq.getString("aos_phstate").equals(FACEZ);
        boolean cond = ((newitem || newvendor) && phstate);
        if (cond) {
            boolean existShipdate = FndGlobal.IsNull(aosMktPhotoreq.get("aos_shipdate"));
            boolean existQuainscome = FndGlobal.IsNull(aosMktPhotoreq.get("aos_quainscomdate"));
            // 出运日期和质检完成日期都为空
            if (existQuainscome && existShipdate) {
                aosMkt3design.set("aos_status", "大货样封样");
                aosMkt3design.set("aos_user", SYSTEM);
            }
        }
        if (mapList != null) {
            if (mapList.get(TWO) != null) {
                aosMkt3design.set("aos_organization1", mapList.get(2).get("id"));
            }
            if (mapList.get(THREE) != null) {
                aosMkt3design.set("aos_organization2", mapList.get(3).get("id"));
            }
        }

        // 产品信息
        String aosOrgtext = "";
        DynamicObject bdMaterial = BusinessDataServiceHelper.loadSingle(itemId, "bd_material");
        DynamicObjectCollection aos_contryentryS = bdMaterial.getDynamicObjectCollection("aos_contryentry");
        for (DynamicObject aos_contryentry : aos_contryentryS) {
            DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
            String aos_nationalitynumber = aos_nationality.getString("number");
            if ("IE".equals(aos_nationalitynumber)) {
                continue;
            }
            Object org_id = aos_nationality.get("id"); // ItemId
            int OsQty = ItemInfoUtil.getItemOsQty(org_id, itemId);
            int SafeQty = ItemInfoUtil.getSafeQty(org_id);
            if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
                continue;
            aosOrgtext = aosOrgtext + aos_nationalitynumber + ";";
        }

        String item_number = bdMaterial.getString("number");
        String aos_productno = bdMaterial.getString("aos_productno");
        String aos_itemname = bdMaterial.getString("name");
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

        List<String> skuList = DesignSkuList.getSkuList();
        // 产品信息
        DynamicObjectCollection aos_entryentityS = aosMkt3design.getDynamicObjectCollection("aos_entryentity");
        DynamicObject aos_entryentity = aos_entryentityS.addNew();
        aos_entryentity.set("aos_itemid", itemId);
        aos_entryentity.set("aos_is_design", skuList.contains(String.valueOf(itemId)));
        aos_entryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(itemId));
        aosMkt3design.set("aos_productno", aos_productno);
        aos_entryentity.set("aos_itemname", aos_itemname);
        aos_entryentity.set("aos_orgtext", aosOrgtext);
        aos_entryentity.set("aos_specification", aosMktPhotoreq.get("aos_specification"));
        aos_entryentity.set("aos_seting1", aosMktPhotoreq.get("aos_seting1"));
        aos_entryentity.set("aos_seting2", aosMktPhotoreq.get("aos_seting2"));
        aos_entryentity.set("aos_sellingpoint", aosMktPhotoreq.get("aos_sellingpoint"));
        // 拍照需求单据
        DynamicObjectCollection dyc_phptoDemand = aosMktPhotoreq.getDynamicObjectCollection("aos_entryentity");
        // 存在申请人需求
        if (dyc_phptoDemand.size() > 0) {
            DynamicObject dy_reqEnt = dyc_phptoDemand.get(0);
            // 申请人需求
            aosMkt3design.set("aos_req", dy_reqEnt.get("aos_picdesc"));
            DynamicObjectCollection dyc_attr = dy_reqEnt.getDynamicObjectCollection("aos_picattr");
            DynamicObjectCollection dyc_reqAtt = aosMkt3design.getDynamicObjectCollection("aos_reqatt");
            for (DynamicObject dy_attr : dyc_attr) {
                dyc_reqAtt.addNew().set("fbasedataid", dy_attr);
            }
        }
        // 存在开发需求
        if (dyc_phptoDemand.size() > 1) {
            DynamicObject dy_deveEnt = dyc_phptoDemand.get(1);
            // 开发需求
            aosMkt3design.set("aos_deve", dy_deveEnt.get("aos_picdesc"));
            DynamicObjectCollection dyc_attr = dy_deveEnt.getDynamicObjectCollection("aos_picattr");
            DynamicObjectCollection dyc_deveAtt = aosMkt3design.getDynamicObjectCollection("aos_deveatt");
            for (DynamicObject dy_attr : dyc_attr) {
                dyc_deveAtt.addNew().set("fbasedataid", dy_attr);
            }
        }

        // 24-1-3 gk:存在3D需求附件
        DynamicObjectCollection attaRows = aosMktPhotoreq.getDynamicObjectCollection("aos_3datta");
        if (attaRows.size() > 0) {
            DynamicObjectCollection designRows = aosMkt3design.getDynamicObjectCollection("aos_3datta");
            for (DynamicObject attaRow : attaRows) {
                designRows.addNew().set("fbasedataid", attaRow);
            }
        }

        DynamicObjectCollection aos_entryentity6S = aosMktPhotoreq.getDynamicObjectCollection("aos_entryentity6");
        if (aos_entryentity6S.size() > 0) {
            DynamicObject aos_entryentity6 = aos_entryentity6S.get(0);
            // 申请人需求
            aosMkt3design.set("aos_req", aos_entryentity6.get("aos_reqsupp"));
            DynamicObjectCollection dyc_attr = aos_entryentity6.getDynamicObjectCollection("aos_attach1");
            DynamicObjectCollection dyc_reqAtt = aosMkt3design.getDynamicObjectCollection("aos_reqatt");
            for (DynamicObject dy_attr : dyc_attr) {
                dyc_reqAtt.addNew().set("fbasedataid", dy_attr);
            }
            // 开发需求
            aosMkt3design.set("aos_deve", aos_entryentity6.get("aos_devsupp"));
            DynamicObjectCollection dyc_attr2 = aos_entryentity6.getDynamicObjectCollection("aos_attach2");
            DynamicObjectCollection dyc_deveAtt = aosMkt3design.getDynamicObjectCollection("aos_deveatt");
            for (DynamicObject dy_attr2 : dyc_attr2) {
                dyc_deveAtt.addNew().set("fbasedataid", dy_attr2);
            }
        }

        messageId = String.valueOf(aos3DerId);
        message = "3D产品设计单-设计需求自动创建";
        OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_3design",
            new DynamicObject[] {aosMkt3design}, OperateOption.create());
        if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
            MktComUtil.sendGlobalMessage(messageId, String.valueOf(aosMkt3design),
                String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMkt3design.getString("billno"), message);
        }
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        this.addItemClickListeners("aos_toolbarap");// 给工具栏加监听事件
        this.addItemClickListeners("aos_submit"); // 提交
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String Control = evt.getItemKey();
        try {
            if ("aos_submit".equals(Control)) {
                DynamicObject dy_main = this.getModel().getDataEntity(true);
                aos_submit(dy_main, "A");// 提交
            } else if ("aos_history".equals(Control))
                aos_history();// 查看历史记录
            else if ("aos_querysample".equals(Control)) {
                openSample(this.getView(), this.getModel().getDataEntity(true).getPkValue());
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    /* 打开历史记录 **/
    private void aos_history() throws FndError {
        Cux_Common_Utl.OpenHistory(this.getView());
    }

    /** 初始化事件 **/
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        StatusControl();
    }

    /** 新建事件 **/
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        StatusControl();// 界面控制
    }

    /** 全局状态控制 **/
    private void StatusControl() {
        // 数据层
        Object AosStatus = this.getModel().getValue("aos_status");
        DynamicObject AosUser = (DynamicObject)this.getModel().getValue("aos_user");
        Object CurrentUserId = UserServiceHelper.getCurrentUserId();
        Object CurrentUserName = UserServiceHelper.getUserInfoByID((long)CurrentUserId).get("name");

        // 图片控制

        // 锁住需要控制的字段

        // 当前节点操作人不为当前用户 全锁
        if (!AosUser.getPkValue().toString().equals(CurrentUserId.toString())
            && !"刘中怀".equals(CurrentUserName.toString()) && !"程震杰".equals(CurrentUserName.toString())
            && !"陈聪".equals(CurrentUserName.toString())) {
            // this.getView().setEnable(false, "titlepanel");// 标题面板
            this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
            this.getView().setVisible(false, "bar_save");
            this.getView().setVisible(false, "aos_submit");
        }

        // 状态控制
        else if ("新建".equals(AosStatus)) {
            this.getView().setVisible(true, "bar_save");
            this.getView().setVisible(true, "aos_submit");
        } else if ("已完成".equals(AosStatus)) {
            this.getView().setVisible(false, "bar_save");
            this.getView().setVisible(false, "aos_submit");
            this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
        }

    }

    /** 提交 **/
    public void aos_submit(DynamicObject dy_main, String type) throws FndError {
        // 根据状态判断当前流程节点
        String aos_status = dy_main.getString("aos_status");
        if (aos_status.equals("新建")) {
            submitForNew(dy_main);
            DesignSkuList.createEntity(dy_main);
        }
        FndHistory.Create(dy_main, "提交", aos_status);
        SaveServiceHelper.save(new DynamicObject[] {dy_main});
        if (type.equals("A")) {
            this.getView().invokeOperation("refresh");
            StatusControl();// 提交完成后做新的界面状态控制
        }

    }

}
