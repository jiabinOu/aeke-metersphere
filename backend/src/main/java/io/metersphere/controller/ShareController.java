package io.metersphere.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.api.dto.APIReportResult;
import io.metersphere.api.dto.automation.APIScenarioReportResult;
import io.metersphere.api.dto.automation.TestPlanFailureApiDTO;
import io.metersphere.api.dto.automation.TestPlanFailureScenarioDTO;
import io.metersphere.api.service.ApiDefinitionService;
import io.metersphere.api.service.ApiScenarioReportService;
import io.metersphere.api.service.ShareInfoService;
import io.metersphere.base.domain.IssuesDao;
import io.metersphere.base.domain.LoadTestReportLog;
import io.metersphere.commons.constants.ResourcePoolTypeEnum;
import io.metersphere.commons.constants.ResourceStatusEnum;
import io.metersphere.commons.utils.PageUtils;
import io.metersphere.commons.utils.Pager;
import io.metersphere.controller.request.resourcepool.QueryResourcePoolRequest;
import io.metersphere.dto.LogDetailDTO;
import io.metersphere.dto.ReportDTO;
import io.metersphere.dto.RequestResult;
import io.metersphere.dto.TestResourcePoolDTO;
import io.metersphere.performance.base.*;
import io.metersphere.performance.dto.LoadTestExportJmx;
import io.metersphere.performance.dto.MetricData;
import io.metersphere.performance.dto.Monitor;
import io.metersphere.performance.service.MetricQueryService;
import io.metersphere.performance.service.PerformanceReportService;
import io.metersphere.performance.service.PerformanceTestService;
import io.metersphere.service.TestResourcePoolService;
import io.metersphere.track.dto.TestPlanCaseDTO;
import io.metersphere.track.dto.TestPlanLoadCaseDTO;
import io.metersphere.track.dto.TestPlanSimpleReportDTO;
import io.metersphere.track.request.testplan.LoadCaseReportRequest;
import io.metersphere.track.service.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.util.List;

@RestController
@RequestMapping("share")
public class ShareController {

    @Resource
    TestPlanService testPlanService;
    @Resource
    TestPlanTestCaseService testPlanTestCaseService;
    @Resource
    ApiDefinitionService apiDefinitionService;
    @Resource
    TestPlanApiCaseService testPlanApiCaseService;
    @Resource
    TestPlanScenarioCaseService testPlanScenarioCaseService;
    @Resource
    ApiScenarioReportService apiScenarioReportService;
    @Resource
    TestPlanLoadCaseService testPlanLoadCaseService;
    @Resource
    IssuesService issuesService;
    @Resource
    ShareInfoService shareInfoService;
    @Resource
    PerformanceReportService performanceReportService;
    @Resource
    PerformanceTestService performanceTestService;
    @Resource
    TestPlanReportService testPlanReportService;
    @Resource
    MetricQueryService metricService;
    @Resource
    private TestResourcePoolService testResourcePoolService;
    @Resource
    private ApiScenarioReportService apiReportService;

    @GetMapping("/issues/plan/get/{shareId}/{planId}")
    public List<IssuesDao> getIssuesByPlanoId(@PathVariable String shareId, @PathVariable String planId) {
        shareInfoService.validate(shareId, planId);
        return issuesService.getIssuesByPlanId(planId);
    }

    @GetMapping("/test/plan/report/{shareId}/{planId}")
    public TestPlanSimpleReportDTO getReport(@PathVariable String shareId, @PathVariable String planId) {
        shareInfoService.validate(shareId, planId);
        return testPlanService.getReport(planId, null);
    }

    @GetMapping("/report/export/{shareId}/{planId}/{lang}")
    public void exportHtmlReport(@PathVariable String shareId, @PathVariable String planId,
                                 @PathVariable(required = false) String lang, HttpServletResponse response) throws UnsupportedEncodingException {
        shareInfoService.validate(shareId, planId);
        testPlanService.exportPlanReport(planId, lang, response);
    }

    @GetMapping("/test/plan/case/list/failure/{shareId}/{planId}")
    public List<TestPlanCaseDTO> getFailureCases(@PathVariable String shareId, @PathVariable String planId) {
        shareInfoService.validate(shareId, planId);
        return testPlanTestCaseService.getFailureCases(planId);
    }

