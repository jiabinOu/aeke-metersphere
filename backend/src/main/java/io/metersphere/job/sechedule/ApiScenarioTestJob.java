package io.metersphere.job.sechedule;

import com.alibaba.fastjson.JSONObject;
import io.metersphere.api.dto.automation.ExecuteType;
import io.metersphere.api.dto.automation.RunScenarioRequest;
import io.metersphere.api.exec.ApiPoolDebugService;
import io.metersphere.api.service.ApiAutomationService;
import io.metersphere.commons.constants.ApiRunMode;
import io.metersphere.commons.constants.ReportTriggerMode;
import io.metersphere.commons.constants.ScheduleGroup;
import io.metersphere.commons.utils.CommonBeanFactory;
import io.metersphere.commons.utils.LogUtil;
import io.metersphere.constants.RunModeConstants;
import io.metersphere.dto.RunModeConfigDTO;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;
import org.quartz.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 情景测试Job
 *
 * @author song.tianyang
 * @Date 2020/12/22 2:59 下午
 * @Description
 */
public class ApiScenarioTestJob extends MsScheduleJob {

    private String projectID;

    private List<String> scenarioIds;

    private ApiAutomationService apiAutomationService;

    private ApiPoolDebugService apiPoolDebugService;


    public ApiScenarioTestJob() {
        apiAutomationService = CommonBeanFactory.getBean(ApiAutomationService.class);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        JobKey jobKey = context.getTrigger().getJobKey();
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        String resourceId = jobDataMap.getString("resourceId");
        this.userId = jobDataMap.getString("userId");
        this.expression = jobDataMap.getString("expression");
        this.projectID = jobDataMap.getString("projectId");
        if (resourceId != null) {
            scenarioIds = new ArrayList<>();
            scenarioIds.add(resourceId);
        }

        LogUtil.info(jobKey.getGroup() + " Running: " + resourceId);
        LogUtil.info("CronExpression: " + expression);
        businessExecute(context);
    }

    @Override
    void businessExecute(JobExecutionContext context) {
        RunScenarioRequest request = new RunScenarioRequest();
        String id = UUID.randomUUID().toString();
        request.setId(id);
        request.setReportId(id);
        request.setProjectId(projectID);
        request.setTriggerMode(ReportTriggerMode.SCHEDULE.name());
        request.setExecuteType(ExecuteType.Saved.name());
        request.setIds(this.scenarioIds);
        request.setReportUserID(this.userId);
        request.setRunMode(ApiRunMode.SCHEDULE_SCENARIO.name());
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        String config = jobDataMap.getString("config");
        if (StringUtils.isNotBlank(config)) {
            RunModeConfigDTO runModeConfig = JSONObject.parseObject(config, RunModeConfigDTO.class);
            request.setConfig(runModeConfig);
        } else {
            RunModeConfigDTO runModeConfigDTO = new RunModeConfigDTO();
            runModeConfigDTO.setMode(RunModeConstants.PARALLEL.toString());
            request.setConfig(runModeConfigDTO);
        }
        // 兼容历史定时任务默认给一个资源池
        if (StringUtils.isBlank(request.getConfig().getResourcePoolId())) {
            try {
                apiPoolDebugService.verifyPool(projectID, request.getConfig());
            } catch (Exception e) {
                LoggerUtil.error(e);
            }
        }

        apiAutomationService.run(request);
    }

    public static JobKey getJobKey(String testId) {
        return new JobKey(testId, ScheduleGroup.API_SCENARIO_TEST.name());
    }

    public static TriggerKey getTriggerKey(String testId) {
        return new TriggerKey(testId, ScheduleGroup.API_SCENARIO_TEST.name());
    }
}
