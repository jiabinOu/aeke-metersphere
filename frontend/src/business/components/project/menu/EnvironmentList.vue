<template>
  <ms-container>
    <ms-main-container>
      <div>
        <el-card class="table-card" v-loading="result.loading">
          <!-- 表头 -->
          <template v-slot:header>
            <ms-table-header :create-permission="['PROJECT_ENVIRONMENT:READ+CREATE']" :create-tip="btnTips"
                             :condition.sync="condition" @search="search" @create="createEnv">
              <template v-slot:button>
                <ms-table-button v-permission="['PROJECT_ENVIRONMENT:READ+IMPORT']" icon="el-icon-box"
                                 :content="$t('commons.import')" @click="importJSON"/>
                <ms-table-button v-permission="['PROJECT_ENVIRONMENT:READ+EXPORT']" icon="el-icon-box"
                                 :content="$t('commons.export')" @click="exportJSON"/>
              </template>
            </ms-table-header>
          </template>
          <!-- 环境列表内容 -->
          <el-table border :data="environments"
                    @selection-change="handleSelectionChange" class="adjust-table" style="width: 100%" ref="table"
                    :height="screenHeight"
          >
            <el-table-column type="selection"></el-table-column>
            <el-table-column :label="$t('commons.project')" width="250" column-key="projectId"
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
                  <ms-table-operator :edit-permission="['PROJECT_ENVIRONMENT:READ+EDIT']"
                                     :delete-permission="['PROJECT_ENVIRONMENT:READ+DELETE']"
                                     @editClick="editEnv(scope.row)" @deleteClick="deleteEnv(scope.row)">
                    <template v-slot:middle>
                      <ms-table-operator-button v-permission="['PROJECT_ENVIRONMENT:READ+COPY']"
                                                :tip="$t('commons.copy')"
                                                @exec="copyEnv(scope.row)"
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
        <el-dialog :visible.sync="dialogVisible" :close-on-click-modal="false" width="66%" top="50px" destroy-on-close>
          <template #title>
            <ms-dialog-header :title="dialogTitle" :hide-button="true"
                              @cancel="dialogVisible = false"
                              @confirm="save"/>
          </template>
          <environment-edit :if-create="ifCreate" :environment="currentEnvironment" ref="environmentEdit" @close="close"
                            @confirm="save"
                            :project-id="currentProjectId" @refreshAfterSave="refresh">
          </environment-edit>
        </el-dialog>
        <environment-import :project-list="projectList" @refresh="refresh" ref="envImport"></environment-import>

        <el-dialog title="域名列表" :visible.sync="domainVisible">
          <el-table :data="conditions">
            <el-table-column prop="socket" :label="$t('load_test.domain')" show-overflow-tooltip width="180">
              <template v-slot:default="{row}">
                {{ row.conditionType ? row.server : getUrl(row) }}
              </template>
            </el-table-column>
            <el-table-column :label="'类型'" show-overflow-tooltip
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
            <el-table-column :label="$t('commons.description')" min-width="120px" prop="description"
                             show-overflow-tooltip>
              <template v-slot:default="{row}">
                <span>{{ row.description ? row.description : "-" }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$t('commons.create_time')" min-width="120px" prop="createTime"
                             show-overflow-tooltip>
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
      </div>
    </ms-main-container>
  </ms-container>
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
import {downloadFile, getCurrentProjectID, getUUID} from "@/common/js/utils";
import EnvironmentImport from "@/business/components/project/menu/EnvironmentImport";
import MsMainContainer from "@/business/components/common/components/MsMainContainer";
import MsContainer from "@/business/components/common/components/MsContainer";
import MsDialogHeader from "@/business/components/common/components/MsDialogHeader";

export default {
  name: "EnvironmentList",
  components: {
    MsContainer,
    MsMainContainer,
    EnvironmentImport,
    ProjectSwitch,
    MsAsideContainer,
    MsAsideItem,
    EnvironmentEdit,
    ApiEnvironmentConfig,
    MsTablePagination, MsTableOperatorButton, MsTableOperator, MsTableButton, MsTableHeader, MsDialogHeader
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
      currentProjectId: getCurrentProjectID(),   //复制、创建、编辑环境时所选择项目的id
      selectRows: [],
      isTesterPermission: false,
      domainVisible: false,
      conditions: [],
      currentPage: 1,
      pageSize: 10,
      total: 0,
      projectIds: [],   //当前工作空间所拥有的所有项目id
      projectFilters: [],
      screenHeight: 'calc(100vh - 195px)',
      ifCreate: false, //是否是创建环境
    }
  },
  created() {
  },

  mounted() {
    //跳转环境编辑页面
    if (this.$route && this.$route.query && this.$route.query.resourceId) {
      let id = this.$route.query.resourceId;
      this.$get('/api/environment/get/' + id, response => {
        if (response.data) {
          this.editEnv(response.data);
        } else {
          this.$error(this.$t('api_test.environment.get_env_failed'));
        }
      });
    }
  },

  activated() {
    this.list();
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
    save() {
      this.$refs.environmentEdit.save();
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
          this.projectList = this.projectList.filter(project => project.id === getCurrentProjectID());
          this.projectList.forEach(project => {
            this.idNameMap.set(project.id, project.name);
            this.projectIds.push(project.id);
            this.projectFilters.push({
              text: project.name,
              value: project.id,
            })
          });
          this.getEnvironments();
        })
      } else {
        this.getEnvironments()
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
      })
    },
    createEnv() {
      this.dialogTitle = this.$t('api_test.environment.create');
      this.dialogVisible = true;
      this.currentEnvironment = new Environment();
      this.currentEnvironment.projectId = this.currentProjectId;
      this.ifCreate = true;
    },
    search() {
      this.list()
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

    copyEnv(environment) {
      this.ifCreate = true;
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
        })
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
    // filter(filters) {
    //   this.getEnvironments(filters.projectId)
    // },

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
      })
      downloadFile('MS_' + envs.length + '_Environments.json', JSON.stringify(envs));
    },

    handleProjectChange() {   //项目选择下拉框选择其他项目后清空“启用条件”,因为项目变了模块也就变了。
      this.currentEnvironment.config.httpConfig.conditions = [];
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
    }

  },


}
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
}

.project-item {
  padding-left: 10px;
  padding-right: 20px;
}

.project-item .el-select {
  margin-top: 15px;
}
</style>
