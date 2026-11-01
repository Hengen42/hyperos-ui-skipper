# HyperOS UI Skipper (`hyperos-ui-skipper`)

[English Documentation](README.md) | [Türkçe Dokümantasyon](README_TR.md)

![License](https://img.shields.io/badge/License-MIT-green.svg)
![Platform](https://img.shields.io/badge/Platform-HyperOS%201%20%7C%202%20%7C%203%20%7C%20MIUI-blue.svg)
![Version](https://img.shields.io/badge/Version-v1.0.2-brightgreen.svg)
![Root](https://img.shields.io/badge/Root-KernelSU%20%7C%20KernelSU--Next%20%7C%20APatch%20%7C%20Magisk-orange.svg)
![Hook](https://img.shields.io/badge/Hook-Vector%20%7C%20LSPosed%20(Xposed%20API%2093%2B)-informational.svg)

**Geliştirici:** Hengen  
**Lisans:** MIT  
**Proje Türü:** Açık Kaynak UI Optimizasyonu ve Geliştirici İş Akışı Modülü

---

## 1. Gereksinimler (Gerekli Uygulamalar)

Bu modülün çalışabilmesi için cihazınızda aşağıdaki bileşenlerin kurulu olması **zorunludur**:
- **Root Yöneticisi:** [KernelSU-Next](https://github.com/KernelSU-Next/KernelSU-Next), [KernelSU](https://github.com/tiann/KernelSU), [Magisk](https://github.com/topjohnwu/magisk) veya [APatch](https://github.com/bmax121/APatch)
- **Zygisk Altyapısı:** Magisk Dahili Zygisk, [NeoZygisk](https://github.com/JingMatrix/NeoZygisk) veya [ZygiskNext](https://github.com/Dr-TSNG/ZygiskNext)
- **Xposed Çerçevesi:** [Vector](https://github.com/JingMatrix/Vector) veya [LSPosed](https://github.com/LSPosed/LSPosed)

### Doğrulanan ve Test Edilen Ortam (Geliştirici Kurulumu)
Geliştirici tarafından bizzat test edilip %100 çalıştığı doğrulanan cihaz yapılandırması:
- **Cihaz & ROM:** Xiaomi 14T Pro (`rothko`) — HyperOS 3.0 (Android 16)
- **Root Yöneticisi:** [KernelSU-Next](https://github.com/KernelSU-Next/KernelSU-Next)
- **Zygisk:** [NeoZygisk](https://github.com/JingMatrix/NeoZygisk)
- **Kanca Çerçevesi:** [Vector](https://github.com/JingMatrix/Vector)

---

## 2. Proje Amacı ve Problem Tanımı

Xiaomi'nin **HyperOS** ve **MIUI** arayüzünde (`com.miui.securitycenter`), kullanıcının kendi isteğiyle açmak istediği birçok standart geliştirici ve sistem ayarında tam ekran, 10 saniye geri sayımlı bir uyarı ekranı (**bkz. `dangers.jpeg`**) çıkar.

Bu bekleme ekranının tetiklendiği başlıca yerler:
- **Geliştirici Seçenekleri:** USB Hata Ayıklama (USB Debugging), USB üzerinden Yükleme ve USB Hata Ayıklama (Güvenlik Ayarları) açılırken.
- **Erişilebilirlik (Accessibility):** Otomasyon ve yardımcı uygulamalara erişilebilirlik izni verirken.
- **Bilinmeyen Kaynaklar (Install Unknown Apps):** Harici APK paketlerine kurulum izni verirken.
- **Otomatik Başlatma (Auto-start) ve Özel İzinler:** Uygulamaların arka plan kısıtlamalarını kaldırırken.

**HyperOS UI Skipper**, bu yapay 10 saniyelik arayüz gecikmesini kaldırır; onay sürecini ise kullanıcının tercihine göre esnekleştirir.

---

## 2. Görsel Referans (`dangers.jpeg`)

Proje ana dizininde bulunan **`dangers.jpeg`** dosyası hedef arayüzü gösterir:
- **Üst Başlık:** Uyarı simgesi ve "Tehlike" (Danger) başlığı.
- **Onay Kutusu (`CheckBox`):** *"Olası risklerin farkındayım..."* metni.
- **Onay Butonu (`Button`):** Sayaç bitene kadar kilitli tutulan **"Tamam (10s)"** butonu.

---

## 3. Çalışma Seçenekleri

Modül, kullanıcı kontrolünü elden bırakmak istemeyenler ve tamamen akıcı bir deneyim isteyenler için iki net seçenek sunar:

| Seçenek | Rozet | Açıklama |
|---|---|---|
| **Sadece Sayacı Atla** | `Önerilen` | 10 saniyelik yapay bekleme anında biter. Güvenlik risklerini incelemek, onay kutusunu işaretlemek ve "Tamam"a basmak **sizin kontrolünüzde** kalır. Güvenlik onayını by-pass etmez. |
| **Tam Otomatik Onayla** | `Anında Geçiş` | Sayaç anında sıfırlanır, onay kutusu otomatik işaretlenir ve "Tamam" butonuna basılır. Uyarı ekranı hiç görünmeden anında geçer. |

---

## 4. Mimari ve Nasıl Çalışır

Proje iki parçadan oluşur; **kullanıcının ekstra bir şey yapmasına gerek kalmadan** tek bir zip dosyası içinde birlikte çalışırlar:

1. **Xposed Kanca Modülü (`dev.hengen.uiskipper`):**
   - Görünmezdir; launcher ikonu veya Activity içermez, **hiçbir root izni istemez**.
   - `com.miui.securitycenter` sürecine çalışma zamanında enjekte edilir.
   - **Dinamik UI Gezinimi (UI-Traversal):** Proguard/R8 kod karartmalarından etkilenmemek için doğrudan Android UI ağacındaki `CheckBox` ve `Button` bileşenlerini algılar ve tıklar.
2. **Kök Modülü ve WebUI (`hyperos_ui_skipper`):**
   - KernelSU, KernelSU Next, APatch ve Magisk ile tam uyumludur.
   - Ayarları yönetmek için şık, iki dilli (Türkçe / İngilizce) bir WebUI sunar.
   - Vector (`zygisk_vector`) ve LSPosed (`zygisk_lsposed`) çerçevelerini otomatik tanır ve CLI üzerinden modülü tek tıkla etkinleştirir.

---

## 5. Kurulum (Son Kullanıcı İçin)

Kullanıcıların harici bir APK indirmesine veya elle yüklemesine gerek yoktur:

1. **Ön Hazırlık:** Cihazınızda aşağıdaki bileşenlerin aktif olduğundan emin olun:
   - **Root:** [KernelSU-Next](https://github.com/KernelSU-Next/KernelSU-Next), [KernelSU](https://github.com/tiann/KernelSU), [Magisk](https://github.com/topjohnwu/magisk) veya [APatch](https://github.com/bmax121/APatch)
   - **Zygisk:** Magisk Dahili Zygisk, [NeoZygisk](https://github.com/JingMatrix/NeoZygisk) veya [ZygiskNext](https://github.com/Dr-TSNG/ZygiskNext)
   - **Xposed:** [Vector](https://github.com/JingMatrix/Vector) veya [LSPosed](https://github.com/LSPosed/LSPosed)
2. [Releases](../../releases) bölümünden en güncel **`HyperOS-UI-Skipper-v*.zip`** dosyasını indirin.
3. Modülü **KernelSU**, **APatch** veya **Magisk** üzerinden flaşlayın.
   > [!NOTE]  
   > Flaşlama betiği (`customize.sh`), gerekli kanca APK'sını zip'in içinden otomatik olarak kurar, Vector/LSPosed üzerinde otomatik etkinleştirir ve kapsamı ayarlar. **Cihazı yeniden başlatmanıza gerek yoktur.**
4. Modül yöneticinizdeki **WebUI** simgesine tıklayarak dilediğiniz çalışma seçeneğini belirleyin.

---

## 6. Geliştiriciler İçin Derleme (Build from Source)

Projeyi kendiniz derlemek isterseniz:

### Android Studio / Gradle ile:
```bash
./gradlew :xposed-module:assembleRelease
```

### Yerel PowerShell Scriptleri ile (Windows):
```powershell
# 1. Kanca APK'sını derle:
.\xposed-module\build.ps1

# 2. Flaşlanabilir Modül Zip'ini oluştur:
.\zygisk-module\build.ps1
```
Çıktı `zygisk-module\HyperOS-UI-Skipper-v*.zip` konumunda hazır olacaktır.

---

## 7. Lisans ve Katkı
- **Geliştirici:** Hengen
- **Lisans:** MIT License
