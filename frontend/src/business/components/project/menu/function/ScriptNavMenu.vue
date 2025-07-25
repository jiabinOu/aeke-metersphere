<template>
  <div style="line-height: 20px;">
    <div class="template-title">
      <span class="nav-font">{{ $t('api_test.request.processor.code_template') }}</span>
      <el-link href="https://jmeter.apache.org/usermanual/component_reference.html#BeanShell_PostProcessor"
               target="componentReferenceDoc" style="margin-left: 30px; margin-bottom: 3px;"
               type="primary"><span style="font-size: 13px;">{{ $t('commons.reference_documentation') }}</span>
      </el-link>
    </div>
    <div v-for="(menu, index) in menus" :key="index">
      <span class="link-type" v-if="!menu.hideScript">
        <i class="icon el-icon-arrow-right" style="font-weight: bold; margin-right: 2px;"
           @click="active(menu)" :class="{'is-active': menu.open}"></i>
        <span @click="active(menu)" class="nav-menu-title nav-font">{{ menu.title }}</span>
      </span>

      <el-collapse-transition>
        <div v-if="menu.open && !menu.hideScript">
          <div v-for="(child, key) in menu.children" :key="key" class="func-div">
            <el-link :disabled="child.disabled" @click="handleClick(child)" class="func-link nav-font">
              {{ child.title }}
            </el-link>
          </div>
        </div>
      </el-collapse-transition>
    </div>
    <custom-function-relate ref="customFunctionRelate" @addCustomFuncScript="handleCodeTemplate"/>
    <!--接口列表-->
    <api-func-relevance @save="apiSave" :is-test-plan="false" :is-script="true" @close="apiClose"
                        ref="apiFuncRelevance"/>
  </div>

</template>

<script>
import ApiFuncRelevance from "@/business/components/project/menu/function/ApiFuncRelevance";
import CustomFunctionRelate from "@/business/components/project/menu/function/CustomFunctionRelate";
import {getCodeTemplate} from "@/business/components/project/menu/function/custom-function";
import {SCRIPT_MENU} from "@/business/components/project/menu/function/script-menu";

export default {
  name: "ScriptNavMenu",
  components: {
    ApiFuncRelevance,
    CustomFunctionRelate
  },
  data() {
    return {
      value: true
    }
  },
  props: {
    language: {
      type: String,
      default() {
        return "beanshell"
      }
    },
    menus: {
      type: Array,
      default() {
        return SCRIPT_MENU
      }
    }
  },
  methods: {
    apiSave(data, env) {
      // data：选中的多个接口定义或多个接口用例; env: 关联页面选中的环境
      let condition = env.config.httpConfig.conditions || [];
      let protocol = "";
      let host = "";
      let domain = "";
      let port = "";
      if (condition && condition.length > 0) {
        // 如果有多个环境，取第一个
        protocol = condition[0].protocol ? condition[0].protocol : "http";
        host = condition[0].socket;
        domain = condition[0].domain;
        port = condition[0].port;
      }
      // todo
      if (data.length > 5) {
        this.$warning("最多可以选择5个接口！");
        return;
      }
      let code = "";
      if (data.length > 0) {
        for (let dt of data) {
          // 过滤非HTTP接口API
          if (dt.protocol !== "HTTP") {
            if (!dt.request) {
              continue;
            } else {
              // 是否是HTTP接口CASE
              if (dt.request) {
                let req = JSON.parse(dt.request);
                if (req.protocol !== "HTTP") {
                  continue;
                }
              }
            }
          }
          let param = this._parseRequestObj(dt);
          param['host'] = host;
          param['domain'] = domain;
          param['port'] = port;
          param['protocol'] = protocol;
          code += '\n' + getCodeTemplate(this.language, param);
        }
      }
      this.handleCodeTemplate(code);
      this.$refs.apiFuncRelevance.close();
    },
    handleCodeTemplate(code) {
      this.$emit("handleCode", code);
    },
    _parseRequestObj(data) {
      let requestHeaders = new Map();
      let requestArguments = new Map();
      let requestRest = new Map();
      let requestMethod = "";
      let requestBody = "";
      let requestBodyKvs = new Map();
      let bodyType = "";
      let requestPath = "";
      let request = JSON.parse(data.request);
      // 拼接发送请求需要的参数
      requestPath = request.path;
      requestMethod = request.method;
      let headers = request.headers;
      let rest = request.rest;
      if (rest && rest.length > 0) {
        rest.forEach(r => {
          if (r.enable) {
            requestRest.set(r.name, r.value);
          }
        })
      }
      if (headers && headers.length > 0) {
        headers.forEach(header => {
          if (header.name) {
            requestHeaders.set(header.name, header.value);
          }
        });
      }
      let args = request.arguments;
      if (args && args.length) {
        args.forEach(arg => {
          if (arg.name) {
            requestArguments.set(arg.name, arg.value);
          }
        })
      }
      let body = request.body;
      if (body.type === 'XML') {
        requestBody = body.raw;
        bodyType = "xml";
      } else if (body.type === 'Raw') {
        requestBody = body.raw;
        bodyType = "raw";
      } else if (body.json) {
        requestBody = body.raw;
        bodyType = "json";
      } else if (body.kvs) {
        bodyType = "kvs";
        body.kvs.forEach(arg => {
          if (arg.name) {
            requestBodyKvs.set(arg.name, arg.value);
          }
        })
      }
      return {
        requestPath,
        requestHeaders,
        requestMethod,
        requestBody,
        requestBodyKvs,
        bodyType,
        requestArguments,
        requestRest
      }
    },
    apiClose() {

    },
    handleClick(obj) {
      let code = "";
      if (obj.command) {
        code = this._handleCommand(obj.command);
        if (!code) {
          return;
        }
      } else {
        // todo 优化
        if (this.language !== 'beanshell' && this.language !== 'groovy') {
          if (obj.title === this.$t('api_test.request.processor.code_add_report_length') ||
            obj.title === this.$t('api_test.request.processor.code_hide_report_length')) {
            this.$warning(this.$t('commons.no_corresponding') + " " + this.language + " " + this.$t('commons.code_template') + "！");
            return;
          }
        }
        code = obj.value;
      }
      this.handleCodeTemplate(code);
    },
    _handleCommand(command) {
      switch (command) {
        case 'custom_function':
          this.$refs.customFunctionRelate.open(this.language);
          return "";
        case 'api_definition':
          this.$refs.apiFuncRelevance.open();
          return "";
        case 'new_api_request': {
          // requestObj为空则生产默认模版
          let headers = new Map();
          headers.set('Content-type', 'application/json');
          return getCodeTemplate(this.language, {requestHeaders: headers});
        }
        default:
          return "";
      }
    },
    active(menu) {
      if (!menu.open) {
        this.$set(menu, "open", true);
      } else {
        this.$set(menu, "open", !menu.open);
      }
    }
  }
}
</script>

<style scoped>

.template-title {
  margin-bottom: 4px;
  font-weight: bold;
  font-size: 15px;
  margin-left: 15px;
}

.func-link {
  margin-left: 18px;
}

.func-div >>> .func-link {
  color: #935aa1;
}

.func-div >>> .func-link:hover {
  color: #935aa1;
}

.link-type {
  font-weight: bold;
  font-size: 14px;
}

.icon.is-active {
  transform: rotate(90deg);
}

.nav-menu-title:hover {
  cursor: pointer;
}

.nav-font {
  font-size: 13px;
}
</style>
