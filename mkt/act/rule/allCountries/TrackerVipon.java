package mkt.act.rule.allCountries;

import com.alibaba.nacos.common.utils.Pair;
import common.Cux_Common_Utl;
import common.sal.sys.basedata.dao.SeasonAttrDao;
import common.sal.sys.basedata.dao.impl.SeasonAttrDaoImpl;
import common.sal.util.SalUtil;
import kd.bos.dataentity.FourTuple;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.isc.util.script.feature.tool.date.Quarters;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.isc.iscb.util.misc.Quad;
import kd.isc.iscb.util.misc.Quin;
import kd.isc.iscb.util.misc.Triple;
import mkt.act.dao.ActShopPriceDao;
import mkt.act.dao.impl.ActShopPriceImpl;
import mkt.act.rule.ActInfoCalEdit;
import mkt.act.rule.ActStrategy;
import mkt.act.rule.ActUtil;
import mkt.act.rule.service.ActPlanService;
import mkt.act.rule.service.impl.ActPlanServiceImpl;
import mkt.common.MKTCom;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.TreeBidiMap;
import sal.act.ActShopProfit.aos_sal_act_from;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author create by ?
 * @date 2022/10/7 17:14
 * @action
 */
@SuppressWarnings("unchecked")
public class TrackerVipon implements ActStrategy {
    private static Logger log = Logger.getLogger("TrackerVipon");
//    private static Log log = LogFactory.getLog("TrackerVipon");
    @Override
    public void doOperation(DynamicObject object) throws Exception {
        //获取国别
        Object actEntityId = object.get("id");
        String billno = object.getString("billno");
        String orgId = object.getDynamicObject("aos_nationality").getString("id");
        String orgNumber = object.getDynamicObject("aos_nationality").getString("number");
        String channel = object.getDynamicObject("aos_channel").getString("id");
        String actId = object.getDynamicObject("aos_acttype").getString("id");
        String shopid = object.getDynamicObject("aos_shop").getString("id");
        Date date_actBegin = object.getDate("aos_startdate");
        LocalDate local_s = date_actBegin.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        Date date_actEnd = object.getDate("aos_enddate1");
        LocalDate local_e = date_actEnd.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate local_make = object.getDate("aos_makedate").toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        //获取日志
        DynamicObject aos_sync_log = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
        aos_sync_log.set("aos_type_code", billno+ "    TrackerVipon");
        aos_sync_log.set("aos_groupid", billno);
        aos_sync_log.set("billstatus", "A");
        DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");
        //获取常规品
        Map<String, String> map_conventItem = getConventItem(orgId);
        MKTCom.Put_SyncLog(aos_sync_logS,"常规基础数据获取完成 根据抓客规则获取数据 "+map_conventItem.keySet().size());
        log.info(billno+"  常规基础数据获取完成 根据抓客规则获取数据  "+map_conventItem.keySet().size());
        //获取季节品
        Map<String, String> map_seasonItem = getSeasonItem(orgId,aos_sync_logS);
        MKTCom.Put_SyncLog(aos_sync_logS,"季节基础数据获取完成 根据抓客规则获取数据 "+map_seasonItem.keySet().size());
        log.info(billno+"  季节基础数据获取完成 根据抓客规则获取数据  "+map_seasonItem.keySet().size());
        //获取各品类的分配比例
        Map<String, BigDecimal> map_cateAllocate = ActUtil.queryActCateAllocate(orgId, actId);
        //确定每个品类的分配数量
        Map<String, Integer> map_saleQty = AllocateQty(map_cateAllocate, 100);  //常规品的分配数
        log.info(billno+ "抓客规则获取国别品类分配比例完成");
        try {
            //常规品和季节品的物料ID (之后的过滤以此为基础)
            List<String> list_filterItem = new ArrayList<>();
            for (String itemid : map_conventItem.keySet()) {
                list_filterItem.add(itemid);
            }
            for (String itemid : map_seasonItem.keySet()) {
                list_filterItem.add(itemid);
            }
            //获取物料的主键与编码的对应关系
            BidiMap<String,String> bidiMap_ietmID = mapItemIDToNumber(list_filterItem);

            //获取 15天库存>安全库存，且库存>50  的物料
            Map<String,Integer> map_stock =  FilterItemByStock(orgId,  bidiMap_ietmID,aos_sync_logS);
            list_filterItem.clear();
            for (String key : map_stock.keySet()) {
                list_filterItem.add(bidiMap_ietmID.getKey(key));
            }
            log.info(billno+ "   TrackerVipon抓客规则  物料经过库存过滤：  "+list_filterItem.size());

            //根据活动进行过滤
            list_filterItem = FilterItemByAct(billno,orgId,local_s.toString(),local_e.toString(),local_make,list_filterItem,aos_sync_logS,bidiMap_ietmID);
            log.info(billno+ "   TrackerVipon抓客规则  物料经过活动过滤：  "+list_filterItem.size());

            //根据最惨定价过滤过滤
            Quad quad = FilterItemByPrice(orgId,shopid,list_filterItem,aos_sync_logS,bidiMap_ietmID);
            Map<String, Map<String, BigDecimal>> map_itemProfit = (Map<String, Map<String, BigDecimal>>) quad.getA();
            log.info(billno+ "   TrackerVipon抓客规则  获取毛利率：  "+map_itemProfit.size());
            Map<String, BigDecimal[]> map_itemPirce = (Map<String, BigDecimal[]>) quad.getB();
            log.info(billno+ "   TrackerVipon抓客规则  获取价格：  "+map_itemProfit.size());
            list_filterItem = (List<String>) quad.getC();
            log.info(billno+ "   TrackerVipon抓客规则  根据活动毛利率过滤：  "+list_filterItem.size());
            Map<String,BigDecimal> map_increase = (Map<String, BigDecimal>) quad.getD();

            //获取库存金额，并且根据金额排序排列
            LinkedHashMap<String, BigDecimal> linkMap_inventPrice = new LinkedHashMap<>();
            Map<String, BigDecimal> map_itemCost= calInventQty(orgId, list_filterItem, bidiMap_ietmID, map_stock, linkMap_inventPrice,aos_sync_logS);
            list_filterItem = new ArrayList<>(linkMap_inventPrice.keySet());
            log.info(billno+ "   TrackerVipon抓客规则  获取库存金额并且倒序：  "+list_filterItem.size());

            //查找物料分类
            Map<String, List<String>> map_cate = getItemCate(list_filterItem);
            //选择物料填入单据
            list_filterItem = fillItem(list_filterItem,map_conventItem,map_seasonItem,map_saleQty,map_cate);
            log.info(billno+ "   TrackerVipon  选择物料完成   ：  "+list_filterItem.size());

            //查找帖子id
            Map<String, String> map_asin = ActUtil.queryOrgShopItemASIN(orgId, shopid, list_filterItem);
            log.info(billno+ "   TrackerVipon  查找帖子id完成   ：  "+map_asin.size());
            //物料携带的的其他基础资料
            Map<String, DynamicObject> map_itemInfo = queryItemInfo(orgId, list_filterItem);
            log.info(billno+ "   TrackerVipon  查找物料信息完成   ：  "+map_itemInfo.size());
            //计算活动数量
            Map<String, Integer> map_itemActQty = calItemActQty(list_filterItem, bidiMap_ietmID, map_stock);
            log.info(billno+ "   TrackerVipon  计算活动数量完成   ：  "+map_itemActQty.size());
            //营收和成本
            Pair<Map<String, BigDecimal>, Map<String, BigDecimal>> pair_cal = queryRevenueAndCost(orgId, orgNumber, shopid, list_filterItem, map_itemPirce, map_itemCost, map_itemActQty);
            Map<String, BigDecimal> map_calCost = pair_cal.getFirst();
            Map<String, BigDecimal> map_calReven = pair_cal.getSecond();
            log.info(billno+ "   TrackerVipon  计算营收完成  ：  "+map_calReven.size());
            log.info(billno+ "   TrackerVipon  计算成本完成  ：  "+map_calCost.size());

            //填充数据
            DynamicObjectCollection dyc_actEnt = object.getDynamicObjectCollection("aos_sal_actplanentity");
            //物料的所有字段
            List<String> list_itemFields = new ArrayList<>();
            if (map_itemInfo.size()>0){
                DynamicObject itemInfo = map_itemInfo.values().stream().collect(Collectors.toList()).get(0);
                list_itemFields = itemInfo.getDynamicObjectType().getProperties().stream().map(pro -> pro.getName()).collect(Collectors.toList());
            }
            //detialType
            Map<String, String> map_itemToType = QueryTypedatail(orgNumber, list_filterItem);
            log.info(billno+ "   TrackerVipon  填充数据开始   ");
            for (String itemid : list_filterItem) {
                String itemNumber = bidiMap_ietmID.get(itemid);
                DynamicObject dy_new = dyc_actEnt.addNew();
                //物料基础信息
                if (map_itemInfo.containsKey(itemid)){
                    DynamicObject dy = map_itemInfo.get(itemid);
                    for (String field : list_itemFields) {
                        dy_new.set(field,dy.get(field));
                    }
                }
                //活动价、原价
                if (map_itemPirce.containsKey(itemid)) {
                    BigDecimal[] big_price = map_itemPirce.get(itemid);
                    dy_new.set("aos_price",big_price[0]);
                    dy_new.set("aos_actprice",big_price[1]);
                    if (map_itemProfit.containsKey(itemid)){
                        Map<String, BigDecimal> map = map_itemProfit.get(itemid);
                        dy_new.set("aos_profit",map.get("aos_profit"));
                        dy_new.set("aos_item_cost",map.get("aos_item_cost"));
                        dy_new.set("aos_lowest_fee",map.get("aos_lowest_fee"));
                        dy_new.set("aos_plat_rate",map.get("aos_plat_rate"));
                        dy_new.set("aos_vat_amount",map.get("aos_vat_amount"));
                        dy_new.set("aos_excval",map.get("aos_excval"));
                    }
                }

                //大类中类
                if (map_cate.containsKey(itemid)){
                    List<String> list_cate = map_cate.get(itemid);
                    if (list_cate.size() > 0)
                    dy_new.set("aos_category_stat1",list_cate.get(0));
                    if (list_cate.size()>1) {
                        dy_new.set("aos_category_stat2",list_cate.get(1));
                    }
                }
                //帖子id
                if (map_asin.containsKey(itemid)){
                    dy_new.set("aos_postid",map_asin.get(itemid));
                }
                //活动开始时间和结束时间
                dy_new.set("aos_l_startdate",date_actBegin);
                dy_new.set("aos_enddate",date_actEnd);
                //活动数量
                if (map_itemActQty.containsKey(itemid)){
                    dy_new.set("aos_actqty",map_itemActQty.get(itemid));
                }
                //需提价
                if (map_increase.containsKey(itemid)){
                    dy_new.set("aos_increase",map_increase.get(itemid));
                }
                //活动状态
                dy_new.set("aos_l_actstatus","A");
                //营收
                if (map_calReven.containsKey(itemid))
                    dy_new.set("aos_revenue_tax_free",map_calReven.get(itemid));
                //成本
                if (map_calCost.containsKey(itemid))
                    dy_new.set("aos_cost_tax_free",map_calCost.get(itemid));
                //detialTyep
                if (map_itemToType.containsKey(itemid))
                    dy_new.set("aos_typedetail",map_itemToType.get(itemid));
                if (map_conventItem.containsKey(itemid))
                    dy_new.set("aos_typedetail",map_conventItem.get(itemid));
                else
                    dy_new.set("aos_typedetail",map_seasonItem.get(itemid));
            }
            log.info(billno+ "   TrackerVipon  填充数据结束   ");
            SaveServiceHelper.save(new DynamicObject[]{object});
            object = BusinessDataServiceHelper.loadSingle(object.getPkValue(),"aos_act_select_plan");
            //赋值库存信息、活动信息
            ActPlanService actPlanService = new ActPlanServiceImpl();
            actPlanService.updateActInfo(object);
            //统计行信息到头表
            sal.act.ActShopProfit.aos_sal_act_from.collectRevenueCost(object);
            SaveServiceHelper.save(new DynamicObject[]{object});
            log.info(billno+ "   TrackerVipon  头表填充数据   ");

        }catch (Exception e){
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            log.info(billno+ "   TrackerVipon  发生异常  ：  "+writer.toString());
            e.printStackTrace();
            throw e;
        }
        finally {
            SaveServiceHelper.save(new DynamicObject[]{aos_sync_log});  //保存日志
        }
    }
    /**获取常规产品 **/
    private static Map<String,String> getConventItem(Object orgID){
        Map<String,String> map_re = new HashMap<>();
        SeasonAttrDao seasonAttrDao = new SeasonAttrDaoImpl();
        List<String> list_conventSeason = seasonAttrDao.getConventionalProductSeasonName();
        QFilter filter_season = new QFilter("aos_entryentity.aos_seasonal_attr",QFilter.in,list_conventSeason);
        //滞销报价中的物料
        QFilter filter_org = new QFilter("aos_org","=",orgID);
        LocalDate now = LocalDate.now();
        QFilter filter_date = new QFilter("aos_sche_date",">=",now.minusDays(6).toString());
        List<String> list_type = Arrays.asList("低: 0销量", "高: 0销量");
        QFilter filter_type = new QFilter("aos_entryentity.aos_unsalable_type",QFilter.in,list_type);
        QFilter [] qfs = new QFilter[]{filter_season,filter_date,filter_org,filter_type};
        DynamicObjectCollection dyc_ur = QueryServiceHelper.query("aos_sal_quo_ur", "aos_entryentity.aos_sku aos_sku", qfs);
        for (DynamicObject dy_ur : dyc_ur) {
            map_re.put(dy_ur.getString("aos_sku"),"零销量");
        }
        //滞销清单
        filter_org = new QFilter("aos_orgid","=",orgID);
        DynamicObjectCollection dyc_st = QueryServiceHelper.query("aos_base_stitem", "aos_itemid", new QFilter[]{filter_org});
        List<String> list_itemid = dyc_st.stream().map(dy -> dy.getString("aos_itemid")).distinct().collect(Collectors.toList());

        QFilter filter_id = new QFilter("id",QFilter.in,list_itemid);
        filter_org.__setProperty("aos_contryentry.aos_nationality");
        filter_season = new QFilter("aos_contryentry.aos_seasonseting",QFilter.in,seasonAttrDao.getConventionalProductSeason());
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        DynamicObjectCollection dyc_item = QueryServiceHelper.query("bd_material", str.toString(), new QFilter[]{filter_org,filter_id,filter_season});
        for (DynamicObject dy_st : dyc_item) {
            map_re.put(dy_st.getString("id"),"货多销少");
        }
        return map_re;
    }
    /** 获取季节品/节日品 **/
    private static Map<String,String> getSeasonItem(Object orgID,DynamicObjectCollection aos_sync_logS){
        Map<String,String> map_re = new HashMap<>();
        QFilter filter_org = new QFilter("aos_org","=",orgID);
        //最新的周一
        LocalDate now = LocalDate.now();
        now = now.with(DayOfWeek.MONDAY);
        QFilter filter_from = new QFilter("aos_sche_date",">=",now.toString());
        QFilter filter_to = new QFilter("aos_sche_date","<",now.plusDays(1).toString());
        QFilter filter_type = new QFilter("aos_entryentity.aos_adjtype","=","降价");
        List<String> list_item = QueryServiceHelper
                .query("aos_sal_quo_sa", "aos_entryentity.aos_sku aos_sku", new QFilter[]{filter_from, filter_to, filter_org,filter_type})
                .stream()
                .map(dy -> dy.getString("aos_sku"))
                .distinct()
                .collect(Collectors.toList());
        //季节品的季节属性
        SeasonAttrDao seasonAttrDao = new SeasonAttrDaoImpl();
        List<String> list_season = seasonAttrDao.getSeasonalProductSeason();
        //查找物料中对应的季节属性
        QFilter filter_id = new QFilter("id",QFilter.in,list_item);
        filter_org.__setProperty("aos_contryentry.aos_nationality");
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        str.add("number");
        str.add("aos_contryentry.aos_seasonseting aos_seasonseting");
        str.add("aos_contryentry.aos_festivalseting aos_festivalseting");
        DynamicObjectCollection dyc_item = QueryServiceHelper.query("bd_material", str.toString(), new QFilter[]{filter_org,filter_id});

        for (DynamicObject dy : dyc_item) {
            //季节属性属于季节品 或者 节日属性不为空
            if (list_season.contains(dy.getString("aos_seasonseting")) ||
                    !Cux_Common_Utl.IsNull(dy.get("aos_festivalseting"))){
                map_re.put(dy.getString("id"),"季节滞销");
            }
            else{
                MKTCom.Put_SyncLog(aos_sync_logS,dy.getString("number")+" 剔除;  是否季节品： "+
                        list_season.contains(dy.getString("aos_seasonseting"))+" 是否是节日品： "+
                        !Cux_Common_Utl.IsNull(dy.get("aos_festivalseting")));
            }
        }
        return map_re;
    }
    /**
     * 根据库存过滤物料
     * @param orgID org
     * @return  filterItemid
     */
    private static Map<String,Integer> FilterItemByStock(String orgID,BidiMap<String,String> bidiMap_ietmID,DynamicObjectCollection aos_sync_logS){
        List<String> list_itemNumber = new ArrayList<>(bidiMap_ietmID.values());
        //获取结存的15天前的海外库存
        LocalDate local_now = LocalDate.now();
        Map<String, Integer> map_stockBefore = ActUtil.queryBalanceOverseasInventory(orgID, local_now.minusDays(15).toString(), list_itemNumber);
        //获取当前库存
        Map<String, Integer> map_stock = ActUtil.queryItemOverseasInventory(orgID, list_itemNumber);
        //获取安全库存的标准
        QFilter filter_object = new QFilter("aos_project.name",QFilter.equals,"安全库存＞");
        QFilter filter_org = new QFilter("aos_org",QFilter.equals,orgID);
        DynamicObject dy_stand = QueryServiceHelper.queryOne("aos_sal_quo_m_coe", "aos_value", new QFilter[]{filter_object, filter_org});
        int stand=0;
        if (dy_stand!=null&&dy_stand.get("aos_value")!=null){
            stand = dy_stand.getBigDecimal("aos_value").intValue();
        }
        Map<String,Integer> map_re = new HashMap<>();
        for (String item : list_itemNumber) {
            StringJoiner str = new StringJoiner(";");
            str.add(item);
            boolean create = true;
            if (!map_stock.containsKey(item)){
                str.add("当前库存不存在 剔除");
                create = false;
            }
            if (!map_stockBefore.containsKey(item)){
                str.add("前15天库存不存在 剔除");
                create = false;
            }
            if (create){
                int stock_now = map_stock.get(item);
                str.add("当前库存： "+stock_now);
                int stock_before = map_stockBefore.get(item);
                str.add("之前库存： "+stock_before);
                str.add("安全标准： "+stand);
                if (stock_now>50 && stock_before>stand){
                    map_re.put(item,stock_now);
                }else{
                    str.add("库存不满足");
                }

            }
            MKTCom.Put_SyncLog(aos_sync_logS,   str.toString());
        }
        return map_re;
    }
    /**
     * 根据海外滞销库存过滤物料
     * @param orgID orgid
     * @param list_item itemid
     */
    private static Map<String,String>  FilterItemByUnsale(String orgID,List<String> list_item){
        QFilter filter_org = new QFilter("aos_org",QFilter.equals,orgID);
        QFilter filter_item = new QFilter("aos_entryentity.aos_sku.number",QFilter.in,list_item);
        LocalDate localDate = LocalDate.now().minusDays(7);
        QFilter filter_date = new QFilter("aos_sche_date",">=",localDate.toString());
        StringJoiner str = new StringJoiner(",");
        str.add("aos_entryentity.aos_sku.number aos_sku");
        str.add("aos_entryentity.aos_unsalable_type aos_unsalable_type");
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_sal_quo_ur", str.toString(), new QFilter[]{filter_org, filter_item,filter_date});
        return dyc.stream()
                .collect(Collectors.toMap(
                        dy -> dy.getString("aos_sku"),
                        dy -> dy.getString("aos_unsalable_type"),
                        (key1, key2) -> key1));
    }
    /**
     * 根据活动过滤物料
     * @param org       orgid
     * @param date_s    act begin date
     * @param date_e    act end date
     * @param list_ietm itemid
     * @return  filterItemid
     */
    private static List<String> FilterItemByAct(String billno,String org,String date_s,String date_e,LocalDate local_make,List<String> list_ietm,
                                                DynamicObjectCollection aos_sync_logS,BidiMap<String,String> bidiMap_ietmID){
        //根据国别+抓客活动开始和结束期间(头信息)判断该SKU是否在其他活动计划表中正在进行：LD、DOTD和7DD，且活动开始和结束时间（行上）与抓客期间重叠，
        //且头状态或行状态非“手工关闭”；
        QFilter filter_org = new QFilter("aos_nationality",QFilter.equals,org);
        List<String> list_actType = Arrays.asList("LD", "DOTD", "7DD");
        QFilter filter_actType = new QFilter("aos_acttype.number",QFilter.in,list_actType);
        //情况1   行开始>= 抓客开始 且 行开始 <= 抓客结束
        QFilter filter_begin = new QFilter("aos_sal_actplanentity.aos_l_startdate",">=",date_s);
        QFilter filter_over = new QFilter("aos_sal_actplanentity.aos_l_startdate","<=",date_e);
        QFilter filter1 = filter_begin.and(filter_over);
        //情况2   行结束>=抓客开始 且 行结束<= 抓客结束
        filter_begin = new QFilter("aos_sal_actplanentity.aos_enddate",">=",date_s);
        filter_over = new QFilter("aos_sal_actplanentity.aos_enddate","<=",date_e);
        QFilter filter2 = filter_begin.and(filter_over);
        //情况3  行开始<=抓客开始,且 行结束>= 抓客结束
        QFilter filter_date = filter1.or(filter2);
        QFilter filter_headStatus = new QFilter("aos_actstatus","!=","C");
        QFilter filter_rowStatus = new QFilter("aos_sal_actplanentity.aos_l_actstatus","!=","B");
        QFilter [] qfs = new QFilter[]{filter_org,filter_actType,filter_date,filter_headStatus,filter_rowStatus};
        DynamicObjectCollection dyc_otherAct = QueryServiceHelper.query("aos_act_select_plan", "aos_sal_actplanentity.aos_itemnum aos_itemnum", qfs);
        List<String> list_OtherActItem = dyc_otherAct.stream()
                .map(dy -> dy.getString("aos_itemnum"))
                .collect(Collectors.toList());
        log.info("TrackerVipon  其他活动剔除物料数量：  "+list_OtherActItem.size());

        //根据国别判断录入日期8天内该SKU是否在其他“Tracker/Vipon”的活动计划中，且头状态或行状态非“手工关闭”
        filter_actType = new QFilter("aos_acttype.number",QFilter.equals,"Tracker/Vipon");
        QFilter filter_billno = new QFilter("billno","!=",billno);
        QFilter filter_make_e = new QFilter("aos_makedate","<",local_make.plusDays(1));
        QFilter filter_make_s = new QFilter("aos_makedate",">=",local_make.minusDays(8));
        qfs = new QFilter[]{filter_billno,filter_org,filter_actType,filter_make_e,filter_make_s,filter_headStatus,filter_rowStatus};
        DynamicObjectCollection dyc_theAct = QueryServiceHelper.query("aos_act_select_plan", "aos_sal_actplanentity.aos_itemnum aos_itemnum", qfs);
        List<String> list_sameAct = dyc_theAct.stream()
                .map(dy -> dy.getString("aos_itemnum"))
                .collect(Collectors.toList());
        log.info("TrackerVipon  相同活动剔除物料数量：  "+list_sameAct.size());

        List<String> list_re = new ArrayList<>(list_ietm.size());
        for (String item : list_ietm) {
            if (!list_OtherActItem.contains(item)&&!list_sameAct.contains(item)){
                list_re.add(item);
            }
            else{
                MKTCom.Put_SyncLog(aos_sync_logS,bidiMap_ietmID.get(item)+"存在重复活动 剔除");
            }
        }
        return list_re;
    }
    /**
     * 根据折扣价剔除
     * @param orgid
     * @param shopid
     * @param list_item
     * @param aos_sync_logS
     * @param bidiMap_ietmID
     * @return
     * @throws Exception
     */
    private static Quad FilterItemByPrice(Object orgid,Object shopid,List<String> list_item,
                                          DynamicObjectCollection aos_sync_logS,BidiMap<String,String> bidiMap_ietmID) throws Exception {
        List<String> list_re = new ArrayList<>(list_item.size());
        //获取亚马逊价格
        DynamicObject dy_AmazonShop = SalUtil.get_orgMainShop(orgid);
        Map<String, DynamicObject> map_AmazonPrice = SalUtil.get_shopPrice((String) orgid, dy_AmazonShop.getString("id"), list_item);
        //物料成本(入库成本)
        Map<String, BigDecimal> map_cost = common.sal.util.SalUtil.get_NewstCost((String) orgid, list_item);
        //最低快递费
        Map<String,BigDecimal> map_fee=common.sal.util.SalUtil.getMinExpressFee((String) orgid,(String)shopid,list_item);
        //记录原价和活动价格
        Map<String,BigDecimal[]> map_itemPrice = new HashMap<>();
        //记录需提价
        Map<String,BigDecimal> map_increase = new HashMap<>();
        for (String item : list_item) {
            StringJoiner str = new StringJoiner(";");
            str.add(bidiMap_ietmID.get(item));
            BigDecimal big_Amprice = BigDecimal.ZERO; //亚马逊价格
            str.add("amprice: ");
            if (map_AmazonPrice.containsKey(item)){
                big_Amprice = map_AmazonPrice.get(item).getBigDecimal("aos_currentprice");
                str.add("存在");
            }
            str.add(big_Amprice+" ");
            BigDecimal big_fee = map_fee.getOrDefault(item,BigDecimal.ZERO);
            str.add("快递费：  "+big_fee);
            BigDecimal big_cost = map_cost.getOrDefault(item,BigDecimal.ZERO);
            str.add("成本：  "+big_cost);
            BigDecimal big_price = big_fee.add(big_Amprice.multiply(BigDecimal.valueOf(0.15))).add(big_cost.multiply(BigDecimal.valueOf(0.3)));
            big_price = big_price.setScale(3,BigDecimal.ROUND_HALF_UP);
            str.add("最惨定价:  "+big_price);
            BigDecimal big_discount = big_Amprice.multiply(BigDecimal.valueOf(0.5));
            big_discount = big_discount.setScale(3,BigDecimal.ROUND_HALF_UP);
            if (big_discount.compareTo(big_price)<0){
                big_discount = big_price;
                //计算需提价
                BigDecimal bid_increase = big_discount.multiply(BigDecimal.valueOf(2));
                //计算提交后的利率
                if (big_Amprice.doubleValue()!=0){
                    BigDecimal bid_increaseRate = (bid_increase.subtract(big_Amprice)).divide(big_Amprice, 2, BigDecimal.ROUND_HALF_UP);
                    if (bid_increaseRate.doubleValue()>0.3){
                        str.add("剔除");
                        continue;
                    }
                }
                map_increase.put(item,bid_increase);
            }
            list_re.add(item);
            BigDecimal [] big_ItemPrice = new BigDecimal[2];
            big_ItemPrice[0] = big_Amprice;
            big_ItemPrice[1] = big_discount;
            map_itemPrice.put(item,big_ItemPrice);
            str.add("折扣价：  "+big_discount);
            MKTCom.Put_SyncLog(aos_sync_logS,str.toString());
        }
        //计算毛利率
        //计算毛利率
        Map<String, Map<String, BigDecimal>> map_profit = aos_sal_act_from.get_formula(orgid.toString(), shopid.toString(), null, map_itemPrice);
        return new Quad(map_profit, map_itemPrice, list_re, map_increase);
    }
    /**
     * 根据比例分配数量
     * @param map_proport   每个品类占的比例
     * @param total         总量
     * @return  cate to qty
     */
    private static Map<String,Integer> AllocateQty(Map<String,BigDecimal>map_proport,int total){
        Map<String,Integer> map_re = new HashMap<>();
        int assigned = 0 ;//已经分配量
        int index = 0,size = map_proport.size()-1;
        for (Map.Entry<String, BigDecimal> entry : map_proport.entrySet()) {
            int qty ;
            if (index == size)
                qty = total - assigned;
            else
            {
                qty = new BigDecimal(total).multiply(entry.getValue()).setScale(0,BigDecimal.ROUND_HALF_UP).intValue();
            }
            map_re.put(entry.getKey(),qty);
            assigned += qty;
            index++;
        }
        return map_re;
    }
    private static BidiMap<String,String> mapItemIDToNumber (List<String> list_item){
        BidiMap<String,String> map_re = new TreeBidiMap<>();
        QFilter filter_number = new QFilter("id",QFilter.in,list_item);
        DynamicObjectCollection dyc = QueryServiceHelper.query("bd_material", "id,number", new QFilter[]{filter_number});
        for (DynamicObject dy : dyc) {
            map_re.put(dy.getString("id"),dy.getString("number"));
        }
        return map_re;
    }
    /**
     * 计算库存金额
     * @param orgid                 org
     * @param list_filterItem       itemID
     * @param bidiMap_idToNumber    itemID to itemNumber
     * @param map_itemStock         itemNumber to StockQty
     * @return itemId to StockPrice (sort by stockPrice DESC)
     */
    private static Map<String,BigDecimal> calInventQty(Object orgid,List<String> list_filterItem,BidiMap<String,String> bidiMap_idToNumber,
                                                       Map<String,Integer> map_itemStock,LinkedHashMap <String,BigDecimal> linkMap_re,
                                                       DynamicObjectCollection aos_sync_logS){
        Map<String,BigDecimal> map_re = new HashMap<>();
        //获取最新入库成本
        Map<String, BigDecimal> map_cost = SalUtil.get_NewstCost(String.valueOf(orgid), list_filterItem);
        for (Map.Entry<String, BigDecimal> entry : map_cost.entrySet()) {
            if (bidiMap_idToNumber.containsKey(entry.getKey())){
                String itemNumber = bidiMap_idToNumber.get(entry.getKey());
                if (map_itemStock.containsKey(itemNumber)){
                    BigDecimal big_stockPrice = entry.getValue().multiply(new BigDecimal(map_itemStock.get(itemNumber))).setScale(3,BigDecimal.ROUND_HALF_UP);
                    map_re.put(entry.getKey(),big_stockPrice);
                    MKTCom.Put_SyncLog(aos_sync_logS,itemNumber+ "入库成本："+entry.getValue()+"  库存金额： "+big_stockPrice);
                }
            }
        }
        List<Map.Entry<String, BigDecimal>> list_re = map_re.entrySet().stream()
                .sorted((ent1, ent2) -> {
                    if (ent1.getValue().compareTo(ent2.getValue()) >= 0)
                        return -1;
                    else
                        return 1;
                })
                .collect(Collectors.toList());

        for (Map.Entry<String, BigDecimal> entry : list_re) {
            linkMap_re.put(entry.getKey(),entry.getValue());
        }
        return map_cost;
    }

