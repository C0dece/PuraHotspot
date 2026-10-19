@echo off
chcp 65001 >nul
echo ========================================================
echo   Запуск службы Shizuku на Huawei Pura 70 / EMUI 14
echo ========================================================
echo.
echo Убедитесь, что:
echo  1. Телефон подключен к компьютеру по USB кабелю.
echo  2. В телефоне включена «Отладка по USB» в меню разработчика.
echo.
echo Проверка подключения устройства...
adb devices
echo.

echo Попытка запуска Shizuku...
adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
if %errorlevel% neq 0 (
    echo Попытка запуска по альтернативному пути...
    adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
)

echo.
echo ========================================================
echo Готово! Откройте приложение Shizuku на телефоне:
echo Статус должен стать «Shizuku запущен».
echo Теперь Pura Hotspot работает 100%% программно и бесшумно!
echo ========================================================
pause
