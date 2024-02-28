package mkt.image.test;

import common.fnd.FndMsg;
import kd.bos.bill.events.LocateEvent;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.form.plugin.IMobFormPlugin;

import java.util.EventObject;

/**
 * @author aosom
 * @since 2024/2/27 19:21
 */
public class AosMktTestMob extends AbstractFormPlugin implements IMobFormPlugin
{

    @Override
    public void afterCreateNewData(EventObject e) {
        FndMsg.debug("======================afterCreateNewData======================");

    }

    @Override
    public void uploadFile(EventObject eventObject) {

    }

    @Override
    public void locate(LocateEvent locateEvent) {

    }



}
