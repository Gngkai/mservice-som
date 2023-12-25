package mkt.act.basedata.actType;

import common.sal.util.QFBuilder;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.ExtendedDataEntity;
import kd.bos.entity.plugin.AbstractOperationServicePlugIn;
import kd.bos.entity.plugin.AddValidatorsEventArgs;
import kd.bos.entity.plugin.PreparePropertysEventArgs;
import kd.bos.entity.validate.AbstractValidator;
import kd.bos.servicehelper.QueryServiceHelper;

import java.util.List;

/**
 * @author: GK
 * @create: 2023-12-23 10:50
 * @Description: 活动赋分规则参数表
 */
public class ruleScoreOp  extends AbstractOperationServicePlugIn {
    @Override
    public void onPreparePropertys(PreparePropertysEventArgs e) {
        super.onPreparePropertys(e);
        List<String> fieldKeys = e.getFieldKeys();
        fieldKeys.add("aos_range_s");
        fieldKeys.add("aos_range_e");
    }

    @Override
    public void onAddValidators(AddValidatorsEventArgs e) {
        e.addValidator(new DelivaryDateValidator());
    }
    class DelivaryDateValidator extends AbstractValidator {
        @Override
        public void validate() {
            String entityKey = getEntityKey();
            ExtendedDataEntity[] dataEntities = this.getDataEntities();
            QFBuilder builder = new QFBuilder();
            for (ExtendedDataEntity dataEntity : dataEntities) {
                DynamicObject mainEntity = dataEntity.getDataEntity();
                builder.clear();
                builder.add("id","!=",mainEntity.get("id"));
                builder.add("aos_range_s","<",mainEntity.get("aos_range_e"));
                builder.add("aos_range_e",">",mainEntity.get("aos_range_s"));
                if (QueryServiceHelper.exists(entityKey,builder.toArray())) {
                   this.addErrorMessage(dataEntity,"规则范围重叠，不允许新增！");
                }
            }
        }
    }
}
