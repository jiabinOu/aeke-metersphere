package io.metersphere.track.controller;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.api.dto.IssuesStatusCountDao;
import io.metersphere.base.domain.Issues;
import io.metersphere.base.domain.IssuesDao;
import io.metersphere.base.domain.IssuesWithBLOBs;
import io.metersphere.commons.constants.NoticeConstants;
import io.metersphere.commons.constants.OperLogConstants;
import io.metersphere.commons.constants.OperLogModule;
import io.metersphere.commons.constants.PermissionConstants;
import io.metersphere.commons.utils.PageUtils;
import io.metersphere.commons.utils.Pager;
import io.metersphere.dto.IssueTemplateDao;
import io.metersphere.log.annotation.MsAuditLog;
import io.metersphere.notice.annotation.SendNotice;
import io.metersphere.track.dto.DemandDTO;
import io.metersphere.track.dto.PlatformStatusDTO;
import io.metersphere.track.issue.domain.PlatformUser;
import io.metersphere.track.issue.domain.jira.JiraIssueType;
import io.metersphere.track.issue.domain.zentao.ZentaoBuild;
import io.metersphere.track.request.issues.JiraIssueTypeRequest;
import io.metersphere.track.request.issues.PlatformIssueTypeRequest;
import io.metersphere.track.request.testcase.AuthUserIssueRequest;
import io.metersphere.track.request.testcase.IssuesRequest;
import io.metersphere.track.request.testcase.IssuesUpdateRequest;
import io.metersphere.track.service.IssuesService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RequestMapping("issues")
@RestController
public class IssuesController {

    @Resource
    private IssuesService issuesService;

    @PostMapping("/list/{goPage}/{pageSize}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public Pager<List<IssuesDao>> list(@PathVariable int goPage, @PathVariable int pageSize, @RequestBody IssuesRequest request) {
        Page<List<Issues>> page = PageHelper.startPage(goPage, pageSize, true);
        return PageUtils.setPageInfo(page, issuesService.list(request));
    }

    @PostMapping("/dashboard/list/{goPage}/{pageSize}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public Pager<List<IssuesDao>> listByWorkspaceId(@PathVariable int goPage, @PathVariable int pageSize, @RequestBody IssuesRequest request) {
        Page<List<Issues>> page = PageHelper.startPage(goPage, pageSize, true);
        return PageUtils.setPageInfo(page, issuesService.listByWorkspaceId(request));
    }

    @PostMapping("/list/relate/{goPage}/{pageSize}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public Pager<List<IssuesDao>> relateList(@PathVariable int goPage, @PathVariable int pageSize, @RequestBody IssuesRequest request) {
        Page<List<Issues>> page = PageHelper.startPage(goPage, pageSize, true);
        return PageUtils.setPageInfo(page, issuesService.relateList(request));
    }

