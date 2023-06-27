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
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.progress.ProgressUtil;

public class aos_mkt_funcreq_bill extends AbstractBillPlugIn
		implements RowClickEventListener, ItemClickListener, CellClickListener {
	@Override
	public void afterBindData(EventObject e) {
		super.afterBindData(e);
		long currUserId = RequestContext.get().getCurrUserId();
		Boolean userDapartment = ProgressUtil.JudgeSaleUser(currUserId, ProgressUtil.Dapartment.Mkt_Design.getNumber());
		if (userDapartment) {
			this.getModel().setValue("aos_dapart","design");
		}
		else
			this.getModel().setValue("aos_dapart","");
	}

	public void registerListener(EventObject e) {
		super.registerListener(e);
		// 给单据体加监听
		EntryGrid entryGrid = this.getControl("aos_entryentity");
		entryGrid.addCellClickListener(this); // 单元格点击
		// 给工具栏加监听事件
		this.addItemClickListeners("aos_toolbarap");
		this.addItemClickListeners("aos_importpic"); // 导入图片
	}
	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		if (Control.equals("aos_importpic"))
			aos_importpic();
		else if (Control.equals("aos_deportpic")){
			aos_deportpic();
		}
	}
	/** 图片删除 **/
	private void aos_deportpic(){
		EntryGrid aos_entryentity = this.getControl("aos_entryentity");
		int[] selectRows = aos_entryentity.getSelectRows();
		if (selectRows.length==0){
			this.getView().showMessage("请选择删除行");
		}
		else {
			this.getModel().setValue("aos_picturefield","",selectRows[0]);
		}
	}
	/** 图片导入 **/
	private void aos_importpic() {
		FormShowParameter parameter = new FormShowParameter();
		parameter.setFormId("aos_picmiddle");
		parameter.getOpenStyle().setShowType(ShowType.Modal);
		parameter.setCloseCallBack(new CloseCallBack(this, "aos_confirm"));
		this.getView().showForm(parameter);

	}

	public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
		String actionId = closedCallBackEvent.getActionId();
		if (StringUtils.equals(actionId, "aos_confirm")) {
			int CurrentRowIndex = this.getView().getModel().getEntryCurrentRowIndex("aos_entryentity");
			Object picture = closedCallBackEvent.getReturnData();
			this.getModel().setValue("aos_picturefield", picture, CurrentRowIndex);
		}
	}

	/** 新建事件 **/
	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		InitDefualt();
		InitEntity();
	}

	/** 初始化事件 **/
	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		InitEntity();
		InitPic();
	}

	/** 新建设置默认值 **/
	private void InitDefualt() {
		long CurrentUserId = UserServiceHelper.getCurrentUserId();
		this.getModel().setValue("aos_editor", CurrentUserId);
	}

	/** 对于单据体 **/
	private void InitEntity() {
		DynamicObjectCollection aos_entryentityS = this.getModel().getEntryEntity("aos_entryentity");
		int size = aos_entryentityS.size();
		if (size < 7)
			this.getModel().batchCreateNewEntryRow("aos_entryentity", 7 - size);
		for (int i = 1; i <= 7; i++)
			this.getModel().setValue("aos_seq", i, i - 1);
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

	@Override
	public void cellClick(CellClickEvent arg0) {

	}

	@Override
	public void cellDoubleClick(CellClickEvent arg0) {

	}


}