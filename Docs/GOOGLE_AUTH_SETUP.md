# 🔐 دليل إعداد Google Authentication للحل الدائم

## 🎯 المشكلة والحل

### ❌ المشكلة السابقة
```
Google Access Token → ينتهي بعد ساعة واحدة ⏰
المستخدم → يدخل token جديد يدوياً كل ساعة 😫
التجربة → سيئة جداً ❌
```

### ✅ الحل الجديد: Google Sign-In
```
المستخدم → يضغط "Sign in with Google" مرة واحدة فقط! 🎉
البرنامج → يحصل على Refresh Token (يستمر 6+ أشهر) ✅
البرنامج → يجدد Access Token تلقائياً في الخلفية 🔄
المستخدم → لا يفعل شيء! 😊
```

---

## 📋 الخطوات المطلوبة

### 1️⃣ إعداد Google Cloud Console

#### أ. إنشاء مشروع (إذا لم يكن موجوداً)
```bash
1. اذهب إلى: https://console.cloud.google.com/
2. اختر مشروعك: chat-ui-e1c11
3. أو أنشئ مشروع جديد
```

#### ب. تفعيل APIs المطلوبة
```bash
1. اذهب إلى: APIs & Services > Library
2. فعّل:
   ✅ Google Sign-In API
   ✅ Vertex AI API
   ✅ Cloud Resource Manager API
```

#### ج. إنشاء OAuth 2.0 Credentials

**1. انتقل إلى:**
```
APIs & Services > Credentials > Create Credentials > OAuth 2.0 Client ID
```

**2. اختر Application Type:**
```
Application type: Android
```

**3. املأ البيانات:**

```
Name: Chat UI Android
Package name: com.example.chat_ui

SHA-1 certificate fingerprint:
```

**كيفية الحصول على SHA-1:**

```bash
# Debug keystore (للتطوير)
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android

# Release keystore (للإنتاج)
keytool -list -v -keystore /path/to/your/release.keystore -alias your-key-alias
```

**4. احصل على:**
```
✅ Client ID: xxxxx.apps.googleusercontent.com
✅ Download JSON (احفظه - ستحتاجه!)
```

#### د. إنشاء Web OAuth Client (للـ Server Auth Code)

**مهم جداً:** تحتاج أيضاً Web Client ID!

```
1. Create Credentials > OAuth 2.0 Client ID
2. Application type: Web application
3. Name: Chat UI Web
4. لا تحتاج Redirect URIs
5. احصل على: Web Client ID
```

---

### 2️⃣ إضافة Dependencies

#### `app/build.gradle.kts`

```kotlin
dependencies {
    // ... dependencies موجودة
    
    // Google Sign-In (أضف هذه السطور)
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("com.google.android.gms:play-services-base:18.3.0")
}
```

#### Sync Project
```bash
File > Sync Project with Gradle Files
```

---

### 3️⃣ إضافة Client ID للمشروع

#### أ. في `strings.xml`

**`app/src/main/res/values/strings.xml`:**

```xml
<resources>
    <!-- ... strings موجودة -->
    
    <!-- Google Sign-In -->
    <string name="google_server_client_id">YOUR_WEB_CLIENT_ID.apps.googleusercontent.com</string>
</resources>
```

**⚠️ مهم:** استخدم **Web Client ID** وليس Android Client ID!

#### ب. تحديث `config.properties`

**`app/src/main/assets/config.properties`:**

