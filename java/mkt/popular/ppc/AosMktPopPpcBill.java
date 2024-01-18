package mkt.popular.ppc;

import common.CommonDataSom;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author create by ?
 * @since 2022/5/18 14:31
 * @version PPC操作平台数据源-表单插件
 */
public class AosMktPopPpcBill extends AbstractBillPlugIn {
    private static final String AOS_BUDGET = "aos_budget";
    private static final String AOS_BID = "aos_bid";
	private static final String AOS_ADJUSTBUDGET = "aos_adjustbudget";
	private static final String AOS_ADJUSTBID = "aos_adjustbid";
    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        generateDivid();
        try {
            fillCateSaleQty();
        } catch (Exception exception) {
            StringWriter sw = new StringWriter();
            exception.printStackTrace(new PrintWriter(sw));
            this.getView().showMessage(sw.toString());
            exception.printStackTrace();
        }
    }

    /**
     * 初始化本次出价/上次出价
     */
    private void generateDivid() {
        DynamicObjectCollection aosEntryentityS = this.getModel().getEntryEntity("aos_entryentity");
        int size = aosEntryentityS.size();
        for (int i = 0; i < size; i++) {
            BigDecimal aosLastbid = (BigDecimal)this.getModel().getValue("aos_lastbid", i);
            BigDecimal aosBid = (BigDecimal)this.getModel().getValue("aos_bid", i);
            if (aosLastbid.compareTo(BigDecimal.ZERO) != 0) {
                this.getModel().setValue("aos_divid", aosBid.divide(aosLastbid, 2, RoundingMode.HALF_UP), i);
            }
        }
    }

    /**
     * PPC预算金额汇总/平均出价 （大类维度）
     */
    public void fillCateSaleQty() {
        // 获取 明细单据体
        DynamicObjectCollection detailDy =
            this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity");
        if (detailDy.size() == 0) {
            return;
        }
        // 获取 预算金额单据体
        DynamicObjectCollection cateDy =
            this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity1");
        // 获取所有的产品大类
        Map<String, String> mapCate = CommonDataSom.getCate(1, false);
        // 获取大类的名称
        List<String> listCateName = new ArrayList<>(mapCate.keySet());
        // 大类的预算金额
        Map<String, BigDecimal> mapCateBudget = new HashMap<>(16);
        // 大类的总出价
        Map<String, BigDecimal> mapCateBid = new HashMap<>(16);
        // 每个大类的物料数量
        Map<String, Integer> mapCateItem = new HashMap<>(16);
        listCateName.forEach(e -> {
            mapCateBudget.put(e, BigDecimal.valueOf(0));
            mapCateBid.put(e, BigDecimal.valueOf(0));
            mapCateItem.put(e, 0);
        });
        // 获取明细里面的物料
        List<String> listItem =
            detailDy.stream().map(e -> e.getDynamicObject("aos_itemid").getString("id")).collect(Collectors.toList());
        // 查找物料对应的大类名称
        Map<String, String> mapItemCateName = getItemCateName(listItem);
        // 遍历明细数据，计算 总的预算金额、总的出价，每个大类的物料数
        detailDy.stream().filter(e -> {
            return e.get("aos_itemid") != null && "AVAILABLE".equals(e.get("aos_groupstatus"));
        }).forEach(e -> {
            String itemId = e.getDynamicObject("aos_itemid").getString("id");
            String cate = mapItemCateName.get(itemId);
            if (mapCate.containsKey(cate)) {
                BigDecimal bigTemp;
                // 预算金额
                if (!"".equals(e.get(AOS_BUDGET))) {
                    bigTemp = mapCateBudget.get(cate);
                    bigTemp = bigTemp.add(e.getBigDecimal("aos_budget"));
                    mapCateBudget.put(cate, bigTemp);
                }
                // 出价
                if (!"".equals(e.get(AOS_BID))) {
                    bigTemp = mapCateBid.get(cate);
                    bigTemp = bigTemp.add(e.getBigDecimal("aos_bid"));
                    mapCateBid.put(cate, bigTemp);
                    // 物料数量
                    int itemqty = mapCateItem.get(cate);
                    itemqty++;
                    mapCateItem.put(cate, itemqty);
                }
            }
        });
        listCateName.forEach(e -> {
            DynamicObject dyNew = cateDy.addNew();
            // 预算汇总
            BigDecimal bigAllbudget = mapCateBudget.get(e);
            // 计算平均出价
            BigDecimal bigAllbid = mapCateBid.get(e); // 总出价
            int itemqty = mapCateItem.get(e); // 物料数
            BigDecimal bigBid = BigDecimal.valueOf(0);
            if (itemqty != 0) {
                bigBid = bigAllbid.divide(BigDecimal.valueOf(itemqty), 5, RoundingMode.HALF_UP);
            }
            // 销量
            BigDecimal bigSale = new BigDecimal(0);
            if (bigBid.compareTo(BigDecimal.ZERO) != 0) {
                bigSale = bigAllbudget.divide(bigBid, 5, RoundingMode.HALF_UP);
            }
            dyNew.set("aos_cate", e);
            dyNew.set("aos_sum_budget", bigAllbudget);
            dyNew.set("aos_average_bid", bigBid);
            dyNew.set("aos_result", bigSale);
        });
        this.getView().updateView("aos_entryentity1");
    }

    /**
     * 获取物料的大类名称
	 */
    public Map<String, String> getItemCateName(List<String> listItem) {
        QFilter qfItem = new QFilter("material", QFilter.in, listItem);
        Map<String, String> mapRe = new HashMap<>(16);
        DynamicObjectCollection dycGroup = QueryServiceHelper.query("bd_materialgroupdetail",
            "material as id,group.name as name", new QFilter[] {qfItem});
		dycGroup.forEach(e -> {
			mapRe.put(e.getString("id"), e.getString("name").split(",")[0]);
        });
        return mapRe;
    }

    @Override
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        e.setCheckDataChange(false);
    }

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs args) {
        String operateKey = args.getOperateKey();
        if (AOS_ADJUSTBUDGET.equals(operateKey)) {
            // 出价调整
            adjustPrice(AOS_ADJUSTBUDGET);
        }

        if (AOS_ADJUSTBID.equals(operateKey)) {
            // 预算调整
            adjustPrice(AOS_ADJUSTBID);
        }
    }

    private void adjustPrice(String field) {
        // 获取选中的单据体
        EntryGrid entryGrid = this.getControl("aos_entryentity");
        int[] selectRows = entryGrid.getSelectRows();
        if (selectRows.length == 0) {
            this.getView().showTipNotification("请至少选择一行");
            return;
        }
        // 调整字段
        BigDecimal aosAdjust = (BigDecimal)this.getModel().getValue("aos_adjust");
        if (aosAdjust == null)
		{
			aosAdjust = BigDecimal.ZERO;
		}
		aosAdjust = aosAdjust.setScale(2, RoundingMode.HALF_UP);
        Calendar cal = Calendar.getInstance();
        // 判断调整哪一列
        String colName = "";
        if (AOS_ADJUSTBUDGET.equals(field)) {
            colName = "aos_budget";
        } else if (AOS_ADJUSTBID.equals(field)) {
            colName = "aos_bid";
        }
        // 遍历单据体选中的行
		for (int index : selectRows) {
			BigDecimal value = (BigDecimal) this.getModel().getValue(colName, index);
			if (value == null) {
				value = BigDecimal.ZERO;
			}
			// 比较出价调整的值和原来的值是否相等 如果不相等 修改最后一次出价调整日期为今日
			value = value.setScale(2, RoundingMode.HALF_UP);
			if ("aos_adjustbid".equals(field) && value.compareTo(aosAdjust) != 0) {
				this.getModel().setValue("aos_lastpricedate", cal.getTime(), index);
			}
			// 设置出价 或预算
			this.getModel().setValue(colName, aosAdjust, index);
		}
    }

    @Override
    public void afterLoadData(EventObject e) {
        // 清空调整字段
        this.getModel().setValue("aos_adjust", 0);
    }
}
