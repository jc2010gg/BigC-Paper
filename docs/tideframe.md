# Tideframe integrado en BigCasares

Tideframe es un modulo interno (`tideframe-ui`) compilado desde sus fuentes dentro del mismo JAR de BigCasares.
No se instala `Tideframe.jar` ni se necesita un JAR binario de su API para compilar. BigCasares sigue siendo el
unico `JavaPlugin` y el unico descriptor `plugin.yml`. El modulo puede desactivarse como los demas.

## Codigo y recursos

- `src/main/java/dev/tideframe/`: motor original, API, eventos, camara, cursor, HUD, logica, serializacion,
  generador de pack, importadores, HTTP, observadores, demostraciones y compatibilidad.
- `src/main/java/dev/linqfy/bigCasares/modules/tideframe/`: lifecycle y adaptacion a misiones/tienda.
- `src/main/resources/tideframe/engine/`: configuracion y recursos del motor; no reemplazan `config.yml` de BigCasares.
- `src/main/resources/tideframe/screens/`: pantallas BigCasares.
- `src/test/java/dev/tideframe/`, `src/test/resources/`: pruebas y fixtures del motor original.
- `tools/tideframe-editor/`: fuentes, recursos y pruebas del editor web.
- `META-INF/tideframe/`: licencia MIT y procedencia de recursos conservadas en el JAR.

El paquete `dev.tideframe` conserva nombres e IDs para compatibilidad de API. `TideframePlugin` ya no hereda
de `JavaPlugin`: recibe al host BigCasares. Los listeners, comandos y tareas tienen un scope del motor;
apagarlo no desregistra servicios o listeners ajenos. Al parar se restauran camaras, cierran sesiones,
observadores, HTTP y ejecutores, y se retiran sus tareas y servicio API.

## Instalacion y datos

1. Instalar solamente el JAR final de BigCasares, ademas de sus dependencias habituales (BetterModel, etc.).
2. No instalar tambien el plugin independiente Tideframe: el modulo rechaza ese conflicto para evitar
   duplicar comandos, proveedores y packs.
3. El motor crea `plugins/BigCasares/tideframe/engine/config.yml`. Sus carpetas `screens`, `images`, `sounds`,
   `packs` y archivos de intercambio con el editor quedan bajo ese mismo directorio.
4. Si se migra una instalacion independiente, con el servidor detenido conservar una copia de sus datos y
   trasladar configuracion, pantallas editadas, imagenes y sonidos a esa carpeta antes del primer arranque.
   No se borran ni sobrescriben automaticamente los archivos de `plugins/Tideframe`.

Las pantallas `bigc_misiones` y `bigc_tienda` se instalan antes de arrancar el motor para entrar en su primer pack.
Una pantalla editada se conserva; una version bundled nueva queda aparte para comparar.

## Configuracion

En la configuracion principal de BigCasares:

```yaml
modules:
  tideframe-ui:
    enabled: true
tideframe-ui:
  missions: true
  shop: true
```

La configuracion especifica del motor vive en `tideframe/engine/config.yml`, dentro de los datos de BigCasares.
Conserva las opciones originales: display, sonidos, demos, disparadores, editor y publicacion del pack.
Por defecto el servidor HTTP escucha en loopback, puerto 8136. Para jugadores remotos el operador debe configurar
`pack.public-url` con una URL alcanzable; incluir el motor en el JAR no hace publica una URL local.
No se cambian routers, puertos del sistema ni firewall automaticamente.

El motor usa su estado real de `pack.send` y el UUID del pack cargado, no el archivo de otro plugin.
Con `pack.send: false`, misiones y tienda conservan sus menus clasicos: no se asume que cualquier pack incluya
las fuentes de Tideframe. Mantener `display.auto-fit: false` salvo pruebas controladas: ese shader experimental
puede chocar con otros packs o con Iris/OptiFine.

## Uso y autoridad

- `/tideframe` y sus subcomandos pertenecen a BigCasares. Permisos `tideframe.use` (true) y `tideframe.edit` (op).
- `/misiones`: listas diarias/semanales de solo lectura; Reclamar usa el inventario y servicio originales.
- `/shop` y NPC: categorias, paginas, compra izquierda y venta derecha, siempre a traves de `ShopService`.
- El resto de menus de BigCasares no se convierte automaticamente: incluir todo el motor no equivale a redisenar
  todas las pantallas del plugin.
- Java usa Tideframe cuando la pantalla y su pack estan listos. Bedrock conserva los formularios existentes;
  no se afirma que el HUD Java funcione en Bedrock.
- Planeando, en vehiculo, en el aire, muerto o sin pack: no se fija la camara y se utiliza el menu clasico.
- Una apertura prepara su vista en el evento de apertura. Cierres anteriores no borran la vista nueva.
- Solo la navegacion mas reciente puede abrir su fallback o reclamos, incluidos menus clasicos y aperturas en cola.
  Si hay una camara bloqueada de otra pantalla, se solicita cerrarla antes de abrir un inventario por debajo.
- La espera de medio segundo mas el ping evita que un doble clic sobre una categoria compre una fila recien cambiada.

## Desarrollo

```powershell
.\gradlew.bat --no-daemon clean build
node --test tools/tideframe-editor/*.test.mjs
node tools/tideframe-screens/build-screens.mjs
```

El editor y su servidor se ejecutan desde `tools/tideframe-editor`; consultar su README para argumentos.
Las demostraciones originales se conservan; habilitarlas aumenta el tiempo de generacion y el peso del pack.
El runtime integrado usa el scheduler Paper de BigCasares, no se anuncia soporte Folia del plugin completo.

## Verificacion automatica (2026-11-02)

Con JDK 25.0.3, `.\gradlew.bat --offline --no-daemon clean build` termino en `BUILD SUCCESSFUL` (12 min 49 s)
y ejecuto `test`, `tideframeTest` y `verifyEmbeddedTideframe`:

- `build/test-results/test` (BigCasares): 1323 tests, 0 fallos, 0 errores, 0 omitidos.
- `build/test-results/tideframeTest` (motor): 591 tests en las 107 clases de prueba, 0 fallos, 0 errores y
  1 omitido. `BossPortraitTest.symbolicLinkCannotReadPortraitOutsideConfiguredDirectory` se aborta cuando el
  sistema no permite crear enlaces simbolicos; en la PC de la verificacion (Windows) faltaba ese privilegio,
  asi que ese caso no quedo verificado.
- Editor: `node --test "tools/tideframe-editor/*.test.mjs"` con Node 24: 322 tests, 0 fallos.
- JAR `build/libs/BigCasares-0.0.1.jar`: 299 clases `dev/tideframe`, un unico `plugin.yml` (BigCasares, sin
  `softdepend` de Tideframe), ningun JAR anidado, y `META-INF/tideframe/LICENSE` y `ASSETS.md` presentes.

## Validacion pendiente

Las pruebas comprueban logica, recursos, contratos y empaquetado. No demuestran renderizado ni interaccion real.
Falta verificar en un servidor Paper con clientes Java y Bedrock: entrada con ambos packs, pantalla, clics,
camara y restauracion, recarga, generacion/publicacion HTTP y carga representativa. No desplegar en produccion
sin completar esa aceptacion. La checklist esta en `TODO.md`.
