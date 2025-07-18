<template>
  <div>
    <el-radio-group v-model="body.type" size="mini">
      <el-radio :disabled="isReadOnly" :label="type.FORM_DATA" @change="modeChange">
        {{ $t('api_test.definition.request.body_form_data') }}
      </el-radio>

      <el-radio :disabled="isReadOnly" :label="type.WWW_FORM" @change="modeChange">
        {{ $t('api_test.definition.request.body_x_www_from_urlencoded') }}
      </el-radio>

      <el-radio :disabled="isReadOnly" :label="type.JSON" @change="modeChange">
        {{ $t('api_test.definition.request.body_json') }}
      </el-radio>

      <el-radio :disabled="isReadOnly" :label="type.XML" @change="modeChange">
        {{ $t('api_test.definition.request.body_xml') }}
      </el-radio>

      <el-radio :disabled="isReadOnly" :label="type.RAW" @change="modeChange">
        {{ $t('api_test.definition.request.body_raw') }}
      </el-radio>

      <el-radio :disabled="isReadOnly" :label="type.BINARY" @change="modeChange">
        {{ $t('api_test.definition.request.body_binary') }}
      </el-radio>
    </el-radio-group>
    <div v-if="body.type == 'Form Data' || body.type == 'WWW_FORM'">
      <el-row v-if="body.type == 'Form Data' || body.type == 'WWW_FORM'">
        <el-link class="ms-el-link" @click="batchAdd"> {{ $t("commons.batch_add") }}</el-link>
      </el-row>
      <ms-api-variable
        :with-mor-setting="true"
        :is-read-only="isReadOnly"
        :parameters="body.kvs"
        :urlEncode="body.type == 'WWW_FORM'"
        :isShowEnable="isShowEnable"
        :scenario-definition="scenarioDefinition"
        @editScenarioAdvance="editScenarioAdvance"
        type="body"/>
    </div>
    <div v-if="body.type == 'JSON'">
      <div style="padding: 10px">
        <el-switch active-text="JSON-SCHEMA" v-model="body.format" @change="formatChange" active-value="JSON-SCHEMA"/>
      </div>
      <ms-json-code-edit
        v-if="body.format==='JSON-SCHEMA'"
        :body="body"
        :scenario-definition="scenarioDefinition"
        @editScenarioAdvance="editScenarioAdvance"
        ref="jsonCodeEdit"/>
      <ms-code-edit
        v-else-if="codeEditActive"
        :read-only="isReadOnly"
        :data.sync="body.raw"
        :modes="modes"
        :mode="'json'"
        height="400px"
        ref="codeEdit"/>
    </div>

    <div class="ms-body" v-if="body.type == 'XML'">
      <ms-code-edit
        :read-only="isReadOnly"
        :data.sync="body.raw"
        :modes="modes"
        :mode="'text'"
        ref="codeEdit"/>
    </div>

    <div class="ms-body" v-if="body.type == 'Raw'">
      <ms-code-edit
        :read-only="isReadOnly"
        :data.sync="body.raw"
        :modes="modes"
        ref="codeEdit"/>
    </div>

    <ms-api-binary-variable
      :is-read-only="isReadOnly"
      :parameters="body.binary"
      :isShowEnable="isShowEnable"
      type="body"
      v-if="body.type == 'BINARY'"/>
    <batch-add-parameter @batchSave="batchSave" ref="batchAddParameter"/>
  </div>
</template>

<script>
import MsApiKeyValue from "../ApiKeyValue";
import {BODY_TYPE, KeyValue} from "../../model/ApiTestModel";
import MsCodeEdit from "../../../../common/components/MsCodeEdit";
import MsJsonCodeEdit from "../../../../common/json-schema/JsonSchemaEditor";
import MsDropdown from "../../../../common/components/MsDropdown";
import MsApiVariable from "../ApiVariable";
import MsApiBinaryVariable from "./ApiBinaryVariable";
import MsApiFromUrlVariable from "./ApiFromUrlVariable";
import BatchAddParameter from "../basis/BatchAddParameter";
import Convert from "@/business/components/common/json-schema/convert/convert";
import {jsonParse, trimAll} from "@/business/components/common/json-schema/convert/jsonParse";


