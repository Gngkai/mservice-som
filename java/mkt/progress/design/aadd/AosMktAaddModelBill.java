package mkt.progress.design.aadd;

import java.util.*;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Joiner;
import common.CommonDataSom;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.serialization.SerializationUtils;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.events.ChangeData;
import kd.bos.entity.datamodel.events.ImportDataEventArgs;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.report.CellStyle;
import kd.bos.form.FormShowParameter;
import kd.bos.form.cardentry.CardEntry;
import kd.bos.form.control.AbstractGrid;
import kd.bos.form.control.Image;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.RowClickEvent;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.events.*;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;

import common.sal.util.QFBuilder;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.common.util.sensitiveWordsUtils;
import mkt.common.util.translateUtils;
import mkt.progress.design.AosMktFuncReqTask;

/**
 * @author aosom
 * @version 高级A+模板-表单插件
 */
public class AosMktAaddModelBill extends AbstractBillPlugIn implements RowClickEventListener, HyperLinkClickListener {
    public static final String KEY_USER = "LAN";
    public static final String ZHCN = "中文";
    public static final String HEAD = "标题";
    public static final String CONTEXT = "正文";

    public static final String CA = "CA";

    public static final String US = "US";
    public static final String EN = "EN";
    public static final String A = "A";
    public static final String Q = "Q";
    public static final String AOS_USCA = "aos_usca";
    public static final String NA = "US/CA";
    public static final String STATE = "state";
    public static final String AOS_ENT_TAB = "aos_ent_tab";
    public static final String AOS_REPLACE = "aos_replace";
    public static final String AOS_PRODUCTNO = "aos_productno";
    public static final String AOS_TAB = "aos_tab";
    public static final String AOS_CATE2 = "aos_cate2";
    public static final String AOS_CHANGE = "aos_change";
    public static final String SAVE = "save";
    public static final String AOS_COPYTO = "aos_copyto";
    public static final String AOS_COPYFROM = "aos_copyfrom";
    public static final String AOS_TRANSLATE = "aos_translate";
    public static final String COPYTO = "copyTo";
    public static final String COPYFROM = "copyFrom";
    public static final String NO = "no";
    public static final String TAB = "tab";
    public static final String LAN = "lan";
    public static final String TRANS = "trans";
    public static final String IMG = "img";
    public static final String SOURCE = "source";
    public static final String TERMINAL = "terminal";
    public static final String AOS_CN = "aos_cn";
    public static final String TITLE = "TITLE";
    public static final String CONTENT = "CONTENT";
    /**
     * 敏感词语种缓存标识
     */
    public static final String KEY_SENSITIVE = "seniive";
    private final static List<String> LIST_LANS =
        Arrays.asList("CN", "中文", "US", "CA", "UK", "EN", "DE", "FR", "IT", "ES", "PT", "RO");
    /**
     * 需要进行敏感词校验的字段
     */
    private final static List<String> SENSITIVEFIELDS =
        Arrays.asList("aos_usca", "aos_uk", "aos_de", "aos_fr", "aos_it", "aos_es", "aos_pt", "aos_ro");
    /**
     * 需要判定字符串长度的字段
     */
    private final static List<String> LENGTHFIELDS =
        Arrays.asList("aos_cn", "aos_usca", "aos_uk", "aos_de", "aos_fr", "aos_it", "aos_es", "aos_pt", "aos_ro");

    public static String judgeLan(String lan) {
        String field;
        if (LIST_LANS.contains(lan)) {
            if (ZHCN.equals(lan)) {
                field = "aos_cn";
            } else if (CA.equals(lan) || US.equals(lan) || EN.equals(lan)) {
                field = "aos_usca";
            } else {
                field = "aos_" + lan.toLowerCase();
            }
            return field;
        } else {
            return null;
        }
    }

    public static Map<String, DynamicObjectCollection> findEntiyData(DynamicObject dyMain) {
        Map<String, DynamicObjectCollection> result = new HashMap<>(16);
        DynamicObjectCollection dyc = dyMain.getDynamicObjectCollection("aos_ent_tab");
        for (int i = 0; i < dyc.size(); i++) {
            DynamicObject dy = dyc.get(i);
            result.put(String.valueOf(i), dy.getDynamicObjectCollection("aos_entryentity"));
        }
        return result;
    }

