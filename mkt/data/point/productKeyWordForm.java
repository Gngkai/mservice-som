package mkt.data.point;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.sal.sys.basedata.dao.ItemCategoryDao;
import common.sal.sys.basedata.dao.ItemDao;
import common.sal.sys.basedata.dao.impl.ItemCategoryDaoImpl;
import common.sal.sys.basedata.dao.impl.ItemDaoImpl;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.bill.BillShowParameter;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.ILocaleString;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.dataentity.serialization.SerializationUtils;
import kd.bos.entity.datamodel.events.BeforeImportDataEventArgs;
import kd.bos.entity.datamodel.events.ImportDataEventArgs;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.FormShowParameter;
import kd.bos.form.ShowType;
import kd.bos.form.StyleCss;
import kd.bos.form.control.Control;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.Image;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.events.*;
import kd.bos.form.field.BasedataEdit;
import kd.bos.form.field.ComboEdit;
import kd.bos.form.field.ComboItem;
import kd.bos.form.field.events.BeforeF7SelectEvent;
import kd.bos.form.field.events.BeforeF7SelectListener;
import kd.bos.form.operate.FormOperate;
import kd.bos.lang.Lang;
import kd.bos.list.ListShowParameter;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import kd.fi.bd.util.QFBuilder;
import mkt.common.util.arrangeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import sal.sche.aos_sal_sche_pub.aos_sal_sche_pvt;

