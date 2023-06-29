package mkt.openapi.ListingReq;

import kd.bos.openapi.common.custom.annotation.ApiParam;

import java.io.Serializable;

/**
 * @author create by gk
 * @date 2022/12/9 11:08
 * @action
 */
public class AttaributeModel implements Serializable {
    @ApiParam("文件名")
    private String name;
    @ApiParam("数据")
    private String data;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "AttaributeModel{" +
                "name='" + name + '\'' +
                ", data='" + data + '\'' +
                '}';
    }
}
