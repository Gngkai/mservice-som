package mkt.image.test;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.CloseCallBack;
import kd.bos.form.FormShowParameter;
import kd.bos.form.ShowType;
import kd.bos.form.control.Control;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.control.events.RowClickEvent;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.math.BigDecimal;
import java.util.EventObject;

public class aos_mkt_imgtest_bill2 extends AbstractBillPlugIn implements ItemClickListener, RowClickEventListener {

	@Override
	public void registerListener(EventObject e) {
		super.registerListener(e);
		// 给按钮加监听

		this.addItemClickListeners("aos_toolbarap");// 给工具栏加监听事件
		this.addItemClickListeners("aos_test"); // 测试
		this.addItemClickListeners("aos_show"); // 测试

		// 给单据体加监听
		EntryGrid entryGrid = this.getControl("aos_entryentity");
		EntryGrid aos_subentryentity = this.getControl("aos_subentryentity");

		entryGrid.addRowClickListener(this);
		aos_subentryentity.addRowClickListener(this);
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		try {
			if ("aos_test".equals(Control))
				aos_test();// 提交
			else if ("aos_test2".equals(Control)) {

			}
			if ("aos_test3".equals(Control)) {
				insertLogTest();

			}
			if ("aos_test4".equals(Control)) {
				updateLogTest();

			} else if ("aos_test2".equals(Control))
				;
			else if ("aos_show".equals(Control))
				aos_show();
		} catch (FndError fndMessage) {
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	private void updateLogTest() {
		String selectFields = "entryentity.aos_content," + "aos_subentryentity.aos_testcontent";
		QFilter qFilter = new QFilter("aos_type", QCP.equals, "测试日志221007");
		DynamicObject object = BusinessDataServiceHelper.loadSingle("aos_mkt_taskwork1006_bill", selectFields,
				qFilter.toArray());
		// 获取到单据体entryentity
		DynamicObjectCollection entryentity = object.getDynamicObjectCollection("entryentity");
		for (DynamicObject object1 : entryentity) {
			String aos_content = object1.getString("aos_content");
			if ("test1".equals(aos_content)) {
				// 如果日志内容为test1 修改子单据体
				DynamicObjectCollection aos_subentryentity = object1.getDynamicObjectCollection("aos_subentryentity");
				for (DynamicObject object2 : aos_subentryentity) {
					String aos_testcontent = object2.getString("aos_testcontent");
					if ("子单据体日志内容10".equals(aos_testcontent)) {
						object2.set("aos_testcontent", "子单据体日志内容10000");
						break;
					}
				}
			}
		}
		SaveServiceHelper.update(new DynamicObject[] { object });
	}

	public static void insertLogTest() {
		// 新建一个单据对象
		DynamicObject object = BusinessDataServiceHelper.newDynamicObject("aos_mkt_taskwork1006_bill");
		object.set("billstatus", "A");
		object.set("aos_type", "测试日志221007");

		// 获取到标识为aos_entryentity 的单据体
		DynamicObjectCollection entryentity = object.getDynamicObjectCollection("entryentity");
		// 新增一行单据体数据
		DynamicObject object1 = entryentity.addNew();
		object1.set("aos_content", "test1");

		DynamicObjectCollection aos_subentryentity = object1.getDynamicObjectCollection("aos_subentryentity");
		DynamicObject object2 = aos_subentryentity.addNew();
		object2.set("aos_testcontent", "子单据体日志内容1");

		DynamicObject object3 = aos_subentryentity.addNew();
		object3.set("aos_testcontent", "子单据体日志内容2");
		SaveServiceHelper.save(new DynamicObject[] { object });
	}

	private void aos_show() {
		// TODO 弹框

		FormShowParameter showParameter = new FormShowParameter();
		showParameter.setFormId("aos_sal_product_prt");

		showParameter.getOpenStyle().setShowType(ShowType.Modal);

		// showParameter.getOpenStyle().setTargetKey("aos_detail");

		showParameter.setCloseCallBack(new CloseCallBack(this, "form"));
		this.getView().showForm(showParameter);
	}

	private void aos_test() {
		
		String billno = Cux_Common_Utl.GetBaseBillNo("D2022");
		
		System.out.println("billno ="+billno);
		
	}

	/** 值改变事件 **/
	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		if ("aos_combofield".equals(name)) {
			int CurrentRowIndex = this.getView().getModel().getEntryCurrentRowIndex("aos_entryentity");
			Object aos_combofield = this.getModel().getValue("aos_combofield");

			if ("B".equals(aos_combofield))
				this.getView().getModel().setValue("aos_textfield", "秋冬", CurrentRowIndex);
			else if ("A".equals(aos_combofield))
				this.getView().getModel().setValue("aos_textfield", "春夏", CurrentRowIndex);

		}
	}

	/** 行点击监听 **/
	public void entryRowClick(RowClickEvent evt) {
		Control source = (Control) evt.getSource();
		String name = source.getKey();
		System.out.println("name = " + name);

		if (source.getKey().equals("aos_entryentity")) {
			int CurrentRowIndex = this.getView().getModel().getEntryCurrentRowIndex("aos_entryentity");

			this.getModel().setValue("aos_textfield", "AAA", CurrentRowIndex);
			System.out.println("CurrentRowIndex =" + CurrentRowIndex);

		}
	}

	/** 新建单据时触发 **/
	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);

		System.out.println("into afterCreateNewData");
		this.getModel().setValue("aos_text", "遨森电子商务股份有限公司");
		this.getView().getModel().setValue("aos_text", "遨森电子商务股份有限公司");
		Object aos_text2 = this.getModel().getValue("aos_text2");
		System.out.println("aos_text2 =" + aos_text2);
	}

	/** 打开已存在单据时触发 **/
	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		System.out.println("into afterLoadData");
		// 文本
		Object aos_text = this.getModel().getValue("aos_text");
		Object aos_text2 = this.getModel().getValue("aos_text2");
		System.out.println("aos_text =" + aos_text);
		System.out.println("aos_text2 =" + aos_text2);
		Object aos_orgid = this.getModel().getValue("aos_orgid");

		if (this.getModel().getValue("aos_big") != null) {
			BigDecimal aos_big = (BigDecimal) this.getModel().getValue("aos_big");
			System.out.println("aos_big =" + aos_big);

			System.out.println(aos_big.add(BigDecimal.valueOf(0.1)));
			System.out.println(aos_big.multiply(BigDecimal.valueOf(0.1)));
		}

		// 基础资料
		if (aos_orgid != null) {
			DynamicObject aos_org = (DynamicObject) aos_orgid;
			String aos_orgnumber = aos_org.getString("number");
			System.out.println("aos_orgnumber =" + aos_orgnumber);
		}
	}

}
