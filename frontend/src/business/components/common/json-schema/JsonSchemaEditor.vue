<template>
  <div id="app" v-loading="loading">
    <el-tabs v-model="activeName" @tab-click="handleClick">
      <el-tab-pane :label="$t('organization.message.template')" name="apiTemplate">
        <el-button v-show="!jsonSchemaDisable" type="primary" size="mini" style="margin: 10px 10px 0px" @click="openOneClickOperation">
          {{ this.$t('commons.import') }}
        </el-button>
        <div :style="jsonSchemaDisable? '' : 'min-height: 200px'">
          <json-schema-editor class="schema"
                              :disabled="jsonSchemaDisable"
                              :value="schema"
                              :show-mock-vars="showMockVars"
                              :need-mock="needMock"
                              :scenario-definition="scenarioDefinition"
                              @editScenarioAdvance="editScenarioAdvance"
                              lang="zh_CN" custom/>
        </div>
      </el-tab-pane>
      <el-tab-pane v-if="showPreview" :label="$t('schema.preview')" name="preview">
        <div style="min-height: 200px">
          <pre>{{ this.preview }}</pre>
        </div>
      </el-tab-pane>
    </el-tabs>

    <ms-import-json ref="importJson" @jsonData="jsonData"/>
  </div>
</template>

<script>
import MsImportJson from './import/ImportJson';

const Convert = require('./convert/convert.js');
const MsConvert = new Convert();

export default {
  name: 'App',
  components: {MsImportJson},
  props: {
    body: {},
    showPreview: {
      type: Boolean,
      default: true
    },
    jsonSchemaDisable: {
      type: Boolean,
      default: false
    },
    showMockVars: {
      type: Boolean,
      default() {
        return false;
      }
    },
    scenarioDefinition: Array,
    needMock: {
      type: Boolean,
      default() {
        return true;
      }
    }
  },
  created() {
    if (!this.body.jsonSchema && this.body.raw && this.checkIsJson(this.body.raw)) {
      let obj = {"root": MsConvert.format(JSON.parse(this.body.raw))}
      this.schema = obj;
    } else if (this.body.jsonSchema) {
      this.schema = {"root": this.body.jsonSchema};
    }
    this.body.jsonSchema = this.schema.root;
  },
  watch: {
    schema: {
      handler(newValue, oldValue) {
        this.body.jsonSchema = this.schema.root;
      },
      deep: true
    },
    body: {
      handler(newValue, oldValue) {
        if (!this.body.jsonSchema && this.body.raw && this.checkIsJson(this.body.raw)) {
          let obj = {"root": MsConvert.format(JSON.parse(this.body.raw))}
          this.schema = obj;
        } else if (this.body.jsonSchema) {
          this.schema = {"root": this.body.jsonSchema};
        }
        this.body.jsonSchema = this.schema.root;
      },
      deep: true
    }
  },
  data() {
    return {
      schema:
        {
          "root": {
            "type": "object",
            "properties": {},
          }
        },
      loading: false,
      preview: null,
      activeName: "apiTemplate",
    }
  },
  methods: {
    handleClick() {
      if (this.activeName === 'preview') {
        this.loading = true;
        // 后台转换
        MsConvert.schemaToJsonStr(this.schema.root, (result) => {
          this.preview = result;
          this.loading = false;
        });
      }
    },
    openOneClickOperation() {
      this.$refs.importJson.openOneClickOperation();
    },
    checkIsJson(json) {
      try {
        JSON.parse(json);
        return true;
      } catch (e) {
        return false;
      }
    },
    jsonData(data) {
      this.schema.root = {};
      this.$nextTick(() => {
        this.schema.root = data;
        this.body.jsonSchema = this.schema.root;
      })
    },
    editScenarioAdvance(data) {
      this.$emit('editScenarioAdvance', data);
    },
  }
}
</script>
<style>

</style>
