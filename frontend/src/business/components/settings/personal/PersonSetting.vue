<template>
  <div v-loading="result.loading">
    <el-card class="table-card">
      <template v-slot:header>
        <div>
          <el-row type="flex" just ify="space-between" align="middle">
            <span class="title">{{ $t('commons.personal_info') }}</span>
          </el-row>
        </div>
      </template>

      <!--Personal information menu-->
      <el-table border class="adjust-table" :data="tableData" style="width: 100%">
        <el-table-column prop="id" label="ID"/>
        <el-table-column prop="name" :label="$t('commons.username')"/>
        <el-table-column prop="email" :label="$t('commons.email')"/>
        <el-table-column prop="phone" :label="$t('commons.phone')"/>
        <el-table-column prop="createTime" :label="$t('commons.create_time')">
          <template v-slot:default="scope">
            <span>{{ scope.row.createTime | timestampFormatDate }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('commons.operating')">
          <template v-slot:default="scope">
            <div>
              <ms-table-operator-button :tip="$t('member.edit_information')" icon="el-icon-edit"
                                        type="primary" @exec="edit(scope.row)"/>
              <ms-table-operator-button :tip="$t('member.edit_password')" icon="el-icon-s-tools" v-if="isLocalUser"
                                        type="success" @exec="editPassword(scope.row)"/>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!--Modify personal details-->
    <el-dialog :close-on-click-modal="false" :title="$t('member.modify_personal_info')" :visible.sync="updateVisible"
               width="40%"
               :destroy-on-close="true" @close="handleClose">
      <el-form :model="form" label-position="right" label-width="100px" size="small" :rules="rule"
               ref="updateUserForm">
        <el-form-item label="ID" prop="id">
          <el-input v-model="form.id" autocomplete="off" :disabled="true"/>
        </el-form-item>
        <el-form-item :label="$t('commons.username')" prop="name">
          <el-input v-model="form.name" autocomplete="off"/>
        </el-form-item>
        <el-form-item :label="$t('commons.email')" prop="email">
          <el-input v-model="form.email" autocomplete="off" :disabled="!isLocalUser"/>
        </el-form-item>
        <el-form-item :label="$t('commons.phone')" prop="phone">
          <el-input v-model="form.phone" autocomplete="off"/>
        </el-form-item>
      </el-form>
      <jira-user-info @auth="handleAuth" v-if="hasJira" :data="currentPlatformInfo"/>
      <tapd-user-info @auth="handleAuth" v-if="hasTapd" :data="currentPlatformInfo"/>
      <zentao-user-info @auth="handleAuth" v-if="hasZentao" :data="currentPlatformInfo"/>
      <azure-devops-user-info @auth="handleAuth" v-if="hasAzure" :data="currentPlatformInfo"/>
      <template v-slot:footer>
        <ms-dialog-footer
          @cancel="updateVisible = false"
          @confirm="updateUser('updateUserForm')"/>
      </template>
    </el-dialog>

    <!--Change personal password-->
    <el-dialog :close-on-click-modal="false" :title="$t('member.edit_password')" :visible.sync="editPasswordVisible"
               width="35%"
               :destroy-on-close="true" @close="handleClose" left>
      <el-form :model="ruleForm" :rules="rules" ref="editPasswordForm" label-width="100px" class="demo-ruleForm">
        <el-form-item :label="$t('member.old_password')" prop="password" style="margin-bottom: 29px">
          <el-input v-model="ruleForm.password" autocomplete="off" show-password/>
        </el-form-item>
        <el-form-item :label="$t('member.new_password')" prop="newpassword">
          <el-input v-model="ruleForm.newpassword" autocomplete="off" show-password/>
        </el-form-item>
        <el-form-item :label="$t('member.repeat_password')" prop="repeatPassword">
          <el-input v-model="ruleForm.repeatPassword" autocomplete="off" show-password/>
        </el-form-item>
      </el-form>
      <span slot="footer" class="dialog-footer">
           <ms-dialog-footer
             @cancel="editPasswordVisible = false"
             @confirm="updatePassword('editPasswordForm')"/>
        </span>
    </el-dialog>

  </div>
</template>

<script>
import {TokenKey} from "@/common/js/constants";
import MsDialogFooter from "../../common/components/MsDialogFooter";
import {getCurrentUser, getCurrentWorkspaceId, listenGoBack, removeGoBackListener} from "@/common/js/utils";
import MsTableOperatorButton from "../../common/components/MsTableOperatorButton";
import {EMAIL_REGEX, PHONE_REGEX} from "@/common/js/regex";
import JiraUserInfo from "@/business/components/settings/personal/JiraUserInfo";
import TapdUserInfo from "@/business/components/settings/personal/TapdUserInfo";
import {getIntegrationService} from "@/network/workspace";
import ZentaoUserInfo from "@/business/components/settings/personal/ZentaoUserInfo";
import AzureDevopsUserInfo from "@/business/components/settings/personal/AzureDevopsUserInfo";
import {logout} from "@/network/user";

export default {
  name: "MsPersonSetting",
  components: {ZentaoUserInfo, TapdUserInfo, JiraUserInfo, AzureDevopsUserInfo, MsDialogFooter, MsTableOperatorButton},
  inject: [
    'reload'
  ],
  data() {
    return {
      result: {},
      isLocalUser: false,
      updateVisible: false,
      editPasswordVisible: false,
      tableData: [],
      updatePath: '/user/update/current',
      updatePasswordPath: '/user/update/password',
      form: {platformInfo: {}},
      currentPlatformInfo: {
        jiraAccount: '',
        jiraPassword: '',
        tapdUserName: '',
        zentaoUserName: '',
        zentaoPassword: '',
        azureDevopsPat: ''
      },
      ruleForm: {},
      hasJira: false,
      hasTapd: false,
      hasZentao: false,
      hasAzure: false,
      rule: {
        name: [
          {required: true, message: this.$t('member.input_name'), trigger: 'blur'},
          {min: 2, max: 20, message: this.$t('commons.input_limit', [2, 20]), trigger: 'blur'},
          {
            required: true,
            pattern: /^[\u4e00-\u9fa5_a-zA-Z0-9.·-]+$/,
            message: this.$t('member.special_characters_are_not_supported'),
            trigger: 'blur'
          }
        ],
        phone: [
          {
            pattern: PHONE_REGEX,
            message: this.$t('member.mobile_number_format_is_incorrect'),
            trigger: 'blur'
          }
        ],
        email: [
          {required: true, message: this.$t('member.input_email'), trigger: 'blur'},
          {
            required: true,
            pattern: EMAIL_REGEX,
            message: this.$t('member.email_format_is_incorrect'),
            trigger: 'blur'
          }
        ],
      },
      rules: {
        password: [
          {required: true, message: this.$t('user.input_password'), trigger: 'blur'},
        ],
        newpassword: [
          {required: true, message: this.$t('user.input_password'), trigger: 'blur'},
          {
            required: true,
            pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[^]{8,30}$/,
            message: this.$t('member.password_format_is_incorrect'),
            trigger: 'blur'
          },
        ],
        repeatPassword: [
          {required: true, message: this.$t('user.input_password'), trigger: 'blur'},
          {
            required: true,
            pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[^]{8,30}$/,
            message: this.$t('member.password_format_is_incorrect'),
            trigger: 'blur'
          },
        ]
      }
    };
  },

  activated() {
    this.initTableData();
  },
  methods: {
    currentUser: () => {
      return getCurrentUser();
    },
    edit: function (row) {
      this.updateVisible = true;
      this.form = Object.assign({}, row);
      this.getPlatformInfo(row);
      listenGoBack(this.handleClose);
    },
    getPlatformInfo(row) {
      if (row.platformInfo) {
        this.form.platformInfo = JSON.parse(row.platformInfo);
      } else {
        this.form.platformInfo = {};
      }
      let orgId = getCurrentWorkspaceId();
      if (!this.form.platformInfo[orgId]) {
        this.form.platformInfo[orgId] = {};
      }
      this.currentPlatformInfo = this.form.platformInfo[orgId];
      this.result = getIntegrationService((data) => {
        let platforms = data.map(d => d.platform);
        if (platforms.indexOf("Tapd") !== -1) {
          this.hasTapd = true;
        }
        if (platforms.indexOf("Jira") !== -1) {
          this.hasJira = true;
        }
        if (platforms.indexOf("Zentao") !== -1) {
          this.hasZentao = true;
        }
        if (platforms.indexOf("AzureDevops") !== -1) {
          this.hasAzure = true;
        }
      });
    },
    editPassword(row) {
      this.editPasswordVisible = true;
      listenGoBack(this.handleClose);
    },
    cancel() {
      this.editPasswordVisible = false;
      this.ruleForm.password = "";
      this.ruleForm.newpassword = "";
    },
    closeDialog() {
      this.editPasswordVisible = false;
      this.ruleForm.password = "";
      this.ruleForm.newpassword = "";
    },
    updateUser(updateUserForm) {
      this.$refs[updateUserForm].validate(valid => {
        if (valid) {
          let param = {};
          Object.assign(param, this.form);
          param.platformInfo = JSON.stringify(this.form.platformInfo);
          this.result = this.$post(this.updatePath, param, response => {
            this.$success(this.$t('commons.modify_success'));
            localStorage.setItem(TokenKey, JSON.stringify(response.data));
            this.updateVisible = false;
            this.initTableData();
            this.reload();
          });
        } else {
          return false;
        }
      });
    },
    updatePassword(editPasswordForm) {
      this.$refs[editPasswordForm].validate(valid => {
        if (valid) {
          if (this.ruleForm.newpassword !== this.ruleForm.repeatPassword) {
            this.$warning(this.$t('member.inconsistent_passwords'));
            return;
          }
          this.result = this.$post(this.updatePasswordPath, this.ruleForm, response => {
            this.$success(this.$t('commons.modify_success'));
            logout();
          });
        } else {
          return false;
        }
      });
    },
    initTableData() {
      this.result = this.$get("/user/info", response => {
        let data = response.data;
        this.isLocalUser = response.data.source === 'LOCAL';
        let dataList = [];
        dataList[0] = data;
        this.tableData = dataList;
        this.handleRouteOpen();
      });
    },
    handleRouteOpen() {
      let params = this.$route.params;
      if (params.open) {
        this.edit(this.tableData[0]);
        params.open = false;
      }
    },
    handleAuth(type) {
      let param = {...this.currentPlatformInfo};
      param.workspaceId = getCurrentWorkspaceId();
      param.platform = type;
      this.$parent.result = this.$post("issues/user/auth", param, () => {
        this.$success(this.$t('organization.integration.verified'));
      });
    },
    handleClose() {
      this.form = {};
      this.ruleForm = {};
      removeGoBackListener(this.handleClose);
      this.editPasswordVisible = false;
      this.updateVisible = false;
    }
  }
};
</script>

<style scoped>

</style>
