package mkt.act.rule;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import mkt.act.rule.service.ActPlanService;
import mkt.act.rule.service.impl.ActPlanServiceImpl;

import java.util.Date;
import java.util.EventObject;

import common.fnd.AosomLog;

/**
 * 活动信息计算
 */
public class ActInfoCalEdit extends AbstractBillPlugIn {

	private static AosomLog logger = AosomLog.init("ActInfoCalEdit");
    static{
		logger.setService("aos.mms");
		logger.setDomain("mms.act");
    }
    private static final ActPlanService actPlanService = new ActPlanServiceImpl();

    @Override
    public void registerListener(EventObject e) {
        this.addItemClickListeners("tbmain");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        String itemKey = evt.getItemKey();
        if ("aos_actinfo".equals(itemKey)) {
            calActInfo();
        }
    }

    private void calActInfo() {
        DynamicObject dataEntity = this.getModel().getDataEntity(true);
        DynamicObject aos_nationality = dataEntity.getDynamicObject("aos_nationality");
        if (aos_nationality == null) {
            this.getView().showTipNotification("请选择国别!");
            return;
        }
        // 获取活动开始日期
        Date aos_startdate = dataEntity.getDate("aos_startdate");
        if (aos_startdate == null) {
            this.getView().showTipNotification("请填写开始日期!");
            return;
        }

        Date aos_enddate1 = dataEntity.getDate("aos_enddate1");
        if (aos_enddate1 == null) {
            this.getView().showTipNotification("请填写结束日期!");
            return;
        }
        // 获取渠道id
        long aos_platformid = dataEntity.getLong("aos_channel.id");
        if (aos_platformid == 0) {
            this.getView().showTipNotification("请选择平台!");
            return;
        }

        String aos_shopnum = dataEntity.getString("aos_shop.number");
        if (aos_shopnum == null || "".equals(aos_shopnum)) {
            this.getView().showTipNotification("请选择店铺!");
            return;
        }

        String aos_acttypenum = dataEntity.getString("aos_acttype.number");
        if (aos_acttypenum == null || "".equals(aos_acttypenum)) {
            this.getView().showTipNotification("请选择活动类型!");
            return;
        }
        actPlanService.updateActInfo(dataEntity);
        this.getView().updateView("aos_sal_actplanentity");
    }
}
