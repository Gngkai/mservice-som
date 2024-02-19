package mkt.data.point;

import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.bill.BillShowParameter;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.ILocaleString;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
import kd.bos.entity.datamodel.events.BeforeImportDataEventArgs;
import kd.bos.entity.datamodel.events.ImportDataEventArgs;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.ShowType;
import kd.bos.form.StyleCss;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.Image;
import kd.bos.form.control.Label;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.events.*;
import kd.bos.form.field.BasedataEdit;
import kd.bos.form.field.ComboEdit;
import kd.bos.form.field.ComboItem;
import kd.bos.form.field.events.BeforeF7SelectEvent;
import kd.bos.form.field.events.BeforeF7SelectListener;
import kd.bos.lang.Lang;
import kd.bos.list.ListShowParameter;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import common.sal.util.QFBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.*;

/**
 * @author aosom
 * @version 品名关键词库
 */
public class AosMktPointBillOld extends AbstractBillPlugIn
    implements RowClickEventListener, BeforeF7SelectListener, HyperLinkClickListener {
    public final static String DB_MKT = "aos.mkt";
    public final static String AOS_CATEGORY1 = "aos_category1";
    public final static String AOS_CATEGORY2 = "aos_category2";
    public final static String AOS_CATEGORY3 = "aos_category3";
    public final static String OVERRIDENEW = "overridenew";
    public final static String AOS_EGSKU = "aos_egsku";
    public final static String AOS_ORGID = "aos_orgid";
    public final static String AOS_ITEMID = "aos_itemid";
    public final static String AOS_ITEMNAMECN = "aos_itemnamecn";
    public final static String US = "US";
    public final static String CA = "CA";
    public final static String ZH = "zh";
    public final static String AOS_EN_CATEGORY1 = "aos_en_category1";

    private static final Log logger = LogFactory.getLog(AosMktPointBillOld.class);

    public static String obj2String(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    /** 设置文本的类别 **/
    public static void setCate(DynamicObject dyMain) {
        StringJoiner str = new StringJoiner(",");
        int two = 2;
        int count = 4;
        for (int i = 1; i < count; i++) {
            str.add(dyMain.getString("aos_category" + i));
            dyMain.set("aos_category" + i + "_name", dyMain.getString("aos_category" + i));
        }
        QFBuilder builder = new QFBuilder();
        builder.add("name", "=", str.toString());
        DynamicObject[] cate = BusinessDataServiceHelper.load("bd_materialgroup", "name", builder.toArray());
        if (cate.length > 0) {
            ILocaleString name = cate[0].getLocaleString("name");
            String valueEn = name.getLocaleValue_en();
            String[] split = valueEn.split(",");
            if (split.length > 0) {
                dyMain.set("aos_en_category1", split[0]);
            }
            if (split.length > 1) {
                dyMain.set("aos_en_category2", split[1]);
            }
            if (split.length > two) {
                dyMain.set("aos_en_category3", split[2]);
            }
        }
    }

    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        statusControl();
    }

    @Override
    public void registerListener(EventObject e) {
        try {
            // 根据人员过滤国别
            BasedataEdit aosOrgid = this.getControl("aos_orgid");
            aosOrgid.addBeforeF7SelectListener(this);
            EntryGrid aosItementity = this.getControl("aos_itementity");
            aosItementity.addHyperClickListener(this);
        } catch (Exception ex) {
            this.getView().showErrorNotification("registerListener = " + ex);
        }
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (AOS_CATEGORY1.equals(name) || AOS_CATEGORY2.equals(name) || AOS_CATEGORY3.equals(name)) {
            initCategory();
            getPoints();
        } else if (AOS_EGSKU.equals(name)) {
            aosEgskuChange();
        } else if (AOS_ORGID.equals(name)) {
            initPoint();
        } else if (AOS_ITEMNAMECN.equals(name)) {
            initPoint();
            getPoints();
        }
        if (AOS_CATEGORY2.equals(name) || AOS_CATEGORY1.equals(name)) {
            autoSetCategoryInfo();
        }
    }

    private void autoSetCategoryInfo() {
        String aosCategory1 = obj2String(this.getModel().getValue("aos_category1"));
        String aosCategory2 = obj2String(this.getModel().getValue("aos_category2"));
        String aosCategory = aosCategory1 + "," + aosCategory2;
        // 产品中类改变自动带出编辑
        DynamicObject dynamicObject = getEditorInfo(aosCategory);
        DynamicObject editor = getEditorInfo2(aosCategory1, aosCategory2);

        if (dynamicObject != null && editor != null) {
            this.getModel().setValue("aos_user", editor.getString("aos_edmanar"));
            this.getModel().setValue("aos_groupid", dynamicObject.getString("aos_group_edit"));
            this.getModel().setValue("aos_confirmor", dynamicObject.getString("aos_edit_leader"));
        } else {
            this.getModel().setValue("aos_user", null);
            this.getModel().setValue("aos_groupid", null);
            this.getModel().setValue("aos_confirmor", null);
        }
    }

    /**
     * 根据大类中类获取编辑助理信息
     * 
     * @param aosCategory1 大类
     * @param aosCategory2 中类
     * @return 编辑助理信息
     */
    private DynamicObject getEditorInfo2(String aosCategory1, String aosCategory2) {
        return QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_edmanar",
            new QFilter[] {new QFilter("aos_category1", QCP.equals, aosCategory1)
                .and(new QFilter("aos_category2", QCP.equals, aosCategory2))});
    }

    /**
     *
     * @return 组别编辑等信息
     */
    private DynamicObject getEditorInfo(String aosCategory) {
        return QueryServiceHelper.queryOne("aos_mkt_groupinfo", "aos_editor,aos_group_edit,aos_edit_leader",
            new QFilter[] {new QFilter("aos_category2.name", QCP.equals, aosCategory)});
    }

    private void getPoints() {
        Object aosCategory1 = this.getModel().getValue("aos_category1");
        Object aosCategory2 = this.getModel().getValue("aos_category2");
        Object aosCategory3 = this.getModel().getValue("aos_category3");
        Object aosItemnamecn = this.getModel().getValue("aos_itemnamecn");
        if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2) && FndGlobal.IsNotNull(aosCategory3)
            && FndGlobal.IsNotNull(aosItemnamecn)) {
            QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
            QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
            QFilter filterCategory3 = new QFilter("aos_category3", "=", aosCategory3);
            QFilter[] filters = new QFilter[] {filterCategory1, filterCategory2, filterCategory3};
            String selectField = "aos_itemnamefr,aos_itemnamees";
            DynamicObject aosMktDataSlogan = QueryServiceHelper.queryOne("aos_mkt_data_slogan", selectField, filters);
            if (aosMktDataSlogan != null) {
                String aosItemnamees = aosMktDataSlogan.getString("aos_itemnamees");
                String aosItemnamefr = aosMktDataSlogan.getString("aos_itemnamefr");
                this.getModel().setValue("aos_pointses", aosItemnamees);
                this.getModel().setValue("aos_pointsfr", aosItemnamefr);
            }
        }
    }

    private void aosEgskuChange() {
        Object aosEgsku = this.getModel().getValue("aos_egsku");
        if (aosEgsku == null) {
            this.getView().setVisible(true, "aos_picture");
            this.getView().setVisible(false, "aos_image");
        } else {
            this.getView().setVisible(false, "aos_picture");
            this.getView().setVisible(true, "aos_image");
            String itemNumber = ((DynamicObject)aosEgsku).getString("number");
            String url = "https://cls3.s3.amazonaws.com/" + itemNumber + "/1-1.jpg";
            Image image = this.getControl("aos_image");
            image.setUrl(url);
            this.getModel().setValue("aos_picture", url);
        }
    }

    @Override
    public void afterLoadData(EventObject e) {
        initPicture();
        initCategory();
        initPoint();
        isGwEditor();
        // 初始化官网样式
        initWebcolor();
    }

    private void initWebcolor() {
        DynamicObjectCollection aosLinentityGwS = this.getModel().getEntryEntity("aos_linentity_gw");
        if (aosLinentityGwS != null && aosLinentityGwS.size() > 0) {
            this.getView().setVisible(true, "aos_flexpanelap5");
            Label label = this.getView().getControl("aos_labelap");
            label.setText("本单存在官网关键词！");
        } else {
            this.getView().setVisible(false, "aos_flexpanelap5");
        }

    }

    private void initPoint() {
        this.getView().setVisible(false, "aos_pointses");
        this.getView().setVisible(false, "aos_pointsfr");
        Object aosOrgid = this.getModel().getValue("aos_orgid");
        if (aosOrgid != null) {
            DynamicObject aosOrg = (DynamicObject)aosOrgid;
            String aosOrgnumber = aosOrg.getString("number");
            if (US.equals(aosOrgnumber)) {
                this.getView().setVisible(true, "aos_pointses");
            } else if (CA.equals(aosOrgnumber)) {
                this.getView().setVisible(true, "aos_pointsfr");
            }
        }
    }

    /**
     * 初始化图片
     */
    private void initPicture() {
        aosEgskuChange();
    }

    @Override
    public void afterCreateNewData(EventObject e) {
        aosEgskuChange();
        initCategory();
        initOrg();
        initPoint();
        isGwEditor();
        this.getView().setVisible(false, "aos_flexpanelap5");
    }

    /**
     * 初始化国别
     */
    private void initOrg() {
        long currentUserId = UserServiceHelper.getCurrentUserId();
        DynamicObject aosMktUseright = QueryServiceHelper.queryOne("aos_mkt_userights",
            "entryentity.aos_orgid aos_orgid", new QFilter[] {new QFilter("aos_user", QCP.equals, currentUserId)});
        long aosOrgid = aosMktUseright.getLong("aos_orgid");
        this.getModel().setValue("aos_orgid", aosOrgid);
    }

    /**
     * 初始化类别
     */
    private void initCategory() {
        Object aosCategory1 = this.getModel().getValue("aos_category1");
        Object aosCategory2 = this.getModel().getValue("aos_category2");
        Object aosCategory3 = this.getModel().getValue("aos_category3");
        ComboEdit comboEdit = this.getControl("aos_category1");
        ComboEdit comboEdit2 = this.getControl("aos_category2");
        ComboEdit comboEdit3 = this.getControl("aos_category3");
        List<ComboItem> data = new ArrayList<>();
        List<ComboItem> data2 = new ArrayList<>();
        List<ComboItem> data3 = new ArrayList<>();
        // 大类
        QFilter filterLevel = new QFilter("level", "=", "1");
        QFilter filterDivide = new QFilter("name", "!=", "待分类");
        QFilter filterOrg = new QFilter("standard.name", "=", "物料基本分类标准");
        QFilter[] filtersGroup = new QFilter[] {filterLevel, filterDivide, filterOrg};
        String selectGroup = "name,level";
        DataSet bdMaterialgroupS = QueryServiceHelper.queryDataSet(
            this.getClass().getName() + "." + "bd_materialgroupS", "bd_materialgroup", selectGroup, filtersGroup, null);
        while (bdMaterialgroupS.hasNext()) {
            Row bdMaterialgroup = bdMaterialgroupS.next();
            // 获取数据
            String categoryName = bdMaterialgroup.getString("name");
            data.add(new ComboItem(new LocaleString(categoryName), categoryName));
        }
        bdMaterialgroupS.close();
        comboEdit.setComboItems(data);
        if (aosCategory1 == null) {
            comboEdit2.setComboItems(null);
            comboEdit3.setComboItems(null);
        } else {
            filterLevel = new QFilter("level", "=", "2");
            filterDivide = new QFilter("parent.name", "=", aosCategory1);
            filtersGroup = new QFilter[] {filterLevel, filterDivide};
            selectGroup = "name,level";
            DataSet bdMaterialgroup2S =
                QueryServiceHelper.queryDataSet(this.getClass().getName() + "." + "bd_materialgroup2S",
                    "bd_materialgroup", selectGroup, filtersGroup, null);
            while (bdMaterialgroup2S.hasNext()) {
                Row bdMaterialgroup2 = bdMaterialgroup2S.next();
                // 获取数据
                String categoryName;
                String[] names = bdMaterialgroup2.getString("name").split(",");
                if (names.length > 1) {
                    categoryName = names[1];
                } else {
                    continue;
                }
                data2.add(new ComboItem(new LocaleString(categoryName), categoryName));
            }
            bdMaterialgroup2S.close();
            comboEdit2.setComboItems(data2);
            if (aosCategory2 == null) {
                comboEdit3.setComboItems(null);
            } else {
                filterLevel = new QFilter("level", "=", "3");
                filterDivide = new QFilter("parent.name", "=", aosCategory1 + "," + aosCategory2);
                filtersGroup = new QFilter[] {filterLevel, filterDivide};
                selectGroup = "name,level";
                DataSet bdMaterialgroup3S =
                    QueryServiceHelper.queryDataSet(this.getClass().getName() + "." + "bd_materialgroup3S",
                        "bd_materialgroup", selectGroup, filtersGroup, null);
                while (bdMaterialgroup3S.hasNext()) {
                    Row bdMaterialgroup3 = bdMaterialgroup3S.next();
                    // 获取数据
                    String categoryName;
                    String[] names = bdMaterialgroup3.getString("name").split(",");
                    if (names.length > 2) {
                        categoryName = names[2];
                    } else {
                        continue;
                    }
                    data3.add(new ComboItem(new LocaleString(categoryName), categoryName));
                }
                bdMaterialgroup3S.close();
                comboEdit3.setComboItems(data3);
            }
        }
        // 根据下拉列表的大中小类赋值文本的大中小类
        if (FndGlobal.IsNull(aosCategory1)) {
            this.getModel().setValue("aos_category1_name", null);
            this.getModel().setValue("aos_category2_name", null);
            this.getModel().setValue("aos_category3_name", null);
        } else {
            if (FndGlobal.IsNull(aosCategory2)) {
                this.getModel().setValue("aos_category2_name", null);
                this.getModel().setValue("aos_category3_name", null);
            } else {
                this.getModel().setValue("aos_category2_name", aosCategory2);
                if (FndGlobal.IsNull(aosCategory3)) {
                    this.getModel().setValue("aos_category3_name", null);
                } else {
                    this.getModel().setValue("aos_category3_name", aosCategory3);
                }
            }
            this.getModel().setValue("aos_category1_name", this.getModel().getValue("aos_category1"));
        }
    }

    @Override
    public void beforeImportData(BeforeImportDataEventArgs e) {
        try {
            // 获取导入类型
            Map<String, Object> option = e.getOption();
            String importtype = obj2String(option.get("importtype"));
            Map<String, Object> sourceData = e.getSourceData();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>)sourceData.get("aos_linentity");
            @SuppressWarnings("unchecked")
            Object aosOrgid = ((Map<String, Object>)sourceData.get("aos_orgid")).get("number");
            Object aosCategory1 = sourceData.get("aos_category1");
            Object aosCategory2 = sourceData.get("aos_category2");
            Object aosCategory3 = sourceData.get("aos_category3");
            Object aosItemnamecn = sourceData.get("aos_itemnamecn");
            int rows = 0;
            if (list != null) {
                rows = list.size();
            }
            Object aosOrgId = FndGlobal.get_import_id(aosOrgid.toString(), "bd_country");
            if (StringUtils.equals(importtype, OVERRIDENEW)) {
                for (int l = 0; l < rows; l++) {
                    String importMainvoc = list.get(l).get("aos_mainvoc").toString();
                    String sql = " DELETE FROM tk_aos_mkt_point_r r  WHERE 1=1 " + " and r.fk_aos_mainvoc = ? "
                        + " and exists (select 1 from tk_aos_mkt_point t " + " where 1=1 " + " and t.fid = r.fid "
                        + " and t.fk_aos_category1 = ? " + " and t.fk_aos_category2 = ? "
                        + " and t.fk_aos_category3 = ? " + " and t.fk_aos_itemname = ?" + " and t.fk_aos_orgid =  ? )";
                    Object[] params =
                        {importMainvoc, aosCategory1, aosCategory2, aosCategory3, aosItemnamecn, aosOrgId};
                    DB.execute(DBRoute.of(DB_MKT), sql, params);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void afterImportData(ImportDataEventArgs e) {
        try {
            Map<String, Object> sourceData = e.getSourceData();
            Object aosCategory1 = sourceData.get("aos_category1");
            Object aosCategory2 = sourceData.get("aos_category2");
            DynamicObject dynamicObject = getEditorInfo(obj2String(aosCategory1) + "," + obj2String(aosCategory2));
            DynamicObject dynamicObject2 = getEditorInfo2(obj2String(aosCategory1), obj2String(aosCategory2));
            this.getModel().setValue("aos_user", dynamicObject2.getString("aos_edmanar"));
            this.getModel().setValue("aos_groupid", dynamicObject.getString("aos_group_edit"));
            this.getModel().setValue("aos_confirmor", dynamicObject.getString("aos_edit_leader"));
            setCate(this.getModel().getDataEntity(true));
        } catch (Exception exception) {
            logger.error("导入后出现异常:" + DateFormatUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss") + "--"
                + SalUtil.getExceptionStr(exception));
        }
    }

    @Override
    public void beforeF7Select(BeforeF7SelectEvent beforeF7SelectEvent) {
        // 国别权限控制
        try {
            String name = beforeF7SelectEvent.getProperty().getName();
            // 获取当前人员id
            long currentUserId = UserServiceHelper.getCurrentUserId();
            if (StringUtils.equals(name, AOS_ORGID)) {
                DynamicObjectCollection list =
                    QueryServiceHelper.query("aos_mkt_userights", "entryentity.aos_orgid aos_orgid",
                        new QFilter[] {new QFilter("aos_user", QCP.equals, currentUserId)});
                List<String> orgList = new ArrayList<>();
                for (DynamicObject obj : list) {
                    orgList.add(obj.getString("aos_orgid"));
                }
                QFilter qFilter = new QFilter("id", QCP.in, orgList);
                ListShowParameter showParameter = (ListShowParameter)beforeF7SelectEvent.getFormShowParameter();
                showParameter.getListFilterParameter().getQFilters().add(qFilter);
            }
        } catch (Exception ex) {
            this.getView().showErrorNotification("beforeF7Select = " + ex);
        }
    }

    private void showBill(String selectPkid) {
        // 当工作交接安排控件被点击的时候，创建弹出表单页面的对象
        BillShowParameter billShowParameter = new BillShowParameter();
        // 设置弹出表单页面的标识
        billShowParameter.setFormId("aos_mkt_keyword");
        // 设置弹出表单页面数据的主键id
        billShowParameter.setPkId(selectPkid);
        // 设置弹出表单页面的标题
        billShowParameter.setCaption("SKU关键词库");
        // 设置弹出表单页面的打开方式
        billShowParameter.getOpenStyle().setShowType(ShowType.Modal);
        // 可全屏
        billShowParameter.setShowFullScreen(true);
        StyleCss styleCss = new StyleCss();
        styleCss.setHeight("800");
        styleCss.setWidth("1200");
        // 设置弹出表单的样式，宽，高等
        billShowParameter.getOpenStyle().setInlineStyleCss(styleCss);
        this.getView().showForm(billShowParameter);
    }

    @Override
    public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
        int rowIndex = hyperLinkClickEvent.getRowIndex();
        String fieldName = hyperLinkClickEvent.getFieldName();
        if (AOS_ITEMID.equals(fieldName)) {
            // 获取国别+物料
            DynamicObject aosOrgid = (DynamicObject)this.getModel().getValue("aos_orgid");
            DynamicObject aosItemid = (DynamicObject)this.getModel().getValue("aos_itemid", rowIndex);

            if (aosOrgid == null || aosItemid == null) {
                this.getView().showTipNotification("国别和物料不为空才能跳转至SKU关键词库!");
                return;
            }

            DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_keyword", "id",
                new QFilter[] {new QFilter("aos_orgid", QCP.equals, aosOrgid.getString("id")),
                    new QFilter("aos_itemid", QCP.equals, aosItemid.getString("id")),});

            if (dynamicObject == null) {
                this.getView().showTipNotification("没有此国别+物料的SKU关键词库!");
                return;
            }

            String id = dynamicObject.getString("id");
            showBill(id);
        }
    }

    /**
     * 官网编辑只能使用官网页签
     */
    private void isGwEditor() {
        long currentUserId = UserServiceHelper.getCurrentUserId();
        boolean exists = QueryServiceHelper.exists("perm_userrole", new QFilter[] {
            new QFilter("user", QCP.equals, currentUserId), new QFilter("role.name", QCP.equals, "官网编辑")});
        if (exists) {
            this.getView().setEnable(false, "aos_flexpanelap1");
            this.getView().setEnable(false, "aos_advconap");
            this.getView().setEnable(false, "aos_advconap1");
        }
    }

    @Override
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        String language = Lang.get().getLocale().getLanguage();
        if (language.equals(ZH)) {
            if (Cux_Common_Utl.IsNull(this.getModel().getValue(AOS_EN_CATEGORY1))) {
                setCate(this.getModel().getDataEntity(true));
                this.getView().invokeOperation("save");
            }
        }
    }

    /** 不同的语言环境设置不同的可见性 **/
    public void statusControl() {
        String language = Lang.get().getLocale().getLanguage();
        List<String> list;
        if (language.equals(ZH)) {
            list = Arrays.asList("aos_en_category1", "aos_en_category2", "aos_en_category3");
        }
        // en
        else {
            list = Arrays.asList("aos_category1", "aos_category2", "aos_category3");
        }
        this.getView().setVisible(false, list.toArray(new String[0]));
    }
}