# Build ortamı — Smart Cleaner v39 (JDK 17 zorunlu)

Bu belge, v39 kapalı test hazırlığında `BUILD FAILED in 4s` ile duran Gradle
hatasının kök nedenini ve kalıcı çözümünü açıklar.

## Kök neden

Temiz klonda çalıştırılan ilk komut

```bash
./gradlew --no-daemon --stacktrace :app:checkDebugAarMetadata
```

şu istisna ile durdu:

```text
java.lang.IllegalArgumentException: 25.0.4
    at org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse(JavaVersion.java:307)
    at org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.current(JavaVersion.java:176)
    at org.jetbrains.kotlin.cli.jvm.modules.JavaVersionUtilsKt.isAtLeastJava9(javaVersionUtils.kt:11)
    ...
    at org.gradle.initialization.ScriptEvaluatingSettingsProcessor.applySettingsScript(...)
```

Okunması gereken üç nokta:

1. Yığın izi **`settings.gradle.kts` derlenirken** oluşuyor. Yani hata proje
   yapılandırmasından, bağımlılıklardan, AAR metadata'sından veya eksik
   `local.properties` dosyasından önce gerçekleşiyor. Android SDK yolu ile
   ilgisi yoktur.
2. Hata, Gradle 8.13'ün içinde gömülü gelen **Kotlin 2.0.21** derleyicisinden
   (`kotlin-compiler-embeddable`) geliyor. Bu derleyicinin içindeki IntelliJ
   `JavaVersion.parse()` yardımcı sınıfı, kabul ettiği en yüksek Java sürümünü
   sabit olarak taşır ve bu sürümün üstündeki her değeri
   `IllegalArgumentException` ile reddeder.
3. `25.0.4` istisna mesajının tamamıdır: Gradle'ı çalıştıran JVM'in
   `java.version` değeridir. Yani **build JDK 25 üzerinde başlatılmıştır.**

Bu davranış doğrudan ölçülerek doğrulandı (Gradle 8.13 ve 8.14.3 aynı
`kotlin-compiler-embeddable-2.0.21` sürümünü taşır):

| `JavaVersion.parse(...)` | Sonuç |
|---|---|
| `1.8.0_292`, `11.0.2`, `17.0.12`, `21.0.10`, `22.0.1`, `23.0.2`, `24.0.1` | OK |
| `25`, `25.0.4`, `26.0.1` | `IllegalArgumentException` |

Sınır tam olarak Java 24 ile 25 arasındadır ve hata metni birebir kullanıcı
logundaki metindir.

## Neden "sadece JAVA_HOME'u değiştir" yeterli değil

