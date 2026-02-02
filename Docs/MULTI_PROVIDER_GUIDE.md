# دليل دعم Multiple API Providers

## 📋 نظرة عامة

تم إضافة دعم لمزودي API متعددين في التطبيق، مما يسمح لك بالتبديل بين:
- **HuggingFace Router** (الافتراضي) - 114+ نموذج
- **Google Vertex AI** - نماذج Gemini وغيرها

## 🎯 الميزات الجديدة

### 1. نظام Multi-Provider
- اختيار سهل بين المزودين
- حفظ تلقائي للإعدادات
- دعم OpenAI-compatible API format
- Headers مخصصة لكل provider

### 2. واجهة مستخدم محسّنة
- قائمة منسدلة لاختيار Provider
- عرض Base URL الافتراضي لكل provider
- زر Reset للعودة للإعدادات الافتراضية
- رسائل مساعدة لكل provider

### 3. Configuration Management
- حفظ في SharedPreferences
- دعم config.properties
- API متقدم للتحكم

## 🏗️ البنية المعمارية

### الملفات الجديدة

#### 1. `ApiProvider.kt`
```kotlin
enum class ApiProvider {
    HUGGINGFACE,
    GOOGLE_VERTEX_AI
}

data class ProviderConfig(
    val provider: ApiProvider,
    val baseUrl: String,
    val apiKey: String
)
```

#### 2. `ApiSettingsScreenV2.kt`
واجهة مستخدم محسّنة مع:
- Provider selection dropdown
- Dynamic placeholders
- Reset to default button
- Helper messages

### التعديلات على الملفات الموجودة

#### 1. `ConfigManager.kt`
```kotlin
// دوال جديدة
fun getApiProvider(): ApiProvider
fun setApiProvider(provider: ApiProvider)
fun getProviderConfig(): ProviderConfig
fun saveProviderConfig(config: ProviderConfig)
```

#### 2. `ChatStreamingClient.kt`
```kotlin
// استخدام ProviderConfig بدلاً من hardcoded values
val providerConfig = ConfigManager.getProviderConfig()

// Build request with provider-specific headers
providerConfig.getHeaders().forEach { (key, value) ->
    requestBuilder.addHeader(key, value)
}
```

## 🚀 كيفية الاستخدام

### للمستخدم النهائي

1. **افتح API Settings**
   - من القائمة الجانبية → Settings → API Configuration

2. **اختر Provider**
   - اضغط على قائمة "API Provider"
   - اختر بين HuggingFace أو Google Vertex AI

3. **أدخل البيانات**
   - **HuggingFace:**
     - Base URL: `https://router.huggingface.co/v1`
     - API Key: `hf_...` (من huggingface.co/settings/tokens)
   
   - **Google Vertex AI:**
     - Base URL: `https://us-central1-aiplatform.googleapis.com/v1/projects/PROJECT_ID/locations/LOCATION/endpoints/openapi`
     - API Key: استخدم `gcloud auth print-access-token`

4. **احفظ الإعدادات**
   - اضغط "Save" لحفظ الإعدادات
   - استخدم "Test" لاختبار الاتصال

### للمطورين

#### استخدام ProviderConfig في الكود

```kotlin
// الحصول على الإعدادات الحالية
val config = ConfigManager.getProviderConfig()

// التحقق من الصلاحية
if (config.isValid()) {
    val url = config.getChatCompletionsUrl()
    val headers = config.getHeaders()
}

// حفظ إعدادات جديدة
val newConfig = ProviderConfig(
    provider = ApiProvider.GOOGLE_VERTEX_AI,
    baseUrl = "https://...",
    apiKey = "ya29..."
)
ConfigManager.saveProviderConfig(newConfig)
```

#### إضافة Provider جديد

1. أضف enum value في `ApiProvider.kt`:
```kotlin
enum class ApiProvider(val displayName: String, val defaultBaseUrl: String) {
    HUGGINGFACE(...),
    GOOGLE_VERTEX_AI(...),
    NEW_PROVIDER(
        displayName = "New Provider",
        defaultBaseUrl = "https://api.newprovider.com/v1"
    )
}
```

2. (اختياري) عدّل `getAuthHeader()` إذا كان format مختلف:
```kotlin
fun getAuthHeader(): String {
    return when (provider) {
        ApiProvider.HUGGINGFACE -> "Bearer $apiKey"
        ApiProvider.GOOGLE_VERTEX_AI -> "Bearer $apiKey"
        ApiProvider.NEW_PROVIDER -> "X-API-Key $apiKey" // مثال
    }
}
```

## 🔧 الإعدادات

### config.properties
```properties
# Default provider
API_PROVIDER=HUGGINGFACE

# Base URLs (will be overridden by selected provider)
OPENAI_BASE_URL=https://router.huggingface.co/v1

# API Key (configure in app settings)
OPENAI_API_KEY=
```

### local.properties (اختياري)
```properties
# Override for development
API_PROVIDER=GOOGLE_VERTEX_AI
OPENAI_BASE_URL=https://us-central1-aiplatform.googleapis.com/v1/projects/my-project/locations/us-central1/endpoints/openapi
OPENAI_API_KEY=ya29.....
```

## 📊 مقارنة بين Providers

