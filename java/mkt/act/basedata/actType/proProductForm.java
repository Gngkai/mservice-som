package mkt.act.basedata.actType;

import common.fnd.FndGlobal;
import common.sal.sys.basedata.dao.ItemDao;
import common.sal.sys.basedata.dao.impl.ItemDaoImpl;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.orm.query.QFilter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author: GK
 * @create: 2023-12-21 15:09
 * @Description:  周销售推荐产品清单
 */
public class proProductForm extends AbstractBillPlugIn {
    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
    }

    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        init(-1);
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        super.propertyChanged(e);
        String name = e.getProperty().getName();
        if ("aos_item".equals(name)) {
            int rowIndex = e.getChangeSet()[0].getRowIndex();
            if (rowIndex>=0)
             init(rowIndex);
        }
    }

    private void init(int index){
        //设置组别
        Object aos_org = this.getModel().getValue("aos_org");
        if (aos_org == null )
            return;
        if (index <0) {
            DynamicObjectCollection entryentity = this.getModel().getEntryEntity("entryentity");
            if (entryentity.size()==0) {
                return;
            }
            List<String> list = new ArrayList<>(entryentity.size());
            for (DynamicObject row : entryentity) {
                DynamicObject aos_item = row.getDynamicObject("aos_item");
                if (aos_item==null) {
                    continue;
                }
                Object aos_season = row.get("aos_season");
                Object aos_itemstatus = row.get("aos_itemstatus");
                if (FndGlobal.IsNull(aos_season) || FndGlobal.IsNull(aos_itemstatus))
                    list.add(aos_item.getString("id"));
            }
            ItemDao itemDao = new ItemDaoImpl();
            QFilter filter = new QFilter("id",QFilter.in,list);
            filter.and("aos_contryentry.aos_nationality",QFilter.equals,((DynamicObject)aos_org).getString("id"));
            StringJoiner str = new StringJoiner(",");
            str.add("id");
            str.add("aos_contryentry.aos_seasonseting aos_seasonseting");
            str.add("aos_contryentry.aos_contryentrystatus aos_contryentrystatus");

            DynamicObjectCollection itemInfoes = itemDao.listItemObj(str.toString(), filter, null);
            Map<String, DynamicObject> map = itemInfoes
                    .stream()
                    .collect(Collectors.toMap(
                            dy -> dy.getString("id"),
                            dy -> dy, (key1, key2) -> key1));
            for (int i = 0; i < entryentity.size(); i++) {
                DynamicObject row = entryentity.get(i);
                DynamicObject aos_item = row.getDynamicObject("aos_item");
                if (aos_item==null) {
                    continue;
                }
                String id = aos_item.getString("id");
                if (map.containsKey(id)) {
                    DynamicObject info = map.get(id);
                    this.getModel().setValue("aos_season",info.getString("aos_seasonseting"),i);
                    this.getModel().setValue("aos_itemstatus",info.getString("aos_contryentrystatus"),i);
                }
            }
        }
        else {
            DynamicObject row = this.getModel().getEntryRowEntity("entryentity", index);
            List<String> list = new ArrayList<>();
            DynamicObject aos_item = row.getDynamicObject("aos_item");
            if (aos_item==null) {
                this.getModel().setValue("aos_season",null);
                this.getModel().setValue("aos_itemstatus",null);
                return;
            }
            list.add(aos_item.getString("id"));

            ItemDao itemDao = new ItemDaoImpl();
            QFilter filter = new QFilter("id",QFilter.in,list);
            filter.and("aos_contryentry.aos_nationality",QFilter.equals,((DynamicObject)aos_org).getString("id"));
            StringJoiner str = new StringJoiner(",");
            str.add("id");
            str.add("aos_contryentry.aos_seasonseting aos_seasonseting");
            str.add("aos_contryentry.aos_contryentrystatus aos_contryentrystatus");

            DynamicObjectCollection itemInfoes = itemDao.listItemObj(str.toString(), filter, null);

            if (itemInfoes.size()>0) {
                DynamicObject info = itemInfoes.get(0);
                this.getModel().setValue("aos_season",info.getString("aos_seasonseting"),index);
                this.getModel().setValue("aos_itemstatus",info.getString("aos_contryentrystatus"),index);
            }
        }
    }
}
