<template>
  <div v-if="reloaded">
    <div class="text-container" style="border:1px #DCDFE6 solid; height: 100%;border-radius: 4px ;width: 100%">
      <el-form :model="response" ref="response" label-width="100px">

        <el-collapse-transition>
          <el-tabs v-model="activeName" v-show="isActive" style="margin: 20px">
            <el-tab-pane v-if="!isTcp" :label="$t('api_test.definition.request.response_header')" name="headers"
                         class="pane">
              <ms-api-key-value style="width: 95%" :isShowEnable="false" :suggestions="headerSuggestions"
                                :items="response.headers"/>
            </el-tab-pane>
            <el-tab-pane :label="$t('api_test.definition.request.response_body')" name="body" class="pane">
              <mock-api-response-body :isReadOnly="false" :isShowEnable="false" :api-id="apiId" :body="response.body"
                                      :headers="response.headers" :use-post-script="response.usePostScript"
                                      :need-mock="false"/>
            </el-tab-pane>

            <el-tab-pane v-if="!isTcp" :label="$t('api_test.definition.request.status_code')" name="status_code"
                         class="pane">
              <el-row>
                <el-col :span="2"/>
                <el-col :span="20">
                  <el-input size="small" style="width: 180px;margin-top: 10px" v-model="response.httpCode"/>
                </el-col>
                <el-col :span="2"/>
              </el-row>
            </el-tab-pane>
            <el-tab-pane :label="$t('commons.response_time_delay') + '(ms)'" name="delayed" class="pane">
              <el-row>
                <el-input-number v-model="response.delayed" :min="0">
                  <template slot="append">ms</template>
                </el-input-number>
              </el-row>
            </el-tab-pane>
          </el-tabs>
        </el-collapse-transition>
      </el-form>
    </div>
    <div v-show="response.usePostScript">
      <el-row style="margin-top: 10px;">
        <el-col :span="12">
          <p class="tip">{{ $t('api_test.definition.request.post_script') }}</p>
        </el-col>
        <el-col :span="12">
          <i class="el-icon-close" @click="removePostScript"/>
        </el-col>
      </el-row>
      <div class="text-container" style="border:1px #DCDFE6 solid; height: 100%;border-radius: 4px ;width: 100%">
        <div style="padding: 15px 0;">
          <mock-api-script-editor v-if="response.body.scriptObject" :jsr223-processor="response.body.scriptObject"/>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import MockApiResponseBody from "@/business/components/api/definition/components/mock/Components/MockApiResponseBody";
import MsApiKeyValue from "@/business/components/api/definition/components/ApiKeyValue";
import MsApiAuthConfig from "@/business/components/api/definition/components/auth/ApiAuthConfig";
import ApiRequestMethodSelect from "@/business/components/api/definition/components/collapse/ApiRequestMethodSelect";
import MsApiVariable from "@/business/components/api/definition/components/ApiVariable";
import MsApiAssertions from "@/business/components/api/definition/components/assertion/ApiAssertions";
import MsApiExtract from "@/business/components/api/definition/components/extract/ApiExtract";
import BatchAddParameter from "@/business/components/api/definition/components/basis/BatchAddParameter";
import MsApiAdvancedConfig from "@/business/components/api/definition/components/request/http/ApiAdvancedConfig";
import MsJsr233Processor from "@/business/components/api/automation/scenario/component/Jsr233Processor";
import MockApiScriptEditor from "@/business/components/api/definition/components/mock/Components/MockApiScriptEditor";
import ApiDefinitionStepButton
  from "@/business/components/api/definition/components/request/components/ApiDefinitionStepButton";
import {Body, BODY_FORMAT} from "@/business/components/api/definition/model/ApiTestModel";
import {REQUEST_HEADERS} from "@/common/js/constants";