    /**
     * 进行敏感词校验
     * 
     * @param sensitiveWords 敏感词
     * @param dyTab 单据对象
     * @param text 文本
     * @param lanRow 行
     * @param name 名称
     */
    private static void filterSentiviteWordsRow(JSONObject sensitiveWords, DynamicObject dyTab, Object text, int lanRow,
        String name) {
        String lan;
        if (AOS_USCA.equals(name)) {
            lan = "US/CA";
        } else {
            lan = name.substring(name.length() - 2).toUpperCase();
        }
        if (FndGlobal.IsNotNull(text)) {
            if (lan.equals(NA)) {
                JSONObject result = sensitiveWordsUtils.sensitiveWordVerificate(sensitiveWords, text.toString(), "US");
                if (result.getBoolean(STATE)) {
                    setSentiviteWord(dyTab, result, lanRow, lan);
                }
                // 校验结果没有敏感词，则删除敏感词记录
                else {
                    setSentiviteWord(dyTab, null, lanRow, lan);
                }

                result = sensitiveWordsUtils.sensitiveWordVerificate(sensitiveWords, text.toString(), "CA");
                if (result.getBoolean(STATE)) {
                    setSentiviteWord(dyTab, result, lanRow, lan);
                }
                // 校验结果没有敏感词，则删除敏感词记录
                else {
                    setSentiviteWord(dyTab, null, lanRow, lan);
                }
            } else {
                JSONObject sensitiveResult =
                    sensitiveWordsUtils.sensitiveWordVerificate(sensitiveWords, text.toString(), lan);
                if (sensitiveResult.getBoolean(STATE)) {
                    setSentiviteWord(dyTab, sensitiveResult, lanRow, lan);
                }
                // 校验结果没有敏感词，则删除敏感词记录
                else {
                    setSentiviteWord(dyTab, null, lanRow, lan);
                }

            }
        }
        // 校验内容为空，说明没有敏感词
        else {
            setSentiviteWord(dyTab, null, lanRow, lan);
        }
    }

    /**
     * 填充敏感词单据
     * 
     * @param dyTab 敏感词单据
     * @param sensitiveResult 校验结果
     * @param lanRow 行索引
     * @param lan 语言
     */
    private static void setSentiviteWord(DynamicObject dyTab, JSONObject sensitiveResult, int lanRow, String lan) {
        DynamicObjectCollection wordEntityRows = dyTab.getDynamicObjectCollection("aos_subentryentity");
        // 记录敏感词对应的单据行,顺便删除该行单据体以前的敏感词信息
        Map<String, DynamicObject> mapSentiveWord = new HashMap<>(16);
        // 语言行
        String lanRowInfo = String.valueOf(lanRow);
        for (DynamicObject subRow : wordEntityRows) {
            String subLan = subRow.getString("aos_sublan");
            if (!subLan.equals(lan)) {
                continue;
            }
            StringJoiner rowInfo = new StringJoiner("/");
            // 如果该敏感词对应的行里面有校验行，则删掉其中的校验行
            String[] rowS = subRow.getString("aos_rows").split("/");
            for (String row : rowS) {
                if (!row.equals(lanRowInfo)) {
                    rowInfo.add(row);
                }
            }
            subRow.set("aos_rows", rowInfo.toString());
            mapSentiveWord.put(subRow.getString("aos_word"), subRow);
        }
        if (sensitiveResult != null) {
            // 校验结果
            JSONArray dataRows = sensitiveResult.getJSONArray("data");
            // 开始将结果加入到单据体中
            for (int i = 0; i < dataRows.size(); i++) {
                JSONObject dataRow = dataRows.getJSONObject(i);
                String words = dataRow.getString("words");
                // 如果敏感词已经存在了，则在敏感词行中加入这条数据
                if (mapSentiveWord.containsKey(words)) {
                    DynamicObject dy = mapSentiveWord.get(words);
                    String value = dy.getString("aos_rows");
                    if (FndGlobal.IsNull(value)) {
                        value = lanRowInfo;
                    } else {
                        value = value + "/" + lanRowInfo;
                    }
                    dy.set("aos_rows", value);
                }
                // 反之则添加行
                else {
                    DynamicObject newRow = wordEntityRows.addNew();
                    newRow.set("aos_sublan", lan);
                    newRow.set("aos_wordtype", dataRow.get("type"));
                    newRow.set("aos_word", words);
                    newRow.set("aos_subword", dataRow.get("replace"));
                    newRow.set("aos_rows", lanRowInfo);
                    newRow.set("aos_replace", "replace");
                }
            }
        }
        // 将敏感词单据中 没有对应行的数据删除
        wordEntityRows.removeIf(dy -> FndGlobal.IsNull(dy.get("aos_rows")));
    }

