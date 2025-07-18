<template xmlns:v-slot="http://www.w3.org/1999/XSL/Transform">
  <div>
    <el-row>
      <el-col :span="spanCount">
        <!-- HTTP 请求参数 -->
        <div style="border:1px #DCDFE6 solid; height: 100%;border-radius: 4px ;width: 100%" v-loading="isReloadData">
          <el-tabs v-model="activeName" class="request-tabs">
            <!-- 请求头-->
            <el-tab-pane :label="$t('api_test.request.headers')" name="headers">
              <el-tooltip class="item-tabs" effect="dark" :content="$t('api_test.request.headers')" placement="top-start" slot="label">
              <span>{{ $t('api_test.request.headers') }}
                <div class="el-step__icon is-text ms-api-col ms-header" v-if="request.headers.length>1">
                  <div class="el-step__icon-inner">{{ request.headers.length - 1 }}</div>
                </div>
              </span>
              </el-tooltip>
              <el-row>
                <el-link class="ms-el-link" @click="batchAdd" style="color: #783887"> {{ $t("commons.batch_add") }}</el-link>
              </el-row>
              <ms-api-key-value :append-to-body="true" :show-desc="true" :is-read-only="isReadOnly" :isShowEnable="isShowEnable"
                                :suggestions="headerSuggestions" :items="request.headers" :need-mock="false"/>
            </el-tab-pane>

            <!--query 参数-->
            <el-tab-pane :label="$t('api_test.definition.request.query_param')" name="parameters">
              <el-tooltip class="item-tabs" effect="dark" :content="$t('api_test.definition.request.query_info')" placement="top-start" slot="label">
              <span>{{ $t('api_test.definition.request.query_param') }}
                <div class="el-step__icon is-text ms-api-col ms-header" v-if="request.arguments.length>1">
                  <div class="el-step__icon-inner">{{ request.arguments.length - 1 }}</div>
                </div></span>
              </el-tooltip>
              <el-row>
                <el-link class="ms-el-link" @click="batchAdd" style="color: #783887"> {{ $t("commons.batch_add") }}</el-link>
              </el-row>
              <mock-combination-condition :filter-type-object="request" :is-read-only="isReadOnly" :is-show-enable="isShowEnable" :suggestions="apiParams.query"
                                          :disable-variable-tip="true"
                                          :parameters="request.arguments"/>
            </el-tab-pane>

            <!--REST 参数-->
            <el-tab-pane :label="$t('api_test.definition.request.rest_param')" name="rest">
              <el-tooltip class="item-tabs" effect="dark" :content="$t('api_test.definition.request.rest_info')" placement="top-start" slot="label">
              <span>
                {{ $t('api_test.definition.request.rest_param') }}
                <div class="el-step__icon is-text ms-api-col ms-header" v-if="request.rest.length>1">
                  <div class="el-step__icon-inner">{{ request.rest.length - 1 }}</div>
                </div>
              </span>
              </el-tooltip>
              <el-row>
                <el-link class="ms-el-link" @click="batchAdd" style="color: #783887"> {{ $t("commons.batch_add") }}</el-link>
              </el-row>
              <mock-combination-condition :is-rest="true" :filter-type-object="request" :is-read-only="isReadOnly" :is-show-enable="isShowEnable" :suggestions="apiParams.rest"
                                          :disable-variable-tip="true"
                                          :parameters="request.rest"/>
            </el-tab-pane>

            <!--请求体-->
            <el-tab-pane v-if="isBodyShow" :label="$t('api_test.request.body')" name="body" style="overflow: auto">
              <mock-api-body @headersChange="reloadBody"
                             :suggestions="apiParams.form"
                             :is-read-only="isReadOnly"
                             :isShowEnable="isShowEnable"
                             :need-mock="false"
                             :headers="request.headers" :body="request.body"/>
            </el-tab-pane>
            <el-tab-pane name="create" v-if="hasPermission('PROJECT_API_DEFINITION:READ+CREATE_API') && hasLicense() && definitionTest">
              <template v-slot:label>
                <el-button size="mini" type="primary" @click.stop @click="generate">{{ $t('commons.generate_test_data') }}</el-button>
              </template>
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-col>
    </el-row>
    <batch-add-parameter @batchSave="batchSave" ref="batchAddParameter"/>
  </div>
