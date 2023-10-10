package mkt.progress.design;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.google.common.base.Joiner;
import common.Cux_Common_Utl;
import common.sal.util.SalUtil;
import common.sal.util.SaveUtils;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.DBServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.fi.bd.util.QFBuilder;
import mkt.progress.design.functDiagram.functDiagramForm;
import mkt.progress.iface.iteminfo;

public class aos_mkt_funcreq_init extends AbstractTask {

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		do_operate();
	}

	public static void do_operate() {
		List<String> createProductNo = GenerateFuncReq();// 获取已存在的产品号
		QFilter filter_prod = new QFilter("aos_productno", "!=", null).and("aos_productno", "!=", "");
		QFilter[] filters_prod = new QFilter[] { filter_prod };
		String SelectColumn = "aos_productno,id";
		DataSet bd_materialS = QueryServiceHelper.queryDataSet("aos_mkt_funcreq_init.do_operate", "bd_material",
				SelectColumn, filters_prod, null);
		String[] GroupBy = new String[] { "aos_productno" };
		bd_materialS = bd_materialS.groupBy(GroupBy).min("id").finish();
		List<DynamicObject> list_save = new ArrayList<>();
		while (bd_materialS.hasNext()) {
			Row bd_material = bd_materialS.next();
			String aos_productno = bd_material.getString("aos_productno");
			if (createProductNo.contains(aos_productno)) {
				continue;
			}
			// 下单国别与品类编辑
			Object ItemId = bd_material.get("id");
			Object aos_editordefualt = queryEditor(ItemId);
			// 数据初始化
			DynamicObject aos_mkt_functreq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_functreq");
			aos_mkt_functreq.set("aos_segment3", aos_productno);
			aos_mkt_functreq.set("aos_editor", aos_editordefualt);
			aos_mkt_functreq.set("aos_makeby", aos_editordefualt);
			aos_mkt_functreq.set("aos_picitem", ItemId);
			aos_mkt_functreq.set("billstatus", "A");

			DynamicObjectCollection aos_entryentity2S = aos_mkt_functreq.getDynamicObjectCollection("aos_entryentity2");

			// 同产品号下货号
			QFilter filter_productno = new QFilter("aos_productno", "=", aos_productno);// 未导入标记
			QFilter[] filters = new QFilter[] { filter_productno };
			DynamicObjectCollection bd_materialSameS = QueryServiceHelper.query("bd_material", "id", filters);
			List<String> list_orgtext_total = new ArrayList<>();
			for (DynamicObject bd_materialSame : bd_materialSameS) {
				Object item_id = bd_materialSame.get("id");
				List<String> list_orgtext = new ArrayList<>();
				DynamicObject material = BusinessDataServiceHelper.loadSingle(item_id, "bd_material");
				DynamicObjectCollection aos_contryentryS = material.getDynamicObjectCollection("aos_contryentry");
				// 获取所有国家品牌 字符串拼接 终止
				for (DynamicObject aos_contryentry : aos_contryentryS) {
					addItemOrg(aos_contryentry,item_id,list_orgtext,list_orgtext_total);
				}
				DynamicObject aos_entryentity2 = aos_entryentity2S.addNew();
				aos_entryentity2.set("aos_itemid",material);

				aos_entryentity2.set("aos_orgdetail", Joiner.on(";").join(list_orgtext));
			}
			aos_mkt_functreq.set("aos_orgtext", Joiner.on(";").join(list_orgtext_total));
			setIamgTable(aos_mkt_functreq);
			list_save.add(aos_mkt_functreq);
			SaveUtils.SaveEntity("aos_mkt_functreq",list_save,false);
		}
		bd_materialS.close();
		SaveUtils.SaveEntity("aos_mkt_functreq",list_save,true);
		GenerateFuncReqByCombinate(createProductNo);
	}

	private static List<String> GenerateFuncReq() {
		return QueryServiceHelper.query("aos_mkt_functreq", "aos_segment3", null)
				.stream()
				.filter(dy -> !Cux_Common_Utl.IsNull(dy.get("aos_segment3")))
				.map(dy -> dy.getString("aos_segment3"))
				.distinct()
				.collect(Collectors.toList());
	}

	/**
	 * 生成功能图通过组别数据
	 * @param createProductNo	已经生成的产品号
	 */
	public static void GenerateFuncReqByCombinate(List<String> createProductNo){
		DynamicObject[] dyc_combinate = queryCombinate(createProductNo);
		List<DynamicObject> list_save = new ArrayList<>(5000);
		for (DynamicObject dy_item : dyc_combinate) {
			String aos_productno = dy_item.getString("number");
			List<String> itemids = getItemids(dy_item);
			if (itemids.size()==0) {
				continue;
			}
			String ItemId = itemids.get(0);
			Object aos_editordefualt = queryEditor(ItemId);
			// 数据初始化
			DynamicObject aos_mkt_functreq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_functreq");
			aos_mkt_functreq.set("aos_segment3", aos_productno);
			aos_mkt_functreq.set("aos_editor", aos_editordefualt);
			aos_mkt_functreq.set("aos_makeby", aos_editordefualt);
			aos_mkt_functreq.set("aos_picitem", ItemId);
			aos_mkt_functreq.set("billstatus", "A");

			// 同产品号下货号
			DynamicObjectCollection aos_entryentity2S = aos_mkt_functreq.getDynamicObjectCollection("aos_entryentity2");

			List<String> list_orgtext_total = new ArrayList<>();
			for (String item_id : itemids) {
				List<String> list_orgtext = new ArrayList<>();
				DynamicObject material = BusinessDataServiceHelper.loadSingle(item_id, "bd_material");
				DynamicObjectCollection aos_contryentryS = material.getDynamicObjectCollection("aos_contryentry");
				// 获取所有国家品牌 字符串拼接 终止
				for (DynamicObject aos_contryentry : aos_contryentryS) {
					addItemOrg(aos_contryentry,item_id,list_orgtext,list_orgtext_total);
				}
				DynamicObject aos_entryentity2 = aos_entryentity2S.addNew();
				aos_entryentity2.set("aos_itemid", material);
				aos_entryentity2.set("aos_orgdetail", Joiner.on(";").join(list_orgtext));
			}
			aos_mkt_functreq.set("aos_orgtext", Joiner.on(";").join(list_orgtext_total));
			//设置图片页签
			setIamgTable(aos_mkt_functreq);

			list_save.add(aos_mkt_functreq);
			SaveUtils.SaveEntity("aos_mkt_functreq",list_save,false);
		}
		SaveUtils.SaveEntity("aos_mkt_functreq",list_save,true);
	}

	/**
	 * 查找所有的组合货号
	 * @param createProductNo 已经生成的产品号
	 * @return	查找所有的组合货号
	 */
	public static DynamicObject[] queryCombinate(List<String> createProductNo){
		QFBuilder builder = new QFBuilder();
		builder.add("number",QFilter.not_in,createProductNo);
		builder.add("aos_iscomb","=","1");
		StringJoiner str = new StringJoiner(",");
		str.add("id");
		str.add("number");
		str.add("aos_contryentry.aos_nationality");
		str.add("aos_submaterialentry.aos_submaterial");
		return BusinessDataServiceHelper.load("bd_material", str.toString(), builder.toArray());
	}

	/**
	 * 获取品类编辑
	 * @param ItemId id
	 * @return	获取品类编辑
	 */
	public static long queryEditor(Object ItemId){
		String category = (String) SalUtil.getCategoryByItemId(ItemId + "").get("name");
		String[] category_group = category.split(",");
		String AosCategory1 = null;
		String AosCategory2 = null;
		int category_length = category_group.length;
		if (category_length > 0)
			AosCategory1 = category_group[0];
		if (category_length > 1)
			AosCategory2 = category_group[1];
		long aos_editordefualt = 0;
		if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
			QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
			QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
			QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2 };
			String SelectStr = "aos_eng aos_editor,aos_designer";
			DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_proguser", SelectStr,
					filters_category);
			if (aos_mkt_proguser != null) {
				aos_editordefualt = aos_mkt_proguser.getLong("aos_editor");
			}
		}
		return aos_editordefualt;
	}

	/** 添加图片行 **/
	static 	List<String> list_lan = functDiagramForm.list_language;
	private static void setIamgTable(DynamicObject aos_mkt_functreq){
		//主标题
		LocaleString locale_title = new LocaleString();
		locale_title.setLocaleValue_zh_CN("主标题");
		locale_title.setLocaleValue_en("Main tTitle");
		LocaleString local_value = new LocaleString();
		local_value.setLocaleValue_zh_CN("作图备注");
		local_value.setLocaleValue_en("Note");

		//副标题
		LocaleString local_head = new LocaleString();
		local_head.setLocaleValue_en("Sub title1");
		local_head.setLocaleValue_zh_CN("副标题1");
		LocaleString local_value2 = new LocaleString();
		local_value2.setLocaleValue_zh_CN("正文1");
		local_value2.setLocaleValue_en("Text1");
		//1-6 的图片table
		for (int index=1;index<7;index++){
			DynamicObjectCollection dyc_ent = aos_mkt_functreq.getDynamicObjectCollection("aos_entity" + index);
			int seq = 1;
			for (int n=1;n<=list_lan.size();n++){
				DynamicObject dy_newRow = dyc_ent.addNew();
				dy_newRow.set("seq",seq);
				seq++;
				//语言
				dy_newRow.set("aos_language"+index,list_lan.get(n-1));
				//主标题
				dy_newRow.set("aos_head"+index,locale_title);
				//作图备注
				dy_newRow.set("aos_title"+index,local_value);
				//语言行
				dy_newRow.set("aos_lan"+index,list_lan.get(n-1));
				long pid = DBServiceHelper.genLongIds("tk_aos_mkt_functreq_r"+index,1)[0];
				dy_newRow.set("id",pid);
				dy_newRow.set("pid",0);
				//副标题和中文
				DynamicObject dy_subRow = dyc_ent.addNew();
				dy_subRow.set("seq",seq);
				seq++;
				dy_subRow.set("pid",pid);
				dy_subRow.set("aos_head"+index,local_head);
				dy_subRow.set("aos_title"+index,local_value2);
				dy_subRow.set("aos_lan"+index,list_lan.get(n-1));

			}
		}
	}

	/**判断物料的国别是否应该加入到下单国别 **/
	public static void addItemOrg (DynamicObject  aos_contryentry,Object item_id,List<String> list_org,List<String> list_allOrg){
		DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
		String aos_nationalitynumber = aos_nationality.getString("number");
		if ("IE".equals(aos_nationalitynumber))
			return;
		Object org_id = aos_nationality.get("id"); // ItemId
		int OsQty = iteminfo.GetItemOsQty(org_id, item_id);
		int SafeQty = iteminfo.GetSafeQty(org_id);
		if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
			return;
		if (!list_org.contains(aos_nationalitynumber)) {
			list_org.add(aos_nationalitynumber);
		}
		if (!list_allOrg.contains(aos_nationalitynumber)) {
			list_allOrg.add(aos_nationalitynumber);
		}
	}

	/** 获取组合货号下，第一个sku
	 * @return**/
	private static List<String> getItemids(DynamicObject dy_item){
		List<String> itemids = new ArrayList<>();
		for (DynamicObject dy_row : dy_item.getDynamicObjectCollection("aos_contryentry")) {
			for (DynamicObject dy_sub : dy_row.getDynamicObjectCollection("aos_submaterialentry")) {
				if (dy_sub.get("aos_submaterial")!=null) {
					String item = dy_sub.getDynamicObject("aos_submaterial").getString("id");
					if (!itemids.contains(item)) {
						itemids.add(item);
					}
				}
			}
		}
		return itemids;
	}
}