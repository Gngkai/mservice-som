package mkt.progress.listing;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.*;
import common.sal.sys.basedata.dao.ItemCategoryDao;
import common.sal.sys.basedata.dao.impl.ItemCategoryDaoImpl;
import common.sal.util.SalUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.entity.EntityMetadataCache;
import kd.bos.entity.datamodel.events.ImportDataEventArgs;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.ConfirmCallBackListener;
import kd.bos.form.MessageBoxOptions;
import kd.bos.form.MessageBoxResult;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.events.*;
import kd.bos.form.operate.FormOperate;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;
import mkt.common.MKTS3PIC;
import mkt.common.otel.MmsOtelUtils;
import mkt.progress.ProgressUtil;
import mkt.progress.design.AosMktDesignReqBill;
import mkt.progress.iface.ItemInfoUtil;

/**
 * @author aosom
 * @version Listing优化需求表-表单插件
 */
public class AosMktListingReqBill extends AbstractBillPlugIn
    implements ItemClickListener, HyperLinkClickListener, RowClickEventListener {
    /**
     * 系统管理员
     **/
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    private static final Tracer TRACER = MmsOtelUtils.getTracer(AosMktListingReqBill.class, RequestContext.get());
    private final static Log log = LogFactory.getLog("ProductAdjustPriceBill");

    /**
     * 值校验
     **/
    private static void saveControl(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
            int errorCount = 0;
            String errorMessage = "";
            // 数据层
            Object aosType = dyMain.get("aos_type");
            // 校验
            if (Cux_Common_Utl.IsNull(aosType)) {
                errorCount++;
                errorMessage = FndError.AddErrorMessage(errorMessage, "任务类型必填!");
            }
            if (errorCount > 0) {
                throw new FndError(errorMessage);
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 编辑确认状态下提交
     **/
    private static void submitForNew(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());

        try (Scope ignored = span.makeCurrent()) {
            // 异常参数
            FndReturn retrun = new FndReturn();
            // 数据层
            // 校验
            if (retrun.GetErrorCount() > 0) {
                throw new FndError(retrun);
            }
            // 循环每一行
            DynamicObjectCollection entityS = dyMain.getDynamicObjectCollection("aos_entryentity");
            List<DynamicObject> listRequire = new ArrayList<>();
            List<DynamicObject> listRequirePic = new ArrayList<>();
            for (DynamicObject entity : entityS) {
                Object aosRequire = entity.get("aos_require");
                Object aosRequirepic = entity.get("aos_requirepic");
                if (!Cux_Common_Utl.IsNull(aosRequire)) {
                    listRequire.add(entity);
                }
                if (!Cux_Common_Utl.IsNull(aosRequirepic)) {
                    listRequirePic.add(entity);
                }
            }

            if (listRequire.size() > 0) {
                generateListingSon(listRequire, retrun, dyMain);
                if (retrun.GetErrorCount() > 0) {
                    throw new FndError("文案需求生成Listing优化子表失败!");
                }
            }

            if (listRequirePic.size() > 0) {
                generateDesignReq(listRequirePic, retrun, dyMain);
                if (retrun.GetErrorCount() > 0) {
                    retrun.AddErrorMessage("图片需求生成设计需求表失败!");
                    throw new FndError(retrun);
                }
            }
            dyMain.set("aos_status", "已完成");
            dyMain.set("aos_user", SYSTEM);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 文案需求生成Listing优化子表
     *
     **/
    public static void generateListingSon(List<DynamicObject> listRequire, FndReturn fndReturn, DynamicObject dyMain)
        throws FndError {
        // 异常处理
        fndReturn.clear();
        // 信息处理
        boolean messageFlag = false;
        String messageId;
        String message;
        // 数据层
        Object aosDesigner = dyMain.get("aos_designer");
        Object billno = dyMain.get("billno");
        Object reqId = dyMain.get("id");
        Object aosType = dyMain.get("aos_type");
        Object aosSource = dyMain.get("aos_source");
        Object aosImportance = dyMain.get("aos_importance");
        Object aosEditor = dyMain.get("aos_editor");
        Object aosDemandate = dyMain.get("aos_demandate");
        Object aosIsSmallLov;
        Object aosOrgid = dyMain.get("aos_orgid");
        Object aosRequireby = dyMain.get("aos_requireby");
        Object aosProductbill = dyMain.get("aos_productbill");
        Object aosProductid = dyMain.get("aos_productid");
        Object aosOrgnumber;
        DynamicObject aosOrganization1 = dyMain.getDynamicObject("aos_organization1");
        String aosOrganization1Str = aosOrganization1.getString("number");
        Object aosOsconfirmlov = null;

        if (aosOrgid != null) {
            aosOrgnumber = ((DynamicObject)aosOrgid).get("number");
            if (sign.us.name.equals(aosOrgnumber) || sign.ca.name.equals(aosOrgnumber)
                || sign.uk.name.equals(aosOrgnumber)) {
                aosIsSmallLov = "否";
            } else {
                aosIsSmallLov = "是";
            }
        } else {
            if (sign.oldOpt.name.equals(aosType)) {
                // 类型如果是老品优化
                aosIsSmallLov = null;
            } else {
                aosIsSmallLov = "是";
            }
        }

        if (sign.enjoyDept.name.equals(aosOrganization1Str)) {
            // 体验&文案部
            aosIsSmallLov = null;
        }
        if (sign.equal.name.equals(aosType)) {
            // 四者一致
            aosIsSmallLov = "是";
            aosOsconfirmlov = "否";
        }

        // 循环创建
        for (DynamicObject dyn3dR : listRequire) {
            // 头信息
            // 根据国别大类中类取对应营销US编辑
            Object itemId = dyn3dR.getDynamicObject("aos_itemid").getPkValue();
            String category = MKTCom.getItemCateNameZH(itemId);
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
            long aosEditordefualt = 0;
            long aosDesignerdefualt = 0;
            if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
                String type = "";
                if (dyMain.get("aos_type") != null && !"".equals(dyMain.getString("aos_type"))) {
                    type = dyMain.getString("aos_type");
                }
                String orgId = "";
                if (dyMain.get("aos_orgid") != null) {
                    orgId = dyMain.getDynamicObject("aos_orgid").getString("id");
                }
                String[] selectFields = new String[] {"aos_eng aos_editor"};
                DynamicObject aosMktDesigner =
                    ProgressUtil.findDesignerByType(orgId, aosCategory1, aosCategory2, type, selectFields);
                DynamicObject dyEditor = ProgressUtil.findEditorByType(aosCategory1, aosCategory2, type);
                if (aosMktDesigner != null) {
                    aosDesignerdefualt = aosMktDesigner.getLong("aos_designer");
                }
                if (dyEditor != null) {
                    aosEditordefualt = dyEditor.getLong("aos_user");
                }
            }

            if (aosEditordefualt == 0 && aosEditor == null) {
                fndReturn.AddErrorCount();
                fndReturn.AddErrorMessage("英语品类编辑师不存在!");
                return;
            }

            if (aosDesignerdefualt == 0 && aosDesigner == null) {
                fndReturn.AddErrorCount();
                fndReturn.AddErrorMessage("默认品类设计师不存在!");
                return;
            }

            if (aosEditor != null) {
                aosEditordefualt = (long)((DynamicObject)aosEditor).getPkValue();
            }

            if (aosDesigner != null) {
                aosDesignerdefualt = (long)((DynamicObject)aosDesigner).getPkValue();
            }

            // 根据国别编辑产品号 合并单据
            DynamicObject aosMktListingSon;
            QFilter filterEditor = new QFilter("aos_editor", "=", aosEditordefualt);
            QFilter filterSourceid = new QFilter("aos_sourceid", "=", reqId);
            String aosSegment3 = dyn3dR.getString("aos_segment3");
            QFilter filterMent = new QFilter("aos_entryentity.aos_segment3_r", "=", aosSegment3);
            QFilter[] filters = new QFilter[] {filterEditor, filterSourceid, filterMent};
            DynamicObject aosMktListingSonq = QueryServiceHelper.queryOne("aos_mkt_listing_son", "id", filters);
            if (aosMktListingSonq == null) {
                aosMktListingSon = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_son");
                messageFlag = true;
                aosMktListingSon.set("aos_requireby", aosRequireby);
                aosMktListingSon.set("aos_requiredate", new Date());
                aosMktListingSon.set("aos_type", aosType);
                aosMktListingSon.set("aos_source", aosSource);
                aosMktListingSon.set("aos_importance", aosImportance);
                aosMktListingSon.set("aos_designer", aosDesignerdefualt);
                aosMktListingSon.set("aos_editor", aosEditordefualt);
                aosMktListingSon.set("aos_user", aosEditordefualt);
                aosMktListingSon.set("aos_orignbill", billno);
                aosMktListingSon.set("aos_sourceid", reqId);
                aosMktListingSon.set("aos_status", "编辑确认");
                aosMktListingSon.set("aos_sourcetype", "LISTING");
                aosMktListingSon.set("aos_demandate", aosDemandate);
                aosMktListingSon.set("aos_ismalllov", aosIsSmallLov);
                aosMktListingSon.set("aos_orgid", aosOrgid);
                aosMktListingSon.set("aos_osconfirmlov", aosOsconfirmlov);
                aosMktListingSon.set("aos_productbill", aosProductbill);
                aosMktListingSon.set("aos_productid", aosProductid);
                // BOTP
                aosMktListingSon.set("aos_sourcebilltype", "aos_mkt_listing_req");
                aosMktListingSon.set("aos_sourcebillno", dyMain.get("billno"));
                aosMktListingSon.set("aos_srcentrykey", "aos_entryentity");

                List<DynamicObject> mapList =
                    Cux_Common_Utl.GetUserOrg(dyMain.getDynamicObject("aos_requireby").getPkValue());
                if (mapList != null) {
                    if (mapList.size() >= 3 && mapList.get(2) != null) {
                        aosMktListingSon.set("aos_organization1", mapList.get(2).get("id"));
                    }
                    if (mapList.size() >= 4 && mapList.get(3) != null) {
                        aosMktListingSon.set("aos_organization2", mapList.get(3).get("id"));
                    }
                }
            } else {
                aosMktListingSon =
                    BusinessDataServiceHelper.loadSingle(aosMktListingSonq.getLong("id"), "aos_mkt_listing_son");
            }

            // 明细
            DynamicObjectCollection aosEntryentityS = aosMktListingSon.getDynamicObjectCollection("aos_entryentity");
            DynamicObject aosEntryentity = aosEntryentityS.addNew();
            aosEntryentity.set("aos_itemid", itemId);
            aosEntryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(itemId));
            aosEntryentity.set("aos_require", dyn3dR.get("aos_require"));
            aosEntryentity.set("aos_srcrowseq", dyn3dR.get("SEQ"));

            DynamicObjectCollection aosAttribute = aosEntryentity.getDynamicObjectCollection("aos_attribute");
            aosAttribute.clear();
            DynamicObjectCollection aosAttributefrom = dyn3dR.getDynamicObjectCollection("aos_attribute");
            DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
            DynamicObject tempFile;
            for (DynamicObject d : aosAttributefrom) {
                tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
                aosAttribute.addNew().set("fbasedataid", tempFile);
            }

            // 物料相关信息
            DynamicObjectCollection aosSubentryentityS =
                aosEntryentity.getDynamicObjectCollection("aos_subentryentity");
            DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
            aosSubentryentity.set("aos_segment3", dyn3dR.get("aos_segment3"));
            aosSubentryentity.set("aos_broitem", dyn3dR.get("aos_broitem"));
            aosSubentryentity.set("aos_itemname", dyn3dR.get("aos_itemname"));
            aosSubentryentity.set("aos_orgtext", dyn3dR.get("aos_orgtext"));
            aosSubentryentity.set("aos_reqinput", dyn3dR.get("aos_require"));
            aosEntryentity.set("aos_segment3_r", dyn3dR.get("aos_segment3"));
            aosEntryentity.set("aos_broitem_r", dyn3dR.get("aos_broitem"));
            aosEntryentity.set("aos_itemname_r", dyn3dR.get("aos_itemname"));
            aosEntryentity.set("aos_orgtext_r", ProgressUtil.getOrderOrg(itemId));
            AosMktListingSonBill.setListSonUserOrganizate(aosMktListingSon);
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_son",
                new DynamicObject[] {aosMktListingSon}, OperateOption.create());

            FndHistory.Create("aos_mkt_listing_son", operationrst.getSuccessPkIds().get(0).toString(), "优化需求生成文案",
                "到编辑确认节点");

            // 修复关联关系
            try {
                ProgressUtil.botp("aos_mkt_listing_son", aosMktListingSon.get("id"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            if (messageFlag) {
                messageId = String.valueOf(aosEditordefualt);
                message = "Listing优化需求表子表-Listing优化需求自动创建";
                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    MKTCom.SendGlobalMessage(messageId, String.valueOf(aosMktListingSon),
                        String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktListingSon.getString("billno"),
                        message);
                }
            }
        }
    }

    /**
     * 图片需求生成设计需求表
     *
     **/
    public static void generateDesignReq(List<DynamicObject> listRequirePic, FndReturn fndReturn,
        DynamicObject dyMain) {

        // 异常处理
        fndReturn.clear();
        // 信息处理
        boolean messageFlag = false;
        String messageId;
        String message;
        // 数据层
        Object aosDesigner = dyMain.get("aos_designer");
        Object aosEditor = dyMain.get("aos_editor");
        Object billno = dyMain.get("billno");
        Object reqId = dyMain.get("id");

        Object aosSource = dyMain.get("aos_source");
        if (dyMain.getBoolean(sign.autoFlag.name) && FndGlobal.IsNull(sign.productBill.name)) {
            aosSource = "老品重拍-传图";
        }

        Object aosImportance = dyMain.get("aos_importance");
        Object aosOrgid = dyMain.get("aos_orgid");
        Object aosRequireby = dyMain.get("aos_requireby");

        Object aosProductbill = dyMain.get("aos_productbill");
        Object aosProductid = dyMain.get("aos_productid");

        // 循环创建
        for (DynamicObject dyn3dR : listRequirePic) {
            String aosSegment3 = dyn3dR.getString("aos_segment3");
            // 根据国别大类中类取对应营销US编辑
            Object itemId = dyn3dR.getDynamicObject("aos_itemid").getPkValue();
            String category = MKTCom.getItemCateNameZH(itemId);
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
            long aosEditordefualt = 0;
            long aosDesignerdefualt = 0;
            long aosDesigneror = 0;
            long aos3d = 0;
            if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
                String type = "";
                if (dyMain.get("aos_type") != null && !"".equals(dyMain.getString("aos_type"))) {
                    type = dyMain.getString("aos_type");
                }
                String orgId = "";
                if (dyMain.get("aos_orgid") != null) {
                    orgId = dyMain.getDynamicObject("aos_orgid").getString("id");
                }
                String[] selectFields = new String[] {"aos_eng aos_editor", "aos_designeror", "aos_3d"};
                DynamicObject aosMktProguser =
                    ProgressUtil.findDesignerByType(orgId, aosCategory1, aosCategory2, type, selectFields);
                if (aosMktProguser != null) {
                    aosEditordefualt = aosMktProguser.getLong("aos_editor");
                    aosDesignerdefualt = aosMktProguser.getLong("aos_designer");
                    aosDesigneror = aosMktProguser.getLong("aos_designeror");
                    aos3d = aosMktProguser.getLong("aos_3d");
                }
            }

            if (aosEditordefualt == 0 && aosEditor == null) {
                fndReturn.AddErrorCount();
                fndReturn.AddErrorMessage("英语品类编辑师不存在!");
                return;
            }

            if (aosDesignerdefualt == 0 && aosDesigner == null) {
                fndReturn.AddErrorCount();
                fndReturn.AddErrorMessage("默认品类设计师不存在!");
                return;
            }

            if (aosDesigner != null) {
                aosDesignerdefualt = (long)((DynamicObject)aosDesigner).getPkValue();
            }

            // 根据国别编辑合并单据
            DynamicObject aosMktDesignreq;
            QFilter filterSegment3 = new QFilter("aos_groupseg", "=", aosSegment3);
            QFilter filterSourceid = new QFilter("aos_sourceid", "=", reqId);
            QFilter[] filters = new QFilter[] {filterSegment3, filterSourceid};
            DynamicObject aosMktDesignreqq = QueryServiceHelper.queryOne("aos_mkt_designreq", "id", filters);
            if (aosMktDesignreqq == null) {
                aosMktDesignreq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designreq");
                messageFlag = true;
                aosMktDesignreq.set("aos_groupseg", aosSegment3);
                aosMktDesignreq.set("aos_requiredate", new Date());
                aosMktDesignreq.set("aos_type", dyMain.get("aos_type"));
                aosMktDesignreq.set("aos_shipdate", null);
                aosMktDesignreq.set("aos_orignbill", billno);
                aosMktDesignreq.set("aos_sourceid", reqId);
                aosMktDesignreq.set("aos_status", "设计");
                aosMktDesignreq.set("aos_user", aosDesignerdefualt);
                aosMktDesignreq.set("aos_source", aosSource);
                aosMktDesignreq.set("aos_importance", aosImportance);
                aosMktDesignreq.set("aos_designer", aosDesignerdefualt);
                aosMktDesignreq.set("aos_dm", aosDesigneror);
                aosMktDesignreq.set("aos_3der", aos3d);
                aosMktDesignreq.set("aos_orgid", aosOrgid);
                aosMktDesignreq.set("aos_sourcetype", "LISTING");
                aosMktDesignreq.set("aos_requireby", aosRequireby);
                aosMktDesignreq.set("aos_productbill", aosProductbill);
                aosMktDesignreq.set("aos_productid", aosProductid);

                // BOTP
                aosMktDesignreq.set("aos_sourcebilltype", "aos_mkt_listing_req");
                aosMktDesignreq.set("aos_sourcebillno", dyMain.get("billno"));
                aosMktDesignreq.set("aos_srcentrykey", "aos_entryentity");

                List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(((DynamicObject)aosRequireby).getPkValue());
                if (mapList != null) {
                    if (mapList.size() >= 3 && mapList.get(2) != null) {
                        aosMktDesignreq.set("aos_organization1", mapList.get(2).get("id"));
                    }
                    if (mapList.size() >= 4 && mapList.get(3) != null) {
                        aosMktDesignreq.set("aos_organization2", mapList.get(3).get("id"));
                    }
                }
            } else {
                aosMktDesignreq =
                    BusinessDataServiceHelper.loadSingle(aosMktDesignreqq.getLong("id"), "aos_mkt_designreq");
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
                // 安全库存 海外库存
                if ("C".equals(aosContryentry.getString("aos_contryentrystatus")) && osQty < safeQty) {
                    continue;
                }
                aosOrgtext.append(aosNationalitynumber).append(";");

                Object obj = aosContryentry.getDynamicObject("aos_contrybrand");
                if (obj == null) {
                    continue;
                }
                String value =
                    aosNationalitynumber + "~" + aosContryentry.getDynamicObject("aos_contrybrand").getString("number");

                String bra = aosContryentry.getDynamicObject("aos_contrybrand").getString("number");
                if (bra != null) {
                    setBra.add(bra);
                }
                if (setBra.size() > 1) {
                    {
                        if (!aosContrybrandStr.toString().contains(value)) {
                            aosContrybrandStr.append(value).append(";");
                        }
                    }
                } else if (setBra.size() == 1) {
                    if (bra != null) {
                        aosContrybrandStr = new StringBuilder(bra);
                    }
                }
            }
            String itemNumber = bdMaterial.getString("number");
            String url = "https://clss.s3.amazonaws.com/" + itemNumber + ".jpg";
            String aosProductno = bdMaterial.getString("aos_productno");
            String aosItemname = bdMaterial.getString("name");
            // 获取同产品号物料
            StringBuilder aosBroitem = new StringBuilder();
            if (!Cux_Common_Utl.IsNull(aosProductno)) {
                DynamicObjectCollection bdMaterialS = QueryServiceHelper.query("bd_material", "id,number,aos_type",
                    new QFilter("aos_productno", QCP.equals, aosProductno).and("aos_type", QCP.equals, "A").toArray());
                for (DynamicObject bd : bdMaterialS) {
                    Object itemid = bdMaterial.get("id");
                    if ("B".equals(bd.getString("aos_type"))) {
                        continue; // 配件不获取
                    }
                    boolean exist = QueryServiceHelper.exists("bd_material", new QFilter("id", QCP.equals, itemid)
                        .and("aos_contryentry.aos_contryentrystatus", QCP.not_equals, "C"));
                    if (!exist) {
                        continue;
                    }
                    // 全球终止不取
                    int osQty = getOsQty(itemid);
                    if (osQty < 10) {
                        continue;
                    }
                    String number = bd.getString("number");
                    if (!itemNumber.equals(number)) {
                        aosBroitem.append(number).append(";");
                    }
                }
            }

            DynamicObject aosEntryentity = aosEntryentityS.addNew();
            aosEntryentity.set("aos_itemid", itemId);
            aosEntryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(itemId));
            aosEntryentity.set("aos_desreq", dyn3dR.get("aos_requirepic"));
            aosEntryentity.set("aos_language", dyn3dR.get("aos_language"));
            aosEntryentity.set("aos_3d", dyn3dR.get("aos_3d"));
            aosEntryentity.set("aos_srcrowseq", dyn3dR.get("SEQ"));

            DynamicObjectCollection aosAttribute = aosEntryentity.getDynamicObjectCollection("aos_attribute");
            aosAttribute.clear();
            DynamicObjectCollection aosAttributefrom = dyn3dR.getDynamicObjectCollection("aos_attribute");
            DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
            DynamicObject tempFile;
            for (DynamicObject d : aosAttributefrom) {
                tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
                aosAttribute.addNew().set("fbasedataid", tempFile);
            }

            DynamicObjectCollection aosSubentryentityS =
                aosEntryentity.getDynamicObjectCollection("aos_subentryentity");
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
            AosMktDesignReqBill.setEntityValue(aosMktDesignreq);
            AosMktDesignReqBill.createDesiginBeforeSave(aosMktDesignreq);
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
                new DynamicObject[] {aosMktDesignreq}, OperateOption.create());

            FndHistory.Create("aos_mkt_designreq", operationrst.getSuccessPkIds().get(0).toString(), "优化需求生成设计完成",
                "到设计节点");

            // 修复关联关系
            try {
                ProgressUtil.botp("aos_mkt_designreq", aosMktDesignreq.get("id"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            if (messageFlag) {
                messageId = String.valueOf(aosDesignerdefualt);
                message = "设计需求表-Listing优化需求自动创建";
                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    MKTCom.SendGlobalMessage(messageId, String.valueOf(aosMktDesignreq),
                        String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktDesignreq.getString("billno"),
                        message);
                    FndHistory.Create(aosMktDesignreq, aosMktDesignreq.getString("aos_status"),
                        "设计需求表-Listing优化需求自动创建");
                }
            }
        }
    }

    /**
     * 物料改变和导入数据后的单据行赋值
     *
     */
    private static void entityRowSetValue(DynamicObject dyRow, Object id) throws FndError {
        DynamicObject aosItemid = dyRow.getDynamicObject("aos_itemid");
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_MONTH, -90);
        Date dateFrom = calendar.getTime();
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String dateFromStr = writeFormat.format(dateFrom);
        if (aosItemid == null) {
            // 清空
            dyRow.set("aos_segment3", null);
            dyRow.set("aos_broitem", null);
            dyRow.set("aos_itemname", null);
            dyRow.set("aos_orgtext", null);
            // 删除子单据体
            deleteSubEntry(dyRow);
        } else {
            Object itemId = aosItemid.getPkValue();
            StringBuilder aosOrgtext = new StringBuilder();
            DynamicObject bdMaterial = BusinessDataServiceHelper.loadSingle(itemId, "bd_material");
            String itemNumber = bdMaterial.getString("number");
            String aosProductno = bdMaterial.getString("aos_productno");
            String aosItemname = bdMaterial.getString("name");
            boolean aosIscomb = bdMaterial.getBoolean("aos_iscomb");

            DynamicObjectCollection aosContryentryS = bdMaterial.getDynamicObjectCollection("aos_contryentry");
            // 字符串拼接
            for (DynamicObject aosContryentry : aosContryentryS) {
                DynamicObject aosNationality = aosContryentry.getDynamicObject("aos_nationality");
                String aosNationalitynumber = aosNationality.getString("number");
                if ("IE".equals(aosNationalitynumber)) {
                    continue;
                }
                Object orgId = aosNationality.get("id");
                int osQty = ItemInfoUtil.getItemOsQty(orgId, itemId);
                int safeQty = ItemInfoUtil.getSafeQty(orgId);
                // 终止、小于安全库存
                if ("C".equals(aosContryentry.getString("aos_contryentrystatus")) && osQty < safeQty) {
                    continue;
                }
                // 代卖、小于安全库存
                if ("F".equals(aosContryentry.getString("aos_contryentrystatus")) && osQty < safeQty) {
                    continue;
                }
                // 虚拟上架、小于安全库存
                if ("H".equals(aosContryentry.getString("aos_contryentrystatus")) && osQty < safeQty) {
                    continue;
                }
                aosOrgtext.append(aosNationalitynumber).append(";");
            }

            // 提示：所有国别已终止，且无库存，不建议优化listing
            if (FndGlobal.IsNull(aosOrgtext.toString()) && !aosIscomb) {
                throw new FndError(itemNumber + "- 所有国别已终止，且无库存，不建议优化listing!");
            }

            // 获取同产品号物料
            StringBuilder aosBroitem = new StringBuilder();
            if (!Cux_Common_Utl.IsNull(aosProductno)) {
                DynamicObjectCollection bdMaterialS = QueryServiceHelper.query("bd_material", "id,number,aos_type",
                    new QFilter("aos_productno", QCP.equals, aosProductno).and("aos_type", QCP.equals, "A").toArray());
                for (DynamicObject bd : bdMaterialS) {
                    Object itemid = bd.getString("id");
                    if ("B".equals(bd.getString("aos_type"))) {
                        continue; // 配件不获取
                    }
                    DynamicObject one = QueryServiceHelper.queryOne("bd_material", "id",
                        new QFilter("number", "=", bd.getString("number"))
                            .and("aos_contryentry.aos_contryentrystatus", "!=", "C").toArray());
                    if (FndGlobal.IsNull(one)) {
                        continue;// 全球终止不取
                    }
                    int osQty = getOsQty(itemid);
                    if (osQty < 10) {
                        continue;
                    }
                    String number = bd.getString("number");
                    if (!itemNumber.equals(number)) {
                        aosBroitem.append(number).append(";");
                    }
                }
            }
            // 赋值物料相关
            dyRow.set("aos_segment3", aosProductno);
            dyRow.set("aos_itemname", aosItemname);
            dyRow.set("aos_broitem", aosBroitem.toString());
            dyRow.set("aos_orgtext", aosOrgtext.toString());
            // 对近期流程进行赋值
            QFilter filterItem = new QFilter("aos_entryentity.aos_itemid", QCP.equals, itemId);
            QFilter filterFid = null;
            if (id != null) {
                filterFid = new QFilter("id", QCP.not_equals, id);
            }
            QFilter filterDate = new QFilter("aos_requiredate", QCP.large_equals, dateFromStr);
            QFilter[] filters = new QFilter[] {filterItem, filterFid, filterDate};
            DynamicObjectCollection aosMktListingReqS =
                QueryServiceHelper
                    .query("aos_mkt_listing_req",
                        "id,billno," + "aos_entryentity.aos_require aos_require,"
                            + "aos_entryentity.aos_requirepic aos_requirepic," + "aos_requireby,aos_requiredate",
                        filters);
            // 删除子单据体
            deleteSubEntry(dyRow);
            int i = 0;
            DynamicObjectCollection dycRowSubEntity = dyRow.getDynamicObjectCollection("aos_subentryentity");
            for (DynamicObject aosMktListingReq : aosMktListingReqS) {
                aosMktListingReq.get("billno");
                DynamicObject dySubNew = dycRowSubEntity.addNew();
                dySubNew.set("SEQ", i);
                dySubNew.set("aos_likeno", aosMktListingReq.get("billno"));
                dySubNew.set("aos_requirelast", aosMktListingReq.get("aos_require"));
                dySubNew.set("aos_requirepiclast", aosMktListingReq.get("aos_requirepic"));
                dySubNew.set("aos_likeid", aosMktListingReq.get("id"));
                dySubNew.set("aos_lastrequire", aosMktListingReq.get("aos_requireby"));
                dySubNew.set("aos_lastdate", aosMktListingReq.get("aos_requiredate"));
                i++;
            }
        }
    }

    /**
     * 删除子单据体
     */
    private static void deleteSubEntry(DynamicObject dyRow) {
        DynamicObjectCollection dycRowSubEntity = dyRow.getDynamicObjectCollection("aos_subentryentity");
        Iterator<DynamicObject> iterator = dycRowSubEntity.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    /**
     * 获取物料全球海外库存
     *
     */
    public static int getOsQty(Object itemid) {
        int aosInstockQty = 0;
        DynamicObjectCollection aosSyncInvouValueS = QueryServiceHelper.query("aos_sync_invou_value", "aos_instock_qty",
            new QFilter("aos_item", QCP.equals, itemid).toArray());
        for (DynamicObject aosSyncInvouValue : aosSyncInvouValueS) {
            aosInstockQty += aosSyncInvouValue.getInt("aos_instock_qty");
        }
        return aosInstockQty;
    }

    /**
     * 创建优化需求表后给物料的大类赋值
     **/
    public static void setItemCate(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
            DynamicObjectCollection dycEntity = dyMain.getDynamicObjectCollection("aos_entryentity");
            ItemCategoryDao itemCategoryDao = new ItemCategoryDaoImpl();
            for (DynamicObject dyEnt : dycEntity) {
                Object aosItem = dyEnt.get("aos_itemid");
                Object itemid;
                if (aosItem instanceof Long) {
                    itemid = aosItem;
                } else if (aosItem instanceof DynamicObject) {
                    itemid = ((DynamicObject)aosItem).get("id");
                } else if (aosItem instanceof String) {
                    itemid = aosItem;
                } else {
                    return;
                }
                String cateNameZh = itemCategoryDao.getItemCateNameZH(itemid);
                if (Cux_Common_Utl.IsNull(cateNameZh)) {
                    continue;
                }
                String[] split = cateNameZh.split(",");
                dyEnt.set("aos_category1", split[0]);
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    @Override
    public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
        int rowIndex = hyperLinkClickEvent.getRowIndex();
        String fieldName = hyperLinkClickEvent.getFieldName();
        if (sign.likeNo.name.equals(fieldName)) {
            Object aosLikeid = this.getModel().getValue("aos_likeid", rowIndex);
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_listing_req", aosLikeid);
        }
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        this.addItemClickListeners("aos_toolbarap");
        this.addItemClickListeners("aos_submit");
        this.addItemClickListeners("aos_open");

        EntryGrid entryGrid = this.getControl("aos_subentryentity");
        entryGrid.addRowClickListener(this);
        entryGrid.addHyperClickListener(this);
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        Span span = MmsOtelUtils.getCusMainSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
            if (sign.submit.name.equals(control)) {
                DynamicObject dyMain = this.getModel().getDataEntity(true);
                aosSubmit(dyMain, "OLD");
            } else if (sign.history.name.equals(control)) {
                // 查看历史记录
                aosHistory();
            } else if (sign.open.name.equals(control)) {
                // 打开产品资料变跟单
                aosOpen();
            }
        } catch (FndError fndError) {
            fndError.show(getView(), FndWebHook.urlMms);
            MmsOtelUtils.setException(span, fndError);
        } catch (Exception ex) {
            FndError.showex(getView(), ex, FndWebHook.urlMms);
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private void aosOpen() {
        Object aosProductid = this.getModel().getValue("aos_productid");
        DynamicObject aosProdatachan = QueryServiceHelper.queryOne("aos_prodatachan", "id",
            new QFilter[] {new QFilter("id", QCP.equals, aosProductid)});
        if (!Cux_Common_Utl.IsNull(aosProdatachan)) {
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_prodatachan", aosProdatachan.get("id"));
        } else {
            this.getView().showErrorNotification("产品资料变跟单信息不存在!");
        }
    }

    @Override
    public void afterImportData(ImportDataEventArgs e) {
        DynamicObject mainDyEntity = this.getModel().getDataEntity(true);
        DynamicObject aosRequireby = mainDyEntity.getDynamicObject("aos_requireby");
        if (aosRequireby != null) {
            mainDyEntity.set("aos_orgid", ProgressUtil.getOrgByOrganizate(aosRequireby.getPkValue()));
        }
        Set<String> failS = new HashSet<>();
        try {
            Object pkValue = mainDyEntity.getPkValue();
            DynamicObjectCollection dycSub = mainDyEntity.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject dyRow : dycSub) {
                try {
                    entityRowSetValue(dyRow, pkValue);
                } catch (FndError fndMessage) {
                    FndMsg.debug("into error");
                    failS.add(dyRow.getDynamicObject("aos_itemid").getPkValue().toString());
                    e.setCancel(true);
                    e.setCancelMessages(0, 0, Collections.singletonList(fndMessage.getErrorMessage()));

                }
            }
            Iterator<DynamicObject> iterator = dycSub.iterator();
            while (iterator.hasNext()) {
                DynamicObject dyn = iterator.next();
                String itemId = dyn.getDynamicObject("aos_itemid").getPkValue().toString();
                if (FndGlobal.IsNotNull(failS) && failS.contains(itemId)) {
                    iterator.remove();
                }
            }
            SaveServiceHelper.save(new DynamicObject[] {mainDyEntity});
        } catch (FndError fndMessage) {
            e.setCancel(true);
            e.setCancelMessages(0, 0, Collections.singletonList(fndMessage.getErrorMessage()));
            fndMessage.printStackTrace();
        } catch (Exception e1) {
            e.setCancel(true);
            StringWriter sw = new StringWriter();
            e1.printStackTrace(new PrintWriter(sw));
            String write = sw.toString();
            int maxLength = 2000;
            if (write.length() > maxLength) {
                write = write.substring(0, maxLength - 1);
            }
            e.setCancelMessages(0, 0, Collections.singletonList(e1.getMessage()));
            log.error("listing需求优化子表导入异常  " + write);
            e1.printStackTrace();
        }
    }

    /**
     * 打开历史记录
     **/
    private void aosHistory() throws FndError {
        Cux_Common_Utl.OpenHistory(this.getView());
    }

    /**
     * 初始化事件
     **/
    @Override
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        statusControl();
    }

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs args) {
        FormOperate formOperate = (FormOperate)args.getSource();
        String operatation = formOperate.getOperateKey();
        try {
            if (sign.save.name.equals(operatation)) {
                statusControl();
                this.getView().invokeOperation("refresh");
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    /**
     * 保存之前
     **/
    @Override
    public void beforeDoOperation(BeforeDoOperationEventArgs args) {
        FormOperate formOperate = (FormOperate)args.getSource();
        String operatation = formOperate.getOperateKey();
        try {
            if (sign.equal.name.equals(operatation)) {
                DynamicObject dyMain = this.getModel().getDataEntity(true);
                // 保存校验 保存校验调整为驼峰命名
                saveControl(dyMain);
                setItemCate(dyMain);
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
            args.setCancel(true);
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
            args.setCancel(true);
        }
    }

    /**
     * 老品优化校验
     */
    private void oldValid() {
        String fid = this.getModel().getDataEntity(true).getString("id");
        DynamicObject orgObj = (DynamicObject)this.getModel().getValue("aos_orgid", 0);
        if (FndGlobal.IsNotNull(orgObj)) {
            DynamicObject itemObj = (DynamicObject)this.getModel().getValue("aos_itemid", 0);
            boolean exists = QueryServiceHelper.exists("aos_mkt_listing_req",
                new QFilter("aos_entryentity.aos_itemid", QCP.equals, itemObj.getString("id"))
                    .and("aos_orgid", QCP.equals, orgObj.getString("id")).and("id", QCP.not_equals, fid)
                    .and("aos_requiredate", QCP.large_equals, FndDate.add_days(new Date(), -90)).toArray());
            if (exists) {
                ConfirmCallBackListener confirmCallBackListener = new ConfirmCallBackListener("oldvalid", this);
                // 设置页面确认框，参数为：标题，选项框类型，回调监听
                this.getView().showConfirm("3个月内该SKU已提优化，是否继续", MessageBoxOptions.YesNo, confirmCallBackListener);
            } else {
                aosSubmit(this.getModel().getDataEntity(true), "A");
            }
        } else {
            aosSubmit(this.getModel().getDataEntity(true), "A");
        }
    }

    @Override
    public void confirmCallBack(MessageBoxClosedEvent event) {
        super.confirmCallBack(event);
        try {
            if (sign.oldValid.name.equalsIgnoreCase(event.getCallBackId())
                && event.getResult().equals(MessageBoxResult.Yes)) {
                aosSubmit(this.getModel().getDataEntity(true), "A");
            }
        } catch (Exception e) {
            this.getView().showErrorNotification("报错：" + e.getMessage());
        }

    }

    /**
     * 新建事件
     **/
    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        statusControl();
        initDefualt();
        this.getView().setVisible(false, "aos_submit");
    }

    /**
     * 界面关闭事件
     **/
    @Override
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        e.setCheckDataChange(false);
    }

    /**
     * 值改变事件
     **/
    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        try {
            if (sign.itemId.name.equals(name)) {
                int rowIndex = e.getChangeSet()[0].getRowIndex();
                aosItemChange(rowIndex);
            } else if (sign.type.name.equals(name)) {
                aosTypeChange();
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
            if (sign.itemId.name.equals(name)) {
                this.getModel().setValue(sign.itemId.name, null, e.getChangeSet()[0].getRowIndex());
            }
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }

    }

    /**
     * 任务类型改变
     **/
    private void aosTypeChange() {
        Object aosType = this.getModel().getValue("aos_type");
        Object aosRequiredate = this.getModel().getValue("aos_requiredate");
        Object aosDemandate = new Date();
        if (sign.equal.name.equals(aosType)) {
            // 四者一致
            aosDemandate = Cux_Common_Utl.add_NoWeekendDays((Date)aosRequiredate, 1);
        } else if (sign.oldOpt.name.equals(aosType)) {
            aosDemandate = Cux_Common_Utl.add_NoWeekendDays((Date)aosRequiredate, 3);
        }
        this.getModel().setValue("aos_demandate", aosDemandate);
    }

    /**
     * 新建设置默认值
     **/
    private void initDefualt() {
        long currentUserId = UserServiceHelper.getCurrentUserId();
        List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(currentUserId);
        if (mapList != null) {
            int two = 2;
            int three = 3;
            int four = 4;
            if (mapList.size() >= three && mapList.get(two) != null) {
                this.getModel().setValue("aos_organization1", mapList.get(two).get("id"));
            }
            if (mapList.size() >= four && mapList.get(three) != null) {
                this.getModel().setValue("aos_organization2", mapList.get(three).get("id"));
            }
        }

        // 根据组织获取国别
        String aosUnit = Cux_Common_Utl.GetUserOrgLong(currentUserId);
        this.getModel().setValue("aos_unit", aosUnit);
        if (aosUnit.contains(sign.usa.name)) {
            aosUnit("US");
        } else if (aosUnit.contains(sign.canada.name)) {
            aosUnit("CA");
        } else if (aosUnit.contains(sign.unitKingdom.name)) {
            aosUnit("UK");
        } else if (aosUnit.contains(sign.ukSale.name)) {
            aosUnit("UK");
        } else if (aosUnit.contains(sign.de.name)) {
            aosUnit("DE");
        } else if (aosUnit.contains(sign.fr.name)) {
            aosUnit("FR");
        } else if (aosUnit.contains(sign.it.name)) {
            aosUnit("IT");
        } else if (aosUnit.contains(sign.sp.name)) {
            aosUnit("ES");
        } else if (aosUnit.contains(sign.usName.name)) {
            aosUnit("US");
        } else if (aosUnit.contains(sign.caName.name)) {
            aosUnit("CA");
        } else if (aosUnit.contains(sign.ukName.name)) {
            aosUnit("UK");
        } else if (aosUnit.contains(sign.deName.name)) {
            aosUnit("DE");
        } else if (aosUnit.contains(sign.frName.name)) {
            aosUnit("FR");
        } else if (aosUnit.contains(sign.itName.name)) {
            aosUnit("IT");
        } else if (aosUnit.contains(sign.esName.name)) {
            aosUnit("ES");
        }
    }

    private void aosUnit(String value) {
        QFilter filterUnit = new QFilter("number", "=", value);
        QFilter[] filters = new QFilter[] {filterUnit};
        String selectColumn = "id,number";
        DynamicObject bdCountry = QueryServiceHelper.queryOne("bd_country", selectColumn, filters);
        if (bdCountry != null) {
            this.getModel().setValue("aos_orgid", bdCountry.get("id"));
        }
    }

    /**
     * 物料值改变
     **/
    private void aosItemChange(int row) throws FndError {
        if (row >= 0) {
            Object pkValue = this.getModel().getDataEntity(true).getPkValue();
            DynamicObject dyRow =
                this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity").get(row);
            entityRowSetValue(dyRow, pkValue);
            this.getView().updateView("aos_entryentity", row);
        }
    }

    /**
     * 提交
     **/
    public void aosSubmit(DynamicObject dyMain, String type) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
            // 【国别+SKU+老品优化】判断是否弹窗提醒;
            if (sign.old.name.equals(type)) {
                oldValid();
            } else {
                saveControl(dyMain);
                String aosStatus = dyMain.getString("aos_status");
                if (sign.applyBy.name.equals(aosStatus)) {
                    submitForNew(dyMain);
                }
                setItemCate(dyMain);
                // 保存
                SaveServiceHelper.save(new DynamicObject[] {dyMain});
                FndHistory.Create(dyMain, "提交", aosStatus);
                // 界面提交，提交后眼状态控制
                if (sign.A.name.equals(type)) {
                    this.getView().invokeOperation("refresh");
                    statusControl();// 提交完成后做新的界面状态控制
                }
            }

        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 全局状态控制
     **/
    private void statusControl() {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
            // 数据层
            Object aosStatus = this.getModel().getValue("aos_status");
            DynamicObject aosUser = (DynamicObject)this.getModel().getValue("aos_user");
            Object currentUserId = UserServiceHelper.getCurrentUserId();
            Object currentUserName = UserServiceHelper.getUserInfoByID((long)currentUserId).get("name");
            // 图片控制
            // InitPic();
            // 锁住需要控制的字段
            // 当前节点操作人不为当前用户 全锁
            if (!aosUser.getPkValue().toString().equals(currentUserId.toString())
                && !"程震杰".equals(currentUserName.toString()) && !"陈聪".equals(currentUserName.toString())) {
                this.getView().setEnable(false, "titlepanel");
                this.getView().setEnable(false, "aos_contentpanelflex");
                this.getView().setVisible(false, "bar_save");
                this.getView().setVisible(false, "aos_submit");
            }
            // 状态控制
            if (sign.applyBy.name.equals(aosStatus)) {
                this.getView().setVisible(true, "aos_submit");
                this.getView().setEnable(true, "aos_contentpanelflex");
                this.getView().setVisible(true, "bar_save");
            } else if (sign.finish.name.equals(aosStatus)) {
                this.getView().setVisible(false, "aos_submit");
                this.getView().setEnable(false, "aos_contentpanelflex");
                this.getView().setVisible(false, "bar_save");
                this.getView().setVisible(false, "aos_import");
                this.getView().setVisible(false, "aos_refresh");
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 标识枚举类
     */
    private enum sign {
        /**
         * US
         */
        us("US"),
        /**
         * CA
         */
        ca("CA"),
        /**
         * UK
         */
        uk("UK"),
        /**
         * 体验&文案部
         */
        enjoyDept("体验&文案部"),
        /**
         * 四者一致
         */
        equal("四者一致"),
        /**
         * 自动标识
         */
        autoFlag("aos_autoflag"),
        /**
         * 产品单号
         */
        productBill("aos_productbill"),
        /**
         * 类似单号
         */
        likeNo("aos_likeno"),
        /**
         * 提交按钮
         */
        submit("aos_submit"),
        /**
         * 产看历史记录按钮
         */
        history("aos_history"),
        /**
         * 打开按钮
         */
        open("aos_open"),
        /**
         * 物料ID
         */
        itemId("aos_itemid"),
        /**
         * 保存操作
         */
        save("save"),
        /**
         * 老品校验
         */
        oldValid("oldvalid"),
        /**
         * 类型
         */
        type("aos_type"),
        /**
         * 老品优化
         */
        oldOpt("老品优化"),
        /**
         * 美国
         */
        usa("美国"),
        /**
         * 加拿大
         */
        canada("加拿大"),
        /**
         * 英国
         */
        unitKingdom("英国"),
        /**
         * 英国销售部
         */
        ukSale("英国销售部"),
        /**
         * 德国
         */
        de("德国"),
        /**
         * 法国
         */
        fr("法国"),
        /**
         * 意大利
         */
        it("意大利"),
        /**
         * 西班牙
         */
        sp("西班牙"),
        /**
         * 美国公司
         */
        usName("AOSOM LLC"),
        /**
         * 加拿大公司
         */
        caName("AOSOM CANADA INC"),
        /**
         * 德国公司
         */
        deName("MH HANDEL GMBH"),
        /**
         * 法国公司
         */
        frName("MH FRANCE"),
        /**
         * 意大利公司
         */
        itName("AOSOM ITALY SRL"),
        /**
         * 西班牙公司
         */
        esName("SPANISH AOSOM, S.L."),
        /**
         * OLD
         */
        old("OLD"),
        /**
         * 申请人
         */
        applyBy("申请人"),
        /**
         * 英国公司
         */
        ukName("MH STAR UK LTD"),
        /**
         * 已完成
         */
        finish("已完成"),
        /**
         * A
         */
        A("A");

        /**
         * 名称
         */
        private final String name;

        /**
         * 构造方法
         *
         * @param name 名称
         */
        sign(String name) {
            this.name = name;
        }
    }
}