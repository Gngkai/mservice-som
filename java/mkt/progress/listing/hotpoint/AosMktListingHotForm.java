package mkt.progress.listing.hotpoint;

import com.alibaba.fastjson.JSONArray;
import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.control.Control;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.util.EventObject;
import java.util.Map;

import static mkt.progress.listing.hotpoint.AosMktListingHotUtil.*;

/**
 * @author aosom
 * @since 2024/2/22 11:24
 * @version 爆品质量打分-动态表单插件
 */
public class AosMktListingHotForm extends AbstractFormPlugin {

    public final static String BTNOK = "btnok";
    public final static String DES = "DES";
    public final static String VED = "VED";
    public final static String DOC = "DOC";
    public final static String YES = "是";

    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        Map<String, Object> map = this.getView().getFormShowParameter().getCustomParam("params");
        Object aosType = map.get("aos_type");
        if (DES.equals(aosType)) {
            // 设计类型仅显示设计条目
            this.getView().setVisible(true, "aos_groupdes");
            this.getView().setVisible(false, "aos_groupved");
            this.getView().setVisible(false, "aos_groupdoc");
            for (String key : DOCGROUP) {
                this.getModel().setValue(key, null, 0);
            }
        } else if (VED.equals(aosType)) {
            // 视频类型仅显示视频条目
            this.getView().setVisible(false, "aos_groupdes");
            this.getView().setVisible(true, "aos_groupved");
            this.getView().setVisible(false, "aos_groupdoc");
            for (String key : VEDGROUP) {
                this.getModel().setValue(key, null, 0);
            }
        } else if (DOC.equals(aosType)) {
            // 文案类型仅显示文案条目
            this.getView().setVisible(false, "aos_groupdes");
            this.getView().setVisible(false, "aos_groupved");
            this.getView().setVisible(true, "aos_groupdoc");
            for (String key : DOCGROUP) {
                this.getModel().setValue(key, null, 0);
            }
        }
    }

    @Override
    public void click(EventObject evt) {
        super.click(evt);
        Control source = (Control)evt.getSource();
        String button = source.getKey();
        if (BTNOK.equals(button)) {
            btnOk();
        }
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        this.addClickListeners(BTNOK);
    }

    /**
     * 确定
     */
    private void btnOk() {
        // 校验是否全部填写
        Map<String, Object> map = this.getView().getFormShowParameter().getCustomParam("params");
        Object aosType = map.get("aos_type");
        DynamicObjectCollection entityS = this.getModel().getEntryEntity("aos_entryentity");
        FndMsg.debug(entityS.size());
        for (DynamicObject entity : entityS) {

            String aosPromot = entity.getString("aos_promot");
            String aosContext = entity.getString("aos_context");
            if (YES.equals(aosPromot) && FndGlobal.IsNull(aosContext)) {
                this.getView().showErrorNotification("是否需优化为是时,优化内容必填!");
                return;
            }

            if (DES.equals(aosType)) {
                for (String key : DESGROUP) {
                    if (FndGlobal.IsNull(entity.get(key))) {
                        // 若打分明细内有字段未打分，则提交时报错"打分明细未填写!"
                        this.getView().showErrorNotification("打分明细未填写!");
                        return;
                    }
                }
            }
            if (DOC.equals(aosType)) {
                for (String key : DOCGROUP) {
                    if (FndGlobal.IsNull(entity.get(key))) {
                        // 若打分明细内有字段未打分，则提交时报错"打分明细未填写!"
                        this.getView().showErrorNotification("打分明细未填写!");
                        return;
                    }
                }
            }
            if (VED.equals(aosType)) {
                for (String key : VEDGROUP) {
                    if (FndGlobal.IsNull(entity.get(key))) {
                        // 若打分明细内有字段未打分，则提交时报错"打分明细未填写!"
                        this.getView().showErrorNotification("打分明细未填写!");
                        return;
                    }
                }
            }
        }
        JSONArray ids = (JSONArray)map.get("ids");
        for (Object id : ids) {
            DynamicObject aosMktHotPoint = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_hot_point");
            DynamicObject entry = aosMktHotPoint.getDynamicObjectCollection("aos_entryentity").get(0);
            if (DES.equals(aosType)) {
                for (String key : DESGROUP) {
                    entry.set(key, entityS.get(0).get(key));
                }
            }
            if (DOC.equals(aosType)) {
                for (String key : DOCGROUP) {
                    entry.set(key, entityS.get(0).get(key));
                }
            }
            if (VED.equals(aosType)) {
                for (String key : VEDGROUP) {
                    entry.set(key, entityS.get(0).get(key));
                }
            }
            entry.set("aos_promot", entityS.get(0).get("aos_promot"));
            entry.set("aos_context", entityS.get(0).get("aos_context"));
            entry.set("aos_picture", entityS.get(0).get("aos_picture"));
            SaveServiceHelper.save(new DynamicObject[] {aosMktHotPoint});
        }
        this.getView().close();
    }

}