```properties
# Google OAuth Configuration
GOOGLE_WEB_CLIENT_ID=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

---

### 4️⃣ تهيئة GoogleAuthManager

#### في `MainActivity.kt`

```kotlin
import com.example.chat_ui.auth.GoogleAuthManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ... existing code
        
        // Initialize Google Auth
        val serverClientId = getString(R.string.google_server_client_id)
        GoogleAuthManager.initialize(this, serverClientId)
        
        // ... rest of code
    }
}
```

---

### 5️⃣ تحديث API Settings Screen

#### استخدم الـ UI الجديد مع Google Sign-In

```kotlin
@Composable
fun ApiSettingsScreenV3(onBackClick: () -> Unit) {
    // ... existing code
    
    var useGoogleAuth by remember { mutableStateOf(false) }
    
    // Show Google Sign-In option for Vertex AI
    if (selectedProvider == ApiProvider.GOOGLE_VERTEX_AI) {
        Column {
            Text("Authentication Method")
            
            Row {
                RadioButton(
                    selected = !useGoogleAuth,
                    onClick = { useGoogleAuth = false }
                )
                Text("Manual Token")
            }
            
            Row {
                RadioButton(
                    selected = useGoogleAuth,
                    onClick = { useGoogleAuth = true }
                )
                Text("Google Sign-In (Recommended)")
            }
            
            if (useGoogleAuth) {
                Spacer(modifier = Modifier.height(8.dp))
                GoogleSignInButton(
                    onSignInSuccess = { email, name ->
                        testResult = "✓ Signed in as $email"
                    },
                    onSignInError = { error ->
                        testResult = "✗ Error: $error"
                    },
                    isSignedIn = GoogleAuthManager.isSignedIn()
                )
            }
        }
    }
}
```

---

## 🔄 كيف يعمل النظام

### تدفق العمل

```
1. المستخدم يضغط "Sign in with Google"
   ↓
2. نافذة Google Sign-In تظهر
   ↓
3. المستخدم يختار حساب Google
   ↓
4. البرنامج يحصل على:
   - ID Token (للتعريف)
   - Server Auth Code (للحصول على Refresh Token)
   ↓
5. البرنامج يحفظ:
   - Access Token (صالح ساعة)
   - معلومات الحساب
   ↓
6. عند كل API request:
   - التحقق من Access Token
   - إذا منتهي → جدده تلقائياً
   - استخدم Token جديد
```

### Auto-Refresh Mechanism

```kotlin
// في ChatStreamingClient
val accessToken = when (providerConfig.getAuthMethod()) {
    AuthMethod.GOOGLE_SIGN_IN -> {
        // يجدد تلقائياً إذا منتهي!
        GoogleAuthManager.getAccessToken().token
    }
    AuthMethod.API_KEY -> providerConfig.apiKey
}
```

---

## 🧪 الاختبار

### 1. اختبار Google Sign-In

```kotlin
// في أي Composable
GoogleSignInButton(
    onSignInSuccess = { email, displayName ->
        Log.i("Test", "Signed in: $email - $displayName")
    },
    onSignInError = { error ->
        Log.e("Test", "Error: $error")
    },
    isSignedIn = GoogleAuthManager.isSignedIn()
)
```

### 2. اختبار Token Refresh

```kotlin
// Get token (يجدد تلقائياً إذا منتهي)
lifecycleScope.launch {
    val result = GoogleAuthManager.getAccessToken()
    when (result) {
        is GoogleAuthManager.TokenResult.Success -> {
            Log.i("Test", "Token: ${result.token}")
        }
        is GoogleAuthManager.TokenResult.Error -> {
            Log.e("Test", "Error: ${result.message}")
        }
    }
}
```

### 3. اختبار API Call مع Google Auth

```kotlin
// في ApiSettingsScreen
Button(onClick = {
    scope.launch {
        val config = ProviderConfig(
            provider = ApiProvider.GOOGLE_VERTEX_AI,
            baseUrl = "https://...",
            useGoogleAuth = true
        )
        
        // Test request
        val token = GoogleAuthManager.getAccessToken()
        // Make API call with token...
    }
}) {
    Text("Test API with Google Auth")
}
```

---

## 🔐 الأمان

### Best Practices

1. **لا تشارك Client Secret في Android**
   ```kotlin
   // ❌ سيء - لا تضع Client Secret في Android
   val clientSecret = "xxxxxx"
   
   // ✅ جيد - Client Secret فقط في Backend
   // Android يستخدم فقط Client ID
   ```

2. **استخدم ProGuard Rules**
   
   **`proguard-rules.pro`:**
   ```proguard
   # Keep Google Sign-In classes
   -keep class com.google.android.gms.** { *; }
   -dontwarn com.google.android.gms.**
   ```

3. **Validate Tokens في Backend**
   ```kotlin
   // للإنتاج: تحقق من ID Token في backend
   // لا تثق بالـ client فقط
   ```

---

## 🐛 استكشاف الأخطاء

### خطأ: "Sign-In failed: 10"
**السبب:** SHA-1 fingerprint غير صحيح

**الحل:**
```bash
# احصل على SHA-1 الصحيح
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android

