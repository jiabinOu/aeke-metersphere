<template>
  <div>
    <span class="kv-description" v-if="description">
      {{ description }}
    </span>
    <div style="padding-bottom: 10px;">
      <el-link style="float: right;" @click="batchAdd" type="primary" :disabled="isReadOnly">
        {{ $t("commons.batch_add") }}
      </el-link>
    </div>
    <div style="display: none;">nothing</div>
    <div>
      <div class="kv-row" v-for="(item, index) in items" :key="index">
        <el-row type="flex" :gutter="20" justify="space-between" align="middle">
          <el-col class="kv-checkbox">
            <input type="checkbox" v-if="!isDisable(index)" @change="change" :value="item.uuid" v-model="item.enable"
                   :disabled="isDisable(index) || isReadOnly"/>
          </el-col>

          <el-col>
            <ms-api-variable-input :show-copy="showCopy" :show-variable="showVariable" :is-read-only="isReadOnly"
                                   v-model="item.name" size="small" maxlength="200" @change="change"
                                   :placeholder="$t('api_test.variable_name')" show-word-limit/>
          </el-col>
          <el-col>
            <el-input :disabled="isReadOnly" v-model="item.value" size="small" @change="change"
                      :placeholder="$t('api_test.value')" show-word-limit/>
          </el-col>
          <el-col>
            <el-input :disabled="isReadOnly" v-model="item.remark" size="small" @change="change"
                      :placeholder="$t('commons.remark')" show-word-limit/>
          </el-col>
          <el-col class="kv-delete">
            <el-button size="mini" class="el-icon-delete-solid" circle @click="remove(index)"
                       :disabled="isDisable(index) || isReadOnly"/>
          </el-col>
        </el-row>
      </div>
    </div>
    <batch-add-parameter @batchSave="batchSave" ref="batchAdd"/>
  </div>
</template>

<script>
import {KeyValue} from "../model/ScenarioModel";
import MsApiVariableInput from "./ApiVariableInput";
import BatchAddParameter from "@/business/components/api/definition/components/basis/BatchAddParameter";

export default {
  name: "MsApiScenarioVariables",
  components: {BatchAddParameter, MsApiVariableInput},
  props: {
    description: String,
    items: Array,
    isReadOnly: {
      type: Boolean,
      default: false
    },
    showVariable: {
      type: Boolean,
      default: true
    },
    showCopy: {
      type: Boolean,
      default: true
    },
  },
  data() {
    return {};
  },
  methods: {
    remove: function (index) {
      this.items.splice(index, 1);
      this.$emit('change', this.items);
    },
    change: function () {
      let isNeedCreate = true;
      let removeIndex = -1;
      let repeatKey = "";
      this.items.forEach((item, index) => {
        this.items.forEach((row, rowIndex) => {
          if (item.name === row.name && index !== rowIndex) {
            repeatKey = item.name;
          }
        });
        if (!item.name && !item.value) {
          // 多余的空行
          if (index !== this.items.length - 1) {
            removeIndex = index;
          }
          // 没有空行，需要创建空行
          isNeedCreate = false;
        }
      });
      if (repeatKey !== "") {
        this.$warning(this.$t('api_test.environment.common_config') + "【" + repeatKey + "】" + this.$t('load_test.param_is_duplicate'));
      }
      if (isNeedCreate && !repeatKey) {
        this.items.push(new KeyValue({enable: true}));
      }
      this.$emit('change', this.items);
      // TODO 检查key重复

    },
    isDisable: function (index) {
      return this.items.length - 1 === index;
    },
    _handleBatchVars(data) {
      let params = data.split(/[\r\n]+/);
      let keyValues = [];
      params.forEach(item => {
        let line = item.split(/：|:/);
        let values = item.split(line[0] + ":");
        let required = false;
        keyValues.unshift(new KeyValue({
          name: line[0],
          required: required,
          value: values[1],
          type: "text",
          valid: false,
          file: false,
          encode: true,
          enable: true,
          contentType: "text/plain"
        }));
      });
      return keyValues;
    },
    batchAdd() {
      this.$refs.batchAdd.open();
    },
    batchSave(data) {
      if (data) {
        let keyValues = this._handleBatchVars(data);
        keyValues.forEach(keyValue => {
          let isAdd = true;
          for (let i in this.items) {
            let item = this.items[i];
            if (item.name === keyValue.name) {
              item.value = keyValue.value;
              isAdd = false;
            }
          }
          if (isAdd) {
            this.items.unshift(keyValue);
          }
        })
      }
    },
  },
  created() {
    if (this.items.length === 0) {
      this.items.push(new KeyValue({enable: true}));
    }
  }
};
</script>

<style scoped>
.kv-description {
  font-size: 13px;
}

.kv-checkbox {
  width: 20px;
  margin-right: 10px;
}

.kv-row {
  margin-top: 10px;
}

.kv-delete {
  width: 60px;
}
</style>
