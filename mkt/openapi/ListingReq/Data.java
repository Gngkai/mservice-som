package mkt.openapi.ListingReq;

import java.io.Serializable;
import java.util.List;

/**
 * @author create by gk
 * @date 2022/12/12 13:52
 * @action
 */
public class Data implements Serializable {
    private int successCount;
    private int failCount;
    private List<Result> result;
    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    public List<Result> getResult() {
        return result;
    }

    public void setResult(List<Result> result) {
        this.result = result;
    }
}
