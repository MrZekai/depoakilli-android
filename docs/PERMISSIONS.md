# İzinler

## Ağ

`INTERNET` ve `ACCESS_NETWORK_STATE`, yalnızca Google Mobile Ads ve UMP reklam gizlilik akışı için kullanılır.

## Fotoğraf ve video

- Android 13+: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`
- Android 14+: kullanıcı kısmi erişimi seçerse `READ_MEDIA_VISUAL_USER_SELECTED`
- Android 12 ve altı: sürüme uygun eski medya erişimi

Bu izinler uygulamanın temel işlevi olan fotoğraf/video depolama yönetimi için kullanılır. İçerik analizi cihaz üzerinde gerçekleşir.

## Uygulama önbelleği ölçümü

`PACKAGE_USAGE_STATS`, Android 8.0 ve üzerindeki cihazlarda diğer kullanıcı uygulamalarının bildirdiği önbellek toplamlarını `StorageStatsManager` ile okuyabilmek için bildirilir. Bu normal bir çalışma zamanı izni değildir; kullanıcı Android'in Kullanım Erişimi ekranından ayrıca etkinleştirir.

Manifestteki `tools:ignore="ProtectedPermissions"` yalnız bu bilinçli AppOps kullanımını Android Lint'e açıklar. İzni kullanıcı adına vermez, sistem ayrıcalığı sağlamaz ve başka lint kontrollerini kapatmaz.

- Yalnız uygulama etiketi, paket adı ve toplam önbellek baytı işlenir.
- Kişisel dosya içeriği, kullanım zamanı veya kullanım geçmişi okunmaz.
- Sorgu arka plan iş parçacığında ve yalnız uygulama açıldığında, geri dönüldüğünde, kullanıcı yenilediğinde veya Akıllı Tarama başlatıldığında çalışır.
- Başka uygulamaların özel önbelleği sessizce silinmez. Kullanıcı ilgili uygulamaya dokunduğunda Android'in resmî uygulama ayrıntıları ekranı açılır.

Paket görünürlüğü `QUERY_ALL_PACKAGES` ile genişletilmez. Manifestte yalnız ana ekranda başlatılabilen kullanıcı uygulamaları için kapsamlı bir `MAIN` + `LAUNCHER` sorgusu bulunur.

## Bilinçli olarak istenmeyen izinler

- `MANAGE_EXTERNAL_STORAGE`
- `QUERY_ALL_PACKAGES`
- Accessibility Service
- arka plan konumu
- kişiler, SMS ve çağrı kayıtları

Bu izinler ilk sürümde gereksiz geniş erişim yaratacağı için eklenmemiştir.
