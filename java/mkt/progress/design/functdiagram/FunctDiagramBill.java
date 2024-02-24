package mkt.progress.design.functdiagram;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.deepl.api.DeepLException;
import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DataEntityState;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.dataentity.metadata.IDataEntityProperty;
import kd.bos.dataentity.metadata.clr.DataEntityPropertyCollection;
import kd.bos.dataentity.serialization.SerializationUtils;
import kd.bos.entity.BadgeInfo;
import kd.bos.entity.datamodel.events.AfterAddRowEventArgs;
import kd.bos.entity.datamodel.events.AfterDeleteRowEventArgs;
import kd.bos.entity.datamodel.events.BeforeDeleteRowEventArgs;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.report.CellStyle;
import kd.bos.exception.ErrorCode;
import kd.bos.exception.KDException;
import kd.bos.form.*;
import kd.bos.form.container.TabPage;
import kd.bos.form.control.*;
import kd.bos.form.events.*;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import common.sal.util.QFBuilder;
import mkt.common.util.sensitiveWordsUtils;
import mkt.common.util.translateUtils;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author create by gk
 * @version 新功能图需求表-表单插件
 */
public class FunctDiagramBill extends AbstractBillPlugIn implements HyperLinkClickListener {
    /**
     * 敏感词语种缓存标识
     */
    public static final String KEY_SENSITIVE = "seniive";
    /**
     * 删除行pid
     */
    private static final String KEY_DELETE = "DELETE";
    /**
     * 增行的语言
     */
    private static final String KEY_INSERT = "INSERT";
    private static final String INSERT = "insert";
    private static final String TRANS = "trans";
    private static final String DELETEENTRY = "deleteentry";
    private static final String EDIT = "edit";
    private static final String CLEAR = "clear";
    private static final String SAVE = "save";
    private static final String AOS_HISTORY = "aos_history";
    private static final String COPYFROM = "copyfrom";
    private static final String COPYTO = "copyto";
    private static final String TRANSLATE = "translate";
    private static final String EXCHANGE = "exchange";
    private static final String AOS_VALUE = "aos_value";
    private static final String ZHCN = "中文";
    private static final String EN = "EN";
    private static final String UK = "UK";
    private static final String US = "US";
    private static final String CA = "CA";
    /**
     * 用户可操控的语言
     */
    private static final String KEY_USER = "LAN";
    private static final String A = "A";
    private static final String C = "C";
    private static final String ZERO = "0";
    private static final int SEVEN = 7;
    private static final String AOS_LAN = "aos_lan";

    public static List<String> list_language;
    /**
     * 语言对应的翻译标识
     */
    public static Map<String, String> map_transLan;

    public static List<String> sensitiviteFields = new ArrayList<>();

    static {
        list_language = Arrays.asList("中文", "EN", "DE", "FR", "IT", "ES", "PT", "RO", "UK", "US", "CA");
        map_transLan = new HashMap<>();
        map_transLan.put("中文", "ZH");
        map_transLan.put("EN", "EN-GB");
        map_transLan.put("DE", "DE");
        map_transLan.put("FR", "FR");
        map_transLan.put("IT", "IT");
        map_transLan.put("ES", "ES");
        map_transLan.put("PT", "PT-PT");
        map_transLan.put("RO", "RO");
        map_transLan.put("UK", "EN-GB");
        map_transLan.put("US", "EN-US");
        map_transLan.put("CA", "EN-US");
        for (int i = 1; i < SEVEN; i++) {
            sensitiviteFields.add("aos_value" + i);
            sensitiviteFields.add("aos_content" + i);
        }
    }
    /**
     * 增删行权限
     */
    private boolean isAddRow = false;
    /**
     * 交换界面权限
     */
    private boolean isExchange = false;

    /** 将单据根据语言分组，并且根据seq 排序 **/
    private static Map<String, List<DynamicObject>> entityGroupBylan(DynamicObjectCollection dyc, String index) {
        return dyc.stream().sorted((dy1, dy2) -> {
            int seq1 = dy1.getInt("seq");
            int seq2 = dy2.getInt("seq");
            if (seq1 < seq2) {
                return -1;
            } else {
                return 1;
            }
        }).collect(Collectors.groupingBy(dy -> dy.getString("aos_lan" + index)));
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        for (int i = 1; i < SEVEN; i++) {
            EntryGrid entryGrid = this.getView().getControl("aos_subentryentity" + i);
            entryGrid.addHyperClickListener(this);
        }
    }