import java.math.BigDecimal;
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
    private static final String KEY_ITEM = "item";  //物料值改变的是否由品名改变触发的标识

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

            this.addClickListeners("aos_buttonap");
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
            spiltWord();
        }
        else if (itemKey.equals("aos_open")){
            openView();
        }
        else if (itemKey.equals("aos_copy")){
            DynamicObject aos_orgid = (DynamicObject) this.getModel().getValue("aos_orgid");
            if (aos_orgid == null)
                return;
            FormShowParameter showParameter = FndGlobal.CraeteForm(this, "aos_mkt_point_from", "copyTo", null);
            showParameter.setCustomParam("org",aos_orgid.getString("id"));
            getView().showForm(showParameter);
        }
    }

    @Override
    public void click(EventObject evt) {
        super.click(evt);
        Control control = (Control) evt.getSource();
        String key = control.getKey();
        if (key.equals("aos_buttonap")){
            Map<String,Object> params = new HashMap<>();
            FndGlobal.OpenForm(this,"aos_mkt_point_sw",params);
        }
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (name.equals("aos_category1") || name.equals("aos_category2") || name.equals("aos_category3")) {
            init_category();
            Get_points();
            if ("aos_category2".equals(name) || "aos_category1".equals(name)) {
                autoSetCategoryInfo();
            }
        }
        else if (name.equals("aos_egsku")) {
            String value = getPageCache().get(KEY_ITEM);
            if (FndGlobal.IsNotNull(value) && Boolean.parseBoolean(value)){
                setCate();
            }
            aos_egsku_change();
            getPageCache().put(KEY_ITEM,String.valueOf(true));

        }
        else if (name.equals("aos_orgid")) {
            init_point();
        }
        else if (name.equals("aos_itemnamecn")) {
            init_point();
            Get_points();
            setItemEntity();
            setegItem();
        }
    }

    /**
     * 品名改变带出物料
     */
    private void setegItem(){
        Object itemnamecn = this.getModel().getValue("aos_itemnamecn");
        if (FndGlobal.IsNull(itemnamecn)){
            this.getModel().setValue("aos_egsku",null);
        }
        else {
            QFilter filter = new QFilter("name","=",itemnamecn);
            ItemDao itemDao = new ItemDaoImpl();
            DynamicObjectCollection dyc = itemDao.listItemObj("id", filter, null);
            if (dyc.size()==0){
                this.getModel().setValue("aos_egsku",null);
            }
            else {
                getPageCache().put(KEY_ITEM,String.valueOf(false));
                this.getModel().setValue("aos_egsku",dyc.get(0).get("id"));

            }
        }

    }

    /**
     * 根据推荐物料带出品名还和类别
     */
    private void setCate(){
        Object egSku = getModel().getValue("aos_egsku");
        if (FndGlobal.IsNotNull(egSku)){
            String itemid = ((DynamicObject)egSku).getString("id");
            String name = ((DynamicObject)egSku).getString("name");
            ItemCategoryDao categoryDao = new ItemCategoryDaoImpl();
            String itemCateNameZH = categoryDao.getItemCateNameZH(itemid);
            String[] split = itemCateNameZH.split(",");
            if (split.length>0){
                getModel().setValue("aos_category1",split[0]);
            }
            if (split.length>1){
                getModel().setValue("aos_category2",split[1]);
            }
            if (split.length>2){
                getModel().setValue("aos_category3",split[2]);
            }
            this.getModel().setValue("aos_itemnamecn",name);
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
        /*
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

         */
    }

    @Override
    public void afterImportData(ImportDataEventArgs e) {
        try {
            Map<String, Object> sourceData = e.getSourceData();
            Object aos_category1 = sourceData.get("aos_category1");
            Object aos_category2 = sourceData.get("aos_category2");
            DynamicObject dynamicObject = getEditorInfo(obj2String(aos_category1) + "," + obj2String(aos_category2));
            this.getModel().setValue("aos_user", dynamicObject.getString("aos_editor"));
            //this.getModel().setValue("aos_groupid", dynamicObject.getString("aos_group_edit"));
            //this.getModel().setValue("aos_confirmor", dynamicObject.getString("aos_edit_leader"));
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
    public void beforeDoOperation(BeforeDoOperationEventArgs args) {
        super.beforeDoOperation(args);
        if (args.getSource() instanceof FormOperate){
            FormOperate formOperate = (FormOperate) args.getSource();
            String operateKey = formOperate.getOperateKey();
            if (operateKey.equals("save")){
                beforeSave();
                args.setCancel(false);
            }
        }
    }

    @Override
    public void closedCallBack(ClosedCallBackEvent event) {
        super.closedCallBack(event);
        String actionId = event.getActionId();
        if (actionId.equals("aos_mkt_point_sw")){
            this.getView().updateView("aos_entryentity");
            getView().updateView("aos_linentity");
        }
        else if (actionId.equals("copyTo")){
            Object redata = event.getReturnData();
            if (redata == null)
                return;
            DynamicObjectCollection data = (DynamicObjectCollection) redata;
            if (data.size()==0) {
                return;
            }

            DynamicObjectCollection aos_ent = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_linentity");
            EntryGrid entryGrid = this.getControl("aos_linentity");
            List<String> list_control = entryGrid.getItems().stream().map(control -> control.getKey()).collect(Collectors.toList());
            List<String> list_allFields = aos_sal_sche_pvt.getDynamicObjectType(aos_ent.getDynamicObjectType());
            List<String> list_fields = new ArrayList<>(list_control.size());
            for (String key : list_control) {
                if (list_allFields.contains(key))
                    list_fields.add(key);
            }

            if (aos_ent.size()==0) {
                return;
            }
            DynamicObject dy_org = (DynamicObject) this.getModel().getValue("aos_orgid");
            QFBuilder builder = new QFBuilder();
            for (DynamicObject row : data) {
                builder.add("aos_orgid","=",dy_org.getPkValue());
                builder.add("id","!=",this.getModel().getDataEntity().getPkValue());
                builder.add("aos_itemnamecn","=",row.getString("aos_item"));
                String cate = row.getString("aos_cate");
                String[] split = cate.split(",");
                if (split.length>0){
                    builder.add("aos_category1","=",split[0]);
                }
                if (split.length>1){
                    builder.add("aos_category2","=",split[1]);
                }
                if (split.length>2){
                    builder.add("aos_category3","=",split[2]);
                }
                DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_point", "id", builder.toArray());
                for (DynamicObject dy_fid : dyc) {
                    DynamicObject dy_target = BusinessDataServiceHelper.loadSingle(dy_fid.getString("id"), "aos_mkt_point");
                    DynamicObjectCollection targetLine = dy_target.getDynamicObjectCollection("aos_linentity");
                    targetLine.removeIf(dy->true);
                    for (DynamicObject theRow : aos_ent) {
                        DynamicObject addRow = targetLine.addNew();
                        for (String field : list_fields) {
                            addRow.set(field,theRow.get(field));
                        }
                    }
                    SaveServiceHelper.save(new DynamicObject[]{dy_target});
                }
            }
            getView().showSuccessNotification("Copy To Success");
        }
    }

    //设置sku清单
    private void setItemEntity(){
        QFBuilder builder = new QFBuilder();
        DynamicObject aos_orgid = (DynamicObject) this.getModel().getValue("aos_orgid");
        if (aos_orgid!=null){
            builder.add("aos_orgid","=",aos_orgid.getPkValue());
        }
        builder.add("aos_category1","=",getModel().getValue("aos_category1"));
        builder.add("aos_category2","=",getModel().getValue("aos_category2"));
        builder.add("aos_category3","=",getModel().getValue("aos_category3"));
        builder.add("aos_itemname","=",getModel().getValue("aos_itemnamecn"));
        builder.add("aos_itemid","!=","");
        getModel().deleteEntryData("aos_itementity");
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_keyword", "aos_itemid,aos_itemid.number number", builder.toArray());
        for (DynamicObject row : dyc) {
            int index = getModel().createNewEntryRow("aos_itementity");
            this.getModel().setValue("aos_itemid",row.get("aos_itemid"),index);
            this.getModel().setValue("aos_picture1", aos_sal_sche_pvt.get_img_url(row.getString("number")),index);
        }
    }

    private void beforeSave() {
        String language = Lang.get().getLocale().getLanguage();
        if (language.equals("zh")){
            if (Cux_Common_Utl.IsNull(this.getModel().getValue("aos_en_category1"))){
                setCate(this.getModel().getDataEntity(true));
            }
        }
    }

    private static void setRelate(DynamicObject dy_main){
        DynamicObjectCollection entityRows = dy_main.getDynamicObjectCollection("aos_linentity");
        for (DynamicObject entityRow : entityRows) {
            ILocaleString correlate = entityRow.getLocaleString("aos_correlate");
            if (FndGlobal.IsNull(correlate.getLocaleValue_zh_CN())){
                correlate.setLocaleValue_en("");
            }
            else if (correlate.getLocaleValue_zh_CN().equals("高")){
                correlate.setLocaleValue_en("High");
            }
            else if (correlate.getLocaleValue_zh_CN().equals("中")){
                correlate.setLocaleValue_en("Medium");
            }
            else if (correlate.getLocaleValue_zh_CN().equals("低")){
                correlate.setLocaleValue_en("Low");
            }
            else {
                correlate.setLocaleValue_en(correlate.getLocaleValue_zh_CN());
            }
            entityRow.set("aos_correlate",correlate);
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
        } else {
            this.getModel().setValue("aos_user", null);
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
                String aos_itemnamees = aos_mkt_data_slogan.getString("aos_itemnamees");
                this.getModel().setValue("aos_pointses", aos_itemnamees);
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
        this.getView().setVisible(false, "aos_pointses");
        Object aos_orgid = this.getModel().getValue("aos_orgid");
        if (aos_orgid != null) {
            DynamicObject aos_org = (DynamicObject) aos_orgid;
            String aos_orgnumber = aos_org.getString("number");
            if (aos_orgnumber.equals("CA")) {
                this.getView().setVisible(true, "aos_pointsfr");
            }
            if (aos_orgnumber.equals("US")) {
                this.getView().setVisible(true, "aos_pointses");
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
        ComboEdit comboEditName = getControl("aos_itemnamecn");
        List<ComboItem> data = new ArrayList<>();
        List<ComboItem> data2 = new ArrayList<>();
        List<ComboItem> data3 = new ArrayList<>();
        List<ComboItem> nameData = new ArrayList<>();
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
        }
        else{
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
        //根据类别赋值品类名称
        if (FndGlobal.IsNotNull(aos_category1) && FndGlobal.IsNotNull(aos_category2) && FndGlobal.IsNotNull(aos_category3)){
            QFilter filter  = new QFilter("group.name","=",aos_category1+","+aos_category2+","+aos_category3);
            DynamicObjectCollection dyc = QueryServiceHelper.query("bd_materialgroupdetail", "material.name name", new QFilter[]{filter, filter_org});
            List<String> names = new ArrayList<>(dyc.size());
            for (DynamicObject dy : dyc) {
                String name = dy.getString("name");
                if (names.contains(name))
                    continue;
                names.add(name);
                nameData.add(new ComboItem(new LocaleString(name),name));
            }
        }
        comboEditName.setComboItems(nameData);
    }

    /**
     * 打开sku关键词库单据
     * @param selectPkid
     */
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
        styleCss.setWidth("1500");
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
            if (split.length>0){
                dy_main.set("aos_en_category1",split[0]);
                dy_main.set("aos_category1_name",split[0]);
            }
            if (split.length>1){
                dy_main.set("aos_en_category2",split[1]);
                dy_main.set("aos_category2_name",split[1]);
            }
            if (split.length>2){
                dy_main.set("aos_en_category3",split[2]);
                dy_main.set("aos_category3_name",split[2]);
            }
        }
        //设置相关性的英文
        setRelate(dy_main);
        //设置物料名
        String itemnamecn = dy_main.getString("aos_itemnamecn");
        dy_main.set("aos_itemnamecn_s",itemnamecn);
        //设置英文品名
        if (FndGlobal.IsNotNull(itemnamecn)){
            dy_main.set("aos_itemnameen",itemnamecn);
        }
        else{
            builder.clear();
            builder.add("name" ,"=",itemnamecn);
            DynamicObject dy = BusinessDataServiceHelper.loadSingle("bd_material", "name", builder.toArray());
            if (dy!=null){
                dy_main.set("aos_itemnameen",dy.getLocaleString("name").getLocaleValue_en());
            }
            else
                dy_main.set("aos_itemnameen","");
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
        builder.add("aos_item","=",getModel().getValue("aos_itemnamecn").toString());
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
        int highSearch = new BigDecimal(linentity.size()).multiply(bd_high).setScale(0, BigDecimal.ROUND_DOWN).intValue();
        //中搜索条数
        BigDecimal bd_mid = dy_parameter.getBigDecimal("aos_search1");
        int midSearch = new BigDecimal(linentity.size()).multiply(bd_mid).setScale(0, BigDecimal.ROUND_DOWN).intValue()-highSearch;

        //高相关
        BigDecimal higRelate = dy_parameter.getBigDecimal("aos_relate3");
        //中相关
        BigDecimal midRelate = dy_parameter.getBigDecimal("aos_relate2");

        //根据搜索量从大到小排序
        List<DynamicObject> entityRows = linentity
                .stream()
                .sorted((dy1, dy2) -> {
                    int search1 = Optional.of(dy1.getInt("aos_search")).orElse(0);
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
                relevance = "中相关";
            }
            else if (correlate.contains("低")){
                relevance = "低相关";
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
        DynamicObjectCollection entityRows = getModel().getDataEntity(true).getDynamicObjectCollection("aos_linentity");
        if (entityRows.size()==0) {
            return;
        }
        List<String> spiltKeyWord = new ArrayList<>();
        //搜索总量
        Map<String,Integer> map_search = new HashMap<>(spiltKeyWord.size());
        //词频
        Map<String,Integer> map_frequencyCount = new HashMap<>();
        //行信息
        Map<String,DynamicObject> map_oldRows = new HashMap<>();
        //品名词库的数据
        Map<String,DynamicObject> map_lineEntity = new HashMap<>();
        for (DynamicObject row : entityRows) {
            String keyWord = row.getString("aos_keyword");
            if (FndGlobal.IsNull(keyWord))
                continue;
            map_lineEntity.put(keyWord,row);

            //搜索总量
            int search = Optional.ofNullable(row.getInt("aos_search")).orElse(0);
            String[] split = keyWord.split(" ");
            List<String> combinations = arrangeUtils.seqCombinate(split);
            //将关键词拆分的结果结存
            getPageCache().put(keyWord, SerializationUtils.toJsonString(combinations));
            for (String combination : combinations) {
                if (!spiltKeyWord.contains(combination)) {
                    spiltKeyWord.add(combination);
                }
                int wordSearch = map_search.getOrDefault(combination, 0);
                wordSearch = wordSearch + search;
                map_search.put(combination,wordSearch);

                int frequency = map_frequencyCount.getOrDefault(combination, 0);
                frequency++;
                map_frequencyCount.put(combination,frequency);
            }
        }
        //将词根表之前的数据也添加进去
        DynamicObjectCollection aos_entryentity = getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject object : aos_entryentity) {
            String keyWord = object.getString("aos_root");
            if (FndGlobal.IsNotNull(keyWord)){
                if (!spiltKeyWord.contains(keyWord))
                    spiltKeyWord.add(keyWord);
            }
        }

        //获取拆分词的词频和搜索量
        Map<String,String> map_wordInfo = new HashMap<>();
        calculateWordFrequency(spiltKeyWord,map_wordInfo);
        Map<String,Integer> map_frequency = new LinkedHashMap<>(map_frequencyCount.size());
        if (map_frequencyCount.entrySet().size()>0){
            map_frequencyCount.entrySet().stream()
                    .sorted((o1,o2)->-(o1.getValue()-o2.getValue()))
                    .forEach(e-> map_frequency.put(e.getKey(),e.getValue()));
        }
        //数据填充

        for (DynamicObject dy : aos_entryentity) {
            if (FndGlobal.IsNotNull(dy.getString("aos_root")))
                map_oldRows.put(dy.getString("aos_root"),dy);
        }
        getModel().deleteEntryData("aos_entryentity");
        aos_entryentity = getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity");
        for (Map.Entry<String, Integer> entry : map_frequency.entrySet()) {
            DynamicObject newRow = aos_entryentity.addNew();
            newRow.set("aos_root",entry.getKey());

            if (map_oldRows.keySet().contains(entry.getKey())){
                DynamicObject oldRow = map_oldRows.get(entry.getKey());
                newRow.set("aos_root_attr",oldRow.get("aos_root_attr"));
                newRow.set("aos_root_value",oldRow.get("aos_root_value"));
                newRow.set("aos_total",oldRow.get("aos_total"));
            }
            else {
                if (map_wordInfo.containsKey(entry.getKey())){
                    String attrValue = map_wordInfo.get(entry.getKey());
                    if (FndGlobal.IsNotNull(attrValue)){
                        String[] split = attrValue.split("/");
                        if (split.length>0)
                            newRow.set("aos_root_attr",split[0]);
                        if (split.length>1)
                            newRow.set("aos_root_value",split[1]);
                    }
                }
            }
            newRow.set("aos_word_fre",entry.getValue());
            newRow.set("aos_total",map_search.getOrDefault(entry.getKey(),0));

            //设置品名数据库的信息
            if (map_lineEntity.containsKey(entry.getKey())) {
                DynamicObject lienRow = map_lineEntity.get(entry.getKey());
                lienRow.set("aos_attribute",newRow.get("aos_root_attr"));
            }
        }
        getView().updateView("aos_entryentity");
        getView().updateView("aos_linentity");
    }

    //查找属性标签和属性值
    private void calculateWordFrequency(List<String> spildWord ,Map<String,String> map_wordInfo){
        QFBuilder builder = new QFBuilder();
        Object orgid = this.getModel().getDataEntity(true).getDynamicObject("aos_orgid").getPkValue();
        builder.add("aos_org","=",orgid);
        builder.add("aos_root","!=","");
        builder.add("aos_root",QFilter.in,spildWord);
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
            }
        }

    }

    /**
     * 打开相关性弹窗
     */
    private void openView(){
        //查找相关性的id
        QFBuilder builder = new QFBuilder();
        Object aos_orgid = ((DynamicObject)getModel().getValue("aos_orgid")).get("id");
        builder.add("aos_org","=",aos_orgid);
        builder.add("aos_item","=",getModel().getValue("aos_itemnamecn"));
        StringJoiner str = new StringJoiner(",");
        str.add(getModel().getValue("aos_category1").toString());
        str.add(getModel().getValue("aos_category2").toString());
        str.add(getModel().getValue("aos_category3").toString());
        builder.add("aos_cate.name","=",str.toString());
        DynamicObject dy = QueryServiceHelper.queryOne("aos_mkt_keyword_par", "id", builder.toArray());
        Object pk;
        if (dy == null){
            builder.clear();
            builder.add("name","=",str.toString());
            DynamicObject cate = QueryServiceHelper.queryOne("bd_materialgroup", "id", builder.toArray());

            //如果为相关性为维护，则新建一单
            DynamicObject dy_parater = BusinessDataServiceHelper.newDynamicObject("aos_mkt_keyword_par");
            dy_parater.set("aos_cate",cate.get("id"));
            dy_parater.set("aos_item",getModel().getValue("aos_itemnamecn"));
            dy_parater.set("aos_org",aos_orgid);
            OperationResult result = SaveServiceHelper.saveOperate("aos_mkt_keyword_par", new DynamicObject[]{dy_parater}, OperateOption.create());
            pk = result.getSuccessPkIds().get(0);
        }
        else {
            pk = dy.get("id");
        }
        //创建弹出单据页面对象，并赋值
        BillShowParameter billShowParameter = new BillShowParameter();
        //设置弹出子单据页面的标识
        billShowParameter.setFormId("aos_mkt_keyword_par");
        //设置弹出子单据页面的标题
        //设置弹出子单据页面的打开方式
        billShowParameter.getOpenStyle().setShowType(ShowType.Modal);
        //设置弹出子单据页面的样式，高600宽800
        StyleCss inlineStyleCss = new StyleCss();
        inlineStyleCss.setHeight("700");
        inlineStyleCss.setWidth("1000");
        billShowParameter.getOpenStyle().setInlineStyleCss(inlineStyleCss);
        billShowParameter.setPkId(pk);
        //设置子页面关闭回调对象，回调本插件，标识为常量KEY_LEAVE_DAYS对应的值
        //弹窗子页面和父页面绑定
        this.getView().showForm(billShowParameter);
    }
}
