package mkt.progress.listing.hotpoint;

import common.CommonMktListing;
import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.fnd.FndMsg;
import common.sal.util.SalUtil;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.util.*;
import java.util.stream.Collectors;

import static mkt.progress.ProgressUtil.Is_saleout;

/**
 * @author aosom
 * @since 2024/2/2 10:57
 * @version 爆品质量打分-工具类
 */
public class AosMktListingHotUtil {
    public final static String NEWDES = "新品设计";
    public final static String HOME = "居家系列";
    public final static String VED = "视频";
    public final static int TWO = 2;
    public final static int THREE = 3;
    public final static int FOUR = 4;
    public final static String[] DOCGROUP = {"aos_point1", "aos_point2", "aos_point3", "aos_point4", "aos_point5"};
    public final static String[] DESGROUP = {"aos_point6", "aos_point7", "aos_point8", "aos_point9", "aos_point10"};
    public final static String[] VEDGROUP = {"aos_point11", "aos_point12"};
    public final static String NO = "否";

    /**
     * 视频打分明细
     *
     * @param photoDyn 拍照需求表对象
     */
    public static void createHotFromPhoto(DynamicObject photoDyn) {
        FndMsg.debug("===========into createHotFromPhoto===========");
        // 新生成的拍照需求表，是否新产品=是，是否爆品=是，需求类型1=视频 触发生成
        // 拍照需求类型
        String aosType = photoDyn.getString("aos_type");
        // 是否新产品
        boolean aosNewitem = photoDyn.getBoolean("aos_newitem");
        // 是否爆品
        boolean aosIsSaleout = photoDyn.getBoolean("aos_is_saleout");
        if (!(VED.equals(aosType) && aosNewitem && aosIsSaleout)) {
            return;// 未满足条件不生成对应单据
        }
        // 货号
        DynamicObject itemId = photoDyn.getDynamicObject("aos_itemid");
        // 爆品质量打分对象
        DynamicObject aosMktHotPoint = BusinessDataServiceHelper.newDynamicObject("aos_mkt_hot_point");
        // 类型
        aosMktHotPoint.set("aos_type", "VED");
        // 申请日期
        aosMktHotPoint.set("aos_applydate", new Date());
        // 申请人取拍照需求表摄像师
        aosMktHotPoint.set("aos_apply", photoDyn.get("aos_vedior"));
        // SKU
        aosMktHotPoint.set("aos_itemid", itemId);
        // 流程节点
        aosMktHotPoint.set("aos_status", "待确认");
        // 来源单id
        aosMktHotPoint.set("aos_sourceid", photoDyn.getPkValue().toString());
        // 当前操作人 根据品类判断
        setUser(aosMktHotPoint, itemId.getString("id"));
        // 爆品质量打分行
        setLine(aosMktHotPoint, itemId, VEDGROUP);
        OperationServiceHelper.executeOperate("save", "aos_mkt_hot_point", new DynamicObject[] {aosMktHotPoint},
            OperateOption.create());
    }

