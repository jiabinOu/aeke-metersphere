<template>
  <div @click="exe" class="recycle" :class="{'is-active': condition.trashEnable}">
    <el-row>
      <el-col :span="21"><i class="el-icon-delete"> {{$t('commons.trash')}}</i></el-col>
      <el-col :span="3"><span style="color: #06dc9c">{{ total }}</span></el-col>
    </el-row>
  </div>
</template>

<script>
    export default {
      name: "ModuleTrashButton",
      props: {
        condition: {
          type: Object,
          default() {
            return {};
          }
        },
        exe: {
          type: Function
        },
        total: Number,
      },
      methods: {
        // enableTrash() {
        //
        // }
      }
    }
</script>

<style scoped>

  .recycle {
    padding-left: 25px;
    margin-top: 15px;
    height: 26px;
    line-height: 26px;
    margin-bottom: -10px;
  }

  .recycle:hover {
    color: #6d317c;
    cursor: pointer;
  }

  .is-active {
    background-color: #f3f6f9;
  }

</style>
