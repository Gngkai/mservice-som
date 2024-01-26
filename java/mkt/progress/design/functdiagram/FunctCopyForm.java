package mkt.progress.design.functdiagram;

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
 * @version copy
 */
public class FunctCopyForm extends AbstractFormPlugin {
    public final static String AOS_MKT_FUNCTREQ = "aos_mkt_functreq";
    public final static String USERLAN = "userLan";
    public final static String BTNOK = "btnok";
    public final static String AOS_AADD_MODEL = "aos_aadd_model";
    public final static int SEVEN = 7;

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
        // 功能图文案
        if (AOS_MKT_FUNCTREQ.equals(entityId)) {
            // 语言类
            TransferTreeNode languageNode = new TransferTreeNode("language", "语言", true);
            // 展开子节点
            languageNode.setIsOpened(false);
            FormShowParameter formShowParameter = this.getView().getFormShowParameter();
            List<String> userCopyLanguage = new ArrayList<>();
            if (formShowParameter.getCustomParam(USERLAN) != null) {
                userCopyLanguage = formShowParameter.getCustomParam("userLan");
            }
            for (String id : userCopyLanguage) {
                // 节点 ID
                TransferTreeNode childNode = new TransferTreeNode("lan/" + id,
                    // text，节点显示内容
                    id,
                    // disabled，该节点是否允许选中
                    false);
                languageNode.addChild(childNode);
            }
            data.add(languageNode);
            // 图片类
            TransferTreeNode imgNode = new TransferTreeNode("img", "图片", true);
            imgNode.setIsOpened(false);
            for (int i = 1; i < SEVEN; i++) {
                String id = String.valueOf(i);
                // 节点 ID
                TransferTreeNode childNode = new TransferTreeNode("img/" + id,
                    // text，节点显示内容
                    "图片" + i,
                    // disabled，该节点是否允许选中
                    false);
                imgNode.addChild(childNode);
            }
            data.add(imgNode);
            trans.setTransferListData(data, null);
        }
        // 高级A+
        else if (AOS_AADD_MODEL.equals(entityId)) {
            // 图片类
            TransferTreeNode imgNode = new TransferTreeNode("tab", "页签", true);
            imgNode.setIsOpened(false);
            DynamicObjectCollection tabEntRows =
                this.getView().getParentView().getModel().getDataEntity(true).getDynamicObjectCollection("aos_ent_tab");
            for (int i = 0; i < tabEntRows.size(); i++) {
                DynamicObject dy = tabEntRows.get(i);
                String text;
                if (FndGlobal.IsNotNull(dy.get("aos_tab"))) {
                    text = dy.getString("aos_tab") + " ( 页签" + (i + 1) + " )";
                } else {
                    text = "页签" + (i + 1);
                }

                String id = String.valueOf(i);
                // 节点 ID
                TransferTreeNode childNode = new TransferTreeNode("tab/" + id,
                    // text，节点显示内容
                    text,
                    // disabled，该节点是否允许选中
                    false);
                imgNode.addChild(childNode);
            }
            data.add(imgNode);
            trans.setTransferListData(data, null);
            // 语言类
            TransferTreeNode languageNode = new TransferTreeNode("language", "语言", true);
            // 展开子节点
            languageNode.setIsOpened(false);
            FormShowParameter formShowParameter = this.getView().getFormShowParameter();
            List<String> userCopyLanguage = new ArrayList<>();
            if (formShowParameter.getCustomParam(USERLAN) != null) {
                userCopyLanguage = formShowParameter.getCustomParam("userLan");
            }
            for (String id : userCopyLanguage) {
                // 节点 ID
                TransferTreeNode childNode = new TransferTreeNode("lan/" + id,
                    // text，节点显示内容
                    id,
                    // disabled，该节点是否允许选中
                    false);
                languageNode.addChild(childNode);
            }
            data.add(languageNode);
        }
    }

    private void init() {
        ComboEdit comboEdit = this.getControl("aos_no");
        List<ComboItem> data = new ArrayList<>();
        String entityId = this.getView().getParentView().getEntityId();
        if (AOS_MKT_FUNCTREQ.equals(entityId)) {
            DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_functreq", "aos_segment3", null);
            for (DynamicObject dy : dyc) {
                if (!Cux_Common_Utl.IsNull(dy.get("aos_segment3"))) {
                    data.add(
                        new ComboItem(new LocaleString(dy.getString("aos_segment3")), dy.getString("aos_segment3")));
                }
            }
        }
        // 高级A+
        else if (AOS_AADD_MODEL.equals(entityId)) {
            Object aosProductno = getView().getParentView().getModel().getValue("aos_productno");
            QFBuilder builder = new QFBuilder("aos_productno", "!=", aosProductno);
            DynamicObjectCollection dyc =
                QueryServiceHelper.query("aos_aadd_model", "aos_productno", builder.toArray());
            for (DynamicObject dy : dyc) {
                if (!Cux_Common_Utl.IsNull(dy.get("aos_productno"))) {
                    data.add(
                        new ComboItem(new LocaleString(dy.getString("aos_productno")), dy.getString("aos_productno")));
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
        Control source = (Control)evt.getSource();
        String key = source.getKey();
        if (BTNOK.equals(key)) {
            String aosNo = (String)this.getModel().getValue("aos_no");
            Map<String, Object> mapReturn = new HashMap<>(16);
            mapReturn.put("no", aosNo);
            TransferContainer transferContainerTreeStyle = this.getControl("aos_tran");
            // 用户操作穿梭框后可获取选中的 ID 数组
            List<Object> selectedIdsTreeStyle = transferContainerTreeStyle.getSelectedData();
            // 显示风格为树形的穿梭框选中数据格式
            Map<String, List<String>> mapData = new HashMap<>(16);
            if (selectedIdsTreeStyle != null) {
                for (Object tree : selectedIdsTreeStyle) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>)tree;
                    String id = (String)map.get("id");
                    String[] split = id.split("/");
                    List<String> listData = mapData.computeIfAbsent(split[0], k -> new ArrayList<>());
                    listData.add(split[1]);
                }
            }
            mapReturn.put("data", mapData);
            this.getView().returnDataToParent(mapReturn);
            this.getView().close();
        }
    }
}
