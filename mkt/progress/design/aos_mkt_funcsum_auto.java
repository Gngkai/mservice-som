package mkt.progress.design;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

import common.Cux_Common_Utl;
import common.fnd.FndHistory;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.entity.EntityMetadataCache;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import mkt.progress.ProgressUtil;
import mkt.progress.listing.aos_mkt_listingmin_bill;
import sal.synciface.imp.aos_sal_import_pub;

/** Listing优化需求表小语种 海外编辑确认:功能图 状态 三个工作日后自动提交 **/
public class aos_mkt_funcsum_auto extends AbstractTask {
	/** 系统管理员 **/
	public final static String system = Cux_Common_Utl.SYSTEM;

	@Override
	public void execute(RequestContext arg0, Map<String, Object> arg1) throws KDException {
		do_operate();
	}

	private void do_operate() {
		QFilter QfilterStatus = new QFilter("aos_status", QCP.in, new String[] { "海外编辑确认:功能图"});
		QFilter QfilterSmall = new QFilter("aos_orgsmall", "=","" );
		QFilter[] filters = new QFilter[] { QfilterStatus, QfilterSmall };
		DynamicObjectCollection aos_mkt_listing_minS = QueryServiceHelper.query("aos_mkt_listing_min",
				"id,aos_funcdate" + ",aos_status,aos_orgid.number aos_orgnumber", filters);
		LocalDate now = LocalDate.now();
		for (DynamicObject aos_mkt_listing_min : aos_mkt_listing_minS) {
			Object id = aos_mkt_listing_min.get("id");
			Date aos_funcdate = aos_mkt_listing_min.getDate("aos_funcdate");
			if (aos_funcdate==null)
				continue;
			LocalDate funcDate = aos_funcdate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
			long day = now.toEpochDay() - funcDate.toEpochDay();
			if (day < 3)
				continue; // 只处理三个工作日以上单据
			String aos_status = aos_mkt_listing_min.getString("aos_status");
			DynamicObject aos_mkt_listing_minold = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_listing_min");
			String aos_orgnumber = aos_mkt_listing_min.getString("aos_orgnumber");
			aos_mkt_listing_minold.set("aos_submitter","A");	//设置功能图提交节点人为系统提交
			if ("海外编辑确认:功能图".equals(aos_status)) {
				if ("IT".equals(aos_orgnumber)) {
					GenerateFuncSummary(id, "IT");// 插入功能图翻译台账
					GenerateOsSmall(id, "RO");// 同时生成小站海外编辑确认:功能图 的listing优化需求小语种
				}
				aos_mkt_listing_minold.set("aos_status", "结束");
				aos_mkt_listing_minold.set("aos_user", system);
				FndHistory.Create(aos_mkt_listing_minold,"提交",aos_status);
				OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
						new DynamicObject[] { aos_mkt_listing_minold }, OperateOption.create());
				aos_mkt_listingmin_bill.setErrorList(aos_mkt_listing_minold);
			} /*
				 * else if ("小站海外编辑确认:功能图".equals(aos_status)) { if ("ES".equals(aos_orgnumber))
				 * aos_orgnumber = "PT"; else if ("IT".equals(aos_orgnumber)) aos_orgnumber =
				 * "RO"; GenerateFuncSummary(id, aos_orgnumber);// 插入功能图翻译台账
				 * aos_mkt_listing_minold.set("aos_status", "结束");
				 * aos_mkt_listing_minold.set("aos_user", system);
				 * FndHistory.Create(aos_mkt_listing_minold,"提交",aos_status);
				 * OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min", new
				 * DynamicObject[] { aos_mkt_listing_minold }, OperateOption.create()); }
				 */

		}
	}

	private void GenerateFuncSummary(Object id, String aos_orgnumber) {
		// 数据层
		DynamicObject aos_mkt_listing_minold = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_listing_min");
		Object billno = aos_mkt_listing_minold.get("billno");
		Object aos_orgid = aos_mkt_listing_minold.get("aos_orgid");
		DynamicObjectCollection aos_entryentityS = aos_mkt_listing_minold.getDynamicObjectCollection("aos_entryentity");
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			DynamicObject aos_mkt_funcsumdata = BusinessDataServiceHelper.newDynamicObject("aos_mkt_funcsumdata");
			aos_mkt_funcsumdata.set("aos_orgid", aos_orgid);
			aos_mkt_funcsumdata.set("aos_itemid", aos_entryentity.get("aos_itemid"));
			aos_mkt_funcsumdata.set("aos_sourcebill", billno);
			aos_mkt_funcsumdata.set("aos_creationdate", new Date());
			aos_mkt_funcsumdata.set("aos_eng", "N");
			aos_mkt_funcsumdata.set("aos_sourceid", id);
			OperationServiceHelper.executeOperate("save", "aos_mkt_funcsumdata",
					new DynamicObject[] { aos_mkt_funcsumdata }, OperateOption.create());
		}
	}

	private void GenerateOsSmall(Object id, String aos_orgnumber) {
		DynamicObject aos_mkt_listing_minold = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_listing_min");
		Object aos_orgsmall = aos_sal_import_pub.get_import_id(aos_orgnumber, "bd_country");
		DynamicObject aos_mkt_listing_min = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_min");
		Object aos_user = null;
		QFilter filter = new QFilter("number", "=", "024044");
		DynamicObject bos_user = QueryServiceHelper.queryOne("bos_user", "id", new QFilter[] { filter });
		if (bos_user != null)
			aos_user = bos_user.get("id");
		aos_mkt_listing_min.set("billno", aos_mkt_listing_minold.get("billno"));
		aos_mkt_listing_min.set("aos_user", aos_user);
		aos_mkt_listing_min.set("aos_sourcetype", aos_mkt_listing_minold.get("aos_sourcetype"));
		aos_mkt_listing_min.set("aos_status", "小站海外编辑确认:功能图");
		aos_mkt_listing_min.set("aos_funcdate", new Date());
		aos_mkt_listing_min.set("aos_requireby", aos_mkt_listing_minold.get("aos_requireby"));
		aos_mkt_listing_min.set("aos_organization1", aos_mkt_listing_minold.get("aos_organization1"));
		aos_mkt_listing_min.set("aos_organization2", aos_mkt_listing_minold.get("aos_organization2"));
		aos_mkt_listing_min.set("aos_requiredate", aos_mkt_listing_minold.get("aos_requiredate"));
		aos_mkt_listing_min.set("aos_demandate", aos_mkt_listing_minold.get("aos_demandate"));
		aos_mkt_listing_min.set("aos_type", aos_mkt_listing_minold.get("aos_type"));
		aos_mkt_listing_min.set("aos_source", aos_mkt_listing_minold.get("aos_source"));
		aos_mkt_listing_min.set("aos_importance", aos_mkt_listing_minold.get("aos_importance"));
		aos_mkt_listing_min.set("aos_designer", aos_mkt_listing_minold.get("aos_designer"));
		aos_mkt_listing_min.set("aos_editor", aos_mkt_listing_minold.get("aos_editor"));
		aos_mkt_listing_min.set("aos_editormin", aos_mkt_listing_minold.get("aos_editormin"));
		aos_mkt_listing_min.set("aos_oseditor", aos_mkt_listing_minold.get("aos_oseditor"));
		aos_mkt_listing_min.set("aos_orgid", aos_mkt_listing_minold.get("aos_orgid"));
		aos_mkt_listing_min.set("aos_orgsmall", aos_mkt_listing_minold.get("aos_orgsmall"));
		aos_mkt_listing_min.set("aos_osconfirmlov", aos_mkt_listing_minold.get("aos_osconfirmlov"));
		aos_mkt_listing_min.set("aos_funconfirm", aos_mkt_listing_minold.get("aos_funconfirm"));
		aos_mkt_listing_min.set("aos_orignbill", aos_mkt_listing_minold.get("aos_orignbill"));
		aos_mkt_listing_min.set("aos_sourceid", aos_mkt_listing_minold.get("aos_sourceid"));
		aos_mkt_listing_min.set("aos_orgsmall", aos_orgsmall);

		DynamicObjectCollection aos_entryentityS = aos_mkt_listing_minold.getDynamicObjectCollection("aos_entryentity");

		DynamicObjectCollection aos_entryentityNewS = aos_mkt_listing_min.getDynamicObjectCollection("aos_entryentity");

		for (DynamicObject aos_entryentity : aos_entryentityS) {
			DynamicObject aos_entryentityNew = aos_entryentityNewS.addNew();
			aos_entryentityNew.set("aos_itemid", aos_entryentity.get("aos_itemid"));
			aos_entryentityNew.set("aos_is_saleout",ProgressUtil.Is_saleout(aos_entryentity.getDynamicObject("aos_itemid").getPkValue()));
			aos_entryentityNew.set("aos_require", aos_entryentity.get("aos_require"));
			aos_entryentityNew.set("aos_case", aos_entryentity.get("aos_case"));

			DynamicObjectCollection aos_attribute = aos_entryentityNew.getDynamicObjectCollection("aos_attribute");
			aos_attribute.clear();
			DynamicObjectCollection aos_attributefrom = aos_entryentity.getDynamicObjectCollection("aos_attribute");
			DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
			DynamicObject tempFile = null;
			for (DynamicObject d : aos_attributefrom) {
				tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
				aos_attribute.addNew().set("fbasedataid", tempFile);
			}

			aos_entryentityNew.set("aos_write", aos_entryentity.get("aos_write"));
			aos_entryentityNew.set("aos_opt", aos_entryentity.get("aos_opt"));
			aos_entryentityNew.set("aos_pic", aos_entryentity.get("aos_pic"));
			aos_entryentityNew.set("aos_subtitle", aos_entryentity.get("aos_subtitle"));
			aos_entryentityNew.set("aos_title", aos_entryentity.get("aos_title"));
			aos_entryentityNew.set("aos_keyword", aos_entryentity.get("aos_keyword"));
			aos_entryentityNew.set("aos_other", aos_entryentity.get("aos_other"));
			aos_entryentityNew.set("aos_etc", aos_entryentity.get("aos_etc"));
			aos_entryentityNew.set("aos_segment3_r", aos_entryentity.get("aos_segment3_r"));
			aos_entryentityNew.set("aos_broitem_r", aos_entryentity.get("aos_broitem_r"));
			aos_entryentityNew.set("aos_itemname_r", aos_entryentity.get("aos_itemname_r"));
			aos_entryentityNew.set("aos_orgtext_r", ProgressUtil.getOrderOrg(aos_entryentity.getDynamicObject("aos_itemid").getPkValue()));

			DynamicObjectCollection aos_subentryentityNewS = aos_entryentityNew
					.getDynamicObjectCollection("aos_subentryentity");
			DynamicObject aos_subentryentity = aos_entryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
			DynamicObject aos_subentryentityNew = aos_subentryentityNewS.addNew();
			aos_subentryentityNew.set("aos_segment3", aos_subentryentity.get("aos_segment3"));
			aos_subentryentityNew.set("aos_broitem", aos_subentryentity.get("aos_broitem"));
			aos_subentryentityNew.set("aos_itemname", aos_subentryentity.get("aos_itemname"));
			aos_subentryentityNew.set("aos_orgtext", aos_subentryentity.get("aos_orgtext"));
			aos_subentryentityNew.set("aos_reqinput", aos_subentryentity.get("aos_reqinput"));
			aos_subentryentityNew.set("aos_caseinput", aos_subentryentity.get("aos_caseinput"));

		}

		OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
				new DynamicObject[] { aos_mkt_listing_min }, OperateOption.create());

	}

}
