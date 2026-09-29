<template>
  <main class="login-page">
    <div class="login-card">
      <div class="login-language">
        <i @click="toggleLanguage">
          {{ $locale === "zh" ? "English" : "中文" }}
        </i>
      </div>
      <el-form @submit.native.prevent="submit">
        <img src="/wordart-black.svg" alt="Langfuse" />
        <h1>{{ copy.title }}</h1>
        <el-alert
          v-if="expired"
          :title="copy.expired"
          type="warning"
          :closable="false"
          show-icon
          class="session-expired"
        />
        <el-form-item :label="copy.aamId">
          <el-input v-model.trim="aamId" autocomplete="username" autofocus />
        </el-form-item>
        <el-form-item :label="copy.ticket">
          <el-input v-model="ticket" type="password" autocomplete="current-password" />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="login-error" />
        <el-button type="primary" native-type="submit" :loading="submitting" :disabled="submitting" class="login-submit">
          {{ submitting ? copy.signingIn : copy.signIn }}
        </el-button>
      </el-form>
    </div>
  </main>
</template>

<script>
export default {
  props: { expired: { type: Boolean, default: false }, onLogin: { type: Function, required: true } },
  data: () => ({ aamId: "38971135", ticket: "mock-ticket", submitting: false, error: "" }),
  computed: {
    copy() {
      return this.$locale === "zh"
        ? {
            title: "AIOps 可观测平台",
            subtitle: "使用 ICBC AAM 统一认证登录",
            expired: "登录会话已过期，请重新认证。",
            aamId: "统一认证号",
            ticket: "认证票据",
            signIn: "登录",
            signingIn: "登录中…",
            hint: "当前开发环境使用 Mock AAM 验票。",
            failed: "登录失败，请检查统一认证号和票据。"
          }
        : {
            title: "AIOps Observability",
            subtitle: "Sign in with ICBC AAM",
            expired: "Your session has expired. Please sign in again.",
            aamId: "AAM ID",
            ticket: "Authentication ticket",
            signIn: "Sign in",
            signingIn: "Signing in…",
            hint: "The development environment uses Mock AAM verification.",
            failed: "Sign-in failed. Check your AAM ID and ticket."
          };
    }
  },
  methods: {
    toggleLanguage() {
      this.$store.dispatch("changeLanguage", this.$locale === "zh" ? "en" : "zh");
    },
    async submit() {
      if (this.submitting) return;
      this.error = "";
      this.submitting = true;
      try {
        await this.onLogin(this.aamId, this.ticket);
      } catch (e) {
        this.error = (e && e.message) || this.copy.failed;
      } finally {
        this.submitting = false;
      }
    }
  }
};
</script>

<style lang="scss" scoped>
.login-card {
  position: relative;
  .login-language {
    position: absolute;
    display: inline-block;
    right: 10px;
    top: 10px;
    cursor: pointer;
    color: #667085;
    font-size:12px;
    &:hover {
      color: #1586ed;
    }
    i {
      font-style: normal;
    }
  }
  .el-form-item {
    margin-bottom: 14px;
  }
  .el-form-item__label {
    font-size: 13px;
    font-weight: 600;
    padding-bottom: 4px;
  }
  .el-input__inner {
    padding: 10px;
    border-radius: 6px;
  }
  .login-submit {
    width: 100%;
    border-radius: 6px;
    font-weight: 700;
    padding: 10px;
  }
  .login-error {
    margin-bottom: 8px;
  }
  .session-expired {
    margin-bottom: 8px;
  }
}
</style>
