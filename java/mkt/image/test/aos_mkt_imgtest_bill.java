package mkt.image.test;

import java.util.EventObject;

import common.fnd.FndError;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.operate.FormOperate;

public class aos_mkt_imgtest_bill extends AbstractBillPlugIn implements ItemClickListener {
	
	/** 新建单据时触发 **/
	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		this.getModel().setValue("aos_text", "遨森电子商务股份有限公司");
		this.getView().getModel().setValue("aos_text", "遨森电子商务股份有限公司");
	}
	
	/** 打开已存在单据时触发 **/
	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
	}
	
	/** 在关闭前触发  **/
	public void beforeClosed(BeforeClosedEvent e) {
		super.beforeClosed(e);
		e.setCheckDataChange(false);
		//e.setCheckDataChange(false); //若此参数设置false 若界面有调整 则不跳出提示
	}
	
	/** 值改变事件 **/
	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		if ("aos_text".equals(name)) {
			this.getView().showTipNotification("演示文本值改变请注意！");
		}
	}
	
	/** 在操作之前触发  **/
	public void beforeDoOperation(BeforeDoOperationEventArgs args) {
		FormOperate formOperate = (FormOperate) args.getSource();
		String Operatation = formOperate.getOperateKey();
		
		try {
			if ("save".equals(Operatation) ) 
				SaveControl(); // 保存校验
			else if ("refresh".equals(Operatation) ) 
				SaveControl(); // 保存校验
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
			args.setCancel(true);
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
			args.setCancel(true);
		} 
		
	}
	
	private void SaveControl() throws FndError {
		int ErrorCount = 0;
		String ErrorMessage = "";
		Object aos_text = this.getModel().getValue("aos_text");
		
		
		if (!"遨森电子商务股份有限公司222".contains(aos_text+"")) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "演示文本信息错误2！");
		}
		
		if (!"遨森电子商务股份有限公司".equals(aos_text)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "演示文本信息错误！");
		}
		//遨森电子商务股份有限公
		
		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}
		
	}

	/** 在操作之后触发  **/
	public void afterDoOperation(AfterDoOperationEventArgs args) {
		/*FormOperate formOperate = (FormOperate) args.getSource();
		String Operatation = formOperate.getOperateKey();*/
	}
	
	/** 添加监听 **/
	@Override
	public void registerListener(EventObject e) {
		super.registerListener(e);
		this.addItemClickListeners("aos_toolbarap");// 给工具栏加监听事件
		this.addItemClickListeners("aos_submit"); // 提交
		this.addItemClickListeners("aos_confirm"); // 确认
		this.addItemClickListeners("aos_back"); // 退回
	}
	
	
}