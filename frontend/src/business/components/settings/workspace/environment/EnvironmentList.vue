<template>
  <div>
    <el-card class="table-card" v-loading="result.loading">
      <!-- 表头 -->
      <template v-slot:header>
        <ms-table-header :create-permission="['WORKSPACE_PROJECT_ENVIRONMENT:READ+CREATE']"
                         :create-tip="btnTips"
                         :condition.sync="condition" @search="search" @create="createEnv">
          <template v-slot:button>
            <ms-table-button v-permission="['WORKSPACE_PROJECT_ENVIRONMENT:READ+IMPORT']" icon="el-icon-box"
                             :content="$t('commons.import')" @click="importJSON"/>
            <ms-table-button v-permission="['WORKSPACE_PROJECT_ENVIRONMENT:READ+EXPORT']" icon="el-icon-box"
                             :content="$t('commons.export')" @click="exportJSON"/>
          </template>
        </ms-table-header>
      </template>
      <!-- 环境列表内容 -->
      <el-table border :data="environments" @filter-change="filter"
                @selection-change="handleSelectionChange" class="adjust-table" style="width: 100%" ref="table"
                :height="screenHeight"
                @select-all="handleSelectAll"
                @select="handleSelect"
      >
        <el-table-column type="selection"></el-table-column>
        <el-table-column width="30" min-width="30" :resizable="false" align="center">
          <template v-slot:default="scope">
            <show-more-btn :is-show="scope.row.showMore" :buttons="buttons" :size="selectDataCounts"/>
          </template>
        </el-table-column>
        <el-table-column :label="$t('commons.project')" width="250" :filters="projectFilters" column-key="projectId"
                         show-overflow-tooltip>
          <template v-slot="scope">
            <span>{{ idNameMap.get(scope.row.projectId) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('api_test.environment.name')" prop="name" show-overflow-tooltip>
        </el-table-column>
        <el-table-column :label="$t('api_test.environment.socket')" show-overflow-tooltip>
          <template v-slot="scope">
            <span v-if="parseDomainName(scope.row)!='SHOW_INFO'">{{ parseDomainName(scope.row) }}</span>
            <el-button v-else icon="el-icon-s-data" size="mini" @click="showInfo(scope.row)">
              {{ $t('workspace.env_group.view_details') }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column :label="$t('commons.operating')">
          <template v-slot:default="scope">
            <div>
              <ms-table-operator :edit-permission="['WORKSPACE_PROJECT_ENVIRONMENT:READ+EDIT']"
                                 :delete-permission="['WORKSPACE_PROJECT_ENVIRONMENT:READ+DELETE']"
                                 @editClick="editEnv(scope.row)" @deleteClick="deleteEnv(scope.row)">
                <template v-slot:middle>
                  <ms-table-operator-button v-permission="['WORKSPACE_PROJECT_ENVIRONMENT:READ+COPY']"
                                            :tip="$t('commons.copy')" @exec="copyEnv(scope.row)"
                                            icon="el-icon-document-copy" type="info"/>
                </template>
              </ms-table-operator>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <ms-table-pagination :change="list" :current-page.sync="currentPage" :page-size.sync="pageSize"
                           :total="total"/>
    </el-card>

    <!-- 创建、编辑、复制环境时的对话框 -->
    <el-dialog :visible.sync="dialogVisible" :close-on-click-modal="false" top="50px" width="66%" destroy-on-close>
      <template #title>
        <ms-dialog-header :title="dialogTitle"
                          @cancel="dialogVisible = false"
                          @confirm="save"/>
      </template>
      <el-row>
        <el-col :span="20">
          <el-form label-width="80px" :rules="rules" style="display: flow-root">
            <el-form-item class="project-item" prop="currentProjectId" :label="$t('project.select')">
              <el-select v-model="currentProjectId" filterable clearable :disabled="!ifCreate">
                <el-option v-for="item in projectList" :key="item.id" :label="item.name" :value="item.id"></el-option>
              </el-select>
            </el-form-item>
          </el-form>
        </el-col>
      </el-row>
      <environment-edit :if-create="ifCreate" :environment="currentEnvironment" ref="environmentEdit" @close="close"
                        :hide-button="true"
                        :project-id="currentProjectId" @refreshAfterSave="refresh">
      </environment-edit>
    </el-dialog>
    <environment-import :project-list="projectList" @refresh="refresh" ref="envImport"></environment-import>

    <el-dialog :title="$t('workspace.env_group.domain_list')" :visible.sync="domainVisible">
      <el-table :data="conditions">
        <el-table-column prop="socket" :label="$t('load_test.domain')" show-overflow-tooltip width="180">
          <template v-slot:default="{row}">
            {{ row.conditionType ? row.server : getUrl(row) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('commons.type')" show-overflow-tooltip
                         min-width="100px">
          <template v-slot:default="{row}">
            <el-tag type="info" size="mini">{{ row.conditionType ? row.conditionType : "HTTP" }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="type" :label="$t('api_test.environment.condition_enable')" show-overflow-tooltip
                         min-width="100px">
          <template v-slot:default="{row}">
            {{ row.conditionType ? "-" : getName(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="details" show-overflow-tooltip min-width="120px" :label="$t('api_test.value')">
          <template v-slot:default="{row}">
            {{ row.conditionType ? "-" : getDetails(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="description" show-overflow-tooltip min-width="120px" :label="$t('commons.description')">
          <template v-slot:default="{row}">
            <span>{{ row.description ? row.description : "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" show-overflow-tooltip min-width="120px" :label="$t('commons.create_time')">
          <template v-slot:default="{row}">
            <span v-if="!row.conditionType">{{ row.time | timestampFormatDate }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <span slot="footer" class="dialog-footer">
    <el-button @click="domainVisible = false" size="mini">{{ $t('commons.cancel') }}</el-button>
    <el-button type="primary" @click="domainVisible = false" size="mini">{{ $t('commons.confirm') }}</el-button>
  </span>
    </el-dialog>
    <env-group-cascader :title="$t('workspace.env_group.batch_add_to_ws')" ref="cascader" @confirm="_batchAddToGroup"/>
  </div>
</template>

<script>
import MsTableHeader from "@/business/components/common/components/MsTableHeader";
import MsTableButton from "@/business/components/common/components/MsTableButton";
import MsTableOperator from "@/business/components/common/components/MsTableOperator";
import MsTableOperatorButton from "@/business/components/common/components/MsTableOperatorButton";
import MsTablePagination from "@/business/components/common/pagination/TablePagination";
import ApiEnvironmentConfig from "@/business/components/api/test/components/ApiEnvironmentConfig";
import {Environment, parseEnvironment} from "@/business/components/api/test/model/EnvironmentModel";
import EnvironmentEdit from "@/business/components/api/test/components/environment/EnvironmentEdit";
import MsAsideItem from "@/business/components/common/components/MsAsideItem";
import MsAsideContainer from "@/business/components/common/components/MsAsideContainer";
import ProjectSwitch from "@/business/components/common/head/ProjectSwitch";
import {downloadFile, getUUID, strMapToObj} from "@/common/js/utils";
import EnvironmentImport from "@/business/components/project/menu/EnvironmentImport";
import ShowMoreBtn from "@/business/components/track/case/components/ShowMoreBtn";
import {_handleSelect, _handleSelectAll, getSelectDataCounts, setUnSelectIds} from "@/common/js/tableUtils";
import EnvGroupCascader from "@/business/components/settings/workspace/environment/EnvGroupCascader";
import MsDialogHeader from "../../../common/components/MsDialogHeader";

export default {
  name: "EnvironmentList",
  components: {
    EnvGroupCascader,
    EnvironmentImport,
    ProjectSwitch,
    MsAsideContainer,
    MsAsideItem,
    EnvironmentEdit,
    ApiEnvironmentConfig,
    MsTablePagination,
    MsTableOperatorButton,
    MsTableOperator,
    MsTableButton,
    MsTableHeader,
    ShowMoreBtn,
    MsDialogHeader
  },
  data() {
    return {
      btnTips: this.$t('api_test.environment.create'),
      projectList: [],
      condition: {},   //封装传递给后端的查询条件
      environments: [],
      currentEnvironment: new Environment(),
      result: {},
      dialogVisible: false,
      idNameMap: new Map(),
      dialogTitle: '',
      currentProjectId: '',   //复制、创建、编辑环境时所选择项目的id
      selectRows: [],
      selectRow: new Set(),
      isTesterPermission: false,
      domainVisible: false,
      conditions: [],
      currentPage: 1,
      pageSize: 10,
      total: 0,
      projectIds: [],   //当前工作空间所拥有的所有项目id
      projectFilters: [],
      screenHeight: 'calc(100vh - 210px)',
      rules: {
        currentProjectId: [
          {required: true, message: "", trigger: 'blur'},
        ],
      },
      selectDataCounts: 0,
      buttons: [
        {
          name: this.$t('workspace.env_group.batch_add_to_ws'), handleClick: this.batchAddToGroup
        },
      ],
      ifCreate: false, //是否是创建环境
    };
  },
  created() {
    this.list();
  },

  activated() {
    this.list();
  },

  watch: {
    //当创建及复制环境所选择的项目变化时，改变当前环境对应的projectId
    currentProjectId() {
      // el-select什么都不选时值为''，为''的话也会被当成有效的projectId传给后端，转化使其无效
      if (this.currentProjectId === '') {
        this.currentEnvironment.projectId = null;
      } else {
        this.currentEnvironment.projectId = this.currentProjectId;
      }
    }

  },

  methods: {
    showInfo(row) {
      const config = JSON.parse(row.config);
      this.conditions = config.httpConfig.conditions;
      if (config.tcpConfig && config.tcpConfig.server) {
        let condition = {
          conditionType: 'TCP',
          server: config.tcpConfig.server,
          description: config.tcpConfig.description
        }
        this.conditions.push(condition);
      }
      this.domainVisible = true;
    },
    getName(row) {
      switch (row.type) {
        case "NONE":
          return this.$t("api_test.definition.document.data_set.none");
        case "MODULE":
          return this.$t("test_track.module.module");
        case "PATH":
          return this.$t("api_test.definition.api_path");
      }
    },
    getUrl(row) {
      return row.protocol + "://" + row.socket;
    },
    getDetails(row) {
      if (row && row.type === "MODULE") {
        if (row.details && row.details instanceof Array) {
          let value = "";
          row.details.forEach((item) => {
            value += item.name + ",";
          });
          if (value.endsWith(",")) {
            value = value.substr(0, value.length - 1);
          }
          return value;
        }
      } else if (row && row.type === "PATH" && row.details.length > 0 && row.details[0].name) {
        return row.details[0].value === "equals" ? this.$t("commons.adv_search.operators.equals") + row.details[0].name : this.$t("api_test.request.assertions.contains") + row.details[0].name;
      } else {
        return "";
      }
    },
    list() {
      if (!this.projectList || this.projectList.length === 0) {   //没有项目数据的话请求项目数据
        this.$get("/project/listAll", (response) => {
          this.projectList = response.data;  //获取当前工作空间所拥有的项目,
          this.projectFilters = [];
          this.projectList.forEach(project => {
            this.idNameMap.set(project.id, project.name);
            this.projectIds.push(project.id);
            this.projectFilters.push({
              text: project.name,
              value: project.id,
            });
          });
          this.getEnvironments(this.condition.projectIds);
        });
      } else {
        this.getEnvironments(this.condition.projectIds);
      }
    },
    getEnvironments(projectIds) {
      this.environments = [];
      if (projectIds && projectIds.length > 0) {
        this.condition.projectIds = projectIds;
      } else {
        this.condition.projectIds = this.projectIds;
      }
      let url = '/api/environment/list/' + this.currentPage + '/' + this.pageSize;
      this.result = this.$post(url, this.condition, response => {
        this.environments = response.data.listObject;
        this.total = response.data.itemCount;
      });
    },
    createEnv() {
      this.currentProjectId = '';
      this.dialogTitle = this.$t('api_test.environment.create');
      this.dialogVisible = true;
      this.ifCreate = true;
      this.currentEnvironment = new Environment();
    },
    search() {
      this.list();
    },
    editEnv(environment) {
      this.dialogTitle = this.$t('api_test.environment.config_environment');
      this.currentProjectId = environment.projectId;
      const temEnv = {};
      Object.assign(temEnv, environment);
      parseEnvironment(temEnv);   //parseEnvironment会改变环境对象的内部结构，从而影响前端列表的显示，所以复制一个环境对象作为代替
      this.currentEnvironment = temEnv;
      this.dialogVisible = true;
      this.ifCreate = false;
    },
    save() {
      this.$refs.environmentEdit.save();
    },
    copyEnv(environment) {
      this.currentProjectId = environment.projectId;  //复制时默认选择所要复制环境对应的项目
      this.dialogTitle = this.$t('api_test.environment.copy_environment');
      const temEnv = {};
      Object.assign(temEnv, environment);
      parseEnvironment(temEnv);   //parseEnvironment会改变环境对象的内部结构，从而影响前端列表的显示，所以复制一个环境对象作为代替
      let newEnvironment = new Environment(temEnv);
      newEnvironment.id = null;
      newEnvironment.config.databaseConfigs.forEach(dataSource => {
        if (dataSource.id) {
          dataSource.id = getUUID();
        }
      })
      newEnvironment.name = this.getNoRepeatName(newEnvironment.name);
      this.dialogVisible = true;
      this.currentEnvironment = newEnvironment;

    },
    deleteEnv(environment) {
      if (environment.id) {
        this.$confirm(this.$t('commons.confirm_delete') + environment.name, {
          confirmButtonText: this.$t('commons.confirm'),
          cancelButtonText: this.$t('commons.cancel'),
          type: "warning"
        }).then(() => {
          this.result = this.$get('/api/environment/delete/' + environment.id, () => {
            this.$success(this.$t('commons.delete_success'));
            this.list();
          });
        }).catch(() => {
          this.$info(this.$t('commons.delete_cancelled'));
        });
      }
    },
    getNoRepeatName(name) {
      for (let i in this.environments) {
        if (this.environments[i].name === name) {
          return this.getNoRepeatName(name + ' copy');
        }
      }
      return name;
    },

    //筛选指定项目下的环境
    filter(filters) {
      this.getEnvironments(filters.projectId);
    },

    //对话框取消按钮
    close() {
      this.dialogVisible = false;
      this.$refs.environmentEdit.clearValidate();
    },
    refresh() {
      this.list();
    },
    handleSelectionChange(value) {
      this.selectRows = value;
    },
    importJSON() {
      this.$refs.envImport.open();
    },
    exportJSON() {
      if (this.selectRows.length < 1) {
        this.$warning(this.$t('api_test.environment.select_environment'));
        return;
      }
      //拷贝一份选中的数据，不然下面删除id和projectId的时候会影响原数据
      const envs = JSON.parse(JSON.stringify(this.selectRows));
      envs.map(env => {  //不导出id和projectId和模块启用条件
        if (env.config) {  //旧环境可能没有config数据
          let tempConfig = JSON.parse(env.config);
          if (tempConfig.httpConfig.conditions && tempConfig.httpConfig.conditions.length > 0) {
            tempConfig.httpConfig.conditions.map(condition => {
              if (condition.type === 'MODULE') {
                condition.details = [];
              }
            })
            env.config = JSON.stringify(tempConfig);
          }
        }
        delete env.id;
        delete env.projectId;
      });
      downloadFile('MS_' + envs.length + '_Environments.json', JSON.stringify(envs));
    },

    parseDomainName(environment) {   //解析出环境域名用于前端展示
      if (environment.config) {
        const config = JSON.parse(environment.config);
        if (config.httpConfig && !config.httpConfig.conditions) {
          if (config.httpConfig.protocol && config.httpConfig.domain) {
            return config.httpConfig.protocol + "://" + config.httpConfig.domain;
          }
          return "";
        } else {
          if (config.httpConfig.conditions.length === 1) {
            if (config.tcpConfig && config.tcpConfig.server) {
              return "SHOW_INFO";
            }
            let obj = config.httpConfig.conditions[0];
            if (obj.protocol && obj.socket) {
              return obj.protocol + "://" + obj.socket;
            }
          } else if (config.httpConfig.conditions.length > 1) {
            return "SHOW_INFO";
          } else if (config.tcpConfig && config.tcpConfig.server) {
            return config.tcpConfig.server;
          } else {
            return "";
          }
        }
      } else {  //旧版本没有对应的config数据,其域名保存在protocol和domain中
        return environment.protocol + '://' + environment.domain;
      }
    },
    batchAddToGroup() {
      this.$refs.cascader.open();
    },
    handleSelectAll(selection) {
      _handleSelectAll(this, selection, this.environments, this.selectRow, this.condition);
      setUnSelectIds(this.environments, this.condition, this.selectRow);
      this.selectDataCounts = getSelectDataCounts(this.condition, this.total, this.selectRow);
      this.$emit('selection', selection);
    },
    handleSelect(selection, row) {
      _handleSelect(this, selection, row, this.selectRow);
      setUnSelectIds(this.environments, this.condition, this.selectRow);
      this.selectDataCounts = getSelectDataCounts(this.condition, this.total, this.selectRow);
      this.$emit('selection', selection);
    },
    _batchAddToGroup(value) {
      let sign = this.checkRepeat();
      if (!sign) {
        return false;
      }
      let map = new Map();
      this.selectRow.forEach(row => {
        map.set(row.projectId, row.id);
      })
      this.$post("/environment/group/batch/add", {map: strMapToObj(map), groupIds: value}, () => {
        this.$success(this.$t('commons.save_success'));
        this.getEnvironments();
      })
    },
    checkRepeat() {
      let projectArr = [];
      this.selectRow.forEach(row => {
        projectArr.push(row.projectId)
      })
      let repeatArr = this.duplicates(projectArr);
      let str = "";
      repeatArr.forEach(id => {
        const project = this.projectList.find(p => p.id === id);
        if (project) {
          str += project.name + " ";
        }
      })
      if (str) {
        this.$warning(str + this.$t('workspace.env_group.choice_conflict'));
        return false;
      }
      return true;
    },
    duplicates(arr) {
      let a = arr.sort(), b = [];
      for (let i in a) {
        if (a[i] === a[i - 1] && b.indexOf(a[i]) === -1) b.push(a[i]);
      }
      return b;
    }
  },


};
</script>

<style scoped>
.item-bar {
  width: 100%;
  background: #F9F9F9;
  padding: 5px 10px;
  box-sizing: border-box;
  border: solid 1px #e6e6e6;
}

.item-selected {
  background: #ECF5FF;
  border-left: solid #409EFF 5px;
}

.item-selected .item-right {
  visibility: visible;
}

.environment-edit {
  margin-left: 0;
  width: 100%;
  border: 0;
  min-height: 400px;
  max-height: 450px;
}

.project-item {
  padding-left: 10px;
  padding-right: 10px;
}

.project-item .el-select {
  width: 100%;
}
</style>
