package mkt.progress.design.aadd;

import java.util.ArrayList;
import java.util.Date;
import java.util.EventObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.fnd.FndMsg;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.ClientProperties;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.Image;
import kd.bos.form.control.Label;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
import kd.bos.form.field.ComboEdit;
import kd.bos.form.field.ComboItem;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MktComUtil;
import mkt.progress.iface.ItemInfoUtil;

/**
 * @author aosom
 * @version 高级A+需求单-表单插件
 */
public class AosMktAaddBill extends AbstractBillPlugIn implements HyperLinkClickListener {
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    public final static String NEWTYPE = "新款";
    public final static String ENORG = "US,UK,CA";
    public final static String EUORG = "IT,ES,DE,FR";
    public final static String YES = "是";
    public final static String IE = "IE";
    public final static String AOS_OUEDITOR = "aos_oueditor";
    public final static String AOS_02HQ = "aos_02hq";
    public final static String AOS_SALEHELPER = "aos_salehelper";
    public final static String AOS_OSEDITOR = "aos_oseditor";
    public final static String AOS_DESIGNER = "aos_designer";
    public final static String AOS_DESIGNASSIT = "aos_designassit";
    public final static String MIN = "MIN";
    public final static String NORMAL = "NORMAL";
    public final static String EN_05 = "EN_05";
    public final static String EN_04 = "EN_04";
    public final static String SM_04 = "SM_04";
    public final static String SM_02 = "SM_02";
    public final static String NEWMODEL = "新品对比模块录入";
    public final static String AOS_URL = "aos_url";
    public final static String NEW = "新建";
    public final static String EDCONFIRM = "文案确认";
    public final static String OSCONFIRM = "海外确认";
    public final static String DESIGN = "设计制作";
    public final static String SALEONLINE = "销售上线";
    public final static String AOS_PRODUCTNO = "aos_productno";
    public final static String AOS_TYPE = "aos_type";
    public final static String AOS_ITEMID = "aos_itemid";
    public final static String SAVE = "save";
    public final static String AOS_SUBMIT = "aos_submit";
    public final static String AOS_HISTORY = "aos_history";
    public final static String A = "A";
    public final static int TWO = 2;
    public final static String CZJ = "程震杰";

    /**
     * 销售上线提交 B88F-81D5
     */
    public static void submitForSale(DynamicObject dyMain) throws FndError {
        FndMsg.debug("=====Into submitForSale=====");
        String reqFid = dyMain.getPkValue().toString();
        String aosBillNo = dyMain.getString("aos_billno");
        Object aosRequireby = dyMain.get("aos_requireby");
        dyMain.set("aos_status", "已完成");
        dyMain.set("aos_user", SYSTEM);
        dyMain.set("aos_salesub_date", new Date());
        // 发送消息
        String messageId = aosRequireby.toString();
        MktComUtil.sendGlobalMessage(messageId, "aos_mkt_aadd", reqFid, aosBillNo, "高级A+需求单-已完成");
    }