    private static List<String> fillItem(List<String> list_filterItem,Map<String,String> map_conventItem,
                                         Map<String,String> map_seasonItem,Map<String,Integer> map_cateStand,Map<String,List<String>> map_cate){
        List<String> list_ALLfilledItem = new ArrayList<>();
        List<String> list_seasonFill = new ArrayList<>();

        Map<String,Integer> map_cateFill = new HashMap<>();
        for (String item : list_filterItem) {
            if (map_conventItem.containsKey(item)){
                if (map_cate.containsKey(item)){
                    List<String> list_cate = map_cate.get(item);
                    if (list_cate.size()>0){
                        String cate = list_cate.get(0);
                        int fillCount = map_cateFill.computeIfAbsent(cate, kye -> 0);
                        int stand = map_cateStand.getOrDefault(cate, 0);
                        if (fillCount<stand){
                            list_ALLfilledItem.add(item);
                            fillCount++;
                            map_cateFill.put(cate,fillCount);
                        }
                    }
                }
            }
            else if (map_seasonItem.containsKey(item)){
                if (list_seasonFill.size()<50){
                    list_seasonFill.add(item);
                    list_ALLfilledItem.add(item);
                }
            }
            if (list_ALLfilledItem.size()>=150)
                break;
        }
        return list_ALLfilledItem;
    }
    private static Map<String,DynamicObject> queryItemInfo (Object orgid,List<String> list_itemid){
        QFilter filter_item = new QFilter("id",QFilter.in,list_itemid);
        QFilter filter_org = new QFilter("aos_contryentry.aos_nationality",QFilter.equals,orgid);
        QFilter [] qfs = new QFilter[]{filter_item,filter_org};
        StringJoiner str = new StringJoiner(",");
        str.add("id aos_itemnum");
        str.add("name aos_itemname");
        str.add("aos_contryentry.aos_seasonseting.number aos_seasonattr");
        str.add("aos_contryentry.aos_contrybrand.name aos_brand");
        return QueryServiceHelper.query("bd_material", str.toString(), qfs).stream()
                .collect(Collectors.toMap(
                        dy -> dy.getString("aos_itemnum"),
                        dy -> dy,
                        (key1, key2) -> key1));
    }

