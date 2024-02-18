package mkt.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import common.CommonDataSom;
import common.fnd.FndGlobal;

import common.sal.sys.basedata.dao.CountryDao;
import common.sal.sys.basedata.dao.impl.CountryDaoImpl;
import common.sal.util.QFBuilder;
import common.sal.util.SaveUtils;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.ILocaleString;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;

import kd.bos.form.control.events.ClickListener;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

/**
 * @author aosom
 */
public class aos_czj_test_bill extends AbstractBillPlugIn implements ItemClickListener, ClickListener {
    private static final Log log = LogFactory.getLog("mkt.common.aos_czj_test_bill");
    private static final String AOS_TEST = "aos_test";
    /**
     * 设置sku清单
     * 
     * @param dyMain 目标对象
     */
    private static void setItemEntity(DynamicObject dyMain) {
        common.sal.util.QFBuilder builder = new common.sal.util.QFBuilder();
        DynamicObject aosOrgid = dyMain.getDynamicObject("aos_orgid");
        if (aosOrgid != null) {
            builder.add("aos_orgid", "=", aosOrgid.getPkValue());
        }
        builder.add("aos_category1", "=", dyMain.getString("aos_category1"));
        builder.add("aos_category2", "=", dyMain.getString("aos_category2"));
        builder.add("aos_category3", "=", dyMain.getString("aos_category3"));
        builder.add("aos_itemname", "=", dyMain.getString("aos_itemnamecn"));
        builder.add("aos_itemid", "!=", "");
        DynamicObjectCollection dycLine = dyMain.getDynamicObjectCollection("aos_itementity");
        dycLine.removeIf(dy -> true);
        DynamicObjectCollection dyc =
            QueryServiceHelper.query("aos_mkt_keyword", "aos_itemid,aos_itemid.number number", builder.toArray());
        for (DynamicObject row : dyc) {
            DynamicObject addNewRow = dycLine.addNew();
            addNewRow.set("aos_itemid", row.get("aos_itemid"));
            addNewRow.set("aos_picture1", CommonDataSom.get_img_url(row.getString("number")));
        }
    }

    public static boolean figure(String str) {
        String regex = "[-+]?\\d*\\.?\\d+";
        return str.matches(regex);
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        this.addItemClickListeners("aos_test");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        if (control.equals(AOS_TEST)) {
            log.info("迁移开始： {}", LocalDateTime.now());
            try {
                syncSkuData();
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                log.error(sw.toString());
            }
            log.info("迁移结束： {}", LocalDateTime.now());
        }
    }

    /**
     * 同步sku词库
     */
    public void syncSkuData() {
        List<DynamicObject> saveList = new ArrayList<>(5000);
        QFBuilder builder = new QFBuilder();
        builder.add("aos_orgid", "!=", "");
        builder.add("aos_category1", "!=", "");
        builder.add("aos_category2", "!=", "");
        builder.add("aos_category3", "!=", "");
        builder.add("aos_itemnamecn", "!=", "");
        DynamicObjectCollection dyS = QueryServiceHelper.query("aos_mkt_point", "id", builder.toArray());
        for (DynamicObject dy : dyS) {
            DynamicObject dyMain = BusinessDataServiceHelper.loadSingle(dy.get("id"), "aos_mkt_point");
            setItemEntity(dyMain);
            saveList.add(dyMain);
            SaveUtils.SaveEntity(saveList, false);
        }
        SaveUtils.SaveEntity(saveList, true);
    }

