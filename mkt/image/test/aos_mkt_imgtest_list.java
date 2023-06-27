package mkt.image.test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import sal.synciface.common.aos_sal_sync_common;

public class aos_mkt_imgtest_list extends AbstractListPlugin {

	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String control = evt.getItemKey();
		System.out.println("control =" + control);
		if ("aos_batchchange".equals(control))
			aos_batchchange();// 批量修改
		else if ("aos_test".equals(control))
			aos_test();
	}

	private void aos_test() {
		Map<String, Object> params = new HashMap<>();
		
		params.put("p_org_id", "81");
		params.put("p_user_id", UserServiceHelper.getCurrentUserId());
		
		
		aos_sal_sync_common.submit_request(params, "aos_mkt_syncif_clear");
		
	}

	/** 批量修改功能 **/
	private void aos_batchchange() {
		// TODO 批量修改
		List<ListSelectedRow> list = getSelectedRows();// 获取选中行
		// 0

		for (int i = 0; i < list.size(); i++) {
			String fid = list.get(i).toString();
			System.out.println("==================");
			System.out.println("i =" + i);
			System.out.println("fid =" + fid);

			DynamicObject aos_mkt_img_test2 = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_img_test2");
			aos_mkt_img_test2.set("aos_text2", "批量修改" + fid);
			aos_mkt_img_test2.set("aos_big", 10086);

			DynamicObjectCollection aos_entryentityS = aos_mkt_img_test2.getDynamicObjectCollection("aos_entryentity");

			for (DynamicObject aos_entryentity : aos_entryentityS) {
				aos_entryentity.set("aos_textfield", "测试 批量修改单据体行 文本3");
			}

			for (int r = 0; r <= 4; r++) {
				DynamicObject aos_entryentity = aos_entryentityS.addNew();
				aos_entryentity.set("aos_combofield", "A");
				aos_entryentity.set("aos_textfield", "TEETT");
			}

			OperationServiceHelper.executeOperate("save", "aos_mkt_img_test2",
					new DynamicObject[] { aos_mkt_img_test2 }, OperateOption.create());
		}

		this.getView().invokeOperation("refresh");
		this.getView().showSuccessNotification("已成功完成批量修改!");

	}

}