# أضفه في Google Cloud Console:
# APIs & Services > Credentials > OAuth 2.0 Client > Edit
```

### خطأ: "API not enabled"
**السبب:** Google Sign-In API غير مفعّل

**الحل:**
```
1. Google Cloud Console
2. APIs & Services > Library
3. ابحث عن "Google Sign-In API"
4. اضغط "Enable"
```

### خطأ: "Invalid client ID"
**السبب:** استخدام Android Client ID بدلاً من Web Client ID

**الحل:**
```kotlin
// ⚠️ مهم: استخدم Web Client ID
val serverClientId = "xxxxx.apps.googleusercontent.com" // Web Client!
GoogleAuthManager.initialize(context, serverClientId)
```

### Token لا يتجدد تلقائياً
**السبب:** لم يتم طلب `serverAuthCode`

**الحل:**
```kotlin
// في GoogleAuthManager.initialize()
val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
    .requestServerAuthCode(serverClientId) // ✅ مهم!
    .requestEmail()
    .requestScopes(Scope(VERTEX_AI_SCOPE))
    .build()
```

---

## 📊 مقارنة الحلول

| الحل | المدة | التجربة | التعقيد |
|------|-------|---------|---------|
| **Manual Token** | ساعة واحدة | سيئة جداً 😫 | بسيط |
| **Google Sign-In** | 6+ أشهر | ممتازة 🎉 | متوسط |
| **Service Account** | دائم | جيدة | معقد |

---

## 🚀 الخطوات التالية

### للتطوير
- [x] إضافة Google Sign-In UI
- [x] تنفيذ Auto-refresh
- [ ] اختبار مع مستخدمين حقيقيين
- [ ] إضافة error handling محسّن

### للإنتاج
- [ ] استخدام Release keystore SHA-1
- [ ] إضافة Backend لتحويل Auth Code → Refresh Token
- [ ] إضافة Analytics لتتبع sign-ins
- [ ] تحسين UX للـ sign-out/re-auth

---

## 💡 نصائح إضافية

### 1. Cache Strategy
```kotlin
// Token يُحفظ في memory مع expiry time
// Auto-refresh قبل انتهاء Token بـ 5 دقائق
private var tokenExpiryTime = System.currentTimeMillis() + (55 * 60 * 1000)
```

### 2. Multiple Accounts
```kotlin
// يمكن للمستخدم تبديل الحسابات
GoogleAuthManager.signOut()
// ثم Sign in بحساب جديد
```

### 3. Offline Handling
```kotlin
// إذا offline، استخدم cached token
if (!isNetworkAvailable()) {
    return cachedAccessToken
}
```

---

## 📚 المراجع

- [Google Sign-In for Android](https://developers.google.com/identity/sign-in/android/start)
- [OAuth 2.0 for Mobile Apps](https://developers.google.com/identity/protocols/oauth2/native-app)
- [Vertex AI Authentication](https://cloud.google.com/vertex-ai/docs/authentication)

---

**آخر تحديث:** 19 ديسمبر 2025  
**الإصدار:** 2.0  
**المطور:** Chat UI Team

---

## ✅ Checklist للإعداد

- [ ] إنشاء OAuth 2.0 Client في Google Cloud Console
- [ ] الحصول على Web Client ID
- [ ] الحصول على SHA-1 fingerprint
- [ ] إضافة Client ID في strings.xml
- [ ] إضافة Google Sign-In dependency
- [ ] تهيئة GoogleAuthManager في MainActivity
- [ ] تحديث API Settings UI
- [ ] اختبار Sign-In
- [ ] اختبار Auto-refresh
- [ ] اختبار API calls

**عند إتمام كل الخطوات، ستحصل على تجربة مستخدم سلسة بدون الحاجة لإدخال tokens يدوياً!** 🎉
