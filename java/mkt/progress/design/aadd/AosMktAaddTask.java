package mkt.progress.design.aadd;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;

import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;

/**
 * @author aosom
 * @version 每日定时取前一天新出运的SKU，SKU在高级A+进度表存在，但出运国别下没有打√
 */
public class AosMktAaddTask extends AbstractTask {
    public static void doOperate() {
        // 获取当前日期
        Calendar date = Calendar.getInstance();
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        date.add(Calendar.DAY_OF_MONTH, -1);
        String yester = df.format(date.getTime());
        DynamicObjectCollection bdMaterialS =
            QueryServiceHelper.query("bd_material", "id itemid,aos_contryentry.aos_nationality.number orgnumber",
                new QFilter("aos_contryentry.aos_firstshipment", QCP.like, yester + "%").toArray());
        for (DynamicObject bdMaterial : bdMaterialS) {
            Object itemid = bdMaterial.get("itemid");
            String orgnumber = bdMaterial.getString("orgnumber");
            // 1.若进度表目标国别下没有√，则按照原逻辑判断
            DynamicObject aosMktAddtrack = BusinessDataServiceHelper.loadSingleFromCache("aos_mkt_addtrack",
                new QFilter("aos_itemid", QCP.equals, itemid).and("aos_" + orgnumber, QCP.equals, false).toArray());
            if (FndGlobal.IsNotNull(aosMktAddtrack)) {
                DynamicObject aosItemid = aosMktAddtrack.getDynamicObject("aos_itemid");
                String aosProductno = aosItemid.getString("aos_productno");
                if ("US,CA,UK".contains(orgnumber)) {
                    // 判断同产品号是否有英语国别制作完成
                    boolean exists3 = QueryServiceHelper.exists("aos_mkt_addtrack",
                        (new QFilter("aos_itemid.aos_productno", QCP.equals, aosProductno)
                            .and(new QFilter("aos_us", QCP.equals, true).or("aos_ca", QCP.equals, true).or("aos_uk",
                                QCP.equals, true)))
                            .toArray());
                    // 存在英语国别制作完成 到05节点 不存在 到04节点
                    if (exists3) {
                        AosMktAaddBill.generateAddFromDesign(aosItemid, orgnumber, "EN_05");
                    } else {
                        AosMktAaddBill.generateAddFromDesign(aosItemid, orgnumber, "EN_04");
                    }
                } else if ("DE,FR,IT,ES".contains(orgnumber)) {
                    // 判断同产品号是否有小语种国别制作完成
                    boolean exists3 = QueryServiceHelper.exists("aos_mkt_addtrack",
                        (new QFilter("aos_itemid.aos_productno", QCP.equals, aosProductno)
                            .and(new QFilter("aos_de", QCP.equals, true).or("aos_fr", QCP.equals, true)
                                .or("aos_it", QCP.equals, true).or("aos_es", QCP.equals, true)))
                            .toArray());
                    // 存在小语种国别制作完成 到04节点 不存在 到02节点
                    if (exists3) {
                        AosMktAaddBill.generateAddFromDesign(aosItemid, orgnumber, "SM_04");
                    } else {
                        AosMktAaddBill.generateAddFromDesign(aosItemid, orgnumber, "SM_02");
                    }
                }
            } else {
                // 2.若进度表目标国别下有√，则生成新的国别新品的流程，流程直接到新品对比模块录入
                DynamicObject aosMktAddTrack = BusinessDataServiceHelper.loadSingleFromCache("aos_mkt_addtrack",
                    new QFilter("aos_itemid", QCP.equals, itemid).and("aos_" + orgnumber, QCP.equals, true).toArray());
                FndMsg.debug("into c");
                if (FndGlobal.IsNotNull(aosMktAddTrack)) {
                    FndMsg.debug("into b");
                    // 新的国别新品的流程，流程直接到新品对比模块录入
                    DynamicObject aosItemid = aosMktAddTrack.getDynamicObject("aos_itemid");
                    AosMktAaddBill.generateAddFromDesign(aosItemid, orgnumber, "EN_05");
                }
            }
        }
    }

    @Override
    public void execute(RequestContext arg0, Map<String, Object> arg1) throws KDException {
        doOperate();
    }
}
