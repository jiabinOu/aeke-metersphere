<template>
  <el-card>
    <div class="card-content">
      <div class="ms-main-div" @click="showAll">

        <!--操作按钮-->
        <div class="ms-opt-btn">
          <el-tooltip :content="$t('commons.follow')" placement="bottom" effect="dark" v-if="!showFollow">
            <i class="el-icon-star-off"
               style="color: #06dc9c; font-size: 25px;  margin-right: 15px;cursor: pointer;position: relative;top: 5px "
               @click="saveFollow"/>
          </el-tooltip>
          <el-tooltip :content="$t('commons.cancel')" placement="bottom" effect="dark" v-if="showFollow">
            <i class="el-icon-star-on"
               style="color: #06dc9c; font-size: 28px; margin-right: 15px;cursor: pointer;position: relative;top: 5px "
               @click="saveFollow"/>
          </el-tooltip>
          <el-link type="primary" style="margin-right: 20px" @click="openHis" v-if="form.id">
            {{ $t('operating_log.change_history') }}
          </el-link>
          <!--  版本历史 -->
          <ms-version-history v-xpack
                              ref="versionHistory"
                              :version-data="versionData"
                              :current-id="currentTestCaseInfo.id"
                              :current-project-id="currentProjectId"
                              :is-read="true"
                              @compare="compare" @checkout="checkout" @create="create" @del="del"/>
          <ms-table-button v-if="this.path!=='/test/case/add'"
                           v-permission="['PROJECT_TRACK_CASE:READ+COPY']"
                           id="inputDelay"
                           type="primary"
                           :content="$t('commons.copy')"
                           size="small" @click="handleCopyPublic"
                           icon=""
                           />
        </div>
        <el-form :model="form" :rules="rules" ref="caseFrom" v-loading="result.loading" class="case-form">
          <ms-form-divider :title="$t('test_track.plan_view.base_info')"/>
          <el-row>
            <el-col :span="8">
              <el-form-item
                :placeholder="$t('test_track.case.input_name')"
                :label="$t('test_track.case.name')"
                :label-width="formLabelWidth"
                prop="name">
                <el-input :disabled="readOnly" v-model="form.name" size="small" class="ms-case-input"></el-input>
              </el-form-item>
            </el-col>

            <el-col :span="8">
              <el-form-item :label="$t('test_track.case.project')" :label-width="formLabelWidth" prop="projectId">
                <el-select v-model="form.projectId" filterable clearable :disabled="readOnly">
                  <el-option v-for="item in projectList" :key="item.id" :label="item.name" :value="item.id"></el-option>
                </el-select>
              </el-form-item>
            </el-col>

            <el-col :span="8">
              <el-form-item :label="$t('commons.tag')" :label-width="formLabelWidth" prop="tag">
                <ms-input-tag :read-only="readOnly" :currentScenario="form" v-if="showInputTag" ref="tag" :disabled="true"
                              class="ms-case-input"/>
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 自定义字段 -->
          <el-form v-if="isFormAlive" :model="customFieldForm" :rules="customFieldRules" ref="customFieldForm"
                   class="case-form">
            <custom-filed-form-item :form="customFieldForm" :form-label-width="formLabelWidth"
                                    :issue-template="testCaseTemplate" :is-public="publicEnable"/>
          </el-form>

          <el-row v-if="isCustomNum">
            <el-col :span="7">
              <el-form-item label="ID" :label-width="formLabelWidth" prop="customNum">
                <el-input :disabled="readOnly" v-model.trim="form.customNum" size="small"
                          class="ms-case-input"></el-input>
              </el-form-item>
            </el-col>
          </el-row>


          <ms-form-divider :title="$t('test_track.case.step_info')"/>

          <form-rich-text-item :disabled="readOnly" :label-width="formLabelWidth"
                               :title="$t('test_track.case.prerequisite')" :data="form" prop="prerequisite"/>

          <step-change-item :label-width="formLabelWidth" :form="form"/>
          <form-rich-text-item :disabled="readOnly" :label-width="formLabelWidth" v-if="form.stepModel === 'TEXT'"
                               :title="$t('test_track.case.step_desc')" :data="form" prop="stepDescription"/>
          <form-rich-text-item :disabled="readOnly" :label-width="formLabelWidth" v-if="form.stepModel === 'TEXT'"
                               :title="$t('test_track.case.expected_results')" :data="form" prop="expectedResult"/>

          <test-case-step-item :label-width="formLabelWidth" v-if="form.stepModel === 'STEP' || !form.stepModel"
                               :form="form" :read-only="readOnly"/>

          <ms-form-divider :title="$t('test_track.case.other_info')"/>

          <test-case-edit-other-info :read-only="readOnly" :project-id="projectIds" :form="form" :comments.sync="comments"
                                     :label-width="formLabelWidth" :case-id="form.id" ref="otherInfo"/>
          <test-case-comment :case-id="form.id"
                             @getComments="getComments" ref="testCaseComment"/>

        </el-form>

      </div>
      <ms-change-history ref="changeHistory"/>

      <el-dialog
        :fullscreen="true"
        :visible.sync="dialogVisible"
        :destroy-on-close="true"
        width="100%"
      >
        <test-case-version-diff  v-if="dialogVisible" :old-data="oldData" :new-data="newData"
                                :tree-nodes="treeNodes" :is-public="publicEnable"></test-case-version-diff>

      </el-dialog>


    </div>
    <batch-move ref="testBatchMove" :public-enable="publicEnable"
                @copyPublic="copyPublic"/>
  </el-card>