    private static Map<String,Integer> calItemActQty(List<String> list_item,BidiMap<String,String> map_itemIdToNumber, Map<String,Integer> map_stock){
        Map<String,Integer> map_re = new HashMap<>();
        for (String itemid : list_item) {
            String itemNumber = map_itemIdToNumber.get(itemid);
            int stock = map_stock.getOrDefault(itemNumber, 0);
            stock = BigDecimal.valueOf(stock).multiply(BigDecimal.valueOf(0.1)).setScale(0,BigDecimal.ROUND_HALF_UP).intValue();
            int min = Math.min(stock, 20);
            map_re.put(itemid,min);
        }
        return map_re;
    }
    private static Pair<Map<String,BigDecimal>,Map<String,BigDecimal>> queryRevenueAndCost(Object orgid,String orgNumber,Object shopid,List<String> list_item,Map<String,
            BigDecimal[]> map_price, Map<String,BigDecimal> map_cost,Map<String,Integer> map_actQty){
        //获取快递费
        Map<String, BigDecimal> map_fee = SalUtil.getMinExpressFee(orgid.toString(), shopid.toString(),list_item);
        //平台费率
        QFilter filter_shop = new QFilter("id",QFilter.equals,shopid);
        DynamicObject dy_shop = QueryServiceHelper.queryOne("aos_sal_shop", "aos_plat_rate,aos_vat", new QFilter[]{filter_shop});
        BigDecimal big_platRate = BigDecimal.ZERO,big_vat = BigDecimal.ZERO;
        if (dy_shop!=null && dy_shop.get("aos_plat_rate")!=null)
            big_platRate = dy_shop.getBigDecimal("aos_plat_rate");

        //vat
        if (dy_shop!=null && dy_shop.get("aos_vat")!=null && dy_shop.getBigDecimal("aos_vat").compareTo(BigDecimal.ZERO)>0)
            big_vat = dy_shop.getBigDecimal("aos_vat");
        else{
            big_vat = ActUtil.get_VAT(orgid);
        }
        //汇率
        BigDecimal big_exchange = ActUtil.get_realTimeCurrency(orgNumber);

        Map<String,Map<String,BigDecimal>> map_itemInfo = new HashMap<>();
        for (String item : list_item) {
            Map<String,BigDecimal> map = new HashMap<>();
            BigDecimal[] big_price = map_price.get(item);
            //原价和活动价格
            map.put("aos_price",big_price[0]);
            map.put("aos_actprice",big_price[1]);
            //入库成本
            map.put("aos_item_cost",map_cost.get(item));
            //快递费
            if (map_fee.containsKey(item))
                map.put("aos_lowest_fee",map_fee.get(item));
            else
                map.put("aos_lowest_fee",BigDecimal.ZERO);
            //平台费率
            map.put("aos_plat_rate",big_platRate);
            //vat
            map.put("aos_vat_amount",big_vat);
            //汇率
            map.put("aos_excval",big_exchange);
            //活动数量
            map.put("aos_actqty",new BigDecimal(map_actQty.get(item)));
            map_itemInfo.put(item,map);
        }

        Map<String, BigDecimal> map_calCost = aos_sal_act_from.get_cost(orgid.toString(), shopid.toString(), map_itemInfo);
        Map<String, BigDecimal> map_calRevenue = aos_sal_act_from.get_revenue(orgid.toString(), shopid.toString(), map_itemInfo);

        Pair<Map<String, BigDecimal>, Map<String, BigDecimal>> pair_re = Pair.with(map_calCost, map_calRevenue);
        return pair_re;
    }
    /** 查找类型细分**/
    private static Map<String,String> QueryTypedatail(String aos_orgnum,List<String> list_itemID){
        DynamicObjectCollection selectList = ActUtil.queryActSelectList(aos_orgnum);
        return selectList.stream()
                .filter(dy->list_itemID.contains(dy.getString("aos_sku")))
                .collect(Collectors.toMap(
                        dy -> dy.getString("aos_sku"),
                        dy -> dy.getString("aos_typedetail"),
                        (key1, key2) -> key1
                ));
    }
    /**查找大类中类**/
    public static Map<String,List<String>> getItemCate(List<String> list_item){
        Map<String,List<String>> map_re =new HashMap<>();
        QFilter filter_item = new QFilter("material",QFilter.in,list_item);
        QFilter filter_standard =new QFilter("standard.number",QFilter.equals,"JBFLBZ");
        DynamicObjectCollection dyc_data = QueryServiceHelper
                .query("bd_materialgroupdetail", "group.name group,material", new QFilter[]{filter_item, filter_standard});
        for (DynamicObject dy : dyc_data) {
            List<String> list_cate = Arrays.stream(dy.getString("group").split(","))
                    .collect(Collectors.toList());
            map_re.put(dy.getString("material"),list_cate);
        }
        return map_re;
    }
}
