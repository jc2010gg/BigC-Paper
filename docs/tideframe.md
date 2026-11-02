# Pantallas de Tideframe (opcional)

BigCasares puede mostrar algunos menús con **Tideframe**, un plugin aparte de interfaz (menú a pantalla completa con cursor, dibujado con el resource pack de Tideframe). Es solo presentación: los precios, el dinero, los objetos, los premios y los permisos siguen en los servicios de BigCasares. Sin Tideframe, todo funciona igual que antes.

Módulo: `tideframe-ui` (`dev.linqfy.bigCasares.modules.tideframe`).

## Qué pantallas hay

| Pantalla | Se abre con | Qué hace | Qué no hace |
| --- | --- | --- | --- |
| `bigc_misiones` | `/misiones` (y lo que hoy abre las listas de misiones) | Misiones diarias o semanales: progreso, estado y premio. "Reclamar" cierra la pantalla y abre el menú de inventario de siempre | No reclama: el cobro sigue en el menú de inventario, por `claimAll` |
| `bigc_tienda` | `/shop` y los NPC de tienda | Categorías y productos por páginas. Clic izquierdo compra, derecho vende | Nada fuera de `ShopService`: cada clic es una sola llamada al servicio, con las mismas reglas que el menú de inventario |

## Cuándo se usa Tideframe y cuándo el menú de siempre

Se abre la pantalla de Tideframe solo si se cumple todo esto; si no, se abre el menú de siempre (inventario, o formulario en Bedrock):

- Tideframe está instalado, activo y registró su servicio `TideframeApi`.
- Tideframe ya tiene la pantalla `bigc_*` cargada.
- El jugador es de **Java**. En Bedrock siguen los formularios de BigCasares: los HUD y fuentes de Tideframe no existen en Bedrock.
- El jugador cargó **el pack de Tideframe** (su propio UUID, no cualquier pack). Para eso Tideframe tiene que mandar su propio pack (`pack.send: true`, lo normal): con `pack.send: false` cualquier pack cargado cuenta como el suyo, así que BigCasares no puede comprobarlo y usa siempre los menús de siempre (lo avisa en el log). Si cambiás `pack.send`, reiniciá el servidor: el archivo puede decir `true` antes de que Tideframe lo recargue. Una vez que BigCasares lo vio en `false`, conserva esa protección incluso entre recargas y reconexiones del módulo; solo un reinicio completo la restablece. Un `config.yml` de Tideframe que no se puede leer también cuenta como `false`.
- No está planeando, en un vehículo, en el aire (gancho, caída) ni muerto: el menú de Tideframe congela la cámara.

Si la pantalla no llega a abrir (pack rechazado, tiempo agotado, desconexión), se abre el menú de siempre.
Si todavía queda otra pantalla de Tideframe abierta, el jugador recibe un aviso para cerrarla primero;
el inventario clásico espera ese cierre para no quedar debajo de una cámara bloqueada. Pedir otro menú
cancela ese fallback pendiente. Una pantalla anterior con el mismo id no cuenta como apertura exitosa.
Las aperturas clásicas de tienda y misiones también registran navegación y esperan el cierre de esa cámara,
incluso si su pantalla individual está desactivada en la configuración de Tideframe.

## Autoridad y doble efecto

- Los botones de las pantallas `bigc_*` no tienen acciones de Tideframe. BigCasares atiende el clic y lo cancela en Tideframe, así nada se cobra ni se entrega dos veces.
- Las únicas excepciones usan la acción `close` de Tideframe, que solo cierra el menú:
  - la X;
  - "Reclamar" en misiones. BigCasares escucha su clic izquierdo y no cierra nada por su cuenta. El menú de inventario se abre recién con el cierre que hace Tideframe en el mismo paso del clic, y nunca encima de otro menú de Tideframe. El clic derecho no hace nada.
  - Si una pantalla editada no se cierra con "Reclamar" (le sacaron el `close`, o la pantalla vuelve a otra al cerrar), no se abre nada: el jugador ve un aviso, el log explica qué falta y misiones usa el menú de siempre hasta recargar BigCasares.
- Las pantallas `bigc_*` solo las abre BigCasares. Si Tideframe intenta abrir una por su cuenta, BigCasares cancela la apertura y le avisa al jugador que se abre con su comando: abriría sin datos y sin las comprobaciones de BigCasares. Pasa en estos casos:
  - al reabrir menús después de recargar (`editor.reopen-after-reload`);
  - con `/tideframe open`;
  - con "Abrir en el juego" del editor.