    @Override
    public void registerListener(EventObject e) {
        // 给工具栏加监听事件
        this.addItemClickListeners("tbmain");
        this.addItemClickListeners("aos_change");
        CardEntry metaEntry = getControl("aos_ent_tab");
        metaEntry.addRowClickListener(this);
        AbstractGrid grid = this.getControl("aos_subentryentity");
        grid.addHyperClickListener(this);
    }

    @Override
    public void entryRowClick(RowClickEvent evt) {
        CardEntry metaEntry = (CardEntry)evt.getSource();
        if (StringUtils.equals(AOS_ENT_TAB, metaEntry.getKey())) {
            setEntityColor();
        }
    }

    @Override
    public void hyperLinkClick(HyperLinkClickEvent event) {
        String fieldName = event.getFieldName();
        int rowIndex = event.getRowIndex();
        if (fieldName.equals(AOS_REPLACE) && rowIndex >= 0) {
            int tabCurrentRow = this.getModel().getEntryCurrentRowIndex("aos_ent_tab");
            DynamicObject dyTab =
                this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_ent_tab").get(tabCurrentRow);
            // 敏感词行
            DynamicObject senSitiveRow = dyTab.getDynamicObjectCollection("aos_subentryentity").get(rowIndex);
            // 语言
            String lan = senSitiveRow.getString("aos_sublan");
            // 替换词
            String replaceWord = senSitiveRow.getString("aos_subword");
            if (FndGlobal.IsNull(replaceWord)) {
                this.getView().showTipNotification("替换词不存在");
                return;
            }
            // 敏感词
            String sentitiveWord = senSitiveRow.getString("aos_word");
            // 判断替换的控件名
            String field;
            if (lan.equals(NA)) {
                field = "aos_usca";
            } else {
                field = "aos_" + lan.toLowerCase();
            }
            // 语言行
            DynamicObjectCollection dycEnt = dyTab.getDynamicObjectCollection("aos_entryentity");
            String[] rowS = senSitiveRow.getString("aos_rows").split("/");
            for (String row : rowS) {
                DynamicObject dySentivite = dycEnt.get(Integer.parseInt(row));
                String value = dySentivite.getString(field);
                value = sensitiveWordsUtils.replaceSensitiveWords(value, sentitiveWord, replaceWord);
                this.getModel().setValue(field, value, Integer.parseInt(row), tabCurrentRow);
            }
            this.getView().showSuccessNotification("敏感词已替换");
        }
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        // 修改产品编号
        if (name.equals(AOS_PRODUCTNO)) {
            aosProductNoChange();
        }
        // 敏感词校验
        else if (SENSITIVEFIELDS.contains(name)) {
            ChangeData changeData = e.getChangeSet()[0];
            if (changeData.getRowIndex() >= 0) {
                int tabRow = this.getModel().getEntryCurrentRowIndex("aos_ent_tab");
                DynamicObject dyTab =
                    this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_ent_tab").get(tabRow);
                JSONObject sensitiveWords = JSONObject.parseObject(getPageCache().get(KEY_SENSITIVE));
                filterSentiviteWordsRow(sensitiveWords, dyTab, changeData.getNewValue(), changeData.getRowIndex(),
                    name);
                getView().updateView("aos_subentryentity");
                getView().updateView("aos_entryentity", changeData.getRowIndex());
                setEntityColor();
                DynamicObjectCollection aosSubentryentity = dyTab.getDynamicObjectCollection("aos_subentryentity");
                if (aosSubentryentity.size() > 0) {
                    getModel().setValue("aos_check", true, tabRow);
                } else {
                    getModel().setValue("aos_check", false, tabRow);
                }
            }
        }
        // tab修改检验行长度
        else if (name.equals(AOS_TAB)) {
            int rowIndex = e.getChangeSet()[0].getRowIndex();
            if (rowIndex >= 0) {
                if (checkTable(rowIndex)) {
                    this.getView().showTipNotification("The input exceeds the specified length!");
                }
            }
        }
        // 小项修改校验长度
        else if (name.equals(AOS_CATE2)) {
            int rowIndex = e.getChangeSet()[0].getRowIndex();
            int tabIndex = e.getChangeSet()[0].getParentRowIndex();
            if (rowIndex >= 0 && tabIndex >= 0) {
                if (checkLanRow(tabIndex, rowIndex)) {
                    this.getView().showTipNotification("The input exceeds the specified length!");
                }
            }
        }
        // 判断字符串长度
        if (LENGTHFIELDS.contains(name)) {
            int rowIndex = e.getChangeSet()[0].getRowIndex();
            int tabIndex = e.getChangeSet()[0].getParentRowIndex();
            if (rowIndex >= 0 && tabIndex >= 0) {
                if (checkSensitiveWord(tabIndex, rowIndex, name)) {
                    this.getView().showTipNotification("The input exceeds the specified length!");
                }
            }
        }
    }

    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        statusControl();
        List<QFilter> materialFilter = SalUtil.get_MaterialFilter();
        QFilter filter = new QFilter("aos_productno", "=", this.getModel().getValue("aos_productno"));
        materialFilter.add(filter);
        DynamicObject dy = QueryServiceHelper.queryOne("bd_material", "number", materialFilter.toArray(new QFilter[0]));
        if (dy != null) {
            Image image = this.getView().getControl("aos_imageap");
            image.setUrl(CommonDataSom.get_img_url(dy.getString("number")));
        }
        initSensitiveWords();
        aosProductNoChange();
        setEntityColor();
    }

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs evt) {
        super.afterDoOperation(evt);
        String control = evt.getOperateKey();
        if (AOS_CHANGE.equals(control)) {
            aosChange();
        } else if (SAVE.equals(control)) {
            if (!checkTable()) {
                getView().showTipNotification("The input exceeds the specified length!");
            }
        }
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        if (AOS_COPYTO.equals(control)) {
            filterSentivitEntry();
            FormShowParameter showParameter = FndGlobal.CraeteForm(this, "aos_mkt_functcopy", "copyTo", null);
            showParameter.setCaption("A+ Copy To");
            @SuppressWarnings("unchecked")
            List<String> users =
                (List<String>)SerializationUtils.fromJsonStringToList(this.getPageCache().get(KEY_USER), String.class);
            showParameter.setCustomParam("userLan", users);
            getView().showForm(showParameter);
        } else if (AOS_COPYFROM.equals(control)) {
            filterSentivitEntry();
            FormShowParameter showParameter = FndGlobal.CraeteForm(this, "aos_mkt_functcopy", "copyFrom", null);
            showParameter.setCaption("A+ Copy Form");
            @SuppressWarnings("unchecked")
            List<String> users =
                (List<String>)SerializationUtils.fromJsonStringToList(this.getPageCache().get(KEY_USER), String.class);
            showParameter.setCustomParam("userLan", users);
            getView().showForm(showParameter);
        } else if (AOS_TRANSLATE.equals(control)) {
            FormShowParameter showParameter = FndGlobal.CraeteForm(this, "aos_mkt_funct_tran", "trans", null);
            showParameter.setCaption("Translate Form");
            @SuppressWarnings("unchecked")
            List<String> users =
                (List<String>)SerializationUtils.fromJsonStringToList(this.getPageCache().get(KEY_USER), String.class);
            showParameter.setCustomParam("userLan", users);
            getView().showForm(showParameter);
        }
    }

    @Override
    public void closedCallBack(ClosedCallBackEvent event) {
        super.closedCallBack(event);
        String actionId = event.getActionId();
        @SuppressWarnings("unchecked")
        Map<String, Object> returnData = (Map<String, Object>)event.getReturnData();
        if (returnData == null) {
            return;
        }
        if (actionId.equals(COPYTO) || actionId.equals(COPYFROM)) {
            if (!returnData.containsKey(NO)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>)returnData.get("data");
            if (!data.containsKey(TAB) || !data.containsKey(LAN)) {
                return;
            }
            @SuppressWarnings("unchecked")
            List<String> tabs = (List<String>)data.get(TAB);
            @SuppressWarnings("unchecked")
            List<String> lans = (List<String>)data.get(LAN);
            String products = (String)returnData.get("no");
            QFBuilder builder = new QFBuilder();
            String[] noS = products.split(",");
            if (actionId.equals(COPYTO)) {
                for (String no : noS) {
                    if (FndGlobal.IsNull(no)) {
                        continue;
                    }
                    builder.clear();
                    builder.add("aos_productno", "=", no);
                    List<Object> list =
                        QueryServiceHelper.queryPrimaryKeys("aos_aadd_model", builder.toArray(), null, 1);
                    DynamicObject dy = BusinessDataServiceHelper.loadSingle(list.get(0), "aos_aadd_model");
                    copyValue(this.getModel().getDataEntity(true), dy, tabs, lans);
                    SaveServiceHelper.save(new DynamicObject[] {dy});
                }
                this.getView().showSuccessNotification("Copy to Success");
            } else {
                for (String no : noS) {
                    if (FndGlobal.IsNull(no)) {
                        continue;
                    }
                    builder.clear();
                    builder.add("aos_productno", "=", no);
                    List<Object> list =
                        QueryServiceHelper.queryPrimaryKeys("aos_aadd_model", builder.toArray(), null, 1);
                    DynamicObject dy = BusinessDataServiceHelper.loadSingle(list.get(0), "aos_aadd_model");
                    copyValue(dy, this.getModel().getDataEntity(true), tabs, lans);
                }
                filterSentivitEntry();
                this.getView().showSuccessNotification("Copy from Success");
            }
        } else if (actionId.equals(TRANS)) {
            if (returnData.get(IMG) == null || returnData.get(SOURCE) == null || returnData.get(TERMINAL) == null) {
                return;
            }
            // 翻译前的语言
            String sourceLan = (String)returnData.get("source");
            String field = judgeLan(sourceLan);
            if (FndGlobal.IsNull(field)) {
                this.getView().showErrorNotification("翻译源语言不存在");
                return;
            }
            // 翻译后的语言
            @SuppressWarnings("unchecked")
            List<String> listTerminal = (List<String>)returnData.get("terminal");
            @SuppressWarnings("unchecked")
            List<String> listTable = (List<String>)returnData.get("img");
            Map<String, DynamicObjectCollection> entiyData = findEntiyData(this.getModel().getDataEntity(true));
            // 语言
            for (String lan : listTerminal) {
                // 判断对应的字段
                // 翻译完成进行赋值
                String transField = judgeLan(lan);
                if (FndGlobal.IsNull(transField)) {
                    continue;
                }
                // 记录页签下的数据
                List<DynamicObject> listRow = new ArrayList<>();
                List<String> listText = new ArrayList<>();
                for (String tab : listTable) {
                    // 当前页签下的数据
                    DynamicObjectCollection tableData = entiyData.get(tab);
                    for (DynamicObject rowData : tableData) {
                        String text = rowData.getString(field);
                        if (FndGlobal.IsNotNull(text)) {
                            listRow.add(rowData);
                            listText.add(text);
                        }
                    }
                }
                List<String> listTransalate = translateUtils.transalate(sourceLan, lan, listText);
                for (int i = 0; i < listRow.size(); i++) {
                    DynamicObject dyRow = listRow.get(i);
                    dyRow.set(transField, listTransalate.get(i));
                }
            }
            filterSentivitEntry();
            this.getView().showSuccessNotification("Translate Success");
        }
    }

    @Override
    public void beforeDoOperation(BeforeDoOperationEventArgs args) {
        FormOperate formOperate = (FormOperate)args.getSource();
        String operatation = formOperate.getOperateKey();
        if (SAVE.equals(operatation)) {
            setProductItem(this.getModel().getDataEntity(true));
            cleanButton();
            filterSentivitEntry();
        }
    }

    @Override
    public void afterImportData(ImportDataEventArgs e) {
        super.afterImportData(e);
        DynamicObject entity = this.getModel().getDataEntity(true);
        setProductItem(entity);
    }

    @Override
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        DynamicObject dataEntity = this.getModel().getDataEntity(true);
        Set<String> fields = new HashSet<>();
        fields.add("aos_picturefield");
        for (String field : SENSITIVEFIELDS) {
            fields.add(field + "_h");
        }
        fields.add("aos_cn_h");
        fields.add("aos_check");
        fields.add("aos_sublan");
        fields.add("aos_wordtype");
        fields.add("aos_word");
        fields.add("aos_subword");
        fields.add("aos_replace");
        fields.add("aos_rows");
        SalUtil.skipVerifyFieldChanged(dataEntity, dataEntity.getDynamicObjectType(), fields);
        // this.getView().invokeOperation("save");
    }

    /**
     * 将没有命名的页签删除
     */
    private void cleanButton() {
        DynamicObjectCollection dyc = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_ent_tab");
        dyc.removeIf(dy -> dy.getDynamicObjectCollection("aos_entryentity").size() == 0);
        getView().updateView();
    }

    /**
     * 设置兄弟货号
     * 
     * @param dyMain 单据
     */
    public void setProductItem(DynamicObject dyMain) {
        String aosProductno = dyMain.getString("aos_productno");
        if (FndGlobal.IsNull(aosProductno)) {
            return;
        }
        DynamicObjectCollection productEntity = dyMain.getDynamicObjectCollection("aos_entryentity2");
        if (productEntity.size() > 0) {
            return;
        }
        // 未导入标记
        QFilter filterProductno = new QFilter("aos_productno", "=", aosProductno);
        QFilter[] filters = new QFilter[] {filterProductno};
        DynamicObjectCollection bdMaterialSameS = QueryServiceHelper.query("bd_material", "id", filters);
        List<String> listOrgtextTotal = new ArrayList<>();
        for (DynamicObject bdMaterialSame : bdMaterialSameS) {
            Object itemId = bdMaterialSame.get("id");
            List<String> listOrgtext = new ArrayList<>();
            DynamicObject material = BusinessDataServiceHelper.loadSingle(itemId, "bd_material");
            DynamicObjectCollection aosContryentryS = material.getDynamicObjectCollection("aos_contryentry");
            // 获取所有国家品牌 字符串拼接 终止
            for (DynamicObject aosContryentry : aosContryentryS) {
                AosMktFuncReqTask.addItemOrg(aosContryentry, itemId, listOrgtext, listOrgtextTotal);
            }
            DynamicObject aosEntryentity2 = productEntity.addNew();
            aosEntryentity2.set("aos_itemid", material);
            aosEntryentity2.set("aos_orgdetail", Joiner.on(";").join(listOrgtext));
        }
    }

    /**
     * copy 单据
     * 
     * @param dySource 源单据
     * @param dyTarget 目标单据
     * @param tabs 页签
     * @param lans 语言
     */
    private void copyValue(DynamicObject dySource, DynamicObject dyTarget, List<String> tabs, List<String> lans) {
        DynamicObjectCollection tabEntity = dyTarget.getDynamicObjectCollection("aos_ent_tab");
        DynamicObjectCollection sourcEntity = dySource.getDynamicObjectCollection("aos_ent_tab");
        // 获取源单的相关数据
        Map<String, DynamicObjectCollection> sourceData = findEntiyData(dySource);
        if (sourceData.size() == 0) {
            return;
        }
        // 获取目标单据的相关数据
        Map<String, DynamicObjectCollection> targetData = findEntiyData(dyTarget);
        // 页签维度
        for (String tab : tabs) {
            if (sourceData.containsKey(tab)) {
                DynamicObjectCollection sourceRowDy = sourceData.get(tab);
                DynamicObjectCollection targetRowDy = null;
                // 目标单据存在则覆盖
                if (targetData.containsKey(tab)) {
                    tabEntity.get(Integer.parseInt(tab)).set("aos_tab",
                        sourcEntity.get(Integer.parseInt(tab)).get("aos_tab"));
                    targetRowDy = targetData.get(tab);
                }
                // 目标单据不存在则新增
                if (targetRowDy == null) {
                    DynamicObject tabRow = tabEntity.addNew();
                    tabRow.set("seq", Integer.parseInt(tab) + 1);
                    tabRow.set("aos_tab", sourcEntity.get(Integer.parseInt(tab)).get("aos_tab"));
                    targetRowDy = tabRow.getDynamicObjectCollection("aos_entryentity");
                }
                for (int sourceIndex = 0; sourceIndex < sourceRowDy.size(); sourceIndex++) {
                    DynamicObject sourceSubRow = sourceRowDy.get(sourceIndex);
                    DynamicObject targetSubRow;
                    if (targetRowDy.size() == sourceIndex) {
                        targetSubRow = targetRowDy.addNew();
                        targetSubRow.set("seq", sourceIndex + 1);
                    } else {
                        targetSubRow = targetRowDy.get(sourceIndex);
                    }
                    // 大项，小项
                    targetSubRow.set("aos_cate1", sourceSubRow.get("aos_cate1"));
                    targetSubRow.set("aos_cate2", sourceSubRow.get("aos_cate2"));
                    // 设置每个语种
                    for (String lan : lans) {
                        String field = judgeLan(lan);
                        if (FndGlobal.IsNull(field)) {
                            continue;
                        }
                        targetSubRow.set(field, sourceSubRow.get(field));
                    }
                }
            }
        }
    }

    /**
     * 改名
     */
    private void aosChange() {
        FndGlobal.OpenForm(this, "aos_aadd_model_show", null);
    }

    /**
     * 产品号值改变
     */
    private void aosProductNoChange() {
        Object aosProductno = this.getModel().getValue("aos_productno");
        if (FndGlobal.IsNull(aosProductno)) {
            this.getModel().setValue("aos_picturefield", null);
        } else {
            // 图片字段
            DynamicObject aosItemidObject = BusinessDataServiceHelper.loadSingle("bd_material",
                new QFilter("aos_productno", QCP.equals, aosProductno).toArray());
            if (FndGlobal.IsNotNull(aosItemidObject)) {
                QFilter filter = new QFilter("entryentity.aos_articlenumber", QCP.equals, aosItemidObject.getPkValue())
                    .and("billstatus", QCP.in, new String[] {"C", "D"});
                DynamicObjectCollection query = QueryServiceHelper.query("aos_newarrangeorders",
                    "entryentity.aos_picture", filter.toArray(), "aos_creattime desc");
                if (query != null && query.size() > 0) {
                    Object o = query.get(0).get("entryentity.aos_picture");
                    this.getModel().setValue("aos_picturefield", o);
                }
            }
        }
    }

    /**
     * 查询可以copy，翻译的语言
     */
    private void statusControl() {
        // 根据用户查找相应的人员能够操作的语言
        QFBuilder builder = new QFBuilder();
        builder.add("aos_user", "=", RequestContext.get().getCurrUserId());
        DynamicObject dyUserPermiss =
            QueryServiceHelper.queryOne("aos_mkt_permiss_lan", "aos_row,aos_exchange,aos_lan", builder.toArray());
        List<String> userCopyLanguage = new ArrayList<>();
        // 记录能够操作的字段控件
        List<String> userFidlds = new ArrayList<>();
        if (dyUserPermiss != null) {
            Object aosLan = dyUserPermiss.get("aos_lan");
            if (FndGlobal.IsNotNull(aosLan)) {
                // 用户能够copy的语言
                String[] lanS = dyUserPermiss.getString("aos_lan").split(",");
                for (String lan : lanS) {
                    if (FndGlobal.IsNotNull(lan)) {
                        userCopyLanguage.add(lan);
                        userFidlds.add(judgeLan(lan));
                    }
                }
            }
        }
        this.getPageCache().put(KEY_USER, SerializationUtils.toJsonString(userCopyLanguage));
        AbstractGrid grid = this.getControl("aos_entryentity");
        grid.setLock("aos_cn");
        if (!userFidlds.contains(AOS_CN)) {
            this.getModel().setValue("aos_cn_h", true);
        }
        for (String field : SENSITIVEFIELDS) {
            if (!userFidlds.contains(field)) {
                getModel().setValue(field + "_h", true);
            }
        }
    }

    /**
     * 初始化敏感词
     */
    private void initSensitiveWords() {
        // 根据产品号查询一个最新的物料
        Object productNo = getModel().getValue("aos_productno");
        JSONObject lanSensitiveWords = sensitiveWordsUtils.FindMaterialSensitiveWords(productNo);
        getPageCache().put(KEY_SENSITIVE, lanSensitiveWords.toString());
    }

    /**
     * 对所有的页签行进行校验
     */
    private void filterSentivitEntry() {
        DynamicObjectCollection dycTab = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_ent_tab");
        JSONObject sensitiveWords = JSONObject.parseObject(getPageCache().get(KEY_SENSITIVE));
        for (int tabIndex = 0; tabIndex < dycTab.size(); tabIndex++) {
            DynamicObject dyTable = dycTab.get(tabIndex);
            // 语言单据体
            DynamicObjectCollection entityRows = dyTable.getDynamicObjectCollection("aos_entryentity");
            for (int i = 0; i < entityRows.size(); i++) {
                DynamicObject row = entityRows.get(i);
                for (String field : SENSITIVEFIELDS) {
                    filterSentiviteWordsRow(sensitiveWords, dyTable, row.get(field), i, field);
                }
            }
            if (dyTable.getDynamicObjectCollection("aos_subentryentity").size() > 0) {
                this.getModel().setValue("aos_check", true, tabIndex);
            } else {
                this.getModel().setValue("aos_check", false, tabIndex);
            }
        }
        setEntityColor();
        getView().updateView();
    }

    /**
     * 修改敏感词行的颜色
     */
    private void setEntityColor() {
        int tabRow = this.getModel().getEntryCurrentRowIndex("aos_ent_tab");
        if (FndGlobal.IsNull(tabRow)) {
            return;
        }
        DynamicObjectCollection dycTab = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_ent_tab");
        if (dycTab.isEmpty()) {
            return;
        }
        DynamicObject tabEntryRow = dycTab.get(tabRow);
        // 获取语言信息单据体控件
        AbstractGrid grid = this.getView().getControl("aos_entryentity");
        List<CellStyle> list = new ArrayList<>();
        // 获取语言单据数据
        DynamicObjectCollection dycLanInfo = tabEntryRow.getDynamicObjectCollection("aos_entryentity");
        DynamicObjectCollection subRows = tabEntryRow.getDynamicObjectCollection("aos_subentryentity");
        // 记录存在敏感词的行
        Map<String, List<String>> mapSentiviteRows = new HashMap<>(16);
        for (DynamicObject row : subRows) {
            String field;
            String lan = row.getString("aos_sublan");
            if (lan.equals(NA)) {
                field = "aos_usca";
            } else {
                field = "aos_" + lan.toLowerCase();
            }
            mapSentiviteRows.put(field, Arrays.asList(row.getString("aos_rows").split("/")));
        }
        for (int i = 0; i < dycLanInfo.size(); i++) {
            for (String field : SENSITIVEFIELDS) {
                CellStyle cs = new CellStyle();
                cs.setFieldKey(field);
                cs.setRow(i);
                // 该行为敏感词，设置为红色，否则为黑色
                if (mapSentiviteRows.containsKey(field) && mapSentiviteRows.get(field).contains(String.valueOf(i))) {
                    // 红色
                    cs.setForeColor("#fb2323");
                } else {
                    // 黑色
                    cs.setForeColor("#404040");
                }
                list.add(cs);
            }
        }
        grid.setCellStyle(list);
    }

    private boolean checkTable() {
        DynamicObjectCollection dycTab = this.getModel().getEntryEntity("aos_ent_tab");
        boolean flag = true;
        for (int tabIndex = 0; tabIndex < dycTab.size(); tabIndex++) {
            if (checkTable(tabIndex)) {
                flag = false;
            }
        }
        return flag;
    }

    /**
     * 校验敏感词
     */
    public boolean checkTable(int tabRow) {
        DynamicObjectCollection dycLan =
            this.getModel().getEntryEntity("aos_ent_tab").get(tabRow).getDynamicObjectCollection("aos_entryentity");
        boolean flag = true;
        for (int i = 0; i < dycLan.size(); i++) {
            if (checkLanRow(tabRow, i)) {
                flag = false;
            }
        }
        return !flag;
    }

    /**
     * 校验敏感词行
     */
    private boolean checkLanRow(int tabRow, int lanRow) {
        boolean flag = true;
        for (String lengthField : LENGTHFIELDS) {
            if (checkSensitiveWord(tabRow, lanRow, lengthField)) {
                flag = false;
            }
        }
        return !flag;
    }

    /**
     * @param tabRow 单据头行
     * @param lanRow 语言行
     * @param field 字段
     * @return 是否校验成功
     */
    private boolean checkSensitiveWord(int tabRow, int lanRow, String field) {
        Object aosTab = getModel().getValue("aos_tab", tabRow);
        Object aosCate2 = getModel().getValue("aos_cate2", lanRow, tabRow);
        if (FndGlobal.IsNull(aosTab) || FndGlobal.IsNull(aosCate2)) {
            this.getModel().setValue(field + "_t", "", lanRow, tabRow);
            return false;
        }
        String tabValue = String.valueOf(aosTab);
        String cate2Value = String.valueOf(aosCate2);
        Object value = this.getModel().getValue(field, lanRow, tabRow);
        int valueSize = 0;
        if (FndGlobal.IsNotNull(value)) {
            valueSize = value.toString().length();
        }
        String filterValue = "";
        boolean resultt = true;
        switch (tabValue) {
            case "轮播图模块":
                // 标题
                if (cate2Value.equals(TITLE) || cate2Value.equals(HEAD)) {
                    filterValue = valueSize + "/" + 25;
                    resultt = valueSize <= 25;
                }
                break;
            case "锚点模块":
                if (cate2Value.equals(TITLE) || cate2Value.equals(HEAD)) {
                    filterValue = valueSize + "/" + 50;
                    resultt = valueSize <= 50;
                } else if (cate2Value.equals(CONTENT) || cate2Value.equals(CONTEXT)) {
                    filterValue = valueSize + "/" + 200;
                    resultt = valueSize <= 200;
                }
                break;
            case "细节模块":
                if (cate2Value.equals(TITLE) || cate2Value.equals(HEAD)) {
                    filterValue = valueSize + "/" + 30;
                    resultt = valueSize <= 30;
                } else if (cate2Value.equals(CONTENT) || cate2Value.equals(CONTEXT)) {
                    filterValue = valueSize + "/" + 150;
                    resultt = valueSize <= 150;
                }
                break;
            case "QA模块":
                if (cate2Value.equals(Q)) {
                    filterValue = valueSize + "/" + 120;
                    resultt = valueSize <= 120;
                } else if (cate2Value.equals(A)) {
                    filterValue = valueSize + "/" + 250;
                    resultt = valueSize <= 250;
                }
                break;
            default:
                break;
        }
        this.getModel().setValue(field + "_t", filterValue, lanRow, tabRow);
        return !resultt;
    }
}