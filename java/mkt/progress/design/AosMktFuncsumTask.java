package mkt.progress.design;

import java.util.Date;
import java.util.Map;

import common.Cux_Common_Utl;
import common.fnd.FndDate;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.entity.EntityMetadataCache;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import mkt.progress.ProgressUtil;
import mkt.progress.listing.AosMktListingMinBill;

/**
 * @author aosom
 * @version Listing优化需求表小语种 海外编辑确认:功能图 状态 三个工作日后自动提交
 */
public class AosMktFuncsumTask extends AbstractTask {
    /** 系统管理员 **/
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;

    @Override
    public void execute(RequestContext arg0, Map<String, Object> arg1) throws KDException {
        doOperate();
    }

    private void doOperate() {
        QFilter qfilterStatus = new QFilter("aos_status", QCP.in, new String[] {"海外编辑确认:功能图"});
        QFilter qfilterSmall = new QFilter("aos_orgsmall", "=", "");
        QFilter[] filters = new QFilter[] {qfilterStatus, qfilterSmall};
        DynamicObjectCollection aosMktListingMinS = QueryServiceHelper.query("aos_mkt_listing_min",
            "id,aos_funcdate" + ",aos_status,aos_orgid.number aos_orgnumber", filters);
        for (DynamicObject aosMktListingMin : aosMktListingMinS) {
            Object id = aosMktListingMin.get("id");
            Date aosFuncdate = aosMktListingMin.getDate("aos_funcdate");
            if (aosFuncdate == null)
            {
                continue;
            }
            if (FndDate.BetweenWeekendDays(new Date(), aosFuncdate) < 3)
            {
                continue; // 只处理三个工作日以上单据
            }
            String aosStatus = aosMktListingMin.getString("aos_status");
            DynamicObject aosMktListingMinold = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_listing_min");
            String aosOrgnumber = aosMktListingMin.getString("aos_orgnumber");
            // 设置功能图提交节点人为系统提交
            aosMktListingMinold.set("aos_submitter", "A");
            if ("海外编辑确认:功能图".equals(aosStatus)) {
                if ("IT".equals(aosOrgnumber)) {
                    // 插入功能图翻译台账
                    generateFuncSummary(id);
                    // 同时生成小站海外编辑确认:功能图 的listing优化需求小语种
                    generateOsSmall(id);
                }
                aosMktListingMinold.set("aos_status", "结束");
                aosMktListingMinold.set("aos_user", SYSTEM);
                FndHistory.Create(aosMktListingMinold, "提交", aosStatus);
                OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
                    new DynamicObject[] {aosMktListingMinold}, OperateOption.create());
                AosMktListingMinBill.setErrorList(aosMktListingMinold);
            }
        }
    }

    private void generateFuncSummary(Object id) {
        // 数据层
        DynamicObject aosMktListingMinold = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_listing_min");
        Object billno = aosMktListingMinold.get("billno");
        Object aosOrgid = aosMktListingMinold.get("aos_orgid");
        DynamicObjectCollection aosEntryentityS = aosMktListingMinold.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            DynamicObject aosMktFuncsumdata = BusinessDataServiceHelper.newDynamicObject("aos_mkt_funcsumdata");
            aosMktFuncsumdata.set("aos_orgid", aosOrgid);
            aosMktFuncsumdata.set("aos_itemid", aosEntryentity.get("aos_itemid"));
            aosMktFuncsumdata.set("aos_sourcebill", billno);
            aosMktFuncsumdata.set("aos_creationdate", new Date());
            aosMktFuncsumdata.set("aos_eng", "N");
            aosMktFuncsumdata.set("aos_sourceid", id);
            OperationServiceHelper.executeOperate("save", "aos_mkt_funcsumdata",
                new DynamicObject[] {aosMktFuncsumdata}, OperateOption.create());
        }
    }

    private void generateOsSmall(Object id) {
        DynamicObject aosMktListingMinold = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_listing_min");
        Object aosOrgsmall = FndGlobal.get_import_id("RO", "bd_country");
        DynamicObject aosMktListingMin = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_min");
        Object aosUser = null;
        QFilter filter = new QFilter("number", "=", "024044");
        DynamicObject bosUser = QueryServiceHelper.queryOne("bos_user", "id", new QFilter[] {filter});
        if (bosUser != null)
        {
            aosUser = bosUser.get("id");
        }
        AosMktListingMinBill.setListingMin(aosMktListingMinold, aosOrgsmall, aosUser, aosMktListingMin);
        DynamicObjectCollection aosEntryentityS = aosMktListingMinold.getDynamicObjectCollection("aos_entryentity");
        DynamicObjectCollection aosEntryentityNewS = aosMktListingMin.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            DynamicObject aosEntryentityNew = aosEntryentityNewS.addNew();
            aosEntryentityNew.set("aos_itemid", aosEntryentity.get("aos_itemid"));
            aosEntryentityNew.set("aos_is_saleout",
                ProgressUtil.Is_saleout(aosEntryentity.getDynamicObject("aos_itemid").getPkValue()));
            aosEntryentityNew.set("aos_require", aosEntryentity.get("aos_require"));
            aosEntryentityNew.set("aos_case", aosEntryentity.get("aos_case"));
            DynamicObjectCollection aosAttribute = aosEntryentityNew.getDynamicObjectCollection("aos_attribute");
            aosAttribute.clear();
            DynamicObjectCollection aosAttributefrom = aosEntryentity.getDynamicObjectCollection("aos_attribute");
            DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
            DynamicObject tempFile = null;
            for (DynamicObject d : aosAttributefrom) {
                tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
                aosAttribute.addNew().set("fbasedataid", tempFile);
            }
            AosMktListingMinBill.setEntryNew(aosEntryentity, aosEntryentityNew);
        }
        OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min", new DynamicObject[] {aosMktListingMin},
            OperateOption.create());
    }

}