- Abrir de nuevo `/shop` o `/misiones` con la pantalla abierta conserva lo que se acaba de mandar. Tideframe avisa que la pantalla vieja fue reemplazada, pero mantiene las variables y listas del jugador. Lo que BigCasares recuerda de cada jugador se olvida solo cuando se cierra toda la interfaz de Tideframe.
- Los datos de una nueva apertura se preparan durante su evento de apertura, no durante el comando que la solicita. Así, un comando `/shop` ejecutado al cerrar otra pantalla no pierde su vista con la limpieza anterior. Las escrituras de variables y listas conservan el envío diferido de la API de Tideframe.
- "Reclamar" recuerda la navegación en la que se pulsó: cualquier apertura posterior lo cancela, aunque todavía esté en cola o esa otra pantalla ya se haya cerrado. No reaparece un inventario de reclamos sobre la tienda.
- Una fila de la lista se resuelve contra lo que ese jugador estaba viendo: el id de la fila se busca de nuevo en el catálogo actual. Si el producto ya no está, no se compra nada.
- Las listas nunca tienen más filas de las que muestra la pantalla (6), así que la fila tocada es siempre el producto de esa página.
- Cuando cambian las filas (al abrir, entrar a una categoría, cambiar de página o volver), los clics en ellas se ignoran durante medio segundo más el ping del jugador. Así el segundo clic de un doble clic sobre una categoría no compra el producto que quedó en esa fila. Mientras las filas no cambian, cada clic compra o vende al instante, como en el menú de inventario.
- Los textos y listas se mandan como variables globales del jugador con prefijo `bigc_`; las pantallas no declaran esas variables (una variable local las taparía).

## Instalación

1. Poner Tideframe en `plugins/` (integración preparada contra su API 0.23.0 para Paper 26.2; pendiente de prueba real). BigCasares lo declara en `softdepend`.
2. Al arrancar el servidor, en la carga de BigCasares (antes de que se active cualquier plugin), BigCasares copia `bigc_misiones.json` y `bigc_tienda.json` a `plugins/Tideframe/screens/`, solo si cambiaron. Así entran en el pack que arma Tideframe al activarse. Si Tideframe se activa más tarde (por ejemplo, con un gestor de plugins), las carga solo (con `editor.auto-reload: true`) o con `/tideframe reload`, y rearma su pack una vez.
3. Cada jugador de Java recibe dos packs: el de BigCasares y el de Tideframe, cada uno con su UUID. No se pisan: Tideframe solo usa su namespace `tideframe` y BigCasares no trae shaders ni fuentes. Para jugadores remotos, los dos publicadores necesitan una URL pública (`pack.public-url` en Tideframe y la del publicador de BigCasares).

Mantener `display.auto-fit: false` en Tideframe en producción: el shader de ajuste es experimental, solo para clientes 26.2, y puede chocar con otros packs o con Iris y OptiFine.

## Configuración (`config.yml`)

```yaml
modules:
  tideframe-ui:
    enabled: true     # false: nunca se usa Tideframe
tideframe-ui:
  missions: true      # misiones con Tideframe
  shop: true          # tienda con Tideframe
```

Un `config.yml` existente no recibe estas claves solo; sin ellas, todo queda en `true`.

## Editar las pantallas

Las pantallas se editan en el editor web de Tideframe como cualquier página suya. BigCasares nunca pisa una pantalla editada: si trae una versión nueva, la deja en `plugins/BigCasares/tideframe/<id>.bundled.json` para comparar. `plugins/BigCasares/tideframe/installed-screens.properties` guarda qué versión escribió.

Al editar, conservar los ids que usa BigCasares:

- Misiones: `diarias`, `semanales`, `reclamar` (con acción `close`) y la lista `bigc_misiones`.
- Tienda: `fila`, `volver`, `anterior`, `siguiente` y la lista `bigc_tienda`.
- En las dos, la X `cerrar` con acción `close`.

El "Revisar" del editor avisa que esos botones "no hacen nada": es lo esperado, porque los atiende BigCasares.

Para abrir estas pantallas desde otro menú de Tideframe, poné en el botón la acción de comando (`command:/shop` o `command:/misiones`), no `open:bigc_tienda`: BigCasares cancela las aperturas que no pidió.

Las pantallas de origen se generan con el modelo del editor de Tideframe:

```powershell
node tools/tideframe-screens/build-screens.mjs <carpeta de Tideframe>
```

## Compilar contra Tideframe

`libs/tideframe-api-0.23.0.jar` (unos 7 KB) tiene solo la interfaz `TideframeApi` y los eventos de clic, apertura y cierre, sacados de una compilación de Tideframe. Es `compileOnly`: no entra en el JAR de BigCasares. En el servidor esas clases las pone el plugin Tideframe. Solo dos clases nombran tipos de Tideframe (`TideframeApiBridge`, `TideframeEventRelay`); se cargan por reflexión después de encontrar Tideframe activo, y un test lo comprueba. Para regenerar el JAR de la API con otra versión de Tideframe:

```powershell
.\tools\tideframe-api\make-api-jar.ps1 -Classes <Tideframe>\build\classes\java\main -Version <versión>
```

Al conectar, BigCasares comprueba que la API instalada tenga cada método que usa, con los mismos tipos. Si no coincide, lo avisa en el log y se usan los menús de siempre. Si igual una llamada falla más tarde, deja de usar Tideframe hasta el próximo arranque.

## Límites

- Las filas muestran texto: la tienda de Tideframe no dibuja el ícono de cada producto (el menú de inventario sí).
- Un texto más largo que su columna se corta, salvo con la versión de Tideframe que achica el texto que no entra.
- **No probado todavía en clientes reales**: Java (pantallas, escala, clics, packs juntos) y la caída a formularios en Bedrock. La lista está en `TODO.md`.
