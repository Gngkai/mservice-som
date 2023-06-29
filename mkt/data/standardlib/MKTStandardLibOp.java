package mkt.data.standardlib;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.plugin.AbstractOperationServicePlugIn;
import kd.bos.entity.plugin.PreparePropertysEventArgs;
import kd.bos.entity.plugin.args.AfterOperationArgs;
import kd.bos.servicehelper.operation.SaveServiceHelper;

/**
 * 营销标准库通用操作插件插件
 */
public class MKTStandardLibOp extends AbstractOperationServicePlugIn {

    @Override
    public void onPreparePropertys(PreparePropertysEventArgs e) {
        e.getFieldKeys().add("billstatus");
    }

    @Override
    public void afterExecuteOperationTransaction(AfterOperationArgs e) {
        String operationKey = e.getOperationKey();
        // 手动关闭
        if ("aos_manuclose".equals(operationKey)) {
            DynamicObject[] dataEntities = e.getDataEntities();
            for (DynamicObject obj:dataEntities) {
                obj.set("billstatus", "E");// 状态更新为手动关闭
            }
            SaveServiceHelper.save(dataEntities);
            this.operationResult.setMessage("手动关闭成功");
        }

        // 手动开启
        if ("aos_manuopen".equals(operationKey)) {
            DynamicObject[] dataEntities = e.getDataEntities();
            for (DynamicObject obj:dataEntities) {
                obj.set("billstatus", "A");// 状态更新为手动关闭
            }
            SaveServiceHelper.save(dataEntities);
            this.operationResult.setMessage("手动开启成功");
        }

        // 已优化
        if ("aos_optimized".equals(operationKey)) {
            DynamicObject[] dataEntities = e.getDataEntities();
            for (DynamicObject obj:dataEntities) {
                String billstatus = obj.getString("billstatus");
                if ("D".equals(billstatus)) {
                    obj.set("billstatus", "B");// 如果状态为已优化更新状态为待确认
                }
            }
            SaveServiceHelper.save(dataEntities);
            this.operationResult.setMessage("优化成功,待确认");
        }
    }
}
