package io.metersphere.track.service;

import io.metersphere.api.service.ShareInfoService;
import io.metersphere.base.domain.TestPlan;
import io.metersphere.base.domain.TestPlanReport;
import io.metersphere.base.domain.TestPlanReportContentWithBLOBs;
import io.metersphere.base.domain.TestPlanWithBLOBs;
import io.metersphere.base.mapper.TestPlanMapper;
import io.metersphere.commons.constants.*;
import io.metersphere.commons.utils.BeanUtils;
import io.metersphere.commons.utils.CommonBeanFactory;
import io.metersphere.commons.utils.LogUtil;
import io.metersphere.dto.BaseSystemConfigDTO;
import io.metersphere.dto.UserDTO;
import io.metersphere.i18n.Translator;
import io.metersphere.notice.sender.NoticeModel;
import io.metersphere.notice.service.NoticeSendService;
import io.metersphere.service.ProjectService;
import io.metersphere.service.SystemParameterService;
import io.metersphere.service.UserService;
import io.metersphere.track.dto.TestPlanDTOWithMetric;
import org.apache.commons.beanutils.BeanMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

@Service
@Transactional
public class TestPlanMessageService {
    @Resource
    private ProjectService projectService;
    @Lazy
    @Resource
    private TestPlanService testPlanService;
    @Resource
    private UserService userService;
    @Resource
    private ShareInfoService shareInfoService;
    @Resource
    private TestPlanMapper testPlanMapper;
    @Lazy
    @Resource
    private TestPlanReportService testPlanReportService;

    public static final Integer FULL_MARKS = 100;


    @Async
    public void checkTestPlanStatusAndSendMessage(TestPlanReport report, TestPlanReportContentWithBLOBs testPlanReportContent, boolean sendMessage) {
        if (report == null) {
            return;
        }
        if (testPlanReportContent != null) {
            report = testPlanReportService.checkTestPlanReportHasErrorCase(report, testPlanReportContent);
        }
        if (!report.getIsApiCaseExecuting() && !report.getIsPerformanceExecuting() && !report.getIsScenarioExecuting()) {
            //更新TestPlan状态为完成
            TestPlanWithBLOBs testPlan = testPlanMapper.selectByPrimaryKey(report.getTestPlanId());
            if (testPlan != null
                    && !StringUtils.equalsAny(testPlan.getStatus(), TestPlanStatus.Completed.name(), TestPlanStatus.Finished.name())) {

                testPlan.setStatus(calcTestPlanStatusWithPassRate(testPlan));
                testPlanService.editTestPlan(testPlan);
            }
            try {
                if (sendMessage && testPlan != null && StringUtils.equalsAny(report.getTriggerMode(),
                        ReportTriggerMode.MANUAL.name(),
                        ReportTriggerMode.API.name(),
                        ReportTriggerMode.SCHEDULE.name()) && !StringUtils.equalsIgnoreCase(report.getStatus(), ExecuteResult.TEST_PLAN_RUNNING.toString())
                ) {
                    //发送通知
                    this.sendMessage(testPlan, report, testPlan.getProjectId());
                }
            } catch (Exception e) {
                LogUtil.error(e);
            }
        }
    }

    public String calcTestPlanStatusWithPassRate(TestPlanWithBLOBs testPlan) {
        try {
            // 计算通过率
            TestPlanDTOWithMetric testPlanDTOWithMetric = BeanUtils.copyBean(new TestPlanDTOWithMetric(), testPlan);
            testPlanService.calcTestPlanRate(Collections.singletonList(testPlanDTOWithMetric));
            //测试进度
            Double testRate = Optional.ofNullable(testPlanDTOWithMetric.getTestRate()).orElse(0.0);
            //通过率
            Double passRate = Optional.ofNullable(testPlanDTOWithMetric.getPassRate()).orElse(0.0);

            // 已完成：测试进度=100% 且 通过率=100%
            if (testRate >= FULL_MARKS && passRate >= FULL_MARKS) {
                return TestPlanStatus.Completed.name();
            }

            // 已结束：超过了计划结束时间（如有） 或 测试进度=100% 且 通过率非100%
            Long plannedEndTime = testPlan.getPlannedEndTime();
            long currentTime = System.currentTimeMillis();
            if(Objects.nonNull(plannedEndTime) && currentTime >= plannedEndTime){
                return TestPlanStatus.Finished.name();
            }

            if(testRate >= FULL_MARKS && passRate < FULL_MARKS){
                return TestPlanStatus.Finished.name();
            }

        } catch (Exception e) {
            LogUtil.error("计算通过率失败！", e);
        }

        // 进行中：0 < 测试进度 < 100%
        return TestPlanStatus.Underway.name();
    }