Projenin desteklenen derleme JDK'sı zaten 17'dir (AGP 8.13.2, Kotlin 2.2.21,
`sourceCompatibility`/`jvmTarget` 17). Ancak `JAVA_HOME` makineye bağlı bir
ayardır: Android Studio'nun paket JBR'si, Windows'ta PATH'e eklenmiş yeni bir
JDK veya CI dışı bir kabuk bunu sessizce değiştirebilir. Depoya kişisel bir
`org.gradle.java.home` yolu yazmak da yasaktır (bkz. `SECURITY_RULES.md` #4).

Ayrıca hata `settings.gradle.kts` **derlenirken** oluştuğu için, Kotlin DSL
içine yazılacak bir kontrol JDK 25'te hiç çalışamaz. Koruma, script
derlenmeden önce devreye girmelidir.

## Uygulanan çözüm

### 1. `gradle/gradle-daemon-jvm.properties` (birincil düzeltme)

```properties
toolchainVersion=17
```

Gradle 8.8+ "Daemon JVM criteria" mekanizmasıdır. Wrapper'ı hangi JVM
başlatırsa başlatsın, Gradle daemon'ı **Java 17 toolchain'i üzerinde** çatallar.
Kotlin DSL derlemesi daemon içinde gerçekleştiği için hata kaynağı ortadan
kalkar ve yerel Windows Git Bash ile GitHub Actions aynı sonucu üretir.

Gereksinim: makinede bulunabilir bir Java 17 JDK kurulu olmalıdır (JAVA_HOME,
SDKMAN!, Android Studio JBR 17, `/usr/lib/jvm`, `Program Files\Eclipse Adoptium`
vb.). Bulunamazsa Gradle artık anlaşılmaz bir `IllegalArgumentException` yerine
şu net mesajı verir:

```text
Unable to download toolchain matching the requirements ({languageVersion=17, ...})
```

Çözümü: Temurin 17 kurun (veya Android Studio'nun JBR 17'sini kullanın).

### 2. `settings.gradle.kts` içinde hızlı-başarısızlık koruması (ikinci hat)

`gradle/gradle-daemon-jvm.properties` silinir veya yok sayılırsa, script
derlenebildiği her sürümde (17–24) çalışan bir `check` devreye girer ve
17 dışındaki sürümlerde uyarı, 17 altındaki/24 üstündeki sürümlerde
açıklayıcı bir hata üretir.

### 3. `scripts/bootstrap-local-env.sh` (temiz klon açılışı)

Temiz bir klonda ilk Gradle komutundan önce çalıştırılır:

```bash
bash scripts/bootstrap-local-env.sh
```

- JDK durumunu raporlar (JAVA_HOME 25 ise ne olacağını açıkça söyler).
- `ANDROID_HOME` / `ANDROID_SDK_ROOT` veya bilinen varsayılan konumlardan
  Android SDK'yı bulur ve **git tarafından yok sayılan** `local.properties`
  dosyasını yazar. Windows Git Bash altında `/c/Users/...` biçimindeki MSYS
  yolunu Java/Gradle'ın okuyacağı `C\:/Users/...` biçimine dönüştürür. Depoya
  hiçbir kişisel yol yazılmaz.
- `platforms/android-36` eksikse uyarır (compileSdk/targetSdk 36).
- `gradlew` çalıştırılabilir değilse `chmod +x` uygular.

## Neden Gradle sürümü yükseltilmedi

Gradle 8.14.3 de aynı `kotlin-compiler-embeddable-2.0.21` sürümünü taşır ve
Java 25'te aynı şekilde başarısız olur (bu ortamda doğrudan ölçüldü). Java 25'i
destekleyen bir Gradle sürümüne geçmek AGP 8.13.2 uyumluluk matrisini yeniden
doğrulamayı gerektirir ve kapalı test öncesinde gereksiz risktir. Proje zaten
JDK 17'yi hedeflediği için doğru ve asgari düzeltme daemon'ı 17'ye sabitlemektir.

| Bileşen | Sürüm | Not |
|---|---|---|
| Gradle wrapper | 8.13 | Değiştirilmedi |
| AGP | 8.13.2 | Gradle 8.13 ile uyumlu |
| Kotlin | 2.2.21 | Derleme Kotlin'i (gömülü DSL Kotlin'i 2.0.21'dir) |
| Build JDK | **17** | `gradle-daemon-jvm.properties` ile sabitlendi |
| compileSdk / targetSdk | 36 | Değiştirilmedi |
| minSdk | 30 | Değiştirilmedi |

## Temiz klonda doğrulama sırası

### Windows bellek güvenliği

Android Lint'in Debug ve QA varyantlarını aynı Gradle çağrısında analiz etmek,
16 GB Windows sisteminde yüzlerce iş parçacığı ve birden fazla lint modeli
oluşturabilir. v39 doğrulamasında Windows commit alanı 39 MB'a düştüğünde G1,
ek 200 MB sanal alan ayıramadı ve JVM native OOM ile kapandı. Bu bir lint
bulgusu veya kaynak kod hatası değildir.

Bu nedenle depo ayarları Gradle heap'ini 2 GB ile, worker sayısını 2 ile
sınırlar ve paralel proje yürütmesini kapatır. Yerel teslim scripti ayrıca
`lintDebug` ile `lintQa` görevlerini ayrı JVM süreçlerinde çalıştırır. Uzun bir
build öncesinde Windows'ta yeterli boş fiziksel/sanal bellek bulunmalıdır.

```bash
bash scripts/bootstrap-local-env.sh
python3 scripts/validate-project.py
./gradlew --no-daemon --stacktrace :app:checkDebugAarMetadata
./gradlew --no-daemon :app:dependencyInsight --configuration debugRuntimeClasspath --dependency androidx.fragment:fragment
./gradlew --no-daemon --stacktrace testDebugUnitTest
./gradlew --no-daemon --stacktrace lintDebug
./gradlew --no-daemon --stacktrace assembleQa
bash scripts/verify-qa-signing.sh app/build/outputs/apk/qa/app-qa.apk
bash scripts/verify-qa-apk.sh app/build/outputs/apk/qa/app-qa.apk
```

`./gradlew -version` çıktısındaki `JVM:` satırı 17 göstermelidir. Göstermiyorsa
daemon kriteri devreye girmemiş demektir; Java 17 kurulumunu kontrol edin.
