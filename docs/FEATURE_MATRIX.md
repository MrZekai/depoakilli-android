# Özellik ve Android Gerçeklik Matrisi

| Özellik | İlk sürüm | Uygulama biçimi |
|---|---:|---|
| Depolama göstergesi | Evet | `StatFs` ile gerçek toplam/boş alan |
| RAM göstergesi | Evet | `ActivityManager.MemoryInfo` |
| DepoAkıllı cache temizliği | Evet | Uygulamanın kendi cache dizinleri |
| Sistem cache yönetimi | Evet | Android'in kullanıcı onaylı sistem ekranı |
| Başka uygulamanın özel cache'ini sessiz silme | Hayır | Android tarafından engellenir |
| APK paket önerisi | Kısmi | MediaStore'un uygulamaya gösterebildiği indirilenler |
| Ekran görüntüsü temizliği | Evet | MediaStore + yaş/kaynak sinyalleri |
| Büyük video temizliği | Evet | MediaStore + boyut/yaş sinyalleri |
| Tam yinelenen dosya | Evet | Boyut ön elemesi + tam SHA-256 |
| Benzer fakat aynı olmayan fotoğraf | Sıradaki faz | Algısal hash ve kalite puanı |
| Toplu silme | Evet | Android 11+ `MediaStore.createDeleteRequest` |
| Fotoğraf/video sıkıştırma | Sıradaki faz | Media3/codec tabanlı, önizlemeli |
| RAM boost / uygulamaları zorla kapatma | Hayır | Yanıltıcı ve Android çalışma modeline aykırı |
| Geniş dosya erişimi | Değerlendirme sonrası | Play deklarasyonu gerektiren ayrı karar |

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
