/*
 * Copyright (c) 2019- 2019 threefish(https://gitee.com/threefish https://github.com/threefish) All Rights Reserved.
 * 本项目完全开源，商用完全免费。但请勿侵犯作者合法权益，如申请软著等。
 * 最后修改时间：2019/10/07 18:26:07
 * 源 码 地 址：https://gitee.com/threefish/NutzFw
 */

package com.nutzfw.core.plugin.flowable.behavior;

import com.nutzfw.core.plugin.flowable.assignment.FlowAssignment;
import com.nutzfw.core.plugin.flowable.constant.FlowConstant;
import com.nutzfw.core.plugin.flowable.dto.CandidateGroupsDTO;
import com.nutzfw.core.plugin.flowable.dto.CandidateUsersDTO;
import com.nutzfw.core.plugin.flowable.dto.FlowSubmitInfoDTO;
import com.nutzfw.core.plugin.flowable.dto.UserTaskExtensionDTO;
import com.nutzfw.core.plugin.flowable.enums.TaskReviewerScopeEnum;
import com.nutzfw.core.plugin.flowable.util.FlowUtils;
import com.nutzfw.modules.organize.enums.LeaderTypeEnum;
import com.nutzfw.modules.organize.service.DepartmentLeaderService;
import org.apache.commons.collections.CollectionUtils;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.flowable.task.service.TaskService;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.nutz.ioc.Ioc;
import org.nutz.lang.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author huchuc@vip.qq.com
 * @date: 2019/7/3
 */
@SuppressWarnings("all")
public class CustomUserTaskActivityBehavior extends UserTaskActivityBehavior {

    UserTaskExtensionDTO    taskExtensionDTO;
    DepartmentLeaderService departmentLeaderService;
    Ioc                     ioc;

    public CustomUserTaskActivityBehavior(UserTask userTask, DepartmentLeaderService departmentLeaderService, Ioc ioc) {
        super(userTask);
        this.departmentLeaderService = departmentLeaderService;
        this.ioc = ioc;
        taskExtensionDTO = FlowUtils.getUserTaskExtension(userTask);
        if (taskExtensionDTO != null) {
            userTask.setAssignee(null);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    protected void handleAssignments(TaskService taskService, String assignee, String owner, List<String> candidateUsers,
                                     List<String> candidateGroups, TaskEntity task, ExpressionManager expressionManager, DelegateExecution execution) {
        if (taskExtensionDTO != null) {
            //流程发起者
            String flowSubmitter = getExpressionValue(FlowConstant.SUBMITTER, expressionManager, execution);
            String flowSubmitterDeptId = getExpressionValue(FlowConstant.SUBMITTER_DEPT_ID, expressionManager, execution);
            switch (taskExtensionDTO.getTaskReviewerScope()) {
                case SINGLE_USER:
                    assignee = taskExtensionDTO.getAssignee();
                    break;
                case FLOW_SUBMITTER:
                    assignee = flowSubmitter;
                    break;
                case MULTIPLE_USERS:
                    candidateUsers = taskExtensionDTO.getCandidateUsers().stream().map(CandidateUsersDTO::getUserName).collect(Collectors.toList());
                    break;
                case USER_ROLE_GROUPS:
                    candidateGroups = taskExtensionDTO.getCandidateGroups().stream().map(CandidateGroupsDTO::getRoleCode).collect(Collectors.toList());
                    break;
                case DEPARTMENT_HEAD:
                case DEPARTMENT_LEADER:
                    if (taskExtensionDTO.getTaskReviewerScope() == TaskReviewerScopeEnum.DEPARTMENT_HEAD) {
                        //查询部门主管领导
                        candidateUsers = departmentLeaderService.queryUserNames(flowSubmitterDeptId, LeaderTypeEnum.HEAD);
                        if (candidateUsers.contains(flowSubmitter)) {
                            //如果自己就是部门主管领导则分配给再上级部门主管办理
                            candidateUsers = departmentLeaderService.queryIterationUserNames(flowSubmitterDeptId, LeaderTypeEnum.HEAD);
                        }
                    } else if (taskExtensionDTO.getTaskReviewerScope() == TaskReviewerScopeEnum.DEPARTMENT_LEADER) {
                        //查询部门分管领导
                        candidateUsers = departmentLeaderService.queryUserNames(flowSubmitterDeptId, LeaderTypeEnum.LEADER);
                        if (candidateUsers.contains(flowSubmitter)) {
                            //如果自己就是部门分管领导则分配给部门主管领导办理
                            candidateUsers = departmentLeaderService.queryUserNames(flowSubmitterDeptId, LeaderTypeEnum.HEAD);
                        }
                    }
                    if (CollectionUtils.isNotEmpty(candidateUsers) && candidateUsers.size() == 1) {
                        //只有一个符合条件的主管，直接将任务分配给该主管
                        assignee = candidateUsers.get(0);
                        candidateUsers = new ArrayList<>(0);
                    }
                    break;
                case JAVA_BEAN_ASSIGNMENT:
                    FlowAssignment flowAssignment = getFlowAssignment(taskExtensionDTO.getIocFlowAssignment());
                    assignee = flowAssignment.getAssignee(taskService, new FlowSubmitInfoDTO(flowSubmitter, flowSubmitterDeptId), assignee, owner, candidateUsers, candidateGroups, task, execution);
                    candidateUsers = flowAssignment.getCandidateUsers(taskService, new FlowSubmitInfoDTO(flowSubmitter, flowSubmitterDeptId), assignee, owner, candidateUsers, candidateGroups, task, execution);
                    candidateGroups = flowAssignment.getCandidateGroups(taskService, new FlowSubmitInfoDTO(flowSubmitter, flowSubmitterDeptId), assignee, owner, candidateUsers, candidateGroups, task, execution);
                    break;
                default:
                    break;
            }
            if (Strings.isBlank(assignee) && CollectionUtils.isEmpty(candidateUsers) && CollectionUtils.isEmpty(candidateGroups)) {
                //没有任何可以办理的人员或角色组
                throw new RuntimeException("找不到任何符合条件的下一步经办人！流程无法继续办理！");
            }
        }
        super.handleAssignments(taskService, assignee, owner, candidateUsers, candidateGroups, task, expressionManager, execution);
    }


    public FlowAssignment getFlowAssignment(String iocFlowAssignment) {
        FlowAssignment flowAssignment;
        try {
            if (iocFlowAssignment.startsWith("$ioc:")) {
                flowAssignment = ioc.get(FlowAssignment.class, iocFlowAssignment.substring(5));
            } else {
                throw new RuntimeException("表达式错误！");
            }
        } catch (Exception e) {
            throw new RuntimeException("获取 [" + TaskReviewerScopeEnum.JAVA_BEAN_ASSIGNMENT.getValue() + "]失败！", e);
        }
        return flowAssignment;
    }

    private String getExpressionValue(String expression, ExpressionManager expressionManager, DelegateExecution execution) {
        return expressionManager.createExpression("${" + expression + "}").getValue(execution).toString();
    }


}
