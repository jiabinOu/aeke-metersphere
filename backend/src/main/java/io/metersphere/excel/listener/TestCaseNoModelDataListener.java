package io.metersphere.excel.listener;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.metersphere.base.domain.TestCase;
import io.metersphere.base.domain.TestCaseWithBLOBs;
import io.metersphere.commons.constants.CommonConstants;
import io.metersphere.commons.constants.TestCaseConstants;
import io.metersphere.commons.constants.TestCaseStatusConstants;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.*;
import io.metersphere.dto.CustomFieldDao;
import io.metersphere.excel.annotation.NotRequired;
import io.metersphere.excel.domain.ExcelErrData;
import io.metersphere.excel.domain.TestCaseExcelData;
import io.metersphere.excel.domain.TestCaseExcelDataFactory;
import io.metersphere.excel.utils.ExcelValidateHelper;
import io.metersphere.excel.utils.FunctionCaseImportEnum;
import io.metersphere.i18n.Translator;
import io.metersphere.track.request.testcase.TestCaseImportRequest;
import io.metersphere.track.service.TestCaseService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 由于功能案例中含有自定义字段。导入的时候使用无模板对象的读取方式
 *
 * @author song.tianyang
 * @Date 2021/7/7 4:25 下午
 */
public class TestCaseNoModelDataListener extends AnalysisEventListener<Map<Integer, String>> {

    private Class excelDataClass;

    protected List<ExcelErrData<TestCaseExcelData>> errList = new ArrayList<>();

    protected List<TestCaseExcelData> excelDataList = new ArrayList<>();

    private Map<Integer, String> headMap;
    private Map<String, String> excelHeadToFieldNameDic = new HashMap<>();


    /**
     * 每隔2000条存储数据库，然后清理list ，方便内存回收
     */
    protected static final int BATCH_COUNT = 5000;

    private TestCaseService testCaseService;

    protected List<TestCaseExcelData> updateList = new ArrayList<>();  //存储待更新用例的集合

    protected List<TestCaseExcelData> list = new ArrayList<>();

    protected boolean isUpdated = false;  //判断是否更新过用例，将会传给前端

    Set<String> customIds;

    private List<String> names = new LinkedList<>();
    private List<String> ids = new LinkedList<>();

    Map<String, CustomFieldDao> customFieldsMap = new HashMap<>();

    private TestCaseImportRequest request;

    public boolean isUpdated() {
        return isUpdated;
    }

