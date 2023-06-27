package mkt.progress.design.functDiagram;

import com.deepl.api.DeepLException;
import com.deepl.api.TextResult;
import com.deepl.api.Translator;
import common.Cux_Common_Utl;
import common.fnd.FndHistory;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.dataentity.serialization.SerializationUtils;
import kd.bos.entity.datamodel.events.AfterAddRowEventArgs;
import kd.bos.entity.datamodel.events.AfterDeleteRowEventArgs;
import kd.bos.entity.datamodel.events.BeforeDeleteRowEventArgs;
import kd.bos.exception.ErrorCode;
import kd.bos.exception.KDException;
import kd.bos.form.CloseCallBack;
import kd.bos.form.FormShowParameter;
import kd.bos.form.IPageCache;
import kd.bos.form.ShowType;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.Image;
import kd.bos.form.control.TreeEntryGrid;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import kd.fi.bd.util.QFBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author create by gk
 * @date 2023/3/30 13:38
 * @action  功能图需求表界面插件
 */
@SuppressWarnings({"unchecked","rawtypes"})
public class functDiagramForm extends AbstractBillPlugIn {
    public static String  authKey = "7d2a206b-8182-c99a-a314-76874dd27d89:fx";
    Translator translator = new Translator(authKey);
    public static  List<String> list_language;
    public static  Map<String,String> map_transLan;    //语言对应的翻译标识
    private static final String KEY_DELETE = "DELETE";  //删除行pid
    private static final String KEY_INSERT = "INSERT";  //增行的语言
    private boolean isAddRow = false; //增删行权限
    private boolean isExchange = false; //交换界面权限
    private static final String KEY_USER = "LAN"; //用户可操控的语言
    static {
        list_language = Arrays.asList("中文","EN","DE","FR","IT","ES","PT","RO","UK","US","CA");
        map_transLan = new HashMap<>();
        map_transLan.put("中文","ZH");
        map_transLan.put("EN","EN-GB");
        map_transLan.put("DE","DE");
        map_transLan.put("FR","FR");
        map_transLan.put("IT","IT");
        map_transLan.put("ES","ES");
        map_transLan.put("PT","PT-PT");
        map_transLan.put("RO","RO");
        map_transLan.put("UK","EN-GB");
        map_transLan.put("US","EN-US");
        map_transLan.put("CA","EN-US");
    }
    @Override
    public void afterBindData(EventObject e)  {
        super.afterBindData(e);
        InitPic();
        StatusControl();
        InitEntity();
//        addRow();
    }
    @Override
    public void afterAddRow(AfterAddRowEventArgs e) {
        String name = e.getEntryProp().getName();
        int insertRow = e.getInsertRow();
        String entityIndex = name.substring(name.length() - 1);
        DynamicObject dy_insertRow = this.getModel().getDataEntity(true).getDynamicObjectCollection(name).get(insertRow);
        String pid = dy_insertRow.getString("pid");
        String lan =this.getPageCache().get(KEY_INSERT);
        int index =1;
        if (!Cux_Common_Utl.IsNull(this.getPageCache().get(pid))) {
            index = Integer.parseInt(getPageCache().get(pid)) + 1;
        }
        getPageCache().put(pid,String.valueOf(index));
        LocaleString local_head = new LocaleString();
        local_head.setLocaleValue_en("Sub title"+index);
        local_head.setLocaleValue_zh_CN("副标题"+index);
        this.getModel().setValue("aos_head"+entityIndex,local_head,insertRow);
        local_head.setLocaleValue_zh_CN("正文"+index);
        local_head.setLocaleValue_en("Text"+index);
        this.getModel().setValue("aos_title"+entityIndex,local_head,insertRow);
        this.getModel().setValue("aos_lan"+entityIndex,lan,insertRow);
    }

