package io.metersphere.controller;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.base.domain.IssueTemplate;
import io.metersphere.commons.constants.OperLogConstants;
import io.metersphere.commons.constants.OperLogModule;
import io.metersphere.commons.constants.PermissionConstants;
import io.metersphere.commons.utils.PageUtils;
import io.metersphere.commons.utils.Pager;
import io.metersphere.controller.request.BaseQueryRequest;
import io.metersphere.controller.request.UpdateIssueTemplateRequest;
import io.metersphere.dto.IssueTemplateDao;
import io.metersphere.log.annotation.MsAuditLog;
import io.metersphere.service.IssueTemplateService;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RequestMapping("field/template/issue")
@RestController

public class IssueTemplateController {
    @Resource
    private IssueTemplateService issueTemplateService;

    @PostMapping("/add")
    @RequiresPermissions(PermissionConstants.PROJECT_TEMPLATE_READ_ISSUE_TEMPLATE)
    @MsAuditLog(module = OperLogModule.WORKSPACE_TEMPLATE_SETTINGS_ISSUE, type = OperLogConstants.CREATE, content = "#msClass.getLogDetails(#request)", msClass = IssueTemplateService.class)
    public void add(@RequestBody UpdateIssueTemplateRequest request) {
        issueTemplateService.add(request);
    }

    @PostMapping("/list/{goPage}/{pageSize}")
    @RequiresPermissions(PermissionConstants.PROJECT_TEMPLATE_READ_ISSUE_TEMPLATE)
    public Pager<List<IssueTemplate>> list(@PathVariable int goPage, @PathVariable int pageSize, @RequestBody BaseQueryRequest request) {
        Page<List<IssueTemplate>> page = PageHelper.startPage(goPage, pageSize, true);
        return PageUtils.setPageInfo(page, issueTemplateService.list(request));
    }

    @GetMapping("/delete/{id}")
    @RequiresPermissions(PermissionConstants.PROJECT_TEMPLATE_READ_ISSUE_TEMPLATE)
    @MsAuditLog(module = OperLogModule.WORKSPACE_TEMPLATE_SETTINGS_ISSUE, type = OperLogConstants.DELETE, beforeEvent = "#msClass.getLogDetails(#id)", msClass = IssueTemplateService.class)
    public void delete(@PathVariable(value = "id") String id) {
        issueTemplateService.delete(id);
    }

    @PostMapping("/update")
    @RequiresPermissions(PermissionConstants.PROJECT_TEMPLATE_READ_ISSUE_TEMPLATE)
    @MsAuditLog(module = OperLogModule.WORKSPACE_TEMPLATE_SETTINGS_ISSUE, type = OperLogConstants.UPDATE, beforeEvent = "#msClass.getLogDetails(#request.id,#request.customFields)", content = "#msClass.getLogDetails(#request)", msClass = IssueTemplateService.class)
    public void update(@RequestBody UpdateIssueTemplateRequest request) {
        issueTemplateService.update(request);
    }

    @GetMapping({"/option/{projectId}", "/option"})
    @RequiresPermissions(value = {PermissionConstants.PROJECT_TEMPLATE_READ_ISSUE_TEMPLATE,
            PermissionConstants.WORKSPACE_PROJECT_MANAGER_READ_CREATE, PermissionConstants.WORKSPACE_PROJECT_MANAGER_READ_EDIT}, logical = Logical.OR)
    public List<IssueTemplate> list(@PathVariable(required = false) String projectId) {
        return issueTemplateService.getOption(projectId);
    }

    @GetMapping("/get/relate/{projectId}")
    @RequiresPermissions(value = {PermissionConstants.PROJECT_TEMPLATE_READ_ISSUE_TEMPLATE, PermissionConstants.PROJECT_TRACK_ISSUE_READ}, logical = Logical.OR)
    public IssueTemplateDao getTemplate(@PathVariable String projectId) {
        return issueTemplateService.getTemplate(projectId);
    }
}
