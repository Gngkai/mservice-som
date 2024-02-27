package mkt.act.rule.service.impl;

import com.alibaba.fastjson.JSONObject;

import common.sal.EventRuleCommon;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author lch
 * @since 2022/11/9
 */
public class ActPlanServiceImpl implements ActPlanService {

    private static final ActPlanDao ACTPLANDAO = new ActPlanDaoImpl();
    private static final LocalMemoryCache CACHE;
    static {
        CACHE = localMemoryCache();
    }

    private static LocalMemoryCache localMemoryCache() {
        return CacheUtil.getLocalMemoryCache("mkt_region", "actplan_cache", 5000, 1200);
    }

    @Override
    public Set<Long> getNormalActItemIdSet(Long aosOrgid, Long aosPlatformid, String aosShopnum, String actType,
        int beforeAfterDays) {
        String[] actTypeArr = null;
        if (actType != null) {
            actTypeArr = actType.split(",");
        }
        Calendar calendar = DateUtil.todayCalendar();
        calendar.add(Calendar.DAY_OF_MONTH, -beforeAfterDays);
        Date start = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, beforeAfterDays * 2);
        Date end = calendar.getTime();
        DynamicObjectCollection dynamicObjects =
            ACTPLANDAO.listNormalActivityItems(aosOrgid, aosPlatformid, aosShopnum, actTypeArr, start, end);
        return dynamicObjects.stream().map(obj -> obj.getLong("aos_itemid")).collect(Collectors.toSet());
    }

    @Override
    public boolean containsItem(Long aosOrgid, Long aosItemid, Long aosPlatformid, String aosShopnum, String actType,
        int beforeAfterDays) {
        String cacheKey =
            "mkt:actplan:" + aosOrgid + ":" + aosPlatformid + ":" + aosShopnum + ":" + actType + ":" + beforeAfterDays;

        @SuppressWarnings("unchecked")
        Set<Long> normalActItemCache = (Set<Long>)CACHE.get(cacheKey);
        // 2.没获取到从数据库中获取
        if (normalActItemCache == null) {
            synchronized (AvailableDaysServiceImpl.class) {
                if (CACHE.get(cacheKey) == null) {
                    // 2.1 获取线上店铺销量
                    normalActItemCache =
                        getNormalActItemIdSet(aosOrgid, aosPlatformid, aosShopnum, actType, beforeAfterDays);
                    // 2.2 存入缓存
                    CACHE.put(cacheKey, normalActItemCache);
                }
            }
        }
        return normalActItemCache != null && normalActItemCache.contains(aosItemid);
    }

    @Override
    public Map<String, JSONObject> listActivityShopItem(Long aosOrgid, Long aosPlatformid, Long aosShopid,
        String[] actType, Date date) {
        DynamicObjectCollection dynamicObjects =
            ACTPLANDAO.listNormalActivityCollection(aosOrgid, aosPlatformid, aosShopid, actType, date);
        Map<String, JSONObject> shopItemMap = new HashMap<>(16);
        for (DynamicObject dynamicObject : dynamicObjects) {
            long aosShopid1 = dynamicObject.getLong("aos_shopid");
            long aosItemid1 = dynamicObject.getLong("aos_itemid");
            Object aosChannel = dynamicObject.get("aos_channel");
            Object aosActtype = dynamicObject.get("aos_acttype");
            Object aosCategoryStat1 = dynamicObject.get("aos_category_stat1");
            Object aosCategoryStat2 = dynamicObject.get("aos_category_stat2");
            Object aoslStartdate = dynamicObject.get("aos_l_startdate");
            Object aosEnddate = dynamicObject.get("aos_enddate");
            Object aosPostid = dynamicObject.get("aos_postid");
            Object billno = dynamicObject.get("billno");

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("aos_channel", aosChannel);
            jsonObject.put("aos_acttype", aosActtype);
            jsonObject.put("aos_category_stat1", aosCategoryStat1);
            jsonObject.put("aos_category_stat2", aosCategoryStat2);
            jsonObject.put("aos_l_startdate", aoslStartdate);
            jsonObject.put("aos_enddate", aosEnddate);
            jsonObject.put("aos_postid", aosPostid);
            jsonObject.put("aos_itemid", aosItemid1);
            jsonObject.put("aos_shopid", aosShopid1);
            jsonObject.put("billno", billno);

            String key = aosShopid1 + ":" + aosItemid1;
            shopItemMap.put(key, jsonObject);
        }
        return shopItemMap;
    }

    @Override
    public Set<String> listShopItemByStartDate(Long aosOrgid, String[] actType, Date date) {
        return ACTPLANDAO.listShopItemByStartDate(aosOrgid, actType, date);
    }

    @Override
    public void updateActInfo(DynamicObject dataEntity) {
        EventRuleCommon.updateActInfo(dataEntity);
    }
}