    @GetMapping("/test/plan/case/list/all/{shareId}/{planId}")
    public List<TestPlanCaseDTO> getAllCases(@PathVariable String shareId, @PathVariable String planId) {
        shareInfoService.validate(shareId, planId);
        return testPlanTestCaseService.getAllCases(planId);
    }

    @GetMapping("/test/plan/load/case/list/failure/{shareId}/{planId}")
    public List<TestPlanLoadCaseDTO> getLoadFailureCases(@PathVariable String shareId, @PathVariable String planId) {
        shareInfoService.validate(shareId, planId);
        return testPlanLoadCaseService.getFailureCases(planId);
    }

    @GetMapping("/test/plan/load/case/list/all/{shareId}/{planId}")
    public List<TestPlanLoadCaseDTO> getLoadAllCases(@PathVariable String shareId, @PathVariable String planId) {
        shareInfoService.validate(shareId, planId);
        return testPlanLoadCaseService.getAllCases(planId);
    }

    @GetMapping("/test/plan/api/case/list/failure/{shareId}/{planId}")
    public List<TestPlanFailureApiDTO> getApiFailureList(@PathVariable String shareId, @PathVariable String planId) {
        shareInfoService.validate(shareId, planId);
        return testPlanApiCaseService.getFailureCases(planId);
    }

    @GetMapping("/test/plan/api/case/list/errorReport/{shareId}/{planId}")
    public List<TestPlanFailureApiDTO> getErrorReportApiCaseList(@PathVariable String shareId, @PathVariable String planId) {
        shareInfoService.validate(shareId, planId);
        return testPlanApiCaseService.getErrorReportCases(planId);
    }

    @GetMapping("/test/plan/api/case/list/unExecute/{shareId}/{planId}")
    public List<TestPlanFailureApiDTO> getUnExecuteCases(@PathVariable String shareId, @PathVariable String planId) {
        shareInfoService.validate(shareId, planId);
        return testPlanApiCaseService.getUnExecuteCases(planId);
    }

    @GetMapping("/test/plan/api/case/list/all/{shareId}/{planId}")
    public List<TestPlanFailureApiDTO> getApiAllList(@PathVariable String shareId, @PathVariable String planId) {
        shareInfoService.validate(shareId, planId);
        return testPlanApiCaseService.getAllCases(planId);
    }

    @GetMapping("/test/plan/scenario/case/list/failure/{shareId}/{planId}")
    public List<TestPlanFailureScenarioDTO> getScenarioFailureList(@PathVariable String shareId, @PathVariable String planId) {
        shareInfoService.validate(shareId, planId);
        return testPlanScenarioCaseService.getFailureCases(planId);
    }

    @GetMapping("/test/plan/scenario/case/list/all/{shareId}/{planId}")
    public List<TestPlanFailureScenarioDTO> getScenarioAllList(@PathVariable String shareId, @PathVariable String planId) {
        shareInfoService.validate(shareId, planId);
        return testPlanScenarioCaseService.getAllCases(planId);
    }

    @GetMapping("/test/plan/scenario/case/list/errorReport/{shareId}/{planId}")
    public List<TestPlanFailureScenarioDTO> getScenarioErrorReportList(@PathVariable String shareId, @PathVariable String planId) {
        shareInfoService.validate(shareId, planId);
        return testPlanScenarioCaseService.getErrorReportCases(planId);
    }

    @GetMapping("/test/plan/scenario/case/list/unExecute/{shareId}/{planId}")
    public List<TestPlanFailureScenarioDTO> getUnExecuteScenarioCases(@PathVariable String shareId, @PathVariable String planId) {
        shareInfoService.validate(shareId, planId);
        return testPlanScenarioCaseService.getUnExecuteCases(planId);
    }

    @GetMapping("/api/definition/report/getReport/{shareId}/{testId}")
    public APIReportResult getApiReport(@PathVariable String shareId, @PathVariable String testId) {
//        shareInfoService.apiReportValidate(shareId, testId);
        return apiDefinitionService.getDbResult(testId);
    }

    @GetMapping("/api/scenario/report/get/{shareId}/{reportId}")
    public APIScenarioReportResult get(@PathVariable String shareId, @PathVariable String reportId) {
        shareInfoService.validateExpired(shareId); // 测试计划，和接口都会用这个
        return apiScenarioReportService.get(reportId,false);
    }