    @Override
    public void hyperLinkClick(HyperLinkClickEvent event) {
        String fieldName = event.getFieldName();
        String lanIndex = fieldName.substring(fieldName.length() - 1);
        int eventRowIndex = event.getRowIndex();
        if (eventRowIndex >= 0) {
            DynamicObject rowEntity = this.getModel().getEntryRowEntity("aos_subentryentity" + lanIndex, eventRowIndex);
            int currentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_entity" + lanIndex);
            String sentitiveWord = rowEntity.getString("aos_word" + lanIndex);
            String replaceWord = rowEntity.getString("aos_subword" + lanIndex);
            String value, entityField;
            String type = rowEntity.getString("aos_type" + lanIndex);
            if (FndGlobal.IsNull(type)) {
                return;
            }
            // 标题行
            if (A.equals(type) || C.equals(type)) {
                entityField = "aos_value" + lanIndex;
                value = this.getModel().getValue(entityField, currentRowIndex).toString();
            } else {
                entityField = "aos_content" + lanIndex;
                value = this.getModel().getValue(entityField, currentRowIndex).toString();
            }
            if (FndGlobal.IsNotNull(replaceWord)) {
                value = sensitiveWordsUtils.replaceSensitiveWords(value, sentitiveWord, replaceWord);
                this.getModel().setValue(entityField, value, currentRowIndex);
            }
        }
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (sensitiviteFields.contains(name)) {
            int rowIndex = e.getChangeSet()[0].getRowIndex();
            String lanIndex = name.substring(name.length() - 1);
            DynamicObject entityRow =
                getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + lanIndex).get(rowIndex);
            filterSentiviteWordsRow(entityRow, rowIndex, name, true);
        }
    }

    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        initPic();
        statusControl();
        initEntity();
        initSensitiveWords();
        setEntityStatus();
    }

    @Override
    public void afterAddRow(AfterAddRowEventArgs e) {
        String name = e.getEntryProp().getName();
        int insertRow = e.getInsertRow();
        String entityIndex = name.substring(name.length() - 1);
        DynamicObject dyInsertRow = this.getModel().getDataEntity(true).getDynamicObjectCollection(name).get(insertRow);
        String pid = dyInsertRow.getString("pid");
        String lan = this.getPageCache().get(KEY_INSERT);
        int index = 1;
        if (!Cux_Common_Utl.IsNull(this.getPageCache().get(pid))) {
            index = Integer.parseInt(getPageCache().get(pid)) + 1;
        }
        getPageCache().put(pid, String.valueOf(index));
        LocaleString localHead = new LocaleString();
        localHead.setLocaleValue_en("Sub title" + index);
        localHead.setLocaleValue_zh_CN("副标题" + index);
        this.getModel().setValue("aos_head" + entityIndex, localHead, insertRow);
        localHead.setLocaleValue_zh_CN("正文" + index);
        localHead.setLocaleValue_en("Text" + index);
        this.getModel().setValue("aos_title" + entityIndex, localHead, insertRow);
        this.getModel().setValue("aos_lan" + entityIndex, lan, insertRow);
    }

    @Override
    public void beforeDeleteRow(BeforeDeleteRowEventArgs e) {
        int[] rowIndexs = e.getRowIndexs();
        String name = e.getEntryProp().getName();
        String pid =
            this.getModel().getDataEntity(true).getDynamicObjectCollection(name).get(rowIndexs[0]).getString("pid");
        if (ZERO.equals(pid)) {
            e.setCancel(true);
        } else if (!Cux_Common_Utl.IsNull(getPageCache().get(pid))) {
            int index = Integer.parseInt(getPageCache().get(pid));
            index = index - rowIndexs.length;
            getPageCache().put(pid, String.valueOf(index));
        }
        this.getPageCache().put(KEY_DELETE, pid);
    }

    @Override
    public void afterDeleteRow(AfterDeleteRowEventArgs e) {
        String pid = this.getView().getPageCache().get(KEY_DELETE);
        // 现在有的行
        int rows = Integer.parseInt(this.getPageCache().get(pid));
        String name = e.getEntryProp().getName();
        DynamicObjectCollection dyc = this.getModel().getDataEntity(true).getDynamicObjectCollection(name);
        if (rows > 0) {
            String entityIndex = name.substring(name.length() - 1);
            int index = 1;
            for (DynamicObject dy : dyc) {
                if (dy.getString("pid").equals(pid)) {
                    LocaleString localHead = new LocaleString();
                    localHead.setLocaleValue_en("Subtitle" + index);
                    localHead.setLocaleValue_zh_CN("副标题" + index);
                    dy.set("aos_head" + entityIndex, localHead);
                    localHead.setLocaleValue_zh_CN("正文" + index);
                    localHead.setLocaleValue_en("main body" + index);
                    dy.set("aos_title" + entityIndex, localHead);
                    index++;
                }
            }
        }
    }

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs eventArgs) {
        super.afterDoOperation(eventArgs);
        String operateKey = eventArgs.getOperateKey();
        // 增行
        if (INSERT.equals(operateKey.substring(0, operateKey.length() - 1))) {
            int entityIndex = Integer.parseInt(operateKey.substring(operateKey.length() - 1));
            insertEnity(entityIndex);
        }
        // 删行
        else if (DELETEENTRY.equals(operateKey.substring(0, operateKey.length() - 1))) {
            int entityIndex = Integer.parseInt(operateKey.substring(operateKey.length() - 1));
            deleteEntity(entityIndex);
        }
        // 编辑
        else if (EDIT.equals(operateKey)) {
            edit();
        }
        // 清除
        else if (CLEAR.equals(operateKey)) {
            clear();
        }
        // 保存记录日志
        else if (SAVE.equals(operateKey)) {
            boolean existe = filterSentiviteWords();
            if (existe) {
                this.getView().showTipNotification("Sensitive words in the interface");
            }
            FndHistory.Create(this.getView(), "保存", "");
        }
        // 查看日志
        else if (AOS_HISTORY.equals(operateKey)) {
            // 查看历史记录
            Cux_Common_Utl.OpenHistory(this.getView());
        } else if (COPYFROM.equals(operateKey)) {
            findCate("aos_mkt_functcopy", operateKey, "Copy From");
        } else if (COPYTO.equals(operateKey)) {
            findCate("aos_mkt_functcopy", "copyto", "Copy To");
        } else if (TRANSLATE.equals(operateKey)) {
            findCate("aos_mkt_funct_tran", "trans", "Translate Form");
        } else if (EXCHANGE.equals(operateKey)) {
            findCate("aos_mkt_funct_ex", "exchange", "Image Exchange");
        }
    }

    @Override
    public void closedCallBack(ClosedCallBackEvent event) {
        super.closedCallBack(event);
        String actionId = event.getActionId();
        if (COPYFROM.equals(actionId)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> returnData = (Map<String, Object>)event.getReturnData();
            if (returnData != null) {
                copyFrom(returnData);
                this.getView().showSuccessNotification("Copy from Success");
            }
        } else if (COPYTO.equals(actionId)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> returnData = (Map<String, Object>)event.getReturnData();
            if (returnData != null) {
                copyTo(returnData);
                this.getView().showSuccessNotification("Copy to Success");
            }
        } else if (TRANS.equals(actionId)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> returnData = (Map<String, Object>)event.getReturnData();
            if (returnData != null) {
                try {
                    translate(returnData);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (DeepLException e) {
                    e.printStackTrace();
                    throw new KDException(new ErrorCode("翻译异常", e.getMessage()));
                }
                this.getView().showSuccessNotification("Translate Success");
            }
        } else if (EXCHANGE.equals(actionId) && event.getReturnData() != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> returnData = (Map<String, Object>)event.getReturnData();
            Object source = returnData.get("source");
            Object end = returnData.get("end");
            if (!source.equals(end)) {
                imgExchange(source, end);
            }
        }
    }

    /** copy 弹窗 **/
    private void findCate(String page, String callback, String name) {
        boolean existe = filterSentiviteWords();
        if (existe) {
            this.getView().showTipNotification("Sensitive words in the interface");
        }
        // 创建弹出页面对象，FormShowParameter表示弹出页面为动态表单
        FormShowParameter showParameter = new FormShowParameter();
        // 设置弹出页面的编码
        showParameter.setFormId(page);
        // 设置弹出页面标题
        showParameter.setCaption(name);
        @SuppressWarnings("unchecked")
        List<String> users =
            (List<String>)SerializationUtils.fromJsonStringToList(this.getPageCache().get(KEY_USER), String.class);
        showParameter.setCustomParam("userLan", users);
        // 设置页面关闭回调方法
        // CloseCallBack参数：回调插件，回调标识
        showParameter.setCloseCallBack(new CloseCallBack(this, callback));
        // 设置弹出页面打开方式，支持模态，新标签等
        showParameter.getOpenStyle().setShowType(ShowType.Modal);
        // 弹出页面对象赋值给父页面
        this.getView().showForm(showParameter);
    }

    /** copy to **/
    private void copyTo(Map<String, Object> mapParamet) {
        // 产品号
        Object skuNo = mapParamet.get("no");
        if (!Cux_Common_Utl.IsNull(skuNo)) {
            Object data = mapParamet.get("data");
            if (data != null) {
                String itemNo = (String)skuNo;
                List<String> listNos = Arrays.stream(itemNo.split(",")).filter(no -> no != null && !"".equals(no))
                    .distinct().collect(Collectors.toList());
                QFBuilder qfBuilder = new QFBuilder();
                qfBuilder.add("aos_segment3", QFilter.in, listNos);
                List<String> listFunctIds = QueryServiceHelper.query("aos_mkt_functreq", "id", qfBuilder.toArray())
                    .stream().map(dy -> dy.getString("id")).distinct().collect(Collectors.toList());
                // 选择的单据及其语言信息
                @SuppressWarnings("unchecked")
                Map<String, List<String>> mapData = (Map<String, List<String>>)data;
                for (String id : listFunctIds) {
                    DynamicObject dyFunct = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_functreq");
                    // 选择的语言
                    List<String> listLanguage = mapData.get("lan");
                    // 选择的图片
                    List<String> listImg = mapData.get("img");
                    for (String key : listImg) {
                        DynamicObjectCollection dycSource =
                            this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + key);
                        DynamicObjectCollection dycTarget = dyFunct.getDynamicObjectCollection("aos_entity" + key);
                        // 将本单据体的行数 和 目的单据的行数同步
                        Map<String, List<DynamicObject>> mapSource = entityGroupBylan(dycSource, key);
                        Map<String, List<DynamicObject>> mapTarget = entityGroupBylan(dycTarget, key);
                        // 原单副标题行数
                        int sourceSize = mapSource.get("EN").size();
                        // 目标单副标题行数
                        int targetSize = mapTarget.get("EN").size();
                        if (targetSize < sourceSize) {
                            mapTarget = targetEntityAddRow(key, dycTarget, mapTarget, (sourceSize - targetSize));
                        }
                        setValue(key, listLanguage, mapSource, mapTarget);
                    }
                    FndHistory.Create(dyFunct, "保存",
                        UserServiceHelper.getCurrentUser("name").getString("name") + " copy to");
                    SaveServiceHelper.save(new DynamicObject[] {dyFunct});
                }
            }
        }
    }

    /** copy fROM **/
    private void copyFrom(Map<String, Object> mapParamet) {
        Object skuNo = mapParamet.get("no");
        if (!Cux_Common_Utl.IsNull(skuNo)) {
            Object data = mapParamet.get("data");
            if (data != null) {
                String itemNo = (String)skuNo;
                List<String> listNos = Arrays.stream(itemNo.split(",")).filter(no -> no != null && !"".equals(no))
                    .distinct().collect(Collectors.toList());
                QFBuilder qfBuilder = new QFBuilder();
                qfBuilder.add("aos_segment3", QFilter.equals, listNos.get(0));
                DynamicObject dySource = QueryServiceHelper.queryOne("aos_mkt_functreq", "id", qfBuilder.toArray());
                // 选择的单据及其语言信息
                @SuppressWarnings("unchecked")
                Map<String, List<String>> mapData = (Map<String, List<String>>)data;
                // 选择的语言
                List<String> listLanguage = mapData.get("lan");
                // 选择的图片
                List<String> listImg = mapData.get("img");
                if (listLanguage == null || listLanguage.size() == 0 || listImg == null || listImg.size() == 0) {
                    return;
                }
                if (dySource != null) {
                    DynamicObject dyFunct =
                        BusinessDataServiceHelper.loadSingle(dySource.get("id"), "aos_mkt_functreq");
                    for (String key : listImg) {
                        DynamicObjectCollection dycSource = dyFunct.getDynamicObjectCollection("aos_entity" + key);
                        DynamicObjectCollection dycTarget =
                            this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + key);
                        // 将本单据体的行数 和 目的单据的行数同步
                        Map<String, List<DynamicObject>> mapSource = entityGroupBylan(dycSource, key);
                        Map<String, List<DynamicObject>> mapTarget = entityGroupBylan(dycTarget, key);
                        // 原单副标题行数
                        int sourceSize = mapSource.get("EN").size();
                        // 目标单副标题行数
                        int targetSize = mapTarget.get("EN").size();
                        if (targetSize < sourceSize) {
                            for (int i = 0; i < (sourceSize - targetSize); i++) {
                                this.insertEnity(Integer.parseInt(key));
                            }
                        }
                        // 重新获取
                        dycTarget = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + key);
                        mapTarget = entityGroupBylan(dycTarget, key);
                        setValue(key, listLanguage, mapSource, mapTarget, dycTarget);
                    }
                }
            }
        }
    }

    /** copy目的单增行 **/
    private Map<String, List<DynamicObject>> targetEntityAddRow(String key, DynamicObjectCollection dycRow,
        Map<String, List<DynamicObject>> mapTarget, int row) {
        // 获取 seq
        int seq = dycRow.get(dycRow.size() - 1).getInt("seq");
        for (Map.Entry<String, List<DynamicObject>> entry : mapTarget.entrySet()) {
            // 父行
            DynamicObject dyPidRow = entry.getValue().get(0);
            for (int i = 0; i < row; i++) {
                DynamicObject dyNewRow = dycRow.addNew();
                dyNewRow.set("pid", dyPidRow.get("id"));
                dyNewRow.set("aos_lan" + key, entry.getKey());
                seq++;
                dyNewRow.set("seq", seq);
            }
        }
        // 设置seq
        Map<String, List<DynamicObject>> mapLanRow = entityGroupBylan(dycRow, key);
        seq = 0;
        for (String lan : list_language) {
            List<DynamicObject> listDy = mapLanRow.get(lan);
            for (DynamicObject dy : listDy) {
                seq++;
                dy.set("seq", seq);
            }
        }
        return entityGroupBylan(dycRow, key);
    }

    /** copy to 赋值 **/
    private void setValue(String key, List<String> listLanguage, Map<String, List<DynamicObject>> mapSource,
        Map<String, List<DynamicObject>> mapTarget) {
        for (String language : listLanguage) {
            if (!mapTarget.containsKey(language)) {
                continue;
            }
            if (!mapSource.containsKey(language)) {
                continue;
            }
            List<DynamicObject> listTarget = mapTarget.get(language);
            List<DynamicObject> listSource = mapSource.get(language);
            for (int i = 0; i < listTarget.size(); i++) {
                DynamicObject dyTargetRow = listTarget.get(i);
                // 小于等于，则清空
                if (listSource.size() <= i) {
                    dyTargetRow.set("aos_value" + key, "");
                    dyTargetRow.set("aos_content" + key, "");
                } else {
                    DynamicObject dySourceRow = listSource.get(i);
                    dyTargetRow.set("aos_value" + key, dySourceRow.get("aos_value" + key));
                    dyTargetRow.set("aos_content" + key, dySourceRow.get("aos_content" + key));
                }
            }
        }
    }

    /** copy from 赋值 **/
    private void setValue(String key, List<String> listLanguage, Map<String, List<DynamicObject>> mapSource,
        Map<String, List<DynamicObject>> mapTarget, DynamicObjectCollection dycForm) {
        for (String language : listLanguage) {
            if (!mapTarget.containsKey(language)) {
                continue;
            }
            if (!mapSource.containsKey(language)) {
                continue;
            }
            List<DynamicObject> listTarget = mapTarget.get(language);
            List<DynamicObject> listSource = mapSource.get(language);
            for (int i = 0; i < listTarget.size(); i++) {
                DynamicObject dyTargetRow = listTarget.get(i);
                int index = dycForm.indexOf(dyTargetRow);
                // 小于等于，则清空
                if (listSource.size() <= i) {
                    this.getModel().setValue("aos_value" + key, "", index);
                    this.getModel().setValue("aos_content" + key, "", index);
                } else {
                    DynamicObject dySourceRow = listSource.get(i);
                    this.getModel().setValue("aos_value" + key, dySourceRow.get("aos_value" + key), index);
                    this.getModel().setValue("aos_content" + key, dySourceRow.get("aos_content" + key), index);
                }
            }
        }
    }

    /** 清空 **/
    private void clear() {
        for (int i = 1; i < SEVEN; i++) {
            TreeEntryGrid entryGrid = this.getControl("aos_entity" + i);
            int[] selectRows = entryGrid.getSelectRows();
            for (int selectRow : selectRows) {
                this.getModel().setValue("aos_value" + i, "", selectRow);
                this.getModel().setValue("aos_content" + i, "", selectRow);
            }
        }
    }

    /** 编辑 **/
    private void edit() {
        this.getModel().setValue("aos_limit", "B");
        this.getView().setVisible(true, "bar_save");
    }

    /** 对于单据体 **/
    private void initEntity() {
        // 主标题行
        LocaleString localeTitle = new LocaleString();
        localeTitle.setLocaleValue_zh_CN("主标题");
        localeTitle.setLocaleValue_en("Main Title");
        LocaleString localValue = new LocaleString();
        localValue.setLocaleValue_zh_CN("作图备注");
        localValue.setLocaleValue_en("Note");
        // 记录每个单据的子单据体多少行
        Map<String, Integer> mapSubIndex = new HashMap<>(16);
        for (int i = 1; i < SEVEN; i++) {
            DynamicObjectCollection dyc =
                this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + i);
            // 记录主行的 lan
            Map<String, String> mapMainRowLan = new HashMap<>(16);
            int mainRow = 0;
            for (int index = 0; index < dyc.size(); index++) {
                DynamicObject dyRow = dyc.get(index);
                if (dyRow.getInt("pid") == 0) {
                    if (Cux_Common_Utl.IsNull(dyRow.get("aos_language" + i))) {
                        this.getModel().setValue("aos_language" + i, list_language.get(mainRow), index);
                        this.getModel().setValue("aos_lan" + i, list_language.get(mainRow), index);
                    }
                    this.getModel().setValue("aos_head" + i, localeTitle, index);
                    this.getModel().setValue("aos_title" + i, localValue, index);
                    mapMainRowLan.put(dyRow.getString("id"), list_language.get(mainRow));
                    mainRow++;
                }
                // 子单据
                else if (dyRow.getInt("pid") != 0) {
                    int subRows = mapSubIndex.getOrDefault(dyRow.getString("pid"), 0);
                    subRows++;
                    LocaleString localHead = new LocaleString();
                    localHead.setLocaleValue_en("Sub title" + subRows);
                    localHead.setLocaleValue_zh_CN("副标题" + subRows);
                    this.getModel().setValue("aos_head" + i, localHead, index);
                    localHead.setLocaleValue_zh_CN("正文" + subRows);
                    localHead.setLocaleValue_en("Text" + subRows);
                    this.getModel().setValue("aos_title" + i, localHead, index);
                    mapSubIndex.put(dyRow.getString("pid"), subRows);
                }
            }
            // 补上 lan
            for (int index = 0; index < dyc.size(); index++) {
                DynamicObject dyRow = dyc.get(index);
                // 子单据
                if (dyRow.getInt("pid") != 0) {
                    if (Cux_Common_Utl.IsNull(dyRow.get("aos_lan" + i))) {
                        String pid = dyRow.getString("pid");
                        if (mapMainRowLan.containsKey(pid)) {
                            this.getModel().setValue("aos_lan" + i, mapMainRowLan.get(pid), index);
                        }
                    }

                }
            }
            TreeEntryGrid treeEntryGrid = this.getControl("aos_entity" + i);
            treeEntryGrid.setCollapse(false);
        }
        IPageCache pageCache = this.getView().getPageCache();
        for (Map.Entry<String, Integer> entry : mapSubIndex.entrySet()) {
            pageCache.put(entry.getKey(), entry.getValue().toString());
        }
    }

    /** 对于图片 **/
    private void initPic() {
        // 数据层
        Object aosItemid = this.getModel().getValue("aos_picitem");
        // 如果存在物料 设置图片
        if (aosItemid != null) {
            String itemNumber = ((DynamicObject)aosItemid).getString("number");
            String url = "https://clss.s3.amazonaws.com/" + itemNumber + ".jpg";
            Image image = this.getControl("aos_image");
            image.setUrl(url);
        }
    }

    /** 增行 **/
    private void insertEnity(int entityIndex) {
        String language;
        DynamicObjectCollection dycRow =
            this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + entityIndex);
        int insertRow; // 增行的位置
        for (int i = 0; i < dycRow.size(); i++) {
            DynamicObject dyRow = dycRow.get(i);
            if (dyRow.getLong("pid") == 0) {
                insertRow = i;
                language = dyRow.getString("aos_language" + entityIndex);
                this.getPageCache().put(KEY_INSERT, language);
                this.getModel().insertEntryRow("aos_entity" + entityIndex, insertRow);
            }
        }
    }

    /** 删行 **/
    private void deleteEntity(int entityIndex) {
        EntryGrid entryGrid = this.getView().getControl("aos_entity" + entityIndex);
        int[] selectRows = entryGrid.getSelectRows();
        if (selectRows.length == 0) {
            this.getView().showTipNotification("请先选择EN下需要删除的数据");
        } else {
            List<String> listDeleteRow = new ArrayList<>(selectRows.length);
            for (int row : selectRows) {
                String lan = (String)this.getModel().getValue("aos_lan" + entityIndex, row);
                if ("EN".equals(lan)) {
                    listDeleteRow
                        .add(this.getModel().getEntryRowEntity("aos_entity" + entityIndex, row).getString("id"));
                }
            }
            DynamicObjectCollection dycEntity =
                this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + entityIndex);
            Map<String, List<DynamicObject>> mapEnt = dycEntity.stream().sorted((dy1, dy2) -> {
                int seq1 = dy1.getInt("seq");
                int seq2 = dy2.getInt("seq");
                if (seq1 > seq2) {
                    return 1;
                } else {
                    return -1;
                }
            }).collect(Collectors.groupingBy(dy -> dy.getString("aos_lan" + entityIndex)));
            // 对应到语言的第几列
            List<Integer> listDeleteIndex = new ArrayList<>();
            List<DynamicObject> listEn = mapEnt.get("EN");
            for (int i = 0; i < listEn.size(); i++) {
                if (listDeleteRow.contains(listEn.get(i).getString("id"))) {
                    listDeleteIndex.add(i);
                }
            }
            for (Map.Entry<String, List<DynamicObject>> entry : mapEnt.entrySet()) {
                List<DynamicObject> listLanRow = entry.getValue();
                for (Integer index : listDeleteIndex) {
                    if (listLanRow.size() > index) {
                        this.getModel().deleteEntryRow("aos_entity" + entityIndex,
                            dycEntity.indexOf(listLanRow.get(index)));
                    }
                }
            }
        }
    }

    /** 状态控制 **/
    private void statusControl() {
        this.getModel().setValue("aos_limit", "A");
        this.getView().setVisible(false, "bar_save");
        // 根据用户查找相应的人员能够操作的语言
        QFBuilder builder = new QFBuilder();
        builder.add("aos_user", "=", RequestContext.get().getCurrUserId());
        DynamicObject dyUserPermiss =
            QueryServiceHelper.queryOne("aos_mkt_permiss_lan", "aos_row,aos_exchange,aos_lan", builder.toArray());
        List<String> userCopyLanguage = new ArrayList<>();
        if (dyUserPermiss != null) {
            isAddRow = dyUserPermiss.getBoolean("aos_row");
            isExchange = dyUserPermiss.getBoolean("aos_exchange");
            if (!Cux_Common_Utl.IsNull(dyUserPermiss.get(AOS_LAN))) {
                String[] lanS = dyUserPermiss.getString("aos_lan").split(",");
                // 用户能够copy的语言
                for (String lan : lanS) {
                    if (!Cux_Common_Utl.IsNull(lan)) {
                        userCopyLanguage.add(lan);
                    }
                }
            }
        }
        if (!isExchange) {
            this.getView().setVisible(false, "aos_exchange");
        }
        if (!isAddRow) {
            // 增删行标识
            List<String> listKey = new ArrayList<>(12);
            for (int i = 1; i < SEVEN; i++) {
                listKey.add("aos_addrow" + i);
                listKey.add("aos_deleterow" + i);
            }
            this.getView().setVisible(false, listKey.toArray(new String[0]));
        }
        StringJoiner str = new StringJoiner(",");
        for (String lan : userCopyLanguage) {
            str.add(lan);
        }
        getModel().setValue("aos_u_lan", str.toString());
        this.getPageCache().put(KEY_USER, SerializationUtils.toJsonString(userCopyLanguage));

        DynamicObjectCollection entityS = this.getModel().getEntryEntity("aos_entryentity2");
        if (FndGlobal.IsNotNull(entityS) && entityS.size() > 0) {
            TabPage control = this.getControl("aos_tabpageap7");
            BadgeInfo badgeInfo = new BadgeInfo();
            badgeInfo.setBadgeText("!");
            badgeInfo.setOffset(new String[] {"5px", "5px"});
            control.setBadgeInfo(badgeInfo);
        }

    }

    /**
     * 控制单据的能否缩放
     */
    private void setEntityStatus() {
        @SuppressWarnings("unchecked")
        List<String> users =
            (List<String>)SerializationUtils.fromJsonStringToList(this.getPageCache().get(KEY_USER), String.class);
        if (!users.contains(ZHCN)) {
            users.add(ZHCN);
        }
        if (!users.contains(EN)) {
            users.add(EN);
        }
        for (int i = 1; i < SEVEN; i++) {
            TreeEntryGrid control = getControl("aos_entity" + i);
            DynamicObjectCollection dyc =
                this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + i);
            List<Integer> list = new ArrayList<>(dyc.size());
            for (int row = 0; row < dyc.size(); row++) {
                DynamicObject dyRow = dyc.get(row);
                String pid = dyRow.getString("pid");
                if (FndGlobal.IsNotNull(pid) && pid.equals(ZERO)) {
                    String lan = dyRow.getString("aos_lan" + i);
                    if (users.contains(lan)) {
                        list.add(row);
                    }
                }
            }
            int[] rows = new int[list.size()];
            for (int index = 0; index < list.size(); index++) {
                rows[index] = list.get(index);
            }
            control.expand(rows);
        }
    }

    /** 翻译 **/
    private void translate(Map<String, Object> paraments) throws InterruptedException, DeepLException {
        // 源语言
        String source = (String)paraments.get("source");
        String sourceLan;
        if (source.equals(UK) || source.equals(US) || source.equals(CA) || source.equals(EN)) {
            sourceLan = "EN";
        } else {
            sourceLan = map_transLan.get(source);
        }
        // 需要翻译的语言
        @SuppressWarnings("unchecked")
        List<String> listLans = (List<String>)paraments.get("terminal");
        if (listLans.size() == 0) {
            return;
        }
        // 图片页
        @SuppressWarnings("unchecked")
        List<String> img = (List<String>)paraments.get("img");
        // 存储翻译的内容
        List<String> listValue = new ArrayList<>();
        // 存储翻译内容的位置
        List<String> listValueIndex = new ArrayList<>();
        Map<String, Map<String, List<DynamicObject>>> mapImgEntity = new HashMap<>(16);
        for (String entityIndex : img) {
            EntryGrid entryGrid = this.getControl("aos_entity" + entityIndex);
            int[] selectRows = entryGrid.getSelectRows();
            // 翻译行主键
            List<String> listTranslateRow = new ArrayList<>(selectRows.length);
            boolean wholeTranslate = selectRows.length == 0;
            DynamicObjectCollection dycEntity =
                this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + entityIndex);
            // 记录需要翻译的行主键
            if (!wholeTranslate) {
                for (int row : selectRows) {
                    if (dycEntity.get(row).getString("aos_lan" + entityIndex).equals(source)) {
                        listTranslateRow.add(dycEntity.get(row).getString("id"));
                    }
                }
            }
            // 不为全翻，但是又没勾选对应的语言行，默认还是为全翻
            if (!wholeTranslate && listTranslateRow.size() == 0) {
                wholeTranslate = true;
            }
            // 根据语言分组
            Map<String, List<DynamicObject>> mapEnt = dycEntity.stream().sorted((dy1, dy2) -> {
                int seq1 = dy1.getInt("seq");
                int seq2 = dy2.getInt("seq");
                if (seq1 > seq2) {
                    return 1;
                } else {
                    return -1;
                }
            }).collect(Collectors.groupingBy(dy -> dy.getString("aos_lan" + entityIndex)));
            mapImgEntity.put(entityIndex, mapEnt);
            // 翻译源
            List<DynamicObject> listSourceLan = mapEnt.get(source);
            for (int i = 0; i < listSourceLan.size(); i++) {
                DynamicObject dy = listSourceLan.get(i);
                // 不为全翻，且不在勾选的行里面
                if (!wholeTranslate && !listTranslateRow.contains(dy.getString("id"))) {
                    continue;
                }
                String value = dy.getString("aos_value" + entityIndex);
                if (!Cux_Common_Utl.IsNull(value)) {
                    listValue.add(value);
                    listValueIndex.add(entityIndex + "-" + i + "-1");
                }
                // 作图备注不翻译
                if (dy.getInt("pid") != 0) {
                    value = dy.getString("aos_content" + entityIndex);
                    if (!Cux_Common_Utl.IsNull(value)) {
                        listValue.add(value);
                        listValueIndex.add(entityIndex + "-" + i + "-2");
                    }
                }
            }
        }
        // 无翻译内容
        if (listValue.size() == 0) {
            return;
        }
        for (String lan : listLans) {
            List<String> textResults = translateUtils.transalate(sourceLan, lan, listValue);
            // 翻译结果的下标
            int resultIndex = 0;
            for (String entityIndex : img) {
                Map<String, List<DynamicObject>> mapEnt = mapImgEntity.get(entityIndex);
                List<DynamicObject> listOtehrLan = mapEnt.get(lan);
                for (int i = 0; i < listOtehrLan.size(); i++) {
                    DynamicObject dy = listOtehrLan.get(i);
                    if (listValueIndex.contains(entityIndex + "-" + i + "-1")) {
                        dy.set("aos_value" + entityIndex, textResults.get(resultIndex));
                        resultIndex++;
                    }
                    if (listValueIndex.contains(entityIndex + "-" + i + "-2")) {
                        dy.set("aos_content" + entityIndex, textResults.get(resultIndex));
                        resultIndex++;
                    }
                }
            }
        }
        for (String index : img) {
            this.getView().updateView("aos_entity" + index);
        }
    }

    /** 图片交换 **/
    private void imgExchange(Object sourceImage, Object endImage) {
        DynamicObjectCollection dycSource =
            this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + sourceImage);
        DynamicObjectCollection dycSourceClone = (DynamicObjectCollection)dycSource.clone();
        DynamicObjectCollection dycEnd =
            this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + endImage);
        DynamicObjectCollection dycEndClone = (DynamicObjectCollection)dycEnd.clone();
        dycSource.clear();
        dycEnd.clear();
        for (DynamicObject dy : dycEndClone) {
            DynamicObject dyNew = dycSource.addNew();
            copyEntityValue(dyNew, sourceImage, dy, endImage);
        }
        for (DynamicObject dy : dycSourceClone) {
            DynamicObject dyNew = dycEnd.addNew();
            copyEntityValue(dyNew, endImage, dy, sourceImage);
        }
        Object imgSource = this.getModel().getValue("aos_picture" + sourceImage);
        Object imgEnd = this.getModel().getValue("aos_picture" + endImage);
        this.getModel().setValue("aos_picture" + sourceImage, imgEnd);
        this.getModel().setValue("aos_picture" + endImage, imgSource);
        this.getView().updateView("aos_entity" + sourceImage);
        this.getView().updateView("aos_entity" + endImage);
        TreeEntryGrid treeEntryGrid = this.getControl("aos_entity" + sourceImage);
        treeEntryGrid.setCollapse(false);
        treeEntryGrid = this.getControl("aos_entity" + endImage);
        treeEntryGrid.setCollapse(false);
        int[] rows = new int[] {Integer.parseInt(sourceImage.toString()), Integer.parseInt(endImage.toString())};
        intiSensitiveWordsColor(rows);
    }

    /**
     * 初始化敏感词
     */
    private void initSensitiveWords() {
        // 根据产品号查询一个最新的物料
        Object productNo = getModel().getValue("aos_segment3");
        JSONObject lanSensitiveWords = sensitiveWordsUtils.FindMaterialSensitiveWords(productNo);
        getPageCache().put(KEY_SENSITIVE, lanSensitiveWords.toString());
        intiSensitiveWordsColor();
    }

    private void intiSensitiveWordsColor(int... indexs) {
        List<Integer> entityRows = new ArrayList<>();
        // 判断哪些单据需要进行颜色控制
        String contronalType;
        if (indexs.length > 0) {
            for (int index : indexs) {
                entityRows.add(index);
            }
            contronalType = "A";
        } else {
            for (int i = 1; i < SEVEN; i++) {
                entityRows.add(i);
            }
            contronalType = "B";
        }

        // 设置单据颜色和徽标
        for (int i : entityRows) {
            DynamicObjectCollection entity =
                getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + i);
            List<Integer> valueRows = new ArrayList<>(), contentRows = new ArrayList<>();
            boolean sensitivExist = false;
            // 判断哪些行上存在敏感词
            for (int index = 0; index < entity.size(); index++) {
                DynamicObject dyRow = entity.get(index);
                if (dyRow.getBoolean("aos_value_s" + i)) {
                    valueRows.add(index);
                    if (!sensitivExist) {
                        sensitivExist = true;
                    }
                }
                if (dyRow.getBoolean("aos_content" + i)) {
                    contentRows.add(index);
                    if (!sensitivExist) {
                        sensitivExist = true;
                    }
                }
            }
            if (valueRows.size() > 0) {
                int[] rows = valueRows.stream().mapToInt(Integer::intValue).toArray();
                setEntityRowColor("aos_entity" + i, "aos_head" + i, rows, sensitivExist);
            }
            if (contentRows.size() > 0) {
                int[] rows = contentRows.stream().mapToInt(Integer::intValue).toArray();
                setEntityRowColor("aos_entity" + i, "aos_title" + i, rows, sensitivExist);
            }

            boolean[] existRow = new boolean[] {sensitivExist};
            // 当是图片交换类型的时候，需要将没有敏感词的页签去掉徽标
            if ("A".equals(contronalType)) {
                setTableColor(String.valueOf(i), existRow);
            }
            // 当是初始化界面时候，不需要将没有敏感词的页签去掉徽标
            else {
                if (sensitivExist) {
                    setTableColor(String.valueOf(i), existRow);
                }
            }
        }
    }

    /**
     * 校验所有单据是否有敏感词
     */
    private boolean filterSentiviteWords() {
        boolean result = false;
        for (int entityInex = 1; entityInex < SEVEN; entityInex++) {
            DynamicObjectCollection entityRows =
                getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + entityInex);
            for (int row = 0; row < entityRows.size(); row++) {
                DynamicObject entityRow = entityRows.get(row);
                boolean exiteSentivite = filterSentiviteWordsRow(entityRow, row, "aos_value" + entityInex, false);
                if (!result) {
                    result = exiteSentivite;
                }
                exiteSentivite = filterSentiviteWordsRow(entityRow, row, "aos_content" + entityInex, false);
                if (!result) {
                    result = exiteSentivite;
                }
            }
        }
        if (result) {
            getView().updateView();
            intiSensitiveWordsColor();
        }
        return result;
    }

    private boolean filterSentiviteWordsRow(DynamicObject entityRow, int rowIndex, String fieldName,
        boolean colorControl) {
        String valueField = fieldName.substring(0, fieldName.length() - 1);
        String lanIndex = fieldName.substring(fieldName.length() - 1);
        String language = entityRow.getString("aos_lan" + lanIndex);
        String text = entityRow.getString(fieldName);
        if (language.equals(ZHCN)) {
            return false;
        }
        JSONObject sensitiveWords = JSONObject.parseObject(getPageCache().get(KEY_SENSITIVE));
        JSONObject sensitiveResult = sensitiveWordsUtils.sensitiveWordVerificate(sensitiveWords, text, language);
        String entityName = "aos_entity" + lanIndex;
        DynamicObjectCollection subEntityRows = entityRow.getDynamicObjectCollection("aos_subentryentity" + lanIndex);
        // 删除子单据体中原本的对应类型的敏感词
        String fieldType, titleField;
        if (AOS_VALUE.equals(valueField)) {
            titleField = "aos_head" + lanIndex;
            String value = entityRow.getString(titleField);
            boolean cond = (FndGlobal.IsNotNull(value) && ("主标题".equals(value) || "Main Title".equals(value)));
            if (cond) {
                fieldType = "A";
            } else {
                fieldType = "C";
            }
        } else {
            titleField = "aos_title" + lanIndex;
            String value = entityRow.getString(titleField);
            boolean cond = (FndGlobal.IsNotNull(value) && ("作图备注".equals(value) || "Note".equals(value)));
            if (cond) {
                fieldType = "B";
            } else {
                fieldType = "D";
            }
        }
        subEntityRows.removeIf(next -> next.getString("aos_type" + lanIndex).equals(fieldType));
        boolean state = sensitiveResult.getBoolean("state");
        if (state) {
            JSONArray wordsArr = sensitiveResult.getJSONArray("data");
            for (int i = 0; i < wordsArr.size(); i++) {
                JSONObject words = wordsArr.getJSONObject(i);
                DynamicObject subEntityRow = subEntityRows.addNew();
                subEntityRow.set("aos_type" + lanIndex, fieldType);
                subEntityRow.set("aos_wordtype" + lanIndex, words.getString("type"));
                subEntityRow.set("aos_sublan" + lanIndex, words.getString("lan"));
                subEntityRow.set("aos_word" + lanIndex, words.getString("words"));
                subEntityRow.set("aos_subword" + lanIndex, words.getString("replace"));
                subEntityRow.set("aos_replace" + lanIndex, "replace");
            }
        }
        entityRow.set(valueField + "_s" + lanIndex, state);
        if (colorControl) {
            this.getView().updateView("aos_subentryentity" + lanIndex);
            int[] rows = new int[] {rowIndex};
            setEntityRowColor(entityName, titleField, rows, state);
            setTableColor(lanIndex);
        }
        return state;
    }

    /**
     * 设置单据颜色
     *
     * @param entityName 单据名
     * @param fieldName 字段名
     * @param rows 行
     * @param state 是否存在敏感词
     */
    private void setEntityRowColor(String entityName, String fieldName, int[] rows, boolean state) {
        // 获取分录表体
        AbstractGrid grid = this.getView().getControl(entityName);
        ArrayList<CellStyle> csList = new ArrayList<>();
        for (int row : rows) {
            CellStyle cs = new CellStyle();
            if (state) {
                // 红色
                cs.setForeColor("#fb2323");
            } else {
                // 黑色
                cs.setForeColor("#404040");
            }
            // 列标识
            cs.setFieldKey(fieldName);
            // 行索引
            cs.setRow(row);
            csList.add(cs);
        }
        // 设置单元格样式
        grid.setCellStyle(csList);
    }

    /**
     * 设置页签徽标
     *
     * @param index 行
     */
    private void setTableColor(String index, boolean... senWords) {
        DynamicObjectCollection dyc =
            this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + index);
        boolean sensitiveWords = false;
        if (senWords.length > 0) {
            sensitiveWords = senWords[0];
        } else {
            for (DynamicObject dyRow : dyc) {
                if (dyRow.getBoolean("aos_value_s" + index)) {
                    sensitiveWords = true;
                    break;
                }
                if (dyRow.getBoolean("aos_content_s" + index)) {
                    sensitiveWords = true;
                    break;
                }
            }
        }
        TabPage control = this.getControl("aos_tabpageap" + index);
        BadgeInfo badgeInfo = new BadgeInfo();
        if (sensitiveWords) {
            badgeInfo.setBadgeText("!");
            badgeInfo.setOffset(new String[] {"5px", "5px"});
        } else {
            badgeInfo.setOffset(new String[] {"-50px", "-50px"});
        }
        control.setBadgeInfo(badgeInfo);
    }

    private void copyEntityValue(DynamicObject dyNew, Object newImage, DynamicObject dySource, Object sourceImage) {
        // 子单据体赋值
        DynamicObjectCollection sourceSubEntity =
            dySource.getDynamicObjectCollection("aos_subentryentity" + sourceImage);
        DynamicObjectCollection newSubEntity = dyNew.getDynamicObjectCollection("aos_subentryentity" + newImage);
        for (DynamicObject sourceSubRow : sourceSubEntity) {
            DynamicObject newSubRow = newSubEntity.addNew();
            newSubRow.set("aos_type" + newImage, sourceSubRow.get("aos_type" + sourceImage));
            newSubRow.set("aos_wordtype" + newImage, sourceSubRow.get("aos_wordtype" + sourceImage));
            newSubRow.set("aos_sublan" + newImage, sourceSubRow.get("aos_sublan" + sourceImage));
            newSubRow.set("aos_word" + newImage, sourceSubRow.get("aos_word" + sourceImage));
            newSubRow.set("aos_subword" + newImage, sourceSubRow.get("aos_subword" + sourceImage));
            newSubRow.set("aos_replace" + newImage, sourceSubRow.get("aos_replace" + sourceImage));
        }
        dyNew.set("id", dySource.get("id"));
        dyNew.set("pid", dySource.get("pid"));
        dyNew.set("aos_language" + newImage, dySource.get("aos_language" + sourceImage));
        dyNew.set("aos_head" + newImage, dySource.get("aos_head" + sourceImage));
        dyNew.set("aos_value" + newImage, dySource.get("aos_value" + sourceImage));
        dyNew.set("aos_title" + newImage, dySource.get("aos_title" + sourceImage));
        dyNew.set("aos_content" + newImage, dySource.get("aos_content" + sourceImage));
        dyNew.set("aos_lan" + newImage, dySource.get("aos_lan" + sourceImage));
        dyNew.set("aos_value_s" + newImage, dySource.get("aos_value_s" + sourceImage));
        dyNew.set("aos_content_s" + newImage, dySource.get("aos_content_s" + sourceImage));
    }

    @Override
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        DynamicObject dataEntity = getModel().getDataEntity(true);
        DataEntityState dataEntityState = dataEntity.getDataEntityState();

        DataEntityPropertyCollection properties = dataEntity.getDataEntityType().getProperties();

        // 取消单据头的 界面控制字段保存校验
        IDataEntityProperty property = properties.get("aos_limit");
        dataEntityState.setBizChanged(property.getOrdinal(), false);

        property = dataEntity.getDataEntityType().getProperties().get("aos_u_lan");
        dataEntityState.setBizChanged(property.getOrdinal(), false);

        // 取消树形单据体的 标题 保存校验
        List<String> fields = Arrays.asList("aos_language", "aos_head", "aos_title", "aos_lan");
        for (int index = 1; index < SEVEN; index++) {
            DynamicObjectCollection entityRows = dataEntity.getDynamicObjectCollection("aos_entity" + index);
            for (DynamicObject entityRow : entityRows) {
                DataEntityPropertyCollection entryProes = entityRow.getDataEntityType().getProperties();
                for (String field : fields) {
                    property = entryProes.get(field + index);
                    entityRow.getDataEntityState().setBizChanged(property.getOrdinal(), false);
                }
            }
        }
    }
}