</template>

<script>
import {TokenKey} from '@/common/js/constants';
import MsDialogFooter from '../../../common/components/MsDialogFooter';
import {
  getCurrentProjectID,
  getCurrentUser,
  getNodePath,
  getUUID,
  handleCtrlSEvent,
  hasLicense,
  listenGoBack,
  removeGoBackListener
} from "@/common/js/utils";
import TestCaseAttachment from "@/business/components/track/case/components/TestCaseAttachment";
import CaseComment from "@/business/components/track/case/components/CaseComment";
import MsInputTag from "@/business/components/api/automation/scenario/MsInputTag";
import MsPreviousNextButton from "../../../common/components/MsPreviousNextButton";
import {STEP} from "@/business/components/api/automation/scenario/Setting";
import TestCaseComment from "@/business/components/track/case/components/TestCaseComment";
import ReviewCommentItem from "@/business/components/track/review/commom/ReviewCommentItem";
import {API_STATUS, REVIEW_STATUS} from "@/business/components/api/definition/model/JsonData";
import MsTableButton from "@/business/components/common/components/MsTableButton";
import MsSelectTree from "../../../common/select-tree/SelectTree";
import MsTestCaseStepRichText from "./MsRichText";
import CustomFiledComponent from "@/business/components/project/template/CustomFiledComponent";
import {buildCustomFields, buildTestCaseOldFields, getTemplate, parseCustomField} from "@/common/js/custom_field";
import MsFormDivider from "@/business/components/common/components/MsFormDivider";
import TestCaseEditOtherInfo from "@/business/components/track/case/components/TestCaseEditOtherInfo";
import FormRichTextItem from "@/business/components/track/case/components/FormRichTextItem";
import TestCaseStepItem from "@/business/components/track/case/components/TestCaseStepItem";
import StepChangeItem from "@/business/components/track/case/components/StepChangeItem";
import MsChangeHistory from "../../../history/ChangeHistory";
import {getTestTemplate} from "@/network/custom-field-template";
import CustomFiledFormItem from "@/business/components/common/components/form/CustomFiledFormItem";
import BatchMove from "@/business/components/track/case/components/BatchMove";
import TestCaseVersionDiff from "@/business/components/track/case/version/TestCaseVersionDiff";


const requireComponent = require.context('@/business/components/xpack/', true, /\.vue$/);
const versionHistory = requireComponent.keys().length > 0 ? requireComponent("./version/VersionHistory.vue") : {};

