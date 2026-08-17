# Akıllı Temizleyici ana ekran tasarım kararı

Ana ekran için kullanıcı tarafından 4 numaralı konsept kesin olarak seçildi. Bu
dosya, sonraki değişikliklerin seçilen görsel yönü yanlışlıkla bozmasını önlemek
için karar kaydıdır.

## Sabit görsel yapı

- Görünen Türkçe uygulama adı yalnızca `Akıllı Temizleyici` olmalıdır.
- Üst bölüm lacivertten mavi ve turkuaza geçen güçlü bir gradyan kullanır.
- Gerçek depolama doluluk oranı ile kullanılabilir RAM, üstte tek bir özet
  şeridinde görünür.
- `Telefonun Hazır` mesajı, kalkan/temizlik görseli ve beyaz `AKILLI TARAMA`
  düğmesi ana odaktır.
- Araç alanı aynı kartların tekrarından oluşmaz:
  - Büyük mavi `Çöp Temizliği` kartı
  - Yeşil `Uygulama belleğini boşalt` kartı
  - Mor `Kopyaları Sil` kartı
  - Ayrı yatay `WhatsApp Temizleyici`, `Büyük Dosyalar` ve
    `Uygulama Önbelleği` satırları
- Banner reklam, alt gezinmenin hemen üzerinde sabit ve içerikten ayrılmıştır.
- Alt gezinme `Ana Sayfa`, `Temizlik`, `Araçlar` sırasını korur.

## Davranış kuralları

- Akıllı Tarama ve Çöp Temizliği, medya izni verilmemiş olsa bile uygulamanın
  kendi güvenli geçici dosyalarını ve erişilebilen ortak çöp adaylarını tarar.
- Kopyaları Sil ve Büyük Dosyalar, yalnız kullanıcı o araca dokunduğunda fotoğraf
  ve video erişimi ister.
- WhatsApp Temizleyici, Android belge seçicisiyle kullanıcının seçtiği WhatsApp
  Media klasörünü tarar; genel depolama yetkisi istemez.
- RAM kartı başka uygulamaları kapatmış gibi davranmaz. Akıllı Temizleyici'nin
  ağır tarama/reklam kaynaklarını bırakır ve gerçek RAM değerini yeniden ölçer.
- Uygulama Önbelleği, Android'in bildirdiği gerçek önbellek miktarlarını gösterir
  ve diğer uygulamalar için Android'in resmî depolama ekranını açar.

Teknik test paketindeki `.qa` kimliği kullanıcı arayüzünde gösterilmez ve uygulama
adına eklenmez.
