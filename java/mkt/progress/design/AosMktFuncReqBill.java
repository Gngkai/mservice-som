package mkt.progress.design;

import java.util.EventObject;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.form.CloseCallBack;
import kd.bos.form.FormShowParameter;
import kd.bos.form.ShowType;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.Image;
import kd.bos.form.control.events.CellClickEvent;
import kd.bos.form.control.events.CellClickListener;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.progress.ProgressUtil;

/**
 * @author aosom
 * @version 功能图需求表-表单插件
 */
public class AosMktFuncReqBill extends AbstractBillPlugIn
    implements RowClickEventListener, ItemClickListener, CellClickListener {
    public final static String AOS_IMPORTPIC = "aos_importpic";
    public final static String AOS_DEPORTPIC = "aos_deportpic";

    public final static String AOS_CONFIRM = "aos_confirm";
    public final static int SEVEN = 7;

    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        long currUserId = RequestContext.get().getCurrUserId();
        Boolean userDapartment = ProgressUtil.JudgeSaleUser(currUserId, ProgressUtil.Dapartment.Mkt_Design.getNumber());
        if (userDapartment) {
            this.getModel().setValue("aos_dapart", "design");
        } else {
            this.getModel().setValue("aos_dapart", "");
        }
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给单据体加监听
        EntryGrid entryGrid = this.getControl("aos_entryentity");
        // 单元格点击
        entryGrid.addCellClickListener(this);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        // 导入图片
        this.addItemClickListeners("aos_importpic");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        String control = evt.getItemKey();
        if (control.equals(AOS_IMPORTPIC)) {
            aosImportpic();
        } else if (control.equals(AOS_DEPORTPIC)) {
            aosDeportpic();
        }
    }

    /** 图片删除 **/
    private void aosDeportpic() {
        EntryGrid aosEntryentity = this.getControl("aos_entryentity");
        int[] selectRows = aosEntryentity.getSelectRows();
        if (selectRows.length == 0) {
            this.getView().showMessage("请选择删除行");
        } else {
            this.getModel().setValue("aos_picturefield", "", selectRows[0]);
        }
    }

    /** 图片导入 **/
    private void aosImportpic() {
        FormShowParameter parameter = new FormShowParameter();
        parameter.setFormId("aos_picmiddle");
        parameter.getOpenStyle().setShowType(ShowType.Modal);
        parameter.setCloseCallBack(new CloseCallBack(this, "aos_confirm"));
        this.getView().showForm(parameter);

    }

    @Override
    public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
        String actionId = closedCallBackEvent.getActionId();
        if (StringUtils.equals(actionId, AOS_CONFIRM)) {
            int currentRowIndex = this.getView().getModel().getEntryCurrentRowIndex("aos_entryentity");
            Object picture = closedCallBackEvent.getReturnData();
            this.getModel().setValue("aos_picturefield", picture, currentRowIndex);
        }
    }

    /** 新建事件 **/
    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        initDefualt();
        initEntity();
    }

    /** 初始化事件 **/
    @Override
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        initEntity();
        initPic();
    }

    /** 新建设置默认值 **/
    private void initDefualt() {
        long currentUserId = UserServiceHelper.getCurrentUserId();
        this.getModel().setValue("aos_editor", currentUserId);
    }

    /** 对于单据体 **/
    private void initEntity() {
        DynamicObjectCollection aosEntryentityS = this.getModel().getEntryEntity("aos_entryentity");
        int size = aosEntryentityS.size();
        if (size < SEVEN) {
            this.getModel().batchCreateNewEntryRow("aos_entryentity", 7 - size);
        }
        for (int i = 1; i <= SEVEN; i++) {
            this.getModel().setValue("aos_seq", i, i - 1);
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

    @Override
    public void cellClick(CellClickEvent arg0) {

    }

    @Override
    public void cellDoubleClick(CellClickEvent arg0) {

    }

}