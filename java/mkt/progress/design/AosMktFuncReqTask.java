package mkt.progress.design;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.google.common.base.Joiner;
import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import common.sal.util.SaveUtils;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.DBServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import common.sal.util.QFBuilder;
import mkt.progress.design.functdiagram.FunctDiagramBill;
import mkt.progress.iface.ItemInfoUtil;

/**
 * @author aosom
 * @version MKT_功能图需求表初始化-调度任务类
 */
public class AosMktFuncReqTask extends AbstractTask {
    public final static int SEVEN = 7;
    public final static String IE = "IE";
    public final static String C = "C";
    public final static String AOS_CONTRYENTRYSTATUS = "aos_contryentrystatus";
    public final static String AOS_CONTRYENTRY = "aos_contryentry";
    public final static String AOS_SUBMATERIALENTRY = "aos_submaterialentry";
    /** 添加图片行 **/
    static List<String> list_lan = FunctDiagramBill.list_language;
    public static void doOperate() {
        // 获取已存在的产品号
        List<String> createProductNo = generateFuncReq();
        QFilter filterProd = new QFilter("aos_productno", "!=", null).and("aos_productno", "!=", "");
        QFilter[] filtersProd = new QFilter[] {filterProd};
        String selectColumn = "aos_productno,id";
        DataSet bdMaterialS = QueryServiceHelper.queryDataSet("aos_mkt_funcreq_init.do_operate", "bd_material",
            selectColumn, filtersProd, null);
        String[] groupBy = new String[] {"aos_productno"};
        bdMaterialS = bdMaterialS.groupBy(groupBy).min("id").finish();
        List<DynamicObject> listSave = new ArrayList<>();
        while (bdMaterialS.hasNext()) {
            Row bdMaterial = bdMaterialS.next();
            String aosProductno = bdMaterial.getString("aos_productno");
            if (createProductNo.contains(aosProductno)) {
                continue;
            }
            // 下单国别与品类编辑
            Object itemId = bdMaterial.get("id");
            Object aosEditordefualt = queryEditor(itemId);
            // 数据初始化
            DynamicObject aosMktFunctreq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_functreq");
            aosMktFunctreq.set("aos_segment3", aosProductno);
            aosMktFunctreq.set("aos_editor", aosEditordefualt);
            aosMktFunctreq.set("aos_makeby", aosEditordefualt);
            aosMktFunctreq.set("aos_picitem", itemId);
            aosMktFunctreq.set("billstatus", "A");
            DynamicObjectCollection aosEntryentity2S = aosMktFunctreq.getDynamicObjectCollection("aos_entryentity2");
            // 同产品号下货号
            QFilter filterProductno = new QFilter("aos_productno", "=", aosProductno);
            QFilter[] filters = new QFilter[] {filterProductno};
            DynamicObjectCollection bdMaterialSameS = QueryServiceHelper.query("bd_material", "id", filters);
            List<String> listOrgtextTotal = new ArrayList<>();
            for (DynamicObject bdMaterialSame : bdMaterialSameS) {
                Object itemIdSame = bdMaterialSame.get("id");
                List<String> listOrgtext = new ArrayList<>();
                DynamicObject material = BusinessDataServiceHelper.loadSingle(itemIdSame, "bd_material");
                DynamicObjectCollection aosContryentryS = material.getDynamicObjectCollection("aos_contryentry");
                // 获取所有国家品牌 字符串拼接 终止
                for (DynamicObject aosContryentry : aosContryentryS) {
                    addItemOrg(aosContryentry, itemIdSame, listOrgtext, listOrgtextTotal);
                }
                DynamicObject aosEntryentity2 = aosEntryentity2S.addNew();
                aosEntryentity2.set("aos_itemid", material);
                aosEntryentity2.set("aos_orgdetail", Joiner.on(";").join(listOrgtext));
            }
            aosMktFunctreq.set("aos_orgtext", Joiner.on(";").join(listOrgtextTotal));
            setIamgTable(aosMktFunctreq);
            listSave.add(aosMktFunctreq);
            SaveUtils.SaveEntity("aos_mkt_functreq", listSave, false);
        }
        bdMaterialS.close();
        SaveUtils.SaveEntity("aos_mkt_functreq", listSave, true);
        generateFuncReqByCombinate(createProductNo);
    }

