package mkt.progress.design;

import java.time.LocalDate;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import common.fnd.FndLog;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import mkt.progress.listing.AosMktListingMinBill;

/** 功能图翻译台账生成 设计需求表 定时任务 **/
public class aos_mkt_design_init extends AbstractTask {

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		do_operate(param);
	}

	public static void do_operate(Map<String, Object> param) {
		Set<String> OrignItem = GenerateOrignItem();
		Set<String> NoPhotoItem = GenerateNoPhotoItem();
		Set<String> EndDesignItem = GenerateEndDesignItem();
		QFilter filter = new QFilter("aos_eng", QCP.equals, "N");
		QFilter[] filters = new QFilter[] { filter };
		DynamicObjectCollection aos_mkt_funcsumdataS = QueryServiceHelper.query("aos_mkt_funcsumdata",
				"id,aos_itemid,aos_sourceid,aos_orgid", filters);

		FndLog log = FndLog.init("功能图翻译台账生成", LocalDate.now().toString());
		for (DynamicObject aos_mkt_funcsumdata : aos_mkt_funcsumdataS) {
			String aos_itemid = aos_mkt_funcsumdata.getString("aos_itemid");
			if (OrignItem.contains(aos_itemid) || NoPhotoItem.contains(aos_itemid)
					|| EndDesignItem.contains(aos_itemid)) {
				// 货号维度生成设计需求表
				boolean exists = QueryServiceHelper.exists("aos_mkt_listing_min", new QFilter[]{new QFilter("id", "=", aos_mkt_funcsumdata.get("aos_sourceid"))});
				if (!exists){
					log.add(aos_mkt_funcsumdata.getString("id")+"  源单不存在");
					continue;
				}

				DynamicObject aos_mkt_listing_min = BusinessDataServiceHelper.loadSingle(aos_mkt_funcsumdata.get("aos_sourceid"), "aos_mkt_listing_min");

				String aos_frombill = AosMktListingMinBill.generateDesign(aos_mkt_listing_min,aos_itemid,aos_mkt_funcsumdata.getString("aos_orgid"));

				// 回写功能图翻译台账数据表
				DynamicObject aos_mkt_funcsumdataSingle = BusinessDataServiceHelper
						.loadSingle(aos_mkt_funcsumdata.get("id"), "aos_mkt_funcsumdata");
				aos_mkt_funcsumdataSingle.set("aos_eng", "Y");
				aos_mkt_funcsumdataSingle.set("aos_frombill", aos_frombill);
				aos_mkt_funcsumdataSingle.set("aos_triggerdate", new Date());
				OperationServiceHelper.executeOperate("save", "aos_mkt_funcsumdata",
						new DynamicObject[] { aos_mkt_funcsumdataSingle }, OperateOption.create());
			} else {
				continue;
			}
		}
		log.finnalSave();
	}

	/** 设计需求表 期初已完成货号 **/
	private static Set<String> GenerateOrignItem() {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_designitem", "aos_itemid", null);
		return list.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
	}

	/** 不拍照任务清单货号 **/
	private static Set<String> GenerateNoPhotoItem() {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_nophotolist", "aos_itemid", null);
		return list.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
	}

	/** 已结束的设计需求表 **/
	private static Set<String> GenerateEndDesignItem() {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_designreq",
				"aos_entryentity.aos_itemid aos_itemid", new QFilter[] { new QFilter("aos_status", QCP.equals, "结束") });
		return list.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
	}

}