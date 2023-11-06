package mkt.progress.report;

import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndWebHook;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

public class aos_mkt_listing_rpt extends AbstractFormPlugin implements RowClickEventListener, HyperLinkClickListener {
	private static final String DB_MKT = "aos.mkt";
	private static final String KEY_REQ = "seq";
	//private static final String KEY_SON = "son";
	//private static final String KEY_Sal = "sal";
	//private static final String KEY_Design = "design";

	@Override
	public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
		int RowIndex = hyperLinkClickEvent.getRowIndex();
		String FieldName = hyperLinkClickEvent.getFieldName();
		if ("aos_listingbill".equals(FieldName)) {
			Object aos_listingbillid = this.getModel().getValue("aos_listingbillid", RowIndex);
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_listing_req", aos_listingbillid);
		}
		else if ("aos_sonbill".equals(FieldName)) {
			Object aos_sonbillid = this.getModel().getValue("aos_sonbillid", RowIndex);
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_listing_son", aos_sonbillid);
		}
		else if ("aos_salbill".equals(FieldName)) {
			Object aos_salbillid = this.getModel().getValue("aos_salbillid", RowIndex);
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_listing_sal", aos_salbillid);
		}
		else if ("aos_designbill".equals(FieldName)) {
			Object aos_designbillid = this.getModel().getValue("aos_designbillid", RowIndex);
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_designreq", aos_designbillid);
		}
	}
	
	@Override
	public void registerListener(EventObject e) {
		super.registerListener(e);
		// 给工具栏加监听事件
		this.addItemClickListeners("aos_toolbarap");
		// 给单据体加监听
		EntryGrid aos_entryentity = this.getControl("aos_entryentity");
		aos_entryentity.addRowClickListener(this);
		aos_entryentity.addHyperClickListener(this);
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		try {
			if ("aos_query".equals(Control))
				aos_query();// 查询
			if ("aos_clear".equals(Control))
				aos_clear();
		} catch (FndError fndError) {
			fndError.show(getView(),FndWebHook.urlMms);
		} catch (Exception ex) {
			FndError.showex(getView(), ex, FndWebHook.urlMms);
		}
	}

	private void aos_clear() {
		this.getModel().setValue("aos_listingbill_q", null);
		this.getModel().setValue("aos_sonbill_q", null);
		this.getModel().setValue("aos_salbill_q", null);
		this.getModel().setValue("aos_designbill_q", null);
		this.getModel().setValue("aos_item",null);
	}
	private void aos_query(){
		//获取四个流程的单号
		Map<String, List<String>> map_no = queryNoByItem();
		queryByNo(map_no);
	}
	/** 通过物料查询 **/
	private Map<String, List<String>> queryNoByItem(){
		Map<String,List<String>> map_re = new HashMap<>();
		DynamicObject dy_item = this.getModel().getDataEntity(true).getDynamicObject("aos_item");
		if (dy_item !=null){
			String itemId = dy_item.getString("id");
			//需求表id
			QFilter filter_item = new QFilter("aos_entryentity.aos_itemid","=",itemId);
			List<String> list_req = QueryServiceHelper.query("aos_mkt_listing_req", "billno", new QFilter[]{filter_item}).stream()
					.map(dy -> dy.getString("billno"))
					.distinct()
					.collect(Collectors.toList());
			map_re.put(KEY_REQ,list_req);
		}
		return map_re;
	}
	/** 通过单号查询 **/
	private void queryByNo(Map<String,List<String>> map_no) {
		// 清除界面原有数据
		this.getModel().deleteEntryData("aos_entryentity");
		int i = 0;
		// 获取界面查询条件
		Object aos_listingbill_q = this.getModel().getValue("aos_listingbill_q");
		Object aos_sonbill_q = this.getModel().getValue("aos_sonbill_q");
		Object aos_salbill_q = this.getModel().getValue("aos_salbill_q");
		Object aos_designbill_q = this.getModel().getValue("aos_designbill_q");
		String sql  = "select tamlr.fbillno aos_listingbill,  " + 
				"tamlr.fk_aos_requireby aos_listinguser, "
				+ "tamlr.FId aos_listingbillid," + 
				"tamls.fbillno aos_sonbill,  " + 
				"tamls.fk_aos_user aos_sonuser, " + 
				"tamls.FId aos_sonbillid, " + 
				"tamlr.fk_aos_status aos_sonstatus, " + 
				"tasal.fbillno aos_salbill, " + 
				"tasal.fk_aos_status aos_salstatus, " + 
				"tasal.FId aos_salbillid, " + 
				"tasal.fk_aos_user aos_saluser," +
				"tamd.fbillno aos_designbill," +
				"tamd.fk_aos_user aos_designuser," + 
				"tamd.FId aos_designbillid," + 
				"tamd.fk_aos_status aos_designstatus " + 
				"from tk_aos_mkt_listing_req tamlr  " + 
				"left join tk_aos_mkt_listing_son tamls on  " + 
				"tamlr.FId  = tamls.fk_aos_sourceid  " + 
				"left join tk_aos_mkt_listing_sal tasal on " + 
				"tasal.fk_aos_sourceid  = tamls.FId  "
				+ "left join tk_aos_mkt_designreq tamd on "
				+ "tamd.fk_aos_sourceid  = tamlr.FId  " + 
				"where 1=1 ";
		// 拼接查询条件有三种情况
		if (!Cux_Common_Utl.IsNull(aos_listingbill_q)) {
			// 1.根据需求单查询
			sql = sql + " and tamlr.fbillno like '%"+aos_listingbill_q+"%' ";
		}
		else if (!Cux_Common_Utl.IsNull(aos_sonbill_q)) {
			// 2.根据文案单查询
			sql = sql + " and tamls.fbillno like '%"+aos_sonbill_q+"%' ";
		}
		else if (!Cux_Common_Utl.IsNull(aos_salbill_q)) {
			// 3.根据销售确认单查询
			sql = sql + " and tasal.fbillno like '%"+aos_salbill_q+"%' ";
		}
		else if (!Cux_Common_Utl.IsNull(aos_designbill_q)) {
			// 4.设计需求表
			sql = sql + " and tamd.fbillno like '%"+aos_designbill_q+"%' ";
		}
		
		// 5.最后拼接 控制冗余
		sql = sql + "and ((exists (select 1 " + 
				"from tk_aos_mkt_listing_son_r tamlsr " + 
				"where 1 = 1 " + 
				"and tamlsr.FId = tamls.FId " + 
				"and tamlsr.fk_aos_segment3_r = tamd.fk_aos_groupseg) " + 
				"or (tamls.FId is null) " + 
				"	or (not exists (select 1 " + 
				"from tk_aos_mkt_listing_son_r tamlsr " + 
				"where 1 = 1 " + 
				"	and tamlsr.FId = tamls.FId " + 
				"	and tamlsr.fk_aos_segment3_r = tamd.fk_aos_groupseg) " + 
				"	and tamls.FId is not null " + 
				"	) " +  // and tamd.FId is not null
				")   " + 
				")";
		
		DataSet aos_mkt_listing_reqS = DB.queryDataSet("aos_mkt_listing_rpt.aos_query", DBRoute.of(DB_MKT), sql,
				null);

		while (aos_mkt_listing_reqS.hasNext()) {
			Row aos_mkt_listing_req = aos_mkt_listing_reqS.next();
			if (map_no.containsKey(KEY_REQ)) {
				String reqBill = aos_mkt_listing_req.getString("aos_listingbill");
				if (!map_no.get(KEY_REQ).contains(reqBill)) {
					continue;
				}
			}
			this.getModel().createNewEntryRow("aos_entryentity");
			this.getModel().setValue("aos_listingbill", aos_mkt_listing_req.get("aos_listingbill"), i);// 需求单号
			this.getModel().setValue("aos_listinguser", aos_mkt_listing_req.get("aos_listinguser"), i);// 需求单申请人
			this.getModel().setValue("aos_sonbill", aos_mkt_listing_req.get("aos_sonbill"), i);// 文案需求单
			this.getModel().setValue("aos_sonstatus", aos_mkt_listing_req.get("aos_sonstatus"), i);// 文案节点
			this.getModel().setValue("aos_sonuser", aos_mkt_listing_req.get("aos_sonuser"), i);// 文案当前操作人
			this.getModel().setValue("aos_salbill", aos_mkt_listing_req.get("aos_salbill"), i);
			this.getModel().setValue("aos_salstatus", aos_mkt_listing_req.get("aos_salstatus"), i);
			this.getModel().setValue("aos_saluser", aos_mkt_listing_req.get("aos_saluser"), i);
			this.getModel().setValue("aos_listingbillid", aos_mkt_listing_req.get("aos_listingbillid"), i);
			this.getModel().setValue("aos_sonbillid", aos_mkt_listing_req.get("aos_sonbillid"), i);
			this.getModel().setValue("aos_salbillid", aos_mkt_listing_req.get("aos_salbillid"), i);
			this.getModel().setValue("aos_designbill", aos_mkt_listing_req.get("aos_designbill"), i);
			this.getModel().setValue("aos_designstatus", aos_mkt_listing_req.get("aos_designstatus"), i);
			this.getModel().setValue("aos_designuser", aos_mkt_listing_req.get("aos_designuser"), i);
			this.getModel().setValue("aos_designbillid", aos_mkt_listing_req.get("aos_designbillid"), i);
			i++;
		}
		aos_mkt_listing_reqS.close();
	}
}