    @Async
    public void sendMessage(TestPlan testPlan, TestPlanReport testPlanReport, String projectId) {
        assert testPlan != null;
        SystemParameterService systemParameterService = CommonBeanFactory.getBean(SystemParameterService.class);
        NoticeSendService noticeSendService = CommonBeanFactory.getBean(NoticeSendService.class);
        assert systemParameterService != null;
        assert noticeSendService != null;
        BaseSystemConfigDTO baseSystemConfigDTO = systemParameterService.getBaseInfo();
        String url = baseSystemConfigDTO.getUrl() + "/#/track/testPlan/reportList";
        String subject = "";
        String successContext = "${operator}执行的 ${name} 测试计划运行成功, 报告: ${planShareUrl}";
        String failedContext = "${operator}执行的 ${name} 测试计划运行失败, 报告: ${planShareUrl}";
        String context = "${operator}完成了测试计划: ${name}, 报告: ${planShareUrl}";
        if (StringUtils.equals(testPlanReport.getTriggerMode(), ReportTriggerMode.API.name())) {
            subject = "Jenkins任务通知";
        } else {
            subject = "任务通知";
        }
        // 计算通过率
        TestPlanDTOWithMetric testPlanDTOWithMetric = BeanUtils.copyBean(new TestPlanDTOWithMetric(), testPlan);
        testPlanService.calcTestPlanRate(Collections.singletonList(testPlanDTOWithMetric));

        String creator = testPlanReport.getCreator();
        UserDTO userDTO = userService.getUserDTO(creator);

        Map paramMap = new HashMap();
        paramMap.put("type", "testPlan");
        paramMap.put("url", url);
        paramMap.put("projectId", projectId);
        if (userDTO != null) {
            paramMap.put("operator", userDTO.getName());
            paramMap.put("executor", userDTO.getId());
        }
        paramMap.putAll(new BeanMap(testPlanDTOWithMetric));

        String testPlanShareUrl = shareInfoService.getTestPlanShareUrl(testPlanReport.getId(), creator);
        paramMap.put("planShareUrl", baseSystemConfigDTO.getUrl() + "/sharePlanReport" + testPlanShareUrl);

        /**
         * 测试计划的消息通知配置包括 完成、成功、失败
         * 所以发送通知时必定会有"完成"状态的通知
         */
        Map<String, String> execStatusEventMap = new HashMap<>();
        execStatusEventMap.put(TestPlanReportStatus.COMPLETED.name(), NoticeConstants.Event.COMPLETE);
        if (StringUtils.equalsIgnoreCase(testPlanReport.getStatus(), TestPlanReportStatus.SUCCESS.name())) {
            execStatusEventMap.put(testPlanReport.getStatus(), NoticeConstants.Event.EXECUTE_SUCCESSFUL);
        } else if (StringUtils.equalsIgnoreCase(testPlanReport.getStatus(), TestPlanReportStatus.FAILED.name())) {
            execStatusEventMap.put(testPlanReport.getStatus(), NoticeConstants.Event.EXECUTE_FAILED);
        } else if (!StringUtils.equalsIgnoreCase(testPlanReport.getStatus(), TestPlanReportStatus.COMPLETED.name())) {
            execStatusEventMap.put(testPlanReport.getStatus(), NoticeConstants.Event.COMPLETE);
        }
        for (Map.Entry<String, String> entry : execStatusEventMap.entrySet()) {
            String status = entry.getKey();
            String event = entry.getValue();
            NoticeModel noticeModel = NoticeModel.builder()
                    .operator(creator)
                    .context(context)
                    .successContext(successContext)
                    .failedContext(failedContext)
                    .testId(testPlan.getId())
                    .status(status)
                    .event(event)
                    .subject(subject)
                    .paramMap(paramMap)
                    .build();

            if (StringUtils.equals(testPlanReport.getTriggerMode(), ReportTriggerMode.MANUAL.name())) {
                noticeSendService.send(projectService.getProjectById(projectId), NoticeConstants.TaskType.TEST_PLAN_TASK, noticeModel);
            }

            if (StringUtils.equalsAny(testPlanReport.getTriggerMode(), ReportTriggerMode.SCHEDULE.name(), ReportTriggerMode.API.name())) {
                noticeSendService.send(testPlanReport.getTriggerMode(), NoticeConstants.TaskType.TEST_PLAN_TASK, noticeModel);
            }
        }
    }
}
