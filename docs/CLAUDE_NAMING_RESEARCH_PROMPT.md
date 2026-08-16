# Claude için global uygulama adı araştırma promptu

Sen kıdemli bir global mobil marka stratejisti, Google Play ASO araştırmacısı ve ön marka-risk analistisin. 16 Ağustos 2026 itibarıyla aşağıdaki Android uygulaması için masa başı araştırma yaparak globalde kullanılabilecek en güçlü uygulama adını bul.

## Ürün bağlamı

- Kalıcı Android paket adı: `com.mrzekai.depoakilli`. Paket adı değişmeyecek; ancak kullanıcıya görünen uygulama ve Play Store adı değişebilir.
- Mevcut Türkçe prototip adı: “DepoAkıllı”.
- Geçici çalışma adları: İngilizce “Smart Cleaner”, Türkçe “Akıllı Temizleyici”. Bunları varsayılan kazanan kabul etme; fazla jenerik olup olmadıklarını kanıtla.
- Platform: Android, Kotlin + Jetpack Compose.
- Pazar: önce global; İngilizce ana mağaza dili, Türkçe tam yerelleştirme. Sonraki aşamada Almanca, İspanyolca, Portekizce, Fransızca, Endonezce ve Hintçe düşünülebilir.
- Gelir modeli: ücretsiz + AdMob.
- Temel vaat: güvenli, kullanıcı onaylı, cihaz üzerinde depolama düzenleme ve temizleme.
- Özellikler: gerçek depolama/RAM bilgisi; tam içerik parmak iziyle yinelenen dosya tespiti; eski ekran görüntüleri, büyük videolar, indirilenler ve APK paketleri için açıklanabilir öneriler; uygulamanın kendi önbelleğini temizleme; Android'in resmî depolama, uygulama ve bellek ayarlarına yönlendirme; İngilizce/Türkçe arayüz.
- Gizlilik konumu: fotoğraf/video analizi cihaz üzerinde; kullanıcı onayı olmadan silme yok.
- Dürüstlük sınırı: uygulama başka uygulamaların özel önbelleğini sessizce silemez, RAM'i zorla boşalttığını veya CPU'yu soğuttuğunu iddia etmez. “AI” ifadesi yalnız açıklanabilir cihaz-içi öneri motorunu doğru biçimde anlatıyorsa kullanılmalı.
- Mevcut görsel yön: koyu zümrüt + canlı lime. Bu palet henüz kesinleşmedi.

## Zorunlu araştırma

Web'de güncel araştırma yap. Her önemli bulgu için doğrudan URL, erişim tarihi ve kısa kanıt notu ver. Arama sonucu sayfasını değil mümkün olduğunca asıl kaynağı kullan.

1. Google Play'de en az 25 doğrudan/komşu rakibi incele. Uygulama adı, geliştirici, indirme bandı, puan, son güncelleme, adlandırma kalıbı ve öne çıkan kelimeleri tabloya yaz. Özellikle “cleaner”, “storage”, “files”, “duplicate photos”, “phone clean”, “smart clean” kümelerini karşılaştır.
2. Aday kelimelerin arama niyetini Google Trends ve erişebildiğin güncel ASO/anahtar kelime kaynaklarıyla karşılaştır. Veri yoksa bunu açıkça söyle; sayı uydurma.
3. Her finalist için Google Play tam ad araması, genel web araması ve mümkünse Apple App Store çakışma taraması yap.
4. Her finalist için WIPO Global Brand Database, EUIPO eSearch/TMview, USPTO Trademark Search ve TÜRKPATENT üzerinde ön tarama yap. İlgili sınıfları özellikle Nice 9 ve 42 açısından değerlendir. Bu yalnız ön taramadır; hukuki marka tescil görüşü gibi sunma.
5. `.com` alan adı ile mantıklı alternatif alan adlarını; temel sosyal kullanıcı adlarını ve GitHub organizasyon/repo adı çakışmalarını kontrol et. Kesin müsaitlik doğrulanamıyorsa “doğrulanamadı” yaz.
6. Adayların İngilizce, Türkçe, Almanca, İspanyolca, Portekizce, Fransızca, Endonezce ve Hintçe konuşan kişilerde telaffuz, yanlış anlam, olumsuz çağrışım ve yazım riski için dilsel ön kontrolünü yap.
7. Google Play'in güncel uygulama başlığı karakter sınırını ve yanıltıcı metadata politikalarını resmî Google kaynağından doğrula. “#1”, “best”, “free”, gereksiz emoji, anlamsız anahtar kelime yığma veya desteklenmeyen performans iddiaları önerme.

