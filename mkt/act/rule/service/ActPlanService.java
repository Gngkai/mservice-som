package mkt.act.rule.service;

import com.alibaba.fastjson.JSONObject;
import kd.bos.dataentity.entity.DynamicObject;

import java.util.Date;
import java.util.Map;
import java.util.Set;

/**
 * @author: lch
 * @createDate: 2022/11/9
 * @description:
 * @updateRemark:
 */
public interface ActPlanService {

    /**
     *
     * @param aos_orgid 国别id
     * @param aos_platformid 渠道id
     * @param aos_shopnum 店铺编码
     * @param actType 活动类型数组
     * @param beforeAfterDays 活动日前后几天
     * @return 开始日期-结束日期中正常的活动类型物料idSet
     */
    Set<Long> getNormalActItemIdSet(Long aos_orgid, Long aos_platformid, String aos_shopnum, String actType, int beforeAfterDays);

    /**
     *
     * @param aos_orgid 国别id
     * @param aos_itemid 物料id
     * @param aos_platformid 渠道id
     * @param aos_shopnum 店铺编码
     * @param actType 活动类型数组
     * @param beforeAfterDays 活动日前后几天
     * @return true : 包含此物料 false : 不包含此物料
     */
    boolean containsItem(Long aos_orgid, Long aos_itemid, Long aos_platformid, String aos_shopnum, String actType, int beforeAfterDays);

    /**
     *
     * @param aos_orgid 国别id 不能为空
     * @param aos_platformid 渠道id 可以为空
     * @param aos_shopid 店铺id 可以为空
     * @param actType 活动类型数组
     * @param date 某一天
     * @return KEY: 店铺id:物料id VALUE:其他字段的值
     */
    Map<String, JSONObject> listActivityShopItem(Long aos_orgid, Long aos_platformid, Long aos_shopid, String[] actType, Date date);

    /**
     *
     * @param aos_orgid 国别id
     * @param actType 活动类型数组
     * @param date 开始日期
     * @return 开始日期-结束日期中正常的活动类型物料id
     */
    Set<String> listShopItemByStartDate(Long aos_orgid, String[] actType, Date date);

    /**
     *
     * @param object 对象
     * @return
     */
    void updateActInfo(DynamicObject object);
}