    private static List<String> generateFuncReq() {
        return QueryServiceHelper.query("aos_mkt_functreq", "aos_segment3", null).stream()
            .filter(dy -> !Cux_Common_Utl.IsNull(dy.get("aos_segment3"))).map(dy -> dy.getString("aos_segment3"))
            .distinct().collect(Collectors.toList());
    }

    /**
     * 生成功能图通过组别数据
     * 
     * @param createProductNo 已经生成的产品号
     */
    public static void generateFuncReqByCombinate(List<String> createProductNo) {
        DynamicObject[] dycCombinate = queryCombinate(createProductNo);
        List<DynamicObject> listSave = new ArrayList<>(5000);
        for (DynamicObject dyItem : dycCombinate) {
            String aosProductno = dyItem.getString("number");
            List<String> itemids = getItemids(dyItem);
            if (itemids.size() == 0) {
                continue;
            }
            String itemId = itemids.get(0);
            Object aosEditordefualt = queryEditor(itemId);
            // 数据初始化
            DynamicObject aosMktFunctreq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_functreq");
            aosMktFunctreq.set("aos_segment3", aosProductno);
            aosMktFunctreq.set("aos_editor", aosEditordefualt);
            aosMktFunctreq.set("aos_makeby", aosEditordefualt);
            aosMktFunctreq.set("aos_picitem", itemId);
            aosMktFunctreq.set("billstatus", "A");
            // 同产品号下货号
            DynamicObjectCollection aosEntryentity2S = aosMktFunctreq.getDynamicObjectCollection("aos_entryentity2");
            List<String> listOrgtextTotal = new ArrayList<>();
            for (String itemIdSame : itemids) {
                List<String> listOrgtext = new ArrayList<>();
                DynamicObject material = BusinessDataServiceHelper.loadSingle(itemIdSame, "bd_material");
                DynamicObjectCollection aosContryentryS = material.getDynamicObjectCollection("aos_contryentry");
                // 获取所有国家品牌 字符串拼接 终止
                for (DynamicObject aosContryentry : aosContryentryS) {
                    addItemOrg(aosContryentry, itemIdSame, listOrgtext, listOrgtextTotal);
                }
                DynamicObject aosEntryentity2 = aosEntryentity2S.addNew();
                aosEntryentity2.set("aos_itemid", material);
                aosEntryentity2.set("aos_orgdetail", Joiner.on(";").join(listOrgtext));
            }
            aosMktFunctreq.set("aos_orgtext", Joiner.on(";").join(listOrgtextTotal));
            // 设置图片页签
            setIamgTable(aosMktFunctreq);
            listSave.add(aosMktFunctreq);
            SaveUtils.SaveEntity("aos_mkt_functreq", listSave, false);
        }
        SaveUtils.SaveEntity("aos_mkt_functreq", listSave, true);
    }

    /**
     * 查找所有的组合货号
     * 
     * @param createProductNo 已经生成的产品号
     * @return 查找所有的组合货号
     */
    public static DynamicObject[] queryCombinate(List<String> createProductNo) {
        QFBuilder builder = new QFBuilder();
        builder.add("number", QFilter.not_in, createProductNo);
        builder.add("aos_iscomb", "=", "1");
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        str.add("number");
        str.add("aos_contryentry.aos_nationality");
        str.add("aos_submaterialentry.aos_submaterial");
        return BusinessDataServiceHelper.load("bd_material", str.toString(), builder.toArray());
    }

