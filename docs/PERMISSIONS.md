# İzinler

## Ağ

`INTERNET` ve `ACCESS_NETWORK_STATE`, yalnızca Google Mobile Ads ve UMP reklam gizlilik akışı için kullanılır.

## Fotoğraf ve video

- Android 13+: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`
- Android 14+: kullanıcı kısmi erişimi seçerse `READ_MEDIA_VISUAL_USER_SELECTED`
- Android 12 ve altı: sürüme uygun eski medya erişimi

Bu izinler uygulamanın temel işlevi olan fotoğraf/video depolama yönetimi için kullanılır. İçerik analizi cihaz üzerinde gerçekleşir.

## Bilinçli olarak istenmeyen izinler

- `MANAGE_EXTERNAL_STORAGE`
- `QUERY_ALL_PACKAGES`
- Accessibility Service
- arka plan konumu
- kişiler, SMS ve çağrı kayıtları

Bu izinler ilk sürümde gereksiz geniş erişim yaratacağı için eklenmemiştir.
