<template>
  <span>
     <el-select v-if="data.type === 'select' || data.type === 'multipleSelect'"
                :disabled="disabled"
                :multiple="data.type === 'multipleSelect'"
                @change="handleChange"
                filterable v-model="data[prop]" :placeholder="$t('commons.default')">
      <el-option
        v-for="(item,index) in data.options ? data.options : []"
        :key="index"
        @change="handleChange"
        :label="getTranslateOption(item)"
        :value="item.value">
      </el-option>
    </el-select>

    <el-cascader
      v-else-if="data.type === 'cascadingSelect'"
      expand-trigger="hover"
      @change="handleChange"
      :props="{label: 'text'}"
      :options="data.options"
      v-model="data[prop]">
    </el-cascader>

    <el-input
      v-else-if="data.type === 'textarea'"
      type="textarea"
      @change="handleChange"
      :rows="2"
      :disabled="disabled"
      :placeholder="$t('commons.input_content')"
      class="custom-with"
      v-model="data[prop]">
    </el-input>

    <el-checkbox-group
      v-else-if="data.type === 'checkbox'"
      :disabled="disabled"
      v-model="data[prop]">
      <el-checkbox v-for="(item, index) in data.options ? data.options : []"
                   :key="index"
                   @change="handleChange"
                   :label="item.value">
        {{ getTranslateOption(item) }}
      </el-checkbox>
    </el-checkbox-group>

    <el-radio
      v-else-if="data.type === 'radio'"
      v-model="data[prop]"
      :disabled="disabled"
      v-for="(item,index) in data.options ? data.options : []"
      :key="index"
      @change="handleChange"
      :label="item.value">
      {{ getTranslateOption(item) }}
    </el-radio>

    <el-input-number
      v-else-if="data.type === 'int'"
      v-model="data[prop]"
      :disabled="disabled"
      @change="handleChange"/>

    <el-input-number
      v-else-if="data.type === 'float'"
      :disabled="disabled"
      @change="handleChange"
      v-model="data[prop]" :precision="2" :step="0.1"/>

     <el-date-picker
       class="custom-with"
       @change="handleChange"
       v-else-if="data.type === 'date' || data.type === 'datetime'"
       :disabled="disabled"
       v-model="data[prop]"
       :type="data.type === 'date' ? 'date' : 'datetime'"
       :placeholder="$t('commons.select_date')">
    </el-date-picker>

    <el-select v-else-if="data.type === 'member' || data.type === 'multipleMember'"
               :multiple="data.type === 'multipleMember'"
               @change="handleChange"
               :disabled="disabled"
               filterable v-model="data[prop]" :placeholder="$t('commons.default')">
       <el-option
         v-for="(item) in memberOptions"
         :key="item.id"
         :label="item.name + ' (' + item.email + ')'"
         :value="item.id">
       </el-option>
    </el-select>

    <ms-input-tag v-else-if="data.type === 'multipleInput'"
                  @input="handleChange"
                  :read-only="disabled" :currentScenario="data" :prop="prop"/>

    <ms-mark-down-text v-else-if="data.type === 'richText'"
                       :prop="prop"
                       @change="handleChange"
                       :default-open="defaultOpen"
                       :data="data" :disabled="disabled"/>

    <el-input class="custom-with"
              @input="handleChange"
              :disabled="disabled"
              v-else v-model="data[prop]"/>

  </span>

</template>

<script>
import MsTableColumn from "@/business/components/common/components/table/MsTableColumn";
import {getCurrentProjectID} from "@/common/js/utils";
import MsInputTag from "@/business/components/api/automation/scenario/MsInputTag";
import MsMarkDownText from "@/business/components/track/case/components/MsMarkDownText";
import {getProjectMemberOption} from "@/network/user";

export default {
  name: "CustomFiledComponent",
  components: {MsMarkDownText, MsInputTag, MsTableColumn},
  props: [
    'data',
    'prop',
    'form',
    'disabled',
    'defaultOpen'
  ],
  data() {
    return {
      memberOptions: [],
    };
  },
  mounted() {
    getProjectMemberOption((data) => {
      this.memberOptions = data;
    });
  },
  methods: {
    getTranslateOption(item) {
      return item.system ? this.$t(item.text) : item.text;
    },
    handleChange() {
      if (this.form) {
        this.$set(this.form, this.data.name, this.data[this.prop]);
      }
    },
  }
};
</script>

<style scoped>
.el-select {
  width: 100%;
}

.el-date-editor.el-input {
  width: 100%;
}

.custom-with >>> .el-input__inner {
  height: 32px;
}

>>> .el-input--suffix .el-input__inner {
  height: 32px;
}
</style>
