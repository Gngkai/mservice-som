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
 * @author lch
 * @since 2022/11/11
 * @version Coupon取消提醒定时任务
 */
public class CouponCancelWarnTask extends AbstractTask {
    private static final ActPlanService ACTPLANSERVICE = new ActPlanServiceImpl();
    private static final CouponCancelDao COUPONCANCELDAO = new CouponCancelDaoImpl();
    private static final ItemCacheService ITEMCACHESERVICE = new ItemCacheServiceImpl();
    private static final CountryDao COUNTRYDAO = new CountryDaoImpl();
    private static final AosomLog LOGGER = AosomLog.init("CouponCancelWarnTask");

    static {
        LOGGER.setService("AOS.MMS");
        LOGGER.setDomain("MMS.ACT");
    }

    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        try {
            List<Long> orgIdList = COUNTRYDAO.listSaleCountry();
            for (Long aosOrgid : orgIdList) {
                newCouponCancelBill(aosOrgid);
            }
        } catch (RuntimeException e) {
            LOGGER.error("生成Coupon取消提醒单报错", e);
        }
    }

    private void newCouponCancelBill(Long aosOrgid) {
        Calendar calendar = DateUtil.todayCalendar();
        Date today = calendar.getTime();
        // 时间推后一天
        calendar.add(Calendar.DAY_OF_MONTH, +1);
        Set<String> couponExistSet =
            ACTPLANSERVICE.listShopItemByStartDate(aosOrgid, new String[] {"Coupon"}, calendar.getTime());
        String[] actTypeArr =
            new String[] {"LD", "7DD", "Tracker/Vipon", "Prime Coupon", "Prime Exclusive Discounts", "Best Deals"};
        Map<String, JSONObject> activityShopItem =
            ACTPLANSERVICE.listActivityShopItem(aosOrgid, null, null, actTypeArr, calendar.getTime());
        Map<Long, List<JSONObject>> groupMap = new HashMap<>(16);
        // 物料国别信息 是否爆品
        DynamicObjectCollection bdMaterialS =
            QueryServiceHelper.query("bd_material", "id,aos_contryentry.aos_is_saleout aos_is_saleout",
                new QFilter("aos_contryentry.aos_nationality", QCP.equals, aosOrgid).toArray());
        Map<String, Boolean> saleOutMap = new HashMap<>(16);
        for (DynamicObject bdMaterial : bdMaterialS) {
            saleOutMap.put(bdMaterial.getString("id"), bdMaterial.getBoolean("aos_is_saleout"));
        }

        for (String key : activityShopItem.keySet()) {
            // 1.如果不存在Coupon 则不需提醒
            if (!couponExistSet.contains(key)) {
                continue;
            }
            // 2.存在Coupon 则分组
            Long aosItemid = Long.parseLong(key.split(":")[1]);
            long itemSalGroupId = ITEMCACHESERVICE.getItemSalGroupId(aosOrgid, aosItemid);
            List<JSONObject> jsonObjects = groupMap.computeIfAbsent(itemSalGroupId, k -> new ArrayList<>());
            jsonObjects.add(activityShopItem.get(key));
        }

        // 赋值
        for (Long aosGroupid : groupMap.keySet()) {
            DynamicObject object = COUPONCANCELDAO.newDynamicObject();
            object.set("aos_orgid", aosOrgid);
            object.set("aos_groupid", aosGroupid);
            object.set("aos_makeby", UserServiceHelper.getCurrentUserId());
            object.set("aos_makedate", today);
            DynamicObjectCollection entryentity = object.getDynamicObjectCollection("entryentity");
            List<JSONObject> jsonObjects = groupMap.get(aosGroupid);
            if (jsonObjects.isEmpty()) {
                continue;
            }
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
            SaveServiceHelper.saveOperate(entityNumber, new DynamicObject[] {object}, OperateOption.create());
        }
    }
}
