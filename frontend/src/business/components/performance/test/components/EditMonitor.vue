<template>
  <el-dialog
    :close-on-click-modal="false"
    :title="$t('load_test.add_monitor')"
    :visible.sync="dialogVisible"
    width="70%"
    @closed="closeFunc"
    :destroy-on-close="true"
    :append-to-body="true"
    v-loading="result.loading"
  >
    <div style="height: 50vh;overflow-y: auto;">
      <el-form :model="form" label-position="right" label-width="100px" size="small" :rules="rule" ref="monitorForm">
        <el-form-item :label="$t('commons.name')" prop="name">
          <el-input v-model="form.name" autocomplete="off"/>
        </el-form-item>
        <h4 style="margin-left: 80px;">{{ $t('load_test.monitor_host') }}</h4>
        <el-row>
          <el-col :span="12">
            <el-form-item label="IP" prop="ip">
              <el-input v-model="form.ip" autocomplete="off"/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Port" prop="port">
              <el-input-number v-model="form.port" :min="1" :max="65535"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('commons.description')" prop="description">
          <el-input v-model="form.description" autocomplete="off"/>
        </el-form-item>
        <h4 style="margin-left: 80px;">{{ $t('load_test.monitor_item') }}</h4>
        <el-row>
          <el-col :span="20" :offset="2">
            <monitor-key-value key-placeholder="Label" value-placeholder="promQL" :items="monitorList"
                               :suggestions="metricSuggestions"/>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <template v-slot:footer>
      <ms-dialog-footer
        v-if="index !== '' && index !== undefined"
        @cancel="dialogVisible = false"
        @confirm="update"/>
      <ms-dialog-footer
        v-else
        @cancel="dialogVisible = false"
        @confirm="create"/>
    </template>

  </el-dialog>
</template>

<script>

import MsDialogFooter from "@/business/components/common/components/MsDialogFooter";
import MonitorKeyValue from "@/business/components/performance/test/components/MonitorKeyValue";
import {KeyValue} from "@/business/components/api/test/model/ScenarioModel";

export default {
  name: "EditMonitor",
  components: {MonitorKeyValue, MsDialogFooter},
  props: {
    testId: String,
    list: Array,
  },
  data() {
    return {
      result: {},
      form: {},
      dialogVisible: false,
      rule: {
        name: {required: true, message: "名称必填", trigger: 'blur'},
        ip: {required: true, message: "ip必填", trigger: 'blur'},
        port: {required: true, message: "port必填", trigger: 'blur'},
      },
      index: '',
      monitorList: [],
      metricSuggestions: [{value: 'cpu'}, {value: 'memory'}, {value: 'disk'}, {value: 'netIn'}, {value: 'netOut'}]
    };
  },
  methods: {
    open(data, index) {
      this.index = '';
      this.dialogVisible = true;
      if (data) {
        const copy = JSON.parse(JSON.stringify(data));
        if (copy.monitorConfig) {
          this.monitorList = JSON.parse(copy.monitorConfig);
        }
        this.form = copy;
      }
      if (this.monitorList.length === 0) {
        this.monitorList.push(new KeyValue({enable: true}));
      }
      if (index !== '' && index !== undefined) {
        this.index = index;
      }
    },
    closeFunc() {
      this.form = {};
      this.dialogVisible = false;
      this.monitorList = [];
    },
    update() {
      this.$refs.monitorForm.validate(valid => {
        if (valid) {
          this.form.monitorConfig = JSON.stringify(this.monitorList);
          this.list.splice(this.index, 1, this.form);
          this.$emit("update:list", this.list);
          this.dialogVisible = false;
        } else {
          return false;
        }
      });
    },
    create() {
      this.$refs.monitorForm.validate(valid => {
        if (valid) {
          this.form.monitorConfig = JSON.stringify(this.monitorList);
          // this.form.monitorStatus = CONFIG_TYPE.NOT;
          this.list.push(this.form);
          this.$emit("update:list", this.list);
          this.dialogVisible = false;
        } else {
          return false;
        }
      });
    },
  }
};
</script>

<style scoped>
.box {
  padding-left: 5px;
}
</style>
