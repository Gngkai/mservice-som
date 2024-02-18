package mkt.synciface;

import common.CommonDataSom;
import common.CommonDataSomQuo;
import common.Cux_Common_Utl;
import common.fnd.FndHistory;
import common.sal.util.SalUtil;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.exception.ErrorCode;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.common.MktComUtil;
import mkt.common.MktS3Pic;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.ItemInfoUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author create by gk
 * @since 2022/12/5 14:36
 * @version 每周一定时自动生成综合帖任务的设计需求表；-调度任务类
 */
public class AosMktSyncCreateDesignTask extends AbstractTask {
    public final static String AOS_PRODUCTNO = "aos_productno";
    public final static int TWO = 2;
    public final static int THREE = 3;

    public static void sync(DynamicObjectCollection aosSyncLogS) {
        DynamicObjectCollection dycShipItem = queryShipItem();
        // 已经生成过的
        List<String> createOrgItem = findCreateOrgItem();
        for (DynamicObject dyItem : dycShipItem) {
            StringJoiner logRow = new StringJoiner(" ; ");
            logRow.add(dyItem.getString("orgNumber"));
            logRow.add(dyItem.getString("itemNumber"));
            List<DynamicObject> listSku = new ArrayList<>();
            if (judgeItem(dyItem, listSku, logRow) && judgeDesign(dyItem, logRow)) {
                logRow.add("生成单据");
                String key = dyItem.getString("aos_org") + "/" + dyItem.getString("aos_item");
                if (createOrgItem.contains(key)) {
                    logRow.add("已经生成过了");
                    continue;
                }
                createMultipleEntity(dyItem);
            }
            MktComUtil.putSyncLog(aosSyncLogS, logRow.toString());
        }
    }

    /** ①查询备货单中，过去7天内出运的SKU **/
    private static DynamicObjectCollection queryShipItem() {
        LocalDate now = LocalDate.now();
        QFilter filterFrom = new QFilter("aos_shipmentdate", ">=", now.minusDays(7).toString());
        QFilter filterTo = new QFilter("aos_shipmentdate", "<", now.toString());
        QFilter[] qfs = new QFilter[] {filterFrom, filterTo};
        StringJoiner str = new StringJoiner(",");
        str.add("aos_soldtocountry aos_org");
        str.add("aos_soldtocountry.number orgNumber");
        str.add("aos_stockentry.aos_sto_artno aos_item");
        str.add("aos_stockentry.aos_sto_artno.number itemNumber");
        return QueryServiceHelper.query("aos_preparationnote", str.toString(), qfs);
    }

    /** 判断对应产品号下某国别有2个以上SKU（剔除终止、异常；且供应链小于10） **/
    private static boolean judgeItem(DynamicObject dyItem, List<DynamicObject> listOther, StringJoiner str) {
        String aosOrg = dyItem.getString("aos_org");
        String aosItem = dyItem.getString("aos_item");
        // 查找产品号
        QFilter filterId = new QFilter("id", "=", aosItem);
        DynamicObject dyNo = QueryServiceHelper.queryOne("bd_material", "aos_productno", new QFilter[] {filterId});
        if (dyNo == null || dyNo.get(AOS_PRODUCTNO) == null) {
            str.add("产品号为空剔除");
            return false;
        }
        String aosProductno = dyNo.getString(AOS_PRODUCTNO);
        QFilter filterNo = new QFilter("aos_productno", "=", aosProductno);
        QFilter filterOrg = new QFilter("aos_contryentry.aos_nationality", "=", aosOrg);
        List<String> listStatus = Arrays.asList("C", "D");
        QFilter filterStatus = new QFilter("aos_contryentry.aos_contryentrystatus", QFilter.not_in, listStatus);
        // 同一产品号的物料
        List<QFilter> materialFilter = SalUtil.get_MaterialFilter();
        materialFilter.add(filterNo);
        materialFilter.add(filterStatus);
        materialFilter.add(filterOrg);
        int size = materialFilter.size();
        QFilter[] qfs = materialFilter.toArray(new QFilter[size]);
        DynamicObjectCollection dycOtherItem = QueryServiceHelper.query("bd_material", "id,number", qfs);
        if (dycOtherItem.size() < TWO) {
            str.add("同产品号物料小于2剔除");
            return false;
        }
        // 查找供应链数量
        ArrayList<String> arrItemid = (ArrayList<String>)dycOtherItem.stream().map(dy -> dy.getString("id")).distinct()
            .collect(Collectors.toList());
        Map<String, Integer> mapDomesticAndInPro =
            CommonDataSom.getScmQty(common.scmQtyType.domesticAndInProcess, aosOrg, arrItemid);
        Map<String, Integer> mapOversea = CommonDataSom.getScmQty(common.scmQtyType.oversea, aosOrg, arrItemid);
        Map<String, Integer> mapOnhand = CommonDataSom.getScmQty(common.scmQtyType.onHand, aosOrg, arrItemid);
        for (DynamicObject dyProductNo : dycOtherItem) {
            String itemid = dyProductNo.getString("id");
            int scmQty = 0;
            scmQty += mapDomesticAndInPro.getOrDefault(itemid, 0);
            scmQty += mapOversea.getOrDefault(itemid, 0);
            scmQty += mapOnhand.getOrDefault(itemid, 0);
            if (scmQty >= 10) {
                listOther.add(dyProductNo);
            }
        }
        if (listOther.size() > TWO) {
            return true;
        } else {
            str.add("同产品号供应链数量合格物料小于2剔除");
            return false;
        }
    }

