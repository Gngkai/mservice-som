package mkt.act.rule;

import kd.bos.dataentity.entity.DynamicObject;


/**
 * lch
 * 活动策略
 * 2022-06-24
 */
public interface ActStrategy {

    void doOperation(DynamicObject object) throws Exception;

}
