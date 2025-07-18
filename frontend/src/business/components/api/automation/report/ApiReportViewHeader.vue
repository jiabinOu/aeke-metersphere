<template>
  <header class="report-header">
    <el-row>
      <el-col>
        <span v-if="!debug">
          <el-input v-if="nameIsEdit" size="mini" @blur="handleSave(report.name)" @keyup.enter.native="handleSaveKeyUp"
                    style="width: 200px" v-model="report.name" maxlength="60" show-word-limit/>
          <span v-else>
            <el-link v-if="isSingleScenario"
                     type="primary"
                     class="report-name"
                     @click="clickResource">
              {{ report.name }}
            </el-link>
            <span v-else>
              {{ report.name }}
            </span>
            <i v-if="showCancelButton" class="el-icon-edit" style="cursor:pointer" @click="nameIsEdit = true"
               @click.stop/>
          </span>
        </span>
        <span v-if="report.endTime || report.createTime">
          <span style="margin-left: 10px">{{ $t('report.test_start_time') }}：</span>
          <span class="time"> {{ report.createTime | timestampFormatDate }}</span>
          <span style="margin-left: 10px">{{ $t('report.test_end_time') }}：</span>
          <span class="time"> {{ report.endTime | timestampFormatDate }}</span>
        </span>
        <div style="float: right">
          <el-button v-if="!isPlan && (!debug || exportFlag) && !isTemplate && !isUi"
                     v-permission="['PROJECT_API_REPORT:READ+EXPORT']" :disabled="isReadOnly" class="export-button"
                     plain type="primary" size="mini" @click="handleExport(report.name)" style="margin-right: 10px">
            {{ $t('test_track.plan_view.export_report') }}
          </el-button>

          <el-popover
            v-if="!isPlan && (!debug || exportFlag) && !isTemplate"
            v-permission="['PROJECT_PERFORMANCE_REPORT:READ+EXPORT']"
            style="margin-right: 10px;float: right;"
            placement="bottom"
            width="300">
            <p>{{ shareUrl }}</p>
            <span style="color: red;float: left;margin-left: 10px;" v-if="application.typeValue">{{
                $t('commons.validity_period') + application.typeValue
              }}</span>
            <div style="text-align: right; margin: 0">
              <el-button type="primary" size="mini" :disabled="!shareUrl"
                         v-clipboard:copy="shareUrl">{{ $t("commons.copy") }}
              </el-button>
            </div>
            <el-button slot="reference" :disabled="isReadOnly" type="danger" plain size="mini"
                       @click="handleShare(report)">
              {{ $t('test_track.plan_view.share_report') }}
            </el-button>
          </el-popover>

          <el-button v-if="showCancelButton" class="export-button" plain size="mini" @click="returnView">
            {{ $t('commons.cancel') }}
          </el-button>

        </div>
      </el-col>
    </el-row>
  </header>
</template>

<script>

import {generateShareInfoWithExpired} from "@/network/share";
import MsTag from "@/business/components/common/components/MsTag";
import {getCurrentProjectID, getCurrentWorkspaceId, getUUID} from "@/common/js/utils";

const requireComponent = require.context('@/business/components/xpack/', true, /\.vue$/);
const UiDownloadScreenshot = requireComponent.keys().length > 0 ? requireComponent("./ui/automation/report/UiDownloadScreenshot.vue") : {};

