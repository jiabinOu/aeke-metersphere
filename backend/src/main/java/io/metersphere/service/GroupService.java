package io.metersphere.service;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.base.domain.*;
import io.metersphere.base.mapper.*;
import io.metersphere.base.mapper.ext.ExtGroupMapper;
import io.metersphere.base.mapper.ext.ExtUserGroupMapper;
import io.metersphere.commons.constants.UserGroupConstants;
import io.metersphere.commons.constants.UserGroupType;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.user.SessionUser;
import io.metersphere.commons.utils.*;
import io.metersphere.controller.request.GroupRequest;
import io.metersphere.controller.request.group.EditGroupRequest;
import io.metersphere.controller.request.group.EditGroupUserRequest;
import io.metersphere.dto.*;
import io.metersphere.i18n.Translator;
import io.metersphere.log.utils.ReflexObjectUtil;
import io.metersphere.log.vo.DetailColumn;
import io.metersphere.log.vo.OperatingLogDetails;
import io.metersphere.log.vo.system.SystemReference;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class GroupService {


    @Resource
    private ExtUserGroupMapper extUserGroupMapper;
    @Resource
    private GroupMapper groupMapper;
    @Resource
    private ExtGroupMapper extGroupMapper;
    @Resource
    private SqlSessionFactory sqlSessionFactory;
    @Resource
    private UserGroupPermissionMapper userGroupPermissionMapper;
    @Resource
    private UserGroupMapper userGroupMapper;
    @Resource
    private WorkspaceService workspaceService;
    @Resource
    private WorkspaceMapper workspaceMapper;
    @Resource
    private ProjectMapper projectMapper;

    private static final String GLOBAL = "global";

    private static final Map<String, List<String>> map = new HashMap<>(4) {{
        put(UserGroupType.SYSTEM, Arrays.asList(UserGroupType.SYSTEM, UserGroupType.WORKSPACE, UserGroupType.PROJECT));
        put(UserGroupType.WORKSPACE, Arrays.asList(UserGroupType.WORKSPACE, UserGroupType.PROJECT));
        put(UserGroupType.PROJECT, Collections.singletonList(UserGroupType.PROJECT));
    }};

    private static final Map<String, String> typeMap = new HashMap<>(4) {{
        put(UserGroupType.SYSTEM, "系统");
        put(UserGroupType.WORKSPACE, "工作空间");
        put(UserGroupType.PROJECT, "项目");
    }};

    public Pager<List<GroupDTO>> getGroupList(EditGroupRequest request) {
        SessionUser user = SessionUtils.getUser();
        List<UserGroupDTO> userGroup = extUserGroupMapper.getUserGroup(Objects.requireNonNull(user).getId(), request.getProjectId());
        List<String> groupTypeList = userGroup.stream().map(UserGroupDTO::getType).distinct().collect(Collectors.toList());
        return getGroups(groupTypeList, request);
    }

    public void buildUserInfo(List<GroupDTO> testCases) {
        List<String> userIds = new ArrayList();
        userIds.addAll(testCases.stream().map(GroupDTO::getCreator).collect(Collectors.toList()));
        if (!userIds.isEmpty()) {
            Map<String, String> userMap = ServiceUtils.getUserNameMap(userIds);
            testCases.forEach(caseResult -> {
                caseResult.setCreator(userMap.get(caseResult.getCreator()));
            });
        }
    }

    public Group addGroup(EditGroupRequest request) {
        Group group = new Group();
        checkGroupExist(request);
        group.setId(request.getId());
        group.setName(request.getName());
        group.setCreator(SessionUtils.getUserId());
        group.setDescription(request.getDescription());
        group.setSystem(false);
        group.setType(request.getType());
        group.setCreateTime(System.currentTimeMillis());
        group.setUpdateTime(System.currentTimeMillis());
        if (BooleanUtils.isTrue(request.getGlobal())) {
            group.setScopeId(GLOBAL);
        } else {
            group.setScopeId(request.getScopeId());
        }
        groupMapper.insertSelective(group);
        return group;
    }

    private void checkGroupExist(EditGroupRequest request) {
        String name = request.getName();
        String id = request.getId();
        GroupExample groupExample = new GroupExample();
        GroupExample.Criteria criteria = groupExample.createCriteria();
        criteria.andNameEqualTo(name);
        if (StringUtils.isNotBlank(id)) {
            criteria.andIdNotEqualTo(id);
        }
        List<Group> groups = groupMapper.selectByExample(groupExample);
        if (CollectionUtils.isNotEmpty(groups)) {
            MSException.throwException("用户组名称已存在！");
        }
    }

    public void editGroup(EditGroupRequest request) {
        if (StringUtils.equals(request.getId(), UserGroupConstants.ADMIN)) {
            MSException.throwException("系统管理员无法编辑！");
        }
        checkGroupExist(request);
        Group group = new Group();
        request.setScopeId(null);
        BeanUtils.copyBean(group, request);
        group.setUpdateTime(System.currentTimeMillis());
        groupMapper.updateByPrimaryKeySelective(group);
    }

    public void deleteGroup(String id) {
        Group group = groupMapper.selectByPrimaryKey(id);
        if (group != null) {
            if (BooleanUtils.isTrue(group.getSystem())) {
                MSException.throwException("系统用户组不支持删除！");
            }
        }
        groupMapper.deleteByPrimaryKey(id);

        UserGroupExample userGroupExample = new UserGroupExample();
        userGroupExample.createCriteria().andGroupIdEqualTo(id);
        userGroupMapper.deleteByExample(userGroupExample);

        UserGroupPermissionExample example = new UserGroupPermissionExample();
        example.createCriteria().andGroupIdEqualTo(id);
        userGroupPermissionMapper.deleteByExample(example);
    }

    public GroupPermissionDTO getGroupResource(Group g) {
        GroupPermissionDTO dto = new GroupPermissionDTO();
        InputStream permission = getClass().getResourceAsStream("/permission.json");
        String type = g.getType();
        String id = g.getId();
        UserGroupPermissionExample userGroupPermissionExample = new UserGroupPermissionExample();
        userGroupPermissionExample.createCriteria().andGroupIdEqualTo(id);
        List<UserGroupPermission> userGroupPermissions = userGroupPermissionMapper.selectByExample(userGroupPermissionExample);
        List<String> permissionList = userGroupPermissions.stream().map(UserGroupPermission::getPermissionId).collect(Collectors.toList());
        if (permission == null) {
            throw new RuntimeException("读取文件失败!");
        } else {
            GroupJson group;
            try {
                group = JSON.parseObject(permission, GroupJson.class);
                List<GroupResource> resource = group.getResource();
                List<GroupPermission> permissions = group.getPermissions();
                List<GroupResourceDTO> dtoPermissions = dto.getPermissions();
                dtoPermissions.addAll(getResourcePermission(resource, permissions, type, permissionList));
            } catch (IOException e) {
                LogUtil.error(e);
            }
        }
        return dto;
    }

    public void editGroupPermission(EditGroupRequest request) {
        List<GroupPermission> permissions = request.getPermissions();
        if (CollectionUtils.isEmpty(permissions)) {
            return;
        }

        UserGroupPermissionExample userGroupPermissionExample = new UserGroupPermissionExample();
        userGroupPermissionExample.createCriteria().andGroupIdEqualTo(request.getUserGroupId());
        userGroupPermissionMapper.deleteByExample(userGroupPermissionExample);

        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        UserGroupPermissionMapper mapper = sqlSession.getMapper(UserGroupPermissionMapper.class);
        String groupId = request.getUserGroupId();
        permissions.forEach(permission -> {
            if (BooleanUtils.isTrue(permission.getChecked())) {
                String permissionId = permission.getId();
                String resourceId = permission.getResourceId();
                UserGroupPermission groupPermission = new UserGroupPermission();
                groupPermission.setId(UUID.randomUUID().toString());
                groupPermission.setGroupId(groupId);
                groupPermission.setPermissionId(permissionId);
                groupPermission.setModuleId(resourceId);
                mapper.insert(groupPermission);
            }
        });
        sqlSession.flushStatements();
        if (sqlSession != null && sqlSessionFactory != null) {
            SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
        }
    }

    public List<Group> getGroupByType(EditGroupRequest request) {
        List<Group> list = new ArrayList<>();
        GroupExample example = new GroupExample();
        GroupExample.Criteria criteria = example.createCriteria();
        String type = request.getType();
        if (StringUtils.isBlank(type)) {
            return list;
        }

        if (!StringUtils.equals(type, UserGroupType.SYSTEM)) {
            criteria.andTypeEqualTo(type);
        }
        if (BooleanUtils.isTrue(request.isOnlyQueryGlobal())) {
            criteria.andScopeIdEqualTo(GLOBAL);
        }

        return groupMapper.selectByExample(example);
    }

    public List<Map<String, Object>> getAllUserGroup(String userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        UserGroupExample userGroupExample = new UserGroupExample();
        userGroupExample.createCriteria().andUserIdEqualTo(userId);
        List<UserGroup> userGroups = userGroupMapper.selectByExample(userGroupExample);
        List<String> groupsIds = userGroups.stream().map(UserGroup::getGroupId).distinct().collect(Collectors.toList());
        for (String id : groupsIds) {
            Group group = groupMapper.selectByPrimaryKey(id);
            String type = group.getType();
            Map<String, Object> map = new HashMap<>(2);
            map.put("type", id + "+" + type);
            WorkspaceResource workspaceResource = workspaceService.listResource(id, group.getType());
            List<String> collect = userGroups.stream().filter(ugp -> ugp.getGroupId().equals(id)).map(UserGroup::getSourceId).collect(Collectors.toList());
            map.put("ids", collect);
            if (StringUtils.equals(type, UserGroupType.WORKSPACE)) {
                map.put("workspaces", workspaceResource.getWorkspaces());
            }
            if (StringUtils.equals(type, UserGroupType.PROJECT)) {
                map.put("projects", workspaceResource.getProjects());
            }
            list.add(map);
        }
        return list;
    }

    public List<Group> getGroupsByType(GroupRequest request) {
        String resourceId = request.getResourceId();
        String type = request.getType();
        List<String> scopeList = Arrays.asList(GLOBAL, resourceId);
        if (StringUtils.equals(type, UserGroupType.PROJECT) && StringUtils.isNotBlank(request.getProjectId())) {
            scopeList = Arrays.asList(GLOBAL, resourceId, request.getProjectId());
        }
        GroupExample groupExample = new GroupExample();
        groupExample.createCriteria().andScopeIdIn(scopeList)
                .andTypeEqualTo(type);
        return groupMapper.selectByExample(groupExample);
    }

    public List<Group> getWorkspaceMemberGroups(String workspaceId, String userId) {
        return extUserGroupMapper.getWorkspaceMemberGroups(workspaceId, userId);
    }

    private List<GroupResourceDTO> getResourcePermission(List<GroupResource> resource, List<GroupPermission> permissions, String type, List<String> permissionList) {
        List<GroupResourceDTO> dto = new ArrayList<>();
        List<GroupResource> resources = resource.stream().filter(g -> g.getId().startsWith(type)||g.getId().startsWith("PERSONAL")).collect(Collectors.toList());
        permissions.forEach(p -> {
            if (permissionList.contains(p.getId())) {
                p.setChecked(true);
            }
        });
        for (GroupResource r : resources) {
            GroupResourceDTO resourceDTO = new GroupResourceDTO();
            resourceDTO.setResource(r);
            List<GroupPermission> collect = permissions
                    .stream()
                    .filter(p -> StringUtils.equals(r.getId(), p.getResourceId()))
                    .collect(Collectors.toList());
            resourceDTO.setPermissions(collect);
            resourceDTO.setType(r.getId().split("_")[0]);
            dto.add(resourceDTO);
        }
        return dto;
    }

    private Pager<List<GroupDTO>> getGroups(List<String> groupTypeList, EditGroupRequest request) {
        if (groupTypeList.contains(UserGroupType.SYSTEM)) {
            return getUserGroup(UserGroupType.SYSTEM, request);
        }

        if (groupTypeList.contains(UserGroupType.WORKSPACE)) {
            return getUserGroup(UserGroupType.WORKSPACE, request);
        }

        if (groupTypeList.contains(UserGroupType.PROJECT)) {
            return getUserGroup(UserGroupType.PROJECT, request);
        }

        return new Pager<>();
    }

    private Pager<List<GroupDTO>> getUserGroup(String groupType, EditGroupRequest request) {
        List<String> types;
        String workspaceId = SessionUtils.getCurrentWorkspaceId();
        String projectId = SessionUtils.getCurrentProjectId();
        List<String> scopes = Arrays.asList(GLOBAL, workspaceId, projectId);
        int goPage = request.getGoPage();
        int pageSize = request.getPageSize();
        Page<Object> page = PageHelper.startPage(goPage, pageSize, true);
        if (StringUtils.equals(groupType, UserGroupType.SYSTEM)) {
            scopes = new ArrayList<>();
        }
        types = map.get(groupType);
        request.setTypes(types);
        request.setScopes(scopes);
        List<GroupDTO> groups = extGroupMapper.getGroupList(request);
        buildUserInfo(groups);
        return PageUtils.setPageInfo(page, groups);
    }

    public List<Group> getProjectMemberGroups(String projectId, String userId) {
        return extUserGroupMapper.getProjectMemberGroups(projectId, userId);
    }

    public List<GroupDTO> getAllGroup() {
        List<String> types = map.get(UserGroupType.SYSTEM);
        EditGroupRequest request = new EditGroupRequest();
        request.setTypes(types);
        return extGroupMapper.getGroupList(request);
    }

    public List<?> getResource(String type, String groupId) {
        Group group = groupMapper.selectByPrimaryKey(groupId);
        if (group == null) {
            return new ArrayList<>();
        }
        if (StringUtils.equals(UserGroupType.WORKSPACE, type)) {
            return workspaceService.getWorkspaceGroupResource(group.getScopeId());
        }
        if (StringUtils.equals(UserGroupType.PROJECT, type)) {
            return workspaceService.getProjectGroupResource(group.getScopeId());
        }
        return new ArrayList<>();
    }

    public List<User> getGroupUser(EditGroupRequest request) {
        return extUserGroupMapper.getGroupUser(request);
    }

    public void removeGroupMember(String userId, String groupId) {
        UserGroupExample userGroupExample = new UserGroupExample();
        userGroupExample.createCriteria()
                .andGroupIdEqualTo(groupId)
                .andUserIdEqualTo(userId);
        userGroupMapper.deleteByExample(userGroupExample);
    }

    public List<?> getGroupSource(String userId, String groupId) {
        UserGroupExample userGroupExample = new UserGroupExample();
        userGroupExample.createCriteria().andUserIdEqualTo(userId).andGroupIdEqualTo(groupId);
        List<UserGroup> userGroups = userGroupMapper.selectByExample(userGroupExample);
        List<String> sources = userGroups.stream().map(UserGroup::getSourceId).collect(Collectors.toList());
        if (sources.isEmpty()) {
            return new ArrayList<>();
        }

        Group group = groupMapper.selectByPrimaryKey(groupId);
        String type = group.getType();

        if (StringUtils.equals(type, UserGroupType.WORKSPACE)) {
            WorkspaceExample workspaceExample = new WorkspaceExample();
            workspaceExample.createCriteria().andIdIn(sources);
            return workspaceMapper.selectByExample(workspaceExample);
        }

        if (StringUtils.equals(type, UserGroupType.PROJECT)) {
            ProjectExample projectExample = new ProjectExample();
            projectExample.createCriteria().andIdIn(sources);
            return projectMapper.selectByExample(projectExample);
        }

        return new ArrayList<>();
    }

    public void addGroupUser(EditGroupUserRequest request) {
        String groupId = request.getGroupId();
        Group group = groupMapper.selectByPrimaryKey(groupId);
        List<String> userIds = request.getUserIds();
        for (String userId : userIds) {
            UserGroupExample userGroupExample = new UserGroupExample();
            userGroupExample.createCriteria().andUserIdEqualTo(userId)
                    .andGroupIdEqualTo(groupId);
            List<UserGroup> userGroups = userGroupMapper.selectByExample(userGroupExample);
            if (userGroups.size() > 0) {
                MSException.throwException(Translator.get("user_already_exists") + ": " + userId);
            } else {
                this.addGroupUser(group, userId, request.getSourceIds());
            }
        }
    }

    private void addGroupUser(Group group, String userId, List<String> sourceIds) {
        String id = group.getId();
        String type = group.getType();
        if (StringUtils.equals(type, UserGroupType.SYSTEM)) {
            UserGroup userGroup = new UserGroup();
            userGroup.setId(UUID.randomUUID().toString());
            userGroup.setUserId(userId);
            userGroup.setGroupId(id);
            userGroup.setSourceId("system");
            userGroup.setCreateTime(System.currentTimeMillis());
            userGroup.setUpdateTime(System.currentTimeMillis());
            userGroupMapper.insertSelective(userGroup);
        } else {
            QuotaService quotaService = CommonBeanFactory.getBean(QuotaService.class);
            SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
            UserGroupMapper mapper = sqlSession.getMapper(UserGroupMapper.class);
            checkQuota(quotaService, type, sourceIds, Collections.singletonList(userId));
            for (String sourceId : sourceIds) {
                UserGroup userGroup = new UserGroup();
                userGroup.setId(UUID.randomUUID().toString());
                userGroup.setUserId(userId);
                userGroup.setGroupId(id);
                userGroup.setSourceId(sourceId);
                userGroup.setCreateTime(System.currentTimeMillis());
                userGroup.setUpdateTime(System.currentTimeMillis());
                mapper.insertSelective(userGroup);
            }
            sqlSession.flushStatements();
            if (sqlSession != null && sqlSessionFactory != null) {
                SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
            }
        }
    }

    private void checkQuota(QuotaService quotaService, String type, List<String> sourceIds, List<String> userIds) {
        if (quotaService != null) {
            Map<String, List<String>> addMemberMap = sourceIds.stream().collect(Collectors.toMap( id -> id, id -> userIds));
            quotaService.checkMemberCount(addMemberMap, type);
        }
    }

    public void editGroupUser(EditGroupUserRequest request) {
        String groupId = request.getGroupId();
        Group group = groupMapper.selectByPrimaryKey(groupId);
        if (!StringUtils.equals(group.getType(), UserGroupType.SYSTEM)) {
            List<String> userIds = request.getUserIds();
            if (CollectionUtils.isNotEmpty(userIds)) {
                // 编辑单个用户
                String userId = userIds.get(0);
                List<String> sourceIds = request.getSourceIds();
                UserGroupExample userGroupExample = new UserGroupExample();
                userGroupExample.createCriteria().andGroupIdEqualTo(groupId).andUserIdEqualTo(userId);
                userGroupMapper.deleteByExample(userGroupExample);

                SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
                UserGroupMapper mapper = sqlSession.getMapper(UserGroupMapper.class);
                for (String sourceId : sourceIds) {
                    UserGroup userGroup = new UserGroup();
                    userGroup.setId(UUID.randomUUID().toString());
                    userGroup.setUserId(userId);
                    userGroup.setGroupId(groupId);
                    userGroup.setSourceId(sourceId);
                    userGroup.setCreateTime(System.currentTimeMillis());
                    userGroup.setUpdateTime(System.currentTimeMillis());
                    mapper.insertSelective(userGroup);
                }
                sqlSession.flushStatements();
                if (sqlSession != null && sqlSessionFactory != null) {
                    SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
                }
            }
        }
    }

    public String getLogDetails(String id) {
        Group group = groupMapper.selectByPrimaryKey(id);
        if (group != null) {
            List<DetailColumn> columns = ReflexObjectUtil.getColumns(group, SystemReference.groupColumns);
            for (DetailColumn column : columns) {
                if ("scopeId".equals(column.getColumnName()) && column.getOriginalValue() != null && StringUtils.isNotEmpty(column.getOriginalValue().toString())) {
                    if ("global".equals(column.getOriginalValue())) {
                        column.setOriginalValue("是");
                    } else {
                        String scopeId = group.getScopeId();
                        Workspace workspace = workspaceMapper.selectByPrimaryKey(scopeId);
                        if (workspace != null) {
                            column.setOriginalValue("否; 所属工作空间：" + workspace.getName());
                        } else {
                            column.setOriginalValue("否");
                        }
                    }
                }
                if ("type".equals(column.getColumnName()) && column.getOriginalValue() != null && StringUtils.isNotEmpty(column.getOriginalValue().toString())) {
                    column.setOriginalValue(typeMap.get((String) column.getOriginalValue()));
                }
            }
            OperatingLogDetails details = new OperatingLogDetails(JSON.toJSONString(group.getId()), null, group.getName(), group.getCreator(), columns);
            return JSON.toJSONString(details);
        }
        return null;
    }

    public List<Workspace> getWorkspace(String userId) {
        List<Workspace> list = new ArrayList<>();
        GroupExample groupExample = new GroupExample();
        groupExample.createCriteria().andTypeEqualTo(UserGroupType.WORKSPACE);
        List<Group> groups = groupMapper.selectByExample(groupExample);
        List<String> groupIds = groups.stream().map(Group::getId).collect(Collectors.toList());

        if (CollectionUtils.isEmpty(groups)) {
            return list;
        }
        UserGroupExample userGroupExample = new UserGroupExample();
        userGroupExample.createCriteria().andUserIdEqualTo(userId).andGroupIdIn(groupIds);
        List<UserGroup> userGroups = userGroupMapper.selectByExample(userGroupExample);
        List<String> workspaceIds = userGroups.stream().map(UserGroup::getSourceId).collect(Collectors.toList());

        if (CollectionUtils.isEmpty(workspaceIds)) {
            return list;
        }

        WorkspaceExample workspaceExample = new WorkspaceExample();
        workspaceExample.createCriteria().andIdIn(workspaceIds);
        list = workspaceMapper.selectByExample(workspaceExample);
        return list;
    }

}
