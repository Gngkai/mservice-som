package mkt.data.keyword;

import common.CommonDataSomDis;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.metadata.IDataEntityProperty;
import kd.bos.entity.BadgeInfo;
import kd.bos.entity.EntityType;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.container.TabPage;
import kd.bos.form.control.Hyperlink;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.*;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import common.sal.util.QFBuilder;
import mkt.common.otel.MmsOtelUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * lch 2022-04-15
 *
 * @author aosom
 */

public class ItemKeywordBillPlugin extends AbstractBillPlugIn {
    private static final Tracer TRACER = MmsOtelUtils.getTracer(ItemKeywordBillPlugin.class, RequestContext.get());
    private static final String AOS_IMPKEYWORD = "aos_impkeyword";
    private static final String AOS_COPYTO = "aos_copyto";
    private static final String AOS_ORGID = "aos_orgid";
    private static final String AOS_ITEMID = "aos_itemid";
    private static final String ITEMS_SELECT = "items_select";
    private static final String AOS_MKT_KEYWORD_SL = "aos_mkt_keyword_sl";
    private static final String SAVE = "save";
    private static final String INSERT = "_insert";

    @Override
    public void afterBindData(EventObject e) {
        Span span = MmsOtelUtils.getCusMainSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
            setItemUrl();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private void setItemUrl() {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
            this.getModel().setValue("aos_textfield", "AM链接");
            Hyperlink hyperlink = this.getView().getControl("aos_hyperlinkap");
            DynamicObject aosOrgid = (DynamicObject)this.getModel().getValue("aos_orgid");
            DynamicObject aosItemid = (DynamicObject)this.getModel().getValue("aos_itemid");
            Map<String, Object> style = new HashMap<>(16);
            if (aosOrgid != null && aosItemid != null) {
                String itemId = aosItemid.getString("id");
                List<String> list = Collections.singletonList(itemId);
                Map<String, String> map = SalUtil.queryAsin(aosOrgid.getPkValue(), aosOrgid.getString("number"), list);
                if (map.containsKey(itemId)) {
                    String url = map.get(itemId);
                    String[] split = url.split("!");
                    if (split.length > 1) {
                        hyperlink.setUrl(split[0] + split[1]);
                        Map<String, Object> map1 = new HashMap<>(16);
                        style.put("text", map1);
                        map1.put("zh_CN", split[1]);
                        map1.put("en_US", split[1]);
                    }
                }
            }
            this.getView().updateControlMetadata("aos_hyperlinkap", style);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    @Override
    public void registerListener(EventObject e) {
        this.addItemClickListeners("tbmain");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
            String itemKey = evt.getItemKey();
            if (AOS_IMPKEYWORD.equals(itemKey)) {
                // 如果为引用关键词库
                importItemKeyword();
                statusControl();
            } else if (AOS_COPYTO.equals(itemKey)) {
                // 如果为引用关键词库
                CommonDataSomDis.popForm(this, "aos_mkt_itemselect", "items_select", null);
                statusControl();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        Span span = MmsOtelUtils.getCusMainSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
            String name = e.getProperty().getName();
            if (AOS_ORGID.equals(name)) {
                setItemUrl();
            } else if (AOS_ITEMID.equals(name)) {
                setItemUrl();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    @Override
    public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
        String actionId = closedCallBackEvent.getActionId();
        if (StringUtils.equals(actionId, ITEMS_SELECT) && null != closedCallBackEvent.getReturnData()) {
            // 这里返回对象为Object，可强转成相应的其他类型，
            // 单条数据可用String类型传输，返回多条数据可放入map中，也可使用json等方式传输
            @SuppressWarnings("unchecked")
            Map<String, List<String>> returnData = (Map<String, List<String>>)closedCallBackEvent.getReturnData();
            copyToData(returnData);
            getView().showSuccessNotification("Copy To Success (●'◡'●)");
        } else if (AOS_MKT_KEYWORD_SL.equals(actionId)) {
            String data = (String)closedCallBackEvent.getReturnData();
            syncPointDate(data);
        }

    }

    private void copyToData(Map<String, List<String>> returnData) {
        QFilter orgFilter = null;
        DynamicObject aosOrgid = (DynamicObject)this.getModel().getValue("aos_orgid");
        if (aosOrgid != null) {
            orgFilter = new QFilter("aos_orgid", QCP.equals, aosOrgid.getString("id"));
        }
        List<String> aosItems = returnData.get("itemIdList");
        QFilter itemFilter = new QFilter("aos_itemid", QCP.in, aosItems);

        List<String> tabList = returnData.get("tabList");
        StringJoiner selectField = new StringJoiner(",");
        selectField.add("id");
        Map<String, EntityType> allEntities = getModel().getDataEntityType().getAllEntities();
        // 添加页签字段
        for (String tab : tabList) {
            Map<String, IDataEntityProperty> fields = allEntities.get(tab).getFields();
            for (String field : fields.keySet()) {
                selectField.add(tab + "." + field);
            }
        }
        // 查找字段
        DynamicObject[] load = BusinessDataServiceHelper.load("aos_mkt_keyword", selectField.toString(),
            new QFilter[] {orgFilter, itemFilter});
        if (load.length == 0) {
            return;
        }
        // 开始赋值
        for (String tab : tabList) {
            // 当前界面数据
            DynamicObjectCollection sourceData = getModel().getDataEntity(true).getDynamicObjectCollection(tab);
            for (DynamicObject targetEntity : load) {
                DynamicObjectCollection targetRow = targetEntity.getDynamicObjectCollection(tab);
                targetRow.clear();
                for (DynamicObject sourceRow : sourceData) {
                    DynamicObject newRow = targetRow.addNew();
                    for (String field : allEntities.get(tab).getFields().keySet()) {
                        newRow.set(field, sourceRow.get(field));
                    }
                }
            }
        }
        SaveServiceHelper.save(load);
    }

    @Override
    public void beforeDoOperation(BeforeDoOperationEventArgs args) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
            FormOperate operate = (FormOperate)args.getSource();
            String operateKey = operate.getOperateKey();
            if (SAVE.equals(operateKey)) {
                beforeSave();
                statusControl();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 初始化事件
     **/
    @Override
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        statusControl();
    }

    /**
     * 界面状态控制
     */
    private void statusControl() {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
            DynamicObjectCollection aosLinentityGwS = this.getModel().getEntryEntity("aos_linentity_gw");
            if (FndGlobal.IsNotNull(aosLinentityGwS) && aosLinentityGwS.size() > 0) {
                TabPage control = this.getControl("aos_tabpageap3");
                BadgeInfo badgeInfo = new BadgeInfo();
                badgeInfo.setBadgeText("!");
                badgeInfo.setOffset(new String[] {"5px", "5px"});
                control.setBadgeInfo(badgeInfo);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 引入关键词库
     */
    private void importItemKeyword() {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
            // 1. 国别
            DynamicObject aosOrgid = (DynamicObject)this.getModel().getValue("aos_orgid");
            // 2. 物料
            StringJoiner str = new StringJoiner(",");
            str.add("id");
            str.add("aos_detail");
            QFBuilder builder = new QFBuilder();
            builder.add("aos_orgid", QCP.equals, aosOrgid.getString("id"));
            builder.add("aos_itemnamecn", "=", getModel().getValue("aos_itemname"));
            DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_point", str.toString(), builder.toArray());
            if (list == null || list.size() == 0) {
                this.getView().showTipNotification("无可用数据!");
            } else if (list.size() > 1) {
                // 弹出 属性细分选择框
                Map<String, Object> params = new HashMap<>(16);
                List<String> detailList = new ArrayList<>(list.size());
                for (DynamicObject row : list) {
                    detailList.add(row.getString("aos_detail"));
                }
                params.put("detailList", detailList);
                FndGlobal.OpenForm(this, "aos_mkt_keyword_sl", params);
            } else {
                syncPointDate(list.get(0).getString("aos_detail"));
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 保存前事件
     */
    private void beforeSave() {

        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
            DynamicObjectCollection sourcEntity =
                this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity");
            DynamicObjectCollection copyEntity =
                this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity1");
            copyEntity.removeIf(dy -> true);
            for (DynamicObject sourceRow : sourcEntity) {
                DynamicObject newRow = copyEntity.addNew();
                newRow.set("aos_pr_keyword", sourceRow.get("aos_mainvoc"));
                newRow.set("aos_pr_sort", sourceRow.get("aos_sort"));
                newRow.set("aos_pr_search", sourceRow.get("aos_search"));
                newRow.set("aos_pr_employ", sourceRow.get("aos_employ"));
                newRow.set("aos_pr_state", sourceRow.get("aos_promote"));
                newRow.set("aos_pr_lable", sourceRow.get("aos_attribute"));
            }
            getView().updateView("aos_entryentity1");
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    @Override
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        DynamicObject dataEntity = this.getModel().getDataEntity(true);
        Set<String> fields = new HashSet<>();
        fields.add("aos_hyperlinkap");
        fields.add("aos_textfield");
        SalUtil.skipVerifyFieldChanged(dataEntity, dataEntity.getDynamicObjectType(), fields);
    }

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs eventArgs) {
        super.afterDoOperation(eventArgs);
        String operateKey = eventArgs.getOperateKey();
        // 批量新增行
        if (operateKey.contains(INSERT)) {
            String[] split = operateKey.split("_");
            StringJoiner str = new StringJoiner("_");
            for (int i = 0; i < split.length - 1; i++) {
                str.add(split[i]);
            }
            int row = 1;
            Object insertRow = this.getModel().getValue("aos_insert_row");
            if (FndGlobal.IsNotNull(insertRow)) {
                row = Integer.parseInt(insertRow.toString());
            }
            this.getModel().batchCreateNewEntryRow(str.toString(), row);
        }
    }

    /**
     * 引用关键词数据
     * 
     * @param key 属性细分
     */
    private void syncPointDate(String key) {
        // 1. 国别
        DynamicObject aosOrgid = (DynamicObject)this.getModel().getValue("aos_orgid");
        // 2. 物料
        StringJoiner str = new StringJoiner(",");
        str.add("aos_linentity.aos_keyword aos_keyword");
        str.add("aos_linentity.aos_sort aos_sort");
        str.add("aos_linentity.aos_search aos_search");
        str.add("aos_linentity.aos_apply aos_apply");
        str.add("aos_linentity.aos_attribute aos_attribute");
        str.add("aos_linentity.aos_remake aos_remake");

        QFBuilder builder = new QFBuilder();
        builder.add("aos_orgid", QCP.equals, aosOrgid.getString("id"));
        builder.add("aos_itemnamecn", "=", getModel().getValue("aos_itemname"));
        builder.add("aos_detail", "=", key);
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_point", str.toString(), builder.toArray());
        System.out.println("list.size() = " + list.size());
        // 删除原来的
        this.getModel().deleteEntryData("aos_entryentity");
        // 取出关键词 赋值到关键词单据体
        for (DynamicObject obj : list) {
            int index = this.getModel().createNewEntryRow("aos_entryentity");
            getModel().setValue("aos_mainvoc", obj.get("aos_keyword"), index);
            getModel().setValue("aos_sort", obj.get("aos_sort"), index);
            getModel().setValue("aos_search", obj.get("aos_search"), index);
            getModel().setValue("aos_apply", obj.get("aos_apply"), index);
            getModel().setValue("aos_attribute", obj.get("aos_attribute"), index);
            getModel().setValue("aos_attribute", obj.get("aos_attribute"), index);
            getModel().setValue("aos_remake", obj.get("aos_remake"), index);
        }

        // 删除原来的
        str = new StringJoiner(",");
        str.add("aos_linentity_gw.aos_gw_keyword aos_gw_keyword");
        str.add("aos_linentity_gw.aos_gw_search aos_gw_search");
        DynamicObjectCollection listgw = QueryServiceHelper.query("aos_mkt_point", str.toString(), builder.toArray());
        System.out.println("listgw.size() = " + listgw.size());
        if (FndGlobal.IsNull(listgw) || list.size() == 0) {
            this.getView().showTipNotification("无官网可用数据!");
            return;
        }

        this.getModel().deleteEntryData("aos_linentity_gw");
        // 取出关键词 赋值到关键词单据体
        for (DynamicObject obj : listgw) {
            String aosGwKeyword = obj.getString("aos_gw_keyword");
            String aosGwSearch = obj.getString("aos_gw_search");
            int index = this.getModel().createNewEntryRow("aos_linentity_gw");
            this.getModel().setValue("aos_gw_keyword", aosGwKeyword, index);
            this.getModel().setValue("aos_gw_search", aosGwSearch, index);
        }

    }
}
