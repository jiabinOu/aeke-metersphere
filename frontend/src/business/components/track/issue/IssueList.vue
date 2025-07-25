<template>
  <ms-container>
    <ms-main-container>

      <el-card class="table-card">
        <template v-slot:header>
          <ms-table-header :create-permission="['PROJECT_TRACK_ISSUE:READ+CREATE']" :condition.sync="page.condition" @search="search" @create="handleCreate"
                           :create-tip="$t('test_track.issue.create_issue')"
                           :tip="$t('commons.search_by_name_or_id')">
            <template v-slot:button>
              <el-tooltip v-if="isThirdPart" :content="$t('test_track.issue.update_third_party_bugs')">
                <ms-table-button icon="el-icon-refresh" v-if="true"
                                 :content="$t('test_track.issue.sync_bugs')" @click="syncIssues"/>
              </el-tooltip>
            </template>
          </ms-table-header>
        </template>

        <ms-table
          v-loading="page.result.loading"
          :data="page.data"
          :enableSelection="false"
          :condition="page.condition"
          :total="page.total"
          :page-size.sync="page.pageSize"
          :operators="operators"
          :show-select-all="false"
          :screen-height="screenHeight"
          :remember-order="true"
          @handlePageChange="getIssues"
          :fields.sync="fields"
          :field-key="tableHeaderKey"
          @order="getIssues"
          @filter="search"
          :custom-fields="issueTemplate.customFields"
          ref="table"
        >
    <span v-for="(item) in fields" :key="item.key">
        <ms-table-column width="1">
        </ms-table-column>
          <ms-table-column
            :label="$t('test_track.issue.id')"
            prop="num"
            :field="item"
            sortable
            min-width="100"
            :fields-width="fieldsWidth">
          </ms-table-column>

          <ms-table-column
            :field="item"
            :fields-width="fieldsWidth"
            :label="$t('test_track.issue.title')"
            sortable
            min-width="110"
            prop="title">
          </ms-table-column>

          <ms-table-column
            :field="item"
            :fields-width="fieldsWidth"
            :filters="platformFilters"
            :label="$t('test_track.issue.platform')"
            min-width="80"
            prop="platform">
          </ms-table-column>

          <ms-table-column
                  :field="item"
                  :fields-width="fieldsWidth"
                  sortable
                  min-width="110"
                  :label="$t('test_track.issue.platform_status') "
                  prop="platformStatus">
            <template v-slot="scope">
              <span v-if="scope.row.platform ==='Zentao'">{{ scope.row.platformStatus ? issueStatusMap[scope.row.platformStatus] : '--'}}</span>
              <span v-else-if="scope.row.platform ==='Tapd'">{{ scope.row.platformStatus ? tapdIssueStatusMap[scope.row.platformStatus] : '--'}}</span>
              <span v-else>{{ scope.row.platformStatus ? scope.row.platformStatus : '--'}}</span>
            </template>
          </ms-table-column>

          <ms-table-column
            :field="item"
            :fields-width="fieldsWidth"
            column-key="creator"
            :filters="creatorFilters"
            sortable
            min-width="100px"
            :label="$t('custom_field.issue_creator')"
            prop="creatorName">
          </ms-table-column>

          <ms-table-column
            :field="item"
            :fields-width="fieldsWidth"
            :label="$t('test_track.issue.issue_resource')"
            prop="resourceName">
            <template v-slot="scope">
              <el-link v-if="scope.row.resourceName" @click="$router.push('/track/plan/view/' + scope.row.resourceId)">
                {{ scope.row.resourceName }}
              </el-link>
              <span v-else>
              --
            </span>
            </template>
          </ms-table-column>
        <ms-table-column prop="createTime"
                       :field="item"
                       :fields-width="fieldsWidth"
                       :label="$t('commons.create_time')"
                       sortable
                       min-width="180px">
            <template v-slot:default="scope">
              <span>{{ scope.row.createTime | timestampFormatDate }}</span>
            </template>
          </ms-table-column >

          <issue-description-table-item :fields-width="fieldsWidth" :field="item"/>

         <ms-table-column
           :field="item"
           :fields-width="fieldsWidth"
           :label="item.label"
           prop="caseCount">
            <template v-slot="scope">
               <router-link :to="scope.row.caseCount > 0 ? {name: 'testCase', params: { projectId: 'all', ids: scope.row.caseIds }} : {}">
                 {{scope.row.caseCount}}
               </router-link>
            </template>
         </ms-table-column>

          <ms-table-column v-for="field in issueTemplate.customFields" :key="field.id"
                           :field="item"
                           :fields-width="fieldsWidth"
                           :label="field.system ? $t(systemNameMap[field.name]) :field.name"
                           :prop="field.name">
              <template v-slot="scope">
                <span v-if="field.name === '状态'">
                  {{getCustomFieldValue(scope.row, field, issueStatusMap[scope.row.status])}}
                </span>
                <span v-else-if="field.type === 'richText'">
                   <el-popover
                     placement="right"
                     width="500"
                     trigger="hover"
                     popper-class="issues-popover">
                     <ms-mark-down-text prop="value" :data="{value: getCustomFieldValue(scope.row, field)}" :disabled="true"/>
                    <el-button slot="reference" type="text">{{ $t('test_track.issue.preview') }}</el-button>
                  </el-popover>
                </span>
                <span v-else>
                  {{getCustomFieldValue(scope.row, field)}}
                </span>
              </template>
          </ms-table-column>

        </span>
        </ms-table>

        <ms-table-pagination :change="getIssues" :current-page.sync="page.currentPage" :page-size.sync="page.pageSize"
                             :total="page.total"/>

        <issue-edit @refresh="getIssues" ref="issueEdit"/>
      </el-card>
    </ms-main-container>
  </ms-container>
