<template>

  <el-drawer
    class="field-template-edit"
    :before-close="handleClose"
    :visible.sync="visible"
    :with-header="false"
    size="100%"
    :modal="false"
    ref="drawer"
    v-loading="result.loading">
    <template v-slot:default="scope">

      <template-component-edit-header :template="form" @cancel="handleClose" @save="handleSave"/>

      <el-main class="container">
        <el-scrollbar>
          <ms-form-divider :title="$t('test_track.plan_view.base_info')"/>

          <el-form :model="form" :rules="rules" label-position="right" label-width="80px" size="small" ref="form">
            <el-form-item :label="$t('commons.template_name')" prop="name" :label-width="labelWidth">
              <el-input v-model="form.name" :disabled="isSystem" autocomplete="off" maxlength="64"
                        show-word-limit></el-input>
            </el-form-item>

            <slot name="base"></slot>

            <el-form-item :label="$t('commons.description')" prop="description" :label-width="labelWidth">
              <el-input v-model="form.description" :autosize="{ minRows: 2, maxRows: 4}" maxlength="255" show-word-limit
                        type="textarea"></el-input>
            </el-form-item>

            <ms-form-divider :title="$t('custom_field.template_setting')"/>

            <slot></slot>

            <el-form-item :label="$t('table.selected_fields')" class="filed-list" :label-width="labelWidth">
              <el-button :disabled="!hasPermissions" type="primary" @click="relateField">{{ $t('custom_field.add_field') }}</el-button>
              <el-button :disabled="!hasPermissions" plain type="primary" @click="addField">{{
                  $t('custom_field.custom_field_setting')
                }}
              </el-button>
              <span v-if="!hasPermissions" style="font-size: 12px; color: red; margin-left: 10px">{{$t("custom_field.no_custom_fields_permission")}}</span>
            </el-form-item>

            <el-form-item :label-width="labelWidth">
              <custom-field-form-list
                :table-data="relateFields"
                :scene="scene"
                :platform="form.platform"
                :template-contain-ids="templateContainIds"
                :custom-field-ids="form.customFieldIds"
                ref="customFieldFormList"
              />
            </el-form-item>

          </el-form>

          <custom-field-relate-list
            :template-id="form.id"
            :template-contain-ids="templateContainIds"
            @save="handleRelate"
            :scene="scene"
            ref="customFieldRelateList"/>

          <custom-field-edit ref="customFieldEdit" :label-width="labelWidth" :scene="scene"
                             @save="handleCustomFieldAdd"/>

        </el-scrollbar>
      </el-main>
    </template>
  </el-drawer>
</template>

<script>

import draggable from 'vuedraggable';
import TemplateComponentEditHeader
  from "@/business/components/track/plan/view/comonents/report/TemplateComponentEditHeader";
import MsFormDivider from "@/business/components/common/components/MsFormDivider";
import CustomFieldFormList from "@/business/components/project/template/CustomFieldFormList";
import CustomFieldRelateList from "@/business/components/project/template/CustomFieldRelateList";
import {getCurrentProjectID} from "@/common/js/utils";
import CustomFieldEdit from "@/business/components/project/template/CustomFieldEdit";
import {generateTableHeaderKey, getCustomFieldsKeys} from "@/common/js/tableUtils";
import {hasPermissions} from "@/common/js/utils";

export default {
  name: "FieldTemplateEdit",
  components: {
    CustomFieldEdit,
    CustomFieldRelateList,
    CustomFieldFormList,
    MsFormDivider,
    TemplateComponentEditHeader,
    draggable
  },
  data() {
    return {
      result: {},
      templateContainIds: new Set(),
      relateFields: []
    };
  },
  props: {
    visible: {
      type: Boolean,
      default() {
        return false;
      }
    },
    scene: String,
    url: String,
    rules: Object,
    labelWidth: String,
    form: {
      type: Object,
      default() {
        return {};
      }
    }
  },
  computed: {
    isSystem() {
      return this.form.system;
    },
    hasPermissions() {
      return hasPermissions('PROJECT_TEMPLATE:READ+CUSTOM');
    }
  },
  methods: {
    open() {
      this.$nextTick(() => {
        this.init();
        this.getRelateFields();
        this.$emit('update:visible', true);
      });
    },
    init() {
      this.relateFields = [];
      this.templateContainIds = new Set();
    },
    handleClose() {
      this.$emit('update:visible', false);
    },
    handleRelate(data) {
      this.templateContainIds.add(...data);
      this.$refs.customFieldFormList.appendData(data);
    },
    handleSave() {
      this.$refs.form.validate((valid) => {
        if (valid) {
          let param = {};
          Object.assign(param, this.form);
          if (this.form.steps) {
            param.steps = JSON.stringify(this.form.steps);
          }
          param.options = JSON.stringify(this.form.options);
          param.projectId = getCurrentProjectID();
          let customFields = this.relateFields;
          if (customFields) {
            let keys = getCustomFieldsKeys(customFields);
            customFields.forEach(item => {
              if (!item.key) {
                item.key = generateTableHeaderKey(keys, customFields);
              }
              item.defaultValue = JSON.stringify(item.defaultValue);
            });
          }
          param.customFields = customFields;
          this.result = this.$post(this.url, param, () => {
            this.handleClose();
            this.$success(this.$t('commons.save_success'));
            this.$emit('refresh');
          });
        }
      });
    },
    handleCustomFieldAdd(data) {
      this.templateContainIds.add(data.id);
      data.fieldId = data.id;
      data.id = null;
      data.options = JSON.parse(data.options);
      if (data.type === 'checkbox') {
        data.defaultValue = [];
      }
      this.relateFields.push(data);
    },
    relateField() {
      this.$refs.customFieldRelateList.open();
    },
    addField() {
      this.$refs.customFieldEdit.open(null, this.$t('custom_field.create'));
    },
    getRelateFields() {
      let condition = {};
      condition.templateId = this.form.id;
      if (this.form.id) {
        this.result = this.$post('custom/field/template/list',
          condition, (response) => {
            this.relateFields = response.data;
            this.relateFields.forEach(item => {
              if (item.options) {
                item.options = JSON.parse(item.options);
              }
              if (item.defaultValue) {
                item.defaultValue = JSON.parse(item.defaultValue);
              } else if (item.type === 'checkbox') {
                item.defaultValue = [];
              }
              this.templateContainIds.add(item.fieldId);
            });
            this.$refs.customFieldFormList.refreshTable();
          });
      } else {
        this.appendDefaultFiled();
      }
    },
    appendDefaultFiled() {
      let condition = {
        projectId: getCurrentProjectID(),
        scene: this.scene
      };
      this.result = this.$post('custom/field/default', condition, (response) => {
        let data = response.data;
        data.forEach(item => {
          if (item.id) {
            this.templateContainIds.add(item.id);
          }
          item.fieldId = item.id;
          item.id = null;
          item.options = JSON.parse(item.options);
          if (item.type === 'checkbox') {
            item.defaultValue = [];
          }
        });
        this.relateFields.push(...data);
      });
    }
  }
};
</script>

<style scoped>

.container {
  height: calc(100vh - 62px);
}

.filed-list {
  margin-top: 30px;
}

.field-template-edit {
  z-index: 1500 !important;
}

</style>
