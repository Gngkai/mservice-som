package mkt.act.rule.Import;

import common.CommonDataSomAct;
import common.fnd.FndGlobal;
import common.sal.sys.sync.dao.*;
import common.sal.sys.sync.dao.impl.*;
import common.sal.sys.sync.service.AvailableDaysService;
import common.sal.sys.sync.service.ItemCacheService;
import common.sal.sys.sync.service.impl.AvailableDaysServiceImpl;
import common.sal.sys.sync.service.impl.ItemCacheServiceImpl;
import common.sal.util.QFBuilder;
import kd.bos.algo.Algo;
import kd.bos.algo.AlgoContext;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.ErrorCode;
import kd.bos.exception.KDException;
import kd.bos.formula.FormulaEngine;
import kd.bos.servicehelper.QueryServiceHelper;
import mkt.act.rule.ActUtil;
import mkt.common.MktComUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author: GK
 * @create: 2023-12-21 18:49
 * @Description: 关于计算的工具类
 */
class EventRuleUtil {
    EventRule eventRule;

    EventRuleUtil(EventRule eventRule){
        this.eventRule = eventRule;
    }

    /**
     * 计算物料的ASIN
     */
    public void setItemAsin(){
        if (eventRule.map_asin!=null) {
            return;
        }
        Object shopid = eventRule.actPlanEntity.getDynamicObject("aos_shop").getPkValue();
        List<String> list_filterItem = new ArrayList<>(eventRule.shopItemInfo.size());
        for (DynamicObject info :eventRule.shopItemInfo) {
            list_filterItem.add(info.getString("id"));
        }
        eventRule.map_asin = ActUtil.queryOrgShopItemASIN(eventRule.orgEntity.getPkValue(),shopid,list_filterItem);

        //根据帖子ID将物料进行分组
        eventRule.map_asinToItem = new HashMap<>();
        for (DynamicObject row : eventRule.shopItemInfo) {
            String itemID = row.getString("id");

            if (eventRule.map_asin.containsKey(itemID)) {
                String asin = eventRule.map_asin.get(itemID);
                eventRule.map_asinToItem.computeIfAbsent(asin,k->new ArrayList<>()).add(row);
            }
        }
    }