    /**
     * 新品选品模块录入提交
     */
    public static void submitForModel(DynamicObject dyMain) throws FndError {
        FndMsg.debug("=====Into submitForModel=====");
        String reqFid = dyMain.getPkValue().toString();
        String aosBillNo = dyMain.getString("aos_billno");
        Object aosUser = dyMain.get("aos_sale");
        DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
        DynamicObject aosItemId = null;
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            aosItemId = aosEntryentity.getDynamicObject("aos_itemid");
        }
        // 国别
        String aosOrg = dyMain.getString("aos_org");
        Boolean itemExist = queryItemExist(aosItemId, aosOrg);
        if (!itemExist) {
            dyMain.set("aos_user", SYSTEM);
            dyMain.set("aos_status", "已完成");
        } else {
            // 判断是否存在国别首次入库日期
            Date aosFirstindate = queryItemFirstDate(aosItemId, aosOrg);
            if (FndGlobal.IsNotNull(aosFirstindate)) {
                dyMain.set("aos_status", "销售上线");
                dyMain.set("aos_firstindate", aosFirstindate);
                dyMain.set("aos_salerece_date", new Date());
                dyMain.set("aos_user", aosUser);
            } else {
                dyMain.set("aos_status", "销售上线");
                dyMain.set("aos_user", SYSTEM);
            }
        }
        // 发送消息
        String messageId = aosUser.toString();
        MktComUtil.sendGlobalMessage(messageId, "aos_mkt_aadd", reqFid, aosBillNo, "高级A+需求单-销售上线");
    }

    private static Date queryItemFirstDate(DynamicObject aosItemId, String aosOrg) {
        try {
            DynamicObject bdMaterial =
                QueryServiceHelper.queryOne("bd_material", "aos_contryentry.aos_firstindate aos_firstindate",
                    new QFilter("id", QCP.equals, aosItemId.getPkValue().toString())
                        .and("aos_contryentry.aos_nationality.number", QCP.equals, aosOrg)
                        .and("aos_contryentry.aos_contryentrystatus", QCP.not_equals, "F")
                        .and("aos_contryentry.aos_contryentrystatus", QCP.not_equals, "H").toArray());
            if (FndGlobal.IsNotNull(bdMaterial)) {
                return bdMaterial.getDate("aos_firstindate");
            } else {
                return null;
            }
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 设计制作提交
     */
    public static void submitForDesign(DynamicObject dyMain) throws FndError {
        Object aosType = dyMain.get("aos_type");
        // 国别
        Object aosOrg = dyMain.get("aos_org");
        String reqFid = dyMain.getPkValue().toString();
        String aosBillNo = dyMain.getString("aos_billno");
        DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
        DynamicObject aosItemId = aosEntryentityS.get(0).getDynamicObject("aos_itemid");
        Object aosProductno = aosItemId.get("aos_productno");
        if (NEWTYPE.equals(aosType)) {
            // 触发生成同款单据
            // 1.按流程中的SKU，生成其他英语国别(US/CA/UK)的同款高级A+需求表
            Set<String> ouSet = getItemOrg(aosItemId.getPkValue());
            if (ouSet.size() > 0) {
                for (String ou : ouSet) {
                    if (ENORG.contains(ou) && !aosOrg.equals(ou)) {
                        genSameAadd(reqFid, ou, 0, "NORMAL", aosItemId.getPkValue());
                    }
                }
            }
            // 2.按流程中的SKU，找到对应产品号下的其他SKU，生成多张英语国别(US/CA/UK)同款高级A+需求表，流程到04节点
            DynamicObjectCollection sameSegmentS = QueryServiceHelper.query("bd_material", "id",
                new QFilter("aos_productno", QCP.equals, aosProductno).and("aos_protype", QCP.equals, "N").toArray());
            for (DynamicObject sameSegment : sameSegmentS) {
                Set<String> segmentSet = getItemOrg(sameSegment.get("id"));
                if (segmentSet.size() > 0) {
                    for (String ou : segmentSet) {
                        if (ENORG.contains(ou)
                            && !sameSegment.getString("id").equals(aosItemId.getPkValue().toString())) {
                            genSameAadd(reqFid, ou, 0, "NORMAL", sameSegment.get("id"));
                        }
                    }
                }
            }
            // 3.统计流程中的SKU，对应产品号下的所有SKU，汇总出所有小语种下单国别
            Map<String, Integer> ouCount = new HashMap<>(16);
            for (DynamicObject sameSegment : sameSegmentS) {
                Set<String> segmentSet = getItemOrg(sameSegment.get("id"));
                if (segmentSet.size() > 0) {
                    for (String ou : segmentSet) {
                        if (EUORG.contains(ou) && !aosOrg.equals(ou)) {
                            if (FndGlobal.IsNull(ouCount.get(ou))) {
                                ouCount.put(ou, 0);
                            } else {
                                ouCount.put(ou, ouCount.get(ou) + 1);
                            }
                            genSameAadd(reqFid, ou, ouCount.get(ou), "MIN", sameSegment.get("id"));
                        }
                    }
                }
            }
        }
        // 判断高级A+进度表是否存在
        if (ENORG.contains(aosOrg.toString())) {
            DynamicObject aosMktAddtrack = BusinessDataServiceHelper.loadSingleFromCache("aos_mkt_addtrack",
                new QFilter("aos_itemid", QCP.equals, aosItemId.getPkValue()).toArray());
            if (FndGlobal.IsNotNull(aosMktAddtrack)) {
                aosMktAddtrack.set("aos_" + aosOrg, true);
            } else {
                aosMktAddtrack = BusinessDataServiceHelper.newDynamicObject("aos_mkt_addtrack");
                aosMktAddtrack.set("aos_" + aosOrg, true);
                aosMktAddtrack.set("aos_itemid", aosItemId);
            }
            OperationServiceHelper.executeOperate("save", "aos_mkt_addtrack", new DynamicObject[] {aosMktAddtrack},
                OperateOption.create());
        }

        Boolean itemExist = queryItemExist(aosItemId, aosOrg.toString());
        Object aosUser = dyMain.get("aos_monitor");
        if (!itemExist) {
            dyMain.set("aos_user", SYSTEM);
            dyMain.set("aos_status", "已完成");
        } else {
            submitForModel(dyMain);
            FndHistory.Create(dyMain, "提交", "新品对比模块录入");
        }
        dyMain.set("aos_design_date", new Date());
        // 发送消息
        String messageId = aosUser.toString();
        MktComUtil.sendGlobalMessage(messageId, "aos_mkt_aadd", reqFid, aosBillNo, "高级A+需求单-新品对比模块录入");
    }

    /**
     * 海外确认提交
     */
    public static void submitForOs(DynamicObject dyMain) throws FndError {
        // 此时设计师肯定存在
        Object aosUser = dyMain.get("aos_design");
        dyMain.set("aos_status", "设计制作");
        dyMain.set("aos_user", aosUser);
        dyMain.set("aos_auto", "MANUAL");
        DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
        DynamicObject aosItemId = aosEntryentityS.get(0).getDynamicObject("aos_itemid");
        // 国别
        Object aosOrg = dyMain.get("aos_org");
        if (EUORG.contains(aosOrg.toString())) {
            DynamicObject aosMktAddtrack = BusinessDataServiceHelper.loadSingleFromCache("aos_mkt_addtrack",
                new QFilter("aos_itemid", QCP.equals, aosItemId.getPkValue()).toArray());
            if (FndGlobal.IsNotNull(aosMktAddtrack)) {
                aosMktAddtrack.set("aos_" + aosOrg, true);
            } else {
                aosMktAddtrack = BusinessDataServiceHelper.newDynamicObject("aos_mkt_addtrack");
                aosMktAddtrack.set("aos_" + aosOrg, true);
                aosMktAddtrack.set("aos_itemid", aosItemId);
            }
            OperationServiceHelper.executeOperate("save", "aos_mkt_addtrack", new DynamicObject[] {aosMktAddtrack},
                OperateOption.create());
        }
        // 发送消息
        String messageId = aosUser.toString();
        String reqFid = dyMain.getPkValue().toString();
        String aosBillNo = dyMain.getString("aos_billno");
        MktComUtil.sendGlobalMessage(messageId, "aos_mkt_aadd", reqFid, aosBillNo, "高级A+需求单-设计制作");
        // 如果当前为小语种同步主单 则需要同步修改其他子单
        boolean aosMin = dyMain.getBoolean("aos_min");
        Object aosSourceid = dyMain.get("aos_sourceid");
        if (aosMin) {
            DynamicObjectCollection aosMktAaddS = QueryServiceHelper.query("aos_mkt_aadd", "id",
                new QFilter("aos_son", QCP.equals, true).and("aos_org", QCP.equals, dyMain.getString("aos_org"))
                    .and("aos_sourceid", QCP.equals, aosSourceid).toArray());
            for (DynamicObject aosMktAadd : aosMktAaddS) {
                DynamicObject son = BusinessDataServiceHelper.loadSingle(aosMktAadd.get("id"), "aos_mkt_aadd");
                son.set("aos_user", son.get("aos_design"));
                son.set("aos_status", "设计制作");
                OperationServiceHelper.executeOperate("save", "aos_mkt_aadd", new DynamicObject[] {son},
                    OperateOption.create());
            }
        }
    }

    /**
     * 文案确认节点提交
     */
    public static void submitForEdit(DynamicObject dyMain) throws FndError {
        FndError fndError = new FndError();
        DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
        // 物料ID
        Object itemId = aosEntryentityS.get(0).getDynamicObject("aos_itemid").getPkValue();
        Object aosItemId = aosEntryentityS.get(0).getDynamicObject("aos_itemid");
        // 国别
        Object aosOrg = dyMain.get("aos_org");
        dyMain.set("aos_edsubmit", UserServiceHelper.getCurrentUserId());
        dyMain.set("aos_eddate", new Date());
        // 校验是否海外确认必填
        Object aosOsconfirm = dyMain.get("aos_osconfirm");
        if (FndGlobal.IsNull(aosOsconfirm)) {
            fndError.add("是否海外确认必填!");
            throw fndError;
        }
        // 判断高级A+进度表是否存在
        if (EUORG.contains(aosOrg.toString())) {
            DynamicObject aosMktAddtrack = BusinessDataServiceHelper.loadSingleFromCache("aos_mkt_addtrack",
                new QFilter("aos_itemid", QCP.equals, itemId).toArray());
            if (FndGlobal.IsNotNull(aosMktAddtrack)) {
                aosMktAddtrack.set("aos_" + aosOrg, true);
            } else {
                aosMktAddtrack = BusinessDataServiceHelper.newDynamicObject("aos_mkt_addtrack");
                aosMktAddtrack.set("aos_" + aosOrg, true);
                aosMktAddtrack.set("aos_itemid", aosItemId);
            }
            OperationServiceHelper.executeOperate("save", "aos_mkt_addtrack", new DynamicObject[] {aosMktAddtrack},
                OperateOption.create());
        }

        // 根据是否海外确认分类
        if (YES.equals(aosOsconfirm) && ENORG.contains(aosOrg.toString())) {
            Object aosUser = UserServiceHelper.getCurrentUserId();
            dyMain.set("aos_status", "海外确认");
            dyMain.set("aos_osdate", new Date());
            dyMain.set("aos_user", aosUser);
            // 发送消息
            String messageId = aosUser.toString();
            String reqFid = dyMain.getPkValue().toString();
            String aosBillNo = dyMain.getString("aos_billno");
            MktComUtil.sendGlobalMessage(messageId, "aos_mkt_aadd", reqFid, aosBillNo, "高级A+需求单-海外确认");
        } else /* if ("否".equals(aos_osconfirm)) */ {
            Object aosUser = dyMain.get("aos_design");
            dyMain.set("aos_status", "设计制作");
            dyMain.set("aos_user", aosUser);
            // 发送消息
            String messageId = aosUser.toString();
            String reqFid = dyMain.getPkValue().toString();
            String aosBillNo = dyMain.getString("aos_billno");
            MktComUtil.sendGlobalMessage(messageId, "aos_mkt_aadd", reqFid, aosBillNo, "高级A+需求单-设计制作");
            // 如果当前为小语种确认主单 则提交所有小语种同步单据
            boolean aosMin = dyMain.getBoolean("aos_min");
            Object aosSourceid = dyMain.get("aos_sourceid");
            if (aosMin) {
                DynamicObjectCollection aosMktAaddS = QueryServiceHelper.query("aos_mkt_aadd", "id",
                    new QFilter("aos_son", QCP.equals, true).and("aos_sourceid", QCP.equals, aosSourceid)
                        .and("aos_org", QCP.equals, dyMain.getString("aos_org")).toArray());
                for (DynamicObject aosMktAadd : aosMktAaddS) {
                    DynamicObject son = BusinessDataServiceHelper.loadSingle(aosMktAadd.get("id"), "aos_mkt_aadd");
                    son.set("aos_user", son.get("aos_design"));
                    son.set("aos_status", "设计制作");
                    OperationServiceHelper.executeOperate("save", "aos_mkt_aadd", new DynamicObject[] {son},
                        OperateOption.create());
                }
            }
        }
    }

    /**
     * 新建节点提交
     */
    public static void submitForNew(DynamicObject dyMain) throws FndError {
        FndMsg.debug("=====Into submitForNew=====");
        FndError fndError = new FndError();
        // 校验是否至少存在一行SKU
        DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
        if (FndGlobal.IsNull(aosEntryentityS) || aosEntryentityS.size() == 0
            || FndGlobal.IsNull(aosEntryentityS.get(0).get("aos_itemid"))) {
            fndError.add("至少填写一行SKU数据!");
            throw fndError;
        }
        String aosProductno = aosEntryentityS.get(0).getDynamicObject("aos_itemid").getString("aos_productno");
        DynamicObject aosMktAadd = QueryServiceHelper.queryOne("aos_mkt_aadd", "aos_entryentity.aos_itemid",
            new QFilter("aos_entryentity.aos_itemid.aos_productno", QCP.equals, aosProductno)
                .and("id", QCP.not_equals, dyMain.getPkValue().toString()).toArray());
        if (FndGlobal.IsNotNull(aosMktAadd)) {
            throw new FndError(aosProductno + "产品号已有高级A+，不允许重复生成！");
        }
        // 物料ID
        Object itemId = aosEntryentityS.get(0).getDynamicObject("aos_itemid").getPkValue();
        String category = MktComUtil.getItemCateNameZh(itemId);
        String[] categoryGroup = category.split(",");
        // 大类
        String aosCategory1 = null;
        // 中类
        String aosCategory2 = null;
        int categoryLength = categoryGroup.length;
        if (categoryLength > 0) {
            aosCategory1 = categoryGroup[0];
        }
        if (categoryLength > 1) {
            aosCategory2 = categoryGroup[1];
        }
        // 国别
        Object aosOrg = dyMain.get("aos_org");
        // 根据国别+大类+小类 从营销国别品类人员表中取国别
        DynamicObject aosMktProgOrgUser =
            QueryServiceHelper.queryOne("aos_mkt_progorguser", "aos_oueditor,aos_02hq,aos_salehelper,aos_oseditor",
                new QFilter("aos_orgid.number", QCP.equals, aosOrg).and("aos_category1", QCP.equals, aosCategory1)
                    .and("aos_category2", QCP.equals, aosCategory2).toArray());
        if (FndGlobal.IsNull(aosMktProgOrgUser) || FndGlobal.IsNull(aosMktProgOrgUser.get(AOS_OUEDITOR))) {
            fndError.add(aosOrg + "~" + aosCategory1 + "~" + aosCategory2 + ",国别编辑不存在!");
            throw fndError;
        }
        // 带出组长
        if (FndGlobal.IsNull(aosMktProgOrgUser) || FndGlobal.IsNull(aosMktProgOrgUser.get(AOS_02HQ))) {
            fndError.add(aosOrg + "~" + aosCategory1 + "~" + aosCategory2 + ",组长不存在!");
            throw fndError;
        }
        // 带出销售助理
        if (FndGlobal.IsNull(aosMktProgOrgUser) || FndGlobal.IsNull(aosMktProgOrgUser.get(AOS_SALEHELPER))) {
            fndError.add(aosOrg + "~" + aosCategory1 + "~" + aosCategory2 + ",销售助理不存在!");
            throw fndError;
        }
        // 带出海外编辑
        if (FndGlobal.IsNull(aosMktProgOrgUser) || FndGlobal.IsNull(aosMktProgOrgUser.get(AOS_OSEDITOR))) {
            fndError.add(aosOrg + "~" + aosCategory1 + "~" + aosCategory2 + ",海外编辑不存在!");
            throw fndError;
        }
        // 若未填写设计师则按照逻辑带出设计师
        // 表头设计师
        Object aosDesign = dyMain.get("aos_design");
        // 任务类型
        Object aosType = dyMain.get("aos_type");
        if (FndGlobal.IsNull(aosDesign)) {
            if (NEWTYPE.equals(aosType)) {
                DynamicObject aosMktProgUser = QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_designer",
                    new QFilter("aos_category1", QCP.equals, aosCategory1)
                        .and("aos_category2", QCP.equals, aosCategory2).toArray());
                if (FndGlobal.IsNull(aosMktProgUser) || FndGlobal.IsNull(aosMktProgUser.get(AOS_DESIGNER))) {
                    fndError.add(aosCategory1 + "~" + aosCategory2 + ",品类设计不存在!");
                    throw fndError;
                }
                dyMain.set("aos_design", aosMktProgUser.get("aos_designer"));
            } else {
                DynamicObject aosMktProgUser = QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_designassit",
                    new QFilter("aos_category1", QCP.equals, aosCategory1)
                        .and("aos_category2", QCP.equals, aosCategory2).toArray());
                if (FndGlobal.IsNull(aosMktProgUser) || FndGlobal.IsNull(aosMktProgUser.get(AOS_DESIGNASSIT))) {
                    fndError.add(aosCategory1 + "~" + aosCategory2 + ",初级品类不存在!");
                    throw fndError;
                }
                dyMain.set("aos_design", aosMktProgUser.get("aos_designassit"));
            }
        }
        Object aosUser;
        if (EUORG.contains(aosOrg.toString())) {
            aosUser = aosMktProgOrgUser.get("aos_oseditor");
            dyMain.set("aos_user", aosUser);
            dyMain.set("aos_status", "海外确认");
            dyMain.set("aos_osdate", new Date());
        } else {
            aosUser = aosMktProgOrgUser.get("aos_oueditor");
            dyMain.set("aos_user", aosUser);
            dyMain.set("aos_status", "文案确认");
        }
        dyMain.set("aos_oueditor", aosMktProgOrgUser.get("aos_oueditor"));
        dyMain.set("aos_oseditor", aosMktProgOrgUser.get("aos_oseditor"));
        Object aos02hq = aosMktProgOrgUser.get("aos_02hq");
        Object aosSalehelper = aosMktProgOrgUser.get("aos_salehelper");
        dyMain.set("aos_monitor", aos02hq);
        dyMain.set("aos_sale", aosSalehelper);
        // 发送消息
        String messageId = aosUser.toString();
        String reqFid = dyMain.getPkValue().toString();
        String aosBillNo = dyMain.getString("aos_billno");
        MktComUtil.sendGlobalMessage(messageId, "aos_mkt_aadd", reqFid, aosBillNo, "高级A+需求单-文案确认");
    }

    /**
     * 获取下单国别
     */
    public static Set<String> getItemOrg(Object aosItemId) {
        Set<String> ouSet = new HashSet<>();
        DynamicObject bdMaterial = BusinessDataServiceHelper.loadSingle(aosItemId, "bd_material");
        DynamicObjectCollection aosContryentryS = bdMaterial.getDynamicObjectCollection("aos_contryentry");
        // 获取所有国家品牌 字符串拼接 终止
        for (DynamicObject aosContryentry : aosContryentryS) {
            // 物料国别
            DynamicObject aosNationality = aosContryentry.getDynamicObject("aos_nationality");
            // 物料国别编码
            String aosNationalitynumber = aosNationality.getString("number");
            if (IE.equals(aosNationalitynumber)) {
                continue;
            }
            Object orgId = aosNationality.get("id");
            int osQty = ItemInfoUtil.getItemOsQty(orgId, aosItemId);
            int onQty = getOnHandQty(Long.parseLong(orgId.toString()), Long.parseLong(aosItemId.toString()));
            osQty += onQty;
            int safeQty = ItemInfoUtil.getSafeQty(orgId);
            String aosContryentrystatus = aosContryentry.getString("aos_contryentrystatus");
            if ("F".equals(aosContryentrystatus) || "H".equals(aosContryentrystatus)) {
                continue;
            }
            if ("C".equals(aosContryentry.getString("aos_contryentrystatus")) && osQty < safeQty) {
                continue;
            }
            ouSet.add(aosNationalitynumber);
        }
        return ouSet;
    }

    /**
     * 获取在途数量
     */
    public static int getOnHandQty(long fkAosOrgFid, long fkAosItemFid) {
        try {
            QFilter filterItem = new QFilter("aos_itemid", "=", fkAosItemFid);
            QFilter filterOu = new QFilter("aos_orgid", "=", fkAosOrgFid);
            QFilter filterQty = new QFilter("aos_ship_qty", ">", 0);
            QFilter filterFlag = new QFilter("aos_os_flag", "=", "N");
            QFilter filterStatus = new QFilter("aos_shp_status", "=", "PACKING");
            filterStatus = filterStatus.or("aos_shp_status", "=", "SHIPPING");
            QFilter[] filters = new QFilter[] {filterItem, filterOu, filterQty, filterFlag, filterStatus};
            DynamicObjectCollection aosSyncShp = QueryServiceHelper.query("aos_sync_shp", "aos_ship_qty", filters);
            int aosShipQty = 0;
            for (DynamicObject d : aosSyncShp) {
                aosShipQty += d.getInt("aos_ship_qty");
            }
            return aosShipQty;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        }
    }

    /**
     * 生成同款高级A+需求单
     */
    public static void genSameAadd(String reqFid, String ou, int count, String type, Object itemId) throws FndError {
        FndError fndError = new FndError();
        DynamicObject sourceBill = BusinessDataServiceHelper.loadSingle(reqFid, "aos_mkt_aadd");
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
        DynamicObject aosMktAadd = BusinessDataServiceHelper.newDynamicObject("aos_mkt_aadd");
        aosMktAadd.set("aos_requireby", sourceBill.get("aos_requireby"));
        aosMktAadd.set("aos_organization1", sourceBill.get("aos_organization1"));
        aosMktAadd.set("aos_requiredate", sourceBill.get("aos_requiredate"));
        aosMktAadd.set("aos_type", "同款");
        aosMktAadd.set("aos_org", ou);
        DynamicObject aosMktProgOrgUser =
            QueryServiceHelper.queryOne("aos_mkt_progorguser", "aos_oueditor,aos_02hq,aos_salehelper,aos_oseditor",
                new QFilter("aos_orgid.number", QCP.equals, ou).and("aos_category1", QCP.equals, aosCategory1)
                    .and("aos_category2", QCP.equals, aosCategory2).toArray());
        if (FndGlobal.IsNull(aosMktProgOrgUser) || FndGlobal.IsNull(aosMktProgOrgUser.get(AOS_OUEDITOR))) {
            fndError.add(ou + "~" + aosCategory1 + "~" + aosCategory2 + ",国别编辑不存在!");
            throw fndError;
        }
        // 带出组长
        if (FndGlobal.IsNull(aosMktProgOrgUser) || FndGlobal.IsNull(aosMktProgOrgUser.get(AOS_02HQ))) {
            fndError.add(ou + "~" + aosCategory1 + "~" + aosCategory2 + ",组长不存在!");
            throw fndError;
        }
        // 带出销售助理
        if (FndGlobal.IsNull(aosMktProgOrgUser) || FndGlobal.IsNull(aosMktProgOrgUser.get(AOS_SALEHELPER))) {
            fndError.add(ou + "~" + aosCategory1 + "~" + aosCategory2 + ",销售助理不存在!");
            throw fndError;
        }
        // 带出海外编辑
        if (FndGlobal.IsNull(aosMktProgOrgUser) || FndGlobal.IsNull(aosMktProgOrgUser.get(AOS_OSEDITOR))) {
            fndError.add(ou + "~" + aosCategory1 + "~" + aosCategory2 + ",海外编辑不存在!");
            throw fndError;
        }
        aosMktAadd.set("aos_osconfirm", sourceBill.get("aos_osconfirm"));
        aosMktAadd.set("aos_oueditor", aosMktProgOrgUser.get("aos_oueditor"));
        aosMktAadd.set("aos_monitor", aosMktProgOrgUser.get("aos_02hq"));
        aosMktAadd.set("aos_design", sourceBill.get("aos_design"));
        aosMktAadd.set("aos_sale", aosMktProgOrgUser.get("aos_salehelper"));
        aosMktAadd.set("aos_sourceid", sourceBill.get("id"));
        aosMktAadd.set("aos_sourcebillno", sourceBill.get("aos_billno"));
        DynamicObjectCollection sourceEntryS = sourceBill.getDynamicObjectCollection("aos_entryentity");
        DynamicObject sourceEntry = sourceEntryS.get(0);
        DynamicObjectCollection aosEntryentityS = aosMktAadd.getDynamicObjectCollection("aos_entryentity");
        DynamicObject aosEntryentity = aosEntryentityS.addNew();
        aosEntryentity.set("aos_itemid", itemId);
        aosEntryentity.set("aos_aadd", sourceEntry.get("aos_aadd"));
        aosEntryentity.set("aos_new", sourceEntry.get("aos_new"));
        aosEntryentity.set("aos_same", sourceEntry.get("aos_same"));
        aosEntryentity.set("aos_trans", sourceEntry.get("aos_trans"));
        aosEntryentity.set("aos_url", sourceEntry.get("aos_url"));
        if (count == 0) {
            if (MIN.equals(type)) {
                // 小语种同步主单
                aosMktAadd.set("aos_min", true);
                aosMktAadd.set("aos_status", "海外确认");
                aosMktAadd.set("aos_osdate", new Date());
                aosMktAadd.set("aos_user", aosMktProgOrgUser.get("aos_oseditor"));
                aosMktAadd.set("aos_oseditor", aosMktProgOrgUser.get("aos_oseditor"));
                aosMktAadd.set("aos_oueditor", aosMktProgOrgUser.get("aos_oueditor"));
            } else if (NORMAL.equals(type)) {
                aosMktAadd.set("aos_status", "设计制作");
                aosMktAadd.set("aos_user", sourceBill.get("aos_design"));
            }
        } else {
            if (MIN.equals(type)) {
                // 小语种同步子单
                aosMktAadd.set("aos_son", true);
                aosMktAadd.set("aos_status", "小语种同步");
                aosMktAadd.set("aos_user", SYSTEM);
            }
        }
        OperationServiceHelper.executeOperate("save", "aos_mkt_aadd", new DynamicObject[] {aosMktAadd},
            OperateOption.create());
    }

    public static void generateAddFromDesign(DynamicObject aosItemid, String ou, String type) throws FndError {
        FndError fndError = new FndError();
        // 申请人
        DynamicObject aosMktAaddOne = QueryServiceHelper.queryOne("aos_mkt_aadd", "aos_requireby,aos_organization1",
            new QFilter("aos_itemid.aos_productno", QCP.equals, aosItemid.getString("aos_productno")).toArray());
        DynamicObject aosMktAadd = BusinessDataServiceHelper.newDynamicObject("aos_mkt_aadd");
        aosMktAadd.set("aos_requireby", aosMktAaddOne.get("aos_requireby"));
        aosMktAadd.set("aos_organization1", aosMktAaddOne.get("aos_organization1"));
        aosMktAadd.set("aos_requiredate", new Date());
        aosMktAadd.set("aos_type", "国别新品");
        aosMktAadd.set("aos_org", ou);
        aosMktAadd.set("aos_osconfirm", "是");
        String category = MktComUtil.getItemCateNameZh(aosItemid.getPkValue());
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
        DynamicObject aosMktProgUser = QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_designassit",
            new QFilter("aos_category1", QCP.equals, aosCategory1).and("aos_category2", QCP.equals, aosCategory2)
                .toArray());
        if (FndGlobal.IsNull(aosMktProgUser) || FndGlobal.IsNull(aosMktProgUser.get(AOS_DESIGNASSIT))) {
            fndError.add(aosCategory1 + "~" + aosCategory2 + ",初级品类不存在!");
            throw fndError;
        }
        DynamicObject aosMktProgOrgUser =
            QueryServiceHelper.queryOne("aos_mkt_progorguser", "aos_oueditor,aos_02hq,aos_salehelper,aos_oseditor",
                new QFilter("aos_orgid.number", QCP.equals, ou).and("aos_category1", QCP.equals, aosCategory1)
                    .and("aos_category2", QCP.equals, aosCategory2).toArray());
        if (FndGlobal.IsNull(aosMktProgOrgUser) || FndGlobal.IsNull(aosMktProgOrgUser.get(AOS_OUEDITOR))) {
            fndError.add(ou + "~" + aosCategory1 + "~" + aosCategory2 + ",国别编辑不存在!");
            throw fndError;
        }
        aosMktAadd.set("aos_design", aosMktProgUser.get("aos_designassit"));
        aosMktAadd.set("aos_oueditor", aosMktProgOrgUser.get("aos_oueditor"));
        aosMktAadd.set("aos_monitor", aosMktProgOrgUser.get("aos_02hq"));
        aosMktAadd.set("aos_sale", aosMktProgOrgUser.get("aos_salehelper"));
        if (EN_05.equals(type)) {
            // 判断国别物料是否存在
            Boolean itemExist = queryItemExist(aosItemid, ou);
            if (!itemExist) {
                aosMktAadd.set("aos_user", SYSTEM);
                aosMktAadd.set("aos_status", "已完成");
            } else {
                aosMktAadd.set("aos_user", aosMktProgOrgUser.get("aos_02hq"));
                aosMktAadd.set("aos_status", "新品对比模块录入");
            }
            // 将对应A+需求表对应国别勾选
            DynamicObject aosMktAddtrack = BusinessDataServiceHelper.loadSingleFromCache("aos_mkt_addtrack",
                new QFilter("aos_itemid", QCP.equals, aosItemid.getPkValue()).toArray());
            if (FndGlobal.IsNotNull(aosMktAddtrack)) {
                aosMktAddtrack.set("aos_" + ou, true);
            } else {
                aosMktAddtrack = BusinessDataServiceHelper.newDynamicObject("aos_mkt_addtrack");
                aosMktAddtrack.set("aos_" + ou, true);
                aosMktAddtrack.set("aos_itemid", aosItemid);
            }
            OperationServiceHelper.executeOperate("save", "aos_mkt_addtrack", new DynamicObject[] {aosMktAddtrack},
                OperateOption.create());
        } else if (EN_04.equals(type)) {
            aosMktAadd.set("aos_user", aosMktProgUser.get("aos_designassit"));
            aosMktAadd.set("aos_status", "设计制作");
        } else if (SM_04.equals(type)) {
            aosMktAadd.set("aos_user", aosMktProgUser.get("aos_designassit"));
            aosMktAadd.set("aos_status", "设计制作");
            // 将对应A+需求表对应国别勾选
            DynamicObject aosMktAddtrack = BusinessDataServiceHelper.loadSingleFromCache("aos_mkt_addtrack",
                new QFilter("aos_itemid", QCP.equals, aosItemid.getPkValue()).toArray());
            if (FndGlobal.IsNotNull(aosMktAddtrack)) {
                aosMktAddtrack.set("aos_" + ou, true);
            } else {
                aosMktAddtrack = BusinessDataServiceHelper.newDynamicObject("aos_mkt_addtrack");
                aosMktAddtrack.set("aos_" + ou, true);
                aosMktAddtrack.set("aos_itemid", aosItemid);
            }
            OperationServiceHelper.executeOperate("save", "aos_mkt_addtrack", new DynamicObject[] {aosMktAddtrack},
                OperateOption.create());
        } else if (SM_02.equals(type)) {
            aosMktAadd.set("aos_oseditor", aosMktProgOrgUser.get("aos_oseditor"));
            aosMktAadd.set("aos_user", aosMktProgOrgUser.get("aos_oseditor"));
            aosMktAadd.set("aos_status", "海外确认");
            aosMktAadd.set("aos_osdate", new Date());
        }
        DynamicObjectCollection aosEntryentityS = aosMktAadd.getDynamicObjectCollection("aos_entryentity");
        DynamicObject aosEntryentity = aosEntryentityS.addNew();
        aosEntryentity.set("aos_itemid", aosItemid);
        OperationResult result = OperationServiceHelper.executeOperate("save", "aos_mkt_aadd",
            new DynamicObject[] {aosMktAadd}, OperateOption.create());
        // 2023-11-16 gk 单据到达新品确认节点后立马提交
        if (result.isSuccess()) {
            String status = aosMktAadd.getString("aos_status");
            if (FndGlobal.IsNotNull(status) && NEWMODEL.equals(status)) {
                Object id = result.getSuccessPkIds().get(0);
                DynamicObject dyMain = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_aadd");
                submitForModel(dyMain);
                FndHistory.Create(dyMain, "提交", "新品对比模块录入");
                SaveServiceHelper.save(new DynamicObject[] {dyMain});
            }
        }
    }

    public static Boolean queryItemExist(DynamicObject aosItemid, String ou) {
        try {
            DynamicObject bdMaterial = QueryServiceHelper.queryOne("bd_material", "id",
                new QFilter("id", QCP.equals, aosItemid.getPkValue().toString())
                    .and("aos_contryentry.aos_nationality.number", QCP.equals, ou)
                    .and("aos_contryentry.aos_contryentrystatus", QCP.not_equals, "F")
                    .and("aos_contryentry.aos_contryentrystatus", QCP.not_equals, "H").toArray());
            return FndGlobal.IsNotNull(bdMaterial);
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        EntryGrid entryGrid = this.getControl("aos_entryentity");
        entryGrid.addHyperClickListener(this);
    }

    @Override
    public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
        int rowIndex = hyperLinkClickEvent.getRowIndex();
        String fieldName = hyperLinkClickEvent.getFieldName();
        if (AOS_URL.equals(fieldName)) {
            getView().openUrl(this.getModel().getValue(fieldName, rowIndex).toString());
        } else if (AOS_PRODUCTNO.equals(fieldName)) {
            // 根据状态判断当前流程节点
            DynamicObject aosItemid = (DynamicObject)this.getModel().getValue("aos_itemid", 0);
            DynamicObject aosAaddModel = QueryServiceHelper.queryOne("aos_aadd_model", "id",
                new QFilter("aos_productno", QCP.equals, aosItemid.getString("aos_productno")).toArray());
            if (FndGlobal.IsNotNull(aosAaddModel)) {
                FndGlobal.OpenBillById(getView(), "aos_aadd_model", aosAaddModel.get("id"));
            }
        }
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        FndMsg.debug("name =" + name);
        if (AOS_TYPE.equals(name)) {
            // 任务类型
            aosTypeChange();
        } else if (AOS_ITEMID.equals(name)) {
            aosItemIdChange();
        }
    }

    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        statusControl();
        init();
    }

    /**
     * 初始化事件
     **/
    @Override
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        statusControl();
        aosItemIdChange();
    }

    @Override
    public void beforeDoOperation(BeforeDoOperationEventArgs args) {
        FormOperate formOperate = (FormOperate)args.getSource();
        String operatation = formOperate.getOperateKey();
        try {
            if (SAVE.equals(operatation)) {
                saveControl();
            }
            statusControl();
        } catch (FndError fndError) {
            fndError.show(getView());
            args.setCancel(true);
        } catch (Exception ex) {
            FndError.showex(getView(), ex);
            args.setCancel(true);
        }
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        try {
            if (AOS_SUBMIT.equals(control)) {
                aosSubmit(this.getModel().getDataEntity(true), "A");
            } else if (AOS_HISTORY.equals(control)) {
                // 查看历史记录
                aosHistory();
            }
        } catch (FndError fndError) {
            fndError.show(getView());
        } catch (Exception ex) {
            FndError.showex(getView(), ex);
        }
    }

    /**
     * 查看历史记录
     */
    private void aosHistory() {
        FndHistory.OpenHistory(this.getView());
    }

    /**
     * 提交按钮逻辑
     */
    public void aosSubmit(DynamicObject dyMain, String type) throws FndError {
        // 手工提交时先保存数据
        if (A.equals(type)) {
            this.getView().invokeOperation("save");
            this.getView().invokeOperation("refresh");
            statusControl();// 提交完成后做新的界面状态控制
        }
        FndMsg.debug("=====Into aosSubmit=====");
        String aosStatus = dyMain.getString("aos_status");
        switch (aosStatus) {
            case "新建":
                submitForNew(dyMain);
                break;
            case "文案确认":
                submitForEdit(dyMain);
                break;
            case "海外确认":
                submitForOs(dyMain);
                break;
            case "设计制作":
                submitForDesign(dyMain);
                break;
            case "新品对比模块录入":
                submitForModel(dyMain);
                break;
            case "销售上线":
                submitForSale(dyMain);
                break;
            default:
                break;
        }
        SaveServiceHelper.saveOperate("aos_mkt_aadd", new DynamicObject[] {dyMain}, OperateOption.create());
        FndHistory.Create(dyMain, "提交", aosStatus);
        if (A.equals(type)) {
            this.getView().invokeOperation("refresh");
            // 提交完成后做新的界面状态控制
            statusControl();
        }
    }

    /**
     * 物料值改变事件
     */
    private void aosItemIdChange() {
        FndMsg.debug("=====aosItemIdChange=====");
        int currentRow = 0;
        Object aosItemid = this.getModel().getValue("aos_itemid", currentRow);
        Label aosAttr1 = this.getView().getControl("aos_attr1");
        Label aosSpec = this.getView().getControl("aos_spec");
        Label aosBrand = this.getView().getControl("aos_brand");
        Object aosOrg = this.getModel().getValue("aos_org");
        Image aosImageap = this.getControl("aos_imageap");
        if (FndGlobal.IsNull(aosItemid)) {
            aosAttr1.setText(null);
            aosSpec.setText(null);
            aosBrand.setText(null);
            this.getModel().setValue("aos_url", null);
            aosImageap.setUrl(null);
        } else {
            DynamicObject aosItemidObject = (DynamicObject)aosItemid;
            DynamicObject bdMaterial =
                BusinessDataServiceHelper.loadSingle(aosItemidObject.getPkValue(), "bd_material");
            // 明细面板
            String category = MktComUtil.getItemCateNameZh(aosItemidObject.getPkValue());
            String[] categoryGroup = category.split(",");
            String aosCategory1 = null;
            // \\192.168.70.61\Marketing_Files图片库\A+上架资料\设计高级版A+\大类\品名-产品号
            if (categoryGroup.length > 0) {
                aosCategory1 = categoryGroup[0];
            }
            String aosUrl = "\\\\192.168.70.61\\Marketing_Files\\图片库\\A+上架资料\\设计高级版A+\\" + aosCategory1 + "\\"
                + bdMaterial.getString("name") + "-" + bdMaterial.getString("aos_productno");
            this.getModel().setValue("aos_url", aosUrl);
            // 货号信息面板
            aosImageap.setUrl("https://cls3.s3.amazonaws.com/" + bdMaterial.getString("number") + "/1-1.jpg");
            aosAttr1.setText("产品属性1\n" + bdMaterial.getString("aos_seting_cn"));
            aosSpec.setText("产品规格\n" + bdMaterial.getString("aos_specification_cn"));
            if (FndGlobal.IsNotNull(aosOrg)) {
                DynamicObjectCollection aosContryentryS = bdMaterial.getDynamicObjectCollection("aos_contryentry");
                for (DynamicObject aosContryentry : aosContryentryS) {
                    DynamicObject aosNationality = aosContryentry.getDynamicObject("aos_nationality");
                    String aosNationalitynumber = aosNationality.getString("number");
                    if (!aosOrg.equals(aosNationalitynumber)) {
                        continue;
                    }
                    aosBrand.setText("品牌\n" + aosContryentry.getDynamicObject("aos_contrybrand").getString("number"));
                }
            }
        }
    }

    /**
     * 任务类型值改变事件
     */
    private void aosTypeChange() {
        Object aosType = getModel().getValue("aos_type");
        ComboEdit aosOrg = getControl("aos_org");
        List<ComboItem> data = new ArrayList<>();
        if (NEWTYPE.equals(aosType)) {
            data.add(new ComboItem(new LocaleString("US"), "US"));
            data.add(new ComboItem(new LocaleString("UK"), "UK"));
        } else {
            data.add(new ComboItem(new LocaleString("US"), "US"));
            data.add(new ComboItem(new LocaleString("CA"), "CA"));
            data.add(new ComboItem(new LocaleString("UK"), "UK"));
            data.add(new ComboItem(new LocaleString("DE"), "DE"));
            data.add(new ComboItem(new LocaleString("FR"), "FR"));
            data.add(new ComboItem(new LocaleString("IT"), "IT"));
            data.add(new ComboItem(new LocaleString("ES"), "ES"));
        }
        aosOrg.setComboItems(data);
    }

    /**
     * 初始化数据
     */
    private void init() {
        try {
            // 带出人员部门
            long currentUserId = UserServiceHelper.getCurrentUserId();
            List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(currentUserId);
            if (mapList != null) {
                if (mapList.get(TWO) != null) {
                    this.getModel().setValue("aos_organization1", mapList.get(2).get("id"));
                }
            }
            // 设置国别初始下拉为空
            aosTypeChange();
        } catch (Exception ex) {
            FndError.showex(getView(), ex);
        }
    }

    /**
     * 保存校验
     *
     */
    private void saveControl() throws FndError {
        FndError fndError = new FndError();
        Object aosType = this.getModel().getValue("aos_type");
        Object aosOrg = this.getModel().getValue("aos_org");
        Object aosStatus = this.getModel().getValue("aos_status");
        Object fid = this.getModel().getDataEntity().getPkValue();
        // 对于新建节点
        if (NEW.equals(aosStatus)) {
            if (FndGlobal.IsNull(aosType)) {
                fndError.addCount();
                fndError.add("任务类型必填!");
            }
            if (FndGlobal.IsNull(aosOrg)) {
                fndError.addCount();
                fndError.add("国别必填!");
            }
            // 校验是否至少存在一行SKU
            Object aosItemId = this.getModel().getValue("aos_itemid", 0);
            if (FndGlobal.IsNull(aosItemId)) {
                fndError.add("至少填写一行SKU数据!");
                throw fndError;
            }
            String aosProductno = ((DynamicObject)aosItemId).getString("aos_productno");
            // 判断产品号是否已存在
            DynamicObject aosMktAadd = QueryServiceHelper.queryOne("aos_mkt_aadd", "aos_entryentity.aos_itemid",
                new QFilter("aos_entryentity.aos_itemid.aos_productno", QCP.equals, aosProductno)
                    .and("id", QCP.not_equals, fid).toArray());
            if (FndGlobal.IsNotNull(aosMktAadd)) {
                fndError.add(aosProductno + "产品号已有高级A+，不允许重复生成！");
                throw fndError;
            }
        }
        if (fndError.getCount() > 0) {
            throw fndError;
        }
    }

    /**
     * 状态控制
     */
    private void statusControl() throws FndError {
        // 数据层
        Object aosUser = this.getModel().getValue("aos_user");
        Object aosStatus = this.getModel().getValue("aos_status");
        Object currentUserId = UserServiceHelper.getCurrentUserId();
        Object currentUserName = UserServiceHelper.getUserInfoByID((long)currentUserId).get("name");
        if (aosUser instanceof DynamicObject) {
            aosUser = ((DynamicObject)aosUser).getPkValue();
        }
        // 权限控制
        if (!aosUser.toString().equals(currentUserId.toString()) && !CZJ.equals(currentUserName.toString())
            && !"刘中怀".equals(currentUserName.toString()) && !"赵轩".equals(currentUserName.toString())) {
            // 标题面板
            this.getView().setEnable(false, "titlepanel");
            // 主界面面板
            this.getView().setEnable(false, "aos_contentpanelflex");
            // 保存
            this.getView().setVisible(false, "bar_save");
            // 提交
            this.getView().setVisible(false, "aos_submit");
        }
        // 状态控制
        EntryGrid entryGrid = this.getControl("aos_entryentity");
        entryGrid.setColumnProperty("aos_contrast", ClientProperties.Lock, true);
        entryGrid.setColumnProperty("aos_desc", ClientProperties.Lock, true);

        if (NEW.equals(aosStatus)) {
            // 是否海外确认
            this.getView().setEnable(false, "aos_osconfirm");
            entryGrid.setColumnProperty("aos_aadd", ClientProperties.Lock, true);
            entryGrid.setColumnProperty("aos_new", ClientProperties.Lock, true);
            entryGrid.setColumnProperty("aos_same", ClientProperties.Lock, true);
            entryGrid.setColumnProperty("aos_trans", ClientProperties.Lock, true);
        } else if (EDCONFIRM.equals(aosStatus)) {
            // 是否海外确认
            this.getView().setEnable(true, "aos_osconfirm");
            // 任务类型
            this.getView().setEnable(false, "aos_type");
            // 国别
            this.getView().setEnable(false, "aos_org");
            // 设计师
            this.getView().setEnable(false, "aos_design");
            // 销售
            this.getView().setEnable(false, "aos_sale");
            // 国别编辑
            this.getView().setEnable(false, "aos_oueditor");
            // 新款
            this.getView().setEnable(false, 0, "aos_new");
            // 同款
            this.getView().setEnable(false, 0, "aos_same");
            // 翻译
            this.getView().setEnable(false, 0, "aos_trans");
            // 货号
            this.getView().setEnable(false, 0, "aos_itemid");
        } else if (OSCONFIRM.equals(aosStatus)) {
            // 是否海外确认
            this.getView().setEnable(false, "aos_osconfirm");
            // 任务类型
            this.getView().setEnable(false, "aos_type");
            // 国别
            this.getView().setEnable(false, "aos_org");
            // 设计师
            this.getView().setEnable(false, "aos_design");
            // 销售
            this.getView().setEnable(false, "aos_sale");
            // 国别编辑
            this.getView().setEnable(false, "aos_oueditor");
            // 高级A+文案
            this.getView().setEnable(false, 0, "aos_aadd");
            // 新款
            this.getView().setEnable(false, 0, "aos_new");
            // 同款
            this.getView().setEnable(false, 0, "aos_same");
            // 翻译
            this.getView().setEnable(false, 0, "aos_trans");
            // 货号
            this.getView().setEnable(false, 0, "aos_itemid");
        } else if (DESIGN.equals(aosStatus)) {
            // 是否海外确认
            this.getView().setEnable(false, "aos_osconfirm");
            // 任务类型
            this.getView().setEnable(false, "aos_type");
            // 国别
            this.getView().setEnable(false, "aos_org");
            // 设计师
            this.getView().setEnable(false, "aos_design");
            // 销售
            this.getView().setEnable(false, "aos_sale");
            // 国别编辑
            this.getView().setEnable(false, "aos_oueditor");
            // 高级A+文案
            this.getView().setEnable(false, 0, "aos_aadd");
            // 新款
            this.getView().setEnable(true, 0, "aos_new");
            // 同款
            this.getView().setEnable(true, 0, "aos_same");
            // 翻译
            this.getView().setEnable(true, 0, "aos_trans");
            // 货号
            this.getView().setEnable(false, 0, "aos_itemid");
            // 备注
            this.getView().setEnable(true, 0, "aos_desc");
        } else if (NEWMODEL.equals(aosStatus)) {
            // 保存
            this.getView().setVisible(false, "bar_save");
            // 是否海外确认
            this.getView().setEnable(false, "aos_osconfirm");
            // 任务类型
            this.getView().setEnable(false, "aos_type");
            // 国别
            this.getView().setEnable(false, "aos_org");
            // 设计师
            this.getView().setEnable(false, "aos_design");
            // 销售
            this.getView().setEnable(false, "aos_sale");
            // 国别编辑
            this.getView().setEnable(false, "aos_oueditor");
            // 高级A+文案
            this.getView().setEnable(false, 0, "aos_aadd");
            // 新款
            this.getView().setEnable(false, 0, "aos_new");
            // 同款
            this.getView().setEnable(false, 0, "aos_same");
            // 翻译
            this.getView().setEnable(false, 0, "aos_trans");
            // 货号
            this.getView().setEnable(false, 0, "aos_itemid");
            entryGrid.setColumnProperty("aos_contrast", ClientProperties.Lock, false);
        } else if (SALEONLINE.equals(aosStatus)) {
            // 保存
            this.getView().setVisible(false, "bar_save");
            // 是否海外确认
            this.getView().setEnable(false, "aos_osconfirm");
            // 任务类型
            this.getView().setEnable(false, "aos_type");
            // 国别
            this.getView().setEnable(false, "aos_org");
            // 设计师
            this.getView().setEnable(false, "aos_design");
            // 销售
            this.getView().setEnable(false, "aos_sale");
            // 国别编辑
            this.getView().setEnable(false, "aos_oueditor");
            // 高级A+文案
            this.getView().setEnable(false, 0, "aos_aadd");
            // 新款
            this.getView().setEnable(false, 0, "aos_new");
            // 同款
            this.getView().setEnable(false, 0, "aos_same");
            // 翻译
            this.getView().setEnable(false, 0, "aos_trans");
            // 货号
            this.getView().setEnable(false, 0, "aos_itemid");
        }
    }
}
