<template>
  <mavon-editor :id="id" :editable="!disabled" @imgAdd="imgAdd" :default-open="defaultOpenValue"
                :xss-options="xssOptions" :style="{'min-height': customMinHeight + 'px', 'min-width': '100px'}"
                @change="change"
                :subfield="false" :toolbars="toolbars" :language="language" :toolbarsFlag="!disabled"
                @imgDel="imgDel" v-model="data[prop]" ref="md"/>
</template>

<script>
import {getCurrentUser, getUUID} from "@/common/js/utils";
import {deleteMarkDownImg, uploadMarkDownImg} from "@/network/image";
import {DEFAULT_XSS_ATTR} from "@/common/js/constants";

export default {
  name: "MsMarkDownText",
  components: {},
  props: {
    data: Object,
    prop: String,
    disabled: Boolean,
    defaultOpen: {
      type: String,
      default() {
        return 'preview';
      }
    },
    autoReview: {
      type: Boolean,
      default() {
        return true;
      }
    },
    customMinHeight: {
      type: [Number, String],
      default() {
        return 20;
      }
    },
    toolbars: {
      type: Object,
      default() {
        return {
          bold: true, // 粗体
          italic: true, // 斜体
          header: true, // 标题
          underline: true, // 下划线
          strikethrough: true, // 中划线
          mark: true, // 标记
          superscript: true, // 上角标
          subscript: true, // 下角标
          quote: true, // 引用
          ol: true, // 有序列表
          ul: true, // 无序列表
          link: true, // 链接
          imagelink: true, // 图片链接
          code: true, // code
          table: true, // 表格
          fullscreen: true, // 全屏编辑
          readmodel: true, // 沉浸式阅读
          htmlcode: true, // 展示html源码
          help: true, // 帮助
          /* 1.3.5 */
          undo: true, // 上一步
          redo: true, // 下一步
          trash: true, // 清空
          save: false, // 保存（触发events中的save事件）
          /* 1.4.2 */
          navigation: true, // 导航目录
          /* 2.1.8 */
          alignleft: true, // 左对齐
          aligncenter: true, // 居中
          alignright: true, // 右对齐
          /* 2.2.1 */
          subfield: true, // 单双栏模式
          preview: true, // 预览
        }
      }
    }
  },
  data() {
    return {
      result: {loading: false},
      id: getUUID(),
      xssOptions: {
        whiteList: {
          div: DEFAULT_XSS_ATTR,
          p: DEFAULT_XSS_ATTR,
          br: [],
          h1: DEFAULT_XSS_ATTR,
          h2: DEFAULT_XSS_ATTR,
          h3: DEFAULT_XSS_ATTR,
          h4: DEFAULT_XSS_ATTR,
          h5: DEFAULT_XSS_ATTR,
          h6: DEFAULT_XSS_ATTR,
          hr: DEFAULT_XSS_ATTR,
          span: DEFAULT_XSS_ATTR,
          strong: DEFAULT_XSS_ATTR,
          b: DEFAULT_XSS_ATTR,
          i: DEFAULT_XSS_ATTR,
          pre: DEFAULT_XSS_ATTR,
          code: DEFAULT_XSS_ATTR,
          tr: DEFAULT_XSS_ATTR,
          table: [...DEFAULT_XSS_ATTR, 'width', 'border'],
          td: [...DEFAULT_XSS_ATTR, 'width', 'colspan'],
          th: [...DEFAULT_XSS_ATTR, 'width', 'colspan'],
          a: [...DEFAULT_XSS_ATTR, 'target', 'href', 'title', 'rel'],
          img: [...DEFAULT_XSS_ATTR, "src", "alt", "width", "height"],
          tbody: DEFAULT_XSS_ATTR,
          ul: DEFAULT_XSS_ATTR,
          li: DEFAULT_XSS_ATTR,
          ol: DEFAULT_XSS_ATTR,
          dl: DEFAULT_XSS_ATTR,
          dt: DEFAULT_XSS_ATTR,
          em: DEFAULT_XSS_ATTR,
          blockquote: DEFAULT_XSS_ATTR,
        },
        stripIgnoreTagBody: true
      },
      defaultOpenValue: 'preview'
    }
  },

  computed: {
    language() {
      const user = getCurrentUser();
      // 取值为空时默认中文，如：导出
      if (!user) {
        return 'zh_CN';
      }
      const language = user.language;
      switch (language) {
        case 'zh_CN':
          return 'zh-CN';
        case 'zh_TW':
          return 'zh-TW';
        case 'en_US':
          return 'en';
        default:
          return 'zh-CN';
      }
    }
  },
  watch: {
    defaultOpen() {
      if (this.defaultOpen) {
        this.defaultOpenValue = this.defaultOpen;
      }
    }
  },
  mounted() {
    if (!this.disabled) {
      // 点击编辑，失去焦点展示
      let el = document.getElementById(this.id);
      if (!this.autoReview) {
        this.defaultOpenValue = null;
      }
      if (this.defaultOpen) {
        this.defaultOpenValue = this.defaultOpen;
      }
      if (el) {
        el.addEventListener('click', () => {
          let imagePreview = el.getElementsByClassName('v-note-img-wrapper');
          if (imagePreview.length > 0) { // 图片预览的时候不切换到编辑模式
            if (this.autoReview) {
              this.defaultOpenValue = 'preview';
            }
          } else {
            if (this.autoReview) {
              this.defaultOpenValue = null;
            }
          }
        });
        let input = el.getElementsByClassName('auto-textarea-input');
        input[0].addEventListener('blur', () => {
          if (this.autoReview) {
            this.defaultOpenValue = 'preview';
          }
        });
      }
    }

  },
  methods: {
    imgAdd(pos, file) {
      this.result.loading = true;
      uploadMarkDownImg(file, (data) => {
        this.$success(this.$t('commons.save_success'));
        let url = '/resource/md/get?fileName=' + data;
        this.$refs.md.$img2Url(pos, url);
        this.result.loading = false;
      });
      this.$emit('imgAdd', file);
    },
    imgDel(file) {
      deleteMarkDownImg(file);
    },
    getContent() {
      return this.$refs.md.d_render;
    },
    getTextareaDom() {
      return this.$refs.md.getTextareaDom();
    },
    toolbar_left_click(param) {
      this.$refs.md.toolbar_left_click(param);
    },
    change() {
      this.$nextTick(() => {
        this.$emit('change');
      });
    }
  }
}
</script>

<style scoped>

.v-note-wrapper {
  display: block !important;
  position: static !important;
}

/deep/ .dropdown-item.dropdown-images {
  display: none;
}

/deep/ .v-note-op .v-left-item.transition .op-icon.fa.fa-mavon-picture-o.dropdown.dropdown-wrapper .op-image.popup-dropdown.transition {
  margin-left: 45px;
}


/deep/ .v-note-op .v-left-item.transition .add-image-link-wrapper .add-image-link .op-btn.sure {
  background: #783887;
}

</style>