export default {
  name: "MsApiBody",
  components: {
    MsApiVariable,
    MsDropdown,
    MsCodeEdit,
    MsApiKeyValue,
    MsApiBinaryVariable,
    MsApiFromUrlVariable,
    MsJsonCodeEdit,
    BatchAddParameter
  },
  props: {
    body: {
      type: Object,
      default() {
        return {
          json: true,
          kV: false,
          kvs: [],
          oldKV: false,
          type: "JSON",
          valid: false,
          xml: false
        }
      }
    },
    headers: Array,
    isReadOnly: {
      type: Boolean,
      default: false
    },
    isShowEnable: {
      type: Boolean,
      default: true
    },
    scenarioDefinition: Array,
  },
  data() {
    return {
      type: BODY_TYPE,
      modes: ['text', 'json', 'xml', 'html'],
      jsonSchema: "JSON",
      codeEditActive: true,
      hasOwnProperty: Object.prototype.hasOwnProperty,
      propIsEnumerable: Object.prototype.propertyIsEnumerable,
    };
  },

  watch: {
    'body.typeChange'() {
      this.reloadCodeEdit();
    },
  },
  methods: {
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
      // 清除多出部分属性
      let property = ['description', 'maxLength', 'minLength', 'pattern', 'format', 'enum', 'default'];
      // 清除多出部分属性
      for (let key in to) {
        if (!this.hasOwnProperty.call(from, key) && property.indexOf(key) === -1) {
          delete to[key]
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
    },
    reloadCodeEdit() {
      this.codeEditActive = false;
      this.$nextTick(() => {
        this.codeEditActive = true;
      });
    },
    formatChange() {
      const MsConvert = new Convert();

      if (this.body.format === 'JSON-SCHEMA') {
        if (this.body.raw) {
          try {
            const tmpStr = trimAll(this.body.raw)
            const tmpObj = jsonParse(tmpStr)
            this.body.jsonSchema = MsConvert.format(tmpObj);
          } catch (e) {
            this.body.format = 'JSON';
            this.$error(this.$t('api_test.definition.json_format_error'));
          }
        }
      } else {
        if (this.body.jsonSchema) {
          MsConvert.schemaToJsonStr(this.body.jsonSchema, (result) => {
            this.$set(this.body, 'raw', result);
            this.reloadCodeEdit();
          });
        }
      }
    },
    modeChange(mode) {
      switch (this.body.type) {
        case "JSON":
          this.setContentType("application/json");
          break;
        case "XML":
          this.setContentType("text/xml");
          break;
        case "WWW_FORM":
          this.setContentType("application/x-www-form-urlencoded");
          break;
        // todo from data
        case "BINARY":
          this.setContentType("application/octet-stream");
          break;
        default:
          this.removeContentType();
          break;
      }
    },
    setContentType(value) {
      let isType = false;
      this.headers.forEach(item => {
        if (item.name === "Content-Type" || item.name == "contentType") {
          item.value = value;
          isType = true;
        }
      })
      if (this.body && this.body.kvs && value === "application/x-www-form-urlencoded") {
        this.body.kvs.forEach(item => {
          item.urlEncode = true;
        });
      }
      if (!isType) {
        this.headers.unshift(new KeyValue({name: "Content-Type", value: value}));
        this.$emit('headersChange');
      }
    },
    removeContentType() {
      for (let index in this.headers) {
        if (this.headers[index].name === "Content-Type") {
          this.headers.splice(index, 1);
          this.$emit('headersChange');
          return;
        }
      }
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
          this.body.kvs.unshift(obj);
        }
      }
    },
    batchSave(data) {
      if (data) {
        let params = data.split(/[\r\n]+/);
        let keyValues = [];
        params.forEach(item => {
          let line = [];
          line[0] = item.substring(0, item.indexOf(":"));
          line[1] = item.substring(item.indexOf(":") + 1, item.length);
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
          this.format(this.body.kvs, item);
        })
      }
    },
    editScenarioAdvance(data) {
      this.$emit('editScenarioAdvance', data);
    },
  },
  created() {
    if (!this.body.type) {
      this.body.type = BODY_TYPE.FORM_DATA;
    }
    if (this.body.kvs) {
      this.body.kvs.forEach(param => {
        if (!param.type) {
          param.type = 'text';
        }
      });
    }

  }
}
</script>

<style scoped>
.textarea {
  margin-top: 10px;
}

.ms-body {
  padding: 15px 0;
  height: 400px;
}

.el-dropdown {
  margin-left: 20px;
  line-height: 30px;
}

.ace_editor {
  border-radius: 5px;
}

.el-radio-group {
  margin: 10px 10px;
  margin-top: 15px;
}

.ms-el-link {
  float: right;
  margin-right: 45px;
}
</style>
