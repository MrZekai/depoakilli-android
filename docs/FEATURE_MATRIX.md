# Özellik ve Android Gerçeklik Matrisi

| Özellik | İlk sürüm | Uygulama biçimi |
|---|---:|---|
| Depolama göstergesi | Evet | `StatFs` ile gerçek toplam/boş alan |
| RAM göstergesi | Evet | `ActivityManager.MemoryInfo` |
| Uygulama belleği temizliği | Evet | Tarama seçimleri korunur, geçici tam ekran reklam referansları bırakılır; `Debug.MemoryInfo.totalPss` ile önce/sonra ölçülür |
| Bu uygulamanın cache temizliği | Evet | Uygulamanın kendi cache dizinleri doğrudan silinir |
| Diğer uygulamaların cache ölçümü | Evet | Kullanıcı Kullanım Erişimi verdikten sonra `StorageStatsManager.cacheBytes`; ana ekran uygulamalarıyla sınırlı görünürlük |
| Diğer uygulamanın cache yönetimi | Bilgi amaçlı | Önbellek toplamı ayrı gösterilir; temizlenebilir toplama katılmaz ve silinmiş gibi gösterilmez |
| Başka uygulamanın özel cache'ini sessiz silme | Hayır | Android tarafından engellenir |
| APK paket önerisi | Kısmi | MediaStore'un uygulamaya gösterebildiği indirilenler |
| Ekran görüntüsü temizliği | Evet | MediaStore + yaş/kaynak sinyalleri |
| Büyük video temizliği | Evet | MediaStore + boyut/yaş sinyalleri |
| Tam yinelenen dosya | Evet | Boyut + 64 KB baş/son örneği; yalnız eşleşen adaylarda tam SHA-256; DCIM/Camera orijinali korunur |
| WhatsApp medya temizliği | Evet | Kullanıcının bağladığı Media ağacı Görsel, Video, Belge, Ses/Müzik, Sesli Mesaj, Çıkartma/GIF ve Diğer olarak sınıflandırılır; önizleme, kategori/öğe seçimi, uygulama içi onay ve gerçek belge silme |
| Cihaz Merkezi | Evet | Depolama, RAM/PSS, pil, Android/API, CPU/çekirdek, ekran ve uygulama sürümü gerçek Android verilerinden okunur |
| Ayarlar ve destek | Evet | Değerlendirme, geri bildirim, paylaşma, uygulama içi Gizlilik Politikası, Hizmet Şartları, Hakkında ve UMP gizlilik tercihleri |
| Benzer fakat aynı olmayan fotoğraf | Sıradaki faz | Algısal hash ve kalite puanı |
| Toplu silme | Evet | Android 11+ `MediaStore.createDeleteRequest` |
| Fotoğraf/video sıkıştırma | Sıradaki faz | Media3/codec tabanlı, önizlemeli |
| Başka uygulamaları topluca kapatma | Hayır | Android 14+ `killBackgroundProcesses` üçüncü taraf uygulamalarda yalnız çağıranın kendi sürecini sonlandırabilir |
| Geniş dosya erişimi | Değerlendirme sonrası | Play deklarasyonu gerektiren ayrı karar |
| İngilizce/Türkçe arayüz | Evet | İngilizce varsayılan, `values-tr` Türkçe; ek diller insan kalite kontrolü sonrası eklenecek |
| App Open reklamı | Evet | Üçüncü ön plan gelişinden sonra, iki saat sıklık sınırı |

## Akıllı öneri motoru

İlk sürüm, dosyaları bir sunucuya yüklemeyen açıklanabilir bir puanlama motoru kullanır:

- dosya yaşı;
- dosya boyutu;
- MIME türü;
- kaynak klasör;
- ekran görüntüsü ve APK sinyalleri;
- tam içerik parmak izi;
- yakın zamanda oluşturulan dosyalara koruma cezası.

Her öneri kullanıcıya neden gösterir ve güven puanı verir. “Akıllı” ifadesi hiçbir zaman otomatik veya sessiz silme anlamına gelmez. Akıllı Tarama sonuç başlığındaki alan yalnız seçilip gerçekten silinebilen öğelerin toplamıdır; Android'in koruduğu diğer uygulama önbelleği ayrı ve bilgi amaçlı gösterilir.
