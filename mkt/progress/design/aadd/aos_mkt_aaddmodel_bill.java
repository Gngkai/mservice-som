package mkt.progress.design.aadd;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.form.ClientProperties;
import kd.bos.form.FormShowParameter;
import kd.bos.form.IFormView;
import kd.bos.form.IPageCache;
import kd.bos.form.ShowType;
import kd.bos.form.control.Button;
import kd.bos.form.control.Control;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

public class aos_mkt_aaddmodel_bill extends AbstractBillPlugIn {

	public void registerListener(EventObject e) {
		for (int i = 1; i <= 10; i++) {
			Button aos_button = this.getControl("aos_button" + i);
			aos_button.addClickListener(this);
		}
		this.addItemClickListeners("tbmain");// 给工具栏加监听事件
		this.addItemClickListeners("aos_change");
	}

	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		IPageCache iPageCache = this.getPageCache();
		iPageCache.put("button", "1");
		openInContainer();
	}

	/** 新建事件 **/
	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		IPageCache iPageCache = this.getPageCache();
		iPageCache.put("button", "1");
		openInContainer();
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		if ("aos_change".equals(Control)) {
			aos_change();
		}
	}

	/**
	 * 改名
	 */
	private void aos_change() {
		FndMsg.debug("=====into aos_change=====");
		FndGlobal.OpenForm(this, "aos_aadd_model_show", null);
	}

	@Override
	public void click(EventObject evt) {
		String source = ((Control) evt.getSource()).getKey();
		try {
			if (source.contains("aos_button")) {
				aos_button(source);// 提交
			}
		} catch (FndError FndError) {
			this.getView().showTipNotification(FndError.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}

	}

	public void beforeDoOperation(BeforeDoOperationEventArgs args) {
		FormOperate formOperate = (FormOperate) args.getSource();
		String Operatation = formOperate.getOperateKey();
		if ("save".equals(Operatation)) {
			saveEntity();
		}
	}

	/**
	 * 保存单据体
	 */
	private void saveEntity() {
		String sonPageId = this.getPageCache().get("sonPageId");
		IFormView mainView = this.getView().getView(sonPageId);
		Object fid = this.getModel().getDataEntity().getPkValue();
		String button = this.getPageCache().get("button");
		DynamicObjectCollection aos_entryentityS = mainView.getModel().getEntryEntity("aos_entryentity");
		List<DynamicObject> aosBillDeatilS = new ArrayList<>();
		DeleteServiceHelper.delete("aos_aadd_model_detail",
				new QFilter("aos_sourceid", QCP.equals, fid).and("aos_button", QCP.equals, button).toArray());

		int i = 1;
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			aos_entryentity.set("aos_seq", i);
			i++;
		}

		for (DynamicObject aos_entryentity : aos_entryentityS) {
			DynamicObject aos_aadd_model_detail = BusinessDataServiceHelper.newDynamicObject("aos_aadd_model_detail");
			aos_aadd_model_detail.set("aos_cate1", aos_entryentity.get("aos_cate1"));
			aos_aadd_model_detail.set("aos_cate2", aos_entryentity.get("aos_cate2"));
			aos_aadd_model_detail.set("aos_cn", aos_entryentity.get("aos_cn"));
			aos_aadd_model_detail.set("aos_usca", aos_entryentity.get("aos_usca"));
			aos_aadd_model_detail.set("aos_uk", aos_entryentity.get("aos_uk"));
			aos_aadd_model_detail.set("aos_de", aos_entryentity.get("aos_de"));
			aos_aadd_model_detail.set("aos_fr", aos_entryentity.get("aos_fr"));
			aos_aadd_model_detail.set("aos_it", aos_entryentity.get("aos_it"));
			aos_aadd_model_detail.set("aos_es", aos_entryentity.get("aos_es"));
			aos_aadd_model_detail.set("aos_seq", aos_entryentity.get("aos_seq"));
			aos_aadd_model_detail.set("aos_sourceid", fid);
			aos_aadd_model_detail.set("aos_button", Integer.parseInt(button));
			aosBillDeatilS.add(aos_aadd_model_detail);
		}
		SaveServiceHelper.save(aosBillDeatilS.toArray(new DynamicObject[0]));
	}

	private void aos_button(String source) {
		openInContainer();
		IPageCache iPageCache = this.getPageCache();
		String buttonString = source.replace("aos_button", "");
		iPageCache.put("button", buttonString);
		for (int i = 1; i <= 10; i++) {
			Map<String, Object> map = new HashMap<>();
			if (i == Integer.parseInt(buttonString)) {
				map.put(ClientProperties.BackColor, "#45cdff");
			} else
				map.put(ClientProperties.BackColor, "#ffffff");
			this.getView().updateControlMetadata("aos_button" + i, map);
		}
	}

	/**
	 * 在容器中打开
	 */
	private void openInContainer() {
		for (int i = 1; i <= 10; i++) {
			Map<String, Object> map = new HashMap<>();
			map.put(ClientProperties.Text, new LocaleString((String) this.getModel().getValue("aos_textfield" + i)));
			this.getView().updateControlMetadata("aos_button" + i, map);
		}
		FormShowParameter parameter = new FormShowParameter();
		parameter.setFormId("aos_aadd_model_form");
		parameter.getOpenStyle().setShowType(ShowType.InContainer);
		parameter.getOpenStyle().setTargetKey("aos_flexpanelap1"); // 设置父容器标识
		this.getView().showForm(parameter);
		IPageCache iPageCache = this.getPageCache();
		String sonPageId = parameter.getPageId();
		iPageCache.put("sonPageId", sonPageId);
	}
}