package mkt.data.global;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import common.fnd.FndGlobal;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.bill.BillShowParameter;
import kd.bos.bill.OperationStatus;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.ShowType;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.CellClickEvent;
import kd.bos.form.control.events.CellClickListener;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
import kd.bos.form.operate.FormOperate;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

/**
 * @author aosom
 * @version 数据库全局查询-动态表单插件
 */
public class AosMktGlobalForm extends AbstractFormPlugin
    implements RowClickEventListener, CellClickListener, HyperLinkClickListener {

    private final static String POINT = "P";
    private final static String POINT_BILL = "aos_mkt_point";
    private final static String STANDARD = "S";
    private final static String STANDARD_BILL = "aos_mkt_standard";
    private final static String SLOGAN = "Slogan";
    private final static String SLOGAN_BILL = "aos_mkt_data_slogan";
    private final static String AOS_QUERY = "aos_query";
	private final static String AOS_DATA = "aos_data";



    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        EntryGrid aosEntryentity = this.getControl("aos_entryentity");
        aosEntryentity.addCellClickListener(this);
        aosEntryentity.addRowClickListener(this);
        aosEntryentity.addHyperClickListener(this);
    }

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs args) {
        super.afterDoOperation(args);
        FormOperate formOperate = (FormOperate)args.getSource();
        if (AOS_QUERY.equals(formOperate.getOperateKey())) {
            aosQuery();
        }
    }

    private void aosQuery() {
        this.getModel().deleteEntryData("aos_entryentity");
        Object aosText = this.getModel().getValue("aos_text");
        if (FndGlobal.IsNull(aosText)) {
            this.getView().showTipNotification("文本不能为空!");
        } else {
            String aosTextStr = aosText.toString();
            int i = 0;
            queryAll(aosTextStr, i);
        }
    }

    private void queryAll(String aosTextStr, int i) {
        // 获取人员国别权限
        long currentUserId = UserServiceHelper.getCurrentUserId();
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_userights", "entryentity.aos_orgid aos_orgid",
            new QFilter[] {new QFilter("aos_user", QCP.equals, currentUserId)});
        List<String> orgList = new ArrayList<>();
        for (DynamicObject obj : list) {
            orgList.add(obj.getString("aos_orgid"));
        }
        QFilter qFilterRightS = new QFilter("aos_orgid", QCP.in, orgList);
        // 关键词库头查询
        i = commonQuery(i, aosTextStr, "aos_orgid.number", "国别", POINT, POINT_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_category1", "大类", POINT, POINT_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_category2", "中类", POINT, POINT_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_category3", "小类", POINT, POINT_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_itemnamecn", "品名", POINT, POINT_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_points", "核心词", POINT, POINT_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_egsku.number", "举例SKU", POINT, POINT_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_linentity.aos_mainvoc", "关键词", POINT, POINT_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_linentity.aos_type", "类型", POINT, POINT_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_linentity.aos_relate", "相关性", POINT, POINT_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_linentity.aos_rate", "同款占比", POINT, POINT_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_linentity.aos_counts", "搜索量", POINT, POINT_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_linentity.aos_source", "来源", POINT, POINT_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_linentity.aos_adress", "应用位置", POINT, POINT_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_pointses", "ES核心词", POINT, POINT_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_pointsfr", "FR核心词", POINT, POINT_BILL, qFilterRightS);
        // 国别文案标准库头查询
        i = commonQuery(i, aosTextStr, "aos_orgid.number", "1、产品资料 国别", STANDARD, STANDARD_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_category1", "1、产品资料 大类", STANDARD, STANDARD_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_category2", "1、产品资料 中类", STANDARD, STANDARD_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_category3", "1、产品资料 小类", STANDARD, STANDARD_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_itemnamecn", "1、产品资料 中文品名", STANDARD, STANDARD_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_itemnameen", "1、产品资料 英文品名", STANDARD, STANDARD_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_user.name", "1、产品资料 品类编辑", STANDARD, STANDARD_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_detail", "1、产品资料 属性细分", STANDARD, STANDARD_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_salesentity.aos_salesl", "2、产品卖点 卖点", STANDARD, STANDARD_BILL,
            qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_salesentity.aos_saledetaill", "2、产品卖点 卖点细分-CN", STANDARD, STANDARD_BILL,
            qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_salesentity.aos_bulletl", "2、产品卖点 Bullet Points-EN", STANDARD,
            STANDARD_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_salesentity.aos_featuresl", "2、产品卖点 Features-EN", STANDARD, STANDARD_BILL,
            qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_descentity.aos_contain", "3、长描述 内容", STANDARD, STANDARD_BILL,
            qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_para", "4、参数", STANDARD, STANDARD_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_warnentity.aos_warncnl", "5、警示语Note 中文", STANDARD, STANDARD_BILL,
            qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_warnentity.aos_warnusl", "5、警示语Note 英语", STANDARD, STANDARD_BILL,
            qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_titleentity.aos_platformid.name", "6、标题 英语", STANDARD, STANDARD_BILL,
            qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_titleentity.aos_standard_definel", "6、标题 标准定义", STANDARD, STANDARD_BILL,
            qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_titleentity.aos_classic_definel", "6、标题 经典定义", STANDARD, STANDARD_BILL,
            qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_colour", "7、综合帖选项类型 颜色", STANDARD, STANDARD_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_size", "7、综合帖选项类型 尺寸", STANDARD, STANDARD_BILL, qFilterRightS);
        i = commonQuery(i, aosTextStr, "aos_desc", "8、备注Tips", STANDARD, STANDARD_BILL, qFilterRightS);
        // 品名Slogan库
        i = commonQuery(i, aosTextStr, "aos_category1", "大类", SLOGAN, SLOGAN_BILL, null);
        i = commonQuery(i, aosTextStr, "aos_category2", "中类", SLOGAN, SLOGAN_BILL, null);
        i = commonQuery(i, aosTextStr, "aos_category3", "小类", SLOGAN, SLOGAN_BILL, null);
        i = commonQuery(i, aosTextStr, "aos_user.number", "英语编辑", SLOGAN, SLOGAN_BILL, null);
        i = commonQuery(i, aosTextStr, "aos_itemnamecn", "CN 品名", SLOGAN, SLOGAN_BILL, null);
        i = commonQuery(i, aosTextStr, "aos_slogancn", "CN Slogan", SLOGAN, SLOGAN_BILL, null);
        i = commonQuery(i, aosTextStr, "aos_itemid.number", "参考SKU", SLOGAN, SLOGAN_BILL, null);
        i = commonQuery(i, aosTextStr, "aos_itemnameen", "EN 品名", SLOGAN, SLOGAN_BILL, null);
        i = commonQuery(i, aosTextStr, "aos_sloganen", "EN Slogan", SLOGAN, SLOGAN_BILL, null);
        i = commonQuery(i, aosTextStr, "aos_itemnamede", "DE 品名", SLOGAN, SLOGAN_BILL, null);
        i = commonQuery(i, aosTextStr, "aos_slogande", "DE Slogan", SLOGAN, SLOGAN_BILL, null);
        i = commonQuery(i, aosTextStr, "aos_itemnamefr", "FR 品名", SLOGAN, SLOGAN_BILL, null);
        i = commonQuery(i, aosTextStr, "aos_sloganfr", "FR Slogan", SLOGAN, SLOGAN_BILL, null);
        i = commonQuery(i, aosTextStr, "aos_itemnameit", "IT 品名", SLOGAN, SLOGAN_BILL, null);
        i = commonQuery(i, aosTextStr, "aos_sloganit", "IT Slogan", SLOGAN, SLOGAN_BILL, null);
        i = commonQuery(i, aosTextStr, "aos_itemnamees", "ES 品名", SLOGAN, SLOGAN_BILL, null);
        i = commonQuery(i, aosTextStr, "aos_sloganes", "ES Slogan", SLOGAN, SLOGAN_BILL, null);
    }

    private int commonQuery(int i, String aosTextStr, String sign, String name, String data, String bill,
        QFilter qFilterRightS) {
        QFilter filterCom = new QFilter(sign, "like", "%" + aosTextStr + "%");
        QFilter[] filtersCom = new QFilter[] {filterCom, qFilterRightS};
        String selectCom = "id," + sign;
        DataSet comS =
            QueryServiceHelper.queryDataSet("Common_Query.COMS" + data + sign, bill, selectCom, filtersCom, null);
        while (comS.hasNext()) {
            Row com = comS.next();
            String aosContain = com.getString(sign);
            long aosId = com.getLong("id");
            this.getModel().createNewEntryRow("aos_entryentity");
            this.getModel().setValue("aos_data", data, i);
            this.getModel().setValue("aos_name", name, i);
            this.getModel().setValue("aos_contain", aosContain, i);
            this.getModel().setValue("aos_dataid", aosId, i);
            i++;
        }
        comS.close();
        return i;
    }

    @Override
    public void cellClick(CellClickEvent arg0) {}

    @Override
    public void cellDoubleClick(CellClickEvent arg0) {}

    @Override
    public void hyperLinkClick(HyperLinkClickEvent arg0) {
        String name = arg0.getFieldName();
        int row = arg0.getRowIndex();
        if (AOS_DATA.equals(name) && (row != -1)) {
            BillShowParameter showParameter = new BillShowParameter();
            Object pkId = this.getModel().getValue("aos_dataid", row);
            String aosData = (String)this.getModel().getValue("aos_data");
            if (POINT.equals(aosData)) {
                showParameter.setFormId(POINT_BILL);
            } else if (STANDARD.equals(aosData)) {
                showParameter.setFormId(STANDARD_BILL);
            } else if (SLOGAN.equals(aosData)) {
                showParameter.setFormId(SLOGAN_BILL);
            }
            showParameter.getOpenStyle().setShowType(ShowType.Modal);
            showParameter.setPkId(pkId);
            showParameter.setStatus(OperationStatus.EDIT);
            this.getView().showForm(showParameter);
        }
    }
}
