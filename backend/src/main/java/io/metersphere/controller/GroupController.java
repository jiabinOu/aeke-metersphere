package io.metersphere.controller;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.base.domain.Group;
import io.metersphere.base.domain.User;
import io.metersphere.base.domain.Workspace;
import io.metersphere.commons.constants.OperLogConstants;
import io.metersphere.commons.constants.OperLogModule;
import io.metersphere.commons.constants.PermissionConstants;
import io.metersphere.commons.utils.PageUtils;
import io.metersphere.commons.utils.Pager;
import io.metersphere.commons.utils.SessionUtils;
import io.metersphere.controller.request.GroupRequest;
import io.metersphere.controller.request.group.EditGroupRequest;
import io.metersphere.controller.request.group.EditGroupUserRequest;
import io.metersphere.dto.GroupDTO;
import io.metersphere.dto.GroupPermissionDTO;
import io.metersphere.log.annotation.MsAuditLog;
import io.metersphere.service.GroupService;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.annotation.Logical;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@RequestMapping("/user/group")
@RestController
public class GroupController {

    @Resource
    private GroupService groupService;

    @PostMapping("/get/{goPage}/{pageSize}")
    @RequiresPermissions(value = {PermissionConstants.SYSTEM_GROUP_READ}, logical = Logical.OR)
    public Pager<List<GroupDTO>> getGroupList(@PathVariable int goPage, @PathVariable int pageSize, @RequestBody EditGroupRequest request) {
        request.setGoPage(goPage);
        request.setPageSize(pageSize);
        return groupService.getGroupList(request);
    }

    @PostMapping("/get/current/project/{goPage}/{pageSize}")
    @RequiresPermissions(value = {PermissionConstants.PROJECT_GROUP_READ}, logical = Logical.OR)
    public Pager<List<GroupDTO>> getCurrentProjectGroupList(@PathVariable int goPage, @PathVariable int pageSize, @RequestBody EditGroupRequest request) {
        request.setOnlyQueryCurrentProject(true);
        request.setGoPage(goPage);
        request.setPageSize(pageSize);
        return groupService.getGroupList(request);
    }

    @GetMapping("/get/all")
    @RequiresPermissions(value = {PermissionConstants.SYSTEM_GROUP_READ, PermissionConstants.SYSTEM_USER_READ}, logical = Logical.OR)
    public List<GroupDTO> getAllGroup() {
        return groupService.getAllGroup();
    }

    @PostMapping("/get")
    @RequiresPermissions(value = {PermissionConstants.SYSTEM_GROUP_READ, PermissionConstants.SYSTEM_USER_READ}, logical = Logical.OR)
    public List<Group> getGroupByType(@RequestBody EditGroupRequest request) {
        return groupService.getGroupByType(request);
    }

    @PostMapping("/add")
    @RequiresPermissions(value = {PermissionConstants.SYSTEM_GROUP_READ_CREATE, PermissionConstants.PROJECT_GROUP_READ_CREATE}, logical = Logical.OR)
    @MsAuditLog(module = OperLogModule.GROUP_PERMISSION, type = OperLogConstants.CREATE, content = "#msClass.getLogDetails(#request.id)", msClass = GroupService.class)
    public Group addGroup(@RequestBody EditGroupRequest request) {
        request.setId(UUID.randomUUID().toString());
        return groupService.addGroup(request);
    }

    @PostMapping("/edit")
    @RequiresPermissions(value = {PermissionConstants.SYSTEM_GROUP_READ_EDIT, PermissionConstants.PROJECT_GROUP_READ_EDIT}, logical = Logical.OR)
    @MsAuditLog(module = OperLogModule.GROUP_PERMISSION, type = OperLogConstants.UPDATE, beforeEvent = "#msClass.getLogDetails(#request.id)", content = "#msClass.getLogDetails(#request.id)", msClass = GroupService.class)
    public void editGroup(@RequestBody EditGroupRequest request) {
        groupService.editGroup(request);
    }

    @GetMapping("/delete/{id}")
    @RequiresPermissions(value = {PermissionConstants.SYSTEM_GROUP_READ_DELETE, PermissionConstants.PROJECT_GROUP_READ_DELETE}, logical = Logical.OR)
    @MsAuditLog(module = OperLogModule.GROUP_PERMISSION, type = OperLogConstants.DELETE, beforeEvent = "#msClass.getLogDetails(#id)", msClass = GroupService.class)
    public void deleteGroup(@PathVariable String id) {
        groupService.deleteGroup(id);
    }