    /** SKU对设计需求表，存在已完成的流程 **/
    private static boolean judgeDesign(DynamicObject dyItem, StringJoiner str) {
        String aosItem = dyItem.getString("aos_item");
        QFilter filterItem = new QFilter("aos_entryentity.aos_itemid", "=", aosItem);
        QFilter filterStatus = new QFilter("aos_status", "=", "结束");
        boolean exists = QueryServiceHelper.exists("aos_mkt_designreq", new QFilter[] {filterItem, filterStatus});
        str.add("设计需求表存在： " + exists);
        return exists;
    }

    /** 生成设计需求表 **/
    private static void createEntity(DynamicObject dyItem, List<DynamicObject> listOther) {
        String aosOrg = dyItem.getString("aos_org");
        String aosItem = dyItem.getString("aos_item");
        DynamicObject dyDesignReq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designreq");
        Date dateNow = new Date();
        dyDesignReq.set("aos_requireby", Cux_Common_Utl.SYSTEM);
        dyDesignReq.set("aos_status", "设计");
        // 申请日期
        dyDesignReq.set("aos_requiredate", dateNow);
        dyDesignReq.set("aos_orgid", aosOrg);
        dyDesignReq.set("aos_type", "其他");
        dyDesignReq.set("aos_source", "综合帖");
        // 设计师
        String category = MktComUtil.getItemCateNameZh(aosItem);
        String[] categoryGroup = category.split(",");
        String[] fields = new String[] {"aos_designeror", "aos_3d", "aos_eng"};
        DynamicObject dyDesign =
            ProgressUtil.findDesignerByType(aosOrg, categoryGroup[0], categoryGroup[1], "其他", fields);
        Object aosDesign = dyDesign.get("aos_designer");
        dyDesignReq.set("aos_designer", aosDesign);
        dyDesignReq.set("aos_user", aosDesign);
        dyDesignReq.set("aos_dm", dyDesign.get("aos_designeror"));
        dyDesignReq.set("aos_3der", dyDesign.get("aos_3d"));
        // 组织
        List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(aosDesign);
        if (mapList != null) {
            if (mapList.get(TWO) != null) {
                dyDesignReq.set("aos_organization1", mapList.get(2).get("id"));
            }
            if (mapList.get(THREE) != null) {
                dyDesignReq.set("aos_organization2", mapList.get(3).get("id"));
            }
        }
        // 明细行
        DynamicObjectCollection dycDesignEnt = dyDesignReq.getDynamicObjectCollection("aos_entryentity");
        DynamicObject dyNewRow = dycDesignEnt.addNew();
        dyNewRow.set("aos_itemid", aosItem);
        dyNewRow.set("aos_is_saleout", ProgressUtil.Is_saleout(aosItem));
        dyNewRow.set("aos_desreq", fillDesreq(listOther));
        // 子单据体
        DynamicObject dySubRow = dyNewRow.getDynamicObjectCollection("aos_subentryentity").addNew();
        StringBuilder aosContrybrandStr = new StringBuilder();
        StringBuilder aosOrgtext = new StringBuilder();
        DynamicObject bdMaterial = BusinessDataServiceHelper.loadSingle(aosItem, "bd_material");
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
            int osQty = ItemInfoUtil.getItemOsQty(orgId, aosItem);
            int safeQty = ItemInfoUtil.getSafeQty(orgId);
            int onQty = CommonDataSomQuo.get_on_hand_qty(Long.parseLong(orgId.toString()), Long.parseLong(aosItem));
            osQty += onQty;
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
        // 图片字段
        String url = "https://clss.s3.amazonaws.com/" + itemNumber + ".jpg";
        String aosProductno = bdMaterial.getString("aos_productno");
        String aosItemname = bdMaterial.getString("name");
        // 获取同产品号物料
        QFilter filterProductno = new QFilter("aos_productno", QCP.equals, aosProductno);
        QFilter[] filters = new QFilter[] {filterProductno};
        StringBuilder aosBroitem = new StringBuilder();
        DynamicObjectCollection bdMaterialS = QueryServiceHelper.query("bd_material", "number,aos_type", filters);
        for (DynamicObject bd : bdMaterialS) {
            if ("B".equals(bd.getString("aos_type"))) {
                continue;
            }
            String number = bd.getString("number");
            if (!itemNumber.equals(number)) {
                aosBroitem.append(number).append(";");
            }
        }
        dySubRow.set("aos_sub_item", aosItem);
        dySubRow.set("aos_segment3", aosProductno);
        dySubRow.set("aos_itemname", aosItemname);
        dySubRow.set("aos_brand", aosContrybrandStr);
        dySubRow.set("aos_pic", url);
        dySubRow.set("aos_developer", bdMaterial.get("aos_developer"));
        dySubRow.set("aos_seting1", bdMaterial.get("aos_seting_cn"));
        dySubRow.set("aos_seting2", bdMaterial.get("aos_seting_en"));
        dySubRow.set("aos_spec", bdMaterial.get("aos_specification_cn"));
        dySubRow.set("aos_url", MktS3Pic.getItemPicture(itemNumber));
        dySubRow.set("aos_broitem", aosBroitem);
        dySubRow.set("aos_orgtext", aosOrgtext);
        dySubRow.set("aos_sellingpoint", bdMaterial.get("aos_sellingpoint"));
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
            dySubRow.set("aos_productstyle_new", productStyle.toString());
        }
        dySubRow.set("aos_shootscenes", bdMaterial.getString("aos_shootscenes"));