    public void find() {
        // 迁移高级A+模板
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_aadd_model", "id", null);
        List<String> pks = dyc.stream().map(dy -> dy.getString("id")).collect(Collectors.toList());
        // 查找明细信息
        QFilter filter = new QFilter("aos_sourceid", "!=", "");
        StringJoiner str = new StringJoiner(",");
        str.add("aos_sourceid");
        str.add("aos_seq");
        str.add("aos_button");
        str.add("aos_cate1");
        str.add("aos_cate2");
        str.add("aos_cn");
        str.add("aos_usca");
        str.add("aos_uk");
        str.add("aos_de");
        str.add("aos_fr");
        str.add("aos_it");
        str.add("aos_es");
        dyc = QueryServiceHelper.query("aos_aadd_model_detail", str.toString(), new QFilter[] {filter});
        // 根据主键分组
        Map<String, Map<String, List<DynamicObject>>> mapData = new HashMap<>(16);
        for (DynamicObject dy : dyc) {
            String aosSourceid = dy.getString("aos_sourceid");
            Map<String, List<DynamicObject>> map = mapData.computeIfAbsent(aosSourceid, k -> new HashMap<>(16));
            String aosSeq = dy.getString("aos_button");
            List<DynamicObject> list = map.computeIfAbsent(aosSeq, k -> new ArrayList<>());
            list.add(dy);
        }
        List<DynamicObject> listSave = new ArrayList<>(5000);
        // 主键
        for (Map.Entry<String, Map<String, List<DynamicObject>>> entry : mapData.entrySet()) {
            if (!pks.contains(entry.getKey())) {
                continue;
            }
            DynamicObject addEntry = BusinessDataServiceHelper.loadSingle(entry.getKey(), "aos_aadd_model");
            if (addEntry == null) {
                continue;
            }
			listSave.add(addEntry);
            DynamicObjectCollection tabEntRows = addEntry.getDynamicObjectCollection("aos_ent_tab");
            // 页签遍历
            for (Map.Entry<String, List<DynamicObject>> lanEntry : entry.getValue().entrySet()) {
                // 页签对应的第几行
                String tabIndex = lanEntry.getKey();
                // 添加页签数据
                DynamicObject tabNewRow = tabEntRows.addNew();
                tabNewRow.set("seq", Integer.valueOf(tabIndex));
                tabNewRow.set("aos_tab", addEntry.getString("aos_textfield" + tabIndex));
                DynamicObjectCollection lanEntryRows = tabNewRow.getDynamicObjectCollection("aos_entryentity");
                // 填加语言行
                for (DynamicObject lanRow : lanEntry.getValue()) {
                    DynamicObject lanNewRow = lanEntryRows.addNew();
                    lanNewRow.set("seq", lanRow.get("aos_seq"));
                    lanNewRow.set("aos_cate1", lanRow.get("aos_cate1"));
                    lanNewRow.set("aos_cate2", lanRow.get("aos_cate2"));
                    lanNewRow.set("aos_cn", lanRow.get("aos_cn"));
                    lanNewRow.set("aos_usca", lanRow.get("aos_usca"));
                    lanNewRow.set("aos_uk", lanRow.get("aos_uk"));
                    lanNewRow.set("aos_de", lanRow.get("aos_de"));
                    lanNewRow.set("aos_fr", lanRow.get("aos_fr"));
                    lanNewRow.set("aos_it", lanRow.get("aos_it"));
                    lanNewRow.set("aos_es", lanRow.get("aos_es"));
                }
            }
            SaveUtils.SaveEntity(listSave, false);
        }
        SaveUtils.SaveEntity(listSave, true);

    }

    /** 同步敏感词库 **/
    private void aosTest() {
        Log log = LogFactory.getLog("mkt.common.aos_czj_test_bill");
        log.info("敏感词同步开始：{}" + LocalDateTime.now());
        try {
            // 查找敏感词信息
            QFBuilder builder = new QFBuilder();
            builder.add("aos_string2", "!=", "");
            StringJoiner str = new StringJoiner(",");
			int count = 10;
            for (int i = 1; i <= count; i++) {
                str.add("aos_string" + i);
            }
            DynamicObjectCollection dyc = QueryServiceHelper.query("aos_czj_tmp", str.toString(), builder.toArray());
            List<DynamicObject> saveEntity = new ArrayList<>(dyc.size());
            // 新建单据
            DynamicObjectType type =
                BusinessDataServiceHelper.newDynamicObject("aos_mkt_sensitive").getDynamicObjectType();
            for (DynamicObject dy : dyc) {
                DynamicObject dyNew = new DynamicObject(type);
				dyNew.set("billstatus", "A");
				dyNew.set("aos_words", dy.get("aos_string2"));
				dyNew.set("aos_type", dy.get("aos_string3"));
				dyNew.set("aos_replace", dy.get("aos_string4"));
				dyNew.set("aos_level", dy.get("aos_string5"));
                String org = dy.getString("aos_string1");
                DynamicObjectCollection entity = dyNew.getDynamicObjectCollection("entryentity");
                if ("CN".equals(org)) {
                    DynamicObject subNew = entity.addNew();
                    subNew.set("aos_whole", type);
                } else {
                    String[] split = org.split("/");
                    for (String value : split) {
                        DynamicObject subNew = entity.addNew();
                        subNew.set("aos_lan", value);
                        subNew.set("aos_cate1", dy.get("aos_string6"));
                        subNew.set("aos_cate2", dy.get("aos_string7"));
                        subNew.set("aos_cate3", dy.get("aos_string8"));
                        subNew.set("aos_name", dy.get("aos_string9"));
                    }
                }
                saveEntity.add(dyNew);
            }
            SaveUtils.SaveEntity("aos_mkt_sensitive", saveEntity, true);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            log.error(sw.toString());
            e.printStackTrace();
        }
        log.info("敏感词同步结束：{}" + LocalDateTime.now());

    }

