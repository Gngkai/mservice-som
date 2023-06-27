package mkt.act.dao.impl;

import common.sal.util.DateUtil;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import mkt.act.dao.ActAbnormalDao;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * @author: lch
 * @createDate: 2022/11/14
 * @description:
 * @updateRemark:
 */
public class ActAbnormalDaoImpl implements ActAbnormalDao {

    private static final String ENTITY_NAME = "aos_act_skureminder";
    @Override
    public Set<String> listAlreadyDeal(String aos_orgnum, int days) {
        Calendar calendar = DateUtil.todayCalendar();
        QFilter filter = new QFilter("aos_makedate", QCP.large_equals, DateUtil.formatDay(calendar.getTime()));
        filter.and(new QFilter("aos_orgid.number", QCP.equals, aos_orgnum));
        filter.and(new QFilter("billstatus", QCP.not_equals, "A"));//不为新建状态
        filter.and(new QFilter("aos_entryentity.aos_abnormal", QCP.not_equals, ""));//措施不为空

        DynamicObjectCollection alreadyDealItem = QueryServiceHelper.query(ENTITY_NAME, "aos_entryentity.aos_itemid aos_itemid,aos_entryentity.aos_originbillno aos_originbillno", filter.toArray());

        Set<String> alreadyDealItems = new HashSet<>();
        for (DynamicObject object : alreadyDealItem) {
            String aos_originbillno = object.getString("aos_originbillno");
            String aos_itemid = object.getString("aos_itemid");
            alreadyDealItems.add(aos_originbillno + ":" + aos_itemid);
        }
        return alreadyDealItems;
    }
}
