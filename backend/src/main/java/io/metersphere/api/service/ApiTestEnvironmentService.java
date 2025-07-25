package io.metersphere.api.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.metersphere.api.dto.ApiMockEnvUpdateDTO;
import io.metersphere.api.dto.ApiTestEnvironmentDTO;
import io.metersphere.api.dto.mockconfig.MockConfigStaticData;
import io.metersphere.api.tcp.TCPPool;
import io.metersphere.base.domain.*;
import io.metersphere.base.mapper.ApiTestEnvironmentMapper;
import io.metersphere.base.mapper.UserGroupMapper;
import io.metersphere.base.mapper.ext.ExtApiTestEnvironmentMapper;
import io.metersphere.commons.constants.NoticeConstants;
import io.metersphere.commons.constants.NotificationConstants;
import io.metersphere.commons.constants.ProjectApplicationType;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.*;
import io.metersphere.controller.request.EnvironmentRequest;
import io.metersphere.dto.BaseSystemConfigDTO;
import io.metersphere.dto.ProjectConfig;
import io.metersphere.i18n.Translator;
import io.metersphere.log.utils.ReflexObjectUtil;
import io.metersphere.log.vo.DetailColumn;
import io.metersphere.log.vo.OperatingLogDetails;
import io.metersphere.log.vo.system.SystemReference;
import io.metersphere.notice.service.NotificationService;
import io.metersphere.service.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class ApiTestEnvironmentService {

    @Resource
    private ApiTestEnvironmentMapper apiTestEnvironmentMapper;
    @Resource
    private ExtApiTestEnvironmentMapper extApiTestEnvironmentMapper;
    @Resource
    private EnvironmentGroupProjectService environmentGroupProjectService;
    @Resource
    private ProjectApplicationService projectApplicationService;
    @Resource
    private SqlSessionFactory sqlSessionFactory;
    @Resource
    private NotificationService notificationService;
    @Lazy
    @Resource
    private ProjectService projectService;
    @Resource
    private UserGroupMapper userGroupMapper;

    public static final String POST_STEP = "postStepProcessor";
    public static final String PRE_STEP = "preStepProcessor";
    public static final String POST = "postProcessor";
    public static final String PRE = "preProcessor";
    public static final String SCRIPT = "script";
    public static final String JSR = "jsr223";
    public static final String ASSERTIONS = "assertions";

    public List<ApiTestEnvironmentWithBLOBs> list(String projectId) {
        ApiTestEnvironmentExample example = new ApiTestEnvironmentExample();
        example.createCriteria().andProjectIdEqualTo(projectId);
        example.setOrderByClause("update_time desc");
        return apiTestEnvironmentMapper.selectByExampleWithBLOBs(example);
    }

    public List<ApiTestEnvironmentWithBLOBs> listByConditions(EnvironmentRequest environmentRequest) {
        if (CollectionUtils.isEmpty(environmentRequest.getProjectIds())) {
            return new ArrayList<>();
        }
        ApiTestEnvironmentExample example = new ApiTestEnvironmentExample();
        ApiTestEnvironmentExample.Criteria criteria = example.createCriteria();
        criteria.andProjectIdIn(environmentRequest.getProjectIds());
        if (StringUtils.isNotBlank(environmentRequest.getName())) {
            environmentRequest.setName(StringUtils.wrapIfMissing(environmentRequest.getName(), '%'));    //使搜索文本变成数据库中的正则表达式
            criteria.andNameLike(environmentRequest.getName());
        }
        example.setOrderByClause("update_time desc");
        return apiTestEnvironmentMapper.selectByExampleWithBLOBs(example);
    }

    public List<ApiTestEnvironmentWithBLOBs> selectByExampleWithBLOBs(ApiTestEnvironmentExample example) {
        return apiTestEnvironmentMapper.selectByExampleWithBLOBs(example);
    }


    public ApiTestEnvironmentWithBLOBs get(String id) {
        return apiTestEnvironmentMapper.selectByPrimaryKey(id);
    }

    public void delete(String id) {
        apiTestEnvironmentMapper.deleteByPrimaryKey(id);
        environmentGroupProjectService.deleteRelateEnv(id);
    }

    public void update(ApiTestEnvironmentWithBLOBs apiTestEnvironment) {
        checkEnvironmentExist(apiTestEnvironment);
        apiTestEnvironmentMapper.updateByPrimaryKeyWithBLOBs(apiTestEnvironment);
    }

    public void updateByPrimaryKeyWithBLOBs(ApiTestEnvironmentWithBLOBs apiTestEnvironment) {
        apiTestEnvironmentMapper.updateByPrimaryKeyWithBLOBs(apiTestEnvironment);
    }

    public String add(ApiTestEnvironmentWithBLOBs apiTestEnvironmentWithBLOBs) {
        apiTestEnvironmentWithBLOBs.setId(UUID.randomUUID().toString());
        checkEnvironmentExist(apiTestEnvironmentWithBLOBs);
        apiTestEnvironmentMapper.insert(apiTestEnvironmentWithBLOBs);
        return apiTestEnvironmentWithBLOBs.getId();
    }

    public String add(ApiTestEnvironmentDTO request, List<MultipartFile> sslFiles) {
        request.setId(UUID.randomUUID().toString());
        request.setCreateUser(SessionUtils.getUserId());
        checkEnvironmentExist(request);
        FileUtils.createFiles(request.getUploadIds(), sslFiles, FileUtils.BODY_FILE_DIR + "/ssl");
        //检查Config，判断isMock参数是否给True
        request = this.updateConfig(request, false);
        request.setCreateTime(System.currentTimeMillis());
        request.setUpdateTime(System.currentTimeMillis());
        apiTestEnvironmentMapper.insert(request);
        checkAndSendReviewMessage(request.getId(),
                request.getName(),
                request.getProjectId(),
                NoticeConstants.TaskType.ENV_TASK,
                null,
                request.getConfig(),
                request.getCreateUser()
        );
        return request.getId();
    }

    private ApiTestEnvironmentDTO updateConfig(ApiTestEnvironmentDTO request, boolean isMock) {
        if (StringUtils.isNotEmpty(request.getConfig())) {
            try {
                JSONObject configObj = JSONObject.parseObject(request.getConfig());
                if (configObj.containsKey("httpConfig")) {
                    JSONObject httpObj = configObj.getJSONObject("httpConfig");
                    httpObj.put("isMock", isMock);
                }
                request.setConfig(configObj.toJSONString());
            } catch (Exception e) {
            }
        }
        return request;
    }

    public void update(ApiTestEnvironmentDTO apiTestEnvironment, List<MultipartFile> sslFiles) {
        checkEnvironmentExist(apiTestEnvironment);
        FileUtils.createFiles(apiTestEnvironment.getUploadIds(), sslFiles, FileUtils.BODY_FILE_DIR + "/ssl");
        apiTestEnvironment.setUpdateTime(System.currentTimeMillis());
        ApiTestEnvironmentWithBLOBs envOrg = apiTestEnvironmentMapper.selectByPrimaryKey(apiTestEnvironment.getId());
        apiTestEnvironmentMapper.updateByPrimaryKeyWithBLOBs(apiTestEnvironment);

        if (StringUtils.isBlank(apiTestEnvironment.getCreateUser())){
            ProjectApplication reviewer = projectApplicationService
                    .getProjectApplication(apiTestEnvironment.getProjectId(), ProjectApplicationType.API_SCRIPT_REVIEWER.name());
            if (StringUtils.isNotBlank(reviewer.getTypeValue())) {
                checkAndSendReviewMessage(apiTestEnvironment.getId(),
                        apiTestEnvironment.getName(),
                        apiTestEnvironment.getProjectId(),
                        NoticeConstants.TaskType.ENV_TASK,
                        envOrg.getConfig(),
                        apiTestEnvironment.getConfig(),
                        reviewer.getTypeValue()
                );
            } else {
                UserGroupExample example = new UserGroupExample();
                example.createCriteria().andSourceIdEqualTo(apiTestEnvironment.getProjectId()).andGroupIdEqualTo("project_admin");
                List<UserGroup> userGroups = userGroupMapper.selectByExample(example);
                if ( CollectionUtils.isNotEmpty(userGroups)) {
                    userGroups.forEach(userGroup -> {
                        checkAndSendReviewMessage(apiTestEnvironment.getId(),
                                apiTestEnvironment.getName(),
                                apiTestEnvironment.getProjectId(),
                                NoticeConstants.TaskType.ENV_TASK,
                                envOrg.getConfig(),
                                apiTestEnvironment.getConfig(),
                                userGroup.getUserId()
                        );
                    });
                }
            }
        } else {
            checkAndSendReviewMessage(apiTestEnvironment.getId(),
                    apiTestEnvironment.getName(),
                    apiTestEnvironment.getProjectId(),
                    NoticeConstants.TaskType.ENV_TASK,
                    envOrg.getConfig(),
                    apiTestEnvironment.getConfig(),
                    apiTestEnvironment.getCreateUser()
            );
        }
    }

    private void checkEnvironmentExist(ApiTestEnvironmentWithBLOBs environment) {
        if (environment.getName() != null) {
            if (StringUtils.isEmpty(environment.getProjectId())) {
                MSException.throwException(Translator.get("项目ID不能为空"));
            }
            ApiTestEnvironmentExample example = new ApiTestEnvironmentExample();
            ApiTestEnvironmentExample.Criteria criteria = example.createCriteria();
            criteria.andNameEqualTo(environment.getName())
                    .andProjectIdEqualTo(environment.getProjectId());
            if (StringUtils.isNotBlank(environment.getId())) {
                criteria.andIdNotEqualTo(environment.getId());
            }
            if (apiTestEnvironmentMapper.selectByExample(example).size() > 0) {
                MSException.throwException(Translator.get("api_test_environment_already_exists"));
            }
        }
    }

    /**
     * 通过项目ID获取Mock环境  （暂时定义mock环境为： name = Mock环境）
     *
     * @param projectId
     * @return
     */
    public synchronized ApiTestEnvironmentWithBLOBs getMockEnvironmentByProjectId(String projectId) {

        SystemParameterService systemParameterService = CommonBeanFactory.getBean(SystemParameterService.class);
        BaseSystemConfigDTO baseSystemConfigDTO = systemParameterService.getBaseInfo();
        String baseUrl = baseSystemConfigDTO.getUrl();
        String protocal = "http";
        if (baseSystemConfigDTO != null && StringUtils.isNotEmpty(baseSystemConfigDTO.getUrl())) {
            baseUrl = baseSystemConfigDTO.getUrl();
            if (baseUrl.startsWith("http:")) {
                protocal = "http";
            } else if (baseUrl.startsWith("https:")) {
                protocal = "https";
            }
        }

        String apiName = MockConfigStaticData.MOCK_EVN_NAME;
        ApiTestEnvironmentWithBLOBs returnModel;
        ApiTestEnvironmentExample example = new ApiTestEnvironmentExample();
        example.createCriteria().andProjectIdEqualTo(projectId).andNameEqualTo(apiName);
        List<ApiTestEnvironmentWithBLOBs> list = this.selectByExampleWithBLOBs(example);

        String projectNumber = this.getSystemIdByProjectId(projectId);
        if (list.isEmpty()) {
            returnModel = this.genHttpApiTestEnvironmentByUrl(projectId, projectNumber, protocal, apiName, baseUrl);
            this.add(returnModel);
        } else {
            returnModel = list.get(0);
            returnModel = this.checkMockEvnIsRightful(returnModel, protocal, projectId, projectNumber, apiName, baseUrl);
        }
        return returnModel;
    }

    private ApiTestEnvironmentWithBLOBs updateMockEvn(ApiTestEnvironmentWithBLOBs mockEnv, String protocol, String newUrl) {
        if (mockEnv.getConfig() != null) {
            try {
                JSONObject configObj = JSONObject.parseObject(mockEnv.getConfig());
                if (configObj.containsKey("httpConfig")) {
                    JSONObject httpObj = configObj.getJSONObject("httpConfig");
                    JSONObject httpObject = this.genHttpMockConfig(httpObj, newUrl, protocol);
                    configObj.put("httpConfig", httpObject);
                }
                if (configObj.containsKey("tcpConfig")) {
                    JSONObject tcpObj = configObj.getJSONObject("tcpConfig");
                    JSONObject tcpObject = this.genTcpMockConfig(tcpObj, newUrl);
                    configObj.put("tcpConfig", tcpObject);
                }
                mockEnv.setConfig(configObj.toString());
            } catch (Exception e) {
                LogUtil.error(e);
            }
        }
        return mockEnv;
    }

    private JSONObject genTcpMockConfig(@NotNull JSONObject tcpObj, String newUrl) {
        if (tcpObj.containsKey("server")) {
            String url = newUrl;
            if (url.startsWith("http://")) {
                url = url.substring(7);
            } else if (url.startsWith("https://")) {
                url = url.substring(8);
            }
            String ipStr = url;
            if (url.contains(":") && !url.endsWith(":")) {
                String[] urlArr = url.split(":");
                int port = -1;
                try {
                    port = Integer.parseInt(urlArr[urlArr.length - 1]);
                } catch (Exception e) {
                }
                if (port > -1) {
                    ipStr = urlArr[0];
                }
            }
            tcpObj.put("server", ipStr);
        }
        return tcpObj;
    }

    private JSONObject genHttpMockConfig(@NotNull JSONObject httpObj, String newUrl, String protocol) {
        if (httpObj.containsKey("isMock") && httpObj.getBoolean("isMock")) {
            if (httpObj.containsKey("conditions")) {
                String url = newUrl;
                if (url.startsWith("http://")) {
                    url = url.substring(7);
                } else if (url.startsWith("https://")) {
                    url = url.substring(8);
                }
                String ipStr = url;
                String portStr = "";
                if (url.contains(":") && !url.endsWith(":")) {
                    String[] urlArr = url.split(":");
                    int port = -1;
                    try {
                        port = Integer.parseInt(urlArr[urlArr.length - 1]);
                    } catch (Exception e) {
                    }
                    if (port > -1) {
                        portStr = String.valueOf(port);
                        ipStr = urlArr[0];
                    }
                }
                if (httpObj.containsKey("socket") && httpObj.containsKey("protocol") && httpObj.containsKey("port")) {
                    String httpSocket = httpObj.getString("socket");
                    if (httpSocket.contains("/mock")) {
                        String[] httpSocketArr = StringUtils.split(httpSocket, "/mock");
                        httpSocket = StringUtils.join(url, "/mock", httpSocketArr[1]);
                    }
                    httpObj.put("socket", httpSocket);
                    httpObj.put("protocol", protocol);
                    if (StringUtils.isNotEmpty(portStr)) {
                        httpObj.put("port", portStr);
                    } else {
                        httpObj.put("port", "");
                    }
                }

                JSONArray conditions = httpObj.getJSONArray("conditions");
                for (int i = 0; i < conditions.size(); i++) {
                    JSONObject httpItem = conditions.getJSONObject(i);
                    if (httpItem.containsKey("socket") && httpItem.containsKey("protocol") && httpItem.containsKey("domain")) {
                        String httpSocket = httpItem.getString("socket");
                        if (httpSocket.contains("/mock")) {
                            String[] httpSocketArr = httpSocket.split("/mock");
                            httpSocket = StringUtils.join(url, "/mock", httpSocketArr[1]);
                        }
                        httpItem.put("socket", httpSocket);
                        httpItem.put("domain", ipStr);
                        httpItem.put("protocol", protocol);
                        if (StringUtils.isNotEmpty(portStr)) {
                            httpItem.put("port", portStr);
                        } else {
                            httpItem.put("port", "");
                        }
                    }
                }
            }
        }
        return httpObj;
    }

    private ApiTestEnvironmentWithBLOBs checkMockEvnIsRightful(ApiTestEnvironmentWithBLOBs returnModel, String protocal, String projectId, String projectNumber, String name, String url) {
        boolean needUpdate = false;
        ProjectService projectService = CommonBeanFactory.getBean(ProjectService.class);
        Project project = projectService.getProjectById(projectId);
        if (returnModel.getConfig() != null && project != null) {
            try {
                JSONObject configObj = JSONObject.parseObject(returnModel.getConfig());
                if (configObj.containsKey("httpConfig")) {
                    JSONObject httpObj = configObj.getJSONObject("httpConfig");
                    if (httpObj.containsKey("isMock") && httpObj.getBoolean("isMock")) {
                        if (httpObj.containsKey("conditions")) {
                            JSONArray conditions = httpObj.getJSONArray("conditions");
                            if (conditions.isEmpty()) {
                                needUpdate = true;
                            } else {
                                for (int i = 0; i < conditions.size(); i++) {
                                    JSONObject obj = conditions.getJSONObject(i);
                                    String socket = url;
                                    if (socket.startsWith("http://")) {
                                        socket = socket.substring(7);
                                    } else if (socket.startsWith("https://")) {
                                        socket = socket.substring(8);
                                    }
                                    if (!obj.containsKey("socket") || !StringUtils.startsWith(String.valueOf(obj.get("socket")), socket)) {
                                        needUpdate = true;
                                        break;
                                    } else if (!obj.containsKey("protocol") || !StringUtils.equals(protocal, String.valueOf(obj.get("protocol")))) {
                                        needUpdate = true;
                                        break;
                                    }

                                    String projectSocket = String.valueOf(obj.get("socket"));
                                    if (!StringUtils.contains(projectSocket, "/mock/" + projectNumber)) {
                                        needUpdate = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                ProjectConfig config = projectApplicationService.getSpecificTypeValue(project.getId(), ProjectApplicationType.MOCK_TCP_PORT.name());
                Integer mockPortStr = config.getMockTcpPort();
                if (mockPortStr != null && mockPortStr != 0) {
                    if (configObj.containsKey("tcpConfig")) {
                        JSONObject tcpConfigObj = configObj.getJSONObject("tcpConfig");
                        if (tcpConfigObj.containsKey("port")) {
                            if (tcpConfigObj.getInteger("port").intValue() != mockPortStr) {
                                needUpdate = true;
                            }
                        } else {
                            needUpdate = true;
                        }

                        if (tcpConfigObj.containsKey("server")) {
                            if (!StringUtils.equals(tcpConfigObj.getString("server"), url)) {
                                needUpdate = true;
                            }
                        } else {
                            needUpdate = true;
                        }
                    }
                }
            } catch (Exception e) {
                needUpdate = true;
                LogUtil.error(e);
            }
        }
        if (needUpdate) {
            String id = returnModel.getId();
            returnModel = this.genHttpApiTestEnvironmentByUrl(id, project, projectNumber, protocal, name, url);
            apiTestEnvironmentMapper.updateByPrimaryKeyWithBLOBs(returnModel);
        }
        return returnModel;
    }

    private ApiTestEnvironmentWithBLOBs genHttpApiTestEnvironmentByUrl(String projectId, String projectNumber, String protocal, String name, String baseUrl) {
        ProjectService projectService = CommonBeanFactory.getBean(ProjectService.class);
        Project project = projectService.getProjectById(projectId);
        if (project != null) {
            return this.genHttpApiTestEnvironmentByUrl(null, project, projectNumber, protocal, name, baseUrl);
        }
        return null;
    }

    private ApiTestEnvironmentWithBLOBs genHttpApiTestEnvironmentByUrl(String envId, Project project, String projectNumber, String protocal, String name, String baseUrl) {
        if (project == null) {
            return null;
        }
        String socket = "";
        String url = baseUrl;
        if (url.startsWith("http://")) {
            url = url.substring(7);
        } else if (url.startsWith("https://")) {
            url = url.substring(8);
        }
        socket = url;
        String tcpSocket = socket;
        if (StringUtils.isNotEmpty(tcpSocket) && tcpSocket.contains(":")) {
            tcpSocket = socket.split(":")[0];
        }

        String portStr = "";
        String ipStr = url;
        if (url.contains(":") && !url.endsWith(":")) {
            String[] urlArr = url.split(":");
            int port = -1;
            try {
                port = Integer.parseInt(urlArr[urlArr.length - 1]);
            } catch (Exception e) {
            }
            if (port > -1) {
                portStr = String.valueOf(port);
                ipStr = urlArr[0];
            }
        }

        JSONObject httpConfig = new JSONObject();
        httpConfig.put("socket", null);
        httpConfig.put("isMock", true);
        httpConfig.put("domain", null);
        JSONArray httpVariablesArr = new JSONArray();
        Map<String, Object> httpMap = new HashMap<>();
        httpMap.put("enable", true);
        httpVariablesArr.add(httpMap);
        httpConfig.put("headers", new JSONArray(httpVariablesArr));
        httpConfig.put("protocol", null);
        httpConfig.put("port", null);
        JSONArray httpItemArr = new JSONArray();
        JSONObject httpItem = new JSONObject();
        httpItem.put("id", UUID.randomUUID().toString());
        httpItem.put("type", "NONE");
        httpItem.put("socket", socket + "/mock/" + projectNumber);
        httpItem.put("protocol", protocal);
        JSONArray protocolVariablesArr = new JSONArray();
        Map<String, Object> protocolMap = new HashMap<>();
        protocolMap.put("enable", true);
        protocolVariablesArr.add(protocolMap);
        httpItem.put("headers", new JSONArray(protocolVariablesArr));
        httpItem.put("domain", ipStr);
        if (StringUtils.isNotEmpty(portStr)) {
            httpItem.put("port", portStr);
        } else {
            httpItem.put("port", "");
        }
        JSONArray detailArr = new JSONArray();
        JSONObject detailObj = new JSONObject();
        detailObj.put("name", "");
        detailObj.put("value", "contains");
        detailObj.put("enable", true);
        detailArr.add(detailObj);
        httpItem.put("details", detailArr);

        httpItemArr.add(httpItem);
        httpConfig.put("conditions", httpItemArr);
        httpConfig.put("defaultCondition", "NONE");

        JSONObject tcpConfigObj = new JSONObject();
        tcpConfigObj.put("classname", "TCPClientImpl");
        tcpConfigObj.put("reUseConnection", false);
        tcpConfigObj.put("nodelay", false);
        tcpConfigObj.put("closeConnection", false);
        if (project != null) {
            ProjectConfig config = projectApplicationService.getSpecificTypeValue(project.getId(), ProjectApplicationType.MOCK_TCP_PORT.name());
            Integer mockPort = config.getMockTcpPort();
            if (mockPort != null && mockPort != 0) {
                tcpConfigObj.put("server", tcpSocket);
                tcpConfigObj.put("port", mockPort);
            }
        }

        ApiTestEnvironmentWithBLOBs blobs = null;
        if (StringUtils.isNotEmpty(envId)) {
            blobs = this.get(envId);
        }
        if (blobs != null && StringUtils.isNotEmpty(blobs.getConfig())) {
            JSONObject object = JSONObject.parseObject(blobs.getConfig());
            object.put("httpConfig", httpConfig);
            object.put("tcpConfig", tcpConfigObj);
            blobs.setConfig(object.toString());
        } else {
            blobs = new ApiTestEnvironmentWithBLOBs();
            JSONObject commonConfigObj = new JSONObject();
            JSONArray commonVariablesArr = new JSONArray();
            Map<String, Object> commonMap = new HashMap<>();
            commonMap.put("enable", true);
            commonVariablesArr.add(commonMap);
            commonConfigObj.put("variables", commonVariablesArr);
            commonConfigObj.put("enableHost", false);
            commonConfigObj.put("hosts", new String[]{});

            JSONArray databaseConfigObj = new JSONArray();

            JSONObject object = new JSONObject();
            object.put("commonConfig", commonConfigObj);
            object.put("httpConfig", httpConfig);
            object.put("databaseConfigs", databaseConfigObj);
            object.put("tcpConfig", tcpConfigObj);
            blobs.setConfig(object.toString());
        }
        blobs.setProjectId(project.getId());
        blobs.setName(name);

        return blobs;
    }

    public void batchUpdateMockEvnInfoByBaseUrl(String oldBaseUrl, String baseUrl) {
        ApiTestEnvironmentExample example = new ApiTestEnvironmentExample();
        example.createCriteria().andNameEqualTo(MockConfigStaticData.MOCK_EVN_NAME);
        long mockEnvCount = apiTestEnvironmentMapper.countByExample(example);
        BatchProcessingUtil.batchUpdateMockEnvConfig(oldBaseUrl, baseUrl, mockEnvCount, this::updateMockEvnInfoByBaseUrlAndLimitNum);
    }

    public void updateMockEvnInfoByBaseUrlAndLimitNum(ApiMockEnvUpdateDTO mockEnvUpdateDTO) {
        if (StringUtils.isNotEmpty(mockEnvUpdateDTO.getBaseUrl())) {
            List<ApiTestEnvironmentWithBLOBs> allEvnList = extApiTestEnvironmentMapper.selectByNameAndLimitNum(
                    MockConfigStaticData.MOCK_EVN_NAME, mockEnvUpdateDTO.getLimitStart(), mockEnvUpdateDTO.getLimitSize());
            List<ApiTestEnvironmentWithBLOBs> updateList = new ArrayList<>();
            for (ApiTestEnvironmentWithBLOBs model : allEvnList) {
                if (StringUtils.equals(model.getName(), MockConfigStaticData.MOCK_EVN_NAME)) {
                    String protocal = "";
                    if (mockEnvUpdateDTO.getBaseUrl().startsWith("http:")) {
                        protocal = "http";
                    } else if (mockEnvUpdateDTO.getBaseUrl().startsWith("https:")) {
                        protocal = "https";
                    }
                    ApiTestEnvironmentWithBLOBs updateModel = this.updateMockEvn(model, protocal, mockEnvUpdateDTO.getBaseUrl());
                    updateList.add(updateModel);
                }
            }
            if (CollectionUtils.isNotEmpty(updateList)) {
                SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
                ApiTestEnvironmentMapper batchMapper = sqlSession.getMapper(ApiTestEnvironmentMapper.class);

                for (ApiTestEnvironmentWithBLOBs updateModel : updateList) {
                    batchMapper.updateByPrimaryKeyWithBLOBs(updateModel);
                }
                sqlSession.flushStatements();
                if (sqlSession != null && sqlSessionFactory != null) {
                    SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
                }
            }

        }
    }

    private String getSystemIdByProjectId(String projectId) {
        ProjectService projectService = CommonBeanFactory.getBean(ProjectService.class);
        Project project = projectService.getProjectById(projectId);
        if (project != null) {
            project = projectService.checkSystemId(project);
            return projectService.getSystemIdByProjectId(projectId);
        } else {
            return "";
        }
    }

    public String getLogDetails(String id) {
        ApiTestEnvironmentWithBLOBs bloBs = apiTestEnvironmentMapper.selectByPrimaryKey(id);
        if (bloBs != null) {
            List<DetailColumn> columns = ReflexObjectUtil.getColumns(bloBs, SystemReference.environmentColumns);
            OperatingLogDetails details = new OperatingLogDetails(JSON.toJSONString(bloBs.getId()), bloBs.getProjectId(), bloBs.getName(), bloBs.getCreateUser(), columns);
            return JSON.toJSONString(details);
        }
        return null;
    }

    public String getMockInfo(String projectId) {
        String returnStr = "";
        ApiTestEnvironmentWithBLOBs mockEnv = this.getMockEnvironmentByProjectId(projectId);
        if (mockEnv != null && mockEnv.getConfig() != null) {
            try {
                JSONObject configObj = JSONObject.parseObject(mockEnv.getConfig());

                if (configObj.containsKey("tcpConfig")) {
                    JSONObject tcpConfigObj = configObj.getJSONObject("tcpConfig");
                    int tcpPort = 0;
                    if (tcpConfigObj.containsKey("port")) {
                        tcpPort = tcpConfigObj.getInteger("port").intValue();
                        if (tcpPort == 0 || !TCPPool.isTcpOpen(tcpPort)) {
                            return returnStr;
                        }
                    } else {
                        return returnStr;
                    }
                    if (tcpConfigObj.containsKey("server")) {
                        String server = tcpConfigObj.getString("server");
                        returnStr = server + ":" + tcpPort;
                    } else {
                        return returnStr;
                    }
                }
            } catch (Exception e) {
                LogUtil.error(e);
            }
        }
        return returnStr;
    }

    @Async
    public void checkAndSendReviewMessage(
            String id,
            String name,
            String projectId,
            String resourceType,
            String requestOrg,
            String requestTarget,
            String sendUser) {
        try {
            ProjectApplication scriptEnable = projectApplicationService
                    .getProjectApplication(projectId, ProjectApplicationType.API_REVIEW_TEST_SCRIPT.name());

            if (BooleanUtils.toBoolean(scriptEnable.getTypeValue())) {
                List<String> org = scriptList(requestOrg);
                List<String> target = scriptList(requestTarget);
                boolean isSend = isSend(org, target);
                if (isSend) {

                    ProjectApplication reviewer = projectApplicationService
                            .getProjectApplication(projectId, ProjectApplicationType.API_SCRIPT_REVIEWER.name());
                    if (StringUtils.isNotBlank(reviewer.getTypeValue())) {
                        sendUser = reviewer.getTypeValue();
                    }
                    if (projectService.isProjectMember(projectId, sendUser)) {
                        Notification notification = new Notification();
                        notification.setTitle("环境设置");
                        notification.setOperator(reviewer.getTypeValue());
                        notification.setOperation(NoticeConstants.Event.REVIEW);
                        notification.setResourceId(id);
                        notification.setResourceName(name);
                        notification.setResourceType(resourceType);
                        notification.setType(NotificationConstants.Type.SYSTEM_NOTICE.name());
                        notification.setStatus(NotificationConstants.Status.UNREAD.name());
                        notification.setCreateTime(System.currentTimeMillis());
                        notification.setReceiver(sendUser);
                        notificationService.sendAnnouncement(notification);
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.error("发送通知失败", e);
        }

    }

    public static List<String> scriptList(String request) {
        List<String> list = new ArrayList<>();
        if (StringUtils.isNotBlank(request)){
            JSONObject configObj = JSONObject.parseObject(request);
            toList(list, configObj, POST_STEP, PRE_STEP);
            toList(list, configObj, PRE, POST);
            JSONObject object = configObj.getJSONObject(ASSERTIONS);
            if (ObjectUtils.isNotEmpty(object)) {
                JSONArray jsrArray = object.getJSONArray(JSR);
                if (jsrArray != null) {
                    for (int j = 0; j < jsrArray.size(); j++) {
                        JSONObject jsr223 = jsrArray.getJSONObject(j);
                        if (jsr223 != null) {
                            list.add(jsr223.getString(SCRIPT));
                        }
                    }
                }
            }

        }
        return list;
    }

    private static void toList(List<String> list, JSONObject configObj, String pre, String post) {
        JSONObject preProcessor = configObj.getJSONObject(pre);
        if (ObjectUtils.isNotEmpty(preProcessor) && StringUtils.isNotBlank(preProcessor.getString(SCRIPT))) {
            list.add(StringUtils.join(pre,preProcessor.getString(SCRIPT)));
        }
        JSONObject postProcessor = configObj.getJSONObject(post);
        if (ObjectUtils.isNotEmpty(postProcessor) && StringUtils.isNotBlank(postProcessor.getString(SCRIPT))) {
            list.add(StringUtils.join(post,postProcessor.getString(SCRIPT)));
        }
    }

    public static boolean isSend(List<String> orgList, List<String> targetList) {
        if (orgList.size() != targetList.size()) {
            if (CollectionUtils.isEmpty(targetList)) {
                return false;
            }
            if (CollectionUtils.isEmpty(orgList)) {
                return true;
            }
            return true;
        }

        List<String> diff = targetList.stream()
                .filter(s -> !orgList.contains(s))
                .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(diff)) {
            return true;
        }
        return false;
    }
}