    public TestCaseNoModelDataListener(TestCaseImportRequest request, Class c) {
        this.excelDataClass = c;
        this.testCaseService = (TestCaseService) CommonBeanFactory.getBean("testCaseService");
        customIds = new HashSet<>();

        this.request = request;

        List<CustomFieldDao> customFields = request.getCustomFields();
        if (CollectionUtils.isNotEmpty(customFields)) {
            for (CustomFieldDao dto : customFields) {
                String name = dto.getName();
                if (StringUtils.isNotEmpty(name)) {
                    name = name.trim();
                }
                if (StringUtils.equalsAny(name, "责任人", "維護人", "Maintainer")) {
                    customFieldsMap.put("maintainer", dto);
                } else if (StringUtils.equalsAny(name, "用例等级", "用例等級", "Priority")) {
                    customFieldsMap.put("priority", dto);
                } else if (StringUtils.equalsAny(name, "用例状态", "用例狀態", "Case status")) {
                    customFieldsMap.put("status", dto);
                } else {
                    customFieldsMap.put(name, dto);
                }
            }
        }
    }

    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        this.headMap = headMap;
        try {
            this.genExcelHeadToFieldNameDicAndGetNotRequiredFields();
        } catch (NoSuchFieldException e) {
            LogUtil.error(e);
        }
        this.formatHeadMap();
        super.invokeHeadMap(headMap, context);
    }

    public void invoke(Map<Integer, String> data, AnalysisContext analysisContext) {
        String errMsg;
        Integer rowIndex = analysisContext.readRowHolder().getRowIndex();
        String updateMsg = "update_testcase";
        TestCaseExcelData testCaseExcelData = this.parseDataToModel(data);
        try {
            //根据excel数据实体中的javax.validation + 正则表达式来校验excel数据
            errMsg = ExcelValidateHelper.validateEntity(testCaseExcelData);
            //自定义校验规则
            errMsg = validate(testCaseExcelData, errMsg);
        } catch (NoSuchFieldException e) {
            errMsg = Translator.get("parse_data_error");
            LogUtil.error(e.getMessage(), e);
        }

        if (!StringUtils.isEmpty(errMsg)) {

            //如果errMsg只有"update testcase"，说明用例待更新
            if (!errMsg.equals(updateMsg)) {
                ExcelErrData excelErrData = new ExcelErrData(testCaseExcelData, rowIndex,
                        Translator.get("number") + " " + rowIndex + " " + Translator.get("row") + Translator.get("error")
                                + "：" + errMsg);
                errList.add(excelErrData);
            }
        } else {
            list.add(testCaseExcelData);
        }
        if (list.size() > BATCH_COUNT) {
            saveData();
            list.clear();
        }
    }

    public String validate(TestCaseExcelData data, String errMsg) {
        Set<String> savedCustomIds = request.getSavedCustomIds();
        String importType = request.getImportType();
        StringBuilder stringBuilder = new StringBuilder(errMsg);
        if (request.isUseCustomId() || StringUtils.equals(importType, FunctionCaseImportEnum.Update.name())) {
            if (data.getCustomNum() == null) {
                stringBuilder.append(Translator.get("id_required") + ";");
            } else {
                String customId = data.getCustomNum();
                if (StringUtils.isEmpty(customId)) {
                    stringBuilder.append(Translator.get("id_required") + ";");
                } else if (customIds.contains(customId.toLowerCase())) {
                    stringBuilder.append(Translator.get("id_repeat_in_table") + ";");
                } else if (StringUtils.equals(FunctionCaseImportEnum.Create.name(), importType) && savedCustomIds.contains(customId)) {
                    stringBuilder.append(Translator.get("custom_num_is_exist") + ";");
                } else if (StringUtils.equals(FunctionCaseImportEnum.Update.name(), importType) && !savedCustomIds.contains(customId)) {
                    stringBuilder.append(Translator.get("custom_num_is_not_exist") + ";");
                } else {
                    customIds.add(customId.toLowerCase());
                }
            }
        }

        String nodePath = data.getNodePath();
        //校验”所属模块"
        if (nodePath != null) {
            String[] nodes = nodePath.split("/");
            //校验模块深度
            if (nodes.length > TestCaseConstants.MAX_NODE_DEPTH + 1) {
                stringBuilder.append(Translator.get("test_case_node_level_tip") +
                        TestCaseConstants.MAX_NODE_DEPTH + Translator.get("test_case_node_level") + "; ");
            }
            //模块名不能为空
            for (int i = 0; i < nodes.length; i++) {
                if (i != 0 && StringUtils.equals(nodes[i].trim(), "")) {
                    stringBuilder.append(Translator.get("module_not_null") + "; ");
                    break;
                }
            }
            //增加字数校验，每一层不能超过100字
            for (int i = 0; i < nodes.length; i++) {
                String nodeStr = nodes[i];
                if (StringUtils.isNotEmpty(nodeStr)) {
                    if (nodeStr.trim().length() > 100) {
                        stringBuilder.append(Translator.get("module") + Translator.get("test_track.length_less_than") + "100:" + nodeStr);
                        break;
                    }
                }
            }
        }

        //校验自定义必填字段
        for (Map.Entry<String, CustomFieldDao> customEntry : customFieldsMap.entrySet()) {
            String customName = customEntry.getKey();
            CustomFieldDao field = customEntry.getValue();

            if (field.getRequired()) {
                String value = null;
                if (StringUtils.equals(customName, "status")) {
                    value = data.getStatus();
                    if (!checkCaseStatus(value)) {
                        stringBuilder.append(Translator.get("case_status_not_exist") + "; ");
                    }
                } else if (StringUtils.equals(customName, "priority")) {
                    value = data.getPriority();
                } else if (StringUtils.equals(customName, "maintainer")) {
                    value = data.getMaintainer();
                    //校验维护人
                    if (StringUtils.isBlank(data.getMaintainer())) {
                        data.setMaintainer(SessionUtils.getUserId());
                    } else {
                        if (!request.getUserIds().contains(data.getMaintainer())) {
                            stringBuilder.append(Translator.get("user_not_exists") + "：" + data.getMaintainer() + "; ");
                        }
                    }
                    continue;
                } else {
                    value = data.getCustomDatas().get(customName);
                }
                if (StringUtils.isEmpty(value)) {
                    stringBuilder.append(field.getName() + " " + Translator.get("required") + "; ");
                }
            }
        }

        /*
        校验Excel中是否有ID
            有的话校验ID是否已在当前项目中存在，存在则更新用例，
            不存在则继续校验看是否重复，不重复则新建用例。
         */
        if (null != data.getCustomNum()) {  //当前读取的数据有ID

            if (StringUtils.equals(request.getImportType(), FunctionCaseImportEnum.Update.name())) {
                String checkResult = null;
                if (request.isUseCustomId()) {
                    checkResult = testCaseService.checkCustomIdExist(data.getCustomNum(), request.getProjectId());
                } else {
                    int customNumId = -1;
                    try {
                        customNumId = Integer.parseInt(data.getCustomNum());
                    } catch (Exception e) {
                    }
                    if (customNumId < 0) {
                        stringBuilder.append(Translator.get("id_not_rightful") + "[" + data.getCustomNum() + "]; ");
                    } else {
                        checkResult = testCaseService.checkIdExist(customNumId, request.getProjectId());
                    }
                }
                if (null != checkResult) {  //该ID在当前项目中存在
                    //如果前面所经过的校验都没报错
                    if (StringUtils.isEmpty(stringBuilder)) {
                        data.setId(checkResult);
                        updateList.add(data);   //将当前数据存入更新列表
                        stringBuilder.append("update_testcase");   //该信息用于在invoke方法中判断是否该更新用例
                    }
                    return stringBuilder.toString();
                } else {
                /*
                该ID在当前数据库中不存在，应当继续校验用例是否重复,
                在下面的校验过程中，num的值会被用于判断是否重复，所以应当先设置为null
                 */
                    data.setNum(null);
                }
            }

        }

        /*
        校验用例
         */
        if (request.getTestCaseNames().contains(data.getName())) {
            TestCaseWithBLOBs testCase = new TestCaseWithBLOBs();
            BeanUtils.copyBean(testCase, data);
            testCase.setProjectId(request.getProjectId());
            String steps = getSteps(data);
            testCase.setSteps(steps);
            testCase.setType("functional");

            boolean dbExist = testCaseService.exist(testCase);
            boolean excelExist = false;

            if (dbExist) {
                // db exist
                stringBuilder.append(Translator.get("test_case_already_exists") + "：" + data.getName() + "; ");
            } else {
                // @Data 重写了 equals 和 hashCode 方法
                excelExist = excelDataList.contains(data);
            }

            if (excelExist) {
                // excel exist
                stringBuilder.append(Translator.get("test_case_already_exists_excel") + "：" + data.getName() + "; ");
            } else {
                if (!dbExist) {
                    excelDataList.add(data);
                }
            }

        } else {
            request.getTestCaseNames().add(data.getName());
            excelDataList.add(data);
        }
        return stringBuilder.toString();
    }

    public List<String> getNames() {
        return this.names;
    }

    public List<String> getIds() {
        return this.ids;
    }

    public void setNames(List<String> names) {
        this.names = names;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public void saveData() {

        //excel中用例都有错误时就返回，只要有用例可用于更新或者插入就不返回
        if (!errList.isEmpty() && !request.isIgnore()) {
            return;
        }

        if (!(list.size() == 0)) {
            List<TestCaseWithBLOBs> result = list.stream()
                    .map(item -> this.convert2TestCase(item))
                    .collect(Collectors.toList());
            testCaseService.saveImportData(result, request);
            this.names = result.stream().map(TestCase::getName).collect(Collectors.toList());
            this.ids = result.stream().map(TestCase::getId).collect(Collectors.toList());
            this.isUpdated = true;
        }

        if (!(updateList.size() == 0)) {
            List<TestCaseWithBLOBs> result2 = updateList.stream()
                    .map(item -> this.convert2TestCaseForUpdate(item))
                    .collect(Collectors.toList());
            testCaseService.updateImportData(result2, request);
            this.isUpdated = true;
            this.names = result2.stream().map(TestCase::getName).collect(Collectors.toList());
            this.ids = result2.stream().map(TestCase::getId).collect(Collectors.toList());
            updateList.clear();
        }

    }

    /**
     * 检验导入功能用例的状态
     *
     * @param status
     * @return
     */
    private boolean checkCaseStatus(String status) {
        if (StringUtils.equalsAnyIgnoreCase(status, "Underway", "进行中", "進行中")) {
            return true;
        } else if (StringUtils.equalsAnyIgnoreCase(status, "Prepare", "未开始", "未開始")) {
            return true;
        } else if (StringUtils.equalsAnyIgnoreCase(status, "Completed", "已完成", "已完成")) {
            return true;
        }
        return false;
    }

    private TestCaseWithBLOBs convert2TestCase(TestCaseExcelData data) {
        TestCaseWithBLOBs testCase = new TestCaseWithBLOBs();
        BeanUtils.copyBean(testCase, data);
        testCase.setId(UUID.randomUUID().toString());
        testCase.setProjectId(request.getProjectId());
        testCase.setCreateTime(System.currentTimeMillis());
        testCase.setUpdateTime(System.currentTimeMillis());
        if (request.isUseCustomId()) {
            testCase.setCustomNum(data.getCustomNum());
        }

        String nodePath = data.getNodePath();

        if (!nodePath.startsWith("/")) {
            nodePath = "/" + nodePath;
        }
        if (nodePath.endsWith("/")) {
            nodePath = nodePath.substring(0, nodePath.length() - 1);
        }
        testCase.setNodePath(nodePath);

        //将标签设置为前端可解析的格式
        String modifiedTags = modifyTagPattern(data);
        testCase.setTags(modifiedTags);
        testCase.setType("functional");

        String caseStatusValue = "";
        if (StringUtils.equalsAnyIgnoreCase(data.getStatus(), "Underway", "进行中", "進行中")) {
            caseStatusValue = "Underway";
        } else if (StringUtils.equalsAnyIgnoreCase(data.getStatus(), "Prepare", "未开始", "未開始")) {
            caseStatusValue = "Prepare";
        } else if (StringUtils.equalsAnyIgnoreCase(data.getStatus(), "Completed", "已完成", "已完成")) {
            caseStatusValue = "Completed";
        }
        data.setStatus(caseStatusValue);

        String customFieldsJson = this.getCustomFieldsJson(data);
        testCase.setCustomFields(customFieldsJson);
        if (StringUtils.isNotBlank(data.getMaintainer())) {
            testCase.setMaintainer(data.getMaintainer());
        }

        if (StringUtils.isNotBlank(data.getStepModel())
                && StringUtils.equals(data.getStepModel(), TestCaseConstants.StepModel.TEXT.name())) {
            testCase.setStepDescription(data.getStepDesc());
            testCase.setExpectedResult(data.getStepResult());
        } else {
            String steps = getSteps(data);
            testCase.setSteps(steps);
        }
        return testCase;
    }

    /**
     * 将Excel中的数据对象转换为用于更新操作的用例数据对象，
     *
     * @param data
     * @return
     */
    private TestCaseWithBLOBs convert2TestCaseForUpdate(TestCaseExcelData data) {
        TestCaseWithBLOBs testCase = new TestCaseWithBLOBs();
        BeanUtils.copyBean(testCase, data);
        testCase.setProjectId(request.getProjectId());
        testCase.setUpdateTime(System.currentTimeMillis());

        //调整nodePath格式
        String nodePath = data.getNodePath();
        if (!nodePath.startsWith("/")) {
            nodePath = "/" + nodePath;
        }
        if (nodePath.endsWith("/")) {
            nodePath = nodePath.substring(0, nodePath.length() - 1);
        }
        testCase.setNodePath(nodePath);

        String steps = getSteps(data);
        testCase.setSteps(steps);

        JSONArray customArr = new JSONArray();
        String caseStatusValue = "";
        if (StringUtils.equalsAnyIgnoreCase(data.getStatus(), "Underway", "进行中", "進行中")) {
            caseStatusValue = "Underway";
        } else if (StringUtils.equalsAnyIgnoreCase(data.getStatus(), "Prepare", "未开始", "未開始")) {
            caseStatusValue = "Prepare";
        } else if (StringUtils.equalsAnyIgnoreCase(data.getStatus(), "Completed", "已完成", "已完成")) {
            caseStatusValue = "Completed";
        }
        data.setStatus(caseStatusValue);

        String customFieldsJson = this.getCustomFieldsJson(data);
        testCase.setCustomFields(customFieldsJson);
        if (StringUtils.isNotBlank(data.getMaintainer())) {
            testCase.setMaintainer(data.getMaintainer());
        }

        //将标签设置为前端可解析的格式
        String modifiedTags = modifyTagPattern(data);
        testCase.setTags(modifiedTags);

        if (!request.isUseCustomId()) {
            testCase.setNum(Integer.parseInt(data.getCustomNum()));
            testCase.setCustomNum(null);
        }

        return testCase;
    }

    /**
     * 调整tags格式，便于前端进行解析。
     * 例如对于：标签1，标签2。将调整为:["标签1","标签2"]。
     */
    public String modifyTagPattern(TestCaseExcelData data) {
        String tags = data.getTags();
        try {
            if (StringUtils.isNotBlank(tags)) {
                JSONArray array = JSONArray.parseArray(tags);
                return array.toJSONString();
            }
            return "[]";
        } catch (Exception e) {
            if (tags != null) {
                Stream<String> stringStream = Arrays.stream(tags.split("[,;，；\"\\r|\\n|\\r\\n\"]"));  //当标签值以中英文的逗号和分号分隔时才能正确解析
                List<String> tagList = stringStream.map(tag -> tag = "\"" + tag + "\"")
                        .collect(Collectors.toList());
                String modifiedTags = StringUtils.join(tagList, ",");
                modifiedTags = "[" + modifiedTags + "]";
                return modifiedTags;
            } else {
                return "[]";
            }
        }
    }

    public String getSteps(TestCaseExcelData data) {
        JSONArray jsonArray = new JSONArray();

        List<String> stepDescList = new ArrayList<>();
        List<String> stepResList = new ArrayList<>();
        ListUtils<String> listUtils = new ListUtils<>();

        Set<Integer> rowNums = new HashSet<>();
        if (data.getStepDesc() != null) {
            String[] stepDesc = data.getStepDesc().split("\r|\n|\r\n");

            int rowIndex = 1;
            for (String row : stepDesc) {
                TestCaseNoModelDataListener.RowInfo rowInfo = this.parseIndexInRow(row, rowIndex);
                stepDescList.add(rowInfo.rowInfo);
                rowNums.add(rowIndex++);
            }
        } else {
            stepDescList.add("");
        }

        if (data.getStepResult() != null) {
            String[] stepRes = data.getStepResult().split("\r|\n|\r\n");
            int lastStepIndex = 1;
            for (String row : stepRes) {
                TestCaseNoModelDataListener.RowInfo rowInfo = this.parseIndexInRow(row, lastStepIndex);
                String rowMessage = rowInfo.rowInfo;
                stepResList.add(rowMessage);
                lastStepIndex++;
            }
        } else {
            stepResList.add("");
        }

        int index = stepDescList.size() > stepResList.size() ? stepDescList.size() : stepResList.size();

        for (int i = 0; i < index; i++) {

            // 保持插入顺序，判断用例是否有相同的steps
            JSONObject step = new JSONObject(true);
            step.put("num", i + 1);
            if (i < stepDescList.size()) {
                step.put("desc", stepDescList.get(i));
            } else {
                step.put("desc", "");
            }

            if (i < stepResList.size()) {
                step.put("result", stepResList.get(i));
            } else {
                step.put("result", "");
            }

            jsonArray.add(step);
        }
        return jsonArray.toJSONString();
    }

    private RowInfo parseIndexInRow(String row, int rowIndex) {
        RowInfo rowInfo = new RowInfo();
        String parseString = row;

        int index = -1;
        String rowMessage = row;
        String[] indexSplitCharArr = new String[]{")", "）", "]", "】", ".", ",", "，", "。"};
        if (StringUtils.startsWithAny(row, "(", "（", "[", "【")) {
            parseString = parseString.substring(1);
        }
        for (String splitChar : indexSplitCharArr) {
            if (StringUtils.contains(parseString, splitChar)) {
                String[] rowSplit = StringUtils.split(parseString, splitChar);
                if (rowSplit.length > 0) {
                    String indexString = rowSplit[0];
                    if (StringUtils.isNumeric(indexString) && indexString.equals(String.valueOf(rowIndex))) {
                        try {
                            index = Integer.parseInt(indexString);
                            rowMessage = StringUtils.substring(parseString, indexString.length() + splitChar.length());
                        } catch (Exception e) {
                        }

                        if (index > -1) {
                            break;
                        } else {
                            rowMessage = row;
                        }
                    }
                }
            }
        }
        rowInfo.index = index;
        if (rowMessage == null) {
            rowMessage = "";
        }
        rowInfo.rowInfo = rowMessage;
        return rowInfo;
    }

    private TestCaseExcelData parseDataToModel(Map<Integer, String> row) {
        TestCaseExcelData data = new TestCaseExcelDataFactory().getTestCaseExcelDataLocal();
        for (Map.Entry<Integer, String> headEntry : headMap.entrySet()) {
            Integer index = headEntry.getKey();
            String field = headEntry.getValue();
            String value = StringUtils.isEmpty(row.get(index)) ? "" : row.get(index);

            if (excelHeadToFieldNameDic.containsKey(field)) {
                field = excelHeadToFieldNameDic.get(field);
            }

            if (StringUtils.equals(field, "id")) {
                data.setId(value);
            } else if (StringUtils.equals(field, "num")) {
                try {
                    data.setNum(Integer.parseInt(value));
                } catch (Exception e) {
                    MSException.throwException("[ID]" + value + "格式化异常");
                }
            } else if (StringUtils.equals(field, "customNum")) {
                data.setCustomNum(value);
            } else if (StringUtils.equals(field, "name")) {
                data.setName(value);
            } else if (StringUtils.equals(field, "nodePath")) {
                data.setNodePath(value);
            } else if (StringUtils.equals(field, "tags")) {
                data.setTags(value);
            } else if (StringUtils.equals(field, "prerequisite")) {
                data.setPrerequisite(value);
            } else if (StringUtils.equals(field, "remark")) {
                data.setRemark(value);
            } else if (StringUtils.equals(field, "stepDesc")) {
                data.setStepDesc(value);
            } else if (StringUtils.equals(field, "stepResult")) {
                data.setStepResult(value);
            } else if (StringUtils.equals(field, "stepModel")) {
                data.setStepModel(value);
            } else if (StringUtils.equals(field, "status")) {
                data.setStatus(parseStatus(value));
            } else if (StringUtils.equals(field, "maintainer")) {
                data.setMaintainer(value);
            } else if (StringUtils.equals(field, "priority")) {
                data.setPriority(value);
            } else {
                data.getCustomDatas().put(field, value);
            }
        }
        return data;
    }

    public List<ExcelErrData<TestCaseExcelData>> getErrList() {
        return this.errList;
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        saveData();
        list.clear();
    }

    private void formatHeadMap() {
        for (Integer key : headMap.keySet()) {
            String name = headMap.get(key);
            if (excelHeadToFieldNameDic.containsKey(name)) {
                headMap.put(key, excelHeadToFieldNameDic.get(name));
            }
        }
    }

    private String getCustomFieldsJson(TestCaseExcelData data) {
        JSONArray customArr = new JSONArray();
        for (Map.Entry<String, CustomFieldDao> customEntry : customFieldsMap.entrySet()) {
            String customName = customEntry.getKey();
            CustomFieldDao field = customEntry.getValue();

            Object value;
            String inputValue;
            if (StringUtils.equals(customName, "status")) {
                inputValue = data.getStatus();
            } else if (StringUtils.equals(customName, "priority")) {
                inputValue = data.getPriority();
            } else if (StringUtils.equals(customName, "maintainer")) {
                inputValue = data.getMaintainer();
            } else {
                inputValue = data.getCustomDatas().get(customName);
            }
            if (StringUtils.isEmpty(inputValue)) {
                inputValue = StringUtils.EMPTY;
            }
            value = inputValue;

            if (StringUtils.equalsAnyIgnoreCase(field.getType(), "multipleSelect", "multipleInput")) {
                value = modifyMultipleSelectPattern(field, inputValue);
            }
            if (StringUtils.equalsAnyIgnoreCase(field.getType(), "select")) {
                value = parseText2Value(field, inputValue);
            }
            JSONObject statusObj = new JSONObject();
            statusObj.put("id", UUID.randomUUID().toString());
            statusObj.put("name", field.getName());
            statusObj.put("value", value);
            statusObj.put("customData", null);
            statusObj.put("type", field.getType());
            customArr.add(statusObj);
        }
        return customArr.toJSONString();
    }

    private String parseText2Value(CustomFieldDao field, String inputValue) {
        String optionsStr = field.getOptions();
        if (StringUtils.isNotBlank(optionsStr)) {
            List<JSONObject> options = JSONArray.parseArray(optionsStr, JSONObject.class);
            for (JSONObject option : options) {
                if (StringUtils.equals(option.getString("text"), inputValue)) {
                    return option.getString("value");
                }
            }
        }
        return inputValue;
    }

    /**
     * 调整自定义多选下拉框格式，便于前端进行解析。
     * 例如对于：下拉值1，下拉值2。将调整为:["下拉值1","下拉值2"]。
     */
    public List modifyMultipleSelectPattern(CustomFieldDao field, String values) {
        List<String> result = new ArrayList<>();
        try {
            if (StringUtils.isNotBlank(values)) {
                result = JSONArray.parseArray(values, String.class);
            }
        } catch (Exception e) {
            if (values != null) {
                if (values.startsWith("[")) {
                    values = values.substring(1);
                }
                if (values.endsWith("]")) {
                    values = values.substring(0, values.length() - 1);
                }
                result = Arrays.asList(values.split("[,;，；]")); //当标签值以中英文的逗号和分号分隔时才能正确解析
            }
        }
        for (int i = 0; i < result.size(); i++) {
            result.set(i, parseText2Value(field, result.get(i)));
        }
        return result;
    }

    /**
     * @description: 获取注解里ExcelProperty的value
     */
    public Set<String> genExcelHeadToFieldNameDicAndGetNotRequiredFields() throws NoSuchFieldException {

        Set<String> result = new HashSet<>();
        Field field;
        Field[] fields = excelDataClass.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            field = excelDataClass.getDeclaredField(fields[i].getName());
            field.setAccessible(true);
            ExcelProperty excelProperty = field.getAnnotation(ExcelProperty.class);
            if (excelProperty != null) {
                StringBuilder value = new StringBuilder();
                for (String v : excelProperty.value()) {
                    value.append(v);
                }
                excelHeadToFieldNameDic.put(value.toString(), field.getName());
                // 检查是否必有的头部信息
                if (field.getAnnotation(NotRequired.class) != null) {
                    result.add(value.toString());
                }
            }
        }
        return result;
    }

    class RowInfo {
        public int index;
        public String rowInfo;
    }

    public static boolean isNumericzidai(String str) {
        Pattern pattern = Pattern.compile("-?[0-9]+(\\.[0-9]+)?");
        Matcher isNum = pattern.matcher(str);
        if (!isNum.matches()) {
            return false;
        }
        return true;
    }

    public String parseStatus(String parseStatus){
        String caseStatusValue = "";
        if(StringUtils.equalsAnyIgnoreCase(parseStatus,
                TestCaseStatusConstants.UNDERWAY_US, TestCaseStatusConstants.UNDERWAY_CN, TestCaseStatusConstants.UNDERWAY_TW)){
            caseStatusValue = TestCaseStatusConstants.UNDERWAY_US;
        }else if(StringUtils.equalsAnyIgnoreCase(parseStatus,
                TestCaseStatusConstants.PREPARE_US, TestCaseStatusConstants.PREPARE_CN, TestCaseStatusConstants.PREPARE_TW)){
            caseStatusValue = TestCaseStatusConstants.PREPARE_US;
        }else if(StringUtils.equalsAnyIgnoreCase(parseStatus,
                TestCaseStatusConstants.COMPLETED_US, TestCaseStatusConstants.COMPLETED_CN, TestCaseStatusConstants.COMPLETED_TW)){
            caseStatusValue = TestCaseStatusConstants.COMPLETED_US;
        }else if(StringUtils.equalsAnyIgnoreCase(parseStatus, CommonConstants.TrashStatus)){
            caseStatusValue = CommonConstants.TrashStatus;
        }
        return caseStatusValue;
    }
}