    @GetMapping("/performance/report/{shareId}/{reportId}")
    public ReportDTO getLoadTestReport(@PathVariable String shareId, @PathVariable String reportId) {
        //todo
        return performanceReportService.getReportTestAndProInfo(reportId);
    }

    @GetMapping("/performance/report/content/report_time/{shareId}/{reportId}")
    public ReportTimeInfo getReportTimeInfo(@PathVariable String shareId, @PathVariable String reportId) {
        // todo
        return performanceReportService.getReportTimeInfo(reportId);
    }

    @PostMapping("/test/plan/load/case/report/exist/{shareId}")
    public Boolean isExistReport(@PathVariable String shareId, @RequestBody LoadCaseReportRequest request) {
        // testPlanLoadCaseService  todo checkout
        return testPlanLoadCaseService.isExistReport(request);
    }

    @GetMapping("/performance/report/get-advanced-config/{shareId}/{reportId}")
    public String getAdvancedConfig(@PathVariable String shareId, @PathVariable String reportId) {
        shareInfoService.validate(shareId, reportId);
        return performanceReportService.getAdvancedConfiguration(reportId);
    }

    @GetMapping("/performance/report/get-jmx-content/{reportId}")
    public List<LoadTestExportJmx> getJmxContents(@PathVariable String reportId) {
        return performanceReportService.getJmxContent(reportId);
    }

    @GetMapping("/performance/report/get-jmx-content/{shareId}/{reportId}")
    public LoadTestExportJmx getJmxContent(@PathVariable String shareId, @PathVariable String reportId) {
        return performanceReportService.getJmxContent(reportId).get(0);
    }

    @GetMapping("/performance/get-jmx-content/{shareId}/{testId}")
    public List<LoadTestExportJmx> getOldJmxContent(@PathVariable String shareId, @PathVariable String testId) {
//        checkPermissionService.checkPerformanceTestOwner(testId);
        return performanceTestService.getJmxContent(testId);
    }

    @GetMapping("/test/plan/report/db/{shareId}/{reportId}")
    public TestPlanSimpleReportDTO getTestPlanDbReport(@PathVariable String shareId, @PathVariable String reportId) {
        shareInfoService.validate(shareId, reportId);
        return testPlanReportService.getReport(reportId);
    }

    @GetMapping("/performance/report/content/testoverview/{shareId}/{reportId}")
    public TestOverview getTestOverview(@PathVariable String shareId, @PathVariable String reportId) {
        return performanceReportService.getTestOverview(reportId);
    }

    @GetMapping("/performance/report/content/load_chart/{shareId}/{reportId}")
    public List<ChartsData> getLoadChartData(@PathVariable String shareId, @PathVariable String reportId) {
        return performanceReportService.getLoadChartData(reportId);
    }

    @GetMapping("/performance/report/content/res_chart/{shareId}/{reportId}")
    public List<ChartsData> getResponseTimeChartData(@PathVariable String shareId, @PathVariable String reportId) {
        return performanceReportService.getResponseTimeChartData(reportId);
    }

    @GetMapping("/performance/report/content/error_chart/{shareId}/{reportId}")
    public List<ChartsData> getErrorChartData(@PathVariable String shareId, @PathVariable String reportId) {
        return performanceReportService.getErrorChartData(reportId);
    }

    @GetMapping("/performance/report/content/response_code_chart/{shareId}/{reportId}")
    public List<ChartsData> getResponseCodeChartData(@PathVariable String shareId, @PathVariable String reportId) {
        return performanceReportService.getResponseCodeChartData(reportId);
    }

    @GetMapping("/performance/report/content/{shareId}/{reportKey}/{reportId}")
    public List<ChartsData> getReportChart(@PathVariable String shareId, @PathVariable String reportKey, @PathVariable String reportId) {
        return performanceReportService.getReportChart(reportKey, reportId);
    }

    @GetMapping("/performance/report/content/{shareId}/{reportId}")
    public List<Statistics> getReportContent(@PathVariable String shareId, @PathVariable String reportId) {
        return performanceReportService.getReportStatistics(reportId);
    }

