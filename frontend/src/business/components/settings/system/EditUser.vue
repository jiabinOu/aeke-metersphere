<template>
  <el-dialog :close-on-click-modal="false" :title="title"
             :visible.sync="createVisible" width="40%"
             @closed="handleClose" class="edit-user-dialog"
             :destroy-on-close="true">
    <div v-loading="result.loading">
      <el-form :model="form" label-width="120px" size="small" :rules="rule" ref="createUserForm">
        <el-form-item label="ID" prop="id">
          <el-input v-model="form.id" autocomplete="off" :placeholder="$t('user.input_id_placeholder')"
                    :disabled="type === 'Edit'" class="form-input"/>
        </el-form-item>
        <el-form-item :label="$t('commons.username')" prop="name">
          <el-input v-model="form.name" autocomplete="off" :placeholder="$t('user.input_name')" class="form-input"/>
        </el-form-item>
        <el-form-item :label="$t('commons.email')" prop="email">
          <el-input v-model="form.email" autocomplete="off" :placeholder="$t('user.input_email')" class="form-input"/>
        </el-form-item>
        <el-form-item :label="$t('commons.phone')" prop="phone">
          <el-input v-model="form.phone" autocomplete="off" :placeholder="$t('user.input_phone')" class="form-input"/>
        </el-form-item>
        <el-form-item :label="$t('commons.password')" prop="password" v-if="type === 'Add'">
          <el-input v-model="form.password" autocomplete="new-password" show-password
                    :placeholder="$t('user.input_password')" class="form-input"/>
        </el-form-item>
        <div v-for="(group, index) in form.groups" :key="index">
          <el-form-item :label="getLabel(index)"
                        :prop="'groups.' + index + '.type'"
                        :rules="{required: true, message: $t('user.select_group'), trigger: 'change'}"
          >
            <el-select filterable v-model="group.type" :placeholder="$t('user.select_group')"
                       class="edit-user-select" :disabled="form.groups[index].type != null && form.groups[index].type !== '' " @change="getResource(group.type, index)">
              <el-option
                v-for="item in activeGroup(group)"
                :key="item.id"
                :label="item.name"
                :value="item.id + '+' +item.type"
              >
              </el-option>
            </el-select>
            <el-button @click.prevent="removeGroup(group)" style="margin-left: 20px;">
              {{ $t('commons.delete') }}
            </el-button>
          </el-form-item>
          <div v-if="groupType(group.type) === ws">
            <el-form-item :label="$t('commons.workspace')"
                          :prop="'groups.' + index + '.ids'"
                          :rules="{required: true, message: $t('workspace.select'), trigger: 'change'}"
            >
              <el-select filterable v-model="group.ids" :placeholder="$t('workspace.select')" multiple
                         :filter-method="(value) => filterWorkspaceOption(value, group)"
                         @visible-change="(value) => resetWorkspaceOption(value, group)"
                         @change="updateWorkSpace(group.index,group.type)"
                         class="edit-user-select">
                <el-option
                  v-for="item in group.workspaceOptions"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id">
                </el-option>
                <div style="text-align: center; color: #8a8b8d;" v-if="group.showSearchGetMore">
                  {{ $t('user.search_get_more_tip') }}
                </div>
              </el-select>
            </el-form-item>
          </div>
          <div v-if="groupType(group.type) === project">
            <el-form-item :label="$t('commons.project')"
                          :prop="'groups.' + index + '.ids'"
                          :rules="{required: true, message: $t('user.select_project'), trigger: 'change'}"
            >
              <el-select filterable v-model="group.ids" :placeholder="$t('user.select_project')" multiple
                         :filter-method="(value) => filterProjectOption(value, group)"
                         @visible-change="(value) => resetProjectOption(value, group)"
                         class="edit-user-select" @change="setWorkSpaceIds(group.ids,group.projects)">
                <el-option
                  v-for="item in group.projectOptions"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id">
                </el-option>
                <div style="text-align: center; color: #8a8b8d;" v-if="group.showSearchGetMore">
                  {{ $t('user.search_get_more_tip') }}
                </div>
              </el-select>
            </el-form-item>
          </div>
        </div>

        <el-form-item>
          <template>
            <el-button type="success" class="form-input" @click="addGroup('createUserForm')" :disabled="btnAddRole">
              {{ $t('group.add') }}
            </el-button>
          </template>
        </el-form-item>
      </el-form>
    </div>
    <template v-slot:footer>
      <div class="dialog-footer">
        <el-button @click="createVisible = false" size="medium">{{ $t('commons.cancel') }}</el-button>
        <el-button type="primary" @click="createUser('createUserForm')" @keydown.enter.native.prevent size="medium">
          {{ $t('commons.confirm') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script>
import {EMAIL_REGEX, PHONE_REGEX} from "@/common/js/regex";
import {GROUP_PROJECT, GROUP_SYSTEM, GROUP_WORKSPACE} from "@/common/js/constants";

export default {
  name: "EditUser",
  components: {},
  data() {
    return {
      result: {},
      createVisible: false,
      updateVisible: false,
      btnAddRole: false,
      form: {
        groups: [{
          type: '',
          showSearchGetMore: false,
        }]
      },
      rule: {
        id: [
          {required: true, message: this.$t('user.input_id'), trigger: 'blur'},
          {min: 1, max: 50, message: this.$t('commons.input_limit', [1, 50]), trigger: 'blur'},
          {
            required: true,
            pattern: '^[^\u4e00-\u9fa5]+$',
            message: this.$t('user.special_characters_are_not_supported'),
            trigger: 'blur'
          }
        ],
        name: [
          {required: true, message: this.$t('user.input_name'), trigger: 'blur'},
          {min: 2, max: 50, message: this.$t('commons.input_limit', [2, 50]), trigger: 'blur'},
          {
            required: true,
            message: this.$t('user.special_characters_are_not_supported'),
            trigger: 'blur'
          }
        ],
        phone: [
          {
            pattern: PHONE_REGEX,
            message: this.$t('user.mobile_number_format_is_incorrect'),
            trigger: 'blur'
          }
        ],
        email: [
          {required: true, message: this.$t('user.input_email'), trigger: 'blur'},
          {
            required: true,
            pattern: EMAIL_REGEX,
            message: this.$t('user.email_format_is_incorrect'),
            trigger: 'blur'
          }
        ],
        password: [
          {required: true, message: this.$t('user.input_password'), trigger: 'blur'},
          {
            required: true,
            pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[\s\S]{8,30}$/,
            message: this.$t('member.password_format_is_incorrect'),
            trigger: 'blur'
          }
        ],
      },
      userGroup: [],
      workspaces: [],
      projects: [],
      type: "Add",
      title: "创建用户",
      currentWSGroupIndex:-1,
      currentGroupWSIds:new Set,
      limitOptionCount: 400,
    }
  },
  computed: {
    ws() {
      return GROUP_WORKSPACE;
    },
    project() {
      return GROUP_PROJECT;
    }
  },
  methods: {
    open(type, title, row) {
      this.type = type ? type : this.type;
      this.title = title ? title : this.title;

      if (type === 'Edit') {
        this.result = this.$get('/user/group/all/' + encodeURIComponent(row.id), response => {
          let data = response.data;
          this.$set(this.form, "groups", data);
          for (let group of this.form.groups) {
            this.handleWorkspaceOption(group, group.workspaces);
            this.handleProjectOption(group, group.projects);
          }
        });
        this.form = Object.assign({}, row);
      }

      if (this.$refs.createUserForm) {
        this.$refs.createUserForm.clearValidate();
      }

      this.createVisible = true;
      this.getAllUserGroup();
    },
    handleClose() {
      this.createVisible = false;
      this.form = {groups: [{type: ''}]};
      this.btnAddRole = false;
      this.currentWSGroupIndex=-1;
      this.currentGroupWSIds=new Set;
    },
    activeGroup(roleInfo) {
      return this.userGroup.filter(function (group) {
        let value = true;
        if (!roleInfo.selects) {
          return true;
        }
        if (roleInfo.selects.length === 0) {
          value = true;
        }
        for (let i = 0; i < roleInfo.selects.length; i++) {
          let idType = group.id + "+" + group.type;
          if (idType === roleInfo.selects[i]) {
            value = false;
          }
        }
        return value;
      })
    },
    removeGroup(item) {
      if (this.form.groups.length === 1) {
        this.$info(this.$t('user.remove_group_tip'));
        return;
      }
      let index = this.form.groups.indexOf(item);
      let isRemove = this.checkRemove(item,index);
      if (!isRemove) {
        return;
      }
      if (item.type) {
        let _type = item.type.split("+")[1];
        if (_type === 'WORKSPACE') {
          this.currentWSGroupIndex = -1;
        } else {
          if (this.currentWSGroupIndex > index) {
            this.currentWSGroupIndex = this.currentWSGroupIndex-1
          }
        }
      }
      if (index !== -1) {
        this.form.groups.splice(index, 1)
      }
      if (this.form.groups.length < this.userGroup.length) {
        this.btnAddRole = false;
      }
    },
    checkRemove(item,index){
      if (!item.type) {
        return true;
      }
      let type = item.type.split("+")[1];
      if (type === this.ws) {
        let isHaveWorkspace = 0;
        let isHaveProject = 0;
        for (let i = 0; i < this.form.groups.length; i++) {
          if (index === i) {
            continue;
          }
          let group = this.form.groups[i];
          if (!group.type) {
            continue;
          }
          let _type = group.type.split("+")[1];
          if (_type === this.ws) {
            isHaveWorkspace += 1;
          }
          if (_type === this.project) {
            isHaveProject += 1;
          }
        }
        if (isHaveWorkspace === 0 && isHaveProject >0 ) {
          this.$message.warning(this.$t('commons.not_eligible_for_deletion'))
          return false;
        } else {
          this.currentGroupWSIds = new Set;
        }
      }
      return true;
    },
    addGroup(validForm) {
      this.$refs[validForm].validate(valid => {
        if (valid) {
          let roleInfo = {};
          roleInfo.selects = [];
          let ids = this.form.groups.map(r => r.type);
          ids.forEach(id => {
            roleInfo.selects.push(id);
          })
          let groups = this.form.groups;
          groups.push(roleInfo);
          if (this.form.groups.length > this.userGroup.length - 1) {
            this.btnAddRole = true;
          }
        } else {
          return false;
        }
      })
    },
    createUser(createUserForm) {
      this.$refs[createUserForm].validate(valid => {
        if (valid) {
          let url = this.type === 'Add' ? '/user/special/add' : '/user/special/update';
          this.result = this.$post(url, this.form, () => {
            this.$success(this.$t('commons.save_success'));
            this.$emit("refresh");
            this.createVisible = false;
          });
        } else {
          return false;
        }
      })
    },
    getAllUserGroup() {
      this.$post('/user/group/get', {type: GROUP_SYSTEM}, res => {
        let data = res.data;
        if (data) {
          this.userGroup = data;
        }
      })
    },
    groupType(idType) {
      if (!idType) {
        return;
      }
      return idType.split("+")[1];
    },
    getResource(idType, index) {
      if (!idType) {
        return;
      }
      let id = idType.split("+")[0];
      let type = idType.split("+")[1];
      if (index>0 && this.form.groups[index].ids && this.form.groups[index].ids.length >0) {
       return;
      }
      let isHaveWorkspace = false;
      if (type === 'PROJECT') {
        for (let i = 0; i < this.form.groups.length; i++) {
          let group = this.form.groups[i];
          let _type = group.type.split("+")[1];
          if (_type === 'WORKSPACE') {
            isHaveWorkspace = true;
            break;
          }
        }
      } else if (type === 'WORKSPACE') {
        isHaveWorkspace = true;
      }
      this.result = this.$get('/workspace/list/resource/' + id + "/" + type, res => {
        let data = res.data;
        if (data) {
          this._setResource(data, index, type);
          if(isHaveWorkspace === false ){
            this.addWorkspaceGroup(id,index);
          }
        }
      })
    },
    _setResource(data, index, type) {
      switch (type) {
        case GROUP_WORKSPACE: {
          this.form.groups[index].workspaces = data.workspaces;
          this.handleWorkspaceOption(this.form.groups[index], data.workspaces);
          break;
        }
        case GROUP_PROJECT:
          this.form.groups[index].projects = data.projects;
          this.handleProjectOption(this.form.groups[index], data.projects);
          break;
        default:
      }
    },
    addWorkspaceGroup(id,index){
      let isHaveWorkSpace ;
      this.form.groups.forEach(item =>{
        if (item.type === "ws_member+WORKSPACE") {
          isHaveWorkSpace = true;
        }
      })
      if (isHaveWorkSpace) {
        return;
      }
      this.result = this.$get('/workspace/list/resource/' + id + "/WORKSPACE", res => {
        let data = res.data;
        if (data) {
          let roleInfo = {};
          roleInfo.selects = [];
          roleInfo.type = "ws_member+WORKSPACE";
          let ids = this.form.groups.map(r => r.type);
          ids.forEach(id => {
            roleInfo.selects.push(id);
          })
          if (this.currentGroupWSIds.size > 0) {
            roleInfo.ids = [];
            this.currentGroupWSIds.forEach(item =>{
              roleInfo.ids.push(item);
            })
          } else {
            roleInfo.ids = [];
          }
          let groups = this.form.groups;
          groups.push(roleInfo);
          this.currentWSGroupIndex = index+1;
          this._setResource(data, index+1, 'WORKSPACE');
        }
      })
    },
    getLabel(index) {
      let a = index + 1;
      return this.$t('commons.group') + a;
    },
    setWorkSpaceIds(ids,projects){
      projects.forEach(project => {
        ids.forEach(item =>{
          if(item === project.id){
            this.currentGroupWSIds.add(project.workspaceId);
            if(this.form.groups[this.currentWSGroupIndex] &&
              this.form.groups[this.currentWSGroupIndex].ids.indexOf(project.workspaceId) === -1){
              this.form.groups[this.currentWSGroupIndex].ids.push(project.workspaceId);
            }
          }
        })
      });
    },
    updateWorkSpace(index,type){
      let _type = type.split("+")[1];
      if (_type === 'WORKSPACE') {
        this.currentGroupWSIds.forEach(item =>{
          this.form.groups[index].ids.push(item);
        })
      }else {
        this.form.groups[index].ids = [];
      }
    },
    handleWorkspaceOption(group, workspaces) {
      if (!workspaces) {
        return;
      }
      this.$set(group, 'showSearchGetMore', workspaces.length > this.limitOptionCount);
      const options = workspaces.slice(0, this.limitOptionCount);
      this.$set(group, 'workspaceOptions', options);
      if (!group.ids || group.ids.length === 0) {
        return;
      }
      for (let id of group.ids) {
        let index = options.findIndex(o => o.id === id);
        if (index <= -1) {
          let obj = workspaces.find(d => d.id === id);
          if (obj) {
            group.workspaceOptions.unshift(obj);
          }
        }
      }
    },
    handleProjectOption(group, projects) {
      if (!projects) {
        return;
      }
      this.$set(group, 'showSearchGetMore', projects.length > this.limitOptionCount);
      const options = projects.slice(0, this.limitOptionCount);
      this.$set(group, 'projectOptions', options);
      if (!group.ids || group.ids.length === 0) {
        return;
      }
      for (let id of group.ids) {
        let index = options.findIndex(o => o.id === id);
        if (index <= -1) {
          let obj = projects.find(d => d.id === id);
          if (obj) {
            group.projectOptions.unshift(obj);
          }
        }
      }
    },
    filterWorkspaceOption(queryString, group) {
      let workspaces = group.workspaces;
      let copy = JSON.parse(JSON.stringify(workspaces));
      this.handleWorkspaceOption(group, queryString ? copy.filter(this.createFilter(queryString)) : copy);
    },
    filterProjectOption(queryString, group) {
      let projects = group.projects;
      let copy = JSON.parse(JSON.stringify(projects));
      this.handleProjectOption(group, queryString ? copy.filter(this.createFilter(queryString)) : copy);
    },
    createFilter(queryString) {
      return item => (item.name.toLowerCase().indexOf(queryString.toLowerCase()) !== -1);
    },
    resetWorkspaceOption(val, group) {
      if (val) {
        this.handleWorkspaceOption(group, group.workspaces);
      }
    },
    resetProjectOption(val, group) {
      if (val) {
        this.handleProjectOption(group, group.projects);
      }
    }
  }
}
</script>

<style scoped>

.form-input {
  width: 80%;
}

.edit-user-select {
  width: 80%;
}

</style>
