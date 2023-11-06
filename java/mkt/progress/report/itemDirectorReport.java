package mkt.progress.report;

import common.sal.sys.basedata.dao.ItemCategoryDao;
import common.sal.sys.basedata.dao.impl.ItemCategoryDaoImpl;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.datamodel.IDataModel;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author create by ?
 * @date 2023/5/12 14:38
 * @action
 */
public class itemDirectorReport extends AbstractFormPlugin {
    private final  static List<String> list_country;
    static {
        list_country = Arrays.asList("US", "CA", "UK", "DE", "FR", "IT", "ES");
    }
    @Override
    public void afterDoOperation(AfterDoOperationEventArgs eventArgs) {
        super.afterDoOperation(eventArgs);
        String operateKey = eventArgs.getOperateKey();
        if (operateKey.equals("aos_query")){
            querData();
        }
    }

    private void querData(){
        this.getModel().deleteEntryData("aos_entryentity");

        List<String> itemIds = getFilter();
        //查找大中小类
        ItemCategoryDao cateDao = new ItemCategoryDaoImpl();
        Map<String, DynamicObject> map_cate = cateDao.getItemCategoryByItemId("group.name group", itemIds);

        //采购和跟单
        QFilter  filter = new QFilter("aos_offerbillentry.aos_materiel",QFilter.in,itemIds);
        StringJoiner str = new StringJoiner(",");
        str.add("aos_offerbillentry.aos_materiel aos_materiel");
        str.add("aos_supplier.aos_purer aos_purer");
        str.add("aos_supplier.aos_documentary aos_documentary");
        str.add("aos_offerbillentry.aos_updatetime aos_updatetime");
        DataSet dataSet = QueryServiceHelper.queryDataSet(Thread.currentThread().getName(),"aos_offerbill", str.toString(), new QFilter[]{filter}, null);
        dataSet = dataSet.groupBy(new String[]{"aos_materiel"}).maxP("aos_updatetime", "aos_purer").maxP("aos_updatetime", "aos_documentary").finish();
        Map<String,String> map_pur = new HashMap<>();
        for (Row row : dataSet) {
            if (row.get("aos_purer") == null)
                continue;
            if (row.getString("aos_documentary")==null)
                continue;
            map_pur.put(row.getString("aos_materiel"),row.get("aos_purer")+"/"+row.getString("aos_documentary"));
        }
        dataSet.close();


        //编辑 和 设计
        Map<String, DynamicObject> map_edit = QueryServiceHelper.query("aos_mkt_proguser", "aos_category1,aos_category2,aos_eng,aos_designer", null)
                .stream()
                .collect(Collectors.toMap(
                        dy -> dy.getString("aos_category1") + "/" + dy.getString("aos_category2"),
                        dy -> dy,
                        (key1, key2) -> key1
                ));

        //销售人员
        str = new StringJoiner(",");
        str.add("aos_orgid.number aos_orgid");
        str.add("aos_category1");
        str.add("aos_category2");
        str.add("aos_02hq");
        Map<String, DynamicObject> map_sale = QueryServiceHelper.query("aos_mkt_progorguser", str.toString(), null)
                .stream()
                .collect(Collectors.toMap(
                        dy -> dy.getString("aos_orgid") + "/" + dy.getString("aos_category1") + "/" + dy.getString("aos_category2"),
                        dy -> dy,
                        (key1, key2) -> key1
                ));

        IDataModel dataModel = this.getModel();
        dataModel.batchCreateNewEntryRow("aos_entryentity",itemIds.size());
        for (int index = 0; index < itemIds.size(); index++) {
            String itemId = itemIds.get(index);

            dataModel.setValue("aos_item",itemId,index);
            //记录大类和中类
            String cate = "";
            //品类
            if (map_cate.containsKey(itemId)){
                String itemCate = map_cate.get(itemId).getString("group");
                String[] split = itemCate.split(",");
                for (int i = 0; i < split.length; i++) {
                   dataModel.setValue("aos_cate"+(i+1),split[i],index);
                }
                if (split.length>=2) {
                    cate = split[0]+"/"+split[1];
                }
            }
            //采购和跟单
            if (map_pur.containsKey(itemId)){
                String user = map_pur.get(itemId);
                String[] split = user.split("/");
                dataModel.setValue("aos_procure",split[0],index);
                dataModel.setValue("aos_documentary",split[1],index);
            }
            //编辑 设计
            if (map_edit.containsKey(cate)){
                DynamicObject dy_temp = map_edit.get(cate);
                dataModel.setValue("aos_edit",dy_temp.get("aos_eng"),index);
                dataModel.setValue("aos_design",dy_temp.get("aos_designer"),index);
            }
            for (String country : list_country) {
                String key = country+"/"+cate;
                if (map_sale.containsKey(key)){
                    DynamicObject dy = map_sale.get(key);
                    dataModel.setValue("aos_sale_"+country.toLowerCase(),dy.get("aos_02hq"),index);
                }
            }
        }
//        this.getModel().updateEntryCache(dyc_row);
//        this.getView().updateView("aos_entryentity");
    }
    private List<String> getFilter(){
        DynamicObjectCollection dyc_item = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_sku");
        List<String> results = new ArrayList<>();
        for (DynamicObject dy : dyc_item) {
            results.add(dy.getDynamicObject("fbasedataid").getString("id"));
        }
        return results;
    }
}
