# QA APK imza düzeni

GitHub Actions tarafından üretilen test APK'sı ile Google Play'e gönderilecek
release AAB aynı uygulama değildir ve aynı anahtarı kullanmaz.

## QA APK

- Paket kimliği: `com.mrzekai.depoakilli.qa`
- Görünen ad: `Smart Cleaner QA` / `Akıllı Temizleyici QA`
- Anahtar: `keystore/depoakilli-ci-qa.jks`
- Sertifika SHA-256:
  `50:8E:01:21:97:DA:76:D5:16:BA:D2:48:80:B6:7C:6F:06:7F:CD:64:6C:51:A0:43:A1:1C:0C:34:5E:6E:AD:54`

QA anahtarı yalnız `.qa` paketini imzalar. Bu anahtar bilerek repoda tutulur;
production veya Google Play anahtarı değildir. Böylece her GitHub Actions
çalıştırması aynı sertifikayı kullanır ve yeni QA APK, önceki QA APK'nın üzerine
veri silmeden güncelleme olarak kurulabilir.

Eski CI APK'sı `com.mrzekai.depoakilli.debug` paketini kullanıyorsa yeni QA
APK onun yanına kurulabilir; eski uygulamayı kaldırmak zorunlu değildir.

## Google Play release AAB

- Paket kimliği: `com.mrzekai.depoakilli`
- QA suffix'i yoktur.
- Yalnız GitHub Secrets içindeki gerçek upload keystore ile imzalanır.
- `release` yapılandırması QA anahtarına düşmez.
- İlk Play AAB gönderilmeden önce upload keystore oluşturulmalı, güvenli biçimde
  yedeklenmeli ve sonraki bütün sürümlerde aynı anahtar kullanılmalıdır.

CI, üretilen QA APK'nın sertifika özetini `apksigner` ile doğrular. Beklenmeyen
bir anahtar kullanılırsa iş akışı başarısız olur.
