package com.nutzfw.modules.business.leave.delegate;

import com.nutzfw.core.plugin.flowable.delegate.AbstractJavaDelegate;
import org.flowable.engine.delegate.DelegateExecution;
import org.nutz.ioc.Ioc;
import org.nutz.ioc.loader.annotation.IocBean;

/**
 * @author 黄川 huchuc@vip.qq.com
 * @date: 2019/4/8
 * 自动化任务-- 可以推送、发邮件、发通知、数据入库、等等一系列自动化任务 -- 此类是一个demo 仅供参考
 */
@IocBean(args = {"refer:$ioc"}, name = "automatedTaskJavaDelegate")
public class AutomatedTaskJavaDelegate extends AbstractJavaDelegate {

    public AutomatedTaskJavaDelegate(Ioc ioc) {
        super(ioc);
    }

    /**
     * @param execution     原始执行信息
     * @param businessKeyId 业务信息
     */
    @Override
    public void execute(DelegateExecution execution, String businessKeyId) {
        System.out.println(businessKeyId);
    }

}