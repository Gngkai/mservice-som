package mkt.act.dao;

import kd.bos.dataentity.entity.DynamicObjectCollection;

import java.util.Date;
import java.util.Set;

/**
 * @author: lch
 * @createDate: 2022/11/9
 * @description: 活动计划和选品查询接口
 * @updateRemark:
 */
public interface ActPlanDao {

    /**
     *
     * @param aos_orgid 国别id
     * @param aos_platformid 渠道id
     * @param aos_shopnum 店铺编码
     * @param actType 活动类型数组
     * @param start 开始日期
     * @param end 结束日期
     * @return 开始日期-结束日期中正常的活动类型物料id
     */
    DynamicObjectCollection listNormalActivityItems(Long aos_orgid, Long aos_platformid, String aos_shopnum, String[] actType, Date start, Date end);


    DynamicObjectCollection listNormalActivityCollection(Long aos_orgid, Long aos_platformid, Long aos_shopid, String[] actType, Date date);


    /**
     *
     * @param aos_orgid 国别id
     * @param actType 活动类型数组
     * @param date 开始日期
     * @return 开始日期-结束日期中正常的活动类型物料id
     */
    Set<String> listShopItemByStartDate(Long aos_orgid, String[] actType, Date date);
}
