<template>
  <div v-if="isShow">

    <el-button-group v-if="isShowChangeButton">

      <el-tooltip class="item" effect="dark" content="接口列表" placement="left">
        <el-button plain style="width: 44px;height: 32px;padding: 5px 8px;" :class="{active: isApiListEnable}"
                   @click="apiChange('api')">API
        </el-button>
      </el-tooltip>

      <el-tooltip class="item" effect="dark" content="用例列表" placement="right">
        <el-button plain class="case-button" style="width: 44px;height: 32px;padding: 1px;"
                   :class="{active: !isApiListEnable}" @click="caseChange('case')">CASE
        </el-button>
      </el-tooltip>

    </el-button-group>

    <slot name="version"></slot>

    <slot></slot>
  </div>
</template>

<script>

export default {
  name: "ApiListContainer",
  data() {
    return {
      isShow: true,
    };
  },
  props: {
    isApiListEnable: Boolean,
    isShowChangeButton: {
      type: Boolean,
      default: true
    }
  },
  methods: {
    apiChange() {
      this.$emit('isApiListEnableChange', true);
    },
    caseChange() {
      this.$emit('isApiListEnableChange', false);
    },
  }
};
</script>

<style scoped>

.active {
  border: solid 1px #06dc9c;
  background-color: #06dc9c;
  color: #FFFFFF;
}

.case-button {
  border-left: solid 1px #6d317c;
}

</style>
