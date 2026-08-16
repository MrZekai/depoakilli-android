# Smart Cleaner / Akıllı Temizleyici (çalışma adı)

`com.mrzekai.depoakilli` paket kimlikli, Kotlin ve Jetpack Compose ile geliştirilen Android depolama düzenleyicisi. Paket kimliği kalıcıdır; görünen global marka adı ASO ve marka araştırmasından sonra kesinleşecektir.

Uygulama; sahte RAM hızlandırma veya CPU soğutma iddiaları yerine Android'in izin verdiği gerçek depolama işlemlerini sunar. Fotoğraf, video, ekran görüntüsü, indirilen dosya, APK paketi ve uygulama önbelleklerini cihaz üzerinde analiz eder. Silme işlemleri kullanıcı seçimi ve Android sistem onayı olmadan başlamaz.

## Çalışan ilk sürüm

- Android 16 / API 36 hedefi
- İngilizce varsayılan arayüz ve tam Türkçe yerelleştirme
- Android 13+ uygulama dili ayarı
- Modern açık mavi/turkuaz Compose arayüzü ve sadeleştirilmiş ana ekran
- Depolama ve gerçek RAM durumu
- Gerçek PSS ölçümlü uygulama RAM optimizasyonu; geçici tarama ve tam ekran reklam belleğini serbest bırakma
- Cihaz-içi, açıklanabilir AI temizlik puanı
- Eski ekran görüntüsü önerileri
- 150 MB üzerindeki eski video önerileri
- Eski indirilen dosya ve erişilebilen APK paketi taraması
- Aynı boyuttaki adaylarda tam SHA-256 içerik doğrulamalı yinelenen dosya tespiti
- Android 11+ toplu silme onay ekranı
- Uygulamanın kendi önbelleğini doğrudan ve güvenli temizleme
- Kullanıcı Kullanım Erişimi verdikten sonra Android `StorageStatsManager` ile cihaz geneli gerçek cache toplamı
- Ana ekranda başlatılabilen uygulamaları cache boyutuna göre sıralayan ve doğru Android uygulama ekranını açan önbellek yöneticisi
- Uygulama depolama, cihaz depolama, bellek/uygulama ve dil ayarlarına çalışan yönlendirmeler
- Google Mobile Ads 25.4.0 test entegrasyonu
- UMP 4.0.0 reklam gizlilik/onay akışı
- Ana Sayfa dâhil üç sekmede navigasyon üstünde görünür sabit banner
- Temizlik sonrasında aralıklı geçiş reklamı
- İlk iki kullanımı bölmeyen, iki saat sıklık sınırlı uygulama açılış reklamı
- Test AdMob kimlikleriyle release üretimini durduran koruma
- GitHub Actions debug APK ve manuel imzalı AAB iş akışları
- Aynı sertifikayla güncellenebilen, production'dan ayrı `com.mrzekai.depoakilli.qa` test APK'sı

## Teknik sınırlar

Android, normal bir Play uygulamasının başka uygulamaların özel dizinlerini ve önbelleğini sessizce silmesini engeller. Bu nedenle uygulama:

- başka uygulamaların gerçek cache toplamını ölçer ancak özel cache dizinlerini doğrudan silmez; ilgili Android ekranını açar;
- RAM'i zorla boşaltmış gibi davranmaz;
- kullanıcı görmeden dosya silmez;
- `MANAGE_EXTERNAL_STORAGE` iznini ilk Play sürümünde istemez;
- erişilemeyen belgeler için temizlenmiş alan uydurmaz.

Geniş dosya erişimi isteyen sonraki sürüm ancak Play'in izin deklarasyonu, inceleme videosu ve çekirdek dosya yönetimi gerekçesi hazırlandıktan sonra ayrı bir kararla eklenmelidir.

## Derleme

Gereksinimler:

- JDK 17
- Android SDK 36

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions test APK'sı kalıcı QA sertifikasıyla imzalanır ve görünür adı
`Akıllı Temizleyici QA` olur. Eski `.debug` test uygulamasının yanına kurulabilir;
sonraki `.qa` APK'lar veri silmeden güncelleme olur. QA ve Play release imzalarının
ayrımı [`docs/QA_SIGNING.md`](docs/QA_SIGNING.md) içinde açıklanmıştır.

## AdMob

Debug derlemeler Google'ın resmî test kimliklerini kullanır. Canlı kimlikler kaynak koda yazılmaz.

Release için aşağıdaki Gradle özellikleri gerekir:

```text
ADMOB_APP_ID
ADMOB_BANNER_ID
ADMOB_INTERSTITIAL_ID
ADMOB_MEDIUM_RECTANGLE_ID
ADMOB_APP_OPEN_ID
```

GitHub Actions tarafında aynı adlarla repository secret oluşturulur. Canlı kimlikler eksikse `bundleRelease` durur.

Banner ve geçiş reklamının gösterildiği/gösterilmediği noktalar
[`docs/AD_PLACEMENTS.md`](docs/AD_PLACEMENTS.md) dosyasında sabitlenmiştir.

## İmzalı AAB

`.github/workflows/release-aab.yml` aşağıdaki secrets değerlerini bekler:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
ADMOB_APP_ID
ADMOB_BANNER_ID
ADMOB_INTERSTITIAL_ID
ADMOB_MEDIUM_RECTANGLE_ID
ADMOB_APP_OPEN_ID
```

Upload key hiçbir zaman repoya eklenmemelidir. Yeni uygulamanın ilk AAB'sinde kullanılan upload key güvenli biçimde yedeklenmeli ve sonraki tüm güncellemelerde aynı anahtar kullanılmalıdır.

## Yol haritası

1. Gerçek cihaz QA ve izin akışları
2. Yakın benzer fotoğraf gruplama ve “en iyisini koru” kalite puanı
3. Fotoğraf/video sıkıştırma
4. Kullanılmayan uygulama raporu ve güvenli kaldırma yönlendirmesi
5. Play izin değerlendirmesinden sonra gelişmiş dosya/paket tarama
6. Türkçe Play Store görselleri ve kapalı test

Detaylar için [`docs/FEATURE_MATRIX.md`](docs/FEATURE_MATRIX.md), [`docs/DEVICE_QA_CHECKLIST.md`](docs/DEVICE_QA_CHECKLIST.md) ve [`docs/PLAY_RELEASE_CHECKLIST.md`](docs/PLAY_RELEASE_CHECKLIST.md) dosyalarına bakın.