    //平台仓可售天数
    public Map<String, BigDecimal> platSalDay;
    /**
     * 设置平台仓可售天数
     */
    public void setItemPlatSalDay (){
        if (platSalDay!=null) {
            return;
        }
        platSalDay = new HashMap<>();

        //获取7天店铺销量
        setItemShopSale(7);
        Map<Long, Integer> shopSalQty = itemShopSale.getOrDefault("7",new HashMap<>());
        //获取平台仓库存和非平台仓库存
        setItemPlatStock();
        setItemNoPlatStock();
        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            long itemID = Long.parseLong(itemInfoe.getString("id"));
            //库存
            int sock = itemPlatStock.getOrDefault(itemID,0);
            if (sock ==0){
                sock = itemNoPlatStock.getOrDefault(itemID,0);
            }
            //销量
            int salQty = shopSalQty.getOrDefault(itemID, 0);
            if (sock == 0){
                platSalDay.put(String.valueOf(itemID),new BigDecimal(0));
            }
            else {
                if (salQty ==0 ){
                    platSalDay.put(String.valueOf(itemID),new BigDecimal(999));
                }
                else{
                    platSalDay.put(String.valueOf(itemID),new BigDecimal(sock).divide(new BigDecimal(salQty),1,BigDecimal.ROUND_UP));
                }
            }
        }
    }

    public Map<String,Map<String,BigDecimal>> pastSale;
    /**
     * 店铺过去卖价价格
     * @param day 天数
     */
    public void setPastSale(String day){
        if (pastSale==null) {
            pastSale = new HashMap<>();
        }
        if (pastSale.containsKey(day)) {
            return;
        }
        QFBuilder builder = new QFBuilder();
        builder.add("aos_org","=",eventRule.orgEntity.getPkValue());
        builder.add("aos_shopfid","=",eventRule.actPlanEntity.getDynamicObject("aos_shop").getPkValue());
        LocalDate now = LocalDate.now();
        builder.add("aos_local_date","<",now.toString());
        builder.add("aos_local_date",">=",now.minusDays(Long.parseLong(day)).toString());
        builder.add("aos_item_fid","!=","");
        builder.add("aos_trans_price",">",0);
        OrderLineDao orderLineDao = new OrderLineDaoImpl();
        String select = "aos_item_fid,aos_trans_price";
        Map<String,BigDecimal> results = new HashMap<>(eventRule.itemInfoes.size());
        DynamicObjectCollection orderInfoes = orderLineDao.queryOrderInfo(select, builder);
        for (DynamicObject row : orderInfoes) {
            String itemFid = row.getString("aos_item_fid");
            BigDecimal value = BigDecimal.ZERO;
            if (results.containsKey(itemFid)) {
                value = results.get(itemFid);
            }
            if (value.compareTo(row.getBigDecimal("aos_trans_price"))<0) {
                value = row.getBigDecimal("aos_trans_price");
            }
            results.put(itemFid,value);
        }
        pastSale.put(day,results);
    }

    /**
     * 设置折扣
     * @return 折扣
     */
    public Map<String,BigDecimal> setDiscount(){
        BigDecimal discount = eventRule.actPlanEntity.getBigDecimal("aos_disstrength");
        if (discount ==null) {
            discount = BigDecimal.ONE;
        }
        Map<String,BigDecimal> result = new HashMap<>(eventRule.itemInfoes.size());
        for (DynamicObject infoe : eventRule.itemInfoes) {
            result.put(infoe.getString("id"),discount);
        }
        return result;
    }

    public Map<String,Map<String,BigDecimal>> pastPrices;
    /**
     * 店铺过去最低定价价格
     * @param day 天数
     */
    public void setPastPrice(String day){
        if (pastPrices==null) {
            pastPrices = new HashMap<>();
        }
        if (pastPrices.containsKey(day)) {
            return;
        }
        Map<String,BigDecimal> results = new HashMap<>(eventRule.itemInfoes.size());
        QFBuilder builder = new QFBuilder();
        LocalDate now = LocalDate.now();
        builder.add("aos_date","<",now.toString());
        builder.add("aos_date",">=",now.minusDays(Integer.parseInt(day)).toString());
        builder.add("aos_orgid","=",eventRule.orgEntity.getPkValue());
        builder.add("aos_shopfid","=",eventRule.actPlanEntity.getDynamicObject("aos_shop").getPkValue());
        try (AlgoContext ignored = Algo.newContext()){
            String algo = "mkt.act.rule.EventRule.setPastPrice";
            DataSet dataSet = QueryServiceHelper.queryDataSet(algo, "aos_mkt_invprz_bak", "aos_itemid,aos_currentprice", builder.toArray(),null);
            DataSet groupDataSet = dataSet.groupBy(new String[]{"aos_itemid"}).min("aos_currentprice").finish();
            for (Row row : groupDataSet) {
                results.put(row.getString("aos_itemid"),row.getBigDecimal("aos_currentprice"));
            }
        }
        pastPrices.put(day,results);
    }

    Map<String,String> seasonStage;
    /**
     * 设置季节品和节日品的 季初、季中，季末
     */
    public void setSeasonStage(){
        if (seasonStage!=null) {
            return;
        }
        seasonStage = new HashMap<>();
        QFBuilder builder = new QFBuilder();
        builder.add("aos_orgid","=",eventRule.orgEntity.getPkValue());
        //获取数据
        Date now = new Date();
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_base_date", "aos_type,aos_datefrom,aos_dateto", builder.toArray());
        for (DynamicObject row : dyc) {
            String type = row.getString("aos_type");
            //阶段开始时间
            Date startDate = row.getDate("aos_datefrom");
            //阶段结束时间
            Date endDate = row.getDate("aos_dateto");
            //如果当前日期出于这个阶段中，那么毛利率标准取这个阶段的数据
            if (startDate.before(now) && now.before(endDate)){
                //圣诞节 季初
                switch (type) {
                    case "ACCH-1":
                        seasonStage.put("CHRISTMAS", "季节品1");
                        break;
                    //圣诞 季中
                    case "ACCH-2":
                        seasonStage.put("CHRISTMAS", "季节品2");
                        break;
                    //圣诞 季末
                    case "ACCH-3":
                        seasonStage.put("CHRISTMAS", "季节品3");
                        break;
                    //万圣节 季初
                    case "ACHA-1":
                        seasonStage.put("HALLOWEEN", "季节品1");
                        break;
                    //万圣节 季中
                    case "ACHA-2":
                        seasonStage.put("HALLOWEEN", "季节品2");
                        break;
                    //万圣节 季末
                    case "ACHA-3":
                        seasonStage.put("HALLOWEEN", "季节品3");
                        break;
                    //春夏品 季初
                    case "ACTSS-1":
                        seasonStage.put("SPRING_SUMMER_PRO", "季节品1");
                        break;
                    //春夏品 季中
                    case "ACTSS-2":
                        seasonStage.put("SPRING_SUMMER_PRO", "季节品2");
                        break;
                    //春夏品 季中
                    case "ACTSS-3":
                        seasonStage.put("SPRING_SUMMER_PRO", "季节品3");
                        break;
                    //秋冬品 季初
                    case "ACTAW-1":
                        seasonStage.put("AUTUMN_WINTER_PRO", "季节品1");
                        break;
                    //秋冬品 季中
                    case "ACTAW-2":
                        seasonStage.put("AUTUMN_WINTER_PRO", "季节品2");
                        break;
                    //秋冬品 季末
                    case "ACTAW-3":
                        seasonStage.put("AUTUMN_WINTER_PRO", "季节品3");
                        break;
                }
            }
        }
    }

    /**
     * 计算毛利率
     */
    Map<String, Map<String, BigDecimal>> itemActProfit;
    public void setItemActProfit() {
        if (itemActProfit != null) {
            return;
        }
        //获取活动价格
        setItemActPrice();
        //获取原价
        setItemPrice();
        Map<String,BigDecimal[]> map_itemPrice = new HashMap<>(eventRule.itemInfoes.size());
        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            String itemID = itemInfoe.getString("id");
            BigDecimal [] price = new BigDecimal[2];
            price[0] = itemPrice.getOrDefault(itemID,BigDecimal.ZERO);
            price[1] = itemActPrice.getOrDefault(itemID,BigDecimal.ZERO);
            map_itemPrice.put(itemID,price);
        }
        String shopId = eventRule.actPlanEntity.getDynamicObject("aos_shop").getString("id");
        try {
            itemActProfit = CommonDataSomAct.get_formula2(eventRule.orgEntity.getString("id"), shopId, map_itemPrice, "/");
        }catch (Exception e){
            e.printStackTrace();
            throw new KDException(new ErrorCode("计算毛利率异常",e.getMessage()));

        }

    }

    public Map<String,BigDecimal> itemCurrentPrice;
    /**
     * 计算店铺当前价格
     */
    public void setCurrentPrice(){
        if (itemCurrentPrice!=null) {
            return;
        }
        itemCurrentPrice = new HashMap<>(eventRule.itemInfoes.size());
        QFBuilder builder = new QFBuilder();
        builder.add("aos_org","=",eventRule.orgEntity.getPkValue());
        builder.add("aos_type","=","currentPrice");
        builder.add("aos_shop","=",eventRule.actPlanEntity.getDynamicObject("aos_shop").getPkValue());
        builder.add("aos_item","!=","");
        builder.add("aos_price",">",0);
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_shop_price", "aos_item,aos_price", builder.toArray());
        for (DynamicObject dy : dyc) {
            itemCurrentPrice.put(dy.getString("aos_item"),dy.getBigDecimal("aos_price"));
        }
        //缺失价格的物料
        List<String> missingPrice = new ArrayList<>(eventRule.itemInfoes.size());
        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            if (!itemCurrentPrice.containsKey(itemInfoe.getString("id"))){
                missingPrice.add(itemInfoe.getString("id"));
            }
        }
        //查找每日价格中的数据
        ItemPriceDao priceDao = new ItemPriceDaoImpl();
        Map<String, BigDecimal> results = priceDao.queryShopItemPrice(eventRule.orgEntity.getPkValue(), eventRule.actPlanEntity.getDynamicObject("aos_shop").getPkValue());
        for (Map.Entry<String, BigDecimal> entry : results.entrySet()) {
            if (missingPrice.contains(entry.getKey())) {
                itemCurrentPrice.put(entry.getKey(),entry.getValue());
            }
        }
    }

    public Map<String,BigDecimal> itemWebPrice;
    /**
     * 计算官网当前价格
     */
    public void setWebPrice(){
        if (itemWebPrice!=null) {
            return;
        }
        itemWebPrice = new HashMap<>(eventRule.itemInfoes.size());
        QFBuilder builder = new QFBuilder();
        builder.add("aos_org","=",eventRule.orgEntity.getPkValue());
        builder.add("aos_type","=","webPrice");
        builder.add("aos_shop","=",eventRule.actPlanEntity.getDynamicObject("aos_shop").getPkValue());
        builder.add("aos_item","!=","");
        builder.add("aos_price",">",0);
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_shop_price", "aos_item,aos_price", builder.toArray());
        for (DynamicObject dy : dyc) {
            itemWebPrice.put(dy.getString("aos_item"),dy.getBigDecimal("aos_price"));
        }
        //缺失价格的物料
        List<String> missingPrice = new ArrayList<>(eventRule.itemInfoes.size());
        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            if (!itemWebPrice.containsKey(itemInfoe.getString("id"))){
                missingPrice.add(itemInfoe.getString("id"));
            }
        }
        //查找每日价格中的数据
        ItemPriceDao priceDao = new ItemPriceDaoImpl();
        Map<String, BigDecimal> results = priceDao.queryWebItemPrice(eventRule.orgEntity.getPkValue());
        for (Map.Entry<String, BigDecimal> entry : results.entrySet()) {
            if (missingPrice.contains(entry.getKey())) {
                itemWebPrice.put(entry.getKey(),entry.getValue());
            }
        }
    }

    public Map<String,BigDecimal> itemVrpPrice;
    /**
     * 计算Vrp价格
     */
    public void setVrpPrice(){
        if (itemVrpPrice!=null) {
            return;
        }
        itemVrpPrice = new HashMap<>(eventRule.itemInfoes.size());
        QFBuilder builder = new QFBuilder();
        builder.add("aos_org","=",eventRule.orgEntity.getPkValue());
        builder.add("aos_shop","=",eventRule.actPlanEntity.getDynamicObject("aos_shop").getPkValue());
        builder.add("aos_type","=","vrp");
        builder.add("aos_item","!=","");
        builder.add("aos_price",">",0);
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_shop_price", "aos_item,aos_price", builder.toArray());
        for (DynamicObject dy : dyc) {
            itemVrpPrice.put(dy.getString("aos_item"),dy.getBigDecimal("aos_price"));
        }
    }

    Map<String,List<String>> profitStand;
    /**
     * 获取符合各个毛利率标准的物料
     */
    public void setProfitStand(){
        if (profitStand!=null) {
            return;
        }
        //计算最惨定价
        setMinPrice();
        profitStand = new HashMap<>();
        String select = "aos_type1,aos_use,aos_pro_"+(eventRule.orgEntity.getString("number").toLowerCase())+" aos_pro";
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_act_pro", select, null);
        for (DynamicObject dy : dyc) {
            //项目名称
            String project = dy.getString("aos_type1");
            //采用最低定价
            if (dy.getBoolean("aos_use")) {
                profitStand.put(project,minPriceItem);
            }
            //比较毛利率
            else {
                //毛利标准
                BigDecimal proStand = dy.getBigDecimal("aos_pro");
                if (proStand == null){
                    proStand = BigDecimal.ZERO;
                }
                List<String> filterItems = new ArrayList<>(eventRule.itemInfoes.size());
                for (DynamicObject itemInfoe : eventRule.itemInfoes) {
                    String itemId = itemInfoe.getString("id");
                    BigDecimal itemProfit = BigDecimal.ZERO;
                    if (itemActProfit.containsKey(itemId)){
                        itemProfit = itemActProfit.get(itemId).getOrDefault("value",BigDecimal.ZERO);
                    }
                    if (itemProfit.compareTo(proStand)>=0){
                        filterItems.add(itemId);
                    }
                }
                profitStand.put(project,filterItems);
            }
        }
    }

    /**
     * 计算满足最惨定价的物料
     */
    List<String> minPriceItem;
    public void setMinPrice(){
        if (minPriceItem!=null) {
            return;
        }
        setItemActProfit();
        minPriceItem = new ArrayList<>(eventRule.itemInfoes.size());
        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            String itemId = itemInfoe.getString("id");
            StringJoiner str = new StringJoiner(" , ");
            str.add(itemInfoe.getString("number"));
            if (itemActProfit.containsKey(itemId)){
                Map<String, BigDecimal> profit = itemActProfit.get(itemId);
                BigDecimal fee = profit.getOrDefault("aos_lowest_fee", BigDecimal.ZERO);
                str.add("fee : "+fee);
                BigDecimal cost = profit.getOrDefault("aos_item_cost", BigDecimal.ZERO);
                str.add("cost : "+cost);
                BigDecimal price = itemPrice.getOrDefault(itemId, BigDecimal.ZERO);
                price = fee
                        .add(price.multiply(BigDecimal.valueOf(0.15)))
                        .add(cost.multiply(BigDecimal.valueOf(0.3)));
                str.add("price : "+price);
                BigDecimal actPrice = itemActPrice.getOrDefault(itemId, BigDecimal.ZERO);
                str.add("actprice: "+actPrice);
                if (actPrice.compareTo(price)>=0){
                    minPriceItem.add(itemId);
                }else {
                    str.add("不满足最惨定价");
                }
            }
            else {
                str.add("活动毛利为空，最惨价 fales");
            }
            eventRule.fndLog.add(str.toString());
        }
    }

    public Map<Long,Integer> itemNoPlatStock;
    /**
     * 非平台仓库存
     */
    public void setItemNoPlatStock(){
        if (itemNoPlatStock!=null) {
            return;
        }
        ItemStockDao itemStockDao = new ItemStockDaoImpl();
        itemNoPlatStock = itemStockDao.listItemNoPlatformStock(eventRule.orgEntity.getLong("id"));
    }

    public Map<Long,Integer> itemPlatStock;
    /**
     * 平台仓库存
     */
    public void setItemPlatStock(){
        if (itemPlatStock!=null) {
            return;
        }
        itemPlatStock = new HashMap<>(eventRule.itemInfoes.size());
        setItemStock();
        setItemNoPlatStock();
        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            Long itemID = itemInfoe.getLong("id");
            Integer stock = itemSotck.getOrDefault(itemID, 0);
            Integer noPlatStock = itemNoPlatStock.getOrDefault(itemID, 0);
            itemPlatStock.put(itemID,(stock-noPlatStock));
        }
    }

    public Map<Long,Integer> itemMaxAge;
    /**
     * 最大库龄
     */
    public void setItemMaxAge(){
        if (itemMaxAge!=null) {
            return;
        }
        ItemAgeDao ageDao = new ItemAgeDaoImpl();
        itemMaxAge = ageDao.listItemMaxAgeByOrgId(eventRule.orgEntity.getLong("id"));
    }

    public Map<String,String> itemWeekUnsale;
    /**
     * 获取物料周滞销类型数据
     */
    public void setItemWeekUnsale(){
        if (itemWeekUnsale!=null) {
            return;
        }
        itemWeekUnsale = new HashMap<>(eventRule.itemInfoes.size());
        //查找最新一期的海外滞销报价日期
        QFBuilder builder = new QFBuilder();
        builder.add("aos_org","=",eventRule.orgEntity.getPkValue());
        DynamicObject dy = QueryServiceHelper.queryOne("aos_sal_quo_ur", "max(aos_sche_date) aos_sche_date", builder.toArray());
        LocalDate date = dy.getDate("aos_sche_date").toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        builder.add("aos_sche_date",">=",date.toString());
        builder.add("aos_sche_date","<",date.plusDays(1));
        builder.add("aos_entryentity.aos_sku","!=","");
        builder.add("aos_entryentity.aos_unsalable_type","!=","");

        StringJoiner str = new StringJoiner(",");
        str.add("aos_entryentity.aos_sku aos_sku");
        str.add("aos_entryentity.aos_unsalable_type aos_unsalable_type");
        DynamicObjectCollection results = QueryServiceHelper.query("aos_sal_quo_ur", str.toString(), builder.toArray());
        for (DynamicObject row : results) {
            itemWeekUnsale.put(row.getString("aos_sku"),row.getString("aos_unsalable_type"));
        }
    }

    public Map<String,String> itemUnsaleTyep;
    /**
     * 查找物料滞销类型
     */
    public void setItemUnsale(){
        if (itemUnsaleTyep!=null) {
            return;
        }
        //获取下拉列表
        Map<String, String> typeValue = MktComUtil.getComboMap("aos_base_stitem", "aos_type");
        QFBuilder builder = new QFBuilder();
        builder.add("aos_orgid","=",eventRule.orgEntity.getPkValue());
        builder.add("aos_itemid","!=","");
        builder.add("aos_type","!=","");
        DynamicObjectCollection results = QueryServiceHelper.query("aos_base_stitem", "aos_itemid,aos_type", builder.toArray());
        itemUnsaleTyep = new HashMap<>(results.size());
        for (DynamicObject result : results) {
            itemUnsaleTyep.put(result.getString("aos_itemid"),typeValue.get(result.getString("aos_type")));
        }
    }

    public Map<String,Map<Long,Integer>> itemShopSale;
    /**
     * 计算店铺销量
     * @param day 天数
     */
    public void setItemShopSale(int day){
        if (itemShopSale==null) {
            itemShopSale = new HashMap<>();
        }
        //判断传入天数天数是否为空
        if (FndGlobal.IsNull(day)) {
            return;
        }
        //判断这种天数的销量是否已经计算过
        if (itemShopSale.containsKey(String.valueOf(day))) {
            return;
        }
        //设置查找销量的开始时间和结束时间
        long orgID = eventRule.orgEntity.getLong("id");
        long shopId = eventRule.actPlanEntity.getDynamicObject("aos_shop").getLong("id");
        Calendar instance = Calendar.getInstance();
        Date end = instance.getTime();
        instance.add(Calendar.DATE,-day);
        Date start = instance.getTime();
        //计算销量
        OrderLineDao orderLineDao = new OrderLineDaoImpl();
        Map<Long, Integer> result = orderLineDao.listItemSales(orgID, null, shopId, start, end);
        itemShopSale.put(String.valueOf(day),result);
    }

    public Map<String,BigDecimal> itemActPrice;
    /**
     * 计算活动价格
     */
    public void setItemActPrice(){
        if (itemActPrice!=null) {
            return;
        }

        itemActPrice = new HashMap<>(eventRule.itemInfoes.size());
        //获取活动价公式
        String priceformula = eventRule.typEntity.getString("aos_priceformula_v");
        if (FndGlobal.IsNull(priceformula)) {
            return;
        }
        //获取公式中参数的值
        Map<String, Map<String, BigDecimal>> parameters = parsingPriceRule(priceformula);

        //设置定价习俗
        BigDecimal custom;
        if (FndGlobal.IsNotNull(eventRule.typEntity.getString("aos_custom"))) {
            custom = new BigDecimal("0."+eventRule.typEntity.getString("aos_custom"));
        }
        else {
            custom = new BigDecimal("0.99");
        }

        Map<String,Object> calParameters = new HashMap<>();
        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            calParameters.clear();
            String itemid = itemInfoe.getString("id");
            //获取物料对应的公式中每个参数对应的值
            for (Map.Entry<String, Map<String, BigDecimal>> entry : parameters.entrySet()) {
                calParameters.put(entry.getKey(),entry.getValue().getOrDefault(itemid,BigDecimal.ONE));
            }
            BigDecimal price = new BigDecimal(FormulaEngine.execExcelFormula(priceformula, calParameters).toString());
            price = price.setScale(0,BigDecimal.ROUND_DOWN);
            price = price.subtract(BigDecimal.ONE).add(custom);
            itemActPrice.put(itemid,price);
        }
    }

    /**
     * 解析价格公式
     * @param formal    公式
     * @return  key 公式标识： value：itemid : value
     */
    public Map<String, Map<String, BigDecimal>>  parsingPriceRule(String formal){
        String[] values = FormulaEngine.extractVariables(formal);
        Map<String, Map<String, BigDecimal>> parameters = new HashMap<>();
        for (String value : values) {
            //匹配成功
            boolean match = false;
            switch (value){
                case "currentPrice":
                    match = true;
                    setCurrentPrice();
                    parameters.put(value,itemCurrentPrice);
                    break;
                case "webPrice":
                    match = true;
                    setWebPrice();
                    parameters.put(value,itemWebPrice);
                    break;
                case "vrp":
                    match = true;
                    setVrpPrice();
                    parameters.put(value,itemVrpPrice);
                    break;
                case "discount":
                    match = true;
                    parameters.put(value,setDiscount());
                    break;
            }
            //上面没有匹配成功则说明 需要用到天数
            if (!match){
                String caption = value.substring(0, value.length() - 2);
                String day = value.substring(value.length() - 2);
                //店铺过去最低定价
                if ("shopPrice".equals(caption)){
                    setPastPrice(day);
                    parameters.put(value,pastPrices.get(day));
                }
                //店铺过去最低成交价
                else if ("shopSale".equals(caption)){
                    setPastSale(day);
                    parameters.put(value,pastSale.get(day));
                }
            }
        }
        return parameters;
    }

    public Map<String,BigDecimal> itemPrice;
    /**
     * 定价
     */
    public void setItemPrice(){
        if (itemPrice!=null) {
            return;
        }
        ItemPriceDao itemPriceDao = new ItemPriceDaoImpl();
        Object shopid = eventRule.actPlanEntity.getDynamicObject("aos_shop").getPkValue();
        itemPrice = itemPriceDao.queryShopItemPrice(eventRule.orgEntity.getPkValue(),shopid);
    }

    public Map<Long,Integer> itemSotck;
    /**
     * 海外库存
     */
    public void setItemStock(){
        if (itemSotck!=null) {
            return;
        }
        itemSotck = new HashMap<>();
        ItemStockDao stockDao = new ItemStockDaoImpl();
        //获取国别下的所有物料的海外库存
        itemSotck = stockDao.listItemOverSeaStock(eventRule.orgEntity.getLong("id"));
    }

    public Map<String,String> itemStatus;
    /**
     * 物料状态
     */
    public void setItemStatus(){
        if (itemStatus!=null) {
            return;
        }
        itemStatus = new HashMap<>(eventRule.itemInfoes.size());
        //获取下拉列表
        Map<String, String> countryStatus = MktComUtil.getComboMap("bd_material", "aos_contryentrystatus");

        for (DynamicObject infoe : eventRule.itemInfoes) {
            String itemid = infoe.getString("id");
            String status = infoe.getString("aos_contryentrystatus");
            itemStatus.put(itemid,countryStatus.get(status));
        }
    }

    public Map<String,Integer> itemSaleDay;
    /**
     * 可售天数
     */
    public void setSaleDays(){
        if (itemSaleDay !=null) {
            return;
        }
        //缓存海外库存
        setItemStock();
        //缓存7日日均销量
        setItemAverageByDay(7);
        Map<String, BigDecimal> itemAverageSale = itemAverage.get("7");
        Date startdate = new Date();
        AvailableDaysService service = new AvailableDaysServiceImpl();
        itemSaleDay = new HashMap<>(eventRule.itemInfoes.size());
        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            String itemID = itemInfoe.getString("id");
            int stock = itemSotck.getOrDefault(Long.parseLong(itemID), 0);
            BigDecimal sale = itemAverageSale.getOrDefault(itemID, BigDecimal.ZERO);
            int day = service.calAvailableDays(eventRule.orgEntity.getLong("id"), Long.parseLong(itemID), stock, sale, startdate);
            itemSaleDay.put(itemID,day);
        }
    }

    public Map<String,String> itemSeason;
    /**
     * 季节属性
     */
    public void setItemSeason(){
        if (itemSeason!=null) {
            return;
        }
        itemSeason = new HashMap<>(eventRule.itemInfoes.size());
        for (DynamicObject result :eventRule.itemInfoes) {
            itemSeason.put(result.getString("id"),result.getString("season"));
        }
    }

    public Map<String,Map<String, BigDecimal>> itemAverage;
    /**
     * 获取国别日均
     * @param day 日期
     */
    public void setItemAverageByDay(int day){
        if (itemAverage == null){
            itemAverage = new HashMap<>();
        }
        if (!itemAverage.containsKey(String.valueOf(day))){
            String number = eventRule.orgEntity.getString("number");
            Calendar instance = Calendar.getInstance();
            //美加前推一天
            if ("US".equals(number) || "CA".equals(number)){
                instance.add(Calendar.DATE,-1);
            }
            //设置开始时间和结束时间
            Date endDate = instance.getTime();
            instance.add(Calendar.DATE,-day);
            Date startDate = instance.getTime();
            OrderLineDao orderLineDao = new OrderLineDaoImpl();
            //获取销量
            Map<Long, Integer> saleInfo = orderLineDao.listItemSalesByOrgId(eventRule.orgEntity.getLong("id"), startDate, endDate);
            Map<String,BigDecimal> itemSale = new HashMap<>(saleInfo.size());
            if (day!=0){
                BigDecimal bd_day = new BigDecimal(day);
                //计算日均
                for (Map.Entry<Long, Integer> entry : saleInfo.entrySet()) {
                    BigDecimal value = new BigDecimal(entry.getValue()).divide(bd_day,4,BigDecimal.ROUND_HALF_UP);
                    itemSale.put(entry.getKey().toString(),value);
                }
            }
            else {
                for (Map.Entry<Long, Integer> entry : saleInfo.entrySet()) {
                    itemSale.put(entry.getKey().toString(),new BigDecimal(entry.getValue()));
                }
            }
            itemAverage.put(String.valueOf(day),itemSale);
        }
    }

    public Map<String,BigDecimal> reviewStars;
    public Map<String,Integer> reviewQty;
    /**
     * 获取评论星级
     */
    public void setReview(){
        if (reviewStars!=null) {
            return;
        }
        reviewStars = new HashMap<>();
        reviewQty = new HashMap<>();
        QFBuilder builder = new QFBuilder();
        builder.add("aos_orgid","=",eventRule.orgEntity.getPkValue());
        builder.add("aos_itemid","!=","");

        String select = "aos_itemid,aos_stars,aos_review";
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_sync_review", select, builder.toArray(), "modifytime asc");
        for (DynamicObject dy : dyc) {
            String itemid = dy.getString("aos_itemid");
            BigDecimal value = BigDecimal.ZERO;
            if (FndGlobal.IsNotNull(dy.get("aos_stars"))) {
                value = dy.getBigDecimal("aos_stars");
            }
            reviewStars.put(itemid,value);
            int qty = 0;
            if (FndGlobal.IsNotNull("aos_review")) {
                qty = dy.getInt("aos_review");
            }
            reviewQty.put(itemid,qty);
        }
    }

    public Map<String,Integer> itemActSalDay;
    /**
     * 活动可售天数
     */
    public void setActSaleDay(){
        if (itemActSalDay !=null) {
            return;
        }
        itemActSalDay = new HashMap<>(eventRule.itemInfoes.size());
        //设置活动库存
        setActInv();
        //设置7天日均销量
        setItemAverageByDay(7);
        Map<String, BigDecimal> itemAverageSale = itemAverage.get("7");
        itemSaleDay = new HashMap<>();
        Date startdate = eventRule.actPlanEntity.getDate("aos_startdate");
        AvailableDaysService service = new AvailableDaysServiceImpl();
        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            String itemID = itemInfoe.getString("id");
            int stock = itemActInv.getOrDefault(itemID, 0);
            BigDecimal sale = itemAverageSale.getOrDefault(itemID, BigDecimal.ZERO);
            int day = service.calAvailableDays(eventRule.orgEntity.getLong("id"), Long.parseLong(itemID), stock, sale, startdate);
            itemActSalDay.put(itemID,day);
        }
    }

    public Map<String,Integer> itemActInv;
    /**
     * 活动库存
     */
    public void setActInv(){
        if (itemActInv!=null) {
            return;
        }
        //先获取海外库存
        setItemStock();
        //取活动前的入库数量
        Date actStartdate = eventRule.actPlanEntity.getDate("aos_startdate");
        if (actStartdate == null){
            actStartdate = new Date();
        }
        WarehouseStockDao warehouseStockDao = new WarehouseStockDaoImpl();
        Map<String, Integer> itemRevenue = warehouseStockDao.getItemRevenue(eventRule.orgEntity.getPkValue(), actStartdate);
        //设置国别7天日均
        setItemAverageByDay(7);
        Map<String, BigDecimal> averageSales = itemAverage.get("7");

        //region 记录当前时间到活动时间这段期间的销量 ：（活动日-当前日期）*国别7天日均 * 季节系数，日均计算时加季节系数
        Map<String,BigDecimal> scheItemSale = new HashMap<>(eventRule.itemInfoes.size());
        //判断现在离活动开始差的月数
        LocalDate actStartDate = actStartdate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate nowDate = LocalDate.now();
        int months = (actStartDate.getYear() - nowDate.getYear())*12 + (actStartDate.getMonthValue() - nowDate.getMonthValue());
        //记录开始时间段和结束时间段
        LocalDate startDate = nowDate,endDate = nowDate;
        ItemCacheService cacheService = new ItemCacheServiceImpl();
        //月份
        int monthValue;
        //这段期间的天数差
        BigDecimal saleDay;
        for (int monthIndex = 0; monthIndex < months+1; monthIndex++) {
            //最后一个之前的时间如 总时间是 7.20-9.21，这段期间为 7.20-8.1 ; 8.1-9.1
            if (monthIndex<months){
                //结束时间
                endDate = endDate.plusMonths(1).withDayOfMonth(1);
                monthValue = startDate.getMonthValue();
                saleDay = new BigDecimal(endDate.toEpochDay() - startDate.toEpochDay());
            }
            //最后一个月：9.1 - 9.21
            else {
                endDate = actStartDate;
                monthValue = startDate.getMonthValue();
                saleDay = new BigDecimal(endDate.toEpochDay() - startDate.toEpochDay());
            }
            //计算这段时间的销量：（活动日-当前日期）*国别7天日均*月份系数
            for (DynamicObject itemInfoe : eventRule.itemInfoes) {
                String itemId = itemInfoe.getString("id");
                BigDecimal saleValue = scheItemSale.getOrDefault(itemId, BigDecimal.ZERO);

                //获取日均销量
                BigDecimal itemAveSale = averageSales.getOrDefault(itemId, BigDecimal.ZERO);
                //获取系数
                BigDecimal coefficient = cacheService.getCoefficient(eventRule.orgEntity.getLong("id"), Long.parseLong(itemId), monthValue);
                itemAveSale = itemAveSale.multiply(saleDay).multiply(coefficient);

                saleValue = saleValue.add(itemAveSale);
                scheItemSale.put(itemId,saleValue);
            }
            startDate = endDate;
        }
        //endregion

        itemActInv = new HashMap<>(eventRule.itemInfoes.size());
        //计算活动日库存 （当前库存+在途）-活动前的销量
        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            String itemid = itemInfoe.getString("id");
            //海外库存
            int stock = itemSotck.getOrDefault(Long.parseLong(itemid), 0);
            //在库
            int revenue = itemRevenue.getOrDefault(itemid, 0);
            //活动前的这段时间的销量
            BigDecimal sale = scheItemSale.getOrDefault(itemid, BigDecimal.ZERO);
            int value = BigDecimal.valueOf(stock + revenue).subtract(sale).setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
            itemActInv.put(itemid,value);
        }

    }

    /**
     * 计算物料的爆品分数
     * @param weight    爆品权重
     */
    public void setSaleOutWeight(BigDecimal weight) {
        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            String itemId = itemInfoe.getString("id");
            String number = itemInfoe.getString("number");
            //爆品
            BigDecimal itemWeight = BigDecimal.ZERO;
            if (itemInfoe.getBoolean("aos_is_saleout")) {
                itemWeight = BigDecimal.valueOf(10).multiply(weight);
                eventRule.fndLog.add(number + "  爆品分数  " + itemWeight);
            }
            itemWeight = eventRule.weightMap.getOrDefault(itemId,BigDecimal.ZERO).add(itemWeight);
            eventRule.weightMap.put(itemId,itemWeight);
        }
    }


    //周销售推荐产品清单中存在该物料
    List<String> recommendItem ;
    /**
     * 计算物料推荐分数
     * @param weight   推荐权重
     */
    public void setRecommendWeight(BigDecimal weight) {
        //先查找销售推荐表中的数据
        if (recommendItem == null) {
            QFBuilder builder = new QFBuilder();
            builder.add("aos_org", "=", eventRule.orgEntity.getLong("id"));
            builder.add("entryentity.aos_item", "!=", "");
            builder.add("billstatus", "!=", "A");
            LocalDate startDate = eventRule.actPlanEntity.getDate("aos_startdate").toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate endDate = eventRule.actPlanEntity.getDate("aos_enddate").toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            builder.add("entryentity.aos_date_s", "<=", endDate.toString());
            builder.add("entryentity.aos_date_e", ">=", startDate.toString());
            DynamicObjectCollection dyc = QueryServiceHelper.query("aos_act_propose", "entryentity.aos_item aos_item", builder.toArray());
            recommendItem = new ArrayList<>(dyc.size());
            for (DynamicObject dy : dyc) {
                recommendItem.add(dy.getString("aos_item"));
            }
        }
        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            String itemId = itemInfoe.getString("id");
            String number = itemInfoe.getString("number");
            //推荐
            BigDecimal itemWeight = BigDecimal.ZERO;
            if (recommendItem.contains(itemId)) {
                itemWeight = BigDecimal.valueOf(10).multiply(weight);
            }
            eventRule.fndLog.add(number + "  周销售清单推荐分数  " + itemWeight);
            itemWeight = eventRule.weightMap.getOrDefault(itemId, BigDecimal.ZERO).add(itemWeight);
            eventRule.weightMap.put(itemId, itemWeight);
        }
    }

    /**
     * 计算物料的历史增量分数
     * @param day       天数
     * @param weight    权重
     */
    public void setHistoryWeight(int day,BigDecimal weight,String sort) {
        setIntervalScore();
        //历史销量增量
        Map<String,BigDecimal> historySalesMap = new HashMap<>(eventRule.itemInfoes.size());
        //获取历史活动增量
        if(day >=0){
            LocalDate now = LocalDate.now();
            QFBuilder builder = new QFBuilder();
            builder.add("aos_org", "=", eventRule.orgEntity.getLong("id"));
            builder.add("entryentity.aos_item", "!=", "");
            builder.add("aos_incre_salqty", ">", "0");
            builder.add("aos_sal_actplanentity.aos_sal_actplanentity", "<=", now.toString());
            builder.add("aos_sal_actplanentity.aos_enddate", ">=", now.minusDays(day).toString());

            StringJoiner str = new StringJoiner(",");
            str.add("aos_sal_actplanentity.aos_itemnum aos_itemnum");
            str.add("aos_sal_actplanentity.aos_incre_salqty aos_incre_salqty");
            DynamicObjectCollection dyc = QueryServiceHelper.query("aos_act_select_plan", str.toString(), builder.toArray());

            //记录物料的历史增量
            Map<String,BigDecimal> itemSale = new HashMap<>(dyc.size());
            //记录物料的活动次数
            Map<String,Integer> itemActCount = new HashMap<>(dyc.size());

            for (DynamicObject dy : dyc) {
                String itemId = dy.getString("aos_itemnum");
                BigDecimal qty = dy.getBigDecimal("aos_incre_salqty");

                BigDecimal sale = itemSale.getOrDefault(itemId, BigDecimal.ZERO).add(qty);
                itemSale.put(itemId, sale);

                Integer count = itemActCount.getOrDefault(itemId, 0);
                itemActCount.put(itemId, count + 1);
            }

            //计算物料的历史增量
            for (DynamicObject itemInfoe : eventRule.itemInfoes) {
                String itemId = itemInfoe.getString("id");
                String number = itemInfoe.getString("number");
                BigDecimal sale = historySalesMap.getOrDefault(itemId, BigDecimal.ZERO);
                Integer count = itemActCount.getOrDefault(itemId, 0);
                //历史增量
                BigDecimal itemWeight = sale.divide(BigDecimal.valueOf(count), 4, BigDecimal.ROUND_HALF_UP);
                eventRule.fndLog.add(number+" 增长销量："+sale+"  次数： "+count + "  历史增量  " + itemWeight);
                historySalesMap.put(itemId, itemWeight);
            }
        }

        //先进行排序
        List<String> sortItemList ;
        //进行降序排序
        if (sort.equals("降序")){
         sortItemList = sortDesc(historySalesMap);
        }
        //升序排序
        else {
            sortItemList = sortAsc(historySalesMap);
        }

        if (sortItemList.size() > 0){
            calIntervalScore(sortItemList,weight,"历史增量");
        }
    }

    //设置店铺历史销量分数
    public void setShopHistoryWeight(int day,BigDecimal weight,String sort) {
        setIntervalScore();
        //历史销量增量
        Map<String,BigDecimal> historySalesMap = new HashMap<>(eventRule.itemInfoes.size());
        //获取店铺历史销量
        if(day >=0){
            setItemShopSale(day);
            Map<Long, Integer> shopSalQty = itemShopSale.getOrDefault(String.valueOf(day),new HashMap<>());
            for (DynamicObject itemInfoe : eventRule.itemInfoes) {
                String itemId = itemInfoe.getString("id");
                BigDecimal sale = BigDecimal.valueOf(shopSalQty.getOrDefault(Long.parseLong(itemId),0));
                historySalesMap.put(itemId, sale);
            }
        }

        //先进行排序
        List<String> sortItemList ;
        //进行降序排序
        if (sort.equals("降序")){
            sortItemList =  sortDesc(historySalesMap);
        }
        //升序排序
        else {
            sortItemList =  sortAsc(historySalesMap);
        }

        if (sortItemList.size() > 0){
           calIntervalScore(sortItemList,weight,"店铺过去销量 "+day+" 天");
        }
    }

    //设置店铺历史销量分数
    public void setOrgAvgWeight(int day,BigDecimal weight,String sort) {
        setIntervalScore();
        //基于排序的数据
        Map<String,BigDecimal> historySalesMap = new HashMap<>(eventRule.itemInfoes.size());
        //获取国别日均销量
        if(day >=0){
            setItemAverageByDay(day);
            Map<String, BigDecimal> itemSaleAve =itemAverage.get(String.valueOf(day));
            for (DynamicObject itemInfoe : eventRule.itemInfoes) {
                String itemId = itemInfoe.getString("id");
                BigDecimal sale = itemSaleAve.getOrDefault(itemId,BigDecimal.ZERO);
                historySalesMap.put(itemId, sale);
            }
        }

        //先进行排序
        List<String> sortItemList ;
        //进行降序排序
        if (sort.equals("降序")){
            sortItemList =  sortDesc(historySalesMap);
        }
        //升序排序
        else {
            sortItemList =  sortAsc(historySalesMap);
        }
        if (sortItemList.size() > 0){
            calIntervalScore(sortItemList,weight,"国别日均 "+day+" 天");
        }
    }

    //活动日库存分数
    public void setActStockWeight(BigDecimal weight,String sort) {
        setIntervalScore();
        //基于排序的数据
        Map<String,BigDecimal> sortDataMap = new HashMap<>(eventRule.itemInfoes.size());
        //获取活动日库存
        setActInv();

        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            String itemId = itemInfoe.getString("id");
            BigDecimal sale = new BigDecimal(itemActInv.getOrDefault(itemId,0));
            sortDataMap.put(itemId, sale);
        }

        //先进行排序
        List<String> sortItemList ;
        //进行降序排序
        if (sort.equals("降序")){
            sortItemList =  sortDesc(sortDataMap);
        }
        //升序排序
        else {
            sortItemList =  sortAsc(sortDataMap);
        }
        if (sortItemList.size() > 0){
            calIntervalScore(sortItemList,weight,"预计活动日库存");
        }
    }

    //活动日库存分数
    public void setActSaleDayWeight(BigDecimal weight,String sort) {
        setIntervalScore();
        //基于排序的数据
        Map<String,BigDecimal> sortDataMap = new HashMap<>(eventRule.itemInfoes.size());
        //获取活动日可售天数
        setActSaleDay();

        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            String itemId = itemInfoe.getString("id");
            BigDecimal sale = new BigDecimal(itemActSalDay.getOrDefault(itemId,0));
            sortDataMap.put(itemId, sale);
        }

        //先进行排序
        List<String> sortItemList ;
        //进行降序排序
        if (sort.equals("降序")){
            sortItemList =  sortDesc(sortDataMap);
        }
        //升序排序
        else {
            sortItemList =  sortAsc(sortDataMap);
        }
        if (sortItemList.size() > 0){
            calIntervalScore(sortItemList,weight,"预计活动日可售天数");
        }
    }

    //可售天数分数
    public void setSalDayWeight(BigDecimal weight,String sort) {
        setIntervalScore();
        //基于排序的数据
        Map<String,BigDecimal> sortDataMap = new HashMap<>(eventRule.itemInfoes.size());
        //平台仓可售天数
        setItemPlatSalDay();

        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            String itemId = itemInfoe.getString("id");
            BigDecimal sale = platSalDay.getOrDefault(itemId,BigDecimal.ZERO);
            sortDataMap.put(itemId, sale);
        }

        //先进行排序
        List<String> sortItemList ;
        //进行降序排序
        if (sort.equals("降序")){
            sortItemList =  sortDesc(sortDataMap);
        }
        //升序排序
        else {
            sortItemList =  sortAsc(sortDataMap);
        }
        if (sortItemList.size() > 0){
            calIntervalScore(sortItemList,weight,"当前可售天数");
        }
    }

    //当前库存金额分数
    public void setStockPriceWeight(BigDecimal weight,String sort) {
        setIntervalScore();
        //基于排序的数据
        Map<String,BigDecimal> sortDataMap = new HashMap<>(eventRule.itemInfoes.size());
        //海外库存
        setItemStock();
        ItemCacheService cacheService = new ItemCacheServiceImpl();

        long orgid = eventRule.orgEntity.getLong("id");
        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            String itemId = itemInfoe.getString("id");
            //入库成本
            Integer stock = itemSotck.getOrDefault(Long.parseLong(itemId), 0);
            BigDecimal latestItemCost = cacheService.getLatestItemCost(orgid, Long.parseLong(itemId));
            BigDecimal sale = latestItemCost.multiply(new BigDecimal(stock));
            sortDataMap.put(itemId, sale);
        }

        //先进行排序
        List<String> sortItemList ;
        //进行降序排序
        if (sort.equals("降序")){
            sortItemList =  sortDesc(sortDataMap);
        }
        //升序排序
        else {
            sortItemList =  sortAsc(sortDataMap);
        }
        if (sortItemList.size() > 0){
            calIntervalScore(sortItemList,weight,"当前库存金额");
        }
    }

    //活动毛利率分数
    public void setActProWeight(BigDecimal weight,String sort) {
        setIntervalScore();
        //基于排序的数据
        Map<String,BigDecimal> sortDataMap = new HashMap<>(eventRule.itemInfoes.size());
        //毛利率
        setItemActProfit();
        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            String itemId = itemInfoe.getString("id");
            //毛利率
            BigDecimal sale = BigDecimal.ZERO;
            if (itemActProfit.containsKey(itemId)){
                sale = itemActProfit.get(itemId).getOrDefault("value",BigDecimal.ZERO);
            }
            sortDataMap.put(itemId, sale);
        }

        //先进行排序
        List<String> sortItemList ;
        //进行降序排序
        if (sort.equals("降序")){
            sortItemList =  sortDesc(sortDataMap);
        }
        //升序排序
        else {
            sortItemList =  sortAsc(sortDataMap);
        }
        if (sortItemList.size() > 0){
            calIntervalScore(sortItemList,weight,"活动毛利率");
        }
    }

    //设置平台仓库存分数
    public void setPlantStockWeight(BigDecimal weight,String sort) {
        setIntervalScore();
        //基于排序的数据
        Map<String,BigDecimal> sortDataMap = new HashMap<>(eventRule.itemInfoes.size());
        //设置海外库存
        setItemPlatStock();
        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            String itemId = itemInfoe.getString("id");
            //物料库存
            BigDecimal sale = new BigDecimal(itemPlatStock.getOrDefault(Long.parseLong(itemId),0));
            sortDataMap.put(itemId, sale);
        }

        //先进行排序
        List<String> sortItemList ;
        //进行降序排序
        if (sort.equals("降序")){
            sortItemList =  sortDesc(sortDataMap);
        }
        //升序排序
        else {
            sortItemList =  sortAsc(sortDataMap);
        }
        if (sortItemList.size() > 0){
            calIntervalScore(sortItemList,weight,"平台仓库存数量");
        }
    }

    //设置排序区间的分数
    Map<String,Integer> sortWeightMap;
    public void setIntervalScore() {
        if (sortWeightMap !=null) {
            return;
        }
        //排序区间
        QFBuilder builder = new QFBuilder();
        builder.add("aos_range_s", ">=", 0);
        builder.add("aos_range_e", ">=", 0);
        builder.add("aos_score", ">=", 0);
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_act_share", "aos_range_s,aos_range_e,aos_score", builder.toArray(), "aos_range_s asc");
        sortWeightMap = new LinkedHashMap<>(dyc.size());
        int size = eventRule.itemInfoes.size();
        //记录最后一位的下标
        int lastIndex = 0;
        for (int i = 0; i < dyc.size()-1; i++) {
            DynamicObject row = dyc.get(i);
            BigDecimal aos_range_s = row.getBigDecimal("aos_range_s");
            BigDecimal aos_range_e = row.getBigDecimal("aos_range_e");
            int score = row.getInt("aos_score");
            int stareIndex = BigDecimal.valueOf(size).multiply(aos_range_s).setScale(0,BigDecimal.ROUND_DOWN).intValue();
            int endIndex = BigDecimal.valueOf(size).multiply(aos_range_e).setScale(0,BigDecimal.ROUND_DOWN).intValue();
            sortWeightMap.put(stareIndex+"/"+endIndex,score);
            eventRule.fndLog.add("排序区间  "+stareIndex+"/"+endIndex+"  分数  "+score);
            lastIndex = endIndex;
        }
        if (dyc.size()>0){
            int score = dyc.get(dyc.size()-1).getInt("aos_score");
            eventRule.fndLog.add("排序区间  "+lastIndex+"/"+(size+1)+"  分数  "+score);
            sortWeightMap.put(lastIndex+"/"+(size+1),score);
        }
    }

    //升序排序
    public List<String>  sortAsc(Map<String, BigDecimal> historySalesMap) {
        return  historySalesMap
                .entrySet()
                .stream()
                .sorted((e1,e2)->-e2.getValue().compareTo(e1.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    //降序排序
    public List<String> sortDesc(Map<String, BigDecimal> historySalesMap) {
        return  historySalesMap
                .entrySet()
                .stream()
                .sorted((e1,e2)->e2.getValue().compareTo(e1.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 根据区间计算每个排序方式分数
     * @param sortItemList  排序后的商品id
     * @param weight    权重
     * @param sortName  排序名称
     */
    private void calIntervalScore ( List<String> sortItemList,BigDecimal weight,String sortName){
        for (DynamicObject itemInfoe : eventRule.itemInfoes) {
            String itemId = itemInfoe.getString("id");
            String number = itemInfoe.getString("number");
            int index = sortItemList.indexOf(itemId);
            //区间分数
            int itemScore = 0;
            for (Map.Entry<String, Integer> entry : sortWeightMap.entrySet()) {
                String[] split = entry.getKey().split("/");
                if (index >= Integer.parseInt(split[0]) && index < Integer.parseInt(split[1])) {
                    itemScore = entry.getValue();
                    break;
                }
            }
            BigDecimal score = weight.multiply( new BigDecimal(itemScore));
            StringJoiner str = new StringJoiner(" , ");
            str.add("编码: "+number);
            str.add("区位: "+index);
            str.add("区间分数： "+itemScore);
            str.add("权重： "+weight);
            str.add("排序方式： "+sortName);
            str.add("分数:  "+score);

            eventRule.fndLog.add(str.toString());
            BigDecimal allScore = eventRule.weightMap.getOrDefault(itemId, BigDecimal.ZERO).add(score);
            eventRule.weightMap.put(itemId, allScore);
        }
    }
}