## Ad üretme kuralları

- En az 30 özgün aday üret.
- Hem markalaşabilir icat/hibrit adlar hem de açıklayıcı fakat ayırt edilebilir adlar olsun.
- Ana marka mümkünse 4–12 harf, kolay okunur ve sesli söylenebilir olsun.
- Play Store başlığı güncel karakter sınırına uysun.
- “Clean Master”, “CCleaner”, “Files by Google”, “AVG Cleaner”, “Norton Clean” gibi bilinen markalara fonetik/görsel olarak yaklaşma.
- Tek başına “Smart Cleaner”, “Phone Cleaner”, “AI Cleaner”, “Storage Cleaner” gibi aşırı jenerik adları ancak araştırma açıkça destekliyorsa finalist yap.
- “Booster”, “Turbo”, “Battery Saver”, “Antivirus”, “CPU Cooler”, “Junk remover” gibi üründe doğrulanmayan vaatleri kullanma.
- Türkçede iyi duran ama globalde telaffuz edilemeyen; ya da İngilizcede iyi olup başka hedef dillerde olumsuz çağrışımı bulunan adları ele.

## Puanlama modeli

Her adayı 100 üzerinden puanla ve ağırlıkları aynen kullan:

- Ayırt edicilik ve hatırlanabilirlik: 20
- Google Play/ASO arama niyeti: 15
- Marka ön taraması riski: 20
- Mağaza ve rakip çakışma riski: 15
- Global telaffuz ve dil güvenliği: 10
- Alan adı/sosyal isim uygulanabilirliği: 10
- Ürünün dürüst konumuna uyum: 10

Bir veri doğrulanamadığında o alt puanı iyimser biçimde yükseltme; belirsizliği ve gerekli manuel kontrolü yaz.

## İstenen çıktı

1. En fazla 12 maddelik yönetici özeti.
2. Rakip ve anahtar kelime araştırma tablosu.
3. 30 adayın tamamı: ad, kısa anlam/hikâye, önerilen İngilizce Play başlığı, önerilen Türkçe Play başlığı, toplam puan ve tek cümle risk.
4. Puanı en yüksek 10 aday için ayrıntılı karşılaştırma matrisi.
5. Son üç aday için:
   - neden uygun;
   - neden elenebilir;
   - doğrulanmış mağaza/marka/domain bulguları;
   - İngilizce ve Türkçe kısa slogan;
   - koyu zümrüt/lime paletin uyumu veya kanıta dayalı alternatif renk yönü;
   - uygulama ikonu için tek cümlelik görsel fikir.
6. Tek bir nihai öneri ve ikinci tercih. “Kazanan” demeden önce hangi kontrollerin eksik kaldığını yaz.
7. “DepoAkıllı”, “Smart Cleaner” ve “Akıllı Temizleyici” için ayrı ayrı devam et/değiştir kararı ver.
8. Son bölümde kaynakları konu başlıklarına göre grupla; erişilemeyen veya doğrulanamayan kaynakları açıkça işaretle.

Önemli: İsim, indirme, arama hacmi, marka kaydı, alan adı veya mağaza müsaitliği uydurma. Canlı olarak doğrulayamadığın her iddiayı “doğrulanamadı” etiketiyle belirt. Bulgular ile yorumlarını birbirinden ayır.
