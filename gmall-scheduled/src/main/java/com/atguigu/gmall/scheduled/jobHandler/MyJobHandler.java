package com.atguigu.gmall.scheduled.jobHandler;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import org.springframework.stereotype.Component;

@Component
public class MyJobHandler {

    @XxlJob("myJobHandler")
    public ReturnT<String> execute(String param){
        XxlJobLogger.log("使用XXL-jobLogger 打印执行日志");
        System.out.println("我的任务执行完成了"+param + "线程为： "+Thread.currentThread().getName());
        return ReturnT.SUCCESS;
    }
}
