<template>
  <div v-permission="['PROJECT_API_SCENARIO:READ','WORKSPACE_USER:READ']">
    <el-menu
      v-if="showMenu"
      :unique-opened="true"
      class="header-user-menu align-right header-top-menu"
      mode="horizontal"
      :background-color="color"
      text-color="#fff"
      active-text-color="#fff">
      <el-menu-item onselectstart="return false">
        <el-tooltip effect="light">
          <template v-slot:content>
            <span>{{ $t('commons.task_center') }}</span>
          </template>
          <div @click="showTaskCenter" v-if="runningTotal > 0">
            <el-badge :value="runningTotal" class="item" type="primary">
              <font-awesome-icon class="icon global focusing" :icon="['fas', 'tasks']" style="font-size: 18px"/>
            </el-badge>
          </div>
          <font-awesome-icon @click="showTaskCenter" class="icon global focusing" :icon="['fas', 'tasks']" v-else/>
        </el-tooltip>
      </el-menu-item>
    </el-menu>

    <el-drawer
      :visible.sync="taskVisible"
      :destroy-on-close="true"
      direction="rtl"
      :withHeader="true"
      :modal="false"
      :title="$t('commons.task_center')"
      :size="size.toString()"
      custom-class="ms-drawer-task">
      <el-card style="float: left;width: 850px" :style="{'width': (size - 550)+'px'}" v-if="size > 550 ">
        <div class="ms-task-opt-btn" @click="packUp">{{ $t('commons.task_close') }}</div>
        <!-- 接口用例结果 -->
        <ms-request-result-tail :response="response" ref="debugResult" v-if="executionModule === 'API' && reportType !=='API_INTEGRATED'"/>

        <ms-api-report-detail :showCancelButton="false" :reportId="reportId" v-if="executionModule === 'SCENARIO'|| reportType ==='API_INTEGRATED'"/>

        <performance-report-view :perReportId="reportId" v-if="executionModule === 'PERFORMANCE'"/>
      </el-card>

      <el-card style="width: 550px;float: right" v-loading="loading">
        <div style="color: #2B415C;margin: 0px 20px 0px;">
          <el-form label-width="95px" class="ms-el-form-item">
            <el-row>
              <el-col :span="12">
                <el-form-item :label="$t('test_track.report.list.trigger_mode')" prop="runMode">
                  <el-select size="mini" style="margin-right: 10px" v-model="condition.triggerMode" @change="changeInit"
                             :disabled="isDebugHistory">
                    <el-option v-for="item in runMode" :key="item.id" :value="item.id" :label="item.label"/>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="$t('commons.status')" prop="status">
                  <el-select size="mini" style="margin-right: 10px" v-model="condition.executionStatus" @change="init(true)"
                             :disabled="isDebugHistory">
                    <el-option v-for="item in runStatus" :key="item.id" :value="item.id" :label="item.label"/>
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item :label="$t('commons.executor')" prop="status">
                  <el-select v-model="condition.executor" :placeholder="$t('commons.executor')" filterable size="small"
                             style="margin-right: 10px" @change="init(true)" :disabled="isDebugHistory">
                    <el-option
                      v-for="item in maintainerOptions"
                      :key="item.id"
                      :label="item.id + ' (' + item.name + ')'"
                      :value="item.id">
                    </el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-button size="mini" class="ms-task-stop" @click="stop(null)" :disabled="isDebugHistory">
                  {{ $t('report.stop_btn_all') }}
                </el-button>
              </el-col>
            </el-row>
          </el-form>
        </div>
        <el-divider direction="horizontal" style="width: 100%"/>

        <el-tabs v-model="activeName" @tab-click="init(true)" v-loading="loading">
          <el-tab-pane :key="tab.id" :name="tab.id" :label="tab.label" v-for="tab in tabs" :disabled="isDebugHistory">
            <span slot="label">
              <el-badge class="ms-badge-item" v-if="showBadge(tab.id) > 0" :value="showBadge(tab.id)">
                {{ tab.label }}
              </el-badge>
              <span style="font-size: 13px" v-else>{{ tab.label }}</span>
            </span>
            <task-center-item
              :task-data="taskData"
              :is-debug-history="isDebugHistory"
              :show-type="showType"
              :total="total"
              :maintainer-options="maintainerOptions"
              @packUp="packUp"
              @nextPage="nextPage"
              @showReport="showReport"
            />
          </el-tab-pane>
        </el-tabs>
      </el-card>

    </el-drawer>
  </div>
</template>

<script>
import MsDrawer from "../common/components/MsDrawer";
import {getCurrentProjectID, getCurrentUser, hasPermissions} from "@/common/js/utils";
import TaskCenterItem from "./TaskCenterItem";

