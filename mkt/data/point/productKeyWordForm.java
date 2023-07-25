package mkt.data.point;

import common.Cux_Common_Utl;
import common.fnd.FndError;
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
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
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
import kd.fi.bd.util.QFBuilder;
import mkt.common.util.arrangeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import sal.synciface.imp.aos_sal_import_pub;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author create by gk
 * @date 2023/7/24 9:59
 * @action  品名关键词库界面插件
 */
@SuppressWarnings("unchecked")
public class productKeyWordForm extends AbstractBillPlugIn implements RowClickEventListener, BeforeF7SelectListener, HyperLinkClickListener {
    public final static String DB_MKT = "aos.mkt";// 供应链库
    private static final Log logger = LogFactory.getLog(aos_mkt_point_bill.class);

    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        statusControl();
    }

    @Override
    public void registerListener(EventObject e) {
        try {
            BasedataEdit aos_orgid = this.getControl("aos_orgid");// 根据人员过滤国别
            aos_orgid.addBeforeF7SelectListener(this);

            EntryGrid aos_itementity = this.getControl("aos_itementity");
            aos_itementity.addHyperClickListener(this);
            this.addItemClickListeners("tbmain");
        } catch (Exception ex) {
            this.getView().showErrorNotification("registerListener = " + ex.toString());
        }
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String itemKey = evt.getItemKey();
        if (itemKey.equals("aos_cal")){
           try {
               setUpACategory();
           }catch (FndError error){
               getView().showErrorNotification(error.getErrorMessage());
           }
           catch (Exception e){
               getView().showErrorNotification(e.getMessage());
           }
        }
        else if (itemKey.equals("aos_split")){

        }
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (name.equals("aos_category1") || name.equals("aos_category2") || name.equals("aos_category3")) {
            init_category();
            Get_points();
        }
        else if (name.equals("aos_egsku")) {
            aos_egsku_change();
        }
        else if (name.equals("aos_orgid")) {
            init_point();
        }
        else if (name.equals("aos_itemnamecn")) {
            init_point();
            Get_points();
        }
        if ("aos_category2".equals(name) || "aos_category1".equals(name)) {
            autoSetCategoryInfo();
        }
    }

    @Override
    public void afterLoadData(EventObject e) {
        init_picture();
        init_category();
        init_point();
    }

    @Override
    public void afterCreateNewData(EventObject e) {
        aos_egsku_change();
        init_category();
        init_org();
        init_point();
    }

    @Override
    public void beforeImportData(BeforeImportDataEventArgs e) {
        try {
            // 获取导入类型
            Map<String, Object> option = e.getOption();
            String importtype = obj2String(option.get("importtype"));
            Map<String, Object> sourceData = e.getSourceData();
            List<Map<String, Object>> list = (List<Map<String, Object>>) sourceData.get("aos_linentity");
            Object aos_orgid = ((Map<String, Object>) sourceData.get("aos_orgid")).get("number");
            Object aos_category1 = sourceData.get("aos_category1");
            Object aos_category2 = sourceData.get("aos_category2");
            Object aos_category3 = sourceData.get("aos_category3");
            Object aos_itemnamecn = sourceData.get("aos_itemnamecn");

            int rows = 0;
            if (list != null) {
                rows = list.size();
            }
            Object aos_org_id = aos_sal_import_pub.get_import_id(aos_orgid.toString(), "bd_country");

            if (StringUtils.equals(importtype, "overridenew")) {
                for (int l = 0; l < rows; l++) {

                    String import_mainvoc = list.get(l).get("aos_mainvoc").toString();

                    String sql = " DELETE FROM tk_aos_mkt_point_r r  WHERE 1=1 " + " and r.fk_aos_mainvoc = ? "
                            + " and exists (select 1 from tk_aos_mkt_point t " + " where 1=1 " + " and t.fid = r.fid "
                            + " and t.fk_aos_category1 = ? " + " and t.fk_aos_category2 = ? "
                            + " and t.fk_aos_category3 = ? " + " and t.fk_aos_itemname = ?"
                            + " and t.fk_aos_orgid =  ? )";
                    Object[] params = { import_mainvoc, aos_category1, aos_category2, aos_category3, aos_itemnamecn,
                            aos_org_id };
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
            Object aos_category1 = sourceData.get("aos_category1");
            Object aos_category2 = sourceData.get("aos_category2");
            DynamicObject dynamicObject = getEditorInfo(obj2String(aos_category1) + "," + obj2String(aos_category2));
            this.getModel().setValue("aos_user", dynamicObject.getString("aos_editor"));
            this.getModel().setValue("aos_groupid", dynamicObject.getString("aos_group_edit"));
            this.getModel().setValue("aos_confirmor", dynamicObject.getString("aos_edit_leader"));
            setCate(this.getModel().getDataEntity(true));
        } catch (Exception exception) {
            logger.error("导入后出现异常:" + DateFormatUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss") +"--" + SalUtil.getExceptionStr(exception));
        }
    }

    @Override
    public void beforeF7Select(BeforeF7SelectEvent beforeF7SelectEvent) {
        // 国别权限控制
        try {
            String name = beforeF7SelectEvent.getProperty().getName();
            // 获取当前人员id
            long CurrentUserId = UserServiceHelper.getCurrentUserId();
            if (StringUtils.equals(name, "aos_orgid")) {
                DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_userights",
                        "entryentity.aos_orgid aos_orgid",
                        new QFilter[] { new QFilter("aos_user", QCP.equals, CurrentUserId) });
                List<String> orgList = new ArrayList<>();
                for (DynamicObject obj : list) {
                    orgList.add(obj.getString("aos_orgid"));
                }
                QFilter qFilter = new QFilter("id", QCP.in, orgList);
                ListShowParameter showParameter = (ListShowParameter) beforeF7SelectEvent.getFormShowParameter();
                showParameter.getListFilterParameter().getQFilters().add(qFilter);
            }
        } catch (Exception ex) {
            this.getView().showErrorNotification("beforeF7Select = " + ex.toString());
        }
    }

    @Override
    public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
        int rowIndex = hyperLinkClickEvent.getRowIndex();
        String fieldName = hyperLinkClickEvent.getFieldName();
        if ("aos_itemid".equals(fieldName)) {
            //  获取国别+物料
            DynamicObject aos_orgid = (DynamicObject) this.getModel().getValue("aos_orgid");
            DynamicObject aos_itemid = (DynamicObject) this.getModel().getValue("aos_itemid", rowIndex);

            if (aos_orgid == null || aos_itemid == null) {
                this.getView().showTipNotification("国别和物料不为空才能跳转至SKU关键词库!");
                return;
            }

            DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_keyword", "id", new QFilter[]{
                    new QFilter("aos_orgid", QCP.equals, aos_orgid.getString("id")),
                    new QFilter("aos_itemid", QCP.equals, aos_itemid.getString("id")),
            });

            if (dynamicObject == null) {
                this.getView().showTipNotification("没有此国别+物料的SKU关键词库!");
                return;
            }

            String id = dynamicObject.getString("id");
            showBill(id);
        }
    }

    @Override
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        String language = Lang.get().getLocale().getLanguage();
        if (language.equals("zh")){
            if (Cux_Common_Utl.IsNull(this.getModel().getValue("aos_en_category1"))){
                setCate(this.getModel().getDataEntity(true));
                this.getView().invokeOperation("save");
            }
        }
    }

    private void autoSetCategoryInfo() {
        String aos_category1 = obj2String(this.getModel().getValue("aos_category1"));
        String aos_category2 = obj2String(this.getModel().getValue("aos_category2"));
        String aos_category = aos_category1 + "," + aos_category2;
        // 产品中类改变自动带出编辑
        DynamicObject dynamicObject = getEditorInfo(aos_category);
        if (dynamicObject != null) {
            this.getModel().setValue("aos_user", dynamicObject.getString("aos_editor"));
            this.getModel().setValue("aos_groupid", dynamicObject.getString("aos_group_edit"));
            this.getModel().setValue("aos_confirmor", dynamicObject.getString("aos_edit_leader"));
        } else {
            this.getModel().setValue("aos_user", null);
            this.getModel().setValue("aos_groupid", null);
            this.getModel().setValue("aos_confirmor", null);
        }
    }

    /**
     * 获取编辑
     * @param aos_category 组别
     * @return 组别编辑等信息
     */
    private DynamicObject getEditorInfo(String aos_category) {
        return QueryServiceHelper.queryOne("aos_mkt_groupinfo", "aos_editor,aos_group_edit,aos_edit_leader", new QFilter[]{
                new QFilter("aos_category2.name", QCP.equals, aos_category)
        });
    }

    private void Get_points() {
        Object aos_category1 = this.getModel().getValue("aos_category1");
        Object aos_category2 = this.getModel().getValue("aos_category2");
        Object aos_category3 = this.getModel().getValue("aos_category3");
        Object aos_itemnamecn = this.getModel().getValue("aos_itemnamecn");
        if (aos_category1 != null && aos_category2 != null && aos_category3 != null && aos_itemnamecn != null
                && aos_category1.toString() != "" && aos_category2.toString() != "" && aos_category3.toString() != ""
                && aos_itemnamecn.toString() != "") {
            QFilter filter_category1 = new QFilter("aos_category1", "=", aos_category1);
            QFilter filter_category2 = new QFilter("aos_category2", "=", aos_category2);
            QFilter filter_category3 = new QFilter("aos_category3", "=", aos_category3);
            QFilter[] filters = new QFilter[] { filter_category1, filter_category2, filter_category3 };
            String SelectField = "aos_itemnamefr,aos_itemnamees";
            DynamicObject aos_mkt_data_slogan = QueryServiceHelper.queryOne("aos_mkt_data_slogan", SelectField,
                    filters);
            if (aos_mkt_data_slogan != null) {
                String aos_itemnamefr = aos_mkt_data_slogan.getString("aos_itemnamefr");
                this.getModel().setValue("aos_pointsfr", aos_itemnamefr);
            }
        }
    }

    private void aos_egsku_change() {
        Object aos_egsku = this.getModel().getValue("aos_egsku");
        if (aos_egsku == null) {
            this.getView().setVisible(true, "aos_picture");
            this.getView().setVisible(false, "aos_image");
        } else {
            this.getView().setVisible(false, "aos_picture");
            this.getView().setVisible(true, "aos_image");
            String item_number = ((DynamicObject) aos_egsku).getString("number");
            String url = "https://cls3.s3.amazonaws.com/" + item_number + "/1-1.jpg";
            Image image = this.getControl("aos_image");
            image.setUrl(url);
            this.getModel().setValue("aos_picture", url);
        }
    }

    private void init_point() {
        this.getView().setVisible(false, "aos_pointsfr");
        Object aos_orgid = this.getModel().getValue("aos_orgid");
        if (aos_orgid != null) {
            DynamicObject aos_org = (DynamicObject) aos_orgid;
            String aos_orgnumber = aos_org.getString("number");
            if (aos_orgnumber.equals("CA")) {
                this.getView().setVisible(true, "aos_pointsfr");
            }
        }
    }

    /**
     * 初始化图片
     */
    private void init_picture() {
        aos_egsku_change();
    }

    /**
     * 初始化国别
     */
    private void init_org() {
        long CurrentUserId = UserServiceHelper.getCurrentUserId();
        DynamicObject aos_mkt_useright = QueryServiceHelper.queryOne("aos_mkt_userights",
                "entryentity.aos_orgid aos_orgid",
                new QFilter[] { new QFilter("aos_user", QCP.equals, CurrentUserId) });
        long aos_orgid = aos_mkt_useright.getLong("aos_orgid");
        this.getModel().setValue("aos_orgid", aos_orgid);
    }

    /**
     * 初始化类别
     */
    private void init_category() {
        Object aos_category1 = this.getModel().getValue("aos_category1");
        Object aos_category2 = this.getModel().getValue("aos_category2");
        Object aos_category3 = this.getModel().getValue("aos_category3");
        ComboEdit comboEdit = this.getControl("aos_category1");
        ComboEdit comboEdit2 = this.getControl("aos_category2");
        ComboEdit comboEdit3 = this.getControl("aos_category3");
        List<ComboItem> data = new ArrayList<>();
        List<ComboItem> data2 = new ArrayList<>();
        List<ComboItem> data3 = new ArrayList<>();
        // 大类
        QFilter filter_level = new QFilter("level", "=", "1");
        QFilter filter_divide = new QFilter("name", "!=", "待分类");
        QFilter filter_org = new QFilter("standard.name", "=", "物料基本分类标准");
        QFilter[] filters_group = new QFilter[] { filter_level, filter_divide ,filter_org};
        String select_group = "name,level";
        DataSet bd_materialgroupS = QueryServiceHelper.queryDataSet(
                this.getClass().getName() + "." + "bd_materialgroupS", "bd_materialgroup", select_group, filters_group,
                null);
        while (bd_materialgroupS.hasNext()) {
            Row bd_materialgroup = bd_materialgroupS.next();
            // 获取数据
            String category_name = bd_materialgroup.getString("name");
            data.add(new ComboItem(new LocaleString(category_name), category_name));
        }
        bd_materialgroupS.close();
        comboEdit.setComboItems(data);
        if (aos_category1 == null) {
            comboEdit2.setComboItems(null);
            comboEdit3.setComboItems(null);
        } else {
            filter_level = new QFilter("level", "=", "2");
            filter_divide = new QFilter("parent.name", "=", aos_category1);
            filters_group = new QFilter[] { filter_level, filter_divide };
            select_group = "name,level";
            DataSet bd_materialgroup2S = QueryServiceHelper.queryDataSet(
                    this.getClass().getName() + "." + "bd_materialgroup2S", "bd_materialgroup", select_group,
                    filters_group, null);
            while (bd_materialgroup2S.hasNext()) {
                Row bd_materialgroup2 = bd_materialgroup2S.next();
                // 获取数据
                String category_name = bd_materialgroup2.getString("name").replace(aos_category1 + ",", "");
                data2.add(new ComboItem(new LocaleString(category_name), category_name));
            }
            bd_materialgroup2S.close();
            comboEdit2.setComboItems(data2);
            if (aos_category2 == null) {
                comboEdit3.setComboItems(null);
            } else {
                filter_level = new QFilter("level", "=", "3");
                filter_divide = new QFilter("parent.name", "=", aos_category1 + "," + aos_category2);
                filters_group = new QFilter[] { filter_level, filter_divide };
                select_group = "name,level";

                DataSet bd_materialgroup3S = QueryServiceHelper.queryDataSet(
                        this.getClass().getName() + "." + "bd_materialgroup3S", "bd_materialgroup", select_group,
                        filters_group, null);
                while (bd_materialgroup3S.hasNext()) {
                    Row bd_materialgroup3 = bd_materialgroup3S.next();
                    // 获取数据
                    String category_name = bd_materialgroup3.getString("name").replace(aos_category1 + ",", "")
                            .replace(aos_category2 + ",", "");
                    data3.add(new ComboItem(new LocaleString(category_name), category_name));
                }
                bd_materialgroup3S.close();
                comboEdit3.setComboItems(data3);
            }
        }
        //根据下拉列表的大中小类赋值文本的大中小类
        if (aos_category1 == null || aos_category1.equals("")){
            this.getModel().setValue("aos_category1_name",null);
            this.getModel().setValue("aos_category2_name",null);
            this.getModel().setValue("aos_category3_name",null);
        }else{
            if (aos_category2 == null || aos_category2.equals("")){
                this.getModel().setValue("aos_category2_name",null);
                this.getModel().setValue("aos_category3_name",null);
            }else{
                this.getModel().setValue("aos_category2_name",aos_category2);
                if (aos_category3 == null || aos_category3.equals("")){
                    this.getModel().setValue("aos_category3_name",null);
                }else {
                    this.getModel().setValue("aos_category3_name",aos_category3);
                }
            }
            this.getModel().setValue("aos_category1_name",this.getModel().getValue("aos_category1"));
        }
    }

    private void showBill(String selectPkid) {
        //当工作交接安排控件被点击的时候，创建弹出表单页面的对象
        BillShowParameter billShowParameter = new BillShowParameter();
        //设置弹出表单页面的标识
        billShowParameter.setFormId("aos_mkt_keyword");
        //设置弹出表单页面数据的主键id
        billShowParameter.setPkId(selectPkid);
        //设置弹出表单页面的标题
        billShowParameter.setCaption("SKU关键词库");
        //设置弹出表单页面的打开方式
        billShowParameter.getOpenStyle().setShowType(ShowType.Modal);

        billShowParameter.setShowFullScreen(true);//可全屏
        StyleCss styleCss = new StyleCss();
        styleCss.setHeight("800");
        styleCss.setWidth("1200");
        //设置弹出表单的样式，宽，高等
        billShowParameter.getOpenStyle().setInlineStyleCss(styleCss);
        this.getView().showForm(billShowParameter);
    }

    /** 设置文本的类别  **/
    public static void setCate(DynamicObject dy_main){
        StringJoiner str = new StringJoiner(",");
        for (int i =1;i<4;i++){
            str.add(dy_main.getString("aos_category"+i));
            dy_main.set("aos_category"+i+"_name",dy_main.getString("aos_category"+i));
        }
        QFBuilder builder = new QFBuilder();
        builder.add("name","=",str.toString());
        DynamicObject[] cate = BusinessDataServiceHelper.load("bd_materialgroup", "name", builder.toArray());
        if (cate.length>0) {
            ILocaleString name = cate[0].getLocaleString("name");
            String value_en = name.getLocaleValue_en();
            String[] split = value_en.split(",");
            if (split.length>0)
                dy_main.set("aos_en_category1",split[0]);
            if (split.length>1)
                dy_main.set("aos_en_category2",split[1]);
            if (split.length>2)
                dy_main.set("aos_en_category3",split[2]);
        }

    }

    /** 不同的语言环境设置不同的可见性**/
    public void statusControl(){
        String language = Lang.get().getLocale().getLanguage();
        List<String> list;
        if (language.equals("zh")){
            list = Arrays.asList("aos_en_category1", "aos_en_category2", "aos_en_category3");
        }
        //en
        else{
            list = Arrays.asList("aos_category1", "aos_category2", "aos_category3");
        }
        this.getView().setVisible(false,list.toArray(new String[0]));
    }

    public static String obj2String(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    /**
     * 计算分类
     */
    private void setUpACategory() throws ParseException {
        DynamicObjectCollection linentity = getModel().getDataEntity(true).getDynamicObjectCollection("aos_linentity");
        if (linentity.size()==0)
            return;
        //根据单据信息查找相关性标准
        QFBuilder builder = new QFBuilder();
        DynamicObject orgEntity = this.getModel().getDataEntity(true).getDynamicObject("aos_orgid");
        builder.add("aos_org","=",orgEntity.getPkValue());
        StringJoiner str = new StringJoiner(",");
        for (int i = 1; i < 4; i++) {
            str.add(getModel().getValue("aos_category"+i).toString());
        }
        builder.add("aos_cate.name","=",str.toString());
        builder.add("aos_item_name","=",getModel().getValue("aos_itemnamecn").toString());
        builder.add("aos_relate1",">=",0);
        builder.add("aos_relate2",">=",0);
        builder.add("aos_relate3",">=",0);
        builder.add("aos_search1",">=",0);
        builder.add("aos_search2",">=",0);
        str = new StringJoiner(",");
        for (int i = 1; i < 4; i++) {
            str.add("aos_relate"+i);
        }
        str.add("aos_search1");
        str.add("aos_search2");
        DynamicObject dy_parameter = QueryServiceHelper.queryOne("aos_mkt_keyword_par", "", builder.toArray());
        if (dy_parameter==null)
            throw new FndError("相关性参数未维护,请先维护相关参数");

        //确定高搜索的条数
        BigDecimal bd_high = dy_parameter.getBigDecimal("aos_search2");
        int highSearch = new BigDecimal(linentity.size()).multiply(bd_high).setScale(0, BigDecimal.ROUND_UP).intValue();
        //中搜索条数
        BigDecimal bd_mid = dy_parameter.getBigDecimal("aos_search1").subtract(bd_high);
        int midSearch = new BigDecimal(linentity.size()).multiply(bd_mid).setScale(0, BigDecimal.ROUND_UP).intValue();

        //高相关
        BigDecimal higRelate = dy_parameter.getBigDecimal("aos_relate3");
        //中相关
        BigDecimal midRelate = dy_parameter.getBigDecimal("aos_relate2");

        //根据搜索量从大到小排序
        List<DynamicObject> entityRows = linentity
                .stream()
                .sorted((dy1, dy2) -> {
                    int search1 = Optional.ofNullable(dy1.getInt("aos_search")).orElse(0);
                    int search2 = Optional.ofNullable(dy2.getInt("aos_search")).orElse(0);
                    return -(search1 - search2);
                })
                .collect(Collectors.toList());
        NumberFormat instance = NumberFormat.getInstance();
        for (int index = 0; index < entityRows.size(); index++) {
            //判断搜索
            String search = "";
            if (index<highSearch)
                search = "高搜索词";
            else if (index<(highSearch+midSearch))
                search = "中搜索词";
            else
                search = "低搜索词";
            //判断相关性
            String relevance = "";
            DynamicObject dy_row = entityRows.get(index);
            String correlate = dy_row.getLocaleString("aos_correlate").getLocaleValue_zh_CN();
            if (correlate.contains("高")) {
                relevance = "高相关";
            }
            else if (correlate.contains("中")){
                relevance = "中";
            }
            else if (correlate.contains("低")){
                relevance = "低";
            }
            else {
                BigDecimal bd_correlate;
                if (correlate.contains("%")){
                    bd_correlate = BigDecimal.valueOf(instance.parse(correlate).doubleValue());
                }
                else {
                    bd_correlate = new BigDecimal(correlate);
                }
                if (bd_correlate.compareTo(higRelate)>=0){
                    relevance = "高相关";
                }
                else if (bd_correlate.compareTo(midRelate)>=0){
                    relevance = "中相关";
                }
                else{
                    relevance = "低相关";
                }

            }
            dy_row.set("aos_sort",relevance+search);
        }
        getView().updateView("aos_linentity");
    }

    /**
     * 拆分词根
     */
    private void spiltWord(){
        getModel().deleteEntryData("aos_entryentity");
        DynamicObjectCollection entityRows = getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity");
        if (entityRows.size()==0) {
            return;
        }
        List<String> spiltKeyWord = new ArrayList<>();
        //搜索总量
        Map<String,Integer> map_search = new HashMap<>(spiltKeyWord.size());
        for (DynamicObject row : entityRows) {
            String keyWord = row.getString("aos_keyword");
            if (FndGlobal.IsNull(keyWord))
                continue;

            //搜索总量
            int search = Optional.ofNullable(row.getInt("aos_search")).orElse(0);
            String[] split = keyWord.split(" ");
            List<String> combinations = arrangeUtils.getCombinations(split);
            for (String combination : combinations) {
                if (!spiltKeyWord.contains(combination)) {
                    spiltKeyWord.add(combination);
                }
                int wordSearch = map_search.getOrDefault(combination, 0);
                wordSearch = wordSearch + search;
                map_search.put(combination,wordSearch);
            }
        }

        //获取拆分词的词频和搜索量
        Map<String,Integer> map_frequency = new HashMap<>(spiltKeyWord.size());
        Map<String,String> map_wordInfo = new HashMap<>();
        calculateWordFrequency(spiltKeyWord,map_wordInfo);


    }
    private void calculateWordFrequency(List<String> spildWord ,Map<String,String> map_wordInfo){
        QFBuilder builder = new QFBuilder();
        Object orgid = this.getModel().getDataEntity(true).getDynamicObject("aos_orgid").getPkValue();
        builder.add("aos_org","=",orgid);
        builder.add("aos_root","!=","");
        DynamicObjectCollection result = QueryServiceHelper.query("aos_mkt_root", "aos_root,aos_attribute,aos_value", builder.toArray());
        Map<String,Integer> map_frequency = new HashMap<>();
        for (DynamicObject dy_row : result) {
            String root = dy_row.getString("aos_root");
            for (String word : spildWord) {
                //完全相等
                if (root.equals(word)){
                    int wordFrequency = map_frequency.getOrDefault(word, 0);
                    wordFrequency++;
                    map_frequency.put(word,wordFrequency);
                    String info = dy_row.getString("aos_attribute")+"/"+dy_row.getString("aos_value");
                    map_wordInfo.put(word,info);
                }
                String[] splitWords = root.split(" ");
                for (String splitWord : splitWords) {
                    if (splitWord.equals(word)){
                        int wordFrequency = map_frequency.getOrDefault(word, 0);
                        wordFrequency++;
                        map_frequency.put(word,wordFrequency);
                    }
                }
            }

        }


    }
}
