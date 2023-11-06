package mkt.act.month;

import java.util.ArrayList;
import java.util.Date;
import java.util.EventObject;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.field.BasedataEdit;
import kd.bos.form.field.events.BeforeF7SelectEvent;
import kd.bos.form.field.events.BeforeF7SelectListener;
import kd.bos.list.ListShowParameter;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

public class aos_mkt_actmonth_bill extends AbstractBillPlugIn implements RowClickEventListener, BeforeF7SelectListener {

	@Override
	public void registerListener(EventObject e) {
		try {
			BasedataEdit aos_orgid = this.getControl("aos_orgid");// 根据人员过滤国别//销售人员国别组别维护
			aos_orgid.addBeforeF7SelectListener(this);
		} catch (Exception ex) {
			this.getView().showErrorNotification("registerListener = " + ex.toString());
		}
	}

	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		if (name.equals("aos_datefrom") || name.equals("aos_dateto")) {
			Object aos_datefrom = this.getModel().getValue("aos_datefrom");
			Object aos_dateto = this.getModel().getValue("aos_dateto");
			if (aos_datefrom != null && aos_dateto != null && !aos_datefrom.toString().equals("")
					&& !aos_dateto.toString().equals("") && !aos_datefrom.toString().equals("null")
					&& !aos_dateto.toString().equals(null)) {
				Date aos_datefrom_d = (Date) aos_datefrom;
				Date aos_dateto_d = (Date) aos_dateto;
				Long aos_period = (aos_dateto_d.getTime() - aos_datefrom_d.getTime()) / (1000L * 3600L * 24L);
				int aos_period_i = aos_period.intValue() + 1;
				this.getModel().setValue("aos_period", aos_period_i);
			} else
				this.getModel().setValue("aos_period", null);
		} else if (name.equals("aos_orgid") || name.equals("aos_platformid") || name.equals("aos_shopid")
				|| name.equals("aos_activity_name")) {
			init_billno();
		}
	}

	@Override
	public void afterLoadData(EventObject e) {
	}

	public void afterCreateNewData(EventObject e) {
		init_org();
	}

	private void init_billno() {
		// TODO 初始化单据编号
		Object aos_orgid = this.getModel().getValue("aos_orgid");
		Object aos_platformid = this.getModel().getValue("aos_platformid");
		Object aos_shopid = this.getModel().getValue("aos_shopid");
		Object aos_activity_name = this.getModel().getValue("aos_activity_name");
		if (aos_orgid != null && aos_platformid != null && aos_shopid != null && aos_activity_name != null) {
			String aos_orgnumber = ((DynamicObject) aos_orgid).getString("number");
			String aos_shopnumber = ((DynamicObject) aos_shopid).getString("aos_short_code");
			String aos_activity_name_str = ((DynamicObject) aos_activity_name).getString("number");
			String aos_billno_l = this.getModel().getValue("billno").toString();
			String billno = aos_orgnumber + "-"  + aos_shopnumber + "-" + aos_activity_name_str
					+ "-" + aos_billno_l;
			this.getModel().setValue("aos_billno", billno);
		}
	}

	private void init_org() {
		// TODO 初始化国别
		long CurrentUserId = UserServiceHelper.getCurrentUserId();
		DynamicObject aos_mkt_useright = QueryServiceHelper.queryOne("aos_mkt_userights",
				"entryentity.aos_orgid aos_orgid",
				new QFilter[] { new QFilter("aos_user", QCP.equals, CurrentUserId) });
		long aos_orgid = aos_mkt_useright.getLong("aos_orgid");
		this.getModel().setValue("aos_orgid", aos_orgid);
	}

	@Override
	public void beforeF7Select(BeforeF7SelectEvent beforeF7SelectEvent) {
		// 国别权限控制
		try {
			String name = beforeF7SelectEvent.getProperty().getName();
			// 获取当前人员id
			long CurrentUserId = UserServiceHelper.getCurrentUserId();
			if (StringUtils.equals(name, "aos_orgid")) {
				DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_userights",
						"entryentity.aos_orgid aos_orgid",
						new QFilter[] { new QFilter("aos_user", QCP.equals, CurrentUserId) });
				List<String> orgList = new ArrayList<>();
				for (DynamicObject obj : list) {
					orgList.add(obj.getString("aos_orgid"));
				}
				QFilter qFilter = new QFilter("id", QCP.in, orgList);
				ListShowParameter showParameter = (ListShowParameter) beforeF7SelectEvent.getFormShowParameter();
				showParameter.getListFilterParameter().getQFilters().add(qFilter);
			}
		} catch (Exception ex) {
			this.getView().showErrorNotification("beforeF7Select = " + ex.toString());
		}
	}
}