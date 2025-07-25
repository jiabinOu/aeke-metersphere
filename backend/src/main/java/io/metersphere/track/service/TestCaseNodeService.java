package io.metersphere.track.service;


import com.alibaba.fastjson.JSON;
import com.google.common.util.concurrent.AtomicDouble;
import io.metersphere.base.domain.*;
import io.metersphere.base.mapper.*;
import io.metersphere.base.mapper.ext.ExtTestCaseMapper;
import io.metersphere.base.mapper.ext.ExtTestCaseNodeMapper;
import io.metersphere.base.mapper.ext.ExtTestCaseReviewTestCaseMapper;
import io.metersphere.base.mapper.ext.ExtTestPlanTestCaseMapper;
import io.metersphere.commons.constants.TestCaseConstants;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.BeanUtils;
import io.metersphere.commons.utils.CommonBeanFactory;
import io.metersphere.commons.utils.SessionUtils;
import io.metersphere.dto.NodeNumDTO;
import io.metersphere.exception.ExcelException;
import io.metersphere.i18n.Translator;
import io.metersphere.log.utils.ReflexObjectUtil;
import io.metersphere.log.vo.DetailColumn;
import io.metersphere.log.vo.OperatingLogDetails;
import io.metersphere.log.vo.api.ModuleReference;
import io.metersphere.service.NodeTreeService;
import io.metersphere.track.dto.TestCaseDTO;
import io.metersphere.track.dto.TestCaseNodeDTO;
import io.metersphere.track.dto.TestPlanCaseDTO;
import io.metersphere.track.request.testcase.*;
import io.metersphere.track.request.testplancase.QueryTestPlanCaseRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class TestCaseNodeService extends NodeTreeService<TestCaseNodeDTO> {

    @Resource
    TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    ExtTestCaseNodeMapper extTestCaseNodeMapper;
    @Resource
    TestCaseMapper testCaseMapper;
    @Resource
    TestPlanMapper testPlanMapper;
    @Resource
    ExtTestPlanTestCaseMapper extTestPlanTestCaseMapper;
    @Resource
    ExtTestCaseMapper extTestCaseMapper;
    @Resource
    SqlSessionFactory sqlSessionFactory;
    @Resource
    TestPlanProjectService testPlanProjectService;
    @Resource
    ProjectMapper projectMapper;
    @Resource
    TestCaseReviewTestCaseMapper testCaseReviewTestCaseMapper;
    @Resource
    ExtTestCaseReviewTestCaseMapper extTestCaseReviewTestCaseMapper;
    @Resource
    TestCaseReviewMapper testCaseReviewMapper;

    public TestCaseNodeService() {
        super(TestCaseNodeDTO.class);
    }

    public String addNode(TestCaseNode node) {
        validateNode(node);
        node.setCreateTime(System.currentTimeMillis());
        node.setUpdateTime(System.currentTimeMillis());
        if (StringUtils.isBlank(node.getId())) {
            node.setId(UUID.randomUUID().toString());
        }
        node.setCreateUser(SessionUtils.getUserId());
        double pos = getNextLevelPos(node.getProjectId(), node.getLevel(), node.getParentId());
        node.setPos(pos);
        testCaseNodeMapper.insertSelective(node);
        return node.getId();
    }

    public List<String> getNodes(String nodeId) {
        return extTestCaseNodeMapper.getNodes(nodeId);
    }

    private void validateNode(TestCaseNode node) {
        if (node.getLevel() > TestCaseConstants.MAX_NODE_DEPTH) {
            MSException.throwException(Translator.get("test_case_node_level_tip")
                    + TestCaseConstants.MAX_NODE_DEPTH + Translator.get("test_case_node_level"));
        }
        checkTestCaseNodeExist(node);
    }

    private void checkTestCaseNodeExist(TestCaseNode node) {
        if (node.getName() != null) {
            TestCaseNodeExample example = new TestCaseNodeExample();
            TestCaseNodeExample.Criteria criteria = example.createCriteria();
            criteria.andNameEqualTo(node.getName())
                    .andProjectIdEqualTo(node.getProjectId());

            if (StringUtils.isNotBlank(node.getParentId())) {
                criteria.andParentIdEqualTo(node.getParentId());
            } else {
                criteria.andLevelEqualTo(node.getLevel());
            }

            if (StringUtils.isNotBlank(node.getId())) {
                criteria.andIdNotEqualTo(node.getId());
            }
            if (testCaseNodeMapper.selectByExample(example).size() > 0) {
                MSException.throwException(Translator.get("test_case_module_already_exists"));
            }
        }
    }

    public TestCaseNode getDefaultNode(String projectId) {
        TestCaseNodeExample example = new TestCaseNodeExample();
        example.createCriteria().andProjectIdEqualTo(projectId).andNameEqualTo("未规划用例").andParentIdIsNull();
        List<TestCaseNode> list = testCaseNodeMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(list)) {
            NodeNumDTO record = new NodeNumDTO();
            record.setId(UUID.randomUUID().toString());
            record.setCreateUser(SessionUtils.getUserId());
            record.setName("未规划用例");
            record.setPos(1.0);
            record.setLevel(1);
            record.setCreateTime(System.currentTimeMillis());
            record.setUpdateTime(System.currentTimeMillis());
            record.setProjectId(projectId);
            testCaseNodeMapper.insert(record);
            record.setCaseNum(0);
            return record;
        }else {
            return list.get(0);
        }
    }
    public List<TestCaseNodeDTO> getNodeTreeByProjectId(String projectId) {
        QueryTestCaseRequest request = new QueryTestCaseRequest();
        return getNodeTreeByProjectId(projectId, request);
    }

    public List<TestCaseNodeDTO> getNodeTreeByProjectId(String projectId, QueryTestCaseRequest request) {
        // 判断当前项目下是否有默认模块，没有添加默认模块
        this.getDefaultNode(projectId);
        List<TestCaseNodeDTO> testCaseNodes = extTestCaseNodeMapper.getNodeTreeByProjectId(projectId);
        request.setUserId(SessionUtils.getUserId());
        request.setProjectId(projectId);

        //优化：将for循环内的SQL抽出来，只差一次
        List<String> allModuleIdList = new ArrayList<>();
        for (TestCaseNodeDTO node : testCaseNodes) {
            List<String> moduleIds = new ArrayList<>();
            moduleIds = this.nodeList(testCaseNodes, node.getId(), moduleIds);
            moduleIds.add(node.getId());
            for (String moduleId : moduleIds) {
                if(!allModuleIdList.contains(moduleId)){
                    allModuleIdList.add(moduleId);
                }
            }
        }
        request.setModuleIds(allModuleIdList);
        List<Map<String,Object>> moduleCountList = extTestCaseMapper.moduleCountByCollection(request);
        Map<String,Integer> moduleCountMap = this.parseModuleCountList(moduleCountList);
        testCaseNodes.forEach(node -> {
            List<String> moduleIds = new ArrayList<>();
            moduleIds = this.nodeList(testCaseNodes, node.getId(), moduleIds);
            moduleIds.add(node.getId());
            int countNum = 0;
            for (String moduleId : moduleIds) {
                if(moduleCountMap.containsKey(moduleId)){
                    countNum += moduleCountMap.get(moduleId).intValue();
                }
            }
            node.setCaseNum(countNum);
        });
        return getNodeTrees(testCaseNodes);
    }

    private Map<String, Integer> parseModuleCountList(List<Map<String, Object>> moduleCountList) {
        Map<String,Integer> returnMap = new HashMap<>();
        for (Map<String, Object> map: moduleCountList){
            Object moduleIdObj = map.get("moduleId");
            Object countNumObj = map.get("countNum");
            if(moduleIdObj!= null && countNumObj != null){
                String moduleId = String.valueOf(moduleIdObj);
                try {
                    Integer countNumInteger = new Integer(String.valueOf(countNumObj));
                    returnMap.put(moduleId,countNumInteger);
                }catch (Exception e){
                }
            }
        }
        return returnMap;
    }

    public static List<String> nodeList(List<TestCaseNodeDTO> testCaseNodes, String pid, List<String> list) {
        for (TestCaseNodeDTO node : testCaseNodes) {
            //遍历出父id等于参数的id，add进子节点集合
            if (StringUtils.equals(node.getParentId(), pid)) {
                list.add(node.getId());
                //递归遍历下一级
                nodeList(testCaseNodes, node.getId(), list);
            }
        }
        return list;
    }

    public int editNode(DragNodeRequest request) {
        request.setUpdateTime(System.currentTimeMillis());
        checkTestCaseNodeExist(request);
        if (!CollectionUtils.isEmpty(request.getNodeIds())) {
            List<TestCaseDTO> testCases = extTestCaseMapper.getForNodeEdit(request.getNodeIds());
            testCases.forEach(testCase -> {
                StringBuilder path = new StringBuilder(testCase.getNodePath());
                List<String> pathLists = Arrays.asList(path.toString().split("/"));
                pathLists.set(request.getLevel(), request.getName());
                path.delete(0, path.length());
                for (int i = 1; i < pathLists.size(); i++) {
                    path = path.append("/").append(pathLists.get(i));
                }
                testCase.setNodePath(path.toString());
            });
            batchUpdateTestCase(testCases);
        }
        return testCaseNodeMapper.updateByPrimaryKeySelective(request);
    }

    /**
     * 修改用例的 nodePath
     * @param editNodeIds
     * @param projectId
     */
    public void editCasePathForMinder(List<String> editNodeIds, String projectId) {
        if (!CollectionUtils.isEmpty(editNodeIds)) {
            List<TestCaseNodeDTO> nodeTrees = getNodeTrees(extTestCaseNodeMapper.getNodeTreeByProjectId(projectId));
            List<TestCaseDTO> testCases = extTestCaseMapper.getForNodeEdit(editNodeIds);
            nodeTrees.forEach(nodeTree -> buildUpdateTestCase(nodeTree, testCases, null, "/", "0", 1));
            batchUpdateTestCase(testCases);
        }
    }

    public int deleteNode(List<String> nodeIds) {
        if (CollectionUtils.isEmpty(nodeIds)) {
            return 1;
        }
        TestCaseService testCaseService = CommonBeanFactory.getBean(TestCaseService.class);
        List<String> testCaseIdList = this.selectCaseIdByNodeIds(nodeIds);
        TestCaseBatchRequest request = new TestCaseBatchRequest();
        request.setIds(testCaseIdList);
        testCaseService.deleteToGcBatch(request.getIds());

        TestCaseNodeExample testCaseNodeExample = new TestCaseNodeExample();
        testCaseNodeExample.createCriteria().andIdIn(nodeIds);
        return testCaseNodeMapper.deleteByExample(testCaseNodeExample);
    }

    private List<String> selectCaseIdByNodeIds(List<String> nodeIds) {
        if(CollectionUtils.isEmpty(nodeIds)){
            return  new ArrayList<>();
        }else {
            return extTestCaseMapper.selectIdsByNodeIds(nodeIds);
        }
    }

    /**
     * 获取当前计划下
     * 有关联数据的节点
     *
     * @param request
     * @return List<TestCaseNodeDTO>
     */
    public List<TestCaseNodeDTO> getNodeByQueryRequest(QueryTestPlanCaseRequest request) {

        List<TestCaseNodeDTO> list = new ArrayList<>();
        List<String> projectIds = testPlanProjectService.getProjectIdsByPlanId(request.getPlanId());
        projectIds.forEach(id -> {
            Project project = projectMapper.selectByPrimaryKey(id);
            String name = project.getName();
            List<TestCaseNodeDTO> nodeList = getNodeDTO(id, request);
            TestCaseNodeDTO testCaseNodeDTO = new TestCaseNodeDTO();
            testCaseNodeDTO.setId(project.getId());
            testCaseNodeDTO.setName(name);
            testCaseNodeDTO.setLabel(name);
            testCaseNodeDTO.setChildren(nodeList);
            list.add(testCaseNodeDTO);
        });

        return list;
    }

    /**
     * 获取当前计划下
     * 有关联数据的节点
     *
     * @param planId plan id
     * @return List<TestCaseNodeDTO>
     */
    public List<TestCaseNodeDTO> getNodeByPlanId(String planId) {
        List<TestCase> testCases = extTestPlanTestCaseMapper.getTestCaseWithNodeInfo(planId);
        Map<String, List<String>> projectNodeMap = getProjectNodeMap(testCases);
        return getNodeTreeWithPruningTree(projectNodeMap);
    }

    public List<TestCaseNodeDTO> getPublicNodeByProjectNode(List<TestCaseNodeDTO> projectNodes) {
        QueryTestCaseRequest request = new QueryTestCaseRequest();
        request.setCasePublic(true);
        for (TestCaseNodeDTO dto : projectNodes) {
            List<TestCaseNodeDTO> children = this.getNodeTreeByProjectId(dto.getId(), request);
            dto.setChildren(children);
            int sum = children.stream().mapToInt(TestCaseNodeDTO::getCaseNum).sum();
            dto.setCaseNum(sum);
        }
        return projectNodes;
    }

    public List<TestCaseNodeDTO> getNodeByReviewId(String reviewId) {
        List<TestCase> testCases = extTestCaseReviewTestCaseMapper.getTestCaseWithNodeInfo(reviewId);
        Map<String, List<String>> projectNodeMap = getProjectNodeMap(testCases);
        return getNodeTreeWithPruningTree(projectNodeMap);
    }

    public List<TestCaseNodeDTO> getNodeTreeWithPruningTree(Map<String, List<String>> projectNodeMap) {
        List<TestCaseNodeDTO> list = new ArrayList<>();
        projectNodeMap.forEach((k, v) -> {
            Project project = projectMapper.selectByPrimaryKey(k);
            if (project != null) {
                String name = project.getName();
                List<TestCaseNodeDTO> testCaseNodes = getNodeTreeWithPruningTree(k, v);
                TestCaseNodeDTO testCaseNodeDTO = new TestCaseNodeDTO();
                testCaseNodeDTO.setId(project.getId());
                testCaseNodeDTO.setName(name);
                testCaseNodeDTO.setLabel(name);
                testCaseNodeDTO.setChildren(testCaseNodes);
                if (!CollectionUtils.isEmpty(testCaseNodes)) {
                    list.add(testCaseNodeDTO);
                }
            }
        });
        return list;
    }

    /**
     * 获取当前项目下的
     * @param projectId
     * @param pruningTreeIds
     * @return
     */
    public List<TestCaseNodeDTO> getNodeTreeWithPruningTree(String projectId, List<String> pruningTreeIds) {
        List<TestCaseNodeDTO> testCaseNodes = extTestCaseNodeMapper.getNodeTreeByProjectId(projectId);
        List<TestCaseNodeDTO> nodeTrees = getNodeTrees(testCaseNodes);
        Iterator<TestCaseNodeDTO> iterator = nodeTrees.iterator();
        while (iterator.hasNext()) {
            TestCaseNodeDTO rootNode = iterator.next();
            if (pruningTree(rootNode, pruningTreeIds)) {
                iterator.remove();
            }
        }
        return nodeTrees;
    }

    private Map<String, List<String>> getProjectNodeMap(List<TestCase> testCases) {
        Map<String, List<String>> projectNodeMap = new HashMap<>();
        for (TestCase testCase : testCases) {
            List<String> nodeIds = Optional.ofNullable(projectNodeMap.get(testCase.getProjectId())).orElse(new ArrayList<>());
            nodeIds.add(testCase.getNodeId());
            projectNodeMap.put(testCase.getProjectId(), nodeIds);
        }
        return projectNodeMap;
    }

    private List<TestCaseNodeDTO> getNodeDTO(String projectId, QueryTestPlanCaseRequest request) {
        List<TestPlanCaseDTO> testPlanTestCases = extTestPlanTestCaseMapper.listByPlanId(request);
        if (testPlanTestCases.isEmpty()) {
            return null;
        }

        List<String> caseIds = testPlanTestCases.stream()
                .map(TestPlanCaseDTO::getCaseId)
                .collect(Collectors.toList());

        TestCaseExample testCaseExample = new TestCaseExample();
        testCaseExample.createCriteria().andIdIn(caseIds);
        List<String> dataNodeIds = testCaseMapper.selectByExample(testCaseExample).stream()
                .map(TestCase::getNodeId)
                .collect(Collectors.toList());

        return getNodeTreeWithPruningTree(projectId, dataNodeIds);
    }

    public List<TestCaseNodeDTO> getAllNodeByPlanId(QueryNodeRequest request) {
        String planId = request.getTestPlanId();
        TestPlan testPlan = testPlanMapper.selectByPrimaryKey(planId);
        if (testPlan == null) {
            return Collections.emptyList();
        }
        return getAllNodeByProjectId(request);
    }

    public List<TestCaseNodeDTO> getAllNodeByProjectId(QueryNodeRequest request) {
        return getNodeTreeByProjectId(request.getProjectId());
    }

    public List<TestCaseNodeDTO> getAllNodeByReviewId(QueryNodeRequest request) {
        String reviewId = request.getReviewId();
        String projectId = request.getProjectId();
        TestCaseReview testCaseReview = testCaseReviewMapper.selectByPrimaryKey(reviewId);
        if (testCaseReview == null) {
            return Collections.emptyList();
        }

        return getNodeTreeByProjectId(projectId);
    }

    public Map<String, String> createNodeByTestCases(List<TestCaseWithBLOBs> testCases, String projectId) {
        List<String> nodePaths = testCases.stream()
                .map(TestCase::getNodePath)
                .collect(Collectors.toList());

        return this.createNodes(nodePaths, projectId);
    }

    public Map<String, String> createNodes(List<String> nodePaths, String projectId) {
        List<TestCaseNodeDTO> nodeTrees = getNodeTreeByProjectId(projectId);
        Map<String, String> pathMap = new HashMap<>();
        for (String item : nodePaths) {
            if (item == null) {
                throw new ExcelException(Translator.get("test_case_module_not_null"));
            }
            List<String> nodeNameList = new ArrayList<>(Arrays.asList(item.split("/")));
            Iterator<String> itemIterator = nodeNameList.iterator();
            Boolean hasNode = false;
            String rootNodeName;

            if (nodeNameList.size() <= 1) {
                throw new ExcelException(Translator.get("test_case_create_module_fail") + ":" + item);
            } else {
                itemIterator.next();
                itemIterator.remove();
                rootNodeName = itemIterator.next().trim();
                //原来没有，新建的树nodeTrees也不包含
                for (TestCaseNodeDTO nodeTree : nodeTrees) {
                    if (StringUtils.equals(rootNodeName, nodeTree.getName())) {
                        hasNode = true;
                        createNodeByPathIterator(itemIterator, "/" + rootNodeName, nodeTree,
                                pathMap, projectId, 2);
                    }
                }
            }
            if (!hasNode) {
                createNodeByPath(itemIterator, rootNodeName, null, projectId, 1, "", pathMap);
            }
        }
        return pathMap;

    }

    @Override
    public String insertNode(String nodeName, String pId, String projectId, Integer level) {
        TestCaseNode testCaseNode = new TestCaseNode();
        testCaseNode.setName(nodeName.trim());
        testCaseNode.setParentId(pId);
        testCaseNode.setProjectId(projectId);
        testCaseNode.setCreateTime(System.currentTimeMillis());
        testCaseNode.setUpdateTime(System.currentTimeMillis());
        testCaseNode.setLevel(level);
        testCaseNode.setCreateUser(SessionUtils.getUserId());
        testCaseNode.setId(UUID.randomUUID().toString());
        double pos = getNextLevelPos(projectId, level, pId);
        testCaseNode.setPos(pos);
        testCaseNodeMapper.insert(testCaseNode);
        return testCaseNode.getId();
    }

    public void dragNode(DragNodeRequest request) {

        checkTestCaseNodeExist(request);

        List<String> nodeIds = request.getNodeIds();

        List<TestCaseDTO> testCases = QueryTestCaseByNodeIds(nodeIds);

        TestCaseNodeDTO nodeTree = request.getNodeTree();

        if (nodeTree == null) {
            return;
        }

        List<TestCaseNode> updateNodes = new ArrayList<>();

        buildUpdateTestCase(nodeTree, testCases, updateNodes, "/", "0", 1);

        updateNodes = updateNodes.stream()
                .filter(item -> nodeIds.contains(item.getId()))
                .collect(Collectors.toList());

        batchUpdateTestCaseNode(updateNodes);

        batchUpdateTestCase(testCases);
    }

    private void batchUpdateTestCaseNode(List<TestCaseNode> updateNodes) {
        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        TestCaseNodeMapper testCaseNodeMapper = sqlSession.getMapper(TestCaseNodeMapper.class);
        updateNodes.forEach((value) -> {
            testCaseNodeMapper.updateByPrimaryKeySelective(value);
        });
        sqlSession.flushStatements();
        if (sqlSession != null && sqlSessionFactory != null) {
            SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
        }
    }

    private void batchUpdateTestCase(List<TestCaseDTO> testCases) {
        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        TestCaseMapper testCaseMapper = sqlSession.getMapper(TestCaseMapper.class);
        testCases.forEach((value) -> {
            testCaseMapper.updateByPrimaryKeySelective(value);
        });
        sqlSession.flushStatements();
        if (sqlSession != null && sqlSessionFactory != null) {
            SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
        }
    }

    private List<TestCaseDTO> QueryTestCaseByNodeIds(List<String> nodeIds) {
        QueryTestCaseRequest testCaseRequest = new QueryTestCaseRequest();
        testCaseRequest.setNodeIds(nodeIds);
        if(testCaseRequest.getFilters()!=null && !testCaseRequest.getFilters().containsKey("status")){
            testCaseRequest.getFilters().put("status",new ArrayList<>(0));
        }
        return extTestCaseMapper.list(testCaseRequest);
    }

    private void buildUpdateTestCase(TestCaseNodeDTO rootNode, List<TestCaseDTO> testCases,
                                     List<TestCaseNode> updateNodes, String rootPath, String pId, int level) {

        rootPath = rootPath + rootNode.getName();

        if (level > 8) {
            MSException.throwException(Translator.get("node_deep_limit"));
        }

        if (updateNodes != null) {
            TestCaseNode testCaseNode = new TestCaseNode();
            testCaseNode.setId(rootNode.getId());
            testCaseNode.setLevel(level);
            testCaseNode.setParentId(pId);
            updateNodes.add(testCaseNode);
        }

        for (TestCaseDTO item : testCases) {
            if (StringUtils.equals(item.getNodeId(), rootNode.getId())) {
                item.setNodePath(rootPath);
            }
        }

        List<TestCaseNodeDTO> children = rootNode.getChildren();
        if (children != null && children.size() > 0) {
            for (int i = 0; i < children.size(); i++) {
                buildUpdateTestCase(children.get(i), testCases, updateNodes, rootPath + '/', rootNode.getId(), level + 1);
            }
        }
    }

    public Project getProjectByNode(String nodeId) {
        TestCaseNodeExample example = new TestCaseNodeExample();
        example.createCriteria().andIdEqualTo(nodeId);
        List<TestCaseNode> testCaseNodes = testCaseNodeMapper.selectByExample(example);
        String projectId = testCaseNodes.get(0).getProjectId();
        return projectMapper.selectByPrimaryKey(projectId);
    }

    private TestCaseNode getCaseNode(String id) {
        return testCaseNodeMapper.selectByPrimaryKey(id);
    }

    @Override
    public TestCaseNodeDTO getNode(String id) {
        return extTestCaseNodeMapper.get(id);
    }

    @Override
    public void updatePos(String id, Double pos) {
        extTestCaseNodeMapper.updatePos(id, pos);
    }

    /**
     * 按照指定排序方式获取同级模块的列表
     *
     * @param projectId 所属项目 id
     * @param level     node level
     * @param parentId  node parent id
     * @param order     pos 排序方式
     * @return 按照指定排序方式排序的同级模块列表
     */
    private List<TestCaseNode> getPos(String projectId, int level, String parentId, String order) {
        TestCaseNodeExample example = new TestCaseNodeExample();
        TestCaseNodeExample.Criteria criteria = example.createCriteria();
        criteria.andProjectIdEqualTo(projectId).andLevelEqualTo(level);
        if (level != 1 && StringUtils.isNotBlank(parentId)) {
            criteria.andParentIdEqualTo(parentId);
        }
        example.setOrderByClause(order);
        return testCaseNodeMapper.selectByExample(example);
    }

    /**
     * 刷新同级模块的 pos 值
     *
     * @param projectId project id
     * @param level     node level
     * @param parentId  node parent id
     */
    @Override
    protected void refreshPos(String projectId, int level, String parentId) {
        List<TestCaseNode> nodes = getPos(projectId, level, parentId, "pos asc");
        if (!CollectionUtils.isEmpty(nodes)) {
            SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
            TestCaseNodeMapper testCaseNodeMapper = sqlSession.getMapper(TestCaseNodeMapper.class);
            AtomicDouble pos = new AtomicDouble(DEFAULT_POS);
            nodes.forEach((node) -> {
                node.setPos(pos.getAndAdd(DEFAULT_POS));
                testCaseNodeMapper.updateByPrimaryKey(node);
            });
            sqlSession.flushStatements();
            if (sqlSession != null && sqlSessionFactory != null) {
                SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
            }
        }
    }


    /**
     * 获得同级模块下一个 pos 值
     *
     * @param projectId project id
     * @param level     node level
     * @param parentId  node parent id
     * @return 同级模块下一个 pos 值
     */
    private double getNextLevelPos(String projectId, int level, String parentId) {
        List<TestCaseNode> list = getPos(projectId, level, parentId, "pos desc");
        if (!CollectionUtils.isEmpty(list) && list.get(0) != null && list.get(0).getPos() != null) {
            return list.get(0).getPos() + DEFAULT_POS;
        } else {
            return DEFAULT_POS;
        }
    }


    public String getLogDetails(List<String> ids) {
        TestCaseNodeExample example = new TestCaseNodeExample();
        example.createCriteria().andIdIn(ids);
        List<TestCaseNode> nodes = testCaseNodeMapper.selectByExample(example);
        if (org.apache.commons.collections.CollectionUtils.isNotEmpty(nodes)) {
            List<String> names = nodes.stream().map(TestCaseNode::getName).collect(Collectors.toList());
            OperatingLogDetails details = new OperatingLogDetails(JSON.toJSONString(ids), nodes.get(0).getProjectId(), String.join(",", names), nodes.get(0).getCreateUser(), new LinkedList<>());
            return JSON.toJSONString(details);
        }
        return null;
    }

    public String getLogDetails(TestCaseNode node) {
        TestCaseNode module = null;
        if (StringUtils.isNotEmpty(node.getId())) {
            module = testCaseNodeMapper.selectByPrimaryKey(node.getId());
        }
        if (module == null && StringUtils.isNotEmpty(node.getName())) {
            TestCaseNodeExample example = new TestCaseNodeExample();
            TestCaseNodeExample.Criteria criteria = example.createCriteria();
            criteria.andNameEqualTo(node.getName()).
                    andProjectIdEqualTo(node.getProjectId())
                    .andLevelEqualTo(node.getLevel());

            if (StringUtils.isNotEmpty(node.getId())) {
                criteria.andIdNotEqualTo(node.getId());
            }
            List<TestCaseNode> list = testCaseNodeMapper.selectByExample(example);
            if (org.apache.commons.collections.CollectionUtils.isNotEmpty(list)) {
                module = list.get(0);
            }
        }
        if (module != null) {
            List<DetailColumn> columns = ReflexObjectUtil.getColumns(module, ModuleReference.moduleColumns);
            OperatingLogDetails details = new OperatingLogDetails(JSON.toJSONString(module.getId()), module.getProjectId(), module.getCreateUser(), columns);
            return JSON.toJSONString(details);
        }
        return null;
    }

    public long countById(String nodeId) {
        TestCaseNodeExample example = new TestCaseNodeExample();
        example.createCriteria().andIdEqualTo(nodeId);
        return testCaseNodeMapper.countByExample(example);
    }

    public LinkedList<TestCaseNode> getPathNodeById(String moduleId) {
        TestCaseNode testCaseNode = testCaseNodeMapper.selectByPrimaryKey(moduleId);
        LinkedList<TestCaseNode> returnList = new LinkedList<>();

        while (testCaseNode != null) {
            returnList.addFirst(testCaseNode);
            if (testCaseNode.getParentId() == null) {
                testCaseNode = null;
            } else {
                testCaseNode = testCaseNodeMapper.selectByPrimaryKey(testCaseNode.getParentId());
            }
        }
        return returnList;
    }

    public long trashCount(String projectId) {
        return extTestCaseMapper.trashCount(projectId);
    }

    public void minderEdit(TestCaseMinderEditRequest request) {
        deleteNode(request.getIds());

        List<TestCaseMinderEditRequest.TestCaseNodeMinderEditItem> testCaseNodes = request.getTestCaseNodes();
        List<String> editNodeIds = new ArrayList<>();

        if (org.apache.commons.collections.CollectionUtils.isNotEmpty(testCaseNodes)) {

            for (TestCaseMinderEditRequest.TestCaseNodeMinderEditItem item: testCaseNodes) {
                if (StringUtils.isBlank(item.getParentId()) || item.getParentId().equals("root")) {
                    item.setParentId(null);
                }
                item.setProjectId(request.getProjectId());
                if (item.getIsEdit()) {
                    DragNodeRequest editNode = new DragNodeRequest();
                    BeanUtils.copyBean(editNode, item);
                    checkTestCaseNodeExist(editNode);
                    editNodeIds.add(editNode.getId());
                    testCaseNodeMapper.updateByPrimaryKeySelective(editNode);
                } else {
                    TestCaseNode testCaseNode = new TestCaseNode();
                    BeanUtils.copyBean(testCaseNode, item);
                    testCaseNode.setProjectId(request.getProjectId());
                    addNode(testCaseNode);
                }
            }

            editCasePathForMinder(editNodeIds, request.getProjectId());
        }
    }

    public long publicCount(String workSpaceId) {

        return extTestCaseMapper.countByWorkSpaceId(workSpaceId);
    }

    /**
     * 统计某些节点下的临时节点的数量
     * @param nodeIds
     * @return
     */
    public Map<String, Integer> getMinderTreeExtraNodeCount(List<String> nodeIds) {
        if (nodeIds.isEmpty()) {
            return new HashMap<>();
        }
        List<Map<String, Object>> moduleCountList = extTestCaseMapper.moduleExtraNodeCount(nodeIds);
        return this.parseModuleCountList(moduleCountList);
    }
}