package mkt.progress.listing;

import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndHistory;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.form.ClientProperties;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;
import mkt.progress.ProgressUtil;

/**
 * @author aosom
 * @version Listing优化需求销售确认单-表单插件
 */
public class AosMktListingSalBill extends AbstractBillPlugIn implements ItemClickListener {
    /** 系统管理员 **/
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        // 提交
        this.addItemClickListeners("aos_submit");
        // 打开来源流程
        this.addItemClickListeners("aos_open");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        String control = evt.getItemKey();
        try {
            if (sign.submit.name.equals(control)) {
                aosSubmit();
            } else if (sign.open.name.equals(control)) {
                aosOpen();
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    private void aosOpen() {
        Object aosSourceid = this.getModel().getValue("aos_sourceid");
        Object aosSourcetype = this.getModel().getValue("aos_sourcetype");
        if (sign.design.name.equals(aosSourcetype)) {
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_designreq", aosSourceid);
        } else if (sign.sonName.name.equals(aosSourcetype)) {
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_listing_son", aosSourceid);
        } else if (sign.minName.name.equals(aosSourcetype)) {
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_listing_min", aosSourceid);
        } else if (sign.desCmp.name.equals(aosSourcetype)) {
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_designcmp", aosSourceid);
        }
    }

    /** 初始化事件 **/
    @Override
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        statusControl();
    }

    /** 新建事件 **/
    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        statusControl();// 界面控制
    }

    /** 界面关闭事件 **/
    @Override
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        e.setCheckDataChange(false);
    }

    /** 提交 **/
    private void aosSubmit() throws FndError {
        String aosStatus = this.getModel().getValue("aos_status").toString();
        switch (aosStatus) {
            case "申请人":
                submitReq();
                break;
            case "销售确认":
                submitForSal();
                break;
            default:
                break;
        }
        FndHistory.Create(this.getView(), "提交", aosStatus);
        statusControl();
    }

    /** 全局状态控制 **/
    private void statusControl() {
        // 数据层
        Object aosStatus = this.getModel().getValue("aos_status");
        DynamicObject aosUser = (DynamicObject)this.getModel().getValue("aos_user");
        Object currentUserId = UserServiceHelper.getCurrentUserId();
        Object currentUserName = UserServiceHelper.getUserInfoByID((long)currentUserId).get("name");
        // 图片控制
        // InitPic();

        // 锁住需要控制的字段

        // 当前节点操作人不为当前用户 全锁
        if (!aosUser.getPkValue().toString().equals(currentUserId.toString())
            && !"程震杰".equals(currentUserName.toString()) && !"刘中怀".equals(currentUserName.toString())
            && !"陈聪".equals(currentUserName.toString())) {
            this.getView().setEnable(false, "titlepanel");
            this.getView().setEnable(false, "aos_contentpanelflex");
            this.getView().setVisible(false, "bar_save");
            this.getView().setVisible(false, "aos_submit");
        }
        // 状态控制
        if (sign.saleConfirm.name.equals(aosStatus)) {
            Map<String, Object> map = new HashMap<>(16);
            map.put(ClientProperties.Text, new LocaleString("销售确认"));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(true, "aos_submit");
            this.getView().setEnable(false, "aos_contentpanelflex");
            this.getView().setVisible(false, "bar_save");
        } else if (sign.end.name.equals(aosStatus)) {
            this.getView().setVisible(false, "aos_submit");
            this.getView().setEnable(false, "aos_contentpanelflex");
            this.getView().setVisible(false, "bar_save");
        }
    }

    /** 申请人状态下提交 **/
    private void submitReq() {
        // 异常参数
        FndError fndError = new FndError();
        // 数据层
        Object aosDesigner = this.getModel().getValue("aos_designer");
        Object fid = this.getModel().getDataEntity().getPkValue();
        Object aosBillno = this.getModel().getValue("billno");
        Object aosItemid = ((DynamicObject)this.getModel().getValue("aos_itemid", 0)).getPkValue();
        Object aosOrgid = ((DynamicObject)this.getModel().getValue("aos_orgid")).getPkValue();
        Object aosSale = ProgressUtil.findUserByOrgCate(aosOrgid, aosItemid, "aos_salehelper");
        if (aosDesigner == null) {
            fndError.add("设计为空,流程无法流转!");
        }
        if (fndError.getCount() > 0) {
            throw fndError;
        }
        // 执行保存操作
        this.getModel().setValue("aos_status", "销售确认");
        this.getModel().setValue("aos_user", aosSale);
        this.getView().invokeOperation("save");
        this.getView().invokeOperation("refresh");
        String messageId = String.valueOf(aosSale);
        String message = "设计需求表-Listing优化需求自动创建";
        MKTCom.SendGlobalMessage(messageId, "aos_mkt_listing_sal", String.valueOf(fid), String.valueOf(aosBillno),
            message);
    }

    /** 销售确认状态下提交 **/
    private void submitForSal() {
        // 异常参数
        // 数据层
        // 执行保存操作
        this.getModel().setValue("aos_status", "结束");
        this.getModel().setValue("aos_user", SYSTEM);
        this.getView().invokeOperation("save");
        this.getView().invokeOperation("refresh");

    }

    /**
     * 标识枚举类
     */
    private enum sign {
        /**
         * aos_submit
         */
        submit("aos_submit"),
        /**
         * 设计需求表
         */
        design("设计需求表"),
        /**
         * Listing优化需求表子表
         */
        sonName("Listing优化需求表子表"),
        /**
         * 结束
         */
        end("结束"),
        /**
         * 销售确认
         */
        saleConfirm("销售确认"),
        /**
         * Listing优化需求表小语种
         */
        minName("Listing优化需求表小语种"),
        /**
         * 设计完成表
         */
        desCmp("设计完成表"),

        /**
         * aos_open
         */
        open("aos_open")

        ;

        /**
         * 名称
         */
        private final String name;

        /**
         * 构造方法
         *
         * @param name 名称
         */
        sign(String name) {
            this.name = name;
        }
    }

}