</template>

<script>
import MsTable from "@/business/components/common/components/table/MsTable";
import MsTableColumn from "@/business/components/common/components/table/MsTableColumn";
import MsTableOperators from "@/business/components/common/components/MsTableOperators";
import MsTableButton from "@/business/components/common/components/MsTableButton";
import MsTablePagination from "@/business/components/common/pagination/TablePagination";
import {
  ISSUE_PLATFORM_OPTION,
  ISSUE_STATUS_MAP,
  SYSTEM_FIELD_NAME_MAP,
  TAPD_ISSUE_STATUS_MAP
} from "@/common/js/table-constants";
import MsTableHeader from "@/business/components/common/components/MsTableHeader";
import IssueDescriptionTableItem from "@/business/components/track/issue/IssueDescriptionTableItem";
import IssueEdit from "@/business/components/track/issue/IssueEdit";
import {checkSyncIssues, getIssuePartTemplateWithProject, getIssues, syncIssues} from "@/network/Issue";
import {
  getCustomFieldValue,
  getCustomTableWidth,
  getPageInfo, getTableHeaderWithCustomFields, getLastTableSortField
} from "@/common/js/tableUtils";
import MsContainer from "@/business/components/common/components/MsContainer";
import MsMainContainer from "@/business/components/common/components/MsMainContainer";
import {getCurrentProjectID, getCurrentWorkspaceId} from "@/common/js/utils";
import {getProjectMember} from "@/network/user";
import {LOCAL} from "@/common/js/constants";
import MsMarkDownText from "@/business/components/track/case/components/MsMarkDownText";

