package mkt.progress.listing.manager;

import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author aosom
 * 数字资产管理初始化调度任务类
 */
public class ListingManaInitTask extends AbstractTask {

    /**
     * 亚马逊链接
     */
    private static Map<String, String> itemAmUrlMap;

    /**
     * CL链接
     */
    private static Map<String, Map<String, String>> clUrlMap;

    /**
     * CL埋词个数
     */
    private static Map<String, Integer> clKeyWordMap;

    /**
     * AM属性字段填写率
     */
    private static Map<String, BigDecimal> clCateMap;

    /**
     * AM属性字段填写率
     */
    private static Map<String, Date> clDateMap;

    /**
     * 物料图片
     */
    private static Map<String, String> itemPictureMap;

    /**
     * 拍照需求表
     */
    private static Map<String, Map<String, String>> photoMap;

    /**
     * 3D产品设计单
     */
    private static Set<String> design3DSet;
    /**
     * 物料基本分类
     */
    private static Map<String, String> categoryIdMap;

    /**
     * 高级A+需求单已结束国别物料
     */
    private static Set<String> aaddMap;

    /**
     * 物料对应人员信息
     */
    private static Map<String, String> itemUserMap;

    /**
     * 品类人员对应表
     */
    private static Map<String, String> cateUserMap;

    /**
     * 国别品类人员对应表
     */
    private static Map<String, String> orgCateUserMap;

    /**
     * 根据国别物料创建 Listing数字资产管理
     *
     * @param listManaDyn Listing数字资产管理对象
     * @param orgId       国别
     * @param itemId      物料
     * @param aosOrgNum   国别编码
     */
    public static void listManaCreate(DynamicObject listManaDyn, String orgId, String itemId, String aosOrgNum) {
        listManaDyn.set("aos_orgid", orgId);
        listManaDyn.set("aos_itemid", itemId);
        listManaDyn.set("aos_purer", itemUserMap.get(itemId + "~PUR"));
        listManaDyn.set("aos_follower", itemUserMap.get(itemId + "~FOL"));
//        listManaDyn.set("aos_picture", itemPictureMap.get(itemId));
        listManaDyn.set("aos_amurl", itemAmUrlMap.get(orgId + "~" + itemId));
        listManaDyn.set("aos_qty", clKeyWordMap.get(orgId + "~" + itemId));
        listManaDyn.set("aos_amrate", clCateMap.get(orgId + "~" + itemId));
        listManaDyn.set("aos_onlinedate", clDateMap.get(orgId + "~" + itemId));


        if (aaddMap.contains(aosOrgNum + "~" + itemId)) {
            listManaDyn.set("aos_aadd", true);
        }
        if (design3DSet.contains(itemId)) {
            listManaDyn.set("aos_3d", true);
        }
        Map<String, String> photoInfo = photoMap.get(itemId);
        if (FndGlobal.IsNotNull(photoInfo)) {
            listManaDyn.set("aos_vediocate", photoInfo.get("aos_vediocate"));
            listManaDyn.set("aos_veditem", photoInfo.get("aos_veditem"));
            listManaDyn.set("aos_s3address1", photoInfo.get(aosOrgNum + "aos_s3address1"));
            listManaDyn.set("aos_s3address2", photoInfo.get(aosOrgNum + "aos_s3address2"));
            listManaDyn.set("aos_filename", photoInfo.get(aosOrgNum + "aos_filename"));
        }
        String group = categoryIdMap.get(itemId);
        String[] split = group.split(",");
        if (split.length == 3) {
            String aosCate1 = split[0];
            String aosCate2 = split[1];
            String aosCate3 = split[2];
            String cateKey = aosCate1 + "~" + aosCate2;
            listManaDyn.set("aos_category1", aosCate1);
            listManaDyn.set("aos_category2", aosCate2);
            listManaDyn.set("aos_category3", aosCate3);
            listManaDyn.set("aos_editor", cateUserMap.get(cateKey + "~EDI"));
            listManaDyn.set("aos_designer", cateUserMap.get(cateKey + "~DES"));
            listManaDyn.set("aos_us_sale", orgCateUserMap.get("US~" + cateKey));
            listManaDyn.set("aos_ca_sale", orgCateUserMap.get("CA~" + cateKey));
            listManaDyn.set("aos_uk_sale", orgCateUserMap.get("UK~" + cateKey));
            listManaDyn.set("aos_de_sale", orgCateUserMap.get("DE~" + cateKey));
            listManaDyn.set("aos_fr_sale", orgCateUserMap.get("FR~" + cateKey));
            listManaDyn.set("aos_it_sale", orgCateUserMap.get("IT~" + cateKey));
            listManaDyn.set("aos_es_sale", orgCateUserMap.get("ES~" + cateKey));
        }
        DynamicObjectCollection aosPointS = listManaDyn.getDynamicObjectCollection("aos_pointentity");
        aosPointS.clear();
        aosPointS.addNew();

        DynamicObjectCollection aosUrlS = listManaDyn
                .getDynamicObjectCollection("aos_urlentity");
        aosUrlS.clear();
        Map<String, String> itemClUrlMap = clUrlMap.get(orgId + "~" + itemId);
        if (FndGlobal.IsNotNull(itemClUrlMap)){
            for (String key : itemClUrlMap.keySet()) {
                DynamicObject aosUrl = aosUrlS.addNew();
                aosUrl.set("aos_platformid", key.split("~")[0]);
                aosUrl.set("aos_shopid", key.split("~")[1]);
                aosUrl.set("aos_shopsku", key.split("~")[2]);
                aosUrl.set("aos_url", itemClUrlMap.get(key));
            }
        }
    }