    @PostMapping("/permission")
    public GroupPermissionDTO getGroupResource(@RequestBody Group group) {
        return groupService.getGroupResource(group);
    }

    @PostMapping("/permission/edit")
    @RequiresPermissions(value = {PermissionConstants.SYSTEM_GROUP_READ_SETTING_PERMISSION, PermissionConstants.PROJECT_GROUP_READ_SETTING_PERMISSION}, logical = Logical.OR)
    public void editGroupPermission(@RequestBody EditGroupRequest editGroupRequest) {
        groupService.editGroupPermission(editGroupRequest);
    }

    @GetMapping("/all/{userId}")
    public List<Map<String, Object>> getAllUserGroup(@PathVariable("userId") String userId) {
        return groupService.getAllUserGroup(userId);
    }

    @PostMapping("/list")
    public List<Group> getGroupsByType(@RequestBody GroupRequest request) {
        return groupService.getGroupsByType(request);
    }

    @GetMapping("/list/ws/{workspaceId}/{userId}")
    public List<Group> getWorkspaceMemberGroups(@PathVariable String workspaceId, @PathVariable String userId) {
        return groupService.getWorkspaceMemberGroups(workspaceId, userId);
    }

    @GetMapping("/list/project/{projectId}/{userId}")
    public List<Group> getProjectMemberGroups(@PathVariable String projectId, @PathVariable String userId) {
        return groupService.getProjectMemberGroups(projectId, userId);
    }

    @GetMapping("/ws/{userId}")
    public List<Workspace> getWorkspace(@PathVariable String userId) {
        return groupService.getWorkspace(userId);
    }

    @GetMapping("/{type}/{id}")
    public List<?> getResource(@PathVariable String type, @PathVariable String id) {
        return groupService.getResource(type, id);
    }

    @PostMapping("/user/{goPage}/{pageSize}")
    @RequiresPermissions(value = {PermissionConstants.SYSTEM_GROUP_READ}, logical = Logical.OR)
    public Pager<List<User>> getGroupUser(@PathVariable int goPage, @PathVariable int pageSize, @RequestBody EditGroupRequest editGroupRequest) {
        Page<Object> page = PageHelper.startPage(goPage, pageSize, true);
        return PageUtils.setPageInfo(page, groupService.getGroupUser(editGroupRequest));
    }

    @PostMapping("/current/project/user/{goPage}/{pageSize}")
    @RequiresPermissions(value = {PermissionConstants.PROJECT_GROUP_READ}, logical = Logical.OR)
    public Pager<List<User>> getCurrentProjectGroupUser(@PathVariable int goPage, @PathVariable int pageSize, @RequestBody EditGroupRequest editGroupRequest) {
        editGroupRequest.setOnlyQueryCurrentProject(true);
        if (StringUtils.isBlank(editGroupRequest.getProjectId())) {
            editGroupRequest.setProjectId(SessionUtils.getCurrentProjectId());
        }
        Page<Object> page = PageHelper.startPage(goPage, pageSize, true);
        return PageUtils.setPageInfo(page, groupService.getGroupUser(editGroupRequest));
    }

    @GetMapping("/rm/{userId}/{groupId}")
    @RequiresPermissions(value = {PermissionConstants.PROJECT_GROUP_READ_DELETE, PermissionConstants.SYSTEM_GROUP_READ_DELETE}, logical = Logical.OR)
    public void removeGroupMember(@PathVariable String userId, @PathVariable String groupId) {
        groupService.removeGroupMember(userId, groupId);
    }

    @GetMapping("/source/{userId}/{groupId}")
    public List<?> getGroupSource(@PathVariable String userId, @PathVariable String groupId) {
        return groupService.getGroupSource(userId, groupId);
    }

    @PostMapping("/add/member")
    @RequiresPermissions(value = {PermissionConstants.SYSTEM_GROUP_READ_CREATE, PermissionConstants.PROJECT_GROUP_READ_CREATE}, logical = Logical.OR)
    public void addGroupUser(@RequestBody EditGroupUserRequest request) {
        groupService.addGroupUser(request);
    }

    @PostMapping("/edit/member")
    @RequiresPermissions(value = {PermissionConstants.SYSTEM_GROUP_READ_EDIT, PermissionConstants.PROJECT_GROUP_READ_EDIT}, logical = Logical.OR)
    public void editGroupUser(@RequestBody EditGroupUserRequest request) {
        groupService.editGroupUser(request);
    }
}