export default {
  name: "TestCaseEditShow",
  components: {
    CustomFiledFormItem,
    StepChangeItem,
    TestCaseStepItem,
    FormRichTextItem,
    TestCaseEditOtherInfo,
    MsFormDivider,
    CustomFiledComponent,
    MsTableButton,
    MsSelectTree,
    ReviewCommentItem,
    TestCaseComment, MsPreviousNextButton, MsInputTag, CaseComment, MsDialogFooter, TestCaseAttachment,
    MsTestCaseStepRichText,
    MsChangeHistory,
    BatchMove,
    'MsVersionHistory': versionHistory.default,
    TestCaseVersionDiff
  },
  data() {
    return {
      // sysList: [],//一级选择框的数据
      path: "/test/case/add",
      selectIds: [],
      projectList: [],
      isPublic: false,
      isXpack: false,
      testCaseTemplate: {},
      options: REVIEW_STATUS,
      statuOptions: API_STATUS,
      comments: [],
      result: {},
      dialogFormVisible: false,
      showFollow: false,
      form: {
        name: '',
        module: 'default-module',
        nodePath: '/未规划用例',
        maintainer: getCurrentUser().id,
        priority: 'P0',
        type: '',
        method: '',
        prerequisite: '',
        testId: '',
        otherTestName: '',
        steps: [{
          num: 1,
          desc: '',
          result: ''
        }],
        stepDesc: '',
        stepResult: '',
        selected: [],
        remark: '',
        tags: [],
        demandId: '',
        demandName: '',
        status: 'Prepare',
        reviewStatus: 'Prepare',
        stepDescription: '',
        expectedResult: '',
        stepModel: 'STEP',
        customNum: '',
        followPeople: '',
      },
      maintainerOptions: [],
      // testOptions: [],
      workspaceId: '',
      rules: {
        name: [
          {required: true, message: this.$t('test_track.case.input_name'), trigger: 'blur'},
          {max: 255, message: this.$t('test_track.length_less_than') + '255', trigger: 'blur'}
        ],
        module: [{required: true, message: this.$t('test_track.case.input_module'), trigger: 'change'}],
        customNum: [
          {required: true, message: "ID必填", trigger: 'blur'},
          {max: 50, message: this.$t('test_track.length_less_than') + '50', trigger: 'blur'}
        ],
        demandName: [{required: true, message: this.$t('test_track.case.input_demand_name'), trigger: 'change'}],
        maintainer: [{required: true, message: this.$t('test_track.case.input_maintainer'), trigger: 'change'}],
        priority: [{required: true, message: this.$t('test_track.case.input_priority'), trigger: 'change'}],
        method: [{required: true, message: this.$t('test_track.case.input_method'), trigger: 'change'}],
        // prerequisite: [{max: 500, message: this.$t('test_track.length_less_than') + '500', trigger: 'blur'}],
        // remark: [{max: 1000, message: this.$t('test_track.length_less_than') + '1000', trigger: 'blur'}]
      },
      customFieldRules: {},
      customFieldForm: null,
      formLabelWidth: "100px",
      operationType: '',
      isCreateContinue: false,
      isStepTableAlive: true,
      isFormAlive: true,
      methodOptions: [
        {value: 'auto', label: this.$t('test_track.case.auto')},
        {value: 'manual', label: this.$t('test_track.case.manual')}
      ],
      testCase: {},
      showInputTag: true,
      tableType: "",
      stepFilter: new STEP,
      moduleObj: {
        id: 'id',
        label: 'name',
      },
      tabId: getUUID(),
      versionData: [],
      currentProjectId: "" ,
      dialogVisible: false,
      oldData: null,
      newData: null,
      readOnly: true
    };
  },
  props: {
    treeNodes: {
      type: Array
    },
    currentTestCaseInfo: {},
    selectNode: {
      type: Object
    },
    selectCondition: {
      type: Object
    },
    type: String,
    publicEnable: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    projectIds() {
      return getCurrentProjectID();
    },
    moduleOptions() {
      return this.$store.state.testCaseModuleOptions;
    },
    isCustomNum() {
      return this.$store.state.currentProjectIsCustomNum;
    },
  },

  beforeDestroy() {
    this.removeListener();
  },
  mounted() {
    this.getSelectOptions();
    if (this.type === 'edit' || this.type === 'copy') {
      this.open(this.currentTestCaseInfo)
      this.getComments(this.currentTestCaseInfo)
    }
    // Cascader 级联选择器: 点击文本就让它自动点击前面的input就可以触发选择。
    setInterval(function () {
      document.querySelectorAll('.el-cascader-node__label').forEach(el => {
        el.onclick = function () {
          if (this.previousElementSibling) this.previousElementSibling.click();
        };
      });
    }, 1000);
    if (this.selectNode && this.selectNode.data && !this.form.id) {
      this.form.module = this.selectNode.data.id;
      this.form.nodePath = this.selectNode.data.path;
    }
    if ((!this.form.module || this.form.module === "default-module" || this.form.module === "root") && this.treeNodes.length > 0) {
      this.form.module = this.treeNodes[0].id;
      this.form.nodePath = this.treeNodes[0].path;
    }
    if (!(this.$store.state.testCaseMap instanceof Map)) {
      this.$store.state.testCaseMap = new Map();
    }
    this.$store.state.testCaseMap.set(this.form.id, 0);
  },
  created() {
    if (!this.projectList || this.projectList.length === 0) {   //没有项目数据的话请求项目数据
      this.$get("/project/listAll", (response) => {
        this.projectList = response.data;  //获取当前工作空间所拥有的项目,
      })
    }
    this.projectId = this.projectIds;
    let initAddFuc = this.initAddFuc;
    getTestTemplate()
      .then((template) => {
        this.testCaseTemplate = template;
        this.$store.commit('setTestCaseTemplate', this.testCaseTemplate);
        initAddFuc();
      });
    if (this.selectNode && this.selectNode.data && !this.form.id) {
      this.form.module = this.selectNode.data.id;
      this.form.nodePath = this.selectNode.data.path;
    } else {
      this.form.module = this.treeNodes && this.length > 0 ? this.treeNodes[0].id : "";
    }
    if (this.type === 'edit' || this.type === 'copy') {
      this.form.module = this.currentTestCaseInfo.nodeId;
      this.form.nodePath = this.currentTestCaseInfo.nodePath;
    }
    if ((!this.form.module || this.form.module === "default-module" || this.form.module === "root") && this.treeNodes.length > 0) {
      this.form.module = this.treeNodes[0].id;
      this.form.nodePath = this.treeNodes[0].path;
    }
    this.$get('/test/case/follow/' + this.currentTestCaseInfo.id, response => {
      this.form.follows = response.data;
      for (let i = 0; i < response.data.length; i++) {
        if (response.data[i] === this.currentUser().id) {
          this.showFollow = true;
          break;
        }
      }
    }),
      this.result = this.$get('/project_application/get/config/' + this.projectId + "/CASE_PUBLIC", res => {
        let data = res.data;
        if (data && data.casePublic) {
          this.isPublic = true;
        }
      })
    if (hasLicense()) {
      this.isXpack = true;
    } else {
      this.isXpack = false;
    }
    if (hasLicense()) {
      this.getVersionHistory();
    }
  },
  methods: {
    currentUser: () => {
      return getCurrentUser();
    },
    openHis() {
      this.$refs.changeHistory.open(this.form.id, ["测试用例", "測試用例", "Test case", "TRACK_TEST_CASE"]);
    },
    setModule(id, data) {
      this.form.module = id;
      this.form.nodePath = data.path;
    },
    initAddFuc() {
      // this.loadOptions();
      this.addListener(); //  添加 ctrl s 监听
      if (this.selectNode && this.selectNode.data && !this.form.id) {
        this.form.module = this.selectNode.data.id;
        this.form.nodePath = this.selectNode.data.path;
      } else {
        this.form.module = this.treeNodes && this.length > 0 ? this.treeNodes[0].id : "";
      }
      if (this.type === 'edit' || this.type === 'copy') {
        this.form.module = this.currentTestCaseInfo.nodeId;
        this.form.nodePath = this.currentTestCaseInfo.nodePath;
      }
      if ((!this.form.module || this.form.module === "default-module" || this.form.module === "root") && this.treeNodes.length > 0) {
        this.form.module = this.treeNodes[0].id;
        this.form.nodePath = this.treeNodes[0].path;
      }
      if (this.type === 'add') {
        //设置自定义熟悉默认值
        this.customFieldForm = parseCustomField(this.form, this.testCaseTemplate, this.customFieldRules);
        this.form.name = this.testCaseTemplate.caseName;
        this.form.stepDescription = this.testCaseTemplate.stepDescription;
        this.form.expectedResult = this.testCaseTemplate.expectedResult;
        this.form.prerequisite = this.testCaseTemplate.prerequisite;
        this.form.stepModel = this.testCaseTemplate.stepModel;
        if (this.testCaseTemplate.steps) {
          this.form.steps = JSON.parse(this.testCaseTemplate.steps);
        }
      }
    },
    setDefaultValue() {
      if (!this.form.prerequisite) {
        this.form.prerequisite = "";
      }
      if (!this.form.stepDescription) {
        this.form.stepDescription = "";
      }
      if (!this.form.expectedResult) {
        this.form.expectedResult = "";
      }
      if (!this.form.remark) {
        this.form.remark = "";
      }
      this.$store.state.testCaseMap.set(this.form.id, 0);
    },
    handleCommand(e) {
      if (e === "ADD_AND_CREATE") {
        this.$refs['caseFrom'].validate((valid) => {
          if (!valid) {
            this.saveCase();
          } else {
            this.saveCase(function (t) {
              let tab = {};
              tab.name = 'add';
              t.$emit('addTab', tab);
            });
          }
        })
      } else if (e === 'ADD_AND_PUBLIC') {
        this.form.casePublic = true;
        this.saveCase();
      } else {
        this.saveCase();
      }
    },
    openComment() {
      this.$refs.testCaseComment.open()
    },
    getComments(testCase) {
      let id = '';
      if (testCase) {
        id = testCase.id;
      } else {
        id = this.form.id;
      }
      this.result = this.$get('/test/case/comment/list/' + id, res => {
        this.comments = res.data;
      })
    },
    showAll() {
      if (!this.customizeVisible) {
        this.operatingElements = this.stepFilter.get("ALL");
        this.selectedTreeNode = undefined;
      }
      //this.reload();
    },
    reload() {
      this.isStepTableAlive = false;
      this.$nextTick(() => {
        this.isStepTableAlive = true;
        this.$store.state.testCaseMap.set(this.form.id, 0);
      });
    },
    reloadForm() {
      this.isFormAlive = false;
      this.$nextTick(() => (this.isFormAlive = true));
    },
    open(testCase) {
      /*
             this.form.selected=[["automation", "3edaaf31-3fa4-4a53-9654-320205c2953a"],["automation", "3aa58bd1-c986-448c-8060-d32713dbd4eb"]]
      */
      this.projectId = this.projectIds;
      let initFuc = this.initEdit;
      getTemplate('field/template/case/get/relate/', this)
        .then((template) => {
          this.testCaseTemplate = template;
          this.$store.commit('setTestCaseTemplate', this.testCaseTemplate);
          initFuc(testCase);
        });
    },
    initEdit(testCase , callback) {
      if (window.history && window.history.pushState) {
        history.pushState(null, null, document.URL);
        window.addEventListener('popstate', this.close);
      }
      this.resetForm();
      listenGoBack(this.close);
      this.operationType = 'add';
      if (testCase) {
        //修改
        this.operationType = 'edit';
        //复制
        if (this.type === 'copy') {
          this.operationType = 'add';
          this.setFormData(testCase);
          this.setTestCaseExtInfo(testCase);
          this.getSelectOptions();
          //设置自定义熟悉默认值
          this.customFieldForm = parseCustomField(this.form, this.testCaseTemplate, this.customFieldRules, buildTestCaseOldFields(this.form));
          this.reload();
        } else {
          this.getTestCase(testCase.id);
        }
      } else {
        if (this.selectNode.data) {
          this.form.module = this.selectNode.data.id;
        } else {
          if (this.moduleOptions.length > 0) {
            this.form.module = this.moduleOptions[0].id;
          }
        }
        let user = JSON.parse(localStorage.getItem(TokenKey));
        this.form.priority = 'P3';
        this.form.type = 'functional';
        this.form.method = 'manual';
        this.form.maintainer = user.id;
        this.form.tags = [];
        this.getSelectOptions();
        this.customFieldForm = parseCustomField(this.form, this.testCaseTemplate, this.customFieldRules);
        this.reload();
      }
      if (callback) {
        callback();
      }
    },
    getTestCase(id) {
      this.showInputTag = false;
      if (!id) {
        id = this.currentTestCaseInfo.id;
      }
      this.result = this.$get('/test/case/get/' + id, response => {
        if (response.data) {
          this.path = "/test/case/edit";
          if (this.currentTestCaseInfo.isCopy) {
            this.path = "/test/case/add";
          }
        } else {
          this.path = "/test/case/add";
        }
        let testCase = response.data;
        this.setFormData(testCase);
        this.setTestCaseExtInfo(testCase);
        this.getSelectOptions();
        this.reload();
        this.$nextTick(() => {
          this.showInputTag = true;
        });
      });
    },
    async setFormData(testCase) {
      try {
        testCase.selected = JSON.parse(testCase.testId);
      } catch (error) {
        testCase.selected = testCase.testId
      }
      let tmp = {};
      Object.assign(tmp, testCase);
      tmp.steps = JSON.parse(testCase.steps);
      if (!tmp.steps || tmp.steps.length < 1) {
        tmp.steps = [{
          num: 1,
          desc: '',
          result: ''
        }];
      }
      tmp.tags = JSON.parse(tmp.tags);
      Object.assign(this.form, tmp);
      if (!this.form.stepModel) {
        this.form.stepModel = "STEP";
      }
      this.casePublic = tmp.casePublic;
      this.form.module = testCase.nodeId;
      //设置自定义熟悉默认值
      this.customFieldForm = parseCustomField(this.form, this.testCaseTemplate, this.customFieldRules, testCase ? buildTestCaseOldFields(this.form) : null);
      this.setDefaultValue();
      // 重新渲染，显示自定义字段的必填校验
      this.reloadForm();
    },
    setTestCaseExtInfo(testCase) {
      this.testCase = {};
      if (testCase) {
        // 复制 不查询评论
        this.testCase = testCase.isCopy ? {} : testCase;
      }
    },
    close() {
      //移除监听，防止监听其他页面
      removeGoBackListener(this.close);
      this.dialogFormVisible = false;
    },
    handleCopyPublic(testCase) {
      this.selectIds.push(this.form.id);
      this.$refs.testBatchMove.open(this.treeNodes, this.selectIds, this.moduleOptions);

    },
    copyPublic(param) {
      param.condition = this.condition;
      this.result = this.$post('/test/case/batch/copy/public', param, () => {
        this.$success(this.$t('commons.save_success'));
        this.$refs.testBatchMove.close();
        this.$emit("refresh", this.form);
      });
    },
    saveCase(callback) {
      let isValidate = true;
      this.$refs['caseFrom'].validate((valid) => {
        if (!valid) {
          isValidate = false;
          return false;
        }
      });
      this.$refs['customFieldForm'].validate((valid) => {
        if (!valid) {
          isValidate = false;
          return false;
        }
      });
      if (isValidate) {
        this._saveCase(callback);
      }
    },
    _saveCase(callback) {
      let param = this.buildParam();
      if (this.validate(param)) {
        let option = this.getOption(param);
        this.result = this.$request(option, (response) => {
          this.$success(this.$t('commons.save_success'));
          this.path = "/test/case/edit";
          // this.operationType = "edit"
          this.$emit("refreshTestCase",);
          this.$store.state.testCaseMap.delete(this.form.id);
          //this.tableType = 'edit';
          this.$emit("refresh", this.form);
          if (this.form.id) {
            this.$emit("caseEdit", param);
          } else {
            param.id = response.data.id;
            this.$emit("caseCreate", param);
            this.close();
          }
          this.form.id = response.data.id;
          this.currentTestCaseInfo.id = response.data.id;
          if (callback) {
            callback(this);
          }
          if (hasLicense()) {
            this.getVersionHistory();
          }
          // 保存用例后刷新附件
        });
      }
    },
    buildParam() {
      let param = {};
      Object.assign(param, this.form);
      param.steps = JSON.stringify(this.form.steps);
      param.nodeId = this.form.module;
      param.nodePath = getNodePath(this.form.module, this.moduleOptions);
      if (this.projectId) {
        param.projectId = this.projectId;
      }
      param.name = param.name.trim();

      if (this.form.tags instanceof Array) {
        this.form.tags = JSON.stringify(this.form.tags);
      }
      param.testId = JSON.stringify(this.form.selected);
      param.tags = this.form.tags;
      param.type = 'functional';
      buildCustomFields(this.form, param, this.testCaseTemplate);
      this.parseOldFields(param);
      return param;
    },
    parseOldFields(param) {
      let customFieldsStr = param.customFields;
      if (customFieldsStr) {
        let customFields = JSON.parse(customFieldsStr);
        customFields.forEach(item => {
          if (item.name === '用例等级') {
            param.priority = item.value;
          }
          if (item.name === '责任人') {
            param.maintainer = item.value;
          }
          if (item.name === '用例状态') {
            param.status = item.value;
          }
        });
      }
    },
    getOption(param) {
      let formData = new FormData();
      if (this.$refs.otherInfo && this.$refs.otherInfo.uploadList) {
        this.$refs.otherInfo.uploadList.forEach(f => {
          formData.append("file", f);
        });
      }

      if (this.$refs.otherInfo && this.$refs.otherInfo.fileList) {
        if (param.isCopy) {
          // 如果是copy，则把文件的ID传到后台进行文件复制
          param.fileIds = this.$refs.otherInfo.fileList.map(f => f.id);
        }
        param.updatedFileList = this.$refs.otherInfo.fileList;
      } else {
        param.fileIds = [];
        param.updatedFileList = [];
      }

      let requestJson = JSON.stringify(param, function (key, value) {
        return key === "file" ? undefined : value
      });

      formData.append('request', new Blob([requestJson], {
        type: "application/json"
      }));
      return {
        method: 'POST',
        url: this.path,
        data: formData,
        headers: {
          'Content-Type': undefined
        }
      };
    },
    validate(param) {
      for (let i = 0; i < param.steps.length; i++) {
        if ((param.steps[i].desc && param.steps[i].desc.length > 300) ||
          (param.steps[i].result && param.steps[i].result.length > 300)) {
          this.$warning(this.$t('test_track.case.step_desc') + ","
            + this.$t('test_track.case.expected_results') + this.$t('test_track.length_less_than') + '300');
          return false;
        }
      }
      if (param.name === '') {
        this.$warning(this.$t('test_track.case.input_name'));
        return false;
      }
      return true;
    },
    typeChange() {
      this.form.testId = '';
    },
    getMaintainerOptions() {
      this.$get('/user/project/member/list', response => {
        this.maintainerOptions = response.data;
      });
    },
    getSelectOptions() {
      this.getMaintainerOptions();
    },
    resetForm() {
      //防止点击修改后，点击新建触发校验
      if (this.$refs['caseFrom']) {
        this.$refs['caseFrom'].validate((valid) => {
          this.$refs['caseFrom'].resetFields();
          this._resetForm();
          return true;
        });
      } else {
        this._resetForm();
      }
    },
    _resetForm() {
      this.form.name = '';
      this.form.module = '';
      this.form.type = '';
      this.form.method = '';
      this.form.maintainer = '';
      this.form.priority = '';
      this.form.prerequisite = '';
      this.form.remark = '';
      this.form.testId = '';
      this.form.testName = '';
      this.form.steps = [{
        num: 1,
        desc: '',
        result: ''
      }];
      this.form.customNum = '';
    },
    addListener() {
      document.addEventListener("keydown", this.createCtrlSHandle);
    },
    removeListener() {
      document.removeEventListener("keydown", this.createCtrlSHandle);
    },
    createCtrlSHandle(event) {
      let curTabId = this.$store.state.curTabId;
      if (curTabId === this.tabId)
        handleCtrlSEvent(event, this.saveCase);
    },
    saveFollow() {
      if (this.showFollow) {
        this.showFollow = false;
        for (let i = 0; i < this.form.follows.length; i++) {
          if (this.form.follows[i] === this.currentUser().id) {
            this.form.follows.splice(i, 1)
            break;
          }
        }
        if (this.path === "/test/case/edit") {
          this.result.loading = true
          this.$post('/test/case/edit/follows/' + this.form.id, this.form.follows, () => {
            this.result.loading = false
            this.$success(this.$t('commons.cancel_follow_success'));
          });
        }

      } else {
        this.showFollow = true;
        if (!this.form.follows) {
          this.form.follows = [];
        }
        this.form.follows.push(this.currentUser().id)

        if (this.path === "/test/case/edit") {
          this.result.loading = true
          this.$post('/test/case/edit/follows/' + this.form.id, this.form.follows, () => {
            this.result.loading = false
            this.$success(this.$t('commons.follow_success'));
          });
        }
      }
    },
    getVersionHistory() {
      this.$get('/test/case/versions/' + this.currentTestCaseInfo.id, response => {
        for (let i = 0; i < response.data.length ; i++) {
          this.currentProjectId = response.data[i].projectId
        }
        this.versionData = response.data;
        this.$refs.versionHistory.loading = false;
      });
    },
    setSpecialPropForCompare: function (that) {
      that.newData.tags = JSON.parse(that.newData.tags || "");
      that.newData.steps = JSON.parse(that.newData.steps || "");
      that.oldData.tags = JSON.parse(that.oldData.tags || "");
      that.oldData.steps = JSON.parse(that.oldData.steps || "");
      that.newData.readOnly = true;
      that.oldData.readOnly = true;
    },
    compare(row) {
      this.$get('/test/case/get/' + row.id + "/" + this.currentTestCaseInfo.refId, response => {
        let p1 = this.$get('/test/case/get/' + response.data.id);
        let p2 = this.$get('/test/case/get/' + this.currentTestCaseInfo.id);
        let that = this;
        Promise.all([p1, p2]).then(data => {
          if (data[0] && data[1]) {
            that.newData = data[0].data.data;
            that.oldData = data[1].data.data;
            let testCase = this.versionData.filter(v => v.versionId === this.currentTestCaseInfo.versionId)[0];
            that.newData.versionName = response.data.versionName
            that.oldData.versionName = testCase.versionName
            that.newData.userName = response.data.createName
            that.oldData.userName = testCase.createName
            this.setSpecialPropForCompare(that);
            that.dialogVisible = true;
          }
        });
      });
    },
    checkout(row) {
      this.$refs.versionHistory.loading = true;
      let testCase = this.versionData.filter(v => v.versionId === row.id)[0];
      if (testCase) {
        this.$get('test/case/get/' + testCase.id, response => {
          let testCase = response.data;
          this.$emit("checkout", testCase);
          this.$refs.versionHistory.loading = false;
        });
      }
    },
    create(row) {
      // 创建新版本
      this.form.versionId = row.id;
      this.saveCase();
    },
    del(row) {
      let that = this;
      this.$alert(this.$t('api_test.definition.request.delete_confirm') + ' ' + row.name + " ？", '', {
        confirmButtonText: this.$t('commons.confirm'),
        callback: (action) => {
          if (action === 'confirm') {
            this.$get('/test/case/delete/' + row.id + '/' + this.form.refId, () => {
              this.$success(this.$t('commons.delete_success'));
              this.getVersionHistory();
            });
          } else {
            that.$refs.versionHistory.loading = false;
          }
        }
      });
    },
    changeType(type) {
      this.type = type;
    }
  }
}
</script>

<style scoped>

.el-switch {
  margin-bottom: 10px;
}

.case-name {
  width: 194px;
}

.container {
  height: 100vh;
}

.case-form {
  height: 95%;
  overflow: auto;
}

.case-dialog >>> .el-dialog__body {
  padding: 0 20px 10px 20px;
}

.container >>> .el-card__body {
  height: calc(100vh - 120px);
}

.comment-card >>> .el-card__header {
  padding: 27px 20px;
}

.comment-card >>> .el-card__body {
  height: calc(100vh - 120px);
}

.head-right {
  text-align: right;
}

.ms-main-div {
  background-color: white;
}

.ms-opt-btn {
  position: fixed;
  right: 50px;
  z-index: 9;
}

.ms-case-input {
  width: 100%;
}

.ms-case {
  width: 100%;
}

/deep/ .el-button-group > .el-button:first-child {
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
  height: 32px;
  width: 56px;
}

.other-info-tabs {
  padding-left: 60px;
  margin-left: 40px;
}
</style>
