package mkt.progress.photo;

import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.entity.datamodel.ListSelectedRowCollection;
import kd.bos.entity.datamodel.events.PackageDataEvent;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.*;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.form.operatecol.OperationColItem;
import kd.bos.list.IListView;
import kd.bos.list.column.ListOperationColumnDesc;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.parainfo;

import java.util.*;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndHistory;

/**
 * @author create by gk
 */
public class AosMktProgPhReqList extends AbstractListPlugin {
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    private static final String KEY_ITEM = "item";
    private static final String AOS_FIND = "aos_find";
    private static final String AOS_SHOWCLOSE = "aos_showclose";
    private static final String AOS_SHOWOPEN = "aos_showopen";
    private static final String AOS_SHOWALL = "aos_showall";
    private static final String AOS_BATCHGIVE = "aos_batchgive";
    private static final String AOS_SUBMIT = "aos_submit";
    private static final String AOS_CLEAR = "aos_clear";
    private static final String AOS_CLOSE = "aos_close";
    private static final String ZX = "赵轩";
    private static final String FORM = "form";
    private static final String GIVE = "give";


    @Override
    public void packageData(PackageDataEvent e) {
        if (e.getSource() instanceof ListOperationColumnDesc) {
            @SuppressWarnings("unchecked")
            List<OperationColItem> operationColItems = (List<OperationColItem>)e.getFormatValue();
            for (OperationColItem operationColItem : operationColItems) {
                LocaleString name = new LocaleString();
                String billno = e.getRowData().getString("billno");
                String aosPonumber = e.getRowData().getString("aos_ponumber");
                // 设置操作列名称为单据编号
                if (FndGlobal.IsNull(billno)) {
                    name.setLocaleValue(" ");
                } else {
                    name.setLocaleValue(billno);
                }
                operationColItem.setOperationName(name);
                // 判断货号合同是否已关闭
                if (FndGlobal.IsNotNull(aosPonumber)) {
                    boolean exist = QueryServiceHelper.exists("aos_purcontract",
                        new QFilter("billno", QCP.equals, aosPonumber).and("billstatus", QCP.equals, "F").toArray());
                    if (exist) {
                        operationColItem.setForeColor("red");
                    }
                }
            }
        }
        super.packageData(e);
    }

    @Override
    public void setFilter(SetFilterEvent e) {
        List<QFilter> qFilters = e.getQFilters();
        // 非布景师
        if (!ProgressUtil.JudeMaster()) {
            String[] viewPermiss = new String[] {"aos_whiteph", "aos_actph", "aos_vedior",};
            parainfo.setRights(qFilters, this.getPageCache(), "aos_mkt_photoreq", viewPermiss);
        }
        IPageCache pageCache = this.getPageCache();
        if (FndGlobal.IsNotNull(pageCache.get(KEY_ITEM))) {
            String items = pageCache.get(KEY_ITEM);
            List<String> listItem = Arrays.asList(items.split(","));
            if (listItem.size() > 0) {
                QFilter filterItem = new QFilter("aos_itemid.id", QFilter.in, listItem);
                qFilters.add(filterItem);
            }
        }
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String itemKey = evt.getItemKey();
        if (AOS_FIND.equals(itemKey)) {
            exactQueryItem();
        } else if (AOS_SHOWCLOSE.equals(itemKey)) {
            // 查询关闭流程
            IPageCache iPageCache = this.getView().getPageCache();
            iPageCache.put("p_close_flag", "true");
            this.getView().invokeOperation("refresh");
        } else if (AOS_SHOWOPEN.equals(itemKey)) {
            // 查询未关闭流程
            IPageCache iPageCache = this.getView().getPageCache();
            iPageCache.put("p_close_flag", "false");
            this.getView().invokeOperation("refresh");
        } else if (AOS_SHOWALL.equals(itemKey)) {
            // 查询全部流程
            IPageCache iPageCache = this.getView().getPageCache();
            iPageCache.put("p_close_flag", "all");
            this.getView().invokeOperation("refresh");
        } else if (AOS_BATCHGIVE.equals(itemKey)) {
            // 批量转办
            aosOpen();
        } else if (AOS_SUBMIT.equals(itemKey)) {
            // 提交
            aosSubmit();
        } else if (AOS_CLEAR.equals(itemKey)) {
            IPageCache pageCache = this.getPageCache();
            pageCache.put(KEY_ITEM, null);
            this.getView().invokeOperation("refresh");
        } else if (AOS_CLOSE.equals(itemKey)) {
            aosClose();
        }
    }

