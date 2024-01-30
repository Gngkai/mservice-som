package mkt.progress.synthesis.gift;

import common.sal.util.QFBuilder;
import kd.bos.algo.Algo;
import kd.bos.algo.AlgoContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.workflow.api.AgentExecution;
import kd.bos.workflow.engine.extitf.IWorkflowPlugin;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

/**
 * @author: GK
 * @create: 2024-01-25 16:08
 * @Description: 正常审批流程 下一节点分支判断
 */
public class giftWorkPlugin implements IWorkflowPlugin {
    @Override
    public void notify(AgentExecution e) {
        IWorkflowPlugin.super.notify(e);
    }

    @Override
    public boolean hasTrueCondition(AgentExecution e) {
        //判断计算当前的本月的总费用，并且设置总费用
        DynamicObject mainEntry = BusinessDataServiceHelper.loadSingle(e.getBusinessKey(), e.getEntityNumber());
        DynamicObject orgEntry = mainEntry.getDynamicObject("aos_org");
        Date createDate = mainEntry.getDate("createtime");
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(createDate);
        //设置为月初 1号 0时0分0秒
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        //获取流程参数
        Map<String, Object> params = e.getCurrentWFPluginParams();
        BigDecimal budget = new BigDecimal(params.get("budget").toString());
        BigDecimal cost = BigDecimal.ZERO;
        try (AlgoContext ignored = Algo.newContext()) {
            QFBuilder builder = new QFBuilder();
            builder.add("aos_org", "=", orgEntry.getPkValue());
            builder.add("createtime", ">=", calendar.getTime());
            calendar.add(Calendar.MONTH, 1);
            builder.add("createtime", "<", calendar.getTime());
            DynamicObject dy = QueryServiceHelper.queryOne(e.getEntityNumber(), "sum(entryentity.aos_cost) aos_cost", builder.toArray());
            if (dy != null) {
                cost = dy.getBigDecimal("aos_cost");
            }
        }
        //预算小于等于金额，超预算
        return (budget.compareTo(cost) <= 0);
    }
}
