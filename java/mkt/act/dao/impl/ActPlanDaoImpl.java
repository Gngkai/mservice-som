package mkt.act.dao.impl;

import common.sal.util.DateUtil;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import mkt.act.dao.ActPlanDao;

import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author: lch
 * @createDate: 2022/11/9
 * @description:
 * @updateRemark:
 */
public class ActPlanDaoImpl implements ActPlanDao {

    private static final String ENTITY_NAME = "aos_act_select_plan";

    @Override
    public DynamicObjectCollection listNormalActivityItems(Long aos_orgid, Long aos_platformid, String aos_shopnum, String[] actType, Date start, Date end) {
        QFilter filter = new QFilter("aos_actstatus", QCP.not_equals, "C");// 不为手工关闭
        filter.and(new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"));// 行上活动类型为正常
        filter.and(new QFilter("aos_nationality", QCP.equals, aos_orgid));// 国别
        if (aos_platformid != null) {
            filter.and(new QFilter("aos_channel", QCP.equals, aos_platformid));// 渠道
        }

        if (aos_shopnum != null) {
            filter.and(new QFilter("aos_shop.number", QCP.equals, aos_shopnum));// 店铺
        }

        if (actType != null) {
            filter.and(new QFilter("aos_acttype.number", QCP.in, actType));// 活动类型
        }

        if (start != null) {
            filter.and(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, end));// 活动类型
        }

        if (end != null) {
            filter.and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.large_equals, start));// 活动类型
        }

        String selectFields = "aos_sal_actplanentity.aos_itemnum aos_itemid";
        return QueryServiceHelper.query(ENTITY_NAME, selectFields, filter.toArray());
    }

    @Override
    public DynamicObjectCollection listNormalActivityCollection(Long aos_orgid, Long aos_platformid, Long aos_shopid, String[] actType, Date date) {
        QFilter filter = new QFilter("aos_nationality", QCP.equals, aos_orgid);// 已提报
        filter.and(new QFilter("aos_actstatus", QCP.equals, "B"));// 行上活动类型为正常
        filter.and(new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"));// 行上活动类型为正常
        if (aos_platformid != null) {
            filter.and(new QFilter("aos_channel", QCP.equals, aos_platformid));// 渠道
        }

        if (aos_shopid != null) {
            filter.and(new QFilter("aos_shop", QCP.equals, aos_shopid));// 店铺
        }

        if (date != null) {
            filter.and(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.like, DateUtil.formatDay(date) + "%"));// 活动类型
        }

        if (actType != null) {
            filter.and(new QFilter("aos_acttype.number", QCP.in, actType));// 活动类型
        }
        String selectFields = "billno,aos_channel," +
                "aos_shop aos_shopid," +
                "aos_acttype," +
                "aos_sal_actplanentity.aos_itemnum aos_itemid," +
                "aos_sal_actplanentity.aos_category_stat1 aos_category_stat1," +
                "aos_sal_actplanentity.aos_category_stat2 aos_category_stat2," +
                "aos_sal_actplanentity.aos_l_startdate aos_l_startdate," +
                "aos_sal_actplanentity.aos_enddate aos_enddate," +
                "aos_sal_actplanentity.aos_postid aos_postid";
        return QueryServiceHelper.query(ENTITY_NAME, selectFields, filter.toArray());
    }

    @Override
    public Set<String> listShopItemByStartDate(Long aos_orgid, String[] actType, Date date) {
        QFilter filter = new QFilter("aos_nationality", QCP.equals, aos_orgid);// 已提报
        filter.and(new QFilter("aos_actstatus", QCP.equals, "B"));// 行上活动类型为正常
        filter.and(new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"));// 行上活动类型为正常

        if (date != null) {
            filter.and(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, DateUtil.formatDay(date)));
            filter.and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.large_equals, DateUtil.formatDay(date)));
        }

        if (actType != null) {
            filter.and(new QFilter("aos_acttype.number", QCP.in, actType));// 活动类型
        }

        String selectFields = "aos_shop," +
                "aos_sal_actplanentity.aos_itemnum aos_itemid";
        DynamicObjectCollection list = QueryServiceHelper.query(ENTITY_NAME, selectFields, filter.toArray());

        return list.stream()
                .map(obj -> obj.getLong("aos_shop") + ":" + obj.getLong("aos_itemid"))
                .collect(Collectors.toSet());
    }

}
