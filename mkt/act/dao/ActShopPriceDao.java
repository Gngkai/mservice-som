package mkt.act.dao;


import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * @author: gk
 * @createDate: 2022/11/24
 * @description: 活动店铺价格查询
 * @updateRemark:
 */
public interface ActShopPriceDao {
     Map<String, BigDecimal> QueryOrgChannelShopPrice(Object orgid,Object channelid,Object shopid);
     Map<String, BigDecimal> QueryOrgChannelShopItemPrice(Object orgid, Object channelid, Object shopid, List<String> list_itemid);
     Map<String, BigDecimal> QueryOrgChannelShopItemNumberPrice(Object orgid, Object channelid, Object shopid, List<String> list_itemNumber);
     BigDecimal QueryOrgChannelShopItemPrice(Object orgid, Object channelid, Object shopid, Object itemid);
}
