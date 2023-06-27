package mkt.test;

import com.alibaba.druid.util.StringUtils;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.control.Control;
import kd.bos.form.control.events.BeforeClickEvent;
import kd.bos.form.field.TimeRangeEdit;
import kd.bos.form.plugin.AbstractFormPlugin;

import java.util.EventObject;

import static kd.swc.hsbp.common.constants.CalPayrollTaskConstants.KEY_STARTTIME;

public class demo03 extends AbstractFormPlugin {
    public void afterCreateNewData(EventObject e) {

        int s = 3*60*60 + 3*60 + 3; // -> 03:03:03
        //单据头
        this.getModel().setValue("aos_timefield",s);
        //单据体第1行
        //this.getModel().setValue("entry_timefield",s,0);


        //获取开始、结束字段属性的标识
        TimeRangeEdit edit = this.getView().getControl("aos_timerangefield");
        String key_startTime = edit.getStartDateFieldKey();
        String key_endTime = edit.getEndDateFieldKey();
        int startValue = 3*60*60 + 3*60 + 3; // -> 03:03:03
        int endValue = 4*60*60 + 4*60 +0;// -> 04:04:00
        // 赋值
        this.getModel().setValue(key_startTime, startValue);
        this.getModel().setValue(key_endTime, endValue);
    }

    public void propertyChanged(PropertyChangedArgs e) {
        String fieldKey = e.getProperty().getName();
        if (StringUtils.equals("aos_timerangefield1", fieldKey)){
            // 开始时间值改变
            System.out.println("propertyChanged开始时间值改变");
        }
        else if (StringUtils.equals("aos_timerangefield2", fieldKey)){
            // 结束时间值改变
            System.out.println("propertyChanged结束时间改变");
        }
    }

    public void beforePropertyChanged(PropertyChangedArgs e) {
        String fieldKey = e.getProperty().getName();
        if (StringUtils.equals("aos_timerangefield1", fieldKey)) {
            // 开始时间值改变
            System.out.println("beforePropertyChanged开始时间值改变");
        }
        else if (StringUtils.equals("aos_timerangefield2", fieldKey)){
            // 结束时间值改变
            System.out.println("beforePropertyChanged结束时间值改变");
        }
    }


    //图片
    public void registerListener(EventObject e) {
        super.registerListener(e);

        // 侦听单据体图片字段点击事件
        this.addClickListeners("aos_picturefield");
    }
    public void beforeClick(BeforeClickEvent evt) {
        super.beforeClick(evt);
        Control source = (Control)evt.getSource();
        if (StringUtils.equals("aos_picturefield", source.getKey())){
            System.out.println("图片被点击");
        }
    }
}