    /**
     * 设计打分明细
     * 
     * @param designDyn 设计需求表对象
     */
    public static void createHotFromDesign(DynamicObject designDyn) {
        // 设计需求表类型
        String aosType = designDyn.getString("aos_type");
        if (!NEWDES.equals(aosType)) {
            return;// 非新品设计类型不生成对应单据
        }
        // 设计需求表行上是否爆品为是的SKU需要拆分生成爆品质量打分表
        DynamicObjectCollection entityS = designDyn.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject entity : entityS) {
            // 爆品
            boolean aosIsSaleout = entity.getBoolean("aos_is_saleout");
            if (!aosIsSaleout) {
                continue; // 非爆品不处理
            }
            // 货号
            DynamicObject itemId = entity.getDynamicObject("aos_itemid");
            // 爆品质量打分对象
            DynamicObject aosMktHotPoint = BusinessDataServiceHelper.newDynamicObject("aos_mkt_hot_point");
            // 类型
            aosMktHotPoint.set("aos_type", "DES");
            // 申请日期
            aosMktHotPoint.set("aos_applydate", new Date());
            // 申请人取设计需求表制作设计师
            aosMktHotPoint.set("aos_apply", designDyn.get("aos_designby"));
            // SKU
            aosMktHotPoint.set("aos_itemid", itemId);
            // 流程节点
            aosMktHotPoint.set("aos_status", "待确认");
            // 当前操作人 根据品类判断
            setUser(aosMktHotPoint, itemId.getString("id"));
            // 爆品质量打分行
            setLine(aosMktHotPoint, itemId, DESGROUP);
            OperationServiceHelper.executeOperate("save", "aos_mkt_hot_point", new DynamicObject[] {aosMktHotPoint},
                OperateOption.create());
        }
    }

    /**
     * 设置爆品打分行信息
     *
     * @param aosMktHotPoint 爆品打分单据对象
     * @param itemId 物料
     * @param group 类型分组
     */
    private static void setLine(DynamicObject aosMktHotPoint, DynamicObject itemId, String[] group) {
        DynamicObjectCollection aosEntryentityS = aosMktHotPoint.getDynamicObjectCollection("aos_entryentity");
        // 去Listing资产管理下该货号所存在国别
        DynamicObjectCollection orgS = QueryServiceHelper.query("aos_mkt_listing_mana", "aos_orgid",
            new QFilter("aos_itemid.number", QCP.equals, itemId.getString("number")).toArray());
        for (DynamicObject org : orgS) {
            DynamicObject aosEntryentity = aosEntryentityS.addNew();
            // 国别
            aosEntryentity.set("aos_orgid", org.getString("aos_orgid"));
            // 是否优化
            aosEntryentity.set("aos_promot", "否");
            // 设置打分为空
            for (String key : group) {
                aosEntryentity.set(key, null);
            }
        }
    }

    /**
     * 
     * @param aosMktHotPoint 爆品质量打分对象
     * @param itemId 物料id
     */
    private static void setUser(DynamicObject aosMktHotPoint, String itemId) {
        String category = (String)SalUtil.getCategoryByItemId(itemId).get("name");
        String[] categoryGroup = category.split(",");
        String aosCategory1 = null;
        int categoryLength = categoryGroup.length;
        if (categoryLength > 0) {
            aosCategory1 = categoryGroup[0];
        }
        if (HOME.equals(aosCategory1)) {
            aosMktHotPoint.set("aos_user", FndGlobal.getBaseByName("任艇艇", "bos_user"));
        } else {
            aosMktHotPoint.set("aos_user", FndGlobal.getBaseByName("刘欢", "bos_user"));
        }
    }

    /**
     * 爆品打分单创建设计需求表
     * 
     * @param aosItemid 物料
     * @param hotDyn 爆品打分单据对象
     */
    public static void createDesign(DynamicObject aosItemid, DynamicObject hotDyn) {
        // 循环爆品打分行
        DynamicObjectCollection hotLineS = hotDyn.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject hotLine : hotLineS) {
            String aosPromot = hotLine.getString("aos_promot");
            if (NO.equals(aosPromot)) {
                continue;
            }
            String itemId = aosItemid.getString("id");
            String category = CommonMktListing.getItemCateNameZH(itemId);
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
            long aosDesigner = 0;
            long aosDesigneror = 0;
            long aos3d = 0;
            String[] selectFields = new String[] {"aos_eng aos_editor", "aos_designeror", "aos_3d"};
            DynamicObject aosMktProguser =
                CommonMktListing.findDesignerByType(null, aosCategory1, aosCategory2, "四者一致", selectFields);
            if (aosMktProguser != null) {
                aosDesigner = aosMktProguser.getLong("aos_designer");
                aosDesigneror = aosMktProguser.getLong("aos_designeror");
                aos3d = aosMktProguser.getLong("aos_3d");
            }
            DynamicObject aosMktDesignreq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designreq");
            aosMktDesignreq.set("aos_requiredate", new Date());
            aosMktDesignreq.set("aos_type", "四者一致");
            aosMktDesignreq.set("aos_orignbill", hotDyn.getString("billno"));
            aosMktDesignreq.set("aos_sourceid", hotDyn.getPkValue().toString());
            aosMktDesignreq.set("aos_status", "设计");
            aosMktDesignreq.set("aos_user", aosDesigner);
            aosMktDesignreq.set("aos_designer", aosDesigner);
            aosMktDesignreq.set("aos_dm", aosDesigneror);
            aosMktDesignreq.set("aos_orgid", hotLine.getDynamicObject("aos_orgid"));
            aosMktDesignreq.set("aos_3der", aos3d);
            aosMktDesignreq.set("aos_sourcetype", "HOT");
            aosMktDesignreq.set("aos_requireby", hotDyn.get("aos_apply"));
            aosMktDesignreq.set("aos_sourcebilltype", "aos_mkt_hot_point");
            aosMktDesignreq.set("aos_sourcebillno", hotDyn.getString("billno"));
            aosMktDesignreq.set("aos_srcentrykey", "aos_entryentity");
            setOrgnization(aosMktDesignreq, (hotDyn.getDynamicObject("aos_apply")).getPkValue());
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
                int osQty = CommonMktListing.GetItemOsQty(orgId, itemId);
                int safeQty = CommonMktListing.GetSafeQty(orgId);
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
                    if (!aosContrybrandStr.toString().contains(value)) {
                        aosContrybrandStr.append(value).append(";");
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
                    int osQty = CommonMktListing.getOsQty(itemid);
                    if (osQty < 10) {
                        continue;
                    }
                    String number = bd.getString("number");
                    if (!itemNumber.equals(number)) {
                        aosBroitem.append(number).append(";");
                    }
                }
            }
            DynamicObjectCollection aosEntryentityS = aosMktDesignreq.getDynamicObjectCollection("aos_entryentity");
            DynamicObject aosEntryentity = aosEntryentityS.addNew();
            aosEntryentity.set("aos_itemid", itemId);
            aosEntryentity.set("aos_is_saleout", CommonMktListing.Is_saleout(itemId));
            aosEntryentity.set("aos_desreq", "爆品质量打分退回:" + hotLine.getDynamicObject("aos_orgid").getString("number")
                + "," + hotLine.getString("aos_context"));
            aosEntryentity.set("aos_srcrowseq", hotLine.get("SEQ"));
            DynamicObjectCollection aosSubentryentityS =
                aosEntryentity.getDynamicObjectCollection("aos_subentryentity");
            DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
            aosSubentryentity.set("aos_sub_item", itemId);
            aosSubentryentity.set("aos_segment3", aosProductno);
            aosSubentryentity.set("aos_itemname", aosItemname);
            aosSubentryentity.set("aos_brand", aosContrybrandStr);
            aosSubentryentity.set("aos_pic", url);
            // 开发
            aosSubentryentity.set("aos_developer", bdMaterial.get("aos_developer"));
            aosSubentryentity.set("aos_seting1", bdMaterial.get("aos_seting_cn"));
            aosSubentryentity.set("aos_seting2", bdMaterial.get("aos_seting_en"));
            aosSubentryentity.set("aos_spec", bdMaterial.get("aos_specification_cn"));
            aosSubentryentity.set("aos_url", CommonMktListing.GetItemPicture(itemNumber));
            aosSubentryentity.set("aos_broitem", aosBroitem);
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
            CommonMktListing.setEntityValue(aosMktDesignreq);
            CommonMktListing.createDesiginBeforeSave(aosMktDesignreq);
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
                new DynamicObject[] {aosMktDesignreq}, OperateOption.create());
            FndHistory.Create("aos_mkt_designreq", operationrst.getSuccessPkIds().get(0).toString(), "爆品质量打分表生成设计需求",
                "到设计节点");
            // 修复关联关系
            try {
                CommonMktListing.botp("aos_mkt_designreq", aosMktDesignreq.get("id"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 设置组织
     * 
     * @param dyn 要设置的单据对象
     * @param user 对应的人员
     */
    private static void setOrgnization(DynamicObject dyn, Object user) {
        List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(user);
        if (mapList != null) {
            if (mapList.size() >= THREE && mapList.get(TWO) != null) {
                dyn.set("aos_organization1", mapList.get(2).get("id"));
            }
            if (mapList.size() >= FOUR && mapList.get(THREE) != null) {
                dyn.set("aos_organization2", mapList.get(3).get("id"));
            }
        }
    }

    /**
     * 创建拍照需求表
     * 
     * @param aosItemid 物料
     * @param hotDyn 爆品打分表对象
     */
    public static void createPhoto(DynamicObject aosItemid, DynamicObject hotDyn) {
        String fromPhotoId = hotDyn.getString("aos_sourceid");
        DynamicObject fromPhoto = BusinessDataServiceHelper.loadSingle(fromPhotoId, "aos_mkt_photoreq");
        DynamicObjectCollection hotLineS = hotDyn.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject hotLine : hotLineS) {
            String aosPromot = hotLine.getString("aos_promot");
            if (NO.equals(aosPromot)) {
                continue;
            }
            DynamicObject aosMktPhotoReq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_photoreq");
            aosMktPhotoReq.set("aos_itemid", aosItemid);
            aosMktPhotoReq.set("aos_orgid", hotLine.get("aos_orgid"));
            aosMktPhotoReq.set("aos_firstindate", fromPhoto.get("aos_firstindate"));
            aosMktPhotoReq.set("aos_user", fromPhoto.get("aos_vedior"));
            aosMktPhotoReq.set("aos_requireby", hotDyn.get("aos_apply"));
            aosMktPhotoReq.set("aos_requiredate", new Date());
            aosMktPhotoReq.set("aos_type", "视频");
            aosMktPhotoReq.set("aos_status", "视频剪辑");
            aosMktPhotoReq.set("aos_sonflag", false);
            aosMktPhotoReq.set("billno", fromPhoto.get("billno") + "-爆品打分");
            setPhoto(aosMktPhotoReq, fromPhoto);
            setOrgnization(aosMktPhotoReq, (hotDyn.getDynamicObject("aos_apply")).getPkValue());
            // 视频需求单据体
            DynamicObjectCollection aosEntryentity1S = aosMktPhotoReq.getDynamicObjectCollection("aos_entryentity1");
            String vedesc = "爆品质量打分退回:" + hotLine.getDynamicObject("aos_orgid").getString("number") + ","
                + hotLine.getString("aos_context");
            DynamicObject aosEntryentity1 = aosEntryentity1S.addNew();
            aosEntryentity1.set("aos_applyby2", "申请人");
            aosEntryentity1.set("aos_veddesc", vedesc);
            aosEntryentity1 = aosEntryentity1S.addNew();
            aosEntryentity1.set("aos_applyby2", "开发/采购");
            aosEntryentity1.set("aos_veddesc", vedesc);
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq",
                new DynamicObject[] {aosMktPhotoReq}, OperateOption.create());
            FndHistory.Create("aos_mkt_photoreq", operationrst.getSuccessPkIds().get(0).toString(),
                "爆品质量打分表生成拍照需求-" + fromPhoto.getString("billno"), "到视频剪辑");
        }
    }

    /**
     * 根据老拍照单赋值新拍照单
     * 
     * @param aosMktPhotoReq 新拍照单
     * @param fromPhoto 老拍照单
     */
    private static void setPhoto(DynamicObject aosMktPhotoReq, DynamicObject fromPhoto) {
        aosMktPhotoReq.set("billstatus", "A");
        aosMktPhotoReq.set("aos_shipdate", fromPhoto.getDate("aos_shipdate"));
        aosMktPhotoReq.set("aos_urgent", fromPhoto.get("aos_urgent"));
        aosMktPhotoReq.set("aos_photoflag", fromPhoto.get("aos_photoflag"));
        aosMktPhotoReq.set("aos_reason", fromPhoto.get("aos_reason"));
        aosMktPhotoReq.set("aos_sameitemid", fromPhoto.get("aos_sameitemid"));
        aosMktPhotoReq.set("aos_vedioflag", fromPhoto.get("aos_vedioflag"));
        aosMktPhotoReq.set("aos_reqtype", fromPhoto.get("aos_reqtype"));
        aosMktPhotoReq.set("aos_sourceid", fromPhoto.get("aos_sourceid"));
        aosMktPhotoReq.set("aos_is_saleout", fromPhoto.get("aos_is_saleout"));
        aosMktPhotoReq.set("aos_itemname", fromPhoto.get("aos_itemname"));
        aosMktPhotoReq.set("aos_contrybrand", fromPhoto.get("aos_contrybrand"));
        aosMktPhotoReq.set("aos_newitem", fromPhoto.get("aos_newitem"));
        aosMktPhotoReq.set("aos_newvendor", fromPhoto.get("aos_newvendor"));
        aosMktPhotoReq.set("aos_ponumber", fromPhoto.get("aos_ponumber"));
        aosMktPhotoReq.set("aos_linenumber", fromPhoto.get("aos_linenumber"));
        aosMktPhotoReq.set("aos_earlydate", fromPhoto.get("aos_earlydate"));
        aosMktPhotoReq.set("aos_checkdate", fromPhoto.get("aos_checkdate"));
        aosMktPhotoReq.set("aos_specification", fromPhoto.get("aos_specification"));
        aosMktPhotoReq.set("aos_seting1", fromPhoto.get("aos_seting1"));
        aosMktPhotoReq.set("aos_seting2", fromPhoto.get("aos_seting2"));
        aosMktPhotoReq.set("aos_sellingpoint", fromPhoto.get("aos_sellingpoint"));
        aosMktPhotoReq.set("aos_vendor", fromPhoto.get("aos_vendor"));
        aosMktPhotoReq.set("aos_city", fromPhoto.get("aos_city"));
        aosMktPhotoReq.set("aos_contact", fromPhoto.get("aos_contact"));
        aosMktPhotoReq.set("aos_address", fromPhoto.get("aos_address"));
        aosMktPhotoReq.set("aos_phone", fromPhoto.get("aos_phone"));
        aosMktPhotoReq.set("aos_phstate", fromPhoto.get("aos_phstate"));
        aosMktPhotoReq.set("aos_rcvbill", fromPhoto.get("aos_rcvbill"));
        aosMktPhotoReq.set("aos_sampledate", fromPhoto.get("aos_sampledate"));
        aosMktPhotoReq.set("aos_installdate", fromPhoto.get("aos_installdate"));
        aosMktPhotoReq.set("aos_poer", fromPhoto.get("aos_poer"));
        aosMktPhotoReq.set("aos_developer", fromPhoto.get("aos_developer"));
        aosMktPhotoReq.set("aos_follower", fromPhoto.get("aos_follower"));
        aosMktPhotoReq.set("aos_whiteph", fromPhoto.get("aos_whiteph"));
        aosMktPhotoReq.set("aos_actph", fromPhoto.get("aos_actph"));
        aosMktPhotoReq.set("aos_vedior", fromPhoto.get("aos_vedior"));
        aosMktPhotoReq.set("aos_3d", fromPhoto.get("aos_3d"));
        aosMktPhotoReq.set("aos_whitedate", fromPhoto.get("aos_whitedate"));
        aosMktPhotoReq.set("aos_actdate", fromPhoto.get("aos_actdate"));
        aosMktPhotoReq.set("aos_picdate", fromPhoto.get("aos_picdate"));
        aosMktPhotoReq.set("aos_funcpicdate", fromPhoto.get("aos_funcpicdate"));
        aosMktPhotoReq.set("aos_vedio", fromPhoto.get("aos_vedio"));
        aosMktPhotoReq.set("aos_designer", fromPhoto.get("aos_designer"));
        aosMktPhotoReq.set("aos_sale", fromPhoto.get("aos_sale"));
        aosMktPhotoReq.set("aos_desc", fromPhoto.get("aos_desc"));
        aosMktPhotoReq.set("aos_vediotype", fromPhoto.get("aos_vediotype"));
        aosMktPhotoReq.set("aos_orgtext", fromPhoto.get("aos_orgtext"));
        aosMktPhotoReq.set("aos_samplestatus", fromPhoto.get("aos_samplestatus"));
        aosMktPhotoReq.set("aos_quainscomdate", fromPhoto.get("aos_quainscomdate"));

        // 照片需求单据体(新)
        DynamicObjectCollection aosEntryentity5S = aosMktPhotoReq.getDynamicObjectCollection("aos_entryentity5");
        DynamicObjectCollection aosEntryentity5OriS = fromPhoto.getDynamicObjectCollection("aos_entryentity5");
        for (DynamicObject aosEntryentity5Ori : aosEntryentity5OriS) {
            DynamicObject aosEntryentity5 = aosEntryentity5S.addNew();
            aosEntryentity5.set("aos_reqfirst", aosEntryentity5Ori.get("aos_reqfirst"));
            aosEntryentity5.set("aos_reqother", aosEntryentity5Ori.get("aos_reqother"));
            aosEntryentity5.set("aos_detail", aosEntryentity5Ori.get("aos_detail"));
            aosEntryentity5.set("aos_scene1", aosEntryentity5Ori.get("aos_scene1"));
            aosEntryentity5.set("aos_object1", aosEntryentity5Ori.get("aos_object1"));
            aosEntryentity5.set("aos_scene2", aosEntryentity5Ori.get("aos_scene2"));
            aosEntryentity5.set("aos_object2", aosEntryentity5Ori.get("aos_object2"));
        }
        // 照片需求单据体(新2)
        DynamicObjectCollection aosEntryentity6S = aosMktPhotoReq.getDynamicObjectCollection("aos_entryentity6");
        DynamicObjectCollection aosEntryentity6OriS = fromPhoto.getDynamicObjectCollection("aos_entryentity6");
        for (DynamicObject aosEntryentity6Ori : aosEntryentity6OriS) {
            DynamicObject aosEntryentity6 = aosEntryentity6S.addNew();
            aosEntryentity6.set("aos_reqsupp", aosEntryentity6Ori.get("aos_reqsupp"));
            aosEntryentity6.set("aos_devsupp", aosEntryentity6Ori.get("aos_devsupp"));
        }
        // 照片需求单据体
        DynamicObjectCollection aosEntryentityS = aosMktPhotoReq.getDynamicObjectCollection("aos_entryentity");
        DynamicObjectCollection aosEntryentityOriS = fromPhoto.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject aosEntryentityOri : aosEntryentityOriS) {
            DynamicObject aosEntryentity = aosEntryentityS.addNew();
            aosEntryentity.set("aos_applyby", aosEntryentityOri.get("aos_applyby"));
            aosEntryentity.set("aos_picdesc", aosEntryentityOri.get("aos_picdesc"));
        }

        // 拍摄情况单据体
        DynamicObjectCollection aosEntryentity2S = aosMktPhotoReq.getDynamicObjectCollection("aos_entryentity2");
        DynamicObjectCollection aosEntryentityOri2S = fromPhoto.getDynamicObjectCollection("aos_entryentity2");
        for (DynamicObject aosEntryentityOri2 : aosEntryentityOri2S) {
            DynamicObject aosEntryentity2 = aosEntryentity2S.addNew();
            aosEntryentity2.set("aos_phtype", aosEntryentityOri2.get("aos_phtype"));
            aosEntryentity2.set("aos_complete", aosEntryentityOri2.get("aos_complete"));
            aosEntryentity2.set("aos_completeqty", aosEntryentityOri2.get("aos_completeqty"));
        }
        // 流程退回原因单据体
        DynamicObjectCollection aosEntryentity3S = aosMktPhotoReq.getDynamicObjectCollection("aos_entryentity3");
        DynamicObjectCollection aosEntryentityOri3S = fromPhoto.getDynamicObjectCollection("aos_entryentity3");
        for (DynamicObject aosEntryentityOri3 : aosEntryentityOri3S) {
            DynamicObject aosEntryentity3 = aosEntryentity3S.addNew();
            aosEntryentity3.set("aos_returnby", aosEntryentityOri3.get("aos_returnby"));
            aosEntryentity3.set("aos_return", aosEntryentityOri3.get("aos_return"));
            aosEntryentity3.set("aos_returnreason", aosEntryentityOri3.get("aos_returnreason"));
        }
        aosMktPhotoReq.set("aos_phreturn", fromPhoto.get("aos_phreturn"));
        aosMktPhotoReq.set("aos_phreason", fromPhoto.get("aos_phreason"));
        aosMktPhotoReq.set("aos_dereturn", fromPhoto.get("aos_dereturn"));
        aosMktPhotoReq.set("aos_dereason", fromPhoto.get("aos_dereason"));
        // 视频地址单据体
        DynamicObjectCollection aosEntryentity4S = aosMktPhotoReq.getDynamicObjectCollection("aos_entryentity4");
        DynamicObjectCollection aosEntryentityOri4S = fromPhoto.getDynamicObjectCollection("aos_entryentity4");
        for (DynamicObject aosEntryentityOri4 : aosEntryentityOri4S) {
            DynamicObject aosEntryentity4 = aosEntryentity4S.addNew();
            aosEntryentity4.set("aos_orgshort", aosEntryentityOri4.get("aos_orgshort"));
            aosEntryentity4.set("aos_brand", aosEntryentityOri4.get("aos_brand"));
            aosEntryentity4.set("aos_s3address1", aosEntryentityOri4.get("aos_s3address1"));
            aosEntryentity4.set("aos_s3address2", aosEntryentityOri4.get("aos_s3address2"));
            aosEntryentity4.set("aos_editor", aosEntryentityOri4.get("aos_editor"));
            aosEntryentity4.set("aos_salerece_date", new Date());
            aosEntryentityOri4.set("aos_salerece_date", new Date());
        }
    }
}
