package mkt.data.point;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.ExtendedDataEntity;
import kd.bos.entity.plugin.AbstractOperationServicePlugIn;
import kd.bos.entity.plugin.AddValidatorsEventArgs;
import kd.bos.entity.plugin.PreparePropertysEventArgs;
import kd.bos.entity.validate.AbstractValidator;
import kd.bos.servicehelper.QueryServiceHelper;
import common.sal.util.QFBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

/**
 * @author create by gk
 * @date 2023/7/27 14:18
 * @action  词根属性明细表保存插件
 */
public class rootAttrSaveOP extends AbstractOperationServicePlugIn {
    @Override
    public void onPreparePropertys(PreparePropertysEventArgs e) {
        List<String> list = Arrays.asList("aos_org", "aos_root", "aos_attribute");
        e.getFieldKeys().addAll(list);
    }

    @Override
    public void onAddValidators(AddValidatorsEventArgs e) {
        e.addValidator(new rootAttrDataValidator());
    }

    class rootAttrDataValidator extends AbstractValidator{

        @Override
        public void validate() {
            ExtendedDataEntity[] dataEntities = this.getDataEntities();
            for (ExtendedDataEntity dataEntity : dataEntities) {
                DynamicObject entity = dataEntity.getDataEntity();
                Object orgid = null;
                if (entity.get("aos_org") instanceof DynamicObject){
                    DynamicObject aos_org = entity.getDynamicObject("aos_org");
                    orgid = aos_org.getPkValue();
                }
                else if (entity.get("aos_org") instanceof Long){
                    orgid = entity.get("aos_org");
                }
                String aos_root = entity.getString("aos_root");
                String aos_attribute = entity.getString("aos_attribute");
                QFBuilder builder = new QFBuilder();
                builder.add("id","!=",entity.getPkValue());
                builder.add("aos_org","=",orgid);
                builder.add(" aos_root","=", aos_root);
                builder.add("aos_attribute","=",aos_attribute);
                if (QueryServiceHelper.exists(this.entityKey,builder.toArray())) {
                    StringJoiner text = new StringJoiner(" ");
                    text.add(orgid.toString());
                    text.add(aos_root);
                    text.add(aos_attribute);
                    text.add("数据重复");
                    this.addErrorMessage(dataEntity,text.toString());
                }
            }
        }
    }
}
