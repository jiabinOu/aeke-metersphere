<template>
  <div v-loading="result.loading">
    <env-popover :env-map="projectEnvMap"
                 :project-ids="projectIds"
                 @setProjectEnvMap="setProjectEnvMap"
                 :environment-type.sync="environmentType"
                 :group-id="envGroupId"
                 :is-scenario="false"
                 @setEnvGroup="setEnvGroup"
                 :show-config-button-with-out-permission="showConfigButtonWithOutPermission"
                 :project-list="projectList"
                 ref="envPopover" class="env-popover"/>


    <el-input :placeholder="$t('api_test.definition.request.select_case')" @blur="filterSearch"
              @keyup.enter.native="filterSearch" class="search-input" size="small" v-model="condition.name"/>
    <ms-table-adv-search-bar :condition.sync="condition" class="adv-search-bar"
                             v-if="condition.components !== undefined && condition.components.length > 0"
                             @search="filterSearch"/>
    <version-select v-xpack :project-id="projectId" @changeVersion="changeVersion" style="float: left;"
                    class="search-input"/>

    <ms-table ref="scenarioTable"
              v-loading="result.loading"
              :data="tableData"
              :condition="condition"
              :page-size="pageSize"
              :total="total"
              :remember-order="true"
              row-key="id"
              :row-order-group-id="projectId"
              @order="search"
              @filter="filterSearch"
              :disable-header-config="true"
              :show-select-all="true"
              @selectCountChange="selectCountChange">

      <el-table-column v-if="!customNum" prop="num" label="ID" sortable="custom"
                       show-overflow-tooltip>
      </el-table-column>
      <el-table-column v-if="customNum" prop="customNum" label="ID" sortable="custom"
                       show-overflow-tooltip>
      </el-table-column>
      <el-table-column prop="name" :label="$t('api_test.automation.scenario_name')" sortable="custom" min-width="100px"
                       show-overflow-tooltip/>
      <el-table-column
        v-if="versionEnable"
        column-key="version_id"
        :filters="versionFilters"
        :label="$t('commons.version')"
        min-width="100px">
        <template v-slot:default="scope">
          <span>{{ scope.row.versionName }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="level" :label="$t('api_test.automation.case_level')" sortable="custom" min-width="100px"
                       show-overflow-tooltip>
        <template v-slot:default="scope">
          <priority-table-item :value="scope.row.level" ref="level"/>
        </template>

      </el-table-column>
      <el-table-column prop="tagNames" :label="$t('api_test.automation.tag')" min-width="100">
        <template v-slot:default="scope">
          <ms-tag v-for="itemName in scope.row.tags" :key="itemName" type="success" effect="plain" :content="itemName"
                  style="margin-left: 0px; margin-right: 2px"/>
        </template>
      </el-table-column>
      <el-table-column prop="userId" :label="$t('api_test.automation.creator')" show-overflow-tooltip sortable="custom" min-width="100px"/>
      <el-table-column prop="updateTime" :label="$t('api_test.automation.update_time')" width="180" sortable="custom">
        <template v-slot:default="scope">
          <span>{{ scope.row.updateTime | timestampFormatDate }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="stepTotal" :label="$t('api_test.automation.step')" show-overflow-tooltip/>
      <el-table-column prop="lastResult" :label="$t('api_test.automation.last_result')" sortable="custom">
        <template v-slot:default="{row}">
          <el-link type="success" @click="showReport(row)" v-if="row.lastResult === 'Success'">
            {{ $t('api_test.automation.success') }}
          </el-link>
          <el-link type="danger" @click="showReport(row)" v-if="row.lastResult === 'Fail'">
            {{ $t('api_test.automation.fail') }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="passRate" :label="$t('api_test.automation.passing_rate')"
                       show-overflow-tooltip/>
    </ms-table>
    <ms-table-pagination :change="search" :current-page.sync="currentPage" :page-size.sync="pageSize"
                         :total="total"/>
  </div>
</template>

<script>
import MsTableHeader from "@/business/components/common/components/MsTableHeader";
import MsTablePagination from "@/business/components/common/pagination/TablePagination";
import ShowMoreBtn from "@/business/components/track/case/components/ShowMoreBtn";
import MsTag from "../../../../../common/components/MsTag";
import MsApiReportDetail from "../../../../../api/automation/report/ApiReportDetail";
import MsTableMoreBtn from "../../../../../api/automation/scenario/TableMoreBtn";
import MsTestPlanList from "../../../../../api/automation/scenario/testplan/TestPlanList";
import TestPlanScenarioListHeader from "./TestPlanScenarioListHeader";
import EnvPopover from "@/business/components/api/automation/scenario/EnvPopover";
import PriorityTableItem from "@/business/components/track/common/tableItems/planview/PriorityTableItem";
import MsTableAdvSearchBar from "@/business/components/common/components/search/MsTableAdvSearchBar";
import {
  TEST_PLAN_RELEVANCE_API_SCENARIO_CONFIGS
} from "@/business/components/common/components/search/search-components";
import {ENV_TYPE} from "@/common/js/constants";
import MsTable from "@/business/components/common/components/table/MsTable";
import {getVersionFilters} from "@/network/project";

const requireComponent = require.context('@/business/components/xpack/', true, /\.vue$/);
const VersionSelect = requireComponent.keys().length > 0 ? requireComponent("./version/VersionSelect.vue") : {};

export default {
  name: "RelevanceScenarioList",
  components: {
    MsTable,
    PriorityTableItem,
    EnvPopover,
    TestPlanScenarioListHeader,
    MsTablePagination,
    MsTableMoreBtn,
    ShowMoreBtn,
    MsTableHeader,
    MsTag,
    MsApiReportDetail,
    MsTestPlanList,
    MsTableAdvSearchBar,
    'VersionSelect': VersionSelect.default,
  },
  props: {
    referenced: {
      type: Boolean,
      default: false,
    },
    selectNodeIds: Array,
    projectId: String,
    planId: String,
    versionEnable: Boolean,
  },
  data() {
    return {
      result: {},
      showConfigButtonWithOutPermission: false,
      condition: {
        components: TEST_PLAN_RELEVANCE_API_SCENARIO_CONFIGS
      },
      currentScenario: {},
      schedule: {},
      selectAll: false,
      tableData: [],
      currentPage: 1,
      pageSize: 10,
      total: 0,
      reportId: "",
      infoDb: false,
      selectRows: new Set(),
      projectEnvMap: new Map(),
      projectList: [],
      projectIds: new Set(),
      map: new Map(),
      customNum: false,
      environmentType: ENV_TYPE.JSON,
      envGroupId: "",
      versionFilters: [],
    };
  },
  computed: {
    ENV_TYPE() {
      return ENV_TYPE;
    }
  },
  watch: {
    selectNodeIds() {
      this.search();
    },
    projectId() {
      this.condition.versionId = null;
      this.search();
      this.getVersionOptions();
    },
  },
  created() {
    this.getWsProjects();
    this.getVersionOptions();
  },
  methods: {
    filterSearch() {
      this.currentPage = 1;
      this.search();
    },
    search() {
      this.projectEnvMap.clear();
      this.projectIds.clear();
      if (!this.projectId) {
        return;
      }
      this.getProject(this.projectId);
      this.selectRows = new Set();
      this.loading = true;
      if (this.condition.filters) {
        this.condition.filters.status = ["Prepare", "Underway", "Completed"];
      } else {
        this.condition.filters = {status: ["Prepare", "Underway", "Completed"]};
      }

      this.condition.moduleIds = this.selectNodeIds;

      if (this.projectId != null) {
        this.condition.projectId = this.projectId;
      }

      if (this.planId != null) {
        this.condition.planId = this.planId;
      }
      this.condition.stepTotal = "testPlan";

      let url = "/test/plan/scenario/case/relevance/list/" + this.currentPage + "/" + this.pageSize;
      this.result = this.$post(url, this.condition, response => {
        let data = response.data;
        this.total = data.itemCount;
        this.tableData = data.listObject;
        this.tableData.forEach(item => {
          if (item.tags && item.tags.length > 0) {
            item.tags = JSON.parse(item.tags);
          }
        });
        this.clear();
      });
    },
    clear() {
      this.selectRows.clear();
    },
    setProjectEnvMap(projectEnvMap) {
      this.projectEnvMap = projectEnvMap;
    },
    setEnvGroup(id) {
      this.envGroupId = id;
    },
    getWsProjects() {
      this.$get("/project/getOwnerProjects", res => {
        this.projectList = res.data;
      });
    },
    getProject(projectId) {
      if (projectId) {
        this.$get('/project_application/get/config/' + projectId + "/SCENARIO_CUSTOM_NUM", result => {
          let data = result.data;
          if (data) {
            this.customNum = data.scenarioCustomNum;
          }
        });
      }
    },
    initProjectIds() {
      this.projectIds.clear();
      this.map.clear();
      this.selectRows.forEach(row => {
        this.$get('/api/automation/getApiScenarioProjectId/' + row.id, res => {
          let data = res.data;
          data.projectIds.forEach(d => this.projectIds.add(d));
          this.map.set(row.id, data.projectIds);
        });
      });
    },
    checkEnv() {
      return this.$refs.envPopover.checkEnv();
    },
    changeVersion(currentVersion) {
      this.condition.versionId = currentVersion || null;
      this.search();
    },
    getVersionOptions() {
      getVersionFilters(this.projectId, (data) => {
        this.versionFilters = data;
      });
    },
    filter(field) {
      this.condition.filters = field || null;
      this.search();
    },
    selectCountChange(data) {
      this.selectRows = this.$refs.scenarioTable.selectRows;
      this.initProjectIds();
      this.$emit("selectCountChange", data);
    }
  }
};
</script>

<style scoped>
/deep/ .el-drawer__header {
  margin-bottom: 0px;
}

.env-popover {
  float: right;
  margin-top: 10px;
}

.search-input {
  float: right;
  width: 250px;
  margin-top: 10px;
  margin-bottom: 20px;
  margin-right: 20px;
}

.adv-search-bar {
  float: right;
  margin-top: 15px;
  margin-right: 10px;
}
</style>