export default {
  name: "IssueList",
  components: {
    MsMarkDownText,
    MsMainContainer,
    MsContainer,
    IssueEdit,
    IssueDescriptionTableItem,
    MsTableHeader,
    MsTablePagination, MsTableButton, MsTableOperators, MsTableColumn, MsTable
  },
  data() {
    return {
      page: getPageInfo(),
      fields: [],
      tableHeaderKey:"ISSUE_LIST",
      fieldsWidth: getCustomTableWidth('ISSUE_LIST'),
      screenHeight: 'calc(100vh - 200px)',
      operators: [
        {
          tip: this.$t('commons.edit'), icon: "el-icon-edit",
          exec: this.handleEdit,
          permissions: ['PROJECT_TRACK_ISSUE:READ+EDIT']
        }, {
          tip: this.$t('commons.copy'), icon: "el-icon-copy-document", type: "success",
          exec: this.handleCopy,
          isDisable: this.btnDisable,
          permissions: ['PROJECT_TRACK_ISSUE:READ+CREATE']
        }, {
          tip: this.$t('commons.delete'), icon: "el-icon-delete", type: "danger",
          exec: this.handleDelete,
          permissions: ['PROJECT_TRACK_ISSUE:READ+DELETE']
        }
      ],
      issueTemplate: {},
      members: [],
      isThirdPart: false,
      creatorFilters: [],
    };
  },
  watch: {
    '$route'(to, from) {
      window.removeEventListener("resize", this.tableDoLayout);
    },
  },
  activated() {
    this.page.result.loading = true;
    this.$nextTick(() => {
      // 解决错位问题
      window.addEventListener('resize', this.tableDoLayout);
      getProjectMember((data) => {
        this.members = data;
      });
      getIssuePartTemplateWithProject((template) => {
        this.initFields(template);
        this.page.result.loading = false;
      });
      this.getIssues();
    });
  },
  computed: {
    platformFilters() {
     return ISSUE_PLATFORM_OPTION;
    },
    issueStatusMap() {
      return ISSUE_STATUS_MAP;
    },
    tapdIssueStatusMap() {
      return TAPD_ISSUE_STATUS_MAP;
    },
    systemNameMap() {
      return SYSTEM_FIELD_NAME_MAP;
    },
    projectId() {
      return getCurrentProjectID();
    },
    workspaceId(){
      return getCurrentWorkspaceId();
    }
  },
  created() {
    this.getMaintainerOptions();
  },
  methods: {
    tableDoLayout() {
      if (this.$refs.table) this.$refs.table.doLayout();
    },
    getCustomFieldValue(row, field, defaultVal) {
      let value = getCustomFieldValue(row, field, this.members);
      return value ? value : defaultVal;
    },
    initFields(template) {
      this.issueTemplate = template;
      if (this.issueTemplate.platform === LOCAL) {
        this.isThirdPart = false;
      } else {
        this.isThirdPart = true;
      }
      this.fields = getTableHeaderWithCustomFields('ISSUE_LIST', this.issueTemplate.customFields);
      if (!this.isThirdPart) {
        for (let i = 0; i < this.fields.length; i++) {
          if (this.fields[i].id === 'platformStatus') {
            this.fields.splice(i, 1);
            break;
          }
        }
        // 如果不是三方平台则移除备选字段中的平台状态
        let removeField = {id: 'platformStatus', name: 'platformStatus', remove: true};
        this.issueTemplate.customFields.push(removeField);
      }
      if (this.$refs.table) this.$refs.table.reloadTable();
    },
    search() {
      // 添加搜索条件时，当前页设置成第一页
      this.page.currentPage = 1;
      this.getIssues();
    },
    getIssues() {
      this.page.condition.projectId = this.projectId;
      this.page.condition.workspaceId= this.workspaceId;
      this.page.condition.orders = getLastTableSortField(this.tableHeaderKey);
      this.page.result = getIssues(this.page);
    },
    getMaintainerOptions() {
      this.$get('/user/project/member/list', response => {
        this.creatorFilters = response.data.map(u => {
          return {text: u.name, value: u.id};
        });
      });
    },
    handleEdit(data) {
      this.$refs.issueEdit.open(data, 'edit');
    },
    handleCreate() {
      this.$refs.issueEdit.open(null, 'add');
    },
    handleCopy(data) {
      let copyData = {};
      Object.assign(copyData, data);
      copyData.id = null;
      copyData.name = data.name + '_copy';
      this.$refs.issueEdit.open(copyData, 'copy');
    },
    handleDelete(data) {
      this.$alert(this.$t('test_track.issue.delete_tip') + ' ' + data.title + " ？", '', {
        confirmButtonText: this.$t('commons.confirm'),
        callback: (action) => {
          if (action === 'confirm') {
            this._handleDelete(data);
          }
        }
      });
    },
    _handleDelete(data) {
      this.page.result = this.$get('issues/delete/' + data.id, () => {
        this.$success(this.$t('commons.delete_success'));
        this.getIssues();
      });
    },
    btnDisable(row) {
      if (this.issueTemplate.platform !== row.platform) {
        return true;
      }
      return false;
    },
    syncIssues() {
      this.page.result.loading = true;
      syncIssues((data) => {
        if (data === false) {
          checkSyncIssues(this.page.result);
        } else {
          this.$success(this.$t('test_track.issue.sync_complete'));
          this.page.result.loading = false;
          this.getIssues();
        }
      });
    }
  }
};
</script>

<style scoped>
.table-page {
  padding-top: 20px;
  margin-right: -9px;
  float: right;
}

.el-table {
  cursor: pointer;
}
</style>
