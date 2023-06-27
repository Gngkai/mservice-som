package mkt.act.dao.impl;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import mkt.act.dao.CouponCancelDao;

/**
 * @author: lch
 * @createDate: 2022/11/11
 * @description: Coupon取消Dao
 * @updateRemark:
 */
public class CouponCancelDaoImpl implements CouponCancelDao {

    private static final String ENTITY_NAME = "aos_couponcancel";

    @Override
    public DynamicObject newDynamicObject() {
        DynamicObject newDynamicObject = BusinessDataServiceHelper.newDynamicObject(ENTITY_NAME);
        newDynamicObject.set("billstatus", "A");
        return newDynamicObject;
    }
}