        OperationResult result = SaveServiceHelper.saveOperate("aos_mkt_designreq", new DynamicObject[] {dyDesignReq},
            OperateOption.create());
        List<Object> successPkIds = result.getSuccessPkIds();
        if (successPkIds.size() > 0) {
            DynamicObject dyTemp = BusinessDataServiceHelper.loadSingle(successPkIds.get(0), "aos_mkt_designreq");
            FndHistory.Create(dyTemp, "提交", "自动帖生成");
            FndHistory.Create(dyTemp, "提交", "设计");
        }
    }

    /** 生成综合贴国别物料清单 **/
    private static void createMultipleEntity(DynamicObject dyItem) {
        DynamicObject dyMultiple = BusinessDataServiceHelper.newDynamicObject("aos_mkt_multiple");
        String orgNumber = dyItem.getString("orgNumber");
        String itemNumber = dyItem.getString("itemNumber");
        dyMultiple.set("aos_org", dyItem.getString("aos_org"));
        dyMultiple.set("aos_sku", dyItem.getString("aos_item"));
        dyMultiple.set("billstatus", "A");
        OperationResult result =
            SaveServiceHelper.saveOperate("aos_mkt_multiple", new DynamicObject[] {dyMultiple}, OperateOption.create());
        if (!result.isSuccess()) {
            throw new KDException(new ErrorCode(" MMS综合贴清单保存失败： " + orgNumber + "  " + itemNumber,
                result.getAllErrorOrValidateInfo().get(0).getMessage()));
        }
    }

    private static String fillDesreq(List<DynamicObject> listOther) {
        StringBuilder builder = new StringBuilder();
        builder.append("综合帖需求，本国别下单SKU：");
        StringJoiner str = new StringJoiner("、");
        for (int i = 0; i <= TWO; i++) {
            str.add(listOther.get(i).getString("number"));
        }
        builder.append(str);
        builder.append(";");
        return builder.toString();
    }

    /** 查找已经生成的综合贴的国别物料 **/
    private static List<String> findCreateOrgItem() {
        return QueryServiceHelper.query("aos_mkt_multiple", "aos_org,aos_sku", null).stream()
            .map(dy -> dy.getString("aos_org") + "/" + dy.getString("aos_sku")).distinct().collect(Collectors.toList());
    }

    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        DynamicObject aosSyncLog = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
        aosSyncLog.set("aos_type_code", "生成综合帖任务的设计需求表");
        aosSyncLog.set("aos_groupid", LocalDateTime.now());
        aosSyncLog.set("billstatus", "A");
        DynamicObjectCollection aosSyncLogS = aosSyncLog.getDynamicObjectCollection("aos_entryentity");
        sync(aosSyncLogS);
        SaveServiceHelper.save(new DynamicObject[] {aosSyncLog});
    }
}
