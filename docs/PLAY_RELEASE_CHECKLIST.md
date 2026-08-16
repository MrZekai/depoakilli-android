# Google Play Release Kontrol Listesi

## Kod ve derleme

- [ ] `./gradlew testDebugUnitTest lintDebug assembleDebug` geçiyor
- [ ] Kapalı test APK'sı en az iki fiziksel Android cihazda çalışıyor
- [ ] Android 13, 14, 15 ve 16 izin akışları test edildi
- [ ] Silme iptali hiçbir öğeyi sonuç listesinden kaldırmıyor
- [ ] Büyük dosya taraması ana iş parçacığını bloklamıyor
- [ ] Çökme/ANR raporları temiz

## Kimlik ve imza

- [ ] Paket adı `com.mrzekai.depoakilli`
- [ ] İlk upload key üretildi
- [ ] Keystore en az iki güvenli yerde yedeklendi
- [ ] GitHub secrets tamamlandı
- [ ] İmza sertifikası SHA-256 değeri kaydedildi

## AdMob

- [ ] AdMob uygulaması oluşturuldu
- [ ] Banner, MREC, interstitial ve App Open birimleri oluşturuldu
- [ ] UMP Avrupa düzenlemeleri mesajı yayımlandı
- [ ] Canlı kimlikler yalnızca GitHub secrets içinde
- [ ] Reklamlar sistem silme onayıyla karıştırılmıyor
- [ ] Geçiş reklamı temizleme sonucundan sonra ve seyrek gösteriliyor
- [ ] App Open ilk iki kullanımı bölmüyor, UMP öncesi istek yapmıyor ve iki saat sınırına uyuyor
- [ ] Ana Sayfa MREC ile sabit banner aynı anda gösterilmiyor

## Play politikaları

- [ ] Fotoğraf/video izin deklarasyonu hazır
- [ ] Uygulama içi belirgin izin açıklaması ekran görüntüsü hazır
- [ ] Veri Güvenliği formu gerçek SDK davranışıyla eşleşiyor
- [ ] Gizlilik politikası HTTPS üzerinde yayımlandı
- [ ] “RAM hızlandırma”, “CPU soğutma” ve erişilemeyen cache vaatleri mağaza metninde yok

## Mağaza varlıkları

- [ ] 512×512 uygulama ikonu
- [ ] 1024×500 feature graphic
- [ ] En az dört telefon ekran görüntüsü
- [ ] İngilizce ve Türkçe kısa/uzun açıklama
- [ ] Global marka adı için ASO, alan adı ve marka ön taraması tamamlandı
- [ ] Destek e-postası ve HTTPS web sayfası
