<template>
  <el-dialog :close-on-click-modal="false" width="60%" class="schedule-edit" :visible.sync="dialogVisible"
             @close="close">
    <template>
      <div>
        <el-tabs v-model="activeName">
          <el-tab-pane :label="$t('schedule.task_config')" name="first">
            <div class="el-step__icon is-text" style="margin-right: 10px;">
              <div class="el-step__icon-inner">1</div>
            </div>
            <span>{{ $t('schedule.edit_timer_task') }}</span>
            <el-form :model="form" :rules="rules" ref="from" style="padding-top: 10px;margin-left: 20px;" class="ms-el-form-item__error">
              <el-form-item :label="$t('commons.schedule_cron_title')"
                            prop="cronValue" style="height: 50px">
                <el-row :gutter="20">
                  <el-col :span="16">
                    <el-input :disabled="isReadOnly" v-model="form.cronValue" class="inp"
                              :placeholder="$t('schedule.please_input_cron_expression')" size="mini">
                      <a :disabled="isReadOnly" type="primary" @click="showCronDialog" slot="suffix" class="head">
                        {{ $t('schedule.generate_expression') }}
                      </a>
                    </el-input>

                    <span>{{ this.$t('commons.schedule_switch') }}</span>
                    <el-tooltip effect="dark" placement="bottom"
                                :content="schedule.enable ? $t('commons.close_schedule') : $t('commons.open_schedule')">
                      <el-switch v-model="schedule.enable" style="margin-left: 20px"></el-switch>
                    </el-tooltip>
                  </el-col>
                  <el-col :span="2">
                    <el-button :disabled="isReadOnly" type="primary" @click="saveCron" size="mini">{{
                        $t('commons.save')
                      }}
                    </el-button>
                  </el-col>
                </el-row>

              </el-form-item>
              <crontab-result :ex="form.cronValue" ref="crontabResult"/>
            </el-form>
            <el-dialog width="60%" :title="$t('schedule.generate_expression')" :visible.sync="showCron"
                       :modal="false">
              <crontab @hide="showCron=false" @fill="crontabFill" :expression="schedule.value"
                       ref="crontab"/>
            </el-dialog>
          </el-tab-pane>
          <el-tab-pane :label="$t('schedule.task_notification')" name="second">
            <schedule-task-notification :test-id="testId"
                                        :schedule-receiver-options="scheduleReceiverOptions"/>
          </el-tab-pane>
        </el-tabs>
      </div>
    </template>
  </el-dialog>
</template>

<script>
import {
  getCurrentProjectID,
  getCurrentUser,
  getCurrentWorkspaceId,
  listenGoBack,
  removeGoBackListener
} from "@/common/js/utils";
import Crontab from "../cron/Crontab";
import CrontabResult from "../cron/CrontabResult";
import {cronValidate} from "@/common/js/cron";
import ScheduleTaskNotification from "../../project/notification/ScheduleTaskNotification";

function defaultCustomValidate() {
  return {pass: true};
}

export default {
  name: "MsScheduleEdit",
  components: {CrontabResult, Crontab, ScheduleTaskNotification},
  props: {
    testId: String,
    save: Function,
    schedule: {},
    customValidate: {
      type: Function,
      default: defaultCustomValidate
    },
    isReadOnly: {
      type: Boolean,
      default: false
    },
  },


  watch: {
    'schedule.value'() {
      this.form.cronValue = this.schedule.value;
    }
  },
  data() {
    const validateCron = (rule, cronValue, callback) => {
      let customValidate = this.customValidate(this.getIntervalTime());
      if (!cronValue) {
        callback(new Error(this.$t('commons.input_content')));
      } else if (!cronValidate(cronValue)) {
        callback(new Error(this.$t('schedule.cron_expression_format_error')));
      }else if(!this.intervalValidate()){
        callback(new Error(this.$t('schedule.cron_expression_interval_error')));
      }
      else if (!customValidate.pass) {
        callback(new Error(customValidate.info));
      } else {
        callback();
      }
    };
    return {
      scheduleReceiverOptions: [],
      operation: true,
      dialogVisible: false,
      showCron: false,
      form: {
        cronValue: ""
      },
      activeName: 'first',
      rules: {
        cronValue: [{required: true, validator: validateCron, trigger: 'blur'}],
      }
    };
  },
  methods: {
    currentUser: () => {
      return getCurrentUser();
    },
    initUserList() {


      this.result = this.$get('/user/project/member/list', response => {
        this.scheduleReceiverOptions = response.data;
      });

    },
    intervalValidate() {
      if (this.getIntervalTime() < 1 * 60 * 1000) {
        return false;
      }
      return true;
    },
    buildParam() {
      let param = {};
      param.notices = this.tableData;
      param.testId = this.testId;
      return param;
    },
    open() {
      this.initUserList();
      this.dialogVisible = true;
      this.form.cronValue = this.schedule.value;
      listenGoBack(this.close);
      this.activeName = 'first';
    },
    crontabFill(value, resultList) {
      //确定后回传的值
      this.form.cronValue = value;
      this.$refs.crontabResult.resultList = resultList;
      this.$refs['from'].validate();
    },
    showCronDialog() {
      let tmp = this.schedule.value;
      this.schedule.value = '';
      this.$nextTick(() => {
        this.schedule.value = tmp;
        this.showCron = true;
      });
    },
    saveCron() {
      this.$refs['from'].validate((valid) => {
        if (valid) {
          this.intervalShortValidate();
          this.save(this.form.cronValue);
          this.dialogVisible = false;
        } else {
          return false;
        }
      });
    },
    saveNotice() {
      let param = this.buildParam();
      this.result = this.$post("notice/save", param, () => {
        this.$success(this.$t('commons.save_success'));
      });
    },
    close() {
      this.dialogVisible = false;
      this.form.cronValue = '';
      this.$refs['from'].resetFields();
      if (!this.schedule.value) {
        this.$refs.crontabResult.resultList = [];
      }
      removeGoBackListener(this.close);
    },
    intervalShortValidate() {
      if (this.getIntervalTime() < 3 * 60 * 1000) {
        // return false;
        this.$info(this.$t('schedule.cron_expression_interval_short_error'));
      }
      return true;
    },
    resultListChange() {
      this.$refs['from'].validate();
    },
    getIntervalTime() {
      let resultList = this.$refs.crontabResult.resultList;
      let time1 = new Date(resultList[0]);
      let time2 = new Date(resultList[1]);
      return time2 - time1;
    },
  },
  computed: {
    isTesterPermission() {
      return false;
    }
  }
};
</script>

<style scoped>

.inp {
  width: 50%;
  margin-right: 20px;
}

.el-form-item {
  margin-bottom: 10px;
}

.head {
  border-bottom: 1px solid #06dc9c;
  color: #06dc9c;
  font-family: "Helvetica Neue", Helvetica, "PingFang SC", "Hiragino Sans GB", Arial, sans-serif;
  font-size: 13px;
  cursor: pointer;
}
.ms-el-form-item__error >>> .el-form-item__error{
  left: 100px;
  padding-top: 0px;
}
</style>
