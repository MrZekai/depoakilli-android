# Akıllı Temizleyici Gizlilik Politikası Taslağı

Son güncelleme: 17 Ağustos 2026

Akıllı Temizleyici, kullanıcının cihazındaki depolama alanını analiz ederek temizleme önerileri sunar. Fotoğraf, video ve dosya içeriği analiz amacıyla geliştiricinin sunucularına yüklenmez. Analiz cihaz üzerinde gerçekleştirilir.

## Erişilen veriler

Uygulama, kullanıcının izniyle fotoğraf ve video dosyalarının ad, boyut, tarih, tür ve içerik parmak izi gibi teknik özelliklerini işler. Bu bilgiler temizlik önerisi üretmek için cihaz üzerinde kullanılır.

Android 8.0 ve üzerinde isteğe bağlı Uygulama Önbelleği Yöneticisi, Android'in sunduğu uygulama depolama istatistiklerini okuyabilmek için Kullanım Erişimi ister. Uygulama etiketi, paket adı ve bildirilen önbellek bayt toplamı yalnız cihazda işlenir; kişisel dosya içeriği okunmaz ve geliştiriciye yüklenmez. Paket görünürlüğü kullanıcı tarafından başlatılabilen uygulamalarla sınırlıdır; geniş `QUERY_ALL_PACKAGES` erişimi istenmez. Kullanım Erişimi Android Ayarları'ndan her zaman kaldırılabilir.

## Dosya silme

Akıllı Temizleyici kullanıcı seçimi olmadan dosya silmez. Desteklenen Android sürümlerinde silme işlemi ayrıca Android'in sistem onay ekranından geçirilir. Başka bir uygulamanın önbelleği için Android'in resmî uygulama ayrıntıları ekranını açar; işlemi kullanıcı seçer.

## Reklamlar

Uygulama Google Mobile Ads ile reklam gösterir. Google; reklam sunumu, sahtekârlığı önleme, ölçümleme ve kullanıcı tercihlerine göre reklam kişiselleştirme amacıyla cihaz veya reklam kimliği gibi verileri işleyebilir. Gereken bölgelerde kullanıcı tercihleri Google User Messaging Platform üzerinden alınır ve uygulama içinde yeniden yönetilebilir.

## Veri paylaşımı

Kullanıcı dosyaları geliştiriciyle veya reklam sağlayıcısıyla paylaşılmaz. Reklam SDK'sının işlediği teknik veriler Google'ın kendi gizlilik şartlarına tabidir.

## Veri saklama

Temizlik taraması sonuçları ilk sürümde uzaktaki bir sunucuda saklanmaz. Uygulamanın yerel geçici verileri, kullanıcı tarafından uygulama verileri veya önbelleği temizlenerek kaldırılabilir.

## İletişim

Yayımdan önce bu alana gerçek destek e-postası eklenecektir: `[DESTEK_E-POSTASI]`

> Bu metin mağaza yayını öncesinde gerçek AdMob yapılandırması, Veri Güvenliği formu ve yayıncı iletişim bilgileriyle son kez eşleştirilmelidir.