| الميزة | HuggingFace Router | Google Vertex AI |
|--------|-------------------|------------------|
| **عدد النماذج** | 114+ | متعدد (Gemini, etc) |
| **Authentication** | Bearer Token (HF) | Bearer Token (Google) |
| **Base URL Format** | `/v1` | `/v1/projects/.../endpoints/openapi` |
| **التكلفة** | حسب النموذج | حسب الاستخدام |
| **Setup** | سهل - مجرد token | يحتاج Google Cloud setup |
| **المميزات** | Smart routing, 114+ models | Gemini models, Enterprise features |

## 🔐 الأمان

### Best Practices

1. **لا تحفظ API Keys في الكود**
   ```kotlin
   // ❌ سيء
   val apiKey = "hf_xxxxx"
   
   // ✅ جيد
   val apiKey = ConfigManager.openAiApiKey
   ```

2. **استخدم local.properties للتطوير**
   - لا يتم commit في Git
   - آمن للمفاتيح الخاصة

3. **Google Access Tokens**
   - قصيرة الأجل (1 ساعة)
   - يجب تجديدها دورياً
   - استخدم Service Accounts في الإنتاج

## 🧪 الاختبار

### اختبار HuggingFace

```bash
curl -X POST https://router.huggingface.co/v1/chat/completions \
  -H "Authorization: Bearer hf_xxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "omni",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

### اختبار Google Vertex AI

```bash
# الحصول على Access Token
gcloud auth print-access-token

# الاختبار
curl -X POST \
  https://us-central1-aiplatform.googleapis.com/v1/projects/PROJECT_ID/locations/us-central1/endpoints/openapi/chat/completions \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemini-2.5-flash",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

## 🐛 استكشاف الأخطاء

### خطأ: "Invalid API configuration"
**الحل:**
- تأكد من Base URL صحيح
- تأكد من API Key غير فارغ
- اختبر الاتصال باستخدام curl

### خطأ: "Connection failed"
**الحل:**
- تحقق من اتصال الإنترنت
- تحقق من صلاحية API Key
- للـ Google: تأكد من Access Token لم ينته

### خطأ: "401 Unauthorized"
**الحل:**
- **HuggingFace:** تحقق من صلاحية HF Token
- **Google:** جدد Access Token: `gcloud auth print-access-token`

### Google Access Token منتهي
```bash
# تجديد Token
gcloud auth login
gcloud auth print-access-token

# أو استخدم Service Account
gcloud auth activate-service-account --key-file=key.json
gcloud auth print-access-token
```

## 📝 أمثلة عملية

### مثال 1: التبديل إلى Google Vertex AI برمجياً

```kotlin
fun switchToGoogleVertexAI() {
    val config = ProviderConfig(
        provider = ApiProvider.GOOGLE_VERTEX_AI,
        baseUrl = "https://us-central1-aiplatform.googleapis.com/v1/projects/chat-ui-e1c11/locations/us-central1/endpoints/openapi",
        apiKey = getGoogleAccessToken() // من gcloud
    )
    ConfigManager.saveProviderConfig(config)
}
```

### مثال 2: استخدام Custom Headers

```kotlin
val config = ProviderConfig(
    provider = ApiProvider.GOOGLE_VERTEX_AI,
    baseUrl = "https://...",
    apiKey = "ya29...",
    customHeaders = mapOf(
        "X-Custom-Header" to "value",
        "User-Agent" to "ChatUI-Android/1.0"
    )
)
```

### مثال 3: Validation قبل الحفظ

```kotlin
fun validateAndSave(config: ProviderConfig): Boolean {
    if (!config.isValid()) {
        showError("Invalid configuration")
        return false
    }
    
    ConfigManager.saveProviderConfig(config)
    showSuccess("Configuration saved")
    return true
}
```

## 🔄 Migration من النظام القديم

### الكود القديم
```kotlin
val baseUrl = ConfigManager.openAiBaseUrl
val apiKey = ConfigManager.openAiApiKey
```

### الكود الجديد
```kotlin
val config = ConfigManager.getProviderConfig()
val baseUrl = config.getChatCompletionsUrl()
val headers = config.getHeaders()
```

## 📚 المراجع

- [HuggingFace Router Docs](https://huggingface.co/docs/api-inference/index)
- [Google Vertex AI Docs](https://cloud.google.com/vertex-ai/docs)
- [OpenAI API Format](https://platform.openai.com/docs/api-reference)

## 💡 نصائح للأداء

1. **Cache Access Tokens**
   - Google tokens صالحة لمدة ساعة
   - احفظها وأعد استخدامها

2. **Error Handling**
   - تحقق من `config.isValid()` قبل كل request
   - اعرض رسائل خطأ واضحة للمستخدم

3. **Logging**
   ```kotlin
   Log.i(TAG, "Using provider: ${config.provider.displayName}")
   Log.d(TAG, "Request URL: ${config.getChatCompletionsUrl()}")
   ```

## ✅ خطة التطوير المستقبلية

- [ ] إضافة providers إضافية (Anthropic, Azure OpenAI)
- [ ] Auto-refresh للـ Google Access Tokens
- [ ] Connection testing مباشر في UI
- [ ] Provider-specific model lists
- [ ] Error recovery mechanisms
- [ ] Analytics لاستخدام كل provider

---

**آخر تحديث:** 19 ديسمبر 2025  
**الإصدار:** 1.0  
**المطور:** Chat UI Team
