# ScrollGuard — İlerleme Takibi

Son güncelleme: 2026-03-19 | Mevcut sürüm: **v6**

---

## ✅ Tamamlananlar

### Temel Altyapı
- [x] Android projesinin temel yapısı oluşturuldu (Gradle, Manifest)
- [x] Dark theme UI tasarlandı (toggle, durum metni, engellenen uygulamalar listesi)
- [x] Adaptive launcher icon (XML vector, mipmap-anydpi-v26)
- [x] Foreground Service (`ScrollBlockerService`) — arka plan + bildirim çubuğu
- [x] Accessibility Service (`ScrollAccessibilityService`) — engelleme motoru
- [x] `isAccessibilityEnabled()` → `AccessibilityManager` API kullanılıyor (güvenilir)

### Engelleme Mantığı (v5)
- [x] TikTok / Snapchat → uygulama açılışında 3sn sonra HOME
- [x] YouTube Shorts (nav-tab seçili) → HOME
- [x] YouTube ana sayfa Shorts → içerik alanı bounds tespiti → HOME
- [x] Instagram Reels (nav-tab seçili) → HOME (hikaye izleme serbest)
- [x] Tarayıcıda Instagram Reels → URL bar tespiti → HOME
- [x] PiP modu → engelleme bypass (küçük pencere video izleme serbest)
- [x] Cooldown düzeltmesi (1500ms → 300ms spam önleme)
- [x] `isEnabled` yeniden senkronizasyon (servis restart sonrası)
- [x] Engelleme gecikmesi: 3000ms (TÜM yollar, sadece TikTok değil)
- [x] `detectReelsScreen()` — ortak fonksiyon, node-size filtresi (>48dp)
- [x] YouTube scroll tespiti (Shorts player'da swipe yeniden aktif)
- [x] Instagram Reels player bounds-tespiti
- [x] Instagram hikaye koruması (scroll tespiti yok)
- [x] **Geri Sayım Sayacı (v9):** Gün Seçici + Katı Kilitleme + Onay Penceresi + PiP Block

---

## 🚧 Devam Eden / Test Bekleniyor

- [ ] YouTube ana sayfa Shorts tespiti → node boyut filtresi eklendi ama geniş test gerekiyor
- [ ] Firefox / Edge URL bar ID → sadece Chrome kesin test edildi
- [ ] Instagram in-feed Reels (scroll ederken karşılaşılan, player açılmadan izlenen) → çok karmaşık, ileride
- [ ] Engelleme istatistiği (`blockedCount`) UI'da görünmüyor

---

## 📋 Yapılabilecek Sonraki Özellikler

### Yüksek Öncelik
- [ ] **Engelleme sayacı UI** — "Bugün X içerik engellendi" widget'ı MainActivty'e ekle
- [ ] **Şifre kilidi** — Toggle'ı kapatmak PIN gerektirsin (`AlertDialog` + `SharedPreferences`)
- [ ] **Instagram in-feed Reels** — Ana sayfadaki Reels içeriklerini de yakala

### Orta Öncelik
- [ ] **Zamanlayıcı** — Belirli saat aralıklarında otomatik aktif/pasif
- [ ] **Beyaz liste** — Hangi uygulamalar engellensin kullanıcı seçebilsin
- [ ] **Önyükleme alıcısı** — Telefon yeniden başladığında servis otomatik başlasın (`BOOT_COMPLETED`)

### Düşük Öncelik
- [ ] **Haftalık istatistik** — Kaç saat kısa videodan korunuldu
- [ ] **Widget** — Ana ekranda hızlı toggle
- [ ] **Firebase** — İstatistik senkronizasyonu

---

## Bilinen Kısıtlamalar

| Kısıtlama | Açıklama |
|---|---|
| Google Play | Accessibility Service kullanan uygulamalar politika ihlali sayılabilir. APK olarak dağıt. |
| Instagram in-feed Reels | Ana sayfada scroll edilirken rastlanan Reels içeriği engellemiyor |
| Tarayıcı URL | Her tarayıcının farklı URL bar view ID'si var, kapsamlı test gerekiyor |
| Android 14+ | `foregroundServiceType` için daha kısıtlı |

---

## Versiyon Geçmişi

| Versiyon | Değişiklik |
|---|---|
| v1 | `performAction(SCROLL_BACKWARD)` — çalışmadı |
| v2 | `dispatchGesture()` counter-swipe — çalışmadı |
| v3 | HOME redirect + metin tespiti (tüm ağaç) — YouTube tamamen kapandı |
| v4 | `isSelected` kontrolü + PiP bypass + tarayıcı URL — YouTube sorun düzeltildi |
| v5 | Cooldown düzeltmesi + ana sayfa Shorts tespiti (bounds) + Instagram hikaye koruması |
| v6 | TÜM engelleme yollarına 3000ms delay + `detectReelsScreen()` ortak fonksiyon + YouTube scroll yeniden aktif |
| v7 | Geri Sayım Sayacı özelliği eklendi (Chips + Custom Input + Service Timer) |
| v8 | Sayaç arayüzü iyileştirildi: Dropdown (ComboBox) + NumberPicker (Alarm tipi) |
| v9 | **Garantili Koruma:** Gün Seçici + Katı Kilitleme + Onay Mesajı + PiP Engelleme |
