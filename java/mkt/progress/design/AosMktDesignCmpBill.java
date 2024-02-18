package mkt.progress.design;

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
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.entity.EntityMetadataCache;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.ClientProperties;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MktComUtil;
import mkt.progress.ProgressUtil;

/**
 * @author aosom
 * @version 设计完成表-表单插件
 */
public class AosMktDesignCmpBill extends AbstractBillPlugIn implements ItemClickListener, HyperLinkClickListener {
    /** 系统管理员 **/
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    public final static String EN = "EN";
    public final static String US = "US";
    public final static String CA = "CA";
    public final static String UK = "UK";
    public final static String A = "A";
    public final static String OUGROUP = "ES/IT/DE/FR";
    public final static String AOS_SUBMIT = "aos_submit";
    public final static String AOS_BACK = "aos_back";
    public final static String AOS_OPEN = "aos_open";
    public final static String SAME = "四者一致";
    public final static String DESIGN = "设计传图";
    public final static String SALECONFIRM = "销售确认";
    public final static String APPLY = "申请人";
    public final static String END = "结束";
    public final static String AOS_SEGMENT3 = "aos_segment3";
    public final static String OLD = "老品优化";
    public final static int TWO = 2;
    public final static int THREE = 3;

    /** 设计传图 **/
    private static void submitForEditor(DynamicObject dyMain) throws FndError {
        // 数据层
        DynamicObject aosOrgid = dyMain.getDynamicObject("aos_orgid");
        String aosOrgnumber = null;
        Object aosLanguage = dyMain.getDynamicObjectCollection("aos_entryentity").get(0).get("aos_language");
        String aosLanguagenumber = null;
        if (aosOrgid != null) {
            aosOrgnumber = aosOrgid.getString("number");
        }
        if (aosLanguage != null) {
            aosLanguagenumber = (String)aosLanguage;
        }
        int total = 0;
        DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            if (aosEntryentity.getBoolean("aos_valid")) {
                total += aosEntryentity.getInt("aos_whited") + aosEntryentity.getInt("aos_whites")
                    + aosEntryentity.getInt("aos_backround") + aosEntryentity.getInt("aos_sizepic")
                    + aosEntryentity.getInt("aos_anew") + aosEntryentity.getInt("aos_alike")
                    + aosEntryentity.getInt("aos_trans") + aosEntryentity.getInt("aos_despic")
                    + aosEntryentity.getInt("aos_ps") + aosEntryentity.getInt("aos_fix");
            }
        }
        // 第一行功能图翻译语种为EN才走老逻辑 不然都生成确认单
        if (EN.equals(aosLanguagenumber)) {
            if (US.equals(aosOrgnumber) || CA.equals(aosOrgnumber) || UK.equals(aosOrgnumber)) {
                // 英语国别生成销售确认单
                generateListingSal(dyMain);
            } else if (total == 0) {
                // 小语种国别生成listing优化需求-小语种
                generateListingLanguage(dyMain);
            }
        } else {
            // 生成销售确认单
            generateListingSal(dyMain);
        }
        dyMain.set("aos_status", "结束");
        dyMain.set("aos_user", SYSTEM);
    }

    /** 创建Listing优化需求表小语种 **/
    private static void generateListingLanguage(DynamicObject dyMain) throws FndError {
        // 信息参数
        String messageId;
        String message;
        // 数据层
        DynamicObject aosDesigner = dyMain.getDynamicObject("aos_designer");
        DynamicObject aosOrgid = dyMain.getDynamicObject("aos_orgid");
        String orgid = null;
        if (!Cux_Common_Utl.IsNull(aosOrgid)) {
            orgid = aosOrgid.getString("id");
        }
        Object aosDesignerId = aosDesigner.getPkValue();
        Object billno = dyMain.get("billno");
        // 当前界面主键
        Object reqFid = dyMain.getPkValue();
        // 任务类型
        Object aosType = dyMain.get("aos_type");
        // 任务来源
        Object aosSource = dyMain.get("aos_source");
        // 紧急程度
        Object aosImportance = dyMain.get("aos_importance");
        // 设计需求表申请人
        DynamicObject aosRequireby = dyMain.getDynamicObject("aos_requireby");
        List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(aosRequireby.getPkValue());
        Object lastItemId = null;
        Object aosDemandate = new Date();
        if (SAME.equals(aosType)) {
            aosDemandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
        } else if (OLD.equals(aosType)) {
            aosDemandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);
        }
        // 校验小语种是否重复
        duplicateCheck(dyMain);
        // 生成小语种
        DynamicObject aosMktListingMin = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_min");
        aosMktListingMin.set("aos_requireby", aosRequireby);
        aosMktListingMin.set("aos_requiredate", new Date());
        aosMktListingMin.set("aos_type", "功能图翻译");
        aosMktListingMin.set("aos_source", aosSource);
        aosMktListingMin.set("aos_importance", aosImportance);
        aosMktListingMin.set("aos_designer", aosDesignerId);
        aosMktListingMin.set("aos_orignbill", billno);
        aosMktListingMin.set("aos_sourceid", reqFid);
        aosMktListingMin.set("aos_status", "编辑确认");
        // 小语种来源类型为设计完成表
        aosMktListingMin.set("aos_sourcetype", "CMP");
        aosMktListingMin.set("aos_orgid", aosOrgid);
        aosMktListingMin.set("aos_demandate", aosDemandate);
        if (mapList != null) {
            if (mapList.get(TWO) != null) {
                aosMktListingMin.set("aos_organization1", mapList.get(2).get("id"));
            }
            if (mapList.get(THREE) != null) {
                aosMktListingMin.set("aos_organization2", mapList.get(3).get("id"));
            }
        }
        DynamicObjectCollection mktListingMinS = aosMktListingMin.getDynamicObjectCollection("aos_entryentity");
        DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
        // 循环所有行
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            if (!aosEntryentity.getBoolean("aos_valid")) {
                continue;
            }
            DynamicObject mktListingMin = mktListingMinS.addNew();
            DynamicObject subentryentity = aosEntryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
            lastItemId = aosEntryentity.get("aos_itemid.id");
            mktListingMin.set("aos_itemid", aosEntryentity.get("aos_itemid"));
            mktListingMin.set("aos_is_saleout", ProgressUtil.Is_saleout(lastItemId));
            mktListingMin.set("aos_require", aosEntryentity.get("aos_desreq"));
            // 附件
            DynamicObjectCollection aosAttribute = mktListingMin.getDynamicObjectCollection("aos_attribute");
            aosAttribute.clear();
            DynamicObjectCollection aosAttributefrom = aosEntryentity.getDynamicObjectCollection("aos_attribute");
            DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
            DynamicObject tempFile;
            for (DynamicObject d : aosAttributefrom) {
                tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
                aosAttribute.addNew().set("fbasedataid", tempFile);
            }
            DynamicObjectCollection aosSubentryentityS = mktListingMin.getDynamicObjectCollection("aos_subentryentity");
            DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
            aosSubentryentity.set("aos_segment3", subentryentity.get("aos_segment3"));
            aosSubentryentity.set("aos_broitem", subentryentity.get("aos_broitem"));
            aosSubentryentity.set("aos_itemname", subentryentity.get("aos_itemname"));
            aosSubentryentity.set("aos_orgtext", subentryentity.get("aos_orgtext"));
            aosSubentryentity.set("aos_reqinput", aosEntryentity.get("aos_desreq"));
            mktListingMin.set("aos_segment3_r", subentryentity.get("aos_segment3"));
            mktListingMin.set("aos_broitem_r", subentryentity.get("aos_broitem"));
            mktListingMin.set("aos_itemname_r", subentryentity.get("aos_itemname"));
            mktListingMin.set("aos_orgtext_r",
                ProgressUtil.getOrderOrg(aosEntryentity.getDynamicObject("aos_itemid").getPkValue()));
        }
        // 根据循环中最后一个物料去获取对应的 英语编辑师 小语种编辑师 LastItemId
        String category = MktComUtil.getItemCateNameZh(lastItemId);
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
        long aosEditor = 0;
        if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
            QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
            QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
            QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2};
            String selectStr = "aos_eng aos_editor";
            DynamicObject aosMktProguser = QueryServiceHelper.queryOne("aos_mkt_proguser", selectStr, filtersCategory);
            if (aosMktProguser != null) {
                aosEditor = aosMktProguser.getLong("aos_editor");
            }
        }
        if (aosEditor == 0) {
            throw new FndError(aosCategory1 + "," + aosCategory2 + "英语编辑不存在!");
        }
        long aosOueditor = 0;
        if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
            DynamicObject aosMktProgorguser =
                ProgressUtil.minListtFindEditorByType(orgid, aosCategory1, aosCategory2, aosType.toString());
            if (aosMktProgorguser != null) {
                aosOueditor = aosMktProgorguser.getLong("aos_user");
            }
        }
        if (aosOueditor == 0) {
            throw new FndError(aosCategory1 + "," + aosCategory2 + "小语种编辑师不存在!");
        }
        // 小语种编辑师
        aosMktListingMin.set("aos_editormin", aosOueditor);
        aosMktListingMin.set("aos_user", aosOueditor);
        messageId = String.valueOf(aosOueditor);
        message = "Listing优化需求表小语种-设计完成表自动创建";
        OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
            new DynamicObject[] {aosMktListingMin}, OperateOption.create());
        if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
            MktComUtil.sendGlobalMessage(messageId, String.valueOf(aosMktListingMin),
                String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktListingMin.getString("billno"), message);
            FndHistory.Create(dyMain, messageId, "生成小语种");
            FndHistory.Create(aosMktListingMin, aosMktListingMin.getString("aos_status"), "Listing优化需求表小语种-设计完成表自动创建");
        }
    }

    /** 生成销售确认单 **/
    private static void generateListingSal(DynamicObject dyMain) {
        // 信息处理
        String messageId;
        String message;
        // 数据层
        // 设计
        Object aosDesigner = dyMain.getDynamicObject("aos_designer").getPkValue();
        // 编辑确认师傅
        Object billno = dyMain.get("billno");
        // 当前界面主键
        Object reqFid = dyMain.getPkValue();
        DynamicObject aosOrgid = dyMain.getDynamicObject("aos_orgid");
        Object aosType = dyMain.get("aos_type");
        Object aosOrgnumber = aosOrgid.getString("number");
        Object itemId =
            dyMain.getDynamicObjectCollection("aos_entryentity").get(0).getDynamicObject("aos_itemid").getPkValue();
        String category = MktComUtil.getItemCateNameZh(itemId);
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
        long aosOueditor = 0;
        if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
            QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
            QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
            QFilter filterOu = new QFilter("aos_orgid.number", "=", aosOrgnumber);
            QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2, filterOu};
            String selectStr = "aos_oueditor";
            DynamicObject aosMktProgorguser =
                QueryServiceHelper.queryOne("aos_mkt_progorguser", selectStr, filtersCategory);
            if (aosMktProgorguser != null) {
                aosOueditor = aosMktProgorguser.getLong("aos_oueditor");
            }
        }
        if (aosOueditor == 0) {
            throw new FndError(aosCategory1 + "," + aosCategory2 + "国别编辑师不存在!");
        }
        DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
        // 初始化
        DynamicObject aosMktListingSal = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_sal");
        aosMktListingSal.set("aos_requireby", aosDesigner);
        aosMktListingSal.set("aos_designer", aosDesigner);
        aosMktListingSal.set("aos_status", "销售确认");
        aosMktListingSal.set("aos_orgid", aosOrgid);
        aosMktListingSal.set("aos_orignbill", billno);
        aosMktListingSal.set("aos_sourceid", reqFid);
        aosMktListingSal.set("aos_type", aosType);
        aosMktListingSal.set("aos_requiredate", new Date());
        aosMktListingSal.set("aos_editor", aosOueditor);
        aosMktListingSal.set("aos_sourcetype", "设计完成表");
        DynamicObjectCollection cmpEntryentityS = aosMktListingSal.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            if (!aosEntryentity.getBoolean("aos_valid")) {
                continue;
            }
            DynamicObject aosSubentryentity = aosEntryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
            DynamicObject cmpEntryentity = cmpEntryentityS.addNew();
            cmpEntryentity.set("aos_itemid", aosEntryentity.get("aos_itemid"));
            cmpEntryentity.set("aos_segment3", aosSubentryentity.get("aos_segment3"));
            cmpEntryentity.set("aos_itemname", aosSubentryentity.get("aos_itemname"));
            cmpEntryentity.set("aos_broitem", aosSubentryentity.get("aos_broitem"));
            cmpEntryentity.set("aos_salestatus", "已确认");
            cmpEntryentity.set("aos_text", aosEntryentity.get("aos_designway"));
        }
        long aosSale = 0;
        if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
            QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
            QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
            QFilter filterOu = new QFilter("aos_orgid.number", "=", aosOrgnumber);
            QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2, filterOu};
            String selectStr = "aos_salehelper aos_salehelper";
            DynamicObject aosMktProgorguser =
                QueryServiceHelper.queryOne("aos_mkt_progorguser", selectStr, filtersCategory);
            if (aosMktProgorguser != null) {
                aosSale = aosMktProgorguser.getLong("aos_salehelper");
            }
        }
        if (aosSale == 0) {
            throw new FndError(aosCategory1 + "," + aosCategory2 + "国别销售不存在!");
        }
        aosMktListingSal.set("aos_sale", aosSale);
        aosMktListingSal.set("aos_user", aosSale);
        messageId = String.valueOf(aosSale);
        message = "Listing优化销售确认单-设计完成表国别自动创建";
        OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_sal",
            new DynamicObject[] {aosMktListingSal}, OperateOption.create());
        if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
            MktComUtil.sendGlobalMessage(messageId, String.valueOf(aosMktListingSal),
                String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktListingSal.getString("billno"), message);
            FndHistory.Create(dyMain, messageId, "生成销售确认单");
            FndHistory.Create(aosMktListingSal, aosMktListingSal.getString("aos_status"), "Listing优化需求表小语种-设计完成表自动创建");
        }
    }

    /** 销售确认 **/
    private static void submitForSale(DynamicObject dyMain) throws FndError {
        // 数据层
        DynamicObject aosOrgid = dyMain.getDynamicObject("aos_orgid");
        String aosOrgnumber = null;
        Object aosLanguage = dyMain.getDynamicObjectCollection("aos_entryentity").get(0).get("aos_language");
        String aosLanguagenumber = null;
        if (aosOrgid != null) {
            aosOrgnumber = aosOrgid.getString("number");
        }
        if (aosLanguage != null) {
            aosLanguagenumber = (String)aosLanguage;
        }
        Boolean valid = getValid(dyMain);
        // 功能图全新+功能图类似
        int func = 0;
        // 白底精修或 背景有值或是否3D建模=是
        DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            if (aosEntryentity.getBoolean("aos_valid")) {
                func += aosEntryentity.getInt("aos_funcpic") + aosEntryentity.getInt("aos_funclike");
            }
        }

        // 设置单据流程状态
        // 流转给设计
        // 设置单据流程状态
        // 流转给设计
        if (aosOrgnumber != null && EN.equals(aosLanguagenumber) && (OUGROUP.contains(aosOrgnumber))) {
            // 是否应用
            if (valid) {
                // 生成小语种
                generateListingLanguage(dyMain);
            }
            // 功能图全新+功能图类似
            if (func > 0) {
                // 设置单据流程状态
                dyMain.set("aos_status", "设计传图");
                // 流转给设计
                dyMain.set("aos_user", dyMain.get("aos_requireby"));
            } else {
                // 设置单据流程状态
                dyMain.set("aos_status", "结束");
                // 流转给设计
                dyMain.set("aos_user", SYSTEM);
            }
        }
    }

    private static Boolean getValid(DynamicObject dyMain) {
        boolean valid = false;
        DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            boolean aosValid = aosEntryentity.getBoolean("aos_valid");
            if (aosValid) {
                valid = true;
                break;
            }
        }
        return valid;
    }

    /** 申请人提交 **/
    private static void submitForApply(DynamicObject dyMain) {
        String messageId;
        String message = "设计完成表(国别)-销售确认";
        // 当前界面主键
        Object reqFid = dyMain.getPkValue();
        Object billno = dyMain.get("billno");
        Object aosItemid =
            dyMain.getDynamicObjectCollection("aos_entryentity").get(0).getDynamicObject("aos_itemid").getPkValue();
        Object aosOrgid = dyMain.getDynamicObject("aos_orgid").getPkValue();
        Object aosSale = ProgressUtil.findUserByOrgCate(aosOrgid, aosItemid, "aos_02hq");
        messageId = String.valueOf(((DynamicObject)aosSale).getPkValue());
        // 设置单据流程状态
        dyMain.set("aos_status", "销售确认");
        // 流转给销售人员
        dyMain.set("aos_user", aosSale);
        MktComUtil.sendGlobalMessage(messageId, "aos_mkt_designcmp", String.valueOf(reqFid), String.valueOf(billno),
            message);
    }

    /** 生成小语种文案的时候先校验是否存在，存在的情况下，先删除 **/
    private static void duplicateCheck(DynamicObject dyMain) {
        String billno = dyMain.getString("billno");
        if (billno == null) {
            return;
        }
        List<String> listItem = dyMain.getDynamicObjectCollection("aos_entryentity").stream()
            .map(dy -> dy.getDynamicObject("aos_itemid").getString("id")).distinct().collect(Collectors.toList());
        // 查找小语种是否存在
        QFilter filterBillno = new QFilter("aos_orignbill", "=", billno);
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        str.add("aos_entryentity.aos_itemid");
        List<String> listDelete = new ArrayList<>();
        DynamicObject[] dyc =
            BusinessDataServiceHelper.load("aos_mkt_listing_min", str.toString(), new QFilter[] {filterBillno});
        // 校验每单的物料是否在当前的提交单中都存在
        for (DynamicObject dyMin : dyc) {
            DynamicObjectCollection dycEnt = dyMin.getDynamicObjectCollection("aos_entryentity");
            boolean reject = true;
            for (DynamicObject dy : dycEnt) {
                String itemid = dy.getDynamicObject("aos_itemid").getString("id");
                if (!listItem.contains(itemid)) {
                    reject = false;
                    break;
                }
            }
            if (reject && !listDelete.contains(dyMin.getString("id"))) {
                listDelete.add(dyMin.getString("id"));
            }
        }
        QFilter filterId = new QFilter("id", QFilter.in, listDelete);
        DeleteServiceHelper.delete("aos_mkt_listing_min", new QFilter[] {filterId});
        // 添加日志
        FndHistory.Create(dyMain, "设计完成表生成小语种单据", "小语种存在重复单据:   " + listDelete.size());
    }

    @Override
    public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
        int rowIndex = hyperLinkClickEvent.getRowIndex();
        String fieldName = hyperLinkClickEvent.getFieldName();
        if (AOS_SEGMENT3.equals(fieldName)) {
            Object aosSegment3 = this.getModel().getValue("aos_segment3", rowIndex);
            DynamicObject aosMktFunctreq = QueryServiceHelper.queryOne("aos_mkt_functreq", "id",
                new QFilter[] {new QFilter("aos_segment3", QCP.equals, aosSegment3)});
            if (!Cux_Common_Utl.IsNull(aosMktFunctreq)) {
                Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_functreq", aosMktFunctreq.get("id"));
            } else {
                this.getView().showErrorNotification("功能图需求表信息不存在!");
            }
        }
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        // 提交
        this.addItemClickListeners("aos_submit");
        // 编辑退回
        this.addItemClickListeners("aos_back");
        EntryGrid entryGrid = this.getControl("aos_subentryentity");
        entryGrid.addHyperClickListener(this);
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        try {
            if (AOS_SUBMIT.equals(control)) {
                aosSubmit(this.getModel().getDataEntity(true), "A");
            } else if (AOS_BACK.equals(control)) {
                aosBack();
            } else if (AOS_OPEN.equals(control)) {
                aosOpen();
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    private void aosOpen() {
        Object aosSourceid = this.getModel().getValue("aos_sourceid");
        Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_designreq", aosSourceid);
    }

    /** 初始化事件 **/
    @Override
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        statusControl();
    }

    /** 新建事件 **/
    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        statusControl();// 界面控制
    }

    /** 界面关闭事件 **/
    @Override
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        e.setCheckDataChange(false);
    }

    /** 销售退回 **/
    private void aosBack() throws FndError {
        String messageId;
        String message = "设计完成表(国别)-销售退回";
        // 当前界面主键
        Object reqFid = this.getModel().getDataEntity().getPkValue();
        Object billno = this.getModel().getValue("billno");
        Object aosRequireby = this.getModel().getValue("aos_requireby");
        boolean reasonflag = false;
        // 校验 销售退回时，明细中必须有一行有退回原因；
        DynamicObjectCollection aosEntryentityS = this.getModel().getEntryEntity("aos_entryentity");
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            String aosReason = aosEntryentity.getString("aos_reason");
            if (!Cux_Common_Utl.IsNull(aosReason)) {
                reasonflag = true;
            }
        }
        if (!reasonflag) {
            throw new FndError("销售退回时，明细中必须有一行有退回原因!");
        }
        messageId = String.valueOf(aosRequireby);
        // 设置单据流程状态
        this.getModel().setValue("aos_status", "申请人");
        // 流转给编辑
        this.getModel().setValue("aos_user", aosRequireby);
        this.getView().invokeOperation("save");
        this.getView().invokeOperation("refresh");
        MktComUtil.sendGlobalMessage(messageId, "aos_mkt_designcmp", String.valueOf(reqFid), String.valueOf(billno),
            message);
    }

    /** 提交 **/
    public void aosSubmit(DynamicObject dyMain, String type) throws FndError {
        // 根据状态判断当前流程节点
        String aosStatus = dyMain.getString("aos_status");
        switch (aosStatus) {
            case "申请人":
                submitForApply(dyMain);
                break;
            case "销售确认":
                submitForSale(dyMain);
                break;
            case "设计传图":
                submitForEditor(dyMain);
                break;
            default:
                break;
        }
        FndHistory.Create(dyMain, "提交", aosStatus);
        SaveServiceHelper.save(new DynamicObject[] {dyMain});
        if (A.equals(type)) {
            this.getView().invokeOperation("refresh");
            statusControl();// 提交完成后做新的界面状态控制
        }
    }

    /** 全局状态控制 **/
    private void statusControl() {
        // 数据层
        Object aosStatus = this.getModel().getValue("aos_status");
        DynamicObject aosUser = (DynamicObject)this.getModel().getValue("aos_user");
        Object currentUserId = UserServiceHelper.getCurrentUserId();
        Object currentUserName = UserServiceHelper.getUserInfoByID((long)currentUserId).get("name");
        // 锁住需要控制的字段
        this.getView().setVisible(false, "aos_back");
        // 当前节点操作人不为当前用户 全锁
        if (!aosUser.getPkValue().toString().equals(currentUserId.toString())
            && !"程震杰".equals(currentUserName.toString()) && !"陈聪".equals(currentUserName.toString())
            && !"刘中怀".equals(currentUserName.toString())) {
            // 标题面板
            this.getView().setEnable(false, "titlepanel");
            // 主界面面板
            this.getView().setEnable(false, "aos_contentpanelflex");
            this.getView().setVisible(false, "bar_save");
            this.getView().setVisible(false, "aos_submit");
        }
        // 状态控制
        Map<String, Object> map = new HashMap<>(16);
        if (SALECONFIRM.equals(aosStatus)) {
            map.put(ClientProperties.Text, new LocaleString("销售确认"));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(true, "aos_submit");
            // 主界面面板
            this.getView().setEnable(true, "aos_contentpanelflex");
            this.getView().setVisible(true, "bar_save");
            this.getView().setVisible(true, "aos_back");
        } else if (APPLY.equals(aosStatus)) {
            map.put(ClientProperties.Text, new LocaleString("提交"));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(true, "aos_submit");
            this.getView().setEnable(true, "aos_contentpanelflex");
            this.getView().setVisible(true, "bar_save");
            this.getView().setVisible(false, "aos_back");
        } else if (END.equals(aosStatus)) {
            this.getView().setVisible(false, "aos_submit");
            this.getView().setEnable(false, "aos_contentpanelflex");
            this.getView().setVisible(false, "bar_save");
        } else if (DESIGN.equals(aosStatus)) {
            this.getView().setVisible(true, "aos_submit");
            this.getView().setEnable(true, "aos_contentpanelflex");
            this.getView().setVisible(true, "bar_save");
        }
    }
}