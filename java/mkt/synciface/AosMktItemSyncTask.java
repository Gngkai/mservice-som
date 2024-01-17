package mkt.synciface;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.progress.listing.AosMktListingReqBill;

/**
 * @author aosom
 * @version 工作流定时信息同步-调度任务类
 */
public class AosMktItemSyncTask extends AbstractTask {
    public final static String EIPDB = Cux_Common_Utl.EIPDB;

    public static void doOperate() {
        // 物料数据初始化
        HashMap<String, Map<String, String>> itemMap = generateItem();
        // 文案数据初始化
        HashMap<String, String> son = generateSon();
        // 设计需求数据初始化
        HashMap<String, String> design = generateDesign();
        // 1.拍照需求表
        DynamicObject[] aosMktPhotoreqS = BusinessDataServiceHelper.load("aos_mkt_photoreq", "id",
            new QFilter("aos_status", QCP.not_equals, "已完成").toArray());
        for (DynamicObject aosMktPhotoreqId : aosMktPhotoreqS) {
            Object fid = aosMktPhotoreqId.get("id");
            DynamicObject aosMktPhotoreq = BusinessDataServiceHelper.loadSingleFromCache(fid, "aos_mkt_photoreq");
            String item = aosMktPhotoreq.getDynamicObject("aos_itemid").getString("number");
            if (FndGlobal.IsNull(itemMap.get(item))) {
                continue;
            }
            aosMktPhotoreq.set("aos_itemname", itemMap.get(item).get("name"));
            aosMktPhotoreq.set("aos_seting1", itemMap.get(item).get("aos_seting_cn"));
            aosMktPhotoreq.set("aos_specification", itemMap.get(item).get("aos_specification_cn"));
            aosMktPhotoreq.set("aos_sellingpoint", itemMap.get(item).get("aos_sellingpoint"));
            SaveServiceHelper.saveOperate("aos_mkt_photoreq", new DynamicObject[] {aosMktPhotoreq},
                OperateOption.create());
        }
        // 2.设计需求表
        DynamicObject[] aosMktDesignreqS = BusinessDataServiceHelper.load("aos_mkt_designreq", "id",
            new QFilter("aos_status", QCP.not_equals, "结束").toArray());
        for (DynamicObject aosMktDesignreqId : aosMktDesignreqS) {
            Object fid = aosMktDesignreqId.get("id");
            DynamicObject aosMktDesignreq = BusinessDataServiceHelper.loadSingleFromCache(fid, "aos_mkt_designreq");
            DynamicObjectCollection aosEntryentityS = aosMktDesignreq.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                DynamicObjectCollection aosSubentryentityS =
                    aosEntryentity.getDynamicObjectCollection("aos_subentryentity");
                for (DynamicObject aosSubentryentity : aosSubentryentityS) {
                    DynamicObject aosSubItem = aosSubentryentity.getDynamicObject("aos_sub_item");
                    if (FndGlobal.IsNull(aosSubItem)) {
                        continue;
                    }
                    String item = aosSubItem.getString("number");
                    if (FndGlobal.IsNull(itemMap.get(item))) {
                        continue;
                    }
                    aosSubentryentity.set("aos_itemname", itemMap.get(item).get("name"));
                    aosSubentryentity.set("aos_seting1", itemMap.get(item).get("aos_seting_cn"));
                    aosSubentryentity.set("aos_spec", itemMap.get(item).get("aos_specification_cn"));
                    aosSubentryentity.set("aos_sellingpoint", itemMap.get(item).get("aos_sellingpoint"));
                }
            }
            SaveServiceHelper.saveOperate("aos_mkt_designreq", new DynamicObject[] {aosMktDesignreq},
                OperateOption.create());
        }
        // 3.listing优化需求表
        DynamicObject[] aosMktListingReqS =
            BusinessDataServiceHelper.load("aos_mkt_listing_req", "id", new QFilter("aos_status", QCP.equals, "已完成")
                .and("aos_requiredate", QCP.large_equals, LocalDate.now().minusDays(30).toString()).toArray());
        for (DynamicObject aosMktListingReqId : aosMktListingReqS) {
            String fid = aosMktListingReqId.getString("id");
            DynamicObject aosMktListingReq =
                BusinessDataServiceHelper.loadSingleFromCache(aosMktListingReqId.get("id"), "aos_mkt_listing_req");
            if (FndGlobal.IsNull(aosMktListingReq)) {
                continue;
            }
            DynamicObjectCollection aosEntryentityS = aosMktListingReq.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                // 英语文案完成情况
                aosEntryentity.set("aos_enstatus", son.get(fid));
                // DE文案翻译情况
                aosEntryentity.set("aos_destatus", getMin("DE", fid));
                // FR文案翻译情况
                aosEntryentity.set("aos_frstatus", getMin("FR", fid));
                // IT文案翻译情况
                aosEntryentity.set("aos_itstatus", getMin("IT", fid));
                // ES文案翻译情况
                aosEntryentity.set("aos_esstatus", getMin("ES", fid));
                // 品类设计完成情况
                aosEntryentity.set("aos_signstatus", design.get(fid));
                // DE图片翻译情况
                aosEntryentity.set("aos_depic", getPic("DE", fid));
                // FR图片翻译情况
                aosEntryentity.set("aos_frpic", getPic("FR", fid));
                // IT图片翻译情况
                aosEntryentity.set("aos_itpic", getPic("IT", fid));
                // ES图片翻译情况
                aosEntryentity.set("aos_espic", getPic("ES", fid));
            }
            AosMktListingReqBill.setItemCate(aosMktListingReq);
            SaveServiceHelper.saveOperate("aos_mkt_listing_req", new DynamicObject[] {aosMktListingReq},
                OperateOption.create());
        }
    }

    private static Object getMin(String org, String fid) {
        Object status = null;
        String sql = " select taml.fk_aos_status from  tk_aos_mkt_listing_min taml,"
            + " tk_aos_mkt_listing_son tamls  ,tk_aos_mkt_listing_req tamlq," + EIPDB + ".T_BD_Country tbc"
            + " where 1=1 " + " and tamls.fid = taml.fk_aos_sourceid " + " and tamlq.fid = tamls.fk_aos_sourceid "
            + " and tbc.fid = taml.fk_aos_orgid " + " and tbc.fnumber = ? " + " and tamlq.fid = ? ";
        Object[] params = {org, fid};
        DataSet ds = DB.queryDataSet("getPic" + org, DBRoute.of("aos.mkt"), sql, params);
        while (ds.hasNext()) {
            Row row = ds.next();
            status = row.get(0);
        }
        ds.close();
        return status;
    }

    /**
     * 获取图片翻译情况
     */
    private static Object getPic(String org, Object fid) {
        Object status = null;
        String sql = " select tamd.fk_aos_status from tk_aos_mkt_designreq tamd," + " tk_aos_mkt_listing_min taml,"
            + " tk_aos_mkt_designcmp tamdc," + " tk_aos_mkt_designreq tamd2," + " tk_aos_mkt_listing_req tamlq," + EIPDB
            + ".T_BD_Country tbc" + " where 1=1 " + " and taml.fid = tamd.fk_aos_sourceid "
            + " and tamdc.fid = taml.fk_aos_sourceid " + " and tamd2.fid = tamdc.fk_aos_sourceid "
            + " and tamlq.fid = tamd2.fk_aos_sourceid " + " and tbc.fid = tamdc.fk_aos_orgid " + " and tbc.fnumber = ? "
            + " and tamlq.fid = ? ";
        Object[] params = {org, fid};
        DataSet ds = DB.queryDataSet("getPic" + org, DBRoute.of("aos.mkt"), sql, params);
        while (ds.hasNext()) {
            Row row = ds.next();
            status = row.get(0);
        }
        return status;
    }

    /**
     * 设计需求初始化
     */
    private static HashMap<String, String> generateDesign() {
        HashMap<String, String> design = new HashMap<>(16);
        DynamicObjectCollection aosMktDesignreqS =
            QueryServiceHelper.query("aos_mkt_designreq", "aos_sourceid,aos_status", null);
        for (DynamicObject aosMktDesignreq : aosMktDesignreqS) {
            design.put(aosMktDesignreq.getString("aos_sourceid"), aosMktDesignreq.getString("aos_status"));
        }
        return design;
    }

    /**
     * 文案数据初始化
     */
    private static HashMap<String, String> generateSon() {
        HashMap<String, String> son = new HashMap<>(16);
        DynamicObjectCollection aosMktListingSonS =
            QueryServiceHelper.query("aos_mkt_listing_son", "aos_sourceid,aos_status", null);
        for (DynamicObject aosMktListingSon : aosMktListingSonS) {
            son.put(aosMktListingSon.getString("aos_sourceid"), aosMktListingSon.getString("aos_status"));
        }
        return son;
    }

    /**
     * 物料数据初始化
     */
    public static HashMap<String, Map<String, String>> generateItem() {
        HashMap<String, Map<String, String>> item = new HashMap<>(16);
        String selectColumn = "number,name,aos_seting_cn,aos_specification_cn,aos_sellingpoint";
        DataSet bdMaterialS = QueryServiceHelper.queryDataSet("aos_mkt_item_sync.generateItem", "bd_material",
            selectColumn, new QFilter("aos_protype", QCP.equals, "N").toArray(), null);
        while (bdMaterialS.hasNext()) {
            Row bdMaterial = bdMaterialS.next();
            HashMap<String, String> info = new HashMap<>(16);
            info.put("name", bdMaterial.getString("name"));
            info.put("aos_seting_cn", bdMaterial.getString("aos_seting_cn"));
            info.put("aos_specification_cn", bdMaterial.getString("aos_specification_cn"));
            info.put("aos_sellingpoint", bdMaterial.getString("aos_sellingpoint"));
            item.put(bdMaterial.getString("number"), info);
        }
        bdMaterialS.close();
        return item;
    }

    @Override
    public void execute(RequestContext arg0, Map<String, Object> arg1) throws KDException {
        doOperate();
    }

}