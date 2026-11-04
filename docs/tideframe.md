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
- `/misiones`: listas diarias/semanales. Reclamar cobra en la misma pantalla con el cobro de siempre del
  modulo (`MissionModule.claimAll`, que solo paga lo no cobrado) y redibuja la lista; el boton no tiene accion
  de Tideframe y el clic se cancela. Una pantalla editada que todavia tenga `close` en Reclamar no se cierra.
- Tienda (desde un mercader NPC; el comando `/shop` esta desactivado en BigCasares y lo dice): categorias,
  paginas, compra izquierda y venta derecha, siempre a traves de `ShopService`.
  El boton "Cant." alterna x1, x4 y x16 lotes: cada clic compra o vende esa cantidad como una sola operacion
  (un cobro o un pago, todo o nada) y las filas muestran el precio de esa cantidad. Cambiar la cantidad cuenta
  como filas nuevas para la espera anti doble clic. Si el saldo no alcanza para la cantidad elegida, la fila
  muestra "falta $X" en lugar del precio de compra. En Bedrock el primer boton del formulario de una categoria
  es "Volver a las categorías", el segundo la misma cantidad y el tercero alterna "Modo: comprar" y "Modo: vender"
  (el formulario de categorías muestra cuántos productos tiene cada una, como Java). Comprando, una fila que no se puede
  pagar va en rojo con lo que falta; vendiendo, muestra el precio de venta, en rojo con cuántos lleva el jugador si
  no alcanzan, o "no se compra acá" si el producto no tiene precio de venta.
  Shift + clic derecho (Java) vende todos los lotes enteros que el jugador lleva, hasta 64, en una sola
  operacion. Compras, ventas y cobros de misiones suenan (exito, rechazo, recompensa) solo para quien los hizo,
  desde cualquier menu: `ShopService` avisa cada operacion de un jugador. Las que salen bien quedan en
  `/bigcasares historial` (permiso `bigcasares.shop.history`, todos): las ultimas 10, guardadas con el jugador (`bigcasares:shop_history_v1`), asi que sobreviven reinicios.
- Misiones: las listas para cobrar van primero (Java y Bedrock), asi nunca quedan entre las ocultas. Cuando una
  mision queda lista, el jugador recibe un aviso en el chat una sola vez, con el premio. Las dos ediciones
  muestran cuanta plata pagaria Reclamar ("2 para cobrar · $650"). En Bedrock, despues de comprar o vender el
  formulario de la categoria vuelve a abrirse con el saldo nuevo, como la pantalla Java que sigue abierta.
- Favoritos: Shift + clic izquierdo (Java) o el modo "favoritos" del formulario (Bedrock) marcan un producto. La
  categoria "★ Favoritos" aparece primera mientras haya alguno. Se guardan con el jugador (PersistentDataContainer,
  clave `bigcasares:shop_favorites_v1`, lineas `categoria/entrada`, hasta 54); los productos que el catalogo ya no
  tiene se saltean pero no se borran.
- Misiones guardan con el jugador cuanto cobro en la semana (`bigcasares:missions_week_v1`, se reinicia con la
  semana) y su racha de dias con todas las diarias cobradas (`bigcasares:missions_streak_v1`, se corta si pasa un
  dia). Se ven junto al reinicio: la racha en Diarias y "esta semana $X" en Semanales, en Java y Bedrock.
- Al entrar, si hay misiones para cobrar, el jugador recibe a los 3 segundos "Tenés N misiones para cobrar ($X)".
- "Cambiar" (Java, pestaña Diarias) y "Cambiar las diarias" (Bedrock) reemplazan las diarias una vez por dia,
  con un segundo toque en menos de 3 segundos para confirmar. Nunca mientras haya una diaria lista sin cobrar:
  el cambio la borraria. Esa proteccion vale tambien para `/misiones reroll`.