    /**
     * 获取品类编辑
     * 
     * @param itemId id
     * @return 获取品类编辑
     */
    public static long queryEditor(Object itemId) {
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
        long aosEditordefualt = 0;
        if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
            QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
            QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
            QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2};
            String selectStr = "aos_eng aos_editor,aos_designer";
            DynamicObject aosMktProguser = QueryServiceHelper.queryOne("aos_mkt_proguser", selectStr, filtersCategory);
            if (aosMktProguser != null) {
                aosEditordefualt = aosMktProguser.getLong("aos_editor");
            }
        }
        return aosEditordefualt;
    }

    private static void setIamgTable(DynamicObject aosMktFunctreq) {
        // 主标题
        LocaleString localeTitle = new LocaleString();
        localeTitle.setLocaleValue_zh_CN("主标题");
        localeTitle.setLocaleValue_en("Main tTitle");
        LocaleString localValue = new LocaleString();
        localValue.setLocaleValue_zh_CN("作图备注");
        localValue.setLocaleValue_en("Note");
        // 副标题
        LocaleString localHead = new LocaleString();
        localHead.setLocaleValue_en("Sub title1");
        localHead.setLocaleValue_zh_CN("副标题1");
        LocaleString localValue2 = new LocaleString();
        localValue2.setLocaleValue_zh_CN("正文1");
        localValue2.setLocaleValue_en("Text1");
        // 1-6 的图片table
        for (int index = 1; index < SEVEN; index++) {
            DynamicObjectCollection dycEnt = aosMktFunctreq.getDynamicObjectCollection("aos_entity" + index);
            int seq = 1;
            for (int n = 1; n <= list_lan.size(); n++) {
                DynamicObject dyNewRow = dycEnt.addNew();
                dyNewRow.set("seq", seq);
                seq++;
                // 语言
                dyNewRow.set("aos_language" + index, list_lan.get(n - 1));
                // 主标题
                dyNewRow.set("aos_head" + index, localeTitle);
                // 作图备注
                dyNewRow.set("aos_title" + index, localValue);
                // 语言行
                dyNewRow.set("aos_lan" + index, list_lan.get(n - 1));
                long pid = DBServiceHelper.genLongIds("tk_aos_mkt_functreq_r" + index, 1)[0];
                dyNewRow.set("id", pid);
                dyNewRow.set("pid", 0);
                // 副标题和中文
                DynamicObject dySubRow = dycEnt.addNew();
                dySubRow.set("seq", seq);
                seq++;
                dySubRow.set("pid", pid);
                dySubRow.set("aos_head" + index, localHead);
                dySubRow.set("aos_title" + index, localValue2);
                dySubRow.set("aos_lan" + index, list_lan.get(n - 1));
            }
        }
    }

    /** 判断物料的国别是否应该加入到下单国别 **/
    public static void addItemOrg(DynamicObject aosContryentry, Object itemId, List<String> listOrg,
        List<String> listAllOrg) {
        DynamicObject aosNationality = aosContryentry.getDynamicObject("aos_nationality");
        String aosNationalitynumber = aosNationality.getString("number");
        if (IE.equals(aosNationalitynumber)) {
            return;
        }
        Object orgId = aosNationality.get("id");
        int osQty = ItemInfoUtil.getItemOsQty(orgId, itemId);
        int safeQty = ItemInfoUtil.getSafeQty(orgId);
        if (C.equals(aosContryentry.getString(AOS_CONTRYENTRYSTATUS)) && osQty < safeQty) {
            return;
        }
        if (!listOrg.contains(aosNationalitynumber)) {
            listOrg.add(aosNationalitynumber);
        }
        if (!listAllOrg.contains(aosNationalitynumber)) {
            listAllOrg.add(aosNationalitynumber);
        }
    }

    /**
     * 获取组合货号下，第一个sku
     *
     **/
    private static List<String> getItemids(DynamicObject dyItem) {
        List<String> itemids = new ArrayList<>();
        for (DynamicObject dyRow : dyItem.getDynamicObjectCollection(AOS_CONTRYENTRY)) {
            for (DynamicObject dySub : dyRow.getDynamicObjectCollection(AOS_SUBMATERIALENTRY)) {
                if (dySub.get("aos_submaterial") != null) {
                    String item = dySub.getDynamicObject("aos_submaterial").getString("id");
                    if (!itemids.contains(item)) {
                        itemids.add(item);
                    }
                }
            }
        }
        return itemids;
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        doOperate();
    }
}