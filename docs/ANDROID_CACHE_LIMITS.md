# Android uygulama önbelleği sınırı

## Ürün kararı

Akıllı Temizleyici, başka uygulamaların özel önbelleğini kendi arayüzünden
sessizce silmiş gibi davranmaz. Akıllı Tarama'da görülen büyük uygulama
önbelleği değeri yalnız ölçümdür; temizlenebilir toplam ve Temizle düğmesi
yalnız uygulamanın gerçekten silebildiği seçili dosyaları içerir.

## Teknik neden

- Android `StorageStatsManager`, başka paketlerin depolama ve cache
  istatistiklerini yalnız kullanıcının Ayarlar'dan verdiği Kullanım Erişimiyle
  sorgulamaya izin verir. Bu API ölçüm sunar; silme API'si değildir.
- Başka uygulamanın özel cache dizini o uygulamanın sandbox'ındadır. Normal bir
  Play uygulamasına cihaz genelinde `CLEAR_APP_CACHE` ayrıcalığı verilmez.
- Accessibility ile Ayarlar ekranlarına otomatik tıklama veya kullanıcıdan
  habersiz toplu silme uygulanmaz. Bu yaklaşım kırılgan, yanıltıcı ve Play
  politikası açısından yüksek risklidir.

Resmî Android kaynakları:

- https://developer.android.com/reference/android/app/usage/StorageStatsManager
- https://developer.android.com/training/data-storage/shared/documents-files

## Gerçek temizlik kapsamı

Uygulama aşağıdaki alanları gerçek olarak temizler:

- kendi iç ve dış cache dizinleri;
- kullanıcının MediaStore üzerinden seçip Android silme onayını verdiği medya;
- kullanıcı tarafından bağlanan WhatsApp/WhatsApp Business Media ağacındaki,
  uygulama içinde seçilip kalıcı silme onayı verilen belgeler;
- MediaStore'un gerçekten gösterebildiği eski indirilen medya ve tam içerik
  eşleşmeli kopyalar. Android'in göstermediği belge/APK dosyaları bulunmuş gibi
  raporlanmaz.
