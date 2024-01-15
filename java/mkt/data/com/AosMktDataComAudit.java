package mkt.data.com;

import common.fnd.FndGlobal;
import kd.bos.form.plugin.AbstractFormPlugin;

import java.util.Map;

/**
 * @author aosom
 */
public class AosMktDataComAudit {
    public static void audit(AbstractFormPlugin plugin, Map<String, Object> params) {
        FndGlobal.OpenForm(plugin, "aos_mkt_data_common", params);
    }
}
