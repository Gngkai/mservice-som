package mkt.act.task;

import com.alibaba.fastjson.JSONObject;

import common.fnd.AosomLog;
import common.sal.sys.basedata.dao.CountryDao;
import common.sal.sys.basedata.dao.impl.CountryDaoImpl;
import common.sal.sys.sync.service.ItemCacheService;
import common.sal.sys.sync.service.impl.ItemCacheServiceImpl;
import common.sal.util.DateUtil;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.act.dao.CouponCancelDao;
import mkt.act.dao.impl.CouponCancelDaoImpl;
import mkt.act.rule.service.ActPlanService;
import mkt.act.rule.service.impl.ActPlanServiceImpl;

import java.util.*;

/**
 * @author: lch
 * @createDate: 2022/11/11
 * @description: Coupon取消提醒定时任务
 * @updateRemark:
 */
public class CouponCancelWarnTask extends AbstractTask {
	private static AosomLog logger = AosomLog.init("CouponCancelWarnTask");
	static {
		logger.setService("aos.mms");
		logger.setDomain("mms.act");
	}
	private static final ActPlanService actPlanService = new ActPlanServiceImpl();
	private static final CouponCancelDao couponCancelDao = new CouponCancelDaoImpl();
	private static final ItemCacheService itemCacheService = new ItemCacheServiceImpl();
	private static final CountryDao countryDao = new CountryDaoImpl();

	@Override
	public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
		try {
			List<Long> orgIdList = countryDao.listSaleCountry();
			for (Long aos_orgid : orgIdList) {
				newCouponCancelBill(aos_orgid);
			}
		} catch (RuntimeException e) {
			// 1.打印日志
			logger.error("生成Coupon取消提醒单报错");
			// 2.发送邮件
		}
	}

	private void newCouponCancelBill(Long aos_orgid) {

		Calendar calendar = DateUtil.todayCalendar();
		Date today = calendar.getTime();
		// 时间推后一天
		calendar.add(Calendar.DAY_OF_MONTH, +1);
		Set<String> couponExistSet = actPlanService.listShopItemByStartDate(aos_orgid, new String[] { "Coupon" },
				calendar.getTime());
		String[] actTypeArr = new String[] { "LD", "7DD", "Tracker/Vipon", "Prime Coupon", "Prime Exclusive Discounts",
				"Best Deals" };
		Map<String, JSONObject> activityShopItem = actPlanService.listActivityShopItem(aos_orgid, null, null,
				actTypeArr, calendar.getTime());

		Map<Long, List<JSONObject>> groupMap = new HashMap<>();

		// 物料国别信息 是否爆品
		DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material",
				"id,aos_contryentry.aos_is_saleout aos_is_saleout",
				new QFilter("aos_nationality", QCP.equals, aos_orgid).toArray());
		Map<String, Boolean> saleOutMap = new HashMap<>();
		for (DynamicObject bd_material : bd_materialS)
			saleOutMap.put(bd_material.getString("id"), bd_material.getBoolean("aos_is_saleout"));

		for (String key : activityShopItem.keySet()) {
			// 1.如果不存在Coupon 则不需提醒
			if (!couponExistSet.contains(key))
				continue;

			// 2.存在Coupon 则分组
			Long aos_itemid = Long.parseLong(key.split(":")[1]);
			long itemSalGroupId = itemCacheService.getItemSalGroupId(aos_orgid, aos_itemid);
			List<JSONObject> jsonObjects = groupMap.computeIfAbsent(itemSalGroupId, k -> new ArrayList<>());
			jsonObjects.add(activityShopItem.get(key));
		}

		// 赋值
		for (Long aos_groupid : groupMap.keySet()) {
			DynamicObject object = couponCancelDao.newDynamicObject();
			object.set("aos_orgid", aos_orgid);
			object.set("aos_groupid", aos_groupid);
			object.set("aos_makeby", UserServiceHelper.getCurrentUserId());
			object.set("aos_makedate", today);

			DynamicObjectCollection entryentity = object.getDynamicObjectCollection("entryentity");
			List<JSONObject> jsonObjects = groupMap.get(aos_groupid);
			if (jsonObjects.isEmpty())
				continue;

			for (JSONObject jsonObject : jsonObjects) {
				// 单据体对象
				DynamicObject etyObj = entryentity.addNew();
				etyObj.set("aos_platformid", jsonObject.get("aos_channel"));
				etyObj.set("aos_shopid", jsonObject.get("aos_shopid"));
				etyObj.set("aos_itemid", jsonObject.get("aos_itemid"));
				etyObj.set("aos_asin", jsonObject.get("aos_postid"));
				etyObj.set("aos_category1", jsonObject.get("aos_category_stat1"));
				etyObj.set("aos_category2", jsonObject.get("aos_category_stat2"));
				etyObj.set("aos_actbillno", jsonObject.get("billno"));
				etyObj.set("aos_acttypeid", jsonObject.get("aos_acttype"));
				etyObj.set("aos_dealstart", jsonObject.get("aos_l_startdate"));
				etyObj.set("aos_dealend", jsonObject.get("aos_enddate"));
				etyObj.set("aos_is_saleout", saleOutMap.get(jsonObject.getString("aos_itemid")));
			}

			String entityNumber = object.getDynamicObjectType().getName();
			SaveServiceHelper.saveOperate(entityNumber, new DynamicObject[] { object }, OperateOption.create());
		}
	}
}
