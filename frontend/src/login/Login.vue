<template>
  <div class="container" v-if="ready">
    <el-row type="flex">
      <el-col :span="12">
        <div class="content">
          <div class="title">
          </div>
          <div>
            <div class="welcome">
              <span>{{ loginTitle }}</span>
            </div>
          </div>
          <div class="form">
            <el-form :model="form" :rules="rules" ref="form">
              <el-form-item v-slot:default>
                <el-radio-group v-model="form.authenticate" @change="redirectAuth(form.authenticate)">
                  <el-radio label="LDAP" size="mini" v-if="openLdap">LDAP</el-radio>
                  <el-radio label="LOCAL" size="mini" v-if="openLdap">{{ $t('login.normal_Login') }}</el-radio>
                  <el-radio :label="auth.id" size="mini" v-for="auth in authSources" :key="auth.id">{{ auth.type }}
                    {{ auth.name }}
                  </el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item prop="username">
                <el-input v-model="form.username" :placeholder="$t('commons.login_username')" autofocus
                          autocomplete="off"/>
              </el-form-item>
              <el-form-item prop="password">
                <el-input v-model="form.password" :placeholder="$t('commons.password')" show-password
                          autocomplete="off"
                          maxlength="30" show-word-limit/>
              </el-form-item>
            </el-form>
          </div>
          <div class="btn">
            <el-button type="primary" class="submit" @click="submit('form')">
              {{ $t('commons.login') }}
            </el-button>
          </div>
          <div class="msg">
            {{ msg }}
          </div>
        </div>
      </el-col>

      <div class="divider"/>

      <el-col :span="12">
        <div class="content">
          <img class="login-image" src="../assets/logo-AEKE.png" alt="">
        </div>
      </el-col>

    </el-row>

  </div>
</template>

<script>
import {getCurrentUserId, hasPermissions, publicKeyEncrypt, saveLocalStorage} from '@/common/js/utils';
import {CURRENT_LANGUAGE, DEFAULT_LANGUAGE, PRIMARY_COLOR} from "@/common/js/constants";

const requireComponent = require.context('@/business/components/xpack/', true, /\.vue$/);
const display = requireComponent.keys().length > 0 ? requireComponent("./display/Display.vue") : {};
const auth = requireComponent.keys().length > 0 ? requireComponent("./auth/Auth.vue") : {};
const license = requireComponent.keys().length > 0 ? requireComponent("./license/LicenseMessage.vue") : null;

export default {
  name: "Login",
  data() {
    return {
      result: {},
      form: {
        username: '',
        password: '',
        authenticate: 'LOCAL'
      },
      rules: this.getDefaultRules(),
      msg: '',
      ready: false,
      openLdap: false,
      authSources: [],
      loginUrl: 'signin',
      lastUser: null,
      loginTitle: this.$t('commons.welcome')
    };
  },
  beforeCreate() {
    this.$get('/system/theme', res => {
      this.color = res.data ? res.data : PRIMARY_COLOR;
      document.body.style.setProperty('--primary_color', this.color);
    });
    // 保存当前站点url
    this.$get('/system/save/baseurl?baseurl=' + window.location.origin)
      .then(() => {
      })
      .catch(() => {
      });

    this.result = this.$get("/isLogin").then(response => {

      if (display.default !== undefined) {
        display.default.showLogin(this);
      }

      if (auth.default !== undefined) {
        auth.default.getAuthSources(this);
      }

      if (!response.data.success) {
        this.ready = true;
        // 保存公钥
        localStorage.setItem("publicKey", response.data.message);
        let lang = localStorage.getItem(CURRENT_LANGUAGE);
        if (lang) {
          this.$setLang(lang);
          this.rules = this.getDefaultRules();
        }
      } else {
        let user = response.data.data;
        saveLocalStorage(response.data);
        this.getLanguage(user.language);
        window.location.href = "/";
      }
    });
    this.$get("/ldap/open", response => {
      this.openLdap = response.data;
      if (this.openLdap) {
        this.form.authenticate = 'LDAP';
      }
    });
  },
  created: function () {
    // 主页添加键盘事件,注意,不能直接在焦点事件上添加回车
    document.addEventListener("keydown", this.watchEnter);
    //
    if (license.default) {
      license.default.valid(this);
    }

    // 上次登录的用户
    this.lastUser = sessionStorage.getItem('lastUser');
  },

  destroyed() {
    //移除监听回车按键
    document.removeEventListener("keydown", this.watchEnter);
  },
  methods: {
    //监听回车按钮事件
    watchEnter(e) {
      let keyNum = e.which; //获取被按下的键值
      //判断如果用户按下了回车键（keycody=13）
      if (keyNum === 13) {
        // 按下回车按钮要做的事
        this.submit('form');
      }
    },
    submit(form) {
      this.$refs[form].validate((valid) => {
        if (valid) {
          switch (this.form.authenticate) {
            case "LOCAL":
              this.loginUrl = "/signin";
              this.doLogin();
              break;
            case "LDAP":
              this.loginUrl = "/ldap/signin";
              this.doLogin();
              break;
            default:
              this.loginUrl = "/sso/signin";
              this.doLogin();
          }
        } else {
          return false;
        }
      });
    },
    doLogin() {
      // 删除缓存
      sessionStorage.removeItem('changePassword');
      let publicKey = localStorage.getItem("publicKey");

      let form = {
        username: publicKeyEncrypt(this.form.username, publicKey),
        password: publicKeyEncrypt(this.form.password, publicKey),
        authenticate: this.form.authenticate
      };

      this.result = this.$post(this.loginUrl, form, response => {
        saveLocalStorage(response);
        sessionStorage.setItem('loginSuccess', 'true');
        sessionStorage.setItem('changePassword', response.message);
        this.getLanguage(response.data.language);
        // 检查登录用户的权限
        this.checkRedirectUrl();
      });
    },
    getLanguage(language) {
      if (!language) {
        this.$get("language", response => {
          language = response.data;
          localStorage.setItem(DEFAULT_LANGUAGE, language);
          window.location.href = "/";
        });
      } else {
        window.location.href = "/";
      }
    },
    redirectAuth(authId) {
      if (auth.default) {
        auth.default.redirectAuth(this, authId);
      }
    },
    getDefaultRules() { // 设置完语言要重新赋值
      return {
        username: [
          {required: true, message: this.$t('commons.input_login_username'), trigger: 'blur'},
        ],
        password: [
          {required: true, message: this.$t('commons.input_password'), trigger: 'blur'},
          {min: 6, max: 30, message: this.$t('commons.input_limit', [6, 30]), trigger: 'blur'}
        ]
      };
    },
    checkRedirectUrl() {
      if (this.lastUser === getCurrentUserId()) {
        return;
      }
      let redirectUrl = '/';
      if (hasPermissions('PROJECT_USER:READ', 'PROJECT_ENVIRONMENT:READ', 'PROJECT_OPERATING_LOG:READ', 'PROJECT_FILE:READ+JAR', 'PROJECT_FILE:READ+FILE', 'PROJECT_CUSTOM_CODE:READ', 'PROJECT_MESSAGE:READ', 'PROJECT_TEMPLATE:READ')) {
        redirectUrl = '/project/home';
      } else if (hasPermissions('WORKSPACE_SERVICE:READ', 'WORKSPACE_USER:READ', 'WORKSPACE_PROJECT_MANAGER:READ', 'WORKSPACE_PROJECT_ENVIRONMENT:READ', 'WORKSPACE_OPERATING_LOG:READ')) {
        redirectUrl = '/setting/project/:type';
      } else if (hasPermissions('SYSTEM_USER:READ', 'SYSTEM_WORKSPACE:READ', 'SYSTEM_GROUP:READ', 'SYSTEM_TEST_POOL:READ', 'SYSTEM_SETTING:READ', 'SYSTEM_AUTH:READ', 'SYSTEM_QUOTA:READ', 'SYSTEM_OPERATING_LOG:READ')) {
        redirectUrl = '/setting';
      } else {
        redirectUrl = '/';
      }

      sessionStorage.setItem('redirectUrl', redirectUrl);
    }
  }
};
</script>

