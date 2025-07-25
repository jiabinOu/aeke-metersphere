<template>
  <div class="card-container">
    <ms-table-header :condition.sync="condition" @search="initTableData" ref="tableHeader"
                     :show-create="false" :tip="$t('commons.search_by_id_name_tag')">

      <!-- 不显示 “全部用例” 标题,使标题为空 -->
      <template v-slot:title>
        <span></span>
      </template>

      <template v-slot:button>
        <ms-table-button v-permission="['PROJECT_TRACK_CASE:READ']" v-if="!showMyTestCase" icon="el-icon-s-custom"
                         :content="$t('test_track.plan_view.my_case')" @click="searchMyTestCase"/>
        <ms-table-button v-permission="['PROJECT_TRACK_CASE:READ']" v-if="showMyTestCase" icon="el-icon-files"
                         :content="$t('test_track.plan_view.all_case')" @click="searchMyTestCase"/>
        <ms-table-button v-permission="['PROJECT_TRACK_PLAN:READ+RELEVANCE_OR_CANCEL']" icon="el-icon-connection"
                         :disabled="planStatus==='Archived'"
                         :content="$t('test_track.plan_view.relevance_test_case')"
                         @click="$emit('openTestCaseRelevanceDialog')"/>
      </template>
    </ms-table-header>

    <ms-table
      v-loading="result.loading"
      :field-key="tableHeaderKey"
      :data="tableData"
      :condition="condition"
      :total="total"
      :operators="operators"
      :page-size.sync="pageSize"
      :screen-height="screenHeight"
      :batch-operators="buttons"
      :fields.sync="fields"
      :remember-order="true"
      :row-order-group-id="planId"
      :row-order-func="editTestPlanTestCaseOrder"
      :enable-order-drag="enableOrderDrag"
      @refresh="initTableData"
      @handlePageChange="initTableData"
      @handleRowClick="handleEdit"
      row-key="id"
      ref="table">

      <span v-for="item in fields" :key="item.key">
        <ms-table-column
          v-if="item.id == 'num'"
          prop="customNum"
          sortable="custom"
          :fields-width="fieldsWidth"
          :label="$t('commons.id')"
          min-width="120px"/>

        <ms-table-column
          prop="name"
          sortable="custom"
          :field="item"
          :fields-width="fieldsWidth"
          :label="$t('commons.name')"
          min-width="120px"/>

        <ms-table-column
          v-if="versionEnable"
          prop="versionId"
          :field="item"
          :filters="versionFilters"
          :fields-width="fieldsWidth"
          :label="$t('commons.version')"
          min-width="120px">
           <template v-slot:default="scope">
            <span>{{ scope.row.versionName }}</span>
          </template>
        </ms-table-column>

        <ms-table-column
          prop="priority"
          :field="item"
          :fields-width="fieldsWidth"
          :filters="priorityFilters"
          sortable="custom"
          min-width="120px"
          :label="$t('test_track.case.priority')">
          <template v-slot:default="scope">
            <priority-table-item :value="scope.row.priority" ref="priority"/>
          </template>
        </ms-table-column>

        <ms-table-column
          prop="tags"
          :field="item"
          :fields-width="fieldsWidth"
          :label="$t('commons.tag')"
          min-width="120px">
          <template v-slot:default="scope">
            <ms-tag v-for="(tag, index) in scope.row.showTags" :key="tag + '_' + index" type="success" effect="plain"
                    :content="tag" style="margin-left: 0px; margin-right: 2px"/>
            <span/>
          </template>
        </ms-table-column>
          <ms-table-column
            sortable
            prop="createTime"
            :field="item"
            :fields-width="fieldsWidth"
            :label="$t('commons.create_time')"
            min-width="140px">
          <template v-slot:default="scope">
            <span>{{ scope.row.createTime | timestampFormatDate }}</span>
          </template>
        </ms-table-column>

        <ms-table-column
          prop="nodePath"
          :field="item"
          :fields-width="fieldsWidth"
          :label="$t('test_track.case.module')"
          min-width="120px"/>

        <ms-table-column
          prop="projectName"
          :field="item"
          :fields-width="fieldsWidth"
          :label="$t('test_track.plan.plan_project')"
          min-width="120px"/>

        <ms-table-column
          prop="issuesContent"
          :field="item"
          :fields-width="fieldsWidth"
          :label="$t('test_track.issue.issue')"
          min-width="80px">
          <template v-slot:default="scope">
            <el-popover
              placement="right"
              width="400"
              trigger="hover">
              <test-plan-case-issue-item
                v-if="scope.row.issuesSize && scope.row.issuesSize > 0"
                :data="scope.row"/>
              <el-button
                slot="reference"
                type="text">
                <span @mouseover="loadIssue(scope.row)">
                  {{ scope.row.issuesSize }}
                </span>
              </el-button>
            </el-popover>
          </template>
        </ms-table-column>

        <ms-table-column
          prop="executor"
          :filters="executorFilters"
          min-width="100px"
          :field="item"
          :fields-width="fieldsWidth"
          :label="$t('test_track.plan_view.executor')">
          <template v-slot:default="scope">
            {{ scope.row.executorName }}
          </template>
        </ms-table-column>

        <!-- 责任人(创建该用例时所关联的责任人) -->
        <ms-table-column
          prop="maintainerName"
          :filters="maintainerFilters"
          min-width="100px"
          :field="item"
          :fields-width="fieldsWidth"
          :label="$t('api_test.definition.request.responsible')"/>

        <ms-table-column
          prop="status"
          :filters="statusFilters"
          min-width="100px"
          :field="item"
          :fields-width="fieldsWidth"
          :label="$t('test_track.plan_view.execute_result')">
          <template v-slot:default="scope">
            <span @click.stop="clickt = 'stop'">
              <el-dropdown class="test-case-status" @command="statusChange">
                <span class="el-dropdown-link">
                  <status-table-item :value="scope.row.status"/>
                </span>
                <el-dropdown-menu slot="dropdown" chang>
                  <el-dropdown-item :disabled="!hasEditPermission" :command="{id: scope.row.id, status: 'Pass'}">
                    {{ $t('test_track.plan_view.pass') }}
                  </el-dropdown-item>
                  <el-dropdown-item :disabled="!hasEditPermission"
                                    :command="{id: scope.row.id, status: 'Failure'}">
                    {{ $t('test_track.plan_view.failure') }}
                  </el-dropdown-item>
                  <el-dropdown-item :disabled="!hasEditPermission"
                                    :command="{id: scope.row.id, status: 'Blocking'}">
                    {{ $t('test_track.plan_view.blocking') }}
                  </el-dropdown-item>
                  <el-dropdown-item :disabled="!hasEditPermission" :command="{id: scope.row.id, status: 'Skip'}">
                    {{ $t('test_track.plan_view.skip') }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </el-dropdown>
            </span>
          </template>
        </ms-table-column>

        <ms-table-column
          sortable
          prop="updateTime"
          :field="item"
          :fields-width="fieldsWidth"
          :label="$t('commons.update_time')"
          min-width="140px">
          <template v-slot:default="scope">
            <span>{{ scope.row.updateTime | timestampFormatDate }}</span>
          </template>
        </ms-table-column>

        <ms-table-column v-for="field in testCaseTemplate.customFields" :key="field.id"
                         :filters="field.name === '用例等级' ? priorityFilters : null"
                         :field="item"
                         column-key="priority"
                         :fields-width="fieldsWidth"
                         :label="field.system ? $t(systemFiledMap[field.name]) :field.name"
                         :min-width="120"
                         :prop="field.name">
          <template v-slot="scope">
              <span v-if="field.name === '用例等级'">
                  <priority-table-item
                    :value="getCustomFieldValue(scope.row, field) ? getCustomFieldValue(scope.row, field) : scope.row.priority"/>
              </span>
            <span v-else>
                {{ getCustomFieldValue(scope.row, field) }}
              </span>
          </template>
        </ms-table-column>

      </span>
    </ms-table>

    <ms-table-pagination :change="search" :current-page.sync="currentPage" :page-size.sync="pageSize"
                         :total="total"/>

    <functional-test-case-edit
      ref="testPlanTestCaseEdit"
      :search-param.sync="condition"
      :page-num="currentPage"
      :page-size="pageSize"
      :next-page-data="nextPageData"
      :pre-page-data="prePageData"
      @nextPage="nextPage"
      @prePage="prePage"
      @refresh="initTableData"
      :test-cases="tableData"
      :is-read-only="isReadOnly"
      :total="total"
      :fold="false"
      @refreshTable="search"/>

    <batch-edit ref="batchEdit" @batchEdit="batchEdit"
                :type-arr="typeArr" :value-arr="valueArr" :dialog-title="$t('test_track.case.batch_edit_case')"/>
  </div>
</template>

<script>
import ExecutorEdit from '../ExecutorEdit';
import StatusEdit from '../StatusEdit';
import FunctionalTestCaseEdit from "./FunctionalTestCaseEdit";
import MsTipButton from '../../../../../common/components/MsTipButton';
import MsTablePagination from '../../../../../common/pagination/TablePagination';
import MsTableHeader from '../../../../../common/components/MsTableHeader';
import MsTableButton from '../../../../../common/components/MsTableButton';
import NodeBreadcrumb from '../../../../common/NodeBreadcrumb';

import {TEST_PLAN_FUNCTION_TEST_CASE, TokenKey,} from "@/common/js/constants";
import {getCurrentProjectID, hasLicense, hasPermission} from "@/common/js/utils";
import PriorityTableItem from "../../../../common/tableItems/planview/PriorityTableItem";
import StatusTableItem from "../../../../common/tableItems/planview/StatusTableItem";
import TypeTableItem from "../../../../common/tableItems/planview/TypeTableItem";
import MethodTableItem from "../../../../common/tableItems/planview/MethodTableItem";
import MsTableOperator from "../../../../../common/components/MsTableOperator";
import MsTableOperatorButton from "../../../../../common/components/MsTableOperatorButton";
import {TEST_PLAN_TEST_CASE_CONFIGS} from "../../../../../common/components/search/search-components";
import BatchEdit from "../../../../case/components/BatchEdit";
import MsTag from "@/business/components/common/components/MsTag";
import {
  buildBatchParam,
  getCustomFieldValue,
  getCustomTableHeader,
  getCustomTableWidth,
  getLastTableSortField,
  getTableHeaderWithCustomFields,
  initCondition,
} from "@/common/js/tableUtils";
import MsTable from "@/business/components/common/components/table/MsTable";
import MsTableColumn from "@/business/components/common/components/table/MsTableColumn";
import {getProjectMember} from "@/network/user";
import {getTestTemplate} from "@/network/custom-field-template";
import {editTestPlanTestCaseOrder} from "@/network/test-plan";
import {SYSTEM_FIELD_NAME_MAP} from "@/common/js/table-constants";
import {getTestPlanTestCase} from "@/network/testCase";
import TestPlanCaseIssueItem from "@/business/components/track/plan/view/comonents/functional/TestPlanCaseIssueItem";

export default {
  name: "FunctionalTestCaseList",
  components: {
    TestPlanCaseIssueItem,
    MsTableColumn,
    MsTable,
    FunctionalTestCaseEdit,
    MsTableOperatorButton,
    MsTableOperator,
    MethodTableItem,
    TypeTableItem,
    StatusTableItem,
    PriorityTableItem, StatusEdit, ExecutorEdit, MsTipButton, MsTablePagination,
    MsTableHeader, NodeBreadcrumb, MsTableButton,
    BatchEdit, MsTag
  },
  data() {
    return {
      // updata: false,
      type: TEST_PLAN_FUNCTION_TEST_CASE,
      fields: getCustomTableHeader('TEST_PLAN_FUNCTION_TEST_CASE'),
      fieldsWidth: getCustomTableWidth('TEST_PLAN_FUNCTION_TEST_CASE'),
      screenHeight: 'calc(100vh - 275px)',
      tableLabel: [],
      result: {},
      deletePath: "/test/case/delete",
      condition: {
        components: TEST_PLAN_TEST_CASE_CONFIGS
      },
      nextPageData: null,
      prePageData: null,
      enableOrderDrag: true,
      showMyTestCase: false,
      tableData: [],
      currentPage: 1,
      pageSize: 10,
      total: 0,
      pageCount: 0,
      status: 'default',
      testPlan: {},
      isReadOnly: false,
      hasEditPermission: false,
      tableHeaderKey: 'TEST_PLAN_FUNCTION_TEST_CASE',
      priorityFilters: [
        {text: 'P0', value: 'P0'},
        {text: 'P1', value: 'P1'},
        {text: 'P2', value: 'P2'},
        {text: 'P3', value: 'P3'}
      ],
      methodFilters: [
        {text: this.$t('test_track.case.manual'), value: 'manual'},
        {text: this.$t('test_track.case.auto'), value: 'auto'}
      ],
      statusFilters: [
        {text: this.$t('test_track.plan.plan_status_prepare'), value: 'Prepare'},
        {text: this.$t('test_track.plan_view.pass'), value: 'Pass'},
        {text: this.$t('test_track.plan_view.failure'), value: 'Failure'},
        {text: this.$t('test_track.plan_view.blocking'), value: 'Blocking'},
        {text: this.$t('test_track.plan_view.skip'), value: 'Skip'},
        {text: this.$t('test_track.plan.plan_status_running'), value: 'Underway'},
      ],
      executorFilters: [],
      maintainerFilters: [],
      showMore: false,
      typeArr: [
        {id: 'status', name: this.$t('test_track.plan_view.execute_result')},
        {id: 'executor', name: this.$t('test_track.plan_view.executor')},
      ],
      valueArr: {
        executor: [],
        status: [
          {name: this.$t('test_track.plan_view.pass'), id: 'Pass'},
          {name: this.$t('test_track.plan_view.failure'), id: 'Failure'},
          {name: this.$t('test_track.plan_view.blocking'), id: 'Blocking'},
          {name: this.$t('test_track.plan_view.skip'), id: 'Skip'}
        ]
      },
      selectDataRange: "all",
      testCaseTemplate: {},
      versionFilters: []
    };
  },
  props: {
    planId: {
      type: String
    },
    clickType: String,
    selectNodeIds: {
      type: Array
    },
    versionEnable: {
      type: Boolean,
      default: false
    },
    planStatus: {
      type: String
    },
  },
  computed: {
    editTestPlanTestCaseOrder() {
      return editTestPlanTestCaseOrder;
    },
    systemFiledMap() {
      return SYSTEM_FIELD_NAME_MAP;
    },
    operators() {
      if (this.planStatus === 'Archived') {
        return [
          {
            tip: this.$t('commons.edit'), icon: "el-icon-edit",
            exec: this.handleEdit,
            isDisable: true,
            permissions: ['PROJECT_TRACK_PLAN:READ+RUN']
          },
          {
            tip: this.$t('test_track.plan_view.cancel_relevance'), icon: "el-icon-unlock", type: "danger",
            exec: this.handleDelete,
            isDisable: true,
            permissions: ['PROJECT_TRACK_PLAN:READ+RELEVANCE_OR_CANCEL']
          }
        ]
      } else {
        return [
          {
            tip: this.$t('commons.edit'), icon: "el-icon-edit",
            exec: this.handleEdit,
            permissions: ['PROJECT_TRACK_PLAN:READ+RUN']
          },
          {
            tip: this.$t('test_track.plan_view.cancel_relevance'), icon: "el-icon-unlock", type: "danger",
            exec: this.handleDelete,
            permissions: ['PROJECT_TRACK_PLAN:READ+RELEVANCE_OR_CANCEL']
          }
        ]
      }
    },
    buttons() {
      if (this.planStatus === 'Archived') {
        return [
          {
            name: this.$t('test_track.case.batch_edit_case'), handleClick: this.handleBatchEdit,
            isDisable: true,
            permissions: ['PROJECT_TRACK_PLAN:READ+CASE_BATCH_EDIT']
          },
          {
            name: this.$t('test_track.case.batch_unlink'), handleClick: this.handleDeleteBatch,
            isDisable: true,
            permissions: ['PROJECT_TRACK_PLAN:READ+CASE_BATCH_DELETE']
          }
        ]

      } else {
        return [
          {
            name: this.$t('test_track.case.batch_edit_case'), handleClick: this.handleBatchEdit,
            permissions: ['PROJECT_TRACK_PLAN:READ+CASE_BATCH_EDIT']
          },
          {
            name: this.$t('test_track.case.batch_unlink'), handleClick: this.handleDeleteBatch,
            permissions: ['PROJECT_TRACK_PLAN:READ+CASE_BATCH_DELETE']
          }
        ]
      }
    },
  },
  watch: {
    planId() {
      this.refreshTableAndPlan();
    },
    selectNodeIds() {
      this.condition.selectAll = false;
      this.search();
    },
    tableLabel: {
      handler(newVal) {
        this.updata = !this.updata;
      },
      deep: true
    },
    condition() {
      this.$emit('setCondition', this.condition);
    },
    pageCount() {
      this.currentPage = 1;
    }
  },
  created() {
    this.condition.orders = getLastTableSortField(this.tableHeaderKey);
    this.pageCount = Math.ceil(this.total / this.pageSize);
  },
  mounted() {
    this.$emit('setCondition', this.condition);
    this.$EventBus.$on("openFailureTestCase", this.handleOpenFailureTestCase);
    this.refreshTableAndPlan();
    this.hasEditPermission = hasPermission('PROJECT_TRACK_PLAN:READ+RUN');
    this.getMaintainerOptions();
    this.getTemplateField();
    this.getVersionOptions();
  },
  destroyed() {
    this.$EventBus.$off("openFailureTestCase", this.handleOpenFailureTestCase);
  },
  methods: {
    loadIssue(row) {
      if (row.issuesSize && !row.hasLoadIssue) {
        this.$get("/issues/get/case/PLAN_FUNCTIONAL/" + row.id).then(response => {
          this.$set(row, "issuesContent", response.data.data);
          this.$set(row, "hasLoadIssue", true);
        });
      }
    },
    handleOpenFailureTestCase(row) {
      this.isReadOnly = true;
      this.condition.status = 'Failure';
      this.$refs.testPlanTestCaseEdit.openTestCaseEdit(row, this.tableData);
    },
    nextPage() {
      this.currentPage++;
      this.initTableData(() => {
        this.$refs.testPlanTestCaseEdit.openTestCaseEdit(this.tableData[0], this.tableData);
      });
    },
    prePage() {
      this.currentPage--;
      this.initTableData(() => {
        this.$refs.testPlanTestCaseEdit.openTestCaseEdit(this.tableData[this.tableData.length - 1], this.tableData);
      });
    },
    getTemplateField() {
      this.result.loading = true;
      let p1 = getProjectMember((data) => {
        this.members = data;
      });
      let p2 = getTestTemplate();
      Promise.all([p1, p2]).then((data) => {
        let template = data[1];
        this.testCaseTemplate = template;
        this.fields = getTableHeaderWithCustomFields(this.tableHeaderKey, this.testCaseTemplate.customFields);
        if (this.$refs.table) {
          this.$refs.table.resetHeader();
        }
        this.result.loading = false;
      });
    },
    getCustomFieldValue(row, field) {
      return getCustomFieldValue(row, field, this.members);
    },
    initTableData(callback) {
      initCondition(this.condition, this.condition.selectAll);
      this.enableOrderDrag = this.condition.orders.length > 0 ? false : true;

      this.autoCheckStatus();
      if (this.planId) {
        // param.planId = this.planId;
        this.condition.planId = this.planId;
      }
      if (this.clickType) {
        if (this.status == 'default') {
          this.condition.status = this.clickType;
        } else {
          this.condition.status = null;
        }
        this.status = 'all';
      }
      this.condition.nodeIds = [];
      if (this.selectNodeIds && this.selectNodeIds.length > 0) {
        this.condition.nodeIds = this.selectNodeIds;
      }
      this.condition.projectId = getCurrentProjectID();
      if (this.planId) {
        this.result = getTestPlanTestCase(this.currentPage, this.pageSize, this.condition, (data) => {
          this.total = data.itemCount;
          this.pageCount = Math.ceil(this.total / this.pageSize);
          this.tableData = data.listObject;
          for (let i = 0; i < this.tableData.length; i++) {
            if (this.tableData[i]) {
              if (this.tableData[i].customFields) {
                this.tableData[i].customFields = JSON.parse(this.tableData[i].customFields);
              }
              this.$set(this.tableData[i], "showTags", JSON.parse(this.tableData[i].tags));
              this.$set(this.tableData[i], "issuesSize", this.tableData[i].issuesCount);
              this.$set(this.tableData[i], "hasLoadIssue", false);
              this.$set(this.tableData[i], "issuesContent", []);
            }
          }

          // 需要判断tableData数据，放回调里面
          this.getPreData();

          if (typeof callback === "function") {
            callback();
          }
        });
        this.getNexPageData();
      }
    },
    getNexPageData() {
      getTestPlanTestCase(this.currentPage * this.pageSize + 1, 1, this.condition, (data) => {
        if (data.listObject && data.listObject.length > 0) {
          this.nextPageData = {
            name: data.listObject[0].name
          }
        } else {
          this.nextPageData = null;
        }
      });
    },
    getPreData() {
      // 如果不是第一页并且只有一条数据时，需要调用
      if (this.currentPage > 1 && this.tableData.length === 1) {
        getTestPlanTestCase((this.currentPage - 1) * this.pageSize, 1, this.condition, (data) => {
          if (data.listObject && data.listObject.length > 0) {
            this.prePageData = {
              name: data.listObject[0].name
            }
          } else {
            this.prePageData = null;
          }
        });
      }
    },
    autoCheckStatus() {
      if (!this.planId) {
        return;
      }
      this.$post('/test/plan/autoCheck/' + this.planId, (response) => {
      });
    },
    showDetail(row, event, column) {
      this.isReadOnly = !this.hasEditPermission;
      this.$refs.testPlanTestCaseEdit.openTestCaseEdit(row, this.tableData);
    },
    refresh() {
      this.$refs.table.clear();
      this.search();
      this.$emit('refreshTree');
    },
    refreshTableAndPlan() {
      if (this.$refs.tableHeader) {
        this.$refs.tableHeader.resetSearchData();
      }
      this.getTestPlanById();
      this.initTableData();
    },
    refreshTestPlanRecent() {
      let param = {};
      param.id = this.planId;
      param.updateTime = Date.now();
      this.$post('/test/plan/edit', param);
    },
    search() {
      this.initTableData();
    },
    buildPagePath(path) {
      return path + "/" + this.currentPage + "/" + this.pageSize;
    },
    handleEdit(testCase, index) {
      this.isReadOnly = false;
      this.$refs.testPlanTestCaseEdit.openTestCaseEdit(testCase, this.tableData);
    },
    handleDelete(testCase) {
      this.$alert(this.$t('test_track.plan_view.confirm_cancel_relevance') + ' ' + testCase.name + " ？", '', {
        confirmButtonText: this.$t('commons.confirm'),
        callback: (action) => {
          if (action === 'confirm') {
            this._handleDelete(testCase);
          }
        }
      });
    },
    handleDeleteBatch() {
      if (this.tableData.length < 1) {
        this.$warning(this.$t('test_track.plan_view.no_case_relevance'));
        return;
      }
      this.$alert(this.$t('test_track.plan_view.confirm_cancel_relevance') + " ？", '', {
        confirmButtonText: this.$t('commons.confirm'),
        callback: (action) => {
          if (action === 'confirm') {
            let param = buildBatchParam(this, this.$refs.table.selectIds);
            this.$post('/test/plan/case/batch/delete', param, () => {
              this.refresh();
              this.$success(this.$t('test_track.cancel_relevance_success'));
            });
          }
        }
      });
    },
    _handleDelete(testCase) {
      let testCaseId = testCase.id;
      this.result = this.$post('/test/plan/case/delete/' + testCaseId, {}, () => {
        this.refresh();
        this.$success(this.$t('test_track.cancel_relevance_success'));
      });
    },
    searchMyTestCase() {
      this.showMyTestCase = !this.showMyTestCase;
      if (this.showMyTestCase) {
        let user = JSON.parse(localStorage.getItem(TokenKey));
        this.condition.executor = user.id;
      } else {
        this.condition.executor = null;
      }
      this.initTableData();
    },
    statusChange(param) {
      this.$post('/test/plan/case/edit', param, () => {
        for (let i = 0; i < this.tableData.length; i++) {
          if (this.tableData[i].id == param.id) {
            this.tableData[i].status = param.status;
            break;
          }
        }
        this.updatePlanStatus();
      });
    },
    updatePlanStatus() {
      this.$post('/test/plan/autoCheck/' + this.planId);
    },
    getTestPlanById() {
      if (this.planId) {
        this.$post('/test/plan/get/' + this.planId, {}, response => {
          this.testPlan = response.data;
          this.refreshTestPlanRecent();
        });
      }
    },
    batchEdit(form) {
      let param = buildBatchParam(this, this.$refs.table.selectIds);
      param[form.type] = form.value;
      if (form.type === 'executor') {
        param['modifyExecutor'] = true;
      }
      param.ids = this.$refs.table.selectIds;
      this.$post('/test/plan/case/batch/edit', param, () => {
        this.status = '';
        this.$post('/test/plan/edit/status/' + this.planId);
        this.$success(this.$t('commons.save_success'));
        this.refresh();
      });
    },
    handleBatchEdit() {
      this.getMaintainerOptions();
      this.$refs.batchEdit.open(this.condition.selectAll ? this.total : this.$refs.table.selectRows.size);
    },
    getMaintainerOptions() {
      this.$get('/user/project/member/list', response => {
        this.valueArr.executor = response.data;
        this.executorFilters = response.data.map(u => {
          return {text: u.name, value: u.id};
        });
        this.maintainerFilters = response.data.map(u => {
          return {text: u.id + '(' + u.name + ')', value: u.id};
        });
      });
    },
    getVersionOptions() {
      if (hasLicense()) {
        this.$get('/project/version/get-project-versions/' + getCurrentProjectID(), response => {
          this.versionOptions = response.data;
          this.versionFilters = response.data.map(u => {
            return {text: u.name, value: u.id};
          });
        });
      }
    },
  }
};
</script>

<style scoped>

.search {
  margin-left: 10px;
  width: 240px;
}

.test-case-status, .el-table {
  cursor: pointer;
}

.el-tag {
  margin-left: 10px;
}

.ms-table-header {
  margin-bottom: 10px;
}
</style>
