<template>
  <div>
    <div v-if="request.protocol === 'HTTP'">
      <div v-if="isCustomizeReq">
        <el-select v-model="request.method" class="ms-select" size="small" :disabled="request.disabled">
          <el-option v-for="item in reqOptions" :key="item.id" :label="item.label" :value="item.id"/>
        </el-select>
        <el-input v-model="request.domain" v-if="request.isRefEnvironment  && request.domain" size="small" readonly
                  class="ms-input"/>

        <el-input :placeholder="$t('api_test.definition.request.path_all_info')" v-model="request.path"
                  style="width: 50%" size="small" @blur="urlChange" :disabled="request.disabled"
                  v-if="request.isRefEnvironment"/>

        <el-input :placeholder="$t('api_test.definition.request.path_all_info')" v-model="request.url"
                  style="width: 50%" size="small" @blur="urlChange" :disabled="request.disabled" v-else/>
        <el-checkbox v-if="isCustomizeReq" class="is-ref-environment" v-model="request.isRefEnvironment"
                     @change="setDomain" :disabled="request.disabled">
          {{ $t('api_test.request.refer_to_environment') }}
        </el-checkbox>
      </div>
      <div v-else>
        <el-select v-model="request.method" class="ms-select" size="small" :disabled="request.disabled">
          <el-option v-for="item in reqOptions" :key="item.id" :label="item.label" :value="item.id"/>
        </el-select>
        <el-input v-model="request.domain" v-if="request.domain" size="small" readonly class="ms-input"
                  :disabled="request.disabled"/>
        <el-input :placeholder="$t('api_test.definition.request.path_all_info')" style="width: 50%"
                  v-model="request.path" size="small" @blur="pathChange" :disabled="request.disabled"/>
      </div>
    </div>

    <div v-if="request.protocol === 'TCP' && isCustomizeReq">
      <el-form>
        <el-row>
          <el-col :span="8">
            <el-form-item :label="$t('api_test.request.tcp.server')" prop="server">
              <el-input class="server-input" v-model="request.server" maxlength="300" show-word-limit size="small"/>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('api_test.request.tcp.port')" prop="port" label-width="60px">
              <el-input v-model="request.port" size="small"/>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </div>
  </div>
</template>

<script>
import {REQ_METHOD} from "@/business/components/api/definition/model/JsonData";
import {KeyValue} from "../../../definition/model/ApiTestModel";

export default {
  name: "CustomizeReqInfo",
  props: ['request', 'isCustomizeReq'],
  data() {
    return {
      reqOptions: REQ_METHOD,
      isUrl: false,
    }
  },
  methods: {
    pathChange() {
      this.isUrl = false;
      if (!this.request.path || this.request.path.indexOf('?') === -1) return;
      let url = this.getURL(this.addProtocol(this.request.path));
      if (url) {
        this.request.path = decodeURIComponent(this.request.path.substr(0, this.request.path.indexOf("?")));
      }
    },
    urlChange() {
      this.isUrl = false;
      if (this.request.isRefEnvironment) {
        this.pathChange();
      } else {
        if (!this.request.url || this.request.url.indexOf('?') === -1) return;
        let url = this.getURL(this.addProtocol(this.request.url));
        if (url) {
          let paramUrl = this.request.url.substr(this.request.url.indexOf("?") + 1);
          if (paramUrl) {
            this.request.url = decodeURIComponent(this.request.url.substr(0, this.request.url.indexOf("?")));
          }
        }
      }
    },
    addProtocol(url) {
      if (url) {
        if (!url.toLowerCase().startsWith("https") && !url.toLowerCase().startsWith("http")) {
          return "https://" + url;
        }
      }
      return url;
    },
    getURL(urlStr) {
      try {
        let url = new URL(urlStr);
        if (url.search && url.search.length > 1) {
          let params = url.search.substr(1).split("&");
          params.forEach(param => {
            if (param) {
              let keyValues = param.split("=");
              if (keyValues) {
                this.isUrl = true;
                this.request.arguments.splice(0, 0, new KeyValue({
                  name: keyValues[0],
                  required: false,
                  value: keyValues[1]
                }));
              }
            }
          });
        }
        return url;
      } catch (e) {
        this.$error(this.$t('api_test.request.url_invalid'), 2000);
      }
    },
    setDomain() {
      let urlStr = this.addProtocol(this.request.url);
      const reg = /http(s)?:\/\/([\w-]+\.)+[\w-]+(\/[\w- .\/?%&=]*)?/;
      if (reg.test(urlStr)) {
        let url = this.getURL(urlStr);
        if (url && url.pathname) {
          this.request.path = url.pathname;
        }
      } else {
        this.request.path = this.request.url
      }
      this.$emit("setDomain");
    }
  }
}
</script>

<style scoped>
.server-input {
  width: 50%;
}

.scenario-step-request-name {
  display: inline-block;
  margin: 0 5px;
  overflow-x: hidden;
  padding-bottom: 0;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
  width: 120px;
}

.ms-select {
  width: 100px;
  margin-right: 10px;
}

.ms-input {
  width: 150px;
  margin-right: 10px;
}

.is-ref-environment {
  margin-left: 15px;
}
</style>
