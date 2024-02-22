package mkt.progress.listing.hotpoint;

import common.CommonMktListing;
import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndWebHook;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.util.Date;
import java.util.EventObject;

import static mkt.progress.listing.hotpoint.AosMktListingHotUtil.*;

/**
 * @author aosom
 * @since 2024/2/4 8:12
 * @version 爆品打分-表单插件
 */
public class AosMktListingHotBill extends AbstractBillPlugIn {
    public final static String SAVE = "save";
    public final static String YES = "是";
    public final static String DES = "DES";
    public final static String VED = "VED";
    public final static String DOC = "DOC";
    public final static String AOS_SUBMIT = "aos_submit";
    public final static String CONFIRMSOON = "待确认";
    public final static String SECOND = "二次确认";
    public final static String LISTING = "优化中";
    public final static String END = "结束";

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给工具栏加监听事件
        this.addItemClickListeners("tbmain");
        // 提交
        this.addItemClickListeners("aos_submit");
    }

    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        statusControl();
        initItemPic();
    }

    /**
     * 刷新物料图片信息
     */
    private void initItemPic() {
        String itemNumber = ((DynamicObject)this.getModel().getValue("aos_itemid")).getString("number");
        this.getModel().setValue("aos_url", CommonMktListing.GetItemPicture(itemNumber));
        this.getModel().setValue("aos_skupic", "https://clss.s3.amazonaws.com/" + itemNumber + ".jpg");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        try {
            if (AOS_SUBMIT.equals(control)) {
                aosSubmitButton(this.getModel().getDataEntity(true));
            }
        } catch (FndError fndError) {
            fndError.show(getView());
        } catch (Exception ex) {
            ex.printStackTrace();
            FndError.showex(getView(), ex, FndWebHook.urlMms);
        }

    }

    /**
     * 提交按钮
     */
    private void aosSubmitButton(DynamicObject hotDyn) throws FndError {
        // 提前做保存操作
        this.getView().invokeOperation("save");
        this.getView().invokeOperation("refresh");
        // 提交校验
        saveControl();
        // 获取当前状态并分类
        String aosStatus = hotDyn.getString("aos_status");
        if (CONFIRMSOON.equals(aosStatus)) {
            confirmSoonSubmit(hotDyn);
        } else if (SECOND.equals(aosStatus)) {
            secondSubmit(hotDyn);
        }
        // 保存对应的调整
        this.getView().invokeOperation("refresh");
        this.getView().showSuccessNotification("提交成功!");
    }

    /**
     * 二次确认状态下提交
     *
     * @param hotDyn 爆品打分单据对象
     */
    private void secondSubmit(DynamicObject hotDyn) {
        // 将状态调整为结束
        hotDyn.set("aos_status", "结束");
        hotDyn.set("aos_enddate", new Date());
        SaveServiceHelper.save(new DynamicObject[] {hotDyn});
        // 同步爆品质量打分
        syncListingManager(hotDyn);
    }

    /**
     * 待确认提交
     *
     * @param hotDyn 爆品打分单据对象
     */
    private void confirmSoonSubmit(DynamicObject hotDyn) throws FndError {
        String aosType = hotDyn.getString("aos_type");
        // 修改对应【listing数字资产管理】-【打分明细表】
        syncListingManager(hotDyn);
        hotDyn.set("aos_status", "结束");
        hotDyn.set("aos_user", Cux_Common_Utl.SYSTEM);
        DynamicObject aosItemid = hotDyn.getDynamicObject("aos_itemid");
        // 明细中有是否需优化=“是”的SKU,触发生成优化流程 将状态调整为优化中
        if (DES.equals(aosType)) {
            AosMktListingHotUtil.createDesign(aosItemid, hotDyn);
        } else if (VED.equals(aosType)) {
            AosMktListingHotUtil.createPhoto(aosItemid, hotDyn);
        } else if (DOC.equals(aosType)) {
            AosMktListingHotUtil.createDoc(aosItemid, hotDyn);
        }
        hotDyn.set("aos_confirmdate", new Date());
        SaveServiceHelper.save(new DynamicObject[] {hotDyn});
    }

    /**
     * 修改对应【listing数字资产管理】-【打分明细表】
     * 
     * @param hotDyn 爆品打分单据对象
     * @throws FndError 异常返回
     */
    private void syncListingManager(DynamicObject hotDyn) throws FndError {
        FndError fndError = new FndError();
        String aosType = hotDyn.getString("aos_type");
        DynamicObject aosItemid = hotDyn.getDynamicObject("aos_itemid");
        DynamicObjectCollection entityS = hotDyn.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject entity : entityS) {
            if (DES.equals(aosType)) {
                for (String key : DESGROUP) {
                    if (FndGlobal.IsNull(entity.get(key))) {
                        // 若打分明细内有字段未打分，则提交时报错"打分明细未填写!"
                        fndError.add("打分明细未填写!");
                        throw fndError;
                    }
                }
            }
            if (DOC.equals(aosType)) {
                for (String key : DOCGROUP) {
                    if (FndGlobal.IsNull(entity.get(key))) {
                        // 若打分明细内有字段未打分，则提交时报错"打分明细未填写!"
                        fndError.add("打分明细未填写!");
                        throw fndError;
                    }
                }
            }
            if (VED.equals(aosType)) {
                for (String key : VEDGROUP) {
                    if (FndGlobal.IsNull(entity.get(key))) {
                        // 若打分明细内有字段未打分，则提交时报错"打分明细未填写!"
                        fndError.add("打分明细未填写!");
                        throw fndError;
                    }
                }
            }
        }

        for (DynamicObject entity : entityS) {
            DynamicObject aosOrgid = entity.getDynamicObject("aos_orgid");
            DynamicObject aosMktListingMana = BusinessDataServiceHelper.loadSingle("aos_mkt_listing_mana",
                new QFilter("aos_orgid", QCP.equals, aosOrgid.getPkValue().toString())
                    .and("aos_itemid", QCP.equals, aosItemid.getPkValue().toString()).toArray());

            if (FndGlobal.IsNull(aosMktListingMana)) {
                // 若打分明细内有字段未打分，则提交时报错"打分明细未填写!"
                fndError.add("数字资产管理未找到!");
                throw fndError;
            }

            DynamicObjectCollection pointS = aosMktListingMana.getDynamicObjectCollection("aos_pointentity");
            for (DynamicObject point : pointS) {
                String[] group = null;
                if ("DES".equals(aosType)) {
                    group = DESGROUP;
                } else if ("VED".equals(aosType)) {
                    group = VEDGROUP;
                } else if ("DOC".equals(aosType)) {
                    group = DOCGROUP;
                }
                if (group != null) {
                    for (String key : group) {
                        point.set(key, entity.get(key));
                    }
                }
            }
            SaveServiceHelper.save(new DynamicObject[] {aosMktListingMana});
        }
    }

    /**
     * 提交校验
     */
    private void saveControl() {
        DynamicObjectCollection entityS = this.getModel().getEntryEntity("aos_entryentity");
        for (DynamicObject entity : entityS) {
            String aosPromot = entity.getString("aos_promot");
            String aosContext = entity.getString("aos_context");
            if (YES.equals(aosPromot) && FndGlobal.IsNull(aosContext)) {
                throw new FndError("是否需优化为是时,优化内容必填!");
            }
        }
    }

    @Override
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        e.setCheckDataChange(false);
    }
    /**
     * 界面控制
     */
    private void statusControl() {
        // 类型控制
        Object aosType = this.getModel().getValue("aos_type");
        if (DES.equals(aosType)) {
            // 设计类型仅显示设计条目
            this.getView().setVisible(true, "aos_groupdes");
            this.getView().setVisible(false, "aos_groupved");
            this.getView().setVisible(false, "aos_groupdoc");
        } else if (VED.equals(aosType)) {
            // 视频类型仅显示视频条目
            this.getView().setVisible(false, "aos_groupdes");
            this.getView().setVisible(true, "aos_groupved");
            this.getView().setVisible(false, "aos_groupdoc");
        } else if (DOC.equals(aosType)) {
            // 文案类型仅显示文案条目
            this.getView().setVisible(false, "aos_groupdes");
            this.getView().setVisible(false, "aos_groupved");
            this.getView().setVisible(true, "aos_groupdoc");
        }
        // 状态控制
        Object aosStatus = this.getModel().getValue("aos_status");
        // 提交按钮
        this.getView().setVisible(CONFIRMSOON.equals(aosStatus) || SECOND.equals(aosStatus), "aos_submit");
        // 结束全锁
        if (END.equals(aosStatus)) {
            this.getView().setEnable(false, "contentpanelflex");
        }
    }

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs args) {
        super.afterDoOperation(args);
        FormOperate formOperate = (FormOperate)args.getSource();
        String operatation = formOperate.getOperateKey();
        if (SAVE.equals(operatation)) {
            args.getOperationResult().setShowMessage(false);
        }
    }

}