export default {
  name: "MsTaskCenter",
  components: {
    MsDrawer,
    TaskCenterItem,
    MsRequestResultTail: () => import("../../components/api/definition/components/response/RequestResultTail"),
    MsApiReportDetail: () => import("../../components/api/automation/report/ApiReportDetail"),
    PerformanceReportView: () => import("../../components/performance/report/PerformanceReportView"),
    MsTablePagination: () => import("./TaskPagination"),
  },
  inject: [
    'reload'
  ],
  data() {
    return {
      runningTotal: 0,
      taskVisible: false,
      result: {},
      loading: false,
      taskData: [],
      response: {},
      initEnd: false,
      visible: false,
      showType: "",
      pageSize: 10,
      currentPage: 1,
      total: 0,
      runMode: [
        {id: 'ALL', label: this.$t('api_test.definition.document.data_set.all')},
        {id: 'BATCH', label: this.$t('api_test.automation.batch_execute')},
        {id: 'SCHEDULE', label: this.$t('commons.trigger_mode.schedule')},
        {id: 'MANUAL', label: this.$t('commons.trigger_mode.manual')},
        {id: 'API', label: 'API'}
      ],
      runStatus: [
        {id: '', label: this.$t('api_test.definition.document.data_set.all')},
        {id: 'starting', label: 'Starting'},
        {id: 'running', label: 'Running'},
        {id: 'reporting', label: 'Reporting'},
        {id: 'completed', label: 'Completed'},
        {id: 'success', label: 'Success'},
        {id: 'waiting', label: 'Waiting'},
        {id: "errorReportResult", label: 'FakeError'},
        {id: 'unexecute', label: 'NotExecute'},
        {id: 'stop', label: 'Stopped'},
        {id: 'error', label: 'Error'},
        {id: 'fail', label: 'Fail'},
      ],
      condition: {triggerMode: "", executionStatus: ""},
      maintainerOptions: [],
      websocket: Object,
      size: 550,
      reportId: "",
      executionModule: "",
      reportType: "",
      isDebugHistory: false,
      runningData: {},
      activeName: "API",
      tabs: [
        {id: 'API', label: this.$t('task.api_title')},
        {id: 'SCENARIO', label: this.$t('task.scenario_title')},
        {id: 'PERF', label: this.$t('task.perf_title')}
      ],
    };
  },
  props: {
    color: String,
    showMenu: {
      type: Boolean,
      default: true
    }
  },
  computed: {
    disabled() {
      return this.loading
    }
  },

  created() {
    if (hasPermissions('PROJECT_API_SCENARIO:READ')) {
      this.condition.executor = getCurrentUser().id;
    }
  },
  watch: {
    taskVisible(v) {
      if (!v) {
        this.close();
      }
    }
  },
  methods: {
    showStatus(status) {
      status = status.toLowerCase();
      switch (status) {
        case "unexecute":
          return "NotExecute";
        case "errorreportresult":
          return "FakeError";
        case "stop":
          return "Stopped";
        default:
          return status.toLowerCase()[0].toUpperCase() + status.toLowerCase().substr(1);
      }
    },
    showClass(status) {
      return "ms-task-" + status;
    },
    nextData() {
      this.loading = true;
      this.init();
    },
    format(item) {
      return '';
    },
    packUp() {
      this.size = 500;
    },
    stop(row) {
      let array = [];
      if (row) {
        let request = {type: row.executionModule, reportId: row.id};
        array = [request];
      } else {
        array.push({type: 'API', projectId: getCurrentProjectID(), userId: getCurrentUser().id});
        array.push({type: 'SCENARIO', projectId: getCurrentProjectID(), userId: getCurrentUser().id});
        array.push({type: 'PERFORMANCE', projectId: getCurrentProjectID(), userId: getCurrentUser().id});
      }
      this.$post('/api/automation/stop/batch', array, response => {
        this.$success(this.$t('report.test_stop_success'));
        this.init(true);
      });
    },
    getMaintainerOptions() {
      this.$get('/user/project/member/list', response => {
        this.maintainerOptions = response.data;
        this.condition.executor = getCurrentUser().id;
      });
    },
    initWebSocket() {
      let protocol = "ws://";
      if (window.location.protocol === 'https:') {
        protocol = "wss://";
      }
      this.condition.triggerMode = this.condition.triggerMode || 'ALL';
      const uri = protocol + window.location.host + "/task/center/count/running/" + this.condition.executor + "/" + this.condition.triggerMode;
      this.websocket = new WebSocket(uri);
      this.websocket.onmessage = this.onMessage;
      this.websocket.onopen = this.onOpen;
      this.websocket.onerror = this.onError;
      this.websocket.onclose = this.onClose;
    },
    onOpen() {
    },
    onError(e) {
    },
    onMessage(e) {
      this.loading = false;
      this.runningData = JSON.parse(e.data);
      if (this.runningData) {
        this.setActiveName();
      }
      this.runningTotal = this.runningData.total;
      this.init(true);
    },
    onClose(e) {
    },
    changeInit(){
      if (this.websocket && this.websocket.close instanceof Function) {
        this.websocket.close();
      }
      this.getTaskRunning();
      this.init(true);
    },
    setActiveName() {
      if (this.runningData.apiTotal > 0) {
        this.activeName = 'API';
      } else if (this.runningData.scenarioTotal > 0) {
        this.activeName = 'SCENARIO';
      } else if (this.runningData.perfTotal > 0) {
        this.activeName = 'PERF';
      }
    },
    listenScreenChange() {
      this.size = document.body.clientWidth;
    },
    showTaskCenter() {
      this.init(true);
      this.getTaskRunning();
      this.getMaintainerOptions();
      window.addEventListener("resize", this.listenScreenChange, false);
      this.taskVisible = true;
    },
    close() {
      this.visible = false;
      this.size = 550;
      this.showType = "";
      this.currentPage = 1;
      if (this.websocket && this.websocket.close instanceof Function) {
        this.websocket.close();
      }
    },
    open(activeName) {
      if (activeName) {
        this.activeName = activeName;
      }
      this.showTaskCenter();
    },
    getPercentage(status) {
      if (status) {
        status = status.toLowerCase();
        if (status === "waiting" || status === 'stop') {
          return 0;
        }
        if (status === 'saved' || status === 'completed' || status === 'success' || status === 'error' || status === 'unexecute' || status === 'errorreportresult') {
          return 100;
        }
      }
      return 60;
    },
    showStop(status) {
      if (status) {
        status = status.toLowerCase();
        if (status === "stop" || status === 'saved' || status === 'completed' || status === 'success' || status === 'error' || status === 'unexecute' || status === 'errorreportresult') {
          return false;
        }
      }
      return true;
    },
    getModeName(executionModule) {
      switch (executionModule) {
        case "SCENARIO":
          return this.$t('test_track.scenario_test_case');
        case "PERFORMANCE":
          return this.$t('test_track.performance_test_case');
        case "API":
          return this.$t('test_track.api_test_case');
      }
    },
    showReport(row) {
      if (this.size > 550 && this.reportId === row.id) {
        this.packUp();
        return;
      }
      let status = row.executionStatus;
      if (status) {
        status = row.executionStatus.toLowerCase();
        if (status === 'saved' || status === 'completed' || status === 'success' || status === 'error' || status === 'unexecute' || status === 'errorreportresult') {
          this.size = 1400;
          this.reportId = row.id;
          this.executionModule = row.executionModule;
          this.reportType = row.reportType;
          if (row.executionModule === "API" && row.reportType !== 'API_INTEGRATED') {
            this.getExecResult(row.id);
          }
        } else if (status === 'stop') {
          this.$warning(this.$t('commons.run_stop'));
        } else {
          this.$warning(this.$t('commons.run_warning'))
        }
      }
    },
    getExecResult(reportId) {
      if (reportId) {
        let url = "/api/definition/report/get/" + reportId;
        this.$get(url, response => {
          if (response.data) {
            try {
              let data = JSON.parse(response.data.content);
              this.response = data;
              this.visible = true;
            } catch (error) {
              this.visible = true;
            }
          }
        });
      }
    },
    getMode(mode) {
      if (mode === 'MANUAL') {
        return this.$t('commons.trigger_mode.manual');
      }
      if (mode === 'SCHEDULE') {
        return this.$t('commons.trigger_mode.schedule');
      }
      if (mode === 'TEST_PLAN_SCHEDULE') {
        return this.$t('commons.trigger_mode.schedule');
      }
      if (mode === 'API') {
        return this.$t('commons.trigger_mode.api');
      }
      if (mode === 'BATCH') {
        return this.$t('api_test.automation.batch_execute');
      }
      return mode;
    },
    getTaskRunning() {
      this.initWebSocket();
    },
    init(loading) {
      if (this.showType === "CASE" || this.showType === "SCENARIO") {
        return;
      }
      this.condition.projectId = getCurrentProjectID();
      this.condition.userId = getCurrentUser().id;
      this.condition.activeName = this.activeName;
      this.loading = loading;
      this.result = this.$post('/task/center/list/' + this.currentPage + '/' + this.pageSize, this.condition, response => {
        this.total = response.data.itemCount;
        this.taskData = response.data.listObject;
        this.loading = false;
      });
    },
    initCaseHistory(id) {
      this.activeName = 'API';
      this.result = this.$get('/task/center/case/' + id, response => {
        this.taskData = response.data;
      });
    },
    openHistory(id) {
      this.initCaseHistory(id);
      this.taskVisible = true;
      this.isDebugHistory = true;
      this.condition.triggerMode = "MANUAL";
      this.showType = "CASE";
    },
    openScenarioHistory(id) {
      this.activeName = 'SCENARIO';
      this.result = this.$get('/task/center/scenario/' + id, response => {
        this.taskData = response.data;
      });
      this.showType = "SCENARIO";
      this.isDebugHistory = true;
      this.condition.triggerMode = "MANUAL";
      this.taskVisible = true;
    },
    showBadge(executionModule) {
      switch (executionModule) {
        case "SCENARIO":
          return this.runningData.scenarioTotal;
        case "PERF":
          return this.runningData.perfTotal;
        case "API":
          return this.runningData.apiTotal;
      }
    },
    nextPage(currentPage, pageSize) {
      this.currentPage = currentPage;
      this.pageSize = pageSize;
      this.init();
    },
  }
};
</script>

