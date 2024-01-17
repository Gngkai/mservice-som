package mkt.synciface;

import common.fnd.FndError;
import common.fnd.FndLog;
import common.sal.util.DateUtil;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import common.sal.util.QFBuilder;
import mkt.progress.design.aos_mkt_designreq_bill;

import java.time.LocalDate;
import java.util.Date;
import java.util.Map;

/**
 * @author create by gk
 * @since 2023/3/1 14:47
 * @version 设计需求表 申请人确认节点，五天未确认，则自动提交-调度任务类
 */
public class AosMktSubmitDesignTask extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        operate();
    }

    public void operate() {
        FndLog fndLog = FndLog.init("设计需求表自动提交", LocalDate.now().toString());
        String dynamicType = "aos_mkt_designreq";
        QFBuilder qfBuilder = new QFBuilder();
        qfBuilder.add("aos_status", "=", "申请人确认");
        Date dateNow = new Date();
        // 查找设计需求表
        DynamicObjectCollection dycDesignreqs =
            QueryServiceHelper.query("aos_mkt_designreq", "id", qfBuilder.toArray());
        for (DynamicObject dy : dycDesignreqs) {
            DynamicObject dyMain = BusinessDataServiceHelper.loadSingle(dy.get("id"), dynamicType);
            // 申请人的上个节点时间
            Date dateLastNode = dyMain.getDate("modifytime");
            // 查找该单据上个节点的提交时间
            qfBuilder.clear();
            qfBuilder.add("aos_formid", "=", dynamicType);
            qfBuilder.add("aos_sourceid", "=", dyMain.getPkValue());
            DynamicObject dyOperate = QueryServiceHelper.queryOne("aos_sync_operate",
                "max(aos_actiondate) aos_actiondate", qfBuilder.toArray());
            if (dyOperate != null && dyOperate.get("aos_actiondate") != null) {
                dateLastNode = dyOperate.getDate("aos_actiondate");
            }
            // 判断上个节点的提交时间和当前时间比较，是否大于5天
            int differentDays = DateUtil.betweenDay(dateLastNode, dateNow);
            if (differentDays >= 5) {
                try {
                    new aos_mkt_designreq_bill().aos_submit(dyMain, "B");
                } catch (FndError fndError) {
                    fndLog.add(dyMain.getString("billno") + " : " + fndError.getErrorMessage());
                }
            }
        }
        fndLog.finnalSave();
    }
}
