<template>
  <div>
    <slot name="header">
      <el-input :placeholder="$t('test_track.module.search')" v-model="filterText" size="small" :clearable="true"/>
    </slot>

    <el-tree
      class="filter-tree node-tree"
      :data="extendTreeNodes"
      :default-expanded-keys="expandedNode"
      :default-expand-all="defaultExpandAll"
      node-key="id"
      @node-drag-end="handleDragEnd"
      @node-expand="nodeExpand"
      @node-collapse="nodeCollapse"
      :filter-node-method="filterNode"
      :expand-on-click-node="false"
      highlight-current
      :draggable="!disabled"
      ref="tree">

      <template v-slot:default="{node,data}">
      <span class="custom-tree-node father" @click="handleNodeSelect(node)">

        <span v-if="data.isEdit" @click.stop>
          <el-input @blur.stop="save(node, data)" @keyup.enter.native.stop="$event.target.blur()" v-model="data.name"
                    class="name-input" size="mini" ref="nameInput" :draggable="true"/>
        </span>

        <span v-if="!data.isEdit" class="node-icon">
          <i class="el-icon-folder"/>
        </span>
        <el-tooltip class="item" effect="dark" :content="data.name" placement="top-start" :open-delay="1000">
          <span v-if="!data.isEdit" class="node-title" v-text="isDefault(data) ? getLocalDefaultName() : data.name"/>
        </el-tooltip>

        <span class="count-title" v-if="isDisplay !== 'relevance'">
          <span style="color: #06dc9c">{{ data.caseNum }}</span>
        </span>
        <span v-if="!disabled" class="node-operate child">
          <el-tooltip
            v-if="data.id !== 'root' && data.name !== defaultLabel"
            class="item"
            effect="dark"
            v-permission="updatePermission"
            :open-delay="200"
            :content="$t('test_track.module.rename')"
            placement="top">
            <i @click.stop="edit(node, data)" class="el-icon-edit"></i>
          </el-tooltip>
          <el-tooltip
            v-if="data.name === defaultLabel && data.level !== 1"
            v-permission="updatePermission"
            class="item"
            effect="dark"
            :open-delay="200"
            :content="$t('test_track.module.rename')"
            placement="top">
            <i @click.stop="edit(node, data)" class="el-icon-edit"></i>
          </el-tooltip>
          <el-tooltip
            class="item"
            effect="dark"
            :open-delay="200"
            v-permission="addPermission"
            v-if="!isDefault(data)"
            :content="$t('test_track.module.add_submodule')"
            placement="top">
            <i @click.stop="append(node, data)" class="el-icon-circle-plus-outline"></i>
          </el-tooltip>

          <el-tooltip
            v-if="data.name === defaultLabel && data.level !==1"
            class="item" effect="dark"
            :open-delay="200"
            v-permission="deletePermission"
            :content="$t('commons.delete')"
            placement="top">
            <i @click.stop="remove(node, data)" class="el-icon-delete"></i>
          </el-tooltip>

          <el-tooltip
            v-if="data.id !== 'root' && data.name !== defaultLabel"
            class="item" effect="dark"
            :open-delay="200"
            :content="$t('commons.delete')"
            v-permission="deletePermission"
            placement="top">
            <i @click.stop="remove(node, data)" class="el-icon-delete"></i>
          </el-tooltip>
        </span>
      </span>
      </template>
    </el-tree>
  </div>
</template>

<script>