- Con PlaceholderAPI: `%bigc_misiones_listas%`, `%bigc_misiones_premio%` y `%bigc_tienda_ultima%`, con
  identificador propio (`bigcasares` ya lo usan los equipos). Se actualizan cada 2 segundos en el hilo del servidor.
- Textos largos: el motor ya achica las lineas que no entran en su columna y, si igual no entran, las corta
  con "…" (`TextFit`, pantallas de esquema 9 que no usan la calidad "legacy", como `bigc_*`).
- `/bigcasares tideframe` (permiso `bigcasares.tideframe.status`, op): si el motor corre, si envia su pack,
  que pantallas `bigc_*` faltan y cuantos jugadores Java tienen el pack (y cuantos Bedrock usan formularios).
  Tambien cuenta, desde el arranque, cuantas veces misiones o tienda se abrieron con el menu clasico y por que
  (sin pack cargado, en el aire o vehiculo, pantalla sin cargar, pack.send apagado, apertura fallida...).
- El resto de menus de BigCasares no se convierte automaticamente: incluir todo el motor no equivale a redisenar
  todas las pantallas del plugin.
- Java usa Tideframe cuando la pantalla y su pack estan listos. Bedrock conserva los formularios existentes;
  no se afirma que el HUD Java funcione en Bedrock.
- Planeando, en vehiculo, en el aire, muerto o sin pack: no se fija la camara y se utiliza el menu clasico.
- Una apertura prepara su vista en el evento de apertura. Cierres anteriores no borran la vista nueva.
- Solo la navegacion mas reciente puede abrir su fallback o reclamos, incluidos menus clasicos y aperturas en cola.
  Si hay una camara bloqueada de otra pantalla, se solicita cerrarla antes de abrir un inventario por debajo.
- La espera de medio segundo mas el ping evita que un doble clic sobre una categoria compre una fila recien cambiada.

## Bedrock: formularios con el aspecto de Tideframe

Bedrock no puede dibujar el HUD Java de Tideframe. Tienda y misiones siguen siendo formularios de Floodgate, pero
el pack de Bedrock de BigCasares los dibuja con los colores de `bigc_tienda` y `bigc_misiones`.

- `tools/tideframe-screens/build-bedrock-ui.mjs` lee esas dos pantallas y genera `resourcepack/bedrock/ui/`,
  `textures/ui/bigc/` y `font/glyph_E5.png`. Si cambian los colores de las pantallas, volver a ejecutarlo.
  `BedrockFormPackContractTest` falla si quedan desfasados.
- Solo cambia el aspecto de los formularios cuyo título empieza con `BedrockFormStyle.TAG` (códigos de color
  invisibles). El pack agrega una segunda fábrica a la pantalla de formularios de Bedrock: su panel tapa el
  formulario normal solo con esa marca. Los demás formularios (otros plugins, Don Pollos) no cambian, y si el
  panel no se dibujara, los botones normales de abajo responden igual.
- Tienda: cada fila muestra el ícono de Bedrock del material cuando el pack propio de Bedrock lo tiene
  (`geyser/bedrock-textures.txt`, `BedrockItemIcons`). Los objetos custom quedan sin ícono. El precio va en
  dorado, o en rojo si no alcanza el saldo.
- Misiones (antes, el cofre de inventario): pestañas Diarias/Semanales, cada misión con barra de progreso,
  estado y premio, y "Reclamar" cuando hay algo para cobrar. Reclamar cobra con el mismo cobro que Java y
  vuelve a mostrar la lista. La tienda tiene cantidad y modo vender (ver "Uso y autoridad"). Las barras usan los caracteres `U+E500..U+E503` del pack; sin el pack se usan barras de texto.
- El pack de Bedrock pasó a la versión 2.1.0 para que los clientes descarguen el nuevo. Cambiar el pack de
  Bedrock requiere reiniciar el servidor.
- No verificado en un cliente Bedrock real: ver la lista de `TODO.md`.

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
