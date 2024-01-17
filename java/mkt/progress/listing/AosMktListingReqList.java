package mkt.progress.listing;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import edu.umd.cs.findbugs.annotations.NoWarning;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRowCollection;
import kd.bos.form.*;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.parainfo;

/**
 * @author aosom
 * @version Listing优化需求表-列表插件
 */
public class AosMktListingReqList extends AbstractListPlugin {
    public final static String AOS_MKT_LISTING_REQ = "aos_mkt_listing_req";
    private final static String KEY_CATE = "cate";
    private final static String SHOWCLOSE = "aos_showclose";
    private final static String SUBMIT = "aos_submit";
    private final static String FINDCATE = "aos_findcate";
    private final static String FORM = "form";

    @Override
    public void setFilter(SetFilterEvent e) {
        List<QFilter> qFilters = e.getQFilters();
        parainfo.setRights(qFilters, this.getPageCache(), AOS_MKT_LISTING_REQ);
        IPageCache pageCache = this.getPageCache();
        if (FndGlobal.IsNotNull(pageCache.get(KEY_CATE))) {
            String items = pageCache.get(KEY_CATE);
            List<String> listCate = Arrays.asList(items.split(","));
            if (listCate.size() > 0) {
                QFilter filterItem = new QFilter("aos_entryentity.aos_category1", QFilter.in, listCate);
                qFilters.add(filterItem);
            }
            pageCache.put(KEY_CATE, null);
        }
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String itemKey = evt.getItemKey();
        try {
            if (SHOWCLOSE.equals(itemKey)) {
                parainfo.showClose(this.getView());
            } else if (SUBMIT.equals(itemKey)) {
                aosSubmit();
            } else if (FINDCATE.equals(itemKey)) {
                findCate();
            }
        } catch (FndError error) {
            this.getView().showMessage(error.getErrorMessage());
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            this.getView().showMessage(writer.toString());
            e.printStackTrace();
        }
    }

    private void aosSubmit() {
        try {
            ListSelectedRowCollection selectedRows = this.getSelectedRows();
            int size = selectedRows.size();
            if (size == 0) {
                this.getView().showTipNotification("请先选择提交数据");
            } else {
                IFormView view = this.getView();
                String message = ProgressUtil.submitEntity(view, selectedRows);
                if (message != null && message.length() > 0) {
                    message = "提交成功,部分无权限单据无法提交：  " + message;
                } else {
                    message = "提交成功";
                }
                this.getView().updateView();
                this.getView().showSuccessNotification(message);
            }

        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    /** 品类筛选 **/
    private void findCate() {
        // 创建弹出页面对象，FormShowParameter表示弹出页面为动态表单
        FormShowParameter showParameter = new FormShowParameter();
        // 设置弹出页面的编码
        showParameter.setFormId("aos_mkt_reqcate");
        // 设置弹出页面标题
        // 设置页面关闭回调方法
        // CloseCallBack参数：回调插件，回调标识
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
            if (returnData.containsKey(KEY_CATE) && returnData.get(KEY_CATE) != null) {
                List<String> listCate = returnData.get(KEY_CATE);
                String cate = String.join(",", listCate);
                this.getView().getPageCache().put(KEY_CATE, cate);
            }
            this.getView().invokeOperation("refresh");
        }
    }
}
