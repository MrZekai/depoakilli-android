# Reklam yerleşimleri

Tüm reklam biçimleri yalnızca UMP izin akışı reklam isteğine izin verdiğinde yüklenir.
Debug derlemeleri Google'ın resmî test kimliklerini kullanır; canlı kimlikler yalnız
imzalı release iş akışında GitHub Secrets üzerinden sağlanır.

## Tüm ana sekmeler: sabit banner

- Boyut: 320 × 50 standart banner.
- Konum: Alt navigasyonun hemen üstü.
- Ana Sayfa, AI Temizlik ve Araçlar sekmelerinde aynı görünür konumu kullanır.
- UMP izin vermeden reklam isteği yapılmaz; banner alanı onay ve tarama sırasında
  sabit tutulduğu için alt navigasyon yer değiştirmez.
- Banner ile alt navigasyon arasında 8dp boşluk ve ayırıcı çizgi bulunur.
- Ana Sayfa'daki 300 × 250 MREC kaldırılmıştır; böylece içerik tekrarı ve reklam
  yoğunluğu azaltılırken küçük banner her zaman bulunabilir kalır.

## Temizlik sonrası geçiş reklamı

- Doğal geçiş noktası: Kullanıcının onayladığı temizlik başarıyla tamamlandıktan sonra.
- Sıklık sınırı: En fazla beş dakikada bir.
- Reklam hazır değilse temizlik akışı bekletilmez veya engellenmez.
- Sekme değişiminde, tarama başlangıcında, izin istemede, sistem silme onayından önce
  veya iptal edilen silme işleminde gösterilmez.
- Sistem seçici/izin/silme ekranından dönüşte App Open bastırılır; temizlik sonrası
  en fazla tek bir tam ekran reklam değerlendirilir.
- App Open gösteriminden sonraki 30 saniye içinde geçiş reklamı gösterilmez.

## Uygulama açılış reklamı

- `AppOpenAd` yalnız uygulama arka plandan ön plana dönerken değerlendirilir.
- İlk iki ön plana gelişte gösterilmez; en erken üçüncü gelişte gösterilebilir.
- İki gösterim arasında en az iki saat bulunur.
- Dört saatten eski yüklenmiş reklam kullanılmaz ve yeniden yüklenir.
- Bellek bırakma işleminden sonraki 60 saniye boyunca yeni tam ekran reklam isteği
  yapılmaz.
- UMP reklam izni alınmadan yüklenmez veya gösterilmez.
- İlk kurulum/ilk izin deneyimini bölmez ve içerik açıldıktan sonra sürpriz biçimde
  bindirilmez. Reklam hazır değilse uygulama açılışı geciktirilmez.

## Kullanılmayan biçimler

- Ödüllü reklam yoktur.
- Temizlik özelliğini reklama tıklama şartına bağlayan akış yoktur.
- Sistem düğmelerini veya uygulama eylemlerini taklit eden reklam yoktur.
