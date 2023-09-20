package mkt.synciface;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import common.fnd.FndMsg;
import common.sal.impl.ComImpl;
import common.sal.impl.ComImpl2;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import sal.synciface.imp.aos_sal_import_pub;

/** 最低定价结存接口 **/
public class aos_mkt_dayprice_bak extends AbstractTask {
	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		do_operate(param);
	}

	public static void do_operate(Map<String, Object> param) {
		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		Date Today = today.getTime();
		// 删除今日数据
		DeleteServiceHelper.delete("aos_base_przbak", new QFilter[] { new QFilter("aos_date", "=", Today) });
		// JSONArray p_ret_cursor = ComImpl.GetCursorSom(param, "CUXPRZBAK_MMS");
		JSONObject obj = ComImpl2.GetCursorEsb(param, "CUXPRZBAK_MMS", "CUX_MMS_BASIC");
		JSONArray p_ret_cursor = obj.getJSONArray("p_real_model");

		int length = p_ret_cursor.size();
		System.out.println("length =" + length);
		if (length > 0) {
			DynamicObject aos_base_przbak = BusinessDataServiceHelper.newDynamicObject("aos_base_przbak");
			aos_base_przbak.set("aos_date", Today);
			DynamicObjectCollection aos_entryentityS = aos_base_przbak.getDynamicObjectCollection("aos_entryentity");
			for (int i = 0; i < length; i++) {
				FndMsg.debug("i:" + i);
				HashMap<String, Object> RankJson = (HashMap<String, Object>) p_ret_cursor.get(i);
				Object aos_orgid = aos_sal_import_pub.get_import_id(RankJson.get("OU_NAME"), "bd_country");
				Object aos_itemid = aos_sal_import_pub.get_import_id(RankJson.get("MHSKU"), "bd_material");
				DynamicObject aos_entryentity = aos_entryentityS.addNew();
				aos_entryentity.set("aos_orgid", aos_orgid);
				aos_entryentity.set("aos_itemid", aos_itemid);
				aos_entryentity.set("aos_currentprice", RankJson.get("MIN_PRICE"));
			}
			OperationServiceHelper.executeOperate("save", "aos_base_przbak", new DynamicObject[] { aos_base_przbak },
					OperateOption.create());
		}
	}
}