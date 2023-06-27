package mkt.act.dao;

import java.util.Set;

/**
 * @author: lch
 * @createDate: 2022/11/14
 * @description: 活动SKU异常提醒表
 * @updateRemark:
 */
public interface ActAbnormalDao {

    /**
     *
     * @param aos_orgnum 国别编码
     * @return 活动计划和选品编码+物料idSet
     */
    Set<String> listAlreadyDeal(String aos_orgnum, int days);
}
