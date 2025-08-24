<template>
  <div class="skeleton-card">
    <div class="skeleton-header">
      <div class="skeleton skeleton-avatar"></div>
      <div class="skeleton-text-group">
        <div class="skeleton skeleton-title"></div>
        <div class="skeleton skeleton-subtitle"></div>
      </div>
    </div>
    <div class="skeleton-content">
      <div class="skeleton skeleton-paragraph"></div>
      <div class="skeleton skeleton-paragraph short"></div>
      <div class="skeleton skeleton-paragraph"></div>
    </div>
    <div class="skeleton-footer">
      <div class="skeleton skeleton-button"></div>
      <div class="skeleton skeleton-button secondary"></div>
    </div>
  </div>
</template>

<script setup>
// 卡片骨架屏组件
</script>

<style scoped>
.skeleton-card {
  background: var(--bg-elevated);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
  border: 1px solid var(--border-primary);
}

.skeleton-header {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.skeleton-avatar {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-full);
  flex-shrink: 0;
}

.skeleton-text-group {
  flex: 1;
  min-width: 0;
}

.skeleton-title {
  height: 20px;
  width: 70%;
  margin-bottom: var(--space-2);
}

.skeleton-subtitle {
  height: 16px;
  width: 50%;
}

.skeleton-content {
  margin-bottom: var(--space-6);
}

.skeleton-paragraph {
  height: 16px;
  margin-bottom: var(--space-2);
}

.skeleton-paragraph:last-child {
  margin-bottom: 0;
}

.skeleton-paragraph.short {
  width: 60%;
}

.skeleton-footer {
  display: flex;
  gap: var(--space-3);
}

.skeleton-button {
  height: 36px;
  width: 80px;
  border-radius: var(--radius-md);
}

.skeleton-button.secondary {
  width: 60px;
}

/* 骨架屏基础样式 */
.skeleton {
  background: linear-gradient(
    90deg,
    var(--color-secondary-200) 25%,
    var(--color-secondary-100) 50%,
    var(--color-secondary-200) 75%
  );
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s infinite ease-in-out;
  border-radius: var(--radius-base);
}

[data-theme="dark"] .skeleton {
  background: linear-gradient(
    90deg,
    var(--color-secondary-700) 25%,
    var(--color-secondary-600) 50%,
    var(--color-secondary-700) 75%
  );
  background-size: 200% 100%;
}

@keyframes skeleton-loading {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

/* 减少动画支持 */
@media (prefers-reduced-motion: reduce) {
  .skeleton {
    animation: none;
    background: var(--color-secondary-200);
  }
  
  [data-theme="dark"] .skeleton {
    background: var(--color-secondary-700);
  }
}
</style>