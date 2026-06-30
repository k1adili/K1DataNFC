# K1 Data NFC 📡🔐

اپلیکیشن اندروید برای ذخیره اطلاعات رمزگذاری‌شده روی تگ‌های NFC

---

## ✅ امکانات

- **اسکن تگ NFC**: هر تگ با شناسه سخت‌افزاری منحصربه‌فرد شناسایی می‌شود
- **ذخیره متن و تصویر**: متن نامحدود + چند تصویر برای هر تگ
- **رمزنگاری AES-256-GCM**: با استفاده از Android Keystore — امن‌ترین روش
- **قابلیت ویرایش**: هر زمان می‌توانید اطلاعات تگ را ویرایش کنید
- **پشتیبان‌گیری ZIP**: فایل رمزگذاری‌شده — روی حافظه گوشی، Drive یا Dropbox
- **جستجو**: سریع بین تگ‌ها جستجو کنید
- **پشتیبانی از Android 4.4+** (API 19 به بالا)

---

## 🏗️ ساختار پروژه

```
K1DataNFC/
├── app/src/main/
│   ├── java/com/k1datanfc/
│   │   ├── K1Application.java        ← Application class
│   │   ├── EncryptionManager.java    ← AES-256-GCM encryption
│   │   ├── DatabaseManager.java      ← Encrypted JSON storage
│   │   ├── BackupManager.java        ← ZIP backup/restore
│   │   ├── NfcHelper.java            ← NFC foreground dispatch
│   │   ├── NfcTag.java               ← Data model
│   │   ├── MainActivity.java         ← Tag list + NFC scanning
│   │   ├── TagDetailActivity.java    ← View/edit tag data
│   │   ├── TagListAdapter.java       ← RecyclerView adapter
│   │   ├── BackupActivity.java       ← Backup/restore UI
│   │   └── SettingsActivity.java     ← PIN + info
│   ├── res/layout/
│   │   ├── activity_main.xml
│   │   ├── activity_tag_detail.xml
│   │   ├── activity_backup.xml
│   │   ├── activity_settings.xml
│   │   ├── item_tag.xml
│   │   └── item_image.xml
│   ├── res/xml/
│   │   ├── nfc_tech_filter.xml
│   │   └── file_paths.xml
│   └── AndroidManifest.xml
```

---

## 🔒 معماری امنیتی

```
داده خام  →  AES-256-GCM  →  فایل .enc در حافظه داخلی اپ
                ↑
         Android Keystore
         (کلید هرگز از دستگاه خارج نمی‌شود)
```

- **متن**: رمزگذاری شده در فایل JSON  
- **تصاویر**: هر تصویر به‌صورت جداگانه رمزگذاری شده (`.enc`)
- **پشتیبان**: ZIP حاوی فایل‌های رمزگذاری‌شده — بدون کلید قابل خواندن نیست

---

## 📲 نحوه Build

### پیش‌نیازها
- Android Studio Hedgehog یا جدیدتر
- JDK 17
- Android SDK (API 19–34)

### مراحل
```bash
# ۱. باز کردن پروژه در Android Studio
File → Open → پوشه K1DataNFC را انتخاب کنید

# ۲. Sync کردن dependencies
Gradle Sync را اجرا کنید

# ۳. Build
Build → Generate Signed Bundle/APK
```

### Build مستقیم با Gradle
```bash
cd K1DataNFC
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 نحوه استفاده

1. **اسکن تگ**: گوشی را به تگ NFC نزدیک کنید
2. **تگ جدید**: فرم ویرایش باز می‌شود — نام و یادداشت وارد کنید
3. **افزودن تصویر**: دکمه "+ افزودن تصویر" را بزنید
4. **ذخیره**: دکمه "ذخیره" را بزنید
5. **اسکن مجدد**: اطلاعات ذخیره‌شده نمایش داده می‌شود
6. **پشتیبان**: منو → پشتیبان‌گیری

---

## 🔑 مجوزها (Permissions)

| مجوز | دلیل |
|------|------|
| `NFC` | خواندن تگ‌های NFC |
| `READ_EXTERNAL_STORAGE` | انتخاب تصویر (Android ≤ 12) |
| `READ_MEDIA_IMAGES` | انتخاب تصویر (Android 13+) |
| `WRITE_EXTERNAL_STORAGE` | ذخیره فایل بکاپ (Android ≤ 9) |
| `INTERNET` | (آماده برای Cloud در نسخ آینده) |

---

## ⚙️ وابستگی‌های اصلی

```gradle
implementation 'com.google.crypto.tink:tink-android:1.10.0'
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.recyclerview:recyclerview:1.3.2'
```

---

## 🚀 توسعه‌های آینده

- [ ] اتصال مستقیم Google Drive API
- [ ] اتصال مستقیم Dropbox SDK  
- [ ] رمز عبور ورود به برنامه (PIN/biometric)
- [ ] تم تاریک (Dark mode)
- [ ] صادرکردن به PDF

---

**K1 Data NFC** — داده‌هایتان امن، دسترسی سریع 🔐