</template>

<script>
import MsApiKeyValue from "@/business/components/api/definition/components/ApiKeyValue";
import MsApiAuthConfig from "@/business/components/api/definition/components/auth/ApiAuthConfig";
import ApiRequestMethodSelect from "@/business/components/api/definition/components/collapse/ApiRequestMethodSelect";
import {REQUEST_HEADERS} from "@/common/js/constants";
import MsApiAssertions from "@/business/components/api/definition/components/assertion/ApiAssertions";
import MsApiExtract from "@/business/components/api/definition/components/extract/ApiExtract";
import {Body, KeyValue} from "@/business/components/api/definition/model/ApiTestModel";
import {getUUID, hasLicense, hasPermission} from "@/common/js/utils";
import BatchAddParameter from "@/business/components/api/definition/components/basis/BatchAddParameter";
import MsApiAdvancedConfig from "@/business/components/api/definition/components/request/http/ApiAdvancedConfig";
import MsJsr233Processor from "@/business/components/api/automation/scenario/component/Jsr233Processor";
import ApiDefinitionStepButton
  from "@/business/components/api/definition/components/request/components/ApiDefinitionStepButton";
import Convert from "@/business/components/common/json-schema/convert/convert";
import MockApiBody from "@/business/components/api/definition/components/mock/Components/MockApiBody";
import MockCombinationCondition
  from "@/business/components/api/definition/components/mock/Components/MockCombinationCondition";