export default {
  name: "MsApiReportViewHeader",
  components: {MsTag, 'UiDownloadScreenshot': UiDownloadScreenshot.default},
  props: {
    report: {},
    debug: Boolean,
    showCancelButton: {
      type: Boolean,
      default: true,
    },
    isShare: {
      type: Boolean,
      default: false,
    },
    isTemplate: Boolean,
    exportFlag: {
      type: Boolean,
      default: false,
    },
    isPlan: Boolean
  },
  computed: {
    path() {
      return "/api/test/edit?id=" + this.report.testId;
    },
    scenarioId() {
      if (typeof this.report.scenarioId === 'string') {
        return this.report.scenarioId;
      } else {
        return "";
      }
    },
    isSingleScenario() {
      try {
        JSON.parse(this.report.scenarioId);
        return false;
      } catch (e) {
        return true;
      }
    },
    isUi() {
      return this.report.reportType && this.report.reportType.startsWith("UI");
    },
  },
  data() {
    return {
      isReadOnly: false,
      nameIsEdit: false,
      shareUrl: "",
      application: {}
    }
  },
  created() {

  },
  methods: {
    handleExport(name) {
      this.$emit('reportExport', name);
    },
    handleSave(name) {
      this.nameIsEdit = false;
      this.$emit('reportSave', name);
    },
    handleSaveKeyUp($event) {
      $event.target.blur();
    },
    clickResource() {
      if (this.isUi && this.isShare) {
        return;
      }
      //跳转修改只修改场景报告跳转逻辑。 UI部分的不清楚具体跳转细节，所以维持原来的代码逻辑。
      let workspaceId = getCurrentWorkspaceId();
      this.$get("/project/api/project/get/" + this.scenarioId, res => {
        if(res.data){
          let scenarioProjectId = res.data.id;
          if (scenarioProjectId !== getCurrentProjectID()) {
            workspaceId = res.data.workspaceId;
            this.checkPermission(workspaceId, scenarioProjectId, res.data.name);
          } else {
            this.checkPermission(workspaceId, scenarioProjectId, null);
          }
        }
      });
    },
    checkPermission(workspaceId, projectId, projectName) {
      this.$get("/workspace/get/" + workspaceId, response => {
        if (response.data) {
          let workspaceName = response.data.name;
          this.$get('/project/getOwnerProjectIds', res => {
            const project = res.data.find(p => p === projectId);
            if (!project) {
              this.$warning(this.$t('commons.no_permission'));
            } else {
              this.redirect(workspaceId, projectId, projectName, workspaceName);
            }
          })
        }
      });
    },
    redirect(workspaceId, projectId, projectName, workspaceName) {
      let data = this.$router.resolve({
        name: this.isUi ? 'uiAutomation' : 'ApiAutomation',
        query: {
          redirectID: getUUID(),
          dataType: "scenario",
          projectId: projectId,
          workspaceId: workspaceId,
          resourceId: this.scenarioId,
          projectName: projectName,
          workspaceName: workspaceName,
        }
      });
      window.open(data.href, '_blank');
    },
    returnView() {
      if (this.isUi) {
        this.$router.push('/ui/report');
      } else {
        this.$router.push('/api/automation/report');
      }
    },
    handleShare(report) {
      this.getProjectApplication();
      let pram = {};
      pram.customData = report.id;
      pram.shareType = 'API_REPORT';
      let thisHost = window.location.host;
      let shareUrl = thisHost + "/shareApiReport";
      if (this.isUi) {
        pram.shareType = 'UI_REPORT';
        shareUrl = thisHost + "/shareUiReport";
      }
      generateShareInfoWithExpired(pram, (data) => {
        this.shareUrl = shareUrl + data.shareUrl;
      });
    },
    getProjectApplication() {
      let path = "/API_SHARE_REPORT_TIME";
      if (this.isUi) {
        path = "/UI_SHARE_REPORT_TIME";
      }
      this.$get('/project_application/get/' + getCurrentProjectID() + path, res => {
        if (res.data && res.data.typeValue) {
          let quantity = res.data.typeValue.substring(0, res.data.typeValue.length - 1);
          let unit = res.data.typeValue.substring(res.data.typeValue.length - 1);
          if (unit === 'H') {
            res.data.typeValue = quantity + this.$t('commons.date_unit.hour');
          } else if (unit === 'D') {
            res.data.typeValue = quantity + this.$t('commons.date_unit.day');
          } else if (unit === 'M') {
            res.data.typeValue = quantity + this.$t('commons.workspace_unit') + this.$t('commons.date_unit.month');
          } else if (unit === 'Y') {
            res.data.typeValue = quantity + this.$t('commons.date_unit.year');
          }
          this.application = res.data;
        }
      });
    },
  }
}
</script>

<style scoped>

.export-button {
  float: right;
  margin-right: 10px;
}

.report-name {
  border-bottom: 1px solid var(--primary_color);
}
</style>
