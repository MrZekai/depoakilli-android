# Özellik ve Android Gerçeklik Matrisi

| Özellik | İlk sürüm | Uygulama biçimi |
|---|---:|---|
| Depolama göstergesi | Evet | `StatFs` ile gerçek toplam/boş alan |
| RAM göstergesi | Evet | `ActivityManager.MemoryInfo` |
| Uygulama RAM optimizasyonu | Evet | Tarama sonuçları ve yüklü tam ekran reklam referansları bırakılır; `Debug.MemoryInfo.totalPss` ile önce/sonra ölçülür |
| Uygulama cache temizliği | Evet | Uygulamanın kendi cache dizinleri |
| Uygulama depolama detayları | Evet | Android'in bu uygulamaya ait resmî ayar ekranı |
| Sistem ve uygulama depolama yönetimi | Evet | Android'in depolama/uygulamalar ayar ekranları |
| Başka uygulamanın özel cache'ini sessiz silme | Hayır | Android tarafından engellenir |
| APK paket önerisi | Kısmi | MediaStore'un uygulamaya gösterebildiği indirilenler |
| Ekran görüntüsü temizliği | Evet | MediaStore + yaş/kaynak sinyalleri |
| Büyük video temizliği | Evet | MediaStore + boyut/yaş sinyalleri |
| Tam yinelenen dosya | Evet | Boyut ön elemesi + tam SHA-256 |
| Benzer fakat aynı olmayan fotoğraf | Sıradaki faz | Algısal hash ve kalite puanı |
| Toplu silme | Evet | Android 11+ `MediaStore.createDeleteRequest` |
| Fotoğraf/video sıkıştırma | Sıradaki faz | Media3/codec tabanlı, önizlemeli |
| Başka uygulamaları topluca kapatma | Hayır | Android 14+ `killBackgroundProcesses` üçüncü taraf uygulamalarda yalnız çağıranın kendi sürecini sonlandırabilir |
| Geniş dosya erişimi | Değerlendirme sonrası | Play deklarasyonu gerektiren ayrı karar |
| İngilizce/Türkçe arayüz | Evet | İngilizce varsayılan, `values-tr` Türkçe |
| App Open reklamı | Evet | Üçüncü ön plan gelişinden sonra, iki saat sıklık sınırı |

## AI motoru

İlk sürüm, dosyaları bir sunucuya yüklemeyen açıklanabilir bir puanlama motoru kullanır:

- dosya yaşı;
- dosya boyutu;
- MIME türü;
- kaynak klasör;
- ekran görüntüsü ve APK sinyalleri;
- tam içerik parmak izi;
- yakın zamanda oluşturulan dosyalara koruma cezası.

Her öneri kullanıcıya neden gösterir ve güven puanı verir. “AI” hiçbir zaman otomatik/sessiz silme anlamına gelmez.