    @GetMapping("/performance/report/content/errors/{shareId}/{reportId}")
    public List<Errors> getReportErrors(@PathVariable String shareId, @PathVariable String reportId) {
        return performanceReportService.getReportErrors(reportId);
    }

    @GetMapping("/performance/report/content/errors_top5/{shareId}/{reportId}")
    public List<ErrorsTop5> getReportErrorsTop5(@PathVariable String shareId, @PathVariable String reportId) {
        return performanceReportService.getReportErrorsTOP5(reportId);
    }

    @GetMapping("/performance/report/log/resource/{shareId}/{reportId}")
    public List<LogDetailDTO> getResourceIds(@PathVariable String shareId, @PathVariable String reportId) {
        return performanceReportService.getReportLogResource(reportId);
    }

    @GetMapping("/performance/report/log/download/{reportId}/{resourceId}")
    public void downloadLog(@PathVariable String reportId, @PathVariable String resourceId, HttpServletResponse response) throws Exception {
        performanceReportService.downloadLog(response, reportId, resourceId);
    }

    @GetMapping("/performance/report/log/{shareId}/{reportId}/{resourceId}/{goPage}")
    public Pager<List<LoadTestReportLog>> logs(@PathVariable String shareId, @PathVariable String reportId, @PathVariable String resourceId, @PathVariable int goPage) {
        Page<Object> page = PageHelper.startPage(goPage, 1, true);
        return PageUtils.setPageInfo(page, performanceReportService.getReportLogs(reportId, resourceId));
    }

    @GetMapping("/metric/query/{shareId}/{id}")
    public List<MetricData> queryMetric(@PathVariable String shareId, @PathVariable("id") String reportId) {
        return metricService.queryMetric(reportId);
    }

    @GetMapping("/metric/query/resource/{shareId}/{id}")
    public List<Monitor> queryReportResource(@PathVariable String shareId, @PathVariable("id") String reportId) {
        return metricService.queryReportResource(reportId);
    }

    @GetMapping("/performance/report/get-load-config/{shareId}/{testId}")
    public String getLoadConfiguration(@PathVariable String shareId, @PathVariable String testId) {
        //checkPermissionService.checkPerformanceTestOwner(testId);
        return performanceTestService.getLoadConfiguration(testId);
    }

    @GetMapping("/performance/report/get-load-config/{reportId}")
    public String getLoadConfiguration(@PathVariable String reportId) {
        return performanceReportService.getLoadConfiguration(reportId);
    }

    @GetMapping("/testresourcepool/list/quota/valid")
    public List<TestResourcePoolDTO> getTestResourcePools() {
        QueryResourcePoolRequest resourcePoolRequest = new QueryResourcePoolRequest();
        resourcePoolRequest.setStatus(ResourceStatusEnum.VALID.name());
        // 数据脱敏
        // 仅对k8s操作
        List<TestResourcePoolDTO> testResourcePoolDTOS = testResourcePoolService.listResourcePools(resourcePoolRequest);
        testResourcePoolDTOS.stream()
                .filter(testResourcePoolDTO -> StringUtils.equals(ResourcePoolTypeEnum.K8S.name(), testResourcePoolDTO.getType()))
                .forEach(pool -> pool.getResources().forEach(resource -> {
                    String configuration = resource.getConfiguration();
                    JSONObject map = JSON.parseObject(configuration);
                    if (map.containsKey("token")) {
                        map.put("token", "******");
                    }
                    if (map.containsKey("masterUrl")) {
                        map.put("masterUrl", "******");
                    }
                    if (map.containsKey("jobTemplate")) {
                        map.put("jobTemplate", "******");
                    }
                    if (map.containsKey("namespace")) {
                        map.put("namespace", "******");
                    }
                    resource.setConfiguration(JSON.toJSONString(map));
                }));
        return testResourcePoolDTOS;
    }

    @GetMapping("/{shareId}/scenario/report/selectReportContent/{stepId}")
    public RequestResult selectReportContent(@PathVariable String stepId, @PathVariable String shareId) {
        shareInfoService.validateExpired(shareId);
        return apiReportService.selectReportContent(stepId);
    }

    @GetMapping("/api/definition/report/by/id/{reportId}")
    public APIReportResult getApiReport(@PathVariable String reportId) {
        return apiDefinitionService.getReportById(reportId);
    }
}
