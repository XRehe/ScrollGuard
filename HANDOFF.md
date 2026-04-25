# ScrollGuard — AI Handoff Raporu (v9)

## Proje Nedir?
Android uygulaması. Instagram Reels, YouTube Shorts, TikTok gibi kısa video içeriklerini **sistem seviyesinde** engelliyor.  
Arayüz: sadece bir toggle butonu. Arka planda çalışır, pil tüketimi minimum.

**Proje yolu:** `C:\Users\LENOVA\Desktop\ScrollGuard`  
**Package:** `com.scrollguard` | **Min SDK:** 26 | **Target SDK:** 34 | **Dil:** Kotlin

---

## Dosya Yapısı

```
ScrollGuard/
├── build.gradle / settings.gradle / gradle.properties / gradlew.bat
├── gradle/wrapper/gradle-wrapper.properties   (Gradle 8.6)
├── HANDOFF.md / agent.md / progress.md
└── app/
    ├── build.gradle   (AGP 8.2.2, Kotlin 1.9.22, compileSdk 34)
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/scrollguard/
        │   ├── MainActivity.kt                 ← Toggle UI
        │   ├── ScrollBlockerService.kt         ← Foreground service
        │   └── ScrollAccessibilityService.kt   ← ENGELLEMENİN YAPILDIĞI YER
        └── res/
            ├── layout/activity_main.xml        ← Dark theme UI
            ├── xml/accessibility_config.xml
            ├── values/{strings,colors,themes}.xml
            ├── color/toggle_track_selector.xml
            └── drawable + mipmap-anydpi-v26/   ← Uygulama ikonu (adaptive icon)
```

---

## Bileşenler

### MainActivity.kt
- Toggle ON → `ScrollBlockerService` başlatır
- Toggle OFF → servisi durdurur
- **Süre Ayarı (v9):** Gün (0-30), Saat ve Dakika seçicileri. Bildirimde saniyesine kadar canlı takip.
- **Katı Mod (Strict Mode):** Süre başlatılınca "Koru" butonu kilitlenir (devre dışı kalır). Manuel kapatmak imkansızdır.
- **PiP Engelleme:** TikTok, Instagram ve Snapchat küçük pencerede (PiP) oynatılmaya çalışılırsa anında engellenir.
- **Onay Uyarı Penceresi:** Süreli koruma başlatılmadan önce kullanıcıdan "Kapatılamaz, emin misin?" onayı alır.
- İzin yoksa → dialog + `Settings.ACTION_ACCESSIBILITY_SETTINGS`'e yönlendir

### ScrollBlockerService.kt
- `Foreground Service` → bildirim çubuğunda kalıcı ikon
- `START_STICKY` → sistem öldürse bile yeniden başlar
- `onCreate()` → `ScrollAccessibilityService.setEnabled(true)`
- `onDestroy()` → `ScrollAccessibilityService.setEnabled(false)`

### ScrollAccessibilityService.kt — v6 (kritik)

**8 Katmanlı Engelleme:**

| Katman | Hedef | Eylem |
|---|---|---|
| 1 | TikTok, Snapchat | 3sn sonra `GLOBAL_ACTION_HOME` |
| 2 | YouTube Shorts (nav-tab) | `isSelected==true` → 3sn sonra HOME |
| 3 | YouTube Shorts (ana sayfa player) | İçerik alanında "Shorts" (>48dp) → 3sn sonra HOME |
| 4 | YouTube scroll | Büyük delta (>%60 ekran) → 3sn sonra HOME |
| 5 | Instagram Reels (nav-tab) | `isSelected==true` → 3sn sonra HOME |
| 6 | Instagram Reels (player açık) | İçerik alanında "Reels" (>48dp) → 3sn sonra HOME |
| 7 | Chrome/Firefox/Edge | URL bar `instagram.com/reel` → 3sn sonra HOME |
| 8 | TikTok/Snapchat scroll yedek | Büyük delta → 3sn sonra HOME |

**Önemli Detaylar:**
- `SPAM_COOLDOWN_MS = 300ms`
- `BLOCK_DELAY_MS = 3000ms`
- **Süre Yönetimi:** `ScrollBlockerService` içinde `CountDownTimer` çalışır, bildirimde kalan süreyi gösterir ve bitince `MainActivity`'ye broadcast gönderir.
- `isInPiPMode()` → PiP modunda hiçbir engelleme yok.

---

## Denendi Ama Çalışmayan Yaklaşımlar

1. **`ACTION_SCROLL_BACKWARD`** → ViewPager2'yi etkilemedi, sayfa zaten geçmişti
2. **`dispatchGesture()` counter-swipe** → Kullanıcının parmağıyla güreşti, kullanıcı kazandı
3. **`findAccessibilityNodeInfosByText()` seçimsiz** → Nav-bar ikonu da tutuldu, YouTube tamamen kapandı
4. **`BLOCK_COOLDOWN_MS = 1500ms`** → Cooldown penceresinde yeniden giriş bloklanmıyordu
5. **`blockWithHome(delayMs=0)` YouTube/Instagram'da** → Anında kapanıyordu, `BLOCK_DELAY_MS` yanlışlıkla sadece TikTok'ta uygulandı

---

## Dağıtım ve Android 13+ Kurulumu

APK olarak paylaşıldığında (sideloading) Android'in güvenlik mekanizmaları (Play Protect ve Kısıtlanmış Ayarlar) engeller çıkarır. Bunları aşma yöntemleri:

### 1. Google Play Protect
APK kurulurken "Zararlı uygulama engellendi" veya "Play Protect bu uygulamayı tanımıyor" diyebilir.
- **Çözüm:** "Yine de yükle" (Install anyway) seçeneğine tıkla. Eğer Play Protect çok katıysa Play Store ayarlarından geçici olarak Play Protect'i kapatabilirsin.

### 2. Kısıtlanmış Ayarlar (Restricted Settings - Android 13+)
Erişilebilirlik ayarlarında ScrollGuard'ı açmaya çalıştığında "Kısıtlanmış Ayar" (Restricted Setting) uyarısı alırsın ve açmana izin vermez.
- **Çözüm:**
    1. Telefonun **Ayarlar** -> **Uygulamalar** -> **ScrollGuard** kısmına git.
    2. Sağ üst köşedeki **3 noktaya** (:gear: veya :vertical_ellipsis:) tıkla.
    3. **"Kısıtlanmış ayarlara izin ver"** (Allow restricted settings) seçeneğini seç.
    4. Şimdi tekrar Erişilebilirlik ayarlarına dön, artık ScrollGuard'ı aktif edebilirsin.

### 3. Pil Optimizasyonu
Uygulama arka planda kapanmasın diye uygulama bilgilerinden pil tasarrufunu "Kısıtlama yok" (No restrictions) yapman önerilir.
