package mkt.act.dao.impl;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import mkt.act.dao.ActShopPriceDao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author create by gk
 * @date 2022/11/24 8:51
 * @action
 */
public class ActShopPriceImpl implements ActShopPriceDao {
    public static final String ENTITY_NAME = "aos_sal_act_shopprice";
    @Override
    public Map<String, BigDecimal> QueryOrgChannelShopPrice(Object orgid, Object channelid, Object shopid) {
        QFilter filter_org = new QFilter("aos_orgid","=",orgid);
        QFilter filter_channel = new QFilter("aos_channel","=",channelid);
        QFilter filter_shop = new QFilter("aos_shop","=",shopid);
        QFilter [] qfs = new QFilter[]{filter_org,filter_shop,filter_channel};
        return QueryServiceHelper.query(ENTITY_NAME, "aos_item,aos_price", qfs)
                .stream()
                .collect(Collectors.toMap(
                        dy -> dy.getString("aos_item"),
                        dy -> dy.getBigDecimal("aos_price"),
                        (key1, key2) -> key1));
    }
    @Override
    public Map<String, BigDecimal> QueryOrgChannelShopItemPrice(Object orgid, Object channelid, Object shopid, List<String> list_itemid) {
        QFilter filter_org = new QFilter("aos_orgid","=",orgid);
        QFilter filter_channel = new QFilter("aos_channel","=",channelid);
        QFilter filter_shop = new QFilter("aos_shop","=",shopid);
        QFilter filter_item = new QFilter("aos_item",QFilter.in,list_itemid);
        QFilter [] qfs = new QFilter[]{filter_org,filter_shop,filter_channel,filter_item};
        return QueryServiceHelper.query(ENTITY_NAME, "aos_item,aos_price", qfs)
                .stream()
                .collect(Collectors.toMap(
                        dy -> dy.getString("aos_item"),
                        dy -> dy.getBigDecimal("aos_price"),
                        (key1, key2) -> key1));
    }
    @Override
    public Map<String, BigDecimal> QueryOrgChannelShopItemNumberPrice(Object orgid, Object channelid, Object shopid, List<String> list_itemNumber) {
        QFilter filter_org = new QFilter("aos_orgid","=",orgid);
        QFilter filter_channel = new QFilter("aos_channel","=",channelid);
        QFilter filter_shop = new QFilter("aos_shop","=",shopid);
        QFilter filter_item = new QFilter("aos_item.number",QFilter.in,list_itemNumber);
        QFilter [] qfs = new QFilter[]{filter_org,filter_shop,filter_channel,filter_item};
        return QueryServiceHelper.query(ENTITY_NAME, "aos_item,aos_price", qfs)
                .stream()
                .collect(Collectors.toMap(
                        dy -> dy.getString("aos_item"),
                        dy -> dy.getBigDecimal("aos_price"),
                        (key1, key2) -> key1));
    }
    @Override
    public BigDecimal QueryOrgChannelShopItemPrice(Object orgid, Object channelid, Object shopid, Object itemid) {
        QFilter filter_org = new QFilter("aos_orgid","=",orgid);
        QFilter filter_channel = new QFilter("aos_channel","=",channelid);
        QFilter filter_shop = new QFilter("aos_shop","=",shopid);
        QFilter filter_item = new QFilter("aos_item","=",itemid);
        QFilter [] qfs = new QFilter[]{filter_org,filter_shop,filter_channel,filter_item};
        BigDecimal big_price = BigDecimal.ZERO;
        DynamicObject dy = QueryServiceHelper.queryOne(ENTITY_NAME, "aos_price", qfs);
        if (dy!=null) {
            big_price = dy.getBigDecimal("aos_price");
        }
        return big_price;
    }
}
