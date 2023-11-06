package mkt.popular.ppc;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import sal.sche.aos_sal_sche_pub.aos_sal_sche_pvt;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author create by ?
 * @date 2022/5/18 14:31
 * @action PPC操作平台数据源
 */
public class aos_mkt_popular_ppc_for extends AbstractBillPlugIn {
	@Override
	public void afterBindData(EventObject e) {
		super.afterBindData(e);
		GenerateDivid();
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
	private void GenerateDivid() {
		DynamicObjectCollection aos_entryentityS = this.getModel().getEntryEntity("aos_entryentity");
		int size = aos_entryentityS.size();
		for (int i = 0; i < size; i++) {
			BigDecimal aos_lastbid = (BigDecimal) this.getModel().getValue("aos_lastbid",i);
			BigDecimal aos_bid = (BigDecimal) this.getModel().getValue("aos_bid",i);
			if (aos_lastbid.compareTo(BigDecimal.ZERO) != 0) 
				this.getModel().setValue("aos_divid", aos_bid.divide(aos_lastbid, 2, BigDecimal.ROUND_HALF_UP), i);
		}
	}

	/**
	 * PPC预算金额汇总/平均出价 （大类维度）
	 */
	public void fillCateSaleQty() {
		// 获取 明细单据体
		DynamicObjectCollection detailDy = this.getModel().getDataEntity(true)
				.getDynamicObjectCollection("aos_entryentity");
		if (detailDy.size() == 0)
			return;
		// 获取 预算金额单据体
		DynamicObjectCollection cateDy = this.getModel().getDataEntity(true)
				.getDynamicObjectCollection("aos_entryentity1");
		// 获取所有的产品大类
		Map<String, String> map_cate = aos_sal_sche_pvt.getCate(1, false);
		// 获取大类的名称
		List<String> listCateName = map_cate.entrySet().stream().map(e -> e.getKey()).collect(Collectors.toList());
		// 大类的预算金额
		Map<String, BigDecimal> map_cateBudget = new HashMap<>();
		// 大类的总出价
		Map<String, BigDecimal> map_cateBid = new HashMap<>();
		// 每个大类的物料数量
		Map<String, Integer> map_cateItem = new HashMap<>();
		listCateName.stream().forEach(e -> {
			map_cateBudget.put(e, BigDecimal.valueOf(0));
			map_cateBid.put(e, BigDecimal.valueOf(0));
			map_cateItem.put(e, 0);
		});
		// 获取明细里面的物料
		List<String> listItem = detailDy.stream().map(e -> e.getDynamicObject("aos_itemid").getString("id"))
				.collect(Collectors.toList());
		// 查找物料对应的大类名称
		Map<String, String> map_itemCateName = getItemCateName(listItem);
		// 遍历明细数据，计算 总的预算金额、总的出价，每个大类的物料数
		detailDy.stream().filter(e -> {
			return e.get("aos_itemid") != null && e.get("aos_groupstatus").equals("AVAILABLE");
		}).forEach(e -> {
			String itemId = e.getDynamicObject("aos_itemid").getString("id");
			String cate = map_itemCateName.get(itemId);
			if (map_cate.containsKey(cate)) {
				BigDecimal big_temp = BigDecimal.ZERO;
				// 预算金额
				if (!e.get("aos_budget").equals("")) {
					big_temp = map_cateBudget.get(cate);
					big_temp = big_temp.add(e.getBigDecimal("aos_budget"));
					map_cateBudget.put(cate, big_temp);
				}
				// 出价
				if (!e.get("aos_bid").equals("")) {
					big_temp = map_cateBid.get(cate);
					big_temp = big_temp.add(e.getBigDecimal("aos_bid"));
					map_cateBid.put(cate, big_temp);
					// 物料数量
					int itemqty = map_cateItem.get(cate);
					itemqty++;
					map_cateItem.put(cate, itemqty);
				}
			}
		});
		listCateName.stream().forEach(e -> {
			DynamicObject dy_new = cateDy.addNew();
			// 预算汇总
			BigDecimal big_allbudget = map_cateBudget.get(e);
			// 计算平均出价
			BigDecimal big_allbid = map_cateBid.get(e); // 总出价
			int itemqty = map_cateItem.get(e); // 物料数
			BigDecimal big_bid = BigDecimal.valueOf(0);
			if (itemqty != 0)
				big_bid = big_allbid.divide(BigDecimal.valueOf(itemqty), 5, BigDecimal.ROUND_HALF_UP);
			// 销量
			BigDecimal big_sale = new BigDecimal(0);
			if (big_bid.compareTo(BigDecimal.ZERO) != 0)
				big_sale = big_allbudget.divide(big_bid, 5, BigDecimal.ROUND_HALF_UP);
			dy_new.set("aos_cate", e);
			dy_new.set("aos_sum_budget", big_allbudget);
			dy_new.set("aos_average_bid", big_bid);
			dy_new.set("aos_result", big_sale);
		});
		this.getView().updateView("aos_entryentity1");
	}

	/**
	 * 获取物料的大类名称
	 * 
	 * @param list_item
	 */
	public Map<String, String> getItemCateName(List<String> list_item) {
		QFilter qf_item = new QFilter("material", QFilter.in, list_item);
		Map<String, String> map_re = new HashMap<>();
		DynamicObjectCollection dyc_group = QueryServiceHelper.query("bd_materialgroupdetail",
				"material as id,group.name as name", new QFilter[] { qf_item });
		dyc_group.stream().forEach(e -> {
			map_re.put(e.getString("id"), e.getString("name").split(",")[0]);
		});
		return map_re;
	}

	@Override
	public void beforeClosed(BeforeClosedEvent e) {
		super.beforeClosed(e);
		e.setCheckDataChange(false);
	}


	@Override
	public void afterDoOperation(AfterDoOperationEventArgs args) {
		String operateKey = args.getOperateKey();
		if ("aos_adjustbudget".equals(operateKey)) {
			// 出价调整
			adjustPrice("aos_adjustbudget");
		}

		if ("aos_adjustbid".equals(operateKey)) {
			// 预算调整
			adjustPrice("aos_adjustbid");
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
		BigDecimal aos_adjust = (BigDecimal) this.getModel().getValue("aos_adjust");
		if (aos_adjust == null) aos_adjust = BigDecimal.ZERO;
		aos_adjust = aos_adjust.setScale(2, BigDecimal.ROUND_HALF_UP);

		Calendar cal = Calendar.getInstance();
		// 判断调整哪一列
		String colName = "";
		if ("aos_adjustbudget".equals(field)) {
			colName = "aos_budget";
		} else if ("aos_adjustbid".equals(field)) {
			colName = "aos_bid";
		}
		// 遍历单据体选中的行
		for (int i = 0; i < selectRows.length; i++) {
			int index = selectRows[i];
			BigDecimal value = (BigDecimal) this.getModel().getValue(colName, index);
			if(value == null) value = BigDecimal.ZERO;
			// 比较出价调整的值和原来的值是否相等 如果不相等 修改最后一次出价调整日期为今日
			value = value.setScale(2, BigDecimal.ROUND_HALF_UP);
			if ("aos_adjustbid".equals(field) && value.compareTo(aos_adjust) != 0) {
				this.getModel().setValue("aos_lastpricedate", cal.getTime(), index);
			}

			// 设置出价 或预算
			this.getModel().setValue(colName, aos_adjust, index);
		}
	}


	@Override
	public void afterLoadData(EventObject e) {
		// 清空调整字段
		this.getModel().setValue("aos_adjust", 0);
	}
}
