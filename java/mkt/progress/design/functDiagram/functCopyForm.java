package mkt.progress.design.functDiagram;

import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.form.FormShowParameter;
import kd.bos.form.control.Control;
import kd.bos.form.control.TransferContainer;
import kd.bos.form.field.ComboEdit;
import kd.bos.form.field.ComboItem;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.form.transfer.TransferNode;
import kd.bos.form.transfer.TransferTreeNode;
import kd.bos.servicehelper.QueryServiceHelper;
import common.sal.util.QFBuilder;

import java.util.*;

/**
 * @author create by gk
 * @date 2023/3/31 16:19
 * @action  功能图copy界面
 */
@SuppressWarnings("unchecked")
public class functCopyForm extends AbstractFormPlugin {
    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        init();
    }
    @Override
    public void afterCreateNewData(EventObject e) {
        String entityId = this.getView().getParentView().getEntityId();
        // 显示风格为树形(树形模式)
        TransferContainer trans = this.getControl("aos_tran");
        List<TransferNode> data = new ArrayList<>();
        //功能图文案
        if (entityId.equals("aos_mkt_functreq")){
            //语言类
            TransferTreeNode languageNode = new TransferTreeNode("language","语言",true);
            languageNode.setIsOpened(false); // 展开子节点
            FormShowParameter formShowParameter = this.getView().getFormShowParameter();
            List<String> userCopyLanguage = new ArrayList<>();
            if (formShowParameter.getCustomParam("userLan")!=null) {
                userCopyLanguage =  formShowParameter.getCustomParam("userLan");
            }
            for (int n = 0; n < userCopyLanguage.size(); n++) {
                String id =userCopyLanguage.get(n);
                TransferTreeNode childNode = new TransferTreeNode(
                        "lan/"+id,        // 节点 ID
                        userCopyLanguage.get(n),    // text，节点显示内容
                        false         // disabled，该节点是否允许选中
                );
                languageNode.addChild(childNode);
            }
            data.add(languageNode);

            //图片类
            TransferTreeNode imgNode = new TransferTreeNode("img","图片",true);
            imgNode.setIsOpened(false);
            for (int i = 1; i < 7; i++) {
                String id = String.valueOf(i);
                TransferTreeNode childNode = new TransferTreeNode(
                        "img/"+id,        // 节点 ID
                        "图片"+i,    // text，节点显示内容
                        false         // disabled，该节点是否允许选中
                );
                imgNode.addChild(childNode);
            }
            data.add(imgNode);
            trans.setTransferListData(data,null);
        }
        //高级A+
        else if (entityId.equals("aos_aadd_model")){
            //图片类
            TransferTreeNode imgNode = new TransferTreeNode("tab","页签",true);
            imgNode.setIsOpened(false);
            DynamicObjectCollection tabEntRows = this.getView().getParentView().getModel().getDataEntity(true).getDynamicObjectCollection("aos_ent_tab");
            for (int i = 0; i < tabEntRows.size(); i++) {
                DynamicObject dy = tabEntRows.get(i);
                String text;
                if (FndGlobal.IsNotNull(dy.get("aos_tab"))){
                    text = dy.getString("aos_tab")+" ( 页签"+(i+1)+" )";
                }
                else {
                    text ="页签"+(i+1);
                }

                String id = String.valueOf(i);
                TransferTreeNode childNode = new TransferTreeNode(
                        "tab/"+id,        // 节点 ID
                        text,    // text，节点显示内容
                        false         // disabled，该节点是否允许选中
                );
                imgNode.addChild(childNode);
            }

            data.add(imgNode);
            trans.setTransferListData(data,null);

            //语言类
            TransferTreeNode languageNode = new TransferTreeNode("language","语言",true);
            languageNode.setIsOpened(false); // 展开子节点
            FormShowParameter formShowParameter = this.getView().getFormShowParameter();
            List<String> userCopyLanguage = new ArrayList<>();
            if (formShowParameter.getCustomParam("userLan")!=null) {
                userCopyLanguage =  formShowParameter.getCustomParam("userLan");
            }
            for (int n = 0; n < userCopyLanguage.size(); n++) {
                String id =userCopyLanguage.get(n);
                TransferTreeNode childNode = new TransferTreeNode(
                        "lan/"+id,        // 节点 ID
                        userCopyLanguage.get(n),    // text，节点显示内容
                        false         // disabled，该节点是否允许选中
                );
                languageNode.addChild(childNode);
            }
            data.add(languageNode);
        }
    }

    private void init(){
        ComboEdit comboEdit = this.getControl("aos_no");
        List<ComboItem> data = new ArrayList<>();
        String entityId = this.getView().getParentView().getEntityId();
        if (entityId.equals("aos_mkt_functreq")){
            DynamicObjectCollection  dyc = QueryServiceHelper.query("aos_mkt_functreq", "aos_segment3", null);
            for (DynamicObject dy : dyc) {
                if (!Cux_Common_Utl.IsNull(dy.get("aos_segment3"))){
                    data.add(new ComboItem(new LocaleString(dy.getString("aos_segment3")), dy.getString("aos_segment3")));
                }
            }
        }
        //高级A+
        else if (entityId.equals("aos_aadd_model")){
            Object aos_productno = getView().getParentView().getModel().getValue("aos_productno");
            QFBuilder builder = new QFBuilder("aos_productno","!=",aos_productno);
            DynamicObjectCollection  dyc = QueryServiceHelper.query("aos_aadd_model","aos_productno",builder.toArray());
            for (DynamicObject dy : dyc) {
                if (!Cux_Common_Utl.IsNull(dy.get("aos_productno"))){
                    data.add(new ComboItem(new LocaleString(dy.getString("aos_productno")), dy.getString("aos_productno")));
                }
            }

       }
        comboEdit.setComboItems(data);
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        this.addClickListeners("btnok");
    }

    @Override
    public void click(EventObject evt) {
        super.click(evt);
        Control source = (Control) evt.getSource();
        String key = source.getKey();
        if (key.equals("btnok")){
            String aos_no = (String) this.getModel().getValue("aos_no");
            Map<String,Object> map_return = new HashMap<>();
            map_return.put("no",aos_no);
            TransferContainer transferContainerTreeStyle = this.getControl("aos_tran");
            // 用户操作穿梭框后可获取选中的 ID 数组
            List<Object> selectedIdsTreeStyle = transferContainerTreeStyle.getSelectedData();
            // 显示风格为树形的穿梭框选中数据格式
            Map<String,List<String>> map_data = new HashMap<>();
            if (selectedIdsTreeStyle!=null){
                for (Object tree : selectedIdsTreeStyle) {
                    Map<String, Object> map = (Map<String, Object>) tree;
                    String id = (String) map.get("id");
                    String[] split = id.split("/");
                    List<String> list_data = map_data.computeIfAbsent(split[0], k -> new ArrayList<>());
                    list_data.add(split[1]);
                }
            }
            map_return.put("data",map_data);
            this.getView().returnDataToParent(map_return);
            this.getView().close();
        }
    }
}
