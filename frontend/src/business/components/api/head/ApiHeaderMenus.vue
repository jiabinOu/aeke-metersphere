<template>
  <div id="menu-bar" v-if="isRouterAlive">
    <el-row type="flex">
      <project-change :project-name="currentProject"/>
      <el-col :span="14">
        <el-menu class="header-menu" :unique-opened="true" mode="horizontal" router :default-active='currentPath'>

          <el-menu-item :index="'/api/home'">
            {{ $t("i18n.home") }}
          </el-menu-item>
          <el-menu-item :index="'/api/definition'" v-permission="['PROJECT_API_DEFINITION:READ']">
            {{ $t("i18n.definition") }}
          </el-menu-item>

          <el-menu-item :index="'/api/automation'" v-permission="['PROJECT_API_SCENARIO:READ']">
            {{ $t("i18n.automation") }}
          </el-menu-item>

          <el-menu-item :index="'/api/automation/report'" v-permission="['PROJECT_API_REPORT:READ']">
            {{ $t("i18n.report") }}
          </el-menu-item>
        </el-menu>
      </el-col>
    </el-row>
  </div>

</template>

<script>

import MsRecentList from "../../common/head/RecentList";
import MsShowAll from "../../common/head/ShowAll";
import MsCreateButton from "../../common/head/CreateButton";
import ProjectChange from "@/business/components/common/head/ProjectSwitch";
import {PROJECT_ID, PROJECT_NAME, WORKSPACE, WORKSPACE_ID} from "@/common/js/constants";

export default {
  name: "MsApiHeaderMenus",
  components: {MsCreateButton, MsShowAll, MsRecentList, ProjectChange},
  data() {
    return {
      currentPath: '',
      isRouterAlive: true,
      apiTestProjectPath: '',
      currentProject: ''
    };
  },
  watch: {
    '$route': {
      immediate: true,
      handler(to) {
        if (to.name==='ApiAutomation') {
          if (to.query.projectName && to.query.projectId) {
            sessionStorage.setItem(PROJECT_NAME, to.query.projectName);
            sessionStorage.setItem(PROJECT_ID, to.query.projectId);
          }
          if (to.query.workspaceName && to.query.workspaceId) {
            sessionStorage.setItem(WORKSPACE, to.query.workspaceName);
            sessionStorage.setItem(WORKSPACE_ID, to.query.workspaceId);
          }
        }
        let path = to.path.split("/", 4);
        this.currentPath = '/' + path[1] + '/' + path[2];
        if (path[3] === "report") {
          this.currentPath = this.currentPath + '/' + path[3];
        }
      }
    }
  },
  methods: {
    reload() {
      this.isRouterAlive = false;
      this.$nextTick(function () {
        this.isRouterAlive = true;
      });
    },
  },
  mounted() {

  },
  beforeDestroy() {

  }
};

</script>

<style scoped>
#menu-bar {
  border-bottom: 1px solid #E6E6E6;
  background-color: #FFF;
}

.menu-divider {
  margin: 0;
}

.blank_item {
  display: none;
}

.deactivation >>> .el-submenu__title {
  border-bottom: white !important;
}

.el-menu-item {
  padding: 0 10px;
}

</style>
