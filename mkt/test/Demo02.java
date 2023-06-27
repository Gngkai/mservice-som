package mkt.test;

import kd.bos.entity.datamodel.IDataModel;
import kd.bos.form.IFormView;
import kd.bos.form.plugin.AbstractFormPlugin;

import java.util.EventObject;

public class Demo02 extends AbstractFormPlugin {
   // public void afterCreateNewDate(EventObject e){
   // this.getModel().getValue("aos_mkt_progress_test02");
   // }
   private final static String KEY_ENTRYENTITY = "aos_mkt_progress_test02";
   private final static String KEY_INTEGERFIELD1 = "aos_telephonefield";

   public void afterCreateNewDate(EventObject e){
      int rowCount = this.getModel().getEntryRowCount(KEY_ENTRYENTITY);
      if(rowCount < 3){
         this.getModel().batchCreateNewEntryRow(KEY_ENTRYENTITY,3 - rowCount);
         rowCount = 3;
      }

      for(int row = 0;row < rowCount;row++){
         int fldValue = row + 1;
         this.getModel().setValue(KEY_INTEGERFIELD1,fldValue,row);
      }
   }
}

