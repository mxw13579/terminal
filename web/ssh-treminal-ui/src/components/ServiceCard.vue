<template>
  <div class="service-card" :class="{ 'card-disabled': disabled }">
    <div class="card-header">
      <div class="card-icon">
        <i :class="icon"></i>
      </div>
      <div class="card-title-section">
        <h3 class="card-title">{{ title }}</h3>
        <p class="card-subtitle">{{ subtitle }}</p>
      </div>
    </div>
    
    <div class="card-body">
      <p class="card-description">{{ description }}</p>
      
      <div class="card-features">
        <div class="features-title">主要功能:</div>
        <ul class="features-list">
          <li v-for="feature in features" :key="feature" class="feature-item">
            <i class="fas fa-check"></i>
            <span>{{ feature }}</span>
          </li>
        </ul>
      </div>
    </div>
    
    <div class="card-footer">
      <button 
        :class="['card-button', buttonClass]"
        :disabled="disabled || loading"
        @click="handleClick"
      >
        <i v-if="loading" class="fas fa-spinner fa-spin"></i>
        <span>{{ loading ? '连接中...' : buttonText }}</span>
      </button>
      
      <div v-if="disabled && !loading" class="disabled-hint">
        <i class="fas fa-info-circle"></i>
        <span>请先连接服务器</span>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  title: {
    type: String,
    required: true
  },
  subtitle: {
    type: String,
    required: true
  },
  description: {
    type: String,
    required: true
  },
  icon: {
    type: String,
    required: true
  },
  features: {
    type: Array,
    default: () => []
  },
  buttonText: {
    type: String,
    default: '开始使用'
  },
  buttonClass: {
    type: String,
    default: 'btn-primary'
  },
  disabled: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['click'])

const handleClick = () => {
  if (!props.disabled && !props.loading) {
    emit('click')
  }
}
</script>

<style scoped>
.service-card {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  padding: 32px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
  overflow: hidden;
}

.service-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #4299e1, #667eea);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.service-card:hover::before {
  opacity: 1;
}

.service-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.card-disabled {
  opacity: 0.7;
  transform: none !important;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08) !important;
}

.card-disabled::before {
  background: linear-gradient(90deg, #a0aec0, #cbd5e0);
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  margin-bottom: 24px;
}

.card-icon {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #4299e1, #667eea);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4px 16px rgba(66, 153, 225, 0.3);
}

.card-disabled .card-icon {
  background: linear-gradient(135deg, #a0aec0, #cbd5e0);
  box-shadow: 0 4px 16px rgba(160, 174, 192, 0.2);
}

.card-icon i {
  font-size: 1.8rem;
  color: white;
}

.card-title-section {
  flex: 1;
}

.card-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #2d3748;
  margin: 0 0 8px 0;
  line-height: 1.2;
}

.card-subtitle {
  font-size: 1rem;
  color: #4299e1;
  margin: 0;
  font-weight: 500;
}

.card-disabled .card-subtitle {
  color: #a0aec0;
}

.card-body {
  flex: 1;
  margin-bottom: 32px;
}

.card-description {
  font-size: 1rem;
  color: #4a5568;
  line-height: 1.6;
  margin: 0 0 24px 0;
}

.card-features {
  background: #f8fafc;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #e2e8f0;
}

.features-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 12px;
}

.features-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.9rem;
  color: #4a5568;
}

.feature-item i {
  color: #48bb78;
  font-size: 0.8rem;
  flex-shrink: 0;
}

.card-disabled .feature-item i {
  color: #a0aec0;
}

.card-footer {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-button {
  width: 100%;
  padding: 16px 24px;
  border: none;
  border-radius: 12px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  position: relative;
  overflow: hidden;
}

.card-button::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.5s ease;
}

.card-button:hover::before {
  left: 100%;
}

.btn-primary {
  background: linear-gradient(135deg, #4299e1, #667eea);
  color: white;
  box-shadow: 0 4px 16px rgba(66, 153, 225, 0.3);
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #3182ce, #5a67d8);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(66, 153, 225, 0.4);
}

.btn-secondary {
  background: linear-gradient(135deg, #38b2ac, #319795);
  color: white;
  box-shadow: 0 4px 16px rgba(56, 178, 172, 0.3);
}

.btn-secondary:hover:not(:disabled) {
  background: linear-gradient(135deg, #319795, #2c7a7b);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(56, 178, 172, 0.4);
}

.card-button:disabled {
  background: #e2e8f0;
  color: #a0aec0;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.card-button:disabled::before {
  display: none;
}

.disabled-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 0.875rem;
  color: #a0aec0;
  text-align: center;
}

.disabled-hint i {
  font-size: 1rem;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .service-card {
    padding: 24px;
  }
  
  .card-header {
    gap: 16px;
    margin-bottom: 20px;
  }
  
  .card-icon {
    width: 56px;
    height: 56px;
  }
  
  .card-icon i {
    font-size: 1.5rem;
  }
  
  .card-title {
    font-size: 1.3rem;
  }
  
  .card-subtitle {
    font-size: 0.9rem;
  }
  
  .card-description {
    font-size: 0.95rem;
  }
  
  .card-features {
    padding: 16px;
  }
  
  .card-button {
    padding: 14px 20px;
    font-size: 0.95rem;
  }
}

@media (max-width: 480px) {
  .service-card {
    padding: 20px;
  }
  
  .card-header {
    flex-direction: column;
    text-align: center;
    gap: 12px;
  }
  
  .card-icon {
    margin: 0 auto;
  }
  
  .card-title {
    font-size: 1.2rem;
  }
}
</style>