# Живой реестр состояния проекта (Living State Ledger)

## Цель
Автономная мини-утилита **Pura Hotspot** для смартфонов Huawei (Pura 70, EMUI 14.2 / HarmonyOS).
Задача: автоматическое включение мобильной точки доступа при подключении к выбранному Bluetooth-устройству (автомагнитола, наушники) и отключение при разъединении (с задержкой-антидребезгом).
Ключевые критерии:
- Работа полностью в фоне при заблокированном телефоне в кармане;
- Без необходимости в ПК, руте или отладке по Wi-Fi (которой нет в EMUI 14);
- Единоразовая настройка: установил, выбрал устройство, включил службу и забыл.

## Сделано
1. **Двухуровневый движок автоматизации (`HotspotController.kt`)**:
   - **Основной**: `PuraAccessibilityService.kt` — через службу специальных возможностей (Accessibility). При появлении события Bluetooth будит экран (Screen WakeLock), открывает системную шторку переключателей (`GLOBAL_ACTION_QUICK_SETTINGS`), находит плитку «Точка доступа» / «Личная точка доступа», проверяет ее статус и кликает, после чего закрывает шторку и повторно гасит/блокирует экран (`GLOBAL_ACTION_LOCK_SCREEN`). Не требует разблокировки PIN/отпечатком (плитка в EMUI доступна с экрана блокировки).
   - **Резервный**: `ShizukuManager.kt` — выполнение `cmd wifi start-softap` / `cmd connectivity start-tethering wifi` через ADB-шелл, если на телефоне активен Shizuku.
2. **Фоновый сервис (`HotspotService.kt`)**:
   - `ForegroundService` с типом `connectedDevice` и `START_STICKY`.
   - Регистрация в `AndroidManifest.xml` с автозапуском при перезагрузке устройства (`BootReceiver.kt` на `BOOT_COMPLETED`).
   - Постоянное неблокирующее уведомление с текущим статусом мониторинга.
3. **Фильтрация и антидребезг (`BluetoothReceiver.kt`)**:
   - Перехват `ACTION_ACL_CONNECTED` и `ACTION_ACL_DISCONNECTED`.
   - Настраиваемая задержка отключения (0, 15с, 30с, 60с) с `WakeLock` на время паузы.
4. **Интерфейс (`MainActivity.kt`, Material 3)**:
   - Выбор сопряженного Bluetooth-устройства из выпадающего списка.
   - Выбор интервала отключения.
   - Статус служб: Доступность (Accessibility) и Shizuku.
   - Кнопки ручного тестирования включения/выключения.
   - Поддержка темной темы EMUI (кастомные стили `spinner_item.xml`, `spinner_dropdown_item.xml`).
5. **Публикация и релиз**:
   - GitHub: `https://github.com/C0dece/PuraHotspot`
   - Релиз v1.2.0: собран APK и выложен в Assets + корень репозитория.

## В процессе / Следующий шаг
- Сбор обратной связи от пользователя после установки и теста APK на реальном устройстве Huawei Pura 70 (проверка поведения плитки в шторке при выключенном экране).

## Архитектурные решения
- **Отказ от беспроводного ADB/Shizuku как единственного метода**: В EMUI 14 пункт «Отладка по Wi-Fi» полностью вырезан Huawei, а требование подключать телефон к ПК при каждой перезагрузке неприемлемо для обычного пользователя.
- **Использование Quick Settings Accessibility**: Экран блокировки Huawei по умолчанию разрешает доступ к панели управления/шторке без ввода пин-кода. Это позволяет автоматике нажать плитку за ~300мс и сразу вернуть телефон в спящий режим.

## Грабли (Caveats & Anti-Patterns)
- *Shizuku API 13.x*: метод `Shizuku.newProcess` сделан приватным. Использовать `Shizuku.isPreV11()` (без подчеркивания) или reflection.
- *Темная тема EMUI*: Стандартный `android.R.layout.simple_spinner_dropdown_item` в темной теме EMUI отрисовывается черным текстом по темному фону. Решено созданием выделенных `layout/spinner_item.xml` с явным `android:textColor="?attr/colorOnSurface"`.
- *Плитка в шторке*: Для работы метода Accessibility значок «Точка доступа» обязательно должен присутствовать в верхней панели быстрых переключателей (шторке) пользователя.

## Ключевые файлы
- `app/src/main/java/com/pura/autohotspot/PuraAccessibilityService.kt`
- `app/src/main/java/com/pura/autohotspot/HotspotController.kt`
- `app/src/main/java/com/pura/autohotspot/HotspotService.kt`
- `app/src/main/java/com/pura/autohotspot/BluetoothReceiver.kt`
- `app/src/main/java/com/pura/autohotspot/MainActivity.kt`
- `app/src/main/res/xml/accessibility_service_config.xml`
