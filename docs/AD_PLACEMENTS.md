# Reklam yerleşimleri

Tüm reklam biçimleri yalnızca UMP izin akışı reklam isteğine izin verdiğinde yüklenir.
Debug derlemeleri Google'ın resmî test kimliklerini kullanır; canlı kimlikler yalnız
imzalı release iş akışında GitHub Secrets üzerinden sağlanır.

## Ana Sayfa: orta dikdörtgen (MREC)

- Boyut: 300 × 250 (`AdSize.MEDIUM_RECTANGLE`).
- Konum: Ana Sayfa akışında araç kartları ve son tarama bilgisinden sonra.
- Aynı anda alttaki sabit banner gösterilmez. Böylece reklam yoğunluğu ve yanlış
  tıklama riski azaltılır.
- Medya erişimi verilmeden, UMP izin vermeden veya tarama sürerken gösterilmez.
- Reklam alanı açıkça “Sponsorlu / Sponsored” olarak etiketlenir.

## AI Temizlik ve Araçlar: sabit banner

- Boyut: 320 × 50 standart banner.
- Konum: Alt navigasyonun hemen üstü.
- Ana Sayfa'da MREC bulunduğu için bu banner gizlenir.
- Medya erişimi verilmeden, UMP izin vermeden veya tarama sürerken gösterilmez.

## Temizlik sonrası geçiş reklamı

- Doğal geçiş noktası: Kullanıcının onayladığı temizlik başarıyla tamamlandıktan sonra.
- Sıklık sınırı: En fazla beş dakikada bir.
- Reklam hazır değilse temizlik akışı bekletilmez veya engellenmez.
- Sekme değişiminde, tarama başlangıcında, izin istemede, sistem silme onayından önce
  veya iptal edilen silme işleminde gösterilmez.

## Uygulama açılış reklamı

- `AppOpenAd` yalnız uygulama arka plandan ön plana dönerken değerlendirilir.
- İlk iki ön plana gelişte gösterilmez; en erken üçüncü gelişte gösterilebilir.
- İki gösterim arasında en az iki saat bulunur.
- Dört saatten eski yüklenmiş reklam kullanılmaz ve yeniden yüklenir.
- UMP reklam izni alınmadan yüklenmez veya gösterilmez.
- İlk kurulum/ilk izin deneyimini bölmez ve içerik açıldıktan sonra sürpriz biçimde
  bindirilmez. Reklam hazır değilse uygulama açılışı geciktirilmez.

## Kullanılmayan biçimler

- Ödüllü reklam yoktur.
- Temizlik özelliğini reklama tıklama şartına bağlayan akış yoktur.
- Sistem düğmelerini veya uygulama eylemlerini taklit eden reklam yoktur.
