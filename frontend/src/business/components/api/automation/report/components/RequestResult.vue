<template>
  <el-card class="ms-cards" v-if="request && request.responseResult">
    <div class="request-result">
      <div @click="active">
        <el-row :gutter="18" type="flex" align="middle" class="info">
          <el-col class="ms-req-name-col" :span="18" v-if="indexNumber!=undefined">
            <el-tooltip :content="getName(request.name)" placement="top">
              <div class="method ms-req-name">
                <div class="el-step__icon is-text ms-api-col-create">
                  <div class="el-step__icon-inner"> {{ indexNumber }}</div>
                </div>
                <i class="icon el-icon-arrow-right" :class="{'is-active': showActive}" @click="active" @click.stop/>
                <span>{{ getName(request.name) }}</span>
              </div>
            </el-tooltip>
          </el-col>
          <el-col :span="3">
            <div v-if="totalStatus">
              <el-tooltip effect="dark" v-if="baseErrorCode && baseErrorCode!==''" :content="baseErrorCode"
                          style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" placement="bottom"
                          :open-delay="800">
                <div v-if="totalStatus === 'Success'|| totalStatus === 'success'" style="color: #5daf34">
                  {{ baseErrorCode }}
                </div>
                <div v-else-if="totalStatus === 'errorReportResult'" style="color: #F6972A">
                  {{ baseErrorCode }}
                </div>
                <div v-else style="color: #FE6F71">
                  {{ baseErrorCode }}
                </div>
              </el-tooltip>
            </div>
            <div v-else>
              <el-tooltip effect="dark" v-if="baseErrorCode && baseErrorCode!==''" :content="baseErrorCode"
                          style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" placement="bottom"
                          :open-delay="800">
                <div v-if="request.success" style="color: #F6972A">
                  {{ baseErrorCode }}
                </div>
                <div v-else style="color: #FE6F71">
                  {{ baseErrorCode }}
                </div>
              </el-tooltip>
            </div>
          </el-col>
          <el-col :span="6">
            <div v-if="totalStatus">
              <el-tooltip effect="dark" :content="request.responseResult.responseCode"
                          style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" placement="bottom"
                          :open-delay="800">
                <div v-if="totalStatus === 'Success'|| totalStatus === 'success'" style="color: #5daf34">
                  {{ request.responseResult.responseCode }}
                </div>
                <div v-else-if="totalStatus === 'errorReportResult'" style="color: #F6972A">
                  {{ request.responseResult.responseCode }}
                </div>
                <div style="color: #FE6F71" v-else>
                  {{ request.responseResult.responseCode }}
                </div>
              </el-tooltip>
            </div>
            <div v-else>
              <el-tooltip effect="dark" :content="request.responseResult.responseCode"
                          style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" placement="bottom"
                          :open-delay="800">
                <div style="color: #F6972A" v-if="baseErrorCode && baseErrorCode!=='' && request.success">
                  {{ request.responseResult.responseCode }}
                </div>
                <div style="color: #5daf34" v-else-if="request.success">
                  {{ request.responseResult.responseCode }}
                </div>
                <div style="color: #FE6F71" v-else>
                  {{ request.responseResult.responseCode }}
                </div>
              </el-tooltip>
            </div>
          </el-col>
          <el-col :span="3">
            <div v-if="totalStatus">
              <div v-if="totalStatus === 'Success'|| totalStatus === 'success'" style="color: #5daf34">
                {{ request.responseResult.responseTime }}
              </div>
              <div v-else-if="totalStatus === 'errorReportResult'" style="color: #F6972A">
                {{ request.responseResult.responseTime }}
              </div>
              <div style="color: #FE6F71" v-else>
                {{ request.responseResult.responseTime }}
              </div>
            </div>
            <div v-else>
              <span v-if="request.success">
                {{ request.responseResult.responseTime }} ms
              </span>
              <span style="color: #FE6F71" v-else>
                {{ request.responseResult.responseTime }} ms
              </span>
            </div>
          </el-col>
          <el-col :span="2">
            <div v-if="totalStatus">
              <el-tag size="mini" v-if="totalStatus === 'unexecute'">{{
                  $t('api_test.home_page.detail_card.unexecute')
                }}
              </el-tag>
              <el-tag v-else-if="totalStatus === 'errorReportResult' " class="ms-test-error_code"
                      size="mini">
                {{ $t('error_report_library.option.name') }}
              </el-tag>
              <el-tag size="mini" type="success" v-else-if="totalStatus === 'Success' || totalStatus === 'success'">
                {{ $t('api_report.success') }}
              </el-tag>
              <el-tag size="mini" type="danger" v-else> {{ $t('api_report.fail') }}</el-tag>
            </div>
            <div v-else>
              <el-tag v-if="request.testing" class="ms-test-running" size="mini">
                <i class="el-icon-loading" style="font-size: 16px"/>
                {{ $t('commons.testing') }}
              </el-tag>
              <el-tag size="mini" v-else-if="request.unexecute">{{
                  $t('api_test.home_page.detail_card.unexecute')
                }}
              </el-tag>
              <el-tag size="mini" v-else-if="!request.success && request.status && request.status==='unexecute'">{{
                  $t('api_test.home_page.detail_card.unexecute')
                }}
              </el-tag>
              <el-tag v-else-if="baseErrorCode && baseErrorCode!== '' && request.success" class="ms-test-error_code"
                      size="mini">
                {{ $t('error_report_library.option.name') }}
              </el-tag>
              <el-tag size="mini" type="success" v-else-if="request.success"> {{ $t('api_report.success') }}</el-tag>
              <el-tag size="mini" type="danger" v-else> {{ $t('api_report.fail') }}</el-tag>
            </div>
          </el-col>
        </el-row>
      </div>

      <el-collapse-transition>
        <div v-show="showActive && !request.unexecute" style="width: 99%">
          <ms-request-result-tail
            v-loading="requestInfo.loading"
            :scenario-name="scenarioName"
            :request-type="requestType"
            :request="requestInfo"
            :console="console"
            v-if="showActive"/>
        </div>
      </el-collapse-transition>
    </div>
  </el-card>
