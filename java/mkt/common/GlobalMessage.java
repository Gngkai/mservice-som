package mkt.common;

import kd.bos.bill.BillShowParameter;
import kd.bos.bill.OperationStatus;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.form.ShowType;
import kd.bos.notification.AbstractNotificationClick;
import kd.bos.notification.events.ButtonClickEventArgs;
import kd.bos.servicehelper.workflow.MessageCenterServiceHelper;
import kd.bos.url.UrlService;
import kd.bos.workflow.engine.msg.info.MessageInfo;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author create by gk
 * @since 2022/11/5 13:28
 * @version 全部消息提醒的弹框点击事件
 */
public class GlobalMessage extends AbstractNotificationClick {
    private static final String AOS_DETAIL = "aos_detail";
    private static final String NAME = "name";
    private static final String ID = "id";
    private static final String FORMID = "formId";
    private static final String PKID = "pkId";
    @Override
    public void buttonClick(ButtonClickEventArgs eventArgs) {
        String buttonKey = eventArgs.getButtonKey();
        if (AOS_DETAIL.equals(buttonKey)){
            Map<String, Object> params = this.getNotificationFormInfo().getNotification().getParams();
            if (params.containsKey(NAME)&& params.containsKey(ID)){
                BillShowParameter showParameter = new BillShowParameter();
                showParameter.setFormId(params.get("name").toString());
                showParameter.setPkId(params.get("id"));
                showParameter.getOpenStyle().setShowType(ShowType.NewWindow);
                showParameter.setStatus(OperationStatus.VIEW);
                this.getFormView().showForm(showParameter);
            }
            else{
                this.getFormView().showMessage("单据不存在，无法打开界面。");
            }
        }
    }


    /**
     * 系统发送消息
     *
     * @param msg          消息接收人
     * @param id 消息类型 : MessageInfo.TYPE_ALAR（报警） MessageInfo.TYPE_WARNING（预警） MessageInfo.TYPE_MESSAGE（消息通知）
     */
    public static void SendMessage(String msg, String id) {
        Map<String, String> map = new HashMap<>(16);
        ArrayList<Long> ids = new ArrayList<>();
        String messageType = MessageInfo.TYPE_MESSAGE;
        map.put("title", msg);
        map.put("content", msg);
        map.put("tag", msg);
        ids.add(Long.valueOf(id));
        MessageInfo messageInfo = new MessageInfo();
        //消息标题
        LocaleString title = new LocaleString();
        title.setLocaleValue_zh_CN(map.get("title"));
        title.setLocaleValue_en(map.get("title"));
        title.setLocaleValue_zh_TW(map.get("title"));
        messageInfo.setMessageTitle(title);
        //消息内容
        LocaleString content = new LocaleString();
        content.setLocaleValue_zh_CN("<h3>" + map.get("content") + "</h3>");
        content.setLocaleValue_en("<h3>" + map.get("content") + "</h3>");
        content.setLocaleValue_zh_TW("<h3>" + map.get("content") + "</h3>");
        messageInfo.setMessageContent(content);
        //消息接收方ID
        messageInfo.setUserIds(ids);
        //消息类型
        messageInfo.setType(messageType);
        // 业务标签
        messageInfo.setTag(map.get("tag"));
        //设置快速处理url
        if (StringUtils.isNotBlank(map.get(FORMID)) && StringUtils.isNotBlank(map.get(PKID))) {
            messageInfo.setContentUrl(
                UrlService.getDomainContextUrl() + "/index.html?formId=" + map.get("formId") + "&pkId=" + map.get("pkId"));
        }
        //发送消息
        MessageCenterServiceHelper.sendMessage(messageInfo);
    }

    /**
     * 系统发送消息
     *
     * @param msg          消息接收人
     * @param id 消息类型 : MessageInfo.TYPE_ALAR（报警） MessageInfo.TYPE_WARNING（预警） MessageInfo.TYPE_MESSAGE（消息通知）
     */
    @Deprecated
    public static void SendMessageTest(String msg, String id) {
        Map<String, String> map = new HashMap<>(16);
        ArrayList<Long> ids = new ArrayList<>();
        String messageType = MessageInfo.TYPE_WARNING;
        map.put("title", msg);
        map.put("content", msg);
        map.put("tag", msg);
        ids.add(Long.valueOf(id));

        MessageInfo messageInfo = new MessageInfo();
        //消息标题
        LocaleString title = new LocaleString();
        title.setLocaleValue_zh_CN(map.get("title"));
        title.setLocaleValue_en(map.get("title"));
        title.setLocaleValue_zh_TW(map.get("title"));
        messageInfo.setMessageTitle(title);
        //消息内容
        LocaleString content = new LocaleString();
        content.setLocaleValue_zh_CN("<h3>" + map.get("content") + "</h3>");
        content.setLocaleValue_en("<h3>" + map.get("content") + "</h3>");
        content.setLocaleValue_zh_TW("<h3>" + map.get("content") + "</h3>");
        messageInfo.setMessageContent(content);
        //消息接收方ID
        messageInfo.setUserIds(ids);
        //消息类型
        messageInfo.setType(messageType);
        // 业务标签
        messageInfo.setTag(map.get("tag"));
        //设置快速处理url
        if (StringUtils.isNotBlank(map.get(FORMID)) && StringUtils.isNotBlank(map.get(PKID))) {
            messageInfo.setContentUrl(UrlService.getDomainContextUrl() + "/index.html?formId=" + map.get("formId") + "&pkId=" + map.get("pkId"));
        }
        //发送消息
        MessageCenterServiceHelper.sendMessage(messageInfo);
    }
}
