<template>
  <div>
    <el-tag size="mini" type="primary" effect="plain" v-if="getStatus(status) === 'running'">
      {{ showStatus(status) }}
    </el-tag>
    <el-tag size="mini" type="success" v-else-if="getStatus(status) === 'success'">
      {{ showStatus(status) }}
    </el-tag>
    <el-tag size="mini" type="danger" v-else-if="getStatus(status) === 'error'">
      {{ showStatus(status) }}
    </el-tag>
    <el-tag size="mini" type="danger" style="background-color: #F6972A; color: #FFFFFF"
            v-else-if="getStatus(status) === 'fake_error'|| getStatus(status)==='errorreportresult'">
      FakeError
    </el-tag>
    <el-tag v-else size="mini" type="info">
      {{ showStatus(status) }}
    </el-tag>
  </div>
</template>

<script>
export default {
  name: "MsTaskReportStatus",

  props: {
    status: String
  },
  methods: {
    getStatus(status) {
      if (status) {
        return status.toLowerCase();
      }
      return "PENDING";
    },
    showStatus(status) {
      if (!status) {
        status = 'PENDING';
      }
      return status.toLowerCase()[0].toUpperCase() + status.toLowerCase().substr(1);
    }
  }
}
</script>

<style scoped>

</style>
