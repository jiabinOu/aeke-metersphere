<template>
  <el-form :model="editData" label-position="right" label-width="80px" size="small" ref="form3" :rules="rules">
    <el-form-item :label="$t('api_test.variable_name')" prop="name">
      <el-input v-model="editData.name" :placeholder="$t('api_test.variable_name')"></el-input>
    </el-form-item>

    <el-form-item :label="$t('commons.description')" prop="description">
      <el-input class="ms-http-textarea"
                v-model="editData.description"
                type="textarea"
                :disabled="disabled"
                :placeholder="$t('commons.input_content')"
                :autosize="{ minRows: 2, maxRows: 10}"
                :rows="2" size="small"/>
    </el-form-item>
    <el-tabs v-model="activeName" @tab-click="handleClick" style="margin-left: 40px">
      <el-tab-pane :label="$t('variables.config')" name="config">
        <el-row>
          <el-col :span="5" style="margin-top: 5px">
            <span>{{$t('variables.add_file')}}</span>
          </el-col>
          <el-col :span="19">
            <ms-csv-file-upload :parameter="editData"/>
          </el-col>
        </el-row>
        <el-row style="margin-top: 10px">
          <el-col :span="5" style="margin-top: 5px">
            <span>Encoding</span>
          </el-col>
          <el-col :span="19">
            <el-autocomplete
              size="small"
              style="width: 100%"
              v-model="editData.encoding"
              :disabled="disabled"
              :fetch-suggestions="querySearch"
              :placeholder="$t('commons.input_content')"
            ></el-autocomplete>
          </el-col>
        </el-row>
        <el-row style="margin-top: 10px">
          <el-col :span="5" style="margin-top: 5px">
            <span>{{$t('variables.delimiter')}}</span>
          </el-col>
          <el-col :span="19">
            <el-input v-model="editData.delimiter" size="small" :disabled="disabled"/>
          </el-col>
        </el-row>
        <el-row style="margin-top: 10px">
          <el-col :span="5" style="margin-top: 5px">
            <span>{{$t('variables.quoted_data')}}</span>
          </el-col>
          <el-col :span="19">
            <el-select v-model="editData.quotedData" size="small" :disabled="disabled">
              <el-option label="True" :value="true"/>
              <el-option label="False" :value="false"/>
            </el-select>
          </el-col>
        </el-row>
      </el-tab-pane>
      <el-tab-pane :label="$t('schema.preview')" name="preview">
        <div v-if="showMessage">{{ $t('variables.csv_message') }}</div>
        <el-table
          :data="previewData"
          style="width: 100%"
          height="240px"
          v-loading="loading">
          <!-- 自定义列的遍历-->
          <el-table-column v-for="(item, index) in columns" :key="index" :label="columns[index]" align="left" width="180">
            <!-- 数据的遍历  scope.row就代表数据的每一个对象-->
            <template slot-scope="scope">
              <span>{{scope.row[index]}}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-form>
</template>

<script>
import MsCsvFileUpload from "./CsvFileUpload";
import axios from "axios";

export default {
    name: "MsEditCsv",
    components: {
      MsCsvFileUpload
    },
    props: {
      editData: {},
    },
    data() {
      return {
        activeName: "config",
        visible: false,
        loading: false,
        editFlag: false,
        previewData: [],
        columns: [],
        allData: [],
        showMessage: false,
        rules: {
          name: [
            {required: true, message: this.$t('test_track.case.input_name'), trigger: 'blur'},
          ],
        },
      }
    },
    computed: {
      disabled() {
        return !(this.editData.name && this.editData.name !== "");
      }
    },
    watch: {
      'editData.name': {
        handler(v) {
          this.handleClick();
        }
      }
    },
    methods: {
      complete(results) {
        if (results.errors && results.errors.length > 0) {
          this.$error(results.errors);
          return;
        }
        if (this.allData) {
          this.columns = this.allData[0];
          this.allData.splice(0, 1);
          this.previewData = this.allData;
        }
        this.loading = false;
      },
      cleanPreview() {
        this.allData = [];
        this.columns = [];
        this.previewData = [];
      },
      step(results, parser) {
        if (this.allData.length < 500) {
          this.allData.push(results.data);
        } else {
          this.showMessage = true;
        }
      },

      handleClick() {
        let config = {
          complete: this.complete,
          step: this.step,
          delimiter: this.editData.delimiter ? this.editData.delimiter : ","
        };
        this.allData = [];
        // 本地文件
        if (this.editData.files && this.editData.files.length > 0 && this.editData.files[0].file) {
          this.loading = true;
          this.$papa.parse(this.editData.files[0].file, config);
        }
        // 远程下载文件
        if (this.editData.files && this.editData.files.length > 0 && !this.editData.files[0].file) {
          let file = this.editData.files[0];
          let url = '/api/automation/file/download';
          this.result = axios.post(url, file , {responseType: 'blob'}).then(response => {
            const content = response.data;
            const blob = new Blob([content]);
            this.loading = true;
            this.$papa.parse(blob, config);
          });
        }
      },
      createFilter(queryString) {
        return (restaurant) => {
          return (restaurant.value.toLowerCase().indexOf(queryString.toLowerCase()) === 0);
        };
      },

      querySearch(queryString, cb) {
        let restaurants = [{value: "UTF-8"}, {value: "UTF-16"},{value: "GB2312"}, {value: "ISO-8859-15"}, {value: "US-ASCll"}];
        let results = queryString ? restaurants.filter(this.createFilter(queryString)) : restaurants;
        // 调用 callback 返回建议列表的数据
        cb(results);
      },

    }
  }
</script>

<style scoped>
  ms-is-leaf >>> .is-leaf {
    color: red;
  }
</style>