export default {
  name: "MockRequestParam",
  components: {
    ApiDefinitionStepButton,
    MsJsr233Processor,
    MsApiAdvancedConfig,
    BatchAddParameter,
    ApiRequestMethodSelect,
    MsApiExtract,
    MsApiAuthConfig,
    MockApiBody,
    MsApiKeyValue,
    MsApiAssertions,
    MockCombinationCondition,
  },
  props: {
    method: String,
    request: {},
    response: {},
    definitionTest: {
      type: Boolean,
      default() {
        return false;
      }
    },
    showScript: {
      type: Boolean,
      default: true,
    },
    referenced: {
      type: Boolean,
      default: false,
    },
    isShowEnable: Boolean,
    jsonPathList: Array,
    apiParams: Object,
    isReadOnly: {
      type: Boolean,
      default: false
    },
    type: String,
  },
  data() {
    let validateURL = (rule, value, callback) => {
      try {
        new URL(this.addProtocol(this.request.url));
      } catch (e) {
        callback(this.$t('api_test.request.url_invalid'));
      }
    };
    return {
      activeName: this.request.method === "POST" ? "body" : "parameters",
      rules: {
        name: [
          {max: 300, message: this.$t('commons.input_limit', [1, 300]), trigger: 'blur'}
        ],
        url: [
          {max: 500, required: true, message: this.$t('commons.input_limit', [1, 500]), trigger: 'blur'},
          {validator: validateURL, trigger: 'blur'}
        ],
        path: [
          {max: 500, message: this.$t('commons.input_limit', [0, 500]), trigger: 'blur'},
        ]
      },
      spanCount: 21,
      headerSuggestions: REQUEST_HEADERS,
      isReloadData: false,
      isBodyShow: true,
      dialogVisible: false,
      hasOwnProperty: Object.prototype.hasOwnProperty,
      propIsEnumerable: Object.prototype.propertyIsEnumerable

    }
  },
  created() {
    if (!this.referenced && this.showScript) {
      this.spanCount = 21;
    } else {
      this.spanCount = 24;
    }
    this.init();
  },
  methods: {
    hasPermission,
    hasLicense,
    generate() {
      if (this.request.body && (this.request.body.jsonSchema || this.request.body.raw)) {
        if (!this.request.body.jsonSchema) {
          const MsConvert = new Convert();
          this.request.body.jsonSchema = MsConvert.format(JSON.parse(this.request.body.raw));
        }
        this.$post('/api/test/data/generator', this.request.body.jsonSchema, response => {
          if (response.data) {
            if (this.request.body.format !== 'JSON-SCHEMA') {
              this.request.body.raw = response.data;
            } else {
              const MsConvert = new Convert();
              let data = MsConvert.format(JSON.parse(response.data));
              this.request.body.jsonSchema = this.deepAssign(this.request.body.jsonSchema, data);
            }
            this.reloadBody();
          }
        });
      }
    },
    remove(row) {
      let index = this.request.hashTree.indexOf(row);
      this.request.hashTree.splice(index, 1);
      this.reload();
    },
    copyRow(row) {
      let obj = JSON.parse(JSON.stringify(row));
      obj.id = getUUID();
      this.request.hashTree.push(obj);
      this.reload();
    },
    reload() {
      this.isReloadData = true
      this.$nextTick(() => {
        this.isReloadData = false
      })
    },
    init() {
      if (Object.prototype.toString.call(this.request).match(/\[object (\w+)\]/)[1].toLowerCase() !== 'object') {
        this.request = JSON.parse(this.request);
      }
      if (!this.request.body) {
        this.request.body = new Body();
      }
      if (!this.request.headers) {
        this.request.headers = [];
      }
      if (!this.request.body.kvs) {
        this.request.body.kvs = [];
      }
      if (!this.request.rest) {
        this.request.rest = [];
      }
      if (!this.request.arguments) {
        this.request.arguments = [];
      }
    },
    reloadBody() {
      // 解决修改请求头后 body 显示错位
      this.isBodyShow = false;
      this.$nextTick(() => {
        this.isBodyShow = true;
      });
    },
    batchAdd() {
      this.$refs.batchAddParameter.open();
    },
    format(array, obj) {
      if (array) {
        let isAdd = true;
        for (let i in array) {
          let item = array[i];
          if (item.name === obj.name) {
            item.value = obj.value;
            isAdd = false;
          }
        }
        if (isAdd) {
          switch (this.activeName) {
            case "parameters":
              this.request.arguments.unshift(obj);
              break;
            case "rest":
              this.request.rest.unshift(obj);
              break;
            case "headers":
              this.request.headers.unshift(obj);
              break;
            default:
              break;
          }
        }
      }
    },
    batchSave(data) {
      if (data) {
        let params = data.split(/[\r\n]+/);
        let keyValues = [];
        params.forEach(item => {
          let line = item.split(/：|:/);
          let required = false;
          keyValues.unshift(new KeyValue({
            name: line[0],
            required: required,
            value: line[1],
            description: line[2],
            type: "text",
            valid: false,
            file: false,
            encode: true,
            enable: true,
            contentType: "text/plain"
          }));
        })

        keyValues.forEach(item => {
          switch (this.activeName) {
            case "parameters":
              this.format(this.request.arguments, item);
              break;
            case "rest":
              this.format(this.request.rest, item);
              break;
            case "headers":
              this.format(this.request.headers, item);
              break;
            default:
              break;
          }
        })
      }
    },

    isObj(x) {
      let type = typeof x;
      return x !== null && (type === 'object' || type === 'function');
    },

    toObject(val) {
      if (val === null || val === undefined) {
        return;
      }

      return Object(val);
    },

    assignKey(to, from, key) {
      let val = from[key];

      if (val === undefined || val === null) {
        return;
      }
      if (!this.hasOwnProperty.call(to, key) || !this.isObj(val)) {
        to[key] = val;
      } else {
        to[key] = this.assign(Object(to[key]), from[key]);
      }
    },

    assign(to, from) {
      if (to === from) {
        return to;
      }
      from = Object(from);
      for (let key in from) {
        if (this.hasOwnProperty.call(from, key)) {
          this.assignKey(to, from, key);
        }
      }

      if (Object.getOwnPropertySymbols) {
        let symbols = Object.getOwnPropertySymbols(from);

        for (let i = 0; i < symbols.length; i++) {
          if (this.propIsEnumerable.call(from, symbols[i])) {
            this.assignKey(to, from, symbols[i]);
          }
        }
      }

      return to;
    },

    deepAssign(target) {
      target = this.toObject(target);
      for (let s = 1; s < arguments.length; s++) {
        this.assign(target, arguments[s]);
      }
      return target;
    }
  }
}
</script>

<style scoped>
.ms-query {
  background: #783887;
  color: white;
  height: 18px;
  border-radius: 42%;
}

.ms-header {
  background: #783887;
  color: white;
  height: 18px;
  border-radius: 42%;
}

.request-tabs {
  margin: 10px;
  min-height: 200px;
}

.ms-el-link {
  float: right;
  margin-right: 45px;
}
</style>