    @PostMapping("/add")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ_CREATE)
    @MsAuditLog(module = OperLogModule.TRACK_BUG, type = OperLogConstants.CREATE, content = "#msClass.getLogDetails(#issuesRequest)", msClass = IssuesService.class)
    @SendNotice(taskType = NoticeConstants.TaskType.DEFECT_TASK, event = NoticeConstants.Event.CREATE, subject = "缺陷通知")
    public IssuesWithBLOBs addIssues(@RequestBody IssuesUpdateRequest issuesRequest) {
        return issuesService.addIssues(issuesRequest);
    }

    @PostMapping("/update")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ_EDIT)
    @MsAuditLog(module = OperLogModule.TRACK_BUG, type = OperLogConstants.UPDATE, beforeEvent = "#msClass.getLogDetails(#issuesRequest.id)", content = "#msClass.getLogDetails(#issuesRequest.id)", msClass = IssuesService.class)
    @SendNotice(taskType = NoticeConstants.TaskType.DEFECT_TASK, event = NoticeConstants.Event.UPDATE, subject = "缺陷通知")
    public IssuesWithBLOBs updateIssues(@RequestBody IssuesUpdateRequest issuesRequest) {
        return issuesService.updateIssues(issuesRequest);
    }

    @GetMapping("/get/case/{refType}/{id}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public List<IssuesDao> getIssues(@PathVariable String refType, @PathVariable String id) {
        return issuesService.getIssues(id, refType);
    }

    @GetMapping("/get/{id}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public IssuesWithBLOBs getIssue(@PathVariable String id) {
        return issuesService.getIssue(id);
    }

    @GetMapping("/plan/get/{planId}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public List<IssuesDao> getIssuesByPlanId(@PathVariable String planId) {
        return issuesService.getIssuesByPlanId(planId);
    }

    @GetMapping("/auth/{workspaceId}/{platform}")
    public void testAuth(@PathVariable String workspaceId, @PathVariable String platform) {
        issuesService.testAuth(workspaceId, platform);
    }

    @PostMapping("/user/auth")
    @RequiresPermissions(PermissionConstants.WORKSPACE_SERVICE_READ)
    public void userAuth(@RequestBody AuthUserIssueRequest authUserIssueRequest) {
        issuesService.userAuth(authUserIssueRequest);
    }

    @GetMapping("/close/{id}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ_EDIT)
    public void closeLocalIssue(@PathVariable String id) {
        issuesService.closeLocalIssue(id);
    }

    @PostMapping("/delete/relate")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public void deleteRelate(@RequestBody IssuesRequest request) {
        issuesService.deleteIssueRelate(request);
    }

    @GetMapping("/delete/{id}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ_DELETE)
    @MsAuditLog(module = OperLogModule.TRACK_BUG, type = OperLogConstants.DELETE, beforeEvent = "#msClass.getLogDetails(#id)", msClass = IssuesService.class)
    @SendNotice(taskType = NoticeConstants.TaskType.DEFECT_TASK, target = "#targetClass.get(#id)", targetClass = IssuesService.class, event = NoticeConstants.Event.DELETE, subject = "缺陷通知")
    public void delete(@PathVariable String id) {
        issuesService.delete(id);
    }

    @PostMapping("/tapd/user")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public List<PlatformUser> getTapdUsers(@RequestBody IssuesRequest request) {
        return issuesService.getTapdProjectUsers(request);
    }

    @PostMapping("/zentao/user")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public List<PlatformUser> getZentaoUsers(@RequestBody IssuesRequest request) {
        return issuesService.getZentaoUsers(request);
    }

    @PostMapping("/zentao/builds")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public List<ZentaoBuild> getZentaoBuilds(@RequestBody IssuesRequest request) {
        return issuesService.getZentaoBuilds(request);
    }

    @GetMapping("/sync/{projectId}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ_EDIT)
    public boolean getPlatformIssue(@PathVariable String projectId) {
        return issuesService.syncThirdPartyIssues(projectId);
    }

    @GetMapping("/sync/check/{projectId}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public boolean checkSync(@PathVariable String projectId) {
        return issuesService.checkSync(projectId);
    }

    @PostMapping("/change/status")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ_EDIT)
    public void changeStatus(@RequestBody IssuesRequest request) {
        issuesService.changeStatus(request);
    }

    @PostMapping("/status/count")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public List<IssuesStatusCountDao> getCountByStatus(@RequestBody IssuesRequest request) {
        return issuesService.getCountByStatus(request);
    }

    @GetMapping("/follow/{issueId}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public List<String> getFollows(@PathVariable String issueId) {
        return issuesService.getFollows(issueId);
    }

    @PostMapping("/up/follows/{issueId}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public void saveFollows(@PathVariable String issueId, @RequestBody List<String> follows) {
        issuesService.saveFollows(issueId, follows);
    }

    @GetMapping("/thirdpart/template/{projectId}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public IssueTemplateDao getThirdPartTemplate(@PathVariable String projectId) {
        return issuesService.getThirdPartTemplate(projectId);
    }

    @PostMapping("/jira/issuetype")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public List<JiraIssueType> getJiraIssueType(@RequestBody JiraIssueTypeRequest request) {
        return issuesService.getIssueTypes(request);
    }

    @GetMapping("/demand/list/{projectId}")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_CASE_READ)
    public List<DemandDTO> getDemandList(@PathVariable String projectId) {
        return issuesService.getDemandList(projectId);
    }

    @PostMapping("/platform/transitions")
    @RequiresPermissions(PermissionConstants.PROJECT_TRACK_ISSUE_READ)
    public List<PlatformStatusDTO> getPlatformTransitions(@RequestBody PlatformIssueTypeRequest request) {
        return issuesService.getPlatformTransitions(request);
    }
}