export default {
  name: "MsNodeTree",
  components: {},
  data() {
    return {
      result: {},
      filterText: "",
      expandedNode: [],
      reloaded: false,
      defaultProps: {
        children: "children",
        label: "label"
      },
      extendTreeNodes: [],
    };
  },
  props: {
    isDisplay: {
      type: String,
    },
    type: {
      type: String,
      default: "view"
    },
    treeNodes: {
      type: Array
    },
    allLabel: {
      type: String,
      default() {
        return this.$t("commons.all_label.case");
      }
    },
    defaultLabel: {
      type: String,
      default() {
        return '未规划用例';
      }
    },
    nameLimit: {
      type: Number,
      default() {
        return 50;
      }
    },
    defaultExpandAll: {
      type: Boolean,
      default() {
        return false;
      }
    },
    updatePermission: Array,
    addPermission: Array,
    deletePermission: Array,
    localSuffix: String
  },
  watch: {
    treeNodes() {
      this.init();
    },
    filterText(val) {
      this.filter(val);
    }
  },
  computed: {
    disabled() {
      return this.type !== 'edit';
    }
  },
  methods: {
    init() {
      let num = 0;
      this.treeNodes.forEach(t => {
        num += t.caseNum;
      });
      this.extendTreeNodes = [];
      this.extendTreeNodes.unshift({
        "id": "root",
        "name": this.allLabel,
        "level": 0,
        "children": this.treeNodes,
        "caseNum": num > 0 ? num : ""
      });
      if (this.expandedNode.length === 0) {
        this.expandedNode.push("root");
      }
    },
    setCurrentKeyById(id) {
      if (id) {
        this.$nextTick(() => {
          this.handleNodeSelect(this.$refs.tree.getNode(id));
          this.$refs.tree.setCurrentKey(id);
        })
      }
    },
    handleNodeSelect(node) {
      let nodeIds = [];
      let pNodes = [];
      this.getChildNodeId(node.data, nodeIds);
      this.getParentNodes(node, pNodes);
      this.$emit("nodeSelectEvent", node, nodeIds, pNodes);
    },
    filterNode(value, data) {
      if (!value) return true;
      if (data.label) {
        return data.label.toLowerCase().indexOf(value.toLowerCase()) !== -1;
      }
      return false;
    },
    filter(val) {
      this.$nextTick(() => {
        this.$refs.tree.filter(val);
      });
    },
    nodeExpand(data) {
      if (data.id) {
        this.expandedNode.push(data.id);
      }
    },
    nodeCollapse(data) {
      if (data.id) {
        this.expandedNode.splice(this.expandedNode.indexOf(data.id), 1);
      }
      // this.reloaded = false;
      this.$nextTick(() => {
        let node = this.$refs.tree.getNode(data);
        if (node) {
          node.expanded = false;
        }

        if (data.children && data.children.length > 0) {
          this.changeTreeNodeStatus(data);
        }
      });
    },
    // 改变节点的状态
    changeTreeNodeStatus(parentData) {
      for (let i = 0; i < parentData.children.length; i++) {
        let data = parentData.children[i];
        if (data.id) {
          this.expandedNode.splice(this.expandedNode.indexOf(data.id), 1);
        }
        let node = this.$refs.tree.getNode(data);
        if (node) {
          node.expanded = false;
        }

        // 遍历子节点
        if (data.children && data.children.length > 0) {
          this.changeTreeNodeStatus(data)
        }
      }
    },
    edit(node, data, isAppend) {
      this.$set(data, 'isEdit', true);
      this.$nextTick(() => {
        this.$refs.nameInput.focus();

        // 不知为何，执行this.$set(data, 'isEdit', true);进入编辑状态之后过滤会失效，重新执行下过滤
        if (!isAppend) {
          this.$nextTick(() => {
            this.filter(this.filterText);
          });
          this.$nextTick(() => {
            this.$emit('filter');
          });
        }
      });
    },
    increase(id) {
      this.traverse(id, node => {
        if (node.caseNum) {
          node.caseNum++;
        }
      }, true);
      if (this.extendTreeNodes[0].id === 'root') {
        this.extendTreeNodes[0].caseNum++;
      }
    },
    decrease(id) {
      this.traverse(id, node => {
        if (node.caseNum) {
          node.caseNum--;
        }
      }, true);
      if (this.extendTreeNodes[0].id === 'root') {
        this.extendTreeNodes[0].caseNum--;
      }
    },
    traverse(id, callback, isParentCallback) {
      for (let i = 0; i < this.treeNodes.length; i++) {
        let rootNode = this.treeNodes[i];
        this._traverse(rootNode, id, callback, isParentCallback);
      }
    },
    _traverse(rootNode, id, callback, isParentCallback) {
      if (rootNode.id === id) {
        if (callback) {
          callback(rootNode);
        }
        return true;
      }
      if (!rootNode.children) {
        return false;
      }
      for (let i = 0; i < rootNode.children.length; i++) {
        let children = rootNode.children[i];
        let result = this._traverse(children, id, callback, isParentCallback);
        if (result === true) {
          if (isParentCallback) {
            callback(rootNode);
          }
          return result;
        }
      }
    },
    append(node, data) {
      const newChild = {
        id: undefined,
        isEdit: false,
        name: "",
        children: []
      };
      if (!data.children) {
        this.$set(data, 'children', [])
      }
      data.children.push(newChild);
      this.edit(node, newChild, true);
      node.expanded = true;
      this.$nextTick(() => {
        this.$refs.nameInput.focus();
      });
    },
    save(node, data) {
      if (data.name.trim() === '') {
        this.$warning(this.$t('test_track.case.input_name'));
        return;
      }
      if (data.name.trim().length > this.nameLimit) {
        this.$warning(this.$t('test_track.length_less_than') + this.nameLimit);
        return;
      }
      if (data.name.indexOf("\\") > -1) {
        this.$warning(this.$t('commons.node_name_tip'));
        return;
      }
      let param = {};
      this.buildSaveParam(param, node.parent.data, data);
      if (param.type === 'edit') {
        this.$emit('edit', param);
      } else {
        this.expandedNode.push(param.parentId);
        this.$emit('add', param);
      }
      this.$set(data, 'isEdit', false);
    },
    remove(node, data) {
      if (data.label === undefined) {
        this.$refs.tree.remove(node);
        return;
      }
      let tip = '确定删除节点 ' + data.label + ' 及其子节点下所有资源' + '？';
      // let info =  this.$t("test_track.module.delete_confirm") + data.label + "，" + this.$t("test_track.module.delete_all_resource") + "？";
      this.$alert(tip, "", {
          confirmButtonText: this.$t("commons.confirm"),
          callback: action => {
            if (action === "confirm") {
              let nodeIds = [];
              this.getChildNodeId(node.data, nodeIds);
              this.$emit('remove', nodeIds);
            }
          }
        }
      );
    },
    handleDragEnd(draggingNode, dropNode, dropType, ev) {
      if (dropType === "none" || dropType === undefined) {
        return;
      }
      if (dropNode.data.id === 'root' && dropType === 'before' || draggingNode.data.name === this.defaultLabel) {
        this.$emit('refresh');
        return false;
      }
      let param = this.buildParam(draggingNode, dropNode, dropType);
      let list = [];
      this.getNodeTree(this.treeNodes, draggingNode.data.id, list);
      if (param.parentId === 'root') {
        param.parentId = undefined;
      }
      this.$emit('drag', param, list);
    },
    buildSaveParam(param, parentData, data) {
      if (data.id) {
        param.nodeIds = [];
        param.type = 'edit';
        param.id = data.id;
        param.level = data.level;
        param.parentId = data.parentId;
        this.getChildNodeId(data, param.nodeIds);
      } else {
        param.level = 1;
        param.type = 'add';
        if (parentData.id != 'root') {
          // 非根节点
          param.parentId = parentData.id;
          param.level = parentData.level + 1;
        }
      }
      param.name = data.name.trim();
      param.label = data.name;
    },
    buildParam(draggingNode, dropNode, dropType) {
      let param = {};
      param.id = draggingNode.data.id;
      param.name = draggingNode.data.name;
      param.projectId = draggingNode.data.projectId;
      if (dropType === "inner") {
        param.parentId = dropNode.data.id;
        param.level = dropNode.data.level + 1;
      } else {
        if (!dropNode.parent.id || dropNode.parent.id === 0) {
          param.parentId = 0;
          param.level = 1;
        } else {
          param.parentId = dropNode.parent.data.id;
          param.level = dropNode.parent.data.level + 1;
        }
      }
      let nodeIds = [];
      this.getChildNodeId(draggingNode.data, nodeIds);
      if (dropNode.data.level == 1 && dropType != "inner") {
        // nodeTree 为需要修改的子节点
        param.nodeTree = draggingNode.data;
      } else {
        for (let i = 0; i < this.treeNodes.length; i++) {
          param.nodeTree = this.findTreeByNodeId(this.treeNodes[i], dropNode.data.id);
          if (param.nodeTree) {
            break;
          }
        }
      }
      param.nodeIds = nodeIds;
      return param;
    },
    getNodeTree(nodes, id, list) {
      if (!nodes) {
        return;
      }
      for (let i = 0; i < nodes.length; i++) {
        if (nodes[i].id === id) {
          i - 1 >= 0 ? list[0] = nodes[i - 1].id : list[0] = "";
          list[1] = nodes[i].id;
          i + 1 < nodes.length ? list[2] = nodes[i + 1].id : list[2] = "";
          return;
        }
        if (nodes[i].children) {
          this.getNodeTree(nodes[i].children, id, list);
        }
      }
    },
    findTreeByNodeId(rootNode, nodeId) {
      if (rootNode.id == nodeId) {
        return rootNode;
      }
      if (rootNode.children) {
        for (let i = 0; i < rootNode.children.length; i++) {
          if (this.findTreeByNodeId(rootNode.children[i], nodeId)) {
            return rootNode;
          }
        }
      }
    },
    getChildNodeId(rootNode, nodeIds) {
      //递归获取所有子节点ID
      nodeIds.push(rootNode.id);
      if (rootNode.children) {
        for (let i = 0; i < rootNode.children.length; i++) {
          this.getChildNodeId(rootNode.children[i], nodeIds);
        }
      }
    },
    getParentNodes(rootNode, pNodes) {
      if (rootNode.parent && rootNode.parent.id != 0) {
        this.getParentNodes(rootNode.parent, pNodes);
      }
      if (rootNode.data.name && rootNode.data.name != "") {
        pNodes.push(rootNode.data);
      }
    },
    setCurrentKey(currentNode) {
      if (currentNode && currentNode.data) {
        this.$nextTick(() => {
          this.handleNodeSelect(currentNode);
          this.$refs.tree.setCurrentKey(currentNode.data.id);
        })
      }
    },
    isDefault(data) {
      return data.name === this.defaultLabel && data.level === 1;
    },
    getLocalDefaultName() {
      return this.$t('commons.default_module.' + this.localSuffix);
    }
  }
};
</script>

<style scoped>
.el-dropdown-link {
  cursor: pointer;
  color: #409eff;
}

.el-icon-arrow-down {
  font-size: 12px;
}

.custom-tree-node {
  flex: 1 1 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding-right: 8px;
  width: 100%;
}

.node-tree {
  margin-top: 15px;
}

.father .child {
  display: none;
}

.father:hover .child {
  display: block;
}

.node-title {
  width: 0px;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1 1 auto;
  padding: 0px 5px;
  overflow: hidden;
}

.count-title {
  width: auto;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 0px 5px;
  overflow: hidden;
}

.node-operate > i {
  color: #409eff;
  margin: 0px 5px;
}

.name-input {
  height: 25px;
  line-height: 25px;
}

.name-input >>> .el-input__inner {
  height: 25px;
  line-height: 25px;
}
</style>
