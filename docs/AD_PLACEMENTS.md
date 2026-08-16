# DepoAkıllı reklam yerleşimleri

## Banner

- Konum: Uygulamanın alt bölümünde, üç sekmeli alt navigasyonun hemen üstü.
- Ekranlar: Ana Sayfa, AI Temizlik sonuçları ve Araçlar.
- Koşullar: UMP reklam izni alınmış ve kullanıcı medya erişimi vermiş olmalı.
- Gizlendiği durumlar: İlk izin ekranı ve aktif AI taraması.

## Geçiş reklamı

- Doğal geçiş noktası: Kullanıcının onayladığı temizlik başarıyla tamamlandıktan sonra.
- Sıklık sınırı: En fazla 5 dakikada bir.
- Gösterilmediği durumlar: Uygulama açılışı, sekme değişimi, izin isteme,
  tarama başlangıcı, silme onay ekranı ve iptal edilen silme işlemi.
- Reklam hazır değilse temizlik akışı bekletilmez veya engellenmez.

## İlk sürümde kullanılmayan formatlar

- Uygulama açılış reklamı yoktur.
- Ödüllü reklam yoktur.
- Kullanıcı eylemini taklit eden veya sistem düğmelerine bitişik reklam yoktur.

Debug derlemeleri Google'ın resmî test reklam kimliklerini kullanır. Canlı AdMob
kimlikleri yalnız imzalı release workflow'unda GitHub Secrets üzerinden sağlanır.