<style scoped>
.container {
  width: 1200px;
  height: 730px;
  margin: 0 auto;
  background-color: #FFFFFF;
}

.content {
  margin-left: 10px;
}

.el-row--flex {
  height: 730px;
  margin-top: calc((100vh - 800px) / 2);
}

.el-col:nth-child(3) {
  align-items: center;
  display: flex;
}

.title {
  height: 200px;
} 


.title img {
  width: 293px;
  max-height: 60px;
  margin-top: 165px;
}

.title-img {
  letter-spacing: 0;
  text-align: center;
}

.login-image {
  height: 365px;
  width: 567px;
  margin: auto;
  display: block;
}

.welcome {
  margin-top: 12px;
  margin-bottom: 75px;
  font-size: 50px;
  color: var(--primary_color);
  line-height: 14px;
  text-align: center;
}

.form, .btn {
  padding: 0;
  width: 443px;
  margin: auto;
}

.btn > .submit {
  border-radius: 70px;
  border-color: var(--primary_color);
  background-color: var(--primary_color);
}

.btn > .submit:hover {
  border-color: var(--primary_color);
  background-color: var(--primary_color);
}

.btn > .submit:active {
  border-color: var(--primary_color);
  background-color: var(--primary_color);
}

.el-form-item:first-child {
  margin-top: 60px;
}

/deep/ .el-radio__input.is-checked .el-radio__inner {
  background-color: var(--primary_color);
  background: var(--primary_color);
  border-color: var(--primary_color);
}

/deep/ .el-radio__input.is-checked + .el-radio__label {
  color: var(--primary_color);
}

/deep/ .el-input__inner {
  border-radius: 70px !important;
  background: #f6f3f8 !important;
  border-color: #f6f3f8 !important;
  /*谷歌浏览器默认填充的颜色无法替换，使用下列样式填充*/
  box-shadow: inset 0 0 0 1000px #f6f3f8 !important;
}

.el-input, .el-button {
  width: 443px;
}

/deep/ .el-input__inner:focus {
  border: 1px solid var(--primary_color) !important;
}

.divider {
  border: 1px solid #f6f3f8;
  height: 480px;
  margin: 165px 0px;
}

</style>

<style>
body {
  font-family: -apple-system, BlinkMacSystemFont, "Neue Haas Grotesk Text Pro", "Arial Nova", "Segoe UI", "Helvetica Neue", ".PingFang SC", "PingFang SC", "Source Han Sans SC", "Noto Sans CJK SC", "Source Han Sans CN", "Noto Sans SC", "Source Han Sans TC", "Noto Sans CJK TC", "Hiragino Sans GB", sans-serif;
  font-size: 14px;
  /*background-color: #F5F5F5;*/
  line-height: 26px;
  color: #2B415C;
  -webkit-font-smoothing: antialiased;
  margin: 0;
  height: auto;
}

.form .el-input > .el-input__inner {
  border-radius: 0;
}
</style>