</template>

<script>
import MsRequestMetric from "./RequestMetric";
import MsAssertionResults from "./AssertionResults";
import MsRequestText from "./RequestText";
import MsResponseText from "./ResponseText";
import MsRequestResultTail from "./RequestResultTail";
import {selectStepContent} from "@/network/share";

export default {
  name: "MsRequestResult",
  components: {
    MsResponseText,
    MsRequestText,
    MsAssertionResults,
    MsRequestMetric,
    MsRequestResultTail
  },
  props: {
    request: Object,
    scenarioName: String,
    stepId: String,
    indexNumber: Number,
    console: String,
    totalStatus: String,
    errorCode: {
      type: String,
      default: ""
    },
    isActive: {
      type: Boolean,
      default: false
    },
    isShare: Boolean,
    shareId: String,
  },
  created() {
    this.showActive = this.isActive;
    this.baseErrorCode = this.errorCode;
  },
  data() {
    return {
      requestType: "",
      color: {
        type: String,
        default() {
          return "#B8741A";
        }
      },
      requestInfo: {
        loading: true,
        hasData: false,
        responseResult: {},
        subRequestResults: [],
      },
      baseErrorCode: "",
      backgroundColor: {
        type: String,
        default() {
          return "#F9F1EA";
        }
      },
      showActive: false,
    }
  },
  watch: {
    isActive() {
      this.loadRequestInfoExpand();
      this.showActive = this.isActive;
    },
    errorCode() {
      this.baseErrorCode = this.errorCode;
    },
    request: {
      deep: true,
      handler(n) {
        if (this.request.errorCode) {
          this.baseErrorCode = this.request.errorCode;
        } else if (this.request.attachInfoMap && this.request.attachInfoMap.errorReportResult) {
          if (this.request.attachInfoMap.errorReportResult !== "") {
            this.baseErrorCode = this.request.attachInfoMap.errorReportResult;
          }
        }
      },
    }
  },
  methods: {
    loadRequestInfoExpand() {
      if (!this.request.responseResult || this.request.responseResult.body === null || this.request.responseResult.body === undefined) {
        if (this.isShare) {
          selectStepContent(this.shareId,this.stepId, (data) => {
            this.requestInfo = data;
            this.$nextTick(() => {
              this.requestInfo.loading = false;
            });
          });
        } else {
          this.$get("/api/scenario/report/selectReportContent/" + this.stepId, response => {
            this.requestInfo = response.data;
            this.$nextTick(() => {
              this.requestInfo.loading = false;
            });
          });
        }
      } else {
        this.requestInfo = this.request;
      }
    },
    active() {
      if (this.request.unexecute) {
        this.showActive = false;
      } else {
        this.showActive = !this.showActive;
      }
      if (this.showActive) {
        this.loadRequestInfoExpand();
      }
    },
    getName(name) {
      if (name && name.indexOf("<->") !== -1) {
        return name.split("<->")[0];
      }
      if (name && name.indexOf("^@~@^") !== -1) {
        let arr = name.split("^@~@^");
        let value = arr[arr.length - 1];
        if (value.indexOf("UUID=") !== -1) {
          return value.split("UUID=")[0];
        }
        if (value && value.startsWith("UUID=")) {
          return "";
        }
        if (value && value.indexOf("<->") !== -1) {
          return value.split("<->")[0];
        }
        return value;
      }
      if (name && name.startsWith("UUID=")) {
        return "";
      }
      return name;
    }
  },
}
</script>

<style scoped>
.request-result {
  min-height: 30px;
  padding: 2px 0;
}

.request-result .info {
  margin-left: 20px;
  cursor: pointer;
}

.request-result .method {
  color: #1E90FF;
  font-size: 14px;
  font-weight: 500;
  line-height: 35px;
  padding-left: 5px;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.request-result .url {
  color: #7f7f7f;
  font-size: 12px;
  font-weight: 400;
  margin-top: 4px;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  word-break: break-all;
}

.request-result .tab .el-tabs__header {
  margin: 0;
}

.request-result .text {
  height: 300px;
  overflow-y: auto;
}

.sub-result .info {
  background-color: #FFF;
}

.sub-result .method {
  border-left: 5px solid #1E90FF;
  padding-left: 20px;
}

.ms-cards >>> .el-card__body {
  padding: 1px;
}

.sub-result:last-child {
  border-bottom: 1px solid #EBEEF5;
}

.ms-test-running {
  color: #6D317C;
}

.ms-test-error_code {
  color: #F6972A;
  background-color: #FDF5EA;
  border-color: #FDF5EA;
}

.ms-api-col {
  background-color: #EFF0F0;
  border-color: #EFF0F0;
  margin-right: 10px;
  font-size: 12px;
  color: #64666A;
}

.ms-api-col-create {
  background-color: #EBF2F2;
  border-color: #008080;
  margin-right: 10px;
  font-size: 12px;
  color: #008080;
}

/deep/ .el-step__icon {
  width: 20px;
  height: 20px;
  font-size: 12px;
}

.el-divider--horizontal {
  margin: 2px 0;
  background: 0 0;
  border-top: 1px solid #e8eaec;
}

.icon.is-active {
  transform: rotate(90deg);
}

.ms-req-name {
  display: inline-block;
  margin: 0 5px;
  padding-bottom: 0;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
  width: 350px;
}

.ms-req-name-col {
  overflow-x: hidden;
}
</style>
