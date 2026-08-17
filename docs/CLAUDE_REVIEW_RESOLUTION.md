# Claude eski ZIP incelemesi — çözüm kaydı

Bu kayıt, commit `b36eb85` / sürüm `0.3.0` için hazırlanmış incelemenin
`0.4.1` kaynak koduna nasıl uygulandığını belgeler.

## K — kritik maddeler

| Madde | 0.4.1 kararı |
|---|---|
| K1 Android 10 sessiz silme | Düzeltildi. `minSdk 30`; eski doğrudan `resolver.delete` yolu ve `DeletePlan.Completed` kaldırıldı. Medya silme yalnız Android sistem onayıyla ilerler. |
| K2 tam ekran reklam çakışması | Düzeltildi. İzin, SAF, sistem silme, paylaşım ve haricî destek ekranlarından dönüş için bir sonraki App Open bastırılır. App Open sonrası 30 saniye içinde interstitial gösterilmez. |
| K3 yanlış tarama sıralaması | Düzeltildi. Her MediaStore koleksiyonunda 2.000 en eski ve 2.000 en büyük kayıt birleştirilir; büyük arşiv notu gösterilir. |
| K4 yanlış kopyayı koruma | Düzeltildi. Tek `DCIM/Camera` adayı öncelikle korunur. Kamera adayı belirsizse en eski/kısa yol korunur fakat diğer kopyalar otomatik seçilmez. Korunan ad UI'da gösterilir. |
| K5 Downloads/APK görünürlüğü | Ürün yüzeyi düzeltildi. Ana ekran yalnız bu uygulamanın cache'i ve MediaStore'un gerçekten gösterebildiği indirilen medyayı vaat eder. Geniş Downloads SAF erişimi gerçek cihaz doğrulamasından sonraki ayrı özelliktir. |

## H — yüksek öncelik

| Madde | 0.4.1 kararı |
|---|---|
| H1 1024/1000 farkı | Düzeltildi. Kullanıcıya gösterilen KB/MB/GB değerleri SI 1000 tabanlıdır. |
| H2 banner zıplaması | Düzeltildi. Banner alanı tarama ve UMP bekleme durumunda sabittir; navigasyondan 8dp ve ayırıcı çizgiyle ayrılır. |
| H3 RAM vaadi/sonuç kaybı | Düzeltildi. “RAM Boost/Hızlandır” kaldırıldı. İşlem yalnız uygulama belleğini ölçer ve tarama sonuçlarıyla seçimlerini korur. |
| H4 gereksiz reklam yeniden yükleme | Düzeltildi. Bellek bırakıldıktan sonra 60 saniyelik tam ekran reklam yükleme beklemesi vardır. |
| H5 ölü ayar yönlendirmeleri | Düzeltildi. `resolveActivity` ön kontrolü kaldırıldı; sıralı güvenli başlatma ve başarısızlık snackbar'ı eklendi. Kullanılmayan sistem ayarı callback'leri kaldırıldı. |

## M — orta öncelik

| Madde | 0.4.1 kararı |
|---|---|
| M1 WhatsApp cihaz bağımlılığı | Uygulama WhatsApp ve WhatsApp Business Media ağacını kabul eder; Android 11/13/14/15 gerçek cihaz matrisi zorunlu QA maddesi olarak kalır. |
| M2 hash maliyeti | Düzeltildi. 64 KB baş + 64 KB son örneği ve boyut ön hash'i; yalnız eşleşenlerde tam SHA-256. |
| M3 imzasız AAB | Düzeltildi. Release task upload keystore ortam değişkenleri yoksa durur. |
| M4 ölü MREC | Düzeltildi. Kaynak, BuildConfig, workflow, secret doğrulama ve dokümantasyondan kaldırıldı. |
| M5 gizlilik erişimi | `0.4.0` içinde zaten çözülmüştü: Gizlilik, Hizmet Şartları ve Hakkında uygulama içindedir. Play kaydı için ayrıca herkese açık HTTPS URL gerekir. |
| M6 README/ölü kod | Düzeltildi. Gerçekte olmayan ayar yönlendirmeleri README'den ve çağrı yüzeyinden kaldırıldı. |
| M7 büyük UI dosyası | Eski ve erişilemeyen cache-manager composable'ları kaldırıldı. Ekranları daha küçük dosyalara ayırma, davranışsal hotfix'ten ayrı refactor olarak tutuldu. |
| M8 sık cache sorgusu | Düzeltildi. Başarılı `StorageStatsManager` ölçümleri arasında en az 60 saniye vardır. |
| M9 isim/ASO | Kod hatası değildir. Mağaza adı, isim araştırması tamamlanmadan kesinleştirilmeyecek. |
| M10 ek diller | İngilizce küresel varsayılandır, Türkçe tam yerelleştirilmiştir. Ek diller insan çeviri/RTL QA süreci olmadan otomatik eklenmeyecek. |

## Zorunlu gerçek cihaz kontrolleri

- Android 11, 13, 14, 15 ve 16 üzerinde sistem silme onayı.
- WhatsApp ve WhatsApp Business SAF ağacı, önizleme ve gerçek silme.
- 5.000+ medya dosyasında eski/büyük iki geçişli tarama.
- `DCIM/Camera` + `Download` birebir kopya koruma testi.
- İzin, SAF ve silme ekranından dönüşte tek tam ekran reklam ilkesi.
- Android Ayarlar ile depolama karşılaştırması; hedef fark en fazla ±%2.
