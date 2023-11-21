package mkt.act.rule.service.impl;

import com.alibaba.fastjson.JSONObject;
import common.EventRuleCommon;
import common.sal.sys.basedata.dao.ItemDao;
import common.sal.sys.basedata.dao.ShopDao;
import common.sal.sys.basedata.dao.impl.ItemDaoImpl;
import common.sal.sys.basedata.dao.impl.ShopImpl;
import common.sal.sys.sync.service.AvailableDaysService;
import common.sal.sys.sync.service.ItemCacheService;
import common.sal.sys.sync.service.impl.AvailableDaysServiceImpl;
import common.sal.sys.sync.service.impl.ItemCacheServiceImpl;
import common.sal.util.CacheUtil;
import common.sal.util.DateUtil;
import kd.bos.cache.LocalMemoryCache;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import mkt.act.dao.ActPlanDao;
import mkt.act.dao.impl.ActPlanDaoImpl;
import mkt.act.rule.service.ActPlanService;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author: lch
 * @createDate: 2022/11/9
 * @description:
 * @updateRemark:
 */
public class ActPlanServiceImpl implements ActPlanService {

    private static final ActPlanDao actPlanDao = new ActPlanDaoImpl();
    private static final ItemCacheService itemCacheService = new ItemCacheServiceImpl();
    private static final AvailableDaysService availableDaysService = new AvailableDaysServiceImpl();
    // 缓存
    private static final LocalMemoryCache cache;
    static {
        cache = localMemoryCache();
    }

    private static LocalMemoryCache localMemoryCache() {
        return CacheUtil.getLocalMemoryCache("mkt_region", "actplan_cache", 5000, 1200);
    }



    @Override
    public Set<Long> getNormalActItemIdSet(Long aos_orgid, Long aos_platformid, String aos_shopnum, String actType, int beforeAfterDays) {
        String[] actTypeArr = null;
        if (actType != null) {
            actTypeArr = actType.split(",");
        }
        Calendar calendar = DateUtil.todayCalendar();
        calendar.add(Calendar.DAY_OF_MONTH, -beforeAfterDays);
        Date start = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, beforeAfterDays*2);
        Date end = calendar.getTime();
        DynamicObjectCollection dynamicObjects = actPlanDao.listNormalActivityItems(aos_orgid, aos_platformid, aos_shopnum, actTypeArr, start, end);
        return dynamicObjects
                .stream()
                .map(obj -> obj.getLong("aos_itemid"))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean containsItem(Long aos_orgid, Long aos_itemid, Long aos_platformid, String aos_shopnum, String actType, int beforeAfterDays) {
        String cacheKey = "mkt:actplan:" +
                aos_orgid + ":" +
                aos_platformid + ":" +
                aos_shopnum + ":" +
                actType + ":" +
                beforeAfterDays ;

        @SuppressWarnings("unchecked")
        Set<Long> normalActItemCache = (Set<Long>) cache.get(cacheKey);
        // 2.没获取到从数据库中获取
        if (normalActItemCache == null) {
            synchronized (AvailableDaysServiceImpl.class) {
                if (cache.get(cacheKey) == null) {
                    // 2.1 获取线上店铺销量
                    normalActItemCache = getNormalActItemIdSet(aos_orgid, aos_platformid, aos_shopnum, actType, beforeAfterDays);
                    // 2.2 存入缓存
                    cache.put(cacheKey, normalActItemCache);
                }
            }
        }
        return normalActItemCache != null && normalActItemCache.contains(aos_itemid);
    }

    @Override
    public Map<String, JSONObject> listActivityShopItem(Long aos_orgid, Long aos_platformid, Long aos_shopid, String[] actType, Date date) {
        DynamicObjectCollection dynamicObjects = actPlanDao.listNormalActivityCollection(aos_orgid, aos_platformid, aos_shopid, actType, date);
        Map<String, JSONObject> shopItemMap = new HashMap<>();
        for (DynamicObject dynamicObject : dynamicObjects) {
            long aos_shopid1 = dynamicObject.getLong("aos_shopid");
            long aos_itemid1 = dynamicObject.getLong("aos_itemid");

            Object aos_channel = dynamicObject.get("aos_channel");
            Object aos_acttype = dynamicObject.get("aos_acttype");
            Object aos_category_stat1 = dynamicObject.get("aos_category_stat1");
            Object aos_category_stat2 = dynamicObject.get("aos_category_stat2");
            Object aos_l_startdate = dynamicObject.get("aos_l_startdate");
            Object aos_enddate = dynamicObject.get("aos_enddate");
            Object aos_postid = dynamicObject.get("aos_postid");
            Object billno = dynamicObject.get("billno");

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("aos_channel", aos_channel);
            jsonObject.put("aos_acttype", aos_acttype);
            jsonObject.put("aos_category_stat1", aos_category_stat1);
            jsonObject.put("aos_category_stat2", aos_category_stat2);
            jsonObject.put("aos_l_startdate", aos_l_startdate);
            jsonObject.put("aos_enddate", aos_enddate);
            jsonObject.put("aos_postid", aos_postid);
            jsonObject.put("aos_itemid", aos_itemid1);
            jsonObject.put("aos_shopid", aos_shopid1);
            jsonObject.put("billno", billno);

            String key = aos_shopid1 + ":" + aos_itemid1;
            shopItemMap.put(key, jsonObject);
        }
        return shopItemMap;
    }

    @Override
    public Set<String> listShopItemByStartDate(Long aos_orgid, String[] actType, Date date) {
        return actPlanDao.listShopItemByStartDate(aos_orgid, actType, date);
    }

    @Override
    public void updateActInfo(DynamicObject dataEntity) {
        EventRuleCommon.updateActInfo(dataEntity);
    }
}