    private void keyWordSync() {
        StringJoiner str = new StringJoiner(",");
		// 定义格式化模式，最多保留4位小数
        DecimalFormat df = new DecimalFormat("#.####");
        str.add("id");
        str.add("modifytime");
		// 新相关性
        str.add("aos_linentity.aos_correlate");
        Date date = new Date();
        DynamicObject[] mktPoints = BusinessDataServiceHelper.load("aos_mkt_point", str.toString(), null);
        for (DynamicObject point : mktPoints) {
            DynamicObjectCollection lineEntitys = point.getDynamicObjectCollection("aos_linentity");
            for (DynamicObject lineRow : lineEntitys) {
                // 相关性是数值则保留（其他移到备注）
                String aosRelate = lineRow.getLocaleString("aos_correlate").getLocaleValue_zh_CN();
                if (FndGlobal.IsNotNull(aosRelate)) {
                    ILocaleString aosCorrelate = lineRow.getLocaleString("aos_correlate");
                    // 是数字
                    if (figure(aosRelate)) {
                        String format = df.format(Double.parseDouble(aosRelate));
                        if ("0".equals(format)) {
                            format = "";
                        }
						aosCorrelate.setLocaleValue_zh_CN(format);
						aosCorrelate.setLocaleValue_en(format);
                        lineRow.set("aos_correlate", aosCorrelate);
                        point.set("modifytime", date);
                    }
                }
            }
        }
        SaveServiceHelper.save(mktPoints);
    }

    private Map<String, String> getAllItemCategory() {
        QFilter qFilter = new QFilter("standard.number", QCP.equals, "JBFLBZ");
        String selectFields = "material,group.name categoryname";
        DynamicObjectCollection list =
            QueryServiceHelper.query("bd_materialgroupdetail", selectFields, qFilter.toArray());
        return list.stream().collect(
            Collectors.toMap(obj -> obj.getString("material"), obj -> obj.getString("categoryname"), (k1, k2) -> k1));
    }

	/**
	 * 同步品名关键字库
	 */
	private void syncItemKeyword() {
        StringJoiner str = new StringJoiner(",");
		int count = 8;
		int count2 = 6;
        for (int i = 1; i < count; i++) {
            str.add("aos_string" + i);
        }
        DynamicObjectCollection list = QueryServiceHelper.query("aos_czj_tmp", str.toString(), null);
        // 根据国别,类别，属性拆分
        Map<String, List<String>> splitMap = new HashMap<>(16);
        for (DynamicObject row : list) {
            StringJoiner title = new StringJoiner("/");
            for (int i = 1; i < count2; i++) {
                title.add(row.getString("aos_string" + i));
            }
            List<String> value = splitMap.computeIfAbsent(title.toString(), k -> new ArrayList<>());
            title = new StringJoiner("/");
            title.add(row.getString("aos_string" + 6));
            title.add(row.getString("aos_string" + 7));
            if (!value.contains(title.toString())) {
                value.add(title.toString());
            }
        }
        List<DynamicObject> savEntity = new ArrayList<>(5000);
        CountryDao countryDao = new CountryDaoImpl();
        Map<String, String> orgInfo = new HashMap<>(16);
        for (Map.Entry<String, List<String>> entry : splitMap.entrySet()) {
            String[] split = entry.getKey().split("/");
            // 国别
            String orgid;
            if (orgInfo.containsKey(split[0])) {
                orgid = orgInfo.get(split[0]);
            } else {
                orgid = countryDao.getCountryID(split[0]);
                orgInfo.put(split[0], orgid);
            }
            // 查找对应的单据
            QFBuilder builder = new QFBuilder();
            builder.add("aos_orgid.number", "=", split[0]);
            builder.add("aos_category1", "=", split[1]);
            builder.add("aos_category2", "=", split[2]);
            builder.add("aos_category3", "=", split[3]);
            builder.add("aos_category1", "=", split[1]);
            builder.add("aos_itemnamecn", "=", split[4]);
            DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_point", "id", builder.toArray());
            for (DynamicObject dy : dyc) {
                String id = dy.getString("id");
                DynamicObject pointEntity = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_point");
                // 获取已经有的关键词
                DynamicObjectCollection aosLinentity = pointEntity.getDynamicObjectCollection("aos_linentity");
                List<String> keyList = new ArrayList<>();
                for (DynamicObject dyRow : aosLinentity) {
                    String aosKeyword = dyRow.getString("aos_keyword");
                    if (FndGlobal.IsNotNull(aosKeyword)) {
                        keyList.add(aosKeyword);
                    }
                }
                for (String keyInfo : entry.getValue()) {
                    String[] split1 = keyInfo.split("/");
                    if (keyList.contains(split1[0])) {
                        continue;
                    }
                    DynamicObject row = aosLinentity.addNew();
                    row.set("aos_keyword", split1[0]);
                    row.set("aos_remake", split1[1]);
                    keyList.add(split1[0]);
                }
                savEntity.add(pointEntity);
            }
            SaveUtils.SaveEntity("aos_mkt_point", savEntity, false);
        }
        SaveUtils.SaveEntity("aos_mkt_point", savEntity, true);
    }
}