    /**
     * 批量关闭
     */
    private void aosClose() {
        List<Object> list =
            getSelectedRows().stream().map(ListSelectedRow::getPrimaryKeyValue).distinct().collect(Collectors.toList());
        Object currentUserId = UserServiceHelper.getCurrentUserId();
        Object currentUserName = UserServiceHelper.getUserInfoByID((long)currentUserId).get("name");
        if (!ZX.equals(currentUserName)) {
            this.getView().showErrorNotification("无关闭权限,请联系管理员!");
            return;
        }
        for (Object o : list) {
            String id = o.toString();
            DynamicObject aosMktPhotoreq = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_photoreq");
            aosMktPhotoreq.set("aos_status", "已完成");
            aosMktPhotoreq.set("aos_user", SYSTEM);
            aosMktPhotoreq.set("aos_manual_close", true);
            OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq", new DynamicObject[] {aosMktPhotoreq},
                OperateOption.create());
            FndHistory fndHistory = new FndHistory();
            fndHistory.SetActionBy(currentUserId);
            fndHistory.SetFormId("aos_mkt_photoreq");
            fndHistory.SetSourceId(id);
            fndHistory.SetActionCode("手工关闭");
            fndHistory.SetDesc(currentUserName + "手工关闭!");
            Cux_Common_Utl.History(fndHistory);
        }
        this.getView().showSuccessNotification("关闭成功");
        this.getView().invokeOperation("refresh");
    }

    private void aosSubmit() {
        try {
            ListSelectedRowCollection selectedRows = this.getSelectedRows();
            int size = selectedRows.size();
            IListView view = (IListView)this.getView();
            String billFormId = view.getBillFormId();
            Iterator<ListSelectedRow> iterator = selectedRows.iterator();
            StringBuilder builder = new StringBuilder();
            StringJoiner str = new StringJoiner(";");
            while (iterator.hasNext()) {
                ListSelectedRow next = iterator.next();
                QFilter filterId = new QFilter("id", "=", next.getPrimaryKeyValue());
                DynamicObject dy = QueryServiceHelper.queryOne(billFormId, "aos_status", new QFilter[] {filterId});
                boolean reject = true;
                if (dy != null) {
                    if ("白底拍摄".equals(dy.getString("aos_status"))) {
                        reject = false;
                    } else if ("实景拍摄".equals(dy.getString("aos_status"))) {
                        reject = false;
                    }
                }
                if (reject) {
                    str.add(next.getBillNo());
                    iterator.remove();
                }
            }
            if (str.toString().length() > 0) {
                builder.append("  以下流程不在白底拍照或实景拍照节点，不能批量提交:");
                builder.append(str);
            }

            if (size == 0) {
                this.getView().showTipNotification("请先选择提交数据");
            } else {
                String message = ProgressUtil.submitEntity(view, selectedRows);
                if (message != null && message.length() > 0) {
                    message = "提交成功,部分无权限单据无法提交：  " + message;
                } else {
                    message = "提交成功";
                }
                this.getView().updateView();
                if (builder.toString().length() > 0) {
                    this.getView().showSuccessNotification(message + builder);
                } else {
                    this.getView().showSuccessNotification(message);
                }
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    /** 弹出转办框 **/
    private void aosOpen() {
        FormShowParameter showParameter = new FormShowParameter();
        showParameter.setFormId("aos_mkt_progive");
        showParameter.getOpenStyle().setShowType(ShowType.Modal);
        showParameter.setCloseCallBack(new CloseCallBack(this, "give"));
        this.getView().showForm(showParameter);
    }

    /** 精确查询物料 **/
    private void exactQueryItem() {
        // 创建弹出页面对象，FormShowParameter表示弹出页面为动态表单
        FormShowParameter showParameter = new FormShowParameter();
        // 设置弹出页面的编码
        showParameter.setFormId("aos_mkt_phreq_form");
        // 设置弹出页面标题
        showParameter.setCloseCallBack(new CloseCallBack(this, "form"));
        // 设置弹出页面打开方式，支持模态，新标签等
        showParameter.getOpenStyle().setShowType(ShowType.Modal);
        // 弹出页面对象赋值给父页面
        this.getView().showForm(showParameter);
    }

    /**
     * 页面关闭回调事件
     * 
     * @param closedCallBackEvent event
     */
    @Override
    public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
        super.closedCallBack(closedCallBackEvent);
        String actionId = closedCallBackEvent.getActionId();
        // 判断标识是否匹配，并验证返回值不为空，不验证返回值可能会报空指针
        if (StringUtils.equals(actionId, FORM) && null != closedCallBackEvent.getReturnData()) {
            // 这里返回对象为Object，可强转成相应的其他类型，
            // 单条数据可用String类型传输，返回多条数据可放入map中，也可使用json等方式传输
            @SuppressWarnings("unchecked")
            Map<String, List<String>> returnData = (Map<String, List<String>>)closedCallBackEvent.getReturnData();
            if (returnData.containsKey(KEY_ITEM) && returnData.get(KEY_ITEM) != null) {
                List<String> listItem = returnData.get("item");
                String items = String.join(",", listItem);
                this.getView().getPageCache().put(KEY_ITEM, items);
            }
            this.getView().invokeOperation("refresh");
        } else if (StringUtils.equals(actionId, GIVE)) {
            Object map = closedCallBackEvent.getReturnData();
            if (map == null)
            {
                return;
            }
            @SuppressWarnings("unchecked")
            Object aosUser = ((Map<String, Object>)map).get("aos_user");
            aosBatchgive(aosUser);
        }
    }
    private void aosBatchgive(Object aosUser) {
        List<Object> list =
            getSelectedRows().stream().map(ListSelectedRow::getPrimaryKeyValue).distinct().collect(Collectors.toList());
        Object currentUserId = UserServiceHelper.getCurrentUserId();
        Object currentUserName = UserServiceHelper.getUserInfoByID((long)currentUserId).get("name");
        for (Object o : list) {
            String id = o.toString();
            DynamicObject aosMktPhotoreq = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_photoreq");
            String aosUserold = aosMktPhotoreq.getDynamicObject("aos_user").getPkValue().toString();
            String billno = aosMktPhotoreq.getString("billno");
            if (!(String.valueOf(currentUserId)).equals(aosUserold)) {
                this.getView().showTipNotification(billno + "只允许转办自己的单据!");
                return;
            }
            aosMktPhotoreq.set("aos_user", aosUser);
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq",
                    new DynamicObject[]{aosMktPhotoreq}, OperateOption.create());
            MKTCom.SendGlobalMessage(String.valueOf(((DynamicObject) aosUser).getPkValue()),
                    String.valueOf(aosMktPhotoreq), String.valueOf(operationrst.getSuccessPkIds().get(0)), billno,
                    currentUserName + "流程转办!");
            FndHistory fndHistory = new FndHistory();
            fndHistory.SetActionBy(currentUserId);
            fndHistory.SetFormId("aos_mkt_photoreq");
            fndHistory.SetSourceId(id);
            fndHistory.SetActionCode("流程转办");
            fndHistory.SetDesc(currentUserName + "流程转办!");
            Cux_Common_Utl.History(fndHistory);
        }
        this.getView().showSuccessNotification("转办成功");
        this.getView().invokeOperation("refresh");
    }

}