<style>
.ms-drawer-task {
  top: 42px !important;
}
</style>

<style scoped>
.el-icon-check {
  color: #44b349;
  margin-left: 10px;
}

.report-container {
  height: calc(100vh - 270px);
  overflow-y: auto;
}

.align-right {
  float: right;
}

.icon {
  width: 24px;
}

/deep/ .el-drawer__header {
  font-size: 18px;
  color: #0a0a0a;
  border-bottom: 1px solid #E6E6E6;
  background-color: #FFF;
  margin-bottom: 10px;
  padding-top: 6px;
  padding-bottom: 6px;
}

.ms-card-task >>> .el-card__body {
  padding: 10px;
}

.global {
  color: #fff;
}

.header-top-menu {
  height: 40px;
  line-height: 40px;
  color: inherit;
}

.header-top-menu.el-menu--horizontal > li {
  height: 40px;
  line-height: 40px;
  color: inherit;
}

.header-top-menu.el-menu--horizontal > li.el-submenu > * {
  height: 39px;
  line-height: 40px;
  color: inherit;
}

.header-top-menu.el-menu--horizontal > li.is-active {
  background: var(--color_shallow) !important;
}

.ms-card-task:hover {
  cursor: pointer;
  border-color: #783887;
}

/deep/ .el-progress-bar {
  padding-right: 20px;
}

