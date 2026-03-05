## Запуск приложения
В консоли Android Studio:
```
./gradlew :spyplugin:assembleRelease
```
В директории /background/spyplugin/build/outputs будет лежать aar файл. Из него нужно извлечь jar и с помощью d8 преоброзовать в dex. Dex поместить на сервер.