    /**
     * 获取已生成的数字资产管理数据
     */
    private static Set<String> getManaSet() {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_listing_mana",
                "aos_orgid,aos_itemid", null);
        return list.stream().map(obj -> obj.getString("aos_orgid") + "~" + obj.getString("aos_itemid"))
                .collect(Collectors.toSet());
    }

    /**
     * 主方法
     */
    public static void process() {
        orgCateUserMapInit();
        cateUserMapInit();
        itemCategoryInit();
        itemUserInit();
        photoMapInit();
        aaddMapInit();
        design3DInit();
        itemPictureMapInit();
        itemAmUrlMapInit();

        // CL Data
        clUrlInit();
        clKeyWordInit();
        clCateInit();
        clDateInit();

        Set<String> manaSet = getManaSet();
        DynamicObjectCollection bdMaterialS = QueryServiceHelper.query("bd_material",
                "aos_contryentry.aos_nationality aos_orgid," +
                        "id aos_itemid," +
                        "aos_contryentry.aos_nationality.number aos_orgnum," +
                        "aos_contryentry.aos_is_saleout aos_is_saleout," +
                        "picturefield",
                new QFilter("aos_protype", QCP.equals, "N")
                        .and("aos_contryentry.aos_contryentrystatus", QCP.not_in, new String[]{"H", "F"})
//                       .and("number", QCP.equals, "D34-005V00CG")
                        .toArray());
        List<DynamicObject> listManaDynS = new ArrayList<>();
        int seq = 1;
        int biffSize = bdMaterialS.size();
        for (DynamicObject bdMaterial : bdMaterialS) {
            String orgId = bdMaterial.getString("aos_orgid");
            String itemId = bdMaterial.getString("aos_itemid");
            String aosOrgNum = bdMaterial.getString("aos_orgnum");
            String key = orgId + "~" + itemId;
            DynamicObject listManaDyn = null;
            if (manaSet.contains(key)) {
                // 已经存在则更新
                listManaDyn = BusinessDataServiceHelper.loadSingle("aos_mkt_listing_mana",
                        new QFilter("aos_orgid", QCP.equals, orgId)
                                .and("aos_itemid", QCP.equals, itemId)
                                .toArray());
                listManaUpdate(listManaDyn);
                listManaDyn.set("aos_is_saleout", bdMaterial.get("aos_is_saleout"));
                listManaDyn.set("aos_picture", bdMaterial.get("picturefield"));
            } else {
                listManaDyn = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_mana");
                listManaCreate(listManaDyn, orgId, itemId, aosOrgNum);
                listManaDyn.set("aos_is_saleout", bdMaterial.get("aos_is_saleout"));
                listManaDyn.set("aos_picture", bdMaterial.get("picturefield"));
            }
            listManaDynS.add(listManaDyn);
            if (listManaDynS.size() >= 5000 || seq == biffSize) {
                DynamicObject[] listManaDynArray = listManaDynS.toArray(new DynamicObject[0]);
                SaveServiceHelper.save(listManaDynArray);
                listManaDynS.clear();
            }
            ++seq;
        }
    }

    /**
     * CL物料上线日期初始化
     */
    private static void clDateInit() {
        FndMsg.debug("======Start CL物料上线日期初始化======");
        clDateMap = new HashMap<>();
        DynamicObjectCollection dyns = QueryServiceHelper.query("aos_mkt_cloldate",
                "aos_orgid,aos_itemid,aos_date"
                , null);
        for (DynamicObject dyn : dyns) {
            String key = dyn.getString("aos_orgid") + "~" +
                    dyn.getString("aos_itemid");
            clDateMap.put(key, dyn.getDate("aos_date"));
        }
    }

    /**
     * CL-AM属性字段填写率初始化
     */
    private static void clCateInit() {
        FndMsg.debug("======Start CL-AM属性字段填写率初始化======");
        clCateMap = new HashMap<>();
        DynamicObjectCollection dyns = QueryServiceHelper.query("aos_mkt_caterate",
                "aos_orgid,aos_itemid,aos_caterate"
                , new QFilter("aos_platform", QCP.equals, "AMAZONTEMPLATE").toArray());
        for (DynamicObject dyn : dyns) {
            String key = dyn.getString("aos_orgid") + "~" +
                    dyn.getString("aos_itemid");
            clCateMap.put(key, dyn.getBigDecimal("aos_caterate"));
        }
    }

    /**
     * CL链接初始化
     */
    private static void clUrlInit() {
        FndMsg.debug("======Start CL链接初始化======");
        clUrlMap = new HashMap<>();
        DynamicObjectCollection dyns = QueryServiceHelper.query("aos_mkt_clurl",
                "aos_orgid , aos_platformid, aos_shopid, aos_itemid, " +
                        "aos_shopsku, aos_url"
                , null);
        for (DynamicObject dyn : dyns) {
            String key = dyn.getString("aos_orgid") + "~" +
                    dyn.getString("aos_itemid");
            Map<String, String> info = clUrlMap.computeIfAbsent(key, k -> new HashMap<>());
            info.put(dyn.getString("aos_platformid")
                            + "~" + dyn.getString("aos_shopid")
                            + "~" + dyn.getString("aos_shopsku")
                    , dyn.getString("aos_url"));
            clUrlMap.put(key, info);
        }
    }

    /**
     * CL埋词初始化
     */
    private static void clKeyWordInit() {
        FndMsg.debug("======Start CL埋词初始化======");
        clKeyWordMap = new HashMap<>();
        DynamicObjectCollection dyns = QueryServiceHelper.query("aos_mkt_clkeyword",
                "aos_orgid,aos_itemid,aos_keycount"
                , null);
        for (DynamicObject dyn : dyns) {
            String key = dyn.getString("aos_orgid") + "~" +
                    dyn.getString("aos_itemid");
            clKeyWordMap.put(key, dyn.getInt("aos_keycount"));
        }
    }

    /**
     * 更新已生成的Listing资产管理界面
     *
     * @param listManaDyn 单据实体
     */
    private static void listManaUpdate(DynamicObject listManaDyn) {
        String orgId = listManaDyn.getDynamicObject("aos_orgid").getPkValue().toString();
        String itemId = listManaDyn.getDynamicObject("aos_itemid").getPkValue().toString();
        String aosOrgNum = listManaDyn.getDynamicObject("aos_orgid").getString("number");
        listManaDyn.set("aos_purer", itemUserMap.get(itemId + "~PUR"));
        listManaDyn.set("aos_follower", itemUserMap.get(itemId + "~FOL"));
        listManaDyn.set("aos_picture", itemPictureMap.get(itemId));
        listManaDyn.set("aos_amurl", itemAmUrlMap.get(orgId + "~" + itemId));
        listManaDyn.set("aos_qty", clKeyWordMap.get(orgId + "~" + itemId));
        listManaDyn.set("aos_amrate", clCateMap.get(orgId + "~" + itemId));
        listManaDyn.set("aos_onlinedate", clDateMap.get(orgId + "~" + itemId));

        if (aaddMap.contains(aosOrgNum + "~" + itemId) && !listManaDyn.getBoolean("aos_aadd")) {
            listManaDyn.set("aos_aadd", true);
        }
        if (design3DSet.contains(itemId) && !listManaDyn.getBoolean("aos_3d")) {
            listManaDyn.set("aos_3d", true);
        }


        Map<String, String> photoInfo = photoMap.get(itemId);
        if (FndGlobal.IsNotNull(photoInfo)) {
            listManaDyn.set("aos_veditem", photoInfo.get("aos_veditem"));
            listManaDyn.set("aos_s3address1", photoInfo.get(aosOrgNum + "aos_s3address1"));
            listManaDyn.set("aos_s3address2", photoInfo.get(aosOrgNum + "aos_s3address2"));
            listManaDyn.set("aos_filename", photoInfo.get(aosOrgNum + "aos_filename"));
        }
        String group = categoryIdMap.get(itemId);
        String[] split = group.split(",");
        if (split.length == 3) {
            String aosCate1 = split[0];
            String aosCate2 = split[1];
            String aosCate3 = split[2];
            String cateKey = aosCate1 + "~" + aosCate2;
            listManaDyn.set("aos_category1", aosCate1);
            listManaDyn.set("aos_category2", aosCate2);
            listManaDyn.set("aos_category3", aosCate3);
            listManaDyn.set("aos_editor", cateUserMap.get(cateKey + "~EDI"));
            listManaDyn.set("aos_designer", cateUserMap.get(cateKey + "~DES"));
            listManaDyn.set("aos_us_sale", orgCateUserMap.get("US~" + cateKey));
            listManaDyn.set("aos_ca_sale", orgCateUserMap.get("CA~" + cateKey));
            listManaDyn.set("aos_uk_sale", orgCateUserMap.get("UK~" + cateKey));
            listManaDyn.set("aos_de_sale", orgCateUserMap.get("DE~" + cateKey));
            listManaDyn.set("aos_fr_sale", orgCateUserMap.get("FR~" + cateKey));
            listManaDyn.set("aos_it_sale", orgCateUserMap.get("IT~" + cateKey));
            listManaDyn.set("aos_es_sale", orgCateUserMap.get("ES~" + cateKey));
        }

        DynamicObjectCollection aosUrlS = listManaDyn
                .getDynamicObjectCollection("aos_urlentity");
        aosUrlS.clear();
        Map<String, String> itemClUrlMap = clUrlMap.get(orgId + "~" + itemId);
        if (FndGlobal.IsNotNull(itemClUrlMap)){
            for (String key : itemClUrlMap.keySet()) {
                DynamicObject aosUrl = aosUrlS.addNew();
                aosUrl.set("aos_platformid", key.split("~")[0]);
                aosUrl.set("aos_shopid", key.split("~")[1]);
                aosUrl.set("aos_shopsku", key.split("~")[2]);
                aosUrl.set("aos_url", itemClUrlMap.get(key));
            }
        }
    }

    /**
     * 亚马逊链接初始化
     */
    private static void itemAmUrlMapInit() {
        FndMsg.debug("======Start 亚马逊链接初始化======");
        itemAmUrlMap = new HashMap<>();
        DataSet przS = QueryServiceHelper.queryDataSet("itemAmUrlMapInit",
                "aos_sync_invprice",
                "aos_orgid.id aos_orgid," +
                        "aos_orgid.number aos_orgnum," +
                        "aos_item_code aos_itemid," +
                        "aos_asin," +
                        "id",
                new QFilter("aos_shelfstatus", QCP.equals, "Listed")
                        .and("aos_shopfid.aos_is_mainshop", QCP.equals, true)
                        .toArray(), null);
        przS = przS.groupBy(new String[]{"aos_orgid", "aos_orgnum", "aos_itemid"})
                .maxP("id", "aos_asin").finish();
        while (przS.hasNext()) {
            Row prz = przS.next();
            String aosOrgNum = prz.getString("aos_orgnum");
            String url = "";
            String aos_asin = prz.getString("aos_asin");
            switch (aosOrgNum) {
                case "US":
                    url = "https://www.amazon.com/dp/";
                    break;
                case "CA":
                    url = "https://www.amazon.ca/dp/";
                    break;
                case "UK":
                    url = "https://www.amazon.co.uk/dp/";
                    break;
                case "DE":
                    url = "https://www.amazon.de/dp/";
                    break;
                case "FR":
                    url = "https://www.amazon.fr/dp/";
                    break;
                case "IT":
                    url = "https://www.amazon.it/dp/";
                    break;
                case "ES":
                    url = "https://www.amazon.es/dp/";
                    break;
            }
            url = url + aos_asin;
            itemAmUrlMap.put(prz.getString("aos_orgid") + "~" + prz.getString("aos_itemid"), url);
        }
        przS.close();
        FndMsg.debug("======End 亚马逊链接初始化======");
    }

    /**
     * 物料图片初始化
     */
    private static void itemPictureMapInit() {
        FndMsg.debug("======Start 物料图片初始化======");
        itemPictureMap = new HashMap<>();
        DataSet picS = QueryServiceHelper.queryDataSet("itemPictureMapInit",
                "aos_newarrangeorders",
                "entryentity.aos_articlenumber.id aos_itemid," +
                        "entryentity.aos_picture aos_picture," +
                        "aos_creattime",
                new QFilter("billstatus", QCP.in, new String[]{"C", "D"}).toArray(), null);
        picS = picS.groupBy(new String[]{"aos_itemid"}).maxP("aos_creattime", "aos_picture").finish();
        while (picS.hasNext()) {
            Row pic = picS.next();
            itemPictureMap.put(pic.getString("aos_itemid"), pic.getString("aos_picture"));
        }
        picS.close();
        FndMsg.debug("======End 物料图片初始化======");
    }

    /**
     * 3D产品设计单初始化
     */
    private static void design3DInit() {
        FndMsg.debug("======Start 3D产品设计单初始化======");
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_3design",
                "aos_entryentity.aos_itemid aos_itemid",
                new QFilter("aos_status", QCP.equals, "已完成").toArray());
        design3DSet = list.stream().map(obj -> obj.getString("aos_itemid"))
                .collect(Collectors.toSet());
        FndMsg.debug("======End 3D产品设计单初始化======");
    }

    /**
     * 高级A+需求表初始化
     */
    private static void aaddMapInit() {
        FndMsg.debug("======Start 高级A+需求表初始化======");
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_aadd",
                "aos_org,aos_entryentity.aos_itemid aos_itemid",
                new QFilter("aos_status", QCP.equals, "已完成").toArray());
        aaddMap = list.stream().map(obj -> obj.getString("aos_org")
                        + "~" + obj.getString("aos_itemid"))
                .collect(Collectors.toSet());
        FndMsg.debug("======End 高级A+需求表初始化======");
    }

    /**
     * 拍照需求表初始化
     */
    private static void photoMapInit() {
        FndMsg.debug("======Start 拍照需求表初始化======");
        photoMap = new HashMap<>();
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_photoreq",
                "aos_entryentity4.aos_orgshort aos_orgnum," +
                        "aos_itemid," +
                        "aos_vediocate," +
                        "aos_vediosku," +
                        "aos_entryentity4.aos_s3address1 aos_s3address1," +
                        "aos_entryentity4.aos_s3address2 aos_s3address2," +
                        "aos_entryentity4.aos_filename aos_filename",
                new QFilter("aos_type", QCP.equals, "视频").toArray(),
                "aos_requiredate"
        );
        for (DynamicObject obj : list) {
            String itemId = obj.getString("aos_itemid");
            String aosOrgNum = obj.getString("aos_orgnum");
            Map<String, String> info = photoMap.computeIfAbsent(itemId, k -> new HashMap<>());
            info.put("aos_vediocate", obj.getString("aos_vediocate"));
            info.put("aos_vediosku", obj.getString("aos_vediosku"));
            info.put(aosOrgNum + "aos_s3address1", obj.getString("aos_s3address1"));
            info.put(aosOrgNum + "aos_s3address2", obj.getString("aos_s3address2"));
            info.put(aosOrgNum + "aos_filename", obj.getString("aos_filename"));
            photoMap.put(itemId, info);
        }
        FndMsg.debug("======End 拍照需求表初始化======");
    }

    /**
     * 国别品类人员对应表初始化
     */
    private static void orgCateUserMapInit() {
        FndMsg.debug("======Start 国别品类人员对应表初始化======");
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_progorguser",
                "aos_orgid.number aos_orgnum,aos_category1,aos_category2,aos_salehelper",
                null
        );
        orgCateUserMap = new HashMap<>();
        for (DynamicObject obj : list) {
            orgCateUserMap.put(obj.getString("aos_orgnum") + "~" +
                            obj.getString("aos_category1") + "~" +
                            obj.getString("aos_category2"),
                    obj.getString("aos_salehelper"));
        }
        FndMsg.debug("======End 国别品类人员对应表初始化======");
    }

    /**
     * 品类人员对应表初始化
     */
    private static void cateUserMapInit() {
        FndMsg.debug("======Start 品类人员对应表初始化======");
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_proguser",
                "aos_category1,aos_category2,aos_eng,aos_designer",
                null
        );
        cateUserMap = new HashMap<>();
        for (DynamicObject obj : list) {
            cateUserMap.put(obj.getString("aos_category1") + "~" +
                    obj.getString("aos_category2") + "~EDI", obj.getString("aos_eng"));
            cateUserMap.put(obj.getString("aos_category1") + "~" +
                    obj.getString("aos_category2") + "~DES", obj.getString("aos_designer"));
        }
        FndMsg.debug("======End 品类人员对应表初始化======");
    }

    /**
     * 物料人员初始化
     */
    private static void itemUserInit() {
        FndMsg.debug("======Start 物料人员初始化======");
        DynamicObjectCollection list = QueryServiceHelper.query("aos_offerbill",
                "aos_offerbillentry.aos_materiel aos_itemid," +
                        "aos_supplier.aos_purer aos_purer," +
                        "aos_supplier.aos_documentary aos_documentary",
                new QFilter("billstatus", QCP.equals, "B").toArray(),
                "aos_offerbillentry.aos_updatetime asc");
        itemUserMap = new HashMap<>();
        for (DynamicObject obj : list) {
            String itemId = obj.getString("aos_itemid");
            itemUserMap.put(itemId + "~PUR", obj.getString("aos_purer"));
            itemUserMap.put(itemId + "~FOL", obj.getString("aos_documentary"));
        }
        FndMsg.debug("======End 物料人员初始化======");
    }

    /**
     * 物料类别初始化
     */
    private static void itemCategoryInit() {
        FndMsg.debug("======Start 物料类别初始化======");
        DynamicObjectCollection list = QueryServiceHelper.query("bd_materialgroupdetail",
                "material.id material,group.name group",
                new QFilter("standard.name", QCP.equals, "物料基本分类标准").toArray()
        );
        categoryIdMap = new HashMap<>();
        for (DynamicObject obj : list) {
            categoryIdMap.put(obj.getString("material"), obj.getString("group"));
        }
        FndMsg.debug("======End 物料类别初始化======");
    }

    /**
     * 实现
     *
     * @param requestContext 请求上下文
     * @param map            参数
     * @throws KDException 异常
     */
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        process();
    }

}
