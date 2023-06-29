package mkt.test;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.datamodel.IDataModel;
import kd.bos.extplugin.sample.AbstractFormPlugin;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import java.util.EventObject;

public class demo05 extends AbstractBillPlugIn {

    @Override
    public void afterCreateNewData(EventObject e) {
            super. afterCreateNewData(e);

            //获取用户id
            long userId = UserServiceHelper.getCurrentUserId( );
            //设置当前用户为申请人
            this.getModel().setValue("aos_i18nnamefield" , userId);

            //设网用户十业务组织为默认的申请部门
         //   this.getModel( ).setValue("applyorg", UserServiceHelper.getUserMainOrgId(userTd));
            //默认币种人民币
          //  this.getMlodel().setValue( " currency , 449114351756920832L);


            IDataModel model = this.getModel();
            String remark = (String)model .getValue("aos_userfield");//用户字段输出为null
            System.out.println("remark = "+ remark);

            /*
            简单字段的赋值，直接通过当前页面的数据模型IDataModel 赋值给字段赋值
                @Override
                public void afterCreateNewData(EventObject e) {
                    IDataModel model = this.getModel();
                    model.setValue("kded_remark", "我的备注数据");
                }
             */
            //设置打印次数
            this.getModel().setValue("aos_printcountfield", "9");
            System.out.println("打印次数 = " + this.getModel().getValue("aos_printcountfield"));

     //================================================================================================================================
            long currentUserId = UserServiceHelper.getCurrentUserId();//获取用户ID
            //方式1：通过id赋值-推荐
            this.getModel().setValue("aos_userfield", currentUserId );
            //方式2：通过对象赋值
               // DynamicObject user = BusinessDataServiceHelper.loadSingle(currentUserId, 	"bos_user");
               // this.getModel().setValue("kded_registrant", user);

     //================================================================================================================================
            //给单据体某个字段赋值,从第0行开始
            this.getModel().setValue("aos_textfield","安心",0);
                //读取单据头字段值
            String headtext = (String) this.getModel().getValue("aos_i18nnamefield");
                //  String headtext1 = (String) this.getModel().getValue("aos_userfield");
                //读取单据体字段值
            String entrytext = (String) this.getModel().getValue("aos_textfield",0);
                 // System.out.println("单据头用户id字段值= " + headtext + "单据头用户字段值= " + headtext1);
                //用户字段获取不了
            // System.out.println("单据头用户字段值= " + headtext1);

            System.out.println("单据体字段值= " + entrytext);

            this.getModel().setValue("aos_integerfield",99);
            Integer headint = (Integer) this.getModel().getValue("aos_integerfield");
            System.out.println("整数字段 = " + headint);


    }
}