    @Override
    public void beforeDeleteRow(BeforeDeleteRowEventArgs e) {
        int[] rowIndexs = e.getRowIndexs();
        String name = e.getEntryProp().getName();
        String pid = this.getModel().getDataEntity(true).getDynamicObjectCollection(name).get(rowIndexs[0]).getString("pid");
        if (pid.equals("0"))
            e.setCancel(true);
        else if (!Cux_Common_Utl.IsNull(getPageCache().get(pid))){
            int index = Integer.parseInt(getPageCache().get(pid));
            index = index-rowIndexs.length;
            getPageCache().put(pid,String.valueOf(index));
        }
        this.getPageCache().put(KEY_DELETE,pid);
    }
    @Override
    public void afterDeleteRow(AfterDeleteRowEventArgs e) {
        String pid = this.getView().getPageCache().get(KEY_DELETE);
        int rows = Integer.parseInt(this.getPageCache().get(pid)); //现在有的行
        String name = e.getEntryProp().getName();
        DynamicObjectCollection dyc = this.getModel().getDataEntity(true).getDynamicObjectCollection(name);
        if (rows>0) {
            String entityIndex = name.substring(name.length() - 1);
            int index = 1;
            for (DynamicObject dy : dyc) {
                if (dy.getString("pid").equals(pid)){
                    LocaleString local_head = new LocaleString();
                    local_head.setLocaleValue_en("Subtitle"+index);
                    local_head.setLocaleValue_zh_CN("副标题"+index);
                    dy.set("aos_head"+entityIndex,local_head);
                    local_head.setLocaleValue_zh_CN("正文"+index);
                    local_head.setLocaleValue_en("main body"+index);
                    dy.set("aos_title"+entityIndex,local_head);
                    index++;
                }
            }
        }
    }
    @Override
    public void afterDoOperation(AfterDoOperationEventArgs eventArgs) {
        super.afterDoOperation(eventArgs);
        String operateKey = eventArgs.getOperateKey();
        //增行
        if (operateKey.substring(0,operateKey.length()-1).equals("insert")){
            int  entityIndex = Integer.parseInt(operateKey.substring(operateKey.length() - 1));
            insertEnity(entityIndex);
        }
        //删行
        else if (operateKey.substring(0,operateKey.length()-1).equals("deleteentry")){
            int entityIndex = Integer.parseInt(operateKey.substring(operateKey.length() - 1));
            deleteEntity(entityIndex);
        }
        //编辑
        else if (operateKey.equals("edit")){
            edit();
        }
        //清除
        else if (operateKey.equals("clear"))
            clear();
        //保存记录日志
        else if (operateKey.equals("save")){
            FndHistory.Create(this.getView(), "保存","");
        }
        //查看日志
        else if ("aos_history".equals(operateKey))
           Cux_Common_Utl.OpenHistory(this.getView());// 查看历史记录
        else if (operateKey.equals("copyfrom"))
            findCate("aos_mkt_functcopy",operateKey,"Copy From");
        else if (operateKey.equals("copyto"))
            findCate("aos_mkt_functcopy","copyto","Copy To");
        else if (operateKey.equals("translate"))
            findCate("aos_mkt_funct_tran", "trans", "Translate Form");
        else if (operateKey.equals("exchange"))
            findCate("aos_mkt_funct_ex","exchange","Image Exchange");
    }
    @Override
    public void closedCallBack(ClosedCallBackEvent event) {
        super.closedCallBack(event);
        String actionId = event.getActionId();
        if (actionId.equals("copyfrom")) {
            Object returnData = event.getReturnData();
            if (returnData!=null){
                copyFrom((Map<String, Object>) returnData);
//                this.getView().invokeOperation("save");
                this.getView().showSuccessNotification("Copy from Success");
            }
        }
        else if (actionId.equals("copyto")){
            Object returnData = event.getReturnData();
            if (returnData!=null){
                copyTo((Map<String, Object>) returnData);
                this.getView().showSuccessNotification("Copy to Success");
            }
        }
        else if (actionId.equals("trans")){
            Object returnData = event.getReturnData();
            if (returnData !=null){
                try {
                    translate((Map<String, Object>) returnData);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (DeepLException e) {
                    e.printStackTrace();
                    throw new KDException(new ErrorCode("翻译异常",e.getMessage()));
                }
                this.getView().showSuccessNotification("Translate Success");
            }
        }
        else if (actionId.equals("exchange") && event.getReturnData()!=null ){
           Map<String,Object> returnData = (Map<String, Object>) event.getReturnData();
            Object source = returnData.get("source");
            Object end = returnData.get("end");
            if (!source.equals(end))
                imgExchange(source,end);
        }
    }
    /** copy 弹窗 **/
    private void findCate(String page,String callback,String name){
        //创建弹出页面对象，FormShowParameter表示弹出页面为动态表单
        FormShowParameter ShowParameter = new FormShowParameter();
        //设置弹出页面的编码
        ShowParameter.setFormId(page);
        //设置弹出页面标题
        ShowParameter.setCaption(name);
        List<String> users= (List<String>) SerializationUtils.fromJsonStringToList(this.getPageCache().get(KEY_USER), String.class);
        ShowParameter.setCustomParam("userLan",users);
        //设置页面关闭回调方法
        //CloseCallBack参数：回调插件，回调标识
        ShowParameter.setCloseCallBack(new CloseCallBack(this, callback));
        //设置弹出页面打开方式，支持模态，新标签等
        ShowParameter.getOpenStyle().setShowType(ShowType.Modal);
        //弹出页面对象赋值给父页面
        this.getView().showForm(ShowParameter);
    }
    /** copy to**/
    private void copyTo(Map<String,Object> map_paramet){
        //产品号
        Object skuNo = map_paramet.get("no");
        if (!Cux_Common_Utl.IsNull(skuNo)){
            Object data = map_paramet.get("data");
            if (data!=null){
                String itemNo = (String) skuNo;
                List<String> list_nos = Arrays.stream(itemNo.split(","))
                        .filter(no -> no != null && !no.equals(""))
                        .distinct()
                        .collect(Collectors.toList());
                QFBuilder qfBuilder = new QFBuilder();
                qfBuilder.add("aos_segment3",QFilter.in,list_nos);
                List<String> list_functIds = QueryServiceHelper.query("aos_mkt_functreq", "id", qfBuilder.toArray())
                        .stream()
                        .map(dy -> dy.getString("id"))
                        .distinct()
                        .collect(Collectors.toList());
                //选择的单据及其语言信息
                Map<String,List<String>> map_data= (Map<String, List<String>>) data;
                for (String id : list_functIds) {
                    DynamicObject dy_funct = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_functreq");
                    List<String> list_language = map_data.get("lan");   //选择的语言
                    List<String> list_img = map_data.get("img");        //选择的图片
                    for (String key : list_img) {
                        DynamicObjectCollection dyc_source = this.getModel().getDataEntity(true)
                                .getDynamicObjectCollection("aos_entity" + key);
                        DynamicObjectCollection dyc_target = dy_funct.getDynamicObjectCollection("aos_entity" + key);

                        //将本单据体的行数 和 目的单据的行数同步
                        Map<String, List<DynamicObject>> map_source = entityGroupBylan(dyc_source, key);
                        Map<String, List<DynamicObject>> map_target = entityGroupBylan(dyc_target, key);
                        //原单副标题行数
                        int sourceSize = map_source.get("EN").size();
                        //目标单副标题行数
                        int targetSize = map_target.get("EN").size();
                        if (targetSize < sourceSize){
                            map_target = targetEntityAddRow(key,dyc_target,map_target,(sourceSize-targetSize));
                        }
                        setValue(key,list_language,map_source,map_target);
                    }
                    FndHistory.Create(dy_funct,"保存", UserServiceHelper.getCurrentUser("name").getString("name")+" copy to");
                    SaveServiceHelper.save(new DynamicObject[]{dy_funct});
                }
            }
        }
    }
    /**copy fROM **/
    private void copyFrom(Map<String,Object> map_paramet){
        Object skuNo = map_paramet.get("no");
        if (!Cux_Common_Utl.IsNull(skuNo)){
            Object data = map_paramet.get("data");
            if (data!=null){
                String itemNo = (String) skuNo;
                List<String> list_nos = Arrays.stream(itemNo.split(","))
                        .filter(no -> no != null && !no.equals(""))
                        .distinct()
                        .collect(Collectors.toList());
                QFBuilder qfBuilder = new QFBuilder();
                qfBuilder.add("aos_segment3",QFilter.equals,list_nos.get(0));
                DynamicObject dy_source = QueryServiceHelper.queryOne("aos_mkt_functreq", "id", qfBuilder.toArray());

                //选择的单据及其语言信息
                Map<String,List<String>> map_data = (Map<String, List<String>>) data;
                List<String> list_language = map_data.get("lan");   //选择的语言
                List<String> list_img = map_data.get("img");        //选择的图片
                if (dy_source!=null){
                    DynamicObject dy_funct = BusinessDataServiceHelper.loadSingle( dy_source.get("id"), "aos_mkt_functreq");
                    for (String key : list_img) {
                        DynamicObjectCollection dyc_source = dy_funct
                                .getDynamicObjectCollection("aos_entity" + key);
                        DynamicObjectCollection dyc_target = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + key);
                        //将本单据体的行数 和 目的单据的行数同步
                        Map<String, List<DynamicObject>> map_source = entityGroupBylan(dyc_source, key);
                        Map<String, List<DynamicObject>> map_target = entityGroupBylan(dyc_target, key);
                        //原单副标题行数
                        int sourceSize = map_source.get("EN").size();
                        //目标单副标题行数
                        int targetSize = map_target.get("EN").size();
                        if (targetSize < sourceSize){
                            for (int i = 0; i<(sourceSize-targetSize);i++)
                                this.insertEnity(Integer.parseInt(key));
                        }
                        //重新获取
                         dyc_target = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + key);
                         map_target = entityGroupBylan(dyc_target, key);
                        setValue(key,list_language,map_source,map_target,dyc_target);
                    }
                }
            }
        }
    }
    /** copy目的单增行 **/
    private Map<String, List<DynamicObject>> targetEntityAddRow(String key, DynamicObjectCollection dyc_row, Map<String,List<DynamicObject>> map_target,int row){
        //获取 seq
        int seq = dyc_row.get(dyc_row.size() - 1).getInt("seq");
        for (Map.Entry<String, List<DynamicObject>> entry : map_target.entrySet()) {
            DynamicObject dy_pidRow = entry.getValue().get(0);  //父行
            for (int i = 0; i < row; i++) {
                DynamicObject dy_newRow = dyc_row.addNew();
                dy_newRow.set("pid",dy_pidRow.get("id"));
                dy_newRow.set("aos_lan"+key,entry.getKey());
                seq++;
                dy_newRow.set("seq",seq);
            }
        }
        //设置seq
        Map<String, List<DynamicObject>> map_lanRow = entityGroupBylan(dyc_row, key);
        seq = 0;
        for (String lan : list_language) {
            List<DynamicObject> list_dy = map_lanRow.get(lan);
            for (DynamicObject dy : list_dy) {
                seq++;
                dy.set("seq",seq);
            }
        }
        return entityGroupBylan(dyc_row, key);
    }
    /** 将单据根据语言分组，并且根据seq 排序 **/
    private static Map<String, List<DynamicObject>> entityGroupBylan(DynamicObjectCollection dyc, String index){
        return dyc.stream()
                .sorted((dy1, dy2) -> {
                    int seq1 = dy1.getInt("seq");
                    int seq2 = dy2.getInt("seq");
                    if (seq1 < seq2)
                        return -1;
                    else
                        return 1;
                })
                .collect(Collectors.groupingBy(dy -> dy.getString("aos_lan" + index)));
    }
    /** copy to 赋值 **/
    private void setValue(String key,List<String> list_language, Map<String,List<DynamicObject>> map_source,Map<String,List<DynamicObject>> map_target){
        for (String language : list_language) {
            if (!map_target.containsKey(language)) {
                continue;
            }
            if (!map_source.containsKey(language)){
                continue;
            }
            List<DynamicObject> list_target = map_target.get(language);
            List<DynamicObject> list_source = map_source.get(language);

            for (int i = 0; i < list_target.size(); i++) {
                DynamicObject dy_targetRow = list_target.get(i);
                //小于等于，则清空
                if (list_source.size()<=i) {
                    dy_targetRow.set("aos_value"+key,"");
                    dy_targetRow.set("aos_content"+key,"");
                }
                else {
                    DynamicObject dy_sourceRow = list_source.get(i);
                    dy_targetRow.set("aos_value"+key,dy_sourceRow.get("aos_value"+key));
                    dy_targetRow.set("aos_content"+key,dy_sourceRow.get("aos_content"+key));
                }
            }
        }
    }

    /** copy from  赋值 **/
    private void setValue(String key,List<String> list_language, Map<String,List<DynamicObject>> map_source,Map<String,List<DynamicObject>> map_target,DynamicObjectCollection dyc_form){
        for (String language : list_language) {
            if (!map_target.containsKey(language)) {
                continue;
            }
            if (!map_source.containsKey(language)){
                continue;
            }
            List<DynamicObject> list_target = map_target.get(language);
            List<DynamicObject> list_source = map_source.get(language);

            for (int i = 0; i < list_target.size(); i++) {
                DynamicObject dy_targetRow = list_target.get(i);
                int index = dyc_form.indexOf(dy_targetRow);
                //小于等于，则清空
                if (list_source.size()<=i) {
                    this.getModel().setValue("aos_value"+key,"",index);
                    this.getModel().setValue("aos_content"+key,"",index);
                }
                else {
                    DynamicObject dy_sourceRow = list_source.get(i);
                    this.getModel().setValue("aos_value"+key,dy_sourceRow.get("aos_value"+key),index);
                    this.getModel().setValue("aos_content"+key,dy_sourceRow.get("aos_content"+key),index);
                }
            }
        }
    }
    /** 清空 **/
    private void clear(){
        for (int i=1;i<7;i++){
            TreeEntryGrid entryGrid = this.getControl("aos_entity"+i);
            int[] selectRows = entryGrid.getSelectRows();
            for (int selectRow : selectRows) {
                this.getModel().setValue("aos_value"+i,"",selectRow);
                this.getModel().setValue("aos_content"+i,"",selectRow);
            }
        }
    }
    /** 编辑 **/
    private void edit(){
        this.getModel().setValue("aos_limit","B");
        this.getView().setVisible(true,"bar_save");
    }
    /** 对于单据体 **/
    private void InitEntity() {
        //主标题行
        LocaleString locale_title = new LocaleString();
        locale_title.setLocaleValue_zh_CN("主标题");
        locale_title.setLocaleValue_en("Main Title");
        LocaleString local_value = new LocaleString();
        local_value.setLocaleValue_zh_CN("作图备注");
        local_value.setLocaleValue_en("Note");
        Map<String,Integer> map_subIndex = new HashMap<>(); //记录每个单据的子单据体多少行
        for (int i =1 ;i<7;i++){
            DynamicObjectCollection dyc = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + i);
            //记录主行的 lan
            Map<String,String> map_mainRowLan = new HashMap<>();
            int mainRow =0;
            for (int index = 0; index < dyc.size(); index++) {
                DynamicObject dy_row = dyc.get(index);
                if (dy_row.getInt("pid")==0 ){
                    if (Cux_Common_Utl.IsNull(dy_row.get("aos_language"+i))){
                        this.getModel().setValue("aos_language"+i,list_language.get(mainRow),index);
                        this.getModel().setValue("aos_lan"+i,list_language.get(mainRow),index);
                    }
                    this.getModel().setValue("aos_head"+i,locale_title,index);
                    this.getModel().setValue("aos_title"+i,local_value,index);
                    map_mainRowLan.put(dy_row.getString("id"),list_language.get(mainRow));
                    mainRow++;
                }
                //子单据
               else if (dy_row.getInt("pid") != 0){
                    int subRows = map_subIndex.getOrDefault(dy_row.getString("pid"), 0);
                    subRows++;
                    LocaleString local_head = new LocaleString();
                    local_head.setLocaleValue_en("Sub title"+subRows);
                    local_head.setLocaleValue_zh_CN("副标题"+subRows);
                    this.getModel().setValue("aos_head"+i,local_head,index);
                    local_head.setLocaleValue_zh_CN("正文"+subRows);
                    local_head.setLocaleValue_en("Text"+subRows);
                    this.getModel().setValue("aos_title"+i,local_head,index);
                    map_subIndex.put(dy_row.getString("pid"),subRows);
                }
            }
            //补上 lan
            for (int index = 0; index < dyc.size(); index++) {
                DynamicObject dy_row = dyc.get(index);
                //子单据
               if (dy_row.getInt("pid") != 0){
                   if (Cux_Common_Utl.IsNull( dy_row.get("aos_lan"+i))) {
                       String pid = dy_row.getString("pid");
                       if (map_mainRowLan.containsKey(pid))
                        this.getModel().setValue("aos_lan"+i,map_mainRowLan.get(pid),index);
                   }

                }
            }
            TreeEntryGrid treeEntryGrid = this.getControl("aos_entity" + i);
            treeEntryGrid.setCollapse(false);
        }
        IPageCache pageCache = this.getView().getPageCache();
        for (Map.Entry<String, Integer> entry : map_subIndex.entrySet()) {
            pageCache.put(entry.getKey(),entry.getValue().toString());
        }
    }
    /** 对于图片 **/
    private void InitPic() {
        // 数据层
        Object AosItemid = this.getModel().getValue("aos_picitem");
        // 如果存在物料 设置图片
        if (AosItemid != null) {
            String item_number = ((DynamicObject) AosItemid).getString("number");
            String url = "https://clss.s3.amazonaws.com/" + item_number + ".jpg";
            Image image = this.getControl("aos_image");
            image.setUrl(url);
        }
    }
    /** 增行**/
    private void insertEnity(int entityIndex){
        String language;
        DynamicObjectCollection dyc_row = this.getModel().getDataEntity(true)
                .getDynamicObjectCollection("aos_entity" + entityIndex);
        int  insertRow;  //增行的位置
        for (int i = 0; i < dyc_row.size(); i++) {
            DynamicObject dy_row = dyc_row.get(i);
            if (dy_row.getLong("pid")==0) {
                insertRow = i;
                language = dy_row.getString("aos_language"+entityIndex);
                this.getPageCache().put(KEY_INSERT,language);
                this.getModel().insertEntryRow("aos_entity" + entityIndex,insertRow);
            }
        }
    }
    /** 删行 **/
    private void deleteEntity(int entityIndex){
        EntryGrid entryGrid = this.getView().getControl("aos_entity" + entityIndex);
        int[] selectRows = entryGrid.getSelectRows();
        if (selectRows.length == 0) {
            this.getView().showTipNotification("请先选择EN下需要删除的数据");
        }
        else {
            List<String> list_deleteRow = new ArrayList<>(selectRows.length);
            for (int row : selectRows) {
                String lan = (String) this.getModel().getValue("aos_lan" + entityIndex, row);
                if (lan.equals("EN")){
                    list_deleteRow.add(this.getModel().getEntryRowEntity("aos_entity"+entityIndex,row).getString("id"));
                }
            }
            DynamicObjectCollection dyc_entity = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + entityIndex);
            Map<String, List<DynamicObject>> map_ent = dyc_entity.stream()
                    .sorted((dy1, dy2) -> {
                        int seq1 = dy1.getInt("seq");
                        int seq2 = dy2.getInt("seq");
                        if (seq1 > seq2)
                            return 1;
                        else return -1;
                    })
                    .collect(Collectors.groupingBy(dy -> dy.getString("aos_lan" + entityIndex)));
            List<Integer> list_deleteIndex = new ArrayList<>(); //对应到语言的第几列
            List<DynamicObject> list_en = map_ent.get("EN");
            for (int i = 0; i < list_en.size(); i++) {
                if (list_deleteRow.contains(list_en.get(i).getString("id"))) {
                    list_deleteIndex.add(i);
                }
            }
            for (Map.Entry<String, List<DynamicObject>> entry : map_ent.entrySet()) {
                List<DynamicObject> list_lanRow = entry.getValue();
                for (Integer index : list_deleteIndex) {
                    if (list_lanRow.size()>index){
                        this.getModel().deleteEntryRow("aos_entity" + entityIndex,dyc_entity.indexOf(list_lanRow.get(index)));
                    }

                }
            }
        }
    }
    /** 状态控制 **/
    private void StatusControl() {
        this.getModel().setValue("aos_limit","A");
        this.getView().setVisible(false,"bar_save");
        //根据用户查找相应的人员能够操作的语言
        QFBuilder builder = new QFBuilder();
        builder.add("aos_user","=", RequestContext.get().getCurrUserId());
        DynamicObject dy_userPermiss = QueryServiceHelper.queryOne("aos_mkt_permiss_lan", "aos_row,aos_exchange,aos_lan", builder.toArray());
        List<String> userCopyLanguage = new ArrayList<>();
        if (dy_userPermiss!=null){
            isAddRow = dy_userPermiss.getBoolean("aos_row");
            isExchange = dy_userPermiss.getBoolean("aos_exchange");
            if (!Cux_Common_Utl.IsNull(dy_userPermiss.get("aos_lan"))) {
                //用户能够copy的语言
                for (String lan : dy_userPermiss.getString("aos_lan").split(",")) {
                    if (!Cux_Common_Utl.IsNull(lan)) {
                        userCopyLanguage.add(lan);
                    }
                }
            }
        }
        if (!isExchange)
            this.getView().setVisible(false,"aos_exchange");
        if (!isAddRow){
            List<String> list_Key = new ArrayList<>(12); //增删行标识
            for (int i = 1;i<7;i++){
                list_Key.add("aos_addrow"+i);
                list_Key.add("aos_deleterow"+i);

            }
            this.getView().setVisible(false,list_Key.toArray(new String[0]));
        }
        this.getPageCache().put(KEY_USER,SerializationUtils.toJsonString(userCopyLanguage));
    }
    /** 翻译 **/
    private void translate(Map<String,Object> paraments) throws InterruptedException, DeepLException {
        //源语言
        String source = (String) paraments.get("source");
        String sourceLan;
        if (source.equals("UK") || source.equals("US") || source.equals("CA")||source.equals("EN"))
            sourceLan = "EN";
        else
            sourceLan = map_transLan.get(source);
        //需要翻译的语言
        List<String> list_lans = (List<String>) paraments.get("terminal");
        if (list_lans.size()==0)
            return;
        //图片页
        List<String> img = (List<String>) paraments.get("img");
        //存储翻译的内容
        List<String> list_value = new ArrayList<>();
        //存储翻译内容的位置
        List<String> list_valueIndex = new ArrayList<>();
        Map<String,Map<String,List<DynamicObject>>> map_imgEntity = new HashMap<>();

        for (String entityIndex : img) {
            EntryGrid entryGrid = this.getControl("aos_entity" + entityIndex);
            int[] selectRows = entryGrid.getSelectRows();
            List<String> list_translateRow = new ArrayList<>(selectRows.length); //翻译行主键
            boolean wholeTranslate = false;
            if (selectRows.length == 0)
                wholeTranslate = true;

            DynamicObjectCollection dyc_entity = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + entityIndex);
            //记录需要翻译的行主键
            if (!wholeTranslate){
                for (int row : selectRows) {
                    if (dyc_entity.get(row).getString("aos_lan"+entityIndex).equals(source)){
                        list_translateRow.add(dyc_entity.get(row).getString("id"));
                    }
                }
            }
            //不为全翻，但是又没勾选对应的语言行，默认还是为全翻
            if (!wholeTranslate && list_translateRow.size()==0)
                wholeTranslate = true;

            //根据语言分组
            Map<String, List<DynamicObject>> map_ent = dyc_entity.stream()
                    .sorted((dy1, dy2) -> {
                        int seq1 = dy1.getInt("seq");
                        int seq2 = dy2.getInt("seq");
                        if (seq1 > seq2)
                            return 1;
                        else return -1;
                    })
                    .collect(Collectors.groupingBy(dy -> dy.getString("aos_lan" + entityIndex)));
            map_imgEntity.put(entityIndex,map_ent);
            //翻译源
            List<DynamicObject> list_sourceLan = map_ent.get(source);
            for (int i = 0; i < list_sourceLan.size(); i++) {
                DynamicObject dy = list_sourceLan.get(i);
                //不为全翻，且不在勾选的行里面
                if (!wholeTranslate && !list_translateRow.contains(dy.getString("id")) )
                    continue;
                String value = dy.getString("aos_value"+entityIndex);
                if (!Cux_Common_Utl.IsNull(value)){
                    list_value.add(value);
                    list_valueIndex.add(entityIndex+"-"+i+"-1");
                }
                //作图备注不翻译
                if (dy.getInt("pid")!=0){
                    value = dy.getString("aos_content" + entityIndex);
                    if (!Cux_Common_Utl.IsNull(value)){
                        list_value.add(value);
                        list_valueIndex.add(entityIndex+"-"+i+"-2");
                    }
                }
            }
        }
        //无翻译内容
        if (list_value.size()==0)
            return;
        for (String lan : list_lans) {
            List<TextResult> textResults = translator.translateText(list_value, sourceLan, map_transLan.get(lan));
            //翻译结果的下标
            int resultIndex = 0;
            for (String entityIndex : img) {
                Map<String, List<DynamicObject>> map_ent = map_imgEntity.get(entityIndex);
                List<DynamicObject> list_otehrLan = map_ent.get(lan);
                for (int i = 0; i < list_otehrLan.size(); i++) {
                    DynamicObject dy = list_otehrLan.get(i);
                    if (list_valueIndex.contains(entityIndex+"-"+i+"-1")){
                        dy.set("aos_value" + entityIndex,textResults.get(resultIndex).getText());
                        resultIndex++;
                    }
                    if (list_valueIndex.contains(entityIndex+"-"+i+"-2")){
                        dy.set("aos_content" + entityIndex,textResults.get(resultIndex).getText());
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
    private void imgExchange(Object sourceImage,Object endImage){
        DynamicObjectCollection dyc_source = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + sourceImage);
        DynamicObjectCollection dyc_sourceClone = (DynamicObjectCollection) dyc_source.clone();
        DynamicObjectCollection dyc_end = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity" + endImage);
        DynamicObjectCollection dyc_endClone = (DynamicObjectCollection) dyc_end.clone();
        dyc_source.clear();
        dyc_end.clear();
        for (DynamicObject dy : dyc_endClone) {
            DynamicObject dy_new = dyc_source.addNew();
            dy_new.set("id",dy.get("id"));
            dy_new.set("pid",dy.get("pid"));
            dy_new.set("aos_language"+sourceImage,dy.get("aos_language"+endImage));
            dy_new.set("aos_head"+sourceImage,dy.get("aos_head"+endImage));
            dy_new.set("aos_value"+sourceImage,dy.get("aos_value"+endImage));
            dy_new.set("aos_title"+sourceImage,dy.get("aos_title"+endImage));
            dy_new.set("aos_content"+sourceImage,dy.get("aos_content"+endImage));
            dy_new.set("aos_lan"+sourceImage,dy.get("aos_lan"+endImage));
        }
        for (DynamicObject dy : dyc_sourceClone) {
            DynamicObject dy_new = dyc_end.addNew();
            dy_new.set("id",dy.get("id"));
            dy_new.set("pid",dy.get("pid"));
            dy_new.set("aos_language"+endImage,dy.get("aos_language"+sourceImage));
            dy_new.set("aos_head"+endImage,dy.get("aos_head"+sourceImage));
            dy_new.set("aos_value"+endImage,dy.get("aos_value"+sourceImage));
            dy_new.set("aos_title"+endImage,dy.get("aos_title"+sourceImage));
            dy_new.set("aos_content"+endImage,dy.get("aos_content"+sourceImage));
            dy_new.set("aos_lan"+endImage,dy.get("aos_lan"+sourceImage));
        }
        Object imgSource = this.getModel().getValue("aos_picture" + sourceImage);
        Object imgEnd = this.getModel().getValue("aos_picture" + endImage);
        this.getModel().setValue("aos_picture"+sourceImage,imgEnd);
        this.getModel().setValue("aos_picture"+endImage,imgSource);
        this.getView().updateView("aos_entity" + sourceImage);
        this.getView().updateView("aos_entity" + endImage);
        TreeEntryGrid treeEntryGrid = this.getControl("aos_entity" + sourceImage);
        treeEntryGrid.setCollapse(false);
        treeEntryGrid = this.getControl("aos_entity" + endImage);
        treeEntryGrid.setCollapse(false);
    }
    /** 如果标题行下没有一行，则添加副标题行  **/
    private void addRow(){
        for (int i=1;i<7;i++){
            DynamicObjectCollection dyc = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entity"+i);
            for (int index = 0; index < dyc.size(); index++) {
                DynamicObject dy_row = dyc.get(index);
                if (dy_row.getInt("pid")==0) {
                    String id = dy_row.getString("id");
                    if (this.getPageCache().get(id)==null) {
                        this.getPageCache().put(KEY_INSERT,dy_row.getString("aos_language"+i));
                        this.getModel().insertEntryRow("aos_entity"+i,index);
                    }
                }
            }
        }
    }
}