/deep/ .el-menu-item {
  padding-left: 0;
  padding-right: 0;
}

/deep/ .el-badge__content {
  border-radius: 10px;
  height: 10px;
  line-height: 10px;
}


.item {
  margin-right: 10px;
}

.ms-task-error {
  color: #F56C6C;
}

.ms-task-errorreportresult {
  color: #F6972A;
}

.ms-task-stop {
  color: #F56C6C;
  float: right;
  margin-right: 20px;
}

.ms-task-unexecute {
  color: #909399;
}

.ms-task-success {
  color: #67C23A;
}

.ms-task-stop {
  color: #909399;
}

.ms-task-running {
  color: #783887;
}

.ms-task-name-width {
  display: inline-block;
  overflow-x: hidden;
  padding-bottom: 0;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
  width: 300px;
}

.ms-el-form-item >>> .el-form-item {
  margin-bottom: 6px;
}

.ms-task-opt-btn {
  position: fixed;
  right: 1372px;
  top: 50%;
  z-index: 5;
  width: 20px;
  height: 60px;
  padding: 3px;
  line-height: 30px;
  border-radius: 0 15px 15px 0;
  background-color: #783887;
  color: white;
  display: inline-block;
  cursor: pointer;
  opacity: 0.5;
  font-size: 10px;
  font-weight: bold;
  margin-left: 1px;
}

.ms-task-opt-btn i {
  margin-left: -2px;
}

.ms-task-opt-btn:hover {
  opacity: 0.8;
}

.ms-task-opt-btn:hover i {
  margin-left: 0;
  color: white;
}

.report-bottom {
  margin-top: 10px;
}

.ms-badge-item {
  margin-top: 0px;
  margin-right: 0px;
  font-size: 13px;
}

/deep/ .el-badge__content.is-fixed {
  transform: translateY(-10%) translateX(100%);
}

/deep/.el-badge__content {
  border-radius: 10px;
}


</style>
