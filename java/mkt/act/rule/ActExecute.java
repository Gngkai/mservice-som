package mkt.act.rule;

import kd.bos.dataentity.entity.DynamicObject;

public class ActExecute {

    private final ActStrategy actStrategy;

    public ActExecute(ActStrategy actStrategy) {
        this.actStrategy = actStrategy;
    }

    public void execute(DynamicObject object)  throws Exception{
        actStrategy.doOperation(object);
    }
}
