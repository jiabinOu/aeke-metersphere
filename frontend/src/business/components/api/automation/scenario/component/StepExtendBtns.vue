<template>
  <div>
    <el-dropdown @command="handleCommand" class="scenario-ext-btn">
      <el-link type="primary" :underline="false">
        <el-icon class="el-icon-more"></el-icon>
      </el-link>
      <el-dropdown-menu slot="dropdown">
        <el-dropdown-item command="copy" v-if="data.command">{{ this.$t('commons.copy') }}</el-dropdown-item>
        <el-dropdown-item command="enable" v-if="data.command && data.enable">{{
            this.$t('ui.disable')
          }}
        </el-dropdown-item>
        <el-dropdown-item command="enable" v-if="data.command && !data.enable">{{
            this.$t('ui.enable')
          }}
        </el-dropdown-item>
        <el-dropdown-item command="remove">{{ this.$t('api_test.automation.delete_step') }}</el-dropdown-item>
        <el-dropdown-item command="rename" v-if="!isScenario">{{
            this.$t('test_track.module.rename')
          }}
        </el-dropdown-item>
        <el-dropdown-item command="scenarioVar" v-if="data.type==='scenario'">
          {{ this.$t("api_test.automation.view_scene_variables") }}
        </el-dropdown-item>
        <el-dropdown-item command="openScenario" v-if="data.type==='scenario' && data.referenced==='REF'">
          {{ this.$t("api_test.automation.open_scene") }}
        </el-dropdown-item>
        <el-dropdown-item command="saveAs"
                          v-if="allSamplers.indexOf(data.type)!=-1 && (data.referenced===undefined || data.referenced ==='Created' )">
          {{ this.$t("api_test.automation.save_as_api") }}
        </el-dropdown-item>
        <el-dropdown-item command="setScenario" v-if="data.type==='scenario'">
          {{ $t('commons.reference_settings') }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </el-dropdown>
    <ms-variable-list ref="scenarioParameters" @setVariables="setVariables"/>
    <ms-add-basis-api :currentProtocol="currentProtocol" ref="api"/>


    <el-dialog
      :title="$t('commons.reference_settings')"
      :visible.sync="dialogVisible" width="700px">
      <ul>
        <el-tooltip :content="$t('commons.enable_scene_info')" placement="top" v-if='showEnableScence'>
          <el-checkbox v-model="data.environmentEnable" @change="checkEnv" :disabled="data.disabled">
            {{ $t('commons.enable_scene') }}
          </el-checkbox>
        </el-tooltip>
        <br/>
        <el-checkbox v-model="data.variableEnable" :disabled="data.disabled" @change="variableChange">
          {{ $t('commons.variable_scene') }}
        </el-checkbox>
        <br/>
        <el-checkbox v-model="data.mixEnable" :disabled="data.disabled" @change="mixChange">
          {{ $t('commons.mix_enable') }}
        </el-checkbox>
      </ul>
    </el-dialog>

  </div>
</template>

<script>
import {STEP} from "../Setting";
import MsVariableList from "../variable/VariableList";
import MsAddBasisApi from "../api/AddBasisApi";
import {getCurrentProjectID, getUUID, strMapToObj} from "@/common/js/utils";

export default {
  name: "StepExtendBtns",
  components: {STEP, MsVariableList, MsAddBasisApi},
  props: {
    isScenario: {
      type: Boolean,
      default() {
        return false;
      }
    },
    showEnableScence: {
      type: Boolean,
      default() {
        return true;
      }
    },
    data: Object,
    environmentType: String,
    environmentGroupId: String,
    envMap: Map,
  },
  data() {
    return {
      allSamplers: [],
      currentProtocol: "HTTP",
      filter: new STEP,
      dialogVisible: false,
    }
  },
  mounted() {
    this.allSamplers = this.filter.get('DEFINITION');
  },
  methods: {
    variableChange() {
      if (this.data.variableEnable) {
        this.data.mixEnable = false;
      }
    },
    mixChange() {
      if (this.data.mixEnable) {
        this.data.variableEnable = false;
      }
    },
    handleCommand(cmd) {
      switch (cmd) {
        case  "copy":
          this.$emit('copy');
          break;
        case "remove":
          this.$emit('remove');
          break;
        case "scenarioVar":
          this.$refs.scenarioParameters.open(this.data.variables, this.data.headers, this.data.referenced === 'REF');
          break;
        case "openScenario":
          this.getScenario();
          break;
        case "saveAs":
          this.saveAsApi();
          break;
        case "setScenario":
          this.setScenario();
          break;
        case "enable":
          this.$emit("enable");
          break;
        case "rename":
          this.$emit("rename");
          break;
      }
    },
    setVariables(v, h) {
      this.data.variables = v;
    },
    setScenario() {
      this.dialogVisible = true;
    },
    getScenario() {
      this.result = this.$get("/api/automation/getApiScenario/" + this.data.id, response => {
        if (response.data) {
          if (response.data.projectId === getCurrentProjectID()) {
            this.$emit('openScenario', response.data);
          } else {
            let automationData = this.$router.resolve({
              name: 'ApiAutomation',
              params: {redirectID: getUUID(), dataType: "scenario", dataSelectRange: 'edit:' + response.data.id}
            });
            window.open(automationData.href, '_blank');
          }
        } else {
          this.$error("引用场景已经被删除");
        }
      });
    },
    checkEnv(val) {
      this.$get("/api/automation/checkScenarioEnv/" + this.data.id, res => {
        if (this.data.environmentEnable && !res.data) {
          this.data.environmentEnable = false;
          this.$warning(this.$t('commons.scenario_warning'));
          return;
        }
        this.setDomain(val);
      });
    },
    setDomain(val) {
      let param = {
        environmentEnable: val,
        id: this.data.id,
        environmentType: this.environmentType,
        environmentGroupId: this.environmentGroupId,
        environmentMap: strMapToObj(this.envMap),
        definition: JSON.stringify(this.data)
      }
      this.$post("/api/automation/setDomain", param, res => {
        if (res.data) {
          let data = JSON.parse(res.data);
          this.data.hashTree = data.hashTree;
          this.setOwnEnvironment(this.data.hashTree);
        }
      })
    },
    setOwnEnvironment(scenarioDefinition) {
      for (let i in scenarioDefinition) {
        let typeArray = ["JDBCPostProcessor", "JDBCSampler", "JDBCPreProcessor"]
        if (typeArray.indexOf(scenarioDefinition[i].type) !== -1) {
          scenarioDefinition[i].environmentEnable = this.data.environmentEnable;
          scenarioDefinition[i].refEevMap = new Map();
          if (this.data.environmentEnable && this.data.environmentMap) {
            scenarioDefinition[i].refEevMap = this.data.environmentMap;
          }
        }
        if (scenarioDefinition[i].hashTree !== undefined && scenarioDefinition[i].hashTree.length > 0) {
          this.setOwnEnvironment(scenarioDefinition[i].hashTree);
        }
      }
    },
    saveAsApi() {
      this.currentProtocol = this.data.protocol;
      this.data.customizeReq = false;
      this.$refs.api.open(this.data);
    }
  }
}
</script>

<style scoped>
.scenario-ext-btn {
  margin-left: 10px;
}
</style>
