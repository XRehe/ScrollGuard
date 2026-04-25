# ScrollGuard — Agent Talimatları

## Proje Hakkında
Reels/Shorts engelleyici Android uygulaması. Kotlin + Accessibility Service tabanlı.  
Her şey `ScrollAccessibilityService.kt` dosyasında. Toggle `MainActivity.kt` + `ScrollBlockerService.kt` üzerinden yönetilir.

---

## Kodu Değiştirirken Kritik Kurallar

### ❌ YAPMA
- `dispatchGesture()` ile counter-swipe ekleme — çalışmıyor, kullanıcı kazanıyor
- `performAction(ACTION_SCROLL_BACKWARD)` — ViewPager2'yi etkilemiyor
- `SPAM_COOLDOWN_MS`'i 1000ms'nin üstüne çıkarma — yeniden girişi engeller
- `findAccessibilityNodeInfosByText()` sonucunu seçim kontrolü olmadan kullanma — nav-bar ikonu yanlış pozitif verir
- Instagram'da scroll delta tespiti — hikayeler zarar görür
- `blockWithHome(0)` veya `blockWithHome()` delays olmadan çağırma — her şey default 3000ms olmalı
- Timer mantığını bozma — `ScrollBlockerService` içindeki `CountDownTimer` UI ile `BroadcastReceiver` üzerinden haberleşir.

### ✅ YAP
- `isEnabled` değerini her `onServiceConnected()` çağrısında `ScrollBlockerService.isRunning`'den yenile
- Node araması sonrası her zaman `.recycle()` çağır (bellek sızıntısı önleme)
- "Shorts/Reels" node'larında `isSelected || isChecked` kontrol et (nav-tab vs içerik ayrımı için)
- PiP modu kontrolü: `windows?.any { it.isInPictureInPictureMode }`
- Bounds kontrolü (nav-bar vs içerik): `rect.centerY() < screenHeight * 0.85f`

---

## Yeni Uygulama Eklemek İstersen

`ScrollAccessibilityService.kt` içindeki companion object'te:

```kotlin
// Tamamen engelle (tüm içerik kısa video)
val fullBlockApps = setOf(
    "com.zhiliaoapp.musically",
    ...
    "com.yeni.uygulama"   // ← buraya ekle
)

// Sadece belirli ekran (Shorts/Reels sekmesi)
val partialBlockApps = setOf(
    "com.google.android.youtube",
    ...
    "com.yeni.uygulama"   // ← ya da buraya
)
```

Partial block için `when` bloğuna da handler ekle:
```kotlin
"com.yeni.uygulama" -> handler.post { checkGenericReelsScreen("com.yeni.uygulama", listOf("KeyWord")) }
```

---

## Hata Ayıklama

| Belirti | Olası Neden | Kontrol Et |
|---|---|---|
| Toggle ON ama engelleme yok | `isEnabled = false` | `onServiceConnected()` çağrıldı mı? `ScrollBlockerService.isRunning` true mu? |
| 3 denemeden sonra durdu | Cooldown çok yüksek | `SPAM_COOLDOWN_MS` değerini kontrol et (300ms olmalı) |
| YouTube tamamen kapanıyor | Nav-bar seçim kontrolü eksik | `checkYouTubeScreen()` içinde `isSelected` koşulunu kontrol et |
| PiP'te kapanıyor | `isInPiPMode()` çalışmıyor | `windows` null dönüyor olabilir, try-catch var mı? |
| Hikayeler de kapanıyor | Instagram'da scroll delta var | Instagram `TYPE_VIEW_SCROLLED` handler'ı kaldır |

---

## Package & Sınıf İsimleri
```
com.scrollguard.MainActivity
com.scrollguard.ScrollBlockerService
com.scrollguard.ScrollAccessibilityService
```
