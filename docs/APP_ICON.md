# Akıllı Temizleyici uygulama ikonu

Kullanıcı tarafından seçilen kalıcı ikon; lacivert zemin üzerinde elektrik
mavisi kalkan, beyaz temizlik fırçası, turkuaz yörünge ve iki beyaz parıltıdan
oluşur. İkon içinde yazı veya `QA` ibaresi bulunmaz.

## Android kaynakları

- `drawable-xxxhdpi/ic_launcher_foreground_art.png`: Android 8+ adaptive icon
  için şeffaf ve güvenli alanlı ön katman.
- `drawable-xxxhdpi/ic_launcher_legacy_art.png`: Android 6–7 ve üreticiye özel
  eski başlatıcılar için tam renkli kare kaynak.
- `drawable-xxxhdpi/ic_launcher_monochrome_art.png`: Android 13+ temalı ikon
  için tek renkli alfa maskesi.
- `drawable/ic_launcher_background.xml`: adaptive icon lacivert arka planı.
- `store-assets/icon-512.png`: Google Play Console'a yüklenecek 512×512 PNG.
- `store-assets/icon-master-1536.png`: seçilen görselin arşivlenen ana kaynağı.

Manifest hem normal hem yuvarlak başlatıcı ikonunu `@mipmap` üzerinden kullanır.
Adaptive icon, başlatıcının daire, yuvarlak kare, squircle veya gözyaşı maskesine
göre güvenle kırpılır; merkezdeki kalkan ve fırça güvenli bölgede kalır.