export default {
  name: "MockResponseParam",
  components: {
    ApiDefinitionStepButton,
    MsJsr233Processor,
    MsApiAdvancedConfig,
    BatchAddParameter,
    MsApiVariable,
    ApiRequestMethodSelect,
    MsApiExtract,
    MsApiAuthConfig,
    MockApiResponseBody,
    MockApiScriptEditor,
    MsApiKeyValue,
    MsApiAssertions
  },
  props: {
    response: {},
    apiId: String,
    isTcp: {
      type: Boolean,
      default: false,
    }
  },
  data() {
    return {
      isActive: true,
      reloaded: true,
      activeName: "body",
      modes: ['text', 'json', 'xml', 'html'],
      sqlModes: ['text', 'table'],
      mode: BODY_FORMAT.TEXT,
      isMsCodeEditShow: true,
      reqMessages: "",
      headerSuggestions: REQUEST_HEADERS,
    };
  },
  watch: {
    response() {
      this.setBodyType();
      this.setReqMessage();
    },
  },
  created() {
    this.setBodyType();
    this.setReqMessage();
  },
  methods: {
    setUsePostScript() {
      this.response.usePostScript = true;
      this.refresh();
    },
    removePostScript() {
      this.response.usePostScript = false;
      this.refresh();
    },
    refresh() {
      this.reloaded = false;
      this.$nextTick(() => {
        this.reloaded = true;
      })
    },
    modeChange(mode) {
      this.mode = mode;
    },
    sqlModeChange(mode) {
      this.mode = mode;
    },
    setBodyType() {
      if (!this.response.usePostScript) {
        this.response.usePostScript = false;
      }
      if (!this.response || !this.response || !this.response.headers) {
        return;
      }
      if (!this.response.httpCode || this.response.httpCode === '') {
        this.$set(this.response, "httpCode", "200");
      }
      if (!this.response.delayed) {
        this.response.delayed = 0;
      }
      if (Object.prototype.toString.call(this.response).match(/\[object (\w+)\]/)[1].toLowerCase() !== 'object') {
        this.response = JSON.parse(this.response);
      }
      if (!this.response.body) {
        this.response.body = new Body();
      }
      if (!this.response.headers) {
        this.response.headers = [];
      }
      if (!this.response.body.kvs) {
        this.response.body.kvs = [];
      }
      if (!this.response.rest) {
        this.response.rest = [];
      }
      if (!this.response.arguments) {
        this.response.arguments = [];
      }

      if (this.response.headers.indexOf("Content-Type: application/json") > 0) {
        this.mode = BODY_FORMAT.JSON;
        this.$nextTick(() => {
          if (this.$refs.modeDropdown) {
            this.$refs.modeDropdown.handleCommand(BODY_FORMAT.JSON);
            this.msCodeReload();
          }
        });
      }
    },
    msCodeReload() {
      this.isMsCodeEditShow = false;
      this.$nextTick(() => {
        this.isMsCodeEditShow = true;
      });
    },
    setReqMessage() {
      if (this.response) {
        if (!this.response.url) {
          this.response.url = "";
        }
        if (!this.response.headers) {
          this.response.headers = "";
        }
        if (!this.response.cookies) {
          this.response.cookies = "";
        }
        if (!this.response.body) {
          this.response.body = "";
        }
        if (!this.response) {
          this.response = {};
        }
        if (!this.response.vars) {
          this.response.vars = "";
        }
        this.reqMessages = this.$t('api_test.request.address') + ":\n" + this.response.url + "\n" +
            this.$t('api_test.scenario.headers') + ":\n" + this.response.headers + "\n" + "Cookies :\n" +
            this.response.cookies + "\n" + "Body:" + "\n" + this.response.body;
      }
    },
  },
  mounted() {
    this.setBodyType();
    this.setReqMessage();
  },
  computed: {
    responseResult() {
      return this.response && this.response ? this.response : {};
    }
  }
};
</script>

<style scoped>
.text-container .icon {
  padding: 5px;
}

.text-container .collapse {
  cursor: pointer;
}

.text-container .collapse:hover {
  opacity: 0.8;
}

.text-container .icon.is-active {
  transform: rotate(90deg);
}

.text-container .pane {
  /*background-color: #F5F5F5;*/
  padding: 1px 0;
  height: 250px;
  overflow-y: auto;
}

.text-container .pane.cookie {
  padding: 0;
}

/deep/ .el-tabs__nav-wrap::after {
  height: 0px;
}

.ms-div {
  margin-top: 20px;
}

pre {
  margin: 0;
}

.el-icon-close {
  position: absolute;
  font-size: 20px;
  right: 10px;
  top: 10px;
  color: gray;
}
</style>
