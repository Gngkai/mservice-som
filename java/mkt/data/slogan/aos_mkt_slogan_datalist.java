package mkt.data.slogan;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import common.sal.util.SalUtil;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.servicehelper.BusinessDataServiceHelper;

public class aos_mkt_slogan_datalist extends AbstractListPlugin {
	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		try {
			if (StringUtils.equals("aos_opt", evt.getItemKey()))
				aos_opt();// 优化
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	private void aos_opt() throws FndError {
		FndError fndError = new FndError();
		List<ListSelectedRow> list = getSelectedRows();
		FndMsg.debug("list.size():" + list.size());
		if (FndGlobal.IsNull(list) || list.size() != 1) {
			fndError.add("请勾选一条数据!");
			throw fndError;
		}
		String id = list.get(0).toString();
		DynamicObject aos_mkt_data_slogan = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_data_slogan");
		Map<String, Object> params = new HashMap<>();
		params.put("TYPE", "OPT");
		params.put("aos_category1", aos_mkt_data_slogan.getString("aos_category1"));
		params.put("aos_category2", aos_mkt_data_slogan.getString("aos_category2"));
		params.put("aos_category3", aos_mkt_data_slogan.getString("aos_category3"));
		params.put("aos_itemnamecn", aos_mkt_data_slogan.getString("aos_itemnamecn"));
		params.put("aos_detail", aos_mkt_data_slogan.getString("aos_detail"));

		FndGlobal.OpenForm(this, "aos_mkt_slogan", params);
	}
}